package nl.burggraaf.mc.zelda;

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
        var a = new ZeldaHoofdstuk.Doel("a", "naar a", "precies a", null, s -> s.heeft("a"), null);
        var b = new ZeldaHoofdstuk.Doel("b", "naar b", null, null, s -> s.heeft("b"), null);
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
    void doelenInElkeVolgordeDichtstbijEerst() {
        org.bukkit.Location noord = new org.bukkit.Location(null, 0, 0, -50), oost = new org.bukkit.Location(null, 60, 0, 0), zuid = new org.bukkit.Location(null, 0, 0, 60);
        var tijmen = new ZeldaHoofdstuk.Doel("tijmen", "naar Tijmen", null, null, s -> s.heeft("g:tijmen-1"), null);
        var schild = new ZeldaHoofdstuk.Doel("schild", "schild", null, noord, s -> s.heeft("schild"), "drie");
        var zwaard = new ZeldaHoofdstuk.Doel("zwaard", "zwaard", null, oost, s -> s.heeft("zwaard"), "drie");
        var boog = new ZeldaHoofdstuk.Doel("boog", "boog", null, zuid, s -> s.heeft("boog"), "drie");
        var terug = new ZeldaHoofdstuk.Doel("lichtwoord", "terug", null, null, s -> s.heeft("lichtwoord"), null);
        List<ZeldaHoofdstuk.Doel> l = List.of(tijmen, schild, zwaard, boog, terug);
        assertEquals(tijmen, ZeldaRegels.doel(l, stand(), 50, 0), "eerst het doel zonder groep");
        assertEquals(zwaard, ZeldaRegels.doel(l, stand("g:tijmen-1"), 50, 0), "vanaf het oosten: het zwaard");
        assertEquals(boog, ZeldaRegels.doel(l, stand("g:tijmen-1"), 0, 40), "vanaf het zuiden: de boog");
        assertEquals(schild, ZeldaRegels.doel(l, stand("g:tijmen-1", "boog", "zwaard"), 0, 40), "wat over is, ook al is het ver");
        assertEquals(terug, ZeldaRegels.doel(l, stand("g:tijmen-1", "boog", "zwaard", "schild"), 0, 40));
    }

    @Test
    void sterkBronwater() {
        assertEquals(ZeldaRegels.FLES_HP, ZeldaRegels.flesHp(stand("fles1")));
        assertTrue(ZeldaRegels.flesHp(stand("fles1", "sterkwater")) > ZeldaRegels.FLES_HP, "Hannes zeekraal: flesjes genezen meer");
        assertEquals(1, ZeldaRegels.flessen(stand("fles1", "sterkwater")), "sterk bronwater is geen extra flesje");
    }

    @Test
    void opnieuwOpEenOpenEiland() {
        Proef h = new Proef();
        ZeldaHoofdstuk.Box z = ZeldaHoofdstuk.Box.van(0, 0, 0, 1, 1, 1);
        var dorp = h.rust(new ZeldaHoofdstuk.Rust("dorp", "Dorp", null, z, List.of()));
        h.verhaal(new ZeldaHoofdstuk.Verhaal("plein", z, List.of()));
        var eik = h.rust(new ZeldaHoofdstuk.Rust("eik", "Eik", null, z, List.of()));
        h.verhaal(new ZeldaHoofdstuk.Verhaal("tempel", z, List.of()));
        assertEquals(List.of("v:plein", "v:tempel"), ZeldaRegels.opnieuw(h.volgorde, dorp), "recht pad: alles erna");
        assertEquals(List.of("v:plein"), ZeldaRegels.opnieuw(h.volgorde, dorp, true), "open eiland: alleen tot de volgende rustplaats");
        assertEquals(List.of("v:tempel"), ZeldaRegels.opnieuw(h.volgorde, eik, true));
    }

    @Test
    void oefenplekTeltNietAlsVerplicht() {
        Proef h = new Proef();
        var oefen = h.kamer(kamer("open-plek"));
        oefen.alleenBel = true;
        h.kamer(kamer("kade"));
        var baas = h.kamer(kamer("heksenkring"));
        baas.baas = new ZeldaBaas("Grauwmoer", List.of(), List.of(), List.of(), List.of());
        assertEquals(1, ZeldaRegels.verplichtVoorBaas(h.volgorde), "een kamer die alleen met de bel begint, telt niet");
    }

    @Test
    void rustplaatsenMetVasteNummers() {
        Proef h = new Proef();
        ZeldaHoofdstuk.Box z = ZeldaHoofdstuk.Box.van(0, 0, 0, 1, 1, 1);
        h.rust(0, new ZeldaHoofdstuk.Rust("thuis", "Thuis", null, z, List.of()));
        h.rust(6, new ZeldaHoofdstuk.Rust("dorp", "Lindedorp", null, z, List.of()));
        h.rust(1, new ZeldaHoofdstuk.Rust("eik", "Oude Eik", null, z, List.of()));
        h.rust(2, new ZeldaHoofdstuk.Rust("ruine", "Tempelruïne", null, z, List.of()));
        assertEquals(List.of("thuis", "eik", "ruine", "dorp"), h.rust.stream().map(r -> r.id).toList(), "op nummer (het nummer is de opslag)");
        assertEquals(List.of("thuis", "dorp", "eik", "ruine"), h.volgorde.stream().map(o -> ((ZeldaHoofdstuk.Rust) o).id).toList(), "het pad blijft in volgorde");
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
        for (String k : new String[]{"k:open-plek", "k:veen", "k:heksenkring", "k:kade", ZeldaHoofdstuk1.OMGEZET}) assertTrue(s.heeft(k), k);
        assertFalse(ZeldaHoofdstuk1.aanvullen(s, "h1"), "twee keer = niets nieuws");
        ZeldaVoortgang.Stand jong = stand("schild");
        jong.bereik("h1", 1);
        ZeldaHoofdstuk1.aanvullen(jong, "h1");
        assertTrue(jong.heeft("k:open-plek"));
        assertFalse(jong.heeft("k:veen"), "het Veen nog niet voorbij");
        assertFalse(ZeldaHoofdstuk1.aanvullen(stand(), "h1"), "nieuwe speler: niets");
    }

    @Test
    void oudeStandZonderVlaggenKrijgtWatErVoorLag() {
        // een heel oude stand (alleen de verste rustplaats op het rechte pad): na de omzetting heeft hij wat ervoor lag
        ZeldaVoortgang.Stand moeras = stand();
        moeras.bereik("h1", 4);
        assertTrue(ZeldaHoofdstuk1.aanvullen(moeras, "h1"));
        for (String v : new String[]{"schild", "zwaard", "boog", "k:veen"}) assertTrue(moeras.heeft(v), v);
        assertFalse(moeras.heeft("hart1"), "Grauwmoer nog niet verslagen");
        // na de omzetting telt een nieuwe rustplaats (het dorp is nummer 6) niet meer als «verder op het oude pad»
        ZeldaVoortgang.Stand nieuw = stand();
        ZeldaHoofdstuk1.aanvullen(nieuw, "h1");
        nieuw.bereik("h1", 6);
        ZeldaHoofdstuk1.aanvullen(nieuw, "h1");
        assertFalse(nieuw.heeft("hart1") || nieuw.heeft("schild"), "een nieuwe speler in het dorp krijgt niets cadeau");
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

    @Test
    void spiegelsDraaienMetDeKlokMee() {
        var f = org.bukkit.block.BlockFace.NORTH;
        for (var verwacht : List.of(org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.NORTH)) {
            f = ZeldaRegels.draai(f);
            assertEquals(verwacht, f);
        }
    }

    @Test
    void hetLichtVolgtDeSpiegels() {
        var N = org.bukkit.block.BlockFace.NORTH;
        var W = org.bukkit.block.BlockFace.WEST;
        var S = org.bukkit.block.BlockFace.SOUTH;
        var E = org.bukkit.block.BlockFace.EAST;
        // zoals de kelder van de vuurtoren: bron oost, drie spiegels, doel zuid; muren om een kamer van 0 … 10
        int[] bron = {11, 0, 5}, doel = {2, 0, 11};
        List<int[]> sp = List.of(new int[]{6, 0, 5}, new int[]{6, 0, 1}, new int[]{2, 0, 1});
        java.util.function.Predicate<int[]> vrij = c -> c[0] >= 0 && c[0] <= 10 && c[2] >= 0 && c[2] <= 10;
        var goed = ZeldaRegels.straal(bron, W, sp, List.of(N, W, S), doel, vrij, 64);
        assertTrue(goed.raak(), "de oplossing brengt het licht bij het doel");
        assertEquals(4 + 1 + 3 + 1 + 3 + 1 + 9, goed.punten().size(), "elk blok onderweg, spiegels meegeteld");
        assertFalse(ZeldaRegels.straal(bron, W, sp, List.of(E, W, S), doel, vrij, 64).raak(), "terug naar de bron: geen doel");
        assertFalse(ZeldaRegels.straal(bron, W, sp, List.of(N, S, S), doel, vrij, 64).raak(), "heen en weer tussen twee spiegels: stopt (geen rondjes)");
        var muur = ZeldaRegels.straal(bron, W, sp, List.of(W, W, S), doel, vrij, 64);
        assertFalse(muur.raak());
        assertEquals(0, muur.punten().get(muur.punten().size() - 1)[0], "tot de muur, niet erdoor");
    }

    @Test
    void opnieuwVergeetOokDeSpiegels() {
        Proef h = new Proef();
        ZeldaHoofdstuk.Box z = ZeldaHoofdstuk.Box.van(0, 0, 0, 1, 1, 1);
        var r0 = h.rust(new ZeldaHoofdstuk.Rust("begin", "Begin", null, z, List.of()));
        h.spiegels(new ZeldaHoofdstuk.Spiegels("spiegels", "Spiegels", new int[]{0, 0, 0}, org.bukkit.block.BlockFace.WEST, new int[]{1, 0, 0},
                null, null, null, z));
        var b = h.barstOpPad(new ZeldaHoofdstuk.Barst("kelder", new ZeldaHoofdstuk.Deur(null, null).blok(0, 0, 0), z));
        assertEquals(List.of("r:spiegels"), ZeldaRegels.opnieuw(h.volgorde, r0), "puzzel vergeten, een gebroken muur blijft open");
        assertSame(b, h.onderdeel("kelder"), "een gebarsten muur op het pad hoort bij de volgorde");
        assertTrue(h.volgorde.contains(b));
    }

    @Test
    void eilandnaamInEenZin() {
        assertEquals("het Lindewoud", ZeldaRegels.inZin("Het Lindewoud"));
        assertEquals("de Gloedberg", ZeldaRegels.inZin("De Gloedberg"));
        assertEquals("Nachtrots", ZeldaRegels.inZin("Nachtrots"));
    }

    @Test
    void zoutbaardIsTaaiTotZijnTonKnalt() {
        assertTrue(ZeldaRegels.zoutbaardSchade(10, false) < 5, "zeepokken: een klap doet weinig");
        assertTrue(ZeldaRegels.zoutbaardSchade(10, true) > 10, "versuft: een klap doet meer");
        assertEquals(0, ZeldaRegels.zoutbaardSchade(0, true), 1e-9);
    }

    @Test
    void getijVultVanOnderEnZaktVanBoven() {
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, ZeldaRegels.getijVolgorde(5, true), "vloed: eerst de onderste laag");
        assertArrayEquals(new int[]{4, 3, 2, 1, 0}, ZeldaRegels.getijVolgorde(5, false), "eb: eerst de bovenste laag weg");
        assertEquals(0, ZeldaRegels.getijVolgorde(0, true).length);
    }

    @Test
    void deGetijdenzaalHeeftEenGalerijEnEenBak() {
        assertTrue(ZeldaEiland2b.galerij(-10, 0), "de westkant (bij de poort) is galerij");
        assertTrue(ZeldaEiland2b.galerij(0, -10), "de noordkant is galerij");
        assertFalse(ZeldaEiland2b.galerij(0, 0), "het midden is de bak");
        assertFalse(ZeldaEiland2b.galerij(10, 0), "de oostkant (de drempel) ligt los: alleen zwemmend te halen");
        assertFalse(ZeldaEiland2b.galerij(8, -10), "de noordoosthoek is bak (daar komt de vloeddeur uit)");
    }

    @Test
    void eenStukjeOpnieuwOpEenOpenEiland() {
        ZeldaHoofdstuk.Rust a = new ZeldaHoofdstuk.Rust("a", "A", null, null, java.util.List.of()), b = new ZeldaHoofdstuk.Rust("b", "B", null, null, java.util.List.of());
        ZeldaHoofdstuk.Verhaal v1 = new ZeldaHoofdstuk.Verhaal("een", null, java.util.List.of()), v2 = new ZeldaHoofdstuk.Verhaal("twee", null, java.util.List.of());
        ZeldaHoofdstuk.Getij g = new ZeldaHoofdstuk.Getij("getij", "Zaal", null, false);
        java.util.List<Object> volgorde = java.util.List.of(a, v1, new ZeldaHoofdstuk.Tij(g, null, true), b, v2);
        assertEquals(java.util.List.of("v:een"), ZeldaRegels.opnieuw(volgorde, a, true), "een getij in de volgorde stoort niet");
        assertFalse(g.bezig());
        assertFalse(g.vloed, "een getij begint in rust");
    }

    @Test
    void deKlauwhaakVindtHetHaakpuntWaarJeOpMikt() {
        double[] oog = {0, 0, 0};
        List<double[]> punten = List.of(new double[]{0, 0, 10}, new double[]{3, 0, 10}, new double[]{0, 0, 30});
        assertEquals(0, ZeldaRegels.mik(oog, new double[]{0, 0, 1}, punten, 24, 6), "recht vooruit");
        assertEquals(0, ZeldaRegels.mik(oog, new double[]{0.05, 0, 1}, punten, 24, 6), "net ernaast: toch dat haakpunt");
        assertEquals(1, ZeldaRegels.mik(oog, new double[]{0.3, 0, 1}, punten, 24, 6), "de dichtstbijzijnde richting telt");
        assertEquals(-1, ZeldaRegels.mik(oog, new double[]{1, 0, 0}, punten, 24, 6), "de andere kant op: niets");
        assertEquals(-1, ZeldaRegels.mik(oog, new double[]{0, 0, 1}, List.of(new double[]{0, 0, 30}), 24, 6), "te ver weg");
        assertEquals(-1, ZeldaRegels.mik(oog, new double[]{0, 0, 0}, punten, 24, 6), "geen richting");
        assertEquals(-1, ZeldaRegels.mik(oog, new double[]{0, 0, -1}, punten, 24, 6), "achter je");
    }

    @Test
    void alleenHetSneeuwvosjeLooptMee() {
        assertFalse(ZeldaDier.AAN, "vrije huisdieren blijven uit in Zelda (Jelle)");
        assertTrue(ZeldaDier.actief("sneeuwvos"), "het vosje van de Witte Toppen loopt mee");
        assertFalse(ZeldaDier.actief("vos"));
        assertFalse(ZeldaDier.actief("kat"));
        assertFalse(ZeldaDier.actief(null));
        assertEquals("sneeuwvos", ZeldaDier.soort(stand("dier:vos", "dier:sneeuwvos")), "het dier dat meeloopt gaat voor");
        assertEquals("vos", ZeldaDier.soort(stand("dier:vos")));
        assertNull(ZeldaDier.soort(stand("dier:draak")));
        ZeldaVoortgang.Stand s = stand("dier:sneeuwvos");
        assertEquals(ZeldaDier.NAMEN.get(0), ZeldaDier.naam(s), "nog geen naam: de eerste");
        s.dier = "Pluis";
        assertEquals("Pluis", ZeldaDier.naam(s));
    }

    @Test
    void deKlauwhaakReiktTotDeOudeHaakpunten() {
        assertTrue(ZeldaRegels.HAAK_BEREIK >= 22, "de Meeuwenrots (2a): het haakpunt zit ±21 blokken boven het pleintje");
        assertTrue(ZeldaVoorwerp.LATER.contains("klauwhaak"));
    }

    @Test
    void smidshelmAlleenOnderHetWachtersharnas() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        assertFalse(ZeldaRegels.smidshelm(s, 0), "zonder helm-vlag geen helm");
        s.zet(ZeldaRegels.HELM);
        assertTrue(ZeldaRegels.smidshelm(s, 0));
        assertTrue(ZeldaRegels.smidshelm(s, 1), "ook over het maliënhemd");
        assertFalse(ZeldaRegels.smidshelm(s, 2), "het wachtersharnas heeft een betere helm");
    }

    @Test
    void balansIsLangerMaarLichter() {
        assertTrue(ZeldaRegels.SCHADE < 1 && ZeldaRegels.SCHADE >= 0.3, "vijanden doen minder, maar niet niks");
        assertTrue(ZeldaRegels.BAAS_LEVEN >= 1, "een baas duurt langer, niet korter");
        assertTrue(ZeldaRegels.GOLF_PAUZE >= 2500, "adem tussen golven");
    }

    @Test
    void wieVoorbijDeHeksIsKrijgtDeHelmAlsnog() {
        ZeldaVoortgang.Stand s = new ZeldaVoortgang.Stand();
        s.zet("hart1");
        ZeldaHoofdstuk1.aanvullen(s, "h1");
        assertTrue(s.heeft(ZeldaRegels.HELM));
        ZeldaVoortgang.Stand nieuw = new ZeldaVoortgang.Stand();
        ZeldaHoofdstuk1.aanvullen(nieuw, "h1");
        assertFalse(nieuw.heeft(ZeldaRegels.HELM), "een nieuwe speler haalt hem zelf in Lindedorp");
    }
}
