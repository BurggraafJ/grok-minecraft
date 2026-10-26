package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
    enum Soort { VEENLOPER, SPIN, MOSSCHUTTER, VEENREUS, SOLDAAT, KRUISBOOG, HEKS }

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
        ZeldaMuziek.Lied muziek = ZeldaMuziek.GEVECHT;
        ZeldaBaas baas;                                     // alleen bij een baaskamer
        long hemel = -1;                                    // eigen hemeltijd binnen (-1 = die van de plek)
        // tijdens het spel
        Fase fase = Fase.RUST;
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

    /** Iets om te vinden: loop erdoor en je hebt het (vlag), het zweeft boven een sokkel. */
    static final class Vondst {
        final String vlag, naam, uitleg;
        final Material item;
        final Location plek;
        final Box zone;
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
        boolean einde;                                      // hoofdstuk voltooid

        Verhaal(String id, Box zone, List<Regel> regels) {
            this.id = id;
            this.zone = zone;
            this.regels = regels;
        }
    }

    /** Een personage dat er staat (rechtsklik = praten). Alleen in de wereld als er iemand in de buurt is. */
    static final class Persoon {
        final String naam;
        final Location plek;
        final Villager.Profession beroep;
        final List<Regel> praat;
        Villager ent;

        Persoon(String naam, Location plek, Villager.Profession beroep, List<Regel> praat) {
            this.naam = naam;
            this.plek = plek;
            this.beroep = beroep;
            this.praat = praat;
        }
    }

    // ── de onderdelen, in de volgorde van het pad ───────────────────────────────────────────

    final List<Rust> rust = new ArrayList<>();
    final List<Kamer> kamers = new ArrayList<>();
    final List<Uitdaging> uitdagingen = new ArrayList<>();
    final List<Reeks> reeksen = new ArrayList<>();
    final List<Vondst> vondsten = new ArrayList<>();
    final List<Verhaal> verhalen = new ArrayList<>();
    final List<Persoon> personen = new ArrayList<>();
    final List<Object> volgorde = new ArrayList<>();
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

    /** Naam van de plek voor de sidebar. */
    String plekNaam(Location l) {
        return naam();
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
        volgorde.clear();
    }

    protected Rust rust(Rust r) {
        r.index = rust.size();
        rust.add(r);
        volgorde.add(r);
        return r;
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
        return v;
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
}
