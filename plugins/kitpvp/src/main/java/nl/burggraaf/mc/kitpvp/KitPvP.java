package nl.burggraaf.mc.kitpvp;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * KitPvP: een ronde arena (colosseum met ruïnes en een middenplateau) en een wachtplatform erboven met kit-standaards.
 * Kies een kit (klik op een standaard, de ster in je hotbar of /kit) en je springt de arena in met even bescherming.
 * Geen drops, geen honger, direct respawnen op het platform. Kills, doden en reeksen in stats.yml, top 10 op een bord,
 * eigen statistieken in de sidebar.
 */
public final class KitPvP extends JavaPlugin implements Listener {

    private static final int BOUW_VERSIE = 2;   // 2: oefenarena met bots erbij
    private static final int R = 30;           // arena-straal (binnenkant muur)
    private static final int BZ = 110, RB = 18; // oefenarena (bots): midden (0, BZ), straal
    private static final long GEVECHT_MS = 10_000L;

    private World w;
    private int g;                             // grondhoogte (vloer van de arena)
    private Location wacht;                    // wachtplatform
    private final List<Location> spawns = new ArrayList<>();
    private final List<Location> botSpawns = new ArrayList<>();
    private final Map<UUID, Boolean> kiestBots = new HashMap<>();      // voorkeur in het menu: true = oefenarena
    private final java.util.Set<UUID> inBotArena = new java.util.HashSet<>();
    private final Map<UUID, Long> gevechtTot = new HashMap<>();
    private final Map<UUID, Long> gevechtGemeld = new HashMap<>();
    private long botsLeegSinds = 0;
    private final Random rnd = new Random();
    private NamespacedKey itemKey, kitKey;
    private boolean actief = true;

    private record Kit(String id, String naam, Material icoon, TextColor kleur, String uitleg) {}

    private static final Kit[] KITS = {
            new Kit("krijger", "Krijger", Material.IRON_SWORD, NamedTextColor.WHITE, "IJzeren harnas, zwaard en schild"),
            new Kit("boog", "Boogschutter", Material.BOW, NamedTextColor.GREEN, "Boog (oneindig), maliënkolder, stenen zwaard"),
            new Kit("tank", "Tank", Material.DIAMOND_CHESTPLATE, NamedTextColor.AQUA, "Diamanten borst, bijl en schild, wat trager"),
            new Kit("ninja", "Ninja", Material.FEATHER, NamedTextColor.DARK_GRAY, "Snel en hoog springen, scherp gouden zwaard"),
            new Kit("speer", "Speer", Material.TRIDENT, NamedTextColor.DARK_AQUA, "Drietand die terugkomt, ijzer en maliën"),
            new Kit("heler", "Heler", Material.SPLASH_POTION, NamedTextColor.LIGHT_PURPLE, "Genezende drankjes en regeneratie"),
            new Kit("hamer", "Hamer", Material.MACE, NamedTextColor.GOLD, "Strijdhamer met windladingen: spring en sla!"),
    };

    // spelers
    private final Map<UUID, String> kitVan = new HashMap<>();          // in de arena met deze kit
    private final Map<UUID, Long> beschermdTot = new HashMap<>();
    private final Map<UUID, UUID> laatsteAanvaller = new HashMap<>();
    private final Map<UUID, Long> laatsteAanvalTijd = new HashMap<>();
    private final Map<UUID, Integer> reeks = new HashMap<>();
    private final Map<UUID, Scoreboard> borden = new HashMap<>();

