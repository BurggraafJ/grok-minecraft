package nl.burggraaf.mc.kitpvp;

import java.util.List;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Kapitein Zoutbaard, de baas van de Getijdentempel (eiland 2b): een verdronken zeerover die door Morgals nevel ronddoolt.
 * Hij staat telkens bij een van zijn kruittonnen en gooit drietanden. Zijn jas zit vol zeepokken: een klap doet weinig.
 * Ontploft er een bom bij de ton waar hij staat, dan knalt de ton en staat hij even versuft (dan doet elke klap meer).
 * Om de paar tellen springt hij naar een andere ton; een kapotte ton is na een tijdje weer heel. Bij twee derde en een
 * derde roept hij een matroos. Verslagen = hij vergaat tot zeeschuim (bevrijd van de nevel).
 */
final class Zoutbaard extends ZeldaBaas {

    /** Zo lang staat hij versuft na een knal (seconden), en zo lang is een ton kapot (ms). */
    static final int VERSUFT = 6;
    static final long TON_WEG = 15_000L;
    /** Zo dicht bij een ton (of bij hem) moet een bom ontploffen, en zo dicht moet hij bij zijn ton staan. */
    static final double TON_RAAK = 2.6, BIJ_DE_TON = 3.5, BIJ_HEM = 2.6;

    /** De kruittonnen (blokken), in dezelfde volgorde als {@link #grond} (waar hij erbij staat). */
    final List<int[]> tonnen;
    private final boolean[] heel;
    private final long[] terugOp;
    int bij;                                                 // bij welke ton hij nu staat
    int versuft;                                             // seconden dat hij nog versuft is
    private boolean knalGezegd;

    Zoutbaard(String naam, List<Location> plekken, List<int[]> tonnen, List<ZeldaHoofdstuk.Regel> bijHelft, List<ZeldaHoofdstuk.Regel> bijWinst) {
        super(naam, List.of(), plekken, bijHelft, bijWinst);
        if (plekken.size() != tonnen.size() || plekken.size() < 2) throw new IllegalArgumentException("Zoutbaard: een plek bij elke ton (minstens twee)");
        this.tonnen = tonnen;
        heel = new boolean[tonnen.size()];
        terugOp = new long[tonnen.size()];
        java.util.Arrays.fill(heel, true);
        soort = ZeldaHoofdstuk.Soort.KAPITEIN;
        helper = ZeldaHoofdstuk.Soort.MATROOS;
        kleur = BossBar.Color.BLUE;
        naamKleur = 0x8ec9e8;
        leven = 60;
        levenPerSpeler = 30;
        oproepen = new int[]{1, 1};
    }

    private World w() {
        return grond.get(0).getWorld();
    }

    private Location ton(int i) {
        int[] t = tonnen.get(i);
        return new Location(w(), t[0] + 0.5, t[1] + 0.5, t[2] + 0.5);
    }

    boolean heel(int i) {
        return heel[i];
    }

    /** Staat er niemand op de plek van ton {@code i}? */
    private boolean vrij(int i) {
        int[] t = tonnen.get(i);
        return w().getNearbyEntities(org.bukkit.util.BoundingBox.of(w().getBlockAt(t[0], t[1], t[2])), e -> e instanceof org.bukkit.entity.LivingEntity).isEmpty();
    }

    @Override
    void start(org.bukkit.entity.LivingEntity e) {
        super.start(e);
        bij = 0;
        versuft = 0;
        knalGezegd = false;
        for (int i = 0; i < tonnen.size(); i++) zetTon(i, true);
    }

    @Override
    void stop() {
        for (int i = 0; i < tonnen.size(); i++) zetTon(i, true);
        versuft = 0;
        super.stop();
    }

    private void zetTon(int i, boolean h) {
        heel[i] = h;
        int[] t = tonnen.get(i);
        if (w() != null) w().getBlockAt(t[0], t[1], t[2]).setType(h ? Material.BARREL : Material.AIR, false);
    }

