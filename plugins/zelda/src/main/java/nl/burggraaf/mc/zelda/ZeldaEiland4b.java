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
 * Eiland 4b, «De Gloedberg»: de Smidse (zie ZELDA.md). Achter de Smidsepoort van 4a ligt de Smidse van de eerste smeden,
 * diep in de vulkaan. De Smidsehal is de hub van de kerker: Tijmen leest er het oude schrift boven de Hartdeur («drie
 * vonken wekken het Hart»), en op het grote aambeeld ligt een kapot schild dat Okker in Sintelgat tot het
 * <b>Gloedschild</b> smeedt (terug naar de hub; vuur ketst erop af).
 *
 * <p>De drie vonken haal je in elke volgorde, in een rondje van zalen: de <b>Kettingzaal</b> (een kloof vol lava: haak je
 * naar de pijler met de vonk en naar de overkant, en brand met de vuurstaf het touw door: de kettingbrug valt), de
 * <b>Stuwdam</b> (een bom tegen de dam: het water stroomt over de lava, en lava met water wordt steen: een pad naar de
 * vonk) en de <b>Gietzaal</b> (drie ovens met de vuurstaf: de gietrivier zakt weg; daarna lava die op een ritme stijgt en
 * zakt over de stenen naar de vonk). Met drie vonken en het Gloedschild gaat de Hartdeur open: de Hartgang, de voorkamer
 * (rust, bron) en het Hart van de Smidse, waar <b>Asgrim</b> de Gloedwachter over de lava zweeft ({@link Asgrim}: kantel
 * de kuip boven hem, dan is hij gedoofd). Daarna het hart van licht ({@code hart4}), de Schoorsteen op met de klauwhaak,
 * de krater in: steek het <b>Gloedbaken</b> aan met de vuurstaf ({@code baken4}), en op de uitkijk zie je de Nevelring
 * breken. Nachtrots komt eraan ({@code Zelda.KOMT}).
 *
 * <p>Lava doet in Zelda geen echte schade: wie erin stapt, springt er meteen uit (ZeldaSpel). Dit deel zet elke kolom van
 * zijn vak zelf (de kegel met de krater, met dezelfde vormen als 4a: {@link ZeldaEiland4a#land}, {@link ZeldaEiland4a#berg}),
 * met een wand rond de top die je van buiten niet op klimt. Rustplaatsen houden hun nummer: zet een nieuwe altijd achteraan.
 */
final class ZeldaEiland4b extends ZeldaHoofdstuk {

    static final int VERSIE = 1;
    /** Het vak van dit halve eiland (oost van 4a). */
    static final int X1 = 1260, X2 = 1400, Z1 = 180, Z2 = 480;
    /** De krater (en het Hart van de Smidse eronder). */
    static final int CX = 1312, CZ = 330;
    /** Tot deze straal is de kegel zes hoger (een wand die je van buiten niet op klimt); binnen {@link #KOM} een vlakke kratervloer. */
    static final double RAND = 29.5, KOM = 11;
    /** De vlaggen van 4b. */
    static final String VONK_KETEN = "vonk-keten", VONK_DAM = "vonk-dam", VONK_GIET = "vonk-giet", SCHILDPLAAT = "schildplaat",
            BIJLBLAD = "bijlblad", STUKJE_NEVEL = "hs:gloedberg-4", NEVELRING = "nevelring";
    static final String[] VONKEN = {VONK_KETEN, VONK_DAM, VONK_GIET};

    private final Plugin plugin;
    private Bouwer b;
    int G, D, KF;                                       // G = zeeniveau + 1; D = de vloer van de Smidse (G+2); KF = de kratervloer (G+50)
    private int g;
    private int[][] top;                                // hoogste blok per kolom (alleen tijdens het bouwen)
    private byte[][] soort;

    private static final byte ZEE = 0, ZAND = 1, AS = 2, BERG = 3, KRATER = 4;

    /** Wat al is uitgehakt (tijdens het bouwen): een latere gang zet daar geen wand. */
    private final List<int[]> holtes = new ArrayList<>();
    /** Wat er alleen tijdens het spel is: lava om bij te sputteren, de vuurschalen boven de Hartdeur, het baken. */
    private final List<Location> lava = new ArrayList<>();
    private Location bakenKern, bakenVuur;
    private int[][] schalen;
    private Barst stuwdam;
    private Poort koelpad;
    private Box hal, uitkijk, nis;
    private Nevel nevelnis;
    /** Voor wie de Nevelring nu breekt (het duurt een paar tellen). */
    private final java.util.Set<java.util.UUID> breekt = new java.util.HashSet<>();
    /** Staat Okker (eiland 4a: het Gloedschild) op het pad? Nee = 4a was er niet bij het bouwen (de zelftest meldt het). */
    boolean okkerOpPad;

    ZeldaEiland4b(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override String id() { return "e4b"; }
    @Override String naam() { return "De Gloedberg"; }
    @Override int versie() { return VERSIE; }
    @Override String deelId() { return "zelda-e4b"; }

    /** Een hub (de Smidsehal) met zalen in elke volgorde: «opnieuw vanaf hier» vergeet alleen tot de volgende rustplaats. */
    @Override boolean open() { return true; }

    // ── tijd, naam en muziek per plek ───────────────────────────────────────────────────────

    /** In de Smidse en de krater de schemer; als de Nevelring breekt, komt de zon op (op de hele Gloedberg, zie 4a). */
    @Override
    long hemel(Location l, ZeldaVoortgang.Stand s) {
        if (s.heeft(NEVELRING)) return 23600;
        if (s.heeft("baken4")) return 13800;
        return 13000;
    }

    private static boolean in(double x, double y, double z, int x1, int y1, int z1, int x2, int y2, int z2) {
        return x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1;
    }

    @Override
    String plekNaam(Location l) {
        double x = l.getX(), y = l.getY(), z = l.getZ();
        if (in(x, y, z, 1320, KF + 6, 315, 1340, KF + 20, 327)) return "De uitkijk";
        if (Math.hypot(x - CX - 0.5, z - CZ - 0.5) <= KOM + 1 && y >= KF - 1) return "De krater";
        if (in(x, y, z, 1320, D - 3, 322, 1340, KF + 4, 338)) return "De Schoorsteen";
        if (Math.hypot(x - CX - 0.5, z - CZ - 0.5) <= 15 && y >= D - 2 && y <= D + 18) return "Het Hart van de Smidse";
        if (in(x, y, z, 1266, D - 1, 297, 1297, D + 9, 344)) return "De Hartgang";
        if (in(x, y, z, 1258, G - 8, 244, 1296, D + 10, 270)) return "De Kettingzaal";
        if (in(x, y, z, 1296, D - 2, 238, 1332, D + 11, 274)) return "De Stuwdam";
        if (in(x, y, z, 1287, D - 2, 275, 1333, D + 12, 301)) return "De Gietzaal";
        if (in(x, y, z, 1259, D - 1, 271, 1287, D + 14, 297)) return "De Smidsehal";
        return "De Gloedberg";
    }

    @Override
    ZeldaMuziek.Lied muziek(Location l) {
        return switch (plekNaam(l)) {
            case "De krater", "De uitkijk" -> ZeldaMuziek.GLOED;
            case "De Gloedberg" -> ZeldaMuziek.SINTELGAT;
            default -> ZeldaMuziek.SMIDSE;
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

    /** Een bordje met alleen een naam (een bord tegen de muur, kijkt naar {@code kijk}). */
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

    /** Rustplaats: een zielvuur op een plaat van zwart steen (3 bij 3) met een zielslamp aan een paal; geen hout (lava). */
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

    /** De rots van de berg: zwart en grijs, met hier en daar tufsteen (zoals 4a). */
    private static Material rots(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x, y, z) % 16);
        return k < 5 ? Material.BLACKSTONE : k < 8 ? Material.DEEPSLATE : k < 10 ? Material.BASALT : k < 12 ? Material.TUFF
                : k < 14 ? Material.COBBLED_DEEPSLATE : k < 15 ? Material.SMOOTH_BASALT : Material.GILDED_BLACKSTONE;
    }

    /** De muren van de Smidse: zwarte steen van de eerste smeden, met hier en daar goud. */
    private static Material wand(int x, int y, int z) {
        int k = (int) (Bouwer.hash(x * 5 - 1, y, z * 3 + 2) % 24);
        return k < 13 ? Material.POLISHED_BLACKSTONE_BRICKS : k < 18 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS
                : k < 21 ? Material.BLACKSTONE : k < 23 ? Material.POLISHED_BASALT : Material.GILDED_BLACKSTONE;
    }

    /** De vloer van de Smidse: tegels in een patroon. */
    private static Material vloer(int x, int z) {
        return (x + z) % 5 == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : (x * 3 + z) % 7 == 0 ? Material.POLISHED_BASALT
                : (x + 2 * z) % 3 == 0 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_DEEPSLATE;
    }

    // ── het terrein: de kegel met de krater ─────────────────────────────────────────────────

    /** Hoe hoog de berg hier is (boven G): de vormen van 4a, rond de top zes hoger (de kraterwand), de krater vlak op 50. */
    static double hoogte(int x, int z) {
        double r = Math.hypot(x - CX, z - CZ);
        if (r < KOM) return 50;
        double bg = ZeldaEiland4a.berg(x, z);
        return r < RAND ? bg + 6 : bg;
    }

    /** Hoogte (bovenste blok) en soort van een kolom. */
    private int[] vorm(int x, int z) {
        double d = ZeldaEiland4a.land(x, z);
        if (d <= 0) return new int[]{G - 2 - (int) Math.min(6, -d / 2.5), ZEE};
        double r = Math.hypot(x - CX, z - CZ), bg = hoogte(x, z);
        if (r < KOM) return new int[]{KF, KRATER};
        byte s = bg > 1 ? BERG : AS;
        int hg = G + (int) Math.round(bg);
        if (d < 4) {                                                    // de kust: strand, of een klif onder de berg
            if (s == BERG) hg = Math.min(hg, G + (int) (d * 5) - 1);
            else {
                hg = G + (d < 1.8 ? -1 : 0);
                s = ZAND;
            }
        }
        return new int[]{hg, s};
    }

    private void terrein() {
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                int[] v = vorm(x, z);
                top[x - X1][z - Z1] = v[0];
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
                    vul(x1, G, z1, x2, G + 85, z2, Material.AIR);
                } else for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) kolom(x, z, top[x - X1][z - Z1], soort[x - X1][z - Z1]);
            }
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
        int ymax = G + 85;
        if (s == ZEE) {
            vul(x, g + 1, z, x, hg - 1, z, Material.DEEPSLATE);
            zet(x, hg, z, kans(x, z, 3) == 0 ? Material.TUFF : Material.GRAVEL);
            vul(x, hg + 1, z, x, G - 1, z, Material.WATER);
            vul(x, G, z, x, ymax, z, Material.AIR);
            return;
        }
        vul(x, g + 1, z, x, hg - 3, z, Material.DEEPSLATE);
        vul(x, hg - 2, z, x, hg - 1, z, s == AS || s == ZAND ? Material.TUFF : Material.BLACKSTONE);
        Material opp = switch (s) {
            case ZAND -> hg < G ? (kans(x, z, 3) == 0 ? Material.GRAVEL : Material.TUFF) : kans(x, z, 7) == 0 ? Material.GRAVEL : Material.BLACK_CONCRETE_POWDER;
            case KRATER -> kans(x, z, 3) == 0 ? Material.BASALT : kans(x, z, 4) == 0 ? Material.SMOOTH_BASALT : Material.BLACKSTONE;   // geen magma: daar loop je
            case BERG -> {
                if (steil(x, z, hg)) yield kans(x, z, 9) == 0 ? Material.MAGMA_BLOCK : kans(x, z, 2) == 0 ? Material.BASALT : Material.BLACKSTONE;
                yield rots(x, hg, z);
            }
            default -> {
                int k = kans(x, z, 9);
                yield k < 3 ? Material.TUFF : k < 5 ? Material.GRAVEL : k < 6 ? Material.COARSE_DIRT : k < 7 ? Material.LIGHT_GRAY_CONCRETE_POWDER : k < 8 ? Material.BLACKSTONE : Material.DEEPSLATE;
            }
        };
        zet(x, hg, z, opp);
        vul(x, hg + 1, z, x, ymax, z, Material.AIR);
    }

    // ── gangen en zalen ─────────────────────────────────────────────────────────────────────

    private void holte(int x1, int y1, int z1, int x2, int y2, int z2) {
        holtes.add(new int[]{Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)});
    }

    private boolean uitgehakt(int x, int y, int z) {
        for (int[] h : holtes) if (x >= h[0] && x <= h[3] && y >= h[1] && y <= h[4] && z >= h[2] && z <= h[5]) return true;
        return false;
    }

    /**
     * Een zaal of gang uithakken: lucht van {@code y + 1} tot {@code y + hoog}, een vloer van tegels op {@code y}, de wanden
     * en het dak van zwarte steen (niet waar al eerder is uitgehakt: dan loopt het erin door).
     */
    private void gang(int x1, int z1, int x2, int z2, int y, int hoog) {
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean muur = x < x1 || x > x2 || z < z1 || z > z2;
                if (muur) {
                    for (int yy = y; yy <= y + hoog + 1; yy++) if (!uitgehakt(x, yy, z)) zet(x, yy, z, wand(x, yy, z));
                    continue;
                }
                zet(x, y, z, vloer(x, z));
                vul(x, y + 1, z, x, y + hoog, z, Material.AIR);
                if (!uitgehakt(x, y + hoog + 1, z)) zet(x, y + hoog + 1, z, wand(x, y + hoog + 1, z));
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

    /** Een zuil van gepolijst basalt met een kapiteel. */
    private void zuil(int x, int z, int y1, int y2) {
        vul(x, y1, z, x, y2 - 1, z, Material.POLISHED_BASALT);
        zet(x, y2, z, Material.CHISELED_POLISHED_BLACKSTONE);
    }

    /** Een smid van steen (een beeld): voeten, lijf, een hoofd, en een hamer van ketting. */
    private void beeld(int x, int z, int y, BlockFace kijk) {
        zet(x, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
        vul(x, y + 1, z, x, y + 2, z, Material.POLISHED_BASALT);
        zet(x, y + 3, z, Material.CHISELED_POLISHED_BLACKSTONE);
        zet(x, y + 4, z, Material.GILDED_BLACKSTONE);
        zet(x + kijk.getModX(), y + 1, z + kijk.getModZ(), Bouwer.gericht(Material.ANVIL, kijk.getModX() != 0 ? BlockFace.NORTH : BlockFace.EAST));
    }

    /** Lava achter tralies in een wand (een gloeiende spleet; de lava stroomt nergens heen). */
    private void gloedspleet(int x, int y, int z, boolean langsZ, int dx, int dz) {
        zet(x + dx, y, z + dz, Material.LAVA);
        zet(x + 2 * dx, y, z + 2 * dz, Material.BLACKSTONE);
        zet(x + dx, y - 1, z + dz, Material.BLACKSTONE);
        zet(x + dx, y + 1, z + dz, Material.BLACKSTONE);
        zet(x, y, z, tralies(langsZ));
        lava.add(new Location(w, x + dx + 0.5, y + 0.5, z + dz + 0.5));
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    @Override
    void bouw(Bouwer bouwer) {
        b = bouwer;
        g = bouwer.g;
        G = g + 8;
        D = G + 2;
        KF = G + 50;
        leeg(bouwer.w);
        lava.clear();
        holtes.clear();
        okkerOpPad = false;
        speelgebied = Box.van(X1, g - 4, Z1 + 2, X2 - 1, G + 90, Z2 - 2);
        hal = Box.van(1260, D, 272, 1286, D + 13, 296);
        boolean bouwen = bouwer.plaats;
        List<long[]> chunks = new ArrayList<>();
        if (bouwen) {
            for (int cx = Math.floorDiv(X1, 16); cx <= Math.floorDiv(X2, 16); cx++)
                for (int cz = Math.floorDiv(Z1, 16); cz <= Math.floorDiv(Z2, 16); cz++) chunks.add(new long[]{cx, cz});
            b.daarna(() -> chunks.forEach(c -> w.addPluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = new int[X2 - X1 + 1][Z2 - Z1 + 1];
            soort = new byte[X2 - X1 + 1][Z2 - Z1 + 1];
            terrein();
            holte(1259, D, 272, 1259, D + 14, 296);                     // de oostwand van 4a (de Smidsepoort): daar komt geen wand van 4b
        }
        smidsepoort();
        smidsehal();
        kettingzaal();
        stuwdam();
        gietzaal();
        okker();
        hartdeur();
        hart();
        schoorsteen();
        krater();
        if (bouwen) {
            doorgangen();
            b.daarna(() -> chunks.forEach(c -> w.removePluginChunkTicket((int) c[0], (int) c[1], plugin)));
            top = null;
            soort = null;
        }
        // de begintoestand van deuren, poorten, de dam en de puzzels: altijd (ook zonder herbouw)
        for (Kamer k : kamers) {
            zetDeur(k.in, true);
            zetDeur(k.uit, false);
        }
        for (Barst br : barsten) zetDeur(br.muur, false);
        for (Poort po : poorten) zetDeur(po.deur, false);
        for (Nevel nv : nevels) zetDeur(nv.nevel, false);
        for (Ritme rt : ritmes) {
            zetDeur(rt.lava, true);
            rt.isHoog = false;
            rt.begin = 0;
        }
        for (Reeks r : reeksen) {
            zetDeur(r.deur, false);
            for (int[] d : r.dingen) b.zetAltijd(d[0], d[1], d[2], r.uit);
        }
        for (Kamer k : kamers)
            if (k.baas instanceof Asgrim a) for (int[] c : a.kuipen) b.zetAltijd(c[0], c[1], c[2], Asgrim.kuipBlok(true));
        motor();
        b = null;
    }

    /** De doorgangen tussen de zalen (als laatste: zo zet geen wand ze weer dicht). */
    private void doorgangen() {
        doorgang(1271, 269, 1275, 271, D, 5);                           // de Smidsehal → de Kettingzaal (noord)
        doorgang(1287, 282, 1287, 286, D, 5);                           // de Smidsehal → de gang naar de Gietzaal (oost)
        doorgang(1297, 282, 1297, 286, D, 5);                           // … → de Gietzaal
        doorgang(1295, 247, 1297, 249, D, 4);                           // de Kettingzaal → de Stuwdam
        doorgang(1299, 273, 1301, 275, D, 4);                           // de Stuwdam → de Gietzaal
        doorgang(1271, 298, 1275, 298, D, 6);                           // achter de Hartdeur → de Hartgang
        doorgang(1295, 328, 1297, 332, D, 4);                           // de voorkamer → het Hart (west)
        doorgang(1327, 328, 1327, 332, D, 4);                           // het Hart → de Schoorsteen (oost)
        doorgang(1279, 341, 1281, 341, D, 3);                           // de nis in de voorkamer
        for (int z = 328; z <= 332; z++) {                              // vloer van de doorgangen naar het Hart
            zet(1296, D, z, vloer(1296, z));
            zet(1297, D, z, vloer(1297, z));
            zet(1327, D, z, vloer(1327, z));
        }
    }

    // ── 1. de Smidsepoort (in de wand van 4a) ───────────────────────────────────────────────

    private void smidsepoort() {
        Poort p = poort(new Poort("smidsepoort", deur(Material.GILDED_BLACKSTONE.createBlockData(), 1259, D + 1, 281, 1259, D + 8, 287),
                Box.van(1250, D, 276, 1266, D + 10, 292), s -> s.heeft("k:askamer")));
        verhaal(new Verhaal("smidse", Box.van(1260, D, 279, 1264, D + 6, 289), List.of(
                r("Wiske", "De Smidse van de eerste smeden…"),
                r("Wiske", "Wat is het hier stil. En warm."))));
    }

    // ── 2. de Smidsehal: de hub van de Smidse, Tijmen, het oude schild ───────────────────────

    private void smidsehal() {
        int x1 = 1260, x2 = 1286, z1 = 272, z2 = 296;
        if (b.plaats) {
            gang(x1, z1, x2, z2, D, 13);
            // een loper van gloed naar het aambeeld, zuilen, beelden van smeden langs de wanden
            for (int x = x1; x <= x2; x++) for (int z = 283; z <= 285; z++) zet(x, D, z, z == 284 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE);
            for (int[] c : new int[][]{{1265, 277}, {1281, 277}, {1265, 291}, {1281, 291}}) zuil(c[0], c[1], D + 1, D + 13);
            for (int x = 1263; x <= 1283; x += 5) {
                beeld(x, z1, D + 1, BlockFace.SOUTH);
                if (x < 1270 || x > 1276) beeld(x, z2, D + 1, BlockFace.NORTH);
            }
            // het grote aambeeld in het midden: een sokkel met treden, een aambeeld van steen
            for (int x = 1271; x <= 1275; x++) for (int z = 282; z <= 286; z++) zet(x, D + 1, z, Bouwer.plaat(Material.POLISHED_BLACKSTONE_BRICK_SLAB, false));
            vul(1272, D + 1, 283, 1274, D + 1, 285, Material.POLISHED_BLACKSTONE_BRICKS);
            zet(1273, D + 2, 284, Material.CHISELED_POLISHED_BLACKSTONE);
            for (BlockFace f : new BlockFace[]{BlockFace.EAST, BlockFace.WEST}) zet(1273 + f.getModX(), D + 2, 284, Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, f, true));
            // licht: zielslampen aan kettingen, gloed in de muren achter tralies (hoog: niets brandbaars in de buurt)
            for (int[] l : new int[][]{{1268, 280}, {1278, 280}, {1268, 288}, {1278, 288}}) {
                vul(l[0], D + 10, l[1], l[0], D + 13, l[1], Material.IRON_CHAIN);
                zet(l[0], D + 9, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
            }
            for (int z = 276; z <= 292; z += 8) gloedspleet(x2 + 1, D + 9, z, true, 1, 0);
            for (int x = 1264; x <= 1282; x += 9) gloedspleet(x, D + 9, z1 - 1, false, 0, -1);
            // bordjes bij de uitgangen (ver van de lava)
            bord(1270, D + 3, z1, BlockFace.SOUTH, "De Kettingzaal");
            bord(x2, D + 3, 281, BlockFace.WEST, "De Gietzaal");
            bord(x1, D + 3, 279, BlockFace.EAST, "Sintelgat");
        }
        rustvuur(1263, D, 288);
        rust(new Rust("smidsehal", "De Smidsehal", plek(1265, D + 1, 284, -90f), Box.van(1260, D, 279, 1269, D + 6, 293), List.of()));
        bron("bron-smidsehal", plek(1263, D + 1, 280, 0f));
        Vondst plaat = vondst(new Vondst(SCHILDPLAAT, "Oude schildplaat", "breng hem naar Okker", Material.SHIELD, plek(1273, D + 3.4, 284, 0f),
                Box.van(1271, D + 1, 282, 1275, D + 4, 286)));
        plaat.daarna = (spel, p, s) -> spel.uitleg(p, r("Wiske", "Een schild van de eerste smeden. Kapot."),
                r("Wiske", "Okker in Sintelgat kan het vast smeden."));
        tijmen();
    }

    /**
     * Tijmen (zie ZELDA.md): hij ging vooruit en leest het schrift boven de Hartdeur. Na het baken staat hij op de uitkijk
     * boven de krater (een tweede Tijmen, alleen te zien voor wie het Gloedbaken aanstak).
     */
    private void tijmen() {
        Persoon tijmen = persoon(new Persoon("Tijmen", plek(1268, D + 1, 293, 180f), null, List.of(r("Tijmen", "Het schrift is oud. Maar ik lees het wel."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.zichtbaar = s -> !s.heeft("baken4");
        tijmen.eenmalig("tijmen-smidse", s -> !s.heeft("baken4"), List.of(
                        r("Tijmen", "Daar ben je. Ik zei toch dat ik ging kijken."),
                        r("Wiske", "Hij is er alwéér!"),
                        r("Tijmen", "Boven de Hartdeur staat: drie vonken wekken het Hart."),
                        r("Tijmen", "Daar waakt Asgrim. En boven hem slaapt het baken.")), null)
                .eenmalig("tijmen-vonken", s -> alleVonken(s) && !s.heeft("baken4"), List.of(
                        r("Tijmen", "Drie vonken! Hoor je dat? Het Hart is wakker.")), null)
                .eenmalig("tijmen-asgrim", s -> s.heeft("k:smidshart") && !s.heeft("baken4"), List.of(
                        r("Tijmen", "Asgrim is vrij. Nu het baken nog, boven in de krater.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
        praten(tijmen);
    }

    /** Heeft deze speler alle drie de vonken? */
    static boolean alleVonken(ZeldaVoortgang.Stand s) {
        for (String v : VONKEN) if (!s.heeft(v)) return false;
        return true;
    }

    // ── 3. de Kettingzaal: een kloof vol lava, de klauwhaak en de kettingbrug ───────────────

    private void kettingzaal() {
        int x1 = 1260, x2 = 1294, z1 = 246, z2 = 268, bodem = G - 6;
        if (b.plaats) {
            gang(x1, z1, x2, z2, D, 8);
            // de kloof (z 251 … 263): diep, met lava op de bodem
            for (int x = x1; x <= x2; x++)
                for (int z = 251; z <= 263; z++) {
                    zet(x, bodem - 1, z, Material.DEEPSLATE);
                    zet(x, bodem, z, Material.LAVA);
                    vul(x, bodem + 1, z, x, D, z, Material.AIR);
                }
            holte(x1, bodem + 1, 251, x2, D, 263);
            for (int z = 251; z <= 263; z++)
                for (int y = bodem - 1; y <= D; y++) {
                    zet(x1 - 1, y, z, wand(x1 - 1, y, z));
                    zet(x2 + 1, y, z, wand(x2 + 1, y, z));
                }
            for (int x = x1 - 1; x <= x2 + 1; x++) for (int y = bodem - 1; y < D; y++) {   // de wanden van de kloof onder de randen
                zet(x, y, 250, rots(x, y, 250));
                zet(x, y, 264, rots(x, y, 264));
            }
            for (int x = x1; x <= x2; x += 7) {
                lava.add(new Location(w, x + 0.5, bodem + 1, 257.5));
            }
            // de pijler in het midden, met een altaar en een paal met het haakpunt
            for (int x = 1275; x <= 1279; x++) for (int z = 256; z <= 258; z++) vul(x, bodem, z, x, D, z, Material.POLISHED_BASALT);
            for (int x = 1275; x <= 1279; x++) for (int z = 256; z <= 258; z++) zet(x, D, z, Material.POLISHED_BLACKSTONE_BRICKS);
            vul(1277, D + 1, 257, 1277, D + 2, 257, Material.POLISHED_BLACKSTONE_WALL);
            zet(1277, D + 3, 257, ZeldaHoofdstuk.HAAKPUNT);
            zet(1278, D + 1, 257, Material.CHISELED_POLISHED_BLACKSTONE);
            // kettingen aan het plafond, ijzeren balken over de kloof
            for (int x = x1 + 2; x <= x2 - 2; x += 6) {
                for (int z = 251; z <= 263; z++) zet(x, D + 8, z, Material.POLISHED_BLACKSTONE_BRICKS);
                for (int z = 253; z <= 261; z += 4) vul(x, D + 4 + (z % 3), z, x, D + 7, z, Material.IRON_CHAIN);
            }
            // de haakpunten aan de wanden (noord en zuid), met een lijst
            zet(1277, D + 4, z1 - 1, ZeldaHoofdstuk.HAAKPUNT);
            zet(1281, D + 4, z2 + 1, ZeldaHoofdstuk.HAAKPUNT);
            for (int[] c : new int[][]{{1277, z1 - 1}, {1281, z2 + 1}}) {
                zet(c[0], D + 5, c[1], Material.CHISELED_POLISHED_BLACKSTONE);
                zet(c[0] - 1, D + 4, c[1], Material.GILDED_BLACKSTONE);
                zet(c[0] + 1, D + 4, c[1], Material.GILDED_BLACKSTONE);
            }
            // de kettingbrug (oost, x 1291 … 1293): opgehaald; kettingen naar de lier op de noordrand, met het touw
            for (int x = 1290; x <= 1294; x += 4) vul(x, D + 1, 250, x, D + 7, 250, Material.IRON_CHAIN);
            vul(1290, D + 7, 249, 1294, D + 7, 249, Material.POLISHED_BLACKSTONE_BRICKS);
            zet(1292, D + 1, 248, Material.POLISHED_BLACKSTONE_BRICKS);
            zet(1292, D + 2, 248, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            for (int x = 1291; x <= 1293; x += 2) vul(x, D + 3, 249, x, D + 6, 249, Material.IRON_CHAIN);
            zet(1263, D + 4, z2, Bouwer.hangend(Material.SOUL_LANTERN));
            zet(1263, D + 4, z1, Bouwer.hangend(Material.SOUL_LANTERN));
        }
        verhaal(new Verhaal("kettingzaal", Box.van(1268, D, 264, 1280, D + 5, 268), List.of(
                r("Wiske", "Een kloof vol lava! Daar spring je niet over."),
                r("Wiske", "Op de pijler: iets dat gloeit. En een haakpunt!")))).als = s -> !s.heeft(VONK_KETEN);
        Haakpunt pijler = haakpunt(new Haakpunt("pijler", 1277, D + 3, 257, plek(1276, D + 1, 257, 0f)));
        Haakpunt noord = haakpunt(new Haakpunt("kettingnoord", 1277, D + 4, 245, plek(1277, D + 1, 247, 180f)));
        haakpunt(new Haakpunt("kettingzuid", 1281, D + 4, 269, plek(1281, D + 1, 267, 0f)));
        haak(pijler, plek(1277, D + 1, 266, 180f));
        Vondst vonk = vondst(new Vondst(VONK_KETEN, "Smidsvonk", "een van de drie", Material.BLAZE_POWDER, plek(1278, D + 2.3, 257, 0f),
                Box.van(1278, D + 1, 256, 1279, D + 3, 258)));
        vonk.daarna = (spel, p, s) -> vonkGevonden(spel, p, s);
        haak(noord, plek(1276, D + 1, 257, 180f));
        Reeks brug = reeks(new Reeks("kettingbrug", "De kettingbrug", Reeks.Hoe.VUUR, Material.HAY_BLOCK.createBlockData(), Material.AIR.createBlockData(),
                new Deur(Material.AIR.createBlockData(), Material.WAXED_COPPER_GRATE.createBlockData()).vlak(1291, D, 251, 1293, D, 263),
                Box.van(1283, D - 1, 246, 1295, D + 8, 268), 10));
        brug.dingen.add(new int[]{1292, D + 3, 248});
        brug.voor = plek(1286, D + 1, 248, -90f);
        brug.bijOplossen.add(r("Wiske", "Het touw brandt door. De brug valt!"));
        Verhaal touw = verhaal(new Verhaal("uitleg-touw", Box.van(1284, D, 246, 1294, D + 5, 250), List.of(
                r("Wiske", "Die brug hangt aan een touw."),
                r("Wiske", "Wat gebeurt er als het touw brandt?"))));
        touw.uitleg = true;
        touw.als = s -> !s.heeft("r:kettingbrug");
        verhaal(new Verhaal("stuwgang", Box.van(1295, D, 247, 1297, D + 4, 249), List.of(
                r("Wiske", "Ik hoor water ruisen. Hier, in een vulkaan?")))).als = s -> !s.heeft(VONK_DAM);
    }

    /** Een vonk gevonden: hoeveel nog (Wiske), en de vuurschaal boven de Hartdeur gaat voor jou aan. */
    private void vonkGevonden(ZeldaSpel spel, Player p, ZeldaVoortgang.Stand s) {
        int n = 0;
        for (String v : VONKEN) if (s.heeft(v)) n++;
        spel.vertel(p, r("Wiske", n == 3 ? "Drie vonken! Naar de Hartdeur." : n == 2 ? "Nog één vonk." : "Nog twee vonken."));
    }

    // ── 4. de Stuwdam: een bom tegen de dam; water en lava worden steen ─────────────────────

    private void stuwdam() {
        int x1 = 1298, x2 = 1330, z1 = 247, z2 = 272;
        if (b.plaats) {
            gang(x1, z1, x2, z2, D, 9);
            // het lavameer (alles behalve de looprand west en noord), met het eilandje van de vonk
            for (int x = 1303; x <= x2; x++)
                for (int z = 252; z <= z2; z++) {
                    boolean eiland = x >= 1314 && x <= 1318 && z >= 262 && z <= 266;
                    zet(x, D - 2, z, Material.DEEPSLATE);
                    zet(x, D - 1, z, Material.BLACKSTONE);
                    zet(x, D, z, eiland ? Material.POLISHED_BLACKSTONE_BRICKS : Material.LAVA);
                }
            for (int x = 1303; x <= x2; x += 9) for (int z = 256; z <= z2; z += 8) lava.add(new Location(w, x + 0.5, D + 1, z + 0.5));
            zet(1316, D + 1, 264, Material.CHISELED_POLISHED_BLACKSTONE);           // het altaar van de vonk
            for (int[] c : new int[][]{{1314, 262}, {1318, 262}, {1314, 266}, {1318, 266}}) zet(c[0], D + 1, c[1], Material.POLISHED_BLACKSTONE_WALL);
            // de dam (de noordwand), met een gebarsten stuk; daarachter de waterkelder van de smeden
            gang(1312, 240, 1320, 245, D, 5);
            for (int x = 1308; x <= 1324; x++) for (int y = D + 1; y <= D + 7; y++) zet(x, y, z1 - 1, (x + y) % 4 == 0 ? Material.DEEPSLATE_BRICKS : Material.POLISHED_DEEPSLATE);
            for (int x = 1313; x <= 1319; x++)
                for (int z = 241; z <= 243; z++) {                       // de kelderbak: water dat nergens heen kan (rondom steen)
                    zet(x, D - 1, z, Material.DEEPSLATE_BRICKS);
                    zet(x, D, z, Material.WATER);
                }
            zet(1312, D + 1, 241, Material.GRINDSTONE);
            zet(1320, D + 1, 241, Material.SMITHING_TABLE);
            for (int z = 241; z <= 243; z++) zet(1316, D + 5, z, Material.IRON_CHAIN);
            zet(1316, D + 4, 242, Bouwer.hangend(Material.SOUL_LANTERN));
            // de goot over de looprand naar het meer, en zielslampen
            for (int z = 247; z <= 251; z++) zet(1316, D, z, Material.POLISHED_DEEPSLATE);
            zet(1300, D + 7, 250, Bouwer.hangend(Material.SOUL_LANTERN));
            zet(1300, D + 7, 268, Bouwer.hangend(Material.SOUL_LANTERN));
        }
        verhaal(new Verhaal("stuwdam", Box.van(1298, D, 247, 1306, D + 5, 252), List.of(
                r("Wiske", "Een meer van lava. En daar: een vonk!"),
                r("Wiske", "Achter die dam hoor ik water.")))).als = s -> !s.heeft(VONK_DAM);
        stuwdam = barstOpPad(new Barst("stuwdam", deur(ZeldaHoofdstuk.BARST.createBlockData(), 1314, D + 1, 246, 1318, D + 3, 246),
                Box.van(1308, D, 239, 1324, D + 7, 252)));
        stuwdam.voor = plek(1316, D + 1, 250, 180f);
        koelpad = poort(new Poort("koelpad", new Deur(Material.LAVA.createBlockData(), Material.OBSIDIAN.createBlockData()).vlak(1315, D, 252, 1317, D, 261),
                Box.van(1306, D - 1, 245, 1326, D + 7, 268), s -> s.heeft("b:stuwdam")));
        Verhaal steen = verhaal(new Verhaal("uitleg-koelpad", Box.van(1312, D, 247, 1320, D + 5, 252), List.of(
                r("Wiske", "Water op lava… dat wordt steen!"),
                r("Wiske", "Een pad naar de vonk."))));
        steen.uitleg = true;
        steen.vereist = "b:stuwdam";
        Vondst vonk = vondst(new Vondst(VONK_DAM, "Smidsvonk", "een van de drie", Material.BLAZE_POWDER, plek(1316, D + 2.3, 264, 0f),
                Box.van(1315, D + 1, 263, 1317, D + 3, 265)));
        vonk.daarna = (spel, p, s) -> vonkGevonden(spel, p, s);
        // het bijlblad van de eerste smeden (voor Kees, op het Lindewoud): in de waterkelder, op het rek
        Vondst blad = vondst(new Vondst(BIJLBLAD, "Bijlblad", "voor Kees, op het Lindewoud", Material.IRON_AXE, plek(1319, D + 1.4, 244, 0f),
                Box.van(1318, D + 1, 243, 1320, D + 3, 245)));
        blad.vereist = "g:kees-blad";
        blad.daarna = (spel, p, s) -> {
            if (s.missie(ZeldaHoofdstuk1.BIJLBLAD) == 1) spel.missie(p, ZeldaHoofdstuk1.BIJLBLAD, 2);
            spel.vertel(p, r("Wiske", "Kees zal blij zijn!"));
        };
    }

    // ── 5. de Gietzaal: drie ovens (de vuurstaf), de gietrivier zakt; lava op een ritme ──────

    private void gietzaal() {
        int x1 = 1298, x2 = 1331, z1 = 276, z2 = 300;
        if (b.plaats) {
            gang(1288, 282, 1296, 286, D, 5);                            // de gang van de Smidsehal hierheen
            for (int x = 1289; x <= 1295; x += 3) zet(x, D + 5, 284, Bouwer.hangend(Material.SOUL_LANTERN));
            gang(x1, z1, x2, z2, D, 11);
            // de gietrivier (x 1313 … 1317): lava, één diep
            for (int x = 1313; x <= 1317; x++)
                for (int z = z1; z <= z2; z++) {
                    zet(x, D - 2, z, Material.DEEPSLATE);
                    zet(x, D - 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            for (int z = z1 + 2; z <= z2; z += 8) lava.add(new Location(w, 1315.5, D + 1, z + 0.5));
            // de oostoever: twee ovens (noord en zuid), de gietbak in het midden met de stenen, de nis met de vonk
            oven(1322, 279);
            oven(1322, 297);
            for (int x = 1322; x <= 1328; x++)
                for (int z = 285; z <= 291; z++) {
                    zet(x, D - 2, z, Material.DEEPSLATE);
                    zet(x, D - 1, z, Material.BLACKSTONE);
                    zet(x, D, z, Material.LAVA);
                }
            for (int z = 284; z <= 292; z += 8) for (int x = 1322; x <= 1328; x++) zet(x, D + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            for (int x = 1329; x <= x2; x++) for (int z = 286; z <= 290; z++) zet(x, D, z, Material.POLISHED_BLACKSTONE_BRICKS);
            for (int z = 285; z <= 291; z += 6) vul(x2, D + 1, z, x2, D + 4, z, Material.POLISHED_BASALT);
            zet(1330, D + 1, 288, Material.CHISELED_POLISHED_BLACKSTONE);         // het altaar van de vonk
            // de grote oven boven de nis (hoog in de oostwand)
            vul(x2 - 1, D + 5, 287, x2, D + 6, 289, Material.BRICKS);
            vul(x2 - 1, D + 8, 287, x2, D + 9, 289, Material.BRICKS);
            zet(x2 - 1, D + 7, 287, Material.BRICKS);
            zet(x2 - 1, D + 7, 289, Material.BRICKS);
            zet(x2, D + 7, 288, Material.BRICKS);
            for (int y = D + 10; y <= D + 11; y++) zet(x2, y, 288, Material.BRICKS);
            // gietvormen en een slijpsteen op de werkvloer (west); een bron; zielslampen
            for (int[] c : new int[][]{{1302, 279}, {1304, 279}, {1306, 279}}) zet(c[0], D + 1, c[1], Material.POLISHED_BLACKSTONE_SLAB);
            zet(1300, D + 1, 279, Material.BLAST_FURNACE);
            zet(1300, D + 1, 298, Material.SMITHING_TABLE);
            for (int[] l : new int[][]{{1305, 282}, {1305, 294}, {1325, 280}, {1325, 296}}) {
                vul(l[0], D + 9, l[1], l[0], D + 11, l[1], Material.IRON_CHAIN);
                zet(l[0], D + 8, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
            }
        }
        bron("bron-gietzaal", plek(1302, D + 1, 295, 0f));
        verhaal(new Verhaal("gietzaal", Box.van(1298, D, 280, 1306, D + 6, 288), List.of(
                r("Wiske", "Een rivier van gloeiend ijzer!"),
                r("Wiske", "Aan de overkant: ovens. Koud.")))).als = s -> !s.heeft("r:ovens") && !s.heeft(VONK_GIET);
        Verhaal uitleg = verhaal(new Verhaal("uitleg-ovens", Box.van(1306, D, 284, 1312, D + 6, 292), List.of(
                r("Wiske", "Drie ovens: twee aan de oever, één hoog in de muur."),
                r("Wiske", "Steek ze aan met de vuurstaf, snel na elkaar!"))));
        uitleg.uitleg = true;
        uitleg.als = s -> !s.heeft("r:ovens") && !s.heeft(VONK_GIET);
        Reeks ovens = reeks(new Reeks("ovens", "De ovens", Reeks.Hoe.VUUR, ZeldaEiland4a.vuur(Material.CAMPFIRE, false), ZeldaEiland4a.vuur(Material.CAMPFIRE, true),
                new Deur(Material.LAVA.createBlockData(), Material.AIR.createBlockData()).vlak(1313, D, z1, 1317, D, z2), Box.van(x1, D - 1, z1, x2, D + 11, z2), 25));
        ovens.dingen.add(new int[]{1322, D + 2, 279});
        ovens.dingen.add(new int[]{1322, D + 2, 297});
        ovens.dingen.add(new int[]{x2 - 1, D + 7, 288});
        ovens.voor = plek(1311, D + 1, 288, -90f);
        ovens.bijOplossen.add(r("Wiske", "De ovens branden! De gietrivier zakt weg."));
        Verhaal giet = verhaal(new Verhaal("uitleg-ritme", Box.van(1318, D, 284, 1321, D + 5, 292), List.of(
                r("Wiske", "Kijk: de lava stijgt en zakt."),
                r("Wiske", "Wacht tot hij zakt. Dan springen!"))));
        giet.uitleg = true;
        giet.als = s -> !s.heeft(VONK_GIET);
        Ritme stenen = ritme(new Ritme("gietvloer", "De gietvloer", new Deur(Material.LAVA.createBlockData(), Material.POLISHED_BASALT.createBlockData())
                .blok(1322, D, 288).blok(1324, D, 288).blok(1326, D, 288).blok(1328, D, 288), Box.van(1318, D - 1, 284, x2, D + 5, 292), 4500, 1800));
        stenen.voor = plek(1320, D + 1, 288, -90f);
        Vondst vonk = vondst(new Vondst(VONK_GIET, "Smidsvonk", "een van de drie", Material.BLAZE_POWDER, plek(1330, D + 2.3, 288, 0f),
                Box.van(1329, D + 1, 287, x2, D + 3, 289)));
        vonk.daarna = (spel, p, s) -> vonkGevonden(spel, p, s);
    }

    /** Een smeltoven van baksteen op de oostoever, de mond (een kampvuur, koud) naar het westen. */
    private void oven(int x, int z) {
        if (!b.plaats) return;
        vul(x, D + 1, z - 1, x + 2, D + 3, z + 1, Material.BRICKS);
        vul(x + 1, D + 4, z, x + 1, D + 8, z, Material.BRICKS);
        zet(x, D + 1, z, Material.BRICKS);
        zet(x, D + 3, z, Material.BRICKS);
        zet(x - 1, D + 1, z - 1, Material.ANVIL);
    }

    // ── 6. Okker smeedt het Gloedschild (terug naar Sintelgat, eiland 4a) ───────────────────

    /** Okker de smid (eiland 4a, Sintelgat) smeedt van de schildplaat het Gloedschild: hij staat op het pad (terug naar de hub). */
    private void okker() {
        for (ZeldaHoofdstuk h : eiland == null ? List.<ZeldaHoofdstuk>of() : eiland.delen())
            if (h != this && h.onderdeel("Okker") instanceof Persoon okker) {
                praten(okker);
                okkerOpPad = true;
            }
    }

    /** Waar Okker staat (bij zijn smidse in Sintelgat, eiland 4a). */
    private Location okkerPlek() {
        if (eiland != null) for (ZeldaHoofdstuk h : eiland.delen()) if (h != this && h.plekVan("Okker") != null) return h.plekVan("Okker");
        return new Location(w, 1145.5, D + 1, 317.5);
    }

    // ── 7. de Hartdeur, de Hartgang en de voorkamer ─────────────────────────────────────────

    private void hartdeur() {
        schalen = new int[][]{{1271, D + 8, 296}, {1273, D + 9, 296}, {1275, D + 8, 296}};
        if (b.plaats) {
            // de Hartdeur in de zuidwand van de hal: een lijst van goud, drie vuurschalen erboven (één per vonk)
            for (int y = D + 1; y <= D + 7; y++) for (int x : new int[]{1270, 1276}) zet(x, y, 296, Material.GILDED_BLACKSTONE);
            for (int x = 1270; x <= 1276; x++) zet(x, D + 7, 296, Material.CHISELED_POLISHED_BLACKSTONE);
            for (int[] s : schalen) {
                zet(s[0], s[1] - 1, s[2], Material.CHISELED_POLISHED_BLACKSTONE);
                zet(s[0], s[1], s[2], ZeldaEiland4a.vuur(Material.CAMPFIRE, false));
            }
            // de Hartgang: zuidwaarts, met beelden en zielslampen
            gang(1271, 299, 1275, 319, D, 6);
            for (int z = 302; z <= 316; z += 7) {
                zet(1271, D + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                zet(1275, D + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                zet(1271, D + 2, z, Material.SOUL_LANTERN);
                zet(1275, D + 2, z, Material.SOUL_LANTERN);
            }
            // de voorkamer: rust, bron, een nis vol nevel (pas met de lichtpijlen), de deur naar het Hart (oost)
            gang(1270, 320, 1294, 340, D, 7);
            for (int[] c : new int[][]{{1274, 324}, {1290, 324}, {1274, 336}, {1290, 336}}) zuil(c[0], c[1], D + 1, D + 7);
            gang(1279, 342, 1281, 343, D, 3);
            for (int z = 326; z <= 334; z += 8) gloedspleet(1295, D + 5, z, true, 1, 0);
            for (int z = 327; z <= 333; z += 6) {                            // de deur naar het Hart: een lijst
                vul(1294, D + 1, z, 1294, D + 5, z, Material.GILDED_BLACKSTONE);
            }
            for (int z = 327; z <= 333; z++) zet(1294, D + 5, z, Material.CHISELED_POLISHED_BLACKSTONE);
        }
        Poort hart = poort(new Poort("hartdeur", deur(Material.CHISELED_POLISHED_BLACKSTONE.createBlockData(), 1271, D + 1, 297, 1275, D + 6, 297),
                Box.van(1266, D, 289, 1280, D + 7, 304), s -> alleVonken(s) && s.heeft(ZeldaSpel.GLOEDSCHILD)));
        verhaal(new Verhaal("hartdeur", Box.van(1269, D, 291, 1277, D + 5, 296), List.of(
                r("Wiske", "Een deur met drie vuurschalen."),
                r("Wiske", "Eén voor elke vonk, denk ik.")))).als = s -> !alleVonken(s);
        verhaal(new Verhaal("hartdeur-schild", Box.van(1269, D, 291, 1277, D + 5, 296), List.of(
                r("Wiske", "Wacht. Zonder schild tegen zijn vuur?"),
                r("Wiske", "Okker kan dat oude schild vast smeden.")))).als = s -> alleVonken(s) && !s.heeft(ZeldaSpel.GLOEDSCHILD);
        verhaal(new Verhaal("hartgang", Box.van(1271, D, 300, 1275, D + 5, 306), List.of(
                r("Wiske", "Het Hart van de Smidse. Ik hoor het knetteren."))));
        rustvuur(1273, D, 337);
        rust(new Rust("hartvoorkamer", "De voorkamer", plek(1278, D + 1, 330, -90f), Box.van(1270, D, 322, 1286, D + 6, 340), List.of()));
        bron("bron-voorkamer", plek(1288, D + 1, 337, 0f));
        nis = Box.van(1279, D, 341, 1281, D + 3, 343);
        Vondst stukje = vondst(new Vondst(STUKJE_NEVEL, "Hartstukje", null, Material.RED_DYE, plek(1280, D + 1.4, 343, 0f), Box.van(1279, D + 1, 342, 1281, D + 3, 343)));
        stukje.voorwerp = "lichtpijlen";                                   // achter Morgals nevel: pas met de lichtpijlen (Nachtrots)
        // Morgals nevel voor de nis (5a): een lichtpijl laat hem verdwijnen (de blokken zet de begintoestand, zonder herbouw)
        nevelnis = nevel(new Nevel("nevelnis", new Deur(Nevel.BLOK.createBlockData(), Material.AIR.createBlockData()).vlak(1279, D + 1, 341, 1281, D + 3, 341),
                Box.van(1276, D, 334, 1284, D + 4, 343)));
        nevelnis.voor = plek(1280, D + 1, 336, 0f);
        verhaal(new Verhaal("nevelnis", Box.van(1276, D, 336, 1284, D + 4, 340), List.of(
                r("Wiske", "Zwarte nevel, in die nis. Brr."),
                r("Wiske", "Daar glinstert iets. Nu nog niet.")))).als = s -> !s.heeft("lichtpijlen");
        Verhaal licht = verhaal(new Verhaal("nevelnis-licht", Box.van(1276, D, 334, 1284, D + 4, 340), List.of(
                r("Wiske", "Die nevel! Nu heb je lichtpijlen."))));
        licht.vereist = "lichtpijlen";
        licht.als = s -> !s.heeft(STUKJE_NEVEL) && !s.heeft("n:nevelnis");
    }

    // ── 8. het Hart van de Smidse: Asgrim de Gloedwachter ──────────────────────────────────

    private void hart() {
        int r = 14;
        if (b.plaats) {
            for (int x = CX - r - 1; x <= CX + r + 1; x++)
                for (int z = CZ - r - 1; z <= CZ + r + 1; z++) {
                    double d = Math.hypot(x - CX, z - CZ);
                    if (d > r + 0.6) continue;
                    if (d > r - 0.5) {                                          // de ronde muur
                        for (int y = D - 2; y <= D + 17; y++) zet(x, y, z, wand(x, y, z));
                        continue;
                    }
                    zet(x, D - 2, z, Material.DEEPSLATE);
                    if (d <= 7.5 && d > 2.3 && !brug(x, z)) {                  // de lavaput (met de bruggen naar het midden)
                        zet(x, D - 1, z, Material.BLACKSTONE);
                        zet(x, D, z, Material.LAVA);
                    } else {
                        zet(x, D - 1, z, Material.BLACKSTONE);
                        zet(x, D, z, d <= 2.3 ? (d < 1 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS)
                                : d <= 7.5 ? Material.POLISHED_BASALT : vloer(x, z));
                    }
                    vul(x, D + 1, z, x, D + 16, z, Material.AIR);
                    zet(x, D + 17, z, wand(x, D + 17, z));
                }
            holte(CX - r, D + 1, CZ - r, CX + r, D + 16, CZ + r);
            for (int a = 0; a < 360; a += 45) {                                  // lava achter tralies in de muur, rondom
                double rad = Math.toRadians(a + 22.5);
                int x = CX + (int) Math.round(Math.cos(rad) * (r - 0.5)), z = CZ + (int) Math.round(Math.sin(rad) * (r - 0.5));
                lava.add(new Location(w, x + 0.5, D + 6, z + 0.5));
            }
            // de kuipen aan kettingen boven de zweefplekken, zielslampen tussen de kettingen
            for (int[] k : KUIPEN) {
                vul(k[0], D + 12, k[1], k[0], D + 16, k[1], Material.IRON_CHAIN);
                zet(k[0], D + 11, k[1], Asgrim.kuipBlok(true));
            }
            for (int[] l : new int[][]{{CX, CZ - 10}, {CX, CZ + 10}, {CX - 10, CZ}, {CX + 10, CZ}}) {
                vul(l[0], D + 13, l[1], l[0], D + 16, l[1], Material.IRON_CHAIN);
                zet(l[0], D + 12, l[1], Bouwer.hangend(Material.SOUL_LANTERN));
            }
            for (int x : new int[]{CX - 9, CX + 9}) for (int z : new int[]{CZ - 9, CZ + 9}) zuil(x, z, D + 1, D + 16);
            zet(1301, D + 1, 337, Bouwer.gericht(Material.BELL, BlockFace.EAST));
            for (int x = CX - 7; x <= CX + 7; x++) for (int z = CZ - 7; z <= CZ + 7; z++)
                if (x % 4 == 0 && z % 4 == 2 && Math.hypot(x - CX, z - CZ) < 7 && Math.hypot(x - CX, z - CZ) > 3) lava.add(new Location(w, x + 0.5, D + 1, z + 0.5));
        }
        List<Location> zweef = new ArrayList<>(), landing = new ArrayList<>();
        List<int[]> kuipen = new ArrayList<>();
        for (int[] k : KUIPEN) {
            zweef.add(new Location(w, k[0] + 0.5, D + 5, k[1] + 0.5));
            kuipen.add(new int[]{k[0], D + 11, k[1]});
            landing.add(plek(k[2], D + 1, k[3], 0f));
        }
        Kamer k = kamer(new Kamer("smidshart", "Het Hart van de Smidse", Box.van(CX - r, D - 2, CZ - r, CX + r, D + 17, CZ + r),
                Box.van(1300, D, 325, 1306, D + 5, 335), deur(tralies(true), 1298, D + 1, 328, 1298, D + 4, 332), deur(tralies(true), 1326, D + 1, 328, 1326, D + 4, 332)));
        k.muziek = ZeldaMuziek.ASGRIM;
        for (int[] sp : new int[][]{{CX, CZ - 11}, {CX, CZ + 11}, {CX - 11, CZ}, {CX + 11, CZ}, {CX - 8, CZ - 9}, {CX + 8, CZ + 9}})
            k.spawns.add(plek(sp[0], D + 1, sp[1], 0f));
        Asgrim asgrim = new Asgrim(plugin, "Asgrim", zweef, kuipen, landing,
                List.of(r("Wiske", "Hij wordt zwakker! Volhouden!")),
                List.of(r("Wiske", "De nevel trekt uit hem weg…"),
                        r("Wiske", "Kijk, in het midden: een hart van licht!")));
        k.baas = asgrim;
        k.golf(Soort.ASGRIM);
        k.bel = new int[]{1301, D + 1, 337};
        k.bijStart.add(r("Wiske", "Asgrim, de Gloedwachter! Een wezen van vuur."));
        k.bijStart.add(r("Wiske", "Die kuipen boven hem… vol water!"));
        k.tips.addAll(List.of(r("Wiske", "Schiet een pijl op de kuip boven hem. Of een bom!"),
                r("Wiske", "Gedoofd? Dán slaan. En je schild omhoog!")));
        Vondst hart = vondst(new Vondst("hart4", "Hartcontainer", "een hart erbij", Material.RED_DYE, plek(CX, D + 1.3, CZ, 0f),
                Box.van(CX - 1, D, CZ - 1, CX + 1, D + 3, CZ + 1)));
        hart.vereist = "k:smidshart";                                        // pas als Asgrim vrij is
        // Asgrim als hij vrij is: een kleine gloed op het eilandje; hij zegt waar het baken is
        Persoon vrij = persoon(new Persoon("Asgrim", plek(CX + 1, D + 1, CZ + 1, 225f), null, List.of(r("Asgrim", "Het vuur brandt weer. Dank je."))));
        vrij.soort = EntityType.BLAZE;
        vrij.zichtbaar = s -> s.heeft("k:smidshart");
        vrij.eenmalig("asgrim-1", s -> s.heeft("k:smidshart"), List.of(
                r("Asgrim", "Ssss… ik ben wakker. Wat deed ik?"),
                r("Wiske", "De nevel had je. Nu niet meer."),
                r("Asgrim", "Het Gloedbaken is uit. Steek het aan, boven in de krater."),
                r("Asgrim", "De schoorsteen gaat omhoog. Haak je maar op.")), null);
        praten(vrij);
    }

    /** De zweefplekken van Asgrim (onder een kuip, schuin boven de lava) en waar hij neerploft (op de rand): {kx, kz, lx, lz}. */
    static final int[][] KUIPEN = {{CX + 3, CZ - 3, CX + 7, CZ - 7}, {CX + 3, CZ + 3, CX + 7, CZ + 7}, {CX - 3, CZ + 3, CX - 7, CZ + 7}, {CX - 3, CZ - 3, CX - 7, CZ - 7}};

    /** Ligt hier een brug over de lavaput (twee breed, van de rand naar het eilandje, in de vier richtingen)? */
    private static boolean brug(int x, int z) {
        return (x == CX || x == CX + 1) && Math.abs(z - CZ) <= 8 || (z == CZ || z == CZ + 1) && Math.abs(x - CX) <= 8;
    }

    // ── 9. de Schoorsteen: met de klauwhaak omhoog, en terug: springen (onderin water) ──────

    /**
     * De balkons in de schoorsteen (om en om, nooit boven elkaar: wie valt, valt in het water): {x1, z1, vloer boven D} (2 bij
     * 2), het haakpunt erbij {x, y boven D, z} (boven de open schacht, aan een balk uit de wand) en de landing {x, z}.
     */
    static final int[][] BALKONS = {
            {1331, 328, 6, 1333, 9, 328, 1332, 329},
            {1334, 331, 18, 1333, 21, 332, 1334, 331},
            {1331, 331, 30, 1333, 33, 332, 1332, 331},
            {1334, 328, 42, 1333, 45, 329, 1334, 328}};

    private void schoorsteen() {
        int sx1 = 1331, sx2 = 1335, sz1 = 328, sz2 = 332;
        if (b.plaats) {
            gang(1328, 324, 1338, 336, D, 4);                                    // de voet van de schoorsteen
            for (int x = sx1; x <= sx2; x++)
                for (int z = sz1; z <= sz2; z++) {                               // onderin: een poel (twee diep), en de schacht omhoog
                    zet(x, D - 2, z, Material.DEEPSLATE_BRICKS);
                    zet(x, D - 1, z, Material.WATER);
                    zet(x, D, z, Material.WATER);
                    vul(x, D + 1, z, x, KF + 3, z, Material.AIR);
                }
            for (int x = sx1 - 1; x <= sx2 + 1; x++)
                for (int z = sz1 - 1; z <= sz2 + 1; z++) if (x < sx1 || x > sx2 || z < sz1 || z > sz2) zet(x, D - 1, z, Material.DEEPSLATE_BRICKS);
            for (int y = D + 5; y <= KF + 4; y++)                                // de wanden van de schacht
                for (int x = sx1 - 1; x <= sx2 + 1; x++)
                    for (int z = sz1 - 1; z <= sz2 + 1; z++)
                        if (x == sx1 - 1 || x == sx2 + 1 || z == sz1 - 1 || z == sz2 + 1 || y == KF + 4) zet(x, y, z, (y % 6 == 0) ? Material.POLISHED_BASALT : Material.BRICKS);
            holte(sx1, D + 1, sz1, sx2, KF + 3, sz2);
            for (int[] bk : BALKONS) {
                int y = D + bk[2];
                vul(bk[0], y, bk[1], bk[0] + 1, y, bk[1] + 1, Material.POLISHED_BLACKSTONE_BRICKS);
                boolean west = bk[0] == sx1;
                for (int x = west ? sx1 : sx2 - 1; x <= (west ? sx1 + 1 : sx2); x++) zet(x, D + bk[4], bk[5], stam(Material.DARK_OAK_LOG, org.bukkit.Axis.X));
                zet(bk[3], D + bk[4], bk[5], ZeldaHoofdstuk.HAAKPUNT);
            }
            // bovenaan: een vloer over de schacht (met een gat voor de ladder) en de ladder vanaf het hoogste balkon
            for (int x = sx1; x <= sx2; x++) for (int z = sz1; z <= sz2; z++) if (x != sx2 || z != sz1) zet(x, KF, z, Material.POLISHED_BLACKSTONE_BRICKS);
            for (int y = D + 43; y <= KF + 1; y++) zet(sx2, y, sz1, Bouwer.gericht(Material.LADDER, BlockFace.SOUTH));
            for (int y = D + 8; y <= D + 44; y += 12) zet(sx1 - 1, y, 330, Material.SHROOMLIGHT);
            zet(1330, D + 3, 326, Material.SOUL_LANTERN);
            bord(1336, D + 2, 324, BlockFace.SOUTH, "De krater");
        }
        verhaal(new Verhaal("schoorsteen", Box.van(1328, D, 324, 1338, D + 4, 336), List.of(
                r("Wiske", "Een schoorsteen, helemaal naar boven!"),
                r("Wiske", "Haakpunten aan de balken. Vallen? Onderin is water.")))).als = s -> !s.bezocht(id(), 3);
        rustvuur(1329, D, 326);
        rust(new Rust("schoorsteen", "De Schoorsteen", plek(1329, D + 1, 334, 90f), Box.van(1328, D, 324, 1338, D + 4, 336), List.of()));
        Location van = plek(1336, D + 1, 330, 90f);                              // de eerste haak: vanaf de rand van de poel (oost)
        for (int i = 0; i < BALKONS.length; i++) {
            int[] bk = BALKONS[i];
            Haakpunt hp = haakpunt(new Haakpunt("schoorsteen" + (i + 1), bk[3], D + bk[4], bk[5], plek(bk[6], D + bk[2] + 1, bk[7], 0f)));
            haak(hp, van);
            van = plek(bk[6], D + bk[2] + 1, bk[7], 0f);
        }
    }

    // ── 10. de krater: het Gloedbaken, en de uitkijk waar de Nevelring breekt ──────────────

    private void krater() {
        if (b.plaats) {
            // de tunnel van de schoorsteen naar de krater (west)
            for (int x = 1322; x <= 1330; x++)
                for (int z = 330; z <= 331; z++) {
                    zet(x, KF, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    vul(x, KF + 1, z, x, KF + 3, z, Material.AIR);
                    zet(x, KF + 4, z, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            for (int x = 1322; x <= 1329; x++) for (int y = KF; y <= KF + 4; y++) {
                zet(x, y, 329, Material.POLISHED_BLACKSTONE_BRICKS);
                zet(x, y, 332, Material.POLISHED_BLACKSTONE_BRICKS);
            }
            vul(1330, KF + 1, 330, 1330, KF + 3, 331, Material.AIR);
            // de kratervloer: een lavaring rond het eilandje met het baken, een brug (oost)
            for (int x = CX - 11; x <= CX + 11; x++)
                for (int z = CZ - 11; z <= CZ + 11; z++) {
                    double d = Math.hypot(x - CX, z - CZ);
                    if (d >= KOM) continue;
                    vul(x, KF + 1, z, x, KF + 30, z, Material.AIR);
                    if (d > 2.6 && d <= 5 && !(z == CZ && x > CX)) {
                        zet(x, KF - 1, z, Material.BLACKSTONE);
                        zet(x, KF, z, Material.LAVA);
                    } else if (d <= 2.6 || (z == CZ && x > CX && d <= 5)) zet(x, KF, z, d <= 2.6 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BASALT);
                }
            for (int a = 0; a < 360; a += 60) lava.add(new Location(w, CX + 0.5 + Math.cos(Math.toRadians(a)) * 4, KF + 1, CZ + 0.5 + Math.sin(Math.toRadians(a)) * 4));
            // het Gloedbaken: vier zuilen, een sokkel met de schaal (de vuurstaf), een kroon die gaat gloeien
            for (int[] c : new int[][]{{CX - 1, CZ - 1}, {CX + 1, CZ - 1}, {CX - 1, CZ + 1}, {CX + 1, CZ + 1}}) vul(c[0], KF + 1, c[1], c[0], KF + 4, c[1], Material.POLISHED_BLACKSTONE_BRICKS);
            zet(CX, KF + 1, CZ, Material.CHISELED_POLISHED_BLACKSTONE);
            zet(CX, KF + 2, CZ, ZeldaEiland4a.vuur(Material.CAMPFIRE, false));
            for (int x = CX - 1; x <= CX + 1; x++) for (int z = CZ - 1; z <= CZ + 1; z++) zet(x, KF + 5, z, x == CX && z == CZ ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
            zet(CX, KF + 6, CZ, Material.LIGHTNING_ROD);
            // de trap naar de uitkijk (oost, omhoog door de wand) en de uitkijk met een borstwering
            for (int n = 1; n <= 10; n++) {                                     // trede n: een traptrede op KF+n (je staat op KF+n+1)
                int x = 1321 + n, y = KF + n;
                for (int z = 323; z <= 325; z++) {
                    vul(x, KF - 1, z, x, y - 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    zet(x, y, z, Bouwer.trap(Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.EAST));
                    vul(x, y + 1, z, x, y + 5, z, Material.AIR);
                }
                for (int y2 = y; y2 <= y + 4; y2++) {
                    zet(x, y2, 322, Material.POLISHED_BLACKSTONE_BRICKS);
                    zet(x, y2, 326, Material.POLISHED_BLACKSTONE_BRICKS);
                }
                for (int z = 322; z <= 326; z++) zet(x, y + 6, z, Material.POLISHED_BLACKSTONE_BRICKS);
            }
            int py = KF + 10;
            for (int x = 1332; x <= 1338; x++)
                for (int z = 316; z <= 326; z++) {                       // (niet verder zuid: daar is de schoorsteen)
                    vul(x, G + 40, z, x, py, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    zet(x, py, z, (x + z) % 3 == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE);
                    vul(x, py + 1, z, x, py + 8, z, Material.AIR);
                    boolean rand = x == 1338 || z == 316 || z == 326 || (x == 1332 && (z < 323 || z > 325));
                    if (rand) {
                        zet(x, py + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                        zet(x, py + 2, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    }
                }
            for (int[] c : new int[][]{{1338, 316}, {1338, 326}}) {
                vul(c[0], py + 1, c[1], c[0], py + 3, c[1], Material.POLISHED_BLACKSTONE_BRICKS);
                zet(c[0], py + 4, c[1], Material.SOUL_LANTERN);
            }
        }
        bakenKern = new Location(w, CX, KF + 5, CZ);
        bakenVuur = new Location(w, CX, KF + 2, CZ);
        uitkijk = Box.van(1333, KF + 10, 317, 1337, KF + 16, 325);
        verhaal(new Verhaal("krater", Box.van(1318, KF, 328, 1323, KF + 4, 333), List.of(
                r("Wiske", "De krater! Daar staat het Gloedbaken."),
                r("Wiske", "Koud en donker. Jouw vuurstaf!")))).als = s -> !s.heeft("baken4");
        rustvuur(1317, KF, 335);
        rust(new Rust("krater", "De krater", plek(1319, KF + 1, 331, 90f), Box.van(1314, KF, 326, 1322, KF + 5, 338), List.of()));
        Reeks baken = reeks(new Reeks("gloedbaken", "Het Gloedbaken", Reeks.Hoe.VUUR, ZeldaEiland4a.vuur(Material.CAMPFIRE, false), ZeldaEiland4a.vuur(Material.CAMPFIRE, true),
                new Deur(Material.POLISHED_BLACKSTONE_BRICKS.createBlockData(), Material.SHROOMLIGHT.createBlockData())
                        .blok(CX - 1, KF + 5, CZ - 1).blok(CX + 1, KF + 5, CZ - 1).blok(CX - 1, KF + 5, CZ + 1).blok(CX + 1, KF + 5, CZ + 1),
                Box.van(CX - 11, KF - 1, CZ - 11, CX + 11, KF + 12, CZ + 11), 20));
        baken.dingen.add(new int[]{CX, KF + 2, CZ});
        baken.voor = plek(1321, KF + 1, 330, 90f);
        baken.bijOplossen.add(r("Wiske", "Het Gloedbaken vat vlam!"));
        Vondst vlag = vondst(new Vondst("baken4", "Het Gloedbaken", "brandt weer", Material.NETHER_STAR, plek(CX + 2, KF + 1.6, CZ, 0f),
                Box.van(CX + 1, KF + 1, CZ - 1, CX + 3, KF + 3, CZ + 1)));
        vlag.vereist = "r:gloedbaken";
        vlag.daarna = (spel, p, s) -> spel.vertel(p, r("Wiske", "Kom, de trap op! Naar de uitkijk."));
        Verhaal einde = verhaal(new Verhaal(NEVELRING, uitkijk, List.of(
                r("Wiske", "Kijk, in het noorden! Het licht van de drie bakens."),
                r("Wiske", "Het raakt de nevel… hij breekt!"),
                r("Wiske", "De Nevelring is weg. Nu kunnen we naar Nachtrots!"),
                r(null, "De Gloedberg · het Gloedbaken brandt"))));
        einde.vereist = "baken4";
        einde.einde = true;
        Persoon tijmen = persoon(new Persoon("Tijmen", plek(1336, KF + 11, 320, 135f), null, List.of(r("Tijmen", "Kijk niet te lang in het licht."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.zichtbaar = s -> s.heeft("baken4");
        tijmen.eenmalig("tijmen-ring", s -> s.heeft("baken4"), List.of(
                        r("Wiske", "Tijmen! Hoe kom jij hier nou weer?"),
                        r("Tijmen", "De Nevelring is gebroken. Morgal weet nu dat we komen."),
                        r("Tijmen", "Daar in het noorden ligt Nachtrots. Daar zien we elkaar weer.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
    }

    /**
     * Zonder blokken (dus zonder herbouw): de doelen voor {@code /hint} en Tijmen, na die van 4a. De Smidse in, Tijmen; dan
     * in elke volgorde (doelgroep {@code smidse}) het oude schild, de drie vonken en Okker (het Gloedschild); de Hartdeur,
     * Asgrim, het hart, de schoorsteen, het Gloedbaken en de uitkijk.
     */
    private void motor() {
        doel("smidse4b", "De Smidsepoort gaat open!", "Loop door de poort, de Smidse in.",
                rust("smidsehal").plek, s -> s.bezocht(id(), 0));
        doel("tijmen4b", "Wie leest daar het oude schrift?", "Praat met Tijmen, bij de Hartdeur.",
                plekVan("Tijmen"), s -> s.heeft("g:tijmen-smidse") || alleVonken(s));
        doel("smidse", SCHILDPLAAT, "Op het grote aambeeld ligt iets.", "Pak het oude schild, midden in de hal.",
                plekVan(SCHILDPLAAT), s -> s.heeft(SCHILDPLAAT) || s.heeft(ZeldaSpel.GLOEDSCHILD));
        doel("smidse", VONK_KETEN, "Noord: een kloof vol lava.", "Haak je naar de pijler in de Kettingzaal. Daar ligt een vonk.",
                plekVan(VONK_KETEN), s -> s.heeft(VONK_KETEN));
        doel("smidse", VONK_DAM, "Achter een dam ruist water.", "Een bom tegen de dam. Water op lava wordt steen.",
                plekVan("stuwdam"), s -> s.heeft(VONK_DAM));
        doel("smidse", VONK_GIET, "Oost: een rivier van gloeiend ijzer.", "Steek de drie ovens aan. Dan zakt de lava.",
                plekVan("ovens"), s -> s.heeft(VONK_GIET));
        doel("smidse", "gloedschild", "Okker kan dat oude schild smeden.", "Breng het schild naar Okker, in Sintelgat.",
                okkerPlek(), s -> s.heeft(ZeldaSpel.GLOEDSCHILD) || !s.heeft(SCHILDPLAAT));
        doel("hartdeur", "Drie vonken. Naar de Hartdeur!", "De Hartdeur is in de zuidmuur van de Smidsehal.",
                plekVan("hartdeur"), s -> s.heeft("v:hartgang") || s.heeft("k:smidshart"));
        doel("asgrim", "Daarachter wacht Asgrim.", "Schiet op de kuip boven hem. Dán slaan!",
                plekVan("smidshart"), s -> s.heeft("k:smidshart") || s.heeft("hart4"));
        doel("hart4", "Asgrim is vrij. Pak het hart van licht.", "Op het eilandje in het midden.",
                plekVan("hart4"), s -> s.heeft("hart4"));
        doel("schoorsteen", "Het baken is boven, in de krater.", "Achter het Hart: haak je de schoorsteen op.",
                rust("schoorsteen").plek, s -> s.bezocht(id(), 3));
        doel("gloedbaken", "Het Gloedbaken is koud.", "Richt de vuurstaf op het baken.",
                plekVan("gloedbaken"), s -> s.heeft("r:gloedbaken") || s.heeft("baken4"));
        doel("baken4", "Het Gloedbaken brandt!", "Loop naar het baken, over het bruggetje.",
                plekVan("baken4"), s -> s.heeft("baken4"));
        doel("uitkijk", "Kijk vanaf de uitkijk naar het noorden.", "De trap op, aan de oostkant van de krater.",
                uitkijk.midden(w), s -> s.heeft(id()));
    }

    // ── tijdens het spel: vuurschalen bij de Hartdeur, water bij de dam, nevel, het baken, de Nevelring ─

    @Override
    void tik(ZeldaSpel spel, List<Player> wie, long nu) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (Player p : wie) {
            if (!p.getWorld().equals(w) || spel.beheer.is(p.getUniqueId())) continue;
            ZeldaVoortgang.Stand s = spel.voortgang.van(p.getUniqueId());
            Location l = p.getLocation();
            if (hal != null && hal.groter(4).in(l)) for (int i = 0; i < 3; i++) {       // de vuurschalen boven de Hartdeur: één per vonk
                int[] c = schalen[i];
                p.sendBlockChange(new Location(w, c[0], c[1], c[2]), ZeldaEiland4a.vuur(Material.CAMPFIRE, s.heeft(VONKEN[i])));
            }
            if (stuwdam != null && stuwdam.muur.isOpen && l.distanceSquared(new Location(w, 1316, D, 252)) < 30 * 30) {   // het water stroomt over de rand
                for (int i = 0; i < 6; i++) {
                    double x = 1315 + r.nextDouble() * 3, z = 246.5 + r.nextDouble() * 5.5;
                    p.spawnParticle(Particle.FALLING_WATER, x, D + 1.2 + r.nextDouble() * 1.5, z, 1, 0, 0, 0, 0);
                    p.spawnParticle(Particle.SPLASH, x, D + 1.1, z, 2, 0.2, 0, 0.2, 0.05);
                }
                if (koelpad != null && koelpad.deur.isOpen) p.spawnParticle(Particle.CLOUD, 1316.5, D + 1.2, 252 + r.nextDouble() * 10, 2, 0.6, 0.1, 0.3, 0.01);
                if (r.nextInt(3) == 0) p.playSound(new Location(w, 1316.5, D + 1, 249.5), org.bukkit.Sound.BLOCK_WATER_AMBIENT, org.bukkit.SoundCategory.AMBIENT, 0.6f, 1f);
            }
            if (nis != null && nevelnis != null && !nevelnis.nevel.isOpen && nis.groter(10).in(l))   // Morgals nevel in de nis (tot een lichtpijl hem wegschiet)
                for (int i = 0; i < 6; i++) p.spawnParticle(Particle.LARGE_SMOKE, nis.x1() + r.nextDouble() * 3, D + 1 + r.nextDouble() * 2.5, nis.z1() + 1 + r.nextDouble() * 2, 1, 0, 0.02, 0, 0.005);
            boolean buiten = w.getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) <= l.getBlockY() + 1;
            if (buiten) {
                for (int i = 0; i < 6; i++) p.spawnParticle(Particle.WHITE_ASH, l.clone().add(r.nextDouble(-10, 10), r.nextDouble(2, 8), r.nextDouble(-10, 10)), 2, 0.5, 0.5, 0.5, 0);
                if (!s.heeft("baken4")) Particle.CAMPFIRE_SIGNAL_SMOKE.builder().location(new Location(w, CX + 0.5 + r.nextDouble(-3, 3), KF + 2, CZ + 0.5 + r.nextDouble(-3, 3)))
                        .offset(0.6, 0.4, 0.6).speed(0.02).count(2).receivers(p).force(true).spawn();
            } else for (Location v : lava)                                              // lava die sputtert, als je in de buurt bent
                if (v.distanceSquared(l) < 18 * 18 && r.nextInt(3) == 0) {
                    p.spawnParticle(Particle.LAVA, v, 1, 0.3, 0.1, 0.3, 0);
                    if (r.nextInt(5) == 0) p.playSound(v, org.bukkit.Sound.BLOCK_LAVA_POP, org.bukkit.SoundCategory.AMBIENT, 0.4f, 0.9f + r.nextFloat() * 0.3f);
                }
            if (s.heeft("v:" + NEVELRING) && !s.heeft(NEVELRING) && breekt.add(p.getUniqueId())) nevelring(spel, p);   // de Nevelring breekt
        }
        baken(spel);
    }

    /**
     * Wie het Gloedbaken aanstak ({@code baken4}), ziet het branden: de kroon gloeit, het vuur brandt en er gaat een bundel
     * omhoog, te zien van overal op de Gloedberg (ook vanuit Sintelgat).
     */
    private void baken(ZeldaSpel spel) {
        if (bakenKern == null || eiland == null) return;
        BlockData kroon = Material.SHROOMLIGHT.createBlockData(), vuur = ZeldaEiland4a.vuur(Material.CAMPFIRE, true);
        Particle.DustOptions licht = new Particle.DustOptions(org.bukkit.Color.fromRGB(0xffa040), 2.2f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            ZeldaSpel.Sessie ss = spel.sessie(p);
            if (ss == null || ss.vaart != null || ss.h.eiland != eiland || !spel.voortgang.van(p.getUniqueId()).heeft("baken4")) continue;
            if (!p.getWorld().equals(w) || p.getLocation().distanceSquared(bakenKern) > 260 * 260) continue;
            for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) p.sendBlockChange(bakenKern.clone().add(dx, 0, dz), kroon);
            p.sendBlockChange(bakenVuur, vuur);
            for (int i = 2; i < 46; i += 2) p.spawnParticle(Particle.DUST, bakenKern.clone().add(0.5, i, 0.5), 1, 0.15, 0.3, 0.15, 0, licht);
        }
    }

    /**
     * De Nevelring breekt (één keer, op de uitkijk): het licht van het Gloedbaken gaat omhoog, de bundels van het Getijdenbaken
     * en het Vorstbaken komen uit het noorden, samen raken ze de muur van zwarte nevel aan de horizon, en die spat uiteen.
     * Alleen voor deze speler (deeltjes en geluid), een paar tellen lang.
     */
    private void nevelring(ZeldaSpel spel, Player p) {
        Location oog = p.getEyeLocation();
        org.bukkit.util.Vector noord = new org.bukkit.util.Vector(0.55, 0, -0.84).normalize();       // naar Nachtrots (noordoost)
        Location ring = oog.clone().add(noord.clone().multiply(70)).add(0, 6, 0);
        Location samen = oog.clone().add(noord.clone().multiply(30)).add(0, 26, 0);
        Particle.DustOptions zwart = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x15121c), 4f);
        Particle.DustOptions oranje = new Particle.DustOptions(org.bukkit.Color.fromRGB(0xffa040), 2.5f);
        Particle.DustOptions blauw = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x7fd6ff), 2.5f);
        Particle.DustOptions wit = new Particle.DustOptions(org.bukkit.Color.fromRGB(0xf2fbff), 2.5f);
        org.bukkit.util.Vector opzij = new org.bukkit.util.Vector(-noord.getZ(), 0, noord.getX());
        final int[] t = {0};
        final org.bukkit.scheduler.BukkitTask[] taak = {null};
        taak[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            t[0]++;
            if (!p.isOnline() || t[0] > 240) {
                taak[0].cancel();
                breekt.remove(p.getUniqueId());
                if (spel.voortgang.van(p.getUniqueId()).zet(NEVELRING)) spel.voortgang.vuil();
                return;
            }
            int n = t[0];
            if (n <= 170)                                                                      // de muur van nevel aan de horizon
                for (int i = 0; i < 18; i++) {
                    double a = (i / 17.0 - 0.5) * 70, h = (n % 9 + i * 7) % 26;
                    Location m = ring.clone().add(opzij.clone().multiply(a)).add(0, h - 8, 0);
                    if (n > 130) m.add(m.toVector().subtract(ring.toVector()).normalize().multiply((n - 130) * 0.8));   // hij spat uiteen
                    if (n <= 130 || i % 2 == 0) spel.deeltje(p, Particle.DUST, m, 1, 1.5, 1.5, 1.5, zwart);
                }
            if (n >= 10 && n <= 200)                                                           // het Gloedbaken: een bundel omhoog
                for (int i = 0; i < 6; i++) spel.deeltje(p, Particle.DUST, bakenKern.clone().add(0.5, 2 + ((n * 2 + i * 7) % 40), 0.5), 1, 0.2, 0.3, 0.2, oranje);
            if (n >= 40 && n <= 200) {                                                         // de twee andere bakens, uit het noorden
                for (int k = 0; k < 2; k++) {
                    Location ver = oog.clone().add(noord.clone().multiply(60)).add(opzij.clone().multiply(k == 0 ? -55 : -20)).add(0, 4, 0);
                    double f = Math.min(1, (n - 40) / 40.0);
                    for (int i = 0; i < 8; i++) {
                        double q = (i / 8.0) * f;
                        Location m = ver.clone().add(samen.toVector().subtract(ver.toVector()).multiply(q));
                        spel.deeltje(p, Particle.DUST, m, 1, 0.2, 0.2, 0.2, k == 0 ? blauw : wit);
                    }
                }
                double f = Math.min(1, Math.max(0, (n - 40) / 40.0));
                for (int i = 0; i < 8; i++)
                    spel.deeltje(p, Particle.DUST, bakenKern.clone().add(0.5, 3, 0.5).add(samen.toVector().subtract(bakenKern.toVector()).multiply(i / 8.0 * f)), 1, 0.2, 0.2, 0.2, oranje);
            }
            if (n >= 90 && n <= 135) {                                                         // samen: een witte bundel naar de nevel
                double f = Math.min(1, (n - 90) / 35.0);
                for (int i = 0; i < 12; i++) {
                    Location m = samen.clone().add(ring.toVector().subtract(samen.toVector()).multiply(i / 12.0 * f));
                    spel.deeltje(p, Particle.END_ROD, m, 1, 0.1, 0.1, 0.1, null);
                }
            }
            if (n == 1) p.playSound(p, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
            if (n == 45) p.playSound(p, org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
            if (n == 90) p.playSound(p, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
            if (n == 130) {
                if (spel.voortgang.van(p.getUniqueId()).zet(NEVELRING)) spel.voortgang.vuil();   // de dag breekt aan
                p.playSound(p, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.6f);
                p.playSound(p, org.bukkit.Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
                spel.deeltje(p, Particle.FLASH, ring, 3, 2, 2, 2, org.bukkit.Color.WHITE);
                for (int i = 0; i < 40; i++) spel.deeltje(p, Particle.END_ROD, ring.clone().add(opzij.clone().multiply((i - 20) * 1.6)), 2, 1.5, 4, 1.5, null);
            }
            if (n == 170) p.playSound(p, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        }, 1L, 1L);
    }

    /** Op de kaart: de Smidse (in de vulkaan). */
    @Override
    List<Teken> tekens() {
        return List.of(new Teken("kerker", 1312, 330));
    }
}
