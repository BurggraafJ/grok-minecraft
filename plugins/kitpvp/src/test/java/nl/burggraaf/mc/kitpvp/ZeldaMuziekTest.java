package nl.burggraaf.mc.kitpvp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ZeldaMuziekTest {

    @Test
    void noten() {
        assertEquals(0, ZeldaMuziek.noot("f#3"));
        assertEquals(12, ZeldaMuziek.noot("f#4"));
        assertEquals(24, ZeldaMuziek.noot("f#5"));
        assertEquals(8, ZeldaMuziek.noot("d4"));
        assertEquals(12, ZeldaMuziek.noot("x"));
        assertThrows(IllegalArgumentException.class, () -> ZeldaMuziek.noot("f3"), "onder het nootblokbereik");
        assertThrows(IllegalArgumentException.class, () -> ZeldaMuziek.noot("g5"), "boven het nootblokbereik");
        assertThrows(IllegalArgumentException.class, () -> ZeldaMuziek.noot("h4"));
        assertEquals(0.5f, ZeldaMuziek.toon(0), 1e-6);
        assertEquals(1.0f, ZeldaMuziek.toon(12), 1e-6);
        assertEquals(2.0f, ZeldaMuziek.toon(24), 1e-6);
    }

    @Test
    void spoorLezen() {
        ZeldaMuziek.Spoor s = ZeldaMuziek.spoor(ZeldaMuziek.Instr.HARP, 0.5f, "d4 . | a4+d5 x");
        assertEquals(4, s.stappen().length);
        assertArrayEquals(new int[]{8}, s.stappen()[0]);
        assertNull(s.stappen()[1]);
        assertArrayEquals(new int[]{15, 20}, s.stappen()[2]);
        assertArrayEquals(new int[]{12}, s.stappen()[3]);
    }

    @Test
    void alleLiedjesKloppen() {
        for (ZeldaMuziek.Lied l : ZeldaMuziek.alle()) {
            assertTrue(l.lengte() > 0, l.id());
            assertTrue(l.tik() >= 1, l.id());
            for (ZeldaMuziek.Spoor s : l.sporen()) {
                if (l.lus()) assertEquals(l.lengte(), s.stappen().length, l.id() + ": sporen van een lus even lang");
                assertTrue(s.volume() > 0 && s.volume() <= 1, l.id());
            }
        }
        assertTrue(ZeldaMuziek.GEVECHT.lus());
        assertTrue(ZeldaMuziek.BAAS.lus());
        assertTrue(ZeldaMuziek.ZOUTBAARD.lus());
        assertTrue(ZeldaMuziek.GRIMMEL.lus());
        assertTrue(ZeldaMuziek.ASGRIM.lus());
        assertTrue(ZeldaMuziek.NACHTWACHT.lus());
        for (ZeldaMuziek.Lied l : java.util.List.of(ZeldaMuziek.MORGAL1, ZeldaMuziek.MORGAL2, ZeldaMuziek.MORGAL3)) assertTrue(l.lus(), l.id());
        assertFalse(ZeldaMuziek.DAGERAAD.lus());
        assertFalse(ZeldaMuziek.GEZIEN.lus());
        assertEquals(6, ZeldaMuziek.ZOUTBAARD.maat());
        assertFalse(ZeldaMuziek.VONDST.lus());
    }

    @Test
    void maatUitDeMaatstreep() {
        assertEquals(3, ZeldaMuziek.spoor(ZeldaMuziek.Instr.HARP, 0.5f, "d4 . . | a4 . .").maat());
        assertEquals(8, ZeldaMuziek.spoor(ZeldaMuziek.Instr.HARP, 0.5f, "d4 . a4").maat(), "zonder maatstreep: 8");
        assertEquals(6, ZeldaMuziek.HAVEN.maat());
        assertEquals(6, ZeldaMuziek.ZEE.maat());
        assertEquals(8, ZeldaMuziek.WOUD.maat());
    }

    @Test
    void achtergrondIsAltijdEenLangeLus() {
        for (ZeldaMuziek.Lied l : java.util.List.of(ZeldaMuziek.THUIS, ZeldaMuziek.WOUD, ZeldaMuziek.TEMPEL, ZeldaMuziek.VEEN, ZeldaMuziek.HAVEN, ZeldaMuziek.ZEE,
                ZeldaMuziek.ZOUTKOM, ZeldaMuziek.DUINEN, ZeldaMuziek.VUURTOREN, ZeldaMuziek.DORP, ZeldaMuziek.WAD, ZeldaMuziek.GETIJDENTEMPEL,
                ZeldaMuziek.IJSHOEVE, ZeldaMuziek.SNEEUWSTORM, ZeldaMuziek.WACHTERSPOST, ZeldaMuziek.BERGPAD, ZeldaMuziek.IJSPALEIS,
                ZeldaMuziek.SINTELGAT, ZeldaMuziek.MIJNEN, ZeldaMuziek.GLOED, ZeldaMuziek.SMIDSE,
                ZeldaMuziek.NACHTROTS, ZeldaMuziek.MUREN, ZeldaMuziek.KERKERS, ZeldaMuziek.TOREN, ZeldaMuziek.FEEST)) {
            assertTrue(l.lus(), l.id() + " moet een lus zijn (nooit stilte)");
            assertEquals(0, l.lengte() % l.maat(), l.id() + ": hele maten (wisselen gebeurt op een maatstreep)");
            assertTrue(l.lengte() * l.tik() >= 20 * 14, l.id() + ": te kort, dan wordt hij snel saai (" + l.lengte() * l.tik() / 20.0 + " s)");
            for (ZeldaMuziek.Spoor s : l.sporen()) assertTrue(s.volume() <= 0.3f, l.id() + ": achtergrond hoort zacht te zijn");
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ZeldaMuziek.Lied l : ZeldaMuziek.alle()) assertTrue(ids.add(l.id()), "dubbel lied-id " + l.id());
    }
}
