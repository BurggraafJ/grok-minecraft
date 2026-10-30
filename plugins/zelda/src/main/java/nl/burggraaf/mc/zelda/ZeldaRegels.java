package nl.burggraaf.mc.zelda;

import java.util.ArrayList;
import java.util.List;

/**
 * De regels van Story of Zelda 2.0 die niets met de wereld te maken hebben (en dus in unit-tests kunnen): harten,
 * harnas, flesjes, welk doel {@code /hint} noemt, wat «opnieuw vanaf hier» vergeet en waar je binnenkomt.
 */
final class ZeldaRegels {

    private ZeldaRegels() {
    }

    /** Harten aan het begin. */
    static final int BASIS_HARTEN = 8;
    /** Nooit meer dan dit (twee rijen). */
    static final int MAX_HARTEN = 20;
    /** Zoveel hartstukjes = één hart. */
    static final int STUKJES_PER_HART = 4;
    /** De smidshelm van Kees (Lindedorp; ook bij de Moerashut en de haven voor wie daar al voorbij is). */
    static final String HELM = "helm1";

    /** Draag je de smidshelm (ijzeren helm, en een broek bij de groene tuniek)? Alleen onder het wachtersharnas. */
    static boolean smidshelm(ZeldaVoortgang.Stand s, int harnas) {
        return s.heeft(HELM) && harnas <= 1;
    }

    /**
     * Balans van alle gevechten op één plek (Jelle 27-10: "langer maar minder zwaar, meer verschillende tegenstanders").
     * {@link #SCHADE}: wat elke vijand (ook een baas, ook pijlen en drankjes) op een speler doet, als factor.
     * {@link #BAAS_LEVEN}: het leven van een baas (en per extra speler) als factor. {@link #GOLF_PAUZE}: adem tussen golven.
     * De golven zelf staan per kamer (meer golven, gemengd: vechter + schutter + snel klein beestje).
     */
    static final double SCHADE = 0.55;
    static final double BAAS_LEVEN = 1.5;
    static final long GOLF_PAUZE = 4000L;

    /** Hoogste harnas. */
    static final int MAX_HARNAS = 4;
    /** Hoeveel een vol flesje geneest (halve harten). */
    static final double FLES_HP = 10;
    /** Met sterk bronwater (vlag {@code sterkwater}, van Hanne) geneest een flesje meer. */
    static final double FLES_HP_STERK = 14;

    /** Basis + hartcontainers ({@code hart…}) + een hart per vier hartstukjes ({@code hs:…}). */
    static int harten(ZeldaVoortgang.Stand s) {
        return Math.min(MAX_HARTEN, BASIS_HARTEN + s.aantal("hart") + s.aantal("hs:") / STUKJES_PER_HART);
    }

    /** Hartstukjes voor het volgende hart (0 … 3). */
    static int losseStukjes(ZeldaVoortgang.Stand s) {
        return s.aantal("hs:") % STUKJES_PER_HART;
    }

    /** Het beste harnas dat je hebt: 0 = groene tuniek, 1 … {@link #MAX_HARNAS} ({@code harnas1} …). */
    static int harnas(ZeldaVoortgang.Stand s) {
        for (int n = MAX_HARNAS; n >= 1; n--) if (s.heeft("harnas" + n)) return n;
        return 0;
    }

    static final String[] HARNAS_NAAM = {"Groene tuniek", "Maliënhemd", "Wachtersharnas", "Gloedharnas", "Lichtharnas"};

    /** Hoeveel flesjes je vond ({@code fles1} … {@code fles8}). */
    static int gevonden(ZeldaVoortgang.Stand s) {
        int n = 0;
        for (int i = 1; i <= 8; i++) if (s.heeft("fles" + i)) n++;
        return n;
    }

    /** Hoeveel flesjes je hebt: wat je vond, en (als je er een hebt) de extra van je niveau en je steun ({@link #extraFlessen}). */
    static int flessen(ZeldaVoortgang.Stand s) {
        int n = gevonden(s);
        return n == 0 ? 0 : n + extraFlessen(s);
    }

    /** Hoeveel daarvan vol zijn (teller {@code fles-vol}, nooit meer dan je hebt). */
    static int vol(ZeldaVoortgang.Stand s) {
        return Math.max(0, Math.min(flessen(s), s.teller("fles-vol")));
    }

    /** Alle flesjes vol; true als er iets veranderde. */
    static boolean vulFlessen(ZeldaVoortgang.Stand s) {
        int n = flessen(s);
        if (s.teller("fles-vol") == n) return false;
        s.teller("fles-vol", n);
        return true;
    }

    /** Hoeveel een flesje van deze speler geneest (halve harten). */
    static double flesHp(ZeldaVoortgang.Stand s) {
        return (s.heeft("sterkwater") ? FLES_HP_STERK : FLES_HP) * niveau(s).fles;
    }

