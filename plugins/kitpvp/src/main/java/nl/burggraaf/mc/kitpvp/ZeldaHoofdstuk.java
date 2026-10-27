package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;

/**
 * Een hoofdstuk van Story of Zelda: een stuk wereld (een {@link Deel}) met daarin, in de volgorde van het pad, de
 * onderdelen van het spel: rustplaatsen (checkpoints), kamers (dichte deuren, golven vijanden, muziek), uitdagingen op
 * tijd, puzzels (reeksen), vondsten, verhaalmomenten en personen. {@link ZeldaSpel} laat ze werken; een nieuw
 * hoofdstuk is een subklasse die in {@link #bouw} zijn wereld neerzet en de onderdelen aanmaakt (zie MODI.md).
 *
 * <p>Alle plekken zijn absolute wereldcoördinaten binnen {@link Zelda#GEBIED}. {@link #bouw} draait bij elke start (dan
 * worden de onderdelen opnieuw berekend); blokken zet hij alleen als het deel herbouwd wordt.
 *
 * <p>Zelda 2.0 (zie ZELDA.md): een hoofdstuk is een <em>deel</em> van een eiland ({@link ZeldaEiland}); een eiland heeft
 * één of twee delen. Erbij gekomen: bronnen (genezen en flesjes vullen), doelen (voor {@code /hint}), personen met
 * gesprekken, zijmissies, uitleg die je kunt overslaan, tips na doodgaan en muziek per plek.
 */
abstract class ZeldaHoofdstuk {

    /** Blokgebied, grenzen inbegrepen. */
    record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        static Box van(int ax, int ay, int az, int bx, int by, int bz) {
            return new Box(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
        }

        boolean in(Location l) {
            return in(l.getX(), l.getY(), l.getZ());
        }

        boolean in(double x, double y, double z) {
            return x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1;
        }

        Box groter(int d) {
            return new Box(x1 - d, y1 - d, z1 - d, x2 + d, y2 + d, z2 + d);
        }

        /** Midden van de bodem (om erin te stappen). */
        Location midden(World w) {
            return new Location(w, (x1 + x2 + 1) / 2.0, y1, (z1 + z2 + 1) / 2.0);
        }

        boolean binnen(Box o) {
            return x1 >= o.x1 && y1 >= o.y1 && z1 >= o.z1 && x2 <= o.x2 && y2 <= o.y2 && z2 <= o.z2;
        }
    }

    /** Vijanden (zie {@link ZeldaSpel#spawn}). */
    enum Soort { VEENLOPER, SPIN, MOSSCHUTTER, VEENREUS, SOLDAAT, KRUISBOOG, HEKS, ROVER, ROVERSCHUTTER }

    /** Een regel van een personage: {@code spreker} (mag null: verteller) en tekst. */
    record Regel(String spreker, String tekst) {}

    static Regel r(String spreker, String tekst) {
        return new Regel(spreker, tekst);
    }

    /** Rustplaats: hier bewaar je je voortgang en kom je terug na doodgaan. Index 0 = het begin van het hoofdstuk. */
    static final class Rust {
        final String id, naam;
        final Location plek;
        final Box zone;
        final List<String> geeft;                           // vlaggen die je hier zeker hebt (vondsten ervoor)
        int index;

        Rust(String id, String naam, Location plek, Box zone, List<String> geeft) {
            this.id = id;
            this.naam = naam;
            this.plek = plek;
            this.zone = zone;
            this.geeft = geeft;
        }
    }

    /** Blokken die open of dicht kunnen (poort, brug). Open = {@code open} (meestal lucht). */
    static final class Deur {
        final List<int[]> blokken = new ArrayList<>();
        final BlockData dicht, open;
        boolean isOpen;

        Deur(BlockData dicht, BlockData open) {
            this.dicht = dicht;
            this.open = open;
        }

        Deur blok(int x, int y, int z) {
            blokken.add(new int[]{x, y, z});
            return this;
        }

