package nl.burggraaf.mc.kitpvp;

import java.util.List;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Grimmel, de baas van het IJspaleis (eiland 3b): de reuzenijsbeer van de oude bergwachters, in de ban van Morgals nevel.
 * Hij sjokt achter je aan en slaat. Om de paar tellen gaat hij op zijn achterpoten staan (dan weet je het: hij gaat
 * stormen) en stormt hij rechtdoor naar waar je toen stond, tot hij ergens tegenaan loopt. Een ijszuil = de zuil breekt en
 * hij duizelt (versuft: dan doet een klap veel meer; anders is zijn vacht dik). Een muur = hij schudt even zijn kop. Wie
 * op tijd opzij gaat, of zich wegtrekt met de klauwhaak (die werkt in zijn zaal), laat hem tegen een zuil lopen. Een
 * gebroken zuil groeit na een tijdje terug. Bij twee derde en een derde roept hij een ijskobold. Verslagen = de nevel
 * trekt uit hem weg en hij sjokt naar zijn hol.
 */
final class Grimmel extends ZeldaBaas {

    /** Zo lang duizelt hij na een zuil (seconden), en zo lang is een zuil weg (ms). */
    static final int VERSUFT = 5;
    static final long ZUIL_WEG = 20_000L;
    /** Zo lang staat hij op zijn achterpoten voor hij stormt (tikken), zo snel stormt hij (blokken per tik) en zo ver. */
    static final int AANLOOP = 30;
    static final double SNELHEID = 0.7, BEREIK = 30;
    /** Zo dicht bij zijn midden word je geraakt, en zoveel doet dat (halve harten, vóór de balans en je harnas). */
    static final double RAAK = 1.9, STORM_SCHADE = 8;

    /** De ijszuilen: per zuil {x1, z1, x2, z2, y1, y2} (blokken, grenzen erbij). */
    final List<int[]> zuilen;
    /** Waar hij heen sjokt als hij vrij is (zijn hol). */
    final Location hol;
    private final Plugin plugin;
    private final boolean[] heel;
    private final long[] terugOp;
    int versuft;                                             // seconden dat hij nog duizelt (na een zuil)
    int stormOver;                                           // seconden tot hij weer stormt
    int stormen, zuilRaak;                                   // hoe vaak hij stormde, hoe vaak tegen een zuil (test)
    private BukkitTask taak;                                 // de aanloop en de storm (elke tik)
    private boolean zuilGezegd;

    Grimmel(Plugin plugin, String naam, Location plek, Location hol, List<int[]> zuilen, List<ZeldaHoofdstuk.Regel> bijHelft, List<ZeldaHoofdstuk.Regel> bijWinst) {
        super(naam, List.of(), List.of(plek), bijHelft, bijWinst);
        if (zuilen.isEmpty()) throw new IllegalArgumentException("Grimmel: minstens één ijszuil");
        this.plugin = plugin;
        this.hol = hol;
        this.zuilen = zuilen;
        heel = new boolean[zuilen.size()];
        terugOp = new long[zuilen.size()];
        java.util.Arrays.fill(heel, true);
        soort = ZeldaHoofdstuk.Soort.GRIMMEL;
        helper = ZeldaHoofdstuk.Soort.IJSKOBOLD;
        kleur = BossBar.Color.WHITE;
        naamKleur = 0xbfe6ff;
        leven = 60;
        levenPerSpeler = 30;
        oproepen = new int[]{1, 1};
    }

    private World w() {
        return grond.get(0).getWorld();
    }

    boolean heel(int i) {
        return heel[i];
    }

    boolean stormt() {
        return taak != null;
    }

    /** Het midden van zuil {@code i} (halverwege de hoogte). */
    Location zuil(int i) {
        int[] c = zuilen.get(i);
        return new Location(w(), (c[0] + c[2] + 1) / 2.0, (c[4] + c[5] + 1) / 2.0, (c[1] + c[3] + 1) / 2.0);
    }

