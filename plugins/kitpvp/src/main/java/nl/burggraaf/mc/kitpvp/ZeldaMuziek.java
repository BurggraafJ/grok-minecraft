package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Muziek voor Story of Zelda, met nootblokgeluiden (categorie «Nootblokken», dus spelers kunnen hem zachter zetten).
 * Een {@link Lied} is een paar sporen in een eenvoudig schrift: één teken per stap, {@code .} = rust, {@code d4} of
 * {@code f#5} = een noot (bereik f#3 … f#5, zoals een nootblok), {@code a4+c#5} = akkoord, {@code x} = slag
 * (percussie), {@code |} = maatstreep (alleen voor het lezen). Per speler loopt hoogstens één lus (gevecht, baas) en
 * daarbovenop losse deuntjes (rustplaats, vondst, gewonnen); tijdens een deuntje speelt de lus zachter.
 */
final class ZeldaMuziek {

    enum Instr { HARP, BASS, BELL, FLUTE, CHIME, XYLO, PLING, GUITAR, DIDGE, BIT, KICK, SNARE, HAT, IRON, BANJO }

    record Spoor(Instr instr, float volume, int[][] stappen) {}

    record Lied(String id, int tik, boolean lus, int lengte, List<Spoor> sporen) {}

    /** Noot → stap boven f#3 (0 … 24). */
    static int noot(String s) {
        if (s.equals("x")) return 12;
        if (s.length() < 2) throw new IllegalArgumentException("noot? " + s);
        int pc = switch (s.charAt(0)) {
            case 'c' -> 0;
            case 'd' -> 2;
            case 'e' -> 4;
            case 'f' -> 5;
            case 'g' -> 7;
            case 'a' -> 9;
            case 'b' -> 11;
            default -> throw new IllegalArgumentException("noot? " + s);
        };
        int i = 1;
        if (s.charAt(1) == '#') {
            pc++;
            i++;
        }
        int oct;
        try {
            oct = Integer.parseInt(s.substring(i));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("noot? " + s);
        }
        int n = 12 * (oct + 1) + pc - 54;                       // f#3 = midi 54
        if (n < 0 || n > 24) throw new IllegalArgumentException("buiten bereik (f#3 … f#5): " + s);
        return n;
    }

    static Spoor spoor(Instr instr, float volume, String noten) {
        List<int[]> stappen = new ArrayList<>();
        for (String t : noten.trim().split("\\s+")) {
            if (t.equals("|")) continue;
            if (t.equals(".")) {
                stappen.add(null);
                continue;
            }
            String[] ak = t.split("\\+");
            int[] n = new int[ak.length];
            for (int i = 0; i < ak.length; i++) n[i] = noot(ak[i]);
            stappen.add(n);
        }
        return new Spoor(instr, volume, stappen.toArray(new int[0][]));
    }

    static Lied lied(String id, int tik, boolean lus, Spoor... sporen) {
        int lengte = 0;
        for (Spoor s : sporen) lengte = Math.max(lengte, s.stappen().length);
        return new Lied(id, tik, lus, lengte, List.of(sporen));
    }

    /** Toonhoogte voor een nootblok: 0.5 … 2.0. */
    static float toon(int n) {
        return (float) Math.pow(2.0, (n - 12) / 12.0);
    }

    // ── de liedjes (eigen motieven in d) ────────────────────────────────────────────────────

    /** Gevecht achter dichte deuren: dreigend, in d-klein. */
    static final Lied GEVECHT = lied("gevecht", 3, true,
            spoor(Instr.BASS, 0.8f, "d4 . d4 d4 . d4 . d4 | a#3 . a#3 a#3 . a#3 . a#3 | c4 . c4 c4 . c4 . c4 | a3 . a3 a3 . c#4 . e4"),
            spoor(Instr.KICK, 0.6f, "x . . . x . . . | x . . . x . x . | x . . . x . . . | x . x . x . x x"),
            spoor(Instr.SNARE, 0.4f, ". . x . . . x . | . . x . . . x . | . . x . . . x . | . . x . . x x x"),
            spoor(Instr.HAT, 0.2f, "f#5 . f#5 . f#5 . f#5 . | f#5 . f#5 . f#5 . f#5 . | f#5 . f#5 . f#5 . f#5 . | f#5 . f#5 . f#5 . f#5 ."),
            spoor(Instr.HARP, 0.5f, "d5 . . a4 . . d5 e5 | f5 . e5 . d5 . c5 . | c5 . . g4 . . c5 d5 | e5 . c#5 . a4 . . ."));

    /** De baas: sneller, met een brommende grondtoon. */
    static final Lied BAAS = lied("baas", 2, true,
            spoor(Instr.DIDGE, 0.55f, "d4 . . . . . . . | d#4 . . . . . . . | d4 . . . . . . . | c#4 . . . . . . ."),
            spoor(Instr.BASS, 0.85f, "d4 d4 . d4 a3 . d4 . | d#4 d#4 . d#4 a#3 . d#4 . | d4 d4 . d4 a3 . d4 . | c#4 . e4 . g4 . a#4 ."),
            spoor(Instr.KICK, 0.6f, "x . . x x . . . | x . . x x . . . | x . . x x . . . | x . x . x x x x"),
            spoor(Instr.SNARE, 0.4f, ". . x . . . x . | . . x . . . x . | . . x . . . x . | . x . x . x x x"),
            spoor(Instr.HARP, 0.4f, "d5 a4 f4 a4 d5 a4 f4 a4 | d#5 a#4 g4 a#4 d#5 a#4 g4 a#4 | d5 a4 f4 a4 d5 a4 f4 a4 | c#5 a4 e4 a4 c#5 e5 g4 a#4"),
            spoor(Instr.BELL, 0.3f, "a4 . . . . . . . | a#4 . . . . . . . | a4 . . . . . . . | g4 . . . e4 . . ."));

    /** Deuren dicht: een korte schrik. */
    static final Lied DICHT = lied("dicht", 2, false,
            spoor(Instr.BASS, 0.9f, "d4 . d4 . . . . ."),
            spoor(Instr.KICK, 0.7f, "x . x . . . . ."),
            spoor(Instr.BELL, 0.35f, "a4+d#5 . . . . . . ."));

    /** Kamer gewonnen, deuren open. */
    static final Lied GEWONNEN = lied("gewonnen", 2, false,
            spoor(Instr.HARP, 0.7f, "d4 f#4 a4 d5 . . a4 d5 | f#5 . . . . . . ."),
            spoor(Instr.BELL, 0.5f, ". . . . . . . . | d5+f#5 . . . . . . ."),
            spoor(Instr.BASS, 0.7f, "d4 . . . . . . . | d4 . . . . . . ."),
            spoor(Instr.CHIME, 0.4f, ". . . . . . . . | f#5 . . . . . . ."));

    /** Iets gevonden (zwaard, schild, boog, hart). */
    static final Lied VONDST = lied("vondst", 3, false,
            spoor(Instr.HARP, 0.65f, "a3 . c#4 . e4 . a4 . | . . . . . . . ."),
            spoor(Instr.BELL, 0.5f, ". . . . . . . . | a4+c#5+e5 . . . . . . ."),
            spoor(Instr.FLUTE, 0.45f, ". . . . . . . . | e5 . . . . . . ."),
            spoor(Instr.CHIME, 0.35f, ". . . . . . . . | a4 . . . . . . ."));

    /** Rustplaats bereikt. */
    static final Lied RUST = lied("rust", 3, false,
            spoor(Instr.HARP, 0.55f, "d4 a4 f#4 d5 . . . . . . . ."),
            spoor(Instr.CHIME, 0.3f, ". . . . d5 . . . a4 . . ."),
            spoor(Instr.FLUTE, 0.3f, ". . . . . . f#4 . . . . ."));

    /** Puzzel opgelost / uitdaging gehaald. */
    static final Lied OPGELOST = lied("opgelost", 2, false,
            spoor(Instr.BELL, 0.55f, "c#5 . a#4 . f#4 . a#4 . | c#5 . f#5 . . . . ."),
            spoor(Instr.CHIME, 0.4f, ". . . . . . . . | . . f#5 . . . . ."),
            spoor(Instr.HARP, 0.4f, "f#3 . . . c#4 . . . | f#4 . . . . . . ."));

    /** Te laat. */
    static final Lied MISLUKT = lied("mislukt", 3, false,
            spoor(Instr.BASS, 0.8f, "a3 . . g#3 . . g3 . . . . ."),
            spoor(Instr.HARP, 0.3f, "c5 . . b4 . . a#4 . . . . ."));

    /** Binnenkomen in het avontuur. */
    static final Lied BEGIN = lied("begin", 4, false,
            spoor(Instr.FLUTE, 0.45f, "f#4 . a4 . b4 . . . | a4 . f#4 . e4 . . . | d4 . e4 . f#4 . a4 . | f#4 . . . . . . ."),
            spoor(Instr.HARP, 0.35f, "d4+f#4 . . . . . . . | a3+c#4 . . . . . . . | b3+d4 . . . . . . . | d4+a4 . . . . . . ."));

    /** Zacht motief in het bos, af en toe. */
    static final Lied WOUD = lied("woud", 5, false,
            spoor(Instr.FLUTE, 0.2f, "a4 . . f#4 . . e4 . | d4 . . . . . . ."),
            spoor(Instr.HARP, 0.18f, "d4 . . . . . . . | b3 . . . . . . ."));

    /** Hoofdstuk voltooid. */
    static final Lied KLAAR = lied("klaar", 3, false,
            spoor(Instr.HARP, 0.6f, "d4 f#4 a4 d5 a4 f#4 a4 d5 | e4 g4 b4 e5 b4 g4 b4 e5 | f#4 a4 c#5 f#5 c#5 a4 c#5 e5 | d5 . . . . . . ."),
            spoor(Instr.BELL, 0.45f, "d5 . . . . . . . | e5 . . . . . . . | f#5 . . . . . . . | d5+f#5 . . . . . . ."),
            spoor(Instr.BASS, 0.7f, "d4 . . . . . . . | e4 . . . . . . . | f#4 . . . . . . . | d4 . . . . . . ."),
            spoor(Instr.FLUTE, 0.4f, ". . . . . . . . | . . . . . . . . | . . . . . . . . | a4 . . . . . . ."));

    static List<Lied> alle() {
        return List.of(GEVECHT, BAAS, DICHT, GEWONNEN, VONDST, RUST, OPGELOST, MISLUKT, BEGIN, WOUD, KLAAR);
    }

    // ── afspelen ────────────────────────────────────────────────────────────────────────────

    private static final class Afspeel {
        final Lied lied;
        int stap, wacht;

        Afspeel(Lied lied) {
            this.lied = lied;
        }
    }

    private static final class Speler {
        Afspeel lus;
        final List<Afspeel> los = new ArrayList<>();
    }

    private final Map<UUID, Speler> spelers = new HashMap<>();

    /** Zet de lus van deze speler (null = stil). Dezelfde lus loopt gewoon door. */
    void lus(Player p, Lied l) {
        Speler s = spelers.computeIfAbsent(p.getUniqueId(), k -> new Speler());
        if (l == null) s.lus = null;
        else if (s.lus == null || s.lus.lied != l) s.lus = new Afspeel(l);
    }

    Lied lus(Player p) {
        Speler s = spelers.get(p.getUniqueId());
        return s == null || s.lus == null ? null : s.lus.lied;
    }

    /** Een los deuntje erbovenop. */
    void speel(Player p, Lied l) {
        spelers.computeIfAbsent(p.getUniqueId(), k -> new Speler()).los.add(new Afspeel(l));
    }

    boolean speelt(Player p) {
        Speler s = spelers.get(p.getUniqueId());
        return s != null && (s.lus != null || !s.los.isEmpty());
    }

    void vergeet(UUID id) {
        spelers.remove(id);
    }

    /** Elke servertik. */
    void tik() {
        for (Iterator<Map.Entry<UUID, Speler>> it = spelers.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Speler s = e.getValue();
            if (p == null) {
                it.remove();
                continue;
            }
            float demp = s.los.isEmpty() ? 1f : 0.35f;
            if (s.lus != null) stap(p, s.lus, demp);
            s.los.removeIf(a -> !stap(p, a, 1f));
            if (s.lus == null && s.los.isEmpty()) it.remove();
        }
    }

    /** @return false als het (losse) deuntje klaar is */
    private static boolean stap(Player p, Afspeel a, float demp) {
        if (a.wacht > 0) {
            a.wacht--;
            return true;
        }
        Lied l = a.lied;
        for (Spoor sp : l.sporen()) {
            if (a.stap >= sp.stappen().length) continue;
            int[] n = sp.stappen()[a.stap];
            if (n == null) continue;
            Sound geluid = geluid(sp.instr());
            for (int x : n) p.playSound(p, geluid, SoundCategory.RECORDS, sp.volume() * demp, toon(x));
        }
        a.wacht = l.tik() - 1;
        if (++a.stap >= l.lengte()) {
            if (!l.lus()) return false;
            a.stap = 0;
        }
        return true;
    }

    static Sound geluid(Instr i) {
        return switch (i) {
            case HARP -> Sound.BLOCK_NOTE_BLOCK_HARP;
            case BASS -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case BELL -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case FLUTE -> Sound.BLOCK_NOTE_BLOCK_FLUTE;
            case CHIME -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case XYLO -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case PLING -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case GUITAR -> Sound.BLOCK_NOTE_BLOCK_GUITAR;
            case DIDGE -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            case BIT -> Sound.BLOCK_NOTE_BLOCK_BIT;
            case KICK -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
            case SNARE -> Sound.BLOCK_NOTE_BLOCK_SNARE;
            case HAT -> Sound.BLOCK_NOTE_BLOCK_HAT;
            case IRON -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case BANJO -> Sound.BLOCK_NOTE_BLOCK_BANJO;
        };
    }
}
