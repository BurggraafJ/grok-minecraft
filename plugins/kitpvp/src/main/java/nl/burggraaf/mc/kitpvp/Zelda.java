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
 * Story of Zelda: een avontuur in hoofdstukken, alleen of samen. Door de grote poort in de hub (west) kom je bij je
 * verste rustplaats. Een hoofdstuk ({@link ZeldaHoofdstuk}) is een eigen stuk wereld (een {@link Deel}) met
 * rustplaatsen, kamers die opengaan als je de vijanden verslaat, uitdagingen op tijd, puzzels, vondsten en verhaal;
 * {@link ZeldaSpel} laat het werken, {@link ZeldaVoortgang} bewaart per speler hoe ver je bent (zelda.yml).
 *
 * <p>Een hoofdstuk erbij: een subklasse van {@link ZeldaHoofdstuk} met een eigen deel-id, in {@link #HOOFDSTUKKEN}
 * (zie MODI.md). De wereld ligt in {@link #GEBIED}.
 */
final class Zelda implements Modus {

    /** Gereserveerd voor de Zelda-map: x 1024..2047, z -512..511 (vier regiobestanden, ver van de rest). */
    static final int[] GEBIED = {1024, -512, 2047, 511};

    private final KitPvP plugin;
    final List<ZeldaHoofdstuk> HOOFDSTUKKEN;
    final ZeldaSpel spel;

    Zelda(KitPvP plugin) {
        this.plugin = plugin;
        HOOFDSTUKKEN = List.of(new ZeldaHoofdstuk1(plugin));
        spel = new ZeldaSpel(plugin, this, HOOFDSTUKKEN);
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

    /** De wolf vecht mee tegen monsters, nooit tegen spelers (zoals in de oefenarena). */
    @Override
    public String stand(Player p, Toestand t) {
        return "bots";
    }

    @Override
    public Location respawn(Player p, Toestand t) {
        return spel.respawn(p);
    }

    @Override
    public String plek(Player p, Toestand t) {
        ZeldaSpel.Sessie ss = spel.sessie(p);
        return ss == null ? naam() : ss.h.plekNaam(p.getLocation());
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
        int kamers = 0, uitd = 0, puz = 0, vond = 0;
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
        }
        for (ZeldaMuziek.Lied l : ZeldaMuziek.alle()) if (l.lengte() == 0) fouten.add("zelda: lied " + l.id() + " is leeg");
        // opslag: schrijven en teruglezen in een proefbestand
        File proef = new File(plugin.getDataFolder(), "zelda-zelftest.yml");
        try {
            ZeldaVoortgang v = new ZeldaVoortgang(proef);
            UUID id = UUID.randomUUID();
            v.van(id).bereik("h1", 3);
            v.van(id).zet("zwaard");
            v.van(id).tijd("kloof", 12345);
            v.bewaar();
            ZeldaVoortgang terug = new ZeldaVoortgang(proef);
            if (terug.van(id).rust("h1") != 3 || !terug.van(id).heeft("zwaard") || terug.van(id).beste("kloof") != 12345L)
                fouten.add("zelda: voortgang komt niet goed terug uit het bestand");
        } catch (Exception e) {
            fouten.add("zelda: voortgang bewaren mislukt: " + e);
        } finally {
            if (proef.exists() && !proef.delete()) fouten.add("zelda: proefbestand bleef staan");
        }
        int ents = 0;
        if (plugin.w != null) for (Entity e : plugin.w.getEntities()) if (e.getScoreboardTags().contains(ZeldaSpel.TAG)) ents++;
        if (ents > 60) fouten.add("zelda: " + ents + " entiteiten (te veel)");
        info.add("zelda=" + status().name().toLowerCase() + " hoofdstukken=" + HOOFDSTUKKEN.size() + " rust=" + HOOFDSTUKKEN.get(0).rust.size()
                + " kamers=" + kamers + " uitdagingen=" + uitd + " puzzels=" + puz + " vondsten=" + vond
                + " spelers=" + spel.sessies.size() + " voortgang=" + spel.voortgang.aantal() + " entiteiten=" + ents);
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
                st.rust.put(h.id(), n);
                st.hoofdstuk = h.id();
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
                else t.route(args.length >= 4 && args[3].equalsIgnoreCase("alles"));
            }
            case "in" -> {
                if (p == null) {
                    s.sendMessage("kitpvp zelda in <speler>: speler niet online");
                    return;
                }
                plugin.kiesModus(p, this);
                s.sendMessage("zelda: " + p.getName() + " " + (spel.speelt(p) ? "is in Zelda (" + spel.sessie(p).h.rust.get(spel.sessie(p).rust).naam + ")" : "kon niet naar binnen"));
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
                s.sendMessage(sb + " · kitpvp zelda in|reset|rust|test|verder <speler>");
            }
        }
    }
}
