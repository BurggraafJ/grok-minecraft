package nl.burggraaf.mc.zelda;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.SeaPickle;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

/**
 * Eiland 2b, «Het Zilverstrand» oost: de Getijdentempel (zie ZELDA.md). De dijk van Zoutkom loopt door naar het
 * Getijdenplein, een oud plein van de getijdenwachters met een zielvuur: de hub van dit halve eiland. Daar wacht
 * Tijmen; hij geeft je een maanparel, want sinds Morgals nevel staat het getij stil. Vanaf het plein: noord over het wad
 * naar het wrak van de Kraai (Lorre de papegaai, het logboek van Zoutbaard), zuid naar de getijdenpoelen (Roos de
 * duikster en de parelduik onder water door), oost de tempel.
 *
 * <p>De tempel: de Getijdenzaal staat droog (eb). Beneden gaat een rooster (sluis) alleen open bij eb: de Spoelkamer
 * (het enige gevecht, licht), een gebarsten muur (een bom), het raderwerk. Met de maanparel zet je daar de grote hendel
 * om: het water stijgt, de vloeddeur gaat open en je zwemt omhoog. Bij vloed bereik je de drempel aan de overkant met de
 * Ankerpoort; de sleutel heeft Ouwe Teun in Zoutkom (hij voer vroeger met Zoutbaard): terug over de dijk. Achter de
 * Ankerpoort de Draaikolk: Kapitein Zoutbaard ({@link Zoutbaard}; een bom in zijn ton, dan staat hij versuft). Dan het
 * hart van licht ({@code hart2}) en de toren op naar het Getijdenbaken ({@code baken2}): de Witte Toppen gaan open.
 *
 * <p>Boodschap over zee: Lotje (op de pier van het Lindewoud) wil een zingende schelp uit de poelen (beloning
 * {@code fles3}). Zijmissie hier: de parelduik van Roos ({@code hs:zilverstrand-4}). Rustplaatsen houden hun nummer:
 * zet een nieuwe altijd achteraan.
 */
final class ZeldaEiland2b extends ZeldaHoofdstuk {

    static final int VERSIE = 5;
    /** Het vak van dit halve eiland (oost van {@link ZeldaEiland2a#DIJK_X}); elke kolom zet dit deel zelf. */
    static final int X1 = 1230, X2 = 1360, Z1 = -480, Z2 = -180;
    /** Het hartstukje van 2b (de parelduik van Roos). */
    static final String STUKJE_ROOS = "hs:zilverstrand-4";
    /** Waar alles ligt: het plein, de Getijdenzaal (midden), de Draaikolk, de toren met het baken, het wrak. */
    static final int PX = 1268, PZ = -330, TX = 1300, TZ = -330, DX = 1332, DZ = -330, BX = 1348, BZ = -330, WX = 1316, WZ = -404;

    private final Plugin plugin;
    private Bouwer b;
    int G;                                              // G = pad-hoogte (je staat op G+1); de zee komt tot G−1
    private int g;
    private int[][] top;                                // hoogste blok per kolom (alleen tijdens het bouwen)
    private byte[][] soort;

    private static final byte ZEE = 0, ZAND = 1, PRIEL = 2, ROTS = 3, GRAS = 4, PLEIN = 5, POEL = 6;

    /** Wat er alleen tijdens het spel is (voor tik): de baas, het getij, en waar het baken staat. */
    private Zoutbaard zoutbaard;
    private Getij getij;
    private Location bakenKern;
    /** Staat Teun (eiland 2a: de ankersleutel) op het pad? Nee = 2a was er niet bij het bouwen (de zelftest meldt het). */
    boolean teunOpPad;

    ZeldaEiland2b(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override String id() { return "e2b"; }
    @Override String naam() { return "Het Zilverstrand"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda-e2b"; }

    /** Een hub met wegen in elke volgorde: «opnieuw vanaf hier» vergeet alleen tot de volgende rustplaats. */
    @Override boolean open() { return true; }

    // ── tijd, naam en muziek per plek ───────────────────────────────────────────────────────

    /** Avondzon bij de tempel, binnen de zonsondergang; brandt het baken, dan breekt de ochtend aan. */
    @Override
    long hemel(Location l, ZeldaVoortgang.Stand s) {
        if (s.heeft("baken2")) return 1200;
        return switch (plekNaam(l)) {
            case "De Getijdentempel", "De Draaikolk" -> 12300;
            case "Het Getijdenbaken" -> 12900;
            default -> 10800;
        };
    }

    private boolean inTempel(Location l) {
        double x = l.getX(), y = l.getY(), z = l.getZ();
        if (x >= TX - 12 && x <= TX + 19 && z >= TZ - 12 && z <= TZ + 12 && y >= G - 7 && y <= G + 13) return true;     // de zaal en de gang naar de Draaikolk
        return x >= 1289 && x <= 1320 && z >= -360 && z <= -343 && y <= G - 0.5;                                      // de kelders onder het terras
    }

    @Override
    String plekNaam(Location l) {
        double x = l.getX(), z = l.getZ();
        if (Math.abs(x - BX) <= 7 && Math.abs(z - BZ) <= 7 && l.getY() >= G + 4) return "Het Getijdenbaken";
        if (Math.hypot(x - DX, z - DZ) < 13.5 && l.getY() >= G - 5 && l.getY() < G + 11) return "De Draaikolk";
        if (Math.abs(x - BX) <= 3 && Math.abs(z - BZ) <= 3) return "Het Getijdenbaken";
        if (inTempel(l)) return "De Getijdentempel";
        if (Math.hypot(x - PX, z - PZ) < 14) return "Het Getijdenplein";
        if (x < PX - 11 && Math.abs(z - PZ) < 9) return "De dijk";
        if (Math.hypot(x - WX, z - WZ) < 18) return "Het wrak";
        if (z < -361) return "Het wad";
        if (z > -312) return "De poelen";
        return "Het Getijdeneiland";
    }

    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return switch (plekNaam(l)) {
            case "De Getijdentempel", "De Draaikolk", "Het Getijdenbaken" -> ZeldaMuziek.GETIJDENTEMPEL;
            default -> ZeldaMuziek.WAD;
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

    /** Een hendel op de vloer (of op een sokkel): aan = vloed. */
    static BlockData hendel(BlockFace kijk, boolean aan) {
        Switch s = (Switch) Material.LEVER.createBlockData();
        s.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        s.setFacing(kijk);
        s.setPowered(aan);
        return s;
    }

    private static BlockData augurk(int n, boolean nat) {
        SeaPickle p = (SeaPickle) Material.SEA_PICKLE.createBlockData();
        p.setPickles(Math.max(1, Math.min(4, n)));
        p.setWaterlogged(nat);
        return p;
    }

    /** Prismarine van de getijdenwachters: steen dat de zee kent (een beetje gevarieerd). */
    private Material prisma(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 12);
        return k < 7 ? Material.PRISMARINE_BRICKS : k < 10 ? Material.PRISMARINE : Material.DARK_PRISMARINE;
    }

    private Material rots(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 12);
        return k < 4 ? Material.STONE : k < 6 ? Material.ANDESITE : k < 8 ? Material.TUFF : k < 10 ? Material.DIORITE : k < 11 ? Material.COBBLESTONE : Material.MOSSY_COBBLESTONE;
    }

    private int grond(int x, int z) {
        if (top == null || x < X1 || x > X2 || z < Z1 || z > Z2) return G;
        return top[x - X1][z - Z1];
    }

    private Deur deur(BlockData dicht, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Deur(dicht, Material.AIR.createBlockData()).vlak(x1, y1, z1, x2, y2, z2);
    }

    private void zetDeur(Deur d, boolean open) {
        d.isOpen = open;
        for (int[] c : d.blokken) b.zetAltijd(c[0], c[1], c[2], open ? d.open : d.dicht);
    }

    /** Een bordje met alleen een naam (staand bord, kijkt naar {@code kijk}). */
    private void bord(int x, int y, int z, BlockFace kijk, String tekst) {
        zet(x, y, z, Bouwer.gericht(Material.SPRUCE_SIGN, kijk));
        if (!b.plaats) return;
        b.daarna(() -> {
            if (!(w.getBlockAt(x, y, z).getState() instanceof org.bukkit.block.Sign s)) return;
            s.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text(tekst));
            s.setWaxed(true);
            s.update(true, false);
        });
    }

    private void lantaarnpaal(int x, int y, int z) {
        zet(x, y, z, Material.PRISMARINE_WALL);
        zet(x, y + 1, z, Material.PRISMARINE_WALL);
        zet(x, y + 2, z, Material.SEA_LANTERN);
    }

