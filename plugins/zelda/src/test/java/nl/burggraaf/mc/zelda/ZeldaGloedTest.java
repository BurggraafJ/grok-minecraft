package nl.burggraaf.mc.zelda;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** De Gloedberg (eiland 4a): de hitte in de mijn, en de berg die dicht blijft boven de gangen. */
class ZeldaGloedTest {

    private static final double SNEL = 0.45, KOEL = 0.11;

    private static double stap(double oud, boolean in, boolean fel, boolean koelsteen, boolean harnas, boolean koelplek) {
        return ZeldaRegels.hitte(oud, 0.5, in, SNEL, KOEL, fel, koelsteen, harnas, koelplek);
    }

    @Test
    void hitte() {
        assertEquals(SNEL * 0.5, stap(0, true, false, false, false, false), 1e-9, "zonder koelsteen: snel warm");
        assertEquals(KOEL * 0.5, stap(0, true, false, true, false, false), 1e-9, "met de koelsteen: langzaam");
        assertEquals(0, stap(0, true, false, true, true, false), 1e-9, "het gloedharnas houdt de hitte tegen");
        assertEquals(0.5 - ZeldaRegels.AFKOELEN * 0.5, stap(0.5, true, false, false, false, true), 1e-9, "in water koel je af");
        assertEquals(0.5 - ZeldaRegels.AFKOELEN * 0.5, stap(0.5, false, false, false, false, false), 1e-9, "buiten de hitte koel je af");
        assertEquals(1, stap(0.95, true, false, false, false, false), 1e-9, "nooit meer dan vol");
        assertEquals(0, stap(0.1, false, false, false, false, false), 1e-9, "nooit minder dan niets");
        // de felle gloed: de koelsteen en water helpen niet, alleen het harnas
        assertEquals(0.5 + SNEL * 0.5, stap(0.5, true, true, true, false, true), 1e-9);
        assertEquals(0.5 - ZeldaRegels.AFKOELEN * 0.5, stap(0.5, true, true, false, true, false), 1e-9);
        // met de koelsteen haal je een koelplek op een paar seconden lopen (zo is de mijn gebouwd: hoogstens ±25 blokken ertussen)
        double h = 0;
        int tellen = 0;
        while (h < 1 && tellen < 100) {
            h = stap(h, true, false, true, false, false);
            tellen++;
        }
        assertTrue(tellen / 2.0 >= 8, "met de koelsteen te snel te heet: " + tellen / 2.0 + " s");
        h = 0;
        tellen = 0;
        while (h < 1 && tellen < 100) {
            h = stap(h, true, false, false, false, false);
            tellen++;
        }
        assertTrue(tellen / 2.0 <= 3, "zonder koelsteen hou je het te lang uit: " + tellen / 2.0 + " s");
    }

    @Test
    void gloedharnasIsHarnas3() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        assertFalse(ZeldaRegels.hittevast(s));
        s.zet("harnas2");
        assertFalse(ZeldaRegels.hittevast(s));
        s.zet(ZeldaEiland4a.GLOEDHARNAS);
        assertTrue(ZeldaRegels.hittevast(s));
        assertEquals("Gloedharnas", ZeldaRegels.HARNAS_NAAM[ZeldaRegels.harnas(s)]);
    }

    /**
     * Boven elke zaal van de mijn is de berg hoger dan het plafond (anders zie je de lucht door het dak). Hoogtes boven G:
     * de bovenmijn ligt op G+2, de hete gangen op G−5.
     */
    @Test
    void bergBovenDeMijn() {
        int[][] zalen = {                                       // x1, z1, x2, z2, hoogste blok van het dak (boven G)
                {1172, 328, 1185, 332, 7},                          // de Mijningang (de eerste meters zitten in de wand)
                {1185, 318, 1207, 342, 11},                         // de Lorriehal
                {1193, 296, 1199, 319, 7},                          // de Oude Gang
                {1187, 258, 1207, 297, 12},                         // de Schachtput en de Vonkkamer
                {1215, 261, 1237, 314, 0},                          // de hete gangen
                {1221, 313, 1239, 333, 4},                          // de Gloedader
                {1239, 295, 1257, 331, 12},                         // de Askamer en de gloedtrap
                {1239, 273, 1259, 295, 15},                         // de Smidsepoort
                {1239, 246, 1245, 273, 7},                          // de Asgang (het laatste stuk komt naar buiten)
                {1190, 341, 1202, 365, 8},                          // de Zijgang en de koelkelder
        };
        for (int[] z : zalen)
            for (int x = z[0]; x <= z[2]; x++)
                for (int zz = z[1]; zz <= z[3]; zz++)
                    assertTrue(ZeldaEiland4a.berg(x, zz) >= z[4] + 1.5, "de berg is te laag boven " + x + "," + zz + ": " + ZeldaEiland4a.berg(x, zz) + " (dak " + z[4] + ")");
    }

    @Test
    void eilandEnZee() {
        assertTrue(ZeldaEiland4a.land(ZeldaEiland4a.PX, ZeldaEiland4a.PZ) > 20, "Sintelgat ligt op het land");
        assertTrue(ZeldaEiland4a.land(1070, 336) < 0, "de Zwarte Baai is water (daar ligt het schip)");
        assertTrue(ZeldaEiland4a.land(1134, 215) < 0, "voor de Laadkade is water");
        assertTrue(ZeldaEiland4a.land(1134, 232) > 0, "de Laadkade ligt aan land");
        for (int x = ZeldaEiland4a.X1; x <= ZeldaEiland4a.VX2; x++) {
            assertTrue(ZeldaEiland4a.land(x, ZeldaEiland4a.Z1 + 2) < 0, "zee aan de noordrand van het vak, x " + x);
            assertTrue(ZeldaEiland4a.land(x, ZeldaEiland4a.Z2 - 2) < 0, "zee aan de zuidrand van het vak, x " + x);
        }
        for (int z = ZeldaEiland4a.Z1; z <= ZeldaEiland4a.Z2; z++) {
            assertTrue(ZeldaEiland4a.land(ZeldaEiland4a.X1 + 1, z) < 0, "zee aan de westrand, z " + z);
            assertTrue(ZeldaEiland4a.land(ZeldaEiland4a.VX2 - 2, z) < 0, "zee aan de oostrand (4b), z " + z);
        }
        assertTrue(ZeldaEiland4a.berg(1312, 330) > 40, "de vulkaan steekt boven alles uit");
    }
}
