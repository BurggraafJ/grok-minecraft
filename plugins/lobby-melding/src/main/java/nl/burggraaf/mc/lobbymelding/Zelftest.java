package nl.burggraaf.mc.lobbymelding;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;

/**
 * {@code lobbymelding zelftest}: staat de tuin er zoals de tekening zegt? Eerste regel {@code OK …} of
 * {@code FOUT (n)}, daaronder de fouten. Controleert de levels (sprongen nagerekend, start en finish 3×3, ruimte
 * boven elke stap), elke stap en poortdoorgang in de wereld, de spawn, de labels en hoeveel blokken afwijken.
 */
final class Zelftest {

    private Zelftest() {
    }

    static void draai(LobbyMelding pl, CommandSender uit) {
        List<String> fout = new ArrayList<>();
        Tuin t = pl.tuin();
        if (t == null || !pl.klaar()) {
            uit.sendMessage("FOUT (1)");
            uit.sendMessage("  tuin is nog niet klaar (wordt getekend of gebouwd)");
            return;
        }
        World w = pl.wereld();
        BlockData[] palet = Bouwer.palet(t.p.palet(), pl.getLogger());
        if (t.banen.size() != 10) fout.add("aantal levels " + t.banen.size() + " (verwacht 10)");
        int stappen = 0;
        for (Baan b : t.banen) {
            fout.addAll(b.controleer(t.p));
            for (Baan.Stap s : b.stappen) {
                stappen++;
                for (int x = s.x1(); x <= s.x2(); x++)
                    for (int z = s.z1(); z <= s.z2(); z++) {
                        int y2 = s.soort() == Baan.Soort.LADDER ? s.top() : s.y();
                        for (int y = s.y(); y <= y2; y++) vergelijk(w, palet, t.p, x, y, z, "level " + b.nr, fout);
                        if (s.soort() != Baan.Soort.LADDER)
                            for (int y = s.y() + 1; y <= s.y() + 2; y++)
                                if (!w.getBlockAt(x, y, z).isPassable()) fout.add("level " + b.nr + ": geen ruimte boven de stap op " + x + "," + y + "," + z);
                    }
            }
        }
        for (Tuin.Poort p : t.poorten)
            for (int x = p.x1(); x <= p.x2(); x++)
                for (int h = 1; h <= 3; h++)
                    if (!w.getBlockAt(x, t.g + h, p.z()).isPassable()) fout.add("poort " + p.server() + " dicht op " + x + "," + (t.g + h) + "," + p.z());
        var sp = pl.spawnPlek();
        if (sp == null || !w.getBlockAt(sp).isPassable() || !w.getBlockAt(sp.clone().add(0, 1, 0)).isPassable()
                || w.getBlockAt(sp.clone().add(0, -1, 0)).isPassable()) fout.add("spawn staat niet vrij op een vloer");
        int verwacht = t.p.labels.size() + t.banen.size() + (t.prikbord != null ? 1 : 0) + t.p.dieren.size();
        int labels = pl.aantalLabels();
        if (labels < verwacht) fout.add("labels/dieren: " + labels + " (verwacht " + verwacht + ")");
        // hoeveel blokken wijken af van de tekening (deuren en hekjes die open staan tellen niet)
        int anders = 0;
        for (int x = t.p.x1; x <= t.p.x2; x++)
            for (int z = t.p.z1; z <= t.p.z2; z++)
                for (int y = t.g - 2; y <= t.p.y2; y++) {
                    BlockData wil = palet[t.p.paletIndex(x, y, z)];
                    BlockData is = w.getBlockAt(x, y, z).getBlockData();
                    if (is.equals(wil)) continue;
                    if (is.getMaterial() == wil.getMaterial() && (is instanceof org.bukkit.block.data.Openable)) continue;
                    anders++;
                }
        if (anders > 0) fout.add(anders + " blokken wijken af van de tekening (lobbymelding bouw zet ze terug)");
        if (fout.isEmpty()) {
            uit.sendMessage("OK (" + t.banen.size() + " levels, " + stappen + " stappen, " + t.poorten.size() + " poorten, " + labels + " labels/dieren)");
        } else {
            uit.sendMessage("FOUT (" + fout.size() + ")");
            for (String f : fout.subList(0, Math.min(30, fout.size()))) uit.sendMessage("  " + f);
        }
    }

    private static void vergelijk(World w, BlockData[] palet, Plan p, int x, int y, int z, String wie, List<String> fout) {
        Block b = w.getBlockAt(x, y, z);
        BlockData wil = palet[p.paletIndex(x, y, z)];
        if (!b.getBlockData().equals(wil)) fout.add(wie + ": op " + x + "," + y + "," + z + " staat " + b.getType().name().toLowerCase() + " i.p.v. " + wil.getMaterial().name().toLowerCase());
    }
}
