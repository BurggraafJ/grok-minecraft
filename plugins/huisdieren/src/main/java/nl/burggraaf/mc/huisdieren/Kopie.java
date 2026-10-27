package nl.burggraaf.mc.huisdieren;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Lobby- en kitpvp-kant: een kopie van je huisdier (uit het ondertekende cookie van survival, hier bewaard) loopt met
 * je mee. Niet opgeslagen in de wereld, geen drops, niet te berijden of te voeren; het echte dier blijft op survival.
 * Lobby: onkwetsbaar en vecht niet. KitPvP: een wolf vecht aan jouw kant (vanilla: wie jou slaat en wie jij slaat),
 * alleen in de arena's. Uitgeschakeld = hij blijft liggen (zit stil, geen AI, niet te raken) met een klein
 * revive-balkje boven zijn hoofd dat in {@link #KO_MS} vol loopt; vol, of een nieuwe ronde (respawn, terug naar de
 * hub of kitruimte), dan staat hij weer op en vecht mee. Andere dieren lopen alleen mee en zijn onkwetsbaar.
 */
final class Kopie implements Listener {

    static final String TAG = "huisdier-kopie";
    static final String BALK_TAG = "huisdier-balk";
    static final long KO_MS = 15_000L;                              // zo lang ligt een uitgeschakelde wolf
    private static final int VAKJES = 10;
    private static final String TEAM = "huisdier_kopie";

    private final Huisdieren plugin;
    private final boolean kitpvp;
    private final byte[] sleutel;
    private final NamespacedKey kopieVan;
    private final File map, verborgenBestand;
    private final Map<UUID, UUID> kopie = new HashMap<>();          // speler → entity
    private final Map<UUID, Pakket> pakket = new HashMap<>();       // speler → laatst bekende briefje
    private final Map<UUID, Long> pogingTot = new HashMap<>();
    private final Map<UUID, String> stand = new HashMap<>();
    private final Map<UUID, Long> ko = new HashMap<>();             // kitpvp: speler → tijd waarop zijn wolf weer opstaat
    private final Map<UUID, UUID> balk = new HashMap<>();           // speler → revive-balkje (TextDisplay)
    private final Set<UUID> verborgen = new HashSet<>();

