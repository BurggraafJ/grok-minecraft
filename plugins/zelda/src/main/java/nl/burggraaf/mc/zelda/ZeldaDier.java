package nl.burggraaf.mc.zelda;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.util.Vector;

/**
 * Een dier dat met je meeloopt in Story of Zelda. Het staat in je voortgang als vlag {@code dier:<soort>} met een naam
 * ({@code dier} in zelda.yml). Vrije huisdieren ({@code vos}, {@code kat}, {@code konijn}) staan uit ({@link #AAN}: Jelle
 * vindt een huisdier in Zelda afleidend). Het <b>sneeuwvosje</b> van de Witte Toppen ({@code dier:sneeuwvos}) is iets
 * anders: een rustige verhaal-metgezel ({@link #VERHAAL}) die je daar krijgt en een naam geeft. Het loopt mee (eigen
 * pad, zonder eigen wil: geen slapen, jagen of vechten), wordt nooit geraakt, botst niet, zegt niets (alleen een snuffel)
 * en gaat in een gevecht uit de weg zitten. En het <b>snuffelt</b>: ligt er binnen 14 blokken een verborgen vondst die
 * jij nog niet hebt, dan loopt het erheen en gaat erbij zitten; is er een graafplek ({@link ZeldaHoofdstuk.Graaf}), dan
 * graaft het hem op. Zo helpt het zoeken, ook op eilanden waar je al was. Losstaand van de plugin Huisdieren.
 */
final class ZeldaDier {

    static final String TAG = "zelda-dier";
    /** Vrije huisdieren (vos, kat, konijn): uit (Jelle 27-10: het leidt af). Vlaggen blijven bewaard. */
    static final boolean AAN = false;
    /** Dieren uit het verhaal: die lopen altijd mee (het sneeuwvosje van de Witte Toppen). */
    static final Set<String> VERHAAL = Set.of("sneeuwvos");
    static final List<String> SOORTEN = List.of("vos", "sneeuwvos", "kat", "konijn");   // geen wolf: skeletten vluchten ervoor
    static final List<String> NAMEN = List.of("Vlok", "Pluis", "Sneeuw", "Vonk", "Kiek");

    /** Waar het dier naar zoekt, en wat er gebeurt als het iets opgraaft (zie {@link #tik}). */
    interface Neus {
        /** Verborgen vondsten in de buurt die deze speler nog niet heeft (het dier gaat erbij zitten). */
        List<Location> verborgen(Player p);

        /** Graafplekken op dit eiland die deze speler nog niet heeft. */
        List<ZeldaHoofdstuk.Graaf> graven(Player p);

        /** Wordt er bij deze speler gevochten? Dan gaat het dier uit de weg. */
        boolean gevecht(Player p);

        /** Het dier heeft {@code g} opgegraven. */
        void gegraven(Player p, ZeldaHoofdstuk.Graaf g);
    }

    private static final class Maatje {
        Mob ent;
        String soort;
        Location snuffel;
        ZeldaHoofdstuk.Graaf graaf;
        long snuffelTot, stilSinds, graafStart, graafSinds;
        Location vorige;
    }

    private final Map<UUID, Maatje> dieren = new HashMap<>();
    private long tik;

    /** Loopt dit dier mee? Een verhaal-dier altijd, een vrij huisdier alleen als {@link #AAN}. */
    static boolean actief(String soort) {
        return soort != null && (AAN || VERHAAL.contains(soort));
    }

    /** De soort uit de vlaggen ({@code dier:sneeuwvos}; een dier dat meeloopt gaat voor), of null. */
    static String soort(ZeldaVoortgang.Stand s) {
        String eerste = null;
        for (String v : s.vlaggen)
            if (v.startsWith("dier:") && SOORTEN.contains(v.substring(5))) {
                if (actief(v.substring(5))) return v.substring(5);
                if (eerste == null) eerste = v.substring(5);
            }
        return eerste;
    }

    static String naam(ZeldaVoortgang.Stand s) {
        return s.dier == null || s.dier.isBlank() ? NAMEN.get(0) : s.dier;
    }

    Mob van(Player p) {
        Maatje m = dieren.get(p.getUniqueId());
        return m == null || m.ent == null || !m.ent.isValid() ? null : m.ent;
    }

    /** Waar het dier van deze speler nu naartoe snuffelt (een verborgen vondst), of null. */
    Location snuffelt(Player p) {
        Maatje m = dieren.get(p.getUniqueId());
        return m == null ? null : m.snuffel;
    }

    /** De graafplek waar het dier van deze speler nu heen gaat of graaft, of null. */
    ZeldaHoofdstuk.Graaf graaft(Player p) {
        Maatje m = dieren.get(p.getUniqueId());
        return m == null ? null : m.graaf;
    }