    /** Eén flesje leegdrinken; false als je geen vol flesje hebt. */
    static boolean drink(ZeldaVoortgang.Stand s) {
        int v = vol(s);
        if (v <= 0) return false;
        s.teller("fles-vol", v - 1);
        return true;
    }

    /** Het doel waar je nu heen moet: het eerste dat nog niet klaar is (null = alles gedaan). */
    static ZeldaHoofdstuk.Doel doel(List<ZeldaHoofdstuk.Doel> doelen, ZeldaVoortgang.Stand s) {
        for (ZeldaHoofdstuk.Doel d : doelen) if (d.klaar() == null || !d.klaar().test(s)) return d;
        return null;
    }

    /**
     * Het doel waar je nu heen moet, gezien vanaf {@code x, z}: het eerste dat nog niet klaar is; hoort het bij een groep
     * (doelen die je in elke volgorde mag doen), dan het dichtstbijzijnde uit die groep dat nog niet klaar is.
     */
    static ZeldaHoofdstuk.Doel doel(List<ZeldaHoofdstuk.Doel> doelen, ZeldaVoortgang.Stand s, double x, double z) {
        ZeldaHoofdstuk.Doel eerste = doel(doelen, s);
        if (eerste == null || eerste.groep() == null) return eerste;
        ZeldaHoofdstuk.Doel best = eerste;
        double bd = Double.MAX_VALUE;
        for (ZeldaHoofdstuk.Doel d : doelen) {
            if (!eerste.groep().equals(d.groep()) || d.plek() == null || (d.klaar() != null && d.klaar().test(s))) continue;
            double a = (d.plek().getX() - x) * (d.plek().getX() - x) + (d.plek().getZ() - z) * (d.plek().getZ() - z);
            if (a < bd) {
                bd = a;
                best = d;
            }
        }
        return best;
    }

    /** De zijmissies waar je mee bezig bent (stap 1 … n), in volgorde. */
    static List<ZeldaHoofdstuk.Missie> bezig(List<ZeldaHoofdstuk.Missie> missies, ZeldaVoortgang.Stand s) {
        List<ZeldaHoofdstuk.Missie> l = new ArrayList<>();
        for (ZeldaHoofdstuk.Missie m : missies) {
            int st = s.missie(m.id());
            if (st >= 1 && st <= m.stappen().size()) l.add(m);
        }
        return l;
    }

    /** De zin voor de huidige stap van een missie (of null). */
    static String stap(ZeldaHoofdstuk.Missie m, ZeldaVoortgang.Stand s) {
        int st = s.missie(m.id());
        return st >= 1 && st <= m.stappen().size() ? m.stappen().get(st - 1) : null;
    }

    /**
     * «Opnieuw vanaf hier»: de vlaggen die je vergeet als je het deel opnieuw speelt vanaf rustplaats {@code r}: de
     * verhalen ({@code v:}) en puzzels ({@code r:}) die erna komen. Vondsten, harten, missies en tijden blijven.
     */
    static List<String> opnieuw(List<Object> volgorde, ZeldaHoofdstuk.Rust r) {
        return opnieuw(volgorde, r, false);
    }

    /** Idem; {@code totVolgende} (een open eiland): alleen het stuk tot de volgende rustplaats in de volgorde. */
    static List<String> opnieuw(List<Object> volgorde, ZeldaHoofdstuk.Rust r, boolean totVolgende) {
        List<String> weg = new ArrayList<>();
        boolean na = false;
        for (Object o : volgorde) {
            if (o == r) na = true;
            else if (na && totVolgende && o instanceof ZeldaHoofdstuk.Rust) break;
            else if (na && o instanceof ZeldaHoofdstuk.Verhaal v && !v.einde) weg.add("v:" + v.id);
            else if (na && o instanceof ZeldaHoofdstuk.Reeks rk) weg.add("r:" + rk.id);
            else if (na && o instanceof ZeldaHoofdstuk.Spiegels sp) weg.add("r:" + sp.id);
        }
        return weg;
    }

    /**
     * Verplichte kamers vóór de baas: de gevechten op het hoofdpad (niet {@code vrijwillig} of {@code alleenBel}, geen baas) die vóór de
     * eerste baaskamer liggen. Bouwregel: hoogstens twee (de zelftest controleert het).
     */
    static int verplichtVoorBaas(List<Object> volgorde) {
        int n = 0;
        for (Object o : volgorde) {
            if (!(o instanceof ZeldaHoofdstuk.Kamer k)) continue;
            if (k.baas != null) return n;
            if (!k.vrijwillig && !k.alleenBel) n++;
        }
        return n;
    }

    /**
     * Vrije doorgang: wie een kamer al won ({@code k:<id>}) hoeft er niet nog eens doorheen te vechten. Alleen als
     * iedereen die binnen is hem al won (een nieuwe speler erbij = gewoon vechten).
     */
    static boolean doorgang(java.util.Collection<ZeldaVoortgang.Stand> binnen, String kamer) {
        if (binnen.isEmpty()) return false;
        for (ZeldaVoortgang.Stand s : binnen) if (!s.heeft("k:" + kamer)) return false;
        return true;
    }

