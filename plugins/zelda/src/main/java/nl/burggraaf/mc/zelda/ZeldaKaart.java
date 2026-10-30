package nl.burggraaf.mc.zelda;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;

/**
 * De kaart in vak 9, zoals in Legend of Zelda (zie ZELDA.md, «De kaart»). Vasthouden = het eiland van boven: waar je staat
 * (een pijl), de zielvuren waar je was, dorpen en kerkers, medespelers (groen) en je volgende doel (een rode X, dezelfde bron
 * als {@code /hint}); land dat je nog niet zag ligt onder perkament. Linksklik = omslaan naar de zeekaart (alle eilanden en de
 * vaarroutes); op zee zie je daar je schip varen. Rechtsklik = het kaartmenu van {@link ZeldaSpel#kaart} (reizen, varen, …).
 *
 * <p>Licht voor de server: elk eiland wordt één keer van boven gemeten (verdeeld over tikken, chunks laden op de achtergrond)
 * en bewaard in {@code plugins/KitPvP/zelda-kaart/} tot een deel een nieuwe versie heeft; per speler wordt het blad alleen
 * opnieuw getekend als er iets veranderde (hoogstens eens per seconde), de pijlen vijf keer per seconde. Elke speler heeft
 * een eigen kaart ({@link MapView}, het nummer staat in {@code zelda-kaarten.yml}) en een eigen raster van wat hij ontdekte
 * ({@code ZeldaVoortgang.Stand.kaart}).
 */
final class ZeldaKaart {

    /** Hoger = elk eiland opnieuw meten (als de manier van tekenen verandert). */
    static final int TEKENWIJZE = 3;
    /** Een andere kant om naar te kijken: het eiland of de zee. */
    static final String EILAND = "eiland", ZEE = "zee";

    private final ZeldaPlugin plugin;
    private final ZeldaSpel spel;
    /** Per eiland het blad van boven (zonder mist); ontbreekt zolang het nog gemeten wordt. */
    final Map<String, ZeldaKaartBeeld.Basis> bladen = new HashMap<>();
    private final Map<String, String> sleutels = new HashMap<>();
    private final Map<UUID, MapView> views = new HashMap<>();
    private final Map<UUID, Kijk> kijk = new HashMap<>();
    private final File map, nummers;
    private YamlConfiguration nummer;
    private Meting meting;
    private BukkitTask taak;
    private int versie;                                        // telt op als er een blad bij komt: dan tekent iedereen opnieuw
    private boolean gemeten;                                   // alle eilanden zijn gemeten: niets meer na te kijken

    /** Wat er nu op de kaart van één speler staat. */
    static final class Kijk {
        boolean zee;                                           // omgeslagen naar de zeekaart (linksklik)
        String sleutel;                                        // waarvan het blad nu getekend is
        byte[] px;                                             // het blad (nog niet per se op het doek)
        boolean nieuw;                                         // het blad moet nog naar het doek
        List<ZeldaKaartBeeld.Merk> merken = List.of();
        long tekend, merkTijd, omslag, doelTijd;
        Location doel;                                         // waar /hint je heen stuurt (null = nergens)
        ZeldaEiland doelEiland;                                // … of naar welk eiland je moet varen
        ZeldaSpel.Vaart vaart;                                 // de overtocht waar je pijl op vaart
        long vaartBegin;
    }

    /** Wat er op een blad staat: de pixels en de merken (pijlen, de X, dorpen). */
    record Blad(String kant, String sleutel, byte[] px, List<ZeldaKaartBeeld.Merk> merken, ZeldaKaartBeeld.Vlak vlak) {}

    ZeldaKaart(ZeldaPlugin plugin, ZeldaSpel spel) {
        this.plugin = plugin;
        this.spel = spel;
        this.map = new File(plugin.getDataFolder(), "zelda-kaart");
        this.nummers = new File(plugin.getDataFolder(), "zelda-kaarten.yml");
    }

    void aan() {
        nummer = YamlConfiguration.loadConfiguration(nummers);
        for (ZeldaEiland e : spel.eilanden) laad(e);
        taak = Bukkit.getScheduler().runTaskTimer(plugin, this::tik, 40L, 1L);
    }

