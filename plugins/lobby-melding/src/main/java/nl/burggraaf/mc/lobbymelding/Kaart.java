package nl.burggraaf.mc.lobbymelding;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * {@code lobbymelding kaart}: plaatjes van wat er echt gebouwd staat, om de tuin te bekijken zonder in te loggen.
 * Bovenaanzicht (hoogste blok, met schaduw voor hoogte), zij-aanzichten (eerste blok langs een kijkrichting) en een
 * schuin vogelvlucht-aanzicht. Lezen gebeurt op de hoofdthread (snapshot), tekenen mag daarna overal.
 * Schrijft PNG's naar {@code plugins/LobbyMelding/kaart/}.
 */
final class Kaart {

    private Kaart() {
    }

    /** Een blok uit de wereld, vastgelegd als kleur (0 = lucht) en of het een plantje (niet vol blok) is. */
    record Vak(int rgb, boolean klein) {}

    static Vak vak(Block b) {
        Material m = b.getType();
        if (m.isAir() || m == Material.LIGHT || m == Material.BARRIER || m == Material.STRUCTURE_VOID) return null;
        return new Vak(kleur(b), b.isPassable() && m != Material.WATER);
    }

    static int kleur(Block b) {
        Material m = b.getType();
        String n = m.name();
        if (m == Material.WATER) return 0x3f76e4;
        if (n.contains("FROGLIGHT") || n.contains("LANTERN") || m == Material.SHROOMLIGHT || m == Material.GLOWSTONE) return 0xfff2a0;
        if (n.equals("POPPY") || n.equals("RED_TULIP") || n.equals("ROSE_BUSH")) return 0xd8302c;
        if (n.equals("DANDELION") || n.equals("SUNFLOWER")) return 0xf2d23a;
        if (n.equals("CORNFLOWER")) return 0x4f6fe0;
        if (n.equals("BLUE_ORCHID")) return 0x34a7e0;
        if (n.equals("ALLIUM") || n.equals("LILAC")) return 0xb46ad8;
        if (n.equals("PINK_TULIP") || n.equals("PEONY") || n.equals("PINK_PETALS")) return 0xf0a0c8;
        if (n.equals("ORANGE_TULIP") || n.equals("TORCHFLOWER")) return 0xf08a30;
        if (n.equals("WHITE_TULIP") || n.equals("OXEYE_DAISY") || n.equals("LILY_OF_THE_VALLEY") || n.equals("AZURE_BLUET")) return 0xf2f2ea;
        if (n.contains("GLASS")) return 0xc8e6f0;
        if (n.endsWith("LEAVES")) return n.startsWith("CHERRY") ? 0xf2b2cf : n.startsWith("FLOWERING") ? 0x6f9a3e : n.startsWith("BIRCH") ? 0x7fa64e : 0x4f8a2e;
        int c = b.getBlockData().getMapColor().asRGB();
        return c == 0 ? 0x808080 : c;
    }

