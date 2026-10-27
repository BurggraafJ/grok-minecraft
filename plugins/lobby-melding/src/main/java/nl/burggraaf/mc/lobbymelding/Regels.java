package nl.burggraaf.mc.lobbymelding;

/** Kleine regels van de parkour, los van de server (zodat ze in unit-tests vastliggen). */
final class Regels {

    private Regels() {
    }

    /**
     * Raakt een speler echt de grond (en moet hij terug naar de start)? Alleen bij echt grondcontact (Jelle 26-10):
     * met de voeten op de tuinvloer staan (gras, pad, plaatje of tapijt op de grond: voeten lager dan g + 2; alle
     * baanblokken liggen hoger), in het water van de vijver liggen, of door de vloer zakken. Midden in een sprong,
     * aan een ladder, of even op een rand, muur, dak of hek landen telt niet.
     *
     * @param voetY  hoogte van de voeten
     * @param opGrond staat op iets (zegt de client)
     * @param klimt  hangt aan een ladder of klimop
     * @param inWater in het water
     * @param opBaan het blok onder de voeten hoort bij dit level
     * @param g      grondhoogte (gras op y = g, voeten op g + 1)
     */
    static boolean grondcontact(double voetY, boolean opGrond, boolean klimt, boolean inWater, boolean opBaan, int g) {
        if (opBaan) return false;
        if (voetY < g - 1.5) return true;
        if (inWater && voetY < g + 1.2) return true;
        return opGrond && !klimt && voetY < g + 1.99;
    }

    /**
     * Stopt de run zonder terugzetten: wie vanaf de start (nog geen stap verder) naast het startvlak op de grond
     * stapt, wil niet spelen. Afstand = horizontaal tot het midden van de start.
     */
    static boolean naastStart(int verst, double afstand) {
        return verst == 0 && afstand < 3.2;
    }

    /** 1:02.3 */
    static String tijd(long ms) {
        return String.format("%d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10);
    }

    /** Level dat je bij /hub krijgt: het hoogste dat je ooit speelde (1-based), 0 = nog nooit = de spawn. */
    static int hubLevel(int gehaald, int gespeeld, int aantal) {
        return Math.min(Math.max(gehaald, gespeeld), aantal);
    }

    /** Level waar je aan toe bent (0-based): het eerste dat je nog niet haalde; alles gehaald = het laatste. */
    static int volgend(int gehaald, int aantal) {
        return Math.min(gehaald, aantal - 1);
    }

    /** Mag je level l (0-based) kiezen? Alle gehaalde levels en het eerstvolgende. */
    static boolean open(int l, int gehaald) {
        return l <= gehaald;
    }
}