    void uit() {
        if (taak != null) taak.cancel();
        taak = null;
        meting = null;
        bewaar();
        for (MapView v : views.values()) for (MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
        views.clear();
        kijk.clear();
    }

    /** Een speler gaat weg uit Zelda: zijn kaart vergeet wat erop stond (bij terugkomst opnieuw getekend). */
    void weg(UUID id) {
        kijk.remove(id);
        bewaar();
    }

    // ── de kaart in je hand ─────────────────────────────────────────────────────────────────

    /** De kaart voor vak 9: een gevulde kaart met je eigen {@link MapView}. */
    ItemStack item(Player p) {
        ItemStack it = spel.ding(Material.FILLED_MAP, "Kaart", "kaart");
        MapView v = view(p);
        if (v != null && it.getItemMeta() instanceof MapMeta mm) {
            mm.setMapView(v);
            mm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            it.setItemMeta(mm);
        }
        ZeldaSpel.lore(it, "linksklik: omslaan", "rechtsklik: reizen en meer");
        return it;
    }

    /** Is dit de kaart van Zelda (met de tekenaar erop)? */
    boolean isKaart(ItemStack it) {
        return it != null && it.getType() == Material.FILLED_MAP && it.getItemMeta() instanceof MapMeta mm && mm.hasMapView()
                && mm.getMapView() != null && mm.getMapView().getRenderers().stream().anyMatch(r -> r instanceof Tekenaar);
    }

    /** De eigen kaart van deze speler (het nummer blijft bewaard, ook na /zelda reset). */
    private MapView view(Player p) {
        MapView v = views.get(p.getUniqueId());
        if (v != null) return v;
        World w = p.getWorld();
        int id = nummer.getInt("kaarten." + p.getUniqueId(), -1);
        if (id >= 0) v = Bukkit.getMap(id);
        if (v == null) {
            v = Bukkit.createMap(w);
            nummer.set("kaarten." + p.getUniqueId(), v.getId());
            try {
                nummer.save(nummers);
            } catch (IOException e) {
                plugin.getLogger().warning("zelda-kaarten.yml: " + e.getMessage());
            }
        }
        for (MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
        v.setLocked(true);                                     // geen gewone kaart: Minecraft tekent er zelf niets op
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.addRenderer(new Tekenaar());
        views.put(p.getUniqueId(), v);
        return v;
    }

    /** Linksklik met de kaart: omslaan (het eiland of de zee). Op zee blijft het de zee. */
    void omslaan(Player p) {
        Kijk k = kijk(p.getUniqueId());
        long nu = System.currentTimeMillis();
        if (nu - k.omslag < 250) return;                       // één klik, niet twee (zwaaien en klikken komen allebei binnen)
        k.omslag = nu;
        k.zee = !k.zee;
        k.merkTijd = 0;
        p.playSound(p, org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.1f);
    }

    /** Je pakt de kaart: de eerste keer zegt Wiske hoe hij werkt (uitleg, over te slaan). */
    void vastgepakt(Player p, ZeldaVoortgang.Stand s) {
        if (!s.zet("v:kaart")) return;
        spel.voortgang.vuil();
        spel.uitleg(p, ZeldaHoofdstuk.r("Wiske", "Je kaart! De pijl ben jij, de X is je doel."), ZeldaHoofdstuk.r("Wiske", "Linksklik: de hele zee."),
                ZeldaHoofdstuk.r("Wiske", "Rechtsklik: het menu, ook je niveau."));
    }

    Kijk kijk(UUID id) {
        return kijk.computeIfAbsent(id, k -> new Kijk());
    }

    /** Tekent de kaart van wie hem vasthoudt (Minecraft vraagt het elke tik; meestal is er niets nieuws). */
    final class Tekenaar extends MapRenderer {
        Tekenaar() {
            super(false);
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player p) {
            try {
                ZeldaKaart.this.render(p, canvas);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("zelda-kaart: " + e);
            }
        }
    }

    private void render(Player p, MapCanvas canvas) {
        ZeldaSpel.Sessie ss = spel.sessie(p);
        if (ss == null || p.getInventory().getHeldItemSlot() != 8) return;   // alleen als je hem vasthoudt
        Kijk k = kijk(p.getUniqueId());
        long nu = System.currentTimeMillis();
        if (nu - k.merkTijd < 200) return;                     // de pijlen vijf keer per seconde
        k.merkTijd = nu;
        Blad b = blad(p, k.zee ? ZEE : EILAND, nu);
        if (k.nieuw && k.px != null) {
            k.nieuw = false;
            for (int j = 0; j < ZeldaKaartBeeld.N; j++)
                for (int i = 0; i < ZeldaKaartBeeld.N; i++) canvas.setPixel(i, j, k.px[j * ZeldaKaartBeeld.N + i]);
        }
        MapCursorCollection c = new MapCursorCollection();
        for (ZeldaKaartBeeld.Merk m : b.merken()) {
            MapCursor.Type t = type(m.soort());
            if (t != null) c.addCursor(new MapCursor(ZeldaKaartBeeld.cursor(m.i()), ZeldaKaartBeeld.cursor(m.j()), (byte) m.richting(), t, true));
        }
        canvas.setCursors(c);
    }

    private static MapCursor.Type type(String soort) {
        return switch (soort) {
            case "jij" -> MapCursor.Type.PLAYER;
            case "jij-rand" -> MapCursor.Type.PLAYER_OFF_MAP;
            case "maat" -> MapCursor.Type.FRAME;
            case "doel" -> MapCursor.Type.RED_X;
            case "dorp" -> MapCursor.Type.VILLAGE_PLAINS;
            case "dorp-zand" -> MapCursor.Type.VILLAGE_DESERT;
            case "dorp-sneeuw" -> MapCursor.Type.VILLAGE_SNOWY;
            case "dorp-as" -> MapCursor.Type.VILLAGE_SAVANNA;
            case "dorp-hout" -> MapCursor.Type.VILLAGE_TAIGA;
            case "kerker" -> MapCursor.Type.JUNGLE_TEMPLE;
            case "toren" -> MapCursor.Type.MANSION;
            default -> null;
        };
    }

    // ── wat er op het blad staat ────────────────────────────────────────────────────────────

    /** Het blad van deze speler (de kant {@link #EILAND} of {@link #ZEE}); opnieuw getekend als er iets veranderde. */
    Blad blad(Player p, String kant, long nu) {
        ZeldaSpel.Sessie ss = spel.sessie(p);
        Kijk k = kijk(p.getUniqueId());
        ZeldaVoortgang.Stand s = spel.voortgang.van(p.getUniqueId());
        boolean alles = spel.beheer.is(p.getUniqueId());
        ZeldaEiland e = ss == null ? null : ss.h.eiland;
        if (ss != null && ss.vaart != k.vaart) {                // een nieuwe overtocht: het schip vertrekt nu; aangekomen: het eiland
            if (ss.vaart == null) k.zee = false;
            k.vaart = ss.vaart;
            k.vaartBegin = nu;
        }
        if (nu - k.doelTijd > 1000) {                          // het doel van /hint, eens per seconde
            k.doelTijd = nu;
            ZeldaSpel.Wijzer w = ss == null || e == null || ss.vaart != null ? null : spel.wijzer(e, s, p.getLocation().getX(), p.getLocation().getZ());
            k.doel = w == null ? null : w.plek();
            k.doelEiland = w == null ? null : w.eiland();
        }
        ZeldaKaartBeeld.Basis basis = e == null ? null : bladen.get(e.id());
        if (basis == null || ss != null && ss.vaart != null) kant = ZEE;   // op zee (of nog niet gemeten): de zeekaart
        String sleutel = kant + "|" + (e == null ? "" : e.id()) + "|" + versie + "|" + s.kaart.cardinality() + "|" + s.bezocht.size() + "|"
                + s.vlaggen.size() + "|" + (ss == null ? "" : ss.rh.id() + ":" + ss.rust) + "|" + alles + "|" + (ss != null && ss.vaart != null);
        boolean anders = !sleutel.equals(k.sleutel);
        boolean kantAnders = k.sleutel == null || !k.sleutel.startsWith(kant + "|");
        if (anders && (kantAnders || nu - k.tekend >= 1000)) {
            k.sleutel = sleutel;
            k.tekend = nu;
            k.px = EILAND.equals(kant) ? eilandPx(basis, e, s, ss, alles) : zeePx(e, s, ss, alles);
            k.nieuw = true;
        }
        ZeldaKaartBeeld.Vlak vlak = EILAND.equals(kant) ? basis.vlak() : ZeldaKaartBeeld.Vlak.zee();
        List<ZeldaKaartBeeld.Merk> merken = EILAND.equals(kant) ? eilandMerken(p, vlak, e, s, ss, k, alles) : zeeMerken(p, vlak, e, s, ss, k, alles, nu);
        k.merken = merken;
        return new Blad(kant, k.sleutel, k.px, merken, vlak);
    }

    private byte[] eilandPx(ZeldaKaartBeeld.Basis basis, ZeldaEiland e, ZeldaVoortgang.Stand s, ZeldaSpel.Sessie ss, boolean alles) {
        byte[] px = ZeldaKaartBeeld.blad(basis, s.kaart, alles);
        ZeldaKaartBeeld.Vlak v = basis.vlak();
        int[] hier = null;
        for (ZeldaHoofdstuk h : e.delen())                     // de zielvuren waar je was (waar je terugkomt: blauw, bovenop)
            for (ZeldaHoofdstuk.Rust r : h.rust) {
                if (!alles && !s.bezocht(h.id(), r.index)) continue;
                int i = (int) Math.floor(v.px(r.plek.getX())), j = (int) Math.floor(v.pz(r.plek.getZ()));
                if (ss != null && h == ss.rh && r.index == ss.rust) hier = new int[]{i, j};
                else ZeldaKaartBeeld.vlam(px, i, j, false);
            }
        if (hier != null) ZeldaKaartBeeld.vlam(px, hier[0], hier[1], true);
        ZeldaKaartBeeld.titel(px, e.naam());                    // de naam van het eiland, klein bovenaan
        return px;
    }

    private List<ZeldaKaartBeeld.Merk> eilandMerken(Player p, ZeldaKaartBeeld.Vlak v, ZeldaEiland e, ZeldaVoortgang.Stand s, ZeldaSpel.Sessie ss, Kijk k, boolean alles) {
        List<ZeldaKaartBeeld.Merk> m = new ArrayList<>();
        for (ZeldaHoofdstuk h : e.delen())                     // dorpen en kerkers die je al zag
            for (ZeldaHoofdstuk.Teken t : h.tekens()) {
                int c = ZeldaKaartBeeld.cel(t.x(), t.z());
                if (alles || c >= 0 && s.kaart.get(c)) m.add(new ZeldaKaartBeeld.Merk(t.soort(), v.px(t.x() + 0.5), v.pz(t.z() + 0.5), 8));
            }
        if (k.doel != null && v.op(k.doel.getX(), k.doel.getZ()) && e.deelBij(k.doel) != null)
            m.add(new ZeldaKaartBeeld.Merk("doel", v.px(k.doel.getX()), v.pz(k.doel.getZ()), 8));
        for (Player o : Bukkit.getOnlinePlayers()) {           // medespelers op dit eiland
            ZeldaSpel.Sessie os = spel.sessie(o);
            if (o == p || os == null || os.vaart != null || os.h.eiland != e || spel.beheer.is(o.getUniqueId())) continue;
            Location l = o.getLocation();
            if (v.op(l.getX(), l.getZ())) m.add(new ZeldaKaartBeeld.Merk("maat", v.px(l.getX()), v.pz(l.getZ()), ZeldaKaartBeeld.richting(l.getYaw())));
        }
        Location l = p.getLocation();
        double i = v.px(l.getX()), j = v.pz(l.getZ());
        boolean op = v.op(l.getX(), l.getZ());
        m.add(new ZeldaKaartBeeld.Merk(op ? "jij" : "jij-rand", Math.max(0.5, Math.min(127.5, i)), Math.max(0.5, Math.min(127.5, j)),
                op ? ZeldaKaartBeeld.richting(l.getYaw()) : 8));
        return m;
    }

    /** Hoe dit eiland op de zeekaart van deze speler staat. */
    private ZeldaKaartBeeld.Zicht zicht(ZeldaEiland o, ZeldaVoortgang.Stand s, boolean alles) {
        if (alles || s.heeft("e:" + o.id())) return ZeldaKaartBeeld.Zicht.KLEUR;
        for (ZeldaHoofdstuk h : o.delen()) for (ZeldaHoofdstuk.Rust r : h.rust) if (s.bezocht(h.id(), r.index)) return ZeldaKaartBeeld.Zicht.KLEUR;
        if (o.open(s) && o.aanlegRust() != null) return ZeldaKaartBeeld.Zicht.VORM;
        if (o == spel.eilanden.get(spel.eilanden.size() - 1)) return ZeldaKaartBeeld.Zicht.NEVEL;   // Nachtrots in Morgals nevel
        return ZeldaKaartBeeld.Zicht.WEG;
    }

    private byte[] zeePx(ZeldaEiland e, ZeldaVoortgang.Stand s, ZeldaSpel.Sessie ss, boolean alles) {
        List<ZeldaKaartBeeld.Eilandje> l = new ArrayList<>();
        for (ZeldaEiland o : spel.eilanden) {
            ZeldaKaartBeeld.Basis b = bladen.get(o.id());
            if (b == null) continue;
            l.add(new ZeldaKaartBeeld.Eilandje(b, vakken(o), zicht(o, s, alles), alles ? null : s.kaart));
        }
        boolean[] zee = new boolean[ZeldaKaartBeeld.N * ZeldaKaartBeeld.N];
        byte[] px = ZeldaKaartBeeld.zeekaart(l, zee);
        ZeldaKaartBeeld.Vlak v = ZeldaKaartBeeld.Vlak.zee();
        Location van = e == null ? null : e.aanlegPlek();
        if (ss != null && ss.vaart != null && van != null && ss.vaart.naar().aanlegPlek() != null) {   // op zee: alleen de route van nu
            Location naar = ss.vaart.naar().aanlegPlek();
            ZeldaKaartBeeld.stippel(px, v.px(van.getX()), v.pz(van.getZ()), v.px(naar.getX()), v.pz(naar.getZ()), ZeldaKaartBeeld.STIP, zee);
        } else if (van != null)                                // de vaarroutes: van hier naar elk eiland waar je heen kunt
            for (ZeldaEiland o : spel.eilanden) {
                if (o == e || o.aanlegPlek() == null || !(alles || o.open(s))) continue;
                Location naar = o.aanlegPlek();
                ZeldaKaartBeeld.stippel(px, v.px(van.getX()), v.pz(van.getZ()), v.px(naar.getX()), v.pz(naar.getZ()), ZeldaKaartBeeld.STIP, zee);
            }
        return px;
    }

    private List<ZeldaKaartBeeld.Merk> zeeMerken(Player p, ZeldaKaartBeeld.Vlak v, ZeldaEiland e, ZeldaVoortgang.Stand s, ZeldaSpel.Sessie ss, Kijk k,
                                                 boolean alles, long nu) {
        List<ZeldaKaartBeeld.Merk> m = new ArrayList<>();
        Location doel = k.doel != null ? k.doel : k.doelEiland != null ? k.doelEiland.aanlegPlek() : null;
        if (doel != null && v.op(doel.getX(), doel.getZ())) m.add(new ZeldaKaartBeeld.Merk("doel", v.px(doel.getX()), v.pz(doel.getZ()), 8));
        for (Player o : Bukkit.getOnlinePlayers()) {                // medespelers op een eiland dat je kent (niet in lege zee)
            ZeldaSpel.Sessie os = spel.sessie(o);
            if (o == p || os == null || os.vaart != null || spel.beheer.is(o.getUniqueId()) || os.h.eiland == null) continue;
            ZeldaKaartBeeld.Zicht z = zicht(os.h.eiland, s, alles);
            if (z != ZeldaKaartBeeld.Zicht.KLEUR && z != ZeldaKaartBeeld.Zicht.VORM) continue;
            Location l = o.getLocation();
            if (v.op(l.getX(), l.getZ())) m.add(new ZeldaKaartBeeld.Merk("maat", v.px(l.getX()), v.pz(l.getZ()), ZeldaKaartBeeld.richting(l.getYaw())));
        }
        double[] waar = schip(e, ss, k, nu);
        if (waar != null) m.add(new ZeldaKaartBeeld.Merk("jij", v.px(waar[0]), v.pz(waar[1]), ZeldaKaartBeeld.richting(waar[2])));
        else {
            Location l = p.getLocation();
            if (v.op(l.getX(), l.getZ())) m.add(new ZeldaKaartBeeld.Merk("jij", v.px(l.getX()), v.pz(l.getZ()), ZeldaKaartBeeld.richting(l.getYaw())));
        }
        return m;
    }

    /** Op zee: waar je schip nu is ({x, z, yaw}), van de aanleg van je eiland naar die van het volgende; null = niet op zee. */
    double[] schip(ZeldaEiland e, ZeldaSpel.Sessie ss, Kijk k, long nu) {
        if (ss == null || ss.vaart == null || e == null || e.aanlegPlek() == null || ss.vaart.naar().aanlegPlek() == null) return null;
        Location a = e.aanlegPlek(), b = ss.vaart.naar().aanlegPlek();
        double duur = Math.max(1, ss.vaart.tot() - k.vaartBegin), t = Math.max(0, Math.min(1, (nu - k.vaartBegin) / duur));
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        return new double[]{a.getX() + dx * t, a.getZ() + dz * t, Math.toDegrees(Math.atan2(-dx, dz))};
    }

    /** De stukken wereld van een eiland ({x1, z1, x2, z2}: de speelgebieden van zijn delen). */
    static List<int[]> vakken(ZeldaEiland e) {
        List<int[]> l = new ArrayList<>();
        for (ZeldaHoofdstuk h : e.delen())
            if (h.speelgebied != null) l.add(new int[]{h.speelgebied.x1(), h.speelgebied.z1(), h.speelgebied.x2(), h.speelgebied.z2()});
        return l;
    }

    // ── ontdekken ───────────────────────────────────────────────────────────────────────────

    /**
     * Elke seconde: wat om je heen ligt, heb je gezien. Naar zelda.yml gaat het hoogstens eens per minuut (of als je weggaat),
     * niet bij elk vakje: ontdekken is geen voortgang die je kwijt kunt raken.
     */
    void ontdek(Player p, ZeldaVoortgang.Stand s) {
        Location l = p.getLocation();
        if (ZeldaKaartBeeld.ontdek(s.kaart, l.getX(), l.getZ(), ZeldaKaartBeeld.ZICHT)) nieuw = true;
    }

    private boolean nieuw;                                     // er is iets ontdekt dat nog niet in zelda.yml staat

    /** Wat er ontdekt is, gaat mee met de volgende keer bewaren. */
    void bewaar() {
        if (!nieuw) return;
        nieuw = false;
        spel.voortgang.vuil();
    }

    /**
     * Bij binnenkomen: wat je zeker al zag, ook van een stand van vóór de kaart (kitpvp 2.13): om elke rustplaats waar je was,
     * elk verhaal dat je hoorde, elke vondst en elke gewonnen kamer; een eiland dat je af hebt helemaal. True als er iets bij kwam.
     */
    boolean aanvullen(ZeldaVoortgang.Stand s) {
        BitSet b = s.kaart;
        boolean nieuw = false;
        for (ZeldaEiland e : spel.eilanden) {
            if (s.heeft(e.klaar())) {
                for (int[] v : vakken(e)) nieuw |= ZeldaKaartBeeld.ontdek(b, v[0], v[1], v[2], v[3]);
                continue;
            }
            for (ZeldaHoofdstuk h : e.delen()) {
                if (h.w == null) continue;
                for (ZeldaHoofdstuk.Rust r : h.rust) if (s.bezocht(h.id(), r.index)) nieuw |= ZeldaKaartBeeld.ontdek(b, r.plek.getX(), r.plek.getZ(), 32);
                for (ZeldaHoofdstuk.Verhaal v : h.verhalen)
                    if (s.heeft("v:" + v.id)) nieuw |= ZeldaKaartBeeld.ontdek(b, (v.zone.x1() + v.zone.x2()) / 2.0, (v.zone.z1() + v.zone.z2()) / 2.0, ZeldaKaartBeeld.ZICHT);
                for (ZeldaHoofdstuk.Vondst v : h.vondsten) if (s.heeft(v.vlag)) nieuw |= ZeldaKaartBeeld.ontdek(b, v.plek.getX(), v.plek.getZ(), 16);
                for (ZeldaHoofdstuk.Kamer k : h.kamers)
                    if (s.heeft("k:" + k.id)) nieuw |= ZeldaKaartBeeld.ontdek(b, k.binnen.x1(), k.binnen.z1(), k.binnen.x2(), k.binnen.z2());
            }
        }
        return nieuw;
    }

    // ── een eiland meten (één keer per versie) ──────────────────────────────────────────────

    /** Wat er voor dit eiland op schijf moet staan: de versies van zijn delen en het stuk wereld. */
    String sleutel(ZeldaEiland e) {
        StringBuilder sb = new StringBuilder("zelda-kaart " + TEKENWIJZE);
        for (ZeldaHoofdstuk h : e.delen()) sb.append(' ').append(h.deelId()).append(':').append(h.versie());
        ZeldaKaartBeeld.Vlak v = vlak(e);
        if (v != null) sb.append(String.format(java.util.Locale.ROOT, " %.2f,%.2f,%.4f", v.x0(), v.z0(), v.schaal()));
        return sb.toString();
    }

    /** Welk stuk wereld op het blad van een eiland komt: om alle delen heen (null = nog niet geladen). */
    static ZeldaKaartBeeld.Vlak vlak(ZeldaEiland e) {
        List<int[]> vk = vakken(e);
        if (vk.isEmpty()) return null;
        int x1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
        for (int[] v : vk) {
            x1 = Math.min(x1, v[0]);
            z1 = Math.min(z1, v[1]);
            x2 = Math.max(x2, v[2]);
            z2 = Math.max(z2, v[3]);
        }
        return ZeldaKaartBeeld.Vlak.om(x1, z1, x2, z2);
    }

    private File bestand(ZeldaEiland e) {
        return new File(map, e.id() + ".dat");
    }

    private void laad(ZeldaEiland e) {
        File f = bestand(e);
        if (!f.exists()) return;
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            String sl = in.readUTF();
            if (!sl.equals(sleutel(e))) return;                    // een deel is veranderd: opnieuw meten
            byte[] kleur = new byte[ZeldaKaartBeeld.N * ZeldaKaartBeeld.N];
            in.readFully(kleur);
            int n = in.readInt();
            if (n < 0 || n > ZeldaKaartBeeld.N * ZeldaKaartBeeld.N / 8 + 8) throw new IOException("kapot bestand");
            byte[] land = new byte[n];
            in.readFully(land);
            bladen.put(e.id(), new ZeldaKaartBeeld.Basis(vlak(e), kleur, BitSet.valueOf(land)));
            sleutels.put(e.id(), sl);
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("zelda-kaart " + e.id() + ": " + ex.getMessage() + " (wordt opnieuw gemeten)");
        }
    }

    private void bewaar(ZeldaEiland e, ZeldaKaartBeeld.Basis b, String sl) {
        map.mkdirs();
        File f = bestand(e), tmp = new File(map, e.id() + ".dat.tmp");
        try (DataOutputStream uit = new DataOutputStream(new FileOutputStream(tmp))) {
            uit.writeUTF(sl);
            uit.write(b.kleur());
            byte[] land = b.land().toByteArray();
            uit.writeInt(land.length);
            uit.write(land);
        } catch (IOException ex) {
            plugin.getLogger().warning("zelda-kaart " + e.id() + ": " + ex.getMessage());
            return;
        }
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            plugin.getLogger().warning("zelda-kaart " + e.id() + ": " + ex.getMessage());
        }
    }

