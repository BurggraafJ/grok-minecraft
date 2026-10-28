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
 * (percussie), {@code |} = maatstreep (alleen voor het lezen).
 *
 * <p>Per speler drie lagen: de <b>achtergrond</b> (altijd, per streek: thuis, woud, tempel, veen, haven, zee; wisselt op
 * een maatstreep), daarboven hoogstens één <b>lus</b> (gevecht, baas: de achtergrond zwijgt dan) en losse
 * <b>deuntjes</b> (rustplaats, vondst, gewonnen; de rest speelt dan zachter). Zo is het in Zelda nooit stil.
 */
final class ZeldaMuziek {

    enum Instr { HARP, BASS, BELL, FLUTE, CHIME, XYLO, PLING, GUITAR, DIDGE, BIT, KICK, SNARE, HAT, IRON, BANJO }

    /** {@code maat}: stappen tot de eerste maatstreep ({@code |}), anders 8. */
    record Spoor(Instr instr, float volume, int[][] stappen, int maat) {}

    record Lied(String id, int tik, boolean lus, int lengte, int maat, List<Spoor> sporen) {}

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
        int maat = 0;
        for (String t : noten.trim().split("\\s+")) {
            if (t.equals("|")) {
                if (maat == 0) maat = stappen.size();
                continue;
            }
            if (t.equals(".")) {
                stappen.add(null);
                continue;
            }
            String[] ak = t.split("\\+");
            int[] n = new int[ak.length];
            for (int i = 0; i < ak.length; i++) n[i] = noot(ak[i]);
            stappen.add(n);
        }
        return new Spoor(instr, volume, stappen.toArray(new int[0][]), maat > 0 ? maat : 8);
    }

    static Lied lied(String id, int tik, boolean lus, Spoor... sporen) {
        int lengte = 0;
        for (Spoor s : sporen) lengte = Math.max(lengte, s.stappen().length);
        return new Lied(id, tik, lus, lengte, sporen.length == 0 ? 8 : sporen[0].maat(), List.of(sporen));
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

    /** Hoofdstuk voltooid. */
    static final Lied KLAAR = lied("klaar", 3, false,
            spoor(Instr.HARP, 0.6f, "d4 f#4 a4 d5 a4 f#4 a4 d5 | e4 g4 b4 e5 b4 g4 b4 e5 | f#4 a4 c#5 f#5 c#5 a4 c#5 e5 | d5 . . . . . . ."),
            spoor(Instr.BELL, 0.45f, "d5 . . . . . . . | e5 . . . . . . . | f#5 . . . . . . . | d5+f#5 . . . . . . ."),
            spoor(Instr.BASS, 0.7f, "d4 . . . . . . . | e4 . . . . . . . | f#4 . . . . . . . | d4 . . . . . . ."),
            spoor(Instr.FLUTE, 0.4f, ". . . . . . . . | . . . . . . . . | . . . . . . . . | a4 . . . . . . ."));


    // ── achtergrond: altijd één van deze (per streek); rustig, lang, zacht ─────────────────────

    /** Thuis aan de bosrand: rustig, in D. */
    static final Lied THUIS = lied("thuis", 5, true,
            spoor(Instr.FLUTE, 0.26f, "a4 . . . f#4 . a4 . | b4 . . . . . a4 . | f#4 . . . d4 . e4 . | e4 . . . . . . . "
                    + "| a4 . . . f#4 . a4 . | d5 . . . b4 . . . | c#5 . . . e4 . c#5 . | d5 . . . . . . . "
                    + "| b4 . . . d5 . . . | c#5 . . . a4 . . . | a4 . . . f#4 . . . | b4 . . . . . . . "
                    + "| g4 . . . b4 . . . | a4 . . . c#5 . e5 . | d5 . . . a4 . f#4 . | d4 . . . . . . ."),
            spoor(Instr.HARP, 0.16f, "d4 . a4 . f#4 . a4 . | g3 . d4 . b3 . d4 . | b3 . f#4 . d4 . f#4 . | a3 . e4 . c#4 . e4 . "
                    + "| d4 . a4 . f#4 . a4 . | g3 . d4 . b3 . d4 . | a3 . e4 . c#4 . e4 . | d4 . a4 . f#4 . a4 . "
                    + "| g3 . d4 . b3 . d4 . | a3 . e4 . c#4 . e4 . | f#3 . c#4 . a3 . c#4 . | b3 . f#4 . d4 . f#4 . "
                    + "| g3 . d4 . b3 . d4 . | a3 . e4 . c#4 . e4 . | d4 . a4 . f#4 . a4 . | d4 . a4 . f#4 . d4 ."),
            spoor(Instr.BASS, 0.2f, "d4 . . . a3 . . . | g3 . . . d4 . . . | b3 . . . f#3 . . . | a3 . . . e4 . . . "
                    + "| d4 . . . a3 . . . | g3 . . . d4 . . . | a3 . . . e4 . . . | d4 . . . a3 . . . "
                    + "| g3 . . . d4 . . . | a3 . . . e4 . . . | f#3 . . . c#4 . . . | b3 . . . f#3 . . . "
                    + "| g3 . . . d4 . . . | a3 . . . e4 . . . | d4 . . . a3 . . . | d4 . . . . . . ."),
            spoor(Instr.CHIME, 0.12f, ". . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . e5 . "
                    + "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . a4 . "
                    + "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . f#5 . "
                    + "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . d5 ."));

    /** Het woud: een beekje van harp, de fluit erboven, in b-klein. */
    static final Lied WOUD = lied("woud", 4, true,
            spoor(Instr.HARP, 0.13f, "b3 d4 f#4 b4 f#4 d4 b3 d4 | g3 b3 d4 g4 d4 b3 g3 b3 | d4 f#4 a4 d5 a4 f#4 d4 f#4 | a3 c#4 e4 a4 e4 c#4 a3 c#4 "
                    + "| b3 d4 f#4 b4 f#4 d4 b3 d4 | g3 b3 d4 g4 d4 b3 g3 b3 | g3 b3 e4 g4 e4 b3 g3 b3 | f#3 a#3 c#4 f#4 c#4 a#3 f#3 a#3 "
                    + "| g3 b3 d4 g4 d4 b3 g3 b3 | d4 f#4 a4 d5 a4 f#4 d4 f#4 | a3 c#4 e4 a4 e4 c#4 a3 c#4 | b3 d4 f#4 b4 f#4 d4 b3 d4 "
                    + "| g3 b3 d4 g4 d4 b3 g3 b3 | d4 f#4 a4 d5 a4 f#4 d4 f#4 | g3 b3 e4 g4 f#3 a#3 c#4 f#4 | b3 d4 f#4 b4 f#4 d4 b3 f#3"),
            spoor(Instr.FLUTE, 0.24f, "f#4 . . . . . b4 . | d5 . . . b4 . . . | a4 . . . f#4 . a4 . | e4 . . . . . . . "
                    + "| f#4 . . . . . b4 . | d5 . . . e5 . d5 . | b4 . . . g4 . . . | a#4 . . . c#5 . . . "
                    + "| d5 . . . . . b4 . | a4 . . . . . f#4 . | e4 . . . a4 . c#5 . | b4 . . . . . . . "
                    + "| d5 . . . b4 . g4 . | f#4 . . . a4 . d5 . | e5 . . . c#5 . . . | b4 . . . . . . ."),
            spoor(Instr.BASS, 0.17f, "b3 . . . . . . . | g3 . . . . . . . | d4 . . . . . . . | a3 . . . . . . . "
                    + "| b3 . . . . . . . | g3 . . . . . . . | e4 . . . . . . . | f#3 . . . . . . . "
                    + "| g3 . . . . . . . | d4 . . . . . . . | a3 . . . . . . . | b3 . . . . . . . "
                    + "| g3 . . . . . . . | d4 . . . . . . . | e4 . . . f#3 . . . | b3 . . . . . . ."));

    /** Lindedorp, het dorp onder de grote linde: vrolijk, banjo en gitaar, in G. */
    static final Lied DORP = lied("dorp", 4, true,
            spoor(Instr.BANJO, 0.22f, "g4 . b4 . d5 . b4 . | c5 . . . a4 . . . | b4 . g4 . a4 . b4 . | a4 . . . d4 . . . "
                    + "| g4 . b4 . d5 . e5 . | d5 . . . b4 . g4 . | a4 . c5 . b4 . a4 . | g4 . . . . . . . "
                    + "| b4 . d5 . . . b4 . | c5 . e5 . . . c5 . | b4 . d5 . g4 . b4 . | a4 . . . . . d4 . "
                    + "| e4 . g4 . c5 . e5 . | d5 . b4 . g4 . b4 . | a4 . f#4 . a4 . c5 . | b4 . . . g4 . . ."),
            spoor(Instr.GUITAR, 0.13f, ". . g3+b3+d4 . . . g3+b3+d4 . | . . c4+e4+g4 . . . c4+e4+g4 . | . . g3+b3+d4 . . . g3+b3+d4 . | . . a3+d4+f#4 . . . a3+d4+f#4 . "
                    + "| . . g3+b3+d4 . . . g3+b3+d4 . | . . g3+b3+d4 . . . g3+b3+d4 . | . . a3+d4+f#4 . . . a3+d4+f#4 . | . . g3+b3+d4 . . . g3+b3+d4 . "
                    + "| . . g3+b3+d4 . . . g3+b3+d4 . | . . c4+e4+g4 . . . c4+e4+g4 . | . . g3+b3+d4 . . . g3+b3+d4 . | . . a3+d4+f#4 . . . a3+d4+f#4 . "
                    + "| . . c4+e4+g4 . . . c4+e4+g4 . | . . g3+b3+d4 . . . g3+b3+d4 . | . . a3+d4+f#4 . . . a3+d4+f#4 . | . . g3+b3+d4 . . . g3+b3+d4 ."),
            spoor(Instr.BASS, 0.18f, "g3 . . . d4 . . . | c4 . . . g3 . . . | g3 . . . d4 . . . | d4 . . . a3 . . . "
                    + "| g3 . . . d4 . . . | g3 . . . d4 . . . | d4 . . . a3 . . . | g3 . . . d4 . . . "
                    + "| g3 . . . d4 . . . | c4 . . . g3 . . . | g3 . . . d4 . . . | d4 . . . a3 . . . "
                    + "| c4 . . . g3 . . . | g3 . . . d4 . . . | d4 . . . a3 . . . | g3 . . . d4 . . ."),
            spoor(Instr.HAT, 0.06f, "x . . . x . . . | x . . . x . . . | x . . . x . . . | x . . . x . . . "
                    + "| x . . . x . . . | x . . . x . . . | x . . . x . . . | x . . . x . . . "
                    + "| x . . . x . . . | x . . . x . . . | x . . . x . . . | x . . . x . . . "
                    + "| x . . . x . . . | x . . . x . . . | x . . . x . . . | x . . . x . . ."));

    /** De tempelruïne: klokjes en ijzer, oud en stil, in a-klein. */
    static final Lied TEMPEL = lied("tempel", 5, true,
            spoor(Instr.BELL, 0.16f, "a3+c4+e4 . . . . . . . | a3+c4+f4 . . . . . . . | b3+d4+g4 . . . . . . . | b3+e4+g4 . . . . . . . "
                    + "| a3+c4+e4 . . . . . . . | a3+c4+f4 . . . . . . . | a3+d4+f4 . . . . . . . | g#3+b3+e4 . . . . . . ."),
            spoor(Instr.IRON, 0.2f, ". . e5 . . . d5 . | c5 . . . . . a4 . | b4 . . . d5 . . . | e4 . . . . . . . "
                    + "| . . a4 . . . c5 . | f4 . . . a4 . c5 . | d5 . . . f4 . a4 . | g#4 . . . b4 . e5 ."),
            spoor(Instr.DIDGE, 0.1f, "a3 . . . . . . . | . . . . . . . . | g3 . . . . . . . | . . . . . . . . "
                    + "| a3 . . . . . . . | . . . . . . . . | d4 . . . . . . . | e4 . . . . . . ."),
            spoor(Instr.CHIME, 0.1f, ". . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . e5 . "
                    + "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . b4"));

    /** Het Veen: druppels en een brommende grondtoon, in e-klein. */
    static final Lied VEEN = lied("veen", 5, true,
            spoor(Instr.DIDGE, 0.17f, "e4 . . . . . . . | e4 . . . . . . . | c4 . . . . . . . | b3 . . . . . . . "
                    + "| e4 . . . . . . . | e4 . . . . . . . | c4 . . . . . . . | a#3 . . . b3 . . ."),
            spoor(Instr.HARP, 0.13f, "e4 . g4 . b4 . . . | e4 . g4 . c5 . . . | c4 . e4 . g4 . . . | b3 . d#4 . f#4 . . . "
                    + "| e4 . g4 . b4 . . . | e4 . a4 . c5 . . . | c4 . e4 . a4 . . . | a#3 . . . b3 . d#4 ."),
            spoor(Instr.XYLO, 0.13f, ". . . . . . . b4 | . . . . e5 . . . | . . . . . . g4 . | . . . f#4 . . . . "
                    + "| . . . . . . . e5 | . . . . c5 . . . | . . . . . . a4 . | . . . . . . . ."),
            spoor(Instr.BELL, 0.09f, ". . . . . . . . | . . . . . . . . | . . . . . . . . | b4 . . . . . . . "
                    + "| . . . . . . . . | . . . . . . . . | . . . . . . . . | a#4 . . . . . . ."));

    /** De haven: een zeemanslied in drieën, in G. */
    static final Lied HAVEN = lied("haven", 4, true,
            spoor(Instr.FLUTE, 0.24f, "d4 . g4 . g4 a4 | b4 . . . g4 . | e4 . c5 . c5 b4 | b4 . . . g4 . "
                    + "| a4 . f#4 . a4 b4 | c5 . . . a4 . | b4 . g4 . a4 b4 | a4 . . . . . "
                    + "| d5 . . . b4 . | c5 . e5 . c5 . | b4 . d5 . b4 . | g4 . . . e4 . "
                    + "| e4 . g4 . c5 . | a4 . f#4 . a4 . | g4 . b4 . a4 f#4 | g4 . . . . ."),
            spoor(Instr.GUITAR, 0.16f, "g3 . b3+d4 . b3+d4 . | g3 . b3+d4 . b3+d4 . | g3 . c4+e4 . c4+e4 . | g3 . b3+d4 . b3+d4 . "
                    + "| a3 . d4+f#4 . d4+f#4 . | a3 . d4+f#4 . d4+f#4 . | g3 . b3+d4 . b3+d4 . | a3 . d4+f#4 . d4+f#4 . "
                    + "| g3 . b3+d4 . b3+d4 . | g3 . c4+e4 . c4+e4 . | g3 . b3+d4 . b3+d4 . | g3 . b3+e4 . b3+e4 . "
                    + "| g3 . c4+e4 . c4+e4 . | a3 . d4+f#4 . d4+f#4 . | g3 . b3+d4 . b3+d4 . | g3 . b3+d4 . . ."),
            spoor(Instr.BASS, 0.2f, "g3 . . . . . | g3 . . . . . | c4 . . . . . | g3 . . . . . | d4 . . . . . | d4 . . . . . | g3 . . . . . | d4 . . . . . "
                    + "| g3 . . . . . | c4 . . . . . | g3 . . . . . | e4 . . . . . | c4 . . . . . | d4 . . . . . | g3 . . . . . | g3 . . . . ."));

    /** Op zee, aan boord van de Zilvermeeuw: vrolijk en vooruit, in D. */
    static final Lied ZEE = lied("zee", 3, true,
            spoor(Instr.FLUTE, 0.26f, "a4 . f#4 a4 . d5 | b4 . . g4 . . | a4 . f#4 d4 . f#4 | e4 . . . . . "
                    + "| a4 . f#4 a4 . d5 | d5 . b4 d5 . e5 | c#5 . a4 e4 . c#5 | d5 . . . . . "
                    + "| f#5 . d5 b4 . d5 | d5 . b4 g4 . b4 | a4 . f#4 d4 . f#4 | a4 . . e4 . . "
                    + "| b4 . c#5 d5 . b4 | g4 . a4 b4 . d5 | c#5 . e5 c#5 . a4 | d5 . . . . ."),
            spoor(Instr.HARP, 0.15f, ". . d4+f#4 . . d4+a4 | . . d4+g4 . . b3+d4 | . . d4+f#4 . . d4+a4 | . . c#4+e4 . . c#4+e4 "
                    + "| . . d4+f#4 . . d4+a4 | . . d4+g4 . . b3+d4 | . . c#4+e4 . . c#4+e4 | . . d4+f#4 . . d4+a4 "
                    + "| . . d4+f#4 . . b3+d4 | . . d4+g4 . . b3+d4 | . . d4+f#4 . . d4+a4 | . . c#4+e4 . . c#4+e4 "
                    + "| . . d4+f#4 . . b3+d4 | . . d4+g4 . . b3+d4 | . . c#4+e4 . . c#4+e4 | . . d4+f#4 . . ."),
            spoor(Instr.BASS, 0.22f, "d4 . . a3 . . | g3 . . d4 . . | d4 . . a3 . . | a3 . . e4 . . | d4 . . a3 . . | g3 . . d4 . . | a3 . . e4 . . | d4 . . a3 . . "
                    + "| b3 . . f#3 . . | g3 . . d4 . . | d4 . . a3 . . | a3 . . e4 . . | b3 . . f#3 . . | g3 . . d4 . . | a3 . . e4 . . | d4 . . . . ."),
            spoor(Instr.HAT, 0.08f, "x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . "
                    + "| x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . . . ."));

    // ── het Zilverstrand (eiland 2) ─────────────────────────────────────────────────────────

    /** Zoutkom, het vissersdorp: vrolijk, xylofoon en gitaar, in C. */
    static final Lied ZOUTKOM = lied("zoutkom", 4, true,
            spoor(Instr.XYLO, 0.22f, "e4 . g4 . c5 . e5 . | d5 . c5 . a4 . . . | c5 . a4 . f4 . a4 . | g4 . . . b4 . d5 . "
                    + "| e5 . d5 . c5 . e5 . | e5 . a4 . c5 . . . | d5 . f4 . a4 . d5 . | b4 . . . . . . . "
                    + "| a4 . c5 . f5 . e5 . | e5 . c5 . g4 . . . | f4 . a4 . d5 . c5 . | b4 . g4 . b4 . d5 . "
                    + "| c5 . e5 . g4 . c5 . | a4 . c5 . e5 . d5 . | d5 . b4 . g4 . b4 . | c5 . . . . . . ."),
            spoor(Instr.GUITAR, 0.14f, ". . c4+e4+g4 . . . c4+e4+g4 . | . . a3+c4+e4 . . . a3+c4+e4 . | . . a3+c4+f4 . . . a3+c4+f4 . | . . g3+b3+d4 . . . g3+b3+d4 . "
                    + "| . . c4+e4+g4 . . . c4+e4+g4 . | . . a3+c4+e4 . . . a3+c4+e4 . | . . a3+d4+f4 . . . a3+d4+f4 . | . . g3+b3+d4 . . . g3+b3+d4 . "
                    + "| . . a3+c4+f4 . . . a3+c4+f4 . | . . c4+e4+g4 . . . c4+e4+g4 . | . . a3+d4+f4 . . . a3+d4+f4 . | . . g3+b3+d4 . . . g3+b3+d4 . "
                    + "| . . c4+e4+g4 . . . c4+e4+g4 . | . . a3+c4+e4 . . . a3+c4+e4 . | . . g3+b3+d4 . . . g3+b3+d4 . | . . c4+e4+g4 . . . . ."),
            spoor(Instr.BASS, 0.2f, "c4 . . . g3 . . . | a3 . . . e4 . . . | f4 . . . c4 . . . | g3 . . . d4 . . . "
                    + "| c4 . . . g3 . . . | a3 . . . e4 . . . | d4 . . . a3 . . . | g3 . . . d4 . . . "
                    + "| f4 . . . c4 . . . | c4 . . . g3 . . . | d4 . . . a3 . . . | g3 . . . d4 . . . "
                    + "| c4 . . . g3 . . . | a3 . . . e4 . . . | g3 . . . d4 . . . | c4 . . . . . . ."),
            spoor(Instr.HAT, 0.07f, "x . . . x . . . | x . . . x . . . | x . . . x . . . | x . . . x . x . "
                    + "| x . . . x . . . | x . . . x . . . | x . . . x . . . | x . . . x . x . "
                    + "| x . . . x . . . | x . . . x . . . | x . . . x . . . | x . . . x . x . "
                    + "| x . . . x . . . | x . . . x . . . | x . . . x . . . | x . . . . . . ."));

    /** De duinen: wind en een fluit, in a-klein. */
    static final Lied DUINEN = lied("duinen", 5, true,
            spoor(Instr.FLUTE, 0.25f, "e5 . . . . . d5 . | c5 . . . a4 . . . | b4 . . . . . g4 . | a4 . . . . . . . "
                    + "| a4 . . . c5 . . . | d5 . . . e5 . . . | d5 . . . b4 . . . | b4 . . . . . . . "
                    + "| c5 . . . e5 . . . | f5 . . . e5 . c5 . | d5 . . . b4 . g4 . | a4 . . . . . . ."),
            spoor(Instr.HARP, 0.12f, "a3 . e4 . a4 . e4 . | a3 . e4 . a4 . e4 . | g3 . d4 . g4 . d4 . | a3 . e4 . a4 . e4 . "
                    + "| a3 . c4 . f4 . c4 . | a3 . c4 . f4 . c4 . | g3 . d4 . g4 . d4 . | g3 . b3 . e4 . b3 . "
                    + "| a3 . e4 . a4 . e4 . | a3 . c4 . f4 . c4 . | g3 . d4 . g4 . d4 . | a3 . e4 . a4 . c5 ."),
            spoor(Instr.BASS, 0.15f, "a3 . . . . . . . | a3 . . . . . . . | g3 . . . . . . . | a3 . . . . . . . "
                    + "| f4 . . . . . . . | f4 . . . . . . . | g3 . . . . . . . | e4 . . . . . . . "
                    + "| a3 . . . . . . . | f4 . . . . . . . | g3 . . . . . . . | a3 . . . e4 . . ."),
            spoor(Instr.CHIME, 0.1f, ". . . . . . . . | . . . . . . . e5 | . . . . . . . . | . . . . . . b4 . "
                    + "| . . . . . . . . | . . . . . . . a4 | . . . . . . . . | . . . . . . e5 . "
                    + "| . . . . . . . . | . . . . . . . c5 | . . . . . . . . | . . . . . . . ."));

    /** De kaap en de vuurtoren: klokken over zee, in d-klein. */
    static final Lied VUURTOREN = lied("vuurtoren", 5, true,
            spoor(Instr.BELL, 0.16f, "d4+f4+a4 . . . . . . . | a#3+d4+f4 . . . . . . . | c4+e4+g4 . . . . . . . | a3+c4+f4 . . . . . . . "
                    + "| d4+f4+a4 . . . . . . . | a#3+d4+g4 . . . . . . . | a3+c#4+e4 . . . . . . . | a3+c#4+e4 . . . . . . . "
                    + "| a#3+d4+f4 . . . . . . . | c4+e4+g4 . . . . . . . | d4+f4+a4 . . . . . . . | a3+c#4+e4 . . . . . . ."),
            spoor(Instr.HARP, 0.2f, "a4 . . . d5 . . . | f5 . . . d5 . . . | e5 . . . c5 . g4 . | a4 . . . . . . . "
                    + "| f4 . a4 . d5 . . . | d5 . . . a#4 . g4 . | a4 . c#5 . e5 . . . | e5 . . . . . . . "
                    + "| d5 . . . f5 . . . | e5 . . . g4 . c5 . | d5 . . . a4 . f4 . | e4 . . . c#5 . . ."),
            spoor(Instr.BASS, 0.17f, "d4 . . . . . . . | a#3 . . . . . . . | c4 . . . . . . . | f4 . . . . . . . "
                    + "| d4 . . . . . . . | g3 . . . . . . . | a3 . . . . . . . | a3 . . . e4 . . . "
                    + "| a#3 . . . . . . . | c4 . . . . . . . | d4 . . . . . . . | a3 . . . . . . ."),
            spoor(Instr.CHIME, 0.09f, ". . . . . . . . | . . . . . . . . | . . . . . . . e5 | . . . . . . . . "
                    + "| . . . . . . . . | . . . . . . . . | . . . . . . . e5 | . . . . . . . . "
                    + "| . . . . . . . . | . . . . . . . f5 | . . . . . . . . | . . . . . . . a4"));

    // ── de Getijdentempel (eiland 2b) ───────────────────────────────────────────────────────

    /** Buiten bij de tempel: het wad, het plein, de poelen. Rustig, fluit en gitaar, in F. */
    static final Lied WAD = lied("wad", 4, true,
            spoor(Instr.FLUTE, 0.24f, "a4 . . . c5 . a4 . | g4 . . . e4 . . . | f4 . a4 . d5 . c5 . | a#4 . . . . . . . "
                    + "| c5 . . . a4 . c5 . | e5 . . . d5 . c5 . | d5 . . . a#4 . g4 . | e4 . . . . . . . "
                    + "| e5 . . . c5 . e5 . | f5 . . . d5 . a4 . | a#4 . . . d5 . f4 . | g4 . . . e4 . f4 ."),
            spoor(Instr.GUITAR, 0.13f, ". . a3+c4+f4 . . . a3+c4+f4 . | . . g3+c4+e4 . . . g3+c4+e4 . | . . a3+d4+f4 . . . a3+d4+f4 . | . . a#3+d4+f4 . . . a#3+d4+f4 . "
                    + "| . . a3+c4+f4 . . . a3+c4+f4 . | . . g3+c4+e4 . . . g3+c4+e4 . | . . a#3+d4+f4 . . . a#3+d4+f4 . | . . g3+c4+e4 . . . g3+c4+e4 . "
                    + "| . . a3+c4+e4 . . . a3+c4+e4 . | . . a3+d4+f4 . . . a3+d4+f4 . | . . a#3+d4+f4 . . . a#3+d4+f4 . | . . g3+c4+e4 . . . a3+c4+f4 ."),
            spoor(Instr.BASS, 0.17f, "f4 . . . c4 . . . | c4 . . . g3 . . . | d4 . . . a3 . . . | a#3 . . . f4 . . . "
                    + "| f4 . . . c4 . . . | c4 . . . g3 . . . | a#3 . . . f4 . . . | c4 . . . g3 . . . "
                    + "| a3 . . . e4 . . . | d4 . . . a3 . . . | a#3 . . . f4 . . . | c4 . . . . . f4 ."),
            spoor(Instr.CHIME, 0.08f, ". . . . . . . . | . . . . . . . c5 | . . . . . . . . | . . . . . . . f5 "
                    + "| . . . . . . . . | . . . . . . . g4 | . . . . . . . . | . . . . . . . e5 "
                    + "| . . . . . . . . | . . . . . . . a4 | . . . . . . . . | . . . . . . . ."));

    /** In de Getijdentempel: druppels en ijzer, galmend, in fis-klein. */
    static final Lied GETIJDENTEMPEL = lied("getijdentempel", 5, true,
            spoor(Instr.BELL, 0.15f, "f#3+a3+c#4 . . . . . . . | d4+f#4+a4 . . . . . . . | e4+g#4+b4 . . . . . . . | c#4+e4+g#4 . . . . . . . "
                    + "| f#3+a3+c#4 . . . . . . . | d4+f#4+a4 . . . . . . . | b3+d4+f#4 . . . . . . . | c#4+f4+g#4 . . . . . . ."),
            spoor(Instr.IRON, 0.18f, ". . c#5 . . . a4 . | f#4 . . . a4 . d5 . | e5 . . . b4 . . . | g#4 . . . c#5 . e5 . "
                    + "| f#5 . . . e5 . c#5 . | d5 . . . a4 . f#4 . | b4 . . . d5 . f#4 . | f4 . g#4 . c#5 . . ."),
            spoor(Instr.HARP, 0.12f, ". . . . . . . f#5 | . . . c#5 . . . . | . . . . . b4 . . | . e5 . . . . . . "
                    + "| . . . . . . . a4 | . . . f#5 . . . . | . . . . . . d5 . | . . . . . . . c#5"),
            spoor(Instr.BASS, 0.16f, "f#3 . . . . . . . | d4 . . . . . . . | e4 . . . . . . . | c#4 . . . . . . . "
                    + "| f#3 . . . . . . . | d4 . . . . . . . | b3 . . . . . . . | c#4 . . . g#3 . . ."));

    /** Kapitein Zoutbaard: een zeemansjig in d-klein, met een brommende grondtoon. */
    static final Lied ZOUTBAARD = lied("zoutbaard", 2, true,
            spoor(Instr.BASS, 0.85f, "d4 . . a3 . . | d4 . . a3 . . | c4 . . g3 . . | c4 . . g3 . . | a#3 . . f4 . . | a3 . . e4 . . | d4 . . a3 . . | a3 . c#4 . e4 ."),
            spoor(Instr.KICK, 0.6f, "x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . . x . . | x . x x . x"),
            spoor(Instr.SNARE, 0.4f, ". . x . . x | . . x . . x | . . x . . x | . . x . . x | . . x . . x | . . x . . x | . . x . . x | . x x . x x"),
            spoor(Instr.HARP, 0.5f, "d5 . d5 f5 . e5 | d5 . a4 . . . | e5 . e5 f5 . e5 | e5 . c5 . . . | f5 . f5 a#4 . d5 | c#5 . e5 a4 . . | d5 e5 f5 e5 d5 c5 | c#5 . . a4 . ."),
            spoor(Instr.DIDGE, 0.5f, "d4 . . . . . | d4 . . . . . | c4 . . . . . | c4 . . . . . | a#3 . . . . . | a3 . . . . . | d4 . . . . . | a3 . . . . ."),
            spoor(Instr.BELL, 0.3f, "a4 . . . . . | . . . . . . | g4 . . . . . | . . . . . . | f4 . . . . . | e4 . . . . . | d4 . . . . . | c#4 . . . . ."));

    // ── de Witte Toppen (eiland 3a) ─────────────────────────────────────────────────────────

    /** IJshoeve, het dal en de fjord: klokjes in de sneeuw, warm, in D. */
    static final Lied IJSHOEVE = lied("ijshoeve", 4, true,
            spoor(Instr.CHIME, 0.2f, "d5 . f#5 . e5 . d5 . | b4 . . . a4 . . . | g4 . b4 . a4 . f#4 . | e4 . . . . . . . "
                    + "| d5 . f#5 . e5 . d5 . | b4 . . . d5 . . . | e5 . c#5 . a4 . c#5 . | d5 . . . . . . . "
                    + "| b4 . . . d5 . . . | a4 . . . f#4 . . . | g4 . . . b4 . e5 . | c#5 . . . . . a4 ."),
            spoor(Instr.BELL, 0.11f, "d4+f#4+a4 . . . . . . . | b3+d4+f#4 . . . . . . . | g3+b3+d4 . . . . . . . | a3+c#4+e4 . . . . . . . "
                    + "| d4+f#4+a4 . . . . . . . | b3+d4+f#4 . . . . . . . | a3+c#4+e4 . . . . . . . | d4+f#4+a4 . . . . . . . "
                    + "| g3+b3+d4 . . . . . . . | d4+f#4+a4 . . . . . . . | g3+b3+e4 . . . . . . . | a3+c#4+e4 . . . . . . ."),
            spoor(Instr.HARP, 0.1f, ". . a4 . . . f#4 . | . . f#4 . . . d4 . | . . d4 . . . g4 . | . . e4 . . . c#4 . "
                    + "| . . a4 . . . f#4 . | . . f#4 . . . b3 . | . . e4 . . . a4 . | . . f#4 . . . a4 . "
                    + "| . . g4 . . . d4 . | . . a4 . . . d4 . | . . b4 . . . g4 . | . . e4 . . . c#4 ."),
            spoor(Instr.BASS, 0.16f, "d4 . . . a3 . . . | b3 . . . f#4 . . . | g3 . . . d4 . . . | a3 . . . e4 . . . "
                    + "| d4 . . . a3 . . . | b3 . . . f#4 . . . | a3 . . . e4 . . . | d4 . . . a3 . . . "
                    + "| g3 . . . d4 . . . | d4 . . . a3 . . . | e4 . . . b3 . . . | a3 . . . e4 . . ."));

    /** Het Sparrenbos en de storm: zacht, een brommende didgeridoo, wind en een fluit, in e-klein. */
    static final Lied SNEEUWSTORM = lied("sneeuwstorm", 5, true,
            spoor(Instr.DIDGE, 0.16f, "e4 . . . . . . . | e4 . . . . . . . | c4 . . . . . . . | d4 . . . . . . . "
                    + "| e4 . . . . . . . | e4 . . . . . . . | c4 . . . . . . . | b3 . . . . . . ."),
            spoor(Instr.FLUTE, 0.2f, "b4 . . . . . a4 . | g4 . . . e4 . . . | . . . . . . . . | f#4 . g4 . a4 . . . "
                    + "| b4 . . . d5 . . . | c5 . . . b4 . a4 . | g4 . . . . . . . | f#4 . . . e4 . . ."),
            spoor(Instr.CHIME, 0.08f, ". . . . . . . e5 | . . . . b4 . . . | . . . . . . . . | . . . . . . . d5 "
                    + "| . . . e5 . . . . | . . . . . . . . | . . . . . . . g4 | . . . . b4 . . ."),
            spoor(Instr.BASS, 0.14f, "e4 . . . . . . . | g3 . . . . . . . | c4 . . . . . . . | d4 . . . . . . . "
                    + "| e4 . . . . . . . | a3 . . . . . . . | c4 . . . . . . . | b3 . . . . . . ."));

    /** De Wachtersgang, de Wachterspost en de Bergpoort: ijzeren xylofoon, galm, in a-klein. */
    static final Lied WACHTERSPOST = lied("wachterspost", 5, true,
            spoor(Instr.IRON, 0.18f, "a4 . . . c5 . e5 . | d5 . . . c5 . b4 . | a4 . . . e4 . . . | . . . . . . . . "
                    + "| f4 . . . a4 . c5 . | b4 . . . g4 . . . | a4 . . . . . e4 . | e4 . . . . . . ."),
            spoor(Instr.BELL, 0.12f, "a3+c4+e4 . . . . . . . | g3+b3+d4 . . . . . . . | a3+c4+e4 . . . . . . . | g#3+b3+e4 . . . . . . . "
                    + "| a3+c4+f4 . . . . . . . | g3+b3+d4 . . . . . . . | a3+c4+e4 . . . . . . . | g#3+b3+e4 . . . . . . ."),
            spoor(Instr.HARP, 0.1f, ". . e4 . . . a4 . | . . d4 . . . g4 . | . . c4 . . . e4 . | . . b3 . . . g#4 . "
                    + "| . . c4 . . . f4 . | . . d4 . . . b4 . | . . e4 . . . c5 . | . . b4 . . . g#4 ."),
            spoor(Instr.BASS, 0.15f, "a3 . . . . . . . | g3 . . . . . . . | a3 . . . . . . . | e4 . . . . . . . "
                    + "| f4 . . . . . . . | g3 . . . . . . . | a3 . . . . . . . | e4 . . . . . . ."));

    // ── de Witte Toppen: het IJspaleis (eiland 3b) ─────────────────────────────────────────

    /** Het bergpad, de berghut, het voorplein en de sleebaan: wind, een fluit en een harp, open en hoog, in G. */
    static final Lied BERGPAD = lied("bergpad", 4, true,
            spoor(Instr.FLUTE, 0.2f, "g4 . . . b4 . d5 . | e5 . . . d5 . b4 . | c5 . . . a4 . . . | d5 . . . . . . . "
                    + "| g4 . . . b4 . d5 . | e5 . . . f#5 . e5 . | d5 . . . c5 . a4 . | b4 . . . . . . . "
                    + "| c5 . . . e5 . . . | d5 . . . b4 . g4 . | a4 . . . c5 . f#4 . | g4 . . . . . . ."),
            spoor(Instr.HARP, 0.12f, ". . d4 . . . g4 . | . . e4 . . . b4 . | . . e4 . . . g4 . | . . f#4 . . . a4 . "
                    + "| . . d4 . . . g4 . | . . e4 . . . b4 . | . . f#4 . . . a4 . | . . d4 . . . g4 . "
                    + "| . . e4 . . . g4 . | . . d4 . . . b4 . | . . f#4 . . . a4 . | . . d4 . . . b3 ."),
            spoor(Instr.BELL, 0.1f, "g3+b3+d4 . . . . . . . | e4+g4+b4 . . . . . . . | c4+e4+g4 . . . . . . . | d4+f#4+a4 . . . . . . . "
                    + "| g3+b3+d4 . . . . . . . | e4+g4+b4 . . . . . . . | d4+f#4+a4 . . . . . . . | g3+b3+d4 . . . . . . . "
                    + "| c4+e4+g4 . . . . . . . | g3+b3+d4 . . . . . . . | d4+f#4+a4 . . . . . . . | g3+b3+d4 . . . . . . ."),
            spoor(Instr.CHIME, 0.07f, ". . . . . . . . | . . . . . . . f#5 | . . . . . . . . | . . . e5 . . . . "
                    + "| . . . . . . . . | . . . . . . . . | . . . . . d5 . . | . . . . . . . . "
                    + "| . . . . . . . e5 | . . . . . . . . | . . . . d5 . . . | . . . . . . . ."),
            spoor(Instr.BASS, 0.15f, "g3 . . . d4 . . . | e4 . . . b3 . . . | c4 . . . g3 . . . | d4 . . . a3 . . . "
                    + "| g3 . . . d4 . . . | e4 . . . b3 . . . | d4 . . . a3 . . . | g3 . . . d4 . . . "
                    + "| c4 . . . g3 . . . | g3 . . . d4 . . . | d4 . . . a3 . . . | g3 . . . . . . ."));

    /** Het IJspaleis (binnen): ijzeren xylofoon en klokjes, stil en glinsterend, in b-klein. */
    static final Lied IJSPALEIS = lied("ijspaleis", 5, true,
            spoor(Instr.IRON, 0.18f, "b4 . . . d5 . f#5 . | e5 . . . d5 . c#5 . | b4 . . . f#4 . . . | . . . . . . . . "
                    + "| g4 . . . b4 . d5 . | c#5 . . . a4 . . . | b4 . . . . . f#4 . | f#4 . . . . . . ."),
            spoor(Instr.CHIME, 0.1f, ". . . . . . . f#5 | . . . . b4 . . . | . . . . . . . . | . . . d5 . . . . "
                    + "| . . . . . . . e5 | . . . . . . . . | . . . . c#5 . . . | . . . . . . . ."),
            spoor(Instr.BELL, 0.12f, "b3+d4+f#4 . . . . . . . | a3+c#4+e4 . . . . . . . | b3+d4+f#4 . . . . . . . | f#3+a#3+c#4 . . . . . . . "
                    + "| g3+b3+d4 . . . . . . . | a3+c#4+e4 . . . . . . . | b3+d4+f#4 . . . . . . . | f#3+a#3+c#4 . . . . . . ."),
            spoor(Instr.HARP, 0.09f, ". . f#4 . . . b4 . | . . e4 . . . a4 . | . . d4 . . . b4 . | . . c#4 . . . f#4 . "
                    + "| . . d4 . . . g4 . | . . e4 . . . a4 . | . . f#4 . . . d4 . | . . c#4 . . . a#3 ."),
            spoor(Instr.BASS, 0.15f, "b3 . . . . . . . | a3 . . . . . . . | b3 . . . . . . . | f#3 . . . . . . . "
                    + "| g3 . . . . . . . | a3 . . . . . . . | b3 . . . . . . . | f#3 . . . . . . ."));

    /** Grimmel, de reuzenijsbeer: zware stappen, een brommende grondtoon en een ijzeren melodie, in e-klein. */
    static final Lied GRIMMEL = lied("grimmel", 2, true,
            spoor(Instr.BASS, 0.85f, "e4 . e4 . b3 . e4 . | e4 . e4 . b3 . e4 . | c4 . c4 . g3 . c4 . | d4 . d4 . a3 . d4 . "
                    + "| e4 . e4 . b3 . e4 . | e4 . e4 . b3 . e4 . | c4 . c4 . d4 . d4 . | b3 . c4 . d4 . d#4 ."),
            spoor(Instr.KICK, 0.65f, "x . . . x . x . | x . . . x . x . | x . . . x . x . | x . . . x . x . "
                    + "| x . . . x . x . | x . . . x . x . | x . . . x . x . | x . x . x x x x"),
            spoor(Instr.SNARE, 0.4f, ". . x . . . x . | . . x . . . x . | . . x . . . x . | . . x . . . x . "
                    + "| . . x . . . x . | . . x . . . x . | . . x . . . x . | . x . x . x x x"),
            spoor(Instr.DIDGE, 0.5f, "e4 . . . . . . . | e4 . . . . . . . | c4 . . . . . . . | d4 . . . . . . . "
                    + "| e4 . . . . . . . | e4 . . . . . . . | c4 . . . d4 . . . | b3 . . . . . . ."),
            spoor(Instr.IRON, 0.45f, "b4 . e5 . d5 . b4 . | a4 . b4 . g4 . e4 . | c5 . . . b4 . a4 . | d5 . . . f#5 . e5 . "
                    + "| b4 . e5 . d5 . b4 . | a4 . b4 . g4 . e4 . | c5 . e5 . d5 . f#5 . | e5 . . . d#5 . . ."),
            spoor(Instr.BELL, 0.3f, "e4+g4+b4 . . . . . . . | . . . . . . . . | c4+e4+g4 . . . . . . . | d4+f#4+a4 . . . . . . . "
                    + "| e4+g4+b4 . . . . . . . | . . . . . . . . | c4+e4+g4 . . . d4+f#4+a4 . . . | b3+d#4+f#4 . . . . . . ."));

    // ── de Gloedberg: Sintelgat en de mijnen (eiland 4a) ───────────────────────────────────

    /** Sintelgat, de Zwarte Baai en buiten op de berg: een banjo, een gitaar en het tikken van een aambeeld, in G. */
    static final Lied SINTELGAT = lied("sintelgat", 4, true,
            spoor(Instr.BANJO, 0.2f, "g4 . b4 . d5 . b4 . | c5 . a4 . f#4 . a4 . | g4 . b4 . d5 . e5 . | d5 . . . b4 . . . "
                    + "| e5 . d5 . c5 . b4 . | a4 . c5 . a4 . f#4 . | g4 . a4 . b4 . a4 . | g4 . . . d4 . . . "
                    + "| b4 . . . a4 . g4 . | e4 . g4 . a4 . . . | c5 . b4 . a4 . g4 . | a4 . . . . . d4 ."),
            spoor(Instr.GUITAR, 0.1f, "g3+b3+d4 . . . g3+b3+d4 . . . | d4+f#4+a4 . . . d4+f#4+a4 . . . | g3+b3+d4 . . . c4+e4+g4 . . . | g3+b3+d4 . . . . . . . "
                    + "| c4+e4+g4 . . . c4+e4+g4 . . . | d4+f#4+a4 . . . d4+f#4+a4 . . . | c4+e4+g4 . . . d4+f#4+a4 . . . | g3+b3+d4 . . . . . . . "
                    + "| e4+g4+b4 . . . e4+g4+b4 . . . | c4+e4+g4 . . . c4+e4+g4 . . . | a3+c4+e4 . . . a3+c4+e4 . . . | d4+f#4+a4 . . . . . . ."),
            spoor(Instr.IRON, 0.1f, "d5 . . . . . . . | . . . . d5 . . . | d5 . . . . . . . | . . . . . . d5 . "
                    + "| d5 . . . . . . . | . . . . d5 . . . | d5 . . . . . . . | . . . . . . d5 . "
                    + "| d5 . . . . . . . | . . . . d5 . . . | d5 . . . . . . . | . . . . d5 . d5 ."),
            spoor(Instr.HAT, 0.06f, ". . x . . . x . | . . x . . . x . | . . x . . . x . | . . x . . . x . "
                    + "| . . x . . . x . | . . x . . . x . | . . x . . . x . | . . x . . . x . "
                    + "| . . x . . . x . | . . x . . . x . | . . x . . . x . | . . x . . . x ."),
            spoor(Instr.BASS, 0.17f, "g3 . . . d4 . . . | d4 . . . a3 . . . | g3 . . . c4 . . . | g3 . . . d4 . . . "
                    + "| c4 . . . g3 . . . | d4 . . . a3 . . . | c4 . . . d4 . . . | g3 . . . d4 . . . "
                    + "| e4 . . . b3 . . . | c4 . . . g3 . . . | a3 . . . e4 . . . | d4 . . . a3 . . ."));

    /** De mijn: een lage bas, druppels die vallen en een harp in de verte, in d-klein. */
    static final Lied MIJNEN = lied("mijnen", 5, true,
            spoor(Instr.BASS, 0.2f, "d4 . . . . . a3 . | d4 . . . . . c4 . | a#3 . . . . . f4 . | a3 . . . . . . . "
                    + "| d4 . . . . . a3 . | d4 . . . . . f4 . | g3 . . . a3 . . . | d4 . . . . . . ."),
            spoor(Instr.CHIME, 0.09f, ". . . . . . f5 . | . . . . . . . . | . . d5 . . . . . | . . . . . . . a4 "
                    + "| . . . . f5 . . . | . . . . . . . . | . . . . . . e5 . | . a4 . . . . . ."),
            spoor(Instr.HARP, 0.08f, ". d4 . f4 . a4 . . | . d4 . f4 . a4 . . | . a#3 . d4 . f4 . . | . a3 . c#4 . e4 . . "
                    + "| . d4 . f4 . a4 . . | . d4 . f4 . a4 . . | . g3 . a#3 . d4 . . | . a3 . c#4 . e4 . ."),
            spoor(Instr.DIDGE, 0.12f, "d4 . . . . . . . | . . . . . . . . | a#3 . . . . . . . | a3 . . . . . . . "
                    + "| d4 . . . . . . . | . . . . . . . . | g3 . . . a3 . . . | d4 . . . . . . ."),
            spoor(Instr.PLING, 0.05f, ". . . . . . . . | . . . . . e5 . . | . . . . . . . . | . . . d5 . . . . "
                    + "| . . . . . . . . | . . . f5 . . . . | . . . . . . . . | . . . . . . . ."));

    /** De gloed bij de kern van de berg: een brommende grondtoon, een trage slag en een ijzeren melodie, in e (frygisch). */
    static final Lied GLOED = lied("gloed", 5, true,
            spoor(Instr.DIDGE, 0.16f, "e4 . . . . . . . | e4 . . . . . . . | f4 . . . . . . . | e4 . . . . . . . "
                    + "| e4 . . . . . . . | e4 . . . . . . . | d4 . . . f4 . . . | e4 . . . . . . ."),
            spoor(Instr.KICK, 0.18f, "x . . . . . . . | x . . . x . . . | x . . . . . . . | x . . . x . . . "
                    + "| x . . . . . . . | x . . . x . . . | x . . . . . . . | x . x . x . . ."),
            spoor(Instr.BASS, 0.2f, "e4 . . . e4 . f4 . | e4 . . . . . . . | e4 . . . e4 . g4 . | f4 . . . e4 . . . "
                    + "| e4 . . . e4 . f4 . | e4 . . . . . . . | d4 . . . f4 . g4 . | f4 . . . e4 . . ."),
            spoor(Instr.IRON, 0.1f, "b4 . . . c5 . . . | b4 . . . . . . . | g4 . . . a4 . b4 . | c5 . . . b4 . . . "
                    + "| e5 . . . d5 . c5 . | b4 . . . . . . . | a4 . . . c5 . b4 . | b4 . . . . . . ."),
            spoor(Instr.BELL, 0.08f, "e4+g4+b4 . . . . . . . | . . . . . . . . | f4+a4+c5 . . . . . . . | e4+g4+b4 . . . . . . . "
                    + "| e4+g4+b4 . . . . . . . | . . . . . . . . | d4+f4+a4 . . . . . . . | e4+g4+b4 . . . . . . ."));

    // ── de Gloedberg: de Smidse (eiland 4b) ────────────────────────────────────────────────

    /** De Smidse van de eerste smeden: een dreun als van een hamer, een lage bas en een aambeeld dat ver weg tinkelt, in d-klein. */
    static final Lied SMIDSE = lied("smidse", 5, true,
            spoor(Instr.BASS, 0.2f, "d4 . . . d4 . . . | a#3 . . . a#3 . . . | c4 . . . c4 . . . | a3 . . . a3 . a3 . "
                    + "| d4 . . . d4 . . . | f4 . . . f4 . . . | e4 . . . c4 . . . | a3 . . . . . . ."),
            spoor(Instr.KICK, 0.2f, "x . . . . . x . | x . . . . . x . | x . . . . . x . | x . . . x . x . "
                    + "| x . . . . . x . | x . . . . . x . | x . . . . . x . | x . . . x . x ."),
            spoor(Instr.IRON, 0.12f, "d5 . . . . . c5 . | a#4 . . . a4 . . . | g4 . . . a4 . c5 . | a4 . . . . . . . "
                    + "| d5 . . . f5 . e5 . | d5 . . . a4 . . . | g4 . f4 . e4 . c#5 . | d5 . . . . . . ."),
            spoor(Instr.DIDGE, 0.12f, "d4 . . . . . . . | a#3 . . . . . . . | c4 . . . . . . . | a3 . . . . . . . "
                    + "| d4 . . . . . . . | f4 . . . . . . . | e4 . . . . . . . | a3 . . . . . . ."),
            spoor(Instr.BELL, 0.07f, "d4+f4+a4 . . . . . . . | a#3+d4+f4 . . . . . . . | c4+e4+g4 . . . . . . . | a3+c#4+e4 . . . . . . . "
                    + "| d4+f4+a4 . . . . . . . | f4+a4+c5 . . . . . . . | c4+e4+g4 . . . . . . . | a3+c#4+e4 . . . . . . ."));

    /** Asgrim de Gloedwachter: een vlammend baslijntje, slagen als hamers op een aambeeld en een ijzeren melodie, in a-klein. */
    static final Lied ASGRIM = lied("asgrim", 2, true,
            spoor(Instr.BASS, 0.85f, "a3 . a3 . e4 . a3 . | a3 . a3 . e4 . a3 . | f4 . f4 . c4 . f4 . | g4 . g4 . d4 . g4 . "
                    + "| a3 . a3 . e4 . a3 . | a3 . a3 . e4 . a3 . | f4 . f4 . g4 . g4 . | e4 . f4 . g4 . g#4 ."),
            spoor(Instr.KICK, 0.65f, "x . . . x . x . | x . . . x . x . | x . . . x . x . | x . . . x . x . "
                    + "| x . . . x . x . | x . . . x . x . | x . . . x . x . | x . x . x x x x"),
            spoor(Instr.SNARE, 0.4f, ". . x . . . x . | . . x . . . x . | . . x . . . x . | . . x . . . x . "
                    + "| . . x . . . x . | . . x . . . x . | . . x . . . x . | . x . x . x x x"),
            spoor(Instr.IRON, 0.45f, "e5 . a4 . c5 . e5 . | d5 . c5 . b4 . a4 . | f5 . . . e5 . d5 . | d5 . . . b4 . g4 . "
                    + "| e5 . a4 . c5 . e5 . | f5 . e5 . d5 . c5 . | c5 . d5 . e5 . f5 . | e5 . . . d5 . . ."),
            spoor(Instr.DIDGE, 0.5f, "a3 . . . . . . . | a3 . . . . . . . | f4 . . . . . . . | g4 . . . . . . . "
                    + "| a3 . . . . . . . | a3 . . . . . . . | f4 . . . g4 . . . | e4 . . . . . . ."),
            spoor(Instr.BELL, 0.3f, "a3+c4+e4 . . . . . . . | . . . . . . . . | f4+a4+c5 . . . . . . . | g4+b4+d5 . . . . . . . "
                    + "| a3+c4+e4 . . . . . . . | . . . . . . . . | f4+a4+c5 . . . g4+b4+d5 . . . | e4+g#4+b4 . . . . . . ."));

    // ── Nachtrots: de muren (eiland 5a) ────────────────────────────────────────────────────

    /** Nachtrots buiten (de kreek, de klippen, de muurvoet): laag en een beetje dreigend, maar rustig; golven in de verte, in b-klein. */
    static final Lied NACHTROTS = lied("nachtrots", 5, true,
            spoor(Instr.BASS, 0.2f, "b3 . . . f#4 . . . | g3 . . . d4 . . . | a3 . . . e4 . . . | f#3 . . . c#4 . . . "
                    + "| b3 . . . f#4 . . . | e4 . . . b3 . . . | f#3 . . . a#3 . . . | b3 . . . . . . . "
                    + "| g3 . . . d4 . . . | a3 . . . e4 . . . | b3 . . . d4 . . . | f#3 . . . . . . ."),
            spoor(Instr.FLUTE, 0.11f, "d5 . . c#5 b4 . . . | . . . . d5 . e5 . | c#5 . . . a4 . . . | . . . . . . . . "
                    + "| f#4 . . a4 b4 . . . | g4 . . . e4 . . . | f#4 . . . c#5 . . . | b4 . . . . . . . "
                    + "| . . . . b4 . d5 . | c#5 . . . a4 . . . | b4 . . . f#4 . . . | a#4 . . . . . . ."),
            spoor(Instr.BELL, 0.07f, "b3+d4+f#4 . . . . . . . | g3+b3+d4 . . . . . . . | a3+c#4+e4 . . . . . . . | f#3+a#3+c#4 . . . . . . . "
                    + "| b3+d4+f#4 . . . . . . . | e4+g4+b4 . . . . . . . | f#3+a#3+c#4 . . . . . . . | b3+d4+f#4 . . . . . . . "
                    + "| g3+b3+d4 . . . . . . . | a3+c#4+e4 . . . . . . . | b3+d4+f#4 . . . . . . . | f#3+a#3+c#4 . . . . . . ."),
            spoor(Instr.KICK, 0.1f, "x . . . . . . . | x . . . x . . . | x . . . . . . . | x . . . x . . . "
                    + "| x . . . . . . . | x . . . x . . . | x . . . . . . . | x . . . x . . . "
                    + "| x . . . . . . . | x . . . x . . . | x . . . . . . . | x . . . x . x ."),
            spoor(Instr.CHIME, 0.05f, ". . . . . . . f#5 | . . . . . . . . | . . . . . e5 . . | . . . . . . . . "
                    + "| . . . . . . . . | . . . d5 . . . . | . . . . . . . . | . . . . . . f#5 . "
                    + "| . . . . . . . . | . . . . . . e5 . | . . . . . . . . | . . . . . . . ."));

    /** De weergang en het sluipen: een getokkelde bas op de tenen, een tikkende klok, een stiekem wijsje, in e-klein. */
    static final Lied MUREN = lied("muren", 5, true,
            spoor(Instr.BASS, 0.2f, "e4 . e4 . . . e4 . | e4 . e4 . . . g4 . | d4 . d4 . . . d4 . | b3 . b3 . . . c4 . "
                    + "| c4 . c4 . . . c4 . | a3 . a3 . . . b3 . | e4 . e4 . . . g4 . | f#4 . . . b3 . . ."),
            spoor(Instr.HAT, 0.06f, "x . x . x . x . | x . x . x . x . | x . x . x . x . | x . x . x . x . "
                    + "| x . x . x . x . | x . x . x . x . | x . x . x . x . | x . x . x . x x"),
            spoor(Instr.PLING, 0.08f, ". . b4 . . . a4 g4 | . . . . e4 . . . | . . a4 . . . g4 f#4 | . . . . d4 . . . "
                    + "| . . g4 . . . f#4 e4 | . . . . c4 . . . | . . b4 . . . c5 b4 | a4 . . . f#4 . . ."),
            spoor(Instr.HARP, 0.07f, "e4+g4+b4 . . . . . . . | . . . . . . . . | d4+f#4+a4 . . . . . . . | g3+b3+d4 . . . . . . . "
                    + "| c4+e4+g4 . . . . . . . | a3+c4+e4 . . . . . . . | e4+g4+b4 . . . . . . . | b3+d#4+f#4 . . . . . . ."),
            spoor(Instr.DIDGE, 0.08f, "e4 . . . . . . . | . . . . . . . . | d4 . . . . . . . | . . . . . . . . "
                    + "| c4 . . . . . . . | . . . . . . . . | e4 . . . . . . . | b3 . . . . . . ."));

    /** De kerkers: een brommende grondtoon, druppels, een harp in de diepte en sleutels die rinkelen, in g-klein. */
    static final Lied KERKERS = lied("kerkers", 5, true,
            spoor(Instr.BASS, 0.18f, "g3 . . . . . d4 . | g3 . . . . . a#3 . | d#4 . . . . . a#3 . | d4 . . . . . . . "
                    + "| g3 . . . . . d4 . | c4 . . . . . d#4 . | d4 . . . f#3 . . . | g3 . . . . . . ."),
            spoor(Instr.HARP, 0.08f, ". g3 . a#3 . d4 . . | . g3 . a#3 . d4 . . | . d#4 . g4 . a#4 . . | . d4 . f#4 . a4 . . "
                    + "| . g3 . a#3 . d4 . . | . c4 . d#4 . g4 . . | . d4 . f#4 . a4 . . | . g3 . a#3 . d4 . ."),
            spoor(Instr.CHIME, 0.07f, ". . . . . . . . | . . . d5 . . . . | . . . . . . . . | . . . . . . g4 . "
                    + "| . . . . a#4 . . . | . . . . . . . . | . . . . . . . . | . . . . . d5 . ."),
            spoor(Instr.IRON, 0.05f, ". . . . . . . . | . . . . . . . . | . . . . . . . . | . a4 a#4 . . . . . "
                    + "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . d5 c5 . . . . ."),
            spoor(Instr.DIDGE, 0.11f, "g3 . . . . . . . | . . . . . . . . | d#4 . . . . . . . | d4 . . . . . . . "
                    + "| g3 . . . . . . . | c4 . . . . . . . | d4 . . . . . . . | g3 . . . . . . ."));

    /** De binnenplaats: Morgals soldaten. Trommels als marcherende laarzen, een stoere bas, in c-klein (een gevecht, niet te zwaar). */
    static final Lied NACHTWACHT = lied("nachtwacht", 3, true,
            spoor(Instr.BASS, 0.8f, "c4 . c4 . g3 . c4 . | c4 . c4 . g3 . c4 . | g#3 . g#3 . d#4 . g#3 . | a#3 . a#3 . f4 . g4 . "
                    + "| c4 . c4 . g3 . c4 . | d#4 . d#4 . a#3 . d#4 . | f4 . f4 . c4 . f4 . | g4 . f4 . d#4 . d4 ."),
            spoor(Instr.KICK, 0.6f, "x . . . x . . . | x . . . x . x . | x . . . x . . . | x . . . x . x . "
                    + "| x . . . x . . . | x . . . x . x . | x . . . x . . . | x . x . x x x x"),
            spoor(Instr.SNARE, 0.4f, ". . x . . . x . | . . x . . . x x | . . x . . . x . | . . x . . . x x "
                    + "| . . x . . . x . | . . x . . . x x | . . x . . . x . | . x . x . x x x"),
            spoor(Instr.HAT, 0.18f, "x x x x x x x x | x x x x x x x x | x x x x x x x x | x x x x x x x x "
                    + "| x x x x x x x x | x x x x x x x x | x x x x x x x x | x x x x x x x x"),
            spoor(Instr.IRON, 0.4f, "g4 . . c5 . . d5 . | d#5 . d5 . c5 . . . | c5 . . d#5 . . f5 . | f5 . d#5 . d5 . . . "
                    + "| g4 . . c5 . . d5 . | d#5 . f5 . d#5 . . . | f5 . d#5 . d5 . c5 . | d5 . . . b4 . . ."),
            spoor(Instr.BELL, 0.25f, "c4+d#4+g4 . . . . . . . | . . . . . . . . | g#3+c4+d#4 . . . . . . . | a#3+d4+f4 . . . . . . . "
                    + "| c4+d#4+g4 . . . . . . . | d#4+g4+a#4 . . . . . . . | f4+g#4+c5 . . . . . . . | g3+b3+d4 . . . . . . ."));

    /** Gezien! Een kort alarm (dan sta je weer bij het zielvuur). */
    static final Lied GEZIEN = lied("gezien", 2, false,
            spoor(Instr.BELL, 0.5f, "a4 . a4 . d5 . . . . ."),
            spoor(Instr.BASS, 0.8f, "d4 . d4 . a3 . . . . ."),
            spoor(Instr.PLING, 0.3f, "f#5 . f#5 . . . . . . ."));

    /** Een bron (feeënkring): genezen, flesjes vol. */
    static final Lied BRON = lied("bron", 2, false,
            spoor(Instr.CHIME, 0.35f, "d4 f#4 a4 d5 f#5 d5 a4 f#4 | e4 g4 b4 e5 . . . ."),
            spoor(Instr.BELL, 0.25f, "d4+a4 . . . . . . . | e4+b4 . . . d5+f#5 . . ."),
            spoor(Instr.HARP, 0.2f, ". . . . . . . . | . . . . a4 . d5 ."));

    /** Wiske heeft een hint (zacht, kort). */
    static final Lied HINT = lied("hint", 2, false,
            spoor(Instr.CHIME, 0.3f, "a4 . d5 . f#5 . . ."),
            spoor(Instr.BELL, 0.15f, ". . . . d5 . . ."));

    /** Een opdracht: begonnen of een stap verder. */
    static final Lied OPDRACHT = lied("opdracht", 3, false,
            spoor(Instr.HARP, 0.45f, "g4 . b4 . d5 . . ."),
            spoor(Instr.FLUTE, 0.3f, ". . . . d5 . e5 ."));

    static List<Lied> alle() {
        return List.of(GEVECHT, BAAS, DICHT, GEWONNEN, VONDST, RUST, OPGELOST, MISLUKT, BEGIN, KLAAR,
                THUIS, WOUD, TEMPEL, VEEN, HAVEN, ZEE, BRON, HINT, OPDRACHT, ZOUTKOM, DUINEN, VUURTOREN, DORP,
                WAD, GETIJDENTEMPEL, ZOUTBAARD, IJSHOEVE, SNEEUWSTORM, WACHTERSPOST, BERGPAD, IJSPALEIS, GRIMMEL,
                SINTELGAT, MIJNEN, GLOED, SMIDSE, ASGRIM, NACHTROTS, MUREN, KERKERS, NACHTWACHT, GEZIEN);
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
        Afspeel lus, achter;
        Lied volgende;                                          // achtergrond die op de volgende maatstreep begint
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

    /**
     * Zet de achtergrond van deze speler (null = geen). Een ander lied begint op de volgende maatstreep van het
     * huidige (dan loopt het mooi door); zonder lopende achtergrond meteen.
     */
    void achter(Player p, Lied l) {
        Speler s = spelers.computeIfAbsent(p.getUniqueId(), k -> new Speler());
        if (l == null) {
            s.achter = null;
            s.volgende = null;
        } else if (s.achter == null) s.achter = new Afspeel(l);
        else if (s.achter.lied != l) s.volgende = l;
        else s.volgende = null;
    }

    /** De achtergrond die nu speelt (of zo meteen begint). */
    Lied achter(Player p) {
        Speler s = spelers.get(p.getUniqueId());
        if (s == null) return null;
        if (s.volgende != null) return s.volgende;
        return s.achter == null ? null : s.achter.lied;
    }

    /** Een los deuntje erbovenop. */
    void speel(Player p, Lied l) {
        spelers.computeIfAbsent(p.getUniqueId(), k -> new Speler()).los.add(new Afspeel(l));
    }

    /** Speelt er iets dat níet de achtergrond is (lus of deuntje)? */
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
            else if (s.achter != null) {
                if (s.volgende != null && s.achter.wacht == 0 && s.achter.stap % s.achter.lied.maat() == 0) {
                    s.achter = new Afspeel(s.volgende);
                    s.volgende = null;
                }
                stap(p, s.achter, demp);
            }
            s.los.removeIf(a -> !stap(p, a, 1f));
            if (s.lus == null && s.achter == null && s.los.isEmpty()) it.remove();
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
