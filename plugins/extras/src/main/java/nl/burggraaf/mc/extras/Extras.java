package nl.burggraaf.mc.extras;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /admin: een menu waarin admins de server-extra's live aan/uit zetten. De stand staat in
 * plugins/Extras/schakelaars.yml (op de server, niet in de repo; apply en ensure laten dat bestand met rust).
 * Hoe elke schakelaar werkt:
 *  - TreeFeller: TreeFeller eist permissie extras.treefeller (server.json-patch); uit = die permissie tijdelijk afnemen.
 *  - ChestSort+: uit = chestsort.use tijdelijk afnemen. Graven: uit = graves.place afnemen (gewone item-drop).
 *  - Veinminer: de eigen hoofdschakelaar van Veinminer (VeinMinerEvent.enabled).
 *  - Pijl, honger-vertraging, handlicht: config-vlag `aan` in die eigen plugins (live gelezen).
 *  - Slapen met één: gamerule players_sleeping_percentage 0 (aan) of 100 (uit); mc apply slaat die gamerule over als
 *    hij hier uit staat (zie `gamerules:` in schakelaars.yml).
 *  - Instant oven: deze plugin zet de kooktijd op oven-ticks.
 */
public final class Extras extends JavaPlugin implements Listener {

    private record Schakelaar(String sleutel, String naam, Material icoon, String uitleg) {}

    private static final List<Schakelaar> SCHAKELAARS = List.of(
            new Schakelaar("treefeller", "TreeFeller", Material.IRON_AXE, "Hele boom omhakken (sluipend hakken)"),
            new Schakelaar("veinminer", "Veinminer", Material.IRON_PICKAXE, "Hele ader in één keer (sluipend mijnen)"),
            new Schakelaar("graven", "Graven", Material.SKELETON_SKULL, "Graf met je spullen en XP als je doodgaat"),
            new Schakelaar("chestsort", "ChestSort+", Material.CHEST, "Kisten en inventaris sorteren"),
            new Schakelaar("pijl", "Pijl", Material.ARROW, "Richtingspijl in de actionbar (/pijl)"),
            new Schakelaar("honger", "Honger trager", Material.COOKED_BEEF, "Honger zakt 25% langzamer"),
            new Schakelaar("slapen", "Slapen met één", Material.RED_BED, "Eén speler in bed = dag"),
            new Schakelaar("oven", "Instant oven", Material.FURNACE, "Ovens, blast furnaces en smokers smelten direct"),
            new Schakelaar("handlicht", "Handlicht", Material.TORCH, "Fakkel of lantaarn in je hand geeft licht"),
            new Schakelaar("kist", "Kist", Material.ENDER_CHEST, "Persoonlijke opslag van 54 vakken (/kist)"));
    /** Indeling: 5 bovenaan, 5 eronder, symmetrisch; status-ruitje steeds eronder. */
    private static final int[] SLOTS = {9, 11, 13, 15, 17, 27, 29, 31, 33, 35};

    private File bestand;
    private YamlConfiguration stand;
    private final Map<UUID, PermissionAttachment> bijlagen = new HashMap<>();