    /** Opnieuw meten (alle eilanden, of één): {@code kitpvp zelda kaart opnieuw [eiland]}. */
    void opnieuw(ZeldaEiland alleen) {
        for (ZeldaEiland e : spel.eilanden) if (alleen == null || e == alleen) sleutels.remove(e.id());
        meting = null;
        gemeten = false;
    }

    /** Is er een eiland dat nog gemeten moet worden (of wordt)? */
    boolean bezig() {
        if (meting != null) return true;
        for (ZeldaEiland e : spel.eilanden) if (!sleutel(e).equals(sleutels.get(e.id()))) return true;
        return false;
    }

    /** Eén eiland meten: per chunk de pixels van het blad, chunks op de achtergrond laden (nooit nieuw maken). */
    private final class Meting {
        final ZeldaEiland e;
        final String sl;
        final ZeldaKaartBeeld.Vlak v;
        final World w;
        final byte[] soort = new byte[ZeldaKaartBeeld.N * ZeldaKaartBeeld.N], kleur = new byte[ZeldaKaartBeeld.N * ZeldaKaartBeeld.N];
        final int[] hoogte = new int[ZeldaKaartBeeld.N * ZeldaKaartBeeld.N];
        final Map<Long, List<Integer>> punten = new HashMap<>();
        final ArrayDeque<Long> rij = new ArrayDeque<>();
        final long begin = System.currentTimeMillis();
        int onderweg, chunks;

