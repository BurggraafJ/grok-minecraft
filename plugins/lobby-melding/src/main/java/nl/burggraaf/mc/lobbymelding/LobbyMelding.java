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
import org.bukkit.plugin.messaging.PluginMessageListener;

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
public final class LobbyMelding extends JavaPlugin implements Listener, PluginMessageListener {

    private static final String KANAAL = "BungeeCord";
    private static final String NETWERK = "netwerk:main";
    /** Wat de Velocity-plugin netwerk over een speler zegt: "aftellen:<server>:<Naam>" of "wacht:<server>:<Naam>". */
    private final Map<UUID, String> modus = new HashMap<>();
    private boolean netwerkGezien = false;
    private final Map<UUID, Long> poortTot = new HashMap<>();
    /** Korte hint in de actionbar (na annuleren), tot dit tijdstip; daarna is de actionbar stil. */
    private final Map<UUID, Long> hintTot = new HashMap<>();
    private Tuin tuin;

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
        kiezerKey = new NamespacedKey(this, "level-kiezer");
        resetKey = new NamespacedKey(this, "reset-item");
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
        getServer().getMessenger().registerOutgoingPluginChannel(this, NETWERK);
        getServer().getMessenger().registerIncomingPluginChannel(this, NETWERK, this);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, () -> {
            veilig = maakVloer();
            if (getConfig().getBoolean("parkour", true)) bouwParkour();
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (UUID id : new java.util.ArrayList<>(bezigLevel.keySet())) {
                    Player q = Bukkit.getPlayer(id);
                    if (q == null) bezigLevel.remove(id);
                    else controleerVal(q);
                }
            }, 2L, 2L);
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                maakVeilig(p);
                geefItem(p);
                geefKiezer(p);
                if (aftellend.contains(p.getUniqueId())) continue;
                Long t = startTijd.get(p.getUniqueId());
                String m = modus.get(p.getUniqueId());
                if (t != null) {
                    p.sendActionBar(Component.text("Parkour ", NamedTextColor.AQUA)
                            .append(Component.text(tijd(System.currentTimeMillis() - t), NamedTextColor.WHITE)));
                } else if (m != null && m.startsWith("wacht:")) {
                    p.sendActionBar(Component.text(m.split(":", 3)[2] + " herstart even · je gaat vanzelf terug · rode wol = blijven", NamedTextColor.YELLOW));
                } else if (m != null && m.startsWith("aftellen:")) {
                    // de proxy (netwerk) toont het rustige 3-2-1 zelf in de actionbar
                } else if (hintTot.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
                    p.sendActionBar(Component.text("Je blijft in de lobby · ster = server kiezen", NamedTextColor.GRAY));
                }
            }
        }, 10L, 10L);
        getServer().getScheduler().runTaskTimer(this, this::poortDeeltjes, 40L, 20L);
    }

    /**
     * Poorten beter zichtbaar (Jelle 26-10), zonder herbouw van de tuin: lichtblokken in de kroon van de poort en
     * langs het pad ernaartoe (elke 3 blokken, vlak in de grond). Idempotent: elke start opnieuw gezet.
     */
    private void poortenOplichten(World w) {
        if (tuin == null) return;
        int g = grondY;
        for (Tuin.Poort pt : tuin.poorten) {
            boolean surv = pt.server().equals("survival");
            int cx = (pt.x1() + pt.x2()) / 2, z = pt.z();
            Material licht = surv ? Material.VERDANT_FROGLIGHT : Material.PEARLESCENT_FROGLIGHT;
            for (int s : new int[]{-2, 2}) w.getBlockAt(cx + s, g + 6, z).setType(licht, false);
            w.getBlockAt(cx, g + 5, z).setType(licht, false);
            for (int t = z + 3; t <= -9; t += 3)
                for (int s : new int[]{-1, 1}) w.getBlockAt(cx + s, g, t).setType(licht, false);
        }
    }

    /** Zachte deeltjes in de doorgang van de poorten (elke seconde een paar), groen voor Survival, rood voor KitPvP. */
    private void poortDeeltjes() {
        if (tuin == null || veilig == null) return;
        World w = veilig.getWorld();
        if (w.getPlayers().isEmpty()) return;
        for (Tuin.Poort pt : tuin.poorten) {
            boolean surv = pt.server().equals("survival");
            Location l = new Location(w, (pt.x1() + pt.x2()) / 2.0 + 0.5, grondY + 2.5, pt.z() + 0.5);
            w.spawnParticle(org.bukkit.Particle.DUST, l, 6, 0.9, 1.2, 0.15, 0,
                    new org.bukkit.Particle.DustOptions(surv ? org.bukkit.Color.fromRGB(0x7fd67f) : org.bukkit.Color.fromRGB(0xe06666), 1.0f));
        }
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
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
    private static final int[] LAAN_Z = {-104, -76, -48, 48, 76};   // verspreid door de tuin (zie Tuin)
    private static final int HUT_X = 46;       // huis van x=46..56 (oost) / −56..−46 (west), deur naar de parkourlaan
    private static final int HUIS = 11;        // huis is 11×11
    private static final String[] KLEUR = {"LIGHT_BLUE", "LIME", "ORANGE", "WHITE", "YELLOW", "BROWN", "GREEN", "CYAN", "PURPLE", "RED"};
    private static final int AVENUE_X = 19;    // laan-boulevard x=19..21
    private static final int BOUW_VERSIE = 6;   // 6 = de grote tuin
    private final Map<UUID, Integer> huidigLevel = new HashMap<>();  // ophogen = alles opnieuw bouwen bij de volgende lobbystart

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
        tijdenBestand = new File(getDataFolder(), "tijden.yml");
        tijden = YamlConfiguration.loadConfiguration(tijdenBestand);
        File marker = new File(getDataFolder(), "gebouwd.txt");
        String versie = BOUW_VERSIE + ":" + grondY;
        try {
            plaats = !marker.exists() || !java.nio.file.Files.readString(marker.toPath()).trim().equals(versie);
        } catch (IOException e) {
            plaats = true;
        }
        long t0 = System.currentTimeMillis();
        vloerY = grondY + 1;
        int R = Tuin.RAND + 4;
        for (int cx = Math.floorDiv(-R, 16); cx <= Math.floorDiv(R, 16); cx++)      // tuin blijft geladen (borden, labels)
            for (int cz = Math.floorDiv(-R, 16); cz <= Math.floorDiv(R, 16); cz++) w.addPluginChunkTicket(cx, cz, this);
        if (plaats) {
            getLogger().info("tuin bouwen (versie " + BOUW_VERSIE + ")…");
            vul(w, -R, grondY + 1, -R, R, grondY + 40, R, Material.AIR);   // oude bouwsels weg
            vul(w, -R, grondY - 2, -R, R, grondY - 1, R, Material.DIRT);
            vul(w, -R, grondY, -R, R, grondY, R, Material.GRASS_BLOCK);
        }
        tuin = new Tuin(w, grondY, plaats);
        for (int l = 0; l < LEVELS.length; l++) {                            // levels: geen bomen of bloemenvelden daar
            int sx = l < 5 ? 1 : -1, zc = LAAN_Z[l % 5];
            tuin.reserveer(sx * (HUT_X - 1), zc - 10, sx * 112, zc + 10);
        }
        tuin.bouw();
        for (int l = 0; l < LEVELS.length; l++) {
            int sx = l < 5 ? 1 : -1;
            tuin.levelPoortje(sx, LAAN_Z[l % 5], l + 1, LEVELS[l].naam(), LEVELS[l].moeite(), KLEUR[l]);
        }
        bouwPlaza(w);
        banen = new Location[LEVELS.length][];
        huisje = new Location[LEVELS.length];
        blokNaarPunt.clear();
        for (int l = 0; l < LEVELS.length; l++) bouwLevel(w, l);
        for (Object[] lb : tuin.labels) tuinLabel((Location) lb[0], (String) lb[1], (Float) lb[2]);
        Location spawn = new Location(w, 0.5, grondY + 1, 9.5, 180f, 0f);
        w.setSpawnLocation(spawn);
        veilig = spawn;
        poortenOplichten(w);
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
            if (Math.abs(z) <= 1 || Math.abs(x) <= 1) continue; // doorgang naar de lanen
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
            if (Math.abs(z) <= 2 || Math.abs(x) <= 2) continue;
            zet(w, x, g + 1, z, bloemen[i % bloemen.length]);
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
            for (int lz : LAAN_Z) vul(w, sx * (AVENUE_X + 3), g, lz - 1, sx * (HUT_X - 2), g, lz + 1, Material.DIRT_PATH);
        }
    }

    /** Huis (9×9, hoge wanden, getrapt dak) met deur naar de boulevard en een open voorkant naar de baan. */
    private void bouwHuis(World w, int sx, int zc, int l) {
        Material[] planken = {Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.CHERRY_PLANKS,
                Material.DARK_OAK_PLANKS, Material.JUNGLE_PLANKS, Material.MANGROVE_PLANKS, Material.ACACIA_PLANKS,
                Material.BAMBOO_PLANKS, Material.CRIMSON_PLANKS};
        Material[] stammen = {Material.STRIPPED_OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.CHERRY_LOG,
                Material.DARK_OAK_LOG, Material.JUNGLE_LOG, Material.MANGROVE_LOG, Material.ACACIA_LOG,
                Material.STRIPPED_BAMBOO_BLOCK, Material.CRIMSON_STEM};
        Material plank = planken[l], stam = stammen[l];
        Material dak = Material.valueOf(KLEUR[l] + "_TERRACOTTA");
        Material kleur = Material.valueOf(KLEUR[l] + "_CONCRETE");
        int xa = sx * HUT_X, xb = sx * (HUT_X + HUIS - 1);
        int x1 = Math.min(xa, xb), x2 = Math.max(xa, xb), z1 = zc - 5, z2 = zc + 5, y = vloerY;
        int baanX = sx > 0 ? x2 : x1, boulX = sx > 0 ? x1 : x2;
        vul(w, x1, y - 1, z1, x2, y - 1, z2, Material.COBBLESTONE);
        vul(w, x1, y, z1, x2, y, z2, plank);                                 // vloer
        vul(w, x1, y + 1, z1, x2, y + 5, z2, plank);                         // wanden (5 hoog)
        vul(w, x1 + 1, y + 1, z1 + 1, x2 - 1, y + 5, z2 - 1, Material.AIR);
        for (int[] c : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) vul(w, c[0], y + 1, c[1], c[0], y + 5, c[1], stam);
        vul(w, x1, y + 5, z1, x2, y + 5, z1, kleur);                         // gekleurde band
        vul(w, x1, y + 5, z2, x2, y + 5, z2, kleur);
        vul(w, boulX, y + 1, zc, boulX, y + 3, zc, Material.AIR);            // deur naar de boulevard (3 hoog)
        vul(w, baanX, y + 1, z1 + 1, baanX, y + 4, z2 - 1, Material.AIR);    // open voorkant naar de baan
        for (int x : new int[]{x1 + 2, x1 + 5, x2 - 2}) vul(w, x, y + 2, z2, x, y + 3, z2, Material.GLASS_PANE);   // ramen
        // binnenmuur 'Ranglijst' (z1-kant): donker hout met gouden rand
        vul(w, x1 + 2, y + 1, z1 + 1, x2 - 2, y + 4, z1 + 1, Material.DARK_OAK_PLANKS);
        vul(w, x1 + 2, y + 5, z1 + 1, x2 - 2, y + 5, z1 + 1, Material.GOLD_BLOCK);
        zet(w, x1 + 2, y + 1, z1 + 2, Material.LANTERN);
        zet(w, x2 - 2, y + 1, z1 + 2, Material.LANTERN);
        for (int i = 0; i < 5; i++) vul(w, x1 - 1 + i, y + 6 + i, z1 - 1 + i, x2 + 1 - i, y + 6 + i, z2 + 1 - i, dak); // dak
        vul(w, x1 + 1, y + 6, z1 + 1, x2 - 1, y + 6, z2 - 1, Material.AIR);  // hoog plafond binnen
        zet(w, (x1 + x2) / 2, y + 11, zc, Material.LANTERN);
        // inrichting
        zet(w, x1 + 1, y + 1, z1 + 1, Material.BOOKSHELF);
        zet(w, x1 + 1, y + 2, z1 + 1, Material.BOOKSHELF);
        zet(w, x2 - 1, y + 1, z1 + 1, Material.CRAFTING_TABLE);
        zet(w, x1 + 1, y + 1, z2 - 1, Material.POTTED_RED_TULIP);
        zet(w, x2 - 1, y + 1, z2 - 1, Material.BARREL);
        for (int x = x1 + 1; x <= x2 - 1; x++) zet(w, x, y, zc, kleur);      // loper
        zet(w, (x1 + x2) / 2, y + 5, zc, Material.GLOWSTONE);
        zet(w, x1 + 2, y + 5, zc, Material.GLOWSTONE);
        zet(w, x2 - 2, y + 5, zc, Material.GLOWSTONE);
        // opstapje en lantaarns bij de deur
        zet(w, boulX - sx, y, zc, Material.STONE_BRICK_SLAB);
        lantaarnpaal(w, boulX - sx, zc - 2);
        lantaarnpaal(w, boulX - sx, zc + 2);
    }

    /** Stadion langs de baan: tribunes aan beide kanten, vlaggen in de levelkleur en schijnwerpers op de hoeken. */
    private void bouwStadion(World w, int l, int sx, int zc, int xVan, int xTot) {
        Material kleur = Material.valueOf(KLEUR[l] + "_CONCRETE");
        Material vlag = Material.valueOf(KLEUR[l] + "_BANNER");
        int y = vloerY, xa = Math.min(xVan, xTot), xb = Math.max(xVan, xTot);
        for (int kant : new int[]{-1, 1}) {
            org.bukkit.block.BlockFace rug = kant < 0 ? org.bukkit.block.BlockFace.NORTH : org.bukkit.block.BlockFace.SOUTH;
            org.bukkit.block.data.type.Stairs trap = (org.bukkit.block.data.type.Stairs) Material.SMOOTH_QUARTZ_STAIRS.createBlockData();
            trap.setFacing(rug);
            for (int x = xa; x <= xb; x++) {
                for (int r = 0; r < 3; r++) {
                    int z = zc + kant * (6 + r);
                    vul(w, x, y - 1, z, x, y - 1 + r, z, kleur);              // onderbouw
                    if ((x - xa) % 6 == 5) zet(w, x, y + r, z, Material.SEA_LANTERN);
                    else zet(w, x, y + r, z, trap);
                }
                int zr = zc + kant * 9;
                vul(w, x, y - 1, zr, x, y + 3, zr, kleur);                    // achterwand
                if ((x - xa) % 4 == 0) {
                    zet(w, x, y + 4, zr, Material.OAK_FENCE);
                    zet(w, x, y + 5, zr, vlag);
                }
            }
            for (int x : new int[]{xa - 1, xb + 1}) schijnwerper(w, x, zc + kant * 9);
        }
    }

    private void schijnwerper(World w, int x, int z) {
        int y = vloerY;
        vul(w, x, y - 1, z, x, y + 9, z, Material.POLISHED_DEEPSLATE_WALL);
        vul(w, x - 1, y + 10, z - 1, x + 1, y + 10, z + 1, Material.POLISHED_DEEPSLATE);
        vul(w, x - 1, y + 11, z - 1, x + 1, y + 11, z + 1, Material.SEA_LANTERN);
        zet(w, x, y + 12, z, Material.LIGHTNING_ROD);
    }

    private void bouwLevel(World w, int l) {
        Level lv = LEVELS[l];
        int sx = l < 5 ? 1 : -1;
        int zc = LAAN_Z[l % 5];
        bouwHuis(w, sx, zc, l);
        huisje[l] = new Location(w, sx * (HUT_X + 5) + 0.5, vloerY + 1, zc + 1.5, sx > 0 ? -90f : 90f, 0f);
        java.util.List<Location> punten = new java.util.ArrayList<>();
        int x = sx * (HUT_X + HUIS), y = vloerY, z = zc;  // startblok vlak voor de open voorkant
        int verst = x;
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
                    int idx = punten.size();                  // het hele bad telt als één punt (bodem en rand)
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++) {
                            blokNaarPunt.put(sleutel(x + dx, y - 1, z + dz), new int[]{l, idx});
                            blokNaarPunt.put(sleutel(x + dx, y, z + dz), new int[]{l, idx});     // rand én water
                        }
                    punten.add(new Location(w, x + 0.5, y + 1, z + 0.5, 0f, 0f));
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
                    if (laatste) {
                        bouwFinish(w, x, y, z);
                        int idx = punten.size() - 1;              // het hele finishplatform telt als finish
                        for (int dx = -1; dx <= 1; dx++)
                            for (int dz = -1; dz <= 1; dz++) blokNaarPunt.put(sleutel(x + dx, y, z + dz), new int[]{l, idx});
                    }
                }
            }
        }
        banen[l] = punten.toArray(new Location[0]);
        for (Location pt : banen[l]) verst = sx > 0 ? Math.max(verst, pt.getBlockX()) : Math.min(verst, pt.getBlockX());
        bouwStadion(w, l, sx, zc, sx * (HUT_X + HUIS + 1), verst + sx * 2);
        float kijk = sx > 0 ? -90f : 90f;
        banen[l][0].setYaw(kijk);
        label(new Location(w, sx * (HUT_X + 5) + 0.5, vloerY + 14, zc + 0.5),
                Component.text("Level " + (l + 1), NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.newline()).append(Component.text(lv.naam(), NamedTextColor.WHITE))
                        .append(Component.newline()).append(Component.text(lv.moeite(), NamedTextColor.YELLOW)));
        label(banen[l][0].clone().add(0, 1.3, 0), Component.text("Start", NamedTextColor.GREEN, TextDecoration.BOLD));
        label(banen[l][banen[l].length - 1].clone().add(0, 1.8, 0), Component.text("Finish level " + (l + 1), NamedTextColor.GREEN, TextDecoration.BOLD));
        Location bord = new Location(w, sx * (HUT_X + HUIS + 2) + 0.5, vloerY + 4.5, zc + 0.5);
        borden[l] = bord.getWorld().spawn(bord, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(160, 0, 0, 0));
            d.addScoreboardTag("lobbymelding");
        });
        Location rl = new Location(w, sx * (HUT_X + 5) + 0.5, vloerY + 2.2, zc - 3.6);
        ranglijsten[l] = rl.getWorld().spawn(rl, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(120, 0, 0, 0));
            d.addScoreboardTag("lobbymelding");
        });
        laanRichting[l] = sx;
        werkBordBij(l);
    }

    private final TextDisplay[] ranglijsten = new TextDisplay[10];
    private final int[] laanRichting = new int[10];

    // ── scoreborden en beste tijden ─────────────────────────────────────────────────────────

    private TextDisplay[] borden = new TextDisplay[10];
    private YamlConfiguration tijden;
    private File tijdenBestand;

    private long besteTijd(UUID id, int l) {
        return tijden == null ? 0 : tijden.getLong("level" + (l + 1) + "." + id + ".ms", 0);
    }

    private void slaTijdOp(Player p, int l, long ms) {
        if (tijden == null) return;
        long oud = besteTijd(p.getUniqueId(), l);
        if (oud != 0 && oud <= ms) return;
        String pad = "level" + (l + 1) + "." + p.getUniqueId();
        tijden.set(pad + ".ms", ms);
        tijden.set(pad + ".naam", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(p.displayName()));
        try {
            tijden.save(tijdenBestand);
        } catch (IOException e) {
            getLogger().warning("tijden.yml: " + e.getMessage());
        }
        werkBordBij(l);
    }

    private void werkBordBij(int l) {
        if (tijden == null) return;
        TextColor kleur = TextColor.color(Material.valueOf(KLEUR[l] + "_WOOL") == Material.WHITE_WOOL ? 0xCFEFFF
                : org.bukkit.DyeColor.valueOf(KLEUR[l]).getColor().asRGB());
        Component t = Component.text("Level " + (l + 1) + " · " + LEVELS[l].naam(), kleur, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text(LEVELS[l].moeite(), NamedTextColor.YELLOW));
        List<Map.Entry<String, Long>> lijst = new java.util.ArrayList<>();
        var sec = tijden == null ? null : tijden.getConfigurationSection("level" + (l + 1));
        if (sec != null) for (String k : sec.getKeys(false)) lijst.add(Map.entry(sec.getString(k + ".naam", "?"), sec.getLong(k + ".ms")));
        lijst.sort(Map.Entry.comparingByValue());
        if (lijst.isEmpty()) t = t.append(Component.newline()).append(Component.text("Nog niemand gehaald", NamedTextColor.GRAY));
        String[] plek = {"🥇", "🥈", "🥉", "4.", "5."};
        Component top = Component.empty();
        for (int i = 0; i < Math.min(5, lijst.size()); i++) {
            top = top.append(Component.newline()).append(Component.text(plek[i] + " " + lijst.get(i).getKey() + "  ", i < 3 ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                    .append(Component.text(tijd(lijst.get(i).getValue()), NamedTextColor.GRAY));
        }
        if (borden[l] != null && borden[l].isValid()) borden[l].text(t.append(top));
        if (ranglijsten[l] != null && ranglijsten[l].isValid()) {
            ranglijsten[l].text(Component.text("🏆 RANGLIJST", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.newline()).append(Component.text("Level " + (l + 1) + " · " + LEVELS[l].naam(), kleur))
                    .append(lijst.isEmpty() ? Component.newline().append(Component.text("Nog niemand gehaald", NamedTextColor.GRAY)) : top));
        }
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

    private void tuinLabel(Location plek, String mm, float schaal) {
        Component tekst;
        try {
            tekst = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(mm);
        } catch (RuntimeException e) {
            tekst = Component.text(mm);
        }
        final Component t = tekst;
        plek.getWorld().spawn(plek, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(t);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(90, 0, 0, 0));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(schaal, schaal, schaal), new org.joml.AxisAngle4f()));
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

    /** Hoogste level dat iemand ooit speelde (gestart of gehaald), 1-based; 0 = nog nooit parkour. */
    private int hoogsteGespeeld(UUID id) {
        if (voortgang == null) return 0;
        return Math.max(gehaald(id), voortgang.getInt("gespeeld." + id, 0));
    }

    private void slaGespeeldOp(UUID id, int level) {
        if (voortgang == null || level <= voortgang.getInt("gespeeld." + id, 0)) return;
        voortgang.set("gespeeld." + id, level);
        try {
            voortgang.save(voortgangBestand);
        } catch (IOException e) {
            getLogger().warning("voortgang niet op te slaan: " + e.getMessage());
        }
    }

    /** Via /hub, poort of wissel naar de lobby: bij de start van je hoogste parkourlevel (nooit parkour = spawn). */
    private void naarHoogsteLevel(Player p) {
        if (!p.isOnline() || banen == null) return;
        int hoogste = Math.min(hoogsteGespeeld(p.getUniqueId()), LEVELS.length);
        if (hoogste <= 0) return;
        int l = hoogste - 1;
        p.teleport(banen[l][0]);
        huidigLevel.put(p.getUniqueId(), l);
        p.sendActionBar(Component.text("Lobby · parkour level " + (l + 1) + ": " + LEVELS[l].naam(), NamedTextColor.GRAY));
    }

    /** Naar het huisje van het level waar de speler aan toe is (gehaald + 1); alles gehaald = laatste level. */
    private void naarVolgendLevel(Player p) {
        if (huisje == null) return;
        int l = Math.min(gehaald(p.getUniqueId()), LEVELS.length - 1);
        p.teleport(huisje[l]);
        huidigLevel.put(p.getUniqueId(), l);
        boolean alles = gehaald(p.getUniqueId()) >= LEVELS.length;
        p.sendActionBar(Component.text(alles ? "Alle levels gehaald · probeer ze sneller · /terug = survival"
                : "Parkour level " + (l + 1) + ": " + LEVELS[l].naam() + " · /terug = survival", NamedTextColor.GRAY));
    }

    private static String tijd(long ms) {
        return String.format("%d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10);
    }

    /** Punt onder de speler: midden en de vier hoeken van de voeten (randje van een blok telt ook), plus water. */
    private int[] puntRond(Location l) {
        int y = (int) Math.floor(l.getY() - 0.01);
        double[][] hoek = {{0, 0}, {0.3, 0.3}, {-0.3, 0.3}, {0.3, -0.3}, {-0.3, -0.3}};
        for (double[] h : hoek) {
            int[] p = blokNaarPunt.get(sleutel((int) Math.floor(l.getX() + h[0]), y, (int) Math.floor(l.getZ() + h[1])));
            if (p != null) return p;
        }
        return blokNaarPunt.get(sleutel(l.getBlockX(), l.getBlockY(), l.getBlockZ()));   // in het waterbad
    }

    private final Map<UUID, Long> resetTot = new HashMap<>();

    /** Tijdens een run: gevallen? Dan terug naar de start (of, als je alleen achter het startblok stapte, run stoppen). */
    private void controleerVal(Player p) {
        UUID id = p.getUniqueId();
        Integer bezig = bezigLevel.get(id);
        if (bezig == null || banen == null || aftellend.contains(id)) return;
        long nu = System.currentTimeMillis();
        if (resetTot.getOrDefault(id, 0L) > nu) return;
        Location to = p.getLocation();
        int[] punt = puntRond(to);
        if (punt != null && punt[0] == bezig) return;                 // op de baan
        int lp = Math.min(laatstePunt.getOrDefault(id, 0), banen[bezig].length - 1);
        double laatst = banen[bezig][lp].getY();
        double volgend = lp + 1 < banen[bezig].length ? banen[bezig][lp + 1].getY() : laatst;
        boolean gevallen = to.getY() < Math.min(laatst, volgend) - 2.0     // onder de baan
                || (p.isOnGround() && !p.isClimbing())                       // staat op iets dat geen baan is
                || (p.isInWater() && punt == null);                          // in ander water
        if (!gevallen) return;
        if (lp == 0) {
            int sx = laanRichting[bezig] == 0 ? 1 : laanRichting[bezig];
            boolean vooruit = sx * (to.getX() - banen[bezig][0].getX()) > 0.8;
            if (!vooruit) {                                              // terug het huis in / opzij gestapt: run stoppen
                bezigLevel.remove(id);
                startTijd.remove(id);
                laatstePunt.remove(id);
                return;
            }
        }
        resetTot.put(id, nu + 600);
        p.setFallDistance(0);
        p.setVelocity(new org.bukkit.util.Vector());
        p.teleport(banen[bezig][0]);
        startTijd.put(id, nu);
        laatstePunt.put(id, 0);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
        p.sendActionBar(Component.text("Gevallen! Terug naar de start van level " + (bezig + 1), NamedTextColor.RED));
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
        if (tuin != null && to.getBlockY() > grondY && to.getBlockY() <= grondY + 4) {
            for (Tuin.Poort pt : tuin.poorten) {
                if (to.getBlockZ() != pt.z() || to.getBlockX() < pt.x1() || to.getBlockX() > pt.x2()) continue;
                long nu = System.currentTimeMillis();
                if (poortTot.getOrDefault(p.getUniqueId(), 0L) > nu) break;
                poortTot.put(p.getUniqueId(), nu + 4000);
                naarServer(p, pt.server());
                break;
            }
        }
        if (banen == null || e.getFrom().getX() == to.getX() && e.getFrom().getY() == to.getY() && e.getFrom().getZ() == to.getZ()) return;
        UUID id = p.getUniqueId();
        Integer bezig = bezigLevel.get(id);
        int[] punt = p.isOnGround() || p.isInWater() ? puntRond(to) : null;
        if (punt == null || (bezig != null && punt[0] != bezig && punt[1] != 0)) {
            controleerVal(p);
            return;
        }
        if (punt == null) return;
        int l = punt[0], i = punt[1];
        if (i == 0) {
            if (bezig == null || bezig != l || laatstePunt.getOrDefault(id, 0) > 0) {
                bezigLevel.put(id, l);
                huidigLevel.put(id, l);
                startTijd.put(id, System.currentTimeMillis());
                laatstePunt.put(id, 0);
                slaGespeeldOp(id, l + 1);
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
            long oudPb = besteTijd(id, l);
            long oudRecord = Long.MAX_VALUE;
            var sec = tijden == null ? null : tijden.getConfigurationSection("level" + (l + 1));
            if (sec != null) for (String k : sec.getKeys(false)) oudRecord = Math.min(oudRecord, sec.getLong(k + ".ms", Long.MAX_VALUE));
            boolean pb = oudPb == 0 || ms < oudPb, levelRecord = ms < oudRecord;
            slaTijdOp(p, l, ms);
            if (levelRecord) {
                Bukkit.broadcast(Component.text("🏆 ", NamedTextColor.GOLD).append(p.displayName().colorIfAbsent(NamedTextColor.WHITE))
                        .append(Component.text(" heeft het record van level " + (l + 1) + ": " + tijd(ms) + "!", NamedTextColor.GOLD)));
            } else if (pb) {
                p.sendMessage(Component.text("🏆 Nieuw persoonlijk record: " + tijd(ms) + (oudPb > 0 ? " (was " + tijd(oudPb) + ")" : ""), NamedTextColor.GOLD));
            }
            p.showTitle(Title.title(Component.text("✔ Level " + (l + 1) + " gehaald!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(tijd(ms), NamedTextColor.WHITE).append(pb ? Component.text("  🏆 Nieuw record!", NamedTextColor.GOLD)
                            : Component.text("  (PB " + tijd(oudPb) + ")", NamedTextColor.GRAY)),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1700), Duration.ofMillis(150))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            if (l + 1 < LEVELS.length) {
                aftellendLevel.add(id);
                for (int n = 3; n >= 1; n--) {
                    final int getal = n;
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (!p.isOnline() || aftellend.contains(id) || bezigLevel.containsKey(id)) return;
                        p.showTitle(Title.title(Component.text(String.valueOf(getal), NamedTextColor.GOLD, TextDecoration.BOLD),
                                Component.text("Level " + (l + 2) + ": " + LEVELS[l + 1].naam(), NamedTextColor.WHITE),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + (3 - getal) * 0.25f);
                    }, 40L + (3 - n) * 20L);
                }
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    aftellendLevel.remove(id);
                    if (!p.isOnline() || aftellend.contains(id) || bezigLevel.containsKey(id)) return;
                    p.teleport(banen[l + 1][0]);
                    huidigLevel.put(id, l + 1);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                }, 100L);
            }
        }
    }

    private final Set<UUID> aftellendLevel = new HashSet<>();

    // ── level-kiezer (kompas in de hotbar) ──────────────────────────────────────────────────

    private static final int KIEZER_SLOT = 0;
    private NamespacedKey kiezerKey;

    private static final class Kiezer implements org.bukkit.inventory.InventoryHolder {
        org.bukkit.inventory.Inventory inv;
        public org.bukkit.inventory.Inventory getInventory() { return inv; }
    }

    private final Map<UUID, Kiezer> openKiezers = new HashMap<>();

    private ItemStack kiezerItem() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Level kiezen", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Klik: kies een parkour-level", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(kiezerKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isKiezer(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kiezerKey);
    }

    /** Level-slot in het menu: 1-5 bovenaan, 6-10 onderaan, steeds op 0,2,4,6,8. */
    private static int kiezerSlot(int l) {
        return (l < 5 ? 0 : 18) + (l % 5) * 2;
    }

    private void openKiezer(Player p) {
        if (banen == null) return;
        Kiezer k = new Kiezer();
        k.inv = Bukkit.createInventory(k, 27, Component.text("Parkour-levels", NamedTextColor.DARK_GREEN));
        int gehaald = gehaald(p.getUniqueId());
        ItemStack glas = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glas.getItemMeta();
        gm.setHideTooltip(true);
        glas.setItemMeta(gm);
        for (int i = 0; i < 27; i++) k.inv.setItem(i, glas);
        for (int l = 0; l < LEVELS.length; l++) {
            boolean klaar = l < gehaald, volgende = l == gehaald, open = l <= gehaald;
            ItemStack it = new ItemStack(klaar ? Material.LIME_CONCRETE : volgende ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE);
            ItemMeta m = it.getItemMeta();
            NamedTextColor c = klaar ? NamedTextColor.GREEN : volgende ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY;
            m.displayName(Component.text((klaar ? "✔ " : volgende ? "▶ " : "🔒 ") + "Level " + (l + 1) + " · " + LEVELS[l].naam(), c)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            long best = besteTijd(p.getUniqueId(), l);
            List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.text("Moeilijkheid: " + LEVELS[l].moeite(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Beste tijd: " + (best > 0 ? tijd(best) : "—"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(open ? "Klik: naar de start" : "Haal eerst level " + gehaald + (gehaald == 0 ? "" : "") + " ", open ? NamedTextColor.DARK_GRAY : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            m.setEnchantmentGlintOverride(klaar);
            it.setItemMeta(m);
            it.setAmount(l + 1);
            k.inv.setItem(kiezerSlot(l), it);
        }
        p.openInventory(k.inv);
        openKiezers.put(p.getUniqueId(), k);
    }

    private boolean isKiezerMenu(org.bukkit.inventory.Inventory top, UUID id) {
        if (top.getHolder(false) instanceof Kiezer) return true;
        Kiezer k = openKiezers.get(id);
        return k != null && top.getSize() == k.inv.getSize() && top.getType() == k.inv.getType()
                && java.util.Objects.equals(top.getItem(kiezerSlot(0)), k.inv.getItem(kiezerSlot(0)));
    }

    @EventHandler
    public void onKiezerDicht(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (isKiezerMenu(e.getView().getTopInventory(), e.getPlayer().getUniqueId())) openKiezers.remove(e.getPlayer().getUniqueId());
    }

    private void kiezerKlik(InventoryClickEvent e) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 27) return;
        for (int l = 0; l < LEVELS.length; l++) {
            if (kiezerSlot(l) != slot) continue;
            if (l > gehaald(p.getUniqueId())) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
                return;
            }
            p.closeInventory();
            bezigLevel.remove(p.getUniqueId());
            startTijd.remove(p.getUniqueId());
            laatstePunt.remove(p.getUniqueId());
            p.teleport(banen[l][0]);
            huidigLevel.put(p.getUniqueId(), l);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f);
            p.sendMessage(Component.text("Level " + (l + 1) + ": " + LEVELS[l].naam() + ". Spring van het groene blok!", NamedTextColor.AQUA));
            return;
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
        if (naam.equals("parkour")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Alleen voor spelers.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("kies")) openKiezer(p);
            else opnieuw(p);                                   // /parkour en /parkour reset
            return true;
        }
        if (naam.equals("spawn")) {
            if (sender instanceof Player p && veilig != null) {
                bezigLevel.remove(p.getUniqueId());
                startTijd.remove(p.getUniqueId());
                laatstePunt.remove(p.getUniqueId());
                p.teleport(veilig);
            }
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
            // Met de Velocity-plugin netwerk regelt de proxy wie terug gaat; dan hier niemand.
            sender.sendMessage("terug: " + (netwerkGezien ? "" : String.join(",", Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !blijft.contains(p.getUniqueId())).map(Player::getName).toList())));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("lobby: tuin versie " + BOUW_VERSIE + ", grond " + grondY + ", poorten " + (tuin == null ? 0 : tuin.poorten.size())
                    + ", levels " + (banen == null ? 0 : banen.length) + ", netwerk " + (netwerkGezien ? "ja" : "nee") + ", modus " + modus.size());
            return true;
        }
        sender.sendMessage("Gebruik: /lobbymelding terug | wie-terug");
        return true;
    }

    // ── blijven/terug: commando's en het wol-item in de hotbar ─────────────────────────────

    private static final int ITEM_SLOT = 4; // vak 5
    private NamespacedKey itemKey;

    /** Vak 5: ster (server kiezen), tijdens het aftellen een barrière (annuleren), tijdens wachten rode wol (blijven). */
    private ItemStack blijfItem(boolean aan) {
        return serverItem(null);
    }

    private ItemStack serverItem(String m) {
        boolean aftel = m != null && m.startsWith("aftellen:"), wacht = m != null && m.startsWith("wacht:");
        String naar = m == null ? "" : m.split(":", 3)[2];
        ItemStack it = new ItemStack(aftel ? Material.BARRIER : wacht ? Material.RED_WOOL : Material.NETHER_STAR);
        ItemMeta im = it.getItemMeta();
        im.displayName(Component.text(aftel ? "✖ Annuleren: in de lobby blijven" : wacht ? "Blijven (niet vanzelf terug naar " + naar + ")" : "✦ Server kiezen",
                aftel || wacht ? NamedTextColor.RED : NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        im.lore(List.of(Component.text(aftel ? "Klik: niet naar " + naar + " gaan (ook: /annuleer)"
                : wacht ? "Klik: in de lobby blijven als " + naar + " klaar is (ook: /annuleer)"
                : "Klik: Survival, KitPvP of parkour", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        im.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) (aftel ? 2 : wacht ? 3 : 1));
        it.setItemMeta(im);
        return it;
    }

    private boolean isBlijfItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey);
    }

    private void geefItem(Player p) {
        if (itemKey == null) return;
        String m = modus.get(p.getUniqueId());
        ItemStack huidig = p.getInventory().getItem(ITEM_SLOT);
        ItemStack want = serverItem(m);
        if (isBlijfItem(huidig) && huidig.getType() == want.getType()) return;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            if (i != ITEM_SLOT && isBlijfItem(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
        }
        p.getInventory().setItem(ITEM_SLOT, want);
    }

    // ── servermenu ──────────────────────────────────────────────────────────────────────────

    private static final class ServerMenu implements org.bukkit.inventory.InventoryHolder {
        org.bukkit.inventory.Inventory inv;
        public org.bukkit.inventory.Inventory getInventory() { return inv; }
    }

    private final Set<UUID> openServerMenus = new HashSet<>();

    private ItemStack knop(Material m, String naam, NamedTextColor kleur, String... regels) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(Component.text(naam, kleur).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new java.util.ArrayList<>();
        for (String r : regels) lore.add(Component.text(r, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        im.lore(lore);
        im.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(im);
        return it;
    }

    private void openServerMenu(Player p) {
        ServerMenu sm = new ServerMenu();
        sm.inv = Bukkit.createInventory(sm, 27, Component.text("Waar wil je heen?", NamedTextColor.DARK_AQUA));
        ItemStack glas = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta gm = glas.getItemMeta();
        gm.setHideTooltip(true);
        glas.setItemMeta(gm);
        for (int i = 0; i < 27; i++) sm.inv.setItem(i, glas);
        sm.inv.setItem(10, knop(Material.GRASS_BLOCK, "Survival", NamedTextColor.GREEN, "De survivalwereld", "Klik: erheen (/survival)"));
        sm.inv.setItem(12, knop(Material.IRON_SWORD, "KitPvP", NamedTextColor.RED, "Kies een kit en vecht in de arena", "Klik: erheen (/kitpvp)"));
        sm.inv.setItem(14, knop(Material.FEATHER, "Parkour", NamedTextColor.GOLD, "10 levels verstopt in de tuin", "Klik: kies een level"));
        sm.inv.setItem(16, knop(Material.CHERRY_SAPLING, "Tuin-spawn", NamedTextColor.LIGHT_PURPLE, "Terug naar de fontein (/spawn)"));
        p.openInventory(sm.inv);
        openServerMenus.add(p.getUniqueId());
    }

    private void serverMenuKlik(InventoryClickEvent e) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        switch (slot) {
            case 10 -> { p.closeInventory(); naarServer(p, "survival"); }
            case 12 -> { p.closeInventory(); naarServer(p, "kitpvp"); }
            case 14 -> { p.closeInventory(); Bukkit.getScheduler().runTask(this, () -> openKiezer(p)); }
            case 16 -> {
                p.closeInventory();
                bezigLevel.remove(p.getUniqueId());
                startTijd.remove(p.getUniqueId());
                laatstePunt.remove(p.getUniqueId());
                if (veilig != null) p.teleport(veilig);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f);
            }
            default -> { }
        }
    }

    /** Naar een spelserver (via Velocity). De proxy zet dat als je laatste server. */
    private void naarServer(Player p, String server) {
        String naam = server.equals("kitpvp") ? "KitPvP" : Character.toUpperCase(server.charAt(0)) + server.substring(1);
        p.sendActionBar(Component.text("Naar " + naam + "…", NamedTextColor.GRAY));
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
        stuurNaar(p, server);
    }

    private void stuurActie(Player p, String wat) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buf);
            out.writeUTF("actie");
            out.writeUTF(p.getUniqueId().toString());
            out.writeUTF(wat);
            p.sendPluginMessage(this, NETWERK, buf.toByteArray());
        } catch (IOException e) {
            getLogger().warning("actie: " + e.getMessage());
        }
    }

    /** Berichten van de Velocity-plugin netwerk (aftellen, wachten) voor de hotbar, geluid en actionbar. */
    @Override
    public void onPluginMessageReceived(String kanaal, Player via, byte[] data) {
        if (!kanaal.equals(NETWERK)) return;
        try {
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data));
            String type = in.readUTF();
            if (type.equals("chat")) return;
            UUID id = UUID.fromString(in.readUTF());
            netwerkGezien = true;
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;
            switch (type) {
                case "aftellen" -> {
                    String server = in.readUTF(), naam = in.readUTF();
                    modus.put(id, "aftellen:" + server + ":" + naam);
                    bezigLevel.remove(id);
                    startTijd.remove(id);
                    laatstePunt.remove(id);
                }
                case "tik" -> {
                    int n = Integer.parseInt(in.readUTF());
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.25f, 1f + 0.15f * Math.max(0, 3 - n));
                }
                case "aftellen-klaar" -> {
                    modus.remove(id);
                }
                case "aftellen-stop" -> {
                    modus.remove(id);
                    hintTot.put(id, System.currentTimeMillis() + 3000);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 0.8f);
                }
                case "wacht" -> {
                    String server = in.readUTF(), naam = in.readUTF();
                    modus.put(id, "wacht:" + server + ":" + naam);
                }
                case "hub" -> {         // bewust naar de lobby (niet de eerste join): naar je hoogste parkourlevel
                    if (!isBezoeker(id)) Bukkit.getScheduler().runTaskLater(this, () -> naarHoogsteLevel(p), 2L);
                    return;
                }
                case "wacht-stop" -> {
                    String m = modus.get(id);
                    if (m != null && m.startsWith("wacht:")) modus.remove(id);
                }
                default -> { return; }
            }
            geefItem(p);
        } catch (IOException | RuntimeException e) {
            getLogger().fine("netwerk-bericht: " + e);
        }
    }

    private static final int RESET_SLOT = 8;
    private NamespacedKey resetKey;

    private ItemStack resetItem() {
        ItemStack it = new ItemStack(Material.RED_DYE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Opnieuw", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Klik: terug naar de start van je level (timer opnieuw)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(resetKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isReset(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(resetKey);
    }

    /** Naar de start van je huidige level (lopende run, anders het laatst gekozen/volgende level) en de timer weg. */
    private void opnieuw(Player p) {
        if (banen == null) return;
        UUID id = p.getUniqueId();
        Integer l = bezigLevel.get(id);
        if (l == null) l = huidigLevel.get(id);
        if (l == null) l = Math.min(gehaald(id), LEVELS.length - 1);
        bezigLevel.remove(id);
        startTijd.remove(id);
        laatstePunt.remove(id);
        huidigLevel.put(id, l);
        p.teleport(banen[l][0]);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f);
        p.sendActionBar(Component.text("Level " + (l + 1) + ": " + LEVELS[l].naam() + " · stap op het groene blok", NamedTextColor.AQUA));
    }

    private void geefKiezer(Player p) {
        if (resetKey != null && !isReset(p.getInventory().getItem(RESET_SLOT))) {
            for (int i = 0; i < p.getInventory().getSize(); i++) {
                if (i != RESET_SLOT && isReset(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
            }
            p.getInventory().setItem(RESET_SLOT, resetItem());
        }
        if (kiezerKey == null || isKiezer(p.getInventory().getItem(KIEZER_SLOT))) return;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            if (i != KIEZER_SLOT && isKiezer(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
        }
        p.getInventory().setItem(KIEZER_SLOT, kiezerItem());
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

    private final Map<UUID, Long> laatsteKlik = new HashMap<>();

    /** Eén klik = één wissel: links of rechts, lucht of blok; dubbele events (zelfde klik) binnen 400 ms tellen niet. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKlikItem(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        ItemStack it = e.getHand() == EquipmentSlot.OFF_HAND ? null : e.getItem();
        boolean blijf = isBlijfItem(it), kiezer = isKiezer(it), reset = isReset(it);
        if (!blijf && !kiezer && !reset) {
            ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
            if (e.getHand() == EquipmentSlot.OFF_HAND && (isBlijfItem(hand) || isKiezer(hand) || isReset(hand))) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        Player p = e.getPlayer();
        long nu = System.currentTimeMillis();
        if (nu - laatsteKlik.getOrDefault(p.getUniqueId(), 0L) < 400) return;
        laatsteKlik.put(p.getUniqueId(), nu);
        if (kiezer) {
            openKiezer(p);
            return;
        }
        if (reset) {
            opnieuw(p);
            return;
        }
        String m = modus.get(p.getUniqueId());
        if (m != null) {
            stuurActie(p, "annuleer");
            modus.remove(p.getUniqueId());
            geefItem(p);
            p.updateInventory();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
        } else {
            openServerMenu(p);
        }
    }

    @EventHandler
    public void onInventoryKlik(InventoryClickEvent e) {
        if (isKiezerMenu(e.getView().getTopInventory(), e.getWhoClicked().getUniqueId())) {
            kiezerKlik(e);
            return;
        }
        if (e.getView().getTopInventory().getHolder(false) instanceof ServerMenu) {
            serverMenuKlik(e);
            return;
        }
        boolean eigenInv = e.getClickedInventory() != null && e.getClickedInventory().getType() == org.bukkit.event.inventory.InventoryType.PLAYER;
        if (isBlijfItem(e.getCurrentItem()) || isBlijfItem(e.getCursor()) || isKiezer(e.getCurrentItem()) || isKiezer(e.getCursor())
                || isReset(e.getCurrentItem()) || isReset(e.getCursor())
                || e.getHotbarButton() == ITEM_SLOT || e.getHotbarButton() == KIEZER_SLOT || e.getHotbarButton() == RESET_SLOT
                || (eigenInv && (e.getSlot() == ITEM_SLOT || e.getSlot() == KIEZER_SLOT || e.getSlot() == RESET_SLOT))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSleep(InventoryDragEvent e) {
        if (isBlijfItem(e.getOldCursor()) || isKiezer(e.getOldCursor()) || isReset(e.getOldCursor())
                || isKiezerMenu(e.getView().getTopInventory(), e.getWhoClicked().getUniqueId())
                || e.getView().getTopInventory().getHolder(false) instanceof ServerMenu) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack weg = e.getItemDrop().getItemStack();
        if (isBlijfItem(weg) || isKiezer(weg) || isReset(weg)) e.setCancelled(true);
    }

    @EventHandler
    public void onWissel(PlayerSwapHandItemsEvent e) {
        if (isBlijfItem(e.getMainHandItem()) || isBlijfItem(e.getOffHandItem()) || isKiezer(e.getMainHandItem()) || isKiezer(e.getOffHandItem())
                || isReset(e.getMainHandItem()) || isReset(e.getOffHandItem())) e.setCancelled(true);
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
        speler.getInventory().clear();
        if (veilig != null) speler.teleport(veilig);
        boolean bezoeker = isBezoeker(speler.getUniqueId());
        geefItem(speler);
        geefKiezer(speler);
        if (bezoeker) {                                     // kwam via /parkour uit survival: meteen naar je level
            if (banen != null) naarVolgendLevel(speler);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        startTijd.remove(e.getPlayer().getUniqueId());
        bezigLevel.remove(e.getPlayer().getUniqueId());
        if (!Bukkit.isStopping() && blijft.remove(e.getPlayer().getUniqueId())) bewaarBlijvers(); // bij een lobbyherstart onthouden
        modus.remove(e.getPlayer().getUniqueId());
        poortTot.remove(e.getPlayer().getUniqueId());
        hintTot.remove(e.getPlayer().getUniqueId());
        openServerMenus.remove(e.getPlayer().getUniqueId());
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
