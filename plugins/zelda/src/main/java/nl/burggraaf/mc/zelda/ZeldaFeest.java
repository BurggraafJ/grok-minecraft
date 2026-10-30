package nl.burggraaf.mc.zelda;

import java.util.List;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

/**
 * Het feest op de open plek van het Lindewoud (het einde van Story of Zelda, eiland 5b): wie Zelda bevrijdde ({@code e5b}) en
 * naar huis voer, vindt op het oefenveld iedereen die hielp, met Zelda in het midden. Praten met haar = het einde (vlag
 * {@code zelda}): vuurwerk en «Story of Zelda · voltooid». Daarna blijft alles open: de gasten zeggen iets nieuws, Zelda vraagt
 * om haar kroontje uit Morgals schatkamer (een opdracht terug naar Nachtrots), en Wiske weet hoeveel hartstukjes er nog liggen.
 *
 * <p>Zonder blokken (dus zonder herbouw van het Lindewoud): personen die alleen te zien zijn voor wie Zelda bevrijdde, lichtjes
 * en vuurwerk (deeltjes, per speler) en het lied van thuis als feest ({@link ZeldaMuziek#FEEST}). Hoort bij
 * {@link ZeldaHoofdstuk1} (zijn personen, verhalen, doelen en opdrachten).
 */
final class ZeldaFeest {

    private ZeldaFeest() {
    }

    /** Het midden van het feest: het oefenveld (de palissade van de open plek). */
    static final int FX = 1240, FZ = -60;
    /** De opdracht na het einde: Zelda's kroontje uit Morgals schatkamer (5b), terug naar Zelda. */
    static final String SCHAT = "schatkamer", KROON = "kroon:zelda";
    /** Na het kroontje: Zelda's brieven naar Teun (Zoutkom), Ys (IJshoeve), Okker (Sintelgat) en Wolf (de kreek), terug naar Zelda. */
    static final String BRIEVEN = "brieven";
    static final String[] BRIEF_AAN = {"Teun", "Ys", "Okker", "Wolf"};
    /** Wie Zelda bevrijdde (en dus het feest ziet). */
    static final String E5B = "e5b";

    /** Op het feest: wie hielp, met een plek {dx, dz} rond Zelda. */
    private static final Object[][] GASTEN = {
            {"Hendrik", 5, 0, "armorer", Villager.Type.DESERT, false, "Bas is weer thuis. Hij smeedt al hoefijzers.", null, "Kom eens langs in Zoutkom. Het vuur brandt."},
            {"Bas", 4, 2, null, Villager.Type.PLAINS, true, "Ik ga nooit meer naar een schip kijken!", "vrij:bas", "Ik help Hendrik weer in de smidse."},
            {"Dina", 2, 4, "toolsmith", Villager.Type.SAVANNA, false, "Een warm broodje voor de held. Hier!", null, "In Sintelgat bak ik er nog meer."},
            {"Joppe", 0, 5, null, Villager.Type.SAVANNA, true, "Mama zegt: nu ben ik voorzichtig.", "vrij:joppe", "Ik blijf voortaan bij mama."},
            {"Ys", -2, 4, "fisherman", Villager.Type.SNOW, false, "Teun speelt nog steeds vals. Ik zag het.", null, "Het meer is weer glad. Kom eens glijden."},
            {"Mira", -4, 2, "cartographer", Villager.Type.PLAINS, false, "Mijn vuurtoren brandt. En jij ook, zie ik.", null, "Kijk 's avonds naar zee. Dat is mijn licht."},
            {"Teun", -5, 0, "fisherman", Villager.Type.TAIGA, false, "Zoutbaard had dit feest ook leuk gevonden.", null, "Zoutkom wacht op je. Mijn kaarten ook."},
            {"Wolf", -2, -4, "armorer", Villager.Type.TAIGA, false, "Een wachter van Zelda zonder werk. Heerlijk.", null, "Nachtrots is stil nu. Alleen de zee."},
            {"Hagen", 0, -5, "weaponsmith", Villager.Type.SNOW, false, "Een feest! Het is hier warmer dan in IJshoeve.", null, "Mijn smidse in IJshoeve staat altijd open."},
            {"Okker", 2, -4, "armorer", Villager.Type.SAVANNA, false, "Mijn esse brandt vandaag alleen voor jou.", null, "Sintelgat smeedt weer. Dankzij jou."},
            {"Ida", 4, -2, "cartographer", Villager.Type.SNOW, false, "Ik klom op Morgals toren. Wat een uitzicht!", null, "Morgen klim ik weer. Ga je mee?"}};