        Meting(ZeldaEiland e) {
            this.e = e;
            this.sl = sleutel(e);
            this.v = vlak(e);
            this.w = e.delen().get(0).w;
            List<int[]> vk = vakken(e);
            for (int j = 0; j < ZeldaKaartBeeld.N; j++)
                for (int i = 0; i < ZeldaKaartBeeld.N; i++) {
                    int x = (int) Math.floor(v.wx(i)), z = (int) Math.floor(v.wz(j));
                    boolean in = false;
                    for (int[] b : vk) in |= x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3];
                    if (!in) continue;                                  // buiten het eiland: zee
                    long k = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
                    punten.computeIfAbsent(k, n -> new ArrayList<>()).add(j * ZeldaKaartBeeld.N + i);
                }
            rij.addAll(punten.keySet());
            chunks = rij.size();
        }

        void tik() {
            for (int n = 0; n < 6 && onderweg < 3 && !rij.isEmpty(); n++) {   // een geladen chunk is meteen klaar: hoogstens zes per tik
                long k = rij.poll();
                int cx = (int) (k >> 32), cz = (int) k;
                onderweg++;
                w.getChunkAtAsync(cx, cz, false).whenComplete((c, fout) -> {
                    Runnable r = () -> {
                        onderweg--;
                        if (meting == this) meet(k, c != null && fout == null);
                    };
                    if (Bukkit.isPrimaryThread()) r.run();
                    else Bukkit.getScheduler().runTask(plugin, r);
                });
            }
        }

