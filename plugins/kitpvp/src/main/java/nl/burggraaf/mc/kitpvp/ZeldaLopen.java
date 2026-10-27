package nl.burggraaf.mc.kitpvp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Kun je er lopen? Volgt vanaf de aanleg van een eiland alle plekken waar een speler kan staan: een stap omhoog van
 * hoogstens één blok (springen), omlaag hoogstens drie, over een gat van één blok springen, ladders en water op en neer.
 * De deuren van het eiland (kamers, poorten, gebarsten muren, de deur van een spiegelpuzzel, een poort voor wie iets
 * heeft, een brug die komt, de sluizen van eb en vloed) tellen als open en het water van een getij als water: in het spel gaan ze open. Daarna moet alles te halen zijn: rustplaatsen, personen, vondsten (behalve wat pas met een voorwerp
 * kan dat er nog niet is, zoals de klauwhaak), kamers, uitdagingen, verhalen, bronnen, spiegels, bellen en de plek vóór
 * een gebarsten muur. Zo valt een plek op die de test alleen met teleporteren haalt.
 *
 * <p>Voor elk eiland ({@code kitpvp zelda lopen <eiland>}, en in {@code kitpvp zelda test <speler> alles}). Op het
 * Lindewoud kom je ook zonder de pilaren over de Kloof: via de ladders en de beek. Laadt de chunks van het eiland
 * (alleen voor tests en beheer).
 */
final class ZeldaLopen {

    private final World w;
    private final int x1, x2, y1, y2, z1, z2;
    private final Map<Long, ChunkSnapshot> chunks = new HashMap<>();
    private final Map<Long, Material> open = new HashMap<>();
    private final Set<Long> bereikt = new HashSet<>();

    private ZeldaLopen(World w, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.w = w;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.z1 = z1;
        this.z2 = z2;
    }

    private static long sleutel(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private Material soort(int x, int y, int z) {
        if (x < x1 || x > x2 || z < z1 || z > z2 || y < y1 || y > y2) return Material.BEDROCK;
        Material d = open.get(sleutel(x, y, z));
        if (d != null) return d;
        long c = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
        ChunkSnapshot s = chunks.computeIfAbsent(c, k -> w.getChunkAt(x >> 4, z >> 4).getChunkSnapshot(false, false, false));
        return s.getBlockType(x & 15, y, z & 15);
    }

    private static boolean water(Material m) {
        return m == Material.WATER || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS || m == Material.KELP || m == Material.KELP_PLANT;
    }

    private static boolean ladder(Material m) {
        return m == Material.LADDER || m == Material.VINE;
    }

    private boolean vrij(int x, int y, int z) {
        Material m = soort(x, y, z);
        return m.isAir() || !m.isSolid();
    }

    private boolean staan(int x, int y, int z) {
        if (!vrij(x, y, z) || !vrij(x, y + 1, z)) return false;
        Material hier = soort(x, y, z), onder = soort(x, y - 1, z);
        return onder.isSolid() || water(hier) || ladder(hier);
    }

    private void loop(int sx, int sy, int sz) {
        ArrayDeque<int[]> rij = new ArrayDeque<>();
        if (!staan(sx, sy, sz)) return;
        bereikt.add(sleutel(sx, sy, sz));
        rij.add(new int[]{sx, sy, sz});
        int[][] richting = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!rij.isEmpty() && bereikt.size() < 600_000) {
            int[] c = rij.poll();
            int x = c[0], y = c[1], z = c[2];
            List<int[]> naar = new ArrayList<>();
            Material hier = soort(x, y, z);
            for (int[] r : richting) {
                int nx = x + r[0], nz = z + r[1];
                if (staan(nx, y + 1, nz) && vrij(x, y + 2, z)) naar.add(new int[]{nx, y + 1, nz});       // stap of sprong omhoog
                else if (water(hier) && staan(nx, y + 2, nz) && vrij(x, y + 3, z)) naar.add(new int[]{nx, y + 2, nz});   // uit het water klimmen
                if (staan(nx, y, nz)) naar.add(new int[]{nx, y, nz});
                else if (vrij(nx, y, nz) && vrij(nx, y + 1, nz)) {                                         // eraf, hoogstens drie
                    for (int d = 1; d <= 3; d++) {
                        if (staan(nx, y - d, nz)) {
                            naar.add(new int[]{nx, y - d, nz});
                            break;
                        }
                        if (!vrij(nx, y - d, nz)) break;
                    }
                    int vx = x + 2 * r[0], vz = z + 2 * r[1];                                              // over een gat springen
                    if (vrij(nx, y + 2, nz) && vrij(x, y + 2, z))
                        for (int d = 0; d <= 1; d++)
                            if (staan(vx, y - d, vz)) {
                                naar.add(new int[]{vx, y - d, vz});
                                break;
                            }
                }
            }
            if (ladder(hier) || water(hier)) {                                                             // klimmen en zwemmen
                if (vrij(x, y + 1, z) && vrij(x, y + 2, z)) naar.add(new int[]{x, y + 1, z});
                if (vrij(x, y - 1, z)) naar.add(new int[]{x, y - 1, z});
            }
            for (int[] n : naar) {
                if (!staan(n[0], n[1], n[2]) && !(ladder(soort(n[0], n[1], n[2])) || water(soort(n[0], n[1], n[2])))) continue;
                if (bereikt.add(sleutel(n[0], n[1], n[2]))) rij.add(n);
            }
        }
    }