    /** Hoeveel golven dit gevecht heeft: de gewone, en met de bel ({@code terug}) de extra golven erachter. */
    static int golven(ZeldaHoofdstuk.Kamer k) {
        return k.golven.size() + (k.terug ? k.extra.size() : 0);
    }

    /** Golf {@code i} van dit gevecht (eerst de gewone, dan de extra). */
    static List<ZeldaHoofdstuk.Soort> golf(ZeldaHoofdstuk.Kamer k, int i) {
        return i < k.golven.size() ? k.golven.get(i) : k.extra.get(i - k.golven.size());
    }

    /** Een spiegel een kwartslag verder draaien (met de klok mee: noord → oost → zuid → west). */
    static org.bukkit.block.BlockFace draai(org.bukkit.block.BlockFace f) {
        return switch (f) {
            case NORTH -> org.bukkit.block.BlockFace.EAST;
            case EAST -> org.bukkit.block.BlockFace.SOUTH;
            case SOUTH -> org.bukkit.block.BlockFace.WEST;
            default -> org.bukkit.block.BlockFace.NORTH;
        };
    }

    /** Waar het licht van een spiegelpuzzel langs gaat ({@link #straal}). */
    record Straal(List<int[]> punten, boolean raak) {}

    /**
     * Het licht van een spiegelpuzzel volgen: vanaf {@code bron} in richting {@code r}, blok voor blok. Een spiegel stuurt
     * het verder in zijn richting ({@code stand}), het {@code doel} = raak, een blok dat niet {@code vrij} is = stop.
     * Hoogstens {@code max} blokken (en nooit rondjes). De punten zijn de blokken waar het licht door gaat.
     */
    static Straal straal(int[] bron, org.bukkit.block.BlockFace r, List<int[]> spiegels, List<org.bukkit.block.BlockFace> stand, int[] doel,
                         java.util.function.Predicate<int[]> vrij, int max) {
        List<int[]> punten = new ArrayList<>();
        java.util.Set<String> gezien = new java.util.HashSet<>();
        int x = bron[0], y = bron[1], z = bron[2];
        for (int n = 0; n < max; n++) {
            x += r.getModX();
            y += r.getModY();
            z += r.getModZ();
            int[] c = {x, y, z};
            if (x == doel[0] && y == doel[1] && z == doel[2]) return new Straal(punten, true);
            int i = -1;
            for (int j = 0; j < spiegels.size(); j++) if (spiegels.get(j)[0] == x && spiegels.get(j)[1] == y && spiegels.get(j)[2] == z) i = j;
            if (i >= 0) {
                punten.add(c);
                if (!gezien.add(x + "," + y + "," + z + "," + stand.get(i))) break;
                r = stand.get(i);
                continue;
            }
            if (!vrij.test(c)) break;
            punten.add(c);
        }
        return new Straal(punten, false);
    }

    /**
     * Hoeveel een klap doet bij Kapitein Zoutbaard: zijn jas zit vol zeepokken (een derde), maar versuft (een bom in zijn
     * ton) anderhalf keer zoveel.
     */
    static double zoutbaardSchade(double schade, boolean versuft) {
        return schade * (versuft ? 1.5 : 0.35);
    }

    /**
     * Eb en vloed: in welke volgorde de lagen van een bak omgaan ({@code n} lagen, 0 = de onderste). Naar vloed vult hij
     * van onder naar boven, naar eb zakt het water van boven naar onder.
     */
    static int[] getijVolgorde(int n, boolean naarVloed) {
        int[] v = new int[n];
        for (int i = 0; i < n; i++) v[i] = naarVloed ? i : n - 1 - i;
        return v;
    }

    /**
     * Hoeveel een klap doet bij Grimmel, de reuzenijsbeer van het IJspaleis: zijn vacht is dik (een kwart), maar
     * versuft (hij stormde tegen een ijszuil) iets meer dan gewoon. Zo duurt het gevecht een paar zuilen lang.
     */
    static double grimmelSchade(double schade, boolean versuft) {
        return schade * (versuft ? 1.2 : 0.25);
    }

    /**
     * Hoeveel een klap doet bij Asgrim, de Gloedwachter van de Smidse (eiland 4b): door zijn vlammenschild komt bijna
     * niets heen (een tiende), maar gedoofd (water uit een kuip over hem heen) gewoon alles. Zo duurt het een paar kuipen.
     */
    static double asgrimSchade(double schade, boolean gedoofd) {
        return schade * (gedoofd ? 1.0 : 0.1);
    }

    // ── Morgal (eiland 5b): drie fases ─────────────────────────────────────────────────────

    /** De fases van Morgal: 1 = zijn nevelgeesten, 2 = zijn duistere bollen, 3 = licht in zijn nevelhart; 0 = even adem ertussen. */
    static final int MORGAL_ADEM = 0, MORGAL_GEESTEN = 1, MORGAL_BOLLEN = 2, MORGAL_HART = 3;