        boolean klaar() {
            return rij.isEmpty() && onderweg == 0;
        }

        void meet(long k, boolean geladen) {
            for (int p : punten.get(k)) {
                if (!geladen) continue;                               // niet gemaakt: zee
                int x = (int) Math.floor(v.wx(p % ZeldaKaartBeeld.N)), z = (int) Math.floor(v.wz(p / ZeldaKaartBeeld.N));
                Block b = w.getBlockAt(x, w.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE), z);
                for (int n = 0; n < 80 && b.getY() > w.getMinHeight() && onzichtbaar(b); n++) b = b.getRelative(BlockFace.DOWN);
                hoogte[p] = b.getY();
                if (nat(b.getType())) {
                    int diep = 0;
                    for (Block d = b; diep < 6 && nat(d.getType()); d = d.getRelative(BlockFace.DOWN)) diep++;
                    soort[p] = ZeldaKaartBeeld.NAT;
                    kleur[p] = (byte) diep;
                } else {
                    soort[p] = ZeldaKaartBeeld.LAND;
                    kleur[p] = kaartKleur(b);
                }
            }
        }
    }

    private final Map<Material, Byte> kleurVan = new HashMap<>();

    @SuppressWarnings({"deprecation", "removal"})
    private byte kaartKleur(Block b) {
        Material m = b.getType();
        Byte c = kleurVan.get(m);
        if (c != null) return c;
        int rgb = b.getBlockData().getMapColor().asRGB();
        if (m == Material.LAVA || m == Material.MAGMA_BLOCK) rgb = 0xd87f33;
        else if (m.name().contains("FROGLIGHT") || m.name().contains("LANTERN") || m == Material.SHROOMLIGHT || m == Material.GLOWSTONE) rgb = 0xe5e533;
        c = org.bukkit.map.MapPalette.matchColor(new java.awt.Color(rgb));
        kleurVan.put(m, c);
        return c;
    }

    private static boolean onzichtbaar(Block b) {
        Material m = b.getType();
        if (m.isAir() || m == Material.LIGHT || m == Material.BARRIER || m == Material.STRUCTURE_VOID || m.name().contains("GLASS")) return true;
        return !nat(m) && b.getBlockData().getMapColor().asRGB() == 0;   // iets zonder kaartkleur (een spinnenweb, een knop)
    }

    private static boolean nat(Material m) {
        return m == Material.WATER || m == Material.BUBBLE_COLUMN || m == Material.KELP || m == Material.KELP_PLANT
                || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
    }

    /** Elke tik: verder meten (hoogstens drie chunks tegelijk onderweg); pas als het bouwen klaar is. */
    private void tik() {
        if (Bukkit.getCurrentTick() % 1200 == 0) bewaar();
        if (meting == null) {
            if (gemeten || Bukkit.getCurrentTick() % 40 != 0) return;
            Bouwer b = plugin.bouwer();
            if (b != null && b.bezig()) return;
            gemeten = true;
            for (ZeldaEiland e : spel.eilanden) {
                if (vlak(e) == null || sleutel(e).equals(sleutels.get(e.id()))) continue;
                gemeten = false;
                if (e.delen().get(0).w == null) continue;
                meting = new Meting(e);
                break;
            }
            return;
        }
        meting.tik();
        if (!meting.klaar()) return;
        Meting m = meting;
        meting = null;
        ZeldaKaartBeeld.Basis b = ZeldaKaartBeeld.basis(m.v, m.soort, m.hoogte, m.kleur);
        bladen.put(m.e.id(), b);
        sleutels.put(m.e.id(), m.sl);
        versie++;
        bewaar(m.e, b, m.sl);
        plugin.getLogger().info("zelda-kaart: " + m.e.naam() + " gemeten (" + m.chunks + " chunks, " + (System.currentTimeMillis() - m.begin) + " ms)");
    }

    // ── plaatjes en controle ────────────────────────────────────────────────────────────────

    /**
     * {@code kitpvp zelda kaart <speler>}: zijn kaart als plaatje (beide kanten) in {@code plugins/KitPvP/kaart/}; zonder speler
     * het blad van elk eiland zonder mist. Geeft de bestandsnamen.
     */
    List<String> plaatjes(Player p) throws IOException {
        File uit = new File(plugin.getDataFolder(), "kaart");
        uit.mkdirs();
        List<String> l = new ArrayList<>();
        if (p == null) {
            for (ZeldaEiland e : spel.eilanden) {
                ZeldaKaartBeeld.Basis b = bladen.get(e.id());
                if (b == null) continue;
                byte[] px = ZeldaKaartBeeld.blad(b, new BitSet(), true);
                List<ZeldaKaartBeeld.Merk> m = new ArrayList<>();
                for (ZeldaHoofdstuk h : e.delen()) {
                    for (ZeldaHoofdstuk.Rust r : h.rust) ZeldaKaartBeeld.vlam(px, (int) Math.floor(b.vlak().px(r.plek.getX())), (int) Math.floor(b.vlak().pz(r.plek.getZ())), false);
                    for (ZeldaHoofdstuk.Teken t : h.tekens()) m.add(new ZeldaKaartBeeld.Merk(t.soort(), b.vlak().px(t.x() + 0.5), b.vlak().pz(t.z() + 0.5), 8));
                }
                ZeldaKaartBeeld.titel(px, e.naam());
                l.add(schrijf(uit, "zelda-kaart-" + e.id(), px, m));
            }
            List<ZeldaKaartBeeld.Eilandje> el = new ArrayList<>();
            for (ZeldaEiland e : spel.eilanden)
                if (bladen.get(e.id()) != null) el.add(new ZeldaKaartBeeld.Eilandje(bladen.get(e.id()), vakken(e), ZeldaKaartBeeld.Zicht.KLEUR, null));
            l.add(schrijf(uit, "zelda-kaart-zee", ZeldaKaartBeeld.zeekaart(el, null), List.of()));
            return l;
        }
        for (String kant : List.of(EILAND, ZEE)) {
            Blad b = nu(p, kant);
            l.add(schrijf(uit, "zelda-kaart-" + p.getName().toLowerCase() + "-" + b.kant(), b.px(), b.merken()));
        }
        return l;
    }

    /** Het blad van deze speler zoals het nu is (voor plaatjes en de test); zijn kaart in de hand tekent zich daarna opnieuw. */
    Blad nu(Player p, String kant) {
        Kijk k = kijk(p.getUniqueId());
        k.sleutel = null;
        k.doelTijd = 0;
        Blad b = blad(p, kant, System.currentTimeMillis());
        k.sleutel = null;
        k.merkTijd = 0;
        return b;
    }

    private static String schrijf(File map, String naam, byte[] px, List<ZeldaKaartBeeld.Merk> merken) throws IOException {
        File f = new File(map, naam + ".png");
        ImageIO.write(ZeldaKaartBeeld.plaatje(px, merken, 4), "png", f);
        return f.getPath();
    }

    /** Voor de zelftest: wat er niet klopt aan de kaart (rustplaatsen, dorpen en kerkers buiten hun blad of deel). */
    void zelftest(List<String> fouten, List<String> info) {
        int klaar = 0;
        for (ZeldaEiland e : spel.eilanden) {
            ZeldaKaartBeeld.Vlak v = vlak(e);
            if (v == null) continue;
            if (bladen.containsKey(e.id())) klaar++;
            for (ZeldaHoofdstuk h : e.delen()) {
                for (ZeldaHoofdstuk.Rust r : h.rust)
                    if (!v.op(r.plek.getX(), r.plek.getZ())) fouten.add("zelda kaart " + e.id() + ": rustplaats " + r.id + " valt buiten het blad");
                for (ZeldaHoofdstuk.Teken t : h.tekens()) {
                    if (h.speelgebied == null || !h.speelgebied.in(t.x() + 0.5, h.speelgebied.y1(), t.z() + 0.5))
                        fouten.add("zelda kaart " + h.id() + ": " + t.soort() + " op " + t.x() + "," + t.z() + " ligt buiten het deel");
                    if (type(t.soort()) == null) fouten.add("zelda kaart " + h.id() + ": onbekend teken " + t.soort());
                }
            }
            if (e.aanlegPlek() != null && !ZeldaKaartBeeld.Vlak.zee().op(e.aanlegPlek().getX(), e.aanlegPlek().getZ()))
                fouten.add("zelda kaart " + e.id() + ": de aanleg valt buiten de zeekaart");
        }
        info.add("kaarten=" + klaar + "/" + spel.eilanden.size() + (bezig() ? " (meten)" : ""));
    }
}
