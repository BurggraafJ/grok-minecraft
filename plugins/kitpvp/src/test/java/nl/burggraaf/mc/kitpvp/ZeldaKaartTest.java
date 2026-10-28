package nl.burggraaf.mc.kitpvp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * De kaart in vak 9 (zelda-kaart): het raster van wat je ontdekte (en hoe het in zelda.yml staat), een eilandblad met zee, kust
 * en reliëf, de mist over wat je nog niet zag, de zeekaart, en waar de pijlen staan.
 */
class ZeldaKaartTest {

    private static final int N = ZeldaKaartBeeld.N;

    @Test
    void hetRasterLigtOverHetZeldaGebied() {
        assertEquals(Zelda.GEBIED[0], ZeldaKaartBeeld.GX);
        assertEquals(Zelda.GEBIED[1], ZeldaKaartBeeld.GZ);
        assertEquals(Zelda.GEBIED[2] + 1, ZeldaKaartBeeld.GX + ZeldaKaartBeeld.CEL * ZeldaKaartBeeld.CELLEN);
        assertEquals(Zelda.GEBIED[3] + 1, ZeldaKaartBeeld.GZ + ZeldaKaartBeeld.CEL * ZeldaKaartBeeld.CELLEN);
        assertEquals(0, ZeldaKaartBeeld.cel(1024, -512));
        assertEquals(ZeldaKaartBeeld.CELLEN * ZeldaKaartBeeld.CELLEN - 1, ZeldaKaartBeeld.cel(2047.9, 511.9));
        assertEquals(-1, ZeldaKaartBeeld.cel(1023.9, 0), "buiten het gebied");
        assertEquals(-1, ZeldaKaartBeeld.cel(1500, 512));
    }

    @Test
    void ontdekkenRondJe() {
        BitSet b = new BitSet();
        assertTrue(ZeldaKaartBeeld.ontdek(b, 1212, 2, ZeldaKaartBeeld.ZICHT));
        assertFalse(ZeldaKaartBeeld.ontdek(b, 1212, 2, ZeldaKaartBeeld.ZICHT), "nog eens: niets nieuws");
        assertTrue(b.get(ZeldaKaartBeeld.cel(1212, 2)));
        assertTrue(b.get(ZeldaKaartBeeld.cel(1212 + ZeldaKaartBeeld.ZICHT - 2, 2)), "binnen je zicht");
        assertFalse(b.get(ZeldaKaartBeeld.cel(1212 + ZeldaKaartBeeld.ZICHT + 16, 2)), "verder weg niet");
        int n = b.cardinality();
        assertTrue(n >= 25 && n <= 50, "een rondje van een paar vakjes: " + n);
        BitSet c = new BitSet();
        assertTrue(ZeldaKaartBeeld.ontdek(c, 1104, -16, 1135, 15));
        assertEquals(4 * 4, c.cardinality(), "een gebied van 32 × 32 blokken: 4 × 4 vakjes");
        assertFalse(ZeldaKaartBeeld.ontdek(new BitSet(), 0, 0, 100, 100), "buiten het gebied: niets");
    }

    @Test
    void hetRasterInZeldaYml() throws Exception {
        BitSet b = new BitSet();
        for (int i = 0; i < 40; i++) ZeldaKaartBeeld.ontdek(b, 1100 + i * 9, -50 + i * 3, ZeldaKaartBeeld.ZICHT);
        String s = ZeldaKaartBeeld.schrijf(b);
        assertEquals(b, ZeldaKaartBeeld.lees(s));
        assertTrue(s.length() < 600, "klein in zelda.yml: " + s.length());
        assertEquals(new BitSet(), ZeldaKaartBeeld.lees(null));
        assertEquals(new BitSet(), ZeldaKaartBeeld.lees("geen kaart!"), "onleesbaar: leeg, geen fout");
        assertEquals(new BitSet(), ZeldaKaartBeeld.lees("AAAA"), "geen ingepakt raster: leeg");
        // en via het echte bestand, met een stand van vóór de kaart (zonder «kaart») ernaast
        Path dir = Files.createTempDirectory("zelda-kaart");
        File f = dir.resolve("zelda.yml").toFile();
        Files.writeString(f.toPath(), "spelers:\n  " + UUID.randomUUID() + ":\n    naam: Oud\n    vlaggen: [zwaard]\n");
        ZeldaVoortgang v = new ZeldaVoortgang(f);
        assertEquals(1, v.aantal());
        UUID id = UUID.randomUUID();
        v.van(id).kaart.or(b);
        v.bewaar();
        ZeldaVoortgang terug = new ZeldaVoortgang(f);
        assertEquals(b, terug.van(id).kaart);
        assertEquals(b, terug.van(id).kopie().kaart, "de kopie (beheer) neemt het mee");
        UUID oud = terug.zoek("Oud");
        assertNotNull(oud);
        assertTrue(terug.van(oud).kaart.isEmpty() && terug.van(oud).heeft("zwaard"), "een oude stand: geen kaart, verder alles");
    }

