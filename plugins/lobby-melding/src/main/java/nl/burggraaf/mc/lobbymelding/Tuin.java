package nl.burggraaf.mc.lobbymelding;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

/**
 * De tuinlobby, procedureel gebouwd (geen downloads, altijd hetzelfde resultaat):
 * - midden: plaza met fontein, bloemperken, bankjes, twee poorten (Survival, KitPvP) en een muziektent;
 * - een gracht rond het midden (straal 30-33) met vier houten bruggen;
 * - lanen kruisen het hele terrein (N-Z en O-W) met hagen, bloemranden en lantaarns;
 * - langs de N-Z-laan een dorp van 20 huisjes in allerlei stijlen, met tuintjes;
 * - in de kwadranten: een vijver met brug, een heggendoolhof en prieel, een boomgaard met bijen en pompoenen,
 *   een picknickweide met bloemenspiraal;
 * - langs de O-W-laan tulpenvelden in banen, een molen en een kas;
 * - de parkour-levels (LobbyMelding) liggen buiten, met een parkourlaan en een poortje per level.
 */
final class Tuin {

    static final int RAND = 126;                 // terrein van −RAND..RAND
    static final int GRACHT_IN = 30, GRACHT_UIT = 33;
    static final int PARKOURLAAN = 41;           // x = ±40..42
    static final int[] DORP_Z = {46, 62, 78, 94, 110};

    final World w;
    final int g;                                 // grondhoogte (gras op y = g)
    final boolean plaats;
    final Random rnd = new Random(20261026L);
    private final List<int[]> bezet = new ArrayList<>();   // x1, z1, x2, z2 (geen bomen daar)

    // poorten (triggers): x1, x2, z (blok), naar server
    record Poort(int x1, int x2, int z, String server) {}
    final List<Poort> poorten = new ArrayList<>();
    final List<Object[]> labels = new ArrayList<>();       // Location, String (MiniMessage), schaal

    Tuin(World w, int g, boolean plaats) {
        this.w = w;
        this.g = g;
        this.plaats = plaats;
    }

    // ── gereedschap ─────────────────────────────────────────────────────────────────────────

    void zet(int x, int y, int z, Material m) {
        if (plaats) w.getBlockAt(x, y, z).setType(m, false);
    }

