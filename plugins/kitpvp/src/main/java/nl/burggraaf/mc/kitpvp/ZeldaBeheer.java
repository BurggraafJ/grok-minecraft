package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * Beheermodus voor Story of Zelda (alleen OP's/admins, {@code kitpvp.beheer}): vliegen, onkwetsbaar, vijanden negeren je,
 * kamers, rustplaatsen, vondsten en verhaal reageren niet op je, en je voortgang staat na {@code uit} weer precies zoals
 * hij was. Met {@code /zelda ga <plek>} ga je overal heen (eiland, deel, rustplaats, kamer, persoon).
 *
 * <p>{@code /zelda} of {@code /zelda beheer} = aan/uit · {@code /zelda beheer aan|uit} · {@code /zelda ga <plek>} ·
 * {@code /zelda plekken}.
 */
final class ZeldaBeheer {

    private final ZeldaSpel spel;
    /** Wie in beheer is, met zijn voortgang van daarvoor (die komt bij uit terug). */
    private final Map<UUID, ZeldaVoortgang.Stand> kopie = new HashMap<>();

    ZeldaBeheer(ZeldaSpel spel) {
        this.spel = spel;
    }

    boolean is(UUID id) {
        return kopie.containsKey(id);
    }

    boolean is(Entity e) {
        return e instanceof Player && kopie.containsKey(e.getUniqueId());
    }

    int aantal() {
        return kopie.size();
    }

    private static void regel(Player p, String tekst) {
        p.sendActionBar(Component.text(tekst, NamedTextColor.GRAY));
    }

    /** Beheer aan: zo nodig eerst Zelda in (je laatste rustplaats). */
    boolean aan(Player p) {
        if (is(p.getUniqueId())) return true;
        if (!spel.speelt(p)) spel.plugin.kiesModus(p, spel.modus);
        if (!spel.speelt(p)) {
            regel(p, "Zelda is nu niet te openen");
            return false;
        }
        kopie.put(p.getUniqueId(), spel.voortgang.van(p.getUniqueId()).kopie());
        vlieg(p, true);
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 48, 48, 48))
            if (e instanceof Mob m && p.equals(m.getTarget())) m.setTarget(null);
        return true;
    }

    /** Beheer uit: voortgang terug zoals hij was; {@code inSpel} = blijf in Zelda (terug naar je rustplaats). */
    void uit(Player p, boolean inSpel) {
        ZeldaVoortgang.Stand oud = kopie.remove(p.getUniqueId());
        if (oud == null) return;
        spel.voortgang.zet(p.getUniqueId(), oud);
        spel.bewaar();
        vlieg(p, false);
        if (inSpel && spel.speelt(p)) {
            ZeldaSpel.Sessie ss = spel.sessie(p);
            ss.h = ss.rh;
            spel.uitrusten(p, oud);
            p.teleport(ss.rustplaats().plek);
        }
    }

    private static void vlieg(Player p, boolean aan) {
        p.setFallDistance(0);
        p.setInvulnerable(aan);
        p.setAllowFlight(aan);
        p.setFlying(aan);
    }

    /** Elke seconde (uit ZeldaSpel): vliegen blijft aan, ook als iets anders het uitzette. */
    void houVast(Player p) {
        p.setFallDistance(0);
        if (!p.getAllowFlight()) p.setAllowFlight(true);
        if (!p.isInvulnerable()) p.setInvulnerable(true);
    }

    // ── plekken ─────────────────────────────────────────────────────────────────────────────

    /** Alle plekken om heen te gaan, op naam (kleine letters): eilanden, delen, rustplaatsen, kamers, personen. */
    Map<String, Location> plekken() {
        Map<String, Location> m = new LinkedHashMap<>();
        for (ZeldaEiland e : spel.eilanden) {
            zet(m, e.id(), e.aanlegPlek());
            for (ZeldaHoofdstuk h : e.delen()) {
                if (h.w == null) continue;
                if (!h.rust.isEmpty()) zet(m, h.id(), h.rust.get(0).plek);
                for (ZeldaHoofdstuk.Rust r : h.rust) zet(m, r.id, r.plek);
                for (ZeldaHoofdstuk.Kamer k : h.kamers) zet(m, k.id, k.start.midden(h.w));
                for (ZeldaHoofdstuk.Persoon pe : h.personen) zet(m, pe.naam, pe.plek);
            }
        }
        if (spel.zee.w != null && !spel.zee.dek.isEmpty()) zet(m, "zee", spel.zee.dek.get(0));
        return m;
    }

    private static void zet(Map<String, Location> m, String naam, Location l) {
        if (naam == null || l == null) return;
        m.putIfAbsent(naam.toLowerCase(Locale.ROOT), l);
    }

    /** De naam die bij {@code in} hoort: precies, anders het enige begin dat past; null = onbekend of niet eenduidig. */
    static String kies(Collection<String> namen, String in) {
        if (in == null || in.isEmpty()) return null;
        String z = in.toLowerCase(Locale.ROOT);
        if (namen.contains(z)) return z;
        String gevonden = null;
        for (String n : namen)
            if (n.startsWith(z)) {
                if (gevonden != null) return null;
                gevonden = n;
            }
        return gevonden;
    }

    /** Namen die met {@code begin} beginnen (tab). */
    static List<String> passend(Collection<String> namen, String begin) {
        String z = begin == null ? "" : begin.toLowerCase(Locale.ROOT);
        List<String> l = new ArrayList<>();
        for (String n : namen) if (n.startsWith(z)) l.add(n);
        return l;
    }

    void ga(Player p, String naam) {
        Map<String, Location> m = plekken();
        String k = kies(m.keySet(), naam);
        if (k == null) {
            regel(p, "Onbekende plek · /zelda plekken");
            return;
        }
        if (!aan(p)) return;
        ZeldaSpel.Sessie ss = spel.sessie(p);
        Location l = m.get(k).clone();
        if (ss != null && ss.vaart != null) ss.vaart = null;
        p.setFallDistance(0);
        p.teleport(l);
        if (ss != null) {
            ZeldaHoofdstuk hier = spel.deelOveral(l);
            if (hier != null) ss.h = hier;
        }
        regel(p, "Beheer · " + k);
    }

    // ── commando ────────────────────────────────────────────────────────────────────────────

    void commando(Player p, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "beheer";
        switch (sub) {
            case "beheer" -> {
                String w = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : (is(p.getUniqueId()) ? "uit" : "aan");
                if (w.equals("uit")) {
                    boolean was = is(p.getUniqueId());
                    uit(p, true);
                    regel(p, was ? "Beheer uit · voortgang zoals hij was" : "Beheer staat al uit");
                } else if (aan(p)) regel(p, "Beheer aan · vliegen · /zelda ga <plek>");
            }
            case "ga" -> {
                if (args.length < 2) regel(p, "/zelda ga <plek> · /zelda plekken");
                else ga(p, args[1]);
            }
            case "plekken" -> p.sendMessage(Component.text("Plekken: " + String.join(", ", plekken().keySet()), NamedTextColor.GRAY));
            default -> regel(p, "/zelda beheer [aan|uit] · /zelda ga <plek> · /zelda plekken");
        }
    }

    List<String> tab(String[] args) {
        if (args.length <= 1) return passend(List.of("beheer", "ga", "plekken"), args.length == 1 ? args[0] : "");
        if (args.length == 2 && args[0].equalsIgnoreCase("beheer")) return passend(List.of("aan", "uit"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("ga")) return passend(plekken().keySet(), args[1]);
        return List.of();
    }
}
