package nl.burggraaf.mc.lobbymelding;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Draait op de lobby, de wachtkamer tijdens een herstart van survival. Veilig: geen schade, geen honger, een vaste
 * vloer op de spawn en terug naar boven als je toch valt. Wie binnenkomt ziet een titel, een chatregel en (zolang
 * hij er is) een actionbar dat hij zo vanzelf teruggaat; `mc` en de watchdog sturen iedereen terug zodra survival er is.
 * Ook het sjabloon voor `mc plugin nieuw <naam>`.
 */
public final class LobbyMelding extends JavaPlugin implements Listener {

    private Location veilig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Staging is een kopie van survival met dezelfde plugins: daar niets doen (mc zet daar motd STAGING).
        if (!getConfig().getBoolean("actief", true) || Bukkit.getMotd().contains("STAGING")) {
            getLogger().info("niet actief op deze server");
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, () -> veilig = maakVloer());
        getServer().getScheduler().runTaskTimer(this, () -> {
            String balk = getConfig().getString("actionbar", "");
            for (Player p : Bukkit.getOnlinePlayers()) {
                maakVeilig(p);
                if (!balk.isBlank()) p.sendActionBar(Component.text(balk, NamedTextColor.YELLOW));
            }
        }, 40L, 40L);
    }

    /** Een vloer van 9×9 (barrière onder, glas zichtbaar) op de spawn, met lucht erboven; geeft de plek erop. */
    private Location maakVloer() {
        World w = Bukkit.getWorlds().get(0);
        Location s = w.getSpawnLocation();
        int y = Math.max(w.getMinHeight() + 2, Math.min(w.getMaxHeight() - 4, w.getHighestBlockYAt(s)));
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
        Location plek = new Location(w, bx + 0.5, y + 1, bz + 0.5);
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
        if (p.getHealth() < 20 && !p.isDead()) p.setHealth(Math.min(20, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
    }

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

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (veilig != null && e.getTo().getY() < veilig.getY() - 8) e.getPlayer().teleport(veilig);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (veilig != null) e.setRespawnLocation(veilig);
    }
}
