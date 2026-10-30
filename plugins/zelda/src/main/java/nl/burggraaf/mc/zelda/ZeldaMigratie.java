package nl.burggraaf.mc.zelda;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * De verhuizing van Story of Zelda uit de plugin KitPvP (kitpvp 2.16 en ouder) naar deze plugin: eenmalig, idempotent,
 * en zonder iets weg te gooien.
 *
 * <ol>
 *   <li>{@link #data}: {@code zelda.yml} (alle spellen, niveaus, hartstukjes, de kaart), {@code zelda-kaarten.yml} (het
 *       kaartnummer per speler) en {@code zelda-kaart/} (het gemeten eiland) gaan van {@code plugins/KitPvP/} naar
 *       {@code plugins/Zelda/}. Eerst een backup in {@code plugins/Zelda/migratie/kitpvp-<tijd>/}; de oude bestanden
 *       blijven staan (KitPvP 2.17 leest ze niet meer, een terugval naar 2.16 wel). Staat er al een {@code zelda.yml},
 *       dan wordt niets overschreven.</li>
 *   <li>{@link #wereld}: de eilanden lagen in de kitpvp-wereld op x 1024…2047, z −512…511: precies vier regiobestanden.
 *       Die gaan (met hun entities- en poi-bestanden) naar de nieuwe Zelda-wereld, op dezelfde plek; dan staat alles er
 *       meteen en hoeft er niets herbouwd te worden. De bouwversies van de Zelda-delen en de grondhoogte gaan mee naar
 *       {@code plugins/Zelda/gebouwd.yml}. In de kitpvp-wereld blijft het oude gebied gewoon liggen (niemand komt er).</li>
 * </ol>
 * Wat gedaan is, staat in {@code plugins/Zelda/migratie.yml}; staat het daar, dan gebeurt het nooit meer.
 */
final class ZeldaMigratie {

    /** Het Zelda-gebied: regio's x 2…3, z −1…0 (zie {@link Zelda#GEBIED}). */
    static final int[][] REGIOS = {{2, -1}, {2, 0}, {3, -1}, {3, 0}};
    static final String[] SOORTEN = {"region", "entities", "poi"};
    /** Wat er aan bestanden meegaat (bestand of map in plugins/KitPvP). */
    static final String[] BESTANDEN = {"zelda.yml", "zelda-kaarten.yml", "zelda-kaart"};
    /** Bouwdelen die van Zelda zijn (alle andere in gebouwd.yml zijn van KitPvP). */
    static boolean zeldaDeel(String id) {
        return id.equals("zelda") || id.startsWith("zelda-");
    }

    private final File map, kitpvp, stand;
    private final Logger log;

    ZeldaMigratie(File map, File kitpvp, Logger log) {
        this.map = map;
        this.kitpvp = kitpvp;
        this.stand = new File(map, "migratie.yml");
        this.log = log;
    }

    private YamlConfiguration lees() {
        return YamlConfiguration.loadConfiguration(stand);
    }

    private void zet(String sleutel, Object waarde) {
        YamlConfiguration y = lees();
        y.set(sleutel, waarde);
        try {
            map.mkdirs();
            y.save(stand);
        } catch (IOException e) {
            log.warning("migratie.yml: " + e.getMessage());
        }
    }

    static String nu() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    // ── 1. de bestanden ─────────────────────────────────────────────────────────────────────

    /** Voortgang en kaarten overzetten (één keer). Geeft terug wat er gebeurde. */
    String data() {
        YamlConfiguration y = lees();
        if (y.contains("data.klaar")) return y.getString("data.klaar");
        File oud = new File(kitpvp, "zelda.yml");
        String uit;
        if (!oud.isFile()) uit = "geen oude voortgang";
        else if (new File(map, "zelda.yml").exists()) uit = "zelda.yml stond er al (niets overschreven)";
        else {
            try {
                File backup = new File(map, "migratie/kitpvp-" + nu());
                List<String> mee = new ArrayList<>();
                for (String b : BESTANDEN) {
                    File van = new File(kitpvp, b);
                    if (!van.exists()) continue;
                    kopie(van.toPath(), new File(backup, b).toPath());
                    mee.add(b);
                }
                for (String b : mee) {
                    File naar = new File(map, b);
                    if (!naar.exists()) kopie(new File(kitpvp, b).toPath(), naar.toPath());
                }
                if (!lijktGelijk(oud, new File(map, "zelda.yml"))) throw new IOException("zelda.yml is na het kopiëren niet gelijk");
                uit = "overgezet: " + String.join(", ", mee) + " (backup in " + map.getName() + "/migratie/" + backup.getName() + ")";
            } catch (IOException e) {
                log.severe("migratie: voortgang overzetten mislukt: " + e + " (de oude bestanden staan nog in plugins/KitPvP)");
                return "mislukt: " + e.getMessage();
            }
        }
        log.info("migratie: " + uit);
        zet("data.klaar", uit);
        zet("data.tijd", nu());
        return uit;
    }

    private static boolean lijktGelijk(File a, File b) throws IOException {
        return Files.mismatch(a.toPath(), b.toPath()) == -1;
    }

    /** Een bestand of hele map kopiëren (bestaande bestanden in het doel blijven staan). */
    static void kopie(Path van, Path naar) throws IOException {
        if (Files.isDirectory(van)) {
            try (Stream<Path> s = Files.walk(van)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    Path doel = naar.resolve(van.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(doel);
                    else if (!Files.exists(doel)) {
                        Files.createDirectories(doel.getParent());
                        Files.copy(p, doel, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
        } else {
            Files.createDirectories(naar.getParent());
            Files.copy(van, naar, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    // ── 2. de wereld ────────────────────────────────────────────────────────────────────────

    /** Moet de wereld nog overgezet worden (of, zonder oude eilanden, alleen nog gemarkeerd)? */
    boolean wereldNodig() {
        return !lees().contains("wereld.klaar");
    }

    /**
     * Zet de vier regiobestanden van het Zelda-gebied over van de map {@code van} (de kitpvp-wereld) naar {@code naar} (de
     * Zelda-wereld, die dan dicht moet zijn). Nooit overschrijven. Lukt het, dan gaan de bouwversies mee.
     */
    boolean wereld(File van, File naar) {
        int gekopieerd = 0, al = 0;
        List<String> fout = new ArrayList<>();
        for (String soort : SOORTEN)
            for (int[] r : REGIOS) {
                String naam = "r." + r[0] + "." + r[1] + ".mca";
                File a = new File(new File(van, soort), naam), b = new File(new File(naar, soort), naam);
                if (!a.isFile()) continue;
                if (b.exists()) {
                    al++;
                    continue;
                }
                try {
                    kopie(a.toPath(), b.toPath());
                    gekopieerd++;
                } catch (IOException e) {
                    fout.add(soort + "/" + naam + ": " + e.getMessage());
                }
            }
        String uit;
        if (!fout.isEmpty()) {
            uit = "deels mislukt (" + String.join("; ", fout) + "): de eilanden worden opnieuw gebouwd";
            log.severe("migratie: " + uit);
        } else if (gekopieerd == 0 && al == 0) {
            uit = "geen oude eilanden: alles wordt gebouwd";
        } else {
            uit = gekopieerd + " regiobestanden overgezet" + (al > 0 ? ", " + al + " stonden er al" : "");
            if (al == 0) bouwversies();
        }
        log.info("migratie: wereld " + uit);
        zet("wereld.klaar", uit);
        zet("wereld.tijd", nu());
        return gekopieerd > 0 && fout.isEmpty() && al == 0;
    }

    /** Na de overzet stond er geen eiland (verkeerde map?): alles wordt gebouwd; dat staat er dan ook bij. */
    void nietGelukt(String waarom) {
        log.severe("migratie: de overgezette eilanden staan er niet (" + waarom + "): alles wordt uit code gebouwd");
        zet("wereld.klaar", lees().getString("wereld.klaar", "") + "; maar " + waarom + ": opnieuw gebouwd");
    }

    /** De bouwversies van de Zelda-delen (en de grondhoogte) uit KitPvP's gebouwd.yml: wat er al staat, wordt niet herbouwd. */
    private void bouwversies() {
        YamlConfiguration kit = YamlConfiguration.loadConfiguration(new File(kitpvp, "gebouwd.yml"));
        File doel = new File(map, "gebouwd.yml");
        YamlConfiguration wij = YamlConfiguration.loadConfiguration(doel);
        if (kit.contains("grond") && !wij.contains("grond")) wij.set("grond", kit.getInt("grond"));
        var delen = kit.getConfigurationSection("delen");
        if (delen != null)
            for (String id : delen.getKeys(false))
                if (zeldaDeel(id) && !wij.contains("delen." + id)) wij.set("delen." + id, delen.getInt(id));
        try {
            map.mkdirs();
            wij.save(doel);
        } catch (IOException e) {
            log.warning("gebouwd.yml: " + e.getMessage());
        }
    }

    /** De grondhoogte: uit gebouwd.yml (overgezet van KitPvP), anders zoals KitPvP hem bepaalt (het hoogste blok op 0,0). */
    int grond(World w) {
        YamlConfiguration wij = YamlConfiguration.loadConfiguration(new File(map, "gebouwd.yml"));
        if (wij.contains("grond")) return wij.getInt("grond");
        YamlConfiguration kit = YamlConfiguration.loadConfiguration(new File(kitpvp, "gebouwd.yml"));
        if (kit.contains("grond")) return kit.getInt("grond");
        return w.getHighestBlockYAt(0, 0);
    }

    /**
     * De map met {@code region/} van een wereld. Paper 26 geeft voor een eigen wereld de dimensiemap zelf
     * ({@code world/dimensions/minecraft/zelda}, waar {@code region/} pas komt als er iets bewaard is) en voor de hoofdwereld
     * de wereldmap ({@code world}, met de dimensie onder {@code dimensions/minecraft/overworld}); oudere versies alles direct
     * in de wereldmap.
     */
    static File dimensie(World w) {
        return dimensie(w.getWorldFolder(), w.getKey().getNamespace(), w.getKey().getKey());
    }

    static File dimensie(File basis, String ns, String naam) {
        File ouder = basis.getAbsoluteFile().getParentFile();
        if (basis.getName().equals(naam) && ouder != null && ouder.getName().equals(ns) && ouder.getParentFile() != null
                && ouder.getParentFile().getName().equals("dimensions")) return basis;           // al de dimensiemap
        File d = new File(basis, "dimensions/" + ns + "/" + naam);
        if (new File(d, "region").isDirectory()) return d;
        if (new File(basis, "region").isDirectory()) return basis;
        return d.isDirectory() ? d : basis;
    }

    /** Een oude plek (kitpvp 2.16): in de kitpvp-wereld, in het Zelda-gebied. Wie daar uitlogde, komt in Zelda terug. */
    static boolean inOudGebied(Location l, World zelda) {
        if (l == null || l.getWorld() == null || l.getWorld().equals(zelda)) return false;
        int[] g = Zelda.GEBIED;
        return l.getX() >= g[0] && l.getX() <= g[2] + 1 && l.getZ() >= g[1] && l.getZ() <= g[3] + 1;
    }

    // ── stand ───────────────────────────────────────────────────────────────────────────────

    String stand() {
        YamlConfiguration y = lees();
        return "data: " + y.getString("data.klaar", "nog niet") + " (" + y.getString("data.tijd", "-") + ") · wereld: "
                + y.getString("wereld.klaar", "nog niet") + " (" + y.getString("wereld.tijd", "-") + ")";
    }

    String kort() {
        YamlConfiguration y = lees();
        return (y.contains("data.klaar") ? "data" : "-") + "+" + (y.contains("wereld.klaar") ? "wereld" : "-");
    }
}
