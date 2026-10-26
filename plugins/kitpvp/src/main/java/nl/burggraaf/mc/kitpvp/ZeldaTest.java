package nl.burggraaf.mc.kitpvp;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * {@code kitpvp zelda test <speler> [alles]}: loopt met een echte (test)speler door Story of Zelda, via dezelfde
 * code als in het spel: door de poort in de hub naar binnen, dan alle onderdelen van het hoofdstuk in de volgorde
 * van het pad (rustplaats, verhaal, kamer: deuren dicht, golven verslaan, deuren open, vondst, uitdaging op tijd,
 * puzzel). Zonder {@code alles} stopt hij bij de eerste rustplaats na de eerste kamer. Wist eerst de voortgang van die
 * speler (alleen voor tests). Het resultaat komt na een paar seconden (ook in de serverlog).
 *
 * <p>{@code kitpvp zelda verder <speler>}: door de poort naar binnen, en kijk of je op je verste rustplaats staat
 * (na uitloggen en weer inloggen).
 */
final class ZeldaTest {

    private record Stap(String naam, Runnable doe, int wacht, BooleanSupplier klaar, int max) {}

    private final KitPvP pl;
    private final Zelda z;
    private final Player p;
    private final CommandSender aan;
    private final UUID id;
    private final List<String> fouten = new ArrayList<>(), gedaan = new ArrayList<>();
    private final Deque<Stap> rij = new ArrayDeque<>();
    private BukkitTask taak;

    ZeldaTest(KitPvP pl, Zelda z, Player p, CommandSender aan) {
        this.pl = pl;
        this.z = z;
        this.p = p;
        this.aan = aan;
        this.id = p.getUniqueId();
    }

    private void stap(String naam, Runnable doe, int wacht, BooleanSupplier klaar, int max) {
        rij.add(new Stap(naam, doe, wacht, klaar, max));
    }

    private void moet(boolean ok, String fout) {
        if (!ok) fouten.add(fout);
    }

