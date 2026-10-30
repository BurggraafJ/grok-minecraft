package nl.burggraaf.mc.kitpvp;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

/**
 * De hub: waar je op kitpvp binnenkomt en een modus kiest. Een binnenplaats van 55×55 (midden {@link #HX}/{@link #HZ},
 * ten westen van het colosseum) met een overdekte zuilengang rondom, een fontein in het midden, vier tuinen met
 * kersenbomen en lichtpaden naar de poorten. Je komt binnen aan de oostkant en kijkt naar het westen:
 *
 * <pre>
 *            noord (rechts)            de poorten vult {@link ModusRegister}: elke modus heeft een plek
 *   west  ·  fontein  ·  binnenkomst   (PoortStijl.plek). West is de grote poort (in een lichtschacht).
 *            zuid (links)
 * </pre>
 *
 * Een poort van een OPEN modus heeft een open doorgang met deeltjes; erdoorheen lopen = die modus kiezen. Een
 * BINNENKORT-poort is dicht (tralies en een bordje); ertegenaan lopen geeft één grijze regel. De staat van de poort
 * zet de hub bij elke start opnieuw, dus een modus openzetten hoeft de hub niet te herbouwen.
 *
 * <p>Sinds versie 2 (kitpvp 2.17) is de westplek leeg: Story of Zelda kreeg een eigen poort in de lobby. Daar staat nu een
 * dichtgemetselde, bemoste boog met een bordje dat naar de lobby verwijst ({@link #verwijzing}); geen lichtschacht meer.
 */
final class Hub {

    static final int HX = -160, HZ = 0;          // midden van de hub
    static final int H = 28;                     // buitenmuur op |dx| of |dz| = H
    /** 1: drie poorten (west = Story of Zelda). 2: west leeg, met een verwijzing naar de Zelda-poort in de lobby (2.17). */
    static final int VERSIE = 2;

    /** Een plek voor een poort: midden van de muur (ox, oz) en de richting de hub in (ix, iz). */
    record Slot(int ox, int oz, int ix, int iz, boolean groot) {
        int rx() { return iz; }                  // rechts als je naar buiten (door de poort) kijkt
        int rz() { return -ix; }
        int ow() { return groot ? 3 : 2; }       // halve breedte van de doorgang
        int oh() { return groot ? 10 : 7; }      // hoogte van de doorgang
    }

    static final Map<String, Slot> SLOTS = Map.of(
            "west", new Slot(HX - H, HZ, 1, 0, true),
            "noord", new Slot(HX, HZ - H, 0, 1, false),
            "zuid", new Slot(HX, HZ + H, 0, -1, false));

    /** Wat je raakt: de doorgang van een open poort, of (dicht) de tralies. */
    record Raak(Modus modus, boolean dicht) {}

    private final KitPvP plugin;
    private World w;
    private int g;
    private Location aankomst;

    Hub(KitPvP plugin) {
        this.plugin = plugin;
    }

    Deel deel() {
        return new Deel("hub", VERSIE, this::bouw);
    }

    Location aankomst() {
        return aankomst;
    }

    // ── bouwen ───────────────────────────────────────────────────────────────────────────────

    private Bouwer b;

    private void h(int dx, int y, int dz, Material m) {
        b.zet(HX + dx, y, HZ + dz, m);
    }

    private void h(int dx, int y, int dz, org.bukkit.block.data.BlockData d) {
        b.zet(HX + dx, y, HZ + dz, d);
    }

    private void bouw(Bouwer b) {
        this.b = b;
        this.w = b.w;
        this.g = b.g;
        aankomst = new Location(w, HX + 19.5, g + 1, HZ + 0.5, 90f, 0f);
        if (b.plaats) {
            b.vul(HX - 35, g + 1, HZ - 35, HX + 35, g + 22, HZ + 35, Material.AIR);
            b.vul(HX - 35, g - 1, HZ - 35, HX + 35, g - 1, HZ + 35, Material.DIRT);
            b.vul(HX - 35, g, HZ - 35, HX + 35, g, HZ + 35, Material.GRASS_BLOCK);
            for (int dx = -27; dx <= 27; dx++)
                for (int dz = -27; dz <= 27; dz++) h(dx, g, dz, vloer(dx, dz));
            fontein();
            for (int sx : new int[]{-1, 1})
                for (int sz : new int[]{-1, 1}) tuin(sx, sz);
            zuilengang();
            buitenmuur();
            for (Modus m : plugin.modi.alle()) poortBouwen(SLOTS.get(m.poort().plek()), m.poort());
            if (plugin.modi.opPlek("west") == null) verwijzing(SLOTS.get("west"));
        }
        if (plugin.modi.opPlek("west") == null) bordje(SLOTS.get("west"));
        for (Modus m : plugin.modi.alle()) poortStaat(SLOTS.get(m.poort().plek()), m);
        for (Modus m : plugin.modi.alle()) {
            Slot s = SLOTS.get(m.poort().plek());
            b.label(punt(s, 0, 2.5, g + s.oh() + 3.3), Component.text(m.naam(), m.kleur()), 1.35f);
        }
        plugin.ranglijst(new Location(w, HX + 25.5, g + 3.4, HZ + 0.5));
    }

