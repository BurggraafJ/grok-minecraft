package nl.burggraaf.mc.huisdieren;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Huisdieren: getemde dieren (met eigenaar) gaan niet dood. Dodelijke schade = uitgeteld (KO): 1 HP, onkwetsbaar,
 * zitten (wolf/kat/papegaai) of AI uit, deeltjes en een label "💤 Rust uit… m:ss". Na herstel-seconden of door voeren
 * met zijn eigen voedsel: vol leven, alles terug, eigenaar krijgt een melding. Void, lava en vastzitten: eerst naar het
 * home 'home' (Essentials) of het bed van de eigenaar. /kill (oorzaak KILL) werkt gewoon. Gewonde dieren tonen een
 * HP-label. KO-stand staat in de PDC van het dier en overleeft een herstart.
 */
public final class Huisdieren extends JavaPlugin implements Listener {

    private NamespacedKey koTot, zat, eigenNaam, naamZichtbaar, hadAi;
    private final Set<UUID> volgen = new HashSet<>();   // KO of gewond: label bijwerken

    @Override
    public void onEnable() {
        saveDefaultConfig();
        koTot = new NamespacedKey(this, "ko-tot");
        zat = new NamespacedKey(this, "zat");
        eigenNaam = new NamespacedKey(this, "eigen-naam");
        naamZichtbaar = new NamespacedKey(this, "naam-zichtbaar");
        hadAi = new NamespacedKey(this, "had-ai");
        getServer().getPluginManager().registerEvents(this, this);
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) bekijk(e);
        Bukkit.getScheduler().runTaskTimer(this, this::tik, 20L, 20L);
    }

    @Override
    public void onDisable() {
        // labels van gewonde (niet-KO) dieren terugzetten; KO-dieren houden hun label tot herstel na de herstart
        for (UUID id : volgen) {
            if (Bukkit.getEntity(id) instanceof LivingEntity le && !isKo(le)) zetNaamTerug(le);
        }
    }

    private boolean aan() {
        return getConfig().getBoolean("aan", true);
    }

    private static boolean huisdier(Entity e) {
        return e instanceof Tameable t && t.isTamed() && t.getOwnerUniqueId() != null && e instanceof LivingEntity;
    }

    private boolean isKo(LivingEntity e) {
        return e.getPersistentDataContainer().has(koTot, PersistentDataType.LONG);
    }

    private void bekijk(Entity e) {
        if (!huisdier(e)) return;
        LivingEntity le = (LivingEntity) e;
        if (isKo(le) || gewond(le) || le.getPersistentDataContainer().has(eigenNaam)) volgen.add(e.getUniqueId());
    }

    @EventHandler
    public void onLaad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) bekijk(en);
    }

    private static double max(LivingEntity e) {
        var a = e.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    private static boolean gewond(LivingEntity e) {
        return e.getHealth() < max(e) - 0.01;
    }

    // ── schade ──────────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSchade(EntityDamageEvent e) {
        if (!huisdier(e.getEntity())) return;
        LivingEntity dier = (LivingEntity) e.getEntity();
        EntityDamageEvent.DamageCause oorzaak = e.getCause();
        if (oorzaak == EntityDamageEvent.DamageCause.KILL) return;             // /kill van een admin werkt gewoon
        if (isKo(dier)) {
            e.setCancelled(true);
            if (gevaarlijk(oorzaak)) naarHuis(dier);
            return;
        }
        if (!aan()) return;
        boolean gevaar = gevaarlijk(oorzaak);
        if (gevaar || e.getFinalDamage() >= dier.getHealth()) {
            e.setCancelled(true);
            if (gevaar) naarHuis(dier);
            knockOut(dier);
            return;
        }
        volgen.add(dier.getUniqueId());
        Bukkit.getScheduler().runTask(this, () -> label(dier));
    }

    private static boolean gevaarlijk(EntityDamageEvent.DamageCause c) {
        return c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.LAVA
                || c == EntityDamageEvent.DamageCause.SUFFOCATION;
    }

    @EventHandler(ignoreCancelled = true)
    public void onGenees(EntityRegainHealthEvent e) {
        if (huisdier(e.getEntity())) {
            volgen.add(e.getEntity().getUniqueId());
            Bukkit.getScheduler().runTask(this, () -> label((LivingEntity) e.getEntity()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDoel(EntityTargetEvent e) {
        if (e.getEntity() instanceof LivingEntity le && huisdier(le) && isKo(le)) e.setCancelled(true);
    }

    private void knockOut(LivingEntity dier) {
        PersistentDataContainer pdc = dier.getPersistentDataContainer();
        bewaarNaam(dier);
        pdc.set(koTot, PersistentDataType.LONG, System.currentTimeMillis() + getConfig().getLong("herstel-seconden", 180) * 1000L);
        dier.setHealth(Math.min(1.0, max(dier)));
        dier.setFireTicks(0);
        dier.setFallDistance(0);
        dier.setInvulnerable(true);
        if (dier instanceof Sittable s) {
            pdc.set(zat, PersistentDataType.BYTE, (byte) (s.isSitting() ? 1 : 0));
            s.setSitting(true);
        } else if (dier instanceof Mob m) {
            pdc.set(hadAi, PersistentDataType.BYTE, (byte) (m.hasAI() ? 1 : 0));
            m.eject();
            m.setAI(false);
        }
        if (dier instanceof Mob m) m.setTarget(null);
        volgen.add(dier.getUniqueId());
        dier.getWorld().playSound(dier.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.6f);
        label(dier);
        Player eigenaar = eigenaar(dier);
        if (eigenaar != null) {
            eigenaar.sendActionBar(Component.text("💤 " + naam(dier) + " is uitgeteld en rust even uit (voeren helpt).", NamedTextColor.YELLOW));
        }
    }

    private void herstel(LivingEntity dier, boolean gevoerd) {
        PersistentDataContainer pdc = dier.getPersistentDataContainer();
        pdc.remove(koTot);
        dier.setInvulnerable(false);
        dier.setHealth(max(dier));
        if (dier instanceof Sittable s) {
            Byte b = pdc.get(zat, PersistentDataType.BYTE);
            s.setSitting(b != null && b == 1);
            pdc.remove(zat);
        } else if (dier instanceof Mob m) {
            Byte b = pdc.get(hadAi, PersistentDataType.BYTE);
            m.setAI(b == null || b == 1);
            pdc.remove(hadAi);
        }
        zetNaamTerug(dier);
        volgen.remove(dier.getUniqueId());
        dier.getWorld().spawnParticle(Particle.HEART, dier.getLocation().add(0, dier.getHeight() + 0.3, 0), 6, 0.4, 0.3, 0.4);
        dier.getWorld().playSound(dier.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
        Player eigenaar = eigenaar(dier);
        if (eigenaar != null) {
            eigenaar.sendActionBar(Component.text("🐾 " + naam(dier) + " is weer helemaal fit" + (gevoerd ? " (gevoerd)!" : "!"), NamedTextColor.GREEN));
        }
    }

    // ── voeren ──────────────────────────────────────────────────────────────────────────────

    private static final Set<Material> PAARDENVOER = Set.of(Material.WHEAT, Material.SUGAR, Material.APPLE, Material.HAY_BLOCK,
            Material.GOLDEN_CARROT, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE, Material.CARROT, Material.CACTUS);

    private static boolean voer(LivingEntity dier, ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        if (dier instanceof Animals a && a.isBreedItem(it)) return true;
        if (dier instanceof Parrot) return it.getType().name().endsWith("_SEEDS");
        return dier instanceof org.bukkit.entity.AbstractHorse && PAARDENVOER.contains(it.getType());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onVoeren(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof LivingEntity dier) || !huisdier(dier)) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItem(e.getHand());
        if (it != null && it.getType() == Material.NAME_TAG) {                 // nieuwe naam: na deze tik overnemen
            Bukkit.getScheduler().runTask(this, () -> {
                dier.getPersistentDataContainer().remove(eigenNaam);
                if (volgen.contains(dier.getUniqueId())) {
                    bewaarNaam(dier);
                    label(dier);
                }
            });
            return;
        }
        if (!isKo(dier)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND && e.getHand() != EquipmentSlot.OFF_HAND) return;
        if (!voer(dier, it)) {
            p.sendActionBar(Component.text("💤 " + naam(dier) + " rust uit. Voer hem zijn eigen eten om hem meteen op te lappen.", NamedTextColor.YELLOW));
            return;
        }
        if (p.getGameMode() != GameMode.CREATIVE) it.setAmount(it.getAmount() - 1);
        dier.getWorld().playSound(dier.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.7f, 1.2f);
        herstel(dier, true);
    }

    // ── label ───────────────────────────────────────────────────────────────────────────────

    private void tik() {
        long nu = System.currentTimeMillis();
        for (UUID id : new HashSet<>(volgen)) {
            if (!(Bukkit.getEntity(id) instanceof LivingEntity dier) || !dier.isValid()) {
                if (Bukkit.getEntity(id) == null) continue;                       // niet geladen: later weer
                volgen.remove(id);
                continue;
            }
            Long tot = dier.getPersistentDataContainer().get(koTot, PersistentDataType.LONG);
            if (tot != null) {
                if (nu >= tot) {
                    herstel(dier, false);
                    continue;
                }
                dier.getWorld().spawnParticle(Particle.CLOUD, dier.getLocation().add(0, dier.getHeight() + 0.2, 0), 2, 0.2, 0.1, 0.2, 0.0);
                if (dier instanceof Sittable s && !s.isSitting()) s.setSitting(true);
            }
            label(dier);
        }
    }

    private void bewaarNaam(LivingEntity dier) {
        PersistentDataContainer pdc = dier.getPersistentDataContainer();
        if (pdc.has(eigenNaam)) return;
        Component n = dier.customName();
        pdc.set(eigenNaam, PersistentDataType.STRING, n == null ? "" : GsonComponentSerializer.gson().serialize(n));
        pdc.set(naamZichtbaar, PersistentDataType.BYTE, (byte) (dier.isCustomNameVisible() ? 1 : 0));
    }

    private void zetNaamTerug(LivingEntity dier) {
        PersistentDataContainer pdc = dier.getPersistentDataContainer();
        String s = pdc.get(eigenNaam, PersistentDataType.STRING);
        if (s == null) return;
        dier.customName(s.isEmpty() ? null : GsonComponentSerializer.gson().deserialize(s));
        Byte z = pdc.get(naamZichtbaar, PersistentDataType.BYTE);
        dier.setCustomNameVisible(z != null && z == 1);
        pdc.remove(eigenNaam);
        pdc.remove(naamZichtbaar);
    }

    private String naam(LivingEntity dier) {
        String s = dier.getPersistentDataContainer().get(eigenNaam, PersistentDataType.STRING);
        if (s != null && !s.isEmpty()) return PlainTextComponentSerializer.plainText().serialize(GsonComponentSerializer.gson().deserialize(s));
        if (s == null && dier.customName() != null) return PlainTextComponentSerializer.plainText().serialize(dier.customName());
        return "Je " + dier.getType().name().toLowerCase().replace('_', ' ');
    }

    private void label(LivingEntity dier) {
        if (!dier.isValid()) return;
        Long tot = dier.getPersistentDataContainer().get(koTot, PersistentDataType.LONG);
        if (tot == null && !gewond(dier)) {
            zetNaamTerug(dier);
            volgen.remove(dier.getUniqueId());
            return;
        }
        bewaarNaam(dier);
        String s = dier.getPersistentDataContainer().get(eigenNaam, PersistentDataType.STRING);
        Component naam = s == null || s.isEmpty() ? Component.empty() : GsonComponentSerializer.gson().deserialize(s).append(Component.text(" "));
        Component tekst;
        if (tot != null) {
            long rest = Math.max(0, tot - System.currentTimeMillis()) / 1000;
            tekst = naam.append(Component.text("💤 Rust uit… " + rest / 60 + ":" + String.format("%02d", rest % 60), NamedTextColor.YELLOW));
        } else {
            int hp = (int) Math.ceil(dier.getHealth()), mx = (int) Math.round(max(dier));
            int vol = (int) Math.round(10.0 * dier.getHealth() / max(dier));
            tekst = naam.append(Component.text("❤".repeat(Math.max(0, vol)), NamedTextColor.RED))
                    .append(Component.text("❤".repeat(Math.max(0, 10 - vol)), NamedTextColor.DARK_GRAY))
                    .append(Component.text(" " + hp + "/" + mx, NamedTextColor.GRAY));
        }
        dier.customName(tekst);
        dier.setCustomNameVisible(true);
    }

    // ── naar huis ───────────────────────────────────────────────────────────────────────────

    private Player eigenaar(LivingEntity dier) {
        UUID id = ((Tameable) dier).getOwnerUniqueId();
        return id == null ? null : Bukkit.getPlayer(id);
    }

    private void naarHuis(LivingEntity dier) {
        UUID id = ((Tameable) dier).getOwnerUniqueId();
        Location doel = null;
        File f = new File(getDataFolder().getParentFile(), "Essentials/userdata/" + id + ".yml");
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String w = y.getString("homes.home.world-name", y.getString("homes.home.world"));
            World wereld = w == null ? null : Bukkit.getWorld(w);
            if (wereld == null && w != null) {
                try {
                    wereld = Bukkit.getWorld(UUID.fromString(w));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (wereld != null) doel = new Location(wereld, y.getDouble("homes.home.x"), y.getDouble("homes.home.y"), y.getDouble("homes.home.z"));
        }
        if (doel == null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            doel = op.getRespawnLocation();
        }
        if (doel == null) doel = Bukkit.getWorlds().get(0).getSpawnLocation();
        dier.eject();
        dier.leaveVehicle();
        dier.setFallDistance(0);
        dier.setFireTicks(0);
        dier.teleport(doel);
        Player eigenaar = eigenaar(dier);
        if (eigenaar != null) eigenaar.sendMessage(Component.text("🐾 " + naam(dier) + " is in veiligheid gebracht (bij je home/bed).", NamedTextColor.YELLOW));
    }
}
