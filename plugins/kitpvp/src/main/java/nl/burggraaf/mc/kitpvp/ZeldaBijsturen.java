package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Bijsturen voor beheer (Jelle 28-10: "zodat ik kan bijsturen als ik met mijn vrienden speel"; alleen
 * {@code kitpvp.beheer}): zien wie er waar is en wie vastloopt (doden per gevecht, minuten zonder voortgang), en per speler
 * of groep het niveau of de steun van zijn spel zetten. In de game {@code /zelda …}, op de console {@code kitpvp zelda …}:
 * {@code spelers}, {@code niveau <wie> <niveau> [vast]|los}, {@code steun <wie> <-2…2>}, {@code volg}, {@code doden},
 * {@code spel <speler> [n]}, {@code samen <speler> <met>|los}. {@code <wie>} = een speler, {@code hier} (binnen
 * {@link #HIER} blokken van jou) of {@code allen} (iedereen in Zelda).
 */
final class ZeldaBijsturen {

    /** «hier»: wie binnen zoveel blokken van jou is (je groep). */
    static final double HIER = 48;
    static final List<String> SUB = List.of("spelers", "niveau", "steun", "volg", "doden", "spel", "samen");

    private final ZeldaSpel spel;
    /** Beheerders die een regel willen als iemand vastloopt ({@code /zelda volg}). */
    final Set<UUID> volgers = new HashSet<>();

    ZeldaBijsturen(ZeldaSpel spel) {
        this.spel = spel;
    }

    // ── meldingen ───────────────────────────────────────────────────────────────────────────

    /** Iemand ging dood in een gevecht ({@code n} keer sinds hij het won): bij 3, 6, 9 … een regel voor wie volgt. */
    void dood(Player p, ZeldaHoofdstuk.Kamer k, int n) {
        if (n < 3 || n % 3 != 0) return;
        meld(p.getName() + ": " + n + "× dood in " + k.naam + " (" + ZeldaRegels.niveau(spel.voortgang.van(p.getUniqueId())).naam + ")");
    }

    /** Iemand komt al {@code min} minuten niet verder. */
    void vast(Player p, long min) {
        ZeldaSpel.Sessie ss = spel.sessie(p);
        meld(p.getName() + ": " + min + " min niet verder" + (ss == null ? "" : " (" + ss.h.plekNaam(p.getLocation()) + ")"));
    }

    private void meld(String tekst) {
        for (UUID id : volgers) {
            Player b = Bukkit.getPlayer(id);
            if (b != null && b.hasPermission("kitpvp.beheer")) b.sendMessage(Component.text("Zelda · " + tekst, NamedTextColor.GRAY));
        }
    }

    // ── overzicht ───────────────────────────────────────────────────────────────────────────

    /** Eén regel over een speler in Zelda: spel, waar, niveau (en steun), harten, doden in zijn gevecht, minuten zonder voortgang. */
    String regel(Player p) {
        UUID id = p.getUniqueId();
        ZeldaSpel.Sessie ss = spel.sessie(p);
        ZeldaVoortgang.Stand s = spel.voortgang.van(id);
        StringBuilder sb = new StringBuilder(p.getName());
        sb.append(" · spel ").append(spel.voortgang.actief(id));
        String met = spel.spellen.samenMet(id, s);
        if (met != null) sb.append(" (samen met ").append(met).append(')');
        if (ss != null) sb.append(" · ").append(ss.vaart != null ? "op zee" : ss.h.plekNaam(p.getLocation()));
        sb.append(" · ").append(niveau(s));
        sb.append(" · ").append((int) Math.ceil(p.getHealth() / 2)).append('/').append(ZeldaRegels.harten(s)).append(" harten");
        if (ss != null) {
            String kamer = null;
            int meest = 0;
            for (var e : ss.pogingen.entrySet()) if (e.getValue() > meest) {
                meest = e.getValue();
                kamer = e.getKey();
            }
            if (kamer != null) {
                ZeldaHoofdstuk.Kamer k = spel.kamer(kamer);
                sb.append(" · ").append(meest).append("× dood in ").append(k == null ? kamer : k.naam);
            }
            long min = (System.currentTimeMillis() - ss.laatsteVoortgang) / 60_000L;
            if (min >= 3) sb.append(" · ").append(min).append(" min niet verder");
        }
        if (spel.beheer.is(id)) sb.append(" · beheer");
        return sb.toString();
    }

    /** «Held +1, vast»: het niveau van een spel zoals beheer het ziet. */
    static String niveau(ZeldaVoortgang.Stand s) {
        int st = ZeldaRegels.steun(s);
        return ZeldaRegels.niveau(s).naam + (st > 0 ? " +" + st : st < 0 ? " " + st : "") + (s.vast ? ", vast" : "");
    }

    List<String> overzicht() {
        List<String> l = new ArrayList<>();
        for (UUID id : new ArrayList<>(spel.sessies.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) l.add(regel(p));
        }
        if (l.isEmpty()) l.add("niemand in Zelda");
        return l;
    }

    /** De gevechten waar het meest wordt doodgegaan (alle spelers, alle spellen): kamer, doden, in hoeveel spellen. */
    List<String> doden() {
        Map<String, int[]> per = new LinkedHashMap<>();
        for (var sp : spel.voortgang.alle().values())
            for (ZeldaVoortgang.Stand s : sp.values())
                for (var t : s.tellers.entrySet())
                    if (t.getKey().startsWith("dood:") && t.getValue() > 0) {
                        int[] a = per.computeIfAbsent(t.getKey().substring(5), k -> new int[2]);
                        a[0] += t.getValue();
                        a[1]++;
                    }
        List<Map.Entry<String, int[]>> l = new ArrayList<>(per.entrySet());
        l.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);
        List<String> uit = new ArrayList<>();
        for (var e : l.subList(0, Math.min(10, l.size()))) {
            ZeldaHoofdstuk.Kamer k = spel.kamer(e.getKey());
            uit.add((k == null ? e.getKey() : k.naam) + ": " + e.getValue()[0] + "× dood, in " + e.getValue()[1] + (e.getValue()[1] == 1 ? " spel" : " spellen"));
        }
        if (uit.isEmpty()) uit.add("nog nergens doodgegaan");
        return uit;
    }

    /** Het menu met een hoofd per speler in Zelda: klik = bijsturen. */
    void menu(Player b) {
        ZeldaSpel.Menu m = new ZeldaSpel.Menu(false);
        m.inv = Bukkit.createInventory(m, 27, Component.text("Bijsturen", NamedTextColor.DARK_GRAY));
        int vak = 0;
        for (UUID id : new ArrayList<>(spel.sessies.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || vak >= 27) continue;
            ItemStack it = spel.ding(Material.PLAYER_HEAD, p.getName(), "bs:wie:" + id);
            if (it.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(p);
                it.setItemMeta(sm);
            }
            String[] r = regel(p).split(" · ");
            List<String> l = new ArrayList<>(List.of(r).subList(1, r.length));
            l.add("klik: bijsturen");
            ZeldaSpel.lore(it, l.toArray(new String[0]));
            m.inv.setItem(vak++, it);
        }
        if (vak == 0) m.inv.setItem(13, spel.ding(Material.GRAY_STAINED_GLASS_PANE, "Niemand in Zelda", "niets"));
        b.openInventory(m.inv);
    }

    /** Bijsturen voor één speler: de vier niveaus, steun − en +, vast of los. */
    void stuurMenu(Player b, UUID id) {
        ZeldaVoortgang.Stand s = spel.voortgang.van(id);
        Player p = Bukkit.getPlayer(id);
        ZeldaSpel.Menu m = new ZeldaSpel.Menu(false);
        m.inv = Bukkit.createInventory(m, 9, Component.text((p == null ? s.naam : p.getName()) + " · " + niveau(s), NamedTextColor.DARK_GRAY));
        ZeldaRegels.Niveau nu = ZeldaRegels.niveau(s);
        for (ZeldaRegels.Niveau n : ZeldaRegels.Niveau.values()) {
            ItemStack it = spel.ding(ZeldaSpellen.icoon(n), n.naam, "bs:niveau:" + id + ":" + n.ordinal());
            ZeldaSpel.lore(it, n == nu ? "zo speelt hij nu" : "klik: zetten");
            m.inv.setItem(n.ordinal(), it);
        }
        ItemStack min = spel.ding(Material.RED_DYE, "Steun −", "bs:steun:" + id + ":" + (ZeldaRegels.steun(s) - 1));
        ZeldaSpel.lore(min, "nu " + ZeldaRegels.steun(s), "15 % meer schade");
        m.inv.setItem(5, min);
        ItemStack plus = spel.ding(Material.LIME_DYE, "Steun +", "bs:steun:" + id + ":" + (ZeldaRegels.steun(s) + 1));
        ZeldaSpel.lore(plus, "nu " + ZeldaRegels.steun(s), "15 % minder schade, een flesje extra");
        m.inv.setItem(6, plus);
        ItemStack vast = spel.ding(s.vast ? Material.IRON_BARS : Material.TRIPWIRE_HOOK, s.vast ? "Vast" : "Los", "bs:vast:" + id);
        ZeldaSpel.lore(vast, s.vast ? "hij kan zijn niveau niet veranderen" : "hij kiest zelf", "klik: " + (s.vast ? "los" : "vast"));
        m.inv.setItem(8, vast);
        b.openInventory(m.inv);
    }

    /** Een klik in een bijstuurmenu; true als hij van ons was. */
    boolean klik(Player b, String soort, boolean rechts) {
        if (!soort.startsWith("bs:")) return false;
        if (!b.hasPermission("kitpvp.beheer")) return true;
        String[] d = soort.split(":");
        UUID id = UUID.fromString(d[2]);
        Bukkit.getScheduler().runTask(spel.plugin, () -> {
            switch (d[1]) {
                case "wie" -> stuurMenu(b, id);
                case "niveau" -> {
                    spel.zetNiveau(id, ZeldaRegels.Niveau.values()[Integer.parseInt(d[3])]);
                    stuurMenu(b, id);
                }
                case "steun" -> {
                    spel.zetSteun(id, Integer.parseInt(d[3]));
                    stuurMenu(b, id);
                }
                case "vast" -> {
                    ZeldaVoortgang.Stand s = spel.voortgang.van(id);
                    s.vast = !s.vast;
                    spel.voortgang.vuil();
                    stuurMenu(b, id);
                }
                default -> { }
            }
            spel.bewaar();
        });
        return true;
    }

    // ── commando's ──────────────────────────────────────────────────────────────────────────

    /** Wie er bedoeld is: een speler, {@code hier} of {@code allen}; leeg = niemand gevonden. */
    List<UUID> wie(String w, Player ik) {
        List<UUID> l = new ArrayList<>();
        if (w.equalsIgnoreCase("allen")) l.addAll(spel.sessies.keySet());
        else if (w.equalsIgnoreCase("hier")) {
            if (ik != null) for (UUID id : spel.sessies.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.getWorld().equals(ik.getWorld()) && p.getLocation().distanceSquared(ik.getLocation()) <= HIER * HIER) l.add(id);
            }
        } else {
            Player p = Bukkit.getPlayerExact(w);
            UUID id = p != null ? p.getUniqueId() : spel.voortgang.zoek(w);
            if (id != null) l.add(id);
        }
        return l;
    }

    private String naam(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = spel.voortgang.bekend(id) ? spel.voortgang.van(id).naam : null;
        return n == null || n.isEmpty() ? id.toString().substring(0, 8) : n;
    }

    /**
     * {@code args[0]} = het subcommando (zie {@link #SUB}). Geeft de regels voor wie het vroeg, of null als het niet van
     * ons is. {@code ik} = de beheerder in de game (voor {@code hier}, {@code volg} en het menu), of null op de console.
     */
    List<String> commando(CommandSender s, Player ik, String[] args) {
        if (args.length == 0) return null;
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> uit = new ArrayList<>();
        switch (sub) {
            case "spelers" -> {
                if (ik != null && args.length == 1) {
                    menu(ik);
                    return uit;
                }
                return overzicht();
            }
            case "doden" -> {
                return doden();
            }
            case "volg" -> {
                if (ik == null) return List.of("volg: alleen in de game");
                boolean aan = volgers.add(ik.getUniqueId());
                if (!aan) volgers.remove(ik.getUniqueId());
                return List.of("Zelda volgen " + (aan ? "aan: een regel als iemand vastloopt" : "uit"));
            }
            case "niveau" -> {
                if (args.length < 3) return List.of("niveau <speler|hier|allen> <verhaal|avontuur|held|legende> [vast] · niveau <wie> los");
                List<UUID> l = wie(args[1], ik);
                if (l.isEmpty()) return List.of("niemand gevonden: " + args[1]);
                boolean los = args[2].equalsIgnoreCase("los");
                ZeldaRegels.Niveau n = los ? null : ZeldaRegels.Niveau.van(args[2]);
                if (!los && n == null) return List.of("onbekend niveau: " + args[2] + " (verhaal, avontuur, held, legende)");
                boolean vast = args.length >= 4 && args[3].equalsIgnoreCase("vast");
                for (UUID id : l) {
                    ZeldaVoortgang.Stand st = spel.voortgang.van(id);
                    if (n != null) spel.zetNiveau(id, n);
                    st.vast = !los && (vast || st.vast);
                    spel.voortgang.vuil();
                    uit.add(naam(id) + ": " + niveau(st));
                }
                spel.bewaar();
                return uit;
            }
            case "steun" -> {
                if (args.length < 3) return List.of("steun <speler|hier|allen> <-2 … 2> (per stap 15 % schade, plus = ook een flesje extra)");
                List<UUID> l = wie(args[1], ik);
                if (l.isEmpty()) return List.of("niemand gevonden: " + args[1]);
                int st;
                try {
                    st = Integer.parseInt(args[2].replace("+", ""));
                } catch (NumberFormatException e) {
                    return List.of("geen getal: " + args[2]);
                }
                for (UUID id : l) {
                    spel.zetSteun(id, st);
                    uit.add(naam(id) + ": " + niveau(spel.voortgang.van(id)));
                }
                spel.bewaar();
                return uit;
            }
            case "spel" -> {
                if (args.length < 2) return List.of("spel <speler> [n]");
                List<UUID> l = wie(args[1], ik);
                if (l.size() != 1) return List.of("niemand gevonden: " + args[1]);
                UUID id = l.get(0);
                if (args.length >= 3) {
                    int n;
                    try {
                        n = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        return List.of("geen nummer: " + args[2]);
                    }
                    if (!spel.voortgang.spellen(id).containsKey(n)) return List.of(naam(id) + " heeft geen spel " + n);
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && spel.speelt(p)) spel.wissel(p, n);
                    else {
                        spel.voortgang.kies(id, n);
                        spel.bewaar();
                    }
                }
                int actief = spel.voortgang.actief(id);
                for (var e : spel.voortgang.spellen(id).entrySet()) {
                    ZeldaVoortgang.Stand st = e.getValue();
                    String met = spel.spellen.samenMet(id, st);
                    uit.add(naam(id) + " spel " + e.getKey() + (e.getKey() == actief ? " (nu)" : "") + ": " + st.vlaggen.size() + " vlaggen · "
                            + ZeldaRegels.harten(st) + " harten · " + niveau(st) + (met == null ? "" : " · samen met " + met));
                }
                if (uit.isEmpty()) uit.add(naam(id) + ": geen spellen");
                return uit;
            }
            case "samen" -> {
                if (args.length < 3) return List.of("samen <speler> <met speler> · samen <speler> los");
                List<UUID> l = wie(args[1], ik);
                if (l.size() != 1) return List.of("niemand gevonden: " + args[1]);
                UUID id = l.get(0);
                if (args[2].equalsIgnoreCase("los")) {
                    spel.voortgang.van(id).groep = null;
                    spel.voortgang.vuil();
                    spel.bewaar();
                    return List.of(naam(id) + ": spel " + spel.voortgang.actief(id) + " speelt weer alleen");
                }
                List<UUID> met = wie(args[2], ik);
                if (met.size() != 1) return List.of("niemand gevonden: " + args[2]);
                String fout = spel.spellen.samen(id, met.get(0));
                return List.of(fout != null ? naam(id) + ": " + fout
                        : naam(id) + " speelt nu samen met " + naam(met.get(0)) + " (spel " + spel.voortgang.actief(id) + ")");
            }
            default -> {
                return null;
            }
        }
    }

    /** Namen die met {@code begin} beginnen (hoofdletters maken niet uit). */
    static List<String> passend(List<String> namen, String begin) {
        List<String> l = new ArrayList<>();
        for (String n : namen) if (n.toLowerCase(Locale.ROOT).startsWith(begin.toLowerCase(Locale.ROOT))) l.add(n);
        return l;
    }

    /** Tab voor de subcommando's ({@code args[0]} is het subcommando). */
    List<String> tab(String[] args) {
        List<String> namen = new ArrayList<>();
        for (Player q : Bukkit.getOnlinePlayers()) namen.add(q.getName());
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("niveau", "steun").contains(sub)) {
            List<String> l = new ArrayList<>(List.of("hier", "allen"));
            l.addAll(namen);
            return passend(l, args[1]);
        }
        if (args.length == 2 && List.of("spel", "samen").contains(sub)) return passend(namen, args[1]);
        if (args.length == 3 && sub.equals("niveau")) {
            List<String> l = new ArrayList<>();
            for (ZeldaRegels.Niveau n : ZeldaRegels.Niveau.values()) l.add(n.naam.toLowerCase(Locale.ROOT));
            l.add("los");
            return passend(l, args[2]);
        }
        if (args.length == 4 && sub.equals("niveau")) return passend(List.of("vast"), args[3]);
        if (args.length == 3 && sub.equals("steun")) return passend(List.of("-2", "-1", "0", "1", "2"), args[2]);
        if (args.length == 3 && sub.equals("samen")) {
            List<String> l = new ArrayList<>(namen);
            l.add("los");
            return passend(l, args[2]);
        }
        return List.of();
    }
}