    /** Na het einde zeggen ze op het Lindewoud iets nieuws (één keer, vooraan): {naam, regel}. */
    private static final String[][] THUIS = {
            {"Kees", "Zelda is terug! Ik hak hout voor het feestvuur."},
            {"Hanne", "Ik brouwde iets voor het feest. Proef maar."},
            {"Fien", "Feestbrood! Pak maar, je hebt het verdiend."},
            {"Sem", "Later word ik net als jij!"},
            {"Bram", "Ik gaf je een schild. En kijk nu eens!"},
            {"Gerrit", "Zelda is thuis! Wat een dag."},
            {"Brecht", "Mijn Zilvermeeuw voer nog nooit zo hard."},
            {"Lotje", "Ik telde de meeuwen. En één prinses!"}};

    private static Location plek(ZeldaHoofdstuk1 h, double x, double z, float yaw) {
        return new Location(h.w, x + 0.5, h.G + 1, z + 0.5, yaw, 0f);
    }

    /** Waar iemand op {dx, dz} van het midden heen kijkt: naar Zelda. */
    private static float naarMidden(int dx, int dz) {
        return (float) Math.toDegrees(Math.atan2(dx, -dz));
    }

    /** Het feest erbij (personen, verhalen, het doel en de opdracht), na alle andere personen van het Lindewoud. */
    static void bouw(ZeldaHoofdstuk1 h) {
        // wie op het Lindewoud woont, zegt na het einde iets nieuws (vooraan: het is eenmalig)
        for (String[] t : THUIS) {
            ZeldaHoofdstuk.Persoon pe = null;
            for (ZeldaHoofdstuk.Persoon x : h.personen) if (pe == null && x.naam.equals(t[0])) pe = x;
            if (pe == null) continue;
            boolean genees = t[0].equals("Hanne") || t[0].equals("Fien");
            pe.gesprekken.add(0, new ZeldaHoofdstuk.Gesprek("na-" + t[0].toLowerCase(), s -> s.heeft("zelda") && !s.heeft("g:na-" + t[0].toLowerCase()),
                    List.of(ZeldaHoofdstuk.r(t[0], t[1])), genees ? (spel, p, s) -> spel.genees(p) : null));
        }
        // Tijmen is naar het feest (zijn sterrentoren is leeg voor wie Zelda bevrijdde)
        for (ZeldaHoofdstuk.Persoon x : h.personen) if (x.naam.equals("Tijmen")) x.zichtbaar = s -> !s.heeft(E5B);
        // Zelda, in het midden
        ZeldaHoofdstuk.Persoon zelda = h.persoon(new ZeldaHoofdstuk.Persoon("Zelda", plek(h, FX, FZ, 90f), null, List.of(
                ZeldaHoofdstuk.r("Zelda", "Wat een feest. Dank je wel!"))));
        zelda.soort = EntityType.MANNEQUIN;
        zelda.uiterlijk = e -> ZeldaEiland5b.zelda(e, false);
        zelda.zichtbaar = s -> s.heeft(E5B);
        zelda.beheerNaam = "feest";                                     // /zelda ga feest
        zelda.gesprek("zelda-feest", s -> s.heeft(E5B) && !s.heeft("zelda"), List.of(
                        ZeldaHoofdstuk.r("Zelda", "Iedereen is er. Voor jou!"),
                        ZeldaHoofdstuk.r("Zelda", "Jij en Wiske brachten het licht terug."),
                        ZeldaHoofdstuk.r("Wiske", "We deden het samen!"),
                        ZeldaHoofdstuk.r(null, "Story of Zelda · voltooid")), ZeldaFeest::voltooid)
                .gesprek("zelda-schat", s -> s.heeft("zelda") && s.missie(SCHAT) == 0, List.of(
                        ZeldaHoofdstuk.r("Zelda", "Nog één ding. Morgal stal mijn kroontje."),
                        ZeldaHoofdstuk.r("Zelda", "Het ligt vast in zijn schatkamer, onder de toren.")), (spel, p, s) -> spel.missie(p, SCHAT, 1))
                .gesprek(null, s -> s.missie(SCHAT) == 1, List.of(ZeldaHoofdstuk.r("Zelda", "Morgals schatkamer is onder in zijn toren.")), null)
                .gesprek("zelda-schat-2", s -> s.missie(SCHAT) == 2, List.of(
                        ZeldaHoofdstuk.r("Zelda", "Mijn kroontje! Dank je wel."),
                        ZeldaHoofdstuk.r("Zelda", "Nu is het echt feest.")), (spel, p, s) -> {
                    spel.missie(p, SCHAT, ZeldaVoortgang.KLAAR);
                    if (s.zet(KROON)) spel.voortgang.vuil();
                    spel.genees(p);
                    vuurwerk(spel, h, 2);
                })
                .gesprek("zelda-brieven", s -> s.missie(SCHAT) == ZeldaVoortgang.KLAAR && s.missie(BRIEVEN) == 0, List.of(
                        ZeldaHoofdstuk.r("Zelda", "Wil je mijn dank rondbrengen? Vier brieven."),
                        ZeldaHoofdstuk.r("Zelda", "Voor Teun, Ys, Okker en Wolf.")), (spel, p, s) -> spel.missie(p, BRIEVEN, 1))
                .gesprek(null, s -> s.missie(BRIEVEN) == 1, List.of(ZeldaHoofdstuk.r("Zelda", "Teun, Ys, Okker en Wolf. Brecht vaart je.")), null)
                .gesprek("zelda-brieven-2", s -> s.missie(BRIEVEN) == 2, List.of(
                        ZeldaHoofdstuk.r("Zelda", "Iedereen weet het nu. Dank je wel!"),
                        ZeldaHoofdstuk.r("Wiske", "Kom je nog eens langs? Ik blijf hier.")), (spel, p, s) -> {
                    spel.missie(p, BRIEVEN, ZeldaVoortgang.KLAAR);
                    spel.genees(p);
                    vuurwerk(spel, h, 4);
                })
                .gesprek(null, s -> s.missie(BRIEVEN) == ZeldaVoortgang.KLAAR, List.of(ZeldaHoofdstuk.r("Zelda", "Kom je nog eens? Het feest gaat door.")), null);
        h.missie(SCHAT, "De schat van Morgal", "Zoek Morgals schatkamer, onder in zijn toren.", "Breng Zelda haar kroontje, op het Lindewoud.");
        h.missie(BRIEVEN, "Zelda's brieven", "Breng de brieven naar Teun, Ys, Okker en Wolf.", "Terug naar Zelda, op het Lindewoud.");
        // Tijmen: de wegwijzer op het feest
        ZeldaHoofdstuk.Persoon tijmen = h.persoon(new ZeldaHoofdstuk.Persoon("Tijmen", plek(h, FX - 4, FZ - 2, naarMidden(-4, -2)), null, List.of(
                ZeldaHoofdstuk.r("Tijmen", "Wat een feest. Zelfs de sterren dansen."))));
        tijmen.soort = EntityType.WANDERING_TRADER;
        tijmen.zichtbaar = s -> s.heeft(E5B);
        tijmen.beheerNaam = "tijmen-feest";
        tijmen.eenmalig("feest-tijmen", s -> s.heeft("zelda"), List.of(
                        ZeldaHoofdstuk.r("Tijmen", "Vroeger leerde ik Zelda de sterren lezen."),
                        ZeldaHoofdstuk.r("Tijmen", "Nu lees jij ze beter dan ik.")), null)
                .gesprek(null, s -> true, List.of(), (spel, p, s) -> spel.wegwijzer(p, "Tijmen"));
        // wie hielp, rond Zelda
        for (Object[] g : GASTEN) {
            String naam = (String) g[0];
            int dx = (int) g[1], dz = (int) g[2];
            ZeldaHoofdstuk.Persoon pe = h.persoon(new ZeldaHoofdstuk.Persoon(naam, plek(h, FX + dx, FZ + dz, naarMidden(dx, dz)),
                    g[3] == null ? null : Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft((String) g[3])),
                    List.of(ZeldaHoofdstuk.r(naam, "Daar is ze! Praat met Zelda."))));
            pe.streek = (Villager.Type) g[4];
            pe.kind = (boolean) g[5];
            String nodig = (String) g[7];                                 // een gevangene staat er alleen als je hem bevrijdde
            pe.zichtbaar = s -> s.heeft(E5B) && (nodig == null || s.heeft(nodig));
            pe.beheerNaam = naam.toLowerCase() + "-feest";
            ZeldaHoofdstuk.Actie actie = naam.equals("Dina") ? (spel, p, s) -> spel.genees(p) : null;
            String eerst = naam.equals("Hendrik") ? null : (String) g[6];
            if (naam.equals("Hendrik"))                                         // Bas is alleen thuis als je hem bevrijdde
                pe.eenmalig("feest-hendrik", s -> s.heeft("zelda"), List.of(), (spel, p, s) ->
                        spel.vertel(p, ZeldaHoofdstuk.r("Hendrik", s.heeft("vrij:bas") ? (String) g[6] : "Wat een feest! Zoutkom is trots op je.")));
            else pe.eenmalig("feest-" + naam.toLowerCase(), s -> s.heeft("zelda"), List.of(ZeldaHoofdstuk.r(naam, eerst)), actie);
            pe.gesprek(null, s -> s.heeft("zelda"), List.of(ZeldaHoofdstuk.r(naam, (String) g[8])), null);   // daarna: kom eens langs
        }
        // aankomst in de haven, en op het feest
        ZeldaHoofdstuk.Rust haven = h.rust("haven");
        if (haven != null) {
            Location l = haven.plek;
            ZeldaHoofdstuk.Verhaal thuis = h.verhaal(new ZeldaHoofdstuk.Verhaal("thuiskomst", ZeldaHoofdstuk.Box.van(l.getBlockX() - 6, l.getBlockY() - 1, l.getBlockZ() - 6,
                    l.getBlockX() + 6, l.getBlockY() + 4, l.getBlockZ() + 6), List.of(
                    ZeldaHoofdstuk.r("Wiske", "Thuis! Hoor je dat? Muziek."),
                    ZeldaHoofdstuk.r("Wiske", "Het komt van het oefenveld, in het woud."))));
            thuis.vereist = E5B;
            thuis.als = s -> !s.heeft("zelda");
        }
        ZeldaHoofdstuk.Verhaal feest = h.verhaal(new ZeldaHoofdstuk.Verhaal("feest", ZeldaHoofdstuk.Box.van(FX - 10, h.G, FZ - 10, FX + 10, h.G + 6, FZ + 10), List.of(
                ZeldaHoofdstuk.r("Wiske", "Kijk! Iedereen is er!"),
                ZeldaHoofdstuk.r("Wiske", "Daar, in het midden: Zelda."))));
        feest.vereist = E5B;
        feest.als = s -> !s.heeft("zelda");
        h.doel("feest", "Muziek, bij het oefenveld!", "Op de open plek in het woud. Praat met Zelda.", zelda.plek,
                s -> s.heeft("zelda") || !s.heeft(E5B));
    }

    /**
     * Brecht op elke steiger: nieuws na een grote stap (één keer, waar je hem het eerst ziet), vóór zijn eigen gesprekken.
     * Zo zegt hij na elke stap iets nieuws en stuurt hij je verder (het kroontje, de brieven).
     */
    static void brechtNieuws(ZeldaHoofdstuk.Persoon brecht) {
        List<ZeldaHoofdstuk.Gesprek> nieuws = List.of(
                nieuws("brecht-binnen", s -> s.heeft("e5a") && !s.heeft(ZeldaEiland5b.VRIJ_ZELDA), "Je was binnen in Morgals burcht! Ik hield de wacht."),
                nieuws("brecht-schat", s -> s.heeft("zelda") && s.missie(SCHAT) == 1, "Zelda's kroontje? Onder in Morgals toren, zei ze."),
                nieuws("brecht-kroon", s -> s.heeft(KROON) && s.missie(BRIEVEN) == 0, "Zelda met haar kroontje. Wat een plaatje!"),
                nieuws("brecht-brieven", s -> s.missie(BRIEVEN) == 1, "Brieven van de prinses? Stap maar in, ik vaar."));
        brecht.gesprekken.addAll(0, nieuws);
    }

    private static ZeldaHoofdstuk.Gesprek nieuws(String id, java.util.function.Predicate<ZeldaVoortgang.Stand> als, String regel) {
        return new ZeldaHoofdstuk.Gesprek(id, s -> !s.heeft("g:" + id) && als.test(s), List.of(ZeldaHoofdstuk.r("Brecht", regel)), null);
    }

    /** Na het bouwen van een deel: wie een brief van Zelda krijgt (de laatste met die naam: in de kreek de vrije Wolf, niet die in zijn cel). */
    static void naBouw(ZeldaHoofdstuk h) {
        String[][] wie = {{"e2a", "Teun", "Een brief van Zelda? Voor mij? Nou nou."}, {"e3a", "Ys", "Van de prinses zelf. Die hang ik bij mijn wak."},
                {"e4a", "Okker", "Een brief van Zelda! Die komt boven de esse."}, {"e5a", "Wolf", "Van Zelda. Nu is mijn wacht echt voorbij."}};
        for (String[] w : wie) {
            if (!w[0].equals(h.id())) continue;
            ZeldaHoofdstuk.Persoon pe = null;
            for (ZeldaHoofdstuk.Persoon x : h.personen) if (x.naam.equals(w[1])) pe = x;
            if (pe != null) brief(pe, w[2]);
        }
    }

    /** Een brief van Zelda afgeven (vooraan: hij gaat voor); alle vier = terug naar Zelda. */
    static void brief(ZeldaHoofdstuk.Persoon pe, String regel) {
        String id = "brief-" + pe.naam.toLowerCase();
        pe.gesprekken.add(0, new ZeldaHoofdstuk.Gesprek(id, s -> s.missie(BRIEVEN) == 1 && !s.heeft("g:" + id), List.of(
                ZeldaHoofdstuk.r(pe.naam, regel)), (spel, p, s) -> {
            int n = 0;
            for (String aan : BRIEF_AAN) if (s.heeft("g:brief-" + aan.toLowerCase())) n++;
            if (n >= BRIEF_AAN.length) spel.missie(p, BRIEVEN, 2);
            else spel.zeg(p, net.kyori.adventure.text.Component.text("Brieven · " + n + " van " + BRIEF_AAN.length, net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }));
    }

    /** Het einde: de vlag {@code zelda}, het slotdeuntje en vuurwerk boven de open plek. */
    private static void voltooid(ZeldaSpel spel, Player p, ZeldaVoortgang.Stand s) {
        if (!s.zet("zelda")) return;
        spel.voortgang.vuil();
        spel.bewaar();
        spel.muziek.speel(p, ZeldaMuziek.KLAAR);
        spel.genees(p);
        ZeldaHoofdstuk h = spel.deel("h1");
        if (h instanceof ZeldaHoofdstuk1 h1) vuurwerk(spel, h1, 5);
    }

    /** Vuurwerk boven de palissade (raakt niemand: zie {@code ZeldaSpel.onVuurwerk}). */
    static void vuurwerk(ZeldaSpel spel, ZeldaHoofdstuk1 h, int n) {
        Color[] kleuren = {Color.fromRGB(0xfff2a0), Color.fromRGB(0x8fcf6a), Color.fromRGB(0xf7a8d8), Color.fromRGB(0x7fd6ff), Color.fromRGB(0xffa040)};
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            Location l = new Location(h.w, FX + 0.5 + Math.cos(a) * 9, h.G + 16 + (i % 2) * 3, FZ + 0.5 + Math.sin(a) * 9);
            int k = i;
            Firework f = h.w.spawn(l, Firework.class, fw -> {
                fw.setPersistent(false);
                fw.addScoreboardTag(ZeldaSpel.TAG);
                FireworkMeta m = fw.getFireworkMeta();
                m.addEffect(FireworkEffect.builder().with(k % 2 == 0 ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                        .withColor(kleuren[k % kleuren.length], kleuren[(k + 2) % kleuren.length]).withFade(Color.WHITE).trail(true).flicker(k % 3 == 0).build());
                m.setPower(0);
                fw.setFireworkMeta(m);
            });
            org.bukkit.Bukkit.getScheduler().runTaskLater(spel.plugin, () -> {
                if (f.isValid()) f.detonate();
            }, 2L + i * 6L);
        }
    }

    /**
     * Elke seconde, voor wie op het Lindewoud is en Zelda bevrijdde: lichtjes langs de palissade (ze wisselen van kleur), af en
     * toe een vonk in de lucht, en Zelda's kroontje (alleen voor wie het terugbracht).
     */
    static void tik(ZeldaHoofdstuk1 h, ZeldaSpel spel, List<Player> wie) {
        Location midden = new Location(h.w, FX + 0.5, h.G + 1, FZ + 0.5);
        Color[] kleuren = {Color.fromRGB(0xfff2a0), Color.fromRGB(0xf7a8d8), Color.fromRGB(0x8fcf6a), Color.fromRGB(0x7fd6ff)};
        long t = System.currentTimeMillis() / 1000;
        ZeldaHoofdstuk.Persoon zelda = null;
        for (ZeldaHoofdstuk.Persoon x : h.personen) if (x.naam.equals("Zelda")) zelda = x;
        for (Player p : wie) {
            if (!p.getWorld().equals(h.w) || p.getLocation().distanceSquared(midden) > 48 * 48) continue;
            ZeldaVoortgang.Stand s = spel.voortgang.van(p.getUniqueId());
            if (!s.heeft(E5B)) continue;
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI / 10;
                Particle.DustOptions licht = new Particle.DustOptions(kleuren[(int) ((i + t) % kleuren.length)], 1.3f);
                p.spawnParticle(Particle.DUST, FX + 0.5 + Math.cos(a) * 12.6, h.G + 7.3, FZ + 0.5 + Math.sin(a) * 12.6, 1, 0, 0, 0, 0, licht);
            }
            if ((t + p.getEntityId()) % 4 == 0) p.spawnParticle(Particle.FIREWORK, FX + 0.5, h.G + 18, FZ + 0.5, 30, 3, 1.5, 3, 0.08);
            if (t % 3 == 0) p.spawnParticle(Particle.NOTE, FX + 0.5 + Math.cos(t) * 4, h.G + 3, FZ + 0.5 + Math.sin(t) * 4, 1, 0, 0, 0, Math.abs(t % 24) / 24.0);
            if (zelda != null && zelda.ent != null && zelda.ent.isValid())
                p.sendEquipmentChange(zelda.ent, EquipmentSlot.HEAD, s.heeft(KROON) ? new ItemStack(Material.GOLDEN_HELMET) : new ItemStack(Material.AIR));
        }
    }

    /** Op het feest klinkt het lied van thuis als feest (voor wie Zelda bevrijdde). */
    static ZeldaMuziek.Lied muziek(Location l, ZeldaVoortgang.Stand s) {
        if (!s.heeft(E5B) || Math.hypot(l.getX() - FX - 0.5, l.getZ() - FZ - 0.5) > 24) return null;
        return ZeldaMuziek.FEEST;
    }
}