    private static String xyz(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private ZeldaVoortgang.Stand stand() {
        return z.spel.voortgang.van(id);
    }

    /** Door de poort in de hub, zoals een speler erdoor loopt. */
    private void doorDePoort() {
        pl.naarHub(p);
        Hub.Raak r = pl.hub.raak(pl.hub.poortPunt(z));
        moet(r != null && !r.dicht() && r.modus() == z, "de Zelda-poort stuurt niet naar Zelda");
        if (r != null && !r.dicht()) pl.kiesModus(p, r.modus());
        moet(pl.spelers.van(id).plek() == Toestand.Plek.SPEL && "zelda".equals(pl.spelers.van(id).modus()), "na de poort niet in Zelda: " + pl.spelers.van(id));
        moet(z.spel.speelt(p), "geen Zelda-sessie");
        moet(pl.stand(p).equals("bots"), "stand in Zelda is " + pl.stand(p));
    }

    void route(boolean alles) {
        z.spel.voortgang.wis(id);
        ZeldaHoofdstuk h = z.HOOFDSTUKKEN.get(0);
        stap("poort", () -> {
            doorDePoort();
            Location r0 = h.rust.get(0).plek;
            moet(p.getLocation().distanceSquared(r0) < 4, "niet bij het begin: " + xyz(p.getLocation()) + " (verwacht " + xyz(r0) + ")");
            moet(p.getInventory().getItem(0) != null && p.getInventory().getItem(0).getType() == Material.WOODEN_SWORD, "geen houten zwaard");
            moet(p.getInventory().getItem(8) != null && p.getInventory().getItem(8).getType() == Material.MAP, "geen kaart");
        }, 2, () -> true, 1);
        boolean kamerGehad = false;
        for (Object o : h.volgorde) {
            if (o instanceof ZeldaHoofdstuk.Rust r) {
                if (r.index == 0) continue;
                stap("rust " + r.id, () -> p.teleport(r.plek), 8, () -> stand().rust(h.id()) >= r.index && z.spel.sessie(p) != null && z.spel.sessie(p).rust == r.index, 40);
                if (!alles && kamerGehad) break;
            } else if (o instanceof ZeldaHoofdstuk.Verhaal v) {
                stap("verhaal " + v.id, () -> p.teleport(v.zone.midden(h.w).add(0, 1, 0)), 8,
                        () -> stand().heeft("v:" + v.id) || (v.vereist != null && !stand().heeft(v.vereist)), 40);
            } else if (o instanceof ZeldaHoofdstuk.Vondst v) {
                stap("vondst " + v.vlag, () -> p.teleport(v.zone.midden(h.w).add(0, 1, 0)), 8, () -> stand().heeft(v.vlag), 40);
            } else if (o instanceof ZeldaHoofdstuk.Kamer k) {
                kamerGehad = true;
                stap("kamer " + k.id + " dicht", () -> p.teleport(k.start.midden(h.w).add(0, 1, 0)), 10, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG, 40);
                stap("kamer " + k.id + " verslaan", () -> {
                    moet(!k.in.isOpen && !k.uit.isOpen, "kamer " + k.id + ": deuren niet dicht tijdens het gevecht");
                    moet(!k.vijanden.isEmpty(), "kamer " + k.id + ": geen vijanden");
                    moet(z.spel.muziek.lus(p) == k.muziek, "kamer " + k.id + ": geen gevechtsmuziek");
                }, 5, () -> {
                    for (LivingEntity e : new ArrayList<>(k.vijanden)) if (e.isValid()) e.damage(10_000, p);
                    if (!k.binnen.in(p.getLocation())) p.teleport(k.start.midden(h.w).add(0, 1, 0));
                    return k.fase == ZeldaHoofdstuk.Fase.GEWONNEN;
                }, 900);
                stap("kamer " + k.id + " open", () -> {
                    moet(k.in.isOpen && k.uit.isOpen, "kamer " + k.id + ": deuren niet open na winnen");
                    Material m = h.w.getBlockAt(k.uit.blokken.get(0)[0], k.uit.blokken.get(0)[1], k.uit.blokken.get(0)[2]).getType();
                    moet(m == Material.AIR, "kamer " + k.id + ": uitgang is " + m);
                    moet(z.spel.muziek.lus(p) == null, "kamer " + k.id + ": muziek speelt nog");
                }, 2, () -> true, 1);
            } else if (o instanceof ZeldaHoofdstuk.Uitdaging u) {
                stap("uitdaging " + u.id + " start", () -> p.teleport(u.start.midden(h.w).add(0, 1, 0)), 8,
                        () -> u.loopt.containsKey(id) && u.poort.isOpen, 40);
                stap("uitdaging " + u.id + " finish", () -> p.teleport(u.finish.midden(h.w).add(0, 1, 0)), 8, () -> stand().beste(u.id) != null, 40);
            } else if (o instanceof ZeldaHoofdstuk.Reeks r) {
                stap("puzzel " + r.id, () -> {
                    p.teleport(r.gebied.midden(h.w).add(0, 1, 0));
                    for (int i = 0; i < r.dingen.size(); i++) z.spel.activeer(p, r, i);
                }, 4, () -> r.opgelost && r.deur.isOpen, 20);
            }
        }
        if (alles) stap("einde", () -> { }, 2, () -> stand().heeft(h.id()), 20);
        stap("opslag", () -> {
            ZeldaVoortgang.Stand s = stand();
            ZeldaVoortgang schijf = new ZeldaVoortgang(new File(pl.getDataFolder(), "zelda.yml"));
            moet(schijf.van(id).rust(h.id()) == s.rust(h.id()) && s.rust(h.id()) > 0,
                    "zelda.yml: rustplaats " + schijf.van(id).rust(h.id()) + " (in het geheugen " + s.rust(h.id()) + ")");
            moet(schijf.van(id).vlaggen.containsAll(s.vlaggen) || !z.spel.voortgang.isVuil(), "zelda.yml: vlaggen niet bewaard");
        }, 2, () -> true, 1);
        start("kitpvp zelda test " + p.getName() + (alles ? " alles" : ""));
    }

    void verder() {
        stap("poort", this::doorDePoort, 4, () -> true, 1);
        stap("verder", () -> {
            ZeldaHoofdstuk h = z.spel.hoofdstuk(stand().hoofdstuk);
            int r = stand().rust(h.id());
            Location l = h.rust.get(r).plek;
            moet(p.getLocation().distanceSquared(l) < 4, "niet op de verste rustplaats " + h.rust.get(r).naam + " (" + xyz(l) + ") maar op " + xyz(p.getLocation()));
            gedaan.add("rustplaats " + r + " " + h.rust.get(r).naam);
        }, 2, () -> true, 1);
        start("kitpvp zelda verder " + p.getName());
    }

    private void start(String titel) {
        final int[] gewacht = {0};
        final boolean[] gedaanDoe = {false};
        taak = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (!p.isOnline()) {
                fouten.add("speler ging offline");
                rij.clear();
            }
            Stap s = rij.peek();
            if (s == null) {
                taak.cancel();
                String uit = titel + ": " + (fouten.isEmpty() ? "OK" : "FOUT (" + fouten.size() + ")") + " · " + String.join(" → ", gedaan);
                aan.sendMessage(uit);
                pl.getLogger().info(uit);
                for (String f : fouten) {
                    aan.sendMessage("  ✗ " + f);
                    pl.getLogger().info("  ✗ " + f);
                }
                return;
            }
            if (!gedaanDoe[0]) {
                gedaanDoe[0] = true;
                gewacht[0] = 0;
                try {
                    s.doe().run();
                } catch (RuntimeException e) {
                    fouten.add(s.naam() + ": " + e);
                }
                return;
            }
            gewacht[0]++;
            if (gewacht[0] < s.wacht()) return;
            boolean ok;
            try {
                ok = s.klaar().getAsBoolean();
            } catch (RuntimeException e) {
                fouten.add(s.naam() + ": " + e);
                ok = true;
            }
            if (ok || gewacht[0] - s.wacht() >= s.max()) {
                if (!ok) fouten.add(s.naam() + ": niet gelukt");
                gedaan.add(s.naam());
                rij.poll();
                gedaanDoe[0] = false;
            }
        }, 1L, 1L);
    }
}
