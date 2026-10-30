package nl.burggraaf.mc.zelda;

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
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

/**
 * Eiland 5a, «Nachtrots»: de muren (zie ZELDA.md). Met drie bakens brak de Nevelring; Brecht legt aan in de oude
 * visserskreek, waar niemand kijkt. Daar zit Tijmen verstopt, en achter een raampje in de rotswand zit <b>Wolf</b>, een
 * gevangen wachter van Zelda: hij geeft je de sleutel van de muurdeur en vertelt hoe je binnenkomt («blijf uit hun licht»).
 *
 * <p>Over het klifpad (een haak, een bom) naar de muurvoet, de muurdeur door en de <b>weergang</b> op: vier wachters met
 * een lantaarn lopen er op een vast ritme heen en weer ({@link ZeldaHoofdstuk.Sluip}). Wie in hun licht komt, is gezien:
 * het hek slaat dicht en je staat weer bij het zielvuur. Geen gevecht. Wacht in de balkonnetjes tot een wachter langs is.
 * De Kerkertoren gaat omlaag naar de <b>kerkers</b> (de hub binnen): de cellengang (nog een sluipstuk) met gevangenen van
 * alle eilanden en de slapende kerkermeester met de sleutelbos; zuid achter een gebarsten muur de <b>kapel</b> van Zelda's
 * wachters (haak je over de put, steek de drie vuren aan): de <b>lichtpijlen</b>. Waar een lichtpijl Morgals nevel raakt,
 * verdwijnt die ({@link ZeldaHoofdstuk.Nevel}): de kapeldeur, de wapenkamer (het zwarte harnas; Mira op het Zilverstrand
 * maakt het in het licht van haar vuurtoren weer schoon: het <b>lichtharnas</b>), de nevelgang terug, de kerkertrap.
 * Boven het wachthuis (rust, bron), de binnenpoort (het wachtwoord van Wolf en het lichtharnas) en het enige gevecht: de
 * <b>binnenplaats</b>. Daarna de torenpoort: de toren (5b) is aangekondigd. Na de binnenplaats zijn de wachters gevlucht.
 *
 * <p>Het vak is x 1640 … 1879; tot de toren (5b) er is, bouwt dit deel ook het rotseiland met de toren in het vak van 5b
 * (x 1880 … 2000), als decor. Rustplaatsen houden hun nummer: zet een nieuwe altijd achteraan.
 */
final class ZeldaEiland5a extends ZeldaHoofdstuk {

    static final int VERSIE = 1;
    /** Het vak van dit halve eiland, en dat van de toren (5b; hier alleen decor zolang 5b er niet is). */
    static final int X1 = 1640, X2 = 1879, Z1 = -150, Z2 = 150, TX1 = 1880, TX2 = 2000;
    /** Het midden van de toren (5b), op zijn eigen rots oost van de burcht. */
    static final int TCX = 1940, TCZ = 0;
    /** De vlaggen van 5a. */
    static final String MUURSLEUTEL = "muursleutel", SLEUTELBOS = "sleutelbos", ZWARTHARNAS = "zwartharnas", WACHTWOORD = "wachtwoord",
            LICHTPIJLEN = "lichtpijlen", WOLF_VRIJ = "vrij:wolf", MAANMOS = "maanmos", GEVANGENEN = "gevangenen", LICHTHARNAS = "lichtharnas";
    /** De gevangenen in de cellen (naast Wolf), elk van een ander eiland: id, naam. */
    static final String[][] GEVANGEN = {{"wolkje", "Wolkje"}, {"bas", "Bas"}, {"kiek", "Kiek"}, {"joppe", "Joppe"}};
    static final String STUKJE_GEVANGENEN = "hs:nachtrots-1", STUKJE_KLIP = "hs:nachtrots-2", STUKJE_KIST = "hs:nachtrots-3";

    private final Plugin plugin;
    private final ZeldaEiland2a zilverstrand;               // Mira (de vuurtoren) maakt het zwarte harnas schoon
    private Bouwer b;
    int G, K, P, W;                                          // G = zeeniveau + 1; K = de vloer van de kerkers; P = het plateau; W = de weergang
    private int g;
    private int[][] top;
    private byte[][] soort;
    private static final byte ZEE = 0, KREEK = 1, STRAND = 2, ROTS = 3, VLAK = 4;
    private final List<int[]> holtes = new ArrayList<>();
    private Poort wolfCel;
    private final java.util.Map<String, Poort> cellen = new java.util.HashMap<>();
    private Location torenTop, raam;
    /** Staat Mira (eiland 2a: het lichtharnas) op het pad? Nee = het Zilverstrand was er niet bij het bouwen (de zelftest meldt het). */
    boolean miraOpPad;
    /** Tijmen bij de torenpoort (na de binnenplaats): hij breekt Morgals zegel als de toren (5b) er is. */
    Persoon torenTijmen;

    ZeldaEiland5a(Plugin plugin, ZeldaEiland2a zilverstrand) {
        this.plugin = plugin;
        this.zilverstrand = zilverstrand;
    }

    @Override String id() { return "e5a"; }
    @Override String naam() { return "Nachtrots"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda-e5a"; }

    /** Een hub (de kreek, de kerkers) met stukken in elke volgorde: «opnieuw vanaf hier» vergeet alleen tot de volgende rustplaats. */
    @Override boolean open() { return true; }

    /** Bestaat de toren (5b) al? Dan bouwt dit deel zijn rots niet meer (zoals 4a de kegel van 4b). */
    private boolean torenErbij() {
        return eiland != null && eiland.delen().size() > 1;
    }

    @Override
    String wacht() {
        return "De torenpoort zit nog dicht. Nog even geduld.";
    }

    // ── tijd, naam en muziek per plek ───────────────────────────────────────────────────────

    /** Op Nachtrots is het altijd nacht (Morgal wil dat): eerst de late avond, dieper in de burcht de nacht. Is Morgal weg (5b), dan de ochtend. */
    @Override
    long hemel(Location l, ZeldaVoortgang.Stand s) {
        if (s.heeft("zelda")) return 1500;
        if (s.heeft(ZeldaEiland5b.VRIJ_ZELDA) || s.heeft("k:nachttroon")) return 23300;
        if (s.heeft("k:binnenplaats")) return 18000;
        if (s.heeft(LICHTPIJLEN)) return 17000;
        return 14800;
    }

    private static boolean in(double x, double y, double z, int x1, int y1, int z1, int x2, int y2, int z2) {
        return x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1;
    }

    @Override
    String plekNaam(Location l) {
        double x = l.getX(), y = l.getY(), z = l.getZ();
        if (torenErbij() && in(x, y, z, 1859, P, -6, 1879, P + 12, 6)) return "De Nevelbrug";
        if (in(x, y, z, 1764, W - 1, -28, 1776, W + 9, 57)) return "De weergang";
        if (in(x, y, z, 1765, K, -28, 1775, W, -18)) return "De Kerkertoren";
        if (in(x, y, z, 1798, P, -38, 1812, P + 8, -22)) return "Het wachthuis";
        if (in(x, y, z, 1799, P, -21, 1841, P + 12, 21)) return "De binnenplaats";
        if (in(x, y, z, 1842, P, -5, 1872, P + 12, 5)) return "De torenpoort";
        if (in(x, y, z, 1773, P, -3, 1798, P + 8, 3)) return "De voorburcht";
        if (in(x, y, z, 1801, K, -31, 1808, P, -25)) return "De kerkertrap";
        if (in(x, y, z, 1728, K - 1, -17, 1742, K + 6, -7)) return "De wachtkamer";
        if (in(x, y, z, 1708, K - 1, -27, 1758, K + 6, -13)) return "De cellen";
        if (in(x, y, z, 1702, G, -22, 1707, K + 5, -12)) return "Het Vissersgat";
        if (in(x, y, z, 1759, K - 1, -31, 1777, K + 10, -11)) return "De kerkers";
        if (in(x, y, z, 1781, K - 1, -20, 1794, K + 7, -3)) return "De wapenkamer";
        if (in(x, y, z, 1760, G - 4, -12, 1790, K + 11, 32)) return "De kapel";
        if (in(x, y, z, 1777, K - 1, -25, 1808, K + 6, 28)) return "De wachtersgang";
        if (in(x, y, z, 1640, g, -14, 1729, P + 8, 14)) return "De visserskreek";
        if (in(x, y, z, 1720, g, 12, 1760, P + 3, 46)) return "De klippen";
        if (in(x, y, z, 1740, P - 1, 40, 1767, P + 10, 62)) return "De muurvoet";
        return "Nachtrots";
    }

    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return switch (plekNaam(l)) {
            case "De weergang", "Het wachthuis", "De binnenplaats", "De voorburcht", "De torenpoort" -> ZeldaMuziek.MUREN;
            case "De Kerkertoren", "De kerkertrap", "De wachtkamer", "De cellen", "Het Vissersgat", "De kerkers", "De wapenkamer", "De kapel", "De wachtersgang" -> ZeldaMuziek.KERKERS;
            case "De Nevelbrug" -> ZeldaMuziek.TOREN;
            default -> ZeldaMuziek.NACHTROTS;
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

    private static BlockData stam(Material m, org.bukkit.Axis as) {
        BlockData d = m.createBlockData();
        if (d instanceof Orientable o) o.setAxis(as);
        return d;
    }

    static BlockData tralies(boolean langsZ) {
        return langsZ ? Bouwer.ruit(Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH) : Bouwer.ruit(Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
    }

    private Deur deur(BlockData dicht, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Deur(dicht, Material.AIR.createBlockData()).vlak(x1, y1, z1, x2, y2, z2);
    }

    private void zetDeur(Deur d, boolean open) {
        d.isOpen = open;
        for (int[] c : d.blokken) b.zetAltijd(c[0], c[1], c[2], open ? d.open : d.dicht);
    }

    /** Morgals nevel in een doorgang (dicht = {@link Nevel#BLOK}, open = lucht). */
    private Deur nevelDeur(int x1, int y1, int z1, int x2, int y2, int z2) {
        return deur(Nevel.BLOK.createBlockData(), x1, y1, z1, x2, y2, z2);
    }

    /** Een bordje met alleen een naam (tegen de muur of een rots, kijkt naar {@code kijk}). */
    private void bord(int x, int y, int z, BlockFace kijk, String tekst) {
        zet(x, y, z, Bouwer.gericht(Material.DARK_OAK_WALL_SIGN, kijk));
        if (!b.plaats) return;
        b.daarna(() -> {
            if (!(w.getBlockAt(x, y, z).getState() instanceof org.bukkit.block.Sign s)) return;
            s.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text(tekst));
            s.setWaxed(true);
            s.update(true, false);
        });
    }

    /** Een staand bordje (op de grond, kijkt naar {@code kijk}). */
    private void paalbord(int x, int y, int z, BlockFace kijk, String tekst) {
        zet(x, y, z, Bouwer.gericht(Material.DARK_OAK_SIGN, kijk));
        if (!b.plaats) return;
        b.daarna(() -> {
            if (!(w.getBlockAt(x, y, z).getState() instanceof org.bukkit.block.Sign s)) return;
            s.getSide(org.bukkit.block.sign.Side.FRONT).line(1, Component.text(tekst));
            s.setWaxed(true);
            s.update(true, false);
        });
    }

    /** Rustplaats: een zielvuur op een plaat van zwart steen (3 bij 3), met een zielslamp aan een paal. */
    private void rustvuur(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) zet(x + dx, y, z + dz, dx == 0 && dz == 0 ? Material.COBBLED_DEEPSLATE : Material.POLISHED_BLACKSTONE);
        zet(x, y + 1, z, ZeldaEiland4a.vuur(Material.SOUL_CAMPFIRE, true));
        zet(x + 1, y + 1, z - 1, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        zet(x + 1, y + 2, z - 1, Material.IRON_BARS);
        zet(x + 1, y + 3, z - 1, Material.SOUL_LANTERN);
    }

    private static Villager.Profession beroep(String id) {
        return Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(id));
    }

    /** De rots van Nachtrots: zwart, met hier en daar wat paars van Morgals nevel. */
    static Material rots(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 32);
        return k < 11 ? Material.BLACKSTONE : k < 18 ? Material.DEEPSLATE : k < 22 ? Material.BASALT : k < 25 ? Material.COBBLED_DEEPSLATE
                : k < 28 ? Material.TUFF : k < 30 ? Material.SMOOTH_BASALT : k < 31 ? Material.OBSIDIAN : Material.CRYING_OBSIDIAN;
    }

