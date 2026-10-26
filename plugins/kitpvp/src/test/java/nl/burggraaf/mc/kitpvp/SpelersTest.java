package nl.burggraaf.mc.kitpvp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpelersTest {

    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @Test
    void onbekendIsHub() {
        Spelers s = new Spelers();
        assertEquals(Toestand.HUB, s.van(a));
        assertFalse(s.inArena(a));
        assertNull(s.kit(a));
        assertNull(s.arena(a));
    }

    @Test
    void doorlopen() {
        Spelers s = new Spelers();
        assertEquals(Toestand.HUB, s.zet(a, Toestand.kitruimte("arena", "arena")));
        assertTrue(s.van(a).veilig());
        assertEquals(Toestand.kitruimte("arena", "arena"), s.zet(a, Toestand.arena("arena", "pvp", "krijger")));
        assertTrue(s.inArena(a));
        assertEquals("krijger", s.kit(a));
        assertEquals("pvp", s.arena(a));
        s.zet(b, Toestand.arena("arena", "bots", "boog"));
        assertEquals(Set.of(a), s.inArena("pvp"));
        assertEquals(Set.of(b), s.inArena("bots"));
        assertEquals(2, s.aantalInArena());
        assertEquals(Toestand.arena("arena", "pvp", "krijger"), s.weg(a));
        assertEquals(Toestand.HUB, s.van(a));
        assertEquals(1, s.aantalInArena());
    }

    @Test
    void verlatenAlleenBijAndereModus() {
        Toestand hub = Toestand.HUB, kr = Toestand.kitruimte("arena", "arena"), ar = Toestand.arena("arena", "pvp", "tank"),
                kast = Toestand.kitruimte("kasteel", "kasteel");
        assertNull(Spelers.verlaten(hub, kr));
        assertNull(Spelers.verlaten(kr, ar));
        assertNull(Spelers.verlaten(ar, kr));
        assertEquals("arena", Spelers.verlaten(ar, hub));
        assertEquals("arena", Spelers.verlaten(kr, kast));
        assertEquals("kasteel", Spelers.verlaten(kast, Toestand.spel("zelda", "level1")));
    }

    @Test
    void standVoorHuisdieren() {
        assertEquals("wacht", Spelers.stand(Toestand.HUB, x -> "pvp", t -> "x"));
        assertEquals("wacht", Spelers.stand(Toestand.kitruimte("arena", "oefen"), x -> "pvp", t -> "x"));
        assertEquals("bots", Spelers.stand(Toestand.arena("arena", "bots", "ninja"), x -> x.equals("bots") ? "bots" : "pvp", t -> "x"));
        assertEquals("pvp", Spelers.stand(Toestand.arena("kasteel", "kasteel", "boog"), x -> x.equals("bots") ? "bots" : "pvp", t -> "x"));
        assertEquals("avontuur", Spelers.stand(Toestand.spel("zelda", "1"), x -> "pvp", t -> "avontuur"));
    }

    @Test
    void ongeldigeToestand() {
        assertThrows(NullPointerException.class, () -> Toestand.arena("arena", "pvp", null));
        assertThrows(NullPointerException.class, () -> Toestand.kitruimte(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Toestand(Toestand.Plek.KITRUIMTE, "a", "b", "krijger"));
        assertTrue(Toestand.HUB.beheerd());
        assertFalse(Toestand.spel("zelda", "1").beheerd());
        assertFalse(Toestand.spel("zelda", "1").veilig());
    }
}
