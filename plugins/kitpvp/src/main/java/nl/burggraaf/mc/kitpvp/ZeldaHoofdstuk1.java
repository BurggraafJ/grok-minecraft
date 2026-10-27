package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

/**
 * Hoofdstuk 1, «Het Lindewoud». Je wordt wakker in je huisje aan de rand van het woud: Morgal, de Nachtvorst, heeft
 * prinses Zelda meegenomen naar zijn kasteel Nachtrots, over de zee. Wiske, een vuurvliegje, gaat met je mee. Het pad
 * loopt naar het oosten (x 1100 … 1480, z 0): de open plek (eerste gevecht, schild), de Oude Eik, de tempelruïne (drie
 * vuren, het Wachterszwaard), de Kloof (op tijd over de pilaren), het Veen (gevecht, boog), de doelen (brug), de
 * Heksenkring (baas Grauwmoer, hartcontainer) en de haven, waar Morgals soldaten de kade bezetten en de Zilvermeeuw
 * wacht. Aan de horizon, over de zee: Nachtrots. Zes rustplaatsen; de dag schuift op van ochtend naar zonsondergang.
 *
 * <p>Zelda 2.0, eiland 1 (sessie zelda-eiland-1): een rustiger begin (lichtere kamers, vrije doorgang als je een kamer
 * al won, de bel voor wie nog eens wil), de personen Kees (zijmissie De verloren bijl), Hanne (Moeraskruid), Lotje en
 * Brecht (varen), de houtkap achter de Oude Eik en vier hartstukjes: in de holle eik, van Kees, op een richel in de
 * Kloof (klauwhaak, eiland 3) en achter een gebarsten muur in de tempel (bommen, eiland 2). De rustplaatsen houden hun
 * nummer (oude standen wijzen ernaar): zet een nieuwe rustplaats altijd achteraan.
 */
final class ZeldaHoofdstuk1 extends ZeldaHoofdstuk {

    static final int X0 = 1100;                         // begin van het pad (rx = 0)
    static final int VERSIE = 3;
    /** De vier hartstukjes van het Lindewoud. */
    static final String STUKJE_EIK = "hs:lindewoud-1", STUKJE_KEES = "hs:lindewoud-2", STUKJE_RICHEL = "hs:lindewoud-3", STUKJE_TEMPEL = "hs:lindewoud-4";
    private static final int RX1 = -8, RX2 = 492, Z1 = -64, Z2 = 64;

    private final Plugin plugin;
    private Bouwer b;
    int G;                                              // pad-hoogte (bovenste blok); je staat op G+1
    private int g;
    private int[][] top;                                // hoogste blok per kolom
    private byte[][] soort;

    private static final byte LEEG = 0, OPEN = 1, WAND = 2, ZEE = 3, KLOOF = 4;
    private static final int BOS = 0, PAD = 1, START = 2, HOF = 3, VEEN = 4, KRING = 5, KADE = 6;

    /** Open vormen (relatief: rx, z) waar je kunt lopen; daarbuiten heuvels met bos. */
    private record Vorm(int grond, int type, double[] p) {
        double afstand(double x, double z) {
            return switch (type) {
                case 0 -> Math.hypot(x - p[0], z - p[1]) - p[2];                                     // cirkel
                case 1 -> (Math.hypot((x - p[0]) / p[2], (z - p[1]) / p[3]) - 1) * Math.min(p[2], p[3]); // ellips
                case 2 -> {                                                                           // rechthoek
                    double dx = Math.max(Math.max(p[0] - x, 0), x - p[2]), dz = Math.max(Math.max(p[1] - z, 0), z - p[3]);
                    yield dx == 0 && dz == 0 ? -Math.min(Math.min(x - p[0], p[2] - x), Math.min(z - p[1], p[3] - z)) - 0.5 : Math.hypot(dx, dz);
                }
                default -> {                                                                          // pad (lijn met straal)
                    double best = 1e9;
                    for (int i = 0; i + 3 < p.length - 1; i += 2) best = Math.min(best, lijn(x, z, p[i], p[i + 1], p[i + 2], p[i + 3]));
                    yield best - p[p.length - 1];
                }
            };
        }

        /** Afstand tot de middellijn (alleen voor paden). */
        double hart(double x, double z) {
            double best = 1e9;
            for (int i = 0; i + 3 < p.length - 1; i += 2) best = Math.min(best, lijn(x, z, p[i], p[i + 1], p[i + 2], p[i + 3]));
            return best;
        }