    static int schaduw(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** Snapshot van een blok x1..x2 × y1..y2 × z1..z2 (op de hoofdthread maken). */
    static final class Stuk {
        final int x1, y1, z1, bx, by, bz;
        final Vak[] v;

        Stuk(World w, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            bx = x2 - x1 + 1;
            by = y2 - y1 + 1;
            bz = z2 - z1 + 1;
            v = new Vak[bx * by * bz];
            for (int x = 0; x < bx; x++)
                for (int z = 0; z < bz; z++)
                    for (int y = 0; y < by; y++) v[(x * bz + z) * by + y] = vak(w.getBlockAt(x1 + x, y1 + y, z1 + z));
        }

        Vak get(int x, int y, int z) {      // relatief
            if (x < 0 || y < 0 || z < 0 || x >= bx || y >= by || z >= bz) return null;
            return v[(x * bz + z) * by + y];
        }
    }

    /** Bovenaanzicht (noord boven), hoogte als lichter/donkerder, met een zacht schaduwrandje naar het zuidoosten. */
    static File boven(Stuk s, File map, String naam, int px) throws IOException {
        BufferedImage img = new BufferedImage(s.bx * px, s.bz * px, BufferedImage.TYPE_INT_RGB);
        int[][] hoog = new int[s.bx][s.bz];
        int[][] kl = new int[s.bx][s.bz];
        for (int x = 0; x < s.bx; x++)
            for (int z = 0; z < s.bz; z++) {
                hoog[x][z] = -1;
                for (int y = s.by - 1; y >= 0; y--) {
                    Vak v = s.get(x, y, z);
                    if (v == null) continue;
                    hoog[x][z] = y;
                    kl[x][z] = v.rgb();
                    break;
                }
            }
        for (int x = 0; x < s.bx; x++)
            for (int z = 0; z < s.bz; z++) {
                int c = 0x101010;
                if (hoog[x][z] >= 0) {
                    double f = 0.7 + 0.5 * hoog[x][z] / Math.max(1.0, s.by - 1);
                    int nw = x > 0 && z > 0 ? hoog[x - 1][z - 1] : hoog[x][z];
                    if (nw > hoog[x][z]) f *= 0.78;                    // schaduw van iets hogers
                    else if (nw < hoog[x][z]) f *= 1.08;
                    c = schaduw(kl[x][z], f);
                }
                for (int i = 0; i < px; i++)
                    for (int j = 0; j < px; j++) img.setRGB(x * px + i, z * px + j, c);
            }
        return schrijf(img, map, naam);
    }

    /**
     * Zij-aanzicht: kijk in richting (dx, dz) (een van de vier windrichtingen) door het hele stuk; verder weg =
     * vager. Links-rechts zoals je het zelf ziet.
     */
    static File zij(Stuk s, File map, String naam, int dx, int dz, int px) throws IOException {
        int breed = dx != 0 ? s.bz : s.bx, diep = dx != 0 ? s.bx : s.bz;
        BufferedImage img = new BufferedImage(breed * px, s.by * px, BufferedImage.TYPE_INT_RGB);
        for (int a = 0; a < breed; a++)
            for (int y = 0; y < s.by; y++) {
                int c = 0xa9cdf5;
                for (int d = 0; d < diep; d++) {
                    int x = dx > 0 ? d : dx < 0 ? s.bx - 1 - d : a;
                    int z = dz > 0 ? d : dz < 0 ? s.bz - 1 - d : a;
                    Vak v = s.get(x, y, z);
                    if (v == null) continue;
                    c = mengen(schaduw(v.rgb(), 1.0 - 0.35 * d / diep), 0xa9cdf5, 0.55 * d / diep);
                    break;
                }
                // in richting +z (zuid) kijken: oost (+x) is links; in −z: oost rechts; in +x: zuid rechts; in −x: zuid links
                int col = dz > 0 ? breed - 1 - a : dz < 0 ? a : dx > 0 ? a : breed - 1 - a;
                for (int i = 0; i < px; i++)
                    for (int j = 0; j < px; j++) img.setRGB(col * px + i, (s.by - 1 - y) * px + j, c);
            }
        return schrijf(img, map, naam);
    }

    static int mengen(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Vogelvlucht: schuin van boven, gezien vanuit het zuidoosten (draai 0) of een andere hoek (draai 1..3 = steeds
     * een kwartslag). Elk blok een klein kubusje met drie zichtbare vlakken.
     */
    static File vogel(Stuk s, File map, String naam, int draai, int px) throws IOException {
        int n = Math.max(s.bx, s.bz);
        int w = (int) ((s.bx + s.bz) * px) + px * 2, h = (int) ((s.bx + s.bz) * px / 2.0 + s.by * px) + px * 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        gr.setColor(new Color(0xbfdcf7));
        gr.fillRect(0, 0, w, h);
        // gedraaide maten
        int rx = (draai % 2 == 0) ? s.bx : s.bz, rz = (draai % 2 == 0) ? s.bz : s.bx;
        double ox = rz * px + px, oy = s.by * px + px;
        for (int som = 0; som <= rx + rz - 2; som++)
            for (int y = 0; y < s.by; y++)
                for (int a = Math.max(0, som - (rz - 1)); a <= Math.min(rx - 1, som); a++) {
                    int b = som - a;
                    int[] o = terug(s, draai, a, b);
                    Vak v = s.get(o[0], y, o[1]);
                    if (v == null) continue;
                    int[] oo = terug(s, draai, a + 1, b), oz = terug(s, draai, a, b + 1);
                    Vak boven = s.get(o[0], y + 1, o[1]);
                    Vak oost = a + 1 < rx ? s.get(oo[0], y, oo[1]) : null;
                    Vak zuid = b + 1 < rz ? s.get(oz[0], y, oz[1]) : null;
                    boolean dicht = !v.klein();
                    if (dicht && boven != null && !boven.klein() && oost != null && !oost.klein() && zuid != null && !zuid.klein()) continue;
                    double sx = ox + (a - b) * px, sy = oy + (a + b) * px / 2.0 - y * px;
                    if (v.klein()) {
                        gr.setColor(new Color(v.rgb()));
                        int k = Math.max(1, px / 2);
                        gr.fillRect((int) (sx + px - k / 2.0), (int) (sy + px / 2.0 - k / 2.0), k, k);
                        continue;
                    }
                    // bovenvlak
                    int[] tx = {(int) sx, (int) (sx + px), (int) (sx + 2 * px), (int) (sx + px)};
                    int[] ty = {(int) (sy + px / 2.0), (int) sy, (int) (sy + px / 2.0), (int) (sy + px)};
                    gr.setColor(new Color(schaduw(v.rgb(), 1.08)));
                    gr.fillPolygon(tx, ty, 4);
                    // linkervlak (zuid)
                    int[] lx = {(int) sx, (int) (sx + px), (int) (sx + px), (int) sx};
                    int[] ly = {(int) (sy + px / 2.0), (int) (sy + px), (int) (sy + 2 * px), (int) (sy + px * 1.5)};
                    gr.setColor(new Color(schaduw(v.rgb(), 0.78)));
                    gr.fillPolygon(lx, ly, 4);
                    // rechtervlak (oost)
                    int[] qx = {(int) (sx + px), (int) (sx + 2 * px), (int) (sx + 2 * px), (int) (sx + px)};
                    int[] qy = {(int) (sy + px), (int) (sy + px / 2.0), (int) (sy + px * 1.5), (int) (sy + 2 * px)};
                    gr.setColor(new Color(schaduw(v.rgb(), 0.62)));
                    gr.fillPolygon(qx, qy, 4);
                }
        gr.dispose();
        if (n <= 0) return null;
        return schrijf(img, map, naam);
    }

    /** Gedraaide coördinaat (a langs "oost", b langs "zuid" in beeld) terug naar de echte x/z in het stuk. */
    private static int[] terug(Stuk s, int draai, int a, int b) {
        return switch (draai & 3) {
            case 0 -> new int[]{a, b};
            case 1 -> new int[]{s.bx - 1 - b, a};
            case 2 -> new int[]{s.bx - 1 - a, s.bz - 1 - b};
            default -> new int[]{b, s.bz - 1 - a};
        };
    }

    private static File schrijf(BufferedImage img, File map, String naam) throws IOException {
        map.mkdirs();
        File f = new File(map, naam + ".png");
        ImageIO.write(img, "png", f);
        return f;
    }
}
