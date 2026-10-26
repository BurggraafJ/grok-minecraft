package nl.burggraaf.mc.kitpvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

import java.util.List;

/**
 * Kasteelarena (Jelle 26-10), vooral voor bogen: twee kasteelmuren tegenover elkaar (noord en zuid, 41 lang,
 * 6 hoog, 3 dik) met kantelen en een borstwering op de weergang, schietgaten, een poort in het midden en trappen
 * aan de achterkant naar boven. Ertussen een open middenveld met weinig dekking, zodat je elkaar altijd ziet.
 * Achter elke muur een binnenplaats; hoge buitenmuren houden je binnen. Midden op (0, kz).
 *
 *   z = kz-22  buitenmuur          z = kz+22
 *   z = kz-21..-17 binnenplaats (trappen tegen de muur)
 *   z = kz-16..-14 muur noord · z = kz-13..13 middenveld · z = kz+14..16 muur zuid
 */
final class Kasteel {

    private final World w;
    private final boolean plaats;

    private Kasteel(World w, boolean plaats) {
        this.w = w;
        this.plaats = plaats;
    }

    private void zet(int x, int y, int z, Material m) {
        if (plaats) w.getBlockAt(x, y, z).setType(m, false);
    }

    private void zet(int x, int y, int z, BlockData d) {
        if (plaats) w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    private void vul(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        if (!plaats) return;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != m) b.setType(m, false);
                }
    }

    private static long hash(int x, int z) {
        long h = x * 73428767L ^ z * 912931L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        return Math.abs(h ^ (h >>> 15));
    }

    static void bouw(KitPvP plugin, World w, int g, int kz, boolean plaats, List<Location> spawns) {
        new Kasteel(w, plaats).bouwHet(g, kz, spawns);
    }

    private void bouwHet(int g, int kz, List<Location> spawns) {
        if (plaats) {
            vul(-26, g + 1, kz - 24, 26, g + 14, kz + 24, Material.AIR);
            vul(-26, g - 2, kz - 24, 26, g - 1, kz + 24, Material.DIRT);
            vul(-26, g, kz - 24, 26, g, kz + 24, Material.GRASS_BLOCK);
        }
        // middenveld: gras met een pad van grof zand en een paar lage dekkingen
        for (int x = -23; x <= 23; x++)
            for (int z = kz - 13; z <= kz + 13; z++)
                if (Math.abs(x) <= 1 || hash(x / 2, z / 2) % 9 == 0) zet(x, g, z, Material.COARSE_DIRT);
        for (int sx : new int[]{-1, 1}) {
            vul(sx * 7, g + 1, kz, sx * 9, g + 1, kz, Material.MOSSY_STONE_BRICKS);          // laag muurtje
            zet(sx * 15, g + 1, kz - 5, Material.HAY_BLOCK);
            zet(sx * 15, g + 1, kz + 5, Material.HAY_BLOCK);
            zet(sx * 15, g + 2, kz + 5, Material.HAY_BLOCK);
        }
        // buitenmuren (hoog genoeg dat ook de ninja er niet overheen springt)
        for (int x = -24; x <= 24; x++)
            for (int z : new int[]{kz - 22, kz + 22}) vul(x, g + 1, z, x, g + 11, z, hash(x, z) % 5 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        for (int z = kz - 22; z <= kz + 22; z++)
            for (int x : new int[]{-24, 24}) vul(x, g + 1, z, x, g + 11, z, hash(x, z) % 5 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        for (int k : new int[]{-1, 1}) muur(g, kz, k, spawns);
    }

    /** Eén kasteelmuur; k = -1 noord (kijkt naar +z), +1 zuid (kijkt naar -z). */
    private void muur(int g, int kz, int k, List<Location> spawns) {
        int zin = kz + k * 14, zuit = kz + k * 16, zmid = kz + k * 15;
        Material band = k < 0 ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA;
        // binnenplaats
        for (int x = -23; x <= 23; x++)
            for (int z = Math.min(zuit + k, kz + k * 21); z <= Math.max(zuit + k, kz + k * 21); z++)
                zet(x, g, z, hash(x, z) % 3 == 0 ? Material.GRAVEL : Material.COBBLESTONE);
        // massieve muur, weergang erop
        for (int x = -20; x <= 20; x++)
            for (int z = Math.min(zin, zuit); z <= Math.max(zin, zuit); z++) {
                vul(x, g + 1, z, x, g + 5, z, hash(x, z) % 6 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                zet(x, g + 6, z, Material.POLISHED_ANDESITE);
            }
        for (int x = -20; x <= 20; x++) zet(x, g + 5, zin, band);                      // kleur van de kant
        // kantelen aan de veldkant: om de 3 een blok van 2 hoog, ertussen een halve borstwering (eroverheen schieten)
        Slab half = (Slab) Material.STONE_BRICK_SLAB.createBlockData();
        for (int x = -20; x <= 20; x++) {
            if (Math.floorMod(x, 3) == 0) {
                vul(x, g + 7, zin, x, g + 8, zin, Material.STONE_BRICKS);
                if (Math.floorMod(x, 6) == 0) zet(x, g + 9, zin, Material.LANTERN);
            } else {
                zet(x, g + 7, zin, half);
            }
            if (Math.abs(x) < 13) zet(x, g + 7, zuit, Material.STONE_BRICK_WALL);   // reling achter (niet bij de trappen)
        }
        for (int x : new int[]{-20, 20}) vul(x, g + 7, zmid, x, g + 7, zuit, Material.STONE_BRICK_WALL);
        // schietgaten: 1 breed, 2 hoog, dwars door de muur
        for (int x : new int[]{-17, -11, -5, 5, 11, 17})
            for (int z = Math.min(zin, zuit); z <= Math.max(zin, zuit); z++) vul(x, g + 2, z, x, g + 3, z, Material.AIR);
        // poort in het midden, 3 breed en 3 hoog
        for (int z = Math.min(zin, zuit); z <= Math.max(zin, zuit); z++) vul(-1, g + 1, z, 1, g + 3, z, Material.AIR);
        vul(-2, g + 4, zin, 2, g + 4, zin, Material.CHISELED_STONE_BRICKS);
        // trappen tegen de achterkant, aan beide uiteinden omhoog naar de weergang
        int zt = zuit + k;
        for (int i = 0; i <= 5; i++) {
            for (int sx : new int[]{-1, 1}) {
                int x = sx * (14 + i);
                vul(x, g + 1, zt, x, g + i, zt, Material.STONE_BRICKS);
                Stairs st = (Stairs) Material.STONE_BRICK_STAIRS.createBlockData();
                st.setFacing(sx > 0 ? BlockFace.EAST : BlockFace.WEST);
                zet(x, g + 1 + i, zt, st);
                vul(x, g + 2 + i, zt, x, g + 4 + i, zt, Material.AIR);
            }
        }
        // spawnpunten: twee op de weergang, twee op de binnenplaats, allemaal met de blik op het veld
        float yaw = k < 0 ? 0f : 180f;
        for (int x : new int[]{-9, 9}) spawns.add(new Location(w, x + 0.5, g + 7, zmid + 0.5, yaw, 0f));
        for (int x : new int[]{-5, 5}) spawns.add(new Location(w, x + 0.5, g + 1, kz + k * 19 + 0.5, yaw, 0f));
    }
}
