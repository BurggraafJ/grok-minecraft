package nl.burggraaf.mc.lobbymelding;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * De lobby van Burggraaf Craft: de tuin (een klein tuindorp, zie {@link Tuin}) met de poorten naar Survival en
 * KitPvP en tien parkour-levels ({@link Banen}).
 * <ul>
 * <li>Veilig: onkwetsbaar, geen honger, adventure; wie onder de wereld valt gaat naar de spawn. Op staging niets.</li>
 * <li>Bouwen: een nieuwe {@link #BOUW_VERSIE} bouwt bij de start vanzelf, verdeeld over ticks ({@link Bouwer});
 * oude bouwsels buiten de tuin worden opgeruimd. Alles komt uit de code.</li>
 * <li>Parkour: start op een 3×3 vlak met een groen midden, finish 3×3 met een goud midden. Terug naar de start
 * alleen bij echt grondcontact ({@link Regels#grondcontact}). Voortgang in voortgang.yml, beste tijden in
 * tijden.yml.</li>
 * <li>Meldingen alleen in de actiebalk, kort en grijs. Geen titels, geen chat.</li>
 * <li>Velocity-plugin netwerk: aftellen, wachten, en "hub" (bewust naar de lobby = naar de start van je hoogste
 * level). {@code /lobbymelding terug} is het oude pad zonder netwerk.</li>
 * </ul>
 */
public final class LobbyMelding extends JavaPlugin implements Listener, PluginMessageListener {

    /** Ophogen = bij de volgende start de hele tuin opnieuw (verschil-)bouwen. 8 = het tuindorp (oktober 2026). */
    static final int BOUW_VERSIE = 8;
    private static final int OUDE_STRAAL = 130;          // tot hier stond de tuin van versie 1..7 (−130..130)

    private static final String KANAAL = "BungeeCord";
    private static final String NETWERK = "netwerk:main";
    private static final TextColor GRIJS = NamedTextColor.GRAY;

    /** Wat de Velocity-plugin netwerk over een speler zegt: "aftellen:<server>:<Naam>" of "wacht:<server>:<Naam>". */
    private final Map<UUID, String> modus = new HashMap<>();
    private boolean netwerkGezien = false;
    private final Map<UUID, Long> poortTot = new HashMap<>();
    private final Map<UUID, Long> hintTot = new HashMap<>();
    private final Set<UUID> aftellend = new HashSet<>();
    /** Wie hier vrijwillig is (/blijf zonder netwerk): niet automatisch terug. */
    private final Set<UUID> blijft = new HashSet<>();
    private boolean actief = true;

    private World wereld;
    private int grondY;
    private Location spawn;
    private Tuin tuin;                                    // null tot de tekening klaar is
    private List<Baan> banen;                             // null tot de tuin in de wereld staat
    private final Map<Long, int[]> blokNaarStap = new HashMap<>();
    private Bouwer bouwer;

    private YamlConfiguration voortgang, tijden;
    private File voortgangBestand, tijdenBestand;

    private NamespacedKey itemKey, kiezerKey, resetKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Staging is een kopie van survival met dezelfde plugins: daar niets doen (mc zet daar motd STAGING).
        if (!getConfig().getBoolean("actief", true) || Bukkit.getMotd().contains("STAGING")) {
            actief = false;
            getLogger().info("niet actief op deze server");
            return;
        }
        itemKey = new NamespacedKey(this, "blijf-item");
        kiezerKey = new NamespacedKey(this, "level-kiezer");
        resetKey = new NamespacedKey(this, "reset-item");
        leesBlijvers();
        voortgangBestand = new File(getDataFolder(), "voortgang.yml");
        voortgang = YamlConfiguration.loadConfiguration(voortgangBestand);
        getServer().getMessenger().registerOutgoingPluginChannel(this, KANAAL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, NETWERK);
        getServer().getMessenger().registerIncomingPluginChannel(this, NETWERK, this);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, this::tuinKlaarzetten);
        Bukkit.getScheduler().runTaskTimer(this, this::elkeHalveSeconde, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, this::controleerRuns, 4L, 4L);
        Bukkit.getScheduler().runTaskTimer(this, this::poortDeeltjes, 40L, 20L);
    }

    @Override
    public void onDisable() {
        if (!actief) return;
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains("lobbymelding")) e.remove();
    }

    // ── de tuin klaarzetten: tekening, bouwen (alleen bij een nieuwe versie), labels ───────

    private File marker() {
        return new File(getDataFolder(), "gebouwd.txt");
    }

    /** gebouwd.txt: "versie:grondY[:straal]" (versie 1..7 schreven alleen "versie:grondY"). */
    private int[] leesMarker() {
        try {
            String[] d = Files.readString(marker().toPath()).trim().split(":");
            int v = Integer.parseInt(d[0]), g = Integer.parseInt(d[1]);
            int r = d.length > 2 ? Integer.parseInt(d[2]) : OUDE_STRAAL;
            return new int[]{v, g, r};
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void tuinKlaarzetten() {
        wereld = Bukkit.getWorlds().get(0);
        int[] m = leesMarker();
        if (m != null) grondY = m[1];
        else {
            Location s = wereld.getSpawnLocation();
            grondY = Math.max(wereld.getMinHeight() + 3, Math.min(wereld.getMaxHeight() - 50, wereld.getHighestBlockYAt(s)));
        }
        spawn = new Location(wereld, 0.5, grondY + 1, 9.5, 180f, 0f);
        wereld.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);       // de tuin blijft zoals getekend (geen groei, verdorring, klimop)
        wereld.setGameRule(GameRules.MOB_GRIEFING, false);        // schapen eten het gras niet
        for (int cx = -4; cx <= 3; cx++)                         // de tuin blijft geladen (labels, dieren, borden)
            for (int cz = -4; cz <= 3; cz++) wereld.addPluginChunkTicket(cx, cz, this);
        boolean bouwen = m == null || m[0] != BOUW_VERSIE || m[1] != grondY;
        int opruim = m == null ? 0 : m[0] != BOUW_VERSIE ? Math.max(m[2], Tuin.STRAAL) : 0;
        final int g = grondY;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            long t0 = System.currentTimeMillis();
            Tuin t = new Tuin(g);
            t.bouw();
            long ms = System.currentTimeMillis() - t0;
            Bukkit.getScheduler().runTask(this, () -> {
                tuin = t;
                getLogger().info("tuin getekend in " + ms + " ms (" + t.banen.size() + " levels)");
                if (bouwen) bouw(opruim);
                else tuinInGebruik();
            });
        });
    }

    /** De tuin (opnieuw) in de wereld zetten; daarna borden, labels en dieren. */
    private void bouw(int opruimStraal) {
        if (tuin == null || (bouwer != null && bouwer.bezig())) return;
        banen = null;
        getLogger().info("tuin bouwen (versie " + BOUW_VERSIE + ")…");
        bouwer = new Bouwer(this, wereld, tuin.p, opruimStraal, () -> {
            borden();
            try {
                Files.writeString(marker().toPath(), BOUW_VERSIE + ":" + grondY + ":" + Tuin.STRAAL);
            } catch (IOException e) {
                getLogger().warning("gebouwd.txt: " + e.getMessage());
            }
            migreerTijden();
            tuinInGebruik();
            for (Player p : wereld.getPlayers()) p.teleport(spawn);
        });
        bouwer.start();
    }

    /** Teksten op de borden (staan als blok in de wereld; alleen bij het bouwen). */
    private void borden() {
        for (Plan.Bord b : tuin.p.borden) {
            Block bl = wereld.getBlockAt(b.x(), b.y(), b.z());
            if (!(bl.getState() instanceof Sign s)) {
                getLogger().warning("geen bord op " + b.x() + "," + b.y() + "," + b.z());
                continue;
            }
            for (int i = 0; i < 4; i++) s.getSide(Side.FRONT).line(i, Component.text(b.regels()[i]));
            s.setWaxed(true);
            s.update(true, false);
        }
    }

    /** Tuin staat: banen in gebruik, spawn, labels, ranglijsten en dieren. */
    private void tuinInGebruik() {
        tijdenBestand = new File(getDataFolder(), "tijden.yml");
        tijden = YamlConfiguration.loadConfiguration(tijdenBestand);
        blokNaarStap.clear();
        for (int l = 0; l < tuin.banen.size(); l++) {
            Baan b = tuin.banen.get(l);
            for (int i = 0; i < b.stappen.size(); i++) {
                Baan.Stap s = b.stappen.get(i);
                for (int x = s.x1(); x <= s.x2(); x++)
                    for (int z = s.z1(); z <= s.z2(); z++) {
                        if (s.soort() == Baan.Soort.LADDER) for (int y = s.y(); y <= s.top(); y++) blokNaarStap.put(sleutel(x, y, z), new int[]{l, i});
                        else blokNaarStap.put(sleutel(x, s.y(), z), new int[]{l, i});
                    }
            }
        }
        banen = tuin.banen;
        spawn = new Location(wereld, tuin.spawnX, grondY + 1, tuin.spawnZ, tuin.spawnYaw, 0f);
        wereld.setSpawnLocation(spawn);
        for (Entity e : wereld.getEntities()) if (e.getScoreboardTags().contains("lobbymelding")) e.remove();
        for (Plan.Label lb : tuin.p.labels) tuinLabel(new Location(wereld, lb.x(), lb.y(), lb.z()), lb.tekst(), lb.schaal());
        ranglijsten = new TextDisplay[banen.size()];
        for (int l = 0; l < banen.size(); l++) {
            Baan b = banen.get(l);
            Location plek = bordPlek(b);
            ranglijsten[l] = wereld.spawn(plek, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(70, 0, 0, 0));
                d.setShadowed(false);
                schaal(d, 0.55f);
                d.addScoreboardTag("lobbymelding");
            });
            werkRanglijstBij(l);
        }
        if (tuin.prikbord != null) {
            double[] pb = tuin.prikbord;
            Location plek = new Location(wereld, pb[0], pb[1], pb[2], (float) pb[3], 0f);
            prikbord = wereld.spawn(plek, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.FIXED);
                d.setRotation((float) pb[3], 0f);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(false);
                schaal(d, 0.42f);
                d.addScoreboardTag("lobbymelding");
            });
            werkPrikbordBij();
        }
        dieren();
        poortenGezien = true;
        getLogger().info("lobby klaar: " + banen.size() + " levels, " + tuin.poorten.size() + " poorten, grond " + grondY);
    }

    private boolean poortenGezien = false;

    /** Plek van het bordje met de beste tijden: boven het startbord (eerste bord van dat level). */
    private Location bordPlek(Baan b) {
        for (Plan.Bord bd : tuin.p.borden)
            if (bd.regels()[0].equals("Parkour " + b.nr)) return new Location(wereld, bd.x() + 0.5, bd.y() + 1.75, bd.z() + 0.5);
        return new Location(wereld, b.sx, b.sy + 2.5, b.sz);
    }

    private static void schaal(TextDisplay d, float s) {
        d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                new org.joml.Vector3f(s, s, s), new org.joml.AxisAngle4f()));
    }

    private void tuinLabel(Location plek, String mm, float schaal) {
        Component tekst;
        try {
            tekst = MiniMessage.miniMessage().deserialize(mm);
        } catch (RuntimeException e) {
            tekst = Component.text(mm);
        }
        final Component t = tekst;
        wereld.spawn(plek, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(t);
            d.setShadowed(true);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            schaal(d, schaal);
            d.addScoreboardTag("lobbymelding");
        });
    }

    /** Een paar dieren voor de gezelligheid: kippen in de ren, schapen in de wei, een kat op een bankje. */
    private void dieren() {
        for (Plan.Dier d : tuin.p.dieren) {
            EntityType type;
            try {
                type = EntityType.valueOf(d.soort());
            } catch (IllegalArgumentException e) {
                continue;
            }
            Location l = new Location(wereld, d.x(), d.y(), d.z(), d.yaw(), 0f);
            wereld.spawnEntity(l, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
                e.setPersistent(false);
                e.addScoreboardTag("lobbymelding");
                e.setSilent(type == EntityType.SHEEP);
                if (e instanceof LivingEntity le) {
                    le.setRemoveWhenFarAway(false);
                    le.setInvulnerable(true);
                    if (d.stil()) le.setAI(false);
                }
                if (e instanceof Chicken c) c.setEggLayTime(Integer.MAX_VALUE);
                if (e instanceof Cat c) {
                    c.setCatType(org.bukkit.Registry.CAT_VARIANT.get(NamespacedKey.minecraft("red")));
                    c.setLyingDown(true);
                }
            });
        }
    }

    // ── tijden en voortgang ─────────────────────────────────────────────────────────────────

    /**
     * Bij de eerste start met de nieuwe levels (bouwversie 8): de oude tijden horen bij andere banen. Ze gaan
     * ongewijzigd naar tijden-tuin1.yml (niets weg); de nieuwe ranglijsten beginnen leeg. voortgang.yml blijft.
     */
    private void migreerTijden() {
        File t = new File(getDataFolder(), "tijden.yml");
        if (!t.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(t);
        if (y.getInt("banen", 1) >= 2) return;
        File oud = new File(getDataFolder(), "tijden-tuin1.yml");
        try {
            if (!oud.exists()) Files.copy(t.toPath(), oud.toPath());
            YamlConfiguration nieuw = new YamlConfiguration();
            nieuw.set("banen", 2);
            nieuw.save(t);
            getLogger().info("oude parkourtijden bewaard in tijden-tuin1.yml; nieuwe ranglijsten beginnen leeg");
        } catch (IOException e) {
            getLogger().warning("tijden migreren: " + e.getMessage());
        }
    }

    private int gehaald(UUID id) {
        return voortgang == null ? 0 : voortgang.getInt(id.toString(), 0);
    }

    private int gespeeld(UUID id) {
        return voortgang == null ? 0 : voortgang.getInt("gespeeld." + id, 0);
    }

    private void bewaarVoortgang() {
        try {
            voortgang.save(voortgangBestand);
        } catch (IOException e) {
            getLogger().warning("voortgang niet op te slaan: " + e.getMessage());
        }
    }

    private void slaGehaaldOp(UUID id, int level) {
        if (voortgang == null || level <= gehaald(id)) return;
        voortgang.set(id.toString(), level);
        bewaarVoortgang();
    }

    private void slaGespeeldOp(UUID id, int level) {
        if (voortgang == null || level <= gespeeld(id)) return;
        voortgang.set("gespeeld." + id, level);
        bewaarVoortgang();
    }

    private long besteTijd(UUID id, int l) {
        return tijden == null ? 0 : tijden.getLong("level" + (l + 1) + "." + id + ".ms", 0);
    }

    private List<Map.Entry<String, Long>> lijst(int l) {
        List<Map.Entry<String, Long>> lijst = new ArrayList<>();
        var sec = tijden == null ? null : tijden.getConfigurationSection("level" + (l + 1));
        if (sec != null) for (String k : sec.getKeys(false)) lijst.add(Map.entry(sec.getString(k + ".naam", "?"), sec.getLong(k + ".ms")));
        lijst.sort(Map.Entry.comparingByValue());
        return lijst;
    }

    /** Bewaar een tijd; geeft 2 = nieuw levelrecord, 1 = eigen record, 0 = niet beter. */
    private int slaTijdOp(Player p, int l, long ms) {
        if (tijden == null) return 0;
        long oud = besteTijd(p.getUniqueId(), l);
        if (oud != 0 && oud <= ms) return 0;
        List<Map.Entry<String, Long>> voor = lijst(l);
        boolean record = voor.isEmpty() || ms < voor.get(0).getValue();
        String pad = "level" + (l + 1) + "." + p.getUniqueId();
        tijden.set("banen", 2);
        tijden.set(pad + ".ms", ms);
        tijden.set(pad + ".naam", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(p.displayName()));
        try {
            tijden.save(tijdenBestand);
        } catch (IOException e) {
            getLogger().warning("tijden.yml: " + e.getMessage());
        }
        werkRanglijstBij(l);
        werkPrikbordBij();
        return record ? 2 : 1;
    }

    private TextDisplay[] ranglijsten = new TextDisplay[0];
    private TextDisplay prikbord;

    private void werkRanglijstBij(int l) {
        if (l >= ranglijsten.length || ranglijsten[l] == null || !ranglijsten[l].isValid()) return;
        Component t = Component.text("Beste tijden", NamedTextColor.WHITE);
        List<Map.Entry<String, Long>> lijst = lijst(l);
        if (lijst.isEmpty()) t = t.append(Component.newline()).append(Component.text("nog niemand", GRIJS));
        for (int i = 0; i < Math.min(3, lijst.size()); i++)
            t = t.append(Component.newline()).append(Component.text((i + 1) + ". " + lijst.get(i).getKey() + "  " + Regels.tijd(lijst.get(i).getValue()), GRIJS));
        ranglijsten[l].text(t);
    }

    private void werkPrikbordBij() {
        if (prikbord == null || !prikbord.isValid() || banen == null) return;
        Component t = Component.text("Records", NamedTextColor.WHITE);
        for (int l = 0; l < banen.size(); l++) {
            List<Map.Entry<String, Long>> lijst = lijst(l);
            String r = lijst.isEmpty() ? "—" : lijst.get(0).getKey() + " " + Regels.tijd(lijst.get(0).getValue());
            t = t.append(Component.newline()).append(Component.text((l + 1) + " " + banen.get(l).naam + "  ", NamedTextColor.WHITE))
                    .append(Component.text(r, GRIJS));
        }
        prikbord.text(t);
    }

    // ── parkour ─────────────────────────────────────────────────────────────────────────────

    /** Een lopende poging: level, starttijd (0 = staat nog op de start), verste stap, klaar. */
    private static final class Run {
        final int level;
        long t0 = 0, opStart = 0;
        int verst = 0;
        boolean klaar = false;

        Run(int level) {
            this.level = level;
        }
    }

    private final Map<UUID, Run> runs = new HashMap<>();
    private final Map<UUID, Integer> huidigLevel = new HashMap<>();
    private final Map<UUID, Long> resetTot = new HashMap<>();

    private static long sleutel(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Stap onder de voeten (midden en de vier hoeken van de voeten), of de ladder waar je in hangt. */
    private int[] stapOnder(Location l, boolean klimt) {
        if (klimt) {
            int[] s = blokNaarStap.get(sleutel(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
            if (s != null) return s;
        }
        int y = (int) Math.floor(l.getY() - 0.01);
        double[][] hoek = {{0, 0}, {0.29, 0.29}, {-0.29, 0.29}, {0.29, -0.29}, {-0.29, -0.29}};
        for (double[] h : hoek) {
            int[] s = blokNaarStap.get(sleutel((int) Math.floor(l.getX() + h[0]), y, (int) Math.floor(l.getZ() + h[1])));
            if (s != null) return s;
        }
        return null;
    }

    private void beweeg(Player p, Location to) {
        if (banen == null) return;
        UUID id = p.getUniqueId();
        if (aftellend.contains(id)) return;
        long nu = System.currentTimeMillis();
        boolean klimt = p.isClimbing();
        int[] st = p.isOnGround() || klimt ? stapOnder(to, klimt) : null;
        Run r = runs.get(id);
        if (st != null) {
            int l = st[0], i = st[1];
            Baan b = banen.get(l);
            if (i == 0) {                                                 // op de start
                if (r == null || r.level != l || r.t0 != 0 || r.klaar) {
                    r = new Run(l);
                    runs.put(id, r);
                    huidigLevel.put(id, l);
                    slaGespeeldOp(id, l + 1);
                    balk(p, "Level " + b.nr + " · " + b.naam);
                }
                r.opStart = nu;
                return;
            }
            if (r != null && r.level == l && !r.klaar) {
                if (r.t0 == 0) r.t0 = r.opStart == 0 ? nu : r.opStart;
                r.verst = Math.max(r.verst, i);
                if (i == b.stappen.size() - 1) finish(p, r, nu);
            }
            return;
        }
        if (r == null || r.klaar) return;
        if (r.t0 == 0) r.t0 = r.opStart == 0 ? nu : r.opStart;          // van de start af: de tijd loopt
        if (resetTot.getOrDefault(id, 0L) > nu) return;
        Baan b = banen.get(r.level);
        if (!Regels.grondcontact(to.getY(), p.isOnGround(), klimt, p.isInWater(), false, grondY)) return;
        double afstand = Math.hypot(to.getX() - b.sx, to.getZ() - b.sz);
        if (Regels.naastStart(r.verst, afstand)) {                       // naast de start gestapt: niet aan het spelen
            runs.remove(id);
            return;
        }
        naarStart(p, r.level, true);
    }

    /** Zet de speler op de start van level l; de run begint opnieuw zodra hij eraf stapt. */
    private void naarStart(Player p, int l, boolean opnieuw) {
        Baan b = banen.get(l);
        UUID id = p.getUniqueId();
        resetTot.put(id, System.currentTimeMillis() + 700);
        p.setFallDistance(0);
        p.setVelocity(new org.bukkit.util.Vector());
        p.teleport(new Location(wereld, b.sx, b.sy, b.sz, b.yaw, 8f));
        Run r = new Run(l);
        r.opStart = System.currentTimeMillis();
        runs.put(id, r);
        huidigLevel.put(id, l);
        slaGespeeldOp(id, l + 1);
        if (opnieuw) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.5f);
            balk(p, "Level " + b.nr + " · opnieuw");
        } else {
            balk(p, "Level " + b.nr + " · " + b.naam);
        }
    }

    private void finish(Player p, Run r, long nu) {
        UUID id = p.getUniqueId();
        r.klaar = true;
        int l = r.level;
        Baan b = banen.get(l);
        long ms = nu - r.t0;
        slaGehaaldOp(id, l + 1);
        long oudPb = besteTijd(id, l);
        int uit = slaTijdOp(p, l, ms);
        String extra = uit == 2 ? " · record" : uit == 1 && oudPb > 0 ? " · eigen record" : "";
        boolean laatste = l + 1 >= banen.size();
        balk(p, "✔ Level " + b.nr + " · " + Regels.tijd(ms) + extra + (laatste ? " · alle levels gehaald" : ""));
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.5f, 1.2f);
        if (laatste) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Run nu2 = runs.get(id);
            if (!p.isOnline() || nu2 != r || aftellend.contains(id) || banen == null) return;
            Baan.Stap f = b.finish();
            Location pl = p.getLocation();
            boolean opFinish = pl.getX() >= f.x1() - 1 && pl.getX() <= f.x2() + 2 && pl.getZ() >= f.z1() - 1 && pl.getZ() <= f.z2() + 2;
            if (!opFinish) return;                                        // weggelopen: dan niet verder
            naarStart(p, l + 1, false);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.4f);
        }, 60L);
    }

    /** Elke 4 ticks: wie in een run stil in het water ligt of door de vloer zakt, ook zonder beweging. */
    private void controleerRuns() {
        if (banen == null) return;
        for (UUID id : new ArrayList<>(runs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                runs.remove(id);
                continue;
            }
            Run r = runs.get(id);
            if (r.klaar || r.t0 == 0) continue;
            if (p.isInWater() || p.getLocation().getY() < grondY - 1.5) beweeg(p, p.getLocation());
        }
    }

    private void elkeHalveSeconde() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            maakVeilig(p);
            geefItems(p);
            UUID id = p.getUniqueId();
            if (aftellend.contains(id)) continue;
            String m = modus.get(id);
            Run r = runs.get(id);
            if (m != null && m.startsWith("aftellen:")) continue;         // de proxy (netwerk) toont het 3-2-1 zelf
            if (m != null && m.startsWith("wacht:")) {
                balk(p, m.split(":", 3)[2] + " herstart even · je gaat vanzelf terug");
            } else if (r != null && !r.klaar && r.t0 > 0 && banen != null) {
                balk(p, "Level " + banen.get(r.level).nr + " · " + Regels.tijd(System.currentTimeMillis() - r.t0));
            } else if (hintTot.getOrDefault(id, 0L) > System.currentTimeMillis()) {
                balk(p, "Je blijft in de lobby · ster = server kiezen");
            }
        }
    }

    private static void balk(Player p, String tekst) {
        p.sendActionBar(Component.text(tekst, GRIJS));
    }

    /** Via /hub, de deur op kitpvp of een wissel naar de lobby: bij de start van je hoogste level (nooit = spawn). */
    private void naarHoogsteLevel(Player p) {
        if (!p.isOnline() || banen == null) return;
        int hoogste = Regels.hubLevel(gehaald(p.getUniqueId()), gespeeld(p.getUniqueId()), banen.size());
        if (hoogste <= 0) return;
        int l = hoogste - 1;
        naarStart(p, l, false);
        balk(p, "Lobby · parkour level " + (l + 1) + ": " + banen.get(l).naam);
    }

    /** Naar het level waar de speler aan toe is (/parkour vanuit survival). */
    private void naarVolgendLevel(Player p) {
        if (banen == null) return;
        int l = Regels.volgend(gehaald(p.getUniqueId()), banen.size());
        naarStart(p, l, false);
        balk(p, "Parkour level " + (l + 1) + ": " + banen.get(l).naam + " · /terug = survival");
    }

    private void stopRun(UUID id) {
        runs.remove(id);
    }

    // ── veilig ──────────────────────────────────────────────────────────────────────────────

    private void maakVeilig(Player p) {
        p.setInvulnerable(true);
        if (p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setRemainingAir(p.getMaximumAir());
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (!p.isDead() && max != null && p.getHealth() < max.getValue()) p.setHealth(max.getValue());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID && spawn != null) p.teleport(spawn);
        } else if (e.getEntity().getScoreboardTags().contains("lobbymelding")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHonger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (spawn != null) e.setRespawnLocation(spawn);
    }

    /** Blokken in de tuin zijn om naar te kijken: geen kisten, potten of akkers. Deuren, hekjes en bellen mogen. */
    @EventHandler(priority = EventPriority.LOW)
    public void onBlokKlik(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !actief) return;
        String n = b.getType().name();
        if (e.getAction() == Action.PHYSICAL) {
            if (b.getType() == Material.FARMLAND || n.endsWith("TURTLE_EGG")) e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        boolean mag = (n.endsWith("_DOOR") && !n.equals("IRON_DOOR")) || n.endsWith("_FENCE_GATE") || b.getType() == Material.BELL;
        if (!mag) e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler
    public void onDierKlik(PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains("lobbymelding")) e.setCancelled(true);
    }

    // ── bewegen: vallen, poorten, parkour ───────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location to = e.getTo();
        if (spawn == null) return;
        if (to.getY() < grondY - 8) {
            stopRun(p.getUniqueId());
            p.teleport(spawn);
            return;
        }
        if (tuin != null && to.getBlockY() > grondY && to.getBlockY() <= grondY + 4) {
            for (Tuin.Poort pt : tuin.poorten) {
                if (to.getBlockZ() != pt.z() || to.getBlockX() < pt.x1() || to.getBlockX() > pt.x2()) continue;
                long nu = System.currentTimeMillis();
                if (poortTot.getOrDefault(p.getUniqueId(), 0L) > nu) break;
                poortTot.put(p.getUniqueId(), nu + 4000);
                naarServer(p, pt.server());
                break;
            }
        }
        Location van = e.getFrom();
        if (van.getX() == to.getX() && van.getY() == to.getY() && van.getZ() == to.getZ()) return;
        beweeg(p, to);
    }

    /** Wie (door iets anders dan een sprong) wegspringt naar een andere plek, stopt met zijn run. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Run r = runs.get(e.getPlayer().getUniqueId());
        if (r == null || banen == null || e.getTo() == null) return;
        if (!e.getTo().getWorld().equals(wereld) || afstandTotBaan(banen.get(r.level), e.getTo()) > 3) runs.remove(e.getPlayer().getUniqueId());
    }

    private static double afstandTotBaan(Baan b, Location l) {
        double best = Double.MAX_VALUE;
        for (Baan.Stap s : b.stappen) best = Math.min(best, Math.hypot(l.getX() - s.mx(), l.getZ() - s.mz()));
        return best;
    }

    /** Zachte deeltjes in de doorgang van de poorten (elke seconde een paar). */
    private void poortDeeltjes() {
        if (tuin == null || wereld == null || wereld.getPlayers().isEmpty()) return;
        for (Tuin.Poort pt : tuin.poorten) {
            boolean surv = pt.server().equals("survival");
            Location l = new Location(wereld, (pt.x1() + pt.x2()) / 2.0 + 0.5, grondY + 2.5, pt.z() + 0.5);
            wereld.spawnParticle(org.bukkit.Particle.DUST, l, 5, 0.9, 1.2, 0.15, 0,
                    new org.bukkit.Particle.DustOptions(surv ? org.bukkit.Color.fromRGB(0x9ad59a) : org.bukkit.Color.fromRGB(0xe58b8b), 1.0f));
        }
    }

    // ── commando's ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!actief) {
            sender.sendMessage("LobbyMelding is hier niet actief.");
            return true;
        }
        String naam = command.getName().toLowerCase();
        if (naam.equals("parkour")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Alleen voor spelers.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("kies")) openKiezer(p);
            else opnieuw(p);
            return true;
        }
        if (naam.equals("spawn")) {
            if (sender instanceof Player p && spawn != null) {
                stopRun(p.getUniqueId());
                p.teleport(spawn);
            }
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "terug" -> {
                int s = getConfig().getInt("aftellen", 5);
                boolean alle = args.length >= 2 && args[1].equalsIgnoreCase("alle");
                if (alle) aftellen(Math.max(0, Math.min(10, s)), List.copyOf(Bukkit.getOnlinePlayers()));
                else aftellen(Math.max(0, Math.min(10, s)), Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !blijft.contains(p.getUniqueId())).map(p -> (Player) p).toList());
                sender.sendMessage("aftellen " + s + " s gestart voor " + Bukkit.getOnlinePlayers().size() + " spelers");
            }
            case "wie-terug" -> sender.sendMessage("terug: " + (netwerkGezien ? "" : String.join(",", Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !blijft.contains(p.getUniqueId())).map(Player::getName).toList())));
            case "info" -> sender.sendMessage("lobby: tuin versie " + BOUW_VERSIE + ", grond " + grondY + ", poorten " + (tuin == null ? 0 : tuin.poorten.size())
                    + ", levels " + (banen == null ? 0 : banen.size()) + (bouwer != null && bouwer.bezig() ? ", bouwt nog" : "")
                    + ", netwerk " + (netwerkGezien ? "ja" : "nee") + ", modus " + modus.size() + ", runs " + runs.size());
            case "bouw" -> {
                if (tuin == null) sender.sendMessage("tuin nog niet getekend");
                else {
                    bouw(0);
                    sender.sendMessage("tuin wordt opnieuw nagelopen en waar nodig bijgezet");
                }
            }
            case "zelftest" -> Zelftest.draai(this, sender);
            case "kaart" -> kaart(sender);
            case "voortgang" -> {             // lobbymelding voortgang <speler> [level]: voor tests en beheer
                if (args.length < 2) {
                    sender.sendMessage("Gebruik: lobbymelding voortgang <speler> [gehaald]");
                    return true;
                }
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) {
                    sender.sendMessage("niet online: " + args[1]);
                    return true;
                }
                if (args.length >= 3) {
                    int n = Integer.parseInt(args[2]);
                    voortgang.set(p.getUniqueId().toString(), n <= 0 ? null : n);
                    voortgang.set("gespeeld." + p.getUniqueId(), n <= 0 ? null : n);
                    bewaarVoortgang();
                }
                Run r = runs.get(p.getUniqueId());
                sender.sendMessage(p.getName() + ": gehaald " + gehaald(p.getUniqueId()) + ", gespeeld " + gespeeld(p.getUniqueId())
                        + ", run " + (r == null ? "-" : "level " + (r.level + 1) + (r.klaar ? " klaar" : r.t0 == 0 ? " op start" : " bezig, stap " + r.verst)));
            }
            default -> sender.sendMessage("Gebruik: /lobbymelding terug | wie-terug | info | zelftest | kaart | bouw | voortgang <speler> [n]");
        }
        return true;
    }

    private void kaart(CommandSender sender) {
        if (wereld == null) return;
        int r = Tuin.STRAAL, g = grondY;
        Kaart.Stuk alles = new Kaart.Stuk(wereld, -r, g - 2, -r, r - 1, g + 30, r - 1);
        Kaart.Stuk midden = new Kaart.Stuk(wereld, -26, g - 2, -22, 26, g + 14, 30);
        File map = new File(getDataFolder(), "kaart");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Kaart.boven(alles, map, "boven", 4);
                Kaart.vogel(alles, map, "vogel", 0, 4);
                Kaart.vogel(alles, map, "vogel-noordwest", 2, 4);
                Kaart.vogel(midden, map, "brink", 0, 10);
                Kaart.vogel(midden, map, "brink-noord", 2, 10);
                Kaart.zij(midden, map, "brink-zij", 0, -1, 10);
                getLogger().info("kaart klaar: " + map);
            } catch (IOException e) {
                getLogger().warning("kaart: " + e.getMessage());
            }
        });
        sender.sendMessage("kaart wordt getekend naar " + map);
    }

    // ── het oude pad zonder netwerk: aftellen en terug naar survival (rustig, in de actiebalk) ─

    private void aftellen(int seconden, List<Player> spelers) {
        spelers = spelers.stream().filter(p -> !aftellend.contains(p.getUniqueId())).toList();
        if (spelers.isEmpty()) return;
        final List<Player> lijst = spelers;
        lijst.forEach(p -> {
            aftellend.add(p.getUniqueId());
            stopRun(p.getUniqueId());
        });
        for (int i = 0; i <= seconden; i++) {
            final int n = seconden - i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player p : lijst) {
                    if (!p.isOnline()) continue;
                    if (n > 0) {
                        balk(p, "Survival is er weer · terug in " + n);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 1f + 0.1f * (seconden - n));
                    } else if (blijft.contains(p.getUniqueId())) {
                        balk(p, "Survival is er weer · je blijft in de lobby");
                    } else {
                        stuurNaar(p, getConfig().getString("terug-naar", "survival"));
                    }
                }
                if (n == 0) lijst.forEach(p -> aftellend.remove(p.getUniqueId()));
            }, i * 20L);
        }
    }

    private void stuurNaar(Player p, String server) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buf);
            out.writeUTF("Connect");
            out.writeUTF(server);
            p.sendPluginMessage(this, KANAAL, buf.toByteArray());
        } catch (IOException e) {
            getLogger().warning("terugsturen mislukt: " + e.getMessage());
        }
    }

    private void naarServer(Player p, String server) {
        String naam = server.equals("kitpvp") ? "KitPvP" : Character.toUpperCase(server.charAt(0)) + server.substring(1);
        stopRun(p.getUniqueId());
        balk(p, "Naar " + naam + "…");
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.2f, 1.6f);
        stuurNaar(p, server);
    }

    private void stuurActie(Player p, String wat) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buf);
            out.writeUTF("actie");
            out.writeUTF(p.getUniqueId().toString());
            out.writeUTF(wat);
            p.sendPluginMessage(this, NETWERK, buf.toByteArray());
        } catch (IOException e) {
            getLogger().warning("actie: " + e.getMessage());
        }
    }

    /** Berichten van de Velocity-plugin netwerk (aftellen, wachten, hub) voor de hotbar, geluid en actiebalk. */
    @Override
    public void onPluginMessageReceived(String kanaal, Player via, byte[] data) {
        if (!kanaal.equals(NETWERK)) return;
        try {
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data));
            String type = in.readUTF();
            if (type.equals("chat")) return;
            UUID id = UUID.fromString(in.readUTF());
            netwerkGezien = true;
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;
            switch (type) {
                case "aftellen" -> {
                    String server = in.readUTF(), naam = in.readUTF();
                    modus.put(id, "aftellen:" + server + ":" + naam);
                    stopRun(id);
                }
                case "tik" -> {
                    int n = Integer.parseInt(in.readUTF());
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.25f, 1f + 0.15f * Math.max(0, 3 - n));
                }
                case "aftellen-klaar" -> modus.remove(id);
                case "aftellen-stop" -> {
                    modus.remove(id);
                    hintTot.put(id, System.currentTimeMillis() + 3000);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 0.8f);
                }
                case "wacht" -> {
                    String server = in.readUTF(), naam = in.readUTF();
                    modus.put(id, "wacht:" + server + ":" + naam);
                }
                case "hub" -> {         // bewust naar de lobby (niet de eerste join): naar je hoogste parkourlevel
                    if (!isBezoeker(id)) Bukkit.getScheduler().runTaskLater(this, () -> naarHoogsteLevel(p), 2L);
                    return;
                }
                case "wacht-stop" -> {
                    String m = modus.get(id);
                    if (m != null && m.startsWith("wacht:")) modus.remove(id);
                }
                default -> {
                    return;
                }
            }
            geefItems(p);
        } catch (IOException | RuntimeException e) {
            getLogger().fine("netwerk-bericht: " + e);
        }
    }

    // ── hotbar: vak 1 level kiezen, vak 5 server kiezen (of annuleren/blijven), vak 9 opnieuw ──

    private static final int KIEZER_SLOT = 0, ITEM_SLOT = 4, RESET_SLOT = 8;

    private ItemStack item(Material m, String naam, TextColor kleur, String regel, NamespacedKey key, byte waarde) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(Component.text(naam, kleur).decoration(TextDecoration.ITALIC, false));
        im.lore(List.of(Component.text(regel, GRIJS).decoration(TextDecoration.ITALIC, false)));
        im.getPersistentDataContainer().set(key, PersistentDataType.BYTE, waarde);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack kiezerItem() {
        return item(Material.COMPASS, "Parkour", NamedTextColor.WHITE, "klik: kies een level", kiezerKey, (byte) 1);
    }

    private ItemStack resetItem() {
        return item(Material.RED_DYE, "Opnieuw", NamedTextColor.WHITE, "klik: terug naar de start van je level", resetKey, (byte) 1);
    }

    /** Vak 5: ster (server kiezen), tijdens het aftellen een barrière (annuleren), tijdens wachten rode wol (blijven). */
    private ItemStack serverItem(String m) {
        boolean aftel = m != null && m.startsWith("aftellen:"), wacht = m != null && m.startsWith("wacht:");
        String naar = m == null ? "" : m.split(":", 3)[2];
        if (aftel) return item(Material.BARRIER, "Annuleren", NamedTextColor.WHITE, "klik: in de lobby blijven (ook /annuleer)", itemKey, (byte) 2);
        if (wacht) return item(Material.RED_WOOL, "Blijven", NamedTextColor.WHITE, "klik: niet vanzelf terug naar " + naar, itemKey, (byte) 3);
        return item(Material.NETHER_STAR, "Server kiezen", NamedTextColor.WHITE, "klik: Survival, KitPvP of parkour", itemKey, (byte) 1);
    }

    private boolean heeft(ItemStack it, NamespacedKey key) {
        return key != null && it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(key);
    }

    private boolean eigenItem(ItemStack it) {
        return heeft(it, itemKey) || heeft(it, kiezerKey) || heeft(it, resetKey);
    }

    private void geefItems(Player p) {
        if (itemKey == null) return;
        var inv = p.getInventory();
        ItemStack want = serverItem(modus.get(p.getUniqueId()));
        ItemStack nu = inv.getItem(ITEM_SLOT);
        if (!heeft(nu, itemKey) || nu.getType() != want.getType()) zetVak(p, ITEM_SLOT, want, itemKey);
        if (!heeft(inv.getItem(KIEZER_SLOT), kiezerKey)) zetVak(p, KIEZER_SLOT, kiezerItem(), kiezerKey);
        if (!heeft(inv.getItem(RESET_SLOT), resetKey)) zetVak(p, RESET_SLOT, resetItem(), resetKey);
    }

    private void zetVak(Player p, int vak, ItemStack it, NamespacedKey key) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (i != vak && heeft(inv.getItem(i), key)) inv.setItem(i, null);
        inv.setItem(vak, it);
    }

    private final Map<UUID, Long> laatsteKlik = new HashMap<>();

    /** Eén klik = één actie (links of rechts, lucht of blok); dubbele events binnen 400 ms tellen niet. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKlikItem(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        ItemStack it = e.getHand() == EquipmentSlot.OFF_HAND ? null : e.getItem();
        if (!eigenItem(it)) {
            if (e.getHand() == EquipmentSlot.OFF_HAND && eigenItem(e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        Player p = e.getPlayer();
        long nu = System.currentTimeMillis();
        if (nu - laatsteKlik.getOrDefault(p.getUniqueId(), 0L) < 400) return;
        laatsteKlik.put(p.getUniqueId(), nu);
        if (heeft(it, kiezerKey)) {
            openKiezer(p);
            return;
        }
        if (heeft(it, resetKey)) {
            opnieuw(p);
            return;
        }
        if (modus.get(p.getUniqueId()) != null) {
            stuurActie(p, "annuleer");
            modus.remove(p.getUniqueId());
            geefItems(p);
            p.updateInventory();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
        } else {
            openServerMenu(p);
        }
    }

    /** Naar de start van je level: de lopende run, anders het laatst gekozen level, anders waar je aan toe bent. */
    private void opnieuw(Player p) {
        if (banen == null) return;
        UUID id = p.getUniqueId();
        Run r = runs.get(id);
        Integer l = r != null ? Integer.valueOf(r.level) : huidigLevel.get(id);
        if (l == null) l = Regels.volgend(gehaald(id), banen.size());
        naarStart(p, l, false);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.4f);
    }

    // ── menu's: levels en servers ───────────────────────────────────────────────────────────

    private static final class Menu implements org.bukkit.inventory.InventoryHolder {
        final String soort;
        org.bukkit.inventory.Inventory inv;

        Menu(String soort) {
            this.soort = soort;
        }

        public org.bukkit.inventory.Inventory getInventory() {
            return inv;
        }
    }

    private static ItemStack leeg(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setHideTooltip(true);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack knop(Material m, String naam, TextColor kleur, String... regels) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(Component.text(naam, kleur).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String r : regels) lore.add(Component.text(r, GRIJS).decoration(TextDecoration.ITALIC, false));
        im.lore(lore);
        im.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(im);
        return it;
    }

    /** Level-slot in het menu: 1-5 bovenaan, 6-10 onderaan, steeds op 0,2,4,6,8. */
    static int kiezerSlot(int l) {
        return (l < 5 ? 0 : 18) + (l % 5) * 2;
    }

    private void openKiezer(Player p) {
        if (banen == null) return;
        Menu k = new Menu("levels");
        k.inv = Bukkit.createInventory(k, 27, Component.text("Parkour"));
        for (int i = 0; i < 27; i++) k.inv.setItem(i, leeg(Material.GREEN_STAINED_GLASS_PANE));
        int gehaald = gehaald(p.getUniqueId());
        for (int l = 0; l < banen.size(); l++) {
            Baan b = banen.get(l);
            boolean klaar = l < gehaald, open = Regels.open(l, gehaald);
            long best = besteTijd(p.getUniqueId(), l);
            ItemStack it = knop(klaar ? Material.LIME_CONCRETE : open ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE,
                    "Level " + b.nr + " · " + b.naam, open ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY,
                    b.sterren + (best > 0 ? " · beste " + Regels.tijd(best) : ""),
                    open ? "klik: naar de start" : "haal eerst level " + gehaald);
            ItemMeta im = it.getItemMeta();
            im.setEnchantmentGlintOverride(klaar);
            it.setItemMeta(im);
            it.setAmount(l + 1);
            k.inv.setItem(kiezerSlot(l), it);
        }
        p.openInventory(k.inv);
    }

    private void openServerMenu(Player p) {
        Menu m = new Menu("servers");
        m.inv = Bukkit.createInventory(m, 27, Component.text("Waar wil je heen?"));
        for (int i = 0; i < 27; i++) m.inv.setItem(i, leeg(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        m.inv.setItem(10, knop(Material.GRASS_BLOCK, "Survival", NamedTextColor.WHITE, "de survivalwereld"));
        m.inv.setItem(12, knop(Material.IRON_SWORD, "KitPvP", NamedTextColor.WHITE, "kies een kit en vecht"));
        m.inv.setItem(14, knop(Material.FEATHER, "Parkour", NamedTextColor.WHITE, "tien levels in de tuin"));
        m.inv.setItem(16, knop(Material.OAK_SAPLING, "De brink", NamedTextColor.WHITE, "terug naar de spawn (/spawn)"));
        p.openInventory(m.inv);
    }

    @EventHandler
    public void onInventoryKlik(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu m) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            int slot = e.getRawSlot();
            if (m.soort.equals("levels")) {
                if (banen == null) return;
                for (int l = 0; l < banen.size(); l++) {
                    if (kiezerSlot(l) != slot) continue;
                    if (!Regels.open(l, gehaald(p.getUniqueId()))) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                        return;
                    }
                    p.closeInventory();
                    naarStart(p, l, false);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.3f);
                    return;
                }
            } else {
                switch (slot) {
                    case 10 -> {
                        p.closeInventory();
                        naarServer(p, "survival");
                    }
                    case 12 -> {
                        p.closeInventory();
                        naarServer(p, "kitpvp");
                    }
                    case 14 -> {
                        p.closeInventory();
                        Bukkit.getScheduler().runTask(this, () -> openKiezer(p));
                    }
                    case 16 -> {
                        p.closeInventory();
                        stopRun(p.getUniqueId());
                        if (spawn != null) p.teleport(spawn);
                    }
                    default -> {
                    }
                }
            }
            return;
        }
        boolean eigenInv = e.getClickedInventory() != null && e.getClickedInventory().getType() == org.bukkit.event.inventory.InventoryType.PLAYER;
        if (eigenItem(e.getCurrentItem()) || eigenItem(e.getCursor())
                || e.getHotbarButton() == ITEM_SLOT || e.getHotbarButton() == KIEZER_SLOT || e.getHotbarButton() == RESET_SLOT
                || (eigenInv && (e.getSlot() == ITEM_SLOT || e.getSlot() == KIEZER_SLOT || e.getSlot() == RESET_SLOT))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSleep(InventoryDragEvent e) {
        if (eigenItem(e.getOldCursor()) || e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (eigenItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onWissel(PlayerSwapHandItemsEvent e) {
        if (eigenItem(e.getMainHandItem()) || eigenItem(e.getOffHandItem())) e.setCancelled(true);
    }

    // ── binnenkomen en weggaan ──────────────────────────────────────────────────────────────

    private File blijfBestand() {
        return new File(getDataFolder(), "blijvers.txt");
    }

    private void bewaarBlijvers() {
        try {
            Files.writeString(blijfBestand().toPath(), String.join("\n", blijft.stream().map(UUID::toString).toList()) + "\n");
        } catch (IOException e) {
            getLogger().warning("blijvers.txt: " + e.getMessage());
        }
    }

    private void leesBlijvers() {
        try {
            if (!blijfBestand().exists()) return;
            for (String r : Files.readAllLines(blijfBestand().toPath())) {
                try {
                    if (!r.isBlank()) blijft.add(UUID.fromString(r.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException e) {
            getLogger().warning("blijvers.txt: " + e.getMessage());
        }
        Set<UUID> uitBestand = new HashSet<>(blijft);       // wie niet binnen 3 minuten terug is: vergeten
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (blijft.removeIf(id -> uitBestand.contains(id) && Bukkit.getPlayer(id) == null)) bewaarBlijvers();
        }, 3 * 60 * 20L);
    }

    /** Kwam deze speler net via /parkour uit survival? (bezoekers.txt, geschreven door de plugin lobbyreis) */
    private boolean isBezoeker(UUID id) {
        File f = new File(getDataFolder(), "bezoekers.txt");
        if (!f.exists()) return false;
        boolean gevonden = false;
        StringBuilder rest = new StringBuilder();
        long nu = System.currentTimeMillis();
        try {
            for (String regel : Files.readAllLines(f.toPath())) {
                String[] d = regel.trim().split(" ");
                if (d.length != 2) continue;
                long t;
                try {
                    t = Long.parseLong(d[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (nu - t > 120_000L) continue;
                if (d[0].equals(id.toString())) gevonden = true;
                else rest.append(regel.trim()).append('\n');
            }
            Files.writeString(f.toPath(), rest.toString());
        } catch (IOException e) {
            getLogger().warning("bezoekers.txt: " + e.getMessage());
        }
        return gevonden;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player speler = event.getPlayer();
        maakVeilig(speler);
        speler.getInventory().clear();
        if (spawn != null) speler.teleport(spawn);
        geefItems(speler);
        if (isBezoeker(speler.getUniqueId()) && banen != null) naarVolgendLevel(speler);   // via /parkour uit survival
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        runs.remove(id);
        if (!Bukkit.isStopping() && blijft.remove(id)) bewaarBlijvers();
        modus.remove(id);
        poortTot.remove(id);
        hintTot.remove(id);
        aftellend.remove(id);
        resetTot.remove(id);
        laatsteKlik.remove(id);
    }

    // ── voor Zelftest ───────────────────────────────────────────────────────────────────────

    Tuin tuin() {
        return tuin;
    }

    World wereld() {
        return wereld;
    }

    int grondY() {
        return grondY;
    }

    boolean klaar() {
        return banen != null;
    }

    Location spawnPlek() {
        return spawn;
    }

    int aantalLabels() {
        int n = 0;
        if (wereld != null) for (Entity e : wereld.getEntities()) if (e.getScoreboardTags().contains("lobbymelding")) n++;
        return n;
    }
}
