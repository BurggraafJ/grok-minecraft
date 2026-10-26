package nl.burggraaf.mc.kitpvp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ModusRegisterTest {

    private static final Kitruimte.Palet P = new Kitruimte.Palet(Material.STONE, Material.STONE, Material.STONE, Material.STONE,
            Material.SEA_LANTERN, Material.RED_CARPET, Material.STONE);

    /** Eenvoudige modus voor de test. */
    private record M(String id, String plek, Modus.Status status, List<Arena> arenas, List<Kitruimte> kitruimtes) implements Modus {
        public String naam() { return id.toUpperCase(); }
        public TextColor kleur() { return NamedTextColor.WHITE; }
        public Material icoon() { return Material.STONE; }
        public PoortStijl poort() { return new PoortStijl(plek, Material.STONE, Material.STONE, Material.SEA_LANTERN, Material.STONE, null, null, Color.WHITE); }
        public void join(Player p) { }
    }

    private static Kitruimte ruimte(String id, String modus, String arena) {
        return new Kitruimte(id, modus, arena, id, 0, 0, BlockFace.SOUTH, 5, P, null, null);
    }

    @Test
    void volgordeEnOpzoeken() {
        ModusRegister r = new ModusRegister();
        Arena pvp = new Arena("pvp", "arena", "PvP Arena", "pvp"), bots = new Arena("bots", "arena", "Oefenarena", "bots");
        r.registreer(new M("zelda", "west", Modus.Status.BINNENKORT, List.of(), List.of()));
        r.registreer(new M("arena", "zuid", Modus.Status.OPEN, List.of(pvp, bots), List.of(ruimte("arena", "arena", "pvp"), ruimte("oefen", "arena", "bots"))));
        assertEquals(List.of("zelda", "arena"), r.alle().stream().map(Modus::id).toList());
        assertEquals("arena", r.opPlek("zuid").id());
        assertNull(r.opPlek("noord"));
        assertSame(pvp, r.arena("pvp"));
        assertEquals("oefen", r.kitruimteVoor("bots").id);
        assertEquals("arena", r.kitruimteVoor("pvp").id);
        assertEquals(List.of(pvp, bots), r.arenasVan("arena"));
        assertNull(r.get(null));
        assertNull(r.arena("nope"));
    }

    @Test
    void foutenWordenGeweigerd() {
        ModusRegister r = new ModusRegister();
        r.registreer(new M("a", "west", Modus.Status.BINNENKORT, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> r.registreer(new M("a", "zuid", Modus.Status.BINNENKORT, List.of(), List.of())), "dubbel id");
        assertThrows(IllegalArgumentException.class, () -> r.registreer(new M("b", "west", Modus.Status.BINNENKORT, List.of(), List.of())), "dubbele plek");
        assertThrows(IllegalArgumentException.class, () -> r.registreer(new M("c", "oost", Modus.Status.BINNENKORT, List.of(), List.of())), "onbekende plek");
        assertThrows(IllegalArgumentException.class, () -> r.registreer(new M("Hoofd Letters", "zuid", Modus.Status.BINNENKORT, List.of(), List.of())), "id");
        Arena x = new Arena("x", "d", "X", "pvp");
        assertThrows(IllegalArgumentException.class, () -> r.registreer(new M("d", "zuid", Modus.Status.OPEN, List.of(x), List.of(ruimte("r", "d", "y")))),
                "kitruimte naar arena van een andere modus");
        assertThrows(IllegalArgumentException.class, () -> r.registreer(new M("e", "zuid", Modus.Status.OPEN, List.of(new Arena("y", "anders", "Y", "pvp")), List.of())),
                "arena van een andere modus");
        // een mislukte registratie laat niets achter
        assertEquals(1, r.alle().size());
        assertNull(r.arena("x"));
        r.registreer(new M("d", "zuid", Modus.Status.OPEN, List.of(x), List.of(ruimte("r", "d", "x"))));
        assertThrows(IllegalArgumentException.class, () -> r.registreer(new M("f", "noord", Modus.Status.OPEN, List.of(new Arena("x", "f", "X", "pvp")), List.of())),
                "arena-id bestaat al");
    }

    @Test
    void menuVakken() {
        assertArrayEquals(new int[]{11, 13, 15}, Menus.midden(3, 1));
        assertArrayEquals(new int[]{21, 23}, Menus.midden(2, 2));
        assertArrayEquals(new int[]{13}, Menus.midden(1, 1));
        assertArrayEquals(new int[]{11, 12, 13, 14, 15}, Menus.midden(5, 1));
    }

    @Test
    void hubPlekkenHorenBijHetRegister() {
        assertEquals(ModusRegister.PLEKKEN, Hub.SLOTS.keySet());
        for (Hub.Slot s : Hub.SLOTS.values()) {
            // naar binnen gericht: van de muur naar het midden
            assertEquals(Integer.signum(Hub.HX - s.ox()), s.ix());
            assertEquals(Integer.signum(Hub.HZ - s.oz()), s.iz());
        }
        assertTrue(Hub.SLOTS.get("west").groot());
    }
}
