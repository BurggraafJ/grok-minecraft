package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Asgrim de Gloedwachter, de baas van de Smidse (eiland 4b): een groot vlammenwezen dat over de lava in het Hart van de
 * Smidse zweeft, in de ban van Morgals nevel. Hij zweeft telkens onder een van de grote koelkuipen die aan kettingen aan
 * het plafond hangen, en gooit sintels (langzaam: opzij stappen, of je Gloedschild ervoor). Door zijn vlammenschild komt
 * bijna niets heen. Kantel de kuip boven hem (een pijl, of een bom ertegen): het water dooft hem, hij ploft neer op de
 * rand en is even gedoofd; dan doet elke klap gewoon pijn. Een lege kuip loopt na een tijdje weer vol. Bij twee derde en
 * een derde roept hij hulp. Verslagen = de nevel trekt uit hem weg; hij is weer de kleine Gloedwachter van de smeden.
 */
final class Asgrim extends ZeldaBaas {

    /** Zo lang is hij gedoofd (seconden), en zo lang is een kuip leeg (ms). */
    static final int GEDOOFD = 5;
    static final long KUIP_WEG = 12_000L;
    /** Zo dicht (horizontaal) moet hij onder de kuip zweven om gedoofd te worden, en zo dicht moet een bom bij de kuip. */
    static final double ONDER = 2.6, KUIP_RAAK = 3.0;
    /** Een sintel: schade (halve harten, vóór de balans en je harnas), snelheid (blokken per tik) en hoe dichtbij hij raakt. */
    static final double SINTEL = 6, SINTEL_SNELHEID = 0.42, SINTEL_RAAK = 1.3;
    static final double SCHAAL = 1.9;
    /** Zo vaak verschuift hij en gooit hij (seconden), en onder een derde van zijn leven. */
    static final int VERSCHUIF = 7, VERSCHUIF_SNEL = 5, WERP = 3, WERP_SNEL = 2;

    /** Per zweefplek ({@link #grond}): de kuip erboven ({x, y, z}) en waar hij neerploft als hij gedoofd is (op de rand). */
    final List<int[]> kuipen;
    final List<Location> landing;
    private final Plugin plugin;
    private final boolean[] vol;
    private final long[] terugOp;
    int bij;                                                 // onder welke kuip hij nu zweeft
    int gedoofd;                                             // seconden dat hij nog gedoofd is
    int dovingen, worpen, geblokt;                           // hoe vaak gedoofd, hoeveel sintels, hoeveel tegengehouden (test)
    private int worp, tikjes;
    private Location van, naar;                              // onderweg (vliegen of neerploffen)
    private int onderweg, duur;
    private ZeldaSpel spel;
    private BukkitTask taak;
    private final List<Sintel> sintels = new ArrayList<>();
    private boolean misGezegd, vlamGezegd, doofGezegd, schildGezegd;

    /** Een sintel onderweg: een gloeiende kool die rechtdoor vliegt. */
    private static final class Sintel {
        final ItemDisplay toon;
        final Vector stap;
        Location plek;
        int leeftijd;

        Sintel(ItemDisplay toon, Location plek, Vector stap) {
            this.toon = toon;
            this.plek = plek;
            this.stap = stap;
        }
    }

    Asgrim(Plugin plugin, String naam, List<Location> zweef, List<int[]> kuipen, List<Location> landing,
           List<ZeldaHoofdstuk.Regel> bijHelft, List<ZeldaHoofdstuk.Regel> bijWinst) {
        super(naam, List.of(), zweef, bijHelft, bijWinst);
        if (zweef.size() != kuipen.size() || zweef.size() != landing.size() || zweef.size() < 2)
            throw new IllegalArgumentException("Asgrim: een kuip en een landing bij elke zweefplek (minstens twee)");
        this.plugin = plugin;
        this.kuipen = kuipen;
        this.landing = landing;
        vol = new boolean[kuipen.size()];
        terugOp = new long[kuipen.size()];
        java.util.Arrays.fill(vol, true);
        soort = ZeldaHoofdstuk.Soort.ASGRIM;
        helper = ZeldaHoofdstuk.Soort.SINTELMIJT;
        kleur = BossBar.Color.RED;
        naamKleur = 0xffa040;
        leven = 70;
        levenPerSpeler = 35;
        oproepen = new int[]{1, 1};
    }

