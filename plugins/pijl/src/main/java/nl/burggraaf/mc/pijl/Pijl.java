package nl.burggraaf.mc.pijl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Richtingspijl in de actionbar naar de dichtstbijzijnde (ingeschakelde) andere speler: "⬆ Lotte 132m", relatief aan
 * je kijkrichting, plus eigen opgeslagen plekken (/pijl opslaan <naam>) in het aqua. /pijl opent een menu: per speler volgen aan/uit, kompas (noordpijl) aan/uit, onvindbaar (jij staat
 * in niemands pijl) en de pijl helemaal aan/uit. Instellingen per speler in instellingen.yml.
 */
public final class Pijl extends JavaPlugin implements Listener {

    private static final String[] PIJLEN = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};
    private static final int KOL_PIJL = 2, KOL_KOMPAS = 4, KOL_ONVINDBAAR = 6;   // onderste rij, gecentreerd met tussenruimte

    private final Map<UUID, Long> pauzeTot = new HashMap<>();
    private double bereik;
    private long pauzeMs;
    private File bestand;
    private YamlConfiguration inst;
    private NamespacedKey oudUitKey, doelKey;

    /** Het menu; de houder herkent onze inventory. */
    private static final class Menu implements InventoryHolder {
        Inventory inv;
        final Map<Integer, UUID> hoofden = new HashMap<>();
        final Map<Integer, String> plekken = new HashMap<>();
        int kompas = -1, onvindbaar = -1, pijl = -1;
        int bevestigSlot = -1;
        long bevestigTot;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        oudUitKey = new NamespacedKey(this, "uit");
        doelKey = new NamespacedKey(this, "doel");
        bereik = getConfig().getDouble("bereik", 1000);
        pauzeMs = getConfig().getLong("pauze-seconden", 3) * 1000L;
        bestand = new File(getDataFolder(), "instellingen.yml");
        inst = YamlConfiguration.loadConfiguration(bestand);
        long interval = Math.max(2, getConfig().getLong("interval-ticks", 10));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tik, interval, interval);
    }

    // ── instellingen ────────────────────────────────────────────────────────────────────────

    private boolean vlag(UUID id, String k, boolean standaard) {
        return inst.getBoolean(id + "." + k, standaard);
    }

    private void zetVlag(UUID id, String k, boolean v) {
        inst.set(id + "." + k, v);
        bewaar();
    }

    private Set<String> verborgen(UUID id) {
        return new HashSet<>(inst.getStringList(id + ".verborgen"));
    }

    private void bewaar() {
        try {
            inst.save(bestand);
        } catch (IOException e) {
            getLogger().warning("instellingen.yml: " + e.getMessage());
        }
    }

    private boolean pijlAan(Player p) {
        if (!inst.contains(p.getUniqueId() + ".pijl")) { // oude /pijl-uitstand (vorige versie) overnemen
            boolean oudUit = p.getPersistentDataContainer().has(oudUitKey);
            if (oudUit) {
                p.getPersistentDataContainer().remove(oudUitKey);
                zetVlag(p.getUniqueId(), "pijl", false);
            }
            return !oudUit;
        }
        return vlag(p.getUniqueId(), "pijl", true);
    }

    // ── de pijl ─────────────────────────────────────────────────────────────────────────────

    private boolean vindbaar(Player q) {
        return q.getGameMode() != GameMode.SPECTATOR && !q.isDead()
                && q.getMetadata("vanished").stream().noneMatch(m -> m.asBoolean())
                && !vlag(q.getUniqueId(), "onvindbaar", false);
    }

    private static String pijlVoor(double doelYaw, float eigenYaw) {
        double rel = ((doelYaw - eigenYaw) % 360 + 360) % 360;
        return PIJLEN[(int) Math.round(rel / 45.0) % 8];
    }

    /** Pijl voor de hele server aan/uit (via /admin, config `aan`). */
    private boolean serverAan() {
        return getConfig().getBoolean("aan", true);
    }

    // ── opgeslagen plekken ──────────────────────────────────────────────────────────────────

    private static final int MAX_PLEKKEN = 27;

    private record Plek(String naam, String wereld, double x, double y, double z, boolean aan) {}

    private List<Plek> plekken(UUID id) {
        List<Plek> lijst = new ArrayList<>();
        var sec = inst.getConfigurationSection(id + ".plekken");
        if (sec == null) return lijst;
        for (String k : sec.getKeys(false)) {
            var p = sec.getConfigurationSection(k);
            if (p == null) continue;
            lijst.add(new Plek(p.getString("naam", k), p.getString("wereld", "world"), p.getDouble("x"), p.getDouble("y"),
                    p.getDouble("z"), p.getBoolean("aan", true)));
        }
        lijst.sort(Comparator.comparing(q -> q.naam().toLowerCase()));
        return lijst;
    }

    private static String sleutel(String naam) {
        return naam.toLowerCase().replaceAll("[^a-z0-9_-]", "");
    }

    private Plek plek(UUID id, String naam) {
        String k = sleutel(naam);
        for (Plek p : plekken(id)) if (sleutel(p.naam()).equals(k)) return p;
        return null;
    }

    /** 84m, 999m, 2.4km */
    private static String afstand(double m) {
        return m < 1000 ? Math.round(m) + "m" : String.format(java.util.Locale.ROOT, "%.1fkm", m / 1000.0);
    }

    private static String richting(Location van, double x, double z) {
        double doelYaw = Math.toDegrees(Math.atan2(-(x - van.getX()), z - van.getZ()));
        return pijlVoor(doelYaw, van.getYaw());
    }

    private void tik() {
        if (!serverAan()) return;
        long nu = System.currentTimeMillis();
        double max2 = bereik * bereik;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || !pijlAan(p)) continue;
            if (pauzeTot.getOrDefault(p.getUniqueId(), 0L) > nu) continue;
            Location van = p.getLocation();
            Set<String> uit = verborgen(p.getUniqueId());
            Player dichtst = null;
            double best = max2;
            for (Player q : p.getWorld().getPlayers()) {
                if (q == p || !vindbaar(q) || uit.contains(q.getUniqueId().toString())) continue;
                double d2 = q.getLocation().distanceSquared(van);
                if (d2 <= best) {
                    best = d2;
                    dichtst = q;
                }
            }
            boolean kompas = vlag(p.getUniqueId(), "kompas", false);
            List<Component> delen = new ArrayList<>();
            if (kompas) {
                delen.add(Component.text("N", NamedTextColor.RED, TextDecoration.BOLD)
                        .append(Component.text(pijlVoor(180, van.getYaw()), NamedTextColor.RED)));
            }
            if (dichtst != null) {
                Location naar = dichtst.getLocation();
                delen.add(Component.text(richting(van, naar.getX(), naar.getZ()) + " ", NamedTextColor.GOLD)
                        .append(dichtst.displayName().colorIfAbsent(NamedTextColor.WHITE))
                        .append(Component.text(" " + afstand(Math.sqrt(best)), NamedTextColor.GRAY)));
            }
            // eigen plekken: de twee dichtstbijzijnde in deze wereld, de rest als +n
            List<Plek> actief = new ArrayList<>(), elders = new ArrayList<>();
            for (Plek pl : plekken(p.getUniqueId())) {
                if (!pl.aan()) continue;
                (pl.wereld().equals(p.getWorld().getName()) ? actief : elders).add(pl);
            }
            actief.sort(Comparator.comparingDouble(pl -> Math.hypot(pl.x() - van.getX(), pl.z() - van.getZ())));
            Component plekDeel = null;
            for (int i = 0; i < Math.min(2, actief.size()); i++) {
                Plek pl = actief.get(i);
                Component c = Component.text(richting(van, pl.x(), pl.z()) + " ", NamedTextColor.AQUA)
                        .append(Component.text(pl.naam(), NamedTextColor.AQUA))
                        .append(Component.text(" " + afstand(Math.hypot(pl.x() - van.getX(), pl.z() - van.getZ())), NamedTextColor.GRAY));
                plekDeel = plekDeel == null ? c : plekDeel.append(Component.text("  ", NamedTextColor.GRAY)).append(c);
            }
            int rest = Math.max(0, actief.size() - 2) + elders.size();
            if (plekDeel == null && !elders.isEmpty()) {
                plekDeel = Component.text(elders.get(0).naam() + " (andere wereld)", NamedTextColor.DARK_AQUA);
                rest--;
            }
            if (plekDeel != null && rest > 0) plekDeel = plekDeel.append(Component.text(" +" + rest, NamedTextColor.DARK_GRAY));
            if (plekDeel != null) delen.add(plekDeel);
            if (delen.isEmpty()) continue;
            Component balk = Component.empty();
            for (int i = 0; i < delen.size(); i++) {
                if (i > 0) balk = balk.append(Component.text("  │  ", NamedTextColor.DARK_GRAY));
                balk = balk.append(delen.get(i));
            }
            p.sendActionBar(balk);
        }
    }

    private void pauze(Player p) {
        pauzeTot.put(p.getUniqueId(), System.currentTimeMillis() + pauzeMs);
    }

    @EventHandler public void onBed(PlayerBedEnterEvent e) { pauze(e.getPlayer()); }
    @EventHandler public void onVehicle(VehicleEnterEvent e) { if (e.getEntered() instanceof Player p) pauze(p); }
    @EventHandler public void onMount(EntityMountEvent e) { if (e.getEntity() instanceof Player p) pauze(p); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { pauze(e.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) { pauze(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { pauzeTot.remove(e.getPlayer().getUniqueId()); }

    // ── het menu ────────────────────────────────────────────────────────────────────────────

    private static Component tekst(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack knop(Material m, String naam, boolean aan, String uitleg) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(tekst(naam + ": " + (aan ? "aan" : "uit"), aan ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(tekst(uitleg, NamedTextColor.GRAY), tekst("Klik om " + (aan ? "uit" : "aan") + " te zetten", NamedTextColor.DARK_GRAY)));
        meta.setEnchantmentGlintOverride(aan);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack status(boolean aan) {
        ItemStack it = new ItemStack(aan ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(tekst(aan ? "aan" : "uit", aan ? NamedTextColor.GREEN : NamedTextColor.RED));
        it.setItemMeta(meta);
        return it;
    }

    private List<OfflinePlayer> spelersVoor(Player p) {
        List<OfflinePlayer> online = new ArrayList<>(), offline = new ArrayList<>();
        for (Player q : Bukkit.getOnlinePlayers()) if (q != p) online.add(q);
        for (OfflinePlayer q : Bukkit.getOfflinePlayers())
            if (!q.getUniqueId().equals(p.getUniqueId()) && !q.isOnline() && q.getName() != null) offline.add(q);
        Comparator<OfflinePlayer> opNaam = Comparator.comparing(q -> q.getName() == null ? "" : q.getName().toLowerCase());
        online.sort(opNaam);
        offline.sort(opNaam);
        online.addAll(offline);
        return online.size() > 36 ? online.subList(0, 36) : online;
    }

    private void openMenu(Player p) {
        Menu menu = new Menu();
        int plekRijen = (plekken(p.getUniqueId()).size() + 8) / 9;
        int hoofdRijen = Math.max(1, Math.min(4 - plekRijen, (spelersVoor(p).size() + 8) / 9));
        menu.inv = Bukkit.createInventory(menu, (hoofdRijen + plekRijen + 2) * 9, Component.text("Pijl-instellingen", NamedTextColor.DARK_AQUA));
        vulMenu(p, menu);
        p.openInventory(menu.inv);
        openMenus.put(p.getUniqueId(), menu);
    }

    /** Kolommen voor k items, symmetrisch rond het midden (bij een even aantal blijft het midden vrij). */
    private static int[] gecentreerd(int k) {
        int[] kol = new int[k];
        if (k % 2 == 1) {
            for (int i = 0; i < k; i++) kol[i] = (9 - k) / 2 + i;
        } else {
            int start = (9 - (k + 1)) / 2, j = 0;
            for (int i = 0; i <= k; i++) if (start + i != 4) kol[j++] = start + i;
        }
        return kol;
    }

    private void vulMenu(Player p, Menu menu) {
        Inventory inv = menu.inv;
        inv.clear();
        menu.hoofden.clear();
        UUID id = p.getUniqueId();
        Set<String> uit = verborgen(id);
        List<Plek> eigen = plekken(id);
        menu.plekken.clear();
        int plekRijen = (eigen.size() + 8) / 9;
        int rijen = inv.getSize() / 9, hoofdRijen = Math.max(0, rijen - 2 - plekRijen);
        if (rijen - 2 - hoofdRijen < plekRijen) plekRijen = rijen - 2 - hoofdRijen;  // menu is kleiner dan nu nodig
        if (eigen.size() > plekRijen * 9) eigen = eigen.subList(0, plekRijen * 9);
        Location van = p.getLocation();
        for (int i = 0; i < eigen.size(); i++) {
            Plek pl = eigen.get(i);
            int rij = i / 9, inRij = Math.min(9, eigen.size() - rij * 9);
            int slot = (hoofdRijen + rij) * 9 + gecentreerd(inRij)[i % 9];
            boolean bevestig = slot == menu.bevestigSlot && System.currentTimeMillis() < menu.bevestigTot;
            ItemStack it = new ItemStack(bevestig ? Material.BARRIER : Material.LODESTONE);
            ItemMeta m = it.getItemMeta();
            NamedTextColor kleur = pl.aan() ? NamedTextColor.GREEN : NamedTextColor.RED;
            boolean hier = pl.wereld().equals(p.getWorld().getName());
            String afstandTekst = hier ? afstand(Math.hypot(pl.x() - van.getX(), pl.z() - van.getZ())) : "(andere wereld)";
            if (bevestig) {
                m.displayName(tekst("Verwijder " + pl.naam() + "?", NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
                m.lore(List.of(tekst("Shift- of rechtsklik nog een keer om te verwijderen", NamedTextColor.GRAY),
                        tekst("Gewoon klikken = annuleren", NamedTextColor.DARK_GRAY)));
            } else {
                m.displayName(tekst("📍 " + pl.naam(), kleur).decoration(TextDecoration.BOLD, true));
                m.lore(List.of(tekst(pl.aan() ? "● In je pijl" : "● Niet in je pijl", kleur),
                        tekst(Math.round(pl.x()) + " " + Math.round(pl.y()) + " " + Math.round(pl.z()) + " · " + pl.wereld(), NamedTextColor.GRAY),
                        tekst("Afstand: " + afstandTekst, NamedTextColor.GRAY),
                        tekst("Klik: aan/uit", NamedTextColor.DARK_GRAY),
                        tekst("Shift- of rechtsklik: verwijderen", NamedTextColor.DARK_GRAY)));
                m.setEnchantmentGlintOverride(pl.aan());
            }
            it.setItemMeta(m);
            inv.setItem(slot, it);
            menu.plekken.put(slot, pl.naam());
        }
        List<OfflinePlayer> spelers = spelersVoor(p);
        if (spelers.size() > hoofdRijen * 9) spelers = spelers.subList(0, hoofdRijen * 9);
        for (int i = 0; i < spelers.size(); i++) {
            OfflinePlayer q = spelers.get(i);
            int rij = i / 9, inRij = Math.min(9, spelers.size() - rij * 9);
            int slot = rij * 9 + gecentreerd(inRij)[i % 9];
            boolean zichtbaar = !uit.contains(q.getUniqueId().toString());
            ItemStack hoofd = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) hoofd.getItemMeta();
            sm.setOwningPlayer(q);
            Component naam = q.isOnline() && q.getPlayer() != null ? q.getPlayer().displayName() : Component.text(q.getName());
            sm.displayName(naam.colorIfAbsent(zichtbaar ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .color(zichtbaar ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            sm.lore(List.of(tekst(zichtbaar ? "● Zichtbaar in je pijl" : "● Verborgen in je pijl", zichtbaar ? NamedTextColor.GREEN : NamedTextColor.RED),
                    tekst(q.isOnline() ? "online" : "offline", NamedTextColor.DARK_GRAY),
                    tekst("Klik om te " + (zichtbaar ? "verbergen" : "volgen"), NamedTextColor.DARK_GRAY)));
            sm.setEnchantmentGlintOverride(zichtbaar);
            sm.getPersistentDataContainer().set(doelKey, PersistentDataType.STRING, q.getUniqueId().toString());
            hoofd.setItemMeta(sm);
            inv.setItem(slot, hoofd);
            menu.hoofden.put(slot, q.getUniqueId());
        }
        ItemStack rand = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta rm = rand.getItemMeta();
        rm.setHideTooltip(true);
        rand.setItemMeta(rm);
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, rand);
        int onder = (rijen - 1) * 9;
        menu.kompas = onder + KOL_KOMPAS;
        menu.onvindbaar = onder + KOL_ONVINDBAAR;
        menu.pijl = onder + KOL_PIJL;
        boolean kompas = vlag(id, "kompas", false), onvindbaar = vlag(id, "onvindbaar", false), pijl = pijlAan(p);
        inv.setItem(menu.pijl, knop(pijl ? Material.LIME_WOOL : Material.RED_WOOL, "Pijl", pijl, "De richtingspijl in je actionbar"));
        inv.setItem(menu.pijl - 9, status(pijl));
        inv.setItem(menu.kompas, knop(Material.COMPASS, "Kompas", kompas, "Extra noordpijl (N⬆) in de actionbar"));
        inv.setItem(menu.kompas - 9, status(kompas));
        inv.setItem(menu.onvindbaar, knop(Material.ENDER_EYE, "Onvindbaar", onvindbaar, "Jij staat in niemands pijl"));
        inv.setItem(menu.onvindbaar - 9, status(onvindbaar));
    }

    @EventHandler
    public void onKlik(InventoryClickEvent e) {
        Menu menu = menuVan(e.getView().getTopInventory(), e.getWhoClicked().getUniqueId());
        if (menu == null) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() < 0 || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
        UUID id = p.getUniqueId();
        int slot = e.getRawSlot();
        if (menu.plekken.containsKey(slot)) {
            String naam = menu.plekken.get(slot);
            boolean wis = e.isShiftClick() || e.isRightClick();
            if (wis && slot == menu.bevestigSlot && System.currentTimeMillis() < menu.bevestigTot) {
                inst.set(id + ".plekken." + sleutel(naam), null);
                bewaar();
                menu.bevestigSlot = -1;
                p.sendMessage(Component.text("Plek " + naam + " verwijderd.", NamedTextColor.GRAY));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 1.2f);
                p.closeInventory();
                openMenu(p);   // menu kan kleiner worden
                return;
            } else if (wis) {
                menu.bevestigSlot = slot;
                menu.bevestigTot = System.currentTimeMillis() + 5000;
            } else if (slot == menu.bevestigSlot) {
                menu.bevestigSlot = -1;
            } else {
                String pad = id + ".plekken." + sleutel(naam) + ".aan";
                inst.set(pad, !inst.getBoolean(pad, true));
                bewaar();
            }
        } else if (menu.hoofden.containsKey(slot)) {
            String doel = menu.hoofden.get(slot).toString();
            List<String> lijst = new ArrayList<>(inst.getStringList(id + ".verborgen"));
            if (!lijst.remove(doel)) lijst.add(doel);
            inst.set(id + ".verborgen", lijst);
            bewaar();
        } else if (slot == menu.kompas) {
            zetVlag(id, "kompas", !vlag(id, "kompas", false));
        } else if (slot == menu.onvindbaar) {
            zetVlag(id, "onvindbaar", !vlag(id, "onvindbaar", false));
        } else if (slot == menu.pijl) {
            boolean aan = !pijlAan(p);
            zetVlag(id, "pijl", aan);
            if (!aan) p.sendActionBar(Component.empty());
        } else {
            return;
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        vulMenu(p, menu);
    }

    @EventHandler
    public void onSleep(InventoryDragEvent e) {
        if (menuVan(e.getView().getTopInventory(), e.getWhoClicked().getUniqueId()) != null) e.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Alleen voor spelers.");
            return true;
        }
        if (!serverAan()) {
            p.sendMessage(Component.text("De pijl staat op deze server uit.", NamedTextColor.GRAY));
            return true;
        }
        UUID id = p.getUniqueId();
        if (args.length >= 1 && (args[0].equalsIgnoreCase("opslaan") || args[0].equalsIgnoreCase("verwijder"))) {
            if (args.length < 2) {
                p.sendMessage(Component.text("Gebruik: /pijl " + args[0].toLowerCase() + " <naam>", NamedTextColor.RED));
                return true;
            }
            String naam = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
            String k = sleutel(naam);
            if (k.isEmpty() || naam.length() > 24) {
                p.sendMessage(Component.text("Kies een naam van max. 24 tekens met letters of cijfers.", NamedTextColor.RED));
                return true;
            }
            if (args[0].equalsIgnoreCase("verwijder")) {
                Plek pl = plek(id, naam);
                if (pl == null) {
                    p.sendMessage(Component.text("Je hebt geen plek met de naam " + naam + ".", NamedTextColor.RED));
                } else {
                    inst.set(id + ".plekken." + k, null);
                    bewaar();
                    p.sendMessage(Component.text("Plek " + pl.naam() + " verwijderd.", NamedTextColor.GRAY));
                }
                return true;
            }
            if (plek(id, naam) == null && plekken(id).size() >= MAX_PLEKKEN) {
                p.sendMessage(Component.text("Je hebt al " + MAX_PLEKKEN + " plekken; verwijder er eerst een.", NamedTextColor.RED));
                return true;
            }
            Location l = p.getLocation();
            String pad = id + ".plekken." + k;
            inst.set(pad + ".naam", naam);
            inst.set(pad + ".wereld", l.getWorld().getName());
            inst.set(pad + ".x", Math.floor(l.getX()) + 0.5);
            inst.set(pad + ".y", Math.floor(l.getY()));
            inst.set(pad + ".z", Math.floor(l.getZ()) + 0.5);
            inst.set(pad + ".aan", true);
            bewaar();
            p.sendMessage(Component.text("📍 Plek " + naam + " opgeslagen (" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                    + "). Hij staat nu in je pijl; zet hem uit in /pijl.", NamedTextColor.AQUA));
            return true;
        }
        openMenu(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return java.util.stream.Stream.of("opslaan", "verwijder").filter(a -> a.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("verwijder") && sender instanceof Player p) {
            return plekken(p.getUniqueId()).stream().map(Plek::naam)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }

    /** Open menu's per speler (naast de holder-check; sommige servers geven een kopie-inventory terug). */
    private final Map<UUID, Menu> openMenus = new HashMap<>();

    private Menu menuVan(Inventory top, UUID speler) {
        if (top.getHolder(false) instanceof Menu m) return m;
        Menu m = openMenus.get(speler);
        return m != null && top.getSize() == m.inv.getSize() ? m : null;
    }

    @EventHandler
    public void onMenuDicht(org.bukkit.event.inventory.InventoryCloseEvent e) {
        Menu m = openMenus.get(e.getPlayer().getUniqueId());
        if (m != null && menuVan(e.getView().getTopInventory(), e.getPlayer().getUniqueId()) == m) openMenus.remove(e.getPlayer().getUniqueId());
    }

}