    /** De zuil waar dit blok bij hoort, of -1. */
    int zuilVan(Block b) {
        for (int i = 0; i < zuilen.size(); i++) {
            int[] c = zuilen.get(i);
            if (b.getX() >= c[0] && b.getX() <= c[2] && b.getZ() >= c[1] && b.getZ() <= c[3] && b.getY() >= c[4] && b.getY() <= c[5]) return i;
        }
        return -1;
    }

    /** Zo ziet een hele zuil eruit (blok op hoogte {@code y}). */
    static Material zuilBlok(int[] c, int y) {
        return y == c[5] || y == c[4] ? Material.BLUE_ICE : Material.PACKED_ICE;
    }

    private void zetZuil(int i, boolean h) {
        heel[i] = h;
        int[] c = zuilen.get(i);
        for (int x = c[0]; x <= c[2]; x++)
            for (int z = c[1]; z <= c[3]; z++)
                for (int y = c[4]; y <= c[5]; y++) w().getBlockAt(x, y, z).setType(h ? zuilBlok(c, y) : Material.AIR, false);
    }

    /** Staat er niets levends op de plek van zuil {@code i}? */
    private boolean vrij(int i) {
        int[] c = zuilen.get(i);
        BoundingBox b = new BoundingBox(c[0], c[4], c[1], c[2] + 1, c[5] + 1, c[3] + 1);
        return w().getNearbyEntities(b, e -> e instanceof LivingEntity).isEmpty();
    }

    @Override
    void start(LivingEntity e) {
        super.start(e);
        versuft = 0;
        stormOver = 5;
        stormen = 0;
        zuilRaak = 0;
        zuilGezegd = false;
        for (int i = 0; i < zuilen.size(); i++) zetZuil(i, true);
    }

    @Override
    void stop() {
        if (taak != null) taak.cancel();
        taak = null;
        for (int i = 0; i < zuilen.size(); i++) if (!heel[i]) zetZuil(i, true);
        versuft = 0;
        super.stop();
    }

