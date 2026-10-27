package nl.burggraaf.mc.lobbymelding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bouwtekening van de tuin: per blok in een vaste doos een bloktoestand als tekst ("oak_stairs[facing=north]"),
 * zonder wereld of server. Zo kan de tuin in unit-tests worden nagerekend en daarna in stukjes per tick worden
 * neergezet ({@link Bouwer}). Leeg (0) = lucht. Onder de grond (y &lt; g) staat aarde, op y = g gras, tenzij de tekening
 * iets anders zegt.
 */
final class Plan {

    final int x1, x2, z1, z2, y1, y2;    // doos (inclusief)
    final int g;                          // grondhoogte: gras op y = g
    private final int bx, by, bz;
    private final short[] cel;
    private final List<String> palet = new ArrayList<>();
    private final Map<String, Short> index = new HashMap<>();

    /** Teksten op borden: positie + 4 regels (voorkant). */
    record Bord(int x, int y, int z, String[] regels) {}
    final List<Bord> borden = new ArrayList<>();

    /** Zwevende tekst (TextDisplay): plek, MiniMessage, schaal. */
    record Label(double x, double y, double z, String tekst, float schaal) {}
    final List<Label> labels = new ArrayList<>();

    /** Dieren die bij de start worden neergezet (niet opgeslagen). soort = EntityType-naam. */
    record Dier(String soort, double x, double y, double z, float yaw, boolean stil) {}
    final List<Dier> dieren = new ArrayList<>();

    Plan(int g, int straal, int hoogte) {
        this.g = g;
        x1 = -straal;
        x2 = straal - 1;
        z1 = -straal;
        z2 = straal - 1;
        y1 = g - 3;
        y2 = g + hoogte;
        bx = x2 - x1 + 1;
        by = y2 - y1 + 1;
        bz = z2 - z1 + 1;
        cel = new short[bx * by * bz];
        palet.add("air");
        index.put("air", (short) 0);
        // ondergrond: aarde, gras op g
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                zet(x, g - 2, z, "dirt");
                zet(x, g - 1, z, "dirt");
                zet(x, g, z, "grass_block");
            }
    }

    boolean binnen(int x, int y, int z) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    private int pos(int x, int y, int z) {
        return ((x - x1) * bz + (z - z1)) * by + (y - y1);
    }

    void zet(int x, int y, int z, String blok) {
        if (!binnen(x, y, z)) return;
        String b = blok.startsWith("minecraft:") ? blok.substring(10) : blok;
        Short i = index.get(b);
        if (i == null) {
            i = (short) palet.size();
            palet.add(b);
            index.put(b, i);
        }
        cel[pos(x, y, z)] = i;
    }

    void vul(int xa, int ya, int za, int xb, int yb, int zb, String blok) {
        for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++)
            for (int y = Math.min(ya, yb); y <= Math.max(ya, yb); y++)
                for (int z = Math.min(za, zb); z <= Math.max(za, zb); z++) zet(x, y, z, blok);
    }

    /** Bloktoestand op een plek ("air" buiten de doos boven de grond). */
    String get(int x, int y, int z) {
        if (!binnen(x, y, z)) return y < g ? "dirt" : y == g ? "grass_block" : "air";
        return palet.get(cel[pos(x, y, z)]);
    }

    /** Alleen de bloknaam, zonder [toestand]. */
    String naam(int x, int y, int z) {
        String s = get(x, y, z);
        int k = s.indexOf('[');
        return k < 0 ? s : s.substring(0, k);
    }

    boolean lucht(int x, int y, int z) {
        String n = naam(x, y, z);
        return n.equals("air") || n.equals("light") || n.equals("cave_air");
    }

    /** Kun je hier doorheen lopen (lucht, bloemen, gras, licht)? */
    boolean doorloopbaar(int x, int y, int z) {
        return doorloopbaar(naam(x, y, z));
    }

    static boolean doorloopbaar(String n) {
        return n.equals("air") || n.equals("light") || n.endsWith("_tulip") || n.equals("short_grass") || n.equals("tall_grass")
                || n.equals("fern") || n.equals("large_fern") || n.equals("poppy") || n.equals("dandelion") || n.equals("cornflower")
                || n.equals("oxeye_daisy") || n.equals("allium") || n.equals("azure_bluet") || n.equals("lily_of_the_valley")
                || n.equals("blue_orchid") || n.equals("pink_petals") || n.equals("wildflowers") || n.equals("leaf_litter")
                || n.equals("short_dry_grass") || n.equals("bush") || n.equals("sunflower") || n.equals("lilac") || n.equals("rose_bush")
                || n.equals("peony") || n.equals("sweet_berry_bush") || n.equals("wheat") || n.equals("carrots") || n.equals("potatoes")
                || n.equals("beetroots") || n.equals("torchflower") || n.equals("vine") || n.equals("glow_lichen") || n.equals("sugar_cane");
    }

    List<String> palet() {
        return palet;
    }

    /** Paletnummer per cel, voor de bouwer (zelfde volgorde als {@link #pos}). */
    int paletIndex(int x, int y, int z) {
        return cel[pos(x, y, z)];
    }

    void bord(int x, int y, int z, String... regels) {
        String[] r = new String[4];
        for (int i = 0; i < 4; i++) r[i] = i < regels.length ? regels[i] : "";
        borden.add(new Bord(x, y, z, r));
    }

    void label(double x, double y, double z, String tekst, float schaal) {
        labels.add(new Label(x, y, z, tekst, schaal));
    }
}