    private World w() {
        return grond.get(0).getWorld();
    }

    /** Het midden van kuip {@code i}. */
    Location kuip(int i) {
        int[] k = kuipen.get(i);
        return new Location(w(), k[0] + 0.5, k[1] + 0.5, k[2] + 0.5);
    }

    boolean vol(int i) {
        return vol[i];
    }

    /** Hangt hij nu stil onder een kuip (niet onderweg, niet gedoofd)? */
    boolean zweeft() {
        return leeft() && onderweg == 0 && gedoofd == 0;
    }

    /** Zo ziet een volle kuip eruit (en een lege: een gewone ketel). */
    static org.bukkit.block.data.BlockData kuipBlok(boolean vol) {
        if (!vol) return Material.CAULDRON.createBlockData();
        Levelled l = (Levelled) Material.WATER_CAULDRON.createBlockData();
        l.setLevel(l.getMaximumLevel());
        return l;
    }

    private void zetKuip(int i, boolean v) {
        vol[i] = v;
        int[] k = kuipen.get(i);
        w().getBlockAt(k[0], k[1], k[2]).setBlockData(kuipBlok(v), false);
    }

    @Override
    void start(LivingEntity e) {
        super.start(e);
        e.setAI(false);
        e.setGravity(false);
        e.setSilent(false);
        bij = 0;
        gedoofd = 0;
        dovingen = worpen = geblokt = 0;
        worp = WERP;
        onderweg = 0;
        misGezegd = vlamGezegd = doofGezegd = schildGezegd = false;
        for (int i = 0; i < kuipen.size(); i++) zetKuip(i, true);
        if (taak != null) taak.cancel();
        taak = Bukkit.getScheduler().runTaskTimer(plugin, this::tikje, 1L, 1L);
    }

    @Override
    void stop() {
        if (taak != null) taak.cancel();
        taak = null;
        for (Sintel s : sintels) s.toon.remove();
        sintels.clear();
        for (int i = 0; i < kuipen.size(); i++) zetKuip(i, true);
        gedoofd = 0;
        onderweg = 0;
        super.stop();
    }

    /** Elke seconde: kuipen weer vol, gedoofd tellen, verschuiven naar een andere kuip, sintels gooien. */
    @Override
    void beweeg(ZeldaSpel spel, List<Player> in, float d) {
        this.spel = spel;
        long nu = System.currentTimeMillis();
        helper = d < 0.34f ? ZeldaHoofdstuk.Soort.VONKSCHUTTER : ZeldaHoofdstuk.Soort.SINTELMIJT;   // meer variatie
        for (int i = 0; i < kuipen.size(); i++)
            if (!vol[i] && nu >= terugOp[i]) {                                // de kuip loopt weer vol
                zetKuip(i, true);
                w().playSound(kuip(i), Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, 1f, 0.8f);
                w().spawnParticle(Particle.SPLASH, kuip(i).add(0, 0.6, 0), 12, 0.3, 0.1, 0.3, 0.05);
            }
        if (gedoofd > 0) {
            w().spawnParticle(Particle.LARGE_SMOKE, ent.getLocation().add(0, 1.5, 0), 6, 0.4, 0.6, 0.4, 0.01);
            if (--gedoofd == 0) opvlammen();
            return;
        }
        if (onderweg > 0 || in.isEmpty()) return;
        if (++tel >= (d < 0.34f ? VERSCHUIF_SNEL : VERSCHUIF)) {
            tel = 0;
            vlieg(volgende());
        }
        if (--worp <= 0) {
            worp = d < 0.34f ? WERP_SNEL : WERP;
            Player doel = in.get(0);
            for (Player p : in) if (p.getLocation().distanceSquared(ent.getLocation()) < doel.getLocation().distanceSquared(ent.getLocation())) doel = p;
            werp(doel);
        }
    }