    boolean isDier(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG);
    }

    /** Elke 10 tikken. {@code spelers}: wie in Zelda is, met zijn stand; {@code neus}: wat er te zoeken is. */
    void tik(Map<Player, ZeldaVoortgang.Stand> spelers, Neus neus) {
        tik++;
        long nu = System.currentTimeMillis();
        Set<UUID> wie = new HashSet<>();
        for (var e : spelers.entrySet()) {
            Player p = e.getKey();
            String soort = soort(e.getValue());
            if (!actief(soort) || p.isDead()) continue;
            wie.add(p.getUniqueId());
            Maatje m = dieren.computeIfAbsent(p.getUniqueId(), k -> new Maatje());
            String naam = naam(e.getValue());
            if (m.ent == null || !m.ent.isValid() || !soort.equals(m.soort) || !m.ent.getWorld().equals(p.getWorld())) {
                if (m.ent != null) m.ent.remove();
                m.soort = soort;
                m.ent = maak(soort, naast(p, 1.2), naam);
                m.snuffel = null;
                m.graaf = null;
                continue;
            }
            if (!Component.text(naam, ZeldaSpel.GROEN).equals(m.ent.customName())) m.ent.customName(Component.text(naam, ZeldaSpel.GROEN));
            double d = m.ent.getLocation().distanceSquared(p.getLocation());
            if (d > 28 * 28) {                                   // te ver (gereisd, gevallen, met de klauwhaak): naar je toe
                m.ent.teleport(naast(p, 1.2));
                m.snuffel = null;
                m.graaf = null;
                continue;
            }
            if (neus.gevecht(p)) {                               // er wordt gevochten: uit de weg, rustig zitten
                m.snuffel = null;
                m.graaf = null;
                m.ent.getPathfinder().stopPathfinding();
                if (d < 4 * 4) {
                    Location weg = naast(p, 6);
                    if (weg.distanceSquared(p.getLocation()) > 9) m.ent.teleport(weg);
                }
                zit(m.ent, true);
                continue;
            }
            // graven: een graafplek in de buurt die je nog niet hebt
            if (tik % 4 == 0 && m.graaf == null && m.snuffel == null && nu > m.snuffelTot) {
                ZeldaHoofdstuk.Graaf best = null;
                double bd = 12 * 12;
                for (ZeldaHoofdstuk.Graaf g : neus.graven(p)) {
                    if (!g.plek().getWorld().equals(p.getWorld())) continue;
                    double a = g.plek().distanceSquared(p.getLocation());
                    if (a < bd) {
                        bd = a;
                        best = g;
                    }
                }
                if (best != null) {
                    m.graaf = best;
                    m.graafStart = nu;
                    m.graafSinds = 0;
                    p.playSound(m.ent.getLocation(), Sound.ENTITY_FOX_SNIFF, SoundCategory.NEUTRAL, 0.7f, 1.1f);
                }
            }
            if (m.graaf != null) {
                Location plek = m.graaf.plek();
                if (plek.distanceSquared(p.getLocation()) > 20 * 20) {
                    m.graaf = null;                              // je loopt weg: weer mee
                    zit(m.ent, false);
                } else if (m.ent.getLocation().distanceSquared(plek) > 1.6 * 1.6) {
                    zit(m.ent, false);
                    if (nu - m.graafStart > 10_000L) m.ent.teleport(plek);   // vast? dan er gewoon heen
                    else m.ent.getPathfinder().moveTo(plek, 1.3);
                } else {
                    zit(m.ent, false);
                    m.ent.getPathfinder().stopPathfinding();
                    if (m.graafSinds == 0) m.graafSinds = nu;
                    p.spawnParticle(Particle.BLOCK, plek.clone().add(0, 0.3, 0), 10, 0.3, 0.2, 0.3, 0, Material.SNOW_BLOCK.createBlockData());
                    if (tik % 2 == 0) p.playSound(plek, Sound.BLOCK_SNOW_BREAK, SoundCategory.NEUTRAL, 0.6f, 1.2f);
                    if (nu - m.graafSinds > 2500) {
                        ZeldaHoofdstuk.Graaf g = m.graaf;
                        m.graaf = null;
                        m.snuffelTot = nu + 4000;
                        neus.gegraven(p, g);
                    }
                }
                continue;
            }
            // snuffelen: om de 2 s kijken of er iets verborgen ligt
            if (tik % 4 == 0 && m.snuffel == null && nu > m.snuffelTot) {
                Location best = null;
                double bd = 14 * 14;
                for (Location l : neus.verborgen(p)) {
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

    /** Een plek {@code afstand} blokken achter de speler (vrij), anders waar hij staat. */
    private static Location naast(Player p, double afstand) {
        Location l = p.getLocation();
        Vector terug = l.getDirection().setY(0);
        if (terug.lengthSquared() < 1e-4) terug = new Vector(0, 0, 1);
        terug.normalize().multiply(-afstand);
        Location n = l.clone().add(terug);
        return n.getBlock().isPassable() && n.clone().add(0, 1, 0).getBlock().isPassable() && n.clone().add(0, -1, 0).getBlock().getType().isSolid() ? n : l;
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
