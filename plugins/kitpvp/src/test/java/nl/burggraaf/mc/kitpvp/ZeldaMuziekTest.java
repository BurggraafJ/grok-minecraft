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
        assertFalse(ZeldaMuziek.VONDST.lus());
    }
}
