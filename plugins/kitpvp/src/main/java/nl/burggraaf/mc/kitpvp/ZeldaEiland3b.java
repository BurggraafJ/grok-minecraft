package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

/**
 * Eiland 3b, «De Witte Toppen»: de berg en het IJspaleis (zie ZELDA.md). Achter de Bergpoort van het dal (3a) loopt het
 * bergpad verder door een kloof naar de berghut, waar klimster Ida uitrust. Met de klauwhaak kom je de ijswand op en over
 * een ijskloof, op het voorplein van het IJspaleis van de oude bergwachters: een plateau met een zielvuur, een bron, de
 * IJstuin (achter een gebarsten muur: de sneeuwbloem voor Hanne en de hoorn van Ys, over een tweede glijvloer) en de oude
 * sleebaan naar beneden (een rondje, en een tijdrace).
 *
 * <p>De valbrug is op: haak je naar de poorttoren en trek binnen aan de hendel (dan is de brug er voortaan). In de
 * Poortzaal roept iemand achter een gebarsten muur: Tijmen, die er weer eerder was en opgesloten raakte. Hij geeft een
 * zonnesteen. De Kristalzaal is de hub van het paleis: noord de Glijzaal (glad ijs: glijden tot je ergens tegenaan
 * botst; aan de overkant het Noorderkristal, en dan alleen verder door de Wachtzaal: het enige gevecht, drie gemengde
 * golven), zuid de Spiegelzaal (met de zonnesteen gaat er licht aan; draai de spiegels, de ijsdeur smelt), over een
 * kloof met de klauwhaak naar de Wapenkamer: het Zuiderkristal en het bevroren harnas van de wachters. Dat warmt Hagen
 * in IJshoeve (terug naar de hub): het wachtersharnas. Met twee kristallen en het harnas gaat de troondeur open:
 * Grimmel, de reuzenijsbeer ({@link Grimmel}). Dan het hart van licht ({@code hart3}) en de toren op naar het Vorstbaken
 * ({@code baken3}): de Gloedberg wordt aangekondigd.
 *
 * <p>Zijmissies: de hoorn van Ys (in de IJstuin; Ys geeft er het vierde hartstukje voor) en de sneeuwbloem (een
 * boodschap over zee: Hanne op het Lindewoud brouwt er een vijfde flesje van). Rustplaatsen houden hun nummer: zet een
 * nieuwe altijd achteraan.
 */
final class ZeldaEiland3b extends ZeldaHoofdstuk {

    static final int VERSIE = 1;
    /** Het vak van dit halve eiland (oost van 3a); elke kolom zet dit deel zelf. */
    static final int X1 = 1580, X2 = 1720, Z1 = -480, Z2 = -180;
    /** De as van het bergpad en het paleis (z), en de hartstukjes en vlaggen van 3b. */
    static final int PZ = -294;
    static final String STUKJE_HOORN = "hs:witte-toppen-4";
    static final String NOORD = "kristal-noord", ZUID = "kristal-zuid", IJSHARNAS = "ijsharnas", ZONNESTEEN = "zonnesteen";

    private final Plugin plugin;
    private Bouwer b;
    int G, F;                                           // G = pad-hoogte van het dal (je staat op G+1); F = de vloer van het paleis (G+26)
    private int g;
    private int[][] top;                                // hoogste blok per kolom (alleen tijdens het bouwen)
    private byte[][] soort;

    private static final byte ZEE = 0, ROTS = 1, SNEEUW = 2, STEEN = 3, DIEP = 4, PAD = 5, BAAN = 6;

    /** Wat er alleen tijdens het spel is (voor tik): de baas, de spiegelzaal (de zonnesteen), het baken. */
    private Grimmel grimmel;
    private Spiegels spiegelzaal;
    private Location bakenKern;
    private boolean zonAan;
    /** Staat Hagen (eiland 3a: het wachtersharnas) op het pad? Nee = 3a was er niet bij het bouwen (de zelftest meldt het). */
    boolean hagenOpPad;

    ZeldaEiland3b(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override String id() { return "e3b"; }
    @Override String naam() { return "De Witte Toppen"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda-e3b"; }

    /** Wegen in elke volgorde (het voorplein, de Kristalzaal): «opnieuw vanaf hier» vergeet alleen tot de volgende rustplaats. */
    @Override boolean open() { return true; }

    // ── tijd, naam en muziek per plek ───────────────────────────────────────────────────────

    /** Late middag op het bergpad, zonsondergang op het voorplein, schemer in het paleis; brandt het baken, dan wordt het ochtend. */
    @Override
    long hemel(Location l, ZeldaVoortgang.Stand s) {
        if (s.heeft("baken3")) return 1500;
        return switch (plekNaam(l)) {
            case "Het IJspaleis", "De Troonzaal", "De toren" -> 13300;
            case "Het Vorstbaken" -> 13000;
            case "Het voorplein", "De IJstuin", "De sleebaan" -> 12000;
            default -> 11000;
        };
    }

    private boolean inPaleis(Location l) {
        double x = l.getX(), z = l.getZ(), y = l.getY();
        return x >= 1634 && x <= 1712 && z >= -336 && z <= -255 && y >= F - 8 && y <= F + 17;
    }

    @Override
    String plekNaam(Location l) {
        double x = l.getX(), z = l.getZ(), y = l.getY();
        if (x >= 1684 && x <= 1702 && z >= -321 && z <= -305 && y >= F + 20) return "Het Vorstbaken";
        if (x >= 1689 && x <= 1695 && z >= -316 && z <= -306) return "De toren";
        if (x >= 1680 && x <= 1711 && z >= -306 && z <= -282 && y >= F - 2 && y <= F + 14) return "De Troonzaal";
        if (inPaleis(l)) return "Het IJspaleis";
        if (x >= 1609 && x <= 1630 && z >= -336 && z <= -309) return "De IJstuin";
        if (x >= 1608 && x <= 1633 && z >= -308 && z <= -282) return "Het voorplein";
        if (x >= 1586 && x <= 1616 && z >= -270 && z <= -264 || x >= 1586 && x <= 1592 && z >= -281 && z <= -264) return "De sleebaan";
        if (x <= 1593 && z >= -306 && z <= -280) return "De berghut";
        if (x <= 1610) return "Het bergpad";
        return "De Witte Toppen";
    }

    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return switch (plekNaam(l)) {
            case "Het IJspaleis", "De Troonzaal", "De toren", "Het Vorstbaken" -> ZeldaMuziek.IJSPALEIS;
            default -> ZeldaMuziek.BERGPAD;
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

    /** Een hendel op de vloer. */
    static BlockData hendel(BlockFace kijk, boolean aan) {
        Switch s = (Switch) Material.LEVER.createBlockData();
        s.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        s.setFacing(kijk);
        s.setPowered(aan);
        return s;
    }

    private Material steen(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 14);
        return k < 5 ? Material.STONE : k < 7 ? Material.ANDESITE : k < 9 ? Material.TUFF : k < 11 ? Material.CALCITE : k < 13 ? Material.COBBLESTONE : Material.DIORITE;
    }

    /** De muren van het IJspaleis: ijs, met hier en daar steen van de wachters. */
    private Material ijsmuur(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 12);
        return k < 7 ? Material.PACKED_ICE : k < 9 ? Material.CALCITE : k < 11 ? Material.POLISHED_DIORITE : Material.BLUE_ICE;
    }

    /** De vloer van het paleis (in een patroon). */
    private Material paleisvloer(int x, int z) {
        return (x + z) % 4 == 0 ? Material.QUARTZ_BRICKS : (x * 3 + z) % 7 == 0 ? Material.POLISHED_DIORITE : Material.CALCITE;
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
        zet(x, y + hoog + 2, z, Material.SNOW_BLOCK);
    }

    /** Een spits van ijs (zoals ijspieken): {@code hoog} hoog, onderaan breder. */
    private void ijspiek(int x, int y, int z, int hoog) {
        for (int k = 0; k < hoog; k++) {
            int r = k < hoog / 4 ? 1 : 0;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) if (Math.abs(dx) + Math.abs(dz) <= r) zet(x + dx, y + k, z + dz, k % 5 == 3 ? Material.BLUE_ICE : Material.PACKED_ICE);
        }
    }

    // ── het terrein ─────────────────────────────────────────────────────────────────────────

    /** Hoe ver je van de zee bent (negatief = zee): de kust van het vak (noord, zuid en oost; in het westen ligt 3a). */
    private static double kust(int x, int z) {
        return Math.min(Math.min(z + 474, -186 - z), 1716 - x) + (zacht(x, z, 7) - 0.5) * 4;
    }

    private static double bult(int x, int z, int cx, int cz, double r) {
        double d2 = (x - cx) * (x - cx) + (double) (z - cz) * (z - cz);
        return Math.exp(-d2 / (2 * r * r));
    }

    /**
     * Hoe hoog de berg hier is (boven G): nooit lager dan G+30 (zo kom je nergens via de berg om de haakpunten heen, en
     * aan de rand met 3a is hij even hoog), met toppen in het noorden en het zuiden en ruggen en kuilen ertussen.
     */
    private static int berg(int x, int z) {
        double h = 31 + 17 * bult(x, z, 1650, -392, 24) + 13 * bult(x, z, 1668, -216, 18) + 9 * bult(x, z, 1702, -428, 16)
                + 9 * bult(x, z, 1606, -442, 15) + 7 * bult(x, z, 1600, -214, 13)
                + Math.max(0, (zacht(x + 11, z - 7, 11) - 0.35) * 14) + (zacht(x - 5, z + 3, 5) - 0.5) * 3;
        if (x < 1592) h = 30 + (h - 30) * Math.max(0, x - 1580) / 12.0;     // aan de rand: zoals het dal (3a, G+30)
        return (int) Math.round(Math.max(30, h));
    }

    /** De oude sleebaan (drie breed): de vloer boven G, of -1. Van het plateau west omlaag, dan noord naar de berghut. */
    private static int baan(int x, int z) {
        if (z >= -268 && z <= -266 && x >= 1590 && x <= 1610) return 16 + (x - 1590) / 2;
        if (x >= 1588 && x <= 1589 && z >= -268 && z <= -266) return 16;
        if (x >= 1588 && x <= 1590 && z >= -282 && z <= -269) return z >= -275 ? 16 : z >= -279 ? 15 : 14;
        return -1;
    }

    /**
     * De vlakke plekken: {hoogte boven G, soort}, of null (berg). Het bergpad, de berghut (met de afstap van de ijswand),
     * boven de ijswand, de ijskloof, de overkant, en het plateau van het paleis (voorplein, gracht, paleis, IJstuin).
     */
    private static int[] vlak(int x, int z) {
        if (x >= 1580 && x <= 1583 && z >= -296 && z <= -292) return new int[]{14, PAD};          // het bergpad door de rots
        if (x >= 1584 && x <= 1593 && z >= -304 && z <= -282) {                                     // de berghut
            if (z >= -288 && z <= -287 && x >= 1592) return new int[]{x == 1593 ? 19 : 16, ROTS}; // de afstap van de ijswand (alleen omlaag)
            return new int[]{14, SNEEUW};
        }
        if (x >= 1594 && x <= 1601 && z >= -301 && z <= -286) return new int[]{22, SNEEUW};       // boven de ijswand
        if (x >= 1602 && x <= 1607 && z >= -301 && z <= -286) return new int[]{16, DIEP};         // de ijskloof
        if (x >= 1608 && x <= 1610 && z >= -298 && z <= -290) return new int[]{x == 1610 ? 25 : 24, SNEEUW};   // de overkant
        if (x >= 1611 && z >= -345 && z <= -248) {                                                   // het plateau
            if (x >= 1629 && x <= 1633 && z >= -300 && z <= -288) return new int[]{20, DIEP};      // de gracht
            if (x <= 1628 && z >= -308 && z <= -282) return new int[]{26, STEEN};                  // het voorplein
            if (x >= 1634 && x <= 1711 && z >= -335 && z <= -256) return new int[]{26, STEEN};     // het paleis
            if (x <= 1629 && z >= -335 && z <= -309) return new int[]{26, STEEN};                  // de IJstuin
            return new int[]{26, SNEEUW};
        }
        return null;
    }

