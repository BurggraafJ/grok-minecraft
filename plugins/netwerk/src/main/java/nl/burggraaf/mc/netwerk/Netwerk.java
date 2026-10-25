package nl.burggraaf.mc.netwerk;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

/**
 * Het netwerk van Burggraaf Craft op Velocity:
 * - iedereen komt eerst in de lobby; daar 5-4-3-2-1 naar de server waar je de vorige keer was (annuleerbaar);
 * - /lobby /hub /survival /kitpvp /annuleer /blijf /terug;
 * - na een herstart van een spelserver (of als hij wegvalt) wacht je in de lobby en ga je vanzelf terug;
 * - chat, join/leave en serverwissels over het hele netwerk (één keer, met servertag);
 * - TAB toont iedereen op het netwerk met servertag.
 *
 * Backends praten met ons via het kanaal netwerk:main (alleen berichten van een server tellen, nooit van een speler).
 */
@Plugin(id = "netwerk", name = "Netwerk", version = "1.0.0", authors = {"Minecraft Bot"},
        description = "Lobby-eerst, aftellen naar je laatste server, gedeelde chat en TAB voor Burggraaf Craft")
public final class Netwerk {

    static final MinecraftChannelIdentifier KANAAL = MinecraftChannelIdentifier.from("netwerk:main");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ProxyServer proxy;
    private final Logger log;
    private final Path dir;

    // instellingen (config.json)
    private String lobby = "lobby";
    private String standaard = "survival";
    private List<String> spel = List.of("survival", "kitpvp");
    private Map<String, String> tags = new LinkedHashMap<>();
    private Map<String, String> namen = new LinkedHashMap<>();
    private int aftellen = 5;
    private String joinFormaat = "<gray>[<green>+</green>]</gray> <white><naam>";
    private String quitFormaat = "<gray>[<red>-</red>]</gray> <white><naam>";
    private String wisselFormaat = "<dark_gray>»</dark_gray> <gray><naam></gray> <dark_gray>→</dark_gray> <tag>";
    private String tabFormaat = "<tag> <prefix> <white><naam>";
    private String kop = "<gold><bold>Burggraaf Craft</bold></gold>";

    // staat
    private final Map<String, String> laatste = new ConcurrentHashMap<>();          // uuid → server
    private final Map<String, Profiel> profielen = new ConcurrentHashMap<>();       // uuid → profiel
    private final Map<UUID, String> wachtend = new ConcurrentHashMap<>();           // wacht in de lobby op server
    /** Server gaat zo uit (herstart): pas weer "klaar" als hij eerst echt weg was (of na 90 s). */
    private final Map<String, Long> gaatUit = new ConcurrentHashMap<>();
    private final Map<UUID, Aftelling> aftellingen = new ConcurrentHashMap<>();
    private final Set<UUID> stilleWissel = ConcurrentHashMap.newKeySet();           // volgende wissel niet melden
    private final Map<UUID, Set<UUID>> doorOnsInTab = new ConcurrentHashMap<>();
    private final Map<String, Integer> serverOk = new ConcurrentHashMap<>();        // opeenvolgende geslaagde pings
    private volatile long stilTot = 0;
    private volatile boolean profielenVuil = false, laatsteVuil = false;

    static final class Profiel {
        String naam;     // MiniMessage: nickname of accountnaam
        String prefix;   // MiniMessage
        boolean afk;
        String account;
    }

    private final class Aftelling {
        final String server;
        final boolean naJoin;
        int over;
        Aftelling(String server, boolean naJoin, int over) {
            this.server = server;
            this.naJoin = naJoin;
            this.over = over;
        }
    }

    @Inject
    public Netwerk(ProxyServer proxy, Logger log, @DataDirectory Path dir) {
        this.proxy = proxy;
        this.log = log;
        this.dir = dir;
    }

