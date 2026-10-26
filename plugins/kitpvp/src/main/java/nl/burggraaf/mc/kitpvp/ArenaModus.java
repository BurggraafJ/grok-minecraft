package nl.burggraaf.mc.kitpvp;

import java.util.List;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;

/**
 * PvP Arena: het colosseum (rond, straal 30, midden 0/0) met ruïnes en een middenplateau, en daaronder de oefenarena
 * met bots (midden 0/{@link #BZ}, straal {@link #RB}). Twee kitruimtes: «arena» boven de noordmuur van het colosseum
 * en «oefen» boven de noordmuur van de oefenarena; een zijdeur verbindt ze.
 */
final class ArenaModus extends KitModus {

    static final int R = 30;                    // colosseum: straal binnenkant muur
    static final int BZ = 110, RB = 18;         // oefenarena: midden (0, BZ), straal

    final Arena pvp = new Arena("pvp", "arena", "PvP Arena", "pvp");
    final Arena bots = new Arena("bots", "arena", "Oefenarena", "bots");

    private final Kitruimte ruimte = new Kitruimte("arena", "arena", "pvp", "PvP Arena", 0, -35, BlockFace.SOUTH, 9,
            new Kitruimte.Palet(Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS,
                    Material.POLISHED_DEEPSLATE, Material.SHROOMLIGHT, Material.RED_CARPET, Material.CHISELED_POLISHED_BLACKSTONE),
            "oefen", "Oefenarena");
    private final Kitruimte oefen = new Kitruimte("oefen", "arena", "bots", "Oefenarena", 0, 87, BlockFace.SOUTH, 7,
            new Kitruimte.Palet(Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.SANDSTONE,
                    Material.CHISELED_SANDSTONE, Material.OCHRE_FROGLIGHT, Material.YELLOW_CARPET, Material.CHISELED_RED_SANDSTONE),
            "arena", "PvP Arena");

    ArenaModus(KitPvP plugin) {
        super(plugin);
    }

    @Override public String id() { return "arena"; }
    @Override public String naam() { return "PvP Arena"; }
    @Override public TextColor kleur() { return TextColor.color(0xe06666); }
    @Override public Material icoon() { return Material.IRON_SWORD; }
    @Override public String omschrijving() { return "het colosseum · oefenen tegen bots"; }

    @Override
    public PoortStijl poort() {
        return new PoortStijl("zuid", Material.POLISHED_BLACKSTONE_BRICKS, Material.RED_NETHER_BRICKS, Material.SHROOMLIGHT,
                Material.POLISHED_BLACKSTONE, Material.RED_WALL_BANNER, Material.RED_WALL_BANNER, Color.fromRGB(0xd04848));
    }

    @Override public List<Arena> arenas() { return List.of(pvp, bots); }
    @Override public List<Kitruimte> kitruimtes() { return List.of(ruimte, oefen); }

    @Override
    public List<Deel> delen() {
        return List.of(new Deel("arena", KitPvP.BOUW_VERSIE, b -> {
            colosseum(b);
            oefenarena(b);
            ruimte.bouw(b, plugin);
            oefen.bouw(b, plugin);
        }));
    }

    @Override
    public void zelftest(List<String> fouten, List<String> info) {
        if (pvp.spawns.size() != 8) fouten.add("arena: " + pvp.spawns.size() + " spawns in het colosseum (verwacht 8)");
        if (bots.spawns.size() != 6) fouten.add("arena: " + bots.spawns.size() + " spawns in de oefenarena (verwacht 6)");
    }

    // ── colosseum ────────────────────────────────────────────────────────────────────────────

