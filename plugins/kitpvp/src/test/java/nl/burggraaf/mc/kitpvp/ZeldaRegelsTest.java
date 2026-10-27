package nl.burggraaf.mc.kitpvp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ZeldaRegelsTest {

    private static ZeldaVoortgang.Stand stand(String... vlaggen) {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        for (String v : vlaggen) s.zet(v);
        return s;
    }

    @Test
    void harten() {
        assertEquals(8, ZeldaRegels.harten(stand()));
        assertEquals(9, ZeldaRegels.harten(stand("hart1")), "hartcontainer");
        assertEquals(9, ZeldaRegels.harten(stand("hs:a", "hs:b", "hs:c", "hs:d")), "vier hartstukjes = een hart");
        assertEquals(8, ZeldaRegels.harten(stand("hs:a", "hs:b", "hs:c")), "drie is nog geen hart");
        assertEquals(3, ZeldaRegels.losseStukjes(stand("hs:a", "hs:b", "hs:c")));
        assertEquals(0, ZeldaRegels.losseStukjes(stand("hs:a", "hs:b", "hs:c", "hs:d")));
        assertEquals(8, ZeldaRegels.harten(stand("harnas2", "fles1")), "harnas en flesjes zijn geen harten");
        ZeldaVoortgang.Stand veel = stand();
        for (int i = 0; i < 40; i++) veel.zet("hart" + i);
        assertEquals(ZeldaRegels.MAX_HARTEN, ZeldaRegels.harten(veel));
    }

    @Test
    void harnasIsHetBeste() {
        assertEquals(0, ZeldaRegels.harnas(stand()));
        assertEquals(1, ZeldaRegels.harnas(stand("harnas1")));
        assertEquals(3, ZeldaRegels.harnas(stand("harnas1", "harnas3")));
        assertEquals(4, ZeldaRegels.harnas(stand("harnas4", "harnas2")));
        assertEquals(ZeldaRegels.MAX_HARNAS + 1, ZeldaRegels.HARNAS_NAAM.length);
    }

    @Test
    void flesjes() {
        ZeldaVoortgang.Stand s = stand();
        assertEquals(0, ZeldaRegels.flessen(s));
        assertFalse(ZeldaRegels.drink(s), "geen flesje");
        s.zet("fles1");
        s.zet("fles2");
        assertEquals(2, ZeldaRegels.flessen(s));
        assertEquals(0, ZeldaRegels.vol(s));
        assertTrue(ZeldaRegels.vulFlessen(s));
        assertFalse(ZeldaRegels.vulFlessen(s), "al vol");
        assertEquals(2, ZeldaRegels.vol(s));
        assertTrue(ZeldaRegels.drink(s));
        assertEquals(1, ZeldaRegels.vol(s));
        assertTrue(ZeldaRegels.drink(s));
        assertFalse(ZeldaRegels.drink(s), "leeg");
        s.teller("fles-vol", 9);
        assertEquals(2, ZeldaRegels.vol(s), "nooit meer vol dan je hebt");
    }

    @Test
    void doelEnMissies() {
        var a = new ZeldaHoofdstuk.Doel("a", "naar a", "precies a", null, s -> s.heeft("a"));
        var b = new ZeldaHoofdstuk.Doel("b", "naar b", null, null, s -> s.heeft("b"));
        assertEquals(a, ZeldaRegels.doel(List.of(a, b), stand()));
        assertEquals(b, ZeldaRegels.doel(List.of(a, b), stand("a")));
        assertNull(ZeldaRegels.doel(List.of(a, b), stand("a", "b")));
        var m = new ZeldaHoofdstuk.Missie("lamp", "De lamp", List.of("Zoek olie.", "Breng de olie terug."));
        var n = new ZeldaHoofdstuk.Missie("vlieger", "De vlieger", List.of("Zoek de vlieger."));
        ZeldaVoortgang.Stand s = stand();
        assertTrue(ZeldaRegels.bezig(List.of(m, n), s).isEmpty());
        s.missies.put("lamp", 2);
        assertEquals(List.of(m), ZeldaRegels.bezig(List.of(m, n), s));
        assertEquals("Breng de olie terug.", ZeldaRegels.stap(m, s));
        s.missies.put("lamp", ZeldaVoortgang.KLAAR);
        assertTrue(ZeldaRegels.bezig(List.of(m, n), s).isEmpty());
        assertNull(ZeldaRegels.stap(m, s));
        assertTrue(s.missieKlaar("lamp"));
    }

    /** Een leeg hoofdstuk om onderdelen in te zetten. */
    private static final class Proef extends ZeldaHoofdstuk {
        String id() { return "p"; }
        String naam() { return "Proef"; }
        int versie() { return 1; }
        String deelId() { return "proef"; }
        void bouw(Bouwer b) { }
    }

    @Test
    void opnieuwVanafEenRustplaats() {
        Proef h = new Proef();
        ZeldaHoofdstuk.Box z = ZeldaHoofdstuk.Box.van(0, 0, 0, 1, 1, 1);
        h.rust(new ZeldaHoofdstuk.Rust("begin", "Begin", null, z, List.of()));
        h.verhaal(new ZeldaHoofdstuk.Verhaal("intro", z, List.of()));
        var r1 = h.rust(new ZeldaHoofdstuk.Rust("eik", "Eik", null, z, List.of()));
        h.verhaal(new ZeldaHoofdstuk.Verhaal("tempel", z, List.of()));
        h.reeks(new ZeldaHoofdstuk.Reeks("vuren", "Vuren", ZeldaHoofdstuk.Reeks.Hoe.KLIK, null, null, null, z, 10));
        h.vondst(new ZeldaHoofdstuk.Vondst("zwaard", "Zwaard", "", Material.IRON_SWORD, null, z));
        var einde = h.verhaal(new ZeldaHoofdstuk.Verhaal("einde", z, List.of()));
        einde.einde = true;
        assertEquals(List.of("v:tempel", "r:vuren"), ZeldaRegels.opnieuw(h.volgorde, r1), "alleen verhaal en puzzels erna, niet het einde of vondsten");
        assertEquals(List.of("v:intro", "v:tempel", "r:vuren"), ZeldaRegels.opnieuw(h.volgorde, h.rust.get(0)));
        // later toegevoegde uitleg op de goede plek in het pad
        h.na("intro", h.verhaal(new ZeldaHoofdstuk.Verhaal("hulp", z, List.of())));
        assertEquals(List.of("v:intro", "v:hulp", "v:tempel", "r:vuren"), ZeldaRegels.opnieuw(h.volgorde, h.rust.get(0)));
        assertSame(r1, h.rust("eik"));
        assertSame(r1, h.onderdeel("eik"));
        assertNull(h.onderdeel("niets"));
    }

    @Test
    void gesprekken() {
        var pe = new ZeldaHoofdstuk.Persoon("Mira", null, null, List.of(ZeldaHoofdstuk.r("Mira", "Mooi weer.")));
        pe.gesprek("mira-1", s -> s.missie("lamp") == 0, List.of(ZeldaHoofdstuk.r("Mira", "Mijn lamp is uit.")), null);
        pe.gesprek(null, s -> s.missie("lamp") == 1, List.of(ZeldaHoofdstuk.r("Mira", "Nog geen olie?")), null);
        ZeldaVoortgang.Stand s = stand();
        assertEquals("mira-1", pe.gesprek(s).id());
        assertTrue(pe.nieuw(s), "een gesprek met id dat je nog niet hoorde = iets nieuws");
        s.zet("g:mira-1");
        assertFalse(pe.nieuw(s));
        s.missies.put("lamp", 1);
        assertNull(pe.gesprek(s).id());
        assertFalse(pe.nieuw(s), "zonder id nooit nieuw");
        s.missies.put("lamp", ZeldaVoortgang.KLAAR);
        assertNull(pe.gesprek(s), "niets klopt: het gewone praatje");
    }

    private static ZeldaHoofdstuk.Kamer kamer(String id) {
        ZeldaHoofdstuk.Box z = ZeldaHoofdstuk.Box.van(0, 0, 0, 1, 1, 1);
        return new ZeldaHoofdstuk.Kamer(id, id, z, z, new ZeldaHoofdstuk.Deur(null, null), new ZeldaHoofdstuk.Deur(null, null));
    }

    @Test
    void hoogstensTweeVerplichteKamersVoorDeBaas() {
        Proef h = new Proef();
        h.kamer(kamer("open-plek"));
        var vrij = h.kamer(kamer("uitdaging"));
        vrij.vrijwillig = true;
        h.kamer(kamer("veen"));
        var baas = h.kamer(kamer("heksenkring"));
        baas.baas = new ZeldaBaas("Grauwmoer", List.of(), List.of(), List.of(), List.of());
        h.kamer(kamer("kade"));
        assertEquals(2, ZeldaRegels.verplichtVoorBaas(h.volgorde), "vrijwillige kamers en wat na de baas komt tellen niet");
        Proef zonder = new Proef();
        zonder.kamer(kamer("a"));
        assertEquals(1, ZeldaRegels.verplichtVoorBaas(zonder.volgorde), "geen baas: alle kamers");
    }

    @Test
    void vrijeDoorgangAlsIedereenAlWon() {
        ZeldaVoortgang.Stand oud = stand("k:veen"), nieuw = stand();
        assertTrue(ZeldaRegels.doorgang(List.of(oud), "veen"));
        assertFalse(ZeldaRegels.doorgang(List.of(oud, nieuw), "veen"), "een nieuwe speler erbij: gewoon vechten");
        assertFalse(ZeldaRegels.doorgang(List.of(oud), "kade"));
        assertFalse(ZeldaRegels.doorgang(List.of(), "veen"));
    }

    @Test
    void deBelBrengtExtraGolven() {
        var k = kamer("open-plek");
        k.golf(ZeldaHoofdstuk.Soort.VEENLOPER, ZeldaHoofdstuk.Soort.VEENLOPER).extra(ZeldaHoofdstuk.Soort.SPIN);
        assertEquals(1, ZeldaRegels.golven(k), "gewoon: alleen de lichte golf");
        k.terug = true;
        assertEquals(2, ZeldaRegels.golven(k));
        assertEquals(List.of(ZeldaHoofdstuk.Soort.VEENLOPER, ZeldaHoofdstuk.Soort.VEENLOPER), ZeldaRegels.golf(k, 0));
        assertEquals(List.of(ZeldaHoofdstuk.Soort.SPIN), ZeldaRegels.golf(k, 1));
    }

    @Test
    void eenmaligGesprek() {
        var pe = new ZeldaHoofdstuk.Persoon("Lotje", null, null, List.of(ZeldaHoofdstuk.r("Lotje", "Vijftig!")));
        pe.eenmalig("lotje-1", null, List.of(ZeldaHoofdstuk.r("Lotje", "Ssst!")), null);
        pe.eenmalig("lotje-zilver", s -> s.heeft("e:zilverstrand"), List.of(ZeldaHoofdstuk.r("Lotje", "Meeuwen?")), null);
        ZeldaVoortgang.Stand s = stand();
        assertEquals("lotje-1", pe.gesprek(s).id());
        s.zet("g:lotje-1");
        assertNull(pe.gesprek(s), "gehoord: daarna het gewone praatje");
        s.zet("e:zilverstrand");
        assertTrue(pe.nieuw(s));
    }

    @Test
    void hartstukjesVanEenDeel() {
        Proef h = new Proef();
        ZeldaHoofdstuk.Box z = ZeldaHoofdstuk.Box.van(0, 0, 0, 1, 1, 1);
        h.vondst(new ZeldaHoofdstuk.Vondst("hs:p-1", "Hartstukje", "", Material.REDSTONE, null, z));
        h.vondst(new ZeldaHoofdstuk.Vondst("zwaard", "Zwaard", "", Material.IRON_SWORD, null, z));
        h.stukje("hs:p-2");
        assertEquals(java.util.Set.of("hs:p-1", "hs:p-2"), h.stukjes, "vondsten en beloningen tellen mee, gewone vondsten niet");
    }

    @Test
    void oudeStandVanHetLindewoud() {
        ZeldaVoortgang.Stand s = stand("schild", "zwaard", "boog", "hart1", "h1");
        s.bereik("h1", 5);
        assertTrue(ZeldaHoofdstuk1.aanvullen(s, "h1"));
        for (String k : new String[]{"k:open-plek", "k:veen", "k:heksenkring", "k:kade"}) assertTrue(s.heeft(k), k);
        assertFalse(ZeldaHoofdstuk1.aanvullen(s, "h1"), "twee keer = niets nieuws");
        ZeldaVoortgang.Stand jong = stand("schild");
        jong.bereik("h1", 1);
        ZeldaHoofdstuk1.aanvullen(jong, "h1");
        assertTrue(jong.heeft("k:open-plek"));
        assertFalse(jong.heeft("k:veen"), "het Veen nog niet voorbij");
        assertFalse(ZeldaHoofdstuk1.aanvullen(stand(), "h1"), "nieuwe speler: niets");
    }

    @Test
    void plekLezen() {
        assertArrayEquals(new String[]{"h1", "3"}, ZeldaRegels.plek("h1:3"));
        assertArrayEquals(new String[]{"e2a", "0"}, ZeldaRegels.plek("e2a:0"));
        assertNull(ZeldaRegels.plek(null));
        assertNull(ZeldaRegels.plek("h1"));
        assertNull(ZeldaRegels.plek("h1:x"));
        assertNull(ZeldaRegels.plek(":3"));
        assertNull(ZeldaRegels.plek("h1:-1"), "geen negatieve rustplaats");
    }
}
