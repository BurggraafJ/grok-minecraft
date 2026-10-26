package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alle modi, in de volgorde van registreren (= volgorde in het menu). Houdt ook de arena's en kitruimtes van alle
 * modi bij, zodat de rest van de plugin ze op id kan vinden.
 */
final class ModusRegister {

    /** Plekken voor een poort in de hub. */
    static final Set<String> PLEKKEN = Set.of("west", "zuid", "noord");

    private final Map<String, Modus> modi = new LinkedHashMap<>();
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<String, Kitruimte> ruimtes = new LinkedHashMap<>();

    void registreer(Modus m) {
        String id = m.id();
        if (id == null || !id.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("ongeldig modus-id: " + id);
        if (modi.containsKey(id)) throw new IllegalArgumentException("modus bestaat al: " + id);
        String plek = m.poort().plek();
        if (!PLEKKEN.contains(plek)) throw new IllegalArgumentException(id + ": onbekende poortplek " + plek + " (kan: " + PLEKKEN + ")");
        for (Modus o : modi.values())
            if (o.poort().plek().equals(plek)) throw new IllegalArgumentException(id + ": poortplek " + plek + " is al van " + o.id());
        Map<String, Arena> nieuweArenas = new LinkedHashMap<>();
        for (Arena a : m.arenas()) {
            if (!a.modus.equals(id)) throw new IllegalArgumentException("arena " + a.id + " hoort niet bij " + id);
            if (arenas.containsKey(a.id) || nieuweArenas.put(a.id, a) != null) throw new IllegalArgumentException("arena bestaat al: " + a.id);
        }
        Map<String, Kitruimte> nieuweRuimtes = new LinkedHashMap<>();
        for (Kitruimte r : m.kitruimtes()) {
            if (!r.modus.equals(id)) throw new IllegalArgumentException("kitruimte " + r.id + " hoort niet bij " + id);
            if (!nieuweArenas.containsKey(r.arena)) throw new IllegalArgumentException("kitruimte " + r.id + ": arena " + r.arena + " is niet van " + id);
            if (ruimtes.containsKey(r.id) || nieuweRuimtes.put(r.id, r) != null) throw new IllegalArgumentException("kitruimte bestaat al: " + r.id);
        }
        if (m.status() == Modus.Status.OPEN && m instanceof KitModus && nieuweRuimtes.isEmpty())
            throw new IllegalArgumentException(id + ": een kitmodus heeft minstens één kitruimte nodig");
        modi.put(id, m);
        arenas.putAll(nieuweArenas);
        ruimtes.putAll(nieuweRuimtes);
    }

    List<Modus> alle() {
        return List.copyOf(modi.values());
    }

    Modus get(String id) {
        return id == null ? null : modi.get(id);
    }

    Modus opPlek(String plek) {
        for (Modus m : modi.values()) if (m.poort().plek().equals(plek)) return m;
        return null;
    }

    Arena arena(String id) {
        return id == null ? null : arenas.get(id);
    }

    Collection<Arena> arenas() {
        return arenas.values();
    }

    Kitruimte kitruimte(String id) {
        return id == null ? null : ruimtes.get(id);
    }

    Collection<Kitruimte> kitruimtes() {
        return ruimtes.values();
    }

    /** De kitruimte waar je na een ronde in deze arena terugkomt (de eerste die naar die arena leidt). */
    Kitruimte kitruimteVoor(String arena) {
        for (Kitruimte r : ruimtes.values()) if (r.arena.equals(arena)) return r;
        return null;
    }

    List<Arena> arenasVan(String modus) {
        List<Arena> l = new ArrayList<>();
        for (Arena a : arenas.values()) if (a.modus.equals(modus)) l.add(a);
        return l;
    }
}
