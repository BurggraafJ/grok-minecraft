package nl.burggraaf.mc.kitpvp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ZeldaBoxTest {

    @Test
    void binnenEnBuiten() {
        ZeldaHoofdstuk.Box b = ZeldaHoofdstuk.Box.van(10, 0, 5, 2, 3, -5);
        assertEquals(new ZeldaHoofdstuk.Box(2, 0, -5, 10, 3, 5), b, "hoeken worden gesorteerd");
        assertTrue(b.in(2.0, 0.0, -5.0));
        assertTrue(b.in(10.99, 3.5, 5.99), "het hele blok telt mee");
        assertFalse(b.in(11.0, 1, 0));
        assertFalse(b.in(5, -0.01, 0));
        assertTrue(b.groter(1).in(11.5, 1, 0));
        assertTrue(new ZeldaHoofdstuk.Box(3, 1, -4, 9, 2, 4).binnen(b));
        assertFalse(b.groter(1).binnen(b));
    }

    @Test
    void deurOmvang() {
        ZeldaHoofdstuk.Deur d = new ZeldaHoofdstuk.Deur(null, null).vlak(5, 1, -1, 5, 3, 1);
        assertEquals(9, d.blokken.size());
        assertEquals(new ZeldaHoofdstuk.Box(5, 1, -1, 5, 3, 1), d.omvang());
    }

    @Test
    void gebiedVanHoofdstuk1() {
        // het pad (rx -8 … 492) ligt helemaal in het gereserveerde gebied
        assertTrue(ZeldaHoofdstuk1.X0 - 8 >= Zelda.GEBIED[0]);
        assertTrue(ZeldaHoofdstuk1.X0 + 492 <= Zelda.GEBIED[2]);
    }
}
