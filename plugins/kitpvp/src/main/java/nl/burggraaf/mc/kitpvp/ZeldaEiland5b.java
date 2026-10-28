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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

/**
 * Eiland 5b, «Nachtrots»: de toren en Morgal, het einde (zie ZELDA.md). Tijmen breekt Morgals zegel op de torenpoort; daarachter
 * hangt boven zee een <b>nevelbrug</b> ({@link ZeldaHoofdstuk.Nevel#brug}): wie er een lichtpijl doorheen schiet, ziet hem (drie
 * stukken, over twee oude pijlers). Op de torenrots de <b>voorhof</b> (rust, bron) en de torenhal (Ida, en onder de vloer Morgals
 * schatkamer, die pas na het einde opengaat).
 *
 * <p>De toren beklim je buiten, om de muur heen, in de nevel: elke kant een ander voorwerp. West een trap van licht (lichtpijl),
 * noord een ingestorte trap (klauwhaak; daar is Zelda's kamertje, met een bron), oost drie wachtersvuren (vuurstaf: dan verschijnt
 * de trap), zuid puin (een bom) en weer een trap van licht. Bovenaan het <b>torenbalkon</b> (rust, bron, Tijmen): de laatste rust.
 * Valt er iemand, dan vangt de nevel hem op ({@link ZeldaHoofdstuk.Val}). Een ladder langs het balkon is de korte weg naar beneden
 * (het hek onderaan gaat alleen open voor wie boven was).
 *
 * <p>In de troonzaal <b>Morgal</b> ({@link Morgal}, drie fases). Daarna de ladder naar het dak: daar zit Zelda in een kooi van nevel.
 * Zij is vrij, de zon komt op, en Zelda gaat vooruit naar Brecht in de kreek. De terugvaart ({@link #vaart}) en het feest op de
 * open plek van het Lindewoud ({@link ZeldaFeest}) maken het af: daar zet Zelda de vlag {@code zelda}.
 *
 * <p>Het vak is x 1880 … 2000; dit deel zet elke kolom zelf (5a bouwt hier niets meer). In het vak van 5a zet het alleen wat nodig
 * is om aan te haken: de torenpoort (een {@link Poort}), de gang door de muur en de brugkop (altijd, zonder herbouw).
 */
final class ZeldaEiland5b extends ZeldaHoofdstuk {

    static final int VERSIE = 1;
    static final int X1 = 1880, X2 = 2000, Z1 = -150, Z2 = 150;
    /** Het midden van de toren (op zijn eigen rots, oost van de burcht); de toren zelf is x CX±10, z CZ±10. */
    static final int CX = ZeldaEiland5a.TCX, CZ = ZeldaEiland5a.TCZ;
    /** De vlaggen van 5b. */
    static final String ZEGEL = "zegel", VRIJ_ZELDA = "vrij:zelda", KROONTJE = "kroontje", STUKJE_SCHAT = "hs:nachtrots-4", TERUGVAART = "v:terugvaart";
    /** De stukken van de nevelbrug boven zee (x van … tot), en de twee pijlers ertussen. */
    static final int[][] BRUG = {{1873, 1882}, {1886, 1895}, {1899, 1908}};
    static final int[][] PIJLERS = {{1883, 1885}, {1896, 1898}};

    private final Plugin plugin;
    private final ZeldaEiland5a muren;                      // de torenpoort en Tijmen daar (5a)
    private final ZeldaHoofdstuk1 lindewoud;                // het feest (op de open plek van het Lindewoud)
    private Bouwer b;
    int G, P;
    private int g;
    /** Staat Zelda op het feest (het Lindewoud) op het pad? Nee = het Lindewoud was er niet bij het bouwen (de zelftest meldt het). */
    boolean feestOpPad;
    /** Tijmen bij de torenpoort (5a) staat op het pad: hij breekt het zegel. */
    boolean zegelOpPad;
    Morgal morgal;

    ZeldaEiland5b(Plugin plugin, ZeldaEiland5a muren, ZeldaHoofdstuk1 lindewoud) {
        this.plugin = plugin;
        this.muren = muren;
        this.lindewoud = lindewoud;
    }

    @Override String id() { return "e5b"; }
    @Override String naam() { return "Nachtrots"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda-e5b"; }

    /** Een open deel (een rondje: de trap op, de ladder af): «opnieuw vanaf hier» vergeet alleen tot de volgende rustplaats. */
    @Override boolean open() { return true; }

    // ── tijd, naam en muziek per plek ───────────────────────────────────────────────────────

    /** In de toren is het nacht (Morgal); is Zelda vrij, dan komt de zon op. */
    @Override
    long hemel(Location l, ZeldaVoortgang.Stand s) {
        if (s.heeft("zelda")) return 1500;
        if (s.heeft(VRIJ_ZELDA) || s.heeft("k:nachttroon")) return 23300;
        return 18000;
    }

    private static boolean in(double x, double y, double z, int x1, int y1, int z1, int x2, int y2, int z2) {
        return x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1;
    }

    @Override
    String plekNaam(Location l) {
        double x = l.getX(), y = l.getY(), z = l.getZ();
        if (x < 1910 && Math.abs(z) <= 8) return "De Nevelbrug";
        if (in(x, y, z, 1930, P + 53, -10, 1950, P + 70, 10)) return "Het dak";
        if (in(x, y, z, 1932, P + 40, -8, 1948, P + 52, 8)) return "De troonzaal";
        if (in(x, y, z, 1932, P - 7, -8, 1948, P - 1, 8)) return "Morgals schatkamer";
        if (in(x, y, z, 1942, P + 20, -9, 1949, P + 25, -3)) return "Zelda's kamertje";
        if (in(x, y, z, 1930, P, -10, 1950, P + 9, 10)) return "De torenhal";
        if (in(x, y, z, 1918, P + 39, 8, 1937, P + 46, 19)) return "Het torenbalkon";
        if (y > P + 4 && Math.abs(x - CX) <= 17 && Math.abs(z - CZ) <= 17) return "De torentrap";
        if (x < 1930) return "De voorhof";
        return "De torenrots";
    }

    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return switch (plekNaam(l)) {
            case "Morgals schatkamer" -> ZeldaMuziek.KERKERS;
            default -> ZeldaMuziek.TOREN;
        };
    }

    /** Is Zelda vrij, dan klinkt buiten het lied van thuis (de nacht is voorbij). */
    @Override
    ZeldaMuziek.Lied muziek(Location l, ZeldaVoortgang.Stand s) {
        String n = plekNaam(l);
        if (s.heeft(VRIJ_ZELDA) && !n.equals("Morgals schatkamer") && !n.equals("De torenhal")) return ZeldaMuziek.THUIS;
        return muziek(l);
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

    private static Villager.Profession beroep(String id) {
        return Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(id));
    }

    private Deur deur(BlockData dicht, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Deur(dicht, Material.AIR.createBlockData()).vlak(x1, y1, z1, x2, y2, z2);
    }

