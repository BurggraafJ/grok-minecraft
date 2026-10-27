package nl.burggraaf.mc.lobbymelding;

import java.util.ArrayList;
import java.util.List;

/**
 * Eén parkour-level: de stappen in volgorde (start, blokken, ladders, slijm, finish) in wereldcoördinaten, zonder
 * server. {@link #controleer} rekent elke sprong na met {@link Sprong} en kijkt of er niets in de weg zit.
 */
final class Baan {

    enum Soort { START, BLOK, LADDER, SLIJM, FINISH }

    /**
     * Een stap: vlak van cellen x1..x2 × z1..z2 op bloklaag y; voeten staan op {@code voet} (y + 1, of y + 0,5 bij een
     * halve plaat). Ladder: cellen y..top in één kolom (je hangt erin, je staat er niet op).
     */
    record Stap(Soort soort, int x1, int z1, int x2, int z2, int y, double voet, int top) {
        boolean bevat(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }

        double mx() {
            return (x1 + x2 + 1) / 2.0;
        }

        double mz() {
            return (z1 + z2 + 1) / 2.0;
        }
    }

    final int nr;                     // 1-based
    final String naam, sterren;
    final List<Stap> stappen = new ArrayList<>();
    /** Mag een val op iets anders dan de grond eindigen (daken)? Dan geen controle van de valruimte. */
    boolean valVrij = true;
    /** Waar je staat bij de start (midden van het startvlak) en waar je heen kijkt. */
    double sx, sy, sz;
    float yaw;
    /** Kleinste marge (blokken) die elke sprong moet hebben. */
    double minMarge = 0.5;

    Baan(int nr, String naam, String sterren) {
        this.nr = nr;
        this.naam = naam;
        this.sterren = sterren;
    }

    Stap start() {
        return stappen.get(0);
    }

    Stap finish() {
        return stappen.get(stappen.size() - 1);
    }

    /** Aantal sterren (moeilijkheid 1..5). */
    int moeite() {
        return sterren.length();
    }

    /** Lengte van het afzetvlak in de springrichting (aanloop), hooguit 3. */
    static double aanloop(Stap a, Stap b) {
        double dx = Math.abs(b.mx() - a.mx()), dz = Math.abs(b.mz() - a.mz());
        int lang = dx >= dz ? a.x2 - a.x1 + 1 : a.z2 - a.z1 + 1;
        return Math.min(3, Math.max(1, lang));
    }

    static double nodig(Stap a, Stap b) {
        return Sprong.nodig(a.x1, a.z1, a.x2, a.z2, b.x1, b.z1, b.x2, b.z2);
    }

    /** Marge van de sprong van stap i naar i+1 (blokken over); negatief = kan niet. */
    double marge(int i) {
        Stap a = stappen.get(i), b = stappen.get(i + 1);
        if (b.soort == Soort.LADDER) {                           // ladder vangen op je eigen hoogte
            if (b.y > a.voet || b.top + 1 < a.voet + 1) return -9;
            return Sprong.bereik(aanloop(a, b), 0) - nodig(a, b);
        }
        if (a.soort == Soort.LADDER) {                           // bovenaan de ladder op de paal stappen
            return b.y == a.top && nodig(a, b) == 0 && b.voet == b.y + 1 ? 1.0 : -9;
        }
        if (a.soort == Soort.SLIJM) {                            // stuiteren
            double val = i > 0 ? stappen.get(i - 1).voet - a.voet : 0;
            double dy = b.voet - a.voet;
            if (dy > Sprong.stuiterTop(val) - 0.4) return -9;
            return Sprong.stuiterBereik(val, dy) - nodig(a, b);
        }
        if (b.soort == Soort.SLIJM && b.voet > a.voet - 1) return -9; // op slijm moet je vallen
        return Sprong.bereik(aanloop(a, b), b.voet - a.voet) - nodig(a, b);
    }

    /** Hoort cel (x, y, z) bij deze baan (staan of klimmen)? */
    int stapOp(int x, int y, int z) {
        for (int i = 0; i < stappen.size(); i++) {
            Stap s = stappen.get(i);
            if (!s.bevat(x, z)) continue;
            if (s.soort == Soort.LADDER ? y >= s.y && y <= s.top : y == s.y) return i;
        }
        return -1;
    }

