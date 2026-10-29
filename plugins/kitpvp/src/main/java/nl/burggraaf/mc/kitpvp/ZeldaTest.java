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
import org.bukkit.event.player.PlayerInteractEvent;
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
 *
 * <p>Sinds 2.16: {@code test <speler> niveau} (de vier niveaus, {@link #niveau}), {@code test <speler> spellen [<speler2>]}
 * (meerdere spellen en samen spelen, {@link #spellen}), {@code test <speler> alles niveau <niveau>} (de route op een niveau) en
 * {@code test <speler> alles mee <speler2>} (co-op: de tweede speler neemt een samen-spel aan en loopt overal mee).
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
    private boolean hitteTest;                              // nu wordt de hitte getest (anders houdt de test je koel: hij teleporteert)
    private boolean sluipTest;                              // nu wordt er gesloten (anders zien de wachters je niet: de test teleporteert)
    private String geklikt;
    private Player mee;                                     // co-op: deze speler loopt mee in hetzelfde samen-spel (het script stuurt alleen p)
    private int stilte;                                     // hoe vaak er geen muziek was (de eerste drie worden gemeld)                                 // het eiland waarheen Brecht vaart na een klik in zijn menu

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
        route(alles, null, null, null);
    }

    void route(boolean alles, String vanaf) {
        route(alles, vanaf, null, null);
    }

    /**
     * Met {@code vanaf} (een deel-id, voor wie aan één deel werkt): alles ervoor telt als gedaan (vondsten, kamers, rustplaatsen,
     * puzzels, wat personen geven) en de route begint bij de aanleg van dat eiland. Alleen voor tests: zo hoef je niet elke keer
     * het hele spel door.
     */
    /**
     * Idem, op een {@code niveau} (null = Avontuur), en met {@code twee}: een tweede speler die meeloopt in hetzelfde samen-spel
     * (co-op: hij volgt p overal, ook op zee; aan het eind moet hij alles hebben wat p heeft).
     */
    void route(boolean alles, String vanaf, ZeldaRegels.Niveau niveau, Player twee) {
        if (p.isDead()) p.spigot().respawn();
        pl.naarHub(p);                                       // eerst uit Zelda, anders schrijft de rustplaats meteen weer
        z.spel.voortgang.wis(id);
        if (niveau != null) stand().niveau = niveau.ordinal();
        if (twee != null && !twee.equals(p)) {
            if (twee.isDead()) twee.spigot().respawn();
            pl.naarHub(twee);
            z.spel.voortgang.wis(twee.getUniqueId());
        }
        ZeldaHoofdstuk h = z.HOOFDSTUKKEN.get(0);
        ZeldaHoofdstuk begin = vanaf == null ? null : z.spel.deel(vanaf);
        if (vanaf != null && (begin == null || begin.eiland == null)) {
            aan.sendMessage("kitpvp zelda test: onbekend deel " + vanaf);
            return;
        }
        if (begin != null) {
            ZeldaVoortgang.Stand s = stand();
            for (ZeldaHoofdstuk d : z.HOOFDSTUKKEN) {
                if (d == begin) break;
                overslaan(s, d);
            }
            ZeldaEiland be = begin.eiland;
            s.laatste = be.delen().get(0).id() + ":" + be.aanlegRust().index;
            s.hoofdstuk = be.delen().get(0).id();
            s.bezoek(be.delen().get(0).id(), be.aanlegRust().index);
            z.spel.voortgang.vuil();
        }
        stap("poort", () -> {
            doorDePoort();
            Location r0 = begin != null ? begin.eiland.aanlegPlek() : h.rust.get(0).plek;
            moet(p.getLocation().distanceSquared(r0) < 4, "niet bij het begin: " + xyz(p.getLocation()) + " (verwacht " + xyz(r0) + ")");
            moet(begin != null || p.getInventory().getItem(0) != null && p.getInventory().getItem(0).getType() == Material.WOODEN_SWORD, "geen houten zwaard");
            moet(p.getInventory().getItem(8) != null && p.getInventory().getItem(8).getType() == Material.FILLED_MAP, "geen kaart");
        }, 2, () -> true, 1);
        if (twee != null && !twee.equals(p)) stap("mee: " + twee.getName(), () -> {      // hij neemt de uitnodiging aan: een samen-spel
            pl.kiesModus(twee, z);
            String fout = z.spel.spellen.samen(twee.getUniqueId(), id);
            moet(fout == null, "mee: " + fout);
            mee = twee;
        }, 2, () -> mee == null || z.spel.speelt(mee) && stand().groep != null && stand().groep.equals(z.spel.voortgang.van(mee.getUniqueId()).groep), 40);
        // alle eilanden in de volgorde van het verhaal: varen (met Brecht: klik in zijn menu), elk deel aflopen, wat nu te
        // halen is (een bom tegen een gebarsten muur), de zijmissies (zonder alles: tot na de eerste kamer)
        eilanden:
        for (int i = 0; i < z.EILANDEN.size(); i++) {
            ZeldaEiland e = z.EILANDEN.get(i), volgende = i + 1 < z.EILANDEN.size() ? z.EILANDEN.get(i + 1) : null;
            if (begin != null && i < z.EILANDEN.indexOf(begin.eiland)) continue;      // vanaf een deel: de eilanden ervoor zijn gedaan
            if (e != h.eiland && (begin == null || e != begin.eiland)) stap("varen naar " + e.id(), () -> {
                moet(e.open(stand()), e.id() + " is niet open na het vorige eiland (" + e.openNa() + ")");
                moet(!e.id().equals(geklikt) || z.spel.sessie(p).vaart != null, "Brecht vaart niet na een klik op " + e.naam());
                if (z.spel.sessie(p).vaart == null) {
                    stil();
                    z.spel.vaar(p, e);
                }
                kaartOpZee(e);
            }, 20, () -> z.spel.sessie(p).vaart == null && z.spel.sessie(p).h.eiland == e, 600);
            for (ZeldaHoofdstuk d : e.delen()) {
                if (begin != null && e == begin.eiland && e.delen().indexOf(d) < e.delen().indexOf(begin)) continue;
                if (!pad(d, alles, d == h)) break eilanden;
                stap("einde " + d.id(), () -> { }, 2, () -> stand().heeft(d.id()), 20);
            }
            stap("belopen " + e.id(), () -> {                                         // alles te halen zonder te teleporteren?
                java.util.Set<String> vw = new java.util.HashSet<>();
                for (ZeldaVoorwerp v : z.spel.voorwerpen) vw.add(v.vlag());
                for (String f : ZeldaLopen.onbereikbaar(e, vw)) fouten.add("niet te belopen: " + f);
            }, 2, () -> true, 1);
            stap("kaart " + e.id() + " gemeten", () -> { }, 2, () -> z.spel.landkaart.bladen.containsKey(e.id()), 2400);
            stap("kaart " + e.id(), () -> kaartKlopt(e), 2, () -> true, 1);
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
        // het einde: Story of Zelda voltooid (de klaar-vlag van het laatste eiland), en de kaart zegt het
        if (alles) stap("voltooid", () -> {
            stil();
            ZeldaEiland laatste = z.EILANDEN.get(z.EILANDEN.size() - 1);
            moet(z.spel.voltooid(stand()), "niet voltooid: geen vlag " + laatste.klaar());
            z.spel.kaart(p);
            moet(menuHeeft("niets") && p.getOpenInventory().getTopInventory().getItem(31) != null
                    && p.getOpenInventory().getTopInventory().getItem(31).getType() == Material.NETHER_STAR, "de kaart zegt niet «voltooid»");
            p.closeInventory();
        }, 2, () -> true, 1);
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
        // boodschappen over zee (Hanne vraagt zeekraal van het Zilverstrand, later maanmos van Nachtrots): drie rondes langs alle
        // eilanden (een opdracht van Hanne begint pas als de vorige klaar is), dan alles klaar
        List<ZeldaEiland> rond = begin == null ? z.EILANDEN : List.of(z.EILANDEN.get(0), begin.eiland);   // vanaf een deel: alleen dat eiland en thuis
        if (alles && z.EILANDEN.size() > 1) {
            for (int ronde = 0; ronde < 4; ronde++)                     // (na het feest: het kroontje, dan Zelda's brieven)
                for (ZeldaEiland e : rond) {
                    stap("boodschap: naar " + e.id(), () -> {
                        stil();
                        if (z.spel.sessie(p).h.eiland == e) return;
                        z.spel.kaart(p);
                        moet(klik("eiland:" + e.id()), "kaart: " + e.naam() + " staat er niet op");
                    }, 20, () -> z.spel.sessie(p).vaart == null && z.spel.sessie(p).h.eiland == e, 600);
                    zijmissies(e);
                }
        }
        // later terug: met een nieuw voorwerp (bommen, de klauwhaak) naar een eerder eiland varen (via de kaart) en daar halen
        // wat nu kan (na de boodschappen over zee: anders noemt de hint eerst een opdracht die nog loopt)
        if (alles) for (ZeldaEiland e : rond) {
            List<ZeldaHoofdstuk.Vondst> nu = new ArrayList<>();
            for (ZeldaHoofdstuk d : e.delen())
                for (ZeldaHoofdstuk.Vondst v : d.vondsten) if (v.voorwerp != null && z.spel.voorwerpen.stream().anyMatch(x -> x.vlag().equals(v.voorwerp))) nu.add(v);
            if (nu.isEmpty() || e == z.EILANDEN.get(z.EILANDEN.size() - 1)) continue;
            if (begin == null) stap("hint: later naar " + e.id(), () -> {                 // (vanaf een deel: de eilanden ertussen zijn overgeslagen)                                 // Wiske weet dat je daar nu iets kunt halen
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
        if (alles && z.EILANDEN.size() > 1) for (ZeldaEiland e : begin == null ? z.EILANDEN : List.of(begin.eiland)) klaar(e, true);
        if (alles && begin == null) stap("hint: hartstukjes", () -> {             // alles gevonden: dank je wel; een stukje kwijt: Wiske weet waar
            stil();
            ZeldaSpel.Sessie ss = z.spel.sessie(p);
            ss.hintWacht = 0;
            z.spel.hint(p);
            moet(rij().contains("Dank je wel"), "hint na alles: " + rij());
            stil();
            ZeldaEiland eerste = z.EILANDEN.get(0), hier = ss.h.eiland;
            String weg = eerste.delen().get(0).stukjes.iterator().next();
            stand().wis(weg);
            ss.hintWacht = 0;
            z.spel.hint(p);
            moet(rij().contains(hier == eerste ? "Hier" : ZeldaRegels.inZin(eerste.naam())), "hint wijst niet naar het hartstukje " + weg + ": " + rij());
            ZeldaSpel.Wijzer w = z.spel.wijzer(hier, stand(), p.getLocation().getX(), p.getLocation().getZ());
            moet(hier == eerste ? w.plek() != null || w.stukje() != null : w.eiland() == eerste, "de kaart zet geen X bij het hartstukje");
            stand().zet(weg);
            stil();
        }, 2, () -> true, 1);
        if (twee != null && !twee.equals(p)) stap("mee: alles samen", () -> {   // co-op: wat p heeft, heeft de ander ook
            ZeldaVoortgang.Stand a = stand(), b = z.spel.voortgang.van(twee.getUniqueId());
            moet(a.groep != null && a.groep.equals(b.groep), "mee: niet meer in hetzelfde samen-spel");
            List<String> mist = new ArrayList<>();
            for (String v : a.vlaggen) if (ZeldaVoortgang.deelbaar(v) && !b.heeft(v)) mist.add(v);
            moet(mist.isEmpty(), "mee mist " + mist.size() + " vlaggen: " + mist.subList(0, Math.min(8, mist.size())));
            moet(ZeldaRegels.harten(a) == ZeldaRegels.harten(b) && ZeldaRegels.harnas(a) == ZeldaRegels.harnas(b), "mee: andere harten of harnas");
            var max = twee.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            moet(max != null && max.getValue() == ZeldaRegels.harten(b) * 2, "mee: zijn harten zijn niet bijgewerkt (" + (max == null ? "?" : max.getValue() / 2) + ")");
            moet(!alles || b.heeft(z.EILANDEN.get(z.EILANDEN.size() - 1).klaar()), "mee: het verhaal is voor hem niet uit");
            gedaan.add("mee: " + b.vlaggen.size() + " vlaggen samen");
        }, 30, () -> true, 1);
        stap("bewaren", () -> { }, 2, () -> !z.spel.voortgang.isVuil(), 260);   // hoogstens 10 s: dan bewaart het spel vanzelf
        stap("opslag", () -> {
            ZeldaVoortgang.Stand s = stand();
            ZeldaVoortgang schijf = new ZeldaVoortgang(new File(pl.getDataFolder(), "zelda.yml"));
            moet(schijf.van(id).rust(h.id()) == s.rust(h.id()) && s.rust(h.id()) > 0,
                    "zelda.yml: rustplaats " + schijf.van(id).rust(h.id()) + " (in het geheugen " + s.rust(h.id()) + ")");
            moet(schijf.van(id).vlaggen.containsAll(s.vlaggen) || !z.spel.voortgang.isVuil(), "zelda.yml: vlaggen niet bewaard");
        }, 2, () -> true, 1);
        start("kitpvp zelda test " + p.getName() + (alles ? " alles" : "") + (niveau != null ? " niveau " + niveau.naam.toLowerCase() : "")
                + (twee != null ? " mee " + twee.getName() : ""));
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
                ZeldaEiland thuis = h.eiland, daar = eilandVan(pe);
                boolean over = daar != null && thuis != null && daar != thuis;   // iemand op een ander eiland (Mira, 5a): eerst varen
                if (over) stap("varen naar " + daar.id() + " (" + pe.naam + ")", () -> {
                    stil();
                    z.spel.kaart(p);
                    moet(klik("eiland:" + daar.id()), "kaart: " + daar.naam() + " staat er niet op");
                }, 20, () -> z.spel.sessie(p).vaart == null && z.spel.sessie(p).h.eiland == daar, 600);
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
                stap("naam? (" + pe.naam + ")", () -> { }, 2, () -> {                     // kreeg je een dier: kies zijn naam in het menu
                    if (!ZeldaDier.actief(ZeldaDier.soort(stand())) || stand().dier != null) return true;
                    if (menuHeeft("naam:1")) klik("naam:1");
                    return stand().dier != null;
                }, 400);
                if (over) stap("terug naar " + thuis.id(), () -> {
                    stil();
                    z.spel.kaart(p);
                    moet(klik("eiland:" + thuis.id()), "kaart: " + thuis.naam() + " staat er niet op");
                }, 20, () -> z.spel.sessie(p).vaart == null && z.spel.sessie(p).h.eiland == thuis, 600);
            } else if (o instanceof ZeldaHoofdstuk.Haak hk) {                 // de klauwhaak: een echte haak vanaf de plek ervoor
                stap("haak " + hk.hp().id(), () -> haak(hk.hp(), hk.van()), 4,
                        () -> !z.spel.aanHetHaken(p) && p.getLocation().distanceSquared(hk.hp().landing()) < 2, 80);
            } else if (o instanceof ZeldaHoofdstuk.Graaf gr) {                // je dier graaft iets op (het moet meelopen)
                stap("graaf " + gr.id(), () -> {
                    moet(z.spel.dier.van(p) != null, "graafplek " + gr.id() + ": geen dier dat meeloopt");
                    p.teleport(vrij(gr.plek().clone().add(0, 0, 4)));
                }, 10, () -> stand().heeft(gr.vlag()), 400);
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
                    p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
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
                    p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
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
                if (k.baas instanceof Grimmel gr) stap(gr.naam + " tegen een zuil", () -> {         // hij stormt, jij haakt je weg, hij loopt tegen een zuil
                    moet(stand().heeft("klauwhaak"), "geen klauwhaak voor " + gr.naam);
                    var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    if (max != null) p.setHealth(max.getValue());
                    Location zu = gr.zuil(0), mid = k.start.midden(h.w);
                    double y = gr.grond.get(0).getY();
                    org.bukkit.util.Vector as = mid.toVector().subtract(zu.toVector()).setY(0).normalize();   // van de zuil naar het midden van de zaal
                    Location rand = new Location(h.w, zu.getX(), y, zu.getZ()).add(as.clone().multiply(1.6));
                    Location staan = rand.clone().add(as.clone().multiply(2.0)), beer = rand.clone().add(as.clone().multiply(8.0));
                    beer.setDirection(as.clone().multiply(-1));
                    gr.ent.teleport(beer);
                    staan.setDirection(as.clone().multiply(-1));
                    p.teleport(staan);
                    gr.storm(z.spel, staan.clone());
                    ZeldaHoofdstuk.Haakpunt weg = null;                                 // een haakpunt opzij (niet waar hij heen stormt)
                    for (ZeldaHoofdstuk.Haakpunt hp : h.haakpunten) {
                        if (!k.binnen.in(hp.landing())) continue;
                        org.bukkit.util.Vector v = hp.landing().toVector().subtract(staan.toVector()).setY(0);
                        double opzij = v.clone().subtract(as.clone().multiply(v.dot(as))).length();
                        if (opzij >= 4 && z.spel.zicht(staan.clone().add(0, p.getEyeHeight(), 0), hp) && (weg == null || hp.landing().distanceSquared(staan) < weg.landing().distanceSquared(staan))) weg = hp;
                    }
                    if (weg == null) fouten.add(gr.naam + ": geen haakpunt opzij om je weg te trekken");
                    else haak(weg, staan);
                }, 60, () -> gr.versuft > 0 && !gr.heel(0), 120);
                if (k.baas instanceof Grimmel gr) stap(gr.naam + " duizelt", () -> {
                    moet(gr.zuilRaak >= 1, gr.naam + ": geen zuil geraakt");
                    moet(h.w.getBlockAt(gr.zuilen.get(0)[0], gr.zuilen.get(0)[4], gr.zuilen.get(0)[1]).getType() == Material.AIR, "de zuil is niet gebroken");
                    moet(gr.schade(10) > 10, gr.naam + ": duizelig doet een klap niet meer pijn");
                    var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    moet(max != null && p.getHealth() >= max.getValue() - 0.01, gr.naam + ": je werd toch geraakt (" + p.getHealth() + ")");
                }, 2, () -> true, 1);
                if (k.baas instanceof Asgrim ag) stap(ag.naam + " gedoofd", () -> {             // een echte pijl op de kuip boven hem
                    moet(stand().heeft("boog"), "geen boog voor " + ag.naam);
                    var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    if (max != null) p.setHealth(max.getValue());
                    ag.zetBij(0);
                    Location staan = ag.landing.get(0).clone();
                    org.bukkit.util.Vector naar = ag.kuip(0).toVector().subtract(staan.toVector().add(new org.bukkit.util.Vector(0, p.getEyeHeight(), 0)));
                    staan.setDirection(naar);
                    p.teleport(staan);
                    p.launchProjectile(org.bukkit.entity.Arrow.class, naar.normalize().multiply(3.0));
                }, 30, () -> ag.gedoofd > 0, 60);
                if (k.baas instanceof Asgrim ag) stap(ag.naam + " gedoofd klopt", () -> {
                    moet(!ag.vol(0), ag.naam + ": de kuip is niet gekanteld");
                    moet(ag.dovingen >= 1 && ag.schade(10) >= 10, ag.naam + ": gedoofd doet een klap niet meer pijn");
                    moet(ag.landing.get(0).distanceSquared(ag.ent.getLocation()) < 4, ag.naam + ": niet neergeploft op de rand");
                }, 2, () -> true, 1);
                if (k.baas instanceof Morgal mg) morgal(h, k, mg);
                if (k.baas != null && !(k.baas instanceof Morgal)) stap("baas " + k.baas.naam, () -> {
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
                    moet(m == k.uit.open.getMaterial(), "kamer " + k.id + ": uitgang is " + m);
                    moet(z.spel.muziek.lus(p) == null, "kamer " + k.id + ": muziek speelt nog");
                }, 2, () -> true, 1);
                if (alles && k.bel != null && !belGehad) {                    // terugkomen: vrije doorgang, en de bel voor wie nog eens wil
                    belGehad = true;
                    stap("kamer " + k.id + " vrije doorgang", () -> {
                        z.spel.herstel(k);
                        p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
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
                        p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
                    }, 10, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG && !k.terug, 40);
                    stap("kamer " + k.id + " nieuw erbij verslaan", () -> { }, 5, () -> verslaan(h, k) && stand().heeft("k:" + k.id), 900);
                }
            } else if (o instanceof ZeldaHoofdstuk.Uitdaging u) {
                stap("uitdaging " + u.id + " dicht", () -> p.teleport(vrij(u.start.midden(h.w).add(-3, 1, 0))), 20,
                        () -> !u.poort.isOpen && h.w.getBlockAt(u.poort.blokken.get(0)[0], u.poort.blokken.get(0)[1], u.poort.blokken.get(0)[2]).getType() == u.poort.dicht.getMaterial(), 80);
                stap("uitdaging " + u.id + " start", () -> p.teleport(vrij(u.start.midden(h.w).add(0, 1, 0))), 8,
                        () -> u.loopt.containsKey(id) && u.poort.isOpen, 40);
                if (u.via != null) {                                                     // eerst de finish zonder de weg erlangs: telt niet
                    stap("uitdaging " + u.id + " niet om", () -> p.teleport(vrij(u.finish.midden(h.w).add(0, 1, 0))), 8, () -> {
                        moet(stand().beste(u.id) == null, "uitdaging " + u.id + ": gehaald zonder erlangs te gaan");
                        return true;
                    }, 1);
                    stap("uitdaging " + u.id + " erlangs", () -> p.teleport(u.via.midden(h.w).add(0, 0.5, 0)), 8, () -> u.erdoor.contains(id), 40);
                }
                stap("uitdaging " + u.id + " finish", () -> p.teleport(vrij(u.finish.midden(h.w).add(0, 1, 0))), 8, () -> stand().beste(u.id) != null, 40);
            } else if (o instanceof ZeldaHoofdstuk.Barst b) {                       // een gebarsten muur op het pad: een echte bom
                stap("bom op " + b.id, () -> {
                    moet(stand().heeft("bommen"), "geen bommen voor de gebarsten muur " + b.id);
                    if (b.muur.isOpen) z.spel.herstel(b);
                    gooi(h, b);
                }, 50, () -> b.muur.isOpen && stand().heeft("b:" + b.id), 60);
            } else if (o instanceof ZeldaHoofdstuk.Glij gl) {                      // een glijvloer: de oplossing (de eerste zet is een echte stap op het ijs)
                final int[] zet = {0};
                stap("glij " + gl.id, () -> {
                    moet(gl.oplossing.size() > 0, "glij " + gl.id + ": geen oplossing");
                    p.teleport(new Location(h.w, gl.start[0] + 0.5, gl.y + 1, gl.start[1] + 0.5, p.getLocation().getYaw(), 0f));
                }, 6, () -> {
                    if (z.spel.glijdt(p)) return false;
                    if (zet[0] >= gl.oplossing.size()) return true;
                    org.bukkit.block.BlockFace r = gl.oplossing.get(zet[0]++);
                    if (zet[0] == 1) {                                                      // een stap het ijs op: dan glij je vanzelf
                        Location l = p.getLocation();
                        p.teleport(new Location(h.w, l.getBlockX() + r.getModX() + 0.5, gl.y + 1, l.getBlockZ() + r.getModZ() + 0.5, l.getYaw(), 0f));
                    } else moet(z.spel.glij(p, gl, r), "glij " + gl.id + ": zet " + zet[0] + " (" + r + ") kan niet vanaf " + xyz(p.getLocation()));
                    return false;
                }, 600);
                stap("glij " + gl.id + " aan de overkant", () -> moet(p.getLocation().getBlockX() == gl.doel[0] && p.getLocation().getBlockZ() == gl.doel[1],
                        "glij " + gl.id + ": niet bij het doel " + gl.doel[0] + "," + gl.doel[1] + " maar op " + xyz(p.getLocation())), 4, () -> true, 1);
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
            } else if (o instanceof ZeldaHoofdstuk.Reeks r && r.hoe == ZeldaHoofdstuk.Reeks.Hoe.VUUR) {   // vuurschalen op afstand: echte vlammen
                final int[] i = {0};
                stap("vuurpuzzel " + r.id, () -> {
                    moet(stand().heeft("vuurstaf"), "geen vuurstaf voor de puzzel " + r.id);
                    if (r.opgelost || !r.isAan.isEmpty()) z.spel.herstel(r);
                    i[0] = 0;
                    p.teleport(r.voor != null ? r.voor : vrij(r.gebied.midden(h.w).add(0, 1, 0)));
                }, 4, () -> {
                    if (z.spel.vlamOnderweg(p)) return false;
                    if (r.opgelost) return r.deur.isOpen && stand().heeft("r:" + r.id);
                    if (i[0] >= r.dingen.size()) {
                        fouten.add("vuurpuzzel " + r.id + ": alles geraakt, maar niet opgelost (" + r.isAan.size() + " van " + r.dingen.size() + " aan)");
                        return true;
                    }
                    int[] c = r.dingen.get(i[0]++);
                    vuur(h.w.getBlockAt(c[0], c[1], c[2]));
                    return false;
                }, 200);
            } else if (o instanceof ZeldaHoofdstuk.Smelt sm) {                     // betoverd ijs op het pad: een echte vlam van de vuurstaf
                stap("smelt " + sm.id, () -> {
                    moet(stand().heeft("vuurstaf"), "geen vuurstaf voor het ijs " + sm.id);
                    if (sm.ijs.isOpen) z.spel.herstel(sm);
                    smelt(h, sm);
                }, 10, () -> sm.ijs.isOpen && stand().heeft("s:" + sm.id) && !z.spel.vlamOnderweg(p), 60);
            } else if (o instanceof ZeldaHoofdstuk.Ritme rt) {                     // lava op een ritme: hij stijgt en zakt; wie erin stapt, springt eruit
                final int[] keren = {0};
                final double[] leven = {0};
                Location voor = rt.voor != null ? rt.voor : vrij(rt.gebied.midden(h.w).add(0, 1, 0));
                stap("ritme " + rt.id, () -> {
                    p.teleport(voor);
                    keren[0] = rt.keren;
                }, 10, () -> rt.keren > keren[0] && !rt.isHoog, 300);
                stap("ritme " + rt.id + ": in de lava", () -> { }, 2, () -> {
                    if (!rt.isHoog) return false;
                    var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    if (max != null) p.setHealth(max.getValue());
                    leven[0] = p.getHealth();
                    int[] c = rt.lava.blokken.get(0);
                    p.teleport(new Location(h.w, c[0] + 0.5, c[1] + 1, c[2] + 0.5, voor.getYaw(), 0f));
                    return true;
                }, 400);
                stap("ritme " + rt.id + ": eruit", () -> { }, 30, () -> {
                    moet(!p.isDead() && p.getLocation().distanceSquared(voor) < 9, "ritme " + rt.id + ": niet uit de lava gesprongen (op " + xyz(p.getLocation()) + ")");
                    moet(p.getHealth() >= leven[0] - ZeldaSpel.LAVA_SCHADE - 0.01 && p.getFireTicks() <= 0, "ritme " + rt.id + ": de lava deed te veel (" + p.getHealth() + ")");
                    return true;
                }, 1);
            } else if (o instanceof ZeldaHoofdstuk.Hitte ht) {                     // hitte: warm worden, terug, de koelsteen, afkoelen, het gloedharnas
                hitte(h, ht);
            } else if (o instanceof ZeldaHoofdstuk.Nevel nv) {                     // Morgals nevel op het pad: een echte lichtpijl
                stap("nevel " + nv.id, () -> {
                    moet(stand().heeft("lichtpijlen"), "geen lichtpijlen voor de nevel " + nv.id);
                    if (nv.nevel.isOpen) z.spel.herstel(nv);
                    licht(h, nv);
                }, 10, () -> nv.nevel.isOpen && stand().heeft("n:" + nv.id), 60);
            } else if (o instanceof ZeldaHoofdstuk.Sluip sl) {                     // sluipen: eerst gezien worden, dan echt langs de wachters
                sluipen(h, sl);
            } else if (o instanceof ZeldaHoofdstuk.Val v) {                        // een vangnet: van hoog vallen, de nevel vangt je op (geen schade)
                final int[] voor = {0};
                stap("vangnet " + v.id(), () -> {
                    heel();
                    voor[0] = z.spel.sessie(p).opgevangen;
                    p.setVelocity(new org.bukkit.util.Vector());
                    p.teleport(v.proef());
                }, 4, () -> {
                    if (!((org.bukkit.entity.Entity) p).isOnGround()) p.setFallDistance(Math.max(p.getFallDistance(), 6f));   // zoals een speler die valt (de testspeler valt niet vanzelf)
                    return z.spel.sessie(p).opgevangen > voor[0] || ((org.bukkit.entity.Entity) p).isOnGround();
                }, 200);
                stap("vangnet " + v.id() + " zacht", () -> {
                    p.teleport(v.proef());                                              // en wie toch landt: vallen doet daar geen pijn
                    var val = valSchade();
                    Bukkit.getPluginManager().callEvent(val);
                    moet(val.isCancelled(), "vangnet " + v.id() + ": vallen doet daar toch pijn");
                    p.teleport(z.spel.sessie(p).veilig != null ? z.spel.sessie(p).veilig : z.spel.sessie(p).rustplaats().plek);
                    var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    moet(max != null && p.getHealth() >= max.getValue() - 0.01, "vangnet " + v.id() + ": vallen deed pijn (" + p.getHealth() + ")");
                    moet(z.spel.sessie(p).opgevangen > voor[0], "vangnet " + v.id() + ": de nevel ving je niet op (op " + xyz(p.getLocation()) + ")");
                }, 4, () -> true, 1);
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

    /**
     * Morgal (5b), drie fases zoals een speler: zijn geesten verslaan (echte klappen), een duistere bol terugslaan (een echte zwaai,
     * vlak voor de bol), versuft = een klap doet alles; dan onder de helft naar zijn nevelhart, en een echte lichtpijl erin. Daarna
     * verslaat de gewone stap «verslaan» hem.
     */
    private void morgal(ZeldaHoofdstuk h, ZeldaHoofdstuk.Kamer k, Morgal mg) {
        stap(mg.naam + ": geesten", this::heel, 20, () -> {
            if (mg.fase != ZeldaRegels.MORGAL_GEESTEN) return true;
            moet(mg.schade(10, p) < 1, mg.naam + ": zijn nevel houdt een klap niet tegen (fase 1)");
            for (LivingEntity g : new ArrayList<>(mg.geesten)) if (g.isValid()) g.damage(10_000, p);
            return false;
        }, 1200);
        stap(mg.naam + ": even adem", this::heel, 2, () -> mg.fase == ZeldaRegels.MORGAL_BOLLEN, 400);
        stap(mg.naam + ": een bol terugslaan", this::heel, 4, () -> {
            if (mg.versuft > 0) return true;
            Morgal.Bol b = null;
            for (Morgal.Bol x : mg.bollen) if (!x.terug && b == null && x.leeftijd > 2) b = x;
            if (b == null) return false;
            org.bukkit.util.Vector r = b.stap.clone().normalize();
            Location oog = b.plek.clone().add(r.clone().multiply(2.2));
            Location staan = oog.clone().subtract(0, p.getEyeHeight(), 0);
            staan.setDirection(b.plek.toVector().subtract(oog.toVector()));
            p.setVelocity(new org.bukkit.util.Vector());
            p.teleport(staan);
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerAnimationEvent(p, org.bukkit.event.player.PlayerAnimationType.ARM_SWING));   // een echte zwaai (via de listener)
            return false;
        }, 900);
        stap(mg.naam + ": versuft", () -> {
            moet(mg.teruggeslagen >= 1, mg.naam + ": geen bol teruggeslagen");
            moet(mg.schade(10, p) >= 9.99, mg.naam + ": versuft doet een klap niet alles");
            var max = mg.ent.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (max != null) mg.ent.setHealth(max.getValue() * 0.49);              // de rest van fase 2 slaan we over
            heel();
        }, 2, () -> true, 1);
        stap(mg.naam + ": naar zijn nevelhart", this::heel, 2, () -> mg.fase == ZeldaRegels.MORGAL_HART, 400);
        final int[] schot = {0};
        stap(mg.naam + ": licht in zijn nevelhart", () -> {
            heel();
            moet(mg.schade(10, p) < 2, mg.naam + ": in zijn nevelhart doet een zwaard toch veel");
        }, 2, () -> {
            if (mg.hartTreffers >= 1) return true;
            if (schot[0]++ % 20 != 0) return false;
            Location m = mg.midden();
            Location staan = vrij(new Location(h.w, m.getX() - 5, k.start.y1(), m.getZ()));
            staan.setDirection(m.toVector().subtract(staan.clone().add(0, p.getEyeHeight(), 0).toVector()));
            p.setVelocity(new org.bukkit.util.Vector());
            p.teleport(staan);
            p.setCooldown(ZeldaSpel.Lichtpijlen.ITEM, 0);
            z.spel.voorwerp("lichtpijlen").gebruik(z.spel, p, null);
            return false;
        }, 600);
        stap(mg.naam + ": geraakt", () -> moet(mg.hartTreffers >= 1 && mg.leeft(), mg.naam + ": de lichtpijl deed niets"), 2, () -> true, 1);
    }

    /** Vallen (zoals de server het meldt): voor de test van het vangnet. */
    @SuppressWarnings("removal")
    private org.bukkit.event.entity.EntityDamageEvent valSchade() {
        return new org.bukkit.event.entity.EntityDamageEvent(p, org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 6);
    }

    /** Helemaal beter (de test vecht lang). */
    private void heel() {
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (max != null && !p.isDead()) p.setHealth(max.getValue());
    }

    /** Wat personen op een deel geven (in een gesprek, geen vondst): voor {@link #overslaan}. */
    private static final java.util.Map<String, List<String>> GAVEN = java.util.Map.of(
            "h1", List.of("schild", "fles2", "lichtwoord", ZeldaRegels.HELM),
            "e2a", List.of("harnas1"),
            "e2b", List.of("maanparel", "ankersleutel"),
            "e3a", List.of("sterrenlantaarn"),
            "e3b", List.of("zonnesteen", "harnas2"),
            "e4a", List.of("koelsteen", "harnas3"),
            "e4b", List.of(ZeldaSpel.GLOEDSCHILD),
            "e5a", List.of(ZeldaEiland5a.MUURSLEUTEL, ZeldaEiland5a.WACHTWOORD, ZeldaEiland5a.WOLF_VRIJ, "harnas4"));

    /** Gesprekken op eerdere delen die pas met de toren (5b) komen: die blijven nieuw als je {@link #overslaan}. */
    private static final java.util.Set<String> NA_DE_TOREN = java.util.Set.of("tijmen-zegel", "brecht-zelda", "zelda-kreek", "wolf-zelda");

    /** Een deel overslaan (test vanaf een later deel): alsof je het speelde. */
    private void overslaan(ZeldaVoortgang.Stand s, ZeldaHoofdstuk d) {
        s.zet(d.id());
        if (d.eiland != null) s.zet("e:" + d.eiland.id());
        for (ZeldaHoofdstuk.Vondst v : d.vondsten) if (v.voorwerp == null && (v.vereist == null || !v.vereist.startsWith("g:"))) s.zet(v.vlag);
        for (ZeldaHoofdstuk.Kamer k : d.kamers) s.zet("k:" + k.id);
        for (ZeldaHoofdstuk.Rust r : d.rust) {
            s.bezoek(d.id(), r.index);
            s.bereik(d.id(), r.index);
        }
        for (ZeldaHoofdstuk.Verhaal v : d.verhalen) s.zet("v:" + v.id);
        for (ZeldaHoofdstuk.Reeks r : d.reeksen) s.zet("r:" + r.id);
        for (ZeldaHoofdstuk.Spiegels sp : d.spiegels) s.zet("r:" + sp.id);
        for (ZeldaHoofdstuk.Barst b : d.barsten) s.zet("b:" + b.id);
        for (ZeldaHoofdstuk.Nevel n : d.nevels) s.zet("n:" + n.id);
        for (ZeldaHoofdstuk.Sluip sl : d.sluipen) s.zet("l:" + sl.id);
        for (String g : GAVEN.getOrDefault(d.id(), List.of())) s.zet(g);
        for (String st : d.stukjes) s.zet(st);
        for (ZeldaHoofdstuk.Missie m : d.missies) if (!m.id().equals(ZeldaFeest.SCHAT) && !m.id().equals(ZeldaFeest.BRIEVEN)) s.missies.put(m.id(), ZeldaVoortgang.KLAAR);   // de schat en de brieven zijn na het einde (5b)
        for (ZeldaHoofdstuk.Persoon pe : d.personen) for (ZeldaHoofdstuk.Gesprek g : pe.gesprekken)                                   // wat ze toen zeiden
            if (g.id() != null && !NA_DE_TOREN.contains(g.id()) && !g.id().startsWith("na-") && !g.id().startsWith("feest-") && !g.id().startsWith("zelda-")) s.zet("g:" + g.id());
        ZeldaRegels.vulFlessen(s);
    }

    /** Het eiland waar deze persoon staat (null = op geen enkel eiland, bv. een proefpersoon). */
    private ZeldaEiland eilandVan(ZeldaHoofdstuk.Persoon pe) {
        for (ZeldaEiland e : z.EILANDEN) for (ZeldaHoofdstuk d : e.delen()) if (d.personen.contains(pe)) return e;
        return null;
    }

    /** Schiet een echte lichtpijl (het voorwerp, zoals rechtsklik) op de nevel: vanaf de plek ervoor, op een blok dat je ziet. */
    private void licht(ZeldaHoofdstuk h, ZeldaHoofdstuk.Nevel nv) {
        Location van = nv.voor;
        if (nv.brug) {                                                          // een nevelbrug: door de nevel heen schieten (hij raakt niets)
            if (van == null) {
                fouten.add("nevelbrug " + nv.id + ": geen plek om erop te schieten");
                return;
            }
            Location l = van.clone(), m = nv.nevel.omvang().midden(h.w).add(0, 0.6, 0);
            l.setDirection(m.toVector().subtract(l.clone().add(0, p.getEyeHeight(), 0).toVector()));
            p.setVelocity(new org.bukkit.util.Vector());
            p.teleport(l);
            p.setCooldown(ZeldaSpel.Lichtpijlen.ITEM, 0);
            z.spel.voorwerp("lichtpijlen").gebruik(z.spel, p, null);
            return;
        }
        if (van == null)
            for (int[] c : nv.nevel.blokken) {
                van = vanwaar(h.w.getBlockAt(c[0], c[1], c[2]));
                if (van != null) break;
            }
        if (van == null) {
            fouten.add("nevel " + nv.id + ": nergens een plek om hem te zien");
            return;
        }
        org.bukkit.block.Block doel = null;
        for (int[] c : nv.nevel.blokken)
            if (doel == null && z.spel.zicht(van.clone().add(0, p.getEyeHeight(), 0), h.w.getBlockAt(c[0], c[1], c[2]))) doel = h.w.getBlockAt(c[0], c[1], c[2]);
        if (doel == null) {
            fouten.add("nevel " + nv.id + ": niet te zien vanaf " + xyz(van));
            return;
        }
        Location l = van.clone();
        Location oog = l.clone().add(0, p.getEyeHeight(), 0);
        l.setDirection(doel.getLocation().add(0.5, 0.75, 0.5).toVector().subtract(oog.toVector()));
        p.setVelocity(new org.bukkit.util.Vector());
        p.teleport(l);
        p.setCooldown(ZeldaSpel.Lichtpijlen.ITEM, 0);
        z.spel.voorwerp("lichtpijlen").gebruik(z.spel, p, null);
    }

    /**
     * Sluipen: eerst wordt de test gezien (in de lichtkegel van een wachter: terug bij het zielvuur, het hek even dicht,
     * Wiske zegt iets), dan sluipt hij écht: van schuilplek naar schuilplek over het pad, en hij gaat pas als hij weet dat
     * niemand hem onderweg ziet (het ritme van de wachters ligt vast). Zo blijkt dat het kan.
     */
    private void sluipen(ZeldaHoofdstuk h, ZeldaHoofdstuk.Sluip sl) {
        final Location[] proef = {null};
        stap("sluipen " + sl.id + ": gezien", () -> {
            sluipTest = true;
            z.spel.sessie(p).ongezien = false;
            z.spel.sessie(p).gezienTot = 0;
            stil();
            p.teleport(sl.pad.get(0));
        }, 6, () -> {
            if (sl.begin == 0) return false;                                       // het ritme loopt pas als er iemand is
            if (proef[0] == null) {                                                // een plek vóór een wachter (die niet net draait)
                long nu = System.currentTimeMillis();
                for (ZeldaHoofdstuk.Sluip.Wachter wa : sl.wachters) {
                    ZeldaRegels.Pose po = z.spel.stand(sl, wa, nu);
                    double kx = -Math.sin(Math.toRadians(po.yaw())), kz = Math.cos(Math.toRadians(po.yaw()));
                    for (double d = 2; d <= wa.bereik - 1 && proef[0] == null; d += 0.5) {
                        Location l = new Location(h.w, Math.floor(po.x() + kx * d) + 0.5, Math.floor(po.y()), Math.floor(po.z() + kz * d) + 0.5);
                        if (staan(l) && z.spel.ziet(wa, po, l.getX(), l.getY(), l.getZ()) && z.spel.ziet(wa, z.spel.stand(sl, wa, nu + 300), l.getX(), l.getY(), l.getZ())) proef[0] = l;
                    }
                    if (proef[0] != null) break;
                }
                if (proef[0] != null) p.teleport(proef[0]);
                return false;
            }
            return p.getLocation().distanceSquared(sl.terug) < 4;
        }, 80);
        stap("sluipen " + sl.id + ": terug", () -> {
            moet(p.getLocation().distanceSquared(sl.terug) < 4, "sluipen " + sl.id + ": gezien, maar niet terug bij " + xyz(sl.terug) + " (op " + xyz(p.getLocation()) + ")");
            moet(!sl.hek.isOpen, "sluipen " + sl.id + ": het hek ging niet dicht");
            moet(sl.gezien.isEmpty() || rij().contains(sl.gezien.get(0).tekst()), "sluipen " + sl.id + ": Wiske zegt niets: " + rij());
        }, 2, () -> true, 1);
        int[] index = new int[sl.pad.size()];
        List<int[]> cellen = ZeldaSpel.cellen(sl, index);
        if (cellen == null) {
            stap("sluipen " + sl.id, () -> fouten.add("sluipen " + sl.id + ": geen recht pad"), 1, () -> true, 1);
            return;
        }
        List<Integer> rust = new ArrayList<>();                                     // de cellen waar je mag wachten
        rust.add(0);
        for (int i = 1; i < sl.pad.size(); i++) if (sl.schuil.contains(i) || i == sl.pad.size() - 1) rust.add(index[i]);
        final int[] cel = {0}, doel = {-1}, wacht = {0};
        stap("sluipen " + sl.id, () -> {
            stil();
            cel[0] = 0;
            doel[0] = -1;
            p.teleport(sl.pad.get(0));
        }, 4, () -> {
            if (stand().heeft("l:" + sl.id)) {
                sluipTest = false;
                return true;
            }
            if (cel[0] > 0 && p.getLocation().distanceSquared(sl.terug) < 4) {
                fouten.add("sluipen " + sl.id + ": gezien op " + xyz(new Location(h.w, cellen.get(cel[0])[0], cellen.get(cel[0])[1], cellen.get(cel[0])[2])));
                sluipTest = false;
                return true;
            }
            if (sl.begin == 0 || wacht[0]-- > 0) return false;
            long nu = System.currentTimeMillis();
            if (doel[0] < 0) {                                                       // op een schuilplek: gaan als het hele stuk veilig is
                int volgende = -1;
                for (int r : rust) if (r > cel[0]) {
                    volgende = r;
                    break;
                }
                if (volgende < 0) return false;
                for (int k = 1; k <= volgende - cel[0]; k++) {                      // cel k staat hij op van (k−1)·250 tot k·250 ms
                    int[] c = cellen.get(cel[0] + k);
                    for (long m : new long[]{-150, -50, 50, 150, 250, 350})
                        if (z.spel.gezien(sl, c[0] + 0.5, c[1], c[2] + 0.5, nu + (k - 1) * 250L + m)) return false;
                }
                doel[0] = volgende;
            }
            cel[0]++;
            int[] c = cellen.get(cel[0]), v = cellen.get(cel[0] - 1);
            Location l = new Location(h.w, c[0] + 0.5, c[1], c[2] + 0.5);
            l.setDirection(new org.bukkit.util.Vector(c[0] - v[0], 0, c[2] - v[2]));
            p.setVelocity(new org.bukkit.util.Vector());
            p.teleport(l);
            wacht[0] = 4;
            if (cel[0] >= doel[0]) doel[0] = -1;
            return false;
        }, 1400);
    }

    /** Schiet de klauwhaak (het voorwerp, zoals rechtsklik) vanaf {@code van} naar haakpunt {@code hp}. */
    private void haak(ZeldaHoofdstuk.Haakpunt hp, Location van) {
        moet(stand().heeft("klauwhaak"), "geen klauwhaak voor haakpunt " + hp.id());
        if (van == null) {
            fouten.add("haakpunt " + hp.id() + ": nergens een plek om hem te raken");
            return;
        }
        Location l = van.clone();
        l.setDirection(new org.bukkit.util.Vector(hp.x() + 0.5, hp.y() + 0.5, hp.z() + 0.5).subtract(l.toVector().add(new org.bukkit.util.Vector(0, p.getEyeHeight(), 0))));
        p.setVelocity(new org.bukkit.util.Vector());
        p.teleport(l);
        p.setCooldown(ZeldaSpel.Klauwhaak.ITEM, 0);
        z.spel.voorwerp("klauwhaak").gebruik(z.spel, p, null);
        moet(z.spel.haaktNaar(p) == hp, "haakpunt " + hp.id() + ": de klauwhaak grijpt " + (z.spel.haaktNaar(p) == null ? "niets" : z.spel.haaktNaar(p).id()));
    }

    /** Richt de vuurstaf (het voorwerp, zoals rechtsklik) vanaf waar je staat op dit blok. */
    private void vuur(org.bukkit.block.Block b) {
        Location l = p.getLocation().clone();
        Location oog = l.clone().add(0, p.getEyeHeight(), 0), m = z.spel.zichtpunt(oog, b);
        l.setDirection((m != null ? m : ZeldaSpel.raakpunt(b)).toVector().subtract(oog.toVector()));
        p.setVelocity(new org.bukkit.util.Vector());
        p.teleport(l);
        p.setCooldown(ZeldaSpel.Vuurstaf.ITEM, 0);
        z.spel.voorwerp("vuurstaf").gebruik(z.spel, p, null);
    }

    /** Smelt betoverd ijs met een echte vlam: vanaf de plek ervoor (of een plek waar je het ziet) op een blok dat je ziet. */
    private void smelt(ZeldaHoofdstuk h, ZeldaHoofdstuk.Smelt sm) {
        Location van = sm.voor;
        org.bukkit.block.Block doel = null;
        if (van == null)
            for (int[] c : sm.ijs.blokken) {
                van = vanwaar(h.w.getBlockAt(c[0], c[1], c[2]));
                if (van != null) break;
            }
        if (van == null) {
            fouten.add("ijs " + sm.id + ": nergens een plek om het te zien");
            return;
        }
        for (int[] c : sm.ijs.blokken)
            if (doel == null && z.spel.zicht(van.clone().add(0, p.getEyeHeight(), 0), h.w.getBlockAt(c[0], c[1], c[2]))) doel = h.w.getBlockAt(c[0], c[1], c[2]);
        if (doel == null) {
            fouten.add("ijs " + sm.id + ": niet te zien vanaf " + xyz(van));
            return;
        }
        p.teleport(van);
        vuur(doel);
    }

    /** Een plek om te staan (op 5 … 14 blokken) van waaruit je oog dit blok ziet: om er met de vuurstaf op te mikken. */
    private Location vanwaar(org.bukkit.block.Block b) {
        Location best = null;
        double bd = Double.MAX_VALUE;
        for (int x = b.getX() - 14; x <= b.getX() + 14; x++)
            for (int y = b.getY() - 8; y <= b.getY() + 4; y++)
                for (int zz = b.getZ() - 14; zz <= b.getZ() + 14; zz++) {
                    Location l = new Location(b.getWorld(), x + 0.5, y, zz + 0.5);
                    double d = l.distanceSquared(b.getLocation().add(0.5, 0.5, 0.5));
                    if (d < 25 || d >= bd || !staan(l) || !z.spel.zicht(l.clone().add(0, p.getEyeHeight(), 0), b)) continue;
                    bd = d;
                    best = l;
                }
        return best;
    }

    /**
     * Hitte (eiland 4a): zonder koelsteen en harnas word je snel te heet en kom je terug op de koele plek; met de koelsteen
     * gaat het langzamer, bij een koelplek koel je af, en met het gloedharnas deert het je niet. Daarna weer zoals het was.
     */
    private void hitte(ZeldaHoofdstuk h, ZeldaHoofdstuk.Hitte ht) {
        Location proef = ht.proef != null ? ht.proef : vrij(ht.gebied.midden(h.w).add(0, 1, 0));
        final boolean[] had = new boolean[3];                                   // koelsteen, harnas3, harnas4
        final long[] t0 = {0};
        stap("hitte " + ht.id + ": te heet", () -> {
            hitteTest = true;
            ZeldaVoortgang.Stand s = stand();
            had[0] = s.heeft("koelsteen");
            had[1] = s.heeft("harnas3");
            had[2] = s.heeft("harnas4");
            for (String v : new String[]{"koelsteen", "harnas3", "harnas4"}) s.wis(v);
            z.spel.uitrusten(p, s);
            z.spel.sessie(p).hitte = 0;
            z.spel.sessie(p).koelplek = null;
            p.teleport(proef);
            t0[0] = System.currentTimeMillis();
        }, 2, () -> p.getLocation().distanceSquared(proef) > 9, 200);
        stap("hitte " + ht.id + ": terug", () -> {
            moet(ht.terug != null && p.getLocation().distanceSquared(ht.terug) < 4, "hitte " + ht.id + ": te heet, maar niet terug bij " + (ht.terug == null ? "?" : xyz(ht.terug)) + " (op " + xyz(p.getLocation()) + ")");
            long ms = System.currentTimeMillis() - t0[0];
            moet(ms < 1000 / ht.snel + 2500, "hitte " + ht.id + ": pas na " + ms + " ms te heet");
            moet(z.spel.sessie(p).hitte < 1, "hitte " + ht.id + ": nog steeds te heet na het terugkomen");
        }, 2, () -> true, 1);
        if (!ht.fel) {
            stap("hitte " + ht.id + ": koelsteen", () -> {
                stand().zet("koelsteen");
                z.spel.sessie(p).hitte = 0;
                p.teleport(proef);
            }, 22, () -> {
                double hit = z.spel.sessie(p).hitte;
                moet(hit > 0 && hit < ht.snel * 0.9, "hitte " + ht.id + ": met de koelsteen niet langzamer warm (" + hit + " na een seconde)");
                return true;
            }, 1);
            stap("hitte " + ht.id + ": afkoelen", () -> {
                z.spel.sessie(p).hitte = 0.8;
                p.teleport(ht.terug);
            }, 32, () -> {
                moet(z.spel.sessie(p).hitte < 0.2, "hitte " + ht.id + ": niet afgekoeld bij " + xyz(ht.terug) + " (" + z.spel.sessie(p).hitte + ")");
                return true;
            }, 1);
        }
        stap("hitte " + ht.id + ": gloedharnas", () -> {
            stand().zet("harnas3");
            z.spel.uitrusten(p, stand());
            moet(p.getInventory().getBoots() != null && p.getInventory().getBoots().getType() == Material.IRON_BOOTS, "het gloedharnas is geen ijzer");
            z.spel.sessie(p).hitte = 0;
            p.teleport(proef);
        }, 30, () -> {
            moet(z.spel.sessie(p).hitte == 0, "hitte " + ht.id + ": met het gloedharnas toch warm (" + z.spel.sessie(p).hitte + ")");
            ZeldaVoortgang.Stand s = stand();
            String[] v = {"koelsteen", "harnas3", "harnas4"};
            for (int i = 0; i < 3; i++) if (had[i]) s.zet(v[i]); else s.wis(v[i]);
            z.spel.uitrusten(p, s);
            hitteTest = false;
            return true;
        }, 1);
    }

    /** Een plek om te staan van waaruit je oog dit haakpunt ziet, binnen het bereik van de klauwhaak (zo dichtbij mogelijk, niet op de landing). */
    private Location vanwaar(ZeldaHoofdstuk.Haakpunt hp) {
        var w = hp.landing().getWorld();
        Location best = null;
        double bd = Double.MAX_VALUE;
        int r = (int) ZeldaRegels.HAAK_BEREIK - 2;
        for (int x = hp.x() - r; x <= hp.x() + r; x++)
            for (int y = hp.y() - r; y <= hp.y() + 2; y++)
                for (int zz = hp.z() - r; zz <= hp.z() + r; zz++) {
                    Location l = new Location(w, x + 0.5, y, zz + 0.5);
                    double d = l.distanceSquared(new Location(w, hp.x() + 0.5, hp.y() + 0.5, hp.z() + 0.5));
                    if (d >= bd || d < 9 || l.distanceSquared(hp.landing()) < 9 || !staan(l)) continue;
                    if (!z.spel.zicht(l.clone().add(0, p.getEyeHeight(), 0), hp)) continue;
                    bd = d;
                    best = l;
                }
        return best;
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
        return menuHeeft(p, soort);
    }

    private boolean menuHeeft(Player wie, String soort) {
        var view = wie.getOpenInventory();
        if (!(view.getTopInventory().getHolder(false) instanceof ZeldaSpel.Menu)) return false;
        for (int i = 0; i < view.getTopInventory().getSize(); i++) {
            var it = view.getTopInventory().getItem(i);
            if (it != null && it.hasItemMeta() && soort.equals(it.getItemMeta().getPersistentDataContainer().get(z.spel.itemKey, org.bukkit.persistence.PersistentDataType.STRING))) return true;
        }
        return false;
    }

    /** Rechtsklik op de bel van een kamer, zoals een speler (vanuit het midden van de kamer). */
    private void belKlik(ZeldaHoofdstuk h, ZeldaHoofdstuk.Kamer k) {
        p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
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
        return klik(p, soort, false);
    }

    /** Idem voor speler {@code wie}, links- of rechtsklik. */
    private boolean klik(Player wie, String soort, boolean rechts) {
        var view = wie.getOpenInventory();
        if (!(view.getTopInventory().getHolder(false) instanceof ZeldaSpel.Menu)) return false;
        for (int i = 0; i < view.getTopInventory().getSize(); i++) {
            var it = view.getTopInventory().getItem(i);
            if (it == null || !it.hasItemMeta() || !soort.equals(it.getItemMeta().getPersistentDataContainer().get(z.spel.itemKey, org.bukkit.persistence.PersistentDataType.STRING))) continue;
            var e = new org.bukkit.event.inventory.InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, i,
                    rechts ? org.bukkit.event.inventory.ClickType.RIGHT : org.bukkit.event.inventory.ClickType.LEFT,
                    rechts ? org.bukkit.event.inventory.InventoryAction.PICKUP_HALF : org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            z.spel.onMenu(e);
            return e.isCancelled();
        }
        wie.closeInventory();
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
                final ZeldaHoofdstuk.Haakpunt[] hp = {null};
                final ZeldaHoofdstuk.Smelt[] ijs = {null};
                final ZeldaHoofdstuk.Nevel[] nevel = {null};
                final boolean[] kan = {false};
                stap("later " + v.vlag, () -> {
                    ZeldaVoortgang.Stand s = stand();
                    kan[0] = s.heeft(v.voorwerp) && !s.heeft(v.vlag) && (v.vereist == null || s.heeft(v.vereist));   // na een gesprek: zie de zijmissies
                    if (!kan[0]) return;
                    for (ZeldaHoofdstuk x : e.delen())
                        for (ZeldaHoofdstuk.Barst b : x.barsten) if (b.gebied.in(v.plek) && !b.muur.isOpen) muur[0] = b;
                    if (muur[0] != null) gooi(d, muur[0]);
                    if (v.voorwerp.equals("vuurstaf")) {                             // een echte vlam op het ijs eromheen
                        for (ZeldaHoofdstuk x : e.delen())
                            for (ZeldaHoofdstuk.Smelt sm : x.smelten) if (sm.gebied.in(v.plek) && !sm.ijs.isOpen) ijs[0] = sm;
                        if (ijs[0] != null) smelt(d, ijs[0]);
                    }
                    if (v.voorwerp.equals("lichtpijlen")) {                          // een echte lichtpijl op de nevel ervoor
                        for (ZeldaHoofdstuk x : e.delen())
                            for (ZeldaHoofdstuk.Nevel nv : x.nevels) if (nv.gebied.in(v.plek) && !nv.nevel.isOpen) nevel[0] = nv;
                        if (nevel[0] != null) licht(d, nevel[0]);
                    }
                    if (v.voorwerp.equals("klauwhaak")) {                            // een echte haak naar het haakpunt bij de vondst
                        double bd = 10 * 10;
                        for (ZeldaHoofdstuk x : e.delen())
                            for (ZeldaHoofdstuk.Haakpunt h : x.haakpunten)
                                if (h.landing().distanceSquared(v.plek) < bd) {
                                    bd = h.landing().distanceSquared(v.plek);
                                    hp[0] = h;
                                }
                        if (hp[0] == null) fouten.add("later " + v.vlag + ": geen haakpunt in de buurt");
                        else haak(hp[0], vanwaar(hp[0]));
                    }
                }, 50, () -> !kan[0] || (muur[0] == null || muur[0].muur.isOpen) && (ijs[0] == null || ijs[0].ijs.isOpen && !z.spel.vlamOnderweg(p))
                        && (nevel[0] == null || nevel[0].nevel.isOpen)
                        && (hp[0] == null || !z.spel.aanHetHaken(p) && p.getLocation().distanceSquared(hp[0].landing()) < 2), 80);
                stap("pak " + v.vlag, () -> {
                    if (kan[0]) p.teleport(vrij(v.zone.midden(d.w).add(0, 1, 0)));
                }, 8, () -> !kan[0] || stand().heeft(v.vlag), 40);
            }
    }

    /** Alle vijanden in de kamer verslaan (en binnen blijven); true als de kamer gewonnen is. */
    private boolean verslaan(ZeldaHoofdstuk h, ZeldaHoofdstuk.Kamer k) {
        for (LivingEntity e : new ArrayList<>(k.vijanden)) if (e.isValid()) e.damage(10_000, p);
        if (!k.binnen.in(p.getLocation())) p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
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
                        if (z.spel.vlamOnderweg(p)) return false;
                        ZeldaHoofdstuk.Smelt dicht = null;                          // betoverd ijs ervoor: eerst smelten (een echte vlam)
                        for (ZeldaHoofdstuk x : e.delen()) for (ZeldaHoofdstuk.Smelt sm : x.smelten) if (sm.gebied.in(v.plek) && !sm.ijs.isOpen) dicht = sm;
                        if (dicht != null && "vuurstaf".equals(v.voorwerp)) {
                            smelt(d, dicht);
                            wacht[0] = 12;
                            return false;
                        }
                        ZeldaHoofdstuk.Nevel nevel = null;                          // Morgals nevel ervoor: eerst wegschieten (een echte lichtpijl)
                        for (ZeldaHoofdstuk x : e.delen()) for (ZeldaHoofdstuk.Nevel nv : x.nevels) if (nv.gebied.in(v.plek) && !nv.nevel.isOpen) nevel = nv;
                        if (nevel != null && s.heeft("lichtpijlen")) {
                            licht(d, nevel);
                            wacht[0] = 12;
                            return false;
                        }
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
            p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
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
        stap("geen vrij huisdier", () -> {                                        // een vos (vrij huisdier) loopt niet mee: Jelle wil dat niet
            stand().zet("dier:vos");
            stand().dier = "Pluis";
        }, 30, () -> {
            moet(sp.dier.van(p) == null, "een vrij huisdier loopt mee");
            stand().wis("dier:vos");
            return true;
        }, 1);
        stap("sneeuwvosje", () -> stand().zet("dier:sneeuwvos"), 30, () -> sp.dier.van(p) != null, 60);   // het vosje van de Witte Toppen wel
        stap("sneeuwvosje loopt mee", () -> p.teleport(new Location(h.w, ZeldaHoofdstuk1.X0 + 30.5, bron.midden.getY(), 2.5)), 100,
                () -> sp.dier.van(p) != null && sp.dier.van(p).getLocation().distance(p.getLocation()) < 6, 100);
        ZeldaHoofdstuk.Vondst verstopt = h.vondsten.stream().filter(v -> v.verborgen && v.vereist == null && v.voorwerp == null).findFirst().orElse(null);
        if (verstopt != null) stap("sneeuwvosje snuffelt", () -> p.teleport(vrij(verstopt.plek.clone().add(6, 0, 0))), 20,
                () -> verstopt.plek.equals(sp.dier.snuffelt(p)), 100);
        stap("sneeuwvosje weg", () -> {
            stand().wis("dier:sneeuwvos");
            stand().dier = null;
        }, 20, () -> sp.dier.van(p) == null, 40);
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
        stap("uitleg uit stil", () -> moet(!rij().contains("je kaart"), "uitleg kwam toch: " + rij()), 2, () -> true, 1);
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
        ZeldaHoofdstuk.Haakpunt haakpunt = h.haakpunten.isEmpty() ? null : h.haakpunten.get(0);
        if (haakpunt != null) {                                                    // de klauwhaak (vak 5): een echte haak naar het oude haakpunt
            stap("klauwhaak", () -> {
                stand().zet("klauwhaak");
                sp.uitrusten(p, stand());
                moet(item(4) == ZeldaSpel.Klauwhaak.ITEM, "klauwhaak niet in vak 5 maar " + item(4));
                haak(haakpunt, vanwaar(haakpunt));
            }, 4, () -> !sp.aanHetHaken(p) && p.getLocation().distanceSquared(haakpunt.landing()) < 2, 80);
            stap("klauwhaak mis", () -> {                                          // de kloof in, waar geen haakpunt is: je blijft staan
                Location l = p.getLocation().clone();
                l.setYaw(90f);
                l.setPitch(-10f);
                p.teleport(l);
                p.setCooldown(ZeldaSpel.Klauwhaak.ITEM, 0);
                sp.voorwerp("klauwhaak").gebruik(sp, p, null);
                moet(sp.aanHetHaken(p) && sp.haaktNaar(p) == null, "klauwhaak mis: " + (sp.haaktNaar(p) == null ? "geen haak" : "grijpt " + sp.haaktNaar(p).id()));
            }, 4, () -> !sp.aanHetHaken(p), 60);
            stap("klauwhaak weg", () -> {
                moet(p.getLocation().distanceSquared(haakpunt.landing()) < 4, "klauwhaak mis: toch verplaatst naar " + xyz(p.getLocation()));
                stand().wis("klauwhaak");
                stand().wis("v:haak-mis");
                sp.uitrusten(p, stand());
            }, 2, () -> true, 1);
        }
        ZeldaHoofdstuk.Reeks vuren = h.reeksen.stream().filter(r -> r.hoe == ZeldaHoofdstuk.Reeks.Hoe.KLIK).findFirst().orElse(null);
        if (vuren != null) {                                                       // de vuurstaf (vak 6): een vuurschaal op afstand aansteken
            stap("vuurstaf", () -> {
                stil();
                stand().zet("vuurstaf");
                sp.uitrusten(p, stand());
                moet(item(5) == ZeldaSpel.Vuurstaf.ITEM, "vuurstaf niet in vak 6 maar " + item(5));
                if (vuren.opgelost || !vuren.isAan.isEmpty()) sp.herstel(vuren);
                int[] c = vuren.dingen.get(0);
                org.bukkit.block.Block b = h.w.getBlockAt(c[0], c[1], c[2]);
                Location van = vanwaar(b);
                if (van == null) {
                    fouten.add("vuurstaf: geen plek om de vuurschaal te zien");
                    return;
                }
                p.teleport(van);
                vuur(b);
            }, 4, () -> !sp.vlamOnderweg(p) && vuren.isAan.contains(0), 60);
            stap("vuurstaf weg", () -> {
                sp.herstel(vuren);
                stand().wis("vuurstaf");
                sp.uitrusten(p, stand());
            }, 2, () -> true, 1);
        }
        ZeldaHoofdstuk.Nevel nevel = null;
        for (ZeldaHoofdstuk d : z.HOOFDSTUKKEN) if (nevel == null && !d.nevels.isEmpty()) nevel = d.nevels.get(0);
        if (nevel != null) {                                                       // de lichtpijlen (vak 7): een pijl van licht, en Morgals nevel verdwijnt
            ZeldaHoofdstuk.Nevel nv = nevel;
            stap("lichtpijlen", () -> {
                stil();
                stand().zet("lichtpijlen");
                sp.uitrusten(p, stand());
                moet(item(6) == ZeldaSpel.Lichtpijlen.ITEM, "lichtpijlen niet in vak 7 maar " + item(6));
                var boog = p.getInventory().getItem(1);
                moet(boog == null || boog.getType() != Material.BOW || boog.getItemMeta().displayName() != null, "de boog heeft geen naam");
                p.setCooldown(ZeldaSpel.Lichtpijlen.ITEM, 0);
                int voor = sp.lichtpijlen.size();
                sp.voorwerp("lichtpijlen").gebruik(sp, p, null);
                moet(sp.lichtpijlen.size() > voor, "de lichtpijlen schieten geen pijl van licht");
                if (nv.nevel.isOpen) sp.herstel(nv);
                sp.verdrijf(p, nv);
                moet(nv.nevel.isOpen && stand().heeft("n:" + nv.id), "nevel " + nv.id + " verdwijnt niet");
            }, 4, () -> true, 1);
            stap("lichtpijlen weg", () -> {
                sp.herstel(nv);
                moet(!nv.nevel.isOpen, "nevel " + nv.id + " komt niet terug");
                stand().wis("lichtpijlen");
                stand().wis("n:" + nv.id);
                sp.uitrusten(p, stand());
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
            p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
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
        stap("kaart vasthouden", () -> {                                     // vak 9: de kaart met twee handen (het schild even weg), linksklik = omslaan
            stil();
            stand().zet("schild");
            boolean uit = stand().wis("o:uitleg-uit");
            sp.uitrusten(p, stand());
            moet(sp.landkaart.isKaart(p.getInventory().getItem(8)), "vak 9: geen kaart van Zelda");
            var vak = new org.bukkit.event.player.PlayerItemHeldEvent(p, p.getInventory().getHeldItemSlot(), 8);
            Bukkit.getPluginManager().callEvent(vak);
            p.getInventory().setHeldItemSlot(8);
            moet(p.getInventory().getItemInOffHand().getType() == Material.AIR, "met de kaart in de hand: het schild blijft");
            boolean zee = sp.landkaart.kijk(id).zee;
            Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(p, org.bukkit.event.block.Action.LEFT_CLICK_AIR, p.getInventory().getItem(8), null,
                    org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
            moet(sp.landkaart.kijk(id).zee != zee, "linksklik met de kaart: niet omgeslagen");
            sp.landkaart.kijk(id).omslag = 0;
            Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(p, org.bukkit.event.block.Action.LEFT_CLICK_AIR, p.getInventory().getItem(8), null,
                    org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
            moet(sp.landkaart.kijk(id).zee == zee, "nog eens linksklik: niet terug");
            Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItem(8), null,
                    org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
            moet(p.getOpenInventory().getTopInventory().getHolder(false) instanceof ZeldaSpel.Menu, "rechtsklik met de kaart: geen menu");
            p.closeInventory();
            moet(rij().contains("Linksklik"), "Wiske legt de kaart niet uit: " + rij());
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerItemHeldEvent(p, 8, 0));
            p.getInventory().setHeldItemSlot(0);
            moet(p.getInventory().getItemInOffHand().getType() == Material.SHIELD, "de kaart weg: het schild niet terug");
            var wissel = new org.bukkit.event.player.PlayerSwapHandItemsEvent(p, p.getInventory().getItemInOffHand(), p.getInventory().getItemInMainHand());
            Bukkit.getPluginManager().callEvent(wissel);
            moet(wissel.isCancelled(), "F wisselt in Zelda nog zwaard en schild");
            stand().wis("schild");
            if (uit) stand().zet("o:uitleg-uit");
            sp.uitrusten(p, stand());
            stil();
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

    /**
     * De kaart in vak 9 op dit eiland: je pijl staat waar je bent en wijst waar je kijkt, waar je staat is ontdekt (geen perkament),
     * je zielvuur is blauw, de X staat waar {@code /hint} heen wijst (en alleen dan), en op de zeekaart staat het eiland in kleur.
     */
    private void kaartKlopt(ZeldaEiland e) {
        ZeldaKaart k = z.spel.landkaart;
        ZeldaSpel.Sessie ss = z.spel.sessie(p);
        moet(k.isKaart(p.getInventory().getItem(8)), "vak 9: geen kaart van Zelda");
        k.ontdek(p, stand());
        ZeldaKaart.Blad b = k.nu(p, ZeldaKaart.EILAND);
        moet(ZeldaKaart.EILAND.equals(b.kant()), "kaart " + e.id() + ": geen eilandblad");
        if (!ZeldaKaart.EILAND.equals(b.kant())) return;
        ZeldaKaartBeeld.Vlak v = b.vlak();
        Location l = p.getLocation();
        var jij = b.merken().stream().filter(m -> m.soort().equals("jij")).toList();
        moet(jij.size() == 1 && Math.abs(jij.get(0).i() - v.px(l.getX())) < 0.01 && Math.abs(jij.get(0).j() - v.pz(l.getZ())) < 0.01
                && jij.get(0).richting() == ZeldaKaartBeeld.richting(l.getYaw()), "kaart " + e.id() + ": je pijl klopt niet (" + jij + ")");
        int i = (int) Math.floor(v.px(l.getX())), j = (int) Math.floor(v.pz(l.getZ()));
        ZeldaKaartBeeld.Basis basis = k.bladen.get(e.id());
        moet(!basis.isLand(i, j) || b.px()[j * ZeldaKaartBeeld.N + i] != ZeldaKaartBeeld.PERKAMENT, "kaart " + e.id() + ": waar je staat is nog perkament");
        ZeldaHoofdstuk.Rust r = ss.rustplaats();
        if (ss.rh.eiland == e) {
            int ri = (int) Math.floor(v.px(r.plek.getX())), rj = (int) Math.floor(v.pz(r.plek.getZ()));
            moet(rj < 2 || b.px()[(rj - 2) * ZeldaKaartBeeld.N + ri] == ZeldaKaartBeeld.ZIEL, "kaart " + e.id() + ": je zielvuur (" + r.id + ") is niet blauw");
        }
        Location d = z.spel.wijzer(e, stand(), l.getX(), l.getZ()).plek();
        var x = b.merken().stream().filter(m -> m.soort().equals("doel")).toList();
        if (d != null && v.op(d.getX(), d.getZ()) && e.deelBij(d) != null)
            moet(x.size() == 1 && Math.abs(x.get(0).i() - v.px(d.getX())) < 0.01 && Math.abs(x.get(0).j() - v.pz(d.getZ())) < 0.01,
                    "kaart " + e.id() + ": de X staat niet waar /hint heen wijst (" + xyz(d) + ")");
        else moet(x.isEmpty(), "kaart " + e.id() + ": een X zonder doel van /hint");
        ZeldaKaart.Blad zee = k.nu(p, ZeldaKaart.ZEE);
        moet(zee.merken().stream().anyMatch(m -> m.soort().equals("jij")), "zeekaart: geen pijl");
        int ri = (int) Math.floor(zee.vlak().px(r.plek.getX())), rj = (int) Math.floor(zee.vlak().pz(r.plek.getZ()));
        java.util.Set<Byte> zeeKleur = java.util.Set.of(ZeldaKaartBeeld.DIEP, ZeldaKaartBeeld.ONDIEP, ZeldaKaartBeeld.GOLF, ZeldaKaartBeeld.PERKAMENT);
        boolean kleur = false;
        for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) kleur |= !zeeKleur.contains(zee.px()[Math.max(0, Math.min(127, rj + c)) * ZeldaKaartBeeld.N + Math.max(0, Math.min(127, ri + a))]);
        moet(kleur, "zeekaart: " + e.naam() + " staat er niet in kleur op");
        gedaan.add("kaart " + e.id() + " (" + stand().kaart.cardinality() + " vakjes)");
    }

    /** Op zee: de zeekaart (vanzelf), met je pijl en de stippels van de vaarroute. */
    private void kaartOpZee(ZeldaEiland naar) {
        ZeldaSpel.Sessie ss = z.spel.sessie(p);
        if (ss == null || ss.vaart == null || !z.spel.landkaart.bladen.containsKey(ss.h.eiland.id())) return;
        ZeldaKaart.Blad b = z.spel.landkaart.nu(p, ZeldaKaart.EILAND);
        moet(ZeldaKaart.ZEE.equals(b.kant()), "op zee: geen zeekaart");
        moet(b.merken().stream().anyMatch(m -> m.soort().equals("jij")), "op zee: geen schip op de kaart");
        int stip = 0;
        for (byte c : b.px()) if (c == ZeldaKaartBeeld.STIP) stip++;
        moet(stip > 3, "op zee: geen vaarroute naar " + naar.naam());
    }

    private Material item(int vak) {
        var it = p.getInventory().getItem(vak);
        return it == null ? Material.AIR : it.getType();
    }

    /** Alles wat er nu op de actiebalk staat of nog komt, als platte tekst. */
    private String rij() {
        return rij(p);
    }

    private String rij(Player wie) {
        ZeldaSpel.Sessie ss = z.spel.sessie(wie);
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

    /**
     * Co-op: de tweede speler volgt p overal (zelfde plek, zelfde eiland; vaart p, dan vaart hij ook). Hij wordt niet gezien
     * en wordt niet te heet (de test teleporteert hem).
     */
    private void volg() {
        if (!mee.isOnline() || !z.spel.speelt(mee) || !z.spel.speelt(p)) return;
        if (mee.isDead()) {
            mee.spigot().respawn();
            return;
        }
        ZeldaSpel.Sessie a = z.spel.sessie(p), b = z.spel.sessie(mee);
        b.hitte = 0;
        b.ongezien = true;
        if (b.vaart != null) return;                         // hij vaart nog
        ZeldaEiland waar = a.vaart != null ? a.vaart.naar() : a.h.eiland;
        if (waar != null && b.h.eiland != waar) {
            if (waar.open(z.spel.voortgang.van(mee.getUniqueId()))) z.spel.vaar(mee, waar);
            return;
        }
        if (a.vaart != null || p.isDead() || !p.getWorld().equals(mee.getWorld())) return;
        if (mee.getLocation().distanceSquared(p.getLocation()) > 9) {
            mee.setFallDistance(0);
            mee.teleport(p.getLocation());
        }
    }

    /** Een plek bij {@code l} om te staan buiten elke rustplaats en bron (daar genees je vanzelf). */
    private Location buiten(ZeldaHoofdstuk h, Location l) {
        for (int r = 6; r < 60; r += 3)
            for (int[] d : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
                Location t = vrij(l.clone().add(d[0], 1, d[1]));
                if (!staan(t) || !h.speelgebied.in(t)) continue;
                boolean zone = false;
                for (ZeldaHoofdstuk.Rust rp : h.rust) zone |= rp.zone.groter(2).in(t);
                for (ZeldaHoofdstuk.Bron b : h.bronnen) zone |= b.zone.groter(2).in(t);
                for (ZeldaHoofdstuk.Kamer k : h.kamers) zone |= k.binnen.groter(2).in(t);
                if (!zone) return t;
            }
        return l;
    }

    /**
     * {@code kitpvp zelda test <speler> niveau}: de vier niveaus met een echte speler: een nieuw spel is Avontuur, de knop op de
     * kaart en het menu, Verhaal kiezen (twee extra flesjes), de schade van een vijand per niveau (de verhouding klopt), op adem
     * komen (Verhaal snel, Legende traag), in een kamer het schildje van licht, midden in het gevecht wisselen, doodgaan (de
     * doodteller, hulp bij de volgende poging), winnen (de ster is het laagste niveau tijdens het gevecht), de bel op Held (ster
     * Held, «Gewonnen op Held»), drie keer dood (Wiske: «makkelijker»), beheer: vast en los, steun, het overzicht en de doden, en
     * de heldenrand. Wist eerst en na afloop de voortgang van die speler (alleen voor tests).
     */
    void niveau() {
        if (p.isDead()) p.spigot().respawn();
        pl.naarHub(p);
        z.spel.voortgang.wis(id);
        ZeldaSpel sp = z.spel;
        ZeldaHoofdstuk h = z.HOOFDSTUKKEN.get(0);
        ZeldaHoofdstuk.Kamer k = h.kamers.stream().filter(x -> !x.alleenBel && x.bel != null).findFirst()
                .orElse(h.kamers.stream().filter(x -> !x.alleenBel).findFirst().orElseThrow());
        String naam = p.getName();
        final double[] max = {20};
        stap("poort", this::doorDePoort, 2, () -> true, 1);
        stap("avontuur", () -> {
            moet(ZeldaRegels.niveau(stand()) == ZeldaRegels.Niveau.AVONTUUR, "een nieuw spel begint niet op Avontuur");
            sp.kaart(p);
            moet(menuHeeft("menu:niveau") && menuHeeft("menu:spellen"), "de kaart heeft geen knop Niveau of Spellen");
            moet(klik("menu:niveau"), "klik op Niveau: niets");
        }, 3, () -> menuHeeft("niveau:0") && menuHeeft("niveau:3"), 20);
        stap("verhaal kiezen", () -> moet(klik("niveau:0"), "klik op Verhaal: niets"), 3, () -> ZeldaRegels.niveau(stand()) == ZeldaRegels.Niveau.VERHAAL, 20);
        stap("verhaal: flesjes", () -> {
            moet(rij().contains("Niveau · Verhaal"), "geen regel na het kiezen: " + rij());
            stil();
            stand().zet("fles2");
            ZeldaRegels.vulFlessen(stand());
            sp.uitrusten(p, stand());
            var fl = p.getInventory().getItem(2);
            moet(ZeldaRegels.flessen(stand()) == 3 && fl != null && fl.getAmount() == 3, "Verhaal: geen twee extra flesjes (" + (fl == null ? 0 : fl.getAmount()) + ")");
            p.teleport(buiten(h, h.rust.get(0).plek));
        }, 2, () -> true, 1);
        stap("zonder harnas", () -> p.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]), 3, () -> true, 1);   // harnas telt een tik later niet meer
        stap("schade per niveau", () -> {
            var m = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            max[0] = m == null ? 20 : m.getValue();
            LivingEntity v = sp.spawn(ZeldaHoofdstuk.Soort.VEENLOPER, p.getLocation().add(2, 0, 0), 1);
            double[] verlies = new double[4];
            for (ZeldaRegels.Niveau n : ZeldaRegels.Niveau.values()) {
                sp.zetNiveau(id, n);
                p.setNoDamageTicks(0);
                p.setHealth(max[0]);
                p.damage(4, v);
                verlies[n.ordinal()] = max[0] - p.getHealth();
            }
            v.remove();
            sp.uitrusten(p, stand());
            double a = verlies[ZeldaRegels.Niveau.AVONTUUR.ordinal()];
            moet(a > 0, "een vijand deed niets");
            for (ZeldaRegels.Niveau n : ZeldaRegels.Niveau.values())
                moet(a > 0 && Math.abs(verlies[n.ordinal()] / a - n.schade) < 0.02, n.naam + ": schade × " + (a > 0 ? verlies[n.ordinal()] / a : 0) + " (verwacht " + n.schade + ")");
            gedaan.add("schade " + java.util.Arrays.toString(verlies));
            sp.zetNiveau(id, ZeldaRegels.Niveau.VERHAAL);
            p.setHealth(10);
            sp.sessie(p).geraakt = System.currentTimeMillis();
            sp.sessie(p).bijkomen = 0;
        }, 2, () -> true, 1);
        stap("op adem: verhaal", () -> { }, 110, () -> p.getHealth() > 10.5, 20);
        stap("op adem: legende", () -> {
            sp.zetNiveau(id, ZeldaRegels.Niveau.LEGENDE);
            p.setHealth(10);
            sp.sessie(p).geraakt = System.currentTimeMillis();
            sp.sessie(p).bijkomen = 0;
        }, 160, () -> {
            moet(p.getHealth() == 10, "Legende: na 8 s al op adem (" + p.getHealth() + ")");
            return true;
        }, 1);
        stap("kamer " + k.id + ": schildje", () -> {
            sp.zetNiveau(id, ZeldaRegels.Niveau.VERHAAL);
            p.setHealth(max[0]);
            stil();
            if (k.fase != ZeldaHoofdstuk.Fase.RUST) sp.herstel(k);
            p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
        }, 10, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG, 40);
        stap("kamer: gouden harten", () -> {
            moet(p.getAbsorptionAmount() >= 7.9, "Verhaal: geen schildje van licht (" + p.getAbsorptionAmount() + ")");
            moet(rij().contains("schildje"), "Wiske zegt niets over het schildje: " + rij());
            stil();
            sp.kaart(p);
            moet(klik("menu:niveau"), "in het gevecht: geen menu");
        }, 3, () -> menuHeeft("niveau:1"), 20);
        stap("kamer: wisselen in het gevecht", () -> moet(klik("niveau:1"), "klik op Avontuur: niets"), 3,
                () -> ZeldaRegels.niveau(stand()) == ZeldaRegels.Niveau.AVONTUUR && k.fase == ZeldaHoofdstuk.Fase.BEZIG, 20);
        stap("kamer: dood", () -> p.setHealth(0), 3, () -> {
            if (p.isDead()) p.spigot().respawn();
            return !p.isDead();
        }, 40);
        stap("kamer: doodteller", () -> {
            moet(stand().teller("dood:" + k.id) == 1 && sp.sessie(p).pogingen.getOrDefault(k.id, 0) == 1,
                    "doodteller " + stand().teller("dood:" + k.id) + ", pogingen " + sp.sessie(p).pogingen.get(k.id));
            stil();
            if (k.fase != ZeldaHoofdstuk.Fase.RUST) sp.herstel(k);
            p.teleport(vrij(k.start.midden(h.w).add(0, 1, 0)));
        }, 5, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG, 40);
        stap("kamer: hulp na doodgaan", () -> moet(Math.abs(p.getAbsorptionAmount() - 4) < 0.1, "Avontuur na één keer dood: geen twee gouden harten ("
                + p.getAbsorptionAmount() + ")"), 3, () -> true, 1);
        stap("kamer: winnen (beheer zet Held)", () -> {
            List<String> uit = sp.bijsturen.commando(aan, null, new String[]{"niveau", naam, "held"});
            moet(uit != null && String.join(" ", uit).contains("Held"), "beheer niveau: " + uit);
        }, 2, () -> verslaan(h, k), 600);
        stap("kamer: ster", () -> {
            moet(ZeldaRegels.ster(stand(), k.id) == ZeldaRegels.Niveau.AVONTUUR, "ster " + ZeldaRegels.ster(stand(), k.id) + " (het laagste niveau in het gevecht was Avontuur)");
            moet(!sp.sessie(p).pogingen.containsKey(k.id), "na winnen telt de hulp niet terug");
            moet(!rij().contains("Gewonnen op"), "«Gewonnen op» voor Avontuur: " + rij());
            stil();
        }, 3, () -> true, 1);
        if (k.bel != null) {
            stap("kamer: bel op Held", () -> belKlik(h, k), 5, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG, 40);
            stap("kamer: Held winnen", () -> { }, 5, () -> verslaan(h, k), 1200);
            stap("kamer: ster Held", () -> {
                moet(ZeldaRegels.ster(stand(), k.id) == ZeldaRegels.Niveau.HELD, "ster na de bel op Held: " + ZeldaRegels.ster(stand(), k.id));
                moet(rij().contains("Gewonnen op Held"), "geen «Gewonnen op Held»: " + rij());
                stil();
                sp.zetNiveau(id, ZeldaRegels.Niveau.AVONTUUR);
                sp.sessie(p).pogingen.put(k.id, 2);
            }, 3, () -> true, 1);
            stap("drie keer dood: bel", () -> belKlik(h, k), 5, () -> k.fase == ZeldaHoofdstuk.Fase.BEZIG, 40);
            stap("drie keer dood", () -> p.setHealth(0), 3, () -> {
                if (p.isDead()) p.spigot().respawn();
                return !p.isDead();
            }, 40);
            stap("drie keer dood: Wiske", () -> { }, 5, () -> {
                moet(rij().contains("makkelijker"), "Wiske biedt na drie keer dood geen makkelijker niveau aan: " + rij());
                return true;
            }, 1);
        }
        stap("vast", () -> {
            stil();
            sp.bijsturen.commando(aan, null, new String[]{"niveau", naam, "legende", "vast"});
            moet(stand().vast && ZeldaRegels.niveau(stand()) == ZeldaRegels.Niveau.LEGENDE, "beheer: niet vast op Legende");
            sp.kaart(p);
            moet(klik("menu:niveau"), "klik op Niveau (vast): niets");
        }, 4, () -> {
            moet(rij().contains("staat vast") && !menuHeeft("niveau:0"), "vast: toch het menu of geen regel: " + rij());
            sp.spellen.klik(p, "niveau:0", false);
            return true;
        }, 1);
        stap("los en steun", () -> {
            moet(ZeldaRegels.niveau(stand()) == ZeldaRegels.Niveau.LEGENDE, "vast: toch veranderd");
            sp.bijsturen.commando(aan, null, new String[]{"niveau", naam, "los"});
            moet(!stand().vast, "beheer: niet los");
            sp.bijsturen.commando(aan, null, new String[]{"steun", naam, "2"});
            moet(Math.abs(ZeldaRegels.schade(stand()) - ZeldaRegels.SCHADE * 1.75 * 0.7) < 1e-9 && ZeldaRegels.flessen(stand()) == 3, "steun 2: schade "
                    + ZeldaRegels.schade(stand()) + ", flesjes " + ZeldaRegels.flessen(stand()));
            String ov = String.join(" | ", sp.bijsturen.overzicht());
            moet(ov.contains(naam) && ov.contains("Legende +2"), "overzicht: " + ov);
            String d = String.join(" | ", sp.bijsturen.doden());
            moet(d.contains(k.naam), "doden: " + d);
            gedaan.add("overzicht: " + ov);
            sp.bijsturen.commando(aan, null, new String[]{"steun", naam, "0"});
        }, 2, () -> true, 1);
        stap("heldenrand", () -> {
            for (String b : sp.bazen()) stand().teller("ster:" + b, ZeldaRegels.Niveau.HELD.ordinal() + 1);
            sp.uitrusten(p, stand());
            var borst = p.getInventory().getChestplate();
            boolean goud = borst != null && borst.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta am && am.getTrim() != null
                    && am.getTrim().getMaterial().equals(org.bukkit.inventory.meta.trim.TrimMaterial.GOLD);
            moet(goud, "alle bazen op Held: geen gouden heldenrand");
        }, 2, () -> true, 1);
        stap("opruimen", () -> {
            stil();
            pl.naarHub(p);
            z.spel.voortgang.wis(id);
            z.spel.bewaar();
        }, 2, () -> true, 1);
        stap("kamer terug", () -> { }, 20, () -> k.fase == ZeldaHoofdstuk.Fase.RUST && k.vijanden.isEmpty(), 200);
        start("kitpvp zelda test " + p.getName() + " niveau");
    }

    /**
     * {@code kitpvp zelda test <speler> spellen [<tweede speler>]}: meerdere spellen met een echte speler: via de kaart een nieuw
     * spel op Held (het begint bij Thuis, zonder je spullen, je eerste spel blijft), terug naar het eerste spel (je spullen en je
     * rustplaats), een spel wissen (niet het spel waarin je speelt). Met een tweede speler ook samen spelen: uitnodigen,
     * aannemen in zijn «Spellen» (een samen-spel bij jouw voortgang, zijn eigen spel blijft), dan delen ze wat nieuw is (niet
     * de opties), zijn harten volgen, en beheer maakt het los. Wist eerst en na afloop de voortgang (alleen voor tests).
     */
    void spellen(Player twee) {
        if (p.isDead()) p.spigot().respawn();
        pl.naarHub(p);
        z.spel.voortgang.wis(id);
        ZeldaSpel sp = z.spel;
        ZeldaVoortgang v = sp.voortgang;
        ZeldaHoofdstuk h = z.HOOFDSTUKKEN.get(0);
        ZeldaHoofdstuk.Rust r1 = h.rust.get(1);
        stap("poort", this::doorDePoort, 2, () -> true, 1);
        stap("spel 1", () -> {
            stand().zet("zwaard");
            stand().zet("v:intro");
            sp.uitrusten(p, stand());
            p.teleport(r1.plek);
        }, 8, () -> sp.sessie(p).rust == r1.index && stand().bezocht(h.id(), r1.index), 40);
        stap("spellen", () -> {
            stil();
            sp.kaart(p);
            moet(klik("menu:spellen"), "klik op Spellen: niets");
        }, 3, () -> menuHeeft("nieuw:2") && menuHeeft("spel:1") && menuHeeft("samen"), 20);
        stap("nieuw spel", () -> moet(klik("nieuw:2"), "klik op een lege plek: niets"), 3, () -> menuHeeft("nieuwniveau:2:2"), 20);
        stap("nieuw spel op Held", () -> moet(klik("nieuwniveau:2:2"), "klik op Held: niets"), 5, () -> v.actief(id) == 2 && sp.speelt(p), 40);
        stap("spel 2", () -> {
            moet(ZeldaRegels.niveau(stand()) == ZeldaRegels.Niveau.HELD, "spel 2 is niet op Held");
            moet(!stand().heeft("zwaard") && item(0) == Material.WOODEN_SWORD, "spel 2 heeft de spullen van spel 1");
            moet(p.getLocation().distanceSquared(h.rust.get(0).plek) < 4, "spel 2 begint niet bij Thuis: " + xyz(p.getLocation()));
            moet(v.spellen(id).get(1) != null && v.spellen(id).get(1).heeft("zwaard"), "spel 1 is weg");
            stil();
            sp.kaart(p);
            klik("menu:spellen");
        }, 5, () -> menuHeeft("spel:1"), 20);
        stap("terug naar spel 1", () -> moet(klik("spel:1"), "klik op spel 1: niets"), 5, () -> v.actief(id) == 1 && sp.speelt(p), 40);
        stap("spel 1 terug", () -> {
            moet(stand().heeft("zwaard") && item(0) == Material.IRON_SWORD, "spel 1: het zwaard is weg");
            moet(p.getLocation().distanceSquared(r1.plek) < 4, "spel 1: niet op zijn laatste rustplaats (" + r1.naam + ") maar op " + xyz(p.getLocation()));
            moet(ZeldaRegels.niveau(stand()) == ZeldaRegels.Niveau.AVONTUUR, "spel 1 is niet meer op Avontuur");
            stil();
            sp.spellen.spellenMenu(p);
            klik(p, "spel:2", true);
        }, 5, () -> menuHeeft("wisja:2"), 20);
        stap("spel 2 wissen", () -> moet(klik("wisja:2"), "klik op «Ja, wissen»: niets"), 3, () -> !v.spellen(id).containsKey(2), 20);
        stap("het spel waarin je speelt blijft", () -> {
            p.closeInventory();
            sp.spellen.klik(p, "wisja:1", false);
        }, 3, () -> {
            moet(v.spellen(id).containsKey(1) && v.actief(id) == 1, "het spel waarin je speelt is gewist");
            p.closeInventory();
            return true;
        }, 1);
        if (twee != null && !twee.equals(p)) {
            UUID tid = twee.getUniqueId();
            stap("samen: " + twee.getName(), () -> {
                if (twee.isDead()) twee.spigot().respawn();
                pl.naarHub(twee);
                v.wis(tid);
                pl.kiesModus(twee, z);
            }, 5, () -> sp.speelt(twee), 40);
            stap("samen: uitnodigen", () -> {
                v.van(tid).zet("eigen-spel");
                v.van(tid).zet("o:uitleg-uit");
                stand().zet("o:proef");
                sp.spellen.nodigUit(p, twee);
                moet(sp.spellen.uitnodiging(tid) != null, "geen uitnodiging");
                moet(rij(twee).contains("wil samen spelen"), "hij hoort niets: " + rij(twee));
                sp.spellen.spellenMenu(twee);
            }, 3, () -> menuHeeft(twee, "aannemen"), 20);
            stap("samen: aannemen", () -> moet(klik(twee, "aannemen", false), "klik op de uitnodiging: niets"), 5, () -> v.actief(tid) == 2 && sp.speelt(twee), 40);
            stap("samen: één avontuur", () -> {
                ZeldaVoortgang.Stand b = v.van(tid);
                moet(b.groep != null && b.groep.equals(stand().groep), "geen samen-spel");
                moet(b.heeft("zwaard") && !b.heeft("eigen-spel"), "het samen-spel begint niet bij jouw voortgang");
                moet(b.heeft("o:uitleg-uit") && !b.heeft("o:proef"), "de opties zijn niet van hemzelf");
                moet(v.spellen(tid).get(1) != null && v.spellen(tid).get(1).heeft("eigen-spel"), "zijn eigen spel is weg");
                moet(twee.getLocation().distanceSquared(r1.plek) < 4, "hij staat niet op jouw laatste rustplaats: " + xyz(twee.getLocation()));
                stand().zet("hart-proef");
                stand().zet("o:nog-een");
                b.zet("k:proef-samen");
            }, 2, () -> true, 1);
            stap("samen: delen", () -> { }, 25, () -> v.van(tid).heeft("hart-proef") && stand().heeft("k:proef-samen"), 40);
            stap("samen: eigen", () -> {
                moet(!v.van(tid).heeft("o:nog-een"), "een optie ging over");
                var m = twee.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                moet(m != null && m.getValue() == ZeldaRegels.harten(v.van(tid)) * 2 && ZeldaRegels.harten(v.van(tid)) == ZeldaRegels.harten(stand()),
                        "zijn harten volgen niet (" + (m == null ? "?" : m.getValue() / 2) + ")");
                moet(String.join(" ", sp.bijsturen.overzicht()).contains("samen met"), "het overzicht zegt niet wie samen speelt");
                List<String> los = sp.bijsturen.commando(aan, null, new String[]{"samen", twee.getName(), "los"});
                moet(v.van(tid).groep == null, "beheer: niet los: " + los);
            }, 2, () -> true, 1);
            stap("samen: opruimen", () -> {
                pl.naarHub(twee);
                v.wis(tid);
            }, 2, () -> true, 1);
        }
        stap("opruimen", () -> {
            stil();
            pl.naarHub(p);
            v.wis(id);
            sp.bewaar();
        }, 2, () -> true, 1);
        start("kitpvp zelda test " + p.getName() + " spellen" + (twee != null ? " " + twee.getName() : ""));
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
                if (z.spel.sessie(p) != null) z.spel.sessie(p).ongezien = false;
                if (mee != null && z.spel.sessie(mee) != null) z.spel.sessie(mee).ongezien = false;
                String uit = titel + ": " + (fouten.isEmpty() ? "OK" : "FOUT (" + fouten.size() + ")") + " · " + String.join(" → ", gedaan);
                aan.sendMessage(uit);
                pl.getLogger().info(uit);
                for (String f : fouten) {
                    aan.sendMessage("  ✗ " + f);
                    pl.getLogger().info("  ✗ " + f);
                }
                return;
            }
            if (!hitteTest && z.spel.sessie(p) != null) z.spel.sessie(p).hitte = 0;   // de test teleporteert: niet te heet worden
            if (mee != null) volg();
            if (!sluipTest && z.spel.sessie(p) != null) z.spel.sessie(p).ongezien = true;   // … en niet gezien worden (behalve bij het sluipen)
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
                if (z.spel.speelt(p) && z.spel.muziek.achter(p) == null && z.spel.muziek.lus(p) == null && stilte++ < 3)
                    fouten.add(s.naam() + ": stilte (geen muziek)");             // muziek altijd aan (Jelle)
                gedaan.add(s.naam());
                rij.poll();
                gedaanDoe[0] = false;
            }
        }, 1L, 1L);
    }
}