    private void zetDeur(Deur d, boolean open) {
        d.isOpen = open;
        for (int[] c : d.blokken) b.zetAltijd(c[0], c[1], c[2], open ? d.open : d.dicht);
    }

    /** Een trap van licht in de nevel (dicht = lucht, open = treden van wit steen), zie {@link ZeldaRegels#trap}. */
    private Deur lichttrap(int x, int y, int z, int dx, int dz, int n) {
        BlockFace op = dx > 0 ? BlockFace.EAST : dx < 0 ? BlockFace.WEST : dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        Deur d = new Deur(Material.AIR.createBlockData(), Bouwer.trap(Material.SMOOTH_QUARTZ_STAIRS, op));
        for (int[] c : ZeldaRegels.trap(x, y, z, dx, dz, n, 2)) d.blok(c[0], c[1], c[2]);
        return d;
    }

    /** Een bordje met alleen een naam (staand, kijkt naar {@code kijk}). */
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

    /** Een richel of overloop langs de toren: vloer op {@code y}, een randje aan de buitenkant ({@code rand}: welke kanten). */
    private void richel(int x1, int z1, int x2, int z2, int y, boolean n, boolean o, boolean zuid, boolean west) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                zet(x, y, z, (x + z) % 5 == 0 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                vul(x, y + 1, z, x, y + 4, z, Material.AIR);
                if ((x + z) % 3 == 0) zet(x, y - 1, z, Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.NORTH, true));
            }
        for (int x = x1; x <= x2; x++) {
            if (n) zet(x, y + 1, z1 - 1, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            if (zuid) zet(x, y + 1, z2 + 1, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        }
        for (int z = z1; z <= z2; z++) {
            if (west) zet(x1 - 1, y + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            if (o) zet(x2 + 1, y + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        }
    }

    // ── het terrein: zee, twee pijlers en de torenrots ──────────────────────────────────────

    private static final byte ZEE = 0, ROTS = 1, VLAK = 2, PIJLER = 3;

    /** Hoe ver deze kolom binnen de rand van de torenrots ligt (negatief = zee). */
    static double torenRots(int x, int z) {
        double r = Math.hypot(x - CX, z - CZ);
        return 31 - r + 1.5 * Math.sin(Math.atan2(z - CZ, x - CX) * 5) + 1.0 * Math.sin(x / 3.7 + z / 4.3);
    }

    /** Ligt deze kolom in een pijler van de oude brug? */
    static boolean pijler(int x, int z) {
        if (Math.abs(z) > 2) return false;
        for (int[] p : PIJLERS) if (x >= p[0] && x <= p[1]) return true;
        return false;
    }

    /** Hoogte (bovenste blok, ten opzichte van G) en soort van een kolom; {@code P = G + 14} is het plateau. */
    static int[] vorm(int x, int z) {
        if (pijler(x, z)) return new int[]{14, PIJLER};
        if (x >= 1909 && x <= 1914 && Math.abs(z) <= 3) return new int[]{14, VLAK};      // waar de brug aankomt
        double d = torenRots(x, z);
        if (d >= 1) return new int[]{14, VLAK};
        if (d > -4) return new int[]{Math.max(-2, 14 - (int) ((1 - d) * 3.5)), ROTS};
        return new int[]{-2 - (int) Math.min(6, -d / 2.5), ZEE};
    }

    private void terrein() {
        int[][] top = new int[X2 - X1 + 1][Z2 - Z1 + 1];
        byte[][] soort = new byte[X2 - X1 + 1][Z2 - Z1 + 1];
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                int[] v = vorm(x, z);
                top[x - X1][z - Z1] = G + v[0];
                soort[x - X1][z - Z1] = (byte) v[1];
            }
        for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(X2, 16); cx++)
            for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) {
                int x1 = Math.max(X1, cx * 16), x2 = Math.min(X2, cx * 16 + 15), z1 = Math.max(Z1, cz * 16), z2 = Math.min(Z2, cz * 16 + 15);
                boolean diep = true;
                for (int x = x1; x <= x2 && diep; x++)
                    for (int z = z1; z <= z2 && diep; z++) diep = soort[x - X1][z - Z1] == ZEE && top[x - X1][z - Z1] == G - 8;
                if (diep) {
                    vul(x1, g + 1, z1, x2, G - 9, z2, Material.DEEPSLATE);
                    vul(x1, G - 8, z1, x2, G - 8, z2, Material.GRAVEL);
                    vul(x1, G - 7, z1, x2, G - 1, z2, Material.WATER);
                    vul(x1, G, z1, x2, G + 100, z2, Material.AIR);
                } else for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) kolom(x, z, top[x - X1][z - Z1], soort[x - X1][z - Z1]);
            }
    }

    private void kolom(int x, int z, int hg, byte s) {
        int ymax = G + 100;
        if (s == ZEE) {
            vul(x, g + 1, z, x, hg - 1, z, Material.DEEPSLATE);
            zet(x, hg, z, Bouwer.hash(x, z) % 3 == 0 ? Material.TUFF : Material.GRAVEL);
            vul(x, hg + 1, z, x, G - 1, z, Material.WATER);
            vul(x, G, z, x, ymax, z, Material.AIR);
            return;
        }
        vul(x, g + 1, z, x, hg - 3, z, Material.DEEPSLATE);
        for (int y = hg - 2; y <= hg; y++) zet(x, y, z, s == PIJLER && y == hg ? Material.POLISHED_BLACKSTONE_BRICKS : ZeldaEiland5a.rots(x, y, z));
        if (hg < G) vul(x, hg + 1, z, x, G - 1, z, Material.WATER);                     // een klif die onder water begint
        vul(x, Math.max(hg + 1, G), z, x, ymax, z, Material.AIR);
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        g = bouwer.g;
        G = g + 8;
        P = G + 14;
        if (morgal != null) morgal.stop();                                     // een herbouw midden in een gevecht: de oude Morgal ruimt op
        leeg(bouwer.w);
        feestOpPad = false;
        zegelOpPad = false;
        morgal = null;
        speelgebied = Box.van(X1, g - 4, Z1 + 2, X2, G + 100, Z2 - 2);
        boolean bouwen = bouwer.plaats;
        List<long[]> chunks = new ArrayList<>();
        if (bouwen) {
            for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(X2, 16); cx++)
                for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) chunks.add(new long[]{cx, cz});
            b.daarna(() -> chunks.forEach(c -> w.addPluginChunkTicket((int) c[0], (int) c[1], plugin)));
            terrein();
            toren();
        }
        aanhaken();
        torenpoort();
        nevelbrug();
        voorhof();
        torenhal();
        westtrap();
        noordrichel();
        kamertje();
        oostvuren();
        zuidpuin();
        balkon();
        troonzaal();
        dak();
        ladder();
        vangnet();
        feest();
        if (bouwen) b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
        // de begintoestand van de deuren, poorten, de nevel en de puzzels: altijd (ook zonder herbouw)
        for (Kamer k : kamers) {
            zetDeur(k.in, true);
            zetDeur(k.uit, false);
        }
        for (Barst br : barsten) zetDeur(br.muur, false);
        for (Poort po : poorten) zetDeur(po.deur, false);
        for (Nevel nv : nevels) zetDeur(nv.nevel, false);
        for (Reeks r : reeksen) {
            zetDeur(r.deur, false);
            for (int[] d : r.dingen) b.zetAltijd(d[0], d[1], d[2], r.uit);
        }
        motor();
        b = null;
    }

    /**
     * Aanhaken aan 5a (altijd, zonder herbouw: zo blijft het kloppen als 5a ooit opnieuw gebouwd wordt): de gang door de oostmuur
     * achter de torenpoort, en de brugpoort: een overdekte gang door de rots tot aan de klif (x 1859 … 1874, z −2 … 2), met muren en
     * een dak, zodat je er alleen door de torenpoort in komt (niet over de rotsen om de burcht heen).
     */
    private void aanhaken() {
        for (int x = 1859; x <= 1874; x++)
            for (int z = -3; z <= 3; z++) {
                boolean rand = Math.abs(z) == 3;
                if (x >= 1873) {                                                    // het eind van de brugpoort steekt boven zee uit
                    if (!rand) continue;
                    b.vulAltijd(x, P - 2, z, x, P + 7, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    b.zetAltijd(x, P + 8, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    continue;
                }
                if (rand) {
                    if (x >= 1863) b.vulAltijd(x, P + 1, z, x, P + 7, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    continue;
                }
                b.zetAltijd(x, P, z, Math.abs(z) <= 1 ? Material.POLISHED_BLACKSTONE_BRICKS : ZeldaEiland5a.vloer(x, z));
                int hoog = x <= 1862 ? P + 5 : P + 6;
                b.vulAltijd(x, P + 1, z, x, hoog, z, Material.AIR);
                if (x >= 1863) b.zetAltijd(x, P + 7, z, x % 4 == 0 && z == 0 ? Material.SOUL_LANTERN : Material.POLISHED_BLACKSTONE);   // het dak
            }
        for (int x = 1859; x <= 1862; x++) {                                        // een boog in de muur
            b.zetAltijd(x, P + 5, -2, Material.POLISHED_BLACKSTONE_BRICKS);
            b.zetAltijd(x, P + 5, 2, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int z = -2; z <= 2; z++) b.zetAltijd(1874, P + 7, z, Material.POLISHED_BLACKSTONE_BRICKS);   // de boog aan zee
        for (int z : new int[]{-3, 3}) b.zetAltijd(1874, P + 9, z, Material.SOUL_LANTERN);
    }

    // ── 1. de torenpoort: Tijmen breekt het zegel ───────────────────────────────────────────

    private void torenpoort() {
        if (muren != null && muren.torenTijmen != null) {
            praten(muren.torenTijmen);
            zegelOpPad = true;
        }
        poort(new Poort("torenpoort", deur(Material.CRYING_OBSIDIAN.createBlockData(), 1858, P + 1, -2, 1858, P + 7, 2), Box.van(1848, P, -3, 1866, P + 8, 3),
                s -> s.heeft(ZEGEL)));
    }

    // ── 2. de nevelbrug: drie stukken brug in de nevel, boven zee ───────────────────────────

    private void nevelbrug() {
        if (b.plaats)
            for (int[] p : PIJLERS) {                                               // de pijlers: een lantaarn van de wachters erop
                int m = (p[0] + p[1]) / 2;
                for (int z : new int[]{-2, 2}) {
                    vul(m, P + 1, z, m, P + 2, z, Material.QUARTZ_PILLAR);
                    zet(m, P + 3, z, Material.SOUL_LANTERN);
                }
                for (int x = p[0]; x <= p[1]; x++) for (int z = -2; z <= 2; z++) if (Math.abs(z) == 2 && x != m) zet(x, P + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            }
        Verhaal uitleg = verhaal(new Verhaal("uitleg-brug", Box.van(1866, P, -2, 1872, P + 5, 2), List.of(
                r("Wiske", "De brug is weg… Nee, kijk: nevel boven het water."),
                r("Wiske", "Daar zit een brug in! Schiet er een lichtpijl doorheen."))));
        uitleg.uitleg = true;
        uitleg.als = s -> !s.heeft("n:brug1");
        double[] voor = {1871, BRUG[1][0] - 2, BRUG[2][0] - 2};
        for (int i = 0; i < BRUG.length; i++) {
            Deur d = new Deur(Material.AIR.createBlockData(), Material.SMOOTH_QUARTZ.createBlockData()).vlak(BRUG[i][0], P, -1, BRUG[i][1], P, 1);
            Nevel nv = nevelOpPad(new Nevel("brug" + (i + 1), d, Box.van(BRUG[i][0] - 9, P - 1, -4, BRUG[i][1] + 4, P + 6, 4)));
            nv.brug = true;
            nv.voor = plek(voor[i], P + 1, 0, -90f);
            nv.binnen = plek(voor[i], P + 1, 0, -90f);
        }
    }

    // ── 3. de voorhof: aankomst op de torenrots ─────────────────────────────────────────────

    private void voorhof() {
        if (b.plaats) {
            for (int x = 1909; x <= 1927; x++)
                for (int z = -7; z <= 7; z++) if (Math.abs(z) <= 1 || Math.hypot(x - 1918, z) < 7.5) zet(x, P, z, ZeldaEiland5a.vloer(x, z));
            paalbord(1912, P + 1, 3, BlockFace.WEST, "De Nevelbrug");
            paalbord(1925, P + 1, -3, BlockFace.EAST, "De toren");
            for (int[] c : new int[][]{{1911, -4}, {1911, 4}, {1925, -6}, {1925, 6}}) {
                zet(c[0], P + 1, c[1], Material.POLISHED_BLACKSTONE_BRICK_WALL);
                zet(c[0], P + 2, c[1], Material.SOUL_LANTERN);
            }
        }
        rustvuur(1916, P, -4);
        rust(new Rust("voorhof", "De voorhof", plek(1918, P + 1, -2, 90f), Box.van(1910, P, -7, 1926, P + 5, 7), List.of()));
        bron("bron-voorhof", plek(1918, P + 1, 4, 0f));
        verhaal(new Verhaal("voorhof", Box.van(1910, P, -6, 1926, P + 5, 6), List.of(
                r("Wiske", "De toren van Morgal. Wat is hij hoog!"),
                r("Wiske", "Er loopt een trap omheen. Maar ook in de nevel…"))));
    }

    // ── 4. de toren: muren, de torenhal, de troonzaal en het dak (alleen blokken) ───────────

    /** De toren: vierkant (x 1930 … 1950, z −10 … 10), twee dik, van het plateau tot het dak (P + 53), met kantelen en vier spitsen. */
    private void toren() {
        for (int x = CX - 10; x <= CX + 10; x++)
            for (int z = CZ - 10; z <= CZ + 10; z++) {
                boolean rand = Math.abs(x - CX) >= 9 || Math.abs(z - CZ) >= 9;
                if (rand) {
                    for (int y = P + 1; y <= P + 53; y++) {
                        boolean raam = (y - P) % 12 == 6 && (Math.abs(x - CX) == 4 || Math.abs(z - CZ) == 4) && (Math.abs(x - CX) >= 9) != (Math.abs(z - CZ) >= 9);
                        zet(x, y, z, raam ? Material.PURPLE_STAINED_GLASS : ZeldaEiland5a.zwart(x, y, z));
                    }
                    boolean buiten = Math.abs(x - CX) == 10 || Math.abs(z - CZ) == 10;
                    if (buiten && (x + z) % 2 == 0) zet(x, P + 54, z, ZeldaEiland5a.zwart(x, P + 54, z));
                    else if (buiten) zet(x, P + 54, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    continue;
                }
                zet(x, P, z, (x - CX) * (x - CX) + (z - CZ) * (z - CZ) < 10 ? Material.CRYING_OBSIDIAN : ZeldaEiland5a.vloer(x, z));
                vul(x, P + 1, z, x, P + 8, z, Material.AIR);
                vul(x, P + 9, z, x, P + 39, z, Material.POLISHED_BLACKSTONE);
                zet(x, P + 40, z, (x + z) % 2 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_DEEPSLATE);
                vul(x, P + 41, z, x, P + 52, z, Material.AIR);
                zet(x, P + 53, z, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        for (int[] c : new int[][]{{-10, -10}, {10, -10}, {-10, 10}, {10, 10}}) {     // vier spitsen op de hoeken
            vul(CX + c[0], P + 54, CZ + c[1], CX + c[0], P + 58, CZ + c[1], Material.POLISHED_BLACKSTONE_BRICKS);
            zet(CX + c[0], P + 59, CZ + c[1], Material.CRYING_OBSIDIAN);
        }
        // de deur van de torenhal (west), met banieren
        vul(CX - 10, P + 1, -1, CX - 9, P + 3, 1, Material.AIR);
        for (int z : new int[]{-2, 2}) zet(CX - 11, P + 4, z, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.WEST));
    }

    // ── 5. de torenhal: Ida, een bron, en onder de vloer Morgals schatkamer ─────────────────

    private void torenhal() {
        if (b.plaats) {
            for (int[] c : new int[][]{{CX - 6, -6}, {CX - 6, 6}, {CX + 6, -6}, {CX + 6, 6}}) {
                vul(c[0], P + 1, c[1], c[0], P + 8, c[1], Material.POLISHED_BLACKSTONE_BRICKS);
                zet(c[0], P + 7, c[1] + (c[1] < 0 ? 1 : -1), Material.SOUL_LANTERN);
            }
            for (int z = -6; z <= 6; z += 4) zet(CX + 8, P + 4, z, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.WEST));
            zet(CX - 7, P + 1, -7, Material.BARREL);
            zet(CX - 7, P + 1, 7, Material.BARREL);
            // de trap omlaag naar de schatkamer (oost, z −1 … 1) en de schatkamer zelf (x 1933 … 1939, z −5 … 5)
            for (int x = 1941; x <= 1946; x++)
                for (int z = -1; z <= 1; z++) {
                    int y = P - (1947 - x);
                    zet(x, y, z, Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST));
                    vul(x, y + 1, z, x, P, z, Material.AIR);
                }
            for (int x = 1941; x <= 1946; x++) {
                vul(x, P + 1, -2, x, P + 2, -2, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                vul(x, P + 1, 2, x, P + 2, 2, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            }
            vul(1940, P + 1, -1, 1940, P + 2, 1, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            for (int x = 1932; x <= 1940; x++)
                for (int z = -6; z <= 6; z++) {
                    boolean rand = x == 1932 || x == 1940 || Math.abs(z) == 6;
                    zet(x, P - 6, z, rand ? Material.POLISHED_BLACKSTONE_BRICKS : (x + z) % 3 == 0 ? Material.GILDED_BLACKSTONE : Material.POLISHED_DEEPSLATE);
                    for (int y = P - 5; y <= P - 1; y++) zet(x, y, z, rand ? ZeldaEiland5a.zwart(x, y, z) : Material.AIR);
                }
            vul(1940, P - 5, -1, 1940, P - 3, 1, Material.AIR);                     // de deur (een hek, de poort)
            for (int[] c : new int[][]{{1933, -5}, {1934, -5}, {1933, 5}, {1935, 5}, {1933, -4}}) zet(c[0], P - 5, c[1], Material.GOLD_BLOCK);
            zet(1933, P - 4, -5, Material.GOLD_BLOCK);
            for (int z : new int[]{-3, 3}) zet(1933, P - 5, z, Material.CHEST);
            vul(1935, P - 5, -1, 1935, P - 5, 1, Material.POLISHED_BLACKSTONE_BRICKS);  // twee sokkels
            zet(1936, P - 1, 0, Bouwer.hangend(Material.SOUL_LANTERN));
        }
        bron("bron-torenhal", plek(CX - 4, P + 1, 4, 0f));
        Persoon ida = persoon(new Persoon("Ida", plek(CX - 5, P + 1, -4, 90f), beroep("cartographer"), List.of(
                r("Ida", "Ik klim alle bergen. Deze toren nog niet."))));
        ida.streek = Villager.Type.SNOW;
        ida.eenmalig("ida-toren", s -> !s.heeft(VRIJ_ZELDA), List.of(
                        r("Ida", "Hoi! Ik klom de rots op, vanuit zee."),
                        r("Ida", "Binnen kun je niet omhoog. De trap is buiten."),
                        r("Ida", "Maar die zit in de nevel. Brr.")), null)
                .eenmalig("ida-dag", s -> s.heeft(VRIJ_ZELDA), List.of(
                        r("Ida", "De zon! Nu durf ik wel naar het dak.")), null);
        verhaal(new Verhaal("torenhal", Box.van(CX - 8, P, -8, CX + 2, P + 5, 8), List.of(
                r("Wiske", "Morgals hal. Leeg… en geen trap omhoog."),
                r("Wiske", "Die trap omlaag? Daar glimt iets.")))).als = s -> !s.heeft(VRIJ_ZELDA);
        // Morgals schatkamer: dicht tot Zelda erom vraagt (na het feest)
        poorten.add(new Poort("schatkamer", deur(ZeldaEiland5a.tralies(true), 1940, P - 5, -1, 1940, P - 3, 1), Box.van(1938, P - 6, -3, 1947, P + 1, 3),
                s -> s.missie(ZeldaFeest.SCHAT) >= 1));
        verhaal(new Verhaal("schatkamer-dicht", Box.van(1941, P - 6, -1, 1944, P - 2, 1), List.of(
                r("Wiske", "Goud! Morgals schatkamer. Op slot.")))).als = s -> s.missie(ZeldaFeest.SCHAT) == 0;
        Vondst kroon = vondst(new Vondst(KROONTJE, "Zelda's kroontje", "breng het naar Zelda", Material.GOLDEN_HELMET, plek(1935, P - 3.6, 0, 0f),
                Box.van(1934, P - 5, -1, 1936, P - 3, 1)));
        kroon.vereist = "g:zelda-schat";
        kroon.daarna = (spel, p, s) -> {
            if (s.missie(ZeldaFeest.SCHAT) == 1) spel.missie(p, ZeldaFeest.SCHAT, 2);
            spel.vertel(p, r("Wiske", "Haar kroontje! Gauw terug naar Zelda."));
        };
        Vondst stukje = vondst(new Vondst(STUKJE_SCHAT, "Hartstukje", null, Material.RED_DYE, plek(1936, P - 4.6, -4, 0f), Box.van(1935, P - 5, -5, 1937, P - 3, -3)));
        stukje.vereist = "g:zelda-schat";
    }

    // ── 6. de torentrap, west: een trap van licht in de nevel ───────────────────────────────

    private void westtrap() {
        if (b.plaats) {
            richel(1928, -10, 1929, 0, P + 10, false, false, false, true);           // de richel langs de westmuur
            richel(1924, -16, 1929, -11, P + 10, true, false, false, true);          // de overloop op de hoek (noordwest)
            zet(1925, P + 11, -15, Material.SOUL_LANTERN);
        }
        Nevel trap = nevelOpPad(new Nevel("trap1", lichttrap(1928, P + 1, 10, 0, -1, 10), Box.van(1924, P, -1, 1934, P + 12, 17)));
        trap.brug = true;
        trap.voor = plek(1928, P + 1, 14, 180f);
        verhaal(new Verhaal("trap1", Box.van(1925, P, 11, 1933, P + 4, 16), List.of(
                r("Wiske", "Hier begint de trap. In de nevel, natuurlijk.")))).als = s -> !s.heeft("n:trap1");
    }

    // ── 7. noord: de ingestorte trap (klauwhaak), de richel en Zelda's kamertje ─────────────

    private void noordrichel() {
        if (b.plaats) {
            for (int[] c : ZeldaRegels.trap(1930, P + 11, -12, 1, 0, 4, 2)) {            // de oude trap, tot hij ophoudt
                zet(c[0], c[1], c[2], Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST));
                vul(c[0], c[1] + 1, c[2], c[0], c[1] + 4, c[2], Material.AIR);
                zet(c[0], c[1] - 1, c[2], Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.WEST, true));
            }
            zet(1934, P + 13, -12, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS);        // waar hij brak
            richel(1941, -12, 1950, -11, P + 20, true, false, false, false);
            richel(1951, -16, 1956, -11, P + 20, true, true, false, false);          // de overloop (noordoost)
            zet(1941, P + 26, -11, ZeldaHoofdstuk.HAAKPUNT);                           // hoog: alleen vanaf de ingestorte trap te raken
            zet(1955, P + 21, -15, Material.SOUL_LANTERN);
        }
        Haakpunt hp = haakpunt(new Haakpunt("torenrichel", 1941, P + 26, -11, plek(1942, P + 21, -12, 90f)));
        verhaal(new Verhaal("ingestort", Box.van(1929, P + 10, -16, 1934, P + 16, -11), List.of(
                r("Wiske", "De trap is ingestort! Te ver om te springen."),
                r("Wiske", "Daar, boven de richel: een haakpunt.")))).als = s -> !s.heeft("v:noordrichel");
        haak(hp, plek(1933, P + 15, -12, -90f));
        verhaal(new Verhaal("noordrichel", Box.van(1941, P + 20, -12, 1956, P + 24, -11), List.of(
                r("Wiske", "Gelukt! Kijk, een deurtje in de muur."))));
    }

    /** Zelda's kamertje (achter het deurtje op de noordrichel): haar bed, haar tekeningen, een bron. */
    private void kamertje() {
        if (b.plaats) {
            for (int x = 1942; x <= 1948; x++)
                for (int z = -9; z <= -3; z++) {
                    boolean rand = x == 1942 || z == -3;
                    zet(x, P + 20, z, rand ? Material.POLISHED_BLACKSTONE : (x + z) % 2 == 0 ? Material.CALCITE : Material.SMOOTH_QUARTZ);
                    for (int y = P + 21; y <= P + 24; y++) zet(x, y, z, rand ? Material.POLISHED_BLACKSTONE : Material.AIR);
                    zet(x, P + 25, z, Material.POLISHED_BLACKSTONE);
                }
            vul(1945, P + 21, -10, 1946, P + 23, -9, Material.AIR);                  // het deurtje (in de noordmuur)
            zet(1948, P + 22, -10, Material.PURPLE_STAINED_GLASS);                   // haar raam: daar brandt licht
            zet(1948, P + 22, -9, Material.PURPLE_STAINED_GLASS);
            zet(1947, P + 24, -8, Material.SHROOMLIGHT);
            BlockData voet = Material.RED_BED.createBlockData(), hoofd = Material.RED_BED.createBlockData();
            if (voet instanceof org.bukkit.block.data.type.Bed bv && hoofd instanceof org.bukkit.block.data.type.Bed bh) {
                bv.setFacing(BlockFace.EAST);
                bv.setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
                bh.setFacing(BlockFace.EAST);
                bh.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
            }
            zet(1947, P + 21, -4, voet);
            zet(1948, P + 21, -4, hoofd);
            zet(1943, P + 21, -8, Material.LECTERN);
            zet(1943, P + 21, -4, Material.BOOKSHELF);
            zet(1944, P + 21, -4, Material.FLOWER_POT);
            zet(1944, P + 23, -4, Bouwer.gericht(Material.WHITE_WALL_BANNER, BlockFace.NORTH));
        }
        bron("bron-kamertje", plek(1945, P + 21, -6, 0f));
        verhaal(new Verhaal("kamertje", Box.van(1943, P + 20, -8, 1948, P + 24, -4), List.of(
                r("Wiske", "Hier sliep Zelda. Kijk, haar tekeningen."),
                r("Wiske", "Drie bakens en een fee… Ze wist dat we kwamen!"))));
    }

    // ── 8. oost: drie wachtersvuren (vuurstaf), dan verschijnt de trap ──────────────────────

    /** De drie wachtersvuren naast de oosttrap: {x, y (de schaal), z}. */
    private int[][] vuren = new int[0][];

    private void oostvuren() {
        vuren = new int[][]{{1956, P + 23, -8}, {1956, P + 26, -5}, {1956, P + 29, -2}};
        if (b.plaats) {
            for (int[] v : vuren) {
                vul(v[0], P + 1, v[2], v[0], v[1] - 2, v[2], Material.QUARTZ_PILLAR);
                zet(v[0], v[1] - 1, v[2], Material.CHISELED_QUARTZ_BLOCK);
            }
            richel(1951, 0, 1952, 10, P + 30, false, true, false, false);
            richel(1951, 11, 1956, 16, P + 30, false, true, true, false);           // de overloop (zuidoost)
            zet(1955, P + 31, 15, Material.SOUL_LANTERN);
        }
        Verhaal uitleg = verhaal(new Verhaal("uitleg-vuren", Box.van(1951, P + 20, -16, 1956, P + 24, -11), List.of(
                r("Wiske", "Drie koude vuren, naast de muur."),
                r("Wiske", "Snel alle drie aan, met je vuurstaf!"))));
        uitleg.uitleg = true;
        uitleg.als = s -> !s.heeft("r:torenvuren");
        Reeks reeks = reeks(new Reeks("torenvuren", "De wachtersvuren", Reeks.Hoe.VUUR, ZeldaEiland4a.vuur(Material.CAMPFIRE, false), ZeldaEiland4a.vuur(Material.CAMPFIRE, true),
                lichttrap(1952, P + 21, -10, 0, 1, 10), Box.van(1947, P + 19, -17, 1958, P + 32, 2), 20));
        for (int[] v : vuren) reeks.dingen.add(new int[]{v[0], v[1], v[2]});
        reeks.voor = plek(1953, P + 21, -13, 0f);
        reeks.bijOplossen.add(r("Wiske", "Een trap van licht! Gauw, naar boven."));
    }

    // ── 9. zuid: puin op de richel (een bom), en weer een trap van licht ────────────────────

    private void zuidpuin() {
        if (b.plaats) {
            richel(1947, 11, 1950, 12, P + 30, false, false, true, false);
            richel(1930, 11, 1936, 12, P + 40, false, false, true, false);
        }
        Barst puin = barstOpPad(new Barst("torenpuin", deur(ZeldaHoofdstuk.BARST.createBlockData(), 1949, P + 31, 11, 1950, P + 33, 12),
                Box.van(1945, P + 30, 9, 1957, P + 35, 17)));
        puin.voor = plek(1953, P + 31, 13, 90f);
        verhaal(new Verhaal("puin", Box.van(1951, P + 30, 11, 1956, P + 34, 16), List.of(
                r("Wiske", "Een stuk muur ligt op de richel."),
                r("Wiske", "Dat puin is gebarsten. Een bom!")))).als = s -> !s.heeft("b:torenpuin");
        Nevel trap = nevelOpPad(new Nevel("trap2", lichttrap(1946, P + 31, 12, -1, 0, 10), Box.van(1934, P + 29, 8, 1951, P + 42, 15)));
        trap.brug = true;
        trap.voor = plek(1947, P + 31, 11, 90f);
    }

    // ── 10. het torenbalkon: de laatste rust, Tijmen ────────────────────────────────────────

    private void balkon() {
        if (b.plaats) {
            richel(1920, 9, 1929, 17, P + 40, true, false, true, true);
            vul(1921, P + 41, 18, 1921, P + 41, 18, Material.AIR);
            zet(1921, P + 41, 17, Material.AIR);
            for (int[] c : new int[][]{{1920, 9}, {1920, 17}, {1929, 17}}) zet(c[0], P + 41, c[1], Material.POLISHED_BLACKSTONE_BRICK_WALL);
            zet(1920, P + 42, 9, Material.SOUL_LANTERN);
            zet(1929, P + 42, 17, Material.SOUL_LANTERN);
            for (int z = 9; z <= 17; z += 4) zet(1919, P + 39, z, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.WEST));
        }
        rustvuur(1924, P + 40, 12);
        rust(new Rust("balkon", "Het torenbalkon", plek(1926, P + 41, 11, 90f), Box.van(1921, P + 40, 9, 1937, P + 45, 17), List.of()));   // ook de richel voor de deur
        bron("bron-balkon", plek(1923, P + 41, 15, 0f));
        verhaal(new Verhaal("balkon", Box.van(1921, P + 40, 9, 1936, P + 45, 17), List.of(
                r("Wiske", "Het torenbalkon. Wat ben je hoog!"),
                r("Wiske", "Rust eerst uit. Daarbinnen is Morgal."))));
        Persoon tijmen = persoon(new Persoon("Tijmen", plek(1927, P + 41, 15, 180f), null, List.of(r("Tijmen", "Rust maar. Het vuur houdt de nevel weg."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.eenmalig("tijmen-balkon", s -> !s.heeft("k:nachttroon"), List.of(
                        r("Wiske", "Tijmen! Hoe kom jij nou hier boven?"),
                        r("Tijmen", "Met de sterren. Luister: Morgal gooit bollen van nevel."),
                        r("Tijmen", "Sla ze terug met je zwaard."),
                        r("Tijmen", "En zijn hart? Daar helpt alleen licht.")), null)
                .eenmalig("tijmen-dag", s -> s.heeft(VRIJ_ZELDA), List.of(
                        r("Tijmen", "De zon. Na zo veel nachten."),
                        r("Tijmen", "Ga maar naar huis. Ik kom ook, naar het feest.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
        praten(tijmen);
    }

    // ── 11. de troonzaal: Morgal ────────────────────────────────────────────────────────────

    private void troonzaal() {
        if (b.plaats) {
            for (int x = 1944; x <= 1948; x++)                                        // de verhoging voor de troon
                for (int z = -3; z <= 3; z++) zet(x, P + 41, z, x == 1944 ? Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST) : Material.POLISHED_BLACKSTONE_BRICKS.createBlockData());
            for (int z = -1; z <= 1; z++) {                                           // de troon
                zet(1948, P + 42, z, Material.POLISHED_BLACKSTONE_BRICKS);
                vul(1948, P + 43, z, 1948, P + 45, z, z == 0 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS);
            }
            zet(1947, P + 42, 0, Bouwer.trap(Material.POLISHED_BLACKSTONE_STAIRS, BlockFace.EAST));
            for (int[] c : new int[][]{{1935, -5}, {1935, 5}, {1941, -5}, {1941, 5}}) {   // vier zuilen
                vul(c[0], P + 41, c[1], c[0], P + 52, c[1], Material.POLISHED_BLACKSTONE_BRICKS);
                zet(c[0], P + 47, c[1] + (c[1] < 0 ? 1 : -1), Material.SOUL_LANTERN);
            }
            for (int x = 1934; x <= 1946; x += 4) {
                zet(x, P + 47, -8, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.SOUTH));
                zet(x, P + 47, 8, Bouwer.gericht(Material.PURPLE_WALL_BANNER, BlockFace.NORTH));
            }
            vul(1932, P + 41, 10, 1934, P + 44, 10, Material.AIR);                   // de deur in de zuidmuur (vanaf de richel)
            for (int y = P + 41; y <= P + 52; y++) zet(1932, y, -8, Bouwer.gericht(Material.LADDER, BlockFace.EAST));   // de ladder naar het dak
        }
        Kamer k = kamer(new Kamer("nachttroon", "De troonzaal", Box.van(1932, P + 40, -8, 1948, P + 53, 8), Box.van(1933, P + 41, -7, 1947, P + 48, 6),
                deur(ZeldaEiland5a.tralies(false), 1932, P + 41, 9, 1934, P + 44, 9),
                new Deur(Material.POLISHED_BLACKSTONE_BRICKS.createBlockData(), Bouwer.gericht(Material.LADDER, BlockFace.EAST)).blok(1932, P + 53, -8)));
        k.muziek = ZeldaMuziek.MORGAL1;
        k.hemel = 18000;
        List<Location> geesten = new ArrayList<>();
        for (int[] sp : new int[][]{{1934, -7}, {1934, 7}, {1938, -7}, {1938, 7}, {1946, -6}, {1946, 6}}) {
            k.spawns.add(plek(sp[0], P + 41, sp[1], 0f));
            geesten.add(plek(sp[0], P + 42, sp[1], 0f));
        }
        morgal = new Morgal(plugin, "Morgal", plek(1945, P + 46, 0, 90f),
                List.of(plek(1945, P + 42, -2, 90f), plek(1946, P + 42, 0, 90f), plek(1945, P + 42, 2, 90f)),
                plek(1939, P + 46, 0, 90f), plek(1939, P + 41, 0, 90f), geesten,
                List.of(r("Wiske", "Hij is weg… verdwenen in zijn eigen nevel!"),
                        r("Wiske", "Hoor je dat? Boven ons, op het dak!")));
        k.baas = morgal;
        k.golf(Soort.MORGAL);
        k.bijStart.add(r("Morgal", "Een kind en een vonkje. In mijn toren?"));
        k.bijStart.add(r("Wiske", "Zijn nevel beschermt hem. Eerst zijn geesten!"));
        k.tips.addAll(List.of(r("Wiske", "Een bol vlakbij? Zwaai met je zwaard!"),
                r("Wiske", "Zijn nevelhart? Schiet lichtpijlen, vak 7.")));
    }

    // ── 12. het dak: Zelda in een kooi van nevel ────────────────────────────────────────────

    private void dak() {
        Deur kooi = new Deur(Material.TINTED_GLASS.createBlockData(), Material.AIR.createBlockData());
        for (int x = CX - 1; x <= CX + 1; x++)
            for (int z = CZ - 1; z <= CZ + 1; z++) {
                if (x != CX || z != CZ) for (int y = P + 54; y <= P + 56; y++) kooi.blok(x, y, z);
                kooi.blok(x, P + 57, z);
            }
        if (b.plaats) for (int[] c : new int[][]{{CX - 2, CZ - 2}, {CX + 2, CZ - 2}, {CX - 2, CZ + 2}, {CX + 2, CZ + 2}}) {
            zet(c[0], P + 54, c[1], Material.POLISHED_BLACKSTONE_BRICK_WALL);
            zet(c[0], P + 55, c[1], Material.SOUL_LANTERN);
        }
        poort(new Poort("kooi", kooi, Box.van(CX - 10, P + 53, CZ - 10, CX + 10, P + 58, CZ + 10), s -> s.heeft("k:nachttroon")));
        Persoon zelda = persoon(new Persoon("Zelda", plek(CX, P + 54, CZ, 90f), null, List.of(r("Zelda", "Help! Haal me hieruit!"))));
        zelda.soort = EntityType.MANNEQUIN;
        zelda.uiterlijk = e -> ZeldaEiland5b.zelda(e, false);
        zelda.zichtbaar = s -> !s.heeft(VRIJ_ZELDA);
        zelda.gesprek("zelda-vrij", s -> s.heeft("k:nachttroon") && !s.heeft(VRIJ_ZELDA), List.of(
                r("Zelda", "Je bent gekomen! En Wiske ook."),
                r("Zelda", "Morgal is weg. Kijk, de zon komt op!"),
                r("Zelda", "Ik ga vooruit, naar Brecht. Tot zo!")), (spel, p, s) -> {
            if (s.zet(VRIJ_ZELDA)) spel.voortgang.vuil();
            spel.muziek.speel(p, ZeldaMuziek.DAGERAAD);
            p.getWorld().spawnParticle(Particle.END_ROD, plek(CX, P + 55, CZ, 0f), 50, 0.4, 0.8, 0.4, 0.08);
        });
        praten(zelda);
        Verhaal einde = verhaal(new Verhaal("dageraad", Box.van(CX - 4, P + 53, CZ - 4, CX + 4, P + 58, CZ + 4), List.of(
                r("Wiske", "Zelda is vrij! We hebben het gedaan."),
                r(null, "Nachtrots · de nacht is voorbij"))));
        einde.vereist = VRIJ_ZELDA;
        einde.einde = true;
    }

    /**
     * Zo ziet Zelda eruit (een mannequin: zoals een speler): een witte jurk met goud, en een kroontje ({@code kroon}; op het feest
     * pas als je het terugbracht, zie {@link ZeldaFeest}).
     */
    static void zelda(org.bukkit.entity.Entity e, boolean kroon) {
        if (!(e instanceof org.bukkit.entity.Mannequin m)) return;
        m.setImmovable(true);
        m.setDescription(Component.empty());
        var eq = m.getEquipment();
        eq.setChestplate(kleur(Material.LEATHER_CHESTPLATE, 0xf4f0ff, true));
        eq.setLeggings(kleur(Material.LEATHER_LEGGINGS, 0xe9e2ff, false));
        eq.setBoots(kleur(Material.LEATHER_BOOTS, 0xd4af37, false));
        eq.setHelmet(kroon ? new org.bukkit.inventory.ItemStack(Material.GOLDEN_HELMET) : null);
        eq.setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.END_ROD));
    }

    private static org.bukkit.inventory.ItemStack kleur(Material m, int rgb, boolean goud) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(m);
        if (it.getItemMeta() instanceof org.bukkit.inventory.meta.ColorableArmorMeta lm) {
            lm.setColor(org.bukkit.Color.fromRGB(rgb));
            if (goud) lm.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(org.bukkit.inventory.meta.trim.TrimMaterial.GOLD, org.bukkit.inventory.meta.trim.TrimPattern.SPIRE));
            it.setItemMeta(lm);
        }
        return it;
    }

    // ── 13. de ladder: de korte weg terug naar de voorhof (het hek gaat alleen open voor wie boven was) ─

    private void ladder() {
        if (b.plaats) {
            vul(1921, P + 1, 19, 1921, P + 41, 19, Material.POLISHED_BLACKSTONE_BRICKS);
            for (int y = P + 1; y <= P + 40; y++) zet(1921, y, 18, Bouwer.gericht(Material.LADDER, BlockFace.NORTH));
            zet(1921, P + 42, 19, Material.SOUL_LANTERN);
        }
        Deur hek = new Deur(Material.IRON_BARS.createBlockData(), Material.AIR.createBlockData())
                .vlak(1920, P + 1, 18, 1920, P + 3, 18).vlak(1922, P + 1, 18, 1922, P + 3, 18).vlak(1921, P + 1, 17, 1921, P + 3, 17);
        poort(new Poort("torenladder", hek, Box.van(1917, P, 14, 1925, P + 43, 22), s -> s.bezocht(id(), 1)));
        verhaal(new Verhaal("ladder-dicht", Box.van(1918, P, 14, 1924, P + 4, 16), List.of(
                r("Wiske", "Een ladder, helemaal naar boven. Het hek zit dicht.")))).als = s -> !s.bezocht(id(), 1);
    }

    // ── 14. het vangnet: wie valt, vangt de nevel op ────────────────────────────────────────

    private void vangnet() {
        val(new Val("toren", Box.van(1866, G - 10, -40, 1976, P + 72, 40), G, plek(1925, P + 24, 24, 0f)));
    }

    // ── 15. het feest (op het Lindewoud): Zelda daar staat op het pad ───────────────────────

    private void feest() {
        if (lindewoud != null && lindewoud.onderdeel("Zelda") instanceof Persoon zelda) {
            praten(zelda);
            feestOpPad = true;
        }
    }

    /**
     * Zonder blokken: de doelen voor {@code /hint} en Tijmen, na die van 5a. Tijmen breekt het zegel, de nevelbrug, de trap om de
     * toren (licht, klauwhaak, vuurstaf, een bom, licht), Morgal, Zelda op het dak, en naar huis (het feest).
     */
    private void motor() {
        Location tijmen = muren != null && muren.torenTijmen != null ? muren.torenTijmen.plek : plek(1852, P + 1, 2, -90f);
        doel("zegel", "Tijmen staat bij de torenpoort.", "Praat met Tijmen. Hij kent een spreuk.", tijmen, s -> s.heeft(ZEGEL));
        doel("nevelbrug", "Achter de poort hangt nevel boven het water.", "Schiet een lichtpijl door de nevel. Daar zit een brug.",
                plekVan("brug1"), s -> s.bezocht(id(), 0));
        doel("trap1", "Om de toren loopt een trap. In de nevel.", "Links van de deur: een lichtpijl langs de muur, omhoog.",
                plekVan("trap1"), s -> s.heeft("n:trap1") || s.heeft("v:noordrichel") || s.bezocht(id(), 1));
        doel("richel", "De trap is ingestort.", "Boven de richel zit een haakpunt. Je klauwhaak!",
                plekVan("ingestort"), s -> s.heeft("v:noordrichel") || s.heeft("r:torenvuren") || s.bezocht(id(), 1));
        doel("torenvuren", "Drie koude vuren naast de trap.", "Steek ze snel aan met je vuurstaf.",
                plekVan("torenvuren"), s -> s.heeft("r:torenvuren") || s.heeft("b:torenpuin") || s.bezocht(id(), 1));
        doel("torenpuin", "Puin op de richel.", "Dat puin is gebarsten. Een bom!",
                plekVan("torenpuin"), s -> s.heeft("b:torenpuin") || s.bezocht(id(), 1));
        doel("trap2", "Weer nevel langs de muur.", "Schiet een lichtpijl de trap op.",
                plekVan("trap2"), s -> s.bezocht(id(), 1));
        doel("morgal", "Morgal wacht in zijn troonzaal.", "Sla zijn bollen terug. En licht in zijn hart.",
                plekVan("nachttroon"), s -> s.heeft("k:nachttroon"));
        doel("zelda", "Boven op het dak: Zelda!", "De ladder achter in de troonzaal, naar het dak.",
                plekVan("Zelda"), s -> s.heeft(VRIJ_ZELDA));
        Location brecht = muren != null && muren.onderdeel("Brecht") instanceof Persoon pb ? pb.plek : rust("voorhof").plek;
        doel("thuis", "Naar huis! Iedereen wacht.", "Brecht ligt in de kreek. Of vaar met je kaart (vak 9).",
                brecht, s -> s.heeft("zelda"));
    }

    // ── de terugvaart ───────────────────────────────────────────────────────────────────────

    /** De eerste keer naar het Lindewoud nadat Zelda vrij is: Zelda vaart mee (één keer). */
    @Override
    List<Regel> vaart(ZeldaVoortgang.Stand s, ZeldaEiland naar) {
        if (!naar.id().equals("lindewoud") || !s.heeft(VRIJ_ZELDA) || s.heeft("zelda") || !s.zet(TERUGVAART)) return null;
        return List.of(r("Brecht", "Daar gaan we. Naar huis!"),
                r("Zelda", "Kijk, de bakens. Ze branden alle drie."),
                r("Wiske", "En ik ben nog nooit zo fel geweest!"),
                r("Zelda", "Op het Lindewoud wacht een verrassing."));
    }

    // ── tijdens het spel: nevel om de toren (tot Morgal weg is), dan licht ──────────────────

    @Override
    void tik(ZeldaSpel spel, List<Player> wie, long nu) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        Particle.DustOptions nevel = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x15121c), 4f);
        for (Player p : wie) {
            if (!p.getWorld().equals(w) || spel.beheer.is(p.getUniqueId())) continue;
            ZeldaVoortgang.Stand s = spel.voortgang.van(p.getUniqueId());
            if (!s.heeft("k:nachttroon")) {
                for (int i = 0; i < 6; i++) {                                           // Morgals nevel om de toren (buiten de trap)
                    double a = r.nextDouble() * Math.PI * 2, rr = 19 + r.nextDouble() * 6;
                    spel.deeltje(p, Particle.DUST, new Location(w, CX + 0.5 + Math.cos(a) * rr, P + 4 + r.nextDouble() * 54, CZ + 0.5 + Math.sin(a) * rr), 1, 1.5, 2, 1.5, nevel);
                }
                if (r.nextInt(30) == 0) p.playSound(new Location(w, CX, P + 46, CZ), org.bukkit.Sound.ENTITY_EVOKER_AMBIENT, org.bukkit.SoundCategory.HOSTILE, 0.5f, 0.5f);
            } else if (s.heeft(VRIJ_ZELDA) && r.nextInt(2) == 0)                       // licht om de toren: de nacht is voorbij
                spel.deeltje(p, Particle.END_ROD, new Location(w, CX + 0.5 + r.nextDouble(-14, 14), P + 30 + r.nextDouble() * 30, CZ + 0.5 + r.nextDouble(-14, 14)), 2, 0.4, 0.4, 0.4, null);
        }
    }

    /** Op de kaart: Morgals toren. */
    @Override
    List<Teken> tekens() {
        return List.of(new Teken("toren", 1940, 0));
    }
}
