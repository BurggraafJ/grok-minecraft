package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Eiland 3a, «De Witte Toppen»: het dal en het dorp (zie ZELDA.md). De Zilvermeeuw legt aan in een fjord; een weg door
 * de sneeuw loopt naar het bergdorp IJshoeve, de hub. Daar wacht Tijmen met een sterrenlantaarn, want in het Sparrenbos
 * raast een sneeuwstorm die een spoor verbergt. Vanuit IJshoeve: west het bevroren meer (Grootvader Ys vist er, Noor
 * schaatst er een rondje om het eilandje), noordwest het Sparrenbos (de storm, de pootjes, de kuil met het sneeuwvosje en
 * twee nevelwolven: het enige gevecht), noord de Uitkijk, oost de weg naar de Bergpoort (de smidse van Hagen ligt eraan).
 *
 * <p>Het vosje loopt mee en graaft bij de rotswand een oude deur van de bergwachters op: de Wachtersgang onder de rots
 * door naar de Wachterspost in de kloof. Daar ligt de klauwhaak; drie haakpunten in de kloofwand zijn de les, en zo kom
 * je boven op de Uitkijk (een rondje terug naar het dorp). Met de klauwhaak haak je je omhoog bij de ingestorte trap van
 * de Bergpoort: daar begint het bergpad naar het IJspaleis (eiland 3b), nu nog dicht door een lawine.
 *
 * <p>Zijmissies: Saar en Sven zoeken hun geiten (Bles, Mekker en Sokje, verspreid over het dal; wie thuis is, staat in de
 * stal), en Ys geeft een brief mee voor zijn oude vriend Teun in Zoutkom (een opdracht terug naar het Zilverstrand; Teun
 * geeft het vierde flesje). Rustplaatsen houden hun nummer: zet een nieuwe altijd achteraan.
 */
final class ZeldaEiland3a extends ZeldaHoofdstuk {

    static final int VERSIE = 1;
    /** Het vak van dit halve eiland (3b, de berg en het IJspaleis, komt oost ervan: x 1580 … 1720). */
    static final int X1 = 1400, X2 = 1579, Z1 = -480, Z2 = -180;
    /** Het plein van IJshoeve, het meer (eilandje in het midden), de kop van de fjord. */
    static final int PX = 1480, PZ = -294, MX = 1432, MZ = -300, EX = 1430, EZ = -300;
    /** De hartstukjes van 3a: de geiten (Saar), verstopt op het eilandje (het vosje), in het ijsblok bij de smidse (vuurstaf). */
    static final String STUKJE_GEITEN = "hs:witte-toppen-1", STUKJE_EILANDJE = "hs:witte-toppen-2", STUKJE_IJS = "hs:witte-toppen-3";
    /** De vlag van de deur die het vosje opgraaft (de Wachtersgang). */
    static final String DEUR = "wachtersdeur";

    private final Plugin plugin;
    private Bouwer b;
    int G;                                              // G = pad-hoogte (je staat op G+1); de zee komt tot G−1
    private int g;
    private int[][] top;                                // hoogste blok per kolom (alleen tijdens het bouwen)
    private byte[][] soort;
    private boolean[][] pad, vrij;                      // een pad (ander oppervlak), vrij van bomen

    private static final byte ZEE = 0, STRAND = 1, SNEEUW = 2, ROTS = 3, PLEIN = 4, MEER = 5, KLOOF = 6, BEEK = 7;
    /** De bevroren beek: van de voet van de berg naar de kop van de fjord (x, z). */
    private static final int[][] BEEKLIJN = {{1549, -266}, {1536, -262}, {1522, -255}, {1509, -251}, {1497, -244}, {1487, -238}, {1479, -231}, {1476, -227}};

    /** De storm in het Sparrenbos: hier sneeuwt het hard; dieper erin verdwaal je zonder licht. */
    private Box storm, diep;
    private Location stormUit;
    /** Het spoor van pootjes in de storm (x, z), van de bosrand naar de kuil. */
    private static final int[][] POOTJES = {{1458, -343}, {1455, -347}, {1452, -351}, {1449, -355}, {1447, -359}, {1444, -362},
            {1441, -365}, {1439, -368}, {1437, -371}};
    private final Map<UUID, Long> verdwaald = new HashMap<>();

    ZeldaEiland3a(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override String id() { return "e3a"; }
    @Override String naam() { return "De Witte Toppen"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda-e3a"; }

    /** Een hub met wegen in elke volgorde: «opnieuw vanaf hier» vergeet alleen tot de volgende rustplaats. */
    @Override boolean open() { return true; }

    /** Het bergpad naar het IJspaleis (3b) ligt nog onder een lawine. */
    @Override String wacht() {
        return "Het bergpad is nog dicht gesneeuwd. Nog even geduld.";
    }

    // ── tijd, naam en muziek per plek ───────────────────────────────────────────────────────

    /** De dag schuift op met wat je deed (een open eiland: je loopt ook terug): vroeg licht, en een gouden middag bij de Bergpoort. */
    @Override
    long hemel(Location l, ZeldaVoortgang.Stand s) {
        if (s.heeft(id())) return 10500;
        if (s.heeft("klauwhaak")) return 7500;
        if (s.heeft("dier:sneeuwvos")) return 5000;
        if (s.heeft("lantaarn")) return 3000;
        return 1200;
    }

    private boolean inGang(Location l) {
        double x = l.getX(), z = l.getZ(), y = l.getY();
        if (x >= 1453 && x <= 1484 && z >= -434 && z <= -424 && y <= G + 4) return true;      // de Wachtersgang
        return x >= 1484 && x <= 1531 && z >= -447 && z <= -430 && y <= G + 3;                  // de kloof met de Wachterspost
    }

    @Override
    String plekNaam(Location l) {
        double x = l.getX(), z = l.getZ();
        if (inGang(l)) return "De Wachterspost";
        if (x >= 1538 && Math.abs(z - PZ) <= 16) return "De Bergpoort";
        if (x >= 1486 && x <= 1550 && z <= -380) return "De Uitkijk";
        if (x <= 1476 && z <= -322 && z >= -426) return "Het Sparrenbos";
        if (Math.hypot((x - MX) / 24.0, (z - MZ) / 18.0) < 1) return "Het meer";
        if (z >= -240) return "De fjord";
        return "IJshoeve";
    }

    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return switch (plekNaam(l)) {
            case "Het Sparrenbos" -> ZeldaMuziek.SNEEUWSTORM;
            case "De Wachterspost", "De Bergpoort" -> ZeldaMuziek.WACHTERSPOST;
            default -> ZeldaMuziek.IJSHOEVE;
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

    private static BlockData stam(Material m, org.bukkit.Axis as) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable o) o.setAxis(as);
        return d;
    }

    private static BlockData vuur(Material m, boolean aan) {
        Campfire c = (Campfire) m.createBlockData();
        c.setLit(aan);
        return c;
    }

    private static BlockData hek(Material m, boolean langsZ) {
        return langsZ ? Bouwer.ruit(m, BlockFace.NORTH, BlockFace.SOUTH) : Bouwer.ruit(m, BlockFace.EAST, BlockFace.WEST);
    }

    private Material steen(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 14);
        return k < 5 ? Material.STONE : k < 7 ? Material.ANDESITE : k < 9 ? Material.TUFF : k < 11 ? Material.CALCITE : k < 13 ? Material.COBBLESTONE : Material.DIORITE;
    }

