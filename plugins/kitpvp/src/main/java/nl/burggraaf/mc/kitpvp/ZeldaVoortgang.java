package nl.burggraaf.mc.kitpvp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Voortgang in Story of Zelda, per speler: per hoofdstuk de verste rustplaats (checkpoint), vlaggen (vondsten zoals
 * {@code zwaard}, gezien verhaal {@code v:intro}, voltooid {@code h1}) en beste tijden van uitdagingen. Staat in
 * {@code plugins/KitPvP/zelda.yml}; zo ga je de volgende dag verder waar je was.
 */
final class ZeldaVoortgang {

    /** Wat één speler heeft bereikt. */
    static final class Stand {
        String naam = "";
        String hoofdstuk;                                       // waar je verder gaat; null = het eerste
        final Map<String, Integer> rust = new LinkedHashMap<>();
        final Set<String> vlaggen = new LinkedHashSet<>();
        final Map<String, Long> tijden = new LinkedHashMap<>();

        /** De verste rustplaats in dit hoofdstuk (0 = het begin). */
        int rust(String h) {
            return rust.getOrDefault(h, 0);
        }

        /** Rustplaats {@code i} bereikt; true als dat verder is dan ooit (dan is het bewaard). */
        boolean bereik(String h, int i) {
            if (i <= rust(h)) return false;
            rust.put(h, i);
            return true;
        }

        boolean heeft(String vlag) {
            return vlaggen.contains(vlag);
        }

        /** true als de vlag nieuw is. */
        boolean zet(String vlag) {
            return vlaggen.add(vlag);
        }

        Long beste(String uitdaging) {
            return tijden.get(uitdaging);
        }

        /** Een gehaalde tijd; true als het een nieuw record is (of de eerste keer). */
        boolean tijd(String uitdaging, long ms) {
            Long oud = tijden.get(uitdaging);
            if (oud != null && oud <= ms) return false;
            tijden.put(uitdaging, ms);
            return true;
        }

        Stand kopie() {
            Stand s = new Stand();
            s.naam = naam;
            s.hoofdstuk = hoofdstuk;
            s.rust.putAll(rust);
            s.vlaggen.addAll(vlaggen);
            s.tijden.putAll(tijden);
            return s;
        }
    }

    private final File bestand;
    private final Map<UUID, Stand> standen = new HashMap<>();
    private boolean vuil;

    ZeldaVoortgang(File bestand) {
        this.bestand = bestand;
        laad();
    }

    File bestand() {
        return bestand;
    }

    Stand van(UUID id) {
        return standen.computeIfAbsent(id, k -> new Stand());
    }

    /** De beste tijden van een uitdaging: naam en ms, snelste eerst. */
    List<Map.Entry<String, Long>> besteTijden(String uitdaging, int n) {
        List<Map.Entry<String, Long>> l = new java.util.ArrayList<>();
        for (Stand s : standen.values()) {
            Long t = s.tijden.get(uitdaging);
            if (t != null) l.add(Map.entry(s.naam == null || s.naam.isEmpty() ? "?" : s.naam, t));
        }
        l.sort(Map.Entry.comparingByValue());
        return l.subList(0, Math.min(n, l.size()));
    }

    boolean bekend(UUID id) {
        return standen.containsKey(id);
    }

    void zet(UUID id, Stand s) {
        standen.put(id, s);
        vuil = true;
    }

    void wis(UUID id) {
        if (standen.remove(id) != null) vuil = true;
    }

    int aantal() {
        return standen.size();
    }

    /** Er is iets veranderd dat bewaard moet worden. */
    void vuil() {
        vuil = true;
    }

    boolean isVuil() {
        return vuil;
    }

    void laad() {
        standen.clear();
        if (!bestand.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(bestand);
        ConfigurationSection sp = y.getConfigurationSection("spelers");
        if (sp == null) return;
        for (String k : sp.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(k);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection c = sp.getConfigurationSection(k);
            if (c == null) continue;
            Stand s = new Stand();
            s.naam = c.getString("naam", "");
            s.hoofdstuk = c.getString("hoofdstuk");
            ConfigurationSection r = c.getConfigurationSection("rust");
            if (r != null) for (String h : r.getKeys(false)) s.rust.put(h, r.getInt(h));
            s.vlaggen.addAll(c.getStringList("vlaggen"));
            ConfigurationSection t = c.getConfigurationSection("tijden");
            if (t != null) for (String u : t.getKeys(false)) s.tijden.put(u, t.getLong(u));
            standen.put(id, s);
        }
    }

    /** Schrijf alles weg (eerst naar een tijdelijk bestand, dan vervangen: nooit half geschreven). */
    void bewaar() throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Story of Zelda: voortgang per speler (verste rustplaats per hoofdstuk, vondsten, beste tijden in ms)."));
        for (Map.Entry<UUID, Stand> e : standen.entrySet()) {
            Stand s = e.getValue();
            String k = "spelers." + e.getKey();
            y.set(k + ".naam", s.naam);
            if (s.hoofdstuk != null) y.set(k + ".hoofdstuk", s.hoofdstuk);
            for (var r : s.rust.entrySet()) y.set(k + ".rust." + r.getKey(), r.getValue());
            y.set(k + ".vlaggen", List.copyOf(s.vlaggen));
            for (var t : s.tijden.entrySet()) y.set(k + ".tijden." + t.getKey(), t.getValue());
        }
        File map = bestand.getAbsoluteFile().getParentFile();
        if (map != null) map.mkdirs();
        File tmp = new File(map, bestand.getName() + ".tmp");
        y.save(tmp);
        Files.move(tmp.toPath(), bestand.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        vuil = false;
    }

    /** 21430 ms → «0:21,4». */
    static String tijd(long ms) {
        long tienden = Math.max(0, ms) / 100;
        return String.format(Locale.ROOT, "%d:%02d,%d", tienden / 600, (tienden / 10) % 60, tienden % 10);
    }
}
