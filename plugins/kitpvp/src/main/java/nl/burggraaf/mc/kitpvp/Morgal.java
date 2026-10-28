package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spellcaster;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Morgal, de Nachtvorst: de laatste baas (eiland 5b, de troonzaal boven in zijn toren). Drie fases, met even adem ertussen
 * (dan doet niemand iets, en Wiske geeft je wat licht terug):
 * <ol>
 *   <li><b>Nevelgeesten.</b> Hij zweeft hoog boven zijn troon, in zijn nevel: daar kom je niet bij. Hij roept geesten op (twee
 *   golven); licht doet ze dubbel pijn. Zijn ze weg, dan zakt hij naar beneden.</li>
 *   <li><b>Duistere bollen.</b> Hij loopt heen en weer voor zijn troon en gooit langzame bollen van nevel. Sla er een terug met
 *   je zwaard (een zwaai als hij vlakbij is): raakt hij Morgal, dan is die even versuft en doet een klap alles. Anders houdt
 *   zijn mantel bijna alles tegen. Onder de helft van zijn leven vlucht hij omhoog.</li>
 *   <li><b>Licht in zijn nevelhart.</b> Hij zweeft in een kringetje midden in de zaal, gehuld in zijn nevelhart. Alleen licht
 *   komt erdoor: een lichtpijl doet het volle (en tegen nevel het dubbele). Hij gooit sneller; een teruggeslagen bol laat hem
 *   neerploffen, dan mag het zwaard ook.</li>
 * </ol>
 * Verslagen = hij verdwijnt in zijn eigen nevel, en de nacht is voorbij. De balans staat in {@link ZeldaRegels#morgalSchade}
 * en de gedeelde {@link ZeldaRegels#SCHADE} en {@link ZeldaRegels#BAAS_LEVEN}.
 */
final class Morgal extends ZeldaBaas {

    static final double SCHAAL = 1.6;
    /** Seconden adem tussen twee fases, en hoe lang hij versuft is (in fase 2, en in fase 3 op de grond). */
    static final int ADEM = 6, VERSUFT = 5, VERSUFT_HART = 4;
    /** Een bol: schade (halve harten, vóór de balans en je harnas), snelheid (blokken per tik), teruggeslagen sneller; hoe dichtbij hij raakt. */
    static final double BOL_SCHADE = 5, BOL_SNELHEID = 0.24, TERUG_SNELHEID = 0.55, BOL_RAAK = 1.25;
    /** Om de zoveel seconden een bol (fase 2, fase 3), en om de zoveel verschuift hij in fase 2. */
    static final int WERP = 4, WERP_HART = 3, VERSCHUIF = 6;
    /** Onder dit deel van zijn leven vlucht hij omhoog (fase 3). */
    static final float HELFT = 0.5f;
    /** Geesten per golf in fase 1 (plus één per extra speler). */
    static final int[] GOLVEN = {2, 3};

    final Location boven, hart, neer;
    final List<Location> vloer, geestPlekken;
    private final Plugin plugin;
    int fase = ZeldaRegels.MORGAL_GEESTEN, golf, adem, versuft, werp, wacht, hartTreffers, teruggeslagen, geraakt;
    private int volgende, bij, verschuif, tikjes, onderweg, duur;
    private boolean hartGeest;
    private Location van, naar;
    private ZeldaSpel spel;
    private BukkitTask taak;
    final List<Bol> bollen = new ArrayList<>();
    final List<LivingEntity> geesten = new ArrayList<>();
    private final java.util.Set<String> gezegd = new java.util.HashSet<>();

    /** Een duistere bol onderweg (of teruggeslagen: dan is hij van licht). */
    static final class Bol {
        final ItemDisplay toon;
        Location plek;
        Vector stap;
        boolean terug;
        int leeftijd;

        Bol(ItemDisplay toon, Location plek, Vector stap) {
            this.toon = toon;
            this.plek = plek;
            this.stap = stap;
        }
    }

    /**
     * @param boven        fase 1: hoog boven de troon (daar verschijnt hij)
     * @param vloer        fase 2: de plekken voor de troon waar hij heen en weer loopt (minstens twee)
     * @param hart         fase 3: het midden van de zaal, hoog (hij zweeft er in een kringetje omheen)
     * @param neer         waar hij neerploft als een teruggeslagen bol hem in fase 3 raakt
     * @param geestPlekken waar zijn geesten verschijnen
     */
    Morgal(Plugin plugin, String naam, Location boven, List<Location> vloer, Location hart, Location neer, List<Location> geestPlekken,
           List<ZeldaHoofdstuk.Regel> bijWinst) {
        super(naam, List.of(), List.of(boven), List.of(), bijWinst);
        if (vloer.size() < 2 || geestPlekken.isEmpty()) throw new IllegalArgumentException("Morgal: minstens twee plekken op de vloer en een plek voor geesten");
        this.plugin = plugin;
        this.boven = boven;
        this.vloer = vloer;
        this.hart = hart;
        this.neer = neer;
        this.geestPlekken = geestPlekken;
        soort = ZeldaHoofdstuk.Soort.MORGAL;
        helper = ZeldaHoofdstuk.Soort.NEVELGEEST;
        kleur = BossBar.Color.PURPLE;
        naamKleur = 0xb48ae0;
        leven = 80;
        levenPerSpeler = 40;
        oproepen = new int[]{0, 0};                                      // zijn geesten roept hij zelf op (fase 1)
    }

    private World w() {
        return boven.getWorld();
    }

    /** Het midden van zijn lijf (waar een teruggeslagen bol heen vliegt). */
    Location midden() {
        return ent.getLocation().add(0, ent.getHeight() * 0.55, 0);
    }

    private ZeldaHoofdstuk.Kamer kamer() {
        return spel == null ? null : spel.kamerVan(this);
    }

    private List<Player> in() {
        ZeldaHoofdstuk.Kamer k = kamer();
        return k == null ? List.of() : spel.in(k.binnen);
    }

    /** Eén regel voor iedereen in de zaal, maar één keer per gevecht ({@code sleutel}). */
    private void zeg(String sleutel, ZeldaHoofdstuk.Regel... regels) {
        if (spel == null || !gezegd.add(sleutel)) return;
        for (Player p : in()) for (ZeldaHoofdstuk.Regel r : regels) spel.vertel(p, r);
    }

    @Override
    void start(LivingEntity e) {
        super.start(e);
        e.setAI(false);
        e.setGravity(false);
        e.setSilent(true);
        fase = ZeldaRegels.MORGAL_GEESTEN;
        golf = adem = versuft = wacht = hartTreffers = teruggeslagen = geraakt = verschuif = onderweg = 0;
        werp = WERP;
        bij = 1;
        hartGeest = false;
        gezegd.clear();
        geesten.clear();
        for (Bol b : bollen) b.toon.remove();
        bollen.clear();
        if (taak != null) taak.cancel();
        taak = Bukkit.getScheduler().runTaskTimer(plugin, this::tikje, 1L, 1L);
    }

    @Override
    void stop() {
        if (taak != null) taak.cancel();
        taak = null;
        for (Bol b : bollen) b.toon.remove();
        bollen.clear();
        geesten.clear();
        ZeldaHoofdstuk.Kamer k = kamer();
        if (k != null) k.muziek = ZeldaMuziek.MORGAL1;
        super.stop();
    }

    /** Elke seconde: versuft tellen, de fase (geesten, adem, bollen, nevelhart). */
    @Override
    void beweeg(ZeldaSpel spel, List<Player> in, float d) {
        this.spel = spel;
        if (in.isEmpty()) return;
        if (versuft > 0) {
            w().spawnParticle(Particle.CRIT, ent.getLocation().add(0, ent.getHeight() + 0.2, 0), 6, 0.4, 0.1, 0.4, 0.02);
            if (--versuft == 0) bijkomen();
            return;
        }
        switch (fase) {
            case ZeldaRegels.MORGAL_GEESTEN -> {
                ZeldaHoofdstuk.Kamer k = kamer();                                // met meer spelers geeft de kamer er een geest bij: die hoort bij de eerste golf
                if (k != null) for (LivingEntity e : k.vijanden) if (e != ent && !geesten.contains(e)) geesten.add(e);
                geesten.removeIf(g -> !g.isValid() || g.isDead());
                if (!geesten.isEmpty()) return;
                if (wacht > 0) wacht--;
                else if (golf < GOLVEN.length) roep(GOLVEN[golf++] + in.size() - 1);
                else naarAdem(ZeldaRegels.MORGAL_BOLLEN);
            }
            case ZeldaRegels.MORGAL_ADEM -> {
                if (--adem <= 0) begin(volgende);
            }
            case ZeldaRegels.MORGAL_BOLLEN -> {
                if (d < HELFT) {
                    naarAdem(ZeldaRegels.MORGAL_HART);
                    return;
                }
                if (++verschuif >= VERSCHUIF && onderweg == 0) {
                    verschuif = 0;
                    int n = (bij + 1 + rnd.nextInt(vloer.size() - 1)) % vloer.size();
                    loop(vloer.get(n), 24);
                    bij = n;
                }
                if (--werp <= 0) {
                    werp = WERP;
                    werp(dichtst(in));
                }
            }
            default -> {                                                    // het nevelhart
                if (!hartGeest && d < 0.25f) {
                    hartGeest = true;
                    roep(1);
                }
                if (--werp <= 0) {
                    werp = WERP_HART;
                    werp(dichtst(in));
                }
            }
        }
    }

    private Player dichtst(List<Player> in) {
        Player best = in.get(0);
        for (Player p : in) if (p.getLocation().distanceSquared(ent.getLocation()) < best.getLocation().distanceSquared(ent.getLocation())) best = p;
        return best;
    }

    /** Zijn geesten: {@code n} nevelgeesten verschijnen in de zaal. */
    private void roep(int n) {
        ZeldaHoofdstuk.Kamer k = kamer();
        if (k == null) return;
        spreuk(30);
        w().playSound(ent.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.7f);
        int spelers = Math.max(1, in().size());
        for (int i = 0; i < n; i++) {
            LivingEntity g = spel.spawn(ZeldaHoofdstuk.Soort.NEVELGEEST, geestPlekken.get((i + golf * 2) % geestPlekken.size()), spelers);
            k.vijanden.add(g);
            geesten.add(g);
        }
        if (golf <= 1) zeg("geesten", ZeldaHoofdstuk.r("Wiske", "Zijn geesten! Licht doet ze dubbel pijn."));
        wacht = 3;
    }

    /** Even adem: niemand doet iets, hij gaat naar zijn plek voor de volgende fase, en je krijgt wat licht terug. */
    private void naarAdem(int daarna) {
        fase = ZeldaRegels.MORGAL_ADEM;
        adem = ADEM;
        volgende = daarna;
        for (Bol b : bollen) b.toon.remove();
        bollen.clear();
        if (daarna == ZeldaRegels.MORGAL_BOLLEN) {
            vlieg(vloer.get(bij), 60);
            zeg("adem1", ZeldaHoofdstuk.r("Morgal", "Mijn geesten… Dan doe ik het zelf."),
                    ZeldaHoofdstuk.r("Wiske", "Hij komt naar beneden. Even op adem komen!"));
        } else {
            vlieg(hart, 40);
            zeg("adem2", ZeldaHoofdstuk.r("Morgal", "Genoeg! Nu zie je mijn nevelhart."),
                    ZeldaHoofdstuk.r("Wiske", "Hij vlucht omhoog. Adem even uit…"));
        }
        for (Player p : in()) spel.bijkomen(p, 6);
        w().playSound(ent.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
    }

    /** Een nieuwe fase begint (met zijn eigen muziek). */
    private void begin(int f) {
        fase = f;
        werp = 2;
        verschuif = 0;
        ZeldaHoofdstuk.Kamer k = kamer();
        if (k != null) k.muziek = f == ZeldaRegels.MORGAL_BOLLEN ? ZeldaMuziek.MORGAL2 : ZeldaMuziek.MORGAL3;
        if (f == ZeldaRegels.MORGAL_BOLLEN)
            zeg("bollen", ZeldaHoofdstuk.r("Wiske", "Een bol van nevel! Sla hem terug met je zwaard."));
        else zeg("hart", ZeldaHoofdstuk.r("Wiske", "Zijn nevelhart! Alleen licht komt erdoor. Je lichtpijlen!"));
    }

    /** Naar {@code l} zweven, in {@code tikken} tikken. */
    void vlieg(Location l, int tikken) {
        if (!leeft()) return;
        van = ent.getLocation();
        naar = l.clone();
        onderweg = duur = Math.max(1, tikken);
    }

    /** Naar {@code l} lopen (fase 2: langzaam, over de vloer). */
    private void loop(Location l, int tikken) {
        vlieg(l, tikken);
    }

    /** Armen omhoog (een spreuk), {@code tikken} tikken lang. */
    private void spreuk(int tikken) {
        if (ent instanceof Spellcaster s) {
            s.setSpell(Spellcaster.Spell.SUMMON_VEX);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (ent instanceof Spellcaster c && c.isValid()) c.setSpell(Spellcaster.Spell.NONE);
            }, tikken);
        }
    }

    /** Een duistere bol naar {@code doel} gooien. */
    void werp(Player doel) {
        if (!leeft() || doel == null) return;
        Location start = ent.getLocation().add(0, ent.getHeight() * 0.7, 0);
        Vector r = doel.getLocation().add(0, 1.1, 0).toVector().subtract(start.toVector());
        if (r.lengthSquared() < 0.01) return;
        spreuk(16);
        ItemDisplay toon = w().spawn(start, ItemDisplay.class, dsp -> {
            dsp.setPersistent(false);
            dsp.addScoreboardTag(ZeldaSpel.TAG);
            dsp.setItemStack(new ItemStack(Material.CRYING_OBSIDIAN));
            dsp.setBrightness(new Display.Brightness(12, 12));
            dsp.setBillboard(Display.Billboard.CENTER);
            dsp.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(0.7f, 0.7f, 0.7f), new org.joml.AxisAngle4f()));
        });
        bollen.add(new Bol(toon, start, r.normalize().multiply(BOL_SNELHEID)));
        w().playSound(start, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.6f);
    }

    /** Elke tik: zweven, lopen of vliegen, en de bollen onderweg. */
    private void tikje() {
        if (!leeft()) return;
        tikjes++;
        if (onderweg > 0) {
            onderweg--;
            double f = 1 - onderweg / (double) duur;
            f = f * f * (3 - 2 * f);
            Location l = van.clone().add(naar.clone().subtract(van).toVector().multiply(f));
            ent.teleport(kijk(l));
        } else if (versuft == 0) {
            Location l = switch (fase) {
                case ZeldaRegels.MORGAL_GEESTEN -> boven.clone().add(0, Math.sin(tikjes / 12.0) * 0.4, 0);
                case ZeldaRegels.MORGAL_HART -> hart.clone().add(Math.cos(tikjes / 40.0) * 3, Math.sin(tikjes / 15.0) * 0.4, Math.sin(tikjes / 40.0) * 3);
                case ZeldaRegels.MORGAL_BOLLEN -> vloer.get(bij);
                default -> ent.getLocation();
            };
            ent.teleport(kijk(l));
        }
        // zijn nevel: een zwarte mantel om hem heen; in fase 1 een koepel, in fase 3 een kloppend hart
        Particle.DustOptions zwart = new Particle.DustOptions(Color.fromRGB(0x15121c), 1.8f), paars = new Particle.DustOptions(Color.fromRGB(0x6a2d8f), 1.4f);
        Location m = ent.getLocation().add(0, ent.getHeight() * 0.5, 0);
        if (tikjes % 2 == 0) w().spawnParticle(Particle.DUST, m, 3, 0.5, 0.9, 0.5, 0, zwart);
        if (versuft == 0 && (fase == ZeldaRegels.MORGAL_GEESTEN || fase == ZeldaRegels.MORGAL_HART) && tikjes % 3 == 0) {
            double straal = fase == ZeldaRegels.MORGAL_HART ? 1.6 + 0.3 * Math.sin(tikjes / 4.0) : 2.4;
            for (int i = 0; i < 8; i++) {
                double a = tikjes / 10.0 + i * Math.PI / 4, b = Math.sin(tikjes / 7.0 + i) * 0.9;
                w().spawnParticle(Particle.DUST, m.clone().add(Math.cos(a) * straal, b * straal * 0.6, Math.sin(a) * straal), 1, 0, 0, 0, 0, i % 2 == 0 ? zwart : paars);
            }
        }
        if (bollen.isEmpty()) return;
        List<Player> in = in();
        for (var it = bollen.iterator(); it.hasNext(); ) {
            Bol b = it.next();
            b.leeftijd++;
            if (b.terug) b.stap = midden().toVector().subtract(b.plek.toVector()).normalize().multiply(TERUG_SNELHEID);   // licht vindt zijn weg
            b.plek.add(b.stap);
            b.toon.teleport(b.plek);
            if (b.terug) w().spawnParticle(Particle.END_ROD, b.plek, 2, 0.08, 0.08, 0.08, 0.01);
            else {
                w().spawnParticle(Particle.DUST, b.plek, 2, 0.15, 0.15, 0.15, 0, paars);
                if (b.leeftijd % 3 == 0) w().spawnParticle(Particle.SQUID_INK, b.plek, 1, 0.05, 0.05, 0.05, 0.005);
            }
            boolean weg = b.leeftijd > 160 || (!b.terug && !b.plek.getBlock().isPassable());
            if (!weg && b.terug && b.plek.distance(midden()) < 1.7) {
                weg = true;
                raak();
            } else if (!weg && !b.terug)
                for (Player p : in) {
                    if (p.getLocation().add(0, 1.0, 0).distance(b.plek) > BOL_RAAK) continue;
                    weg = true;
                    treft(p, b);
                    break;
                }
            if (weg) {
                w().spawnParticle(b.terug ? Particle.FLASH : Particle.LARGE_SMOKE, b.plek, b.terug ? 1 : 8, 0.2, 0.2, 0.2, 0.01, b.terug ? Color.WHITE : null);
                b.toon.remove();
                it.remove();
            }
        }
    }

    /** Hij kijkt naar de dichtstbijzijnde speler. */
    private Location kijk(Location l) {
        List<Player> in = in();
        if (!in.isEmpty()) l.setDirection(dichtst(in).getLocation().toVector().subtract(l.toVector()).setY(0).lengthSquared() < 0.01
                ? new Vector(-1, 0, 0) : dichtst(in).getLocation().toVector().subtract(l.toVector()).setY(0));
        return l;
    }

    /** Een bol raakt {@code p}: je schild ervoor houdt hem tegen, anders schade. */
    private void treft(Player p, Bol b) {
        boolean schild = p.isBlocking();
        if (schild) {
            Vector k = p.getLocation().getDirection().setY(0), naarBol = b.plek.toVector().subtract(p.getLocation().toVector()).setY(0);
            schild = k.lengthSquared() > 0.01 && naarBol.lengthSquared() > 0.01 && k.normalize().dot(naarBol.normalize()) > 0.2;
        }
        if (schild) {
            p.playSound(p, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            return;
        }
        geraakt++;
        p.damage(BOL_SCHADE, ent);
        if (gezegd.add("tip-bol")) spel.vertel(p, ZeldaHoofdstuk.r("Wiske", "Wacht tot hij vlakbij is. Dan zwaaien!"));
    }

    /**
     * Een zwaai met het zwaard: is er een bol vlakbij en (ongeveer) voor je, dan sla je hem terug (hij wordt licht en vliegt naar
     * Morgal).
     */
    @Override
    boolean zwaai(ZeldaSpel spel, Player p) {
        this.spel = spel;
        Location oog = p.getEyeLocation();
        Vector kijk = oog.getDirection();
        Bol best = null;
        double bd = Double.MAX_VALUE;
        for (Bol b : bollen) {
            if (b.terug) continue;
            double[] o = {oog.getX(), oog.getY(), oog.getZ()}, k = {kijk.getX(), kijk.getY(), kijk.getZ()}, q = {b.plek.getX(), b.plek.getY(), b.plek.getZ()};
            double d = b.plek.distanceSquared(oog);
            if (d < bd && ZeldaRegels.terugslaan(o, k, q, ZeldaRegels.ZWAAI_BEREIK, ZeldaRegels.ZWAAI_HOEK)) {
                bd = d;
                best = b;
            }
        }
        if (best == null) return false;
        best.terug = true;
        best.leeftijd = 0;
        best.toon.setItemStack(new ItemStack(Material.SEA_LANTERN));
        best.toon.setBrightness(new Display.Brightness(15, 15));
        teruggeslagen++;
        w().playSound(best.plek, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.2f, 1.6f);
        w().playSound(best.plek, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.2f);
        w().spawnParticle(Particle.END_ROD, best.plek, 12, 0.2, 0.2, 0.2, 0.08);
        return true;
    }

    /** Een teruggeslagen bol raakt hem: versuft (in fase 3 ploft hij neer op de vloer). */
    private void raak() {
        if (!leeft() || versuft > 0 || fase == ZeldaRegels.MORGAL_ADEM || fase == ZeldaRegels.MORGAL_GEESTEN) return;
        versuft = fase == ZeldaRegels.MORGAL_HART ? VERSUFT_HART : VERSUFT;
        onderweg = 0;
        if (fase == ZeldaRegels.MORGAL_HART) vlieg(neer, 10);
        else ent.teleport(kijk(vloer.get(bij).clone()));
        ent.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, versuft * 20 + 10, 0, false, false));
        if (ent instanceof Spellcaster s) s.setSpell(Spellcaster.Spell.NONE);
        w().playSound(ent.getLocation(), Sound.ENTITY_EVOKER_HURT, 1f, 0.6f);
        w().spawnParticle(Particle.FLASH, midden(), 1, 0, 0, 0, 0, Color.WHITE);
        zeg("versuft", ZeldaHoofdstuk.r("Wiske", "Raak! Hij is versuft. Nu slaan!"));
    }

    /** Hij komt bij uit zijn versuftheid (fase 3: hij vliegt weer omhoog). */
    private void bijkomen() {
        ent.removePotionEffect(PotionEffectType.GLOWING);
        werp = fase == ZeldaRegels.MORGAL_HART ? WERP_HART : WERP;
        if (fase == ZeldaRegels.MORGAL_HART) vlieg(hart, 30);
        w().playSound(ent.getLocation(), Sound.ENTITY_EVOKER_AMBIENT, 1f, 0.6f);
    }

    /** Door zijn nevel komt bijna niets; zie {@link ZeldaRegels#morgalSchade}. Buiten fase 3 gaat hij nooit dood. */
    @Override
    double schade(double schade, Entity door) {
        if (!leeft()) return schade;
        boolean licht = door != null && door.getScoreboardTags().contains(ZeldaSpel.LICHT);
        double x = ZeldaRegels.morgalSchade(schade, fase, versuft > 0, licht);
        var max = ent.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double hp = ent.getHealth();
        if (fase == ZeldaRegels.MORGAL_BOLLEN && max != null) x = Math.min(x, Math.max(0, hp - max.getValue() * (HELFT - 0.02)));   // fase 2 eindigt bij de helft
        if (fase != ZeldaRegels.MORGAL_HART) x = Math.min(x, Math.max(0, hp - 1));
        if (fase == ZeldaRegels.MORGAL_HART && licht && x > 0) {
            hartTreffers++;
            w().spawnParticle(Particle.END_ROD, midden(), 20, 0.4, 0.6, 0.4, 0.06);
            w().playSound(ent.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.8f);
            zeg("hartraak", ZeldaHoofdstuk.r("Wiske", "Raak! Zijn nevelhart scheurt!"));
        } else if (x < schade * 0.5) w().playSound(ent.getLocation(), Sound.BLOCK_SCULK_BREAK, 0.8f, 0.6f);   // de nevel houdt het tegen
        return x;
    }

    @Override
    double schade(double schade) {
        return schade(schade, null);
    }

    /** Hij verdwijnt in zijn eigen nevel: een draaikolk van zwart die krimpt, dan een flits van licht. Zijn geesten lossen op. */
    @Override
    void weg(Location l) {
        if (taak != null) taak.cancel();
        taak = null;
        for (Bol b : bollen) b.toon.remove();
        bollen.clear();
        for (LivingEntity g : geesten) if (g.isValid()) {
            g.getWorld().spawnParticle(Particle.END_ROD, g.getLocation().add(0, 0.4, 0), 10, 0.2, 0.2, 0.2, 0.04);
            g.remove();
        }
        geesten.clear();
        World w = l.getWorld();
        Location m = l.clone().add(0, 1.6, 0);
        Particle.DustOptions zwart = new Particle.DustOptions(Color.fromRGB(0x15121c), 2.2f);
        w.playSound(l, Sound.ENTITY_EVOKER_DEATH, 1f, 0.5f);
        final int[] t = {0};
        final BukkitTask[] kolk = {null};
        kolk[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            t[0]++;
            double r = Math.max(0.1, 3.5 * (1 - t[0] / 40.0));
            for (int i = 0; i < 10; i++) {
                double a = t[0] / 3.0 + i * Math.PI / 5;
                w.spawnParticle(Particle.DUST, m.clone().add(Math.cos(a) * r, (i - 5) * 0.25 * r, Math.sin(a) * r), 1, 0, 0, 0, 0, zwart);
            }
            if (t[0] >= 40) {
                kolk[0].cancel();
                w.spawnParticle(Particle.FLASH, m, 2, 0.2, 0.2, 0.2, 0, Color.WHITE);
                w.spawnParticle(Particle.END_ROD, m, 80, 1.2, 1.2, 1.2, 0.15);
                w.playSound(m, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
                w.playSound(m, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.8f);
            }
        }, 1L, 1L);
        if (spel != null) for (Player p : spel.in(kamer() != null ? kamer().binnen : ZeldaHoofdstuk.Box.van(l.getBlockX(), l.getBlockY(), l.getBlockZ(), l.getBlockX(), l.getBlockY(), l.getBlockZ())))
            spel.muziek.speel(p, ZeldaMuziek.DAGERAAD);
    }
}
