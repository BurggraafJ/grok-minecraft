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
    /** Alle delen van alle eilanden. */
    final List<ZeldaHoofdstuk> HOOFDSTUKKEN;
    final ZeldaZee zee;
    final ZeldaSpel spel;

    Zelda(KitPvP plugin) {
        this.plugin = plugin;
        EILANDEN = List.of(
                new ZeldaEiland("lindewoud", "Het Lindewoud", Material.OAK_SAPLING, List.of(new ZeldaHoofdstuk1(plugin)), "haven", null, "h1", List.of()),
                new ZeldaEiland("zilverstrand", "Het Zilverstrand", Material.SAND, List.of(new ZeldaEiland2a(plugin)), "steiger", "h1", "baken2", List.of(
                        ZeldaHoofdstuk.r("Brecht", "Op het Zilverstrand woont mijn zus Mira."),
                        ZeldaHoofdstuk.r("Brecht", "Zij past op de vuurtoren. Al twintig jaar."),
                        ZeldaHoofdstuk.r("Wiske", "Een vuurtoren wijst de weg. Precies wat we zoeken!"))));
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
        for (ZeldaHoofdstuk h : HOOFDSTUKKEN) d.add(new Deel(h.deelId(), h.versie(), h::bouw));
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
        int kamers = 0, uitd = 0, puz = 0, vond = 0, barsten = 0, haken = 0, spieg = 0;
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
                    if (!deurKlopt(h, k.uit, false)) fouten.add(hid + ": kamer " + k.id + ": uitgang is open");
                }
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
            // bouwregel vanaf eiland 2: per half eiland hoogstens één verplichte kamer (het Lindewoud is ouder)
            if (h.eiland != EILANDEN.get(0)) {
                long moet = h.kamers.stream().filter(k -> !k.vrijwillig && k.baas == null).count();
                if (moet > 1) fouten.add(hid + ": " + moet + " verplichte kamers (hoogstens 1 per half eiland)");
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
            v.bewaar();
            ZeldaVoortgang terug = new ZeldaVoortgang(proef);
            ZeldaVoortgang.Stand t = terug.van(id);
            if (t.rust("h1") != 3 || !t.heeft("zwaard") || t.beste("kloof") != 12345L || !"h1:2".equals(t.laatste) || t.missie("proef") != 2
                    || t.teller("fles-vol") != 1 || !"Pluis".equals(t.dier) || !t.bezocht("h1", 3))
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
        for (var e : spel.sessies.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && !p.isDead() && spel.metgezel.van(p) == null)
                info.add("(wiske van " + p.getName() + " nog niet in de wereld)");
        }
        info.add("zelda=" + status().name().toLowerCase() + " eilanden=" + EILANDEN.size() + " delen=" + HOOFDSTUKKEN.size() + " rust=" + HOOFDSTUKKEN.get(0).rust.size()
                + " kamers=" + kamers + " uitdagingen=" + uitd + " puzzels=" + puz + " vondsten=" + vond + " bronnen=" + bronnen + " doelen=" + doelen
                + " personen=" + personen + " missies=" + missies + " hartstukjes=" + stukjes + " barsten=" + barsten + " haakpunten=" + haken
                + " spiegels=" + spieg + " voorwerpen=" + spel.voorwerpen.size()
                + " liedjes=" + ZeldaMuziek.alle().size()
                + " spelers=" + spel.sessies.size() + " voortgang=" + spel.voortgang.aantal() + " entiteiten=" + ents + " feeen=" + feeen + " dieren=" + spel.dier.aantal());
    }

    private static boolean deurKlopt(ZeldaHoofdstuk h, ZeldaHoofdstuk.Deur d, boolean open) {
        int[] c = d.blokken.get(0);
        return h.w.getBlockAt(c[0], c[1], c[2]).getType() == (open ? d.open : d.dicht).getMaterial();
    }

    // ── beheer: kitpvp zelda … ──────────────────────────────────────────────────────────────

    void beheer(CommandSender s, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase() : "status";
        Player p = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
        switch (sub) {
            case "reset" -> {
                if (p == null) {
                    s.sendMessage("kitpvp zelda reset <speler>: speler niet online");
                    return;
                }
                boolean in = spel.speelt(p);
                if (in) plugin.naarHub(p);
                spel.voortgang.wis(p.getUniqueId());
                spel.bewaar();
                s.sendMessage("zelda: voortgang van " + p.getName() + " gewist" + (in ? " (naar de hub)" : ""));
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
                s.sendMessage("zelda: " + p.getName() + " staat nu op rustplaats " + n + " (" + h.rust.get(n).naam + ")");
            }
            case "test" -> {
                if (p == null) {
                    s.sendMessage("kitpvp zelda test <speler> [alles|dood]: speler niet online");
                    return;
                }
                ZeldaTest t = new ZeldaTest(plugin, this, p, s);
                if (args.length >= 4 && args[3].equalsIgnoreCase("dood")) t.dood();
                else if (args.length >= 4 && args[3].equalsIgnoreCase("motor")) t.motor();
                else t.route(args.length >= 4 && args[3].equalsIgnoreCase("alles"));
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
                s.sendMessage(sb + " · kitpvp zelda in|reset|rust|test|verder|vaar|vlag|hint|beheer <speler>");
            }
        }
    }
}
