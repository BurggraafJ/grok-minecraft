package nl.burggraaf.mc.kitpvp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZeldaVoortgangTest {

    @TempDir
    File map;

    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @Test
    void rustplaatsGaatAlleenVooruit() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        assertEquals(0, s.rust("h1"));
        assertTrue(s.bereik("h1", 2));
        assertFalse(s.bereik("h1", 1), "terug naar een eerdere rustplaats is geen voortgang");
        assertFalse(s.bereik("h1", 2));
        assertEquals(2, s.rust("h1"));
        assertTrue(s.bereik("h1", 5));
        assertEquals(0, s.rust("h2"), "elk hoofdstuk apart");
    }

    @Test
    void vlaggenEnTijden() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        assertTrue(s.zet("zwaard"));
        assertFalse(s.zet("zwaard"));
        assertTrue(s.heeft("zwaard"));
        assertNull(s.beste("kloof"));
        assertTrue(s.tijd("kloof", 20_000));
        assertFalse(s.tijd("kloof", 21_000), "langzamer is geen record");
        assertTrue(s.tijd("kloof", 18_500));
        assertEquals(18_500L, s.beste("kloof"));
    }

    @Test
    void bewarenEnTerugLezen() throws Exception {
        File f = new File(map, "zelda.yml");
        ZeldaVoortgang v = new ZeldaVoortgang(f);
        assertEquals(0, v.aantal());
        ZeldaVoortgang.Stand s = v.van(a);
        s.naam = "Test";
        s.hoofdstuk = "h1";
        s.bereik("h1", 3);
        s.zet("schild");
        s.zet("v:intro");
        s.tijd("kloof", 17_250);
        v.van(b).bereik("h1", 1);
        v.vuil();
        v.bewaar();
        assertFalse(v.isVuil());
        assertFalse(new File(map, "zelda.yml.tmp").exists(), "geen tijdelijk bestand achtergelaten");

        ZeldaVoortgang terug = new ZeldaVoortgang(f);
        assertEquals(2, terug.aantal());
        ZeldaVoortgang.Stand t = terug.van(a);
        assertEquals("Test", t.naam);
        assertEquals("h1", t.hoofdstuk);
        assertEquals(3, t.rust("h1"));
        assertTrue(t.heeft("schild") && t.heeft("v:intro"));
        assertEquals(17_250L, t.beste("kloof"));
        assertEquals(1, terug.van(b).rust("h1"));
    }

    @Test
    void wissenEnKapotBestand() throws Exception {
        File f = new File(map, "zelda.yml");
        Files.writeString(f.toPath(), "spelers:\n  geen-uuid:\n    rust: {h1: 4}\n  " + a + ":\n    rust: {h1: 2}\n");
        ZeldaVoortgang v = new ZeldaVoortgang(f);
        assertEquals(1, v.aantal(), "een kapotte sleutel wordt overgeslagen");
        assertEquals(2, v.van(a).rust("h1"));
        v.wis(a);
        assertTrue(v.isVuil());
        assertFalse(v.bekend(a));
        assertEquals(0, v.van(a).rust("h1"));
    }

    @Test
    void kopieIsLos() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        s.bereik("h1", 2);
        ZeldaVoortgang.Stand k = s.kopie();
        k.bereik("h1", 4);
        k.zet("boog");
        assertEquals(2, s.rust("h1"));
        assertFalse(s.heeft("boog"));
    }

    @Test
    void nieuweVeldenBewarenEnTerugLezen() throws Exception {
        File f = new File(map, "zelda.yml");
        ZeldaVoortgang v = new ZeldaVoortgang(f);
        ZeldaVoortgang.Stand s = v.van(a);
        s.laatste = "h1:2";
        s.bereik("h1", 2);
        assertTrue(s.bezoek("h1", 2));
        assertFalse(s.bezoek("h1", 2));
        s.missies.put("lamp", 1);
        s.missies.put("vlieger", ZeldaVoortgang.KLAAR);
        s.teller("fles-vol", 2);
        s.dier = "Pluis";
        v.vuil();
        v.bewaar();
        ZeldaVoortgang.Stand t = new ZeldaVoortgang(f).van(a);
        assertEquals("h1:2", t.laatste);
        assertTrue(t.bezocht("h1", 2));
        assertFalse(t.bezocht("h1", 1), "een nieuwe stand vult niet aan: alleen echt bezochte rustplaatsen");
        assertEquals(1, t.missie("lamp"));
        assertTrue(t.missieKlaar("vlieger"));
        assertEquals(0, t.missie("onbekend"));
        assertEquals(2, t.teller("fles-vol"));
        assertEquals("Pluis", t.dier);
        t.teller("fles-vol", 0);
        assertFalse(t.tellers.containsKey("fles-vol"), "nul = weg");
    }

    @Test
    void oudeStandWordtAangevuld() throws Exception {
        File f = new File(map, "zelda.yml");
        Files.writeString(f.toPath(), "spelers:\n  " + a + ":\n    naam: Oud\n    hoofdstuk: h1\n    rust: {h1: 3}\n    vlaggen: [schild, zwaard, v:intro]\n");
        ZeldaVoortgang.Stand s = new ZeldaVoortgang(f).van(a);
        assertNull(s.laatste, "geen laatste: dan geldt de verste rustplaats");
        for (int i = 0; i <= 3; i++) assertTrue(s.bezocht("h1", i), "rustplaats " + i + " telt als bezocht");
        assertFalse(s.bezocht("h1", 4));
        assertTrue(s.heeft("zwaard"));
        assertTrue(s.missies.isEmpty() && s.tellers.isEmpty() && s.dier == null);
    }

    @Test
    void standVan230BlijftHeel() throws Exception {
        File f = new File(map, "zelda.yml");
        Files.writeString(f.toPath(), "spelers:\n  " + a + ":\n    naam: Twee\n    hoofdstuk: h1\n    rust: {h1: 4}\n"
                + "    vlaggen: [schild, zwaard, boog, v:intro, v:wiske, k:open-plek, fles1, hs:test, o:uitleg-uit]\n"
                + "    tijden: {kloof: 19000}\n    laatste: h1:2\n    bezocht: [h1:0, h1:2, h1:4]\n"
                + "    missies: {proef: 1000}\n    tellers: {fles-vol: 1}\n    dier: Pluis\n");
        ZeldaVoortgang v = new ZeldaVoortgang(f);
        ZeldaVoortgang.Stand s = v.van(a);
        assertEquals("h1:2", s.laatste);
        assertTrue(s.bezocht("h1", 2) && !s.bezocht("h1", 1), "een stand met bezocht wordt niet aangevuld");
        assertTrue(ZeldaHoofdstuk1.aanvullen(s, "h1"));
        assertTrue(s.heeft("k:veen") && s.heeft("fles1") && s.heeft("hs:test") && s.heeft("o:uitleg-uit"));
        v.vuil();
        v.bewaar();
        ZeldaVoortgang.Stand t = new ZeldaVoortgang(f).van(a);
        assertTrue(t.vlaggen.containsAll(s.vlaggen) && s.vlaggen.containsAll(t.vlaggen), "niets kwijt na bewaren");
        assertEquals(19_000L, t.beste("kloof"));
        assertTrue(t.missieKlaar("proef"));
        assertEquals(1, t.teller("fles-vol"));
        assertEquals("Pluis", t.dier);
        assertEquals("h1:2", t.laatste);
    }

    @Test
    void tellenEnVoortgang() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        int v0 = s.voortgang();
        s.zet("hs:a");
        s.zet("hs:b");
        s.zet("hart1");
        assertEquals(2, s.aantal("hs:"));
        assertEquals(1, s.aantal("hart"));
        int v1 = s.voortgang();
        assertNotEquals(v0, v1, "nieuwe vlag = voortgang");
        s.bezoek("h1", 1);
        assertNotEquals(v1, s.voortgang(), "nieuwe rustplaats = voortgang");
        int v2 = s.voortgang();
        s.missies.put("lamp", 1);
        assertNotEquals(v2, s.voortgang(), "missiestap = voortgang");
        assertTrue(s.wis("hs:a"));
        assertFalse(s.wis("hs:a"));
        ZeldaVoortgang.Stand k = s.kopie();
        k.missies.put("lamp", 2);
        k.bezoek("h1", 5);
        assertEquals(1, s.missie("lamp"));
        assertFalse(s.bezocht("h1", 5));
    }

    @Test
    void tijdOpmaak() {
        assertEquals("0:21,4", ZeldaVoortgang.tijd(21_430));
        assertEquals("1:05,0", ZeldaVoortgang.tijd(65_000));
        assertEquals("0:00,0", ZeldaVoortgang.tijd(-5));
    }

    @Test
    void besteTijden() {
        ZeldaVoortgang v = new ZeldaVoortgang(new File(map, "zelda.yml"));
        v.van(a).naam = "Anna";
        v.van(a).tijd("kloof", 19_000);
        v.van(b).naam = "Bram";
        v.van(b).tijd("kloof", 15_500);
        v.van(UUID.randomUUID()).bereik("h1", 2);                // geen tijd: telt niet mee
        var l = v.besteTijden("kloof", 5);
        assertEquals(2, l.size());
        assertEquals("Bram", l.get(0).getKey());
        assertEquals(15_500L, l.get(0).getValue());
        assertEquals(1, v.besteTijden("kloof", 1).size());
        assertTrue(v.besteTijden("anders", 5).isEmpty());
    }
}
