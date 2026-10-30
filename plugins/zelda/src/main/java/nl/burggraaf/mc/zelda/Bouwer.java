package nl.burggraaf.mc.zelda;

import java.util.ArrayDeque;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Zet blokken uit code, verdeeld over ticks (een paar ms per tick), zodat een herbouw geen lag-piek geeft. Alles gaat
 * in volgorde in een rij; {@link #start} werkt hem af. {@code zet}/{@code vul} doen alleen iets als {@link #plaats}
 * aan staat (het deel wordt herbouwd); {@code ...Altijd} en {@link #daarna} gebeuren altijd (bv. de staat van een
 * poort). Labels ({@link #label}) komen er meteen.
 */
final class Bouwer {

    final World w;
    final int g;                        // grondhoogte (vloer van de arena's)
    boolean plaats;
    private final Plugin plugin;
    private final ArrayDeque<Stap> rij = new ArrayDeque<>();
    private BukkitTask taak;
    long gezet;

    @FunctionalInterface
    private interface Stap {
        /** @return true als hij klaar is */
        boolean doe(long tot);
    }

    Bouwer(Plugin plugin, World w, int g) {
        this.plugin = plugin;
        this.w = w;
        this.g = g;
    }

    // ── blokken ──────────────────────────────────────────────────────────────────────────────

    void zet(int x, int y, int z, Material m) {
        if (plaats) zetAltijd(x, y, z, m);
    }

    void zet(int x, int y, int z, BlockData d) {
        if (plaats) zetAltijd(x, y, z, d);
    }

    void vul(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        if (plaats) vulAltijd(x1, y1, z1, x2, y2, z2, m);
    }

    void zetAltijd(int x, int y, int z, Material m) {
        rij.add(t -> {
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() != m) {
                b.setType(m, false);
                gezet++;
            }
            return true;
        });
    }

    void zetAltijd(int x, int y, int z, BlockData d) {
        rij.add(t -> {
            Block b = w.getBlockAt(x, y, z);
            if (!b.getBlockData().equals(d)) {
                b.setBlockData(d, false);
                gezet++;
            }
            return true;
        });
    }

    void vulAltijd(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), ay = Math.min(y1, y2), by = Math.max(y1, y2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        rij.add(new Stap() {
            int x = ax, y = ay, z = az;

            public boolean doe(long tot) {
                int n = 0;
                while (x <= bx) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != m) {
                        b.setType(m, false);
                        gezet++;
                    }
                    if (++z > bz) {
                        z = az;
                        if (++y > by) {
                            y = ay;
                            x++;
                        }
                    }
                    if ((++n & 255) == 0 && System.nanoTime() > tot) return x > bx;
                }
                return true;
            }
        });
    }

    /**
     * Iets per kolom (x, z) in een rechthoek, chunk voor chunk (zo wordt elke chunk één keer geladen), over de ticks
     * verdeeld. Alleen als {@link #plaats}.
     */
    void perKolom(int x1, int z1, int x2, int z2, java.util.function.BiConsumer<Integer, Integer> kolom) {
        if (!plaats) return;
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        rij.add(new Stap() {
            int cx = Math.floorDiv(ax, 16), cz = Math.floorDiv(az, 16), i = 0;

            public boolean doe(long tot) {
                while (cx <= Math.floorDiv(bx, 16)) {
                    int x = cx * 16 + (i & 15), z = cz * 16 + (i >> 4);
                    if (x >= ax && x <= bx && z >= az && z <= bz) kolom.accept(x, z);
                    if (++i == 256) {
                        i = 0;
                        if (++cz > Math.floorDiv(bz, 16)) {
                            cz = Math.floorDiv(az, 16);
                            cx++;
                        }
                        if (System.nanoTime() > tot) return cx > Math.floorDiv(bx, 16);
                    }
                }
                return true;
            }
        });
    }

    /** Iets dat na de blokken hiervoor moet gebeuren (bv. tekst op een bord). Altijd. */
    void daarna(Runnable r) {
        rij.add(t -> {
            r.run();
            return true;
        });
    }

    boolean bezig() {
        return taak != null;
    }

    int inRij() {
        return rij.size();
    }

    /** Werk de rij af: zonder spelers 25 ms per tick, met spelers 6 ms. Daarna {@code klaar}. */
    void start(Runnable klaar) {
        taak = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long tot = System.nanoTime() + (Bukkit.getOnlinePlayers().isEmpty() ? 25 : 6) * 1_000_000L;
            while (!rij.isEmpty() && System.nanoTime() < tot) {
                if (rij.peek().doe(tot)) rij.poll();
            }
            if (rij.isEmpty()) {
                taak.cancel();
                taak = null;
                klaar.run();
            }
        }, 1L, 1L);
    }

    void stop() {
        if (taak != null) taak.cancel();
        taak = null;
        rij.clear();
    }

    // ── blokdata ─────────────────────────────────────────────────────────────────────────────

    static BlockData trap(Material m, BlockFace f, boolean omgekeerd) {
        Stairs s = (Stairs) m.createBlockData();
        s.setFacing(f);
        if (omgekeerd) s.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
        return s;
    }

    static BlockData trap(Material m, BlockFace f) {
        return trap(m, f, false);
    }

    static BlockData blad(Material m) {
        Leaves l = (Leaves) m.createBlockData();
        l.setPersistent(true);
        return l;
    }

    static BlockData plaat(Material m, boolean boven) {
        Slab s = (Slab) m.createBlockData();
        s.setType(boven ? Slab.Type.TOP : Slab.Type.BOTTOM);
        return s;
    }

    static BlockData hangend(Material lantaarn) {
        Lantern l = (Lantern) lantaarn.createBlockData();
        l.setHanging(true);
        return l;
    }

    /** Blok dat ergens tegenaan hangt of kijkt (wandbanier, wandbord, fakkel). */
    static BlockData gericht(Material m, BlockFace f) {
        BlockData d = m.createBlockData();
        if (d instanceof Directional dir) dir.setFacing(f);
        else if (d instanceof Rotatable r) r.setRotation(f);
        return d;
    }

    /** IJzeren tralies of een glasruit die aan twee kanten aansluit (zonder physics doen ze dat niet vanzelf). */
    static BlockData ruit(Material m, BlockFace a, BlockFace b) {
        BlockData d = m.createBlockData();
        if (d instanceof MultipleFacing mf) {
            mf.setFace(a, true);
            mf.setFace(b, true);
        }
        return d;
    }

    /** Muurtje (stone brick wall e.d.) met een paal: zonder physics staat hij er als losse paal, dat is genoeg. */
    static BlockData paal(Material m) {
        return m.createBlockData();
    }

    static long hash(int x, int z) {
        long h = x * 73428767L ^ z * 912931L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        return Math.abs(h ^ (h >>> 15));
    }

    static long hash(int x, int y, int z) {
        return hash(x * 31 + y * 1013, z * 17 - y * 7);
    }

    // ── labels ───────────────────────────────────────────────────────────────────────────────

    TextDisplay label(Location l, Component tekst, float schaal) {
        return w.spawn(l, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(tekst);
            d.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(schaal, schaal, schaal), new org.joml.AxisAngle4f()));
            d.addScoreboardTag(ZeldaSpel.TAG);
        });
    }
}
