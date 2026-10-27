package nl.burggraaf.mc.kitpvp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sittable;

/**
 * Je eigen huisdier in Story of Zelda (zie ZELDA.md: je vindt het later in het spel, op de Witte Toppen). Het staat in
 * je voortgang als vlag {@code dier:<soort>} ({@code vos}, {@code sneeuwvos}, {@code kat}, {@code konijn}) met een naam ({@code dier} in zelda.yml). Het loopt met je mee (eigen pad, zonder eigen wil: geen
 * slapen, jagen of vechten), wordt nooit geraakt, en <b>snuffelt</b>: ligt er binnen 14 blokken een verborgen vondst
 * (hartstukje) die jij nog niet hebt, dan loopt het erheen en gaat erbij zitten. Zo helpt het zoeken, ook op eilanden
 * waar je al was. Losstaand van de plugin Huisdieren (je survival-dier loopt gewoon ook mee).
 */
final class ZeldaDier {

    static final String TAG = "zelda-dier";
    /** Uit tot een later eiland het dier in het verhaal brengt (Jelle 27-10: het leidt af). Vlaggen blijven bewaard. */
    static final boolean AAN = false;
    static final List<String> SOORTEN = List.of("vos", "sneeuwvos", "kat", "konijn");   // geen wolf: skeletten vluchten ervoor
    static final List<String> NAMEN = List.of("Pluis", "Vonk", "Sneeuw", "Bram", "Kiek");

    private static final class Maatje {
        Mob ent;
        String soort;
        Location snuffel;
        long snuffelTot, stilSinds;
        Location vorige;
    }

    private final Map<UUID, Maatje> dieren = new HashMap<>();
    private long tik;

    /** De soort uit de vlaggen ({@code dier:vos}), of null. */
    static String soort(ZeldaVoortgang.Stand s) {
        for (String v : s.vlaggen)
            if (v.startsWith("dier:") && SOORTEN.contains(v.substring(5))) return v.substring(5);
        return null;
    }

    static String naam(ZeldaVoortgang.Stand s) {
        return s.dier == null || s.dier.isBlank() ? NAMEN.get(0) : s.dier;
    }

    Mob van(Player p) {
        Maatje m = dieren.get(p.getUniqueId());
        return m == null || m.ent == null || !m.ent.isValid() ? null : m.ent;
    }

