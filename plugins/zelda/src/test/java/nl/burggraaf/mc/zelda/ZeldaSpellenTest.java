package nl.burggraaf.mc.zelda;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Meerdere spellen per speler, oude voortgang (2.15) en het samen-spel. */
class ZeldaSpellenTest {

    @TempDir
    File map;

    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @Test
    void oudeStandWordtSpelEen() throws Exception {
        File f = new File(map, "zelda.yml");
        Files.writeString(f.toPath(), """
                spelers:
                  %s:
                    naam: Oud
                    hoofdstuk: e4b
                    rust: {h1: 6, e4b: 2}
                    vlaggen: [zwaard, schild, hart1, baken3, 'hs:lindewoud-1', 'o:uitleg-uit']
                    tijden: {kloof: 18000}
                    laatste: 'e4b:2'
                    bezocht: ['h1:0', 'e4b:2']
                    missies: {bijl: 1000}
                    tellers: {fles-vol: 3}
                    dier: Sneeuwvlok
                """.formatted(a));
        ZeldaVoortgang v = new ZeldaVoortgang(f);
        assertEquals(1, v.actief(a));
        assertEquals(Set.of(1), v.spellen(a).keySet());
        ZeldaVoortgang.Stand s = v.van(a);
        assertEquals(ZeldaRegels.Niveau.AVONTUUR, ZeldaRegels.niveau(s), "oude stand = Avontuur (de balans van 2.15)");
        assertEquals("e4b:2", s.laatste);
        assertTrue(s.heeft("baken3") && s.heeft("hs:lindewoud-1") && s.heeft("o:uitleg-uit"));
        assertEquals(3, s.teller("fles-vol"));
        assertEquals(1000, s.missie("bijl"));
        assertEquals("Sneeuwvlok", s.dier);
        assertNull(s.groep);
        // zonder iets te veranderen bewaren en teruglezen: niets weg
        v.vuil();
        v.bewaar();
        String yml = Files.readString(f.toPath());
        assertFalse(yml.contains("\n    spel:"), "één spel: geen spelnummer nodig (2.15 leest het gewoon)");
        ZeldaVoortgang.Stand t = new ZeldaVoortgang(f).van(a);
        assertEquals(s.vlaggen, t.vlaggen);
        assertEquals(s.rust, t.rust);
        assertEquals(s.bezocht, t.bezocht);
        assertEquals(s.tijden, t.tijden);
        assertEquals(s.laatste, t.laatste);
    }

    @Test
    void drieSpellenNaastElkaar() throws Exception {
        File f = new File(map, "zelda.yml");
        ZeldaVoortgang v = new ZeldaVoortgang(f);
        ZeldaVoortgang.Stand een = v.van(a);
        een.naam = "Jelle";
        een.zet("zwaard");
        assertEquals(2, v.vrij(a));
        ZeldaVoortgang.Stand twee = v.nieuw(a, 2, null);
        twee.niveau = ZeldaRegels.Niveau.HELD.ordinal();
        assertEquals("Jelle", twee.naam, "de naam gaat mee");
        assertNull(v.nieuw(a, 2, null), "plek 2 is bezet");
        assertTrue(v.kies(a, 2));
        assertSame(twee, v.van(a));
        assertFalse(v.van(a).heeft("zwaard"), "een nieuw spel begint leeg");
        v.van(a).zet("boog");
        v.nieuw(a, 3, null).zet("schild");
        assertEquals(-1, v.vrij(a), "drie spellen: vol");
        assertFalse(v.kies(a, 4));
        v.van(a).tijd("kloof", 20_000);
        een.tijd("kloof", 19_000);
        v.van(b).naam = "Daan";
        v.van(b).tijd("kloof", 19_500);
        v.vuil();
        v.bewaar();

        ZeldaVoortgang t = new ZeldaVoortgang(f);
        assertEquals(2, t.actief(a), "je speelt verder in het spel waar je was");
        assertEquals(Set.of(1, 2, 3), t.spellen(a).keySet());
        assertTrue(t.van(a).heeft("boog") && !t.van(a).heeft("zwaard"));
        assertEquals(ZeldaRegels.Niveau.HELD, ZeldaRegels.niveau(t.van(a)));
        assertTrue(t.spellen(a).get(1).heeft("zwaard"));
        assertTrue(t.spellen(a).get(3).heeft("schild"));
        assertEquals(2, t.besteTijden("kloof", 5).size(), "één record per speler, over al zijn spellen");
        assertEquals(19_000L, t.besteTijden("kloof", 5).get(0).getValue());
        assertEquals(a, t.zoek("jelle"));
        // één spel wissen
        assertTrue(t.wis(a, 2));
        assertEquals(1, t.actief(a), "het spel waarin je speelde is weg: je eerste andere spel");
        assertFalse(t.wis(a, 2));
        assertTrue(t.wis(a, 1) && t.wis(a, 3));
        assertFalse(t.bekend(a), "geen spellen meer = geen voortgang");
    }

