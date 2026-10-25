package nl.burggraaf.mc.opmaak;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
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
    /** Nickname uit Essentials-userdata, gelezen bij join (Essentials zet displayName pas iets later). */
    private final Map<UUID, Component> nickBijJoin = new HashMap<>();
    private final Map<UUID, Long> joinTijd = new HashMap<>();
    /** Naamlabel boven het hoofd voor spelers met een nickname (vanilla-naamtag verborgen via team). */
    private final Map<UUID, TextDisplay> labels = new HashMap<>();
    private final Map<UUID, String> laatsteTab = new HashMap<>();
    private static final String NICK_TEAM = "opmaak_nick";
    private long tik = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        leesTerugkeer();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::elkeTik, 20L, 1L);
    }

    @Override
    public void onDisable() {
        labels.values().forEach(TextDisplay::remove);
        labels.clear();
    }

    /** ChestSort+ staat per speler standaard uit; zet het één keer aan (daarna kan de speler het zelf uitzetten). */
    private void chestSortStandaardAan(Player p) {
        if (!getConfig().getBoolean("chestsort-standaard-aan", true)) return;
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("chestsort-plus", "enabled");
        org.bukkit.persistence.PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (!pdc.has(key)) pdc.set(key, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
    }

    private static String plat(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** De naam zoals iedereen hem ziet: de nickname (Essentials /nick) of anders de accountnaam. */
    Component naamVan(Player p) {
        Component d = p.displayName();
        if (!plat(d).equals(p.getName())) return d;
        Long t = joinTijd.get(p.getUniqueId());
        Component n = nickBijJoin.get(p.getUniqueId());
        if (n != null && t != null && System.currentTimeMillis() - t < 15_000L) return n;
        return d;
    }

    private Component leesEssentialsNick(Player p) {
        if (!getConfig().getBoolean("nicknames", true)) return null;
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null) return null;
        File f = new File(ess.getDataFolder(), "userdata/" + p.getUniqueId() + ".yml");
        if (!f.exists()) return null;
        try {
            String nick = YamlConfiguration.loadConfiguration(f).getString("nickname");
            if (nick == null || nick.isBlank()) return null;
            nick = nick.replace('&', '\u00a7');
            return LegacyComponentSerializer.legacySection().deserialize(nick);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean heeftNick(Player p) {
        return getConfig().getBoolean("nicknames", true) && !plat(naamVan(p)).equals(p.getName());
    }

    private boolean labelZichtbaar(Player p) {
        return heeftNick(p) && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && !p.isInvisible()
                && !p.hasPotionEffect(PotionEffectType.INVISIBILITY)
                && p.getMetadata("vanished").stream().noneMatch(m -> m.asBoolean());
    }

    private Team nickTeam() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(NICK_TEAM);
        if (t == null) {
            t = sb.registerNewTeam(NICK_TEAM);
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return t;
    }

    /** AFK: veilige /afk (plugin VeiligAfk, metadata) of Essentials' (auto-)AFK; die laatste geeft alleen de tag. */
    private boolean isAfk(Player p) {
        if (p.hasMetadata("afk-veilig")) return true;
        var ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) return false;
        try {
            Object user = ess.getClass().getMethod("getUser", Player.class).invoke(ess, p);
            return user != null && (Boolean) user.getClass().getMethod("isAfk").invoke(user);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static final Component AFK_TAG = Component.text("[AFK] ", net.kyori.adventure.text.format.NamedTextColor.GRAY);

    private Team afkTeam() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam("opmaak_afk");
        if (t == null) {
            t = sb.registerNewTeam("opmaak_afk");
            t.prefix(AFK_TAG);
        }
        return t;
    }

    private Location labelPlek(Player p) {
        Location l = p.getLocation();
        return new Location(l.getWorld(), l.getX(), l.getY() + p.getHeight() + 0.25, l.getZ(), 0f, 0f);
    }

    private void elkeTik() {
        tik++;
        Team team = nickTeam();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean nick = heeftNick(p);
            boolean afk = tik % 10 == 0 ? isAfk(p) : afkStand.contains(id);
            if (afk) afkStand.add(id); else afkStand.remove(id);
            Team afkT = afkTeam();
            if (nick && !team.hasEntry(p.getName())) team.addEntry(p.getName());          // (zet hem ook uit het afk-team)
            if (!nick && afk && !afkT.hasEntry(p.getName())) afkT.addEntry(p.getName());
            if (!nick && !afk && afkT.hasEntry(p.getName())) afkT.removeEntry(p.getName());
            if (!nick && !afk && team.hasEntry(p.getName())) team.removeEntry(p.getName());
            TextDisplay label = labels.get(id);
            if (labelZichtbaar(p)) {
                Location plek = labelPlek(p);
                if (label != null && (!label.isValid() || label.getWorld() != plek.getWorld())) {
                    label.remove();
                    label = null;
                }
                Component tekst = afk ? AFK_TAG.append(naamVan(p)) : naamVan(p);
                if (label == null) {
                    label = plek.getWorld().spawn(plek, TextDisplay.class, d -> {
                        d.setPersistent(false);
                        d.setBillboard(Display.Billboard.CENTER);
                        d.setSeeThrough(false);
                        d.setShadowed(false);
                        d.setTeleportDuration(2);
                        d.text(tekst);
                    });
                    p.hideEntity(this, label);
                    labels.put(id, label);
                } else {
                    label.teleport(plek);
                    if (!tekst.equals(label.text())) label.text(tekst);
                }
                label.setTextOpacity(p.isSneaking() ? (byte) 100 : (byte) -1);
            } else if (label != null) {
                label.remove();
                labels.remove(id);
            }
            if (tik % 20 == 0) {
                String sleutel = mm.serialize(naamVan(p)) + "|" + prefixVan(p) + "|" + afk;
                if (!sleutel.equals(laatsteTab.get(id))) {
                    laatsteTab.put(id, sleutel);
                    zetTab(p);
                }
            }
        }
        for (Iterator<Map.Entry<UUID, TextDisplay>> it = labels.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, TextDisplay> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) {
                e.getValue().remove();
                it.remove();
            }
        }
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
                Placeholder.component("naam", naamVan(p)),
                Placeholder.component("prefix", mm.deserialize(prefixVan(p))));
        return mm.deserialize(getConfig().getString(sleutel, ""), alles);
    }

    private void zetTab(Player p) {
        if (getConfig().getString("tablijst", "").isBlank()) return;
        Component naam = tekst("tablijst", p);
        p.playerListName(afkStand.contains(p.getUniqueId()) ? AFK_TAG.append(naam) : naam);
    }

    private final java.util.Set<UUID> afkStand = new java.util.HashSet<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        joinTijd.put(p.getUniqueId(), System.currentTimeMillis());
        Component nick = leesEssentialsNick(p);
        if (nick != null) nickBijJoin.put(p.getUniqueId(), nick);
        else nickBijJoin.remove(p.getUniqueId());
        laatsteTab.remove(p.getUniqueId());
        chestSortStandaardAan(p);
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
        UUID id = e.getPlayer().getUniqueId();
        TextDisplay label = labels.remove(id);
        if (label != null) label.remove();
        laatsteTab.remove(id);
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
        Component naamComp = naamVan(p);
        String formaat = getConfig().getString("chat", "");
        e.renderer((bron, naam, bericht, kijker) -> mm.deserialize(formaat,
                Placeholder.component("prefix", prefix),
                Placeholder.component("naam", naamComp),
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
