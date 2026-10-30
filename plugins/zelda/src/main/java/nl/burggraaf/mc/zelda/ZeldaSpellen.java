package nl.burggraaf.mc.zelda;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * De menu's op de kaart voor niveau en spellen (Jelle 28-10): het <b>niveau</b> kiezen (vier, {@link ZeldaRegels.Niveau};
 * wisselen kan altijd, ook in een gevecht), je <b>spellen</b> (hoogstens {@link ZeldaVoortgang#MAX_SPELLEN}: verder spelen,
 * een nieuw spel, een spel wissen) en <b>samen spelen</b> (iemand uitnodigen; wie aanneemt krijgt een samen-spel dat bij jouw
 * voortgang begint, {@link ZeldaVoortgang#deel}). Klein en duidelijk: hoogstens één rij, korte grijze regels.
 */
final class ZeldaSpellen {

    /** Een uitnodiging om samen te spelen: van wie, voor welk spel van hem, tot wanneer. */
    record Uitnodiging(UUID van, int spel, long tot) {}

    /** Zo lang geldt een uitnodiging. */
    static final long GELDIG = 5 * 60_000L;

    private final ZeldaSpel spel;
    /** Wie uitgenodigd is (de gast) → de uitnodiging. */
    final Map<UUID, Uitnodiging> uitnodigingen = new HashMap<>();
    private final java.util.Random rnd = new java.util.Random();

    ZeldaSpellen(ZeldaSpel spel) {
        this.spel = spel;
    }

    static Material icoon(ZeldaRegels.Niveau n) {
        return switch (n) {
            case VERHAAL -> Material.FEATHER;
            case AVONTUUR -> Material.IRON_SWORD;
            case HELD -> Material.GOLDEN_SWORD;
            case LEGENDE -> Material.NETHERITE_SWORD;
        };
    }

    private static void glans(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
    }

    // ── knoppen op de kaart ─────────────────────────────────────────────────────────────────

    /** «Niveau: Avontuur» op de kaart: klik = kiezen (tenzij beheer het vastzette). */
    ItemStack niveauKnop(ZeldaVoortgang.Stand s) {
        ZeldaRegels.Niveau n = ZeldaRegels.niveau(s);
        ItemStack it = spel.ding(icoon(n), "Niveau: " + n.naam, "menu:niveau");
        List<String> l = new ArrayList<>();
        l.add(n.uitleg[0]);
        int held = ZeldaRegels.sterren(s, spel.gevechten(), ZeldaRegels.Niveau.HELD);
        if (held > 0) l.add("gevechten op Held of hoger: " + held + " van " + spel.gevechten().size());
        l.add(s.vast ? "staat vast" : "klik: kiezen");
        ZeldaSpel.lore(it, l.toArray(new String[0]));
        return it;
    }

    /** «Spellen» op de kaart: je spellen en samen spelen; glanst als er een uitnodiging is. */
    ItemStack spellenKnop(Player p) {
        UUID id = p.getUniqueId();
        ItemStack it = spel.ding(Material.BOOKSHELF, "Spellen", "menu:spellen");
        String met = samenMet(id, spel.voortgang.van(id));
        Uitnodiging u = uitnodiging(id);
        List<String> l = new ArrayList<>();
        l.add("Spel " + spel.voortgang.actief(id) + (met == null ? "" : " · samen met " + met));
        if (u != null) l.add("uitnodiging van " + naam(u.van()));
        l.add("klik: je spellen, samen spelen");
        ZeldaSpel.lore(it, l.toArray(new String[0]));
        if (u != null) glans(it);
        return it;
    }

    // ── menu's ──────────────────────────────────────────────────────────────────────────────

    private ZeldaSpel.Menu menu(int grootte, String titel) {
        ZeldaSpel.Menu m = new ZeldaSpel.Menu(false);
        m.inv = Bukkit.createInventory(m, grootte, Component.text(titel, NamedTextColor.DARK_GRAY));
        return m;
    }

    /** De vier niveaus. {@code nieuw} > 0: voor een nieuw spel op die plek (dan begint het spel na de keuze). */
    void niveauMenu(Player p, int nieuw) {
        ZeldaVoortgang.Stand s = spel.voortgang.van(p.getUniqueId());
        ZeldaSpel.Menu m = menu(9, nieuw > 0 ? "Nieuw spel · kies een niveau" : "Niveau");
        ZeldaRegels.Niveau nu = ZeldaRegels.niveau(s);
        for (ZeldaRegels.Niveau n : ZeldaRegels.Niveau.values()) {
            ItemStack it = spel.ding(icoon(n), n.naam, nieuw > 0 ? "nieuwniveau:" + nieuw + ":" + n.ordinal() : "niveau:" + n.ordinal());
            ZeldaSpel.lore(it, n.uitleg[0], n.uitleg[1], nieuw == 0 && n == nu ? "zo speel je nu" : "klik: kiezen");
            if (nieuw == 0 && n == nu) glans(it);
            m.inv.setItem(1 + 2 * n.ordinal(), it);
        }
        p.openInventory(m.inv);
    }

    /** Je spellen (plekken 1 … 3), samen spelen, en een uitnodiging als die er is. */
    void spellenMenu(Player p) {
        UUID id = p.getUniqueId();
        ZeldaSpel.Menu m = menu(9, "Spellen");
        var al = spel.voortgang.spellen(id);
        int actief = spel.voortgang.actief(id);
        for (int n = 1; n <= ZeldaVoortgang.MAX_SPELLEN; n++) {
            int vak = 2 * n - 1;
            ZeldaVoortgang.Stand s = al.get(n);
            if (s == null) {
                ItemStack it = spel.ding(Material.GRAY_STAINED_GLASS_PANE, "Spel " + n, "nieuw:" + n);
                ZeldaSpel.lore(it, "leeg", "klik: nieuw spel");
                m.inv.setItem(vak, it);
                continue;
            }
            ZeldaHoofdstuk h = waar(s);
            ItemStack it = spel.ding(h.eiland == null ? Material.OAK_SAPLING : h.eiland.icoon(), "Spel " + n, "spel:" + n);
            List<String> l = new ArrayList<>();
            l.add((h.eiland == null ? h.naam() : h.eiland.naam()) + (spel.voltooid(s) ? " · voltooid" : ""));
            l.add(ZeldaRegels.harten(s) + " harten · " + ZeldaRegels.niveau(s).naam);
            String met = samenMet(id, s);
            if (met != null) l.add("samen met " + met);
            if (n == actief) l.add("hier speel je nu");
            else {
                l.add("klik: verder spelen");
                l.add("rechtsklik: wissen");
            }
            ZeldaSpel.lore(it, l.toArray(new String[0]));
            if (n == actief) glans(it);
            m.inv.setItem(vak, it);
        }
        ItemStack samen = spel.ding(Material.PLAYER_HEAD, "Samen spelen", "samen");
        ZeldaSpel.lore(samen, "nodig iemand uit voor dit spel");
        m.inv.setItem(7, samen);
        Uitnodiging u = uitnodiging(id);
        if (u != null) {
            ItemStack it = spel.ding(Material.WRITABLE_BOOK, "Uitnodiging van " + naam(u.van()), "aannemen");
            ZeldaSpel.lore(it, "klik: samen spelen");
            glans(it);
            m.inv.setItem(8, it);
        }
        p.openInventory(m.inv);
    }

    /** Wie er nu in Zelda is (behalve jij): klik = uitnodigen. */
    void samenMenu(Player p) {
        ZeldaSpel.Menu m = menu(18, "Samen spelen");
        String groep = spel.voortgang.van(p.getUniqueId()).groep;
        int vak = 0;
        for (UUID id : new ArrayList<>(spel.sessies.keySet())) {
            Player q = Bukkit.getPlayer(id);
            if (q == null || q.equals(p) || spel.beheer.is(id) || vak >= 18) continue;
            ItemStack it = spel.ding(Material.PLAYER_HEAD, q.getName(), "nodig:" + id);
            if (it.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(q);
                it.setItemMeta(sm);
            }
            boolean al = groep != null && groep.equals(spel.voortgang.van(id).groep);
            ZeldaSpel.lore(it, al ? "speelt al met je samen" : "klik: uitnodigen");
            m.inv.setItem(vak++, it);
        }
        if (vak == 0) {
            ItemStack it = spel.ding(Material.GRAY_STAINED_GLASS_PANE, "Er is nu niemand", "niets");
            ZeldaSpel.lore(it, "vrienden moeten in Zelda zijn");
            m.inv.setItem(4, it);
        }
        p.openInventory(m.inv);
    }

    /** Spel {@code n} wissen? Ja of nee. */
    void wisMenu(Player p, int n) {
        ZeldaSpel.Menu m = menu(9, "Spel " + n + " wissen?");
        ItemStack ja = spel.ding(Material.RED_WOOL, "Ja, wissen", "wisja:" + n);
        ZeldaSpel.lore(ja, "alles van spel " + n + " is dan weg");
        m.inv.setItem(2, ja);
        m.inv.setItem(6, spel.ding(Material.LIME_WOOL, "Nee", "menu:spellen"));
        p.openInventory(m.inv);
    }

    /** Een klik in een van deze menu's (of op de knoppen op de kaart); true als hij van ons was. */
    boolean klik(Player p, String soort, boolean rechts) {
        UUID id = p.getUniqueId();
        ZeldaVoortgang.Stand s = spel.voortgang.van(id);
        Runnable doe;
        if (soort.equals("menu:niveau")) doe = () -> {
            if (s.vast) {
                p.closeInventory();
                spel.zeg(p, Component.text("Je niveau staat vast", NamedTextColor.GRAY));
            } else niveauMenu(p, 0);
        };
        else if (soort.equals("menu:spellen")) doe = () -> spellenMenu(p);
        else if (soort.equals("samen")) doe = () -> samenMenu(p);
        else if (soort.startsWith("niveau:")) doe = () -> {
            p.closeInventory();
            if (s.vast) return;
            ZeldaRegels.Niveau n = ZeldaRegels.Niveau.values()[Integer.parseInt(soort.substring(7))];
            spel.zetNiveau(id, n);
            p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.2f);
            spel.zeg(p, Component.text("Niveau · " + n.naam, NamedTextColor.GRAY));
        };
        else if (soort.startsWith("nieuwniveau:")) doe = () -> {
            String[] d = soort.split(":");
            p.closeInventory();
            nieuwSpel(p, Integer.parseInt(d[1]), ZeldaRegels.Niveau.values()[Integer.parseInt(d[2])]);
        };
        else if (soort.startsWith("nieuw:")) doe = () -> {
            int n = Integer.parseInt(soort.substring(6));
            if (s.vast) {                                    // het niveau staat vast (beheer): het nieuwe spel ook
                p.closeInventory();
                nieuwSpel(p, n, ZeldaRegels.niveau(s));
            } else niveauMenu(p, n);
        };
        else if (soort.startsWith("spel:")) doe = () -> {
            int n = Integer.parseInt(soort.substring(5));
            if (n == spel.voortgang.actief(id)) return;
            if (rechts) wisMenu(p, n);
            else {
                p.closeInventory();
                spel.wissel(p, n);
                p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
            }
        };
        else if (soort.startsWith("wisja:")) doe = () -> {
            int n = Integer.parseInt(soort.substring(6));
            if (n != spel.voortgang.actief(id) && spel.voortgang.wis(id, n)) {
                spel.bewaar();
                p.playSound(p, Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.2f);
            }
            spellenMenu(p);
        };
        else if (soort.startsWith("nodig:")) doe = () -> {
            p.closeInventory();
            Player q = Bukkit.getPlayer(UUID.fromString(soort.substring(6)));
            if (q != null) nodigUit(p, q);
        };
        else if (soort.equals("aannemen")) doe = () -> {
            p.closeInventory();
            neemAan(p);
        };
        else return false;
        Bukkit.getScheduler().runTask(spel.plugin, doe);
        return true;
    }

    // ── nieuw spel en samen spelen ──────────────────────────────────────────────────────────

    /** Een nieuw spel op plek {@code n} met dit niveau, en meteen daarin verder (bij het begin). */
    void nieuwSpel(Player p, int n, ZeldaRegels.Niveau niveau) {
        UUID id = p.getUniqueId();
        ZeldaVoortgang.Stand nu = spel.voortgang.van(id);
        ZeldaVoortgang.Stand s = spel.voortgang.nieuw(id, n, null);
        if (s == null) return;
        s.niveau = niveau.ordinal();
        for (String v : nu.vlaggen) if (!ZeldaVoortgang.deelbaar(v)) s.zet(v);   // je opties gaan mee
        s.steun = nu.steun;                                  // en wat beheer voor je bijstuurde
        s.vast = nu.vast;
        spel.wissel(p, n);
    }

    /** Nodig {@code wie} uit voor het spel van {@code p} (één grijze regel bij hem; aannemen in zijn «Spellen»). */
    void nodigUit(Player p, Player wie) {
        if (!spel.speelt(wie)) return;
        String g = spel.voortgang.van(p.getUniqueId()).groep;
        if (g != null && g.equals(spel.voortgang.van(wie.getUniqueId()).groep)) {
            spel.zeg(p, Component.text(wie.getName() + " speelt al met je samen", NamedTextColor.GRAY));
            return;
        }
        uitnodigingen.put(wie.getUniqueId(), new Uitnodiging(p.getUniqueId(), spel.voortgang.actief(p.getUniqueId()), System.currentTimeMillis() + GELDIG));
        spel.zeg(p, Component.text("Uitgenodigd: " + wie.getName(), NamedTextColor.GRAY));
        spel.zeg(wie, Component.text(p.getName(), ZeldaSpel.GROEN).append(Component.text(" wil samen spelen · kaart, Spellen", NamedTextColor.GRAY)));
        wie.playSound(wie, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
    }

    /** De uitnodiging voor deze speler (null = geen, of verlopen). */
    Uitnodiging uitnodiging(UUID id) {
        Uitnodiging u = uitnodigingen.get(id);
        if (u != null && u.tot() < System.currentTimeMillis()) {
            uitnodigingen.remove(id);
            return null;
        }
        return u;
    }

    /** De uitnodiging aannemen: samen met wie je uitnodigde. */
    void neemAan(Player p) {
        Uitnodiging u = uitnodiging(p.getUniqueId());
        if (u == null) return;
        uitnodigingen.remove(p.getUniqueId());
        if (spel.voortgang.actief(u.van()) != u.spel()) {    // hij speelt nu een ander spel
            spel.zeg(p, Component.text("Die uitnodiging geldt niet meer", NamedTextColor.GRAY));
            return;
        }
        String fout = samen(p.getUniqueId(), u.van());
        if (fout != null) {
            spel.zeg(p, Component.text(fout, NamedTextColor.GRAY));
            return;
        }
        uitnodigingen.remove(p.getUniqueId());
        Player gastheer = Bukkit.getPlayer(u.van());
        if (gastheer != null && spel.speelt(gastheer))
            spel.zeg(gastheer, Component.text(p.getName(), ZeldaSpel.GROEN).append(Component.text(" speelt mee", NamedTextColor.GRAY)));
    }

    /**
     * Zet {@code gast} in het spel waarin {@code met} nu speelt: heeft hij daar al een spel van, dan dat; anders een nieuw spel
     * op een vrije plek dat precies bij die voortgang begint (zijn eigen niveau, opties, flesjes, records en vosnaam). Is hij
     * in Zelda, dan komt hij binnen op de laatste rustplaats. Geeft een korte fout, of null.
     */
    String samen(UUID gast, UUID met) {
        if (gast.equals(met)) return "Dat ben je zelf";
        ZeldaVoortgang.Stand thuis = spel.voortgang.van(met);
        if (thuis.groep == null) {
            String g = "g" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(rnd.nextInt(1296), 36);
            spel.beheer.ookKopie(met, k -> k.groep = g);   // is hij in beheer: ook de voortgang die na het beheer terugkomt
            thuis.groep = g;
            spel.voortgang.vuil();
        }
        Integer al = null;
        for (var e : spel.voortgang.spellen(gast).entrySet()) if (thuis.groep.equals(e.getValue().groep)) al = e.getKey();
        int n = al != null ? al : spel.voortgang.vrij(gast);
        if (n < 0) return "Geen plek vrij · wis eerst een spel";
        if (al == null) {
            ZeldaVoortgang.Stand eigen = spel.voortgang.van(gast);
            ZeldaVoortgang.Stand s = thuis.kopie();
            s.tijden.clear();
            s.tellers.clear();
            s.dier = null;
            s.vlaggen.removeIf(v -> !ZeldaVoortgang.deelbaar(v));
            for (String v : eigen.vlaggen) if (!ZeldaVoortgang.deelbaar(v)) s.zet(v);
            s.niveau = eigen.niveau;
            s.steun = eigen.steun;
            s.vast = eigen.vast;
            ZeldaRegels.vulFlessen(s);
            spel.voortgang.nieuw(gast, n, s);
            spel.voortgang.sluitAan(gast, n);
        }
        Player p = Bukkit.getPlayer(gast);
        if (p != null && spel.speelt(p)) spel.wissel(p, n);
        else spel.voortgang.kies(gast, n);
        spel.bewaar();
        return null;
    }

    /** Het deel waar dit spel verder gaat (zijn laatste rustplaats, anders zijn hoofdstuk). */
    private ZeldaHoofdstuk waar(ZeldaVoortgang.Stand s) {
        String[] pl = ZeldaRegels.plek(s.laatste);
        if (pl != null && spel.deel(pl[0]) != null) return spel.deel(pl[0]);
        return spel.hoofdstuk(s.hoofdstuk);
    }

    /** De namen van wie er met je in dit spel samen speelt (null = alleen). */
    String samenMet(UUID id, ZeldaVoortgang.Stand s) {
        if (s.groep == null) return null;
        List<String> l = new ArrayList<>();
        for (var e : spel.voortgang.groep(s.groep))
            if (!e.getKey().equals(id)) {
                String n = naam(e.getKey());
                if (!l.contains(n)) l.add(n);
            }
        return l.isEmpty() ? null : String.join(", ", l);
    }

    private String naam(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = spel.voortgang.bekend(id) ? spel.voortgang.van(id).naam : null;
        return n == null || n.isEmpty() ? "?" : n;
    }
}
