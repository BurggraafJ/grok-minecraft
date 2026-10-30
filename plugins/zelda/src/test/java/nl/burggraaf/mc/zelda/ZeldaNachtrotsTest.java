package nl.burggraaf.mc.zelda;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Nachtrots (eiland 5a): de regels van het sluipen (de stand van een wachter, zijn lichtkegel, kun je een stuk pad
 * ongezien lopen), het terrein, en een model van de weergang en de cellengang (de blokken die het licht tegenhouden): elk
 * stuk tussen twee schuilplekken moet op een flink deel van het ritme ongezien te halen zijn, en een schuilplek ziet niemand.
 */
class ZeldaNachtrotsTest {

    @Test
    void standVanEenWachter() {
        double[][] st = {{0.0, 0, 0, 0, 0}, {0.5, 10, 0, 0, 0}, {0.75, 10, 0, 0, 180}};
        ZeldaRegels.Pose p = ZeldaRegels.pose(st, 0.25);
        assertEquals(5, p.x(), 1e-9, "halverwege de eerste twee standen");
        assertEquals(0, p.yaw(), 1e-9);
        assertEquals(10, ZeldaRegels.pose(st, 0.5).x(), 1e-9);
        assertEquals(90, Math.abs(ZeldaRegels.pose(st, 0.625).yaw()), 1e-9, "hij draait gelijkmatig");
        assertEquals(5, ZeldaRegels.pose(st, 0.875).x(), 1e-9, "na de laatste stand terug naar de eerste");
        assertEquals(ZeldaRegels.pose(st, 0.1).x(), ZeldaRegels.pose(st, 1.1).x(), 1e-9, "het ritme loopt rond");
        // met een fase begint hij elders in zijn ritme, maar loopt hij hetzelfde stuk
        double[][] a = ZeldaEiland5a.standen(true, 1770.5, 0, 31, 42, 0), b = ZeldaEiland5a.standen(true, 1770.5, 0, 31, 42, 0.25);
        for (double f = 0; f < 1; f += 0.05) assertEquals(ZeldaRegels.pose(a, f).z(), ZeldaRegels.pose(b, f + 0.25).z(), 1e-6, "fase " + f);
        for (double f = 0; f < 1; f += 0.01) {
            double z = ZeldaRegels.pose(a, f).z();
            assertTrue(z >= 31.5 - 1e-9 && z <= 42.5 + 1e-9, "hij blijft op zijn stuk: " + z);
        }
    }

    @Test
    void lichtkegel() {
        ZeldaRegels.Pose zuid = new ZeldaRegels.Pose(0, 0, 0, 0);               // kijkt naar +z
        assertTrue(ZeldaRegels.inKegel(zuid, 6, 32, 0, 0, 4), "recht vooruit");
        assertFalse(ZeldaRegels.inKegel(zuid, 6, 32, 0, 0, -4), "achter zijn rug");
        assertFalse(ZeldaRegels.inKegel(zuid, 6, 32, 0, 0, 7), "te ver");
        assertFalse(ZeldaRegels.inKegel(zuid, 6, 32, 3, 0, 3), "45 graden opzij: te ver opzij");
        assertTrue(ZeldaRegels.inKegel(zuid, 6, 32, 0.5, 0, 0.5), "vlak bij hem ziet hij je altijd");
        assertFalse(ZeldaRegels.inKegel(zuid, 6, 32, 0, 5, 3), "vijf hoger: niet");
        ZeldaRegels.Pose west = new ZeldaRegels.Pose(0, 0, 0, 90);              // kijkt naar −x
        assertTrue(ZeldaRegels.inKegel(west, 6, 32, -4, 0, 0));
        assertFalse(ZeldaRegels.inKegel(west, 6, 32, 4, 0, 0));
    }

    @Test
    void sluipenOpTijd() {
        boolean[][] g = new boolean[10][5];
        for (int c = 0; c < 5; c++) for (int t = 0; t < 3; t++) g[t][c] = true;    // de eerste drie tijdstappen ziet hij alles
        assertTrue(ZeldaRegels.sluipt(g, 0, 4, 4, 1), "na het licht: vrij");
        assertFalse(ZeldaRegels.sluipt(g, 0, 4, 0, 1), "in het licht");
        assertFalse(ZeldaRegels.sluipt(g, 0, 4, 2, 1), "net te vroeg (speling: een tijdstap ervoor telt mee)");
        assertTrue(ZeldaRegels.vensters(g, 0, 4, 1) > 0);
        int[] index = new int[3];
        List<int[]> c = ZeldaRegels.cellen(List.of(new int[]{0, 0, 0}, new int[]{3, 0, 0}, new int[]{3, 0, -2}), index);
        assertEquals(6, c.size());
        assertArrayEquals(new int[]{0, 3, 5}, index);
        assertNull(ZeldaRegels.cellen(List.of(new int[]{0, 0, 0}, new int[]{3, 0, 3}), null), "schuin kan niet");
    }

