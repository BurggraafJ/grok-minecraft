package nl.burggraaf.mc.zelda;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

/** Glijvloeren (eiland 3b): glijden tot je ergens tegenaan botst, en de twee puzzels van het IJspaleis. */
class ZeldaGlijTest {

    /** Een plattegrond als vakjes: # = rots, . = ijs, S/G = vloer (rij 0 = noord, kolom 0 = west). */
    private record Kaart(String[] r) {
        char t(int x, int z) {
            return z < 0 || z >= r.length || x < 0 || x >= r[z].length() ? '#' : r[z].charAt(x);
        }

        ZeldaRegels.Vak ijs() { return (x, z) -> t(x, z) == '.'; }
        ZeldaRegels.Vak vrij() { return (x, z) -> t(x, z) != '#'; }
        ZeldaRegels.Vak vloer() { return (x, z) -> t(x, z) == 'S' || t(x, z) == 'G'; }

        int[] vind(char c) {
            for (int z = 0; z < r.length; z++) for (int x = 0; x < r[z].length(); x++) if (r[z].charAt(x) == c) return new int[]{x, z};
            throw new IllegalStateException("geen " + c);
        }

        int[] glij(int[] van, BlockFace f) {
            return ZeldaRegels.glij(van[0], van[1], f, ijs(), vrij(), vloer(), 64);
        }

        List<BlockFace> oplossing(int[] van, int[] naar) {
            return ZeldaRegels.glijOplossing(van, naar, ijs(), vrij(), vloer(), 64);
        }
    }

    @Test
    void glijdenTotJeErgensTegenaanBotst() {
        Kaart k = new Kaart(new String[]{
                "#####",
                "#..##",
                "#...#",
                "S..#G",
                "#####"});
        assertArrayEquals(new int[]{2, 3}, k.glij(new int[]{0, 3}, BlockFace.EAST), "tot de rots");
        assertArrayEquals(new int[]{2, 1}, k.glij(new int[]{2, 3}, BlockFace.NORTH), "tot de muur");
        assertArrayEquals(new int[]{2, 1}, k.glij(new int[]{2, 1}, BlockFace.EAST), "tegen een rots: blijft staan");
        assertArrayEquals(new int[]{0, 3}, k.glij(new int[]{2, 3}, BlockFace.WEST), "van het ijs af: op de vloer en stil");
        assertNull(k.oplossing(k.vind('S'), k.vind('G')), "G ligt achter een rots: niet te halen");
    }

    @Test
    void deGlijzaalIsOplosbaarEnEenRichting() {
        Kaart k = new Kaart(ZeldaEiland3b.ZAAL);
        int[] s = k.vind('S'), g = k.vind('G');
        List<BlockFace> weg = k.oplossing(s, g);
        assertNotNull(weg, "de Glijzaal is op te lossen");
        assertEquals(5, weg.size(), "vijf zetten: " + weg);
        int[] c = s;
        for (BlockFace f : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.NORTH)) c = k.glij(c, f);
        assertArrayEquals(g, c, "de oplossing in ZeldaEiland3b (NENWN) komt bij het doel");
        assertNull(k.oplossing(g, s), "van het doel kun je niet terug naar de start (verder via de Wachtzaal)");
    }

    @Test
    void deIJstuinIsOplosbaarEnTerugTeGlijden() {
        Kaart k = new Kaart(ZeldaEiland3b.TUIN);
        int[] s = k.vind('S'), g = k.vind('G');
        List<BlockFace> weg = k.oplossing(s, g);
        assertNotNull(weg);
        assertEquals(7, weg.size(), "zeven zetten: " + weg);
        int[] c = s;
        for (char r : "NESWSEN".toCharArray())
            c = k.glij(c, switch (r) { case 'N' -> BlockFace.NORTH; case 'E' -> BlockFace.EAST; case 'S' -> BlockFace.SOUTH; default -> BlockFace.WEST; });
        assertArrayEquals(g, c, "de oplossing in ZeldaEiland3b (NESWSEN) komt bij het doel");
        assertNotNull(k.oplossing(g, s), "uit de tuin glij je ook weer terug");
    }

    @Test
    void geenVastloopplekken() {
        for (String[] kaart : List.of(ZeldaEiland3b.ZAAL, ZeldaEiland3b.TUIN)) {
            Kaart k = new Kaart(kaart);
            int[] s = k.vind('S'), g = k.vind('G');
            for (int z = 0; z < kaart.length; z++)
                for (int x = 0; x < kaart[z].length(); x++) {
                    if (kaart[z].charAt(x) != '.') continue;
                    int[] c = {x, z};
                    if (k.oplossing(s, c) == null && k.oplossing(g, c) == null) continue;   // niet te bereiken: maakt niet uit
                    assertTrue(k.oplossing(c, g) != null || k.oplossing(c, s) != null, "vast op " + x + "," + z);
                }
        }
    }

    @Test
    void grimmelHeeftEenDikkeVacht() {
        assertTrue(ZeldaRegels.grimmelSchade(10, false) < 5, "gewoon: weinig");
        assertTrue(ZeldaRegels.grimmelSchade(10, true) > 10, "duizelig: veel");
    }
}
