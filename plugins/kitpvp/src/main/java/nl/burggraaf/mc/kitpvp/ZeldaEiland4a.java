package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

/**
 * Eiland 4a, «De Gloedberg»: Sintelgat en de mijnen (zie ZELDA.md). In de Zwarte Baai (zwart zand, de Zilvermeeuw aan de
 * steiger) leg je aan; Sintelgat is het mijnwerkersdorp tegen de berg, de hub: Tijmen (de koelsteen), Meester Okker de
 * smid (zijn esse is koud), Dina (de verdwaalde lorrie), Fenna de koerier (de race over de sporen naar de Laadkade) en
 * oude Kobus (de drie mijnvuren, hoog op de rotsen).
 *
 * <p>De mijn: de Lorriehal (de hub binnen), de Oude Gang (ingestort: een bom), de Schachtput (de klauwhaak) en de
 * Vonkkamer met de <b>vuurstaf</b> van de eerste smeden; drie lampen hoog aan de wanden openen het hek naar beneden. Daar
 * zijn de <b>hete gangen</b>: hitte als rustige druk (de balk onderin), met de koelsteen houd je het even uit, in water koel
 * je af, en bevroren water smelt je met de vuurstaf tot een koelplek. In de Gloedader ligt gloedijzer; een trap omhoog is
 * de korte weg terug naar de Lorriehal. Okker smeedt er (met zijn esse weer aan) het <b>gloedharnas</b> van: daarmee kom
 * je in de gloed bij de kern, door de Askamer (het enige gevecht: drie gemengde golven, met een rustplaats en een bron
 * ervoor) naar de Smidsepoort. Daarachter ligt de Smidse (4b, nog dicht: {@code Zelda.KOMT}). De Asgang leidt naar buiten,
 * naar het Uitzicht (Ida, de klimster van de Witte Toppen), en over het Asveld terug naar het dorp.
 *
 * <p>Zijmissies: de lorrie (Dina), de sporen (Fenna), de mijnvuren (Kobus); op de Witte Toppen smelt de vuurstaf het ijsblok
 * bij Hagen, en Hagen wil een gloedkool van Okker (een boodschap over zee, de beloning is daar). Tot de Smidse er is,
 * bouwt dit deel ook de kegel van de vulkaan in het vak van 4b ({@link #vulkaanErbij}). Rustplaatsen houden hun nummer: zet
 * een nieuwe altijd achteraan.
 */
final class ZeldaEiland4a extends ZeldaHoofdstuk {

    static final int VERSIE = 1;
    /** Het vak van dit halve eiland (west); de Smidse (4b) komt oost ervan, tot {@link #VX2}. */
    static final int X1 = 1060, X2 = 1259, Z1 = 180, Z2 = 480, VX2 = 1400;
    /** Het plein van Sintelgat. */
    static final int PX = 1135, PZ = 330;
    /** De hartstukjes van 4a (de vierde van de Gloedberg is voor 4b) en de vlaggen. */
    static final String STUKJE_LORRIE = "hs:gloedberg-1", STUKJE_SPOREN = "hs:gloedberg-2", STUKJE_VUREN = "hs:gloedberg-3";
    static final String VUURSTAF = "vuurstaf", KOELSTEEN = "koelsteen", GLOEDIJZER = "gloedijzer", GLOEDHARNAS = "harnas3";

    private final Plugin plugin;
    private Bouwer b;
    int G, D, F2;                                       // G = zeeniveau + 1 (je staat op G+1); D = Sintelgat en de bovenmijn (G+2); F2 = de hete gangen (G−5)
    private int g;
    private int[][] top;                                // hoogste blok per kolom (alleen tijdens het bouwen)
    private byte[][] soort;
    private boolean[][] pad;

    private static final byte ZEE = 0, ZAND = 1, AS = 2, DORP = 3, BERG = 4, PAD = 5;

    /** Wat al is uitgehakt (tijdens het bouwen): een latere gang zet daar geen wand (zo maakt de volgorde niet uit). */
    private final List<int[]> holtes = new ArrayList<>();

    /** Wat er alleen tijdens het spel is: de krater (rook), de lava om bij te sputteren. */
    private Location krater;
    private final List<Location> lava = new ArrayList<>();