    @Test
    void terrein() {
        for (int x = ZeldaEiland5a.X1; x <= ZeldaEiland5a.X2; x++) {
            assertTrue(ZeldaEiland5a.land(x, ZeldaEiland5a.Z1 + 2) < 0, "zee aan de noordrand, x " + x);
            assertTrue(ZeldaEiland5a.land(x, ZeldaEiland5a.Z2 - 2) < 0, "zee aan de zuidrand, x " + x);
        }
        for (int z = ZeldaEiland5a.Z1; z <= ZeldaEiland5a.Z2; z++) assertTrue(ZeldaEiland5a.land(ZeldaEiland5a.X1 + 2, z) < 0, "zee aan de westrand, z " + z);
        for (int x = 1735; x <= 1872; x++)
            for (int z = -64; z <= 64; z++) assertTrue(ZeldaEiland5a.land(x, z) >= 9, "de burcht staat op het plateau: " + x + "," + z);
        assertTrue(ZeldaEiland5a.torenRots(ZeldaEiland5a.TCX, ZeldaEiland5a.TCZ) > 20, "de toren staat op zijn rots");
        assertTrue(ZeldaEiland5a.torenRots(1880, 0) < 0, "tussen de burcht en de toren: zee");
        assertTrue(ZeldaEiland5a.torenRots(ZeldaEiland5a.TX2 - 2, 0) < 0, "zee aan de oostrand van het vak van de toren");
    }

    @Test
    void lichtpijlenEnLichtharnas() {
        assertEquals("Lichtharnas", ZeldaRegels.HARNAS_NAAM[4]);
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        for (int i = 1; i <= 8; i++) s.zet("fles" + i);
        assertEquals(8, ZeldaRegels.flessen(s), "acht flesjes (het achtste: van maanglas, Hanne)");
        s.zet("harnas4");
        assertEquals(4, ZeldaRegels.harnas(s));
        assertTrue(ZeldaVoorwerp.LATER.contains("lichtpijlen"), "de nevelnis in de Smidse wacht op de lichtpijlen");
    }

    // ── een model van de sluipstukken: dezelfde wachters, de blokken die het licht tegenhouden ──

