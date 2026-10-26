package nl.burggraaf.mc.kitpvp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** De toestand van elke speler (zie {@link Toestand}). Wie niet bekend is, staat in de hub. */
final class Spelers {

    private final Map<UUID, Toestand> stand = new HashMap<>();

    Toestand van(UUID id) {
        return stand.getOrDefault(id, Toestand.HUB);
    }

    /** Zet de nieuwe toestand en geef de oude terug. */
    Toestand zet(UUID id, Toestand t) {
        Toestand oud = stand.put(id, t);
        return oud == null ? Toestand.HUB : oud;
    }

    Toestand weg(UUID id) {
        Toestand oud = stand.remove(id);
        return oud == null ? Toestand.HUB : oud;
    }

    boolean inArena(UUID id) {
        return van(id).inArena();
    }

    /** Kit van wie in een arena staat, anders null. */
    String kit(UUID id) {
        return van(id).kit();
    }

    /** Arena van wie in een arena staat, anders null. */
    String arena(UUID id) {
        Toestand t = van(id);
        return t.inArena() ? t.ruimte() : null;
    }

    Set<UUID> inArena(String arena) {
        Set<UUID> s = new HashSet<>();
        for (var e : stand.entrySet()) if (e.getValue().inArena() && e.getValue().ruimte().equals(arena)) s.add(e.getKey());
        return s;
    }

    int aantalInArena() {
        int n = 0;
        for (Toestand t : stand.values()) if (t.inArena()) n++;
        return n;
    }

    /** De modus die je verlaat bij deze overgang (dan krijgt die modus {@code leave}), anders null. */
    static String verlaten(Toestand oud, Toestand nieuw) {
        return oud.modus() != null && !oud.modus().equals(nieuw.modus()) ? oud.modus() : null;
    }

    /**
     * Metadata {@code kitpvp-stand} voor de plugin Huisdieren: {@code wacht} (hub en kitruimte: veilig),
     * in een arena de stand van die arena ({@code pvp} of {@code bots}), in een eigen spel wat de modus zegt.
     */
    static String stand(Toestand t, Function<String, String> arenaStand, Function<Toestand, String> spelStand) {
        return switch (t.plek()) {
            case HUB, KITRUIMTE -> "wacht";
            case ARENA -> arenaStand.apply(t.ruimte());
            case SPEL -> spelStand.apply(t);
        };
    }
}
