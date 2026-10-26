package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Bots in de oefenarena: zombies, husks en skeletten in harnas (tag {@code kitpvp-bot}, niet opgeslagen, branden niet,
 * geen drops). 1 + 2 per speler in de oefenarena (max {@code bots-max}), weg na 30 s leeg; ze vallen alleen spelers in
 * de oefenarena aan en blijven binnen.
 */
final class Bots {

    static final String TAG = "kitpvp-bot";

    private final KitPvP plugin;
    private final Random rnd = new Random();
    private long leegSinds = 0;

    Bots(KitPvP plugin) {
        this.plugin = plugin;
    }

    static boolean is(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG);
    }

    List<LivingEntity> alle(World w) {
        List<LivingEntity> l = new ArrayList<>();
        if (w == null) return l;
        for (LivingEntity e : w.getLivingEntities()) if (is(e) && !e.isDead()) l.add(e);
        return l;
    }

    private void spawn(World w, List<Location> plekken, Set<UUID> spelers) {
        if (plekken.isEmpty()) return;
        Location l = plekken.stream().max(Comparator.comparingDouble(s -> {
            double min = 1e9;
            for (UUID id : spelers) {
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
            m.addScoreboardTag(TAG);
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

    /** Elke seconde. */
    void tik(World w, int g, Arena arena, long seconden) {
        Set<UUID> in = plugin.spelers.inArena(arena.id);
        in.removeIf(id -> Bukkit.getPlayer(id) == null);
        List<LivingEntity> nu = alle(w);
        long t = System.currentTimeMillis();
        if (in.isEmpty()) {
            if (leegSinds == 0) leegSinds = t;
            if (t - leegSinds > 30_000L) nu.forEach(Entity::remove);
            return;
        }
        leegSinds = 0;
        int max = Math.max(1, plugin.getConfig().getInt("bots-max", 6));
        int gewenst = Math.min(max, 1 + 2 * in.size());
        if (nu.size() < gewenst && seconden % 2 == 0) spawn(w, arena.spawns, in);
        for (LivingEntity b : nu) {
            if (!ArenaModus.inOefenarena(b.getLocation(), g) && !arena.spawns.isEmpty())
                b.teleport(arena.spawns.get(rnd.nextInt(arena.spawns.size())));
            if (b instanceof org.bukkit.entity.Mob m && (!(m.getTarget() instanceof Player pt) || !in.contains(pt.getUniqueId()))) {
                Player dichtst = null;
                double best = 1e9;
                for (UUID id : in) {
                    Player q = Bukkit.getPlayer(id);
                    if (q == null || plugin.beschermd(id)) continue;
                    double d = q.getLocation().distanceSquared(b.getLocation());
                    if (d < best) {
                        best = d;
                        dichtst = q;
                    }
                }
                m.setTarget(dichtst);
            }
        }
    }
}
