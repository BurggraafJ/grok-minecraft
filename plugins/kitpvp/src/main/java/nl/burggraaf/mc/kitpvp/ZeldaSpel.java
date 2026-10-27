package nl.burggraaf.mc.kitpvp;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * De motor van Story of Zelda: wie er speelt (een {@link Sessie} per speler), de zones van het hoofdstuk (rustplaats,
 * verhaal, vondst, kamer, uitdaging), vijanden, de baas, muziek, de verteller (alles wat spelers lezen staat op één
 * plek: de actiebalk, één korte grijze regel tegelijk), uitrusting uit de voortgang, en de kaart (reizen tussen
 * bereikte rustplaatsen). KitPvP laat spelers in {@link Toestand.Plek#SPEL} met rust; dit regelt schade, honger,
 * doodgaan, drops en grenzen voor wie in Zelda is.
 */
final class ZeldaSpel implements Listener {

    static final String TAG = "zelda", NPC = "zelda-npc";
    static final TextColor GROEN = TextColor.color(0x8fcf6a);
    private static final int BASIS_HARTEN = 8;

    /** Per speler in Zelda. */
    static final class Sessie {
        final ZeldaHoofdstuk h;
        int rust;                                            // waar je na doodgaan terugkomt
        final Deque<Component> rij = new ArrayDeque<>();
        Component nu;
        long tot, gestuurd, volgendWoud, laatsteHemel = -1;

        Sessie(ZeldaHoofdstuk h, int rust) {
            this.h = h;
            this.rust = rust;
        }
    }

    /** Herkenning van het kaartmenu. */
    static final class Menu implements InventoryHolder {
        Inventory inv;

        public Inventory getInventory() {
            return inv;
        }
    }

    final KitPvP plugin;
    final Zelda modus;
    final List<ZeldaHoofdstuk> hoofdstukken;
    final ZeldaMuziek muziek = new ZeldaMuziek();
    final Map<UUID, Sessie> sessies = new HashMap<>();
    ZeldaVoortgang voortgang;
    private final NamespacedKey itemKey, hartKey;
    private final Random rnd = new Random();
    private final List<BukkitTask> taken = new ArrayList<>();
    private long tikken;

    ZeldaSpel(KitPvP plugin, Zelda modus, List<ZeldaHoofdstuk> hoofdstukken) {
        this.plugin = plugin;
        this.modus = modus;
        this.hoofdstukken = hoofdstukken;
        this.itemKey = new NamespacedKey(plugin, "zelda");
        this.hartKey = new NamespacedKey(plugin, "zelda-harten");
    }

    void aan() {
        voortgang = new ZeldaVoortgang(new java.io.File(plugin.getDataFolder(), "zelda.yml"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        taken.add(Bukkit.getScheduler().runTaskTimer(plugin, this::elkeTik, 20L, 1L));
    }

    void uit() {
        taken.forEach(BukkitTask::cancel);
        taken.clear();
        for (UUID id : new ArrayList<>(sessies.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) weg(p);
        }
        for (ZeldaHoofdstuk h : hoofdstukken) {
            for (ZeldaHoofdstuk.Kamer k : h.kamers) k.vijanden.forEach(Entity::remove);
            for (ZeldaHoofdstuk.Vondst v : h.vondsten) if (v.toon != null) v.toon.remove();
            for (ZeldaHoofdstuk.Persoon p : h.personen) if (p.ent != null) p.ent.remove();
        }
        if (plugin.w != null) for (Entity e : plugin.w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        bewaar();
    }

    ZeldaHoofdstuk hoofdstuk(String id) {
        for (ZeldaHoofdstuk h : hoofdstukken) if (h.id().equals(id)) return h;
        return hoofdstukken.get(0);
    }

    boolean speelt(Player p) {
        return sessies.containsKey(p.getUniqueId());
    }

    Sessie sessie(Player p) {
        return sessies.get(p.getUniqueId());
    }

    // ── binnenkomen en weggaan ──────────────────────────────────────────────────────────────

    /** Speler komt binnen (via de poort of het menu, KitPvP heeft hem al in SPEL gezet). */
    void binnen(Player p) {
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        if (!p.getName().equals(s.naam)) {
            s.naam = p.getName();
            voortgang.vuil();
        }
        ZeldaHoofdstuk h = hoofdstuk(s.hoofdstuk);
        int r = Math.max(0, Math.min(s.rust(h.id()), h.rust.size() - 1));
        Sessie ss = new Sessie(h, r);
        ss.volgendWoud = System.currentTimeMillis() + 40_000L;
        sessies.put(p.getUniqueId(), ss);
        for (String v : h.rust.get(r).geeft) if (s.zet(v)) voortgang.vuil();
        uitrusten(p, s);
        herstel(p);
        Location l = h.rust.get(r).plek;
        p.teleport(l);
        p.setFallDistance(0);
        hemel(p, ss, l);
        muziek.speel(p, ZeldaMuziek.BEGIN);
        if (s.heeft("v:intro")) zeg(p, Component.text(h.naam() + " · " + h.rust.get(r).naam + " · vak 8 = terug naar de hub", NamedTextColor.GRAY));
    }

    /** Speler gaat weg (hub, andere modus, uitloggen). */
    void weg(Player p) {
        Sessie ss = sessies.remove(p.getUniqueId());
        muziek.vergeet(p.getUniqueId());
        if (ss != null) for (ZeldaHoofdstuk.Kamer k : ss.h.kamers) if (k.baas != null && k.baas.balk != null) p.hideBossBar(k.baas.balk);
        for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) u.loopt.remove(p.getUniqueId());
        p.resetPlayerTime();
        p.resetPlayerWeather();
        p.getInventory().clear();
        p.setLevel(0);
        p.setExp(0f);
        p.sendActionBar(Component.empty());
        harten(p, -1);
        bewaar();
    }

    /** Vol leven, geen vuur, honger vast op 17 (geen vanzelf genezen: rust, harten van vijanden en gewonnen kamers wel). */
    private void herstel(Player p) {
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (!p.isDead() && max != null) p.setHealth(max.getValue());
        p.setFireTicks(0);
        p.setFoodLevel(17);
        p.setSaturation(0f);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
    }

    private void geneesBij(Player p, double hp) {
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || p.isDead()) return;
        p.setHealth(Math.min(max.getValue(), p.getHealth() + hp));
    }

    Location respawn(Player p) {
        Sessie ss = sessie(p);
        return ss == null ? null : ss.h.rust.get(ss.rust).plek;
    }

    void bewaar() {
        if (voortgang == null || !voortgang.isVuil()) return;
        try {
            voortgang.bewaar();
        } catch (IOException e) {
            plugin.getLogger().warning("zelda.yml: " + e.getMessage());
        }
    }

    // ── uitrusting (volgt uit de voortgang) ─────────────────────────────────────────────────

    private ItemStack ding(Material m, String naam, String soort) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(naam, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, soort);
        if (meta instanceof LeatherArmorMeta lm) lm.setColor(soort.equals("laarzen") ? Color.fromRGB(0x5b3a22) : Color.fromRGB(0x3f8a2e));
        it.setItemMeta(meta);
        return it;
    }

    void uitrusten(Player p, ZeldaVoortgang.Stand s) {
        var inv = p.getInventory();
        inv.clear();
        inv.setItem(0, s.heeft("zwaard") ? ding(Material.IRON_SWORD, "Wachterszwaard", "zwaard") : ding(Material.WOODEN_SWORD, "Houten zwaard", "zwaard"));
        if (s.heeft("boog")) {
            ItemStack boog = ding(Material.BOW, "Jagersboog", "boog");
            boog.addUnsafeEnchantment(Enchantment.INFINITY, 1);
            inv.setItem(1, boog);
            inv.setItem(17, ding(Material.ARROW, "Pijl", "pijl"));
        }
        if (s.heeft("schild")) inv.setItemInOffHand(ding(Material.SHIELD, "Eikenschild", "schild"));
        inv.setItem(8, ding(Material.MAP, "Kaart", "kaart"));
        inv.setItem(Menus.HUB_VAK, plugin.hubItem());
        inv.setHelmet(ding(Material.LEATHER_HELMET, "Muts", "muts"));
        inv.setChestplate(ding(Material.LEATHER_CHESTPLATE, "Groene tuniek", "tuniek"));
        inv.setBoots(ding(Material.LEATHER_BOOTS, "Laarzen", "laarzen"));
        inv.setHeldItemSlot(0);
        int extra = 0;
        for (String v : s.vlaggen) if (v.startsWith("hart")) extra++;
        harten(p, BASIS_HARTEN + extra);
    }

    /** Zet het aantal harten (via een eigen modifier, zodat het buiten Zelda nooit blijft hangen); -1 = weg. */
    private void harten(Player p, int harten) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        a.removeModifier(hartKey);
        if (harten > 0) a.addModifier(new AttributeModifier(hartKey, harten * 2.0 - a.getBaseValue(), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
    }

    // ── verteller: één regel tegelijk op de actiebalk ───────────────────────────────────────

    void vertel(Player p, ZeldaHoofdstuk.Regel r) {
        Component c = r.spreker() == null ? Component.text(r.tekst(), NamedTextColor.GRAY)
                : Component.text(r.spreker(), GROEN).append(Component.text(" · " + r.tekst(), NamedTextColor.GRAY));
        zeg(p, c);
    }

    void zeg(Player p, Component c) {
        Sessie ss = sessie(p);
        if (ss == null) return;
        if (ss.rij.size() < 8) ss.rij.add(c);
    }

    private void verteller(long nu) {
        for (var e : sessies.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            Sessie ss = e.getValue();
            if (p == null) continue;
            if (nu >= ss.tot) {
                ss.nu = ss.rij.poll();
                if (ss.nu == null) continue;
                int len = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(ss.nu).length();
                ss.tot = nu + Math.max(2600, Math.min(6000, 1600 + 55L * len));
                ss.gestuurd = nu;
                p.sendActionBar(ss.nu);
                String tekst = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(ss.nu);
                if (tekst.startsWith("Wiske ·")) wiske(p, 6);
            } else if (ss.nu != null && nu - ss.gestuurd >= 1500) {
                ss.gestuurd = nu;
                p.sendActionBar(ss.nu);
            }
        }
    }

    /** Wiske, het vuurvliegje: een klein lichtje naast je schouder, alleen voor jou te zien. */
    private void wiske(Player p, int n) {
        Location l = p.getEyeLocation();
        org.bukkit.util.Vector links = l.getDirection().crossProduct(new org.bukkit.util.Vector(0, 1, 0)).normalize().multiply(-0.7);
        l.add(links).add(0, 0.25 + Math.sin(tikken / 9.0) * 0.08, 0);
        p.spawnParticle(Particle.END_ROD, l, n, 0.06, 0.06, 0.06, 0.004);
    }

    // ── de klok ─────────────────────────────────────────────────────────────────────────────

    private void elkeTik() {
        tikken++;
        muziek.tik();
        long nu = System.currentTimeMillis();
        if (tikken % 5 == 0) {
            for (var e : new ArrayList<>(sessies.entrySet())) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null) sessies.remove(e.getKey());
                else if (!p.isDead()) zones(p, e.getValue(), nu);
            }
            for (ZeldaHoofdstuk h : hoofdstukken) {
                for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) uitdaging(u, nu);
                for (ZeldaHoofdstuk.Reeks r : h.reeksen) reeks(r, nu);
            }
        }
        if (tikken % 10 == 0) {
            verteller(nu);
            for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Kamer k : h.kamers) kamer(k, nu);
        }
        if (tikken % 20 == 0) {
            for (var e : new ArrayList<>(sessies.entrySet())) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) seconde(p, e.getValue(), nu);
            }
            for (ZeldaHoofdstuk h : hoofdstukken) decor(h);
            if (tikken % 200 == 0) bewaar();
        }
    }

    /** Elke seconde per speler: hemel, grenzen, rust genezen, af en toe een zacht motief. */
    private void seconde(Player p, Sessie ss, long nu) {
        Location l = p.getLocation();
        if (p.isDead()) return;
        if (!ss.h.speelgebied.in(l) || !l.getWorld().equals(ss.h.w)) {
            p.setFallDistance(0);
            p.teleport(ss.h.rust.get(ss.rust).plek);
            return;
        }
        hemel(p, ss, l);
        if (tikken % 60 == 0) wiske(p, 1);
        for (ZeldaHoofdstuk.Rust r : ss.h.rust) if (r.zone.in(l)) geneesBij(p, 2);
        if (p.getFoodLevel() != 17) p.setFoodLevel(17);
        if (nu > ss.volgendWoud && !muziek.speelt(p)) {
            ss.volgendWoud = nu + 45_000L + rnd.nextInt(35_000);
            muziek.speel(p, ZeldaMuziek.WOUD);
        }
    }

    private void hemel(Player p, Sessie ss, Location l) {
        long t = ss.h.hemel(l);
        for (ZeldaHoofdstuk.Kamer k : ss.h.kamers) if (k.hemel >= 0 && k.binnen.in(l)) t = k.hemel;
        if (Math.abs(t - ss.laatsteHemel) > 40) {
            ss.laatsteHemel = t;
            p.setPlayerTime(t, false);
        }
    }

    // ── zones ───────────────────────────────────────────────────────────────────────────────

    private void zones(Player p, Sessie ss, long nu) {
        Location l = p.getLocation();
        ZeldaHoofdstuk h = ss.h;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        for (ZeldaHoofdstuk.Rust r : h.rust) if (r.zone.in(l)) rust(p, ss, s, r);
        for (ZeldaHoofdstuk.Verhaal v : h.verhalen)
            if (v.zone.in(l) && !s.heeft("v:" + v.id) && (v.vereist == null || s.heeft(v.vereist))) verhaal(p, ss, s, v);
        for (ZeldaHoofdstuk.Vondst v : h.vondsten) if (v.zone.in(l) && !s.heeft(v.vlag)) vind(p, s, v);
        for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) {
            Long start = u.loopt.get(p.getUniqueId());
            if (u.start.in(l)) {
                if (start == null || nu - start > 600) begin(p, u, nu);
            } else if (start != null && u.finish.in(l)) gehaald(p, s, u, nu - start);
        }
        for (ZeldaHoofdstuk.Kamer k : h.kamers) if (k.fase == ZeldaHoofdstuk.Fase.RUST && k.start.in(l)) begin(k, nu);
    }

    private void rust(Player p, Sessie ss, ZeldaVoortgang.Stand s, ZeldaHoofdstuk.Rust r) {
        ss.rust = r.index;
        boolean nieuw = false;
        for (String v : r.geeft) nieuw |= s.zet(v);
        if (nieuw) uitrusten(p, s);
        if (s.bereik(ss.h.id(), r.index)) {
            s.hoofdstuk = ss.h.id();
            voortgang.vuil();
            bewaar();
            zeg(p, Component.text("Rustplaats · " + r.naam, NamedTextColor.GRAY));
            muziek.speel(p, ZeldaMuziek.RUST);
            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.02);
            herstel(p);
        } else if (nieuw) voortgang.vuil();
    }

    private void verhaal(Player p, Sessie ss, ZeldaVoortgang.Stand s, ZeldaHoofdstuk.Verhaal v) {
        s.zet("v:" + v.id);
        voortgang.vuil();
        for (ZeldaHoofdstuk.Regel r : v.regels) vertel(p, r);
        if (v.einde) {
            s.zet(ss.h.id());
            bewaar();
            muziek.speel(p, ZeldaMuziek.KLAAR);
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.5, 0), 40, 1.2, 1, 1.2, 0.02);
        }
    }

    private void vind(Player p, ZeldaVoortgang.Stand s, ZeldaHoofdstuk.Vondst v) {
        s.zet(v.vlag);
        voortgang.vuil();
        bewaar();
        uitrusten(p, s);
        if (v.vlag.startsWith("hart")) herstel(p);
        muziek.speel(p, ZeldaMuziek.VONDST);
        zeg(p, Component.text(v.naam, NamedTextColor.WHITE).append(Component.text(" · " + v.uitleg, NamedTextColor.GRAY)));
        p.getWorld().spawnParticle(Particle.END_ROD, v.plek, 30, 0.3, 0.5, 0.3, 0.05);
        if (v.toon != null && v.toon.isValid() && !nodig(v, p.getUniqueId())) {
            v.toon.remove();
            v.toon = null;
        }
    }

    // ── deuren ──────────────────────────────────────────────────────────────────────────────

    /** Deur open of dicht; wie in de doorgang staat bij het sluiten, wordt naar {@code binnen} geduwd. */
    void deur(ZeldaHoofdstuk.Deur d, boolean open, Location binnen) {
        if (d.isOpen == open) return;
        d.isOpen = open;
        var w = binnen.getWorld();
        if (!open) {
            ZeldaHoofdstuk.Box om = d.omvang().groter(0);
            for (Player p : Bukkit.getOnlinePlayers())
                if (om.in(p.getLocation()) || om.in(p.getLocation().clone().add(0, 1, 0))) p.teleport(binnen.clone().setDirection(binnen.toVector().subtract(p.getLocation().toVector())));
        }
        BlockData data = open ? d.open : d.dicht;
        for (int[] c : d.blokken) w.getBlockAt(c[0], c[1], c[2]).setBlockData(data, false);
        int[] m = d.blokken.get(d.blokken.size() / 2);
        Location ml = new Location(w, m[0] + 0.5, m[1] + 0.5, m[2] + 0.5);
        w.playSound(ml, open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_PISTON_EXTEND, 1f, open ? 0.8f : 0.6f);
        w.spawnParticle(Particle.BLOCK, ml, 30, 0.5, 1, 1, data.getMaterial().isAir() ? Material.STONE.createBlockData() : data);
    }

    // ── kamers ──────────────────────────────────────────────────────────────────────────────

    List<Player> in(ZeldaHoofdstuk.Box b) {
        List<Player> l = new ArrayList<>();
        for (UUID id : sessies.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.isDead() && b.in(p.getLocation())) l.add(p);
        }
        return l;
    }

    private void begin(ZeldaHoofdstuk.Kamer k, long nu) {
        List<Player> in = in(k.binnen);
        if (in.isEmpty()) return;
        k.fase = ZeldaHoofdstuk.Fase.BEZIG;
        k.golf = 0;
        k.volgendeGolf = 0;
        k.leegSinds = 0;
        k.meegedaan.clear();
        Location midden = k.start.midden(in.get(0).getWorld()).add(0, 1, 0);
        deur(k.in, false, midden);
        deur(k.uit, false, midden);
        for (Player p : in) {
            k.meegedaan.add(p.getUniqueId());
            muziek.speel(p, ZeldaMuziek.DICHT);
            muziek.lus(p, k.muziek);
            for (ZeldaHoofdstuk.Regel r : k.bijStart) vertel(p, r);
        }
        golf(k, in.size());
    }

    private void golf(ZeldaHoofdstuk.Kamer k, int spelers) {
        List<ZeldaHoofdstuk.Soort> soorten = new ArrayList<>(k.golven.get(k.golf));
        for (int i = 1; i < spelers && soorten.size() < 8; i++) soorten.add(soorten.get((i - 1) % soorten.size()) == ZeldaHoofdstuk.Soort.HEKS ? ZeldaHoofdstuk.Soort.MOSSCHUTTER : soorten.get((i - 1) % soorten.size()));
        List<Location> plekken = new ArrayList<>(k.spawns);
        java.util.Collections.shuffle(plekken, rnd);
        int i = 0;
        for (ZeldaHoofdstuk.Soort s : soorten) {
            Location l = s == ZeldaHoofdstuk.Soort.HEKS && k.baas != null ? k.baas.grond.get(0) : plekken.get(i++ % plekken.size());
            LivingEntity e = spawn(s, l, spelers);
            k.vijanden.add(e);
            if (s == ZeldaHoofdstuk.Soort.HEKS && k.baas != null) {
                k.baas.start(e);
                for (Player p : in(k.binnen)) p.showBossBar(k.baas.balk);
            }
        }
    }

    private void kamer(ZeldaHoofdstuk.Kamer k, long nu) {
        if (k.fase == ZeldaHoofdstuk.Fase.RUST) return;
        if (k.fase == ZeldaHoofdstuk.Fase.GEWONNEN) {
            if (!in(k.binnen.groter(10)).isEmpty()) k.leegSinds = 0;
            else if (k.leegSinds == 0) k.leegSinds = nu;
            else if (nu - k.leegSinds > 60_000L) herstel(k);
            return;
        }
        List<Player> in = in(k.binnen);
        for (UUID id : sessies.keySet()) {                   // muziek en balk: alleen wie binnen is
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            boolean binnen = in.contains(p);
            if (binnen && muziek.lus(p) != k.muziek) muziek.lus(p, k.muziek);
            if (!binnen && muziek.lus(p) == k.muziek) muziek.lus(p, null);
            if (k.baas != null && k.baas.balk != null) {
                if (binnen) p.showBossBar(k.baas.balk);
                else p.hideBossBar(k.baas.balk);
            }
            if (binnen) k.meegedaan.add(id);
        }
        k.vijanden.removeIf(e -> !e.isValid() || e.isDead());
        if (in.isEmpty()) {
            if (k.leegSinds == 0) k.leegSinds = nu;
            else if (nu - k.leegSinds > 4000L) herstel(k);
            return;
        }
        k.leegSinds = 0;
        for (LivingEntity e : k.vijanden) {
            if (!k.binnen.groter(1).in(e.getLocation()) && !k.spawns.isEmpty()) e.teleport(k.spawns.get(rnd.nextInt(k.spawns.size())));
            if (e instanceof Mob m && (!(m.getTarget() instanceof Player t) || !in.contains(t))) {
                Player best = null;
                double d = 1e9;
                for (Player p : in) {
                    double a = p.getLocation().distanceSquared(e.getLocation());
                    if (a < d) {
                        d = a;
                        best = p;
                    }
                }
                m.setTarget(best);
            }
        }
        if (k.baas != null && tikken % 20 == 0) {
            int oproep = k.baas.tik(this, in);
            for (int n = 0; n < oproep + (in.size() > 2 ? 1 : 0) && oproep > 0; n++)
                k.vijanden.add(spawn(ZeldaHoofdstuk.Soort.MOSSCHUTTER, k.spawns.get(rnd.nextInt(k.spawns.size())), in.size()));
        }
        if (!k.vijanden.isEmpty()) return;
        if (k.golf + 1 < k.golven.size()) {
            if (k.volgendeGolf == 0) {
                k.volgendeGolf = nu + 2500;
                for (Player p : in) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.RECORDS, 0.8f, 0.6f);
            } else if (nu >= k.volgendeGolf) {
                k.golf++;
                k.volgendeGolf = 0;
                golf(k, in.size());
            }
            return;
        }
        gewonnen(k, in);
    }

    private void gewonnen(ZeldaHoofdstuk.Kamer k, List<Player> in) {
        k.fase = ZeldaHoofdstuk.Fase.GEWONNEN;
        k.leegSinds = 0;
        Location midden = k.start.midden(in.get(0).getWorld());
        deur(k.in, true, midden);
        deur(k.uit, true, midden);
        for (Player p : in) {
            muziek.lus(p, null);
            muziek.speel(p, ZeldaMuziek.GEWONNEN);
            geneesBij(p, 6);
            if (k.baas != null) {
                if (k.baas.balk != null) p.hideBossBar(k.baas.balk);
                for (ZeldaHoofdstuk.Regel r : k.baas.bijWinst) vertel(p, r);
            }
        }
        if (k.baas != null) k.baas.stop();
    }

    /** Terug naar het begin: vijanden weg, ingang open, uitgang dicht. */
    void herstel(ZeldaHoofdstuk.Kamer k) {
        k.vijanden.forEach(Entity::remove);
        k.vijanden.clear();
        if (k.baas != null) {
            if (k.baas.balk != null) for (UUID id : sessies.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.hideBossBar(k.baas.balk);
            }
            k.baas.stop();
        }
        for (UUID id : sessies.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && muziek.lus(p) == k.muziek && !k.binnen.in(p.getLocation())) muziek.lus(p, null);
        }
        k.fase = ZeldaHoofdstuk.Fase.RUST;
        k.leegSinds = 0;
        k.golf = 0;
        Location buiten = k.in.omvang().midden(plugin.w).add(-2, 0, 0);
        deur(k.in, true, buiten);
        deur(k.uit, false, k.start.midden(plugin.w).add(0, 1, 0));
    }

    // ── vijanden ────────────────────────────────────────────────────────────────────────────

    private static void zetAttr(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    LivingEntity spawn(ZeldaHoofdstuk.Soort s, Location l, int spelers) {
        var w = l.getWorld();
        Class<? extends Mob> klasse = switch (s) {
            case VEENLOPER, VEENREUS -> org.bukkit.entity.Zombie.class;
            case SPIN -> org.bukkit.entity.Spider.class;
            case MOSSCHUTTER -> org.bukkit.entity.Bogged.class;
            case SOLDAAT -> org.bukkit.entity.Vindicator.class;
            case KRUISBOOG -> org.bukkit.entity.Pillager.class;
            case HEKS -> org.bukkit.entity.Witch.class;
        };
        w.spawnParticle(Particle.SOUL, l.clone().add(0, 0.5, 0), 20, 0.4, 0.2, 0.4, 0.03);
        w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 0.8, 0), 10, 0.3, 0.5, 0.3, 0.01);
        w.playSound(l, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.5f, 1.3f);
        return w.spawn(l, klasse, m -> {
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            m.addScoreboardTag(TAG);
            var eq = m.getEquipment();
            double hp, dmg = -1, schaal = 1;
            switch (s) {
                case VEENLOPER -> {
                    hp = 16;
                    dmg = 3;
                    ItemStack jas = new ItemStack(Material.LEATHER_CHESTPLATE);
                    if (jas.getItemMeta() instanceof LeatherArmorMeta lm) {
                        lm.setColor(Color.fromRGB(0x4a5d23));
                        jas.setItemMeta(lm);
                    }
                    eq.setChestplate(jas);
                    eq.setHelmet(new ItemStack(Material.MOSS_CARPET));
                }
                case VEENREUS -> {
                    hp = 40 + 10 * (spelers - 1);
                    dmg = 5;
                    schaal = 1.6;
                    eq.setHelmet(new ItemStack(Material.MOSS_BLOCK));
                    eq.setItemInMainHand(new ItemStack(Material.WOODEN_AXE));
                    zetAttr(m, Attribute.KNOCKBACK_RESISTANCE, 0.6);
                    zetAttr(m, Attribute.MOVEMENT_SPEED, 0.2);
                }
                case SPIN -> {
                    hp = 12;
                    dmg = 2.5;
                }
                case MOSSCHUTTER -> hp = 12;
                case SOLDAAT -> {
                    hp = 20;
                    dmg = 4;
                    eq.setItemInMainHand(new ItemStack(Material.STONE_AXE));
                }
                case KRUISBOOG -> hp = 16;
                default -> {
                    hp = 80 + 40 * (spelers - 1);
                    schaal = 1.5;
                    zetAttr(m, Attribute.KNOCKBACK_RESISTANCE, 0.5);
                }
            }
            for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND})
                eq.setDropChance(sl, 0f);
            if (m instanceof org.bukkit.entity.Zombie z) {
                z.setShouldBurnInDay(false);
                z.setAdult();
            }
            if (m instanceof org.bukkit.entity.AbstractSkeleton sk) sk.setShouldBurnInDay(false);
            if (m instanceof org.bukkit.entity.Raider r) {
                r.setCanJoinRaid(false);
                r.setPatrolLeader(false);
            }
            zetAttr(m, Attribute.MAX_HEALTH, hp);
            m.setHealth(hp);
            if (dmg > 0) zetAttr(m, Attribute.ATTACK_DAMAGE, dmg);
            if (schaal != 1) zetAttr(m, Attribute.SCALE, schaal);
            zetAttr(m, Attribute.FOLLOW_RANGE, 40);
        });
    }

    static boolean isVijand(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG) && !(e instanceof Player);
    }

    private ZeldaBaas baasVan(Entity e) {
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Kamer k : h.kamers) if (k.baas != null && k.baas.ent != null && k.baas.ent.equals(e)) return k.baas;
        return null;
    }

    // ── uitdagingen op tijd ─────────────────────────────────────────────────────────────────

    private void begin(Player p, ZeldaHoofdstuk.Uitdaging u, long nu) {
        boolean eerste = !u.loopt.containsKey(p.getUniqueId());
        u.loopt.put(p.getUniqueId(), nu);
        if (eerste) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.RECORDS, 0.7f, 1.2f);
    }

    private void gehaald(Player p, ZeldaVoortgang.Stand s, ZeldaHoofdstuk.Uitdaging u, long ms) {
        u.loopt.remove(p.getUniqueId());
        u.openTot = System.currentTimeMillis() + 3000;
        Long oud = s.beste(u.id);
        boolean record = s.tijd(u.id, ms);
        voortgang.vuil();
        u.bordOud |= record;
        p.setLevel(0);
        p.setExp(0f);
        muziek.speel(p, ZeldaMuziek.OPGELOST);
        zeg(p, Component.text(u.naam + " · " + ZeldaVoortgang.tijd(ms) + (record && oud != null ? " · record" : ""), NamedTextColor.GRAY));
    }

    private void uitdaging(ZeldaHoofdstuk.Uitdaging u, long nu) {
        long limiet = u.seconden * 1000L;
        for (var it = u.loopt.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !speelt(p)) {
                it.remove();
                continue;
            }
            long over = limiet - (nu - e.getValue());
            if (over <= 0) {
                it.remove();
                p.setLevel(0);
                p.setExp(0f);
                muziek.speel(p, ZeldaMuziek.MISLUKT);
                continue;
            }
            int sec = (int) ((over + 999) / 1000);
            if (sec != p.getLevel()) {
                p.playSound(p, sec <= 5 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.RECORDS, sec <= 5 ? 0.5f : 0.35f, sec <= 5 ? 1.6f : 1.0f);
                p.setLevel(sec);
            }
            p.setExp(Math.max(0f, Math.min(0.999f, over / (float) limiet)));
        }
        boolean open = !u.loopt.isEmpty() || nu < u.openTot;
        if (open != u.poort.isOpen) {
            if (!open && !in(u.poort.omvang().groter(1)).isEmpty()) return;     // niemand insluiten
            deur(u.poort, open, u.poort.omvang().midden(plugin.w).add(1, 0, 0));
        }
    }

    // ── puzzels (reeksen) ───────────────────────────────────────────────────────────────────

    /** Zet ding {@code i} van reeks {@code r} aan (rechtsklik of pijl). */
    void activeer(Player p, ZeldaHoofdstuk.Reeks r, int i) {
        if (r.opgelost || r.isAan.contains(i)) return;
        long nu = System.currentTimeMillis();
        if (r.isAan.isEmpty()) r.tot = nu + r.seconden * 1000L;
        r.isAan.add(i);
        int[] d = r.dingen.get(i);
        var w = p.getWorld();
        Block b = w.getBlockAt(d[0], d[1], d[2]);
        b.setBlockData(r.aan, false);
        Location l = b.getLocation().add(0.5, 0.8, 0.5);
        w.playSound(l, r.hoe == ZeldaHoofdstuk.Reeks.Hoe.KLIK ? Sound.ITEM_FIRECHARGE_USE : Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.9f + 0.15f * r.isAan.size());
        w.spawnParticle(r.hoe == ZeldaHoofdstuk.Reeks.Hoe.KLIK ? Particle.FLAME : Particle.HAPPY_VILLAGER, l, 20, 0.3, 0.3, 0.3, 0.02);
        if (r.isAan.size() < r.dingen.size()) return;
        r.opgelost = true;
        r.leegSinds = 0;
        deur(r.deur, true, r.gebied.midden(w));
        for (Player q : in(r.gebied)) {
            if (voortgang.van(q.getUniqueId()).zet("r:" + r.id)) voortgang.vuil();
            q.setLevel(0);
            q.setExp(0f);
            muziek.speel(q, ZeldaMuziek.OPGELOST);
            for (ZeldaHoofdstuk.Regel rg : r.bijOplossen) vertel(q, rg);
        }
    }

    /** Puzzel terug naar het begin. */
    void herstel(ZeldaHoofdstuk.Reeks r) {
        r.opgelost = false;
        r.isAan.clear();
        r.leegSinds = 0;
        deur(r.deur, false, r.gebied.midden(plugin.w));
        for (int[] d : r.dingen) plugin.w.getBlockAt(d[0], d[1], d[2]).setBlockData(r.uit, false);
    }

    private void reeks(ZeldaHoofdstuk.Reeks r, long nu) {
        if (r.opgelost) {
            if (!in(r.gebied.groter(6)).isEmpty()) r.leegSinds = 0;
            else if (r.leegSinds == 0) r.leegSinds = nu;
            else if (nu - r.leegSinds > 60_000L) herstel(r);
            return;
        }
        List<Player> in = in(r.gebied);
        for (Player p : in)                                  // wie hem al eens oploste, hoeft dat niet opnieuw
            if (voortgang.van(p.getUniqueId()).heeft("r:" + r.id)) {
                r.opgelost = true;
                r.leegSinds = 0;
                r.isAan.clear();
                for (int i = 0; i < r.dingen.size(); i++) {
                    int[] d = r.dingen.get(i);
                    plugin.w.getBlockAt(d[0], d[1], d[2]).setBlockData(r.aan, false);
                }
                deur(r.deur, true, r.gebied.midden(plugin.w));
                return;
            }
        if (r.isAan.isEmpty()) return;
        long over = r.tot - nu;
        if (over <= 0) {
            r.isAan.clear();
            for (int[] d : r.dingen) plugin.w.getBlockAt(d[0], d[1], d[2]).setBlockData(r.uit, false);
            for (Player p : in) {
                p.setLevel(0);
                p.setExp(0f);
                muziek.speel(p, ZeldaMuziek.MISLUKT);
                p.playSound(p, r.hoe == ZeldaHoofdstuk.Reeks.Hoe.KLIK ? Sound.BLOCK_FIRE_EXTINGUISH : Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1f);
            }
            return;
        }
        int sec = (int) ((over + 999) / 1000);
        for (Player p : in) {
            if (p.getLevel() != sec) {
                p.setLevel(sec);
                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.RECORDS, 0.3f, sec <= 5 ? 1.5f : 1f);
            }
            p.setExp(Math.max(0f, Math.min(0.999f, over / (r.seconden * 1000f))));
        }
    }

    // ── versiering: vondsten en personen alleen als er iemand in de buurt is ──────────────────

    private boolean nodig(ZeldaHoofdstuk.Vondst v, UUID behalve) {
        for (UUID id : sessies.keySet()) {
            if (id.equals(behalve)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld().equals(v.plek.getWorld()) && p.getLocation().distanceSquared(v.plek) < 48 * 48
                    && !voortgang.van(id).heeft(v.vlag)) return true;
        }
        return false;
    }

    private boolean dichtbij(Location l, double r) {
        for (UUID id : sessies.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld().equals(l.getWorld()) && p.getLocation().distanceSquared(l) < r * r) return true;
        }
        return false;
    }

    private void decor(ZeldaHoofdstuk h) {
        for (ZeldaHoofdstuk.Vondst v : h.vondsten) {
            boolean moet = nodig(v, null);
            if (moet && (v.toon == null || !v.toon.isValid())) {
                v.toon = v.plek.getWorld().spawn(v.plek, ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.addScoreboardTag(TAG);
                    d.setItemStack(new ItemStack(v.item));
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setGlowing(true);
                    d.setGlowColorOverride(Color.fromRGB(0xfff2a0));
                });
            } else if (!moet && v.toon != null) {
                v.toon.remove();
                v.toon = null;
            }
            if (v.toon != null && v.toon.isValid()) {                 // langzaam draaien en zweven
                float hoek = (float) ((tikken / 20 % 360) * Math.toRadians(18));
                float op = (float) Math.sin(tikken / 20.0 * 0.6) * 0.12f;
                v.toon.setInterpolationDelay(0);
                v.toon.setInterpolationDuration(20);
                v.toon.setTransformation(new org.bukkit.util.Transformation(new Vector3f(0, op, 0), new AxisAngle4f(hoek, 0, 1, 0),
                        new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
                if (tikken % 60 == 0) v.plek.getWorld().spawnParticle(Particle.END_ROD, v.plek, 3, 0.25, 0.3, 0.25, 0.01);
            }
        }
        for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) {           // bord met de beste tijden
            if (u.bord == null) continue;
            boolean moet = dichtbij(u.bord, 40);
            if (moet && (u.bordTekst == null || !u.bordTekst.isValid())) {
                u.bordTekst = u.bord.getWorld().spawn(u.bord, org.bukkit.entity.TextDisplay.class, d -> {
                    d.setPersistent(false);
                    d.addScoreboardTag(TAG);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                    d.setTransformation(new org.bukkit.util.Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
                });
                u.bordOud = true;
            } else if (!moet && u.bordTekst != null) {
                u.bordTekst.remove();
                u.bordTekst = null;
            }
            if (u.bordTekst != null && u.bordOud) {
                u.bordOud = false;
                Component t = Component.text(u.naam, GROEN);
                var lijst = voortgang.besteTijden(u.id, 5);
                if (lijst.isEmpty()) t = t.append(Component.text("\nnog geen tijden", NamedTextColor.GRAY));
                for (int i = 0; i < lijst.size(); i++)
                    t = t.append(Component.text("\n" + (i + 1) + ". " + lijst.get(i).getKey() + "  " + ZeldaVoortgang.tijd(lijst.get(i).getValue()), NamedTextColor.GRAY));
                u.bordTekst.text(t);
            }
        }
        for (ZeldaHoofdstuk.Persoon p : h.personen) {
            boolean moet = dichtbij(p.plek, 56);
            if (moet && (p.ent == null || !p.ent.isValid())) {
                p.ent = p.plek.getWorld().spawn(p.plek, Villager.class, v -> {
                    v.setPersistent(false);
                    v.addScoreboardTag(TAG);
                    v.addScoreboardTag(NPC);
                    v.setAI(false);
                    v.setInvulnerable(true);
                    v.setSilent(true);
                    v.setCollidable(false);
                    if (p.beroep != null) v.setProfession(p.beroep);
                    v.setVillagerLevel(5);
                    v.customName(Component.text(p.naam, GROEN));
                    v.setCustomNameVisible(false);
                    v.setRotation(p.plek.getYaw(), 0);
                });
            } else if (!moet && p.ent != null) {
                p.ent.remove();
                p.ent = null;
            }
        }
    }

    // ── kaart: reizen ───────────────────────────────────────────────────────────────────────

    private void kaart(Player p) {
        Sessie ss = sessie(p);
        if (ss == null) return;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, 27, Component.text(ss.h.naam(), NamedTextColor.DARK_GRAY));
        int verst = s.rust(ss.h.id());
        for (ZeldaHoofdstuk.Rust r : ss.h.rust) {
            if (r.index > 17) break;
            int slot = 1 + r.index + (r.index >= 7 ? 2 : 0);
            ItemStack it;
            if (r.index <= verst) {
                it = ding(r.index == ss.rust ? Material.SOUL_CAMPFIRE : Material.CAMPFIRE, r.naam, "rust:" + r.index);
                ItemMeta meta = it.getItemMeta();
                meta.lore(List.of(Component.text(r.index == ss.rust ? "hier ben je" : "reizen", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
                it.setItemMeta(meta);
            } else it = ding(Material.GRAY_STAINED_GLASS_PANE, "…", "niets");
            m.inv.setItem(slot, it);
        }
        m.inv.setItem(26, ding(Material.OAK_DOOR, "Terug naar de hub", "hub"));
        p.openInventory(m.inv);
    }

    @EventHandler
    public void onMenu(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() >= 27) return;
        ItemStack it = e.getCurrentItem();
        String soort = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (soort == null) return;
        Sessie ss = sessie(p);
        if (soort.equals("hub")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                plugin.naarHub(p);
            });
        } else if (soort.startsWith("rust:") && ss != null) {
            int i = Integer.parseInt(soort.substring(5));
            if (i > voortgang.van(p.getUniqueId()).rust(ss.h.id()) || i >= ss.h.rust.size()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                ss.rust = i;
                p.setFallDistance(0);
                p.teleport(ss.h.rust.get(i).plek);
                p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
            });
        }
    }

    @EventHandler
    public void onMenuSleep(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    // ── gebeurtenissen ──────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onKlik(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!speelt(p)) return;
        Block geklikt = e.getClickedBlock();
        if (e.getAction() == Action.PHYSICAL) {                 // geen akkers vertrappen
            if (geklikt != null && geklikt.getType() == Material.FARMLAND) e.setCancelled(true);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && geklikt != null && (geklikt.getState() instanceof InventoryHolder
                || switch (geklikt.getType()) {
                    case CRAFTING_TABLE, COMPOSTER, CAULDRON, WATER_CAULDRON, FLOWER_POT, LECTERN, CAMPFIRE, SOUL_CAMPFIRE -> true;
                    default -> false;
                })) e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);   // geen tonnen, werkbank, ketel: spullen blijven bij jou
        ItemStack it = e.getItem();
        String soort = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if ("kaart".equals(soort)) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) kaart(p);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getHand() == EquipmentSlot.HAND) {
            Block b = e.getClickedBlock();
            for (ZeldaHoofdstuk.Reeks r : sessie(p).h.reeksen) {
                if (r.hoe != ZeldaHoofdstuk.Reeks.Hoe.KLIK) continue;
                int i = r.index(b.getX(), b.getY(), b.getZ());
                if (i >= 0) {
                    e.setCancelled(true);
                    activeer(p, r, i);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPijl(ProjectileHitEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p) || !speelt(p)) return;
        if (e.getHitEntity() != null) {
            ZeldaBaas baas = baasVan(e.getHitEntity());
            if (baas != null) baas.geraakt();
        }
        Block b = e.getHitBlock();
        if (b == null) return;
        for (ZeldaHoofdstuk.Reeks r : sessie(p).h.reeksen) {
            if (r.hoe != ZeldaHoofdstuk.Reeks.Hoe.PIJL) continue;
            int i = r.index(b.getX(), b.getY(), b.getZ());
            if (i >= 0) {
                activeer(p, r, i);
                if (e.getEntity() instanceof AbstractArrow a) a.remove();
                return;
            }
        }
    }

    @EventHandler
    public void onPraat(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains(NPC)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Sessie ss = sessie(p);
        if (ss == null || e.getHand() != EquipmentSlot.HAND || !ss.rij.isEmpty() || ss.nu != null) return;
        for (ZeldaHoofdstuk.Persoon pe : ss.h.personen)
            if (e.getRightClicked().equals(pe.ent)) for (ZeldaHoofdstuk.Regel r : pe.praat) vertel(p, r);
    }

    private static Player speler(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof Tameable t && t.isTamed() && t.getOwner() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSchade(EntityDamageByEntityEvent e) {
        Entity doel = e.getEntity();
        Entity d = e.getDamager();
        if (doel.getScoreboardTags().contains(NPC)) {
            e.setCancelled(true);
            return;
        }
        Player a = speler(d), v = doel instanceof Player p ? p : null;
        if (v != null && a != null && !a.equals(v) && (speelt(v) || speelt(a))) {    // samen, niet tegen elkaar
            e.setCancelled(true);
            return;
        }
        Entity bron = d instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : d;
        if (isVijand(doel) && isVijand(bron)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcSchade(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains(NPC)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDood(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (!speelt(p)) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(false);
        e.setDroppedExp(0);
        e.deathMessage(null);
        muziek.lus(p, null);
        for (ZeldaHoofdstuk.Uitdaging u : sessie(p).h.uitdagingen) u.loopt.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!speelt(p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || !speelt(p)) return;
            herstel(p);
            p.setLevel(0);
            p.setExp(0f);
            Sessie ss = sessie(p);
            ss.laatsteHemel = -1;
            hemel(p, ss, p.getLocation());
            p.playSound(p, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.4f, 1.4f);
        });
    }

    @EventHandler
    public void onVijandDood(EntityDeathEvent e) {
        if (!isVijand(e.getEntity()) || e.getEntity() instanceof Villager) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player k = e.getEntity().getKiller();
        if (k != null && speelt(k) && rnd.nextInt(10) < 3) {
            geneesBij(k, 2);
            e.getEntity().getWorld().spawnParticle(Particle.HEART, e.getEntity().getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
            k.playSound(k, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
        }
    }

    @EventHandler
    public void onBrand(EntityCombustEvent e) {
        if (isVijand(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDoel(EntityTargetEvent e) {
        if (e.getTarget() == null) return;
        if (e.getTarget().getScoreboardTags().contains(NPC)) e.setCancelled(true);
        else if (isVijand(e.getEntity()) && isVijand(e.getTarget())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrank(EntityPotionEffectEvent e) {
        if (e.getCause() == EntityPotionEffectEvent.Cause.POTION_DRINK && baasVan(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (baasVan(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHonger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && speelt(p)) {
            e.setCancelled(true);
            if (p.getFoodLevel() != 17) p.setFoodLevel(17);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (speelt(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onOppakken(PlayerAttemptPickupItemEvent e) {
        if (speelt(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onVijandPakt(EntityPickupItemEvent e) {
        if (isVijand(e.getEntity())) e.setCancelled(true);
    }

    /** Geen prestatie-meldingen in de chat (monster verslagen e.d.): in Zelda blijft het stil. Opmaak slaat null over. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrestatie(org.bukkit.event.player.PlayerAdvancementDoneEvent e) {
        if (speelt(e.getPlayer())) e.message(null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        harten(e.getPlayer(), -1);                           // na een crash nooit met Zelda-harten in de hub
    }

    // ── voor de zelftest ────────────────────────────────────────────────────────────────────

    int vijanden() {
        int n = 0;
        for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Kamer k : h.kamers) n += k.vijanden.size();
        return n;
    }
}