    /** Oude stenen van de bergwachters (een beetje gevarieerd). */
    private Material wachtersteen(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 10);
        return k < 6 ? Material.STONE_BRICKS : k < 8 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
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
        zet(x, y, z, Bouwer.gericht(Material.SPRUCE_SIGN, kijk));
        if (!b.plaats) return;
        b.daarna(() -> {
            if (!(w.getBlockAt(x, y, z).getState() instanceof org.bukkit.block.Sign s)) return;
            s.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text(tekst));
            s.setWaxed(true);
            s.update(true, false);
        });
    }

    private void lantaarnpaal(int x, int y, int z) {
        zet(x, y, z, Material.SPRUCE_FENCE);
        zet(x, y + 1, z, Material.SPRUCE_FENCE);
        zet(x, y + 2, z, Material.LANTERN);
    }

    /** Rustplaats: zielvuur in een kring van steen op hoogte {@code y} (de vloer), met een lantaarnpaal. */
    private void rustvuur(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) zet(x + dx, y, z + dz, dx == 0 && dz == 0 ? Material.COBBLESTONE : Material.POLISHED_ANDESITE);
        zet(x, y + 1, z, vuur(Material.SOUL_CAMPFIRE, true));
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            zet(x + f.getModX() * 2, y + 1, z + f.getModZ() * 2, Bouwer.trap(Material.STONE_BRICK_STAIRS, f.getOppositeFace()));
        zet(x + 2, y + 1, z - 2, Material.STONE_BRICKS);
        zet(x + 2, y + 2, z - 2, Material.SPRUCE_FENCE);
        zet(x + 2, y + 3, z - 2, Material.SOUL_LANTERN);
    }

    private static Villager.Profession beroep(String id) {
        return Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(id));
    }

    /** Een spar: een stam van {@code hoog} met een kegel van naalden, sneeuw erop. */
    private void spar(int x, int y, int z, int hoog) {
        vul(x, y, z, x, y + hoog - 1, z, Material.SPRUCE_LOG);
        BlockData naald = Bouwer.blad(Material.SPRUCE_LEAVES);
        for (int k = 2; k <= hoog; k++) {
            int r = (int) Math.round((hoog - k) * 0.34 + (k % 2 == 0 ? 0.6 : 0));
            if (k == hoog) r = 0;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r + 1 || (dx == 0 && dz == 0 && k < hoog)) continue;
                    zet(x + dx, y + k, z + dz, naald);
                }
        }
        zet(x, y + hoog + 1, z, naald);
        zet(x, y + hoog + 2, z, Material.SNOW);
    }

    // ── het terrein ─────────────────────────────────────────────────────────────────────────

    private static double ellips(double x, double z, double cx, double cz, double a, double c) {
        return (1 - Math.hypot((x - cx) / a, (z - cz) / c)) * Math.min(a, c);
    }

    /** Hoe ver je op het land bent (negatief = zee): een groot eiland, en in het oosten de berg tot de rand van het vak. */
    private static double land(int x, int z) {
        double d = ellips(x, z, 1478, -330, 74, 142);
        d = Math.max(d, Math.min(x - 1536, Math.min(z + 470, -198 - z)));
        return d + (zacht(x, z, 9) - 0.5) * 6;
    }

    /** Hoe hoog de rots hier is boven G (0 = geen rots): de berg in het oosten, de noordrug, de rotsuitloper, de fjordkliffen. */
    private static int berg(int x, int z) {
        double r = zacht(x + 50, z - 20, 6);
        if (x >= 1550) return (int) Math.min(30, 10 + (x - 1550) * 0.9 + r * 6);
        if (z <= -447) return (int) Math.max(4, 14 + r * 6 - Math.max(0, (1440 - x) * 0.3));
        if (x >= 1440 && x <= 1469 && z <= -425) return (int) (12 + r * 4);
        if (x >= 1470 && x <= 1489 && z <= -404) return (int) (13 + r * 4);
        if (x >= 1531 && z <= -430) return (int) (12 + r * 4);
        if (z >= -226 && z <= -192 && (x >= 1450 && x <= 1463 || x >= 1477 && x <= 1490)) return (int) (4 + (z + 226) / 5.0 + r * 3);
        return 0;
    }

    /** De Uitkijk: een helling vanaf het dorp naar een vlakte op G+6 boven de kloof. */
    private static int heuvel(int x, int z) {
        if (x < 1490 || x > 1549 || z < -429 || z > -345) return 0;
        if (z <= -400) return 6;
        return (int) Math.round(6 * Math.min(1, Math.max(0, (-345 - z) / 55.0)));
    }

    /** Vlakke plekken (x, z, straal): rustplaatsen, personen, het dorp. */
    private static final int[][] VAST = {
            {1466, -231, 7}, {1460, -334, 6}, {1488, -255, 3}, {1418, -317, 3}, {1455, -421, 5}, {1544, -294, 6}, {1444, -364, 3}, {1436, -380, 13}};
    /** Vlakke rechthoeken (x1, z1, x2, z2): het dorp, de weg naar de Bergpoort. */
    private static final int[][] VLAK = {{1450, -322, 1506, -280}, {1490, -298, 1549, -290}};

    private static boolean vast(int x, int z) {
        for (int[] v : VAST) if (Math.hypot(x - v[0], z - v[1]) <= v[2] + 0.4) return true;
        for (int[] v : VLAK) if (x >= v[0] && x <= v[2] && z >= v[1] && z <= v[3]) return true;
        return false;
    }

    private static boolean meer(int x, int z) {
        return Math.hypot((x - MX) / 20.0, (z - MZ) / 14.0) < 1;
    }

    /** Ligt dit in de bevroren beek (twee breed)? */
    private static boolean beek(int x, int z) {
        for (int i = 0; i + 1 < BEEKLIJN.length; i++) {
            int[] a = BEEKLIJN[i], c = BEEKLIJN[i + 1];
            double dx = c[0] - a[0], dz = c[1] - a[1], len2 = dx * dx + dz * dz;
            double t = Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / len2));
            double kx = a[0] + dx * t + (zacht(x, z, 6) - 0.5) * 1.5, kz = a[1] + dz * t;
            if (Math.hypot(x - kx, z - kz) <= 1.1) return true;
        }
        return false;
    }

    private static boolean kloof(int x, int z) {
        return x >= 1484 && x <= 1530 && z >= -446 && z <= -430;
    }

    /** Hoogte (bovenste blok) en soort van een kolom. */
    private int[] vorm(int x, int z) {
        if (x >= 1464 && x <= 1476 && z >= -226) return new int[]{G - 7, ZEE};                // de fjord
        double d = land(x, z);
        if (d <= 0) return new int[]{G - 2 - (int) Math.min(6, -d / 2.5), ZEE};
        if (kloof(x, z)) return new int[]{G - 6, KLOOF};
        int bg = berg(x, z);
        if (bg > 0) return new int[]{G + bg, ROTS};
        if (meer(x, z)) return new int[]{G, MEER};
        if (beek(x, z)) return new int[]{G - 1, BEEK};
        if (Math.hypot(x - PX, z - PZ) <= 10.4) return new int[]{G, PLEIN};
        int hv = heuvel(x, z);
        if (hv > 0) return new int[]{G + hv, SNEEUW};
        if (d < 2.2) return new int[]{G - 1, STRAND};
        if (vast(x, z) || pad[x - X1][z - Z1]) return new int[]{G, SNEEUW};
        return new int[]{G + (int) Math.max(0, Math.min(1, Math.round(zacht(x, z, 5) * 2.2 - 0.9))), SNEEUW};
    }

    /** Een pad tussen punten (breedte {@code br}): ander oppervlak, en vrij van bomen (iets breder). */
    private void padLijn(int br, boolean zichtbaar, int[]... punten) {
        for (int i = 0; i + 1 < punten.length; i++) {
            int[] a = punten[i], c = punten[i + 1];
            double len = Math.hypot(c[0] - a[0], c[1] - a[1]);
            for (double t = 0; t <= len; t += 0.5) {
                double px = a[0] + (c[0] - a[0]) * t / Math.max(1e-6, len), pz = a[1] + (c[1] - a[1]) * t / Math.max(1e-6, len);
                for (int dx = -br - 2; dx <= br + 2; dx++)
                    for (int dz = -br - 2; dz <= br + 2; dz++) {
                        int x = (int) Math.round(px) + dx, z = (int) Math.round(pz) + dz;
                        if (x < X1 || x > X2 || z < Z1 || z > Z2) continue;
                        double r = Math.hypot(dx, dz);
                        if (r <= br + 2.2) vrij[x - X1][z - Z1] = true;
                        if (zichtbaar && r <= br + 0.3) pad[x - X1][z - Z1] = true;
                    }
            }
        }
    }

    /** Alle wegen van het eiland (oppervlak, en geen bomen erop). */
    private void wegen() {
        padLijn(1, true, new int[]{1466, -229}, new int[]{1468, -240}, new int[]{1474, -255}, new int[]{1478, -270}, new int[]{1480, -284});   // steiger → dorp
        padLijn(1, true, new int[]{1470, -294}, new int[]{1453, -294});                                                                         // dorp → meer
        padLijn(1, true, new int[]{1472, -302}, new int[]{1465, -310}, new int[]{1461, -320}, new int[]{1460, -331});                           // dorp → bosrand
        padLijn(1, true, new int[]{1450, -313}, new int[]{1452, -322}, new int[]{1457, -331});                                                  // meer → bosrand (rondje)
        padLijn(1, true, new int[]{1480, -304}, new int[]{1482, -320}, new int[]{1488, -340}, new int[]{1496, -365}, new int[]{1500, -398}, new int[]{1514, -418});  // naar de Uitkijk
        padLijn(2, true, new int[]{1490, -294}, new int[]{1548, -294});                                                                         // de weg naar de Bergpoort
        padLijn(1, false, POOTJES);                                                                                                             // door de storm (sneeuw)
        padLijn(1, false, new int[]{1458, -336}, new int[]{1458, -343});
        padLijn(1, true, new int[]{1436, -390}, new int[]{1441, -398}, new int[]{1448, -408}, new int[]{1454, -417}, new int[]{1455, -422});   // het noordpad
        for (int x = 1428; x <= 1444; x++) for (int z = -392; z <= -366; z++) vrij[x - X1][z - Z1] = true;                                   // de kuil en de spar
    }

    private void terrein() {
        pad = new boolean[X2 - X1 + 1][Z2 - Z1 + 1];
        vrij = new boolean[X2 - X1 + 1][Z2 - Z1 + 1];
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
                if (diep) {
                    vul(x1, g + 1, z1, x2, G - 9, z2, Material.STONE);
                    vul(x1, G - 8, z1, x2, G - 8, z2, Material.GRAVEL);
                    vul(x1, G - 7, z1, x2, G - 1, z2, Material.WATER);
                    vul(x1, G, z1, x2, G + 44, z2, Material.AIR);
                } else for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) kolom(x, z, top[x - X1][z - Z1], soort[x - X1][z - Z1]);
            }
        // bomen: een dicht sparrenbos in het noordwesten, verder hier en daar een spar
        for (int x = X1 + 3; x <= X2 - 3; x++)
            for (int z = Z1 + 3; z <= Z2 - 3; z++) {
                if (soort[x - X1][z - Z1] != SNEEUW || vrij[x - X1][z - Z1] || pad[x - X1][z - Z1] || vast(x, z) || heuvel(x, z) > 0) continue;
                boolean bos = x <= 1476 && z <= -322 && z >= -424, bosje = !bos && zacht(x + 70, z - 30, 22) > 0.62;
                int raster = bos ? 4 : bosje ? 5 : 8;
                if (Math.floorMod(x + kans(z, x, 2), raster) != 0 || Math.floorMod(z + kans(x, z, 2), raster) != 0) continue;
                if (!bos && !bosje && kans(x, z, 3) != 0) continue;
                boolean ruimte = true;
                for (int dx = -2; dx <= 2 && ruimte; dx++)
                    for (int dz = -2; dz <= 2 && ruimte; dz++) {
                        int ax = x + dx - X1, az = z + dz - Z1;
                        ruimte = ax >= 0 && az >= 0 && ax < top.length && az < top[0].length && !vrij[ax][az] && soort[ax][az] == SNEEUW
                                && Math.abs(top[ax][az] - top[x - X1][z - Z1]) <= 1;
                    }
                if (!ruimte) continue;
                spar(x, top[x - X1][z - Z1] + 1, z, 7 + kans(x + 1, z, bos ? 6 : 4));
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) vrij[x + dx - X1][z + dz - Z1] = true;   // geen varen of stam in de stam
            }
        // keien in de sneeuw
        for (int x = X1 + 2; x <= X2 - 2; x += 7)
            for (int z = Z1 + 2; z <= Z2 - 2; z += 7) {
                int kx = x + kans(x, z, 5), kz = z + kans(z, x, 5);
                if (kx > X2 || kz > Z2 || soort[kx - X1][kz - Z1] != SNEEUW || vrij[kx - X1][kz - Z1] || pad[kx - X1][kz - Z1] || vast(kx, kz) || kans(kx, kz, 4) != 0) continue;
                int y = top[kx - X1][kz - Z1] + 1;
                zet(kx, y, kz, steen(kx, y, kz));
                if (kans(kx + 3, kz, 2) == 0) zet(kx + 1, y, kz, Material.COBBLESTONE);
                zet(kx, y + 1, kz, Material.SNOW);
            }
        // varens onder de sparren, hier en daar een omgevallen stam
        for (int x = 1406; x <= 1476; x++)
            for (int z = -424; z <= -322; z++) {
                if (soort[x - X1][z - Z1] != SNEEUW || pad[x - X1][z - Z1] || vast(x, z) || vrij[x - X1][z - Z1]) continue;
                int y = top[x - X1][z - Z1] + 1, k = kans(x + 5, z - 9, 40);
                if (k == 0) zet(x, y, z, Material.FERN);
                else if (k == 1 && kans(x, z + 3, 3) == 0) for (int i = 0; i < 4 && x + i <= 1476 && soort[x + i - X1][z - Z1] == SNEEUW
                        && top[x + i - X1][z - Z1] == y - 1 && !vrij[x + i - X1][z - Z1]; i++) zet(x + i, y, z, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            }
    }

    private void kolom(int x, int z, int hg, byte s) {
        int ymax = G + 44;
        if (s == ZEE) {
            vul(x, g + 1, z, x, hg - 1, z, Material.STONE);
            zet(x, hg, z, kans(x, z, 4) == 0 ? Material.SAND : Material.GRAVEL);
            vul(x, hg + 1, z, x, G - 1, z, Material.WATER);
            vul(x, G, z, x, ymax, z, Material.AIR);
            return;
        }
        if (s == MEER) {
            vul(x, g + 1, z, x, G - 5, z, Material.STONE);
            zet(x, G - 4, z, kans(x, z, 3) == 0 ? Material.CLAY : Material.GRAVEL);
            vul(x, G - 3, z, x, G - 1, z, Material.WATER);
            double r = Math.hypot(x - EX, z - EZ);
            zet(x, G, z, r >= 5.5 && r <= 8.5 ? Material.BLUE_ICE : Material.PACKED_ICE);
            vul(x, G + 1, z, x, ymax, z, Material.AIR);
            return;
        }
        if (s == BEEK) {
            vul(x, g + 1, z, x, hg - 1, z, Material.STONE);
            zet(x, hg, z, kans(x, z, 5) == 0 ? Material.BLUE_ICE : Material.PACKED_ICE);
            vul(x, hg + 1, z, x, ymax, z, Material.AIR);
            return;
        }
        if (s == ROTS) {
            for (int y = g + 1; y < hg; y++) zet(x, y, z, steen(x, y, z));
            zet(x, hg, z, hg >= G + 4 ? (kans(x, z, 11) == 0 ? Material.PACKED_ICE : Material.SNOW_BLOCK) : steen(x, hg, z));
            vul(x, hg + 1, z, x, ymax, z, Material.AIR);
            return;
        }
        vul(x, g + 1, z, x, hg - 3, z, Material.STONE);
        vul(x, hg - 2, z, x, hg - 1, z, s == KLOOF ? Material.STONE : Material.DIRT);
        Material opp = switch (s) {
            case STRAND -> kans(x, z, 3) == 0 ? Material.SNOW_BLOCK : Material.GRAVEL;
            case PLEIN -> {
                int r = (int) Math.round(Math.hypot(x - PX, z - PZ));
                yield r % 4 == 0 ? Material.POLISHED_ANDESITE : (x + z) % 3 == 0 ? Material.STONE_BRICKS : Material.CALCITE;
            }
            case KLOOF -> kans(x, z, 3) == 0 ? Material.SNOW_BLOCK : kans(x, z, 2) == 0 ? Material.GRAVEL : Material.STONE;
            default -> pad[x - X1][z - Z1] ? (kans(x, z, 3) == 0 ? Material.GRAVEL : kans(x, z, 2) == 0 ? Material.COARSE_DIRT : Material.SNOW_BLOCK)
                    : Material.SNOW_BLOCK;
        };
        zet(x, hg, z, opp);
        vul(x, hg + 1, z, x, ymax, z, Material.AIR);
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        g = bouwer.g;
        G = g + 8;
        leeg(bouwer.w);
        speelgebied = Box.van(X1 + 1, g - 4, Z1 + 2, X2, G + 50, Z2 - 2);
        storm = Box.van(1404, G - 6, -402, 1476, G + 30, -342);
        diep = Box.van(1404, G - 6, -398, 1470, G + 30, -348);
        stormUit = plek(1460, G + 1, -339, 0f);
        boolean bouwen = bouwer.plaats;
        List<long[]> chunks = new ArrayList<>();
        if (bouwen) {
            for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(X2, 16); cx++)
                for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) chunks.add(new long[]{cx, cz});
            b.daarna(() -> chunks.forEach(c -> w.addPluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = new int[X2 - X1 + 1][Z2 - Z1 + 1];
            soort = new byte[X2 - X1 + 1][Z2 - Z1 + 1];
            terrein();
        }
        steiger();
        dorp();
        meertje();
        bos();
        kuil();
        rotswand();
        wachterspost();
        bergpoort();
        stal();
        if (bouwen) versiering();
        if (bouwen) {
            b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = null;
            soort = null;
            pad = null;
            vrij = null;
        }
        // de begintoestand van deuren en poorten: altijd (ook zonder herbouw)
        for (Kamer k : kamers) {
            zetDeur(k.in, true);
            zetDeur(k.uit, false);
        }
        for (Uitdaging u : uitdagingen) zetDeur(u.poort, false);
        for (Poort po : poorten) zetDeur(po.deur, false);
        motor();
        b = null;
    }

    // ── 1. de fjord en de steiger ───────────────────────────────────────────────────────────

    private void steiger() {
        if (b.plaats) {
            // de steiger langs de westkant van de fjord, op palen
            for (int z = -226; z <= -204; z++) {
                vul(1464, G, z, 1466, G, z, Material.SPRUCE_PLANKS);
                vul(1464, G + 1, z, 1466, G + 4, z, Material.AIR);
                if (z % 4 == 0) for (int x : new int[]{1464, 1466}) {
                    vul(x, G - 7, z, x, G + 1, z, Material.SPRUCE_LOG);
                    if (x == 1466 && z % 8 == 0) zet(x, G + 2, z, Material.LANTERN);
                }
            }
            vul(1464, G, -203, 1466, G, -203, Material.STONE_BRICKS);
            vul(1467, G, -212, 1468, G, -211, Material.SPRUCE_PLANKS);                   // de loopplank naar het schip
            schip(1472, -222, -199);
            // de kop van de fjord: een paar tonnen en netten
            zet(1461, G + 1, -228, Material.BARREL);
            zet(1461, G + 2, -228, Material.BARREL);
            zet(1471, G + 1, -229, Material.BARREL);
            lantaarnpaal(1469, G + 1, -233);
            bord(1468, G + 1, -237, BlockFace.SOUTH, "IJshoeve");
        }
        rustvuur(1460, G, -232);
        rust(new Rust("steiger", "De fjord", plek(1465, G + 1, -231, 0f), Box.van(1458, G, -236, 1474, G + 6, -227), List.of()));
        verhaal(new Verhaal("aankomst3", Box.van(1458, G, -236, 1474, G + 6, -227), List.of(
                r("Wiske", "Sneeuw! De Witte Toppen."),
                r("Wiske", "Brr. Ik krijg ijs op mijn vleugels."))));
        Persoon brecht = persoon(new Persoon("Brecht", plek(1465, G + 1, -214, 90f), beroep("fisherman"), List.of(
                r("Brecht", "Zeg maar waarheen. De Zilvermeeuw ligt klaar."))));
        brecht.eenmalig("brecht-ijs", null, List.of(
                        r("Brecht", "Brr. Hier vriest zelfs mijn baard."),
                        r("Brecht", "Ik wacht hier op je. Neem je tijd.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.varen(p));
        verhaal(new Verhaal("weg3", Box.van(1470, G, -262, 1480, G + 5, -250), List.of(
                r("Wiske", "Rook uit schoorstenen. Daar is een dorp!"))));
    }

    /** De Zilvermeeuw in de fjord: boeg naar het noorden ({@code boeg}), achtersteven op {@code achter}. */
    private void schip(int mx, int boeg, int achter) {
        int dek = G;
        for (int z = boeg; z <= achter; z++) {
            double t = (achter - z) / (double) (achter - boeg);         // 0 = achtersteven, 1 = boeg
            double breed = 3.4 * Math.min(1, Math.min(t * 3.5 + 0.35, (1 - t) * 2.6 + 0.1));
            int bodem = G - 3 + (t > 0.8 ? (int) ((t - 0.8) * 10) : 0);
            int wi = (int) Math.round(breed);
            for (int y = bodem; y <= dek; y++)
                for (int x = mx - wi; x <= mx + wi; x++) {
                    boolean rand = Math.abs(x - mx) == wi || y == bodem;
                    if (y == dek) zet(x, y, z, rand ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                    else zet(x, y, z, rand && y == G - 1 ? Material.WHITE_TERRACOTTA : Material.DARK_OAK_PLANKS);
                }
            if (wi >= 1) {
                zet(mx - wi, dek + 1, z, Material.DARK_OAK_FENCE);
                zet(mx + wi, dek + 1, z, Material.DARK_OAK_FENCE);
            }
        }
        vul(mx - 3, dek + 1, -212, mx - 3, dek + 1, -211, Material.AIR);              // een opening in de reling bij de loopplank
        for (int[] m : new int[][]{{-215, 13}, {-206, 11}}) {
            vul(mx, dek + 1, m[0], mx, dek + m[1], m[0], Material.SPRUCE_LOG);
            for (int x = mx - 3; x <= mx + 3; x++) zet(x, dek + m[1] - 2, m[0], stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            for (int y = dek + 4; y <= dek + m[1] - 3; y++)
                for (int x = mx - 3; x <= mx + 3; x++) if (x != mx) zet(x, y, m[0], y == dek + (m[1] + 2) / 2 ? Material.LIGHT_BLUE_WOOL : Material.WHITE_WOOL);
            zet(mx, dek + m[1] + 1, m[0], Material.LIGHT_BLUE_WOOL);
        }
        for (int n = 0; n < 4; n++) zet(mx, dek + 1 + n / 2, boeg - 1 - n, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
        zet(mx, dek + 1, achter - 1, Material.LANTERN);
        zet(mx + 1, dek + 1, achter - 3, Material.BARREL);
    }

    // ── 2. IJshoeve: het plein, Tijmen, Saar en Sven, Hagen ─────────────────────────────────

    private void dorp() {
        if (b.plaats) {
            // het plein: een sneeuwvlok van andesiet in het midden, lantaarns rondom
            for (int i = -4; i <= 4; i++) {
                zet(PX + i, G, PZ, Material.POLISHED_DIORITE);
                zet(PX, G, PZ + i, Material.POLISHED_DIORITE);
                if (Math.abs(i) <= 2) {
                    zet(PX + i, G, PZ + i, Material.POLISHED_DIORITE);
                    zet(PX + i, G, PZ - i, Material.POLISHED_DIORITE);
                }
            }
            for (int[] l : new int[][]{{PX - 8, PZ - 6}, {PX + 8, PZ - 6}, {PX - 8, PZ + 6}, {PX + 8, PZ + 6}}) lantaarnpaal(l[0], G + 1, l[1]);
            huis(1468, -320, 1475, -313, BlockFace.SOUTH);
            huis(1486, -320, 1493, -313, BlockFace.SOUTH);
            boerderij();
            smidse();
            ijsblok();
            // Tijmens kampje: een luifel met sterren, een kijker
            int tx = 1488, tz = -286;
            for (int[] p : new int[][]{{tx - 1, tz - 1}, {tx + 2, tz - 1}, {tx - 1, tz + 1}, {tx + 2, tz + 1}}) vul(p[0], G + 1, p[1], p[0], G + 3, p[1], Material.SPRUCE_FENCE);
            for (int x = tx - 1; x <= tx + 2; x++)
                for (int z = tz - 1; z <= tz + 1; z++) zet(x, G + 4, z, (x + z) % 2 == 0 ? Material.BLUE_WOOL : Material.LIGHT_BLUE_WOOL);
            zet(tx + 2, G + 1, tz, Material.SPRUCE_FENCE);
            zet(tx + 2, G + 2, tz, Bouwer.gericht(Material.LIGHTNING_ROD, BlockFace.UP));
            zet(tx - 1, G + 5, tz, Material.END_ROD);
            // bordjes bij de uitgangen
            bord(1471, G + 1, -297, BlockFace.EAST, "Het meer");
            bord(1473, G + 1, -305, BlockFace.SOUTH, "Het Sparrenbos");
            bord(1484, G + 1, -306, BlockFace.SOUTH, "De Uitkijk");
            bord(1491, G + 1, -298, BlockFace.WEST, "De Bergpoort");
            bord(1477, G + 1, -284, BlockFace.NORTH, "De fjord");
        }
        rustvuur(1475, G, -299);
        rust(new Rust("hoeve", "IJshoeve", plek(1478, G + 1, -297, 90f), Box.van(1470, G, -304, 1490, G + 6, -284), List.of()));
        bron("bron-plein", plek(PX, G + 1, PZ, 0f));
        verhaal(new Verhaal("hoeve", Box.van(1470, G, -304, 1490, G + 6, -284), List.of(
                r("Wiske", "IJshoeve. Wat een warm dorpje!"),
                r("Wiske", "Kijk, wie staat daar bij die luifel?"))));
        tijmen();
        geiten();
        hagen();
        vondst(new Vondst(STUKJE_IJS, "Hartstukje", "", Material.REDSTONE, plek(1501, G + 1.3, -289, 0f),
                Box.van(1501, G, -289, 1501, G + 2, -289))).voorwerp = "vuurstaf";
        geit("Sokje", plek(1488, G + 1, -255, 30f));
    }

    /** Een huisje van sparrenhout op een stenen voet, met een sneeuwdak (nok oost-west) en een deur aan de kant {@code deur}. */
    private void huis(int x1, int z1, int x2, int z2, BlockFace deur) {
        BlockData raamX = Bouwer.ruit(Material.LIGHT_BLUE_STAINED_GLASS_PANE, BlockFace.EAST, BlockFace.WEST);
        BlockData raamZ = Bouwer.ruit(Material.LIGHT_BLUE_STAINED_GLASS_PANE, BlockFace.NORTH, BlockFace.SOUTH);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean muurZ = z == z1 || z == z2, muurX = x == x1 || x == x2;
                zet(x, G, z, Material.SPRUCE_PLANKS);
                for (int y = G + 1; y <= G + 4; y++) {
                    if (!muurZ && !muurX) {
                        zet(x, y, z, Material.AIR);
                        continue;
                    }
                    boolean hoek = muurZ && muurX;
                    boolean raam = y == G + 2 && !hoek && (muurZ ? (x - x1) % 3 == 2 : (z - z1) % 3 == 2);
                    zet(x, y, z, hoek ? Material.SPRUCE_LOG.createBlockData() : y == G + 1 ? Material.COBBLESTONE.createBlockData()
                            : raam ? (muurZ ? raamX : raamZ) : Material.SPRUCE_PLANKS.createBlockData());
                }
            }
        // het dak: treden van sneeuw naar de nok; de gevels van planken
        int nok = 0;
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            int y = G + 5 + Math.min(z - (z1 - 1), z2 + 1 - z);
            nok = Math.max(nok, y);
            for (int x = x1 - 1; x <= x2 + 1; x++) zet(x, y, z, Material.SNOW_BLOCK);
            for (int x : new int[]{x1, x2}) if (z >= z1 && z <= z2) vul(x, G + 5, z, x, y - 1, z, Material.SPRUCE_PLANKS);
        }
        // de deur, een schoorsteen, wat spullen
        int dx = (x1 + x2) / 2;
        if (deur == BlockFace.SOUTH) vul(dx, G + 1, z2, dx, G + 2, z2, Material.AIR);
        else vul(dx, G + 1, z1, dx, G + 2, z1, Material.AIR);
        int mz = (z1 + z2) / 2;
        vul(x2 - 1, G + 5, mz + 1, x2 - 1, nok + 1, mz + 1, Material.BRICKS);
        zet(x2 - 1, nok + 2, mz + 1, vuur(Material.CAMPFIRE, true));
        zet(x1 + 1, G + 1, z1 + 1, Material.BARREL);
        zet(x2 - 1, G + 1, z1 + 1, Material.CRAFTING_TABLE);
        zet(dx, G + 4, mz, Bouwer.hangend(Material.LANTERN));
    }

    /**
     * Tijmen, de reizende tovenaar (zie ZELDA.md): hij was er weer eerder. Zijn gave hier is de sterrenlantaarn: in de
     * sneeuwstorm van het Sparrenbos laat hij het spoor zien dat de sneeuw verbergt.
     */
    private void tijmen() {
        Persoon tijmen = persoon(new Persoon("Tijmen", plek(1488, G + 1, -285, 135f), null, List.of(r("Tijmen", "De sterren zwijgen even."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.eenmalig("tijmen-ijs", null, List.of(
                        r("Tijmen", "Daar ben je. Koud, hè?"),
                        r("Wiske", "Hij is er alwéér! Hoe dan?"),
                        r("Tijmen", "In het Sparrenbos raast een storm. Hij verbergt een spoor."),
                        r("Tijmen", "Deze lantaarn laat zien wat de sneeuw verstopt.")), (spel, p, s) -> {
                    spel.beloon(p, "lantaarn", "Sterrenlantaarn", "licht in de storm");
                    spel.uitleg(p, r("Wiske", "Met dat licht zie je sporen in de storm."));
                })
                .eenmalig("tijmen-haak", s -> s.heeft("klauwhaak"), List.of(
                        r("Tijmen", "De klauwhaak van de wachters. Mooi zo."),
                        r("Tijmen", "Hoog op de berg slaapt het Vorstbaken.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
        praten(tijmen);
    }

    /** De geitenboerderij van Saar en Sven: een huisje, een open stal (wie thuis is, staat erin), hooi. */
    private void boerderij() {
        huis(1454, -305, 1462, -298, BlockFace.SOUTH);
        for (int x = 1454; x <= 1462; x++)
            for (int z = -291; z <= -283; z++) {
                boolean muur = x == 1454 || z == -291 || z == -283;
                zet(x, G, z, x == 1462 ? Material.SPRUCE_PLANKS : Material.HAY_BLOCK);
                for (int y = G + 1; y <= G + 3; y++) zet(x, y, z, muur ? Material.SPRUCE_PLANKS : Material.AIR);
                zet(x, G + 4, z, Material.SPRUCE_SLAB);
                zet(x, G + 5, z, Material.SNOW);
            }
        for (int z : new int[]{-291, -283}) vul(1462, G + 1, z, 1462, G + 3, z, Material.SPRUCE_LOG);
        for (int z : new int[]{-288, -286}) zet(1455, G + 1, z, Material.SPRUCE_FENCE);
        zet(1455, G + 1, -290, Material.HAY_BLOCK);
        zet(1464, G + 1, -285, Material.HAY_BLOCK);
        zet(1465, G + 1, -282, Material.HAY_BLOCK);
        zet(1461, G + 3, -287, Bouwer.hangend(Material.LANTERN));
    }

    /** Saar en Sven en hun geiten: zijmissie «Zoekgeraakte geiten» (Bles, Mekker, Sokje) → een hartstukje. */
    private void geiten() {
        stukje(STUKJE_GEITEN);
        missie("geiten", "Zoekgeraakte geiten", "Zoek Bles, Mekker en Sokje rond IJshoeve.", "Vertel Saar dat ze thuis zijn.");
        Persoon saar = persoon(new Persoon("Saar", plek(1465, G + 1, -288, 90f), beroep("shepherd"), List.of(
                r("Saar", "Geiten klimmen overal op. Zelfs op het dak."))));
        saar.streek = Villager.Type.SNOW;
        saar.gesprek("saar-1", s -> s.missie("geiten") == 0, List.of(
                        r("Saar", "Hoi! Ik ben Saar. Dat is Sven, hij slaapt."),
                        r("Saar", "Onze geiten zijn weg. Gevlucht voor de storm."),
                        r("Saar", "Bles, Mekker en Sokje. Zie jij ze?")), (spel, p, s) -> spel.missie(p, "geiten", 1))
                .gesprek(null, s -> s.missie("geiten") == 1, List.of(r("Saar", "Bles, Mekker en Sokje. Ze dragen een belletje.")), null)
                .gesprek("saar-2", s -> s.missie("geiten") == 2, List.of(
                        r("Saar", "Ze zijn er alle drie! Sven, wakker worden!"),
                        r("Saar", "Hier. Dit vond Sven in het hooi.")), (spel, p, s) -> {
                    spel.missie(p, "geiten", ZeldaVoortgang.KLAAR);
                    spel.beloon(p, STUKJE_GEITEN, "Hartstukje", null);
                });
        Persoon sven = persoon(new Persoon("Sven", plek(1464, G + 1, -284, 120f), beroep("shepherd"), List.of(r("Sven", "Zzz…"))));
        sven.streek = Villager.Type.SNOW;
        sven.kind = true;
        sven.eenmalig("sven-1", null, List.of(
                        r("Sven", "Hm? Ik sliep niet. Ik telde geiten."),
                        r("Sven", "Met mijn ogen dicht. Dat telt ook.")), null)
                .eenmalig("sven-vos", s -> s.heeft("dier:sneeuwvos"), List.of(
                        r("Sven", "Een sneeuwvosje! Die zie je bijna nooit.")), null);
    }

    /** Wie thuis is, staat in de stal (alleen te zien voor wie hem vond). Na de zoekgeraakte geiten (/zelda ga bles = de zoekgeraakte). */
    private void stal() {
        String[] namen = {"Bles", "Mekker", "Sokje"};
        for (int i = 0; i < namen.length; i++) {
            String n = namen[i];
            Persoon thuis = persoon(new Persoon(n, plek(1456, G + 1, -289 + 2 * i, -90f), null, List.of(r(n, "Mèèh."))));
            thuis.soort = EntityType.GOAT;
            thuis.zichtbaar = s -> s.heeft("g:geit-" + n.toLowerCase());
        }
    }

    /** Een zoekgeraakte geit: te vinden als Saar het vroeg; gevonden = naar huis (dan staat hij in de stal). */
    private Persoon geit(String naam, Location plek) {
        String id = "geit-" + naam.toLowerCase();
        Persoon geit = persoon(new Persoon(naam, plek, null, List.of(r(naam, "Mèèèh!"))));
        geit.soort = EntityType.GOAT;
        geit.zichtbaar = s -> !s.heeft("g:" + id);
        geit.eenmalig(id, s -> s.missie("geiten") == 1, List.of(
                r(naam, "Mèèh!"),
                r("Wiske", "Gevonden! Hup " + naam + ", naar huis.")), (spel, p, s) -> {
            if (s.heeft("g:geit-bles") && s.heeft("g:geit-mekker") && s.heeft("g:geit-sokje")) spel.missie(p, "geiten", 2);
        });
        return geit;
    }

    /** Hagen de smid: smeedde ooit klauwhaken voor de bergwachters; er zit iets in het ijsblok naast zijn smidse. */
    private void hagen() {
        Persoon hagen = persoon(new Persoon("Hagen", plek(1500, G + 1, -300, 180f), beroep("weaponsmith"), List.of(
                r("Hagen", "Kou is goed voor staal. Voor mij minder."))));
        hagen.streek = Villager.Type.SNOW;
        hagen.eenmalig("hagen-1", null, List.of(
                        r("Hagen", "Een reiziger! Kom bij het vuur."),
                        r("Hagen", "Ik smeedde klauwhaken voor de bergwachters."),
                        r("Hagen", "In hun oude Wachterspost ligt er vast nog een."),
                        r("Hagen", "Die ligt achter het Sparrenbos.")), null)
                .eenmalig("hagen-haak", s -> s.heeft("klauwhaak"), List.of(
                        r("Hagen", "Laat zien… Goed staal. Die houdt je wel."),
                        r("Hagen", "De trap bij de Bergpoort is kapot. Haak je omhoog!")), null)
                .eenmalig("hagen-ijs", s -> s.heeft("g:hagen-1"), List.of(
                        r("Hagen", "Zie je dat ijsblok? Er zit iets in."),
                        r("Hagen", "Mijn vuur krijgt het niet los. Betoverd ijs.")), null);
    }

    /** De smidse van Hagen: open naar de weg, een esse, een aambeeld, een waterbak. */
    private void smidse() {
        int x1 = 1496, x2 = 1504, z1 = -306, z2 = -299;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean muur = x == x1 || x == x2 || z == z1;
                zet(x, G, z, Material.COBBLESTONE);
                for (int y = G + 1; y <= G + 4; y++) zet(x, y, z, muur ? (y == G + 1 ? Material.STONE_BRICKS : Material.SPRUCE_PLANKS) : Material.AIR);
                zet(x, G + 5, z, Material.SPRUCE_SLAB);
                zet(x, G + 6, z, Material.SNOW);
            }
        for (int x : new int[]{x1, x2}) vul(x, G + 1, z2, x, G + 4, z2, Material.SPRUCE_LOG);
        // de esse: een vuur onder een kap van baksteen
        vul(1497, G + 1, -305, 1498, G + 1, -304, Material.STONE_BRICKS);
        zet(1497, G + 2, -305, vuur(Material.CAMPFIRE, true));
        vul(1497, G + 3, -305, 1498, G + 4, -304, Material.BRICKS);
        vul(1497, G + 5, -305, 1497, G + 8, -305, Material.BRICKS);
        zet(1501, G + 1, -303, Bouwer.gericht(Material.ANVIL, BlockFace.EAST));
        zet(1503, G + 1, -305, Material.BLAST_FURNACE);
        zet(1503, G + 1, -304, Material.WATER_CAULDRON);
        zet(1499, G + 1, -305, Material.GRINDSTONE);
        zet(1500, G + 4, -302, Bouwer.hangend(Material.LANTERN));
    }

    /** Een blok betoverd ijs bij de smidse, met een hartstukje erin: pas te halen met de vuurstaf (eiland 4a). */
    private void ijsblok() {
        for (int x = 1500; x <= 1502; x++)
            for (int y = G + 1; y <= G + 3; y++)
                for (int z = -290; z <= -288; z++) zet(x, y, z, x == 1501 && z == -289 && y <= G + 2 ? Material.AIR : Material.ICE);
        zet(1501, G, -289, Material.PACKED_ICE);
    }

    // ── 3. het meer: Ys, Noor en de ijsbaan, het eilandje ───────────────────────────────────

    private void meertje() {
        if (b.plaats) {
            // het eilandje met een spar
            for (int x = EX - 3; x <= EX + 3; x++)
                for (int z = EZ - 3; z <= EZ + 3; z++) {
                    if (Math.hypot(x - EX, z - EZ) > 2.6) continue;
                    vul(x, G - 4, z, x, G, z, Material.STONE);
                    zet(x, G + 1, z, Material.SNOW_BLOCK);
                }
            spar(EX, G + 2, EZ, 9);
            // het wak van Ys, zijn krukje, een ton, een lantaarn op een paal
            vul(1442, G - 3, -308, 1443, G, -307, Material.WATER);
            zet(1445, G + 1, -306, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.EAST));
            zet(1445, G + 1, -308, Material.BARREL);
            lantaarnpaal(1446, G + 1, -309);
            // het vishutje aan de oever
            for (int x = 1449; x <= 1452; x++)
                for (int z = -315; z <= -311; z++) {
                    boolean muur = x == 1449 || x == 1452 || z == -315 || z == -311;
                    zet(x, G, z, Material.SPRUCE_PLANKS);
                    for (int y = G + 1; y <= G + 3; y++) zet(x, y, z, muur ? Material.SPRUCE_PLANKS : Material.AIR);
                    zet(x, G + 4, z, Material.SPRUCE_SLAB);
                    zet(x, G + 5, z, Material.SNOW);
                }
            vul(1449, G + 1, -313, 1449, G + 2, -313, Material.AIR);
            // de ijsbaan: een startplaat, een hekje, vlaggetjes, een bord voor de tijden
            zet(1437, G + 1, -301, Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            for (int[] v : new int[][]{{1435, -299}, {1439, -299}, {1435, -303}, {1439, -303}, {1421, -301}, {1421, -299}}) {
                zet(v[0], G + 1, v[1], Material.SPRUCE_FENCE);
                zet(v[0], G + 2, v[1], Material.RED_WOOL);
            }
            zet(1441, G + 1, -297, Material.SPRUCE_FENCE);
            zet(1441, G + 2, -297, Material.SPRUCE_FENCE);
        }
        verhaal(new Verhaal("meer", Box.van(1448, G, -298, 1455, G + 4, -290), List.of(
                r("Wiske", "Een bevroren meer. Daar vist iemand!"))));
        // Grootvader Ys, de oude bergwachter: hij vist bij zijn wak, en heeft een brief voor Teun (een opdracht terug naar Zoutkom)
        Persoon ys = persoon(new Persoon("Ys", plek(1444, G + 1, -307, 90f), beroep("fisherman"), List.of(
                r("Ys", "Stil maar. Dan bijten ze misschien."))));
        ys.streek = Villager.Type.SNOW;
        ys.eenmalig("ys-1", null, List.of(
                        r("Ys", "Kom erbij, kind. Ze bijten toch niet."),
                        r("Ys", "Ik ben Ys. Vroeger was ik bergwachter."),
                        r("Ys", "Boven op de berg staat ons IJspaleis."),
                        r("Ys", "Nu woont daar alleen de kou.")), null)
                .gesprek("ys-brief", s -> s.missie("brief") == 0, List.of(
                        r("Ys", "Je kwam over zee? Ken je Teun, uit Zoutkom?"),
                        r("Ys", "Wij waren jongens samen, op de Kraai."),
                        r("Ys", "Breng hem deze brief. Hij zal lachen.")), (spel, p, s) -> spel.missie(p, "brief", 1))
                .gesprek(null, s -> s.missie("brief") == 1, List.of(r("Ys", "Teun zit vast op zijn bankje. Dat deed hij toen al.")), null)
                .eenmalig("ys-brief2", s -> s.missieKlaar("brief"), List.of(
                        r("Ys", "Wat zei hij? Ha! Die ouwe zeehond."),
                        r("Ys", "Dank je, kind.")), null)
                .eenmalig("ys-vos", s -> s.heeft("dier:sneeuwvos"), List.of(
                        r("Ys", "Een sneeuwvos. Die brengen geluk, zeggen ze.")), null)
                .eenmalig("ys-haak", s -> s.heeft("klauwhaak"), List.of(
                        r("Ys", "Een klauwhaak! Zo klommen wij de berg op."),
                        r("Ys", "Het bergpad begint bij de Bergpoort.")), null);
        // Noor, zijn kleindochter: de ijsbaan om het eilandje
        Persoon noor = persoon(new Persoon("Noor", plek(1440, G + 1, -304, 45f), null, List.of(
                r("Noor", "Nog een rondje? Je tijd staat op het bord."))));
        noor.streek = Villager.Type.SNOW;
        noor.kind = true;
        noor.eenmalig("noor-1", null, List.of(
                        r("Noor", "Hoi! Ik ben Noor. Opa Ys vist daar."),
                        r("Noor", "Ik schaats het snelst van IJshoeve."),
                        r("Noor", "Stap op de plaat, en één keer om het eilandje!")), null)
                .eenmalig("noor-2", s -> s.beste("ijsbaan") != null, List.of(
                        r("Noor", "Wauw, wat snel! Opa, zag je dat?")), null);
        verhaal(new Verhaal("uitleg-ijsbaan", Box.van(1439, G, -306, 1442, G + 4, -303), List.of(
                r("Wiske", "Stap op de plaat, dan gaat het hekje open."),
                r("Wiske", "Eén rondje om het eilandje. Glibberig!")))).uitleg = true;
        Uitdaging baan = uitdaging(new Uitdaging("ijsbaan", "De ijsbaan", Box.van(1436, G, -301, 1438, G + 3, -300),
                Box.van(1436, G, -303, 1438, G + 3, -302), Box.van(1419, G - 1, -311, 1442, G + 5, -289),
                deur(hek(Material.SPRUCE_FENCE, false), 1436, G + 1, -299, 1438, G + 1, -299), 20));
        baan.bord = new Location(w, 1441.5, G + 3.4, -297.5);
        baan.via = Box.van(1419, G, -302, 1425, G + 3, -298);              // de overkant: wie de bocht afsnijdt, telt niet
        Vondst stukje = vondst(new Vondst(STUKJE_EILANDJE, "Hartstukje", "", Material.REDSTONE, plek(EX + 2, G + 2.3, EZ + 1, 0f),
                Box.van(EX + 1, G + 2, EZ, EX + 3, G + 4, EZ + 2)));
        stukje.verborgen = true;                                            // onder de spar: het vosje ruikt hem
        geit("Bles", plek(1418, G + 1, -317, 200f));
    }

    // ── 4. het Sparrenbos: de storm, de pootjes ─────────────────────────────────────────────

    private void bos() {
        if (b.plaats) bord(1462, G + 1, -331, BlockFace.SOUTH, "Het Sparrenbos");
        rustvuur(1463, G, -336);
        rust(new Rust("bosrand", "De bosrand", plek(1460, G + 1, -334, 0f), Box.van(1455, G, -339, 1466, G + 6, -329), List.of()));
        Verhaal zonder = verhaal(new Verhaal("storm", Box.van(1452, G, -342, 1466, G + 6, -337), List.of(
                r("Wiske", "Wat een storm! Ik zie geen hand voor ogen."),
                r("Wiske", "Misschien weet Tijmen raad?"))));
        zonder.als = s -> !s.heeft("lantaarn");
        verhaal(new Verhaal("pootjes", Box.van(1452, G, -348, 1462, G + 6, -342), List.of(
                r("Wiske", "Kijk, in het licht: pootjes in de sneeuw!"),
                r("Wiske", "Kleine pootjes. Zullen we ze volgen?")))).vereist = "lantaarn";
        bron("bron-bos", plek(1444, G + 1, -364, 0f));
    }

    // ── 5. de kuil onder de grote spar: twee nevelwolven, het sneeuwvosje ───────────────────

    private void kuil() {
        int y0 = G - 3;                                                     // de bodem; je staat op G−2
        if (b.plaats) {
            for (int x = 1431; x <= 1441; x++)
                for (int z = -385; z <= -375; z++) {
                    boolean wand = x == 1431 || x == 1441 || z == -385 || z == -375;
                    vul(x, g + 1, z, x, y0 - 1, z, Material.STONE);
                    if (wand) {
                        for (int y = y0; y <= G; y++) zet(x, y, z, y == G ? Material.SNOW_BLOCK : kans(x + y, z, 3) == 0 ? Material.STONE : Material.DIRT);
                        continue;
                    }
                    zet(x, y0, z, kans(x, z, 4) == 0 ? Material.PODZOL : Material.SNOW_BLOCK);
                    vul(x, y0 + 1, z, x, G + 6, z, Material.AIR);
                }
            // de hellingen erin (zuid) en eruit (noord)
            for (int x = 1435; x <= 1437; x++) {
                int[][] trap = {{-374, y0}, {-373, y0}, {-372, y0 + 1}, {-371, y0 + 2}, {-386, y0}, {-387, y0 + 1}, {-388, y0 + 2}};
                for (int[] t : trap) {
                    vul(x, g + 1, t[0], x, t[1], t[0], Material.DIRT);
                    zet(x, t[1], t[0], Material.SNOW_BLOCK);
                    vul(x, t[1] + 1, t[0], x, G + 4, t[0], Material.AIR);
                }
            }
            // de grote spar, met zijn wortels in de kuil; het holletje van het vosje
            spar(1428, G + 1, -381, 18);
            vul(1429, G + 1, -381, 1429, G + 12, -381, Material.SPRUCE_LOG);
            for (int z = -383; z <= -379; z++) zet(1431, G, z, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            zet(1430, G, -381, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            vul(1431, y0 + 1, -381, 1431, y0 + 2, -381, Material.AIR);
            zet(1431, y0, -381, Material.PODZOL);
            zet(1440, y0 + 1, -384, Bouwer.gericht(Material.BELL, BlockFace.WEST));
        }
        verhaal(new Verhaal("kuil", Box.van(1433, G, -372, 1439, G + 5, -367), List.of(
                r("Wiske", "Hoor je dat? Gepiep, daar in die kuil!")))).als = s -> !s.heeft("k:kuil");
        BlockData sneeuw = Material.SNOW_BLOCK.createBlockData();
        Kamer k = kamer(new Kamer("kuil", "De kuil", Box.van(1432, y0, -384, 1440, G + 1, -376), Box.van(1433, y0 + 1, -383, 1439, G, -377),
                deur(sneeuw, 1435, y0 + 1, -375, 1437, G, -375), deur(sneeuw, 1435, y0 + 1, -385, 1437, G, -385)));
        for (int[] s : new int[][]{{1433, -380}, {1439, -380}, {1436, -383}, {1433, -383}, {1439, -383}})
            k.spawns.add(plek(s[0], y0 + 1, s[1], 0f));
        k.golf(Soort.NEVELWOLF, Soort.NEVELWOLF)
                .extra(Soort.NEVELWOLF, Soort.NEVELWOLF, Soort.NEVELWOLF);
        k.bijStart.add(r("Wiske", "Wolven! Ze zitten achter het vosje aan."));
        k.bel = new int[]{1440, y0 + 1, -384};
        k.tips.addAll(List.of(r("Wiske", "Ze zijn snel. Schild omhoog, dan een klap."),
                r("Wiske", "In het bos staat een feeënkring. Daar genees je.")));
        // het sneeuwvosje in zijn holletje onder de wortels
        Persoon vos = persoon(new Persoon("Vosje", plek(1431, y0 + 1, -381, -90f), null, List.of(r("Wiske", "Hij rilt nog een beetje."))));
        vos.soort = EntityType.FOX;
        vos.kind = true;
        vos.zichtbaar = s -> !s.heeft("dier:sneeuwvos");
        vos.uiterlijk = e -> {
            if (e instanceof org.bukkit.entity.Fox f) {
                f.setFoxType(org.bukkit.entity.Fox.Type.SNOW);
                f.setSitting(true);
            }
        };
        vos.gesprek(null, s -> !s.heeft("k:kuil"), List.of(r("Wiske", "Hij is bang. Eerst die wolven weg!")), null)
                .eenmalig("vosje", s -> !s.heeft("dier:sneeuwvos"), List.of(
                        r("Wiske", "Arm ding. Hij rilt helemaal."),
                        r("Wiske", "Kom maar. Jij mag met ons mee.")), (spel, p, s) -> spel.dier(p, "sneeuwvos"));
        praten(vos);
        verhaal(new Verhaal("noordpad", Box.van(1433, G, -393, 1440, G + 5, -389), List.of(
                r("Wiske", "Hier waait het minder. Waar gaat dit pad heen?"))));
    }

    // ── 6. de rotswand: het vosje graaft een deur op ────────────────────────────────────────

    private void rotswand() {
        int x0 = 1455;                                                      // het midden van de deur
        if (b.plaats) {
            // de deur van de bergwachters in de rots, met een sneeuwhoop ervoor
            for (int z = -428; z <= -425; z++) {
                vul(x0 - 1, G, z, x0 + 1, G, z, Material.STONE_BRICKS);
                vul(x0 - 1, G + 1, z, x0 + 1, G + 3, z, Material.AIR);
            }
            for (int x : new int[]{x0 - 2, x0 + 2}) vul(x, G + 1, -425, x, G + 4, -425, Material.CHISELED_STONE_BRICKS);
            vul(x0 - 1, G + 4, -425, x0 + 1, G + 4, -425, Material.STONE_BRICKS);
            zet(x0, G + 5, -425, Material.CHISELED_STONE_BRICKS);
            for (int[] s : new int[][]{{x0 - 2, -424}, {x0 + 2, -424}, {x0 - 3, -424}, {x0 + 3, -424}}) zet(s[0], G + 1, s[1], Material.SNOW_BLOCK);
            for (int x = x0 - 2; x <= x0 + 2; x++) zet(x, G + 4, -424, Material.SNOW_BLOCK);
            for (int x = x0 - 1; x <= x0 + 1; x++) zet(x, G + 1, -423, Material.SNOW);
            // de Wachtersgang: eerst het noorden in, dan oost, langzaam omlaag naar de kloof
            for (int z = -433; z <= -429; z++) {
                vul(x0 - 1, G, z, x0 + 1, G, z, Material.STONE_BRICKS);
                vul(x0 - 1, G + 1, z, x0 + 1, G + 3, z, Material.AIR);
            }
            for (int x = x0 + 2; x <= 1483; x++) {
                int f = gangVloer(x);
                for (int z = -433; z <= -431; z++) {
                    vul(x, f - 1, z, x, f, z, Material.STONE_BRICKS);
                    vul(x, f + 1, z, x, f + 3, z, Material.AIR);
                    zet(x, f + 4, z, wachtersteen(x, f + 4, z));
                }
                if ((x - x0) % 6 == 0) zet(x, f + 3, -432, Bouwer.hangend(Material.SOUL_LANTERN));
            }
            zet(x0, G + 3, -430, Bouwer.hangend(Material.SOUL_LANTERN));
        }
        Verhaal wand = verhaal(new Verhaal("rotswand", Box.van(x0 - 5, G, -424, x0 + 5, G + 5, -416), List.of(
                r("Wiske", "Een rotswand. Hier houdt het pad op…"))));
        wand.als = s -> !s.heeft("dier:sneeuwvos") && !s.heeft(DEUR);
        Verhaal snuf = verhaal(new Verhaal("snuffel", Box.van(x0 - 5, G, -424, x0 + 5, G + 5, -414), List.of(
                r("Wiske", "Kijk, je vosje ruikt iets! Daar, in de sneeuw."))));
        snuf.als = s -> s.heeft("dier:sneeuwvos") && !s.heeft(DEUR);
        graaf(new Graaf("deur", plek(x0, G + 1, -422, 180f), DEUR, r("Wiske", "Een deur onder de sneeuw! Slim vosje.")));
        poort(new Poort("wachtersdeur", deur(Material.SNOW_BLOCK.createBlockData(), x0 - 1, G + 1, -424, x0 + 1, G + 3, -424),
                Box.van(x0 - 4, G, -430, x0 + 4, G + 5, -419), s -> s.heeft(DEUR)));
        verhaal(new Verhaal("gang", Box.van(x0 + 2, G, -433, x0 + 7, G + 4, -431), List.of(
                r("Wiske", "Een gang van de bergwachters. Zo oud!"))));
    }

    /** De vloer van de Wachtersgang (oostwaarts): op G bij de deur, dan elke vier blokken één omlaag, tot de kloof (G−6). */
    private int gangVloer(int x) {
        return G - Math.max(0, Math.min(6, (x - 1457) / 4));
    }

    // ── 7. de kloof en de Wachterspost: de klauwhaak, de klim ──────────────────────────────

    private void wachterspost() {
        int y0 = G - 6;                                                     // de bodem van de kloof; je staat op G−5
        if (b.plaats) {
            // de post: een hal tegen de noordwand, met een torentje
            for (int x = 1498; x <= 1510; x++)
                for (int z = -446; z <= -440; z++) {
                    boolean muur = x == 1498 || x == 1510 || z == -446 || z == -440;
                    zet(x, y0, z, (x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                    for (int y = y0 + 1; y <= y0 + 5; y++) {
                        boolean raam = y == y0 + 3 && z == -440 && (x == 1500 || x == 1508);
                        zet(x, y, z, !muur ? Material.AIR : raam ? Material.IRON_BARS : wachtersteen(x, y, z));
                    }
                    zet(x, y0 + 6, z, muur ? Material.STONE_BRICK_WALL : Material.STONE_BRICK_SLAB);
                }
            for (int x = 1498; x <= 1501; x++)
                for (int z = -446; z <= -443; z++)
                    for (int y = y0 + 6; y <= y0 + 9; y++) {
                        boolean muur = x == 1498 || x == 1501 || z == -446 || z == -443;
                        zet(x, y, z, y == y0 + 9 ? (muur && (x + z) % 2 == 0 ? Material.STONE_BRICK_WALL : Material.AIR) : muur ? wachtersteen(x, y, z) : Material.AIR);
                    }
            vul(1503, y0 + 1, -440, 1505, y0 + 3, -440, Material.AIR);           // de deur
            zet(1504, y0 + 4, -440, Material.CHISELED_STONE_BRICKS);
            for (int x : new int[]{1502, 1506}) zet(x, y0 + 3, -439, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.SOUTH));
            // binnen: het rek met de klauwhaak, tafels, tonnen, banieren
            vul(1503, y0 + 1, -445, 1505, y0 + 1, -445, Material.SPRUCE_PLANKS);
            for (int x : new int[]{1502, 1506}) zet(x, y0 + 3, -445, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.SOUTH));
            zet(1500, y0 + 1, -441, Material.BARREL);
            zet(1500, y0 + 2, -441, Material.BARREL);
            zet(1509, y0 + 1, -445, Bouwer.gericht(Material.ANVIL, BlockFace.NORTH));
            zet(1508, y0 + 1, -441, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.WEST));
            zet(1507, y0 + 1, -441, Material.SPRUCE_PLANKS);
            for (int x : new int[]{1500, 1508}) zet(x, y0 + 5, -443, Bouwer.hangend(Material.SOUL_LANTERN));
            // de les: drie haakpunten in de zuidwand, met plankieren (de wachters klommen zo naar boven)
            for (int x = 1514; x <= 1516; x++) for (int z = -431; z <= -430; z++) zet(x, G - 2, z, Material.SPRUCE_PLANKS);
            for (int x = 1522; x <= 1524; x++) for (int z = -431; z <= -430; z++) zet(x, G + 2, z, Material.SPRUCE_PLANKS);
            for (int x : new int[]{1514, 1516}) zet(x, G - 3, -430, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            for (int x : new int[]{1522, 1524}) zet(x, G + 1, -430, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            zet(1513, G + 1, -430, Material.STONE_BRICKS);
            zet(1513, G + 1, -431, HAAKPUNT);
            zet(1522, G + 6, -430, Material.STONE_BRICKS);
            zet(1522, G + 6, -431, HAAKPUNT);
            // boven: de rand van de Uitkijk met een hek, en een kraan van de wachters met het laatste haakpunt
            for (int x = 1490; x <= 1534; x++) if (x != 1527) zet(x, G + 7, -429, hek(Material.SPRUCE_FENCE, false));
            vul(1527, G + 7, -429, 1527, G + 9, -429, Material.SPRUCE_LOG);
            zet(1527, G + 9, -430, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            zet(1527, G + 9, -431, HAAKPUNT);
            zet(1527, G + 8, -430, Material.IRON_CHAIN);
            // de Uitkijk: een bankje, een lantaarn
            for (int x = 1506; x <= 1508; x++) zet(x, G + 7, -416, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.NORTH));
            lantaarnpaal(1505, G + 7, -415);
        }
        rustvuur(1494, y0, -436);
        rust(new Rust("wachterspost", "De Wachterspost", plek(1497, y0 + 1, -435, -90f), Box.van(1490, y0, -439, 1512, y0 + 5, -431), List.of()));
        verhaal(new Verhaal("post", Box.van(1501, y0 + 1, -439, 1507, y0 + 4, -436), List.of(
                r("Wiske", "De Wachterspost. Hier woonden de wachters."),
                r("Wiske", "Alles is stil. Waar zijn ze gebleven?"))));
        vondst(new Vondst("klauwhaak", "Klauwhaak", "vak 5 · richt op een haakpunt", Material.TRIPWIRE_HOOK, plek(1504, y0 + 2.4, -445, 0f),
                Box.van(1503, y0 + 1, -445, 1505, y0 + 3, -443)));
        verhaal(new Verhaal("uitleg-haak", Box.van(1499, y0 + 1, -445, 1509, y0 + 4, -441), List.of(
                r("Wiske", "Zie je die donkere blokken met een ring?"),
                r("Wiske", "Richt erop en rechtsklik. Hup, omhoog!")))).vereist = "klauwhaak";
        Haakpunt h1 = haakpunt(new Haakpunt("kloof1", 1513, G + 1, -431, plek(1515, G - 1, -430, 90f)));
        Haakpunt h2 = haakpunt(new Haakpunt("kloof2", 1522, G + 6, -431, plek(1524, G + 3, -430, 90f)));
        Haakpunt h3 = haakpunt(new Haakpunt("kloof3", 1527, G + 9, -431, plek(1527, G + 7, -427, 180f)));
        haak(h1, plek(1513, y0 + 1, -437, 0f));
        haak(h2, h1.landing());
        haak(h3, h2.landing());
        verhaal(new Verhaal("uitkijk", Box.van(1522, G + 7, -428, 1532, G + 10, -423), List.of(
                r("Wiske", "Boven! Kijk, daar ligt IJshoeve."),
                r("Wiske", "En in het oosten: de Bergpoort.")))).vereist = "klauwhaak";
        geit("Mekker", plek(1514, G + 7, -410, 160f));
    }

    // ── 8. de Bergpoort: de ingestorte trap, het laatste haakpunt, het begin van het bergpad ─

    private void bergpoort() {
        int vlak = G + 10;                                                  // het plateau van de poort; je staat op G+11
        if (b.plaats) {
            // het plateau en het bergpad (oost, omhoog), met muurtjes langs de rand
            for (int x = 1550; x <= 1562; x++)
                for (int z = -300; z <= -288; z++) {
                    vul(x, g + 1, z, x, vlak - 1, z, Material.STONE);
                    zet(x, vlak, z, (x + z) % 3 == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
                    vul(x, vlak + 1, z, x, vlak + 8, z, Material.AIR);
                    boolean rand = z == -300 || z == -288 || x == 1550;
                    if (rand && !(x == 1550 && Math.abs(z - PZ) <= 2)) zet(x, vlak + 1, z, Material.STONE_BRICK_WALL);
                }
            for (int z = PZ - 2; z <= PZ + 2; z++) zet(1551, vlak + 1, z, Material.STONE_BRICK_WALL);     // een lage muur onder de poort
            for (int x = 1563; x <= 1579; x++) {
                int f = vlak + Math.min(4, (x - 1562) / 3);
                for (int z = PZ - 2; z <= PZ + 2; z++) {
                    vul(x, g + 1, z, x, f - 1, z, Material.STONE);
                    zet(x, f, z, Material.SNOW_BLOCK);
                    vul(x, f + 1, z, x, f + 10, z, x >= 1575 ? Material.SNOW_BLOCK : Material.AIR);   // de lawine
                }
                for (int z : new int[]{PZ - 3, PZ + 3}) for (int y = f + 1; y <= f + 6; y++) zet(x, y, z, steen(x, y, z));
            }
            for (int x = 1575; x <= 1579; x++) for (int z = PZ - 2; z <= PZ + 2; z++) if (kans(x, z, 3) == 0) zet(x, vlak + 4 + 11, z, Material.PACKED_ICE);
            // de poort: twee zuilen en een boog, met het haakpunt eronder
            for (int z : new int[]{PZ - 3, PZ + 3}) vul(1551, vlak + 1, z, 1551, vlak + 4, z, Material.CHISELED_STONE_BRICKS);
            vul(1551, vlak + 5, PZ - 3, 1551, vlak + 5, PZ + 3, Material.STONE_BRICKS);
            zet(1551, vlak + 6, PZ, Material.CHISELED_STONE_BRICKS);
            zet(1551, vlak + 4, PZ, HAAKPUNT);
            for (int z : new int[]{PZ - 2, PZ + 2}) zet(1551, vlak + 4, z, Bouwer.trap(Material.STONE_BRICK_STAIRS, BlockFace.WEST, true));
            for (int z : new int[]{PZ - 3, PZ + 3}) zet(1550, vlak + 3, z, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.WEST));
            // de oude wachterstrap langs de wand, halverwege ingestort
            for (int x = 1541; x <= 1549; x++) {
                if (x >= 1545 && x <= 1547) {
                    if (x == 1546) zet(x, G + 1, -305, Material.COBBLESTONE);
                    continue;
                }
                int f = G + 1 + (x - 1541);
                for (int z = -305; z <= -303; z++) {
                    vul(x, G + 1, z, x, f - 1, z, Material.STONE_BRICKS);
                    zet(x, f, z, Bouwer.trap(Material.STONE_BRICK_STAIRS, BlockFace.EAST));
                }
            }
            for (int[] k : new int[][]{{1545, -302}, {1547, -306}, {1544, -301}}) zet(k[0], G + 1, k[1], Material.COBBLESTONE);
            lantaarnpaal(1542, G + 1, -290);
            zet(1558, vlak + 1, -289, Material.LANTERN);
        }
        Verhaal voet = verhaal(new Verhaal("bergvoet", Box.van(1538, G, -299, 1549, G + 5, -289), List.of(
                r("Wiske", "De trap is ingestort. Hier kom je niet op."),
                r("Wiske", "Tenzij je klimt als een bergwachter…"))));
        voet.als = s -> !s.heeft("klauwhaak");
        verhaal(new Verhaal("bergvoet-haak", Box.van(1538, G, -299, 1549, G + 5, -289), List.of(
                r("Wiske", "Daar boven, in de poort: een haakpunt!")))).vereist = "klauwhaak";
        Haakpunt h4 = haakpunt(new Haakpunt("bergpoort", 1551, vlak + 4, PZ, plek(1553, vlak + 1, PZ, -90f)));
        haak(h4, plek(1542, G + 1, PZ, -90f));
        rustvuur(1559, vlak, PZ - 3);
        rust(new Rust("bergpoort", "De Bergpoort", plek(1557, vlak + 1, PZ, -90f), Box.van(1551, vlak, -300, 1562, vlak + 6, -288), List.of()));
        Verhaal einde = verhaal(new Verhaal("bergpoort", Box.van(1552, vlak, -298, 1562, vlak + 6, -290), List.of(
                r("Wiske", "De Bergpoort! Hier begint het bergpad."),
                r("Wiske", "Daarboven ligt het IJspaleis. En het Vorstbaken."),
                r(null, "De Witte Toppen · het dal voltooid"))));
        einde.vereist = "klauwhaak";
        einde.einde = true;
        verhaal(new Verhaal("lawine", Box.van(1568, vlak + 1, PZ - 2, 1574, vlak + 8, PZ + 2), List.of(
                r("Wiske", "Een lawine! Hier kunnen we nog niet door."))));
    }

    // ── versiering: sneeuwpoppen, een geitenweide, houtstapels, lantaarns langs de wegen ──

    private void sneeuwpop(int x, int z, BlockFace kijk) {
        int y = top[x - X1][z - Z1] + 1;
        zet(x, y, z, Material.SNOW_BLOCK);
        zet(x, y + 1, z, Material.SNOW_BLOCK);
        zet(x, y + 2, z, Bouwer.gericht(Material.CARVED_PUMPKIN, kijk));
        zet(x + 1, y + 1, z, Bouwer.gericht(Material.SPRUCE_FENCE, kijk));
    }

    private void versiering() {
        sneeuwpop(1468, -281, BlockFace.EAST);                              // bij de stal (Sven bouwde hem)
        sneeuwpop(1451, -306, BlockFace.WEST);                              // aan het meer, bij Noor
        sneeuwpop(1492, -309, BlockFace.SOUTH);
        // de geitenweide onder de stal (met een opening naar het dorp)
        for (int x = 1452; x <= 1466; x++)
            for (int z = -279; z <= -270; z++) {
                boolean rand = x == 1452 || x == 1466 || z == -279 || z == -270;
                int y = top[x - X1][z - Z1] + 1;
                if (soort[x - X1][z - Z1] != SNEEUW || pad[x - X1][z - Z1]) continue;
                if (rand && !(z == -279 && (x == 1459 || x == 1460))) zet(x, y, z, x == 1452 || x == 1466 ? hek(Material.SPRUCE_FENCE, true) : hek(Material.SPRUCE_FENCE, false));
                else if (!rand && kans(x, z, 7) == 0) zet(x, y, z, Material.SHORT_GRASS);
            }
        zet(1455, top[1455 - X1][-273 - Z1] + 1, -273, Material.HAY_BLOCK);
        // houtstapels bij de huizen (naast de deur)
        for (int[] st : new int[][]{{1473, -312}, {1490, -312}, {1455, -306}})
            for (int i = 0; i < 3; i++) {
                zet(st[0] + i, G + 1, st[1], stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
                if (i < 2) zet(st[0] + i, G + 2, st[1], stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            }
        // een slee bij Noor
        zet(1449, G + 1, -303, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.WEST));
        zet(1448, G + 1, -303, Bouwer.plaat(Material.SPRUCE_SLAB, false));
        // lantaarns langs de wegen (niet op het pad, niet bij het ijs van de beek)
        int[][][] wegen = {{{1466, -229}, {1468, -240}, {1474, -255}, {1478, -270}}, {{1546, -294}, {1510, -294}},
                {{1482, -320}, {1488, -340}, {1496, -365}, {1500, -398}}};
        for (int[][] weg : wegen)
            for (int i = 0; i + 1 < weg.length; i++) {
                int[] a = weg[i], c = weg[i + 1];
                double len = Math.hypot(c[0] - a[0], c[1] - a[1]);
                for (double t = 6; t < len; t += 13) {
                    int x = (int) Math.round(a[0] + (c[0] - a[0]) * t / len + (c[1] - a[1]) / len * 3.2);
                    int z = (int) Math.round(a[1] + (c[1] - a[1]) * t / len - (c[0] - a[0]) / len * 3.2);
                    boolean dorp = x >= VLAK[0][0] && x <= VLAK[0][2] && z >= VLAK[0][1] && z <= VLAK[0][3];
                    if (x < X1 || x > X2 || z < Z1 || z > Z2 || pad[x - X1][z - Z1] || soort[x - X1][z - Z1] != SNEEUW || dorp) continue;
                    boolean ijs = false;
                    for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) ijs |= beek(x + dx, z + dz);
                    if (!ijs) lantaarnpaal(x, top[x - X1][z - Z1] + 1, z);
                }
            }
    }

    /**
     * Zonder blokken (dus zonder herbouw): de doelen voor {@code /hint} en Tijmen. Eerst de lantaarn van Tijmen, dan de
     * storm en het vosje, de deur die hij opgraaft, de klauwhaak, de klim, en de Bergpoort.
     */
    private void motor() {
        doel("tijmen3", "Een dorp in de sneeuw. Daar staat Tijmen!", "Tijmen staat op het plein van IJshoeve.",
                plekVan("Tijmen"), s -> s.heeft("lantaarn"));
        doel("storm", "Achter het dorp ligt het Sparrenbos.", "Volg in de storm de pootjes, met de lantaarn.",
                plekVan("kuil"), s -> s.heeft("v:kuil") || s.heeft("k:kuil"));
        doel("kuil", "Wolven bij het vosje! Help hem.", "Jaag de wolven in de kuil weg.",
                plekVan("kuil"), s -> s.heeft("k:kuil"));
        doel("vosje", "Het vosje rilt nog. Ga naar hem toe.", "Praat met het vosje in de kuil.",
                plekVan("Vosje"), s -> s.heeft("dier:sneeuwvos"));
        doel("deur", "Volg je vosje naar het noorden.", "Bij de rotswand ruikt hij iets. Loop erheen.",
                plekVan("deur"), s -> s.heeft(DEUR));
        doel("post", "Een gang onder de rots. Volg hem.", "In de Wachterspost ligt een klauwhaak.",
                plekVan("klauwhaak"), s -> s.heeft("klauwhaak"));
        doel("klim", "Haak je omhoog, de kloof uit.", "Klauwhaak in vak 5. Richt op een haakpunt.",
                plekVan("wachterspost"), s -> s.heeft("v:uitkijk") || s.heeft("v:bergvoet-haak") || s.heeft(id()));
        doel("bergpoort", "Naar de Bergpoort, oost van het dorp.", "Haak je omhoog in de poort, boven de trap.",
                new Location(w, 1544.5, G + 1, PZ + 0.5), s -> s.heeft(id()));
    }

    // ── tijdens het spel: sneeuw, de storm, de pootjes ──────────────────────────────────────

    @Override
    void tik(ZeldaSpel spel, List<Player> wie, long nu) {
        if (storm == null) return;
        Particle.DustOptions poot = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x8fa7c4), 0.7f);
        for (Player p : wie) {
            if (!p.getWorld().equals(w) || spel.beheer.is(p.getUniqueId())) continue;
            Location l = p.getLocation();
            ZeldaVoortgang.Stand s = spel.voortgang.van(p.getUniqueId());
            boolean inStorm = storm.in(l), buiten = w.getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) <= l.getBlockY() + 1;
            if (buiten) sneeuw(p, l, inStorm && !s.heeft("k:kuil") ? 70 : inStorm ? 25 : 10, inStorm);
            if (inStorm && nu % 6000 < 1000) p.playSound(l, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.08f, 0.6f);
            if (inStorm && s.heeft("lantaarn")) {
                p.spawnParticle(Particle.END_ROD, l.clone().add(0, 1.2, 0), 2, 0.6, 0.4, 0.6, 0.005);
                if (!s.heeft("dier:sneeuwvos"))                             // de pootjes: alleen te zien in het licht van de lantaarn
                    for (int i = 0; i + 1 < POOTJES.length; i++) {
                        int[] a = POOTJES[i], c = POOTJES[i + 1];
                        double len = Math.hypot(c[0] - a[0], c[1] - a[1]);
                        for (double t = 0; t < len; t += 1.3) {
                            double x = a[0] + 0.5 + (c[0] - a[0]) * t / len, z = a[1] + 0.5 + (c[1] - a[1]) * t / len;
                            if ((x - l.getX()) * (x - l.getX()) + (z - l.getZ()) * (z - l.getZ()) > 18 * 18) continue;
                            double zij = (Math.round(t / 1.3) % 2 == 0 ? 0.18 : -0.18);
                            p.spawnParticle(Particle.DUST, new Location(w, x + zij * (c[1] - a[1]) / len, G + 1.05, z - zij * (c[0] - a[0]) / len), 1, 0, 0, 0, 0, poot);
                        }
                    }
            }
            if (!s.heeft(DEUR) && inGang(l) && l.getX() >= 1453 && l.getZ() <= -426) {   // samen door de opgegraven deur: dan heb jij hem ook
                s.zet(DEUR);
                spel.voortgang.vuil();
            }
            if (diep.in(l) && !s.heeft("lantaarn") && !spel.inGevecht(p)) {   // zonder licht verdwaal je in de storm: terug naar de bosrand
                p.setFallDistance(0);
                p.teleport(stormUit);
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
                Long vorige = verdwaald.get(p.getUniqueId());
                if (vorige == null || nu - vorige > 30_000L) {
                    verdwaald.put(p.getUniqueId(), nu);
                    spel.vertel(p, r("Wiske", "Ik zie niks meer! Terug, voor we verdwalen."));
                }
            }
        }
    }

    /** Sneeuw die om je heen valt (alleen voor jou); in de storm veel, en wat as die waait. */
    private void sneeuw(Player p, Location l, int n, boolean storm) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            Location v = l.clone().add(r.nextDouble(-11, 11), r.nextDouble(2, 9), r.nextDouble(-11, 11));
            p.spawnParticle(Particle.SNOWFLAKE, v, 1, 0, 0, 0, storm ? 0.05 : 0.0);
            if (storm && i % 3 == 0) p.spawnParticle(Particle.WHITE_ASH, v.add(0, -3, 0), 2, 1, 1, 1, 0);
        }
    }
}
