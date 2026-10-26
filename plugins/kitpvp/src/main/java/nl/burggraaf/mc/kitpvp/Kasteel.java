package nl.burggraaf.mc.kitpvp;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

/**
 * Kasteelarena (PvP Castle), vooral voor bogen. Het speelveld is gelijk gebleven: twee kasteelmuren tegenover elkaar
 * (noord rood, zuid blauw; 41 lang, 6 hoog, 3 dik) met kantelen en een halve borstwering op de weergang,
 * schietgaten, een poort in het midden en trappen aan de achterkant; ertussen een open middenveld met weinig dekking.
 * Achter elke muur een binnenplaats; buitenmuren van 11 hoog (met kantelen erop) houden je binnen.
 *
 * <p>Versie 4 (hub-update): gemengde stenen (stone bricks, mossy, cracked, deepslate-voet en -banden), ronde
 * hoektorens met kantelen, schietsleuven en een vlag (rood noord, blauw zuid), banieren aan de veldkant, fakkels en
 * lantaarns, een gracht rondom met een ophaalbrug naar een dichte poort, en terrein met sparren en keien.
 *
 * <pre>
 *   z = kz-22  buitenmuur          z = kz+22       torens op (±25, kz±23), gracht 3..7 buiten de buitenmuur
 *   z = kz-21..-17 binnenplaats (trappen tegen de muur)
 *   z = kz-16..-14 muur noord · z = kz-13..13 middenveld · z = kz+14..16 muur zuid
 * </pre>
 */
final class Kasteel {

    private final Bouwer b;
    private final int g, kz;

    private Kasteel(Bouwer b, int kz) {
        this.b = b;
        this.g = b.g;
        this.kz = kz;
    }

    static void bouw(Bouwer b, int kz, List<Location> spawns) {
        new Kasteel(b, kz).bouwHet(spawns);
    }

    /** Gemengde steen voor muren en torens. */
    private Material steen(int x, int y, int z) {
        if (y <= g + 1) return Bouwer.hash(x, y, z) % 3 == 0 ? Material.COBBLED_DEEPSLATE : Material.DEEPSLATE_BRICKS;
        long h = Bouwer.hash(x, y, z) % 100;
        if (h < 58) return Material.STONE_BRICKS;
        if (h < 74) return Material.MOSSY_STONE_BRICKS;
        if (h < 88) return Material.CRACKED_STONE_BRICKS;
        if (h < 94) return Material.ANDESITE;
        return Material.DEEPSLATE_BRICKS;
    }

    private void muurkolom(int x, int z, int y1, int y2) {
        for (int y = y1; y <= y2; y++) b.zet(x, y, z, steen(x, y, z));
    }

    /** Afstand buiten de buitenmuur (afgeronde hoeken); ≤ 0 = binnen. */
    private double buiten(int x, int z) {
        double ax = Math.abs(x) - 24, az = Math.abs(z - kz) - 22;
        if (ax > 0 && az > 0) return Math.sqrt(ax * ax + az * az);
        return Math.max(ax, az);
    }

    private void bouwHet(List<Location> spawns) {
        if (b.plaats) {
            b.vul(-42, g + 1, kz - 38, 42, g + 24, kz + 56, Material.AIR);
            b.vul(-42, g - 2, kz - 38, 42, g - 1, kz + 56, Material.DIRT);
            b.vul(-42, g, kz - 38, 42, g, kz + 56, Material.GRASS_BLOCK);
            omgeving();
            // middenveld: gras met een pad van grof zand, wat gras en bloemen (geen dekking), twee lage muurtjes en hooi
            for (int x = -23; x <= 23; x++)
                for (int z = kz - 13; z <= kz + 13; z++) {
                    if (Math.abs(x) <= 1 || Bouwer.hash(x / 2, z / 2) % 9 == 0) b.zet(x, g, z, Material.COARSE_DIRT);
                    else if (Bouwer.hash(x, z) % 6 == 0) b.zet(x, g + 1, z, Material.SHORT_GRASS);
                    else if (Bouwer.hash(x, z) % 23 == 0) b.zet(x, g + 1, z, Bouwer.hash(z, x) % 2 == 0 ? Material.POPPY : Material.CORNFLOWER);
                }
            for (int sx : new int[]{-1, 1}) {
                for (int x = 7; x <= 9; x++) b.zet(sx * x, g + 1, kz, Material.MOSSY_STONE_BRICKS);
                b.zet(sx * 15, g + 1, kz - 5, Material.HAY_BLOCK);
                b.zet(sx * 15, g + 1, kz + 5, Material.HAY_BLOCK);
                b.zet(sx * 15, g + 2, kz + 5, Material.HAY_BLOCK);
            }
            // buitenmuren (hoog genoeg dat ook de ninja er niet overheen springt), kantelen en lantaarns erop
            for (int x = -24; x <= 24; x++)
                for (int z : new int[]{kz - 22, kz + 22}) buitenmuur(x, z, x);
            for (int z = kz - 22; z <= kz + 22; z++)
                for (int x : new int[]{-24, 24}) buitenmuur(x, z, z);
            for (int k : new int[]{-1, 1}) binnenplaats(k);
        }
        for (int k : new int[]{-1, 1}) muur(k, spawns);
        if (b.plaats) {
            for (int sx : new int[]{-1, 1})
                for (int sz : new int[]{-1, 1}) toren(sx * 25, kz + sz * 23, sz < 0 ? Material.RED_WALL_BANNER : Material.BLUE_WALL_BANNER, sx, sz);
            ophaalbrug();
        }
    }

