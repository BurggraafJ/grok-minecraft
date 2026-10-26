package nl.burggraaf.mc.kitpvp;

import org.bukkit.entity.Player;

/** Een modus met kits: je komt in een kitruimte, kiest een kit en springt een arena in. */
abstract class KitModus implements Modus {

    protected final KitPvP plugin;

    KitModus(KitPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public Status status() {
        return Status.OPEN;
    }

    @Override
    public void join(Player p) {
        plugin.naarKitruimte(p, kitruimtes().get(0), true);
    }
}
