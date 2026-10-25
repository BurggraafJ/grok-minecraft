package nl.burggraaf.mc.lobbymelding;

import java.io.ByteArrayOutputStream;
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
 * - Een korte parkour vanaf de spawn (start = groen, checkpoint = goud, finish = smaragd), met tijd.
 * - `/lobbymelding terug` (console, door mc als survival klaar is): groot aftellen 5-4-3-2-1 met geluid, dan via
 *   Velocity (BungeeCord-kanaal) iedereen naar survival.
 * Ook het sjabloon voor `mc plugin nieuw <naam>`.
 */
public final class LobbyMelding extends JavaPlugin implements Listener {

    /** Blokken van de parkour, relatief aan de spawn (dx, dy boven de grond, dz). Eerste = start, laatste = finish. */
    private static final int[][] BAAN = {
            {3, 1, 0}, {5, 1, 0}, {7, 2, 0}, {9, 2, 1}, {9, 3, 3}, {9, 3, 5}, {7, 4, 6}, {5, 4, 6},
            {3, 5, 6}, {2, 5, 8}, {2, 6, 10}, {4, 6, 11}, {6, 7, 11}, {8, 7, 12}, {10, 8, 12}};
    private static final int CHECKPOINT = 7;
    private static final Material[] KLEUREN = {Material.LIGHT_BLUE_CONCRETE, Material.CYAN_CONCRETE,
            Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE, Material.PINK_CONCRETE};
    private static final String KANAAL = "BungeeCord";

    private Location veilig;
    private Location[] baan;
    private int grondY;
    private final Map<UUID, Long> startTijd = new HashMap<>();
    private final Map<UUID, Integer> laatstePunt = new HashMap<>();
    private final Map<UUID, Long> besteTijd = new HashMap<>();
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

    // ── parkour ─────────────────────────────────────────────────────────────────────────────

    private void bouwParkour() {
        World w = veilig.getWorld();
        int bx = veilig.getBlockX(), bz = veilig.getBlockZ();
        baan = new Location[BAAN.length];
        for (int i = 0; i < BAAN.length; i++) {
            int[] b = BAAN[i];
            Block blok = w.getBlockAt(bx + b[0], grondY + b[1], bz + b[2]);
            Material m = i == 0 ? Material.LIME_CONCRETE : i == BAAN.length - 1 ? Material.EMERALD_BLOCK
                    : i == CHECKPOINT ? Material.GOLD_BLOCK : KLEUREN[i % KLEUREN.length];
            blok.setType(m, false);
            for (int dy = 1; dy <= 3; dy++) blok.getRelative(0, dy, 0).setType(Material.AIR, false);
            baan[i] = blok.getLocation().add(0.5, 1, 0.5);
        }
        Location eind = baan[baan.length - 1];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block b = w.getBlockAt(eind.getBlockX() + dx, eind.getBlockY() - 1, eind.getBlockZ() + dz);
                b.setType(Material.QUARTZ_BLOCK, false);
            }
        }
        label(baan[0].clone().add(0, 1.2, 0), Component.text("Parkour", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("spring erop om te starten", NamedTextColor.GRAY)));
        label(baan[CHECKPOINT].clone().add(0, 1.2, 0), Component.text("Checkpoint", NamedTextColor.GOLD));
        label(eind.clone().add(0, 1.2, 0), Component.text("Finish", NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    private void label(Location plek, Component tekst) {
        plek.getWorld().spawn(plek, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(tekst);
            d.addScoreboardTag("lobbymelding");
        });
    }

    private int puntOnder(Location l) {
        if (baan == null) return -1;
        int x = l.getBlockX(), y = (int) Math.floor(l.getY() - 0.01), z = l.getBlockZ();
        for (int i = 0; i < baan.length; i++) {
            Location b = baan[i];
            if (b.getBlockX() == x && b.getBlockY() - 1 == y && b.getBlockZ() == z && Math.abs(l.getY() - b.getY()) < 0.6) {
                return i;
            }
        }
        return -1;
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
        if (baan == null || e.getFrom().getBlock().equals(to.getBlock()) && e.getFrom().getY() == to.getY()) return;
        UUID id = p.getUniqueId();
        boolean bezig = startTijd.containsKey(id);
        if (bezig && p.isOnGround() && to.getY() < grondY + 1.5) { // gevallen: terug naar het laatste punt
            Location terug = baan[laatstePunt.getOrDefault(id, 0)].clone();
            terug.setYaw(to.getYaw());
            p.teleport(terug);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
            return;
        }
        if (!p.isOnGround()) return;
        int i = puntOnder(to);
        if (i < 0) return;
        if (i == 0) {
            if (!bezig || laatstePunt.getOrDefault(id, 0) != 0) {
                startTijd.put(id, System.currentTimeMillis());
                laatstePunt.put(id, 0);
                p.sendMessage(Component.text("Parkour gestart! Val je, dan ga je terug naar je laatste checkpoint.", NamedTextColor.AQUA));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
            }
            return;
        }
        if (!bezig) return;
        if (i == CHECKPOINT && laatstePunt.getOrDefault(id, 0) < CHECKPOINT) {
            laatstePunt.put(id, CHECKPOINT);
            p.sendMessage(Component.text("Checkpoint! ", NamedTextColor.GOLD)
                    .append(Component.text(tijd(System.currentTimeMillis() - startTijd.get(id)), NamedTextColor.WHITE)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.5f);
        } else if (i == baan.length - 1) {
            long ms = System.currentTimeMillis() - startTijd.remove(id);
            laatstePunt.remove(id);
            Long beste = besteTijd.get(id);
            boolean record = beste == null || ms < beste;
            if (record) besteTijd.put(id, ms);
            Bukkit.broadcast(Component.text("[", NamedTextColor.GRAY).append(Component.text("Parkour", NamedTextColor.GREEN))
                    .append(Component.text("] ", NamedTextColor.GRAY)).append(p.displayName().colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(" haalde de finish in ", NamedTextColor.GRAY))
                    .append(Component.text(tijd(ms), NamedTextColor.WHITE))
                    .append(Component.text(record && beste != null ? " (nieuw record!)" : "", NamedTextColor.GOLD)));
            p.showTitle(Title.title(Component.text("Finish!", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(tijd(ms), NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
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
        if (baan != null) {
            speler.sendMessage(Component.text("Verveel je je? Probeer de parkour naast de spawn (groen blok).", NamedTextColor.AQUA));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        startTijd.remove(e.getPlayer().getUniqueId());
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