    /**
     * Controleer de baan tegen de tekening: start en finish 3×3 met ruimte erboven, elke sprong haalbaar met
     * {@link #minMarge}, niets in de weg tussen de stappen, alles hoger dan de grond (voeten ≥ g + 2), en (als
     * {@link #valVrij}) onder de sprongen alleen lucht tot de grond. Geeft de fouten, leeg = goed.
     */
    List<String> controleer(Plan p) {
        List<String> fout = new ArrayList<>();
        String wie = "level " + nr + " (" + naam + ")";
        if (stappen.size() < 3) fout.add(wie + ": te weinig stappen");
        if (fout.isEmpty()) {
            for (Stap s : new Stap[]{start(), finish()}) {
                if (s.x2 - s.x1 + 1 < 3 || s.z2 - s.z1 + 1 < 3) fout.add(wie + ": " + s.soort + " is kleiner dan 3×3");
            }
            if (start().soort != Soort.START || finish().soort != Soort.FINISH) fout.add(wie + ": begint niet met start of eindigt niet met finish");
        }
        for (int i = 0; i < stappen.size(); i++) {
            Stap s = stappen.get(i);
            if (s.soort != Soort.LADDER && s.voet < p.g + 2) fout.add(wie + ": stap " + i + " ligt te laag (voeten op " + (s.voet - p.g) + ")");
            for (int x = s.x1; x <= s.x2; x++)
                for (int z = s.z1; z <= s.z2; z++) {
                    if (s.soort == Soort.LADDER) {
                        for (int y = s.y; y <= s.top; y++)
                            if (!p.naam(x, y, z).equals("ladder") && !p.naam(x, y, z).equals("vine")) fout.add(wie + ": stap " + i + " geen ladder op " + x + "," + y + "," + z);
                        continue;
                    }
                    if (p.doorloopbaar(x, s.y, z)) fout.add(wie + ": stap " + i + " heeft geen blok op " + x + "," + s.y + "," + z);
                    for (int y = s.y + 1; y <= s.y + 3; y++) {            // ruimte om te springen
                        if (!p.doorloopbaar(x, y, z) && stapOp(x, y, z) < 0) fout.add(wie + ": stap " + i + " geen ruimte boven " + x + "," + y + "," + z + " (" + p.naam(x, y, z) + ")");
                    }
                }
        }
        for (int i = 0; i + 1 < stappen.size(); i++) {
            double m = marge(i);
            Stap b = stappen.get(i + 1);
            double eis = b.soort == Soort.LADDER || stappen.get(i).soort == Soort.LADDER ? 0 : minMarge;
            if (m < eis) fout.add(String.format(java.util.Locale.ROOT, "%s: sprong %d→%d niet haalbaar (marge %.2f, nodig %.2f)", wie, i, i + 1, m, eis));
            fout.addAll(corridor(p, i));
        }
        return fout;
    }

    /** Tussen stap i en i+1: lucht op de lijn ertussen (van voethoogte tot boven je hoofd), en eronder (valruimte). */
    private List<String> corridor(Plan p, int i) {
        List<String> fout = new ArrayList<>();
        Stap a = stappen.get(i), b = stappen.get(i + 1);
        if (a.soort == Soort.LADDER || b.soort == Soort.LADDER) return fout;
        // dichtstbijzijnde punten van de twee vlakken
        double ax = klem(b.mx(), a.x1, a.x2 + 1), az = klem(b.mz(), a.z1, a.z2 + 1);
        double bx = klem(ax, b.x1, b.x2 + 1), bz = klem(az, b.z1, b.z2 + 1);
        ax = klem(bx, a.x1, a.x2 + 1);
        az = klem(bz, a.z1, a.z2 + 1);
        double laag = Math.min(a.voet, b.voet);
        double hoog = Math.max(a.voet, b.voet) + (a.soort == Soort.SLIJM ? Sprong.stuiterTop(Math.max(0, (i > 0 ? stappen.get(i - 1).voet : a.voet) - a.voet)) : Sprong.TOPHOOGTE) + 1.8;
        int n = (int) Math.ceil(Math.hypot(bx - ax, bz - az) * 4) + 1;
        java.util.Set<Long> gezien = new java.util.HashSet<>();
        for (int k = 0; k <= n; k++) {
            double t = n == 0 ? 0 : k / (double) n;
            int x = (int) Math.floor(ax + (bx - ax) * t), z = (int) Math.floor(az + (bz - az) * t);
            if (!gezien.add(((long) x << 32) ^ (z & 0xffffffffL))) continue;
            if (a.bevat(x, z) || b.bevat(x, z)) continue;
            for (int y = (int) Math.floor(laag); y <= (int) Math.floor(hoog - 0.01); y++) {
                if (!p.doorloopbaar(x, y, z) && stapOp(x, y, z) < 0) {
                    fout.add("level " + nr + ": sprong " + i + "→" + (i + 1) + " geblokkeerd op " + x + "," + y + "," + z + " (" + p.naam(x, y, z) + ")");
                    return fout;
                }
            }
            if (valVrij) {
                for (int y = p.g + 1; y < (int) Math.floor(laag); y++) {
                    if (!p.doorloopbaar(x, y, z) && !p.naam(x, y, z).equals("water") && stapOp(x, y, z) < 0) {
                        fout.add("level " + nr + ": onder sprong " + i + "→" + (i + 1) + " staat iets op " + x + "," + y + "," + z + " (" + p.naam(x, y, z) + ")");
                        return fout;
                    }
                }
            }
        }
        return fout;
    }

    private static double klem(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
