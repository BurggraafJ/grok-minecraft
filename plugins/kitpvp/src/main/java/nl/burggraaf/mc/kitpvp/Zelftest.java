package nl.burggraaf.mc.kitpvp;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/**
 * {@code kitpvp zelftest} (ook {@code arena zelftest}): register, hub en poorten (de Zelda-poort is open), elke
 * kitruimte (standaards, deuren, binnenkomst), alle spawns, ranglijsten, of het bouwen klaar is, en via de
 * zelftest-hook van elke modus hun eigen controles (Zelda: deel, rustplaatsen, kamers, puzzels, opslag).
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
        if (zelda == null || zelda.status() != Modus.Status.OPEN) fouten.add("zelda: hoort OPEN te zijn");
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

    /**
     * {@code kitpvp test <speler>}: loop met een echte (test)speler de hele weg: hub, door de Zelda-poort naar binnen en
     * met /arena terug, door de poort van PvP Arena, zijdeur naar de oefenarena, kit kiezen, /arena terug, PvP Castle via het menu, kit, gevecht-tag
     * houdt /arena tegen, terug naar de hub. Alles gaat via dezelfde methodes als poort, deur, standaard en commando.
     */
    static void loop(KitPvP pl, Player p, List<String> fouten, List<String> stappen) {
        UUID id = p.getUniqueId();
        Spelers s = pl.spelers;
        pl.naarHub(p);
        stappen.add("hub");
        moet(fouten, s.van(id).plek() == Toestand.Plek.HUB, "na naarHub niet in de hub");
        moet(fouten, pl.hub.binnen(p.getLocation()), "staat niet in de hub: " + plek(p));
        moet(fouten, pl.stand(p).equals("wacht"), "stand in de hub is " + pl.stand(p));
        moet(fouten, p.getInventory().getItem(4) != null && p.getInventory().getItem(4).getType() == Material.COMPASS, "geen kompas in vak 5");
        Modus zelda = pl.modi.get("zelda"), arena = pl.modi.get("arena"), kasteel = pl.modi.get("kasteel");
        Hub.Raak rz = pl.hub.raak(pl.hub.poortPunt(zelda));
        moet(fouten, rz != null && !rz.dicht() && rz.modus() == zelda, "Zelda-poort stuurt niet naar Zelda");
        if (rz != null && !rz.dicht()) pl.kiesModus(p, rz.modus());
        moet(fouten, s.van(id).plek() == Toestand.Plek.SPEL && "zelda".equals(s.van(id).modus()), "door de Zelda-poort niet in Zelda: " + s.van(id));
        moet(fouten, pl.stand(p).equals("bots"), "stand in Zelda is " + pl.stand(p));
        moet(fouten, p.getLocation().getX() >= Zelda.GEBIED[0] && p.getLocation().getX() <= Zelda.GEBIED[2], "niet in het Zelda-gebied: " + plek(p));
        stappen.add("zelda");
        moet(fouten, isHubItem(pl, p.getInventory().getItem(Menus.HUB_VAK)), "in Zelda geen «Terug naar de hub» in vak 8");
        commando(pl, p, "arena");
        moet(fouten, s.van(id).plek() == Toestand.Plek.HUB && pl.hub.binnen(p.getLocation()), "/arena bracht je niet uit Zelda naar de hub: " + s.van(id));
        moet(fouten, p.getInventory().getItem(4) != null && p.getInventory().getItem(4).getType() == Material.COMPASS, "na Zelda geen kompas in vak 5");
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        moet(fouten, max != null && max.getValue() == 20, "na Zelda nog Zelda-harten: " + (max == null ? "?" : max.getValue()));
        stappen.add("/arena");
        Hub.Raak ra = pl.hub.raak(pl.hub.poortPunt(arena));
        moet(fouten, ra != null && !ra.dicht() && ra.modus() == arena, "poort PvP Arena stuurt niet door");
        if (ra != null) pl.kiesModus(p, ra.modus());
        Kitruimte kr = pl.modi.kitruimte("arena");
        moet(fouten, s.van(id).equals(Toestand.kitruimte("arena", "arena")), "na de poort niet in de kitruimte: " + s.van(id));
        moet(fouten, kr.binnen(p.getLocation()), "staat niet in de kitruimte: " + plek(p));
        stappen.add("kitruimte arena");
        String deur = kr.deurBij(kr.zijdeurPunt());
        moet(fouten, "oefen".equals(deur), "zijdeur gaat naar " + deur);
        if (deur != null && pl.modi.kitruimte(deur) != null) pl.naarKitruimte(p, pl.modi.kitruimte(deur), false);
        moet(fouten, s.van(id).equals(Toestand.kitruimte("arena", "oefen")), "niet in de oefen-kitruimte: " + s.van(id));
        stappen.add("oefen");
        pl.kies(p, "boog", pl.modi.arena("bots"));
        moet(fouten, s.van(id).equals(Toestand.arena("arena", "bots", "boog")), "kit boog: toestand " + s.van(id));
        moet(fouten, pl.stand(p).equals("bots"), "stand in de oefenarena is " + pl.stand(p));
        moet(fouten, p.getInventory().contains(Material.BOW), "geen boog gekregen");
        moet(fouten, ArenaModus.inOefenarena(p.getLocation(), pl.g), "niet in de oefenarena: " + plek(p));
        moet(fouten, isHubItem(pl, p.getInventory().getItem(Menus.HUB_VAK)), "in de oefenarena geen «Terug naar de hub» in vak 8");
        stappen.add("oefenarena");
        boolean gedaan = commando(pl, p, "arena");
        moet(fouten, s.van(id).plek() == Toestand.Plek.HUB && pl.hub.binnen(p.getLocation()),
                "/arena bracht je niet naar de hub (" + gedaan + ", " + s.van(id) + ", " + plek(p) + ", gevecht " + pl.gevecht().in(id) + ")");
        stappen.add("/arena");
        pl.kiesModus(p, kasteel);
        moet(fouten, s.van(id).equals(Toestand.kitruimte("kasteel", "kasteel")), "menu PvP Castle: toestand " + s.van(id));
        pl.kies(p, "krijger", pl.modi.arena("kasteel"));
        moet(fouten, s.van(id).equals(Toestand.arena("kasteel", "kasteel", "krijger")), "kit krijger: toestand " + s.van(id));
        moet(fouten, pl.stand(p).equals("pvp"), "stand in het kasteel is " + pl.stand(p));
        double best = 1e9;
        for (Location l : pl.modi.arena("kasteel").spawns) best = Math.min(best, l.distanceSquared(p.getLocation()));
        moet(fouten, best < 1, "niet op een kasteelspawn: " + plek(p));
        stappen.add("kasteel");
        pl.gevecht().tag(p);
        commando(pl, p, "arena");
        moet(fouten, s.van(id).inArena(), "/arena werkte tijdens een gevecht");
        commando(pl, p, "modus", "arena");
        moet(fouten, s.van(id).inArena(), "/modus werkte tijdens een gevecht");
        moet(fouten, isHubItem(pl, p.getInventory().getItem(Menus.HUB_VAK)), "in het kasteel geen «Terug naar de hub» in vak 8");
        pl.hubKnop(p);
        moet(fouten, s.van(id).inArena(), "«Terug naar de hub» werkte tijdens een gevecht");
        pl.gevecht().untag(p);
        stappen.add("gevecht-tag");
        pl.hubKnop(p);
        moet(fouten, s.van(id).plek() == Toestand.Plek.HUB && pl.hub.binnen(p.getLocation()), "«Terug naar de hub» bracht je niet naar de hub: " + s.van(id));
        stappen.add("hub-knop");
        pl.kiesModus(p, kasteel);
        pl.kies(p, "krijger", pl.modi.arena("kasteel"));
        gedaan = commando(pl, p, "arena");
        moet(fouten, s.van(id).plek() == Toestand.Plek.HUB, "na het gevecht niet terug in de hub (" + gedaan + ", " + s.van(id) + ")");
        stappen.add("hub");
    }

    /**
     * {@code kitpvp test <speler> dood} (alleen voor tests: telt een dood in stats.yml): in het kasteel doodgaan, dan
     * moet je na de respawn in de kitruimte van het kasteel staan. Het resultaat komt een paar ticks later.
     */
    static void dood(KitPvP pl, Player p, org.bukkit.command.CommandSender aan) {
        List<String> fouten = new java.util.ArrayList<>();
        UUID id = p.getUniqueId();
        pl.kiesModus(p, pl.modi.get("kasteel"));
        pl.kies(p, "tank", pl.modi.arena("kasteel"));
        moet(fouten, pl.spelers.inArena(id), "niet in het kasteel");
        p.setHealth(0);
        moet(fouten, pl.spelers.van(id).equals(Toestand.kitruimte("kasteel", "kasteel")), "na doodgaan: " + pl.spelers.van(id));
        moet(fouten, pl.stand(p).equals("wacht"), "stand na doodgaan: " + pl.stand(p));
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isDead()) p.spigot().respawn();
            org.bukkit.Bukkit.getScheduler().runTaskLater(pl, () -> {
                Kitruimte r = pl.modi.kitruimte("kasteel");
                moet(fouten, !p.isDead(), "nog dood");
                moet(fouten, r.binnen(p.getLocation()), "na respawn niet in de kitruimte: " + plek(p));
                moet(fouten, p.getInventory().getItem(4) != null && p.getInventory().getItem(4).getType() == Material.NETHER_STAR, "geen ster na respawn");
                String uit = "kitpvp test dood: " + (fouten.isEmpty() ? "OK" : "FOUT (" + fouten.size() + ") " + String.join("; ", fouten)) + " · kasteel → dood → respawn in de kitruimte";
                aan.sendMessage(uit);
                pl.getLogger().info(uit);                    // het antwoord komt later; RCON is dan al weg
                commando(pl, p, "arena");
            }, 3L);
        }, 2L);
    }

    /** Zoals de speler het commando typt (de uitvoerder direct; performCommand loopt hier een tick later). */
    private static boolean commando(KitPvP pl, Player p, String naam, String... args) {
        return pl.onCommand(p, pl.getCommand(naam), naam, args);
    }

    private static void moet(List<String> fouten, boolean ok, String fout) {
        if (!ok) fouten.add(fout);
    }

    private static String plek(Player p) {
        Location l = p.getLocation();
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private static boolean isHubItem(KitPvP pl, org.bukkit.inventory.ItemStack it) {
        return it != null && it.hasItemMeta() && "hub".equals(it.getItemMeta().getPersistentDataContainer().get(pl.itemKey, org.bukkit.persistence.PersistentDataType.STRING));
    }
}
