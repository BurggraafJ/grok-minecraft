package nl.burggraaf.mc.kitpvp;

import java.util.List;
import java.util.Random;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Een baas in een kamer (hoofdstuk 1: Grauwmoer, de moerasheks). Ze verschijnt telkens ergens anders: boven op een
 * reuzenpaddenstoel (schiet haar eraf met de boog, dan valt ze even versuft neer) of op de grond. Bij twee derde en
 * een derde van haar leven roept ze mosschutters op; onder een derde wordt ze sneller. Haar levensbalk (alleen haar
 * naam) zien alleen de spelers in de kamer.
 *
 * <p>Een andere baas is een subklasse die {@link #beweeg} (wat hij elke seconde doet), {@link #schade} (hoeveel een
 * klap doet), {@link #boem} (een bom in zijn kamer), {@link #geraakt} (een pijl), {@link #raakBlok} (een pijl op een
 * blok in zijn kamer) en {@link #vlam} (de vuurstaf) anders doet, met een eigen {@link #soort} en {@link #helper} (bv.
 * {@link Zoutbaard} op eiland 2b, {@link Asgrim} op eiland 4b).
 */
class ZeldaBaas {

    final String naam;
    final List<Location> toppen, grond;
    final List<ZeldaHoofdstuk.Regel> bijHelft, bijWinst;
    /** Leven (halve harten) voor één speler, en erbij voor elke speler meer. */
    double leven = 80, levenPerSpeler = 40;
    /** Hoeveel helpers hij oproept bij twee derde en bij een derde van zijn leven (0 = geen). */
    int[] oproepen = {2, 2};
    /** Welke vijand de baas zelf is, en welke hij oproept. */
    ZeldaHoofdstuk.Soort soort = ZeldaHoofdstuk.Soort.HEKS, helper = ZeldaHoofdstuk.Soort.MOSSCHUTTER;
    BossBar.Color kleur = BossBar.Color.PURPLE;
    int naamKleur = 0xc9a0dc;
    final Random rnd = new Random();

    LivingEntity ent;
    BossBar balk;
    int tel, verst;
    private boolean opTop, helftGezegd, oproep1, oproep2;

    ZeldaBaas(String naam, List<Location> toppen, List<Location> grond, List<ZeldaHoofdstuk.Regel> bijHelft, List<ZeldaHoofdstuk.Regel> bijWinst) {
        this.naam = naam;
        this.toppen = toppen;
        this.grond = grond;
        this.bijHelft = bijHelft;
        this.bijWinst = bijWinst;
    }

    void start(LivingEntity e) {
        ent = e;
        balk = BossBar.bossBar(Component.text(naam, TextColor.color(naamKleur)), 1f, kleur, BossBar.Overlay.PROGRESS);
        tel = 0;
        verst = 0;
        opTop = false;
        helftGezegd = oproep1 = oproep2 = false;
    }

    void stop() {
        ent = null;
    }

    boolean leeft() {
        return ent != null && ent.isValid() && !ent.isDead();
    }

    float deel() {
        if (!leeft()) return 0f;
        var max = ent.getAttribute(Attribute.MAX_HEALTH);
        return (float) Math.max(0, Math.min(1, ent.getHealth() / (max == null ? 20 : max.getValue())));
    }

    /**
     * Elke seconde tijdens het gevecht.
     *
     * @return het aantal helpers dat hij nu oproept (0 = geen)
     */
    int tik(ZeldaSpel spel, List<Player> in) {
        if (!leeft()) return 0;
        float d = deel();
        balk.progress(d);
        int oproep = 0;
        if (!oproep1 && d < 0.67f) {
            oproep1 = true;
            oproep = oproepen[0];
        } else if (!oproep2 && d < 0.34f) {
            oproep2 = true;
            oproep = oproepen[1];
        }
        if (!helftGezegd && d < 0.5f) {
            helftGezegd = true;
            for (Player p : in) for (ZeldaHoofdstuk.Regel r : bijHelft) spel.vertel(p, r);
        }
        beweeg(spel, in, d);
        return oproep;
    }

    /** Wat de baas elke seconde doet. Grauwmoer: om de paar tellen ergens anders verschijnen (een paddenstoel of de grond). */
    void beweeg(ZeldaSpel spel, List<Player> in, float d) {
        if (verst > 0) {                                     // versuft na een treffer op de paddenstoel
            verst--;
            return;
        }
        int om = d < 0.34f ? 5 : 7;
        if (++tel >= om) {
            tel = 0;
            boolean top = rnd.nextInt(3) > 0 && !in.isEmpty() && !toppen.isEmpty();
            List<Location> l = top ? toppen : grond;
            spring(l.get(rnd.nextInt(l.size())));
            opTop = top;
        }
    }

    private void spring(Location naar) {
        Location van = ent.getLocation();
        van.getWorld().spawnParticle(Particle.WITCH, van.clone().add(0, 1.2, 0), 40, 0.4, 0.8, 0.4, 0.02);
        van.getWorld().spawnParticle(Particle.LARGE_SMOKE, van.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.01);
        van.getWorld().playSound(van, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        ent.teleport(naar);
        naar.getWorld().spawnParticle(Particle.WITCH, naar.clone().add(0, 1.2, 0), 40, 0.4, 0.8, 0.4, 0.02);
        naar.getWorld().playSound(naar, Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.7f);
    }

    /** Geraakt door een pijl: staat ze op een paddenstoel, dan valt ze versuft naar beneden. */
    void geraakt() {
        if (!leeft() || !opTop) return;
        opTop = false;
        Location l = grond.get(rnd.nextInt(grond.size()));
        ent.teleport(l);
        ent.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, false));
        l.getWorld().spawnParticle(Particle.CRIT, l.clone().add(0, 1.5, 0), 25, 0.4, 0.5, 0.4, 0.1);
        l.getWorld().playSound(l, Sound.ENTITY_WITCH_HURT, 1f, 0.6f);
        verst = 4;
        tel = 0;
    }

    /** Hoeveel een klap van {@code schade} bij deze baas doet (standaard alles). */
    double schade(double schade) {
        return schade;
    }

    /** Een bom ontploft in de kamer van deze baas (tijdens het gevecht). */
    void boem(ZeldaSpel spel, Player p, Location l) {
    }

    /**
     * Een pijl raakt een blok in de kamer van deze baas, tijdens het gevecht (Asgrim: de kuip boven hem). True = de baas
     * deed er iets mee (dan verdwijnt de pijl).
     */
    boolean raakBlok(ZeldaSpel spel, Player p, org.bukkit.block.Block b) {
        return false;
    }

    /** Een vlam van de vuurstaf raakt deze baas. True = hij doet er zelf iets mee (dan geen gewone tik). */
    boolean vlam(ZeldaSpel spel, Player p) {
        return false;
    }

    /** De baas is verslagen (hij lost op in licht). */
    void weg(Location l) {
        l.getWorld().spawnParticle(Particle.END_ROD, l.clone().add(0, 1.2, 0), 40, 0.5, 0.8, 0.5, 0.05);
    }
}
