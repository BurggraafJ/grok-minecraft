package nl.burggraaf.mc.zelda;

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
 * heeft, een brug die komt, de sluizen van eb en vloed, met de vuurstaf betoverd ijs, met de lichtpijlen Morgals nevel, het hek
 * van een sluipstuk, en de stenen van lava op een ritme)
 * tellen als open en het water van een getij als water: in het spel gaan ze open. In lava sta je niet (je springt er
 * meteen uit), maar over een gat met lava kun je springen.
 * Met de klauwhaak: een haakpunt dat je ergens vandaan kunt raken (binnen bereik, niets ertussen) brengt je naar zijn
 * landing, en daar loop je verder. Daarna moet alles te halen zijn: rustplaatsen, personen, vondsten (behalve wat pas met een voorwerp
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
    private final List<ZeldaHoofdstuk.Glij> glij = new ArrayList<>();
    private final List<ZeldaHoofdstuk.Val> vallen = new ArrayList<>();

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

    private static boolean hoog(Material m) {
        return org.bukkit.Tag.WALLS.isTagged(m) || org.bukkit.Tag.FENCES.isTagged(m) || org.bukkit.Tag.FENCE_GATES.isTagged(m);
    }

    private boolean vrij(int x, int y, int z) {
        Material m = soort(x, y, z);
        return m.isAir() || !m.isSolid();
    }

    private boolean staan(int x, int y, int z) {
        if (!vrij(x, y, z) || !vrij(x, y + 1, z)) return false;
        Material hier = soort(x, y, z), onder = soort(x, y - 1, z);
        if (hier == Material.LAVA || soort(x, y + 1, z) == Material.LAVA) return false;   // in lava sta je niet (je springt er meteen uit)
        if (opgevangen(x, y, z)) return false;
        if (hoog(onder)) return false;                    // op een muurtje of hek (anderhalf hoog) stap of spring je niet
        return onder.isSolid() || water(hier) || ladder(hier);
    }

    /** Zwem je hier in een vangnet (onder zijn {@code laag})? Daar kom je niet: de nevel zet je terug waar je veilig stond. */
    private boolean opgevangen(int x, int y, int z) {
        for (ZeldaHoofdstuk.Val v : vallen) if (y < v.laag() && v.gebied().in(x + 0.5, y + 0.5, z + 0.5)) return true;
        return false;
    }

    /** De glijvloer waar je op (x, y, z) op het ijs staat, of null. */
    private ZeldaHoofdstuk.Glij glijOp(int x, int y, int z) {
        for (ZeldaHoofdstuk.Glij g : glij) if (y == g.y + 1 && g.isIjs(x, z)) return g;
        return null;
    }

    /** Waar je uitkomt als je vanaf (x, z) over deze glijvloer glijdt (zoals in het spel, {@link ZeldaRegels#glij}). */
    private int[] glijd(ZeldaHoofdstuk.Glij g, int x, int z, org.bukkit.block.BlockFace r) {
        return ZeldaRegels.glij(x, z, r, g::isIjs, (a, b) -> vrij(a, g.y + 1, b) && vrij(a, g.y + 2, b),
                (a, b) -> !g.isIjs(a, b) && soort(a, g.y, b).isSolid(), 64);
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
            ZeldaHoofdstuk.Glij ijs = glijOp(x, y, z);
            if (ijs != null) {                                                                             // op het ijs: alleen glijden
                for (org.bukkit.block.BlockFace r : ZeldaRegels.GLIJ_RICHTINGEN) {
                    int[] e = glijd(ijs, x, z, r);
                    if (e[0] != x || e[1] != z) naar.add(new int[]{e[0], y, e[1]});
                }
                for (int[] n : naar) if (bereikt.add(sleutel(n[0], n[1], n[2]))) rij.add(n);
                continue;
            }
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
                ZeldaHoofdstuk.Glij g = n[1] == y && Math.abs(n[0] - x) + Math.abs(n[2] - z) == 1 ? glijOp(n[0], n[1], n[2]) : null;
                if (g != null) {                                                                           // een stap op het ijs: je glijdt door
                    int[] e = glijd(g, x, z, n[2] < z ? org.bukkit.block.BlockFace.NORTH : n[2] > z ? org.bukkit.block.BlockFace.SOUTH
                            : n[0] > x ? org.bukkit.block.BlockFace.EAST : org.bukkit.block.BlockFace.WEST);
                    n = new int[]{e[0], y, e[1]};
                }
                if (!staan(n[0], n[1], n[2]) && !(ladder(soort(n[0], n[1], n[2])) || water(soort(n[0], n[1], n[2])))) continue;
                if (opgevangen(n[0], n[1], n[2])) continue;
                if (bereikt.add(sleutel(n[0], n[1], n[2]))) rij.add(n);
            }
        }
    }

    /**
     * Kun je dit haakpunt raken vanaf een plek waar je kunt staan (binnen het bereik van de klauwhaak, met niets
     * ertussen)?
     */
    private boolean raakbaar(ZeldaHoofdstuk.Haakpunt hp) {
        int r = (int) ZeldaRegels.HAAK_BEREIK;
        double hx = hp.x() + 0.5, hy = hp.y() + 0.5, hz = hp.z() + 0.5;
        for (int x = hp.x() - r; x <= hp.x() + r; x++)
            for (int z = hp.z() - r; z <= hp.z() + r; z++)
                for (int y = hp.y() - r; y <= hp.y() + r; y++) {
                    if (!bereikt.contains(sleutel(x, y, z))) continue;
                    double ox = x + 0.5, oy = y + 1.62, oz = z + 0.5;
                    double d = Math.sqrt((hx - ox) * (hx - ox) + (hy - oy) * (hy - oy) + (hz - oz) * (hz - oz));
                    if (d > ZeldaRegels.HAAK_BEREIK || d < 0.5) continue;
                    if (zicht(ox, oy, oz, hp, d)) return true;
                }
        return false;
    }

    /** Een lijn van het oog naar het haakpunt: alleen vrije blokken ertussen. */
    private boolean zicht(double ox, double oy, double oz, ZeldaHoofdstuk.Haakpunt hp, double d) {
        double dx = (hp.x() + 0.5 - ox) / d, dy = (hp.y() + 0.5 - oy) / d, dz = (hp.z() + 0.5 - oz) / d;
        for (double a = 0.2; a <= d + 0.3; a += 0.2) {
            int bx = (int) Math.floor(ox + dx * a), by = (int) Math.floor(oy + dy * a), bz = (int) Math.floor(oz + dz * a);
            if (bx == hp.x() && by == hp.y() && bz == hp.z()) return true;
            if (!vrij(bx, by, bz)) return false;
        }
        return false;
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
        return onbereikbaar(e, voorwerpen, Set.of());
    }

    /**
     * Idem, maar de poorten in {@code dicht} (ids) blijven dicht: zo zie je wat er achter een poort ligt (en of je er
     * ook omheen kunt; {@code kitpvp zelda lopen <eiland> dicht <poort> zonder <voorwerp>}).
     */
    static List<String> onbereikbaar(ZeldaEiland e, Set<String> voorwerpen, Set<String> dicht) {
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
            l.glij.addAll(h.glijvloeren);
            l.vallen.addAll(h.vallen);
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                l.opening(k.in);
                l.opening(k.uit);
            }
            for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) l.opening(u.poort);
            for (ZeldaHoofdstuk.Reeks r : h.reeksen)                  // een vuurpuzzel gaat alleen met de vuurstaf open
                if (!dicht.contains(r.id) && (r.hoe != ZeldaHoofdstuk.Reeks.Hoe.VUUR || voorwerpen.contains("vuurstaf"))) l.opening(r.deur);
            if (voorwerpen.contains("bommen"))                                    // een gebarsten muur alleen met bommen
                for (ZeldaHoofdstuk.Barst b : h.barsten) if (!dicht.contains(b.id)) l.opening(b.muur);
            for (ZeldaHoofdstuk.Spiegels s : h.spiegels) l.opening(s.deur);
            for (ZeldaHoofdstuk.Ritme r : h.ritmes) l.opening(r.lava);              // lava op een ritme: de stenen liggen af en toe droog
            for (ZeldaHoofdstuk.Poort p : h.poorten) if (!dicht.contains(p.id)) l.opening(p.deur);
            if (voorwerpen.contains("vuurstaf")) for (ZeldaHoofdstuk.Smelt sm : h.smelten) l.opening(sm.ijs);   // betoverd ijs: gesmolten (lucht of water)
            if (voorwerpen.contains("lichtpijlen")) for (ZeldaHoofdstuk.Nevel nv : h.nevels) if (!dicht.contains(nv.id)) l.opening(nv.nevel);   // Morgals nevel: weggeschoten
            for (ZeldaHoofdstuk.Sluip sl : h.sluipen) l.opening(sl.hek);            // het hek van een sluipstuk staat open (alleen even dicht als je gezien wordt)
            for (ZeldaHoofdstuk.Getij g : h.getijden) {                  // eb en vloed: het water telt als water (zwemmen), de deuren als open
                for (ZeldaHoofdstuk.Deur d : g.lagen) for (int[] c : d.blokken) l.open.put(sleutel(c[0], c[1], c[2]), Material.WATER);
                for (ZeldaHoofdstuk.Deur d : g.sluizen) l.opening(d);
                for (ZeldaHoofdstuk.Deur d : g.vloeddeuren) l.opening(d);
            }
        }
        Location start = e.aanlegPlek();
        l.loop(start.getBlockX(), start.getBlockY(), start.getBlockZ());
        // de klauwhaak: van elk haakpunt dat je kunt raken, verder lopen vanaf zijn landing (tot er niets meer bij komt)
        List<ZeldaHoofdstuk.Haakpunt> haken = new ArrayList<>();
        for (ZeldaHoofdstuk h : e.delen()) haken.addAll(h.haakpunten);
        Set<String> gehaakt = new HashSet<>();
        boolean klauw = voorwerpen.contains("klauwhaak"), meer = klauw;
        while (meer) {
            meer = false;
            for (ZeldaHoofdstuk.Haakpunt hp : haken) {
                if (gehaakt.contains(hp.id()) || !l.raakbaar(hp)) continue;
                gehaakt.add(hp.id());
                meer = true;
                Location land = hp.landing();
                l.loop(land.getBlockX(), land.getBlockY(), land.getBlockZ());
            }
        }
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
            for (ZeldaHoofdstuk.Smelt sm : h.smelten) if (sm.voor != null && !l.bij(sm.voor, 1)) niet.add(d + "voor het ijs " + sm.id);
            for (ZeldaHoofdstuk.Nevel nv : h.nevels) if (nv.voor != null && !l.bij(nv.voor, 1)) niet.add(d + "voor de nevel " + nv.id);
            for (ZeldaHoofdstuk.Sluip sl : h.sluipen) {
                if (!l.in(sl.finish)) niet.add(d + "sluipen " + sl.id + ": de finish");
                if (!l.bij(sl.terug, 1)) niet.add(d + "sluipen " + sl.id + ": terug bij het zielvuur");
            }
            for (ZeldaHoofdstuk.Reeks r : h.reeksen) if (r.voor != null && !l.bij(r.voor, 1)) niet.add(d + "puzzel " + r.id + ": de plek om te mikken");
            for (ZeldaHoofdstuk.Getij g : h.getijden)
                for (ZeldaHoofdstuk.Getij.Hendel hd : g.hendels) if (!l.bij(new int[]{hd.x(), hd.y(), hd.z()}, 2)) niet.add(d + "getij " + g.id + ": hendel op " + hd.x() + "," + hd.y() + "," + hd.z());
            for (ZeldaHoofdstuk.Graaf g : h.graven) if (!l.bij(g.plek(), 1)) niet.add(d + "graafplek " + g.id() + " " + xyz(g.plek()));
            if (klauw) for (ZeldaHoofdstuk.Haakpunt hp : h.haakpunten)
                if (!gehaakt.contains(hp.id())) niet.add(d + "haakpunt " + hp.id() + " " + hp.x() + "," + hp.y() + "," + hp.z() + ": nergens vandaan te raken");
        }
        return niet;
    }

    private static String xyz(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
