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
 * puzzel). Zonder {@code alles} stopt hij bij de eerste rustplaats na de eerste kamer. Met {@code alles} ook: vrije
 * doorgang en de bel in de eerste kamer met een bel, vondsten die pas met een later voorwerp te pakken zijn (blijven
 * liggen), Brecht (varen) en de zijmissies (praten met wie iets nieuws heeft, pakken wat dan te zien is, tot alles klaar
 * is). Wist eerst de voortgang van die speler (alleen voor tests). Het resultaat komt na een paar seconden (ook in de
 * serverlog).
 *
 * <p>{@code kitpvp zelda verder <speler>}: door de poort naar binnen, en kijk of je op je laatste rustplaats staat
 * (na uitloggen en weer inloggen; een oude stand zonder laatste: de verste).
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
    private boolean belGehad;                               // de bel is één keer getest (in de eerste kamer met een bel)
    private String geklikt;                                 // het eiland waarheen Brecht vaart na een klik in zijn menu

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
        if (p.isDead()) p.spigot().respawn();
        pl.naarHub(p);
        Hub.Raak r = pl.hub.raak(pl.hub.poortPunt(z));
        moet(r != null && !r.dicht() && r.modus() == z, "de Zelda-poort stuurt niet naar Zelda");
        if (r != null && !r.dicht()) pl.kiesModus(p, r.modus());
        moet(pl.spelers.van(id).plek() == Toestand.Plek.SPEL && "zelda".equals(pl.spelers.van(id).modus()), "na de poort niet in Zelda: " + pl.spelers.van(id));
        moet(z.spel.speelt(p), "geen Zelda-sessie");
        moet(pl.stand(p).equals("geen"), "stand in Zelda is " + pl.stand(p) + " (geen huisdier in Zelda)");
    }

    void route(boolean alles) {
        if (p.isDead()) p.spigot().respawn();
        pl.naarHub(p);                                       // eerst uit Zelda, anders schrijft de rustplaats meteen weer
        z.spel.voortgang.wis(id);
        ZeldaHoofdstuk h = z.HOOFDSTUKKEN.get(0);
        stap("poort", () -> {
            doorDePoort();
            Location r0 = h.rust.get(0).plek;
            moet(p.getLocation().distanceSquared(r0) < 4, "niet bij het begin: " + xyz(p.getLocation()) + " (verwacht " + xyz(r0) + ")");
            moet(p.getInventory().getItem(0) != null && p.getInventory().getItem(0).getType() == Material.WOODEN_SWORD, "geen houten zwaard");
            moet(p.getInventory().getItem(8) != null && p.getInventory().getItem(8).getType() == Material.MAP, "geen kaart");
        }, 2, () -> true, 1);
        // alle eilanden in de volgorde van het verhaal: varen (met Brecht: klik in zijn menu), elk deel aflopen, wat nu te
        // halen is (een bom tegen een gebarsten muur), de zijmissies (zonder alles: tot na de eerste kamer)
        eilanden:
        for (int i = 0; i < z.EILANDEN.size(); i++) {
            ZeldaEiland e = z.EILANDEN.get(i), volgende = i + 1 < z.EILANDEN.size() ? z.EILANDEN.get(i + 1) : null;
            if (e != h.eiland) stap("varen naar " + e.id(), () -> {
                moet(e.open(stand()), e.id() + " is niet open na het vorige eiland (" + e.openNa() + ")");
                moet(!e.id().equals(geklikt) || z.spel.sessie(p).vaart != null, "Brecht vaart niet na een klik op " + e.naam());
                if (z.spel.sessie(p).vaart == null) {
                    stil();
                    z.spel.vaar(p, e);
                }
            }, 20, () -> z.spel.sessie(p).vaart == null && z.spel.sessie(p).h.eiland == e, 600);
            for (ZeldaHoofdstuk d : e.delen()) {
                if (!pad(d, alles, d == h)) break eilanden;
                stap("einde " + d.id(), () -> { }, 2, () -> stand().heeft(d.id()), 20);
            }
            stap("belopen " + e.id(), () -> {                                         // alles te halen zonder te teleporteren?
                java.util.Set<String> vw = new java.util.HashSet<>();
                for (ZeldaVoorwerp v : z.spel.voorwerpen) vw.add(v.vlag());
                for (String f : ZeldaLopen.onbereikbaar(e, vw)) fouten.add("niet te belopen: " + f);
            }, 2, () -> true, 1);
            laterHalen(e);
            zijmissies(e);
            klaar(e, false);
            for (ZeldaHoofdstuk d : e.delen())
                for (ZeldaHoofdstuk.Persoon pe : d.personen)
                    if (pe.naam.equals("Brecht")) stap("Brecht: varen", () -> {
                        stil();
                        z.spel.praat(p, pe);
                        boolean menu = p.getOpenInventory().getTopInventory().getHolder(false) instanceof ZeldaSpel.Menu;
                        long open = z.EILANDEN.stream().filter(x -> x.open(stand()) && x.aanlegRust() != null).count();
                        moet(open > 1 ? menu : rij().contains("geduld"), "Brecht: " + (open > 1 ? "geen menu" : "zegt " + rij()));
                        if (menu && volgende != null && volgende.open(stand())) {
                            if (klik("eiland:" + volgende.id())) geklikt = volgende.id();
                            else fouten.add("Brecht: " + volgende.naam() + " staat niet in zijn menu");
                        } else p.closeInventory();
                        stil();
                    }, 2, () -> true, 1);
        }
        // een baken dat brandt kondigt het volgende eiland aan: grijs op de kaart («nog even geduld»), en Wiske weet ervan
        if (alles) for (ZeldaEiland.Komt k : z.KOMT) stap("aangekondigd: " + k.id(), () -> {
            stil();
            z.spel.kaart(p);
            boolean staat = menuHeeft("komt:" + k.id());
            p.closeInventory();
            moet(staat == stand().heeft(k.openNa()), k.naam() + (staat ? " staat al op de kaart zonder " : " staat niet op de kaart met ") + k.openNa());
            moet(!stand().heeft(k.openNa()) || z.spel.komt(stand()) != null, "Wiske weet niet dat " + k.naam() + " eraan komt");
            stil();
        }, 2, () -> true, 1);
        // later terug: met een nieuw voorwerp (bommen) naar een eerder eiland varen (via de kaart) en daar halen wat nu kan
        if (alles) for (ZeldaEiland e : z.EILANDEN) {
            List<ZeldaHoofdstuk.Vondst> nu = new ArrayList<>();
            for (ZeldaHoofdstuk d : e.delen())
                for (ZeldaHoofdstuk.Vondst v : d.vondsten) if (v.voorwerp != null && z.spel.voorwerpen.stream().anyMatch(x -> x.vlag().equals(v.voorwerp))) nu.add(v);
            if (nu.isEmpty() || e == z.EILANDEN.get(z.EILANDEN.size() - 1)) continue;
            stap("hint: later naar " + e.id(), () -> {                                 // Wiske weet dat je daar nu iets kunt halen
                stil();
                z.spel.sessie(p).hintWacht = 0;
                z.spel.hint(p);
                moet(rij().contains(ZeldaRegels.inZin(e.naam())), "hint wijst niet naar " + e.naam() + ": " + rij());
                stil();
            }, 2, () -> true, 1);
            stap("terug naar " + e.id() + " (kaart)", () -> {
                stil();
                z.spel.kaart(p);
                moet(klik("eiland:" + e.id()), "kaart: " + e.naam() + " staat er niet op");
            }, 20, () -> z.spel.sessie(p).vaart == null && z.spel.sessie(p).h.eiland == e, 600);
            laterHalen(e);
            stap("later " + e.id(), () -> {
                for (ZeldaHoofdstuk.Vondst v : nu) if (stand().heeft(v.voorwerp)) moet(stand().heeft(v.vlag), "later terug: " + v.vlag + " niet te halen met " + v.voorwerp);
            }, 2, () -> true, 1);
        }
        // boodschappen over zee (Hanne vraagt zeekraal van het Zilverstrand): twee rondes langs alle eilanden, dan alles klaar
        if (alles && z.EILANDEN.size() > 1) {
            for (int ronde = 0; ronde < 2; ronde++)
                for (ZeldaEiland e : z.EILANDEN) {
                    stap("boodschap: naar " + e.id(), () -> {
                        stil();
                        if (z.spel.sessie(p).h.eiland == e) return;
                        z.spel.kaart(p);
                        moet(klik("eiland:" + e.id()), "kaart: " + e.naam() + " staat er niet op");
                    }, 20, () -> z.spel.sessie(p).vaart == null && z.spel.sessie(p).h.eiland == e, 600);
                    zijmissies(e);
                }
            for (ZeldaEiland e : z.EILANDEN) klaar(e, true);
        }
        stap("bewaren", () -> { }, 2, () -> !z.spel.voortgang.isVuil(), 260);   // hoogstens 10 s: dan bewaart het spel vanzelf
        stap("opslag", () -> {
            ZeldaVoortgang.Stand s = stand();
            ZeldaVoortgang schijf = new ZeldaVoortgang(new File(pl.getDataFolder(), "zelda.yml"));
            moet(schijf.van(id).rust(h.id()) == s.rust(h.id()) && s.rust(h.id()) > 0,
                    "zelda.yml: rustplaats " + schijf.van(id).rust(h.id()) + " (in het geheugen " + s.rust(h.id()) + ")");
            moet(schijf.van(id).vlaggen.containsAll(s.vlaggen) || !z.spel.voortgang.isVuil(), "zelda.yml: vlaggen niet bewaard");
        }, 2, () -> true, 1);
        start("kitpvp zelda test " + p.getName() + (alles ? " alles" : ""));
    }

    /**
     * Eén deel aflopen in de volgorde van het pad (rustplaats, verhaal, vondst, kamer, uitdaging, puzzel). Zonder
     * {@code alles}: stop bij de eerste rustplaats na de eerste kamer (dan false).
     */
    private boolean pad(ZeldaHoofdstuk h, boolean alles, boolean eerste) {
        boolean kamerGehad = false;
        for (Object o : h.volgorde) {
            if (o instanceof ZeldaHoofdstuk.Rust r) {
                if (r.index == 0 && eerste) continue;
                stap("rust " + r.id, () -> p.teleport(r.plek), 8, () -> stand().rust(h.id()) >= r.index && z.spel.sessie(p) != null && z.spel.sessie(p).rust == r.index, 40);
                if (!alles && kamerGehad) return false;
            } else if (o instanceof ZeldaHoofdstuk.Verhaal v) {
                stap("verhaal " + v.id, () -> p.teleport(v.zone.midden(h.w).add(0, 1, 0)), 8,
                        () -> stand().heeft("v:" + v.id) || (v.vereist != null && !stand().heeft(v.vereist)) || (v.als != null && !v.als.test(stand())), 40);
            } else if (o instanceof ZeldaHoofdstuk.Vondst v) {
                if (v.vereist != null && v.vereist.startsWith("g:")) continue;          // na een gesprek: zie «zijmissies»
                if (v.voorwerp != null)                                                   // pas met een later voorwerp: blijft liggen
                    stap("vondst " + v.vlag + " (pas met " + v.voorwerp + ")", () -> p.teleport(v.zone.midden(h.w).add(0, 1, 0)), 10, () -> {
                        moet(!stand().heeft(v.vlag), "vondst " + v.vlag + " te pakken zonder " + v.voorwerp);
                        return true;
                    }, 1);
                else stap("vondst " + v.vlag, () -> p.teleport(v.zone.midden(h.w).add(0, 1, 0)), 8, () -> stand().heeft(v.vlag), 40);
            } else if (o instanceof ZeldaHoofdstuk.Praten pr) {               // praten met wie op het pad staat (Tijmen, Bram)
                ZeldaHoofdstuk.Persoon pe = pr.wie();
                stap("praten met " + pe.naam, () -> {
                    p.teleport(vrij(pe.plek.clone().add(1.5, 0, 0)));
                    moet(pe.nieuw(stand()), pe.naam + " heeft niets nieuws te zeggen");
                }, 4, () -> {
                    if (!pe.nieuw(stand())) return true;
                    stil();
                    z.spel.praat(p, pe);
                    gedaan.add(pe.naam);
                    return false;
                }, 20);
            } else if (o instanceof ZeldaHoofdstuk.Poort po) {                // een poort voor wie iets heeft: hij gaat open
                stap("poort " + po.id, () -> {
                    if (po.deur.isOpen) z.spel.herstel(po);
                    p.teleport(vrij(po.gebied.midden(h.w).add(0, 1, 0)));
                }, 8, () -> po.deur.isOpen, 40);
            } else if (o instanceof ZeldaHoofdstuk.Tij t) {                   // eb of vloed: een echte klik op de hendel
                ZeldaHoofdstuk.Getij g = t.g();
                stap("getij " + g.id + (t.vloed() ? " vloed" : " eb"), () -> {
                    if (g.bezig() || g.vloed == t.vloed()) z.spel.zetGetij(g, !t.vloed(), true);   // eerst de andere kant op
                    hendelKlik(h, g, t.h());
                }, 10, () -> !g.bezig() && g.vloed == t.vloed(), 120);
                stap("getij " + g.id + " klopt", () -> {
                    for (ZeldaHoofdstuk.Deur l : g.lagen) {
                        int[] c = l.blokken.get(0);
                        Material m = h.w.getBlockAt(c[0], c[1], c[2]).getType();
                        moet(m == (t.vloed() ? Material.WATER : Material.AIR), "getij " + g.id + ": laag op hoogte " + c[1] + " is " + m);
                    }
                    for (ZeldaHoofdstuk.Deur d : g.sluizen) moet(d.isOpen != t.vloed(), "getij " + g.id + ": sluis " + (d.isOpen ? "open" : "dicht"));
                    for (ZeldaHoofdstuk.Deur d : g.vloeddeuren) moet(d.isOpen == t.vloed(), "getij " + g.id + ": vloeddeur " + (d.isOpen ? "open" : "dicht"));
                    if (g.onthoud != null) moet(stand().heeft(g.onthoud), "getij " + g.id + ": " + g.onthoud + " niet onthouden");
                }, 2, () -> true, 1);
            } else if (o instanceof ZeldaHoofdstuk.Kamer k && k.alleenBel) {  // een oefenplek: rustig, tot je de bel luidt
                kamerGehad = true;
                stap("kamer " + k.id + " rustig", () -> {
                    if (k.fase != ZeldaHoofdstuk.Fase.RUST) z.spel.herstel(k);
                    p.teleport(k.start.midden(h.w).add(0, 1, 0));
                }, 20, () -> {
                    moet(k.fase == ZeldaHoofdstuk.Fase.RUST && k.in.isOpen && k.uit.isOpen && k.vijanden.isEmpty(), "kamer " + k.id + ": begint zonder bel");
                    return true;
                }, 1);
                stap("kamer " + k.id + " bel", () -> belKlik(h, k), 5, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG && !k.in.isOpen && !k.vijanden.isEmpty(), 40);
                stap("kamer " + k.id + " verslaan", () -> moet(z.spel.muziek.lus(p) == k.muziek, "kamer " + k.id + ": geen gevechtsmuziek"),
                        5, () -> verslaan(h, k), 900);
                stap("kamer " + k.id + " open", () -> moet(k.in.isOpen && k.uit.isOpen && stand().heeft("k:" + k.id), "kamer " + k.id + ": niet open of niet gewonnen"),
                        2, () -> true, 1);
            } else if (o instanceof ZeldaHoofdstuk.Kamer k) {
                kamerGehad = true;
                if (alles) for (ZeldaHoofdstuk.Vondst v : h.vondsten)                // wat pas na deze kamer mag (het hart van de baas): nog niet te pakken
                    if (("k:" + k.id).equals(v.vereist))
                        stap("vondst " + v.vlag + " (pas na " + k.id + ")", () -> p.teleport(v.zone.midden(h.w).add(0, 1, 0)), 10, () -> {
                            moet(!stand().heeft(v.vlag), "vondst " + v.vlag + " te pakken zonder " + k.id + " te winnen");
                            return true;
                        }, 1);
                stap("kamer " + k.id + " dicht", () -> {
                    if (k.fase != ZeldaHoofdstuk.Fase.RUST) z.spel.herstel(k);
                    p.teleport(k.start.midden(h.w).add(0, 1, 0));
                }, 10, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG, 40);
                if (k.baas instanceof Zoutbaard zb) stap("bom in de ton van " + zb.naam, () -> {     // een echte bom in de ton waar hij bij staat
                    moet(stand().heeft("bommen"), "geen bommen voor " + zb.naam);
                    var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    if (max != null) p.setHealth(max.getValue());
                    zb.tel = -100;                                                     // even niet springen
                    int[] t = zb.tonnen.get(zb.bij);
                    Location ton = new Location(h.w, t[0] + 0.5, t[1] + 0.5, t[2] + 0.5);
                    org.bukkit.util.Vector naarMidden = k.start.midden(h.w).toVector().subtract(ton.toVector()).setY(0).normalize();
                    Location voor = vrij(ton.clone().add(naarMidden.multiply(3.2)).add(0, 0.5, 0));
                    voor.setDirection(ton.toVector().subtract(voor.toVector().add(new org.bukkit.util.Vector(0, 1.62, 0))));
                    p.teleport(voor);
                    p.setCooldown(ZeldaSpel.Bommen.ITEM, 0);
                    z.spel.voorwerp("bommen").gebruik(z.spel, p, null);
                }, 50, () -> zb.versuft > 0, 60);
                if (k.baas instanceof Zoutbaard zb) stap(zb.naam + " versuft", () -> {
                    int[] t = zb.tonnen.get(zb.bij);
                    moet(h.w.getBlockAt(t[0], t[1], t[2]).getType() == Material.AIR, "de ton is niet gesprongen");
                    moet(zb.schade(10) > 10, zb.naam + ": versuft doet een klap niet meer pijn");
                }, 2, () -> true, 1);
                if (k.baas != null) stap("baas " + k.baas.naam, () -> {
                    moet(k.baas.leeft() && k.baas.balk != null, "baas " + k.baas.naam + " is er niet");
                    if (k.baas.leeft()) k.baas.ent.setHealth(k.baas.ent.getHealth() * 0.55);
                }, 50, () -> k.vijanden.size() >= 1 + Math.max(1, k.baas.oproepen[0]) && k.baas.balk.progress() < 0.6f, 60);
                stap("kamer " + k.id + " verslaan", () -> {
                    moet(!k.in.isOpen && !k.uit.isOpen, "kamer " + k.id + ": deuren niet dicht tijdens het gevecht");
                    moet(!k.vijanden.isEmpty(), "kamer " + k.id + ": geen vijanden");
                    moet(z.spel.muziek.lus(p) == k.muziek, "kamer " + k.id + ": geen gevechtsmuziek");
                }, 5, () -> verslaan(h, k), 900);
                stap("kamer " + k.id + " open", () -> {
                    moet(k.in.isOpen && k.uit.isOpen, "kamer " + k.id + ": deuren niet open na winnen");
                    Material m = h.w.getBlockAt(k.uit.blokken.get(0)[0], k.uit.blokken.get(0)[1], k.uit.blokken.get(0)[2]).getType();
                    moet(m == Material.AIR, "kamer " + k.id + ": uitgang is " + m);
                    moet(z.spel.muziek.lus(p) == null, "kamer " + k.id + ": muziek speelt nog");
                }, 2, () -> true, 1);
                if (alles && k.bel != null && !belGehad) {                    // terugkomen: vrije doorgang, en de bel voor wie nog eens wil
                    belGehad = true;
                    stap("kamer " + k.id + " vrije doorgang", () -> {
                        z.spel.herstel(k);
                        p.teleport(k.start.midden(h.w).add(0, 1, 0));
                    }, 10, () -> k.fase == ZeldaHoofdstuk.Fase.GEWONNEN && k.in.isOpen && k.uit.isOpen && k.vijanden.isEmpty(), 40);
                    stap("kamer " + k.id + " van de andere kant", () -> {                  // terug vanaf de uitgang (zijmissies)
                        z.spel.herstel(k);
                        p.teleport(k.uit.omvang().midden(h.w).add(2, 0, 0));
                    }, 10, () -> k.fase == ZeldaHoofdstuk.Fase.GEWONNEN && k.uit.isOpen, 40);
                    stap("kamer " + k.id + " bel", () -> belKlik(h, k), 5,
                            () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG && k.terug && !k.in.isOpen && !k.vijanden.isEmpty(), 40);
                    stap("kamer " + k.id + " bel verslaan", () -> { }, 5, () -> verslaan(h, k), 1200);
                    stap("kamer " + k.id + " bel gewonnen", () -> moet(k.golf == k.golven.size() + k.extra.size() - 1,
                            "kamer " + k.id + ": met de bel " + (k.golf + 1) + " golven (verwacht " + (k.golven.size() + k.extra.size()) + ")"), 2, () -> true, 1);
                    stap("kamer " + k.id + " nieuw erbij", () -> {                          // wie hem nog niet won, vecht gewoon
                        stand().wis("k:" + k.id);
                        p.teleport(k.start.midden(h.w).add(0, 1, 0));
                    }, 10, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG && !k.terug, 40);
                    stap("kamer " + k.id + " nieuw erbij verslaan", () -> { }, 5, () -> verslaan(h, k) && stand().heeft("k:" + k.id), 900);
                }
            } else if (o instanceof ZeldaHoofdstuk.Uitdaging u) {
                stap("uitdaging " + u.id + " dicht", () -> p.teleport(vrij(u.start.midden(h.w).add(-3, 1, 0))), 20,
                        () -> !u.poort.isOpen && h.w.getBlockAt(u.poort.blokken.get(0)[0], u.poort.blokken.get(0)[1], u.poort.blokken.get(0)[2]).getType() == u.poort.dicht.getMaterial(), 80);
                stap("uitdaging " + u.id + " start", () -> p.teleport(vrij(u.start.midden(h.w).add(0, 1, 0))), 8,
                        () -> u.loopt.containsKey(id) && u.poort.isOpen, 40);
                stap("uitdaging " + u.id + " finish", () -> p.teleport(vrij(u.finish.midden(h.w).add(0, 1, 0))), 8, () -> stand().beste(u.id) != null, 40);
            } else if (o instanceof ZeldaHoofdstuk.Barst b) {                       // een gebarsten muur op het pad: een echte bom
                stap("bom op " + b.id, () -> {
                    moet(stand().heeft("bommen"), "geen bommen voor de gebarsten muur " + b.id);
                    if (b.muur.isOpen) z.spel.herstel(b);
                    gooi(h, b);
                }, 50, () -> b.muur.isOpen && stand().heeft("b:" + b.id), 60);
            } else if (o instanceof ZeldaHoofdstuk.Spiegels sp) {                  // draai de spiegels met echte klikken
                stap("spiegels " + sp.id, () -> {
                    if (sp.opgelost) z.spel.herstel(sp);
                    moet(!z.spel.straal(sp, sp.stand).raak(), "spiegels " + sp.id + ": al opgelost voor er geklikt is");
                    p.teleport(vrij(sp.gebied.midden(h.w).add(0, 1, 0)));
                    for (int i = 0; i < sp.spiegels.size(); i++)
                        for (int n = 0; n < 4 && sp.goed.get(i) != null && sp.stand.get(i) != sp.goed.get(i); n++) {
                            int[] c = sp.spiegels.get(i);
                            var e = new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                                    p.getInventory().getItemInMainHand(), h.w.getBlockAt(c[0], c[1], c[2]), org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
                            z.spel.onKlik(e);
                            moet(e.isCancelled(), "spiegels " + sp.id + ": spiegel " + i + " reageert niet op een klik");
                        }
                }, 4, () -> sp.opgelost && sp.deur.isOpen && stand().heeft("r:" + sp.id), 20);
            } else if (o instanceof ZeldaHoofdstuk.Reeks r) {
                stap("puzzel " + r.id, () -> {
                    if (r.opgelost || !r.isAan.isEmpty()) z.spel.herstel(r);
                    p.teleport(r.gebied.midden(h.w).add(0, 1, 0));
                    for (int i = 0; i < r.dingen.size(); i++) z.spel.activeer(p, r, i);
                }, 4, () -> r.opgelost && r.deur.isOpen, 20);
            }
        }
        return alles;
    }

    /** Rechtsklik op een hendel van het getij, zoals een speler (vanaf de kant waar je staat). */
    private void hendelKlik(ZeldaHoofdstuk h, ZeldaHoofdstuk.Getij g, ZeldaHoofdstuk.Getij.Hendel hd) {
        p.teleport(vrij(new Location(h.w, hd.x() - 0.5, hd.y(), hd.z() + 0.5)));
        var e = new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                p.getInventory().getItemInMainHand(), h.w.getBlockAt(hd.x(), hd.y(), hd.z()), org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
        z.spel.onKlik(e);
        moet(e.isCancelled(), "getij " + g.id + ": de hendel reageert niet op een klik");
    }

    /** Staat er in het open Zelda-menu een item met deze soort? */
    private boolean menuHeeft(String soort) {
        var view = p.getOpenInventory();
        if (!(view.getTopInventory().getHolder(false) instanceof ZeldaSpel.Menu)) return false;
        for (int i = 0; i < view.getTopInventory().getSize(); i++) {
            var it = view.getTopInventory().getItem(i);
            if (it != null && it.hasItemMeta() && soort.equals(it.getItemMeta().getPersistentDataContainer().get(z.spel.itemKey, org.bukkit.persistence.PersistentDataType.STRING))) return true;
        }
        return false;
    }

    /** Rechtsklik op de bel van een kamer, zoals een speler (vanuit het midden van de kamer). */
    private void belKlik(ZeldaHoofdstuk h, ZeldaHoofdstuk.Kamer k) {
        p.teleport(k.start.midden(h.w).add(0, 1, 0));
        var b = h.w.getBlockAt(k.bel[0], k.bel[1], k.bel[2]);
        var e = new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                p.getInventory().getItemInMainHand(), b, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
        z.spel.onKlik(e);
        moet(e.isCancelled(), "kamer " + k.id + ": de bel reageert niet op een klik");
    }

    /** Een vrije plek (twee blokken lucht met een vloer) op {@code l}, vlak eronder of anders erboven (een duintop). */
    private static Location vrij(Location l) {
        for (int i = 0; i <= 3; i++) if (staan(l.clone().add(0, -i, 0))) return l.clone().add(0, -i, 0);
        for (int i = 1; i <= 12; i++) if (staan(l.clone().add(0, i, 0))) return l.clone().add(0, i, 0);
        return l;
    }

    private static boolean staan(Location t) {
        return t.getBlock().isPassable() && t.clone().add(0, 1, 0).getBlock().isPassable() && t.clone().add(0, -1, 0).getBlock().getType().isSolid();
    }

    /** Gooi een echte bom (het voorwerp, zoals rechtsklik) tegen een gebarsten muur, vanaf de plek ervoor. */
    private void gooi(ZeldaHoofdstuk h, ZeldaHoofdstuk.Barst b) {
        Location doel = b.muur.omvang().midden(h.w).add(0, 0.6, 0);
        Location voor = b.voor != null ? b.voor.clone() : null;
        if (voor == null) {                                  // de kant die het verst van wat erachter ligt af is
            double best = -1;
            for (org.bukkit.block.BlockFace f : new org.bukkit.block.BlockFace[]{org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
                Location l = b.muur.omvang().midden(h.w).add(f.getModX() * 2.5, 0, f.getModZ() * 2.5);
                if (!l.getBlock().isPassable() || !l.clone().add(0, 1, 0).getBlock().isPassable()) continue;
                double d = 1e9;
                for (ZeldaHoofdstuk.Vondst v : h.vondsten) if (v.voorwerp != null) d = Math.min(d, v.plek.distance(l));
                if (d > best) {
                    best = d;
                    voor = l;
                }
            }
        }
        if (voor == null) {
            fouten.add("gebarsten muur " + b.id + ": geen plek om ervoor te staan");
            return;
        }
        voor.setDirection(doel.toVector().subtract(voor.toVector().add(new org.bukkit.util.Vector(0, 1.62, 0))));
        p.teleport(voor);
        p.setCooldown(ZeldaSpel.Bommen.ITEM, 0);
        z.spel.voorwerp("bommen").gebruik(z.spel, p, null);
    }

    /** Klik in het open Zelda-menu (Brecht of de kaart) op het item met deze soort; false als het er niet is. */
    private boolean klik(String soort) {
        var view = p.getOpenInventory();
        if (!(view.getTopInventory().getHolder(false) instanceof ZeldaSpel.Menu)) return false;
        for (int i = 0; i < view.getTopInventory().getSize(); i++) {
            var it = view.getTopInventory().getItem(i);
            if (it == null || !it.hasItemMeta() || !soort.equals(it.getItemMeta().getPersistentDataContainer().get(z.spel.itemKey, org.bukkit.persistence.PersistentDataType.STRING))) continue;
            var e = new org.bukkit.event.inventory.InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, i,
                    org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            z.spel.onMenu(e);
            return e.isCancelled();
        }
        p.closeInventory();
        return false;
    }

    /**
     * Wat op dit eiland nu te halen is met een voorwerp dat je hebt (een vondst met {@code voorwerp}): een bom tegen de
     * gebarsten muur ervoor, en dan pakken. Wat nog niet kan (klauwhaak), blijft liggen.
     */
    private void laterHalen(ZeldaEiland e) {
        for (ZeldaHoofdstuk d : e.delen())
            for (ZeldaHoofdstuk.Vondst v : d.vondsten) {
                if (v.voorwerp == null) continue;
                final ZeldaHoofdstuk.Barst[] muur = {null};
                final boolean[] kan = {false};
                stap("later " + v.vlag, () -> {
                    ZeldaVoortgang.Stand s = stand();
                    kan[0] = s.heeft(v.voorwerp) && !s.heeft(v.vlag);
                    if (!kan[0]) return;
                    for (ZeldaHoofdstuk x : e.delen())
                        for (ZeldaHoofdstuk.Barst b : x.barsten) if (b.gebied.in(v.plek) && !b.muur.isOpen) muur[0] = b;
                    if (muur[0] != null) gooi(d, muur[0]);
                }, 50, () -> !kan[0] || muur[0] == null || muur[0].muur.isOpen, 60);
                stap("pak " + v.vlag, () -> {
                    if (kan[0]) p.teleport(vrij(v.zone.midden(d.w).add(0, 1, 0)));
                }, 8, () -> !kan[0] || stand().heeft(v.vlag), 40);
            }
    }

    /** Alle vijanden in de kamer verslaan (en binnen blijven); true als de kamer gewonnen is. */
    private boolean verslaan(ZeldaHoofdstuk h, ZeldaHoofdstuk.Kamer k) {
        for (LivingEntity e : new ArrayList<>(k.vijanden)) if (e.isValid()) e.damage(10_000, p);
        if (!k.binnen.in(p.getLocation())) p.teleport(k.start.midden(h.w).add(0, 1, 0));
        return k.fase == ZeldaHoofdstuk.Fase.GEWONNEN;
    }

    /**
     * De zijmissies van een eiland, zoals een speler ze doet: praat met wie iets nieuws heeft, pak wat daarna te zien is,
     * tot er niets meer is (daarna {@link #klaar}).
     */
    private void zijmissies(ZeldaEiland e) {
        final int[] wacht = {0};
        stap("zijmissies", this::stil, 2, () -> {
            if (wacht[0]-- > 0) return false;
            ZeldaVoortgang.Stand s = stand();
            for (ZeldaHoofdstuk d : e.delen())
                for (ZeldaHoofdstuk.Vondst v : d.vondsten)
                    if (!s.heeft(v.vlag) && v.vereist != null && s.heeft(v.vereist) && (v.voorwerp == null || s.heeft(v.voorwerp))) {
                        p.teleport(v.zone.midden(d.w).add(0, 1, 0));
                        wacht[0] = 8;
                        return false;
                    }
            for (ZeldaHoofdstuk d : e.delen())
                for (ZeldaHoofdstuk.Persoon pe : d.personen)
                    if (pe.nieuw(s)) {
                        stil();
                        z.spel.praat(p, pe);
                        gedaan.add(pe.naam);
                        wacht[0] = 2;
                        return false;
                    }
            return true;
        }, 900);
    }

    /**
     * Zijn de zijmissies van dit eiland klaar, en de hartstukjes gevonden die nu te halen zijn? {@code alle}: ook de
     * missies die nog niet begonnen zijn (aan het eind; een boodschap over zee begint pas als je op het andere eiland was).
     */
    private void klaar(ZeldaEiland e, boolean alle) {
        stap("zijmissies klaar " + e.id(), () -> {
            ZeldaVoortgang.Stand s = stand();
            for (ZeldaHoofdstuk d : e.delen()) {
                for (ZeldaHoofdstuk.Missie m : d.missies)
                    if (alle || s.missie(m.id()) > 0) moet(s.missieKlaar(m.id()), "missie " + m.id() + " niet klaar (stap " + s.missie(m.id()) + ")");
                for (String hs : d.stukjes) {
                    ZeldaHoofdstuk.Vondst v = null;
                    for (ZeldaHoofdstuk.Vondst x : d.vondsten) if (x.vlag.equals(hs)) v = x;
                    boolean later = v != null && v.voorwerp != null && !s.heeft(v.voorwerp);
                    moet(s.heeft(hs) != later, "hartstukje " + hs + (later ? " al gevonden (hoort pas met " + v.voorwerp + ")" : " niet gevonden"));
                }
            }
            var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            moet(max != null && max.getValue() == ZeldaRegels.harten(s) * 2, "harten " + (max == null ? "?" : max.getValue() / 2) + " (verwacht " + ZeldaRegels.harten(s) + ")");
            stil();
        }, 2, () -> true, 1);
    }

    /** Doodgaan in de eerste kamer: respawn bij je rustplaats, spullen houden, kamer terug naar het begin. */
    void dood() {
        if (p.isDead()) p.spigot().respawn();
        pl.naarHub(p);
        z.spel.voortgang.wis(id);
        ZeldaHoofdstuk h = z.HOOFDSTUKKEN.get(0);
        ZeldaHoofdstuk.Kamer k = h.kamers.stream().filter(x -> !x.alleenBel).findFirst().orElseThrow();
        stap("poort", this::doorDePoort, 2, () -> true, 1);
        stap("kamer " + k.id + " dicht", () -> {
            if (k.fase != ZeldaHoofdstuk.Fase.RUST) z.spel.herstel(k);
            p.teleport(k.start.midden(h.w).add(0, 1, 0));
        }, 10, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG, 40);
        stap("dood", () -> p.setHealth(0), 3, () -> {
            if (p.isDead()) p.spigot().respawn();
            return !p.isDead();
        }, 40);
        stap("respawn", () -> { }, 5, () -> {
            Location r = h.rust.get(z.spel.sessie(p).rust).plek;
            moet(p.getLocation().distanceSquared(r) < 4, "na doodgaan niet bij de rustplaats: " + xyz(p.getLocation()) + " (verwacht " + xyz(r) + ")");
            moet(pl.spelers.van(id).plek() == Toestand.Plek.SPEL, "na doodgaan niet meer in Zelda: " + pl.spelers.van(id));
            moet(p.getInventory().getItem(0) != null && p.getInventory().getItem(8) != null, "spullen kwijt na doodgaan");
            var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            moet(max != null && p.getHealth() == max.getValue() && max.getValue() == 16, "leven na respawn " + p.getHealth() + "/" + (max == null ? "?" : max.getValue()));
            return true;
        }, 1);
        stap("kamer " + k.id + " terug", () -> { }, 20, () -> k.fase == ZeldaHoofdstuk.Fase.RUST && k.vijanden.isEmpty() && k.in.isOpen && !k.uit.isOpen, 200);
        start("kitpvp zelda test " + p.getName() + " dood");
    }

    /**
     * {@code kitpvp zelda test <speler> motor}: de motor van Zelda 2.0 met een echte speler: Wiske vliegt mee, altijd
     * muziek, /hint (en nog eens), bron (genezen, flesjes vol), flesje drinken, hartstukjes, harnas, huisdier (verschijnt
     * en loopt mee), gesprek en zijmissie met beloning, opnieuw vanaf een rustplaats, uitleg uit, varen (overtocht en
     * aankomst), een tip na doodgaan in een kamer, de kaart, en weg = alles weer weg. Wist eerst en na afloop de
     * voortgang van die speler (alleen voor tests).
     */
    void motor() {
        if (p.isDead()) p.spigot().respawn();
        pl.naarHub(p);
        z.spel.voortgang.wis(id);
        ZeldaHoofdstuk h = z.HOOFDSTUKKEN.get(0);
        ZeldaSpel sp = z.spel;
        stap("poort", this::doorDePoort, 2, () -> true, 1);
        stap("wiske", () -> { }, 10, () -> {
            var f = sp.metgezel.van(p);
            return f != null && f.getLocation().distance(p.getEyeLocation()) < 4 && p.canSee(f);
        }, 40);
        stap("muziek", () -> moet(sp.muziek.achter(p) == ZeldaMuziek.THUIS, "thuis speelt " + sp.muziek.achter(p)), 25, () -> true, 1);
        stap("hint", () -> {
            sp.sessie(p).rij.clear();
            sp.hint(p);
            moet("tijmen".equals(sp.sessie(p).hintDoel), "hint: doel " + sp.sessie(p).hintDoel + " (verwacht tijmen)");
            moet(!sp.sessie(p).rij.isEmpty() || sp.sessie(p).nu != null, "hint: Wiske zegt niets");
        }, 60, () -> true, 1);
        stap("hint nog eens", () -> {
            sp.hint(p);
            String tekst = rij();
            moet(tekst.contains("Praat met Tijmen"), "hint 2 zegt niet wat je moet doen: " + tekst);
        }, 2, () -> true, 1);
        stap("wegwijzer", () -> {                                                // een persoon zegt je volgende opdracht (doelen in elke volgorde: de dichtstbijzijnde)
            stil();
            stand().zet("g:tijmen-1");
            ZeldaHoofdstuk.Doel d = ZeldaRegels.doel(h.doelen, stand(), p.getLocation().getX(), p.getLocation().getZ());
            sp.wegwijzer(p, "Tijmen");
            moet(d != null && d.groep() != null && rij().contains("Tijmen · " + d.verder()), "wegwijzer: " + rij() + " (verwacht " + (d == null ? "?" : d.verder()) + ")");
            stand().wis("g:tijmen-1");
            stil();
        }, 2, () -> true, 1);
        ZeldaHoofdstuk.Bron bron = h.bronnen.get(0);
        stap("bron", () -> {
            stand().zet("fles2");                                                    // alleen het flesje van Tijmen (nog niet dat van Hanne)
            stand().teller("fles-vol", 0);
            sp.uitrusten(p, stand());
            moet(item(2) == Material.GLASS_BOTTLE, "leeg flesje niet in vak 3 maar " + item(2));
            p.setHealth(6);
            p.teleport(bron.midden);
        }, 50, () -> p.getHealth() > 10 && ZeldaRegels.vol(stand()) == 1 && item(2) == Material.POTION, 60);
        stap("flesje", () -> {
            p.teleport(new Location(h.w, ZeldaHoofdstuk1.X0 + 38.5, bron.midden.getY(), 3.5));
            p.setHealth(4);
            sp.voorwerp("fles").gebruik(sp, p, null);
            moet(p.getHealth() >= 13, "flesje geneest niet: " + p.getHealth());
            moet(ZeldaRegels.vol(stand()) == 0 && item(2) == Material.GLASS_BOTTLE, "flesje niet leeg na drinken");
        }, 2, () -> true, 1);
        stap("hartstukjes", () -> {
            for (String v : new String[]{"hs:a", "hs:b", "hs:c", "hs:d"}) stand().zet(v);
            sp.uitrusten(p, stand());
            var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            moet(max != null && max.getValue() == 18, "vier hartstukjes: " + (max == null ? "?" : max.getValue()) + " (verwacht 18)");
        }, 2, () -> true, 1);
        stap("harnas", () -> {
            stand().zet("harnas2");
            sp.uitrusten(p, stand());
            moet(p.getInventory().getChestplate() != null && p.getInventory().getChestplate().getType() == Material.IRON_CHESTPLATE, "harnas 2 is geen ijzer");
        }, 2, () -> true, 1);
        if (ZeldaDier.AAN) stap("huisdier", () -> {
            stand().zet("dier:vos");
            stand().dier = "Pluis";
        }, 30, () -> sp.dier.van(p) != null, 60);
        if (ZeldaDier.AAN) stap("huisdier loopt mee", () -> p.teleport(new Location(h.w, ZeldaHoofdstuk1.X0 + 30.5, bron.midden.getY(), 2.5)), 100,
                () -> sp.dier.van(p) != null && sp.dier.van(p).getLocation().distance(p.getLocation()) < 6, 100);
        ZeldaHoofdstuk.Persoon proef = new ZeldaHoofdstuk.Persoon("Proef", p.getLocation(), null, List.of(ZeldaHoofdstuk.r("Proef", "Hallo.")));
        proef.gesprek("proef-1", s -> s.missie("proef") == 0, List.of(ZeldaHoofdstuk.r("Proef", "Help je mij?")), (spel, q, s) -> spel.missie(q, "proef", 1));
        proef.gesprek("proef-2", s -> s.missie("proef") == 1, List.of(ZeldaHoofdstuk.r("Proef", "Dank je!")), (spel, q, s) -> {
            spel.missie(q, "proef", ZeldaVoortgang.KLAAR);
            spel.beloon(q, "fles3", "Flesje", "nog een");
        });
        stap("gesprek", () -> {
            stil();
            moet(proef.nieuw(stand()), "persoon heeft niets nieuws");
            sp.praat(p, proef);
            moet(stand().missie("proef") == 1 && stand().heeft("g:proef-1"), "missie niet begonnen");
            stil();
            sp.praat(p, proef);
            moet(stand().missieKlaar("proef") && ZeldaRegels.flessen(stand()) == 2, "missie niet klaar of geen beloning");
            moet(!proef.nieuw(stand()), "persoon blijft iets nieuws hebben");
            stil();
        }, 2, () -> true, 1);
        stap("opnieuw", () -> {
            stand().zet("v:tempel");
            stand().zet("r:vuren");
            stand().bezoek(h.id(), 1);
            sp.opnieuw(p, h, 1);
            moet(!stand().heeft("v:tempel") && !stand().heeft("r:vuren"), "opnieuw vergat de tempel niet");
            moet(p.getLocation().distanceSquared(h.rust.get(1).plek) < 4, "opnieuw: niet bij " + h.rust.get(1).naam);
        }, 20, () -> true, 1);
        stap("uitleg uit", () -> {
            stand().zet("o:uitleg-uit");
            stand().wis("v:uitleg-kaart");
            stil();
            p.teleport(h.rust.get(2).plek);
        }, 20, () -> stand().heeft("v:uitleg-kaart"), 40);
        stap("uitleg uit stil", () -> moet(!rij().contains("Je kaart"), "uitleg kwam toch: " + rij()), 2, () -> true, 1);
        if (!h.barsten.isEmpty()) {
            ZeldaHoofdstuk.Barst b = h.barsten.get(0);
            stap("bom", () -> {                                                   // de bommenzak in vak 4, een echte bom tegen de muur
                stand().zet("bommen");
                sp.uitrusten(p, stand());
                moet(item(3) == ZeldaSpel.Bommen.ITEM, "bommen niet in vak 4 maar " + item(3));
                if (b.muur.isOpen) sp.herstel(b);
                gooi(h, b);
            }, 50, () -> b.muur.isOpen && stand().heeft("b:" + b.id), 60);
            stap("gebarsten muur", () -> {
                moet(sp.barstBij(b.muur.omvang().midden(h.w)) == b, "barstBij vindt de muur niet");
                sp.herstel(b);
            }, 10, () -> b.muur.isOpen, 40);                                      // wie hem openbrak, vindt hem open
            stap("gebarsten muur dicht", () -> {
                stand().wis("b:" + b.id);
                stand().wis("bommen");
                sp.uitrusten(p, stand());
                p.teleport(h.rust.get(2).plek);
                sp.herstel(b);
            }, 10, () -> !b.muur.isOpen, 1);
        }
        ZeldaHoofdstuk.Vondst later = null;
        for (ZeldaHoofdstuk.Vondst v : h.vondsten) if (v.voorwerp != null && later == null) later = v;
        if (later != null) {
            ZeldaHoofdstuk.Vondst v = later;
            stap("vondst zonder " + v.voorwerp, () -> p.teleport(v.zone.midden(h.w).add(0, 1, 0)), 10, () -> !stand().heeft(v.vlag), 1);
            stap("vondst met " + v.voorwerp, () -> stand().zet(v.voorwerp), 2, () -> stand().heeft(v.vlag), 40);
            stap("vondst weer weg", () -> {
                stand().wis(v.voorwerp);
                stand().wis(v.vlag);
                sp.uitrusten(p, stand());
                stil();
            }, 2, () -> true, 1);
        }
        ZeldaHoofdstuk.Getij getij = null;
        for (ZeldaHoofdstuk d : z.HOOFDSTUKKEN) if (getij == null && !d.getijden.isEmpty()) getij = d.getijden.get(0);
        if (getij != null) {
            ZeldaHoofdstuk.Getij g = getij;
            ZeldaHoofdstuk.Getij.Hendel hd = g.hendels.get(0);
            stap("getij: vast", () -> {                                          // zonder wat ervoor nodig is: hij zit vast
                if (g.vloed != g.rustVloed) z.spel.zetGetij(g, g.rustVloed, true);
                stil();
                z.spel.trek(p, g, hd);
                moet(!g.bezig() && g.vloed == g.rustVloed, "getij: de hendel ging om zonder " + g.id);
                moet(rij().contains(hd.nee().tekst()), "getij: Wiske zegt niet waarom hij vastzit: " + rij());
                stil();
            }, 2, () -> true, 1);
            stap("getij: om", () -> {
                for (String v : new String[]{"maanparel", "r:getij"}) stand().zet(v);
                moet(hd.mag().test(stand()), "getij: hendel mag nog steeds niet");
                z.spel.trek(p, g, hd);
                moet(g.bezig() && g.vloed != g.rustVloed, "getij: de hendel gaat niet om");
            }, 5, () -> !g.bezig(), 100);
            stap("getij: water", () -> {
                int[] c = g.lagen.get(g.lagen.size() - 1).blokken.get(0);
                Material m = z.zee.w.getBlockAt(c[0], c[1], c[2]).getType();
                moet(m == (g.vloed ? Material.WATER : Material.AIR), "getij: bovenste laag is " + m);
                z.spel.zetGetij(g, g.rustVloed, true);
                for (String v : new String[]{"maanparel", "r:getij"}) stand().wis(v);
            }, 2, () -> true, 1);
        }
        ZeldaEiland e = h.eiland;
        stap("varen", () -> {
            stil();
            sp.vaar(p, e);
            moet(sp.sessie(p).vaart != null && z.zee.speelgebied.in(p.getLocation()), "niet aan boord: " + xyz(p.getLocation()));
            moet(sp.muziek.achter(p) == ZeldaMuziek.ZEE, "aan boord speelt " + sp.muziek.achter(p));
            moet(z.plek(p, pl.spelers.van(id)).equals("Op zee"), "plek aan boord: " + z.plek(p, pl.spelers.van(id)));
        }, 40, () -> sp.sessie(p).vaart == null, 400);
        stap("aankomst", () -> {
            moet(p.getLocation().distanceSquared(e.aanlegPlek()) < 4, "niet bij de aanleg: " + xyz(p.getLocation()));
            moet(stand().heeft("e:" + e.id()), "aankomst niet onthouden");
        }, 10, () -> true, 1);
        ZeldaHoofdstuk.Kamer k = h.kamers.stream().filter(x -> !x.alleenBel && !x.tips.isEmpty()).findFirst().orElseThrow();
        stap("tip: kamer " + k.id, () -> {
            stil();
            if (k.fase != ZeldaHoofdstuk.Fase.RUST) sp.herstel(k);
            p.teleport(k.start.midden(h.w).add(0, 1, 0));
        }, 10, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG, 40);
        stap("tip: dood", () -> {
            stil();
            p.setHealth(0);
        }, 3, () -> {
            if (p.isDead()) p.spigot().respawn();
            return !p.isDead();
        }, 40);
        stap("tip: Wiske", () -> { }, 4, () -> rij().contains(k.tips.get(0).tekst()), 40);
        stap("kaart", () -> {
            stil();
            sp.kaart(p);
            moet(p.getOpenInventory().getTopInventory().getHolder(false) instanceof ZeldaSpel.Menu, "kaart ging niet open");
            p.closeInventory();
        }, 2, () -> true, 1);
        stap("weg", () -> pl.naarHub(p), 10, () -> {
            var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            moet(sp.metgezel.van(p) == null && sp.dier.van(p) == null, "Wiske of huisdier bleef staan");
            moet(max != null && max.getValue() == 20, "harten na Zelda: " + (max == null ? "?" : max.getValue()));
            moet(sp.muziek.achter(p) == null, "muziek speelt door in de hub");
            return true;
        }, 1);
        stap("kamer terug", () -> { }, 20, () -> k.fase == ZeldaHoofdstuk.Fase.RUST && k.vijanden.isEmpty(), 200);
        stap("opruimen", () -> {
            z.spel.voortgang.wis(id);
            z.spel.bewaar();
        }, 1, () -> true, 1);
        start("kitpvp zelda test " + p.getName() + " motor");
    }

    private Material item(int vak) {
        var it = p.getInventory().getItem(vak);
        return it == null ? Material.AIR : it.getType();
    }

    /** Alles wat er nu op de actiebalk staat of nog komt, als platte tekst. */
    private String rij() {
        ZeldaSpel.Sessie ss = z.spel.sessie(p);
        if (ss == null) return "";
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        StringBuilder sb = new StringBuilder();
        if (ss.nu != null) sb.append(plain.serialize(ss.nu)).append(" | ");
        for (var c : ss.rij) sb.append(plain.serialize(c)).append(" | ");
        return sb.toString();
    }

    /** De verteller leeg (zodat een test kan praten). */
    private void stil() {
        ZeldaSpel.Sessie ss = z.spel.sessie(p);
        if (ss == null) return;
        ss.rij.clear();
        ss.nu = null;
        ss.tot = 0;
    }

    void verder() {
        stap("poort", this::doorDePoort, 4, () -> true, 1);
        stap("verder", () -> {
            ZeldaHoofdstuk h = z.spel.hoofdstuk(stand().hoofdstuk);
            int r = stand().rust(h.id());
            String[] pl = ZeldaRegels.plek(stand().laatste);                // laatste rustplaats gaat voor (oude stand: de verste)
            if (pl != null && z.spel.deel(pl[0]) != null) {
                h = z.spel.deel(pl[0]);
                r = Integer.parseInt(pl[1]);
            }
            Location l = h.rust.get(r).plek;
            moet(p.getLocation().distanceSquared(l) < 4, "niet op de laatste rustplaats " + h.rust.get(r).naam + " (" + xyz(l) + ") maar op " + xyz(p.getLocation()));
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