    @Test
    void eenBladOmEenEiland() {
        ZeldaKaartBeeld.Vlak v = ZeldaKaartBeeld.Vlak.om(1045, -105, 1492, 105);
        assertTrue(v.op(1045, -105) && v.op(1492.9, 105.9) && v.op(1268, 0));
        assertEquals(64, v.px(1269), 0.01, "het midden van het eiland in het midden van het blad");
        assertTrue(v.px(1045) >= 3.9 && v.px(1493) <= 124.1, "een randje zee: " + v.px(1045) + " … " + v.px(1493));
        for (int i : new int[]{0, 17, 127}) assertEquals(i, (int) Math.floor(v.px(v.wx(i))));
        ZeldaKaartBeeld.Vlak z = ZeldaKaartBeeld.Vlak.zee();
        assertEquals(8, z.schaal(), 1e-9);
        assertTrue(z.op(1024, -512) && z.op(2047.9, 511.9) && !z.op(2048, 0));
    }

    /** Een proefeiland: een rond eiland met een meertje en een heuvel in het midden. */
    private static ZeldaKaartBeeld.Basis proef() {
        ZeldaKaartBeeld.Vlak v = ZeldaKaartBeeld.Vlak.om(1100, -100, 1300, 100);
        byte[] soort = new byte[N * N], kleur = new byte[N * N];
        int[] hoogte = new int[N * N];
        byte gras = ZeldaKaartBeeld.kleur(0x7fb238);
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                int p = j * N + i;
                double d = Math.hypot(i - 64, j - 64);
                if (d < 40) {
                    soort[p] = ZeldaKaartBeeld.LAND;
                    kleur[p] = gras;
                    hoogte[p] = (int) (40 - d);
                } else {
                    soort[p] = ZeldaKaartBeeld.NAT;
                    kleur[p] = 5;
                }
                if (Math.hypot(i - 50, j - 64) < 5) {
                    soort[p] = ZeldaKaartBeeld.NAT;
                    kleur[p] = 1;
                }
                if (i > 120) soort[p] = ZeldaKaartBeeld.BUITEN;
            }
        return ZeldaKaartBeeld.basis(v, soort, hoogte, kleur);
    }

    @Test
    void zeeKustEnReliëf() {
        ZeldaKaartBeeld.Basis b = proef();
        assertTrue(b.isLand(64, 64) && b.isLand(50, 64), "het meertje telt als land (de mist ligt erover)");
        assertFalse(b.isLand(2, 2) || b.isLand(125, 64), "open zee (ook buiten het eiland)");
        assertEquals(ZeldaKaartBeeld.WATER, b.kleur()[64 * N + 50], "water op het land blijft water");
        assertEquals(ZeldaKaartBeeld.SCHUIM, b.kleur()[64 * N + 64 + 40], "schuim langs de kust");
        assertEquals(ZeldaKaartBeeld.ONDIEP, b.kleur()[64 * N + 64 + 42]);
        assertTrue(Set.of(ZeldaKaartBeeld.DIEP, ZeldaKaartBeeld.GOLF).contains(b.kleur()[5 * N + 5]), "ver weg: diep (of een golfje)");
        int gras = (ZeldaKaartBeeld.kleur(0x7fb238) & 0xff) >> 2;
        assertEquals(gras * 4 + 2, b.kleur()[40 * N + 64] & 0xff, "noordhelling van de heuvel: licht");
        assertEquals(gras * 4, b.kleur()[90 * N + 64] & 0xff, "zuidhelling: donker");
        long golfjes = 0;
        for (byte k : b.kleur()) if (k == ZeldaKaartBeeld.GOLF) golfjes++;
        assertTrue(golfjes > 8 && golfjes < 260, "hier en daar een golfje: " + golfjes);
        byte[] soort = new byte[N * N], kleur = new byte[N * N];             // een spikkel verdwijnt, een pad blijft
        int[] hoogte = new int[N * N];
        byte groen = ZeldaKaartBeeld.kleur(0x7fb238), steen = ZeldaKaartBeeld.kleur(0x707070);
        java.util.Arrays.fill(soort, ZeldaKaartBeeld.LAND);
        java.util.Arrays.fill(kleur, groen);
        kleur[30 * N + 30] = steen;
        for (int i = 50; i < 70; i++) kleur[80 * N + i] = steen;
        ZeldaKaartBeeld.Basis s = ZeldaKaartBeeld.basis(b.vlak(), soort, hoogte, kleur);
        assertEquals((groen & 0xff) >> 2, (s.kleur()[30 * N + 30] & 0xff) >> 2, "een losse spikkel: weg");
        assertEquals((steen & 0xff) >> 2, (s.kleur()[80 * N + 60] & 0xff) >> 2, "een pad: blijft");
    }

    @Test
    void mistOverWatJeNogNietZag() {
        ZeldaKaartBeeld.Basis b = proef();
        assertArrayEquals(b.kleur(), ZeldaKaartBeeld.blad(b, new BitSet(), true), "beheer: alles");
        byte[] niets = ZeldaKaartBeeld.blad(b, new BitSet(), false);
        assertEquals(ZeldaKaartBeeld.PERKAMENT, niets[64 * N + 64], "niets gezien: perkament");
        assertEquals(ZeldaKaartBeeld.PERKAMENT_RAND, niets[64 * N + 64 + 39], "de kust van het eiland ken je wel");
        assertEquals(b.kleur()[5 * N + 5], niets[5 * N + 5], "de zee is er altijd");
        BitSet o = new BitSet();
        ZeldaKaartBeeld.Vlak v = b.vlak();
        ZeldaKaartBeeld.ontdek(o, v.wx(64), v.wz(64), ZeldaKaartBeeld.ZICHT);
        byte[] wat = ZeldaKaartBeeld.blad(b, o, false);
        assertEquals(b.kleur()[64 * N + 64], wat[64 * N + 64], "waar je stond: het land");
        assertEquals(ZeldaKaartBeeld.PERKAMENT, wat[64 * N + 100], "verder weg nog niet");
        int zacht = 0;
        for (int i = 64; i < 100; i++) if (wat[64 * N + i] != b.kleur()[64 * N + i] && wat[64 * N + i - 1] == b.kleur()[64 * N + i - 1] && i > 65) zacht++;
        assertTrue(zacht >= 1, "een zachte grens (om en om)");
    }

    @Test
    void deZeekaart() {
        ZeldaKaartBeeld.Basis b = proef();
        List<int[]> vak = List.<int[]>of(new int[]{1100, -100, 1300, 100});
        ZeldaKaartBeeld.Vlak z = ZeldaKaartBeeld.Vlak.zee();
        int i = (int) z.px(1200), j = (int) z.pz(0);
        boolean[] zee = new boolean[N * N];
        byte[] kleur = ZeldaKaartBeeld.zeekaart(List.of(new ZeldaKaartBeeld.Eilandje(b, vak, ZeldaKaartBeeld.Zicht.KLEUR, null)), zee);
        assertFalse(zee[j * N + i], "het eiland is land");
        assertTrue(zee[0], "de hoek is zee");
        assertNotEquals(ZeldaKaartBeeld.PERKAMENT, kleur[j * N + i], "in kleur");
        byte[] vorm = ZeldaKaartBeeld.zeekaart(List.of(new ZeldaKaartBeeld.Eilandje(b, vak, ZeldaKaartBeeld.Zicht.VORM, null)), null);
        assertEquals(ZeldaKaartBeeld.PERKAMENT, vorm[j * N + i], "nog niet geweest: de vorm op perkament");
        byte[] weg = ZeldaKaartBeeld.zeekaart(List.of(new ZeldaKaartBeeld.Eilandje(b, vak, ZeldaKaartBeeld.Zicht.WEG, null)), null);
        assertTrue(Set.of(ZeldaKaartBeeld.DIEP, ZeldaKaartBeeld.GOLF).contains(weg[j * N + i]), "nog niet open: zee");
        byte[] nevel = ZeldaKaartBeeld.zeekaart(List.of(new ZeldaKaartBeeld.Eilandje(b, vak, ZeldaKaartBeeld.Zicht.NEVEL, null)), null);
        assertTrue(Set.of(ZeldaKaartBeeld.NEVEL, ZeldaKaartBeeld.NEVEL_LICHT).contains(nevel[j * N + i]), "Nachtrots in Morgals nevel");
        byte[] mist = ZeldaKaartBeeld.zeekaart(List.of(new ZeldaKaartBeeld.Eilandje(b, vak, ZeldaKaartBeeld.Zicht.KLEUR, new BitSet())), null);
        assertEquals(ZeldaKaartBeeld.PERKAMENT, mist[j * N + i], "geweest maar daar niet gekeken: perkament");
        byte[] px = new byte[N * N];
        ZeldaKaartBeeld.stippel(px, 0, 0, 127, 127, ZeldaKaartBeeld.STIP, zee);
        assertEquals(0, px[j * N + i], "een vaarroute gaat niet over land");
        int stippen = 0;
        for (byte k : px) if (k == ZeldaKaartBeeld.STIP) stippen++;
        assertTrue(stippen > 20 && stippen < 80, "stippels, geen lijn: " + stippen);
    }

    @Test
    void pijlenEnTekentjes() {
        assertEquals(0, ZeldaKaartBeeld.richting(0), "zuid");
        assertEquals(4, ZeldaKaartBeeld.richting(90), "west");
        assertEquals(8, ZeldaKaartBeeld.richting(180), "noord (rechtop)");
        assertEquals(8, ZeldaKaartBeeld.richting(-180));
        assertEquals(12, ZeldaKaartBeeld.richting(-90), "oost");
        assertEquals(0, ZeldaKaartBeeld.richting(355));
        assertEquals(0, ZeldaKaartBeeld.cursor(64));
        assertEquals(-128, ZeldaKaartBeeld.cursor(0));
        assertEquals(127, ZeldaKaartBeeld.cursor(128));
        assertEquals(-128, ZeldaKaartBeeld.cursor(-20), "buiten het blad: aan de rand");
        byte[] px = new byte[N * N];
        ZeldaKaartBeeld.vlam(px, 0, 0, false);                        // aan de rand: geen fout
        ZeldaKaartBeeld.vlam(px, 127, 127, true);
        ZeldaKaartBeeld.vlam(px, 40, 40, false);
        assertEquals(ZeldaKaartBeeld.HOUT, px[40 * N + 40]);
        assertEquals(ZeldaKaartBeeld.GEEL, px[39 * N + 40]);
        assertEquals(ZeldaKaartBeeld.VUUR, px[38 * N + 40]);
        assertEquals(ZeldaKaartBeeld.ZWART, px[40 * N + 42], "een donker randje");
        ZeldaKaartBeeld.vlam(px, 80, 40, true);
        assertEquals(ZeldaKaartBeeld.ZIEL, px[38 * N + 80], "waar je terugkomt: blauw");
        ZeldaKaartBeeld.tekst(px, 10, 100, "Nachtrots", ZeldaKaartBeeld.WIT);
        assertTrue(ZeldaKaartBeeld.breedte("Nachtrots") > 30 && ZeldaKaartBeeld.breedte("Het Lindewoud") < 100);
        Set<Byte> kleuren = new java.util.HashSet<>(List.of(ZeldaKaartBeeld.DIEP, ZeldaKaartBeeld.ONDIEP, ZeldaKaartBeeld.SCHUIM, ZeldaKaartBeeld.GOLF,
                ZeldaKaartBeeld.PERKAMENT, ZeldaKaartBeeld.PERKAMENT_RAND, ZeldaKaartBeeld.VUUR, ZeldaKaartBeeld.ZIEL, ZeldaKaartBeeld.STIP));
        assertEquals(9, kleuren.size(), "elke kleur is anders");
        assertNotNull(ZeldaKaartBeeld.plaatje(px, List.of(new ZeldaKaartBeeld.Merk("jij", 64, 64, 8), new ZeldaKaartBeeld.Merk("doel", 30, 30, 8),
                new ZeldaKaartBeeld.Merk("dorp", 90, 90, 8), new ZeldaKaartBeeld.Merk("kerker", 100, 20, 8)), 4));
    }

    @Test
    void eenProefplaatje() throws Exception {
        ZeldaKaartBeeld.Basis b = proef();
        BitSet o = new BitSet();
        ZeldaKaartBeeld.ontdek(o, b.vlak().wx(60), b.vlak().wz(60), 40);
        byte[] px = ZeldaKaartBeeld.blad(b, o, false);
        ZeldaKaartBeeld.vlam(px, 60, 62, true);
        ZeldaKaartBeeld.vlam(px, 80, 70, false);
        ZeldaKaartBeeld.titel(px, "Proefeiland");
        File uit = new File("build/zelda-kaart-proef.png");
        uit.getParentFile().mkdirs();
        javax.imageio.ImageIO.write(ZeldaKaartBeeld.plaatje(px, List.of(new ZeldaKaartBeeld.Merk("jij", 60, 58, 6), new ZeldaKaartBeeld.Merk("doel", 85, 50, 8)), 4), "png", uit);
        assertTrue(uit.length() > 1000);
    }
}
