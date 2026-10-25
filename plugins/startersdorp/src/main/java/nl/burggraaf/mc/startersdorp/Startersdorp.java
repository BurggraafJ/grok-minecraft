package nl.burggraaf.mc.startersdorp;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bouwt één keer per wereld een klein startersdorp. Zoekt rond de spawn een vlakke, open, droge plek, laat
 * Minecraft daar een dorp neerzetten met zijn eigen dorpsstukken (de datapack startersdorp maakt de dorpen kleiner:
 * grootte 3 in plaats van 6) en verplaatst de spawn naar de dorpsbel. Het dorpstype volgt het biome.
 * Chunks laden asynchroon, zodat de server niet hapert tijdens het zoeken.
 *
 * /startersdorp opnieuw  · klaar.yml weg en opnieuw zoeken en bouwen rond de spawn
 * /startersdorp test x z · een proefdorp rond x,z met een kaartje, zonder de spawn te verplaatsen
 */
public final class Startersdorp extends JavaPlugin {

    private static final int MAX_KANDIDATEN = 400;

    /** score: hoogteverschil plus een straf voor bomen en gesloten biomes; lager is beter. */
    private record Plek(int x, int z, int y, int verschil, int bomen, int score, String type) {}

    private File klaar;
    private World wereld;
    private final Deque<int[]> kandidaten = new ArrayDeque<>();
    private Plek beste;
    private int bekeken;
    private String test;
    private boolean bezig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        klaar = new File(getDataFolder(), "klaar.yml");
        if (!klaar.exists()) {
            getServer().getScheduler().runTaskLater(this, () -> begin(null, null), 40L);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (bezig) {
            sender.sendMessage("Startersdorp: er loopt al een zoektocht.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("opnieuw")) {
            klaar.delete();
            begin(null, null);
            sender.sendMessage("Startersdorp: zoekt een plek rond de spawn en bouwt (zie de log).");
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("test")) {
            int x = Integer.parseInt(args[1]);
            int z = Integer.parseInt(args[2]);
            begin(new int[] {x, z}, x + "_" + z);
            sender.sendMessage("Startersdorp: proefdorp rond " + x + ", " + z + " (zie de log).");
            return true;
        }
        return false;
    }

    private void begin(int[] midden, String testNaam) {
        bezig = true;
        test = testNaam;
        kandidaten.clear();
        beste = null;
        bekeken = 0;
        wereld = Bukkit.getWorlds().get(0);
        Location spawn = midden == null ? wereld.getSpawnLocation() : new Location(wereld, midden[0], 64, midden[1]);
        int straal = getConfig().getInt("zoekstraal", 384);
        int stap = 16;
        kandidaten.add(new int[] {spawn.getBlockX(), spawn.getBlockZ()});
        for (int r = stap; r <= straal; r += stap) {
            for (int d = -r; d < r; d += stap) {
                kandidaten.add(new int[] {spawn.getBlockX() + d, spawn.getBlockZ() - r});
                kandidaten.add(new int[] {spawn.getBlockX() + r, spawn.getBlockZ() + d});
                kandidaten.add(new int[] {spawn.getBlockX() - d, spawn.getBlockZ() + r});
                kandidaten.add(new int[] {spawn.getBlockX() - r, spawn.getBlockZ() - d});
            }
        }
        getLogger().info("Zoekt een vlakke, open plek voor het startersdorp rond " + spawn.getBlockX() + ", " + spawn.getBlockZ());
        volgende();
    }

    /** Eén kandidaat per keer: chunks async laden, dan beoordelen op de hoofdthread. */
    private void volgende() {
        int vlak = getConfig().getInt("max-hoogteverschil", 3);
        if (kandidaten.isEmpty() || bekeken >= MAX_KANDIDATEN
                || (beste != null && beste.verschil() <= vlak && beste.bomen() == 0 && beste.score() <= vlak)) {
            bouw();
            return;
        }
        int[] k = kandidaten.poll();
        bekeken++;
        List<CompletableFuture<Chunk>> laden = new ArrayList<>();
        for (int cx = (k[0] - 8) >> 4; cx <= (k[0] + 8) >> 4; cx++) {
            for (int cz = (k[1] - 8) >> 4; cz <= (k[1] + 8) >> 4; cz++) {
                laden.add(wereld.getChunkAtAsync(cx, cz));
            }
        }
        CompletableFuture.allOf(laden.toArray(new CompletableFuture[0])).whenComplete((ok, fout) ->
                getServer().getScheduler().runTask(this, () -> {
                    if (fout == null) {
                        Plek p = beoordeel(k[0], k[1]);
                        if (p != null && (beste == null || p.score() < beste.score())) {
                            beste = p;
                        }
                    }
                    volgende();
                }));
    }

    private Plek beoordeel(int x, int z) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int bomen = 0;
        for (int dx = -8; dx <= 8; dx += 4) {
            for (int dz = -8; dz <= 8; dz += 4) {
                Block top = wereld.getHighestBlockAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Material m = top.getType();
                if (m == Material.WATER || m == Material.LAVA || m == Material.ICE || m == Material.PACKED_ICE
                        || m == Material.BLUE_ICE || m.name().contains("KELP") || m == Material.SEAGRASS) {
                    return null;
                }
                int dak = wereld.getHighestBlockYAt(x + dx, z + dz, HeightMap.WORLD_SURFACE);
                if (dak - top.getY() >= 3 || isBoom(m)) {
                    bomen++;
                }
                min = Math.min(min, top.getY());
                max = Math.max(max, top.getY());
            }
        }
        int y = wereld.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        String biome = wereld.getBiome(x, y, z).getKey().getKey().toLowerCase(Locale.ROOT);
        if (biome.contains("ocean") || biome.contains("river") || biome.contains("swamp") || biome.contains("beach")) {
            return null;
        }
        boolean open = biome.contains("plains") || biome.contains("meadow") || biome.contains("savanna")
                || biome.contains("desert") || biome.contains("sunflower");
        int score = (max - min) + 2 * bomen + (open ? 0 : 3);
        return new Plek(x, z, y, max - min, bomen, score, dorpstype(biome));
    }

