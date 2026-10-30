package nl.burggraaf.mc.zelda;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Wiske, de fee: vliegt naast je schouder mee (een allay zonder eigen wil, alleen voor jou te zien). Praat ze (een
 * regel van «Wiske» op de actiebalk), dan komt ze even voor je zweven en glinstert ze. Rechtsklik op haar = een hint
 * (net als {@code /hint}). Bij een tweede hint vliegt ze een stukje de goede kant op en komt terug ({@link #wijs}).
 * Eén entiteit per speler, niet opgeslagen, onkwetsbaar, tag {@code zelda} + {@code zelda-npc} + {@code zelda-wiske}.
 */
final class ZeldaMetgezel {

    static final String TAG = "zelda-wiske";

    private static final class Fee {
        Allay ent;
        Location pos;
        long praatTot, wijsTot;
        Location wijsNaar;
    }

    private final Plugin plugin;
    private final Map<UUID, Fee> feeen = new HashMap<>();
    /** Hoe fel ze is: het aantal bakens dat weer brandt (elk baken maakt haar feller: meer glans, iets groter). */
    java.util.function.ToIntFunction<Player> licht = p -> 0;
    private long tik;

    ZeldaMetgezel(Plugin plugin) {
        this.plugin = plugin;
    }

    /** De fee van deze speler (null = geen). */
    Allay van(Player p) {
        Fee f = feeen.get(p.getUniqueId());
        return f == null || f.ent == null || !f.ent.isValid() ? null : f.ent;
    }

    boolean isFee(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG);
    }

    /** Van wie is deze fee? */
    UUID eigenaar(Entity e) {
        for (var en : feeen.entrySet()) if (en.getValue().ent != null && en.getValue().ent.equals(e)) return en.getKey();
        return null;
    }

    /** Ze komt even voor je zweven (bij een regel van Wiske). */
    void praat(Player p) {
        Fee f = feeen.get(p.getUniqueId());
        if (f == null) return;
        f.praatTot = System.currentTimeMillis() + 3500;
        if (f.ent != null && f.ent.isValid()) {
            p.spawnParticle(Particle.WAX_OFF, f.ent.getLocation().add(0, 0.4, 0), 6, 0.2, 0.2, 0.2, 0.5);
            p.playSound(f.ent.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, SoundCategory.NEUTRAL, 0.25f, 1.3f);
        }
    }

    /** Vlieg een stukje (hoogstens 9 blokken) naar {@code doel} en kom na 3 s terug; een spoor van licht erachter. */
    void wijs(Player p, Location doel) {
        Fee f = feeen.get(p.getUniqueId());
        if (f == null || doel == null || !doel.getWorld().equals(p.getWorld())) return;
        Vector v = doel.toVector().subtract(p.getEyeLocation().toVector());
        double d = v.length();
        if (d < 1) return;
        Location naar = p.getEyeLocation().add(v.normalize().multiply(Math.min(9, d - 0.5)));
        naar.setY(Math.max(naar.getY(), p.getLocation().getY() + 1.5));
        f.wijsNaar = naar;
        f.wijsTot = System.currentTimeMillis() + 3200;
    }

    /** Elke tik: zorg dat iedere speler in {@code spelers} zijn fee heeft en dat ze meevliegt; de rest weg. */
    void tik(Iterable<Player> spelers) {
        tik++;
        long nu = System.currentTimeMillis();
        java.util.Set<UUID> wie = new java.util.HashSet<>();
        for (Player p : spelers) {
            wie.add(p.getUniqueId());
            Fee f = feeen.computeIfAbsent(p.getUniqueId(), k -> new Fee());
            if (p.isDead()) {
                if (f.ent != null) f.ent.remove();
                f.ent = null;
                continue;
            }
            Location doel = doel(p, f, nu);
            if (f.ent == null || !f.ent.isValid() || !f.ent.getWorld().equals(p.getWorld()) || f.pos == null || f.pos.distanceSquared(doel) > 400) {
                if (f.ent != null) f.ent.remove();
                f.pos = doel.clone();
                f.ent = maak(p, doel);
                continue;
            }
            double lerp = f.wijsNaar != null && nu < f.wijsTot ? 0.12 : 0.3;
            f.pos.add(doel.toVector().subtract(f.pos.toVector()).multiply(lerp));
            Location l = f.pos.clone();
            Vector kijk = p.getEyeLocation().toVector().subtract(l.toVector());
            if (f.wijsNaar != null && nu < f.wijsTot) kijk = f.wijsNaar.toVector().subtract(l.toVector());
            if (kijk.lengthSquared() > 0.01) l.setDirection(kijk);
            f.ent.teleport(l);
            int fel = Math.min(3, licht.applyAsInt(p));
            if (f.wijsNaar != null && nu < f.wijsTot && tik % 2 == 0) p.spawnParticle(Particle.END_ROD, l, 1, 0.02, 0.02, 0.02, 0);
            else if (tik % (40 >> fel) == 0) p.spawnParticle(Particle.END_ROD, l.clone().add(0, 0.3, 0), 1, 0.1 + 0.05 * fel, 0.1, 0.1 + 0.05 * fel, 0.005);
            if (tik % 40 == 0) {                                            // feller = iets groter
                var sc = f.ent.getAttribute(Attribute.SCALE);
                if (sc != null && sc.getBaseValue() != 0.8 + 0.1 * fel) sc.setBaseValue(0.8 + 0.1 * fel);
            }
        }
        for (var it = feeen.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (wie.contains(e.getKey())) continue;
            if (e.getValue().ent != null) e.getValue().ent.remove();
            it.remove();
        }
    }

    /** Waar de fee naartoe wil: naast je linkerschouder, bij praten even voor je, bij wijzen naar het doel. */
    private Location doel(Player p, Fee f, long nu) {
        Location oog = p.getEyeLocation();
        if (f.wijsNaar != null) {
            if (nu < f.wijsTot) return f.wijsNaar.clone();
            f.wijsNaar = null;
        }
        Vector voor = oog.getDirection().setY(0);
        if (voor.lengthSquared() < 1e-4) voor = new Vector(0, 0, 1);
        voor.normalize();
        Vector links = new Vector(voor.getZ(), 0, -voor.getX());           // links van de kijkrichting
        double zweef = Math.sin(tik / 11.0) * 0.12;
        Location l;
        if (nu < f.praatTot) l = oog.clone().add(voor.clone().multiply(1.6)).add(links.clone().multiply(0.7)).add(0, -0.35 + zweef, 0);
        else l = oog.clone().add(links.clone().multiply(0.9)).add(voor.clone().multiply(-0.35)).add(0, -0.2 + zweef, 0);
        return l;
    }

    private Allay maak(Player p, Location l) {
        Allay fee = l.getWorld().spawn(l, Allay.class, a -> {
            a.setPersistent(false);
            a.setVisibleByDefault(false);
            a.addScoreboardTag(ZeldaSpel.TAG);
            a.addScoreboardTag(ZeldaSpel.NPC);
            a.addScoreboardTag(TAG);
            a.setAI(false);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setCollidable(false);
            a.setCanPickupItems(false);
            a.customName(Component.text("Wiske", ZeldaSpel.GROEN));
            a.setCustomNameVisible(false);
            var s = a.getAttribute(Attribute.SCALE);
            if (s != null) s.setBaseValue(0.8);
        });
        p.showEntity(plugin, fee);
        return fee;
    }

    /** Alles weg (plugin uit). */
    void weg() {
        for (Fee f : feeen.values()) if (f.ent != null) f.ent.remove();
        feeen.clear();
    }

    /** Fee van één speler weg (de speler gaat weg uit Zelda). */
    void weg(UUID id) {
        Fee f = feeen.remove(id);
        if (f != null && f.ent != null) f.ent.remove();
    }

    int aantal() {
        int n = 0;
        for (Fee f : feeen.values()) if (f.ent != null && f.ent.isValid()) n++;
        return n;
    }
}
