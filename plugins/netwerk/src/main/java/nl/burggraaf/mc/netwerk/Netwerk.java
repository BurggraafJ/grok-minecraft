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
import com.velocitypowered.api.event.command.CommandExecuteEvent;
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
import net.kyori.adventure.key.Key;
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
 *
 * <p>Plekken (sessie zelda-apart): een eigen spel op een spelserver, zoals Story of Zelda (wereld «zelda» op kitpvp). Voor
 * spelers is het een eigen bestemming: eigen naam, tag en letter ([Zelda], Z), /zelda, /terug en het terugkeren na het
 * inloggen gaan erheen. De backend meldt waar je bent («plek»); erheen = het cookie burggraaf:plek zetten en verbinden
 * (of, als je al op die server bent, «plek-naar» aan de backend).
 */
@Plugin(id = "netwerk", name = "Netwerk", version = "1.1.0", authors = {"Minecraft Bot"},
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
    private int aftellen = 5;                 // terug na een herstart
    private int aftellenJoin = 3;             // na het binnenkomen, naar je vorige server
    private int joinStilMs = 2000;            // eerst even niets na het binnenkomen
    private String joinFormaat = "<gray>[<green>+</green>]</gray> <white><naam>";
    private String quitFormaat = "<gray>[<red>-</red>]</gray> <white><naam>";
    private String wisselFormaat = "<dark_gray>»</dark_gray> <gray><naam></gray> <dark_gray>→</dark_gray> <tag>";
    private String tabFormaat = "<gray><letter></gray> <prefix> <white><naam>";   // H/S/K vooraan (Jelle 26-10)
    private final Map<String, String> letters = new HashMap<>(Map.of("lobby", "H", "survival", "S", "kitpvp", "K", "zelda", "Z"));
    /** Plek → de server waar hij op draait (config.json «plekken»). */
    private final Map<String, String> plekken = new LinkedHashMap<>(Map.of("zelda", "kitpvp"));
    static final Key PLEK_COOKIE = Key.key("burggraaf", "plek");
    private String kop = "<gold>Burggraaf Craft</gold>";
    private int beroepTipMinuten = 10;

    /** Beroepen: alleen de zichtbare rang. Rechten (admin) blijven onder water in LuckPerms. */
    private final Map<String, String> beroepSoorten = new LinkedHashMap<>();
    private final Map<String, String> beroepen = new ConcurrentHashMap<>();       // uuid → beroep-id
    static final String STANDAARD_BEROEP = "wildvreemde";
    /** In gevecht op kitpvp (kitpvp meldt het): tot dit tijdstip niet wisselen. */
    private final Map<UUID, Long> gevechtTot = new ConcurrentHashMap<>();
    private int tipBeurt = 0;
    /** uuid → naam van de admins (beheerders.json, door mc uit data/spelers.json; zelfde lijst als OP). */
    private volatile Map<String, String> beheerders = Map.of();

    // staat
    private final Map<String, String> laatste = new ConcurrentHashMap<>();          // uuid → server
    private final Map<String, Profiel> profielen = new ConcurrentHashMap<>();       // uuid → profiel
    private final Map<UUID, String> wachtend = new ConcurrentHashMap<>();           // wacht in de lobby op server
    /** Waar de backend zegt dat je bent (een plek, of de server zelf); geldt zolang je op die server bent. */
    private final Map<UUID, String> plek = new ConcurrentHashMap<>();
    /** Op weg naar deze plek (wij verbinden): voor de wisselregel, nog voor de backend het meldt. */
    private final Map<UUID, String> verwacht = new ConcurrentHashMap<>();
    /** Net op een server met plekken binnen: de wisselregel wacht even op de plek (tot dit tijdstip). */
    private final Map<UUID, Long> meldNog = new ConcurrentHashMap<>();
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
        boolean beheer;  // admin/OP op survival: mag /beroep geven
    }

    private final class Aftelling {
        final String server;                                  // een server of een plek (Story of Zelda)
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
        beroepen.putAll(leesMap("beroepen.json", new TypeToken<Map<String, String>>() {}.getType()));
        leesBeheerders();
        proxy.getScheduler().buildTask(this, this::leesBeheerders).repeat(30, TimeUnit.SECONDS).schedule();
        proxy.getChannelRegistrar().register(KANAAL);
        var cm = proxy.getCommandManager();
        cm.register(cm.metaBuilder("lobby").aliases("hub").plugin(this).build(), (SimpleCommand) inv -> naarCommando(inv.source(), lobby));
        for (String s : spel) {
            cm.register(cm.metaBuilder(s).plugin(this).build(), (SimpleCommand) inv -> naarCommando(inv.source(), s));
        }
        for (String pk : plekken.keySet()) cm.register(cm.metaBuilder(pk).plugin(this).build(), new PlekCommando(pk));
        cm.register(cm.metaBuilder("annuleer").aliases("blijf", "annuleren").plugin(this).build(),
                (SimpleCommand) inv -> { if (inv.source() instanceof Player pl) annuleer(pl, true); });
        cm.register(cm.metaBuilder("terug").plugin(this).build(), (SimpleCommand) inv -> {
            if (inv.source() instanceof Player pl) terug(pl);
        });
        cm.register(cm.metaBuilder("netwerk").plugin(this).build(), new Beheer());
        cm.register(cm.metaBuilder("beroep").aliases("beroepen").plugin(this).build(), new BeroepCommando());
        cm.register(cm.metaBuilder("spelers").aliases("wie").plugin(this).build(), (SimpleCommand) inv -> spelersLijst(inv.source()));
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
        tags.put("zelda", "<#7fd1c4>[Zelda]</#7fd1c4>");
        namen.put("lobby", "Lobby");
        namen.put("survival", "Survival");
        namen.put("kitpvp", "KitPvP");
        namen.put("zelda", "Story of Zelda");
        beroepSoorten.put("wildvreemde", "<#9a9a9a>Wildvreemde</#9a9a9a>");
        beroepSoorten.put("bouwer", "<#c9a66b>Bouwer</#c9a66b>");
        beroepSoorten.put("dokter", "<#8fd3c7>Dokter</#8fd3c7>");
        beroepSoorten.put("boer", "<#a8c97f>Boer</#a8c97f>");
        beroepSoorten.put("mijnwerker", "<#9ba7b4>Mijnwerker</#9ba7b4>");
        beroepSoorten.put("smid", "<#c7a27a>Smid</#c7a27a>");
        beroepSoorten.put("handelaar", "<#d9a066>Handelaar</#d9a066>");
        beroepSoorten.put("ontdekker", "<#8fb8de>Ontdekker</#8fb8de>");
        beroepSoorten.put("ondernemer", "<#e8c872>Ondernemer</#e8c872>");
        beroepSoorten.put("dierentemmer", "<#d4a5c9>Dierentemmer</#d4a5c9>");
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
                o.addProperty("versie", 3);
                Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
            }
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (!o.has("versie") || o.get("versie").getAsInt() < 3) {     // 2: rustige TAB, 3: serverletter H/S/K (Jelle 26-10)
                o.addProperty("versie", 3);
                o.addProperty("tablijst", tabFormaat);
                o.addProperty("kop", kop);
                o.addProperty("aftellen_join", aftellenJoin);
                o.addProperty("beroep_tip_minuten", beroepTipMinuten);
                Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
            }
            if (o.has("beroepen")) beroepSoorten.putAll(GSON.fromJson(o.get("beroepen"), new TypeToken<Map<String, String>>() {}.getType()));
            if (o.has("beroep_tip_minuten")) beroepTipMinuten = Math.max(0, o.get("beroep_tip_minuten").getAsInt());
            if (o.has("lobby")) lobby = o.get("lobby").getAsString();
            if (o.has("standaard")) standaard = o.get("standaard").getAsString();
            if (o.has("spelservers")) spel = GSON.fromJson(o.get("spelservers"), new TypeToken<List<String>>() {}.getType());
            if (o.has("tags")) tags.putAll(GSON.fromJson(o.get("tags"), new TypeToken<Map<String, String>>() {}.getType()));
            if (o.has("namen")) namen.putAll(GSON.fromJson(o.get("namen"), new TypeToken<Map<String, String>>() {}.getType()));
            if (o.has("aftellen")) aftellen = Math.max(1, Math.min(15, o.get("aftellen").getAsInt()));
            if (o.has("aftellen_join")) aftellenJoin = Math.max(1, Math.min(15, o.get("aftellen_join").getAsInt()));
            if (o.has("join_stil_ms")) joinStilMs = Math.max(0, Math.min(10000, o.get("join_stil_ms").getAsInt()));
            if (o.has("join")) joinFormaat = o.get("join").getAsString();
            if (o.has("quit")) quitFormaat = o.get("quit").getAsString();
            if (o.has("wissel")) wisselFormaat = o.get("wissel").getAsString();
            if (o.has("tablijst")) tabFormaat = o.get("tablijst").getAsString();
            if (o.has("letters")) letters.putAll(GSON.fromJson(o.get("letters"), new TypeToken<Map<String, String>>() {}.getType()));
            if (o.has("kop")) kop = o.get("kop").getAsString();
            if (o.has("plekken")) {
                plekken.clear();
                plekken.putAll(GSON.fromJson(o.get("plekken"), new TypeToken<Map<String, String>>() {}.getType()));
            }
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

    /** "/lobby · /survival · /kitpvp", alleen met servers die in velocity.toml staan. */
    private String commandoLijst() {
        StringBuilder sb = new StringBuilder("/" + lobby);
        for (String s : spel) if (proxy.getServer(s).isPresent()) sb.append(" · /").append(s);
        for (String pk : plekken.keySet()) if (proxy.getServer(serverVanPlek(pk)).isPresent()) sb.append(" · /").append(pk);
        return sb.toString();
    }

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
            pr.prefix = beroepPrefix(p.getUniqueId().toString());
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

    /** De server waar een plek op draait (een gewone server: zichzelf). */
    String serverVanPlek(String pk) {
        return pk == null ? null : plekken.getOrDefault(pk, pk);
    }

    /** Waar iemand is: een plek (Story of Zelda) als de backend dat meldde, anders zijn server. */
    private Optional<String> plekVan(Player p) {
        Optional<String> s = serverVan(p);
        if (s.isEmpty()) return s;
        String pk = plek.get(p.getUniqueId());
        if (pk == null) pk = verwacht.get(p.getUniqueId());
        return Optional.of(pk != null && s.get().equals(serverVanPlek(pk)) ? pk : s.get());
    }

    /** Is dit een spelbestemming (een spelserver of een plek op een spelserver)? */
    private boolean spelPlek(String pk) {
        return pk != null && spel.contains(serverVanPlek(pk));
    }

    /** Heeft deze server plekken (dan meldt de backend waar je bent)? */
    private boolean heeftPlekken(String server) {
        return plekken.containsValue(server);
    }

    private Component formaat(String f, Player p, String server) {
        Profiel pr = profiel(p);
        var r = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("naam", naam(p)),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("tag", server == null ? Component.empty() : tag(server)),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("servernaam", Component.text(server == null ? "" : serverNaam(server))),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("letter", Component.text(server == null ? ""
                        : letters.getOrDefault(server, serverNaam(server).isEmpty() ? "" : serverNaam(server).substring(0, 1).toUpperCase()))),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("prefix", safe(beroepPrefix(p.getUniqueId().toString()))));
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
        plek.remove(p.getUniqueId());
        String vw = verwacht.get(p.getUniqueId());
        if (vw != null && !serverVanPlek(vw).equals(naar)) verwacht.remove(p.getUniqueId());
        if (!naar.equals(lobby)) {
            stopAftelling(p, false);
            if (wachtend.remove(p.getUniqueId()) != null) schrijfWachtend();
        }
        if (spel.contains(naar)) zetLaatste(p.getUniqueId(), verwacht.getOrDefault(p.getUniqueId(), naar));
    }

    private void zetLaatste(UUID id, String pk) {
        String oud = laatste.put(id.toString(), pk);
        if (!pk.equals(oud)) laatsteVuil = true;
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
                proxy.getScheduler().buildTask(this, () -> naJoin(p)).delay(joinStilMs, TimeUnit.MILLISECONDS).schedule();
            }
        } else if (!stilleWissel.remove(p.getUniqueId())) {
            if (naar.equals(lobby)) stuurNaarBackend(p, "hub");   // lobby: naar je hoogste parkourlevel
            if (heeftPlekken(naar) && !verwacht.containsKey(p.getUniqueId())) {
                meldNog.put(p.getUniqueId(), System.currentTimeMillis() + 2000);   // eerst horen waar hij is (Zelda of KitPvP)
            } else {
                meldWissel(p, plekVan(p).orElse(naar));
            }
        }
        if (naar.equals(lobby) && wachtend.containsKey(p.getUniqueId())) {
            stuurNaarBackend(p, "wacht", wachtend.get(p.getUniqueId()), serverNaam(wachtend.get(p.getUniqueId())));
        }
    }

    /**
     * Bewuste wissel: de anderen zien één regel in de chat, de speler zelf alleen een korte actiebalk. Naar een plek met een
     * eigen binnenkomst (Story of Zelda zegt zelf waar je bent): geen actiebalk, anders staat het er twee keer.
     */
    private void meldWissel(Player p, String waar) {
        Component c = formaat(wisselFormaat, p, waar);
        for (Player q : proxy.getAllPlayers()) if (!q.equals(p)) q.sendMessage(c);
        if (!plekken.containsKey(waar)) p.sendActionBar(Component.text("→ " + serverNaam(waar), NamedTextColor.GRAY));
    }

    /**
     * Net binnen in de lobby (na ~2 s stilte). De allereerste keer (nog geen vorige server): je blijft in de lobby,
     * met één korte welkomstregel. Daarna: rustig 3-2-1 in de actionbar naar de server waar je de vorige keer was.
     */
    private void naJoin(Player p) {
        if (!p.isActive() || !serverVan(p).map(lobby::equals).orElse(false)) return;
        if (!laatste.containsKey(p.getUniqueId().toString())) {
            p.sendMessage(Component.text("Welkom op Burggraaf Craft! ", NamedTextColor.GOLD)
                    .append(Component.text("Kies een server met de ster of loop door een poort.", NamedTextColor.GRAY)));
            return;
        }
        String doel = laatste.get(p.getUniqueId().toString());
        if (!spelPlek(doel) || proxy.getServer(serverVanPlek(doel)).isEmpty()) return;   // die server is er niet meer: gewoon in de lobby
        final String server = serverVanPlek(doel);
        proxy.getServer(server).ifPresent(rs -> rs.ping().whenComplete((ping, fout) -> {
            if (!p.isActive() || !serverVan(p).map(lobby::equals).orElse(false)) return;
            if (fout != null) {
                wachtend.put(p.getUniqueId(), server);
                schrijfWachtend();
                stuurNaarBackend(p, "wacht", server, serverNaam(server));
                return;
            }
            startAftelling(p, doel, true);
        }));
    }

    private Component knop(String tekst, TextColor kleur, String cmd, String uitleg) {
        return Component.text(tekst, kleur, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text(uitleg, NamedTextColor.GRAY)));
    }

    private Component serverKnoppen(String behalve) {
        Component c = Component.empty();
        List<String> alle = new ArrayList<>(spel);
        for (String pk : plekken.keySet()) if (!alle.contains(pk)) alle.add(pk);
        for (String s : alle) {
            if (s.equals(behalve) || proxy.getServer(serverVanPlek(s)).isEmpty()) continue;
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
            Optional<RegisteredServer> s = proxy.getServer(serverVanPlek(doel));
            if (s.isEmpty()) return;
            zetCookie(p, doel);
            stilleWissel.add(p.getUniqueId());
            e.setResult(KickedFromServerEvent.RedirectPlayer.create(s.get(),
                    Component.text("De lobby herstart even; je bent naar " + serverNaam(doel) + " gestuurd.", NamedTextColor.YELLOW)));
        }
    }

    @Subscribe
    public void onWeg(DisconnectEvent e) {
        Player p = e.getPlayer();
        plek.remove(p.getUniqueId());
        verwacht.remove(p.getUniqueId());
        meldNog.remove(p.getUniqueId());
        aftellingen.remove(p.getUniqueId());
        gevechtTot.remove(p.getUniqueId());
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
        int n = naJoin ? aftellenJoin : aftellen;
        aftellingen.put(p.getUniqueId(), new Aftelling(server, naJoin, n));
        // rustig: geen chatregels en geen grote titels, alleen de actionbar (zie tik)
        stuurNaarBackend(p, "aftellen", serverVanPlek(server), serverNaam(server), String.valueOf(n));
        tik(p);
    }

    private void tik(Player p) {
        Aftelling a = aftellingen.get(p.getUniqueId());
        if (a == null || !p.isActive()) return;
        if (a.over <= 0) {
            aftellingen.remove(p.getUniqueId());
            stuurNaarBackend(p, "aftellen-klaar", serverVanPlek(a.server));
            Optional<RegisteredServer> rs = proxy.getServer(serverVanPlek(a.server));
            if (rs.isEmpty()) return;
            stilleWissel.add(p.getUniqueId());
            zetCookie(p, a.server);
            p.createConnectionRequest(rs.get()).connect().whenComplete((r, fout) -> {
                if (fout != null || r == null || !r.isSuccessful()) {
                    stilleWissel.remove(p.getUniqueId());
                    verwacht.remove(p.getUniqueId());
                    if (p.isActive() && serverVan(p).map(lobby::equals).orElse(false)) {
                        wachtend.put(p.getUniqueId(), serverVanPlek(a.server));
                        schrijfWachtend();
                        p.sendActionBar(Component.text(serverNaam(a.server) + " is even niet bereikbaar · je gaat vanzelf · /blijf = blijven", NamedTextColor.GRAY));
                    }
                }
            });
            return;
        }
        p.sendActionBar(Component.text((a.naJoin ? "Naar " : "Terug naar ") + serverNaam(a.server) + " in ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(a.over), NamedTextColor.WHITE))
                .append(Component.text("  ·  /blijf om te blijven", NamedTextColor.DARK_GRAY)));
        stuurNaarBackend(p, "tik", String.valueOf(a.over));
        a.over--;
    }

    private void stopAftelling(Player p, boolean melden) {
        Aftelling a = aftellingen.remove(p.getUniqueId());
        if (a == null) return;
        stuurNaarBackend(p, "aftellen-stop", serverVanPlek(a.server));
        if (melden) {
            p.sendActionBar(Component.text("Je blijft in de lobby", NamedTextColor.GRAY));
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

    /** Nog in gevecht op kitpvp? Dan niet weg (actionbar met de resterende tijd). */
    private boolean inGevecht(Player p) {
        Long t = gevechtTot.get(p.getUniqueId());
        if (t == null) return false;
        long over = t - System.currentTimeMillis();
        if (over <= 0 || !serverVan(p).map("kitpvp"::equals).orElse(false)) {
            gevechtTot.remove(p.getUniqueId());
            return false;
        }
        p.sendActionBar(Component.text("⚔ In gevecht · nog " + ((over + 999) / 1000) + " s", NamedTextColor.RED));
        return true;
    }

    private static final Set<String> WISSEL_COMMANDOS = Set.of("server", "lobby", "hub", "survival", "kitpvp", "zelda", "terug", "spawn", "annuleer", "blijf");

    @Subscribe(order = PostOrder.FIRST)
    public void onCommando(CommandExecuteEvent e) {
        if (!(e.getCommandSource() instanceof Player p)) return;
        String root = e.getCommand().trim().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        if (WISSEL_COMMANDOS.contains(root) && inGevecht(p)) e.setResult(CommandExecuteEvent.CommandResult.denied());
    }

    /** Naar een server of een plek (Story of Zelda: de server waar hij op draait, met het cookie; of daar al: de backend). */
    private void naar(Player p, String doel) {
        if (inGevecht(p)) return;
        String server = serverVanPlek(doel);
        Optional<RegisteredServer> rs = proxy.getServer(server);
        if (rs.isEmpty()) {   // bv. kitpvp nog niet in velocity.toml (paneel zonder plek): netjes melden
            String wat = namen.containsKey(doel) ? serverNaam(doel) + " is er nu niet" : "Die server bestaat niet";
            p.sendMessage(Component.text(wat + ".", NamedTextColor.YELLOW));
            return;
        }
        if (plekVan(p).map(doel::equals).orElse(false)) {
            p.sendActionBar(Component.text("Je bent al in " + serverNaam(doel), NamedTextColor.GRAY));
            return;
        }
        stopAftelling(p, false);
        if (wachtend.remove(p.getUniqueId()) != null) schrijfWachtend();
        if (serverVan(p).map(server::equals).orElse(false)) {     // zelfde server, andere plek (KitPvP ↔ Zelda): de backend wisselt
            stuurNaarBackend(p, "plek-naar", doel);
            return;
        }
        zetCookie(p, doel);
        p.createConnectionRequest(rs.get()).connect().whenComplete((r, fout) -> {
            if (fout != null || r == null || !r.isSuccessful()) {
                verwacht.remove(p.getUniqueId());
                p.sendMessage(Component.text(serverNaam(doel) + " is nu niet bereikbaar (herstart?). Probeer het zo nog eens.", NamedTextColor.YELLOW));
            }
        });
    }

    /** Naar een server met plekken: het cookie zegt de backend waar je heen wilt (de plek, of de server zelf = zijn hub). */
    private void zetCookie(Player p, String doel) {
        String server = serverVanPlek(doel);
        if (!heeftPlekken(server)) return;
        verwacht.put(p.getUniqueId(), doel);
        try {
            p.storeCookie(PLEK_COOKIE, doel.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            log.debug("cookie: {}", ex.toString());
        }
    }

    /**
     * /zelda (een plek): erheen. Met iets erachter, of als je er al bent, is het een commando van de backend zelf
     * (/zelda beheer, …): dan geeft Velocity het door (hasPermission = false).
     */
    private final class PlekCommando implements SimpleCommand {
        private final String pk;

        PlekCommando(String pk) {
            this.pk = pk;
        }

        @Override
        public void execute(Invocation inv) {
            naarCommando(inv.source(), pk);
        }

        @Override
        public boolean hasPermission(Invocation inv) {
            if (inv.arguments().length > 0) return false;
            return !(inv.source() instanceof Player p) || !plekVan(p).map(pk::equals).orElse(false);
        }
    }

    // ── elke seconde: aftellen, TAB ─────────────────────────────────────────────────────────

    private long seconde = 0;

    private void elkeSeconde() {
        seconde++;
        long nu = System.currentTimeMillis();
        for (Player p : proxy.getAllPlayers()) {
            if (aftellingen.containsKey(p.getUniqueId())) tik(p);
            Long tot = meldNog.get(p.getUniqueId());
            if (tot != null && (tot <= nu || plek.containsKey(p.getUniqueId()))) {
                meldNog.remove(p.getUniqueId());
                plekVan(p).ifPresent(w -> meldWissel(p, w));
            }
        }
        try {
            werkTabBij();
        } catch (RuntimeException ex) {
            if (seconde % 60 == 1) log.warn("tab bijwerken: {}", ex.toString());
        }
        if (beroepTipMinuten > 0 && seconde % (beroepTipMinuten * 60L) == (beroepTipMinuten * 30L) && !proxy.getAllPlayers().isEmpty()) beroepTip();
        if (seconde % 2 == 0) {
            if (laatsteVuil || profielenVuil) proxy.getScheduler().buildTask(this, this::bewaar).schedule();
        }
    }

    private void werkTabBij() {
        List<Player> alle = new ArrayList<>(proxy.getAllPlayers());
        Map<String, Integer> perServer = new LinkedHashMap<>();
        for (String s : namen.keySet()) if (proxy.getServer(s).isPresent() && !s.equals("staging")) perServer.put(s, 0);
        Map<UUID, String> waar = new HashMap<>();
        Map<UUID, String> server = new HashMap<>();
        for (Player p : alle) {
            String s = serverVan(p).orElse(null);
            if (s == null) continue;
            server.put(p.getUniqueId(), s);
            waar.put(p.getUniqueId(), plekVan(p).orElse(s));
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
        Component kopC = safe(kop);              // rustig: alleen de naam, geen tellers of commando's
        Component voetC = Component.empty();
        for (Player kijker : alle) {
            String mijn = server.get(kijker.getUniqueId());
            if (mijn == null) continue;
            TabList tab = kijker.getTabList();
            tab.setHeaderAndFooter(kopC, voetC);
            Set<UUID> ons = doorOnsInTab.computeIfAbsent(kijker.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            for (Player ander : alle) {
                String daar = waar.get(ander.getUniqueId());
                if (daar == null || ander.equals(kijker)) continue;
                if (server.get(ander.getUniqueId()).equals(mijn)) {          // zelfde server: de backend beheert die regel
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
                    String terug = laatste.get(en.getKey().toString());
                    startAftelling(p.get(), terug != null && s.equals(serverVanPlek(terug)) ? terug : s, false);
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
        stuurNaarBackend(p, "profiel", pr.naam == null ? p.getUsername() : pr.naam, beroepPrefix(p.getUniqueId().toString()),
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
                    try {
                        pr.beheer = Boolean.parseBoolean(in.readUTF());
                    } catch (RuntimeException geenVeld) {
                        pr.beheer = oud != null && oud.beheer;
                    }
                    pr.prefix = beroepPrefix(id);      // de zichtbare rang is het beroep (niet de LuckPerms-groep)
                    pr.account = proxy.getPlayer(UUID.fromString(id)).map(Player::getUsername).orElse(oud == null ? null : oud.account);
                    if (oud == null || !java.util.Objects.equals(oud.naam, pr.naam) || !java.util.Objects.equals(oud.prefix, pr.prefix) || oud.afk != pr.afk || oud.beheer != pr.beheer) {
                        profielen.put(id, pr);
                        profielenVuil = true;
                    }
                }
                case "plek" -> {                               // de backend: waar deze speler nu is (een plek of de server zelf)
                    UUID id = UUID.fromString(in.readUTF());
                    String pk = in.readUTF();
                    if (!bron.getPlayer().getUniqueId().equals(id) || !van.equals(serverVanPlek(pk))) return;
                    String oud = plekVan(bron.getPlayer()).orElse(van);
                    plek.put(id, pk);
                    verwacht.remove(id);
                    if (spel.contains(van)) zetLaatste(id, pk);
                    if (!oud.equals(pk) && !meldNog.containsKey(id)) {     // op dezelfde server gewisseld (KitPvP ↔ Zelda): de anderen zien het
                        Component c = formaat(wisselFormaat, bron.getPlayer(), pk);
                        for (Player q : proxy.getAllPlayers()) if (!q.equals(bron.getPlayer())) q.sendMessage(c);
                    }
                }
                case "actie" -> {
                    UUID id = UUID.fromString(in.readUTF());
                    String wat = in.readUTF();
                    Optional<Player> p = proxy.getPlayer(id);
                    if (p.isEmpty()) return;
                    if (!bron.getPlayer().getUniqueId().equals(id)) return;    // een server mag alleen voor zijn eigen speler iets vragen
                    if (wat.startsWith("gevecht:")) {          // kitpvp: zoveel ms in gevecht (0 = klaar)
                        long ms = Math.max(0, Math.min(60_000, Long.parseLong(wat.substring(8))));
                        if (ms == 0) gevechtTot.remove(id); else gevechtTot.put(id, System.currentTimeMillis() + ms);
                        return;
                    }
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

    // ── beroepen en /spelers ────────────────────────────────────────────────────────────────

    String beroepPrefix(String uuid) {
        String b = beroepen.getOrDefault(uuid, STANDAARD_BEROEP);
        return beroepSoorten.getOrDefault(b, beroepSoorten.getOrDefault(STANDAARD_BEROEP, "<gray>Wildvreemde</gray>"));
    }

    private String beroepNaam(String id) {
        return PlainTextComponentSerializer.plainText().serialize(safe(beroepSoorten.getOrDefault(id, id)));
    }

    private static final String[] TIPS = {
            "Iedereen begint als <#9a9a9a>Wildvreemde</#9a9a9a>. Een beroep, zoals Bouwer, Dokter of Boer, verdien je in het spel.",
            "Beroepen: Bouwer, Dokter, Boer, Mijnwerker, Smid, Handelaar, Ontdekker. <white>/spelers</white> laat zien wie wat is.",
            "Nog Wildvreemde? Laat zien wat je kunt, dan krijg je een beroep.",
    };

    private void beroepTip() {
        Component c = Component.text("✦ ", TextColor.color(0xe8c872)).append(MM.deserialize("<gray>" + TIPS[tipBeurt++ % TIPS.length]));
        iedereen(c);
    }

    /** uuid van een bekende speler op account- of nicknaam (online eerst). */
    private String zoekSpeler(String naam) {
        Optional<Player> online = proxy.getPlayer(naam);
        if (online.isPresent()) return online.get().getUniqueId().toString();
        for (var en : profielen.entrySet()) {
            Profiel pr = en.getValue();
            if (pr.account != null && pr.account.equalsIgnoreCase(naam)) return en.getKey();
        }
        for (var en : profielen.entrySet()) {
            Profiel pr = en.getValue();
            if (pr.naam != null && PlainTextComponentSerializer.plainText().serialize(safe(pr.naam)).equalsIgnoreCase(naam)) return en.getKey();
        }
        return null;
    }

    private Component naamVanId(String uuid) {
        Profiel pr = profielen.get(uuid);
        Optional<Player> p = proxy.getPlayer(UUID.fromString(uuid));
        if (pr != null && pr.naam != null) return safe(pr.naam);
        return Component.text(p.map(Player::getUsername).orElse(pr != null && pr.account != null ? pr.account : "?"));
    }

    /**
     * beroepen-bron.json (door mc uit data/spelers.json): wat daar staat en nog niet toegepast is, zet de proxy
     * één keer live (daarna geldt /beroep weer). beroepen-bron.toegepast.json onthoudt wat al gedaan is.
     */
    private void leesBeroepenBron() {
        try {
            Path f = dir.resolve("beroepen-bron.json");
            if (!Files.exists(f)) return;
            Type t = new TypeToken<Map<String, String>>() {}.getType();
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            Map<String, String> bron = o.has("beroepen") ? GSON.fromJson(o.get("beroepen"), t) : Map.of();
            Path g = dir.resolve("beroepen-bron.toegepast.json");
            Map<String, String> gedaan = new HashMap<>();
            if (Files.exists(g)) {
                JsonObject og = GSON.fromJson(Files.readString(g, StandardCharsets.UTF_8), JsonObject.class);
                if (og.has("beroepen")) gedaan = GSON.fromJson(og.get("beroepen"), t);
            }
            if (bron.equals(gedaan)) return;
            boolean iets = false;
            for (var en : bron.entrySet()) {
                if (en.getValue().equals(gedaan.get(en.getKey()))) continue;
                if (!beroepSoorten.containsKey(en.getValue())) {
                    log.warn("beroepen-bron: onbekend beroep {} voor {}", en.getValue(), en.getKey());
                    continue;
                }
                if (en.getValue().equals(STANDAARD_BEROEP)) beroepen.remove(en.getKey()); else beroepen.put(en.getKey(), en.getValue());
                iets = true;
            }
            for (String u : gedaan.keySet()) if (!bron.containsKey(u)) { beroepen.remove(u); iets = true; }
            if (iets) {
                schrijf("beroepen.json", new HashMap<>(beroepen));
                for (var en : profielen.entrySet()) en.getValue().prefix = beroepPrefix(en.getKey());
                profielenVuil = true;
                for (Player p : proxy.getAllPlayers()) stuurProfiel(p);
            }
            JsonObject uit = new JsonObject();
            uit.add("beroepen", GSON.toJsonTree(bron));
            schrijf("beroepen-bron.toegepast.json", uit);
            log.info("beroepen-bron toegepast: {}", bron);
        } catch (IOException | RuntimeException ex) {
            log.warn("beroepen-bron.json: {}", ex.toString());
        }
    }

    private void leesBeheerders() {
        leesBeroepenBron();
        try {
            Path f = dir.resolve("beheerders.json");
            if (!Files.exists(f)) return;
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            Map<String, String> m = GSON.fromJson(o.get("beheerders"), new TypeToken<Map<String, String>>() {}.getType());
            if (m != null) beheerders = Map.copyOf(m);
        } catch (IOException | RuntimeException ex) {
            log.warn("beheerders.json: {}", ex.toString());
        }
    }

    /** Beroepen geven: console, of een admin uit de centrale lijst (beheerders.json = dezelfde admins als OP). */
    private boolean magBeroepGeven(CommandSource src) {
        if (src instanceof ConsoleCommandSource || src.hasPermission("netwerk.beheer")) return true;
        return src instanceof Player p && beheerders.containsKey(p.getUniqueId().toString());
    }

    /** Geef een beroep; null = fout (tekst voor de gebruiker). */
    String zetBeroep(String naam, String beroep) {
        String id = zoekSpeler(naam);
        if (id == null) return "Speler " + naam + " ken ik niet (moet één keer op survival zijn geweest).";
        String b = beroep.toLowerCase(Locale.ROOT);
        if (!beroepSoorten.containsKey(b)) return "Onbekend beroep. Kies uit: " + String.join(", ", beroepSoorten.keySet());
        if (b.equals(STANDAARD_BEROEP)) beroepen.remove(id); else beroepen.put(id, b);
        schrijf("beroepen.json", new HashMap<>(beroepen));
        Profiel pr = profielen.get(id);
        if (pr != null) {
            pr.prefix = beroepPrefix(id);
            profielenVuil = true;
        }
        proxy.getPlayer(UUID.fromString(id)).ifPresent(this::stuurProfiel);
        iedereen(Component.text("✦ ", TextColor.color(0xe8c872)).append(naamVanId(id).colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(" is nu ", NamedTextColor.GRAY)).append(safe(beroepSoorten.get(b))));
        return null;
    }

    private final class BeroepCommando implements SimpleCommand {
        @Override
        public void execute(Invocation inv) {
            CommandSource src = inv.source();
            String[] a = inv.arguments();
            if (a.length >= 2) {
                if (!magBeroepGeven(src)) {
                    src.sendMessage(Component.text("Alleen een beheerder geeft beroepen.", NamedTextColor.GRAY));
                    return;
                }
                String fout = zetBeroep(a[0], a[1]);
                if (fout != null) src.sendMessage(Component.text(fout, NamedTextColor.GRAY));
                return;
            }
            String id = a.length == 1 ? zoekSpeler(a[0]) : src instanceof Player p ? p.getUniqueId().toString() : null;
            Component lijst = Component.empty();
            boolean eerste = true;
            for (String b : beroepSoorten.keySet()) {
                if (!eerste) lijst = lijst.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                eerste = false;
                lijst = lijst.append(safe(beroepSoorten.get(b)));
            }
            if (id != null) {
                src.sendMessage(naamVanId(id).colorIfAbsent(NamedTextColor.WHITE).append(Component.text(" is ", NamedTextColor.GRAY)).append(safe(beroepPrefix(id))));
            }
            src.sendMessage(Component.text("Beroepen: ", NamedTextColor.GRAY).append(lijst));
            if (magBeroepGeven(src)) src.sendMessage(Component.text("Geven: /beroep <speler> <beroep>", NamedTextColor.DARK_GRAY));
        }

        @Override
        public List<String> suggest(Invocation inv) {
            String[] a = inv.arguments();
            List<String> uit = new ArrayList<>();
            if (a.length <= 1) {
                String begin = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
                for (Player p : proxy.getAllPlayers()) if (p.getUsername().toLowerCase(Locale.ROOT).startsWith(begin)) uit.add(p.getUsername());
                for (Profiel pr : profielen.values()) if (pr.account != null && pr.account.toLowerCase(Locale.ROOT).startsWith(begin) && !uit.contains(pr.account)) uit.add(pr.account);
            } else if (a.length == 2) {
                for (String b : beroepSoorten.keySet()) if (b.startsWith(a[1].toLowerCase(Locale.ROOT))) uit.add(b);
            }
            return uit;
        }
    }

    /** /spelers: iedereen die het netwerk kent, met beroep en online/offline. */
    private void spelersLijst(CommandSource src) {
        Map<String, String> waar = new HashMap<>();
        for (Player p : proxy.getAllPlayers()) waar.put(p.getUniqueId().toString(), plekVan(p).orElse(""));
        Set<String> ids = new java.util.LinkedHashSet<>(waar.keySet());
        ids.addAll(profielen.keySet());
        List<String> lijst = new ArrayList<>(ids);
        lijst.sort(java.util.Comparator.comparing((String id) -> !waar.containsKey(id))
                .thenComparing(id -> PlainTextComponentSerializer.plainText().serialize(naamVanId(id)).toLowerCase(Locale.ROOT)));
        src.sendMessage(Component.text("Spelers", NamedTextColor.GOLD).append(Component.text("  " + waar.size() + " online · " + lijst.size() + " bekend", NamedTextColor.DARK_GRAY)));
        for (String id : lijst) {
            boolean on = waar.containsKey(id);
            Profiel pr = profielen.get(id);
            Component regel = Component.text(on ? " ● " : " ○ ", on ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                    .append(safe(beroepPrefix(id))).append(Component.space())
                    .append(naamVanId(id).colorIfAbsent(on ? NamedTextColor.WHITE : NamedTextColor.GRAY));
            String acc = pr != null && pr.account != null ? pr.account : proxy.getPlayer(UUID.fromString(id)).map(Player::getUsername).orElse(null);
            if (acc != null && !acc.equalsIgnoreCase(PlainTextComponentSerializer.plainText().serialize(naamVanId(id))))
                regel = regel.append(Component.text(" (" + acc + ")", NamedTextColor.DARK_GRAY));
            regel = regel.append(Component.text(on ? "  " + serverNaam(waar.get(id)) : "  offline", NamedTextColor.DARK_GRAY));
            src.sendMessage(regel);
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
            } else if (a.length >= 1 && a[0].equalsIgnoreCase("ping")) {
                // elke server uit velocity.toml pingen (mc selftest/paneel: ziet de proxy zijn backends?)
                for (RegisteredServer rs : proxy.getAllServers()) {
                    String naam = rs.getServerInfo().getName();
                    rs.ping().orTimeout(5, TimeUnit.SECONDS).whenComplete((ping, fout) -> src.sendMessage(Component.text(
                            "netwerk ping " + naam + " (" + rs.getServerInfo().getAddress() + "): "
                                    + (fout != null ? "ONBEREIKBAAR " + fout.getClass().getSimpleName()
                                    : "ok, " + ping.getVersion().getName() + ", " + ping.getPlayers().map(pl -> pl.getOnline()).orElse(0) + " spelers"))));
                }
            } else if (a.length >= 3 && a[0].equalsIgnoreCase("beroep")) {
                String fout = zetBeroep(a[1], a[2]);
                src.sendMessage(Component.text(fout == null ? "netwerk: " + a[1] + " is nu " + a[2] : fout));
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
                            .append(plek.containsKey(p.getUniqueId()) ? " plek=" + plek.get(p.getUniqueId()) : "")
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
