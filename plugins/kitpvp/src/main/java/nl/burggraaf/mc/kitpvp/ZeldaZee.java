package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.plugin.Plugin;

/**
 * De Glazen Zee: de overtocht tussen de eilanden. Een stuk open zee (x 1840 … 2040, z −510 … −330, ver van de eilanden)
 * met de Zilvermeeuw erop, onder volle zeilen naar het oosten. Wie vaart (kaart, vak 9, een ander eiland), staat een
 * paar tellen aan dek (zeemansmuziek, Brecht aan het roer, Wiske praat) en stapt dan uit bij de steiger van het eiland.
 * Geen rustplaatsen: dit is geen eiland. Bouwdeel {@code zelda-zee}.
 */
final class ZeldaZee extends ZeldaHoofdstuk {

    static final int VERSIE = 1;
    static final int X1 = 1840, X2 = 2040, Z1 = -510, Z2 = -330;
    /** Midden van het schip (achtersteven op X0, boeg op X0 + 29), dek op G + 1. */
    static final int X0 = 1926, MZ = -420;

    private final Plugin plugin;
    private Bouwer b;
    int G;
    /** Plekken aan dek waar je staat tijdens de overtocht. */
    final List<Location> dek = new ArrayList<>();
    Box schip;

    ZeldaZee(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override String id() { return "zee"; }
    @Override String naam() { return "De Glazen Zee"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda-zee"; }

    @Override
    long hemel(Location l) {
        return 1200;                                            // vroeg in de ochtend: de Zilvermeeuw vaart bij het eerste licht
    }

    @Override
    String plekNaam(Location l) {
        return "Op zee";
    }

    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return ZeldaMuziek.ZEE;
    }

    private void zet(int x, int y, int z, Material m) {
        b.zet(x, y, z, m);
    }

    private void zet(int x, int y, int z, BlockData d) {
        b.zet(x, y, z, d);
    }

    private void vul(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        b.vul(x1, y1, z1, x2, y2, z2, m);
    }

    private static BlockData stam(Material m, org.bukkit.Axis as) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable o) o.setAxis(as);
        return d;
    }

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        G = bouwer.g + 8;
        leeg(bouwer.w);
        dek.clear();
        int dekY = G + 1;
        speelgebied = Box.van(X0 - 4, G - 6, MZ - 8, X0 + 36, G + 30, MZ + 8);
        schip = Box.van(X0, G - 3, MZ - 5, X0 + 29, G + 20, MZ + 5);
        List<long[]> chunks = new ArrayList<>();
        if (bouwer.plaats) {
            for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(X2, 16); cx++)
                for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) chunks.add(new long[]{cx, cz});
            b.daarna(() -> chunks.forEach(c -> w.addPluginChunkTicket((int) c[0], (int) c[1], plugin)));
            vul(X1, bouwer.g + 1, Z1, X2, G - 6, Z2, Material.STONE);
            vul(X1, G - 5, Z1, X2, G - 5, Z2, Material.SAND);
            vul(X1, G - 4, Z1, X2, G - 1, Z2, Material.WATER);
            vul(X1, G, Z1, X2, G + 24, Z2, Material.AIR);
            romp(dekY);
            b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
        }
        for (int[] d : new int[][]{{9, -1}, {11, 1}, {14, -2}, {17, 2}, {23, 0}, {12, -2}})
            dek.add(new Location(w, X0 + d[0] + 0.5, dekY + 1, MZ + d[1] + 0.5, -90f, 0f));
        persoon(new Persoon("Brecht", new Location(w, X0 + 2.5, dekY + 1, MZ + 0.5, -90f, 0f),
                Registry.VILLAGER_PROFESSION.get(org.bukkit.NamespacedKey.minecraft("fisherman")), List.of(
                r("Brecht", "Wind in de zeilen. Zo mag ik het zien."),
                r("Brecht", "Houd je vast, de Glazen Zee is grillig."))));
        Persoon zelda = persoon(new Persoon("Zelda", new Location(w, X0 + 18.5, dekY + 1, MZ - 1.5, -90f, 0f), null, List.of(   // de terugvaart (5b)
                r("Zelda", "De Glazen Zee! Wat heb ik dit gemist."))));
        zelda.soort = org.bukkit.entity.EntityType.MANNEQUIN;
        zelda.uiterlijk = e -> ZeldaEiland5b.zelda(e, false);
        zelda.zichtbaar = s -> s.heeft(ZeldaEiland5b.VRIJ_ZELDA) && !s.heeft("zelda");
        b = null;
    }

    /** De Zilvermeeuw (dezelfde vorm als in de haven van het Lindewoud), boeg naar het oosten. */
    private void romp(int dekY) {
        int a = X0, e = X0 + 29;
        for (int x = a; x <= e; x++) {
            double t = (x - a) / (double) (e - a);
            double breed = 4.2 * Math.min(1, Math.min(t * 3.5 + 0.35, (1 - t) * 2.6 + 0.1));
            int bodem = G - 3 + (t > 0.8 ? (int) ((t - 0.8) * 10) : 0);
            for (int y = bodem; y <= dekY; y++) {
                double wd = breed * (0.55 + 0.45 * (y - bodem) / (double) (dekY - bodem + 0.01));
                int wi = (int) Math.round(wd);
                for (int z = MZ - wi; z <= MZ + wi; z++) {
                    boolean rand = Math.abs(z - MZ) == wi || y == bodem;
                    if (y == dekY) zet(x, y, z, rand ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                    else if (rand) zet(x, y, z, y == G - 1 ? Material.WHITE_TERRACOTTA : Material.DARK_OAK_PLANKS);
                    else zet(x, y, z, Material.AIR);
                }
            }
            int wi = (int) Math.round(breed);
            if (wi >= 1) {
                zet(x, dekY + 1, MZ - wi, Material.DARK_OAK_FENCE);
                zet(x, dekY + 1, MZ + wi, Material.DARK_OAK_FENCE);
            }
        }
        // roer en kajuit op de achtersteven
        vul(a + 1, dekY + 1, MZ - 3, a + 1, dekY + 3, MZ + 3, Material.DARK_OAK_PLANKS);
        vul(a + 1, dekY + 4, MZ - 3, a + 5, dekY + 4, MZ + 3, Material.SPRUCE_SLAB);
        zet(a + 1, dekY + 5, MZ, Material.LANTERN);
        zet(a + 4, dekY + 1, MZ, Material.SPRUCE_FENCE);
        zet(a + 4, dekY + 2, MZ, Bouwer.gericht(Material.SPRUCE_TRAPDOOR, BlockFace.WEST));
        for (int z : new int[]{MZ - 3, MZ + 3}) {
            zet(a + 5, dekY + 1, z, Material.DARK_OAK_FENCE);
            zet(a + 5, dekY + 2, z, Material.DARK_OAK_FENCE);
            zet(a + 5, dekY + 3, z, Material.DARK_OAK_FENCE);
        }
        // masten en bolle zeilen
        for (int[] m : new int[][]{{a + 10, 15}, {a + 20, 12}}) {
            vul(m[0], dekY + 1, MZ, m[0], dekY + m[1], MZ, Material.SPRUCE_LOG);
            for (int z = MZ - 4; z <= MZ + 4; z++) zet(m[0], dekY + m[1] - 2, z, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            for (int y = dekY + 4; y <= dekY + m[1] - 3; y++)
                for (int z = MZ - 4; z <= MZ + 4; z++) {
                    if (z == MZ) continue;
                    boolean buik = Math.abs(z - MZ) <= 2 && y > dekY + 5 && y < dekY + m[1] - 4;
                    Material doek = y == dekY + (m[1] + 2) / 2 ? Material.LIGHT_BLUE_WOOL : Material.WHITE_WOOL;
                    zet(m[0] + (buik ? 1 : 0), y, z, doek);
                }
            zet(m[0], dekY + m[1] + 1, MZ, Material.LIGHT_BLUE_WOOL);
            zet(m[0] + 1, dekY + m[1] + 1, MZ, Material.LIGHT_BLUE_WOOL);
        }
        // boegspriet en lantaarn
        for (int n = 0; n < 5; n++) zet(e + 1 + n, dekY + 1 + n / 2, MZ, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
        zet(e - 1, dekY + 1, MZ, Material.LANTERN);
        // vaten en touw aan dek
        zet(a + 7, dekY + 1, MZ + 3, Material.BARREL);
        zet(a + 7, dekY + 1, MZ - 3, Material.BARREL);
        zet(a + 15, dekY + 1, MZ + 3, Material.BARREL);
        zet(a + 24, dekY + 1, MZ - 2, Material.IRON_CHAIN);
    }
}
