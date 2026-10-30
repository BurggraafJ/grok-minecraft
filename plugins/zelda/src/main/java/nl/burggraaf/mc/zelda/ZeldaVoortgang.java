package nl.burggraaf.mc.zelda;

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
 * waar je was. Een speler heeft hoogstens {@link #MAX_SPELLEN} spellen (opslagplekken) naast elkaar; {@link #van} is het
 * spel waarin hij nu speelt (een oude stand van 2.15 is spel 1). Spellen met dezelfde {@code groep} zijn een samen-spel:
 * {@link #deel} geeft wat de een nieuw heeft aan de rest. Per spel:
 * <ul>
 *   <li>{@code laatste}: de rustplaats waar je het laatst was ({@code h1:3}); daar kom je binnen en na doodgaan terug.
 *       Oude standen (zonder) vallen terug op de verste rustplaats van {@code hoofdstuk}.</li>
 *   <li>{@code rust}: per deel de verste rustplaats (index), {@code bezocht}: alle bereikte rustplaatsen ({@code h1:0},
 *       {@code e2a:3}; de kaart reist daarheen; bij het lezen aangevuld uit {@code rust}).</li>
 *   <li>{@code vlaggen}: vondsten ({@code zwaard}, {@code hart1}, hartstukje {@code hs:<id>}, {@code fles1},
 *       {@code harnas2}, {@code dier:vos}), gezien verhaal {@code v:<id>}, opgeloste puzzel {@code r:<id>}, gewonnen
 *       kamer {@code k:<id>}, gesprek {@code g:<id>}, voltooid {@code h1}, opties {@code o:<naam>}.</li>
 *   <li>{@code missies}: zijmissies, per id de stap (1 … n bezig, {@link #KLAAR} = klaar).</li>
 *   <li>{@code tellers}: getallen (bv. {@code fles-vol}, {@code ster:<kamer>} = het beste niveau + 1, {@code dood:<kamer>}); {@code dier}: de naam van je huisdier; {@code tijden}: beste
 *       tijden van uitdagingen in ms.</li>
 *   <li>{@code kaart}: wat je ontdekte (de kaart in vak 9), ingepakt als tekst ({@link ZeldaKaartBeeld#schrijf}).</li>
 *   <li>{@code niveau} ({@link ZeldaRegels.Niveau}, oude standen Avontuur), {@code steun} en {@code vast} (beheer),
 *       {@code groep} (samen-spel).</li>
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
        final java.util.BitSet kaart = new java.util.BitSet();  // wat je ontdekte: vakjes van 8 × 8 blokken (ZeldaKaartBeeld.cel)
        int niveau = ZeldaRegels.Niveau.AVONTUUR.ordinal();     // het niveau van dit spel (ZeldaRegels.Niveau; oude standen: Avontuur)
        int steun;                                              // bijsturen door beheer: −2 … 2 (stil, de speler ziet het niet)
        boolean vast;                                           // het niveau staat vast (beheer): de speler kan het niet veranderen
        String groep;                                           // een samen-spel: wie dezelfde groep heeft, deelt zijn voortgang (null = alleen)

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
            s.kaart.or(kaart);
            s.niveau = niveau;
            s.steun = steun;
            s.vast = vast;
            s.groep = groep;
            return s;
        }
    }

    /** Stap van een voltooide zijmissie. */
    static final int KLAAR = 1000;
    /** Zoveel spellen (opslagplekken) heeft een speler hoogstens. */
    static final int MAX_SPELLEN = 3;

    /** Eén speler: zijn spellen (1 … {@link #MAX_SPELLEN}) en in welk hij nu speelt. */
    static final class Speler {
        int actief = 1;
        final java.util.TreeMap<Integer, Stand> spellen = new java.util.TreeMap<>();

        Stand stand() {
            return spellen.computeIfAbsent(actief, k -> new Stand());
        }
    }

    private final File bestand;
    private final Map<UUID, Speler> spelers = new HashMap<>();
    private boolean vuil;

    ZeldaVoortgang(File bestand) {
        this.bestand = bestand;
        laad();
    }

    File bestand() {
        return bestand;
    }

    /** Het spel waarin deze speler nu speelt (een nieuwe speler: spel 1). */
    Stand van(UUID id) {
        return speler(id).stand();
    }

    private Speler speler(UUID id) {
        return spelers.computeIfAbsent(id, k -> new Speler());
    }

    /** Het nummer van het spel waarin hij nu speelt. */
    int actief(UUID id) {
        return speler(id).actief;
    }

    /** Al zijn spellen, op nummer (leeg = nog nooit gespeeld). */
    java.util.SortedMap<Integer, Stand> spellen(UUID id) {
        Speler sp = spelers.get(id);
        return sp == null ? new java.util.TreeMap<>() : java.util.Collections.unmodifiableSortedMap(sp.spellen);
    }

    /** De eerste vrije plek voor een nieuw spel, of -1. */
    int vrij(UUID id) {
        Speler sp = spelers.get(id);
        for (int n = 1; n <= MAX_SPELLEN; n++) if (sp == null || !sp.spellen.containsKey(n)) return n;
        return -1;
    }

    /** Speel voortaan in spel {@code n} (het bestaat al, of het wordt een nieuw spel); false als n niet kan. */
    boolean kies(UUID id, int n) {
        if (n < 1 || n > MAX_SPELLEN) return false;
        Speler sp = speler(id);
        String naam = sp.stand().naam;
        sp.actief = n;
        if (sp.stand().naam == null || sp.stand().naam.isEmpty()) sp.stand().naam = naam;
        vuil = true;
        return true;
    }

    /** Een nieuw spel op plek {@code n} (nog niet om in te spelen: {@link #kies}); null als die plek bezet is. */
    Stand nieuw(UUID id, int n, Stand begin) {
        Speler sp = speler(id);
        if (n < 1 || n > MAX_SPELLEN || sp.spellen.containsKey(n)) return null;
        Stand s = begin == null ? new Stand() : begin;
        s.naam = sp.stand().naam;
        sp.spellen.put(n, s);
        vuil = true;
        return s;
    }

    /** De beste tijden van een uitdaging: naam en ms, snelste eerst (per speler zijn beste, over al zijn spellen). */
    List<Map.Entry<String, Long>> besteTijden(String uitdaging, int n) {
        List<Map.Entry<String, Long>> l = new java.util.ArrayList<>();
        for (Speler sp : spelers.values()) {
            Long best = null;
            String naam = null;
            for (Stand s : sp.spellen.values()) {
                Long t = s.tijden.get(uitdaging);
                if (t != null && (best == null || t < best)) best = t;
                if (s.naam != null && !s.naam.isEmpty()) naam = s.naam;
            }
            if (best != null) l.add(Map.entry(naam == null ? "?" : naam, best));
        }
        l.sort(Map.Entry.comparingByValue());
        return l.subList(0, Math.min(n, l.size()));
    }

    /** De speler met deze naam (hoofdletters maken niet uit), of null. */
    UUID zoek(String naam) {
        if (naam == null) return null;
        for (Map.Entry<UUID, Speler> e : spelers.entrySet())
            for (Stand s : e.getValue().spellen.values()) if (naam.equalsIgnoreCase(s.naam)) return e.getKey();
        return null;
    }

    /** Alle bekende spelersnamen (voor tab). */
    List<String> namen() {
        java.util.Set<String> l = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Speler sp : spelers.values()) for (Stand s : sp.spellen.values()) if (s.naam != null && !s.naam.isEmpty()) l.add(s.naam);
        return new java.util.ArrayList<>(l);
    }

    boolean bekend(UUID id) {
        return spelers.containsKey(id);
    }

    /** Vervang het spel waarin hij nu speelt (beheer: de voortgang van vóór het beheer terug). */
    void zet(UUID id, Stand s) {
        Speler sp = speler(id);
        sp.spellen.put(sp.actief, s);
        vuil = true;
    }

    /** Alles van deze speler weg (al zijn spellen). */
    void wis(UUID id) {
        if (spelers.remove(id) != null) vuil = true;
        kreeg.keySet().removeIf(k -> k.startsWith(id + ":"));
    }

    /**
     * Alleen spel {@code n} weg. Was dat het spel waarin hij speelt, dan speelt hij voortaan in zijn eerste andere spel (of
     * is hij helemaal weg). False als dat spel er niet was.
     */
    boolean wis(UUID id, int n) {
        Speler sp = spelers.get(id);
        if (sp == null || sp.spellen.remove(n) == null) return false;
        vuil = true;
        kreeg.remove(id + ":" + n);
        if (sp.spellen.isEmpty()) spelers.remove(id);
        else if (sp.actief == n) sp.actief = sp.spellen.firstKey();
        return true;
    }

    /** Hoeveel spelers er voortgang hebben. */
    int aantal() {
        return spelers.size();
    }

    /** Alle spelers met hun spellen (voor beheer: wie waar is, waar het meest wordt doodgegaan). */
    Map<UUID, java.util.SortedMap<Integer, Stand>> alle() {
        Map<UUID, java.util.SortedMap<Integer, Stand>> m = new java.util.LinkedHashMap<>();
        for (var e : spelers.entrySet()) m.put(e.getKey(), java.util.Collections.unmodifiableSortedMap(e.getValue().spellen));
        return m;
    }

    /** Wie er in deze groep (een samen-spel) speelt: speler en spelnummer. */
    List<Map.Entry<UUID, Integer>> groep(String g) {
        List<Map.Entry<UUID, Integer>> l = new java.util.ArrayList<>();
        if (g == null) return l;
        for (var e : spelers.entrySet())
            for (var s : e.getValue().spellen.entrySet()) if (g.equals(s.getValue().groep)) l.add(Map.entry(e.getKey(), s.getKey()));
        return l;
    }

    /** Er is iets veranderd dat bewaard moet worden. */
    void vuil() {
        vuil = true;
    }

    boolean isVuil() {
        return vuil;
    }

    // ── samen spelen: een groep deelt zijn voortgang ────────────────────────────────────────────────────

    /** Per groep alle vlaggen die ooit gedeeld zijn, in volgorde (alleen nieuwe gaan over). */
    private final Map<String, java.util.LinkedHashSet<String>> pot = new HashMap<>();
    /** Per spel in een groep ({@code uuid:n}): hoeveel van de pot hij al kreeg. */
    private final Map<String, Integer> kreeg = new HashMap<>();

    /**
     * Spel {@code n} van deze speler gaat (weer) meedoen in zijn groep, als kopie van wat de groep nu heeft: hij krijgt
     * voortaan alleen wat erbij komt (niet wat een ander ooit vergat).
     */
    void sluitAan(UUID id, int n) {
        Stand s = spellen(id).get(n);
        if (s == null || s.groep == null) return;
        java.util.LinkedHashSet<String> p = pot.get(s.groep);
        if (p != null) kreeg.put(id + ":" + n, p.size());
        else kreeg.remove(id + ":" + n);
    }

    /** Gaat deze vlag over naar de groep? Opties ({@code o:}, bv. uitleg uit) zijn van jezelf. */
    static boolean deelbaar(String vlag) {
        return !vlag.startsWith("o:");
    }

    /**
     * Samen spelen: wat iemand in een groep nieuw heeft (vlaggen, behalve opties), krijgt de rest ook; de verste rustplaats
     * per deel, bezochte rustplaatsen, zijmissies (de verste stap) en de kaart worden samengevoegd. Alleen nieuwe vlaggen
     * gaan over: wie iets vergat («opnieuw vanaf hier»), krijgt het niet terug. Wie {@code buiten} is (beheer), doet niet mee
     * en krijgt het later. Geeft de spelers wier spel waarin ze nu spelen iets kreeg.
     */
    java.util.Set<UUID> deel(java.util.function.Predicate<UUID> buiten) {
        Map<String, List<Map.Entry<UUID, Integer>>> groepen = new HashMap<>();
        for (var e : spelers.entrySet()) {
            if (buiten != null && buiten.test(e.getKey())) continue;
            for (var s : e.getValue().spellen.entrySet())
                if (s.getValue().groep != null) groepen.computeIfAbsent(s.getValue().groep, k -> new java.util.ArrayList<>()).add(Map.entry(e.getKey(), s.getKey()));
        }
        java.util.Set<UUID> kregen = new java.util.HashSet<>();
        for (var g : groepen.entrySet()) {
            List<Stand> leden = new java.util.ArrayList<>();
            for (var l : g.getValue()) leden.add(spelers.get(l.getKey()).spellen.get(l.getValue()));
            java.util.LinkedHashSet<String> p = pot.computeIfAbsent(g.getKey(), k -> new java.util.LinkedHashSet<>());   // een nieuwe groep
            for (Stand s : leden) for (String v : s.vlaggen) if (deelbaar(v)) p.add(v);
            List<String> lijst = new java.util.ArrayList<>(p);
            Map<String, Integer> rust = new HashMap<>(), missies = new HashMap<>();
            java.util.Set<String> bezocht = new java.util.HashSet<>();
            java.util.BitSet kaart = new java.util.BitSet();
            for (Stand s : leden) {
                s.rust.forEach((k, v) -> rust.merge(k, v, Math::max));
                s.missies.forEach((k, v) -> missies.merge(k, v, Math::max));
                bezocht.addAll(s.bezocht);
                kaart.or(s.kaart);
            }
            for (int i = 0; i < leden.size(); i++) {
                Stand s = leden.get(i);
                String sleutel = g.getValue().get(i).getKey() + ":" + g.getValue().get(i).getValue();
                int van = kreeg.getOrDefault(sleutel, 0);   // nieuw in de groep: alles uit de pot (hij is een kopie, dus vooral wat erbij kwam)
                boolean anders = false;
                for (int j = van; j < lijst.size(); j++) anders |= s.vlaggen.add(lijst.get(j));
                kreeg.put(sleutel, lijst.size());
                for (var r : rust.entrySet()) if (r.getValue() > s.rust(r.getKey())) {
                    s.rust.put(r.getKey(), r.getValue());
                    anders = true;
                }
                for (var m : missies.entrySet()) if (m.getValue() > s.missie(m.getKey())) {
                    s.missies.put(m.getKey(), m.getValue());
                    anders = true;
                }
                anders |= s.bezocht.addAll(bezocht);
                if (!kaart.equals(s.kaart)) {
                    java.util.BitSet voor = (java.util.BitSet) s.kaart.clone();
                    s.kaart.or(kaart);
                    anders |= !voor.equals(s.kaart);
                }
                if (anders) {
                    vuil = true;
                    UUID id = g.getValue().get(i).getKey();
                    if (spelers.get(id).actief == g.getValue().get(i).getValue()) kregen.add(id);
                }
            }
        }
        return kregen;
    }

    // ── bestand ─────────────────────────────────────────────────────────────────────────────────────────

    void laad() {
        spelers.clear();
        pot.clear();
        kreeg.clear();
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
            Speler s = new Speler();
            s.actief = Math.max(1, Math.min(MAX_SPELLEN, c.getInt("spel", 1)));   // oude stand (2.15): spel 1
            s.spellen.put(s.actief, lees(c));
            ConfigurationSection andere = c.getConfigurationSection("spellen");
            if (andere != null) for (String n : andere.getKeys(false)) {
                int nr;
                try {
                    nr = Integer.parseInt(n);
                } catch (NumberFormatException e) {
                    continue;
                }
                ConfigurationSection a = andere.getConfigurationSection(n);
                if (a != null && nr >= 1 && nr <= MAX_SPELLEN && nr != s.actief) s.spellen.put(nr, lees(a));
            }
            spelers.put(id, s);
        }
        // samen-spellen: wat er nu in het bestand staat, is al gedeeld (wie iets vergat, krijgt het niet terug)
        for (var e : spelers.entrySet())
            for (var sp2 : e.getValue().spellen.entrySet()) {
                String g = sp2.getValue().groep;
                if (g == null) continue;
                java.util.LinkedHashSet<String> p = pot.computeIfAbsent(g, k -> new java.util.LinkedHashSet<>());
                for (String v : sp2.getValue().vlaggen) if (deelbaar(v)) p.add(v);
            }
        for (var e : spelers.entrySet())
            for (var sp2 : e.getValue().spellen.entrySet())
                if (sp2.getValue().groep != null) kreeg.put(e.getKey() + ":" + sp2.getKey(), pot.get(sp2.getValue().groep).size());
    }

    private static Stand lees(ConfigurationSection c) {
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
        s.kaart.or(ZeldaKaartBeeld.lees(c.getString("kaart")));
        s.niveau = c.getInt("niveau", ZeldaRegels.Niveau.AVONTUUR.ordinal());   // oude stand (2.15): Avontuur
        s.steun = c.getInt("steun", 0);
        s.vast = c.getBoolean("vast", false);
        s.groep = c.getString("groep");
        return s;
    }

    private static void schrijf(YamlConfiguration y, String k, Stand s) {
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
        if (!s.kaart.isEmpty()) y.set(k + ".kaart", ZeldaKaartBeeld.schrijf(s.kaart));
        y.set(k + ".niveau", s.niveau);
        if (s.steun != 0) y.set(k + ".steun", s.steun);
        if (s.vast) y.set(k + ".vast", true);
        if (s.groep != null) y.set(k + ".groep", s.groep);
    }

    /** Schrijf alles weg (eerst naar een tijdelijk bestand, dan vervangen: nooit half geschreven). */
    void bewaar() throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Story of Zelda: voortgang per speler. spelers.<uuid> = het spel waarin hij nu speelt (spel: n), spellen.<n> = zijn andere spellen;",
                "per spel: laatste en bezochte rustplaatsen, verste per deel, vlaggen, missies, tellers, beste tijden in ms, niveau, groep (samen-spel)."));
        for (Map.Entry<UUID, Speler> e : spelers.entrySet()) {
            Speler sp = e.getValue();
            String k = "spelers." + e.getKey();
            schrijf(y, k, sp.stand());
            if (sp.actief != 1 || sp.spellen.size() > 1) y.set(k + ".spel", sp.actief);
            for (var s : sp.spellen.entrySet()) if (s.getKey() != sp.actief) schrijf(y, k + ".spellen." + s.getKey(), s.getValue());
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
