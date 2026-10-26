package nl.burggraaf.mc.kitpvp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Gevecht-tag: 10 s na een treffer (gegeven of gekregen) kun je niet weg. De proxy (plugin netwerk) krijgt
 * {@code gevecht:<ms>} (hooguit ~1x per 1,5 s) en {@code gevecht:0} als het voorbij is, en weigert dan wisselen.
 */
final class Gevecht {

    static final long MS = 10_000L;

    private final Plugin plugin;
    private final Map<UUID, Long> tot = new HashMap<>();
    private final Map<UUID, Long> gemeld = new HashMap<>();

    Gevecht(Plugin plugin) {
        this.plugin = plugin;
    }

    boolean in(UUID id) {
        return tot.getOrDefault(id, 0L) > System.currentTimeMillis();
    }

    Long tot(UUID id) {
        return tot.get(id);
    }

    void tag(Player p) {
        long nu = System.currentTimeMillis();
        tot.put(p.getUniqueId(), nu + MS);
        if (nu - gemeld.getOrDefault(p.getUniqueId(), 0L) > 1500L) {
            gemeld.put(p.getUniqueId(), nu);
            meldProxy(p, "gevecht:" + MS);
        }
    }

    void untag(Player p) {
        if (tot.remove(p.getUniqueId()) != null) meldProxy(p, "gevecht:0");
        gemeld.remove(p.getUniqueId());
    }

    /** Uitgelogd: alleen vergeten (de proxy ruimt zelf op). */
    void vergeet(UUID id) {
        tot.remove(id);
        gemeld.remove(id);
    }

    /** In gevecht? Dan één actiebalkregel en true. */
    boolean blokkeer(Player p) {
        if (!in(p.getUniqueId())) return false;
        long over = tot.get(p.getUniqueId()) - System.currentTimeMillis();
        p.sendActionBar(Component.text("⚔ In gevecht · nog " + ((over + 999) / 1000) + " s", NamedTextColor.RED));
        return true;
    }

    private void meldProxy(Player p, String wat) {
        try {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(buf);
            out.writeUTF("actie");
            out.writeUTF(p.getUniqueId().toString());
            out.writeUTF(wat);
            p.sendPluginMessage(plugin, "netwerk:main", buf.toByteArray());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().fine("netwerk: " + e.getMessage());
        }
    }
}
