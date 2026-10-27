package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Eiland 2a, «Het Zilverstrand» west (zie ZELDA.md): zon, duinen en het vissersdorp Zoutkom. Je legt aan bij de
 * steiger; op het plein zit Ouwe Teun. Op de kaap in het westen staat de vuurtoren van Mira: meeuwenrovers bezetten het
 * bordes (het enige gevecht) en sloten haar op. In de kelder leid je het licht met draaispiegels naar een lamp; dan
 * gaan de tralies open en vind je Mira's oude bommenzak. Met de eerste bom breek je de gebarsten kelderwand open, naar
 * de duinen. Daar staat de Meeuwenrots: achter een gebarsten muur ligt in het meeuwennest het Zeelicht, Mira's lamp.
 * Breng hem terug: de vuurtoren brandt weer en zijn bundel wijst naar zee, waar de Getijdentempel ligt (eiland 2b).
 *
 * <p>Erbij: Hendrik de smid (zijmissie Zilverschelpen → maliënhemd), Pim met zijn vlieger in de duinen (tijdrace →
 * hartstukje), een smokkelgrot achter een gebarsten muur onder de kaap (hartstukje, met bommen) en een hartstukje boven
 * op de Meeuwenrots (haakpunt, pas met de klauwhaak van eiland 3). De dijk naar de tempel staat nog onder water: eiland
 * 2b sluit aan op {@link #DIJK_X} (zie ZELDA.md). Rustplaatsen houden hun nummer: zet een nieuwe altijd achteraan.
 */
final class ZeldaEiland2a extends ZeldaHoofdstuk {

    static final int VERSIE = 1;
    /** Het vak van dit halve eiland (eiland 2b krijgt x 1230 … 1360 van hetzelfde vak). */
    static final int X1 = 1060, X2 = 1229, Z1 = -480, Z2 = -180, OOST = 1360;
    /** De dijk naar de Getijdentempel loopt bij z −332 … −328 tot de rand van dit vak (2b gaat verder vanaf x 1230). */
    static final int DIJK_X = 1229, DIJK_Z1 = -332, DIJK_Z2 = -328;
    /** De hartstukjes van 2a (2b heeft {@code hs:zilverstrand-4}). */
    static final String STUKJE_PIM = "hs:zilverstrand-1", STUKJE_GROT = "hs:zilverstrand-2", STUKJE_ROTS = "hs:zilverstrand-3";
    /** Waar alles ligt: plein, kaap (en vuurtoren), duinpan, Meeuwenrots, steiger. */
    static final int PX = 1148, PZ = -296, KX = 1093, KZ = -322, LX = 1089, LZ = -322, DX = 1150, DZ = -372, RX = 1198, RZ = -398;

    private final Plugin plugin;
    private Bouwer b;
    int G, TOP;                                         // G = pad-hoogte (je staat op G+1); TOP = de kaap
    private int g;
    private int[][] top;                                // hoogste blok per kolom (alleen tijdens het bouwen)

    private static final byte ZEE = 0, STRAND = 1, GRAS = 2, DUIN = 3, KAAP = 4, KLIF = 5, PAD = 6, PLEIN = 7, ROTS = 8, ROTSTOP = 9, STEEN = 10;

    /** Wat er alleen tijdens het spel is: de vlieger van Pim. */
    private ItemDisplay vlieger;
    private Uitdaging race;
    private Persoon pim;

    ZeldaEiland2a(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override String id() { return "e2a"; }
    @Override String naam() { return "Het Zilverstrand"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda-e2a"; }

    // ── tijd, naam en muziek per plek ───────────────────────────────────────────────────────

    /** Ochtend bij de steiger, middag in het dorp en de duinen, avondzon bij de vuurtoren. */
    @Override
    long hemel(Location l) {
        double[][] anker = {{1131, -219, 1500}, {PX, PZ, 4500}, {KX, KZ, 11200}, {DX, DZ, 6500}, {RX, RZ, 8500}, {1215, -330, 5500}};
        double som = 0, gew = 0;
        for (double[] a : anker) {
            double d2 = Math.pow(l.getX() - a[0], 2) + Math.pow(l.getZ() - a[1], 2) + 25;
            som += a[2] / d2;
            gew += 1 / d2;
        }
        return (long) (som / gew);
    }

    @Override
    String plekNaam(Location l) {
        double x = l.getX(), z = l.getZ();
        if (Math.hypot(x - LX, z - LZ) < 6.5 || (x >= 1080 && x <= 1107 && z >= -333 && z <= -306 && l.getY() < TOP + 12)) return "De vuurtoren";
        if (Math.hypot(x - KX, z - KZ) < 22) return "De kaap";
        if (z > -262 && x > 1115 && x < 1175) return "De steiger";
        if (Math.hypot(x - PX, z - PZ) < 30) return "Zoutkom";
        if (Math.hypot(x - RX, z - RZ) < 17) return "De Meeuwenrots";
        if (x > 1196 && z > -350) return "De dijk";
        if (z < -338) return "De duinen";
        return "Het strand";
    }

    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return switch (plekNaam(l)) {
            case "De vuurtoren", "De kaap" -> ZeldaMuziek.VUURTOREN;
            case "De duinen", "De Meeuwenrots" -> ZeldaMuziek.DUINEN;
            default -> ZeldaMuziek.ZOUTKOM;
        };
    }

    // ── hulpjes ─────────────────────────────────────────────────────────────────────────────

    private void zet(int x, int y, int z, Material m) {
        b.zet(x, y, z, m);
    }

    private void zet(int x, int y, int z, BlockData d) {
        b.zet(x, y, z, d);
    }

    private void vul(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        b.vul(x1, y1, z1, x2, y2, z2, m);
    }

    private Location plek(double x, double y, double z, float yaw) {
        return new Location(w, x + 0.5, y, z + 0.5, yaw, 0f);
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

    private static BlockData hek(Material m, boolean langsZ) {
        return langsZ ? Bouwer.ruit(m, BlockFace.NORTH, BlockFace.SOUTH) : Bouwer.ruit(m, BlockFace.EAST, BlockFace.WEST);
    }

    private int grond(int x, int z) {
        if (top == null || x < X1 || x > X2 || z < Z1 || z > Z2) return G;
        return top[x - X1][z - Z1];
    }

    private Material rots(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 12);
        return k < 4 ? Material.STONE : k < 6 ? Material.ANDESITE : k < 8 ? Material.DIORITE : k < 10 ? Material.CALCITE : k < 11 ? Material.TUFF : Material.COBBLESTONE;
    }

    private Material muur(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 10);
        return k < 6 ? Material.STONE_BRICKS : k < 8 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
    }

    // ── het terrein ─────────────────────────────────────────────────────────────────────────

    private static double ellips(double x, double z, double cx, double cz, double a, double c) {
        return (1 - Math.hypot((x - cx) / a, (z - cz) / c)) * Math.min(a, c);
    }

    /** Hoe ver je op het land bent (negatief = zee). */
    private static double land(int x, int z) {
        double d = Math.max(Math.max(ellips(x, z, 1150, -318, 58, 64), ellips(x, z, KX, KZ, 20, 20)),
                Math.max(ellips(x, z, 1168, -386, 50, 30), Math.max(ellips(x, z, 1206, -330, 14, 11), ellips(x, z, 1112, -350, 34, 13))));
        return d + (zacht(x, z, 7) - 0.5) * 5;
    }

    /** De paden (lijnen met punten x, z): steiger, kaap, duinen, de Meeuwenrots, de dijk. */
    private static final int[][] PADEN = {
            {1130, -262, 1134, -276, 1142, -288},
            {1140, -300, 1130, -312, 1120, -321},
            {1150, -306, 1153, -330, 1151, -352, 1150, -366},
            {1089, -347, 1110, -352, 1132, -362, 1146, -370},
            {1157, -368, 1175, -364, 1188, -372, 1194, -380},
            {1158, -302, 1180, -314, 1196, -326, 1210, -330}};

    private static double lijn(double x, double z, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az, t = ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz);
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(x - ax - t * vx, z - az - t * vz);
    }

    private static double pad(int x, int z) {
        double best = 1e9;
        for (int[] p : PADEN)
            for (int i = 0; i + 3 < p.length; i += 2) best = Math.min(best, lijn(x, z, p[i], p[i + 1], p[i + 2], p[i + 3]));
        return best;
    }

    /** Duinen: zachte bulten (x, z, hoogte, straal), nooit steiler dan één blok per stap. */
    private static final int[][] BULTEN = {{1166, -392, 7, 12}, {1176, -372, 7, 12}, {1188, -364, 5, 10}, {1178, -356, 6, 10}, {1140, -392, 6, 12},
            {1128, -380, 4, 10}, {1160, -406, 6, 11}, {1212, -382, 5, 10}, {1212, -404, 4, 9}, {1152, -350, 3, 8}, {1182, -410, 5, 10}, {1132, -402, 5, 10}};

    private static double duin(int x, int z) {
        double hg = 0;
        for (int[] bt : BULTEN) {
            double u = Math.hypot(x - bt[0], z - bt[1]) / bt[3];
            if (u < 1) hg = Math.max(hg, bt[2] * Math.pow(1 - u * u, 2));
        }
        return hg + (zacht(x, z, 6) - 0.5) * 1.2;
    }

    /** Vaste plekken (x, z, straal, hoogte boven G, soort): vlak en zeker land (pannen, schelpen, strandjes). */
    private static final int[][] VAST = {
            {DX, DZ, 8, 0, DUIN}, {1161, -372, 3, 0, DUIN}, {1196, -382, 5, 0, DUIN},   // duinpan, start van de duinbaan, voor de Meeuwenrots
            {1089, -346, 3, 0, STRAND}, {1095, -300, 3, 0, STRAND},             // uitgang van de kelder, voor de smokkelgrot
            {1152, -260, 2, 0, STRAND}, {1112, -275, 2, 0, STRAND}, {1117, -355, 3, 0, STRAND}, {1207, -336, 2, 0, GRAS},
            {1172, -253, 2, 0, STEEN},                                          // rotsje in de branding (schelp)
            {1210, -330, 4, 0, GRAS}};                                          // begin van de dijk

    /** Waar de duinen beginnen (golvend, niet langs een liniaal). */
    private static double duingrens(int x, int z) {
        return -336 + (zacht(x, z, 13) - 0.5) * 14;
    }

    /** Hoogte (bovenste blok) en soort van een kolom. */
    private int[] vorm(int x, int z) {
        for (int[] v : VAST) if (Math.hypot(x - v[0], z - v[1]) <= v[2] + 0.4) return new int[]{G + v[3], v[4]};
        double d = land(x, z);
        double rr = Math.hypot(x - RX, z - RZ) + (zacht(x + 3, z - 7, 4) - 0.5) * 2;
        if (rr < 6.8) return new int[]{G + 20, ROTSTOP};
        if (rr < 10.5) return new int[]{G + 3 + (int) Math.round((10.5 - rr) / 3.7 * 16), ROTS};
        if (d <= 0) return new int[]{G - 2 - (int) Math.min(6, -d / 2.5), ZEE};
        if (x >= 1108 && x <= 1118 && z >= -324 && z <= -320) return new int[]{G, KLIF};      // de trap naar het bordes
        double kr = Math.hypot(x - KX, z - KZ) + (zacht(x + 5, z, 5) - 0.5) * 2.5;
        if (kr < 15.5) return new int[]{TOP, KAAP};
        if (kr < 19.5) return new int[]{TOP - (int) Math.round((kr - 15.5) / 4.0 * (TOP - G)), KLIF};
        if (d < 2.5) return new int[]{G - 1, STRAND};
        if (d < 6) return new int[]{G, STRAND};
        if (Math.hypot(x - PX, z - PZ) <= 10.5) return new int[]{G, PLEIN};
        double p = pad(x, z);
        if (z < duingrens(x, z)) {
            double zacht = Math.min(1, (d - 6) / 6.0) * Math.min(1, Math.max(0, (p - 2.5) / 5.0));
            return new int[]{G + (int) Math.round(Math.max(0, duin(x, z) * zacht)), DUIN};
        }
        if (p < 2.2) return new int[]{G, PAD};
        return new int[]{G, GRAS};
    }

    private void terrein() {
        byte[][] soort = new byte[X2 - X1 + 1][Z2 - Z1 + 1];
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                int[] v = vorm(x, z);
                top[x - X1][z - Z1] = v[0];
                soort[x - X1][z - Z1] = (byte) v[1];
            }
        // duinen nooit steiler dan één blok per stap (twee keer vegen: heen en terug)
        for (int ronde = 0; ronde < 2; ronde++)
            for (int i = 0; i < top.length; i++)
                for (int j = 0; j < top[0].length; j++) {
                    int a = ronde == 0 ? i : top.length - 1 - i, c = ronde == 0 ? j : top[0].length - 1 - j;
                    if (soort[a][c] != DUIN) continue;
                    for (int[] n : new int[][]{{a - 1, c}, {a + 1, c}, {a, c - 1}, {a, c + 1}})
                        if (n[0] >= 0 && n[1] >= 0 && n[0] < top.length && n[1] < top[0].length && soort[n[0]][n[1]] == DUIN)
                            top[a][c] = Math.min(top[a][c], top[n[0]][n[1]] + 1);
                }
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) kolom(x, z, top[x - X1][z - Z1], soort[x - X1][z - Z1]);
    }

    private void zee(int x, int z, int bodem) {
        int ymax = G + 44;
        vul(x, g + 1, z, x, bodem - 1, z, Material.STONE);
        zet(x, bodem, z, kans(x, z, 5) == 0 ? Material.GRAVEL : Material.SAND);
        vul(x, bodem + 1, z, x, G - 1, z, Material.WATER);
        if (bodem < G - 3 && kans(x + 9, z, 9) == 0) zet(x, bodem + 1, z, Material.SEAGRASS);
        vul(x, G, z, x, ymax, z, Material.AIR);
    }

    private void kolom(int x, int z, int hg, byte s) {
        int ymax = G + 44;
        if (s == ZEE) {
            zee(x, z, hg);
            return;
        }
        Material opp, onder;
        switch (s) {
            case STRAND, DUIN -> {
                opp = kans(x, z, 23) == 0 ? Material.GRAVEL : Material.SAND;
                onder = Material.SAND;
            }
            case KAAP -> {
                opp = kans(x, z, 6) == 0 ? rots(x, hg, z) : Material.GRASS_BLOCK;
                onder = Material.STONE;
            }
            case KLIF, ROTS, ROTSTOP, STEEN -> {
                opp = rots(x, hg, z);
                onder = Material.STONE;
            }
            case PAD -> {
                opp = kies(x, z, Material.DIRT_PATH, Material.DIRT_PATH, Material.COARSE_DIRT, Material.GRAVEL);
                onder = Material.DIRT;
            }
            case PLEIN -> {
                int r = (int) Math.hypot(x - PX, z - PZ);
                opp = r % 3 == 0 ? Material.CUT_SANDSTONE : kies(x, z, Material.SMOOTH_SANDSTONE, Material.SANDSTONE, Material.SMOOTH_SANDSTONE);
                onder = Material.SANDSTONE;
            }
            default -> {
                double r = zacht(x, z, 4);
                boolean zand = (land(x, z) < 9 && r < 0.45) || (z < duingrens(x, z) + 7 && r < 0.3 + (duingrens(x, z) + 7 - z) / 10.0);
                opp = zand ? Material.SAND : kans(x, z, 11) == 0 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
                onder = opp == Material.SAND ? Material.SAND : Material.DIRT;
            }
        }
        boolean steen = s == KAAP || s == KLIF || s == ROTS || s == ROTSTOP || s == STEEN;
        if (steen) {
            for (int y = g + 1; y < hg; y++) zet(x, y, z, rots(x, y, z));
        } else {
            vul(x, g + 1, z, x, hg - 4, z, onder == Material.SAND ? Material.SANDSTONE : Material.STONE);
            vul(x, hg - 3, z, x, hg - 1, z, onder);
        }
        zet(x, hg, z, opp);
        if (hg < G - 1) vul(x, hg + 1, z, x, G - 1, z, Material.WATER);
        vul(x, Math.max(hg + 1, G), z, x, ymax, z, Material.AIR);
        // begroeiing
        int k = kans(x + 11, z - 3, 100);
        Material plant = null;
        if (s == DUIN && opp == Material.SAND && pad(x, z) > 2.5) plant = k < 12 ? Material.SHORT_DRY_GRASS : k < 16 ? Material.TALL_DRY_GRASS : k < 17 ? Material.DEAD_BUSH : null;
        else if (s == STRAND && hg >= G && opp == Material.SAND) plant = k < 4 ? Material.SHORT_DRY_GRASS : null;
        else if ((s == GRAS || s == KAAP) && opp == Material.GRASS_BLOCK)
            plant = k < 12 ? Material.SHORT_GRASS : k < 14 ? kies(x, z, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.PINK_TULIP, Material.ALLIUM) : k < 16 ? Material.SHORT_DRY_GRASS : null;
        if (plant != null && hg >= G) zet(x, hg + 1, z, plant);
    }

    // ── kleine bouwstenen ───────────────────────────────────────────────────────────────────

    /** Rustplaats: zielvuur in een kring van stenen op hoogte {@code y} (de vloer), met een lantaarnpaal. */
    private void rustvuur(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) zet(x + dx, y, z + dz, dx == 0 && dz == 0 ? Material.COBBLESTONE : Material.SMOOTH_SANDSTONE);
        zet(x, y + 1, z, vuur(Material.SOUL_CAMPFIRE, true));
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            zet(x + f.getModX() * 2, y + 1, z + f.getModZ() * 2, Bouwer.trap(Material.SANDSTONE_STAIRS, f.getOppositeFace()));
        zet(x + 2, y + 1, z - 2, Material.CHISELED_SANDSTONE);
        zet(x + 2, y + 2, z - 2, Material.SANDSTONE_WALL);
        zet(x + 2, y + 3, z - 2, Material.SOUL_LANTERN);
    }

    private void sokkel(int x, int y, int z) {
        zet(x, y, z, Material.CHISELED_SANDSTONE);
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            zet(x + f.getModX(), y, z + f.getModZ(), Bouwer.trap(Material.SANDSTONE_STAIRS, f.getOppositeFace()));
    }

    private void lantaarnpaal(int x, int y, int z) {
        zet(x, y, z, Material.SPRUCE_FENCE);
        zet(x, y + 1, z, Material.SPRUCE_FENCE);
        zet(x, y + 2, z, Material.LANTERN);
    }

    /** Een vlaggetje (paal met een gekleurd doekje). */
    private void vlag(int x, int z, Material doek) {
        int y = grond(x, z);
        zet(x, y + 1, z, Material.SPRUCE_FENCE);
        zet(x, y + 2, z, Material.SPRUCE_FENCE);
        zet(x, y + 3, z, doek);
    }

    private Deur deur(BlockData dicht, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Deur(dicht, Material.AIR.createBlockData()).vlak(x1, y1, z1, x2, y2, z2);
    }

    private void zetDeur(Deur d, boolean open) {
        d.isOpen = open;
        for (int[] c : d.blokken) b.zetAltijd(c[0], c[1], c[2], open ? d.open : d.dicht);
    }

    /** Een wit huisje met een blauwgroen dak langs x, deur aan de kant {@code voor} (noord of zuid). */
    private void huis(int x1, int z1, int x2, int z2, BlockFace voor) {
        vul(x1, G, z1, x2, G, z2, Material.SPRUCE_PLANKS);
        vul(x1, G + 1, z1, x2, G + 4, z2, Material.CALCITE);
        vul(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 4, z2 - 1, Material.AIR);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) vul(c[0], G + 1, c[1], c[0], G + 4, c[1], Material.STRIPPED_BIRCH_LOG);
        int diep = z2 - z1;
        for (int t = 0; t <= diep / 2 + 1; t++) {
            int zn = z1 - 1 + t, zs = z2 + 1 - t;
            if (zn > zs) break;
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                if (zn == zs) zet(x, G + 5 + t, zn, Bouwer.plaat(Material.DARK_PRISMARINE_SLAB, false));
                else {
                    zet(x, G + 5 + t, zn, Bouwer.trap(Material.DARK_PRISMARINE_STAIRS, BlockFace.SOUTH));
                    zet(x, G + 5 + t, zs, Bouwer.trap(Material.DARK_PRISMARINE_STAIRS, BlockFace.NORTH));
                }
            }
            if (zn + 1 <= zs - 1) {
                vul(x1, G + 5 + t, zn + 1, x1, G + 5 + t, zs - 1, Material.CALCITE);
                vul(x2, G + 5 + t, zn + 1, x2, G + 5 + t, zs - 1, Material.CALCITE);
            }
        }
        int dz = voor == BlockFace.SOUTH ? z2 : z1;
        int mx = (x1 + x2) / 2;
        zet(mx, G + 1, dz, Material.AIR);
        zet(mx, G + 2, dz, Material.AIR);
        zet(mx, G + 3, dz + voor.getModZ(), Bouwer.gericht(Material.WALL_TORCH, voor));
        for (int x : new int[]{mx - 2, mx + 2}) {
            zet(x, G + 2, dz, hek(Material.GLASS_PANE, false));
            zet(x, G + 2, dz + voor.getModZ(), Bouwer.gericht(Material.SPRUCE_TRAPDOOR, voor));
        }
        zet(mx, G + 3, (z1 + z2) / 2, Bouwer.hangend(Material.LANTERN));
        zet(x1 + 1, G + 1, voor == BlockFace.SOUTH ? z1 + 1 : z2 - 1, Material.BARREL);
    }

    /** Een omgekeerde roeiboot op het strand. */
    private void boot(int x, int z) {
        int y = grond(x, z);
        for (int dx = 0; dx < 4; dx++) {
            zet(x + dx, y + 1, z, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.SOUTH, true));
            zet(x + dx, y + 1, z + 1, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.NORTH, true));
        }
        zet(x - 1, y + 1, z, Bouwer.plaat(Material.SPRUCE_SLAB, false));
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        g = bouwer.g;
        G = g + 8;
        TOP = G + 9;
        leeg(bouwer.w);
        if (vlieger != null) vlieger.remove();
        vlieger = null;
        speelgebied = Box.van(X1 + 2, g - 4, Z1 + 2, X2 - 1, G + 50, Z2 - 2);
        boolean bouwen = bouwer.plaats;
        boolean oost = eiland == null || eiland.delen().size() == 1;      // 2b is er nog niet: zee tot de rand van het vak
        List<long[]> chunks = new ArrayList<>();
        if (bouwen) {
            for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(oost ? OOST : X2, 16); cx++)
                for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) chunks.add(new long[]{cx, cz});
            b.daarna(() -> chunks.forEach(c -> w.addPluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = new int[X2 - X1 + 1][Z2 - Z1 + 1];
            terrein();
            if (oost) for (int x = X2 + 1; x <= OOST; x++)
                for (int z = Z1; z <= Z2; z++) zee(x, z, G - 8 + (int) (zacht(x, z, 9) * 2));
        }
        steiger();
        zoutkom();
        smokkelgrot();
        kaap();
        vuurtoren();
        kelder();
        noordstrand();
        duinpan();
        rots();
        dijk();
        einde();
        if (bouwen) {
            b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = null;
        }
        // de begintoestand van deuren en puzzels: altijd (ook zonder herbouw)
        for (Kamer k : kamers) {
            zetDeur(k.in, true);
            zetDeur(k.uit, false);
        }
        for (Uitdaging u : uitdagingen) zetDeur(u.poort, false);
        for (Barst br : barsten) zetDeur(br.muur, false);
        for (Spiegels sp : spiegels) {
            zetDeur(sp.deur, false);
            sp.opgelost = false;
            for (int i = 0; i < sp.spiegels.size(); i++) {
                int[] c = sp.spiegels.get(i);
                sp.stand.set(i, sp.begin.get(i));
                b.zetAltijd(c[0], c[1], c[2], Spiegels.blok(sp.begin.get(i)));
            }
            b.zetAltijd(sp.doel[0], sp.doel[1], sp.doel[2], sp.doelUit);
        }
        motor();
        b = null;
    }

    // ── 1. de steiger ───────────────────────────────────────────────────────────────────────

    private void steiger() {
        if (b.plaats) {
            for (int z = -266; z <= -224; z++) {
                for (int x = 1129; x <= 1131; x++) {
                    zet(x, G, z, Material.SPRUCE_PLANKS);
                    vul(x, G + 1, z, x, G + 4, z, Material.AIR);
                }
                if ((z + 266) % 4 == 0)
                    for (int x : new int[]{1128, 1132}) {
                        vul(x, G - 8, z, x, G + 1, z, Material.SPRUCE_LOG);
                        if ((z + 266) % 8 == 0) zet(x, G + 2, z, Material.LANTERN);
                    }
            }
            // de stenen kop met het zielvuur
            vul(1125, G - 8, -223, 1137, G - 1, -213, Material.STONE_BRICKS);
            for (int x = 1125; x <= 1137; x++)
                for (int z = -223; z <= -213; z++) {
                    zet(x, G, z, (x + z) % 3 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                    vul(x, G + 1, z, x, G + 5, z, Material.AIR);
                    boolean rand = x == 1125 || x == 1137 || z == -213 || (z == -223 && (x < 1129 || x > 1131));
                    if (rand && !(x == 1137 && z >= -220 && z <= -218)) zet(x, G + 1, z, Bouwer.paal(Material.STONE_BRICK_WALL));
                }
            for (int[] c : new int[][]{{1125, -213}, {1137, -213}, {1125, -223}, {1137, -223}}) zet(c[0], G + 2, c[1], Material.LANTERN);
            // touwen en tonnen, een loopplank naar het schip
            zet(1126, G + 1, -214, Material.BARREL);
            zet(1127, G + 1, -214, Material.BARREL);
            zet(1126, G + 2, -214, Material.BARREL);
            for (int z = -220; z <= -218; z++) zet(1138, G + 1, z, Bouwer.plaat(Material.SPRUCE_SLAB, false));
            schip(1139, -219);
        }
        rustvuur(1128, G, -218);
        rust(new Rust("steiger", "De steiger", plek(1132, G + 1, -219, 180f), Box.van(1124, G, -224, 1138, G + 6, -212), List.of()));
        verhaal(new Verhaal("aankomst", Box.van(1124, G, -224, 1138, G + 6, -212), List.of(
                r("Wiske", "Zout, zon en meeuwen. Het Zilverstrand!"),
                r("Wiske", "Kijk, op de kaap: een vuurtoren zonder licht."))));
        Persoon brecht = persoon(new Persoon("Brecht", plek(1135, G + 1, -215, 135f), beroep("fisherman"), List.of(
                r("Brecht", "Zeg maar waarheen. De Zilvermeeuw ligt klaar."))));
        brecht.gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.varen(p));
    }

    private static Villager.Profession beroep(String id) {
        return Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(id));
    }

    /** De Zilvermeeuw aan de steiger (dezelfde vorm als in de haven van het Lindewoud), boeg naar het oosten. */
    private void schip(int a, int mz) {
        int e = a + 29, dek = G + 1;
        for (int x = a; x <= e; x++) {
            double t = (x - a) / (double) (e - a);
            double breed = 4.2 * Math.min(1, Math.min(t * 3.5 + 0.35, (1 - t) * 2.6 + 0.1));
            int bodem = G - 3 + (t > 0.8 ? (int) ((t - 0.8) * 10) : 0);
            for (int y = bodem; y <= dek; y++) {
                double wd = breed * (0.55 + 0.45 * (y - bodem) / (double) (dek - bodem + 0.01));
                int wi = (int) Math.round(wd);
                for (int z = mz - wi; z <= mz + wi; z++) {
                    boolean rand = Math.abs(z - mz) == wi || y == bodem;
                    if (y == dek) zet(x, y, z, rand ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                    else if (rand) zet(x, y, z, y == G - 1 ? Material.WHITE_TERRACOTTA : Material.DARK_OAK_PLANKS);
                    else zet(x, y, z, Material.AIR);
                }
            }
            int wi = (int) Math.round(breed);
            if (wi >= 1) {
                zet(x, dek + 1, mz - wi, Material.DARK_OAK_FENCE);
                zet(x, dek + 1, mz + wi, Material.DARK_OAK_FENCE);
            }
        }
        for (int[] m : new int[][]{{a + 10, 15}, {a + 20, 12}}) {
            vul(m[0], dek + 1, mz, m[0], dek + m[1], mz, Material.SPRUCE_LOG);
            for (int z = mz - 4; z <= mz + 4; z++) zet(m[0], dek + m[1] - 2, z, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            for (int y = dek + 4; y <= dek + m[1] - 3; y++)
                for (int z = mz - 4; z <= mz + 4; z++) {
                    if (z == mz) continue;
                    Material doek = y == dek + (m[1] + 2) / 2 ? Material.LIGHT_BLUE_WOOL : Material.WHITE_WOOL;
                    zet(m[0], y, z, doek);                      // zeilen gereefd: plat tegen de mast
                }
            zet(m[0], dek + m[1] + 1, mz, Material.LIGHT_BLUE_WOOL);
        }
        for (int n = 0; n < 5; n++) zet(e + 1 + n, dek + 1 + n / 2, mz, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
        zet(e - 1, dek + 1, mz, Material.LANTERN);
        zet(a + 3, dek + 1, mz, Material.BARREL);
    }

    // ── 2. Zoutkom: het plein, Teun, Hendrik, de huisjes ────────────────────────────────────

    private void zoutkom() {
        verhaal(new Verhaal("zoutkom", Box.van(1124, G - 1, -270, 1138, G + 5, -258), List.of(
                r("Wiske", "Een vissersdorp. Wie zou hier iets weten?")))).vereist = "v:aankomst";
        if (b.plaats) {
            // de put midden op het plein
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    zet(PX + dx, G, PZ + dz, Material.SANDSTONE);
                    zet(PX + dx, G + 1, PZ + dz, dx == 0 && dz == 0 ? Material.WATER : Material.CUT_SANDSTONE);
                }
            zet(PX, G, PZ, Material.WATER);
            zet(PX - 1, G + 2, PZ, Material.SPRUCE_FENCE);
            zet(PX + 1, G + 2, PZ, Material.SPRUCE_FENCE);
            vul(PX - 1, G + 3, PZ, PX + 1, G + 3, PZ, Material.SPRUCE_SLAB);
            zet(PX, G + 2, PZ, Bouwer.hangend(Material.LANTERN));
            // Teuns bankje, uitkijkend over zee
            for (int x = 1142; x <= 1144; x++) zet(x, G + 1, -288, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.NORTH));
            zet(1141, G + 1, -288, Bouwer.gericht(Material.SPRUCE_TRAPDOOR, BlockFace.WEST));
            zet(1145, G + 1, -288, Bouwer.gericht(Material.SPRUCE_TRAPDOOR, BlockFace.EAST));
            for (int[] l : new int[][]{{1139, -302}, {1157, -290}, {1139, -290}}) lantaarnpaal(l[0], G + 1, l[1]);
            // huisjes
            huis(1137, -317, 1145, -311, BlockFace.SOUTH);
            huis(1126, -302, 1133, -296, BlockFace.SOUTH);
            huis(1157, -315, 1164, -309, BlockFace.SOUTH);
            huis(1158, -282, 1165, -277, BlockFace.NORTH);
            rokerij();
            smidse();
            // bloembakken en een boompje
            for (int x : new int[]{1139, 1143}) zet(x, G + 1, -310, Material.FLOWER_POT);
            zet(1147, G + 1, -311, Material.OAK_LOG);
            zet(1147, G + 2, -311, Material.OAK_LOG);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) zet(1147 + dx, G + 3, -311 + dz, Bouwer.blad(Material.FLOWERING_AZALEA_LEAVES));
            zet(1147, G + 4, -311, Bouwer.blad(Material.AZALEA_LEAVES));
            // netten te drogen en omgekeerde bootjes op het strand
            for (int x : new int[]{1146, 1154}) vul(x, grond(x, -262) + 1, -262, x, grond(x, -262) + 4, -262, Material.SPRUCE_FENCE);
            for (int x = 1147; x <= 1153; x++) {
                zet(x, grond(x, -262) + 4, -262, Material.COBWEB);
                if (x % 2 == 0) zet(x, grond(x, -262) + 3, -262, Material.COBWEB);
            }
            boot(1160, -264);
            boot(1121, -267);
        }
        rustvuur(1155, G, -301);
        rust(new Rust("plein", "Zoutkom", plek(1152, G + 1, -300, 90f), Box.van(1139, G, -306, 1158, G + 6, -287), List.of()));
        // Ouwe Teun op het bankje: de bakens (uitleg), en wat er met Mira is
        Persoon teun = persoon(new Persoon("Teun", plek(1145, G + 1, -290, 0f), beroep("fisherman"), List.of(
                r("Teun", "Vroeger ving je hier zilvervis. Zakken vol."))));
        teun.streek = Villager.Type.TAIGA;
        teun.eenmalig("teun-1", null, List.of(
                        r("Teun", "Een nieuw gezicht. Ga even zitten."),
                        r("Teun", "Drie bakens hielden de Glazen Zee licht."),
                        r("Teun", "Morgal doofde ze. Sindsdien hangt er nevel."),
                        r("Teun", "Mira van de vuurtoren weet waar het eerste ligt.")), null)
                .gesprek(null, s -> !s.heeft("k:bordes"), List.of(r("Teun", "Rovers op het bordes. Mira zit opgesloten.")), null)
                .eenmalig("teun-licht", s -> s.heeft(id()), List.of(
                        r("Teun", "De vuurtoren brandt weer. Dat ik dat nog zie."),
                        r("Teun", "De bundel wijst naar de tempel. Bij eb, jong.")), null);
        hendrik();
    }

    private void rokerij() {
        int x1 = 1130, x2 = 1136, z1 = -290, z2 = -286;
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) vul(c[0], G + 1, c[1], c[0], G + 3, c[1], Material.SPRUCE_LOG);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean gat = x == 1133 && z == -288;
                zet(x, G + 4, z, gat ? Material.AIR.createBlockData() : Bouwer.plaat(Material.SPRUCE_SLAB, false));
            }
        vul(x1, G + 1, z1, x2, G + 1, z1, Material.SPRUCE_PLANKS);
        zet(1133, G + 1, -288, vuur(Material.CAMPFIRE, true));
        for (int x : new int[]{1132, 1134}) zet(x, G + 3, -288, Material.DRIED_KELP_BLOCK);
        zet(1131, G + 3, -289, Bouwer.hangend(Material.LANTERN));
        zet(1135, G + 1, -289, Material.BARREL);
    }

    /** De smidse van Hendrik: open, met een aambeeld, een smeltoven en een kuipje lava. */
    private void smidse() {
        int x1 = 1158, x2 = 1166, z1 = -300, z2 = -292;
        vul(x1, G, z1, x2, G, z2, Material.STONE_BRICKS);
        vul(x1, G + 1, z1, x2, G + 5, z2, Material.AIR);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) vul(c[0], G + 1, c[1], c[0], G + 4, c[1], Material.STRIPPED_SPRUCE_LOG);
        vul(x1, G + 5, z1, x2, G + 5, z2, Material.SPRUCE_PLANKS);
        vul(x2, G + 1, z1 + 1, x2, G + 4, z2 - 1, Material.BRICKS);
        zet(1161, G + 1, -296, Material.ANVIL);
        zet(1165, G + 1, -298, Material.BLAST_FURNACE);
        zet(1165, G + 1, -294, Material.LAVA_CAULDRON);
        vul(1165, G + 2, -294, 1165, G + 8, -294, Material.BRICKS);
        zet(1165, G + 2, -294, Material.AIR);
        zet(1165, G + 9, -294, vuur(Material.CAMPFIRE, true));
        zet(1165, G + 1, -296, Material.SMITHING_TABLE);
        zet(1159, G + 1, -299, Material.GRINDSTONE);
        zet(1163, G + 1, -299, Material.BARREL);
        zet(1162, G + 4, -296, Bouwer.hangend(Material.LANTERN));
    }

    /** Hendrik de smid: zijmissie «Zilverschelpen» (vijf schelpen langs het strand), beloning het maliënhemd. */
    private void hendrik() {
        Persoon hendrik = persoon(new Persoon("Hendrik", plek(1160, G + 1, -295, 90f), beroep("armorer"), List.of(
                r("Hendrik", "Kapot? Breng het maar. Ik maak alles."))));
        hendrik.streek = Villager.Type.DESERT;
        missie("schelpen", "Zilverschelpen", "Zoek vijf zilverschelpen langs het strand.", "Breng de schelpen naar Hendrik.");
        hendrik.gesprek("hendrik-1", s -> s.missie("schelpen") == 0, List.of(
                        r("Hendrik", "Jij gaat naar die rovers? Zo, in een tuniek?"),
                        r("Hendrik", "Ik smeed je een maliënhemd. Maar ik mis zilver."),
                        r("Hendrik", "Vijf zilverschelpen, langs het strand. Breng je ze?")), (spel, p, s) -> spel.missie(p, "schelpen", 1))
                .gesprek(null, s -> s.missie("schelpen") == 1, List.of(r("Hendrik", "Vijf schelpen. Ze glanzen in het zand.")), null)
                .gesprek("hendrik-2", s -> s.missie("schelpen") == 2, List.of(
                        r("Hendrik", "Vijf! Even smeden…"),
                        r("Hendrik", "Hier. Licht, en het houdt een klap tegen.")), (spel, p, s) -> {
                    spel.missie(p, "schelpen", ZeldaVoortgang.KLAAR);
                    spel.beloon(p, "harnas1", "Maliënhemd", "klappen doen minder pijn");
                });
        int[][] schelpen = {{1152, -260}, {1112, -275}, {1117, -355}, {1172, -253}, {1207, -336}};
        for (int i = 0; i < schelpen.length; i++) {
            int x = schelpen[i][0], z = schelpen[i][1];
            Vondst v = vondst(new Vondst("schelp:" + (i + 1), "Zilverschelp", "voor Hendrik", Material.NAUTILUS_SHELL,
                    plek(x, G + 1.3, z, 0f), Box.van(x - 1, G, z - 1, x + 1, G + 3, z + 1)));
            v.vereist = "g:hendrik-1";
            v.daarna = (spel, p, s) -> {
                if (s.aantal("schelp:") >= schelpen.length && s.missie("schelpen") == 1) spel.missie(p, "schelpen", 2);
            };
        }
    }

    // ── 3. de smokkelgrot onder de kaap (bommen) ────────────────────────────────────────────

    private void smokkelgrot() {
        if (b.plaats) {
            for (int x = 1093; x <= 1098; x++)
                for (int y = G; y <= G + 5; y++)
                    for (int z = -310; z <= -304; z++) zet(x, y, z, rots(x, y, z));
            vul(1094, G + 1, -309, 1097, G + 3, -305, Material.AIR);
            vul(1094, G, -309, 1097, G, -305, Material.SAND);
            zet(1094, G + 1, -309, Material.BARREL);
            zet(1094, G + 2, -309, Material.BARREL);
            zet(1097, G + 1, -309, Material.BARREL);
            zet(1096, G + 3, -309, Bouwer.hangend(Material.LANTERN));
            zet(1095, G + 1, -309, Material.IRON_CHAIN);
        }
        barst(new Barst("grot", deur(BARST.createBlockData(), 1095, G + 1, -304, 1096, G + 2, -304),
                Box.van(1090, G - 1, -311, 1101, G + 6, -297))).voor = plek(1095, G + 1, -301, 180f);
        verhaal(new Verhaal("grot", Box.van(1091, G, -303, 1100, G + 5, -297), List.of(
                r("Wiske", "Die rotswand is gebarsten. Daarachter glinstert iets."))));
        vondst(new Vondst(STUKJE_GROT, "Hartstukje", "", Material.REDSTONE, plek(1096, G + 1.4, -307, 0f),
                Box.van(1094, G + 1, -309, 1097, G + 3, -305))).voorwerp = "bommen";
    }

    // ── 4. de kaap: de trap en het bordes (het gevecht) ─────────────────────────────────────

    private void kaap() {
        rustvuur(1123, G, -316);
        rust(new Rust("kaap", "De kaap", plek(1120, G + 1, -322, 90f), Box.van(1116, G, -328, 1128, G + 6, -313), List.of()));
        verhaal(new Verhaal("bordes", Box.van(1116, G, -328, 1128, G + 6, -313), List.of(
                r("Wiske", "Daarboven, op het bordes: rovers!"),
                r("Wiske", "Ben je er klaar voor? Dan de trap op."))));
        if (b.plaats) {
            // de trap: van de grond (x 1117) naar het bordes (x 1108)
            vul(1108, G, -324, 1108, TOP, -320, Material.STONE_BRICKS);
            vul(1108, TOP + 1, -324, 1118, TOP + 5, -320, Material.AIR);
            for (int i = 1; i <= 9; i++) {
                int x = 1108 + i, y = TOP - i;
                for (int z = -323; z <= -321; z++) {
                    vul(x, G - 2, z, x, y - 1, z, Material.STONE_BRICKS);
                    zet(x, y, z, Bouwer.trap(Material.STONE_BRICK_STAIRS, BlockFace.WEST));
                    vul(x, y + 1, z, x, TOP + 4, z, Material.AIR);
                }
                for (int z : new int[]{-324, -320}) {
                    vul(x, G - 2, z, x, y, z, Material.STONE_BRICKS);
                    zet(x, y + 1, z, Bouwer.paal(Material.STONE_BRICK_WALL));
                    vul(x, y + 2, z, x, TOP + 4, z, Material.AIR);
                }
            }
            // het bordes: plaveisel, borstwering, de poort van de rovers
            for (int x = 1088; x <= 1107; x++)
                for (int z = -328; z <= -316; z++) {
                    if (Math.hypot(x - LX, z - LZ) <= 5.4) continue;
                    zet(x, TOP, z, (x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                    vul(x, TOP + 1, z, x, TOP + 6, z, Material.AIR);
                    boolean rand = z == -328 || z == -316 || x == 1107;
                    if (rand && !(x == 1107 && z >= -323 && z <= -321)) {
                        zet(x, TOP + 1, z, muur(x, TOP + 1, z));
                        zet(x, TOP + 2, z, Bouwer.paal(Material.STONE_BRICK_WALL));
                    }
                }
            for (int z : new int[]{-324, -320}) vul(1107, TOP + 1, z, 1107, TOP + 4, z, Material.CHISELED_STONE_BRICKS);
            vul(1107, TOP + 4, -323, 1107, TOP + 4, -321, Material.STONE_BRICKS);
            for (int z : new int[]{-324, -320}) zet(1108, TOP + 3, z, Bouwer.gericht(Material.WALL_TORCH, BlockFace.EAST));
            zet(1099, TOP, -322, Material.GLASS);                           // lichtgat naar de kelder
            // spullen van de rovers
            zet(1105, TOP + 1, -327, Material.BARREL);
            zet(1104, TOP + 1, -327, Material.BARREL);
            zet(1105, TOP + 2, -327, Material.BARREL);
            vul(1096, TOP + 1, -317, 1096, TOP + 3, -317, Material.SPRUCE_FENCE);
            zet(1096, TOP + 4, -317, Bouwer.gericht(Material.BLACK_WALL_BANNER, BlockFace.EAST));
        }
        BlockData hekDicht = hek(Material.SPRUCE_FENCE, true);
        Kamer k = kamer(new Kamer("bordes", "Het bordes", Box.van(1095, TOP - 1, -327, 1106, TOP + 10, -317), Box.van(1097, TOP, -326, 1105, TOP + 6, -318),
                deur(hekDicht, 1107, TOP + 1, -323, 1107, TOP + 3, -321), deur(Material.DARK_OAK_PLANKS.createBlockData(), LX + 5, TOP + 1, LZ, LX + 5, TOP + 2, LZ)));
        for (int[] s : new int[][]{{1100, -325}, {1104, -325}, {1105, -322}, {1104, -319}, {1100, -319}, {1098, -322}})
            k.spawns.add(plek(s[0], TOP + 1, s[1], 90f));
        k.golf(Soort.ROVER, Soort.ROVER, Soort.ROVERSCHUTTER)
                .extra(Soort.ROVER, Soort.ROVERSCHUTTER, Soort.ROVER, Soort.ROVERSCHUTTER);
        k.bijStart.add(r("Wiske", "Meeuwenrovers! Pas op voor de schutter."));
        zet(1097, TOP + 1, -327, Bouwer.gericht(Material.BELL, BlockFace.EAST));
        k.bel = new int[]{1097, TOP + 1, -327};
        k.tips.addAll(List.of(r("Wiske", "De schutter eerst: die schiet van ver."),
                r("Wiske", "Onder aan de trap staat een zielvuur. Daar genees je.")));
    }

    // ── 5. de vuurtoren (Mira) ──────────────────────────────────────────────────────────────

    private void vuurtoren() {
        if (b.plaats) {
            for (int x = LX - 7; x <= LX + 7; x++)
                for (int z = LZ - 7; z <= LZ + 7; z++) {
                    double d = Math.hypot(x - LX, z - LZ);
                    if (d <= 5.4) {
                        boolean rand = d > 4.2;
                        for (int y = TOP + 1; y <= TOP + 24; y++) {
                            if (rand) zet(x, y, z, ((y - TOP - 1) / 4) % 2 == 0 ? Material.WHITE_CONCRETE : Material.RED_CONCRETE);
                            else zet(x, y, z, (y == TOP + 8 || y == TOP + 16) ? Material.SPRUCE_PLANKS : Material.AIR);
                        }
                        zet(x, TOP, z, rand ? Material.STONE_BRICKS : Material.SMOOTH_STONE);
                    }
                    if (d <= 6.4) zet(x, TOP + 25, z, Material.SMOOTH_STONE);           // de omloop
                    if (d > 5.6 && d <= 6.4) zet(x, TOP + 26, z, Material.IRON_BARS);
                    if (d <= 3.4) {                                                       // de lantaarnkamer
                        for (int y = TOP + 26; y <= TOP + 29; y++) zet(x, y, z, d > 2.2 ? Material.GLASS : Material.AIR);
                        zet(x, TOP + 30, z, Material.RED_CONCRETE);
                    }
                    if (d <= 2.4) zet(x, TOP + 31, z, Material.RED_CONCRETE);
                    if (d <= 1.4) zet(x, TOP + 32, z, Material.RED_CONCRETE);
                }
            zet(LX, TOP + 33, LZ, Material.RED_CONCRETE);
            zet(LX, TOP + 34, LZ, Material.LIGHTNING_ROD);
            for (int y : new int[]{TOP + 5, TOP + 13, TOP + 21}) {
                zet(LX, y, LZ - 5, hek(Material.GLASS_PANE, false));
                zet(LX - 5, y, LZ, hek(Material.GLASS_PANE, true));
                zet(LX, y, LZ + 5, hek(Material.GLASS_PANE, false));
            }
            // ladder naar boven (tegen de zuidwand) en de lamp
            BlockData ladder = Bouwer.gericht(Material.LADDER, BlockFace.NORTH);
            for (int y = TOP + 1; y <= TOP + 25; y++) zet(LX, y, LZ + 4, ladder);
            zet(LX, TOP + 26, LZ + 3, Material.AIR);
            zet(LX, TOP + 27, LZ + 3, Material.AIR);
            zet(LX, TOP + 26, LZ, Material.IRON_BLOCK);
            zet(LX, TOP + 27, LZ, Material.GLASS);                                       // hier hoort het Zeelicht
            zet(LX + 5, TOP + 3, LZ, Material.WHITE_CONCRETE);
            // binnen: Mira's spullen
            zet(LX - 3, TOP + 1, LZ - 2, Material.CARTOGRAPHY_TABLE);
            zet(LX - 2, TOP + 1, LZ - 3, Material.BARREL);
            zet(LX + 3, TOP + 1, LZ - 2, Material.BARREL);
            zet(LX - 3, TOP + 1, LZ + 2, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.WEST));
            zet(LX, TOP + 6, LZ, Bouwer.hangend(Material.LANTERN));
            zet(LX, TOP + 14, LZ, Bouwer.hangend(Material.LANTERN));
        }
        verhaal(new Verhaal("vuurtoren", Box.van(LX - 4, TOP + 1, LZ - 4, LX + 4, TOP + 5, LZ + 4), List.of(
                r("Wiske", "Er zit hier iemand. Praat eens met haar."))));
        Persoon mira = persoon(new Persoon("Mira", plek(LX - 3, TOP + 1, LZ, -90f), beroep("cartographer"), List.of(
                r("Mira", "Het licht brandt. Brecht ziet het vast al."))));
        mira.eenmalig("mira-1", null, List.of(
                        r("Mira", "Gered! Die rovers sloten me op in mijn toren."),
                        r("Mira", "Hun meeuwen pikten mijn lamp, het Zeelicht."),
                        r("Mira", "In de kelder liggen mijn oude bommen."),
                        r("Mira", "Achter de spiegels. Volg het licht.")), null)
                .gesprek(null, s -> !s.heeft("bommen"), List.of(r("Mira", "De kelder: draai de spiegels, volg het licht.")), null)
                .eenmalig("mira-2", s -> !s.heeft("zeelicht"), List.of(
                        r("Mira", "Mijn bommen! Wees er zuinig op."),
                        r("Mira", "De meeuwen nestelen op de Meeuwenrots, in de duinen.")), null)
                .gesprek(null, s -> !s.heeft("zeelicht"), List.of(r("Mira", "De Meeuwenrots, in de duinen. Zoek wat glimt.")), null);
    }

    // ── 6. de kelder: spiegels, de bommenzak, de gebarsten wand ─────────────────────────────

    private void kelder() {
        int y = G + 5;                                                      // het licht loopt op deze hoogte
        if (b.plaats) {
            vul(1080, G + 3, -332, 1098, G + 8, -312, Material.STONE_BRICKS);
            vul(1081, G + 4, -331, 1097, G + 7, -313, Material.AIR);
            for (int x = 1081; x <= 1097; x++)
                for (int z = -331; z <= -313; z++) zet(x, G + 3, z, (x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
            for (int[] l : new int[][]{{1083, -330}, {1095, -315}, {1083, -315}, {1096, -330}}) zet(l[0], G + 7, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
            // het lichtgat: daglicht door het glas in het bordes, door een schacht, door een gat in de oostwand
            zet(1099, y, -322, Material.SEA_LANTERN);
            vul(1099, y + 1, -322, 1099, TOP - 1, -322, Material.AIR);
            zet(1098, y, -322, Material.AIR);
            // voetstukken onder de spiegels
            for (int[] s : new int[][]{{1092, -322}, {1092, -328}, {1086, -328}, {1083, -322}}) zet(s[0], G + 4, s[1], Material.CHISELED_STONE_BRICKS);
            // het bergkamertje achter de tralies
            vul(1086, G + 3, -311, 1092, G + 8, -306, Material.STONE_BRICKS);
            vul(1087, G + 4, -310, 1091, G + 7, -307, Material.AIR);
            sokkel(1089, G + 4, -308);
            zet(1087, G + 4, -310, Material.BARREL);
            zet(1091, G + 4, -310, Material.BARREL);
            zet(1091, G + 5, -310, Material.BARREL);
            zet(1089, G + 7, -309, Bouwer.hangend(Material.LANTERN));
            // de gang naar buiten achter de gebarsten wand (noord), en een trapje naar het strand
            vul(1087, G + 3, -342, 1090, G + 6, -333, Material.STONE_BRICKS);
            vul(1088, G + 4, -342, 1089, G + 5, -333, Material.AIR);
            vul(1088, G + 6, -342, 1089, G + 6, -342, Material.AIR);
            for (int i = 1; i <= 3; i++)
                for (int x = 1088; x <= 1089; x++) {
                    vul(x, G - 2, -342 - i, x, G + 3 - i - 1, -342 - i, Material.STONE_BRICKS);
                    zet(x, G + 3 - i, -342 - i, Bouwer.trap(Material.STONE_BRICK_STAIRS, BlockFace.SOUTH));
                    vul(x, G + 4 - i, -342 - i, x, G + 7, -342 - i, Material.AIR);
                }
            zet(1088, G + 5, -334, Bouwer.gericht(Material.WALL_TORCH, BlockFace.SOUTH));
            // het luik uit de vuurtoren: een ladder omlaag, tegen een pilaar
            BlockData omlaag = Bouwer.gericht(Material.LADDER, BlockFace.SOUTH);
            vul(LX + 2, G + 4, LZ - 4, LX + 2, G + 7, LZ - 4, Material.STONE_BRICKS);
            for (int ly = G + 4; ly <= TOP; ly++) zet(LX + 2, ly, LZ - 3, omlaag);
        }
        verhaal(new Verhaal("spiegeluitleg", Box.van(LX + 1, G + 4, LZ - 5, LX + 4, G + 7, LZ - 1), List.of(
                r("Wiske", "Licht door een gat in de muur. En spiegels…"),
                r("Wiske", "Klik op een spiegel om hem te draaien.")))).uitleg = true;
        Spiegels sp = spiegels(new Spiegels("spiegels", "De spiegels", new int[]{1099, y, -322}, BlockFace.WEST, new int[]{1086, y, -312},
                Material.REDSTONE_LAMP.createBlockData(), Material.REDSTONE_LAMP.createBlockData(d -> ((org.bukkit.block.data.Lightable) d).setLit(true)),
                deur(Bouwer.ruit(Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST), 1088, G + 4, -312, 1090, G + 6, -312), Box.van(1080, G + 2, -333, 1098, G + 9, -311)));
        sp.spiegel(1092, y, -322, BlockFace.EAST, BlockFace.NORTH)
                .spiegel(1092, y, -328, BlockFace.SOUTH, BlockFace.WEST)
                .spiegel(1086, y, -328, BlockFace.NORTH, BlockFace.SOUTH)
                .spiegel(1083, y, -322, BlockFace.EAST, null);                     // een lokspiegel
        sp.bijOplossen.add(r("Wiske", "Het licht raakt de lamp. De tralies gaan open!"));
        sokkelVondst();
        Verhaal bom = verhaal(new Verhaal("uitleg-bom", Box.van(1087, G + 4, -310, 1091, G + 7, -307), List.of(
                r("Wiske", "Bommen! Rechtsklik om er een te gooien."),
                r("Wiske", "Een gebarsten muur? Daar helpt een bom."))));
        bom.vereist = "bommen";
        bom.uitleg = true;
        Barst wand = barstOpPad(new Barst("kelder", deur(BARST.createBlockData(), 1088, G + 4, -332, 1089, G + 5, -332), Box.van(1080, G + 2, -343, 1098, G + 9, -311)));
        wand.voor = plek(1088, G + 4, -329, 180f);
    }

    private void sokkelVondst() {
        vondst(new Vondst("bommen", "Bommenzak", "rechtsklik: een bom gooien", Material.FIREWORK_STAR, plek(1089, G + 5.3, -308, 0f),
                Box.van(1088, G + 4, -309, 1090, G + 7, -307)));
    }

    // ── 7. buiten: het noordstrand en het wrak ──────────────────────────────────────────────

    private void noordstrand() {
        verhaal(new Verhaal("duinen", Box.van(1084, G, -349, 1094, G + 6, -342), List.of(
                r("Wiske", "Buiten! Daar, in de duinen: een rots vol meeuwen."))));
        if (b.plaats) {
            // het wrak: een gebroken romp, half in het zand
            int y = G;
            for (int x = 1111; x <= 1123; x++) {
                int wi = x < 1113 || x > 1121 ? 1 : 2;
                for (int z = -355 - wi; z <= -355 + wi; z++) {
                    boolean rand = Math.abs(z + 355) == wi;
                    if (rand && !(x == 1116 && z > -355)) vul(x, y + 1, z, x, y + (x % 3 == 0 ? 1 : 2), z, Material.DARK_OAK_PLANKS);
                    zet(x, y, z, Material.SPRUCE_PLANKS);
                }
            }
            for (int x = 1113; x <= 1121; x += 4) {
                zet(x, y + 3, -357, Material.DARK_OAK_FENCE);
                zet(x, y + 3, -353, Material.DARK_OAK_FENCE);
            }
            for (int x = 1124; x <= 1131; x++) zet(x, y + 1, -353 + (x - 1124) / 3, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            zet(1112, y + 1, -355, Material.BARREL);
            for (int x = 1125; x <= 1129; x += 2) zet(x, y + 2, -354 + (x - 1124) / 3, Material.WHITE_CARPET);
        }
    }

    // ── 8. de duinpan: Pim en zijn vlieger (tijdrace) ───────────────────────────────────────

    /** De route van de vlieger (x, z), van de start tot de duintop. */
    static final int[][] VLIEGER = {{1160, -372}, {1170, -384}, {1181, -379}, {1185, -366}, {1178, -356}};

    private void duinpan() {
        rustvuur(1146, G, -376);
        rust(new Rust("duinpan", "De duinpan", plek(1150, G + 1, -371, -90f), Box.van(1142, G, -380, 1157, G + 6, -364), List.of("bommen")));
        if (b.plaats) {
            // startplaat, het hek van de duinbaan en vlaggetjes langs de route
            zet(1157, G + 1, -372, Material.BIRCH_PRESSURE_PLATE);
            for (int z : new int[]{-375, -369}) vul(1159, G + 1, z, 1159, G + 3, z, Material.STRIPPED_SPRUCE_LOG);
            for (int i = 0; i + 1 < VLIEGER.length; i++) {
                double ax = VLIEGER[i][0], az = VLIEGER[i][1], bx = VLIEGER[i + 1][0], bz = VLIEGER[i + 1][1];
                double len = Math.hypot(bx - ax, bz - az), nx = -(bz - az) / len, nz = (bx - ax) / len;
                for (double t = 0.3; t < len; t += 6) {
                    double x = ax + (bx - ax) * t / len, z = az + (bz - az) * t / len;
                    vlag((int) Math.round(x + nx * 3), (int) Math.round(z + nz * 3), Material.RED_WOOL);
                    vlag((int) Math.round(x - nx * 3), (int) Math.round(z - nz * 3), Material.WHITE_WOOL);
                }
            }
            int[] f = VLIEGER[VLIEGER.length - 1];
            int fy = grond(f[0], f[1]);
            vul(f[0] - 1, fy + 1, f[1] - 2, f[0] - 1, fy + 3, f[1] - 2, Material.SPRUCE_FENCE);
            zet(f[0] - 1, fy + 4, f[1] - 2, Material.YELLOW_WOOL);
            lantaarnpaal(1144, G + 1, -366);
        }
        verhaal(new Verhaal("uitleg-vlieger", Box.van(1151, G, -369, 1155, G + 4, -365), List.of(
                r("Wiske", "Stap op de plaat bij het hek, dan gaat de vlieger.")))).uitleg = true;
        race = uitdaging(new Uitdaging("vlieger", "De vlieger", Box.van(1156, G, -373, 1158, G + 3, -371),
                Box.van(VLIEGER[4][0] - 2, G, VLIEGER[4][1] - 2, VLIEGER[4][0] + 2, G + 10, VLIEGER[4][1] + 2),
                Box.van(1150, G - 1, -395, 1195, G + 14, -350), deur(hek(Material.SPRUCE_FENCE, true), 1159, G + 1, -374, 1159, G + 2, -370), 22));
        race.bord = new Location(w, 1155.5, G + 3.4, -376.5);
        race.daarna = (spel, p, s) -> {
            if (s.missie("vlieger") == 1) spel.missie(p, "vlieger", 2);
        };
        pim = persoon(new Persoon("Pim", plek(1153, G + 1, -367, 180f), null, List.of(
                r("Pim", "Nog een keer? Je tijd staat op het bord."))));
        pim.kind = true;
        stukje(STUKJE_PIM);
        missie("vlieger", "De vlieger", "Ren de vlieger achterna: stap op de plaat bij Pim.", "Vertel Pim dat het gelukt is.");
        pim.eenmalig("pim-1", null, List.of(
                        r("Pim", "Mijn vlieger trekt zo hard! Hou je hem bij?"),
                        r("Pim", "Stap op de plaat en ren hem achterna."),
                        r("Pim", "Haal je de duintop voor hij landt?")), (spel, p, s) -> spel.missie(p, "vlieger", s.beste("vlieger") != null ? 2 : 1))
                .gesprek("pim-2", s -> s.missie("vlieger") == 2, List.of(
                        r("Pim", "Je haalde hem! Hier, dit vond ik in het zand.")), (spel, p, s) -> {
                    spel.missie(p, "vlieger", ZeldaVoortgang.KLAAR);
                    spel.beloon(p, STUKJE_PIM, "Hartstukje", null);
                })
                .gesprek(null, s -> s.missie("vlieger") == 1, List.of(r("Pim", "Stap op de plaat. En dan rennen!")), null);
    }

    // ── 9. de Meeuwenrots: het nest, het Zeelicht ───────────────────────────────────────────

    private void rots() {
        if (b.plaats) {
            // de zuidwand met de gebarsten muur, de grot erachter
            for (int x = 1193; x <= 1203; x++)
                for (int y = G + 1; y <= G + 8; y++)
                    for (int z = -391; z <= -389; z++) zet(x, y, z, rots(x, y, z));
            vul(1194, G + 1, -402, 1202, G + 5, -391, Material.AIR);
            vul(1194, G, -402, 1202, G, -391, Material.GRAVEL);
            vul(1197, G + 1, -390, 1198, G + 2, -390, Material.AIR);
            vul(1196, G + 1, -388, 1199, G + 3, -386, Material.AIR);
            // het nest: hooi, veren en alles wat glimt
            for (int[] n : new int[][]{{1197, -400}, {1198, -401}, {1199, -400}, {1196, -399}, {1200, -399}}) zet(n[0], G + 1, n[1], Material.HAY_BLOCK);
            zet(1198, G + 1, -399, Material.WHITE_CARPET);
            zet(1195, G + 1, -397, Material.GOLD_BLOCK);
            zet(1201, G + 1, -396, Material.RAW_GOLD_BLOCK);
            zet(1201, G + 2, -396, Material.BELL);
            zet(1195, G + 1, -393, Material.LANTERN);
            zet(1201, G + 1, -393, Material.LANTERN);
            zet(1198, G + 5, -396, Bouwer.hangend(Material.LANTERN));
            for (int[] n : new int[][]{{1195, -401}, {1201, -401}, {1196, -394}}) zet(n[0], G + 1, n[1], Material.WHITE_CARPET);
            // boven op de rots: meeuwennesten en een haakpunt aan de rand
            for (int[] n : new int[][]{{1196, -396}, {1199, -400}, {1201, -395}, {1195, -400}}) {
                zet(n[0], G + 21, n[1], Material.HAY_BLOCK);
                zet(n[0] + 1, G + 21, n[1], Material.WHITE_CARPET);
            }
            vul(RX - 1, G + 20, -392, RX + 1, G + 20, -391, Material.STONE);
            zet(RX, G + 21, -391, HAAKPUNT);
            vul(1196, G + 20, -400, 1202, G + 20, -394, Material.STONE);
            vul(1196, G + 21, -398, 1198, G + 23, -396, Material.AIR);
        }
        verhaal(new Verhaal("meeuwenrots", Box.van(1191, G, -388, 1204, G + 6, -379), List.of(
                r("Wiske", "De Meeuwenrots. En een gebarsten muur…"),
                r("Wiske", "Daarachter zit vast hun nest. Gooi een bom!"))));
        Barst muur = barstOpPad(new Barst("rots", deur(BARST.createBlockData(), 1197, G + 1, -389, 1198, G + 2, -389), Box.van(1186, G - 1, -404, 1210, G + 8, -378)));
        muur.voor = plek(1197, G + 1, -385, 180f);
        vondst(new Vondst("zeelicht", "Het Zeelicht", "breng hem naar Mira", Material.SEA_LANTERN, plek(1198, G + 2.4, -400, 0f),
                Box.van(1196, G + 1, -402, 1200, G + 4, -397)));
        verhaal(new Verhaal("nest", Box.van(1194, G + 1, -402, 1202, G + 5, -391), List.of(
                r("Wiske", "Mira's lamp! Snel terug naar de vuurtoren."),
                r("Wiske", "Met je kaart reis je zo naar een zielvuur.")))).vereist = "zeelicht";
        verhaal(new Verhaal("top", Box.van(1191, G, -388, 1204, G + 6, -379), List.of(
                r("Wiske", "Boven op de rots glinstert nog iets. Te hoog…")))).vereist = "zeelicht";
        haakpunt(new Haakpunt("rots", RX, G + 21, -391, plek(RX, G + 21, -396, 180f)));
        vondst(new Vondst(STUKJE_ROTS, "Hartstukje", "", Material.REDSTONE, plek(1200, G + 21.4, -398, 0f),
                Box.van(1199, G + 21, -400, 1202, G + 24, -397))).voorwerp = "klauwhaak";
    }

    // ── 10. de dijk (eiland 2b sluit hier aan) ──────────────────────────────────────────────

    private void dijk() {
        if (b.plaats) {
            for (int x = 1206; x <= DIJK_X; x++) {
                boolean nat = x > 1220;
                int y = nat ? G - 2 : G;
                for (int z = DIJK_Z1; z <= DIJK_Z2; z++) {
                    vul(x, G - 8, z, x, y - 1, z, Material.STONE_BRICKS);
                    zet(x, y, z, kies(x, z, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS));
                    if (nat) zet(x, y + 1, z, Material.WATER);
                    else vul(x, y + 1, z, x, y + 4, z, Material.AIR);
                }
                if (x % 4 == 0)
                    for (int z : new int[]{DIJK_Z1 - 1, DIJK_Z2 + 1}) {
                        vul(x, G - 8, z, x, nat ? G - 1 : G + 1, z, Material.SPRUCE_LOG);
                        if (!nat) zet(x, G + 2, z, Material.LANTERN);
                    }
            }
            // het dijkhuisje met de peilschaal
            vul(1200, G, -341, 1205, G, -338, Material.SPRUCE_PLANKS);
            vul(1200, G + 1, -341, 1205, G + 3, -338, Material.CALCITE);
            vul(1201, G + 1, -340, 1204, G + 3, -339, Material.AIR);
            vul(1199, G + 4, -342, 1206, G + 4, -337, Material.DARK_PRISMARINE_SLAB);
            zet(1202, G + 1, -338, Material.AIR);
            zet(1202, G + 2, -338, Material.AIR);
            for (int y = G - 3; y <= G + 3; y++) zet(1219, y, -334, y % 2 == 0 ? Material.WHITE_CONCRETE : Material.RED_CONCRETE);
        }
        verhaal(new Verhaal("dijk", Box.van(1208, G, -335, 1220, G + 5, -325), List.of(
                r("Wiske", "De dijk loopt de zee in. Nu staat hij onder water."))));
    }

    // ── 11. terug bij Mira: het licht gaat aan (einde van 2a) ───────────────────────────────

    private void einde() {
        Verhaal einde = verhaal(new Verhaal("licht", Box.van(LX - 4, TOP + 1, LZ - 4, LX + 4, TOP + 5, LZ + 4), List.of(
                r("Mira", "Mijn Zeelicht! Ik zet hem meteen terug."),
                r("Mira", "Zie je de bundel? Hij wijst naar de tempel."),
                r("Wiske", "Het Getijdenbaken! Bij eb gaan we erheen."),
                r(null, "De vuurtoren brandt weer"))));
        einde.vereist = "zeelicht";
        einde.einde = true;
    }

    /**
     * Zonder blokken (dus zonder herbouw): de bron bij de Meeuwenrots, de doelen voor {@code /hint} en de volgorde van
     * het pad (de smokkelgrot ligt al vroeg aan het strand, maar is pas met bommen te halen).
     */
    private void motor() {
        bron("bron-rots", plek(1191, G + 1, -383, 0f));
        doel("teun", "Een dorp! Iemand weet vast meer.", "Ouwe Teun zit op het bankje op het plein.",
                plekVan("Teun"), s -> s.heeft("g:teun-1") || s.heeft("k:bordes"));
        doel("bordes", "Naar de vuurtoren, op de kaap in het westen.", "De trap op. Rovers bezetten het bordes.",
                plekVan("bordes"), s -> s.heeft("k:bordes"));
        doel("mira", "Is daar iemand in de vuurtoren?", "Door de deur achter het bordes. Praat met haar.",
                plekVan("Mira"), s -> s.heeft("g:mira-1") || s.heeft("r:spiegels") || s.heeft("bommen"));
        doel("spiegels", "In de kelder. Leid het licht naar de lamp.", "Klik op een spiegel om hem te draaien. Volg het licht.",
                plekVan("spiegels"), s -> s.heeft("r:spiegels") || s.heeft("bommen"));
        doel("bommen", "De tralies zijn open. Wat ligt daar?", "In het kamertje achter de tralies.",
                plekVan("bommen"), s -> s.heeft("bommen"));
        doel("kelder", "Die gebarsten wand. Gooi er een bom tegen!", "Bom in vak 4. Kijk naar de wand en rechtsklik.",
                plekVan("kelder"), s -> s.heeft("b:kelder") || s.rust(id()) >= 3);
        doel("rots", "De meeuwen nestelen op de Meeuwenrots, in de duinen.", "De grote rots in het noordoosten. Ook daar: een bom.",
                plekVan("rots"), s -> s.heeft("zeelicht"));
        doel("licht", "Breng het Zeelicht naar Mira.", "In de vuurtoren, op de kaap. Reis via je kaart.",
                plekVan("Mira"), s -> s.heeft(id()));
    }

    // ── tijdens het spel: de vlieger en het licht van de vuurtoren ──────────────────────────

    /** Waar de vlieger is op een deel {@code f} (0 … 1) van zijn route, hoog boven de duinen. */
    private Location route(double f) {
        double totaal = 0;
        for (int i = 0; i + 1 < VLIEGER.length; i++) totaal += Math.hypot(VLIEGER[i + 1][0] - VLIEGER[i][0], VLIEGER[i + 1][1] - VLIEGER[i][1]);
        double over = Math.max(0, Math.min(1, f)) * totaal;
        for (int i = 0; i + 1 < VLIEGER.length; i++) {
            double len = Math.hypot(VLIEGER[i + 1][0] - VLIEGER[i][0], VLIEGER[i + 1][1] - VLIEGER[i][1]);
            if (over <= len || i + 2 == VLIEGER.length) {
                double t = Math.min(1, over / len);
                double x = VLIEGER[i][0] + (VLIEGER[i + 1][0] - VLIEGER[i][0]) * t + 0.5, z = VLIEGER[i][1] + (VLIEGER[i + 1][1] - VLIEGER[i][1]) * t + 0.5;
                return new Location(w, x, w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 6.5, z);
            }
            over -= len;
        }
        return pim.plek.clone().add(0, 7, 0);
    }

    @Override
    void tik(ZeldaSpel spel, List<Player> wie, long nu) {
        vliegerTik(spel, wie, nu);
        licht(spel, wie);
    }

    /** De vlieger van Pim: hangt boven hem in de wind, en vliegt voor je uit als je de duinbaan rent. */
    private void vliegerTik(ZeldaSpel spel, List<Player> wie, long nu) {
        if (race == null || pim == null) return;
        boolean bij = false;
        for (Player p : wie) bij |= p.getLocation().distanceSquared(pim.plek) < 64 * 64;
        if (!bij) {
            if (vlieger != null) vlieger.remove();
            vlieger = null;
            return;
        }
        long start = Long.MAX_VALUE;
        for (long s : race.loopt.values()) start = Math.min(start, s);
        Location doel;
        if (start != Long.MAX_VALUE) doel = route((nu - start + 1000) / (race.seconden * 1000.0 * 0.8));
        else {
            double t = nu / 1000.0;
            doel = pim.plek.clone().add(2 + Math.sin(t * 0.7) * 1.5, 7 + Math.sin(t * 1.3) * 0.6, -1 + Math.cos(t * 0.5));
        }
        if (vlieger == null || !vlieger.isValid()) {
            vlieger = w.spawn(doel, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(ZeldaSpel.TAG);
                d.setItemStack(new ItemStack(Material.RED_STAINED_GLASS_PANE));
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(20);
                d.setTransformation(new org.bukkit.util.Transformation(new Vector3f(), new AxisAngle4f((float) Math.PI / 4, 0, 0, 1),
                        new Vector3f(1.6f, 1.6f, 1.6f), new AxisAngle4f()));
            });
        } else vlieger.teleport(doel);
        Location v = vlieger.getLocation();
        for (Player p : wie) {
            if (p.getLocation().distanceSquared(v) > 60 * 60) continue;
            p.spawnParticle(Particle.DUST, v.clone().add(0, -0.9, 0), 3, 0.05, 0.3, 0.05, 0, new Particle.DustOptions(org.bukkit.Color.WHITE, 0.7f));
            if (start == Long.MAX_VALUE) {                             // het touwtje naar Pim
                Location hand = pim.plek.clone().add(0, 0.8, 0);
                for (int i = 1; i < 8; i++) {
                    double f = i / 8.0;
                    p.spawnParticle(Particle.DUST, hand.clone().add(v.clone().subtract(hand).toVector().multiply(f)), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(0xdddddd), 0.4f));
                }
            }
        }
    }

    /** Wie het Zeelicht terugbracht ({@code e2a}), ziet de vuurtoren branden: de lamp en een bundel naar de tempel. */
    private void licht(ZeldaSpel spel, List<Player> wie) {
        Location lamp = new Location(w, LX + 0.5, TOP + 27.5, LZ + 0.5);
        BlockData aan = Material.SEA_LANTERN.createBlockData();
        org.bukkit.util.Vector richting = new org.bukkit.util.Vector(1300 - LX, -12, -330 - LZ).normalize();
        Particle.DustOptions licht = new Particle.DustOptions(org.bukkit.Color.fromRGB(0xfff4c0), 1.6f);
        for (Player p : wie) {
            if (!spel.voortgang.van(p.getUniqueId()).heeft(id()) || p.getLocation().distanceSquared(lamp) > 120 * 120) continue;
            p.sendBlockChange(lamp, aan);
            for (int i = 2; i < 90; i += 3) p.spawnParticle(Particle.DUST, lamp.clone().add(richting.clone().multiply(i)), 1, 0.15, 0.15, 0.15, 0, licht);
        }
    }
}