    /** Rustplaats: zielvuur in een kring van prismarine op hoogte {@code y} (de vloer), met een lantaarnpaal. */
    private void rustvuur(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) zet(x + dx, y, z + dz, dx == 0 && dz == 0 ? Material.COBBLESTONE : Material.DARK_PRISMARINE);
        zet(x, y + 1, z, vuur(Material.SOUL_CAMPFIRE, true));
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            zet(x + f.getModX() * 2, y + 1, z + f.getModZ() * 2, Bouwer.trap(Material.PRISMARINE_BRICK_STAIRS, f.getOppositeFace()));
        zet(x + 2, y + 1, z - 2, Material.DARK_PRISMARINE);
        zet(x + 2, y + 2, z - 2, Material.PRISMARINE_WALL);
        zet(x + 2, y + 3, z - 2, Material.SOUL_LANTERN);
    }

    /** Een klein zielvuur (waar geen plaats is voor de hele kring): een vuur op een steen, met een lantaarn. */
    private void zielvuurtje(int x, int y, int z) {
        zet(x, y, z, Material.DARK_PRISMARINE);
        zet(x, y + 1, z, vuur(Material.SOUL_CAMPFIRE, true));
    }

    private void sokkel(int x, int y, int z) {
        zet(x, y, z, Material.CHISELED_STONE_BRICKS);
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            zet(x + f.getModX(), y, z + f.getModZ(), Bouwer.trap(Material.PRISMARINE_BRICK_STAIRS, f.getOppositeFace()));
    }

    private static Villager.Profession beroep(String id) {
        return Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(id));
    }

    // ── het terrein ─────────────────────────────────────────────────────────────────────────

    private static double ellips(double x, double z, double cx, double cz, double a, double c) {
        return (1 - Math.hypot((x - cx) / a, (z - cz) / c)) * Math.min(a, c);
    }

    /** Hoe ver je op het land bent (negatief = zee): het plein, het tempeleiland, het wad in het noorden, de poelen in het zuiden. */
    private static double land(int x, int z) {
        double d = Math.max(Math.max(ellips(x, z, PX, PZ, 14, 13), ellips(x, z, 1301, -330, 24, 22)),
                Math.max(ellips(x, z, DX, DZ, 16, 15), ellips(x, z, BX + 2, BZ, 9, 9)));
        d = Math.max(d, Math.max(ellips(x, z, 1295, -384, 38, 20), Math.max(ellips(x, z, 1326, -404, 24, 16), ellips(x, z, 1276, -356, 10, 16))));
        d = Math.max(d, Math.max(ellips(x, z, 1302, -287, 30, 15), Math.max(ellips(x, z, 1278, -309, 10, 12), ellips(x, z, 1306, -306, 8, 7))));
        return d + (zacht(x, z, 7) - 0.5) * 5;
    }

    /** Vaste plekken (x, z, straal, hoogte boven G, soort): vlak (rustplaatsen, personen, de duikbaan, het wrak). */
    private static final int[][] VAST = {
            {PX, PZ, 12, 0, PLEIN},
            {1284, -290, 3, 0, ROTS}, {1279, -284, 5, 0, ROTS}, {1288, -284, 6, 0, ROTS}, {1316, -284, 6, 0, ROTS}, {1321, -284, 3, 0, ROTS},
            {1306, -276, 2, 0, ROTS}, {1300, -296, 3, 0, ROTS},
            {WX, WZ, 17, -1, ZAND}, {1276, -350, 4, -1, ZAND}};

    /** Hoogte (bovenste blok) en soort van een kolom. */
    private int[] vorm(int x, int z) {
        for (int[] v : VAST) if (Math.hypot(x - v[0], z - v[1]) <= v[2] + 0.4) return new int[]{G + v[3], v[4]};
        double d = land(x, z);
        if (d <= 0) return new int[]{G - 2 - (int) Math.min(6, -d / 2.5), ZEE};
        if (Math.hypot(x - PX, z - PZ) <= 11.5) return new int[]{G, PLEIN};
        if (z < -352) {                                                     // het wad: platen en prielen
            if (d > 3 && zacht(x + 31, z - 17, 6) < 0.27) return new int[]{G - 2, PRIEL};
            return new int[]{G - 1, ZAND};
        }
        if (d < 2.2) return new int[]{G - 1, ZAND};                         // een randje strand
        if (z > -312 && x > 1268) {                                         // de poelen: rotsen met poeltjes
            if (d > 4 && zacht(x + 40, z, 3) < 0.17) return new int[]{G - 2, POEL};
            return new int[]{G + (int) Math.max(0, Math.min(2, Math.round(zacht(x, z, 5) * 3.2 - 0.9))), ROTS};
        }
        if (Math.hypot(x - 1301, z + 330) < 26 || Math.hypot(x - DX, z - DZ) < 17)
            return new int[]{G + (int) Math.max(0, Math.min(1, Math.round(zacht(x, z, 4) * 2 - 0.6))), ROTS};
        return new int[]{G, GRAS};
    }

    private void terrein() {
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                int[] v = vorm(x, z);
                top[x - X1][z - Z1] = v[0];
                soort[x - X1][z - Z1] = (byte) v[1];
            }
        // land nooit steiler dan één blok per stap (twee keer vegen: heen en terug)
        for (int ronde = 0; ronde < 2; ronde++)
            for (int i = 0; i < top.length; i++)
                for (int j = 0; j < top[0].length; j++) {
                    int a = ronde == 0 ? i : top.length - 1 - i, c = ronde == 0 ? j : top[0].length - 1 - j;
                    if (soort[a][c] != ROTS && soort[a][c] != GRAS) continue;
                    for (int[] n : new int[][]{{a - 1, c}, {a + 1, c}, {a, c - 1}, {a, c + 1}})
                        if (n[0] >= 0 && n[1] >= 0 && n[0] < top.length && n[1] < top[0].length && soort[n[0]][n[1]] != ZEE)
                            top[a][c] = Math.min(top[a][c], Math.max(G, top[n[0]][n[1]] + 1));
                }
        // per chunk: diepe zee in één keer (weinig stappen), de rest kolom voor kolom
        for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(X2, 16); cx++)
            for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) {
                int x1 = Math.max(X1, cx * 16), x2 = Math.min(X2, cx * 16 + 15), z1 = Math.max(Z1, cz * 16), z2 = Math.min(Z2, cz * 16 + 15);
                boolean diep = true;
                for (int x = x1; x <= x2 && diep; x++)
                    for (int z = z1; z <= z2 && diep; z++) diep = soort[x - X1][z - Z1] == ZEE && top[x - X1][z - Z1] == G - 8;
                if (diep) {
                    vul(x1, g + 1, z1, x2, G - 9, z2, Material.STONE);
                    vul(x1, G - 8, z1, x2, G - 8, z2, Material.SAND);
                    vul(x1, G - 7, z1, x2, G - 1, z2, Material.WATER);
                    vul(x1, G, z1, x2, G + 44, z2, Material.AIR);
                } else for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) kolom(x, z, top[x - X1][z - Z1], soort[x - X1][z - Z1]);
            }
    }

    private void kolom(int x, int z, int hg, byte s) {
        int ymax = G + 44;
        if (s == ZEE || s == PRIEL || s == POEL) {
            vul(x, g + 1, z, x, hg - 1, z, s == POEL ? Material.STONE : Material.SANDSTONE);
            zet(x, hg, z, s == POEL ? rots(x, hg, z) : kans(x, z, 5) == 0 ? Material.GRAVEL : Material.SAND);
            vul(x, hg + 1, z, x, G - 1, z, Material.WATER);
            int k = kans(x + 9, z, 13);
            if (s == ZEE && hg < G - 3 && k < 2) zet(x, hg + 1, z, k == 0 ? Material.SEAGRASS : Material.TALL_SEAGRASS);
            if (s == POEL && k < 2) zet(x, hg + 1, z, augurk(1 + k, true));
            if (s == PRIEL && k == 0) zet(x, hg + 1, z, Material.SEAGRASS);
            vul(x, G, z, x, ymax, z, Material.AIR);
            return;
        }
        Material opp, onder;
        switch (s) {
            case ZAND -> {
                opp = kans(x, z, 17) == 0 ? Material.GRAVEL : kans(x, z, 29) == 0 ? Material.CLAY : Material.SAND;
                onder = Material.SAND;
            }
            case ROTS -> {
                opp = rots(x, hg, z);
                onder = Material.STONE;
            }
            case PLEIN -> {
                int r = (int) Math.round(Math.hypot(x - PX, z - PZ));
                opp = r % 4 == 0 ? Material.DARK_PRISMARINE : (x + z) % 3 == 0 ? Material.PRISMARINE : Material.PRISMARINE_BRICKS;
                onder = Material.STONE_BRICKS;
            }
            default -> {
                opp = zacht(x, z, 4) < 0.35 ? Material.SAND : kans(x, z, 9) == 0 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
                onder = opp == Material.SAND ? Material.SAND : Material.DIRT;
            }
        }
        if (s == ROTS) for (int y = g + 1; y < hg; y++) zet(x, y, z, rots(x, y, z));
        else {
            vul(x, g + 1, z, x, hg - 3, z, onder == Material.SAND ? Material.SANDSTONE : Material.STONE);
            vul(x, hg - 2, z, x, hg - 1, z, onder);
        }
        zet(x, hg, z, opp);
        vul(x, hg + 1, z, x, ymax, z, Material.AIR);
        int k = kans(x + 11, z - 3, 100);
        Material plant = null;
        if (s == GRAS && opp == Material.GRASS_BLOCK) plant = k < 14 ? Material.SHORT_GRASS : k < 17 ? Material.SHORT_DRY_GRASS : k < 18 ? Material.BLUE_ORCHID : null;
        else if (s == ZAND && hg >= G - 1 && k < 2) plant = Material.SHORT_DRY_GRASS;
        else if (s == ROTS && k < 3) plant = augurk(1, false).getMaterial();
        if (plant == Material.SEA_PICKLE) zet(x, hg + 1, z, augurk(1, false));
        else if (plant != null) zet(x, hg + 1, z, plant);
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        g = bouwer.g;
        G = g + 8;
        leeg(bouwer.w);
        teunOpPad = false;
        speelgebied = Box.van(X1 - 1, g - 4, Z1 + 2, X2 - 2, G + 50, Z2 - 2);
        boolean bouwen = bouwer.plaats;
        List<long[]> chunks = new ArrayList<>();
        if (bouwen) {
            for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(X2, 16); cx++)
                for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) chunks.add(new long[]{cx, cz});
            b.daarna(() -> chunks.forEach(c -> w.addPluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = new int[X2 - X1 + 1][Z2 - Z1 + 1];
            soort = new byte[X2 - X1 + 1][Z2 - Z1 + 1];
            terrein();
        }
        dijk();
        plein();
        wrak();
        poelen();
        tempel();
        zaal();
        kelders();
        draaikolk();
        toren();
        if (bouwen) {
            b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = null;
            soort = null;
        }
        // de begintoestand van deuren, poorten en het getij: altijd (ook zonder herbouw)
        for (Kamer k : kamers) {
            zetDeur(k.in, true);
            zetDeur(k.uit, false);
        }
        for (Uitdaging u : uitdagingen) zetDeur(u.poort, false);
        for (Barst br : barsten) zetDeur(br.muur, false);
        for (Poort po : poorten) zetDeur(po.deur, false);
        for (Getij gt : getijden) {
            gt.vloed = gt.rustVloed;
            gt.laag = -1;
            for (Deur l : gt.lagen) zetDeur(l, !gt.rustVloed);
            for (Deur d : gt.sluizen) zetDeur(d, !gt.rustVloed);
            for (Deur d : gt.vloeddeuren) zetDeur(d, gt.rustVloed);
            for (Getij.Hendel hd : gt.hendels) b.zetAltijd(hd.x(), hd.y(), hd.z(), hendel(BlockFace.EAST, gt.rustVloed));
        }
        if (zoutbaard != null) for (int[] t : zoutbaard.tonnen) b.zetAltijd(t[0], t[1], t[2], Material.BARREL);
        motor();
        b = null;
    }

    // ── 1. de dijk (verder vanaf Zoutkom) ───────────────────────────────────────────────────

    private void dijk() {
        int z1 = ZeldaEiland2a.DIJK_Z1, z2 = ZeldaEiland2a.DIJK_Z2;
        if (b.plaats) {
            for (int x = X1; x <= PX - 12; x++) {
                boolean nat = x <= 1240;                                     // het eerste stuk nog onder water, dan net erboven
                int y = nat ? G - 2 : G - 1;
                for (int z = z1; z <= z2; z++) {
                    vul(x, g + 1, z, x, y - 1, z, Material.STONE_BRICKS);
                    zet(x, y, z, kies(x, z, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.STONE_BRICKS));
                    if (nat) zet(x, y + 1, z, Material.WATER);
                    vul(x, nat ? y + 2 : y + 1, z, x, y + 6, z, Material.AIR);
                }
                if (x % 4 == 0)
                    for (int z : new int[]{z1 - 1, z2 + 1}) {
                        vul(x, g + 1, z, x, nat ? G - 1 : G + 1, z, Material.SPRUCE_LOG);
                        if (!nat) zet(x, G + 2, z, Material.LANTERN);
                    }
            }
            vul(PX - 11, G, z1, PX - 11, G, z2, Material.STONE_BRICKS);  // de laatste stap op het plein
        }
        verhaal(new Verhaal("dijk2", Box.van(X1 + 1, G - 1, z1 - 2, X1 + 22, G + 4, z2 + 2), List.of(
                r("Wiske", "De dijk loopt door. Daar ligt de tempel!"))));
    }

    // ── 2. het Getijdenplein (de hub) en Tijmen ─────────────────────────────────────────────

    private void plein() {
        if (b.plaats) {
            // de getijdenwijzer midden op het plein: een ronde kom met een wijzer
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    zet(PX + dx, G, PZ + dz, Material.DARK_PRISMARINE);
                }
            zet(PX, G, PZ, Material.SEA_LANTERN);
            zet(PX + 1, G + 1, PZ - 1, Bouwer.gericht(Material.LIGHTNING_ROD, BlockFace.NORTH));     // de wijzer van de getijdenklok
            // gebroken zuilen langs de rand, en lantaarns
            for (int a = 0; a < 8; a++) {
                double hoek = a * Math.PI / 4 + Math.PI / 8;
                int x = PX + (int) Math.round(Math.cos(hoek) * 10), z = PZ + (int) Math.round(Math.sin(hoek) * 10);
                if (Math.abs(z - PZ) <= 3) continue;                          // de dijk en de tempelweg vrij
                int hg = 2 + kans(x, z, 4);
                for (int y = G + 1; y <= G + hg; y++) zet(x, y, z, y == G + hg ? Material.CHISELED_STONE_BRICKS : Material.PRISMARINE_BRICKS);
                if (a % 2 == 0) zet(x, G + hg + 1, z, Material.SEA_LANTERN);
            }
            for (int[] l : new int[][]{{PX - 9, PZ - 5}, {PX - 9, PZ + 5}, {PX + 9, PZ - 5}, {PX + 9, PZ + 5}}) lantaarnpaal(l[0], G + 1, l[1]);
            // de weg naar de tempel (het plein ligt al op G, de tempel ook)
            for (int x = PX + 11; x <= 1284; x++)
                for (int z = PZ - 3; z <= PZ + 3; z++) {
                    vul(x, g + 1, z, x, G - 1, z, Material.STONE_BRICKS);
                    zet(x, G, z, Math.abs(z - PZ) == 3 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS);
                    vul(x, G + 1, z, x, G + 6, z, Material.AIR);
                }
            // bordjes bij de uitgangen
            bord(PX + 7, G + 1, PZ - 4, BlockFace.WEST, "Tempel");
            bord(PX - 2, G + 1, PZ - 10, BlockFace.SOUTH, "Het wrak");
            bord(PX + 2, G + 1, PZ + 10, BlockFace.NORTH, "De poelen");
            bord(PX - 8, G + 1, PZ + 4, BlockFace.EAST, "Zoutkom");
            // Tijmens kampje: een luifel met sterren, een kijker
            int tx = PX + 6, tz = PZ + 7;
            for (int[] p : new int[][]{{tx - 1, tz - 1}, {tx + 2, tz - 1}, {tx - 1, tz + 1}, {tx + 2, tz + 1}}) vul(p[0], G + 1, p[1], p[0], G + 3, p[1], Material.SPRUCE_FENCE);
            for (int x = tx - 1; x <= tx + 2; x++)
                for (int z = tz - 1; z <= tz + 1; z++) zet(x, G + 4, z, (x + z) % 2 == 0 ? Material.BLUE_WOOL : Material.LIGHT_BLUE_WOOL);
            zet(tx + 2, G + 1, tz, Material.SPRUCE_FENCE);
            zet(tx + 2, G + 2, tz, Bouwer.gericht(Material.LIGHTNING_ROD, BlockFace.UP));
            zet(tx - 1, G + 5, tz, Material.END_ROD);
        }
        rustvuur(PX - 4, G, PZ - 5);
        rust(new Rust("plein", "Het Getijdenplein", plek(PX - 1, G + 1, PZ, -90f), Box.van(PX - 8, G, PZ - 9, PX + 8, G + 6, PZ + 9), List.of()));
        verhaal(new Verhaal("plein2b", Box.van(PX - 8, G, PZ - 9, PX + 8, G + 6, PZ + 9), List.of(
                r("Wiske", "Een oud plein. Kijk, wie staat daar?"))));
        tijmen();
    }

    /**
     * Tijmen, de reizende tovenaar (zie ZELDA.md): hij was er weer eerder. Zijn gave hier is de maanparel: het getij
     * staat stil sinds de nevel, maar het luistert naar de maan.
     */
    private Persoon tijmen() {
        Persoon tijmen = persoon(new Persoon("Tijmen", plek(PX + 5, G + 1, PZ + 7, 135f), null, List.of(r("Tijmen", "De sterren zwijgen even."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.eenmalig("tijmen-tij", null, List.of(
                        r("Tijmen", "Daar ben je. Ik was er weer eerder."),
                        r("Wiske", "Hoe doet hij dat toch?"),
                        r("Tijmen", "Het getij staat stil, sinds de nevel."),
                        r("Tijmen", "Neem deze maanparel. Het water luistert ernaar.")), (spel, p, s) -> {
                    spel.beloon(p, "maanparel", "Maanparel", "het getij luistert ernaar");
                    spel.uitleg(p, r("Wiske", "Een hendel in de tempel? Met de parel gaat hij om."));
                })
                .eenmalig("tijmen-baken", s -> s.heeft("baken2"), List.of(
                        r("Tijmen", "Het Getijdenbaken brandt. De sterren zingen ervan."),
                        r("Tijmen", "Op de Witte Toppen zien we elkaar weer.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
        praten(tijmen);
        return tijmen;
    }

    // ── 3. het wad en het wrak van de Kraai (Lorre, het logboek) ───────────────────────────

    private void wrak() {
        int y = G - 1;                                                      // het zand
        if (b.plaats) {
            // de romp: een lange boot op zijn kiel in het zand, boeg naar het oosten, de zuidkant opengebroken
            int a = WX - 14, e = WX + 14;
            for (int x = a; x <= e; x++) {
                double t = (x - a) / (double) (e - a);
                int wi = (int) Math.round(4.2 * Math.min(1, Math.min(t * 3 + 0.35, (1 - t) * 2.4 + 0.1)));
                for (int z = WZ - wi; z <= WZ + wi; z++) {
                    boolean rand = Math.abs(z - WZ) == wi;
                    zet(x, y, z, Material.SPRUCE_PLANKS);                   // de bodem van het ruim
                    vul(x, y + 1, z, x, y + 5, z, Material.AIR);
                    if (!rand) continue;
                    boolean gat = z > WZ && x >= WX - 6 && x <= WX + 1;       // waar de zee hem openbrak
                    int hoog = gat ? (x % 3 == 0 ? 1 : 0) : 3 + (x % 4 == 0 ? 1 : 0);
                    for (int dy = 1; dy <= hoog; dy++) zet(x, y + dy, z, dy == hoog && hoog > 2 ? Material.DARK_OAK_PLANKS : Material.STRIPPED_DARK_OAK_LOG);
                }
                if (x % 3 == 0 && wi >= 2) for (int z = WZ - wi + 1; z <= WZ + wi - 1; z++) if (kans(x, z, 3) > 0 && z < WZ + 1) zet(x, y + 4, z, Material.DARK_OAK_PLANKS);  // resten van het dek
            }
            // de kajuit achterop (west)
            vul(a + 1, y + 1, WZ - 2, a + 5, y + 4, WZ + 2, Material.DARK_OAK_PLANKS);
            vul(a + 2, y + 1, WZ - 1, a + 4, y + 3, WZ + 1, Material.AIR);
            vul(a + 5, y + 1, WZ, a + 5, y + 2, WZ, Material.AIR);                       // de deur van de kajuit
            zet(a + 2, y + 1, WZ - 1, Material.CHEST);
            zet(a + 3, y + 1, WZ + 1, Material.LECTERN);
            zet(a + 3, y + 3, WZ, Bouwer.hangend(Material.LANTERN));
            // de gebroken mast, tonnen, een anker in het zand
            vul(WX + 2, y + 1, WZ, WX + 2, y + 9, WZ, Material.SPRUCE_LOG);
            for (int n = 0; n < 5; n++) zet(WX + 3 + n, y + 1, WZ - 6 - n / 2, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            zet(WX + 2, y + 10, WZ, Material.BLACK_WOOL);
            for (int[] t : new int[][]{{WX - 4, WZ - 2}, {WX - 3, WZ - 2}, {WX + 5, WZ + 1}}) zet(t[0], y + 1, t[1], Material.BARREL);
            zet(WX - 4, y + 2, WZ - 2, Material.BARREL);
            zet(WX + 7, y + 1, WZ + 2, Material.RAW_GOLD_BLOCK);
            zet(WX - 1, y + 1, WZ + 3, Material.WHITE_CARPET);
            zet(WX + 18, y, WZ + 4, Material.ANVIL);
            for (int x = WX + 17; x <= WX + 19; x++) zet(x, y + 1, WZ + 3, Material.IRON_CHAIN);
        }
        verhaal(new Verhaal("wrak", Box.van(WX - 8, y, WZ + 3, WX + 4, y + 5, WZ + 9), List.of(
                r("Wiske", "Een wrak. «De Kraai», staat er op de boeg."))));
        verhaal(new Verhaal("logboek", Box.van(WX - 13, y + 1, WZ - 1, WX - 10, y + 4, WZ + 1), List.of(
                r("Wiske", "Een logboek. «Mijn ton buskruit gaat altijd mee.»"),
                r("Wiske", "«Kapitein Zoutbaard.» Hij was hier de baas."))));
        // Lorre, de papegaai van Zoutbaard: herhaalt wat hij hoorde
        Persoon lorre = persoon(new Persoon("Lorre", plek(WX - 3, y + 1, WZ + 1, 180f), null, List.of(r("Lorre", "Krrraa! Lorre wil een koekje!"))));
        lorre.soort = EntityType.PARROT;
        lorre.uiterlijk = e -> {
            if (e instanceof org.bukkit.entity.Parrot pa) {
                pa.setVariant(org.bukkit.entity.Parrot.Variant.RED);
                pa.setSitting(true);
            }
        };
        lorre.eenmalig("lorre-1", null, List.of(
                        r("Lorre", "Krrr! Mijn schat! Mijn schat!"),
                        r("Lorre", "Zoutbaard! Teun! Teun! Krrraa!"),
                        r("Wiske", "Teun? Die van het bankje in Zoutkom?")), null)
                .eenmalig("lorre-vrij", s -> s.heeft("k:draaikolk"), List.of(
                        r("Lorre", "Kapitein vrij! Kapitein vrij! Krrr!")), null);
    }

    // ── 4. de getijdenpoelen: Roos en de parelduik ─────────────────────────────────────────

    private void poelen() {
        int ax = 1288, bx = 1316, pz = -284;                                 // de poel waar je in duikt, en die waar je uitkomt
        if (b.plaats) {
            for (int[] p : new int[][]{{ax, pz}, {bx, pz}})
                for (int x = p[0] - 4; x <= p[0] + 4; x++)
                    for (int z = p[1] - 4; z <= p[1] + 4; z++) {
                        if (Math.hypot(x - p[0], z - p[1]) > 3.6) continue;
                        vul(x, g + 1, z, x, G - 6, z, Material.STONE);
                        vul(x, G - 5, z, x, G - 1, z, Material.WATER);
                        vul(x, G, z, x, G + 4, z, Material.AIR);
                        if (kans(x, z, 4) == 0) zet(x, G - 5, z, Material.KELP);
                    }
            // de gang onder de rotsen door: prismarine, met lichtjes, vol water
            vul(ax + 4, G - 6, pz - 2, bx - 4, G - 2, pz + 2, Material.PRISMARINE_BRICKS);
            vul(ax + 3, G - 5, pz - 1, bx - 3, G - 3, pz + 1, Material.WATER);
            for (int x = ax + 5; x <= bx - 4; x += 4) zet(x, G - 2, pz, Material.SEA_LANTERN);
            for (int x = ax + 3; x <= bx - 3; x += 7) zet(x, G - 5, pz + 1, augurk(3, true));
            vul(ax + 4, G - 1, pz - 2, bx - 4, G - 1, pz + 2, Material.STONE);
            // de startplaat, het hekje, de finish
            zet(1281, G + 1, pz, Material.BIRCH_PRESSURE_PLATE);
            for (int z : new int[]{pz - 2, pz + 2}) vul(1284, G + 1, z, 1284, G + 3, z, Material.STRIPPED_SPRUCE_LOG);
            vul(1320, G, pz - 2, 1322, G, pz + 2, Material.SMOOTH_STONE);
            vul(1320, G + 1, pz - 2, 1322, G + 3, pz + 2, Material.AIR);
            for (int z : new int[]{pz - 2, pz + 2}) {
                vul(1322, G + 1, z, 1322, G + 2, z, Material.SPRUCE_FENCE);
                zet(1322, G + 3, z, Material.LIGHT_BLUE_WOOL);
            }
            // Roos haar plekje: een mand met schelpen, een handdoek
            zet(1283, G + 1, -291, Material.BARREL);
            zet(1285, G + 1, -292, Material.LIGHT_BLUE_CARPET);
            zet(1286, G + 1, -292, Material.LIGHT_BLUE_CARPET);
            // de zingende schelp op een rotsje
            sokkel(1306, G + 1, -276);
            lantaarnpaal(1279, grond(1279, -289) + 1, -289);
        }
        verhaal(new Verhaal("poelen", Box.van(1276, G, -298, 1292, G + 5, -286), List.of(
                r("Wiske", "Getijdenpoelen! Kijk, daar zit iemand."))));
        verhaal(new Verhaal("uitleg-duik", Box.van(1276, G, -288, 1279, G + 4, -280), List.of(
                r("Wiske", "Stap op de plaat, dan gaat het hekje open."),
                r("Wiske", "Duik in de poel, en zwem door de gang!")))).uitleg = true;
        Uitdaging duik = uitdaging(new Uitdaging("parelduik", "De parelduik", Box.van(1280, G, pz - 1, 1282, G + 3, pz + 1),
                Box.van(1320, G, pz - 2, 1322, G + 3, pz + 2), Box.van(1278, G - 7, pz - 8, 1324, G + 8, pz + 8),
                deur(hek(Material.SPRUCE_FENCE, true), 1284, G + 1, pz - 1, 1284, G + 2, pz + 1), 25));
        duik.bord = new Location(w, 1279.5, G + 3.4, -289.5);
        duik.via = Box.van(ax + 6, G - 5, pz - 1, bx - 6, G - 3, pz + 1);   // alleen wie door de gang zwom, haalt de finish
        duik.daarna = (spel, p, s) -> {
            if (s.missie("parelduik") == 1) spel.missie(p, "parelduik", 2);
        };
        Persoon roos = persoon(new Persoon("Roos", plek(1284, G + 1, -290, 180f), beroep("fisherman"), List.of(
                r("Roos", "Nog een keer? Je tijd staat op het bord."))));
        roos.streek = Villager.Type.JUNGLE;
        stukje(STUKJE_ROOS);
        missie("parelduik", "De parelduik", "Duik onder de rotsen door. Start bij Roos.", "Vertel Roos dat het gelukt is.");
        roos.eenmalig("roos-1", null, List.of(
                        r("Roos", "Hoi! Ik ben Roos. Ik duik naar parels."),
                        r("Roos", "Onder de rotsen loopt een gang naar de andere poel."),
                        r("Roos", "Ben jij er sneller doorheen dan ik?")), (spel, p, s) -> spel.missie(p, "parelduik", s.beste("parelduik") != null ? 2 : 1))
                .gesprek("roos-2", s -> s.missie("parelduik") == 2, List.of(
                        r("Roos", "Wat snel! Hier, dit zat in een schelp.")), (spel, p, s) -> {
                    spel.missie(p, "parelduik", ZeldaVoortgang.KLAAR);
                    spel.beloon(p, STUKJE_ROOS, "Hartstukje", null);
                })
                .gesprek(null, s -> s.missie("parelduik") == 1, List.of(r("Roos", "Stap op de plaat. En dan duiken!")), null)
                .eenmalig("roos-baken", s -> s.heeft("baken2"), List.of(
                        r("Roos", "Het water beweegt weer! Eb en vloed, net als vroeger.")), null);
        // de zingende schelp: een boodschap voor Lotje op het Lindewoud (zie ZeldaHoofdstuk1, zijmissie De zingende schelp)
        Vondst schelp = vondst(new Vondst("zingschelp", "Zingende schelp", "voor Lotje", Material.NAUTILUS_SHELL,
                plek(1306, G + 2.3, -276, 0f), Box.van(1305, G + 1, -277, 1307, G + 4, -275)));
        schelp.vereist = "g:lotje-schelp";
        schelp.daarna = (spel, p, s) -> {
            if (s.missie("zingschelp") == 1) spel.missie(p, "zingschelp", 2);
        };
    }

    // ── 5. de tempel: het plateau, de poort, de buitenkant ──────────────────────────────────

    /** Een cel van de Getijdenzaal (dx, dz vanaf het midden, binnen ±11): de galerij (vloer G) of de bak (vloer G−6). */
    static boolean galerij(int dx, int dz) {
        return dx <= -9 || (Math.abs(dz) >= 9 && dx <= 5);
    }

    /** De hoogste vaste blok in een cel van de bak: de vloer, een trede van de trap, of de drempel voor de Ankerpoort. */
    private int bodem(int dx, int dz) {
        if (dz == 8 && dx >= -8 && dx <= -4) return G - 1 - (dx + 8);       // de trap naar beneden (vijf treden)
        if (dx >= 8 && Math.abs(dz) <= 2) return G;                          // de drempel voor de Ankerpoort
        return G - 6;
    }

    private void tempel() {
        if (!b.plaats) return;
        // het plateau rond de tempel en het terras boven de kelders
        for (int[] r : new int[][]{{1284, -346, 1316, -314}, {1288, -361, 1320, -343}}) {
            vul(r[0], g + 1, r[1], r[2], G - 1, r[3], Material.STONE_BRICKS);
            for (int x = r[0]; x <= r[2]; x++)
                for (int z = r[1]; z <= r[3]; z++) {
                    boolean rand = x == r[0] || x == r[2] || z == r[1] || z == r[3];
                    zet(x, G, z, rand ? Material.DARK_PRISMARINE : (x + z) % 5 == 0 ? Material.PRISMARINE : Material.PRISMARINE_BRICKS);
                }
            vul(r[0], G + 1, r[1], r[2], G + 6, r[3], Material.AIR);
        }
        // de muren van de zaal, met ramen; het dak met een rand
        int x1 = TX - 12, x2 = TX + 12, z1 = TZ - 12, z2 = TZ + 12;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean muur = x == x1 || x == x2 || z == z1 || z == z2;
                if (!muur) continue;
                for (int y = G - 6; y <= G + 12; y++) {
                    boolean raam = y >= G + 6 && y <= G + 8 && ((x - x1) % 4 == 2 || (z - z1) % 4 == 2) && !(x == x1 && Math.abs(z - TZ) <= 2);
                    zet(x, y, z, raam ? Material.LIGHT_BLUE_STAINED_GLASS : y == G + 12 || y == G - 6 ? Material.DARK_PRISMARINE : prisma(x, y, z));
                }
                zet(x, G + 13, z, (x + z) % 2 == 0 ? Material.PRISMARINE_BRICK_SLAB : Material.DARK_PRISMARINE);
            }
        vul(x1 + 1, G + 12, z1 + 1, x2 - 1, G + 12, z2 - 1, Material.PRISMARINE_BRICKS);
        for (int x = x1 + 3; x <= x2 - 3; x += 4)
            for (int z = z1 + 3; z <= z2 - 3; z += 4) zet(x, G + 12, z, Material.SEA_LANTERN);
        // het dak: een lage trap van prismarine, met een getijdenteken
        for (int i = 1; i <= 4; i++) vul(x1 + 1 + i * 2, G + 13 + i - 1, z1 + 1 + i * 2, x2 - 1 - i * 2, G + 13 + i - 1, z2 - 1 - i * 2, i % 2 == 0 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS);
        zet(TX, G + 17, TZ, Material.SEA_LANTERN);
        // de poort (west), met een portiek van zuilen
        vul(x1, G + 1, TZ - 1, x1, G + 3, TZ + 1, Material.AIR);
        for (int z : new int[]{TZ - 2, TZ + 2}) {
            vul(x1 - 1, G + 1, z, x1 - 1, G + 5, z, Material.CHISELED_STONE_BRICKS);
            vul(x1 - 4, G + 1, z - (z < TZ ? 1 : -1), x1 - 4, G + 4, z - (z < TZ ? 1 : -1), Material.PRISMARINE_WALL);
        }
        vul(x1 - 1, G + 4, TZ - 1, x1 - 1, G + 5, TZ + 1, Material.DARK_PRISMARINE);
        zet(x1 - 1, G + 6, TZ, Material.SEA_LANTERN);
        for (int z = TZ - 3; z <= TZ + 3; z++) zet(x1 - 4, G + 5, z, Material.PRISMARINE_BRICK_SLAB);
    }

    // ── 6. de Getijdenzaal: de bak, de galerij, de trap, de drempel, het getij ──────────────

    private void zaal() {
        if (b.plaats) {
            for (int dx = -11; dx <= 11; dx++)
                for (int dz = -11; dz <= 11; dz++) {
                    int x = TX + dx, z = TZ + dz;
                    if (galerij(dx, dz)) {
                        vul(x, g + 1, z, x, G - 1, z, Material.PRISMARINE_BRICKS);
                        boolean rand = !galerij(dx + 1, dz) || !galerij(dx - 1, dz) || !galerij(dx, dz + 1) || !galerij(dx, dz - 1);
                        zet(x, G, z, rand ? Material.DARK_PRISMARINE : (dx + dz) % 2 == 0 ? Material.PRISMARINE_BRICKS : Material.PRISMARINE);
                    } else {
                        int bo = bodem(dx, dz);
                        vul(x, g + 1, z, x, Math.min(bo, G - 6) - 1, z, Material.PRISMARINE_BRICKS);
                        if (bo == G - 6) zet(x, G - 6, z, (dx + dz) % 3 == 0 ? Material.DARK_PRISMARINE : Material.PRISMARINE);
                        else if (bo == G) vul(x, G - 6, z, x, G, z, Math.abs(dz) == 2 || dx == 8 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS);
                        else {
                            vul(x, G - 6, z, x, bo - 1, z, Material.PRISMARINE_BRICKS);
                            zet(x, bo, z, Bouwer.trap(Material.PRISMARINE_BRICK_STAIRS, BlockFace.WEST));
                        }
                        vul(x, bo + 1, z, x, G + 11, z, Material.AIR);
                        continue;
                    }
                    vul(x, G + 1, z, x, G + 11, z, Material.AIR);
                }
            // wat er ligt in een droge bak: zand en schelpen in de vloer (wat erop ligt, spoelt weg met het getij)
            for (int dx = -8; dx <= 11; dx++)
                for (int dz = -11; dz <= 11; dz++) {
                    if (galerij(dx, dz) || bodem(dx, dz) != G - 6) continue;
                    int k = kans(TX + dx, TZ + dz, 30);
                    if (k == 0) zet(TX + dx, G - 6, TZ + dz, Material.SAND);
                    else if (k == 1) zet(TX + dx, G - 6, TZ + dz, Material.DEAD_BRAIN_CORAL_BLOCK);
                }
            // pilaren tegen de muren en hangende lantaarns boven de bak
            for (int[] p : new int[][]{{-11, -11}, {11, -11}, {-11, 11}, {11, 11}, {-11, 0}, {0, -11}, {0, 11}})
                if (galerij(p[0], p[1])) vul(TX + p[0], G + 1, TZ + p[1], TX + p[0], G + 11, TZ + p[1], Material.DARK_PRISMARINE);
            for (int[] p : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}, {0, 0}}) {
                vul(TX + p[0], G + 6, TZ + p[1], TX + p[0], G + 11, TZ + p[1], Material.IRON_CHAIN);
                zet(TX + p[0], G + 5, TZ + p[1], Material.SEA_LANTERN);
            }
            // een randje op de galerij (behalve waar de trap begint)
            for (int dx = -10; dx <= 5; dx++)
                for (int dz = -10; dz <= 10; dz++) {
                    if (!galerij(dx, dz)) continue;
                    boolean langsBak = !galerij(dx + 1, dz) || !galerij(dx, dz + 1) || !galerij(dx, dz - 1);
                    if (langsBak && !(dz >= 8 && dx <= -8) && Math.abs(dz) != 10 && (dx + dz) % 2 == 0) zet(TX + dx, G + 1, TZ + dz, Material.PRISMARINE_WALL);
                }
            // de drempel aan de overkant: een zielvuurtje en de Ankerpoort in de oostmuur
            zielvuurtje(TX + 9, G + 1, TZ - 2);
            zet(TX + 9, G + 1, TZ + 2, Material.SOUL_LANTERN);
            vul(TX + 13, G, TZ - 1, TX + 19, G, TZ + 1, Material.DARK_PRISMARINE);
            for (int x = TX + 13; x <= TX + 19; x++) {
                for (int z : new int[]{TZ - 2, TZ + 2}) vul(x, G + 1, z, x, G + 4, z, x % 2 == 0 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS);
                vul(x, G + 4, TZ - 1, x, G + 4, TZ + 1, Material.PRISMARINE_BRICKS);
                vul(x, G + 1, TZ - 1, x, G + 3, TZ + 1, Material.AIR);
                vul(x, g + 1, TZ - 1, x, G - 1, TZ + 1, Material.STONE_BRICKS);
            }
            zet(TX + 16, G + 3, TZ - 1, Bouwer.gericht(Material.SOUL_WALL_TORCH, BlockFace.SOUTH));
            for (int z : new int[]{TZ - 1, TZ + 1}) zet(TX + 12, G + 4, z, Material.CHISELED_STONE_BRICKS);
            zet(TX + 12, G + 4, TZ, Material.SEA_LANTERN);
        }
        // het getij: de bak vol (vloed) of leeg (eb, zo staat hij als niemand er is: Morgals nevel)
        getij = getij(new Getij("getij", "De Getijdenzaal", Box.van(TX - 12, G - 7, -360, 1320, G + 12, TZ + 12), false));
        getij.onthoud = "r:getij";
        getij.binnen = plek(TX, G - 5, TZ, 0f);                             // op de bodem van de bak (bij vloed: zwemmen)
        for (int y = G - 5; y <= G - 1; y++) {
            Deur laag = new Deur(Material.WATER.createBlockData(), Material.AIR.createBlockData());
            for (int dx = -11; dx <= 11; dx++)
                for (int dz = -11; dz <= 11; dz++) if (!galerij(dx, dz) && bodem(dx, dz) < y) laag.blok(TX + dx, y, TZ + dz);
            getij.lagen.add(laag);
        }
        BlockData rooster = Material.WAXED_OXIDIZED_COPPER_GRATE.createBlockData();
        getij.sluizen.add(new Deur(rooster, Material.AIR.createBlockData()).vlak(TX - 1, G - 5, TZ - 9, TX + 1, G - 3, TZ - 9));
        getij.vloeddeuren.add(new Deur(rooster, Material.AIR.createBlockData()).vlak(TX + 9, G - 5, TZ - 12, TX + 11, G - 3, TZ - 12));
        if (b.plaats) zet(TX - 9, G + 1, TZ - 3, Material.DARK_PRISMARINE);                // de sokkel van de hendel op de galerij
        getij.hendels.add(new Getij.Hendel(TX - 9, G + 2, TZ - 3, s -> s.heeft("maanparel") && s.heeft("r:getij"),
                r("Wiske", "Hij zit vast. Hij hangt aan iets, diep beneden."), null));
        getij.buurt = Box.van(TX - 18, G - 8, -362, BX + 9, G + HOOG + 8, TZ + 16);   // ook wie in de Draaikolk of de toren is
        // het pad: binnen, naar beneden, het rooster
        rustvuur(1285, G, -334);
        rust(new Rust("tempelpoort", "De Getijdentempel", plek(1285, G + 1, TZ, -90f), Box.van(1282, G, -336, 1287, G + 5, -325), List.of()));
        verhaal(new Verhaal("zaal", Box.van(TX - 11, G, TZ - 3, TX - 9, G + 4, TZ + 3), List.of(
                r("Wiske", "Een zaal zonder water. Dat hoort niet."),
                r("Wiske", "Daar beneden zit een rooster."))));
        bron("bron-zaal", plek(TX, G - 5, TZ - 6, 0f));
        verhaal(new Verhaal("droog", Box.van(TX - 3, G - 6, TZ - 8, TX + 3, G - 2, TZ - 4), List.of(
                r("Wiske", "Zeewier en schelpen. Hier stond zee."),
                r("Wiske", "Het rooster is open. Erdoor?"))));
    }

    // ── 7. de kelders onder het terras: de Spoelkamer, de pijpgang, het raderwerk ──────────

    private void kelders() {
        int y0 = G - 6;                                                      // de vloer; je staat op G−5
        if (b.plaats) {
            // de gang van het rooster naar de Spoelkamer
            vul(TX - 2, y0, TZ - 17, TX + 2, G - 2, TZ - 9, Material.PRISMARINE_BRICKS);
            vul(TX - 1, G - 5, TZ - 17, TX + 1, G - 3, TZ - 9, Material.AIR);
            zet(TX, G - 2, TZ - 13, Material.SEA_LANTERN);
            // de Spoelkamer
            vul(1290, y0, -358, 1304, G - 1, -347, Material.PRISMARINE_BRICKS);
            vul(1291, G - 5, -357, 1303, G - 2, -348, Material.AIR);
            for (int x = 1291; x <= 1303; x++)
                for (int z = -357; z <= -348; z++) zet(x, y0, z, (x + z) % 4 == 0 ? Material.DARK_PRISMARINE : Material.PRISMARINE);
            for (int x = 1293; x <= 1301; x += 4) zet(x, G - 1, -352, Material.SEA_LANTERN);
            for (int[] p : new int[][]{{1294, -351}, {1300, -351}, {1294, -355}, {1300, -355}}) vul(p[0], G - 5, p[1], p[0], G - 2, p[1], Material.DARK_PRISMARINE);
            for (int x = 1292; x <= 1302; x += 5) zet(x, y0, -357, Material.IRON_TRAPDOOR);
            zet(1303, G - 5, -357, Material.BARREL);
            zet(1303, G - 4, -357, Material.BARREL);
            // de pijpgang naar het raderwerk (met de gebarsten muur)
            vul(1304, y0, -355, 1308, G - 2, -351, Material.PRISMARINE_BRICKS);
            vul(1305, G - 5, -354, 1307, G - 3, -352, Material.AIR);
            for (int x = 1305; x <= 1307; x++) zet(x, G - 2, -355, Material.IRON_CHAIN);
            // het raderwerk
            vul(1308, y0, -359, 1320, G - 1, -345, Material.PRISMARINE_BRICKS);
            vul(1309, G - 5, -358, 1319, G - 1, -346, Material.AIR);
            for (int x = 1309; x <= 1319; x++)
                for (int z = -358; z <= -346; z++) zet(x, y0, z, (x * 3 + z) % 5 == 0 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS);
            // twee grote raderen tegen de oostmuur (houten ringen met spaken), een as met kettingen
            for (int[] r : new int[][]{{-355, G - 3}, {-349, G - 3}}) {
                for (int a = 0; a < 16; a++) {
                    double hoek = a * Math.PI / 8;
                    int z = r[0] + (int) Math.round(Math.cos(hoek) * 2.4), y = r[1] + (int) Math.round(Math.sin(hoek) * 2.0);
                    if (y >= G - 5 && y <= G - 1) zet(1319, y, z, Material.STRIPPED_SPRUCE_LOG);
                }
                zet(1319, r[1], r[0], Material.SPRUCE_LOG);
                zet(1318, r[1], r[0], stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            }
            vul(1313, G - 4, -349, 1313, G - 1, -349, Material.IRON_CHAIN);
            zet(1314, G - 1, -356, Material.SEA_LANTERN);
            zet(1312, G - 1, -349, Material.SEA_LANTERN);
            sokkel(1316, G - 5, -352);
            // de gang naar de vloeddeur (die opent in de hoek van de bak)
            vul(TX + 8, y0, TZ - 16, TX + 12, G - 2, TZ - 13, Material.PRISMARINE_BRICKS);
            vul(TX + 9, G - 5, TZ - 16, TX + 11, G - 3, TZ - 13, Material.AIR);
        }
        Kamer k = kamer(new Kamer("spoelkamer", "De Spoelkamer", Box.van(1291, G - 6, -357, 1303, G - 1, -348), Box.van(1292, G - 5, -356, 1302, G - 2, -349),
                deur(Material.WAXED_OXIDIZED_COPPER_GRATE.createBlockData(), TX - 1, G - 5, -347, TX + 1, G - 3, -347),
                deur(Material.WAXED_OXIDIZED_COPPER_GRATE.createBlockData(), 1304, G - 5, -354, 1304, G - 3, -352)));
        for (int[] s : new int[][]{{1292, -350}, {1292, -356}, {1297, -356}, {1302, -355}, {1302, -349}, {1297, -353}})
            k.spawns.add(plek(s[0], G - 5, s[1], 0f));
        k.golf(Soort.MATROOS, Soort.HARPOENIER)                 // balans: langer, lichter, gemengd (Jelle 27-10)
                .golf(Soort.MATROOS, Soort.ROVER, Soort.MATROOS)
                .golf(Soort.HARPOENIER, Soort.MATROOS, Soort.ROVERSCHUTTER)
                .extra(Soort.MATROOS, Soort.HARPOENIER, Soort.MATROOS, Soort.HARPOENIER);
        k.bijStart.add(r("Wiske", "Verdronken matrozen! Ze zijn betoverd."));
        if (b.plaats) zet(1291, G - 5, -357, Bouwer.gericht(Material.BELL, BlockFace.EAST));
        k.bel = new int[]{1291, G - 5, -357};
        k.tips.addAll(List.of(r("Wiske", "Die met de drietand gooit. Pak hem eerst."),
                r("Wiske", "Bij de bron in de zaal genees je weer.")));
        verhaal(new Verhaal("pijpgang", Box.van(1305, G - 5, -354, 1306, G - 3, -352), List.of(
                r("Wiske", "Weer zo'n gebarsten muur. Je weet het!"))));
        Barst muur = barstOpPad(new Barst("pijpgang", deur(BARST.createBlockData(), 1308, G - 5, -354, 1308, G - 3, -352),
                Box.van(1302, G - 7, -360, 1321, G + 1, -346)));
        muur.voor = plek(1305, G - 5, -353, -90f);
        verhaal(new Verhaal("raderwerk", Box.van(1309, G - 5, -355, 1312, G - 2, -351), List.of(
                r("Wiske", "Raderen, kettingen… en een grote hendel."),
                r("Wiske", "Zou de maanparel hem kunnen bewegen?"))));
        Getij.Hendel groot = new Getij.Hendel(1316, G - 4, -352, s -> s.heeft("maanparel"),
                r("Wiske", "Er staat een maantje op. Tijmen weet er vast meer van."), true);    // de grote hendel zet alleen vloed
        getij.hendels.add(groot);
        tij(getij, groot, true);
        verhaal(new Verhaal("stijgt", Box.van(1309, G - 5, -358, 1319, G - 1, -346), List.of(
                r("Wiske", "Hoor je dat? Het water stijgt!"),
                r("Wiske", "Door de deur, en zwem omhoog!")))).vereist = "r:getij";
        // een uitweg: een ladder naar een luik in het terras (voor wie hier zonder de Spoelkamer kwam, bv. samen spelen
        // terwijl een ander het water liet zakken); het luik gaat alleen van binnenuit open
        BlockData ladder = Bouwer.gericht(Material.LADDER, BlockFace.SOUTH);                  // tegen de noordwand
        if (b.plaats) for (int y = G - 5; y <= G - 1; y++) zet(1318, y, -358, ladder);
        org.bukkit.block.data.type.TrapDoor luik = (org.bukkit.block.data.type.TrapDoor) Material.IRON_TRAPDOOR.createBlockData();
        luik.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
        luik.setFacing(BlockFace.NORTH);
        poort(new Poort("luik", new Deur(luik, ladder).blok(1318, G, -358),                    // open: de ladder loopt door tot het terras
                Box.van(1316, G - 5, -358, 1319, G, -355), s -> s.heeft("e:zilverstrand")));
    }

    // ── 8. de drempel, de Ankerpoort, de Draaikolk (Kapitein Zoutbaard) ─────────────────────

    private void draaikolk() {
        rust(new Rust("ankerpoort", "De Ankerpoort", plek(TX + 10, G + 1, TZ, 90f), Box.van(TX + 8, G, TZ - 2, TX + 11, G + 4, TZ + 2), List.of()));
        Verhaal anker = verhaal(new Verhaal("ankerpoort", Box.van(TX + 8, G, TZ - 2, TX + 11, G + 4, TZ + 2), List.of(
                r("Wiske", "Een slot in de vorm van een anker."),
                r("Wiske", "Wie heeft hier de sleutel van?"))));
        anker.als = s -> !s.heeft("ankersleutel");
        for (ZeldaHoofdstuk h : eiland == null ? List.<ZeldaHoofdstuk>of() : eiland.delen())    // de sleutel heeft Teun, in Zoutkom (eiland 2a)
            if (h != this && h.onderdeel("Teun") instanceof Persoon teun) {
                praten(teun);
                teunOpPad = true;
            }
        if (b.plaats) {
            // de ronde zaal: een ring om een draaikolk, met een koepel
            for (int x = DX - 14; x <= DX + 14; x++)
                for (int z = DZ - 14; z <= DZ + 14; z++) {
                    double d = Math.hypot(x - DX, z - DZ);
                    if (d > 13.4) continue;
                    vul(x, g + 1, z, x, G - 1, z, Material.STONE_BRICKS);
                    if (d > 11.5) {
                        for (int y = G; y <= G + 9; y++) zet(x, y, z, y == G + 6 && kans(x, z, 3) == 0 ? Material.SEA_LANTERN : prisma(x, y, z));
                        zet(x, G + 10, z, Material.DARK_PRISMARINE);
                        continue;
                    }
                    if (d <= 5.2) {                                          // de kolk: drie diep
                        vul(x, G - 4, z, x, G - 4, z, Material.PRISMARINE);
                        vul(x, G - 3, z, x, G - 1, z, Material.WATER);
                        zet(x, G, z, Material.AIR);
                    } else zet(x, G, z, d > 10.5 ? Material.DARK_PRISMARINE : (int) d % 2 == 0 ? Material.PRISMARINE_BRICKS : Material.PRISMARINE);
                    vul(x, G + 1, z, x, G + 9, z, Material.AIR);
                    zet(x, G + 10, z, d < 3 ? Material.SEA_LANTERN : Material.PRISMARINE_BRICKS);
                    zet(x, G + 11, z, Material.DARK_PRISMARINE);
                }
            // het eilandje in het midden: het wrakhout van zijn sloep
            for (int x = DX - 1; x <= DX + 1; x++)
                for (int z = DZ - 1; z <= DZ + 1; z++) {
                    vul(x, G - 4, z, x, G - 1, z, Material.STRIPPED_SPRUCE_LOG);
                    zet(x, G, z, Material.SPRUCE_PLANKS);
                }
            vul(DX + 1, G + 1, DZ + 1, DX + 1, G + 4, DZ + 1, Material.SPRUCE_FENCE);
            zet(DX + 1, G + 5, DZ + 1, Material.BLACK_BANNER);
            // de boeg van de Kraai die door de noordmuur steekt, goud en netten
            for (int i = 0; i < 6; i++) {
                int x = DX - 2 + i, z = DZ - 12 + i / 2;
                vul(x, G + 1, z, x, G + 2 + (i < 3 ? 1 : 0), z, Material.DARK_OAK_PLANKS);
            }
            zet(DX + 4, G + 1, DZ - 10, Material.RAW_GOLD_BLOCK);
            zet(DX + 5, G + 1, DZ - 9, Material.GOLD_BLOCK);
            zet(DX - 6, G + 1, DZ + 8, Material.CHEST);
            for (int y = G + 3; y <= G + 6; y++) zet(DX - 11, y, DZ + 2, Material.COBWEB);
            // de deuren: west (van de Ankerpoort) en oost (naar de toren); de gang ervoor weer open (de ronde muur liep erdoor)
            for (int x = DX - 13; x <= DX - 12; x++) vul(x, G + 1, DZ - 1, x, G + 3, DZ + 1, Material.AIR);
            for (int x : new int[]{DX - 12, DX + 12}) {
                vul(x, G + 1, DZ - 1, x, G + 3, DZ + 1, Material.AIR);
                zet(x, G, DZ, Material.DARK_PRISMARINE);
                for (int z : new int[]{DZ - 2, DZ + 2}) vul(x, G + 1, z, x, G + 4, z, Material.CHISELED_STONE_BRICKS);
            }
        }
        poort(new Poort("ankerpoort", deur(Bouwer.ruit(Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH), TX + 12, G + 1, TZ - 1, TX + 12, G + 3, TZ + 1),
                Box.van(TX + 8, G - 1, TZ - 3, TX + 14, G + 5, TZ + 3), s -> s.heeft("ankersleutel")));
        BlockData tralies = Bouwer.ruit(Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH);
        Kamer k = kamer(new Kamer("draaikolk", "De Draaikolk", Box.van(DX - 12, G - 4, DZ - 12, DX + 12, G + 9, DZ + 12), Box.van(DX - 9, G, DZ - 9, DX + 9, G + 6, DZ + 9),
                deur(tralies, DX - 12, G + 1, DZ - 1, DX - 12, G + 3, DZ + 1), deur(tralies, DX + 12, G + 1, DZ - 1, DX + 12, G + 3, DZ + 1)));
        k.muziek = ZeldaMuziek.ZOUTBAARD;
        k.hemel = 17500;
        for (int a = 0; a < 6; a++) {
            double hoek = a * Math.PI / 3;
            k.spawns.add(plek(DX + Math.round(Math.cos(hoek) * 8), G + 1, DZ + Math.round(Math.sin(hoek) * 8), 0f));
        }
        List<Location> plekken = new ArrayList<>();
        List<int[]> tonnen = new ArrayList<>();
        for (int a : new int[]{90, 210, 330}) {
            double hoek = Math.toRadians(a);
            int tx = DX + (int) Math.round(Math.cos(hoek) * 10), tz = DZ + (int) Math.round(Math.sin(hoek) * 10);
            int sx = DX + (int) Math.round(Math.cos(hoek) * 8), sz = DZ + (int) Math.round(Math.sin(hoek) * 8);
            tonnen.add(new int[]{tx, G + 1, tz});
            Location l = plek(sx, G + 1, sz, 0f);
            l.setDirection(new org.bukkit.util.Vector(DX - sx, 0, DZ - sz));
            plekken.add(l);
        }
        zoutbaard = new Zoutbaard("Kapitein Zoutbaard", plekken, tonnen,
                List.of(r("Zoutbaard", "Arrr… taai, voor een landrot!")),
                List.of(r("Zoutbaard", "De nevel… trekt op. Ik zie de sterren."),
                        r("Zoutbaard", "Zeg Teun gedag, jong."),
                        r("Wiske", "Hij is zeeschuim geworden. Kijk: een hart van licht.")));
        k.baas = zoutbaard;
        k.golf(Soort.KAPITEIN);
        k.bijStart.add(r("Zoutbaard", "Arrr! Wie komt er aan mijn schat?"));
        k.bijStart.add(r("Wiske", "Zijn tonnen! Gooi er een bom in."));
        k.tips.addAll(List.of(r("Wiske", "Wacht tot hij bij een ton staat. Dan een bom!"),
                r("Wiske", "Leven laag? Drink je flesje, vak 3.")));
        Vondst hart = vondst(new Vondst("hart2", "Hartcontainer", "een hart erbij", Material.RED_DYE, plek(DX, G + 1.3, DZ, 0f), Box.van(DX - 1, G, DZ - 1, DX + 1, G + 3, DZ + 1)));
        hart.vereist = "k:draaikolk";                            // pas als Zoutbaard vrij is
    }

    // ── 9. de toren en het Getijdenbaken ────────────────────────────────────────────────────

    /** De wenteltrap: de cellen rond het midden van de toren, met de klok mee (vanaf west). */
    private static final int[][] RING = {{-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}};
    /** Hoe hoog het platform bovenop de toren ligt (boven G). */
    static final int HOOG = 24;

    private void toren() {
        int bx = BX + 6;                                                     // het baken, op het oosten van het platform
        if (b.plaats) {
            // een portaaltje van de Draaikolk naar de toren
            vul(DX + 13, g + 1, BZ - 2, BX - 2, G, BZ + 2, Material.STONE_BRICKS);
            vul(DX + 13, G + 1, BZ - 2, BX - 3, G + 4, BZ + 2, Material.PRISMARINE_BRICKS);
            vul(DX + 13, G + 1, BZ - 1, BX - 3, G + 3, BZ + 1, Material.AIR);
            // de toren: 5 x 5, een wenteltrap om een zuil
            for (int x = BX - 2; x <= BX + 2; x++)
                for (int z = BZ - 2; z <= BZ + 2; z++) {
                    vul(x, g + 1, z, x, G, z, Material.STONE_BRICKS);
                    boolean muur = Math.abs(x - BX) == 2 || Math.abs(z - BZ) == 2;
                    if (muur) for (int y = G + 1; y < G + HOOG; y++)
                        zet(x, y, z, (y - G) % 6 == 3 && Math.abs(x - BX) + Math.abs(z - BZ) == 2 ? Material.LIGHT_BLUE_STAINED_GLASS : prisma(x, y, z));
                    else vul(x, G + 1, z, x, G + HOOG + 3, z, Material.AIR);
                }
            vul(BX, G + 1, BZ, BX, G + HOOG, BZ, Material.DARK_PRISMARINE);
            vul(BX - 2, G + 1, BZ, BX - 2, G + 2, BZ, Material.AIR);             // de deur
            zet(BX, G + 1, BZ, Material.SEA_LANTERN);
            for (int n = 1; n <= HOOG; n++) {
                int[] c = RING[n % 8], v = RING[(n + 7) % 8];
                BlockFace op = c[0] > v[0] ? BlockFace.EAST : c[0] < v[0] ? BlockFace.WEST : c[1] > v[1] ? BlockFace.SOUTH : BlockFace.NORTH;
                zet(BX + c[0], G + n, BZ + c[1], n == HOOG ? Material.DARK_PRISMARINE.createBlockData() : Bouwer.trap(Material.PRISMARINE_BRICK_STAIRS, op));
                if (n % 6 == 0) zet(BX, G + n, BZ, Material.SEA_LANTERN);
            }
            // het platform met een borstwering, en het baken
            int p1 = BX - 6, p2 = BX + 8, q1 = BZ - 6, q2 = BZ + 6, y = G + HOOG;
            for (int x = p1; x <= p2; x++)
                for (int z = q1; z <= q2; z++) {
                    boolean gat = Math.abs(x - BX) <= 1 && Math.abs(z - BZ) <= 1 && !(x == BX && z == BZ) && !(x == BX - 1 && z == BZ);
                    zet(x, y, z, gat ? Material.AIR : (x + z) % 3 == 0 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS);
                    vul(x, y + 1, z, x, y + 7, z, Material.AIR);
                    boolean rand = x == p1 || x == p2 || z == q1 || z == q2;
                    if (rand) zet(x, y + 1, z, Material.PRISMARINE_WALL);
                    if (Math.abs(x - BX) == 2 && Math.abs(z - BZ) <= 2 || Math.abs(z - BZ) == 2 && Math.abs(x - BX) <= 2)
                        if (!(x == BX - 2 && z == BZ)) zet(x, y + 1, z, Material.PRISMARINE_WALL);   // om het trapgat
                }
            // het baken: een kooi van zuilen met een kern (dood glas; voor wie hem ontstak: licht)
            for (int[] c : new int[][]{{bx - 1, BZ - 1}, {bx + 1, BZ - 1}, {bx - 1, BZ + 1}, {bx + 1, BZ + 1}}) vul(c[0], y + 1, c[1], c[0], y + 4, c[1], Material.PRISMARINE_WALL);
            vul(bx - 1, y + 5, BZ - 1, bx + 1, y + 5, BZ + 1, Material.DARK_PRISMARINE);
            zet(bx, y + 6, BZ, Material.PRISMARINE_WALL);
            zet(bx, y + 7, BZ, Material.LIGHTNING_ROD);
            zet(bx, y + 1, BZ, Material.CHISELED_STONE_BRICKS);
            zet(bx, y + 2, BZ, Material.TINTED_GLASS);
            zet(bx, y + 3, BZ, Material.PRISMARINE_WALL);
        }
        bakenKern = new Location(w, bx, G + HOOG + 2, BZ);
        verhaal(new Verhaal("toren", Box.van(DX + 13, G + 1, BZ - 1, BX - 1, G + 3, BZ + 1), List.of(
                r("Wiske", "Een trap naar boven. Naar het baken!"))));
        rustvuur(BX - 4, G + HOOG, BZ - 4);
        rust(new Rust("baken", "Het Getijdenbaken", plek(BX - 2, G + HOOG + 1, BZ + 3, 90f), Box.van(BX - 6, G + HOOG, BZ - 6, BX + 8, G + HOOG + 6, BZ + 6), List.of()));
        vondst(new Vondst("baken2", "Het Getijdenbaken", "brandt weer", Material.HEART_OF_THE_SEA, plek(bx - 2, G + HOOG + 1.5, BZ, 0f),
                Box.van(bx - 3, G + HOOG + 1, BZ - 1, bx - 2, G + HOOG + 3, BZ + 1)));
        Verhaal einde = verhaal(new Verhaal("baken", Box.van(BX + 2, G + HOOG, BZ - 6, BX + 4, G + HOOG + 6, BZ + 6), List.of(
                r("Wiske", "Het Getijdenbaken brandt! Ik voel me… feller!"),
                r("Wiske", "Kijk, in het noorden: sneeuw op de bergen."),
                r("Wiske", "De Witte Toppen. Daar ligt het volgende baken."),
                r(null, "Het Getijdenbaken brandt weer"))));
        einde.vereist = "baken2";
        einde.einde = true;
    }

    /**
     * Zonder blokken (dus zonder herbouw): de doelen voor {@code /hint} en Tijmen. De drempel en de Ankerpoort liggen
     * pas binnen bereik bij vloed; de sleutel heeft Teun in Zoutkom.
     */
    private void motor() {
        doel("tijmen2b", "De dijk loopt naar de tempel. Volg hem.", "Tijmen wacht op het plein bij de tempel.",
                plekVan("Tijmen"), s -> s.heeft("maanparel"));
        doel("zaal", "In de tempel ligt de Getijdenzaal.", "Loop de trap af, de droge zaal in.",
                plekVan("droog"), s -> s.heeft("v:droog") || s.heeft("k:spoelkamer") || s.heeft("r:getij"));
        doel("spoelkamer", "Beneden in de zaal: een open rooster.", "Door het rooster naar de Spoelkamer.",
                plekVan("spoelkamer"), s -> s.heeft("k:spoelkamer") || s.heeft("r:getij"));
        doel("pijpgang", "Een gebarsten muur. Een bom!", "Bom in vak 4. Kijk naar de muur en rechtsklik.",
                plekVan("pijpgang"), s -> s.heeft("b:pijpgang") || s.heeft("r:getij"));
        doel("raderwerk", "Het raderwerk. Trek aan de grote hendel.", "Met de maanparel gaat hij om. Dan stijgt het water.",
                new Location(w, 1316.5, G - 5, -351.5), s -> s.heeft("r:getij"));
        doel("drempel", "Bij vloed kun je overal heen zwemmen.", "Zwem naar de deur aan de overkant van de zaal.",
                plekVan("ankerpoort"), s -> s.heeft("v:ankerpoort") || s.heeft("ankersleutel"));
        doel("sleutel", "Een ankerslot. Wie heeft de sleutel?", "Teun kende Zoutbaard. Vraag het hem, in Zoutkom.",
                teun(), s -> s.heeft("ankersleutel"));
        doel("zoutbaard", "Achter de Ankerpoort wacht Zoutbaard.", "Water laag? Trek de hendel op de galerij, zwem naar de poort.",
                plekVan("draaikolk"), s -> s.heeft("k:draaikolk") || s.heeft("hart2"));
        doel("hart2", "Zoutbaard is vrij. Pak het hart van licht.", "Op het eilandje midden in de kolk.",
                plekVan("hart2"), s -> s.heeft("hart2"));
        doel("baken", "De toren op, naar het Getijdenbaken.", "Achter de Draaikolk gaat een trap omhoog.",
                plekVan("baken2"), s -> s.heeft("baken2"));
    }

    /** Waar Ouwe Teun zit (op het plein van Zoutkom, eiland 2a). */
    private Location teun() {
        if (eiland != null) for (ZeldaHoofdstuk h : eiland.delen()) if (h != this && h.plekVan("Teun") != null) return h.plekVan("Teun");
        return new Location(w, ZeldaEiland2a.PX, G + 1, ZeldaEiland2a.PZ);
    }

    // ── tijdens het spel: de kolk, het baken ────────────────────────────────────────────────

    @Override
    void tik(ZeldaSpel spel, List<Player> wie, long nu) {
        kolk(wie, nu);
        baken(spel);
    }

    /** De draaikolk: schuim dat rondgaat, voor wie in de buurt is. */
    private void kolk(List<Player> wie, long nu) {
        double t = nu / 1000.0;
        Particle.DustOptions schuim = new Particle.DustOptions(org.bukkit.Color.fromRGB(0xe8f6ff), 1.2f);
        for (Player p : wie) {
            if (!p.getWorld().equals(w) || p.getLocation().distanceSquared(new Location(w, DX, G, DZ)) > 30 * 30) continue;
            for (int i = 0; i < 10; i++) {
                double a = t * 2.2 + i * Math.PI / 5, r = 2 + (i % 3) * 1.2;
                p.spawnParticle(Particle.DUST, new Location(w, DX + 0.5 + Math.cos(a) * r, G - 0.1, DZ + 0.5 + Math.sin(a) * r), 1, 0, 0, 0, 0, schuim);
            }
            p.spawnParticle(Particle.BUBBLE_POP, new Location(w, DX + 0.5, G - 0.2, DZ + 0.5), 4, 2.5, 0, 2.5, 0.02);
        }
    }

    /**
     * Wie het baken ontstak ({@code baken2}), ziet het branden: de kern licht op en er gaat een bundel omhoog, te zien
     * van overal op het Zilverstrand (ook vanuit Zoutkom).
     */
    private void baken(ZeldaSpel spel) {
        if (bakenKern == null || eiland == null) return;
        BlockData aan = Material.SEA_LANTERN.createBlockData();
        Particle.DustOptions licht = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x9ff2ff), 2.0f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            ZeldaSpel.Sessie ss = spel.sessie(p);
            if (ss == null || ss.vaart != null || ss.h.eiland != eiland || !spel.voortgang.van(p.getUniqueId()).heeft("baken2")) continue;
            if (!p.getWorld().equals(w) || p.getLocation().distanceSquared(bakenKern) > 200 * 200) continue;
            p.sendBlockChange(bakenKern, aan);
            for (int i = 1; i < 40; i += 2) p.spawnParticle(Particle.DUST, bakenKern.clone().add(0.5, i, 0.5), 1, 0.12, 0.3, 0.12, 0, licht);
        }
    }

    /** Op de kaart: de Getijdentempel. */
    @Override
    List<Teken> tekens() {
        return List.of(new Teken("kerker", 1300, -330));
    }
}