    /** Een andere zweefplek (liefst onder een volle kuip). */
    private int volgende() {
        int n = kuipen.size();
        int naar = (bij + 1 + rnd.nextInt(n - 1)) % n;
        for (int k = 0; k < n && !vol[naar]; k++) {
            naar = (naar + 1) % n;
            if (naar == bij) naar = (naar + 1) % n;
        }
        return naar;
    }

    /** Naar zweefplek {@code i} vliegen (een tel onderweg, met een spoor van vuur). */
    void vlieg(int i) {
        if (!leeft()) return;
        bij = i;
        van = ent.getLocation();
        naar = grond.get(i).clone();
        onderweg = duur = 20;
        w().playSound(van, Sound.ENTITY_BLAZE_BURN, 1f, 0.7f);
    }

    /** Meteen onder kuip {@code i} hangen (de test). */
    void zetBij(int i) {
        if (!leeft()) return;
        bij = i;
        onderweg = 0;
        tel = -100;
        ent.teleport(grond.get(i));
    }

    /** Een sintel naar {@code doel} gooien. */
    private void werp(Player doel) {
        Location start = ent.getLocation().add(0, ent.getHeight() * 0.55, 0);
        Vector r = doel.getLocation().add(0, 1.0, 0).toVector().subtract(start.toVector());
        if (r.lengthSquared() < 0.01) return;
        Vector stap = r.normalize().multiply(SINTEL_SNELHEID);
        ItemDisplay toon = w().spawn(start, ItemDisplay.class, dsp -> {
            dsp.setPersistent(false);
            dsp.addScoreboardTag(ZeldaSpel.TAG);
            dsp.setItemStack(new ItemStack(Material.FIRE_CHARGE));
            dsp.setBrightness(new Display.Brightness(15, 15));
            dsp.setBillboard(Display.Billboard.CENTER);
        });
        sintels.add(new Sintel(toon, start, stap));
        worpen++;
        w().playSound(start, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.8f);
    }

