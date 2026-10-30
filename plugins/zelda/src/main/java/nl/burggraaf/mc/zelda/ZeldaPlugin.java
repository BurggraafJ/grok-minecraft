package nl.burggraaf.mc.zelda;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Story of Zelda als eigen spel (sessie zelda-apart, Jelle 30-10: "een aparte portal … zodat het voelt alsof het iets anders
 * is dan kitpvp"). Draait op de kitpvp-server, maar in een eigen wereld ({@link #WERELD}) met een eigen naam, sidebar,
 * tablijst (via opmaak), vak 8 naar de lobby en eigen commando's ({@code /zelda}, {@code /hint}, console {@code zelda …}).
 *
 * <p>Binnenkomen: door de Zelda-poort in de lobby. De lobby (of de proxy) zet het cookie {@link #PLEK_COOKIE} op
 * {@code zelda} en stuurt je naar de kitpvp-server; hier lezen we het cookie terwijl je nog laadt
 * ({@link AsyncPlayerSpawnLocationEvent}), zodat je meteen in deze wereld verschijnt (nooit eerst in de KitPvP-hub). Zonder
 * cookie kom je terug waar je wegging (een /terug na Zelda = weer Zelda). De plugin KitPvP bemoeit zich alleen met
 * spelers in zijn eigen wereld.
 *
 * <p>De eerste start na de verhuizing zet de oude voortgang en de eilanden over ({@link ZeldaMigratie}): eenmalig,
 * idempotent, de oude bestanden blijven staan en er komt een backup bij. Zie ZELDA.md, «Een eigen spel».
 */
public final class ZeldaPlugin extends JavaPlugin implements Listener {

    /** De wereld van Story of Zelda op de kitpvp-server. */
    static final String WERELD = "zelda";
    /** Waar je heen wilt op deze server: {@code zelda} of {@code kitpvp} (gezet door de lobby of de proxy, gewist zodra gelezen). */
    static final NamespacedKey PLEK_COOKIE = new NamespacedKey("burggraaf", "plek");
    static final String KANAAL = "netwerk:main";
    /** Kleur van Story of Zelda buiten het spel (lobby, tablijst, chat, sidebar): de Glazen Zee. */
    static final TextColor KLEUR = TextColor.color(0x7fd1c4);
    /** Vak 8: naar de lobby. */
    static final int LOBBY_VAK = 7;

    volatile World w;
    int g;                                                     // grondhoogte (dezelfde als op kitpvp: de eilanden kwamen daarvandaan)
    Zelda zelda;
    private Bouwer bouwer;
    private boolean actief = true;
    /** Waar je verschijnt als je binnenkomt (daarna zet het spel je op je rustplaats); ook gelezen buiten de hoofdthread. */
    private volatile Location wachtplek;
    /** Wat het cookie zei (of null als het niet op tijd kwam): de join kijkt er nog eens naar. */
    private final Map<UUID, String> wens = new ConcurrentHashMap<>();
    private final Set<UUID> geenAntwoord = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Scoreboard> borden = new HashMap<>();
    private long seconden;
    private boolean overgezet;

    // ── aan en uit ──────────────────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("actief", true) || Bukkit.getMotd().contains("STAGING")) {
            actief = false;
            getLogger().info("niet actief op deze server");
            return;
        }
        ZeldaMigratie migratie = new ZeldaMigratie(getDataFolder(), new File(getDataFolder().getParentFile(), "KitPvP"), getLogger());
        migratie.data();                                       // voortgang, kaarten, gemeten kaart: vóór het spel ze leest
        zelda = new Zelda(this);
        w = laadWereld(migratie);
        g = migratie.grond(w);
        boolean nagaan = overgezet;
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, KANAAL);
        getServer().getMessenger().registerIncomingPluginChannel(this, KANAAL, (kanaal, via, data) -> vanNetwerk(data));
        bouw(Set.of());
        if (nagaan && !wachtplek.clone().add(0, -1, 0).getBlock().getType().isSolid()) {   // vangnet: dan maar uit code
            migratie.nietGelukt("geen vloer op " + wachtplek.getBlockX() + "," + wachtplek.getBlockY() + "," + wachtplek.getBlockZ());
            bouw(Set.of("alles"));
        }
        zelda.aan();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getWorld().equals(w)) binnen(p);
        Bukkit.getScheduler().runTaskTimer(this, this::elkeSeconde, 40L, 20L);
    }

    @Override
    public void onDisable() {
        if (!actief || zelda == null) return;
        zelda.uit();
        if (bouwer != null) bouwer.stop();
    }

    /** De Zelda-wereld laden (de eerste keer: maken, en de eilanden van kitpvp erin zetten). Dezelfde regels als op kitpvp. */
    private World laadWereld(ZeldaMigratie migratie) {
        World wereld = maakWereld();
        if (migratie.wereldNodig()) {
            World kit = Bukkit.getWorlds().get(0);
            File naar = ZeldaMigratie.dimensie(wereld), van = ZeldaMigratie.dimensie(kit);
            getLogger().info("migratie: eilanden van " + van + " naar " + naar);
            if (!Bukkit.unloadWorld(wereld, true)) throw new IllegalStateException("de Zelda-wereld gaat niet dicht voor de migratie");
            overgezet = migratie.wereld(van, naar);
            wereld = maakWereld();
        }
        World kit = Bukkit.getWorlds().get(0);
        wereld.setDifficulty(kit.getDifficulty() == Difficulty.PEACEFUL ? Difficulty.EASY : kit.getDifficulty());
        wereld.setGameRule(GameRules.ADVANCE_TIME, false);
        wereld.setGameRule(GameRules.ADVANCE_WEATHER, false);
        wereld.setGameRule(GameRules.SPAWN_MOBS, false);
        wereld.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        wereld.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        wereld.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        wereld.setGameRule(GameRules.PVP, Boolean.TRUE.equals(kit.getGameRuleValue(GameRules.PVP)));
        wereld.setStorm(false);
        wereld.setTime(6000);
        return wereld;
    }

    private static World maakWereld() {
        World bestaand = Bukkit.getWorld(WERELD);
        if (bestaand != null) return bestaand;
        World wereld = new WorldCreator(WERELD).type(WorldType.FLAT).generateStructures(false).createWorld();
        if (wereld == null) throw new IllegalStateException("de Zelda-wereld kon niet geladen worden");
        return wereld;
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    /** Bouw de delen (eilanden en zee) waarvan de versie in gebouwd.yml niet klopt. {@code forceer}: ids, of "alles". */
    void bouw(Set<String> forceer) {
        if (bouwer != null) bouwer.stop();
        File yml = new File(getDataFolder(), "gebouwd.yml");
        YamlConfiguration stand = YamlConfiguration.loadConfiguration(yml);
        bouwer = new Bouwer(this, w, g);
        Map<String, Integer> nieuw = new LinkedHashMap<>();
        for (Deel d : zelda.delen()) {
            bouwer.plaats = forceer.contains("alles") || forceer.contains(d.id()) || stand.getInt("delen." + d.id(), -1) != d.versie();
            if (bouwer.plaats) nieuw.put(d.id(), d.versie());
            d.bouw().accept(bouwer);
        }
        bouwer.plaats = false;
        Location begin = zelda.HOOFDSTUKKEN.get(0).rust.get(0).plek;
        w.setSpawnLocation(begin);
        wachtplek = begin.clone();
        long t0 = System.currentTimeMillis();
        Bouwer deze = bouwer;
        if (!nieuw.isEmpty()) getLogger().info("bouwen: " + String.join(", ", nieuw.keySet()) + " (" + deze.inRij() + " stappen, verdeeld over ticks)");
        deze.start(() -> {
            if (nieuw.isEmpty()) return;
            stand.set("grond", g);
            nieuw.forEach((k, v) -> stand.set("delen." + k, v));
            try {
                getDataFolder().mkdirs();
                stand.save(yml);
            } catch (IOException e) {
                getLogger().warning("gebouwd.yml: " + e.getMessage());
            }
            getLogger().info("gebouwd: " + String.join(", ", nieuw.keySet()) + " in " + (System.currentTimeMillis() - t0) + " ms, " + deze.gezet + " blokken");
        });
    }

    Bouwer bouwer() {
        return bouwer;
    }

    // ── binnenkomen en weggaan ──────────────────────────────────────────────────────────────

    boolean inZelda(Player p) {
        return w != null && p.getWorld().equals(w);
    }

    /** Naar binnen (lobbypoort, de proxy, beheer, test): verder op je laatste rustplaats. */
    void binnen(Player p) {
        if (!actief || zelda.spel.speelt(p)) return;
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.CREATIVE) p.setGameMode(GameMode.ADVENTURE);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.setMetadata("kitpvp-stand", new FixedMetadataValue(this, "geen"));   // Huisdieren: geen huisdier in Zelda (Jelle 27-10)
        zelda.spel.binnen(p);
        bord(p);
        meldPlek(p, true);
    }

    /** Vak 8 of de kaart: naar de lobby (de proxy brengt je; tot dan blijf je gewoon in Zelda staan). */
    void naarLobby(Player p) {
        p.closeInventory();
        stuur(p, "BungeeCord", "Connect", "lobby");
    }

    /** Naar de KitPvP-hub op deze server (de proxy vraagt het met /kitpvp, of een test): de hub zet KitPvP. */
    void naarKitpvp(Player p) {
        World kit = Bukkit.getWorlds().get(0);
        if (!p.getWorld().equals(kit)) p.teleport(kit.getSpawnLocation());
    }

    @EventHandler
    public void onSpawnPlek(AsyncPlayerSpawnLocationEvent e) {
        World zw = w;
        Location wacht = wachtplek;
        if (!actief || zw == null || wacht == null) return;
        UUID id = e.getConnection().getProfile().getId();
        String plek = leesCookie(e.getConnection(), id);
        Location was = e.getSpawnLocation();
        boolean zelda = plek != null ? plek.equals("zelda")
                : !e.isNewPlayer() && was != null && (zw.equals(was.getWorld()) || ZeldaMigratie.inOudGebied(was, zw));
        if (zelda) e.setSpawnLocation(wacht);
        else if (was != null && zw.equals(was.getWorld())) e.setSpawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
    }

    /** Het cookie lezen (hooguit 1 s) en wissen. null = geen of geen antwoord (dan kijkt de join nog eens). */
    private String leesCookie(PlayerConfigurationConnection c, UUID id) {
        if (id == null) return null;
        try {
            byte[] b = c.retrieveCookie(PLEK_COOKIE).get(1000, TimeUnit.MILLISECONDS);
            String s = b == null ? "" : new String(b, StandardCharsets.UTF_8).trim();
            if (!s.isEmpty()) c.storeCookie(PLEK_COOKIE, new byte[0]);
            if (s.equals("zelda") || s.equals("kitpvp")) {
                wens.put(id, s);
                return s;
            }
            return null;
        } catch (Exception ex) {
            geenAntwoord.add(id);
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        if (!actief) return;
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        wens.remove(id);
        if (inZelda(p)) binnen(p);
        else p.removeMetadata("kitpvp-stand", this);
        if (geenAntwoord.remove(id)) {                       // het cookie kwam niet op tijd: nu (in het spel) nog eens
            p.retrieveCookie(PLEK_COOKIE).orTimeout(5, TimeUnit.SECONDS).whenComplete((b, fout) -> {
                String s = fout != null || b == null ? "" : new String(b, StandardCharsets.UTF_8).trim();
                if (s.isEmpty()) return;
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!p.isOnline()) return;
                    p.storeCookie(PLEK_COOKIE, new byte[0]);
                    if (s.equals("zelda") && !inZelda(p)) binnen(p);
                    else if (s.equals("kitpvp") && inZelda(p)) naarKitpvp(p);
                });
            });
        }
        for (long t : new long[]{10L, 40L}) Bukkit.getScheduler().runTaskLater(this, () -> meldPlek(p, false), t);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        if (!actief) return;
        Player p = e.getPlayer();
        if (zelda.spel.speelt(p)) zelda.spel.weg(p);
        borden.remove(p.getUniqueId());
        wens.remove(p.getUniqueId());
        geenAntwoord.remove(p.getUniqueId());
    }

    /** Uit de Zelda-wereld (naar de KitPvP-hub): eerst Zelda netjes weg (spullen, harten, muziek), daarna zet KitPvP je klaar. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAndereWereld(PlayerChangedWorldEvent e) {
        if (!actief) return;
        Player p = e.getPlayer();
        if (e.getFrom().equals(w) && !inZelda(p)) {
            if (zelda.spel.speelt(p)) zelda.spel.weg(p);
            p.removeMetadata("kitpvp-stand", this);
            borden.remove(p.getUniqueId());
            if (p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            meldPlek(p, true);
        } else if (inZelda(p) && !zelda.spel.speelt(p)) {   // op een andere manier hier beland (bv. /tp): dan ook echt binnen
            Bukkit.getScheduler().runTask(this, () -> {
                if (p.isOnline() && inZelda(p)) binnen(p);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (!actief || !zelda.spel.speelt(e.getPlayer())) return;
        Location l = zelda.spel.respawn(e.getPlayer());
        if (l != null) e.setRespawnLocation(l);
    }

    // ── het netwerk (Velocity, plugin netwerk) ──────────────────────────────────────────────

    /** Vertel de proxy waar je bent (zelda of kitpvp): voor de tablijst, de wisselregel en /terug. */
    private void meldPlek(Player p, boolean nu) {
        if (!p.isOnline()) return;
        stuur(p, KANAAL, "plek", p.getUniqueId().toString(), inZelda(p) ? "zelda" : "kitpvp");
    }

    /** Van de proxy: «plek-naar <uuid> <zelda|kitpvp>» (/zelda of /kitpvp terwijl je al op deze server bent). */
    private void vanNetwerk(byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            String type = in.readUTF();
            if (!type.equals("plek-naar")) return;
            Player p = Bukkit.getPlayer(UUID.fromString(in.readUTF()));
            String plek = in.readUTF();
            if (p == null) return;
            if (plek.equals("zelda")) binnen(p);
            else if (plek.equals("kitpvp") && inZelda(p)) naarKitpvp(p);
        } catch (IOException | RuntimeException ex) {
            getLogger().fine("onleesbaar netwerkbericht: " + ex.getMessage());
        }
    }

    private void stuur(Player p, String kanaal, String... velden) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buf);
            for (String v : velden) out.writeUTF(v);
            p.sendPluginMessage(this, kanaal, buf.toByteArray());
        } catch (IOException | RuntimeException ex) {
            getLogger().fine("naar het netwerk: " + ex.getMessage());
        }
    }

    // ── vak 8 en de sidebar ─────────────────────────────────────────────────────────────────

    /** Vak 8: «Naar de lobby». */
    ItemStack lobbyItem() {
        ItemStack it = zelda.spel.ding(Material.RECOVERY_COMPASS, "Naar de lobby", "lobby");
        var meta = it.getItemMeta();
        meta.displayName(Component.text("Naar de lobby", NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    /** De sidebar van Story of Zelda: waar je bent, je hartstukjes, en (donkergrijs) de weg naar de lobby. Rustig. */
    private void bord(Player p) {
        ZeldaSpel.Sessie ss = zelda.spel.sessie(p);
        if (ss == null) return;
        Scoreboard sb = borden.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective o = sb.getObjective("zelda");
        if (o == null) {
            o = sb.registerNewObjective("zelda", org.bukkit.scoreboard.Criteria.DUMMY, Component.text("Story of Zelda", KLEUR));
            o.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            o.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        }
        int[] st = zelda.spel.stukjes(zelda.spel.voortgang.van(p.getUniqueId()));
        Component[] regels = {
                Component.text(zelda.plek(p), NamedTextColor.WHITE),
                Component.text("Hartstukjes ", NamedTextColor.GRAY).append(Component.text(st[0], NamedTextColor.WHITE))
                        .append(Component.text(" / " + st[1], NamedTextColor.DARK_GRAY)),
                Component.text("Lobby: vak 8", NamedTextColor.DARK_GRAY),
        };
        for (int i = 0; i < regels.length; i++) {
            var s = o.getScore("r" + i);
            s.setScore(regels.length - i);
            if (!regels[i].equals(s.customName())) s.customName(regels[i]);
        }
        if (p.getScoreboard() != sb) p.setScoreboard(sb);
    }

    /** Teams van opmaak (naamtag verbergen bij een nickname, [AFK]) staan op het hoofdscorebord: overnemen. */
    private void spiegelTeams() {
        Scoreboard hoofd = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Scoreboard sb : borden.values()) {
            for (Team t : hoofd.getTeams()) {
                Team kopie = sb.getTeam(t.getName());
                if (kopie == null) kopie = sb.registerNewTeam(t.getName());
                if (!kopie.prefix().equals(t.prefix())) kopie.prefix(t.prefix());
                for (Team.Option opt : Team.Option.values()) if (kopie.getOption(opt) != t.getOption(opt)) kopie.setOption(opt, t.getOption(opt));
                for (String en : t.getEntries()) if (!kopie.hasEntry(en)) kopie.addEntry(en);
                for (String en : new ArrayList<>(kopie.getEntries())) if (!t.hasEntry(en)) kopie.removeEntry(en);
            }
        }
    }

    private void elkeSeconde() {
        seconden++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!zelda.spel.speelt(p)) continue;
            var m = p.getMetadata("kitpvp-stand");
            if (m.isEmpty() || !"geen".equals(m.get(0).asString())) p.setMetadata("kitpvp-stand", new FixedMetadataValue(this, "geen"));
            if (seconden % 2 == 0 || p.getScoreboard() != borden.get(p.getUniqueId())) bord(p);
        }
        spiegelTeams();
    }

    // ── commando's ──────────────────────────────────────────────────────────────────────────

    /** Alleen voor de console (of in de game met beheer): testen, bouwen, nakijken. */
    private static final Set<String> CONSOLE = Set.of("zelftest", "test", "lopen", "in", "uit", "rust", "vaar", "vlag", "verder", "kaart",
            "plaatjes", "bouw", "status", "hint", "migratie");

    static boolean magBeheer(CommandSender s) {
        return s.hasPermission("zelda.beheer");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!actief) {
            sender.sendMessage("Story of Zelda is hier niet actief.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "hint" -> {
                if (sender instanceof Player p) zelda.spel.hint(p);
                return true;
            }
            case "zelda" -> {
                if (sender instanceof Player p && !magBeheer(p)) {     // spelers: /zelda brengt je erheen (in Zelda: één grijze regel)
                    if (inZelda(p)) p.sendActionBar(Component.text("Story of Zelda · vak 8 = lobby", NamedTextColor.GRAY));
                    else binnen(p);
                    return true;
                }
                String sub = args.length >= 1 ? args[0].toLowerCase() : "";
                if (sender instanceof Player p && !CONSOLE.contains(sub)) zelda.spel.beheer.commando(p, args);
                else beheer(sender, args);
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (actief && command.getName().equalsIgnoreCase("zelda") && magBeheer(sender)) return zelda.spel.beheer.tab(args);
        return List.of();
    }

    /** Console: zelda zelftest | status | bouw [deel|alles] | plaatjes [begin] | migratie | in|uit <speler> | en alles van {@link Zelda#beheer}. */
    private void beheer(CommandSender s, String[] args) {
        if (!magBeheer(s)) {
            s.sendMessage("Geen toegang.");
            return;
        }
        String sub = args.length >= 1 ? args[0].toLowerCase() : "status";
        switch (sub) {
            case "zelftest" -> {
                List<String> fouten = new ArrayList<>(), info = new ArrayList<>();
                zelftest(fouten, info);
                s.sendMessage("zelda zelftest: " + (fouten.isEmpty() ? "OK" : "FOUT (" + fouten.size() + ")") + " · " + String.join(" ", info));
                for (String f : fouten) s.sendMessage("  ✗ " + f);
            }
            case "bouw" -> {
                Set<String> wat = new HashSet<>();
                for (int i = 1; i < args.length; i++) wat.add(args[i].toLowerCase());
                if (wat.isEmpty()) wat.add("alles");
                bouw(wat);
                s.sendMessage("zelda bouw: " + String.join(", ", wat) + " (" + bouwer.inRij() + " stappen)");
            }
            case "plaatjes" -> {
                Kaart.alleen = args.length >= 2 ? args[1] : null;
                try {
                    for (String f : ZeldaPlaatjes.maak(w, g, new File(getDataFolder(), "plaatjes"))) s.sendMessage("zelda plaatjes: " + f);
                } catch (IOException | RuntimeException e) {
                    s.sendMessage("zelda plaatjes mislukt: " + e);
                } finally {
                    Kaart.alleen = null;
                }
            }
            case "migratie" -> s.sendMessage("zelda migratie: " + new ZeldaMigratie(getDataFolder(), new File(getDataFolder().getParentFile(), "KitPvP"), getLogger()).stand());
            case "in" -> {
                Player p = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (p == null) {
                    s.sendMessage("zelda in <speler>: speler niet online");
                    return;
                }
                binnen(p);
                s.sendMessage("zelda: " + p.getName() + " " + (zelda.spel.speelt(p) ? "is in Zelda (" + zelda.spel.sessie(p).rustplaats().naam + ")" : "kon niet naar binnen"));
            }
            case "uit" -> {
                Player p = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (p == null) {
                    s.sendMessage("zelda uit <speler>: speler niet online (gaat naar de KitPvP-hub op deze server)");
                    return;
                }
                naarKitpvp(p);
                s.sendMessage("zelda: " + p.getName() + " is " + (inZelda(p) ? "nog in Zelda" : "in de KitPvP-hub"));
            }
            case "status" -> {
                if (args.length >= 1) {
                    zelda.beheer(s, voor("zelda", args));
                    return;
                }
                s.sendMessage("zelda: wereld=" + (w == null ? "-" : w.getName()) + " grond=" + g + " spelers=" + zelda.spel.sessies.size()
                        + " bouwen=" + (bouwer != null && bouwer.bezig() ? bouwer.inRij() + " stappen" : "klaar")
                        + " · zelda zelftest | test <speler> … | lopen <eiland> | in|uit <speler> | reset <speler> [spel] | bouw [deel|alles] | plaatjes [begin] | migratie · bijsturen: spelers|niveau|steun|volg|doden|spel|samen");
            }
            default -> zelda.beheer(s, voor("zelda", args));
        }
    }

    private static String[] voor(String eerst, String[] args) {
        String[] uit = new String[args.length + 1];
        uit[0] = eerst;
        System.arraycopy(args, 0, uit, 1, args.length);
        return uit;
    }

    /** {@code zelda zelftest}: de wereld, de migratie en alles van het spel (delen, rustplaatsen, kamers, opslag, …). */
    private void zelftest(List<String> fouten, List<String> info) {
        if (w == null) {
            fouten.add("wereld: niet geladen");
            return;
        }
        if (!w.getName().equals(WERELD)) fouten.add("wereld heet " + w.getName());
        if (w.equals(Bukkit.getWorlds().get(0))) fouten.add("Zelda draait in de hoofdwereld (hoort een eigen wereld te zijn)");
        if (Boolean.TRUE.equals(w.getGameRuleValue(GameRules.ADVANCE_TIME)) || Boolean.TRUE.equals(w.getGameRuleValue(GameRules.SPAWN_MOBS)))
            fouten.add("wereld: tijd of mobs staan aan");
        if (w.getDifficulty() != Bukkit.getWorlds().get(0).getDifficulty() && Bukkit.getWorlds().get(0).getDifficulty() != Difficulty.PEACEFUL)
            fouten.add("wereld: moeilijkheid " + w.getDifficulty() + " (kitpvp " + Bukkit.getWorlds().get(0).getDifficulty() + ": de schade is daarop afgesteld)");
        if (wachtplek == null || !wachtplek.getWorld().equals(w)) fouten.add("geen plek om binnen te komen");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (inZelda(p) && !zelda.spel.speelt(p) && !p.isDead()) fouten.add(p.getName() + " staat in de Zelda-wereld zonder spel");
            if (zelda.spel.speelt(p) && !inZelda(p)) fouten.add(p.getName() + " speelt Zelda buiten de Zelda-wereld");
            if (zelda.spel.speelt(p) && (p.getInventory().getItem(LOBBY_VAK) == null || p.getInventory().getItem(LOBBY_VAK).getType() != Material.RECOVERY_COMPASS))
                fouten.add(p.getName() + ": geen «Naar de lobby» in vak 8");
            if (zelda.spel.speelt(p) && p.getScoreboard().getObjective("zelda") == null) fouten.add(p.getName() + ": geen Zelda-sidebar");
        }
        ZeldaMigratie m = new ZeldaMigratie(getDataFolder(), new File(getDataFolder().getParentFile(), "KitPvP"), getLogger());
        if (m.wereldNodig()) fouten.add("migratie: de wereld is nog niet overgezet");
        zelda.zelftest(fouten, info);
        info.add("wereld=" + w.getName() + " migratie=" + m.kort());
    }

    // voor de test
    Location wachtplek() {
        return wachtplek;
    }

    Map<UUID, Scoreboard> borden() {
        return borden;
    }

    static List<String> lijst(String... s) {
        return Arrays.asList(s);
    }

    /** Alle Zelda-entiteiten in de Zelda-wereld. */
    int entiteiten() {
        int n = 0;
        if (w != null) for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(ZeldaSpel.TAG)) n++;
        return n;
    }
}
