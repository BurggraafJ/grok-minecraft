package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;

/**
 * Een arena waar je met een kit in springt. {@code stand} is wat de plugin Huisdieren ziet ({@code pvp}: spelers tegen
 * elkaar, wolf vecht mee; {@code bots}: oefenarena, alleen tegen bots). De spawns vult het deel dat de arena bouwt.
 */
final class Arena {

    final String id, modus, naam, stand;
    final List<Location> spawns = new ArrayList<>();

    Arena(String id, String modus, String naam, String stand) {
        this.id = id;
        this.modus = modus;
        this.naam = naam;
        this.stand = stand;
    }

    boolean bots() {
        return stand.equals("bots");
    }
}
