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
                if (aftellend.contains(p.getUniqueId())) continue;
                Long t = startTijd.get(p.getUniqueId());
                if (t != null) {
                    p.sendActionBar(Component.text("Parkour ", NamedTextColor.AQUA)
                            .append(Component.text(tijd(System.currentTimeMillis() - t), NamedTextColor.WHITE)));
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

    // ── parkour: levels ─────────────────────────────────────────────────────────────────────

    /** Een level: naam, kleur van de blokken, en sprongen (dx, dy, dz) vanaf het vorige blok. Baan loopt in +x. */
    private record Level(String naam, Material blok, int[][] sprongen) {}

    private static final Level[] LEVELS = {
            new Level("Opwarmer", Material.LIGHT_BLUE_CONCRETE, new int[][]{
                    {2, 0, 0}, {2, 0, 0}, {2, 1, 0}, {2, 0, 1}, {2, 0, -1}, {2, 1, 0}, {2, 0, 0}, {2, 1, 1}, {2, 0, -1}, {2, 0, 0}}),
            new Level("Sprongen", Material.LIME_TERRACOTTA, new int[][]{
                    {3, 0, 0}, {3, 0, 0}, {2, 1, 1}, {3, 0, 0}, {2, 1, -1}, {3, 0, 1}, {3, 0, 0}, {2, 1, 0}, {3, 0, -1}, {3, 0, 0}}),
            new Level("Zigzag", Material.ORANGE_CONCRETE, new int[][]{
                    {2, 0, 2}, {2, 0, -2}, {2, 1, 2}, {3, 0, -1}, {2, 0, -2}, {2, 1, 2}, {3, 0, 0}, {2, 0, -2}, {2, 1, 2}, {3, 0, -1}, {3, 0, 0}}),
            new Level("Hoogtevrees", Material.PURPLE_CONCRETE, new int[][]{
                    {3, 1, 0}, {3, 1, 1}, {3, 1, -1}, {4, 0, 0}, {3, 1, 0}, {2, 1, 2}, {3, 1, -1}, {4, 0, 0}, {3, 1, 1}, {3, 0, 0}}),
            new Level("Meester", Material.RED_CONCRETE, new int[][]{
                    {4, 0, 0}, {3, 1, 1}, {4, 0, -1}, {4, 1, 0}, {3, 0, 2}, {4, -1, 0}, {3, 1, -2}, {4, 0, 0}, {3, 1, 1}, {4, 0, 0}, {3, 0, 0}}),
    };
    /** z-baan per level t.o.v. de spawn (12 uit elkaar), en x van het startblok. */
    private static final int[] BAAN_Z = {0, 12, -12, 24, -24};
    private static final int START_X = 11;

    /** Blokken van de oude enkele parkour (vorige versie), om op te ruimen. */
    private static final int[][] OUDE_BAAN = {
            {3, 1, 0}, {5, 1, 0}, {7, 2, 0}, {9, 2, 1}, {9, 3, 3}, {9, 3, 5}, {7, 4, 6}, {5, 4, 6},
            {3, 5, 6}, {2, 5, 8}, {2, 6, 10}, {4, 6, 11}, {6, 7, 11}, {8, 7, 12}, {10, 8, 12}};

    private int vloerY;                                   // y van de vloer in de huisjes = y van de startblokken
    private Location[][] banen;                           // per level de sta-plek op elk blok
    private Location[] huisje;                            // per level de plek in het huisje
    private final Map<Long, int[]> blokNaarPunt = new HashMap<>();
    private final Map<UUID, Integer> bezigLevel = new HashMap<>();
    private YamlConfiguration voortgang;
    private File voortgangBestand;

    private static long sleutel(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private void zet(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void vul(World w, int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) zet(w, x, y, z, m);
    }

    private void bouwParkour() {
        World w = veilig.getWorld();
        int bx = veilig.getBlockX(), bz = veilig.getBlockZ();
        // oude enkele baan opruimen
        for (int[] b : OUDE_BAAN) zet(w, bx + b[0], grondY + b[1], bz + b[2], Material.AIR);
        vul(w, bx + 9, grondY + 7, bz + 11, bx + 11, grondY + 7, bz + 13, Material.AIR);

        vloerY = grondY + 1;
        banen = new Location[LEVELS.length][];
        huisje = new Location[LEVELS.length];
        blokNaarPunt.clear();
        for (int l = 0; l < LEVELS.length; l++) {
            Level lv = LEVELS[l];
            int zc = bz + BAAN_Z[l];
            int x0 = bx + START_X;
            bouwHuisje(w, x0 - 5, zc, l);
            huisje[l] = new Location(w, x0 - 2.5, vloerY + 1, zc + 0.5, -90f, 0f);
            Location[] baan = new Location[lv.sprongen().length + 1];
            int x = x0, y = vloerY, z = zc;
            for (int i = 0; i < baan.length; i++) {
                if (i > 0) {
                    int[] d = lv.sprongen()[i - 1];
                    x += d[0];
                    y += d[1];
                    z += d[2];
                }
                boolean eerste = i == 0, laatste = i == baan.length - 1;
                Material m = eerste ? Material.LIME_CONCRETE : laatste ? Material.EMERALD_BLOCK : lv.blok();
                vul(w, x, y + 1, z, x, y + 3, z, Material.AIR);
                zet(w, x, y, z, m);
                baan[i] = new Location(w, x + 0.5, y + 1, z + 0.5, -90f, 0f);
                blokNaarPunt.put(sleutel(x, y, z), new int[]{l, i});
                if (laatste) bouwFinish(w, x, y, z);
            }
            banen[l] = baan;
            label(new Location(w, x0 - 2.5, vloerY + 5.6, zc + 0.5),
                    Component.text("Level " + (l + 1), NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.newline()).append(Component.text(lv.naam(), NamedTextColor.WHITE)));
            label(baan[0].clone().add(0, 1.3, 0), Component.text("Start", NamedTextColor.GREEN, TextDecoration.BOLD));
            label(baan[baan.length - 1].clone().add(0, 1.6, 0), Component.text("Finish level " + (l + 1), NamedTextColor.GREEN, TextDecoration.BOLD));
        }
    }

    /** Huisje van 5×5 (x1..x1+4), deur aan de baankant (+x) en aan de spawnkant (−x), raampjes, dakje, lantaarn. */
    private void bouwHuisje(World w, int x1, int zc, int l) {
        Material[] planken = {Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.CHERRY_PLANKS, Material.DARK_OAK_PLANKS};
        Material[] stammen = {Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.CHERRY_LOG, Material.DARK_OAK_LOG};
        Material[] daken = {Material.RED_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.PINK_TERRACOTTA, Material.BLACK_TERRACOTTA};
        Material plank = planken[l % planken.length], stam = stammen[l % stammen.length], dak = daken[l % daken.length];
        int x2 = x1 + 4, z1 = zc - 2, z2 = zc + 2, y = vloerY;
        vul(w, x1 - 1, y + 1, z1 - 1, x2 + 1, y + 5, z2 + 1, Material.AIR);
        vul(w, x1, y, z1, x2, y, z2, plank);                       // vloer
        vul(w, x1, y + 1, z1, x2, y + 3, z2, plank);               // wanden (binnen straks leeg)
        vul(w, x1 + 1, y + 1, z1 + 1, x2 - 1, y + 3, z2 - 1, Material.AIR);
        for (int[] c : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) vul(w, c[0], y + 1, c[1], c[0], y + 3, c[1], stam);
        vul(w, x2, y + 1, zc, x2, y + 2, zc, Material.AIR);         // deur naar de baan
        vul(w, x1, y + 1, zc, x1, y + 2, zc, Material.AIR);         // deur naar de spawn
        zet(w, x1 + 2, y + 2, z1, Material.GLASS_PANE);            // raampjes
        zet(w, x1 + 2, y + 2, z2, Material.GLASS_PANE);
        vul(w, x1 - 1, y + 4, z1 - 1, x2 + 1, y + 4, z2 + 1, dak); // dak
        vul(w, x1, y + 5, z1, x2, y + 5, z2, dak);
        zet(w, x1 + 1, y + 1, z1 + 1, Material.LANTERN);
        zet(w, x1 + 3, y + 1, z2 - 1, Material.FLOWER_POT);
        zet(w, x1 + 1, y + 1, z2 - 1, Material.POTTED_POPPY);
        zet(w, x1 + 3, y + 1, z1 + 1, Material.POTTED_DANDELION);
        // paadje naar de spawn
        for (int x = x1 - 1; x > x1 - 6; x--) zet(w, x, grondY, zc, Material.DIRT_PATH);
        zet(w, x1 - 1, y, zc, Material.OAK_SLAB);                  // opstapje bij de achterdeur
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
        List<Player> spelers = List.copyOf(Bukkit.getOnlinePlayers());
        if (spelers.isEmpty()) return;
        spelers.forEach(p -> aftellend.add(p.getUniqueId()));
        for (int i = 0; i <= seconden; i++) {
            final int n = seconden - i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player p : spelers) {
                    if (!p.isOnline()) continue;
                    if (n > 0) {
                        TextColor kleur = AFTEL_KLEUR[Math.min(n, AFTEL_KLEUR.length) - 1];
                        p.showTitle(Title.title(Component.text(String.valueOf(n), kleur, TextDecoration.BOLD),
                                Component.text("Survival is er weer!", NamedTextColor.GRAY),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(150))));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f + 0.15f * (seconden - n));
                    } else {
                        p.showTitle(Title.title(Component.text("Daar ga je!", NamedTextColor.GREEN, TextDecoration.BOLD),
                                Component.empty(),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                        stuurNaar(p, getConfig().getString("terug-naar", "survival"));
                    }
                }
                if (n == 0) spelers.forEach(p -> aftellend.remove(p.getUniqueId()));
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("terug")) {
            int s = getConfig().getInt("aftellen", 5);
            aftellen(Math.max(0, Math.min(10, s)));
            sender.sendMessage("aftellen " + s + " s gestart voor " + Bukkit.getOnlinePlayers().size() + " spelers");
            return true;
        }
        sender.sendMessage("Gebruik: /lobbymelding terug");
        return true;
    }

    // ── binnenkomen ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player speler = event.getPlayer();
        maakVeilig(speler);
        if (veilig != null) speler.teleport(veilig);
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
