package nl.burggraaf.mc.kistzicht;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kistzicht:
 *  - hologram: kijk je (binnen hologram-afstand) naar een kist, trapped chest of barrel, dan zie alleen jij erboven een
 *    TextDisplay met de top-N items + aantal (dubbele kist = beide helften). Eén niet-persistente display per speler,
 *    elke 5 ticks bijgewerkt; weg zodra je wegkijkt, de kist weg is, of je uitlogt. Niets wordt opgeslagen.
 *  - /unload: hoofdinventaris (vakken 9-35; niet hotbar, armor, offhand) naar kisten/barrels binnen unload-straal die
 *    dat item (zelfde soort + data) al bevatten; groene deeltjeslijn naar elke kist die iets kreeg.
 *  - tips: elke tip-minuten één tip uit tips-lijst (om de beurt), alleen als er iemand online is.
 * Schakelaars `aan`, `unload`, `tips` in config.yml (live, via /admin van Extras).
 */
public final class Kistzicht extends JavaPlugin implements Listener {

    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private final Map<UUID, Location> doelen = new HashMap<>();
    private int tipIndex = 0;
    private int tipTeller = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::hologramTick, 20L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, this::tipTick, 20L * 60, 20L * 60);   // elke minuut tellen
    }

    @Override
    public void onDisable() {
        for (TextDisplay d : displays.values()) if (d.isValid()) d.remove();
        displays.clear();
        doelen.clear();
    }

    private boolean vlag(String k) {
        return getConfig().getBoolean(k, true);
    }

    // ── hologram ────────────────────────────────────────────────────────────────────────────

    private static boolean isKist(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL;
    }

    /** Inventory van een kist/barrel (dubbele kist = beide helften), of null. */
    private static Inventory inhoud(Block b) {
        if (!isKist(b.getType())) return null;
        BlockState st = b.getState(false);
        if (st instanceof Chest c) return c.getInventory();
        if (st instanceof Barrel br) return br.getInventory();
        return null;
    }

    /** Midden-boven van de kist (dubbele kist: midden tussen beide helften). */
    private static Location bovenkant(Block b, Inventory inv) {
        Location l = b.getLocation().add(0.5, 0, 0.5);
        InventoryHolder h = inv.getHolder(false);
        if (h instanceof DoubleChest dc && dc.getLeftSide(false) instanceof Chest a && dc.getRightSide(false) instanceof Chest c) {
            l = a.getLocation().add(c.getLocation()).multiply(0.5).add(0.5, 0, 0.5);
        }
        return l.add(0, 1.15, 0);
    }

    private void hologramTick() {
        boolean aan = vlag("aan");
        int afstand = Math.max(1, getConfig().getInt("hologram-afstand", 5));
        Set<UUID> gezien = new HashSet<>();
        if (aan) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                Block b = p.getTargetBlockExact(afstand);
                if (b == null) continue;
                Inventory inv = inhoud(b);
                if (inv == null) continue;
                gezien.add(p.getUniqueId());
                toon(p, bovenkant(b, inv), tekst(inv));
            }
        }
        for (UUID id : new ArrayList<>(displays.keySet())) if (!gezien.contains(id)) weg(id);
    }

    private void toon(Player p, Location waar, Component tekst) {
        TextDisplay d = displays.get(p.getUniqueId());
        if (d != null && (!d.isValid() || d.getWorld() != waar.getWorld())) {
            weg(p.getUniqueId());
            d = null;
        }
        if (d == null) {
            d = waar.getWorld().spawn(waar, TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setVisibleByDefault(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setSeeThrough(false);
                t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
                t.setBrightness(new Display.Brightness(15, 15));
                t.text(tekst);
            });
            p.showEntity(this, d);
            displays.put(p.getUniqueId(), d);
            doelen.put(p.getUniqueId(), waar);
            return;
        }
        Location oud = doelen.get(p.getUniqueId());
        if (oud == null || oud.distanceSquared(waar) > 0.01) {
            d.teleport(waar);
            doelen.put(p.getUniqueId(), waar);
        }
        if (!tekst.equals(d.text())) d.text(tekst);
    }

    private void weg(UUID id) {
        TextDisplay d = displays.remove(id);
        doelen.remove(id);
        if (d != null && d.isValid()) d.remove();
    }

    private Component tekst(Inventory inv) {
        Map<Material, Integer> tel = new LinkedHashMap<>();
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir()) continue;
            tel.merge(it.getType(), it.getAmount(), Integer::sum);
        }
        if (tel.isEmpty()) return Component.text("leeg", NamedTextColor.GRAY);
        List<Map.Entry<Material, Integer>> lijst = new ArrayList<>(tel.entrySet());
        lijst.sort((a, b) -> b.getValue() - a.getValue());
        int n = Math.max(1, getConfig().getInt("hologram-regels", 3));
        Component c = Component.empty();
        for (int i = 0; i < Math.min(n, lijst.size()); i++) {
            Map.Entry<Material, Integer> e = lijst.get(i);
            if (i > 0) c = c.append(Component.newline());
            c = c.append(Component.text(e.getValue() + "× ", NamedTextColor.YELLOW))
                    .append(Component.translatable(e.getKey().translationKey(), NamedTextColor.WHITE));
        }
        if (lijst.size() > n) {
            c = c.append(Component.newline()).append(Component.text("+" + (lijst.size() - n) + " andere soort" + (lijst.size() - n == 1 ? "" : "en"), NamedTextColor.GRAY));
        }
        return c;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        weg(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWereld(PlayerChangedWorldEvent e) {
        weg(e.getPlayer().getUniqueId());
    }

    // ── /unload ─────────────────────────────────────────────────────────────────────────────

    private record Doel(Inventory inv, Location midden) {}

    private List<Doel> kistenRond(Location l, int straal) {
        List<Doel> uit = new ArrayList<>();
        Set<Location> al = new HashSet<>();
        World w = l.getWorld();
        int cx = l.getBlockX() >> 4, cz = l.getBlockZ() >> 4, r = (straal >> 4) + 1;
        double max = (double) straal * straal;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!w.isChunkLoaded(x, z)) continue;
                Chunk ch = w.getChunkAt(x, z);
                for (BlockState st : ch.getTileEntities(b -> isKist(b.getType()), false)) {
                    Location m = st.getLocation().add(0.5, 0.5, 0.5);
                    if (m.distanceSquared(l) > max) continue;
                    Inventory inv = inhoud(st.getBlock());
                    if (inv == null) continue;
                    Location sleutel = inv.getLocation() != null ? inv.getLocation().toBlockLocation() : st.getLocation();
                    if (!al.add(sleutel)) continue;                       // dubbele kist maar één keer
                    uit.add(new Doel(inv, bovenkant(st.getBlock(), inv).subtract(0, 0.65, 0)));
                }
            }
        }
        uit.sort((a, b) -> Double.compare(a.midden().distanceSquared(l), b.midden().distanceSquared(l)));
        return uit;
    }

    private static boolean heeft(Inventory inv, ItemStack it) {
        for (ItemStack s : inv.getContents()) if (s != null && s.isSimilar(it)) return true;
        return false;
    }

    private void unload(Player p) {
        int straal = Math.max(1, getConfig().getInt("unload-straal", 10));
        List<Doel> kisten = kistenRond(p.getLocation(), straal);
        PlayerInventory pi = p.getInventory();
        Map<Doel, Integer> gekregen = new LinkedHashMap<>();
        int totaal = 0;
        for (int slot = 9; slot <= 35; slot++) {
            ItemStack it = pi.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            for (Doel d : kisten) {
                if (!heeft(d.inv(), it)) continue;
                int voor = it.getAmount();
                Map<Integer, ItemStack> rest = d.inv().addItem(it.clone());
                int over = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
                int erin = voor - over;
                if (erin <= 0) continue;
                gekregen.merge(d, erin, Integer::sum);
                totaal += erin;
                if (over <= 0) {
                    it = null;
                    break;
                }
                it.setAmount(over);
            }
            pi.setItem(slot, it);
        }
        if (totaal == 0) {
            p.sendActionBar(Component.text(kisten.isEmpty() ? "Geen kisten binnen " + straal + " blokken" : "Geen kist in de buurt heeft die items al (of ze zijn vol)", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        p.sendActionBar(Component.text(totaal + " items in " + gekregen.size() + (gekregen.size() == 1 ? " kist" : " kisten") + " gelegd", NamedTextColor.GREEN));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        lijnen(p, new ArrayList<>(gekregen.keySet()));
    }

    /** Groene deeltjeslijnen van de speler naar elke kist die iets kreeg, ~2 s lang, alleen voor die speler. */
    private void lijnen(Player p, List<Doel> doelen) {
        Particle.DustOptions groen = new Particle.DustOptions(Color.fromRGB(80, 255, 80), 0.8f);
        final int[] keer = {0};
        Bukkit.getScheduler().runTaskTimer(this, taak -> {
            if (!p.isOnline() || keer[0]++ >= 8) {
                taak.cancel();
                return;
            }
            Location van = p.getLocation().add(0, 1.0, 0);
            for (Doel d : doelen) {
                if (d.midden().getWorld() != van.getWorld()) continue;
                Vector stap = d.midden().toVector().subtract(van.toVector());
                double len = stap.length();
                int n = (int) Math.min(60, Math.max(2, len * 3));
                stap.multiply(1.0 / n);
                Location punt = van.clone();
                for (int i = 0; i <= n; i++) {
                    p.spawnParticle(Particle.DUST, punt, 1, 0, 0, 0, 0, groen);
                    punt.add(stap);
                }
                p.spawnParticle(Particle.HAPPY_VILLAGER, d.midden(), 4, 0.3, 0.3, 0.3, 0);
            }
        }, 0L, 5L);
    }

    // ── tips ────────────────────────────────────────────────────────────────────────────────

    private void tipTick() {
        if (++tipTeller < Math.max(1, getConfig().getInt("tip-minuten", 5))) return;
        tipTeller = 0;
        stuurTip();
    }

    private boolean stuurTip() {
        if (!vlag("tips") || Bukkit.getOnlinePlayers().isEmpty()) return false;
        List<String> tips = getConfig().getStringList("tips-lijst");
        if (tips.isEmpty()) return false;
        String tip = tips.get(tipIndex++ % tips.size());
        Component msg = Component.text("💡 Tip: ", NamedTextColor.GOLD).append(Component.text(tip, NamedTextColor.YELLOW));
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        return true;
    }

    // ── commando's ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("unload")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Alleen in de game.");
                return true;
            }
            if (!vlag("unload")) {
                p.sendMessage(Component.text("/unload staat nu uit.", NamedTextColor.RED));
                return true;
            }
            unload(p);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("tip")) {
            sender.sendMessage(stuurTip() ? "tip gestuurd" : "geen tip (uit, of niemand online)");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("zelftest")) {
            sender.sendMessage("kistzicht ok: hologram=" + (vlag("aan") ? "aan" : "uit") + " unload=" + (vlag("unload") ? "aan" : "uit")
                    + " tips=" + (vlag("tips") ? "aan" : "uit") + " displays=" + displays.size() + " tips-lijst=" + getConfig().getStringList("tips-lijst").size());
            return true;
        }
        sender.sendMessage("kistzicht tip | zelftest");
        return true;
    }
}
