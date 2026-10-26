package nl.burggraaf.mc.huisdieren;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Survival-kant van «je huisdier gaat mee»: houdt per speler bij welke getemde dieren hij heeft en welke er meegaat
 * (standaard het laatst getemde; /huisdier kiest), maakt van dat dier een momentopname en zet die als ondertekende
 * cookie bij de speler. Het echte dier blijft altijd hier.
 */
final class Reizen implements Listener {

    static final NamespacedKey COOKIE = new NamespacedKey("huisdieren", "dier");

    private final Huisdieren plugin;
    private final File bestand;
    private final YamlConfiguration reg;
    private final byte[] sleutel;
    private boolean vuil;

    Reizen(Huisdieren plugin, byte[] sleutel) {
        this.plugin = plugin;
        this.sleutel = sleutel;
        this.bestand = new File(plugin.getDataFolder(), "reizen.yml");
        this.reg = YamlConfiguration.loadConfiguration(bestand);
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::ronde, 100L, 200L);       // elke 10 s
        for (Player p : Bukkit.getOnlinePlayers()) Bukkit.getScheduler().runTaskLater(plugin, () -> stuur(p), 60L);
    }

    void stop() {
        bewaar();
    }

    static boolean reisbaar(Entity e) {
        return Huisdieren.huisdier(e) && e.isValid() && reisbaarType(e);
    }

    /** Wolf, kat, papegaai en paardachtigen (paard, ezel, lama, kameel …) reizen mee; waterdieren niet. */
    static boolean reisbaarType(Entity e) {
        return e instanceof Wolf || e instanceof Cat || e instanceof Parrot || e instanceof AbstractHorse;
    }

    // ── register ────────────────────────────────────────────────────────────────────────────

    private ConfigurationSection dier(String id) {
        return reg.getConfigurationSection("dieren." + id);
    }

    private String actief(String speler) {
        return reg.getString("spelers." + speler + ".actief", "");
    }

    private void zetActief(String speler, String dierId) {
        reg.set("spelers." + speler + ".actief", dierId);
        reg.set("spelers." + speler + ".t", System.currentTimeMillis());
        vuil = true;
    }

    /** Wat er zichtbaar aan een dier is; verandert dit, dan maken we een nieuwe momentopname. */
    private String kenmerk(LivingEntity d) {
        StringBuilder sb = new StringBuilder(d.getType().name());
        sb.append('|').append(plugin.echteNaamJson(d)).append('|').append(d instanceof org.bukkit.entity.Ageable a && a.isAdult());
        if (d instanceof Wolf w) sb.append('|').append(w.getVariant().getKey()).append(w.getCollarColor());
        if (d instanceof Cat c) sb.append('|').append(c.getCatType().getKey()).append(c.getCollarColor());
        if (d instanceof Parrot p) sb.append('|').append(p.getVariant());
        if (d instanceof Horse h) sb.append('|').append(h.getColor()).append(h.getStyle());
        if (d instanceof Llama l) sb.append('|').append(l.getColor());
        if (d.getEquipment() != null) {
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.BODY, EquipmentSlot.SADDLE}) {
                try {
                    ItemStack it = d.getEquipment().getItem(s);
                    sb.append('|').append(it.getType()).append(it.hasItemMeta() ? it.getItemMeta().hashCode() : 0);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return sb.toString();
    }

    /** Dier bijwerken in het register; geeft true als de momentopname nieuw is. */
    private boolean registreer(LivingEntity d) {
        UUID eig = ((Tameable) d).getOwnerUniqueId();
        if (eig == null) return false;
        String id = d.getUniqueId().toString(), speler = eig.toString();
        ConfigurationSection s = dier(id);
        String k = kenmerk(d);
        boolean nieuw = s == null || !speler.equals(s.getString("eigenaar")) || !k.equals(s.getString("kenmerk")) || !s.contains("data");
        if (nieuw) {
            byte[] data;
            try {
                data = Bukkit.getUnsafe().serializeEntity(d);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("momentopname van " + d.getType() + " lukt niet: " + ex);
                return false;
            }
            if (s == null) {
                s = reg.createSection("dieren." + id);
                s.set("sinds", System.currentTimeMillis());
            }
            s.set("eigenaar", speler);
            s.set("type", d.getType().name());
            s.set("naam", plugin.naam(d));
            s.set("kenmerk", k);
            s.set("data", Base64.getEncoder().encodeToString(data));
            vuil = true;
        }
        String a = actief(speler);
        if (a.isEmpty() || (!a.equals("geen") && dier(a) == null)) {
            zetActief(speler, id);
            stuurAls(eig);
        } else if (nieuw && a.equals(id)) {
            reg.set("spelers." + speler + ".t", System.currentTimeMillis());
            stuurAls(eig);
        }
        return nieuw;
    }

    private void ronde() {
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) if (reisbaar(e)) registreer(e);
        }
        bewaar();
    }

    private void bewaar() {
        if (!vuil) return;
        vuil = false;
        try {
            plugin.getDataFolder().mkdirs();
            reg.save(bestand);
        } catch (IOException e) {
            plugin.getLogger().warning("reizen.yml: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTemmen(EntityTameEvent e) {
        if (!(e.getEntity() instanceof LivingEntity d)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!reisbaar(d)) return;
            registreer(d);
            UUID eig = ((Tameable) d).getOwnerUniqueId();
            zetActief(eig.toString(), d.getUniqueId().toString());       // het laatst getemde gaat mee
            stuurAls(eig);
            bewaar();
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAanpassen(PlayerInteractEntityEvent e) {               // naamkaartje, halsband verven, harnas
        if (!reisbaar(e.getRightClicked())) return;
        LivingEntity d = (LivingEntity) e.getRightClicked();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (reisbaar(d) && registreer(d)) bewaar();
        }, 2L);
    }

    @EventHandler
    public void onDood(EntityDeathEvent e) {
        String id = e.getEntity().getUniqueId().toString();
        ConfigurationSection s = dier(id);
        if (s == null) return;
        String speler = s.getString("eigenaar", "");
        reg.set("dieren." + id, null);
        vuil = true;
        if (id.equals(actief(speler))) {
            String volgende = nieuwste(speler);
            zetActief(speler, volgende == null ? "" : volgende);
            try {
                stuurAls(UUID.fromString(speler));
            } catch (IllegalArgumentException ignored) {
            }
        }
        bewaar();
    }

    private List<String> dierenVan(String speler) {
        List<String> uit = new ArrayList<>();
        ConfigurationSection d = reg.getConfigurationSection("dieren");
        if (d == null) return uit;
        for (String id : d.getKeys(false)) if (speler.equals(d.getString(id + ".eigenaar"))) uit.add(id);
        uit.sort((a, b) -> Long.compare(d.getLong(b + ".sinds"), d.getLong(a + ".sinds")));
        return uit;
    }

    private String nieuwste(String speler) {
        List<String> l = dierenVan(speler);
        return l.isEmpty() ? null : l.get(0);
    }

    // ── cookie ──────────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> stuur(p), 40L);
    }

    private void stuurAls(UUID speler) {
        Player p = Bukkit.getPlayer(speler);
        if (p != null) stuur(p);
    }

    Pakket pakket(String speler) {
        String a = actief(speler);
        long t = reg.getLong("spelers." + speler + ".t", 0L);
        ConfigurationSection s = a.isEmpty() || a.equals("geen") ? null : dier(a);
        if (s == null) return new Pakket(t, "", null);
        return new Pakket(t, a, Base64.getDecoder().decode(s.getString("data", "")));
    }

    void stuur(Player p) {
        if (sleutel == null || !p.isOnline()) return;
        try {
            byte[] b = pakket(p.getUniqueId().toString()).maak(sleutel, p.getUniqueId());
            if (b.length > Pakket.MAX) {
                plugin.getLogger().warning("huisdier van " + p.getName() + " te groot voor een cookie (" + b.length + " bytes)");
                return;
            }
            p.storeCookie(COOKIE, b);
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().fine("cookie: " + ex);
        }
    }

    // ── /huisdier: kiezen wie er meegaat ────────────────────────────────────────────────────

    private static final class Menu implements InventoryHolder {
        Inventory inv;
        final List<String> ids = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void menu(Player p) {
        String speler = p.getUniqueId().toString();
        for (World w : Bukkit.getWorlds()) {                                       // geladen dieren eerst bijwerken
            for (LivingEntity e : w.getLivingEntities()) {
                if (reisbaar(e) && p.getUniqueId().equals(((Tameable) e).getOwnerUniqueId())) registreer(e);
            }
        }
        bewaar();
        List<String> ids = dierenVan(speler);
        if (ids.isEmpty()) {
            p.sendActionBar(Component.text("Nog geen getemd dier. Tem er een, dan gaat hij mee.", NamedTextColor.GRAY));
            return;
        }
        Menu m = new Menu();
        int n = Math.min(ids.size(), 53);
        int maat = Math.min(54, ((n + 1 + 8) / 9) * 9);
        m.inv = Bukkit.createInventory(m, maat, Component.text("Welk dier gaat mee?"));
        String a = actief(speler);
        for (int i = 0; i < n; i++) {
            String id = ids.get(i);
            ConfigurationSection s = dier(id);
            Material mat = Material.matchMaterial(s.getString("type", "") + "_SPAWN_EGG");
            ItemStack it = new ItemStack(mat == null ? Material.NAME_TAG : mat);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.text(s.getString("naam", "?"), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(id.equals(a) ? "gaat mee naar lobby en kitpvp" : "klik: deze gaat mee", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            if (id.equals(a)) meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
            m.inv.setItem(i, it);
            m.ids.add(id);
        }
        ItemStack geen = new ItemStack(Material.BARRIER);
        ItemMeta gm = geen.getItemMeta();
        gm.displayName(Component.text("Geen dier mee", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (a.equals("geen")) gm.setEnchantmentGlintOverride(true);
        geen.setItemMeta(gm);
        m.inv.setItem(maat - 1, geen);
        p.openInventory(m.inv);
    }

    @EventHandler
    public void onKlik(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != m.inv) return;
        int slot = e.getSlot();
        String speler = p.getUniqueId().toString();
        if (slot == m.inv.getSize() - 1) {
            zetActief(speler, "geen");
            p.sendActionBar(Component.text("Er gaat geen dier mee", NamedTextColor.GRAY));
        } else if (slot >= 0 && slot < m.ids.size()) {
            String id = m.ids.get(slot);
            if (dier(id) == null) return;
            zetActief(speler, id);
            p.sendActionBar(Component.text(dier(id).getString("naam", "Je dier") + " gaat mee", NamedTextColor.GRAY));
        } else return;
        bewaar();
        stuur(p);
        p.closeInventory();
    }

    @EventHandler
    public void onSleep(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    String status() {
        ConfigurationSection d = reg.getConfigurationSection("dieren");
        ConfigurationSection s = reg.getConfigurationSection("spelers");
        int grootste = 0;
        if (d != null) for (String id : d.getKeys(false)) grootste = Math.max(grootste, d.getString(id + ".data", "").length() * 3 / 4);
        return "dieren=" + (d == null ? 0 : d.getKeys(false).size()) + " spelers=" + (s == null ? 0 : s.getKeys(false).size())
                + " grootste=" + grootste + "B sleutel=" + (sleutel != null);
    }
}
