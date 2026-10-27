package nl.burggraaf.mc.kitpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Een voorwerp dat je in Story of Zelda kunt krijgen (flesje; later bommen, klauwhaak, vuurstaf, lichtpijlen: zie
 * ZELDA.md). Je hebt het als je de {@link #vlag} hebt; {@link ZeldaSpel#uitrusten} zet het dan in {@link #vak}.
 * Registreer een nieuw voorwerp in {@link Zelda} ({@code spel.voorwerpen.add(...)}). Het item krijgt vanzelf de
 * Zelda-sleutel met {@link #id}, zodat {@link #gebruik} (rechtsklik) en {@link #raak} (een projectiel ervan raakt
 * iets) bij dit voorwerp aankomen.
 */
interface ZeldaVoorwerp {

    /** Kort en vast (ook de sleutel op het item). */
    String id();

    /** Je hebt het als je deze vlag hebt. */
    String vlag();

    /** Vak in de hotbar: 2 … 6 (0 zwaard, 1 boog, 7 hub, 8 kaart). */
    int vak();

    /** Het item zoals het nu in je hand ligt (bv. het aantal volle flesjes); null = niets. */
    ItemStack item(ZeldaSpel spel, Player p, ZeldaVoortgang.Stand s);

    /** Rechtsklik met het item (het event is al geannuleerd). */
    default void gebruik(ZeldaSpel spel, Player p, PlayerInteractEvent e) {
    }

    /** Een projectiel dat met dit item is afgeschoten raakt iets (zet de sleutel zelf op het projectiel). */
    default void raak(ZeldaSpel spel, Player p, ProjectileHitEvent e) {
    }
}