        private static double lijn(double x, double z, double ax, double az, double bx, double bz) {
            double vx = bx - ax, vz = bz - az, t = ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz);
            t = Math.max(0, Math.min(1, t));
            return Math.hypot(x - ax - t * vx, z - az - t * vz);
        }
    }

    private static Vorm cirkel(int grond, double x, double z, double r) {
        return new Vorm(grond, 0, new double[]{x, z, r});
    }

    private static Vorm ellips(int grond, double x, double z, double a, double c) {
        return new Vorm(grond, 1, new double[]{x, z, a, c});
    }

    private static Vorm rechthoek(int grond, double x1, double z1, double x2, double z2) {
        return new Vorm(grond, 2, new double[]{x1, z1, x2, z2});
    }

    private static Vorm pad(double r, double... punten) {
        double[] p = new double[punten.length + 1];
        System.arraycopy(punten, 0, p, 0, punten.length);
        p[punten.length] = r;
        return new Vorm(PAD, 3, p);
    }

    private static final List<Vorm> VORMEN = List.of(
            cirkel(START, 16, 0, 14),
            pad(3.5, 28, 0, 38, 3, 48, 4, 56, 2, 65, 0),
            cirkel(BOS, 76, 0, 13),
            pad(3, 87, 0, 95, 0),
            cirkel(BOS, 101, 0, 9),
            pad(2.5, 100, -8, 98, -13, 94, -17),                 // naar de houtkap, achter de Oude Eik
            cirkel(BOS, 92, -20, 5),
            pad(3, 108, 0, 116, 0),
            rechthoek(HOF, 114, -13, 141, 13),
            rechthoek(HOF, 142, -5, 152, 5),
            pad(3.5, 151, 0, 166, 0),
            pad(3.5, 199, 0, 215, 0),
            ellips(VEEN, 233, 0, 18, 13),
            pad(3.5, 250, 0, 266, 0),
            cirkel(VEEN, 257, 0, 6),
            rechthoek(VEEN, 262, -6, 276, 6),
            pad(3, 275, 0, 284, 0),
            cirkel(KRING, 296, 0, 14),
            pad(3.5, 308, 0, 325, 0),
            rechthoek(KADE, 324, -13, 346, 13));

    ZeldaHoofdstuk1(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override String id() { return "h1"; }
    @Override String naam() { return "Het Lindewoud"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda"; }

    @Override
    long hemel(Location l) {
        double rx = l.getX() - X0;
        double[][] punten = {{0, 500}, {100, 2500}, {155, 5000}, {207, 7000}, {258, 9500}, {300, 11000}, {322, 12000}, {370, 12600}};
        if (rx <= punten[0][0]) return (long) punten[0][1];
        for (int i = 1; i < punten.length; i++)
            if (rx <= punten[i][0]) {
                double t = (rx - punten[i - 1][0]) / (punten[i][0] - punten[i - 1][0]);
                return (long) (punten[i - 1][1] + t * (punten[i][1] - punten[i - 1][1]));
            }
        return (long) punten[punten.length - 1][1];
    }

    @Override
    String plekNaam(Location l) {
        double rx = l.getX() - X0;
        if (rx < 60) return "Thuis";
        if (rx >= 84 && rx < 100 && l.getZ() < -11) return "De houtkap";
        if (rx < 110) return "Lindewoud";
        if (rx < 158) return "Tempelruïne";
        if (rx < 212) return "De Kloof";
        if (rx < 280) return "Het Veen";
        if (rx < 312) return "Heksenkring";
        return "De Haven";
    }

    /** Altijd muziek: per streek een eigen lied (zie ZeldaMuziek). */
    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return switch (plekNaam(l)) {
            case "Thuis" -> ZeldaMuziek.THUIS;
            case "Tempelruïne" -> ZeldaMuziek.TEMPEL;
            case "Het Veen", "Heksenkring" -> ZeldaMuziek.VEEN;
            case "De Haven" -> ZeldaMuziek.HAVEN;
            default -> ZeldaMuziek.WOUD;
        };
    }

    // ── hulpjes ─────────────────────────────────────────────────────────────────────────────

    private void zet(int rx, int y, int z, Material m) {
        b.zet(X0 + rx, y, z, m);
    }

    private void zet(int rx, int y, int z, BlockData d) {
        b.zet(X0 + rx, y, z, d);
    }

    private void vul(int rx1, int y1, int z1, int rx2, int y2, int z2, Material m) {
        b.vul(X0 + rx1, y1, z1, X0 + rx2, y2, z2, m);
    }

    private Location plek(double rx, double y, double z, float yaw) {
        return new Location(w, X0 + rx + 0.5, y, z + 0.5, yaw, 0f);
    }

    private Box box(int rx1, int y1, int z1, int rx2, int y2, int z2) {
        return Box.van(X0 + rx1, y1, z1, X0 + rx2, y2, z2);
    }

    private static long h(int x, int z) {
        return Bouwer.hash(x, z);
    }

    private static int kans(int x, int z, int n) {
        return (int) (h(x * 7 + 3, z * 13 - 5) % n);
    }

    /** Zachte ruis 0 … 1 (waarden op een rooster van {@code s}, ertussen lineair). */
    private static double zacht(int x, int z, int s) {
        int gx = Math.floorDiv(x, s), gz = Math.floorDiv(z, s);
        double fx = (x - gx * s) / (double) s, fz = (z - gz * s) / (double) s;
        double a = (h(gx, gz) % 1000) / 1000.0, c = (h(gx + 1, gz) % 1000) / 1000.0;
        double d = (h(gx, gz + 1) % 1000) / 1000.0, e = (h(gx + 1, gz + 1) % 1000) / 1000.0;
        double o = a + (c - a) * fx, n = d + (e - d) * fx;
        return o + (n - o) * fz;
    }

    private static Material kies(int x, int z, Material... m) {
        return m[kans(x, z, m.length)];
    }

    private static BlockData stam(Material m, org.bukkit.Axis as) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable o) o.setAxis(as);
        return d;
    }

    private static BlockData vuur(Material m, boolean aan) {
        Campfire c = (Campfire) m.createBlockData();
        c.setLit(aan);
        return c;
    }

    private static BlockData rank(BlockFace f) {
        MultipleFacing v = (MultipleFacing) Material.VINE.createBlockData();
        v.setFace(f, true);
        return v;
    }

    private static BlockData rijp(Material m) {
        BlockData d = m.createBlockData();
        if (d instanceof Ageable a) a.setAge(a.getMaximumAge());
        return d;
    }

    // ── het terrein ─────────────────────────────────────────────────────────────────────────

    private static boolean kloof(int rx, int z) {
        return rx >= 166 && rx <= 199 && Math.abs(z) <= 26;
    }

    private static boolean kanaal(int rx, int z) {
        return rx >= 266 && rx <= 270 && Math.abs(z) <= 26;
    }

    /** De kustlijn: oostelijk hiervan is zee (de kade steekt uit tot 346). */
    private static double kust(int z) {
        int a = Math.abs(z);
        return a <= 13 ? 346 : 338 - (a - 13) * 0.5 + zacht(z, 0, 6) * 3;
    }

    private static boolean eiland(int rx, int z) {
        return Math.hypot((rx - 446) / 27.0, z / 24.0) + zacht(rx, z, 5) * 0.12 < 1.0;
    }

    private void kolom(int rx, int z) {
        int i = rx - RX1, j = z - Z1;
        int ymax = G + 36;
        if (rx > 405) ymax = G + 56;
        if (eiland(rx, z)) {
            double r = Math.hypot((rx - 446) / 27.0, z / 24.0);
            int hoog = r > 0.82 ? G + 1 + (int) ((1.0 - r) * 18) : G + 4;
            vul(rx, g + 1, z, rx, hoog - 1, z, Material.BLACKSTONE);
            zet(rx, hoog, z, kies(rx, z, Material.BLACKSTONE, Material.BASALT, Material.TUFF, Material.COBBLED_DEEPSLATE));
            vul(rx, hoog + 1, z, rx, ymax, z, Material.AIR);
            top[i][j] = hoog;
            soort[i][j] = WAND;
            return;
        }
        if (rx > kust(z)) {
            int bodem = G - 4 - (int) (zacht(rx, z, 9) * 2);
            vul(rx, g + 1, z, rx, bodem - 1, z, Material.STONE);
            zet(rx, bodem, z, kans(rx, z, 4) == 0 ? Material.GRAVEL : Material.SAND);
            vul(rx, bodem + 1, z, rx, G - 1, z, Material.WATER);
            vul(rx, G, z, rx, ymax, z, Material.AIR);
            top[i][j] = G - 1;
            soort[i][j] = ZEE;
            return;
        }
        if (kloof(rx, z)) {
            zet(rx, g + 1, z, Material.STONE);
            vul(rx, g + 2, z, rx, g + 3, z, Material.WATER);
            vul(rx, g + 4, z, rx, ymax, z, Material.AIR);
            top[i][j] = g + 3;
            soort[i][j] = KLOOF;
            return;
        }
        if (kanaal(rx, z)) {
            zet(rx, g + 1, z, Material.GRAVEL);
            vul(rx, g + 2, z, rx, G - 2, z, Material.WATER);
            vul(rx, G - 1, z, rx, ymax, z, Material.AIR);
            top[i][j] = G - 2;
            soort[i][j] = KLOOF;
            return;
        }
        double d = 1e9;
        Vorm v = null;
        for (Vorm f : VORMEN) {
            double a = f.afstand(rx, z);
            if (a < d) {
                d = a;
                v = f;
            }
        }
        int x = X0 + rx;
        if (d <= 0) {
            Material opp = grond(v, rx, z, x);
            boolean water = v.grond() == VEEN && zacht(x, z, 4) > 0.62 && Math.abs(z) > 2 && rx < 262;
            vul(rx, g + 1, z, rx, G - 4, z, Material.STONE);
            vul(rx, G - 3, z, rx, G - 1, z, water ? Material.MUD : Material.DIRT);
            zet(rx, G, z, water ? Material.WATER : opp);
            vul(rx, G + 1, z, rx, ymax, z, Material.AIR);
            if (water && kans(x, z, 5) == 0) zet(rx, G + 1, z, Material.LILY_PAD);
            else if (!water) versier(v, rx, z, x, opp);
            top[i][j] = G;
            soort[i][j] = OPEN;
            return;
        }
        // heuvels: meteen 3 hoog (niet op te springen), daarna oplopend
        int hoog = G + 3 + (int) Math.min(8, d * 0.8) + (int) (zacht(x, z, 7) * 3);
        double zee = kust(z) - rx;
        if (zee < 10) hoog = Math.min(hoog, G + (int) Math.max(0, zee / 2));
        Material opp;
        if (zee < 6) opp = Material.SAND;
        else if (rx > 276 && rx < 312) {
            double r = zacht(x, z, 4);
            opp = r < 0.3 ? Material.MOSS_BLOCK : r < 0.55 ? Material.STONE : r < 0.75 ? Material.ANDESITE : Material.TUFF;
        }
        else if (rx > 208 && rx < 280) opp = kies(x, z, Material.MOSS_BLOCK, Material.GRASS_BLOCK, Material.GRASS_BLOCK, Material.MUD, Material.ROOTED_DIRT);
        else opp = kans(x, z, 7) == 0 ? Material.PODZOL : Material.GRASS_BLOCK;
        vul(rx, g + 1, z, rx, hoog - 4, z, Material.STONE);
        vul(rx, hoog - 3, z, rx, hoog - 1, z, opp == Material.SAND ? Material.SAND : Material.DIRT);
        zet(rx, hoog, z, opp);
        vul(rx, hoog + 1, z, rx, ymax, z, Material.AIR);
        if (opp == Material.GRASS_BLOCK && kans(x, z, 4) == 0) zet(rx, hoog + 1, z, kans(x, z, 3) == 0 ? Material.FERN : Material.SHORT_GRASS);
        top[i][j] = hoog;
        soort[i][j] = WAND;
    }

    private Material grond(Vorm v, int rx, int z, int x) {
        return switch (v.grond()) {
            case PAD -> v.hart(rx, z) < 1.3 ? (kans(x, z, 6) == 0 ? Material.COARSE_DIRT : Material.DIRT_PATH)
                    : kans(x, z, 9) == 0 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            case HOF -> kies(x, z, Material.STONE_BRICKS, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                    Material.CRACKED_STONE_BRICKS, Material.MOSS_BLOCK, Material.GRASS_BLOCK, Material.GRASS_BLOCK);
            case VEEN -> kies(x, z, Material.MUD, Material.MOSS_BLOCK, Material.MOSS_BLOCK, Material.GRASS_BLOCK, Material.GRASS_BLOCK, Material.ROOTED_DIRT);
            case KRING -> kies(x, z, Material.PODZOL, Material.PODZOL, Material.MYCELIUM, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MOSS_BLOCK);
            case KADE -> kies(x, z, Material.COBBLESTONE, Material.STONE_BRICKS, Material.STONE_BRICKS, Material.GRAVEL, Material.ANDESITE, Material.MOSSY_COBBLESTONE);
            default -> kans(x, z, 10) == 0 ? Material.PODZOL : kans(x, z, 13) == 0 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
        };
    }

    private void versier(Vorm v, int rx, int z, int x, Material opp) {
        if (opp != Material.GRASS_BLOCK && opp != Material.MOSS_BLOCK && opp != Material.PODZOL && opp != Material.MYCELIUM) return;
        int k = kans(x + 11, z - 3, 100);
        Material m = null;
        switch (v.grond()) {
            case START -> m = k < 7 ? kies(x, z, Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.AZURE_BLUET, Material.LILY_OF_THE_VALLEY)
                    : k < 22 ? Material.SHORT_GRASS : null;
            case PAD -> m = k < 12 ? Material.SHORT_GRASS : k < 15 ? Material.FERN : k < 17 ? Material.DANDELION : null;
            case BOS -> m = k < 12 ? Material.SHORT_GRASS : k < 16 ? Material.FERN : k < 18 ? kies(x, z, Material.POPPY, Material.ALLIUM, Material.AZURE_BLUET) : null;
            case HOF -> m = k < 6 ? Material.SHORT_GRASS : null;
            case VEEN -> m = k < 8 ? Material.SHORT_GRASS : k < 10 ? Material.BROWN_MUSHROOM : null;
            case KRING -> m = k < 4 ? (kans(x, z, 2) == 0 ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM) : null;
            default -> { }
        }
        if (m != null) zet(rx, G + 1, z, m);
    }

    // ── bomen ───────────────────────────────────────────────────────────────────────────────

    private void bladbol(int rx, int y, int z, double r, Material blad) {
        BlockData d = Bouwer.blad(blad);
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++)
            for (int dy = -ri; dy <= ri; dy++)
                for (int dz = -ri; dz <= ri; dz++) {
                    double a = dx * dx + dz * dz + dy * dy * 1.6;
                    if (a <= r * r - (h(X0 + rx + dx * 3, z + dz * 5 + dy * 11) % 3)) zet(rx + dx, y + dy, z + dz, d);
                }
    }

    private void boom(int rx, int z, int y) {
        int x = X0 + rx;
        int k = kans(x, z, 100);
        if (rx > 276 && rx < 312) {                                // dode bomen rond de Heksenkring
            int hg = 5 + kans(x, z, 4);
            vul(rx, y + 1, z, rx, y + hg, z, Material.DARK_OAK_LOG);
            BlockFace[] tak = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            BlockFace f = tak[kans(x, z, 4)];
            zet(rx + f.getModX(), y + hg - 1, z + f.getModZ(), stam(Material.DARK_OAK_LOG, f.getModX() != 0 ? org.bukkit.Axis.X : org.bukkit.Axis.Z));
            zet(rx + 2 * f.getModX(), y + hg, z + 2 * f.getModZ(), Material.DARK_OAK_LOG);
            if (k < 40) bladbol(rx, y + hg, z, 1.6, Material.DARK_OAK_LEAVES);
            return;
        }
        if (rx > 208 && rx < 280) {                                // moerasbomen met ranken
            int hg = 4 + kans(x, z, 3);
            vul(rx, y + 1, z, rx, y + hg, z, Material.MANGROVE_LOG);
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
                zet(rx + f.getModX(), y + 1, z + f.getModZ(), Material.MANGROVE_ROOTS);
            bladbol(rx, y + hg + 1, z, 3.2, Material.MANGROVE_LEAVES);
            for (int n = 0; n < 4; n++) {
                int dx = kans(x + n, z, 7) - 3, dz = kans(x, z + n, 7) - 3;
                if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
                BlockFace f = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? BlockFace.WEST : BlockFace.EAST) : (dz > 0 ? BlockFace.NORTH : BlockFace.SOUTH);
                for (int dy = 0; dy < 2 + kans(x, z + n * 3, 3); dy++) zet(rx + dx, y + hg - dy, z + dz, rank(f));
            }
            return;
        }
        if (k < 30 || rx > 312) {                                  // eik
            int hg = 4 + kans(x, z, 3);
            vul(rx, y + 1, z, rx, y + hg, z, Material.OAK_LOG);
            bladbol(rx, y + hg, z, 2.8, k % 3 == 0 && rx < 120 ? Material.AZALEA_LEAVES : Material.OAK_LEAVES);
            if (k % 5 == 0 && rx < 120) zet(rx, y + hg + 2, z, Bouwer.blad(Material.FLOWERING_AZALEA_LEAVES));
        } else if (k < 55) {                                       // berk
            int hg = 5 + kans(x, z, 3);
            vul(rx, y + 1, z, rx, y + hg, z, Material.BIRCH_LOG);
            bladbol(rx, y + hg, z, 2.2, Material.BIRCH_LEAVES);
        } else {                                                   // spar
            int hg = 7 + kans(x, z, 4);
            vul(rx, y + 1, z, rx, y + hg, z, Material.SPRUCE_LOG);
            BlockData blad = Bouwer.blad(Material.SPRUCE_LEAVES);
            for (int dy = 2; dy <= hg + 1; dy++) {
                int r = Math.max(0, (hg + 1 - dy) / 2 - ((dy & 1) == 0 ? 0 : 1)) + (dy == hg + 1 ? 0 : 1);
                if (dy == hg + 1) r = 0;
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++)
                        if (dx * dx + dz * dz <= r * r + 1 && !(dx == 0 && dz == 0 && dy <= hg)) zet(rx + dx, y + dy, z + dz, blad);
            }
            zet(rx, y + hg + 1, z, blad);
        }
    }

    private void bomen() {
        int s = 6;
        for (int cx = Math.floorDiv(RX1, s); cx <= Math.floorDiv(470, s); cx++)
            for (int cz = Math.floorDiv(-44, s); cz <= Math.floorDiv(44, s); cz++) {
                int rx = cx * s + kans(cx, cz, s), z = cz * s + kans(cz, cx, s);
                if (rx <= RX1 + 2 || rx >= RX2 - 2 || Math.abs(z) > 44) continue;
                int i = rx - RX1, j = z - Z1;
                if (soort[i][j] != WAND || eiland(rx, z) || kust(z) - rx < 8) continue;
                // niet vlak tegen een open plek (dan hangen de bladeren over het pad)
                boolean vrij = true;
                for (int dx = -2; dx <= 2 && vrij; dx++)
                    for (int dz = -2; dz <= 2 && vrij; dz++) {
                        int a = i + dx, c = j + dz;
                        if (a < 0 || c < 0 || a >= soort.length || c >= soort[0].length || soort[a][c] != WAND) vrij = false;
                    }
                if (vrij) boom(rx, z, top[i][j]);
            }
    }

    // ── kleine bouwstenen ───────────────────────────────────────────────────────────────────

    /** Rustplaats: zielvuur in een kring van stenen, met een staande steen en lantaarn. */
    private void rustvuur(int rx, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) zet(rx + dx, G, z + dz, dx == 0 && dz == 0 ? Material.COBBLESTONE : Material.MOSSY_COBBLESTONE);
        zet(rx, G + 1, z, vuur(Material.SOUL_CAMPFIRE, true));
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            zet(rx + f.getModX() * 2, G + 1, z + f.getModZ() * 2, Bouwer.trap(Material.MOSSY_STONE_BRICK_STAIRS, f.getOppositeFace()));
        int sz = z + (z >= 0 ? 2 : -2);
        zet(rx + 2, G + 1, sz, Material.CHISELED_STONE_BRICKS);
        zet(rx + 2, G + 2, sz, Material.MOSSY_STONE_BRICKS);
        zet(rx + 2, G + 3, sz, Material.SOUL_LANTERN);
    }

    private void sokkel(int rx, int z) {
        zet(rx, G + 1, z, Material.CHISELED_STONE_BRICKS);
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            zet(rx + f.getModX(), G + 1, z + f.getModZ(), Bouwer.trap(Material.STONE_BRICK_STAIRS, f.getOppositeFace()));
    }

    private void lantaarnpaal(int rx, int z, Material paal) {
        zet(rx, G + 1, z, paal);
        zet(rx, G + 2, z, paal);
        zet(rx, G + 3, z, Material.LANTERN);
    }

    /** Een rij hekken die aan beide kanten aansluit (geen kieren tussen de palen). */
    private static BlockData hek(Material m, boolean langsZ) {
        return langsZ ? Bouwer.ruit(m, BlockFace.NORTH, BlockFace.SOUTH) : Bouwer.ruit(m, BlockFace.EAST, BlockFace.WEST);
    }

    private Deur deur(BlockData dicht, int rx, int y1, int z1, int y2, int z2) {
        return new Deur(dicht, Material.AIR.createBlockData()).vlak(X0 + rx, y1, z1, X0 + rx, y2, z2);
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        g = bouwer.g;
        G = g + 8;
        leeg(bouwer.w);
        speelgebied = box(-2, g - 4, -26, 392, G + 40, 26);
        boolean bouwen = bouwer.plaats;
        List<long[]> chunks = new ArrayList<>();
        if (bouwen) {
            for (int cx = Math.floorDiv(X0 + RX1, 16); cx <= Math.floorDiv(X0 + RX2, 16); cx++)
                for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) chunks.add(new long[]{cx, cz});
            b.daarna(() -> chunks.forEach(c -> w.addPluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = new int[RX2 - RX1 + 1][Z2 - Z1 + 1];
            soort = new byte[RX2 - RX1 + 1][Z2 - Z1 + 1];
            for (int rx = RX1; rx <= RX2; rx++)
                for (int z = Z1; z <= Z2; z++) kolom(rx, z);
            bomen();
        }
        thuis();
        openPlek();
        oudeEik();
        ruine();
        kloofBouw();
        veen();
        moerashut();
        doelen();
        heksenkring();
        haven();
        if (bouwen) {
            schip();
            nachtrots();
            b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = null;
            soort = null;
        }
        // de begintoestand van deuren en puzzels: altijd (ook zonder herbouw)
        for (Kamer k : kamers) {
            zetDeur(k.in, true);
            zetDeur(k.uit, false);
        }
        for (Uitdaging u : uitdagingen) zetDeur(u.poort, false);
        for (Barst br : barsten) zetDeur(br.muur, false);
        for (Reeks r : reeksen) {
            zetDeur(r.deur, false);
            for (int[] d : r.dingen) b.zetAltijd(d[0], d[1], d[2], r.uit);
        }
        motor();
        b = null;
    }

    /**
     * Zelda 2.0 (zonder blokken, dus zonder herbouw): bronnen bij de kamers, uitleg halverwege (over te slaan), tips
     * na doodgaan en de doelen voor {@code /hint}.
     */
    private void motor() {
        // genezen bij elke kamer: twee feeënkringen (alleen licht, geen blokken)
        bron("bron-bospad", plek(47, G + 1, 3, 0f));
        bron("bron-kring", plek(279, G + 1, 0, 0f));
        // uitleg halverwege (overgeslagen met «Uitleg: uit» op de kaart)
        for (String id : new String[]{"tempel", "kloof", "doelen"}) if (onderdeel(id) instanceof Verhaal v) v.uitleg = true;
        Verhaal hulp = na("intro", verhaal(new Verhaal("hulp", box(4, G, -8, 30, G + 6, 10), List.of(
                r("Wiske", "Weet je het even niet? Klik op mij, of typ /hint.")))));
        hulp.vereist = "v:intro";
        hulp.uitleg = true;
        na("hulp", verhaal(new Verhaal("uitleg-bron", box(45, G, 1, 49, G + 4, 5), List.of(
                r("Wiske", "Een feeënkring. Sta erin, dan genees je."))))).uitleg = true;
        na("eik", verhaal(new Verhaal("uitleg-rust", box(96, G, -6, 108, G + 6, 7), List.of(
                r("Wiske", "Bij een zielvuur bewaar je je reis, en genees je."),
                r("Wiske", "Val je, dan begin je hier weer."))))).uitleg = true;
        na("ruine", verhaal(new Verhaal("uitleg-kaart", box(154, G, -4, 162, G + 6, 4), List.of(
                r("Wiske", "Je kaart, vak 9: reizen naar elk zielvuur."),
                r("Wiske", "Rechtsklik op een zielvuur: dat stuk opnieuw."))))).uitleg = true;
        // tips na doodgaan in een kamer (eerste keer de eerste tip, dan de tweede)
        for (Kamer k : kamers) switch (k.id) {
            case "open-plek" -> k.tips.addAll(List.of(r("Wiske", "Niet stilstaan. Slaan, stap terug, weer slaan."),
                    r("Wiske", "Even op adem komen? De feeënkring vlakbij geneest.")));
            case "veen" -> k.tips.addAll(List.of(r("Wiske", "Eerst de mosschutters: die schieten van ver."),
                    r("Wiske", "Houd rechts: je schild vangt de pijlen.")));
            case "heksenkring" -> k.tips.addAll(List.of(r("Wiske", "Staat ze op een paddenstoel? Schiet haar eraf met je boog."),
                    r("Wiske", "Eerst haar helpers, dan zij.")));
            case "kade" -> k.tips.addAll(List.of(r("Wiske", "De kruisbogen achterin eerst. Blokkeer met je schild."),
                    r("Wiske", "Bij het zielvuur aan de trap genees je weer.")));
            default -> { }
        }
        // de doelen van het pad, voor /hint (eerst een duwtje, nog eens vragen = wat je moet doen)
        doel("schild", "Volg het pad naar het oosten, het bos in.", "Achter de open plek glanst iets op een sokkel.",
                plekVan("schild"), s -> s.heeft("schild"));
        doel("vuren", "De oude tempel. Steek de drie vuurschalen aan.", "Eén op de grond, één op de torenvoet, één midden in de vijver. Snel!",
                plekVan("vuren"), s -> s.heeft("r:vuren") || s.heeft("zwaard"));
        doel("zwaard", "In het heiligdom achter de poort ligt een zwaard.", "Door de open poort, helemaal achterin.",
                plekVan("zwaard"), s -> s.heeft("zwaard"));
        doel("kloof", "Over de Kloof. Stap op de plaat en ren.", "Van pilaar naar pilaar, dan over de stam. Niet stilstaan.",
                plekVan("kloof"), s -> s.rust(id()) >= 3);
        doel("boog", "Door het Veen, naar het oosten.", "Voorbij het Veen ligt een boog. Blijf op de droge plekken.",
                plekVan("boog"), s -> s.heeft("boog"));
        doel("doelen", "Drie doelen aan de overkant. Pak je boog.", "Raak ze alle drie binnen vijftien tellen, dan komt de brug.",
                plekVan("doelen"), s -> s.heeft("r:doelen") || s.heeft("hart1"));
        doel("heks", "Achter de brug wacht Grauwmoer, in de Heksenkring.", "Staat ze op een paddenstoel? Schiet haar eraf.",
                plekVan("heksenkring"), s -> s.heeft("hart1"));
        doel("kade", "Naar de haven. Morgals soldaten bezetten de kade.", "Over de kade, naar de pier.",
                plekVan("kade"), s -> s.heeft("k:kade") || s.heeft(id()));
        doel("brecht", "Zoek de visser op de pier.", "Helemaal aan het eind van de pier, bij het schip.",
                plekVan("brecht"), s -> s.heeft(id()));
    }

    /** Oude standen (van vóór de {@code k:}-vlaggen): kamers die je al voorbij bent, tellen als gewonnen. */
    @Override
    boolean aanvullen(ZeldaVoortgang.Stand s) {
        return aanvullen(s, id());
    }

    static boolean aanvullen(ZeldaVoortgang.Stand s, String id) {
        int r = s.rust(id);
        boolean nieuw = false;
        if (r >= 1) nieuw |= s.zet("k:open-plek");                      // de Oude Eik ligt achter de open plek
        if (r >= 4) nieuw |= s.zet("k:veen");                           // de Moerashut achter het Veen
        if (r >= 5 || s.heeft("hart1")) nieuw |= s.zet("k:heksenkring");
        if (s.heeft(id)) nieuw |= s.zet("k:kade");                      // wie het eiland af heeft, joeg ze van de kade
        return nieuw;
    }

    /** Een bel in een kamer (rx, z op de vloer): luiden = nog eens vechten. */
    private int[] bel(int rx, int z, BlockFace kijk) {
        zet(rx, G + 1, z, Bouwer.gericht(Material.BELL, kijk));
        return new int[]{X0 + rx, G + 1, z};
    }

    private void zetDeur(Deur d, boolean open) {
        d.isOpen = open;
        for (int[] c : d.blokken) b.zetAltijd(c[0], c[1], c[2], open ? d.open : d.dicht);
    }

    // ── 1. thuis ────────────────────────────────────────────────────────────────────────────

    private void thuis() {
        // huisje (8 × 7) aan de noordkant van de open plek, deur naar het zuiden
        int x1 = 5, x2 = 12, z1 = -12, z2 = -6;
        vul(x1, G, z1, x2, G, z2, Material.SPRUCE_PLANKS);
        vul(x1, G + 1, z1, x2, G + 3, z2, Material.MUD_BRICKS);
        vul(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 3, z2 - 1, Material.AIR);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) vul(c[0], G + 1, c[1], c[0], G + 3, c[1], Material.STRIPPED_SPRUCE_LOG);
        vul(x1, G + 4, z1, x2, G + 4, z2, Material.STRIPPED_SPRUCE_LOG);
        vul(x1 + 1, G + 4, z1 + 1, x2 - 1, G + 4, z2 - 1, Material.SPRUCE_PLANKS);
        // zadeldak langs x
        for (int t = 0; t <= 4; t++) {
            int zn = z1 - 1 + t, zs = z2 + 1 - t;
            if (zn > zs) break;
            for (int rx = x1 - 1; rx <= x2 + 1; rx++) {
                if (zn == zs) zet(rx, G + 5 + t, zn, Bouwer.plaat(Material.DARK_OAK_SLAB, false));
                else {
                    zet(rx, G + 5 + t, zn, Bouwer.trap(Material.DARK_OAK_STAIRS, BlockFace.SOUTH));
                    zet(rx, G + 5 + t, zs, Bouwer.trap(Material.DARK_OAK_STAIRS, BlockFace.NORTH));
                }
            }
            if (zn + 1 <= zs - 1) {
                vul(x1, G + 5 + t, zn + 1, x1, G + 5 + t, zs - 1, Material.MUD_BRICKS);
                vul(x2, G + 5 + t, zn + 1, x2, G + 5 + t, zs - 1, Material.MUD_BRICKS);
            }
        }
        // deur, ramen, schoorsteen met rook
        BlockData deurOnder = Material.SPRUCE_DOOR.createBlockData(d -> {
            ((org.bukkit.block.data.type.Door) d).setFacing(BlockFace.SOUTH);
            ((org.bukkit.block.data.type.Door) d).setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
        });
        BlockData deurBoven = Material.SPRUCE_DOOR.createBlockData(d -> {
            ((org.bukkit.block.data.type.Door) d).setFacing(BlockFace.SOUTH);
            ((org.bukkit.block.data.type.Door) d).setHalf(org.bukkit.block.data.Bisected.Half.TOP);
        });
        zet(8, G + 1, z2, deurOnder);
        zet(8, G + 2, z2, deurBoven);
        for (int rx : new int[]{6, 10, 11}) zet(rx, G + 2, z2, hek(Material.GLASS_PANE, false));
        zet(x1, G + 2, -9, hek(Material.GLASS_PANE, true));
        zet(x2, G + 2, -9, hek(Material.GLASS_PANE, true));
        for (int y = G + 1; y <= G + 9; y++) zet(11, y, -11, Material.COBBLESTONE);
        zet(11, G + 10, -11, vuur(Material.CAMPFIRE, true));
        zet(8, G + 3, z2 + 1, Bouwer.gericht(Material.WALL_TORCH, BlockFace.SOUTH));
        // binnen: bed, tafel, kist, lantaarn
        zet(6, G + 1, -11, Material.BARREL);
        zet(7, G + 1, -11, Material.CRAFTING_TABLE);
        zet(10, G + 1, -8, Material.SPRUCE_FENCE);
        zet(10, G + 2, -8, Bouwer.plaat(Material.SPRUCE_SLAB, false));
        zet(9, G + 3, -9, Bouwer.hangend(Material.LANTERN));
        // moestuin en hek
        vul(14, G, -12, 19, G, -9, Material.FARMLAND);
        for (int rx = 14; rx <= 19; rx++)
            for (int z = -12; z <= -9; z++) zet(rx, G + 1, z, rijp(rx % 2 == 0 ? Material.WHEAT : Material.CARROTS));
        for (int rx = 13; rx <= 20; rx++) {
            zet(rx, G + 1, -13, hek(Material.OAK_FENCE, false));
            zet(rx, G + 1, -8, hek(Material.OAK_FENCE, false));
        }
        zet(16, G + 1, -8, Material.AIR);
        zet(21, G + 1, -10, Material.COMPOSTER);
        // put
        int px = 22, pz = 6;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                zet(px + dx, G, pz + dz, Material.COBBLESTONE);
                zet(px + dx, G + 1, pz + dz, dx == 0 && dz == 0 ? Material.WATER : Material.MOSSY_COBBLESTONE);
            }
        zet(px, G, pz, Material.WATER);
        zet(px - 1, G + 2, pz, Material.OAK_FENCE);
        zet(px + 1, G + 2, pz, Material.OAK_FENCE);
        vul(px - 1, G + 3, pz, px + 1, G + 3, pz, Material.SPRUCE_SLAB);
        zet(px, G + 2, pz, Bouwer.hangend(Material.LANTERN));
        // bloemen langs het pad naar het huis
        for (int z = -5; z <= -2; z++) {
            zet(7, G + 1, z, Material.AIR);
            zet(8, G, z, Material.DIRT_PATH);
            zet(9, G + 1, z, kies(X0 + 9, z, Material.POPPY, Material.DANDELION, Material.CORNFLOWER));
        }
        lantaarnpaal(28, 3, Material.OAK_FENCE);
        // de houtstapel en het hakblok van buurman Kees (zijn bijl is weg)
        for (int rx = 22; rx <= 25; rx++) {
            zet(rx, G + 1, -10, stam(Material.OAK_LOG, org.bukkit.Axis.X));
            if (rx > 22) zet(rx, G + 2, -10, stam(Material.OAK_LOG, org.bukkit.Axis.X));
        }
        zet(24, G + 3, -10, stam(Material.BIRCH_LOG, org.bukkit.Axis.X));
        zet(23, G + 1, -7, Material.STRIPPED_OAK_LOG);

        rustvuur(18, 5);
        rust(new Rust("thuis", "Thuis", plek(18, G + 1, 0, -90f), box(4, G, -8, 30, G + 6, 10), List.of()));
        verhaal(new Verhaal("intro", box(4, G, -8, 30, G + 6, 10), List.of(
                r("Wiske", "Eindelijk wakker. Er is iets vreselijks gebeurd."),
                r("Wiske", "Morgal heeft prinses Zelda meegenomen, over de zee."),
                r("Wiske", "Naar Nachtrots, zijn kasteel. Wij gaan haar halen."),
                r("Wiske", "Volg het pad naar het oosten. Ik blijf bij je."))));
        kees();
    }

    /** Buurman Kees, de houthakker: zijmissie «De verloren bijl» (de bijl ligt bij de houtkap), beloning een hartstukje. */
    private void kees() {
        Persoon kees = persoon(new Persoon("Kees", plek(26, G + 1, -7, 50f), Registry.VILLAGER_PROFESSION.get(org.bukkit.NamespacedKey.minecraft("toolsmith")),
                List.of(r("Kees", "Weer aan het hakken. Dankzij jou."))));
        kees.streek = Villager.Type.TAIGA;
        stukje(STUKJE_KEES);
        missie("bijl", "De verloren bijl", "Zoek de bijl bij de houtkap, achter de Oude Eik.", "Breng de bijl terug naar Kees.");
        kees.gesprek("kees-1", s -> s.missie("bijl") == 0, List.of(
                        r("Kees", "Ha, buurkind. Mijn bijl is weg."),
                        r("Kees", "Hij viel bij de houtkap, achter de Oude Eik."),
                        r("Kees", "Toen kwamen die moerasdingen. Ik rende!")), (spel, p, s) -> spel.missie(p, "bijl", 1))
                .gesprek(null, s -> s.missie("bijl") == 1, List.of(r("Kees", "De houtkap ligt achter de Oude Eik, naar het noorden.")), null)
                .gesprek("kees-2", s -> s.missie("bijl") == 2, List.of(
                        r("Kees", "Mijn bijl! Hier, dit lag al jaren in mijn schuur.")), (spel, p, s) -> {
                    spel.missie(p, "bijl", ZeldaVoortgang.KLAAR);
                    spel.beloon(p, STUKJE_KEES, "Hartstukje", null);
                })
                .eenmalig("kees-eik", s -> s.missieKlaar("bijl") && !s.heeft(STUKJE_EIK), List.of(
                        r("Kees", "Als kind verstopte ik dingen in de Oude Eik."),
                        r("Kees", "Aan de achterkant zit een gat.")), null);
        verhaal(new Verhaal("kees", box(20, G, -11, 30, G + 5, -3), List.of(
                r("Wiske", "Buurman Kees kijkt zo zorgelijk.")))).vereist = "v:intro";
    }

    // ── 2. de open plek (gevecht) ───────────────────────────────────────────────────────────

    private void openPlek() {
        for (int z = 1; z <= 4; z += 3) lantaarnpaal(36 + z * 4, z == 1 ? -2 : 6, Material.OAK_FENCE);
        lantaarnpaal(52, -1, Material.OAK_FENCE);
        verhaal(new Verhaal("struik", box(52, G, -6, 58, G + 6, 8), List.of(
                r("Wiske", "Stil… er beweegt iets in het struikgewas."))));
        int cx = 76;
        // palissade: een ring van sparrenstammen, open bij de poorten
        for (int rx = cx - 14; rx <= cx + 14; rx++)
            for (int z = -14; z <= 14; z++) {
                double d = Math.hypot(rx - cx, z);
                if (d < 12 || d >= 13.2) continue;
                if (Math.abs(z) <= 1) continue;
                int hg = 5 + kans(X0 + rx, z, 2);
                vul(rx, G + 1, z, rx, G + hg, z, Material.SPRUCE_LOG);
                zet(rx, G + hg + 1, z, Material.SPRUCE_FENCE);
            }
        for (int poort : new int[]{64, 88}) {
            for (int z : new int[]{-2, 2}) vul(poort, G + 1, z, poort, G + 6, z, Material.STRIPPED_SPRUCE_LOG);
            for (int z = -2; z <= 2; z++) zet(poort, G + 5, z, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            for (int z = -1; z <= 1; z++) zet(poort, G + 4, z, hek(Material.SPRUCE_FENCE, true));
            zet(poort + (poort < cx ? -1 : 1), G + 4, -2, Bouwer.gericht(Material.WALL_TORCH, poort < cx ? BlockFace.WEST : BlockFace.EAST));
            zet(poort + (poort < cx ? -1 : 1), G + 4, 2, Bouwer.gericht(Material.WALL_TORCH, poort < cx ? BlockFace.WEST : BlockFace.EAST));
        }
        // binnen: een paar keien en een omgevallen stam als dekking
        vul(72, G + 1, -5, 73, G + 1, -4, Material.MOSSY_COBBLESTONE);
        zet(72, G + 2, -5, Material.COBBLESTONE);
        for (int rx = 77; rx <= 81; rx++) zet(rx, G + 1, 5, stam(Material.OAK_LOG, org.bukkit.Axis.X));
        zet(79, G + 2, 5, Material.MOSS_CARPET);
        zet(84, G + 1, -3, Material.MOSSY_COBBLESTONE);

        BlockData poortDicht = hek(Material.SPRUCE_FENCE, true);
        Kamer k = kamer(new Kamer("open-plek", "De open plek", box(65, G - 1, -13, 87, G + 10, 13), box(67, G, -10, 85, G + 6, 10),
                deur(poortDicht, 64, G + 1, -1, G + 3, 1), deur(poortDicht, 88, G + 1, -1, G + 3, 1)));
        for (int a = 0; a < 8; a++) {
            double hoek = Math.PI / 8 + a * Math.PI / 4;
            k.spawns.add(plek(cx + Math.round(Math.cos(hoek) * 7.5), G + 1, Math.round(Math.sin(hoek) * 7.5), 0f));
        }
        k.golf(Soort.VEENLOPER, Soort.VEENLOPER)                                   // rustig beginnen: één golf van twee
                .extra(Soort.SPIN, Soort.VEENLOPER, Soort.SPIN, Soort.VEENLOPER);       // met de bel: nog eens, zwaarder
        k.bel = bel(67, -5, BlockFace.EAST);

        sokkel(91, 2);
        vondst(new Vondst("schild", "Eikenschild", "houd rechts om te blokkeren", Material.SHIELD, plek(91, G + 2.3, 2, 0f), box(89, G, -3, 93, G + 4, 3)));
    }

    // ── 3. de Oude Eik ──────────────────────────────────────────────────────────────────────

    private void oudeEik() {
        // een eeuwenoude eik (4 × 4), hol: aan de achterkant (noord) kun je erin kruipen
        int ex = 99, ez = -8;                                   // stam: ex … ex+3, ez … ez+3
        for (int y = G + 1; y <= G + 12; y++) vul(ex, y, ez, ex + 3, y, ez + 3, Material.OAK_WOOD);
        vul(ex + 1, G + 1, ez + 1, ex + 2, G + 3, ez + 2, Material.AIR);          // de holte
        vul(ex + 1, G + 1, ez, ex + 2, G + 2, ez, Material.AIR);                 // het gat aan de achterkant
        vul(ex + 1, G, ez + 1, ex + 2, G, ez + 2, Material.ROOTED_DIRT);
        zet(ex + 2, G + 3, ez + 2, Material.SHROOMLIGHT);
        // wortels (niet voor het gat)
        int[][] wortels = {{-1, 1, -2, 1}, {-1, 3, -2, 3}, {4, 0, 5, 0}, {4, 2, 5, 2}, {1, 4, 1, 5}, {3, 4, 3, 5}, {0, -1, 0, -2}};
        for (int[] wt : wortels) {
            zet(ex + wt[0], G + 1, ez + wt[1], Material.OAK_WOOD);
            zet(ex + wt[2], G + 1, ez + wt[3], Material.ROOTED_DIRT);
        }
        // takken en een grote kruin
        int[][] takken = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] t : takken)
            for (int n = 1; n <= 4; n++) {
                int tx = (t[0] > 0 ? ex + 3 : t[0] < 0 ? ex : ex + 1) + t[0] * n, tz = (t[1] > 0 ? ez + 3 : t[1] < 0 ? ez : ez + 2) + t[1] * n;
                zet(tx, G + 9 + n / 2, tz, stam(Material.OAK_LOG, t[0] != 0 ? org.bukkit.Axis.X : org.bukkit.Axis.Z));
            }
        bladbol(ex + 2, G + 13, ez + 2, 6.5, Material.OAK_LEAVES);
        bladbol(ex + 6, G + 12, ez + 5, 4, Material.OAK_LEAVES);
        bladbol(ex - 3, G + 12, ez, 4, Material.AZALEA_LEAVES);
        for (int n = 0; n < 6; n++) zet(ex - 2 + n, G + 8, ez + 6, Bouwer.hangend(Material.LANTERN));
        for (int y = G + 9; y >= G + 7; y--) zet(ex - 1, y, ez + 3, rank(BlockFace.EAST));
        rustvuur(104, 4);
        rust(new Rust("eik", "Oude Eik", plek(103, G + 1, 0, -90f), box(96, G, -6, 108, G + 6, 7), List.of("schild")));
        Vondst hol = vondst(new Vondst(STUKJE_EIK, "Hartstukje", "", Material.REDSTONE,
                new Location(w, X0 + ex + 2, G + 1.4, ez + 2), box(ex + 1, G, ez + 1, ex + 2, G + 3, ez + 2)));
        hol.verborgen = true;                                   // je huisdier snuffelt hem op

        // de houtkap van Kees: stronken, een stapel stammen en het hakblok met zijn bijl
        for (int[] st : new int[][]{{90, -17}, {95, -23}, {91, -24}, {88, -19}}) zet(st[0], G + 1, st[1], Material.OAK_LOG);
        for (int rx = 88; rx <= 90; rx++) zet(rx, G + 1, -21, stam(Material.OAK_LOG, org.bukkit.Axis.X));
        zet(89, G + 2, -21, stam(Material.OAK_LOG, org.bukkit.Axis.X));
        zet(92, G + 1, -21, Material.STRIPPED_OAK_LOG);
        lantaarnpaal(96, -21, Material.SPRUCE_FENCE);
        Vondst bijl = vondst(new Vondst("bijl", "De bijl van Kees", "breng hem terug", Material.IRON_AXE,
                plek(92, G + 2.3, -21, 0f), box(91, G, -22, 93, G + 4, -20)));
        bijl.vereist = "g:kees-1";
        bijl.daarna = (spel, p, s) -> {
            if (s.missie("bijl") == 1) spel.missie(p, "bijl", 2);
        };
    }

    // ── 4. de tempelruïne: drie vuren, het zwaard ───────────────────────────────────────────

    private Material steen(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 10);
        return k < 4 ? Material.STONE_BRICKS : k < 7 ? Material.MOSSY_STONE_BRICKS : k < 9 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_COBBLESTONE;
    }

    private void muur(int rx1, int z1, int rx2, int z2, int min, int max, boolean heel) {
        for (int rx = Math.min(rx1, rx2); rx <= Math.max(rx1, rx2); rx++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                int x = X0 + rx;
                int hg = heel ? max : min + (int) (zacht(x, z, 3) * (max - min + 1));
                for (int y = G + 1; y <= G + hg; y++) zet(rx, y, z, steen(x, y, z));
            }
    }

    private void ruine() {
        verhaal(new Verhaal("tempel", box(110, G, -5, 117, G + 6, 5), List.of(
                r("Wiske", "Een oude tempel. Drie vuurschalen, één poort."),
                r("Wiske", "Steek ze snel aan, voor ze weer doven."))));
        // buitenmuren van het hof: noord en zuid gebroken, oost heel (met de poort)
        muur(114, -13, 141, -13, 2, 6, false);
        muur(114, 13, 141, 13, 2, 6, false);
        muur(114, -13, 114, -3, 3, 6, false);
        muur(114, 3, 114, 13, 3, 6, false);
        muur(141, -13, 141, 13, 7, 7, true);
        for (int z = -1; z <= 1; z++) vul(141, G + 1, z, 141, G + 3, z, Material.AIR);
        for (int z = -2; z <= 2; z++) zet(141, G + 4, z, Material.CHISELED_STONE_BRICKS);
        zet(140, G + 4, -2, Bouwer.gericht(Material.WALL_TORCH, BlockFace.WEST));
        zet(140, G + 4, 2, Bouwer.gericht(Material.WALL_TORCH, BlockFace.WEST));
        // poortboog in het westen
        for (int z : new int[]{-3, 3}) vul(114, G + 1, z, 114, G + 6, z, Material.CHISELED_STONE_BRICKS);
        for (int z = -3; z <= 3; z++) zet(114, G + 6, z, Material.STONE_BRICKS);
        for (int z = -2; z <= 2; z++) zet(114, G + 5, z, Bouwer.trap(Material.STONE_BRICK_STAIRS, BlockFace.WEST, true));
        // zuilen (sommige gebroken) en een omgevallen zuil
        for (int rx = 118; rx <= 136; rx += 6)
            for (int z : new int[]{-8, 8}) {
                if (rx == 130 && z == 8) continue;                   // daar staat de torenvoet
                int hg = 2 + kans(X0 + rx, z, 5);
                vul(rx, G + 1, z, rx, G + hg, z, Material.STONE_BRICKS);
                zet(rx, G + 1, z, Material.CHISELED_STONE_BRICKS);
                if (hg >= 5) zet(rx, G + hg + 1, z, Bouwer.plaat(Material.STONE_BRICK_SLAB, false));
            }
        for (int rx = 121; rx <= 125; rx++) zet(rx, G + 1, -4, stam(Material.STONE_BRICKS, org.bukkit.Axis.X));
        zet(126, G + 1, -4, Material.CHISELED_STONE_BRICKS);

        // vuur 1: op de grond
        int v1x = 120, v1z = -10;
        zet(v1x, G + 1, v1z, Material.CHISELED_STONE_BRICKS);
        // vuur 2: boven op een gebroken torenvoet, via puin omhoog
        vul(128, G + 1, 8, 132, G + 4, 11, Material.STONE_BRICKS);
        for (int rx = 128; rx <= 132; rx++)
            for (int z = 8; z <= 11; z++) zet(rx, G + 4, z, steen(X0 + rx, G + 4, z));
        for (int t = 0; t < 4; t++) {
            vul(124 + t, G + 1, 10, 124 + t, G + 1 + t, 10, Material.MOSSY_COBBLESTONE);
        }
        int v2x = 131, v2z = 10;
        // vuur 3: op een zuil in een vijver, via stapstenen
        for (int rx = 129; rx <= 139; rx++)
            for (int z = -12; z <= -4; z++) {
                if (Math.hypot(rx - 134, (z + 8) * 1.2) > 5.3) continue;
                zet(rx, G - 1, z, Material.WATER);
                zet(rx, G, z, Material.WATER);
                if (kans(X0 + rx, z, 6) == 0) zet(rx, G + 1, z, Material.LILY_PAD);
                else zet(rx, G + 1, z, Material.AIR);
            }
        int[][] stenen = {{131, -6}, {133, -7}, {135, -8}};
        for (int[] s : stenen) {
            zet(s[0], G, s[1], Material.MOSSY_COBBLESTONE);
            zet(s[0], G + 1, s[1], Material.MOSSY_STONE_BRICKS);
        }
        vul(137, G - 1, -10, 138, G + 2, -9, Material.STONE_BRICKS);
        int v3x = 137, v3z = -10;
        zet(138, G + 3, -9, Material.MOSSY_STONE_BRICK_WALL);
        // het heiligdom (dicht dak met een lichtgat boven de sokkel)
        muur(142, -5, 152, -5, 7, 7, true);
        muur(142, 5, 152, 5, 7, 7, true);
        muur(152, -5, 152, 5, 7, 7, true);
        for (int z = -1; z <= 1; z++) vul(152, G + 1, z, 152, G + 3, z, Material.AIR);
        for (int rx = 142; rx <= 152; rx++)
            for (int z = -5; z <= 5; z++) {
                boolean gat = Math.abs(rx - 147) <= 1 && Math.abs(z) <= 1;
                zet(rx, G + 8, z, gat ? Material.AIR.createBlockData() : Bouwer.plaat(Material.STONE_BRICK_SLAB, false));
                zet(rx, G, z, (rx + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.CHISELED_STONE_BRICKS);
            }
        for (int rx = 144; rx <= 150; rx += 3)
            for (int z : new int[]{-4, 4}) zet(rx, G + 7, z, Bouwer.hangend(Material.SOUL_LANTERN));
        sokkel(147, 0);

        Reeks vuren = reeks(new Reeks("vuren", "Drie vuren", Reeks.Hoe.KLIK, vuur(Material.CAMPFIRE, false), vuur(Material.CAMPFIRE, true),
                deur(Bouwer.ruit(Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH), 141, G + 1, -1, G + 3, 1),
                box(113, G - 2, -14, 142, G + 10, 14), 25));
        vuren.dingen.add(new int[]{X0 + v1x, G + 2, v1z});
        vuren.dingen.add(new int[]{X0 + v2x, G + 5, v2z});
        vuren.dingen.add(new int[]{X0 + v3x, G + 3, v3z});
        vuren.bijOplossen.add(r("Wiske", "De poort is open!"));
        vondst(new Vondst("zwaard", "Wachterszwaard", "het zwaard van de oude wachters", Material.IRON_SWORD,
                plek(147, G + 2.3, 0, 0f), box(145, G, -2, 149, G + 4, 2)));
        // achter de zuidmuur van het heiligdom: een kamertje, alleen open te breken met een bom (eiland 2)
        vul(143, G, 6, 148, G + 4, 9, Material.STONE_BRICKS);
        vul(144, G + 1, 6, 147, G + 3, 8, Material.AIR);
        vul(144, G, 6, 147, G, 8, Material.MOSSY_STONE_BRICKS);
        zet(146, G + 1, 8, Material.CHISELED_STONE_BRICKS);
        zet(145, G + 3, 7, Bouwer.hangend(Material.SOUL_LANTERN));
        barst(new Barst("tempelmuur", new Deur(BARST.createBlockData(), Material.AIR.createBlockData()).vlak(X0 + 145, G + 1, 5, X0 + 146, G + 2, 5),
                box(141, G - 1, -6, 153, G + 8, 10)));
        vondst(new Vondst(STUKJE_TEMPEL, "Hartstukje", "", Material.REDSTONE, plek(146, G + 2.3, 8, 0f), box(144, G, 6, 147, G + 3, 8))).voorwerp = "bommen";
        verhaal(new Verhaal("barst", box(143, G, 2, 151, G + 5, 4), List.of(r("Wiske", "Die muur is gebarsten. Met een flinke klap…"))));
        rustvuur(157, -3);
        rust(new Rust("ruine", "Tempelruïne", plek(157, G + 1, 0, -90f), box(154, G, -4, 162, G + 6, 4), List.of("schild", "zwaard")));
    }

    // ── 5. de Kloof: op tijd over de pilaren ────────────────────────────────────────────────

    private void kloofBouw() {
        verhaal(new Verhaal("kloof", box(158, G, -4, 162, G + 6, 4), List.of(
                r("Wiske", "Die poort aan de overkant blijft niet lang open."),
                r("Wiske", "Stap op de plaat… en rennen!"))));
        // pilaren: rx, z, hoogte boven G (je staat erop op G + h + 1)
        int[][] pilaren = {{167, 0, 0}, {169, 1, 0}, {171, 1, 1}, {173, 2, 1}, {182, 2, 2}, {184, 1, 2}, {186, -1, 1},
                {188, -2, 1}, {191, -2, 1}, {193, -1, 0}, {195, 0, 0}, {197, 0, 0}, {198, 0, 0}};
        if (b.plaats) {
            for (int[] p : pilaren) {
                for (int y = g + 1; y <= G + p[2]; y++) zet(p[0], y, p[1], y >= G + p[2] - 1 ? Material.MOSSY_STONE_BRICKS : kies(X0 + p[0], y, Material.STONE, Material.ANDESITE, Material.COBBLESTONE));
                if (kans(X0 + p[0], p[1], 3) == 0) zet(p[0], G + p[2] + 1, p[1], Material.MOSS_CARPET);
            }
            // een omgevallen stam als brug (rx 175 … 180)
            for (int rx = 175; rx <= 180; rx++) zet(rx, G + 1, 2, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            for (int y = g + 1; y <= G; y++) {
                zet(175, y, 2, Material.STONE);
                zet(180, y, 2, Material.STONE);
            }
            // ladder terug omhoog (westwand)
            BlockData ladder = Bouwer.gericht(Material.LADDER, BlockFace.EAST);
            for (int y = g + 3; y <= G; y++) zet(166, y, 3, ladder);
            // startplaat en een paar lantaarns
            zet(164, G + 1, 0, Material.STONE_PRESSURE_PLATE);
            lantaarnpaal(163, -3, Material.SPRUCE_FENCE);
            // de poortmuur aan de overkant
            for (int z = -9; z <= 9; z++) {
                if (Math.abs(z) <= 1) continue;
                for (int y = G + 1; y <= G + 6; y++) zet(201, y, z, steen(X0 + 201, y, z));
            }
            for (int z = -2; z <= 2; z++) {
                zet(201, G + 4, z, Material.CHISELED_STONE_BRICKS);
                zet(201, G + 5, z, Material.STONE_BRICKS);
                zet(201, G + 6, z, Material.MOSSY_STONE_BRICKS);
            }
            zet(200, G + 4, -2, Bouwer.gericht(Material.WALL_TORCH, BlockFace.WEST));
            zet(200, G + 4, 2, Bouwer.gericht(Material.WALL_TORCH, BlockFace.WEST));
            // een richel in de oostwand, drie blokken boven het water: alleen te halen met de klauwhaak (eiland 3)
            vul(200, G - 3, -9, 202, G + 2, -5, Material.STONE);
            vul(200, G - 2, -8, 201, G - 2, -6, Material.MOSSY_COBBLESTONE);
            vul(200, G - 1, -8, 201, G + 1, -6, Material.AIR);
            zet(201, G + 1, -6, Material.MOSS_CARPET);
            zet(200, G + 2, -7, HAAKPUNT);
            // een waterval verderop in dezelfde wand (te ver van de richel om erlangs omhoog te zwemmen)
            vul(199, g + 2, -15, 201, G + 3, -13, Material.STONE);
            BlockData val = Material.WATER.createBlockData(d -> ((org.bukkit.block.data.Levelled) d).setLevel(8));
            for (int y = g + 2; y <= G + 2; y++) zet(200, y, -14, y <= g + 3 || y == G + 2 ? Material.WATER.createBlockData() : val);
            for (int y = g + 2; y <= G + 1; y++) zet(199, y, -14, Material.AIR);
            for (int y = g + 2; y <= g + 3; y++) zet(199, y, -14, Material.WATER);
            zet(199, G + 2, -14, Material.MOSSY_COBBLESTONE);
        }
        Uitdaging kloof = uitdaging(new Uitdaging("kloof", "De Kloof", box(162, G, -5, 164, G + 3, 5), box(202, G, -4, 205, G + 5, 4),
                box(158, g, -10, 206, G + 12, 10), deur(Bouwer.ruit(Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH), 201, G + 1, -1, G + 3, 1), 25));
        kloof.bord = new Location(w, X0 + 161.5, G + 3.4, -2.5);
        haakpunt(new Haakpunt("richel", X0 + 200, G + 2, -7, plek(200, G - 1, -7, 90f)));
        vondst(new Vondst(STUKJE_RICHEL, "Hartstukje", "", Material.REDSTONE, plek(201, G - 0.6, -7, 0f), box(200, G - 1, -8, 201, G + 1, -6))).voorwerp = "klauwhaak";
        verhaal(new Verhaal("richel", box(158, G, -4, 162, G + 6, 4), List.of(
                r("Wiske", "Een hartstukje op die richel! Nog te hoog."))));
        rustvuur(208, -3);
        rust(new Rust("kloofrand", "Kloofrand", plek(208, G + 1, 0, -90f), box(203, G, -4, 213, G + 6, 4), List.of("schild", "zwaard")));
        verhaal(new Verhaal("zout", box(203, G, -4, 213, G + 6, 4), List.of(
                r("Wiske", "Ruik je dat? Zout. De zee is niet ver meer."))));
    }

    // ── 6. het Veen (gevecht) ───────────────────────────────────────────────────────────────

    private void veen() {
        verhaal(new Verhaal("veen", box(211, G, -4, 215, G + 6, 4), List.of(
                r("Wiske", "Het Veen. Blijf op de droge plekken."))));
        int cx = 233;
        if (b.plaats) {
            for (int rx = cx - 20; rx <= cx + 20; rx++)
                for (int z = -15; z <= 15; z++) {
                    double e = Math.hypot((rx - cx) / 18.0, z / 13.0);
                    if (e < 0.9 || e > 1.04 || Math.abs(z) <= 1) continue;
                    int hg = 5 + kans(X0 + rx, z, 2);
                    for (int y = G + 1; y <= G + hg; y++) zet(rx, y, z, kans(X0 + rx, y * 7 + z, 3) == 0 ? Material.MUDDY_MANGROVE_ROOTS : Material.MANGROVE_ROOTS);
                    bladbol(rx, G + hg + 1, z, 1.2, Material.MANGROVE_LEAVES);
                }
            for (int poort : new int[]{216, 250})
                for (int z : new int[]{-2, 2}) vul(poort, G + 1, z, poort, G + 5, z, Material.MANGROVE_LOG);
            for (int poort : new int[]{216, 250}) {
                for (int z = -2; z <= 2; z++) zet(poort, G + 4, z, stam(Material.MANGROVE_LOG, org.bukkit.Axis.Z));
                zet(poort, G + 5, 0, Material.OCHRE_FROGLIGHT);
            }
            // bomen in het veen als dekking, een paar paddenstoelen
            int[][] bomen = {{226, -6}, {240, 6}, {236, -8}, {228, 7}};
            for (int[] t : bomen) {
                vul(t[0], G - 2, t[1], t[0], G, t[1], Material.MUD);
                boom(t[0], t[1], G);
            }
            for (int[] p : new int[][]{{224, 0}, {242, -1}, {233, 9}}) zet(p[0], G + 1, p[1], Material.OCHRE_FROGLIGHT);
        }
        BlockData wortels = Material.MANGROVE_ROOTS.createBlockData();
        Kamer k = kamer(new Kamer("veen", "Het Veen", box(217, G - 2, -13, 249, G + 12, 13), box(220, G - 1, -10, 246, G + 6, 10),
                deur(wortels, 216, G + 1, -1, G + 3, 1), deur(wortels, 250, G + 1, -1, G + 3, 1)));
        int[][] sp = {{224, -4}, {230, -9}, {238, -9}, {244, -4}, {244, 4}, {238, 9}, {230, 9}, {224, 4}};
        for (int[] s : sp) {
            if (b.plaats) {
                zet(s[0], G, s[1], Material.MOSS_BLOCK);
                zet(s[0], G + 1, s[1], Material.AIR);
                zet(s[0], G + 2, s[1], Material.AIR);
            }
            k.spawns.add(plek(s[0], G + 1, s[1], 0f));
        }
        k.golf(Soort.MOSSCHUTTER, Soort.VEENLOPER, Soort.SPIN)
                .golf(Soort.VEENREUS, Soort.MOSSCHUTTER)
                .extra(Soort.SPIN, Soort.SPIN, Soort.MOSSCHUTTER, Soort.VEENLOPER);
        zet(219, G, -4, Material.MOSS_BLOCK);
        k.bel = bel(219, -4, BlockFace.EAST);
        // drie kruiden voor Hanne (pas te zien als ze erom vroeg)
        Object[][] kruiden = {{"kruid:veenbes", "Veenbes", Material.SWEET_BERRIES, 234, -11}, {"kruid:zonnedauw", "Zonnedauw", Material.TORCHFLOWER, 221, 6},
                {"kruid:moerasmunt", "Moerasmunt", Material.MANGROVE_PROPAGULE, 245, 6}};
        for (Object[] kr : kruiden) {
            int rx = (int) kr[3], z = (int) kr[4];
            zet(rx, G, z, Material.MOSS_BLOCK);
            zet(rx, G + 1, z, Material.AIR);
            zet(rx, G + 2, z, Material.AIR);
            Vondst v = vondst(new Vondst((String) kr[0], (String) kr[1], "voor Hanne", (Material) kr[2], plek(rx, G + 1.3, z, 0f), box(rx - 1, G, z - 1, rx + 1, G + 3, z + 1)));
            v.vereist = "g:hanne-1";
            v.daarna = (spel, p, s) -> {
                if (s.aantal("kruid:") >= kruiden.length && s.missie("moeraskruid") == 1) spel.missie(p, "moeraskruid", 2);
            };
        }
        sokkel(253, 3);
        vondst(new Vondst("boog", "Jagersboog", "raakt wat ver weg is", Material.BOW, plek(253, G + 2.3, 3, 0f), box(251, G, -3, 255, G + 4, 4)));
    }

    // ── 7. de moerashut ─────────────────────────────────────────────────────────────────────

    private void moerashut() {
        int x1 = 254, x2 = 259, z1 = -6, z2 = -3;
        if (b.plaats) {
            for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) vul(c[0], G + 1, c[1], c[0], G + 2, c[1], Material.MANGROVE_LOG);
            vul(x1, G + 3, z1, x2, G + 3, z2, Material.MANGROVE_PLANKS);
            vul(x1, G + 4, z1, x2, G + 6, z2, Material.SPRUCE_PLANKS);
            vul(x1 + 1, G + 4, z1 + 1, x2 - 1, G + 6, z2 - 1, Material.AIR);
            for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) vul(c[0], G + 4, c[1], c[0], G + 6, c[1], Material.MANGROVE_LOG);
            zet(256, G + 4, z2, Material.AIR);
            zet(256, G + 5, z2, Material.AIR);
            zet(258, G + 5, z2, hek(Material.GLASS_PANE, false));
            for (int rx = x1 - 1; rx <= x2 + 1; rx++)
                for (int z = z1 - 1; z <= z2 + 1; z++) zet(rx, G + 7, z, Bouwer.plaat(Material.MANGROVE_SLAB, false));
            for (int y = G + 1; y <= G + 3; y++) zet(256, y, z2 + 1, Bouwer.gericht(Material.LADDER, BlockFace.SOUTH));
            zet(257, G + 6, z2 - 1, Bouwer.hangend(Material.LANTERN));
            zet(255, G + 4, z1 + 1, Material.CAULDRON);
            // onder de hut: Hannes ketel, haar brouwsel en een ton
            vul(255, G, -5, 258, G, -3, Material.COARSE_DIRT);
            vul(255, G + 1, -5, 258, G + 2, -3, Material.AIR);
            zet(255, G + 1, -4, Material.WATER_CAULDRON.createBlockData(d -> ((org.bukkit.block.data.Levelled) d).setLevel(3)));
            zet(255, G + 1, -5, Material.BREWING_STAND);
            zet(258, G + 1, -5, Material.BARREL);
            zet(258, G + 2, -4, Bouwer.hangend(Material.LANTERN));
        }
        hanne();
        rustvuur(260, 3);
        rust(new Rust("moerashut", "Moerashut", plek(258, G + 1, 0, -90f), box(252, G, -4, 263, G + 6, 6), List.of("schild", "zwaard", "boog")));
    }

    /** Hanne, de kruidenvrouw onder de moerashut: zijmissie «Moeraskruid» (drie kruiden in het Veen), beloning het eerste flesje. */
    private void hanne() {
        Persoon hanne = persoon(new Persoon("Hanne", plek(257, G + 1, -4, 0f), Registry.VILLAGER_PROFESSION.get(org.bukkit.NamespacedKey.minecraft("cleric")),
                List.of(r("Hanne", "Leeg? Het zielvuur hiernaast vult je flesje weer."))));
        hanne.streek = Villager.Type.SWAMP;
        missie("moeraskruid", "Moeraskruid", "Zoek veenbes, zonnedauw en moerasmunt in het Veen.", "Breng de kruiden naar Hanne.");
        hanne.gesprek("hanne-1", s -> s.missie("moeraskruid") == 0, List.of(
                        r("Hanne", "Een bezoeker! Pas op, de ketel is heet."),
                        r("Hanne", "Ik brouw bronwater, maar mijn kruiden zijn op."),
                        r("Hanne", "Ze groeien in het Veen. Breng je er drie?")), (spel, p, s) -> spel.missie(p, "moeraskruid", 1))
                .gesprek(null, s -> s.missie("moeraskruid") == 1, List.of(r("Hanne", "Veenbes, zonnedauw, moerasmunt. Ze glanzen wat.")), null)
                .gesprek("hanne-2", s -> s.missie("moeraskruid") == 2, List.of(
                        r("Hanne", "Alle drie! Hier, een flesje bronwater.")), (spel, p, s) -> {
                    spel.missie(p, "moeraskruid", ZeldaVoortgang.KLAAR);
                    spel.beloon(p, "fles1", "Flesje", "drink als je gewond bent");
                    spel.uitleg(p, r("Wiske", "Leeg? Een bron of zielvuur vult het weer."));
                });
    }

    // ── 8. de doelen: raak ze alle drie, dan komt de brug ───────────────────────────────────

    private void doelen() {
        verhaal(new Verhaal("doelen", box(261, G, -6, 264, G + 6, 6), List.of(
                r("Wiske", "Drie doelen aan de overkant. Raak ze snel na elkaar."))));
        int[][] doel = {{273, G + 3, -5}, {275, G + 6, 0}, {272, G + 2, 5}};
        if (b.plaats) {
            BlockData ladder = Bouwer.gericht(Material.LADDER, BlockFace.EAST);
            for (int y = g + 4; y <= G; y++) zet(266, y, -5, ladder);
            vul(265, g + 1, -5, 265, G, -5, Material.STONE_BRICKS);
            // kademuurtjes langs het kanaal
            for (int z = -6; z <= 6; z++) {
                if (Math.abs(z) <= 1) continue;
                if (z != -5) zet(265, G + 1, z, Material.STONE_BRICK_WALL);
                zet(271, G + 1, z, Material.STONE_BRICK_WALL);
            }
            // paaltjes en een boog voor de doelen
            vul(273, G + 1, -5, 273, G + 2, -5, Material.STONE_BRICKS);
            vul(272, G + 1, 5, 272, G + 1, 5, Material.STONE_BRICKS);
            for (int z : new int[]{-2, 2}) vul(275, G + 1, z, 275, G + 6, z, Material.STONE_BRICKS);
            for (int z = -2; z <= 2; z++) if (z != 0) zet(275, G + 6, z, Material.STONE_BRICKS);
            zet(275, G + 7, 0, Material.CHISELED_STONE_BRICKS);
            zet(264, G + 1, -3, Material.SPRUCE_FENCE);
            zet(264, G + 2, -3, Material.LANTERN);
        }
        Deur brug = new Deur(Material.AIR.createBlockData(), Material.SPRUCE_PLANKS.createBlockData()).vlak(X0 + 266, G, -1, X0 + 270, G, 1);
        Reeks r = reeks(new Reeks("doelen", "Drie doelen", Reeks.Hoe.PIJL, Material.TARGET.createBlockData(), Material.VERDANT_FROGLIGHT.createBlockData(),
                brug, box(260, G - 8, -8, 278, G + 12, 8), 15));
        for (int[] d : doel) r.dingen.add(new int[]{X0 + d[0], d[1], d[2]});
        r.bijOplossen.add(r("Wiske", "Raak! Daar komt de brug."));
    }

    // ── 9. de Heksenkring (baas) ────────────────────────────────────────────────────────────

    private void heksenkring() {
        int cx = 296;
        if (b.plaats) {
            for (int rx = cx - 16; rx <= cx + 16; rx++)
                for (int z = -16; z <= 16; z++) {
                    double d = Math.hypot(rx - cx, z);
                    if (d < 13 || d >= 14.6 || Math.abs(z) <= 1) continue;
                    int hg = 6 + kans(X0 + rx, z, 3);
                    for (int y = G + 1; y <= G + hg; y++)
                        zet(rx, y, z, kies(X0 + rx, y * 5 + z, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.MOSSY_COBBLESTONE, Material.DEEPSLATE_BRICKS));
                }
            for (int poort : new int[]{283, 309}) {
                for (int z : new int[]{-2, 2}) vul(poort, G + 1, z, poort, G + 6, z, Material.CHISELED_DEEPSLATE);
                for (int z = -2; z <= 2; z++) {
                    zet(poort, G + 4, z, Material.DEEPSLATE_TILES);
                    zet(poort, G + 5, z, Material.DEEPSLATE_TILES);
                }
                zet(poort + (poort < cx ? -1 : 1), G + 4, 0, Material.SOUL_LANTERN);
            }
            // vier reuzenpaddenstoelen (de heks verschijnt er bovenop)
            for (int[] p : new int[][]{{cx - 6, -6}, {cx + 6, -6}, {cx - 6, 6}, {cx + 6, 6}}) {
                vul(p[0], G + 1, p[1], p[0], G + 5, p[1], Material.MUSHROOM_STEM);
                Material hoed = (p[0] + p[1]) % 2 == 0 ? Material.RED_MUSHROOM_BLOCK : Material.BROWN_MUSHROOM_BLOCK;
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                        zet(p[0] + dx, G + 6, p[1] + dz, hoed);
                    }
                zet(p[0], G + 5, p[1] + 1, Material.SHROOMLIGHT);
            }
            // ketel in het midden, ring van kaarsen en zielvuren
            zet(cx, G + 1, 0, Material.CAULDRON);
            for (int a = 0; a < 12; a++) {
                double hoek = a * Math.PI / 6;
                int rx = cx + (int) Math.round(Math.cos(hoek) * 11), z = (int) Math.round(Math.sin(hoek) * 11);
                if (Math.abs(z) <= 2) continue;
                zet(rx, G + 1, z, a % 2 == 0 ? Material.SOUL_LANTERN.createBlockData() : vuur(Material.SOUL_CAMPFIRE, true));
            }
        }
        BlockData tralies = Bouwer.ruit(Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH);
        Kamer k = kamer(new Kamer("heksenkring", "Heksenkring", box(284, G - 1, -14, 308, G + 16, 14), box(286, G, -10, 306, G + 8, 10),
                deur(tralies, 283, G + 1, -1, G + 3, 1), deur(tralies, 309, G + 1, -1, G + 3, 1)));
        k.muziek = ZeldaMuziek.BAAS;
        k.hemel = 15500;
        k.bijStart.add(r("Grauwmoer", "Een kind? Stuurt Morgal mij een kind?"));
        for (int a = 0; a < 6; a++) {
            double hoek = a * Math.PI / 3 + Math.PI / 6;
            k.spawns.add(plek(cx + Math.round(Math.cos(hoek) * 9), G + 1, Math.round(Math.sin(hoek) * 9), 0f));
        }
        List<Location> toppen = new ArrayList<>(), grond = new ArrayList<>();
        for (int[] p : new int[][]{{cx - 6, -6}, {cx + 6, -6}, {cx - 6, 6}, {cx + 6, 6}}) toppen.add(plek(p[0], G + 7, p[1], 90f));
        for (int[] p : new int[][]{{cx, -3}, {cx + 4, 2}, {cx - 4, 2}, {cx, 5}}) grond.add(plek(p[0], G + 1, p[1], 90f));
        k.baas = new ZeldaBaas("Grauwmoer", toppen, grond,
                List.of(r("Grauwmoer", "Taai, voor een kind…")), List.of(r("Wiske", "Ze is weg. Kijk, daar: een hart van licht.")));
        k.golf(Soort.HEKS);
        sokkel(313, 2);
        vondst(new Vondst("hart1", "Hartcontainer", "een hart erbij", Material.RED_DYE, plek(313, G + 2.3, 2, 0f), box(311, G, -3, 315, G + 4, 3)));
    }

    // ── 10. de haven: de kade, de pier, de Zilvermeeuw ──────────────────────────────────────

    private void huis(int x1, int z1, int x2, int z2, Material muur, Material dak, BlockFace voor) {
        vul(x1, G + 1, z1, x2, G + 4, z2, muur);
        vul(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 4, z2 - 1, Material.AIR);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) vul(c[0], G + 1, c[1], c[0], G + 4, c[1], Material.SPRUCE_LOG);
        // schuin dak langs x
        int diep = z2 - z1;
        for (int t = 0; t <= diep / 2 + 1; t++) {
            int zn = z1 - 1 + t, zs = z2 + 1 - t;
            if (zn > zs) break;
            for (int rx = x1 - 1; rx <= x2 + 1; rx++) {
                if (zn == zs) zet(rx, G + 5 + t, zn, Bouwer.plaat(Material.SPRUCE_SLAB, false));
                else {
                    zet(rx, G + 5 + t, zn, Bouwer.trap(dak, BlockFace.SOUTH));
                    zet(rx, G + 5 + t, zs, Bouwer.trap(dak, BlockFace.NORTH));
                }
            }
            if (zn + 1 <= zs - 1) {
                vul(x1, G + 5 + t, zn + 1, x1, G + 5 + t, zs - 1, muur);
                vul(x2, G + 5 + t, zn + 1, x2, G + 5 + t, zs - 1, muur);
            }
        }
        int dz = voor == BlockFace.SOUTH ? z2 : z1;
        int mx = (x1 + x2) / 2;
        zet(mx, G + 1, dz, Material.AIR);
        zet(mx, G + 2, dz, Material.AIR);
        zet(mx, G + 3, dz + voor.getModZ(), Bouwer.gericht(Material.WALL_TORCH, voor));
        zet(mx - 2, G + 2, dz, hek(Material.GLASS_PANE, false));
        zet(mx + 2, G + 2, dz, hek(Material.GLASS_PANE, false));
        zet(mx, G + 3, (z1 + z2) / 2, Bouwer.hangend(Material.LANTERN));
    }

    private void haven() {
        rustvuur(318, -3);
        rust(new Rust("haven", "Haventrap", plek(319, G + 1, 0, -90f), box(314, G, -4, 323, G + 6, 4), List.of("schild", "zwaard", "boog", "hart1")));
        verhaal(new Verhaal("kade", box(320, G, -4, 323, G + 6, 4), List.of(
                r("Wiske", "Soldaten van Morgal. Ze hebben de kade bezet!"))));
        if (b.plaats) {
            // muren rond het plein
            for (int z = -13; z <= 13; z++)
                if (Math.abs(z) > 1) for (int y = G + 1; y <= G + 5; y++) zet(324, y, z, steen(X0 + 324, y, z));
            for (int rx = 324; rx <= 346; rx++)
                for (int z : new int[]{-13, 13}) for (int y = G + 1; y <= G + 5; y++) zet(rx, y, z, steen(X0 + rx, y, z));
            for (int z = -13; z <= 13; z++)
                if (Math.abs(z) > 1) {
                    for (int y = G + 1; y <= G + 4; y++) zet(346, y, z, Material.STONE_BRICKS);
                    zet(346, G + 5, z, z % 2 == 0 ? Material.STONE_BRICK_WALL : Material.AIR);
                }
            for (int poort : new int[]{324, 346}) {
                for (int z : new int[]{-2, 2}) vul(poort, G + 1, z, poort, G + 6, z, Material.CHISELED_STONE_BRICKS);
                for (int z = -2; z <= 2; z++) zet(poort, G + 5, z, Material.STONE_BRICKS);
                zet(poort, G + 4, -1, Material.STONE_BRICKS);
                zet(poort, G + 4, 0, Material.STONE_BRICKS);
                zet(poort, G + 4, 1, Material.STONE_BRICKS);
            }
            // huizen, marktkraam, tonnen
            huis(327, -12, 334, -7, Material.WHITE_TERRACOTTA, Material.SPRUCE_STAIRS, BlockFace.SOUTH);
            huis(337, -12, 344, -7, Material.BRICKS, Material.DARK_OAK_STAIRS, BlockFace.SOUTH);
            huis(328, 7, 335, 12, Material.MUD_BRICKS, Material.SPRUCE_STAIRS, BlockFace.NORTH);
            for (int[] p : new int[][]{{339, 8}, {343, 8}}) vul(p[0], G + 1, p[1], p[0], G + 2, p[1], Material.SPRUCE_FENCE);
            for (int rx = 338; rx <= 344; rx++) {
                zet(rx, G + 3, 8, rx % 2 == 0 ? Material.RED_WOOL : Material.WHITE_WOOL);
                zet(rx, G + 3, 9, rx % 2 == 0 ? Material.RED_WOOL : Material.WHITE_WOOL);
            }
            zet(340, G + 1, 9, Material.BARREL);
            zet(342, G + 1, 9, Material.HAY_BLOCK);
            for (int[] p : new int[][]{{326, -5}, {326, 4}, {344, -5}, {344, 5}}) zet(p[0], G + 1, p[1], Material.BARREL);
            lantaarnpaal(335, -4, Material.SPRUCE_FENCE);
            lantaarnpaal(335, 4, Material.SPRUCE_FENCE);
            // pier
            for (int rx = 347; rx <= 376; rx++) {
                for (int z = -1; z <= 1; z++) {
                    vul(rx, G - 4, z, rx, G - 1, z, Material.WATER);
                    zet(rx, G, z, Material.SPRUCE_PLANKS);
                    vul(rx, G + 1, z, rx, G + 4, z, Material.AIR);
                }
                if ((rx - 347) % 4 == 0)
                    for (int z : new int[]{-2, 2}) {
                        vul(rx, G - 5, z, rx, G + 1, z, Material.SPRUCE_LOG);
                        if ((rx - 347) % 8 == 0) zet(rx, G + 2, z, Material.LANTERN);
                    }
            }
            // loopplank naar het schip
            for (int z = 2; z <= 4; z++) zet(363, G + 1, z, Bouwer.plaat(Material.SPRUCE_SLAB, true));
            zet(363, G + 1, 2, Bouwer.plaat(Material.SPRUCE_SLAB, false));
            zet(363, G + 1, -2, Material.SPRUCE_LOG);
        }
        BlockData hekDicht = hek(Material.SPRUCE_FENCE, true);
        Kamer k = kamer(new Kamer("kade", "De kade", box(325, G - 1, -12, 345, G + 10, 12), box(328, G, -9, 342, G + 6, 9),
                deur(hekDicht, 324, G + 1, -1, G + 3, 1), deur(hekDicht, 346, G + 1, -1, G + 3, 1)));
        int[][] sp = {{330, -4}, {336, -5}, {342, -3}, {342, 3}, {336, 5}, {330, 4}};
        for (int[] s : sp) k.spawns.add(plek(s[0], G + 1, s[1], 90f));
        k.golf(Soort.SOLDAAT, Soort.SOLDAAT, Soort.KRUISBOOG)
                .extra(Soort.SOLDAAT, Soort.KRUISBOOG, Soort.KRUISBOOG, Soort.SOLDAAT);
        k.bel = bel(327, -3, BlockFace.EAST);

        // Brecht: na de kade vaart hij je naar elk eiland dat open is (een nieuw eiland verschijnt vanzelf in zijn menu)
        Persoon brecht = persoon(new Persoon("Brecht", plek(366, G + 1, -1, 90f), Registry.VILLAGER_PROFESSION.get(org.bukkit.NamespacedKey.minecraft("fisherman")), List.of(
                r("Brecht", "De Zilvermeeuw vaart bij het eerste licht."))));
        brecht.eenmalig("brecht-terug", s -> s.heeft(id()) && s.aantal("e:") >= 2, List.of(
                        r("Brecht", "Weer thuis? Het Lindewoud heeft je gemist.")), null)
                .gesprek(null, s -> s.heeft(id()), List.of(), (spel, p, s) -> spel.varen(p));
        // Lotje telt meeuwen aan het eind van de pier
        Persoon lotje = persoon(new Persoon("Lotje", plek(373, G + 1, 1, -90f), null, List.of(
                r("Lotje", "Negenenveertig, vijftig… Niet storen, hoor!"))));
        lotje.kind = true;
        lotje.eenmalig("lotje-1", null, List.of(
                        r("Lotje", "Ssst! Ik tel meeuwen."),
                        r("Lotje", "Toen de soldaten kwamen, vlogen ze allemaal weg."),
                        r("Lotje", "Nu komen ze terug. Zevenenveertig, achtenveertig…")), null)
                .eenmalig("lotje-zilver", s -> s.heeft("e:zilverstrand"), List.of(
                        r("Lotje", "Was je op het Zilverstrand? Zijn daar meer meeuwen?")), null);
        Verhaal einde = verhaal(new Verhaal("brecht", box(360, G, -2, 369, G + 5, 2), List.of(
                r("Brecht", "Jij joeg ze weg? Dan ben jij die van het dorp."),
                r("Brecht", "Om Nachtrots hangt nevel. Daar vaart niemand door."),
                r("Brecht", "Zodra de wind draait, varen we. Zeg maar wanneer."),
                r("Wiske", "We komen eraan, Zelda."),
                r(null, "Het Lindewoud · voltooid"))));
        einde.einde = true;
    }

    // ── het schip (versiering) ──────────────────────────────────────────────────────────────

    private void schip() {
        int a = 352, e = 381, mz = 8, dek = G + 1;
        for (int rx = a; rx <= e; rx++) {
            double t = (rx - a) / (double) (e - a);                       // 0 = achtersteven, 1 = boeg
            double breed = 4.2 * Math.min(1, Math.min(t * 3.5 + 0.35, (1 - t) * 2.6 + 0.1));
            int bodem = G - 3 + (t > 0.8 ? (int) ((t - 0.8) * 10) : 0);
            for (int y = bodem; y <= dek; y++) {
                double w = breed * (0.55 + 0.45 * (y - bodem) / (double) (dek - bodem + 0.01));
                int wi = (int) Math.round(w);
                for (int z = mz - wi; z <= mz + wi; z++) {
                    boolean rand = Math.abs(z - mz) == wi || y == bodem;
                    if (y == dek) zet(rx, y, z, rand ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                    else if (rand) zet(rx, y, z, y == G - 1 ? Material.WHITE_TERRACOTTA : Material.DARK_OAK_PLANKS);
                    else zet(rx, y, z, Material.AIR);
                }
            }
            int wi = (int) Math.round(breed);
            if (wi >= 1) {
                zet(rx, dek + 1, mz - wi, Material.DARK_OAK_FENCE);
                zet(rx, dek + 1, mz + wi, Material.DARK_OAK_FENCE);
            }
        }
        // kajuit op de achtersteven
        vul(a + 1, dek + 1, mz - 3, a + 5, dek + 3, mz + 3, Material.DARK_OAK_PLANKS);
        vul(a + 2, dek + 1, mz - 2, a + 4, dek + 2, mz + 2, Material.AIR);
        vul(a + 1, dek + 4, mz - 3, a + 5, dek + 4, mz + 3, Material.SPRUCE_SLAB);
        zet(a + 5, dek + 1, mz, Material.AIR);
        zet(a + 5, dek + 2, mz, Material.AIR);
        zet(a + 3, dek + 2, mz - 3, hek(Material.GLASS_PANE, false));
        zet(a + 3, dek + 2, mz + 3, hek(Material.GLASS_PANE, false));
        zet(a + 3, dek + 2, mz, Bouwer.hangend(Material.LANTERN));
        zet(a + 1, dek + 5, mz, Material.LANTERN);
        // masten en zeilen (wit met een lichtblauwe baan)
        for (int[] m : new int[][]{{362, 15}, {372, 12}}) {
            vul(m[0], dek + 1, mz, m[0], dek + m[1], mz, Material.SPRUCE_LOG);
            for (int z = mz - 4; z <= mz + 4; z++) zet(m[0], dek + m[1] - 2, z, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            for (int y = dek + 4; y <= dek + m[1] - 3; y++)
                for (int z = mz - 4; z <= mz + 4; z++) {
                    if (z == mz) continue;
                    boolean buik = Math.abs(z - mz) <= 2 && y > dek + 5 && y < dek + m[1] - 4;
                    Material doek = y == dek + (m[1] + 2) / 2 ? Material.LIGHT_BLUE_WOOL : Material.WHITE_WOOL;
                    zet(m[0] + (buik ? 1 : 0), y, z, doek);
                }
            zet(m[0], dek + m[1] + 1, mz, Material.LIGHT_BLUE_WOOL);
            zet(m[0] + 1, dek + m[1] + 1, mz, Material.LIGHT_BLUE_WOOL);
        }
        // opening in de reling bij de loopplank
        for (int rx = 362; rx <= 364; rx++) zet(rx, dek + 1, 4, Material.AIR);
        // boegspriet
        for (int n = 0; n < 5; n++) zet(e + 1 + n, dek + 1 + n / 2, mz, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
        zet(e - 1, dek + 1, mz, Material.LANTERN);
    }

    // ── Nachtrots aan de horizon ────────────────────────────────────────────────────────────

    private Material zwart(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 10);
        return k < 5 ? Material.POLISHED_BLACKSTONE_BRICKS : k < 8 ? Material.DEEPSLATE_BRICKS : k < 9 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.BLACKSTONE;
    }

    private void toren(int cx, int cz, int r, int hoog, int voet) {
        for (int rx = cx - r; rx <= cx + r; rx++)
            for (int z = cz - r; z <= cz + r; z++) {
                double d = Math.hypot(rx - cx, z - cz);
                if (d > r + 0.4) continue;
                boolean rand = d > r - 0.8;
                for (int y = voet; y <= hoog; y++) {
                    if (rand) {
                        boolean raam = y % 6 == 3 && (rx == cx || z == cz) && y < hoog - 2;
                        zet(rx, y, z, raam ? Material.SHROOMLIGHT : zwart(X0 + rx, y, z));
                    } else if (y == hoog) zet(rx, y, z, Material.POLISHED_BLACKSTONE);
                }
            }
        // spits
        for (int t = 0; t <= r + 3; t++) {
            int rr = r + 1 - t;
            for (int rx = cx - rr; rx <= cx + rr; rx++)
                for (int z = cz - rr; z <= cz + rr; z++)
                    if (Math.hypot(rx - cx, z - cz) <= rr + 0.3) zet(rx, hoog + 1 + t * 2, z, Material.DEEPSLATE_TILES);
            if (rr >= 0) for (int rx = cx - rr; rx <= cx + rr; rx++)
                for (int z = cz - rr; z <= cz + rr; z++)
                    if (Math.hypot(rx - cx, z - cz) <= rr + 0.3) zet(rx, hoog + 2 + t * 2, z, Material.DEEPSLATE_TILES);
            if (rr <= 0) break;
        }
    }

    private void nachtrots() {
        int voet = G + 4, x1 = 432, x2 = 462, z1 = -13, z2 = 13;
        for (int rx = x1; rx <= x2; rx++)
            for (int z = z1; z <= z2; z++) {
                boolean rand = rx == x1 || rx == x2 || z == z1 || z == z2;
                if (!rand) continue;
                for (int y = voet; y <= G + 16; y++) zet(rx, y, z, zwart(X0 + rx, y, z));
                if ((rx + z) % 2 == 0) zet(rx, G + 17, z, zwart(X0 + rx, G + 17, z));
            }
        // poort aan de westkant met een rood licht erboven
        vul(x1, voet, -2, x1, voet + 5, 2, Material.AIR);
        vul(x1 - 1, voet, -2, x1 - 1, voet + 5, 2, Material.IRON_BARS);
        zet(x1, voet + 8, 0, Material.MAGMA_BLOCK);
        zet(x1 - 1, voet + 8, 0, Material.CRYING_OBSIDIAN);
        for (int[] t : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) toren(t[0], t[1], 3, G + 24, voet);
        // de burcht in het midden
        for (int rx = 440; rx <= 454; rx++)
            for (int z = -7; z <= 7; z++) {
                boolean rand = rx == 440 || rx == 454 || Math.abs(z) == 7;
                if (!rand) continue;
                for (int y = voet; y <= G + 26; y++) {
                    boolean raam = y % 5 == 2 && (rx + z) % 4 == 0 && y > voet + 3;
                    zet(rx, y, z, raam ? (rx == 440 ? Material.SHROOMLIGHT : Material.PURPLE_STAINED_GLASS) : zwart(X0 + rx, y, z));
                }
            }
        vul(441, G + 26, -6, 453, G + 26, 6, Material.POLISHED_BLACKSTONE);
        toren(447, 0, 4, G + 34, G + 26);
        zet(447, G + 46, 0, Material.CRYING_OBSIDIAN);
        zet(447, G + 47, 0, Material.MAGMA_BLOCK);
    }
}
