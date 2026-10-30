package nl.burggraaf.mc.zelda;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** De vier niveaus: alleen de steun voor de speler verschilt, nooit wat je moet doen. */
class ZeldaNiveauTest {

    private static ZeldaVoortgang.Stand op(ZeldaRegels.Niveau n) {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        s.niveau = n.ordinal();
        return s;
    }

    @Test
    void avontuurIsDeOudeBalans() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        assertEquals(ZeldaRegels.Niveau.AVONTUUR, ZeldaRegels.niveau(s), "een nieuwe (en oude) stand begint op Avontuur");
        assertEquals(ZeldaRegels.SCHADE, ZeldaRegels.schade(s), 1e-9);
        assertArrayEquals(new long[]{6_000, 2_000}, ZeldaRegels.bijkomen(s, false), "op adem komen zoals in 2.15");
        assertNull(ZeldaRegels.bijkomen(s, true), "in een gevecht niet");
        assertEquals(ZeldaRegels.FLES_HP, ZeldaRegels.flesHp(s), 1e-9);
        assertEquals(ZeldaRegels.GOLF_PAUZE, ZeldaRegels.golfPauze(List.of(s)));
        assertEquals(0, ZeldaRegels.schildje(s, 0));
        s.zet("fles2");
        assertEquals(1, ZeldaRegels.flessen(s));
    }

    @Test
    void elkNiveauZwaarderDanHetVorige() {
        ZeldaRegels.Niveau[] n = ZeldaRegels.Niveau.values();
        assertEquals(4, n.length);
        for (int i = 1; i < n.length; i++) {
            assertTrue(n[i].schade > n[i - 1].schade, n[i].naam + ": meer schade dan " + n[i - 1].naam);
            assertTrue(n[i].hartKans < n[i - 1].hartKans, n[i].naam + ": minder hartjes");
            assertTrue(n[i].na >= n[i - 1].na && n[i].elk >= n[i - 1].elk, n[i].naam + ": niet sneller op adem");
            assertTrue(n[i].schild + n[i].hulp * ZeldaRegels.HULP_KEER <= n[i - 1].schild + n[i - 1].hulp * ZeldaRegels.HULP_KEER, n[i].naam + ": niet meer schildjes");
            assertTrue(n[i].flessen <= n[i - 1].flessen && n[i].fles <= n[i - 1].fles, n[i].naam + ": niet meer flesjes");
            assertEquals(2, n[i].uitleg.length, "twee korte regels in het menu");
        }
        for (ZeldaRegels.Niveau x : n) for (String r : x.uitleg) assertTrue(r.length() <= 32, "kort: " + r);
    }

    @Test
    void verhaalHelptVeel() {
        ZeldaVoortgang.Stand s = op(ZeldaRegels.Niveau.VERHAAL);
        assertEquals(ZeldaRegels.SCHADE * 0.5, ZeldaRegels.schade(s), 1e-9);
        assertNotNull(ZeldaRegels.bijkomen(s, true), "op Verhaal kom je ook in een gevecht op adem");
        assertEquals(4, ZeldaRegels.schildje(s, 0), "een schildje van licht bij elk gevecht");
        assertEquals(0, ZeldaRegels.flessen(s), "zonder flesje ook geen extra");
        s.zet("fles2");
        assertEquals(3, ZeldaRegels.flessen(s), "twee extra flesjes");
        assertTrue(ZeldaRegels.vulFlessen(s));
        assertEquals(3, ZeldaRegels.vol(s));
        assertEquals(12, ZeldaRegels.flesHp(s), 1e-9, "zes harten per flesje");
        ZeldaVoortgang.Stand avontuur = new ZeldaVoortgang.Stand();
        assertEquals(6_000, ZeldaRegels.golfPauze(List.of(avontuur, s)), "de meeste hulp in de kamer bepaalt de adem");
    }

    @Test
    void hulpNaDoodgaan() {
        assertEquals(2, ZeldaRegels.schildje(op(ZeldaRegels.Niveau.AVONTUUR), 1));
        assertEquals(6, ZeldaRegels.schildje(op(ZeldaRegels.Niveau.AVONTUUR), 7), "hoogstens drie keer");
        assertEquals(2, ZeldaRegels.schildje(op(ZeldaRegels.Niveau.HELD), 2));
        assertEquals(0, ZeldaRegels.schildje(op(ZeldaRegels.Niveau.LEGENDE), 3), "Legende: geen hulp");
        assertEquals(10, ZeldaRegels.schildje(op(ZeldaRegels.Niveau.VERHAAL), 3));
    }

    @Test
    void steunVanBeheer() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        s.steun = 1;
        assertEquals(ZeldaRegels.SCHADE * 0.85, ZeldaRegels.schade(s), 1e-9);
        s.zet("fles1");
        assertEquals(2, ZeldaRegels.flessen(s), "steun omhoog: een flesje extra");
        s.steun = -9;
        assertEquals(-2, ZeldaRegels.steun(s), "hoogstens twee stappen");
        assertEquals(ZeldaRegels.SCHADE * 1.3, ZeldaRegels.schade(s), 1e-9);
        assertEquals(1, ZeldaRegels.flessen(s), "steun omlaag pakt geen flesjes af");
        s.niveau = 17;
        assertEquals(ZeldaRegels.Niveau.AVONTUUR, ZeldaRegels.niveau(s), "iets vreemds = Avontuur");
    }

    @Test
    void niveauOpNaam() {
        assertEquals(ZeldaRegels.Niveau.HELD, ZeldaRegels.Niveau.van("held"));
        assertEquals(ZeldaRegels.Niveau.LEGENDE, ZeldaRegels.Niveau.van("Leg"));
        assertEquals(ZeldaRegels.Niveau.VERHAAL, ZeldaRegels.Niveau.van("v"));
        assertNull(ZeldaRegels.Niveau.van("makkelijk"));
        assertNull(ZeldaRegels.Niveau.van(""));
    }

    @Test
    void samenNetIetsZwaarder() {
        var Z = ZeldaHoofdstuk.Soort.VEENLOPER;
        var S = ZeldaHoofdstuk.Soort.SPIN;
        assertEquals(List.of(Z, S), ZeldaRegels.samen(List.of(Z, S), 1, null, null), "alleen: de golf zoals hij is");
        assertEquals(List.of(Z, S, Z), ZeldaRegels.samen(List.of(Z, S), 2, null, null), "per extra speler één erbij");
        assertEquals(List.of(Z, S, Z, S, Z), ZeldaRegels.samen(List.of(Z, S), 4, null, null));
        assertEquals(ZeldaRegels.GOLF_MAX, ZeldaRegels.samen(List.of(Z, S, Z, S, Z, S, Z), 5, null, null).size(), "hoogstens acht");
        assertEquals(List.of(Z, S), ZeldaRegels.samen(List.of(Z), 2, Z, S), "een baas roept een helper, geen tweede baas");
        assertEquals(60 * ZeldaRegels.BAAS_LEVEN, ZeldaRegels.baasLeven(60, 30, 1), 1e-9);
        assertEquals(120 * ZeldaRegels.BAAS_LEVEN, ZeldaRegels.baasLeven(60, 30, 3), 1e-9);
        assertEquals(0, ZeldaRegels.extraHelpers(2));
        assertEquals(1, ZeldaRegels.extraHelpers(3));
    }

    @Test
    void sterrenEnHeldenrand() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        assertNull(ZeldaRegels.ster(s, "veen"));
        assertTrue(ZeldaRegels.ster(s, "veen", ZeldaRegels.Niveau.AVONTUUR));
        assertFalse(ZeldaRegels.ster(s, "veen", ZeldaRegels.Niveau.VERHAAL), "lager winnen pakt geen ster af");
        assertTrue(ZeldaRegels.ster(s, "veen", ZeldaRegels.Niveau.HELD));
        assertEquals(ZeldaRegels.Niveau.HELD, ZeldaRegels.ster(s, "veen"));
        assertEquals(1, ZeldaRegels.sterren(s, List.of("veen", "kade"), ZeldaRegels.Niveau.HELD));
        assertEquals(0, ZeldaRegels.sterren(s, List.of("veen", "kade"), ZeldaRegels.Niveau.LEGENDE));
        List<String> bazen = List.of("heksenkring", "draaikolk");
        assertNull(ZeldaRegels.heldenrand(s, bazen));
        ZeldaRegels.ster(s, "heksenkring", ZeldaRegels.Niveau.LEGENDE);
        ZeldaRegels.ster(s, "draaikolk", ZeldaRegels.Niveau.HELD);
        assertEquals(ZeldaRegels.Niveau.HELD, ZeldaRegels.heldenrand(s, bazen), "alle bazen op Held of hoger");
        ZeldaRegels.ster(s, "draaikolk", ZeldaRegels.Niveau.LEGENDE);
        assertEquals(ZeldaRegels.Niveau.LEGENDE, ZeldaRegels.heldenrand(s, bazen));
        assertNull(ZeldaRegels.heldenrand(s, List.of()));
    }
}