    // ── opstarten ───────────────────────────────────────────────────────────────────────────

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        leesConfig();
        laatste.putAll(leesMap("laatste.json", new TypeToken<Map<String, String>>() {}.getType()));
        Map<String, Profiel> p = leesMap("profielen.json", new TypeToken<Map<String, Profiel>>() {}.getType());
        profielen.putAll(p);
        proxy.getChannelRegistrar().register(KANAAL);
        var cm = proxy.getCommandManager();
        cm.register(cm.metaBuilder("lobby").aliases("hub").plugin(this).build(), (SimpleCommand) inv -> naarCommando(inv.source(), lobby));
        for (String s : spel) {
            cm.register(cm.metaBuilder(s).plugin(this).build(), (SimpleCommand) inv -> naarCommando(inv.source(), s));
        }
        cm.register(cm.metaBuilder("annuleer").aliases("blijf", "annuleren").plugin(this).build(),
                (SimpleCommand) inv -> { if (inv.source() instanceof Player pl) annuleer(pl, true); });
        cm.register(cm.metaBuilder("terug").plugin(this).build(), (SimpleCommand) inv -> {
            if (inv.source() instanceof Player pl) terug(pl);
        });
        cm.register(cm.metaBuilder("netwerk").plugin(this).build(), new Beheer());
        proxy.getScheduler().buildTask(this, this::elkeSeconde).repeat(1, TimeUnit.SECONDS).schedule();
        proxy.getScheduler().buildTask(this, this::controleerWachtenden).repeat(2, TimeUnit.SECONDS).schedule();
        proxy.getScheduler().buildTask(this, this::bewaar).repeat(15, TimeUnit.SECONDS).schedule();
        schrijfWachtend();
        log.info("Netwerk actief: lobby={}, spelservers={}, standaard={}", lobby, spel, standaard);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent e) {
        profielenVuil = laatsteVuil = true;
        bewaar();
    }

    private void leesConfig() {
        tags.put("lobby", "<aqua>[Lobby]</aqua>");
        tags.put("survival", "<green>[Survival]</green>");
        tags.put("kitpvp", "<red>[KitPvP]</red>");
        namen.put("lobby", "Lobby");
        namen.put("survival", "Survival");
        namen.put("kitpvp", "KitPvP");
        try {
            Files.createDirectories(dir);
            Path f = dir.resolve("config.json");
            if (!Files.exists(f)) {
                JsonObject o = new JsonObject();
                o.addProperty("$uitleg", "Netwerk-plugin. Tags en formaten in MiniMessage. Placeholders: <naam>, <tag>, <prefix>.");
                o.addProperty("lobby", lobby);
                o.addProperty("standaard", standaard);
                o.add("spelservers", GSON.toJsonTree(spel));
                o.add("tags", GSON.toJsonTree(tags));
                o.add("namen", GSON.toJsonTree(namen));
                o.addProperty("aftellen", aftellen);
                o.addProperty("join", joinFormaat);
                o.addProperty("quit", quitFormaat);
                o.addProperty("wissel", wisselFormaat);
                o.addProperty("tablijst", tabFormaat);
                o.addProperty("kop", kop);
                Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
            }
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (o.has("lobby")) lobby = o.get("lobby").getAsString();
            if (o.has("standaard")) standaard = o.get("standaard").getAsString();
            if (o.has("spelservers")) spel = GSON.fromJson(o.get("spelservers"), new TypeToken<List<String>>() {}.getType());
            if (o.has("tags")) tags.putAll(GSON.fromJson(o.get("tags"), new TypeToken<Map<String, String>>() {}.getType()));
            if (o.has("namen")) namen.putAll(GSON.fromJson(o.get("namen"), new TypeToken<Map<String, String>>() {}.getType()));
            if (o.has("aftellen")) aftellen = Math.max(1, Math.min(15, o.get("aftellen").getAsInt()));
            if (o.has("join")) joinFormaat = o.get("join").getAsString();
            if (o.has("quit")) quitFormaat = o.get("quit").getAsString();
            if (o.has("wissel")) wisselFormaat = o.get("wissel").getAsString();
            if (o.has("tablijst")) tabFormaat = o.get("tablijst").getAsString();
            if (o.has("kop")) kop = o.get("kop").getAsString();
        } catch (IOException | RuntimeException ex) {
            log.warn("config.json niet te lezen, standaardwaarden: {}", ex.toString());
        }
    }

    private <T> Map<String, T> leesMap(String naam, Type type) {
        try {
            Path f = dir.resolve(naam);
            if (!Files.exists(f)) return new HashMap<>();
            Map<String, T> m = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), type);
            return m == null ? new HashMap<>() : m;
        } catch (IOException | RuntimeException ex) {
            log.warn("{} niet te lezen: {}", naam, ex.toString());
            return new HashMap<>();
        }
    }

    private void schrijf(String naam, Object o) {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(naam + ".tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve(naam), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            log.warn("{} niet op te slaan: {}", naam, ex.toString());
        }
    }

    private synchronized void bewaar() {
        if (laatsteVuil) {
            laatsteVuil = false;
            schrijf("laatste.json", new HashMap<>(laatste));
        }
        if (profielenVuil) {
            profielenVuil = false;
            schrijf("profielen.json", new HashMap<>(profielen));
        }
    }

    /** wachtend.json: wie in de lobby wacht op welke server (mc leest dit bij een herstart). */
    private void schrijfWachtend() {
        Map<String, String> uit = new LinkedHashMap<>();
        wachtend.forEach((id, s) -> proxy.getPlayer(id).ifPresent(p -> uit.put(p.getUsername(), s)));
        schrijf("wachtend.json", uit);
    }

    // ── namen, tags en profielen ────────────────────────────────────────────────────────────

    private String serverNaam(String s) {
        return namen.getOrDefault(s, s);
    }

    private Component tag(String s) {
        return MM.deserialize(tags.getOrDefault(s, "<gray>[" + serverNaam(s) + "]</gray>"));
    }

    private Profiel profiel(Player p) {
        Profiel pr = profielen.get(p.getUniqueId().toString());
        if (pr == null) {
            pr = new Profiel();
            pr.naam = p.getUsername();
            pr.prefix = "<gray>[Member]</gray>";
            pr.account = p.getUsername();
        }
        return pr;
    }

    private Component naam(Player p) {
        try {
            return MM.deserialize(profiel(p).naam);
        } catch (RuntimeException e) {
            return Component.text(p.getUsername());
        }
    }

    private Optional<String> serverVan(Player p) {
        return p.getCurrentServer().map(c -> c.getServerInfo().getName());
    }

    private Component formaat(String f, Player p, String server) {
        Profiel pr = profiel(p);
        var r = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("naam", naam(p)),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("tag", server == null ? Component.empty() : tag(server)),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("prefix", safe(pr.prefix)));
        return MM.deserialize(f, r);
    }

    private static Component safe(String mm) {
        try {
            return mm == null ? Component.empty() : MM.deserialize(mm);
        } catch (RuntimeException e) {
            return Component.text(mm);
        }
    }

    private void iedereen(Component c) {
        for (Player p : proxy.getAllPlayers()) p.sendMessage(c);
    }

    // ── binnenkomen, wisselen, weggaan ──────────────────────────────────────────────────────

    @Subscribe(order = PostOrder.LATE)
    public void onKiesServer(PlayerChooseInitialServerEvent e) {
        Optional<RegisteredServer> l = proxy.getServer(lobby);
        if (l.isPresent()) e.setInitialServer(l.get());
    }

    @Subscribe
    public void onVerbonden(ServerConnectedEvent e) {
        Player p = e.getPlayer();
        String naar = e.getServer().getServerInfo().getName();
        if (!naar.equals(lobby)) {
            stopAftelling(p, false);
            if (wachtend.remove(p.getUniqueId()) != null) schrijfWachtend();
        }
        if (spel.contains(naar)) {
            String oud = laatste.put(p.getUniqueId().toString(), naar);
            if (!naar.equals(oud)) laatsteVuil = true;
        }
    }

    @Subscribe
    public void onNaVerbinden(ServerPostConnectEvent e) {
        Player p = e.getPlayer();
        String naar = serverVan(p).orElse(null);
        if (naar == null) return;
        stuurProfiel(p);
        doorOnsInTab.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).clear();
        if (e.getPreviousServer() == null) {
            if (System.currentTimeMillis() > stilTot) iedereen(formaat(joinFormaat, p, naar));
            if (naar.equals(lobby)) {
                proxy.getScheduler().buildTask(this, () -> naJoin(p)).delay(1200, TimeUnit.MILLISECONDS).schedule();
            }
        } else if (!stilleWissel.remove(p.getUniqueId())) {
            iedereen(formaat(wisselFormaat, p, naar));
        }
        if (naar.equals(lobby) && wachtend.containsKey(p.getUniqueId())) {
            stuurNaarBackend(p, "wacht", wachtend.get(p.getUniqueId()), serverNaam(wachtend.get(p.getUniqueId())));
        }
    }

    /** Net binnen in de lobby: aftellen naar de vorige server (nieuw: standaard), of wachten als die er niet is. */
    private void naJoin(Player p) {
        if (!p.isActive() || !serverVan(p).map(lobby::equals).orElse(false)) return;
        String doel = laatste.getOrDefault(p.getUniqueId().toString(), standaard);
        if (!spel.contains(doel) || proxy.getServer(doel).isEmpty()) doel = standaard;
        boolean nieuw = !laatste.containsKey(p.getUniqueId().toString());
        final String server = doel;
        proxy.getServer(server).ifPresent(rs -> rs.ping().whenComplete((ping, fout) -> {
            if (!p.isActive() || !serverVan(p).map(lobby::equals).orElse(false)) return;
            if (fout != null) {
                wachtend.put(p.getUniqueId(), server);
                schrijfWachtend();
                p.sendMessage(Component.text("» " + serverNaam(server) + " start even op; je gaat vanzelf zodra hij er is. ", NamedTextColor.YELLOW)
                        .append(knop("[Blijven]", NamedTextColor.GREEN, "/annuleer", "In de lobby blijven")));
                stuurNaarBackend(p, "wacht", server, serverNaam(server));
                return;
            }
            if (nieuw) {
                p.sendMessage(Component.text("Welkom op Burggraaf Craft! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("Dit is de lobby; je gaat zo naar " + serverNaam(server) + ".", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
            }
            startAftelling(p, server, true);
        }));
    }

    private Component knop(String tekst, TextColor kleur, String cmd, String uitleg) {
        return Component.text(tekst, kleur, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text(uitleg, NamedTextColor.GRAY)));
    }

    private Component serverKnoppen(String behalve) {
        Component c = Component.empty();
        for (String s : spel) {
            if (s.equals(behalve) || proxy.getServer(s).isEmpty()) continue;
            c = c.append(Component.space()).append(knop("[" + serverNaam(s) + "]", NamedTextColor.AQUA, "/" + s, "Naar " + serverNaam(s)));
        }
        return c;
    }

    @Subscribe
    public void onGekickt(KickedFromServerEvent e) {
        Player p = e.getPlayer();
        String van = e.getServer().getServerInfo().getName();
        if (e.kickedDuringLogin()) return;
        String reden = e.getServerKickReason().map(PlainTextComponentSerializer.plainText()::serialize).orElse("").toLowerCase(Locale.ROOT);
        if (e.kickedDuringServerConnect()) {
            // kon niet naar die server (bv. herstart): melden; wie in de lobby staat wacht erop
            if (spel.contains(van) && serverVan(p).map(lobby::equals).orElse(false)) {
                wachtend.put(p.getUniqueId(), van);
                schrijfWachtend();
                e.setResult(KickedFromServerEvent.Notify.create(Component.text(serverNaam(van)
                        + " is even niet bereikbaar; je gaat vanzelf zodra hij er weer is (/annuleer = blijven).", NamedTextColor.YELLOW)));
            }
            return;
        }
        boolean afsluiten = reden.isEmpty() || reden.contains("closed") || reden.contains("restart") || reden.contains("stopp")
                || reden.contains("shutdown") || reden.contains("disconnect") || reden.contains("timed out");
        if (spel.contains(van)) {
            Optional<RegisteredServer> l = proxy.getServer(lobby);
            if (l.isEmpty()) return;
            if (afsluiten) {
                wachtend.put(p.getUniqueId(), van);
                gaatUit.put(van, System.currentTimeMillis());
                schrijfWachtend();
            }
            stilleWissel.add(p.getUniqueId());
            e.setResult(KickedFromServerEvent.RedirectPlayer.create(l.get(), Component.text(afsluiten
                    ? serverNaam(van) + " herstart even. Je wacht veilig in de lobby en gaat vanzelf terug."
                    : "Je bent van " + serverNaam(van) + " gehaald en staat nu in de lobby.", NamedTextColor.YELLOW)));
        } else if (van.equals(lobby)) {
            String doel = laatste.getOrDefault(p.getUniqueId().toString(), standaard);
            Optional<RegisteredServer> s = proxy.getServer(doel);
            if (s.isEmpty()) return;
            stilleWissel.add(p.getUniqueId());
            e.setResult(KickedFromServerEvent.RedirectPlayer.create(s.get(),
                    Component.text("De lobby herstart even; je bent naar " + serverNaam(doel) + " gestuurd.", NamedTextColor.YELLOW)));
        }
    }

    @Subscribe
    public void onWeg(DisconnectEvent e) {
        Player p = e.getPlayer();
        aftellingen.remove(p.getUniqueId());
        stilleWissel.remove(p.getUniqueId());
        doorOnsInTab.remove(p.getUniqueId());
        if (wachtend.remove(p.getUniqueId()) != null) schrijfWachtend();
        if (e.getLoginStatus() == DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN && System.currentTimeMillis() > stilTot) {
            Component c = formaat(quitFormaat, p, null);
            for (Player q : proxy.getAllPlayers()) if (!q.equals(p)) q.sendMessage(c);
        }
        for (Player q : proxy.getAllPlayers()) {
            Set<UUID> s = doorOnsInTab.get(q.getUniqueId());
            if (s != null && s.remove(p.getUniqueId())) q.getTabList().removeEntry(p.getUniqueId());
        }
    }

    // ── aftellen ────────────────────────────────────────────────────────────────────────────

    private static final TextColor[] KLEUR = {NamedTextColor.AQUA, NamedTextColor.GREEN, NamedTextColor.YELLOW,
            NamedTextColor.GOLD, NamedTextColor.RED};

    private void startAftelling(Player p, String server, boolean naJoin) {
        if (aftellingen.containsKey(p.getUniqueId())) return;
        aftellingen.put(p.getUniqueId(), new Aftelling(server, naJoin, aftellen));
        Component regel = Component.text("» ", NamedTextColor.DARK_GRAY)
                .append(Component.text(naJoin ? "Je gaat over " + aftellen + " s naar " : serverNaam(server) + " is er weer! Over " + aftellen + " s ga je terug naar ", NamedTextColor.YELLOW))
                .append(Component.text(serverNaam(server), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(naJoin ? (laatste.containsKey(p.getUniqueId().toString()) ? " (waar je de vorige keer was). " : ". ") : ". ", NamedTextColor.YELLOW))
                .append(knop("[Annuleren]", NamedTextColor.RED, "/annuleer", "In de lobby blijven (ook: /annuleer of de rode knop in je hotbar)"))
                .append(serverKnoppen(server));
        p.sendMessage(regel);
        stuurNaarBackend(p, "aftellen", server, serverNaam(server), String.valueOf(aftellen));
        tik(p);
    }

    private void tik(Player p) {
        Aftelling a = aftellingen.get(p.getUniqueId());
        if (a == null || !p.isActive()) return;
        if (a.over <= 0) {
            aftellingen.remove(p.getUniqueId());
            stuurNaarBackend(p, "aftellen-klaar", a.server);
            Optional<RegisteredServer> rs = proxy.getServer(a.server);
            if (rs.isEmpty()) return;
            p.showTitle(Title.title(Component.text("Daar ga je!", NamedTextColor.GREEN, TextDecoration.BOLD), Component.text(serverNaam(a.server), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            stilleWissel.add(p.getUniqueId());
            p.createConnectionRequest(rs.get()).connect().whenComplete((r, fout) -> {
                if (fout != null || r == null || !r.isSuccessful()) {
                    stilleWissel.remove(p.getUniqueId());
                    if (p.isActive() && serverVan(p).map(lobby::equals).orElse(false)) {
                        wachtend.put(p.getUniqueId(), a.server);
                        schrijfWachtend();
                        p.sendMessage(Component.text("» " + serverNaam(a.server) + " is even niet bereikbaar; ik probeer het zo opnieuw (/annuleer = blijven).", NamedTextColor.YELLOW));
                    }
                }
            });
            return;
        }
        TextColor kleur = KLEUR[Math.min(a.over, KLEUR.length) - 1];
        p.showTitle(Title.title(Component.text(String.valueOf(a.over), kleur, TextDecoration.BOLD),
                Component.text((a.naJoin ? "Naar " : "Terug naar ") + serverNaam(a.server) + " · /annuleer om te blijven", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(150))));
        stuurNaarBackend(p, "tik", String.valueOf(a.over));
        a.over--;
    }

    private void stopAftelling(Player p, boolean melden) {
        Aftelling a = aftellingen.remove(p.getUniqueId());
        if (a == null) return;
        stuurNaarBackend(p, "aftellen-stop", a.server);
        if (melden) {
            p.clearTitle();
            p.sendMessage(Component.text("» Geannuleerd: je blijft in de lobby. ", NamedTextColor.GREEN)
                    .append(Component.text("Naar een server: ", NamedTextColor.GRAY)).append(serverKnoppen(null).compact())
                    .append(Component.text("  (of de ster in je hotbar)", NamedTextColor.DARK_GRAY)));
        }
    }

    private void annuleer(Player p, boolean melden) {
        boolean wacht = wachtend.remove(p.getUniqueId()) != null;
        if (wacht) {
            schrijfWachtend();
            stuurNaarBackend(p, "wacht-stop", "");
        }
        if (aftellingen.containsKey(p.getUniqueId())) {
            stopAftelling(p, melden);
        } else if (melden) {
            p.sendMessage(Component.text(wacht ? "» Je blijft in de lobby; je gaat niet vanzelf terug. /terug als je wilt."
                    : "» Er loopt geen aftelling. Naar een server:", NamedTextColor.GREEN).append(wacht ? Component.empty() : serverKnoppen(null)));
        }
    }

    private void terug(Player p) {
        String doel = laatste.getOrDefault(p.getUniqueId().toString(), standaard);
        naar(p, doel);
    }

    private void naarCommando(CommandSource src, String server) {
        if (!(src instanceof Player p)) {
            src.sendMessage(Component.text("Alleen voor spelers."));
            return;
        }
        naar(p, server);
    }

    private void naar(Player p, String server) {
        Optional<RegisteredServer> rs = proxy.getServer(server);
        if (rs.isEmpty()) {
            p.sendMessage(Component.text("Die server bestaat niet.", NamedTextColor.RED));
            return;
        }
        if (serverVan(p).map(server::equals).orElse(false)) {
            p.sendMessage(Component.text("Je bent al op " + serverNaam(server) + ".", NamedTextColor.GRAY));
            return;
        }
        stopAftelling(p, false);
        if (wachtend.remove(p.getUniqueId()) != null) schrijfWachtend();
        p.createConnectionRequest(rs.get()).connect().whenComplete((r, fout) -> {
            if (fout != null || r == null || !r.isSuccessful()) {
                p.sendMessage(Component.text(serverNaam(server) + " is nu niet bereikbaar (herstart?). Probeer het zo nog eens.", NamedTextColor.YELLOW));
            }
        });
    }

    // ── elke seconde: aftellen, TAB ─────────────────────────────────────────────────────────

    private long seconde = 0;

    private void elkeSeconde() {
        seconde++;
        for (Player p : proxy.getAllPlayers()) {
            if (aftellingen.containsKey(p.getUniqueId())) tik(p);
        }
        try {
            werkTabBij();
        } catch (RuntimeException ex) {
            if (seconde % 60 == 1) log.warn("tab bijwerken: {}", ex.toString());
        }
        if (seconde % 2 == 0) {
            if (laatsteVuil || profielenVuil) proxy.getScheduler().buildTask(this, this::bewaar).schedule();
        }
    }

    private void werkTabBij() {
        List<Player> alle = new ArrayList<>(proxy.getAllPlayers());
        Map<String, Integer> perServer = new LinkedHashMap<>();
        for (String s : namen.keySet()) if (proxy.getServer(s).isPresent() && !s.equals("staging")) perServer.put(s, 0);
        Map<UUID, String> waar = new HashMap<>();
        for (Player p : alle) {
            String s = serverVan(p).orElse(null);
            if (s == null) continue;
            waar.put(p.getUniqueId(), s);
            perServer.merge(s, 1, Integer::sum);
        }
        Component voet = Component.empty();
        boolean eerste = true;
        for (var en : perServer.entrySet()) {
            if (en.getKey().equals("staging")) continue;
            if (!eerste) voet = voet.append(Component.text("  ·  ", NamedTextColor.DARK_GRAY));
            eerste = false;
            voet = voet.append(tag(en.getKey())).append(Component.text(" " + en.getValue(), NamedTextColor.WHITE));
        }
        Component kopC = safe(kop).append(Component.newline()).append(Component.text(alle.size() + " online", NamedTextColor.GRAY));
        Component voetC = voet.append(Component.newline()).append(Component.text("/lobby · /survival · /kitpvp", NamedTextColor.DARK_GRAY));
        for (Player kijker : alle) {
            String mijn = waar.get(kijker.getUniqueId());
            if (mijn == null) continue;
            TabList tab = kijker.getTabList();
            tab.setHeaderAndFooter(kopC, voetC);
            Set<UUID> ons = doorOnsInTab.computeIfAbsent(kijker.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            for (Player ander : alle) {
                String daar = waar.get(ander.getUniqueId());
                if (daar == null || ander.equals(kijker)) continue;
                if (daar.equals(mijn)) {          // zelfde server: de backend beheert die regel
                    ons.remove(ander.getUniqueId());
                    continue;
                }
                Component weergave = tabNaam(ander, daar);
                Optional<TabListEntry> bestaand = tab.getEntry(ander.getUniqueId());
                if (bestaand.isEmpty()) {
                    tab.addEntry(TabListEntry.builder().tabList(tab).profile(ander.getGameProfile()).displayName(weergave)
                            .latency((int) Math.min(9999, ander.getPing())).gameMode(0).listed(true).build());
                    ons.add(ander.getUniqueId());
                } else {
                    TabListEntry en = bestaand.get();
                    if (!en.getDisplayNameComponent().map(weergave::equals).orElse(false)) en.setDisplayName(weergave);
                    if (!en.isListed()) en.setListed(true);
                    ons.add(ander.getUniqueId());
                }
            }
            for (UUID id : new ArrayList<>(ons)) {
                if (!waar.containsKey(id)) {
                    tab.removeEntry(id);
                    ons.remove(id);
                }
            }
        }
    }

    private Component tabNaam(Player p, String server) {
        Component c = formaat(tabFormaat, p, server);
        return profiel(p).afk ? Component.text("[AFK] ", NamedTextColor.GRAY).append(c) : c;
    }

    // ── herstarts: wachtenden terug zodra hun server er weer is ─────────────────────────────

    private void controleerWachtenden() {
        Set<String> servers = new HashSet<>(wachtend.values());
        for (String s : spel) if (!servers.contains(s)) serverOk.remove(s);
        for (String s : servers) {
            Optional<RegisteredServer> rs = proxy.getServer(s);
            if (rs.isEmpty()) continue;
            rs.get().ping().whenComplete((ping, fout) -> {
                if (fout != null) {
                    serverOk.put(s, 0);
                    gaatUit.remove(s);
                    return;
                }
                Long uit = gaatUit.get(s);
                if (uit != null && System.currentTimeMillis() - uit < 90_000L) return;   // nog niet weg geweest
                gaatUit.remove(s);
                int n = serverOk.merge(s, 1, Integer::sum);
                if (n < 2) return;                   // twee keer achter elkaar (≥ 2 s) bereikbaar: echt klaar
                for (var en : new ArrayList<>(wachtend.entrySet())) {
                    if (!en.getValue().equals(s)) continue;
                    Optional<Player> p = proxy.getPlayer(en.getKey());
                    if (p.isEmpty() || !p.get().isActive()) {
                        wachtend.remove(en.getKey());
                        continue;
                    }
                    if (!serverVan(p.get()).map(lobby::equals).orElse(false)) continue;
                    if (aftellingen.containsKey(en.getKey())) continue;
                    wachtend.remove(en.getKey());
                    stuurNaarBackend(p.get(), "wacht-stop", s);
                    startAftelling(p.get(), s, false);
                }
                schrijfWachtend();
            });
        }
    }

    // ── kanaal netwerk:main ─────────────────────────────────────────────────────────────────

    void stuurNaarBackend(Player p, String type, String... velden) {
        p.getCurrentServer().ifPresent(c -> {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(type);
            out.writeUTF(p.getUniqueId().toString());
            for (String v : velden) out.writeUTF(v == null ? "" : v);
            c.sendPluginMessage(KANAAL, out.toByteArray());
        });
    }

    private void stuurProfiel(Player p) {
        Profiel pr = profiel(p);
        String s = serverVan(p).orElse("");
        stuurNaarBackend(p, "profiel", pr.naam == null ? p.getUsername() : pr.naam, pr.prefix == null ? "" : pr.prefix,
                String.valueOf(pr.afk), tags.getOrDefault(s, ""));
    }

    @Subscribe
    public void onBericht(PluginMessageEvent e) {
        if (!e.getIdentifier().equals(KANAAL)) return;
        e.setResult(PluginMessageEvent.ForwardResult.handled());   // nooit doorgeven (ook niet van een speler naar de server)
        if (!(e.getSource() instanceof ServerConnection bron)) return;   // alleen backends mogen ons iets vertellen
        String van = bron.getServerInfo().getName();
        try {
            ByteArrayDataInput in = e.dataAsDataStream();
            String type = in.readUTF();
            switch (type) {
                case "chat" -> {
                    Component c = GsonComponentSerializer.gson().deserialize(in.readUTF());
                    for (Player p : proxy.getAllPlayers()) {
                        if (!serverVan(p).map(van::equals).orElse(false)) p.sendMessage(c);
                    }
                }
                case "profiel" -> {
                    String id = in.readUTF();
                    Profiel pr = new Profiel();
                    pr.naam = in.readUTF();
                    pr.prefix = in.readUTF();
                    pr.afk = Boolean.parseBoolean(in.readUTF());
                    Profiel oud = profielen.get(id);
                    pr.account = proxy.getPlayer(UUID.fromString(id)).map(Player::getUsername).orElse(oud == null ? null : oud.account);
                    if (oud == null || !java.util.Objects.equals(oud.naam, pr.naam) || !java.util.Objects.equals(oud.prefix, pr.prefix) || oud.afk != pr.afk) {
                        profielen.put(id, pr);
                        profielenVuil = true;
                    }
                }
                case "actie" -> {
                    UUID id = UUID.fromString(in.readUTF());
                    String wat = in.readUTF();
                    Optional<Player> p = proxy.getPlayer(id);
                    if (p.isEmpty()) return;
                    if (!bron.getPlayer().getUniqueId().equals(id)) return;    // een server mag alleen voor zijn eigen speler iets vragen
                    if (wat.equals("annuleer")) annuleer(p.get(), true);
                    else if (wat.equals("terug")) terug(p.get());
                    else if (wat.startsWith("naar:")) naar(p.get(), wat.substring(5));
                }
                default -> { }
            }
        } catch (RuntimeException ex) {
            log.warn("onleesbaar bericht van {}: {}", van, ex.toString());
        }
    }

    // ── beheer (console): netwerk wacht <server> | status | stil <s> ────────────────────────

    private final class Beheer implements SimpleCommand {
        @Override
        public void execute(Invocation inv) {
            CommandSource src = inv.source();
            String[] a = inv.arguments();
            if (a.length >= 2 && a[0].equalsIgnoreCase("wacht")) {
                String s = a[1];
                Optional<RegisteredServer> l = proxy.getServer(lobby);
                Optional<RegisteredServer> rs = proxy.getServer(s);
                if (l.isEmpty() || rs.isEmpty()) {
                    src.sendMessage(Component.text("onbekende server of geen lobby"));
                    return;
                }
                int n = 0;
                for (Player p : rs.get().getPlayersConnected()) {
                    wachtend.put(p.getUniqueId(), s);
                    stilleWissel.add(p.getUniqueId());
                    p.createConnectionRequest(l.get()).fireAndForget();
                    n++;
                }
                serverOk.put(s, 0);
                gaatUit.put(s, System.currentTimeMillis());
                schrijfWachtend();
                src.sendMessage(Component.text("netwerk: " + n + " spelers van " + s + " wachten in de lobby"));
            } else if (a.length >= 1 && a[0].equalsIgnoreCase("stil")) {
                int s = 30;
                try {
                    if (a.length >= 2) s = Math.max(0, Math.min(600, Integer.parseInt(a[1])));
                } catch (NumberFormatException ignored) {
                }
                stilTot = System.currentTimeMillis() + s * 1000L;
                src.sendMessage(Component.text("netwerk: join/leave " + s + " s stil"));
            } else {
                StringBuilder sb = new StringBuilder("netwerk status:");
                for (Player p : proxy.getAllPlayers()) {
                    sb.append("\n  ").append(p.getUsername()).append(" @ ").append(serverVan(p).orElse("?"))
                            .append(" laatste=").append(laatste.getOrDefault(p.getUniqueId().toString(), "-"))
                            .append(wachtend.containsKey(p.getUniqueId()) ? " wacht=" + wachtend.get(p.getUniqueId()) : "")
                            .append(aftellingen.containsKey(p.getUniqueId()) ? " aftellen" : "");
                }
                sb.append("\n  wachtend: ").append(wachtend.size()).append(", profielen: ").append(profielen.size())
                        .append(", laatste: ").append(laatste.size());
                src.sendMessage(Component.text(sb.toString()));
            }
        }

        @Override
        public boolean hasPermission(Invocation inv) {
            return inv.source() instanceof ConsoleCommandSource || inv.source().hasPermission("netwerk.beheer");
        }
    }
}
