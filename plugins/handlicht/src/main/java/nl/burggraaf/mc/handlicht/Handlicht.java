package nl.burggraaf.mc.handlicht;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handlicht: houdt een speler een lichtgevend item vast (main- of off-hand), dan staat er een onzichtbaar
 * minecraft:light-blok op ooghoogte (of anders op voethoogte) dat meebeweegt. Alleen in lucht, cave_air of
 * stilstaand water (dan waterlogged); bestaande blokken worden nooit overschreven.
 * Elk geplaatst blok staat in lichten.txt; bij start en stop wordt alles opgeruimd, ook na een crash.
 */
public final class Handlicht extends JavaPlugin implements Listener {

    private record Plek(String wereld, int x, int y, int z, boolean water) {
        String regel() { return wereld + " " + x + " " + y + " " + z + " " + (water ? 1 : 0); }
    }

    private final Map<Material, Integer> licht = new EnumMap<>(Material.class);
    private final Map<UUID, Plek> geplaatst = new HashMap<>();
    private File bestand;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Object[][] lijst = {
                {"TORCH", 14}, {"WALL_TORCH", 14}, {"SOUL_TORCH", 10}, {"COPPER_TORCH", 14}, {"REDSTONE_TORCH", 7},
                {"LANTERN", 15}, {"SOUL_LANTERN", 10}, {"COPPER_LANTERN", 15}, {"GLOWSTONE", 15}, {"SEA_LANTERN", 15},
                {"SHROOMLIGHT", 15}, {"GLOW_BERRIES", 14}, {"LAVA_BUCKET", 15}, {"END_ROD", 14}, {"JACK_O_LANTERN", 15},
                {"BEACON", 15}, {"CAMPFIRE", 15}, {"SOUL_CAMPFIRE", 10}, {"OCHRE_FROGLIGHT", 15}, {"VERDANT_FROGLIGHT", 15},
                {"PEARLESCENT_FROGLIGHT", 15}, {"GLOW_LICHEN", 7}, {"CRYING_OBSIDIAN", 10}, {"MAGMA_BLOCK", 3},
                {"LIGHT", 15}, {"CONDUIT", 15}, {"SEA_PICKLE", 6}, {"AMETHYST_CLUSTER", 5}, {"BLAZE_ROD", 10},
                {"GLOW_INK_SAC", 6}, {"ENDER_CHEST", 7}, {"RESPAWN_ANCHOR", 15}, {"REDSTONE_LAMP", 15},
                {"COPPER_BULB", 15}, {"LAVA", 15}, {"NETHER_STAR", 10}, {"FIRE_CHARGE", 10}, {"GLOW_ITEM_FRAME", 6}};
        for (Object[] r : lijst) {
            Material m = Material.matchMaterial((String) r[0]);
            if (m != null) licht.put(m, (Integer) r[1]);
        }
        bestand = new File(getDataFolder(), "lichten.txt");
        ruimBestandOp();
        getServer().getPluginManager().registerEvents(this, this);
        long iv = Math.max(1, getConfig().getInt("interval", 3));
        Bukkit.getScheduler().runTaskTimer(this, this::tik, iv, iv);
        getLogger().info("handlicht " + (aan() ? "aan" : "uit") + ", " + licht.size() + " lichtgevende items");
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(geplaatst.keySet())) weg(id);
        bewaar();
    }

    private boolean aan() {
        return getConfig().getBoolean("aan", true);
    }

    private int niveau(ItemStack it) {
        return it == null ? 0 : licht.getOrDefault(it.getType(), 0);
    }

    private void tik() {
        boolean aan = aan();
        for (Player p : Bukkit.getOnlinePlayers()) {
            int n = 0;
            if (aan && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                n = Math.max(niveau(p.getInventory().getItemInMainHand()), niveau(p.getInventory().getItemInOffHand()));
            }
            if (n <= 0) {
                weg(p.getUniqueId());
                continue;
            }
            Plek oud = geplaatst.get(p.getUniqueId());
            Block doel = kies(p, oud);
            if (doel == null) {
                weg(p.getUniqueId());
                continue;
            }
            if (oud != null && oud.wereld().equals(doel.getWorld().getName())
                    && oud.x() == doel.getX() && oud.y() == doel.getY() && oud.z() == doel.getZ()) {
                if (doel.getBlockData() instanceof Light l && l.getLevel() != n) {
                    l.setLevel(n);
                    doel.setBlockData(l, false);
                }
                continue;
            }
            weg(p.getUniqueId());
            boolean water = doel.getType() == Material.WATER;
            Plek nieuw = new Plek(doel.getWorld().getName(), doel.getX(), doel.getY(), doel.getZ(), water);
            geplaatst.put(p.getUniqueId(), nieuw);
            bewaar();                                     // eerst vastleggen, dan plaatsen: bij een crash ruimen we het op
            Light l = (Light) Material.LIGHT.createBlockData();
            l.setLevel(n);
            l.setWaterlogged(water);
            doel.setBlockData(l, false);
        }
    }

    /** Ooghoogte, anders voethoogte; het eigen lichtblok telt als vrij. */
    private Block kies(Player p, Plek oud) {
        Location oog = p.getEyeLocation();
        for (Block b : new Block[]{oog.getBlock(), p.getLocation().getBlock()}) {
            if (oud != null && b.getType() == Material.LIGHT && oud.wereld().equals(b.getWorld().getName())
                    && oud.x() == b.getX() && oud.y() == b.getY() && oud.z() == b.getZ()) return b;
            if (vrij(b)) return b;
        }
        return null;
    }

    private static boolean vrij(Block b) {
        Material t = b.getType();
        if (t == Material.AIR || t == Material.CAVE_AIR) return true;
        return t == Material.WATER && b.getBlockData() instanceof Levelled lv && lv.getLevel() == 0;
    }

    private void weg(UUID id) {
        Plek p = geplaatst.remove(id);
        if (p == null) return;
        haalWeg(p);
        bewaar();
    }

    /** Alleen weghalen als er nog steeds een light-blok staat (iemand kan er iets anders neergezet hebben). */
    private static void haalWeg(Plek p) {
        World w = Bukkit.getWorld(p.wereld());
        if (w == null) return;
        Block b = w.getBlockAt(p.x(), p.y(), p.z());
        if (b.getType() != Material.LIGHT) return;
        BlockData d = b.getBlockData();
        boolean water = d instanceof Light l && l.isWaterlogged();
        b.setType(water ? Material.WATER : Material.AIR, false);
    }

    private void bewaar() {
        List<String> regels = new ArrayList<>();
        for (Plek p : geplaatst.values()) regels.add(p.regel());
        try {
            getDataFolder().mkdirs();
            Files.write(bestand.toPath(), regels);
        } catch (IOException e) {
            getLogger().warning("lichten.txt: " + e.getMessage());
        }
    }

    private void ruimBestandOp() {
        if (!bestand.exists()) return;
        int n = 0;
        try {
            for (String r : Files.readAllLines(bestand.toPath())) {
                String[] d = r.trim().split(" ");
                if (d.length < 4) continue;
                haalWeg(new Plek(d[0], Integer.parseInt(d[1]), Integer.parseInt(d[2]), Integer.parseInt(d[3]), d.length > 4 && d[4].equals("1")));
                n++;
            }
            Files.write(bestand.toPath(), List.of());
        } catch (IOException | RuntimeException e) {
            getLogger().warning("lichten.txt opruimen: " + e.getMessage());
        }
        if (n > 0) getLogger().info(n + " achtergebleven lichtblok(ken) opgeruimd");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { weg(e.getPlayer().getUniqueId()); }

    @EventHandler
    public void onDood(PlayerDeathEvent e) { weg(e.getEntity().getUniqueId()); }

    @EventHandler
    public void onWereld(PlayerChangedWorldEvent e) { weg(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) { weg(e.getPlayer().getUniqueId()); }
}
