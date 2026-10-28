package nl.burggraaf.mc.kitpvp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Story of Zelda: een avontuur over de eilanden van de Glazen Zee, alleen of samen (ontwerp: ZELDA.md). Door de grote
 * poort in de hub (west) kom je bij je laatste rustplaats. Een eiland ({@link ZeldaEiland}) bestaat uit één of twee
 * delen ({@link ZeldaHoofdstuk}, elk een eigen stuk wereld: een {@link Deel}) met rustplaatsen, bronnen, kamers,
 * uitdagingen, puzzels, vondsten, personen, zijmissies en verhaal; tussen de eilanden vaar je met de Zilvermeeuw
 * ({@link ZeldaZee}). {@link ZeldaSpel} laat het werken, {@link ZeldaVoortgang} bewaart per speler hoe ver je bent.
 *
 * <p>Een eiland erbij: delen (subklassen van {@link ZeldaHoofdstuk} met een eigen deel-id) en één regel in
 * {@link #EILANDEN} (zie ZELDA.md en MODI.md). De wereld ligt in {@link #GEBIED}.
 */
final class Zelda implements Modus {

    /** Gereserveerd voor de Zelda-map: x 1024..2047, z -512..511 (vier regiobestanden, ver van de rest). */
    static final int[] GEBIED = {1024, -512, 2047, 511};

    private final KitPvP plugin;
    /** De eilanden in de volgorde van het verhaal (zie ZELDA.md). Een nieuw eiland = één regel erbij. */
    final List<ZeldaEiland> EILANDEN;
    /**
     * Eilanden (of delen ervan) die eraan komen, nog niet gebouwd: grijs op de kaart zodra hun {@code openNa} er is. Sinds
     * de toren van Nachtrots (5b) is alles gebouwd: leeg. Een nieuw eiland (of deel) kan hier weer aangekondigd worden.
     */
    final List<ZeldaEiland.Komt> KOMT = List.of();
    /** Alle delen van alle eilanden. */
    final List<ZeldaHoofdstuk> HOOFDSTUKKEN;
    final ZeldaZee zee;
    final ZeldaSpel spel;

    Zelda(KitPvP plugin) {
        this.plugin = plugin;
        ZeldaEiland2a zilverstrand = new ZeldaEiland2a(plugin);
        ZeldaHoofdstuk1 lindewoud = new ZeldaHoofdstuk1(plugin);
        ZeldaEiland5a muren = new ZeldaEiland5a(plugin, zilverstrand);
        EILANDEN = List.of(
                new ZeldaEiland("lindewoud", "Het Lindewoud", Material.OAK_SAPLING, List.of(lindewoud), "haven", null, "h1", List.of()),
                new ZeldaEiland("zilverstrand", "Het Zilverstrand", Material.SAND, List.of(zilverstrand, new ZeldaEiland2b(plugin)), "steiger", "h1", "baken2", List.of(
                        ZeldaHoofdstuk.r("Brecht", "Op het Zilverstrand woont mijn zus Mira."),
                        ZeldaHoofdstuk.r("Brecht", "Zij past op de vuurtoren. Al twintig jaar."),
                        ZeldaHoofdstuk.r("Wiske", "Een vuurtoren wijst de weg. Precies wat we zoeken!"))),
                new ZeldaEiland("witte-toppen", "De Witte Toppen", Material.SNOW_BLOCK, List.of(new ZeldaEiland3a(plugin), new ZeldaEiland3b(plugin)), "steiger", "baken2", "baken3", List.of(
                        ZeldaHoofdstuk.r("Brecht", "Het wordt kouder. Kijk, ijsschotsen."),
                        ZeldaHoofdstuk.r("Brecht", "Op de Witte Toppen woont een oude vriend van Teun."),
                        ZeldaHoofdstuk.r("Wiske", "Bergen met sneeuw erop! Zo hoog!"))),
                new ZeldaEiland("gloedberg", "De Gloedberg", Material.MAGMA_BLOCK, List.of(new ZeldaEiland4a(plugin), new ZeldaEiland4b(plugin)), "baai", "baken3", "baken4", List.of(
                        ZeldaHoofdstuk.r("Brecht", "Ruik je dat? Rook. We zijn er bijna."),
                        ZeldaHoofdstuk.r("Brecht", "Op de Gloedberg wonen mijnwerkers. Taaie lui."),
                        ZeldaHoofdstuk.r("Wiske", "Die berg rookt! Is dat… een vulkaan?"))),
                new ZeldaEiland("nachtrots", "Nachtrots", Material.CRYING_OBSIDIAN, List.of(muren, new ZeldaEiland5b(plugin, muren, lindewoud)), "kreek", "baken4", "zelda", List.of(
                        ZeldaHoofdstuk.r("Brecht", "Daar ligt hij. Nachtrots, zonder Nevelring."),
                        ZeldaHoofdstuk.r("Brecht", "Ik vaar je naar de oude visserskreek. Daar kijkt niemand."),
                        ZeldaHoofdstuk.r("Wiske", "Brr. Hier is het altijd nacht."))));
        List<ZeldaHoofdstuk> alle = new ArrayList<>();
        for (ZeldaEiland e : EILANDEN) alle.addAll(e.delen());
        HOOFDSTUKKEN = List.copyOf(alle);
        zee = new ZeldaZee(plugin);
        spel = new ZeldaSpel(plugin, this, EILANDEN, zee);
    }

    @Override public String id() { return "zelda"; }
    @Override public String naam() { return "Story of Zelda"; }
    @Override public TextColor kleur() { return TextColor.color(0x8fcf6a); }
    @Override public Material icoon() { return Material.MAP; }
    @Override public String omschrijving() { return "avontuur · alleen of samen"; }
    @Override public Status status() { return Status.OPEN; }

    @Override
    public PoortStijl poort() {
        return new PoortStijl("west", Material.MOSSY_STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.VERDANT_FROGLIGHT,
                Material.MOSSY_STONE_BRICKS, Material.GREEN_WALL_BANNER, Material.GREEN_WALL_BANNER, Color.fromRGB(0x8fcf6a));
    }

    @Override
    public List<Deel> delen() {
        List<Deel> d = new ArrayList<>();
        for (ZeldaHoofdstuk h : HOOFDSTUKKEN) d.add(new Deel(h.deelId(), h.versie(), b -> {
            h.bouw(b);
            ZeldaFeest.naBouw(h);                                       // Zelda's brieven bij Teun, Ys, Okker en Wolf (na het feest)
        }));
        d.add(new Deel(zee.deelId(), zee.versie(), zee::bouw));
        return d;
    }

    @Override
    public void aan(KitPvP p) {
        spel.aan();
    }

    @Override
    public void uit() {
        spel.uit();
    }

    @Override
    public void join(Player p) {
        ZeldaVoortgang.Stand s = spel.voortgang.van(p.getUniqueId());
        plugin.naarSpel(p, this, spel.hoofdstuk(s.hoofdstuk).id());
        spel.binnen(p);
    }

    @Override
    public void leave(Player p) {
        spel.weg(p);
    }

    /** Geen huisdier (plugin Huisdieren) in Zelda: het leidt af (Jelle 27-10); een eigen dier komt later in het verhaal. */
    @Override
    public String stand(Player p, Toestand t) {
        return "geen";
    }

    @Override
    public Location respawn(Player p, Toestand t) {
        return spel.respawn(p);
    }

    @Override
    public String plek(Player p, Toestand t) {
        ZeldaSpel.Sessie ss = spel.sessie(p);
        if (ss == null) return naam();
        return ss.vaart != null ? zee.plekNaam(p.getLocation()) : ss.h.plekNaam(p.getLocation());
    }

    // ── zelftest ────────────────────────────────────────────────────────────────────────────

    private static boolean vrij(Location l) {
        return l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private static String xyz(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private static boolean inGebied(ZeldaHoofdstuk.Box b) {
        return b.x1() >= GEBIED[0] && b.x2() <= GEBIED[2] && b.z1() >= GEBIED[1] && b.z2() <= GEBIED[3];
    }

    @Override
    public void zelftest(List<String> fouten, List<String> info) {
        if (status() != Status.OPEN) fouten.add("zelda: niet open");
        if (spel.voortgang == null) {
            fouten.add("zelda: niet aan");
            return;
        }
        YamlConfiguration gebouwd = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gebouwd.yml"));
        Bouwer b = plugin.bouwer();
        boolean klaar = b == null || !b.bezig();
        int kamers = 0, uitd = 0, puz = 0, vond = 0, barsten = 0, haken = 0, spieg = 0, poorten = 0, getijden = 0, graven = 0, glij = 0, ijs = 0, hitten = 0, ritmes = 0, nevels = 0, sluipen = 0,
                bruggen = 0, vallen = 0;
        for (ZeldaHoofdstuk h : HOOFDSTUKKEN) {
            String hid = "zelda " + h.id();
            if (klaar && gebouwd.getInt("delen." + h.deelId(), -1) != h.versie())
                fouten.add(hid + ": deel " + h.deelId() + " niet gebouwd (gebouwd.yml " + gebouwd.getInt("delen." + h.deelId(), -1) + ", verwacht " + h.versie() + ")");
            if (h.w == null || h.speelgebied == null) {
                fouten.add(hid + ": niet geladen");
                continue;
            }
            if (!inGebied(h.speelgebied)) fouten.add(hid + ": speelgebied buiten het Zelda-gebied");
            if (h.rust.size() < 2) fouten.add(hid + ": te weinig rustplaatsen (" + h.rust.size() + ")");
            for (int i = 0; i < h.rust.size(); i++)
                if (h.rust.get(i).index != i) fouten.add(hid + ": rustplaats " + h.rust.get(i).id + " heeft nummer " + h.rust.get(i).index + " op plek " + i + " (nummers 0 … n−1)");
            for (ZeldaHoofdstuk.Rust r : h.rust) {
                if (!h.speelgebied.in(r.plek) || !r.zone.in(r.plek)) fouten.add(hid + ": rustplaats " + r.id + " ligt niet in zijn zone/speelgebied");
                if (!klaar) continue;
                if (!vrij(r.plek)) fouten.add(hid + ": rustplaats " + r.id + " zit vast op " + xyz(r.plek));
                else if (!r.plek.clone().add(0, -1, 0).getBlock().getType().isSolid()) fouten.add(hid + ": rustplaats " + r.id + " zonder vloer op " + xyz(r.plek));
            }
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                kamers++;
                if (!inGebied(k.binnen) || !k.start.binnen(k.binnen)) fouten.add(hid + ": kamer " + k.id + ": start ligt niet in de kamer");
                if (k.golven.isEmpty() || k.spawns.isEmpty()) fouten.add(hid + ": kamer " + k.id + ": geen golven of spawns");
                if (!klaar) continue;
                if (k.bel != null && h.w.getBlockAt(k.bel[0], k.bel[1], k.bel[2]).getType() != Material.BELL)
                    fouten.add(hid + ": kamer " + k.id + ": geen bel op " + k.bel[0] + "," + k.bel[1] + "," + k.bel[2]);
                for (Location l : k.spawns) {
                    if (!vrij(l)) fouten.add(hid + ": kamer " + k.id + ": spawn zit vast op " + xyz(l));
                    else if (!l.clone().add(0, -1, 0).getBlock().getType().isSolid()) fouten.add(hid + ": kamer " + k.id + ": spawn zonder vloer op " + xyz(l));
                }
                if (k.fase == ZeldaHoofdstuk.Fase.RUST) {
                    if (!deurKlopt(h, k.in, true)) fouten.add(hid + ": kamer " + k.id + ": ingang is dicht");
                    if (!deurKlopt(h, k.uit, k.alleenBel)) fouten.add(hid + ": kamer " + k.id + ": uitgang is " + (k.alleenBel ? "dicht (een oefenplek is open)" : "open"));
                }
                if (k.alleenBel && k.bel == null) fouten.add(hid + ": kamer " + k.id + ": alleen met de bel, maar er is geen bel");
            }
            for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) {
                uitd++;
                if (u.seconden < 5) fouten.add(hid + ": uitdaging " + u.id + ": te kort");
                if (klaar && u.loopt.isEmpty() && !deurKlopt(h, u.poort, false) && System.currentTimeMillis() > u.openTot)
                    fouten.add(hid + ": uitdaging " + u.id + ": poort open terwijl niemand loopt");
            }
            for (ZeldaHoofdstuk.Reeks r : h.reeksen) {
                puz++;
                if (r.dingen.isEmpty()) fouten.add(hid + ": puzzel " + r.id + ": niets om aan te zetten");
                if (!klaar || r.opgelost || !r.isAan.isEmpty()) continue;
                for (int[] d : r.dingen)
                    if (h.w.getBlockAt(d[0], d[1], d[2]).getType() != r.uit.getMaterial())
                        fouten.add(hid + ": puzzel " + r.id + ": " + d[0] + "," + d[1] + "," + d[2] + " is " + h.w.getBlockAt(d[0], d[1], d[2]).getType());
                if (!deurKlopt(h, r.deur, false)) fouten.add(hid + ": puzzel " + r.id + ": deur staat al open");
            }
            vond += h.vondsten.size();
            for (ZeldaHoofdstuk.Vondst v : h.vondsten) if (!inGebied(v.zone)) fouten.add(hid + ": vondst " + v.vlag + " buiten het gebied");
            boolean einde = h.verhalen.stream().anyMatch(v -> v.einde);
            if (!einde) fouten.add(hid + ": geen einde");
            // Zelda 2.0, eiland 1: rustig (hoogstens twee verplichte kamers vóór de baas), gebarsten muren, haakpunten, personen
            int verplicht = ZeldaRegels.verplichtVoorBaas(h.volgorde);
            if (verplicht > 2) fouten.add(hid + ": " + verplicht + " verplichte kamers vóór de baas (hoogstens 2)");
            for (ZeldaHoofdstuk.Barst br : h.barsten) {
                barsten++;
                if (!inGebied(br.gebied) || !br.muur.omvang().binnen(br.gebied)) fouten.add(hid + ": gebarsten muur " + br.id + " ligt niet in zijn gebied");
                if (klaar && !br.muur.isOpen && !deurKlopt(h, br.muur, false)) fouten.add(hid + ": gebarsten muur " + br.id + " staat open");
                if (klaar && br.voor != null && (!vrij(br.voor) || !br.gebied.in(br.voor))) fouten.add(hid + ": gebarsten muur " + br.id + ": de plek ervoor is niet vrij");
            }
            // Zelda 2.0, eiland 2: spiegels (het licht komt met de oplossing bij het doel, niet al aan het begin)
            for (ZeldaHoofdstuk.Spiegels sp : h.spiegels) {
                spieg++;
                if (!inGebied(sp.gebied) || sp.goed.stream().allMatch(java.util.Objects::isNull)) fouten.add(hid + ": spiegels " + sp.id + " buiten het gebied of zonder oplossing");
                if (!klaar) continue;
                if (!spel.straal(sp, sp.oplossing()).raak()) fouten.add(hid + ": spiegels " + sp.id + ": met de oplossing komt het licht niet bij het doel");
                if (sp.opgelost) continue;
                if (spel.straal(sp, sp.begin).raak()) fouten.add(hid + ": spiegels " + sp.id + ": aan het begin al opgelost");
                for (int[] c : sp.spiegels)
                    if (h.w.getBlockAt(c[0], c[1], c[2]).getType() != ZeldaHoofdstuk.Spiegels.BLOK) fouten.add(hid + ": spiegels " + sp.id + ": geen spiegel op " + c[0] + "," + c[1] + "," + c[2]);
                if (!deurKlopt(h, sp.deur, false)) fouten.add(hid + ": spiegels " + sp.id + ": deur staat al open");
            }
            // bouwregel: per half eiland hoogstens één verplichte kamer (plus de baas); oefenplekken (alleen met de bel) tellen niet
            long moet = h.kamers.stream().filter(k -> !k.vrijwillig && !k.alleenBel && k.baas == null).count();
            if (moet > 1) fouten.add(hid + ": " + moet + " verplichte kamers (hoogstens 1 per half eiland)");
            // poorten voor wie iets heeft (het lichtwoord): dicht in rust, en niet voor iedereen open
            for (ZeldaHoofdstuk.Poort po : h.poorten) {
                poorten++;
                if (!inGebied(po.gebied) || !po.deur.omvang().binnen(po.gebied)) fouten.add(hid + ": poort " + po.id + " ligt niet in zijn gebied");
                if (po.mag.test(new ZeldaVoortgang.Stand())) fouten.add(hid + ": poort " + po.id + " gaat voor iedereen open");
                if (klaar && !po.deur.isOpen && !deurKlopt(h, po.deur, false)) fouten.add(hid + ": poort " + po.id + " staat open");
            }
            // eb en vloed (eiland 2b): in rust zoals het hoort (water, sluizen, vloeddeuren), hendels op hun plek, niet voor iedereen
            for (ZeldaHoofdstuk.Getij g : h.getijden) {
                getijden++;
                if (!inGebied(g.gebied) || g.lagen.isEmpty() || g.hendels.isEmpty()) fouten.add(hid + ": getij " + g.id + " zonder lagen of hendels, of buiten het gebied");
                for (ZeldaHoofdstuk.Getij.Hendel hd : g.hendels) {
                    if (hd.mag().test(new ZeldaVoortgang.Stand())) fouten.add(hid + ": getij " + g.id + ": een hendel werkt voor iedereen");
                    if (klaar && h.w.getBlockAt(hd.x(), hd.y(), hd.z()).getType() != Material.LEVER) fouten.add(hid + ": getij " + g.id + ": geen hendel op " + hd.x() + "," + hd.y() + "," + hd.z());
                }
                if (!klaar || g.bezig() || g.vloed != g.rustVloed) continue;
                for (ZeldaHoofdstuk.Deur l : g.lagen)
                    if (!deurKlopt(h, l, !g.rustVloed)) fouten.add(hid + ": getij " + g.id + ": een laag staat niet in rust (" + (g.rustVloed ? "vloed" : "eb") + ")");
                for (ZeldaHoofdstuk.Deur d : g.sluizen) if (!deurKlopt(h, d, !g.rustVloed)) fouten.add(hid + ": getij " + g.id + ": een sluis staat verkeerd");
                for (ZeldaHoofdstuk.Deur d : g.vloeddeuren) if (!deurKlopt(h, d, g.rustVloed)) fouten.add(hid + ": getij " + g.id + ": een vloeddeur staat verkeerd");
            }
            if (h instanceof ZeldaEiland2b e2b && !e2b.teunOpPad) fouten.add(hid + ": Teun (2a, de ankersleutel) staat niet op het pad");
            if (h instanceof ZeldaEiland3b e3b && !e3b.hagenOpPad) fouten.add(hid + ": Hagen (3a, het wachtersharnas) staat niet op het pad");
            if (h instanceof ZeldaEiland4b e4b && !e4b.okkerOpPad) fouten.add(hid + ": Okker (4a, het Gloedschild) staat niet op het pad");
            if (h instanceof ZeldaEiland5a e5a && !e5a.miraOpPad) fouten.add(hid + ": Mira (2a, het lichtharnas) staat niet op het pad");
            if (h instanceof ZeldaEiland5b e5b && !e5b.feestOpPad) fouten.add(hid + ": Zelda op het feest (het Lindewoud) staat niet op het pad");
            if (h instanceof ZeldaEiland5b e5b && !e5b.zegelOpPad) fouten.add(hid + ": Tijmen bij de torenpoort (5a, het zegel) staat niet op het pad");
            // glijvloeren (eiland 3b): start en doel om te staan, ijs onder het ijs, en met de oplossing kom je aan de overkant
            for (ZeldaHoofdstuk.Glij gl : h.glijvloeren) {
                glij++;
                String gid = hid + ": glijvloer " + gl.id;
                if (!inGebied(gl.gebied) || gl.ijs.isEmpty() || gl.oplossing.isEmpty()) fouten.add(gid + " buiten het gebied, zonder ijs of zonder oplossing");
                if (!gl.gebied.in(gl.start[0] + 0.5, gl.y + 1, gl.start[1] + 0.5) || !gl.gebied.in(gl.doel[0] + 0.5, gl.y + 1, gl.doel[1] + 0.5) || gl.isIjs(gl.start[0], gl.start[1]) || gl.isIjs(gl.doel[0], gl.doel[1]))
                    fouten.add(gid + ": start of doel ligt niet in de zaal (of op het ijs)");
                if (!klaar) continue;
                for (int[] c : new int[][]{gl.start, gl.doel}) {
                    Location l = new Location(h.w, c[0] + 0.5, gl.y + 1, c[1] + 0.5);
                    if (!vrij(l) || !l.clone().add(0, -1, 0).getBlock().getType().isSolid()) fouten.add(gid + ": " + xyz(l) + " is niet om te staan");
                }
                int geenIjs = 0;
                for (long k : gl.ijs) {
                    int x = (int) (k >> 32), z = (int) k;
                    Material m = h.w.getBlockAt(x, gl.y, z).getType();
                    if (m != Material.BLUE_ICE && m != Material.PACKED_ICE && m != Material.ICE) geenIjs++;
                }
                if (geenIjs > 0) fouten.add(gid + ": " + geenIjs + " ijsvakjes zonder ijs");
                int[] c = gl.start.clone();
                for (int i = 0; i < gl.oplossing.size(); i++) c = spel.glijNaar(gl, c[0], c[1], gl.oplossing.get(i));
                if (c[0] != gl.doel[0] || c[1] != gl.doel[1]) fouten.add(gid + ": met de oplossing kom je op " + c[0] + "," + c[1] + " (doel " + gl.doel[0] + "," + gl.doel[1] + ")");
            }
            // vuurpuzzels (eiland 4a): van de plek om te mikken zie je elke vuurschaal of lamp, binnen het bereik van de vuurstaf
            for (ZeldaHoofdstuk.Reeks r : h.reeksen) {
                if (r.hoe != ZeldaHoofdstuk.Reeks.Hoe.VUUR) continue;
                if (r.voor == null) {
                    fouten.add(hid + ": puzzel " + r.id + ": geen plek om te mikken (voor)");
                    continue;
                }
                if (!klaar) continue;
                if (!vrij(r.voor)) fouten.add(hid + ": puzzel " + r.id + ": de plek om te mikken is niet vrij op " + xyz(r.voor));
                for (int[] d : r.dingen)
                    if (laad(r.voor, d) && !spel.zicht(r.voor.clone().add(0, 1.62, 0), h.w.getBlockAt(d[0], d[1], d[2])))
                        fouten.add(hid + ": puzzel " + r.id + ": " + d[0] + "," + d[1] + "," + d[2] + " niet te zien vanaf " + xyz(r.voor) + ": je ziet "
                                + spel.zichtRaakt(r.voor.clone().add(0, 1.62, 0), h.w.getBlockAt(d[0], d[1], d[2])));
            }
            // betoverd ijs (eiland 4a): in zijn gebied, dicht in rust, van de plek ervoor te zien
            for (ZeldaHoofdstuk.Smelt sm : h.smelten) {
                ijs++;
                String sid = hid + ": ijs " + sm.id;
                if (!inGebied(sm.gebied) || !sm.ijs.omvang().binnen(sm.gebied)) fouten.add(sid + " ligt niet in zijn gebied");
                if (sm.ijs.dicht.getMaterial() != ZeldaHoofdstuk.Smelt.IJS) fouten.add(sid + " is geen " + ZeldaHoofdstuk.Smelt.IJS);
                if (!klaar) continue;
                if (!sm.ijs.isOpen && !deurKlopt(h, sm.ijs, false)) fouten.add(sid + " is gesmolten terwijl niemand het smolt");
                if (sm.voor != null) {
                    boolean zien = false;
                    for (int[] c : sm.ijs.blokken) zien |= laad(sm.voor, c) && spel.zicht(sm.voor.clone().add(0, 1.62, 0), h.w.getBlockAt(c[0], c[1], c[2]));
                    if (!vrij(sm.voor) || !sm.gebied.in(sm.voor)) fouten.add(sid + ": de plek ervoor is niet vrij of ligt niet in het gebied");
                    else if (!zien && !sm.ijs.isOpen) {
                        int[] c = sm.ijs.blokken.get(sm.ijs.blokken.size() / 2);
                        fouten.add(sid + ": van de plek ervoor niet te zien: je ziet " + spel.zichtRaakt(sm.voor.clone().add(0, 1.62, 0), h.w.getBlockAt(c[0], c[1], c[2])));
                    }
                }
            }
            // hitte (eiland 4a): een plek om heen te gaan als het te heet wordt, en een koelplek erin (water, zielvuur of bron)
            for (ZeldaHoofdstuk.Hitte ht : h.hitten) {
                hitten++;
                String hd = hid + ": hitte " + ht.id;
                if (!inGebied(ht.gebied) || ht.terug == null || ht.snel <= 0) fouten.add(hd + ": buiten het gebied, zonder plek om terug te gaan of zonder hitte");
                if (!ht.fel && (ht.koel <= 0 || ht.koel >= ht.snel)) fouten.add(hd + ": de koelsteen helpt niet");
                boolean koel = ht.fel;
                for (ZeldaHoofdstuk.Rust r : h.rust) koel |= ht.gebied.in(r.plek);
                for (ZeldaHoofdstuk.Bron br : h.bronnen) koel |= ht.gebied.in(br.midden);
                for (ZeldaHoofdstuk.Smelt sm : h.smelten) koel |= sm.ijs.open.getMaterial() == Material.WATER && ht.gebied.in(sm.ijs.omvang().midden(h.w));
                if (!koel) fouten.add(hd + ": geen koelplek (water, zielvuur of bron) in de hitte");
                if (klaar && ht.terug != null && !vrij(ht.terug)) fouten.add(hd + ": de plek om terug te gaan is niet vrij op " + xyz(ht.terug));
                if (ht.proef != null && (!ht.gebied.in(ht.proef) || klaar && !vrij(ht.proef))) fouten.add(hd + ": de proefplek ligt niet vrij in de hitte");
            }
            // een baas met ijszuilen (Grimmel): de zuilen staan er als er niet gevochten wordt, in zijn kamer
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                if (!(k.baas instanceof Grimmel gr)) continue;
                if (!k.haken) fouten.add(hid + ": kamer " + k.id + ": de klauwhaak werkt niet in het gevecht met " + gr.naam);
                for (int i = 0; i < gr.zuilen.size(); i++) {
                    int[] c = gr.zuilen.get(i);
                    if (!k.binnen.in(c[0], c[4], c[1]) || !k.binnen.in(c[2], c[5], c[3])) fouten.add(hid + ": kamer " + k.id + ": zuil " + i + " ligt niet in de kamer");
                    if (klaar && k.fase == ZeldaHoofdstuk.Fase.RUST && h.w.getBlockAt(c[0], c[4], c[1]).getType() != Grimmel.zuilBlok(c, c[4])) fouten.add(hid + ": kamer " + k.id + ": zuil " + i + " is weg");
                }
                boolean haak = false;
                for (ZeldaHoofdstuk.Haakpunt hp : h.haakpunten) haak |= k.binnen.in(hp.landing());
                if (!haak) fouten.add(hid + ": kamer " + k.id + ": geen haakpunt om je weg te trekken");
            }
            // lava op een ritme (eiland 4b): in zijn gebied, in rust liggen de stenen droog, de plek ervoor is vrij
            for (ZeldaHoofdstuk.Ritme rt : h.ritmes) {
                ritmes++;
                String rid = hid + ": ritme " + rt.id;
                if (!inGebied(rt.gebied) || !rt.lava.omvang().binnen(rt.gebied) || rt.laag < 2000 || rt.hoog < 500) fouten.add(rid + ": buiten zijn gebied, of te snel");
                if (!klaar) continue;
                if (!rt.isHoog && !deurKlopt(h, rt.lava, true)) fouten.add(rid + ": de stenen liggen niet droog");
                if (rt.voor != null && (!vrij(rt.voor) || !rt.gebied.in(rt.voor))) fouten.add(rid + ": de plek ervoor is niet vrij of ligt niet in het gebied");
            }
            // een baas met koelkuipen (Asgrim): de kuipen hangen vol als er niet gevochten wordt, de plekken liggen in de kamer
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                if (!(k.baas instanceof Asgrim a)) continue;
                for (int i = 0; i < a.kuipen.size(); i++) {
                    int[] c = a.kuipen.get(i);
                    if (!k.binnen.in(c[0], c[1], c[2]) || !k.binnen.in(a.grond.get(i)) || !k.binnen.in(a.landing.get(i))) fouten.add(hid + ": kamer " + k.id + ": kuip " + i + " ligt niet in de kamer");
                    if (!klaar) continue;
                    if (k.fase == ZeldaHoofdstuk.Fase.RUST && h.w.getBlockAt(c[0], c[1], c[2]).getType() != Material.WATER_CAULDRON) fouten.add(hid + ": kamer " + k.id + ": kuip " + i + " is niet vol");
                    if (!a.grond.get(i).getBlock().isPassable()) fouten.add(hid + ": kamer " + k.id + ": zweefplek " + i + " zit vast");
                    Location l = a.landing.get(i);
                    if (!vrij(l) || !l.clone().add(0, -1, 0).getBlock().getType().isSolid()) fouten.add(hid + ": kamer " + k.id + ": landing " + i + " niet vrij of zonder vloer");
                }
            }
            // Morgals nevel (eiland 5a): in zijn gebied, dicht in rust, en te zien (te raken) vanaf de plek ervoor
            for (ZeldaHoofdstuk.Nevel nv : h.nevels) {
                nevels++;
                String nid = hid + ": nevel " + nv.id;
                if (!inGebied(nv.gebied) || !nv.nevel.omvang().binnen(nv.gebied)) fouten.add(nid + " ligt niet in zijn gebied");
                if (nv.brug) {                                          // een nevelbrug (5b): in de nevel lucht, met licht een brug; vanaf de plek ervoor te zien
                    bruggen++;
                    if (!nv.nevel.dicht.getMaterial().isAir() || !nv.nevel.open.getMaterial().isSolid()) fouten.add(nid + ": een nevelbrug is lucht in de nevel en een brug in het licht");
                    if (nv.voor == null) fouten.add(nid + ": geen plek om erop te schieten (voor)");
                    if (!klaar || nv.voor == null) continue;
                    if (!nv.nevel.isOpen && !deurKlopt(h, nv.nevel, false)) fouten.add(nid + " is er al terwijl niemand hem zag");
                    Location oog = nv.voor.clone().add(0, 1.62, 0), m = nv.nevel.omvang().midden(h.w).add(0, 0.6, 0);
                    laad(nv.voor, new int[]{m.getBlockX(), m.getBlockY(), m.getBlockZ()});
                    var r = h.w.rayTraceBlocks(oog, m.toVector().subtract(oog.toVector()).normalize(), oog.distance(m), org.bukkit.FluidCollisionMode.NEVER, true);
                    if (!vrij(nv.voor) || !nv.gebied.in(nv.voor)) fouten.add(nid + ": de plek ervoor is niet vrij of ligt niet in het gebied");
                    else if (r != null && r.getHitBlock() != null)
                        fouten.add(nid + ": van de plek ervoor niet te zien: je ziet " + r.getHitBlock().getType() + " op " + r.getHitBlock().getX() + "," + r.getHitBlock().getY() + "," + r.getHitBlock().getZ());
                    continue;
                }
                if (nv.nevel.dicht.getMaterial() != ZeldaHoofdstuk.Nevel.BLOK) fouten.add(nid + " is geen " + ZeldaHoofdstuk.Nevel.BLOK);
                if (!klaar) continue;
                if (!nv.nevel.isOpen && !deurKlopt(h, nv.nevel, false)) fouten.add(nid + " is weg terwijl niemand hem wegschoot");
                if (nv.voor != null) {
                    boolean zien = false;
                    for (int[] c : nv.nevel.blokken) zien |= laad(nv.voor, c) && spel.zicht(nv.voor.clone().add(0, 1.62, 0), h.w.getBlockAt(c[0], c[1], c[2]));
                    if (!vrij(nv.voor) || !nv.gebied.in(nv.voor)) fouten.add(nid + ": de plek ervoor is niet vrij of ligt niet in het gebied");
                    else if (!zien && !nv.nevel.isOpen) fouten.add(nid + ": van de plek ervoor niet te zien");
                }
            }
            // vangnetten (eiland 5b): in het gebied, en een proefplek in de lucht (vrij)
            for (ZeldaHoofdstuk.Val v : h.vallen) {
                vallen++;
                if (!inGebied(v.gebied())) fouten.add(hid + ": vangnet " + v.id() + " buiten het Zelda-gebied");
                if (v.proef() != null && (!v.gebied().in(v.proef()) || klaar && !v.proef().getBlock().isPassable())) fouten.add(hid + ": vangnet " + v.id() + ": de proefplek ligt niet vrij in het vangnet");
            }
            // Morgal (eiland 5b): zijn plekken liggen in de troonzaal, vrij (en wie op de vloer staat, heeft een vloer)
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                if (!(k.baas instanceof Morgal mg)) continue;
                List<Location> zweef = new ArrayList<>(List.of(mg.boven, mg.hart)), staan = new ArrayList<>(mg.vloer);
                staan.add(mg.neer);
                zweef.addAll(mg.geestPlekken);
                for (Location l : zweef) {
                    if (!k.binnen.in(l)) fouten.add(hid + ": kamer " + k.id + ": " + mg.naam + " (" + xyz(l) + ") ligt niet in de kamer");
                    else if (klaar && !vrij(l)) fouten.add(hid + ": kamer " + k.id + ": " + mg.naam + " zit vast op " + xyz(l));
                }
                for (Location l : staan) {
                    if (!k.binnen.in(l)) fouten.add(hid + ": kamer " + k.id + ": " + mg.naam + " (" + xyz(l) + ") ligt niet in de kamer");
                    else if (klaar && (!vrij(l) || !l.clone().add(0, -1, 0).getBlock().getType().isSolid())) fouten.add(hid + ": kamer " + k.id + ": " + mg.naam + " staat niet vrij op " + xyz(l));
                }
            }
            // sluipen (eiland 5a): het hek open in rust, een pad om te staan, schuilplekken die niemand ziet, en elk stuk tussen
            // twee schuilplekken is op een deel van het ritme te sluipen (met wat speling, zoals een kind loopt)
            for (ZeldaHoofdstuk.Sluip sl : h.sluipen) {
                sluipen++;
                String sid = hid + ": sluipen " + sl.id;
                if (!inGebied(sl.gebied) || !sl.finish.binnen(sl.gebied.groter(2)) || sl.wachters.isEmpty() || sl.periode < 4000) fouten.add(sid + ": buiten het gebied, zonder wachters of een te snel ritme");
                int[] index = new int[sl.pad.size()];
                List<int[]> cellen = ZeldaSpel.cellen(sl, index);
                if (sl.pad.size() < 2 || cellen == null) {
                    fouten.add(sid + ": geen pad (rechte stukken, langs x of z)");
                    continue;
                }
                if (!sl.finish.in(sl.pad.get(sl.pad.size() - 1))) fouten.add(sid + ": het pad eindigt niet in de finish");
                if (!klaar) continue;
                if (!vrij(sl.terug) || !sl.terug.clone().add(0, -1, 0).getBlock().getType().isSolid()) fouten.add(sid + ": de plek terug (" + xyz(sl.terug) + ") is niet om te staan");
                if (sl.hekOpen == 0 && !sl.hek.isOpen) fouten.add(sid + ": het hek is dicht in rust");
                for (int[] c : cellen) laad(sl.pad.get(0), c);
                for (int[] c : cellen) {
                    Location l = new Location(h.w, c[0] + 0.5, c[1], c[2] + 0.5);
                    if (!vrij(l) || !l.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                        fouten.add(sid + ": het pad gaat over " + xyz(l) + " (niet om te staan)");
                        break;
                    }
                }
                for (ZeldaHoofdstuk.Sluip.Wachter wa : sl.wachters)
                    for (double[] st : wa.standen) {
                        Location l = new Location(h.w, st[1], st[2], st[3]);
                        if (!vrij(l) || !sl.gebied.groter(2).in(l)) fouten.add(sid + ": " + wa.naam + " staat vast op " + xyz(l));
                    }
                int stappen = (int) (sl.periode / 100);
                boolean[][] tabel = spel.zichtTabel(sl, cellen, stappen);
                List<Integer> rust = new ArrayList<>();
                rust.add(0);
                for (int i = 1; i < sl.pad.size(); i++) if (sl.schuil.contains(i) || i == sl.pad.size() - 1) rust.add(i);
                for (int i : rust) {
                    if (i == sl.pad.size() - 1) continue;
                    for (int t = 0; t < stappen; t++)
                        if (tabel[t][index[i]]) {
                            fouten.add(sid + ": schuilplek " + xyz(sl.pad.get(i)) + " wordt gezien");
                            break;
                        }
                }
                for (int r = 0; r + 1 < rust.size(); r++) {
                    int van = index[rust.get(r)], tot = index[rust.get(r + 1)];
                    int v = ZeldaRegels.vensters(tabel, van, tot, 3);
                    if (v * 20 < stappen) fouten.add(sid + ": van " + xyz(sl.pad.get(rust.get(r))) + " naar " + xyz(sl.pad.get(rust.get(r + 1)))
                            + " kom je " + (v == 0 ? "nooit" : "bijna nooit (" + v + " van " + stappen + ")") + " ongezien");
                }
            }
            // een baas met kruittonnen (Zoutbaard): de tonnen staan er als er niet gevochten wordt, en hij kan bij elke ton staan
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                if (!(k.baas instanceof Zoutbaard zb)) continue;
                for (int i = 0; i < zb.tonnen.size(); i++) {
                    int[] t = zb.tonnen.get(i);
                    if (!k.binnen.in(t[0], t[1], t[2]) || !k.binnen.in(zb.grond.get(i))) fouten.add(hid + ": kamer " + k.id + ": ton " + i + " ligt niet in de kamer");
                    if (klaar && k.fase == ZeldaHoofdstuk.Fase.RUST && h.w.getBlockAt(t[0], t[1], t[2]).getType() != Material.BARREL) fouten.add(hid + ": kamer " + k.id + ": ton " + i + " is weg");
                    if (klaar && (!vrij(zb.grond.get(i)) || !zb.grond.get(i).clone().add(0, -1, 0).getBlock().getType().isSolid())) fouten.add(hid + ": kamer " + k.id + ": plek bij ton " + i + " niet vrij");
                }
            }
            for (ZeldaHoofdstuk.Graaf gr : h.graven) {                    // graafplekken (een dier graaft er iets op)
                graven++;
                if (!h.speelgebied.in(gr.plek())) fouten.add(hid + ": graafplek " + gr.id() + " buiten het speelgebied");
                if (klaar && (!vrij(gr.plek()) || !gr.plek().clone().add(0, -1, 0).getBlock().getType().isSolid()))
                    fouten.add(hid + ": graafplek " + gr.id() + " niet vrij of zonder vloer op " + xyz(gr.plek()));
            }
            for (ZeldaHoofdstuk.Haakpunt hp : h.haakpunten) {
                haken++;
                if (!h.speelgebied.in(hp.landing())) fouten.add(hid + ": haakpunt " + hp.id() + " buiten het speelgebied");
                if (!klaar) continue;
                if (h.w.getBlockAt(hp.x(), hp.y(), hp.z()).getType() != ZeldaHoofdstuk.HAAKPUNT) fouten.add(hid + ": haakpunt " + hp.id() + " is " + h.w.getBlockAt(hp.x(), hp.y(), hp.z()).getType());
                if (!vrij(hp.landing()) || !hp.landing().clone().add(0, -1, 0).getBlock().getType().isSolid()) fouten.add(hid + ": haakpunt " + hp.id() + ": landing niet vrij of zonder vloer");
            }
            if (klaar) for (ZeldaHoofdstuk.Persoon pe : h.personen)
                if (!vrij(pe.plek)) fouten.add(hid + ": " + pe.naam + " staat vast op " + xyz(pe.plek));
        }
        // Zelda 2.0: eilanden, genezen bij elke kamer, doelen voor /hint, personen, muziek overal, de zee
        int bronnen = 0, doelen = 0, personen = 0, missies = 0, stukjes = 0;
        java.util.Set<String> mids = new java.util.HashSet<>(), alleStukjes = new java.util.HashSet<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        List<ZeldaHoofdstuk> allen = new ArrayList<>(HOOFDSTUKKEN);
        allen.add(zee);
        for (ZeldaHoofdstuk h : allen) if (!ids.add(h.id())) fouten.add("zelda: dubbel deel-id " + h.id());
        for (int i = 0; i < allen.size(); i++)
            for (int j = i + 1; j < allen.size(); j++) {
                ZeldaHoofdstuk.Box a = allen.get(i).speelgebied, c = allen.get(j).speelgebied;
                if (a != null && c != null && a.x1() <= c.x2() && c.x1() <= a.x2() && a.z1() <= c.z2() && c.z1() <= a.z2())
                    fouten.add("zelda: speelgebied van " + allen.get(i).id() + " en " + allen.get(j).id() + " overlappen");
            }
        java.util.Set<String> eids = new java.util.HashSet<>();
        for (ZeldaEiland e : EILANDEN) {
            String eid = "zelda eiland " + e.id();
            if (!eids.add(e.id())) fouten.add(eid + ": dubbel");
            if (e.aanlegRust() == null) fouten.add(eid + ": aanleg " + e.aanleg() + " is geen rustplaats van " + e.delen().get(0).id());
            List<ZeldaHoofdstuk.Doel> ed = new ArrayList<>();
            for (ZeldaHoofdstuk h : e.delen()) ed.addAll(h.doelen);
            doelen += ed.size();
            if (ed.isEmpty()) fouten.add(eid + ": geen doelen (voor /hint)");
            for (ZeldaHoofdstuk.Doel d : ed)
                if (d.plek() == null || e.deelBij(d.plek()) == null) fouten.add(eid + ": doel " + d.id() + " ligt niet op het eiland");
            java.util.Set<String> hs = new java.util.HashSet<>(), vlaggen = new java.util.HashSet<>();
            for (ZeldaHoofdstuk h : e.delen()) {
                hs.addAll(h.stukjes);
                for (ZeldaHoofdstuk.Vondst v : h.vondsten) {
                    if (!vlaggen.add(v.vlag)) fouten.add(eid + ": vondst " + v.vlag + " dubbel");
                    if (v.voorwerp != null && !ZeldaVoorwerp.LATER.contains(v.voorwerp) && spel.voorwerpen.stream().noneMatch(x -> x.vlag().equals(v.voorwerp)))
                        fouten.add(eid + ": vondst " + v.vlag + " wacht op onbekend voorwerp " + v.voorwerp);
                }
                for (String st : h.stukjes) if (!alleStukjes.add(st)) fouten.add(eid + ": hartstukje " + st + " dubbel");
                for (ZeldaHoofdstuk.Missie m : h.missies) if (!mids.add(m.id())) fouten.add(eid + ": missie " + m.id() + " dubbel");
            }
            if (hs.size() > 4) fouten.add(eid + ": " + hs.size() + " hartstukjes (hoogstens 4 per eiland)");
            stukjes += hs.size();
            for (ZeldaHoofdstuk h : e.delen()) {
                bronnen += h.bronnen.size();
                personen += h.personen.size();
                missies += h.missies.size();
                if (h.speelgebied == null) continue;
                for (ZeldaHoofdstuk.Persoon pe : h.personen) if (!h.speelgebied.in(pe.plek)) fouten.add(eid + ": " + pe.naam + " staat buiten het speelgebied");
                for (ZeldaHoofdstuk.Bron br : h.bronnen) if (!h.speelgebied.in(br.midden)) fouten.add(eid + ": bron " + br.id + " buiten het speelgebied");
                for (ZeldaHoofdstuk.Rust r : h.rust) if (h.muziek(r.plek) == null) fouten.add(eid + ": geen muziek bij " + r.id);
                // genezen in of bij elke kamer: een rustplaats of bron binnen 32 blokken van de ingang
                for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                    Location in = k.in.omvang().midden(h.w);
                    boolean heel = false;
                    for (ZeldaHoofdstuk g : e.delen()) {
                        for (ZeldaHoofdstuk.Rust r : g.rust) heel |= r.plek.distanceSquared(in) < 32 * 32;
                        for (ZeldaHoofdstuk.Bron br : g.bronnen) heel |= br.midden.distanceSquared(in) < 32 * 32;
                    }
                    if (!heel) fouten.add(eid + ": kamer " + k.id + ": geen rustplaats of bron in de buurt (genezen)");
                }
                if (klaar) for (ZeldaHoofdstuk.Bron br : h.bronnen)
                    if (!br.midden.clone().add(0, -1, 0).getBlock().getType().isSolid() || !vrij(br.midden))
                        fouten.add(eid + ": bron " + br.id + " zonder vloer of niet vrij op " + xyz(br.midden));
            }
        }
        if (klaar && gebouwd.getInt("delen." + zee.deelId(), -1) != zee.versie())
            fouten.add("zelda zee: niet gebouwd (gebouwd.yml " + gebouwd.getInt("delen." + zee.deelId(), -1) + ", verwacht " + zee.versie() + ")");
        if (zee.w == null || zee.dek.isEmpty()) fouten.add("zelda zee: niet geladen");
        else {
            if (!inGebied(zee.speelgebied)) fouten.add("zelda zee: buiten het Zelda-gebied");
            if (zee.personen.isEmpty()) fouten.add("zelda zee: geen kapitein");
            if (klaar) for (Location l : zee.dek)
                if (!vrij(l) || !l.clone().add(0, -1, 0).getBlock().getType().isSolid()) fouten.add("zelda zee: dekplek " + xyz(l) + " niet vrij of zonder vloer");
        }
        for (ZeldaMuziek.Lied l : ZeldaMuziek.alle()) if (l.lengte() == 0) fouten.add("zelda: lied " + l.id() + " is leeg");
        for (ZeldaEiland.Komt k : KOMT) {                                // een eiland (of deel) dat eraan komt: nog niet echt gebouwd
            if (k.eiland() == null && !eids.add(k.id())) fouten.add("zelda: eiland " + k.id() + " staat ook bij KOMT (haal het daar weg)");
            ZeldaEiland op = k.eiland() == null ? null : spel.eiland(k.eiland());
            if (k.eiland() != null && op == null) fouten.add("zelda: " + k.naam() + " komt op een onbekend eiland " + k.eiland());
            if (op != null && op.delen().stream().anyMatch(d -> d.id().equals(k.openNa())) && !op.delen().get(op.delen().size() - 1).id().equals(k.openNa()))
                fouten.add("zelda: " + k.naam() + " is al een deel van " + op.naam() + " (haal het uit KOMT)");
        }
        java.util.Set<Integer> vakken = new java.util.HashSet<>();
        for (ZeldaVoorwerp v : spel.voorwerpen) if (!vakken.add(v.vak()) || v.vak() < 2 || v.vak() > 6) fouten.add("zelda: voorwerp " + v.id() + " in vak " + v.vak() + " (dubbel of buiten 2 … 6)");
        // opslag: schrijven en teruglezen in een proefbestand
        File proef = new File(plugin.getDataFolder(), "zelda-zelftest.yml");
        try {
            ZeldaVoortgang v = new ZeldaVoortgang(proef);
            UUID id = UUID.randomUUID();
            v.van(id).bereik("h1", 3);
            v.van(id).zet("zwaard");
            v.van(id).tijd("kloof", 12345);
            v.van(id).laatste = "h1:2";
            v.van(id).missies.put("proef", 2);
            v.van(id).teller("fles-vol", 1);
            v.van(id).dier = "Pluis";
            ZeldaKaartBeeld.ontdek(v.van(id).kaart, 1212, 2, ZeldaKaartBeeld.ZICHT);
            v.bewaar();
            ZeldaVoortgang terug = new ZeldaVoortgang(proef);
            ZeldaVoortgang.Stand t = terug.van(id);
            if (t.rust("h1") != 3 || !t.heeft("zwaard") || t.beste("kloof") != 12345L || !"h1:2".equals(t.laatste) || t.missie("proef") != 2
                    || t.teller("fles-vol") != 1 || !"Pluis".equals(t.dier) || !t.bezocht("h1", 3) || !t.kaart.equals(v.van(id).kaart) || t.kaart.isEmpty())
                fouten.add("zelda: voortgang komt niet goed terug uit het bestand");
        } catch (Exception e) {
            fouten.add("zelda: voortgang bewaren mislukt: " + e);
        } finally {
            if (proef.exists() && !proef.delete()) fouten.add("zelda: proefbestand bleef staan");
        }
        int ents = 0, feeen = 0;
        if (plugin.w != null) for (Entity e : plugin.w.getEntities()) {
            if (e.getScoreboardTags().contains(ZeldaSpel.TAG)) ents++;
            if (e.getScoreboardTags().contains(ZeldaMetgezel.TAG)) feeen++;
        }
        if (ents > 60) fouten.add("zelda: " + ents + " entiteiten (te veel)");
        if (feeen > spel.sessies.size()) fouten.add("zelda: " + feeen + " feeën voor " + spel.sessies.size() + " spelers");
        if (spel.dier.aantal() > spel.sessies.size()) fouten.add("zelda: " + spel.dier.aantal() + " dieren voor " + spel.sessies.size() + " spelers");
        if (ZeldaDier.AAN) fouten.add("zelda: vrije huisdieren staan aan (ZeldaDier.AAN; Jelle wil ze uit)");
        spel.landkaart.zelftest(fouten, info);
        for (var e : spel.sessies.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && !p.isDead() && spel.metgezel.van(p) == null)
                info.add("(wiske van " + p.getName() + " nog niet in de wereld)");
        }
        info.add("zelda=" + status().name().toLowerCase() + " eilanden=" + EILANDEN.size() + " delen=" + HOOFDSTUKKEN.size() + " rust=" + HOOFDSTUKKEN.get(0).rust.size()
                + " kamers=" + kamers + " uitdagingen=" + uitd + " puzzels=" + puz + " vondsten=" + vond + " bronnen=" + bronnen + " doelen=" + doelen
                + " personen=" + personen + " missies=" + missies + " hartstukjes=" + stukjes + " barsten=" + barsten + " haakpunten=" + haken
                + " spiegels=" + spieg + " poorten=" + poorten + " getijden=" + getijden + " graafplekken=" + graven + " glijvloeren=" + glij + " ijs=" + ijs + " hitte=" + hitten + " ritmes=" + ritmes + " nevel=" + nevels + " nevelbruggen=" + bruggen + " sluipen=" + sluipen + " vangnetten=" + vallen
                + " voorwerpen=" + spel.voorwerpen.size()
                + " liedjes=" + ZeldaMuziek.alle().size()
                + " spelers=" + spel.sessies.size() + " voortgang=" + spel.voortgang.aantal() + " entiteiten=" + ents + " feeen=" + feeen + " dieren=" + spel.dier.aantal());
    }

    /** Laad de chunks tussen {@code van} en blok {@code c} (een straal door een niet-geladen chunk raakt niets). Altijd true. */
    private static boolean laad(Location van, int[] c) {
        for (int cx = Math.min(van.getBlockX(), c[0]) >> 4; cx <= Math.max(van.getBlockX(), c[0]) >> 4; cx++)
            for (int cz = Math.min(van.getBlockZ(), c[2]) >> 4; cz <= Math.max(van.getBlockZ(), c[2]) >> 4; cz++) van.getWorld().getChunkAt(cx, cz);
        return true;
    }

    private static boolean deurKlopt(ZeldaHoofdstuk h, ZeldaHoofdstuk.Deur d, boolean open) {
        int[] c = d.blokken.get(0);
        return h.w.getBlockAt(c[0], c[1], c[2]).getType() == (open ? d.open : d.dicht).getMaterial();
    }

    // ── beheer: kitpvp zelda … ──────────────────────────────────────────────────────────────

    /**
     * Zet de Zelda-voortgang van een speler terug naar het begin (online of niet). Zit hij in Zelda, dan eerst naar de hub
     * (Zelda-spullen weg, beheer uit) en daarna opnieuw naar binnen: bij het begin. Geeft één korte regel terug.
     */
    String reset(String wie) {
        Player p = Bukkit.getPlayerExact(wie);
        java.util.UUID id = p != null ? p.getUniqueId() : spel.voortgang.zoek(wie);
        if (id == null) {
            org.bukkit.OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(wie);
            if (o != null) id = o.getUniqueId();
        }
        if (id == null || !spel.voortgang.bekend(id)) return "geen Zelda-voortgang van " + wie;
        String naam = p != null ? p.getName() : spel.voortgang.van(id).naam == null || spel.voortgang.van(id).naam.isEmpty() ? wie : spel.voortgang.van(id).naam;
        boolean in = p != null && spel.speelt(p);
        if (in) plugin.naarHub(p);                           // weg(): beheer uit, spullen weg, voortgang bewaard
        spel.voortgang.wis(id);
        spel.voortgang.vuil();
        spel.bewaar();
        if (in) plugin.kiesModus(p, this);                   // opnieuw binnen: bij het begin
        return "voortgang van " + naam + " gewist" + (in ? ", terug bij het begin" : "");
    }

    void beheer(CommandSender s, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase() : "status";
        Player p = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
        switch (sub) {
            case "reset" -> {
                if (args.length < 3) {
                    s.sendMessage("kitpvp zelda reset <speler> (ook als hij niet online is)");
                    return;
                }
                s.sendMessage("zelda: " + reset(args[2]));
            }
            case "rust" -> {
                if (p == null || args.length < 4) {
                    s.sendMessage("kitpvp zelda rust <speler> <nummer>");
                    return;
                }
                ZeldaVoortgang.Stand st = spel.voortgang.van(p.getUniqueId());
                ZeldaHoofdstuk h = spel.hoofdstuk(st.hoofdstuk);
                int n;
                try {
                    n = Math.max(0, Math.min(h.rust.size() - 1, Integer.parseInt(args[3])));
                } catch (NumberFormatException e) {
                    s.sendMessage("geen nummer: " + args[3]);
                    return;
                }
                st.rust.put(h.id(), Math.max(n, st.rust(h.id())));
                st.hoofdstuk = h.id();
                st.laatste = h.id() + ":" + n;
                for (int i = 0; i <= n; i++) st.bezoek(h.id(), i);
                st.vlaggen.addAll(h.rust.get(n).geeft);
                spel.voortgang.vuil();
                spel.bewaar();
                s.sendMessage("zelda: " + p.getName() + " staat nu op rustplaats " + n + " (" + h.rust.get(n).naam + ")"
                        + (h.rust.get(n).geeft.isEmpty() ? "; spullen geef je met kitpvp zelda vlag" : ""));
            }
            case "test" -> {
                if (p == null) {
                    s.sendMessage("kitpvp zelda test <speler> [alles [vanaf <deel>]|dood|motor]: speler niet online");
                    return;
                }
                ZeldaTest t = new ZeldaTest(plugin, this, p, s);
                if (args.length >= 4 && args[3].equalsIgnoreCase("dood")) t.dood();
                else if (args.length >= 4 && args[3].equalsIgnoreCase("motor")) t.motor();
                else t.route(args.length >= 4 && args[3].equalsIgnoreCase("alles"),
                        args.length >= 6 && args[4].equalsIgnoreCase("vanaf") ? args[5].toLowerCase() : null);   // alles vanaf <deel>: alleen voor tests
            }
            case "in" -> {
                if (p == null) {
                    s.sendMessage("kitpvp zelda in <speler>: speler niet online");
                    return;
                }
                plugin.kiesModus(p, this);
                s.sendMessage("zelda: " + p.getName() + " " + (spel.speelt(p) ? "is in Zelda (" + spel.sessie(p).rustplaats().naam + ")" : "kon niet naar binnen"));
            }
            case "vaar" -> {
                ZeldaEiland e = args.length >= 4 ? spel.eiland(args[3].toLowerCase()) : null;
                if (p == null || e == null || !spel.speelt(p)) {
                    s.sendMessage("kitpvp zelda vaar <speler in Zelda> <eiland> (eilanden: " + String.join(", ", EILANDEN.stream().map(ZeldaEiland::id).toList()) + ")");
                    return;
                }
                spel.vaar(p, e);
                s.sendMessage("zelda: " + p.getName() + " vaart naar " + e.naam());
            }
            case "vlag" -> {
                if (p == null || args.length < 4) {
                    s.sendMessage("kitpvp zelda vlag <speler> <vlag> (nog eens = weg; bv. fles1, hs:test, harnas2, dier:vos, o:uitleg-uit)");
                    return;
                }
                ZeldaVoortgang.Stand st = spel.voortgang.van(p.getUniqueId());
                boolean aan = st.zet(args[3]);
                if (!aan) st.wis(args[3]);
                if (args[3].startsWith("fles")) ZeldaRegels.vulFlessen(st);
                spel.voortgang.vuil();
                spel.bewaar();
                if (spel.speelt(p)) spel.uitrusten(p, st);
                s.sendMessage("zelda: " + p.getName() + " " + args[3] + " " + (aan ? "aan" : "uit"));
            }
            case "lopen" -> {
                ZeldaEiland e = args.length >= 3 ? spel.eiland(args[2].toLowerCase()) : null;
                if (e == null) {
                    s.sendMessage("kitpvp zelda lopen <eiland> [zonder <voorwerp>] [dicht <poort>] (eilanden: " + String.join(", ", EILANDEN.stream().map(ZeldaEiland::id).toList()) + ")");
                    return;
                }
                java.util.Set<String> vw = new java.util.HashSet<>(), dicht = new java.util.HashSet<>();
                for (ZeldaVoorwerp v : spel.voorwerpen) vw.add(v.vlag());
                for (int i = 3; i + 1 < args.length; i += 2)          // kitpvp zelda lopen <eiland> [zonder <voorwerp>] [dicht <poort>] …
                    if (args[i].equalsIgnoreCase("zonder")) vw.remove(args[i + 1].toLowerCase());
                    else if (args[i].equalsIgnoreCase("dicht")) dicht.add(args[i + 1].toLowerCase());
                List<String> niet = ZeldaLopen.onbereikbaar(e, vw, dicht);
                s.sendMessage("zelda lopen " + e.id() + (args.length > 3 ? " (" + String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) + ")" : "")
                        + ": " + (niet.isEmpty() ? "OK, alles te belopen" : niet.size() + " plekken niet te belopen"));
                for (String n : niet) s.sendMessage("  ✗ " + n);
            }
            case "hint" -> {
                if (p == null) {
                    s.sendMessage("kitpvp zelda hint <speler>");
                    return;
                }
                spel.hint(p);
                ZeldaSpel.Sessie ss = spel.sessie(p);
                s.sendMessage("zelda: hint voor " + p.getName() + (ss == null ? " (niet in Zelda)" : " · doel " + ss.hintDoel));
            }
            case "beheer" -> {                               // kitpvp zelda beheer <speler> [aan|uit|ga <plek>|plekken]
                if (p == null) {
                    s.sendMessage("kitpvp zelda beheer <speler> [aan|uit|ga <plek>|plekken] · in beheer: " + spel.beheer.aantal());
                    return;
                }
                String[] rest = args.length >= 4 ? java.util.Arrays.copyOfRange(args, 3, args.length) : new String[0];
                if (rest.length >= 1 && (rest[0].equalsIgnoreCase("aan") || rest[0].equalsIgnoreCase("uit"))) rest = new String[]{"beheer", rest[0]};
                spel.beheer.commando(p, rest.length == 0 ? new String[]{"beheer"} : rest);
                s.sendMessage("zelda: " + p.getName() + " beheer " + (spel.beheer.is(p.getUniqueId()) ? "aan" : "uit")
                        + (spel.speelt(p) ? " · " + spel.sessie(p).h.id() + " " + p.getLocation().getBlockX() + "," + p.getLocation().getBlockY() + "," + p.getLocation().getBlockZ()
                        + " · vliegen=" + p.getAllowFlight() + " onkwetsbaar=" + p.isInvulnerable() : ""));
            }
            case "kaart" -> {                                // kitpvp zelda kaart [<speler>|opnieuw [eiland]]: plaatjes, of opnieuw meten
                if (args.length >= 3 && args[2].equalsIgnoreCase("opnieuw")) {
                    ZeldaEiland e = args.length >= 4 ? spel.eiland(args[3].toLowerCase()) : null;
                    spel.landkaart.opnieuw(e);
                    s.sendMessage("zelda kaart: " + (e == null ? "alle eilanden" : e.naam()) + " wordt opnieuw gemeten");
                    return;
                }
                if (args.length >= 3 && (p == null || !spel.speelt(p))) {
                    s.sendMessage("kitpvp zelda kaart [<speler in Zelda>|opnieuw [eiland]]");
                    return;
                }
                try {
                    for (String f : spel.landkaart.plaatjes(p)) s.sendMessage("zelda kaart: " + f);
                } catch (java.io.IOException e) {
                    s.sendMessage("zelda kaart mislukt: " + e.getMessage());
                }
            }
            case "verder" -> {
                if (p == null) {
                    s.sendMessage("kitpvp zelda verder <speler>: speler niet online");
                    return;
                }
                new ZeldaTest(plugin, this, p, s).verder();
            }
            default -> {
                StringBuilder sb = new StringBuilder("zelda: spelers=" + spel.sessies.size() + " voortgang=" + spel.voortgang.aantal() + " vijanden=" + spel.vijanden());
                for (ZeldaHoofdstuk h : HOOFDSTUKKEN) {
                    sb.append(" · ").append(h.id()).append(":");
                    for (ZeldaHoofdstuk.Kamer k : h.kamers) sb.append(' ').append(k.id).append('=').append(k.fase.name().toLowerCase());
                }
                s.sendMessage(sb + " · kitpvp zelda in|reset|rust|test|verder|vaar|vlag|hint|beheer|kaart <speler> · kitpvp zelda lopen <eiland> · kitpvp zelda kaart [opnieuw]");
            }
        }
    }
}