    /** Elke seconde: gebroken zuilen terug, duizelen, en om de paar tellen stormen naar wie het dichtst bij is. */
    @Override
    void beweeg(ZeldaSpel spel, List<Player> in, float d) {
        long nu = System.currentTimeMillis();
        for (int i = 0; i < zuilen.size(); i++)
            if (!heel[i] && nu >= terugOp[i] && vrij(i)) {                   // de zuil groeit weer aan (niet op iemand)
                zetZuil(i, true);
                w().spawnParticle(Particle.SNOWFLAKE, zuil(i), 30, 0.6, 1.4, 0.6, 0.02);
                w().playSound(zuil(i), Sound.BLOCK_GLASS_PLACE, 0.8f, 0.7f);
            }
        if (versuft > 0) {
            Location kop = ent.getLocation().add(0, ent.getHeight() + 0.3, 0);
            for (int i = 0; i < 5; i++) {                                    // sterretjes boven zijn kop
                double a = nu / 300.0 + i * Math.PI * 2 / 5;
                w().spawnParticle(Particle.WAX_ON, kop.clone().add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9), 1, 0, 0, 0, 0);
            }
            if (--versuft == 0) {
                ent.setAI(true);
                w().playSound(ent.getLocation(), Sound.ENTITY_POLAR_BEAR_AMBIENT, 1f, 0.6f);
            }
            return;
        }
        if (taak != null || in.isEmpty()) return;
        if (--stormOver <= 0) {
            Player doel = in.get(0);
            for (Player p : in) if (p.getLocation().distanceSquared(ent.getLocation()) < doel.getLocation().distanceSquared(ent.getLocation())) doel = p;
            storm(spel, doel.getLocation());
        }
    }

    /**
     * Stormen naar {@code naar}: eerst op zijn achterpoten (de richting staat dan vast), dan rechtdoor tot hij ergens
     * tegenaan loopt (een zuil: versuft; een muur: even niets) of iemand raakt, of {@link #BEREIK} blokken ver is.
     */
    void storm(ZeldaSpel spel, Location naar) {
        if (!leeft() || taak != null) return;
        stormen++;
        Location van = ent.getLocation();
        Vector r = naar.toVector().subtract(van.toVector()).setY(0);
        if (r.lengthSquared() < 0.01) r = new Vector(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5);
        Vector richting = r.normalize();
        float yaw = van.clone().setDirection(richting).getYaw();
        ent.setAI(false);
        ent.setRotation(yaw, 0);
        if (ent instanceof PolarBear pb) pb.setStanding(true);
        w().playSound(van, Sound.ENTITY_POLAR_BEAR_WARNING, 1.6f, 0.6f);
        ZeldaHoofdstuk.Kamer kamer = spel.kamerVan(this);
        final int[] tik = {0};
        final double[] gelopen = {0};
        taak = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!leeft()) {
                klaar();
                return;
            }
            tik[0]++;
            Location l = ent.getLocation();
            if (tik[0] <= AANLOOP) {                                         // op zijn achterpoten: grommen, sneeuw uit zijn vacht
                if (tik[0] % 5 == 0) w().spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, ent.getHeight() * 0.7, 0), 12, 0.8, 0.8, 0.8, 0.02);
                if (tik[0] == AANLOOP) {
                    if (ent instanceof PolarBear pb) pb.setStanding(false);
                    w().playSound(l, Sound.ENTITY_RAVAGER_ROAR, 0.9f, 1.3f);
                }
                return;
            }
            Location volgende = l.clone().add(richting.clone().multiply(SNELHEID));
            volgende.setYaw(yaw);
            volgende.setPitch(0);
            int b = botst(l, volgende);
            if (b != -2) {
                if (b >= 0) zuilKapot(spel, kamer, b);
                else {
                    w().playSound(l, Sound.ENTITY_POLAR_BEAR_HURT, 1f, 0.6f);
                    w().spawnParticle(Particle.BLOCK, l.clone().add(richting.clone().multiply(1.5)).add(0, 1, 0), 20, 0.6, 0.6, 0.6, 0, Material.SNOW_BLOCK.createBlockData());
                }
                klaar();
                return;
            }
            ent.teleport(volgende);
            gelopen[0] += SNELHEID;
            w().spawnParticle(Particle.BLOCK, volgende.clone().add(0, 0.1, 0), 6, 0.6, 0.1, 0.6, 0, Material.SNOW_BLOCK.createBlockData());
            if (tik[0] % 4 == 0) w().playSound(volgende, Sound.ENTITY_POLAR_BEAR_STEP, 1.2f, 0.6f);
            if (kamer != null) for (Player p : spel.in(kamer.binnen)) {               // iemand in de weg: een flinke duw
                Location pl = p.getLocation();
                double dx = pl.getX() - volgende.getX(), dz = pl.getZ() - volgende.getZ();
                if (dx * dx + dz * dz > RAAK * RAAK || Math.abs(pl.getY() - volgende.getY()) > 2.5 || spel.aanHetHaken(p)) continue;
                p.damage(STORM_SCHADE, ent);
                p.setVelocity(richting.clone().multiply(1.1).setY(0.45));
                klaar();
                return;
            }
            if (gelopen[0] >= BEREIK) klaar();
        }, 1L, 1L);
    }

    /**
     * Loopt hij ergens tegenaan als hij van {@code l} naar {@code naar} gaat? -2 = nee, -1 = een muur, anders de zuil.
     * Kijkt naar de blokken die zijn lijf (zijn kader, verschoven) dan raakt, boven de vloer.
     */
    private int botst(Location l, Location naar) {
        BoundingBox bb = ent.getBoundingBox().shift(naar.toVector().subtract(l.toVector()));
        boolean muur = false;
        for (int x = (int) Math.floor(bb.getMinX()); x <= (int) Math.floor(bb.getMaxX() - 1e-3); x++)
            for (int y = (int) Math.floor(bb.getMinY() + 0.05); y <= (int) Math.floor(bb.getMaxY() - 0.05); y++)
                for (int z = (int) Math.floor(bb.getMinZ()); z <= (int) Math.floor(bb.getMaxZ() - 1e-3); z++) {
                    Block b = w().getBlockAt(x, y, z);
                    if (b.isPassable()) continue;
                    int i = zuilVan(b);
                    if (i >= 0 && heel[i]) return i;
                    muur = true;
                }
        return muur ? -1 : -2;
    }

    /** Tegen zuil {@code i}: de zuil breekt, hij duizelt. */
    private void zuilKapot(ZeldaSpel spel, ZeldaHoofdstuk.Kamer kamer, int i) {
        Location m = zuil(i);
        zetZuil(i, false);
        terugOp[i] = System.currentTimeMillis() + ZUIL_WEG;
        w().spawnParticle(Particle.BLOCK, m, 80, 0.8, 1.6, 0.8, 0, Material.PACKED_ICE.createBlockData());
        w().spawnParticle(Particle.SNOWFLAKE, m, 40, 0.8, 1.6, 0.8, 0.05);
        w().playSound(m, Sound.BLOCK_GLASS_BREAK, 1.4f, 0.6f);
        w().playSound(m, Sound.ENTITY_POLAR_BEAR_HURT, 1.2f, 0.5f);
        zuilRaak++;
        versuft = VERSUFT;
        ent.setAI(false);
        ent.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, VERSUFT * 20, 0, false, false));
        if (!zuilGezegd && kamer != null) {
            zuilGezegd = true;
            for (Player q : spel.in(kamer.binnen)) spel.vertel(q, ZeldaHoofdstuk.r("Wiske", "Raak! Hij duizelt. Nu slaan!"));
        }
    }

    /** Klaar met stormen: weer gewoon lopen (tenzij hij duizelt), en over een tijdje weer. */
    private void klaar() {
        if (taak != null) taak.cancel();
        taak = null;
        stormOver = deel() < 0.34f ? 6 : 8;
        if (!leeft()) return;
        if (ent instanceof PolarBear pb) pb.setStanding(false);
        if (versuft == 0) ent.setAI(true);
    }

    /** Zijn vacht is dik: weinig schade; duizelig veel. */
    @Override
    double schade(double schade) {
        return ZeldaRegels.grimmelSchade(schade, versuft > 0);
    }

    /** Pijlen blijven in zijn vacht hangen (niets bijzonders). */
    @Override
    void geraakt() {
    }

    /** De nevel trekt uit hem weg; een gewone (grote) ijsbeer sjokt naar zijn hol en gaat daar liggen. */
    @Override
    void weg(Location l) {
        if (taak != null) taak.cancel();
        taak = null;
        World w = l.getWorld();
        w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1.5, 0), 60, 0.8, 1.0, 0.8, 0.03);
        w.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 1.5, 0), 60, 1.0, 1.2, 1.0, 0.05);
        w.spawnParticle(Particle.END_ROD, l.clone().add(0, 1.5, 0), 30, 0.6, 0.8, 0.6, 0.05);
        w.playSound(l, Sound.ENTITY_POLAR_BEAR_AMBIENT_BABY, 1.2f, 0.6f);
        if (hol == null) return;
        PolarBear beer = w.spawn(l, PolarBear.class, b -> {
            b.setPersistent(false);
            b.addScoreboardTag(ZeldaSpel.TAG);
            b.addScoreboardTag(ZeldaSpel.NPC);
            b.setInvulnerable(true);
            b.setAI(false);
            b.setSilent(true);
            var s = b.getAttribute(Attribute.SCALE);
            if (s != null) s.setBaseValue(1.8);
        });
        Vector r = hol.toVector().subtract(l.toVector()).setY(0);
        double lang = r.length();
        Vector stap = lang < 0.01 ? new Vector() : r.normalize().multiply(0.12);
        float yaw = l.clone().setDirection(stap.lengthSquared() > 0 ? stap : new Vector(0, 0, 1)).getYaw();
        final int[] tik = {0};
        final BukkitTask[] t = {null};
        t[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tik[0]++;
            if (!beer.isValid() || tik[0] * 0.12 >= lang || tik[0] > 200) {
                beer.remove();
                t[0].cancel();
                return;
            }
            Location n = beer.getLocation().add(stap);
            n.setYaw(yaw);
            beer.teleport(n);
            if (tik[0] % 12 == 0) w.playSound(n, Sound.ENTITY_POLAR_BEAR_STEP, 0.8f, 0.7f);
        }, 20L, 1L);
    }
}
