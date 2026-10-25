package nl.burggraaf.mc.opmaak;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Chatopmaak voor de hele server: precies één join- en leave-regel, chat met groepsprefix, advancements als
 * nette regel met hover, en de prefix in de tablijst. Groepen komen uit LuckPerms (permissie group.<naam>).
 *
 * Stilte: `mc` roept `/opmaak stil <s>` aan vlak voor spelers naar de lobby gaan (geen [-] bij een herstart) en
 * schrijft plugins/Opmaak/terugkeer.txt met wie er terugkomen (geen [+] als ze na de herstart terug zijn).
 */
public final class Opmaak extends JavaPlugin implements Listener {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private long stilTot = 0;
    private final Set<String> terugkeer = new HashSet<>();
    private long terugkeerTot = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        leesTerugkeer();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(this::zetTab), 100L, 600L);
    }

    private void leesTerugkeer() {
        File f = new File(getDataFolder(), "terugkeer.txt");
        if (!f.exists()) return;
        try {
            for (String regel : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (!regel.isBlank()) terugkeer.add(regel.trim().toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            getLogger().warning("terugkeer.txt niet te lezen: " + e.getMessage());
        }
        f.delete();
        terugkeerTot = System.currentTimeMillis() + 5 * 60_000L;
    }

    private boolean stil() {
        return System.currentTimeMillis() < stilTot || Bukkit.isStopping();
    }

    private String prefixVan(Player p) {
        ConfigurationSection g = getConfig().getConfigurationSection("groepen");
        if (g == null) return "";
        for (String naam : g.getKeys(false)) {
            if (naam.equals("default")) continue;
            if (p.hasPermission("group." + naam)) return g.getString(naam + ".prefix", "");
        }
        return g.getString("default.prefix", "");
    }

    private Component tekst(String sleutel, Player p, TagResolver... extra) {
        TagResolver alles = TagResolver.resolver(
                TagResolver.resolver(extra),
                Placeholder.unparsed("naam", p.getName()),
                Placeholder.component("prefix", mm.deserialize(prefixVan(p))));
        return mm.deserialize(getConfig().getString(sleutel, ""), alles);
    }

    private void zetTab(Player p) {
        if (!getConfig().getString("tablijst", "").isBlank()) p.playerListName(tekst("tablijst", p));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        zetTab(p);
        boolean terug = System.currentTimeMillis() < terugkeerTot && terugkeer.remove(p.getName().toLowerCase(Locale.ROOT));
        if (!getConfig().getBoolean("toon-join-quit", true) || stil() || terug) {
            e.joinMessage(null);
        } else {
            e.joinMessage(tekst("join", p));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        if (!getConfig().getBoolean("toon-join-quit", true) || stil()) {
            e.quitMessage(null);
        } else {
            e.quitMessage(tekst("quit", e.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Component prefix = mm.deserialize(prefixVan(p));
        String formaat = getConfig().getString("chat", "");
        e.renderer((bron, naam, bericht, kijker) -> mm.deserialize(formaat,
                Placeholder.component("prefix", prefix),
                Placeholder.unparsed("naam", bron.getName()),
                Placeholder.component("bericht", bericht)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (e.message() == null) return; // geen aankondiging (recept, of gamerule uit)
        e.message(tekst("advancement", e.getPlayer(),
                Placeholder.component("advancement", e.getAdvancement().displayName())));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("stil")) {
            int s = 30;
            try {
                if (args.length >= 2) s = Math.max(0, Math.min(600, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
            stilTot = System.currentTimeMillis() + s * 1000L;
            sender.sendMessage("Opmaak: join/leave " + s + " s stil.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("herlaad")) {
            reloadConfig();
            Bukkit.getOnlinePlayers().forEach(this::zetTab);
            sender.sendMessage("Opmaak: config herladen.");
            return true;
        }
        sender.sendMessage("Gebruik: /opmaak stil <seconden> | /opmaak herlaad");
        return true;
    }
}