    /**
     * Hoeveel een klap doet bij Morgal. Zijn nevel beschermt hem bijna helemaal (fase 1 en tussen de fases); in fase 2 doet een
     * klap alles als hij versuft is (een teruggeslagen bol), anders weinig (een lichtpijl prikt iets meer); in fase 3 komt licht
     * dwars door zijn nevelhart (en versuft doet ook een zwaard alles).
     */
    static double morgalSchade(double schade, int fase, boolean versuft, boolean licht) {
        return schade * switch (fase) {
            case MORGAL_BOLLEN -> versuft ? 1.0 : licht ? 0.25 : 0.12;
            case MORGAL_HART -> licht || versuft ? 1.0 : 0.12;
            default -> 0.02;
        };
    }

    /** Hoe ver je een duistere bol met je zwaard terugslaat (van je ogen), en hoeveel graden opzij hij mag zijn. */
    static final double ZWAAI_BEREIK = 4.5, ZWAAI_HOEK = 80;

    /**
     * Sla je met een zwaai deze bol terug? Hij moet dichtbij zijn ({@code bereik}, vanaf je ogen) en ongeveer voor je
     * ({@code hoek} graden opzij van waar je kijkt). Heel dichtbij (een blok) altijd: kinderen zwaaien niet precies.
     */
    static boolean terugslaan(double[] oog, double[] kijk, double[] bol, double bereik, double hoek) {
        double vx = bol[0] - oog[0], vy = bol[1] - oog[1], vz = bol[2] - oog[2];
        double d = Math.sqrt(vx * vx + vy * vy + vz * vz), kl = Math.sqrt(kijk[0] * kijk[0] + kijk[1] * kijk[1] + kijk[2] * kijk[2]);
        if (d > bereik) return false;
        if (d < 1.2 || kl < 1e-9) return true;
        double cos = (vx * kijk[0] + vy * kijk[1] + vz * kijk[2]) / (d * kl);
        return cos >= Math.cos(Math.toRadians(hoek));
    }

    /**
     * Een trap van licht (eiland 5b): {@code n} treden vanaf (x, y, z) in richting (dx, dz), elke trede een blok verder en een
     * hoger; {@code breed} blokken breed (naar rechts, gezien in de looprichting). Geeft de blokken {x, y, z}.
     */
    static List<int[]> trap(int x, int y, int z, int dx, int dz, int n, int breed) {
        List<int[]> l = new ArrayList<>();
        int rx = -dz, rz = dx;                                              // rechts van de looprichting
        for (int k = 0; k < n; k++)
            for (int b = 0; b < breed; b++) l.add(new int[]{x + dx * k + rx * b, y + k, z + dz * k + rz * b});
        return l;
    }

    /** Een vakje (x, z) van een glijvloer: ijs? vrij om in te staan? een gewone vloer? */
    @FunctionalInterface
    interface Vak {
        boolean is(int x, int z);
    }

    /** Hoe snel je over een glijvloer glijdt (blokken per tik). */
    static final double GLIJ_SNELHEID = 0.45;

    /**
     * Glijden over een glijvloer: vanaf vakje {@code x, z} in richting {@code r}. Is het volgende vakje niet vrij (een rots,
     * een muur), dan stop je hier; is het ijs, dan glijd je verder; is het een gewone vloer, dan stap je erop en sta je
     * stil; anders (een gat) stop je ook: je glijdt nooit een afgrond in. Hoogstens {@code max} vakjes. Geeft het vakje
     * {x, z} waar je uitkomt (hetzelfde vakje = je kunt die kant niet op).
     */
    static int[] glij(int x, int z, org.bukkit.block.BlockFace r, Vak ijs, Vak vrij, Vak vloer, int max) {
        int dx = r.getModX(), dz = r.getModZ();
        for (int n = 0; n < max; n++) {
            int nx = x + dx, nz = z + dz;
            if (!vrij.is(nx, nz)) break;
            if (ijs.is(nx, nz)) {
                x = nx;
                z = nz;
                continue;
            }
            if (vloer.is(nx, nz)) return new int[]{nx, nz};
            break;
        }
        return new int[]{x, z};
    }