    ZeldaEiland4a(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override String id() { return "e4a"; }
    @Override String naam() { return "De Gloedberg"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda-e4a"; }

    /** Een hub met wegen in elke volgorde: «opnieuw vanaf hier» vergeet alleen tot de volgende rustplaats. */
    @Override boolean open() { return true; }

    /** Zolang de Smidse (4b) er niet is: «nog even geduld» (de hint en Tijmen). */
    @Override
    String wacht() {
        return "De Smidsepoort gloeit nog dicht. Nog even geduld.";
    }

    // ── tijd, naam en muziek per plek ───────────────────────────────────────────────────────

    /** De dag schuift op met wat je deed: een gouden middag, later de avond; na de Smidsepoort gloeit de berg in de schemer. */
    @Override
    long hemel(Location l, ZeldaVoortgang.Stand s) {
        if (s.heeft(ZeldaEiland4b.NEVELRING)) return 1000;                // de Nevelring is gebroken (4b): de ochtend
        if (s.heeft(id())) return 13000;
        if (s.heeft(GLOEDHARNAS)) return 12300;
        if (s.heeft(VUURSTAF)) return 11300;
        return 10200;
    }

    private static boolean in(double x, double y, double z, int x1, int y1, int z1, int x2, int y2, int z2) {
        return x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1;
    }

    @Override
    String plekNaam(Location l) {
        double x = l.getX(), y = l.getY(), z = l.getZ();
        if (in(x, y, z, 1240, D, 296, 1256, D + 10, 316)) return "De Askamer";
        if (in(x, y, z, 1240, D, 274, 1258, D + 13, 294)) return "De Smidsepoort";
        if (in(x, y, z, 1239, F2, 317, 1255, D + 7, 331)) return "De gloed";
        if (in(x, y, z, 1222, F2, 314, 1238, F2 + 9, 332)) return "De Gloedader";
        if (in(x, y, z, 1239, D, 238, 1245, D + 5, 273)) return "De Asgang";
        if (in(x, y, z, 1209, F2 - 1, 258, 1238, F2 + 6, 334)) return "De hete gangen";
        if (in(x, y, z, 1166, F2 - 2, 259, 1221, D + 10, 365)) return "De mijn";
        if (in(x, y, z, 1232, D - 3, 224, 1252, D + 8, 240)) return "Het Uitzicht";
        if (x <= 1106 && z >= 316 && z <= 356) return "De Zwarte Baai";
        if (Math.hypot(x - PX, z - PZ) <= 36) return "Sintelgat";
        if (z <= 234 && x >= 1122 && x <= 1146) return "De Laadkade";
        if (z <= 300) return "Het Asveld";
        return "De Gloedberg";
    }

    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return switch (plekNaam(l)) {
            case "De Askamer", "De Smidsepoort", "De gloed", "De Gloedader" -> ZeldaMuziek.GLOED;
            case "De mijn", "De hete gangen", "De Asgang" -> ZeldaMuziek.MIJNEN;
            default -> ZeldaMuziek.SINTELGAT;
        };
    }

    // ── hulpjes ─────────────────────────────────────────────────────────────────────────────

    private void zet(int x, int y, int z, Material m) {
        b.zet(x, y, z, m);
    }

    private void zet(int x, int y, int z, BlockData d) {
        b.zet(x, y, z, d);
    }

    private void vul(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        b.vul(x1, y1, z1, x2, y2, z2, m);
    }

    private Location plek(double x, double y, double z, float yaw) {
        return new Location(w, x + 0.5, y, z + 0.5, yaw, 0f);
    }

    private static long h(int x, int z) {
        return Bouwer.hash(x, z);
    }

    private static int kans(int x, int z, int n) {
        return (int) (h(x * 7 + 3, z * 13 - 5) % n);
    }

    /** Zachte ruis 0 … 1 (waarden op een rooster van {@code s}, ertussen lineair). */
    private static double zacht(int x, int z, int s) {
        int gx = Math.floorDiv(x, s), gz = Math.floorDiv(z, s);
        double fx = (x - gx * s) / (double) s, fz = (z - gz * s) / (double) s;
        double a = (h(gx, gz) % 1000) / 1000.0, c = (h(gx + 1, gz) % 1000) / 1000.0;
        double d = (h(gx, gz + 1) % 1000) / 1000.0, e = (h(gx + 1, gz + 1) % 1000) / 1000.0;
        double o = a + (c - a) * fx, n = d + (e - d) * fx;
        return o + (n - o) * fz;
    }

    private static double bult(int x, int z, int cx, int cz, double r) {
        double d2 = (x - cx) * (x - cx) + (double) (z - cz) * (z - cz);
        return Math.exp(-d2 / (2 * r * r));
    }

    private static BlockData stam(Material m, org.bukkit.Axis as) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable o) o.setAxis(as);
        return d;
    }

    static BlockData vuur(Material m, boolean aan) {
        Campfire c = (Campfire) m.createBlockData();
        c.setLit(aan);
        return c;
    }

    private static BlockData spoor(boolean langsX) {
        Rail r = (Rail) Material.RAIL.createBlockData();
        r.setShape(langsX ? Rail.Shape.EAST_WEST : Rail.Shape.NORTH_SOUTH);
        return r;
    }

    private static BlockData tralies(boolean langsZ) {
        return langsZ ? Bouwer.ruit(Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH) : Bouwer.ruit(Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
    }

    private Deur deur(BlockData dicht, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Deur(dicht, Material.AIR.createBlockData()).vlak(x1, y1, z1, x2, y2, z2);
    }

    private void zetDeur(Deur d, boolean open) {
        d.isOpen = open;
        for (int[] c : d.blokken) b.zetAltijd(c[0], c[1], c[2], open ? d.open : d.dicht);
    }

    /** Een bordje met alleen een naam (staand bord, kijkt naar {@code kijk}). */
    private void bord(int x, int y, int z, BlockFace kijk, String tekst) {
        zet(x, y, z, Bouwer.gericht(Material.DARK_OAK_SIGN, kijk));
        if (!b.plaats) return;
        b.daarna(() -> {
            if (!(w.getBlockAt(x, y, z).getState() instanceof org.bukkit.block.Sign s)) return;
            s.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text(tekst));
            s.setWaxed(true);
            s.update(true, false);
        });
    }

    private void lantaarnpaal(int x, int y, int z) {
        zet(x, y, z, Material.DARK_OAK_FENCE);
        zet(x, y + 1, z, Material.DARK_OAK_FENCE);
        zet(x, y + 2, z, Material.LANTERN);
    }

    /** Rustplaats: zielvuur in een kring van steen op hoogte {@code y} (de vloer), met een lantaarnpaal. */
    private void rustvuur(int x, int y, int z, boolean ijzer) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) zet(x + dx, y, z + dz, dx == 0 && dz == 0 ? Material.COBBLED_DEEPSLATE : Material.POLISHED_BLACKSTONE);
        zet(x, y + 1, z, vuur(Material.SOUL_CAMPFIRE, true));
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            zet(x + f.getModX() * 2, y + 1, z + f.getModZ() * 2, Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, f.getOppositeFace()));
        zet(x + 2, y + 1, z - 2, Material.POLISHED_BLACKSTONE_BRICKS);
        zet(x + 2, y + 2, z - 2, ijzer ? Material.IRON_BARS : Material.DARK_OAK_FENCE);   // in de hitte geen hout
        zet(x + 2, y + 3, z - 2, Material.SOUL_LANTERN);
    }

    private static Villager.Profession beroep(String id) {
        return Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(id));
    }

    /** De rots van de berg: zwart en grijs, met hier en daar wat tufsteen. */
    private static Material rots(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 16);
        return k < 5 ? Material.BLACKSTONE : k < 8 ? Material.DEEPSLATE : k < 10 ? Material.BASALT : k < 12 ? Material.TUFF
                : k < 14 ? Material.COBBLED_DEEPSLATE : k < 15 ? Material.SMOOTH_BASALT : Material.GILDED_BLACKSTONE;
    }

    /** De wand van een mijngang: rots, met erts erin (in de hitte meer gloed). */
    private static Material mijnwand(int x, int y, int z, boolean heet) {
        int k = (int) (Bouwer.hash(x * 3 + 1, y, z * 5 - 2) % 40);
        if (k == 0) return Material.DEEPSLATE_COAL_ORE;
        if (k == 1) return Material.DEEPSLATE_IRON_ORE;
        if (k == 2) return Material.DEEPSLATE_COPPER_ORE;
        if (k == 3) return heet ? Material.MAGMA_BLOCK : Material.DEEPSLATE_GOLD_ORE;
        if (k == 4 && heet) return Material.GILDED_BLACKSTONE;
        int r = (int) (Bouwer.hash(x, y, z) % 10);
        return r < 4 ? Material.DEEPSLATE : r < 6 ? Material.COBBLED_DEEPSLATE : r < 8 ? Material.BLACKSTONE : r < 9 ? Material.TUFF : Material.BASALT;
    }

    // ── het terrein ─────────────────────────────────────────────────────────────────────────

    /** Hoe ver je van de zee bent (negatief = zee): een ronde kust om de berg, met de Zwarte Baai in het westen. */
    static double land(int x, int z) {
        double ex = (x - 1229) / 161.0, ez = (z - 330) / 127.0;
        double d = (1 - Math.sqrt(ex * ex + ez * ez)) * 95 + (zacht(x, z, 9) - 0.5) * 7 + (zacht(x + 31, z - 17, 4) - 0.5) * 2;
        return d - 26 * bult(x, z, 1071, 336, 13);
    }

    /**
     * Hoe hoog de berg hier is (boven G), 0 = geen berg: de Asrug (de schouder van de Gloedberg, met de mijn erin: vanaf
     * x 1166 steil omhoog, smal naar de kusten) en de kegel van de vulkaan met de krater (in het vak van 4b, x 1312).
     * Boven de mijn is hij altijd hoog genoeg (de gangen zitten erin).
     */
    static double berg(int x, int z) {
        double rug = 0;
        if (x >= 1166) {
            double basis = 15 + 0.33 * (x - 1166) + (zacht(x + 11, z - 7, 11) - 0.5) * 8 + (zacht(x - 5, z + 3, 5) - 0.5) * 3;
            double val = Math.min(1, Math.min(Math.max(0, (z - 236) / 16.0), Math.max(0, (426 - z) / 16.0)));
            double voet = Math.min(1, (x - 1166) / 4.0);                    // bij Sintelgat: een steile wand
            rug = basis * val * (0.25 + 0.75 * voet) * Math.max(0, Math.min(1, (1300 - x) / 30.0));
        }
        double r = Math.hypot(x - 1312, z - 330), kegel = 0;
        if (r < 125) kegel = 66 * Math.pow(1 - r / 125, 1.2);
        if (r < 10) kegel -= (10 - r) * 1.6;                                 // de krater
        return Math.max(rug, kegel);
    }

    /** Het terras van Sintelgat: binnen de ellips op D, erbuiten in treden omlaag naar het Asveld. */
    private static int terras(int x, int z) {
        double e = Math.hypot((x - 1136) / 33.0, (z - 330) / 31.0);
        return e < 1 ? 2 : e < 1.35 ? (int) Math.round(2 * (1.35 - e) / 0.35) : 0;
    }

    /** Het Uitzicht, waar de Asgang naar buiten komt: een vlak plateau op D, met een trede eromheen. */
    private static int uitzicht(int x, int z) {
        if (x >= 1236 && x <= 1250 && z >= 226 && z <= 237) return 2;
        if (x >= 1234 && x <= 1252 && z >= 224 && z <= 238) return 1;
        return -1;
    }

    /** Hoogte (bovenste blok) en soort van een kolom. */
    private int[] vorm(int x, int z) {
        double d = land(x, z);
        if (d <= 0) return new int[]{G - 2 - (int) Math.min(6, -d / 2.5), ZEE};
        int hoog = Math.max(terras(x, z), (int) Math.round(Math.max(0, Math.min(1, zacht(x, z, 6) * 1.6 - 0.6))));
        byte s = terras(x, z) == 2 ? DORP : AS;
        double bg = berg(x, z);
        if (bg > hoog + 0.5) {
            hoog = (int) Math.round(bg);
            s = BERG;
        }
        int u = uitzicht(x, z);
        if (u >= 0) {
            hoog = u;
            s = AS;
        }
        if (x >= 1088 && x <= 1106 && z >= 326 && z <= 346) {             // de Zwarte Baai: vlak strand
            hoog = 0;
            s = ZAND;
        } else if (d < 4) {                                                  // de kust: strand, of een klif onder de berg
            if (s == BERG) hoog = Math.min(hoog, (int) (d * 5) - 1);
            else {
                hoog = d < 1.8 ? -1 : 0;
                s = ZAND;
            }
        }
        if (s != BERG && s != ZAND && pad[x - X1][z - Z1]) s = PAD;
        return new int[]{G + hoog, s};
    }

    /** Een pad tussen punten (breedte {@code br}): ander oppervlak, en vrij van rotspilaren. */
    private void padLijn(int br, int[]... punten) {
        for (int i = 0; i + 1 < punten.length; i++) {
            int[] a = punten[i], c = punten[i + 1];
            double len = Math.hypot(c[0] - a[0], c[1] - a[1]);
            for (double t = 0; t <= len; t += 0.5) {
                double px = a[0] + (c[0] - a[0]) * t / Math.max(1e-6, len), pz = a[1] + (c[1] - a[1]) * t / Math.max(1e-6, len);
                for (int dx = -br; dx <= br; dx++)
                    for (int dz = -br; dz <= br; dz++) {
                        int x = (int) Math.round(px) + dx, z = (int) Math.round(pz) + dz;
                        if (x >= X1 && x <= X2 && z >= Z1 && z <= Z2 && Math.hypot(dx, dz) <= br + 0.3) pad[x - X1][z - Z1] = true;
                    }
            }
        }
    }

    /** Alle wegen: de baai naar het dorp, het dorp naar de mijn, de sporen naar de Laadkade, het Asveld naar het Uitzicht. */
    private void wegen() {
        padLijn(1, new int[]{1092, 336}, new int[]{1104, 334}, new int[]{1114, 332}, new int[]{1126, 330});
        padLijn(2, new int[]{1144, 330}, new int[]{1170, 330});
        padLijn(1, new int[]{1133, 302}, new int[]{1133, 228});
        padLijn(1, new int[]{1134, 236}, new int[]{1180, 233}, new int[]{1220, 233}, new int[]{1238, 232});
        padLijn(1, new int[]{1136, 340}, new int[]{1137, 358}, new int[]{1132, 380}, new int[]{1124, 398});    // naar het Lavaveld (zuid)
    }

    private void terrein() {
        pad = new boolean[X2 - X1 + 1][Z2 - Z1 + 1];
        wegen();
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                int[] v = vorm(x, z);
                top[x - X1][z - Z1] = v[0];
                soort[x - X1][z - Z1] = (byte) v[1];
            }
        // per chunk: diepe zee in één keer (weinig stappen), de rest kolom voor kolom
        for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(X2, 16); cx++)
            for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) {
                int x1 = Math.max(X1, cx * 16), x2 = Math.min(X2, cx * 16 + 15), z1 = Math.max(Z1, cz * 16), z2 = Math.min(Z2, cz * 16 + 15);
                boolean diep = true;
                for (int x = x1; x <= x2 && diep; x++)
                    for (int z = z1; z <= z2 && diep; z++) diep = soort[x - X1][z - Z1] == ZEE && top[x - X1][z - Z1] == G - 8;
                if (diep) zee(x1, z1, x2, z2);
                else for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) kolom(x, z, top[x - X1][z - Z1], soort[x - X1][z - Z1]);
            }
        // basaltpilaren en keien op het Asveld en het Lavaveld (niet op de wegen, niet in het dorp)
        for (int x = X1 + 3; x <= X2 - 3; x++)
            for (int z = Z1 + 3; z <= Z2 - 3; z++) {
                if (soort[x - X1][z - Z1] != AS || terras(x, z) > 0 || uitzicht(x, z) >= 0 || bijPad(x, z, 2)) continue;
                if (Math.floorMod(x + kans(z, x, 3), 7) != 0 || Math.floorMod(z + kans(x, z, 3), 7) != 0 || kans(x, z, 3) != 0) continue;
                int y = top[x - X1][z - Z1] + 1, n = 1 + kans(x + 3, z, 4);
                for (int i = 0; i < n; i++) zet(x, y + i, z, i == n - 1 && n > 2 ? Material.SMOOTH_BASALT : Material.BASALT);
                if (kans(x, z + 7, 2) == 0) zet(x + 1, y, z, Material.BLACKSTONE);
            }
    }

    private boolean bijPad(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                int ax = x + dx - X1, az = z + dz - Z1;
                if (ax >= 0 && az >= 0 && ax < pad.length && az < pad[0].length && pad[ax][az]) return true;
            }
        return false;
    }

    private void zee(int x1, int z1, int x2, int z2) {
        vul(x1, g + 1, z1, x2, G - 9, z2, Material.DEEPSLATE);
        vul(x1, G - 8, z1, x2, G - 8, z2, Material.GRAVEL);
        vul(x1, G - 7, z1, x2, G - 1, z2, Material.WATER);
        vul(x1, G, z1, x2, G + 72, z2, Material.AIR);
    }

    /** Is het hier steil (een buur minstens vier lager)? */
    private boolean steil(int x, int z, int hg) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int ax = x + d[0] - X1, az = z + d[1] - Z1;
            if (ax >= 0 && az >= 0 && ax < top.length && az < top[0].length && top[ax][az] <= hg - 4) return true;
        }
        return false;
    }

    private void kolom(int x, int z, int hg, byte s) {
        int ymax = G + 72;
        if (s == ZEE) {
            vul(x, g + 1, z, x, hg - 1, z, Material.DEEPSLATE);
            zet(x, hg, z, kans(x, z, 3) == 0 ? Material.TUFF : Material.GRAVEL);
            vul(x, hg + 1, z, x, G - 1, z, Material.WATER);
            vul(x, G, z, x, ymax, z, Material.AIR);
            return;
        }
        vul(x, g + 1, z, x, hg - 3, z, Material.DEEPSLATE);
        vul(x, hg - 2, z, x, hg - 1, z, s == BERG ? Material.BLACKSTONE : Material.TUFF);
        Material opp = switch (s) {
            case ZAND -> hg < G ? (kans(x, z, 3) == 0 ? Material.GRAVEL : Material.TUFF) : kans(x, z, 7) == 0 ? Material.GRAVEL : Material.BLACK_CONCRETE_POWDER;
            case DORP -> kans(x, z, 4) == 0 ? Material.GRAVEL : kans(x, z, 3) == 0 ? Material.PACKED_MUD : Material.COBBLED_DEEPSLATE;
            case PAD -> kans(x, z, 3) == 0 ? Material.COBBLED_DEEPSLATE : Material.GRAVEL;
            case BERG -> {
                if (steil(x, z, hg)) yield kans(x, z, 9) == 0 ? Material.MAGMA_BLOCK : kans(x, z, 2) == 0 ? Material.BASALT : Material.BLACKSTONE;
                yield rots(x, hg, z);
            }
            default -> {                                                   // het Asveld: as en tufsteen
                int k = kans(x, z, 9);
                yield k < 3 ? Material.TUFF : k < 5 ? Material.GRAVEL : k < 6 ? Material.COARSE_DIRT : k < 7 ? Material.LIGHT_GRAY_CONCRETE_POWDER : k < 8 ? Material.BLACKSTONE : Material.DEEPSLATE;
            }
        };
        zet(x, hg, z, opp);
        vul(x, hg + 1, z, x, ymax, z, Material.AIR);
    }

    /**
     * De kegel van de vulkaan in het vak van 4b (x 1260 … 1400), zolang de Smidse er niet is: zee, kust en berg met
     * dezelfde vormen, zodat je vanaf Sintelgat de rokende berg ziet. Per chunk een eigen chunk-ticket (licht). De sessie
     * die 4b bouwt, zet een tweede deel op het eiland: dan bouwt dit deel dit niet meer.
     */
    private void vulkaan() {
        for (int cx = Math.floorDiv(X2 + 1, 16); cx <= Math.floorDiv(VX2, 16); cx++)
            for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) {
                int ccx = cx, ccz = cz;
                b.daarna(() -> w.addPluginChunkTicket(ccx, ccz, plugin));
                int x1 = Math.max(X2 + 1, cx * 16), x2 = Math.min(VX2, cx * 16 + 15), z1 = Math.max(Z1, cz * 16), z2 = Math.min(Z2, cz * 16 + 15);
                boolean diep = true;
                for (int x = x1; x <= x2 && diep; x++)
                    for (int z = z1; z <= z2 && diep; z++) diep = land(x, z) <= -15;
                if (diep) zee(x1, z1, x2, z2);
                else for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) {
                        double d = land(x, z);
                        int hg;
                        byte s;
                        if (d <= 0) {
                            hg = G - 2 - (int) Math.min(6, -d / 2.5);
                            s = ZEE;
                        } else {
                            double bg = berg(x, z);
                            hg = G + (int) Math.round(bg);
                            s = bg > 1 ? BERG : AS;
                            if (d < 4) {
                                hg = s == BERG ? Math.min(hg, G + (int) (d * 5) - 1) : G + (d < 1.8 ? -1 : 0);
                                if (s != BERG) s = ZAND;
                            }
                        }
                        if (s == BERG) {                                       // zonder buren in de tabel: geen «steil»
                            vul(x, g + 1, z, x, hg - 1, z, Material.DEEPSLATE);
                            zet(x, hg, z, Math.hypot(x - 1312, z - 330) < 12 ? Material.MAGMA_BLOCK : rots(x, hg, z));
                            vul(x, hg + 1, z, x, G + 72, z, Material.AIR);
                        } else kolom(x, z, hg, s);
                    }
                b.daarna(() -> w.removePluginChunkTicket(ccx, ccz, plugin));
            }
        // een meertje van lava in de krater (rondom hoger: hij stroomt nergens heen)
        int kx = 1312, kz = 330, ky = G + (int) Math.round(berg(kx, kz));
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) zet(kx + dx, ky, kz + dz, Material.LAVA);
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    /** Bouwt dit deel ook de kegel van de vulkaan (zolang het eiland geen tweede deel heeft: 4b, de Smidse)? */
    boolean vulkaanErbij() {
        return eiland == null || eiland.delen().size() == 1;
    }

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        g = bouwer.g;
        G = g + 8;
        D = G + 2;
        F2 = G - 5;
        leeg(bouwer.w);
        lava.clear();
        holtes.clear();
        speelgebied = Box.van(X1 + 1, g - 4, Z1 + 2, X2, G + 70, Z2 - 2);
        krater = new Location(w, 1312.5, G + berg(1312, 330) + 2, 330.5);
        boolean bouwen = bouwer.plaats;
        List<long[]> chunks = new ArrayList<>();
        if (bouwen) {
            for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(X2, 16); cx++)
                for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) chunks.add(new long[]{cx, cz});
            b.daarna(() -> chunks.forEach(c -> w.addPluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = new int[X2 - X1 + 1][Z2 - Z1 + 1];
            soort = new byte[X2 - X1 + 1][Z2 - Z1 + 1];
            terrein();
            if (vulkaanErbij()) vulkaan();
        }
        baai();
        dorp();
        laadkade();
        mijningang();
        lorriehal();
        oudeGang();
        vonkkamer();
        heteGangen();
        gloedader();
        daaltrap();
        dorpMetVuurstaf();
        gloed();
        askamer();
        smidsepoort();
        asgang();
        zijgang();
        if (bouwen) {
            b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = null;
            soort = null;
            pad = null;
        }
        // de begintoestand van deuren, poorten, ijs en puzzels: altijd (ook zonder herbouw)
        for (Kamer k : kamers) {
            zetDeur(k.in, true);
            zetDeur(k.uit, false);
        }
        for (Uitdaging u : uitdagingen) zetDeur(u.poort, false);
        for (Barst br : barsten) zetDeur(br.muur, false);
        for (Poort po : poorten) zetDeur(po.deur, false);
        for (Smelt sm : smelten) zetDeur(sm.ijs, false);
        for (Reeks r : reeksen) {
            zetDeur(r.deur, false);
            for (int[] d : r.dingen) b.zetAltijd(d[0], d[1], d[2], r.uit);
        }
        motor();
        b = null;
    }

    // ── 1. de Zwarte Baai: de steiger en de Zilvermeeuw ──────────────────────────────────────

    private void baai() {
        if (b.plaats) {
            // de steiger: donker hout op palen, de baai in
            for (int x = 1074; x <= 1092; x++) {
                vul(x, G, 335, x, G, 337, Material.DARK_OAK_PLANKS);
                vul(x, G + 1, 335, x, G + 4, 337, Material.AIR);
                if (x % 4 == 0) for (int z : new int[]{335, 337}) {
                    vul(x, G - 7, z, x, G + 1, z, Material.DARK_OAK_LOG);
                    if (z == 337 && x % 8 == 0) zet(x, G + 2, z, Material.LANTERN);
                }
            }
            vul(1078, G, 338, 1079, G, 338, Material.DARK_OAK_PLANKS);                   // de loopplank naar het schip
            schip(1063, 1083, 342);
            // op het strand: netten, tonnen, een omgekeerde boot van planken
            zet(1093, G + 1, 333, Material.BARREL);
            zet(1094, G + 1, 333, Material.BARREL);
            zet(1093, G + 2, 333, Material.BARREL);
            for (int x = 1097; x <= 1100; x++) zet(x, G + 1, 343, Bouwer.trap(Material.DARK_OAK_STAIRS, BlockFace.NORTH, true));
            lantaarnpaal(1102, G + 1, 338);
            bord(1103, G + 1, 333, BlockFace.EAST, "Sintelgat");
        }
        rustvuur(1097, G, 329, false);
        rust(new Rust("baai", "De Zwarte Baai", plek(1099, G + 1, 334, -90f), Box.van(1088, G - 1, 326, 1106, G + 6, 346), List.of()));
        verhaal(new Verhaal("aankomst4", Box.van(1088, G - 1, 326, 1106, G + 6, 346), List.of(
                r("Wiske", "Zwart zand! En het ruikt hier naar rook."),
                r("Wiske", "Kijk die berg. Hij rookt!"))));
        Persoon brecht = persoon(new Persoon("Brecht", plek(1081, G + 1, 336, 0f), beroep("fisherman"), List.of(
                r("Brecht", "Zeg maar waarheen. De Zilvermeeuw ligt klaar."))));
        brecht.eenmalig("brecht-gloed", null, List.of(
                        r("Brecht", "Hier ruikt alles naar rook. Mijn zeilen worden zwart!"),
                        r("Brecht", "Ik wacht hier op je. Pas op voor die berg.")), null)
                .eenmalig("brecht-poort", s -> s.heeft(id()), List.of(
                        r("Brecht", "De hele berg gloeit vanavond. Dat was jij, hè?")), null)
                .eenmalig("brecht-nevel", s -> s.heeft("baken4"), List.of(
                        r("Brecht", "De nevel in het noorden is weg! Ik zag het vanaf het water."),
                        r("Brecht", "Nachtrots! Zeg maar wanneer, dan varen we.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.varen(p));
        verhaal(new Verhaal("weg4", Box.van(1108, G, 326, 1114, G + 6, 338), List.of(
                r("Wiske", "Huisjes tegen de berg. Daar wonen vast mijnwerkers."))));
    }

    /** De Zilvermeeuw in de baai: boeg naar het westen ({@code boeg}), achtersteven op {@code achter}, midden op {@code mz}. */
    private void schip(int boeg, int achter, int mz) {
        int dek = G;
        for (int x = boeg; x <= achter; x++) {
            double t = (achter - x) / (double) (achter - boeg);         // 0 = achtersteven, 1 = boeg
            double breed = 3.4 * Math.min(1, Math.min(t * 3.5 + 0.35, (1 - t) * 2.6 + 0.1));
            int bodem = G - 3 + (t > 0.8 ? (int) ((t - 0.8) * 10) : 0);
            int wi = (int) Math.round(breed);
            for (int y = bodem; y <= dek; y++)
                for (int z = mz - wi; z <= mz + wi; z++) {
                    boolean rand = Math.abs(z - mz) == wi || y == bodem;
                    if (y == dek) zet(x, y, z, rand ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                    else zet(x, y, z, rand && y == G - 1 ? Material.WHITE_TERRACOTTA : Material.DARK_OAK_PLANKS);
                }
            vul(x, dek + 1, mz - wi + 1, x, dek + 3, mz + wi - 1, Material.AIR);
            if (wi >= 1) {
                zet(x, dek + 1, mz - wi, Material.DARK_OAK_FENCE);
                zet(x, dek + 1, mz + wi, Material.DARK_OAK_FENCE);
            }
        }
        vul(1078, dek + 1, mz - 3, 1079, dek + 1, mz - 3, Material.AIR);              // een opening in de reling bij de loopplank
        for (int[] m : new int[][]{{1068, 13}, {1077, 11}}) {
            vul(m[0], dek + 1, mz, m[0], dek + m[1], mz, Material.SPRUCE_LOG);
            for (int z = mz - 3; z <= mz + 3; z++) zet(m[0], dek + m[1] - 2, z, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            for (int y = dek + 4; y <= dek + m[1] - 3; y++)
                for (int z = mz - 3; z <= mz + 3; z++) if (z != mz) zet(m[0], y, z, y == dek + (m[1] + 2) / 2 ? Material.LIGHT_BLUE_WOOL : Material.WHITE_WOOL);
            zet(m[0], dek + m[1] + 1, mz, Material.LIGHT_BLUE_WOOL);
        }
        for (int n = 0; n < 4; n++) zet(boeg - 1 - n, dek + 1 + n / 2, mz, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
        zet(achter - 1, dek + 1, mz, Material.LANTERN);
        zet(achter - 3, dek + 1, mz + 1, Material.BARREL);
    }

    // ── 2. Sintelgat: het plein, Tijmen, Okker, Dina, Fenna, Kobus ──────────────────────────

    private void dorp() {
        if (b.plaats) {
            // het plein: een ring van gepolijst basalt met een zon van zwarte steen, het monument in het midden
            for (int x = PX - 9; x <= PX + 9; x++)
                for (int z = PZ - 9; z <= PZ + 9; z++) {
                    double r = Math.hypot(x - PX, z - PZ);
                    if (r > 9.3) continue;
                    int ri = (int) Math.round(r);
                    zet(x, D, z, ri % 4 == 0 ? Material.POLISHED_BASALT : (x + z) % 3 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_DEEPSLATE);
                }
            zet(PX, D + 1, PZ, Material.CHISELED_POLISHED_BLACKSTONE);
            zet(PX, D + 2, PZ, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
                zet(PX + f.getModX(), D + 1, PZ + f.getModZ(), Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, f.getOppositeFace()));
            for (int[] l : new int[][]{{1126, 323}, {1144, 323}, {1131, 340}}) lantaarnpaal(l[0], D + 1, l[1]);
            lavaveld();
            smidse();
            huis(1110, 337, 1117, 344, BlockFace.EAST);                   // Dorus en Dina
            huis(1120, 345, 1127, 352, BlockFace.NORTH);
            huis(1140, 346, 1147, 353, BlockFace.NORTH);
            huis(1107, 318, 1114, 325, BlockFace.EAST);
            koerierspost();
            // Tijmens kampje: een luifel, een kijker naar de berg
            int tx = 1127, tz = 320;
            for (int[] p : new int[][]{{tx - 1, tz - 1}, {tx + 2, tz - 1}, {tx - 1, tz + 1}, {tx + 2, tz + 1}}) vul(p[0], D + 1, p[1], p[0], D + 3, p[1], Material.DARK_OAK_FENCE);
            for (int x = tx - 1; x <= tx + 2; x++)
                for (int z = tz - 1; z <= tz + 1; z++) zet(x, D + 4, z, (x + z) % 2 == 0 ? Material.BLUE_WOOL : Material.LIGHT_BLUE_WOOL);
            zet(tx + 2, D + 1, tz - 2, Material.DARK_OAK_FENCE);
            zet(tx + 2, D + 2, tz - 2, Bouwer.gericht(Material.LIGHTNING_ROD, BlockFace.EAST));
            zet(tx - 1, D + 5, tz, Material.END_ROD);
            // Kobus' bankje bij de oostelijke mijnvuurrots
            for (int x = 1145; x <= 1146; x++) zet(x, D + 1, 338, Bouwer.trap(Material.DARK_OAK_STAIRS, BlockFace.SOUTH));
            // bordjes bij de uitgangen
            bord(1126, D + 1, 333, BlockFace.WEST, "De Zwarte Baai");
            bord(1145, D + 1, 327, BlockFace.EAST, "De mijn");
            bord(1131, D + 1, 309, BlockFace.NORTH, "De Laadkade");
            bord(1139, D + 1, 340, BlockFace.SOUTH, "Het Lavaveld");
            // de sporen van de mijn: van het plein naar de ingang
            for (int x = 1145; x <= 1169; x++) zet(x, D + 1, 330, spoor(true));
        }
        rustvuur(1127, D, 335, false);
        rust(new Rust("sintelgat", "Sintelgat", plek(1130, D + 1, 331, -90f), Box.van(1124, D, 321, 1146, D + 6, 340), List.of()));
        bron("bron-sintelgat", plek(1141, D + 1, 335, 0f));
        verhaal(new Verhaal("sintelgat", Box.van(1124, D, 324, 1134, D + 6, 338), List.of(
                r("Wiske", "Sintelgat. Alles is hier zwart van het roet."),
                r("Wiske", "Wie staat daar onder dat afdakje?"))));
        tijmen();
        okker();
        dina();
        fenna();
        kobus();
    }

    /** Een huisje van donker hout op een voet van zwarte steen, met een dak van leisteen en een rokende schoorsteen. */
    private void huis(int x1, int z1, int x2, int z2, BlockFace deur) {
        BlockData raamX = Bouwer.ruit(Material.ORANGE_STAINED_GLASS_PANE, BlockFace.EAST, BlockFace.WEST);
        BlockData raamZ = Bouwer.ruit(Material.ORANGE_STAINED_GLASS_PANE, BlockFace.NORTH, BlockFace.SOUTH);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean muurZ = z == z1 || z == z2, muurX = x == x1 || x == x2;
                zet(x, D, z, Material.DARK_OAK_PLANKS);
                for (int y = D + 1; y <= D + 4; y++) {
                    if (!muurZ && !muurX) {
                        zet(x, y, z, Material.AIR);
                        continue;
                    }
                    boolean hoek = muurZ && muurX;
                    boolean raam = y == D + 2 && !hoek && (muurZ ? (x - x1) % 3 == 2 : (z - z1) % 3 == 2);
                    zet(x, y, z, hoek ? Material.DARK_OAK_LOG.createBlockData() : y == D + 1 ? Material.POLISHED_BLACKSTONE_BRICKS.createBlockData()
                            : raam ? (muurZ ? raamX : raamZ) : Material.DARK_OAK_PLANKS.createBlockData());
                }
            }
        // het dak: treden van leisteen naar de nok (oost-west); de gevels van planken
        int nok = Integer.MIN_VALUE;                                       // de hoogste trede (G is negatief in een vlakke wereld)
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            int y = D + 5 + Math.min(z - (z1 - 1), z2 + 1 - z);
            nok = Math.max(nok, y);
            for (int x = x1 - 1; x <= x2 + 1; x++) zet(x, y, z, Material.DEEPSLATE_TILES);
            for (int x : new int[]{x1, x2}) if (z >= z1 && z <= z2) vul(x, D + 5, z, x, y - 1, z, Material.DARK_OAK_PLANKS);
        }
        // de deur, een schoorsteen, wat spullen
        int dx = (x1 + x2) / 2, dz = (z1 + z2) / 2;
        switch (deur) {
            case EAST -> vul(x2, D + 1, dz, x2, D + 2, dz, Material.AIR);
            case WEST -> vul(x1, D + 1, dz, x1, D + 2, dz, Material.AIR);
            case NORTH -> vul(dx, D + 1, z1, dx, D + 2, z1, Material.AIR);
            default -> vul(dx, D + 1, z2, dx, D + 2, z2, Material.AIR);
        }
        vul(x2 - 1, D + 5, dz + 1, x2 - 1, nok + 1, dz + 1, Material.BRICKS);
        zet(x2 - 1, nok + 2, dz + 1, vuur(Material.CAMPFIRE, true));
        zet(x1 + 1, D + 1, z1 + 1, Material.BARREL);
        zet(x2 - 1, D + 1, z1 + 1, Material.CRAFTING_TABLE);
        zet(dx, D + 4, dz, Bouwer.hangend(Material.LANTERN));
    }

    /** De smidse van Okker: open naar het plein, een grote esse onder een kap (koud), een aambeeld, een slijpsteen. */
    private void smidse() {
        int x1 = 1142, x2 = 1153, z1 = 310, z2 = 321;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean muur = x == x1 || x == x2 || z == z1;
                zet(x, D, z, (x + z) % 2 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.COBBLED_DEEPSLATE);
                for (int y = D + 1; y <= D + 5; y++) zet(x, y, z, muur ? (y <= D + 2 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DARK_OAK_PLANKS) : Material.AIR);
                zet(x, D + 6, z, Material.DEEPSLATE_TILE_SLAB);
            }
        for (int x : new int[]{x1, x2}) vul(x, D + 1, z2, x, D + 5, z2, Material.DARK_OAK_LOG);
        // de esse: een haard van steen, kolen links en rechts, het vuur achterin, een kap en een schoorsteen
        vul(1146, D + 1, 311, 1150, D + 1, 312, Material.STONE_BRICKS);
        zet(1146, D + 2, 311, Material.STONE_BRICKS);
        zet(1150, D + 2, 311, Material.STONE_BRICKS);
        zet(1146, D + 2, 312, Material.STONE_BRICKS);
        zet(1150, D + 2, 312, Material.STONE_BRICKS);
        zet(1147, D + 2, 311, Material.STONE_BRICKS);
        zet(1149, D + 2, 311, Material.STONE_BRICKS);
        vul(1146, D + 3, 311, 1150, D + 3, 311, Material.BRICKS);
        vul(1146, D + 4, 311, 1150, D + 5, 312, Material.BRICKS);
        vul(1148, D + 6, 311, 1148, D + 11, 311, Material.BRICKS);
        zet(1144, D + 1, 314, Bouwer.gericht(Material.ANVIL, BlockFace.EAST));
        zet(1152, D + 1, 312, Material.BLAST_FURNACE);
        zet(1152, D + 1, 314, Material.WATER_CAULDRON);
        zet(1143, D + 1, 312, Bouwer.gericht(Material.GRINDSTONE, BlockFace.NORTH));
        zet(1147, D + 4, 316, Bouwer.hangend(Material.LANTERN));
        for (int x = 1143; x <= 1145; x++) zet(x, D + 3, 311, Bouwer.gericht(Material.ORANGE_WALL_BANNER, BlockFace.SOUTH));
    }

    /** De koerierspost van Fenna: een hutje bij de weg naar de Laadkade, en de startplaat met een hekje. */
    private void koerierspost() {
        for (int x = 1137; x <= 1141; x++)
            for (int z = 300; z <= 305; z++) {
                boolean muur = x == 1137 || x == 1141 || z == 300 || z == 305;
                zet(x, D, z, Material.DARK_OAK_PLANKS);
                for (int y = D + 1; y <= D + 3; y++) zet(x, y, z, muur ? Material.DARK_OAK_PLANKS : Material.AIR);
                zet(x, D + 4, z, Material.DEEPSLATE_TILE_SLAB);
            }
        vul(1137, D + 1, 302, 1137, D + 2, 302, Material.AIR);
        zet(1140, D + 1, 301, Material.BARREL);
        zet(1138, D + 3, 303, Bouwer.hangend(Material.LANTERN));
        zet(1133, D + 1, 304, Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        for (int x : new int[]{1131, 1135}) {
            zet(x, D + 1, 303, Material.DARK_OAK_FENCE);
            zet(x, D + 2, 303, Material.ORANGE_WOOL);
        }
    }

    /** Het Lavaveld (zuid van het dorp): een paar poelen lava met een randje van steen (de lava stroomt nergens heen). */
    private void lavaveld() {
        for (int[] c : new int[][]{{1128, 384}, {1142, 393}, {1117, 405}}) {
            if (c[0] - 2 < X1 || soort[c[0] - X1][c[1] - Z1] != AS) continue;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    int x = c[0] + dx, z = c[1] + dz;
                    boolean rand = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    zet(x, G, z, rand ? Material.BLACKSTONE : Material.LAVA);
                    zet(x, G + 1, z, rand ? Material.BLACKSTONE_WALL : Material.AIR);
                    vul(x, G + 2, z, x, G + 3, z, Material.AIR);
                }
            lava.add(new Location(w, c[0] + 0.5, G + 1, c[1] + 0.5));
        }
    }

    /**
     * Tijmen, de reizende tovenaar (zie ZELDA.md): hij was er weer eerder. Zijn gave hier is de koelsteen: in de hete
     * gangen word je er minder snel warm van.
     */
    private void tijmen() {
        Persoon tijmen = persoon(new Persoon("Tijmen", plek(1128, D + 1, 321, 180f), null, List.of(r("Tijmen", "De sterren zwijgen even."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.eenmalig("tijmen-gloed", null, List.of(
                        r("Tijmen", "Daar ben je. Warm, hè?"),
                        r("Wiske", "Hij is er alwéér! Hoe doet hij dat?"),
                        r("Tijmen", "Een koelsteen, uit het hart van een gletsjer."),
                        r("Tijmen", "Diep in de mijn zul je hem nodig hebben.")), (spel, p, s) -> spel.beloon(p, KOELSTEEN, "Koelsteen", "houdt je koel in de hitte"))
                .eenmalig("tijmen-staf", s -> s.heeft(VUURSTAF), List.of(
                        r("Tijmen", "De vuurstaf van de eerste smeden. Hij past je goed.")), null)
                .eenmalig("tijmen-harnas", s -> s.heeft(GLOEDHARNAS), List.of(
                        r("Tijmen", "Het gloedharnas. Nu deert de gloed je niet.")), null)
                .eenmalig("tijmen-poort", s -> s.heeft(id()), List.of(
                        r("Tijmen", "De Smidsepoort. Daarachter slaapt het laatste baken."),
                        r("Tijmen", "Ik ga vast kijken. Hoe, dat zeg ik niet.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
        praten(tijmen);
    }

    private Persoon okker;

    /** Meester Okker, de smid van Sintelgat: zijn esse is koud. Met vuur en gloedijzer smeedt hij het gloedharnas. */
    private void okker() {
        okker = persoon(new Persoon("Okker", plek(1145, D + 1, 317, 135f), beroep("armorer"), List.of(
                r("Okker", "Heet, hè? Hier word je taai van."))));
        okker.gesprek("okker-schild", s -> s.heeft(ZeldaEiland4b.SCHILDPLAAT) && !s.heeft(ZeldaSpel.GLOEDSCHILD), List.of(   // 4b
                        r("Okker", "Een schild van de eerste smeden! Kapot, maar nog goed."),
                        r("Okker", "Even in het vuur… en op het aambeeld…"),
                        r("Okker", "Hier: het Gloedschild. Vuur ketst er gewoon op af.")), (spel, p, s) -> {
                    p.playSound(p, org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 1.1f);
                    spel.beloon(p, ZeldaSpel.GLOEDSCHILD, "Gloedschild", "vuur ketst erop af");
                })
                .gesprek("okker-harnas", s -> s.heeft(GLOEDIJZER) && esse(s) && !s.heeft(GLOEDHARNAS), List.of(
                        r("Okker", "Gloedijzer én vuur. Even smeden…"),
                        r("Okker", "Hier: het gloedharnas. Zoals de eerste smeden het droegen."),
                        r("Okker", "Achter de Gloedader is het het heetst. Pas op daar.")), (spel, p, s) -> {
                    p.playSound(p, org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 0.9f);
                    spel.beloon(p, GLOEDHARNAS, "Gloedharnas", "tegen de hitte");
                })
                .gesprek("okker-hagen", s -> s.missie(ZeldaEiland3a.GLOEDKOOL) == 1 && s.heeft("g:okker-1"), List.of(
                        r("Okker", "Hagen?! Die ouwe brombeer leeft nog!"),
                        r("Okker", "Hier, een gloedkool. Die gaat nooit uit.")), (spel, p, s) -> {
                    spel.missie(p, ZeldaEiland3a.GLOEDKOOL, 2);
                    spel.beloon(p, "gloedkool", "Gloedkool", "voor Hagen");
                })
                .eenmalig("okker-1", null, List.of(
                        r("Okker", "Een reiziger! Pas op, het is heet hier."),
                        r("Okker", "Mijn esse is koud. De berg ademt slecht sinds de nevel."),
                        r("Okker", "Zonder vuur geen harnas. En zonder harnas niet de diepte in."),
                        r("Okker", "In de oude mijn ligt de vuurstaf van de eerste smeden.")), null)
                .eenmalig("okker-esse", s -> esse(s) && !s.heeft(GLOEDIJZER), List.of(
                        r("Okker", "Het vuur brandt weer! Mooi werk."),
                        r("Okker", "Nu nog gloedijzer, uit de Gloedader. Diep in de mijn.")), null)
                .eenmalig("okker-ijzer", s -> s.heeft(GLOEDIJZER) && !esse(s), List.of(
                        r("Okker", "Gloedijzer! Maar mijn esse is nog koud."),
                        r("Okker", "Steek hem aan met die staf van je.")), null)
                .eenmalig("okker-asgrim", s -> s.heeft("k:smidshart"), List.of(
                        r("Okker", "Asgrim is vrij? Dan gloeit de Smidse weer!")), null)
                .eenmalig("okker-as", s -> s.heeft("k:askamer"), List.of(
                        r("Okker", "Je stond voor de Smidsepoort? Daar smeedden de eersten."),
                        r("Okker", "Asgrim, de Gloedwachter, laat niemand binnen.")), null);
        praten(okker);
    }

    /** Brandt de esse van Okker voor deze speler (hij stak hem ooit aan; {@code r:esse} vergeet «opnieuw vanaf hier», {@code esse} niet)? */
    static boolean esse(ZeldaVoortgang.Stand s) {
        return s.heeft("esse") || s.heeft("r:esse");
    }

    /** Dina, mijnwerker: hun lorrie vol erts rolde de Zijgang in, en daar is alles bevroren (zijmissie, een hartstukje). */
    private void dina() {
        Persoon dina = persoon(new Persoon("Dina", plek(1119, D + 1, 340, 90f), beroep("toolsmith"), List.of(
                r("Dina", "Erts, erts, erts. Daar draait het om in Sintelgat."))));
        stukje(STUKJE_LORRIE);
        missie("lorrie", "De verdwaalde lorrie", "Zoek de lorrie van Dina, in de Zijgang van de mijn.", "Vertel Dina dat je hem vond.");
        dina.eenmalig("dina-joppe", s -> s.heeft("vrij:joppe"), List.of(              // eerst: haar zoon is terug (van Nachtrots, 5a)
                        r("Dina", "Joppe! Daar ben je!"),
                        r("Dina", "Ik zocht je overal in de mijn."),
                        r("Dina", "Dank je. Hier, eet een warm broodje.")), (spel, p, s) -> spel.genees(p))
                .eenmalig("dina-1", null, List.of(
                        r("Dina", "Ben jij die reiziger? Ik ben Dina."),
                        r("Dina", "Onze lorrie rolde de Zijgang in. Vol erts!"),
                        r("Dina", "Daar is nu alles bevroren. Rare boel, in een warme mijn.")), (spel, p, s) -> spel.missie(p, "lorrie", s.heeft("lorrie") ? 2 : 1))
                .gesprek("dina-2", s -> s.missie("lorrie") == 2, List.of(
                        r("Dina", "Je vond hem! Dorus duwt hem wel terug."),
                        r("Dina", "Dit lag tussen het erts. Voor jou.")), (spel, p, s) -> {
                    spel.missie(p, "lorrie", ZeldaVoortgang.KLAAR);
                    spel.beloon(p, STUKJE_LORRIE, "Hartstukje", null);
                })
                .gesprek(null, s -> s.missie("lorrie") == 1, List.of(r("Dina", "De Zijgang is ten zuiden van de Lorriehal.")), null);
        Persoon joppe = persoon(new Persoon("Joppe", plek(1120, D + 1, 342, 90f), null, List.of(r("Joppe", "Mama zegt: nooit meer alleen naar het strand."))));
        joppe.kind = true;
        joppe.zichtbaar = s -> s.heeft("vrij:joppe");                   // de zoon van Dina, die op Nachtrots in een cel zat
    }

    /** Fenna de koerier: elke dag rent ze over de sporen naar de Laadkade (een tijdrace, een hartstukje). */
    private void fenna() {
        verhaal(new Verhaal("uitleg-sporen", Box.van(1130, D, 306, 1136, D + 4, 309), List.of(
                r("Wiske", "Stap op de plaat, dan gaat het hekje open.")))).uitleg = true;
        Uitdaging race = uitdaging(new Uitdaging("sporen", "De sporen", Box.van(1132, D, 303, 1134, D + 3, 305),
                Box.van(1130, G, 221, 1136, G + 4, 226), Box.van(1124, G - 2, 208, 1144, D + 8, 310),
                deur(Bouwer.ruit(Material.DARK_OAK_FENCE, BlockFace.EAST, BlockFace.WEST), 1132, D + 1, 302, 1134, D + 1, 302), 20));
        race.bord = new Location(w, 1130.5, D + 3.4, 306.5);
        race.daarna = (spel, p, s) -> {
            if (s.missie("sporen") == 1) spel.missie(p, "sporen", 2);
        };
        Persoon fenna = persoon(new Persoon("Fenna", plek(1136, D + 1, 307, 90f), beroep("cartographer"), List.of(
                r("Fenna", "Nog een keer? Je tijd staat op het bord."))));
        stukje(STUKJE_SPOREN);
        missie("sporen", "De sporen", "Ren over de sporen naar de Laadkade: stap op de plaat bij Fenna.", "Vertel Fenna dat het gelukt is.");
        fenna.eenmalig("fenna-1", null, List.of(
                        r("Fenna", "Hoi! Ik ben Fenna, de koerier."),
                        r("Fenna", "Elke dag ren ik over de sporen naar de Laadkade."),
                        r("Fenna", "Haal jij het in twintig tellen? Stap op de plaat!")), (spel, p, s) -> spel.missie(p, "sporen", s.beste("sporen") != null ? 2 : 1))
                .gesprek("fenna-2", s -> s.missie("sporen") == 2, List.of(
                        r("Fenna", "Snel, zeg! Sneller dan ik."),
                        r("Fenna", "Hier, dit vond ik ooit op de kade. Voor jou.")), (spel, p, s) -> {
                    spel.missie(p, "sporen", ZeldaVoortgang.KLAAR);
                    spel.beloon(p, STUKJE_SPOREN, "Hartstukje", null);
                })
                .gesprek(null, s -> s.missie("sporen") == 1, List.of(r("Fenna", "Stap op de plaat. En rennen!")), null);
    }

    /** Oude Kobus, mijnwerker: de drie mijnvuren op de rotsen zijn gedoofd (zijmissie met de vuurstaf, een hartstukje). */
    private void kobus() {
        Persoon kobus = persoon(new Persoon("Kobus", plek(1147, D + 1, 338, 180f), beroep("mason"), List.of(
                r("Kobus", "Zestig jaar in de mijn. Ik ken elke steen."))));
        stukje(STUKJE_VUREN);
        missie("mijnvuren", "De mijnvuren", "Steek de drie mijnvuren aan, hoog op de rotsen bij het plein.", "Vertel Kobus dat ze branden.");
        kobus.gesprek("kobus-2", s -> s.missie("mijnvuren") == 2, List.of(
                        r("Kobus", "Ze branden weer! Dat ik dat nog zie."),
                        r("Kobus", "Hier, van mijn opa. Voor jou.")), (spel, p, s) -> {
                    spel.missie(p, "mijnvuren", ZeldaVoortgang.KLAAR);
                    spel.beloon(p, STUKJE_VUREN, "Hartstukje", null);
                })
                .eenmalig("kobus-1", null, List.of(
                        r("Kobus", "Drie vuren brandden hier, één voor elke mijn."),
                        r("Kobus", "Ze gingen uit toen de nevel kwam."),
                        r("Kobus", "Kon ik er maar weer bij. Ze staan zo hoog.")), (spel, p, s) -> spel.missie(p, "mijnvuren", s.heeft("r:mijnvuren") ? 2 : 1))
                .eenmalig("kobus-staf", s -> s.heeft(VUURSTAF), List.of(
                        r("Kobus", "Een vuurstaf! Daarmee kom je overal bij."),
                        r("Kobus", "Diep in de berg ligt de Smidse. Al eeuwen dicht.")), null)
                .eenmalig("kobus-asgrim", s -> s.heeft("k:smidshart"), List.of(
                        r("Kobus", "Asgrim, vrij? Mijn opa zei: hij kan niet tegen water."),
                        r("Kobus", "Een beetje laat, hè, dat ik dat nu pas zeg.")), null)
                .eenmalig("kobus-as", s -> s.heeft("k:askamer"), List.of(
                        r("Kobus", "Je zag de Smidsepoort? Mijn opa zag hem ook, één keer.")), null);
    }

    // ── 3. de Laadkade (de finish van de sporen) ────────────────────────────────────────────

    private void laadkade() {
        if (b.plaats) {
            for (int x = 1126; x <= 1142; x++)
                for (int z = 210; z <= 233; z++) {
                    zet(x, G, z, (x + z) % 4 == 0 ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS);
                    vul(x, G + 1, z, x, G + 4, z, Material.AIR);
                    if ((x == 1126 || x == 1142 || z == 210) && (x + z) % 4 == 0) vul(x, G - 7, z, x, G + 1, z, Material.DARK_OAK_LOG);
                }
            // kisten erts, een kraan, vlaggetjes bij de finish
            for (int[] k : new int[][]{{1128, 224}, {1128, 225}, {1129, 224}, {1140, 216}, {1140, 217}}) zet(k[0], G + 1, k[1], Material.BARREL);
            zet(1128, G + 2, 224, Material.BARREL);
            vul(1139, G + 1, 220, 1139, G + 7, 220, Material.DARK_OAK_LOG);
            for (int z = 216; z <= 220; z++) zet(1139, G + 8, z, stam(Material.DARK_OAK_LOG, org.bukkit.Axis.Z));
            vul(1139, G + 4, 216, 1139, G + 7, 216, Material.IRON_CHAIN);
            zet(1139, G + 3, 216, Material.RAW_IRON_BLOCK);
            for (int x : new int[]{1129, 1137}) {
                zet(x, G + 1, 223, Material.DARK_OAK_FENCE);
                zet(x, G + 2, 223, Material.ORANGE_WOOL);
            }
            // de sporen van de koerier: recht van het dorp naar het eind van de kade
            for (int z = 214; z <= 301; z++) {
                int y = z <= 233 ? G : Math.max(G, top[1133 - X1][z - Z1]);
                zet(1133, y + 1, z, spoor(false));
            }
        }
        verhaal(new Verhaal("laadkade", Box.van(1126, G, 221, 1142, G + 5, 233), List.of(
                r("Wiske", "De Laadkade. Hier gaat het erts de zee over."))));
    }

    // ── 4. de mijn: de ingang en de Lorriehal (de hub binnen) ────────────────────────────────

    /** Hier is uitgehakt (lucht): een latere gang zet hier geen wand. */
    private void holte(int x1, int y1, int z1, int x2, int y2, int z2) {
        holtes.add(new int[]{Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)});
    }

    private boolean uitgehakt(int x, int y, int z) {
        for (int[] h : holtes) if (x >= h[0] && x <= h[3] && y >= h[1] && y <= h[4] && z >= h[2] && z <= h[5]) return true;
        return false;
    }

    /**
     * Een mijngang uithakken: lucht van {@code y + 1} tot {@code y + hoog}, een vloer op {@code y}, de wanden bekleed (niet
     * waar al eerder is uitgehakt: dan loopt de gang erin door).
     */
    private void gang(int x1, int z1, int x2, int z2, int y, int hoog, boolean heet) {
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean muur = x < x1 || x > x2 || z < z1 || z > z2;
                if (muur) {
                    for (int yy = y; yy <= y + hoog + 1; yy++) if (!uitgehakt(x, yy, z)) zet(x, yy, z, mijnwand(x, yy, z, heet));
                    continue;
                }
                zet(x, y, z, kans(x, z, 5) == 0 ? Material.GRAVEL : heet ? (kans(x + 1, z, 3) == 0 ? Material.BASALT : Material.BLACKSTONE)
                        : kans(x + 1, z, 3) == 0 ? Material.COBBLED_DEEPSLATE : Material.DEEPSLATE);
                vul(x, y + 1, z, x, y + hoog, z, Material.AIR);
                if (!uitgehakt(x, y + hoog + 1, z)) zet(x, y + hoog + 1, z, mijnwand(x, y + hoog + 1, z, heet));
            }
        holte(x1, y + 1, z1, x2, y + hoog, z2);
    }

    /** Een stempel (een raam van balken tegen het instorten) dwars op een gang: palen op {@code a} en {@code c}, een balk erop. */
    private void stempel(int x, int z, boolean dwarsX, int a, int c, int y, int hoog, boolean heet) {
        Material paal = heet ? Material.POLISHED_BASALT : Material.DARK_OAK_LOG;
        Material balk = heet ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DARK_OAK_LOG;
        for (int i : new int[]{a, c}) vul(dwarsX ? i : x, y + 1, dwarsX ? z : i, dwarsX ? i : x, y + hoog, dwarsX ? z : i, paal);
        for (int i = a; i <= c; i++) zet(dwarsX ? i : x, y + hoog, dwarsX ? z : i, heet ? balk.createBlockData() : stam(balk, dwarsX ? org.bukkit.Axis.X : org.bukkit.Axis.Z));
    }

    private void mijningang() {
        if (b.plaats) {
            gang(1166, 328, 1185, 332, D, 4, false);
            vul(1165, D + 1, 328, 1165, D + 4, 332, Material.AIR);                // de mond van de mijn (een gang heeft wanden aan alle kanten)
            vul(1165, D, 328, 1165, D, 332, Material.GRAVEL);
            for (int x = 1165; x <= 1185; x++) zet(x, D + 1, 330, spoor(true));
            for (int x = 1169; x <= 1185; x += 4) stempel(x, 0, false, 328, 332, D, 4, false);
            // de ingang: een raam van zware balken met een bord, lantaarns ernaast
            vul(1167, D + 1, 327, 1167, D + 5, 327, Material.DARK_OAK_LOG);
            vul(1167, D + 1, 333, 1167, D + 5, 333, Material.DARK_OAK_LOG);
            for (int z = 326; z <= 334; z++) zet(1167, D + 6, z, stam(Material.DARK_OAK_LOG, org.bukkit.Axis.Z));
            zet(1166, D + 5, 330, Bouwer.gericht(Material.DARK_OAK_WALL_SIGN, BlockFace.WEST));
            lantaarnpaal(1165, D + 1, 326);
            lantaarnpaal(1165, D + 1, 334);
            for (int x = 1173; x <= 1185; x += 6) zet(x, D + 4, 330, Bouwer.hangend(Material.LANTERN));
            if (b.plaats) b.daarna(() -> {
                if (!(w.getBlockAt(1166, D + 5, 330).getState() instanceof org.bukkit.block.Sign s)) return;
                s.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text("De mijn"));
                s.setWaxed(true);
                s.update(true, false);
            });
        }
        Persoon dorus = persoon(new Persoon("Dorus", plek(1163, D + 1, 334, -90f), beroep("toolsmith"), List.of(
                r("Dorus", "Pas op je hoofd. De balken zijn laag."))));
        dorus.eenmalig("dorus-1", null, List.of(
                        r("Dorus", "Hé! Ik ben Dorus. Hier begint de mijn."),
                        r("Dorus", "Diep erin is het te heet. Zelfs voor mij."),
                        r("Dorus", "Neem water mee. Of iets kouds.")), null)
                .eenmalig("dorus-staf", s -> s.heeft(VUURSTAF), List.of(
                        r("Dorus", "De vuurstaf! Mijn opa vertelde erover.")), null)
                .eenmalig("dorus-harnas", s -> s.heeft(GLOEDHARNAS), List.of(
                        r("Dorus", "Een gloedharnas! Daarmee kom je overal.")), null)
                .eenmalig("dorus-as", s -> s.heeft("k:askamer"), List.of(
                        r("Dorus", "Het is stil in de diepte. Dank je wel.")), null);
        verhaal(new Verhaal("mijningang", Box.van(1168, D, 327, 1176, D + 5, 333), List.of(
                r("Wiske", "De mijn. Ik hoor iets druppelen."))));
    }

    private void lorriehal() {
        int x1 = 1186, x2 = 1206, z1 = 319, z2 = 341;
        if (b.plaats) {
            gang(x1, z1, x2, z2, D, 8, false);
            // de sporen: oost-west door de hal, noord-zuid naar de Oude Gang en de Zijgang
            for (int x = x1; x <= x2; x++) zet(x, D + 1, 330, spoor(true));
            for (int z = z1; z <= z2; z++) if (z < 329 || z > 331) zet(1196, D + 1, z, spoor(false));
            // stempels langs de wanden, een balkenrooster onder het dak, lampen aan kettingen
            for (int x = x1 + 2; x <= x2 - 2; x += 6) {
                vul(x, D + 1, z1, x, D + 8, z1, Material.DARK_OAK_LOG);
                vul(x, D + 1, z2, x, D + 8, z2, Material.DARK_OAK_LOG);
                for (int z = z1; z <= z2; z++) zet(x, D + 8, z, stam(Material.DARK_OAK_LOG, org.bukkit.Axis.Z));
            }
            for (int[] l : new int[][]{{1191, 325}, {1201, 325}, {1191, 335}, {1201, 335}}) {
                zet(l[0], D + 8, l[1], Material.IRON_CHAIN);
                zet(l[0], D + 7, l[1], Material.IRON_CHAIN);
                zet(l[0], D + 6, l[1], Bouwer.hangend(Material.LANTERN));
            }
            // ertshopen, kisten, een werkbank met gereedschap
            for (int[] k : new int[][]{{1203, 321}, {1204, 321}, {1204, 322}, {1188, 321}}) zet(k[0], D + 1, k[1], Material.BARREL);
            zet(1203, D + 2, 321, Material.BARREL);
            for (int[] e : new int[][]{{1189, 339}, {1190, 339}, {1189, 338}}) zet(e[0], D + 1, e[1], kans(e[0], e[1], 2) == 0 ? Material.RAW_IRON_BLOCK : Material.COAL_BLOCK);
            zet(1205, D + 1, 339, Material.CRAFTING_TABLE);
            zet(1205, D + 1, 338, Material.SMITHING_TABLE);
            // de deuren: noord (de Oude Gang), zuid (de Zijgang), oost (de Daaltrap)
            vul(1194, D + 1, z1 - 1, 1198, D + 4, z1 - 1, Material.AIR);
            vul(1194, D + 1, z2 + 1, 1198, D + 4, z2 + 1, Material.AIR);
            vul(x2 + 1, D + 1, 324, x2 + 1, D + 3, 326, Material.AIR);
            bord(1193, D + 1, 320, BlockFace.SOUTH, "De Oude Gang");
            bord(1199, D + 1, 340, BlockFace.NORTH, "De Zijgang");
            bord(1188, D + 1, 327, BlockFace.EAST, "Sintelgat");
        }
        rustvuur(1190, D, 336, false);
        rust(new Rust("lorriehal", "De Lorriehal", plek(1193, D + 1, 333, 90f), Box.van(x1, D, 330, 1199, D + 7, z2), List.of()));
        bron("bron-lorriehal", plek(1202, D + 1, 336, 0f));
        verhaal(new Verhaal("lorriehal", Box.van(1186, D, 326, 1192, D + 6, 334), List.of(
                r("Wiske", "Wat een hal! Sporen naar alle kanten."))));
    }

    // ── 5. de Oude Gang (ingestort: een bom) ─────────────────────────────────────────────────

    private void oudeGang() {
        if (b.plaats) {
            gang(1194, 297, 1198, 318, D, 4, false);
            for (int z = 299; z <= 317; z += 4) stempel(0, z, true, 1194, 1198, D, 4, false);
            for (int z = 297; z <= 317; z++) zet(1196, D + 1, z, spoor(false));
            // puin voor en achter de ingestorte stenen
            for (int[] p : new int[][]{{1194, 308}, {1198, 309}, {1195, 304}, {1197, 305}, {1194, 305}})
                zet(p[0], D + 1, p[1], kans(p[0], p[1], 2) == 0 ? Material.COBBLED_DEEPSLATE : Material.GRAVEL);
            zet(1196, D + 4, 313, Bouwer.hangend(Material.LANTERN));
        }
        verhaal(new Verhaal("oudegang", Box.van(1194, D, 309, 1198, D + 4, 316), List.of(
                r("Wiske", "Ingestort! Maar die stenen zijn gebarsten…")))).als = s -> !s.heeft("b:oudegang");
        Barst muur = barstOpPad(new Barst("oudegang", deur(ZeldaHoofdstuk.BARST.createBlockData(), 1194, D + 1, 307, 1198, D + 4, 307),
                Box.van(1192, D, 302, 1200, D + 6, 313)));
        muur.voor = plek(1196, D + 1, 311, 180f);
    }

    // ── 6. de Schachtput en de Vonkkamer: de klauwhaak, de vuurstaf, de lampen ────────────────

    private void vonkkamer() {
        int x1 = 1188, x2 = 1206, z1 = 259, z2 = 296;
        if (b.plaats) {
            gang(x1, z1, x2, z2, D, 9, false);
            // de put: over de hele breedte, water op de bodem (daar val je zacht), een ladder terug omhoog (zuid)
            for (int x = x1; x <= x2; x++)
                for (int z = 284; z <= 294; z++) {
                    zet(x, G - 6, z, Material.DEEPSLATE);
                    vul(x, G - 5, z, x, G - 4, z, Material.WATER);
                    vul(x, G - 3, z, x, D, z, Material.AIR);
                }
            holte(x1, G - 5, 284, x2, D, 294);
            for (int x = x1 - 1; x <= x2 + 1; x++) for (int y = G - 6; y <= D; y++) {
                zet(x, y, 283, y >= G - 4 ? mijnwand(x, y, 283, false) : Material.DEEPSLATE);
                zet(x, y, 295, y >= G - 4 ? mijnwand(x, y, 295, false) : Material.DEEPSLATE);
            }
            for (int z = 284; z <= 294; z++) for (int y = G - 6; y <= D; y++) {
                zet(x1 - 1, y, z, mijnwand(x1 - 1, y, z, false));
                zet(x2 + 1, y, z, mijnwand(x2 + 1, y, z, false));
            }
            for (int y = G - 4; y <= D; y++) zet(1197, y, 294, Bouwer.gericht(Material.LADDER, BlockFace.NORTH));
            // de balken over de put: noord met het haakpunt, zuid met het haakpunt terug en de derde lamp
            for (int x = x1; x <= x2; x++) {
                zet(x, D + 5, 283, x == 1197 ? Material.LODESTONE.createBlockData() : stam(Material.DARK_OAK_LOG, org.bukkit.Axis.X));
                zet(x, D + 6, 296, x == 1201 ? Material.LODESTONE.createBlockData() : stam(Material.DARK_OAK_LOG, org.bukkit.Axis.X));
            }
            // de Vonkkamer: het altaar van de eerste smeden, twee beelden, banieren, steunen voor de lampen
            zet(1197, D + 1, 261, Material.CHISELED_POLISHED_BLACKSTONE);
            for (BlockFace f : new BlockFace[]{BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH})
                zet(1197 + f.getModX(), D + 1, 261 + f.getModZ(), Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, f.getOppositeFace()));
            for (int x : new int[]{1192, 1202}) {                                       // twee smeden van steen, met een aambeeld
                zet(x, D + 1, 260, Material.POLISHED_BLACKSTONE_BRICKS);
                vul(x, D + 2, 260, x, D + 3, 260, Material.POLISHED_BASALT);
                zet(x, D + 4, 260, Material.CHISELED_POLISHED_BLACKSTONE);
                zet(x, D + 1, 262, Bouwer.gericht(Material.ANVIL, BlockFace.NORTH));
                zet(x, D + 1, 264, vuur(Material.CAMPFIRE, false));                   // koude vuren
            }
            for (int x : new int[]{1194, 1200}) zet(x, D + 5, 259, Bouwer.gericht(Material.ORANGE_WALL_BANNER, BlockFace.SOUTH));
            zet(1188, D + 5, 264, Material.POLISHED_BLACKSTONE_BRICKS);                // de steunen van de lampen
            zet(1206, D + 5, 264, Material.POLISHED_BLACKSTONE_BRICKS);
            for (int[] l : new int[][]{{1193, 270}, {1201, 270}, {1197, 278}}) {
                vul(l[0], D + 8, l[1], l[0], D + 9, l[1], Material.IRON_CHAIN);
                zet(l[0], D + 7, l[1], Bouwer.hangend(Material.LANTERN));
            }
            // het hek naar beneden (oost), en de Vonktrap
            for (int y = D + 1; y <= D + 4; y++) for (int z : new int[]{265, 269}) zet(1207, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
            for (int z = 265; z <= 269; z++) zet(1207, D + 4, z, Material.CHISELED_POLISHED_BLACKSTONE);
        }
        verhaal(new Verhaal("schachtput", Box.van(1192, D, 295, 1200, D + 5, 297), List.of(
                r("Wiske", "Een diepe schacht! Daar spring je niet over."),
                r("Wiske", "Aan de balk aan de overkant: een haakpunt.")))).als = s -> !s.heeft(VUURSTAF);
        Haakpunt over = haakpunt(new Haakpunt("schacht", 1197, D + 5, 283, plek(1197, D + 1, 281, 180f)));
        haakpunt(new Haakpunt("schacht-terug", 1201, D + 6, 296, plek(1201, D + 1, 296, 0f)));
        haak(over, plek(1197, D + 1, 296, 180f));
        rustvuur(1203, D, 273, false);
        rust(new Rust("vonkkamer", "De Vonkkamer", plek(1200, D + 1, 275, 180f), Box.van(1190, D, 266, 1206, D + 7, 282), List.of()));
        verhaal(new Verhaal("vonkkamer", Box.van(1190, D, 274, 1204, D + 6, 282), List.of(
                r("Wiske", "Een oude zaal van de smeden."),
                r("Wiske", "Daar, op het altaar!")))).als = s -> !s.heeft(VUURSTAF);
        Vondst staf = vondst(new Vondst(VUURSTAF, "Vuurstaf", "de staf van de eerste smeden", Material.BLAZE_ROD, plek(1197, D + 2.3, 261, 0f),
                Box.van(1196, D + 1, 260, 1198, D + 3, 263)));
        staf.daarna = (spel, p, s) -> spel.uitleg(p, r("Wiske", "Rechtsklik: een vlam, ver weg. Vak 6."),
                r("Wiske", "Hij steekt vuur aan. En hij smelt ijs!"));
        Verhaal lampen = verhaal(new Verhaal("uitleg-lampen", Box.van(1190, D, 266, 1204, D + 6, 282), List.of(
                r("Wiske", "Kijk, hoog aan de muren: drie lampen."),
                r("Wiske", "Steek ze aan, snel na elkaar!"))));
        lampen.uitleg = true;
        lampen.vereist = VUURSTAF;
        lampen.als = s -> !s.heeft("r:lampen");
        Reeks r = reeks(new Reeks("lampen", "De lampen", Reeks.Hoe.VUUR, vuur(Material.CAMPFIRE, false), vuur(Material.CAMPFIRE, true),
                deur(tralies(true), 1207, D + 1, 266, 1207, D + 3, 268), Box.van(x1, D - 1, z1, x2 + 1, D + 9, z2), 20));
        r.dingen.add(new int[]{1188, D + 6, 264});
        r.dingen.add(new int[]{1206, D + 6, 264});
        r.dingen.add(new int[]{1197, D + 7, 296});
        r.voor = plek(1197, D + 1, 279, 180f);
        r.bijOplossen.add(r("Wiske", "Het hek gaat open! Een trap naar beneden."));
    }

    // ── 7. de hete gangen: de koelsteen, koelplekken, bevroren water ────────────────────────

    private void heteGangen() {
        if (b.plaats) {
            // gang A (zuid), gang B (oost), gang C (zuid, naar de Gloedader)
            gang(1216, 262, 1221, 300, F2, 4, true);
            gang(1216, 296, 1236, 300, F2, 4, true);
            gang(1232, 300, 1236, 313, F2, 4, true);
            // de Vonktrap: van de Vonkkamer (D) omlaag naar de hete gangen (F2), oostwaarts
            for (int x = 1208; x <= 1215; x++) {
                int vloer = x <= 1208 ? D : Math.max(F2, D - (x - 1208));
                for (int z = 265; z <= 269; z++) {
                    boolean muur = z == 265 || z == 269;
                    vul(x, vloer - 1, z, x, D + 5, z, Material.DEEPSLATE);
                    if (muur) continue;
                    zet(x, vloer, z, x == 1208 ? Material.POLISHED_BLACKSTONE_BRICKS.createBlockData() : Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST));
                    vul(x, vloer + 1, z, x, Math.min(D + 4, vloer + 5), z, Material.AIR);
                }
                holte(x, vloer + 1, 266, x, Math.min(D + 4, vloer + 5), 268);
            }
            for (int z = 272; z <= 296; z += 6) stempel(0, z, true, 1216, 1221, F2, 4, true);
            for (int x = 1222; x <= 1236; x += 7) stempel(x, 0, false, 296, 300, F2, 4, true);
            // de eerste koelbak (water, altijd): vlak onder de trap
            for (int x = 1217; x <= 1219; x++)
                for (int z = 270; z <= 272; z++) {
                    zet(x, F2 - 1, z, Material.DEEPSLATE);
                    zet(x, F2, z, Material.WATER);
                }
            // lava achter een muurtje: noord in gang A en oost in gang B (ver van het ijs: ijs smelt bij fel licht)
            for (int z = 274; z <= 280; z++) {
                zet(1221, F2 - 1, z, Material.DEEPSLATE);
                zet(1221, F2, z, Material.LAVA);
                zet(1220, F2 + 1, z, Material.BLACKSTONE_WALL);
                lava.add(new Location(w, 1221.5, F2 + 1, z + 0.5));
            }
            zet(1221, F2 + 1, 273, Material.BLACKSTONE_WALL);                 // ook de uiteinden dicht: niet langs de wand de lava in
            zet(1221, F2 + 1, 281, Material.BLACKSTONE_WALL);
            for (int x = 1236; x <= 1236; x++)
                for (int z = 296; z <= 299; z++) {
                    zet(x, F2 - 1, z, Material.DEEPSLATE);
                    zet(x, F2, z, Material.LAVA);
                    zet(x - 1, F2 + 1, z, Material.BLACKSTONE_WALL);
                }
            zet(1236, F2, 300, Material.BLACKSTONE);
            zet(1236, F2 + 1, 300, Material.BLACKSTONE_WALL);
            // zielslampen (zacht licht: daar smelt geen ijs van)
            for (int[] l : new int[][]{{1218, 263}, {1218, 284}, {1225, 298}, {1234, 306}}) zet(l[0], F2 + 4, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
        }
        lava.add(new Location(w, 1236.5, F2 + 1, 297.5));
        Hitte ht = hitte(new Hitte("hetegangen", "De hete gangen", Box.van(1208, F2 - 1, 256, 1238, F2 + 6, 334), 0.45, 0.11, false));
        ht.terug = new Location(w, 1218.5, F2, 271.5, 180f, 0f);
        ht.proef = plek(1218, F2 + 1, 282, 180f);
        verhaal(new Verhaal("hetegangen", Box.van(1214, F2, 265, 1221, F2 + 4, 269), List.of(
                r("Wiske", "Wat een warmte hier beneden!"),
                r("Wiske", "Gelukkig heb je de koelsteen van Tijmen.")))).als = s -> s.heeft(KOELSTEEN);
        verhaal(new Verhaal("hetegangen-heet", Box.van(1214, F2, 265, 1221, F2 + 4, 269), List.of(
                r("Wiske", "Wat een warmte hier beneden!"),
                r("Wiske", "Zou Tijmen hier iets tegen hebben?")))).als = s -> !s.heeft(KOELSTEEN);
        Verhaal uitleg = verhaal(new Verhaal("uitleg-koel", Box.van(1216, F2, 282, 1221, F2 + 4, 288), List.of(
                r("Wiske", "Bevroren water, in een warme mijn?"),
                r("Wiske", "Smelt het met de vuurstaf. Dan koel je af!"))));
        uitleg.uitleg = true;
        uitleg.als = s -> !s.heeft("s:koel1");
        Smelt koel1 = smeltOpPad(new Smelt("koel1", new Deur(ZeldaHoofdstuk.Smelt.IJS.createBlockData(), Material.WATER.createBlockData())
                .vlak(1217, F2, 290, 1219, F2, 292), Box.van(1215, F2 - 1, 284, 1222, F2 + 5, 295)));
        koel1.voor = plek(1218, F2 + 1, 286, 0f);
        koel1.binnen = plek(1218, F2 + 1, 288, 0f);
        Smelt koel2 = smeltOpPad(new Smelt("koel2", new Deur(ZeldaHoofdstuk.Smelt.IJS.createBlockData(), Material.WATER.createBlockData())
                .vlak(1228, F2, 297, 1230, F2, 299), Box.van(1222, F2 - 1, 295, 1233, F2 + 5, 301)));
        koel2.voor = plek(1224, F2 + 1, 298, -90f);
        koel2.binnen = plek(1226, F2 + 1, 298, -90f);
    }

    // ── 8. de Gloedader: gloedijzer, een rustplaats, de trap terug ───────────────────────────

    private void gloedader() {
        int x1 = 1222, x2 = 1238, z1 = 314, z2 = 332;
        if (b.plaats) {
            gang(x1, z1, x2, z2, F2, 8, true);
            // aders van gloed in de wanden en het plafond (ver van het ijs)
            for (int x = x1 - 1; x <= x2 + 1; x++)
                for (int z = z1 - 1; z <= z2 + 1; z++)
                    for (int y = F2 + 1; y <= F2 + 9; y++) {
                        boolean wand = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1 || y == F2 + 9;
                        if (!wand) continue;
                        double ader = Math.abs(Math.sin((x + y * 0.7) * 0.45) + Math.cos((z - y * 0.5) * 0.38));
                        if (ader < 0.12) zet(x, y, z, kans(x + y, z, 3) == 0 ? Material.SHROOMLIGHT : Material.MAGMA_BLOCK);
                        else if (ader < 0.3) zet(x, y, z, kans(x, z + y, 2) == 0 ? Material.RAW_GOLD_BLOCK : Material.GILDED_BLACKSTONE);
                    }
            // de ader met het gloedijzer (oost), een lavaval achter tralies (zuidoost), een koelbak (noordwest)
            for (int y = F2 + 1; y <= F2 + 4; y++) zet(x2 + 1, y, 316, y == F2 + 2 ? Material.RAW_GOLD_BLOCK : Material.MAGMA_BLOCK);
            zet(x2, F2 + 1, 316, Material.POLISHED_BLACKSTONE_BRICKS);
            for (int y = F2 + 1; y <= F2 + 7; y++) {
                zet(x2 + 2, y, 329, Material.LAVA);
                zet(x2 + 1, y, 329, tralies(true));
            }
            for (int x = 1224; x <= 1226; x++)
                for (int z = 316; z <= 318; z++) {
                    zet(x, F2 - 1, z, Material.DEEPSLATE);
                    zet(x, F2, z, Material.WATER);
                }
            // de ingang uit gang C (noord), de deuren west (de Daaltrap) en oost (de gloed)
            vul(1232, F2 + 1, z1 - 1, 1236, F2 + 4, z1 - 1, Material.AIR);
            vul(x1 - 1, F2 + 1, 324, x1 - 1, F2 + 4, 326, Material.AIR);
            vul(x2 + 1, F2 + 1, 324, x2 + 1, F2 + 4, 326, Material.AIR);
        }
        lava.add(new Location(w, x2 + 1.5, F2 + 2, 329.5));
        verhaal(new Verhaal("gloedader", Box.van(1230, F2, 314, 1237, F2 + 5, 319), List.of(
                r("Wiske", "Kijk hoe de muren gloeien!"),
                r("Wiske", "Daar, in de ader: iets dat gloeit.")))).als = s -> !s.heeft(GLOEDIJZER);
        rustvuur(1226, F2, 328, true);
        rust(new Rust("gloedader", "De Gloedader", plek(1228, F2 + 1, 325, -90f), Box.van(x1, F2, 320, 1233, F2 + 6, z2), List.of()));
        bron("bron-gloedader", plek(1232, F2 + 1, 330, 0f));
        vondst(new Vondst(GLOEDIJZER, "Gloedijzer", "breng het naar Okker", Material.RAW_GOLD, plek(1237, F2 + 2.3, 316, 0f),
                Box.van(1235, F2, 315, 1238, F2 + 3, 317)));
    }

    /** De Daaltrap: van de Gloedader omhoog naar de Lorriehal, een hek dat alleen opengaat voor wie gloedijzer heeft (de korte weg terug). */
    private void daaltrap() {
        if (b.plaats) {
            for (int x = 1207; x <= 1221; x++) {
                int vloer = x <= 1208 ? D : x >= 1215 ? F2 : D - (x - 1208);
                for (int z = 323; z <= 327; z++) {
                    boolean muur = z == 323 || z == 327;
                    if (x == 1207) {
                        if (muur) vul(x, D + 1, z, x, D + 4, z, Material.POLISHED_BLACKSTONE_BRICKS);
                        continue;
                    }
                    vul(x, vloer - 1, z, x, D + 5, z, x >= 1215 ? mijnwand(x, vloer, z, true) : Material.DEEPSLATE);
                    if (muur) continue;
                    zet(x, vloer, z, x > 1208 && x < 1215 ? Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST)
                            : Material.POLISHED_BLACKSTONE_BRICKS.createBlockData());
                    vul(x, vloer + 1, z, x, Math.min(D + 4, vloer + 5), z, Material.AIR);
                }
                if (x > 1207) holte(x, vloer + 1, 324, x, Math.min(D + 4, vloer + 5), 326);
            }
            for (int z = 323; z <= 327; z++) zet(1207, D + 4, z, Material.CHISELED_POLISHED_BLACKSTONE);
            zet(1218, F2 + 4, 325, Bouwer.hangend(Material.SOUL_LANTERN));
        }
        poort(new Poort("daaltrap", deur(tralies(true), 1207, D + 1, 324, 1207, D + 3, 326), Box.van(1202, F2, 322, 1214, D + 5, 328),
                s -> s.heeft(GLOEDIJZER) || s.heeft(GLOEDHARNAS) || s.heeft("k:askamer")));
        verhaal(new Verhaal("daaltrap-dicht", Box.van(1203, D, 323, 1206, D + 4, 327), List.of(
                r("Wiske", "Een hek, en daarachter een trap omlaag."),
                r("Wiske", "Op slot. Van de andere kant gaat het vast open.")))).als = s -> !s.heeft(GLOEDIJZER) && !s.heeft(GLOEDHARNAS);
        verhaal(new Verhaal("daaltrap", Box.van(1209, D - 6, 323, 1214, D + 4, 327), List.of(
                r("Wiske", "Een trap omhoog. Naar de Lorriehal!")))).als = s -> s.heeft(GLOEDIJZER);
    }

    // ── 9. terug in Sintelgat: de esse aansteken, de mijnvuren; Okker smeedt het gloedharnas ─

    private void dorpMetVuurstaf() {
        int[][] rotsen = {{1121, 322}, {1150, 343}, {1136, 314}};
        if (b.plaats)
            for (int[] r : rotsen) {                                            // drie hoge rotsen van basalt, met een vuurschaal erop
                vul(r[0], D + 1, r[1], r[0], D + 8, r[1], Material.BASALT);
                zet(r[0], D + 9, r[1], Material.POLISHED_BLACKSTONE_BRICKS);
                zet(r[0] + 1, D + 1, r[1], Material.BLACKSTONE);
                zet(r[0], D + 1, r[1] + 1, Material.BLACKSTONE);
            }
        Reeks esse = reeks(new Reeks("esse", "De esse", Reeks.Hoe.VUUR, vuur(Material.CAMPFIRE, false), vuur(Material.CAMPFIRE, true),
                new Deur(Material.COAL_BLOCK.createBlockData(), Material.MAGMA_BLOCK.createBlockData()).blok(1147, D + 2, 312).blok(1149, D + 2, 312),
                Box.van(1142, D - 1, 310, 1154, D + 6, 325), 30));
        esse.dingen.add(new int[]{1148, D + 2, 311});
        esse.voor = plek(1148, D + 1, 318, 180f);
        esse.bijOplossen.add(r("Wiske", "De esse brandt weer!"));
        Reeks vuren = reeks(new Reeks("mijnvuren", "De mijnvuren", Reeks.Hoe.VUUR, vuur(Material.CAMPFIRE, false), vuur(Material.CAMPFIRE, true),
                new Deur(vuur(Material.CAMPFIRE, false), vuur(Material.CAMPFIRE, true)).blok(PX, D + 3, PZ), Box.van(1110, D - 2, 305, 1160, D + 14, 356), 40));
        for (int[] r : rotsen) vuren.dingen.add(new int[]{r[0], D + 10, r[1]});
        vuren.voor = plek(PX, D + 1, 334, 180f);
        vuren.bijOplossen.add(r("Wiske", "Alle drie! Kijk, het monument brandt ook."));
        praten(okker);
    }

    // ── 10. de gloed: met het gloedharnas naar de Askamer ───────────────────────────────────

    private void gloed() {
        if (b.plaats) {
            // de Gloedgang: oostwaarts omhoog van de Gloedader (F2) naar D
            for (int x = 1239; x <= 1246; x++) {
                int vloer = x <= 1239 ? F2 : Math.min(D, F2 + (x - 1239));
                for (int z = 323; z <= 327; z++) {
                    boolean muur = z == 323 || z == 327;
                    vul(x, vloer - 1, z, x, D + 6, z, mijnwand(x, vloer, z, true));
                    if (muur) continue;
                    zet(x, vloer, z, x > 1239 && x < 1246 ? Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST)
                            : Material.POLISHED_BLACKSTONE_BRICKS.createBlockData());
                    vul(x, vloer + 1, z, x, Math.min(D + 5, vloer + 5), z, Material.AIR);
                }
                holte(x, vloer + 1, 324, x, Math.min(D + 5, vloer + 5), 326);
            }
            // de gloedtrap: een plek om te rusten voor de Askamer
            gang(1247, 318, 1254, 330, D, 6, true);
            for (int x = 1248; x <= 1253; x++) zet(x, D - 1, 329, Material.DEEPSLATE);
            for (int[] l : new int[][]{{1249, 322}, {1253, 322}}) zet(l[0], D + 6, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
            for (int z = 319; z <= 321; z++) {                                  // lava achter tralies in de oostwand
                zet(1256, D + 2, z, Material.LAVA);
                zet(1255, D + 2, z, tralies(true));
            }
            vul(1247, D + 1, 317, 1249, D + 3, 317, Material.AIR);              // naar de Askamer (noord)
        }
        lava.add(new Location(w, 1255.5, D + 3, 320.5));
        Hitte ht = hitte(new Hitte("gloed", "De gloed", Box.van(1239, F2 - 1, 268, 1259, D + 13, 332), 0.4, 0.4, true));
        ht.terug = plek(1228, F2 + 1, 325, 90f);
        ht.proef = plek(1243, F2 + 5, 325, -90f);
        verhaal(new Verhaal("gloed-harnas", Box.van(1240, F2, 323, 1246, D + 4, 327), List.of(
                r("Wiske", "Het gloedharnas houdt de gloed tegen!")))).vereist = GLOEDHARNAS;
        rustvuur(1252, D, 327, true);
        rust(new Rust("gloedtrap", "De gloedtrap", plek(1250, D + 1, 325, 180f), Box.van(1246, D, 322, 1254, D + 5, 330), List.of()));
        bron("bron-gloedtrap", plek(1248, D + 1, 320, 0f));
    }

    // ── 11. de Askamer: asknechten (het enige gevecht, drie gemengde golven) ────────────────

    private void askamer() {
        int x1 = 1240, x2 = 1256, z1 = 296, z2 = 316;
        if (b.plaats) {
            gang(x1, z1, x2, z2, D, 9, true);
            for (int[] p : new int[][]{{1244, 302}, {1251, 302}, {1244, 309}, {1251, 309}})       // vier pilaren van basalt
                for (int dx = 0; dx <= 1; dx++)
                    for (int dz = 0; dz <= 1; dz++) vul(p[0] + dx, D + 1, p[1] + dz, p[0] + dx, D + 9, p[1] + dz, Material.POLISHED_BASALT);
            for (int[] k : new int[][]{{1241, 297}, {1241, 315}, {1255, 315}}) zet(k[0], D + 1, k[1], Material.SHROOMLIGHT);
            for (int[] l : new int[][]{{1248, 300}, {1248, 312}}) zet(l[0], D + 9, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
            for (int x : new int[]{1243, 1253}) {
                zet(x, D + 5, z1, Bouwer.gericht(Material.ORANGE_WALL_BANNER, BlockFace.SOUTH));
                zet(x, D + 5, z2, Bouwer.gericht(Material.ORANGE_WALL_BANNER, BlockFace.NORTH));
            }
            zet(1255, D + 1, 299, Bouwer.gericht(Material.BELL, BlockFace.WEST));
            vul(1247, D + 1, z1 - 1, 1249, D + 3, z1 - 1, Material.AIR);
        }
        Kamer k = kamer(new Kamer("askamer", "De Askamer", Box.van(x1, D, z1, x2, D + 9, z2), Box.van(1242, D, 298, 1254, D + 5, 314),
                deur(tralies(false), 1247, D + 1, 317, 1249, D + 3, 317), deur(tralies(false), 1247, D + 1, 295, 1249, D + 3, 295)));
        for (int[] sp : new int[][]{{1242, 299}, {1254, 299}, {1242, 313}, {1254, 313}, {1248, 306}, {1248, 300}})
            k.spawns.add(plek(sp[0], D + 1, sp[1], 0f));
        k.golf(Soort.ASKNECHT, Soort.SINTELMIJT)                // balans: langer, lichter, gemengd (Jelle 27-10)
                .golf(Soort.ASKNECHT, Soort.VONKSCHUTTER)
                .golf(Soort.ASKNECHT, Soort.SINTELMIJT, Soort.VONKSCHUTTER)
                .extra(Soort.ASKNECHT, Soort.ASKNECHT, Soort.VONKSCHUTTER, Soort.SINTELMIJT);
        k.bijStart.add(r("Wiske", "Asknechten! Betoverd door de nevel."));
        k.bel = new int[]{1255, D + 1, 299};
        k.tips.addAll(List.of(r("Wiske", "De gloeiklodders zijn snel. Schild omhoog!"),
                r("Wiske", "Bij de trap is een bron. Daar genees je.")));
    }

    // ── 12. de Smidsepoort (het einde van 4a) en de Asgang naar buiten ───────────────────────

    private void smidsepoort() {
        int x1 = 1240, x2 = 1258, z1 = 274, z2 = 294;
        if (b.plaats) {
            gang(x1, z1, x2, z2, D, 12, true);
            // de poort in de oostwand: zwart steen met goud, een naad van gloed, een omlijsting
            for (int z = 277; z <= 291; z++)
                for (int y = D + 1; y <= D + 11; y++) {
                    boolean lijst = z == 277 || z == 291 || y == D + 11;
                    Material m = lijst ? Material.POLISHED_BLACKSTONE_BRICKS : z == 284 ? Material.SHROOMLIGHT
                            : (y + z) % 5 == 0 ? Material.GILDED_BLACKSTONE : Material.CHISELED_POLISHED_BLACKSTONE;
                    zet(x2 + 1, y, z, m);
                }
            for (int z = 279; z <= 289; z += 10) {                              // twee smeden van steen naast de poort
                zet(1256, D + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                vul(1256, D + 2, z, 1256, D + 4, z, Material.POLISHED_BASALT);
                zet(1256, D + 5, z, Material.CHISELED_POLISHED_BLACKSTONE);
                zet(1255, D + 1, z, Bouwer.gericht(Material.ANVIL, BlockFace.WEST));
            }
            for (int x = 1248; x <= 1256; x += 4) {                             // lava achter tralies in de noordwand (niet bij de Asgang)
                zet(x, D + 2, z1 - 2, Material.LAVA);
                zet(x, D + 2, z1 - 1, tralies(false));
            }
            for (int[] l : new int[][]{{1246, 280}, {1246, 288}, {1252, 284}}) {
                vul(l[0], D + 11, l[1], l[0], D + 12, l[1], Material.IRON_CHAIN);
                zet(l[0], D + 10, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
            }
            vul(1240, D + 1, z1 - 1, 1244, D + 4, z1 - 1, Material.AIR);           // naar de Asgang (noordwest)
        }
        for (int x = 1248; x <= 1256; x += 4) lava.add(new Location(w, x + 0.5, D + 3, z1 - 1.5));
        rustvuur(1244, D, 290, true);
        rust(new Rust("smidsepoort", "De Smidsepoort", plek(1246, D + 1, 287, -90f), Box.van(x1, D, 282, 1250, D + 8, z2), List.of()));
        Verhaal einde = verhaal(new Verhaal("smidsepoort", Box.van(1251, D, 278, 1258, D + 6, 290), List.of(
                r("Wiske", "Een poort van gloeiend steen…"),
                r("Wiske", "Daarachter: de Smidse van de eerste smeden."),
                r("Wiske", "En boven op de berg slaapt het Gloedbaken."),
                r(null, "De Gloedberg · Sintelgat en de mijnen voltooid"))));
        einde.vereist = "k:askamer";
        einde.einde = true;
    }

    private void asgang() {
        if (b.plaats) {
            gang(1240, 238, 1244, 273, D, 4, false);
            for (int z = 243; z <= 268; z += 5) stempel(0, z, true, 1240, 1244, D, 4, z > 266);
            for (int z = 246; z <= 266; z += 10) zet(1242, D + 4, z, Bouwer.hangend(Material.LANTERN));
            // de uitgang: een raam van balken op het Uitzicht
            for (int y = D + 1; y <= D + 5; y++) for (int x : new int[]{1239, 1245}) zet(x, y, 237, Material.DARK_OAK_LOG);
            for (int x = 1239; x <= 1245; x++) zet(x, D + 5, 237, stam(Material.DARK_OAK_LOG, org.bukkit.Axis.X));
            vul(1240, D + 1, 237, 1244, D + 4, 237, Material.AIR);
            lantaarnpaal(1238, D + 1, 233);
            bord(1236, D + 1, 230, BlockFace.WEST, "Sintelgat");
        }
        poort(new Poort("asgang", deur(tralies(false), 1240, D + 1, 241, 1244, D + 3, 241), Box.van(1237, D - 1, 234, 1247, D + 5, 247),
                s -> s.heeft("k:askamer")));
        rustvuur(1248, D, 235, false);
        rust(new Rust("uitzicht", "Het Uitzicht", plek(1245, D + 1, 232, 90f), Box.van(1236, D - 1, 226, 1251, D + 6, 238), List.of()));
        verhaal(new Verhaal("uitzicht", Box.van(1238, D, 228, 1250, D + 6, 236), List.of(
                r("Wiske", "Frisse lucht! Nou ja, frisser."),
                r("Wiske", "Over het Asveld kom je zo weer in Sintelgat."))));
        Persoon ida = persoon(new Persoon("Ida", plek(1242, D + 1, 229, 180f), beroep("cartographer"), List.of(
                r("Ida", "Mooi hè, al dat zwart?"))));
        ida.streek = Villager.Type.SNOW;
        ida.eenmalig("ida-gloed", null, List.of(
                        r("Wiske", "Ida! Van de Witte Toppen!"),
                        r("Ida", "Hoi! Ik klim alle bergen. Deze is wel warm."),
                        r("Ida", "Boven is de krater. Er zit iets in dat gloeit.")), null)
                .eenmalig("ida-baken", s -> s.heeft("baken4"), List.of(
                        r("Ida", "Ik zag een vuur boven de krater!"),
                        r("Ida", "En in het noorden: de zwarte nevel is weg.")), null)
                .eenmalig("ida-poort", s -> s.heeft(id()) && !s.heeft("baken4"), List.of(
                        r("Ida", "Een poort in de berg? Die klim ik niet. Jij wel.")), null);
    }

    // ── 13. de Zijgang: de oude koelkelder, bevroren dicht, en de lorrie van Dina ────────────

    private void zijgang() {
        if (b.plaats) {
            gang(1194, 342, 1198, 351, D, 4, false);
            gang(1191, 353, 1201, 364, D, 5, false);
            for (int z = 342; z <= 351; z++) zet(1196, D + 1, z, spoor(false));
            for (int z = 344; z <= 350; z += 6) stempel(0, z, true, 1194, 1198, D, 4, false);
            // de koelkelder: blokken pakijs op planken, sneeuw, zachte zielslampen (geen fel licht: ijs smelt daarvan)
            for (int x = 1191; x <= 1201; x += 10)
                for (int z = 354; z <= 363; z++) {
                    zet(x, D + 1, z, z % 3 == 0 ? Material.BLUE_ICE : Material.PACKED_ICE);
                    zet(x, D + 2, z, Material.SPRUCE_SLAB);
                    zet(x, D + 3, z, z % 2 == 0 ? Material.PACKED_ICE : Material.SNOW_BLOCK);
                }
            for (int x = 1192; x <= 1200; x++) zet(x, D, 364, Material.SNOW_BLOCK);
            for (int z = 354; z <= 362; z++) zet(1196, D + 1, z, spoor(false));
            zet(1196, D + 5, 358, Bouwer.hangend(Material.SOUL_LANTERN));
            zet(1196, D + 4, 345, Bouwer.hangend(Material.SOUL_LANTERN));
        }
        verhaal(new Verhaal("koelgang", Box.van(1194, D, 345, 1198, D + 4, 350), List.of(
                r("Wiske", "Een muur van ijs, in een warme mijn?"),
                r("Wiske", "Hier bewaarden ze vast ijs. Daar kom je zo niet door.")))).als = s -> !s.heeft("s:koelgang") && !s.heeft(VUURSTAF);
        verhaal(new Verhaal("koelgang-staf", Box.van(1194, D, 345, 1198, D + 4, 350), List.of(
                r("Wiske", "Die muur van ijs… Je vuurstaf!")))).als = s -> !s.heeft("s:koelgang") && s.heeft(VUURSTAF);
        Smelt muur = smelt(new Smelt("koelgang", deur(ZeldaHoofdstuk.Smelt.IJS.createBlockData(), 1194, D + 1, 352, 1198, D + 4, 352),
                Box.van(1192, D, 344, 1200, D + 6, 364)));
        muur.voor = plek(1196, D + 1, 347, 0f);
        muur.binnen = plek(1196, D + 1, 349, 0f);
        Vondst lorrie = vondst(new Vondst("lorrie", "De lorrie van Dina", "vol erts", Material.MINECART, plek(1196, D + 1.3, 360, 0f),
                Box.van(1195, D, 359, 1197, D + 2, 361)));
        lorrie.vereist = "g:dina-1";
        lorrie.voorwerp = VUURSTAF;
        lorrie.daarna = (spel, p, s) -> {
            if (s.missie("lorrie") == 1) spel.missie(p, "lorrie", 2);
            spel.vertel(p, r("Wiske", "Dina zal blij zijn!"));
        };
    }

    /**
     * Zonder blokken (dus zonder herbouw): de doelen voor {@code /hint} en Tijmen. Tijmen en Okker, de mijn in (de bom, de
     * haak), de vuurstaf, de lampen; dan in elke volgorde de esse en het gloedijzer; het gloedharnas, de Askamer en de
     * Smidsepoort.
     */
    private void motor() {
        doel("tijmen4", "Een dorp tegen de berg. Daar staat Tijmen!", "Tijmen staat onder zijn afdakje, op het plein van Sintelgat.",
                plekVan("Tijmen"), s -> s.heeft(KOELSTEEN));
        doel("okker4", "Iemand moppert bij de smidse.", "Praat met Meester Okker, in de smidse aan het plein.",
                plekVan("Okker"), s -> s.heeft("g:okker-1") || s.heeft(VUURSTAF));
        doel("oudegang", "De vuurstaf ligt in de oude mijn.", "In de Lorriehal noord: een ingestorte gang. Een bom!",
                plekVan("oudegang"), s -> s.heeft("b:oudegang") || s.heeft(VUURSTAF));
        doel("schacht", "Een diepe schacht. Hoe kom je over?", "Haak je naar de balk aan de overkant.",
                new Location(w, 1197.5, D + 1, 296.5), s -> s.heeft(VUURSTAF) || s.heeft("v:vonkkamer"));
        doel("vuurstaf", "Op het altaar ligt de vuurstaf.", "Pak de vuurstaf in de Vonkkamer.",
                plekVan(VUURSTAF), s -> s.heeft(VUURSTAF));
        doel("lampen", "Drie lampen, hoog en ver weg.", "Richt de vuurstaf op de lampen, snel na elkaar.",
                plekVan("lampen"), s -> s.heeft("r:lampen") || s.heeft(GLOEDIJZER));
        doel("smeden", "esse", "De esse van Okker is koud.", "Richt de vuurstaf op de esse, in de smidse.",
                plekVan("esse"), ZeldaEiland4a::esse);
        doel("smeden", "gloedijzer", "Diep in de mijn ligt gloedijzer.", "Achter de lampen gaat een trap omlaag. Smelt het ijs: daar koel je af.",
                plekVan(GLOEDIJZER), s -> s.heeft(GLOEDIJZER));
        doel("harnas4", "Gloedijzer én vuur. Naar Okker!", "Okker smeedt je een gloedharnas.",
                plekVan("Okker"), s -> s.heeft(GLOEDHARNAS));
        doel("askamer", "Met het gloedharnas kun je de gloed in.", "Achter de Gloedader, oost: de Askamer.",
                plekVan("askamer"), s -> s.heeft("k:askamer"));
        doel("smidsepoort", "Achter de Askamer: een poort.", "Loop door naar de Smidsepoort.",
                plekVan("smidsepoort"), s -> s.heeft(id()));
    }

    // ── tijdens het spel: as, rook uit de krater, lava die sputtert; de mijnvuren voor Kobus ─

    @Override
    void tik(ZeldaSpel spel, List<Player> wie, long nu) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (Player p : wie) {
            if (!p.getWorld().equals(w) || spel.beheer.is(p.getUniqueId())) continue;
            ZeldaVoortgang.Stand s = spel.voortgang.van(p.getUniqueId());
            if (s.missie("mijnvuren") == 1 && s.heeft("r:mijnvuren")) spel.missie(p, "mijnvuren", 2);   // de vuren branden: terug naar Kobus
            if (s.heeft("r:esse") && s.zet("esse")) spel.voortgang.vuil();                               // de esse brandt: dat blijft zo
            Location l = p.getLocation();
            boolean buiten = w.getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) <= l.getBlockY() + 1;
            if (buiten) {
                for (int i = 0; i < 8; i++) p.spawnParticle(Particle.WHITE_ASH, l.clone().add(r.nextDouble(-10, 10), r.nextDouble(2, 8), r.nextDouble(-10, 10)), 2, 0.5, 0.5, 0.5, 0);
                if (krater != null) Particle.CAMPFIRE_SIGNAL_SMOKE.builder().location(krater.clone().add(r.nextDouble(-3, 3), 0, r.nextDouble(-3, 3)))
                        .offset(0.6, 0.4, 0.6).speed(0.02).count(2).receivers(p).force(true).spawn();
            } else {
                for (Location v : lava)                                              // binnen: lava die sputtert, als je in de buurt bent
                    if (v.distanceSquared(l) < 18 * 18 && r.nextInt(3) == 0) {
                        p.spawnParticle(Particle.LAVA, v, 1, 0.3, 0.1, 0.3, 0);
                        if (r.nextInt(4) == 0) p.playSound(v, org.bukkit.Sound.BLOCK_LAVA_POP, org.bukkit.SoundCategory.AMBIENT, 0.4f, 0.9f + r.nextFloat() * 0.3f);
                    }
                if (plekNaam(l).equals("De mijn") && r.nextInt(3) == 0) {           // in de bovenmijn druppelt het
                    Location d = l.clone().add(r.nextDouble(-4, 4), 3.2, r.nextDouble(-4, 4));
                    p.spawnParticle(Particle.FALLING_WATER, d, 1, 0, 0, 0, 0);
                    if (r.nextInt(3) == 0) p.playSound(d, org.bukkit.Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, org.bukkit.SoundCategory.AMBIENT, 0.5f, 0.8f + r.nextFloat() * 0.4f);
                }
            }
        }
    }

    /** Op de kaart: Sintelgat en de mijnen. */
    @Override
    List<Teken> tekens() {
        return List.of(new Teken("dorp-as", 1135, 330), new Teken("kerker", 1166, 330));
    }
}
