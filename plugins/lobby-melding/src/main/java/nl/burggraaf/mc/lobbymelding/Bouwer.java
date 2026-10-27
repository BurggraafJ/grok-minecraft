package nl.burggraaf.mc.lobbymelding;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Zet een {@link Plan} in de wereld, chunk voor chunk, met hooguit ±20 ms per tick (geen lagpiek bij de start).
 * Vergelijkt eerst wat er staat en zet alleen wat anders is, dus een tweede keer bouwen verandert niets
 * (idempotent). Buiten de tekening, tot {@code opruimStraal}, wordt alles teruggezet naar plat gras: zo verdwijnen
 * bouwsels van een oudere, grotere tuin.
 */
final class Bouwer {

    static final int OPRUIM_HOOGTE = 48;      // tot g + 48 (de oude tuin kwam tot g + 40)

    private final Plugin plugin;
    private final World w;
    private final Plan plan;
    private final int opruimStraal;
    private final Runnable klaar;
    private final Logger log;
    private BlockData[] palet;
    private BlockData gras, aarde, lucht;
    private final List<int[]> chunks = new ArrayList<>();
    private int volgende = 0;
    private BukkitTask taak;
    int gezet = 0;
    long gestart;

    Bouwer(Plugin plugin, World w, Plan plan, int opruimStraal, Runnable klaar) {
        this.plugin = plugin;
        this.w = w;
        this.plan = plan;
        this.opruimStraal = Math.max(opruimStraal, Math.max(-plan.x1, plan.x2 + 1));
        this.klaar = klaar;
        this.log = plugin.getLogger();
    }

    /** Bloktoestanden uit de tekening omzetten; een fout (onbekend blok) wordt gemeld en wordt lucht. */
    static BlockData[] palet(List<String> namen, Logger log) {
        BlockData[] uit = new BlockData[namen.size()];
        for (int i = 0; i < namen.size(); i++) {
            try {
                uit[i] = Bukkit.createBlockData("minecraft:" + namen.get(i));
            } catch (IllegalArgumentException e) {
                log.warning("onbekend blok in de tuin: " + namen.get(i));
                uit[i] = Material.AIR.createBlockData();
            }
        }
        return uit;
    }

    void start() {
        gestart = System.currentTimeMillis();
        palet = palet(plan.palet(), log);
        gras = Material.GRASS_BLOCK.createBlockData();
        aarde = Material.DIRT.createBlockData();
        lucht = Material.AIR.createBlockData();
        int r = opruimStraal;
        for (int cx = Math.floorDiv(-r, 16); cx <= Math.floorDiv(r - 1, 16); cx++)
            for (int cz = Math.floorDiv(-r, 16); cz <= Math.floorDiv(r - 1, 16); cz++) chunks.add(new int[]{cx, cz});
        // van binnen naar buiten: de tuin zelf eerst
        chunks.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        taak = Bukkit.getScheduler().runTaskTimer(plugin, this::tik, 1L, 1L);
    }

    boolean bezig() {
        return taak != null;
    }

    private void tik() {
        long t0 = System.nanoTime();
        while (volgende < chunks.size() && System.nanoTime() - t0 < 20_000_000L) {
            int[] c = chunks.get(volgende++);
            chunk(c[0], c[1]);
        }
        if (volgende >= chunks.size()) {
            taak.cancel();
            taak = null;
            log.info("tuin gebouwd: " + gezet + " blokken gezet in " + (System.currentTimeMillis() - gestart) + " ms (" + chunks.size() + " chunks)");
            klaar.run();
        }
    }

    private void chunk(int cx, int cz) {
        Chunk ch = w.getChunkAt(cx, cz);
        ChunkSnapshot snap = ch.getChunkSnapshot(false, false, false);
        int g = plan.g, r = opruimStraal;
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                if (x < -r || x >= r || z < -r || z >= r) continue;
                boolean inPlan = x >= plan.x1 && x <= plan.x2 && z >= plan.z1 && z <= plan.z2;
                for (int y = g - 2; y <= g + OPRUIM_HOOGTE; y++) {
                    BlockData wil;
                    if (inPlan && y <= plan.y2) wil = palet[plan.paletIndex(x, y, z)];
                    else wil = y < g ? aarde : y == g ? gras : lucht;
                    if (snap.getBlockData(lx, y, lz).equals(wil)) continue;
                    Block b = w.getBlockAt(x, y, z);
                    b.setBlockData(wil, false);
                    gezet++;
                }
            }
    }
}
