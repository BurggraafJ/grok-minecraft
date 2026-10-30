package nl.burggraaf.mc.zelda;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MinecraftFont;

/**
 * De kaart van Story of Zelda zonder de wereld (dus in unit-tests): het raster van wat je ontdekte, hoe een eiland van boven
 * op het blad komt (een zee met schuim langs de kust, reliëf op het land), de mist over wat je nog niet zag, de zeekaart met
 * alle eilanden, zielvuren, stippellijnen en wat erop staat. Een blad is 128 × 128 pixels met de kleuren van
 * {@link MapPalette}; {@link ZeldaKaart} zet het in de hand van de speler.
 */
@SuppressWarnings({"deprecation", "removal"})
final class ZeldaKaartBeeld {

    private ZeldaKaartBeeld() {
    }

    /** Een kaart is 128 × 128 pixels. */
    static final int N = 128;
    /** Ontdekken gaat per vakje van 8 × 8 blokken, over heel {@link Zelda#GEBIED} (128 × 128 vakjes). */
    static final int CEL = 8, CELLEN = 128;
    /** De hoek van {@link Zelda#GEBIED} (de unit-test kijkt of het klopt). */
    static final int GX = 1024, GZ = -512;
    /** Zo ver om je heen ontdek je (blokken). */
    static final int ZICHT = 32;

    // ── kleuren (de dichtstbijzijnde kleur van een Minecraft-kaart) ──────────────────────────

    static byte kleur(int rgb) {
        return MapPalette.matchColor(new Color(rgb));
    }

    static final byte DIEP = kleur(0x345ab4), ONDIEP = kleur(0x3f6edb), SCHUIM = kleur(0xdcdcdc), GOLF = kleur(0x6699d8),
            PERKAMENT = kleur(0xdcd9d3), PERKAMENT_RAND = kleur(0x878581), WATER = kleur(0x3f6edb), WATER_DIEP = kleur(0x345ab4),
            ZWART = kleur(0x191919), WIT = kleur(0xffffff), VUUR = kleur(0xd87f33), GEEL = kleur(0xe5e533), HOUT = kleur(0x66543a),
            ZIEL = kleur(0x5cdbd5), STIP = kleur(0xf7e9a3), NEVEL = kleur(0x1e1b1b), NEVEL_LICHT = kleur(0x3a3434);

    // ── het raster van wat je ontdekte ──────────────────────────────────────────────────────

    /** Het vakje van deze plek (−1 = buiten het Zelda-gebied). */
    static int cel(double x, double z) {
        int cx = (int) Math.floor((x - GX) / CEL), cz = (int) Math.floor((z - GZ) / CEL);
        return cx < 0 || cz < 0 || cx >= CELLEN || cz >= CELLEN ? -1 : cz * CELLEN + cx;
    }

    /** Alles binnen {@code straal} blokken van (x, z) is ontdekt; true als er iets nieuws bij kwam. */
    static boolean ontdek(BitSet b, double x, double z, int straal) {
        boolean nieuw = false;
        int r = straal / CEL + 1;
        int cx = (int) Math.floor((x - GX) / CEL), cz = (int) Math.floor((z - GZ) / CEL);
        for (int dz = -r; dz <= r; dz++)
            for (int dx = -r; dx <= r; dx++) {
                int x2 = cx + dx, z2 = cz + dz;
                if (x2 < 0 || z2 < 0 || x2 >= CELLEN || z2 >= CELLEN) continue;
                double mx = GX + (x2 + 0.5) * CEL - x, mz = GZ + (z2 + 0.5) * CEL - z;
                if (mx * mx + mz * mz > (straal + CEL / 2.0) * (straal + CEL / 2.0)) continue;
                int i = z2 * CELLEN + x2;
                if (!b.get(i)) {
                    b.set(i);
                    nieuw = true;
                }
            }
        return nieuw;
    }

