package nl.burggraaf.mc.lobbymelding;

import java.util.ArrayList;
import java.util.List;

/**
 * De tuin van Burggraaf Craft (bouwversie 8): een klein tuindorp van ±116 × 116 blokken, procedureel getekend in een
 * {@link Plan} (geen wereld nodig, altijd hetzelfde resultaat).
 * <pre>
 *            boomhut (9)        molen (10)          boerderij, hooiberg (4)
 *   tuinmuur (5)      ┌──── dorpsstraat (ring) ────┐      boomgaard (3)
 *   moestuin          │  Survival  ◇  KitPvP        │
 *                     │       brink + linde         │
 *   vijver (1)        └──── huisjes, daken (7) ────┘      buxustuin (2)
 *   pergola (6)                                           speeltuin (8)
 * </pre>
 * Midden: de brink met de spawn (kijkt naar het noorden, recht op de twee poorten), een linde, een waterput, een
 * prikbord met de records en een wegwijzer. Daaromheen een straatje van kinderkopjes; daarbuiten de erven met elk
 * een eigen tuin en een parkour-level ({@link Banen}).
 */
final class Tuin {

    static final int STRAAL = 64;          // tekening x, z = −64..63 (8 × 8 chunks)
    static final int RAND = 58;            // tot hier de tuin, daarbuiten gras
    static final int HOOGTE = 40;          // tot g + 40

    final Plan p;
    final int g;

    record Poort(int x1, int x2, int z, String server) {}

    final List<Poort> poorten = new ArrayList<>();
    final List<Baan> banen = new ArrayList<>();
    /** Spawn: midden op de brink, kijkt naar het noorden (de poorten). */
    final double spawnX = 0.5, spawnZ = 9.5;
    final float spawnYaw = 180f;
    /** Plekken voor tekst die de plugin bijwerkt: prikbord met records (x, y, z, yaw). */
    double[] prikbord;

    Tuin(int g) {
        this.g = g;
        this.p = new Plan(g, STRAAL, HOOGTE);
    }

    // ── gereedschap ─────────────────────────────────────────────────────────────────────────

    void zet(int x, int h, int z, String b) {
        p.zet(x, g + h, z, b);
    }

    void vul(int xa, int ha, int za, int xb, int hb, int zb, String b) {
        p.vul(xa, g + ha, za, xb, g + hb, zb, b);
    }

    String naam(int x, int h, int z) {
        return p.naam(x, g + h, z);
    }

    boolean vrij(int x, int h, int z) {
        return p.lucht(x, g + h, z);
    }

    static long hash(int x, int z) {
        long h = x * 73428767L ^ z * 912931L ^ 0x9E3779B97F4A7C15L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        h ^= (h >>> 15);
        return h & Long.MAX_VALUE;
    }

    static int kans(int x, int z, int n) {
        return (int) (hash(x, z) % n);
    }

    static final String[] BLOEMEN = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "allium", "pink_tulip",
            "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "lily_of_the_valley", "blue_orchid"};

    static String bloem(int x, int z) {
        return BLOEMEN[kans(x * 3 + 7, z * 5 - 1, BLOEMEN.length)];
    }

    static String blad(String soort) {
        return soort + "_leaves[persistent=true]";
    }

    static String trap(String mat, String richting, boolean boven) {
        return mat + "_stairs[facing=" + richting + ",half=" + (boven ? "top" : "bottom") + "]";
    }

    static String plaat(String mat, boolean boven) {
        return mat + "_slab[type=" + (boven ? "top" : "bottom") + "]";
    }

    static final String[] RICHTING = {"north", "east", "south", "west"};

