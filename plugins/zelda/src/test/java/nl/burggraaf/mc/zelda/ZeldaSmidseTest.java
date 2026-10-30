package nl.burggraaf.mc.zelda;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** De Smidse (eiland 4b): Asgrims vlammenschild, de berg boven de zalen, de kuipen boven de lava. */
class ZeldaSmidseTest {

    @Test
    void asgrimSchade() {
        assertEquals(1.0, ZeldaRegels.asgrimSchade(10, false), 1e-9, "door zijn vlammenschild komt bijna niets");
        assertEquals(10, ZeldaRegels.asgrimSchade(10, true), 1e-9, "gedoofd: gewoon alles");
    }

    /**
     * Boven elke zaal van de Smidse is de berg hoger dan het dak (anders zie je de lucht, of loop je er van buiten in).
     * Hoogtes boven G: de Smidse ligt op G+2.
     */
    @Test
    void bergBovenDeSmidse() {
        int[][] zalen = {                                       // x1, z1, x2, z2, hoogste blok van het dak (boven G)
                {1259, 271, 1287, 297, 16},                         // de Smidsehal
                {1259, 245, 1295, 269, 11},                         // de Kettingzaal
                {1297, 246, 1331, 273, 12},                         // de Stuwdam
                {1311, 239, 1321, 246, 8},                          // de waterkelder
                {1287, 281, 1297, 287, 8},                          // de gang naar de Gietzaal
                {1297, 275, 1332, 301, 14},                         // de Gietzaal
                {1270, 298, 1276, 320, 9},                          // de Hartgang
                {1269, 319, 1295, 344, 10},                         // de voorkamer en de nis
                {1297, 315, 1327, 345, 19},                         // het Hart van de Smidse
                {1327, 323, 1339, 337, 7},                          // de voet van de schoorsteen
                {1330, 327, 1336, 333, 54},                         // de schoorsteen
        };
        for (int[] z : zalen)
            for (int x = z[0]; x <= z[2]; x++)
                for (int zz = z[1]; zz <= z[3]; zz++)
                    assertTrue(ZeldaEiland4b.hoogte(x, zz) >= z[4] + 2, "de berg is te laag boven " + x + "," + zz + ": " + ZeldaEiland4b.hoogte(x, zz) + " (dak " + z[4] + ")");
    }

    @Test
    void kraterwand() {
        // rond de top is de berg een flinke stap hoger: van buiten klim je er niet op
        double binnen = ZeldaEiland4b.hoogte(ZeldaEiland4b.CX + 29, ZeldaEiland4b.CZ), buiten = ZeldaEiland4b.hoogte(ZeldaEiland4b.CX + 30, ZeldaEiland4b.CZ);
        assertTrue(binnen - buiten >= 4, "geen wand rond de top: " + binnen + " → " + buiten);
        assertEquals(50, ZeldaEiland4b.hoogte(ZeldaEiland4b.CX, ZeldaEiland4b.CZ), 1e-9, "de kratervloer is vlak");
        for (int x = ZeldaEiland4b.X1; x <= ZeldaEiland4b.X2; x++) {
            assertTrue(ZeldaEiland4a.land(x, ZeldaEiland4b.Z1 + 2) < 0, "zee aan de noordrand, x " + x);
            assertTrue(ZeldaEiland4a.land(x, ZeldaEiland4b.Z2 - 2) < 0, "zee aan de zuidrand, x " + x);
        }
    }

    @Test
    void kuipenBovenDeLava() {
        for (int[] k : ZeldaEiland4b.KUIPEN) {
            double zweef = Math.hypot(k[0] - ZeldaEiland4b.CX, k[1] - ZeldaEiland4b.CZ), land = Math.hypot(k[2] - ZeldaEiland4b.CX, k[3] - ZeldaEiland4b.CZ);
            assertTrue(zweef > 2.5 && zweef < 7.5, "Asgrim zweeft boven de lava (niet boven het eilandje of de rand): " + zweef);
            assertTrue(land > 8 && land < 13, "hij ploft neer op de rand: " + land);
            assertTrue(Math.hypot(k[0] - k[2], k[1] - k[3]) > Asgrim.ONDER, "neergeploft hangt hij niet meer onder de kuip");
        }
        assertTrue(Asgrim.SINTEL * ZeldaRegels.SCHADE <= 4, "een sintel doet hoogstens twee harten (voor het harnas)");
    }
}