    boolean isDier(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG);
    }

    /**
     * Elke 10 tikken. {@code spelers}: wie in Zelda is, met zijn stand; {@code verborgen}: per speler waar nog iets
     * verborgen ligt dat hij niet heeft (dichtbij genoeg om te zoeken).
     */
    void tik(Map<Player, ZeldaVoortgang.Stand> spelers, Function<Player, List<Location>> verborgen) {
        tik++;
        if (!AAN) {
            if (!dieren.isEmpty()) weg();
            return;
        }
        long nu = System.currentTimeMillis();
        Set<UUID> wie = new HashSet<>();
        for (var e : spelers.entrySet()) {
            Player p = e.getKey();
            String soort = soort(e.getValue());
            if (soort == null || p.isDead()) continue;
            wie.add(p.getUniqueId());
            Maatje m = dieren.computeIfAbsent(p.getUniqueId(), k -> new Maatje());
            String naam = naam(e.getValue());
            if (m.ent == null || !m.ent.isValid() || !soort.equals(m.soort) || !m.ent.getWorld().equals(p.getWorld())) {
                if (m.ent != null) m.ent.remove();
                m.soort = soort;
                m.ent = maak(soort, naast(p), naam);
                m.snuffel = null;
                continue;
            }
            if (!Component.text(naam, ZeldaSpel.GROEN).equals(m.ent.customName())) m.ent.customName(Component.text(naam, ZeldaSpel.GROEN));
            double d = m.ent.getLocation().distanceSquared(p.getLocation());
            if (d > 28 * 28) {                                   // te ver (gereisd, gevallen): naar je toe
                m.ent.teleport(naast(p));
                m.snuffel = null;
                continue;
            }
            // snuffelen: om de 2 s kijken of er iets verborgen ligt
            if (tik % 4 == 0 && m.snuffel == null && nu > m.snuffelTot) {
                Location best = null;
                double bd = 14 * 14;
                for (Location l : verborgen.apply(p)) {
                    if (!l.getWorld().equals(p.getWorld())) continue;
                    double a = l.distanceSquared(p.getLocation());
                    if (a < bd) {
                        bd = a;
                        best = l;
                    }
                }
                if (best != null) {
                    m.snuffel = best.clone();
                    p.playSound(m.ent.getLocation(), Sound.ENTITY_FOX_SNIFF, SoundCategory.NEUTRAL, 0.6f, 1.2f);
                }
            }
            if (m.snuffel != null) {
                if (m.snuffel.distanceSquared(p.getLocation()) < 2.5 * 2.5 || m.snuffel.distanceSquared(p.getLocation()) > 20 * 20) {
                    m.snuffel = null;                            // gevonden (of je loopt weg): weer mee
                    m.snuffelTot = nu + 20_000;
                    zit(m.ent, false);
                } else if (m.ent.getLocation().distanceSquared(m.snuffel) > 1.8 * 1.8) {
                    zit(m.ent, false);
                    m.ent.getPathfinder().moveTo(m.snuffel, 1.3);
                } else {
                    zit(m.ent, true);
                    if (tik % 2 == 0) {
                        p.spawnParticle(Particle.HAPPY_VILLAGER, m.snuffel.clone().add(0, 0.4, 0), 3, 0.3, 0.2, 0.3, 0);
                        p.playSound(m.ent.getLocation(), Sound.ENTITY_FOX_SNIFF, SoundCategory.NEUTRAL, 0.5f, 1.3f);
                    }
                }
                continue;
            }
            // meelopen; stilstaan = na een paar tellen gaan zitten
            boolean stil = m.vorige != null && m.vorige.getWorld().equals(p.getWorld()) && m.vorige.distanceSquared(p.getLocation()) < 0.04;
            m.vorige = p.getLocation();
            if (!stil) m.stilSinds = nu;
            if (d > 3.5 * 3.5) {
                zit(m.ent, false);
                m.ent.getPathfinder().moveTo(p, d > 10 * 10 ? 1.5 : 1.2);
            } else {
                m.ent.getPathfinder().stopPathfinding();
                if (nu - m.stilSinds > 5000) zit(m.ent, true);
                else if (!stil) zit(m.ent, false);
                if (m.ent.getLocation().distanceSquared(p.getLocation()) > 1) {
                    Location kijk = m.ent.getLocation().setDirection(p.getLocation().toVector().subtract(m.ent.getLocation().toVector()));
                    m.ent.setRotation(kijk.getYaw(), 0);
                }
            }
        }
        for (var it = dieren.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (wie.contains(e.getKey())) continue;
            if (e.getValue().ent != null) e.getValue().ent.remove();
            it.remove();
        }
    }

    private static void zit(Mob m, boolean zit) {
        if (m instanceof Sittable s && s.isSitting() != zit) s.setSitting(zit);
    }

    /** Een plek vlak achter de speler. */
    private static Location naast(Player p) {
        Location l = p.getLocation();
        org.bukkit.util.Vector terug = l.getDirection().setY(0);
        if (terug.lengthSquared() < 1e-4) terug = new org.bukkit.util.Vector(0, 0, 1);
        terug.normalize().multiply(-1.2);
        Location n = l.clone().add(terug);
        return n.getBlock().isPassable() && n.clone().add(0, 1, 0).getBlock().isPassable() ? n : l;
    }

    private static Mob maak(String soort, Location l, String naam) {
        Class<? extends Mob> klasse = switch (soort) {
            case "kat" -> Cat.class;
            case "konijn" -> Rabbit.class;
            default -> Fox.class;
        };
        return l.getWorld().spawn(l, klasse, m -> {
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.addScoreboardTag(ZeldaSpel.TAG);
            m.addScoreboardTag(ZeldaSpel.NPC);
            m.addScoreboardTag(TAG);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setCanPickupItems(false);
            m.customName(Component.text(naam, ZeldaSpel.GROEN));
            m.setCustomNameVisible(false);
            if (m instanceof Fox f) f.setFoxType(soort.equals("sneeuwvos") ? Fox.Type.SNOW : Fox.Type.RED);
            if (m instanceof Ageable a) a.setBaby();
            if (m instanceof org.bukkit.entity.Breedable b) b.setAgeLock(true);
            Bukkit.getMobGoals().removeAllGoals(m);              // geen eigen wil: niet slapen, jagen of weglopen
        });
    }

    void weg() {
        for (Maatje m : dieren.values()) if (m.ent != null) m.ent.remove();
        dieren.clear();
    }

    void weg(UUID id) {
        Maatje m = dieren.remove(id);
        if (m != null && m.ent != null) m.ent.remove();
    }

    int aantal() {
        int n = 0;
        for (Maatje m : dieren.values()) if (m.ent != null && m.ent.isValid()) n++;
        return n;
    }
}
