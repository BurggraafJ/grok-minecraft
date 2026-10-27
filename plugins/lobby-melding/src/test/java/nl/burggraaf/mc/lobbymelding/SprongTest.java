package nl.burggraaf.mc.lobbymelding;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** De sprongfysica tegen bekende Minecraft-feiten (parkourspelers weten deze uit hun hoofd). */
class SprongTest {

    @Test
    void sprongHoogteIsAnderhalfBlokNiet() {
        assertEquals(1.25, Sprong.TOPHOOGTE, 0.01);                       // 0,42 omhoog: top 1,2522
    }

    @Test
    void vlakDrieBlokkenGatKanVanafEenLosBlok() {
        // "4b-sprong": drie blokken lucht ertussen, vanaf één blok met een sprintsprong
        double nodig = Sprong.nodig(0, 0, 0, 0, 4, 0, 4, 0);
        assertEquals(2.4, nodig, 1e-9);
        assertTrue(Sprong.bereik(1, 0) - nodig > 0.9);
    }

    @Test
    void vierBlokkenGatPlatIsTeKrap() {
        // vier blokken lucht: kan alleen met een perfecte aanloop; voor ons telt het als niet haalbaar (marge < 0,5)
        double nodig = Sprong.nodig(0, 0, 0, 0, 5, 0, 5, 0);
        assertTrue(Sprong.bereik(1, 0) - nodig < 0.5);
    }

    @Test
    void eenOmhoogMetTweeGatKan_drieGatIsKrap() {
        assertTrue(Sprong.bereik(1, 1) - Sprong.nodig(0, 0, 0, 0, 3, 0, 3, 0) > 1.0);
        assertTrue(Sprong.bereik(1, 1) - Sprong.nodig(0, 0, 0, 0, 4, 0, 4, 0) < 0.5);
    }

    @Test
    void tweeOmhoogKanNooit() {
        assertEquals(Double.NEGATIVE_INFINITY, Sprong.bereik(3, 2));
        assertEquals(Double.NEGATIVE_INFINITY, Sprong.bereik(3, 1.3));
        assertTrue(Sprong.bereik(1, 1) > 0);
    }

    @Test
    void omlaagSpringJeVerder() {
        assertTrue(Sprong.bereik(1, -2) > Sprong.bereik(1, 0));
        assertTrue(Sprong.bereik(1, 0) > Sprong.bereik(1, 1));
    }

    @Test
    void schuinOverHoek() {
        // één blok lucht schuin (dx 2, dz 2): je mag langs de hoek, nodig = √(0,4² + 0,4²)
        assertEquals(Math.hypot(0.4, 0.4), Sprong.nodig(0, 0, 0, 0, 2, 2, 2, 2), 1e-9);
        assertEquals(0, Sprong.nodig(0, 0, 0, 0, 1, 0, 1, 0), 1e-9);    // naast elkaar
    }

    @Test
    void vanafIJsSpringJeVerder() {
        // op ijs glijd je door: na de afzet remt de grond je minder af
        assertTrue(Sprong.bereik(1, 0, 0.98) > Sprong.bereik(1, 0, 0.6));
    }

    @Test
    void slijmGeeftJeHoogteTerug() {
        assertTrue(Sprong.stuiterTop(5) > 4.2);
        assertTrue(Sprong.stuiterTop(3) > 2.8);
        assertTrue(Sprong.stuiterTop(3) < 3.6);
        assertTrue(Sprong.stuiterBereik(3, 2) > 1.0);
    }
}