        /** Een rechthoekige doorgang. */
        Deur vlak(int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) blok(x, y, z);
            return this;
        }

        Box omvang() {
            int[] a = blokken.get(0);
            Box b = new Box(a[0], a[1], a[2], a[0], a[1], a[2]);
            for (int[] c : blokken) b = Box.van(Math.min(b.x1(), c[0]), Math.min(b.y1(), c[1]), Math.min(b.z1(), c[2]),
                    Math.max(b.x2(), c[0]), Math.max(b.y2(), c[1]), Math.max(b.z2(), c[2]));
            return b;
        }
    }

    enum Fase { RUST, BEZIG, GEWONNEN }

    /** Een kamer: wie de {@code start} in loopt, sluit de deuren; golven vijanden; allemaal verslagen = deuren open. */
    static final class Kamer {
        final String id, naam;
        final Box binnen, start;
        final Deur in, uit;
        final List<Location> spawns = new ArrayList<>();
        final List<List<Soort>> golven = new ArrayList<>();
        final List<Regel> bijStart = new ArrayList<>();
        final List<Regel> tips = new ArrayList<>();         // na de 1e, 2e … keer doodgaan hier: één tip (Wiske)
        final List<List<Soort>> extra = new ArrayList<>(); // golven erbij als je de bel luidt (nog eens, zwaarder)
        ZeldaMuziek.Lied muziek = ZeldaMuziek.GEVECHT;
        ZeldaBaas baas;                                     // alleen bij een baaskamer
        long hemel = -1;                                    // eigen hemeltijd binnen (-1 = die van de plek)
        int[] bel;                                          // een bel in de kamer: luiden = nog eens vechten (null = geen)
        boolean vrijwillig;                                 // niet op het hoofdpad (telt niet mee als verplichte kamer)
        boolean alleenBel;                                  // een oefenplek: deuren open, gevochten wordt alleen als je de bel luidt
        // tijdens het spel
        Fase fase = Fase.RUST;
        boolean terug;                                      // dit gevecht begon met de bel: de extra golven doen mee
        final List<LivingEntity> vijanden = new ArrayList<>();
        int golf;
        long volgendeGolf, leegSinds;
        final Set<UUID> meegedaan = new HashSet<>();

        Kamer(String id, String naam, Box binnen, Box start, Deur in, Deur uit) {
            this.id = id;
            this.naam = naam;
            this.binnen = binnen;
            this.start = start;
            this.in = in;
            this.uit = uit;
        }

        Kamer golf(Soort... s) {
            golven.add(List.of(s));
            return this;
        }

        /** Een golf die alleen meedoet als het gevecht met de bel begon (wie terugkomt en nog eens wil). */
        Kamer extra(Soort... s) {
            extra.add(List.of(s));
            return this;
        }
    }

    /** Uitdaging op tijd: stap op de startplaat, de poort gaat open, haal de finish voor de tijd om is. */
    static final class Uitdaging {
        final String id, naam;
        final Box start, finish, gebied;
        final Deur poort;
        final int seconden;
        final Map<UUID, Long> loopt = new HashMap<>();      // speler → starttijd (ms)
        long openTot;
        Location bord;                                      // bord met de beste tijden (null = geen)
        Actie daarna;                                       // na het halen (bv. een missiestap verder)
        org.bukkit.entity.TextDisplay bordTekst;
        boolean bordOud = true;

        Uitdaging(String id, String naam, Box start, Box finish, Box gebied, Deur poort, int seconden) {
            this.id = id;
            this.naam = naam;
            this.start = start;
            this.finish = finish;
            this.gebied = gebied;
            this.poort = poort;
            this.seconden = seconden;
        }
    }

    /**
     * Puzzel: zet alle dingen aan binnen de tijd (vanaf het eerste). {@code KLIK} = rechtsklikken (vuurschalen),
     * {@code PIJL} = raak ze met een pijl (doelwitten). Opgelost = de deur gaat open (of de brug komt), tot er een
     * minuut niemand meer in het gebied is.
     */
    static final class Reeks {
        enum Hoe { KLIK, PIJL }

        final String id, naam;
        final Hoe hoe;
        final List<int[]> dingen = new ArrayList<>();
        final BlockData uit, aan;
        final Deur deur;
        final Box gebied;
        final int seconden;
        final List<Regel> bijOplossen = new ArrayList<>();
        // tijdens het spel
        final Set<Integer> isAan = new HashSet<>();
        long tot, leegSinds;
        boolean opgelost;

        Reeks(String id, String naam, Hoe hoe, BlockData uit, BlockData aan, Deur deur, Box gebied, int seconden) {
            this.id = id;
            this.naam = naam;
            this.hoe = hoe;
            this.uit = uit;
            this.aan = aan;
            this.deur = deur;
            this.gebied = gebied;
            this.seconden = seconden;
        }

        int index(int x, int y, int z) {
            for (int i = 0; i < dingen.size(); i++) {
                int[] d = dingen.get(i);
                if (d[0] == x && d[1] == y && d[2] == z) return i;
            }
            return -1;
        }
    }

    /**
     * Puzzel met draaispiegels: licht komt uit {@code bron} in {@code richting} en loopt rechtdoor tot het iets raakt.
     * Raakt het een spiegel, dan gaat het verder in de richting waar die spiegel naar wijst (rechtsklik = een kwartslag
     * draaien). Komt het licht bij het {@code doel}, dan is hij opgelost: het doel gaat aan en de deur open, tot er een
     * minuut niemand meer is. Geen tijd: rustig puzzelen. Wie hem al eens oploste ({@code r:<id>}) vindt hem opgelost.
     * Een spiegel zonder {@code goed} is een lokspiegel (voor de oplossing maakt hij niet uit).
     */
    static final class Spiegels {
        /** Zo ziet een spiegel eruit: een kristal dat wijst waar het licht heen gaat. */
        static final Material BLOK = Material.AMETHYST_CLUSTER;

        final String id, naam;
        final int[] bron, doel;
        final BlockFace richting;
        final List<int[]> spiegels = new ArrayList<>();
        final List<BlockFace> begin = new ArrayList<>(), goed = new ArrayList<>();
        final BlockData doelUit, doelAan;
        final Deur deur;
        final Box gebied;
        final List<Regel> bijOplossen = new ArrayList<>();
        // tijdens het spel
        final List<BlockFace> stand = new ArrayList<>();
        boolean opgelost;
        long leegSinds;

        Spiegels(String id, String naam, int[] bron, BlockFace richting, int[] doel, BlockData doelUit, BlockData doelAan, Deur deur, Box gebied) {
            this.id = id;
            this.naam = naam;
            this.bron = bron;
            this.richting = richting;
            this.doel = doel;
            this.doelUit = doelUit;
            this.doelAan = doelAan;
            this.deur = deur;
            this.gebied = gebied;
        }

        /** Een spiegel op x, y, z: zo staat hij aan het begin, en zo hoort hij (null = lokspiegel). */
        Spiegels spiegel(int x, int y, int z, BlockFace begin, BlockFace goed) {
            spiegels.add(new int[]{x, y, z});
            this.begin.add(begin);
            this.goed.add(goed);
            stand.add(begin);
            return this;
        }

        int index(int x, int y, int z) {
            for (int i = 0; i < spiegels.size(); i++) {
                int[] d = spiegels.get(i);
                if (d[0] == x && d[1] == y && d[2] == z) return i;
            }
            return -1;
        }

        /** Hoe de spiegels staan als hij opgelost is (lokspiegels zoals ze nu staan). */
        List<BlockFace> oplossing() {
            List<BlockFace> l = new ArrayList<>();
            for (int i = 0; i < spiegels.size(); i++) l.add(goed.get(i) != null ? goed.get(i) : stand.get(i));
            return l;
        }

        static BlockData blok(BlockFace f) {
            BlockData d = BLOK.createBlockData();
            if (d instanceof org.bukkit.block.data.Directional r) r.setFacing(f);
            return d;
        }
    }

    /** Iets om te vinden: loop erdoor en je hebt het (vlag), het zweeft boven een sokkel. */
    static final class Vondst {
        final String vlag, naam, uitleg;
        final Material item;
        final Location plek;
        final Box zone;
        boolean verborgen;                                  // niet in het pad: je huisdier snuffelt hem op
        String vereist;                                     // alleen te zien en te pakken met deze vlag (bv. een missiestap)
        String voorwerp;                                    // wel te zien, maar pas te pakken met dit voorwerp (vlag, bv. klauwhaak)
        Actie daarna;                                       // na het vinden (bv. een missiestap verder)
        ItemDisplay toon;

        Vondst(String vlag, String naam, String uitleg, Material item, Location plek, Box zone) {
            this.vlag = vlag;
            this.naam = naam;
            this.uitleg = uitleg;
            this.item = item;
            this.plek = plek;
            this.zone = zone;
        }
    }

    /** Een verhaalmoment: de eerste keer dat je in de zone komt, een paar korte regels. */
    static final class Verhaal {
        final String id;
        final Box zone;
        final List<Regel> regels;
        String vereist;                                     // alleen als je deze vlag hebt
        Predicate<ZeldaVoortgang.Stand> als;                // en alleen als dit klopt (null = altijd)
        boolean einde;                                      // hoofdstuk voltooid
        boolean uitleg;                                     // uitleg (hoe iets werkt): overgeslagen met «Uitleg: uit»

        Verhaal(String id, Box zone, List<Regel> regels) {
            this.id = id;
            this.zone = zone;
            this.regels = regels;
        }
    }

    /** Wat er na een gesprek gebeurt (missie beginnen, beloning geven): {@code (spel, speler, stand) -> …}. */
    @FunctionalInterface
    interface Actie {
        void doe(ZeldaSpel spel, org.bukkit.entity.Player p, ZeldaVoortgang.Stand s);
    }

    /**
     * Een gesprek: als {@code als} klopt (bv. een missiestap), zegt de persoon {@code regels} en daarna gebeurt
     * {@code daarna}. Een persoon neemt het eerste gesprek dat klopt. Met een {@code id} wordt het onthouden
     * ({@code g:<id>}); zolang je een gesprek met id nog niet hoorde, glinstert de persoon zacht (er is iets nieuws).
     */
    record Gesprek(String id, Predicate<ZeldaVoortgang.Stand> als, List<Regel> regels, Actie daarna) {}

    /** Een personage dat er staat (rechtsklik = praten). Alleen in de wereld als er iemand in de buurt is. */
    static final class Persoon {
        final String naam;
        final Location plek;
        final Villager.Profession beroep;
        final List<Regel> praat;                            // als geen gesprek klopt
        final List<Gesprek> gesprekken = new ArrayList<>();
        org.bukkit.entity.EntityType soort = org.bukkit.entity.EntityType.VILLAGER;
        Villager.Type streek;                               // kleding van een dorpeling (null = vlakte)
        boolean kind;                                       // klein (een kind)
        LivingEntity ent;

        Persoon(String naam, Location plek, Villager.Profession beroep, List<Regel> praat) {
            this.naam = naam;
            this.plek = plek;
            this.beroep = beroep;
            this.praat = praat;
        }

        /** Een gesprek erbij (in volgorde: het eerste dat klopt telt). */
        Persoon gesprek(String id, Predicate<ZeldaVoortgang.Stand> als, List<Regel> regels, Actie daarna) {
            gesprekken.add(new Gesprek(id, als, regels, daarna));
            return this;
        }

        /** Een gesprek dat je maar één keer hoort (daarna telt het volgende). */
        Persoon eenmalig(String id, Predicate<ZeldaVoortgang.Stand> als, List<Regel> regels, Actie daarna) {
            return gesprek(id, s -> !s.heeft("g:" + id) && (als == null || als.test(s)), regels, daarna);
        }

        /** Het gesprek dat nu klopt (null = het gewone {@link #praat}). */
        Gesprek gesprek(ZeldaVoortgang.Stand s) {
            for (Gesprek g : gesprekken) if (g.als() == null || g.als().test(s)) return g;
            return null;
        }

        /** Heeft deze persoon iets nieuws voor je? */
        boolean nieuw(ZeldaVoortgang.Stand s) {
            Gesprek g = gesprek(s);
            return g != null && g.id() != null && !s.heeft("g:" + g.id());
        }
    }

    /** Een bron (feeënkring): erin staan geneest, en je flesjes worden weer vol. Zet hem bij elke kamer. */
    static final class Bron {
        final String id;
        final Location midden;
        final Box zone;

        Bron(String id, Location midden, Box zone) {
            this.id = id;
            this.midden = midden;
            this.zone = zone;
        }
    }

    /**
     * Een doel van het hoofdpad, voor {@code /hint} (en Wiske): het eerste doel dat nog niet {@code klaar} is, is waar
     * je heen moet. {@code tekst} = de eerste hint (een duwtje), {@code verder} = als je het nog eens vraagt (wat je
     * moet doen); Wiske vliegt dan ook even die kant op ({@code plek}).
     */
    record Doel(String id, String tekst, String verder, Location plek, Predicate<ZeldaVoortgang.Stand> klaar, String groep) {}

    /** Een zijmissie: per stap een korte zin voor de hint en het opdrachtenlijstje (stap 1 = {@code stappen.get(0)}). */
    record Missie(String id, String naam, List<String> stappen) {}

    /** Zo ziet een gebarsten muur eruit (op elk eiland hetzelfde, zodat spelers hem leren herkennen). */
    static final Material BARST = Material.CRACKED_DEEPSLATE_BRICKS;
    /** Zo ziet een haakpunt voor de klauwhaak eruit. */
    static final Material HAAKPUNT = Material.LODESTONE;

    /**
     * Een gebarsten muur: dicht tot een bom hem openbreekt ({@link ZeldaSpel#breek}; de bommen komen op eiland 2). Wie
     * hem openbrak ({@code b:<id>}) vindt hem open; zonder iemand in de buurt gaat hij na een minuut weer dicht.
     */
    static final class Barst {
        final String id;
        final Deur muur;
        final Box gebied;
        Location voor;                                      // waar je staat om er een bom tegen te gooien (test; null = vanzelf)
        long leegSinds;

        Barst(String id, Deur muur, Box gebied) {
            this.id = id;
            this.muur = muur;
            this.gebied = gebied;
        }
    }

    /**
     * Een haakpunt voor de klauwhaak (eiland 3): een blok ({@link #HAAKPUNT}) waar de haak in grijpt, en de plek waar je
     * dan staat. Alleen gegevens: het voorwerp dat je erheen trekt, bouwt de sessie van eiland 3.
     */
    record Haakpunt(String id, int x, int y, int z, Location landing) {}

    /**
     * Een poort die opengaat voor wie iets heeft of weet ({@code mag}, bv. het lichtwoord van Tijmen): staat er iemand in
     * het {@code gebied} voor wie hij open mag, dan gaat hij open (voor iedereen); een minuut niemand in de buurt = weer
     * dicht. Zo kan een persoon een stuk van het eiland openen ({@code kitpvp zelda lopen} telt hem als open).
     */
    static final class Poort {
        final String id;
        final Deur deur;
        final Box gebied;
        final Predicate<ZeldaVoortgang.Stand> mag;
        long leegSinds;

        Poort(String id, Deur deur, Box gebied, Predicate<ZeldaVoortgang.Stand> mag) {
            this.id = id;
            this.deur = deur;
            this.gebied = gebied;
            this.mag = mag;
        }
    }

    /** In de volgorde van het pad: hier praat je met {@code wie} (de test praat tot hij niets nieuws meer heeft). */
    record Praten(Persoon wie) {}

    // ── de onderdelen, in de volgorde van het pad ───────────────────────────────────────────

    final List<Rust> rust = new ArrayList<>();
    final List<Kamer> kamers = new ArrayList<>();
    final List<Uitdaging> uitdagingen = new ArrayList<>();
    final List<Reeks> reeksen = new ArrayList<>();
    final List<Vondst> vondsten = new ArrayList<>();
    final List<Verhaal> verhalen = new ArrayList<>();
    final List<Persoon> personen = new ArrayList<>();
    final List<Bron> bronnen = new ArrayList<>();
    final List<Doel> doelen = new ArrayList<>();
    final List<Missie> missies = new ArrayList<>();
    final List<Barst> barsten = new ArrayList<>();
    final List<Haakpunt> haakpunten = new ArrayList<>();
    final List<Spiegels> spiegels = new ArrayList<>();
    final List<Poort> poorten = new ArrayList<>();
    final java.util.Set<String> stukjes = new java.util.LinkedHashSet<>();   // de hartstukjes van dit deel (vondst of beloning)
    final List<Object> volgorde = new ArrayList<>();
    ZeldaEiland eiland;                                     // gezet door ZeldaEiland
    World w;
    Box speelgebied;                                        // wie hierbuiten komt, gaat terug naar zijn rustplaats

    /** Kort, vast id (data, commando's), bv. {@code h1}. */
    abstract String id();

    /** Zoals spelers het zien, bv. «Het Lindewoud». */
    abstract String naam();

    /** Bouwversie: hoger = bij de volgende start herbouwen. */
    abstract int versie();

    /** Id van het bouwdeel (gebouwd.yml, {@code kitpvp bouw <id>}). */
    abstract String deelId();

    /** Zet de wereld neer (alleen als {@code b.plaats}) en maak de onderdelen aan (altijd). */
    abstract void bouw(Bouwer b);

    /** Hemeltijd voor een speler op deze plek (per speler: de dag schuift op terwijl je verder komt). */
    long hemel(Location l) {
        return 6000;
    }

    /** Hemeltijd voor deze speler: standaard per plek; een open eiland kan de dag laten opschuiven met je voortgang. */
    long hemel(Location l, ZeldaVoortgang.Stand s) {
        return hemel(l);
    }

    /** Naam van de plek voor de sidebar. */
    String plekNaam(Location l) {
        return naam();
    }

    /** Achtergrondmuziek op deze plek (nooit null: er is altijd muziek). Een eiland kiest per streek een eigen lied. */
    ZeldaMuziek.Lied muziek(Location l) {
        return ZeldaMuziek.WOUD;
    }

    /**
     * Oude standen aanvullen (bij binnenkomen): wat je zeker al gedaan hebt, als vlag erbij (bv. een kamer die je won
     * voordat er {@code k:}-vlaggen waren). True als er iets bij kwam.
     */
    boolean aanvullen(ZeldaVoortgang.Stand s) {
        return false;
    }

    /**
     * Wat Wiske bij {@code /hint} zegt als er op het eiland niets meer te doen is maar het nog niet af is (het laatste
     * deel telt; bv. «het volgende deel komt nog»). Null = het gewone «kijk eens rond».
     */
    String wacht() {
        return null;
    }

    /**
     * Elke seconde (zolang er iemand in Zelda is): wat dit deel zelf laat bewegen of oplichten (een vlieger, een
     * lichtbundel; op eiland 2b eb en vloed). {@code wie} = de spelers op dit deel.
     */
    void tik(ZeldaSpel spel, List<org.bukkit.entity.Player> wie, long nu) {
    }

    /** Een bom ontploft op dit deel (na de gebarsten muren; bv. een baas verdoven, een ton laten springen). */
    void boem(ZeldaSpel spel, org.bukkit.entity.Player p, Location l) {
    }

    /** Een onderdeel op id (rust, kamer, uitdaging, puzzel, verhaal; vondst op vlag; bron; persoon op naam), of null. */
    Object onderdeel(String id) {
        for (Object o : volgorde) {
            String i = o instanceof Rust r ? r.id : o instanceof Kamer k ? k.id : o instanceof Uitdaging u ? u.id
                    : o instanceof Reeks r ? r.id : o instanceof Verhaal v ? v.id : o instanceof Vondst v ? v.vlag
                    : o instanceof Spiegels sp ? sp.id : o instanceof Barst b ? b.id : o instanceof Poort po ? po.id : null;
            if (id.equals(i)) return o;
        }
        for (Bron b : bronnen) if (b.id.equals(id)) return b;
        for (Persoon p : personen) if (p.naam.equals(id)) return p;
        for (Barst b : barsten) if (b.id.equals(id)) return b;
        return null;
    }

    /** Waar een onderdeel ligt (om naartoe te wijzen), of null. */
    Location plekVan(String id) {
        Object o = onderdeel(id);
        if (o instanceof Rust r) return r.plek;
        if (o instanceof Kamer k) return k.start.midden(w);
        if (o instanceof Uitdaging u) return u.start.midden(w);
        if (o instanceof Reeks r) return r.gebied.midden(w);
        if (o instanceof Spiegels sp) return sp.gebied.midden(w);
        if (o instanceof Verhaal v) return v.zone.midden(w);
        if (o instanceof Vondst v) return v.plek;
        if (o instanceof Bron b) return b.midden;
        if (o instanceof Persoon p) return p.plek;
        if (o instanceof Barst b) return b.muur.omvang().midden(w);
        if (o instanceof Poort po) return po.gebied.midden(w);
        return null;
    }

    Rust rust(String id) {
        for (Rust r : rust) if (r.id.equals(id)) return r;
        return null;
    }

    Missie missie(String id) {
        for (Missie m : missies) if (m.id().equals(id)) return m;
        return null;
    }

    /** Begin opnieuw met lege lijsten (bij elke bouw). */
    protected void leeg(World wereld) {
        w = wereld;
        for (Kamer k : kamers) k.vijanden.forEach(org.bukkit.entity.Entity::remove);     // bij herbouwen tijdens het spel
        for (Vondst v : vondsten) if (v.toon != null) v.toon.remove();
        for (Persoon p : personen) if (p.ent != null) p.ent.remove();
        for (Uitdaging u : uitdagingen) if (u.bordTekst != null) u.bordTekst.remove();
        rust.clear();
        kamers.clear();
        uitdagingen.clear();
        reeksen.clear();
        vondsten.clear();
        verhalen.clear();
        personen.clear();
        bronnen.clear();
        doelen.clear();
        missies.clear();
        barsten.clear();
        haakpunten.clear();
        spiegels.clear();
        poorten.clear();
        stukjes.clear();
        volgorde.clear();
    }

    protected Rust rust(Rust r) {
        r.index = rust.size();
        rust.add(r);
        volgorde.add(r);
        return r;
    }

    /**
     * Een rustplaats met een vast nummer (het nummer is de opslag: {@code h1:3}), op zijn plek in de volgorde van het pad.
     * Voor een eiland dat opnieuw is ingedeeld: de oude rustplaatsen houden hun nummer, een nieuwe krijgt het volgende.
     * De nummers moeten aan het eind 0 … n−1 zijn (de zelftest controleert het).
     */
    protected Rust rust(int index, Rust r) {
        r.index = index;
        int i = 0;
        while (i < rust.size() && rust.get(i).index < index) i++;
        rust.add(i, r);
        volgorde.add(r);
        return r;
    }

    /** Een poort op het pad (de test loopt erheen, met wat ervoor nodig is). */
    protected Poort poort(Poort p) {
        poorten.add(p);
        volgorde.add(p);
        return p;
    }

    /** Op dit punt van het pad praat je met {@code p} (bv. de wegwijzer die je verder stuurt). */
    protected Persoon praten(Persoon p) {
        volgorde.add(new Praten(p));
        return p;
    }

    protected Kamer kamer(Kamer k) {
        kamers.add(k);
        volgorde.add(k);
        return k;
    }

    protected Uitdaging uitdaging(Uitdaging u) {
        uitdagingen.add(u);
        volgorde.add(u);
        return u;
    }

    protected Reeks reeks(Reeks r) {
        reeksen.add(r);
        volgorde.add(r);
        return r;
    }

    protected Vondst vondst(Vondst v) {
        vondsten.add(v);
        volgorde.add(v);
        if (v.vlag.startsWith("hs:")) stukjes.add(v.vlag);
        return v;
    }

    /** Een hartstukje dat je niet vindt maar krijgt (beloning van een zijmissie): telt mee op de kaart. */
    protected String stukje(String vlag) {
        stukjes.add(vlag);
        return vlag;
    }

    protected Barst barst(Barst b) {
        barsten.add(b);
        return b;
    }

    /** Een gebarsten muur óp het pad (je moet erdoor): telt mee in de volgorde (de test gooit er een bom tegen). */
    protected Barst barstOpPad(Barst b) {
        barst(b);
        volgorde.add(b);
        return b;
    }

    protected Spiegels spiegels(Spiegels s) {
        spiegels.add(s);
        volgorde.add(s);
        return s;
    }

    protected Haakpunt haakpunt(Haakpunt h) {
        haakpunten.add(h);
        return h;
    }

    protected Verhaal verhaal(Verhaal v) {
        verhalen.add(v);
        volgorde.add(v);
        return v;
    }

    protected Persoon persoon(Persoon p) {
        personen.add(p);
        return p;
    }

    /** Zet een onderdeel in de volgorde van het pad direct na onderdeel {@code id} (voor wat later is toegevoegd). */
    protected <T> T na(String id, T o) {
        Object voor = onderdeel(id);
        if (voor == null || !volgorde.remove(o)) return o;
        volgorde.add(volgorde.indexOf(voor) + 1, o);
        return o;
    }

    protected Bron bron(Bron b) {
        bronnen.add(b);
        return b;
    }

    /** Een bron: een feeënkring met straal 1,5 rond {@code midden} (de vloer eronder moet er al zijn). */
    protected Bron bron(String id, Location midden) {
        int x = midden.getBlockX(), y = midden.getBlockY(), z = midden.getBlockZ();
        return bron(new Bron(id, midden, Box.van(x - 1, y - 1, z - 1, x + 1, y + 3, z + 1)));
    }

    protected Doel doel(String id, String tekst, String verder, Location plek, Predicate<ZeldaVoortgang.Stand> klaar) {
        return doel(null, id, tekst, verder, plek, klaar);
    }

    /**
     * Een doel in een {@code groep}: doelen in dezelfde groep (na elkaar in de lijst) mag je in elke volgorde doen; de hint
     * noemt het dichtstbijzijnde dat nog niet klaar is.
     */
    protected Doel doel(String groep, String id, String tekst, String verder, Location plek, Predicate<ZeldaVoortgang.Stand> klaar) {
        Doel d = new Doel(id, tekst, verder, plek, klaar, groep);
        doelen.add(d);
        return d;
    }

    protected Missie missie(String id, String naam, String... stappen) {
        Missie m = new Missie(id, naam, List.of(stappen));
        missies.add(m);
        return m;
    }
}