    @Override
    void beweeg(ZeldaSpel spel, List<Player> in, float d) {
        long nu = System.currentTimeMillis();
        for (int i = 0; i < tonnen.size(); i++)
            if (!heel[i] && nu >= terugOp[i] && vrij(i)) {               // een nieuwe ton aangespoeld (niet op iemand)
                zetTon(i, true);
                w().spawnParticle(Particle.SPLASH, ton(i), 20, 0.4, 0.4, 0.4, 0.1);
            }
        if (versuft > 0) {
            Location kop = ent.getLocation().add(0, ent.getHeight() + 0.3, 0);
            for (int i = 0; i < 4; i++) {                                // sterretjes boven zijn hoofd
                double a = nu / 300.0 + i * Math.PI / 2;
                w().spawnParticle(Particle.WAX_ON, kop.clone().add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5), 1, 0, 0, 0, 0);
            }
            if (--versuft == 0) {
                ent.setAI(true);
                w().playSound(ent.getLocation(), Sound.ENTITY_DROWNED_AMBIENT, 1f, 0.6f);
            }
            return;
        }
        if (heel[bij]) w().spawnParticle(Particle.SMOKE, ton(bij).add(0, 0.6, 0), 3, 0.15, 0.05, 0.15, 0.01);   // zijn kruit smeult
        if (++tel >= (d < 0.34f ? 6 : 8)) {
            tel = 0;
            int naar = (bij + 1 + rnd.nextInt(tonnen.size() - 1)) % tonnen.size();
            for (int n = 0; n < tonnen.size() && !heel[naar]; n++) naar = (naar + 1) % tonnen.size();   // liefst een hele ton
            if (naar != bij) spring(naar);
        }
    }

    /** Naar de ton {@code i} springen (een plons, en hij staat ernaast). */
    void spring(int i) {
        Location van = ent.getLocation();
        w().spawnParticle(Particle.SPLASH, van.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.2);
        w().playSound(van, Sound.ENTITY_PLAYER_SPLASH, 0.8f, 0.7f);
        Location naar = grond.get(i).clone();
        ent.teleport(naar);
        bij = i;
        w().spawnParticle(Particle.BUBBLE_POP, naar.clone().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.05);
        w().playSound(naar, Sound.ENTITY_DROWNED_AMBIENT_WATER, 1f, 0.7f);
    }

    /**
     * Een bom. Staat hij bij een hele ton en knalt de bom bij die ton of bij hem (hij staat ervoor), dan springt de ton
     * en is hij even versuft. Een bom bij een andere hele ton laat alleen die ton springen.
     */
    @Override
    void boem(ZeldaSpel spel, Player p, Location l) {
        boolean raak = leeft() && versuft == 0 && heel[bij] && ent.getLocation().distance(ton(bij)) <= BIJ_DE_TON
                && (l.distance(ton(bij)) <= TON_RAAK || l.distance(ent.getLocation().add(0, 1, 0)) <= BIJ_HEM);
        for (int i = 0; i < tonnen.size(); i++)
            if (heel[i] && (l.distance(ton(i)) <= TON_RAAK || (raak && i == bij))) knal(i);
        if (!raak) return;
        versuft = VERSUFT;
        tel = 0;
        ent.setAI(false);
        ent.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, VERSUFT * 20, 0, false, false));
        w().playSound(ent.getLocation(), Sound.ENTITY_DROWNED_HURT, 1f, 0.5f);
        if (!knalGezegd) {
            knalGezegd = true;
            for (Player q : spel.in(spel.kamerVan(this).binnen)) spel.vertel(q, ZeldaHoofdstuk.r(naam, "Arrr! Mijn kruit!"));
        }
    }

    /** Een ton springt (en komt na een tijdje terug). */
    private void knal(int i) {
        zetTon(i, false);
        terugOp[i] = System.currentTimeMillis() + TON_WEG;
        Location t = ton(i);
        w().spawnParticle(Particle.EXPLOSION, t, 4, 0.6, 0.6, 0.6, 0);
        w().spawnParticle(Particle.FLAME, t, 30, 0.6, 0.6, 0.6, 0.05);
        w().playSound(t, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
    }

    /** Zijn jas vol zeepokken: weinig schade; versuft veel. */
    @Override
    double schade(double schade) {
        return ZeldaRegels.zoutbaardSchade(schade, versuft > 0);
    }

    /** Pijlen ketsen af op de zeepokken (niets bijzonders). */
    @Override
    void geraakt() {
    }

    /** Hij vergaat tot zeeschuim. */
    @Override
    void weg(Location l) {
        w().spawnParticle(Particle.SPLASH, l.clone().add(0, 1, 0), 80, 0.6, 1.0, 0.6, 0.3);
        w().spawnParticle(Particle.BUBBLE_POP, l.clone().add(0, 1, 0), 50, 0.6, 1.0, 0.6, 0.05);
        w().spawnParticle(Particle.CLOUD, l.clone().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.02);
        w().playSound(l, Sound.AMBIENT_UNDERWATER_EXIT, 1f, 1.2f);
    }
}
