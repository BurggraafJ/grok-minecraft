package nl.burggraaf.mc.kitpvp;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * KitPvP met een hub. Je komt binnen in de hub ({@link Hub}) en kiest een modus: door een poort lopen, het kompas of
 * {@code /modus}. Een kitmodus brengt je naar zijn kitruimte ({@link Kitruimte}): klik een standaard (of de ster,
 * {@code /kit}) en je springt de arena in met even bescherming. Modi staan in {@link ModusRegister} (zie MODI.md):
 * PvP Arena (colosseum + oefenarena met bots) en PvP Castle (kasteel).
 *
 * <p>Story of Zelda is sinds 2.17 een eigen spel (plugin Zelda, eigen wereld op deze server, eigen poort in de lobby).
 * KitPvP bemoeit zich alleen met spelers in zijn eigen wereld ({@link #hier}); wie de KitPvP-wereld in komt, staat in de hub.
 *
 * <p>Geen drops, geen honger, direct respawnen in de kitruimte van je arena. Kills, doden, reeksen en bots in
 * stats.yml, top 10 op borden, eigen statistieken in de sidebar. Gevecht-tag 10 s (ook op de proxy).
 */
public final class KitPvP extends JavaPlugin implements Listener {

    /** Versie van de arena's (colosseum, oefenarena, kasteel). 3: kasteelarena, 4: hub met modi, kitruimtes, nieuw kasteel. */
    static final int BOUW_VERSIE = 4;

    World w;
    int g;                                                     // grondhoogte (vloer van de arena's)
    final Spelers spelers = new Spelers();
    final ModusRegister modi = new ModusRegister();
    Hub hub;
    NamespacedKey itemKey, kitKey, ruimteKey;

    private Gevecht gevecht;
    private Stats stats;
    private Bots bots;
    private Menus menus;
    private Bouwer bouwer;
    private boolean actief = true;
    private final Random rnd = new Random();
    private final List<TextDisplay> ranglijsten = new ArrayList<>();
    private final Map<UUID, String> arenaKeuze = new HashMap<>();      // kitmenu: gekozen arena binnen de modus
    private final Map<UUID, Long> beschermdTot = new HashMap<>();
    private final Map<UUID, UUID> laatsteAanvaller = new HashMap<>();
    private final Map<UUID, Long> laatsteAanvalTijd = new HashMap<>();
    private final Map<UUID, Integer> reeks = new HashMap<>();
    private final Map<UUID, Scoreboard> borden = new HashMap<>();
    private final Map<UUID, Long> binnenkortGemeld = new HashMap<>();
    private long seconden = 0;

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
        ruimteKey = new NamespacedKey(this, "ruimte");
        stats = new Stats(new File(getDataFolder(), "stats.yml"), getLogger());
        gevecht = new Gevecht(this);
        bots = new Bots(this);
        menus = new Menus(this);
        // de modi, in menuvolgorde; een nieuwe modus = hier één regel erbij (zie MODI.md). Story of Zelda is een eigen plugin.
        modi.registreer(new ArenaModus(this));
        modi.registreer(new KasteelModus(this));
        hub = new Hub(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "netwerk:main");
        Bukkit.getScheduler().runTask(this, () -> {
            w = Bukkit.getWorlds().get(0);
            // alles blijft geladen: standaards, labels en ranglijsten verdwijnen anders
            for (int cx = -4; cx <= 3; cx++)
                for (int cz = -12; cz <= 8; cz++) w.addPluginChunkTicket(cx, cz, this);   // colosseum, oefenarena, kasteel
            for (int cx = Math.floorDiv(Hub.HX - 36, 16); cx <= Math.floorDiv(Hub.HX + 36, 16); cx++)
                for (int cz = Math.floorDiv(Hub.HZ - 36, 16); cz <= Math.floorDiv(Hub.HZ + 36, 16); cz++) w.addPluginChunkTicket(cx, cz, this);
            bouw(Set.of());
            for (Modus m : modi.alle()) m.aan(this);
            for (Player p : Bukkit.getOnlinePlayers()) if (hier(p)) naarHub(p);
        });
        Bukkit.getScheduler().runTaskTimer(this, this::elkeSeconde, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::meldStand, 40L, 5L);
    }

    @Override
    public void onDisable() {
        if (!actief) return;
        for (Modus m : modi.alle()) m.uit();
        if (bouwer != null) bouwer.stop();
        if (stats != null) stats.bewaar();
        if (w != null) {
            for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains("kitpvp") || Bots.is(e)) e.remove();
        }
    }

    /** Is deze speler in de KitPvP-wereld? Alleen dan gelden de KitPvP-regels (Story of Zelda heeft een eigen wereld). */
    boolean hier(Player p) {
        return w != null && p.getWorld().equals(w);
    }

    /** Voor de plugin Huisdieren: waar staat iedereen (wacht = hub of kitruimte, pvp, bots; zie Spelers.stand). */
    private void meldStand() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hier(p)) continue;                              // in Zelda zet de plugin Zelda de stand («geen»)
            String s = stand(p);
            var m = p.getMetadata("kitpvp-stand");
            if (m.isEmpty() || !s.equals(m.get(0).asString())) p.setMetadata("kitpvp-stand", new org.bukkit.metadata.FixedMetadataValue(this, s));
        }
    }

    String stand(Player p) {
        return Spelers.stand(spelers.van(p.getUniqueId()),
                a -> modi.arena(a) == null ? "wacht" : modi.arena(a).stand,
                t -> modi.get(t.modus()) == null ? "wacht" : modi.get(t.modus()).stand(p, t));
    }

    // ── bouwen ──────────────────────────────────────────────────────────────────────────────

    /** Bouw alle delen (hub en die van de modi). {@code forceer}: ids van delen, of "alles". */
    void bouw(Set<String> forceer) {
        if (bouwer != null) bouwer.stop();
        File yml = new File(getDataFolder(), "gebouwd.yml"), oud = new File(getDataFolder(), "gebouwd.txt");
        YamlConfiguration stand = YamlConfiguration.loadConfiguration(yml);
        if (stand.contains("grond")) g = stand.getInt("grond");
        else {
            String eerder = null;
            try {
                if (oud.exists()) eerder = java.nio.file.Files.readString(oud.toPath()).trim();
            } catch (IOException ignored) {
            }
            g = eerder != null && eerder.contains(":") ? Integer.parseInt(eerder.split(":")[1]) : w.getHighestBlockYAt(0, 0);
        }
        for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains("kitpvp")) e.remove();
        ranglijsten.clear();
        bouwer = new Bouwer(this, w, g);
        List<Deel> delen = new ArrayList<>();
        delen.add(hub.deel());
        for (Modus m : modi.alle()) delen.addAll(m.delen());
        Map<String, Integer> nieuw = new LinkedHashMap<>();
        for (Deel d : delen) {
            bouwer.plaats = forceer.contains("alles") || forceer.contains(d.id()) || stand.getInt("delen." + d.id(), -1) != d.versie();
            if (bouwer.plaats) nieuw.put(d.id(), d.versie());
            d.bouw().accept(bouwer);
        }
        bouwer.plaats = false;
        w.setSpawnLocation(hub.aankomst());
        werkRanglijstBij();
        long t0 = System.currentTimeMillis();
        Bouwer deze = bouwer;
        if (!nieuw.isEmpty()) getLogger().info("bouwen: " + String.join(", ", nieuw.keySet()) + " (" + deze.inRij() + " stappen, verdeeld over ticks)");
        deze.start(() -> {
            if (nieuw.isEmpty()) return;
            stand.set("grond", g);
            nieuw.forEach((k, v) -> stand.set("delen." + k, v));
            try {
                getDataFolder().mkdirs();
                stand.save(yml);
                if (oud.exists() && !oud.delete()) getLogger().fine("gebouwd.txt bleef staan");
            } catch (IOException e) {
                getLogger().warning("gebouwd.yml: " + e.getMessage());
            }
            getLogger().info("gebouwd: " + String.join(", ", nieuw.keySet()) + " in " + (System.currentTimeMillis() - t0) + " ms, " + deze.gezet + " blokken");
        });
    }

    /** Een top-10 bord op deze plek (wordt bij elke kill bijgewerkt). */
    void ranglijst(Location l) {
        ranglijsten.add(w.spawn(l, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
            d.addScoreboardTag("kitpvp");
        }));
    }

    private void werkRanglijstBij() {
        if (stats == null) return;
        Component t = stats.ranglijst();
        for (TextDisplay d : ranglijsten) if (d.isValid()) d.text(t);
    }

    // ── waar je bent: hub, kitruimte, arena ─────────────────────────────────────────────────

    /** Zet de toestand; verlaat je daarmee een modus, dan krijgt die {@code leave}. */
    private Toestand zetToestand(Player p, Toestand t) {
        Toestand oud = spelers.zet(p.getUniqueId(), t);
        String weg = Spelers.verlaten(oud, t);
        if (weg != null && modi.get(weg) != null) modi.get(weg).leave(p);
        return oud;
    }

    /** Schoon beginnen: geen kit, geen tag, vol leven, adventure. */
    private void schoon(Player p) {
        gevecht.untag(p);
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
    }

    /** Het item «Terug naar de hub» (vak 8): als /arena, geweigerd tijdens een gevecht. */
    void hubKnop(Player p) {
        if (spelers.inArena(p.getUniqueId()) && gevecht.blokkeer(p)) return;
        p.closeInventory();
        naarHub(p);
    }

    ItemStack hubItem() {
        return menus.hub();
    }

    void naarHub(Player p) {
        zetToestand(p, Toestand.HUB);
        schoon(p);
        p.getInventory().setItem(4, menus.kompas());
        p.getInventory().setItem(8, menus.deur());
        p.getInventory().setHeldItemSlot(4);
        if (hub.aankomst() != null) p.teleport(hub.aankomst());
        werkBordBij(p);
    }

    void naarKitruimte(Player p, Kitruimte r, boolean bericht) {
        zetToestand(p, Toestand.kitruimte(r.modus, r.id));
        schoon(p);
        arenaKeuze.put(p.getUniqueId(), r.arena);
        p.getInventory().setItem(0, menus.kompas());
        p.getInventory().setItem(4, menus.ster());
        p.getInventory().setItem(8, menus.deur());
        p.getInventory().setHeldItemSlot(4);
        if (r.aankomst != null) p.teleport(r.aankomst);
        werkBordBij(p);
        if (bericht) p.sendActionBar(Component.text(r.naam + " · kies een kit", NamedTextColor.GRAY));
    }

    /**
     * Voor modi met een eigen spel in de hub (zoals Zelda tot 2.16): zet de speler in {@link Toestand.Plek#SPEL} van modus {@code m}.
     * Teleporteren, items en regels doet de modus zelf; KitPvP bemoeit zich dan niet met schade, honger of grenzen.
     */
    public void naarSpel(Player p, Modus m, String ruimte) {
        zetToestand(p, Toestand.spel(m.id(), ruimte));
        gevecht.untag(p);
        beschermdTot.remove(p.getUniqueId());
        werkBordBij(p);
    }

    /** Modus kiezen (poort of menu): open = erin, binnenkort = één grijze regel. */
    void kiesModus(Player p, Modus m) {
        if (m.status() != Modus.Status.OPEN) {
            binnenkort(p, m);
            return;
        }
        Toestand t = spelers.van(p.getUniqueId());
        if (t.inArena() && gevecht.blokkeer(p)) return;
        p.closeInventory();
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.2f);
        m.join(p);
    }

    /** Eén korte grijze regel, hooguit eens per 4 s. */
    void binnenkort(Player p, Modus m) {
        long nu = System.currentTimeMillis();
        if (nu - binnenkortGemeld.getOrDefault(p.getUniqueId(), 0L) < 4000L) return;
        binnenkortGemeld.put(p.getUniqueId(), nu);
        p.sendActionBar(Component.text(m.naam() + " · binnenkort", NamedTextColor.GRAY));
    }

    boolean beschermd(UUID id) {
        return beschermdTot.getOrDefault(id, 0L) > System.currentTimeMillis();
    }

    /** Kit kiezen en de arena in. */
    void kies(Player p, String id, Arena arena) {
        Kits.Kit k = Kits.kit(id);
        if (k == null || w == null || arena == null) return;
        Toestand t = spelers.van(p.getUniqueId());
        if (t.inArena()) {
            p.sendMessage(Component.text("Je bent al in de arena. Eerst terug: /arena (of verslagen worden).", NamedTextColor.GRAY));
            return;
        }
        if (!t.beheerd()) return;
        p.closeInventory();
        Kits.geef(p, id);
        p.getInventory().setItem(Menus.HUB_VAK, menus.hub());
        zetToestand(p, Toestand.arena(arena.modus, arena.id, id));
        List<Location> plekken = arena.spawns;
        Location doel = plekken.isEmpty() ? hub.aankomst() : plekken.stream().max(Comparator.comparingDouble(l -> {
            double min = 1e9;
            for (Player q : Bukkit.getOnlinePlayers()) {
                if (q.equals(p) || !spelers.inArena(q.getUniqueId()) || !q.getWorld().equals(l.getWorld())) continue;
                min = Math.min(min, q.getLocation().distanceSquared(l));
            }
            return min + rnd.nextDouble() * 20;
        })).get();
        p.teleport(doel);
        int s = Math.max(0, getConfig().getInt("bescherming-seconden", 3));
        beschermdTot.put(p.getUniqueId(), System.currentTimeMillis() + s * 1000L);
        p.sendActionBar(Component.text(k.naam(), k.kleur()).append(Component.text(" · " + s + " s bescherming · vak 8 = terug naar de hub", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.4f, 1f);
        w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
        werkBordBij(p);
    }

    /** Waar iemand heen moet die veilig hoort te zijn (hub of zijn kitruimte). */
    private void terugNaarVeilig(Player p) {
        Toestand t = spelers.van(p.getUniqueId());
        Kitruimte r = t.plek() == Toestand.Plek.KITRUIMTE ? modi.kitruimte(t.ruimte()) : null;
        Location l = r != null ? r.aankomst : hub.aankomst();
        if (l != null) {
            p.setFallDistance(0);
            p.teleport(l);
        }
    }

    // ── menu's en items ─────────────────────────────────────────────────────────────────────

    private String itemSoort(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    private void openKitMenu(Player p) {
        Toestand t = spelers.van(p.getUniqueId());
        if (t.plek() == Toestand.Plek.HUB) {
            menus.modusMenu(p);
            return;
        }
        if (t.inArena()) {
            p.sendMessage(Component.text("Je vecht al. /arena om terug te gaan naar de hub.", NamedTextColor.GRAY));
            return;
        }
        Kitruimte r = modi.kitruimte(t.ruimte());
        if (r != null) menus.kitMenu(p, r, gekozenArena(p, r));
    }

    private String gekozenArena(Player p, Kitruimte r) {
        String a = arenaKeuze.get(p.getUniqueId());
        Arena ar = modi.arena(a);
        return ar != null && ar.modus.equals(r.modus) ? a : r.arena;
    }

    @EventHandler
    public void onMenuKlik(InventoryClickEvent e) {
        if (!actief) return;
        if (e.getView().getTopInventory().getHolder(false) instanceof Menus.Menu menu) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() >= 27) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || !it.hasItemMeta()) return;
            var pdc = it.getItemMeta().getPersistentDataContainer();
            String kit = pdc.get(kitKey, PersistentDataType.STRING);
            String knop = pdc.get(itemKey, PersistentDataType.STRING);
            if (menu.soort.equals("kit") && kit != null) {
                Toestand t = spelers.van(p.getUniqueId());
                Kitruimte r = modi.kitruimte(t.ruimte());
                if (t.plek() == Toestand.Plek.KITRUIMTE && r != null) {
                    Arena a = modi.arena(gekozenArena(p, r));
                    Bukkit.getScheduler().runTask(this, () -> kies(p, kit, a));
                }
            } else if (knop != null && knop.startsWith("arena:")) {
                arenaKeuze.put(p.getUniqueId(), knop.substring("arena:".length()));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
                Bukkit.getScheduler().runTask(this, () -> openKitMenu(p));
            } else if (knop != null && knop.startsWith("modus:")) {
                Modus m = modi.get(knop.substring("modus:".length()));
                if (m == null) return;
                if (m.status() != Modus.Status.OPEN) {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 0.7f);
                    return;
                }
                Bukkit.getScheduler().runTask(this, () -> kiesModus(p, m));
            }
            return;
        }
        if (e.getWhoClicked() instanceof Player p && (!hier(p) || !spelers.van(p.getUniqueId()).beheerd())) return;
        if (itemSoort(e.getCurrentItem()) != null || itemSoort(e.getCursor()) != null) e.setCancelled(true);
        if (e.getHotbarButton() >= 0 && e.getWhoClicked() instanceof Player p && itemSoort(p.getInventory().getItem(e.getHotbarButton())) != null) e.setCancelled(true);
        if (e.getWhoClicked() instanceof Player p && spelers.van(p.getUniqueId()).veilig() && e.getHotbarButton() >= 0) e.setCancelled(true);
    }

    @EventHandler
    public void onSleep(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menus.Menu) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onKlik(PlayerInteractEvent e) {
        if (!actief || e.getAction() == Action.PHYSICAL || !hier(e.getPlayer())) return;
        String soort = itemSoort(e.getItem());
        if (soort == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        Player p = e.getPlayer();
        switch (soort) {
            case "kies" -> openKitMenu(p);
            case "modus" -> menus.modusMenu(p);
            case "hub" -> hubKnop(p);
            case "lobby" -> {
                if (!gevecht.blokkeer(p)) naarServer(p, "lobby");
            }
            default -> { }
        }
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
        Kitruimte r = modi.kitruimte(a.getPersistentDataContainer().get(ruimteKey, PersistentDataType.STRING));
        if (r == null || spelers.van(p.getUniqueId()).plek() != Toestand.Plek.KITRUIMTE) return;
        kies(p, id, modi.arena(r.arena));
    }

    @EventHandler
    public void onStandaardPakken(PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains("kitpvp")) e.setCancelled(true);
    }

    // ── bots ────────────────────────────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBotDoel(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (!actief || !Bots.is(e.getEntity()) || e.getTarget() == null) return;
        if (!(e.getTarget() instanceof Player p) || !arenaIsBots(spelers.arena(p.getUniqueId()))) e.setCancelled(true);
    }

    private boolean arenaIsBots(String arena) {
        Arena a = modi.arena(arena);
        return a != null && a.bots();
    }

    @EventHandler
    public void onBotDood(org.bukkit.event.entity.EntityDeathEvent e) {
        if (!actief || !Bots.is(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player k = e.getEntity().getKiller();
        if (k == null || !spelers.inArena(k.getUniqueId())) return;
        var max = k.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) k.setHealth(Math.min(max.getValue(), k.getHealth() + 8));
        stats.tel(k.getUniqueId(), k.getName(), "bots");
        k.sendActionBar(Component.text("Bot verslagen · " + stats.get(k.getUniqueId(), "bots"), NamedTextColor.GRAY));
        k.playSound(k.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }

    // ── gevecht ─────────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTreffer(EntityDamageByEntityEvent e) {
        if (!actief) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        if (e.getEntity() instanceof Player p && spelers.inArena(p.getUniqueId()) && (d instanceof Player || Bots.is(d))) gevecht.tag(p);
        if (d instanceof Player a && spelers.inArena(a.getUniqueId()) && (e.getEntity() instanceof Player || Bots.is(e.getEntity()))) gevecht.tag(a);
    }

    private Player aanvallerVan(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof org.bukkit.entity.TNTPrimed t && t.getSource() instanceof Player p) return p;
        if (d instanceof org.bukkit.entity.Tameable t && t.isTamed() && t.getOwner() instanceof Player p) return p;   // je huisdier vecht voor jou
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSchade(EntityDamageEvent e) {
        if (!actief || !(e.getEntity() instanceof Player p) || !hier(p)) return;
        Toestand t = spelers.van(p.getUniqueId());
        if (!t.beheerd()) return;                                  // eigen spel van een modus: die regelt het
        if (!t.inArena()) {                                        // hub en kitruimte: veilig
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) terugNaarVeilig(p);
            return;
        }
        if (beschermd(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Player a = aanvallerVan(be.getDamager());
            if (a != null && !a.equals(p)) {
                Toestand ta = spelers.van(a.getUniqueId());
                // alleen in dezelfde arena, en nooit in de oefenarena (daar alleen bots)
                if (!ta.inArena() || !t.ruimte().equals(ta.ruimte()) || arenaIsBots(t.ruimte())) {
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
        if (!actief || !hier(e.getPlayer())) return;
        Player dood = e.getPlayer();
        Toestand t = spelers.van(dood.getUniqueId());
        if (!t.beheerd()) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepLevel(true);
        gevecht.untag(dood);
        if (!t.inArena()) {
            e.deathMessage(null);
            return;
        }
        Kitruimte terug = modi.kitruimteVoor(t.ruimte());       // na de respawn: de kitruimte van deze arena
        zetToestand(dood, terug != null ? Toestand.kitruimte(terug.modus, terug.id) : Toestand.HUB);
        if (arenaIsBots(t.ruimte())) {                          // oefenarena: telt niet mee, geen melding voor iedereen
            e.deathMessage(null);
            dood.sendActionBar(Component.text("Verslagen in de oefenarena", NamedTextColor.GRAY));
            return;
        }
        Player killer = dood.getKiller();
        if (killer == null) {
            UUID a = laatsteAanvaller.get(dood.getUniqueId());
            Long tijd = laatsteAanvalTijd.get(dood.getUniqueId());
            if (a != null && tijd != null && System.currentTimeMillis() - tijd < 12_000L) killer = Bukkit.getPlayer(a);
        }
        laatsteAanvaller.remove(dood.getUniqueId());
        stats.tel(dood.getUniqueId(), dood.getName(), "doden");
        int oudeReeks = reeks.getOrDefault(dood.getUniqueId(), 0);
        reeks.put(dood.getUniqueId(), 0);
        Component naamDood = dood.displayName().colorIfAbsent(NamedTextColor.WHITE);
        if (killer != null && !killer.equals(dood)) {
            stats.tel(killer.getUniqueId(), killer.getName(), "kills");
            int r = reeks.merge(killer.getUniqueId(), 1, Integer::sum);
            stats.reeks(killer.getUniqueId(), r);
            var max = killer.getAttribute(Attribute.MAX_HEALTH);
            double hartjes = killer.getHealth() / 2.0;
            Kits.Kit kk = Kits.kit(String.valueOf(spelers.kit(killer.getUniqueId())));
            e.deathMessage(Component.text("☠ ", NamedTextColor.DARK_RED).append(naamDood)
                    .append(Component.text(" is verslagen door ", NamedTextColor.GRAY))
                    .append(killer.displayName().colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(kk != null ? " (" + kk.naam() + ", ❤ " + String.format(java.util.Locale.ROOT, "%.1f", hartjes) + ")" : "", NamedTextColor.DARK_GRAY)));
            if (max != null) killer.setHealth(max.getValue());
            if (spelers.inArena(killer.getUniqueId())) Kits.beloon(killer, spelers.kit(killer.getUniqueId()));
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
        stats.bewaar();
        werkRanglijstBij();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!actief || hub.aankomst() == null || !hier(e.getPlayer())) return;
        Player p = e.getPlayer();
        Toestand t = spelers.van(p.getUniqueId());
        if (!t.beheerd()) {
            Modus m = modi.get(t.modus());
            Location l = m == null ? null : m.respawn(p, t);
            if (l != null) {
                e.setRespawnLocation(l);
                return;
            }
        }
        Kitruimte r = t.plek() == Toestand.Plek.KITRUIMTE ? modi.kitruimte(t.ruimte()) : null;
        e.setRespawnLocation(r != null && r.aankomst != null ? r.aankomst : hub.aankomst());
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            if (r != null) naarKitruimte(p, r, true);
            else naarHub(p);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHonger(FoodLevelChangeEvent e) {
        if (!actief || e.getEntity() instanceof Player p && (!hier(p) || !spelers.van(p.getUniqueId()).beheerd())) return;
        e.setCancelled(true);
        if (e.getEntity() instanceof Player p && p.getFoodLevel() != 18) p.setFoodLevel(18);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (actief && hier(e.getPlayer()) && spelers.van(e.getPlayer().getUniqueId()).beheerd()) e.setCancelled(true);
    }

    @EventHandler
    public void onOppakken(PlayerAttemptPickupItemEvent e) {
        if (actief && hier(e.getPlayer()) && spelers.van(e.getPlayer().getUniqueId()).beheerd()) e.setCancelled(true);
    }

    @EventHandler
    public void onWissel(PlayerSwapHandItemsEvent e) {
        if (actief && hier(e.getPlayer()) && spelers.van(e.getPlayer().getUniqueId()).veilig()) e.setCancelled(true);
    }

    @EventHandler
    public void onSchiet(ProjectileLaunchEvent e) {
        if (actief && e.getEntity().getWorld().equals(w) && e.getEntity() instanceof AbstractArrow a && !(e.getEntity() instanceof org.bukkit.entity.Trident)) {
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    @EventHandler(ignoreCancelled = true)                    // pijlen in de arena's ruimen we op (Zelda doet dat in zijn eigen wereld zelf)
    public void onRaak(ProjectileHitEvent e) {
        if (actief && e.getEntity().getWorld().equals(w) && e.getEntity() instanceof AbstractArrow a && !(a instanceof org.bukkit.entity.Trident)) {
            Bukkit.getScheduler().runTaskLater(this, () -> { if (a.isValid()) a.remove(); }, e.getHitBlock() != null ? 40L : 1L);
        }
    }

    @EventHandler
    public void onBeweeg(PlayerMoveEvent e) {
        if (!actief || w == null) return;
        Location from = e.getFrom(), to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        Player p = e.getPlayer();
        Toestand t = spelers.van(p.getUniqueId());
        if (!t.beheerd() || !to.getWorld().equals(w)) return;
        if (to.getY() < g - 10) {                                // ergens doorheen gevallen
            if (t.inArena()) p.setHealth(0);
            else terugNaarVeilig(p);
            return;
        }
        if (t.plek() == Toestand.Plek.HUB) {
            Hub.Raak r = hub.raak(to);
            if (r == null) return;
            if (r.dicht()) binnenkort(p, r.modus());
            else kiesModus(p, r.modus());
        } else if (t.plek() == Toestand.Plek.KITRUIMTE) {
            Kitruimte r = modi.kitruimte(t.ruimte());
            String deur = r == null ? null : r.deurBij(to);
            if (deur == null) return;
            if (deur.equals("hub")) naarHub(p);
            else if (modi.kitruimte(deur) != null) naarKitruimte(p, modi.kitruimte(deur), true);
        }
    }

    // ── binnenkomen en weggaan ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        if (!actief || !hier(e.getPlayer())) return;            // binnen via de Zelda-poort: dan staat hij al in de Zelda-wereld
        Player p = e.getPlayer();
        naarHub(p);
        // rustig binnenkomen: geen titel of chatblok, één korte actiebalkregel
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && spelers.van(p.getUniqueId()).plek() == Toestand.Plek.HUB)
                p.sendActionBar(Component.text("KitPvP · kies een modus", NamedTextColor.GRAY));
        }, 20L);
        reeks.put(p.getUniqueId(), 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!actief) return;
        UUID id = e.getPlayer().getUniqueId();
        Player p = e.getPlayer();
        Toestand t = spelers.van(id);
        if (gevecht.in(id) && t.inArena() && !arenaIsBots(t.ruimte())) {   // weglopen uit een gevecht = verslagen
            stats.tel(id, p.getName(), "doden");
            UUID a = laatsteAanvaller.get(id);
            Player killer = a == null ? null : Bukkit.getPlayer(a);
            if (killer != null && spelers.inArena(killer.getUniqueId())) {
                stats.tel(killer.getUniqueId(), killer.getName(), "kills");
                int r = reeks.merge(killer.getUniqueId(), 1, Integer::sum);
                stats.reeks(killer.getUniqueId(), r);
                var max = killer.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) killer.setHealth(max.getValue());
                werkBordBij(killer);
            }
            Bukkit.broadcast(Component.text("☠ ", NamedTextColor.DARK_RED).append(p.displayName().colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(" liep weg uit het gevecht", NamedTextColor.GRAY)));
            stats.bewaar();
            werkRanglijstBij();
        }
        Toestand oud = spelers.weg(id);
        if (oud.modus() != null && modi.get(oud.modus()) != null) modi.get(oud.modus()).leave(p);
        gevecht.vergeet(id);
        beschermdTot.remove(id);
        reeks.remove(id);
        borden.remove(id);
        arenaKeuze.remove(id);
        binnenkortGemeld.remove(id);
        if (oud.beheerd() && hier(p)) p.getInventory().clear();
    }

    /**
     * Van of naar een andere wereld op deze server (Story of Zelda). Weg uit de KitPvP-wereld: KitPvP vergeet je (je spullen
     * laat het aan Zelda). Erin: je staat in de hub (Zelda heeft zijn spullen dan al opgeruimd).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAndereWereld(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        if (!actief || w == null) return;
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (e.getFrom().equals(w) && !hier(p)) {
            Toestand oud = spelers.weg(id);
            if (oud.modus() != null && modi.get(oud.modus()) != null) modi.get(oud.modus()).leave(p);
            gevecht.untag(p);
            gevecht.vergeet(id);
            beschermdTot.remove(id);
            laatsteAanvaller.remove(id);
            borden.remove(id);
            arenaKeuze.remove(id);
            p.removeMetadata("kitpvp-stand", this);
        } else if (hier(p)) {
            naarHub(p);
        }
    }

    // ── sidebar ─────────────────────────────────────────────────────────────────────────────

    private String plekNaam(Player p, Toestand t) {
        return switch (t.plek()) {
            case HUB -> "§8Hub";
            case KITRUIMTE -> {
                Kitruimte r = modi.kitruimte(t.ruimte());
                yield "§8" + (r == null ? "—" : r.naam);
            }
            case ARENA -> {
                Arena a = modi.arena(t.ruimte());
                yield "§f" + (a == null ? "—" : a.naam);
            }
            case SPEL -> {
                Modus m = modi.get(t.modus());
                yield "§f" + (m == null ? "—" : m.plek(p, t));
            }
        };
    }

    private void werkBordBij(Player p) {
        Scoreboard sb = borden.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective o = sb.getObjective("kitpvp");
        if (o == null) {
            o = sb.registerNewObjective("kitpvp", org.bukkit.scoreboard.Criteria.DUMMY, Component.text("⚔ KitPvP", NamedTextColor.RED));
            o.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            o.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        }
        UUID id = p.getUniqueId();
        int k = stats.get(id, "kills"), d = stats.get(id, "doden");
        Toestand t = spelers.van(id);
        Kits.Kit kit = t.kit() == null ? null : Kits.kit(t.kit());
        String[][] regels = {
                {"b", "§7Kit: " + (kit == null ? "§8—" : "§f" + kit.naam())},
                {"c", "§7Kills: §f" + k + " §8· §7Doden: §f" + d},
                {"f", "§7Reeks: §f" + reeks.getOrDefault(id, 0)},
                {"g", "§7Plek: " + plekNaam(p, t)},
        };
        int score = regels.length + 1;
        for (String[] r : regels) {
            var s = o.getScore(r[0]);
            s.setScore(score--);
            s.customName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(r[1]));
        }
        if (t.plek() == Toestand.Plek.ARENA || t.plek() == Toestand.Plek.SPEL) {     // waar de weg terug is, rustig onderaan
            var s = o.getScore("h");
            s.setScore(score);
            s.customName(Component.text("Hub: vak 8 of /arena", NamedTextColor.DARK_GRAY));
        } else if (o.getScore("h").isScoreSet()) {
            sb.resetScores("h");
        }
        if (p.getScoreboard() != sb) p.setScoreboard(sb);
    }

    /** Teams van opmaak (naamtag verbergen bij een nickname, [AFK]) staan op het hoofdscorebord: overnemen. */
    private void spiegelTeams() {
        Scoreboard hoofd = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Scoreboard sb : borden.values()) {
            for (Team t : hoofd.getTeams()) {
                Team kopie = sb.getTeam(t.getName());
                if (kopie == null) kopie = sb.registerNewTeam(t.getName());
                if (!kopie.prefix().equals(t.prefix())) kopie.prefix(t.prefix());
                for (Team.Option opt : Team.Option.values()) {
                    if (kopie.getOption(opt) != t.getOption(opt)) kopie.setOption(opt, t.getOption(opt));
                }
                for (String en : t.getEntries()) if (!kopie.hasEntry(en)) kopie.addEntry(en);
                for (String en : new ArrayList<>(kopie.getEntries())) if (!t.hasEntry(en)) kopie.removeEntry(en);
            }
        }
    }

    private void elkeSeconde() {
        if (!actief || w == null) return;
        seconden++;
        long nu = System.currentTimeMillis();
        List<Player> inHub = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!hier(p)) continue;                                // Story of Zelda (eigen wereld): eigen regels, eigen sidebar
            UUID id = p.getUniqueId();
            Toestand t = spelers.van(id);
            if (t.beheerd() && p.getFoodLevel() != 18) p.setFoodLevel(18);
            Long b = beschermdTot.get(id);
            if (b != null && b <= nu) {
                beschermdTot.remove(id);
                if (t.inArena()) p.sendActionBar(Component.text("Bescherming voorbij — vecht!", NamedTextColor.RED));
            }
            if (t.plek() == Toestand.Plek.HUB) {
                inHub.add(p);
                if (p.getWorld().equals(w) && !hub.binnen(p.getLocation()) && hub.raak(p.getLocation()) == null) terugNaarVeilig(p);
            } else if (t.plek() == Toestand.Plek.KITRUIMTE) {
                Kitruimte r = modi.kitruimte(t.ruimte());
                if (r != null && p.getWorld().equals(w) && !r.binnen(p.getLocation())) terugNaarVeilig(p);
            }
            Long gt = gevecht.tot(id);
            if (gt != null) {
                if (gt <= nu) gevecht.untag(p);
                else p.sendActionBar(Component.text("⚔ In gevecht · " + ((gt - nu + 999) / 1000) + " s", NamedTextColor.RED));
            }
            if (seconden % 2 == 0) werkBordBij(p);
        }
        for (Arena a : modi.arenas()) if (a.bots()) bots.tik(w, g, a, seconden);
        spiegelTeams();
        hub.deeltjes(inHub);
        for (Modus m : modi.alle()) {
            try {
                m.tik();
            } catch (RuntimeException ex) {
                getLogger().warning("modus " + m.id() + ": " + ex);
            }
        }
    }

    // ── commando's ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!actief) {
            sender.sendMessage("KitPvP is hier niet actief.");
            return true;
        }
        String c = command.getName().toLowerCase();
        if (sender instanceof Player p && !hier(p) && List.of("kit", "modus", "arena").contains(c)
                && !(c.equals("arena") && args.length >= 1 && sender.hasPermission("kitpvp.beheer"))) {
            if (w != null) p.teleport(hub.aankomst() != null ? hub.aankomst() : w.getSpawnLocation());   // vanuit Zelda: naar de KitPvP-hub (de wereldwissel zet je klaar)
            return true;
        }
        switch (c) {
            case "kit" -> {
                if (!(sender instanceof Player p)) return true;
                Toestand t = spelers.van(p.getUniqueId());
                Kitruimte r = t.plek() == Toestand.Plek.KITRUIMTE ? modi.kitruimte(t.ruimte()) : null;
                if (args.length >= 1 && r != null && Kits.kit(args[0].toLowerCase()) != null) kies(p, args[0].toLowerCase(), modi.arena(gekozenArena(p, r)));
                else openKitMenu(p);
                return true;
            }
            case "modus" -> {
                if (sender instanceof Player p) {
                    if (spelers.inArena(p.getUniqueId()) && gevecht.blokkeer(p)) return true;
                    Modus m = args.length >= 1 ? zoekModus(args[0]) : null;
                    if (m != null) kiesModus(p, m);
                    else menus.modusMenu(p);
                } else {
                    for (Modus m : modi.alle()) sender.sendMessage(m.id() + " · " + m.naam() + " · " + m.status().name().toLowerCase() + " · poort " + m.poort().plek());
                }
                return true;
            }
            case "stats" -> {
                Player p = sender instanceof Player pl ? pl : null;
                if (args.length >= 1) p = Bukkit.getPlayerExact(args[0]);
                if (p == null) {
                    sender.sendMessage("Speler niet gevonden.");
                    return true;
                }
                UUID id = p.getUniqueId();
                int k = stats.get(id, "kills"), d = stats.get(id, "doden");
                sender.sendMessage(Component.text("⚔ " + p.getName() + ": " + k + " kills, " + d + " doden, K/D " + Stats.kd(k, d)
                        + ", beste reeks " + stats.get(id, "beste"), NamedTextColor.GOLD));
                return true;
            }
            case "kitpvp" -> {
                beheer(sender, args);
                return true;
            }
            default -> { }
        }
        // arena: beheer (zoals vroeger) of terug naar de hub
        if (args.length >= 1 && sender.hasPermission("kitpvp.beheer") && List.of("aftellen", "bouw", "zelftest", "status").contains(args[0].toLowerCase())) {
            beheer(sender, args);
            return true;
        }
        if (sender instanceof Player p) {
            Toestand t = spelers.van(p.getUniqueId());
            if (t.inArena() && gevecht.blokkeer(p)) return true;
            naarHub(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    private Modus zoekModus(String s) {
        String z = s.toLowerCase();
        for (Modus m : modi.alle()) if (m.id().equals(z) || m.naam().toLowerCase().replace(" ", "").contains(z)) return m;
        return null;
    }

    /** Console/OP: kitpvp zelftest | test | bouw [hub|arena|kasteel|alles] | kaart | status | aftellen [s]. ({@code kitpvp zelda …} = {@code zelda …}.) */
    private void beheer(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kitpvp.beheer")) {
            sender.sendMessage("Geen toegang.");
            return;
        }
        String sub = args.length >= 1 ? args[0].toLowerCase() : "status";
        switch (sub) {
            case "aftellen" -> {
                int n = 5;
                try {
                    if (args.length >= 2) n = Math.max(1, Math.min(10, Integer.parseInt(args[1])));
                } catch (NumberFormatException ignored) {
                }
                aftellen(n);
                sender.sendMessage("aftellen " + n + " s gestart voor " + Bukkit.getOnlinePlayers().size() + " spelers");
            }
            case "bouw" -> {
                Set<String> wat = new HashSet<>();
                for (int i = 1; i < args.length; i++) wat.add(args[i].toLowerCase());
                if (wat.isEmpty()) wat.add("alles");
                bouw(wat);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Toestand t = spelers.van(p.getUniqueId());
                    if (t.veilig()) terugNaarVeilig(p);
                }
                sender.sendMessage("bouwen gestart: " + String.join(", ", wat) + " (" + bouwer.inRij() + " stappen)");
            }
            case "zelftest" -> {
                List<String> fouten = new ArrayList<>(), info = new ArrayList<>();
                Zelftest.draai(this, fouten, info);
                sender.sendMessage("kitpvp zelftest: " + (fouten.isEmpty() ? "OK" : "FOUT (" + fouten.size() + ")") + " · " + String.join(" ", info));
                for (String f : fouten) sender.sendMessage("  ✗ " + f);
            }
            case "zelda" -> {                                      // verhuisd naar de plugin Zelda: kitpvp zelda … = zelda …
                if (Bukkit.getPluginManager().getPlugin("Zelda") == null) sender.sendMessage("Story of Zelda is een eigen plugin (Zelda) en staat hier niet aan.");
                else Bukkit.dispatchCommand(sender, "zelda " + String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            }
            case "kaart" -> {                                      // kitpvp kaart [begin]: alleen plaatjes waarvan de naam zo begint
                Kaart.alleen = args.length >= 2 ? args[1] : null;
                try {
                    for (String f : kaarten()) sender.sendMessage("kaart: " + f);
                } catch (IOException | RuntimeException e) {
                    sender.sendMessage("kaart mislukt: " + e);
                } finally {
                    Kaart.alleen = null;
                }
            }
            case "test" -> {
                Player p = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (p == null) {
                    sender.sendMessage("kitpvp test <speler>: speler niet online");
                    return;
                }
                List<String> fouten = new ArrayList<>(), stappen = new ArrayList<>();
                Zelftest.loop(this, p, fouten, stappen);
                sender.sendMessage("kitpvp test " + p.getName() + ": " + (fouten.isEmpty() ? "OK" : "FOUT (" + fouten.size() + ")") + " · " + String.join(" → ", stappen));
                for (String f : fouten) sender.sendMessage("  ✗ " + f);
                if (args.length >= 3 && args[2].equalsIgnoreCase("dood")) Zelftest.dood(this, p, sender);
                if (args.length >= 3 && args[2].equalsIgnoreCase("arena")) {          // blijf in het kasteel staan (bv. huisdier testen)
                    kiesModus(p, modi.get("kasteel"));
                    kies(p, "krijger", modi.arena("kasteel"));
                    sender.sendMessage("kitpvp test: " + p.getName() + " staat in het kasteel (stand " + stand(p) + ")");
                }
            }
            default -> sender.sendMessage("kitpvp: grond=" + g + " modi=" + modi.alle().size() + " in-arena=" + spelers.aantalInArena()
                    + " spelers=" + Bukkit.getOnlinePlayers().size() + " bouwen=" + (bouwer != null && bouwer.bezig() ? bouwer.inRij() + " stappen" : "klaar")
                    + " · kitpvp zelftest | test <speler> | bouw [hub|arena|kasteel|alles] | kaart [begin] | aftellen [s] · Story of Zelda: zelda …");
        }
    }

    /** Plaatjes van hub, arena's en kasteel in plugins/KitPvP/kaart/ (om te controleren zonder in te loggen; Zelda: zelda plaatjes). */
    private List<String> kaarten() throws IOException {
        File map = new File(getDataFolder(), "kaart");
        int hx = Hub.HX, hz = Hub.HZ, kz = KasteelModus.KZ;
        List<File> f = new ArrayList<>();
        f.add(Kaart.boven(w, map, "hub-boven", hx - 36, hz - 36, hx + 36, hz + 36, g - 2, g + 22, 6));
        f.add(Kaart.voor(w, map, "hub-west", -1, 0, hx + 21, hz - 30, hz + 30, g - 1, g + 18, 60, 8));
        f.add(Kaart.voor(w, map, "hub-zuid", 0, 1, hz + 8, hx - 30, hx + 30, g - 1, g + 18, 30, 8));
        f.add(Kaart.voor(w, map, "hub-noord", 0, -1, hz - 8, hx - 30, hx + 30, g - 1, g + 18, 30, 8));
        f.add(Kaart.voor(w, map, "hub-oost", 1, 0, hx - 21, hz - 30, hz + 30, g - 1, g + 18, 60, 8));
        f.add(Kaart.boven(w, map, "arena-boven", -52, -62, 52, 136, g - 2, g + 20, 3));
        f.add(Kaart.boven(w, map, "kasteel-boven", -44, kz - 40, 44, kz + 58, g - 3, g + 22, 5));
        f.add(Kaart.voor(w, map, "kasteel-noord", 0, -1, kz + 30, -44, 44, g - 3, g + 22, 80, 6));
        f.add(Kaart.voor(w, map, "kasteel-veld", 0, -1, kz + 13, -26, 26, g - 1, g + 14, 30, 10));
        f.add(Kaart.voor(w, map, "kitruimte-arena", 0, 1, -58, -10, 10, g + 7, g + 17, 30, 12));
        List<String> uit = new ArrayList<>();
        for (File x : f) if (x != null) uit.add(x.getPath());
        return uit;
    }

    // gegevens voor de zelftest
    Bouwer bouwer() { return bouwer; }
    Gevecht gevecht() { return gevecht; }
    Stats stats() { return stats; }
    List<TextDisplay> ranglijsten() { return ranglijsten; }
    Bots bots() { return bots; }

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