    /** De vier richtingen waarin je kunt glijden. */
    static final List<org.bukkit.block.BlockFace> GLIJ_RICHTINGEN = List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST);

    /**
     * De kortste weg over een glijvloer van {@code start} naar {@code doel} (allebei vakjes zonder ijs): vanaf een vakje
     * zonder ijs stap je het ijs op (en glijd je), op het ijs glijd je. Null = niet te halen.
     */
    static List<org.bukkit.block.BlockFace> glijOplossing(int[] start, int[] doel, Vak ijs, Vak vrij, Vak vloer, int max) {
        java.util.Map<Long, Object[]> vorige = new java.util.HashMap<>();
        java.util.ArrayDeque<int[]> rij = new java.util.ArrayDeque<>();
        vorige.put(ZeldaHoofdstuk.Glij.sleutel(start[0], start[1]), new Object[]{null, null});     // de start (geen null: putIfAbsent)
        rij.add(start);
        while (!rij.isEmpty() && vorige.size() < 10_000) {
            int[] c = rij.poll();
            if (c[0] == doel[0] && c[1] == doel[1]) {
                List<org.bukkit.block.BlockFace> weg = new ArrayList<>();
                Object[] v = vorige.get(ZeldaHoofdstuk.Glij.sleutel(c[0], c[1]));
                while (v[0] != null) {
                    weg.add(0, (org.bukkit.block.BlockFace) v[1]);
                    int[] p = (int[]) v[0];
                    v = vorige.get(ZeldaHoofdstuk.Glij.sleutel(p[0], p[1]));
                }
                return weg;
            }
            boolean opIjs = ijs.is(c[0], c[1]);
            for (org.bukkit.block.BlockFace r : GLIJ_RICHTINGEN) {
                if (!opIjs && !(ijs.is(c[0] + r.getModX(), c[1] + r.getModZ()) && vrij.is(c[0] + r.getModX(), c[1] + r.getModZ()))) continue;
                int[] e = glij(c[0], c[1], r, ijs, vrij, vloer, max);
                if (e[0] == c[0] && e[1] == c[1]) continue;
                if (vorige.putIfAbsent(ZeldaHoofdstuk.Glij.sleutel(e[0], e[1]), new Object[]{c, r}) == null) rij.add(e);
            }
        }
        return null;
    }

    /** Hoe ver de klauwhaak reikt (blokken, van je ogen tot het haakpunt). */
    static final double HAAK_BEREIK = 24;
    /** Hoeveel graden je ernaast mag mikken: de klauwhaak vindt het haakpunt dan toch (kinderen mikken niet zo precies). */
    static final double HAAK_HOEK = 6;

    /**
     * Op welk haakpunt mik je? Vanaf {@code oog} in richting {@code kijk} (hoeft niet genormaliseerd): het punt uit
     * {@code punten} (middens van de blokken) binnen {@code bereik} met de kleinste hoek, als die hoogstens {@code hoek}
     * graden is. -1 = geen. (Of er iets tussen zit, kijkt de wereld daarna.)
     */
    static int mik(double[] oog, double[] kijk, List<double[]> punten, double bereik, double hoek) {
        double kl = Math.sqrt(kijk[0] * kijk[0] + kijk[1] * kijk[1] + kijk[2] * kijk[2]);
        if (kl < 1e-9) return -1;
        int best = -1;
        double bh = Math.toRadians(hoek);
        for (int i = 0; i < punten.size(); i++) {
            double[] q = punten.get(i);
            double vx = q[0] - oog[0], vy = q[1] - oog[1], vz = q[2] - oog[2];
            double d = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (d < 0.5 || d > bereik) continue;
            double cos = (vx * kijk[0] + vy * kijk[1] + vz * kijk[2]) / (d * kl);
            double a = Math.acos(Math.max(-1, Math.min(1, cos)));
            if (a <= bh) {
                bh = a;
                best = i;
            }
        }
        return best;
    }

    /** Hoe ver de vuurstaf reikt (blokken), en hoeveel graden ernaast mag (net als de klauwhaak). */
    static final double VUUR_BEREIK = 24, VUUR_HOEK = 6;
    /** Wat een vlam van de vuurstaf doet bij een vijand (halve harten); een vijand van ijs krijgt anderhalf keer zoveel. */
    static final double VUUR_SCHADE = 4;

    /** Vanaf dit harnas deert de hitte je niet meer: het gloedharnas ({@code harnas3}, eiland 4a). */
    static final int GLOEDHARNAS = 3;
    /** Zo snel koel je af (per seconde) in water, bij een zielvuur of een bron, en buiten de hitte. */
    static final double AFKOELEN = 0.6;

    /** Deert de hitte je niet meer (het gloedharnas, of beter)? */
    static boolean hittevast(ZeldaVoortgang.Stand s) {
        return harnas(s) >= GLOEDHARNAS;
    }

    /**
     * De hitte van een speler (0 … 1) na {@code dt} seconden. Buiten de hitte, of met het gloedharnas, koel je af. In de
     * hitte word je warm: {@code snel} per seconde, met de koelsteen {@code koel}; in water, bij een zielvuur of in een
     * bron ({@code koelplek}) koel je af. In de felle gloed ({@code fel}) helpen de koelsteen en de koelplekken niet.
     */
    static double hitte(double oud, double dt, boolean inHitte, double snel, double koel, boolean fel, boolean koelsteen, boolean hittevast, boolean koelplek) {
        double d;
        if (!inHitte || hittevast || (koelplek && !fel)) d = -AFKOELEN * dt;
        else d = (fel || !koelsteen ? snel : koel) * dt;
        return Math.max(0, Math.min(1, oud + d));
    }

    // ── sluipen (eiland 5a): wachters met een lantaarn ──────────────────────────────────────

    /** Waar een wachter staat en waar hij heen kijkt (yaw zoals in Minecraft: 0 = zuid, 90 = west, 180 = noord, −90 = oost). */
    record Pose(double x, double y, double z, double yaw) {}

    /**
     * De stand van een wachter op {@code f} (0 … 1) van zijn ritme: tussen twee standen ({fractie, x, y, z, yaw}, oplopend)
     * loopt en draait hij gelijkmatig (de kortste kant op); na de laatste gaat hij terug naar de eerste.
     */
    static Pose pose(double[][] standen, double f) {
        f = f - Math.floor(f);
        if (standen.length == 1) return new Pose(standen[0][1], standen[0][2], standen[0][3], standen[0][4]);
        int n = standen.length;
        for (int i = 0; i < n; i++) {
            double[] a = standen[i], b = standen[(i + 1) % n];
            double fa = a[0], fb = i + 1 < n ? b[0] : b[0] + 1;
            double ff = f < standen[0][0] ? f + 1 : f;
            if (ff < fa || ff > fb) continue;
            double t = fb - fa <= 1e-9 ? 0 : (ff - fa) / (fb - fa);
            double dy = ((b[4] - a[4]) % 360 + 540) % 360 - 180;
            return new Pose(a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, a[3] + (b[3] - a[3]) * t, a[4] + dy * t);
        }
        return new Pose(standen[0][1], standen[0][2], standen[0][3], standen[0][4]);
    }

    /**
     * Valt punt (x, y, z) in de lichtkegel van een wachter: hoogstens {@code bereik} blokken (gemeten over de grond), niet
     * meer dan drie blokken hoger of lager, en hoogstens {@code hoek} graden opzij van waar hij heen kijkt? (Of er iets
     * tussen zit, kijkt de wereld daarna.) Vlak bij hem (een blok) ziet hij je altijd.
     */
    static boolean inKegel(Pose w, double bereik, double hoek, double x, double y, double z) {
        double dx = x - w.x(), dz = z - w.z(), d = Math.sqrt(dx * dx + dz * dz);
        if (Math.abs(y - w.y()) > 3 || d > bereik) return false;
        if (d < 1.0) return true;
        double kx = -Math.sin(Math.toRadians(w.yaw())), kz = Math.cos(Math.toRadians(w.yaw()));
        double cos = (dx * kx + dz * kz) / d;
        return cos >= Math.cos(Math.toRadians(hoek));
    }

    /**
     * Kun je over een stuk pad sluipen? {@code gezien[t][c]} = cel {@code c} wordt gezien op tijdstap {@code t} (het ritme
     * loopt rond: t modulo het aantal tijdstappen). Je begint op tijdstap {@code start} op cel {@code van} en zet elke
     * {@code stap} tijdstappen een cel verder tot je op {@code tot} staat. Een cel mag ook een tijdstap ervoor en erna niet
     * gezien worden (speling: zo haalt een kind het ook).
     */
    static boolean sluipt(boolean[][] gezien, int van, int tot, int start, int stap) {
        int n = gezien.length, r = tot >= van ? 1 : -1;
        for (int k = 1; k <= Math.abs(tot - van); k++) {
            int c = van + r * k, t = start + k * stap;
            for (int s = -1; s <= 1; s++) if (gezien[Math.floorMod(t + s, n)][c]) return false;
        }
        return true;
    }

    /** Op hoeveel van de tijdstappen kun je dit stuk pad beginnen zonder gezien te worden (0 = het kan nooit)? */
    static int vensters(boolean[][] gezien, int van, int tot, int stap) {
        int v = 0;
        for (int t = 0; t < gezien.length; t++) if (sluipt(gezien, van, tot, t, stap)) v++;
        return v;
    }

    /**
     * De cellen van een pad: van punt naar punt in rechte stukken (langs x of langs z), elk blok één cel; {@code index[i]} =
     * de cel van punt {@code i}. Null als een stuk niet recht is.
     */
    static List<int[]> cellen(List<int[]> punten, int[] index) {
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i < punten.size(); i++) {
            int[] p = punten.get(i);
            if (i == 0) {
                l.add(p.clone());
                if (index != null) index[0] = 0;
                continue;
            }
            int[] v = punten.get(i - 1);
            if (v[0] != p[0] && v[2] != p[2]) return null;
            int dx = Integer.signum(p[0] - v[0]), dz = Integer.signum(p[2] - v[2]), n = Math.max(Math.abs(p[0] - v[0]), Math.abs(p[2] - v[2]));
            for (int k = 1; k <= n; k++) l.add(new int[]{v[0] + dx * k, v[1] + (k == n ? p[1] - v[1] : 0), v[2] + dz * k});
            if (index != null) index[i] = l.size() - 1;
        }
        return l;
    }

    /** Een eilandnaam midden in een zin: «Het Lindewoud» → «het Lindewoud». */
    static String inZin(String naam) {
        return naam.startsWith("Het ") ? "het " + naam.substring(4) : naam.startsWith("De ") ? "de " + naam.substring(3) : naam;
    }

    /** {@code "h1:3"} → {@code {"h1", "3"}}; null bij onzin. */
    static String[] plek(String laatste) {
        if (laatste == null) return null;
        int i = laatste.lastIndexOf(':');
        if (i <= 0 || i == laatste.length() - 1) return null;
        try {
            if (Integer.parseInt(laatste.substring(i + 1)) < 0) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        return new String[]{laatste.substring(0, i), laatste.substring(i + 1)};
    }

    // ── niveaus: alleen de steun voor de speler verschilt, nooit wat je moet doen ──────────────────────

    /**
     * Een niveau (Jelle 28-10: "niet sneller door een arena, wel sneller helen, meer harnas, extra flesjes"). Vijanden,
     * golven, bazen en puzzels zijn op elk niveau hetzelfde; dit is alleen de hulp. {@link #AVONTUUR} is de balans van 2.15
     * (oude standen en elk nieuw spel beginnen daar).
     *
     * @param schade       factor op wat vijanden doen (bovenop {@link #SCHADE})
     * @param schild       gouden harten (een schildje van licht) bij het begin van elk gevecht
     * @param na           op adem komen buiten een gevecht: zo lang na de laatste klap (ms) …
     * @param elk          … elke zoveel ms een half hart
     * @param gevechtNa    op adem komen ín een gevecht (0 = nooit)
     * @param gevechtElk   idem, elke zoveel ms
     * @param flessen      extra flesjes (als je er een hebt)
     * @param fles         factor op wat een flesje geneest
     * @param hartKans     kans op een hartje als je een vijand verslaat
     * @param pauze        adem tussen twee golven (ms)
     * @param hulp         gouden harten per keer dat je in hetzelfde gevecht doodging (hoogstens {@link #HULP_KEER} keer)
     */
    enum Niveau {
        VERHAAL("Verhaal", 0.5, 4, 3_000, 1_000, 5_000, 3_000, 2, 1.2, 0.75, 6_000, 2, "Wiske helpt veel", "voor wie vooral wil ontdekken"),
        AVONTUUR("Avontuur", 1.0, 0, 6_000, 2_000, 0, 0, 0, 1.0, 0.5, 4_000, 2, "zoals het spel bedoeld is", "voor iedereen"),
        HELD("Held", 1.35, 0, 8_000, 3_000, 0, 0, 0, 1.0, 1.0 / 3, 4_000, 1, "vijanden slaan harder", "langzamer op adem"),
        LEGENDE("Legende", 1.75, 0, 12_000, 4_000, 0, 0, 0, 0.8, 0.2, 4_000, 0, "voor wie het al kent", "bijna geen hulp");

        final String naam;
        final double schade, fles, hartKans;
        final int schild, flessen, hulp;
        final long na, elk, gevechtNa, gevechtElk, pauze;
        final String[] uitleg;

        Niveau(String naam, double schade, int schild, long na, long elk, long gevechtNa, long gevechtElk, int flessen, double fles,
               double hartKans, long pauze, int hulp, String... uitleg) {
            this.naam = naam;
            this.schade = schade;
            this.schild = schild;
            this.na = na;
            this.elk = elk;
            this.gevechtNa = gevechtNa;
            this.gevechtElk = gevechtElk;
            this.flessen = flessen;
            this.fles = fles;
            this.hartKans = hartKans;
            this.pauze = pauze;
            this.hulp = hulp;
            this.uitleg = uitleg;
        }

        /** Op naam (hoofdletters maken niet uit, het begin mag), of null. */
        static Niveau van(String naam) {
            if (naam == null || naam.isEmpty()) return null;
            for (Niveau n : values()) if (n.naam.toLowerCase(java.util.Locale.ROOT).startsWith(naam.toLowerCase(java.util.Locale.ROOT))) return n;
            return null;
        }
    }

    /** Hoe vaak doodgaan in hetzelfde gevecht hoogstens meetelt voor de hulp. */
    static final int HULP_KEER = 3;
    /** Bijsturen door beheer ({@code steun}): hoogstens zoveel stappen, elk 15 % schade minder (of meer). */
    static final int STEUN_MAX = 2;
    static final double STEUN_STAP = 0.15;

    /** Het niveau van dit spel ({@link Niveau#AVONTUUR} als het iets vreemds is). */
    static Niveau niveau(ZeldaVoortgang.Stand s) {
        return s.niveau >= 0 && s.niveau < Niveau.values().length ? Niveau.values()[s.niveau] : Niveau.AVONTUUR;
    }

    static int steun(ZeldaVoortgang.Stand s) {
        return Math.max(-STEUN_MAX, Math.min(STEUN_MAX, s.steun));
    }

    /** Wat een klap van een vijand bij deze speler doet, als factor (balans × niveau × steun). */
    static double schade(ZeldaVoortgang.Stand s) {
        return SCHADE * niveau(s).schade * (1 - STEUN_STAP * steun(s));
    }

    /** Extra flesjes: van je niveau, en één per stap steun omhoog. */
    static int extraFlessen(ZeldaVoortgang.Stand s) {
        return niveau(s).flessen + Math.max(0, steun(s));
    }

    /** Gouden harten bij het begin van een gevecht: het schildje van je niveau, en hulp als je er al doodging ({@code doden}). */
    static int schildje(ZeldaVoortgang.Stand s, int doden) {
        Niveau n = niveau(s);
        return n.schild + n.hulp * Math.max(0, Math.min(HULP_KEER, doden));
    }

    /** Op adem komen: {na, elk} in ms, of null als het niet mag (in een gevecht, behalve op Verhaal). */
    static long[] bijkomen(ZeldaVoortgang.Stand s, boolean gevecht) {
        Niveau n = niveau(s);
        if (!gevecht) return new long[]{n.na, n.elk};
        return n.gevechtNa > 0 ? new long[]{n.gevechtNa, n.gevechtElk} : null;
    }

    /** De adem tussen twee golven: die van het niveau met de meeste hulp in de kamer. */
    static long golfPauze(java.util.Collection<ZeldaVoortgang.Stand> binnen) {
        long p = GOLF_PAUZE;
        for (ZeldaVoortgang.Stand s : binnen) p = Math.max(p, niveau(s).pauze);
        return p;
    }

    // ── samen: per extra speler net iets zwaarder ─────────────────────────────────────────────────────

    /** Hoeveel vijanden een golf hoogstens heeft (ook samen). */
    static final int GOLF_MAX = 8;

    /**
     * Een golf voor {@code spelers} spelers: per extra speler één vijand erbij (de soorten van de golf om de beurt; voor een
     * baas zijn helper), hoogstens {@link #GOLF_MAX}.
     */
    static List<ZeldaHoofdstuk.Soort> samen(List<ZeldaHoofdstuk.Soort> golf, int spelers, ZeldaHoofdstuk.Soort baas, ZeldaHoofdstuk.Soort helper) {
        List<ZeldaHoofdstuk.Soort> l = new ArrayList<>(golf);
        for (int i = 1; i < spelers && l.size() < GOLF_MAX && !golf.isEmpty(); i++) {
            ZeldaHoofdstuk.Soort s = golf.get((i - 1) % golf.size());
            l.add(baas != null && s == baas ? helper : s);
        }
        return l;
    }

    /** Het leven van een baas: zijn leven, per extra speler meer, en de balans ({@link #BAAS_LEVEN}). */
    static double baasLeven(double leven, double perSpeler, int spelers) {
        return (leven + perSpeler * Math.max(0, spelers - 1)) * BAAS_LEVEN;
    }

    /** Roept een baas hulp, dan met drie of meer spelers één helper extra. */
    static int extraHelpers(int spelers) {
        return spelers > 2 ? 1 : 0;
    }

    // ── sterren en de heldenrand ──────────────────────────────────────────────────────────────────────

    /**
     * Een gevecht gewonnen op {@code niveau} (het laagste dat je tijdens het gevecht had): de ster van die kamer (teller
     * {@code ster:<kamer>} = niveau + 1) gaat omhoog als dit beter is. True als het een nieuwe beste is.
     */
    static boolean ster(ZeldaVoortgang.Stand s, String kamer, Niveau niveau) {
        int oud = s.teller("ster:" + kamer), nieuw = niveau.ordinal() + 1;
        if (nieuw <= oud) return false;
        s.teller("ster:" + kamer, nieuw);
        return true;
    }

    /** Het beste niveau waarop je deze kamer won (null = nog niet gewonnen sinds 2.16). */
    static Niveau ster(ZeldaVoortgang.Stand s, String kamer) {
        int n = s.teller("ster:" + kamer);
        return n >= 1 && n <= Niveau.values().length ? Niveau.values()[n - 1] : null;
    }

    /** Hoeveel van deze kamers je won op {@code minstens} of hoger. */
    static int sterren(ZeldaVoortgang.Stand s, java.util.Collection<String> kamers, Niveau minstens) {
        int n = 0;
        for (String k : kamers) if (s.teller("ster:" + k) >= minstens.ordinal() + 1) n++;
        return n;
    }

    /**
     * De heldenrand: alle bazen gewonnen op Held of hoger = {@link Niveau#HELD} (een gouden rand op je harnas), allemaal op
     * Legende = {@link Niveau#LEGENDE} (paars); anders null.
     */
    static Niveau heldenrand(ZeldaVoortgang.Stand s, java.util.Collection<String> bazen) {
        if (bazen.isEmpty()) return null;
        int laagste = Integer.MAX_VALUE;
        for (String b : bazen) laagste = Math.min(laagste, s.teller("ster:" + b));
        return laagste >= Niveau.LEGENDE.ordinal() + 1 ? Niveau.LEGENDE : laagste >= Niveau.HELD.ordinal() + 1 ? Niveau.HELD : null;
    }
}
