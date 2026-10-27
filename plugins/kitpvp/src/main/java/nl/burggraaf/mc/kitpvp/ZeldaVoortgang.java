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
 * Voortgang in Story of Zelda, per speler. Staat in {@code plugins/KitPvP/zelda.yml}; zo ga je de volgende dag verder
 * waar je was.
 * <ul>
 *   <li>{@code laatste}: de rustplaats waar je het laatst was ({@code h1:3}); daar kom je binnen en na doodgaan terug.
 *       Oude standen (zonder) vallen terug op de verste rustplaats van {@code hoofdstuk}.</li>
 *   <li>{@code rust}: per deel de verste rustplaats (index), {@code bezocht}: alle bereikte rustplaatsen ({@code h1:0},
 *       {@code e2a:3}; de kaart reist daarheen; bij het lezen aangevuld uit {@code rust}).</li>
 *   <li>{@code vlaggen}: vondsten ({@code zwaard}, {@code hart1}, hartstukje {@code hs:<id>}, {@code fles1},
 *       {@code harnas2}, {@code dier:vos}), gezien verhaal {@code v:<id>}, opgeloste puzzel {@code r:<id>}, gewonnen
 *       kamer {@code k:<id>}, gesprek {@code g:<id>}, voltooid {@code h1}, opties {@code o:<naam>}.</li>
 *   <li>{@code missies}: zijmissies, per id de stap (1 … n bezig, {@link #KLAAR} = klaar).</li>
 *   <li>{@code tellers}: getallen (bv. {@code fles-vol}); {@code dier}: de naam van je huisdier; {@code tijden}: beste
 *       tijden van uitdagingen in ms.</li>
 * </ul>
 */
final class ZeldaVoortgang {

    /** Wat één speler heeft bereikt. */
    static final class Stand {
        String naam = "";
        String hoofdstuk;                                       // waar je verder gaat; null = het eerste
        final Map<String, Integer> rust = new LinkedHashMap<>();
        final Set<String> vlaggen = new LinkedHashSet<>();
        final Map<String, Long> tijden = new LinkedHashMap<>();
        String laatste;                                         // "h1:3": waar je verder gaat; null = verste van hoofdstuk
        final Set<String> bezocht = new LinkedHashSet<>();      // "h1:0", "h1:3": bereikte rustplaatsen
        final Map<String, Integer> missies = new LinkedHashMap<>();
        final Map<String, Integer> tellers = new LinkedHashMap<>();
        String dier;                                            // naam van je huisdier (null = nog geen naam)

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

        boolean wis(String vlag) {
            return vlaggen.remove(vlag);
        }

        /** Hoeveel vlaggen met dit begin (bv. {@code hs:} = hartstukjes). */
        int aantal(String begin) {
            int n = 0;
            for (String v : vlaggen) if (v.startsWith(begin)) n++;
            return n;
        }

        /** Rustplaats {@code i} van deel {@code h} bezocht; true als dat nieuw is. */
        boolean bezoek(String h, int i) {
            return bezocht.add(h + ":" + i);
        }

        boolean bezocht(String h, int i) {
            return bezocht.contains(h + ":" + i);
        }

        /** Stap van zijmissie {@code id}: 0 = niet begonnen, 1 … n = bezig, {@link #KLAAR} = klaar. */
        int missie(String id) {
            return missies.getOrDefault(id, 0);
        }

        boolean missieKlaar(String id) {
            return missie(id) == KLAAR;
        }

        int teller(String id) {
            return tellers.getOrDefault(id, 0);
        }

        void teller(String id, int n) {
            if (n == 0) tellers.remove(id);
            else tellers.put(id, n);
        }

        /** Een getal dat telt hoe ver je bent (nieuwe vlag, rustplaats, missiestap): verandert = voortgang. */
        int voortgang() {
            int m = 0;
            for (var e : missies.entrySet()) m = 31 * m + e.getKey().hashCode() + e.getValue();
            return vlaggen.size() * 1009 + bezocht.size() * 31 + m;
        }

        /** Oude standen: de rustplaatsen tot en met de verste tellen als bezocht. */
        void vulBezocht() {
            for (var e : rust.entrySet()) for (int i = 0; i <= e.getValue(); i++) bezocht.add(e.getKey() + ":" + i);
        }

        Stand kopie() {
            Stand s = new Stand();
            s.naam = naam;
            s.hoofdstuk = hoofdstuk;
            s.rust.putAll(rust);
            s.vlaggen.addAll(vlaggen);
            s.tijden.putAll(tijden);
            s.laatste = laatste;
            s.bezocht.addAll(bezocht);
            s.missies.putAll(missies);
            s.tellers.putAll(tellers);
            s.dier = dier;
            return s;
        }
    }

    /** Stap van een voltooide zijmissie. */
    static final int KLAAR = 1000;

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

    /** De speler met deze naam (hoofdletters maken niet uit), of null. */
    UUID zoek(String naam) {
        if (naam == null) return null;
        for (Map.Entry<UUID, Stand> e : standen.entrySet())
            if (naam.equalsIgnoreCase(e.getValue().naam)) return e.getKey();
        return null;
    }

    /** Alle bekende spelersnamen (voor tab). */
    List<String> namen() {
        List<String> l = new java.util.ArrayList<>();
        for (Stand s : standen.values()) if (s.naam != null && !s.naam.isEmpty()) l.add(s.naam);
        l.sort(String.CASE_INSENSITIVE_ORDER);
        return l;
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
            s.laatste = c.getString("laatste");
            if (c.contains("bezocht")) s.bezocht.addAll(c.getStringList("bezocht"));
            else s.vulBezocht();                                 // oude stand: tot en met de verste telt als bezocht
            ConfigurationSection m = c.getConfigurationSection("missies");
            if (m != null) for (String u : m.getKeys(false)) s.missies.put(u, m.getInt(u));
            ConfigurationSection n = c.getConfigurationSection("tellers");
            if (n != null) for (String u : n.getKeys(false)) s.tellers.put(u, n.getInt(u));
            s.dier = c.getString("dier");
            standen.put(id, s);
        }
    }

    /** Schrijf alles weg (eerst naar een tijdelijk bestand, dan vervangen: nooit half geschreven). */
    void bewaar() throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Story of Zelda: voortgang per speler (laatste en bezochte rustplaatsen, verste per deel, vlaggen, missies, tellers, beste tijden in ms)."));
        for (Map.Entry<UUID, Stand> e : standen.entrySet()) {
            Stand s = e.getValue();
            String k = "spelers." + e.getKey();
            y.set(k + ".naam", s.naam);
            if (s.hoofdstuk != null) y.set(k + ".hoofdstuk", s.hoofdstuk);
            for (var r : s.rust.entrySet()) y.set(k + ".rust." + r.getKey(), r.getValue());
            y.set(k + ".vlaggen", List.copyOf(s.vlaggen));
            for (var t : s.tijden.entrySet()) y.set(k + ".tijden." + t.getKey(), t.getValue());
            if (s.laatste != null) y.set(k + ".laatste", s.laatste);
            if (!s.bezocht.isEmpty()) y.set(k + ".bezocht", List.copyOf(s.bezocht));
            for (var m : s.missies.entrySet()) y.set(k + ".missies." + m.getKey(), m.getValue());
            for (var n : s.tellers.entrySet()) y.set(k + ".tellers." + n.getKey(), n.getValue());
            if (s.dier != null) y.set(k + ".dier", s.dier);
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
