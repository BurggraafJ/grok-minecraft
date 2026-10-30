package nl.burggraaf.mc.zelda;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;

/**
 * {@code zelda plaatjes [begin]}: plaatjes van alle eilanden (van boven en van opzij) in {@code plugins/Zelda/plaatjes/},
 * om te controleren zonder in te loggen. Kwam uit {@code kitpvp kaart} (kitpvp 2.16 en ouder).
 */
final class ZeldaPlaatjes {

    private ZeldaPlaatjes() {
    }

    static List<String> maak(World w, int g, File map) throws IOException {
        List<File> f = new ArrayList<>();
        // Story of Zelda, eiland 1 (het open Lindewoud): het hele eiland van boven, de streken van dichtbij, en van opzij
        int zx = ZeldaHoofdstuk1.X0, zg = g + 8;
        f.add(Kaart.boven(w, map, "zelda-boven", zx - 60, -110, zx + 400, 110, g - 2, zg + 50, 2));
        f.add(Kaart.boven(w, map, "zelda-thuis", zx - 10, -22, zx + 90, 22, g - 2, zg + 22, 6));
        f.add(Kaart.boven(w, map, "zelda-dorp", zx + 82, -24, zx + 142, 28, g - 2, zg + 22, 8));
        f.add(Kaart.boven(w, map, "zelda-noord", zx + 78, -82, zx + 186, -10, g - 2, zg + 22, 5));
        f.add(Kaart.boven(w, map, "zelda-tempel", zx + 160, -24, zx + 276, 24, g - 2, zg + 22, 5));
        f.add(Kaart.boven(w, map, "zelda-veen", zx + 112, 32, zx + 240, 92, g - 2, zg + 22, 5));
        f.add(Kaart.boven(w, map, "zelda-haven", zx + 250, -30, zx + 392, 66, g - 2, zg + 22, 4));
        f.add(Kaart.voor(w, map, "zelda-zij", 0, 1, -100, zx - 50, zx + 470, g, zg + 50, 200, 2));
        f.add(Kaart.voor(w, map, "zelda-kloof-zij", 0, 1, -6, zx + 212, zx + 262, g, zg + 10, 14, 10));
        f.add(Kaart.voor(w, map, "zelda-ruine-zij", 0, 1, -12, zx + 164, zx + 210, g + 6, zg + 12, 30, 10));
        f.add(Kaart.voor(w, map, "zelda-toren-zij", 0, 1, -16, zx + 88, zx + 136, g + 6, zg + 22, 30, 10));    // de sterrentoren en de linde
        f.add(Kaart.voor(w, map, "zelda-haven-zij", 0, 1, -24, zx + 320, zx + 390, g + 2, zg + 22, 40, 8));
        f.add(Kaart.voor(w, map, "zelda-kring-zij", 0, 1, 50, zx + 195, zx + 231, g + 6, zg + 14, 34, 10));
        f.add(Kaart.boven(w, map, "zelda-eik", zx + 79, -78, zx + 109, -46, g - 2, zg + 5, 10));                // holle eik en houtkap (onder de kruin)
        f.add(Kaart.voor(w, map, "zelda-richel", 1, 0, zx + 239, -20, 2, g, zg + 8, 20, 10));                   // oostwand van de Kloof
        f.add(Kaart.voor(w, map, "zelda-barst", 0, 1, 2, zx + 194, zx + 208, zg - 1, zg + 9, 10, 12));          // gebarsten muur in het heiligdom
        // eiland 2a, het Zilverstrand west: alles van boven, het dorp, de kelder (net onder het plafond), de kaap en de duinen
        f.add(Kaart.boven(w, map, "zelda-e2a-boven", ZeldaEiland2a.X1, -440, ZeldaEiland2a.X2, -200, g - 2, zg + 45, 3));
        f.add(Kaart.boven(w, map, "zelda-e2a-dorp", 1108, -322, 1172, -210, g - 2, zg + 22, 6));
        f.add(Kaart.boven(w, map, "zelda-e2a-kelder", 1077, -347, 1101, -302, g, zg + 7, 14));
        f.add(Kaart.voor(w, map, "zelda-e2a-kaap-zij", 0, -1, -296, 1068, 1130, g, zg + 45, 60, 6));
        f.add(Kaart.voor(w, map, "zelda-e2a-kaap-noord", 0, 1, -350, 1068, 1130, g, zg + 45, 60, 6));
        f.add(Kaart.boven(w, map, "zelda-e2a-duinen", 1100, -416, 1222, -340, g - 2, zg + 24, 6));
        f.add(Kaart.voor(w, map, "zelda-e2a-rots-zij", 0, -1, -376, 1180, 1216, g, zg + 24, 30, 10));
        f.add(Kaart.voor(w, map, "zelda-e2a-duinen-zij", 0, -1, -345, 1096, 1224, g, zg + 24, 80, 4));
        // eiland 2b, de Getijdentempel: alles van boven, het plein en de tempel, de zaal en de kelders (onder het dak),
        // de Draaikolk, de toren van opzij, het wrak, de poelen (en de gang eronder)
        f.add(Kaart.boven(w, map, "zelda-e2b-boven", ZeldaEiland2b.X1, -430, ZeldaEiland2b.X2, -262, g - 2, zg + 30, 3));
        f.add(Kaart.boven(w, map, "zelda-e2b-tempel", 1256, -362, 1358, -312, g - 2, zg + 11, 6));
        f.add(Kaart.boven(w, map, "zelda-e2b-kelders", 1284, -362, 1322, -316, g, zg - 2, 12));
        f.add(Kaart.voor(w, map, "zelda-e2b-zaal-zij", 0, 1, -330, 1284, 1358, g, zg + 34, 40, 6));
        f.add(Kaart.voor(w, map, "zelda-e2b-kelders-zij", 1, 0, 1300, -362, -316, g, zg + 4, 30, 10));
        f.add(Kaart.boven(w, map, "zelda-e2b-wrak", 1296, -416, 1338, -392, g - 2, zg + 14, 10));
        f.add(Kaart.boven(w, map, "zelda-e2b-poelen", 1270, -300, 1330, -268, g - 2, zg + 8, 8));
        f.add(Kaart.voor(w, map, "zelda-e2b-duik-zij", 0, 1, -284, 1276, 1326, g, zg + 4, 6, 10));
        // eiland 3a, de Witte Toppen (dal en dorp): alles van boven, IJshoeve, het meer, het Sparrenbos met de kuil, de
        // Wachtersgang (net onder het plafond), de kloof van opzij (beide wanden), en de Bergpoort van opzij
        f.add(Kaart.boven(w, map, "zelda-e3a-boven", ZeldaEiland3a.X1, ZeldaEiland3a.Z1, ZeldaEiland3a.X2, ZeldaEiland3a.Z2, g - 2, zg + 34, 2));
        f.add(Kaart.boven(w, map, "zelda-e3a-dorp", 1440, -326, 1510, -276, g - 2, zg + 16, 7));
        f.add(Kaart.boven(w, map, "zelda-e3a-meer", 1408, -320, 1456, -280, g - 2, zg + 14, 8));
        f.add(Kaart.boven(w, map, "zelda-e3a-bos", 1404, -428, 1478, -322, g - 2, zg + 26, 5));
        f.add(Kaart.boven(w, map, "zelda-e3a-kuil", 1424, -394, 1448, -364, g, zg + 1, 12));
        f.add(Kaart.boven(w, map, "zelda-e3a-gang", 1448, -440, 1490, -418, g, zg + 3, 10));
        f.add(Kaart.boven(w, map, "zelda-e3a-kloof", 1480, -450, 1536, -400, g - 2, zg + 12, 8));
        f.add(Kaart.voor(w, map, "zelda-e3a-kloof-noord", 0, -1, -430, 1480, 1536, g, zg + 14, 20, 8));
        f.add(Kaart.voor(w, map, "zelda-e3a-kloof-zuid", 0, 1, -446, 1480, 1536, g, zg + 14, 20, 8));
        f.add(Kaart.boven(w, map, "zelda-e3a-bergpoort", 1530, -312, 1579, -276, g - 2, zg + 30, 8));
        f.add(Kaart.voor(w, map, "zelda-e3a-bergpoort-zij", 0, -1, -294, 1530, 1579, g, zg + 30, 40, 8));
        // eiland 3b, het IJspaleis: alles van boven, het bergpad, het voorplein met de IJstuin, het paleis (net onder de
        // plafonds), de Glijzaal en de IJstuin van dichtbij, de Troonzaal, het geheel en de toren van opzij
        int f3 = zg + 26;
        f.add(Kaart.boven(w, map, "zelda-e3b-boven", ZeldaEiland3b.X1, ZeldaEiland3b.Z1, ZeldaEiland3b.X2, ZeldaEiland3b.Z2, g - 2, zg + 64, 2));
        f.add(Kaart.boven(w, map, "zelda-e3b-bergpad", 1576, -312, 1640, -256, g - 2, zg + 40, 6));
        f.add(Kaart.boven(w, map, "zelda-e3b-paleis", 1606, -338, 1714, -252, f3 - 8, f3 + 7, 4));
        f.add(Kaart.boven(w, map, "zelda-e3b-glijzaal", 1645, -336, 1671, -306, f3 - 4, f3 + 5, 10));
        f.add(Kaart.boven(w, map, "zelda-e3b-tuin", 1608, -337, 1631, -305, f3 - 2, f3 + 7, 10));
        f.add(Kaart.boven(w, map, "zelda-e3b-troon", 1676, -320, 1712, -280, f3 - 2, f3 + 10, 8));
        f.add(Kaart.voor(w, map, "zelda-e3b-zij", 0, -1, -250, 1578, 1718, g, zg + 64, 120, 3));
        f.add(Kaart.voor(w, map, "zelda-e3b-toren-zij", 0, -1, -300, 1676, 1712, f3 - 2, zg + 64, 30, 6));
        // eiland 4a, de Gloedberg (Sintelgat en de mijnen): alles van boven, het dorp, de bovenmijn en de gloed (net onder de
        // plafonds), de hete gangen en de Gloedader, de mijn van opzij (door de Lorriehal en door de Vonkkamer), en het geheel
        int d4 = zg + 2, f4 = zg - 5;
        f.add(Kaart.boven(w, map, "zelda-e4a-boven", ZeldaEiland4a.X1, ZeldaEiland4a.Z1, ZeldaEiland4a.VX2, ZeldaEiland4a.Z2, g - 2, zg + 72, 2));
        f.add(Kaart.boven(w, map, "zelda-e4a-dorp", 1098, 296, 1172, 362, g - 2, d4 + 14, 7));
        f.add(Kaart.boven(w, map, "zelda-e4a-baai", 1058, 316, 1110, 352, g - 2, zg + 16, 8));
        f.add(Kaart.boven(w, map, "zelda-e4a-mijn", 1162, 254, 1262, 368, d4 - 1, d4 + 5, 5));
        f.add(Kaart.boven(w, map, "zelda-e4a-hete-gangen", 1204, 256, 1262, 336, f4 - 1, f4 + 4, 8));
        f.add(Kaart.voor(w, map, "zelda-e4a-mijn-zij", 0, 1, 325, 1160, 1262, g, zg + 40, 14, 5));      // van binnen, door de Daaltrap en de Gloedader
        f.add(Kaart.voor(w, map, "zelda-e4a-vonk-zij", 1, 0, 1197, 252, 300, g, zg + 34, 12, 8));       // door de Schachtput en de Vonkkamer
        f.add(Kaart.voor(w, map, "zelda-e4a-zij", 0, -1, 470, 1060, 1400, g, zg + 72, 300, 2));
        // eiland 4b, de Smidse: alles van boven, de zalen (net onder de plafonds), de kloof van de Kettingzaal, het Hart, de krater,
        // en van opzij: door het Hart en de schoorsteen, en door de Smidsehal
        f.add(Kaart.boven(w, map, "zelda-e4b-boven", ZeldaEiland4b.X1, ZeldaEiland4b.Z1, ZeldaEiland4b.X2, ZeldaEiland4b.Z2, g - 2, zg + 80, 2));
        f.add(Kaart.boven(w, map, "zelda-e4b-zalen", 1256, 236, 1342, 346, d4 - 1, d4 + 4, 5));
        f.add(Kaart.boven(w, map, "zelda-e4b-kloof", 1256, 242, 1300, 272, zg - 8, d4 - 1, 8));
        f.add(Kaart.boven(w, map, "zelda-e4b-krater", 1296, 312, 1342, 346, zg + 48, zg + 62, 8));
        f.add(Kaart.voor(w, map, "zelda-e4b-hart-zij", 0, 1, 330, 1256, 1342, zg - 4, zg + 66, 8, 5));
        f.add(Kaart.voor(w, map, "zelda-e4b-hal-zij", 1, 0, 1262, 236, 346, zg - 8, zg + 20, 14, 5));
        // eiland 5a, Nachtrots (de muren): alles van boven (met de toren van 5b), de kreek en de klippen, de weergang, de burcht
        // (op het plateau), de kerkers (net boven de vloer), en van opzij: door de cellengang, door de kapel en de burcht
        int k5 = zg + 2, p5 = zg + 14, w5 = p5 + 10;
        f.add(Kaart.boven(w, map, "zelda-e5a-boven", ZeldaEiland5a.X1, -110, ZeldaEiland5a.TX2, 110, g - 2, zg + 80, 2));
        f.add(Kaart.boven(w, map, "zelda-e5a-kreek", 1660, -30, 1765, 62, g - 2, p5 + 8, 5));
        f.add(Kaart.boven(w, map, "zelda-e5a-weergang", 1750, -32, 1790, 60, w5 - 1, w5 + 3, 6));
        f.add(Kaart.boven(w, map, "zelda-e5a-burcht", 1738, -66, 1872, 66, p5 - 1, p5 + 4, 4));
        f.add(Kaart.boven(w, map, "zelda-e5a-kerkers", 1700, -38, 1810, 34, k5 - 1, k5 + 3, 5));
        f.add(Kaart.voor(w, map, "zelda-e5a-cellen-zij", 0, 1, -24, 1700, 1810, zg - 6, w5 + 10, 8, 5));
        f.add(Kaart.voor(w, map, "zelda-e5a-kapel-zij", 0, 1, 20, 1700, 1810, zg - 6, w5 + 10, 12, 5));
        f.add(Kaart.voor(w, map, "zelda-e5a-zij", 0, -1, 100, ZeldaEiland5a.X1, ZeldaEiland5a.TX2, g, zg + 80, 200, 2));
        f.add(Kaart.boven(w, map, "zelda-e5a-omgeving", 1560, -260, 2047, 260, g - 2, zg + 80, 1));   // met de randen van het vak (wat je van ver ziet)
        // eiland 5b, de toren: alles van boven, de nevelbrug en de torenrots, de trap om de toren (net boven elke richel), de
        // torenhal en de schatkamer, de troonzaal en het dak, en van opzij: de toren van vier kanten en de brug
        f.add(Kaart.boven(w, map, "zelda-e5b-boven", 1850, -60, 2000, 60, g - 2, zg + 90, 3));
        f.add(Kaart.boven(w, map, "zelda-e5b-brug", 1855, -12, 1915, 12, g - 2, p5 + 6, 8));
        for (int[] r : new int[][]{{p5, 1}, {p5 + 10, 2}, {p5 + 20, 3}, {p5 + 30, 4}, {p5 + 40, 5}})
            f.add(Kaart.boven(w, map, "zelda-e5b-trap" + r[1], 1916, -20, 1964, 20, r[0] - 1, r[0] + 10, 8));
        f.add(Kaart.boven(w, map, "zelda-e5b-hal", 1926, -12, 1952, 12, p5 - 7, p5 + 4, 10));
        f.add(Kaart.boven(w, map, "zelda-e5b-schat", 1926, -12, 1952, 12, p5 - 7, p5 - 2, 10));
        f.add(Kaart.boven(w, map, "zelda-e5b-troon", 1926, -12, 1952, 12, p5 + 40, p5 + 46, 10));
        f.add(Kaart.boven(w, map, "zelda-e5b-dak", 1926, -12, 1952, 12, p5 + 52, p5 + 62, 10));
        f.add(Kaart.voor(w, map, "zelda-e5b-west", 1, 0, 1905, -24, 24, zg - 2, p5 + 62, 60, 6));
        f.add(Kaart.voor(w, map, "zelda-e5b-noord", 0, 1, -30, 1910, 1970, zg - 2, p5 + 62, 60, 6));
        f.add(Kaart.voor(w, map, "zelda-e5b-oost", -1, 0, 1975, -24, 24, zg - 2, p5 + 62, 60, 6));
        f.add(Kaart.voor(w, map, "zelda-e5b-zuid", 0, -1, 30, 1910, 1970, zg - 2, p5 + 62, 60, 6));
        f.add(Kaart.voor(w, map, "zelda-e5b-zij", 0, -1, 40, 1840, 2000, g, zg + 90, 200, 3));
        f.add(Kaart.boven(w, map, "zelda-feest", 1222, -78, 1258, -42, zg - 2, zg + 12, 10));
        List<String> uit = new ArrayList<>();
        for (File x : f) if (x != null) uit.add(x.getPath());
        return uit;
    }
}
