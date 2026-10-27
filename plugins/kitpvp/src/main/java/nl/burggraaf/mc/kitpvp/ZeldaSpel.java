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
import org.bukkit.block.BlockFace;
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
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * De motor van Story of Zelda: wie er speelt (een {@link Sessie} per speler), de zones van het eiland (rustplaats,
 * verhaal, vondst, kamer, uitdaging, bron), vijanden, de baas, muziek (altijd een achtergrond per streek), de verteller
 * (alles wat spelers lezen staat op één plek: de actiebalk, één korte grijze regel tegelijk), Wiske de fee
 * ({@link ZeldaMetgezel}), je huisdier ({@link ZeldaDier}), personen met gesprekken en zijmissies, {@code /hint},
 * uitrusting uit de voortgang (harten, hartstukjes, harnas, flesjes, voorwerpen), de kaart (reizen tussen bereikte
 * rustplaatsen, opnieuw vanaf een rustplaats, uitleg aan/uit, varen naar een ander eiland) en de overtocht over zee.
 * KitPvP laat spelers in {@link Toestand.Plek#SPEL} met rust; dit regelt schade, honger, doodgaan, drops en grenzen
 * voor wie in Zelda is. Zie ZELDA.md voor het ontwerp.
 */
final class ZeldaSpel implements Listener {

    static final String TAG = "zelda", NPC = "zelda-npc";
    static final TextColor GROEN = TextColor.color(0x8fcf6a);
    /** Na zo lang zonder voortgang biedt Wiske één keer hulp aan (en dan pas weer na {@link #AANBOD_OPNIEUW}). */
    static final long VAST_MS = 6 * 60_000L, AANBOD_OPNIEUW = 10 * 60_000L;

    /** Een overtocht naar een ander eiland. */
    record Vaart(ZeldaEiland naar, long tot, boolean eerste) {}

    /** Per speler in Zelda. */
    static final class Sessie {
        ZeldaHoofdstuk h;                                    // het deel waar je nu bent
        ZeldaHoofdstuk rh;                                   // het deel van je rustplaats
        int rust;                                            // waar je na doodgaan terugkomt
        Vaart vaart;                                         // aan boord (null = op een eiland)
        final Deque<Component> rij = new ArrayDeque<>();
        Component nu;
        long tot, gestuurd, laatsteHemel = -1;
        long hintTijd, hintWacht, laatsteVoortgang, aangeboden, bronDeuntje;
        String hintDoel, tip;
        int voortgangStand;
        boolean inBron, naamGevraagd;
        final Map<String, Integer> doden = new HashMap<>();  // kamer → keer doodgegaan (voor tips)

        Sessie(ZeldaHoofdstuk h, int rust) {
            this.h = h;
            this.rh = h;
            this.rust = rust;
        }

        ZeldaHoofdstuk.Rust rustplaats() {
            return rh.rust.get(Math.max(0, Math.min(rust, rh.rust.size() - 1)));
        }
    }

    /** Herkenning van het kaartmenu en het naammenu. */
    static final class Menu implements InventoryHolder {
        Inventory inv;
        final boolean naam;

        Menu(boolean naam) {
            this.naam = naam;
        }

        public Inventory getInventory() {
            return inv;
        }
    }

    final KitPvP plugin;
    final Zelda modus;
    final List<ZeldaEiland> eilanden;
    final List<ZeldaHoofdstuk> hoofdstukken;                 // alle delen van alle eilanden
    final ZeldaZee zee;
    final ZeldaMuziek muziek = new ZeldaMuziek();
    final ZeldaMetgezel metgezel;
    final ZeldaDier dier = new ZeldaDier();
    final List<ZeldaVoorwerp> voorwerpen = new ArrayList<>();
    final Map<UUID, Sessie> sessies = new HashMap<>();
    /** Beheermodus voor OP's (/zelda beheer): vliegen, onkwetsbaar, de wereld reageert niet op je. */
    final ZeldaBeheer beheer = new ZeldaBeheer(this);
    ZeldaVoortgang voortgang;
    final NamespacedKey itemKey;
    private final NamespacedKey hartKey;
    private final Random rnd = new Random();
    private final List<BukkitTask> taken = new ArrayList<>();
    private long tikken;

    ZeldaSpel(KitPvP plugin, Zelda modus, List<ZeldaEiland> eilanden, ZeldaZee zee) {
        this.plugin = plugin;
        this.modus = modus;
        this.eilanden = eilanden;
        List<ZeldaHoofdstuk> alle = new ArrayList<>();
        for (ZeldaEiland e : eilanden) alle.addAll(e.delen());
        this.hoofdstukken = List.copyOf(alle);
        this.zee = zee;
        this.metgezel = new ZeldaMetgezel(plugin);
        this.itemKey = new NamespacedKey(plugin, "zelda");
        this.hartKey = new NamespacedKey(plugin, "zelda-harten");
        voorwerpen.add(new Flesje());
        voorwerpen.add(new Bommen());
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
        for (ZeldaHoofdstuk h : delen()) {
            for (ZeldaHoofdstuk.Kamer k : h.kamers) k.vijanden.forEach(Entity::remove);
            for (ZeldaHoofdstuk.Vondst v : h.vondsten) if (v.toon != null) v.toon.remove();
            for (ZeldaHoofdstuk.Persoon p : h.personen) if (p.ent != null) p.ent.remove();
        }
        metgezel.weg();
        dier.weg();
        if (plugin.w != null) for (Entity e : plugin.w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        bewaar();
    }

    ZeldaHoofdstuk hoofdstuk(String id) {
        ZeldaHoofdstuk h = deel(id);
        return h == null ? hoofdstukken.get(0) : h;
    }

    /** Het deel met dit id (null = bestaat niet). */
    ZeldaHoofdstuk deel(String id) {
        for (ZeldaHoofdstuk h : hoofdstukken) if (h.id().equals(id)) return h;
        return null;
    }

    /** Het deel (van welk eiland ook) waar deze plek in ligt, of null. */
    ZeldaHoofdstuk deelOveral(Location l) {
        for (ZeldaEiland e : eilanden) {
            ZeldaHoofdstuk h = e.deelBij(l);
            if (h != null) return h;
        }
        return null;
    }

    ZeldaEiland eiland(String id) {
        for (ZeldaEiland e : eilanden) if (e.id().equals(id)) return e;
        return null;
    }

    /** Alle delen met onderdelen: de eilanden en de zee. */
    private List<ZeldaHoofdstuk> delen() {
        List<ZeldaHoofdstuk> l = new ArrayList<>(hoofdstukken);
        l.add(zee);
        return l;
    }

    private static <T> List<T> alle(ZeldaEiland e, java.util.function.Function<ZeldaHoofdstuk, List<T>> wat) {
        List<T> l = new ArrayList<>();
        for (ZeldaHoofdstuk h : e.delen()) l.addAll(wat.apply(h));
        return l;
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
        for (ZeldaHoofdstuk d : hoofdstukken) if (d.aanvullen(s)) voortgang.vuil();   // oude standen: wat je al deed
        // waar je verder gaat: je laatste rustplaats, of (oude stand) de verste van je hoofdstuk
        ZeldaHoofdstuk h = null;
        int r = 0;
        String[] pl = ZeldaRegels.plek(s.laatste);
        if (pl != null && deel(pl[0]) != null && Integer.parseInt(pl[1]) < deel(pl[0]).rust.size()) {
            h = deel(pl[0]);
            r = Integer.parseInt(pl[1]);
        }
        if (h == null) {
            h = hoofdstuk(s.hoofdstuk);
            r = Math.max(0, Math.min(s.rust(h.id()), h.rust.size() - 1));
        }
        Sessie ss = new Sessie(h, r);
        long nu = System.currentTimeMillis();
        ss.laatsteVoortgang = nu;
        ss.voortgangStand = s.voortgang();
        sessies.put(p.getUniqueId(), ss);
        for (String v : h.rust.get(r).geeft) if (s.zet(v)) voortgang.vuil();
        uitrusten(p, s);
        p.getInventory().setHeldItemSlot(0);
        herstel(p);
        Location l = h.rust.get(r).plek;
        p.teleport(l);
        p.setFallDistance(0);
        hemel(p, ss, l);
        muziek.speel(p, ZeldaMuziek.BEGIN);
        muziek.achter(p, h.muziek(l));
        if (s.heeft("v:intro")) zeg(p, Component.text(h.naam() + " · " + h.rust.get(r).naam + " · vak 8 = terug naar de hub", NamedTextColor.GRAY));
        if (s.zet("v:wiske")) {                              // Wiske is nu te zien: wie al speelde, krijgt het even te horen
            voortgang.vuil();
            if (s.heeft("v:intro")) {
                vertel(p, ZeldaHoofdstuk.r("Wiske", "Kijk, nu zie je me beter! Ik vlieg met je mee."));
                if (!s.heeft("o:uitleg-uit")) vertel(p, ZeldaHoofdstuk.r("Wiske", "Weet je het even niet? Klik op mij, of typ /hint."));
            }
        }
    }

    /** Speler gaat weg (hub, andere modus, uitloggen). */
    void weg(Player p) {
        beheer.uit(p, false);                                // beheer: voortgang terug, vliegen uit
        Sessie ss = sessies.remove(p.getUniqueId());
        muziek.vergeet(p.getUniqueId());
        metgezel.weg(p.getUniqueId());
        dier.weg(p.getUniqueId());
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
        return ss == null ? null : ss.rustplaats().plek;
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

    ItemStack ding(Material m, String naam, String soort) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(naam, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ARMOR_TRIM);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, soort);
        if (meta instanceof LeatherArmorMeta lm) lm.setColor(switch (soort) {
            case "laarzen" -> Color.fromRGB(0x5b3a22);
            case "broek" -> Color.fromRGB(0x6b4a2a);
            default -> Color.fromRGB(0x3f8a2e);
        });
        it.setItemMeta(meta);
        return it;
    }

    /** Een grijze regel onder de naam van een item (menu). */
    static void lore(ItemStack it, String... regels) {
        ItemMeta meta = it.getItemMeta();
        List<Component> l = new ArrayList<>();
        for (String r : regels) l.add(Component.text(r, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        it.setItemMeta(meta);
    }

    /** Alles wat je bij je hebt volgt uit je voortgang: zwaard, schild, boog, voorwerpen, kaart, harnas en harten. */
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
        for (ZeldaVoorwerp v : voorwerpen) if (s.heeft(v.vlag())) vernieuw(p, s, v);
        inv.setItem(8, ding(Material.MAP, "Kaart", "kaart"));
        inv.setItem(Menus.HUB_VAK, plugin.hubItem());
        harnas(p, ZeldaRegels.harnas(s));
        harten(p, ZeldaRegels.harten(s));
    }

    /** Zet één voorwerp opnieuw in zijn vak (bv. na drinken). */
    void vernieuw(Player p, ZeldaVoortgang.Stand s, ZeldaVoorwerp v) {
        ItemStack it = s.heeft(v.vlag()) ? v.item(this, p, s) : null;
        if (it != null) {
            ItemMeta meta = it.getItemMeta();
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "v:" + v.id());
            it.setItemMeta(meta);
        }
        p.getInventory().setItem(v.vak(), it);
    }

    ZeldaVoorwerp voorwerp(String id) {
        for (ZeldaVoorwerp v : voorwerpen) if (v.id().equals(id)) return v;
        return null;
    }

    /** Harnas {@code n} (0 = groene tuniek … 4 = lichtharnas, zie {@link ZeldaRegels#HARNAS_NAAM}). */
    private void harnas(Player p, int n) {
        var inv = p.getInventory();
        String naam = ZeldaRegels.HARNAS_NAAM[n];
        switch (n) {
            case 0 -> {
                inv.setHelmet(ding(Material.LEATHER_HELMET, "Muts", "muts"));
                inv.setChestplate(ding(Material.LEATHER_CHESTPLATE, naam, "tuniek"));
                inv.setLeggings(null);
                inv.setBoots(ding(Material.LEATHER_BOOTS, "Laarzen", "laarzen"));
            }
            case 1 -> {
                inv.setHelmet(ding(Material.LEATHER_HELMET, "Muts", "muts"));
                inv.setChestplate(ding(Material.CHAINMAIL_CHESTPLATE, naam, "tuniek"));
                inv.setLeggings(ding(Material.LEATHER_LEGGINGS, "Broek", "broek"));
                inv.setBoots(ding(Material.LEATHER_BOOTS, "Laarzen", "laarzen"));
            }
            case 2 -> {
                inv.setHelmet(ding(Material.CHAINMAIL_HELMET, naam, "muts"));
                inv.setChestplate(ding(Material.IRON_CHESTPLATE, naam, "tuniek"));
                inv.setLeggings(ding(Material.CHAINMAIL_LEGGINGS, naam, "broek"));
                inv.setBoots(ding(Material.LEATHER_BOOTS, "Laarzen", "laarzen"));
            }
            default -> {
                Material[] set = n == 3 ? new Material[]{Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS}
                        : new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
                ItemStack[] stuk = new ItemStack[4];
                String[] soort = {"muts", "tuniek", "broek", "laarzen"};
                for (int i = 0; i < 4; i++) {
                    stuk[i] = ding(set[i], naam, soort[i]);
                    if (stuk[i].getItemMeta() instanceof ArmorMeta am) {
                        am.setTrim(new ArmorTrim(n == 3 ? TrimMaterial.COPPER : TrimMaterial.GOLD, n == 3 ? TrimPattern.RIB : TrimPattern.SPIRE));
                        stuk[i].setItemMeta(am);
                    }
                }
                inv.setHelmet(stuk[0]);
                inv.setChestplate(stuk[1]);
                inv.setLeggings(stuk[2]);
                inv.setBoots(stuk[3]);
            }
        }
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
                if (tekst.startsWith("Wiske ·")) metgezel.praat(p);
            } else if (ss.nu != null && nu - ss.gestuurd >= 1500) {
                ss.gestuurd = nu;
                p.sendActionBar(ss.nu);
            }
        }
    }


    // ── de klok ─────────────────────────────────────────────────────────────────────────────

    private void elkeTik() {
        tikken++;
        muziek.tik();
        long nu = System.currentTimeMillis();
        List<Player> spelers = new ArrayList<>();
        for (var e : new ArrayList<>(sessies.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) sessies.remove(e.getKey());
            else spelers.add(p);
        }
        veilig("wiske", () -> metgezel.tik(spelers));
        if (tikken % 5 == 0) {
            for (Player p : spelers) if (!p.isDead() && sessie(p).vaart == null && !beheer.is(p.getUniqueId())) veilig("zones", () -> zones(p, sessie(p), nu));
            for (ZeldaHoofdstuk h : hoofdstukken) {
                for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) uitdaging(u, nu);
                for (ZeldaHoofdstuk.Reeks r : h.reeksen) reeks(r, nu);
                for (ZeldaHoofdstuk.Barst b : h.barsten) barst(b, nu);
            }
        }
        if (tikken % 10 == 5) for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Spiegels sp : h.spiegels) veilig("spiegels", () -> spiegels(sp, nu));
        if (tikken % 10 == 0) {
            verteller(nu);
            for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Kamer k : h.kamers) kamer(k, nu);
            Map<Player, ZeldaVoortgang.Stand> wie = new HashMap<>();
            for (Player p : spelers) wie.put(p, voortgang.van(p.getUniqueId()));
            veilig("huisdier", () -> dier.tik(wie, this::verborgen));
            veilig("bronnen", this::bronnen);
        }
        if (tikken % 20 == 0) {
            for (Player p : spelers) veilig("seconde", () -> seconde(p, sessie(p), nu));
            for (ZeldaHoofdstuk h : delen()) veilig("decor", () -> decor(h));
            for (ZeldaHoofdstuk h : hoofdstukken) {                 // wat een deel zelf laat bewegen (vlieger, lichtbundel, …)
                List<Player> op = new ArrayList<>();
                for (Player p : spelers) if (sessie(p) != null && sessie(p).vaart == null && sessie(p).h == h) op.add(p);
                veilig("deel " + h.id(), () -> h.tik(this, op, nu));
            }
            if (tikken % 200 == 0) bewaar();
        }
    }

    private long laatsteFout;

    /** Eén onderdeel van de klok: een fout stopt de rest niet (en komt hoogstens eens per minuut in de log). */
    private void veilig(String wat, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            long nu = System.currentTimeMillis();
            if (nu - laatsteFout > 60_000L) {
                laatsteFout = nu;
                plugin.getLogger().warning("zelda " + wat + ": " + e);
            }
        }
    }

    /** Elke seconde per speler: grenzen, het deel waar je bent, hemel, genezen, muziek, de overtocht, hulp aanbieden. */
    private void seconde(Player p, Sessie ss, long nu) {
        Location l = p.getLocation();
        if (p.isDead()) return;
        if (p.getFoodLevel() != 17) p.setFoodLevel(17);
        if (beheer.is(p.getUniqueId())) {                    // beheer: overal mogen zijn, geen genezen of hulp
            beheer.houVast(p);
            ZeldaHoofdstuk hier = deelOveral(l);
            if (hier != null) {
                ss.h = hier;
                ss.vaart = null;
                hemel(p, ss, l);
                muziek.achter(p, hier.muziek(l));
            }
            return;
        }
        if (ss.vaart != null) {
            overtocht(p, ss, nu);
            return;
        }
        ZeldaHoofdstuk hier = ss.h.eiland == null ? null : ss.h.eiland.deelBij(l);
        if (hier == null) {                                  // buiten het eiland: terug naar je rustplaats
            p.setFallDistance(0);
            p.teleport(ss.rustplaats().plek);
            return;
        }
        ss.h = hier;
        hemel(p, ss, l);
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        boolean genees = false, bron = false;
        for (ZeldaHoofdstuk h : ss.h.eiland.delen()) {
            for (ZeldaHoofdstuk.Rust r : h.rust) genees |= r.zone.in(l);
            for (ZeldaHoofdstuk.Bron b : h.bronnen) bron |= b.zone.in(l);
        }
        if (genees || bron) {
            geneesBij(p, bron ? 4 : 2);
            if (ZeldaRegels.vulFlessen(s)) {
                voortgang.vuil();
                vernieuw(p, s, voorwerp("fles"));
                if (ZeldaRegels.flessen(s) > 0) zeg(p, Component.text("Flesjes vol", NamedTextColor.GRAY));
            }
        }
        if (bron && !ss.inBron && nu > ss.bronDeuntje) {
            ss.bronDeuntje = nu + 30_000L;
            muziek.speel(p, ZeldaMuziek.BRON);
            p.spawnParticle(Particle.HEART, p.getLocation().add(0, 1.2, 0), 4, 0.4, 0.4, 0.4, 0);
        }
        ss.inBron = bron;
        muziek.achter(p, ss.h.muziek(l));
        // huisdier gevonden maar nog naamloos: even vragen hoe hij heet
        if (ZeldaDier.AAN && ZeldaDier.soort(s) != null && s.dier == null && !ss.naamGevraagd && ss.rij.isEmpty() && ss.nu == null && !inGevecht(p)) {
            ss.naamGevraagd = true;
            naamMenu(p);
        }
        // vast? na een tijd zonder voortgang biedt Wiske één keer hulp aan
        int v = s.voortgang();
        if (v != ss.voortgangStand) {
            ss.voortgangStand = v;
            ss.laatsteVoortgang = nu;
        } else if (nu - ss.laatsteVoortgang > VAST_MS && nu > ss.aangeboden && ss.rij.isEmpty() && ss.nu == null && !inGevecht(p)) {
            ss.aangeboden = nu + AANBOD_OPNIEUW;
            muziek.speel(p, ZeldaMuziek.HINT);
            vertel(p, ZeldaHoofdstuk.r("Wiske", "Kwijt? Klik op mij, dan help ik je."));
        }
    }

    /** Staat deze speler in een kamer waar gevochten wordt? */
    boolean inGevecht(Player p) {
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Kamer k : h.kamers) if (k.fase == ZeldaHoofdstuk.Fase.BEZIG && k.binnen.in(p.getLocation())) return true;
        return false;
    }

    private void hemel(Player p, Sessie ss, Location l) {
        long t = ss.vaart != null ? zee.hemel(l) : ss.h.hemel(l);
        if (ss.vaart == null) for (ZeldaHoofdstuk.Kamer k : ss.h.kamers) if (k.hemel >= 0 && k.binnen.in(l)) t = k.hemel;
        if (Math.abs(t - ss.laatsteHemel) > 40) {
            ss.laatsteHemel = t;
            p.setPlayerTime(t, false);
        }
    }

    // ── zones ───────────────────────────────────────────────────────────────────────────────

    private void zones(Player p, Sessie ss, long nu) {
        Location l = p.getLocation();
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        if (ss.h.eiland == null) return;
        for (ZeldaHoofdstuk h : ss.h.eiland.delen()) {
            for (ZeldaHoofdstuk.Rust r : h.rust) if (r.zone.in(l)) rust(p, ss, s, h, r);
            for (ZeldaHoofdstuk.Verhaal v : h.verhalen)
                if (v.zone.in(l) && !s.heeft("v:" + v.id) && (v.vereist == null || s.heeft(v.vereist))) verhaal(p, h, s, v);
            for (ZeldaHoofdstuk.Vondst v : h.vondsten)
                if (v.zone.in(l) && !s.heeft(v.vlag) && (v.vereist == null || s.heeft(v.vereist)) && (v.voorwerp == null || s.heeft(v.voorwerp))) vind(p, s, v);
            for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) {
                Long start = u.loopt.get(p.getUniqueId());
                if (u.start.in(l)) {
                    if (start == null || nu - start > 600) begin(p, u, nu);
                } else if (start != null && u.finish.in(l)) gehaald(p, s, u, nu - start);
            }
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                boolean won = s.heeft("k:" + k.id);
                if (k.fase == ZeldaHoofdstuk.Fase.RUST && k.start.in(l)) binnenKamer(k, nu);
                else if (k.fase == ZeldaHoofdstuk.Fase.RUST && won && k.uit.omvang().groter(2).in(l)) doorgang(k, List.of(p));   // van de andere kant terug
                else if (k.fase == ZeldaHoofdstuk.Fase.GEWONNEN && !won && k.start.in(l)) begin(k, nu, false);              // nieuw erbij: gewoon vechten
            }
        }
    }

    private void rust(Player p, Sessie ss, ZeldaVoortgang.Stand s, ZeldaHoofdstuk h, ZeldaHoofdstuk.Rust r) {
        ss.rh = h;
        ss.rust = r.index;
        boolean nieuw = false;
        for (String v : r.geeft) nieuw |= s.zet(v);
        if (nieuw) uitrusten(p, s);
        if (h.eiland != null && s.zet("e:" + h.eiland.id())) nieuw = true;      // hier ben je geweest (ook zonder te varen)
        String hier = h.id() + ":" + r.index;
        if (!hier.equals(s.laatste)) {
            s.laatste = hier;
            s.hoofdstuk = h.id();
            nieuw = true;
        }
        s.bereik(h.id(), r.index);
        if (s.bezoek(h.id(), r.index)) {
            voortgang.vuil();
            bewaar();
            zeg(p, Component.text("Rustplaats · " + r.naam, NamedTextColor.GRAY));
            muziek.speel(p, ZeldaMuziek.RUST);
            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.02);
            herstel(p);
        } else if (nieuw) voortgang.vuil();
    }

    private void verhaal(Player p, ZeldaHoofdstuk h, ZeldaVoortgang.Stand s, ZeldaHoofdstuk.Verhaal v) {
        s.zet("v:" + v.id);
        voortgang.vuil();
        if (v.uitleg && s.heeft("o:uitleg-uit")) return;      // uitleg overslaan
        for (ZeldaHoofdstuk.Regel r : v.regels) vertel(p, r);
        if (v.einde) {
            s.zet(h.id());
            bewaar();
            muziek.speel(p, ZeldaMuziek.KLAAR);
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.5, 0), 40, 1.2, 1, 1.2, 0.02);
        }
    }

    private void vind(Player p, ZeldaVoortgang.Stand s, ZeldaHoofdstuk.Vondst v) {
        s.zet(v.vlag);
        if (v.vlag.startsWith("fles")) ZeldaRegels.vulFlessen(s);
        voortgang.vuil();
        bewaar();
        uitrusten(p, s);
        if (v.vlag.startsWith("hart")) herstel(p);
        muziek.speel(p, ZeldaMuziek.VONDST);
        if (v.vlag.startsWith("hs:")) stukje(p, s, v.naam);
        else zeg(p, Component.text(v.naam, NamedTextColor.WHITE).append(Component.text(" · " + v.uitleg, NamedTextColor.GRAY)));
        p.getWorld().spawnParticle(Particle.END_ROD, v.plek, 30, 0.3, 0.5, 0.3, 0.05);
        if (v.toon != null && v.toon.isValid() && !nodig(v, p.getUniqueId())) {
            v.toon.remove();
            v.toon = null;
        }
        if (v.daarna != null) v.daarna.doe(this, p, s);
    }

    /** Een hartstukje erbij: «3 van 4», of een heel hart; de eerste keer legt Wiske het uit. */
    private void stukje(Player p, ZeldaVoortgang.Stand s, String naam) {
        int los = ZeldaRegels.losseStukjes(s);
        if (los == 0) herstel(p);
        zeg(p, Component.text(naam, NamedTextColor.WHITE).append(Component.text(los == 0 ? " · een hart erbij" : " · " + los + " van " + ZeldaRegels.STUKJES_PER_HART, NamedTextColor.GRAY)));
        if (s.zet("v:hartstukje")) {
            voortgang.vuil();
            uitleg(p, ZeldaHoofdstuk.r("Wiske", "Een hartstukje! Vier maken samen een heel hart."));
        }
    }

    /** Uitleg van Wiske (hoe iets werkt): overgeslagen als je op de kaart «Uitleg: uit» koos. */
    void uitleg(Player p, ZeldaHoofdstuk.Regel... regels) {
        if (voortgang.van(p.getUniqueId()).heeft("o:uitleg-uit")) return;
        for (ZeldaHoofdstuk.Regel r : regels) vertel(p, r);
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
            if (p != null && !p.isDead() && !beheer.is(id) && b.in(p.getLocation())) l.add(p);
        }
        return l;
    }

    /**
     * Iemand stapt een kamer in. Won iedereen die binnen is hem al ({@code k:<id>}), dan gaan de deuren gewoon open
     * (vrije doorgang: niet steeds opnieuw vechten); wie nog eens wil, luidt de bel. Anders begint het gevecht.
     */
    private void binnenKamer(ZeldaHoofdstuk.Kamer k, long nu) {
        List<Player> in = in(k.binnen);
        if (in.isEmpty()) return;
        List<ZeldaVoortgang.Stand> wie = new ArrayList<>();
        for (Player p : in) wie.add(voortgang.van(p.getUniqueId()));
        if (!ZeldaRegels.doorgang(wie, k.id)) begin(k, nu, false);
        else doorgang(k, in);
    }

    /** Vrije doorgang: beide deuren open (gaat na een minuut zonder iemand weer dicht); de eerste keer legt Wiske de bel uit. */
    private void doorgang(ZeldaHoofdstuk.Kamer k, List<Player> in) {
        k.fase = ZeldaHoofdstuk.Fase.GEWONNEN;
        k.leegSinds = 0;
        k.terug = false;
        k.meegedaan.clear();
        Location midden = k.start.midden(in.get(0).getWorld());
        deur(k.in, true, midden);
        deur(k.uit, true, midden);
        if (k.bel != null) for (Player p : in)
            if (voortgang.van(p.getUniqueId()).zet("v:bel")) {
                voortgang.vuil();
                uitleg(p, ZeldaHoofdstuk.r("Wiske", "Hier won je al. Nog eens vechten? Luid de bel."));
            }
    }

    /** De bel in een kamer: nog eens vechten, nu met de extra golven. */
    void luid(Player p, ZeldaHoofdstuk.Kamer k) {
        if (k.fase == ZeldaHoofdstuk.Fase.BEZIG || k.bel == null || !k.binnen.in(p.getLocation())) return;
        p.getWorld().playSound(new Location(p.getWorld(), k.bel[0] + 0.5, k.bel[1] + 0.5, k.bel[2] + 0.5), Sound.BLOCK_BELL_USE, 1f, 0.9f);
        begin(k, System.currentTimeMillis(), true);
    }

    private void begin(ZeldaHoofdstuk.Kamer k, long nu, boolean terug) {
        List<Player> in = in(k.binnen);
        if (in.isEmpty()) return;
        k.terug = terug;
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
        List<ZeldaHoofdstuk.Soort> soorten = new ArrayList<>(ZeldaRegels.golf(k, k.golf));
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
        if (k.golf + 1 < ZeldaRegels.golven(k)) {
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
        for (UUID id : k.meegedaan)
            if (sessies.containsKey(id) && voortgang.van(id).zet("k:" + k.id)) voortgang.vuil();
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
        k.terug = false;
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
            case ROVER -> org.bukkit.entity.Drowned.class;
            case ROVERSCHUTTER -> org.bukkit.entity.Skeleton.class;
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
                case ROVER -> {                                      // meeuwenrover: verweerd, met een kort zwaard
                    hp = 18;
                    dmg = 3;
                    eq.setHelmet(gekleurd(Material.LEATHER_HELMET, 0xb02e26));
                    eq.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                }
                case ROVERSCHUTTER -> {                              // schiet van achteren, rode zakdoek
                    hp = 14;
                    eq.setHelmet(gekleurd(Material.LEATHER_HELMET, 0xb02e26));
                    eq.setChestplate(gekleurd(Material.LEATHER_CHESTPLATE, 0x3c44aa));
                    eq.setItemInMainHand(new ItemStack(Material.BOW));
                }
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

    private static ItemStack gekleurd(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(Color.fromRGB(rgb));
            it.setItemMeta(lm);
        }
        return it;
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
        if (u.daarna != null) u.daarna.doe(this, p, s);
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

    // ── spiegels: het licht naar het doel leiden ────────────────────────────────────────────

    /** Waar het licht nu langs gaat (en of het het doel raakt). */
    ZeldaRegels.Straal straal(ZeldaHoofdstuk.Spiegels sp, List<BlockFace> stand) {
        var w = plugin.w;
        return ZeldaRegels.straal(sp.bron, sp.richting, sp.spiegels, stand, sp.doel, c -> w.getBlockAt(c[0], c[1], c[2]).isPassable(), 64);
    }

    /** Rechtsklik op spiegel {@code i}: een kwartslag draaien; komt het licht nu bij het doel, dan is hij opgelost. */
    void draai(Player p, ZeldaHoofdstuk.Spiegels sp, int i) {
        if (sp.opgelost) return;
        sp.stand.set(i, ZeldaRegels.draai(sp.stand.get(i)));
        int[] c = sp.spiegels.get(i);
        var w = p.getWorld();
        w.getBlockAt(c[0], c[1], c[2]).setBlockData(ZeldaHoofdstuk.Spiegels.blok(sp.stand.get(i)), false);
        w.playSound(new Location(w, c[0] + 0.5, c[1] + 0.5, c[2] + 0.5), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f + 0.1f * i);
        sp.leegSinds = 0;
        if (straal(sp, sp.stand).raak()) losOp(sp, false);
    }

    private void losOp(ZeldaHoofdstuk.Spiegels sp, boolean stil) {
        sp.opgelost = true;
        sp.leegSinds = 0;
        var w = plugin.w;
        w.getBlockAt(sp.doel[0], sp.doel[1], sp.doel[2]).setBlockData(sp.doelAan, false);
        deur(sp.deur, true, sp.gebied.midden(w));
        if (stil) return;
        for (Player q : in(sp.gebied)) {
            if (voortgang.van(q.getUniqueId()).zet("r:" + sp.id)) voortgang.vuil();
            muziek.speel(q, ZeldaMuziek.OPGELOST);
            for (ZeldaHoofdstuk.Regel rg : sp.bijOplossen) vertel(q, rg);
        }
    }

    /** Spiegels terug naar het begin. */
    void herstel(ZeldaHoofdstuk.Spiegels sp) {
        var w = plugin.w;
        sp.opgelost = false;
        sp.leegSinds = 0;
        for (int i = 0; i < sp.spiegels.size(); i++) {
            sp.stand.set(i, sp.begin.get(i));
            int[] c = sp.spiegels.get(i);
            w.getBlockAt(c[0], c[1], c[2]).setBlockData(ZeldaHoofdstuk.Spiegels.blok(sp.begin.get(i)), false);
        }
        w.getBlockAt(sp.doel[0], sp.doel[1], sp.doel[2]).setBlockData(sp.doelUit, false);
        deur(sp.deur, false, sp.gebied.midden(w));
    }

    /** Twee keer per seconde: het licht laten zien aan wie in de buurt is; opgelost en een minuut niemand = terug. */
    private void spiegels(ZeldaHoofdstuk.Spiegels sp, long nu) {
        List<Player> bij = in(sp.gebied.groter(4));
        if (sp.opgelost) {
            if (!bij.isEmpty()) sp.leegSinds = 0;
            else if (sp.leegSinds == 0) sp.leegSinds = nu;
            else if (nu - sp.leegSinds > 60_000L) herstel(sp);
        } else for (Player p : in(sp.gebied))                       // wie hem al eens oploste, hoeft dat niet opnieuw
            if (voortgang.van(p.getUniqueId()).heeft("r:" + sp.id)) {
                List<BlockFace> op = sp.oplossing();
                for (int i = 0; i < sp.spiegels.size(); i++) {
                    sp.stand.set(i, op.get(i));
                    int[] c = sp.spiegels.get(i);
                    plugin.w.getBlockAt(c[0], c[1], c[2]).setBlockData(ZeldaHoofdstuk.Spiegels.blok(op.get(i)), false);
                }
                losOp(sp, true);
                break;
            }
        if (bij.isEmpty()) return;
        ZeldaRegels.Straal st = straal(sp, sp.stand);
        var w = plugin.w;
        Particle.DustOptions licht = new Particle.DustOptions(Color.fromRGB(0xfff2a0), 1.1f);
        List<Location> punten = new ArrayList<>();
        double[] vorige = {sp.bron[0] + 0.5, sp.bron[1] + 0.5, sp.bron[2] + 0.5};
        List<int[]> door = new ArrayList<>(st.punten());
        if (st.raak()) door.add(sp.doel);
        for (int[] c : door) {                               // twee stipjes per blok, van midden naar midden
            double[] m = {c[0] + 0.5, c[1] + 0.5, c[2] + 0.5};
            punten.add(new Location(w, (vorige[0] + m[0]) / 2, (vorige[1] + m[1]) / 2, (vorige[2] + m[2]) / 2));
            punten.add(new Location(w, m[0], m[1], m[2]));
            vorige = m;
        }
        for (Player p : bij)
            for (Location l : punten) p.spawnParticle(Particle.DUST, l, 1, 0.02, 0.02, 0.02, 0, licht);
    }

    // ── gebarsten muren (bommen) en haakpunten (klauwhaak) ──────────────────────────────────

    /**
     * Een bom breekt een gebarsten muur open (de bommen komen op eiland 2: roep dit aan als een bom bij
     * {@link #barstBij} ontploft). Wie erbij is, onthoudt het ({@code b:<id>}) en vindt hem voortaan open.
     */
    void breek(Player p, ZeldaHoofdstuk.Barst b) {
        if (b.muur.isOpen) return;
        Location m = b.muur.omvang().midden(p.getWorld()).add(0, 1, 0);
        deur(b.muur, true, m);
        b.leegSinds = 0;
        m.getWorld().playSound(m, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
        m.getWorld().spawnParticle(Particle.EXPLOSION, m, 2, 0.4, 0.4, 0.4, 0);
        List<Player> wie = in(b.gebied);
        if (!wie.contains(p)) wie.add(p);
        for (Player q : wie) if (voortgang.van(q.getUniqueId()).zet("b:" + b.id)) voortgang.vuil();
        muziek.speel(p, ZeldaMuziek.OPGELOST);
    }

    /** De gebarsten muur binnen twee blokken van deze plek (waar een bom ontploft), of null. */
    ZeldaHoofdstuk.Barst barstBij(Location l) {
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Barst b : h.barsten) if (b.muur.omvang().groter(2).in(l)) return b;
        return null;
    }

    /** Gebarsten muur weer dicht. */
    void herstel(ZeldaHoofdstuk.Barst b) {
        b.leegSinds = 0;
        deur(b.muur, false, b.gebied.midden(plugin.w).add(0, 2, 0));
    }

    private void barst(ZeldaHoofdstuk.Barst b, long nu) {
        if (b.muur.isOpen) {                                 // een minuut niemand in de buurt: weer dicht
            if (!in(b.gebied.groter(6)).isEmpty()) b.leegSinds = 0;
            else if (b.leegSinds == 0) b.leegSinds = nu;
            else if (nu - b.leegSinds > 60_000L) herstel(b);
            return;
        }
        for (Player p : in(b.gebied))                        // wie hem ooit openbrak, vindt hem open
            if (voortgang.van(p.getUniqueId()).heeft("b:" + b.id)) {
                b.leegSinds = 0;
                deur(b.muur, true, b.gebied.midden(plugin.w).add(0, 1, 0));
                return;
            }
    }

    /** Het haakpunt in dit blok (voor de klauwhaak van eiland 3), of null. */
    ZeldaHoofdstuk.Haakpunt haakpunt(Block b) {
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Haakpunt k : h.haakpunten) if (k.x() == b.getX() && k.y() == b.getY() && k.z() == b.getZ()) return k;
        return null;
    }

    // ── versiering: vondsten en personen alleen als er iemand in de buurt is ──────────────────

    private boolean nodig(ZeldaHoofdstuk.Vondst v, UUID behalve) {
        for (UUID id : sessies.keySet()) {
            if (id.equals(behalve)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld().equals(v.plek.getWorld()) && p.getLocation().distanceSquared(v.plek) < 48 * 48
                    && !voortgang.van(id).heeft(v.vlag) && (v.vereist == null || voortgang.van(id).heeft(v.vereist))) return true;
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
                    d.setGlowing(!v.verborgen);                        // verborgen: niet door de muren heen te zien
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
                @SuppressWarnings("unchecked")
                Class<Entity> klasse = (Class<Entity>) (p.soort.getEntityClass() == null ? Villager.class : p.soort.getEntityClass());
                Entity nieuw = p.plek.getWorld().spawn(p.plek, klasse, e -> {
                    e.setPersistent(false);
                    e.addScoreboardTag(TAG);
                    e.addScoreboardTag(NPC);
                    e.setInvulnerable(true);
                    e.setSilent(true);
                    e.customName(Component.text(p.naam, GROEN));
                    e.setCustomNameVisible(false);
                    e.setRotation(p.plek.getYaw(), 0);
                    if (e instanceof LivingEntity le) {
                        le.setAI(false);
                        le.setCollidable(false);
                        le.setRemoveWhenFarAway(false);
                    }
                    if (p.kind && e instanceof org.bukkit.entity.Ageable a) {
                        a.setBaby();
                        if (a instanceof org.bukkit.entity.Breedable br) br.setAgeLock(true);
                    }
                    if (e instanceof Villager v) {
                        if (p.beroep != null) v.setProfession(p.beroep);
                        if (p.streek != null) v.setVillagerType(p.streek);
                        v.setVillagerLevel(5);
                    }
                });
                if (nieuw instanceof LivingEntity le) p.ent = le;
                else nieuw.remove();
            } else if (!moet && p.ent != null) {
                p.ent.remove();
                p.ent = null;
            }
        }
        // wie iets nieuws te zeggen heeft (een gesprek dat je nog niet hoorde), glinstert zacht: alleen voor jou
        if (tikken % 40 == 0) for (ZeldaHoofdstuk.Persoon pe : h.personen) {
            if (pe.ent == null || !pe.ent.isValid() || pe.gesprekken.isEmpty()) continue;
            for (UUID id : sessies.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.getWorld().equals(pe.plek.getWorld()) || p.getLocation().distanceSquared(pe.plek) > 24 * 24) continue;
                if (pe.nieuw(voortgang.van(id)))
                    p.spawnParticle(Particle.WAX_OFF, pe.ent.getLocation().add(0, pe.ent.getHeight() + 0.4, 0), 3, 0.15, 0.1, 0.15, 0.3);
            }
        }
    }

    /** Bronnen (feeënkringen): een zachte ring van licht, alleen als er iemand in de buurt is. */
    private void bronnen() {
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Bron b : h.bronnen) {
                if (!dichtbij(b.midden, 32)) continue;
                double hoek = tikken / 10.0 * 0.5;
                for (int i = 0; i < 6; i++) {
                    double a = hoek + i * Math.PI / 3;
                    Location l = b.midden.clone().add(Math.cos(a) * 1.3, 0.15 + (i % 2) * 0.1, Math.sin(a) * 1.3);
                    b.midden.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0xf7a8d8), 0.9f));
                }
                if (tikken % 40 == 0) b.midden.getWorld().spawnParticle(Particle.END_ROD, b.midden.clone().add(0, 0.8, 0), 2, 0.4, 0.4, 0.4, 0.01);
            }
    }

    /** Verborgen vondsten in de buurt die deze speler nog niet heeft (voor het snuffelen van zijn huisdier). */
    private List<Location> verborgen(Player p) {
        List<Location> l = new ArrayList<>();
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || ss.h.eiland == null) return l;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        for (ZeldaHoofdstuk h : ss.h.eiland.delen())
            for (ZeldaHoofdstuk.Vondst v : h.vondsten)
                if (v.verborgen && !s.heeft(v.vlag) && (v.vereist == null || s.heeft(v.vereist)) && (v.voorwerp == null || s.heeft(v.voorwerp))) l.add(v.plek);
        return l;
    }

    // ── kaart: reizen, opnieuw, uitleg, varen ───────────────────────────────────────────────

    /**
     * De kaart (vak 9): bovenaan de rustplaatsen van dit eiland (klik = reizen, rechtsklik = opnieuw vanaf hier: de
     * uitleg en puzzels erna komen terug), dan de eilanden (klik = varen), onderaan opdrachten, harten, harnas, uitleg
     * aan/uit en terug naar de hub.
     */
    void kaart(Player p) {
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || ss.h.eiland == null) return;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        ZeldaEiland eiland = ss.h.eiland;
        Menu m = new Menu(false);
        m.inv = Bukkit.createInventory(m, 36, Component.text(eiland.naam(), NamedTextColor.DARK_GRAY));
        int slot = 0;
        for (ZeldaHoofdstuk h : eiland.delen())
            for (ZeldaHoofdstuk.Rust r : h.rust) {
                if (slot > 17) break;
                ItemStack it;
                boolean hier = h == ss.rh && r.index == ss.rust;
                if (s.bezocht(h.id(), r.index)) {
                    it = ding(hier ? Material.SOUL_CAMPFIRE : Material.CAMPFIRE, r.naam, "rust:" + h.id() + ":" + r.index);
                    lore(it, hier ? "hier ben je" : "klik: reizen", "rechtsklik: opnieuw vanaf hier");
                } else it = ding(Material.GRAY_STAINED_GLASS_PANE, "…", "niets");
                m.inv.setItem(slot++, it);
            }
        slot = 18;
        for (ZeldaEiland e : eilanden) {
            if (!e.open(s) || e.aanlegRust() == null) continue;
            m.inv.setItem(slot++, eilandItem(e, eiland));
            if (slot > 26) break;
        }
        // opdrachten (zijmissies waar je mee bezig bent)
        List<String> regels = new ArrayList<>();
        for (ZeldaEiland e : eilanden)
            for (ZeldaHoofdstuk.Missie mi : ZeldaRegels.bezig(alle(e, h -> h.missies), s)) regels.add(mi.naam() + ": " + ZeldaRegels.stap(mi, s));
        ItemStack op = ding(Material.WRITABLE_BOOK, "Opdrachten", "niets");
        lore(op, regels.isEmpty() ? new String[]{"geen"} : regels.toArray(new String[0]));
        m.inv.setItem(27, op);
        ItemStack hart = ding(Material.RED_DYE, ZeldaRegels.harten(s) + " harten", "niets");
        java.util.Set<String> hier = new java.util.LinkedHashSet<>(alle(eiland, h -> List.copyOf(h.stukjes)));
        int gevonden = 0;
        for (String v : hier) if (s.heeft(v)) gevonden++;
        if (hier.isEmpty()) lore(hart, "hartstukjes " + ZeldaRegels.losseStukjes(s) + " van " + ZeldaRegels.STUKJES_PER_HART);
        else lore(hart, "hartstukjes " + ZeldaRegels.losseStukjes(s) + " van " + ZeldaRegels.STUKJES_PER_HART,
                eiland.naam() + ": " + gevonden + " van " + hier.size() + " gevonden");
        m.inv.setItem(28, hart);
        int hn = ZeldaRegels.harnas(s);
        m.inv.setItem(29, ding(new Material[]{Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE,
                Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE}[hn], ZeldaRegels.HARNAS_NAAM[hn], "niets"));
        if (ZeldaRegels.flessen(s) > 0) {
            ItemStack fl = ding(Material.GLASS_BOTTLE, "Flesjes", "niets");
            lore(fl, ZeldaRegels.vol(s) + " van " + ZeldaRegels.flessen(s) + " vol", "vullen bij een bron of zielvuur");
            m.inv.setItem(30, fl);
        }
        boolean uitleg = !s.heeft("o:uitleg-uit");
        ItemStack ui = ding(uitleg ? Material.BOOK : Material.GRAY_DYE, "Uitleg: " + (uitleg ? "aan" : "uit"), "uitleg");
        lore(ui, "klik: " + (uitleg ? "uit (alleen het verhaal)" : "aan"));
        m.inv.setItem(32, ui);
        m.inv.setItem(35, ding(Material.OAK_DOOR, "Terug naar de hub", "hub"));
        p.openInventory(m.inv);
    }

    /** Een eiland in een menu (kaart of Brecht): klik = varen; waar je bent glanst. */
    private ItemStack eilandItem(ZeldaEiland e, ZeldaEiland hier) {
        ItemStack it = ding(e.icoon(), e.naam(), "eiland:" + e.id());
        lore(it, e == hier ? "hier ben je" : "klik: varen");
        if (e == hier) {
            ItemMeta meta = it.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Naar rustplaats {@code i} van deel {@code h} (alleen als je die al bereikte). */
    void reis(Player p, ZeldaHoofdstuk h, int i) {
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || i < 0 || i >= h.rust.size() || !voortgang.van(p.getUniqueId()).bezocht(h.id(), i)) return;
        ss.h = h;
        ss.rh = h;
        ss.rust = i;
        p.setFallDistance(0);
        p.teleport(h.rust.get(i).plek);
        p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
    }

    /** Opnieuw vanaf rustplaats {@code i}: de verhalen en puzzels erna vergeten (niet je spullen), en erheen. */
    void opnieuw(Player p, ZeldaHoofdstuk h, int i) {
        Sessie ss = sessie(p);
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        if (ss == null || ss.vaart != null || i < 0 || i >= h.rust.size() || !s.bezocht(h.id(), i)) return;
        for (String v : ZeldaRegels.opnieuw(h.volgorde, h.rust.get(i))) s.wis(v);
        voortgang.vuil();
        reis(p, h, i);
        zeg(p, Component.text("Opnieuw vanaf " + h.rust.get(i).naam, NamedTextColor.GRAY));
    }

    // ── varen: de overtocht naar een ander eiland ───────────────────────────────────────────

    /** Aan boord van de Zilvermeeuw; na een paar tellen leg je aan bij {@code naar}. */
    void vaar(Player p, ZeldaEiland naar) {
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || naar == null || naar.aanlegRust() == null || zee.dek.isEmpty()) return;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        boolean eerste = !s.heeft("e:" + naar.id());
        long nu = System.currentTimeMillis();
        ss.vaart = new Vaart(naar, nu + (eerste && !naar.overtocht().isEmpty() ? 16_000L : 9_000L), eerste);
        ss.rij.clear();
        ss.nu = null;
        ss.tot = 0;
        muziek.lus(p, null);
        muziek.achter(p, ZeldaMuziek.ZEE);
        p.setFallDistance(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
        p.teleport(zee.dek.get(rnd.nextInt(zee.dek.size())));
        ss.laatsteHemel = -1;
        hemel(p, ss, p.getLocation());
        p.playSound(p, Sound.ENTITY_BOAT_PADDLE_WATER, 0.6f, 0.8f);
        if (eerste && !naar.overtocht().isEmpty()) for (ZeldaHoofdstuk.Regel r : naar.overtocht()) vertel(p, r);
        else {
            String[][] zinnen = {{"Wiske", "Kijk, meeuwen! Dan is er land in de buurt."}, {"Brecht", "Nog even, dan leggen we aan."},
                    {"Wiske", "Zeeziek? Ik niet. Ik heb vleugels."}, {"Brecht", "De Glazen Zee is kalm vandaag."}};
            String[] z = zinnen[rnd.nextInt(zinnen.length)];
            vertel(p, ZeldaHoofdstuk.r(z[0], z[1]));
        }
    }

    /**
     * Brecht: varen. Is er een ander eiland open, dan een klein menu «Waarheen?» (klik = varen); anders nog even
     * geduld. Een nieuw eiland (met {@code openNa}) verschijnt hier vanzelf.
     */
    void varen(Player p) {
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || ss.h.eiland == null) return;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        List<ZeldaEiland> open = new ArrayList<>();
        for (ZeldaEiland e : eilanden) if (e.open(s) && e.aanlegRust() != null) open.add(e);
        if (open.size() < 2 || zee.dek.isEmpty()) {
            vertel(p, ZeldaHoofdstuk.r("Brecht", "Nog even geduld. Zodra de wind draait, varen we."));
            return;
        }
        Menu m = new Menu(false);
        m.inv = Bukkit.createInventory(m, 9, Component.text("Waarheen?", NamedTextColor.DARK_GRAY));
        int slot = 0;
        for (ZeldaEiland e : open) if (slot < 9) m.inv.setItem(slot++, eilandItem(e, ss.h.eiland));
        p.openInventory(m.inv);
    }

    /** Elke seconde aan boord: golven en schuim, aan dek blijven, en aankomen. */
    private void overtocht(Player p, Sessie ss, long nu) {
        Location l = p.getLocation();
        if (!zee.speelgebied.in(l)) {
            p.setFallDistance(0);
            p.teleport(zee.dek.get(0));
        }
        hemel(p, ss, l);
        var w = zee.w;
        double y = zee.G - 0.6;
        for (int i = 0; i < 6; i++) {                        // schuim langs de romp en voor de boeg
            double x = ZeldaZee.X0 + 3 + rnd.nextDouble() * 26;
            double z = ZeldaZee.MZ + (rnd.nextBoolean() ? 1 : -1) * (4.4 + rnd.nextDouble() * 0.8);
            p.spawnParticle(Particle.SPLASH, new Location(w, x, y + 0.6, z), 6, 0.4, 0.1, 0.2, 0.1);
        }
        p.spawnParticle(Particle.CLOUD, new Location(w, ZeldaZee.X0 + 31, y + 0.4, ZeldaZee.MZ), 4, 0.6, 0.1, 0.8, 0.02);
        p.spawnParticle(Particle.BUBBLE, new Location(w, ZeldaZee.X0 - 1, y, ZeldaZee.MZ), 8, 1.2, 0.2, 1.5, 0.05);
        if (tikken % 60 == 0) p.playSound(p, Sound.AMBIENT_UNDERWATER_EXIT, 0.25f, 0.6f);
        if (nu >= ss.vaart.tot()) aankomst(p, ss);
    }

    private void aankomst(Player p, Sessie ss) {
        ZeldaEiland e = ss.vaart.naar();
        ss.vaart = null;
        ZeldaHoofdstuk.Rust r = e.aanlegRust();
        ZeldaHoofdstuk h = e.delen().get(0);
        ss.h = h;
        ss.rh = h;
        ss.rust = r.index;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        if (s.zet("e:" + e.id())) voortgang.vuil();
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
        p.setFallDistance(0);
        p.teleport(r.plek);
        ss.laatsteHemel = -1;
        hemel(p, ss, r.plek);
        muziek.achter(p, h.muziek(r.plek));
        p.playSound(p, Sound.BLOCK_WOOD_PLACE, 0.6f, 0.8f);
        zeg(p, Component.text(e.naam(), NamedTextColor.GRAY));
    }

    // ── hint: Wiske helpt (niet te veel) ────────────────────────────────────────────────────

    /**
     * {@code /hint} of klik op Wiske. De eerste keer een duwtje (het doel), vraag je het binnen twee minuten nog eens,
     * dan zegt ze wat je moet doen en vliegt ze even die kant op. Geen doel meer? Dan je zijmissies, of verder varen.
     */
    void hint(Player p) {
        Sessie ss = sessie(p);
        if (ss == null) {
            p.sendActionBar(Component.text("Hint · alleen in Story of Zelda", NamedTextColor.GRAY));
            return;
        }
        long nu = System.currentTimeMillis();
        if (nu < ss.hintWacht) return;
        ss.hintWacht = nu + 2500;
        ss.rij.clear();
        ss.tot = 0;
        ss.aangeboden = nu + AANBOD_OPNIEUW;
        muziek.speel(p, ZeldaMuziek.HINT);
        if (ss.vaart != null) {
            vertel(p, ZeldaHoofdstuk.r("Wiske", "We zijn onderweg. Even genieten van de zee."));
            return;
        }
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        ZeldaEiland e = ss.h.eiland;
        String vorige = ss.hintDoel;
        ZeldaHoofdstuk.Doel d = ZeldaRegels.doel(alle(e, h -> h.doelen), s);
        if (d != null) {
            boolean nogEens = d.id().equals(vorige) && nu - ss.hintTijd < 120_000L;
            ss.hintDoel = d.id();
            ss.hintTijd = nu;
            if (nogEens) {
                vertel(p, ZeldaHoofdstuk.r("Wiske", d.verder() != null ? d.verder() : d.tekst()));
                metgezel.wijs(p, d.plek());
            } else vertel(p, ZeldaHoofdstuk.r("Wiske", d.tekst()));
            return;
        }
        ss.hintDoel = null;
        List<ZeldaHoofdstuk.Missie> bezig = ZeldaRegels.bezig(alle(e, h -> h.missies), s);
        if (!bezig.isEmpty()) {
            ZeldaHoofdstuk.Missie m = bezig.get(rnd.nextInt(bezig.size()));
            vertel(p, ZeldaHoofdstuk.r("Wiske", m.naam() + ": " + ZeldaRegels.stap(m, s)));
            return;
        }
        for (ZeldaEiland o : eilanden)
            if (o != e && o.open(s) && o.aanlegRust() != null && !s.heeft("e:" + o.id())) {
                vertel(p, ZeldaHoofdstuk.r("Wiske", "Hier zijn we klaar. Op je kaart (vak 9) kun je naar " + ZeldaRegels.inZin(o.naam()) + " varen."));
                return;
            }
        for (ZeldaHoofdstuk.Persoon pe : alle(e, h -> h.personen))     // iemand met iets nieuws (nog eens vragen = ze wijst)
            if (pe.nieuw(s)) {
                ss.hintDoel = "p:" + pe.naam;
                if (ss.hintDoel.equals(vorige) && nu - ss.hintTijd < 120_000L) metgezel.wijs(p, pe.plek);
                ss.hintTijd = nu;
                vertel(p, ZeldaHoofdstuk.r("Wiske", pe.naam + " wil je iets vertellen."));
                return;
            }
        for (ZeldaHoofdstuk.Vondst v : alle(e, h -> h.vondsten))                // met een nieuw voorwerp kan hier nog iets
            if (later(v, s)) {
                ss.hintDoel = "v:" + v.vlag;
                if (ss.hintDoel.equals(vorige) && nu - ss.hintTijd < 120_000L) metgezel.wijs(p, v.plek);
                ss.hintTijd = nu;
                vertel(p, ZeldaHoofdstuk.r("Wiske", "Met je nieuwe spullen kun je hier nog iets halen."));
                return;
            }
        for (ZeldaEiland o : eilanden)                                          // … of op een ander eiland
            if (o != e && o.open(s) && o.aanlegRust() != null && alle(o, h -> h.vondsten).stream().anyMatch(v -> later(v, s))) {
                vertel(p, ZeldaHoofdstuk.r("Wiske", "Op " + ZeldaRegels.inZin(o.naam()) + " kun je nu iets halen. Vaar er eens heen."));
                return;
            }
        ZeldaHoofdstuk laatste = e.delen().get(e.delen().size() - 1);
        if (!s.heeft(e.klaar()) && laatste.wacht() != null) {             // het eiland is nog niet af, maar hier is niets meer
            vertel(p, ZeldaHoofdstuk.r("Wiske", laatste.wacht()));
            return;
        }
        vertel(p, ZeldaHoofdstuk.r("Wiske", "Ik weet het ook niet. Kijk eens rond, praat met wie je ziet."));
    }

    /** Een vondst die je pas met een voorwerp kon pakken, en dat voorwerp heb je nu. */
    private static boolean later(ZeldaHoofdstuk.Vondst v, ZeldaVoortgang.Stand s) {
        return v.voorwerp != null && s.heeft(v.voorwerp) && !s.heeft(v.vlag) && (v.vereist == null || s.heeft(v.vereist));
    }

    // ── personen, gesprekken en zijmissies ──────────────────────────────────────────────────

    /** Praten met een persoon: het gesprek dat nu klopt (of zijn gewone praatje), daarna wat er moet gebeuren. */
    void praat(Player p, ZeldaHoofdstuk.Persoon pe) {
        Sessie ss = sessie(p);
        if (ss == null || !ss.rij.isEmpty() || ss.nu != null) return;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        ZeldaHoofdstuk.Gesprek g = pe.gesprek(s);
        if (pe.ent != null && pe.ent.isValid()) {             // kijk de speler aan
            Location kijk = pe.ent.getLocation().setDirection(p.getLocation().toVector().subtract(pe.ent.getLocation().toVector()));
            pe.ent.setRotation(kijk.getYaw(), 0);
        }
        for (ZeldaHoofdstuk.Regel r : g == null ? pe.praat : g.regels()) vertel(p, r);
        if (g == null) return;
        if (g.id() != null && s.zet("g:" + g.id())) voortgang.vuil();
        if (g.daarna() != null) g.daarna().doe(this, p, s);
    }

    /** Zet een zijmissie op stap {@code stap} (1 = begonnen, {@link ZeldaVoortgang#KLAAR} = klaar), met een zacht deuntje. */
    void missie(Player p, String id, int stap) {
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        if (s.missie(id) == stap) return;
        s.missies.put(id, stap);
        voortgang.vuil();
        bewaar();
        String naam = id;
        for (ZeldaHoofdstuk h : hoofdstukken) if (h.missie(id) != null) naam = h.missie(id).naam();
        muziek.speel(p, ZeldaMuziek.OPDRACHT);
        if (stap == 1) zeg(p, Component.text("Opdracht · " + naam, NamedTextColor.GRAY));
        else if (stap == ZeldaVoortgang.KLAAR) zeg(p, Component.text("Opdracht voltooid · " + naam, NamedTextColor.GRAY));
    }

    /** Een beloning (vlag): uitrusting bijwerken, deuntje, één regel. */
    void beloon(Player p, String vlag, String naam, String uitleg) {
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        if (!s.zet(vlag)) return;
        if (vlag.startsWith("fles")) ZeldaRegels.vulFlessen(s);
        voortgang.vuil();
        bewaar();
        uitrusten(p, s);
        muziek.speel(p, ZeldaMuziek.VONDST);
        if (vlag.startsWith("hs:")) stukje(p, s, naam);
        else zeg(p, Component.text(naam, NamedTextColor.WHITE).append(Component.text(uitleg == null || uitleg.isEmpty() ? "" : " · " + uitleg, NamedTextColor.GRAY)));
    }

    /** Je huisdier heeft nog geen naam: kies er een (dicht je het menu, dan krijgt hij de eerste). */
    void naamMenu(Player p) {
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        Menu m = new Menu(true);
        m.inv = Bukkit.createInventory(m, 9, Component.text("Hoe heet hij?", NamedTextColor.DARK_GRAY));
        for (int i = 0; i < ZeldaDier.NAMEN.size(); i++) m.inv.setItem(2 + i, ding(Material.NAME_TAG, ZeldaDier.NAMEN.get(i), "naam:" + i));
        if (ZeldaDier.soort(s) != null) p.openInventory(m.inv);
    }

    private void kiesNaam(Player p, String naam) {
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        if (s.dier != null) return;
        s.dier = naam;
        voortgang.vuil();
        bewaar();
        p.playSound(p, Sound.ENTITY_FOX_AMBIENT, 0.6f, 1.4f);
        zeg(p, Component.text(naam, GROEN).append(Component.text(" · loopt met je mee", NamedTextColor.GRAY)));
    }

    @EventHandler
    public void onMenu(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
        ItemStack it = e.getCurrentItem();
        String soort = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (soort == null) return;
        Sessie ss = sessie(p);
        if (menu.naam) {
            if (soort.startsWith("naam:")) {
                String naam = ZeldaDier.NAMEN.get(Integer.parseInt(soort.substring(5)));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    kiesNaam(p, naam);
                    p.closeInventory();
                });
            }
            return;
        }
        boolean rechts = e.getClick().isRightClick();
        if (soort.equals("hub")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                plugin.naarHub(p);
            });
        } else if (soort.equals("uitleg")) {
            ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
            if (!s.wis("o:uitleg-uit")) s.zet("o:uitleg-uit");
            voortgang.vuil();
            p.playSound(p, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
            Bukkit.getScheduler().runTask(plugin, () -> kaart(p));
        } else if (soort.startsWith("rust:") && ss != null) {
            String[] d = soort.split(":");
            ZeldaHoofdstuk h = deel(d[1]);
            int i = Integer.parseInt(d[2]);
            if (h == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                if (rechts) opnieuw(p, h, i);
                else reis(p, h, i);
            });
        } else if (soort.startsWith("eiland:") && ss != null) {
            ZeldaEiland naar = eiland(soort.substring(7));
            if (naar == null || naar == ss.h.eiland || !naar.open(voortgang.van(p.getUniqueId()))) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                vaar(p, naar);
            });
        }
    }

    @EventHandler
    public void onMenuSleep(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onMenuDicht(InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu m && m.naam && e.getPlayer() instanceof Player p)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && voortgang.van(p.getUniqueId()).dier == null && ZeldaDier.soort(voortgang.van(p.getUniqueId())) != null)
                    kiesNaam(p, ZeldaDier.NAMEN.get(0));
            });
    }

    // ── het flesje (voorwerp in vak 3) ──────────────────────────────────────────────────────

    /** Flesjes met bronwater: rechtsklik = drinken (vijf harten). Vullen bij een bron of zielvuur. */
    final class Flesje implements ZeldaVoorwerp {
        public String id() { return "fles"; }
        public String vlag() { return "fles1"; }
        public int vak() { return 2; }

        public ItemStack item(ZeldaSpel spel, Player p, ZeldaVoortgang.Stand s) {
            int n = ZeldaRegels.flessen(s), v = ZeldaRegels.vol(s);
            ItemStack it;
            if (v > 0) {
                it = ding(Material.POTION, "Flesje met bronwater", "fles");
                if (it.getItemMeta() instanceof PotionMeta pm) {
                    pm.setColor(Color.fromRGB(0xf7a8d8));
                    pm.setMaxStackSize(8);
                    pm.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    it.setItemMeta(pm);
                }
                it.setAmount(v);
                lore(it, "rechtsklik: drinken", v + " van " + n + " vol");
            } else {
                it = ding(Material.GLASS_BOTTLE, "Leeg flesje", "fles");
                it.setAmount(Math.max(1, n));
                lore(it, "vullen bij een bron of zielvuur");
            }
            return it;
        }

        public void gebruik(ZeldaSpel spel, Player p, PlayerInteractEvent e) {
            ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            if (max == null || p.getHealth() >= max.getValue()) return;
            if (!ZeldaRegels.drink(s)) {
                p.playSound(p, Sound.ITEM_BOTTLE_EMPTY, 0.4f, 1.2f);
                return;
            }
            voortgang.vuil();
            geneesBij(p, ZeldaRegels.FLES_HP);
            p.playSound(p, Sound.ENTITY_GENERIC_DRINK, 0.7f, 1.1f);
            p.spawnParticle(Particle.HEART, p.getLocation().add(0, 1.4, 0), 5, 0.4, 0.3, 0.4, 0);
            vernieuw(p, s, this);
        }
    }

    // ── de bommenzak (voorwerp in vak 4, eiland 2) ──────────────────────────────────────────

    /**
     * De bommenzak: rechtsklik = een bom gooien (een tel wachten tussen twee bommen). Waar hij neerkomt, sist hij even en
     * dan ontploft hij ({@link #ontplof}): gebarsten muren breken open, vijanden vlakbij krijgen een flinke klap. Blokken
     * gaan nooit echt kapot. De bommen raken nooit op.
     */
    final class Bommen implements ZeldaVoorwerp {
        static final Material ITEM = Material.FIREWORK_STAR;
        static final int LONT = 30;                          // ticks tussen neerkomen en ontploffen

        public String id() { return "bommen"; }
        public String vlag() { return "bommen"; }
        public int vak() { return 3; }

        public ItemStack item(ZeldaSpel spel, Player p, ZeldaVoortgang.Stand s) {
            ItemStack it = ding(ITEM, "Bom", "bommen");
            lore(it, "rechtsklik: gooien", "breekt gebarsten muren");
            return it;
        }

        public void gebruik(ZeldaSpel spel, Player p, PlayerInteractEvent e) {
            if (p.hasCooldown(ITEM)) return;
            p.setCooldown(ITEM, 20);
            var v = p.getEyeLocation().getDirection().multiply(0.85).add(new org.bukkit.util.Vector(0, 0.18, 0));
            org.bukkit.entity.Snowball bal = p.launchProjectile(org.bukkit.entity.Snowball.class, v);
            bal.setItem(new ItemStack(ITEM));
            bal.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "v:" + id());
            bal.addScoreboardTag(TAG);
            p.playSound(p, Sound.ENTITY_SNOWBALL_THROW, 0.6f, 0.7f);
        }

        public void raak(ZeldaSpel spel, Player p, ProjectileHitEvent e) {
            Location l;
            if (e.getHitBlock() != null) {
                Block voor = e.getHitBlockFace() == null ? e.getHitBlock() : e.getHitBlock().getRelative(e.getHitBlockFace());
                l = voor.getLocation().add(0.5, 0.1, 0.5);
            } else l = e.getEntity().getLocation();
            e.getEntity().remove();
            lont(p, l);
        }
    }

    /** Een bom ligt te sissen op {@code l} en ontploft na de lont. */
    void lont(Player p, Location l) {
        var w = l.getWorld();
        ItemDisplay bom = w.spawn(l.clone().add(0, 0.25, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setItemStack(new ItemStack(Bommen.ITEM));
            d.setBrightness(new Display.Brightness(15, 15));
        });
        w.playSound(l, Sound.ENTITY_TNT_PRIMED, 0.6f, 1.3f);
        for (int t = 0; t < Bommen.LONT; t += 5) {
            int tt = t;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (bom.isValid()) w.spawnParticle(Particle.SMOKE, bom.getLocation().add(0, 0.35, 0), 3, 0.05, 0.05, 0.05, 0.01);
                if (tt >= Bommen.LONT - 10 && bom.isValid()) w.spawnParticle(Particle.FLAME, bom.getLocation().add(0, 0.35, 0), 2, 0.05, 0.05, 0.05, 0.01);
            }, t);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            bom.remove();
            if (p.isOnline() && speelt(p)) ontplof(p, l);
        }, Bommen.LONT);
    }

    /**
     * Een bom ontploft (zonder echte schade aan de wereld): knal, gebarsten muur vlakbij open ({@link #breek}), vijanden
     * binnen 3,5 blok een klap, en het deel mag zelf nog iets doen ({@link ZeldaHoofdstuk#boem}).
     */
    void ontplof(Player p, Location l) {
        var w = l.getWorld();
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);
        w.spawnParticle(Particle.EXPLOSION, l.clone().add(0, 0.5, 0), 3, 0.6, 0.4, 0.6, 0);
        w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 0.5, 0), 12, 0.6, 0.4, 0.6, 0.02);
        for (Entity e : w.getNearbyEntities(l, 3.5, 3.5, 3.5))
            if (isVijand(e) && !e.getScoreboardTags().contains(NPC) && e instanceof LivingEntity le && !le.isDead()) {
                le.damage(12, p);
                var weg = le.getLocation().toVector().subtract(l.toVector()).setY(0);
                if (weg.lengthSquared() > 0.01) le.setVelocity(weg.normalize().multiply(0.6).setY(0.35));
            }
        ZeldaHoofdstuk.Barst b = barstBij(l);
        if (b != null) breek(p, b);
        for (ZeldaHoofdstuk h : hoofdstukken) if (h.speelgebied != null && h.speelgebied.in(l)) h.boem(this, p, l);
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
        // eerst wat je aanklikt (bel, spiegel, vuurschaal): dat gaat voor wat je vasthoudt (flesje, bom, kaart)
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getHand() == EquipmentSlot.HAND) {
            Block b = e.getClickedBlock();
            ZeldaEiland hier = sessie(p).h.eiland;
            if (b.getType() == Material.BELL && hier != null)          // de bel van een kamer: nog eens vechten
                for (ZeldaHoofdstuk.Kamer k : alle(hier, h -> h.kamers))
                    if (k.bel != null && k.bel[0] == b.getX() && k.bel[1] == b.getY() && k.bel[2] == b.getZ()) {
                        e.setCancelled(true);
                        luid(p, k);
                        return;
                    }
            if (b.getType() == ZeldaHoofdstuk.Spiegels.BLOK && hier != null)  // een spiegel: een kwartslag draaien
                for (ZeldaHoofdstuk.Spiegels sp : alle(hier, h -> h.spiegels)) {
                    int i = sp.index(b.getX(), b.getY(), b.getZ());
                    if (i >= 0) {
                        e.setCancelled(true);
                        draai(p, sp, i);
                        return;
                    }
                }
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
        ItemStack it = e.getItem();
        String soort = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if ("kaart".equals(soort)) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) kaart(p);
            return;
        }
        if (soort != null && soort.startsWith("v:")) {             // een voorwerp (flesje, …)
            e.setCancelled(true);
            ZeldaVoorwerp v = voorwerp(soort.substring(2));
            if (v != null && e.getHand() == EquipmentSlot.HAND && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK))
                v.gebruik(this, p, e);
            return;
        }
    }

    @EventHandler
    public void onPijl(ProjectileHitEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p) || !speelt(p)) return;
        String vk = e.getEntity().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (vk != null && vk.startsWith("v:") && voorwerp(vk.substring(2)) != null) {
            voorwerp(vk.substring(2)).raak(this, p, e);
            return;
        }
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
        Entity wie = e.getRightClicked();
        if (!wie.getScoreboardTags().contains(NPC)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Sessie ss = sessie(p);
        if (ss == null || e.getHand() != EquipmentSlot.HAND) return;
        if (metgezel.isFee(wie)) {                                   // klik op Wiske = hint
            if (p.getUniqueId().equals(metgezel.eigenaar(wie))) hint(p);
            return;
        }
        if (dier.isDier(wie)) {                                      // aaien
            p.spawnParticle(Particle.HEART, wie.getLocation().add(0, 0.8, 0), 2, 0.2, 0.2, 0.2, 0);
            return;
        }
        List<ZeldaHoofdstuk.Persoon> personen = new ArrayList<>(zee.personen);
        if (ss.h.eiland != null) personen.addAll(alle(ss.h.eiland, h -> h.personen));
        for (ZeldaHoofdstuk.Persoon pe : personen)
            if (wie.equals(pe.ent)) {
                praat(p, pe);
                return;
            }
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

    /** Wiske een tik geven (linksklik) is ook om hulp vragen. NPC-schade is al geannuleerd. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTikWiske(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && metgezel.isFee(e.getEntity()) && p.getUniqueId().equals(metgezel.eigenaar(e.getEntity()))) hint(p);
    }

    /** Beheer: onkwetsbaar (ook vallen, lava, vijanden). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBeheerSchade(EntityDamageEvent e) {
        if (beheer.is(e.getEntity())) e.setCancelled(true);
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
        Sessie ss = sessie(p);
        for (ZeldaHoofdstuk.Uitdaging u : ss.h.uitdagingen) u.loopt.remove(p.getUniqueId());
        for (ZeldaHoofdstuk h : hoofdstukken)                        // doodgegaan in een kamer: straks een tip
            for (ZeldaHoofdstuk.Kamer k : h.kamers)
                if (k.fase == ZeldaHoofdstuk.Fase.BEZIG && k.binnen.in(p.getLocation()) && !k.tips.isEmpty()) {
                    ss.doden.merge(k.id, 1, Integer::sum);
                    ss.tip = h.id() + ":" + k.id;
                }
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
            ss.h = ss.rh;
            ss.laatsteHemel = -1;
            hemel(p, ss, p.getLocation());
            p.playSound(p, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.4f, 1.4f);
            if (ss.tip != null) {                                    // een tip van Wiske voor de kamer waar je viel
                String[] t = ss.tip.split(":", 2);
                ss.tip = null;
                ZeldaHoofdstuk h = deel(t[0]);
                ZeldaHoofdstuk.Kamer k = null;
                if (h != null) for (ZeldaHoofdstuk.Kamer x : h.kamers) if (x.id.equals(t[1])) k = x;
                if (k != null) {
                    int n = ss.doden.getOrDefault(k.id, 1);
                    if (n <= k.tips.size()) vertel(p, k.tips.get(n - 1));
                }
            }
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
        if (e.getTarget().getScoreboardTags().contains(NPC) || beheer.is(e.getTarget())) e.setCancelled(true);
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
        Player p = e.getPlayer();                            // en nooit nog vliegend of onkwetsbaar uit beheer
        if (p.getGameMode() == org.bukkit.GameMode.ADVENTURE || p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
            p.setFlying(false);
            p.setAllowFlight(false);
            p.setInvulnerable(false);
        }
    }

    // ── voor de zelftest ────────────────────────────────────────────────────────────────────

    int vijanden() {
        int n = 0;
        for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Kamer k : h.kamers) n += k.vijanden.size();
        return n;
    }
}
