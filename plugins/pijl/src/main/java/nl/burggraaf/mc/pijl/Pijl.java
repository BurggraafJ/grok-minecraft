package nl.burggraaf.mc.pijl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
 * je kijkrichting. /pijl opent een menu: per speler volgen aan/uit, kompas (noordpijl) aan/uit, onvindbaar (jij staat
 * in niemands pijl) en de pijl helemaal aan/uit. Instellingen per speler in instellingen.yml.
 */
public final class Pijl extends JavaPlugin implements Listener {

    private static final String[] PIJLEN = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};
    private static final int SLOT_KOMPAS = 46, SLOT_ONVINDBAAR = 48, SLOT_PIJL = 50, SLOT_SLUIT = 52;

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

    private void tik() {
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
            if (dichtst == null && !kompas) continue;
            Component balk = Component.empty();
            if (kompas) {
                balk = balk.append(Component.text("N", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text(pijlVoor(180, van.getYaw()), NamedTextColor.RED));
                if (dichtst != null) balk = balk.append(Component.text("  │  ", NamedTextColor.DARK_GRAY));
            }
            if (dichtst != null) {
                Location naar = dichtst.getLocation();
                double doelYaw = Math.toDegrees(Math.atan2(-(naar.getX() - van.getX()), naar.getZ() - van.getZ()));
                balk = balk.append(Component.text(pijlVoor(doelYaw, van.getYaw()) + " ", NamedTextColor.GOLD))
                        .append(dichtst.displayName().colorIfAbsent(NamedTextColor.WHITE))
                        .append(Component.text(" " + Math.round(Math.sqrt(best)) + "m", NamedTextColor.GRAY));
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

    private void openMenu(Player p) {
        Menu menu = new Menu();
        menu.inv = Bukkit.createInventory(menu, 54, Component.text("Pijl-instellingen", NamedTextColor.DARK_AQUA));
        vulMenu(p, menu);
        p.openInventory(menu.inv);
    }

    private void vulMenu(Player p, Menu menu) {
        Inventory inv = menu.inv;
        inv.clear();
        menu.hoofden.clear();
        UUID id = p.getUniqueId();
        Set<String> uit = verborgen(id);
        List<OfflinePlayer> spelers = new ArrayList<>();
        for (Player q : Bukkit.getOnlinePlayers()) if (q != p) spelers.add(q);
        for (OfflinePlayer q : Bukkit.getOfflinePlayers()) {
            if (spelers.size() >= 36) break;
            if (!q.getUniqueId().equals(id) && !q.isOnline() && q.getName() != null) spelers.add(q);
        }
        int slot = 0;
        for (OfflinePlayer q : spelers) {
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
            slot++;
        }
        ItemStack rand = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta rm = rand.getItemMeta();
        rm.displayName(Component.empty());
        rand.setItemMeta(rm);
        for (int i = 36; i < 54; i++) inv.setItem(i, rand);
        boolean kompas = vlag(id, "kompas", false), onvindbaar = vlag(id, "onvindbaar", false), pijl = pijlAan(p);
        inv.setItem(SLOT_KOMPAS, knop(Material.COMPASS, "Kompas", kompas, "Extra noordpijl (N⬆) in de actionbar"));
        inv.setItem(SLOT_KOMPAS - 9, status(kompas));
        inv.setItem(SLOT_ONVINDBAAR, knop(Material.ENDER_EYE, "Onvindbaar", onvindbaar, "Jij staat in niemands pijl"));
        inv.setItem(SLOT_ONVINDBAAR - 9, status(onvindbaar));
        inv.setItem(SLOT_PIJL, knop(pijl ? Material.LIME_WOOL : Material.RED_WOOL, "Pijl", pijl, "De richtingspijl in je actionbar"));
        inv.setItem(SLOT_PIJL - 9, status(pijl));
        ItemStack sluit = new ItemStack(Material.BARRIER);
        ItemMeta sm = sluit.getItemMeta();
        sm.displayName(tekst("Sluiten", NamedTextColor.RED));
        sluit.setItemMeta(sm);
        inv.setItem(SLOT_SLUIT, sluit);
    }

    @EventHandler
    public void onKlik(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != menu.inv) return;
        UUID id = p.getUniqueId();
        int slot = e.getRawSlot();
        if (menu.hoofden.containsKey(slot)) {
            String doel = menu.hoofden.get(slot).toString();
            List<String> lijst = new ArrayList<>(inst.getStringList(id + ".verborgen"));
            if (!lijst.remove(doel)) lijst.add(doel);
            inst.set(id + ".verborgen", lijst);
            bewaar();
        } else if (slot == SLOT_KOMPAS) {
            zetVlag(id, "kompas", !vlag(id, "kompas", false));
        } else if (slot == SLOT_ONVINDBAAR) {
            zetVlag(id, "onvindbaar", !vlag(id, "onvindbaar", false));
        } else if (slot == SLOT_PIJL) {
            boolean aan = !pijlAan(p);
            zetVlag(id, "pijl", aan);
            if (!aan) p.sendActionBar(Component.empty());
        } else if (slot == SLOT_SLUIT) {
            p.closeInventory();
            return;
        } else {
            return;
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        vulMenu(p, menu);
    }

    @EventHandler
    public void onSleep(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Alleen voor spelers.");
            return true;
        }
        openMenu(p);
        return true;
    }
}
