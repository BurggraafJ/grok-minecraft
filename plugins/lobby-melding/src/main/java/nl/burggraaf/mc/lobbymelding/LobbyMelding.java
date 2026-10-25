package nl.burggraaf.mc.lobbymelding;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Draait op de lobby, de wachtkamer tijdens een herstart van survival: wie binnenkomt ziet dat hij zo vanzelf
 * teruggaat. Ook het sjabloon voor `mc plugin nieuw <naam>`.
 */
public final class LobbyMelding extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player speler = event.getPlayer();
        Component titel = Component.text(getConfig().getString("titel", "Even geduld"), NamedTextColor.GOLD);
        Component onder = Component.text(getConfig().getString("ondertitel", ""), NamedTextColor.GRAY);
        speler.showTitle(Title.title(titel, onder,
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(8), Duration.ofMillis(800))));
        String bericht = getConfig().getString("bericht", "");
        if (!bericht.isBlank()) {
            speler.sendMessage(Component.text(bericht, NamedTextColor.YELLOW));
        }
    }
}
