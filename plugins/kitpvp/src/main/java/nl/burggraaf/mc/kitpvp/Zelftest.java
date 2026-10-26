package nl.burggraaf.mc.kitpvp;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

/**
 * {@code kitpvp zelftest} (ook {@code arena zelftest}): register, hub en poorten (ook de dichte Zelda-poort), elke
 * kitruimte (standaards, deuren, binnenkomst), alle spawns, ranglijsten en of het bouwen klaar is.
 */
final class Zelftest {

    private Zelftest() {
    }

    static void draai(KitPvP plugin, List<String> fouten, List<String> info) {
        ModusRegister modi = plugin.modi;
        // register
        List<Modus> alle = modi.alle();
        List<String> ids = alle.stream().map(Modus::id).toList();
        if (!ids.equals(List.of("zelda", "arena", "kasteel"))) fouten.add("modi: " + ids + " (verwacht [zelda, arena, kasteel])");
        List<String> namen = alle.stream().map(Modus::naam).toList();
        if (!namen.equals(List.of("Story of Zelda", "PvP Arena", "PvP Castle"))) fouten.add("namen: " + namen);
        Set<String> plekken = new HashSet<>();
        for (Modus m : alle) {
            if (!plekken.add(m.poort().plek())) fouten.add("dubbele poortplek " + m.poort().plek());
            if (m.status() == Modus.Status.OPEN && m instanceof KitModus && m.kitruimtes().isEmpty()) fouten.add(m.id() + ": open zonder kitruimte");
            m.zelftest(fouten, info);
        }
        Modus zelda = modi.get("zelda");
        if (zelda == null || zelda.status() != Modus.Status.BINNENKORT) fouten.add("zelda: hoort nu nog BINNENKORT te zijn");
        // wereld
        if (plugin.w == null) {
            fouten.add("wereld nog niet geladen");
            return;
        }
        Bouwer b = plugin.bouwer();
        if (b != null && b.bezig()) fouten.add("bouwen nog bezig (" + b.inRij() + " stappen)");
        plugin.hub.zelftest(fouten, info);
        for (Kitruimte r : modi.kitruimtes()) r.zelftest(plugin, fouten);
        for (Arena a : modi.arenas()) {
            if (a.spawns.isEmpty()) fouten.add("arena " + a.id + ": geen spawns");
            for (Location l : a.spawns) {
                if (!l.getBlock().isPassable() || !l.clone().add(0, 1, 0).getBlock().isPassable())
                    fouten.add("arena " + a.id + ": spawn zit vast op " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
                else if (!l.clone().add(0, -1, 0).getBlock().getType().isSolid())
                    fouten.add("arena " + a.id + ": spawn zonder vloer op " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
            }
            if (modi.kitruimteVoor(a.id) == null) fouten.add("arena " + a.id + ": geen kitruimte om na een ronde naar terug te gaan");
        }
        long goed = plugin.ranglijsten().stream().filter(TextDisplay::isValid).count();
        if (goed != 1 + modi.kitruimtes().size()) fouten.add("ranglijsten: " + goed + " (verwacht " + (1 + modi.kitruimtes().size()) + ")");
        StringBuilder sp = new StringBuilder();
        for (Arena a : modi.arenas()) sp.append(a.id).append('=').append(a.spawns.size()).append(' ');
        info.add("grond=" + plugin.g + " kits=" + Kits.KITS.length + " kitruimtes=" + modi.kitruimtes().size() + " spawns: " + sp.toString().trim()
                + " stats=" + plugin.stats().spelers() + " in-arena=" + plugin.spelers.aantalInArena() + " bots=" + plugin.bots().alle(plugin.w).size());
    }
}