    /** De muren van de burcht: zwarte bakstenen van Morgal. */
    static Material zwart(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x * 3 + 1, y, z * 5 - 2) % 20);
        return k < 10 ? Material.POLISHED_BLACKSTONE_BRICKS : k < 14 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : k < 17 ? Material.DEEPSLATE_BRICKS
                : k < 19 ? Material.DEEPSLATE_TILES : Material.BLACKSTONE;
    }

    /** De muren van de kerkers: oud, vochtig steen. */
    static Material steen(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x * 5 - 3, y, z * 7 + 1) % 20);
        return k < 9 ? Material.DEEPSLATE_BRICKS : k < 13 ? Material.CRACKED_DEEPSLATE_BRICKS : k < 16 ? Material.COBBLED_DEEPSLATE
                : k < 18 ? Material.MOSSY_COBBLESTONE : Material.DEEPSLATE_TILES;
    }

    /** De vloer van de kerkers en de burcht: tegels. */
    static Material vloer(int x, int z) {
        return (x + z) % 7 == 0 ? Material.POLISHED_BASALT : (x * 3 + z) % 5 == 0 ? Material.DEEPSLATE_TILES
                : (x + 2 * z) % 3 == 0 ? Material.POLISHED_DEEPSLATE : Material.POLISHED_BLACKSTONE;
    }

    // ── het terrein ─────────────────────────────────────────────────────────────────────────

    /**
     * Hoe ver deze kolom binnen de kust van Nachtrots ligt (blokken; negatief = zee): een afgeronde rechthoek om de burcht,
     * met een rafelige kust. De kreek snijdt er in het westen in (zie {@link #vorm}).
     */
    static double land(int x, int z) {
        double rx = (x - 1785) / 88.0, rz = z / 74.0;
        double s = Math.pow(Math.pow(Math.abs(rx), 4) + Math.pow(Math.abs(rz), 4), 0.25);
        double ruw = 2.5 * Math.sin(x / 7.3 + z / 11.0) + 1.8 * Math.sin(z / 5.9 - x / 13.0) + 1.2 * Math.sin((x + z) / 4.1);
        double d = (1 - s) * 74 + ruw;
        if (x >= 1735 && x <= 1872 && Math.abs(z) <= 64) d = Math.max(d, 9);    // rond de burcht: altijd plateau
        return d;
    }

    /** De rots van de toren (5b): een ronde pilaar oost van de burcht. */
    static double torenRots(int x, int z) {
        double r = Math.hypot(x - TCX, z - TCZ);
        return 28 - r + 2.0 * Math.sin(Math.atan2(z - TCZ, x - TCX) * 5) + 1.2 * Math.sin(x / 3.7 + z / 4.3);
    }

    /** Is deze kolom vlak plateau (waar je loopt of waar gebouwd wordt)? Anders is de rots erboven ruw en hoog. */
    private static boolean vlak(int x, int z) {
        if (x >= 1764 && x <= 1866 && Math.abs(z) <= 60) return true;                 // de burcht en zijn voet
        if (x >= 1742 && x <= 1767 && z >= 40 && z <= 62) return true;                // de muurvoet
        if (x >= 1752 && x <= 1767 && z >= -6 && z <= 44) return true;                // het pad langs de muur
        if (x >= 1744 && x <= 1767 && z >= -6 && z <= 6) return true;                 // voor de hoofdpoort
        if (x >= 1704 && x <= 1720 && z >= -27 && z <= -13) return true;              // de Kreektoren
        return false;
    }

    private static int kreekBreed(int x) {
        if (x < 1700) return 9;
        if (x > 1727) return -1;
        return x >= 1725 ? 11 - (x - 1724) : 11;
    }

    /** Hoogte (bovenste blok) en soort van een kolom. */
    private int[] vorm(int x, int z) {
        int kb = kreekBreed(x);
        if (x >= 1650 && kb >= 0 && Math.abs(z) <= kb) {
            if (x < 1700) return new int[]{Math.abs(z) <= 5 ? G - 6 : G - 4, KREEK};
            return new int[]{x <= 1703 ? G - 1 : G, STRAND};
        }
        double d = x >= TX1 ? torenRots(x, z) : land(x, z);
        if (x >= TX1 && d <= 0 || x < TX1 && d <= 0) return new int[]{G - 2 - (int) Math.min(6, -d / 2.5), ZEE};
        if (x >= TX1) {                                                                // de rots van de toren: steil uit zee, vlak boven
            if (d < 5) return new int[]{G + (int) (d * 4), ROTS};
            return new int[]{P + 6, VLAK};
        }
        if (vlak(x, z)) return new int[]{P, VLAK};
        if (d < 1) return new int[]{G - 1, ROTS};
        if (d < 5) return new int[]{Math.min(P + 2, G + (int) (d * 3.4)), ROTS};
        int ruw = 3 + (int) Math.round(1.6 * Math.sin(x / 3.1) * Math.cos(z / 2.7) + 1.1 * Math.sin((x - z) / 5.3) + (kans(x, z, 9) == 0 ? 2 : 0));
        return new int[]{P + Math.max(3, ruw), ROTS};
    }

    private void terrein() {
        int xe = torenErbij() ? X2 : TX2;
        for (int x = X1; x <= xe; x++)
            for (int z = Z1; z <= Z2; z++) {
                int[] v = vorm(x, z);
                top[x - X1][z - Z1] = v[0];
                soort[x - X1][z - Z1] = (byte) v[1];
            }
        for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(xe, 16); cx++)
            for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) {
                int x1 = Math.max(X1, cx * 16), x2 = Math.min(xe, cx * 16 + 15), z1 = Math.max(Z1, cz * 16), z2 = Math.min(Z2, cz * 16 + 15);
                boolean diep = true;
                for (int x = x1; x <= x2 && diep; x++)
                    for (int z = z1; z <= z2 && diep; z++) diep = soort[x - X1][z - Z1] == ZEE && top[x - X1][z - Z1] == G - 8;
                if (diep) {
                    vul(x1, g + 1, z1, x2, G - 9, z2, Material.DEEPSLATE);
                    vul(x1, G - 8, z1, x2, G - 8, z2, Material.GRAVEL);
                    vul(x1, G - 7, z1, x2, G - 1, z2, Material.WATER);
                    vul(x1, G, z1, x2, G + 85, z2, Material.AIR);
                } else for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) kolom(x, z, top[x - X1][z - Z1], soort[x - X1][z - Z1]);
            }
    }

    private void kolom(int x, int z, int hg, byte s) {
        int ymax = G + 85;
        if (s == ZEE || s == KREEK) {
            vul(x, g + 1, z, x, hg - 1, z, Material.DEEPSLATE);
            zet(x, hg, z, kans(x, z, 3) == 0 ? Material.TUFF : kans(x, z, 4) == 0 ? Material.DEEPSLATE : Material.GRAVEL);
            vul(x, hg + 1, z, x, G - 1, z, Material.WATER);
            vul(x, G, z, x, ymax, z, Material.AIR);
            return;
        }
        vul(x, g + 1, z, x, hg - 3, z, Material.DEEPSLATE);
        if (s == STRAND) {
            vul(x, hg - 2, z, x, hg - 1, z, Material.GRAVEL);
            int k = kans(x, z, 7);
            zet(x, hg, z, k < 3 ? Material.GRAVEL : k < 5 ? Material.TUFF : k < 6 ? Material.COBBLED_DEEPSLATE : Material.BLACK_CONCRETE_POWDER);
            vul(x, hg + 1, z, x, ymax, z, Material.AIR);
            return;
        }
        for (int y = hg - 2; y <= hg; y++) zet(x, y, z, rots(x, y, z));
        vul(x, hg + 1, z, x, ymax, z, Material.AIR);
    }

    // ── gangen en zalen ─────────────────────────────────────────────────────────────────────

    private void holte(int x1, int y1, int z1, int x2, int y2, int z2) {
        holtes.add(new int[]{Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)});
    }

    private boolean uitgehakt(int x, int y, int z) {
        for (int[] hh : holtes) if (x >= hh[0] && x <= hh[3] && y >= hh[1] && y <= hh[4] && z >= hh[2] && z <= hh[5]) return true;
        return false;
    }

    /** Een gang of zaal uithakken: een vloer van tegels op {@code y}, lucht tot {@code y + hoog}, wanden en dak van kerkersteen. */
    private void gang(int x1, int z1, int x2, int z2, int y, int hoog) {
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean muur = x < x1 || x > x2 || z < z1 || z > z2;
                if (muur) {
                    for (int yy = y; yy <= y + hoog + 1; yy++) if (!uitgehakt(x, yy, z)) zet(x, yy, z, steen(x, yy, z));
                    continue;
                }
                zet(x, y, z, vloer(x, z));
                vul(x, y + 1, z, x, y + hoog, z, Material.AIR);
                if (!uitgehakt(x, y + hoog + 1, z)) zet(x, y + hoog + 1, z, steen(x, y + hoog + 1, z));
            }
        holte(x1, y + 1, z1, x2, y + hoog, z2);
    }

    /** Een doorgang (lucht, met een vloer) door de wanden heen: na de zalen, zodat geen wand hem dichtzet. */
    private void doorgang(int x1, int z1, int x2, int z2, int y, int hoog) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                zet(x, y, z, vloer(x, z));
                vul(x, y + 1, z, x, y + hoog, z, Material.AIR);
            }
        holte(x1, y + 1, z1, x2, y + hoog, z2);
    }

    /** De wenteltrap: de vakjes rond een zuil, met de klok mee (vanaf west). */
    private static final int[][] RING = {{-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}};

    /**
     * Een wenteltrap om een zuil op (cx, cz): de eerste trede op {@code y0 + 1} in vakje {@code begin} van {@link #RING}, dan
     * elke trede een vakje verder en een hoger, {@code hoog} treden (de laatste een heel blok). Lucht boven de treden (drie
     * hoog), de zuil van {@code y0 + 1} tot {@code y0 + hoog}. Geeft het vakje van de laatste trede.
     */
    private int[] wenteltrap(int cx, int cz, int y0, int hoog, int begin, Material trap, Material blok, Material zuil) {
        int[] laatste = null;
        for (int n = 1; n <= hoog; n++) {
            int i = Math.floorMod(begin + n - 1, 8);
            int[] c = RING[i], v = RING[Math.floorMod(i - 1, 8)];
            BlockFace op = c[0] > v[0] ? BlockFace.EAST : c[0] < v[0] ? BlockFace.WEST : c[1] > v[1] ? BlockFace.SOUTH : BlockFace.NORTH;
            if (n == 1) {                                                   // de eerste trede: kijkt de kant op van de tweede
                int[] d = RING[Math.floorMod(i + 1, 8)];
                op = d[0] > c[0] ? BlockFace.EAST : d[0] < c[0] ? BlockFace.WEST : d[1] > c[1] ? BlockFace.SOUTH : BlockFace.NORTH;
            }
            zet(cx + c[0], y0 + n, cz + c[1], n == hoog ? blok.createBlockData() : Bouwer.trap(trap, op));
            vul(cx + c[0], y0 + n + 1, cz + c[1], cx + c[0], y0 + n + 3, cz + c[1], Material.AIR);
            laatste = new int[]{cx + c[0], cz + c[1]};
        }
        vul(cx, y0 + 1, cz, cx, y0 + hoog, cz, zuil);
        return laatste;
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        g = bouwer.g;
        G = g + 8;
        K = G + 2;
        P = G + 14;
        W = P + 10;
        leeg(bouwer.w);
        holtes.clear();
        cellen.clear();
        miraOpPad = false;
        speelgebied = Box.van(X1, g - 4, Z1 + 2, X2, G + 90, Z2 - 2);
        boolean bouwen = bouwer.plaats;
        List<long[]> chunks = new ArrayList<>();
        if (bouwen) {
            int xe = torenErbij() ? X2 : TX2;
            for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(xe, 16); cx++)
                for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) chunks.add(new long[]{cx, cz});
            b.daarna(() -> chunks.forEach(c -> w.addPluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = new int[xe - X1 + 1][Z2 - Z1 + 1];
            soort = new byte[xe - X1 + 1][Z2 - Z1 + 1];
            terrein();
            burcht();
            if (!torenErbij()) toren();
        }
        kreek();
        klippen();
        muurvoet();
        weergang();
        kerkerhal();
        cellengang();
        vissersgat();
        kapel();
        wachtersgang();
        mira();
        kerkertrap();
        binnenplaats();
        voorburcht();
        torenpoort();
        if (bouwen) {
            doorgangen();
            b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = null;
            soort = null;
        }
        // de begintoestand van deuren, poorten, nevel en puzzels: altijd (ook zonder herbouw)
        for (Kamer k : kamers) {
            zetDeur(k.in, true);
            zetDeur(k.uit, false);
        }
        for (Barst br : barsten) zetDeur(br.muur, false);
        for (Poort po : poorten) zetDeur(po.deur, false);
        for (Nevel nv : nevels) zetDeur(nv.nevel, false);
        for (Sluip sl : sluipen) {
            zetDeur(sl.hek, true);
            sl.begin = 0;
            sl.hekOpen = 0;
        }
        for (Reeks r : reeksen) {
            zetDeur(r.deur, false);
            for (int[] d : r.dingen) b.zetAltijd(d[0], d[1], d[2], r.uit);
        }
        motor();
        b = null;
    }

    /** De doorgangen tussen de zalen (als laatste: zo zet geen wand ze weer dicht). */
    private void doorgangen() {
        doorgang(1759, -21, 1759, -19, K, 4);                         // de kerkers → de cellengang (het hek)
        doorgang(1767, -12, 1769, -11, K, 4);                         // de kerkers → de zuidgang
        doorgang(1777, -24, 1777, -22, K, 4);                         // de kerkers → de nevelgang (de nevel)
        doorgang(1734, -18, 1736, -18, K, 4);                         // de cellengang → de wachtkamer
        doorgang(1767, 5, 1769, 5, K, 3);                             // de zuidgang → de kapel (de gebarsten muur)
        doorgang(1789, 25, 1789, 27, K, 3);                           // de kapel → de wachtersgang (de nevel)
        doorgang(1794, -11, 1794, -9, K, 3);                          // de wachtersgang → de wapenkamer (de nevel)
        doorgang(1786, -17, 1788, -17, K, 3);                         // de wapenkist (de nevel)
        doorgang(1803, -25, 1805, -25, K, 3);                         // de nevelgang → de kerkertrap (de nevel)
        doorgang(1708, -21, 1708, -19, K, 4);                         // de cellengang → het Vissersgat
    }

    // ── 1. de visserskreek: aankomst, de huisjes, Tijmen, Brecht ───────────────────────────

    private void kreek() {
        if (b.plaats) {
            // de steiger: donker hout op palen, de kreek in; de Zilvermeeuw ligt ernaast
            for (int x = 1686; x <= 1703; x++) {
                vul(x, G, -1, x, G, 1, Material.DARK_OAK_PLANKS);
                vul(x, G + 1, -1, x, G + 4, 1, Material.AIR);
                if (x % 4 == 2) for (int z : new int[]{-1, 1}) {
                    vul(x, G - 6, z, x, G + 1, z, Material.DARK_OAK_LOG);
                    if (z == -1 && x % 8 == 6) zet(x, G + 2, z, Material.LANTERN);
                }
            }
            schip(1664, 1684, 5);
            kreektoren();
            vul(1682, G, 2, 1683, G, 2, Material.DARK_OAK_PLANKS);                       // de loopplank naar het schip
            // drie oude vissershuisjes (verlaten, een beetje kapot), netten, tonnen, een omgekeerde roeiboot
            huisje(1704, 7, 1709, 11, false);
            huisje(1716, 7, 1722, 11, false);
            ruine(1723, -11, 1727, -7);
            for (int x = 1714; x <= 1718; x++) for (int z = 5; z <= 6; z++) zet(x, G + 3, z, Bouwer.plaat(Material.SPRUCE_SLAB, false));   // het afdakje bij Tijmen
            for (int x = 1714; x <= 1718; x += 4) vul(x, G + 1, 5, x, G + 2, 5, Material.SPRUCE_FENCE);
            zet(1712, G + 1, 9, Material.BARREL);
            zet(1713, G + 1, 9, Material.BARREL);
            zet(1712, G + 2, 9, Material.BARREL);
            for (int z = -5; z <= -2; z++) zet(1704, G + 1, z, Material.COBWEB);         // een net dat te drogen hangt
            vul(1704, G + 1, -6, 1704, G + 2, -6, Material.SPRUCE_FENCE);
            vul(1704, G + 1, -1, 1704, G + 2, -1, Material.SPRUCE_FENCE);
            // Wolfs raampje in de rotswand (noord), en een richel eronder met een opstapje
            vul(1711, G + 1, -11, 1713, G + 2, -10, Material.BLACKSTONE);
            vul(1711, G + 3, -11, 1713, G + 5, -10, Material.AIR);
            zet(1712, G + 1, -9, Material.POLISHED_BLACKSTONE);
            zet(1711, K + 4, -12, Material.SOUL_LANTERN);
            paalbord(1726, G + 1, 7, BlockFace.WEST, "De klippen");
            bord(1707, G + 2, -11, BlockFace.SOUTH, "Het Vissersgat");
            paalbord(1705, G + 1, 2, BlockFace.EAST, "De Zilvermeeuw");
            for (int x = 1724; x <= 1726; x++) for (int z = 9; z <= 11; z++) {           // de hoek naar het klifpad: strand
                zet(x, G, z, Material.GRAVEL);
                vul(x, G + 1, z, x, P + 10, z, Material.AIR);
            }
        }
        rustvuur(1712, G, 3);
        rust(new Rust("kreek", "De visserskreek", plek(1709, G + 1, 1, -90f), Box.van(1703, G - 1, -6, 1716, G + 5, 8), List.of()));
        bron("bron-kreek", plek(1720, G + 1, -4, 0f));
        verhaal(new Verhaal("kreek", Box.van(1700, G - 1, -6, 1716, G + 5, 8), List.of(
                r("Wiske", "Wat een donkere kreek. Hier woonden vissers."),
                r("Wiske", "En daarboven, op de rots: Morgals burcht."))));
        Persoon brecht = persoon(new Persoon("Brecht", plek(1697, G + 1, 0, -90f), beroep("fisherman"), List.of(
                r("Brecht", "Zeg maar waarheen. De Zilvermeeuw ligt klaar."))));
        brecht.eenmalig("brecht-kreek", null, List.of(
                        r("Brecht", "Hier legden de vissers vroeger aan. Stil, hè?"),
                        r("Brecht", "Ik wacht met de boot. Wees voorzichtig.")), null)
                .eenmalig("brecht-zelda", s -> s.heeft(ZeldaEiland5b.VRIJ_ZELDA) && !s.heeft("zelda"), List.of(
                        r("Brecht", "Prinses Zelda, aan boord! En jij ook."),
                        r("Brecht", "Iedereen wacht op het Lindewoud.")), (spel, p, s) -> spel.vaar(p, spel.eiland("lindewoud")))
                .eenmalig("brecht-harnas", s -> s.heeft(ZWARTHARNAS) && !s.heeft("harnas4"), List.of(
                        r("Brecht", "Een zwart harnas? Mira's lamp maakt alles schoon."),
                        r("Brecht", "Zeg maar, dan varen we naar het Zilverstrand.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.varen(p));
        ZeldaFeest.brechtNieuws(brecht);                       // nieuws na elke grote stap (één keer, op elke steiger)
        tijmen();
        wolfRaam();
        if (torenErbij()) {                                                     // Zelda is vrij (5b): ze wacht bij de Zilvermeeuw
            Persoon zelda = persoon(new Persoon("Zelda", plek(1700, G + 1, -1, -90f), null, List.of(r("Zelda", "Brecht vaart ons naar huis. Kom je?"))));
            zelda.soort = EntityType.MANNEQUIN;
            zelda.uiterlijk = e -> ZeldaEiland5b.zelda(e, false);
            zelda.zichtbaar = s -> s.heeft(ZeldaEiland5b.VRIJ_ZELDA) && !s.heeft("zelda");
            zelda.eenmalig("zelda-kreek", s -> s.heeft(ZeldaEiland5b.VRIJ_ZELDA) && !s.heeft("zelda"), List.of(
                    r("Zelda", "Daar ben je! Wat een donkere kreek."),
                    r("Zelda", "Praat met Brecht. Dan varen we naar huis.")), null);
        }
    }

    /** De Kreektoren: een ronde toren van Morgal op de klif boven de kreek, met een paars raam (Wolfs cel zit eronder, in de rots). */
    private void kreektoren() {
        int cx = 1712, cz = -20, voet = P + 1, top = P + 18;
        for (int x = cx - 6; x <= cx + 6; x++)
            for (int z = cz - 6; z <= cz + 6; z++) {
                double r = Math.hypot(x - cx, z - cz);
                if (r > 5.5) continue;
                boolean rand = r > 4.4;
                for (int y = voet; y <= top; y++) {
                    boolean raam = rand && (y - voet) % 7 == 4 && z == cz + 5 && x == cx;
                    zet(x, y, z, rand ? (raam ? Material.PURPLE_STAINED_GLASS : zwart(x, y, z)) : y == top ? Material.POLISHED_BLACKSTONE : Material.AIR);
                }
                if (rand && (x + z) % 2 == 0) zet(x, top + 1, z, zwart(x, top + 1, z));
            }
        for (int y = voet + 3; y < top; y += 7) zet(cx, y, cz + 3, Material.SOUL_LANTERN);
        for (int t = 0; t <= 6; t++)
            for (int x = cx - 6 + t; x <= cx + 6 - t; x++)
                for (int z = cz - 6 + t; z <= cz + 6 - t; z++) if (Math.hypot(x - cx, z - cz) <= 6.2 - t) zet(x, top + 2 + t, z, Material.DEEPSLATE_TILES);
        zet(cx, top + 9, cz, Material.CRYING_OBSIDIAN);
    }

    /** De Zilvermeeuw in de kreek: boeg naar het westen ({@code boeg}), achtersteven op {@code achter}, midden op {@code mz}. */
    private void schip(int boeg, int achter, int mz) {
        int dek = G;
        for (int x = boeg; x <= achter; x++) {
            double t = (achter - x) / (double) (achter - boeg);
            double breed = 3.0 * Math.min(1, Math.min(t * 3.5 + 0.35, (1 - t) * 2.6 + 0.1));
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
        vul(achter - 2, dek + 1, mz - 3, achter - 1, dek + 1, mz - 3, Material.AIR);   // een opening in de reling bij de loopplank
        for (int[] m : new int[][]{{boeg + 5, 12}, {boeg + 13, 10}}) {
            vul(m[0], dek + 1, mz, m[0], dek + m[1], mz, Material.SPRUCE_LOG);
            for (int z = mz - 3; z <= mz + 3; z++) zet(m[0], dek + m[1] - 2, z, stam(Material.SPRUCE_LOG, org.bukkit.Axis.Z));
            for (int y = dek + 4; y <= dek + m[1] - 3; y++)
                for (int z = mz - 2; z <= mz + 2; z++) if (z != mz) zet(m[0], y, z, y == dek + (m[1] + 2) / 2 ? Material.LIGHT_BLUE_WOOL : Material.WHITE_WOOL);
            zet(m[0], dek + m[1] + 1, mz, Material.LIGHT_BLUE_WOOL);
        }
        for (int n = 0; n < 4; n++) zet(boeg - 1 - n, dek + 1 + n / 2, mz, stam(Material.SPRUCE_LOG, org.bukkit.Axis.X));
        zet(achter - 1, dek + 1, mz, Material.LANTERN);
    }

    /** Een verlaten vissershuisje van donker hout: één kamer, een half ingezakt dak, een deur op het strand. */
    private void huisje(int x1, int z1, int x2, int z2, boolean deurZuid) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean muur = x == x1 || x == x2 || z == z1 || z == z2;
                zet(x, G, z, Material.SPRUCE_PLANKS);
                for (int y = G + 1; y <= G + 3; y++) {
                    boolean hoek = (x == x1 || x == x2) && (z == z1 || z == z2);
                    zet(x, y, z, !muur ? Material.AIR : hoek ? Material.DARK_OAK_LOG : y == G + 2 && (x + z) % 3 == 0 ? Material.AIR : Material.SPRUCE_PLANKS);
                }
                boolean gat = kans(x, z, 4) == 0 && !muur;                                 // het dak is hier en daar ingezakt
                if (!gat) zet(x, G + 4, z, Bouwer.plaat(Material.DARK_OAK_SLAB, false));
            }
        int mx = (x1 + x2) / 2, dz = deurZuid ? z2 : z1;
        vul(mx, G + 1, dz, mx, G + 2, dz, Material.AIR);
        zet(x1 + 1, G + 1, deurZuid ? z1 + 1 : z2 - 1, Material.BARREL);
        zet(x2 - 1, G + 1, deurZuid ? z1 + 1 : z2 - 1, Material.CAULDRON);
    }

    /** Een ingestort huisje: alleen de muurtjes nog, wat balken. */
    private void ruine(int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean muur = x == x1 || x == x2 || z == z1 || z == z2;
                if (!muur) continue;
                int hh = 1 + kans(x, z, 3);
                for (int y = G + 1; y <= G + hh; y++) zet(x, y, z, kans(x + y, z, 3) == 0 ? Material.MOSSY_COBBLESTONE : Material.COBBLED_DEEPSLATE);
            }
        zet(x1 + 1, G + 1, z1 + 1, stam(Material.DARK_OAK_LOG, org.bukkit.Axis.X));
        zet(x1 + 2, G + 1, z1 + 1, stam(Material.DARK_OAK_LOG, org.bukkit.Axis.X));
    }

    /**
     * Tijmen (zie ZELDA.md): hij ging vooruit en zit verstopt in de kreek; hij weet van Wolf. Na de binnenplaats staat hij
     * bij de torenpoort (een tweede Tijmen, zie {@link #torenpoort}).
     */
    private void tijmen() {
        Persoon tijmen = persoon(new Persoon("Tijmen", plek(1716, G + 1, 6, 180f), null, List.of(r("Tijmen", "Sst. Morgal hoort alles, behalve fluisteren."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.zichtbaar = s -> !s.heeft("k:binnenplaats");
        tijmen.eenmalig("tijmen-kreek", null, List.of(
                        r("Tijmen", "Sst! Ik ben het maar. Zachtjes, hè."),
                        r("Wiske", "Hij is er alwéér!"),
                        r("Tijmen", "Hoor je dat? Iemand fluit. Achter dat raampje."),
                        r("Tijmen", "In de rots zit een cel. Ga eens kijken.")), null)
                .eenmalig("tijmen-wolf", s -> s.heeft(WOLF_VRIJ), List.of(
                        r("Tijmen", "Wolf is vrij. Ik heb zijn arm verbonden."),
                        r("Tijmen", "Een taaie, die wachter.")), null)
                .eenmalig("tijmen-pijlen", s -> s.heeft(LICHTPIJLEN), List.of(
                        r("Tijmen", "Pijlen van licht! Daar is Morgal bang voor.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
        praten(tijmen);
    }

    /** Wolf achter zijn raampje in de rotswand (de cel ligt aan de cellengang): hij geeft de sleutel van de muurdeur. */
    private void wolfRaam() {
        raam = new Location(w, 1712.5, G + 3, -10.5);
        verhaal(new Verhaal("raam", Box.van(1709, G, -12, 1715, G + 4, -8), List.of(
                r("Wiske", "Daar, achter dat raampje. Iemand kijkt naar ons!")))).als = s -> !s.heeft(MUURSLEUTEL);
        Persoon wolf = persoon(new Persoon("Wolf", plek(1712, K + 1, -14, 0f), beroep("armorer"), List.of(r("Wolf", "Sst. Ze horen alles."))));
        wolf.streek = Villager.Type.TAIGA;
        wolf.zichtbaar = s -> !s.heeft(WOLF_VRIJ);
        wolf.gesprek("wolf-vrij", s -> s.heeft(SLEUTELBOS) && !s.heeft(WOLF_VRIJ) && wolfCel != null && wolfCel.deur.isOpen, List.of(
                        r("Wolf", "De sleutels! Je bent een held."),
                        r("Wolf", "Ik glip naar de kreek. Zoek me daar.")), (spel, p, s) -> {
                    if (s.zet(WOLF_VRIJ)) spel.voortgang.vuil();
                    p.getWorld().spawnParticle(Particle.CLOUD, plek(1712, K + 1.5, -14, 0f), 15, 0.3, 0.5, 0.3, 0.02);
                })
                .eenmalig("wolf-1", s -> !s.heeft(WOLF_VRIJ), List.of(
                        r("Wolf", "Psst! Hier, bij het raam!"),
                        r("Wolf", "Ik ben Wolf, een wachter van Zelda."),
                        r("Wolf", "Hier, de sleutel van de muurdeur. Goed verstopt."),
                        r("Wolf", "Over de muur, de toren in. Blijf uit hun licht!")), (spel, p, s) -> spel.beloon(p, MUURSLEUTEL, "Muursleutel", "voor de deur onder aan de muur"))
                .gesprek(null, s -> !s.heeft(SLEUTELBOS), List.of(r("Wolf", "De kerkermeester heeft de sleutels van de cellen.")), null);
        praten(wolf);
    }

    // ── 2. de klippen: een haak over de kloof, een bom in de smokkelgang ────────────────────

    private void klippen() {
        if (b.plaats) {
            // trap omhoog uit de kreek (zuid), een open pad tussen de rotsen
            for (int n = 1; n <= 6; n++) {
                int z = 11 + n;
                for (int x = 1724; x <= 1726; x++) {
                    vul(x, G + 1, z, x, G + n - 1, z, Material.BLACKSTONE);
                    zet(x, G + n, z, Bouwer.trap(Material.POLISHED_BLACKSTONE_STAIRS, BlockFace.SOUTH));
                    vul(x, G + n + 1, z, x, P + 10, z, Material.AIR);
                }
            }
            // de eerste richel (west van de kloof)
            for (int x = 1723; x <= 1733; x++)
                for (int z = 17; z <= 20; z++) {
                    zet(x, G + 6, z, z == 20 || x == 1723 ? Material.BLACKSTONE : vloer(x, z) == Material.POLISHED_BASALT ? Material.SMOOTH_BASALT : Material.POLISHED_BLACKSTONE);
                    vul(x, G + 7, z, x, P + 10, z, Material.AIR);
                }
            // de kloof (x 1734 … 1739): diep, met water op de bodem en een ladder terug (westkant)
            for (int x = 1734; x <= 1739; x++)
                for (int z = 16; z <= 21; z++) {
                    zet(x, G - 2, z, Material.GRAVEL);
                    vul(x, G - 1, z, x, G, z, Material.WATER);
                    vul(x, G + 1, z, x, P + 10, z, Material.AIR);
                }
            for (int y = G + 1; y <= G + 6; y++) zet(1734, y, 18, Bouwer.gericht(Material.LADDER, BlockFace.EAST));
            zet(1733, G + 7, 18, Material.AIR);
            // de tweede richel (oost), met een rotspilaar en het haakpunt
            for (int x = 1740; x <= 1747; x++)
                for (int z = 15; z <= 21; z++) {
                    zet(x, G + 6, z, Material.POLISHED_BLACKSTONE);
                    vul(x, G + 7, z, x, P + 10, z, Material.AIR);
                }
            vul(1740, G + 7, 18, 1740, G + 8, 18, Material.POLISHED_BASALT);
            zet(1740, G + 9, 18, ZeldaHoofdstuk.HAAKPUNT);
            // een spleet in de rots (zuid van de richel): daar ligt iets verstopt
            vul(1746, G + 7, 13, 1747, G + 8, 14, Material.AIR);
            zet(1746, G + 6, 13, Material.BLACKSTONE);
            zet(1747, G + 6, 13, Material.BLACKSTONE);
            // omhoog naar de smokkelgang (zuid)
            for (int n = 1; n <= 4; n++) {
                int z = 21 + n;
                for (int x = 1744; x <= 1746; x++) {
                    vul(x, G + 6, z, x, G + 6 + n - 1, z, Material.BLACKSTONE);
                    zet(x, G + 6 + n, z, Bouwer.trap(Material.POLISHED_BLACKSTONE_STAIRS, BlockFace.SOUTH));
                    vul(x, G + 7 + n, z, x, P + 10, z, Material.AIR);
                }
            }
            for (int x = 1743; x <= 1747; x++)
                for (int z = 26; z <= 29; z++) {
                    zet(x, G + 10, z, Material.POLISHED_BLACKSTONE);
                    vul(x, G + 11, z, x, P + 10, z, Material.AIR);
                }
            // de smokkelgang: een oude gang van de vissers door de rots, omhoog naar de muurvoet
            vul(1743, G + 10, 30, 1747, G + 14, 30, Material.BLACKSTONE);
            for (int z = 31; z <= 37; z++)
                for (int x = 1744; x <= 1746; x++) {
                    zet(x, G + 10, z, Material.POLISHED_BLACKSTONE);
                    vul(x, G + 11, z, x, G + 13, z, Material.AIR);
                    zet(x, G + 14, z, rots(x, G + 14, z));
                }
            for (int n = 1; n <= 4; n++) {                                              // de trap in de gang: G+10 → P
                int z = 37 + n;
                for (int x = 1744; x <= 1746; x++) {
                    vul(x, G + 10, z, x, G + 10 + n - 1, z, Material.BLACKSTONE);
                    zet(x, G + 10 + n, z, Bouwer.trap(Material.POLISHED_BLACKSTONE_STAIRS, BlockFace.SOUTH));
                    vul(x, G + 11 + n, z, x, G + 14 + n, z, Material.AIR);
                }
            }
            for (int z = 42; z <= 44; z++) for (int x = 1744; x <= 1746; x++) {
                zet(x, P, z, Material.POLISHED_BLACKSTONE);
                vul(x, P + 1, z, x, P + 10, z, Material.AIR);
            }
            for (int z = 33; z <= 37; z += 4) zet(1744, G + 13, z, Bouwer.hangend(Material.SOUL_LANTERN));
            zet(1726, G + 9, 19, Material.SOUL_LANTERN);
        }
        verhaal(new Verhaal("klippen", Box.van(1723, G + 6, 17, 1733, G + 10, 20), List.of(
                r("Wiske", "Het pad is ingestort. Te ver om te springen."),
                r("Wiske", "Maar daar, op die pilaar: een haakpunt!")))).als = s -> !s.bezocht(id(), 1);
        Haakpunt pilaar = haakpunt(new Haakpunt("klippen", 1740, G + 9, 18, plek(1742, G + 7, 18, 90f)));
        haak(pilaar, plek(1731, G + 7, 18, -90f));
        Vondst klip = vondst(new Vondst(STUKJE_KLIP, "Hartstukje", null, Material.RED_DYE, plek(1746, G + 7.4, 13, 0f), Box.van(1745, G + 7, 13, 1747, G + 9, 14)));
        klip.verborgen = true;
        Barst gang = barstOpPad(new Barst("smokkelgang", deur(ZeldaHoofdstuk.BARST.createBlockData(), 1744, G + 11, 30, 1746, G + 13, 30),
                Box.van(1742, G + 10, 25, 1748, G + 14, 34)));
        gang.voor = plek(1745, G + 11, 27, 180f);
        verhaal(new Verhaal("smokkelgang", Box.van(1743, G + 10, 26, 1747, G + 13, 29), List.of(
                r("Wiske", "Een oude gang van de vissers. Dichtgemetseld."),
                r("Wiske", "Die stenen zijn gebarsten. Een bom!")))).als = s -> !s.heeft("b:smokkelgang");
    }

    // ── 3. de muurvoet en de muurdeur ───────────────────────────────────────────────────────

    private void muurvoet() {
        if (b.plaats) {
            for (int x = 1742; x <= 1763; x++)
                for (int z = 41; z <= 61; z++) if (kans(x, z, 5) == 0) zet(x, P, z, Material.COBBLED_DEEPSLATE);
            for (int x = 1749; x <= 1755; x++) zet(x, P + 1, 41, Material.POLISHED_BLACKSTONE_WALL);   // een randje langs de smokkelgang
            paalbord(1761, P + 1, 48, BlockFace.WEST, "De weergang");
            zet(1763, P + 3, 49, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.WEST));
        }
        rustvuur(1753, P, 52);
        rust(new Rust("muurvoet", "De muurvoet", plek(1755, P + 1, 50, 90f), Box.van(1748, P, 45, 1763, P + 5, 58), List.of()));
        verhaal(new Verhaal("muurvoet", Box.van(1744, P, 42, 1752, P + 5, 47), List.of(
                r("Wiske", "De muren van Nachtrots. Zo hoog!"),
                r("Wiske", "Daar, onder in de toren: een deur.")))).als = s -> !s.heeft("l:weergang");
        poort(new Poort("muurdeur", deur(Material.IRON_BLOCK.createBlockData(), 1764, P + 1, 50, 1764, P + 3, 52),
                Box.van(1757, P, 46, 1768, P + 5, 56), s -> s.heeft(MUURSLEUTEL)));
        verhaal(new Verhaal("muurdeur-dicht", Box.van(1759, P, 48, 1763, P + 4, 54), List.of(
                r("Wiske", "Op slot. Wie heeft hier een sleutel van?")))).als = s -> !s.heeft(MUURSLEUTEL);
    }

    // ── 4. de burcht: muren, torens, de weergang en de Kerkertoren ──────────────────────────

    /** De buitenmuur (vijf dik), de torens en de gebouwen van de voorburcht: alleen blokken (de onderdelen staan bij hun stuk). */
    private void burcht() {
        // de buitenmuur: x 1768 … 1862, z −56 … 56, vijf dik, tot W; kantelen buiten, een borstwering binnen
        for (int x = 1768; x <= 1862; x++)
            for (int z = -56; z <= 56; z++) {
                int dw = Math.min(Math.min(x - 1768, 1862 - x), Math.min(z + 56, 56 - z));
                if (dw > 4) continue;
                for (int y = P + 1; y <= W; y++) zet(x, y, z, zwart(x, y, z));
                boolean buiten = dw == 0, binnen = dw == 4;
                if (buiten) {
                    boolean hoog = (x + z) % 2 == 0;
                    zet(x, W + 1, z, hoog ? zwart(x, W + 1, z) : Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    if (hoog) zet(x, W + 2, z, zwart(x, W + 2, z));
                } else if (binnen) zet(x, W + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                if (x == 1768 && z % 12 == 0 && Math.abs(z) < 50) zet(x - 1, P + 7, z, Material.SOUL_LANTERN);
            }
        // de torens op de hoeken (SW is de muurtoren met de trap, zie weergang), en de Kerkertoren
        toren(1760, -60, 1774, -46, W + 8);                       // NW
        toren(1856, -60, 1866, -50, W + 8);                       // NE
        toren(1856, 50, 1866, 60, W + 8);                         // SE
        // het poortgebouw van de hoofdpoort (west, z −5 … 5): twee torentjes
        toren(1764, -8, 1768, -4, W + 4);
        toren(1764, 4, 1768, 8, W + 4);
        // de voorburcht: kazernes langs de muren (dicht, met daken), een stal, een smidse van Morgal
        gebouw(1776, -51, 1852, -42, 6);                          // kazerne noord
        gebouw(1776, 42, 1852, 51, 6);                            // kazerne zuid
        gebouw(1776, 6, 1796, 38, 5);                             // stallen (west, zuid van de laan)
        gebouw(1776, -38, 1796, -6, 5);                           // opslag (west, noord van de laan)
        gebouw(1844, -40, 1852, -8, 7);                           // oost, noord van de torengang
        gebouw(1844, 8, 1852, 40, 7);                             // oost, zuid van de torengang
        gebouw(1814, -40, 1840, -26, 6);                          // naast het wachthuis
        gebouw(1800, 24, 1840, 38, 6);                            // zuid van de binnenplaats
        // de binnenmuur om de binnenplaats (x 1799 … 1841, z −21 … 21), met torentjes op de hoeken
        for (int x = 1799; x <= 1841; x++)
            for (int z = -21; z <= 21; z++) {
                boolean rand = x == 1799 || x == 1841 || z == -21 || z == 21;
                if (!rand) continue;
                for (int y = P + 1; y <= P + 8; y++) zet(x, y, z, zwart(x, y, z));
                if ((x + z) % 2 == 0) zet(x, P + 9, z, zwart(x, P + 9, z));
            }
    }

    /** Een vierkante toren met een dak van leien en verlichte ramen (paars glas, een zielslamp erachter). */
    private void toren(int x1, int z1, int x2, int z2, int hoog) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean rand = x == x1 || x == x2 || z == z1 || z == z2;
                for (int y = P + 1; y <= hoog; y++) {
                    boolean raam = rand && (y - P) % 6 == 4 && ((x == (x1 + x2) / 2) || (z == (z1 + z2) / 2));
                    zet(x, y, z, rand ? (raam ? Material.PURPLE_STAINED_GLASS : zwart(x, y, z)) : y == hoog ? Material.POLISHED_BLACKSTONE : Material.AIR);
                }
                if (rand && (x + z) % 2 == 0) zet(x, hoog + 1, z, zwart(x, hoog + 1, z));
            }
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        for (int y = P + 4; y < hoog; y += 6) zet(mx, y, mz, Material.SOUL_LANTERN);
        int r = Math.max(x2 - x1, z2 - z1) / 2;
        for (int t = 0; t <= r; t++)                                              // een spits dak
            for (int x = mx - (r - t); x <= mx + (r - t); x++)
                for (int z = mz - (r - t); z <= mz + (r - t); z++) zet(x, hoog + 2 + t, z, Material.DEEPSLATE_TILES);
        zet(mx, hoog + 3 + r, mz, Material.CRYING_OBSIDIAN);
    }

    /** Een dicht gebouw in de voorburcht (kazerne, stal): muren, ramen zonder licht, een zadeldak. Je komt er niet in. */
    private void gebouw(int x1, int z1, int x2, int z2, int hoog) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean rand = x == x1 || x == x2 || z == z1 || z == z2;
                for (int y = P + 1; y <= P + hoog; y++) {
                    boolean raam = rand && y == P + 3 && (x + z) % 4 == 0;
                    zet(x, y, z, rand ? (raam ? Material.BLACK_STAINED_GLASS_PANE : (y == P + 1 ? Material.COBBLED_DEEPSLATE : Material.DARK_OAK_PLANKS)) : Material.AIR);
                }
            }
        boolean langsX = x2 - x1 >= z2 - z1;
        int breed = langsX ? z2 - z1 : x2 - x1;
        for (int t = 0; t <= (breed + 1) / 2; t++)
            for (int x = x1 - 1; x <= x2 + 1; x++)
                for (int z = z1 - 1; z <= z2 + 1; z++) {
                    int a = langsX ? Math.min(z - (z1 - 1), (z2 + 1) - z) : Math.min(x - (x1 - 1), (x2 + 1) - x);
                    if (a == t) zet(x, P + hoog + 1 + t, z, Material.DEEPSLATE_TILES);
                }
    }

    /** De SW-toren (de muurtoren, met de muurdeur en een wenteltrap naar de weergang) en de Kerkertoren (omlaag naar de kerkers). */
    private void weergang() {
        if (b.plaats) {
            // de muurtoren (SW): x 1764 … 1774, z 46 … 56
            for (int x = 1764; x <= 1774; x++)
                for (int z = 46; z <= 56; z++) {
                    boolean rand = x == 1764 || x == 1774 || z == 46 || z == 56;
                    for (int y = P + 1; y <= W + 6; y++) zet(x, y, z, rand ? zwart(x, y, z) : Material.AIR);
                    zet(x, P, z, vloer(x, z));
                    if (!rand) zet(x, W, z, vloer(x, z));
                    zet(x, W + 7, z, Material.POLISHED_BLACKSTONE);
                    if (rand && (x + z) % 2 == 0) zet(x, W + 8, z, zwart(x, W + 8, z));
                }
            vul(1769, W, 50, 1771, W, 52, Material.AIR);                               // het trapgat
            wenteltrap(1770, 51, P, W - P, 0, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BASALT);
            for (int y = W + 1; y <= W + 3; y++) for (int x = 1769; x <= 1771; x++) zet(x, y, 46, Material.AIR);   // de deur naar de weergang (noord)
            zet(1766, W + 4, 49, Bouwer.hangend(Material.SOUL_LANTERN));
            zet(1766, P + 4, 54, Bouwer.hangend(Material.SOUL_LANTERN));
            // de weergang zelf: de muur x 1768 … 1772 (zie burcht), met balkonnetjes aan de binnenkant
            for (int bz : BALKONS) {
                zet(1772, W + 1, bz, Material.AIR);                                       // de borstwering open
                for (int x = 1773; x <= 1773; x++) {
                    zet(x, W, bz, Material.POLISHED_BLACKSTONE_BRICKS);
                    zet(x, W - 1, bz, Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST, true));
                    vul(x, W + 1, bz, x, W + 3, bz, Material.AIR);
                }
                for (int x = 1772; x <= 1774; x++) {
                    for (int y = W + 1; y <= W + 2; y++) {
                        zet(x, y, bz - 1, zwart(x, y, bz - 1));
                        zet(x, y, bz + 1, zwart(x, y, bz + 1));
                    }
                    zet(x, W, bz - 1, zwart(x, W, bz - 1));
                    zet(x, W, bz + 1, zwart(x, W, bz + 1));
                }
                zet(1774, W, bz, Material.POLISHED_BLACKSTONE_BRICKS);
                zet(1774, W + 1, bz, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                zet(1774, W - 1, bz, Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST, true));
            }
            // de Kerkertoren: x 1765 … 1775, z −28 … −18; een wenteltrap van de weergang naar de kerkers (K)
            for (int x = 1765; x <= 1775; x++)
                for (int z = -28; z <= -18; z++) {
                    boolean rand = x == 1765 || x == 1775 || z == -28 || z == -18;
                    for (int y = P + 1; y <= W + 7; y++) zet(x, y, z, rand ? zwart(x, y, z) : Material.AIR);
                    if (!rand) {
                        zet(x, W, z, vloer(x, z));
                        zet(x, P, z, vloer(x, z));
                    }
                    zet(x, W + 8, z, Material.POLISHED_BLACKSTONE);
                    if (rand && (x + z) % 2 == 0) zet(x, W + 9, z, zwart(x, W + 9, z));
                }
            for (int y = W + 1; y <= W + 3; y++) for (int x = 1769; x <= 1771; x++) zet(x, y, -18, Material.AIR);   // de deur van de weergang (zuid)
            // de schacht door de rots naar beneden (5 bij 5), met wanden van kerkersteen
            for (int x = 1767; x <= 1773; x++)
                for (int z = -26; z <= -20; z++) {
                    boolean rand = x == 1767 || x == 1773 || z == -26 || z == -20;
                    for (int y = K + 9; y <= P; y++) zet(x, y, z, rand ? steen(x, y, z) : Material.AIR);
                }
            vul(1768, W, -25, 1772, W, -21, Material.AIR);
            vul(1768, P, -25, 1772, P, -21, Material.AIR);
            for (int x = 1768; x <= 1772; x++) for (int z = -25; z <= -21; z++) if (Math.abs(x - 1770) == 2 || Math.abs(z + 23) == 2) {
                zet(x, W, z, vloer(x, z));                                                // een rand om het trapgat (boven)
                zet(x, P, z, vloer(x, z));
            }
            wenteltrap(1770, -23, K, W - K, 6, Material.DEEPSLATE_BRICK_STAIRS, Material.DEEPSLATE_BRICKS, Material.POLISHED_DEEPSLATE);
            for (int y = K + 6; y < W; y += 8) zet(1767, y, -23, Material.SOUL_LANTERN);
            zet(1770, W + 6, -23, Bouwer.hangend(Material.SOUL_LANTERN));
            bord(1771, W + 3, -19, BlockFace.SOUTH, "De kerkers");
        }
        Verhaal uitleg = verhaal(new Verhaal("uitleg-sluipen", Box.van(1765, W, 47, 1773, W + 4, 55), List.of(
                r("Wiske", "Wachters met lantaarns! Kom niet in hun licht."),
                r("Wiske", "Wacht in een balkonnetje tot ze langs zijn."),
                r("Wiske", "Bukken helpt: dan zien ze je pas van dichtbij."))));
        uitleg.uitleg = true;
        uitleg.als = s -> !s.heeft("l:weergang");
        Location terug = rust("muurvoet") != null ? rust("muurvoet").plek : plek(1755, P + 1, 50, 90f);
        Sluip sl = sluip(new Sluip("weergang", "De weergang", Box.van(1765, W, -19, 1775, W + 4, 46), Box.van(1769, W, -20, 1771, W + 3, -18),
                deur(tralies(false), 1769, W + 1, -18, 1771, W + 3, -18), terug, RITME));
        sl.actief = s -> !s.heeft("k:binnenplaats");
        for (int i = 0; i < BALKONS.length; i++) patrouille(sl, "Nachtwacht", true, 1770.5, W + 1, BALKONS[i] - 6, BALKONS[i] + 5, FASES[i]);
        sl.punt(plek(1766, W + 1, 48, 0f), true).punt(plek(1771, W + 1, 48, 180f), false);
        for (int bz : BALKONS) sl.punt(plek(1771, W + 1, bz, 180f), false).punt(plek(1773, W + 1, bz, 90f), true).punt(plek(1771, W + 1, bz, 180f), false);
        sl.punt(plek(1771, W + 1, -19, 180f), false);
        sl.gezien.addAll(List.of(r("Wiske", "Gezien! Terug naar het zielvuur."),
                r("Wiske", "Wacht in een balkon tot het licht voorbij is."),
                r("Wiske", "Loop achter de wachter aan. Hij kijkt maar één kant op.")));
        verhaal(new Verhaal("kerkertoren", Box.van(1766, W, -27, 1774, W + 4, -19), List.of(
                r("Wiske", "Gelukt! Een trap, diep naar beneden."))));
    }

    /** De balkonnetjes langs de binnenkant van de weergang (z): hier ziet een wachter je niet. */
    static final int[] BALKONS = {37, 25, 13, 1};

    /**
     * Een wachter die heen en weer loopt over een recht stuk (langs z als {@code langsZ}, anders langs x), van {@code a} naar
     * {@code b} en terug, met een korte draai (via de kant van de zee of de gang, niet naar de schuilplekken). Op de weergang
     * liggen de balkons oost (+x), in de cellengang de nissen noord (−z).
     */
    private void patrouille(Sluip sl, String naam, boolean langsZ, double vast, double y, double a, double bb, double fase) {
        Sluip.Wachter wa = sl.wachter(naam, standen(langsZ, vast, y, a, bb, fase));
        wa.bereik = BEREIK;
        wa.hoek = HOEK;
    }

    /** Hoe ver een wachter ziet (blokken) en hoeveel graden opzij: voor de weergang en de cellengang hetzelfde. */
    static final double BEREIK = 6, HOEK = 32;
    /** Het ritme van alle wachters (ms), en hoe de fases van buur tot buur verschuiven (een kwart terug). */
    static final long RITME = 14_000L;
    static final double[] FASES = {0, 0.75, 0.5, 0.25};

    /**
     * De standen van een wachter die heen en weer loopt (zie {@link #patrouille}): {fractie, x, y, z, yaw}, oplopend. Hij loopt
     * 40 % van het ritme heen, draait 10 % (via de zee of de gang), loopt 40 % terug en draait weer.
     */
    static double[][] standen(boolean langsZ, double vast, double y, double a, double bb, double fase) {
        double[][] st;
        if (langsZ) {                                                           // de weergang: a = noord (kleine z), b = zuid
            double n = a + 0.5, z = bb + 0.5;
            st = new double[][]{{0.00, vast, y, n, 0}, {0.40, vast, y, z, 0}, {0.45, vast, y, z, 90}, {0.50, vast, y, z, 180},
                    {0.90, vast, y, n, 180}, {0.95, vast, y, n, 90}};
        } else {                                                                // de cellengang: a = west (kleine x), b = oost
            double wx = a + 0.5, ox = bb + 0.5;
            st = new double[][]{{0.00, wx, y, vast, -90}, {0.40, ox, y, vast, -90}, {0.45, ox, y, vast, 0}, {0.50, ox, y, vast, 90},
                    {0.90, wx, y, vast, 90}, {0.95, wx, y, vast, 0}};
        }
        for (double[] s : st) s[0] = (s[0] + fase) % 1.0;
        java.util.Arrays.sort(st, java.util.Comparator.comparingDouble(s -> s[0]));
        return st;
    }

    // ── 5. de kerkers: de hal (hub binnen), de cellengang, de wachtkamer, het Vissersgat ─────

    private void kerkerhal() {
        if (b.plaats) {
            gang(1760, -30, 1776, -12, K, 8);
            for (int[] c : new int[][]{{1763, -27}, {1773, -27}, {1763, -16}, {1773, -16}}) {
                vul(c[0], K + 1, c[1], c[0], K + 7, c[1], Material.POLISHED_DEEPSLATE);
                zet(c[0], K + 8, c[1], Material.CHISELED_DEEPSLATE);
            }
            for (int[] l : new int[][]{{1766, -20}, {1774, -14}, {1762, -24}}) {
                vul(l[0], K + 6, l[1], l[0], K + 8, l[1], Material.IRON_CHAIN);
                zet(l[0], K + 5, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
            }
            zet(1776, K + 3, -14, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.WEST));
            zet(1760, K + 3, -14, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.EAST));
            zet(1762, K + 1, -29, Material.BARREL);
            zet(1763, K + 1, -29, Material.BARREL);
            bord(1760, K + 3, -18, BlockFace.EAST, "De cellen");
            bord(1766, K + 3, -12, BlockFace.NORTH, "De kapel");
            bord(1776, K + 3, -25, BlockFace.WEST, "De wachtersgang");
        }
        rustvuur(1764, K, -14);
        rust(new Rust("kerkerhal", "De kerkers", plek(1766, K + 1, -16, -90f), Box.van(1761, K, -19, 1771, K + 5, -12), List.of()));
        bron("bron-kerkers", plek(1773, K + 1, -19, 0f));
        verhaal(new Verhaal("kerkers", Box.van(1761, K, -29, 1775, K + 6, -13), List.of(
                r("Wiske", "De kerkers van Nachtrots. Hoor je dat? Iemand snurkt."),
                r("Wiske", "West: de cellen. Zuid: een gang. Oost: nevel."))));
    }

    /**
     * De cellengang (x 1709 … 1758, z −21 … −19): drie wachters met een lantaarn op een vast ritme, nissen in de noordwand om
     * je te verstoppen, cellen aan beide kanten met gevangenen van alle eilanden, de wachtkamer met de slapende
     * kerkermeester (de sleutelbos) en aan het eind Wolfs cel (met het raampje naar de kreek).
     */
    private void cellengang() {
        if (b.plaats) {
            gang(1709, -21, 1758, -19, K, 4);
            // de nissen in de noordwand (je verstoppen)
            for (int n : NISSEN) {
                for (int z = -23; z <= -22; z++) {
                    zet(n, K, z, vloer(n, z));
                    vul(n, K + 1, z, n, K + 3, z, Material.AIR);
                }
                holte(n, K + 1, -23, n, K + 3, -22);
                zet(n, K + 4, -22, steen(n, K + 4, -22));
                zet(n, K + 4, -23, Material.IRON_CHAIN);
            }
            // de cellen (noord: deur in z −22; zuid: deur in z −18), elk met tralies als deur
            for (int[] c : NOORDCELLEN) cel(c[0], -26, c[1], -23, c[2], -22);
            for (int[] c : ZUIDCELLEN) cel(c[0], c[3], c[1], -14, c[2], -18);
            // Wolfs raampje naar de kreek (in de rotswand, z −12)
            zet(1712, K + 2, -12, Material.AIR);
            zet(1712, K + 2, -13, Material.AIR);
            // de wachtkamer: x 1729 … 1741, z −17 … −8
            gang(1729, -17, 1741, -8, K, 5);
            zet(1739, K + 1, -11, Material.HAY_BLOCK);                                  // zijn kussen (hij slaapt op de grond)
            zet(1735, K + 1, -10, Material.SPRUCE_FENCE);                               // de tafel met de sleutelbos
            zet(1735, K + 2, -10, Material.SPRUCE_PRESSURE_PLATE);
            zet(1734, K + 1, -10, Bouwer.trap(Material.SPRUCE_STAIRS, BlockFace.EAST));
            zet(1730, K + 1, -16, Material.BARREL);
            zet(1730, K + 1, -15, Material.BARREL);
            zet(1730, K + 2, -16, Material.BARREL);
            zet(1741, K + 1, -16, Material.GRINDSTONE);
            for (int x = 1731; x <= 1733; x++) zet(x, K + 3, -8, Material.IRON_CHAIN);
            zet(1735, K + 5, -12, Bouwer.hangend(Material.LANTERN));
            zet(1740, K + 3, -8, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.NORTH));
            // licht in de gang: zielslampen hoog aan de wand (zwak: de lantaarns van de wachters zijn het licht)
            for (int x = 1712; x <= 1756; x += 11) zet(x, K + 4, -21, Bouwer.hangend(Material.SOUL_LANTERN));
            // kleine verrassingen in de lege cellen
            zet(1712, K + 1, -25, Material.BLACK_CARPET);                               // de hoed van Zoutbaard
            zet(1712, K + 2, -25, Material.BLACK_BANNER);
            zet(1724, K + 1, -15, Material.LECTERN);                                    // de tekening
            for (int y = K + 1; y <= K + 3; y++) zet(1757, y, -17, Material.CHISELED_DEEPSLATE);   // de streepjes
            for (int x = 1728; x <= 1731; x++) zet(x, K + 1, -26, Material.MOSS_CARPET);       // het maanmos groeit in een lege cel
        }
        // de kerkermeester slaapt (en snurkt): op zijn tafel de sleutelbos
        Persoon meester = persoon(new Persoon("Kerkermeester", plek(1738, K + 1, -11, 90f), null, List.of(r("Kerkermeester", "Zzz… nog vijf minuutjes… zzz…"))));
        meester.soort = EntityType.PILLAGER;
        meester.uiterlijk = e -> e.setPose(org.bukkit.entity.Pose.SLEEPING, true);
        Verhaal uitleg = verhaal(new Verhaal("uitleg-cellen", Box.van(1760, K, -24, 1765, K + 4, -18), List.of(
                r("Wiske", "Cipiers met lantaarns. Zelfde truc als op de muur!"),
                r("Wiske", "Verstop je in de nissen."))));
        uitleg.uitleg = true;
        uitleg.als = s -> !s.heeft("l:kerkerwacht") && !s.heeft("k:binnenplaats");
        Sluip sl = sluip(new Sluip("kerkerwacht", "De cellen", Box.van(1709, K, -24, 1758, K + 4, -18), Box.van(1709, K, -21, 1712, K + 4, -19),
                deur(tralies(true), 1759, K + 1, -21, 1759, K + 3, -19), rust("kerkerhal").plek, RITME));
        sl.actief = s -> !s.heeft("k:binnenplaats");
        for (int i = 0; i < NISSEN.length; i++) patrouille(sl, "Cipier", false, -19.5, K + 1, NISSEN[i] - 6, NISSEN[i] + 5, FASES[i]);
        sl.punt(plek(1761, K + 1, -24, 90f), true).punt(plek(1761, K + 1, -21, 90f), false);
        for (int n : NISSEN) sl.punt(plek(n, K + 1, -21, 90f), false).punt(plek(n, K + 1, -23, 0f), true).punt(plek(n, K + 1, -21, 90f), false);
        sl.punt(plek(1711, K + 1, -21, 90f), false);
        sl.gezien.addAll(List.of(r("Wiske", "Gezien! Terug naar de hal."),
                r("Wiske", "Wacht in een nis tot de cipier langs is."),
                r("Wiske", "Loop achter hem aan. Hij kijkt maar één kant op.")));
        verhaal(new Verhaal("wachtkamer", Box.van(1733, K, -17, 1737, K + 4, -14), List.of(
                r("Wiske", "De kerkermeester. Hij slaapt!"),
                r("Wiske", "En daar, op zijn tafel: sleutels…")))).als = s -> !s.heeft(SLEUTELBOS);
        Vondst bos = vondst(new Vondst(SLEUTELBOS, "Sleutelbos", "de sleutels van de cellen", Material.TRIPWIRE_HOOK, plek(1735, K + 2.4, -10, 0f),
                Box.van(1734, K + 1, -11, 1736, K + 3, -9)));
        bos.daarna = (spel, p, s) -> {
            if (s.missie(GEVANGENEN) == 1) spel.missie(p, GEVANGENEN, 2);
            spel.vertel(p, r("Wiske", "Nu de cellen. Wolf zit helemaal aan het eind."));
        };
        // Wolfs cel (zuid, aan het eind): open met de sleutelbos
        wolfCel = poort(new Poort("cel-wolf", deur(tralies(false), 1712, K + 1, -18, 1713, K + 3, -18), Box.van(1710, K, -21, 1715, K + 4, -18), s -> s.heeft(SLEUTELBOS)));
        praten((Persoon) onderdeel("Wolf"));
        // de andere gevangenen (een zijmissie: «De gevangenen»)
        missie(GEVANGENEN, "De gevangenen", "Zoek de sleutels van de kerkermeester.", "Open de cellen: wie zit er nog vast?",
                "Vertel Wolf dat iedereen vrij is, in de kreek.");
        stukje(STUKJE_GEVANGENEN);
        gevangene("wolkje", "Wolkje", EntityType.SHEEP, false, 1718, -16, 1718, 1719, -18, List.of(r("Wolkje", "Bèèh…"), r("Wiske", "Een schaap in een cel! Wat zielig.")),
                List.of(r("Wolkje", "Bèèèh!"), r("Wiske", "Een schaap? Van Gerrit, denk ik!"), r("Wiske", "Hup, naar huis. Brecht neemt je mee.")));
        gevangene("bas", "Bas", EntityType.VILLAGER, true, 1745, -16, 1745, 1746, -18, List.of(r("Bas", "Psst! Ik ben Bas, uit Zoutkom."), r("Bas", "Ik wilde Morgals schip zien. Dom, hè?")),
                List.of(r("Bas", "Een sleutel! Eindelijk!"), r("Bas", "Ik ga naar huis, naar Hendrik.")));
        gevangene("kiek", "Kiek", EntityType.GOAT, true, 1754, -23, 1754, 1755, -22, List.of(r("Kiek", "Mèh…"), r("Wiske", "Een geitje. Dat hoort hier niet!")),
                List.of(r("Kiek", "Mèèh!"), r("Wiske", "Een geitje van Saar en Sven, vast."), r("Wiske", "Naar huis, naar IJshoeve!")));
        gevangene("joppe", "Joppe", EntityType.VILLAGER, true, 1745, -23, 1745, 1746, -22, List.of(r("Joppe", "Ik zocht gloedijzer op het strand…"), r("Joppe", "…en toen pakten ze me. Help je me?")),
                List.of(r("Joppe", "Vrij! Dank je wel!"), r("Joppe", "Ik ben Joppe, uit Sintelgat. Naar mama Dina!")));
        wolfKreek();
        // het maanmos voor Hanne (een boodschap over zee): in een lege cel, achter tralies
        poorten.add(new Poort("cel-mos", deur(tralies(false), 1729, K + 1, -22, 1730, K + 3, -22), Box.van(1727, K, -22, 1732, K + 4, -19), s -> s.heeft(SLEUTELBOS)));
        Vondst mos = vondst(new Vondst(MAANMOS, "Maanmos", "voor Hanne, op het Lindewoud", Material.GLOW_LICHEN, plek(1729, K + 1.4, -25, 0f), Box.van(1728, K + 1, -26, 1731, K + 3, -24)));
        mos.vereist = "g:hanne-mos";
        mos.daarna = (spel, p, s) -> {
            if (s.missie(ZeldaHoofdstuk1.MAANMOS) == 1) spel.missie(p, ZeldaHoofdstuk1.MAANMOS, 2);
            spel.vertel(p, r("Wiske", "Het glimt! Hanne zal blij zijn."));
        };
        // kleine verrassingen
        verhaal(new Verhaal("cel-hoed", Box.van(1710, K, -21, 1714, K + 4, -20), List.of(r("Wiske", "Een zwarte hoed. Zoutbaard zat hier ooit!"))));
        verhaal(new Verhaal("cel-tekening", Box.van(1722, K, -21, 1726, K + 4, -19), List.of(r("Wiske", "Een tekening… van jou! En mij!"), r("Wiske", "Morgal weet dus wie we zijn."))));
        verhaal(new Verhaal("cel-streepjes", Box.van(1751, K, -21, 1756, K + 4, -19), List.of(r("Wiske", "Streepjes op de muur. Iemand telde de dagen."))));
    }

    /** De nissen in de noordwand van de cellengang (x): daar ziet een cipier je niet. */
    static final int[] NISSEN = {1750, 1738, 1726};
    /** De cellen aan de noordkant: {x1, x2, deur-x}, en aan de zuidkant {x1, x2, deur-x, z1}. */
    private static final int[][] NOORDCELLEN = {{1752, 1757, 1754}, {1740, 1748, 1745}, {1728, 1736, 1729}, {1716, 1724, 1721}, {1710, 1714, 1712}};
    private static final int[][] ZUIDCELLEN = {{1711, 1714, 1712, -17}, {1716, 1720, 1718, -17}, {1722, 1726, 1724, -17}, {1744, 1748, 1745, -17}, {1750, 1757, 1752, -17}};

    /** Een cel: een kamertje van kerkersteen met tralies als deur (twee breed op {@code dx}, in de gangwand op z {@code dz}). */
    private void cel(int x1, int z1, int x2, int z2, int dx, int dz) {
        gang(x1, Math.min(z1, z2), x2, Math.max(z1, z2), K, 3);
        for (int y = K + 1; y <= K + 3; y++)
            for (int x = dx; x <= dx + 1; x++) zet(x, y, dz, tralies(false));
        zet(x1, K + 1, Math.min(z1, z2), Material.HAY_BLOCK);
        if (x2 - x1 >= 3) zet(x2, K + 1, Math.min(z1, z2), Material.CAULDRON);
    }

    /**
     * Een gevangene van een ander eiland in een cel (deur op {@code dx}, {@code dx2} in de gangwand op z {@code dz}): door de
     * tralies praat hij (de zijmissie begint), met de sleutelbos gaat de cel open, en praten = vrij: hij gaat naar huis
     * (daar staat hij voortaan, en wie op hem wachtte heeft iets te zeggen).
     */
    private void gevangene(String id, String naam, EntityType soort, boolean kind, int x, int z, int dx, int dx2, int dz, List<Regel> eerst, List<Regel> vrij) {
        String vlag = "vrij:" + id;
        Poort cel = poort(new Poort("cel-" + id, deur(tralies(false), dx, K + 1, dz, dx2, K + 3, dz), Box.van(dx - 2, K, dz == -18 ? -21 : -22, dx2 + 2, K + 4, dz == -18 ? -18 : -19), s -> s.heeft(SLEUTELBOS)));
        cellen.put(id, cel);
        Persoon pe = persoon(new Persoon(naam, plek(x, K + 1, z, dz == -18 ? 180f : 0f), null, List.of(eerst.get(0))));
        pe.soort = soort;
        pe.kind = kind;
        if (soort == EntityType.VILLAGER) pe.streek = id.equals("bas") ? Villager.Type.PLAINS : Villager.Type.SAVANNA;
        pe.zichtbaar = s -> !s.heeft(vlag);
        pe.gesprek("vrij-" + id, s -> s.heeft(SLEUTELBOS) && !s.heeft(vlag) && cel.deur.isOpen, vrij, (spel, p, s) -> {
                    if (s.zet(vlag)) spel.voortgang.vuil();
                    p.getWorld().spawnParticle(Particle.CLOUD, pe.plek.clone().add(0, 0.6, 0), 15, 0.3, 0.4, 0.3, 0.02);
                    int los = 0;
                    for (String[] gv : GEVANGEN) if (s.heeft("vrij:" + gv[0])) los++;
                    if (los == GEVANGEN.length) {
                        spel.missie(p, GEVANGENEN, 3);
                        spel.vertel(p, r("Wiske", "Iedereen is vrij! Vertel het Wolf."));
                    } else if (s.missie(GEVANGENEN) < 2) spel.missie(p, GEVANGENEN, 2);
                })
                .eenmalig(id + "-1", s -> !s.heeft(vlag), eerst, (spel, p, s) -> {
                    if (s.missie(GEVANGENEN) == 0) spel.missie(p, GEVANGENEN, s.heeft(SLEUTELBOS) ? 2 : 1);
                });
        praten(pe);
    }

    /** Wolf in de kreek (na zijn cel): het wachtwoord van de binnenpoort, het harnas, en het hartstukje voor de gevangenen. */
    private void wolfKreek() {
        Location wolfKreek = plek(1710, G + 1, 5, 180f);
        Persoon wolf = persoon(new Persoon("Wolf", wolfKreek, beroep("armorer"), List.of(r("Wolf", "Ik hou de kreek in de gaten."))));
        wolf.streek = Villager.Type.TAIGA;
        wolf.zichtbaar = s -> s.heeft(WOLF_VRIJ);
        wolf.eenmalig("wolf-kreek", s -> s.heeft(WOLF_VRIJ), List.of(
                        r("Wolf", "Vrij! En Tijmen verbond mijn arm."),
                        r("Wolf", "Zelda zit boven in de toren. Morgal houdt haar vast."),
                        r("Wolf", "Ons harnas van licht ligt in de wapenkamer."),
                        r("Wolf", "En de binnenpoort? Het wachtwoord is «Maanloos».")), (spel, p, s) -> {
                    if (s.zet(WACHTWOORD)) spel.voortgang.vuil();
                })
                .gesprek("wolf-gevangenen", s -> s.missie(GEVANGENEN) == 3, List.of(
                        r("Wolf", "Iedereen is vrij! Dank je."),
                        r("Wolf", "Dit hield ik voor Zelda. Neem jij het maar.")), (spel, p, s) -> {
                    spel.missie(p, GEVANGENEN, ZeldaVoortgang.KLAAR);
                    spel.beloon(p, STUKJE_GEVANGENEN, "Hartstukje", null);
                })
                .eenmalig("wolf-harnas", s -> s.heeft("harnas4"), List.of(
                        r("Wolf", "Het lichtharnas! Zo glansden wij vroeger allemaal.")), null)
                .eenmalig("wolf-binnen", s -> s.heeft("k:binnenplaats"), List.of(
                        r("Wolf", "De binnenplaats is van ons! De wachters zijn gevlucht."),
                        r("Wolf", "Nu de toren nog.")), null)
                .eenmalig("wolf-zelda", s -> s.heeft(ZeldaEiland5b.VRIJ_ZELDA), List.of(
                        r("Wolf", "Zelda is vrij! Ik wist het wel."),
                        r("Wolf", "Een wachter van Zelda zonder werk. Heerlijk.")), null)
                .gesprek(null, s -> s.heeft(WACHTWOORD) && !s.heeft("harnas4"), List.of(r("Wolf", "Haal eerst het harnas. Dan de binnenpoort.")), null);
        praten(wolf);
    }

    /** Het Vissersgat: een oude gang van de cellengang naar de kreek; het hek gaat open voor wie in de kerkers was (een korte weg terug). */
    private void vissersgat() {
        if (b.plaats) {
            gang(1704, -21, 1707, -19, K, 4);
            for (int z = -18; z <= -12; z++) {
                int vl = z >= -14 ? G : z == -15 ? G + 1 : K;
                for (int x = 1704; x <= 1706; x++) {
                    vul(x, G - 1, z, x, vl - 1, z, Material.BLACKSTONE);
                    zet(x, vl, z, vloer(x, z));
                    vul(x, vl + 1, z, x, vl + 3, z, Material.AIR);
                    zet(x, vl + 4, z, steen(x, vl + 4, z));
                }
                for (int y = G; y <= K + 4; y++) {
                    if (!uitgehakt(1703, y, z)) zet(1703, y, z, steen(1703, y, z));
                    if (!uitgehakt(1707, y, z)) zet(1707, y, z, steen(1707, y, z));
                }
            }
            holte(1704, G + 1, -18, 1706, K + 3, -12);
            zet(1705, K + 3, -17, Bouwer.hangend(Material.SOUL_LANTERN));
        }
        poort(new Poort("vissersgat", deur(tralies(false), 1704, G + 1, -12, 1706, G + 3, -12), Box.van(1702, G, -20, 1708, K + 4, -8),
                s -> s.bezocht(id(), 2)));
        verhaal(new Verhaal("vissersgat-dicht", Box.van(1703, G, -11, 1708, G + 4, -9), List.of(
                r("Wiske", "Een oud hek. Het gaat alleen van binnen open.")))).als = s -> !s.bezocht(id(), 2);
    }

    // ── 6. de kapel van Zelda's wachters: een bom, een haak over de put, drie vuren, de lichtpijlen ─

    private void kapel() {
        VUREN = new int[][]{{1765, K + 5, 27}, {1785, K + 5, 27}, {1775, K + 7, 30}};
        if (b.plaats) {
            gang(1767, -11, 1769, 4, K, 4);                                           // de zuidgang
            zet(1768, K + 4, -4, Bouwer.hangend(Material.SOUL_LANTERN));
            // de kapel: x 1762 … 1788, z 6 … 30, tot K+11; wit steen van de wachters, zwart van de nevel
            for (int x = 1761; x <= 1789; x++)
                for (int z = 5; z <= 31; z++) {
                    boolean rand = x == 1761 || x == 1789 || z == 5 || z == 31;
                    for (int y = G - 4; y <= K + 11; y++) {                      // het dak (K+11) ligt onder het plateau (P)
                        if (rand || y == K + 11 || y == G - 4) {
                            if (!uitgehakt(x, y, z)) zet(x, y, z, licht(x, y, z));
                            continue;
                        }
                        boolean put = z >= 11 && z <= 21;
                        if (put) {
                            zet(x, y, z, y <= G - 3 ? Material.SMOOTH_QUARTZ : y <= G - 1 ? Material.WATER : Material.AIR);
                            continue;
                        }
                        zet(x, y, z, y < K ? licht(x, y, z) : y == K ? (z % 4 == 0 || x % 4 == 0 ? Material.CHISELED_QUARTZ_BLOCK : Material.SMOOTH_QUARTZ) : Material.AIR);
                    }
                }
            holte(1762, G - 3, 6, 1788, K + 10, 30);
            for (int y = G - 2; y <= K; y++) zet(1775, y, 11, Bouwer.gericht(Material.LADDER, BlockFace.SOUTH));   // uit de put terug
            for (int x : new int[]{1764, 1770, 1780, 1786}) {                         // zuilen langs de randen van de put (niet in de lijn van de haak)
                vul(x, K + 1, 10, x, K + 10, 10, Material.QUARTZ_PILLAR);
                vul(x, K + 1, 22, x, K + 10, 22, Material.QUARTZ_PILLAR);
            }
            for (int x = 1762; x <= 1788; x++) {                                        // hekjes: niet per ongeluk de put in (bij de ladder open)
                if (x == 1764 || x == 1770 || x == 1780 || x == 1786) continue;
                if (x != 1775) zet(x, K + 1, 10, Material.DIORITE_WALL);
                if (x < 1774 || x > 1776) zet(x, K + 1, 22, Material.DIORITE_WALL);
            }
            // het haakpunt: op een zuil aan de voorkant van het altaar
            vul(1775, K + 1, 22, 1775, K + 3, 22, Material.QUARTZ_PILLAR);
            zet(1775, K + 4, 22, ZeldaHoofdstuk.HAAKPUNT);
            // het altaar en de drie vuurschalen (hoog op zuilen)
            zet(1775, K + 1, 28, Material.CHISELED_QUARTZ_BLOCK);
            for (int[] v : VUREN) {
                vul(v[0], K + 1, v[2], v[0], v[1] - 1, v[2], Material.QUARTZ_PILLAR);
                zet(v[0], v[1] - 1, v[2], Material.CHISELED_QUARTZ_BLOCK);
            }
            // zwarte vlekken van de nevel op de muren; banieren van de wachters (wit met goud)
            for (int x = 1762; x <= 1788; x += 3) zet(x, K + 6 + (x % 2), 31, Material.BLACK_CONCRETE);
            for (int z = 24; z <= 28; z += 4) {
                zet(1762, K + 4, z, Bouwer.gericht(Material.WHITE_WALL_BANNER, BlockFace.EAST));
                zet(1788, K + 4, z, Bouwer.gericht(Material.WHITE_WALL_BANNER, BlockFace.WEST));
            }
            for (int[] l : new int[][]{{1768, 8}, {1782, 8}, {1768, 26}, {1782, 26}}) {
                vul(l[0], K + 9, l[1], l[0], K + 10, l[1], Material.IRON_CHAIN);
                zet(l[0], K + 8, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
            }
            for (int z = 25; z <= 27; z++) zet(1788, K + 4, z, Material.CHISELED_QUARTZ_BLOCK);   // de lijst om de deur (oost)
        }
        Barst muur = barstOpPad(new Barst("kapelmuur", deur(ZeldaHoofdstuk.BARST.createBlockData(), 1767, K + 1, 5, 1769, K + 3, 5), Box.van(1765, K, -2, 1771, K + 5, 9)));
        muur.voor = plek(1768, K + 1, 1, 0f);
        verhaal(new Verhaal("zuidgang", Box.van(1767, K, -4, 1769, K + 4, 3), List.of(
                r("Wiske", "Een gebarsten muur. Daarachter klinkt het hol.")))).als = s -> !s.heeft("b:kapelmuur");
        verhaal(new Verhaal("kapel", Box.van(1763, K, 6, 1787, K + 5, 9), List.of(
                r("Wiske", "Een kapel van Zelda's wachters. Wit steen!"),
                r("Wiske", "Over de put: een altaar. En een haakpunt.")))).als = s -> !s.heeft(LICHTPIJLEN);
        Haakpunt hp = haakpunt(new Haakpunt("kapel", 1775, K + 4, 22, plek(1775, K + 1, 24, 0f)));
        haak(hp, plek(1775, K + 1, 8, 0f));
        Verhaal vuren = verhaal(new Verhaal("uitleg-kapelvuren", Box.van(1770, K, 23, 1780, K + 4, 26), List.of(
                r("Wiske", "Drie vuurschalen, koud. Jouw vuurstaf!"),
                r("Wiske", "Alle drie snel na elkaar."))));
        vuren.uitleg = true;
        vuren.als = s -> !s.heeft("r:kapelvuren") && !s.heeft(LICHTPIJLEN);
        Reeks reeks = reeks(new Reeks("kapelvuren", "De kapelvuren", Reeks.Hoe.VUUR, ZeldaEiland4a.vuur(Material.CAMPFIRE, false), ZeldaEiland4a.vuur(Material.CAMPFIRE, true),
                new Deur(Material.POLISHED_DEEPSLATE.createBlockData(), Material.SEA_LANTERN.createBlockData()).blok(1774, K + 1, 28).blok(1776, K + 1, 28).blok(1775, K + 1, 29),
                Box.van(1762, K - 1, 22, 1788, K + 10, 30), 20));
        for (int[] v : VUREN) reeks.dingen.add(new int[]{v[0], v[1], v[2]});
        reeks.voor = plek(1775, K + 1, 24, 0f);
        reeks.bijOplossen.add(r("Wiske", "Het altaar licht op! Daar, op het altaar…"));
        Vondst pijlen = vondst(new Vondst(LICHTPIJLEN, "Lichtpijlen", "nevel verdwijnt waar ze raken", Material.SPECTRAL_ARROW, plek(1775, K + 2.4, 28, 0f),
                Box.van(1774, K + 1, 27, 1776, K + 3, 29)));
        pijlen.vereist = "r:kapelvuren";
        pijlen.daarna = (spel, p, s) -> spel.uitleg(p, r("Wiske", "Pijlen van licht! Vak 7, of gewoon je boog."),
                r("Wiske", "Schiet op de nevel bij de deur. Dan is hij weg!"));
        Nevel deur = nevelOpPad(new Nevel("kapeldeur", nevelDeur(1789, K + 1, 25, 1789, K + 3, 27), Box.van(1780, K, 22, 1794, K + 5, 30)));
        deur.voor = plek(1783, K + 1, 26, -90f);
        verhaal(new Verhaal("kapeldeur", Box.van(1780, K, 23, 1787, K + 4, 29), List.of(
                r("Wiske", "Zwarte nevel voor de deur. Morgals nevel!")))).als = s -> !s.heeft(LICHTPIJLEN);
    }

    /** De drie vuurschalen in de kapel: {x, y (de schaal), z} (gezet in {@link #kapel}, met de hoogte van dit deel). */
    private int[][] VUREN = new int[0][];

    /** Het steen van de kapel: wit (calciet en kwarts), met hier en daar zwart van Morgals nevel. */
    static Material licht(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x * 3 - 7, y, z * 11 + 4) % 16);
        return k < 7 ? Material.CALCITE : k < 11 ? Material.SMOOTH_QUARTZ : k < 13 ? Material.QUARTZ_BRICKS : k < 15 ? Material.DIORITE : Material.BLACK_CONCRETE;
    }

    // ── 7. de wachtersgang, de wapenkamer (het zwarte harnas), de nevelgang ─────────────────

    private void wachtersgang() {
        if (b.plaats) {
            gang(1790, 25, 1797, 27, K, 4);                                             // van de kapel oost
            gang(1795, -21, 1797, 24, K, 4);                                            // noordwaarts
            gang(1778, -24, 1806, -22, K, 4);                                           // de nevelgang (west naar de kerkers, oost naar de trap)
            for (int z = -18; z <= 22; z += 8) zet(1797, K + 3, z, Bouwer.gericht(Material.WHITE_WALL_BANNER, BlockFace.WEST));
            for (int z = -16; z <= 24; z += 10) zet(1795, K + 4, z, Bouwer.hangend(Material.SOUL_LANTERN));
            for (int x = 1782; x <= 1804; x += 11) zet(x, K + 4, -23, Bouwer.hangend(Material.SOUL_LANTERN));
            // de wapenkamer: x 1782 … 1793, z −16 … −4
            gang(1782, -16, 1793, -4, K, 6);
            for (int z = -15; z <= -5; z += 2) {                                        // rekken met speren en schilden (blokken)
                zet(1783, K + 1, z, Material.SPRUCE_FENCE);
                zet(1783, K + 2, z, Material.LIGHTNING_ROD);
            }
            vul(1786, K + 1, -11, 1788, K + 1, -9, Material.POLISHED_DEEPSLATE);         // het voetstuk van het harnas
            zet(1787, K + 1, -10, Material.CHISELED_DEEPSLATE);
            for (int x = 1784; x <= 1792; x += 4) zet(x, K + 3, -4, Bouwer.gericht(Material.WHITE_WALL_BANNER, BlockFace.NORTH));
            zet(1787, K + 6, -10, Bouwer.hangend(Material.SOUL_LANTERN));
            // de wapenkist achter nevel (noordwand)
            gang(1786, -19, 1788, -18, K, 3);
            zet(1787, K + 1, -19, Material.CHEST);
        }
        verhaal(new Verhaal("wachtersgang", Box.van(1790, K, 24, 1797, K + 4, 27), List.of(
                r("Wiske", "Banieren van Zelda's wachters. Wit en goud."))));
        Nevel kamer = nevelOpPad(new Nevel("wapenkamer", nevelDeur(1794, K + 1, -11, 1794, K + 3, -9), Box.van(1785, K, -15, 1798, K + 5, -4)));
        kamer.voor = plek(1796, K + 1, -10, 90f);
        Vondst harnas = vondst(new Vondst(ZWARTHARNAS, "Zwart harnas", "Mira's licht maakt het schoon", Material.NETHERITE_CHESTPLATE, plek(1787, K + 2.4, -10, 0f),
                Box.van(1786, K + 1, -11, 1788, K + 3, -9)));
        harnas.daarna = (spel, p, s) -> {
            if (s.missie(LICHTHARNAS) == 0) spel.missie(p, LICHTHARNAS, 1);
            spel.uitleg(p, r("Wiske", "Het harnas van Zelda's wachters. Zwart van de nevel."),
                    r("Wiske", "Licht maakt het schoon. Mira's vuurtoren!"));
        };
        missie(LICHTHARNAS, "Het zwarte harnas", "Breng het zwarte harnas naar Mira, op het Zilverstrand.");
        Nevel kist = nevelOpPad(new Nevel("wapenkist", nevelDeur(1786, K + 1, -17, 1788, K + 3, -17), Box.van(1784, K, -20, 1792, K + 5, -8)));
        kist.voor = plek(1787, K + 1, -13, 180f);
        vondst(new Vondst(STUKJE_KIST, "Hartstukje", null, Material.RED_DYE, plek(1787, K + 2.4, -19, 0f), Box.van(1786, K + 1, -19, 1788, K + 3, -18)));
        Nevel gang = nevelOpPad(new Nevel("nevelgang", nevelDeur(1777, K + 1, -24, 1777, K + 3, -22), Box.van(1771, K, -27, 1784, K + 5, -19)));
        gang.voor = plek(1781, K + 1, -23, 90f);
        verhaal(new Verhaal("nevelgang", Box.van(1771, K, -26, 1776, K + 4, -20), List.of(
                r("Wiske", "Nevel. Daar kom je zo niet door.")))).als = s -> !s.heeft(LICHTPIJLEN);
    }

    /** Mira (eiland 2a, de vuurtoren) maakt het zwarte harnas schoon: zij staat op het pad (de test vaart erheen). */
    private void mira() {
        if (zilverstrand != null && zilverstrand.onderdeel("Mira") instanceof Persoon mira) {
            praten(mira);
            miraOpPad = true;
        }
    }

    // ── 8. de kerkertrap, het wachthuis en de binnenpoort ───────────────────────────────────

    private void kerkertrap() {
        if (b.plaats) {
            // de schacht: x 1802 … 1806, z −30 … −26, een wenteltrap om (1804, −28) van K naar P
            for (int x = 1801; x <= 1807; x++)
                for (int z = -31; z <= -25; z++) {
                    boolean rand = x == 1801 || x == 1807 || z == -31 || z == -25;
                    for (int y = K; y <= P; y++) {
                        if (rand && !uitgehakt(x, y, z)) zet(x, y, z, steen(x, y, z));
                        else if (!rand) zet(x, y, z, y == K ? vloer(x, z) : Material.AIR);
                    }
                }
            holte(1802, K + 1, -30, 1806, P, -26);
            wenteltrap(1804, -28, K, P - K, 6, Material.DEEPSLATE_BRICK_STAIRS, Material.DEEPSLATE_BRICKS, Material.POLISHED_DEEPSLATE);
            zet(1802, K + 6, -30, Material.SOUL_LANTERN);
            // het wachthuis: x 1798 … 1812, z −38 … −24 (binnen x 1799 … 1811, z −37 … −25)
            for (int x = 1798; x <= 1812; x++)
                for (int z = -38; z <= -24; z++) {
                    boolean rand = x == 1798 || x == 1812 || z == -38 || z == -24;
                    boolean gat = x >= 1803 && x <= 1805 && z >= -29 && z <= -27;           // het trapgat in de vloer
                    if (!gat) zet(x, P, z, rand ? zwart(x, P, z) : vloer(x, z));
                    for (int y = P + 1; y <= P + 5; y++) {
                        boolean raam = rand && y == P + 3 && (x + z) % 4 == 0;
                        zet(x, y, z, rand ? (raam ? Material.PURPLE_STAINED_GLASS_PANE : zwart(x, y, z)) : Material.AIR);
                    }
                    zet(x, P + 6, z, Material.POLISHED_BLACKSTONE);
                }
            zet(1800, P + 1, -37, Material.BARREL);
            zet(1801, P + 1, -37, Material.BARREL);
            for (int x = 1805; x <= 1810; x++) zet(x, P + 1, -37, x % 2 == 0 ? Material.RED_WOOL : Material.SPRUCE_SLAB);   // de britsen
            zet(1810, P + 1, -26, Material.SMITHING_TABLE);
            zet(1805, P + 5, -32, Bouwer.hangend(Material.LANTERN));
            zet(1805, P + 5, -26, Bouwer.hangend(Material.LANTERN));
            // de doorgang naar de binnenplaats (z −23 … −21), met muren
            for (int z = -23; z <= -22; z++) {
                for (int x = 1803; x <= 1807; x++) {
                    zet(x, P, z, vloer(x, z));
                    for (int y = P + 1; y <= P + 5; y++) zet(x, y, z, x == 1803 || x == 1807 ? zwart(x, y, z) : Material.AIR);
                    zet(x, P + 6, z, Material.POLISHED_BLACKSTONE);
                }
            }
            vul(1804, P + 1, -21, 1806, P + 4, -21, Material.AIR);
            bord(1807, P + 3, -25, BlockFace.NORTH, "De binnenplaats");
        }
        Nevel nv = nevelOpPad(new Nevel("kerkertrap", nevelDeur(1803, K + 1, -25, 1805, K + 3, -25), Box.van(1798, K, -29, 1808, K + 5, -21)));
        nv.voor = plek(1800, K + 1, -23, 90f);
        rustvuur(1809, P, -34);
        rust(new Rust("wachthuis", "Het wachthuis", plek(1808, P + 1, -31, 180f), Box.van(1799, P, -37, 1811, P + 5, -30), List.of()));
        bron("bron-wachthuis", plek(1801, P + 1, -34, 0f));
        verhaal(new Verhaal("wachthuis", Box.van(1799, P, -37, 1811, P + 4, -30), List.of(
                r("Wiske", "Een wachthuis. Leeg! Even uitrusten."),
                r("Wiske", "Hoor je dat? Laarzen, op de binnenplaats."))));
        poort(new Poort("binnenpoort", deur(Material.IRON_BLOCK.createBlockData(), 1804, P + 1, -24, 1806, P + 3, -24), Box.van(1800, P, -30, 1810, P + 5, -21),
                s -> s.heeft("harnas4") && s.heeft(WACHTWOORD)));
        verhaal(new Verhaal("binnenpoort-woord", Box.van(1802, P, -28, 1808, P + 4, -25), List.of(
                r("Wiske", "Een kijkgat in de deur. «Wachtwoord?»"),
                r("Wiske", "Wolf weet het vast. Hij zit in de kreek.")))).als = s -> !s.heeft(WACHTWOORD);
        verhaal(new Verhaal("binnenpoort-harnas", Box.van(1802, P, -28, 1808, P + 4, -25), List.of(
                r("Wiske", "Wacht. Zo, zonder harnas, die binnenplaats op?"),
                r("Wiske", "Eerst het lichtharnas. Mira kan het schoonmaken.")))).als = s -> s.heeft(WACHTWOORD) && !s.heeft("harnas4");
        verhaal(new Verhaal("binnenpoort", Box.van(1802, P, -28, 1808, P + 4, -25), List.of(
                r("Wiske", "«Maanloos!» Hoor, de grendel gaat eraf.")))).als = s -> s.heeft(WACHTWOORD) && s.heeft("harnas4");
    }

    // ── 9. de binnenplaats: het enige gevecht ───────────────────────────────────────────────

    private void binnenplaats() {
        if (b.plaats) {
            for (int x = 1800; x <= 1840; x++)
                for (int z = -20; z <= 20; z++) {
                    double r = Math.hypot(x - 1820, z);
                    zet(x, P, z, r < 3 ? Material.POLISHED_BLACKSTONE_BRICKS : (int) r % 6 == 0 ? Material.POLISHED_BASALT : (x + z) % 2 == 0 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE);
                    vul(x, P + 1, z, x, P + 12, z, Material.AIR);
                }
            // Morgals zuil in het midden: zwart, met een paars hart
            vul(1820, P + 1, 0, 1820, P + 6, 0, Material.POLISHED_BLACKSTONE_BRICKS);
            zet(1820, P + 7, 0, Material.CRYING_OBSIDIAN);
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
                zet(1820 + f.getModX(), P + 1, f.getModZ(), Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, f.getOppositeFace()));
            // vier vuren van zielvuur in schalen, oefenpoppen, wapenrekken
            for (int[] c : new int[][]{{1808, -12}, {1832, -12}, {1808, 12}, {1832, 12}}) {
                zet(c[0], P + 1, c[1], Material.POLISHED_BLACKSTONE_WALL);
                zet(c[0], P + 2, c[1], ZeldaEiland4a.vuur(Material.SOUL_CAMPFIRE, true));
            }
            for (int[] c : new int[][]{{1826, -16}, {1830, -16}, {1834, -16}}) {
                zet(c[0], P + 1, c[1], Material.SPRUCE_FENCE);
                zet(c[0], P + 2, c[1], Material.HAY_BLOCK);
                zet(c[0], P + 3, c[1], Material.CARVED_PUMPKIN);
            }
            for (int z = 8; z <= 16; z += 4) {
                zet(1801, P + 1, z, Material.SPRUCE_FENCE);
                zet(1801, P + 2, z, Material.LIGHTNING_ROD);
            }
            zet(1838, P + 1, 17, Material.BARREL);
            zet(1838, P + 1, 18, Material.BARREL);
            zet(1837, P + 1, 18, Material.BARREL);
            for (int x = 1806; x <= 1834; x += 7) {
                zet(x, P + 6, -20, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.SOUTH));
                zet(x, P + 6, 20, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.NORTH));
            }
            zet(1812, P + 1, -19, Bouwer.gericht(Material.BELL, BlockFace.SOUTH));
            zet(1812, P, -19, Material.POLISHED_BLACKSTONE);
            vul(1841, P + 1, -2, 1841, P + 4, 2, Material.AIR);                         // de oostpoort (de uitgang)
            vul(1799, P + 1, -2, 1799, P + 4, 2, Material.AIR);                         // de westpoort (na de binnenplaats)
        }
        Kamer k = kamer(new Kamer("binnenplaats", "De binnenplaats", Box.van(1800, P, -20, 1840, P + 10, 20), Box.van(1801, P, -18, 1839, P + 6, 12),
                deur(tralies(false), 1804, P + 1, -21, 1806, P + 4, -21), deur(tralies(true), 1841, P + 1, -2, 1841, P + 4, 2)));
        k.muziek = ZeldaMuziek.NACHTWACHT;
        k.hemel = 18000;
        for (int[] sp : new int[][]{{1810, -6}, {1830, -6}, {1810, 6}, {1830, 6}, {1820, -14}, {1820, 14}, {1836, 0}})
            k.spawns.add(plek(sp[0], P + 1, sp[1], 0f));
        k.golf(Soort.NACHTSOLDAAT, Soort.NEVELMIJT);
        k.golf(Soort.NACHTSOLDAAT, Soort.NACHTSCHUTTER);
        k.golf(Soort.NACHTSOLDAAT, Soort.NACHTSCHUTTER, Soort.NEVELMIJT);
        k.extra(Soort.WACHTMEESTER, Soort.NEVELMIJT, Soort.NEVELMIJT);
        k.bel = new int[]{1812, P + 1, -19};
        k.bijStart.add(r("Wiske", "Morgals soldaten! Daar komen ze."));
        k.bijStart.add(r("Wiske", "Je harnas glanst. Daar schrikken ze van!"));
        k.tips.addAll(List.of(r("Wiske", "De schutters eerst! Die blijven achteraan."),
                r("Wiske", "Zwarte mijtjes? Een lichtpijl doet ze dubbel pijn.")));
        poort(new Poort("westpoort", deur(tralies(true), 1799, P + 1, -2, 1799, P + 4, 2), Box.van(1790, P, -4, 1799, P + 5, 4),   // alleen van de laan (niet midden in een gevecht)
                s -> s.heeft("k:binnenplaats") && k.fase != Fase.BEZIG));
    }

    // ── 10. de voorburcht en de hoofdpoort (na de binnenplaats: een rondje terug) ───────────

    private void voorburcht() {
        if (b.plaats) {
            for (int x = 1769; x <= 1798; x++)
                for (int z = -2; z <= 2; z++) {
                    zet(x, P, z, x <= 1772 ? vloer(x, z) : (x + z) % 3 == 0 ? Material.COBBLED_DEEPSLATE : Material.POLISHED_BLACKSTONE);
                    vul(x, P + 1, z, x, P + 5, z, Material.AIR);
                    if (x <= 1772) zet(x, P + 6, z, zwart(x, P + 6, z));
                }
            for (int x = 1773; x <= 1798; x++)                                           // muurtjes langs de laan
                for (int y = P + 1; y <= P + 3; y++) {
                    zet(x, y, -3, zwart(x, y, -3));
                    zet(x, y, 3, zwart(x, y, 3));
                }
            for (int x = 1776; x <= 1796; x += 10) {
                zet(x, P + 4, -3, Material.SOUL_LANTERN);
                zet(x, P + 4, 3, Material.SOUL_LANTERN);
            }
            // buiten de hoofdpoort: een pad over het plateau naar de muurvoet
            for (int x = 1752; x <= 1767; x++)
                for (int z = -5; z <= 5; z++) if (kans(x, z, 4) == 0) zet(x, P, z, Material.COBBLED_DEEPSLATE);
            paalbord(1765, P + 1, -5, BlockFace.WEST, "De muurvoet");
        }
        poort(new Poort("hoofdpoort", deur(tralies(true), 1768, P + 1, -2, 1768, P + 5, 2), Box.van(1758, P, -5, 1776, P + 6, 5), s -> s.heeft("k:binnenplaats")));
    }

    // ── 11. de torenpoort: het einde van 5a ─────────────────────────────────────────────────

    private void torenpoort() {
        if (b.plaats) {
            for (int x = 1842; x <= 1857; x++)
                for (int z = -3; z <= 3; z++) {
                    zet(x, P, z, vloer(x, z));
                    vul(x, P + 1, z, x, P + 7, z, Material.AIR);
                    if (Math.abs(z) == 3) for (int y = P + 1; y <= P + 7; y++) zet(x, y, z, zwart(x, y, z));
                    zet(x, P + 8, z, Material.POLISHED_BLACKSTONE);
                }
            for (int x = 1844; x <= 1856; x += 6) zet(x, P + 7, 0, Bouwer.hangend(Material.SOUL_LANTERN));
            // de torenpoort in de oostmuur: zwart en paars, dicht (Morgals zegel)
            for (int z = -2; z <= 2; z++)
                for (int y = P + 1; y <= P + 7; y++) zet(1858, y, z, Math.abs(z) + Math.abs(y - P - 4) <= 1 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS);
            zet(1858, P + 4, 0, Material.CRYING_OBSIDIAN);
            for (int y = P + 1; y <= P + 8; y++) {
                zet(1858, y, -3, Material.GILDED_BLACKSTONE);
                zet(1858, y, 3, Material.GILDED_BLACKSTONE);
            }
            // een brug die in het niets eindigt (oost, achter de poort)
            for (int x = 1863; x <= 1870; x++) for (int z = -1; z <= 1; z++) zet(x, P, z, x < 1868 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.AIR);
        }
        rustvuur(1846, P, -1);
        rust(new Rust("torenpoort", "De torenpoort", plek(1849, P + 1, 1, -90f), Box.van(1843, P, -2, 1854, P + 5, 2), List.of()));
        Persoon tijmen = persoon(new Persoon("Tijmen", plek(1852, P + 1, 2, -90f), null, List.of(r("Tijmen", "Rust eerst. Dit wordt de laatste klim."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.zichtbaar = s -> s.heeft("k:binnenplaats");
        torenTijmen = tijmen;
        tijmen.eenmalig("tijmen-toren", s -> s.heeft("k:binnenplaats"), List.of(
                r("Wiske", "Tijmen! Hoe kom jij hier nou weer?"),
                r("Tijmen", "Daar boven is Zelda. En Morgal."),
                r("Tijmen", torenErbij() ? "De poort zit dicht met zijn zegel." : "De poort zit dicht met zijn nevel. Nog even.")), null);
        if (torenErbij()) tijmen.gesprek("tijmen-zegel", s -> s.heeft("k:binnenplaats") && !s.heeft(ZeldaEiland5b.ZEGEL), List.of(   // de toren (5b): hij breekt het zegel
                r("Tijmen", "Morgals zegel. Ik ken een oude spreuk."),
                r("Tijmen", "«Nacht, ga slapen.» Zo. Weg ermee!")), (spel, p, s) -> {
            if (s.zet(ZeldaEiland5b.ZEGEL)) spel.voortgang.vuil();
            spel.muziek.speel(p, ZeldaMuziek.OPGELOST);
            p.getWorld().spawnParticle(Particle.END_ROD, new Location(w, 1857.5, P + 4, 0.5), 40, 0.3, 1.5, 1.5, 0.05);
            p.getWorld().playSound(new Location(w, 1858, P + 4, 0), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
            spel.vertel(p, r("Wiske", "Het zegel breekt! Kom, de toren in."));
        });
        tijmen.gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
        Verhaal einde = verhaal(new Verhaal("torenpoort", Box.van(1852, P, -2, 1857, P + 5, 2), List.of(
                r("Wiske", "De toren van Morgal. Daar is Zelda!"),
                r("Wiske", "De poort zit dicht. Morgals zegel…"),
                r(null, "Nachtrots · de muren zijn genomen"))));
        einde.vereist = "k:binnenplaats";
        einde.einde = true;
        torenTop = new Location(w, TCX + 0.5, P + 62, TCZ + 0.5);
    }

    // ── 12. de toren (5b) als decor: een rots met een toren in nevel ────────────────────────

    private void toren() {
        int voet = P + 6, top = P + 56;
        for (int x = TCX - 9; x <= TCX + 9; x++)
            for (int z = TCZ - 9; z <= TCZ + 9; z++) {
                double r = Math.hypot(x - TCX, z - TCZ);
                if (r > 8.5) continue;
                boolean rand = r > 7.3;
                for (int y = voet; y <= top; y++) {
                    if (!rand) continue;
                    boolean raam = (y - voet) % 9 == 5 && ((x == TCX) || (z == TCZ));
                    zet(x, y, z, raam ? Material.PURPLE_STAINED_GLASS : zwart(x, y, z));
                }
                zet(x, top, z, Material.POLISHED_BLACKSTONE);
            }
        for (int t = 0; t <= 9; t++)
            for (int x = TCX - 9 + t; x <= TCX + 9 - t; x++)
                for (int z = TCZ - 9 + t; z <= TCZ + 9 - t; z++)
                    if (Math.hypot(x - TCX, z - TCZ) <= 9.2 - t) zet(x, top + 1 + t, z, Material.DEEPSLATE_TILES);
        zet(TCX, top + 11, TCZ, Material.CRYING_OBSIDIAN);
        zet(TCX, top + 12, TCZ, Material.CRYING_OBSIDIAN);
        zet(TCX - 8, top - 6, TCZ, Material.SHROOMLIGHT);                          // één raam brandt: daar is Zelda
    }

    /**
     * Zonder blokken (dus zonder herbouw): de doelen voor {@code /hint} en Tijmen. De kreek (Tijmen, Wolf), de klippen, de
     * weergang, de kerkers; dan in elke volgorde (doelgroep {@code kerker}) de sleutelbos, de lichtpijlen, Wolf bevrijden en
     * de wapenkamer; Wolf in de kreek, het lichtharnas (Mira), de kerkertrap, de binnenplaats, de torenpoort.
     */
    private void motor() {
        Location wolfKreek = plek(1710, G + 1, 5, 180f);
        doel("tijmen5a", "Iemand zit verstopt bij de huisjes.", "Praat met Tijmen, onder het afdakje.", plekVan("Tijmen"),
                s -> s.heeft("g:tijmen-kreek") || s.heeft(MUURSLEUTEL));
        doel("wolf5a", "Iemand fluit, achter een raampje in de rots.", "Klim op de richel onder het raam. Praat met Wolf.", raam,
                s -> s.heeft(MUURSLEUTEL));
        doel("klippen", "Langs de klippen naar boven.", "Het pad begint achter de huisjes. Een haak, een bom.", plekVan("klippen"),
                s -> s.bezocht(id(), 1));
        doel("weergang", "Wolfs sleutel past op de muurdeur.", "Over de weergang. Blijf uit het licht van de lantaarns.", plekVan("muurdeur"),
                s -> s.heeft("l:weergang") || s.bezocht(id(), 2));
        doel("kerkers5a", "In de toren gaat een trap omlaag.", "Neem de trap omlaag, de kerkers in.", plekVan("kerkertoren"),
                s -> s.bezocht(id(), 2));
        doel("kerker", SLEUTELBOS, "De kerkermeester heeft de sleutels.", "Sluip door de cellengang naar de wachtkamer.", plekVan(SLEUTELBOS),
                s -> s.heeft(SLEUTELBOS));
        doel("kerker", LICHTPIJLEN, "Zuid in de kerkers: een gebarsten muur.", "Een bom tegen de muur. Daarachter: een kapel.", plekVan("kapelmuur"),
                s -> s.heeft(LICHTPIJLEN));
        doel("kerker", "wolf-vrij", "Wolf zit nog vast.", "Open zijn cel, aan het eind van de cellengang.", plekVan("cel-wolf"),
                s -> s.heeft(WOLF_VRIJ) || !s.heeft(SLEUTELBOS));
        doel("kerker", ZWARTHARNAS, "Nevel voor een deur. Je lichtpijlen!", "Schiet op de nevel. Daar is de wapenkamer.", plekVan("wapenkamer"),
                s -> s.heeft(ZWARTHARNAS) || s.heeft("harnas4") || !s.heeft(LICHTPIJLEN));
        doel("wolf-kreek", "Wolf wacht in de kreek.", "Praat met Wolf, bij het zielvuur in de kreek.", wolfKreek,
                s -> s.heeft(WACHTWOORD));
        doel("lichtharnas", "Het harnas is zwart van de nevel.", "Vaar naar het Zilverstrand. Mira's licht maakt het schoon.", plekVan("Brecht"),
                s -> s.heeft("harnas4"));
        doel("kerkertrap", "Omhoog, naar de binnenplaats.", "De trap achter de nevel, oost in de kerkers.", plekVan("kerkertrap"),
                s -> s.bezocht(id(), 3));
        doel("binnenplaats", "Morgals soldaten, op de binnenplaats.", "Zeg het wachtwoord bij de poort. Rust eerst uit.", plekVan("binnenplaats"),
                s -> s.heeft("k:binnenplaats"));
        doel("torenpoort", "De toren! Daar is Zelda.", "Loop door, oost van de binnenplaats.", rust("torenpoort").plek,
                s -> s.heeft(id()));
    }

    // ── tijdens het spel: onweer in de verte, nevel om de toren, de kerkermeester snurkt ────

    @Override
    void tik(ZeldaSpel spel, List<Player> wie, long nu) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        Particle.DustOptions nevel = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x15121c), 4f);
        for (Player p : wie) {
            if (!p.getWorld().equals(w) || spel.beheer.is(p.getUniqueId())) continue;
            Location l = p.getLocation();
            ZeldaVoortgang.Stand s = spel.voortgang.van(p.getUniqueId());
            boolean dag = s.heeft("k:nachttroon");                                       // Morgal is weg (5b): geen onweer en geen nevel meer
            boolean buiten = w.getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) <= l.getBlockY() + 1;
            if (buiten && !dag && r.nextInt(40) == 0) {                                  // onweer in de verte
                Location ver = l.clone().add(r.nextDouble(-60, 60), 30, r.nextDouble(-60, 60));
                p.playSound(ver, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, org.bukkit.SoundCategory.WEATHER, 0.25f, 0.6f);
            }
            if (torenTop != null && buiten && !dag)                                     // Morgals nevel om de toren
                for (int i = 0; i < 6; i++) {
                    double a = r.nextDouble() * Math.PI * 2, rr = (torenErbij() ? 19 : 10) + r.nextDouble() * 6;
                    spel.deeltje(p, Particle.DUST, new Location(w, TCX + 0.5 + Math.cos(a) * rr, P + 10 + r.nextDouble() * 54, TCZ + 0.5 + Math.sin(a) * rr), 1, 1.5, 2, 1.5, nevel);
                }
            if (l.distanceSquared(new Location(w, 1738, K + 1, -11)) < 14 * 14 && r.nextInt(3) == 0)   // de kerkermeester snurkt
                p.playSound(new Location(w, 1738.5, K + 1.5, -10.5), org.bukkit.Sound.ENTITY_FOX_SLEEP, 0.7f, 0.6f);
            if (l.distanceSquared(new Location(w, 1858, P + 4, 0)) < 30 * 30 && !s.heeft(ZeldaEiland5b.ZEGEL))   // nevel voor de torenpoort
                for (int i = 0; i < 4; i++) p.spawnParticle(Particle.LARGE_SMOKE, 1857.2, P + 1 + r.nextDouble() * 7, -2.5 + r.nextDouble() * 6, 0, 0, 0.03, 0, 1);
        }
    }

    /** Op de kaart: de visserskreek en de kerkers. */
    @Override
    List<Teken> tekens() {
        return List.of(new Teken("dorp-hout", 1706, 0), new Teken("kerker", 1768, -21));
    }
}