    /** Een heel gebied (x1 … x2, z1 … z2) is ontdekt; true als er iets nieuws bij kwam. */
    static boolean ontdek(BitSet b, int x1, int z1, int x2, int z2) {
        boolean nieuw = false;
        for (int z = Math.floorDiv(z1 - GZ, CEL); z <= Math.floorDiv(z2 - GZ, CEL); z++)
            for (int x = Math.floorDiv(x1 - GX, CEL); x <= Math.floorDiv(x2 - GX, CEL); x++) {
                if (x < 0 || z < 0 || x >= CELLEN || z >= CELLEN) continue;
                int i = z * CELLEN + x;
                if (!b.get(i)) {
                    b.set(i);
                    nieuw = true;
                }
            }
        return nieuw;
    }

    /** Zo staat het raster in zelda.yml: ingepakt en als tekst (een paar honderd tekens). */
    static String schrijf(BitSet b) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(b.toByteArray());
        d.finish();
        ByteArrayOutputStream uit = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!d.finished()) uit.write(buf, 0, d.deflate(buf));
        d.end();
        return Base64.getEncoder().encodeToString(uit.toByteArray());
    }

    /** Terug uit zelda.yml; onleesbaar = leeg (dan begin je opnieuw met ontdekken, er gaat niets anders verloren). */
    static BitSet lees(String s) {
        if (s == null || s.isEmpty()) return new BitSet();
        Inflater in = new Inflater();
        try {
            in.setInput(Base64.getDecoder().decode(s));
            ByteArrayOutputStream uit = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!in.finished()) {
                int n = in.inflate(buf);
                if (n == 0 && (in.needsInput() || in.needsDictionary())) break;
                uit.write(buf, 0, n);
                if (uit.size() > CELLEN * CELLEN) break;
            }
            return BitSet.valueOf(uit.toByteArray());
        } catch (IllegalArgumentException | DataFormatException e) {
            return new BitSet();
        } finally {
            in.end();
        }
    }

    // ── een blad: welk stuk wereld erop staat ───────────────────────────────────────────────

    /** Welk stuk wereld op een blad staat: pixel (0, 0) begint bij (x0, z0), {@code schaal} blokken per pixel; noord boven. */
    record Vlak(double x0, double z0, double schaal) {

        /** Een blad om dit gebied heen (met een randje zee), zo groot mogelijk. */
        static Vlak om(int x1, int z1, int x2, int z2) {
            double w = x2 - x1 + 1, h = z2 - z1 + 1, s = Math.max(w, h) / (N - 8.0);
            return new Vlak((x1 + x2 + 1) / 2.0 - N / 2.0 * s, (z1 + z2 + 1) / 2.0 - N / 2.0 * s, s);
        }

        /** De zeekaart: heel {@link Zelda#GEBIED}, 8 blokken per pixel. */
        static Vlak zee() {
            return new Vlak(GX, GZ, CELLEN * CEL / (double) N);
        }

        double px(double x) {
            return (x - x0) / schaal;
        }

        double pz(double z) {
            return (z - z0) / schaal;
        }

        /** Het midden van pixel i (wereld-x). */
        double wx(int i) {
            return x0 + (i + 0.5) * schaal;
        }

        double wz(int j) {
            return z0 + (j + 0.5) * schaal;
        }

        boolean op(double x, double z) {
            double i = px(x), j = pz(z);
            return i >= 0 && j >= 0 && i < N && j < N;
        }
    }

    /** Een eilandblad zonder mist: de kleuren van boven, en wat land is (alles wat geen open zee is). */
    record Basis(Vlak vlak, byte[] kleur, BitSet land) {
        boolean isLand(int i, int j) {
            return i >= 0 && j >= 0 && i < N && j < N && land.get(j * N + i);
        }
    }

    /** Wat er bij het meten op een pixel ligt. */
    static final byte BUITEN = 0, NAT = 1, LAND = 2;

    /**
     * Van wat er gemeten is naar een blad: open zee (water dat de rand van het blad raakt) wordt blauw, met schuim langs de kust,
     * lichter in het ondiepe en hier en daar een golfje; land krijgt reliëf (hoger dan noord = lichter, lager = donkerder, zoals
     * een Minecraft-kaart); water op het land (een meer, een gracht) blijft water maar telt als land (de mist ligt erover).
     *
     * @param soort  per pixel {@link #BUITEN} (buiten het eiland: zee), {@link #NAT} of {@link #LAND}
     * @param hoogte per pixel de hoogte van het bovenste blok
     * @param kleur  per pixel de kaartkleur van het bovenste blok (land) of de diepte van het water
     */
    static Basis basis(Vlak vlak, byte[] soort, int[] hoogte, byte[] kleur) {
        boolean[] zee = new boolean[N * N];
        ArrayDeque<Integer> rij = new ArrayDeque<>();
        for (int k = 0; k < N; k++)
            for (int p : new int[]{k, (N - 1) * N + k, k * N, k * N + N - 1})
                if (soort[p] != LAND && !zee[p]) {
                    zee[p] = true;
                    rij.add(p);
                }
        while (!rij.isEmpty()) {
            int p = rij.poll(), i = p % N, j = p / N;
            int[][] buren = {{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}};
            for (int[] b : buren) {
                if (b[0] < 0 || b[1] < 0 || b[0] >= N || b[1] >= N) continue;
                int q = b[1] * N + b[0];
                if (!zee[q] && soort[q] != LAND) {
                    zee[q] = true;
                    rij.add(q);
                }
            }
        }
        int[] basis = rustig(soort, kleur);
        double[] h = gemiddeld(soort, hoogte);
        byte[] uit = new byte[N * N];
        BitSet land = new BitSet(N * N);
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int p = j * N + i;
                if (zee[p]) continue;
                land.set(p);
                if (soort[p] == NAT) uit[p] = kleur[p] >= 3 ? WATER_DIEP : WATER;
                else {
                    int schaduw = 1;
                    if (j > 0 && soort[p - N] == LAND) schaduw = h[p] - h[p - N] > 0.6 ? 2 : h[p] - h[p - N] < -0.6 ? 0 : 1;
                    uit[p] = (byte) (basis[p] * 4 + schaduw);
                    if (uit[p] == PERKAMENT || uit[p] == PERKAMENT_RAND) uit[p] = (byte) (basis[p] * 4 + 2);   // land is nooit perkament (kwarts)
                }
            }
        zee(uit, zee);
        return new Basis(vlak, uit, land);
    }

    /**
     * De kleur van elk stukje land, zonder losse spikkels: een pixel die (bijna) alleen staat tussen buren van één kleur krijgt
     * die kleur. Een pad (een lijn) blijft: daar heeft elke pixel twee buren van zijn eigen kleur.
     */
    private static int[] rustig(byte[] soort, byte[] kleur) {
        int[] b = new int[N * N], uit = new int[N * N];
        for (int p = 0; p < N * N; p++) b[p] = (kleur[p] & 0xFF) >> 2;
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int p = j * N + i;
                uit[p] = b[p];
                if (soort[p] != LAND) continue;
                int zelfde = 0, buren = 0;
                Map<Integer, Integer> tel = new HashMap<>();
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++) {
                        int x = i + di, y = j + dj;
                        if ((di == 0 && dj == 0) || x < 0 || y < 0 || x >= N || y >= N || soort[y * N + x] != LAND) continue;
                        buren++;
                        if (b[y * N + x] == b[p]) zelfde++;
                        tel.merge(b[y * N + x], 1, Integer::sum);
                    }
                if (zelfde > 1 || buren < 6) continue;
                for (var e : tel.entrySet()) if (e.getValue() >= 5) uit[p] = e.getKey();
            }
        return uit;
    }

    /** De hoogte van het land, gemiddeld over 3 × 3 (voor het reliëf: rustiger dan blok voor blok). */
    private static double[] gemiddeld(byte[] soort, int[] hoogte) {
        double[] uit = new double[N * N];
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                double som = 0;
                int n = 0;
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++) {
                        int x = i + di, y = j + dj;
                        if (x < 0 || y < 0 || x >= N || y >= N || soort[y * N + x] != LAND) continue;
                        som += hoogte[y * N + x];
                        n++;
                    }
                uit[j * N + i] = n == 0 ? hoogte[j * N + i] : som / n;
            }
        return uit;
    }

    /** Kleur de zee in (waar {@code zee} klopt): schuim langs het land, ondiep, diep, en een paar golfjes. */
    static void zee(byte[] uit, boolean[] zee) {
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int p = j * N + i;
                if (!zee[p]) continue;
                int d = afstandLand(zee, i, j, 3);
                uit[p] = d == 1 ? SCHUIM : d <= 3 ? ONDIEP : DIEP;
            }
        for (int cj = 0; cj < N / 11 + 1; cj++)                     // een golfje (︵) hier en daar, op een rustig raster
            for (int ci = 0; ci < N / 16 + 1; ci++) {
                int h = (ci * 73 + cj * 151) % 97;
                int x = ci * 16 + h % 11, y = cj * 11 + 1 + (h / 11) % 8;
                int[][] golf = {{x, y}, {x + 1, y - 1}, {x + 2, y - 1}, {x + 3, y}};
                boolean mag = true;
                for (int[] g : golf) mag &= g[0] >= 0 && g[1] >= 0 && g[0] < N && g[1] < N && zee[g[1] * N + g[0]] && afstandLand(zee, g[0], g[1], 5) > 5;
                if (mag) for (int[] g : golf) uit[g[1] * N + g[0]] = GOLF;
            }
    }

    /** Hoe ver het dichtstbijzijnde land is (hoogstens {@code max}; meer = {@code max + 1}). */
    private static int afstandLand(boolean[] zee, int i, int j, int max) {
        for (int r = 1; r <= max; r++)
            for (int dj = -r; dj <= r; dj++)
                for (int di = -r; di <= r; di++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != r || (r == 1 && di != 0 && dj != 0)) continue;
                    int x = i + di, y = j + dj;
                    if (x >= 0 && y >= 0 && x < N && y < N && !zee[y * N + x]) return r;
                }
        return max + 1;
    }

    /**
     * Een eilandblad voor één speler: land dat je nog niet zag is perkament (met een donkerder rand langs de kust: de vorm
     * van het eiland ken je al van zee), de grens is zacht (om en om). {@code alles} = zonder mist (beheer).
     */
    static byte[] blad(Basis b, BitSet ontdekt, boolean alles) {
        byte[] uit = b.kleur().clone();
        if (alles) return uit;
        boolean[] zien = new boolean[N * N];
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++)
                if (b.land().get(j * N + i)) {
                    int c = cel(b.vlak().wx(i), b.vlak().wz(j));
                    zien[j * N + i] = c >= 0 && ontdekt.get(c);
                }
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int p = j * N + i;
                if (!b.land().get(p)) continue;
                boolean grens = false, kust = false;
                int[][] buren = {{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}};
                for (int[] n : buren) {
                    if (!b.isLand(n[0], n[1])) kust = true;
                    else if (zien[n[1] * N + n[0]] != zien[p]) grens = true;
                }
                if (zien[p]) {
                    if (grens && (i + j) % 2 == 1) uit[p] = PERKAMENT;
                } else if (kust) uit[p] = PERKAMENT_RAND;
                else if (!grens || (i + j) % 2 == 1) uit[p] = PERKAMENT;
            }
        return uit;
    }

    // ── de zeekaart ─────────────────────────────────────────────────────────────────────────

    /** Hoe een eiland op de zeekaart staat. */
    enum Zicht { WEG, NEVEL, VORM, KLEUR }

    /**
     * Een eiland op de zeekaart: zijn blad, de stukken wereld die erbij horen ({x1, z1, x2, z2}), hoe je het ziet en wat je
     * ervan ontdekte (null = alles).
     */
    record Eilandje(Basis basis, List<int[]> vakken, Zicht zicht, BitSet ontdekt) {
        boolean in(double x, double z) {
            for (int[] v : vakken) if (x >= v[0] && x < v[2] + 1 && z >= v[1] && z < v[3] + 1) return true;
            return false;
        }
    }

    /**
     * De zeekaart: de Glazen Zee met de eilanden. Een eiland waar je was in kleur (met mist over wat je er nog niet zag), een
     * eiland waar je heen kunt maar nog niet was als vorm op perkament, een eiland in Morgals nevel als een donkere vlek, de
     * rest is zee. {@code zeeOut} krijgt per pixel of het open zee is (voor de vaarroutes).
     */
    static byte[] zeekaart(List<Eilandje> eilanden, boolean[] zeeOut) {
        Vlak v = Vlak.zee();
        byte[] uit = new byte[N * N];
        boolean[] zee = zeeOut != null ? zeeOut : new boolean[N * N];
        java.util.Arrays.fill(zee, true);
        byte[] nevel = new byte[N * N];
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                double x = v.wx(i), z = v.wz(j);
                for (Eilandje e : eilanden) {
                    if (e.zicht() == Zicht.WEG || !e.in(x, z)) continue;
                    Basis b = e.basis();
                    int bi = (int) Math.floor(b.vlak().px(x)), bj = (int) Math.floor(b.vlak().pz(z));
                    if (!b.isLand(bi, bj)) continue;
                    if (e.zicht() == Zicht.NEVEL) {
                        nevel[j * N + i] = 1;
                        continue;
                    }
                    zee[j * N + i] = false;
                    int c = cel(x, z);
                    boolean zien = e.zicht() == Zicht.KLEUR && (e.ontdekt() == null || c >= 0 && e.ontdekt().get(c));
                    uit[j * N + i] = zien ? b.kleur()[bj * N + bi] : PERKAMENT;
                }
            }
        boolean[] land = new boolean[N * N];
        for (int p = 0; p < N * N; p++) land[p] = !zee[p];
        byte[] land2 = uit.clone();
        zee(uit, zee);
        for (int p = 0; p < N * N; p++) if (land[p]) uit[p] = land2[p];
        for (int j = 0; j < N; j++)                                   // de vorm van een eiland: een donkerder rand
            for (int i = 0; i < N; i++) {
                int p = j * N + i;
                if (!land[p] || uit[p] != PERKAMENT) continue;
                for (int[] n : new int[][]{{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}})
                    if (n[0] < 0 || n[1] < 0 || n[0] >= N || n[1] >= N || zee[n[1] * N + n[0]]) uit[p] = PERKAMENT_RAND;
            }
        boolean nevelt = false;
        for (byte n : nevel) nevelt |= n == 1;
        if (nevelt) for (int j = 0; j < N; j++)                       // Morgals nevel: een donkere wolk om het eiland, naar buiten toe ijler
            for (int i = 0; i < N; i++) {
                int d = 99;
                for (int dj = -4; dj <= 4; dj++)
                    for (int di = -4; di <= 4; di++) {
                        int x = i + di, y = j + dj;
                        if (x >= 0 && y >= 0 && x < N && y < N && nevel[y * N + x] == 1) d = Math.min(d, Math.max(Math.abs(di), Math.abs(dj)));
                    }
                if (d > 4 || land[j * N + i]) continue;
                int p = j * N + i;
                if (d <= 1) uit[p] = (i + j) % 2 == 0 ? NEVEL : NEVEL_LICHT;
                else if (d <= 3 ? (i + j) % 2 == 0 : (i % 2 == 0 && j % 2 == 0)) uit[p] = NEVEL_LICHT;
            }
        return uit;
    }

    // ── tekenen ─────────────────────────────────────────────────────────────────────────────

    static void zet(byte[] px, int i, int j, byte c) {
        if (i >= 0 && j >= 0 && i < N && j < N) px[j * N + i] = c;
    }

    /**
     * Een zielvuur (rustplaats) als vlammetje, met een donker randje: oranje, of blauw voor waar je terugkomt
     * ({@code hier}). (i, j) = het hout.
     */
    static void vlam(byte[] px, int i, int j, boolean hier) {
        byte vuur = hier ? ZIEL : VUUR, kern = hier ? WIT : GEEL;
        int[][] vorm = {{0, -2}, {-1, -1}, {1, -1}, {0, -1}, {-1, 0}, {0, 0}, {1, 0}};
        byte[] kl = {vuur, vuur, vuur, kern, HOUT, HOUT, HOUT};
        for (int dj = -3; dj <= 1; dj++)
            for (int di = -2; di <= 2; di++) {
                boolean in = false, naast = false;
                for (int[] v : vorm) {
                    if (v[0] == di && v[1] == dj) in = true;
                    if (Math.abs(v[0] - di) <= 1 && Math.abs(v[1] - dj) <= 1) naast = true;
                }
                if (!in && naast && Math.abs(di) + Math.abs(dj + 1) <= 3) zet(px, i + di, j + dj, ZWART);
            }
        for (int k = 0; k < vorm.length; k++) zet(px, i + vorm[k][0], j + vorm[k][1], kl[k]);
    }

    /**
     * Een vaarroute: een stippellijn van a naar b, alleen over open zee ({@code zee}; null = overal). Ligt er land
     * tussen, dan vaart hij eromheen ({@link #vaarweg}): rechte stukken over zee, met een bocht om de kust.
     */
    static void stippel(byte[] px, double ai, double aj, double bi, double bj, byte c, boolean[] zee) {
        List<double[]> weg = zee == null ? null : vaarweg(zee, ai, aj, bi, bj);
        if (weg == null) weg = List.of(new double[]{ai, aj}, new double[]{bi, bj});
        double lang = 0, op = 3, gedaan = 0;                     // om de 3 pixels een stip, ook over de bochten heen
        for (int k = 0; k + 1 < weg.size(); k++) lang += Math.hypot(weg.get(k + 1)[0] - weg.get(k)[0], weg.get(k + 1)[1] - weg.get(k)[1]);
        for (int k = 0; k + 1 < weg.size(); k++) {
            double[] a = weg.get(k), b = weg.get(k + 1);
            double d = Math.hypot(b[0] - a[0], b[1] - a[1]);
            for (; op - gedaan < d && lang - op >= 1.5; op += 3) {
                double t = (op - gedaan) / d;
                int i = (int) Math.round(a[0] + (b[0] - a[0]) * t), j = (int) Math.round(a[1] + (b[1] - a[1]) * t);
                if (i < 0 || j < 0 || i >= N || j >= N || zee != null && !zee[j * N + i]) continue;
                px[j * N + i] = c;
            }
            gedaan += d;
        }
    }

    /**
     * De weg over zee van a naar b (punten in pixels): het kortste pad over zeepixels (8 kanten op), daarna strakgetrokken
     * (elk punt naar het verste punt dat je over zee ziet). Begin en eind zijn de dichtste zeepixels bij a en b. Null als
     * er geen weg is of het rechtdoor ook kan.
     */
    static List<double[]> vaarweg(boolean[] zee, double ai, double aj, double bi, double bj) {
        int s = dichtsteZee(zee, ai, aj), t = dichtsteZee(zee, bi, bj);
        if (s < 0 || t < 0 || s == t) return null;
        if (vrij(zee, s % N, s / N, t % N, t / N)) return null;
        double[] af = new double[N * N];
        int[] van = new int[N * N];
        java.util.Arrays.fill(af, Double.MAX_VALUE);
        java.util.PriorityQueue<double[]> rij = new java.util.PriorityQueue<>((x, y) -> Double.compare(x[0], y[0]));
        af[s] = 0;
        van[s] = -1;
        rij.add(new double[]{0, s});
        while (!rij.isEmpty()) {
            double[] k = rij.poll();
            int u = (int) k[1];
            if (k[0] > af[u]) continue;
            if (u == t) break;
            int ui = u % N, uj = u / N;
            for (int di = -1; di <= 1; di++)
                for (int dj = -1; dj <= 1; dj++) {
                    int vi = ui + di, vj = uj + dj, v = vj * N + vi;
                    if ((di | dj) == 0 || vi < 0 || vj < 0 || vi >= N || vj >= N || !zee[v]) continue;
                    double nd = af[u] + (di != 0 && dj != 0 ? 1.4142 : 1) + (kust(zee, vi, vj) ? 2 : 0);   // liever niet langs de kust
                    if (nd < af[v]) {
                        af[v] = nd;
                        van[v] = u;
                        rij.add(new double[]{nd, v});
                    }
                }
        }
        if (af[t] == Double.MAX_VALUE) return null;
        List<Integer> pad = new ArrayList<>();
        for (int u = t; u != -1; u = van[u]) pad.add(0, u);
        List<double[]> weg = new ArrayList<>();
        weg.add(new double[]{ai, aj});
        int i = 0;
        while (i < pad.size() - 1) {                            // strak trekken
            int j = pad.size() - 1;
            while (j > i + 1 && !vrij(zee, pad.get(i) % N, pad.get(i) / N, pad.get(j) % N, pad.get(j) / N)) j--;
            if (i > 0) weg.add(new double[]{pad.get(i) % N, pad.get(i) / N});
            i = j;
        }
        weg.add(new double[]{bi, bj});
        return weg;
    }

    private static boolean kust(boolean[] zee, int i, int j) {
        for (int di = -2; di <= 2; di++)
            for (int dj = -2; dj <= 2; dj++) {
                int a = i + di, b = j + dj;
                if (a >= 0 && b >= 0 && a < N && b < N && !zee[b * N + a]) return true;
            }
        return false;
    }

    /** Ligt de rechte lijn tussen twee pixels helemaal over zee? */
    private static boolean vrij(boolean[] zee, int ai, int aj, int bi, int bj) {
        int n = Math.max(Math.abs(bi - ai), Math.abs(bj - aj)) * 2;
        for (int k = 0; k <= n; k++) {
            int i = (int) Math.round(ai + (bi - ai) * (double) k / Math.max(1, n)), j = (int) Math.round(aj + (bj - aj) * (double) k / Math.max(1, n));
            if (!zee[j * N + i]) return false;
        }
        return true;
    }

    private static int dichtsteZee(boolean[] zee, double fi, double fj) {
        int ci = (int) Math.round(fi), cj = (int) Math.round(fj), beste = -1;
        double bd = Double.MAX_VALUE;
        for (int r = 0; r <= 16 && beste < 0; r++)
            for (int i = ci - r; i <= ci + r; i++)
                for (int j = cj - r; j <= cj + r; j++) {
                    if (i < 0 || j < 0 || i >= N || j >= N || !zee[j * N + i]) continue;
                    double d = Math.hypot(i - fi, j - fj);
                    if (d < bd) {
                        bd = d;
                        beste = j * N + i;
                    }
                }
        return beste;
    }

    /** Een donkere band (elke pixel de donkerste tint van zijn eigen kleur): zo is een naam overal te lezen, ook op sneeuw. */
    static void band(byte[] px, int i1, int j1, int i2, int j2) {
        for (int j = Math.max(0, j1); j <= Math.min(N - 1, j2); j++)
            for (int i = Math.max(0, i1); i <= Math.min(N - 1, i2); i++) {
                int c = px[j * N + i] & 0xFF;
                if (c >= 4) px[j * N + i] = (byte) ((c >> 2) * 4 + 3);
            }
    }

    /** De naam van een eiland bovenaan het blad: wit op een donkere band. */
    static void titel(byte[] px, String naam) {
        int w = breedte(naam), i = (N - w) / 2;
        band(px, i - 3, 1, i + w + 2, 12);
        tekst(px, i, 3, naam, WIT);
    }

    /** Tekst in het lettertype van de kaart, met een donkere schaduw; (i, j) = linksboven. */
    static void tekst(byte[] px, int i, int j, String s, byte c) {
        for (int pass = 0; pass < 2; pass++) {
            int x = i;
            for (char ch : s.toCharArray()) {
                MapFont.CharacterSprite sp = MinecraftFont.Font.getChar(ch);
                if (sp == null) continue;
                for (int r = 0; r < sp.getHeight(); r++)
                    for (int k = 0; k < sp.getWidth(); k++)
                        if (sp.get(r, k)) zet(px, x + k + (pass == 0 ? 1 : 0), j + r + (pass == 0 ? 1 : 0), pass == 0 ? ZWART : c);
                x += sp.getWidth() + 1;
            }
        }
    }

    /** Hoe breed een tekst is in het lettertype van de kaart. */
    static int breedte(String s) {
        return MinecraftFont.Font.getWidth(s);
    }

    // ── wat erop staat ──────────────────────────────────────────────────────────────────────

    /** De plek van een symbool op een kaart (x of z in pixels, 0 … 128) als getal voor een {@code MapCursor} (−128 … 127). */
    static byte cursor(double pixel) {
        return (byte) Math.max(-128, Math.min(127, (int) Math.floor((pixel - N / 2.0) * 2 + 0.5)));
    }

    /** De richting van een pijl (0 … 15, zoals Minecraft zelf: 0 = zuid), uit de kijkrichting. */
    static byte richting(double yaw) {
        double y = ((yaw % 360) + 360) % 360;
        return (byte) ((int) Math.floor(y * 16 / 360 + 0.5) & 15);
    }

    /**
     * Iets op de kaart: {@code soort} ({@code jij}, {@code maat}, {@code doel}, {@code dorp}, {@code dorp-sneeuw},
     * {@code kerker}, {@code toren}, …), waar (in pixels) en de richting (0 … 15).
     */
    record Merk(String soort, double i, double j, int richting) {}

    // ── een plaatje (kitpvp zelda kaart) ────────────────────────────────────────────────────

    /** Het blad als plaatje, {@code maal} keer zo groot, met de merken als eenvoudige tekentjes. */
    static BufferedImage plaatje(byte[] px, List<Merk> merken, int maal) {
        BufferedImage img = new BufferedImage(N * maal, N * maal, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                Color c = MapPalette.getColor(px[j * N + i]);
                int rgb = c == null || c.getAlpha() == 0 ? 0xdcd9d3 : c.getRGB() & 0xffffff;
                for (int a = 0; a < maal; a++) for (int b = 0; b < maal; b++) img.setRGB(i * maal + a, j * maal + b, rgb);
            }
        for (Merk m : merken) {                                      // een merk is 8 × 8 pixels van het blad, zoals in Minecraft
            double x = m.i() * maal, y = m.j() * maal, r = 3.2 * maal;
            int x1 = (int) (x - r - 2), x2 = (int) (x + r + 2), y1 = (int) (y - r - 2), y2 = (int) (y + r + 2);
            for (int py = y1; py <= y2; py++)
                for (int px2 = x1; px2 <= x2; px2++) {
                    if (px2 < 0 || py < 0 || px2 >= N * maal || py >= N * maal) continue;
                    double dx = (px2 + 0.5 - x) / r, dy = (py + 0.5 - y) / r;
                    int c = switch (m.soort()) {
                        case "jij", "maat" -> {                      // een pijl; richting 0 = zuid (omlaag), 8 = noord
                            double hoek = m.richting() * Math.PI / 8, u = dx * Math.cos(hoek) + dy * Math.sin(hoek), v = -dx * Math.sin(hoek) + dy * Math.cos(hoek);
                            boolean in = v <= 1.0 && v >= -0.8 && Math.abs(u) <= (1.0 - v) * 0.4;
                            boolean rand = !in && v <= 1.15 && v >= -0.95 && Math.abs(u) <= (1.15 - v) * 0.45;
                            yield in ? (m.soort().equals("jij") ? 0xffffff : 0x55cc55) : rand ? 0x333333 : -1;
                        }
                        case "doel" -> Math.abs(Math.abs(dx) - Math.abs(dy)) < 0.25 && Math.abs(dx) < 0.85 ? 0xcc2222 : -1;
                        default -> {
                            boolean dorp = m.soort().startsWith("dorp");
                            boolean in = dorp ? dy >= -0.8 && dy <= 0.6 && Math.abs(dx) <= (dy + 0.8) * 0.6 : Math.abs(dx) <= 0.6 && Math.abs(dy) <= 0.7;
                            boolean rand = !in && (dorp ? dy >= -0.95 && dy <= 0.75 && Math.abs(dx) <= (dy + 0.95) * 0.65 : Math.abs(dx) <= 0.75 && Math.abs(dy) <= 0.85);
                            yield in ? (dorp ? 0x9c6b3c : m.soort().equals("toren") ? 0x3b2a4a : 0x6f6f5a) : rand ? 0x111111 : -1;
                        }
                    };
                    if (c >= 0) img.setRGB(px2, py, c);
                }
        }
        return img;
    }
}