    /** Een samen-spel: Daan neemt de uitnodiging van Jelle aan (een kopie van Jelles spel) en ze delen hun voortgang. */
    private ZeldaVoortgang samen() {
        ZeldaVoortgang v = new ZeldaVoortgang(new File(map, "zelda.yml"));
        ZeldaVoortgang.Stand j = v.van(a);
        j.zet("zwaard");
        j.zet("v:intro");
        j.groep = "g1";
        ZeldaVoortgang.Stand d = j.kopie();
        d.groep = "g1";
        d.niveau = ZeldaRegels.Niveau.VERHAAL.ordinal();
        v.van(b).zet("eigen");                               // Daans eigen spel (1) blijft ernaast
        v.nieuw(b, 2, d);
        v.kies(b, 2);
        v.deel(null);
        return v;
    }

    @Test
    void samenDelenWatNieuwIs() {
        ZeldaVoortgang v = samen();
        ZeldaVoortgang.Stand j = v.van(a), d = v.van(b);
        j.zet("schild");
        j.zet("o:uitleg-uit");
        j.bereik("h1", 4);
        j.bezoek("h1", 4);
        j.missies.put("bijl", 2);
        d.zet("k:veen");
        d.kaart.set(77);
        assertEquals(Set.of(a, b), v.deel(null), "beiden kregen iets");
        assertTrue(d.heeft("schild") && j.heeft("k:veen"));
        assertFalse(d.heeft("o:uitleg-uit"), "opties zijn van jezelf");
        assertEquals(4, d.rust("h1"));
        assertTrue(d.bezocht("h1", 4));
        assertEquals(2, d.missie("bijl"));
        assertTrue(j.kaart.get(77));
        assertEquals(ZeldaRegels.Niveau.VERHAAL, ZeldaRegels.niveau(d), "ieder zijn eigen niveau");
        assertFalse(v.spellen(b).get(1).heeft("schild"), "Daans eigen spel deelt niet mee");
        assertTrue(v.deel(null).isEmpty(), "niets nieuws: niemand krijgt iets");
        // opnieuw vanaf hier: vergeten blijft vergeten
        d.wis("v:intro");
        assertTrue(v.deel(null).isEmpty());
        assertFalse(d.heeft("v:intro"));
        assertTrue(j.heeft("v:intro"));
    }

    @Test
    void eenNieuweGroepDeeltMeteen() {
        ZeldaVoortgang v = new ZeldaVoortgang(new File(map, "zelda.yml"));
        v.van(a).zet("zwaard");
        v.van(a).groep = "g2";
        ZeldaVoortgang.Stand d = v.van(a).kopie();
        v.nieuw(b, 2, d);
        v.kies(b, 2);
        v.van(a).zet("boog");                                // nog vóór de eerste ronde: ook dat is nieuw
        v.deel(null);
        assertTrue(v.van(b).heeft("boog"));
    }

    @Test
    void eenDerdeKrijgtNietWatVergetenIs() {
        ZeldaVoortgang v = samen();
        v.van(a).wis("v:intro");                             // Jelle speelt een stuk opnieuw
        UUID c = UUID.randomUUID();
        ZeldaVoortgang.Stand k = v.van(a).kopie();
        v.nieuw(c, 1, k);
        v.sluitAan(c, 1);
        v.deel(null);
        assertFalse(v.van(c).heeft("v:intro"), "een nieuw lid begint bij wat de groep nu heeft");
        v.van(b).zet("k:nieuw");
        v.deel(null);
        assertTrue(v.van(c).heeft("k:nieuw"), "en krijgt wat erbij komt");
        assertTrue(v.wis(c, 1));
        v.nieuw(c, 1, null).groep = "g1";                     // een nieuw spel op dezelfde plek: geen oude teller
        v.deel(null);
        assertTrue(v.van(c).heeft("k:nieuw"));
    }

    @Test
    void samenOokAlsJeErNietBent() {
        ZeldaVoortgang v = samen();
        v.kies(b, 1);                                        // Daan speelt even in zijn eigen spel
        v.van(a).zet("hart1");
        assertEquals(Set.of(), v.deel(null), "Daan speelt nu niet in het samen-spel: hij hoeft niets opnieuw");
        assertTrue(v.spellen(b).get(2).heeft("hart1"), "maar het samen-spel kreeg het wel");
        assertFalse(v.van(b).heeft("hart1"));
    }

    @Test
    void beheerDoetEvenNietMee() {
        ZeldaVoortgang v = samen();
        ZeldaVoortgang.Stand kopie = v.van(b).kopie();       // Daan gaat in beheer
        v.van(a).zet("boog");
        v.deel(id -> id.equals(b));
        assertFalse(v.van(b).heeft("boog"));
        v.van(b).zet("alles-van-beheer");
        v.zet(b, kopie);                                     // beheer uit: zijn voortgang zoals hij was
        v.deel(null);
        assertTrue(v.van(b).heeft("boog"), "na het beheer krijgt hij wat hij miste");
        assertFalse(v.van(a).heeft("alles-van-beheer"));
    }

    @Test
    void samenBewaard() throws Exception {
        ZeldaVoortgang v = samen();
        v.vuil();
        v.bewaar();
        ZeldaVoortgang t = new ZeldaVoortgang(new File(map, "zelda.yml"));
        assertEquals("g1", t.van(a).groep);
        assertEquals("g1", t.van(b).groep);
        assertEquals(2, t.groep("g1").size());
        assertTrue(t.deel(null).isEmpty(), "na het laden: niets dubbel");
        t.van(b).zet("baken2");
        t.deel(null);
        assertTrue(t.van(a).heeft("baken2"));
    }
}