    Kopie(Huisdieren plugin, boolean kitpvp, byte[] sleutel) {
        this.plugin = plugin;
        this.kitpvp = kitpvp;
        this.sleutel = sleutel;
        this.kopieVan = new NamespacedKey(plugin, "kopie-van");
        this.map = new File(plugin.getDataFolder(), "kopie");
        this.verborgenBestand = new File(plugin.getDataFolder(), "verborgen.yml");
        for (String s : YamlConfiguration.loadConfiguration(verborgenBestand).getStringList("verborgen")) {
            try {
                verborgen.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    void start() {
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (isKopie(e) || e.getScoreboardTags().contains(BALK_TAG)) e.remove();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tik, 20L, 10L);
        for (Player p : Bukkit.getOnlinePlayers()) binnen(p);
    }

    void stop() {
        for (UUID s : new HashSet<>(balk.keySet())) balkWeg(s);
        ko.clear();
        for (UUID id : kopie.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        kopie.clear();
    }

    boolean isKopie(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG);
    }

    private UUID eigenaarVan(Entity e) {
        String s = e.getPersistentDataContainer().get(kopieVan, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ── binnenkomen: briefje lezen ──────────────────────────────────────────────────────────

    private File bestand(UUID id) {
        return new File(map, id + ".dat");
    }

    private Pakket bewaard(UUID id) {
        Pakket p = pakket.get(id);
        if (p != null || sleutel == null) return p;
        File f = bestand(id);
        if (!f.isFile()) return null;
        try {
            p = Pakket.lees(sleutel, id, Files.readAllBytes(f.toPath()));
        } catch (IOException e) {
            return null;
        }
        if (p != null) pakket.put(id, p);
        return p;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        binnen(e.getPlayer());
    }

    private void binnen(Player p) {
        UUID id = p.getUniqueId();
        ko.remove(id);
        stand.remove(id);
        ruimSchoudersOp(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) zet(p); }, 30L);
        if (sleutel == null) return;
        try {
            p.retrieveCookie(Reizen.COOKIE).orTimeout(8, TimeUnit.SECONDS).whenComplete((b, fout) -> {
                if (fout != null || b == null || b.length == 0) return;
                Bukkit.getScheduler().runTask(plugin, () -> ontvang(p, b));
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("cookie: " + ex);
        }
    }

    private void ontvang(Player p, byte[] b) {
        UUID id = p.getUniqueId();
        Pakket nieuw = Pakket.lees(sleutel, id, b);
        if (nieuw == null) {
            plugin.getLogger().warning("ongeldig huisdier-briefje van " + p.getName() + " genegeerd");
            return;
        }
        Pakket oud = bewaard(id);
        if (oud != null && oud.tijd >= nieuw.tijd) return;
        pakket.put(id, nieuw);
        try {
            map.mkdirs();
            Files.write(bestand(id).toPath(), b);
        } catch (IOException ex) {
            plugin.getLogger().warning("huisdier bewaren: " + ex.getMessage());
        }
        if (p.isOnline()) zet(p);
    }

    // ── kopie zetten en bijhouden ───────────────────────────────────────────────────────────

    private Entity huidige(UUID speler) {
        UUID e = kopie.get(speler);
        Entity en = e == null ? null : Bukkit.getEntity(e);
        return en != null && en.isValid() ? en : null;
    }

    private void weg(UUID speler) {
        ko.remove(speler);
        balkWeg(speler);
        UUID e = kopie.remove(speler);
        Entity en = e == null ? null : Bukkit.getEntity(e);
        if (en != null) en.remove();
    }

    /** (Opnieuw) de kopie neerzetten bij de speler, of weghalen als hij er niet hoort. */
    void zet(Player p) {
        UUID id = p.getUniqueId();
        weg(id);
        Pakket pk = bewaard(id);
        if (pk == null || pk.leeg() || verborgen.contains(id) || p.isDead() || geenDier(id)) return;
        Entity e;
        try {
            e = Bukkit.getUnsafe().deserializeEntity(pk.data, p.getWorld(), false);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("huisdier van " + p.getName() + " kan hier niet: " + ex);
            return;
        }
        if (!(e instanceof LivingEntity le) || !(e instanceof Tameable t) || !Reizen.reisbaarType(e)) {
            plugin.getLogger().warning("huisdier van " + p.getName() + " is geen tam dier, genegeerd");
            return;
        }
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
        e.getPersistentDataContainer().set(kopieVan, PersistentDataType.STRING, id.toString());
        plugin.maakSchoon(le);
        t.setTamed(true);
        t.setOwner(p);
        le.setRemoveWhenFarAway(false);
        le.setFireTicks(0);
        le.setFallDistance(0);
        var max = le.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) le.setHealth(max.getValue());
        if (e instanceof Sittable s) s.setSitting(false);
        if (e instanceof ChestedHorse ch) ch.setCarryingChest(false);
        if (e instanceof Mob m) m.setTarget(null);
        le.setInvulnerable(!vecht(le, id));
        Location plek = p.getLocation().clone().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.2));
        if (!plek.getBlock().isPassable()) plek = p.getLocation();
        if (!e.spawnAt(plek, CreatureSpawnEvent.SpawnReason.CUSTOM)) return;
        if (le.isLeashed()) le.setLeashHolder(null);
        team(le);
        kopie.put(id, e.getUniqueId());
    }

    private void team(Entity e) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(TEAM);
        if (t == null) {
            t = sb.registerNewTeam(TEAM);
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);   // nooit van een parkourblok duwen
        }
        t.addEntity(e);
    }

    /** Vecht deze kopie nu mee? Alleen een wolf, alleen in kitpvp, alleen als de eigenaar in een arena staat. */
    private boolean vecht(Entity e, UUID eigenaar) {
        return kitpvp && e instanceof Wolf && vechtStand(standVan(eigenaar));
    }

    /** KitPvP-standen waarin gevochten wordt; al het andere (wacht = hub/kitruimte, of een nieuwe modus) is veilig. */
    private static boolean vechtStand(String s) {
        return s.equals("pvp") || s.equals("bots");
    }

    /** KitPvP-stand {@code geen}: hier loopt geen huisdier mee (Story of Zelda). */
    private boolean geenDier(UUID speler) {
        return kitpvp && standVan(speler).equals("geen");
    }

    private String standVan(UUID speler) {
        Player p = Bukkit.getPlayer(speler);
        if (p == null) return "wacht";
        List<MetadataValue> m = p.getMetadata("kitpvp-stand");
        return m.isEmpty() ? "wacht" : m.get(0).asString();
    }

    private void tik() {
        long nu = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (kitpvp) {
                String s = standVan(id), vorig = stand.put(id, s);
                if (vorig != null && vechtStand(vorig) && !vechtStand(s) && ko.containsKey(id)) {   // nieuwe ronde
                    pogingTot.remove(id);
                    Entity k = huidige(id);
                    if (k instanceof LivingEntity kl) opstaan(id, kl);
                    else ko.remove(id);
                }
            }
            if (geenDier(id)) {                                              // kitpvp-modus zonder huisdier (Zelda)
                if (kopie.containsKey(id)) weg(id);
                continue;
            }
            Entity e = huidige(id);
            if (e == null) {
                ko.remove(id);
                balkWeg(id);
                if (p.isDead() || verborgen.contains(id)) continue;
                if (opSchouder(p)) continue;
                Pakket pk = bewaard(id);
                if (pk == null || pk.leeg()) continue;
                if (nu < pogingTot.getOrDefault(id, 0L)) continue;
                pogingTot.put(id, nu + 3000L);
                zet(p);
                continue;
            }
            LivingEntity le = (LivingEntity) e;
            Long tot = ko.get(id);
            if (tot != null) {                                                 // uitgeschakeld: blijft liggen
                boolean ver = !e.getWorld().equals(p.getWorld()) || e.getLocation().distanceSquared(p.getLocation()) > 48 * 48;
                if (nu >= tot || ver) {
                    opstaan(id, le);
                    if (ver) e.teleport(p.getLocation());
                } else {
                    if (le instanceof Mob m && m.getTarget() != null) m.setTarget(null);
                    if (le instanceof Sittable s && !s.isSitting()) s.setSitting(true);
                    werkBalkBij(id, le, tot, nu);
                }
                continue;
            }
            boolean v = vecht(le, id);
            if (le.isInvulnerable() == v) le.setInvulnerable(!v);
            if (le instanceof Mob m && m.getTarget() != null && (!v || !magDoel(id, m.getTarget()))) m.setTarget(null);
            if (le instanceof Sittable s && s.isSitting()) s.setSitting(false);
            if (!e.getWorld().equals(p.getWorld()) || e.getLocation().distanceSquared(p.getLocation()) > 20 * 20) {
                Location l = p.getLocation();
                e.teleport(l.getBlock().isPassable() ? l : l.clone().add(0, 1, 0));
                le.setFallDistance(0);
            } else if (le instanceof AbstractHorse h && le.getLocation().distanceSquared(p.getLocation()) > 4 * 4) {
                h.getPathfinder().moveTo(p, 1.6);                              // paarden en lama's volgen niet vanzelf
            }
        }
    }

    // ── kitpvp: meevechten en uitgeschakeld raken ───────────────────────────────────────────

    private static Player speler(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private boolean pvp(UUID speler) {
        return standVan(speler).equals("pvp");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSchade(EntityDamageEvent e) {
        Entity en = e.getEntity();
        if (!isKopie(en)) return;
        LivingEntity dier = (LivingEntity) en;
        UUID eig = eigenaarVan(en);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID || eig == null) {
            e.setCancelled(true);
            if (eig != null) Bukkit.getScheduler().runTask(plugin, () -> weg(eig));     // komt vanzelf terug bij de speler
            else en.remove();
            return;
        }
        if (ko.containsKey(eig) || !vecht(dier, eig) || e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager();
            Player a = speler(d);
            if (a == null && d instanceof Tameable t && isKopie(d)) a = Bukkit.getPlayer(t.getOwnerUniqueId());
            if (a != null && (a.getUniqueId().equals(eig) || !pvp(eig) || !pvp(a.getUniqueId()))) {
                e.setCancelled(true);
                return;
            }
        }
        if (e.getFinalDamage() >= dier.getHealth()) {
            e.setCancelled(true);
            uitschakelen(eig, dier);
        }
    }

    /** Uitgeschakeld: blijft liggen (zitten, AI uit, onkwetsbaar) met een revive-balkje; geen melding. */
    private void uitschakelen(UUID eig, LivingEntity dier) {
        long nu = System.currentTimeMillis();
        ko.put(eig, nu + KO_MS);
        dier.setInvulnerable(true);
        dier.setFireTicks(0);
        dier.setVelocity(new org.bukkit.util.Vector(0, Math.min(0, dier.getVelocity().getY()), 0));
        if (dier instanceof Mob m) m.setTarget(null);
        if (dier instanceof Sittable s) s.setSitting(true);
        if (dier instanceof Mob m) m.setAI(false);
        dier.getWorld().spawnParticle(Particle.POOF, dier.getLocation().add(0, dier.getHeight() / 2, 0), 6, 0.25, 0.2, 0.25, 0.01);
        werkBalkBij(eig, dier, nu + KO_MS, nu);
    }

    /** Weer op: balkje weg, AI aan, vol leven; vecht mee als de eigenaar in een arena staat. */
    private void opstaan(UUID eig, LivingEntity dier) {
        ko.remove(eig);
        balkWeg(eig);
        if (!dier.isValid()) return;
        if (dier instanceof Mob m) m.setAI(true);
        if (dier instanceof Sittable s) s.setSitting(false);
        var max = dier.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) dier.setHealth(max.getValue());
        dier.setInvulnerable(!vecht(dier, eig));
        dier.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, dier.getLocation().add(0, dier.getHeight() + 0.2, 0), 4, 0.25, 0.15, 0.25, 0.0);
    }

    /** Het balkje: grijze blokjes die groen vollopen. */
    static Component balkTekst(long tot, long nu) {
        double klaar = 1.0 - Math.max(0, Math.min(KO_MS, tot - nu)) / (double) KO_MS;
        int vol = (int) Math.floor(klaar * VAKJES + 1e-9);
        return Component.text("■".repeat(vol), NamedTextColor.GREEN)
                .append(Component.text("■".repeat(VAKJES - vol), NamedTextColor.DARK_GRAY));
    }

    private void werkBalkBij(UUID eig, LivingEntity dier, long tot, long nu) {
        Location l = dier.getLocation().add(0, dier.getHeight() + (dier.isCustomNameVisible() ? 0.75 : 0.3), 0);
        UUID bid = balk.get(eig);
        Entity b = bid == null ? null : Bukkit.getEntity(bid);
        TextDisplay d = b instanceof TextDisplay td && td.isValid() ? td : null;
        if (d == null) {
            d = dier.getWorld().spawn(l, TextDisplay.class, x -> {
                x.setPersistent(false);
                x.addScoreboardTag(BALK_TAG);
                x.setBillboard(Display.Billboard.CENTER);
                x.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                x.setShadowed(false);
                x.setSeeThrough(false);
                x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            });
            balk.put(eig, d.getUniqueId());
        } else if (d.getLocation().distanceSquared(l) > 0.04) {
            d.teleport(l);
        }
        d.text(balkTekst(tot, nu));
    }

    private void balkWeg(UUID eig) {
        UUID bid = balk.remove(eig);
        Entity b = bid == null ? null : Bukkit.getEntity(bid);
        if (b != null) b.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDoel(EntityTargetEvent e) {
        if (!isKopie(e.getEntity()) || e.getTarget() == null) return;
        UUID eig = eigenaarVan(e.getEntity());
        Entity doel = e.getTarget();
        if (eig == null || ko.containsKey(eig) || !vecht(e.getEntity(), eig) || doel.getUniqueId().equals(eig)) {
            e.setCancelled(true);
            return;
        }
        if (isKopie(doel) && eig.equals(eigenaarVan(doel))) {
            e.setCancelled(true);
            return;
        }
        if (!magDoel(eig, doel)) e.setCancelled(true);
    }

    /** Een speler (of zijn huisdier) is alleen een doel als jullie allebei in een PvP-arena staan. */
    private boolean magDoel(UUID eig, Entity doel) {
        if (doel.getUniqueId().equals(eig) || !doel.isValid()) return false;
        UUID tegen = doel instanceof Player q ? q.getUniqueId() : isKopie(doel) ? eigenaarVan(doel) : null;
        if (tegen == null) return true;
        return !tegen.equals(eig) && pvp(eig) && pvp(tegen);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDoodSpeler(PlayerDeathEvent e) {
        weg(e.getPlayer().getUniqueId());                                  // hij gaat met je mee terug
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        pogingTot.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            Entity k = huidige(p.getUniqueId());
            if (k == null) zet(p);
            else if (ko.containsKey(p.getUniqueId()) && k instanceof LivingEntity kl) opstaan(p.getUniqueId(), kl);   // nieuwe ronde
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity k = huidige(p.getUniqueId());
            if (k != null && p.isOnline() && !ko.containsKey(p.getUniqueId()) && (!k.getWorld().equals(p.getWorld()) || k.getLocation().distanceSquared(p.getLocation()) > 8 * 8)) {
                k.teleport(p.getLocation());
                if (k instanceof Mob m) m.setTarget(null);
            }
        }, 2L);
    }

    // ── geen gedoe met kopieën ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAanraken(PlayerInteractEntityEvent e) {
        if (isKopie(e.getRightClicked())) e.setCancelled(true);           // niet zitten, voeren, fokken, berijden
    }

    @EventHandler(ignoreCancelled = true)
    public void onBerijden(EntityMountEvent e) {
        if (isKopie(e.getMount()) || isKopie(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onLos(EntityUnleashEvent e) {
        if (isKopie(e.getEntity())) e.setDropLeash(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaatVallen(EntityDropItemEvent e) {
        if (isKopie(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onDoodKopie(EntityDeathEvent e) {
        if (!isKopie(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler
    public void onLaad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (!isKopie(en)) continue;
            UUID eig = eigenaarVan(en);
            if (eig == null || !en.getUniqueId().equals(kopie.get(eig))) en.remove();
        }
    }

    /** Een papegaai op je schouder is even geen entity; springt hij eraf, dan is hij weer onze kopie. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSchouderEraf(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY || !isKopie(e.getEntity())) return;
        UUID eig = eigenaarVan(e.getEntity());
        if (eig == null || huidige(eig) != null) {
            Bukkit.getScheduler().runTask(plugin, () -> e.getEntity().remove());
            return;
        }
        e.getEntity().setPersistent(false);
        kopie.put(eig, e.getEntity().getUniqueId());
    }

    @SuppressWarnings("deprecation")
    private boolean opSchouder(Player p) {
        return isKopie(p.getShoulderEntityLeft()) || isKopie(p.getShoulderEntityRight());
    }

    @SuppressWarnings("deprecation")
    private void ruimSchoudersOp(Player p) {
        if (isKopie(p.getShoulderEntityLeft())) p.setShoulderEntityLeft(null);
        if (isKopie(p.getShoulderEntityRight())) p.setShoulderEntityRight(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        weg(id);
        ruimSchoudersOp(p);                                                // geen papegaai mee in de spelerdata
        stand.remove(id);
        pogingTot.remove(id);
        pakket.remove(id);
    }

    // ── /huisdier hier: meenemen aan/uit ────────────────────────────────────────────────────

    void wissel(Player p) {
        UUID id = p.getUniqueId();
        if (verborgen.remove(id)) {
            pogingTot.remove(id);
            Pakket pk = bewaard(id);
            if (pk == null || pk.leeg()) {
                p.sendActionBar(Component.text("Geen huisdier mee · kies er een in survival met /huisdier", NamedTextColor.GRAY));
            } else {
                zet(p);
                p.sendActionBar(Component.text("Je huisdier loopt weer mee", NamedTextColor.GRAY));
            }
        } else {
            verborgen.add(id);
            weg(id);
            p.sendActionBar(Component.text("Je huisdier blijft nu thuis · /huisdier = weer mee", NamedTextColor.GRAY));
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("verborgen", verborgen.stream().map(UUID::toString).sorted().toList());
        try {
            plugin.getDataFolder().mkdirs();
            y.save(verborgenBestand);
        } catch (IOException ex) {
            plugin.getLogger().warning("verborgen.yml: " + ex.getMessage());
        }
    }

    String status() {
        int levend = 0;
        for (UUID s : kopie.keySet()) if (huidige(s) != null) levend++;
        File[] f = map.listFiles((d, n) -> n.endsWith(".dat"));
        return "kopieën=" + levend + " bewaard=" + (f == null ? 0 : f.length) + " ko=" + ko.size() + " verborgen=" + verborgen.size()
                + " sleutel=" + (sleutel != null) + " modus=" + (kitpvp ? "kitpvp" : "lobby");
    }

    /**
     * Voor de zelftest: schakel de kopie van deze speler uit en controleer liggen + balkje, daarna opstaan.
     * Geeft "" als alles klopt, anders de fouten.
     */
    String testKo(Player p) {
        UUID id = p.getUniqueId();
        Entity e = huidige(id);
        if (!(e instanceof LivingEntity le)) return " ko:geen-kopie";
        StringBuilder f = new StringBuilder();
        uitschakelen(id, le);
        if (!e.isValid()) f.append(" ko:weg");
        if (!le.isInvulnerable()) f.append(" ko:te-raken");
        if (le instanceof Mob m && m.hasAI()) f.append(" ko:ai-aan");
        if (le instanceof Sittable s && !s.isSitting()) f.append(" ko:zit-niet");
        if (!(balk.get(id) != null && Bukkit.getEntity(balk.get(id)) instanceof TextDisplay)) f.append(" ko:geen-balk");
        String half = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(balkTekst(KO_MS / 2, 0));
        if (half.length() != VAKJES) f.append(" ko:balk-lengte");
        opstaan(id, le);
        if (!e.isValid()) f.append(" op:weg");
        if (le instanceof Mob m && !m.hasAI()) f.append(" op:ai-uit");
        if (le instanceof Sittable s && s.isSitting()) f.append(" op:zit");
        if (balk.containsKey(id)) f.append(" op:balk-blijft");
        return f.toString();
    }

    /** Voor de zelftest: een briefje rechtstreeks toepassen (zoals een cookie). */
    void testOntvang(Player p, byte[] b) {
        ontvang(p, b);
    }
}
