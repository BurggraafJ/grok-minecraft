package nl.burggraaf.mc.zelda;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * De toren van Nachtrots (eiland 5b): Morgals drie fases (hoeveel een klap doet), een duistere bol terugslaan, de trappen van
 * licht, en het terrein (zee om de torenrots, twee pijlers onder de nevelbrug, het plateau waar de brug aankomt).
 */
class ZeldaTorenTest {

    @Test
    void morgalsNevelBeschermtHem() {
        assertEquals(0.2, ZeldaRegels.morgalSchade(10, ZeldaRegels.MORGAL_GEESTEN, false, true), 1e-9, "fase 1: zijn nevel houdt bijna alles tegen, ook licht");
        assertEquals(0.2, ZeldaRegels.morgalSchade(10, ZeldaRegels.MORGAL_ADEM, true, true), 1e-9, "tussen de fases: even niets");
        assertEquals(1.2, ZeldaRegels.morgalSchade(10, ZeldaRegels.MORGAL_BOLLEN, false, false), 1e-9, "fase 2: een gewone klap doet weinig");
        assertEquals(10, ZeldaRegels.morgalSchade(10, ZeldaRegels.MORGAL_BOLLEN, true, false), 1e-9, "fase 2, versuft: alles");
        assertTrue(ZeldaRegels.morgalSchade(10, ZeldaRegels.MORGAL_BOLLEN, false, true) > ZeldaRegels.morgalSchade(10, ZeldaRegels.MORGAL_BOLLEN, false, false),
                "een lichtpijl prikt iets meer");
        assertEquals(10, ZeldaRegels.morgalSchade(10, ZeldaRegels.MORGAL_HART, false, true), 1e-9, "fase 3: licht komt door zijn nevelhart");
        assertEquals(1.2, ZeldaRegels.morgalSchade(10, ZeldaRegels.MORGAL_HART, false, false), 1e-9, "fase 3: een zwaard niet");
        assertEquals(10, ZeldaRegels.morgalSchade(10, ZeldaRegels.MORGAL_HART, true, false), 1e-9, "fase 3, neergeploft: het zwaard wel");
    }

    @Test
    void eenBolTerugslaan() {
        double[] oog = {0, 1.6, 0}, kijk = {0, 0, 1};                            // je kijkt naar +z
        double r = ZeldaRegels.ZWAAI_BEREIK, h = ZeldaRegels.ZWAAI_HOEK;
        assertTrue(ZeldaRegels.terugslaan(oog, kijk, new double[]{0, 1.6, 2.5}, r, h), "recht voor je");
        assertTrue(ZeldaRegels.terugslaan(oog, kijk, new double[]{1.5, 1.2, 2.0}, r, h), "een beetje opzij en lager");
        assertFalse(ZeldaRegels.terugslaan(oog, kijk, new double[]{0, 1.6, 5}, r, h), "te ver");
        assertFalse(ZeldaRegels.terugslaan(oog, kijk, new double[]{0, 1.6, -2.5}, r, h), "achter je");
        assertTrue(ZeldaRegels.terugslaan(oog, kijk, new double[]{0.5, 1.6, -0.5}, r, h), "heel dichtbij: altijd (kinderen zwaaien niet precies)");
    }

    @Test
    void eenTrapVanLicht() {
        List<int[]> t = ZeldaRegels.trap(10, 5, 20, 0, -1, 10, 2);                  // naar het noorden, twee breed
        assertEquals(20, t.size());
        Set<String> gezien = new HashSet<>();
        for (int[] c : t) assertTrue(gezien.add(c[0] + "," + c[1] + "," + c[2]), "geen dubbele treden");
        for (int k = 0; k < 10; k++) {
            int[] links = t.get(2 * k), rechts = t.get(2 * k + 1);
            assertEquals(5 + k, links[1], "elke trede een hoger");
            assertEquals(20 - k, links[2], "elke trede een verder");
            assertEquals(links[1], rechts[1]);
            assertEquals(links[0] + 1, rechts[0], "naar het noorden lopen: rechts is oost");
        }
        int[] o = ZeldaRegels.trap(0, 0, 0, 1, 0, 1, 2).get(1);
        assertArrayEquals(new int[]{0, 0, 1}, o, "naar het oosten lopen: rechts is zuid");
    }

    @Test
    void deTorenrots() {
        // de rand van het vak is zee, de torenrots is plateau (P = G + 14), de brug komt aan op het plateau
        for (int z = ZeldaEiland5b.Z1; z <= ZeldaEiland5b.Z2; z += 15) {
            assertEquals(0, ZeldaEiland5b.vorm(ZeldaEiland5b.X2, z)[1], "x 2000: zee op z " + z);
            assertTrue(ZeldaEiland5b.vorm(ZeldaEiland5b.X2, z)[0] < 0, "zee: onder G");
        }
        for (int z = -60; z <= 60; z += 20) assertEquals(0, ZeldaEiland5b.vorm(1890, z == 0 ? 10 : z)[1], "tussen de burcht en de toren: zee");
        for (int x = ZeldaEiland5b.CX - 22; x <= ZeldaEiland5b.CX + 22; x++)
            for (int z = -22; z <= 22; z++)
                if (Math.hypot(x - ZeldaEiland5b.CX, z) <= 24) assertEquals(14, ZeldaEiland5b.vorm(x, z)[0], "plateau rond de toren op " + x + "," + z);
        for (int x = 1909; x <= 1914; x++) assertEquals(14, ZeldaEiland5b.vorm(x, 0)[0], "waar de brug aankomt: plateau");
        for (int[] p : ZeldaEiland5b.PIJLERS)
            for (int x = p[0]; x <= p[1]; x++) {
                assertTrue(ZeldaEiland5b.pijler(x, 0));
                assertEquals(14, ZeldaEiland5b.vorm(x, 0)[0], "pijler op de hoogte van de brug");
            }
        // de brug: drie stukken, met de pijlers ertussen, van de klif van 5a (x 1872) tot het plateau
        assertEquals(1873, ZeldaEiland5b.BRUG[0][0]);
        for (int i = 0; i + 1 < ZeldaEiland5b.BRUG.length; i++) {
            assertEquals(ZeldaEiland5b.BRUG[i][1] + 1, ZeldaEiland5b.PIJLERS[i][0], "brugstuk " + i + " komt aan op een pijler");
            assertEquals(ZeldaEiland5b.PIJLERS[i][1] + 1, ZeldaEiland5b.BRUG[i + 1][0], "pijler " + i + " gaat verder met een brugstuk");
        }
        int laatste = ZeldaEiland5b.BRUG[ZeldaEiland5b.BRUG.length - 1][1];
        assertEquals(14, ZeldaEiland5b.vorm(laatste + 1, 0)[0], "na het laatste brugstuk: het plateau");
        for (int[] s : ZeldaEiland5b.BRUG) assertEquals(0, ZeldaEiland5b.vorm((s[0] + s[1]) / 2 < 1880 ? 1880 : (s[0] + s[1]) / 2, 0)[1], "onder de brug: zee");
    }
}
