package nl.burggraaf.mc.lobbymelding;

/**
 * De tien parkour-levels, elk met zijn eigen stukje tuin eromheen. Steeds een startvlak van 3×3 met een groen
 * midden en een finish van 3×3 met een gouden midden. Alle sprongen worden in de unit-tests nagerekend
 * ({@link Baan#controleer}); hier staan de ontwerpen.
 * <pre>
 *  1 Stapstenen   ★      over de vijver, stenen uit het water
 *  2 Buxushagen   ★      over de geschoren hagen van de buxustuin
 *  3 Boomstronken ★★     stronken en een omgevallen stam in de boomgaard
 *  4 Hooiberg     ★★     hooibalen en tonnen bij de boerderij, eindigt boven op de hooiberg
 *  5 Tuinmuur     ★★★    klimop op, over de oude muur van de moestuin naar het torentje
 *  6 Pergola      ★★★    over de balken van de rozenpergola
 *  7 Daken        ★★★    via een schoorsteen over de nokken van drie huisjes
 *  8 Speeltuin    ★★★★   van de speeltoren op trampolines van slijm
 *  9 Boomhut      ★★★★   over stronken en takken de grote eik in, naar de boomhut
 * 10 Molen        ★★★★★  langs zakken en kisten, de mast op, naar de stelling van de molen
 * </pre>
 */
final class Banen {

    private Banen() {
    }

    static final String START = "lime_concrete", FINISH = "gold_block";

    static void teken(Tuin t) {
        vijver(t);
        buxustuin(t);
        boomgaard(t);
        hooiberg(t);
        tuinmuur(t);
        pergola(t);
        daken(t);
        speeltuin(t);
        boomhut(t);
        molen(t);
    }

    static BaanTekening nieuw(Tuin t, int nr, String naam, String sterren, int ax, int az, int richting) {
        Baan b = new Baan(nr, naam, sterren);
        t.banen.add(b);
        return new BaanTekening(t.p, b, ax, az, richting);
    }

    /** Bordje bij de start (staand bord, kijkt naar de start) en een paaltje. */
    static void startBord(Tuin t, BaanTekening b, int f, int s) {
        int x = b.wx(f, s), z = b.wz(f, s);
        int rot = switch (b.richting) {            // bord kijkt naar achteren: je leest het als je naar de start loopt
            case 0 -> 0;
            case 1 -> 4;
            case 2 -> 8;
            default -> 12;
        };
        t.zet(x, 1, z, "spruce_sign[rotation=" + rot + "]");
        t.p.bord(x, t.g + 1, z, "Parkour " + b.baan.nr, b.baan.naam, b.baan.sterren, "");
    }

    // ── 1 Stapstenen: de vijver ─────────────────────────────────────────────────────────────

