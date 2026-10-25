package nl.burggraaf.mc.honger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Survival-regels:
 * - Honger zakt langzamer: elke exhaustion van een speler gaat keer `factor` (config.yml).
 * - Niemand vliegt in survival/adventure (ook OP's niet; bv. Essentials zet bij joinen vliegen aan als je "in de lucht"
 *   inlogt). Alleen wie `survival.vliegen` heeft (standaard niemand) mag. Wie vliegend wordt uitgezet krijgt geen
 *   valschade bij de eerstvolgende landing.
 */
public final class Honger extends JavaPlugin implements Listener {

    private float factor = 0.75f;
    private boolean geenVliegen = true;
    private final Set<UUID> zachteLanding = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        factor = (float) Math.max(0.0, Math.min(1.0, getConfig().getDouble("factor", 0.75)));
        geenVliegen = getConfig().getBoolean("geen-vliegen-in-survival", true);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(this::controleerVliegen), 40L, 40L);
        getLogger().info("honger-factor " + factor + ", vliegen in survival " + (geenVliegen ? "uit" : "toegestaan"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent e) {
        if (e.getEntity() instanceof Player && getConfig().getBoolean("aan", true)) e.setExhaustion(e.getExhaustion() * factor);   // aan = /admin
    }

    private void controleerVliegen(Player p) {
        if (!geenVliegen || !p.isOnline()) return;
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;
        if (!p.getAllowFlight() || p.hasPermission("survival.vliegen")) return;
        if (p.isFlying() || !p.isOnGround()) zachteLanding.add(p.getUniqueId());
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setFallDistance(0);
    }

    private void later(Player p, long ticks) {
        Bukkit.getScheduler().runTaskLater(this, () -> controleerVliegen(p), ticks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        // Essentials zet vliegen een paar ticks na de join; daarom een paar keer kijken.
        later(e.getPlayer(), 1);
        later(e.getPlayer(), 20);
        later(e.getPlayer(), 60);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWereld(PlayerChangedWorldEvent e) {
        later(e.getPlayer(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGamemode(PlayerGameModeChangeEvent e) {
        later(e.getPlayer(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVal(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p
                && zachteLanding.remove(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        zachteLanding.remove(e.getPlayer().getUniqueId());
    }
}
