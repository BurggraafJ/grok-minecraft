package nl.burggraaf.mc.lobbymelding;

/**
 * Tekent een parkour-level in de {@link Plan} in eigen coördinaten: f = vooruit (weg van de start), s = rechts,
 * h = hoogte boven de grond (h = 1 is het eerste blok boven het gras). Alles wat je hier zet wordt een stap van de
 * {@link Baan}, in volgorde. Het anker is het midden van het startvlak.
 */
final class BaanTekening {

    final Plan p;
    final Baan baan;
    final int ax, az, richting;           // richting: 0 noord (−z), 1 oost (+x), 2 zuid (+z), 3 west (−x)

    BaanTekening(Plan p, Baan baan, int ax, int az, int richting) {
        this.p = p;
        this.baan = baan;
        this.ax = ax;
        this.az = az;
        this.richting = richting & 3;
    }

    int wx(int f, int s) {
        return switch (richting) {
            case 0 -> ax + s;
            case 1 -> ax + f;
            case 2 -> ax - s;
            default -> ax - f;
        };
    }

    int wz(int f, int s) {
        return switch (richting) {
            case 0 -> az - f;
            case 1 -> az + s;
            case 2 -> az + f;
            default -> az - s;
        };
    }

    int y(int h) {
        return p.g + h;
    }

    /** Windrichting van een eigen richting: "voor", "achter", "links", "rechts". */
    String kant(String eigen) {
        int k = switch (eigen) {
            case "voor" -> 0;
            case "rechts" -> 1;
            case "achter" -> 2;
            default -> 3;
        };
        return new String[]{"north", "east", "south", "west"}[(richting + k) & 3];
    }

    void zet(int f, int h, int s, String blok) {
        p.zet(wx(f, s), y(h), wz(f, s), blok);
    }

    void vul(int f1, int h1, int s1, int f2, int h2, int s2, String blok) {
        for (int f = Math.min(f1, f2); f <= Math.max(f1, f2); f++)
            for (int s = Math.min(s1, s2); s <= Math.max(s1, s2); s++)
                for (int h = Math.min(h1, h2); h <= Math.max(h1, h2); h++) zet(f, h, s, blok);
    }

    private Baan.Stap stap(Baan.Soort soort, int f1, int s1, int f2, int s2, int h, double voet, int top) {
        int xa = wx(f1, s1), xb = wx(f2, s2), za = wz(f1, s1), zb = wz(f2, s2);
        Baan.Stap st = new Baan.Stap(soort, Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb), y(h), voet, top);
        baan.stappen.add(st);
        return st;
    }

    private void ruimte(int f1, int s1, int f2, int s2, int h) {
        vul(f1, h + 1, s1, f2, h + 3, s2, "air");
    }

    /** Startvlak 3×3 rond (0, 0) op hoogte h; je kijkt vooruit (+f). */
    void start(int h, String rand, String midden, String steun) {
        vul(-1, h, -1, 1, h, 1, rand);
        zet(0, h, 0, midden);
        if (steun != null && h > 1) vul(-1, 1, -1, 1, h - 1, 1, steun);
        ruimte(-1, -1, 1, 1, h);
        stap(Baan.Soort.START, -1, -1, 1, 1, h, y(h) + 1, 0);
        baan.sx = wx(0, 0) + 0.5;
        baan.sy = y(h) + 1;
        baan.sz = wz(0, 0) + 0.5;
        baan.yaw = new float[]{180f, -90f, 0f, 90f}[richting];
    }

    /** Eén blok (of een vlak f1..f2 × s1..s2) als stap; steun = materiaal eronder tot de grond (null = zwevend). */
    void blok(int f, int s, int h, String blok, String steun) {
        vlak(f, s, f, s, h, blok, steun);
    }

    void vlak(int f1, int s1, int f2, int s2, int h, String blok, String steun) {
        vul(f1, h, s1, f2, h, s2, blok);
        if (steun != null && h > 1) vul(f1, 1, s1, f2, h - 1, s2, steun);
        ruimte(f1, s1, f2, s2, h);
        stap(Baan.Soort.BLOK, f1, s1, f2, s2, h, y(h) + 1, 0);
    }

    /** Halve plaat (onderste helft) op hoogte h: voeten op h + 0,5. */
    void half(int f, int s, int h, String plaat, String steun) {
        zet(f, h, s, plaat + "[type=bottom]");
        if (steun != null && h > 1) vul(f, 1, s, f, h - 1, s, steun);
        ruimte(f, s, f, s, h);
        stap(Baan.Soort.BLOK, f, s, f, s, h, y(h) + 0.5, 0);
    }

    /**
     * Ladder in cel (f, s) van hoogte h1 tot h2, tegen een paal die in richting {@code naarPaal} ("voor", "links",
     * "rechts") naast de ladder staat; de paal gaat tot h2 en zijn top is de volgende stap.
     */
    void ladder(int f, int s, int h1, int h2, String paal, String naarPaal, String klimmer) {
        int pf = f + (naarPaal.equals("voor") ? 1 : naarPaal.equals("achter") ? -1 : 0);
        int ps = s + (naarPaal.equals("rechts") ? 1 : naarPaal.equals("links") ? -1 : 0);
        vul(pf, 1, ps, pf, h2, ps, paal);
        String weg = kant(switch (naarPaal) {                 // ladder kijkt van de paal af
            case "voor" -> "achter";
            case "achter" -> "voor";
            case "links" -> "rechts";
            default -> "links";
        });
        for (int h = h1; h <= h2; h++) {
            if (klimmer.equals("vine")) zet(f, h, s, "vine[" + kant(naarPaal) + "=true]");
            else zet(f, h, s, "ladder[facing=" + weg + "]");
        }
        ruimte(pf, ps, pf, ps, h2);
        vul(f, h2 + 1, s, f, h2 + 2, s, "air");
        stap(Baan.Soort.LADDER, f, s, f, s, h1, y(h1) + 1, y(h2));
        stap(Baan.Soort.BLOK, pf, ps, pf, ps, h2, y(h2) + 1, 0);
    }

    /** Slijmblok als trampoline (je moet erop vallen). */
    void slijm(int f, int s, int h, String steun) {
        zet(f, h, s, "slime_block");
        if (steun != null && h > 1) vul(f, 1, s, f, h - 1, s, steun);
        ruimte(f, s, f, s, h);
        stap(Baan.Soort.SLIJM, f, s, f, s, h, y(h) + 1, 0);
    }

    /** Finish 3×3 rond (f, s). */
    void finish(int f, int s, int h, String rand, String midden, String steun) {
        vul(f - 1, h, s - 1, f + 1, h, s + 1, rand);
        zet(f, h, s, midden);
        if (steun != null && h > 1) vul(f - 1, 1, s - 1, f + 1, h - 1, s + 1, steun);
        ruimte(f - 1, s - 1, f + 1, s + 1, h);
        stap(Baan.Soort.FINISH, f - 1, s - 1, f + 1, s + 1, h, y(h) + 1, 0);
    }
}
