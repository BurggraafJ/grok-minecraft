package nl.burggraaf.mc.veiligafk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Veilige AFK: /afk (ook /away, /essentials:afk) neemt Essentials' /afk over. 10 s aftellen; bewegen, schade krijgen of
 * aanvallen breekt het af. Daarna geen schade (behalve /kill), geen honger, mobs negeren je; metadata "afk-veilig"
 * (opmaak toont [AFK]). Bewegen, aanvallen of een blok breken/plaatsen/gebruiken heft het op.
 * Essentials' automatische AFK geeft alleen de [AFK]-tag (via opmaak), nooit de bescherming.
 */
public final class VeiligAfk extends JavaPlugin implements Listener {

    private static final Set<String> AFK = Set.of("afk", "away", "eafk", "eaway", "essentials:afk", "essentials:away",
            "essentials:eafk", "essentials:eaway");
    public static final String META = "afk-veilig";

    private final Map<UUID, BukkitTask> aftellen = new HashMap<>();
    private final Map<UUID, Location> startPlek = new HashMap<>();
    private final Set<UUID> veilig = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (UUID id : new HashSet<>(veilig)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.removeMetadata(META, this);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommando(PlayerCommandPreprocessEvent e) {
        String[] d = e.getMessage().substring(1).trim().split("\\s+");
        if (d.length == 0 || !AFK.contains(d[0].toLowerCase()) || d.length > 1) return;   // /afk <speler> laat Essentials doen
        if (!getConfig().getBoolean("aan", true)) return;                                     // uit: gewone Essentials-/afk
        e.setCancelled(true);
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (veilig.contains(id)) {
            stop(p, "Je bent niet meer AFK en weer kwetsbaar.");
            return;
        }
        if (aftellen.containsKey(id)) {
            breekAf(p, "AFK geannuleerd.");
            return;
        }
        int sec = Math.max(1, getConfig().getInt("aftellen-seconden", 10));
        startPlek.put(id, p.getLocation().clone());
        final int[] rest = {sec};
        aftellen.put(id, Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!p.isOnline()) {
                breekAf(p, null);
                return;
            }
            if (rest[0] <= 0) {
                aftellen.remove(id).cancel();
                start(p);
                return;
            }
            p.showTitle(Title.title(Component.text("AFK over " + rest[0], NamedTextColor.GOLD),
                    Component.text("Sta stil, niet aanvallen", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
            p.sendActionBar(Component.text("Veilige AFK over " + rest[0] + " s; bewegen breekt het af", NamedTextColor.YELLOW));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f);
            rest[0]--;
        }, 0L, 20L));
    }

    private void breekAf(Player p, String melding) {
        BukkitTask t = aftellen.remove(p.getUniqueId());
        if (t != null) t.cancel();
        startPlek.remove(p.getUniqueId());
        if (melding != null && p.isOnline()) {
            p.clearTitle();
            p.sendActionBar(Component.text(melding, NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
        }
    }

    private void start(Player p) {
        UUID id = p.getUniqueId();
        startPlek.put(id, p.getLocation().clone());
        veilig.add(id);
        p.setMetadata(META, new FixedMetadataValue(this, true));
        p.setFireTicks(0);
        p.setFallDistance(0);
        for (Entity e : p.getNearbyEntities(48, 32, 48)) {
            if (e instanceof Mob m && p.equals(m.getTarget())) m.setTarget(null);
        }
        p.showTitle(Title.title(Component.text("AFK", NamedTextColor.GREEN), Component.text("Je bent beschermd tot je beweegt", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(300))));
        Bukkit.broadcast(Component.text("* ", NamedTextColor.GRAY).append(p.displayName().colorIfAbsent(NamedTextColor.GRAY))
                .append(Component.text(" is AFK.", NamedTextColor.GRAY)));
    }

    private void stop(Player p, String melding) {
        UUID id = p.getUniqueId();
        if (!veilig.remove(id)) return;
        startPlek.remove(id);
        p.removeMetadata(META, this);
        if (melding != null && p.isOnline()) {
            p.sendActionBar(Component.text(melding, NamedTextColor.YELLOW));
            Bukkit.broadcast(Component.text("* ", NamedTextColor.GRAY).append(p.displayName().colorIfAbsent(NamedTextColor.GRAY))
                    .append(Component.text(" is niet meer AFK.", NamedTextColor.GRAY)));
        }
    }

    private static boolean verplaatst(Location a, Location b) {
        if (a == null || b == null || a.getWorld() != b.getWorld()) return true;
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz > 0.04 || Math.abs(dy) > 0.6;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeweeg(PlayerMoveEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!aftellen.containsKey(id) && !veilig.contains(id)) return;
        if (!verplaatst(startPlek.get(id), e.getTo())) return;
        if (aftellen.containsKey(id)) breekAf(e.getPlayer(), "AFK afgebroken: je bewoog.");
        else stop(e.getPlayer(), "Je bewoog: je bent weer kwetsbaar.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSchade(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        if (veilig.contains(id) && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) stop(p, "Je bent weer kwetsbaar.");
            return;
        }
        if (aftellen.containsKey(id) && !e.isCancelled()) breekAf(p, "AFK afgebroken: je kreeg schade.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAanval(EntityDamageByEntityEvent e) {
        Player aanvaller = null;
        if (e.getDamager() instanceof Player p) aanvaller = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) aanvaller = p;
        if (aanvaller == null) return;
        if (aftellen.containsKey(aanvaller.getUniqueId())) breekAf(aanvaller, "AFK afgebroken: je viel aan.");
        if (veilig.contains(aanvaller.getUniqueId())) stop(aanvaller, "Je viel aan: je bent weer kwetsbaar.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onHonger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && veilig.contains(p.getUniqueId()) && e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDoel(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && veilig.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreek(BlockBreakEvent e) { doeIets(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaats(BlockPlaceEvent e) { doeIets(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGebruik(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK) doeIets(e.getPlayer());
    }

    private void doeIets(Player p) {
        if (aftellen.containsKey(p.getUniqueId())) breekAf(p, "AFK afgebroken.");
        if (veilig.contains(p.getUniqueId())) stop(p, "Je bent weer kwetsbaar.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        breekAf(e.getPlayer(), null);
        veilig.remove(e.getPlayer().getUniqueId());
        e.getPlayer().removeMetadata(META, this);
    }
}
