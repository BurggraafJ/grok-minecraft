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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.block.Action;
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
 * De vanilla enderchest is geen eigen opslag meer (Jelle 26-10): rechtsklik op een enderchest-blok, /ec, /enderchest
 * en varianten openen altijd /kist. Bij inloggen (en bij het starten voor wie online is) gaat wat nog in je
 * enderchest zat naar je kist (eerst een backup in plugins/Kist/ender-backup/); past het niet, dan blijft de rest in
 * de enderchest staan en komt er een regel in plugins/Kist/overloop.log. Er gaat niets weg. Geen knop meer in de kist.
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
    private static final int GROOT = 99;
    private NamespacedKey knopKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new File(getDataFolder(), "kisten").mkdirs();
        knopKey = new NamespacedKey(this, "enderchest-knop");
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> open.values().forEach(this::bewaar), 1200L, 1200L);
        for (Player p : Bukkit.getOnlinePlayers()) enderNaarKist(p);
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
        for (int i = 0; i < 54; i++) if (isKnop(h.inv.getItem(i))) h.inv.setItem(i, null);   // oude enderchest-knop weg
        for (int i = 0; i < 54; i++) maak99(h.inv.getItem(i));
        open.put(id, h);
        return h;
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
        for (int i = 0; i < 54; i++) {
            if (isKnop(h.inv.getItem(i))) h.inv.setItem(i, null);       // oude knop: weg
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
            if (isKnop(it)) it = null;
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
        openKist(p);
        return true;
    }

    private void openKist(Player p) {
        if (!getConfig().getBoolean("aan", true)) return;
        enderNaarKist(p);
        Houder h = laad(p.getUniqueId());
        maakAlles99(p, h);
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.1f);
    }

    /** Zet alles van 'van' in 'naar' (eerst bijstapelen, dan lege vakken). Wat niet past blijft in 'van'. Geeft het aantal items dat bleef. */
    static int verplaats(Inventory van, Inventory naar) {
        int bleef = 0;
        for (int i = 0; i < van.getSize(); i++) {
            ItemStack it = van.getItem(i);
            if (it == null || it.isEmpty()) continue;
            Map<Integer, ItemStack> rest = naar.addItem(it.clone());
            if (rest.isEmpty()) van.setItem(i, null);
            else {
                ItemStack r = rest.values().iterator().next();
                van.setItem(i, r);
                bleef += r.getAmount();
            }
        }
        return bleef;
    }

    /** Enderchest-inhoud eenmalig naar /kist, met backup; niets gaat weg. */
    private void enderNaarKist(Player p) {
        if (!getConfig().getBoolean("aan", true)) return;
        Inventory ec = p.getEnderChest();
        if (ec.isEmpty()) return;
        UUID id = p.getUniqueId();
        List<String> vakken = new ArrayList<>();
        for (ItemStack it : ec.getContents())
            vakken.add(it == null || it.isEmpty() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        File map = new File(getDataFolder(), "ender-backup");
        map.mkdirs();
        YamlConfiguration b = new YamlConfiguration();
        b.set("speler", p.getName());
        b.set("vakken", vakken);
        try {
            b.save(new File(map, id + "-" + System.currentTimeMillis() + ".yml"));
        } catch (IOException ex) {
            getLogger().severe("enderchest-backup " + p.getName() + " mislukt, niets verplaatst: " + ex.getMessage());
            return;
        }
        Houder h = laad(id);
        int voor = 0;
        for (ItemStack it : ec.getContents()) if (it != null && !it.isEmpty()) voor += it.getAmount();
        int bleef = verplaats(ec, h.inv);
        for (int i = 0; i < 54; i++) maak99(h.inv.getItem(i));
        bewaar(h);
        if (h.inv.getViewers().isEmpty()) open.remove(id);
        getLogger().info("enderchest " + p.getName() + ": " + (voor - bleef) + " items naar /kist" + (bleef > 0 ? ", " + bleef + " bleven (kist vol)" : ""));
        if (bleef > 0) {
            try {
                Files.writeString(new File(getDataFolder(), "overloop.log").toPath(),
                        java.time.LocalDateTime.now() + " " + p.getName() + " " + id + ": " + bleef + " items bleven in de enderchest (kist vol)\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) { }
            p.sendMessage(Component.text("Je kist is vol: " + bleef + " items wachten nog veilig. Maak ruimte en open /kist opnieuw.", NamedTextColor.GRAY));
        } else {
            p.sendMessage(Component.text("Je enderchest-spullen staan nu in /kist.", NamedTextColor.GRAY));
        }
    }

    /** Rechtsklik op een enderchest-blok: /kist. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnderBlok(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null
                || e.getClickedBlock().getType() != Material.ENDER_CHEST || e.getPlayer().isSneaking()) return;
        if (!getConfig().getBoolean("aan", true)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline()) openKist(p); });
    }

    /** Vangnet: elke andere manier om je eigen enderchest te openen wordt ook /kist. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderOpen(InventoryOpenEvent e) {
        if (e.getInventory().getType() != InventoryType.ENDER_CHEST || !(e.getPlayer() instanceof Player p)) return;
        if (!getConfig().getBoolean("aan", true)) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline()) openKist(p); });
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
        // enderchest → kist: niets weg, rest blijft staan
        Inventory ec = Bukkit.createInventory(null, 27), kist = Bukkit.createInventory(null, 54);
        for (int i = 0; i < 53; i++) kist.setItem(i, new ItemStack(Material.DIRT, 64));
        kist.setItem(53, new ItemStack(Material.STONE, 60));
        ec.setItem(0, new ItemStack(Material.STONE, 10));
        ec.setItem(5, new ItemStack(Material.OAK_LOG, 7));
        int bleef = verplaats(ec, kist);
        if (bleef != 13 || kist.getItem(53).getAmount() != 64 || ec.getItem(0).getAmount() != 6 || ec.getItem(5).getAmount() != 7)
            fout.add("verplaats: bleef " + bleef + " (verwacht 13)");
        Inventory ec2 = Bukkit.createInventory(null, 27), leeg = Bukkit.createInventory(null, 54);
        ec2.setItem(3, new ItemStack(Material.DIAMOND, 5));
        if (verplaats(ec2, leeg) != 0 || !ec2.isEmpty() || leeg.getItem(0) == null || leeg.getItem(0).getAmount() != 5) fout.add("verplaats leeg");
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
        if (isKnop(e.getCurrentItem()) || isKnop(e.getCursor())) {      // oude knop: niet pakken, weghalen
            e.setCancelled(true);
            if (isKnop(e.getCurrentItem())) e.setCurrentItem(null);
            return;
        }
        if (e.getRawSlot() >= top || e.getRawSlot() < top) Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder(false) == h) maakAlles99(p, h);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSleep(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Houder h) || !(e.getWhoClicked() instanceof Player p)) return;
        if (isKnop(e.getOldCursor())) {
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
        Player pj = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> { if (pj.isOnline()) enderNaarKist(pj); }, 40L);
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
        openKist(e.getPlayer());
    }
}
