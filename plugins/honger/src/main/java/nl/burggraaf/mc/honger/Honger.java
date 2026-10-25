package nl.burggraaf.mc.honger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Honger zakt langzamer: elke exhaustion van een speler gaat keer `factor` (config.yml). */
public final class Honger extends JavaPlugin implements Listener {

    private float factor = 0.75f;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        factor = (float) Math.max(0.0, Math.min(1.0, getConfig().getDouble("factor", 0.75)));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("honger-factor " + factor);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent e) {
        if (e.getEntity() instanceof Player) e.setExhaustion(e.getExhaustion() * factor);
    }
}