    private void buitenmuur(int x, int z, int langs) {
        b.zet(x, g, z, Material.COBBLED_DEEPSLATE);
        muurkolom(x, z, g + 1, g + 11);
        if (Math.floorMod(langs, 2) == 0) b.zet(x, g + 12, z, steen(x, g + 12, z));
        if (Math.floorMod(langs, 8) == 4) b.zet(x, g + 13, z, Material.LANTERN);
    }

    /** Gracht, oevers, gras, sparren en keien rond het kasteel. */
    private void omgeving() {
        for (int x = -42; x <= 42; x++)
            for (int z = kz - 38; z <= kz + 56; z++) {
                double d = buiten(x, z);
                if (d <= 0) continue;
                long h = Bouwer.hash(x, z);
                if (d > 2.5 && d <= 7.5) {                                   // gracht
                    b.zet(x, g - 2, z, h % 3 == 0 ? Material.CLAY : Material.GRAVEL);
                    b.zet(x, g - 1, z, Material.WATER);
                    b.zet(x, g, z, d > 3.5 && d <= 6.5 && h % 29 == 0 ? Material.LILY_PAD : Material.AIR);
                } else if (d > 7.5 && d <= 8.5) {                            // oever
                    b.zet(x, g, z, h % 3 == 0 ? Material.GRAVEL : Material.COARSE_DIRT);
                } else if (d <= 2.5) {                                       // strook langs de muur
                    b.zet(x, g, z, h % 3 == 0 ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
                } else {
                    if (h % 11 == 0) b.zet(x, g, z, Material.COARSE_DIRT);
                    else if (h % 17 == 0) b.zet(x, g, z, Material.PODZOL);
                    else if (h % 5 == 0) b.zet(x, g + 1, z, Material.SHORT_GRASS);
                    else if (h % 31 == 0) b.zet(x, g + 1, z, Material.FERN);
                    else if (h % 47 == 0) b.zet(x, g + 1, z, Bouwer.hash(z, x) % 2 == 0 ? Material.DANDELION : Material.OXEYE_DAISY);
                }
            }
        // sparren en keien op een rooster van 8, met een beetje toeval; niet onder de kitruimte of op de brug
        for (int cx = -40; cx <= 40; cx += 8)
            for (int cz = kz - 36; cz <= kz + 54; cz += 8) {
                long h = Bouwer.hash(cx * 7, cz * 13);
                int x = cx + (int) (h % 5) - 2, z = cz + (int) ((h / 5) % 5) - 2;
                if (buiten(x, z) < 10.5 || Math.abs(x) > 39 || z < kz - 35 || z > kz + 53) continue;
                if (Math.abs(x) <= 11 && z >= kz + 23) continue;
                if (h % 4 == 0) kei(x, z, h);
                else if (h % 4 != 1) spar(x, z, 6 + (int) (h % 4));
            }
    }

    private void spar(int x, int z, int hoog) {
        var blad = Bouwer.blad(Material.SPRUCE_LEAVES);
        for (int y = g + 1; y <= g + hoog; y++) b.zet(x, y, z, Material.SPRUCE_LOG);
        for (int y = g + 3; y <= g + hoog + 1; y++) {
            int r = Math.max(0, (g + hoog + 1 - y + 1) / 2);
            if (y == g + hoog + 1) r = 0;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > r + (r > 1 ? 1 : 0)) continue;
                    if ((dx != 0 || dz != 0) || y > g + hoog) b.zet(x + dx, y, z + dz, blad);
                }
        }
        b.zet(x, g + hoog + 2, z, blad);
    }

    private void kei(int x, int z, long h) {
        Material[] m = {Material.MOSSY_COBBLESTONE, Material.ANDESITE, Material.COBBLESTONE, Material.STONE};
        for (int dx = 0; dx <= 1; dx++)
            for (int dz = 0; dz <= 1; dz++) b.zet(x + dx, g + 1, z + dz, m[(int) ((h + dx + dz * 2) % m.length)]);
        b.zet(x, g + 2, z, Material.MOSSY_COBBLESTONE);
    }

    /** Ronde toren (straal 4) op een hoek, massief, met banden, schietsleuven, kantelen en een vlag. */
    private void toren(int tx, int tz, Material vlag, int sx, int sz) {
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 4.5) continue;
                int x = tx + dx, z = tz + dz;
                boolean ring = d > 3.4;
                for (int y = g - 2; y <= g + 15; y++) {
                    Material m = ring && (y == g + 5 || y == g + 11) ? Material.DEEPSLATE_BRICKS : ring ? steen(x, y, z) : Material.STONE_BRICKS;
                    b.zet(x, y, z, m);
                }
                if (ring && Math.floorMod((int) Math.round(Math.atan2(dz, dx) * 8 / Math.PI), 2) == 0) b.zet(x, g + 16, z, steen(x, g + 16, z));
                // schietsleuven: een smalle donkere nis op vier kanten, buitenkant van de ring
                if (ring && (dx == 0 || dz == 0) && d > 3.9)
                    for (int y : new int[]{g + 8, g + 9, g + 13}) b.zet(x, y, z, Material.AIR);
            }
        b.zet(tx + 3 * sx, g + 16, tz + 3 * sz, Material.LANTERN);
        b.zet(tx - 3 * sx, g + 16, tz - 3 * sz, Material.LANTERN);
        for (int y = g + 16; y <= g + 20; y++) b.zet(tx, y, tz, Material.SPRUCE_FENCE);
        BlockFace naarBuiten = sx > 0 ? BlockFace.EAST : BlockFace.WEST;
        b.zet(tx + sx, g + 20, tz, Bouwer.gericht(vlag, naarBuiten));
    }

    /** Binnenplaats achter een muur: gemengde keien, lantaarnpalen, wat tonnen en hooi langs de buitenmuur. */
    private void binnenplaats(int k) {
        for (int x = -23; x <= 23; x++)
            for (int z = Math.min(kz + k * 17, kz + k * 21); z <= Math.max(kz + k * 17, kz + k * 21); z++) {
                long h = Bouwer.hash(x, z) % 7;
                b.zet(x, g, z, h < 3 ? Material.COBBLESTONE : h < 5 ? Material.GRAVEL : h < 6 ? Material.COBBLED_DEEPSLATE : Material.MOSSY_COBBLESTONE);
            }
        int zw = kz + k * 21;
        for (int sx : new int[]{-1, 1}) {
            b.zet(sx * 10, g + 1, zw, Material.SPRUCE_FENCE);
            b.zet(sx * 10, g + 2, zw, Material.SPRUCE_FENCE);
            b.zet(sx * 10, g + 3, zw, Material.LANTERN);
            b.zet(sx * 2, g + 1, zw, Material.BARREL);
            b.zet(sx * 3, g + 1, zw, Material.HAY_BLOCK);
        }
    }

    /** Eén kasteelmuur; k = -1 noord (kijkt naar +z), +1 zuid (kijkt naar -z). */
    private void muur(int k, List<Location> spawns) {
        int zin = kz + k * 14, zuit = kz + k * 16, zmid = kz + k * 15;
        Material band = k < 0 ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA;
        Material banier = k < 0 ? Material.RED_WALL_BANNER : Material.BLUE_WALL_BANNER;
        BlockFace veld = k < 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        if (b.plaats) {
            // massieve muur, weergang erop
            for (int x = -20; x <= 20; x++)
                for (int z = Math.min(zin, zuit); z <= Math.max(zin, zuit); z++) {
                    muurkolom(x, z, g + 1, g + 5);
                    b.zet(x, g + 6, z, z == zuit ? Material.DEEPSLATE_TILES : Material.POLISHED_ANDESITE);
                }
            for (int x = -20; x <= 20; x++) b.zet(x, g + 5, zin, band);                     // kleur van de kant
            // kantelen aan de veldkant: om de 3 een blok van 2 hoog, ertussen een halve borstwering (eroverheen schieten)
            Slab half = (Slab) Material.STONE_BRICK_SLAB.createBlockData();
            for (int x = -20; x <= 20; x++) {
                if (Math.floorMod(x, 3) == 0) {
                    b.zet(x, g + 7, zin, steen(x, g + 7, zin));
                    b.zet(x, g + 8, zin, Math.floorMod(x, 6) == 0 ? Material.CHISELED_STONE_BRICKS : steen(x, g + 8, zin));
                    if (Math.floorMod(x, 6) == 0) b.zet(x, g + 9, zin, Material.LANTERN);
                } else {
                    b.zet(x, g + 7, zin, half);
                }
                if (Math.abs(x) < 13) b.zet(x, g + 7, zuit, Material.STONE_BRICK_WALL);  // reling achter (niet bij de trappen)
            }
            for (int x : new int[]{-20, 20}) b.vul(x, g + 7, zmid, x, g + 7, zuit, Material.STONE_BRICK_WALL);
            // schietgaten: 1 breed, 2 hoog, dwars door de muur
            for (int x : new int[]{-17, -11, -5, 5, 11, 17})
                for (int z = Math.min(zin, zuit); z <= Math.max(zin, zuit); z++) b.vul(x, g + 2, z, x, g + 3, z, Material.AIR);
            // poort in het midden, 3 breed en 3 hoog, met een gebeitelde latei en fakkels ernaast
            for (int z = Math.min(zin, zuit); z <= Math.max(zin, zuit); z++) b.vul(-1, g + 1, z, 1, g + 3, z, Material.AIR);
            for (int x = -2; x <= 2; x++) b.zet(x, g + 4, zin, Material.CHISELED_STONE_BRICKS);
            for (int x : new int[]{-3, 3}) b.zet(x, g + 3, zin - k, Bouwer.gericht(Material.WALL_TORCH, veld));
            // banieren aan de veldkant (niet voor de schietgaten)
            for (int x : new int[]{-14, -8, 8, 14}) b.zet(x, g + 4, zin - k, Bouwer.gericht(banier, veld));
            // trappen tegen de achterkant, aan beide uiteinden omhoog naar de weergang
            int zt = zuit + k;
            for (int i = 0; i <= 5; i++) {
                for (int sx : new int[]{-1, 1}) {
                    int x = sx * (14 + i);
                    b.vul(x, g + 1, zt, x, g + i, zt, Material.STONE_BRICKS);
                    Stairs st = (Stairs) Material.STONE_BRICK_STAIRS.createBlockData();
                    st.setFacing(sx > 0 ? BlockFace.EAST : BlockFace.WEST);
                    b.zet(x, g + 1 + i, zt, st);
                    b.vul(x, g + 2 + i, zt, x, g + 4 + i, zt, Material.AIR);
                }
            }
        }
        // spawnpunten: twee op de weergang, twee op de binnenplaats, allemaal met de blik op het veld
        float yaw = k < 0 ? 0f : 180f;
        for (int x : new int[]{-9, 9}) spawns.add(new Location(b.w, x + 0.5, g + 7, zmid + 0.5, yaw, 0f));
        for (int x : new int[]{-5, 5}) spawns.add(new Location(b.w, x + 0.5, g + 1, kz + k * 19 + 0.5, yaw, 0f));
    }

    /** Ophaalbrug over de gracht naar een dichte poort in de zuidelijke buitenmuur (alleen om te zien). */
    private void ophaalbrug() {
        int zm = kz + 22;
        for (int z = zm + 1; z <= zm + 8; z++)
            for (int x = -2; x <= 2; x++) b.zet(x, g, z, Math.abs(x) == 2 ? Material.STRIPPED_SPRUCE_LOG : Material.SPRUCE_PLANKS);
        for (int x : new int[]{-2, 2}) {
            b.zet(x, g + 1, zm + 8, Material.SPRUCE_FENCE);
            b.zet(x, g + 2, zm + 8, Material.LANTERN);
        }
        var tralie = Bouwer.ruit(Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
        for (int x = -1; x <= 1; x++)
            for (int y = g + 1; y <= g + 4; y++) b.zet(x, y, zm + 1, tralie);
        for (int y = g + 1; y <= g + 5; y++) {
            b.zet(-2, y, zm + 1, Material.DEEPSLATE_BRICKS);
            b.zet(2, y, zm + 1, Material.DEEPSLATE_BRICKS);
        }
        for (int x = -2; x <= 2; x++) b.zet(x, g + 5, zm + 1, x == 0 ? Material.CHISELED_STONE_BRICKS : Material.DEEPSLATE_BRICKS);
    }
}