    private YamlConfiguration stats;
    private File statsBestand;
    private TextDisplay ranglijst;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("actief", true) || Bukkit.getMotd().contains("STAGING")) {
            actief = false;
            getLogger().info("niet actief op deze server");
            return;
        }
        itemKey = new NamespacedKey(this, "item");
        kitKey = new NamespacedKey(this, "kit");
        statsBestand = new File(getDataFolder(), "stats.yml");
        stats = YamlConfiguration.loadConfiguration(statsBestand);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "netwerk:main");
        Bukkit.getScheduler().runTask(this, () -> {
            w = Bukkit.getWorlds().get(0);
            for (int cx = -4; cx <= 3; cx++)             // arena blijft geladen: kit-standaards en ranglijst verdwijnen anders
                for (int cz = -4; cz <= 8; cz++) w.addPluginChunkTicket(cx, cz, this);   // ook de oefenarena
            bouw(false);
            for (Player p : Bukkit.getOnlinePlayers()) naarWacht(p, false);
        });
        Bukkit.getScheduler().runTaskTimer(this, this::elkeSeconde, 40L, 20L);
    }

    @Override
    public void onDisable() {
        bewaarStats();
        if (w != null) {
            for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains("kitpvp") || isBot(e)) e.remove();
        }
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    private boolean plaats;

    private void zet(int x, int y, int z, Material m) {
        if (plaats) w.getBlockAt(x, y, z).setType(m, false);
    }

    private void zet(int x, int y, int z, BlockData d) {
        if (plaats) w.getBlockAt(x, y, z).setBlockData(d, false);
    }

    private void vul(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        if (!plaats) return;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() != m) b.setType(m, false);
                }
    }

    private BlockData trap(Material m, BlockFace f) {
        Stairs s = (Stairs) m.createBlockData();
        s.setFacing(f);
        return s;
    }

    private BlockData blad(Material m) {
        Leaves l = (Leaves) m.createBlockData();
        l.setPersistent(true);
        return l;
    }

    private static long hash(int x, int z) {
        long h = x * 73428767L ^ z * 912931L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        return Math.abs(h ^ (h >>> 15));
    }

    void bouw(boolean forceer) {
        File marker = new File(getDataFolder(), "gebouwd.txt");
        String eerder = null;
        try {
            if (marker.exists()) eerder = java.nio.file.Files.readString(marker.toPath()).trim();
        } catch (IOException ignored) {
        }
        if (eerder != null && eerder.contains(":")) {
            g = Integer.parseInt(eerder.split(":")[1]);
        } else {
            g = w.getHighestBlockYAt(0, 0);
        }
        String versie = BOUW_VERSIE + ":" + g;
        plaats = forceer || !versie.equals(eerder);
        long t0 = System.currentTimeMillis();
        int wy = g + 9;                                   // vloer van het wachtplatform
        if (plaats) {
            vul(-50, g + 1, -60, 50, g + 30, 50, Material.AIR);
            vul(-50, g, -60, 50, g, 50, Material.GRASS_BLOCK);
        }
        // arena-vloer
        Material[] vloer = {Material.STONE_BRICKS, Material.COBBLESTONE, Material.MOSSY_STONE_BRICKS, Material.ANDESITE,
                Material.GRAVEL, Material.COARSE_DIRT, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.MOSS_BLOCK, Material.PACKED_MUD};
        for (int x = -R - 4; x <= R + 4; x++)
            for (int z = -R - 4; z <= R + 4; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > R + 3.5) continue;
                if (r <= R + 0.5) {
                    zet(x, g, z, vloer[(int) (hash(x / 3, z / 3) % vloer.length)]);
                    zet(x, g - 1, z, Material.STONE);
                } else {                                   // muur: 7 hoog, met kantelen
                    vul(x, g - 1, z, x, g + 6, z, (hash(x, z) % 5 == 0) ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                    if (r <= R + 1.5) zet(x, g + 3, z, (hash(x, z) % 9 == 0) ? Material.SEA_LANTERN : Material.CHISELED_STONE_BRICKS);
                    if (r > R + 2.5 && (Math.floorMod((int) Math.round(Math.atan2(z, x) * 30), 2) == 0)) zet(x, g + 7, z, Material.STONE_BRICK_WALL);
                }
            }
        // pilaren met lantaarns
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 / 12 * i;
            int x = (int) Math.round(Math.cos(a) * (R + 2)), z = (int) Math.round(Math.sin(a) * (R + 2));
            vul(x, g + 7, z, x, g + 9, z, Material.POLISHED_ANDESITE);
            zet(x, g + 10, z, Material.LANTERN);
        }
        // middenplateau (straal 5, 2 hoog) met trappen
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 5.4) continue;
                vul(x, g + 1, z, x, g + 1, z, Material.STONE_BRICKS);
                if (r <= 4.4) zet(x, g + 2, z, (x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE);
                else {
                    Slab s = (Slab) Material.STONE_BRICK_SLAB.createBlockData();
                    zet(x, g + 2, z, s);
                }
            }
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            for (int i = -1; i <= 1; i++) {
                int dx = f.getModX(), dz = f.getModZ();
                int px = dx * 6 + (dz != 0 ? i : 0), pz = dz * 6 + (dx != 0 ? i : 0);
                zet(px, g + 1, pz, trap(Material.STONE_BRICK_STAIRS, f.getOppositeFace()));
                int qx = dx * 5 + (dz != 0 ? i : 0), qz = dz * 5 + (dx != 0 ? i : 0);
                zet(qx, g + 2, qz, trap(Material.STONE_BRICK_STAIRS, f.getOppositeFace()));
            }
        }
        zet(0, g + 3, 0, Material.LODESTONE);
        // ruïnes op straal 18 (8 stuks), struiken en hooi
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 / 8 * i + Math.PI / 8;
            int cx = (int) Math.round(Math.cos(a) * 18), cz = (int) Math.round(Math.sin(a) * 18);
            boolean langsX = i % 2 == 0;
            for (int k = -3; k <= 3; k++) {
                int x = cx + (langsX ? k : 0), z = cz + (langsX ? 0 : k);
                int h = 1 + (int) (hash(x, z) % 3);
                vul(x, g + 1, z, x, g + h, z, hash(x + 1, z) % 3 == 0 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
            }
            int ex = cx + (langsX ? 3 : 1), ez = cz + (langsX ? 1 : 3);
            vul(ex, g + 1, ez, ex, g + 2, ez, Material.MOSSY_STONE_BRICKS);
            zet(cx, g + 4, cz, Material.MOSSY_STONE_BRICK_SLAB);
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + Math.PI / 4;
            int cx = (int) Math.round(Math.cos(a) * 11), cz = (int) Math.round(Math.sin(a) * 11);
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++)
                    for (int dy = 1; dy <= 2; dy++) zet(cx + dx, g + dy, cz + dz, blad(dy == 2 && dx == 1 ? Material.AZALEA_LEAVES : Material.OAK_LEAVES));
            int hx = (int) Math.round(Math.cos(a + Math.PI / 4) * 25), hz = (int) Math.round(Math.sin(a + Math.PI / 4) * 25);
            vul(hx, g + 1, hz, hx + 1, g + 1, hz, Material.HAY_BLOCK);
            zet(hx, g + 2, hz, Material.HAY_BLOCK);
        }
        // spawnpunten op straal 24, kijkend naar het midden
        spawns.clear();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 / 8 * i;
            double x = Math.cos(a) * 24 + 0.5, z = Math.sin(a) * 24 + 0.5;
            Location l = new Location(w, x, g + 1, z);
            l.setDirection(new org.bukkit.util.Vector(-x, 0, -z));
            vul(l.getBlockX(), g + 1, l.getBlockZ(), l.getBlockX(), g + 3, l.getBlockZ(), Material.AIR);
            spawns.add(l);
        }
        // wachtplatform boven de noordmuur (z −48..−36), kijkt uit over de arena
        int z1 = -48, z2 = -37, x1 = -12, x2 = 12;
        vul(x1, wy - 1, z1, x2, wy - 1, z2, Material.POLISHED_DEEPSLATE);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) zet(x, wy, z, (Math.abs(x) + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_QUARTZ);
        for (int x = x1; x <= x2; x++) {
            zet(x, wy + 1, z1, Material.POLISHED_BLACKSTONE_WALL);
            zet(x, wy + 1, z2, Material.GLASS_PANE);
            zet(x, wy + 2, z2, Material.GLASS_PANE);
        }
        for (int z = z1; z <= z2; z++) {
            zet(x1, wy + 1, z, Material.POLISHED_BLACKSTONE_WALL);
            zet(x2, wy + 1, z, Material.POLISHED_BLACKSTONE_WALL);
        }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            vul(c[0], wy - 8, c[1], c[0], wy + 2, c[1], Material.POLISHED_DEEPSLATE);
            zet(c[0], wy + 3, c[1], Material.LANTERN);
        }
        for (int x = -9; x <= 9; x += 3) zet(x, wy, z1 + 2, Material.CHISELED_QUARTZ_BLOCK);   // sokkels voor de kits
        // een rode loper naar het midden
        for (int z = z1 + 3; z <= z2; z++) zet(0, wy, z, Material.RED_CARPET);
        wacht = new Location(w, 0.5, wy + 1, -40.5, 180f, 0f);
        w.setSpawnLocation(wacht);
        // standaards met de kits (niet opgeslagen: elke start nieuw)
        for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains("kitpvp")) e.remove();
        for (int i = 0; i < KITS.length; i++) {
            Kit k = KITS[i];
            Location l = new Location(w, -9 + 3 * i + 0.5, wy + 1, z1 + 2 + 0.5, 0f, 0f);
            w.spawn(l, ArmorStand.class, a -> {
                a.setPersistent(false);
                a.setInvulnerable(true);
                a.setGravity(false);
                a.setArms(true);
                a.setBasePlate(false);
                a.addScoreboardTag("kitpvp");
                a.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, k.id());
                ItemStack[] uit = uitrusting(k.id());
                a.getEquipment().setHelmet(uit[0]);
                a.getEquipment().setChestplate(uit[1]);
                a.getEquipment().setLeggings(uit[2]);
                a.getEquipment().setBoots(uit[3]);
                a.getEquipment().setItemInMainHand(new ItemStack(k.icoon()));
            });
            label(l.clone().add(0, 2.5, 0), Component.text(k.naam(), k.kleur(), TextDecoration.BOLD).append(Component.newline())
                    .append(Component.text(k.uitleg(), NamedTextColor.GRAY)).append(Component.newline())
                    .append(Component.text("▶ klik om te kiezen", NamedTextColor.YELLOW)), 0.8f);
        }
        bouwOefenarena();                                  // na het opruimen van de labels hierboven
        label(new Location(w, 0.5, wy + 4.2, -44.5), Component.text("⚔ KITPVP ⚔", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("Kies een kit en spring de arena in!", NamedTextColor.WHITE)), 1.6f);
        label(new Location(w, 10.5, wy + 2.2, -40.5), Component.text("Hoe werkt het?", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("• Kies een kit (klik of de ster)\n• In het menu: PvP of oefenarena (bots)\n• Kill = vol leven + gouden appel\n• 10 s na een treffer kun je niet weg\n• /lobby · /survival om te gaan", NamedTextColor.GRAY)), 0.9f);
        Location rl = new Location(w, -10.5, wy + 2.2, -40.5);
        ranglijst = w.spawn(rl, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
            d.addScoreboardTag("kitpvp");
        });
        werkRanglijstBij();
        if (plaats) {
            try {
                getDataFolder().mkdirs();
                java.nio.file.Files.writeString(marker.toPath(), versie);
            } catch (IOException e) {
                getLogger().warning("gebouwd.txt: " + e.getMessage());
            }
            getLogger().info("arena gebouwd in " + (System.currentTimeMillis() - t0) + " ms");
        }
    }

    /** Oefenarena (bots): een kleinere ring ten zuiden van het colosseum, met lage muur, lantaarns en wat dekking. */
    private void bouwOefenarena() {
        if (plaats) {
            vul(-RB - 6, g + 1, BZ - RB - 6, RB + 6, g + 20, BZ + RB + 6, Material.AIR);
            vul(-RB - 6, g, BZ - RB - 6, RB + 6, g, BZ + RB + 6, Material.GRASS_BLOCK);
        }
        for (int x = -RB - 3; x <= RB + 3; x++)
            for (int z = -RB - 3; z <= RB + 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > RB + 2.5) continue;
                if (r <= RB + 0.5) {
                    zet(x, g, BZ + z, hash(x / 2, z / 2) % 4 == 0 ? Material.COARSE_DIRT : Material.SMOOTH_SANDSTONE);
                    zet(x, g - 1, BZ + z, Material.STONE);
                } else {
                    vul(x, g - 1, BZ + z, x, g + 5, BZ + z, hash(x, z) % 6 == 0 ? Material.CUT_SANDSTONE : Material.SANDSTONE);
                    if (r > RB + 1.5 && hash(x, z) % 7 == 0) zet(x, g + 6, BZ + z, Material.LANTERN);
                }
            }
        for (int i = 0; i < 6; i++) {                     // dekking: lage muurtjes
            double a = Math.PI * 2 / 6 * i;
            int cx = (int) Math.round(Math.cos(a) * 9), cz = (int) Math.round(Math.sin(a) * 9);
            for (int k = -1; k <= 1; k++) vul(cx + (i % 2 == 0 ? k : 0), g + 1, BZ + cz + (i % 2 == 0 ? 0 : k), cx + (i % 2 == 0 ? k : 0), g + 2, BZ + cz + (i % 2 == 0 ? 0 : k), Material.SANDSTONE_WALL);
        }
        zet(0, g + 1, BZ, Material.CAMPFIRE);
        botSpawns.clear();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 / 6 * i + Math.PI / 6;
            double x = Math.cos(a) * 14 + 0.5, z = Math.sin(a) * 14 + 0.5;
            Location l = new Location(w, x, g + 1, BZ + z);
            l.setDirection(new org.bukkit.util.Vector(-x, 0, -z));
            vul(l.getBlockX(), g + 1, l.getBlockZ(), l.getBlockX(), g + 3, l.getBlockZ(), Material.AIR);
            botSpawns.add(l);
        }
        label(new Location(w, 0.5, g + 8, BZ - RB - 2.5), Component.text("Oefenarena", NamedTextColor.GOLD)
                .append(Component.newline()).append(Component.text("vecht tegen bots", NamedTextColor.GRAY)), 1.2f);
    }

    // ── bots (oefenarena) ───────────────────────────────────────────────────────────────────

    private static final String BOT = "kitpvp-bot";

    private boolean isBot(Entity e) {
        return e != null && e.getScoreboardTags().contains(BOT);
    }

    private List<org.bukkit.entity.LivingEntity> bots() {
        List<org.bukkit.entity.LivingEntity> l = new ArrayList<>();
        if (w == null) return l;
        for (org.bukkit.entity.LivingEntity e : w.getLivingEntities()) if (isBot(e) && !e.isDead()) l.add(e);
        return l;
    }

    private void spawnBot() {
        if (botSpawns.isEmpty()) return;
        Location l = botSpawns.stream().max(Comparator.comparingDouble(s -> {
            double min = 1e9;
            for (UUID id : inBotArena) {
                Player q = Bukkit.getPlayer(id);
                if (q != null) min = Math.min(min, q.getLocation().distanceSquared(s));
            }
            return min + rnd.nextDouble() * 30;
        })).get();
        int soort = rnd.nextInt(3);
        Class<? extends org.bukkit.entity.Mob> klasse = soort == 0 ? org.bukkit.entity.Skeleton.class
                : soort == 1 ? org.bukkit.entity.Husk.class : org.bukkit.entity.Zombie.class;
        w.spawn(l, klasse, m -> {
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            m.addScoreboardTag(BOT);
            m.customName(Component.text("Bot", NamedTextColor.GRAY));
            m.setCustomNameVisible(false);
            if (m instanceof org.bukkit.entity.Zombie z) {
                z.setShouldBurnInDay(false);
                z.setAdult();
            }
            if (m instanceof org.bukkit.entity.AbstractSkeleton sk) sk.setShouldBurnInDay(false);
            var eq = m.getEquipment();
            boolean boog = m instanceof org.bukkit.entity.Skeleton;
            eq.setHelmet(new ItemStack(boog ? Material.CHAINMAIL_HELMET : Material.IRON_HELMET));
            eq.setChestplate(new ItemStack(boog ? Material.LEATHER_CHESTPLATE : Material.CHAINMAIL_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
            eq.setBoots(new ItemStack(Material.IRON_BOOTS));
            eq.setItemInMainHand(new ItemStack(boog ? Material.BOW : rnd.nextBoolean() ? Material.IRON_SWORD : Material.STONE_AXE));
            for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND})
                eq.setDropChance(sl, 0f);
        });
        w.spawnParticle(Particle.CLOUD, l.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
    }

    /** Elke seconde: genoeg bots als er iemand in de oefenarena is, weg als hij al 30 s leeg is; bots blijven binnen. */
    private void botsBijhouden() {
        inBotArena.removeIf(id -> Bukkit.getPlayer(id) == null || !kitVan.containsKey(id));
        List<org.bukkit.entity.LivingEntity> nu = bots();
        long t = System.currentTimeMillis();
        if (inBotArena.isEmpty()) {
            if (botsLeegSinds == 0) botsLeegSinds = t;
            if (t - botsLeegSinds > 30_000L) nu.forEach(Entity::remove);
            return;
        }
        botsLeegSinds = 0;
        int max = Math.max(1, getConfig().getInt("bots-max", 6));
        int gewenst = Math.min(max, 1 + 2 * inBotArena.size());
        if (nu.size() < gewenst && seconden % 2 == 0) spawnBot();
        for (org.bukkit.entity.LivingEntity b : nu) {
            Location l = b.getLocation();
            double dx = l.getX(), dz = l.getZ() - BZ;
            if (dx * dx + dz * dz > (RB - 1) * (RB - 1) || l.getY() < g - 2) b.teleport(botSpawns.get(rnd.nextInt(botSpawns.size())));
            if (b instanceof org.bukkit.entity.Mob m && (m.getTarget() == null || !(m.getTarget() instanceof Player pt) || !inBotArena.contains(pt.getUniqueId()))) {
                Player dichtst = null;
                double best = 1e9;
                for (UUID id : inBotArena) {
                    Player q = Bukkit.getPlayer(id);
                    if (q == null || beschermdTot.getOrDefault(id, 0L) > t) continue;
                    double d = q.getLocation().distanceSquared(l);
                    if (d < best) { best = d; dichtst = q; }
                }
                m.setTarget(dichtst);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBotDoel(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (!actief || !isBot(e.getEntity())) return;
        if (e.getTarget() == null) return;
        if (!(e.getTarget() instanceof Player p) || !inBotArena.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onBotDood(org.bukkit.event.entity.EntityDeathEvent e) {
        if (!actief || !isBot(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player k = e.getEntity().getKiller();
        if (k == null || !kitVan.containsKey(k.getUniqueId())) return;
        var max = k.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) k.setHealth(Math.min(max.getValue(), k.getHealth() + 8));
        tel(k.getUniqueId(), k.getName(), "bots");
        k.sendActionBar(Component.text("Bot verslagen · " + stats.getInt(k.getUniqueId() + ".bots", 0), NamedTextColor.GRAY));
        k.playSound(k.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }

    // ── gevecht-tag: 10 s na een treffer niet weg (ook niet via de proxy) ────────────────────

    private boolean inGevecht(UUID id) {
        return gevechtTot.getOrDefault(id, 0L) > System.currentTimeMillis();
    }

    private void tag(Player p) {
        long nu = System.currentTimeMillis();
        gevechtTot.put(p.getUniqueId(), nu + GEVECHT_MS);
        if (nu - gevechtGemeld.getOrDefault(p.getUniqueId(), 0L) > 1500L) {   // proxy hooguit ~1x per 1,5 s
            gevechtGemeld.put(p.getUniqueId(), nu);
            meldProxy(p, "gevecht:" + GEVECHT_MS);
        }
    }

    private void untag(Player p) {
        if (gevechtTot.remove(p.getUniqueId()) != null) meldProxy(p, "gevecht:0");
        gevechtGemeld.remove(p.getUniqueId());
    }

    private void meldProxy(Player p, String wat) {
        try {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(buf);
            out.writeUTF("actie");
            out.writeUTF(p.getUniqueId().toString());
            out.writeUTF(wat);
            p.sendPluginMessage(this, "netwerk:main", buf.toByteArray());
        } catch (IOException | RuntimeException e) {
            getLogger().fine("netwerk: " + e.getMessage());
        }
    }

    private boolean blokkeerAlsGevecht(Player p) {
        if (!inGevecht(p.getUniqueId())) return false;
        long over = gevechtTot.get(p.getUniqueId()) - System.currentTimeMillis();
        p.sendActionBar(Component.text("⚔ In gevecht · nog " + ((over + 999) / 1000) + " s", NamedTextColor.RED));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTreffer(EntityDamageByEntityEvent e) {
        if (!actief) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        if (e.getEntity() instanceof Player p && kitVan.containsKey(p.getUniqueId()) && (d instanceof Player || isBot(d))) tag(p);
        if (d instanceof Player a && kitVan.containsKey(a.getUniqueId()) && (e.getEntity() instanceof Player || isBot(e.getEntity()))) tag(a);
    }

    private void label(Location l, Component tekst, float schaal) {
        w.spawn(l, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(tekst);
            d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(schaal, schaal, schaal), new org.joml.AxisAngle4f()));
            d.addScoreboardTag("kitpvp");
        });
    }

    // ── kits ────────────────────────────────────────────────────────────────────────────────

    private static ItemStack ding(Material m, int n) {
        return new ItemStack(m, n);
    }

    private static ItemStack betoverd(Material m, Object... ench) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        for (int i = 0; i + 1 < ench.length; i += 2) meta.addEnchant((Enchantment) ench[i], (Integer) ench[i + 1], true);
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack leer(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(c);
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack drank(Material m, PotionType t, int n) {
        ItemStack it = new ItemStack(m, n);
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        meta.setBasePotionType(t);
        it.setItemMeta(meta);
        return it;
    }

    /** helm, borst, broek, schoenen */
    private ItemStack[] uitrusting(String id) {
        return switch (id) {
            case "krijger" -> new ItemStack[]{betoverd(Material.IRON_HELMET), betoverd(Material.IRON_CHESTPLATE), betoverd(Material.IRON_LEGGINGS), betoverd(Material.IRON_BOOTS)};
            case "boog" -> new ItemStack[]{betoverd(Material.CHAINMAIL_HELMET), betoverd(Material.CHAINMAIL_CHESTPLATE), betoverd(Material.CHAINMAIL_LEGGINGS), betoverd(Material.CHAINMAIL_BOOTS)};
            case "tank" -> new ItemStack[]{betoverd(Material.IRON_HELMET), betoverd(Material.DIAMOND_CHESTPLATE), betoverd(Material.IRON_LEGGINGS), betoverd(Material.IRON_BOOTS)};
            case "ninja" -> new ItemStack[]{leer(Material.LEATHER_HELMET, Color.fromRGB(0x202020)), leer(Material.LEATHER_CHESTPLATE, Color.fromRGB(0x202020)),
                    leer(Material.LEATHER_LEGGINGS, Color.fromRGB(0x202020)), betoverd(Material.LEATHER_BOOTS, Enchantment.FEATHER_FALLING, 3)};
            case "speer" -> new ItemStack[]{betoverd(Material.CHAINMAIL_HELMET), betoverd(Material.IRON_CHESTPLATE), betoverd(Material.CHAINMAIL_LEGGINGS), betoverd(Material.CHAINMAIL_BOOTS)};
            case "heler" -> new ItemStack[]{betoverd(Material.GOLDEN_HELMET), betoverd(Material.GOLDEN_CHESTPLATE), betoverd(Material.GOLDEN_LEGGINGS), betoverd(Material.GOLDEN_BOOTS)};
            case "hamer" -> new ItemStack[]{betoverd(Material.IRON_HELMET), betoverd(Material.CHAINMAIL_CHESTPLATE), betoverd(Material.IRON_LEGGINGS), betoverd(Material.IRON_BOOTS, Enchantment.FEATHER_FALLING, 2)};
            default -> new ItemStack[4];
        };
    }

    private void geefKit(Player p, String id) {
        var inv = p.getInventory();
        inv.clear();
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        ItemStack[] uit = uitrusting(id);
        inv.setHelmet(uit[0]);
        inv.setChestplate(uit[1]);
        inv.setLeggings(uit[2]);
        inv.setBoots(uit[3]);
        switch (id) {
            case "krijger" -> {
                inv.setItem(0, betoverd(Material.IRON_SWORD, Enchantment.SHARPNESS, 1));
                inv.setItemInOffHand(betoverd(Material.SHIELD));
            }
            case "boog" -> {
                inv.setItem(0, betoverd(Material.STONE_SWORD));
                inv.setItem(1, betoverd(Material.BOW, Enchantment.POWER, 2, Enchantment.INFINITY, 1, Enchantment.PUNCH, 1));
                inv.setItem(9, ding(Material.ARROW, 1));
            }
            case "tank" -> {
                inv.setItem(0, betoverd(Material.IRON_AXE));
                inv.setItemInOffHand(betoverd(Material.SHIELD));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            }
            case "ninja" -> {
                inv.setItem(0, betoverd(Material.GOLDEN_SWORD, Enchantment.SHARPNESS, 3));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 1, false, false, true));
            }
            case "speer" -> {
                inv.setItem(0, betoverd(Material.TRIDENT, Enchantment.LOYALTY, 3, Enchantment.IMPALING, 1));
                inv.setItem(1, betoverd(Material.STONE_SWORD));
            }
            case "heler" -> {
                inv.setItem(0, betoverd(Material.STONE_SWORD, Enchantment.SHARPNESS, 1));
                inv.setItem(1, drank(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 4));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            }
            case "hamer" -> {
                inv.setItem(0, betoverd(Material.MACE, Enchantment.DENSITY, 1));
                inv.setItem(1, ding(Material.WIND_CHARGE, 6));
                inv.setItem(2, betoverd(Material.STONE_SWORD));
            }
            default -> { }
        }
        inv.setItem(8, ding(Material.GOLDEN_APPLE, 1));
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(18);
        p.setSaturation(0f);
    }

    private Kit kit(String id) {
        for (Kit k : KITS) if (k.id().equals(id)) return k;
        return null;
    }

    private void kies(Player p, String id) {
        Kit k = kit(id);
        if (k == null || w == null) return;
        if (kitVan.containsKey(p.getUniqueId())) {
            p.sendMessage(Component.text("Je bent al in de arena. Eerst terug: /arena (of verslagen worden).", NamedTextColor.GRAY));
            return;
        }
        p.closeInventory();
        geefKit(p, id);
        kitVan.put(p.getUniqueId(), id);
        boolean bots = kiestBots.getOrDefault(p.getUniqueId(), false) && !botSpawns.isEmpty();
        if (bots) inBotArena.add(p.getUniqueId()); else inBotArena.remove(p.getUniqueId());
        List<Location> plekken = bots ? botSpawns : spawns;
        Location doel = plekken.isEmpty() ? wacht : plekken.stream().max(Comparator.comparingDouble(l -> {
            double min = 1e9;
            for (Player q : Bukkit.getOnlinePlayers()) {
                if (q.equals(p) || !kitVan.containsKey(q.getUniqueId())) continue;
                min = Math.min(min, q.getLocation().distanceSquared(l));
            }
            return min + rnd.nextDouble() * 20;
        })).get();
        p.teleport(doel);
        int s = Math.max(0, getConfig().getInt("bescherming-seconden", 3));
        beschermdTot.put(p.getUniqueId(), System.currentTimeMillis() + s * 1000L);
        p.sendActionBar(Component.text(k.naam(), k.kleur()).append(Component.text((bots ? " · oefenarena" : "") + " · " + s + " s bescherming", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.4f, 1f);
        w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
        werkBordBij(p);
    }

    // ── wachtplatform ───────────────────────────────────────────────────────────────────────

    private ItemStack kiesItem() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Kit kiezen", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Klik: kies een kit en spring de arena in", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "kies");
        it.setItemMeta(m);
        return it;
    }

    private ItemStack lobbyItem() {
        ItemStack it = new ItemStack(Material.OAK_DOOR);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Naar de lobby", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Of typ /lobby, /survival", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "lobby");
        it.setItemMeta(m);
        return it;
    }

    private String itemSoort(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    private void naarWacht(Player p, boolean bericht) {
        kitVan.remove(p.getUniqueId());
        inBotArena.remove(p.getUniqueId());
        untag(p);
        beschermdTot.remove(p.getUniqueId());
        laatsteAanvaller.remove(p.getUniqueId());
        p.getInventory().clear();
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        if (p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.ADVENTURE);
        p.setFireTicks(0);
        p.setFallDistance(0);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (!p.isDead() && max != null) p.setHealth(max.getValue());
        p.setFoodLevel(18);
        p.setSaturation(0f);
        p.getInventory().setItem(4, kiesItem());
        p.getInventory().setItem(8, lobbyItem());
        if (wacht != null) p.teleport(wacht);
        werkBordBij(p);
        if (bericht) p.sendActionBar(Component.text("Kies een kit om te vechten", NamedTextColor.GRAY));
    }

    private static final class Menu implements InventoryHolder {
        Inventory inv;
        public Inventory getInventory() { return inv; }
    }

    private void openMenu(Player p) {
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, 27, Component.text("Kies je kit", NamedTextColor.DARK_RED));
        ItemStack glas = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glas.getItemMeta();
        gm.setHideTooltip(true);
        glas.setItemMeta(gm);
        for (int i = 0; i < 27; i++) m.inv.setItem(i, glas);
        for (int i = 0; i < KITS.length; i++) {
            Kit k = KITS[i];
            ItemStack it = new ItemStack(k.icoon());
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.text(k.naam(), k.kleur(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(k.uitleg(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Klik: kies en spring de arena in", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, k.id());
            it.setItemMeta(meta);
            m.inv.setItem(10 + i, it);
        }
        boolean bots = kiestBots.getOrDefault(p.getUniqueId(), false);
        m.inv.setItem(21, arenaKnop(Material.IRON_SWORD, "PvP-arena", "tegen andere spelers", !bots, "pvp"));
        m.inv.setItem(23, arenaKnop(Material.ZOMBIE_HEAD, "Oefenarena", "tegen bots", bots, "bots"));
        p.openInventory(m.inv);
    }

    private ItemStack arenaKnop(Material mat, String naam, String uitleg, boolean gekozen, String id) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(naam, gekozen ? NamedTextColor.GREEN : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(uitleg + (gekozen ? " · gekozen" : " · klik om te kiezen"), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        if (gekozen) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "arena:" + id);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onMenuKlik(InventoryClickEvent e) {
        if (!actief) return;
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() >= 27) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || !it.hasItemMeta()) return;
            String id = it.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
            if (id != null) Bukkit.getScheduler().runTask(this, () -> kies(p, id));
            String knop = it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
            if (knop != null && knop.startsWith("arena:")) {
                kiestBots.put(p.getUniqueId(), knop.equals("arena:bots"));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
                Bukkit.getScheduler().runTask(this, () -> openMenu(p));
            }
            return;
        }
        if (itemSoort(e.getCurrentItem()) != null || itemSoort(e.getCursor()) != null) e.setCancelled(true);
        if (e.getWhoClicked() instanceof Player p && !kitVan.containsKey(p.getUniqueId()) && e.getHotbarButton() >= 0) e.setCancelled(true);
    }

    @EventHandler
    public void onSleep(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onKlik(PlayerInteractEvent e) {
        if (!actief || e.getAction() == Action.PHYSICAL) return;
        String soort = itemSoort(e.getItem());
        if (soort == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        if (soort.equals("kies")) openMenu(e.getPlayer());
        else if (soort.equals("lobby") && !blokkeerAlsGevecht(e.getPlayer())) naarServer(e.getPlayer(), "lobby");
    }

    @EventHandler
    public void onStandaard(PlayerInteractAtEntityEvent e) {
        kiesVia(e.getPlayer(), e.getRightClicked(), e);
    }

    @EventHandler
    public void onStandaard2(PlayerInteractEntityEvent e) {
        kiesVia(e.getPlayer(), e.getRightClicked(), e);
    }

    private void kiesVia(Player p, Entity ent, org.bukkit.event.Cancellable e) {
        if (!actief || !(ent instanceof ArmorStand a)) return;
        String id = a.getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        kies(p, id);
    }

    @EventHandler
    public void onStandaardPakken(PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains("kitpvp")) e.setCancelled(true);
    }

    // ── gevecht ─────────────────────────────────────────────────────────────────────────────

    private Player aanvallerVan(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof org.bukkit.entity.TNTPrimed t && t.getSource() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSchade(EntityDamageEvent e) {
        if (!actief || !(e.getEntity() instanceof Player p)) return;
        if (e.getEntity().getScoreboardTags().contains("kitpvp")) {
            e.setCancelled(true);
            return;
        }
        if (!kitVan.containsKey(p.getUniqueId())) {                // op het platform: veilig
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) naarWacht(p, false);
            return;
        }
        if (beschermdTot.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Player a = aanvallerVan(be.getDamager());
            if (a != null && !a.equals(p)) {
                if (inBotArena.contains(p.getUniqueId()) || inBotArena.contains(a.getUniqueId())) {   // oefenarena: alleen bots
                    e.setCancelled(true);
                    return;
                }
                if (!kitVan.containsKey(a.getUniqueId())) {     // vanaf het platform aanvallen kan niet
                    e.setCancelled(true);
                    return;
                }
                beschermdTot.remove(a.getUniqueId());           // wie aanvalt is niet meer beschermd
                laatsteAanvaller.put(p.getUniqueId(), a.getUniqueId());
                laatsteAanvalTijd.put(p.getUniqueId(), System.currentTimeMillis());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDood(PlayerDeathEvent e) {
        if (!actief) return;
        Player dood = e.getPlayer();
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepLevel(true);
        String kitDood = kitVan.remove(dood.getUniqueId());
        untag(dood);
        if (inBotArena.remove(dood.getUniqueId())) {          // oefenarena: telt niet mee, geen melding voor iedereen
            e.deathMessage(null);
            dood.sendActionBar(Component.text("Verslagen in de oefenarena", NamedTextColor.GRAY));
            return;
        }
        Player killer = dood.getKiller();
        if (killer == null) {
            UUID a = laatsteAanvaller.get(dood.getUniqueId());
            Long t = laatsteAanvalTijd.get(dood.getUniqueId());
            if (a != null && t != null && System.currentTimeMillis() - t < 12_000L) killer = Bukkit.getPlayer(a);
        }
        laatsteAanvaller.remove(dood.getUniqueId());
        tel(dood.getUniqueId(), dood.getName(), "doden");
        int oudeReeks = reeks.getOrDefault(dood.getUniqueId(), 0);
        reeks.put(dood.getUniqueId(), 0);
        Component naamDood = dood.displayName().colorIfAbsent(NamedTextColor.WHITE);
        if (killer != null && !killer.equals(dood)) {
            tel(killer.getUniqueId(), killer.getName(), "kills");
            int r = reeks.merge(killer.getUniqueId(), 1, Integer::sum);
            if (r > stats.getInt(killer.getUniqueId() + ".beste", 0)) stats.set(killer.getUniqueId() + ".beste", r);
            var max = killer.getAttribute(Attribute.MAX_HEALTH);
            double hartjes = killer.getHealth() / 2.0;
            Kit kk = kit(kitVan.getOrDefault(killer.getUniqueId(), ""));
            e.deathMessage(Component.text("☠ ", NamedTextColor.DARK_RED).append(naamDood)
                    .append(Component.text(" is verslagen door ", NamedTextColor.GRAY))
                    .append(killer.displayName().colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(kk != null ? " (" + kk.naam() + ", ❤ " + String.format(java.util.Locale.ROOT, "%.1f", hartjes) + ")" : "", NamedTextColor.DARK_GRAY)));
            if (max != null) killer.setHealth(max.getValue());
            if (kitVan.containsKey(killer.getUniqueId())) {
                if (!killer.getInventory().containsAtLeast(new ItemStack(Material.GOLDEN_APPLE), 3)) killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
                if ("hamer".equals(kitVan.get(killer.getUniqueId()))) killer.getInventory().addItem(new ItemStack(Material.WIND_CHARGE, 2));
                if ("heler".equals(kitVan.get(killer.getUniqueId()))) killer.getInventory().addItem(drank(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1));
            }
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
            killer.sendActionBar(Component.text("⚔ Kill! Reeks: " + r, NamedTextColor.GOLD));
            if (getConfig().getIntegerList("reeks-melding").contains(r)) {
                Bukkit.broadcast(Component.text("🔥 ", NamedTextColor.GOLD).append(killer.displayName().colorIfAbsent(NamedTextColor.WHITE))
                        .append(Component.text(" heeft een reeks van " + r + "!", NamedTextColor.GOLD)));
                for (Player q : Bukkit.getOnlinePlayers()) q.playSound(q.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.5f);
            }
            if (oudeReeks >= 3) {
                Bukkit.broadcast(killer.displayName().colorIfAbsent(NamedTextColor.WHITE).append(Component.text(" stopte de reeks van " + oudeReeks + " van ", NamedTextColor.YELLOW)).append(naamDood));
            }
            werkBordBij(killer);
        } else {
            e.deathMessage(Component.text("☠ ", NamedTextColor.DARK_RED).append(naamDood).append(Component.text(" is gesneuveld", NamedTextColor.GRAY)));
        }
        bewaarStats();
        werkRanglijstBij();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!actief || wacht == null) return;
        e.setRespawnLocation(wacht);
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> naarWacht(p, true));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHonger(FoodLevelChangeEvent e) {
        if (!actief) return;
        e.setCancelled(true);
        if (e.getEntity() instanceof Player p && p.getFoodLevel() != 18) p.setFoodLevel(18);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (actief) e.setCancelled(true);
    }

    @EventHandler
    public void onOppakken(PlayerAttemptPickupItemEvent e) {
        if (actief) e.setCancelled(true);
    }

    @EventHandler
    public void onWissel(PlayerSwapHandItemsEvent e) {
        if (actief && !kitVan.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onSchiet(ProjectileLaunchEvent e) {
        if (actief && e.getEntity() instanceof AbstractArrow a && !(e.getEntity() instanceof org.bukkit.entity.Trident)) {
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    @EventHandler
    public void onRaak(ProjectileHitEvent e) {
        if (actief && e.getEntity() instanceof AbstractArrow a && !(a instanceof org.bukkit.entity.Trident)) {
            Bukkit.getScheduler().runTaskLater(this, () -> { if (a.isValid()) a.remove(); }, e.getHitBlock() != null ? 40L : 1L);
        }
    }

    @EventHandler
    public void onBeweeg(PlayerMoveEvent e) {
        if (!actief || w == null) return;
        Location to = e.getTo();
        if (to.getY() < g - 10) {                    // ergens doorheen gevallen
            Player p = e.getPlayer();
            if (kitVan.containsKey(p.getUniqueId())) p.setHealth(0);
            else naarWacht(p, false);
        }
    }

    // ── binnenkomen en weggaan ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        if (!actief) return;
        Player p = e.getPlayer();
        naarWacht(p, false);
        // rustig binnenkomen: geen titel of chatblok, één korte actionbar-regel (de ster staat in vak 5)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && !kitVan.containsKey(p.getUniqueId()))
                p.sendActionBar(Component.text("KitPvP · kies een kit en arena met de ster", NamedTextColor.GRAY));
        }, 20L);
        reeks.put(p.getUniqueId(), 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Player p = e.getPlayer();
        if (inGevecht(id) && kitVan.containsKey(id) && !inBotArena.contains(id)) {   // weglopen uit een gevecht = verslagen
            tel(id, p.getName(), "doden");
            UUID a = laatsteAanvaller.get(id);
            Player killer = a == null ? null : Bukkit.getPlayer(a);
            if (killer != null && kitVan.containsKey(killer.getUniqueId())) {
                tel(killer.getUniqueId(), killer.getName(), "kills");
                int r = reeks.merge(killer.getUniqueId(), 1, Integer::sum);
                if (r > stats.getInt(killer.getUniqueId() + ".beste", 0)) stats.set(killer.getUniqueId() + ".beste", r);
                var max = killer.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) killer.setHealth(max.getValue());
                werkBordBij(killer);
            }
            Bukkit.broadcast(Component.text("☠ ", NamedTextColor.DARK_RED).append(p.displayName().colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(" liep weg uit het gevecht", NamedTextColor.GRAY)));
            bewaarStats();
            werkRanglijstBij();
        }
        gevechtTot.remove(id);
        gevechtGemeld.remove(id);
        inBotArena.remove(id);
        kitVan.remove(id);
        beschermdTot.remove(id);
        reeks.remove(id);
        borden.remove(id);
        e.getPlayer().getInventory().clear();
    }

    // ── statistieken, sidebar en ranglijst ──────────────────────────────────────────────────

    private void tel(UUID id, String naam, String wat) {
        stats.set(id + ".naam", naam);
        stats.set(id + "." + wat, stats.getInt(id + "." + wat, 0) + 1);
    }

    private void bewaarStats() {
        if (stats == null) return;
        try {
            stats.save(statsBestand);
        } catch (IOException e) {
            getLogger().warning("stats.yml: " + e.getMessage());
        }
    }

    private void werkRanglijstBij() {
        if (ranglijst == null || !ranglijst.isValid() || stats == null) return;
        List<String> ids = new ArrayList<>(stats.getKeys(false));
        ids.sort(Comparator.comparingInt((String k) -> stats.getInt(k + ".kills", 0)).reversed());
        Component t = Component.text("🏆 TOP KILLERS", NamedTextColor.GOLD, TextDecoration.BOLD);
        String[] plek = {"🥇", "🥈", "🥉", "4.", "5.", "6.", "7.", "8.", "9.", "10."};
        int n = 0;
        for (String id : ids) {
            if (n >= 10) break;
            int k = stats.getInt(id + ".kills", 0);
            if (k == 0) continue;
            int d = stats.getInt(id + ".doden", 0);
            t = t.append(Component.newline()).append(Component.text(plek[n] + " " + stats.getString(id + ".naam", "?") + "  ", n < 3 ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                    .append(Component.text(k + " kills · K/D " + kd(k, d), NamedTextColor.GRAY));
            n++;
        }
        if (n == 0) t = t.append(Component.newline()).append(Component.text("Nog geen kills. Wie wordt de eerste?", NamedTextColor.GRAY));
        ranglijst.text(t);
    }

    private static String kd(int k, int d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d == 0 ? (double) k : (double) k / d);
    }

    private void werkBordBij(Player p) {
        Scoreboard sb = borden.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective o = sb.getObjective("kitpvp");
        if (o == null) {
            o = sb.registerNewObjective("kitpvp", org.bukkit.scoreboard.Criteria.DUMMY, Component.text("⚔ KitPvP", NamedTextColor.RED, TextDecoration.BOLD));
            o.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            o.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        }
        String id = p.getUniqueId().toString();
        int k = stats.getInt(id + ".kills", 0), d = stats.getInt(id + ".doden", 0);
        Kit kit = kit(kitVan.getOrDefault(p.getUniqueId(), ""));
        String[][] regels = {
                {"b", "§7Kit: " + (kit == null ? "§8—" : "§f" + kit.naam())},
                {"c", "§7Kills: §f" + k + " §8· §7Doden: §f" + d},
                {"f", "§7Reeks: §f" + reeks.getOrDefault(p.getUniqueId(), 0)},
                {"g", "§7Arena: §f" + (inBotArena.contains(p.getUniqueId()) ? "oefen (bots)" : kiestBots.getOrDefault(p.getUniqueId(), false) ? "§8oefen (bots)" : kitVan.containsKey(p.getUniqueId()) ? "PvP" : "§8PvP")},
        };
        int score = regels.length;
        for (String[] r : regels) {
            var s = o.getScore(r[0]);
            s.setScore(score--);
            s.customName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(r[1]));
        }
        if (p.getScoreboard() != sb) p.setScoreboard(sb);
    }

    /** Teams van opmaak (naamtag verbergen bij een nickname, [AFK]) staan op het hoofdscorebord: overnemen. */
    private void spiegelTeams() {
        Scoreboard hoofd = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Scoreboard sb : borden.values()) {
            for (Team t : hoofd.getTeams()) {
                Team kopie = sb.getTeam(t.getName());
                if (kopie == null) {
                    kopie = sb.registerNewTeam(t.getName());
                }
                if (!kopie.prefix().equals(t.prefix())) kopie.prefix(t.prefix());
                for (Team.Option opt : Team.Option.values()) {
                    if (kopie.getOption(opt) != t.getOption(opt)) kopie.setOption(opt, t.getOption(opt));
                }
                for (String en : t.getEntries()) if (!kopie.hasEntry(en)) kopie.addEntry(en);
                for (String en : new ArrayList<>(kopie.getEntries())) if (!t.hasEntry(en)) kopie.removeEntry(en);
            }
        }
    }

    private long seconden = 0;

    private void elkeSeconde() {
        if (!actief || w == null) return;
        seconden++;
        long nu = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getFoodLevel() != 18) p.setFoodLevel(18);
            Long b = beschermdTot.get(p.getUniqueId());
            if (b != null && b <= nu) {
                beschermdTot.remove(p.getUniqueId());
                if (kitVan.containsKey(p.getUniqueId())) p.sendActionBar(Component.text("Bescherming voorbij — vecht!", NamedTextColor.RED));
            }
            if (!kitVan.containsKey(p.getUniqueId())) {
                if (wacht != null && p.getLocation().distanceSquared(wacht) > 30 * 30 && p.getWorld().equals(w)) naarWacht(p, false);
            }
            Long gt = gevechtTot.get(p.getUniqueId());
            if (gt != null) {
                if (gt <= nu) untag(p);
                else p.sendActionBar(Component.text("⚔ In gevecht · " + ((gt - nu + 999) / 1000) + " s", NamedTextColor.RED));
            }
            if (seconden % 2 == 0) werkBordBij(p);
        }
        botsBijhouden();
        spiegelTeams();
    }

    // ── commando's ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!actief) {
            sender.sendMessage("KitPvP is hier niet actief.");
            return true;
        }
        String c = command.getName().toLowerCase();
        if (c.equals("kit")) {
            if (sender instanceof Player p) {
                if (args.length >= 1 && kit(args[0].toLowerCase()) != null) kies(p, args[0].toLowerCase());
                else if (kitVan.containsKey(p.getUniqueId())) p.sendMessage(Component.text("Je vecht al. /arena om terug te gaan naar het platform.", NamedTextColor.GRAY));
                else openMenu(p);
            }
            return true;
        }
        if (c.equals("stats")) {
            Player p = sender instanceof Player pl ? pl : null;
            if (args.length >= 1) p = Bukkit.getPlayerExact(args[0]);
            if (p == null) {
                sender.sendMessage("Speler niet gevonden.");
                return true;
            }
            String id = p.getUniqueId().toString();
            int k = stats.getInt(id + ".kills", 0), d = stats.getInt(id + ".doden", 0);
            sender.sendMessage(Component.text("⚔ " + p.getName() + ": " + k + " kills, " + d + " doden, K/D " + kd(k, d)
                    + ", beste reeks " + stats.getInt(id + ".beste", 0), NamedTextColor.GOLD));
            return true;
        }
        // arena
        if (args.length >= 1 && args[0].equalsIgnoreCase("aftellen") && sender.hasPermission("kitpvp.beheer")) {
            int n = 5;
            try {
                if (args.length >= 2) n = Math.max(1, Math.min(10, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
            aftellen(n);
            sender.sendMessage("aftellen " + n + " s gestart voor " + Bukkit.getOnlinePlayers().size() + " spelers");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("bouw") && sender.hasPermission("kitpvp.beheer")) {
            bouw(true);
            sender.sendMessage("arena opnieuw gebouwd");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("zelftest") && sender.hasPermission("kitpvp.beheer")) {
            sender.sendMessage("kitpvp zelftest: grond=" + g + " wacht=" + (wacht == null ? "-" : wacht.getBlockX() + "," + wacht.getBlockY() + "," + wacht.getBlockZ())
                    + " spawns=" + spawns.size() + " kits=" + KITS.length + " spelers-in-arena=" + kitVan.size()
                    + " stats=" + stats.getKeys(false).size() + " ranglijst=" + (ranglijst != null && ranglijst.isValid())
                    + " botspawns=" + botSpawns.size() + " bots=" + bots().size() + " in-oefenarena=" + inBotArena.size());
            return true;
        }
        if (sender instanceof Player p) {
            if (kitVan.containsKey(p.getUniqueId()) && blokkeerAlsGevecht(p)) return true;
            naarWacht(p, true);
        }
        return true;
    }

    /** Via Velocity (BungeeCord-kanaal) naar een andere server. */
    private void naarServer(Player p, String server) {
        try {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(buf);
            out.writeUTF("Connect");
            out.writeUTF(server);
            p.sendPluginMessage(this, "BungeeCord", buf.toByteArray());
        } catch (IOException e) {
            p.sendMessage(Component.text("Dat lukte niet: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private static final TextColor[] KLEUR = {NamedTextColor.AQUA, NamedTextColor.GREEN, NamedTextColor.YELLOW, NamedTextColor.GOLD, NamedTextColor.RED};

    /** Groot aftellen vóór een herlading (door mc), daarna gaat iedereen even naar de lobby. */
    private void aftellen(int seconden) {
        for (int i = 0; i < seconden; i++) {
            final int n = seconden - i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(Component.text(String.valueOf(n), KLEUR[Math.min(n, KLEUR.length) - 1], TextDecoration.BOLD),
                            Component.text("Herladen… je wacht even veilig in de lobby", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(150))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f + 0.15f * (seconden - n));
                }
            }, i * 20L);
        }
    }
}