    private boolean in(ZeldaHoofdstuk.Box b) {
        for (int x = b.x1(); x <= b.x2(); x++)
            for (int z = b.z1(); z <= b.z2(); z++)
                for (int y = b.y1(); y <= b.y2(); y++) if (bereikt.contains(sleutel(x, y, z))) return true;
        return false;
    }

    private boolean bij(Location l, int r) {
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return in(ZeldaHoofdstuk.Box.van(x - r, y - r, z - r, x + r, y + r, z + r));
    }

    private boolean bij(int[] c, int r) {
        return in(ZeldaHoofdstuk.Box.van(c[0] - r, c[1] - r, c[2] - r, c[0] + r, c[1] + r, c[2] + r));
    }

    /** Een deur zoals hij open is (meestal lucht; een brug die komt: planken). */
    private void opening(ZeldaHoofdstuk.Deur d) {
        if (d != null) for (int[] c : d.blokken) open.put(sleutel(c[0], c[1], c[2]), d.open.getMaterial());
    }

    /**
     * Wat er op dit eiland niet te belopen is (leeg = alles), met de voorwerpen die er nu zijn ({@code voorwerpen}: hun
     * vlaggen). Elke regel noemt het onderdeel en waar het ligt.
     */
    static List<String> onbereikbaar(ZeldaEiland e, Set<String> voorwerpen) {
        ZeldaHoofdstuk eerste = e.delen().get(0);
        if (eerste.w == null || e.aanlegRust() == null) return List.of(e.id() + ": niet geladen");
        int ax = Integer.MAX_VALUE, ay = Integer.MAX_VALUE, az = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, by = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        for (ZeldaHoofdstuk h : e.delen()) {
            ZeldaHoofdstuk.Box b = h.speelgebied;
            ax = Math.min(ax, b.x1());
            ay = Math.min(ay, b.y1());
            az = Math.min(az, b.z1());
            bx = Math.max(bx, b.x2());
            by = Math.max(by, b.y2());
            bz = Math.max(bz, b.z2());
        }
        ZeldaLopen l = new ZeldaLopen(eerste.w, ax, ay, az, bx, by, bz);
        for (ZeldaHoofdstuk h : e.delen()) {
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                l.opening(k.in);
                l.opening(k.uit);
            }
            for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) l.opening(u.poort);
            for (ZeldaHoofdstuk.Reeks r : h.reeksen) l.opening(r.deur);
            for (ZeldaHoofdstuk.Barst b : h.barsten) l.opening(b.muur);
            for (ZeldaHoofdstuk.Spiegels s : h.spiegels) l.opening(s.deur);
            for (ZeldaHoofdstuk.Poort p : h.poorten) l.opening(p.deur);
            for (ZeldaHoofdstuk.Getij g : h.getijden) {                  // eb en vloed: het water telt als water (zwemmen), de deuren als open
                for (ZeldaHoofdstuk.Deur d : g.lagen) for (int[] c : d.blokken) l.open.put(sleutel(c[0], c[1], c[2]), Material.WATER);
                for (ZeldaHoofdstuk.Deur d : g.sluizen) l.opening(d);
                for (ZeldaHoofdstuk.Deur d : g.vloeddeuren) l.opening(d);
            }
        }
        Location start = e.aanlegPlek();
        l.loop(start.getBlockX(), start.getBlockY(), start.getBlockZ());
        List<String> niet = new ArrayList<>();
        for (ZeldaHoofdstuk h : e.delen()) {
            String d = h.id() + ": ";
            for (ZeldaHoofdstuk.Rust r : h.rust) if (!l.bij(r.plek, 1)) niet.add(d + "rustplaats " + r.id + " " + xyz(r.plek));
            for (ZeldaHoofdstuk.Persoon p : h.personen) if (!l.bij(p.plek, 2)) niet.add(d + p.naam + " " + xyz(p.plek));
            for (ZeldaHoofdstuk.Vondst v : h.vondsten)
                if ((v.voorwerp == null || voorwerpen.contains(v.voorwerp)) && !l.in(v.zone)) niet.add(d + "vondst " + v.vlag + " " + xyz(v.plek));
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                if (!l.in(k.start)) niet.add(d + "kamer " + k.id);
                if (k.bel != null && !l.bij(k.bel, 3)) niet.add(d + "kamer " + k.id + ": de bel");
            }
            for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) {
                if (!l.in(u.start)) niet.add(d + "uitdaging " + u.id + ": start");
                if (!l.in(u.finish)) niet.add(d + "uitdaging " + u.id + ": finish");
            }
            for (ZeldaHoofdstuk.Verhaal v : h.verhalen) if (!l.in(v.zone)) niet.add(d + "verhaal " + v.id);
            for (ZeldaHoofdstuk.Bron b : h.bronnen) if (!l.in(b.zone)) niet.add(d + "bron " + b.id);
            for (ZeldaHoofdstuk.Spiegels s : h.spiegels)
                for (int[] c : s.spiegels) if (!l.bij(c, 3)) niet.add(d + "spiegels " + s.id + ": spiegel op " + c[0] + "," + c[1] + "," + c[2]);
            for (ZeldaHoofdstuk.Barst b : h.barsten) if (b.voor != null && !l.bij(b.voor, 1)) niet.add(d + "voor de gebarsten muur " + b.id);
            for (ZeldaHoofdstuk.Getij g : h.getijden)
                for (ZeldaHoofdstuk.Getij.Hendel hd : g.hendels) if (!l.bij(new int[]{hd.x(), hd.y(), hd.z()}, 2)) niet.add(d + "getij " + g.id + ": hendel op " + hd.x() + "," + hd.y() + "," + hd.z());
        }
        return niet;
    }

    private static String xyz(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