    static String tegen(String r) {
        return switch (r) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** Is hier nog gras met lucht erboven (vrij om iets op te zetten)? */
    boolean grasVrij(int x, int z) {
        return naam(x, 0, z).equals("grass_block") && vrij(x, 1, z);
    }

    /** Plantje op het gras (alleen als daar niets staat). */
    void plant(int x, int z, String b) {
        if (grasVrij(x, z)) zet(x, 1, z, b);
    }

    /** Pad over de grond langs punten {x, z}; soort: "zand" (aarde/grind), "steen" (kinderkopjes), "grind". */
    void pad(int[][] punten, double breed, String soort) {
        double r = breed / 2.0;
        for (int i = 0; i + 1 < punten.length; i++) {
            double ax = punten[i][0] + 0.5, az = punten[i][1] + 0.5, bx = punten[i + 1][0] + 0.5, bz = punten[i + 1][1] + 0.5;
            double len = Math.hypot(bx - ax, bz - az);
            int n = Math.max(1, (int) Math.ceil(len * 3));
            for (int k = 0; k <= n; k++) {
                double cx = ax + (bx - ax) * k / n, cz = az + (bz - az) * k / n;
                for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r); x++)
                    for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r); z++) {
                        if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) > r) continue;
                        padBlok(x, z, soort);
                    }
            }
        }
    }

    void padBlok(int x, int z, String soort) {
        String onder = naam(x, 0, z);
        if (!onder.equals("grass_block") && !onder.equals("dirt_path") && !onder.equals("coarse_dirt") && !onder.equals("gravel")
                && !onder.equals("moss_block") && !onder.equals("rooted_dirt")) return;
        int k = kans(x, z, 20);
        String m = switch (soort) {
            case "steen" -> k < 9 ? "cobblestone" : k < 13 ? "mossy_cobblestone" : k < 17 ? "andesite" : "stone";
            case "grind" -> k < 14 ? "gravel" : k < 18 ? "coarse_dirt" : "dirt_path";
            default -> k < 13 ? "dirt_path" : k < 17 ? "coarse_dirt" : "gravel";
        };
        zet(x, 0, z, m);
        if (Plan.doorloopbaar(naam(x, 1, z))) zet(x, 1, z, "air");
    }

    /** Lantaarnpaal: paal van hek met een lantaarn erop. */
    void lantaarn(int x, int z, String hout) {
        zet(x, 1, z, "stone_brick_wall");
        vul(x, 2, z, x, 3, z, hout + "_fence");
        zet(x, 4, z, "lantern[hanging=false]");
    }

    /** Bankje van 2 trappen met armleuningen (trapdeurtjes), rug naar {@code rug}. */
    void bank(int x, int z, String rug, String hout) {
        boolean langsX = rug.equals("north") || rug.equals("south");
        int dx = langsX ? 1 : 0, dz = langsX ? 0 : 1;
        String zit = trap(hout, rug, false);
        zet(x, 1, z, zit);
        zet(x + dx, 1, z + dz, zit);
        String[] zij = langsX ? new String[]{"west", "east"} : new String[]{"north", "south"};
        zet(x - dx, 1, z - dz, hout + "_trapdoor[facing=" + zij[0] + ",half=bottom,open=true]");
        zet(x + 2 * dx, 1, z + 2 * dz, hout + "_trapdoor[facing=" + zij[1] + ",half=bottom,open=true]");
    }

    /** Haag van bladeren (vast, verwelkt niet) van (xa, za) tot (xb, zb), hoog blokken. */
    void haag(int xa, int za, int xb, int zb, int hoog, String soort) {
        for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++)
            for (int z = Math.min(za, zb); z <= Math.max(za, zb); z++)
                for (int h = 1; h <= hoog; h++) zet(x, h, z, blad(soort));
    }

    /** Hekje (palen worden later verbonden). */
    void hek(int xa, int za, int xb, int zb, String hout) {
        for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++)
            for (int z = Math.min(za, zb); z <= Math.max(za, zb); z++) zet(x, 1, z, hout + "_fence");
    }

    // ── bomen ───────────────────────────────────────────────────────────────────────────────

    /** Ronde boom: stam tot {@code stam}, kruin met straal r (bol, iets plat), wat bloesem door de bladeren. */
    void boom(int x, int z, int stam, double r, String log, String blad, String bloesem, int bloesemKans) {
        int kh = stam + (int) Math.round(r * 0.6);
        for (int dx = (int) -Math.ceil(r); dx <= Math.ceil(r); dx++)
            for (int dz = (int) -Math.ceil(r); dz <= Math.ceil(r); dz++)
                for (int dy = (int) -Math.ceil(r); dy <= Math.ceil(r); dy++) {
                    double e = Math.sqrt(dx * dx + dz * dz + (dy * 1.35) * (dy * 1.35));
                    double rand = r - (kans(x + dx * 7 + dy, z + dz * 5 - dy * 3, 10) < 4 ? 0.6 : 0);
                    if (e > rand) continue;
                    int h = kh + dy;
                    if (h < 2) continue;
                    if (!vrij(x + dx, h, z + dz) && !naam(x + dx, h, z + dz).endsWith("_leaves")) continue;
                    boolean b = bloesem != null && kans(x + dx * 13, z + dz * 11 + h, 100) < bloesemKans;
                    zet(x + dx, h, z + dz, Tuin.blad(b ? bloesem : blad));
                }
        vul(x, 1, z, x, kh, z, log + "[axis=y]");
        if (r >= 3) {                                            // een paar takken
            for (String r2 : RICHTING) {
                if (kans(x + r2.length(), z, 3) == 0) continue;
                int tx = r2.equals("east") ? 1 : r2.equals("west") ? -1 : 0, tz = r2.equals("south") ? 1 : r2.equals("north") ? -1 : 0;
                String as = tx != 0 ? "x" : "z";
                zet(x + tx, kh - 1, z + tz, log + "[axis=" + as + "]");
            }
        }
    }

    void fruitboom(int x, int z) {
        boom(x, z, 2, 2.2, "oak_log", "oak", "flowering_azalea", 35);
        if (kans(x, z, 2) == 0 && grasVrij(x + 1, z + 1)) zet(x + 1, 1, z + 1, "red_tulip");
    }

    void berk(int x, int z) {
        boom(x, z, 4 + kans(x, z, 2), 2.0, "birch_log", "birch", null, 0);
    }

    void kers(int x, int z) {
        boom(x, z, 3, 3.0, "cherry_log", "cherry", null, 0);
        for (int i = 0; i < 6; i++) {                           // roze blaadjes eronder
            int px = x + kans(x + i, z, 7) - 3, pz = z + kans(x, z + i, 7) - 3;
            if (grasVrij(px, pz)) zet(px, 1, pz, "pink_petals[flower_amount=" + (1 + kans(px, pz, 4)) + ",facing=north]");
        }
    }

    void spar(int x, int z, int hoog) {
        for (int h = 2; h <= hoog; h++) {
            double r = Math.max(0, (hoog - h) * 0.42 - (h % 2 == 0 ? 0 : 0.6));
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.hypot(dx, dz) > r + 0.3 || (dx == 0 && dz == 0)) continue;
                    if (vrij(x + dx, h, z + dz)) zet(x + dx, h, z + dz, blad("spruce"));
                }
        }
        vul(x, 1, z, x, hoog - 1, z, "spruce_log[axis=y]");
        zet(x, hoog, z, blad("spruce"));
        zet(x, hoog + 1, z, blad("spruce"));
    }

    /** Treurwilg: brede kruin met hangende sliertjes. */
    void wilg(int x, int z) {
        boom(x, z, 4, 3.4, "oak_log", "oak", "azalea", 30);
        int kh = 4 + 2;
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.hypot(dx, dz);
                if (d < 2.6 || d > 4.2 || kans(x + dx, z + dz, 3) == 0) continue;
                int lang = 2 + kans(x + dz, z + dx, 3);
                for (int h = kh - 1; h >= Math.max(2, kh - lang); h--)
                    if (vrij(x + dx, h, z + dz)) zet(x + dx, h, z + dz, blad("oak"));
            }
    }

    // ── huisjes ─────────────────────────────────────────────────────────────────────────────

    /** Stijl: muur, hoekbalk, dak (trap-materiaal, zonder _stairs), nok (blok), vloer, deur(hout). */
    record Stijl(String muur, String hoek, String dak, String nok, String vloer, String deur) {}

    static final Stijl[] STIJLEN = {
            new Stijl("white_terracotta", "dark_oak_log", "dark_oak", "dark_oak_planks", "oak_planks", "spruce"),
            new Stijl("bricks", "spruce_log", "deepslate_tile", "deepslate_tiles", "spruce_planks", "dark_oak"),
            new Stijl("mud_bricks", "oak_log", "spruce", "spruce_planks", "birch_planks", "oak"),
            new Stijl("smooth_sandstone", "stripped_spruce_log", "brick", "bricks", "oak_planks", "spruce"),
            new Stijl("birch_planks", "stripped_dark_oak_log", "mud_brick", "mud_bricks", "spruce_planks", "birch"),
            new Stijl("light_gray_terracotta", "spruce_log", "dark_oak", "dark_oak_planks", "oak_planks", "oak"),
    };

    /**
     * Huisje: vloer x1..x2 × z1..z2, muren 3 hoog, zadeldak met overstek, nok langs x ({@code nokX}) of z. Deur in het
     * midden van de kant {@code deur} (north/east/south/west). Geeft de hoogte van de nok (h).
     */
    int huisje(int x1, int z1, int x2, int z2, Stijl st, String deur, boolean nokX, boolean schoorsteen) {
        int muurH = 3;
        vul(x1 - 1, 1, z1 - 1, x2 + 1, 14, z2 + 1, "air");
        vul(x1, 0, z1, x2, 0, z2, "cobblestone");
        vul(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, st.vloer());
        vul(x1, 1, z1, x2, muurH, z2, st.muur());
        vul(x1 + 1, 1, z1 + 1, x2 - 1, muurH, z2 - 1, "air");
        for (int[] c : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) vul(c[0], 1, c[1], c[0], muurH, c[1], st.hoek() + "[axis=y]");
        // dak
        int a1 = nokX ? z1 - 1 : x1 - 1, a2 = nokX ? z2 + 1 : x2 + 1;     // dwars op de nok
        int b1 = nokX ? x1 - 1 : z1 - 1, b2 = nokX ? x2 + 1 : z2 + 1;     // langs de nok
        int nokH = muurH + 1;
        for (int i = 0; ; i++) {
            int lo = a1 + i, hi = a2 - i, h = muurH + 1 + i;
            if (lo > hi) break;
            nokH = h;
            for (int b = b1; b <= b2; b++) {
                if (lo == hi) {
                    zetAs(nokX, b, h, lo, st.nok());
                } else {
                    zetAs(nokX, b, h, lo, trap(st.dak(), nokX ? "south" : "east", false));
                    zetAs(nokX, b, h, hi, trap(st.dak(), nokX ? "north" : "west", false));
                    if (i > 0) {                                     // onderkant dicht onder het overstek
                        zetAs(nokX, b, h - 1, lo, trap(st.dak(), nokX ? "north" : "west", true));
                        zetAs(nokX, b, h - 1, hi, trap(st.dak(), nokX ? "south" : "east", true));
                    }
                }
            }
            // gevels
            if (i >= 1) for (int a = lo + 1; a <= hi - 1; a++) {
                zetAs(nokX, nokX ? x1 : z1, h, a, st.muur());
                zetAs(nokX, nokX ? x2 : z2, h, a, st.muur());
            }
            if (i >= 1) for (int a = lo; a <= hi; a++) for (int b = b1 + 1; b <= b2 - 1; b++) {
                // binnen het dak: lucht (behalve de gevels)
                int bx = nokX ? b : a, bz = nokX ? a : b;
                if (bx > x1 && bx < x2 && bz > z1 && bz < z2 && h - 1 > muurH && (a != lo && a != hi)) zet(bx, h - 1, bz, "air");
            }
        }
        // gevelraampje
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        if (nokX) {
            zet(x1, muurH + 2, mz, "glass_pane");
            zet(x2, muurH + 2, mz, "glass_pane");
        } else {
            zet(mx, muurH + 2, z1, "glass_pane");
            zet(mx, muurH + 2, z2, "glass_pane");
        }
        // ramen in de lange muren, bloembakjes eronder
        for (int x = x1 + 2; x <= x2 - 2; x += 2) for (int z : new int[]{z1, z2}) {
            zet(x, 2, z, "glass_pane");
            int buiten = z == z1 ? z1 - 1 : z2 + 1;
            if (vrij(x, 1, buiten) && naam(x, 0, buiten).equals("grass_block")) zet(x, 1, buiten, blad("flowering_azalea"));
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 2) for (int x : new int[]{x1, x2}) {
            zet(x, 2, z, "glass_pane");
            int buiten = x == x1 ? x1 - 1 : x2 + 1;
            if (vrij(buiten, 1, z) && naam(buiten, 0, z).equals("grass_block")) zet(buiten, 1, z, blad("flowering_azalea"));
        }
        // deur
        int dx = deur.equals("east") ? x2 : deur.equals("west") ? x1 : mx;
        int dz = deur.equals("south") ? z2 : deur.equals("north") ? z1 : mz;
        zet(dx, 1, dz, st.deur() + "_door[facing=" + deur + ",half=lower,hinge=left,open=false]");
        zet(dx, 2, dz, st.deur() + "_door[facing=" + deur + ",half=upper,hinge=left,open=false]");
        int ox = deur.equals("east") ? 1 : deur.equals("west") ? -1 : 0, oz = deur.equals("south") ? 1 : deur.equals("north") ? -1 : 0;
        zet(dx + ox, 1, dz + oz, "air");
        zet(dx + ox, 0, dz + oz, "cobblestone");
        // lantaarntje naast de deur, binnen een lamp en een tafeltje
        zet(mx, muurH, mz, "lantern[hanging=true]");
        for (int h = muurH + 1; h < nokH; h++) zet(mx, h, mz, "iron_chain[axis=y]");
        zet(x1 + 1, 1, z1 + 1, "barrel[facing=up]");
        zet(x2 - 1, 1, z1 + 1, "crafting_table");
        zet(x2 - 1, 1, z2 - 1, "potted_red_tulip");
        if (schoorsteen) {
            int sx = nokX ? x2 - 1 : mx, sz = nokX ? mz : z2 - 1;
            vul(sx, 1, sz, sx, nokH + 1, sz, "bricks");
            zet(sx, 1, sz, "furnace[facing=" + (nokX ? "west" : "north") + ",lit=false]");
            zet(sx, nokH + 2, sz, "campfire[lit=true,signal_fire=false,facing=north]");
        }
        return nokH;
    }

    private void zetAs(boolean nokX, int b, int h, int a, String blok) {
        if (nokX) zet(b, h, a, blok);
        else zet(a, h, b, blok);
    }

    /** Voortuintje: hekje langs de voorkant met een opening bij het pad, bloemen ertussen. */
    void voortuin(int x1, int z1, int x2, int z2, int poortX, int poortZ, String hout) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean rand = x == x1 || x == x2 || z == z1 || z == z2;
                if (rand) {
                    if (Math.abs(x - poortX) + Math.abs(z - poortZ) == 0) continue;
                    if (vrij(x, 1, z) || Plan.doorloopbaar(naam(x, 1, z))) zet(x, 1, z, hout + "_fence");
                } else if (grasVrij(x, z) && kans(x, z, 3) != 0) zet(x, 1, z, bloem(x, z));
            }
    }

    // ── alles ───────────────────────────────────────────────────────────────────────────────

    void bouw() {
        brink();
        dorpsstraat();
        poorten();
        Banen.teken(this);
        randBomen();
        begroei();
        uitsparen();
        verbind();
    }

    // ── de brink ────────────────────────────────────────────────────────────────────────────

    void brink() {
        // plein van kinderkopjes rond de spawn, met een waterput erachter
        for (int x = -4; x <= 4; x++)
            for (int z = 5; z <= 15; z++) {
                double d = Math.hypot(x / 4.6, (z - 10) / 5.2);
                if (d <= 1.0) padBlok(x, z, "steen");
            }
        put(0, 13);
        // paden naar de poorten (een V) en naar de straat
        pad(new int[][]{{0, 6}, {-3, 1}, {-6, -3}, {-6, -5}}, 2.6, "zand");
        pad(new int[][]{{0, 6}, {3, 1}, {6, -3}, {6, -5}}, 2.6, "zand");
        pad(new int[][]{{0, 15}, {0, 21}}, 2.6, "zand");
        pad(new int[][]{{-4, 10}, {-17, 10}}, 2.2, "zand");
        pad(new int[][]{{4, 10}, {17, 10}}, 2.2, "zand");
        // de linde: groot en rond, links van de spawn, met een rond bankje
        boom(-11, 15, 5, 5.2, "oak_log", "oak", null, 0);
        for (String r : RICHTING) {
            int bx = r.equals("east") ? 2 : r.equals("west") ? -2 : 0, bz = r.equals("south") ? 2 : r.equals("north") ? -2 : 0;
            zet(-11 + bx, 1, 15 + bz, trap("spruce", r, false));
        }
        // bloemperk tussen de poortpaden: een driehoek met tulpen en lavendel (allium)
        for (int x = -4; x <= 4; x++)
            for (int z = -6; z <= 1; z++) {
                if (Math.abs(x) > (z + 6) * 0.55 + 0.4) continue;
                if (!naam(x, 0, z).equals("grass_block")) continue;
                int ring = (int) Math.round(Math.abs(x) + (1 - z) * 0.3);
                zet(x, 1, z, new String[]{"white_tulip", "pink_tulip", "allium", "red_tulip"}[Math.floorMod(ring, 4)]);
            }
        zet(0, 1, -5, blad("flowering_azalea"));
        // banken langs de brink
        bank(-8, 4, "west", "spruce");
        bank(8, 4, "east", "spruce");
        bank(-3, 17, "south", "spruce");
        bank(2, 17, "south", "spruce");
        // prikbord met de records (rechts van de spawn) en een wegwijzer (links)
        prikbord(6, 9);
        wegwijzer(-5, 7);
        // lantaarns op de hoeken van het plein
        lantaarn(-5, 13, "dark_oak");
        lantaarn(5, 13, "dark_oak");
        // kersenbomen en berken aan de randen van de brink
        kers(12, 16);
        kers(-14, 3);
        berk(13, 2);
        fruitboom(10, -2);
        fruitboom(-10, -2);
        // bloemen in groepjes over de brink
        for (int x = -16; x <= 16; x++)
            for (int z = -12; z <= 21; z++)
                if (kans(x / 3, z / 3, 4) == 0 && kans(x, z, 3) == 0) plant(x, z, bloem(x / 3, z / 3));
    }

    /** Waterput: rand van keien, water erin, twee paaltjes en een dakje. */
    void put(int x, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                zet(x + dx, 0, z + dz, "cobblestone");
                zet(x + dx, 1, z + dz, dx == 0 && dz == 0 ? "air" : "mossy_cobblestone_wall");
            }
        zet(x, 0, z, "water");
        zet(x, -1, z, "water");
        zet(x, -2, z, "mossy_cobblestone");
        zet(x - 1, 2, z, "spruce_fence");
        zet(x + 1, 2, z, "spruce_fence");
        zet(x - 1, 1, z, "mossy_cobblestone");
        zet(x + 1, 1, z, "mossy_cobblestone");
        for (int dx = -1; dx <= 1; dx++) {
            zet(x + dx, 3, z - 1, trap("spruce", "south", false));
            zet(x + dx, 3, z + 1, trap("spruce", "north", false));
            zet(x + dx, 3, z, plaat("spruce", false));
        }
        zet(x, 2, z, "iron_chain[axis=y]");
        zet(x, 1, z, "air");
    }

    void prikbord(int x, int z) {
        // bord van donker hout op twee palen, kijkt naar het westen (naar de spawn)
        vul(x, 1, z - 1, x, 2, z - 1, "dark_oak_fence");
        vul(x, 1, z + 1, x, 2, z + 1, "dark_oak_fence");
        vul(x, 3, z - 1, x, 4, z + 1, "spruce_planks");
        zet(x, 5, z - 1, trap("dark_oak", "south", false));
        zet(x, 5, z, plaat("dark_oak", false));
        zet(x, 5, z + 1, trap("dark_oak", "north", false));
        prikbord = new double[]{x - 0.02, g + 3.0, z + 0.5, 90f};
    }

    void wegwijzer(int x, int z) {
        vul(x, 1, z, x, 4, z, "spruce_fence");
        zet(x, 5, z, "spruce_fence");
        // vier bordjes aan de paal (hangend), elk naar zijn kant; de tekst kijkt naar de spawn
        zet(x, 4, z - 1, "spruce_wall_hanging_sign[facing=east]");
        zet(x, 3, z + 1, "spruce_wall_hanging_sign[facing=east]");
        zet(x - 1, 4, z, "spruce_wall_hanging_sign[facing=south]");
        zet(x + 1, 3, z, "spruce_wall_hanging_sign[facing=south]");
        p.bord(x, g + 4, z - 1, "↑ noord", "Boomhut 9", "Molen 10", "Hooiberg 4");
        p.bord(x, g + 3, z + 1, "↓ zuid", "Daken 7", "Pergola 6", "Vijver 1");
        p.bord(x - 1, g + 4, z, "← west", "Tuinmuur 5", "Vijver 1", "");
        p.bord(x + 1, g + 3, z, "→ oost", "Boomgaard 3", "Buxustuin 2", "Speeltuin 8");
    }

    // ── de dorpsstraat: een ring van kinderkopjes ──────────────────────────────────────────

    void dorpsstraat() {
        int[][] ring = {{-19, -14}, {19, -14}, {21, -12}, {21, 21}, {19, 23}, {-19, 23}, {-21, 21}, {-21, -12}, {-19, -14}};
        pad(ring, 3.2, "steen");
        // uitlopers naar de erven
        pad(new int[][]{{0, -14}, {0, -24}}, 2.4, "zand");                 // noord: molen
        pad(new int[][]{{21, -12}, {27, -20}}, 2.4, "zand");                // noordoost: boerderij
        pad(new int[][]{{21, 4}, {27, 4}}, 2.4, "zand");                    // oost: boomgaard
        pad(new int[][]{{21, 21}, {27, 30}, {27, 46}}, 2.4, "zand");        // zuidoost: buxustuin, speeltuin
        pad(new int[][]{{-21, 21}, {-24, 30}, {-22, 46}}, 2.4, "zand");     // zuidwest: vijver, pergola
        pad(new int[][]{{-21, -4}, {-25, -4}}, 2.4, "zand");                // west: tuinmuur
        pad(new int[][]{{-21, -12}, {-27, -20}}, 2.4, "zand");              // noordwest: bos
        // een paar lantaarns langs de straat (niet te veel)
        for (int[] l : new int[][]{{-17, -16}, {17, -16}, {23, 8}, {-23, 8}, {12, 25}, {-12, 25}}) lantaarn(l[0], l[1], "spruce");
        // hagen aan de binnenkant van de noordkant, achter de poorten
        haag(-15, -11, -10, -11, 2, "oak");
        haag(10, -11, 15, -11, 2, "oak");
        haag(-2, -11, 2, -11, 2, "oak");
        berk(-17, -9);
        berk(17, -9);
        spar(0, -9, 7);
    }

    // ── de poorten: Survival links (rozenboog), KitPvP rechts (smidspoort) ────────────────

    void poorten() {
        int z = -6;
        // Survival: houten boog vol bloeiende azalea, groen licht in de kruin
        int sx = -6;
        for (int s : new int[]{-2, 2}) vul(sx + s, 1, z, sx + s, 4, z, "stripped_oak_log[axis=y]");
        vul(sx - 2, 5, z, sx + 2, 5, z, "stripped_oak_log[axis=x]");
        for (int dx = -3; dx <= 3; dx++) {
            zet(sx + dx, 6, z, blad(kans(dx, z, 2) == 0 ? "flowering_azalea" : "azalea"));
            if (Math.abs(dx) <= 2) zet(sx + dx, 5, z - 1, blad("flowering_azalea"));
            if (Math.abs(dx) <= 2) zet(sx + dx, 5, z + 1, blad("flowering_azalea"));
        }
        for (int s : new int[]{-3, 3}) {
            vul(sx + s, 1, z, sx + s, 4, z, blad(s < 0 ? "flowering_azalea" : "azalea"));
        }
        vul(sx - 1, 7, z, sx + 1, 7, z, blad("flowering_azalea"));
        zet(sx, 6, z, "verdant_froglight");
        zet(sx - 2, 0, z + 1, "moss_block");
        zet(sx + 2, 0, z + 1, "moss_block");
        zet(sx - 2, 1, z + 1, "potted_fern");
        zet(sx + 2, 1, z + 1, "potted_fern");
        for (int dx = -1; dx <= 1; dx++) {
            zet(sx + dx, 0, z, "moss_block");
            for (int h = 1; h <= 4; h++) zet(sx + dx, h, z, "air");
        }
        poorten.add(new Poort(sx - 1, sx + 1, z, "survival"));
        p.label(sx + 0.5, g + 8.8, z + 0.5, "<#9ad59a>Survival</#9ad59a>", 1.5f);

        // KitPvP: boog van steen met zwarte en rode stenen, twee banieren, licht in de kroon
        int kx = 6;
        for (int s : new int[]{-2, 2}) {
            vul(kx + s, 1, z, kx + s, 4, z, "polished_blackstone_bricks");
            zet(kx + s, 1, z, "chiseled_polished_blackstone");
        }
        vul(kx - 2, 5, z, kx + 2, 5, z, "polished_blackstone_bricks");
        zet(kx - 1, 5, z, trap("polished_blackstone_brick", "east", true));
        zet(kx + 1, 5, z, trap("polished_blackstone_brick", "west", true));
        zet(kx, 5, z, "pearlescent_froglight");
        vul(kx - 1, 6, z, kx + 1, 6, z, "red_nether_bricks");
        zet(kx - 2, 6, z, trap("polished_blackstone_brick", "east", false));
        zet(kx + 2, 6, z, trap("polished_blackstone_brick", "west", false));
        zet(kx, 7, z, "polished_blackstone_brick_wall");
        for (int s : new int[]{-3, 3}) {
            zet(kx + s, 1, z, "polished_blackstone_brick_wall");
            zet(kx + s, 2, z, "lantern[hanging=false]");
        }
        zet(kx - 2, 4, z + 1, "red_wall_banner[facing=south]");
        zet(kx + 2, 4, z + 1, "red_wall_banner[facing=south]");
        for (int dx = -1; dx <= 1; dx++) {
            zet(kx + dx, 0, z, "polished_blackstone");
            for (int h = 1; h <= 4; h++) zet(kx + dx, h, z, "air");
        }
        zet(kx - 2, 1, z + 1, "anvil[facing=east]");
        poorten.add(new Poort(kx - 1, kx + 1, z, "kitpvp"));
        p.label(kx + 0.5, g + 8.8, z + 0.5, "<#e58b8b>KitPvP</#e58b8b>", 1.5f);
        // licht in de doorgang (onzichtbaar), zodat de poorten ook 's avonds opvallen
        for (int dx = -1; dx <= 1; dx++) {
            zet(sx + dx, 3, z, "light[level=10]");
            zet(kx + dx, 3, z, "light[level=10]");
        }
    }

    // ── rand en begroeiing ──────────────────────────────────────────────────────────────────

    /** Een losse rij bomen langs de rand, zodat de tuin niet abrupt eindigt. */
    void randBomen() {
        for (int t = -RAND; t <= RAND; t += 5) {
            for (int[] c : new int[][]{{t, -RAND - 2}, {t, RAND + 2}, {-RAND - 2, t}, {RAND + 2, t}}) {
                int x = c[0] + kans(c[0], c[1], 3) - 1, z = c[1] + kans(c[1], c[0], 3) - 1;
                if (!grasVrij(x, z) || !vrijRond(x, z, 2)) continue;
                int k = kans(x, z, 7);
                if (k < 2) spar(x, z, 8 + kans(z, x, 3));
                else if (k < 4) berk(x, z);
                else if (k < 5) kers(x, z);
                else boom(x, z, 4, 2.8, "oak_log", "oak", null, 0);
            }
        }
    }

    boolean vrijRond(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int h = 1; h <= 6; h++)
                    if (!Plan.doorloopbaar(naam(x + dx, h, z + dz))) return false;
        return true;
    }

    /** Kort gras en hier en daar wilde bloemen op lege grasvelden. */
    void begroei() {
        for (int x = -STRAAL; x < STRAAL; x++)
            for (int z = -STRAAL; z < STRAAL; z++) {
                if (!grasVrij(x, z)) continue;
                int k = kans(x * 7, z * 3, 100);
                if (k < 14) zet(x, 1, z, "short_grass");
                else if (k < 16) zet(x, 1, z, "wildflowers[flower_amount=" + (1 + kans(x, z, 4)) + ",facing=north]");
                else if (k < 17) zet(x, 1, z, bloem(x, z));
            }
    }

    /** Lucht tussen de stappen van elk level (zodat geen boom of dak een sprong blokkeert). */
    void uitsparen() {
        for (Baan b : banen)
            for (int i = 0; i + 1 < b.stappen.size(); i++)
                for (Baan.Cel c : b.strook(i))
                    for (int y = c.laag(); y <= c.hoog(); y++) {
                        if (b.stapOp(c.x(), y, c.z()) >= 0) continue;
                        String nm = p.naam(c.x(), y, c.z());
                        if (nm.endsWith("_leaves") || Plan.doorloopbaar(nm)) p.zet(c.x(), y, c.z(), "air");
                    }
    }

    /** Hekken, muurtjes en ruiten verbinden met hun buren (de bouwer zet alles zonder natuurkunde). */
    void verbind() {
        for (int x = p.x1; x <= p.x2; x++)
            for (int z = p.z1; z <= p.z2; z++)
                for (int y = g; y <= p.y2; y++) {
                    String n = p.naam(x, y, z);
                    boolean hekje = n.endsWith("_fence"), muur = n.endsWith("_wall"), ruit = n.endsWith("_pane") || n.equals("iron_bars");
                    if (!hekje && !muur && !ruit) continue;
                    StringBuilder s = new StringBuilder(n).append('[');
                    String[] kant = {"north", "east", "south", "west"};
                    int[][] d = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                    boolean[] aan = new boolean[4];
                    for (int i = 0; i < 4; i++) {
                        String b = p.naam(x + d[i][0], y, z + d[i][1]);
                        aan[i] = verbindtMet(n, b);
                    }
                    for (int i = 0; i < 4; i++) {
                        if (i > 0) s.append(',');
                        s.append(kant[i]).append('=').append(muur ? (aan[i] ? "low" : "none") : aan[i] ? "true" : "false");
                    }
                    if (muur) {
                        boolean recht = (aan[0] && aan[2] && !aan[1] && !aan[3]) || (aan[1] && aan[3] && !aan[0] && !aan[2]);
                        String boven = p.naam(x, y + 1, z);
                        s.append(",up=").append(!recht || !boven.equals("air") ? "true" : "false");
                    }
                    s.append(']');
                    p.zet(x, y, z, s.toString());
                }
    }

    static boolean verbindtMet(String zelf, String buur) {
        if (buur.equals("air") || Plan.doorloopbaar(buur)) return false;
        boolean hekje = zelf.endsWith("_fence"), muur = zelf.endsWith("_wall"), ruit = zelf.endsWith("_pane") || zelf.equals("iron_bars");
        if (hekje) return buur.endsWith("_fence") && buur.contains("nether") == zelf.contains("nether") || buur.endsWith("_fence_gate") || vol(buur);
        if (muur) return buur.endsWith("_wall") || buur.endsWith("_fence_gate") || vol(buur);
        if (ruit) return buur.endsWith("_pane") || buur.equals("iron_bars") || buur.endsWith("glass") || vol(buur);
        return false;
    }

    /** Een vol blok (ruwe schatting op naam): geen trap, plaat, hek, ruit, deur, plant, lantaarn enz. */
    static boolean vol(String n) {
        return !(n.endsWith("_stairs") || n.endsWith("_slab") || n.endsWith("_fence") || n.endsWith("_wall") || n.endsWith("_pane")
                || n.endsWith("_door") || n.endsWith("_trapdoor") || n.endsWith("_sign") || n.endsWith("_banner") || n.contains("lantern")
                || n.equals("iron_chain") || n.equals("iron_bars") || n.equals("ladder") || n.equals("vine") || n.startsWith("potted_")
                || n.equals("flower_pot") || n.endsWith("_leaves") || n.equals("campfire") || n.equals("bell") || n.equals("anvil")
                || n.equals("water") || n.equals("light") || n.endsWith("_carpet") || n.equals("lily_pad") || n.equals("cake")
                || n.endsWith("_button") || n.equals("lever") || n.equals("scaffolding") || Plan.doorloopbaar(n));
    }
}
