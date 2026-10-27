package nl.burggraaf.mc.lobbymelding;

/**
 * Hoe ver kan een speler springen? Rekent de bewegingsregels van Minecraft na (per tick, zoals de server en de client
 * dat doen): op de grond versnelling 0,13 × 0,98 (sprinten) en wrijving 0,6 × 0,91; in de lucht 0,026 × 0,98 en
 * 0,91; springen = 0,42 omhoog plus 0,2 vooruit bij sprinten; zwaartekracht (v − 0,08) × 0,98.
 * Voorzichtig gerekend: je start stil, met je midden 0,3 van de achterrand van het afzetvlak, en springt op het
 * laatste moment dat je nog op het blok staat. Wat hier net lukt, lukt in het echt ook (met een aanloop zelfs ruimer).
 * Een speler is 0,6 breed: je midden mag 0,3 over de rand hangen bij afzetten en bij landen.
 */
final class Sprong {

    private Sprong() {
    }

    static final double GRAV = 0.08, LUCHTWEERSTAND = 0.98, SPRONG = 0.42, SPRINTDUW = 0.2;
    static final double ACC_LUCHT = 0.026 * 0.98, LUCHT = 0.91;
    /** Hoogste sprong (blokken) op een gewoon blok: ±1,25. */
    static final double TOPHOOGTE = top(SPRONG);

    static double top(double vy) {
        double y = 0, top = 0;
        for (int t = 0; t < 40; t++) {
            y += vy;
            vy = (vy - GRAV) * LUCHTWEERSTAND;
            top = Math.max(top, y);
        }
        return top;
    }

    /**
     * Grootste afstand (midden van de speler) voorbij de voorrand van het afzetvlak bij landen op hoogte dy
     * (blokken, + = hoger), met een aanloop van {@code aanloop} blokken (1 = één los blok). Glad = 0,98 voor ijs,
     * 0,6 normaal. Kan het niet (te hoog): {@code Double.NEGATIVE_INFINITY}.
     */
    static double bereik(double aanloop, double dy, double glad) {
        if (dy > TOPHOOGTE - 0.05) return Double.NEGATIVE_INFINITY;
        double accGrond = 0.13 * 0.98 * (0.216 / (glad * glad * glad)), wrijving = glad * 0.91;
        double rand = aanloop + 0.3;                                   // midden mag tot hier
        double x = 0.3, vx = 0;
        double beste = Double.NEGATIVE_INFINITY;
        for (int stap = 0; stap < 200; stap++) {
            beste = Math.max(beste, vlucht(x, vx, dy, accGrond, wrijving) - aanloop);
            double nx = x + vx + accGrond;
            if (nx > rand) break;
            vx = (vx + accGrond) * wrijving;
            x = nx;
        }
        return beste;
    }

    static double bereik(double aanloop, double dy) {
        return bereik(aanloop, dy, 0.6);
    }

    /** Sprong vanaf (x, vx) op de grond; geeft x bij landen op hoogte dy (dalend), of −∞. */
    private static double vlucht(double x, double vx, double dy, double accGrond, double wrijving) {
        double y = 0, vy = SPRONG;
        vx = vx + SPRINTDUW + accGrond;                                // afzet-tick: nog grondversnelling en -wrijving
        x += vx;
        y += vy;
        vx *= wrijving;
        vy = (vy - GRAV) * LUCHTWEERSTAND;
        double top = y, xVoor = x;
        for (int t = 1; t < 80; t++) {
            // landen: de server botst eerst verticaal, met de horizontale stand van vóór deze tick
            if (vy < 0 && y <= dy && top >= dy) return xVoor;
            xVoor = x;
            vx += ACC_LUCHT;
            x += vx;
            y += vy;
            vx *= LUCHT;
            vy = (vy - GRAV) * LUCHTWEERSTAND;
            top = Math.max(top, y);
        }
        return Double.NEGATIVE_INFINITY;
    }

    /**
     * Nodige verplaatsing van je midden om van vlak A naar vlak B te komen (in blokken), met 0,3 speling aan elke
     * kant. Vlakken als cellen x1..x2, z1..z2 (inclusief).
     */
    static double nodig(int ax1, int az1, int ax2, int az2, int bx1, int bz1, int bx2, int bz2) {
        int gx = Math.max(0, Math.max(bx1 - ax2 - 1, ax1 - bx2 - 1));
        int gz = Math.max(0, Math.max(bz1 - az2 - 1, az1 - bz2 - 1));
        double rx = Math.max(0, gx - 0.6), rz = Math.max(0, gz - 0.6);
        return Math.hypot(rx, rz);
    }

    /**
     * Stuiteren op slijm: je valt {@code val} blokken (voeten) op het slijmblok en stuitert terug. Geeft de hoogste
     * stand (voeten, boven het slijm). Minecraft keert de valsnelheid om (factor 1 voor spelers).
     */
    static double stuiterTop(double val) {
        double y = val, vy = 0;
        for (int t = 0; t < 200 && y > 0; t++) {
            y += vy;
            vy = (vy - GRAV) * LUCHTWEERSTAND;
        }
        return top(-vy) * 0.98;                                        // iets voorzichtiger
    }

    /** Horizontale afstand die je tijdens een stuiter (vanaf stilstand) haalt tot je op hoogte dy landt. */
    static double stuiterBereik(double val, double dy) {
        double y = val, vy = 0;
        for (int t = 0; t < 200 && y > 0; t++) {
            y += vy;
            vy = (vy - GRAV) * LUCHTWEERSTAND;
        }
        vy = -vy * 0.98;
        double x = 0, vx = 0, top = 0, xVoor = 0;
        y = 0;
        for (int t = 0; t < 120; t++) {
            xVoor = x;
            vx += ACC_LUCHT;
            x += vx;
            y += vy;
            vx *= LUCHT;
            vy = (vy - GRAV) * LUCHTWEERSTAND;
            top = Math.max(top, y);
            if (vy < 0 && y <= dy && top >= dy) return xVoor;
        }
        return Double.NEGATIVE_INFINITY;
    }
}
