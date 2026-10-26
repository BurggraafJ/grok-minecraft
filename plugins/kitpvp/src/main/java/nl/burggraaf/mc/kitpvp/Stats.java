package nl.burggraaf.mc.kitpvp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;

/** stats.yml: per speler (uuid) naam, kills, doden, beste (reeks) en bots. Ongewijzigd formaat. */
final class Stats {

    private final File bestand;
    private final YamlConfiguration yml;
    private final Logger log;

    Stats(File bestand, Logger log) {
        this.bestand = bestand;
        this.log = log;
        this.yml = YamlConfiguration.loadConfiguration(bestand);
    }

    void tel(UUID id, String naam, String wat) {
        yml.set(id + ".naam", naam);
        yml.set(id + "." + wat, yml.getInt(id + "." + wat, 0) + 1);
    }

    int get(UUID id, String wat) {
        return yml.getInt(id + "." + wat, 0);
    }

    /** Nieuwe reeks: onthoud hem als het de beste ooit is. */
    void reeks(UUID id, int r) {
        if (r > get(id, "beste")) yml.set(id + ".beste", r);
    }

    int spelers() {
        return yml.getKeys(false).size();
    }

    void bewaar() {
        try {
            yml.save(bestand);
        } catch (IOException e) {
            log.warning("stats.yml: " + e.getMessage());
        }
    }

    static String kd(int k, int d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d == 0 ? (double) k : (double) k / d);
    }

    /** Tekst voor het top-10 bord. */
    Component ranglijst() {
        List<String> ids = new ArrayList<>(yml.getKeys(false));
        ids.sort(Comparator.comparingInt((String k) -> yml.getInt(k + ".kills", 0)).reversed());
        Component t = Component.text("🏆 Top killers", NamedTextColor.GOLD);
        String[] plek = {"🥇", "🥈", "🥉", "4.", "5.", "6.", "7.", "8.", "9.", "10."};
        int n = 0;
        for (String id : ids) {
            if (n >= 10) break;
            int k = yml.getInt(id + ".kills", 0);
            if (k == 0) continue;
            int d = yml.getInt(id + ".doden", 0);
            t = t.append(Component.newline()).append(Component.text(plek[n] + " " + yml.getString(id + ".naam", "?") + "  ", n < 3 ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                    .append(Component.text(k + " kills · K/D " + kd(k, d), NamedTextColor.GRAY));
            n++;
        }
        if (n == 0) t = t.append(Component.newline()).append(Component.text("Nog geen kills. Wie wordt de eerste?", NamedTextColor.GRAY));
        return t;
    }
}