    /** Vloermateriaal op (dx, dz) binnen de muren. */
    private Material vloer(int dx, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz), m = Math.max(ax, az);
        double r = Math.sqrt(dx * dx + dz * dz);
        if (m >= 23) {                                           // zuilengang
            int langs = ax >= az ? dz : dx;
            if (m == 25 && Math.floorMod(langs, 4) == 0) return Material.PEARLESCENT_FROGLIGHT;
            return m == 27 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE;
        }
        if (m == 22) return Material.POLISHED_DEEPSLATE;
        if (r <= 6.5) return Material.POLISHED_DEEPSLATE;        // fontein (zie fontein())
        if (r <= 9.5) {                                          // plein rond de fontein
            if (r > 8.5) return Material.POLISHED_DEEPSLATE;
            if ((ax == 8 && dz == 0) || (az == 8 && dx == 0) || (ax == 6 && az == 6)) return Material.PEARLESCENT_FROGLIGHT;
            return Material.CALCITE;
        }
        // paden naar de poorten (en de binnenkomst in het oosten), 5 breed
        String arm = az <= 2 ? (dx < 0 ? "west" : "oost") : ax <= 2 ? (dz < 0 ? "noord" : "zuid") : null;
        if (arm != null) {
            int dwars = arm.equals("west") || arm.equals("oost") ? dz : dx, langs = Math.max(ax, az);
            if (Math.abs(dwars) == 2) return Material.POLISHED_DEEPSLATE;
            Modus mo = plugin.modi.opPlek(arm);
            Material licht = mo != null ? mo.poort().licht() : Material.PEARLESCENT_FROGLIGHT;
            Material pad = mo != null ? mo.poort().vloer() : Material.SMOOTH_QUARTZ;
            if (dwars == 0 && langs % 3 == 0) return licht;
            return pad;
        }
        if (isTuin(dx, dz)) return Material.GRASS_BLOCK;         // zie tuin()
        if (dx % 6 == 0 && dz % 6 == 0) return Material.PEARLESCENT_FROGLIGHT;
        return (Math.floorDiv(dx + 1, 3) + Math.floorDiv(dz + 1, 3)) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;
    }

    private static boolean isTuin(int dx, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        return ax >= 5 && az >= 5 && ax <= 19 && az <= 19 && dx * dx + dz * dz >= 110;
    }

    private void fontein() {
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > 6.5) continue;
                if (r > 5.5) {                                   // rand
                    h(dx, g, dz, Material.POLISHED_DEEPSLATE);
                    h(dx, g + 1, dz, Material.SMOOTH_QUARTZ);
                    continue;
                }
                h(dx, g - 1, dz, r > 3 && r <= 4 && (dx + dz) % 2 == 0 ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                h(dx, g, dz, r <= 1.5 ? Material.PRISMARINE_BRICKS : Material.WATER);
            }
        for (int dx = -1; dx <= 1; dx++)                         // zuil met een schaal erop
            for (int dz = -1; dz <= 1; dz++) {
                h(dx, g + 1, dz, Material.PRISMARINE_BRICKS);
                h(dx, g + 2, dz, Material.PRISMARINE_BRICKS);
                h(dx, g + 3, dz, dx == 0 && dz == 0 ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                h(dx, g + 4, dz, dx == 0 && dz == 0 ? Material.WATER.createBlockData() : Bouwer.plaat(Material.PRISMARINE_BRICK_SLAB, false));
            }
        for (int[] p : new int[][]{{3, -2}, {-2, 4}, {-4, -1}, {2, 4}, {4, 2}}) h(p[0], g + 1, p[1], Material.LILY_PAD);
    }

    private void tuin(int sx, int sz) {
        Material[] planten = {Material.SHORT_GRASS, Material.SHORT_GRASS, Material.FERN, Material.ALLIUM, Material.CORNFLOWER,
                Material.LILY_OF_THE_VALLEY, Material.PINK_PETALS, Material.AZURE_BLUET, Material.SHORT_GRASS, null, null};
        int tx = sx * 13, tz = sz * 13;
        for (int ax = 5; ax <= 19; ax++)
            for (int az = 5; az <= 19; az++) {
                int dx = sx * ax, dz = sz * az;
                if (!isTuin(dx, dz)) continue;
                boolean rand = !isTuin(dx + 1, dz) || !isTuin(dx - 1, dz) || !isTuin(dx, dz + 1) || !isTuin(dx, dz - 1);
                if (rand) {
                    h(dx, g, dz, Material.POLISHED_DEEPSLATE);
                    h(dx, g + 1, dz, Bouwer.plaat(Material.POLISHED_DEEPSLATE_SLAB, false));
                    continue;
                }
                h(dx, g, dz, Bouwer.hash(dx, dz) % 4 == 0 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK);
                if (Math.abs(dx - tx) <= 1 && Math.abs(dz - tz) <= 1) continue;
                Material p = planten[(int) (Bouwer.hash(dx * 3, dz * 7) % planten.length)];
                if (p != null) h(dx, g + 1, dz, p);
            }
        // kersenboom
        for (int y = g + 1; y <= g + 5; y++) h(tx, y, tz, Material.CHERRY_LOG);
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++)
                for (int y = g + 4; y <= g + 8; y++) {
                    double e = (dx * dx + dz * dz) / 12.0 + (y - g - 6.3) * (y - g - 6.3) / 4.0;
                    if (e > 1 || (dx == 0 && dz == 0 && y <= g + 5)) continue;
                    if (Bouwer.hash(tx + dx, y, tz + dz) % 7 == 0 && e > 0.7) continue;    // rafelige rand
                    h(tx + dx, y, tz + dz, Bouwer.blad(Material.CHERRY_LEAVES));
                }
        h(tx, g + 6, tz, Material.SHROOMLIGHT);
        // azalea-struiken in de hoeken bij de zuilengang
        for (int[] s : new int[][]{{sx * 17, sz * 9}, {sx * 9, sz * 17}}) {
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++)
                    h(s[0] + dx * sx, g + 1, s[1] + dz * sz, Bouwer.blad((dx + dz) % 2 == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
            h(s[0], g + 2, s[1], Bouwer.blad(Material.FLOWERING_AZALEA_LEAVES));
        }
    }

    /** Zuilen op |d| = 22 met bogen ertussen; dak op g+12 tot de buitenmuur; hanglampen eronder. */
    private void zuilengang() {
        boolean[] zuil = new boolean[45];
        for (int i = -22; i <= 22; i++) zuil[i + 22] = Math.floorMod(i, 4) == 2 && Math.abs(i) != 2;
        for (int kant = 0; kant < 4; kant++) {
            for (int i = -22; i <= 22; i++) {
                int dx = kant == 0 ? -22 : kant == 1 ? 22 : i, dz = kant == 2 ? -22 : kant == 3 ? 22 : i;
                if (zuil[i + 22]) {
                    h(dx, g + 1, dz, Material.POLISHED_DEEPSLATE);
                    for (int y = g + 2; y <= g + 10; y++) h(dx, y, dz, Material.QUARTZ_PILLAR);
                    h(dx, g + 11, dz, Material.CHISELED_QUARTZ_BLOCK);
                } else {
                    h(dx, g + 11, dz, Material.QUARTZ_BRICKS);
                    boolean langsX = kant >= 2;
                    if (i + 1 <= 22 && zuil[i + 23]) h(dx, g + 10, dz, Bouwer.trap(Material.QUARTZ_STAIRS, langsX ? BlockFace.EAST : BlockFace.SOUTH, true));
                    else if (i - 1 >= -22 && zuil[i + 21]) h(dx, g + 10, dz, Bouwer.trap(Material.QUARTZ_STAIRS, langsX ? BlockFace.WEST : BlockFace.NORTH, true));
                }
            }
        }
        Slot west = SLOTS.get("west");
        for (int dx = -27; dx <= 27; dx++)
            for (int dz = -27; dz <= 27; dz++) {
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                if (m < 22) continue;
                boolean schacht = plugin.modi.opPlek("west") != null && dx <= -22 && Math.abs(dz) <= west.ow() + 4;   // lichtschacht voor de grote poort
                if (schacht) continue;
                h(dx, g + 12, dz, Material.SMOOTH_QUARTZ);
                int langs = Math.abs(dx) >= Math.abs(dz) ? dz : dx;
                if (m == 25 && Math.floorMod(langs, 4) == 0 && Math.abs(langs) > 3) h(dx, g + 11, dz, Bouwer.hangend(Material.LANTERN));
            }
    }

    private void buitenmuur() {
        for (int i = -H; i <= H; i++)
            for (int[] p : new int[][]{{-H, i}, {H, i}, {i, -H}, {i, H}}) {
                int dx = p[0], dz = p[1];
                h(dx, g, dz, Material.POLISHED_DEEPSLATE);
                h(dx, g + 1, dz, Material.POLISHED_DEEPSLATE);
                h(dx, g + 2, dz, Material.POLISHED_DEEPSLATE);
                h(dx, g + 3, dz, Material.DEEPSLATE_TILES);
                boolean pilaster = Math.floorMod(i, 4) == 2;
                for (int y = g + 4; y <= g + 14; y++)
                    h(dx, y, dz, y == g + 8 ? Material.CHISELED_QUARTZ_BLOCK : pilaster ? Material.QUARTZ_PILLAR : Material.QUARTZ_BRICKS);
                h(dx, g + 15, dz, Material.POLISHED_DEEPSLATE);
                if (i % 2 == 0) h(dx, g + 16, dz, Material.POLISHED_DEEPSLATE_WALL);
            }
    }

    /** Wereldpunt van poort-coördinaten: u langs de muur (rechts als je naar buiten kijkt), v de hub in. */
    private int px(Slot s, int u, int v) {
        return s.ox() + u * s.rx() + v * s.ix();
    }

    private int pz(Slot s, int u, int v) {
        return s.oz() + u * s.rz() + v * s.iz();
    }

    private Location punt(Slot s, double u, double v, double y) {
        return new Location(w, s.ox() + u * s.rx() + v * s.ix() + 0.5, y, s.oz() + u * s.rz() + v * s.iz() + 0.5);
    }

    private void p(Slot s, int u, int v, int y, Material m) {
        b.zet(px(s, u, v), y, pz(s, u, v), m);
    }

    private void p(Slot s, int u, int v, int y, org.bukkit.block.data.BlockData d) {
        b.zet(px(s, u, v), y, pz(s, u, v), d);
    }

    private static BlockFace richting(int x, int z) {
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            if (f.getModX() == x && f.getModZ() == z) return f;
        throw new IllegalArgumentException(x + "," + z);
    }

    /** Poortgebouw: omlijsting in de zuilengang, doorgang door de muur, een verlicht portaal erachter. */
    private void poortBouwen(Slot s, PoortStijl st) {
        int ow = s.ow(), oh = s.oh();
        // portaal achter de muur (v -1..-4), achterwand op v = -5
        for (int v = -5; v <= -1; v++)
            for (int u = -ow - 1; u <= ow + 1; u++) {
                p(s, u, v, g - 1, Material.STONE);
                boolean wand = v == -5 || Math.abs(u) == ow + 1;
                for (int y = g; y <= g + oh + 1; y++) {
                    if (!wand && y > g) p(s, u, v, y, Material.AIR);
                    else if (!wand) p(s, u, v, y, u == 0 ? st.licht() : st.vloer());
                    else if (v == -5 && (u == 0 && y >= g + 2 && y <= g + oh - 1 || Math.abs(u) == 1 && y >= g + 3 && y <= g + oh - 2)) p(s, u, v, y, st.licht());   // lichtpaneel
                    else p(s, u, v, y, st.rand());
                }
                p(s, u, v, g + oh + 2, st.rand());
            }
        // muurvlak rond de doorgang
        for (int u = -ow - 2; u <= ow + 2; u++)
            for (int y = g + 1; y <= g + oh + 2; y++) {
                boolean gat = Math.abs(u) <= ow && y <= g + oh;
                if (!gat || (Math.abs(u) == ow && y == g + oh)) p(s, u, 0, y, st.rand());
            }
        // omlijsting (v = +1): zijkanten, latei met sluitsteen, pilasters met licht, een trapgevel erboven
        for (int y = g + 1; y <= g + oh + 1; y++) {
            p(s, -ow - 1, 1, y, st.rand());
            p(s, ow + 1, 1, y, st.rand());
        }
        for (int u = -ow - 1; u <= ow + 1; u++) p(s, u, 1, g + oh + 1, u == 0 ? st.accent() : st.rand());
        for (int y = g + 1; y <= g + oh + 2; y++) {
            p(s, -ow - 2, 1, y, st.accent());
            p(s, ow + 2, 1, y, st.accent());
        }
        p(s, -ow - 2, 1, g + oh + 3, st.licht());
        p(s, ow + 2, 1, g + oh + 3, st.licht());
        for (int u = -ow - 1; u <= ow + 1; u++) p(s, u, 1, g + oh + 2, st.rand());
        for (int u = -ow; u <= ow; u++) p(s, u, 1, g + oh + 3, st.rand());
        for (int u = -1; u <= 1; u++) p(s, u, 1, g + oh + 4, st.accent());
        // vloer ervoor
        for (int v = 1; v <= 3; v++)
            for (int u = -ow - 1; u <= ow + 1; u++) p(s, u, v, g, u == 0 && v == 2 ? st.licht() : st.vloer());
        // vaandels aan de muur, links en rechts
        BlockFace binnen = richting(s.ix(), s.iz());
        int hy = g + (s.groot() ? 7 : 5);
        if (st.vaandelLinks() != null) p(s, -ow - 4, 1, hy, Bouwer.gericht(st.vaandelLinks(), binnen));
        if (st.vaandelRechts() != null) p(s, ow + 4, 1, hy, Bouwer.gericht(st.vaandelRechts(), binnen));
    }

    /**
     * Een lege poortplek (west, sinds Story of Zelda een eigen poort in de lobby heeft): een oude, dichtgemetselde boog van
     * bemoste steen tegen de muur, met klimop, en een bordje dat de weg wijst ({@link #bordje}). Rustig: geen licht, geen deeltjes.
     */
    private void verwijzing(Slot s) {
        int ow = 2;
        for (int u = -ow; u <= ow; u++)
            for (int y = g + 1; y <= g + 5; y++) {
                boolean rand = Math.abs(u) == ow || y == g + 5;
                Material m = rand ? (Bouwer.hash(u, y) % 3 == 0 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS)
                        : (Bouwer.hash(u * 7, y) % 4 == 0 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
                p(s, u, 0, y, m);                                // dichtgemetseld in de muur
                if (rand) p(s, u, 1, y, m);                      // de boog steekt een blok naar voren
            }
        p(s, 0, 1, g + 5, Material.CHISELED_STONE_BRICKS);
        p(s, -1, 1, g + 4, Bouwer.trap(Material.MOSSY_STONE_BRICK_STAIRS, richting(-s.rx(), -s.rz()), true));   // de boog: naar de zuilen toe
        p(s, 1, 1, g + 4, Bouwer.trap(Material.MOSSY_STONE_BRICK_STAIRS, richting(s.rx(), s.rz()), true));
        p(s, 0, 1, g + 4, Bouwer.plaat(Material.MOSSY_STONE_BRICK_SLAB, true));
        for (int u = -ow + 1; u <= ow - 1; u++) p(s, u, 1, g + 1, Material.AIR);
        p(s, -1, 1, g + 3, Material.AIR);
        p(s, 1, 1, g + 3, Material.AIR);
        p(s, 0, 1, g + 3, Material.AIR);
        p(s, -1, 1, g + 2, Material.AIR);
        p(s, 1, 1, g + 2, Material.AIR);
        for (int u = -1; u <= 1; u++) p(s, u, 1, g, Material.MOSS_BLOCK);
        p(s, -1, 1, g + 1, Material.FERN);
        p(s, 1, 1, g + 1, Material.FERN);
    }

    /** Het bordje in de dichte boog (altijd gezet, ook zonder herbouw): «Story of Zelda · heeft nu een · eigen poort · in de lobby». */
    private void bordje(Slot s) {
        int bx = px(s, 0, 1), bz = pz(s, 0, 1), by = g + 2;
        b.zetAltijd(bx, by, bz, Bouwer.gericht(Material.SPRUCE_WALL_SIGN, richting(s.ix(), s.iz())));
        b.daarna(() -> {
            if (w.getBlockAt(bx, by, bz).getState() instanceof Sign bord) {
                String[] r = {"Story of Zelda", "heeft nu een", "eigen poort", "in de lobby"};
                for (int i = 0; i < r.length; i++) bord.getSide(Side.FRONT).line(i, Component.text(r[i], NamedTextColor.BLACK));
                bord.setWaxed(true);
                bord.update(true, false);
            }
        });
    }

    /** Open = doorgang vrij; binnenkort = tralies en een bordje. Altijd gezet (ook zonder herbouw). */
    private void poortStaat(Slot s, Modus m) {
        int ow = s.ow(), oh = s.oh();
        boolean dicht = m.status() != Modus.Status.OPEN;
        BlockFace r = richting(s.rx(), s.rz());
        var tralie = Bouwer.ruit(Material.IRON_BARS, r, r.getOppositeFace());
        for (int u = -ow; u <= ow; u++)
            for (int y = g + 1; y <= g + oh; y++) {
                if (Math.abs(u) == ow && y == g + oh) continue;
                if (dicht) b.zetAltijd(px(s, u, 0), y, pz(s, u, 0), tralie);
                else b.zetAltijd(px(s, u, 0), y, pz(s, u, 0), Material.AIR);
            }
        int bx = px(s, 0, 1), bz = pz(s, 0, 1), by = g + 3;
        if (!dicht) {
            b.zetAltijd(bx, by, bz, Material.AIR);
            return;
        }
        b.zetAltijd(bx, by, bz, Bouwer.gericht(Material.SPRUCE_WALL_SIGN, richting(s.ix(), s.iz())));
        b.daarna(() -> {
            if (w.getBlockAt(bx, by, bz).getState() instanceof Sign bord) {
                bord.getSide(Side.FRONT).line(1, Component.text("Binnenkort", NamedTextColor.BLACK));
                bord.setWaxed(true);
                bord.update(true, false);
            }
        });
    }

    // ── spelen ───────────────────────────────────────────────────────────────────────────────

    /** Binnen de muren (de portalen tellen niet: daar word je al doorgestuurd). */
    boolean binnen(Location l) {
        if (w == null || !l.getWorld().equals(w)) return false;
        double dx = l.getX() - HX - 0.5, dz = l.getZ() - HZ - 0.5;
        return Math.abs(dx) <= H + 0.5 && Math.abs(dz) <= H + 0.5 && l.getY() >= g - 2 && l.getY() <= g + 20;
    }

    /** Raakt de speler op {@code to} een poort? Dicht telt alleen als je ernaartoe kijkt. */
    Raak raak(Location to) {
        if (w == null) return null;
        int bx = to.getBlockX(), by = to.getBlockY(), bz = to.getBlockZ();
        if (by < g + 1 || by > g + 11) return null;
        for (Modus m : plugin.modi.alle()) {
            Slot s = SLOTS.get(m.poort().plek());
            int dx = bx - s.ox(), dz = bz - s.oz();
            int u = dx * s.rx() + dz * s.rz(), v = dx * s.ix() + dz * s.iz();
            if (Math.abs(u) > s.ow()) continue;
            if (m.status() == Modus.Status.OPEN) {
                if (v <= -1 && v >= -5) return new Raak(m, false);
            } else if (v >= 0 && v <= 1) {
                var d = to.getDirection();
                if (-(d.getX() * s.ix() + d.getZ() * s.iz()) > 0.3) return new Raak(m, true);
            }
        }
        return null;
    }

    /** Elke seconde: water uit de fontein, wat zachte vonkjes, gekleurd stof in de poorten. */
    void deeltjes(List<Player> inHub) {
        if (w == null || inHub.isEmpty()) return;
        w.spawnParticle(Particle.FALLING_WATER, new Location(w, HX + 0.5, g + 5.2, HZ + 0.5), 8, 0.6, 0.1, 0.6, 0);
        w.spawnParticle(Particle.SPLASH, new Location(w, HX + 0.5, g + 1.1, HZ + 0.5), 6, 2.5, 0, 2.5, 0);
        w.spawnParticle(Particle.END_ROD, new Location(w, HX + 0.5, g + 4, HZ + 0.5), 2, 9, 2.5, 9, 0.004);
        for (Modus m : plugin.modi.alle()) {
            Slot s = SLOTS.get(m.poort().plek());
            boolean open = m.status() == Modus.Status.OPEN;
            Location c = punt(s, 0, open ? 0 : -2, g + 1 + s.oh() / 2.0);
            var stof = new Particle.DustOptions(m.poort().deeltjes(), open ? 1.3f : 0.9f);
            double ux = Math.abs(s.rx()) * (s.ow() * 0.45), uz = Math.abs(s.rz()) * (s.ow() * 0.45);
            w.spawnParticle(Particle.DUST, c, open ? 10 : 3, ux + Math.abs(s.ix()) * 0.2, s.oh() * 0.3, uz + Math.abs(s.iz()) * 0.2, 0, stof);
        }
    }

    /** Waar je de poort van modus m raakt: in het open portaal, of (dicht) vlak voor de tralies, kijkend naar buiten. */
    Location poortPunt(Modus m) {
        Slot s = SLOTS.get(m.poort().plek());
        boolean open = m.status() == Modus.Status.OPEN;
        Location l = punt(s, 0, open ? -2 : 1, g + 1);
        l.setDirection(new org.bukkit.util.Vector(-s.ix(), 0, -s.iz()));
        return l;
    }

    // ── zelftest ─────────────────────────────────────────────────────────────────────────────

    void zelftest(List<String> fouten, List<String> info) {
        if (w == null || aankomst == null) {
            fouten.add("hub: niet gebouwd");
            return;
        }
        Block onder = aankomst.clone().add(0, -1, 0).getBlock();
        if (!onder.getType().isSolid()) fouten.add("hub: geen vloer onder de binnenkomst (" + onder.getType() + ")");
        if (!aankomst.getBlock().isPassable() || !aankomst.clone().add(0, 1, 0).getBlock().isPassable()) fouten.add("hub: binnenkomst zit vast");
        if (w.getBlockAt(HX, g, HZ).getType() != Material.PRISMARINE_BRICKS) fouten.add("hub: fontein ontbreekt");
        if (w.getBlockAt(HX - H, g + 5, HZ + 9).getType() != Material.QUARTZ_BRICKS) fouten.add("hub: buitenmuur ontbreekt");
        int poorten = 0;
        for (Modus m : plugin.modi.alle()) {
            Slot s = SLOTS.get(m.poort().plek());
            Material midden = w.getBlockAt(px(s, 0, 0), g + 2, pz(s, 0, 0)).getType();
            Material lijst = w.getBlockAt(px(s, -s.ow() - 1, 1), g + 2, pz(s, -s.ow() - 1, 1)).getType();
            if (lijst != m.poort().rand()) fouten.add("poort " + m.id() + ": omlijsting ontbreekt (" + lijst + ")");
            if (m.status() == Modus.Status.OPEN) {
                if (midden != Material.AIR) fouten.add("poort " + m.id() + ": open maar de doorgang is " + midden);
                Raak r = raak(poortPunt(m));
                if (r == null || r.modus() != m || r.dicht()) fouten.add("poort " + m.id() + ": doorgang stuurt niet naar de modus");
            } else {
                if (midden != Material.IRON_BARS) fouten.add("poort " + m.id() + ": binnenkort maar niet dicht (" + midden + ")");
                Block bord = w.getBlockAt(px(s, 0, 1), g + 3, pz(s, 0, 1));
                if (!(bord.getState() instanceof Sign sg) || !"Binnenkort".equals(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(sg.getSide(Side.FRONT).line(1))))
                    fouten.add("poort " + m.id() + ": bordje «Binnenkort» ontbreekt");
                Raak r = raak(poortPunt(m));
                if (r == null || r.modus() != m || !r.dicht()) fouten.add("poort " + m.id() + ": tegen de tralies lopen geeft geen melding");
            }
            poorten++;
        }
        if (plugin.modi.opPlek("west") == null) {                // de lege westplek: de verwijzing naar de Zelda-poort in de lobby
            Slot s = SLOTS.get("west");
            Block bord = w.getBlockAt(px(s, 0, 1), g + 2, pz(s, 0, 1));
            if (!(bord.getState() instanceof Sign sg) || !"Story of Zelda".equals(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(sg.getSide(Side.FRONT).line(0))))
                fouten.add("hub west: het bordje naar de Zelda-poort in de lobby ontbreekt");
            if (w.getBlockAt(px(s, 0, 0), g + 2, pz(s, 0, 0)).isPassable()) fouten.add("hub west: de oude poort staat nog open");
        }
        info.add("hub=" + HX + "," + HZ + " poorten=" + poorten);
    }
}