    private void colosseum(Bouwer b) {
        int g = b.g;
        if (b.plaats) {
            b.vul(-50, g + 1, -60, 50, g + 30, 50, Material.AIR);
            b.vul(-50, g, -60, 50, g, 50, Material.GRASS_BLOCK);
        }
        Material[] vloer = {Material.STONE_BRICKS, Material.COBBLESTONE, Material.MOSSY_STONE_BRICKS, Material.ANDESITE,
                Material.GRAVEL, Material.COARSE_DIRT, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.MOSS_BLOCK, Material.PACKED_MUD};
        for (int x = -R - 4; x <= R + 4; x++)
            for (int z = -R - 4; z <= R + 4; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > R + 3.5) continue;
                if (r <= R + 0.5) {
                    b.zet(x, g, z, vloer[(int) (Bouwer.hash(x / 3, z / 3) % vloer.length)]);
                    b.zet(x, g - 1, z, Material.STONE);
                } else {                                   // muur: 7 hoog, met kantelen
                    b.vul(x, g - 1, z, x, g + 6, z, (Bouwer.hash(x, z) % 5 == 0) ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                    if (r <= R + 1.5) b.zet(x, g + 3, z, (Bouwer.hash(x, z) % 9 == 0) ? Material.SEA_LANTERN : Material.CHISELED_STONE_BRICKS);
                    if (r > R + 2.5 && (Math.floorMod((int) Math.round(Math.atan2(z, x) * 30), 2) == 0)) b.zet(x, g + 7, z, Material.STONE_BRICK_WALL);
                }
            }
        for (int i = 0; i < 12; i++) {                     // pilaren met lantaarns
            double a = Math.PI * 2 / 12 * i;
            int x = (int) Math.round(Math.cos(a) * (R + 2)), z = (int) Math.round(Math.sin(a) * (R + 2));
            b.vul(x, g + 7, z, x, g + 9, z, Material.POLISHED_ANDESITE);
            b.zet(x, g + 10, z, Material.LANTERN);
        }
        for (int x = -5; x <= 5; x++)                      // middenplateau (straal 5, 2 hoog) met trappen
            for (int z = -5; z <= 5; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 5.4) continue;
                b.zet(x, g + 1, z, Material.STONE_BRICKS);
                if (r <= 4.4) b.zet(x, g + 2, z, (x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE);
                else b.zet(x, g + 2, z, (Slab) Material.STONE_BRICK_SLAB.createBlockData());
            }
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            for (int i = -1; i <= 1; i++) {
                int dx = f.getModX(), dz = f.getModZ();
                b.zet(dx * 6 + (dz != 0 ? i : 0), g + 1, dz * 6 + (dx != 0 ? i : 0), Bouwer.trap(Material.STONE_BRICK_STAIRS, f.getOppositeFace()));
                b.zet(dx * 5 + (dz != 0 ? i : 0), g + 2, dz * 5 + (dx != 0 ? i : 0), Bouwer.trap(Material.STONE_BRICK_STAIRS, f.getOppositeFace()));
            }
        }
        b.zet(0, g + 3, 0, Material.LODESTONE);
        for (int i = 0; i < 8; i++) {                      // ruïnes op straal 18
            double a = Math.PI * 2 / 8 * i + Math.PI / 8;
            int cx = (int) Math.round(Math.cos(a) * 18), cz = (int) Math.round(Math.sin(a) * 18);
            boolean langsX = i % 2 == 0;
            for (int k = -3; k <= 3; k++) {
                int x = cx + (langsX ? k : 0), z = cz + (langsX ? 0 : k);
                int h = 1 + (int) (Bouwer.hash(x, z) % 3);
                b.vul(x, g + 1, z, x, g + h, z, Bouwer.hash(x + 1, z) % 3 == 0 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
            }
            int ex = cx + (langsX ? 3 : 1), ez = cz + (langsX ? 1 : 3);
            b.vul(ex, g + 1, ez, ex, g + 2, ez, Material.MOSSY_STONE_BRICKS);
            b.zet(cx, g + 4, cz, Material.MOSSY_STONE_BRICK_SLAB);
        }
        for (int i = 0; i < 4; i++) {                      // struiken en hooi
            double a = Math.PI / 2 * i + Math.PI / 4;
            int cx = (int) Math.round(Math.cos(a) * 11), cz = (int) Math.round(Math.sin(a) * 11);
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++)
                    for (int dy = 1; dy <= 2; dy++) b.zet(cx + dx, g + dy, cz + dz, Bouwer.blad(dy == 2 && dx == 1 ? Material.AZALEA_LEAVES : Material.OAK_LEAVES));
            int hx = (int) Math.round(Math.cos(a + Math.PI / 4) * 25), hz = (int) Math.round(Math.sin(a + Math.PI / 4) * 25);
            b.vul(hx, g + 1, hz, hx + 1, g + 1, hz, Material.HAY_BLOCK);
            b.zet(hx, g + 2, hz, Material.HAY_BLOCK);
        }
        pvp.spawns.clear();                                // spawns op straal 24, kijkend naar het midden
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 / 8 * i;
            double x = Math.cos(a) * 24 + 0.5, z = Math.sin(a) * 24 + 0.5;
            Location l = new Location(b.w, x, g + 1, z);
            l.setDirection(new org.bukkit.util.Vector(-x, 0, -z));
            b.vul(l.getBlockX(), g + 1, l.getBlockZ(), l.getBlockX(), g + 3, l.getBlockZ(), Material.AIR);
            pvp.spawns.add(l);
        }
    }

    // ── oefenarena ───────────────────────────────────────────────────────────────────────────

    private void oefenarena(Bouwer b) {
        int g = b.g;
        if (b.plaats) {
            b.vul(-RB - 6, g + 1, 51, RB + 6, g + 20, BZ + RB + 6, Material.AIR);   // ook de kitruimte erboven
            b.vul(-RB - 6, g, 51, RB + 6, g, BZ + RB + 6, Material.GRASS_BLOCK);
        }
        for (int x = -RB - 3; x <= RB + 3; x++)
            for (int z = -RB - 3; z <= RB + 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > RB + 2.5) continue;
                if (r <= RB + 0.5) {
                    b.zet(x, g, BZ + z, Bouwer.hash(x / 2, z / 2) % 4 == 0 ? Material.COARSE_DIRT : Material.SMOOTH_SANDSTONE);
                    b.zet(x, g - 1, BZ + z, Material.STONE);
                } else {
                    b.vul(x, g - 1, BZ + z, x, g + 5, BZ + z, Bouwer.hash(x, z) % 6 == 0 ? Material.CUT_SANDSTONE : Material.SANDSTONE);
                    if (r > RB + 1.5 && Bouwer.hash(x, z) % 7 == 0) b.zet(x, g + 6, BZ + z, Material.LANTERN);
                }
            }
        for (int i = 0; i < 6; i++) {                      // dekking: lage muurtjes
            double a = Math.PI * 2 / 6 * i;
            int cx = (int) Math.round(Math.cos(a) * 9), cz = (int) Math.round(Math.sin(a) * 9);
            for (int k = -1; k <= 1; k++) {
                int x = cx + (i % 2 == 0 ? k : 0), z = BZ + cz + (i % 2 == 0 ? 0 : k);
                b.vul(x, g + 1, z, x, g + 2, z, Material.SANDSTONE_WALL);
            }
        }
        b.zet(0, g + 1, BZ, Material.CAMPFIRE);
        bots.spawns.clear();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 / 6 * i + Math.PI / 6;
            double x = Math.cos(a) * 14 + 0.5, z = Math.sin(a) * 14 + 0.5;
            Location l = new Location(b.w, x, g + 1, BZ + z);
            l.setDirection(new org.bukkit.util.Vector(-x, 0, -z));
            b.vul(l.getBlockX(), g + 1, l.getBlockZ(), l.getBlockX(), g + 3, l.getBlockZ(), Material.AIR);
            bots.spawns.add(l);
        }
    }

    /** Midden en straal van de oefenarena, voor de bots. */
    static boolean inOefenarena(Location l, int g) {
        double dx = l.getX(), dz = l.getZ() - BZ;
        return dx * dx + dz * dz <= (RB - 1) * (RB - 1) && l.getY() >= g - 2;
    }
}
