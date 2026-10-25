package nl.burggraaf.mc.pijl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Richtingspijl in de actionbar naar de dichtstbijzijnde andere speler in dezelfde wereld: "⬆ Lotte 132m".
 * De pijl is relatief aan waar je naar kijkt. /pijl zet hem per speler aan/uit (standaard aan, onthouden).
 */
public final class Pijl extends JavaPlugin implements Listener {

    private static final String[] PIJLEN = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};
    private NamespacedKey uitKey;
    private final Map<UUID, Long> pauzeTot = new HashMap<>();
    private double bereik;
    private long pauzeMs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        uitKey = new NamespacedKey(this, "uit");
        bereik = getConfig().getDouble("bereik", 1000);
        pauzeMs = getConfig().getLong("pauze-seconden", 3) * 1000L;
        long interval = Math.max(2, getConfig().getLong("interval-ticks", 10));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tik, interval, interval);
    }

    private boolean zichtbaar(Player p) {
        return p.getGameMode() != GameMode.SPECTATOR && !p.isDead()
                && p.getMetadata("vanished").stream().noneMatch(m -> m.asBoolean());
    }

    private void tik() {
        long nu = System.currentTimeMillis();
        double max2 = bereik * bereik;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getPersistentDataContainer().has(uitKey) || p.isDead()) continue;
            if (pauzeTot.getOrDefault(p.getUniqueId(), 0L) > nu) continue;
            Location van = p.getLocation();
            Player dichtst = null;
            double best = max2;
            for (Player q : p.getWorld().getPlayers()) {
                if (q == p || !zichtbaar(q)) continue;
                double d2 = q.getLocation().distanceSquared(van);
                if (d2 <= best) {
                    best = d2;
                    dichtst = q;
                }
            }
            if (dichtst == null) continue;
            Location naar = dichtst.getLocation();
            double dx = naar.getX() - van.getX(), dz = naar.getZ() - van.getZ();
            double doelYaw = Math.toDegrees(Math.atan2(-dx, dz)); // Minecraft-yaw: 0 = zuid, 90 = west
            double rel = ((doelYaw - van.getYaw()) % 360 + 360) % 360;
            String pijl = PIJLEN[(int) Math.round(rel / 45.0) % 8];
            int meter = (int) Math.round(Math.sqrt(best));
            p.sendActionBar(Component.text(pijl + " ", NamedTextColor.GOLD)
                    .append(dichtst.displayName().colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(" " + meter + "m", NamedTextColor.GRAY)));
        }
    }

    private void pauze(Player p) {
        pauzeTot.put(p.getUniqueId(), System.currentTimeMillis() + pauzeMs);
    }

    @EventHandler public void onBed(PlayerBedEnterEvent e) { pauze(e.getPlayer()); }
    @EventHandler public void onVehicle(VehicleEnterEvent e) { if (e.getEntered() instanceof Player p) pauze(p); }
    @EventHandler public void onMount(EntityMountEvent e) { if (e.getEntity() instanceof Player p) pauze(p); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { pauze(e.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) { pauze(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { pauzeTot.remove(e.getPlayer().getUniqueId()); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Alleen voor spelers.");
            return true;
        }
        var pdc = p.getPersistentDataContainer();
        if (pdc.has(uitKey)) {
            pdc.remove(uitKey);
            p.sendMessage(Component.text("Richtingspijl aan.", NamedTextColor.GREEN));
        } else {
            pdc.set(uitKey, PersistentDataType.BOOLEAN, true);
            p.sendActionBar(Component.empty());
            p.sendMessage(Component.text("Richtingspijl uit. /pijl zet hem weer aan.", NamedTextColor.YELLOW));
        }
        return true;
    }
}
