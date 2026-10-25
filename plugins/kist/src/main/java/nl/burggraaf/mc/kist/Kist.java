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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
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
 * /ec, /enderchest en varianten zijn voor iedereen geblokkeerd, ook voor OP's; vak 54 (rechtsonder) is een knop die je
 * gewone enderchest opent.
 * Tot 99 per vak: zolang de kist open is krijgen alle items die normaal tot 64 stapelen (in de kist én in je eigen
 * inventaris, zodat ze met elkaar samenvoegen) het component max_stack_size=99. Bij sluiten (ook uitloggen en stoppen),
 * bij weggooien en bij inloggen gaat het component van je eigen items weer af; stapels boven 64 worden dan gesplitst
 * (passen ze niet, dan vallen ze voor je voeten), er gaat dus niets verloren. In de kist zelf blijft het staan.
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
    private static final int KNOP = 53, GROOT = 99;
    private NamespacedKey knopKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new File(getDataFolder(), "kisten").mkdirs();
        knopKey = new NamespacedKey(this, "enderchest-knop");
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
        h.inv.setMaxStackSize(GROOT);
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
        // vak 54 wordt de enderchest-knop: wat daar lag eerst naar een vrij vak (of de eigenaar / de grond)
        ItemStack oud = h.inv.getItem(KNOP);
        h.inv.setItem(KNOP, null);
        if (oud != null && !oud.isEmpty() && !isKnop(oud)) {
            int vrij = -1;
            for (int i = 0; i < KNOP; i++) if (h.inv.getItem(i) == null || h.inv.getItem(i).isEmpty()) { vrij = i; break; }
            if (vrij >= 0) h.inv.setItem(vrij, oud);
            else {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    normaliseer(oud);
                    for (ItemStack r : splits(oud)) for (ItemStack rest : p.getInventory().addItem(r).values())
                        p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    p.sendMessage(Component.text("Het item uit vak 54 van je kist staat nu in je inventaris (daar zit nu de enderchest-knop).", NamedTextColor.GRAY));
                } else {
                    h.inv.setItem(KNOP, oud);           // eigenaar niet online: laten liggen tot hij zelf opent
                }
            }
        }
        if (h.inv.getItem(KNOP) == null) h.inv.setItem(KNOP, knop());
        for (int i = 0; i < KNOP; i++) maak99(h.inv.getItem(i));
        open.put(id, h);
        return h;
    }

    private ItemStack knop() {
        ItemStack it = new ItemStack(Material.ENDER_CHEST);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Enderchest", NamedTextColor.LIGHT_PURPLE).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Klik: open je gewone enderchest", NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(knopKey, PersistentDataType.BYTE, (byte) 1);
        m.setMaxStackSize(1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isKnop(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(knopKey);
    }

    // ── 99 per vak ──────────────────────────────────────────────────────────────────────────

    /** Van ons: max_stack_size=99 op een item dat normaal tot 64 stapelt. */
    private static boolean isGroot(ItemStack it) {
        if (it == null || it.isEmpty() || it.getType().getMaxStackSize() != 64 || !it.hasItemMeta()) return false;
        ItemMeta m = it.getItemMeta();
        return m.hasMaxStackSize() && m.getMaxStackSize() == GROOT;
    }

    private void maak99(ItemStack it) {
        if (it == null || it.isEmpty() || isKnop(it) || it.getType().getMaxStackSize() != 64 || isGroot(it)) return;
        ItemMeta m = it.getItemMeta();
        if (m == null || m.hasMaxStackSize()) return;                 // eigen component van iets anders: niet aankomen
        m.setMaxStackSize(GROOT);
        it.setItemMeta(m);
    }

    /** Component eraf (alleen op het item zelf; aantal blijft, eventueel boven 64 → daarna splits()). */
    private static void normaliseer(ItemStack it) {
        if (!isGroot(it)) return;
        ItemMeta m = it.getItemMeta();
        m.setMaxStackSize(null);
        it.setItemMeta(m);
    }

    /** Stapel in stukken van hooguit de normale maximale grootte. */
    private static List<ItemStack> splits(ItemStack it) {
        List<ItemStack> uit = new ArrayList<>();
        int max = it.getMaxStackSize(), n = it.getAmount();
        while (n > 0) {
            ItemStack d = it.clone();
            d.setAmount(Math.min(max, n));
            uit.add(d);
            n -= d.getAmount();
        }
        return uit;
    }

    /** Alle 99-componenten van ons uit een inventory halen; teveel per vak gaat terug als losse stapels (rest). */
    static List<ItemStack> normaliseerInventory(Inventory inv) {
        List<ItemStack> teveel = new ArrayList<>();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            ItemStack it = c[i];
            if (!isGroot(it)) continue;
            normaliseer(it);
            List<ItemStack> delen = splits(it);
            inv.setItem(i, delen.get(0));
            teveel.addAll(delen.subList(1, delen.size()));
        }
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack t : teveel) rest.addAll(inv.addItem(t).values());
        return rest;
    }

    private void normaliseerSpeler(Player p) {
        List<ItemStack> rest = new ArrayList<>(normaliseerInventory(p.getInventory()));
        ItemStack cursor = p.getItemOnCursor();
        if (isGroot(cursor)) {
            normaliseer(cursor);
            List<ItemStack> delen = splits(cursor);
            p.setItemOnCursor(delen.get(0));
            for (ItemStack d : delen.subList(1, delen.size())) rest.addAll(p.getInventory().addItem(d).values());
        }
        for (ItemStack r : rest) p.getWorld().dropItemNaturally(p.getLocation(), r);
    }

    private void maakAlles99(Player p, Houder h) {
        // knop altijd in vak 54 (bv. als een sorteer-plugin hem verschoof)
        if (!isKnop(h.inv.getItem(KNOP))) {
            for (int i = 0; i < KNOP; i++) {
                if (isKnop(h.inv.getItem(i))) {
                    h.inv.setItem(i, h.inv.getItem(KNOP));
                    h.inv.setItem(KNOP, knop());
                    break;
                }
            }
            if (!isKnop(h.inv.getItem(KNOP))) {
                ItemStack weg = h.inv.getItem(KNOP);
                h.inv.setItem(KNOP, knop());
                if (weg != null && !weg.isEmpty()) {
                    for (ItemStack r : h.inv.addItem(weg).values()) {
                        normaliseer(r);
                        for (ItemStack d : splits(r)) for (ItemStack rest : p.getInventory().addItem(d).values())
                            p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    }
                }
            }
        }
        for (int i = 0; i < KNOP; i++) {
            if (isKnop(h.inv.getItem(i))) h.inv.setItem(i, null);       // geen tweede knop
            maak99(h.inv.getItem(i));
        }
        PlayerInventory pi = p.getInventory();
        for (int i = 0; i < 36; i++) maak99(pi.getItem(i));
        maak99(pi.getItemInOffHand());
        maak99(p.getItemOnCursor());
    }

    private void bewaar(Houder h) {
        List<String> vakken = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            ItemStack it = h.inv.getItem(i);
            if (i == KNOP && isKnop(it)) it = null;
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
            if (args.length == 1 && args[0].equalsIgnoreCase("zelftest")) zelftest(sender);
            else sender.sendMessage("Alleen voor spelers (console: kist zelftest).");
            return true;
        }
        if (!getConfig().getBoolean("aan", true)) {
            p.sendMessage(Component.text("/kist staat op deze server uit.", NamedTextColor.GRAY));
            return true;
        }
        Houder h = laad(p.getUniqueId());
        maakAlles99(p, h);
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.1f);
        return true;
    }

    /** Test zonder speler: 99-component, samenvoegen, opslaan+laden, normaliseren en splitsen. */
    private void zelftest(CommandSender s) {
        List<String> fout = new ArrayList<>();
        Houder h = new Houder(UUID.randomUUID());
        h.inv = Bukkit.createInventory(h, 54);
        h.inv.setMaxStackSize(GROOT);
        ItemStack a = new ItemStack(Material.COBBLESTONE, 60), b = new ItemStack(Material.COBBLESTONE, 60);
        maak99(a);
        maak99(b);
        if (!isGroot(a) || a.getMaxStackSize() != 99) fout.add("maak99");
        h.inv.setItem(0, a);
        var over = h.inv.addItem(b);                                     // samenvoegen: 99 + 21
        if (h.inv.getItem(0).getAmount() != 99) fout.add("samenvoegen: vak 0 = " + h.inv.getItem(0).getAmount());
        if (h.inv.getItem(1) == null || h.inv.getItem(1).getAmount() != 21 || !over.isEmpty()) fout.add("rest naar vak 1");
        ItemStack ep = new ItemStack(Material.ENDER_PEARL, 16);
        maak99(ep);
        if (isGroot(ep) || ep.getMaxStackSize() != 16) fout.add("16-stapelaar aangepast");
        ItemStack zwaard = new ItemStack(Material.DIAMOND_SWORD);
        maak99(zwaard);
        if (isGroot(zwaard)) fout.add("1-stapelaar aangepast");
        // opslaan + laden
        ItemStack terug = ItemStack.deserializeBytes(h.inv.getItem(0).serializeAsBytes());
        if (terug.getAmount() != 99 || !isGroot(terug)) fout.add("opslaan/laden 99");
        // normaliseren in een spelersachtige inventory (36 vakken)
        Inventory inv = Bukkit.createInventory(null, 36);
        inv.setItem(0, h.inv.getItem(0).clone());
        inv.setItem(1, new ItemStack(Material.COBBLESTONE, 10));
        List<ItemStack> rest = normaliseerInventory(inv);
        int totaal = 0;
        for (ItemStack it : inv.getContents()) if (it != null && it.getType() == Material.COBBLESTONE) {
            totaal += it.getAmount();
            if (isGroot(it) || it.getAmount() > 64) fout.add("niet genormaliseerd: " + it.getAmount());
        }
        if (totaal != 109 || !rest.isEmpty()) fout.add("aantal na normaliseren " + totaal + " (verwacht 109), rest " + rest.size());
        ItemStack gewoon = new ItemStack(Material.COBBLESTONE, 64);
        if (!inv.getItem(0).isSimilar(gewoon)) fout.add("genormaliseerd item stapelt niet met gewone items");
        // vol: rest komt terug (wordt gedropt)
        Inventory vol = Bukkit.createInventory(null, 9);
        for (int i = 1; i < 9; i++) vol.setItem(i, new ItemStack(Material.DIRT, 64));
        ItemStack groot = new ItemStack(Material.STONE, 99);
        maak99(groot);
        vol.setItem(0, groot);
        List<ItemStack> r2 = normaliseerInventory(vol);
        int n2 = vol.getItem(0).getAmount() + r2.stream().mapToInt(ItemStack::getAmount).sum();
        if (n2 != 99) fout.add("vol: " + n2 + " van 99 terug");
        s.sendMessage(fout.isEmpty() ? "kist zelftest: alles ok" : "kist zelftest FOUT: " + String.join("; ", fout));
    }

    @EventHandler
    public void onSluit(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Houder h)) return;
        bewaar(h);
        if (e.getPlayer() instanceof Player p) {
            normaliseerSpeler(p);
            Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline()) normaliseerSpeler(p); });   // cursor die na sluiten terugvalt
            p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.6f, 1.1f);
        }
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onKlik(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Houder h) || !(e.getWhoClicked() instanceof Player p)) return;
        int top = e.getView().getTopInventory().getSize();
        boolean opKnop = e.getRawSlot() == KNOP || isKnop(e.getCurrentItem()) || isKnop(e.getCursor())
                || (e.getHotbarButton() >= 0 && e.getRawSlot() == KNOP);
        if (opKnop) {
            e.setCancelled(true);
            if (e.getRawSlot() == KNOP && p.getUniqueId().equals(h.eigenaar)) {
                Bukkit.getScheduler().runTask(this, () -> {
                    p.closeInventory();
                    p.openInventory(p.getEnderChest());
                    p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1f);
                });
            }
            return;
        }
        if (e.getRawSlot() >= top || e.getRawSlot() < top) Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder(false) == h) maakAlles99(p, h);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSleep(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Houder h) || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlots().contains(KNOP) || isKnop(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder(false) == h) maakAlles99(p, h);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeggooien(PlayerDropItemEvent e) {
        ItemStack it = e.getItemDrop().getItemStack();
        if (isKnop(it)) {
            e.getItemDrop().remove();
            return;
        }
        if (!isGroot(it)) return;
        normaliseer(it);
        List<ItemStack> delen = splits(it);
        e.getItemDrop().setItemStack(delen.get(0));
        for (ItemStack d : delen.subList(1, delen.size())) e.getPlayer().getWorld().dropItem(e.getItemDrop().getLocation(), d);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        normaliseerSpeler(e.getPlayer());       // na een crash met open kist
        // een verdwaalde knop (zou niet moeten) weghalen
        for (int i = 0; i < e.getPlayer().getInventory().getSize(); i++)
            if (isKnop(e.getPlayer().getInventory().getItem(i))) e.getPlayer().getInventory().setItem(i, null);
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