    /** Hoogte (bovenste blok) en soort van een kolom. */
    private int[] vorm(int x, int z) {
        double d = kust(x, z);
        if (d <= 0) return new int[]{G - 2 - (int) Math.min(6, -d / 2.5), ZEE};
        int bn = baan(x, z);
        if (bn >= 0) return new int[]{G + bn, BAAN};
        int[] v = vlak(x, z);
        int hoog = v != null ? v[0] : berg(x, z);
        byte s = v != null ? (byte) v[1] : ROTS;
        boolean paleis = v != null && v[1] == STEEN;
        if (d < 5 && !paleis) {                                                                        // een klif naar zee
            int klif = (int) (d * 6) - 2;
            if (klif < hoog) {
                hoog = Math.max(-1, klif);
                s = ROTS;
            }
        }
        return new int[]{G + hoog, s};
    }

    private void terrein() {
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
                    vul(x1, G, z1, x2, G + 70, z2, Material.AIR);
                } else for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) kolom(x, z, top[x - X1][z - Z1], soort[x - X1][z - Z1]);
            }
        // een borstwering waar het plateau naar zee afloopt (niet bij de gracht)
        for (int x = X1 + 1; x < X2; x++)
            for (int z = Z1 + 1; z < Z2; z++) {
                if (soort[x - X1][z - Z1] != SNEEUW || top[x - X1][z - Z1] != F) continue;
                boolean rand = false;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ax = x + d[0] - X1, az = z + d[1] - Z1;
                    rand |= top[ax][az] < F - 2 && (soort[ax][az] == ROTS || soort[ax][az] == ZEE);
                }
                if (rand) zet(x, F + 1, z, Material.STONE_BRICK_WALL);
            }
        // sparren op de lagere hellingen, ijspieken op het plateau (niet op de wegen)
        for (int x = X1 + 3; x <= X2 - 3; x++)
            for (int z = Z1 + 3; z <= Z2 - 3; z++) {
                byte s = soort[x - X1][z - Z1];
                int y = top[x - X1][z - Z1];
                if (s == ROTS && y >= G + 8 && y <= G + 34 && Math.floorMod(x + kans(z, x, 3), 9) == 0 && Math.floorMod(z + kans(x, z, 3), 9) == 0
                        && kans(x, z, 3) == 0 && vrijRond(x, z, 2, y)) spar(x, y + 1, z, 6 + kans(x + 1, z, 4));
                else if (s == SNEEUW && y == F && (x > 1632 || z < -312 || z > -278) && Math.floorMod(x + kans(z, x, 4), 13) == 0
                        && Math.floorMod(z + kans(x, z, 4), 11) == 0 && vrijRond(x, z, 3, y) && !bijPaleis(x, z)) ijspiek(x, y + 1, z, 5 + kans(x, z, 7));
            }
    }

    /** Is de grond rondom (straal r) even hoog en van dezelfde soort (ruimte voor een boom of een ijspiek)? */
    private boolean vrijRond(int x, int z, int r, int y) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                int ax = x + dx - X1, az = z + dz - Z1;
                if (ax < 0 || az < 0 || ax >= top.length || az >= top[0].length) return false;
                if (top[ax][az] != y || soort[ax][az] != soort[x - X1][z - Z1]) return false;
            }
        return true;
    }

    /** Is het hier steil (een buur minstens vier lager)? */
    private boolean steil(int x, int z, int hg) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int ax = x + d[0] - X1, az = z + d[1] - Z1;
            if (ax >= 0 && az >= 0 && ax < top.length && az < top[0].length && top[ax][az] <= hg - 4) return true;
        }
        return false;
    }

    /** Vlak bij het paleis, de IJstuin of de sleebaan (daar geen ijspieken). */
    private static boolean bijPaleis(int x, int z) {
        return x >= 1606 && x <= 1716 && z >= -340 && z <= -251 || x <= 1622 && z >= -276 && z <= -258;
    }

    private void kolom(int x, int z, int hg, byte s) {
        int ymax = G + 70;
        if (s == ZEE) {
            vul(x, g + 1, z, x, hg - 1, z, Material.STONE);
            zet(x, hg, z, kans(x, z, 4) == 0 ? Material.SAND : Material.GRAVEL);
            vul(x, hg + 1, z, x, G - 1, z, Material.WATER);
            vul(x, G, z, x, ymax, z, Material.AIR);
            return;
        }
        vul(x, g + 1, z, x, hg - 1, z, Material.STONE);
        Material opp = switch (s) {
            case ROTS -> hg >= G + 12 ? (kans(x, z, 13) == 0 ? Material.PACKED_ICE : Material.SNOW_BLOCK) : steen(x, hg, z);
            case STEEN -> (x + z) % 3 == 0 ? Material.STONE_BRICKS : kans(x, z, 4) == 0 ? Material.CRACKED_STONE_BRICKS : Material.POLISHED_ANDESITE;
            case PAD -> kans(x, z, 3) == 0 ? Material.GRAVEL : Material.SNOW_BLOCK;
            case BAAN -> (z == -267 && x >= 1590) || (x == 1589 && z <= -266) ? Material.BLUE_ICE : Material.PACKED_ICE;
            default -> Material.SNOW_BLOCK;
        };
        if (s == ROTS) for (int y = Math.max(g + 1, hg - 4); y < hg; y++) zet(x, y, z, steen(x, y, z));
        if (s == ROTS && steil(x, z, hg)) opp = steen(x, hg, z);                 // een steile rots: geen sneeuw
        zet(x, hg, z, opp);
        vul(x, hg + 1, z, x, ymax, z, Material.AIR);
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        g = bouwer.g;
        G = g + 8;
        F = G + 26;
        leeg(bouwer.w);
        hagenOpPad = false;
        speelgebied = Box.van(X1, g - 4, Z1 + 2, X2 - 1, G + 72, Z2 - 2);
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
        bergpad();
        voorplein();
        ijstuin();
        sleebaan();
        if (bouwen) paleisRomp();
        poortzaal();
        kristalzaal();
        glijzaal();
        wachtzaal();
        oostgang();
        spiegelzaal();
        hagen();
        troonzaal();
        toren();
        if (bouwen) {
            b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = null;
            soort = null;
        }
        // de begintoestand van deuren, poorten en puzzels: altijd (ook zonder herbouw)
        for (Kamer k : kamers) {
            zetDeur(k.in, true);
            zetDeur(k.uit, false);
        }
        for (Uitdaging u : uitdagingen) zetDeur(u.poort, false);
        for (Barst br : barsten) zetDeur(br.muur, false);
        for (Poort po : poorten) zetDeur(po.deur, false);
        for (Reeks r : reeksen) {
            zetDeur(r.deur, false);
            for (int[] d : r.dingen) b.zetAltijd(d[0], d[1], d[2], r.uit);
        }
        for (Spiegels sp : spiegels) {
            for (int i = 0; i < sp.spiegels.size(); i++) {
                int[] c = sp.spiegels.get(i);
                b.zetAltijd(c[0], c[1], c[2], Spiegels.blok(sp.begin.get(i)));
            }
            b.zetAltijd(sp.doel[0], sp.doel[1], sp.doel[2], sp.doelUit);
            zetDeur(sp.deur, false);
            b.zetAltijd(sp.bron[0], sp.bron[1], sp.bron[2], ZON_UIT);
        }
        zonAan = false;
        if (grimmel != null) for (int[] c : grimmel.zuilen)
            for (int x = c[0]; x <= c[2]; x++)
                for (int z = c[1]; z <= c[3]; z++)
                    for (int y = c[4]; y <= c[5]; y++) b.zetAltijd(x, y, z, Grimmel.zuilBlok(c, y));
        motor();
        b = null;
    }

    // ── 1. het bergpad: de berghut, de ijswand, de ijskloof ──────────────────────────────────

    private void bergpad() {
        int y0 = G + 14;                                                    // de vloer bij de berghut; je staat op G+15
        if (b.plaats) {
            // de berghut van de bergwachters: steen en sparrenhout, een bankje ervoor
            for (int x = 1585; x <= 1590; x++)
                for (int z = -304; z <= -299; z++) {
                    boolean muur = x == 1585 || x == 1590 || z == -304 || z == -299;
                    zet(x, y0, z, Material.SPRUCE_PLANKS);
                    for (int y = y0 + 1; y <= y0 + 3; y++) {
                        boolean hoek = (x == 1585 || x == 1590) && (z == -304 || z == -299);
                        boolean raam = y == y0 + 2 && !hoek && z == -299 && x == 1586;
                        zet(x, y, z, !muur ? Material.AIR : hoek ? Material.SPRUCE_LOG : y == y0 + 1 ? Material.COBBLESTONE
                                : raam ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.SPRUCE_PLANKS);
                    }
                }
            for (int z = -305; z <= -298; z++) {                            // een schuin dak met sneeuw
                int y = y0 + 4 + Math.min(z + 305, -298 - z) / 2;
                for (int x = 1584; x <= 1591; x++) zet(x, y, z, Material.SNOW_BLOCK);
            }
            vul(1588, y0 + 1, -299, 1588, y0 + 2, -299, Material.AIR);          // de deur
            zet(1586, y0 + 1, -303, vuur(Material.CAMPFIRE, true));
            zet(1589, y0 + 1, -303, Material.BARREL);
            zet(1587, y0 + 3, -301, Bouwer.hangend(Material.LANTERN));
            for (int x = 1585; x <= 1586; x++) zet(x, y0 + 1, -297, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.NORTH));
            // klimspullen bij de wand
            zet(1592, y0 + 1, -299, Material.BARREL);
            zet(1592, y0 + 1, -300, Material.HAY_BLOCK);
            lantaarnpaal(1584, y0 + 1, -290);
            // het haakpunt boven op de ijswand, een blok over de rand (van onderaf te raken)
            zet(1593, G + 22, PZ, ZeldaHoofdstuk.HAAKPUNT);
            zet(1594, G + 23, PZ - 1, Material.SPRUCE_FENCE);
            zet(1594, G + 23, PZ + 1, Material.SPRUCE_FENCE);
            // de ijskloof: een ladder terug omhoog (alleen aan deze kant), en aan beide kanten een paal met een haakpunt
            for (int y = G + 17; y <= G + 22; y++) zet(1602, y, -300, Bouwer.gericht(Material.LADDER, BlockFace.EAST));
            vul(1609, G + 25, PZ - 2, 1609, G + 28, PZ - 2, Material.SPRUCE_LOG);
            zet(1608, G + 28, PZ - 2, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            zet(1608, G + 27, PZ - 2, ZeldaHoofdstuk.HAAKPUNT);
            vul(1600, G + 23, PZ + 2, 1600, G + 26, PZ + 2, Material.SPRUCE_LOG);
            zet(1601, G + 26, PZ + 2, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
            zet(1601, G + 25, PZ + 2, ZeldaHoofdstuk.HAAKPUNT);
            zet(1608, G + 25, -298, Material.LANTERN);
            bord(1591, y0 + 1, -296, BlockFace.WEST, "Het IJspaleis");
        }
        rustvuur(1589, y0, -287);
        rust(new Rust("berghut", "De berghut", plek(1588, y0 + 1, PZ, -90f), Box.van(1584, y0, -303, 1593, y0 + 6, -283), List.of()));
        verhaal(new Verhaal("bergpad", Box.van(1580, y0, -297, 1585, y0 + 6, -291), List.of(
                r("Wiske", "Een hutje, tegen de berg."),
                r("Wiske", "En daarboven… een paleis van ijs!"))));
        ida();
        verhaal(new Verhaal("ijswand", Box.van(1588, y0, -297, 1593, y0 + 5, -291), List.of(
                r("Wiske", "Een steile wand van ijs."),
                r("Wiske", "Bovenop steekt een haakpunt uit!")))).als = s -> !s.heeft("v:ijskloof");
        Haakpunt wand = haakpunt(new Haakpunt("ijswand", 1593, G + 22, PZ, plek(1595, G + 23, PZ, 90f)));
        haak(wand, plek(1587, y0 + 1, PZ, -90f));
        verhaal(new Verhaal("ijskloof", Box.van(1596, G + 22, -298, 1601, G + 27, -289), List.of(
                r("Wiske", "Een kloof! Daar kom je niet overheen."),
                r("Wiske", "Behalve met… je weet het al.")))).als = s -> !s.bezocht(id(), 1);
        Haakpunt kloof = haakpunt(new Haakpunt("ijskloof", 1608, G + 27, PZ - 2, plek(1609, G + 25, PZ, 90f)));
        haakpunt(new Haakpunt("ijskloof-terug", 1601, G + 25, PZ + 2, plek(1599, G + 23, PZ + 4, -90f)));
        haak(kloof, plek(1598, G + 23, PZ, -90f));
    }

    /** Ida, een klimster die bij de berghut uitrust: ze vertelt over de wand, het paleis en de oude sleebaan. */
    private void ida() {
        Persoon ida = persoon(new Persoon("Ida", plek(1586, G + 15, -296, 160f), beroep("cartographer"), List.of(
                r("Ida", "Hier waait het altijd. Daar word je taai van."))));
        ida.streek = Villager.Type.SNOW;
        ida.eenmalig("ida-1", null, List.of(
                        r("Ida", "Hoi! Ik ben Ida. Ik klim graag."),
                        r("Ida", "Naar het paleis? Die wand is te glad voor mij."),
                        r("Ida", "Met een klauwhaak kom je er wel.")), null)
                .eenmalig("ida-baan", s -> s.bezocht(id(), 1), List.of(
                        r("Ida", "Je was boven! Zag je de oude sleebaan?"),
                        r("Ida", "Stap boven op de plaat. Haal je het in vijftien tellen?")), null)
                .eenmalig("ida-record", s -> s.beste("sleebaan") != null, List.of(
                        r("Ida", "Wat een vaart! Je tijd staat op het bord.")), null)
                .eenmalig("ida-grimmel", s -> s.heeft("k:troonzaal"), List.of(
                        r("Ida", "Het brullen in de berg is gestopt."),
                        r("Ida", "Was dat jij? Dan klim ik morgen naar boven!")), null);
    }

    // ── 2. het voorplein (de hub van het paleis) ────────────────────────────────────────────

    private void voorplein() {
        if (b.plaats) {
            // twee bergwachters van steen bij de gracht, lantaarns, bordjes
            for (int z : new int[]{-301, -287}) {
                zet(1627, F + 1, z, Material.POLISHED_DIORITE);
                vul(1627, F + 2, z, 1627, F + 3, z, Material.QUARTZ_PILLAR);
                zet(1627, F + 4, z, Material.CHISELED_QUARTZ_BLOCK);
                zet(1626, F + 3, z, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.WEST));
                zet(1627, F + 5, z, Material.LIGHTNING_ROD);
            }
            for (int[] l : new int[][]{{1611, -307}, {1628, -307}, {1611, -283}, {1628, -283}}) lantaarnpaal(l[0], F + 1, l[1]);
            bord(1625, F + 1, -297, BlockFace.WEST, "Het IJspaleis");
            bord(1619, F + 1, -306, BlockFace.SOUTH, "De IJstuin");
            bord(1614, F + 1, -283, BlockFace.NORTH, "De sleebaan");
            bord(1612, F + 1, -290, BlockFace.EAST, "Het bergpad");
            // de gracht voor de poort: een ladder eruit (aan de kant van het plein)
            for (int y = F - 5; y <= F; y++) zet(1629, y, -299, Bouwer.gericht(Material.LADDER, BlockFace.EAST));
            for (int x = 1630; x <= 1632; x++) zet(x, F - 5, -296, Material.PACKED_ICE);
        }
        rustvuur(1614, F, -300);
        rust(new Rust("voorplein", "Het voorplein", plek(1617, F + 1, PZ, -90f), Box.van(1611, F, -303, 1622, F + 6, -285), List.of()));
        bron("bron-voorplein", plek(1622, F + 1, -286, 0f));
        Verhaal plein = verhaal(new Verhaal("paleiszicht", Box.van(1611, F, -298, 1616, F + 5, -290), List.of(
                r("Wiske", "Het IJspaleis! Wat een kou komt eraf."),
                r("Wiske", "De brug is opgehaald. Hoe komen we binnen?"))));
        plein.als = s -> !s.heeft("r:valbrug");
        verhaal(new Verhaal("poortboog", Box.van(1622, F, -298, 1628, F + 5, -290), List.of(
                r("Wiske", "Boven de poort: een haakpunt!")))).als = s -> !s.heeft("r:valbrug");
    }

    // ── 3. de IJstuin: een tuin onder glas, een tweede glijvloer, de sneeuwbloem en de hoorn van Ys ─

    /** De glijvloer van de IJstuin (moeilijker, en terug te glijden): # = rots, . = ijs, S = start, G = doel (rij 0 = noord). */
    static final String[] TUIN = {
            "####G##########",
            "#......#......#",
            "#....#........#",
            "#......#......#",
            "#.....#..#...##",
            "#......#......#",
            "#.............#",
            "#........#....#",
            "##............#",
            "#....#.......##",
            "#.#.....#.....#",
            "#######S#######"};

    /** De glijvloer van de Glijzaal (de eerste, verplicht; aan de overkant is er geen weg terug over het ijs). */
    static final String[] ZAAL = {
            "#######G#########",
            "#.....#.........#",
            "#...........#...#",
            "#.#.............#",
            "#.......#.....#.#",
            "#...#...........#",
            "#.........#.....#",
            "#..#............#",
            "#.......#....#..#",
            "#.............#.#",
            "#..#....#.......#",
            "#...........#...#",
            "########S########"};

    /**
     * Een glijvloer bouwen uit een plattegrond (linksboven = {@code x0, z0}, rij 0 = noord): ijs (blauw ijs), rotsen van
     * twee hoog (pakijs met sneeuw), start en doel (gewone vloer). Geeft de glijvloer met zijn ijsvakjes, start en doel.
     */
    private Glij glijvloer(String id, String naam, String[] kaart, int x0, int z0, Box gebied, String oplossing) {
        int[] start = null, doel = null;
        List<int[]> ijs = new ArrayList<>();
        for (int r = 0; r < kaart.length; r++)
            for (int c = 0; c < kaart[r].length(); c++) {
                int x = x0 + c, z = z0 + r;
                char t = kaart[r].charAt(c);
                switch (t) {
                    case '.' -> {
                        ijs.add(new int[]{x, z});
                        zet(x, F, z, Material.BLUE_ICE);
                        vul(x, F + 1, z, x, F + 3, z, Material.AIR);
                    }
                    case 'S', 'G' -> {
                        if (t == 'S') start = new int[]{x, z};
                        else doel = new int[]{x, z};
                        zet(x, F, z, Material.POLISHED_DIORITE);
                        vul(x, F + 1, z, x, F + 3, z, Material.AIR);
                    }
                    default -> {
                        zet(x, F, z, Material.PACKED_ICE);
                        zet(x, F + 1, z, Material.PACKED_ICE);
                        zet(x, F + 2, z, kans(x, z, 3) == 0 ? Material.BLUE_ICE : Material.SNOW_BLOCK);
                    }
                }
            }
        Glij gl = new Glij(id, naam, F, gebied, start, doel);
        for (int[] c : ijs) gl.ijs(c[0], c[1]);
        gl.oplossing(oplossing);
        return gl;
    }

    private void ijstuin() {
        int x1 = 1610, x2 = 1629, z1 = -335, z2 = -309;                    // de muren; binnen x 1611 … 1628, z −334 … −310
        if (b.plaats) {
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    boolean muur = x == x1 || x == x2 || z == z1 || z == z2;
                    zet(x, F, z, muur ? Material.POLISHED_DIORITE : (x + z) % 3 == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
                    for (int y = F + 1; y <= F + 8; y++) {
                        boolean langsX = x == x1 || x == x2, langsZ = z == z1 || z == z2;
                        boolean raam = muur && !(langsX && langsZ) && y >= F + 3 && y <= F + 6 && (langsX ? (z - z1) % 4 == 2 : (x - x1) % 4 == 2);
                        zet(x, y, z, !muur ? Material.AIR : raam ? Material.LIGHT_BLUE_STAINED_GLASS : y == F + 8 ? Material.QUARTZ_BRICKS : ijsmuur(x, y, z));
                    }
                    zet(x, F + 9, z, muur || (x - x1) % 5 == 0 || (z - z1) % 5 == 0 ? Material.QUARTZ_BRICKS : Material.LIGHT_BLUE_STAINED_GLASS);   // een dak van glas
                }
            // de gebarsten muur aan de zuidkant (de ingang)
            for (int x = 1617; x <= 1621; x++) zet(x, F + 4, z2, Material.CHISELED_QUARTZ_BLOCK);
            // naast de glijvloer: dicht (je kunt er niet omheen)
            vul(1611, F + 1, -324, 1611, F + 8, -313, Material.PACKED_ICE);
            vul(1627, F + 1, -324, 1628, F + 8, -313, Material.PACKED_ICE);
            // de tuin: bedden met sneeuwbloemen (wit), paadjes, het balkon met de hoorn, een afstapje
            for (int x = 1611; x <= 1628; x++)
                for (int z = -334; z <= -325; z++) {
                    boolean bed = (x <= 1615 || (x >= 1618 && x <= 1620)) && z <= -327 && z >= -333;
                    if (!bed) continue;
                    zet(x, F, z, (x + z) % 4 == 0 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK);
                    int k = kans(x, z, 6);
                    if (k < 4) zet(x, F + 1, z, k == 0 ? Material.LILY_OF_THE_VALLEY : k == 1 ? Material.WHITE_TULIP : k == 2 ? Material.AZURE_BLUET : Material.OXEYE_DAISY);
                }
            vul(1622, F + 1, -334, 1627, F + 5, -332, Material.QUARTZ_BRICKS);  // het balkon
            for (int x = 1622; x <= 1627; x++) zet(x, F + 5, -332, Material.CHISELED_QUARTZ_BLOCK);
            vul(1621, F + 1, -333, 1621, F + 2, -333, Material.QUARTZ_BRICKS);  // het afstapje (alleen omlaag)
            zet(1624, F + 5, -331, ZeldaHoofdstuk.HAAKPUNT);
            zet(1626, F + 6, -334, Material.SPRUCE_FENCE);
            zet(1626, F + 7, -334, Material.SPRUCE_PRESSURE_PLATE);
            for (int[] l : new int[][]{{1614, -330}, {1619, -330}, {1616, -320}, {1622, -318}, {1613, -316}}) {
                zet(l[0], F + 8, l[1], Material.IRON_CHAIN);
                zet(l[0], F + 7, l[1], Bouwer.hangend(Material.LANTERN));
            }
        }
        Barst muur = barstOpPad(new Barst("ijstuin", deur(ZeldaHoofdstuk.BARST.createBlockData(), 1618, F + 1, z2, 1620, F + 3, z2),
                Box.van(1615, F, -313, 1623, F + 5, -305)));
        muur.voor = plek(1619, F + 1, -306, 180f);
        verhaal(new Verhaal("ijstuin-muur", Box.van(1616, F, -308, 1622, F + 4, -305), List.of(
                r("Wiske", "Weer zo'n gebarsten muur. Wat zit erachter?")))).als = s -> !s.heeft("b:ijstuin");
        na("ijstuin-muur", muur);
        verhaal(new Verhaal("ijstuin", Box.van(1615, F, -312, 1623, F + 4, -310), List.of(
                r("Wiske", "Een tuin onder glas! Wat warm hier."))));
        Verhaal ijs = verhaal(new Verhaal("uitleg-tuinijs", Box.van(1617, F, -313, 1621, F + 4, -311), List.of(
                r("Wiske", "Glad ijs! Daarop glijd je tot je ergens tegenaan botst."),
                r("Wiske", "Kijk goed welke rots je tegenhoudt."))));
        ijs.uitleg = true;
        ijs.als = s -> !s.heeft("v:glij");
        glij(glijvloer("glijtuin", "De IJstuin", TUIN, 1612, -324, Box.van(1611, F, -334, 1628, F + 8, -310), "NESWSEN"));
        verhaal(new Verhaal("bloemen", Box.van(1612, F, -328, 1620, F + 4, -325), List.of(
                r("Wiske", "Sneeuwbloemen! Ze bloeien in de kou."))));
        Vondst bloem = vondst(new Vondst("sneeuwbloem", "Sneeuwbloem", "voor Hanne", Material.LILY_OF_THE_VALLEY, plek(1614, F + 1.3, -330, 0f),
                Box.van(1613, F + 1, -331, 1615, F + 3, -329)));
        bloem.daarna = (spel, p, s) -> {
            if (s.missie("sneeuwbloem") == 0) spel.missie(p, "sneeuwbloem", 1);
            spel.vertel(p, r("Wiske", "Hanne brouwt daar vast iets moois van."));
        };
        Haakpunt balkon = haakpunt(new Haakpunt("balkon", 1624, F + 5, -331, plek(1624, F + 6, -333, 180f)));
        haak(balkon, plek(1622, F + 1, -327, 180f));
        stukje(STUKJE_HOORN);
        missie("hoorn", "De hoorn van Ys", "Zoek de oude hoorn van Ys, in het IJspaleis.", "Breng de hoorn naar Ys, bij het meer.");
        Vondst hoorn = vondst(new Vondst("hoorn", "Hoorn van Ys", "breng hem naar Ys", Material.GOAT_HORN, plek(1626, F + 6.3, -333, 0f),
                Box.van(1625, F + 6, -334, 1627, F + 8, -332)));
        hoorn.daarna = (spel, p, s) -> {
            if (s.missie("hoorn") < 2) spel.missie(p, "hoorn", 2);
            spel.vertel(p, r("Wiske", "Er staat «Ys» in gekrast!"));
        };
    }

    // ── 4. de oude sleebaan: van het plateau naar de berghut (een rondje, en een tijdrace) ──

    private void sleebaan() {
        if (b.plaats) {
            zet(1612, F + 1, -267, Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            for (int z : new int[]{-269, -265}) {
                zet(1611, F + 1, z, Material.SPRUCE_FENCE);
                zet(1611, F + 2, z, Material.LIGHT_BLUE_WOOL);
            }
            zet(1593, G + 15, -283, Material.SPRUCE_FENCE);
            zet(1593, G + 16, -283, Material.SPRUCE_FENCE);
            for (int x : new int[]{1587, 1591}) {                          // de finish: twee vlaggetjes
                zet(x, G + 15, -283, Material.SPRUCE_FENCE);
                zet(x, G + 16, -283, Material.LIGHT_BLUE_WOOL);
            }
        }
        verhaal(new Verhaal("uitleg-sleebaan", Box.van(1611, F, -270, 1616, F + 4, -264), List.of(
                r("Wiske", "De oude sleebaan! Stap op de plaat."),
                r("Wiske", "Dan gaat het hekje open. Naar beneden!")))).uitleg = true;
        Uitdaging baan = uitdaging(new Uitdaging("sleebaan", "De sleebaan", Box.van(1611, F, -268, 1613, F + 3, -266),
                Box.van(1588, G + 14, -283, 1590, G + 18, -280), Box.van(1586, G + 12, -286, 1616, F + 6, -262),
                deur(hek(Material.SPRUCE_FENCE, true), 1610, F + 1, -268, 1610, F + 1, -266), 15));
        baan.bord = new Location(w, 1593.5, G + 17.4, -283.5);
    }

    // ── 5. de romp van het paleis ───────────────────────────────────────────────────────────

    /**
     * Het IJspaleis van buiten: alles tussen de zalen is muur (ijs en steen van de wachters), met banden blauw ijs,
     * ramen, kantelen en spitsen van ijs. De zalen worden er daarna uitgehaald (zie de zalen hieronder).
     */
    private void paleisRomp() {
        int x1 = 1634, x2 = 1711, z1 = -335, z2 = -256;
        vul(x1, F + 1, z1, x2, F + 10, z2, Material.PACKED_ICE);
        vul(1646, F + 11, -306, 1670, F + 15, -282, Material.PACKED_ICE);       // de Kristalzaal is hoger
        vul(1680, F + 11, -306, 1704, F + 13, -282, Material.PACKED_ICE);       // en de Troonzaal
        // de buitenkant: banden, zuilen, ramen (alleen waar een zaal achter zit), kantelen
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean rand = x == x1 || x == x2 || z == z1 || z == z2;
                if (!rand) continue;
                for (int y = F + 1; y <= F + 10; y++) {
                    boolean zuil = (x + z) % 6 == 0;
                    zet(x, y, z, zuil ? Material.QUARTZ_PILLAR : y == F + 1 ? Material.POLISHED_DIORITE : y % 4 == 0 ? Material.BLUE_ICE : ijsmuur(x, y, z));
                }
                if ((x + z) % 2 == 0) zet(x, F + 11, z, Material.QUARTZ_BRICKS);
            }
        for (int[] h : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) {
            vul(h[0], F + 11, h[1], h[0], F + 13, h[1], Material.QUARTZ_PILLAR);
            ijspiek(h[0], F + 14, h[1], 7);
        }
        // de Kristalzaal: een spits van ijs in het midden van het dak
        for (int k = 0; k < 12; k++) {
            int r = Math.max(0, 4 - k / 3);
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) if (dx * dx + dz * dz <= r * r) zet(1658 + dx, F + 16 + k, PZ + dz, k % 4 == 3 ? Material.BLUE_ICE : Material.PACKED_ICE);
        }
        zet(1658, F + 28, PZ, Material.SEA_LANTERN);
        // de poorttoren aan de westkant, met twee torentjes
        for (int z : new int[]{-300, -288})
            for (int dz = -1; dz <= 1; dz++) {
                vul(1633, F + 1, z + dz, 1634, F + 14, z + dz, Material.QUARTZ_BRICKS);
                zet(1633, F + 15, z + dz, (dz == 0) ? Material.QUARTZ_PILLAR : Material.QUARTZ_SLAB);
            }
        vul(1634, F + 11, -299, 1634, F + 12, -289, Material.PACKED_ICE);
        for (int z = -299; z <= -289; z++) if (z % 2 == 0) zet(1634, F + 13, z, Material.QUARTZ_BRICKS);
        // de zalen (eerst allemaal uithalen, daarna maken de zalen hun deuren: dan sluit geen muur een deur weer)
        zaal(1635, -299, 1645, -289, F + 7);                                  // de Poortzaal
        zaal(1637, -306, 1643, -301, F + 4);                                  // de wachtkamer (Tijmen)
        zaal(1647, -305, 1669, -283, F + 14);                                 // de Kristalzaal
        zaal(1647, -333, 1669, -309, F + 9);                                  // de Glijzaal
        zaal(1671, -333, 1685, -319, F + 8);                                  // de Wachtzaal
        zaal(1671, -317, 1677, -283, F + 7);                                  // de Oostgang
        zaal(1679, -295, 1679, -293, F + 5);                                  // het portaal voor de troonzaal
        zaal(1681, -305, 1703, -283, F + 12);                                 // de Troonzaal
        zaal(1705, -298, 1709, -289, F + 6);                                  // het hol van Grimmel
        zaal(1691, -310, 1693, -307, F + 4);                                  // de gang naar de toren
        zaal(1647, -280, 1669, -258, F + 9);                                  // de Spiegelzaal
        zaal(1671, -279, 1677, -263, F + 9);                                  // de kloof in het paleis
        zaal(1679, -279, 1689, -267, F + 7);                                  // de Wapenkamer
    }

    /**
     * Een zaal uit de romp halen (binnen {@code x1 … x2, z1 … z2}): een vloer met een patroon, lucht tot het plafond, de
     * binnenkant van de muren (ijs, met zuilen van kwarts), een plafond met zeelantaarns erin.
     */
    private void zaal(int x1, int z1, int x2, int z2, int plafond) {
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean muur = x < x1 || x > x2 || z < z1 || z > z2;
                if (muur) {
                    boolean zuil = (x == x1 - 1 || x == x2 + 1) ? (z - z1) % 4 == 3 : (x - x1) % 4 == 3;
                    for (int y = F + 1; y <= plafond; y++) zet(x, y, z, zuil ? Material.QUARTZ_PILLAR : y == F + 1 ? Material.POLISHED_DIORITE : ijsmuur(x, y, z));
                    continue;
                }
                zet(x, F, z, paleisvloer(x, z));
                vul(x, F + 1, z, x, plafond, z, Material.AIR);
                boolean lamp = (x - x1) % 5 == 2 && (z - z1) % 5 == 2;
                zet(x, plafond + 1, z, lamp ? Material.SEA_LANTERN : (x + z) % 2 == 0 ? Material.QUARTZ_BRICKS : Material.PACKED_ICE);
            }
    }

    // ── 6. de Poortzaal: de valbrug, en Tijmen achter een gebarsten muur ───────────────────

    private void poortzaal() {
        if (b.plaats) {
            vul(1634, F + 1, -295, 1634, F + 4, -293, Material.AIR);          // de poort (west)
            for (int z = -296; z <= -292; z++) zet(1634, F + 5, z, Material.CHISELED_QUARTZ_BLOCK);
            for (int z : new int[]{-296, -292}) vul(1634, F + 1, z, 1634, F + 4, z, Material.QUARTZ_PILLAR);
            zet(1633, F + 6, PZ, ZeldaHoofdstuk.HAAKPUNT);
            zet(1633, F + 7, PZ, Material.IRON_CHAIN);
            vul(1646, F + 1, -295, 1646, F + 4, -293, Material.AIR);          // naar de Kristalzaal (oost)
            for (int z = -296; z <= -292; z++) zet(1646, F + 5, z, Material.CHISELED_QUARTZ_BLOCK);
            // banieren, twee ijsbeelden van wachters, kettingen van de valbrug
            for (int x : new int[]{1637, 1643}) {
                zet(x, F + 4, -299, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.SOUTH));
                zet(x, F + 4, -289, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.NORTH));
            }
            for (int[] bd : new int[][]{{1644, -298}, {1644, -290}}) {
                zet(bd[0], F + 1, bd[1], Material.POLISHED_DIORITE);
                vul(bd[0], F + 2, bd[1], bd[0], F + 3, bd[1], Material.PACKED_ICE);
                zet(bd[0], F + 4, bd[1], Material.BLUE_ICE);
            }
            for (int y = F + 2; y <= F + 6; y++) zet(1635, y, -297, Material.IRON_CHAIN);
            // de wachtkamer: een bankje, een tafel, een lantaarn (Tijmen zat er opgesloten)
            for (int x = 1638; x <= 1639; x++) zet(x, F + 1, -306, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.SOUTH));
            zet(1642, F + 1, -305, Material.BARREL);
            zet(1642, F + 2, -305, Material.LANTERN);
            for (int x = 1638; x <= 1642; x++) zet(x, F + 4, -300, Material.CHISELED_QUARTZ_BLOCK);
        }
        Haakpunt poort = haakpunt(new Haakpunt("poort", 1633, F + 6, PZ, plek(1636, F + 1, PZ, 90f)));
        haak(poort, plek(1623, F + 1, PZ, -90f));
        verhaal(new Verhaal("uitleg-hendel", Box.van(1635, F, -297, 1640, F + 4, -291), List.of(
                r("Wiske", "Een hendel! Daar hangt de brug aan.")))).als = s -> !s.heeft("r:valbrug");
        Reeks brug = reeks(new Reeks("valbrug", "De valbrug", Reeks.Hoe.KLIK, hendel(BlockFace.EAST, false), hendel(BlockFace.EAST, true),
                new Deur(Material.AIR.createBlockData(), Material.SPRUCE_PLANKS.createBlockData()).vlak(1629, F, -295, 1633, F, -293),
                Box.van(1622, F + 1, -300, 1645, F + 6, -288), 30));
        brug.dingen.add(new int[]{1637, F + 1, -297});
        brug.bijOplossen.add(r("Wiske", "De valbrug zakt! Nu kun je zo naar buiten."));
        verhaal(new Verhaal("geroep", Box.van(1636, F, -299, 1644, F + 4, -295), List.of(
                r("Stem", "Hallo? Is daar iemand?"),
                r("Wiske", "Achter die gebarsten muur! Een bom?")))).als = s -> !s.heeft(ZONNESTEEN) && !s.heeft("b:wachtkamer");
        Barst muur = barstOpPad(new Barst("wachtkamer", deur(ZeldaHoofdstuk.BARST.createBlockData(), 1639, F + 1, -300, 1641, F + 3, -300),
                Box.van(1636, F, -306, 1644, F + 5, -296)));
        muur.voor = plek(1640, F + 1, -297, 180f);
        tijmen();
    }

    /**
     * Tijmen, de reizende tovenaar (zie ZELDA.md): hij was er weer eerder dan jij, en raakte opgesloten toen de deur
     * dichtviel. Zijn gave hier is de zonnesteen: het licht voor de Spiegelzaal. Daarna is hij de wegwijzer van het paleis.
     */
    private void tijmen() {
        Persoon tijmen = persoon(new Persoon("Tijmen", plek(1640, F + 1, -304, 0f), null, List.of(r("Tijmen", "De sterren zwijgen even."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.eenmalig("tijmen-paleis", null, List.of(
                        r("Wiske", "Tijmen?! Hij is er alwéér!"),
                        r("Tijmen", "Hè hè. Ik was er te vroeg, en toen viel de deur dicht."),
                        r("Tijmen", "Hier, een zonnesteen. Hij bewaart het laatste licht."),
                        r("Tijmen", "In de zaal met de spiegels heb je hem nodig.")), (spel, p, s) -> {
                    spel.beloon(p, ZONNESTEEN, "Zonnesteen", "licht voor de spiegels");
                    spel.uitleg(p, r("Wiske", "Met de zonnesteen gaat het licht aan in de spiegelzaal."));
                })
                .eenmalig("tijmen-grimmel", s -> s.heeft("k:troonzaal"), List.of(
                        r("Tijmen", "Grimmel is vrij. Hoor je het? Het paleis ademt weer.")), null)
                .eenmalig("tijmen-vorst", s -> s.heeft("baken3"), List.of(
                        r("Tijmen", "Het Vorstbaken brandt. Nog één baken, dan breekt de nevel."),
                        r("Tijmen", "Op de Gloedberg zien we elkaar weer.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
        praten(tijmen);
    }

    // ── 7. de Kristalzaal: de hub van het paleis ────────────────────────────────────────────

    private void kristalzaal() {
        int mx = 1658;                                                      // het midden (x); z = PZ
        if (b.plaats) {
            vul(1657, F + 1, -308, 1659, F + 4, -306, Material.AIR);          // naar de Glijzaal (noord)
            vul(1657, F + 1, -282, 1659, F + 4, -281, Material.AIR);          // naar de Spiegelzaal (zuid)
            vul(1670, F + 1, -301, 1670, F + 4, -299, Material.AIR);          // naar de Oostgang (oost)
            for (int x = 1656; x <= 1660; x++) {
                zet(x, F + 5, -306, Material.CHISELED_QUARTZ_BLOCK);
                zet(x, F + 5, -282, Material.CHISELED_QUARTZ_BLOCK);
            }
            for (int z = -302; z <= -298; z++) zet(1670, F + 5, z, Material.CHISELED_QUARTZ_BLOCK);
            vul(1657, F, -308, 1659, F, -306, Material.POLISHED_DIORITE);
            vul(1657, F, -282, 1659, F, -281, Material.POLISHED_DIORITE);
            // de bevroren fontein in het midden: een kom van blauw ijs, een straal van ijs die stil staat
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > 3.3) continue;
                    zet(mx + dx, F, PZ + dz, d > 2.4 ? Material.QUARTZ_BRICKS : Material.BLUE_ICE);
                    if (d > 2.4) zet(mx + dx, F + 1, PZ + dz, Material.QUARTZ_SLAB);
                }
            vul(mx, F + 1, PZ, mx, F + 5, PZ, Material.PACKED_ICE);
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                zet(mx + f.getModX(), F + 4, PZ + f.getModZ(), Material.BLUE_ICE);
                zet(mx + f.getModX() * 2, F + 3, PZ + f.getModZ() * 2, Material.PACKED_ICE);
            }
            zet(mx, F + 6, PZ, Material.BLUE_ICE);
            // hoge zuilen van ijs, kroonluchters, banieren bij de deuren
            for (int[] z : new int[][]{{1652, -300}, {1664, -300}, {1652, -288}, {1664, -288}}) {
                vul(z[0], F + 1, z[1], z[0], F + 14, z[1], Material.PACKED_ICE);
                for (int y = F + 3; y <= F + 13; y += 5) zet(z[0], y, z[1], Material.BLUE_ICE);
            }
            for (int[] k : new int[][]{{1652, -294}, {1664, -294}}) {
                vul(k[0], F + 11, k[1], k[0], F + 14, k[1], Material.IRON_CHAIN);
                zet(k[0], F + 10, k[1], Material.SEA_LANTERN);
            }
            for (int x : new int[]{1655, 1661}) {
                zet(x, F + 6, -305, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.SOUTH));
                zet(x, F + 6, -283, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.NORTH));
            }
            bord(1660, F + 1, -303, BlockFace.SOUTH, "De Glijzaal");
            bord(1656, F + 1, -285, BlockFace.NORTH, "De Spiegelzaal");
            bord(1668, F + 1, -297, BlockFace.WEST, "De Oostgang");
        }
        rustvuur(1649, F, -287);
        rust(new Rust("kristalzaal", "De Kristalzaal", plek(1650, F + 1, PZ, -90f), Box.van(1647, F, -303, 1656, F + 7, -285), List.of()));
        bron("bron-kristal", plek(mx, F + 1, -287, 0f));
        verhaal(new Verhaal("kristal-binnen", Box.van(1647, F, -297, 1652, F + 5, -291), List.of(
                r("Wiske", "Een zaal van ijs. Zo mooi!"),
                r("Wiske", "Deuren naar het noorden, het zuiden en het oosten."))));
    }

    // ── 8. de Glijzaal: glad ijs, het Noorderkristal in de kuil erachter ─────────────────────

    private void glijzaal() {
        if (b.plaats) {
            // naast de glijvloer dicht (je kunt er niet omheen), het ijs met zijn rotsen
            vul(1647, F + 1, -325, 1649, F + 9, -313, Material.PACKED_ICE);
            vul(1667, F + 1, -325, 1669, F + 9, -313, Material.PACKED_ICE);
            // de kuil achter het ijs (drie lager): een rand van ijs, de doorgang van het doel, de trap omhoog naar de Wachtzaal
            vul(1647, F - 3, -333, 1669, F, -326, Material.PACKED_ICE);
            for (int x = 1648; x <= 1668; x++)
                for (int z = -333; z <= -327; z++) {
                    zet(x, F - 3, z, (x + z) % 3 == 0 ? Material.QUARTZ_BRICKS : Material.CALCITE);
                    vul(x, F - 2, z, x, F, z, Material.AIR);
                }
            vul(1647, F + 1, -326, 1669, F + 2, -326, Material.PACKED_ICE);    // de rand: twee hoog, net als de rotsen
            zet(1657, F, -326, Material.POLISHED_DIORITE);                       // de doorgang van het doel
            vul(1657, F + 1, -326, 1657, F + 2, -326, Material.AIR);
            int[][] trap = {{1664, -2}, {1665, -1}, {1666, 0}, {1667, 0}, {1668, 0}, {1669, 0}};
            for (int[] t : trap)
                for (int z = -332; z <= -330; z++) {
                    vul(t[0], F - 3, z, t[0], F + t[1], z, Material.QUARTZ_BRICKS);
                    vul(t[0], F + t[1] + 1, z, t[0], F + 4, z, Material.AIR);
                }
            vul(1670, F + 1, -332, 1670, F + 3, -330, Material.AIR);          // naar de Wachtzaal
            // de sokkel van het Noorderkristal, lantaarns aan de muren (geen ijs dat smelt: alles is pakijs)
            zet(1651, F - 2, -330, Material.CHISELED_QUARTZ_BLOCK);
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
                zet(1651 + f.getModX(), F - 2, -330 + f.getModZ(), Bouwer.trap(Material.QUARTZ_STAIRS, f.getOppositeFace()));
            for (int x : new int[]{1652, 1664}) zet(x, F + 4, -309, Bouwer.gericht(Material.SOUL_WALL_TORCH, BlockFace.NORTH));
            vul(1647, F, -312, 1669, F, -309, Material.POLISHED_ANDESITE);
        }
        Verhaal ijs = verhaal(new Verhaal("uitleg-glij", Box.van(1653, F, -312, 1663, F + 4, -309), List.of(
                r("Wiske", "Glad ijs! Daarop glijd je tot je ergens tegenaan botst."),
                r("Wiske", "Kijk goed welke rots je tegenhoudt."))));
        ijs.uitleg = true;
        ijs.als = s -> !s.heeft("v:glij");                                 // wie al eens gleed (de IJstuin), weet het
        glij(glijvloer("glijzaal", "De Glijzaal", ZAAL, 1650, -325, Box.van(1647, F - 3, -333, 1669, F + 9, -309), "NENWN"));
        verhaal(new Verhaal("kuil3b", Box.van(1654, F - 3, -330, 1660, F, -327), List.of(
                r("Wiske", "Over! Terug kan niet meer."),
                r("Wiske", "Kijk, een kristal. En een trap omhoog."))));
        vondst(new Vondst(NOORD, "Noorderkristal", "past in de troondeur", Material.PRISMARINE_CRYSTALS, plek(1651, F - 0.6, -330, 0f),
                Box.van(1650, F - 2, -331, 1652, F, -329)));
        rustvuur(1656, F - 3, -331);                                        // wie in de Wachtzaal valt, begint hier (niet weer glijden)
        rust(new Rust("kristalkuil", "De kristalkuil", plek(1659, F - 2, -328, 90f), Box.van(1648, F - 3, -333, 1663, F, -327), List.of()));
        bron("bron-kuil", plek(1661, F - 2, -331, 0f));
    }

    // ── 9. de Wachtzaal: ijsknechten (het enige gevecht, drie gemengde golven) ──────────────

    private void wachtzaal() {
        if (b.plaats) {
            vul(1673, F + 1, -318, 1675, F + 3, -318, Material.AIR);          // naar de Oostgang (zuid)
            zet(1684, F + 1, -332, Bouwer.gericht(Material.BELL, BlockFace.WEST));
            for (int x : new int[]{1674, 1682}) {
                zet(x, F + 4, -333, Bouwer.gericht(Material.CYAN_WALL_BANNER, BlockFace.SOUTH));
                zet(x, F + 4, -319, Bouwer.gericht(Material.CYAN_WALL_BANNER, BlockFace.NORTH));
            }
            for (int[] r : new int[][]{{1672, -333}, {1685, -333}, {1672, -319}, {1685, -319}}) zet(r[0], F + 1, r[1], Material.BARREL);
            zet(1685, F + 1, -326, Bouwer.gericht(Material.GRINDSTONE, BlockFace.NORTH));
        }
        BlockData ijs = Material.PACKED_ICE.createBlockData();
        Kamer k = kamer(new Kamer("wachtzaal", "De Wachtzaal", Box.van(1671, F, -333, 1685, F + 8, -319), Box.van(1673, F + 1, -331, 1683, F + 5, -321),
                deur(ijs, 1670, F + 1, -332, 1670, F + 3, -330), deur(ijs, 1673, F + 1, -318, 1675, F + 3, -318)));
        for (int[] sp : new int[][]{{1675, -322}, {1681, -322}, {1675, -330}, {1681, -330}, {1678, -326}, {1683, -326}})
            k.spawns.add(plek(sp[0], F + 1, sp[1], 0f));
        k.golf(Soort.IJSKNECHT, Soort.IJSKOBOLD)                // balans: langer, lichter, gemengd (Jelle 27-10)
                .golf(Soort.IJSKNECHT, Soort.VORSTSCHUTTER)
                .golf(Soort.IJSKNECHT, Soort.IJSKOBOLD, Soort.VORSTSCHUTTER)
                .extra(Soort.IJSKNECHT, Soort.IJSKNECHT, Soort.VORSTSCHUTTER, Soort.IJSKOBOLD);
        k.bijStart.add(r("Wiske", "IJsknechten! Ze bewaken het paleis."));
        k.bel = new int[]{1684, F + 1, -332};
        k.tips.addAll(List.of(r("Wiske", "De kleintjes zijn snel. Schild omhoog!"),
                r("Wiske", "Beneden bij de trap is een bron. Daar genees je.")));
    }

    // ── 10. de Oostgang: de rustplaats voor de troondeur ───────────────────────────────────

    private void oostgang() {
        if (b.plaats) {
            for (int z = -314; z <= -286; z += 7) {
                zet(1671, F + 4, z, Bouwer.gericht(Material.SOUL_WALL_TORCH, BlockFace.EAST));
                zet(1677, F + 4, z + 3, Bouwer.gericht(Material.SOUL_WALL_TORCH, BlockFace.WEST));
            }
            // de troondeur: een deur van blauw ijs met twee lege houders voor de kristallen
            for (int y = F + 1; y <= F + 5; y++) for (int z : new int[]{-296, -292}) zet(1678, y, z, Material.QUARTZ_PILLAR);
            for (int z = -296; z <= -292; z++) zet(1678, F + 5, z, Material.CHISELED_QUARTZ_BLOCK);
            zet(1677, F + 3, -296, Bouwer.gericht(Material.SOUL_WALL_TORCH, BlockFace.WEST));
            zet(1677, F + 3, -292, Bouwer.gericht(Material.SOUL_WALL_TORCH, BlockFace.WEST));
        }
        rustvuur(1674, F, -288);
        rust(new Rust("troondrempel", "De troondeur", plek(1675, F + 1, PZ, 90f), Box.van(1671, F, -297, 1677, F + 6, -285), List.of()));
        bron("bron-troon", plek(1674, F + 1, -284, 0f));
        verhaal(new Verhaal("troondeur", Box.van(1673, F, -297, 1677, F + 4, -291), List.of(
                r("Wiske", "Een deur van ijs, met twee gaten."),
                r("Wiske", "Daar passen vast kristallen in.")))).als = s -> !(s.heeft(NOORD) && s.heeft(ZUID));
        verhaal(new Verhaal("troondeur-harnas", Box.van(1673, F, -297, 1677, F + 4, -291), List.of(
                r("Wiske", "Wacht. Zonder harnas tegen wat daar brult?"),
                r("Wiske", "Eerst naar Hagen, in IJshoeve.")))).als = s -> s.heeft(NOORD) && s.heeft(ZUID) && !s.heeft("harnas2");
    }

    // ── 11. de Spiegelzaal (met de zonnesteen), de kloof, de Wapenkamer ──────────────────────

    /** Het licht van de zonnesteen in zijn houder (aan), en de lege houder (uit). */
    private static final Material ZON_AAN = Material.OCHRE_FROGLIGHT, ZON_UIT = Material.CHISELED_TUFF_BRICKS;

    private void spiegelzaal() {
        int y = F + 2;                                                      // de hoogte van het licht
        int[][] sokkels = {{1652, -277}, {1652, -263}, {1663, -263}, {1663, -270}, {1658, -270}};
        if (b.plaats) {
            for (int[] k : sokkels) zet(k[0], F + 1, k[1], Material.QUARTZ_PILLAR);
            // ijszuilen (niet waar het licht moet lopen), banieren, een tweede houder voor het oog
            for (int[] z : new int[][]{{1648, -279}, {1668, -279}, {1648, -259}, {1668, -259}, {1657, -266}})
                vul(z[0], F + 1, z[1], z[0], F + 9, z[1], Material.PACKED_ICE);
            for (int[] d : new int[][]{{0, -1}, {0, 1}, {1, 0}, {-1, 0}}) zet(1646, y + d[0], -277 + d[1], Material.CHISELED_QUARTZ_BLOCK);   // een lijst om de houder
            // de ijsdeur naar de kloof (oost), met een lijst van kwarts
            for (int yy = F + 1; yy <= F + 4; yy++) for (int z : new int[]{-275, -271}) zet(1670, yy, z, Material.QUARTZ_PILLAR);
            for (int z = -275; z <= -271; z++) zet(1670, F + 4, z, Material.CHISELED_QUARTZ_BLOCK);
            // de kloof in het paleis: diep, met een ladder terug omhoog aan de kant van de spiegelzaal
            for (int x = 1672; x <= 1676; x++)
                for (int z = -279; z <= -263; z++) {
                    zet(x, F - 6, z, Material.SNOW_BLOCK);
                    vul(x, F - 5, z, x, F, z, Material.AIR);
                }
            for (int yy = F - 5; yy <= F; yy++) zet(1672, yy, -264, Bouwer.gericht(Material.LADDER, BlockFace.EAST));
            zet(1671, F + 5, -273, ZeldaHoofdstuk.HAAKPUNT);                  // terug: boven de ijsdeur
            zet(1677, F + 5, -273, ZeldaHoofdstuk.HAAKPUNT);                  // heen: boven de deur van de Wapenkamer
            vul(1678, F + 1, -274, 1678, F + 3, -272, Material.AIR);          // de deur van de Wapenkamer
            for (int z = -275; z <= -271; z++) zet(1678, F + 4, z, Material.CHISELED_QUARTZ_BLOCK);
            // de Wapenkamer: rekken, een aambeeld, banieren, de sokkels van het kristal en het harnas
            for (int x = 1680; x <= 1688; x += 2) {
                zet(x, F + 1, -279, Material.SPRUCE_FENCE);
                zet(x, F + 2, -279, Material.SPRUCE_SLAB);
            }
            zet(1688, F + 1, -268, Bouwer.gericht(Material.ANVIL, BlockFace.NORTH));
            zet(1680, F + 1, -268, Material.BARREL);
            for (int x : new int[]{1682, 1686}) zet(x, F + 4, -267, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.NORTH));
            zet(1686, F + 1, -276, Material.CHISELED_QUARTZ_BLOCK);
            zet(1686, F + 1, -271, Material.POLISHED_DIORITE);
        }
        Verhaal donker = verhaal(new Verhaal("spiegeldonker", Box.van(1653, F, -280, 1663, F + 4, -276), List.of(
                r("Wiske", "Spiegels! Maar het is hier zo donker."),
                r("Wiske", "Zonder licht doen spiegels niets."))));
        donker.als = s -> !s.heeft(ZONNESTEEN);
        verhaal(new Verhaal("uitleg-spiegel", Box.van(1653, F, -280, 1663, F + 4, -276), List.of(
                r("Wiske", "De zonnesteen schijnt! Volg het licht."),
                r("Wiske", "Draai de spiegels met rechtsklik. Naar het slot!")))).vereist = ZONNESTEEN;
        Spiegels sp = spiegels(new Spiegels("spiegelzaal", "De Spiegelzaal", new int[]{1646, y, -277}, BlockFace.EAST, new int[]{1670, y, -270},
                Material.PACKED_ICE.createBlockData(), Material.SEA_LANTERN.createBlockData(),
                deur(Material.PACKED_ICE.createBlockData(), 1670, F + 1, -274, 1670, F + 3, -272), Box.van(1647, F, -280, 1669, F + 8, -258)));
        sp.spiegel(1652, y, -277, BlockFace.NORTH, BlockFace.SOUTH)
                .spiegel(1652, y, -263, BlockFace.WEST, BlockFace.EAST)
                .spiegel(1663, y, -263, BlockFace.SOUTH, BlockFace.NORTH)
                .spiegel(1663, y, -270, BlockFace.WEST, BlockFace.EAST)
                .spiegel(1658, y, -270, BlockFace.NORTH, null);                 // een lokspiegel
        sp.licht = s -> s.heeft(ZONNESTEEN);
        sp.bijOplossen.add(r("Wiske", "Het licht smelt de ijsdeur!"));
        spiegelzaal = sp;
        verhaal(new Verhaal("kloof3b", Box.van(1670, F, -275, 1671, F + 4, -271), List.of(
                r("Wiske", "Een kloof, midden in het paleis!"),
                r("Wiske", "Boven de deur aan de overkant: een haakpunt."))));
        Haakpunt heen = haakpunt(new Haakpunt("wapenkamer", 1677, F + 5, -273, plek(1679, F + 1, -273, -90f)));
        Haakpunt terug = haakpunt(new Haakpunt("spiegelzaal", 1671, F + 5, -273, plek(1669, F + 1, -273, 90f)));
        haak(heen, plek(1671, F + 1, -273, -90f));
        verhaal(new Verhaal("wapenkamer", Box.van(1679, F, -276, 1684, F + 4, -270), List.of(
                r("Wiske", "De Wapenkamer van de wachters."))));
        vondst(new Vondst(ZUID, "Zuiderkristal", "past in de troondeur", Material.PRISMARINE_CRYSTALS, plek(1686, F + 2.4, -276, 0f),
                Box.van(1685, F + 1, -277, 1687, F + 3, -275)));
        vondst(new Vondst(IJSHARNAS, "Bevroren harnas", "breng het naar Hagen", Material.IRON_CHESTPLATE, plek(1686, F + 2.4, -271, 0f),
                Box.van(1685, F + 1, -272, 1687, F + 3, -270)));
        verhaal(new Verhaal("harnas3b", Box.van(1684, F, -273, 1688, F + 4, -269), List.of(
                r("Wiske", "Het harnas van een bergwachter! Steenkoud."),
                r("Wiske", "Hagen in IJshoeve kan het vast warmen.")))).vereist = IJSHARNAS;
        haak(terug, plek(1679, F + 1, -273, 90f));
    }

    /** Hagen de smid (eiland 3a, IJshoeve) warmt het harnas: hij staat op het pad (terug naar de hub). */
    private void hagen() {
        for (ZeldaHoofdstuk h : eiland == null ? List.<ZeldaHoofdstuk>of() : eiland.delen())
            if (h != this && h.onderdeel("Hagen") instanceof Persoon hagen) {
                praten(hagen);
                hagenOpPad = true;
            }
    }

    // ── 12. de troondeur, de Troonzaal (Grimmel), zijn hol ──────────────────────────────────

    private void troonzaal() {
        int cx = 1692;                                                      // het midden (x); z = PZ
        List<int[]> zuilen = List.of(new int[]{1686, -300, 1687, -299, F + 1, F + 5}, new int[]{1697, -300, 1698, -299, F + 1, F + 5},
                new int[]{1686, -289, 1687, -288, F + 1, F + 5}, new int[]{1697, -289, 1698, -288, F + 1, F + 5});
        if (b.plaats) {
            // een achthoek: de hoeken vol ijs
            for (int x = 1681; x <= 1703; x++)
                for (int z = -305; z <= -283; z++)
                    if (Math.abs(x - 1692.5) + Math.abs(z + 293.5) > 17) vul(x, F + 1, z, x, F + 12, z, (x + z) % 3 == 0 ? Material.BLUE_ICE : Material.PACKED_ICE);
            for (int z : new int[]{-296, -292}) vul(1680, F + 1, z, 1680, F + 4, z, Material.QUARTZ_PILLAR);
            for (int z = -296; z <= -292; z++) zet(1680, F + 4, z, Material.CHISELED_QUARTZ_BLOCK);
            vul(1704, F + 1, -296, 1704, F + 4, -292, Material.AIR);          // naar het hol (oost)
            for (int x : new int[]{1690, 1694}) vul(x, F + 1, -306, x, F + 4, -306, Material.QUARTZ_PILLAR);
            for (int x = 1690; x <= 1694; x++) zet(x, F + 4, -306, Material.CHISELED_QUARTZ_BLOCK);
            // de haakpunten aan de muren (boven de deuren), om je weg te trekken als hij stormt
            zet(1681, F + 4, PZ, ZeldaHoofdstuk.HAAKPUNT);
            zet(cx, F + 5, -305, ZeldaHoofdstuk.HAAKPUNT);
            zet(cx, F + 4, -283, ZeldaHoofdstuk.HAAKPUNT);
            zet(1703, F + 5, PZ, ZeldaHoofdstuk.HAAKPUNT);
            // de oude troon van de wachters (noordwest), banieren
            zet(1685, F + 1, -303, Bouwer.trap(Material.QUARTZ_STAIRS, BlockFace.SOUTH));
            zet(1684, F + 1, -303, Bouwer.trap(Material.QUARTZ_STAIRS, BlockFace.EAST));
            zet(1686, F + 1, -303, Bouwer.trap(Material.QUARTZ_STAIRS, BlockFace.WEST));
            vul(1685, F + 2, -304, 1685, F + 3, -304, Material.QUARTZ_PILLAR);
            zet(1685, F + 4, -304, Material.CHISELED_QUARTZ_BLOCK);
            for (int x : new int[]{1688, 1697}) {
                zet(x, F + 7, -305, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.SOUTH));
                zet(x, F + 7, -283, Bouwer.gericht(Material.LIGHT_BLUE_WALL_BANNER, BlockFace.NORTH));
            }
            // het hol: sneeuw, een bed van wol, wat visgraat
            for (int x = 1705; x <= 1709; x++) for (int z = -298; z <= -289; z++) zet(x, F, z, kans(x, z, 3) == 0 ? Material.SNOW_BLOCK : Material.WHITE_WOOL);
            zet(1709, F + 1, -297, Material.BONE_BLOCK);
            zet(1709, F + 1, -290, Material.BARREL);
            zet(1707, F + 6, PZ, Bouwer.hangend(Material.SOUL_LANTERN));
        }
        poort(new Poort("troonpoort", deur(Material.BLUE_ICE.createBlockData(), 1678, F + 1, -295, 1678, F + 4, -293),
                Box.van(1673, F, -297, 1679, F + 5, -291), s -> s.heeft(NOORD) && s.heeft(ZUID) && s.heeft("harnas2")));
        verhaal(new Verhaal("troonhal", Box.van(1679, F, -295, 1679, F + 4, -293), List.of(
                r("Wiske", "Hoor je dat? Daarbinnen bromt iets groots."))));
        haakpunt(new Haakpunt("troon-west", 1681, F + 4, PZ, plek(1682, F + 1, PZ, -90f)));
        haakpunt(new Haakpunt("troon-noord", cx, F + 5, -305, plek(cx, F + 1, -304, 0f)));
        haakpunt(new Haakpunt("troon-zuid", cx, F + 4, -283, plek(cx, F + 1, -284, 180f)));
        haakpunt(new Haakpunt("troon-oost", 1703, F + 5, PZ, plek(1702, F + 1, PZ, 90f)));
        BlockData ijs = Material.PACKED_ICE.createBlockData();
        Kamer k = kamer(new Kamer("troonzaal", "De Troonzaal", Box.van(1681, F, -305, 1709, F + 12, -283), Box.van(1683, F + 1, -303, 1701, F + 6, -285),
                deur(ijs, 1680, F + 1, -295, 1680, F + 3, -293), deur(ijs, 1691, F + 1, -306, 1693, F + 3, -306)));
        k.muziek = ZeldaMuziek.GRIMMEL;
        k.hemel = 18000;
        k.haken = true;                                                     // de klauwhaak werkt hier: wegtrekken als hij stormt
        for (int[] sp : new int[][]{{cx, -301}, {cx, -286}, {1685, PZ}, {1700, PZ}, {1689, PZ}, {1696, PZ}})
            k.spawns.add(plek(sp[0], F + 1, sp[1], 0f));
        grimmel = new Grimmel(plugin, "Grimmel", plek(cx, F + 1, -300, 0f), plek(1707, F + 1, PZ, 90f), zuilen,
                List.of(r("Wiske", "Hij wordt moe! Volhouden!")),
                List.of(r("Wiske", "De nevel trekt uit hem weg…"),
                        r("Wiske", "Hij is weer gewoon Grimmel. Kijk, naar zijn hol."),
                        r("Wiske", "En daar: een hart van licht!")));
        k.baas = grimmel;
        k.golf(Soort.GRIMMEL);
        k.bijStart.add(r("Wiske", "Een reuzenijsbeer! Grimmel, de beer van de wachters."));
        k.bijStart.add(r("Wiske", "Gaat hij staan? Opzij, of haak je weg!"));
        k.tips.addAll(List.of(r("Wiske", "Sta voor een ijszuil. Komt hij? Haak je weg!"),
                r("Wiske", "Duizelt hij na een zuil? Dán slaan!")));
        Vondst hart = vondst(new Vondst("hart3", "Hartcontainer", "een hart erbij", Material.RED_DYE, plek(cx, F + 1.3, PZ, 0f),
                Box.van(cx - 1, F, PZ - 1, cx + 1, F + 3, PZ + 1)));
        hart.vereist = "k:troonzaal";                                       // pas als Grimmel vrij is
        // Grimmel in zijn hol, als hij vrij is: hij slaapt, en bromt als je hem aait
        Persoon beer = persoon(new Persoon("Grimmel", plek(1707, F + 1, PZ, 90f), null, List.of(r("Grimmel", "Grrrm… zzz."))));
        beer.soort = EntityType.POLAR_BEAR;
        beer.zichtbaar = s -> s.heeft("k:troonzaal");
        beer.uiterlijk = e -> {
            if (e instanceof org.bukkit.entity.LivingEntity le && le.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null)
                le.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(1.6);
        };
        beer.eenmalig("grimmel-1", null, List.of(
                r("Wiske", "Hij snuffelt aan je hand."),
                r("Grimmel", "Brrrmm."),
                r("Wiske", "Dat betekent dank je. Denk ik.")), null);
    }

    // ── 13. de toren en het Vorstbaken ──────────────────────────────────────────────────────

    /** De wenteltrap: de vakjes rond het midden van de toren, met de klok mee (vanaf west). */
    private static final int[][] RING = {{-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}};
    /** Hoe hoog het platform bovenop de toren ligt (boven F). */
    static final int HOOG = 28;

    private void toren() {
        int tx = 1692, tz = -313, bx = tx + 6;                              // het midden van de toren; het baken op het oosten van het platform
        if (b.plaats) {
            for (int x = tx - 2; x <= tx + 2; x++)
                for (int z = tz - 2; z <= tz + 2; z++) {
                    zet(x, F, z, Material.QUARTZ_BRICKS);
                    boolean muur = Math.abs(x - tx) == 2 || Math.abs(z - tz) == 2;
                    if (muur) for (int y = F + 1; y < F + HOOG; y++)
                        zet(x, y, z, (y - F) % 6 == 3 && Math.abs(x - tx) + Math.abs(z - tz) == 2 ? Material.LIGHT_BLUE_STAINED_GLASS : (y - F) % 6 == 0 ? Material.BLUE_ICE : Material.PACKED_ICE);
                    else vul(x, F + 1, z, x, F + HOOG + 3, z, Material.AIR);
                }
            vul(tx, F + 1, tz, tx, F + HOOG, tz, Material.QUARTZ_PILLAR);
            vul(tx, F + 1, tz + 2, tx, F + 2, tz + 2, Material.AIR);              // de deur (zuid)
            zet(tx, F + 1, tz, Material.SEA_LANTERN);
            for (int n = 1; n <= HOOG; n++) {
                int[] c = RING[n % 8], v = RING[(n + 7) % 8];
                BlockFace op = c[0] > v[0] ? BlockFace.EAST : c[0] < v[0] ? BlockFace.WEST : c[1] > v[1] ? BlockFace.SOUTH : BlockFace.NORTH;
                zet(tx + c[0], F + n, tz + c[1], n == HOOG ? Material.QUARTZ_BRICKS.createBlockData() : Bouwer.trap(Material.QUARTZ_STAIRS, op));
                if (n % 6 == 0) zet(tx, F + n, tz, Material.SEA_LANTERN);
            }
            // het platform met een borstwering, en het baken
            int p1 = tx - 6, p2 = tx + 8, q1 = tz - 6, q2 = tz + 6, y = F + HOOG;
            int[] laatste = RING[HOOG % 8];
            for (int x = p1; x <= p2; x++)
                for (int z = q1; z <= q2; z++) {
                    boolean gat = Math.abs(x - tx) <= 1 && Math.abs(z - tz) <= 1 && !(x == tx && z == tz) && !(x == tx + laatste[0] && z == tz + laatste[1]);
                    zet(x, y, z, gat ? Material.AIR : (x + z) % 3 == 0 ? Material.PACKED_ICE : Material.QUARTZ_BRICKS);
                    vul(x, y + 1, z, x, y + 7, z, Material.AIR);
                    boolean rand = x == p1 || x == p2 || z == q1 || z == q2;
                    if (rand) zet(x, y + 1, z, Material.QUARTZ_SLAB);
                    if (Math.abs(x - tx) == 2 && Math.abs(z - tz) <= 2 || Math.abs(z - tz) == 2 && Math.abs(x - tx) <= 2)
                        if (!(x == tx + laatste[0] * 2 && z == tz + laatste[1] * 2)) zet(x, y + 1, z, Material.QUARTZ_SLAB);   // om het trapgat
                }
            for (int[] h : new int[][]{{p1, q1}, {p1, q2}, {p2, q1}, {p2, q2}}) ijspiek(h[0], y + 1, h[1], 5);
            // het baken: een kooi van zuilen met een kern (dood glas; voor wie hem ontstak: licht)
            for (int[] c : new int[][]{{bx - 1, tz - 1}, {bx + 1, tz - 1}, {bx - 1, tz + 1}, {bx + 1, tz + 1}}) vul(c[0], y + 1, c[1], c[0], y + 4, c[1], Material.QUARTZ_PILLAR);
            vul(bx - 1, y + 5, tz - 1, bx + 1, y + 5, tz + 1, Material.PACKED_ICE);
            zet(bx, y + 6, tz, Material.BLUE_ICE);
            zet(bx, y + 7, tz, Material.LIGHTNING_ROD);
            zet(bx, y + 1, tz, Material.CHISELED_QUARTZ_BLOCK);
            zet(bx, y + 2, tz, Material.TINTED_GLASS);
            zet(bx, y + 3, tz, Material.QUARTZ_SLAB);
        }
        bakenKern = new Location(w, bx, F + HOOG + 2, tz);
        verhaal(new Verhaal("toren3b", Box.van(1691, F, -310, 1693, F + 4, -307), List.of(
                r("Wiske", "Een trap naar boven. Naar het baken!"))));
        rustvuur(tx - 4, F + HOOG, tz - 4);
        rust(new Rust("vorstbaken", "Het Vorstbaken", plek(tx - 2, F + HOOG + 1, tz + 3, 90f), Box.van(tx - 6, F + HOOG, tz - 6, tx + 8, F + HOOG + 6, tz + 6), List.of()));
        vondst(new Vondst("baken3", "Het Vorstbaken", "brandt weer", Material.NETHER_STAR, plek(bx - 2, F + HOOG + 1.5, tz, 0f),
                Box.van(bx - 3, F + HOOG + 1, tz - 1, bx - 2, F + HOOG + 3, tz + 1)));
        Verhaal einde = verhaal(new Verhaal("vorstbaken", Box.van(tx + 2, F + HOOG, tz - 6, tx + 4, F + HOOG + 6, tz + 6), List.of(
                r("Wiske", "Het Vorstbaken brandt! Ik voel me nóg feller!"),
                r("Wiske", "Kijk, ver in het zuiden: rook boven een berg."),
                r("Wiske", "De Gloedberg. Daar slaapt het laatste baken."),
                r(null, "Het Vorstbaken brandt weer"))));
        einde.vereist = "baken3";
        einde.einde = true;
    }

    /**
     * Zonder blokken (dus zonder herbouw): de doelen voor {@code /hint} en Tijmen, na die van het dal (3a). Het bergpad op,
     * het paleis in, Tijmen, dan in elke volgorde de twee kristallen (noord: glijden en de Wachtzaal; zuid: de spiegels en
     * de Wapenkamer), het harnas naar Hagen, de troondeur, Grimmel, het hart en het baken.
     */
    private void motor() {
        doel("bergpad", "Het bergpad is vrij. Naar boven!", "Haak je omhoog bij de ijswand, achter de berghut.",
                new Location(w, 1590.5, G + 15, PZ + 0.5), s -> s.bezocht(id(), 1));
        doel("paleispoort", "De brug van het paleis is op.", "Haak je naar de poorttoren, boven de poort.",
                new Location(w, 1625.5, F + 1, PZ + 0.5), s -> s.heeft("r:valbrug") || s.heeft(ZONNESTEEN));
        doel("tijmen3b", "Hoor je dat? Iemand roept!", "Een bom tegen de gebarsten muur in de Poortzaal.",
                plekVan("Tijmen"), s -> s.heeft(ZONNESTEEN));
        doel("kristal", "glijzaal", "Noord: een zaal vol glad ijs.", "Glij van rots naar rots. Aan de overkant ligt een kristal.",
                plekVan("glijzaal"), s -> s.heeft(NOORD));
        doel("kristal", "wachtzaal", "Een trap omhoog, en wachters.", "Versla de ijsknechten in de Wachtzaal.",
                plekVan("wachtzaal"), s -> s.heeft("k:wachtzaal") || !s.heeft(NOORD));
        doel("kristal", "spiegelzaal", "Zuid: een zaal vol spiegels.", "Draai de spiegels: het licht moet naar het slot.",
                plekVan("spiegelzaal"), s -> s.heeft(ZUID) && (s.heeft(IJSHARNAS) || s.heeft("harnas2")));
        doel("hagen3b", "Dat harnas is steenkoud. Wie warmt het?", "Breng het harnas naar Hagen, in IJshoeve.",
                hagenPlek(), s -> s.heeft("harnas2"));
        doel("troonpoort", "Twee kristallen voor de troondeur.", "De troondeur is in de Oostgang.",
                plekVan("troonpoort"), s -> s.heeft("v:troonhal") || s.heeft("k:troonzaal"));
        doel("grimmel", "Daarbinnen wacht Grimmel.", "Laat hem tegen een ijszuil stormen. Dán slaan!",
                plekVan("troonzaal"), s -> s.heeft("k:troonzaal") || s.heeft("hart3"));
        doel("hart3", "Grimmel is vrij. Pak het hart van licht.", "Midden in de Troonzaal.",
                plekVan("hart3"), s -> s.heeft("hart3"));
        doel("vorstbaken", "De toren op, naar het Vorstbaken.", "Achter de Troonzaal gaat een trap omhoog.",
                plekVan("baken3"), s -> s.heeft("baken3"));
    }

    /** Waar Hagen staat (bij zijn smidse in IJshoeve, eiland 3a). */
    private Location hagenPlek() {
        if (eiland != null) for (ZeldaHoofdstuk h : eiland.delen()) if (h != this && h.plekVan("Hagen") != null) return h.plekVan("Hagen");
        return new Location(w, 1500.5, G + 1, -299.5);
    }

    // ── tijdens het spel: sneeuw, de zonnesteen, het baken ──────────────────────────────────

    @Override
    void tik(ZeldaSpel spel, List<Player> wie, long nu) {
        for (Player p : wie) {
            if (!p.getWorld().equals(w) || spel.beheer.is(p.getUniqueId()) || inPaleis(p.getLocation())) continue;
            Location l = p.getLocation();
            if (w.getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) > l.getBlockY() + 1) continue;     // onder een dak
            java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
            for (int i = 0; i < 12; i++) p.spawnParticle(Particle.SNOWFLAKE, l.clone().add(r.nextDouble(-10, 10), r.nextDouble(2, 8), r.nextDouble(-10, 10)), 1, 0, 0, 0, 0.02);
        }
        zon(spel);
        baken(spel);
    }

    /** De houder in de Spiegelzaal: brandt als er iemand met de zonnesteen in de zaal is (dan schijnt het licht). */
    private void zon(ZeldaSpel spel) {
        if (spiegelzaal == null) return;
        boolean aan = spiegelzaal.opgelost || spel.licht(spiegelzaal);
        if (aan == zonAan) return;
        zonAan = aan;
        int[] c = spiegelzaal.bron;
        w.getBlockAt(c[0], c[1], c[2]).setType(aan ? ZON_AAN : ZON_UIT, false);
        if (aan) w.playSound(new Location(w, c[0] + 0.5, c[1] + 0.5, c[2] + 0.5), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
    }

    /**
     * Wie het baken ontstak ({@code baken3}), ziet het branden: de kern licht op en er gaat een bundel omhoog, te zien
     * van overal op de Witte Toppen (ook vanuit IJshoeve).
     */
    private void baken(ZeldaSpel spel) {
        if (bakenKern == null || eiland == null) return;
        BlockData aan = Material.SEA_LANTERN.createBlockData();
        Particle.DustOptions licht = new Particle.DustOptions(org.bukkit.Color.fromRGB(0xdff6ff), 2.0f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            ZeldaSpel.Sessie ss = spel.sessie(p);
            if (ss == null || ss.vaart != null || ss.h.eiland != eiland || !spel.voortgang.van(p.getUniqueId()).heeft("baken3")) continue;
            if (!p.getWorld().equals(w) || p.getLocation().distanceSquared(bakenKern) > 260 * 260) continue;
            p.sendBlockChange(bakenKern, aan);
            for (int i = 1; i < 40; i += 2) p.spawnParticle(Particle.DUST, bakenKern.clone().add(0.5, i, 0.5), 1, 0.12, 0.3, 0.12, 0, licht);
        }
    }
}