    /** Elke tik: zweven (een beetje op en neer), vliegen of neerploffen, en de sintels onderweg. */
    private void tikje() {
        if (!leeft()) return;
        tikjes++;
        if (onderweg > 0) {
            onderweg--;
            double f = 1 - onderweg / (double) duur;
            Location l = van.clone().add(naar.clone().subtract(van).toVector().multiply(f));
            l.setYaw(ent.getLocation().getYaw());
            ent.teleport(l);
            if (gedoofd == 0) w().spawnParticle(Particle.FLAME, l.clone().add(0, 1.2, 0), 4, 0.3, 0.4, 0.3, 0.01);
        } else if (gedoofd == 0) {
            Location l = grond.get(bij).clone().add(0, Math.sin(tikjes / 9.0) * 0.25, 0);
            l.setYaw((tikjes * 3) % 360);
            ent.teleport(l);
            if (tikjes % 4 == 0) w().spawnParticle(Particle.FLAME, l.clone().add(0, 1.6, 0), 3, 0.6, 0.9, 0.6, 0.01);
        }
        if (sintels.isEmpty()) return;
        ZeldaHoofdstuk.Kamer k = spel == null ? null : spel.kamerVan(this);
        List<Player> in = k == null ? List.of() : spel.in(k.binnen);
        for (var it = sintels.iterator(); it.hasNext(); ) {
            Sintel s = it.next();
            s.leeftijd++;
            s.plek.add(s.stap);
            s.toon.teleport(s.plek);
            w().spawnParticle(Particle.FLAME, s.plek, 2, 0.05, 0.05, 0.05, 0.005);
            if (s.leeftijd % 3 == 0) w().spawnParticle(Particle.SMOKE, s.plek, 1, 0.05, 0.05, 0.05, 0.005);
            boolean weg = s.leeftijd > 90 || !s.plek.getBlock().isPassable();
            for (Player p : in) {
                if (weg || p.getLocation().add(0, 1.0, 0).distance(s.plek) > SINTEL_RAAK) continue;
                weg = true;
                raak(p, s);
            }
            if (weg) {
                w().spawnParticle(Particle.LAVA, s.plek, 4, 0.2, 0.2, 0.2, 0);
                w().playSound(s.plek, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
                s.toon.remove();
                it.remove();
            }
        }
    }

    /** Een sintel raakt {@code p}: het Gloedschild ervoor = niets; een gewoon schild = de helft; anders schade. */
    private void raak(Player p, Sintel s) {
        boolean schild = p.isBlocking();
        if (schild) {
            Vector kijk = p.getLocation().getDirection().setY(0), naarSintel = s.plek.toVector().subtract(p.getLocation().toVector()).setY(0);
            schild = kijk.lengthSquared() > 0.01 && naarSintel.lengthSquared() > 0.01 && kijk.normalize().dot(naarSintel.normalize()) > 0.2;
        }
        boolean gloed = schild && spel.voortgang.van(p.getUniqueId()).heeft(ZeldaSpel.GLOEDSCHILD);
        if (gloed) {
            geblokt++;
            p.playSound(p, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            w().spawnParticle(Particle.FLAME, s.plek, 12, 0.3, 0.3, 0.3, 0.06);
            if (!schildGezegd) {
                schildGezegd = true;
                spel.vertel(p, ZeldaHoofdstuk.r("Wiske", "Het Gloedschild houdt het tegen!"));
            }
            return;
        }
        p.damage(schild ? SINTEL / 2 : SINTEL, ent);
    }

    /**
     * Een pijl raakt een blok: de kuip (of de ketting erboven) kantelt. Hangt hij eronder, dan dooft het water hem.
     */
    @Override
    boolean raakBlok(ZeldaSpel spel, Player p, Block b) {
        this.spel = spel;
        for (int i = 0; i < kuipen.size(); i++) {
            int[] k = kuipen.get(i);
            if (b.getX() == k[0] && b.getZ() == k[2] && b.getY() >= k[1] && b.getY() <= k[1] + 2) {
                kantel(spel, p, i);
                return true;
            }
        }
        return false;
    }

    /** Een bom bij een kuip: hij kantelt. */
    @Override
    void boem(ZeldaSpel spel, Player p, Location l) {
        this.spel = spel;
        for (int i = 0; i < kuipen.size(); i++) if (vol[i] && l.distance(kuip(i)) <= KUIP_RAAK) kantel(spel, p, i);
    }

    /** Kuip {@code i} kantelt: water stort naar beneden. Hangt Asgrim eronder, dan is hij gedoofd. */
    void kantel(ZeldaSpel spel, Player p, int i) {
        if (!vol[i]) return;
        zetKuip(i, false);
        terugOp[i] = System.currentTimeMillis() + KUIP_WEG;
        Location k = kuip(i);
        w().playSound(k, Sound.ITEM_BUCKET_EMPTY, 1.2f, 0.7f);
        for (double y = k.getY() - 0.5; y > landing.get(i).getY(); y -= 0.6) {
            w().spawnParticle(Particle.FALLING_WATER, k.getX(), y, k.getZ(), 6, 0.35, 0.2, 0.35, 0);
            w().spawnParticle(Particle.SPLASH, k.getX(), y, k.getZ(), 4, 0.3, 0.2, 0.3, 0.1);
        }
        Location onder = grond.get(i).clone();
        w().spawnParticle(Particle.CLOUD, onder.clone().add(0, -1, 0), 30, 1.0, 0.4, 1.0, 0.03);
        w().playSound(onder, Sound.BLOCK_LAVA_EXTINGUISH, 1.2f, 0.8f);
        Location hier = ent == null ? null : ent.getLocation();
        boolean onderDeKuip = hier != null && Math.hypot(hier.getX() - k.getX(), hier.getZ() - k.getZ()) <= ONDER;
        if (leeft() && gedoofd == 0 && onderDeKuip && (onderweg == 0 || bij == i)) doof(spel, i);
        else if (!misGezegd && leeft()) {
            misGezegd = true;
            for (Player q : spel.in(spel.kamerVan(this).binnen)) spel.vertel(q, ZeldaHoofdstuk.r("Wiske", "Mis! Hij moet eronder hangen."));
        }
    }

    /** Gedoofd: hij ploft neer op de rand bij de kuip, grijs en rokend, en een klap doet nu gewoon pijn. */
    private void doof(ZeldaSpel spel, int i) {
        gedoofd = GEDOOFD;
        dovingen++;
        tel = 0;
        bij = i;
        van = ent.getLocation();
        naar = landing.get(i).clone();
        naar.setYaw(van.getYaw());
        onderweg = duur = 8;
        ent.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, GEDOOFD * 20 + 10, 0, false, false));
        for (Sintel s : sintels) s.toon.remove();
        sintels.clear();
        w().playSound(van, Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 0.6f);
        w().playSound(van, Sound.ENTITY_BLAZE_HURT, 1f, 0.5f);
        w().spawnParticle(Particle.CLOUD, van.clone().add(0, 1.5, 0), 40, 0.6, 1.0, 0.6, 0.05);
        if (!doofGezegd) {
            doofGezegd = true;
            for (Player q : spel.in(spel.kamerVan(this).binnen)) spel.vertel(q, ZeldaHoofdstuk.r("Wiske", "Gedoofd! Nu slaan!"));
        }
    }

