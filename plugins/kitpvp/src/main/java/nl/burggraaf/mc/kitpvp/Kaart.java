package nl.burggraaf.mc.kitpvp;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * {@code kitpvp kaart}: plaatjes van wat er echt gebouwd is, om te controleren zonder in te loggen. Bovenaanzicht
 * (hoogste blok, met schaduw voor hoogte) en vooraanzichten (eerste blok dat je ziet langs een kijkrichting).
 * Schrijft PNG's naar {@code plugins/KitPvP/kaart/}.
 */
final class Kaart {

    private Kaart() {
    }

    private static int kleur(Block b) {
        Material m = b.getType();
        if (m == Material.WATER) return 0x3f76e4;
        if (m == Material.AIR || m == Material.CAVE_AIR) return 0x9fc6ff;
        int c = b.getBlockData().getMapColor().asRGB();
        if (m.name().contains("FROGLIGHT") || m.name().contains("LANTERN") || m == Material.SHROOMLIGHT) c = 0xfff2a0;
        return c;
    }

    private static int schaduw(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Bovenaanzicht van x1..x2, z1..z2 (noord boven), vanaf hoogte ytop naar beneden. */
    static File boven(World w, File map, String naam, int x1, int z1, int x2, int z2, int ybodem, int ytop, int px) throws IOException {
        int bw = x2 - x1 + 1, bh = z2 - z1 + 1;
        BufferedImage img = new BufferedImage(bw * px, bh * px, BufferedImage.TYPE_INT_RGB);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                int c = 0x000000;
                for (int y = ytop; y >= ybodem; y--) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    c = schaduw(kleur(b), 0.55 + 0.45 * (y - ybodem) / Math.max(1.0, ytop - ybodem));
                    break;
                }
                for (int i = 0; i < px; i++)
                    for (int j = 0; j < px; j++) img.setRGB((x - x1) * px + i, (z - z1) * px + j, c);
            }
        return schrijf(img, map, naam);
    }

    /**
     * Vooraanzicht: kijk vanaf vlak {@code vanaf} (x of z) in richting (dx, dz), hooguit {@code diep} blokken ver;
     * breedte langs de andere as van a1..a2, hoogte y1..y2. Verder weg = donkerder.
     */
    static File voor(World w, File map, String naam, int dx, int dz, int vanaf, int a1, int a2, int y1, int y2, int diep, int px) throws IOException {
        int bw = a2 - a1 + 1, bh = y2 - y1 + 1;
        BufferedImage img = new BufferedImage(bw * px, bh * px, BufferedImage.TYPE_INT_RGB);
        for (int a = a1; a <= a2; a++)
            for (int y = y1; y <= y2; y++) {
                int c = 0x9fc6ff;
                for (int d = 0; d < diep; d++) {
                    int x = dx != 0 ? vanaf + d * dx : a, z = dz != 0 ? vanaf + d * dz : a;
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    c = schaduw(kleur(b), 1.0 - 0.6 * d / diep);
                    break;
                }
                // links-rechts zoals je het ziet als je in (dx, dz) kijkt
                int col = (dx < 0 || dz > 0) ? (a2 - a) : (a - a1);
                for (int i = 0; i < px; i++)
                    for (int j = 0; j < px; j++) img.setRGB(col * px + i, (y2 - y) * px + j, c);
            }
        return schrijf(img, map, naam);
    }

    private static File schrijf(BufferedImage img, File map, String naam) throws IOException {
        map.mkdirs();
        File f = new File(map, naam + ".png");
        ImageIO.write(img, "png", f);
        return f;
    }
}
