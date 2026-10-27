package nl.burggraaf.mc.lobbymelding;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Grondcontact, voortgang en de hub-regel. */
class RegelsTest {

    static final int G = -61;

    @Test
    void opHetGrasStaanIsGrondcontact() {
        assertTrue(Regels.grondcontact(G + 1, true, false, false, false, G));
        assertTrue(Regels.grondcontact(G + 0.9375, true, false, false, false, G));   // pad
        assertTrue(Regels.grondcontact(G + 1.5, true, false, false, false, G));      // plaatje op de grond
    }

    @Test
    void middenInEenSprongNooit() {
        assertFalse(Regels.grondcontact(G + 1.2, false, false, false, false, G));
        assertFalse(Regels.grondcontact(G + 1.0, false, false, false, false, G));     // vlak boven het gras, nog in de lucht
    }

    @Test
    void opEenRandMuurOfDakLandenTeltNiet() {
        assertFalse(Regels.grondcontact(G + 2, true, false, false, false, G));       // hagje, muurtje (h1)
        assertFalse(Regels.grondcontact(G + 9, true, false, false, false, G));       // dak
        assertFalse(Regels.grondcontact(G + 2.5, true, false, false, false, G));     // trap
    }

    @Test
    void aanEenLadderNooit() {
        assertFalse(Regels.grondcontact(G + 1, true, true, false, false, G));
    }

    @Test
    void opDeBaanNooit() {
        assertFalse(Regels.grondcontact(G + 1, true, false, false, true, G));
    }

    @Test
    void inDeVijverOfDoorDeVloer() {
        assertTrue(Regels.grondcontact(G + 0.4, false, false, true, false, G));
        assertFalse(Regels.grondcontact(G + 2.3, false, false, true, false, G));     // spatje water hoog op de baan
        assertTrue(Regels.grondcontact(G - 2, false, false, false, false, G));
    }

    @Test
    void naastDeStartStappenStoptAlleen() {
        assertTrue(Regels.naastStart(0, 2.0));
        assertFalse(Regels.naastStart(0, 5.0));
        assertFalse(Regels.naastStart(3, 1.0));
    }

    @Test
    void hubNaarHoogsteGespeeldeLevel() {
        assertEquals(0, Regels.hubLevel(0, 0, 10));           // nooit parkour: spawn
        assertEquals(4, Regels.hubLevel(3, 4, 10));           // gestart aan level 4
        assertEquals(6, Regels.hubLevel(6, 2, 10));
        assertEquals(10, Regels.hubLevel(12, 0, 10));         // oude voortgang hoger dan het aantal levels
    }

    @Test
    void volgendEnOpen() {
        assertEquals(0, Regels.volgend(0, 10));
        assertEquals(3, Regels.volgend(3, 10));
        assertEquals(9, Regels.volgend(10, 10));
        assertTrue(Regels.open(0, 0));
        assertTrue(Regels.open(3, 3));
        assertFalse(Regels.open(4, 3));
    }

    @Test
    void tijdNotatie() {
        assertEquals("0:05.2", Regels.tijd(5_234));
        assertEquals("1:02.0", Regels.tijd(62_000));
    }
}