    private static String dorpstype(String biome) {
        if (biome.contains("desert") || biome.contains("badlands")) {
            return "desert";
        }
        if (biome.contains("savanna")) {
            return "savanna";
        }
        if (biome.contains("snowy") || biome.contains("frozen") || biome.contains("ice")) {
            return "snowy";
        }
        if (biome.contains("taiga") || biome.contains("grove")) {
            return "taiga";
        }
        return "plains";
    }

    private static boolean isBoom(Material m) {
        String n = m.name();
        return n.endsWith("_LOG") || n.endsWith("_LEAVES") || n.endsWith("_WOOD") || n.equals("VINE")
                || n.endsWith("MUSHROOM_BLOCK") || n.equals("MUSHROOM_STEM") || n.equals("BEE_NEST")
                || n.equals("BAMBOO") || n.equals("MANGROVE_ROOTS") || n.equals("COCOA");
    }

    /** Laad (async) en houd de chunks rond de plek vast; /place structure weigert ongeladen posities. */
    private void bouw() {
        if (beste == null) {
            Location s = wereld.getSpawnLocation();
            int y = wereld.getHighestBlockYAt(s.getBlockX(), s.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            beste = new Plek(s.getBlockX(), s.getBlockZ(), y, 99, 99, 999, "plains");
            getLogger().warning("Geen geschikte plek gevonden; het dorp komt op de spawn zelf.");
        }
        Plek p = beste;
        List<CompletableFuture<Chunk>> laden = new ArrayList<>();
        for (int cx = (p.x() >> 4) - 5; cx <= (p.x() >> 4) + 5; cx++) {
            for (int cz = (p.z() >> 4) - 5; cz <= (p.z() >> 4) + 5; cz++) {
                wereld.addPluginChunkTicket(cx, cz, this);
                laden.add(wereld.getChunkAtAsync(cx, cz));
            }
        }
        CompletableFuture.allOf(laden.toArray(new CompletableFuture[0])).whenComplete((ok, fout) ->
                getServer().getScheduler().runTask(this, () -> {
                    try {
                        bouwHier(p);
                    } finally {
                        wereld.removePluginChunkTickets(this);
                        bezig = false;
                    }
                }));
    }

    private void bouwHier(Plek p) {
        int gekapt = maakOpenPlek(p, getConfig().getInt("open-plek", 30));
        int voor = telDorpsblokken(p);
        String eigen = String.format(Locale.ROOT, getConfig().getString("structuur", "startersdorp:dorp_%s"), p.type());
        String gebruikt = eigen;
        int erbij = plaats(eigen, p) - voor;
        if (erbij < 20) {
            getLogger().warning(eigen + " lukte niet (staat de datapack startersdorp aan?); nu een gewoon Minecraft-dorp.");
            gebruikt = "minecraft:village_" + p.type();
            erbij = plaats(gebruikt, p) - voor;
        }
        if (erbij < 20) {
            getLogger().warning("Het dorp lijkt niet gebouwd (" + erbij + " dorpsblokken erbij); bij de volgende start probeer ik het opnieuw.");
            return;
        }
        int[] huizen = telHuizen(p);
        Location bel = zoekBel(p);
        int dorpelingen = zorgVoorDorpelingen(bel, huizen[0]);
        String kaart = test == null ? "kaartje.png" : "test-" + test + ".png";
        try {
            getDataFolder().mkdirs();
            kaartje(bel.getBlockX(), bel.getBlockZ(), bel.getBlockY(), kaart);
        } catch (IOException e) {
            getLogger().warning("Kon het kaartje niet schrijven: " + e.getMessage());
        }
        String samenvatting = String.format(Locale.ROOT, "%s (%s) bij de bel op %d %d %d: %d huisjes (bedden), %d deuren, %d dorpelingen, hoogteverschil %d, %d boomblokken gekapt, %d plekken bekeken",
                gebruikt, p.type(), bel.getBlockX(), bel.getBlockY(), bel.getBlockZ(), huizen[0], huizen[1], dorpelingen, p.verschil(), gekapt, bekeken);
        if (test != null) {
            getLogger().info("TEST " + samenvatting);
            return;
        }
        wereld.setSpawnLocation(bel);
        YamlConfiguration y = new YamlConfiguration();
        y.set("x", bel.getBlockX());
        y.set("y", bel.getBlockY());
        y.set("z", bel.getBlockZ());
        y.set("type", p.type());
        y.set("structuur", gebruikt);
        y.set("bedden", huizen[0]);
        y.set("dorpelingen", dorpelingen);
        y.set("hoogteverschil", p.verschil());
        y.set("bomen_gekapt", gekapt);
        y.set("kandidaten_bekeken", bekeken);
        try {
            y.save(klaar);
        } catch (IOException e) {
            getLogger().warning("Kon klaar.yml niet schrijven: " + e.getMessage());
        }
        getLogger().info("Startersdorp gebouwd en spawn verplaatst: " + samenvatting);
    }

    /** Plaatst een structuur en geeft het aantal dorpsblokken erna. */
    private int plaats(String structuur, Plek p) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(Locale.ROOT,
                "execute in %s run place structure %s %d %d %d", wereld.getKey(), structuur, p.x(), p.y(), p.z()));
        return telDorpsblokken(p);
    }

    /** Haalt bomen weg in een cirkel rond het dorp, zodat de huisjes niet tussen de stammen staan. */
    private int maakOpenPlek(Plek p, int straal) {
        int weg = 0;
        for (int dx = -straal; dx <= straal; dx++) {
            for (int dz = -straal; dz <= straal; dz++) {
                if (dx * dx + dz * dz > straal * straal) {
                    continue;
                }
                int x = p.x() + dx;
                int z = p.z() + dz;
                int top = wereld.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                for (int y = top; y > p.y() - 6; y--) {
                    Block b = wereld.getBlockAt(x, y, z);
                    if (isBoom(b.getType())) {
                        b.setType(Material.AIR, false);
                        weg++;
                    } else if (!b.getType().isAir() && b.getType().isSolid()) {
                        break;
                    }
                }
            }
        }
        return weg;
    }

    /** De dorpsbel (het plein); anders het midden. Geeft een veilige plek om op te staan, naast de bel. */
    private Location zoekBel(Plek p) {
        for (int r = 0; r <= 48; r++) {
            for (int x = p.x() - r; x <= p.x() + r; x++) {
                for (int z = p.z() - r; z <= p.z() + r; z++) {
                    if (Math.max(Math.abs(x - p.x()), Math.abs(z - p.z())) != r) {
                        continue;
                    }
                    for (int y = p.y() - 10; y <= p.y() + 20; y++) {
                        if (wereld.getBlockAt(x, y, z).getType() == Material.BELL) {
                            int sx = x + 2;
                            int sy = wereld.getHighestBlockYAt(sx, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                            return new Location(wereld, sx + 0.5, sy, z + 0.5);
                        }
                    }
                }
            }
        }
        int sy = wereld.getHighestBlockYAt(p.x(), p.z(), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        return new Location(wereld, p.x() + 0.5, sy, p.z() + 0.5);
    }

    /** {bedden, deuren} binnen 48 blokken: een maat voor het aantal huisjes. */
    private int[] telHuizen(Plek p) {
        int bedden = 0;
        int deuren = 0;
        for (int x = p.x() - 48; x <= p.x() + 48; x++) {
            for (int z = p.z() - 48; z <= p.z() + 48; z++) {
                for (int y = p.y() - 8; y <= p.y() + 16; y++) {
                    Block b = wereld.getBlockAt(x, y, z);
                    String m = b.getType().name();
                    if (m.endsWith("_BED") && b.getBlockData() instanceof Bed bed && bed.getPart() == Bed.Part.HEAD) {
                        bedden++;
                    } else if (m.endsWith("_DOOR") && !m.contains("TRAP") && b.getRelative(0, -1, 0).getType() != b.getType()) {
                        deuren++;
                    }
                }
            }
        }
        return new int[] {bedden, deuren};
    }

    /** Een dorp zonder dorpelingen is saai: zet er een paar bij de bel als Minecraft dat niet deed. */
    private int zorgVoorDorpelingen(Location bel, int bedden) {
        long er = wereld.getNearbyEntitiesByType(Villager.class, bel, 64).size();
        if (er > 0) {
            return (int) er;
        }
        int aantal = Math.max(2, Math.min(6, bedden));
        for (int i = 0; i < aantal; i++) {
            double hoek = 2 * Math.PI * i / aantal;
            int x = bel.getBlockX() + (int) Math.round(3 * Math.cos(hoek));
            int z = bel.getBlockZ() + (int) Math.round(3 * Math.sin(hoek));
            int y = wereld.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            wereld.spawnEntity(new Location(wereld, x + 0.5, y, z + 0.5), EntityType.VILLAGER);
        }
        return aantal;
    }

    /** Telt blokken die alleen een dorp neerzet, in 48 blokken rond de plek. */
    private int telDorpsblokken(Plek p) {
        int n = 0;
        for (int x = p.x() - 48; x <= p.x() + 48; x += 2) {
            for (int z = p.z() - 48; z <= p.z() + 48; z += 2) {
                for (int y = p.y() - 6; y <= p.y() + 12; y++) {
                    String m = wereld.getBlockAt(x, y, z).getType().name();
                    if (m.endsWith("_PLANKS") || m.endsWith("_BED") || m.equals("BELL") || m.endsWith("_DOOR")
                            || m.equals("COBBLESTONE") || m.endsWith("GLASS_PANE") || m.equals("DIRT_PATH")
                            || m.equals("HAY_BLOCK") || m.equals("SMOOTH_SANDSTONE") || m.endsWith("_STAIRS")) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Een kaartje van 96×96 blokken van boven (zonder bladeren), met een rode stip op de spawn. */
    private void kaartje(int cx, int cz, int cy, String naam) throws IOException {
        int r = 48;
        int schaal = 4;
        BufferedImage img = new BufferedImage(2 * r * schaal, 2 * r * schaal, BufferedImage.TYPE_INT_RGB);
        for (int dx = -r; dx < r; dx++) {
            for (int dz = -r; dz < r; dz++) {
                Block b = wereld.getHighestBlockAt(cx + dx, cz + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                int rgb = b.getBlockData().getMapColor().asRGB();
                int schaduw = Math.max(-40, Math.min(40, (b.getY() - cy) * 6));
                int rr = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + schaduw));
                int gg = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + schaduw));
                int bb = Math.max(0, Math.min(255, (rgb & 255) + schaduw));
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    rr = 255;
                    gg = 0;
                    bb = 0;
                }
                for (int i = 0; i < schaal; i++) {
                    for (int j = 0; j < schaal; j++) {
                        img.setRGB((dx + r) * schaal + i, (dz + r) * schaal + j, (rr << 16) | (gg << 8) | bb);
                    }
                }
            }
        }
        ImageIO.write(img, "png", new File(getDataFolder(), naam));
    }
}
