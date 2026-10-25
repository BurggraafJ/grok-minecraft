package nl.burggraaf.mc.kist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /kist: persoonlijke opslag van 54 vakken per speler, bewaard in plugins/Kist/kisten/<uuid>.yml.
 * Eén Inventory-object per speler zolang hij open is (nooit twee kopieën = geen dupes); opslaan bij sluiten, bij
 * uitloggen, elke minuut en bij stoppen (eerst naar een .tmp-bestand, dan atomair vervangen).
 * /ec, /enderchest en varianten zijn voor iedereen geblokkeerd, ook voor OP's.
 */
public final class Kist extends JavaPlugin implements Listener {

    private static final Set<String> EC = Set.of("ec", "enderchest", "echest", "eec", "eechest", "eenderchest",
            "endersee", "eendersee", "essentials:ec", "essentials:enderchest", "essentials:echest", "essentials:eec",
            "essentials:eechest", "essentials:eenderchest", "essentials:endersee", "essentials:eendersee");

    private static final class Houder implements InventoryHolder {
        final UUID eigenaar;
        Inventory inv;

        Houder(UUID eigenaar) {
            this.eigenaar = eigenaar;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final Map<UUID, Houder> open = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new File(getDataFolder(), "kisten").mkdirs();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> open.values().forEach(this::bewaar), 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        for (Houder h : new ArrayList<>(open.values())) {
            bewaar(h);
            for (HumanEntity v : new ArrayList<>(h.inv.getViewers())) v.closeInventory();
        }
        open.clear();
    }

    private File bestand(UUID id) {
        return new File(new File(getDataFolder(), "kisten"), id + ".yml");
    }

    private Houder laad(UUID id) {
        Houder h = open.get(id);
        if (h != null) return h;
        h = new Houder(id);
        h.inv = Bukkit.createInventory(h, 54, Component.text("Mijn kist", NamedTextColor.DARK_PURPLE));
        YamlConfiguration y = YamlConfiguration.loadConfiguration(bestand(id));
        List<String> vakken = y.getStringList("vakken");
        for (int i = 0; i < Math.min(54, vakken.size()); i++) {
            String b = vakken.get(i);
            if (b == null || b.isEmpty()) continue;
            try {
                h.inv.setItem(i, ItemStack.deserializeBytes(Base64.getDecoder().decode(b)));
            } catch (RuntimeException e) {
                getLogger().warning("kist " + id + " vak " + i + " onleesbaar: " + e.getMessage());
            }
        }
        open.put(id, h);
        return h;
    }

    private void bewaar(Houder h) {
        List<String> vakken = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            ItemStack it = h.inv.getItem(i);
            vakken.add(it == null || it.isEmpty() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("vakken", vakken);
        File doel = bestand(h.eigenaar), tmp = new File(doel.getPath() + ".tmp");
        try {
            y.save(tmp);
            Files.move(tmp.toPath(), doel.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            getLogger().severe("kist " + h.eigenaar + " niet opgeslagen: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Alleen voor spelers.");
            return true;
        }
        if (!getConfig().getBoolean("aan", true)) {
            p.sendMessage(Component.text("/kist staat op deze server uit.", NamedTextColor.GRAY));
            return true;
        }
        p.openInventory(laad(p.getUniqueId()).inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.1f);
        return true;
    }

    @EventHandler
    public void onSluit(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Houder h)) return;
        bewaar(h);
        if (e.getPlayer() instanceof Player p) p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.6f, 1.1f);
        // uit het geheugen zodra niemand meer kijkt (de volgende /kist laadt het verse bestand)
        Bukkit.getScheduler().runTask(this, () -> {
            if (h.inv.getViewers().isEmpty() && open.get(h.eigenaar) == h) open.remove(h.eigenaar);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Houder h = open.get(e.getPlayer().getUniqueId());
        if (h != null) bewaar(h);
    }

    /** /ec en varianten: voor iedereen uit, ook OP's. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommando(PlayerCommandPreprocessEvent e) {
        String cmd = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        if (!EC.contains(cmd)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(Component.text("De enderchest-opdracht staat uit. Gebruik /kist voor je persoonlijke opslag.", NamedTextColor.GRAY));
    }
}