    private static long sleutel(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096);
    }

    private static void blok(Set<Long> vast, int x, int z, int hoog) {
        for (int y = 0; y < hoog; y++) vast.add(sleutel(x, y, z));
    }

    /** Ziet de wachter (zijn oog anderhalf boven zijn voeten) iemand op x, y, z: in de kegel, en een vrije lijn naar een van drie hoogtes? */
    private static boolean ziet(Set<Long> vast, ZeldaRegels.Pose po, double x, double y, double z) {
        if (!ZeldaRegels.inKegel(po, ZeldaEiland5a.BEREIK, ZeldaEiland5a.HOEK, x, y, z)) return false;
        double ox = po.x(), oy = po.y() + 1.5, oz = po.z();
        for (double h : new double[]{0.3, 1.0, 1.6}) {
            double dx = x - ox, dy = y + h - oy, dz = z - oz, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean vrij = true;
            for (double a = 0.05; a < d && vrij; a += 0.05)
                vrij = !vast.contains(sleutel((int) Math.floor(ox + dx * a / d), (int) Math.floor(oy + dy * a / d), (int) Math.floor(oz + dz * a / d)));
            if (vrij) return true;
        }
        return false;
    }

    /** Voor elk stuk tussen twee schuilplekken: op hoeveel procent van het ritme kom je ongezien over (en een schuilplek ziet niemand)? */
    private static List<Integer> kansen(Set<Long> vast, List<double[][]> wachters, List<int[]> pad, Set<Integer> schuil) {
        int[] index = new int[pad.size()];
        List<int[]> cel = ZeldaRegels.cellen(pad, index);
        assertNotNull(cel, "een recht pad");
        int stappen = (int) (ZeldaEiland5a.RITME / 100);
        boolean[][] g = new boolean[stappen][cel.size()];
        for (int t = 0; t < stappen; t++)
            for (int c = 0; c < cel.size(); c++)
                for (double[][] st : wachters) g[t][c] |= ziet(vast, ZeldaRegels.pose(st, t / (double) stappen), cel.get(c)[0] + 0.5, cel.get(c)[1], cel.get(c)[2] + 0.5);
        List<Integer> rust = new ArrayList<>();
        rust.add(0);
        for (int i = 1; i < pad.size(); i++) if (schuil.contains(i) || i == pad.size() - 1) rust.add(i);
        for (int i : rust) {
            if (i == pad.size() - 1) continue;
            for (int t = 0; t < stappen; t++) assertFalse(g[t][index[i]], "schuilplek " + pad.get(i)[0] + "," + pad.get(i)[2] + " wordt gezien op stap " + t);
        }
        List<Integer> l = new ArrayList<>();
        for (int r = 0; r + 1 < rust.size(); r++) l.add(100 * ZeldaRegels.vensters(g, index[rust.get(r)], index[rust.get(r + 1)], 3) / stappen);
        return l;
    }

    /** De weergang (y 0 = waar je staat): kantelen west, een borstwering oost, balkons met dichte zijkanten, de torens aan beide eind. */
    @Test
    void weergangIsTeSluipen() {
        Set<Long> vast = new HashSet<>();
        for (int x = 1764; x <= 1775; x++)
            if (x < 1769 || x > 1771) {
                blok(vast, x, 46, 4);
                blok(vast, x, -18, 4);
            }
        Set<Integer> balkon = new HashSet<>();
        for (int b : ZeldaEiland5a.BALKONS) balkon.add(b);
        for (int z = -17; z <= 45; z++) {
            blok(vast, 1768, z, (1768 + z) % 2 == 0 ? 2 : 1);
            if (!balkon.contains(z)) blok(vast, 1772, z, 1);
        }
        for (int b : ZeldaEiland5a.BALKONS) {
            for (int x = 1772; x <= 1774; x++) {
                blok(vast, x, b - 1, 2);
                blok(vast, x, b + 1, 2);
            }
            blok(vast, 1774, b, 1);
        }
        List<double[][]> wachters = new ArrayList<>();
        for (int i = 0; i < ZeldaEiland5a.BALKONS.length; i++)
            wachters.add(ZeldaEiland5a.standen(true, 1770.5, 0, ZeldaEiland5a.BALKONS[i] - 6, ZeldaEiland5a.BALKONS[i] + 5, ZeldaEiland5a.FASES[i]));
        List<int[]> pad = new ArrayList<>(List.of(new int[]{1766, 0, 48}, new int[]{1771, 0, 48}));
        Set<Integer> schuil = new HashSet<>(Set.of(0));
        for (int b : ZeldaEiland5a.BALKONS) {
            pad.add(new int[]{1771, 0, b});
            pad.add(new int[]{1773, 0, b});
            schuil.add(pad.size() - 1);
            pad.add(new int[]{1771, 0, b});
        }
        pad.add(new int[]{1771, 0, -19});
        for (int k : kansen(vast, wachters, pad, schuil)) assertTrue(k >= 20, "een stuk van de weergang is te moeilijk: " + k + "% van het ritme");
    }

    /** De cellengang (y 0 = waar je staat): de noordwand met nissen en celdeuren, de zuidwand met de wachtkamer, de hal in het oosten. */
    @Test
    void cellengangIsTeSluipen() {
        Set<Long> vast = new HashSet<>();
        Set<Integer> nis = new HashSet<>();
        for (int n : ZeldaEiland5a.NISSEN) nis.add(n);
        for (int x = 1708; x <= 1759; x++) {
            if (!nis.contains(x)) blok(vast, x, -22, 4);
            if (x < 1734 || x > 1736) blok(vast, x, -18, 4);
        }
        for (int n : ZeldaEiland5a.NISSEN)
            for (int z = -24; z <= -22; z++) {
                blok(vast, n - 1, z, 4);
                blok(vast, n + 1, z, 4);
            }
        for (int z = -30; z <= -12; z++) if (z < -21 || z > -19) blok(vast, 1759, z, 4);   // de wand tussen de gang en de hal
        List<double[][]> wachters = new ArrayList<>();
        for (int i = 0; i < ZeldaEiland5a.NISSEN.length; i++)
            wachters.add(ZeldaEiland5a.standen(false, -19.5, 0, ZeldaEiland5a.NISSEN[i] - 6, ZeldaEiland5a.NISSEN[i] + 5, ZeldaEiland5a.FASES[i]));
        List<int[]> pad = new ArrayList<>(List.of(new int[]{1761, 0, -24}, new int[]{1761, 0, -21}));
        Set<Integer> schuil = new HashSet<>(Set.of(0));
        for (int n : ZeldaEiland5a.NISSEN) {
            pad.add(new int[]{n, 0, -21});
            pad.add(new int[]{n, 0, -23});
            schuil.add(pad.size() - 1);
            pad.add(new int[]{n, 0, -21});
        }
        pad.add(new int[]{1711, 0, -21});
        for (int k : kansen(vast, wachters, pad, schuil)) assertTrue(k >= 20, "een stuk van de cellengang is te moeilijk: " + k + "% van het ritme");
    }
}