    void zet(int x, int y, int z, BlockData d) {
        if (plaats) w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    void vul(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        if (!plaats) return;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != m) b.setType(m, false);
                }
    }

    void vul(int x1, int y1, int z1, int x2, int y2, int z2, BlockData d) {
        if (!plaats) return;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    Material type(int x, int y, int z) {
        return w.getBlockAt(x, y, z).getType();
    }

    static BlockData blad(Material m) {
        Leaves l = (Leaves) m.createBlockData();
        l.setPersistent(true);
        return l;
    }

    static BlockData trap(Material m, BlockFace f) {
        Stairs s = (Stairs) m.createBlockData();
        s.setFacing(f);
        return s;
    }

    static BlockData trapOm(Material m, BlockFace f) {
        Stairs s = (Stairs) m.createBlockData();
        s.setFacing(f);
        s.setHalf(Bisected.Half.TOP);
        return s;
    }

    static BlockData slab(Material m, boolean boven) {
        Slab s = (Slab) m.createBlockData();
        s.setType(boven ? Slab.Type.TOP : Slab.Type.BOTTOM);
        return s;
    }

    static BlockData richting(Material m, BlockFace f) {
        BlockData d = m.createBlockData();
        if (d instanceof Directional dir && dir.getFaces().contains(f)) dir.setFacing(f);
        return d;
    }

    void deur(int x, int y, int z, Material m, BlockFace f) {
        Door onder = (Door) m.createBlockData();
        onder.setFacing(f);
        onder.setHalf(Bisected.Half.BOTTOM);
        Door boven = (Door) onder.clone();
        boven.setHalf(Bisected.Half.TOP);
        zet(x, y, z, onder);
        zet(x, y + 1, z, boven);
    }

    void reserveer(int x1, int z1, int x2, int z2) {
        bezet.add(new int[]{Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)});
    }

    boolean vrij(int x, int z, int marge) {
        for (int[] r : bezet) if (x >= r[0] - marge && x <= r[2] + marge && z >= r[1] - marge && z <= r[3] + marge) return false;
        return true;
    }

    long hash(int x, int z) {
        long h = x * 73428767L ^ z * 912931L ^ 0x9E3779B97F4A7C15L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        return Math.abs(h ^ (h >>> 15));
    }

    void label(double x, double y, double z, String mm, float schaal) {
        labels.add(new Object[]{new Location(w, x, y, z), mm, schaal});
    }

    void lantaarnpaal(int x, int z) {
        if (!vrijBlok(x, g + 1, z)) return;
        zet(x, g + 1, z, Material.STONE_BRICK_WALL);
        vul(x, g + 2, z, x, g + 3, z, Material.DARK_OAK_FENCE);
        zet(x, g + 4, z, Material.LANTERN);
    }

    boolean vrijBlok(int x, int y, int z) {
        return !plaats || type(x, y, z).isAir() || Tag.isPlant(type(x, y, z));
    }

    void boom(int x, int z, TreeType t) {
        if (!plaats) return;
        zet(x, g, z, Material.GRASS_BLOCK);
        Location l = new Location(w, x, g + 1, z);
        if (!w.generateTree(l, new Random(hash(x, z)), t)) w.generateTree(l, new Random(hash(x, z)), TreeType.TREE);
    }

    static final Material[] BLOEMEN = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.OXEYE_DAISY,
            Material.ALLIUM, Material.PINK_TULIP, Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP,
            Material.WHITE_TULIP, Material.LILY_OF_THE_VALLEY, Material.BLUE_ORCHID};

    Material bloem(int x, int z) {
        return BLOEMEN[(int) (hash(x, z) % BLOEMEN.length)];
    }

    /** Kleine helper voor plantjes (mogen overschreven worden). */
    static final class Tag {
        static boolean isPlant(Material m) {
            String n = m.name();
            return n.endsWith("_TULIP") || n.equals("SHORT_GRASS") || n.equals("TALL_GRASS") || n.equals("POPPY") || n.equals("DANDELION")
                    || n.equals("CORNFLOWER") || n.equals("OXEYE_DAISY") || n.equals("ALLIUM") || n.equals("AZURE_BLUET")
                    || n.equals("LILY_OF_THE_VALLEY") || n.equals("BLUE_ORCHID") || n.equals("FERN");
        }
    }

    // ── alles ───────────────────────────────────────────────────────────────────────────────

    void bouw() {
        reserveer(-14, -14, 14, 14);                                  // plaza
        lanen();
        gracht();
        binnenring();
        for (int sz : new int[]{1, -1})
            for (int sx : new int[]{1, -1})
                for (int i = 0; i < DORP_Z.length; i++) huisje(sx, sz * DORP_Z[i], i + (sx > 0 ? 0 : 5) + (sz > 0 ? 0 : 10));
        vijver(28, -85);
        doolhof(-38, -114);
        prieel(-25, -60);
        boomgaard(20, 50);
        picknick(-28, 60);
        tulpen();
        molen(100, -22);
        kas(-100, -22);
        uitkijk(100, 22);
        bloemenhart(-100, 22);
        parkourlanen();
        bomen();
    }

    // ── lanen ───────────────────────────────────────────────────────────────────────────────

    void lanen() {
        // Noord-zuid (x −2..2) en oost-west (z −2..2): grind en paadjes, bloemranden, lage haag, lantaarns.
        for (int t = -RAND + 4; t <= RAND - 4; t++) {
            for (int d = -2; d <= 2; d++) {
                Material m = Math.abs(d) == 2 ? Material.POLISHED_ANDESITE : (hash(d, t) % 4 == 0 ? Material.GRAVEL : Material.DIRT_PATH);
                if (Math.abs(t) <= 13) continue;                       // plaza
                if (Math.abs(t) >= GRACHT_IN - 1 && Math.abs(t) <= GRACHT_UIT + 1) continue;   // brug
                zet(d, g, t, m);
                zet(t, g, d, m);
            }
            boolean kruising = Math.abs(t) <= 4 || Math.abs(Math.abs(t) - PARKOURLAAN) <= 3 || Math.abs(t) <= 14
                    || (Math.abs(t) >= 20 && Math.abs(t) <= 23)
                    || (Math.abs(t) >= GRACHT_IN - 2 && Math.abs(t) <= GRACHT_UIT + 2);
            boolean dorpPad = false;
            for (int dz : DORP_Z) if (Math.abs(Math.abs(t) - dz) <= 1) dorpPad = true;
            for (int s : new int[]{-1, 1}) {
                if (kruising) continue;
                // langs N-Z
                if (!dorpPad) {
                    zet(s * 3, g + 1, t, bloem(s * 3, t));
                    zet(s * 4, g + 1, t, blad(hash(s, t) % 5 == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
                }
                // langs O-W
                if (Math.abs(Math.abs(t) - PARKOURLAAN) > 3) {
                    zet(t, g + 1, s * 3, bloem(t, s * 3));
                    zet(t, g + 1, s * 4, blad(hash(t, s) % 5 == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
                }
            }
            if (Math.abs(t) > 16 && Math.floorMod(t, 12) == 6 && !kruising) {
                for (int s : new int[]{-1, 1}) {
                    if (!dorpPad) {
                        zet(s * 4, g + 1, t, Material.AIR);
                        lantaarnpaal(s * 3, t);
                    }
                    zet(t, g + 1, s * 4, Material.AIR);
                    lantaarnpaal(t, s * 3);
                }
                if (Math.floorMod(t, 36) == 6) {                       // af en toe een lampion-pompoen (het is bijna Halloween)
                    zet(5, g + 1, t, richting(Material.JACK_O_LANTERN, BlockFace.WEST));
                    zet(t, g + 1, -5, richting(Material.JACK_O_LANTERN, BlockFace.SOUTH));
                }
            }
        }
        reserveer(-5, -RAND, 5, RAND);
        reserveer(-RAND, -5, RAND, 5);
    }

    // ── gracht met bruggen ──────────────────────────────────────────────────────────────────

    void gracht() {
        for (int x = -GRACHT_UIT - 2; x <= GRACHT_UIT + 2; x++)
            for (int z = -GRACHT_UIT - 2; z <= GRACHT_UIT + 2; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r >= GRACHT_IN - 0.5 && r < GRACHT_IN + 0.5 || r >= GRACHT_UIT + 0.5 && r < GRACHT_UIT + 1.5) {
                    zet(x, g, z, Material.POLISHED_ANDESITE);          // kade
                    zet(x, g + 1, z, Material.AIR);
                } else if (r >= GRACHT_IN + 0.5 && r < GRACHT_UIT + 0.5) {
                    zet(x, g - 2, z, hash(x, z) % 3 == 0 ? Material.CLAY : Material.SAND);
                    zet(x, g - 1, z, Material.WATER);
                    zet(x, g, z, Material.WATER);
                    vul(x, g + 1, z, x, g + 3, z, Material.AIR);
                    if (hash(x, z) % 23 == 0) zet(x, g + 1, z, Material.LILY_PAD);
                    if (hash(z, x) % 31 == 0) zet(x, g - 1, z, Material.SEAGRASS);
                }
            }
        // vier bruggen (5 breed) over de lanen
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            int fx = f.getModX(), fz = f.getModZ();
            for (int t = GRACHT_IN - 1; t <= GRACHT_UIT + 1; t++) {
                boolean rand = t == GRACHT_IN - 1 || t == GRACHT_UIT + 1;
                for (int d = -2; d <= 2; d++) {
                    int x = fx * t + (fz != 0 ? d : 0), z = fz * t + (fx != 0 ? d : 0);
                    if (rand) zet(x, g + 1, z, slab(Material.SPRUCE_SLAB, false));
                    else zet(x, g + 1, z, Material.SPRUCE_PLANKS);
                    if (!rand) zet(x, g, z, Material.WATER);
                }
                for (int s : new int[]{-3, 3}) {
                    int x = fx * t + (fz != 0 ? s : 0), z = fz * t + (fx != 0 ? s : 0);
                    zet(x, g + 1, z, Material.SPRUCE_PLANKS);
                    zet(x, g + 2, z, (t == GRACHT_IN - 1 || t == GRACHT_UIT + 1) ? Material.SPRUCE_LOG : Material.SPRUCE_FENCE);
                    if (t == GRACHT_IN - 1 || t == GRACHT_UIT + 1) zet(x, g + 3, z, Material.LANTERN);
                }
            }
        }
    }

    // ── binnen de gracht: bloemperken, poorten, muziektent ──────────────────────────────────

    void binnenring() {
        for (int x = -GRACHT_IN; x <= GRACHT_IN; x++)
            for (int z = -GRACHT_IN; z <= GRACHT_IN; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r < 14.5 || r > GRACHT_IN - 1.5) continue;
                if (Math.abs(x) <= 4 || Math.abs(z) <= 4 || !vrij(x, z, 0)) continue;
                double a = Math.atan2(z, x);
                int sector = (int) Math.floor((a + Math.PI) / (Math.PI / 8));
                // ring-pad op straal 21-22
                if (r >= 20.5 && r < 22.5) {
                    zet(x, g, z, hash(x, z) % 5 == 0 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
                    continue;
                }
                if (r >= 16 && r < 19.5 && sector % 2 == 0) {                 // bloemperken
                    zet(x, g, z, Material.GRASS_BLOCK);
                    Material[] perk = {Material.RED_TULIP, Material.PINK_TULIP, Material.WHITE_TULIP, Material.ORANGE_TULIP,
                            Material.ALLIUM, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.LILY_OF_THE_VALLEY};
                    zet(x, g + 1, z, perk[(sector / 2) % perk.length]);
                } else if (r >= 24 && r < 27.5 && hash(x, z) % 7 == 0) {
                    zet(x, g + 1, z, bloem(x, z));
                }
            }
        poort(-10, -22, "survival");
        poort(10, -22, "kitpvp");
        muziektent(-17, 17);
        kraampjes();
        for (int i = 0; i < 4; i++) {                                        // kersenbomen op de diagonalen
            double a = Math.PI / 4 + Math.PI / 2 * i;
            int x = (int) Math.round(Math.cos(a) * 25), z = (int) Math.round(Math.sin(a) * 25);
            if (!vrij(x, z, 2)) continue;
            boom(x, z, TreeType.CHERRY);
        }
        // bankjes langs het ringpad
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 / 16 * i + Math.PI / 16;
            int x = (int) Math.round(Math.cos(a) * 23.5), z = (int) Math.round(Math.sin(a) * 23.5);
            if (Math.abs(x) <= 4 || Math.abs(z) <= 4 || !vrij(x, z, 0)) continue;
            BlockFace naarMidden = Math.abs(x) > Math.abs(z) ? (x > 0 ? BlockFace.WEST : BlockFace.EAST) : (z > 0 ? BlockFace.NORTH : BlockFace.SOUTH);
            zet(x, g + 1, z, trap(Material.SPRUCE_STAIRS, naarMidden.getOppositeFace()));
        }
        label(0.5, g + 4.2, -8.5,
                "<gold><bold>Welkom in de tuin van Burggraaf Craft</bold></gold>\n<white>Loop door een poort: <green>Survival</green> · <red>KitPvP</red></white>\n<gray>10 parkour-levels verstopt in de tuin: volg de bordjes of het kompas</gray>", 1.1f);
    }

    /** Poort (5 breed, 6 hoog) met een open doorgang van 3×4; erdoor lopen = naar die server. */
    void poort(int cx, int z, String server) {
        boolean surv = server.equals("survival");
        Material pilaar = surv ? Material.STRIPPED_OAK_LOG : Material.POLISHED_BLACKSTONE_BRICKS;
        Material accent = surv ? Material.MOSS_BLOCK : Material.RED_NETHER_BRICKS;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -4; dz <= 2; dz++) {
                zet(cx + dx, g, z + dz, surv ? (hash(dx, dz) % 3 == 0 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK)
                        : (Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? Material.POLISHED_BLACKSTONE : Material.BLACKSTONE);
                vul(cx + dx, g + 1, z + dz, cx + dx, g + 7, z + dz, Material.AIR);
            }
        for (int s : new int[]{-2, 2}) {
            vul(cx + s, g + 1, z, cx + s, g + 5, z, pilaar);
            zet(cx + s, g + 6, z, accent);
        }
        vul(cx - 2, g + 5, z, cx + 2, g + 5, z, accent);
        vul(cx - 1, g + 6, z, cx + 1, g + 6, z, surv ? Material.OAK_LEAVES : Material.CHISELED_POLISHED_BLACKSTONE);
        if (surv) {
            for (int dx = -2; dx <= 2; dx++) zet(cx + dx, g + 6, z, blad(Material.FLOWERING_AZALEA_LEAVES));
            zet(cx, g + 7, z, blad(Material.AZALEA_LEAVES));
            zet(cx - 2, g + 1, z + 1, Material.POTTED_FERN);
            zet(cx + 2, g + 1, z + 1, Material.POTTED_FERN);
        } else {
            zet(cx, g + 7, z, Material.IRON_BARS);
            zet(cx - 2, g + 1, z + 1, Material.ANVIL);
            zet(cx + 2, g + 1, z + 1, Material.TARGET);
        }
        for (int dx = -1; dx <= 1; dx++)                               // onzichtbaar licht in de doorgang
            for (int y = 1; y <= 4; y++) zet(cx + dx, g + y, z, Material.LIGHT);
        for (int s : new int[]{-3, 3}) lantaarnpaal(cx + s, z + 2);
        for (int t = z + 1; t <= -9; t++) zet(cx, g, t, surv ? Material.DIRT_PATH : Material.POLISHED_BLACKSTONE_BRICKS);
        poorten.add(new Poort(cx - 1, cx + 1, z, server));
        label(cx + 0.5, g + 8.6, z + 0.5, surv ? "<green><bold>🌲 SURVIVAL</bold></green>\n<gray>loop erdoorheen</gray>"
                : "<red><bold>⚔ KITPVP</bold></red>\n<gray>loop erdoorheen</gray>", 1.4f);
        reserveer(cx - 4, z - 5, cx + 4, z + 3);
    }

    void muziektent(int cx, int cz) {
        // achthoekige tent op een verhoging, met trapje naar het ringpad
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 6) continue;
                zet(cx + dx, g + 1, cz + dz, Material.STONE_BRICKS);
                zet(cx + dx, g + 2, cz + dz, Material.SPRUCE_PLANKS);
                vul(cx + dx, g + 3, cz + dz, cx + dx, g + 7, cz + dz, Material.AIR);
            }
        int[][] palen = {{-3, -2}, {-3, 2}, {3, -2}, {3, 2}, {-2, -3}, {2, -3}, {-2, 3}, {2, 3}};
        for (int[] p : palen) vul(cx + p[0], g + 3, cz + p[1], cx + p[0], g + 5, cz + p[1], Material.WHITE_CONCRETE);
        for (int lvl = 0; lvl < 3; lvl++) {
            int r = 4 - lvl;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > r + 2) continue;
                    zet(cx + dx, g + 6 + lvl, cz + dz, lvl == 2 ? Material.RED_CONCRETE : (Math.abs(dx) + Math.abs(dz) >= r + 1 ? Material.RED_TERRACOTTA : Material.WHITE_TERRACOTTA));
                }
        }
        zet(cx, g + 9, cz, Material.LIGHTNING_ROD);
        zet(cx, g + 5, cz, Material.IRON_CHAIN);
        zet(cx, g + 4, cz, Material.LANTERN);
        zet(cx, g + 3, cz, Material.JUKEBOX);
        zet(cx + 1, g + 3, cz - 1, Material.NOTE_BLOCK);
        zet(cx - 1, g + 3, cz + 1, Material.NOTE_BLOCK);
        zet(cx + 4, g + 1, cz - 3, trap(Material.STONE_BRICK_STAIRS, BlockFace.WEST));
        zet(cx + 3, g + 2, cz - 3, trap(Material.SPRUCE_STAIRS, BlockFace.WEST));
        label(cx + 0.5, g + 10.5, cz + 0.5, "<light_purple>♪ Muziektent ♪</light_purple>", 0.9f);
        reserveer(cx - 5, cz - 5, cx + 5, cz + 5);
    }

    void kraampjes() {
        Material[][] luifel = {{Material.RED_WOOL, Material.WHITE_WOOL}, {Material.BLUE_WOOL, Material.WHITE_WOOL},
                {Material.GREEN_WOOL, Material.YELLOW_WOOL}, {Material.ORANGE_WOOL, Material.WHITE_WOOL}};
        int[][] plek = {{17, 6}, {24, 6}, {17, -8}, {24, -8}};
        String[] naam = {"Bloemen", "Kaas", "Stroopwafels", "Pompoenen"};
        Material[] waar = {Material.POTTED_RED_TULIP, Material.YELLOW_GLAZED_TERRACOTTA, Material.CAKE, Material.CARVED_PUMPKIN};
        for (int i = 0; i < plek.length; i++) {
            int x = plek[i][0], z = plek[i][1];
            for (int dx = 0; dx <= 4; dx++)
                for (int dz = 0; dz <= 2; dz++) {
                    vul(x + dx, g + 1, z + dz, x + dx, g + 4, z + dz, Material.AIR);
                    zet(x + dx, g + 5, z + dz, luifel[i][dx % 2]);
                }
            for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 2}, {4, 2}}) vul(x + c[0], g + 1, z + c[1], x + c[0], g + 4, z + c[1], Material.SPRUCE_FENCE);
            for (int dx = 1; dx <= 3; dx++) zet(x + dx, g + 1, z + (z > 0 ? 0 : 2), Material.BARREL);
            for (int dx = 1; dx <= 3; dx++) zet(x + dx, g + 2, z + (z > 0 ? 0 : 2), waar[i]);
            label(x + 2.5, g + 6.4, z + 1.5, "<yellow>" + naam[i] + "</yellow>", 0.7f);
            reserveer(x - 1, z - 1, x + 5, z + 3);
        }
    }

    // ── het dorp: huisjes langs de N-Z-laan ─────────────────────────────────────────────────

    private static final Material[][] STIJL = {
            // muur, hoeken, dak (trap), dak-nok (blok), vloer
            {Material.WHITE_TERRACOTTA, Material.DARK_OAK_LOG, Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS, Material.OAK_PLANKS},
            {Material.BRICKS, Material.SPRUCE_LOG, Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILES, Material.SPRUCE_PLANKS},
            {Material.BIRCH_PLANKS, Material.BIRCH_LOG, Material.MUD_BRICK_STAIRS, Material.MUD_BRICKS, Material.BIRCH_PLANKS},
            {Material.CHERRY_PLANKS, Material.STRIPPED_CHERRY_LOG, Material.CHERRY_STAIRS, Material.CHERRY_PLANKS, Material.CHERRY_PLANKS},
            {Material.LIGHT_BLUE_TERRACOTTA, Material.STRIPPED_OAK_LOG, Material.SPRUCE_STAIRS, Material.SPRUCE_PLANKS, Material.OAK_PLANKS},
            {Material.YELLOW_TERRACOTTA, Material.OAK_LOG, Material.BRICK_STAIRS, Material.BRICKS, Material.JUNGLE_PLANKS},
            {Material.STONE_BRICKS, Material.STRIPPED_SPRUCE_LOG, Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS, Material.DARK_OAK_PLANKS},
            {Material.PINK_TERRACOTTA, Material.STRIPPED_BIRCH_LOG, Material.CHERRY_STAIRS, Material.CHERRY_PLANKS, Material.BIRCH_PLANKS},
            {Material.WHITE_CONCRETE, Material.MANGROVE_LOG, Material.MANGROVE_STAIRS, Material.MANGROVE_PLANKS, Material.MANGROVE_PLANKS},
            {Material.CYAN_TERRACOTTA, Material.STRIPPED_DARK_OAK_LOG, Material.STONE_BRICK_STAIRS, Material.STONE_BRICKS, Material.SPRUCE_PLANKS},
    };

    /** Huisje (9 breed langs de laan, 9 diep) met puntdak, schoorsteen, ramen, voortuin met hekje. sx = kant van de laan. */
    void huisje(int sx, int zc, int nr) {
        Material[] st = STIJL[nr % STIJL.length];
        Material muur = st[0], hoek = st[1], dakTrap = st[2], nok = st[3], vloer = st[4];
        int xa = sx * 9, xb = sx * 17;                       // van de laan af: x 9..17 (of −17..−9)
        int x1 = Math.min(xa, xb), x2 = Math.max(xa, xb), z1 = zc - 4, z2 = zc + 4;
        int y = g + 1;
        BlockFace naarLaan = sx > 0 ? BlockFace.WEST : BlockFace.EAST;
        int voorX = sx > 0 ? x1 : x2;
        vul(x1 - 1, g, z1 - 1, x2 + 1, g, z2 + 1, Material.GRASS_BLOCK);
        vul(x1 - 1, y, z1 - 1, x2 + 1, y + 10, z2 + 1, Material.AIR);
        vul(x1, g, z1, x2, g, z2, Material.COBBLESTONE);
        vul(x1 + 1, g, z1 + 1, x2 - 1, g, z2 - 1, vloer);
        vul(x1, y, z1, x2, y + 3, z2, muur);
        vul(x1 + 1, y, z1 + 1, x2 - 1, y + 3, z2 - 1, Material.AIR);
        for (int[] c : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) vul(c[0], y, c[1], c[0], y + 3, c[1], hoek);
        vul(x1, y + 3, z1, x2, y + 3, z1, hoek);            // balk boven de wanden
        vul(x1, y + 3, z2, x2, y + 3, z2, hoek);
        // dak: nok langs x (dwars op de laan), helling naar noord en zuid
        for (int i = 0; i <= 5; i++) {
            int yy = y + 3 + i;
            int za = z1 - 1 + i, zb = z2 + 1 - i;
            if (za > zb) break;
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                if (za == zb) {
                    zet(x, yy, za, nok);
                } else {
                    zet(x, yy, za, trap(dakTrap, BlockFace.SOUTH));
                    zet(x, yy, zb, trap(dakTrap, BlockFace.NORTH));
                }
            }
            if (i >= 1) {                                    // gevels
                for (int z = za + 1; z <= zb - 1; z++) {
                    zet(x1, yy, z, muur);
                    zet(x2, yy, z, muur);
                }
            }
        }
        // ramen op de gevels en zijkanten
        for (int z = z1 + 2; z <= z2 - 2; z += 2) {
            zet(voorX, y + 1, z, Material.GLASS_PANE);
            zet(voorX == x1 ? x2 : x1, y + 1, z, Material.GLASS_PANE);
        }
        for (int x = x1 + 2; x <= x2 - 2; x += 3) {
            zet(x, y + 1, z1, Material.GLASS_PANE);
            zet(x, y + 2, z1, Material.GLASS_PANE);
            zet(x, y + 1, z2, Material.GLASS_PANE);
            zet(x, y + 2, z2, Material.GLASS_PANE);
            zet(x, y, z1 - 1, blad(Material.FLOWERING_AZALEA_LEAVES));     // bloembakje onder het raam
            zet(x, y, z2 - (-1), blad(Material.FLOWERING_AZALEA_LEAVES));
        }
        zet((x1 + x2) / 2, y + 5, zc, Material.GLASS_PANE);   // rondje raam in de gevel
        // deur naar de laan
        zet(voorX, y + 1, zc, Material.AIR);
        deur(voorX, y, zc, Material.SPRUCE_DOOR, naarLaan);
        zet(voorX - sx, y + 2, zc, Material.LANTERN);
        // schoorsteen met rook
        int sxk = sx > 0 ? x2 - 1 : x1 + 1;
        vul(sxk, y, z1 + 1, sxk, y + 8, z1 + 1, Material.COBBLESTONE);
        Lightable vuur = (Lightable) Material.CAMPFIRE.createBlockData();
        vuur.setLit(true);
        zet(sxk, y + 9, z1 + 1, vuur);
        zet(sxk, y, z1 + 1, Material.FURNACE);
        // binnen
        zet(x1 + 1 + (sx > 0 ? 6 : 0), y, z2 - 1, Material.CRAFTING_TABLE);
        zet(sx > 0 ? x2 - 1 : x1 + 1, y, z2 - 1, Material.BARREL);
        zet(sx > 0 ? x2 - 2 : x1 + 2, y, z2 - 1, Material.BOOKSHELF);
        zet((x1 + x2) / 2, y + 4, zc, Material.IRON_CHAIN);
        zet((x1 + x2) / 2, y + 3, zc, Material.LANTERN);
        Material[] tapijt = {Material.RED_CARPET, Material.BLUE_CARPET, Material.GREEN_CARPET, Material.YELLOW_CARPET, Material.PURPLE_CARPET};
        for (int x = x1 + 2; x <= x2 - 2; x++) for (int z = zc - 1; z <= zc + 1; z++) zet(x, y, z, tapijt[nr % tapijt.length]);
        // voortuin: pad naar de laan, hekje, bloemen
        int padTot = sx > 0 ? 5 : -5;
        for (int x = voorX - sx; sx > 0 ? x >= padTot : x <= padTot; x -= sx) {
            zet(x, g, zc, Material.DIRT_PATH);
            zet(x, g + 1, zc, Material.AIR);
        }
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            if (Math.abs(z - zc) <= 0) continue;
            int hx = sx > 0 ? 6 : -6;
            zet(hx, g + 1, z, Material.BIRCH_FENCE);
            for (int x = Math.min(hx + sx, voorX - sx); x <= Math.max(hx + sx, voorX - sx); x++) {
                if (z != zc && Math.abs(z - zc) > 1) zet(x, g + 1, z, bloem(x, z + nr));
            }
        }
        zet(sx > 0 ? 6 : -6, g + 1, zc, Material.AIR);
        lantaarnpaal(sx > 0 ? 7 : -7, zc + 2);
        reserveer(x1 - 2, z1 - 2, x2 + 2, z2 + 2);
        reserveer(Math.min(5 * sx, voorX), z1 - 1, Math.max(5 * sx, voorX), z2 + 1);
    }

    // ── vijver met brug ─────────────────────────────────────────────────────────────────────

    void vijver(int cx, int cz) {
        int rx = 8, rz = 16;
        for (int dx = -rx - 1; dx <= rx + 1; dx++)
            for (int dz = -rz - 1; dz <= rz + 1; dz++) {
                double e = (dx * dx) / (double) (rx * rx) + (dz * dz) / (double) (rz * rz);
                int x = cx + dx, z = cz + dz;
                if (e <= 1.0) {
                    zet(x, g - 2, z, hash(x, z) % 2 == 0 ? Material.CLAY : Material.SAND);
                    zet(x, g - 1, z, e < 0.6 ? Material.WATER : Material.SAND);
                    zet(x, g, z, Material.WATER);
                    vul(x, g + 1, z, x, g + 2, z, Material.AIR);
                    if (e < 0.6 && hash(x, z) % 9 == 0) zet(x, g + 1, z, Material.LILY_PAD);
                    if (e < 0.6 && hash(z, x) % 13 == 0) zet(x, g - 1, z, Material.SEAGRASS);
                } else if (e <= 1.35) {
                    if (hash(x, z) % 4 == 0) {
                        zet(x, g, z, Material.SAND);
                        zet(x, g + 1, z, Material.SUGAR_CANE);
                        zet(x, g + 2, z, Material.SUGAR_CANE);
                    } else if (hash(x, z) % 4 == 1) zet(x, g + 1, z, Material.TALL_GRASS);
                }
            }
        // houten brug dwars (oost-west) over het midden
        for (int dx = -rx - 1; dx <= rx + 1; dx++) {
            int x = cx + dx;
            boolean eind = Math.abs(dx) == rx + 1;
            for (int dz = -1; dz <= 1; dz++) zet(x, g + 1, cz + dz, eind ? slab(Material.OAK_SLAB, false) : Material.OAK_PLANKS.createBlockData());
            if (eind) continue;
            for (int s2 : new int[]{-2, 2}) {
                zet(x, g + 1, cz + s2, Material.OAK_PLANKS);
                zet(x, g + 2, cz + s2, Math.abs(dx) == rx ? Material.OAK_LOG : Material.OAK_FENCE);
            }
        }
        zet(cx - rx, g + 3, cz - 2, Material.LANTERN);
        zet(cx + rx, g + 3, cz + 2, Material.LANTERN);
        label(cx + 0.5, g + 5, cz + 0.5, "<aqua>Eendenvijver</aqua>", 0.8f);
        boom(cx - rx - 4, cz - 10, TreeType.BIRCH);
        boom(cx + rx + 3, cz + 9, TreeType.TREE);
        reserveer(cx - rx - 2, cz - rz - 2, cx + rx + 2, cz + rz + 2);
    }

    // ── heggendoolhof ───────────────────────────────────────────────────────────────────────

    void doolhof(int x0, int z0) {
        int n = 9, maat = 2 * n + 1;                         // 19 × 19
        boolean[][] open = new boolean[maat][maat];
        Random r = new Random(77);
        java.util.ArrayDeque<int[]> stapel = new java.util.ArrayDeque<>();
        open[1][1] = true;
        stapel.push(new int[]{1, 1});
        int[][] d = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        while (!stapel.isEmpty()) {
            int[] c = stapel.peek();
            List<int[]> buren = new ArrayList<>();
            for (int[] k : d) {
                int nx = c[0] + k[0], nz = c[1] + k[1];
                if (nx > 0 && nz > 0 && nx < maat - 1 && nz < maat - 1 && !open[nx][nz]) buren.add(new int[]{nx, nz, k[0] / 2, k[1] / 2});
            }
            if (buren.isEmpty()) {
                stapel.pop();
                continue;
            }
            int[] b = buren.get(r.nextInt(buren.size()));
            open[c[0] + b[2]][c[1] + b[3]] = true;
            open[b[0]][b[1]] = true;
            stapel.push(new int[]{b[0], b[1]});
        }
        open[0][1] = true;                                   // ingang
        open[maat - 1][maat - 2] = true;                     // uitgang
        for (int i = 0; i < maat; i++)
            for (int j = 0; j < maat; j++) {
                int x = x0 + i, z = z0 + j;
                zet(x, g, z, Material.GRASS_BLOCK);
                if (open[i][j]) {
                    vul(x, g + 1, z, x, g + 3, z, Material.AIR);
                    zet(x, g, z, Material.DIRT_PATH);
                } else {
                    BlockData b = blad(hash(x, z) % 9 == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.OAK_LEAVES);
                    vul(x, g + 1, z, x, g + 3, z, b);
                }
            }
        int mx = x0 + (n % 2 == 0 ? n + 1 : n), mz = z0 + (n % 2 == 0 ? n + 1 : n);
        zet(mx, g + 1, mz, Material.GOLD_BLOCK);
        label(mx + 0.5, g + 2.8, mz + 0.5, "<gold>★ Midden van het doolhof ★</gold>", 0.6f);
        label(x0 - 0.5, g + 4.6, z0 + 1.5, "<green><bold>Heggendoolhof</bold></green>\n<gray>vind het gouden blok</gray>", 1f);
        lantaarnpaal(x0 - 1, z0 + 3);
        lantaarnpaal(x0 + maat, z0 + maat - 4);
        reserveer(x0 - 2, z0 - 2, x0 + maat + 1, z0 + maat + 1);
    }

    // ── prieel ──────────────────────────────────────────────────────────────────────────────

    void prieel(int cx, int cz) {
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 6) continue;
                zet(cx + dx, g, cz + dz, (Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? Material.SMOOTH_STONE : Material.STONE_BRICKS);
                vul(cx + dx, g + 1, cz + dz, cx + dx, g + 6, cz + dz, Material.AIR);
            }
        int[][] palen = {{-3, -2}, {-3, 2}, {3, -2}, {3, 2}, {-2, -3}, {2, -3}, {-2, 3}, {2, 3}};
        for (int[] p : palen) {
            vul(cx + p[0], g + 1, cz + p[1], cx + p[0], g + 3, cz + p[1], Material.BIRCH_FENCE);
            zet(cx + p[0], g + 4, cz + p[1], Material.BIRCH_PLANKS);
        }
        for (int lvl = 0; lvl < 3; lvl++) {
            int rr = 4 - lvl;
            for (int dx = -rr; dx <= rr; dx++)
                for (int dz = -rr; dz <= rr; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > rr + 2) continue;
                    if (lvl == 0 && Math.abs(dx) + Math.abs(dz) < rr + 1 && Math.abs(dx) < rr && Math.abs(dz) < rr) continue;
                    zet(cx + dx, g + 5 + lvl, cz + dz, blad(lvl == 2 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
                }
        }
        zet(cx, g + 4, cz, Material.IRON_CHAIN);
        zet(cx, g + 3, cz, Material.LANTERN);
        zet(cx - 2, g + 1, cz, trap(Material.BIRCH_STAIRS, BlockFace.WEST));
        zet(cx + 2, g + 1, cz, trap(Material.BIRCH_STAIRS, BlockFace.EAST));
        zet(cx, g + 1, cz - 2, trap(Material.BIRCH_STAIRS, BlockFace.NORTH));
        label(cx + 0.5, g + 8.8, cz + 0.5, "<light_purple>Prieel</light_purple>", 0.8f);
        reserveer(cx - 5, cz - 5, cx + 5, cz + 5);
    }

    // ── boomgaard met bijen en pompoenen ───────────────────────────────────────────────────

    void boomgaard(int x0, int z0) {
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 6; j++) {
                int x = x0 + 2 + i * 6, z = z0 + j * 11;
                boom(x, z, (i + j) % 3 == 0 ? TreeType.BIRCH : TreeType.TREE);
                reserveer(x - 1, z - 1, x + 1, z + 1);
                if ((i + j) % 2 == 0) {
                    zet(x + 3, g + 1, z + 4, richting(Material.BEEHIVE, BlockFace.SOUTH));
                    zet(x + 3, g + 1, z + 5, Material.SUNFLOWER);
                    zet(x + 3, g + 2, z + 5, sunTop());
                } else {
                    zet(x + 3, g + 1, z + 4, Material.PUMPKIN);
                    zet(x + 2, g + 1, z + 5, richting(Material.CARVED_PUMPKIN, BlockFace.WEST));
                }
            }
        // vogelverschrikker
        int vx = x0 + 9, vz = z0 + 30;
        zet(vx, g + 1, vz, Material.OAK_FENCE);
        zet(vx, g + 2, vz, Material.HAY_BLOCK);
        zet(vx, g + 3, vz, richting(Material.CARVED_PUMPKIN, BlockFace.WEST));
        zet(vx - 1, g + 2, vz, Material.OAK_FENCE);
        zet(vx + 1, g + 2, vz, Material.OAK_FENCE);
        for (int k = 0; k < 4; k++) zet(x0 + 15 + k % 2, g + 1, z0 + 58 + k / 2, Material.HAY_BLOCK);
        label(x0 + 9.5, g + 6, z0 - 3.5, "<gold>Boomgaard</gold>", 0.9f);
        reserveer(x0, z0 - 2, x0 + 18, z0 + 62);
    }

    private BlockData sunTop() {
        BlockData d = Material.SUNFLOWER.createBlockData();
        if (d instanceof Bisected b) b.setHalf(Bisected.Half.TOP);
        return d;
    }

    // ── picknickweide met bloemenspiraal ───────────────────────────────────────────────────

    void picknick(int cx, int cz) {
        // dekens (rood-wit geblokt) met manden
        int[][] dekens = {{cx - 6, cz}, {cx + 2, cz + 10}, {cx - 4, cz + 22}};
        for (int[] d : dekens) {
            for (int dx = 0; dx < 4; dx++)
                for (int dz = 0; dz < 4; dz++) zet(d[0] + dx, g + 1, d[1] + dz, (dx + dz) % 2 == 0 ? Material.RED_CARPET : Material.WHITE_CARPET);
            zet(d[0] + 1, g + 1, d[1] + 1, Material.BARREL);
            zet(d[0] + 2, g + 1, d[1] + 2, Material.CAKE);
            reserveer(d[0] - 1, d[1] - 1, d[0] + 4, d[1] + 4);
        }
        // picknicktafels
        for (int k = 0; k < 2; k++) {
            int tx = cx + 6, tz = cz + 2 + k * 9;
            vul(tx, g + 1, tz, tx + 2, g + 1, tz, slab(Material.SPRUCE_SLAB, true));
            zet(tx, g + 1, tz - 1, trap(Material.SPRUCE_STAIRS, BlockFace.NORTH));
            zet(tx + 2, g + 1, tz - 1, trap(Material.SPRUCE_STAIRS, BlockFace.NORTH));
            zet(tx, g + 1, tz + 1, trap(Material.SPRUCE_STAIRS, BlockFace.SOUTH));
            zet(tx + 2, g + 1, tz + 1, trap(Material.SPRUCE_STAIRS, BlockFace.SOUTH));
            reserveer(tx - 1, tz - 2, tx + 3, tz + 2);
        }
        // bloemenspiraal
        int sx = cx, sz = cz + 42;
        for (double a = 0; a < Math.PI * 5; a += 0.08) {
            double r = 1 + a * 0.55;
            int x = sx + (int) Math.round(Math.cos(a) * r), z = sz + (int) Math.round(Math.sin(a) * r);
            zet(x, g + 1, z, BLOEMEN[(int) (a / (Math.PI / 2)) % BLOEMEN.length]);
        }
        zet(sx, g + 1, sz, Material.BELL);
        boom(cx - 10, cz + 14, TreeType.BIG_TREE);
        reserveer(cx - 13, cz + 11, cx - 7, cz + 17);
        label(cx + 0.5, g + 4, cz - 3.5, "<yellow>Picknickweide</yellow>", 0.9f);
        reserveer(sx - 11, sz - 11, sx + 11, sz + 11);
    }

    // ── tulpenvelden langs de oost-westlaan ────────────────────────────────────────────────

    void tulpen() {
        Material[] kleur = {Material.RED_TULIP, Material.ORANGE_TULIP, Material.PINK_TULIP, Material.WHITE_TULIP, Material.ALLIUM, Material.CORNFLOWER};
        for (int sx : new int[]{1, -1})
            for (int sz : new int[]{1, -1}) {
                int xa = sx * 48, xb = sx * 120;
                int za = sz * 8, zb = sz * 34;
                for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++)
                    for (int z = Math.min(za, zb); z <= Math.max(za, zb); z++) {
                        if (!vrij(x, z, 0)) continue;
                        int baan = Math.floorMod(z, 4);
                        if (baan == 0) {
                            zet(x, g, z, Material.DIRT_PATH);           // paadje tussen de banen
                        } else {
                            zet(x, g, z, Material.GRASS_BLOCK);
                            int band = Math.floorDiv(z - za * 0, 4);
                            zet(x, g + 1, z, kleur[Math.floorMod(band + (sx > 0 ? 0 : 3), kleur.length)]);
                        }
                    }
            }
    }

    // ── molen ───────────────────────────────────────────────────────────────────────────────

    void molen(int cx, int cz) {
        int h = 15;
        for (int y = 0; y <= h; y++) {
            double r = 4.6 - y * 0.12;
            for (int dx = -5; dx <= 5; dx++)
                for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    int x = cx + dx, z = cz + dz;
                    if (y == 0 && d <= r + 1.2) {
                        zet(x, g, z, Material.STONE_BRICKS);
                        vul(x, g + 1, z, x, g + h + 6, z, Material.AIR);
                    }
                    if (d <= r && d > r - 1.1) zet(x, g + 1 + y, z, y < 5 ? Material.STONE_BRICKS : (y % 4 == 0 ? Material.DARK_OAK_LOG : Material.DARK_OAK_PLANKS));
                    if (y == 6 && d <= r + 1.0 && d > r) zet(x, g + 1 + y, z, slab(Material.SPRUCE_SLAB, true));    // omloop
                }
        }
        for (int y = 0; y < 4; y++) {                      // kap
            int rr = 3 - y;
            for (int dx = -rr; dx <= rr; dx++)
                for (int dz = -rr - 1; dz <= rr; dz++) zet(cx + dx, g + h + 2 + y, cz + dz, Material.DARK_OAK_PLANKS);
        }
        deur(cx, g + 1, cz + 4, Material.DARK_OAK_DOOR, BlockFace.SOUTH);
        // wieken (X) op de zuidkant
        int hx = cx, hy = g + h - 1, hz = cz + 5;
        zet(hx, hy, hz, Material.DARK_OAK_LOG);
        zet(hx, hy, hz - 1, Material.DARK_OAK_LOG);
        for (int k = 1; k <= 9; k++) {
            for (int[] s : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                int x = hx + s[0] * k, y = hy + s[1] * k;
                if (y <= g + 1) continue;
                zet(x, y, hz, Material.DARK_OAK_FENCE);
                if (k >= 3) zet(x + s[0], y, hz, Material.WHITE_WOOL);
            }
        }
        label(cx + 0.5, g + h + 8, cz + 0.5, "<white><bold>De Molen</bold></white>", 1.2f);
        reserveer(cx - 11, cz - 6, cx + 11, cz + 7);
    }

    // ── kas ─────────────────────────────────────────────────────────────────────────────────

    void kas(int cx, int cz) {
        int x1 = cx - 8, x2 = cx + 8, z1 = cz - 5, z2 = cz + 5;
        vul(x1, g, z1, x2, g, z2, Material.STONE_BRICKS);
        vul(x1, g + 1, z1, x2, g + 8, z2, Material.AIR);
        vul(x1, g + 1, z1, x2, g + 4, z2, Material.GLASS);
        vul(x1 + 1, g + 1, z1 + 1, x2 - 1, g + 4, z2 - 1, Material.AIR);
        for (int x = x1; x <= x2; x += 4) {
            vul(x, g + 1, z1, x, g + 4, z1, Material.WHITE_CONCRETE);
            vul(x, g + 1, z2, x, g + 4, z2, Material.WHITE_CONCRETE);
        }
        for (int i = 0; i <= 5; i++) {
            for (int x = x1; x <= x2; x++) {
                if (z1 + i > z2 - i) break;
                Material m = (x - x1) % 4 == 0 ? Material.WHITE_CONCRETE : Material.GLASS;
                zet(x, g + 5 + i, z1 + i, m);
                zet(x, g + 5 + i, z2 - i, m);
            }
            for (int z = z1 + i + 1; z <= z2 - i - 1; z++) {
                zet(x1, g + 5 + i, z, Material.GLASS);
                zet(x2, g + 5 + i, z, Material.GLASS);
            }
        }
        for (int x = x1 + 1; x <= x2 - 1; x++)
            for (int z = z1 + 1; z <= z2 - 1; z++) {
                if (z == cz) {
                    zet(x, g, z, Material.MOSSY_COBBLESTONE);
                    continue;
                }
                zet(x, g, z, Material.MOSS_BLOCK);
                long hh = hash(x, z) % 6;
                if (hh == 0) zet(x, g + 1, z, blad(Material.FLOWERING_AZALEA_LEAVES));
                else if (hh == 1) zet(x, g + 1, z, Material.FERN);
                else zet(x, g + 1, z, bloem(x, z));
            }
        zet(x1, g + 1, cz, Material.AIR);
        zet(x1, g + 2, cz, Material.AIR);
        zet(x2, g + 1, cz, Material.AIR);
        zet(x2, g + 2, cz, Material.AIR);
        for (int x = x1 + 2; x <= x2 - 2; x += 4) {
            zet(x, g + 9, cz, Material.IRON_CHAIN);
            zet(x, g + 8, cz, Material.LANTERN);
        }
        label(cx + 0.5, g + 12, cz + 0.5, "<green><bold>De Kas</bold></green>", 1.1f);
        reserveer(x1 - 2, z1 - 2, x2 + 2, z2 + 2);
    }

    // ── uitkijktoren en bloemenhart ────────────────────────────────────────────────────────

    void uitkijk(int cx, int cz) {
        for (int y = 1; y <= 10; y++)
            for (int[] c : new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) zet(cx + c[0], g + y, cz + c[1], Material.SPRUCE_LOG);
        vul(cx - 3, g + 11, cz - 3, cx + 3, g + 11, cz + 3, Material.SPRUCE_PLANKS);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3) zet(cx + dx, g + 12, cz + dz, Material.SPRUCE_FENCE);
        for (int y = 1; y <= 10; y++) {                     // ladder aan de westkant van de oostelijke paal
            zet(cx + 1, g + y, cz + 2, richting(Material.LADDER, BlockFace.WEST));
        }
        zet(cx + 1, g + 11, cz + 2, Material.AIR);
        for (int i = 0; i < 2; i++) {
            int rr = 3 - i;
            for (int dx = -rr; dx <= rr; dx++)
                for (int dz = -rr; dz <= rr; dz++) zet(cx + dx, g + 15 + i, cz + dz, Material.SPRUCE_SLAB);
        }
        for (int[] c : new int[][]{{-3, -3}, {-3, 3}, {3, -3}, {3, 3}}) vul(cx + c[0], g + 13, cz + c[1], cx + c[0], g + 14, cz + c[1], Material.SPRUCE_FENCE);
        zet(cx, g + 14, cz, Material.LANTERN);
        label(cx + 0.5, g + 18, cz + 0.5, "<yellow>Uitkijktoren</yellow>\n<gray>klim de ladder op</gray>", 1f);
        reserveer(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    void bloemenhart(int cx, int cz) {
        // groot hart van rode tulpen met een rand van witte, en een bankje ervoor
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                double x = dx / 8.0, y = -dz / 8.0;
                double f = Math.pow(x * x + y * y - 1, 3) - x * x * y * y * y;
                if (f <= 0) {
                    double f2 = Math.pow((x * 1.15) * (x * 1.15) + (y * 1.15) * (y * 1.15) - 1, 3) - Math.pow(x * 1.15, 2) * Math.pow(y * 1.15, 3);
                    zet(cx + dx, g, cz + dz, Material.GRASS_BLOCK);
                    zet(cx + dx, g + 1, cz + dz, f2 <= 0 ? Material.RED_TULIP : Material.WHITE_TULIP);
                }
            }
        zet(cx, g + 1, cz - 11, trap(Material.OAK_STAIRS, BlockFace.NORTH));
        zet(cx + 1, g + 1, cz - 11, trap(Material.OAK_STAIRS, BlockFace.NORTH));
        label(cx + 0.5, g + 3, cz - 12.5, "<red>❤ Bloemenhart ❤</red>", 0.9f);
        reserveer(cx - 11, cz - 12, cx + 11, cz + 11);
    }

    // ── parkourlanen (de levels zelf bouwt LobbyMelding) ───────────────────────────────────

    /** Lanen langs x = ±40..42 met een poortje per level-ingang; z = ingangen van de parkourhuizen. */
    void parkourlanen() {
        for (int sx : new int[]{1, -1}) {
            for (int z = -RAND + 6; z <= 86; z++) {
                for (int d = -1; d <= 1; d++) zet(sx * (PARKOURLAAN + d), g, z, Math.abs(z % 7) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                if (Math.floorMod(z, 10) == 5 && Math.abs(z) > 6) lantaarnpaal(sx * (PARKOURLAAN - 2), z);
            }
            reserveer(sx * (PARKOURLAAN - 2), -RAND + 6, sx * (PARKOURLAAN + 2), 86);
        }
    }

    /** Pergola-poortje over het pad van de parkourlaan naar een levelhuis, met bord. */
    void levelPoortje(int sx, int zc, int level, String naam, String moeite, String kleur) {
        int xa = sx * (PARKOURLAAN + 2), xb = sx * 45;
        for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++)
            for (int d = -1; d <= 1; d++) {
                zet(x, g, zc + d, Material.DIRT_PATH);
                vul(x, g + 1, zc + d, x, g + 4, zc + d, Material.AIR);
            }
        int px = sx * 43;
        for (int s : new int[]{-2, 2}) vul(px, g + 1, zc + s, px, g + 3, zc + s, Material.OAK_FENCE);
        for (int d = -2; d <= 2; d++) zet(px, g + 4, zc + d, blad(Material.FLOWERING_AZALEA_LEAVES));
        zet(px, g + 5, zc, Material.valueOf(kleur + "_WOOL"));
        label(sx * 42 + 0.5, g + 6.6, zc + 0.5, "<gold><bold>Parkour " + level + "</bold></gold>\n<white>" + naam + "</white> <yellow>" + moeite + "</yellow>", 1.0f);
        reserveer(Math.min(xa, xb), zc - 3, Math.max(xa, xb), zc + 3);
    }

    // ── bomen in de overgebleven ruimte ────────────────────────────────────────────────────

    void bomen() {
        TreeType[] soorten = {TreeType.TREE, TreeType.BIRCH, TreeType.CHERRY, TreeType.TREE, TreeType.BIG_TREE, TreeType.AZALEA};
        for (int x = -RAND + 4; x <= RAND - 4; x += 7)
            for (int z = -RAND + 4; z <= RAND - 4; z += 7) {
                int jx = x + (int) (hash(x, z) % 5) - 2, jz = z + (int) (hash(z, x) % 5) - 2;
                double r = Math.sqrt(jx * jx + jz * jz);
                if (r < GRACHT_UIT + 4) continue;
                if (!vrij(jx, jz, 3)) continue;
                if (hash(jx, jz) % 3 == 0) continue;
                if (plaats && !type(jx, g + 1, jz).isAir() && !Tag.isPlant(type(jx, g + 1, jz))) continue;
                boom(jx, jz, soorten[(int) (hash(jx * 3, jz) % soorten.length)]);
                if (hash(jz, jx) % 2 == 0) {
                    zet(jx + 2, g + 1, jz + 1, bloem(jx, jz));
                    zet(jx - 1, g + 1, jz - 2, Material.SHORT_GRASS);
                }
            }
    }
}