    /** Hij laait weer op en vliegt naar een andere kuip. */
    private void opvlammen() {
        Location l = ent.getLocation();
        w().spawnParticle(Particle.FLAME, l.clone().add(0, 1.5, 0), 50, 0.6, 1.0, 0.6, 0.08);
        w().playSound(l, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.6f);
        ent.removePotionEffect(PotionEffectType.GLOWING);
        worp = WERP;
        vlieg(volgende());
    }

    /** Door zijn vlammenschild komt bijna niets; gedoofd alles. */
    @Override
    double schade(double schade) {
        return ZeldaRegels.asgrimSchade(schade, gedoofd > 0);
    }

    /** Pijlen op hem sissen weg in zijn vlammen. */
    @Override
    void geraakt() {
        if (leeft() && gedoofd == 0) w().playSound(ent.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.6f);
    }

    /** De vuurstaf: hij slokt de vlam op en laait feller (geen schade). */
    @Override
    boolean vlam(ZeldaSpel spel, Player p) {
        if (!leeft()) return false;
        w().spawnParticle(Particle.FLAME, ent.getLocation().add(0, 1.5, 0), 30, 0.5, 0.8, 0.5, 0.05);
        w().playSound(ent.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.8f);
        if (!vlamGezegd) {
            vlamGezegd = true;
            spel.vertel(p, ZeldaHoofdstuk.r("Wiske", "Vuur maakt hem alleen maar feller!"));
        }
        return true;
    }

    /** De nevel trekt uit hem weg: rook, dan een zacht licht. */
    @Override
    void weg(Location l) {
        if (taak != null) taak.cancel();
        taak = null;
        for (Sintel s : sintels) s.toon.remove();
        sintels.clear();
        World w = l.getWorld();
        w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1.5, 0), 60, 0.8, 1.2, 0.8, 0.03);
        w.spawnParticle(Particle.FLAME, l.clone().add(0, 1.5, 0), 40, 0.5, 1.0, 0.5, 0.04);
        w.spawnParticle(Particle.END_ROD, l.clone().add(0, 1.5, 0), 30, 0.6, 0.8, 0.6, 0.05);
        w.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 1.4f, 0.5f);
        w.playSound(l, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
    }
}