    static void vijver(Tuin t) {
        int cx = -32, cz = 36;
        double rx = 11.5, rz = 8.0;
        for (int x = cx - 14; x <= cx + 14; x++)
            for (int z = cz - 11; z <= cz + 11; z++) {
                double dx = (x - cx) / rx, dz = (z - cz) / rz;
                double e = Math.sqrt(dx * dx + dz * dz) + (Tuin.kans(x, z, 10) - 5) * 0.012;
                if (e <= 1.0) {
                    t.zet(x, -2, z, Tuin.kans(x, z, 3) == 0 ? "clay" : "sand");
                    t.zet(x, -1, z, e < 0.75 ? "water" : "sand");
                    t.zet(x, 0, z, "water");
                    t.vul(x, 1, z, x, 3, z, "air");
                    if (e < 0.85 && Tuin.kans(z, x, 11) == 0) t.zet(x, -1, z, "seagrass");
                } else if (e <= 1.18) {
                    t.zet(x, 0, z, Tuin.kans(x, z, 5) == 0 ? "gravel" : "sand");
                    t.zet(x, 1, z, "air");
                    if (Tuin.kans(x, z, 4) == 0) t.vul(x, 1, z, x, 1 + Tuin.kans(z, x, 2) + 1, z, "sugar_cane");
                }
            }
        BaanTekening b = nieuw(t, 1, "Stapstenen", "★", -21, 36, 3);
        b.baan.minMarge = 1.0;
        b.start(1, "spruce_planks", START, null);
        steen(t, b, 3, 0, 1, "mossy_cobblestone");
        steen(t, b, 5, 1, 1, "cobblestone");
        steen(t, b, 7, 1, 1, "mossy_cobblestone");
        steen(t, b, 9, 0, 2, "mossy_stone_bricks");
        steen(t, b, 11, 0, 2, "stone");
        steen(t, b, 13, -1, 1, "cobblestone");
        steen(t, b, 15, -1, 1, "mossy_cobblestone");
        steen(t, b, 17, 0, 2, "mossy_stone_bricks");
        b.finish(20, 0, 1, "spruce_planks", FINISH, null);
        // steigerpaaltjes onder start en finish (in het water)
        for (int[] c : new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {19, -1}, {19, 1}, {21, -1}, {21, 1}})
            t.p.vul(b.wx(c[0], c[1]), t.g - 1, b.wz(c[0], c[1]), b.wx(c[0], c[1]), t.g, b.wz(c[0], c[1]), "spruce_log[axis=y]");
        startBord(t, b, -2, 2);
        // waterlelies (niet onder de stenen)
        for (int x = cx - 11; x <= cx + 11; x++)
            for (int z = cz - 8; z <= cz + 8; z++)
                if (t.naam(x, 0, z).equals("water") && t.vrij(x, 1, z) && Tuin.kans(x, z, 9) == 0 && Math.abs(z - cz) > 1) t.zet(x, 1, z, "lily_pad");
        t.wilg(cx + 2, cz + 11);
        t.wilg(cx - 12, cz - 7);
        t.bank(cx - 3, cz - 10, "north", "oak");
        t.huisje(-46, 17, -40, 23, Tuin.STIJLEN[2], "east", false, true);   // het visserhuisje
        t.pad(new int[][]{{-39, 20}, {-30, 24}, {-24, 30}}, 2.0, "zand");
    }

    /** Stapsteen: blok met een pijler tot de bodem van de vijver. */
    static void steen(Tuin t, BaanTekening b, int f, int s, int h, String mat) {
        b.blok(f, s, h, mat, mat);
        t.p.vul(b.wx(f, s), t.g - 1, b.wz(f, s), b.wx(f, s), t.g, b.wz(f, s), mat);
    }

    // ── 2 Buxushagen: de formele tuin ───────────────────────────────────────────────────────

    static void buxustuin(Tuin t) {
        int x0 = 26, x1 = 57, z0 = 26, z1 = 43;
        // grindpaden en perken in een strak patroon, lage haagjes ver van de baan
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean baan = z >= 31 && z <= 37;
                if (baan) {
                    if (t.naam(x, 0, z).equals("grass_block")) t.zet(x, 0, z, (x + z) % 7 == 0 ? "moss_block" : "grass_block");
                    continue;
                }
                boolean pad = (x - x0) % 8 == 0 || z == z0 || z == z1 || z == 29 || z == 39;
                if (pad) t.padBlok(x, z, "grind");
                else if ((x - x0) % 8 == 1 || (x - x0) % 8 == 7 || z == z0 + 1 || z == z1 - 1 || z == 28 || z == 30 || z == 38 || z == 40)
                    t.zet(x, 1, z, Tuin.blad("oak"));
                else t.zet(x, 1, z, new String[]{"red_tulip", "white_tulip", "pink_tulip", "allium"}[Math.floorMod((x - x0) / 8 + (z > 34 ? 1 : 0), 4)]);
            }
        // vier bolletjes van buxus op de hoeken en een zonnewijzer
        for (int[] c : new int[][]{{x0 + 4, 27}, {x0 + 20, 27}, {x0 + 4, 41}, {x0 + 20, 41}}) {
            t.zet(c[0], 1, c[1], "stone_bricks");
            t.zet(c[0], 2, c[1], Tuin.blad("azalea"));
        }
        BaanTekening b = nieuw(t, 2, "Buxushagen", "★", 29, 34, 1);
        b.baan.minMarge = 1.0;
        b.start(1, "chiseled_stone_bricks", START, null);
        String haag = Tuin.blad("oak"), bol = Tuin.blad("azalea");
        b.vlak(3, 0, 5, 0, 1, haag, null);
        b.blok(7, -2, 1, bol, null);
        b.vlak(9, -2, 11, -2, 1, haag, null);
        b.blok(13, 0, 2, haag, haag);
        b.vlak(15, 0, 17, 0, 2, haag, haag);
        b.blok(19, 2, 1, bol, null);
        b.vlak(21, 2, 23, 2, 1, haag, null);
        b.finish(26, 2, 1, "chiseled_stone_bricks", FINISH, null);
        startBord(t, b, -2, -2);
        t.lantaarn(27, 31, "spruce");
        t.lantaarn(57, 31, "spruce");
    }

    // ── 3 Boomstronken: de boomgaard ────────────────────────────────────────────────────────

    static void boomgaard(Tuin t) {
        for (int x = 27; x <= 56; x += 6)
            for (int z : new int[]{-12, -5, 12, 18}) {
                int bx = x + Tuin.kans(x, z, 3) - 1;
                if (t.vrijRond(bx, z, 1)) t.fruitboom(bx, z);
            }
        // bijenkasten en kisten met appels
        for (int[] c : new int[][]{{30, -8}, {42, -8}, {54, 15}}) t.zet(c[0], 1, c[1], "bee_nest[facing=south,honey_level=0]");
        for (int[] c : new int[][]{{36, 15}, {48, -9}}) {
            t.zet(c[0], 1, c[1], "barrel[facing=up]");
            t.zet(c[0] + 1, 1, c[1], "barrel[facing=up]");
            t.zet(c[0], 2, c[1], "red_carpet");
        }
        BaanTekening b = nieuw(t, 3, "Boomstronken", "★★", 29, 4, 1);
        b.start(1, "stripped_oak_wood[axis=y]", START, null);
        b.blok(3, 0, 1, "oak_log[axis=y]", null);
        b.blok(5, 1, 2, "oak_log[axis=y]", "oak_log[axis=y]");
        b.blok(7, 1, 2, "birch_log[axis=y]", "birch_log[axis=y]");
        b.blok(9, 0, 3, "spruce_log[axis=y]", "spruce_log[axis=y]");
        b.blok(12, 0, 3, "oak_log[axis=y]", "oak_log[axis=y]");
        b.vlak(14, 0, 17, 0, 2, "oak_log[axis=x]", null);       // omgevallen stam op twee blokjes
        t.zet(b.wx(15, 0), 1, b.wz(15, 0), "oak_log[axis=y]");
        t.zet(b.wx(17, 0), 1, b.wz(17, 0), "moss_block");
        b.blok(19, -2, 2, "birch_log[axis=y]", "birch_log[axis=y]");
        b.blok(21, -2, 3, "oak_log[axis=y]", "oak_log[axis=y]");
        b.blok(24, -1, 3, "spruce_log[axis=y]", "spruce_log[axis=y]");
        b.finish(27, -1, 2, "stripped_oak_wood[axis=y]", FINISH, "oak_log[axis=y]");
        startBord(t, b, -2, 2);
        for (int[] c : new int[][]{{5, 3}, {12, -3}, {20, 2}}) {
            int x = b.wx(c[0], c[1]), z = b.wz(c[0], c[1]);
            t.zet(x, 1, z, Tuin.kans(x, z, 2) == 0 ? "red_mushroom" : "brown_mushroom");
        }
    }

    // ── 4 Hooiberg: de boerderij ────────────────────────────────────────────────────────────

    static void hooiberg(Tuin t) {
        // schuur
        t.huisje(42, -52, 52, -42, new Tuin.Stijl("spruce_planks", "dark_oak_log", "dark_oak", "dark_oak_planks", "coarse_dirt", "spruce"),
                "west", false, false);
        // kippenren met drie kippen
        t.hek(37, -32, 44, -32, "oak");
        t.hek(37, -27, 44, -27, "oak");
        t.hek(37, -32, 37, -27, "oak");
        t.hek(44, -32, 44, -27, "oak");
        t.zet(37, 1, -29, "oak_fence_gate[facing=east,open=false]");
        for (int x = 38; x <= 43; x++) for (int z = -31; z <= -28; z++) t.zet(x, 0, z, Tuin.kans(x, z, 3) == 0 ? "coarse_dirt" : "grass_block");
        t.vul(42, 1, -31, 43, 1, -31, "hay_block[axis=x]");
        t.zet(39, 1, -31, "composter[level=0]");
        for (int i = 0; i < 3; i++) t.p.dieren.add(new Plan.Dier("CHICKEN", 39.5 + i * 1.5, t.g + 1, -29.5, i * 90f, false));
        // schapenwei
        t.hek(47, -36, 57, -36, "spruce");
        t.hek(47, -24, 57, -24, "spruce");
        t.hek(47, -36, 47, -24, "spruce");
        t.hek(57, -36, 57, -24, "spruce");
        t.zet(52, 1, -28, "water_cauldron[level=3]");
        t.p.dieren.add(new Plan.Dier("SHEEP", 50.5, t.g + 1, -31.5, 30f, false));
        t.p.dieren.add(new Plan.Dier("SHEEP", 54.5, t.g + 1, -27.5, 200f, false));
        // wagen met hooi en een paar losse balen
        t.vul(36, 1, -40, 38, 1, -38, "spruce_planks");
        t.vul(36, 2, -40, 37, 2, -39, "hay_block[axis=y]");
        t.zet(35, 1, -40, "spruce_trapdoor[facing=west,half=bottom,open=true]");
        BaanTekening b = nieuw(t, 4, "Hooiberg", "★★", 31, -24, 0);
        String hooi = "hay_block[axis=y]";
        b.start(1, "spruce_planks", START, null);
        b.blok(3, 0, 1, hooi, null);
        b.blok(5, 0, 2, hooi, hooi);
        b.blok(7, -1, 2, "barrel[facing=up]", "barrel[facing=up]");
        b.blok(9, -1, 3, hooi, hooi);
        b.vlak(11, 0, 12, 1, 3, hooi, hooi);
        b.blok(15, 0, 3, hooi, hooi);
        b.blok(17, 1, 4, hooi, hooi);
        b.blok(20, 1, 4, hooi, hooi);
        b.finish(23, 1, 4, hooi, FINISH, hooi);
        startBord(t, b, -2, 2);
        t.zet(b.wx(23, 3), 1, b.wz(23, 3), "pitcher_plant[half=lower]");
        t.zet(b.wx(23, 3), 2, b.wz(23, 3), "pitcher_plant[half=upper]");
        t.pad(new int[][]{{27, -20}, {31, -22}, {40, -30}, {41, -47}}, 2.0, "zand");
    }

    // ── 5 Tuinmuur: de ommuurde moestuin ────────────────────────────────────────────────────

    static void tuinmuur(Tuin t) {
        // moestuin ten zuiden van de muur: rijen gewassen, een kasje, een vogelverschrikker
        for (int x = -54; x <= -30; x++)
            for (int z = -3; z <= 13; z++) {
                int rij = Math.floorMod(z + 3, 4);
                if (rij == 3) {
                    t.padBlok(x, z, "zand");
                    continue;
                }
                if (x > -36 && z > 6) continue;
                t.zet(x, 0, z, "farmland[moisture=7]");
                String gewas = switch (Math.floorMod((z + 3) / 4 + x / 9, 4)) {
                    case 0 -> "wheat[age=7]";
                    case 1 -> "carrots[age=7]";
                    case 2 -> "potatoes[age=7]";
                    default -> "beetroots[age=3]";
                };
                t.zet(x, 1, z, gewas);
            }
        t.vul(-42, 0, 5, -42, 0, 5, "water");
        for (int x = -56; x <= -29; x++) t.zet(x, 0, 15, "water");     // slootje langs de zuidkant
        // kasje
        int kx = -34, kz = 8;
        t.vul(kx, 0, kz, kx + 6, 0, kz + 5, "stone_bricks");
        t.vul(kx, 1, kz, kx + 6, 3, kz + 5, "glass");
        t.vul(kx + 1, 1, kz + 1, kx + 5, 3, kz + 4, "air");
        t.vul(kx, 4, kz + 1, kx + 6, 4, kz + 4, "glass");
        t.vul(kx, 5, kz + 2, kx + 6, 5, kz + 3, "glass");
        for (int x = kx; x <= kx + 6; x += 3) t.vul(x, 1, kz, x, 3, kz, "white_concrete");
        for (int x = kx + 1; x <= kx + 5; x++) for (int z = kz + 1; z <= kz + 4; z++) {
            t.zet(x, 0, z, "moss_block");
            t.zet(x, 1, z, Tuin.kans(x, z, 3) == 0 ? "fern" : Tuin.bloem(x, z));
        }
        t.zet(kx, 1, kz + 2, "air");
        t.zet(kx, 2, kz + 2, "air");
        // vogelverschrikker
        t.zet(-46, 1, 1, "oak_fence");
        t.zet(-46, 2, 1, "hay_block[axis=y]");
        t.zet(-46, 3, 1, "carved_pumpkin[facing=south]");
        t.zet(-47, 2, 1, "oak_fence");
        t.zet(-45, 2, 1, "oak_fence");
        BaanTekening b = nieuw(t, 5, "Tuinmuur", "★★★", -25, -8, 3);
        String muur = "stone_bricks", mos = "mossy_stone_bricks";
        b.start(1, "polished_andesite", START, null);
        b.blok(3, 0, 2, "bricks", "bricks");
        b.ladder(5, 0, 2, 4, mos, "voor", "vine");
        b.vlak(8, 0, 11, 0, 4, mos, muur);
        b.vlak(13, 0, 15, 0, 4, muur, mos);
        b.blok(18, 0, 4, mos, muur);
        b.blok(20, 2, 3, muur, muur);
        b.blok(22, 2, 4, mos, muur);
        b.ladder(24, 2, 4, 6, mos, "voor", "vine");
        b.blok(27, 2, 6, muur, mos);
        b.finish(30, 2, 6, mos, FINISH, muur);
        startBord(t, b, -2, 2);
        // een lage muur langs de westkant van de moestuin
        for (int z = -6; z <= 14; z++) if (t.vrij(-57, 1, z)) t.vul(-57, 1, z, -57, 2, z, Tuin.kans(z, 5, 3) == 0 ? mos : muur);
        // klimop tegen de zuidkant van de oude muur (naast de baan, niet erop)
        for (Baan.Stap st : b.baan.stappen) {
            if (st.soort() != Baan.Soort.BLOK || st.y() - t.g < 4) continue;
            for (int x = st.x1(); x <= st.x2(); x++) {
                int z = st.z2() + 1;
                for (int h = 2; h <= st.y() - t.g - 1; h++) if (t.vrij(x, h, z) && Tuin.kans(x, h, 3) != 0) t.zet(x, h, z, "vine[north=true]");
            }
        }
    }

    // ── 6 Pergola: de rozentuin ─────────────────────────────────────────────────────────────

    static void pergola(Tuin t) {
        BaanTekening b = nieuw(t, 6, "Pergola", "★★★", -23, 53, 3);
        String paal = "oak_log[axis=y]", balk = "oak_log[axis=x]";
        b.start(1, "bricks", START, null);
        b.blok(3, 0, 2, "bricks", "bricks");
        b.blok(5, 0, 3, paal, paal);
        b.vlak(7, 0, 10, 0, 3, balk, null);
        b.vlak(13, 0, 15, 0, 3, balk, null);
        b.blok(18, 1, 4, "dark_oak_log[axis=y]", "dark_oak_log[axis=y]");
        b.blok(21, 1, 4, "dark_oak_log[axis=y]", "dark_oak_log[axis=y]");
        b.vlak(23, 0, 26, 0, 3, balk, null);
        b.blok(29, 0, 3, paal, paal);
        b.finish(32, 0, 3, "stripped_oak_wood[axis=y]", FINISH, paal);
        startBord(t, b, -2, -2);
        // de pergola: palen aan beide kanten, dwarsbalken eronder niet (je loopt erop), rozen eromheen
        for (int f = 6; f <= 27; f += 4) {
            for (int s : new int[]{-2, 2}) {
                int x = b.wx(f, s), z = b.wz(f, s);
                t.vul(x, 1, z, x, 3, z, paal);
                t.zet(x, 4, z, Tuin.blad("flowering_azalea"));
            }
        }
        // steunpaaltjes onder de balken
        for (int f : new int[]{7, 10, 13, 15, 23, 26}) {
            int x = b.wx(f, 0), z = b.wz(f, 0);
            t.vul(x, 1, z, x, 2, z, "oak_fence");
        }
        // rozenstruiken en een grindpad eronder
        for (int f = 2; f <= 34; f++)
            for (int s = -4; s <= 4; s++) {
                int x = b.wx(f, s), z = b.wz(f, s);
                if (!t.naam(x, 0, z).equals("grass_block")) continue;
                if (Math.abs(s) <= 1) t.padBlok(x, z, "grind");
                else if (Math.abs(s) == 3 && t.vrij(x, 1, z) && Tuin.kans(x, z, 2) == 0) {
                    t.zet(x, 1, z, "rose_bush[half=lower]");
                    t.zet(x, 2, z, "rose_bush[half=upper]");
                } else if (Math.abs(s) == 4 && t.vrij(x, 1, z)) t.zet(x, 1, z, Tuin.kans(x, z, 3) == 0 ? "peony[half=lower]" : "red_tulip");
            }
        for (int f = 2; f <= 34; f++) for (int s = -4; s <= 4; s++) {
            int x = b.wx(f, s), z = b.wz(f, s);
            if (t.naam(x, 1, z).equals("peony")) t.zet(x, 2, z, "peony[half=upper]");
        }
        t.bank(-40, 58, "south", "spruce");
        t.bank(-30, 47, "north", "spruce");
    }

    // ── 7 Daken: drie huisjes aan de dorpsstraat ────────────────────────────────────────────

    static void daken(Tuin t) {
        int nokA = t.huisje(-14, 28, -8, 34, Tuin.STIJLEN[0], "north", true, false);
        int nokB = t.huisje(-3, 28, 3, 36, Tuin.STIJLEN[1], "north", true, false);
        int nokC = t.huisje(8, 28, 14, 34, Tuin.STIJLEN[3], "north", true, false);
        t.voortuin(-15, 25, -7, 27, -11, 25, "birch");
        t.voortuin(-4, 25, 4, 27, 0, 25, "birch");
        t.voortuin(7, 25, 15, 27, 11, 25, "birch");
        // tuinschuurtje (start) met een ladder aan de westkant
        t.vul(-21, 0, 30, -19, 0, 32, "spruce_planks");
        t.vul(-21, 1, 30, -19, 3, 32, "spruce_planks");
        t.vul(-20, 1, 31, -20, 3, 31, "air");
        for (int[] c : new int[][]{{-21, 30}, {-21, 32}, {-19, 30}, {-19, 32}}) t.vul(c[0], 1, c[1], c[0], 3, c[1], "spruce_log[axis=y]");
        t.zet(-19, 1, 31, "spruce_door[facing=east,half=lower,hinge=left,open=false]");
        t.zet(-19, 2, 31, "spruce_door[facing=east,half=upper,hinge=left,open=false]");
        for (int h = 1; h <= 4; h++) t.zet(-22, h, 31, "ladder[facing=west]");
        BaanTekening b = nieuw(t, 7, "Daken", "★★★", -20, 31, 1);
        b.baan.valVrij = false;                                   // wie valt, landt soms op een dak
        b.start(4, "spruce_planks", START, null);
        b.ladder(3, 0, 4, 9, "bricks", "voor", "ladder");         // de schoorsteen van huisje A
        String nA = Tuin.STIJLEN[0].nok(), nB = Tuin.STIJLEN[1].nok(), nC = Tuin.STIJLEN[3].nok();
        b.vlak(5, 0, 13, 0, nokA, nA, null);                      // nok A (x −15..−7)
        b.vlak(16, 1, 19, 1, nokB, nB, null);                     // nok B (een rij zuidelijker), eerste helft
        b.blok(20, 1, nokB + 1, "bricks", "bricks");              // schoorsteen op de nok
        b.vlak(21, 1, 24, 1, nokB, nB, null);                     // nok B, tweede helft
        b.vlak(27, 0, 35, 0, nokC, nC, null);                     // nok C
        b.finish(38, 0, nokC + 1, "spruce_planks", FINISH, null);  // kraaiennest op palen
        for (int[] c : new int[][]{{37, -1}, {37, 1}, {39, -1}, {39, 1}}) {
            int x = b.wx(c[0], c[1]), z = b.wz(c[0], c[1]);
            t.vul(x, 1, z, x, nokC, z, "spruce_log[axis=y]");
        }
        t.zet(b.wx(40, 0), 1, b.wz(40, 0), "spruce_fence");
        t.zet(b.wx(40, 0), 2, b.wz(40, 0), "bell[attachment=floor,facing=east]");
        startBord(t, b, -2, 2);
        // achtertuinen: waslijn, moestuintje, een kat op het bankje
        for (int x = -14; x <= 14; x++) for (int z = 37; z <= 42; z++) if (t.grasVrij(x, z) && Tuin.kans(x, z, 4) == 0) t.zet(x, 1, z, Tuin.bloem(x, z));
        t.vul(-12, 1, 39, -12, 2, 39, "oak_fence");
        t.vul(-6, 1, 39, -6, 2, 39, "oak_fence");
        for (int x = -11; x <= -7; x++) t.zet(x, 2, 39, x % 2 == 0 ? "white_banner[rotation=0]" : "air");
        t.bank(4, 39, "south", "oak");
        t.p.dieren.add(new Plan.Dier("CAT", 4.5, t.g + 1.6, 39.5, 0f, true));
    }

    // ── 8 Speeltuin: trampolines van slijm ─────────────────────────────────────────────────

    static void speeltuin(Tuin t) {
        BaanTekening b = nieuw(t, 8, "Speeltuin", "★★★★", 30, 52, 1);
        String hout = "spruce_log[axis=y]";
        b.start(4, "spruce_planks", START, "spruce_planks");
        for (int h = 1; h <= 4; h++) t.zet(b.wx(-2, 0), h, b.wz(-2, 0), "ladder[facing=west]");
        b.slijm(3, 0, 1, null);
        b.blok(5, 0, 3, "red_concrete", hout);
        b.blok(7, 0, 4, "yellow_concrete", hout);
        b.ladder(9, 0, 4, 6, hout, "voor", "ladder");
        b.slijm(13, 0, 1, null);
        b.blok(15, 0, 5, "blue_concrete", hout);
        b.blok(17, 1, 5, "lime_concrete", hout);
        b.slijm(20, 1, 2, hout);
        b.blok(22, 1, 4, "orange_concrete", hout);
        b.finish(25, 1, 4, "spruce_planks", FINISH, "spruce_planks");
        startBord(t, b, -2, 2);
        // zandbak, schommel en wip (naast de baan)
        for (int x = 34; x <= 39; x++) for (int z = 44; z <= 47; z++) {
            boolean rand = x == 34 || x == 39 || z == 44 || z == 47;
            t.zet(x, 0, z, rand ? "spruce_planks" : "sand");
            if (rand) t.zet(x, 1, z, plaatS("spruce"));
        }
        for (int x : new int[]{44, 48}) t.vul(x, 1, 45, x, 4, 45, "spruce_fence");
        t.vul(44, 5, 45, 48, 5, 45, "spruce_log[axis=x]");
        for (int x : new int[]{45, 47}) {
            t.vul(x, 2, 45, x, 4, 45, "iron_chain[axis=y]");
            t.zet(x, 1, 45, plaatS("spruce"));
        }
        t.zet(52, 1, 46, "spruce_fence");
        t.vul(50, 2, 46, 54, 2, 46, plaatS("spruce"));
        // glijbaan van de finish naar beneden
        for (int i = 1; i <= 4; i++) t.zet(b.wx(26 + i, 1), 4 - i + 1, b.wz(26 + i, 1), "smooth_quartz_slab[type=bottom]");
    }

    static String plaatS(String hout) {
        return hout + "_slab[type=bottom]";
    }

    // ── 9 Boomhut: de grote eik ─────────────────────────────────────────────────────────────

    static void boomhut(Tuin t) {
        BaanTekening b = nieuw(t, 9, "Boomhut", "★★★★", -30, -26, 0);
        // de eik: dikke stam rechts van de baan, brede kruin hoog
        int tx = b.wx(18, 3), tz = b.wz(18, 3);
        t.boom(tx, tz, 9, 6.0, "oak_log", "oak", null, 0);
        t.vul(tx, 1, tz, tx + 1, 12, tz - 1, "oak_log[axis=y]");
        // takken van de stam naar de baan
        t.zet(b.wx(14, 2), 7, b.wz(14, 2), "oak_log[axis=x]");
        t.zet(b.wx(20, 2), 8, b.wz(20, 2), "oak_log[axis=x]");
        String tak = "oak_log[axis=z]";
        b.start(1, "spruce_planks", START, null);
        b.blok(3, 0, 2, "oak_log[axis=y]", "oak_log[axis=y]");
        b.blok(5, 1, 3, "oak_log[axis=y]", "oak_log[axis=y]");
        b.blok(8, 1, 4, "oak_log[axis=y]", "oak_log[axis=y]");
        b.ladder(10, 1, 4, 7, "oak_log[axis=y]", "voor", "ladder");
        b.vlak(13, 1, 15, 1, 7, tak, null);
        b.blok(17, -1, 8, Tuin.blad("azalea"), null);
        b.vlak(19, -1, 21, -1, 8, tak, null);
        b.blok(24, 0, 8, Tuin.blad("flowering_azalea"), null);
        b.blok(26, 0, 9, tak, null);
        b.finish(29, 0, 9, "spruce_planks", FINISH, null);
        // de boomhut zelf: wandjes en een dakje om de finish, open naar de baan
        for (int f = 27; f <= 31; f++)
            for (int s = -2; s <= 2; s++) {
                boolean rand = f == 27 || f == 31 || Math.abs(s) == 2;
                if (!rand) continue;
                if (f == 27 && Math.abs(s) <= 1) continue;          // ingang
                b.zet(f, 9, s, "spruce_planks");
                b.zet(f, 10, s, (f == 29 || s == 0) && f != 27 ? "glass_pane" : "spruce_fence");
            }
        for (int f = 27; f <= 31; f++) for (int s = -2; s <= 2; s++) b.zet(f, 14, s, plaatS("spruce"));
        for (int f : new int[]{27, 31}) for (int s : new int[]{-2, 2}) b.vul(f, 10, s, f, 13, s, "spruce_log[axis=y]");
        b.zet(31, 13, 0, "lantern[hanging=true]");
        startBord(t, b, -2, -2);
        // bosje eromheen: sparren, berken en varens
        for (int[] c : new int[][]{{-48, -30}, {-52, -40}, {-46, -50}, {-54, -52}, {-20, -40}, {-22, -52}, {-40, -24}, {-50, -22}}) {
            if (!t.vrijRond(c[0], c[1], 2)) continue;
            if (Tuin.kans(c[0], c[1], 2) == 0) t.spar(c[0], c[1], 9 + Tuin.kans(c[1], c[0], 3));
            else t.berk(c[0], c[1]);
        }
        for (int x = -56; x <= -18; x++) for (int z = -56; z <= -20; z++)
            if (t.grasVrij(x, z) && Tuin.kans(x, z, 7) == 0) t.zet(x, 1, z, Tuin.kans(z, x, 3) == 0 ? "fern" : "leaf_litter[segment_amount=2,facing=north]");
    }

    // ── 10 Molen: naar de stelling ──────────────────────────────────────────────────────────

    static void molen(Tuin t) {
        BaanTekening b = nieuw(t, 10, "Molen", "★★★★★", -4, -24, 0);
        b.start(1, "stone_bricks", START, null);
        b.blok(3, 0, 2, "barrel[facing=up]", "barrel[facing=up]");
        b.blok(6, 1, 3, "white_wool", "barrel[facing=up]");
        b.blok(10, 1, 3, "barrel[facing=up]", "spruce_log[axis=y]");
        b.blok(12, -1, 3, "white_wool", "spruce_log[axis=y]");
        b.ladder(14, -1, 3, 6, "spruce_log[axis=y]", "voor", "ladder");
        b.blok(18, -1, 6, "spruce_planks", "spruce_log[axis=y]");
        b.blok(20, 1, 7, "spruce_planks", "spruce_log[axis=y]");
        b.blok(23, 1, 7, "spruce_planks", "spruce_log[axis=y]");
        b.finish(26, 1, 8, "spruce_planks", FINISH, null);
        startBord(t, b, -2, 2);
        // de molen: ronde stenen voet, houten romp, kap, wieken naar het oosten; stelling (omloop) op h8
        int mx = b.wx(32, 1), mz = b.wz(32, 1), top = 17;
        for (int h = 1; h <= top; h++) {
            double r = h <= 7 ? 4.6 : 4.6 - (h - 7) * 0.13;
            for (int dx = -6; dx <= 6; dx++)
                for (int dz = -6; dz <= 6; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > r) continue;
                    boolean wand = d > r - 1.1;
                    t.zet(mx + dx, h, mz + dz, !wand ? "air" : h <= 7 ? (Tuin.kans(dx, h * 7 + dz, 4) == 0 ? "mossy_stone_bricks" : "stone_bricks")
                            : (h % 4 == 0 ? "dark_oak_log[axis=y]" : "dark_oak_planks"));
                }
        }
        for (int h = top + 1; h <= top + 3; h++) {
            int r = top + 3 - h + 1;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r - 1; dz <= r; dz++) t.zet(mx + dx, h, mz + dz, "dark_oak_planks");
        }
        // stelling: ring van planken op h8 met een hekje, sluit aan op de finish
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                double d = Math.hypot(dx, dz);
                if (d < 4.4 || d > 7.4) continue;
                int x = mx + dx, z = mz + dz;
                if (b.baan.stapOp(x, t.g + 8, z) >= 0) continue;
                t.zet(x, 8, z, "spruce_planks");
                if (d > 6.5 && t.vrij(x, 9, z)) t.zet(x, 9, z, "spruce_fence");
                if (d > 6.5 && (dx + dz) % 4 == 0) t.vul(x, 1, z, x, 7, z, "spruce_log[axis=y]");
            }
        // het hekje mag de finish niet afsluiten
        Baan.Stap fin = b.baan.finish();
        for (int x = fin.x1() - 1; x <= fin.x2() + 1; x++) for (int z = fin.z1() - 1; z <= fin.z2() + 1; z++) if (t.naam(x, 9, z).equals("spruce_fence")) t.zet(x, 9, z, "air");
        // deur en ramen
        t.zet(mx, 1, mz + 4, "air");
        t.zet(mx, 2, mz + 4, "air");
        t.zet(mx, 1, mz + 5, "air");
        for (int h : new int[]{11, 14}) t.zet(mx - 4, h, mz, "glass_pane");
        // wieken (kruis) aan de oostkant
        int ax = mx + 5, ah = 14;
        t.zet(mx + 4, ah, mz, "dark_oak_log[axis=x]");
        t.zet(ax, ah, mz, "dark_oak_log[axis=x]");
        for (int k = 1; k <= 8; k++) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int z = mz + d[0] * k, h = ah + d[1] * k;
                if (h < 2) continue;
                t.zet(ax, h, z, "dark_oak_fence");
                if (k >= 3) {
                    int sz = z + (d[1] != 0 ? 1 : 0), sh = h + (d[0] != 0 ? 1 : 0);
                    if (sh >= 2) t.zet(ax, sh, sz, "white_wool");
                }
            }
        }
        // korenveld rond de molen
        for (int x = mx - 16; x <= mx + 16; x++) for (int z = mz - 8; z <= mz + 10; z++) {
            double d = Math.hypot(x - mx, z - mz);
            if (d < 9 || !t.grasVrij(x, z) || Math.abs(x - b.wx(0, 0)) < 5) continue;
            if (Math.floorMod(x, 3) == 0) continue;
            t.zet(x, 0, z, "farmland[moisture=7]");
            t.zet(x, 1, z, "wheat[age=7]");
        }
    }
}
