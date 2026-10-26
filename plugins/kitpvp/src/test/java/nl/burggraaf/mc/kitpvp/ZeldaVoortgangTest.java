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
    void tijdOpmaak() {
        assertEquals("0:21,4", ZeldaVoortgang.tijd(21_430));
        assertEquals("1:05,0", ZeldaVoortgang.tijd(65_000));
        assertEquals("0:00,0", ZeldaVoortgang.tijd(-5));
    }
}
