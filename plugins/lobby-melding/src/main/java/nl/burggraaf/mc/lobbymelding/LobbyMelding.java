package nl.burggraaf.mc.lobbymelding;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * De lobby: de veilige wachtkamer tijdens een herstart van survival.
 * - Geen schade, geen honger, adventure, een vloer op de spawn; wie valt gaat terug naar boven.
 * - Titel, chatregel en actionbar: je gaat zo vanzelf terug.
 * - Parkour in 5 levels (steeds moeilijker), elk met een huisje bij de start en een finishplatform. Voortgang per
 *   speler in voortgang.yml: wie een level haalt, begint de volgende keer in de lobby bij het volgende level.
 * - `/lobbymelding terug` (console, door mc als survival klaar is): groot aftellen 5-4-3-2-1 met geluid, dan via
 *   Velocity (BungeeCord-kanaal) iedereen naar survival.
 * Ook het sjabloon voor `mc plugin nieuw <naam>`.
 */
public final class LobbyMelding extends JavaPlugin implements Listener {

    private static final String KANAAL = "BungeeCord";

    private Location veilig;
    private int grondY;
    private final Map<UUID, Long> startTijd = new HashMap<>();
    private final Map<UUID, Integer> laatstePunt = new HashMap<>();
    private final Set<UUID> aftellend = new HashSet<>();
    /** Wie hier vrijwillig is (/parkour vanuit survival, of /blijf): niet automatisch terug, alleen met /terug. */
    private final Set<UUID> blijft = new HashSet<>();
    private boolean actief = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Staging is een kopie van survival met dezelfde plugins: daar niets doen (mc zet daar motd STAGING).
        if (!getConfig().getBoolean("actief", true) || Bukkit.getMotd().contains("STAGING")) {
            actief = false;
            getLogger().info("niet actief op deze server");
            return;
        }
        itemKey = new NamespacedKey(this, "blijf-item");
        leesBlijvers();
        // Onthouden blijvers (van vóór een lobbyherstart) die niet binnen 3 minuten terug zijn: vergeten.
        Set<UUID> uitBestand = new HashSet<>(blijft);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean weg = blijft.removeIf(id -> uitBestand.contains(id) && Bukkit.getPlayer(id) == null);
            if (weg) bewaarBlijvers();
        }, 3 * 60 * 20L);
        voortgangBestand = new File(getDataFolder(), "voortgang.yml");
        voortgang = YamlConfiguration.loadConfiguration(voortgangBestand);
        getServer().getMessenger().registerOutgoingPluginChannel(this, KANAAL);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, () -> {
            veilig = maakVloer();
            if (getConfig().getBoolean("parkour", true)) bouwParkour();
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            String balk = getConfig().getString("actionbar", "");
            for (Player p : Bukkit.getOnlinePlayers()) {
                maakVeilig(p);
                geefItem(p);
                if (aftellend.contains(p.getUniqueId())) continue;
                Long t = startTijd.get(p.getUniqueId());
                if (t != null) {
                    p.sendActionBar(Component.text("Parkour ", NamedTextColor.AQUA)
                            .append(Component.text(tijd(System.currentTimeMillis() - t), NamedTextColor.WHITE)));
                } else if (blijft.contains(p.getUniqueId())) {
                    p.sendActionBar(Component.text("Parkour · /terug = terug naar survival", NamedTextColor.AQUA));
                } else if (!balk.isBlank()) {
                    p.sendActionBar(Component.text(balk, NamedTextColor.YELLOW));
                }
            }
        }, 10L, 10L);
    }

    @Override
    public void onDisable() {
        for (World w : Bukkit.getWorlds()) {
            w.getEntitiesByClass(TextDisplay.class).stream()
                    .filter(d -> d.getScoreboardTags().contains("lobbymelding")).forEach(TextDisplay::remove);
        }
    }

    // ── veilig ──────────────────────────────────────────────────────────────────────────────

    /** Een vloer van 9×9 (glas waar geen vaste grond is) op de spawn, met lucht erboven; geeft de plek erop. */
    private Location maakVloer() {
        World w = Bukkit.getWorlds().get(0);
        File marker = new File(getDataFolder(), "gebouwd.txt");
        if (marker.exists()) {   // lobby staat al: grondhoogte uit de vorige bouw (de fontein mag die niet verschuiven)
            try {
                grondY = Integer.parseInt(java.nio.file.Files.readString(marker.toPath()).trim().split(":")[1]);
                return new Location(w, 0.5, grondY + 1, 9.5, 180f, 0f);
            } catch (Exception e) {
                getLogger().warning("gebouwd.txt onleesbaar: " + e.getMessage());
            }
        }
        Location s = w.getSpawnLocation();
        int y = Math.max(w.getMinHeight() + 2, Math.min(w.getMaxHeight() - 20, w.getHighestBlockYAt(s)));
        grondY = y;
        int bx = s.getBlockX(), bz = s.getBlockZ();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Block vloer = w.getBlockAt(bx + dx, y, bz + dz);
                if (!vloer.getType().isSolid()) vloer.setType(Material.GLASS, false);
                for (int dy = 1; dy <= 3; dy++) {
                    Block lucht = w.getBlockAt(bx + dx, y + dy, bz + dz);
                    if (!lucht.getType().isAir()) lucht.setType(Material.AIR, false);
                }
            }
        }
        Location plek = new Location(w, bx + 0.5, y + 1, bz + 0.5, -90f, 0f);
        w.setSpawnLocation(plek);
        return plek;
    }

    private void maakVeilig(Player p) {
        p.setInvulnerable(true);
        if (p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setRemainingAir(p.getMaximumAir());
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (!p.isDead() && max != null && p.getHealth() < max.getValue()) p.setHealth(max.getValue());
    }

    // ── lobby: plaza, lanen en parkour-levels ───────────────────────────────────────────────

    /** Eén stap van een baan. t: B blok, I ijs, S slijm, H honing, L ladderpilaar (h hoog), W waterbad,
     *  C plafond (hoofdstoter) boven het huidige blok, N muurtje om omheen te springen (neo). dx = vooruit, dz = opzij. */
    private record Stap(char t, int dx, int dy, int dz, int h) {}

    private record Level(String naam, String moeite, Material blok, Stap[] stappen) {}

    private static Stap b(int dx, int dy, int dz) { return new Stap('B', dx, dy, dz, 0); }
    private static Stap ijs(int dx, int dy, int dz) { return new Stap('I', dx, dy, dz, 0); }
    private static Stap slijm(int dx, int dy, int dz) { return new Stap('S', dx, dy, dz, 0); }
    private static Stap honing(int dx, int dy, int dz) { return new Stap('H', dx, dy, dz, 0); }
    private static Stap ladder(int dx, int dz, int h) { return new Stap('L', dx, 0, dz, h); }
    private static Stap water(int dx, int dy, int dz) { return new Stap('W', dx, dy, dz, 0); }
    private static final Stap PLAFOND = new Stap('C', 0, 0, 0, 0);
    private static final Stap MUUR = new Stap('N', 0, 0, 0, 0);

    private static final Level[] LEVELS = {
            new Level("Opwarmer", "★", Material.LIGHT_BLUE_CONCRETE, new Stap[]{
                    b(2, 0, 0), b(2, 0, 0), b(2, 1, 0), b(2, 0, 1), b(2, 0, -1), b(2, 1, 0), b(2, 0, 0), b(2, 1, 1), b(2, 0, -1), b(2, 0, 0)}),
            new Level("Sprongen", "★★", Material.LIME_TERRACOTTA, new Stap[]{
                    b(3, 0, 0), b(3, 0, 0), b(2, 1, 1), b(3, 0, 0), b(2, 1, -1), b(3, 0, 1), b(3, 0, 0), b(2, 1, 0), b(3, 0, -1), b(4, 0, 0), b(3, 0, 0)}),
            new Level("Zigzag", "★★", Material.ORANGE_CONCRETE, new Stap[]{
                    b(2, 0, 2), b(2, 0, -2), b(2, 1, 2), b(3, 0, -1), b(2, 0, -2), b(2, 1, 2), b(3, 0, 0), b(2, 0, -2), b(2, 1, 2), b(3, 0, -1), b(3, 0, 0)}),
            new Level("IJsbaan", "★★★", Material.PACKED_ICE, new Stap[]{
                    ijs(2, 0, 0), ijs(1, 0, 0), ijs(1, 0, 0), ijs(4, 0, 0), ijs(1, 0, 0), ijs(1, 0, 0), b(4, 1, 0),
                    ijs(2, 0, 1), ijs(1, 0, 0), ijs(1, 0, 0), ijs(4, 0, 0), ijs(1, 0, -1), b(3, 1, 0)}),
            new Level("Ladders", "★★★", Material.YELLOW_CONCRETE, new Stap[]{
                    b(2, 0, 0), ladder(2, 0, 4), b(3, 0, 0), b(2, -1, 1), ladder(2, 0, 3), b(3, 0, -1), b(3, 0, 0),
                    ladder(2, 0, 4), b(2, 0, 2), b(3, -1, 0), b(3, 0, 0)}),
            new Level("Honing", "★★★", Material.HONEYCOMB_BLOCK, new Stap[]{
                    honing(2, 0, 0), honing(2, 0, 0), honing(2, 0, 1), b(2, 0, 0), PLAFOND, b(3, 0, 0), honing(2, 0, -1),
                    honing(2, 0, 0), b(2, 0, 0), b(2, 1, 0), PLAFOND, b(3, 0, 0), honing(2, 0, 1), b(2, 0, 0), b(2, 1, 0)}),
            new Level("Slijm", "★★★★", Material.GREEN_CONCRETE, new Stap[]{
                    b(2, 1, 0), b(2, 1, 0), b(2, 1, 0), b(2, 1, 0), slijm(2, -4, 0), b(3, 2, 0), b(2, 1, 0), b(2, 1, 0),
                    b(2, 1, 1), slijm(2, -5, 0), b(3, 2, 0), b(2, 0, -1), b(3, 0, 0)}),
            new Level("Waterval", "★★★★", Material.CYAN_CONCRETE, new Stap[]{
                    b(2, 0, 0), ladder(2, 0, 6), b(2, 0, 0), b(2, 1, 0), water(3, -5, 0), b(2, 0, 0), b(2, 1, 0),
                    b(2, 1, 1), b(3, 0, 0), b(2, 1, -1), b(3, 0, 0)}),
            new Level("Neo", "★★★★", Material.PURPLE_CONCRETE, new Stap[]{
                    b(2, 0, 0), MUUR, b(2, 0, 0), b(3, 0, 1), b(2, 1, 0), MUUR, b(2, 0, 0), b(3, 0, 0), b(3, 1, 1), MUUR,
                    b(2, 0, 0), b(4, 0, 0), b(3, 0, 0)}),
            new Level("Meester", "★★★★★", Material.RED_CONCRETE, new Stap[]{
                    b(3, 1, 0), b(4, 0, 1), honing(2, 0, 0), honing(2, 0, 0), b(2, 0, 1), PLAFOND, b(4, 0, 0), ladder(2, 0, 4),
                    b(4, 0, -1), MUUR, b(2, 0, 0), b(3, 1, 1), ijs(3, 0, 0), ijs(1, 0, 0), ijs(1, 0, 0), b(5, 0, 0),
                    b(3, 1, -1), b(4, 0, 0)}),
    };
    /** Levels 1-5 oost (+x), 6-10 west (−x); z van de laan. */
    private static final int[] LAAN_Z = {-40, -20, 0, 20, 40};
    private static final int HUT_X = 24;       // binnenkant laan: hut van x=24..30 (oost) / −30..−24 (west)
    private static final int AVENUE_X = 19;    // laan-boulevard x=19..21
    private static final int BOUW_VERSIE = 3;  // ophogen = alles opnieuw bouwen bij de volgende lobbystart

    private int vloerY;                                   // y van de vloer in de huisjes = y van de startblokken
    private Location[][] banen;                           // per level de sta-plek op elk blok
    private Location[] huisje;                            // per level de plek in het huisje
    private final Map<Long, int[]> blokNaarPunt = new HashMap<>();
    private final Map<UUID, Integer> bezigLevel = new HashMap<>();
    private YamlConfiguration voortgang;
    private File voortgangBestand;
    private boolean plaats = true;                        // false = alleen uitrekenen (staat al gebouwd)

    private static long sleutel(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private void zet(World w, int x, int y, int z, Material m) {
        if (plaats) w.getBlockAt(x, y, z).setType(m, false);
    }

    private void zet(World w, int x, int y, int z, org.bukkit.block.data.BlockData d) {
        if (plaats) w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    private void vul(World w, int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        if (!plaats) return;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    Block bl = w.getBlockAt(x, y, z);
                    if (bl.getType() != m) bl.setType(m, false);
                }
    }

    private void bouwParkour() {
        World w = veilig.getWorld();
        File marker = new File(getDataFolder(), "gebouwd.txt");
        String versie = BOUW_VERSIE + ":" + grondY;
        try {
            plaats = !marker.exists() || !java.nio.file.Files.readString(marker.toPath()).trim().equals(versie);
        } catch (IOException e) {
            plaats = true;
        }
        long t0 = System.currentTimeMillis();
        vloerY = grondY + 1;
        if (plaats) {
            vul(w, -90, grondY + 1, -60, 95, grondY + 30, 60, Material.AIR);   // oude bouwsels weg
            vul(w, -90, grondY, -60, 95, grondY, 60, Material.GRASS_BLOCK);
        }
        bouwPlaza(w);
        banen = new Location[LEVELS.length][];
        huisje = new Location[LEVELS.length];
        blokNaarPunt.clear();
        for (int l = 0; l < LEVELS.length; l++) bouwLevel(w, l);
        bouwBoulevards(w);
        Location spawn = new Location(w, 0.5, grondY + 1, 9.5, 180f, 0f);
        w.setSpawnLocation(spawn);
        veilig = spawn;
        if (plaats) {
            try {
                java.nio.file.Files.writeString(marker.toPath(), versie);
            } catch (IOException e) {
                getLogger().warning("gebouwd.txt: " + e.getMessage());
            }
            getLogger().info("lobby gebouwd in " + (System.currentTimeMillis() - t0) + " ms");
        }
    }

    /** Ronde plaza (straal 13) met fontein, lantaarnpalen, bankjes, bloemen en bomen. */
    private void bouwPlaza(World w) {
        int g = grondY;
        for (int x = -13; x <= 13; x++)
            for (int z = -13; z <= 13; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 13.4) continue;
                Material m = r > 12.4 ? Material.POLISHED_ANDESITE
                        : ((int) Math.round(r)) % 4 == 0 ? Material.CHISELED_STONE_BRICKS
                        : (Math.abs(x) + Math.abs(z)) % 2 == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE;
                zet(w, x, g, z, m);
            }
        // fontein: rand op straal 4, water binnenin, zuil met zeelantaarn en water erop
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= 3.4) {
                    zet(w, x, g - 1, z, Material.SEA_LANTERN);
                    zet(w, x, g, z, Material.WATER);
                } else if (r <= 4.4) {
                    zet(w, x, g, z, Material.QUARTZ_BRICKS);
                    zet(w, x, g + 1, z, Material.SMOOTH_QUARTZ_SLAB);
                }
            }
        vul(w, 0, g, 0, 0, g + 3, 0, Material.QUARTZ_PILLAR);
        zet(w, 0, g + 4, 0, Material.SEA_LANTERN);
        zet(w, 0, g + 5, 0, Material.WATER);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) zet(w, d[0], g + 4, d[1], Material.WATER);
        // lantaarnpalen op straal 10
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * 10), z = (int) Math.round(Math.sin(a) * 10);
            lantaarnpaal(w, x, z);
        }
        // bankjes en bloembakken tussen de palen
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            int x = (int) Math.round(Math.cos(a) * 8), z = (int) Math.round(Math.sin(a) * 8);
            if (Math.abs(z) <= 1 && Math.abs(x) > 6) continue; // doorgang naar de boulevards
            if (i % 2 == 0) {
                zet(w, x, g + 1, z, Material.SPRUCE_SLAB);
            } else {
                zet(w, x, g + 1, z, Material.MOSS_BLOCK);
                zet(w, x, g + 2, z, i % 4 == 1 ? Material.AZURE_BLUET : Material.ALLIUM);
            }
        }
        // bloemen en bomen rond de plaza
        Material[] bloemen = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.ALLIUM, Material.PINK_TULIP};
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 / 48 * i;
            int x = (int) Math.round(Math.cos(a) * 14.5), z = (int) Math.round(Math.sin(a) * 14.5);
            if (Math.abs(z) <= 2) continue;
            zet(w, x, g + 1, z, bloemen[i % bloemen.length]);
        }
        for (int[] p : new int[][]{{-16, -11}, {-16, 11}, {16, -11}, {16, 11}, {-8, -16}, {8, -16}, {-8, 16}, {8, 16}}) {
            boom(w, p[0], p[1], (p[0] + p[1]) % 3 == 0 ? org.bukkit.TreeType.CHERRY : org.bukkit.TreeType.BIRCH);
        }
    }

    private void lantaarnpaal(World w, int x, int z) {
        zet(w, x, grondY + 1, z, Material.ANDESITE_WALL);
        vul(w, x, grondY + 2, z, x, grondY + 3, z, Material.DARK_OAK_FENCE);
        zet(w, x, grondY + 4, z, Material.LANTERN);
    }

    private void boom(World w, int x, int z, org.bukkit.TreeType type) {
        if (!plaats) return;
        zet(w, x, grondY, z, Material.GRASS_BLOCK);
        if (!w.generateTree(new Location(w, x, grondY + 1, z), new java.util.Random(x * 31L + z), type)) {
            w.generateTree(new Location(w, x, grondY + 1, z), new java.util.Random(x * 31L + z), org.bukkit.TreeType.TREE);
        }
    }

    /** Boulevards oost en west (3 breed) van z=−46..46 met lantaarns en bomen, paden naar de plaza en de huizen. */
    private void bouwBoulevards(World w) {
        int g = grondY;
        for (int sx : new int[]{1, -1}) {
            int x1 = sx * AVENUE_X, x2 = sx * (AVENUE_X + 2);
            vul(w, x1, g, -46, x2, g, 46, Material.STONE_BRICKS);
            vul(w, sx * (AVENUE_X + 1), g, -46, sx * (AVENUE_X + 1), g, 46, Material.POLISHED_ANDESITE);
            vul(w, sx * 13, g, -1, sx * (AVENUE_X - 1), g, 1, Material.STONE_BRICKS);          // pad plaza → boulevard
            for (int z = -44; z <= 44; z += 8) {
                lantaarnpaal(w, sx * (AVENUE_X - 1), z);
                lantaarnpaal(w, sx * (AVENUE_X + 3), z + 4);
            }
            for (int z = -36; z <= 36; z += 16) {
                if (Math.abs(z) < 6) continue;
                boom(w, sx * (AVENUE_X - 4), z + 6, z % 32 == 0 ? org.bukkit.TreeType.CHERRY : org.bukkit.TreeType.TREE);
            }
            for (int lz : LAAN_Z) vul(w, sx * (AVENUE_X + 3), g, lz - 1, sx * (HUT_X - 1), g, lz + 1, Material.DIRT_PATH);
        }
    }

    /** Huis (7×7, schilddak) met deur naar de boulevard en deur naar de baan, ramen, lantaarns en inrichting. */
    private void bouwHuis(World w, int sx, int zc, int l) {
        Material[] planken = {Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.CHERRY_PLANKS,
                Material.DARK_OAK_PLANKS, Material.JUNGLE_PLANKS, Material.MANGROVE_PLANKS, Material.ACACIA_PLANKS,
                Material.BAMBOO_PLANKS, Material.CRIMSON_PLANKS};
        Material[] stammen = {Material.STRIPPED_OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.CHERRY_LOG,
                Material.DARK_OAK_LOG, Material.JUNGLE_LOG, Material.MANGROVE_LOG, Material.ACACIA_LOG,
                Material.STRIPPED_BAMBOO_BLOCK, Material.CRIMSON_STEM};
        Material[] daken = {Material.RED_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.PINK_TERRACOTTA,
                Material.BLACK_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.YELLOW_TERRACOTTA,
                Material.LIME_TERRACOTTA, Material.PURPLE_TERRACOTTA};
        Material plank = planken[l], stam = stammen[l], dak = daken[l];
        int xa = sx * HUT_X, xb = sx * (HUT_X + 6);
        int x1 = Math.min(xa, xb), x2 = Math.max(xa, xb), z1 = zc - 3, z2 = zc + 3, y = vloerY;
        vul(w, x1, y - 1, z1, x2, y - 1, z2, Material.COBBLESTONE);
        vul(w, x1, y, z1, x2, y, z2, plank);                                 // vloer
        vul(w, x1, y + 1, z1, x2, y + 4, z2, plank);                         // wanden
        vul(w, x1 + 1, y + 1, z1 + 1, x2 - 1, y + 4, z2 - 1, Material.AIR);
        for (int[] c : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) vul(w, c[0], y + 1, c[1], c[0], y + 4, c[1], stam);
        vul(w, x1, y + 1, zc, x1, y + 2, zc, Material.AIR);                  // deuren
        vul(w, x2, y + 1, zc, x2, y + 2, zc, Material.AIR);
        for (int x : new int[]{x1 + 2, x2 - 2}) {                            // ramen
            vul(w, x, y + 2, z1, x, y + 3, z1, Material.GLASS_PANE);
            vul(w, x, y + 2, z2, x, y + 3, z2, Material.GLASS_PANE);
        }
        for (int i = 0; i < 4; i++) vul(w, x1 - 1 + i, y + 5 + i, z1 - 1 + i, x2 + 1 - i, y + 5 + i, z2 + 1 - i, dak); // dak
        zet(w, (x1 + x2) / 2, y + 9, zc, Material.LANTERN);
        // inrichting
        zet(w, x1 + 1, y + 1, z1 + 1, Material.BOOKSHELF);
        zet(w, x1 + 1, y + 2, z1 + 1, Material.LANTERN);
        zet(w, x2 - 1, y + 1, z1 + 1, Material.CRAFTING_TABLE);
        zet(w, x1 + 1, y + 1, z2 - 1, Material.POTTED_RED_TULIP);
        zet(w, x2 - 1, y + 1, z2 - 1, Material.BARREL);
        zet(w, x2 - 1, y + 2, z2 - 1, Material.LANTERN);
        vul(w, x1 + 2, y + 1, zc - 1, x2 - 2, y + 1, zc + 1, Material.AIR);
        for (int x = x1 + 1; x <= x2 - 1; x++) zet(w, x, y + 1, zc, Material.RED_CARPET);
        zet(w, x1 + 3, y + 4, zc, Material.GLOWSTONE);
        // opstapjes en lantaarns bij de deuren
        zet(w, x1 - 1, y, zc, Material.STONE_BRICK_SLAB);
        zet(w, x2 + 1, y, zc, Material.STONE_BRICK_SLAB);
        lantaarnpaal(w, x1 - 1, zc - 2);
        lantaarnpaal(w, x1 - 1, zc + 2);
    }

    private void bouwLevel(World w, int l) {
        Level lv = LEVELS[l];
        int sx = l < 5 ? 1 : -1;
        int zc = LAAN_Z[l % 5];
        bouwHuis(w, sx, zc, l);
        huisje[l] = new Location(w, sx * (HUT_X + 3) + 0.5, vloerY + 1, zc + 0.5, sx > 0 ? -90f : 90f, 0f);
        java.util.List<Location> punten = new java.util.ArrayList<>();
        int x = sx * (HUT_X + 7), y = vloerY, z = zc;     // startblok vlak buiten de deur
        org.bukkit.block.BlockFace ladderKant = sx > 0 ? org.bukkit.block.BlockFace.WEST : org.bukkit.block.BlockFace.EAST;
        punt(w, x, y, z, Material.LIME_CONCRETE, l, punten);
        Stap[] st = lv.stappen();
        for (int s = 0; s < st.length; s++) {
            Stap k = st[s];
            boolean laatste = s == st.length - 1;
            switch (k.t()) {
                case 'C' -> zet(w, x, y + 3, z, lv.blok() == Material.HONEYCOMB_BLOCK ? Material.OAK_PLANKS : lv.blok());
                case 'N' -> {
                    vul(w, x + sx, y + 1, z, x + sx, y + 3, z, Material.STONE_BRICKS);
                    vul(w, x + sx, y + 1, z - 1, x + sx, y + 3, z - 1, Material.STONE_BRICKS);
                }
                case 'L' -> {
                    int px = x + sx * k.dx(), pz = z + k.dz();
                    vul(w, px, y, pz, px, y + k.h(), pz, lv.blok());
                    org.bukkit.block.data.type.Ladder ld = (org.bukkit.block.data.type.Ladder) Material.LADDER.createBlockData();
                    ld.setFacing(ladderKant);
                    for (int yy = y + 1; yy <= y + k.h(); yy++) zet(w, px - sx, yy, pz, ld);
                    x = px;
                    z = pz;
                    y = y + k.h();
                    punt(w, x, y, z, null, l, punten);
                }
                case 'W' -> {
                    x += sx * k.dx();
                    y += k.dy();
                    z += k.dz();
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++) {
                            zet(w, x + dx, y - 1, z + dz, Material.GLASS);
                            boolean rand = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                            zet(w, x + dx, y, z + dz, rand ? Material.GLASS : Material.WATER);
                            vul(w, x + dx, y + 1, z + dz, x + dx, y + 3, z + dz, Material.AIR);
                        }
                }
                default -> {
                    x += sx * k.dx();
                    y += k.dy();
                    z += k.dz();
                    Material m = laatste ? Material.EMERALD_BLOCK : switch (k.t()) {
                        case 'I' -> Material.BLUE_ICE;
                        case 'S' -> Material.SLIME_BLOCK;
                        case 'H' -> Material.HONEY_BLOCK;
                        default -> lv.blok();
                    };
                    punt(w, x, y, z, m, l, punten);
                    if (laatste) bouwFinish(w, x, y, z);
                }
            }
        }
        banen[l] = punten.toArray(new Location[0]);
        label(new Location(w, sx * (HUT_X + 3) + 0.5, vloerY + 11, zc + 0.5),
                Component.text("Level " + (l + 1), NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.newline()).append(Component.text(lv.naam(), NamedTextColor.WHITE))
                        .append(Component.newline()).append(Component.text(lv.moeite(), NamedTextColor.YELLOW)));
        label(banen[l][0].clone().add(0, 1.3, 0), Component.text("Start", NamedTextColor.GREEN, TextDecoration.BOLD));
        label(banen[l][banen[l].length - 1].clone().add(0, 1.8, 0), Component.text("Finish level " + (l + 1), NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    private void punt(World w, int x, int y, int z, Material m, int l, java.util.List<Location> punten) {
        if (m != null) {
            vul(w, x, y + 1, z, x, y + 3, z, Material.AIR);
            zet(w, x, y, z, m);
        }
        blokNaarPunt.put(sleutel(x, y, z), new int[]{l, punten.size()});
        punten.add(new Location(w, x + 0.5, y + 1, z + 0.5, 0f, 0f));
    }

    private void bouwFinish(World w, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                zet(w, x + dx, y, z + dz, Material.QUARTZ_BLOCK);
                vul(w, x + dx, y + 1, z + dz, x + dx, y + 3, z + dz, Material.AIR);
            }
        for (int[] c : new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}}) {
            zet(w, x + c[0], y + 1, z + c[1], Material.OAK_FENCE);
            zet(w, x + c[0], y + 2, z + c[1], Material.LANTERN);
        }
    }

    private void label(Location plek, Component tekst) {
        plek.getWorld().spawn(plek, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(tekst);
            d.addScoreboardTag("lobbymelding");
        });
    }

    private int[] puntOnder(Location l) {
        return blokNaarPunt.get(sleutel(l.getBlockX(), (int) Math.floor(l.getY() - 0.01), l.getBlockZ()));
    }

    private int gehaald(UUID id) {
        return voortgang == null ? 0 : voortgang.getInt(id.toString(), 0);
    }

    private void slaGehaaldOp(UUID id, int level) {
        if (voortgang == null || level <= gehaald(id)) return;
        voortgang.set(id.toString(), level);
        try {
            voortgang.save(voortgangBestand);
        } catch (IOException e) {
            getLogger().warning("voortgang niet op te slaan: " + e.getMessage());
        }
    }

    /** Naar het huisje van het level waar de speler aan toe is (gehaald + 1); alles gehaald = laatste level. */
    private void naarVolgendLevel(Player p) {
        if (huisje == null) return;
        int l = Math.min(gehaald(p.getUniqueId()), LEVELS.length - 1);
        p.teleport(huisje[l]);
        boolean alles = gehaald(p.getUniqueId()) >= LEVELS.length;
        p.sendMessage(Component.text(alles ? "Alle levels gehaald! Probeer ze nog eens sneller (huisjes langs de spawn)."
                : "Parkour level " + (l + 1) + ": " + LEVELS[l].naam() + ". Loop het huisje uit en spring op het groene blok.", NamedTextColor.AQUA));
    }

    private static String tijd(long ms) {
        return String.format("%d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location to = e.getTo();
        if (veilig == null) return;
        if (to.getY() < grondY - 8) {
            p.teleport(veilig);
            return;
        }
        if (banen == null || e.getFrom().getBlock().equals(to.getBlock()) && e.getFrom().getY() == to.getY()) return;
        UUID id = p.getUniqueId();
        Integer bezig = bezigLevel.get(id);
        if (bezig != null && p.isOnGround() && to.getY() < vloerY + 0.5) { // gevallen: terug naar de start van het level
            Location terug = banen[bezig][0].clone();
            terug.setYaw(to.getYaw());
            p.teleport(terug);
            startTijd.put(id, System.currentTimeMillis());
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
            return;
        }
        if (!p.isOnGround()) return;
        int[] punt = puntOnder(to);
        if (punt == null) return;
        int l = punt[0], i = punt[1];
        if (i == 0) {
            if (bezig == null || bezig != l || laatstePunt.getOrDefault(id, 0) > 0) {
                bezigLevel.put(id, l);
                startTijd.put(id, System.currentTimeMillis());
                laatstePunt.put(id, 0);
                p.sendMessage(Component.text("Level " + (l + 1) + " gestart! Val je, dan begin je opnieuw.", NamedTextColor.AQUA));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
            }
            return;
        }
        if (bezig == null || bezig != l) return;
        laatstePunt.put(id, Math.max(laatstePunt.getOrDefault(id, 0), i));
        if (i == banen[l].length - 1) {
            long ms = System.currentTimeMillis() - startTijd.remove(id);
            bezigLevel.remove(id);
            laatstePunt.remove(id);
            boolean nieuw = l + 1 > gehaald(id);
            slaGehaaldOp(id, l + 1);
            Bukkit.broadcast(Component.text("[", NamedTextColor.GRAY).append(Component.text("Parkour", NamedTextColor.GREEN))
                    .append(Component.text("] ", NamedTextColor.GRAY)).append(p.displayName().colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(" haalde level " + (l + 1) + " (" + LEVELS[l].naam() + ") in ", NamedTextColor.GRAY))
                    .append(Component.text(tijd(ms), NamedTextColor.WHITE)));
            p.showTitle(Title.title(Component.text("Level " + (l + 1) + " gehaald!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(tijd(ms) + (nieuw && l + 1 < LEVELS.length ? " · volgende keer level " + (l + 2) : ""), NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            if (l + 1 < LEVELS.length) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (p.isOnline() && !aftellend.contains(id) && !bezigLevel.containsKey(id)) naarVolgendLevel(p);
                }, 60L);
            }
        }
    }

    // ── aftellen en terug naar survival ─────────────────────────────────────────────────────

    private static final TextColor[] AFTEL_KLEUR = {NamedTextColor.AQUA, NamedTextColor.GREEN, NamedTextColor.YELLOW,
            NamedTextColor.GOLD, NamedTextColor.RED};

    private void aftellen(int seconden) {
        aftellen(seconden, Bukkit.getOnlinePlayers().stream()
                .filter(p -> !blijft.contains(p.getUniqueId())).map(p -> (Player) p).toList());
    }

    private void aftellen(int seconden, List<Player> spelers) {
        spelers = spelers.stream().filter(p -> !aftellend.contains(p.getUniqueId())).toList();
        if (spelers.isEmpty()) return;
        final List<Player> lijst = spelers;
        lijst.forEach(p -> aftellend.add(p.getUniqueId()));
        for (int i = 0; i <= seconden; i++) {
            final int n = seconden - i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player p : lijst) {
                    if (!p.isOnline()) continue;
                    if (n > 0) {
                        TextColor kleur = AFTEL_KLEUR[Math.min(n, AFTEL_KLEUR.length) - 1];
                        p.showTitle(Title.title(Component.text(String.valueOf(n), kleur, TextDecoration.BOLD),
                                Component.text("Survival is er weer!", NamedTextColor.GRAY),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(150))));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f + 0.15f * (seconden - n));
                    } else if (blijft.contains(p.getUniqueId())) {
                        p.showTitle(Title.title(Component.text("✔ Survival is weer open", NamedTextColor.GREEN, TextDecoration.BOLD),
                                Component.text("Je blijft in de lobby • klik het blokje om terug te gaan", NamedTextColor.GRAY),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(500))));
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    } else {
                        p.showTitle(Title.title(Component.text("Daar ga je!", NamedTextColor.GREEN, TextDecoration.BOLD),
                                Component.empty(),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                        stuurNaar(p, getConfig().getString("terug-naar", "survival"));
                    }
                }
                if (n == 0) lijst.forEach(p -> aftellend.remove(p.getUniqueId()));
            }, i * 20L);
        }
    }

    private void stuurNaar(Player p, String server) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buf);
            out.writeUTF("Connect");
            out.writeUTF(server);
            p.sendPluginMessage(this, KANAAL, buf.toByteArray());
        } catch (IOException e) {
            getLogger().warning("terugsturen mislukt: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!actief) {
            sender.sendMessage("LobbyMelding is hier niet actief.");
            return true;
        }
        String naam = command.getName().toLowerCase();
        if (naam.equals("blijf") || naam.equals("terug")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Alleen voor spelers.");
                return true;
            }
            zetBlijven(p, naam.equals("blijf"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("terug")) {
            int s = getConfig().getInt("aftellen", 5);
            boolean alle = args.length >= 2 && args[1].equalsIgnoreCase("alle"); // ook wie blijft: alleen het moment, geen teleport
            if (alle) aftellen(Math.max(0, Math.min(10, s)), List.copyOf(Bukkit.getOnlinePlayers()));
            else aftellen(Math.max(0, Math.min(10, s)));
            sender.sendMessage("aftellen " + s + " s gestart voor " + Bukkit.getOnlinePlayers().size() + " spelers");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("wie-terug")) {
            sender.sendMessage("terug: " + String.join(",", Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !blijft.contains(p.getUniqueId())).map(Player::getName).toList()));
            return true;
        }
        sender.sendMessage("Gebruik: /lobbymelding terug | wie-terug");
        return true;
    }

    // ── blijven/terug: commando's en het wol-item in de hotbar ─────────────────────────────

    private static final int ITEM_SLOT = 4; // vak 5
    private NamespacedKey itemKey;

    private ItemStack blijfItem(boolean aan) {
        ItemStack it = new ItemStack(aan ? Material.RED_WOOL : Material.LIME_WOOL);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(aan ? "Blijven: aan – klik om terug te gaan" : "Blijven: uit – klik om te blijven",
                aan ? NamedTextColor.RED : NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text(aan ? "Je blijft in de lobby tot je terug wilt (/terug)."
                : "Je gaat vanzelf terug naar survival zodra die klaar is (/blijf om te blijven).", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isBlijfItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey);
    }

    private void geefItem(Player p) {
        boolean aan = blijft.contains(p.getUniqueId());
        ItemStack huidig = p.getInventory().getItem(ITEM_SLOT);
        Material want = aan ? Material.RED_WOOL : Material.LIME_WOOL;
        if (isBlijfItem(huidig) && huidig.getType() == want) return;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            if (i != ITEM_SLOT && isBlijfItem(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
        }
        p.getInventory().setItem(ITEM_SLOT, blijfItem(aan));
    }

    private File blijfBestand() {
        return new File(getDataFolder(), "blijvers.txt");
    }

    private void bewaarBlijvers() {
        try {
            java.nio.file.Files.writeString(blijfBestand().toPath(),
                    String.join("\n", blijft.stream().map(UUID::toString).toList()) + "\n");
        } catch (IOException e) {
            getLogger().warning("blijvers.txt: " + e.getMessage());
        }
    }

    private void leesBlijvers() {
        try {
            if (!blijfBestand().exists()) return;
            for (String r : java.nio.file.Files.readAllLines(blijfBestand().toPath())) {
                try {
                    if (!r.isBlank()) blijft.add(UUID.fromString(r.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException e) {
            getLogger().warning("blijvers.txt: " + e.getMessage());
        }
    }

    /** Blijven aan (= /blijf) of uit (= /terug: als survival er is meteen aftellen en terug). */
    private void zetBlijven(Player p, boolean aan) {
        UUID id = p.getUniqueId();
        if (aan) {
            blijft.add(id);
            bewaarBlijvers();
            p.sendMessage(Component.text("Je blijft in de lobby, ook als survival weer klaar is. Typ /terug of klik op de wol als je terug wilt.", NamedTextColor.GREEN));
            geefItem(p);
            return;
        }
        blijft.remove(id);
        bewaarBlijvers();
        geefItem(p);
        String host = getConfig().getString("survival-host", "127.0.0.1");
        int poort = getConfig().getInt("survival-poort", 30067);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean up;
            try (java.net.Socket sock = new java.net.Socket()) {
                sock.connect(new java.net.InetSocketAddress(host, poort), 800);
                up = true;
            } catch (IOException e) {
                up = false;
            }
            final boolean survivalUp = up;
            Bukkit.getScheduler().runTask(this, () -> {
                if (!p.isOnline() || blijft.contains(id)) return;
                if (survivalUp) {
                    startTijd.remove(id);
                    bezigLevel.remove(id);
                    aftellen(Math.max(1, Math.min(10, getConfig().getInt("aftellen", 5))), List.of(p));
                } else {
                    p.sendMessage(Component.text("Survival herstart nog; je gaat vanzelf terug zodra die klaar is.", NamedTextColor.YELLOW));
                }
            });
        });
    }

    @EventHandler
    public void onKlikItem(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isBlijfItem(e.getItem())) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        zetBlijven(e.getPlayer(), !blijft.contains(e.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onInventoryKlik(InventoryClickEvent e) {
        if (isBlijfItem(e.getCurrentItem()) || isBlijfItem(e.getCursor())
                || (e.getHotbarButton() == ITEM_SLOT) || (e.getClickedInventory() == e.getWhoClicked().getInventory() && e.getSlot() == ITEM_SLOT)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSleep(InventoryDragEvent e) {
        if (isBlijfItem(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isBlijfItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onWissel(PlayerSwapHandItemsEvent e) {
        if (isBlijfItem(e.getMainHandItem()) || isBlijfItem(e.getOffHandItem())) e.setCancelled(true);
    }

    /** Kwam deze speler net via /parkour uit survival? (bezoekers.txt, geschreven door de plugin lobbyreis) */
    private boolean isBezoeker(UUID id) {
        File f = new File(getDataFolder(), "bezoekers.txt");
        if (!f.exists()) return false;
        boolean gevonden = false;
        StringBuilder rest = new StringBuilder();
        long nu = System.currentTimeMillis();
        try {
            for (String regel : java.nio.file.Files.readAllLines(f.toPath())) {
                String[] d = regel.trim().split(" ");
                if (d.length != 2) continue;
                long t;
                try {
                    t = Long.parseLong(d[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (nu - t > 120_000L) continue;
                if (d[0].equals(id.toString())) gevonden = true;
                else rest.append(regel.trim()).append('\n');
            }
            java.nio.file.Files.writeString(f.toPath(), rest.toString());
        } catch (IOException e) {
            getLogger().warning("bezoekers.txt: " + e.getMessage());
        }
        return gevonden;
    }

    // ── binnenkomen ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player speler = event.getPlayer();
        maakVeilig(speler);
        if (veilig != null) speler.teleport(veilig);
        boolean bezoeker = isBezoeker(speler.getUniqueId());
        if (bezoeker) {
            blijft.add(speler.getUniqueId());
            bewaarBlijvers();
        }
        geefItem(speler);
        if (bezoeker) {
            speler.sendMessage(Component.text("Welkom bij de parkour! Typ /terug om terug te gaan naar je plek in survival.", NamedTextColor.AQUA));
            if (banen != null) naarVolgendLevel(speler);
            return;
        }
        Component titel = Component.text(getConfig().getString("titel", "Even geduld"), NamedTextColor.GOLD);
        Component onder = Component.text(getConfig().getString("ondertitel", ""), NamedTextColor.GRAY);
        speler.showTitle(Title.title(titel, onder,
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(8), Duration.ofMillis(800))));
        String bericht = getConfig().getString("bericht", "");
        if (!bericht.isBlank()) {
            speler.sendMessage(Component.text(bericht, NamedTextColor.YELLOW));
        }
        if (banen != null) naarVolgendLevel(speler);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        startTijd.remove(e.getPlayer().getUniqueId());
        bezigLevel.remove(e.getPlayer().getUniqueId());
        if (!Bukkit.isStopping() && blijft.remove(e.getPlayer().getUniqueId())) bewaarBlijvers(); // bij een lobbyherstart onthouden
        laatstePunt.remove(e.getPlayer().getUniqueId());
        aftellend.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID && veilig != null) p.teleport(veilig);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHonger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (veilig != null) e.setRespawnLocation(veilig);
    }
}