    private static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bestand = new File(getDataFolder(), "schakelaars.yml");
        stand = YamlConfiguration.loadConfiguration(bestand);
        getServer().getPluginManager().registerEvents(this, this);
        // na het laden van alle plugins de stand doorvoeren (Veinminer, gamerule, configs van eigen plugins)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Schakelaar s : SCHAKELAARS) voerDoor(s.sleutel(), false);
            for (Player p : Bukkit.getOnlinePlayers()) rechten(p);
        }, 20L);
    }

    @Override
    public void onDisable() {
        for (PermissionAttachment a : bijlagen.values()) {
            try {
                a.remove();
            } catch (IllegalArgumentException ignored) {
            }
        }
        bijlagen.clear();
    }

    private boolean aan(String k) {
        return stand.getBoolean("schakelaars." + k, true);
    }

    private void zet(String k, boolean v) {
        stand.set("schakelaars." + k, v);
        stand.set("gamerules", null);
        if (!aan("slapen")) stand.set("gamerules.players_sleeping_percentage", 100);
        try {
            stand.save(bestand);
        } catch (IOException e) {
            getLogger().warning("schakelaars.yml: " + e.getMessage());
        }
    }

    // ── doorvoeren ──────────────────────────────────────────────────────────────────────────

    private void voerDoor(String k, boolean melden) {
        boolean v = aan(k);
        switch (k) {
            case "treefeller", "graven", "chestsort" -> {
                for (Player p : Bukkit.getOnlinePlayers()) rechten(p);
            }
            case "veinminer" -> veinminer(v);
            case "pijl" -> eigenVlag("Pijl", v);
            case "honger" -> eigenVlag("Honger", v);
            case "handlicht" -> eigenVlag("Handlicht", v);
            case "kist" -> eigenVlag("Kist", v);
            case "slapen" -> {
                // aan: alleen terugzetten als wij hem eerder op 100 hadden gezet (de waarde uit server.json is 0)
                for (World w : Bukkit.getWorlds()) {
                    Integer nu = w.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE);
                    if (!v) w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 100);
                    else if (nu != null && nu == 100) w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 0);
                }
            }
            default -> { }
        }
        if (melden) getLogger().info(k + " → " + (v ? "aan" : "uit"));
    }

    private void eigenVlag(String plugin, boolean v) {
        Plugin pl = Bukkit.getPluginManager().getPlugin(plugin);
        if (pl instanceof JavaPlugin jp) {
            if (jp.getConfig().getBoolean("aan", true) == v) return;
            jp.getConfig().set("aan", v);
            jp.saveConfig();
        }
    }

    private void veinminer(boolean v) {
        Plugin pl = Bukkit.getPluginManager().getPlugin("Veinminer");
        if (pl == null) return;
        try {
            Class<?> c = Class.forName("de.miraculixx.veinminer.VeinMinerEvent", true, pl.getClass().getClassLoader());
            Object inst = c.getField("INSTANCE").get(null);
            c.getMethod("setEnabled", boolean.class).invoke(inst, v);
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("Veinminer schakelen lukt niet: " + e);
        }
    }

    /** Tijdelijke (niet bewaarde) permissies: wat uit staat wordt de speler afgenomen. Gaat boven LuckPerms-groepen. */
    private void rechten(Player p) {
        PermissionAttachment a = bijlagen.computeIfAbsent(p.getUniqueId(), id -> p.addAttachment(this));
        Map<String, Boolean> wil = new LinkedHashMap<>();
        if (!aan("treefeller")) wil.put("extras.treefeller", false);
        if (!aan("chestsort")) wil.put("chestsort.use", false);
        if (!aan("graven")) wil.put("graves.place", false);
        for (String perm : List.of("extras.treefeller", "chestsort.use", "graves.place")) {
            if (wil.containsKey(perm)) a.setPermission(perm, false);
            else a.unsetPermission(perm);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        bijlagen.remove(e.getPlayer().getUniqueId());
        rechten(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        bijlagen.remove(e.getPlayer().getUniqueId());
    }

    // ── instant oven ────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmelt(FurnaceStartSmeltEvent e) {
        if (aan("oven")) e.setTotalCookTime(Math.max(1, getConfig().getInt("oven-ticks", 1)));
    }

    // ── het menu ────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 2 && sender.hasPermission("extras.admin")) {          // ook vanaf de console: admin <naam> aan|uit
            for (Schakelaar s : SCHAKELAARS) {
                if (s.sleutel().equalsIgnoreCase(args[0])) {
                    zet(s.sleutel(), args[1].equalsIgnoreCase("aan"));
                    voerDoor(s.sleutel(), true);
                    sender.sendMessage(s.naam() + ": " + (aan(s.sleutel()) ? "aan" : "uit"));
                    return true;
                }
            }
        }
        if (!(sender instanceof Player p)) {
            StringBuilder sb = new StringBuilder();
            for (Schakelaar s : SCHAKELAARS) sb.append(s.sleutel()).append('=').append(aan(s.sleutel()) ? "aan" : "uit").append(' ');
            sender.sendMessage(sb.toString().trim());
            return true;
        }
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, 45, Component.text("Server-extra's", NamedTextColor.DARK_RED));
        vul(m);
        p.openInventory(m.inv);
        openMenus.put(p.getUniqueId(), m);
        return true;
    }

    private static Component tekst(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private void vul(Menu m) {
        Inventory inv = m.inv;
        ItemStack glas = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glas.getItemMeta();
        gm.setHideTooltip(true);
        glas.setItemMeta(gm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glas);
        for (int i = 0; i < SCHAKELAARS.size(); i++) {
            Schakelaar s = SCHAKELAARS.get(i);
            boolean v = aan(s.sleutel());
            NamedTextColor kleur = v ? NamedTextColor.GREEN : NamedTextColor.RED;
            ItemStack it = new ItemStack(s.icoon());
            ItemMeta meta = it.getItemMeta();
            meta.displayName(tekst(s.naam() + ": " + (v ? "aan" : "uit"), kleur).decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(tekst(s.uitleg(), NamedTextColor.GRAY), tekst("Klik om " + (v ? "uit" : "aan") + " te zetten", NamedTextColor.DARK_GRAY)));
            meta.setEnchantmentGlintOverride(v);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(meta);
            inv.setItem(SLOTS[i], it);
            ItemStack st = new ItemStack(v ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            ItemMeta sm = st.getItemMeta();
            sm.displayName(tekst(v ? "aan" : "uit", kleur));
            st.setItemMeta(sm);
            inv.setItem(SLOTS[i] + 9, st);
        }
    }

    @EventHandler
    public void onKlik(InventoryClickEvent e) {
        Menu m = menuVan(e.getView().getTopInventory(), e.getWhoClicked().getUniqueId());
        if (m == null) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() < 0 || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
        if (!p.hasPermission("extras.admin")) {
            p.closeInventory();
            return;
        }
        for (int i = 0; i < SCHAKELAARS.size(); i++) {
            int slot = SLOTS[i];
            if (e.getRawSlot() != slot && e.getRawSlot() != slot + 9) continue;
            Schakelaar s = SCHAKELAARS.get(i);
            boolean nieuw = !aan(s.sleutel());
            zet(s.sleutel(), nieuw);
            voerDoor(s.sleutel(), true);
            p.playSound(p.getLocation(), nieuw ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, nieuw ? 1.6f : 0.8f);
            Component msg = Component.text("[Admin] ", NamedTextColor.DARK_RED)
                    .append(p.displayName().colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(" zette " + s.naam() + " " + (nieuw ? "aan" : "uit"), nieuw ? NamedTextColor.GREEN : NamedTextColor.RED));
            for (Player q : Bukkit.getOnlinePlayers()) if (q.hasPermission("extras.admin")) q.sendMessage(msg);
            vul(m);
            return;
        }
    }

    @EventHandler
    public void onSleep(InventoryDragEvent e) {
        if (menuVan(e.getView().getTopInventory(), e.getWhoClicked().getUniqueId()) != null) e.setCancelled(true);
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
