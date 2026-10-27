package nl.burggraaf.mc.kitpvp;

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
    /** Hoogste harnas. */
    static final int MAX_HARNAS = 4;
    /** Hoeveel een vol flesje geneest (halve harten). */
    static final double FLES_HP = 10;

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

    /** Hoeveel flesjes je hebt ({@code fles1} … {@code fles4}). */
    static int flessen(ZeldaVoortgang.Stand s) {
        int n = 0;
        for (int i = 1; i <= 8; i++) if (s.heeft("fles" + i)) n++;
        return n;
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
        List<String> weg = new ArrayList<>();
        boolean na = false;
        for (Object o : volgorde) {
            if (o == r) na = true;
            else if (na && o instanceof ZeldaHoofdstuk.Verhaal v && !v.einde) weg.add("v:" + v.id);
            else if (na && o instanceof ZeldaHoofdstuk.Reeks rk) weg.add("r:" + rk.id);
            else if (na && o instanceof ZeldaHoofdstuk.Spiegels sp) weg.add("r:" + sp.id);
        }
        return weg;
    }

    /**
     * Verplichte kamers vóór de baas: de gevechten op het hoofdpad (niet {@code vrijwillig}, geen baas) die vóór de
     * eerste baaskamer liggen. Bouwregel: hoogstens twee (de zelftest controleert het).
     */
    static int verplichtVoorBaas(List<Object> volgorde) {
        int n = 0;
        for (Object o : volgorde) {
            if (!(o instanceof ZeldaHoofdstuk.Kamer k)) continue;
            if (k.baas != null) return n;
            if (!k.vrijwillig) n++;
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
}
