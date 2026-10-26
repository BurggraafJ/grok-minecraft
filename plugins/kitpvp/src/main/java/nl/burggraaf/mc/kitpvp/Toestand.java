package nl.burggraaf.mc.kitpvp;

import java.util.Objects;

/**
 * Waar een speler op kitpvp is. Precies één van:
 * <ul>
 *   <li>{@code HUB}: in de hub (binnenkomst, na /arena);</li>
 *   <li>{@code KITRUIMTE}: in de kitruimte {@code ruimte} van modus {@code modus}, nog zonder kit;</li>
 *   <li>{@code ARENA}: in arena {@code ruimte} van modus {@code modus}, met kit {@code kit};</li>
 *   <li>{@code SPEL}: in een eigen spel van modus {@code modus} (bv. Story of Zelda); {@code ruimte} kiest de modus zelf.
 *       De algemene KitPvP-regels (geen schade, geen honger, grenzen) gelden dan niet: dat regelt de modus.</li>
 * </ul>
 */
record Toestand(Plek plek, String modus, String ruimte, String kit) {

    enum Plek { HUB, KITRUIMTE, ARENA, SPEL }

    static final Toestand HUB = new Toestand(Plek.HUB, null, null, null);

    Toestand {
        Objects.requireNonNull(plek);
        if (plek != Plek.HUB) {
            Objects.requireNonNull(modus, "modus");
            Objects.requireNonNull(ruimte, "ruimte");
        }
        if (plek == Plek.ARENA) Objects.requireNonNull(kit, "kit");
        else if (kit != null) throw new IllegalArgumentException("alleen in een arena heb je een kit");
    }

    static Toestand kitruimte(String modus, String ruimte) {
        return new Toestand(Plek.KITRUIMTE, modus, ruimte, null);
    }

    static Toestand arena(String modus, String arena, String kit) {
        return new Toestand(Plek.ARENA, modus, arena, kit);
    }

    static Toestand spel(String modus, String ruimte) {
        return new Toestand(Plek.SPEL, modus, ruimte, null);
    }

    boolean inArena() {
        return plek == Plek.ARENA;
    }

    /** Hub en kitruimte: veilig, geen schade, terug naar binnen als je wegloopt. */
    boolean veilig() {
        return plek == Plek.HUB || plek == Plek.KITRUIMTE;
    }

    /** Valt deze speler onder de algemene KitPvP-regels? Niet als een modus een eigen spel draait. */
    boolean beheerd() {
        return plek != Plek.SPEL;
    }
}
