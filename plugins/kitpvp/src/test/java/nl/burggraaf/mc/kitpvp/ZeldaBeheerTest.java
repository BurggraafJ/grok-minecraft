package nl.burggraaf.mc.kitpvp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ZeldaBeheerTest {

    private static final List<String> NAMEN = List.of("lindewoud", "h1", "thuis", "eik", "ruine", "open-plek", "veen", "kees", "hanne", "haven", "heksenkring");

    @Test
    void kiesPrecisOfUniekBegin() {
        assertEquals("eik", ZeldaBeheer.kies(NAMEN, "EIK"), "precies, hoofdletters maken niet uit");
        assertEquals("lindewoud", ZeldaBeheer.kies(NAMEN, "lin"), "uniek begin");
        assertNull(ZeldaBeheer.kies(NAMEN, "h"), "h past bij meer namen (h1, hanne, haven, heksenkring), maar h zelf niet");
        assertEquals("h1", ZeldaBeheer.kies(NAMEN, "h1"));
        assertNull(ZeldaBeheer.kies(NAMEN, "ha"), "hanne en haven");
        assertEquals("haven", ZeldaBeheer.kies(NAMEN, "hav"));
        assertNull(ZeldaBeheer.kies(NAMEN, "zilverstrand"));
        assertNull(ZeldaBeheer.kies(NAMEN, ""));
    }

    @Test
    void tabGeeftPassendeNamen() {
        assertEquals(List.of("h1", "hanne", "haven", "heksenkring"), ZeldaBeheer.passend(NAMEN, "H"));
        assertEquals(NAMEN, ZeldaBeheer.passend(NAMEN, ""));
    }
}
