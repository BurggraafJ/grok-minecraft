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

    static final String TAG = "zelda", NPC = "zelda-npc", IJS = "zelda-ijs";
    /** Een pijl van licht (eiland 5a: wie de lichtpijlen heeft, schiet licht), en een vijand van Morgals nevel (licht doet daar meer). */
    static final String LICHT = "zelda-licht", NEVEL = "zelda-nevel";
    static final TextColor GROEN = TextColor.color(0x8fcf6a);
    /** Na zo lang zonder voortgang biedt Wiske één keer hulp aan (en dan pas weer na {@link #AANBOD_OPNIEUW}). */
    static final long VAST_MS = 6 * 60_000L, AANBOD_OPNIEUW = 10 * 60_000L;
    /** Op adem komen: zo lang na de laatste klap (en niet in een gevecht) geneest elke {@link #BIJKOMEN_ELK} ms een half hart. */
    static final long BIJKOMEN_NA = 6_000L, BIJKOMEN_ELK = 2_000L;

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
        long geraakt, bijkomen;                              // laatste klap; volgende keer op adem komen
        String hintDoel, tip;
        int voortgangStand;
        boolean inBron, naamGevraagd;
        double hitte;                                        // hoe warm je bent in de mijn (0 … 1, de balk onderin; eiland 4a)
        Location koelplek;                                   // waar je in de hitte voor het laatst afkoelde
        long teHeet;
        boolean koelt;
        Location veilig;                                     // waar je het laatst veilig stond (geen lava vlakbij): daar spring je heen uit de lava
        long lavaTijd;
        boolean ongezien;                                    // de wachters zien je niet (alleen de test: hij teleporteert)
        long gezienTot;                                      // net gezien: even niet nog eens
        int opgevangen;                                      // hoe vaak de nevel je opving (een vangnet, 5b; de test kijkt ernaar)
        final Map<String, Integer> gezien = new HashMap<>(); // sluipstuk → keer gezien (voor wat Wiske zegt)
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
        metgezel.licht = p -> voortgang == null ? 0 : voortgang.van(p.getUniqueId()).aantal("baken");   // Wiske wordt feller bij elk baken
        this.itemKey = new NamespacedKey(plugin, "zelda");
        this.hartKey = new NamespacedKey(plugin, "zelda-harten");
        voorwerpen.add(new Flesje());
        voorwerpen.add(new Bommen());
        voorwerpen.add(new Klauwhaak());
        voorwerpen.add(new Vuurstaf());
        voorwerpen.add(new Lichtpijlen());
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
            for (ZeldaHoofdstuk.Sluip sl : h.sluipen) for (ZeldaHoofdstuk.Sluip.Wachter wa : sl.wachters) if (wa.ent != null) wa.ent.remove();
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
        muziek.achter(p, h.muziek(l, s));
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
        trekken.remove(p.getUniqueId());
        vlammen.removeIf(v -> v.wie.equals(p.getUniqueId()));
        glijders.remove(p.getUniqueId());
        glijVorige.remove(p.getUniqueId());
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
            ItemStack boog = ding(Material.BOW, s.heeft("lichtpijlen") ? "Lichtboog" : "Jagersboog", "boog");
            boog.addUnsafeEnchantment(Enchantment.INFINITY, 1);
            if (s.heeft("lichtpijlen")) lore(boog, "schiet pijlen van licht");
            inv.setItem(1, boog);
            inv.setItem(17, ding(Material.ARROW, "Pijl", "pijl"));
        }
        if (s.heeft(GLOEDSCHILD)) inv.setItemInOffHand(gloedschild());
        else if (s.heeft("schild")) inv.setItemInOffHand(ding(Material.SHIELD, "Eikenschild", "schild"));
        for (ZeldaVoorwerp v : voorwerpen) if (v.heeft(s)) vernieuw(p, s, v);
        inv.setItem(8, ding(Material.MAP, "Kaart", "kaart"));
        inv.setItem(Menus.HUB_VAK, plugin.hubItem());
        harnas(p, ZeldaRegels.harnas(s));
        if (ZeldaRegels.smidshelm(s, ZeldaRegels.harnas(s))) {
            p.getInventory().setHelmet(ding(Material.IRON_HELMET, "Smidshelm", "muts"));
            if (p.getInventory().getLeggings() == null) p.getInventory().setLeggings(ding(Material.LEATHER_LEGGINGS, "Broek", "broek"));
        }
        harten(p, ZeldaRegels.harten(s));
    }

    /** Het schild van de eerste smeden, door Okker gesmeed (eiland 4b): zwart met gloed; vuur ketst erop af (Asgrims sintels). */
    static final String GLOEDSCHILD = "gloedschild";

    private ItemStack gloedschild() {
        ItemStack it = ding(Material.SHIELD, "Gloedschild", "schild");
        if (it.getItemMeta() instanceof org.bukkit.inventory.meta.ShieldMeta sm) {
            sm.setBaseColor(org.bukkit.DyeColor.BLACK);
            sm.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.ORANGE, org.bukkit.block.banner.PatternType.GRADIENT_UP));
            sm.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.YELLOW, org.bukkit.block.banner.PatternType.CIRCLE));
            sm.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.BLACK, org.bukkit.block.banner.PatternType.BORDER));
            it.setItemMeta(sm);
        }
        lore(it, "vuur ketst erop af");
        return it;
    }

    /** Zet één voorwerp opnieuw in zijn vak (bv. na drinken). */
    void vernieuw(Player p, ZeldaVoortgang.Stand s, ZeldaVoorwerp v) {
        ItemStack it = v.heeft(s) ? v.item(this, p, s) : null;
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
                for (ZeldaHoofdstuk.Poort po : h.poorten) poort(po, nu);
                for (ZeldaHoofdstuk.Getij g : h.getijden) veilig("getij", () -> getij(g, nu));
                for (ZeldaHoofdstuk.Smelt sm : h.smelten) veilig("ijs", () -> smelt(sm, nu));
                for (ZeldaHoofdstuk.Ritme rt : h.ritmes) veilig("ritme", () -> ritme(rt, nu));
                for (ZeldaHoofdstuk.Nevel nv : h.nevels) veilig("nevel", () -> nevel(nv, nu));
            }
        }
        if (tikken % 2 == 0) for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Sluip sl : h.sluipen) veilig("sluipen", () -> sluip(sl, nu));
        if (!lichtpijlen.isEmpty()) veilig("lichtpijlen", this::lichtpijlen);
        if (tikken % 10 == 5) for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Spiegels sp : h.spiegels) veilig("spiegels", () -> spiegels(sp, nu));
        if (tikken % 10 == 0) {
            verteller(nu);
            for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Kamer k : h.kamers) kamer(k, nu);
            Map<Player, ZeldaVoortgang.Stand> wie = new HashMap<>();
            for (Player p : spelers) wie.put(p, voortgang.van(p.getUniqueId()));
            veilig("huisdier", () -> dier.tik(wie, neus));
            veilig("bronnen", this::bronnen);
            for (Player p : spelers) veilig("hitte", () -> hitte(p, sessie(p), nu));
        }
        if (!trekken.isEmpty()) veilig("klauwhaak", this::trekken);
        if (!vlammen.isEmpty()) veilig("vuurstaf", this::vlammen);
        veilig("glijden", () -> glijden(spelers));
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
                muziek.achter(p, hier.muziek(l, voortgang.van(p.getUniqueId())));
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
        } else if (nu - ss.geraakt > BIJKOMEN_NA && nu >= ss.bijkomen && !inGevecht(p)) {   // buiten een gevecht kom je vanzelf op adem
            ss.bijkomen = nu + BIJKOMEN_ELK;
            geneesBij(p, 1);
        }
        if (bron && !ss.inBron && nu > ss.bronDeuntje) {
            ss.bronDeuntje = nu + 30_000L;
            muziek.speel(p, ZeldaMuziek.BRON);
            p.spawnParticle(Particle.HEART, p.getLocation().add(0, 1.2, 0), 4, 0.4, 0.4, 0.4, 0);
        }
        ss.inBron = bron;
        muziek.achter(p, ss.h.muziek(l, s));
        // een dier dat meeloopt maar nog naamloos is: even vragen hoe hij heet
        if (ZeldaDier.actief(ZeldaDier.soort(s)) && s.dier == null && !ss.naamGevraagd && ss.rij.isEmpty() && ss.nu == null && !inGevecht(p)) {
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

    /** Mag de klauwhaak in het gevecht waar deze speler in staat (een baas die je wegtrekt: {@code Kamer.haken})? */
    boolean haakMag(Player p) {
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Kamer k : h.kamers) if (k.fase == ZeldaHoofdstuk.Fase.BEZIG && k.binnen.in(p.getLocation())) return k.haken;
        return true;
    }

    /** Staat deze speler in een kamer waar gevochten wordt? */
    boolean inGevecht(Player p) {
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Kamer k : h.kamers) if (k.fase == ZeldaHoofdstuk.Fase.BEZIG && k.binnen.in(p.getLocation())) return true;
        return false;
    }

    private void hemel(Player p, Sessie ss, Location l) {
        long t = ss.vaart != null ? zee.hemel(l) : ss.h.hemel(l, voortgang.van(p.getUniqueId()));
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
        if (veiligeGrond(p)) ss.veilig = l.clone();
        for (ZeldaHoofdstuk h : ss.h.eiland.delen())                 // een vangnet: wie valt, staat weer waar hij veilig stond
            for (ZeldaHoofdstuk.Val v : h.vallen)
                if (v.gebied().in(l) && (p.getFallDistance() > 3.5f && !((org.bukkit.entity.Entity) p).isOnGround() || l.getY() < v.laag())) {
                    opvangen(p, ss, v);
                    return;
                }
        for (ZeldaHoofdstuk h : ss.h.eiland.delen()) {
            for (ZeldaHoofdstuk.Rust r : h.rust) if (r.zone.in(l)) rust(p, ss, s, h, r);
            for (ZeldaHoofdstuk.Verhaal v : h.verhalen)
                if (v.zone.in(l) && !s.heeft("v:" + v.id) && (v.vereist == null || s.heeft(v.vereist)) && (v.als == null || v.als.test(s))) verhaal(p, h, s, v);
            for (ZeldaHoofdstuk.Vondst v : h.vondsten)
                if (v.zone.in(l) && !s.heeft(v.vlag) && (v.vereist == null || s.heeft(v.vereist)) && (v.voorwerp == null || s.heeft(v.voorwerp))) vind(p, s, v);
            for (ZeldaHoofdstuk.Uitdaging u : h.uitdagingen) {
                Long start = u.loopt.get(p.getUniqueId());
                if (u.start.in(l)) {
                    if (start == null || nu - start > 600) begin(p, u, nu);
                } else if (start != null && u.via != null && u.via.in(l)) u.erdoor.add(p.getUniqueId());
                else if (start != null && u.finish.in(l) && (u.via == null || u.erdoor.contains(p.getUniqueId()))) gehaald(p, s, u, nu - start);
            }
            for (ZeldaHoofdstuk.Kamer k : h.kamers) {
                if (k.alleenBel) continue;                              // een oefenplek: alleen de bel begint het gevecht
                boolean won = s.heeft("k:" + k.id);
                if (k.fase == ZeldaHoofdstuk.Fase.RUST && k.start.in(l)) binnenKamer(k, nu);
                else if (k.fase == ZeldaHoofdstuk.Fase.RUST && won && k.uit.omvang().groter(2).in(l)) doorgang(k, List.of(p));   // van de andere kant terug
                else if (k.fase == ZeldaHoofdstuk.Fase.GEWONNEN && !won && k.start.in(l)) begin(k, nu, false);              // nieuw erbij: gewoon vechten
            }
        }
    }

    /** De nevel vangt je op (een vangnet): terug naar waar je het laatst veilig stond, zonder schade; de eerste keer zegt Wiske iets. */
    private void opvangen(Player p, Sessie ss, ZeldaHoofdstuk.Val v) {
        Location naar = ss.veilig != null && ss.veilig.getWorld().equals(p.getWorld()) && ss.veilig.distanceSquared(p.getLocation()) < 60 * 60
                && ss.veilig.getY() >= v.laag() ? ss.veilig : ss.rustplaats().plek;
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 0.6, 0), 12, 0.4, 0.4, 0.4, 0.01);
        p.setFallDistance(0);
        p.setVelocity(new org.bukkit.util.Vector());
        p.teleport(naar.clone().setDirection(p.getLocation().getDirection()));
        p.getWorld().spawnParticle(Particle.END_ROD, naar.clone().add(0, 1, 0), 16, 0.3, 0.6, 0.3, 0.02);
        p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.4f);
        ss.opgevangen++;
        ZeldaVoortgang.Stand st = voortgang.van(p.getUniqueId());
        if (st.zet("v:val")) {
            voortgang.vuil();
            vertel(p, ZeldaHoofdstuk.r("Wiske", "Oei! De nevel ving je op."));
        }
    }

    /** In een vangnet doet vallen geen pijn (ook niet als je eerder landt dan de nevel je opvangt). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVal(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p) || !speelt(p)) return;
        Sessie ss = sessie(p);
        if (ss == null || ss.h.eiland == null) return;
        for (ZeldaHoofdstuk h : ss.h.eiland.delen())
            for (ZeldaHoofdstuk.Val v : h.vallen) if (v.gebied().in(p.getLocation())) e.setCancelled(true);
    }

    /** Een beetje beter (een baas geeft je even adem tussen twee fases): {@code hp} halve harten erbij, met hartjes. */
    void bijkomen(Player p, double hp) {
        geneesBij(p, hp);
        p.spawnParticle(Particle.HEART, p.getLocation().add(0, 1.4, 0), 3, 0.4, 0.3, 0.4, 0);
    }

    private void rust(Player p, Sessie ss, ZeldaVoortgang.Stand s, ZeldaHoofdstuk h, ZeldaHoofdstuk.Rust r) {
        ss.rh = h;
        ss.rust = r.index;
        boolean nieuw = false;
        boolean helm = !s.heeft(ZeldaRegels.HELM);
        for (String v : r.geeft) nieuw |= s.zet(v);
        if (helm && s.heeft(ZeldaRegels.HELM)) vertel(p, r.id.equals("dorp") ? ZeldaHoofdstuk.r("Kees", "Hier, een helm. Tegen heksen en soldaten.")
                : ZeldaHoofdstuk.r("Wiske", "Een helm van Kees. Tegen heksen en soldaten."));
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
        BlockData ander = open ? d.dicht : d.open;                  // lucht of water: de stukjes van het andere blok (ijs dat smelt)
        BlockData stukjes = data.getMaterial().isSolid() ? data : ander.getMaterial().isSolid() ? ander : Material.STONE.createBlockData();
        w.spawnParticle(Particle.BLOCK, ml, 30, 0.5, 1, 1, stukjes);
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

    /**
     * De bel in een kamer: nog eens vechten, nu met de extra golven. In een oefenplek ({@code alleenBel}) begint de bel het
     * gevecht; de extra golven komen pas als iedereen binnen hem al eens won.
     */
    void luid(Player p, ZeldaHoofdstuk.Kamer k) {
        if (k.fase == ZeldaHoofdstuk.Fase.BEZIG || k.bel == null || !k.binnen.in(p.getLocation())) return;
        p.getWorld().playSound(new Location(p.getWorld(), k.bel[0] + 0.5, k.bel[1] + 0.5, k.bel[2] + 0.5), Sound.BLOCK_BELL_USE, 1f, 0.9f);
        boolean terug = true;
        if (k.alleenBel) {
            List<ZeldaVoortgang.Stand> wie = new ArrayList<>();
            for (Player q : in(k.binnen)) wie.add(voortgang.van(q.getUniqueId()));
            terug = ZeldaRegels.doorgang(wie, k.id);
        }
        begin(k, System.currentTimeMillis(), terug);
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
        for (int i = 1; i < spelers && soorten.size() < 8; i++) {             // per extra speler één erbij (de baas roept een helper)
            ZeldaHoofdstuk.Soort s = soorten.get((i - 1) % soorten.size());
            soorten.add(k.baas != null && s == k.baas.soort ? k.baas.helper : s);
        }
        List<Location> plekken = new ArrayList<>(k.spawns);
        java.util.Collections.shuffle(plekken, rnd);
        int i = 0;
        for (ZeldaHoofdstuk.Soort s : soorten) {
            boolean baas = k.baas != null && s == k.baas.soort;
            Location l = baas ? k.baas.grond.get(0) : plekken.get(i++ % plekken.size());
            LivingEntity e = spawn(s, l, spelers);
            k.vijanden.add(e);
            if (baas) {
                double hp = (k.baas.leven + k.baas.levenPerSpeler * (spelers - 1)) * ZeldaRegels.BAAS_LEVEN;   // balans: langer
                zetAttr(e, Attribute.MAX_HEALTH, hp);
                e.setHealth(hp);
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
                k.vijanden.add(spawn(k.baas.helper, k.spawns.get(rnd.nextInt(k.spawns.size())), in.size()));
        }
        if (!k.vijanden.isEmpty()) return;
        if (k.golf + 1 < ZeldaRegels.golven(k)) {
            if (k.volgendeGolf == 0) {
                k.volgendeGolf = nu + ZeldaRegels.GOLF_PAUZE;
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
        deur(k.uit, k.alleenBel, k.start.midden(plugin.w).add(0, 1, 0));
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
            case ROVER, MATROOS -> org.bukkit.entity.Zombie.class;     // geen drowned: die valt overdag niemand op het droge aan
            case ROVERSCHUTTER -> org.bukkit.entity.Skeleton.class;
            case HARPOENIER, KAPITEIN -> org.bukkit.entity.Drowned.class;   // met een drietand: gooien doet hij altijd
            case NEVELWOLF -> org.bukkit.entity.Wolf.class;
            case IJSKNECHT, IJSKOBOLD -> org.bukkit.entity.Zombie.class;
            case VORSTSCHUTTER -> org.bukkit.entity.Stray.class;
            case GRIMMEL -> org.bukkit.entity.PolarBear.class;
            case ASKNECHT -> org.bukkit.entity.Zombie.class;
            case VONKSCHUTTER -> org.bukkit.entity.Skeleton.class;
            case SINTELMIJT -> org.bukkit.entity.MagmaCube.class;
            case ASGRIM -> org.bukkit.entity.Blaze.class;
            case NACHTSOLDAAT, WACHTMEESTER -> org.bukkit.entity.Vindicator.class;
            case NACHTSCHUTTER -> org.bukkit.entity.Pillager.class;
            case NEVELMIJT -> org.bukkit.entity.Endermite.class;
            case NEVELGEEST -> org.bukkit.entity.Vex.class;
            case MORGAL -> org.bukkit.entity.Evoker.class;
        };
        w.spawnParticle(Particle.SOUL, l.clone().add(0, 0.5, 0), 20, 0.4, 0.2, 0.4, 0.03);
        w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 0.8, 0), 10, 0.3, 0.5, 0.3, 0.01);
        w.playSound(l, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.5f, 1.3f);
        return w.spawn(l, klasse, m -> {
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            m.addScoreboardTag(TAG);
            if (s == ZeldaHoofdstuk.Soort.IJSKNECHT || s == ZeldaHoofdstuk.Soort.IJSKOBOLD || s == ZeldaHoofdstuk.Soort.VORSTSCHUTTER || s == ZeldaHoofdstuk.Soort.GRIMMEL)
                m.addScoreboardTag(IJS);                             // de vuurstaf doet hier meer
            if (s == ZeldaHoofdstuk.Soort.NEVELMIJT || s == ZeldaHoofdstuk.Soort.NEVELGEEST || s == ZeldaHoofdstuk.Soort.MORGAL)
                m.addScoreboardTag(NEVEL);                           // een lichtpijl doet hier meer
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
                case MATROOS -> {                                    // verdronken matroos: zeewier en een roestig zwaard
                    hp = 16;
                    dmg = 3;
                    eq.setHelmet(new ItemStack(Material.TURTLE_HELMET));
                    eq.setChestplate(gekleurd(Material.LEATHER_CHESTPLATE, 0x2f6f6a));
                    eq.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                }
                case HARPOENIER -> {                                 // gooit drietanden (die doen niet te veel pijn, zie onSchade)
                    hp = 14;
                    eq.setHelmet(gekleurd(Material.LEATHER_HELMET, 0x2f6f6a));
                    eq.setItemInMainHand(new ItemStack(Material.TRIDENT));
                }
                case NEVELWOLF -> {                                  // een wolf in de ban van de nevel (Witte Toppen): rode ogen, snel, niet taai
                    hp = 12;
                    dmg = 3;
                    if (m instanceof org.bukkit.entity.Wolf wo) {
                        wo.setVariant(org.bukkit.entity.Wolf.Variant.SNOWY);
                        wo.setAngry(true);
                    }
                }
                case IJSKNECHT -> {                                  // een ijsknecht van het IJspaleis (Witte Toppen): ijzig blauw, een schop van steen
                    hp = 18;
                    dmg = 3;
                    eq.setHelmet(new ItemStack(Material.PACKED_ICE));
                    eq.setChestplate(gekleurd(Material.LEATHER_CHESTPLATE, 0x9ed4f0));
                    eq.setLeggings(gekleurd(Material.LEATHER_LEGGINGS, 0x6fa8c8));
                    eq.setItemInMainHand(new ItemStack(Material.STONE_SHOVEL));
                }
                case VORSTSCHUTTER -> {                              // schiet met een boog (zijn pijlen maken je niet traag, zie onDrank)
                    hp = 14;
                    eq.setHelmet(gekleurd(Material.LEATHER_HELMET, 0xd8eef8));
                    eq.setItemInMainHand(new ItemStack(Material.BOW));
                }
                case IJSKOBOLD -> {                                  // klein en snel, niet taai
                    hp = 8;
                    dmg = 2;
                    eq.setHelmet(gekleurd(Material.LEATHER_HELMET, 0x9ed4f0));
                    eq.setItemInMainHand(new ItemStack(Material.ICE));
                }
                case GRIMMEL -> {                                    // Grimmel, de reuzenijsbeer: groot, sjokt, stormt (zie Grimmel)
                    hp = 60;
                    dmg = 5;
                    schaal = 2.0;
                    zetAttr(m, Attribute.KNOCKBACK_RESISTANCE, 1.0);
                    zetAttr(m, Attribute.MOVEMENT_SPEED, 0.2);
                }
                case ASKNECHT -> {                                   // een asknecht van de Gloedberg (4a): zwart als roet, een houweel
                    hp = 18;
                    dmg = 3;
                    eq.setHelmet(new ItemStack(Material.BLACKSTONE));
                    eq.setChestplate(gekleurd(Material.LEATHER_CHESTPLATE, 0x3b3633));
                    eq.setLeggings(gekleurd(Material.LEATHER_LEGGINGS, 0x2a2624));
                    eq.setItemInMainHand(new ItemStack(Material.STONE_PICKAXE));
                }
                case VONKSCHUTTER -> {                               // schiet van een afstand, een oranje kap
                    hp = 14;
                    eq.setHelmet(gekleurd(Material.LEATHER_HELMET, 0xd9682b));
                    eq.setItemInMainHand(new ItemStack(Material.BOW));
                }
                case SINTELMIJT -> {                                 // een klein gloeiend klodder: snel, niet taai (splitst niet)
                    if (m instanceof org.bukkit.entity.MagmaCube mc) mc.setSize(1);
                    hp = 6;
                    dmg = 2;
                }
                case ASGRIM -> {                                     // Asgrim de Gloedwachter (4b): een groot vlammenwezen, zweeft (zie Asgrim)
                    hp = 70;
                    schaal = Asgrim.SCHAAL;
                    zetAttr(m, Attribute.KNOCKBACK_RESISTANCE, 1.0);
                }
                case NACHTSOLDAAT -> {                               // een soldaat van Morgal (Nachtrots, 5a): een bijl van steen
                    hp = 20;
                    dmg = 3.5;
                    eq.setItemInMainHand(new ItemStack(Material.STONE_AXE));
                }
                case WACHTMEESTER -> {                               // de wachtmeester van de binnenplaats (alleen met de bel): groter, taaier
                    hp = 30;
                    dmg = 4.5;
                    schaal = 1.25;
                    eq.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                    zetAttr(m, Attribute.KNOCKBACK_RESISTANCE, 0.4);
                }
                case NACHTSCHUTTER -> hp = 16;                       // schiet met een kruisboog, van een afstand
                case NEVELMIJT -> {                                  // een mijt uit Morgals nevel: klein, snel, niet taai
                    hp = 6;
                    dmg = 2;
                }
                case NEVELGEEST -> {                                 // een geest uit Morgals nevel (5b): vliegt, niet taai, licht doet dubbel
                    hp = 8;
                    dmg = 3;
                    if (m instanceof org.bukkit.entity.Vex v) v.setLimitedLifetime(false);
                    zetAttr(m, Attribute.FLYING_SPEED, 0.5);
                }
                case MORGAL -> {                                     // Morgal, de Nachtvorst (5b): groot, zweeft, gooit duistere bollen (zie Morgal)
                    hp = 80;
                    schaal = Morgal.SCHAAL;
                    zetAttr(m, Attribute.KNOCKBACK_RESISTANCE, 1.0);
                }
                case KAPITEIN -> {                                   // Kapitein Zoutbaard: groot, staat bij zijn ton, gooit drietanden
                    hp = 60;
                    schaal = 1.6;
                    eq.setHelmet(gekleurd(Material.LEATHER_HELMET, 0x1d1d21));
                    eq.setChestplate(gekleurd(Material.LEATHER_CHESTPLATE, 0x7a2a22));
                    eq.setItemInMainHand(new ItemStack(Material.TRIDENT));
                    zetAttr(m, Attribute.KNOCKBACK_RESISTANCE, 1.0);
                    zetAttr(m, Attribute.MOVEMENT_SPEED, 0.0);
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
                if (s == ZeldaHoofdstuk.Soort.IJSKOBOLD) z.setBaby();
                else z.setAdult();
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

    /** De kamer van deze baas (null = geen). */
    ZeldaHoofdstuk.Kamer kamerVan(ZeldaBaas b) {
        for (ZeldaHoofdstuk h : hoofdstukken) for (ZeldaHoofdstuk.Kamer k : h.kamers) if (k.baas == b) return k;
        return null;
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
        u.erdoor.remove(p.getUniqueId());
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
        boolean vuur = r.hoe != ZeldaHoofdstuk.Reeks.Hoe.PIJL;
        w.playSound(l, vuur ? Sound.ITEM_FIRECHARGE_USE : Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.9f + 0.15f * r.isAan.size());
        w.spawnParticle(vuur ? Particle.FLAME : Particle.HAPPY_VILLAGER, l, 20, 0.3, 0.3, 0.3, 0.02);
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
        List<Player> bij = new ArrayList<>(in);                 // ook wie bij de deur staat (aan de andere kant, terug)
        for (Player p : in(r.deur.omvang().groter(3))) if (!bij.contains(p)) bij.add(p);
        for (Player p : bij)                                 // wie hem al eens oploste, hoeft dat niet opnieuw
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
                p.playSound(p, r.hoe != ZeldaHoofdstuk.Reeks.Hoe.PIJL ? Sound.BLOCK_FIRE_EXTINGUISH : Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1f);
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
        if (licht(sp) && straal(sp, sp.stand).raak()) losOp(sp, false);
    }

    /** Brandt het licht van deze spiegelpuzzel (er is iemand met de zonnesteen in de zaal, of hij heeft geen voorwaarde)? */
    boolean licht(ZeldaHoofdstuk.Spiegels sp) {
        if (sp.licht == null) return true;
        for (Player q : in(sp.gebied)) if (sp.licht.test(voortgang.van(q.getUniqueId()))) return true;
        return false;
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
        if (bij.isEmpty() || (!sp.opgelost && !licht(sp))) return;
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

    /** Een poort voor wie iets heeft (bv. het lichtwoord): open als er iemand voor wie hij mag in het gebied staat. */
    private void poort(ZeldaHoofdstuk.Poort po, long nu) {
        if (po.deur.isOpen) {                                // een minuut niemand in de buurt: weer dicht
            if (!in(po.gebied.groter(6)).isEmpty()) po.leegSinds = 0;
            else if (po.leegSinds == 0) po.leegSinds = nu;
            else if (nu - po.leegSinds > 60_000L) herstel(po);
            return;
        }
        for (Player p : in(po.gebied))
            if (po.mag.test(voortgang.van(p.getUniqueId()))) {
                po.leegSinds = 0;
                Location m = po.deur.omvang().midden(plugin.w).add(0, 1, 0);
                deur(po.deur, true, m);
                m.getWorld().spawnParticle(Particle.END_ROD, m, 30, 0.6, 1.0, 1.5, 0.03);
                m.getWorld().playSound(m, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
                return;
            }
    }

    /** Poort weer dicht. */
    void herstel(ZeldaHoofdstuk.Poort po) {
        po.leegSinds = 0;
        deur(po.deur, false, po.gebied.midden(plugin.w).add(0, 1, 0));
    }

    // ── eb en vloed (eiland 2b) ─────────────────────────────────────────────────────────────

    /**
     * Aan een hendel trekken. Mag het ({@code mag}, bv. met de maanparel), dan gaat het getij om: het water stijgt of zakt
     * laag voor laag. Mag het niet, dan zit hij vast (en zegt Wiske waarom). Terwijl het water beweegt, doet de hendel niets.
     */
    void trek(Player p, ZeldaHoofdstuk.Getij g, ZeldaHoofdstuk.Getij.Hendel h) {
        Location l = new Location(p.getWorld(), h.x() + 0.5, h.y() + 0.5, h.z() + 0.5);
        if (g.bezig()) return;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        if (!h.mag().test(s)) {
            p.playSound(l, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 0.6f);
            Sessie ss = sessie(p);
            if (h.nee() != null && ss != null && ss.rij.isEmpty() && ss.nu == null) vertel(p, h.nee());
            return;
        }
        if (g.onthoud != null && s.zet(g.onthoud)) voortgang.vuil();
        p.getWorld().playSound(l, Sound.BLOCK_LEVER_CLICK, 1f, 0.7f);
        boolean naar = h.naar() != null ? h.naar() : !g.vloed;
        if (naar != g.vloed) zetGetij(g, naar, false);                   // staat het al zo (een hendel die maar één kant op gaat): niets
    }

    /**
     * Het getij naar {@code vloed} (true) of eb: eerst gaat dicht wat bij het nieuwe getij dicht hoort (bij vloed de sluizen,
     * bij eb de vloeddeuren), dan gaan de lagen om ({@code direct}: in één keer, anders een laag per halve seconde), en aan
     * het eind gaat open wat open hoort.
     */
    void zetGetij(ZeldaHoofdstuk.Getij g, boolean vloed, boolean direct) {
        var w = plugin.w;
        g.vloed = vloed;
        g.leegSinds = 0;
        for (ZeldaHoofdstuk.Getij.Hendel h : g.hendels) {                 // de hendels laten zien hoe het staat
            Block b = w.getBlockAt(h.x(), h.y(), h.z());
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Switch sw && sw.isPowered() != vloed) {
                sw.setPowered(vloed);
                b.setBlockData(sw, false);
            }
        }
        Location m = g.binnen != null ? g.binnen : g.gebied.midden(w).add(0, 1, 0);
        for (ZeldaHoofdstuk.Deur d : vloed ? g.sluizen : g.vloeddeuren) deur(d, false, m);
        if (direct) {
            for (ZeldaHoofdstuk.Deur l : g.lagen) laag(l, vloed);
            klaar(g);
            return;
        }
        g.laag = 0;
        g.volgende = 0;
        w.playSound(m, vloed ? Sound.AMBIENT_UNDERWATER_ENTER : Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.2f, 0.6f);
    }

    private void laag(ZeldaHoofdstuk.Deur l, boolean water) {
        l.isOpen = !water;
        BlockData d = water ? l.dicht : l.open;
        for (int[] c : l.blokken) plugin.w.getBlockAt(c[0], c[1], c[2]).setBlockData(d, false);
    }

    private void klaar(ZeldaHoofdstuk.Getij g) {
        g.laag = -1;
        Location m = g.binnen != null ? g.binnen : g.gebied.midden(plugin.w).add(0, 1, 0);
        for (ZeldaHoofdstuk.Deur d : g.vloed ? g.vloeddeuren : g.sluizen) deur(d, true, m);
    }

    /** Elke vijf tikken: de volgende laag, of (stil en een minuut niemand in de buurt) terug naar de rust. */
    private void getij(ZeldaHoofdstuk.Getij g, long nu) {
        if (g.bezig()) {
            if (nu < g.volgende) return;
            int[] volgorde = ZeldaRegels.getijVolgorde(g.lagen.size(), g.vloed);
            ZeldaHoofdstuk.Deur l = g.lagen.get(volgorde[g.laag]);
            laag(l, g.vloed);
            Location m = l.omvang().midden(plugin.w).add(0, 1, 0);
            plugin.w.playSound(m, g.vloed ? Sound.BLOCK_WATER_AMBIENT : Sound.ITEM_BUCKET_FILL, 1.2f, g.vloed ? 0.8f : 0.6f);
            plugin.w.spawnParticle(g.vloed ? Particle.SPLASH : Particle.BUBBLE_POP, m, 40, 6, 0.3, 6, 0.1);
            for (Player p : in(g.gebied)) p.setFallDistance(0);
            g.laag++;
            g.volgende = nu + 450;
            if (g.laag >= g.lagen.size()) klaar(g);
            return;
        }
        if (g.vloed == g.rustVloed) return;
        if (!in(g.buurt != null ? g.buurt : g.gebied.groter(6)).isEmpty()) g.leegSinds = 0;
        else if (g.leegSinds == 0) g.leegSinds = nu;
        else if (nu - g.leegSinds > 60_000L) zetGetij(g, g.rustVloed, true);
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
                    if (p.zichtbaar != null) e.setVisibleByDefault(false);     // alleen voor wie hem mag zien (zie hieronder)
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
                if (p.uiterlijk != null) p.uiterlijk.accept(nieuw);
                if (nieuw instanceof LivingEntity le) p.ent = le;
                else nieuw.remove();
            } else if (!moet && p.ent != null) {
                if (p.zichtbaar != null) for (Player q : Bukkit.getOnlinePlayers()) if (q.canSee(p.ent)) q.hideEntity(plugin, p.ent);   // niets laten hangen
                p.ent.remove();
                p.ent = null;
            }
            if (p.ent != null && p.zichtbaar != null)                  // een persoon die niet iedereen ziet (een geit die al thuis is)
                for (UUID id : sessies.keySet()) {
                    Player q = Bukkit.getPlayer(id);
                    if (q == null || !q.getWorld().equals(p.plek.getWorld())) continue;
                    boolean zien = p.zichtbaar.test(voortgang.van(id));
                    if (zien && !q.canSee(p.ent)) q.showEntity(plugin, p.ent);
                    else if (!zien && q.canSee(p.ent)) q.hideEntity(plugin, p.ent);
                }
        }
        // wie iets nieuws te zeggen heeft (een gesprek dat je nog niet hoorde), glinstert zacht: alleen voor jou
        if (tikken % 40 == 0) for (ZeldaHoofdstuk.Persoon pe : h.personen) {
            if (pe.ent == null || !pe.ent.isValid() || pe.gesprekken.isEmpty()) continue;
            for (UUID id : sessies.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.getWorld().equals(pe.plek.getWorld()) || p.getLocation().distanceSquared(pe.plek) > 24 * 24) continue;
                if (pe.nieuw(voortgang.van(id)) && (pe.zichtbaar == null || pe.zichtbaar.test(voortgang.van(id))))
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

    /** Wat het dier van een speler zoekt: verborgen vondsten en graafplekken; in een gevecht gaat het uit de weg. */
    private final ZeldaDier.Neus neus = new ZeldaDier.Neus() {
        public List<Location> verborgen(Player p) {
            return ZeldaSpel.this.verborgen(p);
        }

        public List<ZeldaHoofdstuk.Graaf> graven(Player p) {
            return ZeldaSpel.this.graven(p);
        }

        public boolean gevecht(Player p) {
            return inGevecht(p);
        }

        public void gegraven(Player p, ZeldaHoofdstuk.Graaf g) {
            ZeldaSpel.this.gegraven(p, g);
        }
    };

    /** Graafplekken op het eiland van deze speler die hij nog niet heeft. */
    private List<ZeldaHoofdstuk.Graaf> graven(Player p) {
        List<ZeldaHoofdstuk.Graaf> l = new ArrayList<>();
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || ss.h.eiland == null || beheer.is(p.getUniqueId())) return l;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        for (ZeldaHoofdstuk h : ss.h.eiland.delen())
            for (ZeldaHoofdstuk.Graaf g : h.graven) if (!s.heeft(g.vlag())) l.add(g);
        return l;
    }

    /** Het dier heeft iets opgegraven: wie erbij staat, heeft het nu (de vlag), met een deuntje en een regel van Wiske. */
    void gegraven(Player p, ZeldaHoofdstuk.Graaf g) {
        List<Player> wie = in(ZeldaHoofdstuk.Box.van(g.plek().getBlockX() - 8, g.plek().getBlockY() - 4, g.plek().getBlockZ() - 8,
                g.plek().getBlockX() + 8, g.plek().getBlockY() + 6, g.plek().getBlockZ() + 8));
        if (!wie.contains(p)) wie.add(p);
        g.plek().getWorld().playSound(g.plek(), Sound.BLOCK_SNOW_BREAK, 1f, 0.8f);
        g.plek().getWorld().spawnParticle(Particle.END_ROD, g.plek().clone().add(0, 0.6, 0), 20, 0.4, 0.4, 0.4, 0.03);
        for (Player q : wie) {
            if (!voortgang.van(q.getUniqueId()).zet(g.vlag())) continue;
            voortgang.vuil();
            muziek.speel(q, ZeldaMuziek.OPGELOST);
            if (g.zegt() != null) vertel(q, g.zegt());
        }
        bewaar();
    }

    /** Je krijgt een dier (het sneeuwvosje): vlag, nog geen naam, een deuntje, en meteen het menu om hem een naam te geven. */
    void dier(Player p, String soort) {
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        s.zet("dier:" + soort);
        s.dier = null;
        voortgang.vuil();
        bewaar();
        muziek.speel(p, ZeldaMuziek.VONDST);
        Sessie ss = sessie(p);
        if (ss != null) ss.naamGevraagd = false;             // het menu komt (in seconde) als het gesprek uit is
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
            for (Object o : h.volgorde) {                           // in de volgorde van het eiland (niet op nummer)
                if (!(o instanceof ZeldaHoofdstuk.Rust r)) continue;
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
        for (ZeldaEiland.Komt k : modus.KOMT) if (s.heeft(k.openNa()) && slot <= 26) m.inv.setItem(slot++, komtItem(k));
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
        if (voltooid(s)) {                                                 // het verhaal is uit, alles blijft open
            int[] st = stukjes(s);
            ItemStack af = ding(Material.NETHER_STAR, "Story of Zelda · voltooid", "niets");
            lore(af, "hartstukjes: " + st[0] + " van " + st[1]);
            m.inv.setItem(31, af);
        }
        boolean uitleg = !s.heeft("o:uitleg-uit");
        ItemStack ui = ding(uitleg ? Material.BOOK : Material.GRAY_DYE, "Uitleg: " + (uitleg ? "aan" : "uit"), "uitleg");
        lore(ui, "klik: " + (uitleg ? "uit (alleen het verhaal)" : "aan"));
        m.inv.setItem(32, ui);
        m.inv.setItem(35, ding(Material.OAK_DOOR, "Terug naar de hub", "hub"));
        p.openInventory(m.inv);
    }

    /** Een eiland dat er al aankomt (een baken brandt) maar nog niet te bezoeken is: grijs, «nog even geduld». */
    private ItemStack komtItem(ZeldaEiland.Komt k) {
        ItemStack it = ding(k.icoon(), k.naam(), "komt:" + k.id());
        ZeldaEiland op = k.eiland() == null ? null : eiland(k.eiland());
        lore(it, op == null ? "nog even geduld" : "op " + ZeldaRegels.inZin(op.naam()) + " · nog even geduld");
        return it;
    }

    /** Wat Wiske (of de wegwijzer) zegt over een eiland (of een deel ervan) dat eraan komt (null = er komt niets). */
    String komt(ZeldaVoortgang.Stand s) {
        for (ZeldaEiland.Komt k : modus.KOMT)
            if (s.heeft(k.openNa())) return k.eiland() == null ? "Straks varen we naar " + ZeldaRegels.inZin(k.naam()) + ". Nog even geduld."
                    : k.naam() + (eiland(k.eiland()) == null ? "" : " op " + ZeldaRegels.inZin(eiland(k.eiland()).naam())) + " gaat straks open. Nog even geduld.";
        return null;
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
        for (String v : ZeldaRegels.opnieuw(h.volgorde, h.rust.get(i), h.open())) s.wis(v);
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
        List<ZeldaHoofdstuk.Regel> bijzonder = null;                       // een bijzondere overtocht (de terugvaart met Zelda, 5b)
        for (ZeldaHoofdstuk h : hoofdstukken) if (bijzonder == null) bijzonder = h.vaart(s, naar);
        if (bijzonder != null) voortgang.vuil();
        long nu = System.currentTimeMillis();
        ss.vaart = new Vaart(naar, nu + (bijzonder != null || eerste && !naar.overtocht().isEmpty() ? 16_000L : 9_000L), eerste);
        if (ss.hitte > 0) {                                                  // aan boord is het koel
            ss.hitte = 0;
            ss.koelplek = null;
            p.setExp(0f);
        }
        ss.rij.clear();
        ss.nu = null;
        ss.tot = 0;
        muziek.lus(p, null);
        muziek.achter(p, bijzonder != null ? ZeldaMuziek.THUIS : ZeldaMuziek.ZEE);
        p.setFallDistance(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
        p.teleport(zee.dek.get(rnd.nextInt(zee.dek.size())));
        ss.laatsteHemel = -1;
        hemel(p, ss, p.getLocation());
        p.playSound(p, Sound.ENTITY_BOAT_PADDLE_WATER, 0.6f, 0.8f);
        if (bijzonder != null) for (ZeldaHoofdstuk.Regel r : bijzonder) vertel(p, r);
        else if (eerste && !naar.overtocht().isEmpty()) for (ZeldaHoofdstuk.Regel r : naar.overtocht()) vertel(p, r);
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
        for (ZeldaEiland.Komt k : modus.KOMT) if (k.eiland() == null && s.heeft(k.openNa()) && slot < 9) m.inv.setItem(slot++, komtItem(k));   // een deel van een eiland: daar vaar je niet heen
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
        muziek.achter(p, h.muziek(r.plek, voortgang.van(p.getUniqueId())));
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
        ZeldaHoofdstuk.Doel d = ZeldaRegels.doel(alle(e, h -> h.doelen), s, p.getLocation().getX(), p.getLocation().getZ());
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
        List<ZeldaHoofdstuk.Missie> bezig = bezig(e, s);
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
        for (ZeldaHoofdstuk.Persoon pe : alle(e, h -> h.personen))     // iemand met iets nieuws (nog eens vragen = ze wijst), die je ook ziet
            if (pe.nieuw(s) && (pe.zichtbaar == null || pe.zichtbaar.test(s))) {
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
        if (komt(s) != null) {                                             // een baken brandt: het volgende eiland komt eraan
            vertel(p, ZeldaHoofdstuk.r("Wiske", komt(s)));
            return;
        }
        if (voltooid(s)) {                                                 // het verhaal is uit: nog hartstukjes te zoeken?
            int[] st = stukjes(s);
            vertel(p, ZeldaHoofdstuk.r("Wiske", st[0] < st[1] ? "Alles is weer licht. Nog " + (st[1] - st[0]) + " hartstukjes verstopt!"
                    : "Alles is weer licht. Dank je wel!"));
            return;
        }
        vertel(p, ZeldaHoofdstuk.r("Wiske", "Ik weet het ook niet. Kijk eens rond, praat met wie je ziet."));
    }

    /** Is Story of Zelda voltooid (de klaar-vlag van het laatste eiland: Zelda is thuis, het feest)? */
    boolean voltooid(ZeldaVoortgang.Stand s) {
        return s.heeft(eilanden.get(eilanden.size() - 1).klaar());
    }

    /** De hartstukjes van alle eilanden: {gevonden, hoeveel er zijn}. */
    int[] stukjes(ZeldaVoortgang.Stand s) {
        java.util.Set<String> alle = new java.util.LinkedHashSet<>();
        for (ZeldaHoofdstuk h : hoofdstukken) alle.addAll(h.stukjes);
        int n = 0;
        for (String v : alle) if (s.heeft(v)) n++;
        return new int[]{n, alle.size()};
    }

    /** Zijmissies waar je mee bezig bent: die van dit eiland, en anders die van alle eilanden (een boodschap over zee). */
    private List<ZeldaHoofdstuk.Missie> bezig(ZeldaEiland e, ZeldaVoortgang.Stand s) {
        List<ZeldaHoofdstuk.Missie> hier = ZeldaRegels.bezig(alle(e, h -> h.missies), s);
        if (!hier.isEmpty()) return hier;
        List<ZeldaHoofdstuk.Missie> overal = new ArrayList<>();
        for (ZeldaEiland o : eilanden) overal.addAll(alle(o, h -> h.missies));
        return ZeldaRegels.bezig(overal, s);
    }

    /**
     * Een wegwijzer (Tijmen, de tovenaar): hij weet altijd waar je volgende opdracht is. Praten = hij zegt het (zoals de
     * tweede hint), en Wiske vliegt die kant op. Geen doel meer op dit eiland: een eiland waar je nog niet was, je
     * zijmissies, wat je met een nieuw voorwerp kunt halen, of wat het eiland nog laat wachten.
     */
    void wegwijzer(Player p, String naam) {
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || ss.h.eiland == null) return;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        ZeldaEiland e = ss.h.eiland;
        ZeldaHoofdstuk.Doel d = ZeldaRegels.doel(alle(e, h -> h.doelen), s, p.getLocation().getX(), p.getLocation().getZ());
        if (d != null) {
            vertel(p, ZeldaHoofdstuk.r(naam, d.verder() != null ? d.verder() : d.tekst()));
            if (d.plek() != null) metgezel.wijs(p, d.plek());
            return;
        }
        for (ZeldaEiland o : eilanden)
            if (o != e && o.open(s) && o.aanlegRust() != null && !s.heeft("e:" + o.id())) {
                vertel(p, ZeldaHoofdstuk.r(naam, "De sterren wijzen naar " + ZeldaRegels.inZin(o.naam()) + ". Brecht vaart je."));
                return;
            }
        List<ZeldaHoofdstuk.Missie> bezig = bezig(e, s);
        if (!bezig.isEmpty()) {
            ZeldaHoofdstuk.Missie m = bezig.get(0);
            vertel(p, ZeldaHoofdstuk.r(naam, m.naam() + "? " + ZeldaRegels.stap(m, s)));
            return;
        }
        for (ZeldaHoofdstuk.Vondst v : alle(e, h -> h.vondsten))
            if (later(v, s)) {
                vertel(p, ZeldaHoofdstuk.r(naam, "Met je nieuwe spullen ligt hier nog iets voor je."));
                metgezel.wijs(p, v.plek);
                return;
            }
        ZeldaHoofdstuk laatste = e.delen().get(e.delen().size() - 1);
        if (!s.heeft(e.klaar()) && laatste.wacht() != null) {
            vertel(p, ZeldaHoofdstuk.r(naam, laatste.wacht()));
            return;
        }
        if (komt(s) != null) {
            vertel(p, ZeldaHoofdstuk.r(naam, komt(s)));
            return;
        }
        if (voltooid(s)) {
            int[] st = stukjes(s);
            vertel(p, ZeldaHoofdstuk.r(naam, st[0] < st[1] ? "De sterren zeggen: nog " + (st[1] - st[0]) + " hartstukjes." : "Het licht is terug. Rust maar uit."));
            return;
        }
        vertel(p, ZeldaHoofdstuk.r(naam, "Rust maar even uit. We zien elkaar verderop."));
    }

    /** Helemaal beter (bv. een warm broodje bij de bakker). */
    void genees(Player p) {
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || p.isDead() || p.getHealth() >= max.getValue()) return;
        p.setHealth(max.getValue());
        p.playSound(p, Sound.ENTITY_GENERIC_EAT, 0.6f, 1.1f);
        p.spawnParticle(Particle.HEART, p.getLocation().add(0, 1.4, 0), 4, 0.4, 0.3, 0.4, 0);
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
        m.inv = Bukkit.createInventory(m, 9, Component.text("sneeuwvos".equals(ZeldaDier.soort(s)) ? "Hoe heet het vosje?" : "Hoe heet hij?", NamedTextColor.DARK_GRAY));
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
        if (s.zet("v:neus")) {
            voortgang.vuil();
            uitleg(p, ZeldaHoofdstuk.r("Wiske", naam + " ruikt wat verstopt ligt. Volg hem maar!"));
        }
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
        } else if (soort.startsWith("komt:") && ss != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                vertel(p, ZeldaHoofdstuk.r("Wiske", "Daar kunnen we nog niet heen. Nog even geduld."));
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

        /** Elk flesje telt (Tijmen geeft {@code fles2} al voordat Hanne {@code fles1} geeft). */
        public boolean heeft(ZeldaVoortgang.Stand s) {
            return ZeldaRegels.flessen(s) > 0;
        }

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
            geneesBij(p, ZeldaRegels.flesHp(s));
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
        for (ZeldaHoofdstuk h : hoofdstukken) {
            for (ZeldaHoofdstuk.Kamer k : h.kamers)                          // een baas kan iets met een bom (Zoutbaards ton)
                if (k.baas != null && k.fase == ZeldaHoofdstuk.Fase.BEZIG && k.baas.leeft() && k.binnen.in(l)) k.baas.boem(this, p, l);
            if (h.speelgebied != null && h.speelgebied.in(l)) h.boem(this, p, l);
        }
    }

    // ── de klauwhaak (voorwerp in vak 5, eiland 3) ──────────────────────────────────────────

    /**
     * De klauwhaak van de bergwachters: rechtsklik = de haak schieten. Mik je op een haakpunt (een blok
     * {@link ZeldaHoofdstuk#HAAKPUNT}, binnen {@link ZeldaRegels#HAAK_BEREIK}; een paar graden ernaast mag), dan grijpt hij
     * en trekt hij je erheen: je staat daarna op de landing van dat haakpunt. Mis = de haak tikt ergens tegen en komt
     * terug. Werkt op elk eiland (ook de oude haakpunten van het Lindewoud en het Zilverstrand).
     */
    final class Klauwhaak implements ZeldaVoorwerp {
        static final Material ITEM = Material.TRIPWIRE_HOOK;

        public String id() { return "klauwhaak"; }
        public String vlag() { return "klauwhaak"; }
        public int vak() { return 4; }

        public ItemStack item(ZeldaSpel spel, Player p, ZeldaVoortgang.Stand s) {
            ItemStack it = ding(ITEM, "Klauwhaak", "klauwhaak");
            lore(it, "rechtsklik: richt op een haakpunt", "trekt je erheen");
            return it;
        }

        public void gebruik(ZeldaSpel spel, Player p, PlayerInteractEvent e) {
            haak(p);
        }
    }

    /** Een haak onderweg: eerst vliegt hij uit ({@code uit} tikken), dan trekt hij je erheen ({@code trek}; mis = terug). */
    private static final class Trek {
        final ZeldaHoofdstuk.Haakpunt hp;                    // null = mis
        final Location naar;                                 // waar de haak grijpt of stopt
        Location oog;                                        // waar hij vandaan komt (bij het grijpen: waar je ogen dan zijn)
        final int uit, trek;
        int tik;

        Trek(ZeldaHoofdstuk.Haakpunt hp, Location oog, Location naar, int uit, int trek) {
            this.hp = hp;
            this.oog = oog;
            this.naar = naar;
            this.uit = uit;
            this.trek = trek;
        }
    }

    final Map<UUID, Trek> trekken = new HashMap<>();

    /** Het haakpunt waar de klauwhaak van deze speler nu naartoe gaat (null = geen haak onderweg, of mis). */
    ZeldaHoofdstuk.Haakpunt haaktNaar(Player p) {
        Trek t = trekken.get(p.getUniqueId());
        return t == null ? null : t.hp;
    }

    /** Wordt deze speler nu door de klauwhaak getrokken (of is de haak onderweg)? */
    boolean aanHetHaken(Player p) {
        return trekken.containsKey(p.getUniqueId());
    }

    /** De haakpunten waar deze speler bij kan: die van zijn eiland. */
    private List<ZeldaHoofdstuk.Haakpunt> haakpunten(Sessie ss) {
        List<ZeldaHoofdstuk.Haakpunt> l = new ArrayList<>();
        for (ZeldaHoofdstuk h : ss.h.eiland != null ? ss.h.eiland.delen() : hoofdstukken) l.addAll(h.haakpunten);
        return l;
    }

    /** Ziet je oog dit haakpunt (zit er niets tussen)? */
    boolean zicht(Location oog, ZeldaHoofdstuk.Haakpunt hp) {
        Location m = new Location(oog.getWorld(), hp.x() + 0.5, hp.y() + 0.5, hp.z() + 0.5);
        org.bukkit.util.Vector v = m.toVector().subtract(oog.toVector());
        double d = v.length();
        if (d < 0.1 || d > ZeldaRegels.HAAK_BEREIK + 1) return false;
        var r = oog.getWorld().rayTraceBlocks(oog, v.normalize(), d + 0.6, org.bukkit.FluidCollisionMode.NEVER, true);
        return r != null && r.getHitBlock() != null && r.getHitBlock().getX() == hp.x() && r.getHitBlock().getY() == hp.y() && r.getHitBlock().getZ() == hp.z();
    }

    /** De klauwhaak schieten: grijpt hij een haakpunt (waar je op mikt, of een paar graden ernaast), dan trekt hij je erheen. */
    void haak(Player p) {
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || p.isDead() || trekken.containsKey(p.getUniqueId()) || p.hasCooldown(Klauwhaak.ITEM) || (inGevecht(p) && !haakMag(p))
                || glijdt(p)) return;                                       // tijdens het glijden niet (eerst stilstaan)
        p.setCooldown(Klauwhaak.ITEM, 14);
        Location oog = p.getEyeLocation();
        org.bukkit.util.Vector kijk = oog.getDirection();
        ZeldaHoofdstuk.Haakpunt doel = null;
        var raak = p.getWorld().rayTraceBlocks(oog, kijk, ZeldaRegels.HAAK_BEREIK, org.bukkit.FluidCollisionMode.NEVER, true);
        if (raak != null && raak.getHitBlock() != null) doel = haakpunt(raak.getHitBlock());
        if (doel == null) {                                                 // net ernaast gemikt: het dichtstbijzijnde haakpunt in die richting
            List<ZeldaHoofdstuk.Haakpunt> hps = haakpunten(ss);
            List<double[]> punten = new ArrayList<>();
            for (ZeldaHoofdstuk.Haakpunt h : hps) punten.add(new double[]{h.x() + 0.5, h.y() + 0.5, h.z() + 0.5});
            int i = ZeldaRegels.mik(new double[]{oog.getX(), oog.getY(), oog.getZ()}, new double[]{kijk.getX(), kijk.getY(), kijk.getZ()},
                    punten, ZeldaRegels.HAAK_BEREIK, ZeldaRegels.HAAK_HOEK);
            if (i >= 0 && zicht(oog, hps.get(i))) doel = hps.get(i);
        }
        Location naar = doel != null ? new Location(p.getWorld(), doel.x() + 0.5, doel.y() + 0.5, doel.z() + 0.5)
                : raak != null ? raak.getHitPosition().toLocation(p.getWorld()) : oog.clone().add(kijk.clone().multiply(ZeldaRegels.HAAK_BEREIK));
        double d = naar.distance(oog);
        int uit = Math.max(2, (int) Math.ceil(d / 2.6));
        Trek t = new Trek(doel, oog.clone(), naar, uit, doel == null ? 0 : Math.max(3, (int) Math.ceil(d / 1.5)));
        trekken.put(p.getUniqueId(), t);
        p.getWorld().playSound(oog, Sound.ENTITY_FISHING_BOBBER_THROW, 0.8f, 0.6f);
        p.getWorld().playSound(oog, Sound.ITEM_CROSSBOW_SHOOT, 0.4f, 1.4f);
        if (doel == null) {
            ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
            if (s.zet("v:haak-mis")) {
                voortgang.vuil();
                uitleg(p, ZeldaHoofdstuk.r("Wiske", "Richt op een haakpunt: een donker blok met een ring."));
            }
        }
    }

    /** Elke tik: de haken die onderweg zijn (de ketting, het grijpen, het trekken). */
    private void trekken() {
        Particle.DustOptions ketting = new Particle.DustOptions(Color.fromRGB(0x8a8f96), 0.7f);
        for (var it = trekken.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Trek t = e.getValue();
            Sessie ss = p == null ? null : sessie(p);
            if (p == null || p.isDead() || ss == null || ss.vaart != null) {
                it.remove();
                continue;
            }
            t.tik++;
            var w = p.getWorld();
            Location oog = p.getEyeLocation();
            Location kop;
            if (t.tik <= t.uit) kop = t.oog.clone().add(t.naar.toVector().subtract(t.oog.toVector()).multiply(t.tik / (double) t.uit));
            else if (t.hp == null) {                                         // mis: de haak komt terug
                int terug = t.tik - t.uit;
                if (terug == 1) w.playSound(t.naar, Sound.BLOCK_STONE_HIT, 0.8f, 1.6f);
                if (terug >= t.uit) {
                    w.playSound(oog, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.6f, 1.2f);
                    it.remove();
                    continue;
                }
                kop = t.naar.clone().add(oog.toVector().subtract(t.naar.toVector()).multiply(terug / (double) t.uit));
            } else {                                                         // raak: trekken, je ogen langs de ketting naar het haakpunt
                int k = t.tik - t.uit;
                if (k == 1) {
                    t.oog = p.getEyeLocation();                              // vanaf waar je nu bent (je kon intussen bewegen)
                    w.playSound(t.naar, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 0.8f);
                    w.spawnParticle(Particle.CRIT, t.naar, 12, 0.3, 0.3, 0.3, 0.2);
                }
                kop = t.naar;
                if (k >= t.trek) {
                    Location l = t.hp.landing().clone();
                    p.setVelocity(new org.bukkit.util.Vector());
                    p.setFallDistance(0);
                    p.teleport(l);
                    w.playSound(l, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.7f, 0.9f);
                    w.playSound(l, Sound.BLOCK_WOOL_STEP, 0.8f, 0.8f);
                    it.remove();
                    continue;
                }
                double f = k / (double) t.trek;
                org.bukkit.util.Vector naarOog = t.naar.toVector().subtract(t.oog.toVector());
                double lang = naarOog.length();
                Location nieuwOog = t.oog.clone().add(naarOog.multiply(f * Math.max(0, lang - 1.0) / Math.max(0.01, lang)));
                Location voet = nieuwOog.clone().subtract(0, p.getEyeHeight(), 0);
                voet.setYaw(p.getLocation().getYaw());
                voet.setPitch(p.getLocation().getPitch());
                p.setVelocity(new org.bukkit.util.Vector());
                p.setFallDistance(0);
                p.teleport(voet);
                oog = p.getEyeLocation();
            }
            org.bukkit.util.Vector lijn = kop.toVector().subtract(oog.toVector().add(new org.bukkit.util.Vector(0, -0.4, 0)));
            double lang = lijn.length();
            Location van = oog.clone().add(0, -0.4, 0);
            for (double a = 0.6; a < lang; a += 0.45)
                w.spawnParticle(Particle.DUST, van.clone().add(lijn.clone().multiply(a / lang)), 1, 0, 0, 0, 0, ketting);
            w.spawnParticle(Particle.CRIT, kop, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    /** Wie aan de klauwhaak hangt, valt niet en stikt niet (hij wordt even langs de rots getrokken). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHaakSchade(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && trekken.containsKey(p.getUniqueId())
                && (e.getCause() == EntityDamageEvent.DamageCause.FALL || e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION)) e.setCancelled(true);
    }

    // ── de vuurstaf (voorwerp in vak 6, eiland 4a) ──────────────────────────────────────────

    /**
     * De vuurstaf van de eerste smeden (de Gloedberg): rechtsklik = een vlam die rechtdoor vliegt, tot
     * {@link ZeldaRegels#VUUR_BEREIK} blokken. Mik je op een vuurschaal of lamp (een paar graden ernaast mag), dan gaat hij
     * aan ({@link #activeer}); op betoverd ijs, dan smelt het ({@link #smelt}); een vijand krijgt een tik. Werkt op elk
     * eiland: ook de vuurschalen van het Lindewoud en het ijsblok bij de smidse van Hagen.
     */
    final class Vuurstaf implements ZeldaVoorwerp {
        static final Material ITEM = Material.BLAZE_ROD;

        public String id() { return "vuurstaf"; }
        public String vlag() { return "vuurstaf"; }
        public int vak() { return 5; }

        public ItemStack item(ZeldaSpel spel, Player p, ZeldaVoortgang.Stand s) {
            ItemStack it = ding(ITEM, "Vuurstaf", "vuurstaf");
            lore(it, "rechtsklik: een vlam", "steekt vuur aan, smelt ijs");
            return it;
        }

        public void gebruik(ZeldaSpel spel, Player p, PlayerInteractEvent e) {
            vlam(p);
        }
    }

    /** Een vlam onderweg (een paar tikken), en wat hij raakt als hij aankomt (een vuurschaal, ijs, een vijand of niets). */
    private static final class Vlam {
        final UUID wie;
        final Location van, naar;
        final int duur;
        final ZeldaHoofdstuk.Reeks reeks;
        final int ding;
        final ZeldaHoofdstuk.Smelt ijs;
        final LivingEntity vijand;
        int tik;

        Vlam(UUID wie, Location van, Location naar, int duur, ZeldaHoofdstuk.Reeks reeks, int ding, ZeldaHoofdstuk.Smelt ijs, LivingEntity vijand) {
            this.wie = wie;
            this.van = van;
            this.naar = naar;
            this.duur = duur;
            this.reeks = reeks;
            this.ding = ding;
            this.ijs = ijs;
            this.vijand = vijand;
        }
    }

    final List<Vlam> vlammen = new ArrayList<>();

    /** Heeft deze speler een vlam onderweg (de test wacht erop)? */
    boolean vlamOnderweg(Player p) {
        for (Vlam v : vlammen) if (v.wie.equals(p.getUniqueId())) return true;
        return false;
    }

    /** Het midden van wat je in dit blok raakt (een vuurschaal is lager dan een heel blok). */
    static Location raakpunt(Block b) {
        var box = b.getBoundingBox();
        if (box.getVolume() <= 0) return b.getLocation().add(0.5, 0.5, 0.5);
        return box.getCenter().toLocation(b.getWorld());
    }

    /** Ziet je oog dit blok (zit er niets tussen)? */
    boolean zicht(Location oog, Block b) {
        return zichtpunt(oog, b) != null;
    }

    /**
     * Een punt van dit blok dat je oog ziet, binnen het bereik van de vuurstaf: het midden, de bovenkant (ijs in de vloer)
     * of een zijkant. Null = niet te zien.
     */
    Location zichtpunt(Location oog, Block b) {
        var box = b.getBoundingBox();
        List<Location> punten = new ArrayList<>();
        if (box.getVolume() <= 0) punten.add(b.getLocation().add(0.5, 0.5, 0.5));
        else {
            var c = box.getCenter();
            var w = b.getWorld();
            punten.add(c.toLocation(w));
            punten.add(new Location(w, c.getX(), box.getMaxY() - 0.05, c.getZ()));
            punten.add(new Location(w, box.getMinX() + 0.05, c.getY(), c.getZ()));
            punten.add(new Location(w, box.getMaxX() - 0.05, c.getY(), c.getZ()));
            punten.add(new Location(w, c.getX(), c.getY(), box.getMinZ() + 0.05));
            punten.add(new Location(w, c.getX(), c.getY(), box.getMaxZ() - 0.05));
        }
        for (Location m : punten) {
            org.bukkit.util.Vector v = m.toVector().subtract(oog.toVector());
            double d = v.length();
            if (d < 0.1 || d > ZeldaRegels.VUUR_BEREIK + 1) continue;
            var r = oog.getWorld().rayTraceBlocks(oog, v.normalize(), d + 0.4, org.bukkit.FluidCollisionMode.NEVER, true);
            if (r != null && r.getHitBlock() != null && r.getHitBlock().equals(b)) return m;
        }
        return null;
    }

    /** Wat je oog raakt als je naar het midden van dit blok kijkt (voor de zelftest: wat staat ervoor?). */
    String zichtRaakt(Location oog, Block b) {
        Location m = raakpunt(b);
        org.bukkit.util.Vector v = m.toVector().subtract(oog.toVector());
        double d = v.length();
        var r = oog.getWorld().rayTraceBlocks(oog, v.normalize(), d + 0.4, org.bukkit.FluidCollisionMode.NEVER, true);
        if (r == null || r.getHitBlock() == null) return "niets (" + String.format("%.1f", d) + " ver)";
        Block h = r.getHitBlock();
        return h.getType() + " op " + h.getX() + "," + h.getY() + "," + h.getZ() + " (" + String.format("%.1f", d) + " ver)";
    }

    /**
     * De vuurstaf: een vlam waar je op mikt. Raakt hij een vuurschaal of lamp die nog uit is (of mik je er net naast), dan
     * gaat die aan als de vlam aankomt; betoverd ijs smelt; een vijand krijgt een tik.
     */
    void vlam(Player p) {
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || p.isDead() || p.hasCooldown(Vuurstaf.ITEM)) return;
        p.setCooldown(Vuurstaf.ITEM, 16);
        var w = p.getWorld();
        Location oog = p.getEyeLocation();
        org.bukkit.util.Vector kijk = oog.getDirection();
        // wat je met een vlam kunt aansteken of smelten (op dit eiland): {reeks, ding} of {ijs}, met het blok
        List<Object[]> doelen = new ArrayList<>();
        List<double[]> punten = new ArrayList<>();
        for (ZeldaHoofdstuk h : ss.h.eiland != null ? ss.h.eiland.delen() : List.of(ss.h)) {
            for (ZeldaHoofdstuk.Reeks r : h.reeksen) {
                if (r.hoe == ZeldaHoofdstuk.Reeks.Hoe.PIJL || r.opgelost) continue;
                if (r.hoe == ZeldaHoofdstuk.Reeks.Hoe.KLIK && !(r.uit instanceof org.bukkit.block.data.type.Campfire)) continue;   // een hendel trekt de vlam niet om
                for (int i = 0; i < r.dingen.size(); i++) {
                    if (r.isAan.contains(i)) continue;
                    int[] c = r.dingen.get(i);
                    doelen.add(new Object[]{r, i, w.getBlockAt(c[0], c[1], c[2])});
                }
            }
            for (ZeldaHoofdstuk.Smelt sm : h.smelten) {
                if (sm.ijs.isOpen) continue;
                for (int[] c : sm.ijs.blokken) doelen.add(new Object[]{sm, -1, w.getBlockAt(c[0], c[1], c[2])});
            }
        }
        for (Object[] d : doelen) {
            Location m = raakpunt((Block) d[2]);
            punten.add(new double[]{m.getX(), m.getY(), m.getZ()});
        }
        var raak = w.rayTrace(oog, kijk, ZeldaRegels.VUUR_BEREIK, org.bukkit.FluidCollisionMode.NEVER, true, 0.3,
                e -> e instanceof LivingEntity && !e.equals(p) && isVijand(e) && !e.getScoreboardTags().contains(NPC));
        Object[] doel = null;
        LivingEntity vijand = null;
        if (raak != null && raak.getHitBlock() != null)
            for (Object[] d : doelen) if (((Block) d[2]).equals(raak.getHitBlock())) doel = d;
        if (doel == null && raak != null && raak.getHitEntity() instanceof LivingEntity le) vijand = le;
        while (doel == null && vijand == null && !punten.isEmpty()) {       // net ernaast gemikt: het best passende doel dat je ziet
            int i = ZeldaRegels.mik(new double[]{oog.getX(), oog.getY(), oog.getZ()}, new double[]{kijk.getX(), kijk.getY(), kijk.getZ()},
                    punten, ZeldaRegels.VUUR_BEREIK, ZeldaRegels.VUUR_HOEK);
            if (i < 0) break;
            if (zicht(oog, (Block) doelen.get(i)[2])) doel = doelen.get(i);
            else {
                doelen.remove(i);
                punten.remove(i);
            }
        }
        Location gezien = doel != null ? zichtpunt(oog, (Block) doel[2]) : null;
        Location naar = doel != null ? (gezien != null ? gezien : raakpunt((Block) doel[2]))
                : vijand != null ? vijand.getLocation().add(0, vijand.getHeight() / 2, 0)
                : raak != null ? raak.getHitPosition().toLocation(w) : oog.clone().add(kijk.clone().multiply(ZeldaRegels.VUUR_BEREIK));
        Location van = oog.clone().add(kijk.clone().multiply(0.6)).add(0, -0.25, 0);
        int duur = Math.max(2, (int) Math.ceil(naar.distance(van) / 3.0));
        vlammen.add(new Vlam(p.getUniqueId(), van, naar, duur, doel != null && doel[0] instanceof ZeldaHoofdstuk.Reeks r ? r : null,
                doel != null ? (int) doel[1] : -1, doel != null && doel[0] instanceof ZeldaHoofdstuk.Smelt sm ? sm : null, vijand));
        w.playSound(oog, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.3f);
        w.playSound(oog, Sound.ITEM_FIRECHARGE_USE, 0.4f, 1.6f);
    }

    /** Elke tik: de vlammen onderweg (een spoor van vuur), en wat ze raken als ze aankomen. */
    private void vlammen() {
        for (var it = vlammen.iterator(); it.hasNext(); ) {
            Vlam v = it.next();
            Player p = Bukkit.getPlayer(v.wie);
            if (p == null || !speelt(p)) {
                it.remove();
                continue;
            }
            v.tik++;
            var w = v.naar.getWorld();
            org.bukkit.util.Vector weg = v.naar.toVector().subtract(v.van.toVector());
            for (int i = 0; i < 3; i++) {
                double f = Math.min(1, (v.tik - 1 + i / 3.0) / v.duur);
                Location l = v.van.clone().add(weg.clone().multiply(f));
                w.spawnParticle(Particle.FLAME, l, 2, 0.05, 0.05, 0.05, 0.005);
                if (i == 0) w.spawnParticle(Particle.SMALL_FLAME, l, 1, 0.1, 0.1, 0.1, 0.01);
            }
            if (v.tik < v.duur) continue;
            it.remove();
            if (v.reeks != null) activeer(p, v.reeks, v.ding);
            else if (v.ijs != null) smelt(p, v.ijs);
            else if (v.vijand != null && v.vijand.isValid() && !v.vijand.isDead() && baasVan(v.vijand) != null && baasVan(v.vijand).vlam(this, p)) {
                w.spawnParticle(Particle.FLAME, v.vijand.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
            } else if (v.vijand != null && v.vijand.isValid() && !v.vijand.isDead()) {
                v.vijand.damage(ZeldaRegels.VUUR_SCHADE * (v.vijand.getScoreboardTags().contains(IJS) ? 1.5 : 1), p);
                w.spawnParticle(Particle.FLAME, v.vijand.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
                var duw = v.vijand.getLocation().toVector().subtract(v.van.toVector()).setY(0);
                if (duw.lengthSquared() > 0.01) v.vijand.setVelocity(duw.normalize().multiply(0.35).setY(0.2));
            } else {
                w.spawnParticle(Particle.LAVA, v.naar, 3, 0.1, 0.1, 0.1, 0);
                w.spawnParticle(Particle.SMOKE, v.naar, 6, 0.15, 0.15, 0.15, 0.01);
                w.playSound(v.naar, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.6f);
            }
        }
    }

    // ── betoverd ijs (smelt met de vuurstaf, eiland 4a) ─────────────────────────────────────

    /** De vuurstaf smelt betoverd ijs: lucht of water. Wie erbij is, onthoudt het ({@code s:<id>}) en vindt het voortaan gesmolten. */
    void smelt(Player p, ZeldaHoofdstuk.Smelt s) {
        if (s.ijs.isOpen) return;
        var w = p.getWorld();
        Location m = s.ijs.omvang().midden(w).add(0, 1, 0);
        deur(s.ijs, true, binnen(s));
        s.leegSinds = 0;
        w.playSound(m, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.8f);
        w.playSound(m, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.5f);
        w.spawnParticle(Particle.CLOUD, m, 25, 0.8, 0.6, 0.8, 0.03);
        List<Player> wie = in(s.gebied);
        if (!wie.contains(p)) wie.add(p);
        for (Player q : wie) if (voortgang.van(q.getUniqueId()).zet("s:" + s.id)) voortgang.vuil();
        muziek.speel(p, ZeldaMuziek.OPGELOST);
    }

    private Location binnen(ZeldaHoofdstuk.Smelt s) {
        return s.binnen != null ? s.binnen : s.gebied.midden(plugin.w).add(0, 1, 0);
    }

    /** Betoverd ijs weer dicht (bevroren). */
    void herstel(ZeldaHoofdstuk.Smelt s) {
        s.leegSinds = 0;
        deur(s.ijs, false, binnen(s));
    }

    /**
     * Elke vijf tikken: gesmolten en een minuut niemand in de buurt = weer bevroren; wie het ooit smolt, vindt het
     * gesmolten. In rust zet de motor het ijs terug als fel licht het toch liet smelten (gewoon ijs smelt bij licht).
     */
    private void smelt(ZeldaHoofdstuk.Smelt s, long nu) {
        if (s.ijs.isOpen) {
            if (!in(s.gebied.groter(6)).isEmpty()) s.leegSinds = 0;
            else if (s.leegSinds == 0) s.leegSinds = nu;
            else if (nu - s.leegSinds > 60_000L) herstel(s);
            return;
        }
        for (Player p : in(s.gebied))
            if (voortgang.van(p.getUniqueId()).heeft("s:" + s.id)) {
                s.leegSinds = 0;
                deur(s.ijs, true, binnen(s));
                return;
            }
        if (tikken % 100 == 0 && !in(s.gebied.groter(24)).isEmpty())
            for (int[] c : s.ijs.blokken) {
                Block b = plugin.w.getBlockAt(c[0], c[1], c[2]);
                if (b.getType() != s.ijs.dicht.getMaterial()) b.setBlockData(s.ijs.dicht, false);
            }
    }

    // ── hitte in de mijn (eiland 4a) ────────────────────────────────────────────────────────

    /** De hitte waar deze speler in staat (op zijn eiland), of null. */
    ZeldaHoofdstuk.Hitte hitteBij(Player p, Sessie ss) {
        if (ss == null || ss.h.eiland == null) return null;
        Location l = p.getLocation();
        for (ZeldaHoofdstuk h : ss.h.eiland.delen()) for (ZeldaHoofdstuk.Hitte x : h.hitten) if (x.gebied.in(l)) return x;
        return null;
    }

    /** Koelt deze speler hier af: in water, bij een zielvuur of in een bron (van zijn eiland)? */
    boolean koelplek(Player p, Sessie ss) {
        if (p.isInWater()) return true;
        Location l = p.getLocation();
        if (ss.h.eiland != null) for (ZeldaHoofdstuk h : ss.h.eiland.delen()) {
            for (ZeldaHoofdstuk.Rust r : h.rust) if (r.zone.in(l)) return true;
            for (ZeldaHoofdstuk.Bron b : h.bronnen) if (b.zone.in(l)) return true;
        }
        return false;
    }

    /**
     * Twee keer per seconde: in de hitte word je warm (de balk onderin), in water, bij een zielvuur of in een bron koel je
     * af, en met het gloedharnas deert het je niet. Te heet = terug naar waar je het laatst afkoelde. Geen schade.
     */
    private void hitte(Player p, Sessie ss, long nu) {
        if (ss == null || ss.vaart != null || p.isDead() || beheer.is(p.getUniqueId()) || ss.h.eiland == null) return;
        ZeldaHoofdstuk.Hitte h = hitteBij(p, ss);
        if (h == null) ss.koelplek = null;                                   // buiten de hitte: een nieuwe keer begint bij een nieuwe koelplek
        if (h == null && ss.hitte <= 0) return;
        ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
        boolean koel = koelplek(p, ss);
        double oud = ss.hitte;
        ss.hitte = ZeldaRegels.hitte(oud, 0.5, h != null, h == null ? 0 : h.snel, h == null ? 0 : h.koel, h != null && h.fel,
                s.heeft("koelsteen"), ZeldaRegels.hittevast(s), koel);
        if (h != null && koel && !h.fel) ss.koelplek = p.getLocation().clone();
        boolean race = false;
        for (ZeldaHoofdstuk d : ss.h.eiland.delen()) for (ZeldaHoofdstuk.Uitdaging u : d.uitdagingen) race |= u.loopt.containsKey(p.getUniqueId());
        if (!race) p.setExp((float) Math.min(0.999, ss.hitte));
        if (h != null && !h.fel && koel && !ss.koelt && oud > 0.2) {          // afkoelen: het sist even
            p.playSound(p, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.3f);
            p.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.8, 0), 8, 0.3, 0.3, 0.3, 0.02);
        }
        ss.koelt = koel && h != null;
        if (h != null && ss.hitte > 0.6) {
            p.spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1.1, 0), 3, 0.3, 0.4, 0.3, 0.01);
            if (nu % 2000 < 500) p.playSound(p, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.AMBIENT, 0.4f, 0.8f);
            if (s.zet("v:hitte")) {
                voortgang.vuil();
                uitleg(p, ZeldaHoofdstuk.r("Wiske", "Pff, warm! De balk onderin loopt vol."), ZeldaHoofdstuk.r("Wiske", "In water koel je af."));
            }
        }
        if (h != null && ss.hitte >= 1) teHeet(p, ss, h, nu);
    }

    /** Te heet: even terug naar waar je afkoelde (of de koele plek van deze hitte), zonder schade. */
    private void teHeet(Player p, Sessie ss, ZeldaHoofdstuk.Hitte h, long nu) {
        Location naar = h.terug;
        if (!h.fel && ss.koelplek != null && h.gebied.groter(12).in(ss.koelplek) && ss.koelplek.getBlock().isPassable()
                && ss.koelplek.clone().add(0, 1, 0).getBlock().isPassable()) naar = ss.koelplek;
        if (naar == null) naar = ss.rustplaats().plek;
        ss.hitte = 0.4;
        p.setFallDistance(0);
        p.teleport(naar);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false));
        p.playSound(p, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 0.4f, 1.3f);
        if (nu - ss.teHeet > 30_000L && ss.rij.isEmpty() && ss.nu == null)
            vertel(p, ZeldaHoofdstuk.r("Wiske", h.fel ? "Te heet! Zonder harnas kom je hier niet." : "Te heet! Even terug, afkoelen."));
        ss.teHeet = nu;
    }

    // ── lava (eiland 4b): stijgen en zakken, en er meteen weer uit springen ─────────────────

    /** Wat lava doet als je erin stapt: zoveel schade (halve harten), en je springt terug naar waar je veilig stond. */
    static final double LAVA_SCHADE = 2;

    /** Staat deze speler op vaste grond zonder lava vlakbij (onder en naast zijn voeten)? Dan is dit een veilige plek. */
    private boolean veiligeGrond(Player p) {
        if (p.isDead() || p.isInLava() || p.getFireTicks() > 0 || !((Entity) p).isOnGround()) return false;
        Location l = p.getLocation();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        if (!l.getBlock().isPassable() || !l.clone().add(0, -1, 0).getBlock().getType().isSolid()) return false;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = -1; dy <= 0; dy++) if (p.getWorld().getBlockAt(x + dx, y + dy, z + dz).getType() == Material.LAVA) return false;
        for (ZeldaHoofdstuk h : hoofdstukken)                          // niet op de stenen van een ritme (die gaan onder)
            for (ZeldaHoofdstuk.Ritme r : h.ritmes) if (r.lava.omvang().groter(1).in(l)) return false;
        return true;
    }

    /**
     * Lava in Zelda: wie erin stapt, springt er meteen weer uit (terug naar waar hij veilig stond), met een kleine schroeiplek
     * ({@link #LAVA_SCHADE}, nooit dood) en zonder brand. Zo kan lava overal een puzzel zijn, zonder dat je doodgaat.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLava(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !speelt(p) || beheer.is(p.getUniqueId())) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        boolean lava = c == EntityDamageEvent.DamageCause.LAVA;
        if (!lava && c != EntityDamageEvent.DamageCause.FIRE && c != EntityDamageEvent.DamageCause.FIRE_TICK) return;
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null) return;
        if (!lava && !p.isInLava()) {                                   // brand na de lava: weg
            if (System.currentTimeMillis() - ss.lavaTijd < 5000L) {
                e.setCancelled(true);
                p.setFireTicks(0);
            }
            return;
        }
        e.setCancelled(true);
        long nu = System.currentTimeMillis();
        if (nu - ss.lavaTijd < 700L) return;
        ss.lavaTijd = nu;
        Location naar = ss.veilig != null && ss.veilig.getWorld().equals(p.getWorld()) && ss.veilig.distanceSquared(p.getLocation()) < 60 * 60
                ? ss.veilig : ss.rustplaats().plek;
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
        p.getWorld().spawnParticle(Particle.LAVA, p.getLocation().add(0, 0.5, 0), 8, 0.3, 0.2, 0.3, 0);
        p.setFallDistance(0);
        p.setVelocity(new org.bukkit.util.Vector());
        p.teleport(naar);
        p.setFireTicks(0);
        p.playSound(p, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 0.6f, 1.2f);
        p.setHealth(Math.max(1, p.getHealth() - LAVA_SCHADE));
        ss.geraakt = nu;
        ZeldaVoortgang.Stand st = voortgang.van(p.getUniqueId());
        if (st.zet("v:lava")) {
            voortgang.vuil();
            vertel(p, ZeldaHoofdstuk.r("Wiske", "Au! Lava. Daar blijf je uit."));
        }
    }

    /**
     * Elke vijf tikken: lava die op een ritme stijgt en zakt, als er iemand in de buurt is. Een tel voor het stijgen
     * borrelt het. Zonder iemand in de buurt liggen de stenen droog.
     */
    private void ritme(ZeldaHoofdstuk.Ritme r, long nu) {
        boolean iemand = !in(r.gebied.groter(12)).isEmpty();
        if (!iemand) {
            if (r.isHoog) zetRitme(r, false);
            r.begin = 0;
            return;
        }
        if (r.begin == 0) r.begin = nu;
        long t = (nu - r.begin) % (r.laag + r.hoog);
        boolean hoog = t >= r.laag;
        if (hoog != r.isHoog) zetRitme(r, hoog);
        else if (!hoog && t >= r.laag - 1000) {                         // het borrelt: zo meteen stijgt het
            var w = plugin.w;
            for (int i = 0; i < 4; i++) {
                int[] c = r.lava.blokken.get(rnd.nextInt(r.lava.blokken.size()));
                w.spawnParticle(Particle.LAVA, c[0] + 0.5, c[1] + 1.0, c[2] + 0.5, 1, 0.2, 0, 0.2, 0);
            }
            int[] m = r.lava.blokken.get(r.lava.blokken.size() / 2);
            if (tikken % 10 == 0) w.playSound(new Location(w, m[0] + 0.5, m[1] + 0.5, m[2] + 0.5), Sound.BLOCK_LAVA_AMBIENT, 0.8f, 1.3f);
        }
    }

    /** Een deeltje alleen voor deze speler, ook van ver te zien (de Nevelring aan de horizon). {@code data}: kleur of null. */
    void deeltje(Player p, Particle d, Location l, int n, double ox, double oy, double oz, Object data) {
        Object dt = data != null && d.getDataType().isInstance(data) ? data : null;
        if (dt == null && d.getDataType() != Void.class) return;
        d.builder().location(l).count(n).offset(ox, oy, oz).speed(0).data(dt).receivers(p).force(true).spawn();
    }

    /** De lava van een ritme hoog of laag (zonder deurgeluid: lava borrelt en sist). */
    void zetRitme(ZeldaHoofdstuk.Ritme r, boolean hoog) {
        r.isHoog = hoog;
        r.lava.isOpen = !hoog;
        if (hoog) r.keren++;
        var w = plugin.w;
        BlockData d = hoog ? r.lava.dicht : r.lava.open;
        for (int[] c : r.lava.blokken) w.getBlockAt(c[0], c[1], c[2]).setBlockData(d, false);
        int[] m = r.lava.blokken.get(r.lava.blokken.size() / 2);
        Location ml = new Location(w, m[0] + 0.5, m[1] + 0.8, m[2] + 0.5);
        w.playSound(ml, hoog ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.BLOCK_LAVA_EXTINGUISH, 0.9f, hoog ? 0.8f : 1.4f);
        if (!hoog) for (int[] c : r.lava.blokken) w.spawnParticle(Particle.SMOKE, c[0] + 0.5, c[1] + 1.1, c[2] + 0.5, 2, 0.2, 0.05, 0.2, 0.01);
    }

    // ── glijvloeren (eiland 3b) ─────────────────────────────────────────────────────────────

    /** Iemand die over een glijvloer glijdt: vanaf het midden van een vakje, in een richting, tot een ander vakje. */
    private static final class Glijden {
        final ZeldaHoofdstuk.Glij g;
        final double x, z, lang;
        final BlockFace r;
        final int[] naar;
        double gedaan;

        Glijden(ZeldaHoofdstuk.Glij g, int[] van, BlockFace r, int[] naar) {
            this.g = g;
            this.x = van[0] + 0.5;
            this.z = van[1] + 0.5;
            this.r = r;
            this.naar = naar;
            this.lang = Math.abs(naar[0] - van[0]) + Math.abs(naar[1] - van[1]);
        }
    }

    final Map<UUID, Glijden> glijders = new HashMap<>();
    /** Waar je vorige tik stond op een glijvloer (x, z), en tot welke tik je even niet glijdt (net gestopt). */
    private final Map<UUID, double[]> glijVorige = new HashMap<>();

    /** Glijdt deze speler nu over een glijvloer? */
    boolean glijdt(Player p) {
        return glijders.containsKey(p.getUniqueId());
    }

    /** De glijvloer (van het eiland van deze speler) waar hij in staat, of null. */
    ZeldaHoofdstuk.Glij glijvloer(Player p) {
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || ss.h.eiland == null) return null;
        for (ZeldaHoofdstuk h : ss.h.eiland.delen())
            for (ZeldaHoofdstuk.Glij g : h.glijvloeren) if (g.gebied.in(p.getLocation())) return g;
        return null;
    }

    /** Het vakje waar je uitkomt als je vanaf {@code x, z} in richting {@code r} over deze glijvloer glijdt. */
    int[] glijNaar(ZeldaHoofdstuk.Glij g, int x, int z, BlockFace r) {
        var w = plugin.w;
        return ZeldaRegels.glij(x, z, r, g::isIjs,
                (a, b) -> w.getBlockAt(a, g.y + 1, b).isPassable() && w.getBlockAt(a, g.y + 2, b).isPassable(),
                (a, b) -> !g.isIjs(a, b) && w.getBlockAt(a, g.y, b).getType().isSolid(), 64);
    }

    /**
     * Begin te glijden vanaf het vakje waar je staat, in richting {@code r} (zoals een stap op het ijs; de test gebruikt
     * dit ook). False als het niet kan (je staat tegen iets aan, of je glijdt al).
     */
    boolean glij(Player p, ZeldaHoofdstuk.Glij g, BlockFace r) {
        if (glijdt(p) || p.isDead()) return false;
        Location l = p.getLocation();
        int[] van = {l.getBlockX(), l.getBlockZ()};
        if (!g.isIjs(van[0], van[1]) && !g.isIjs(van[0] + r.getModX(), van[1] + r.getModZ())) return false;
        int[] naar = glijNaar(g, van[0], van[1], r);
        if (naar[0] == van[0] && naar[1] == van[1]) return false;
        glijders.put(p.getUniqueId(), new Glijden(g, van, r, naar));
        p.getWorld().playSound(l, Sound.BLOCK_POWDER_SNOW_STEP, 0.8f, 1.4f);
        if (voortgang.van(p.getUniqueId()).zet("v:glij")) voortgang.vuil();   // je gleed al eens (de uitleg bij het ijs zwijgt dan)
        return true;
    }

    /**
     * Elke tik: wie glijdt, schuift verder (bijna een half blok per tik, kijken kan gewoon); wie op het ijs stilstaat en een
     * stap zet, begint te glijden in die richting. Na een stop even niets (anders glij je meteen weer).
     */
    private void glijden(List<Player> spelers) {
        for (Player p : spelers) {
            UUID id = p.getUniqueId();
            Glijden gl = glijders.get(id);
            if (gl != null) {
                Sessie ss = sessie(p);
                if (p.isDead() || ss == null || ss.vaart != null || beheer.is(id) || aanHetHaken(p) || !gl.g.gebied.in(p.getLocation())) {
                    glijders.remove(id);
                    continue;
                }
                gl.gedaan = Math.min(gl.lang, gl.gedaan + ZeldaRegels.GLIJ_SNELHEID);
                Location l = new Location(p.getWorld(), gl.x + gl.r.getModX() * gl.gedaan, gl.g.y + 1, gl.z + gl.r.getModZ() * gl.gedaan,
                        p.getLocation().getYaw(), p.getLocation().getPitch());   // kijken kan gewoon door
                p.setVelocity(new org.bukkit.util.Vector());
                p.setFallDistance(0);
                p.teleport(l);
                if (tikken % 2 == 0) p.getWorld().spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 0.2, 0), 2, 0.2, 0.05, 0.2, 0.01);
                if (gl.gedaan >= gl.lang) {
                    glijders.remove(id);
                    glijVorige.put(id, new double[]{l.getX(), l.getZ(), tikken + 4});
                    if (gl.g.isIjs(gl.naar[0], gl.naar[1])) p.getWorld().playSound(l, Sound.BLOCK_GLASS_HIT, 0.7f, 0.6f);   // ergens tegenaan
                    else p.getWorld().playSound(l, Sound.BLOCK_SNOW_STEP, 0.8f, 1f);                                     // van het ijs af
                }
                continue;
            }
            ZeldaHoofdstuk.Glij g = beheer.is(id) || aanHetHaken(p) ? null : glijvloer(p);
            if (g == null) {
                glijVorige.remove(id);
                continue;
            }
            Location l = p.getLocation();
            double[] v = glijVorige.get(id);
            glijVorige.put(id, new double[]{l.getX(), l.getZ(), v == null ? 0 : v[2]});
            // op het ijs (ook in de lucht: springen helpt niet, je glijdt meteen)
            if (v == null || tikken < v[2] || !g.isIjs(l.getBlockX(), l.getBlockZ()) || l.getY() < g.y + 0.6 || l.getY() > g.y + 3.5) continue;
            double dx = l.getX() - v[0], dz = l.getZ() - v[1];
            if (dx * dx + dz * dz < 0.0025) continue;                         // stil (een klein beetje schuiven telt niet)
            Sessie ss = sessie(p);
            if (ss != null && System.currentTimeMillis() - ss.geraakt < 600) continue;   // een duw (een klap) is geen stap
            BlockFace r = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? BlockFace.EAST : BlockFace.WEST) : (dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
            if (!glij(p, g, r)) {                                              // daar staat iets: blijf in je vakje
                Location m = new Location(p.getWorld(), l.getBlockX() + 0.5, g.y + 1, l.getBlockZ() + 0.5, l.getYaw(), l.getPitch());
                if (Math.abs(l.getX() - m.getX()) > 0.3 || Math.abs(l.getZ() - m.getZ()) > 0.3) {
                    p.setVelocity(new org.bukkit.util.Vector());
                    p.teleport(m);
                    glijVorige.put(id, new double[]{m.getX(), m.getZ(), tikken + 2});
                }
            }
        }
    }

    // ── de lichtpijlen (voorwerp in vak 7, eiland 5a) en Morgals nevel ──────────────────────

    /**
     * De lichtpijlen van Zelda's wachters (Nachtrots): rechtsklik = een pijl van licht, en ook de boog schiet nu licht (elke
     * pijl van wie ze heeft). Waar een lichtpijl Morgals nevel raakt, verdwijnt die ({@link #verdrijf}); vijanden van nevel
     * (de nevelmijt) krijgen het dubbele. Verder doet een lichtpijl wat een pijl doet (een kuip van Asgrim, een doelwit).
     */
    final class Lichtpijlen implements ZeldaVoorwerp {
        static final Material ITEM = Material.STICK;            // met het model van een lichtpijl (een spectral arrow): geen munitie voor de boog

        public String id() { return "lichtpijlen"; }
        public String vlag() { return "lichtpijlen"; }
        public int vak() { return 6; }

        public ItemStack item(ZeldaSpel spel, Player p, ZeldaVoortgang.Stand s) {
            ItemStack it = ding(ITEM, "Lichtpijlen", "lichtpijlen");
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(NamespacedKey.minecraft("spectral_arrow"));
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
            lore(it, "rechtsklik: een pijl van licht", "nevel verdwijnt waar hij raakt");
            return it;
        }

        public void gebruik(ZeldaSpel spel, Player p, PlayerInteractEvent e) {
            schiet(p);
        }
    }

    /** Een lichtpijl schieten (rechtsklik met de lichtpijlen): recht vooruit, zo hard als een volle boog. */
    void schiet(Player p) {
        Sessie ss = sessie(p);
        if (ss == null || ss.vaart != null || p.isDead() || p.hasCooldown(Lichtpijlen.ITEM)) return;
        p.setCooldown(Lichtpijlen.ITEM, 12);
        org.bukkit.entity.SpectralArrow a = p.launchProjectile(org.bukkit.entity.SpectralArrow.class, p.getEyeLocation().getDirection().multiply(3.0));
        a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.3f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
    }

    /** De lichtpijlen die nu vliegen (een spoor van licht). */
    final List<AbstractArrow> lichtpijlen = new ArrayList<>();

    /** Een pijl van wie de lichtpijlen heeft, is een lichtpijl (ook uit de boog). */
    @EventHandler(ignoreCancelled = true)
    public void onLichtpijl(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow a) || !(a.getShooter() instanceof Player p) || !speelt(p)) return;
        if (!voortgang.van(p.getUniqueId()).heeft("lichtpijlen")) return;
        a.addScoreboardTag(LICHT);
        a.addScoreboardTag(TAG);
        a.setPersistent(false);
        a.setGlowing(true);
        a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        lichtpijlen.add(a);
    }

    /** Waar elke lichtpijl de vorige tik was (om te zien of hij door een nevelbrug vloog). */
    private final Map<UUID, Location> pijlVorige = new HashMap<>();

    /**
     * Elke tik: het spoor van de lichtpijlen die nog vliegen. Vliegt een lichtpijl door de nevel van een nevelbrug (ergens tussen
     * waar hij was en waar hij nu is), dan verschijnt de brug.
     */
    private void lichtpijlen() {
        for (var it = lichtpijlen.iterator(); it.hasNext(); ) {
            AbstractArrow a = it.next();
            if (!a.isValid() || a.isInBlock() || a.isOnGround() || a.getTicksLived() > 100) {
                it.remove();
                pijlVorige.remove(a.getUniqueId());
                if (a.isValid() && a.getTicksLived() > 100) a.remove();
                continue;
            }
            Location nu = a.getLocation(), was = pijlVorige.put(a.getUniqueId(), nu.clone());
            a.getWorld().spawnParticle(Particle.END_ROD, nu, 1, 0.02, 0.02, 0.02, 0.003);
            if (!(a.getShooter() instanceof Player p) || !speelt(p)) continue;
            ZeldaHoofdstuk.Nevel brug = brugLangs(was == null ? nu : was, nu);
            if (brug != null) verdrijf(p, brug);
        }
    }

    /** Een nevelbrug (nog in de nevel) waar het stuk van {@code van} naar {@code naar} doorheen gaat (binnen een blok), of null. */
    ZeldaHoofdstuk.Nevel brugLangs(Location van, Location naar) {
        org.bukkit.util.Vector d = naar.toVector().subtract(van.toVector());
        int n = Math.max(1, (int) Math.ceil(d.length() / 0.4));
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Nevel nv : h.nevels) {
                if (!nv.brug || nv.nevel.isOpen) continue;
                ZeldaHoofdstuk.Box b = nv.nevel.omvang();                             // de nevel hangt tot een paar blokken boven de brug (waar je hem ziet)
                ZeldaHoofdstuk.Box om = new ZeldaHoofdstuk.Box(b.x1() - 1, b.y1() - 1, b.z1() - 1, b.x2() + 1, b.y2() + 4, b.z2() + 1);
                for (int i = 0; i <= n; i++) {
                    double f = i / (double) n;
                    if (om.in(van.getX() + d.getX() * f, van.getY() + d.getY() * f, van.getZ() + d.getZ() * f)) return nv;
                }
            }
        return null;
    }

    /** Pijlen pak je in Zelda niet op (ze zijn van licht, of komen vanzelf terug). */
    @EventHandler
    public void onPijlOppakken(org.bukkit.event.player.PlayerPickupArrowEvent e) {
        if (speelt(e.getPlayer())) e.setCancelled(true);
    }

    /** De nevel waar dit blok bij hoort (op elk eiland), of null. */
    ZeldaHoofdstuk.Nevel nevelBij(Block b) {
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Nevel n : h.nevels)
                for (int[] c : n.nevel.blokken) if (c[0] == b.getX() && c[1] == b.getY() && c[2] == b.getZ()) return n;
        return null;
    }

    /** Nevel (nog niet weg) vlak bij deze plek: binnen een blok van de nevel (waar een lichtpijl hem net mist), of null. */
    ZeldaHoofdstuk.Nevel nevelBij(Location l) {
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Nevel n : h.nevels) if (!n.nevel.isOpen && n.nevel.omvang().groter(1).in(l)) return n;
        return null;
    }

    private Location binnen(ZeldaHoofdstuk.Nevel n) {
        return n.binnen != null ? n.binnen : n.gebied.midden(plugin.w).add(0, 1, 0);
    }

    /**
     * Een lichtpijl raakt Morgals nevel: hij verdwijnt (lucht), in een flits van licht. Wie erbij is, onthoudt het
     * ({@code n:<id>}) en vindt hem voortaan weg.
     */
    void verdrijf(Player p, ZeldaHoofdstuk.Nevel n) {
        if (n.nevel.isOpen) return;
        var w = p.getWorld();
        ZeldaHoofdstuk.Box om = n.nevel.omvang();
        Location m = om.midden(w).add(0, (om.y2() - om.y1() + 1) / 2.0, 0);
        if (n.brug) toon(n.nevel, p.getLocation());
        else deur(n.nevel, true, binnen(n));
        n.leegSinds = 0;
        w.playSound(m, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
        w.playSound(m, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.8f);
        w.spawnParticle(Particle.END_ROD, m, 40, (om.x2() - om.x1() + 1) / 2.0, (om.y2() - om.y1() + 1) / 2.0, (om.z2() - om.z1() + 1) / 2.0, 0.06);
        w.spawnParticle(Particle.FLASH, m, 1, 0, 0, 0, 0, Color.WHITE);
        List<Player> wie = in(n.gebied);
        if (!wie.contains(p)) wie.add(p);
        for (Player q : wie) if (voortgang.van(q.getUniqueId()).zet("n:" + n.id)) voortgang.vuil();
        muziek.speel(p, ZeldaMuziek.OPGELOST);
    }

    /**
     * Een nevelbrug verschijnt: blok voor blok, vanaf de kant van {@code vanaf} (een paar tikken lang), met een spoor van licht.
     * Hij telt meteen als open (wie er al op stapt, valt niet: de nevel vangt je op).
     */
    private void toon(ZeldaHoofdstuk.Deur d, Location vanaf) {
        d.isOpen = true;
        var w = vanaf.getWorld();
        List<int[]> blokken = new ArrayList<>(d.blokken);
        blokken.sort(java.util.Comparator.comparingDouble(c -> vanaf.distanceSquared(new Location(w, c[0] + 0.5, c[1] + 0.5, c[2] + 0.5))));
        for (int i = 0; i < blokken.size(); i++) {
            int[] c = blokken.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!d.isOpen) return;
                for (Player p : w.getPlayers())                                      // wie er net middenin hangt, komt erop te staan
                    if (p.getLocation().getBlockX() == c[0] && p.getLocation().getBlockZ() == c[2] && p.getLocation().getY() >= c[1] - 1.8 && p.getLocation().getY() < c[1] + 1)
                        p.teleport(p.getLocation().clone().add(0, c[1] + 1 - p.getLocation().getY(), 0));
                w.getBlockAt(c[0], c[1], c[2]).setBlockData(d.open, false);
                w.spawnParticle(Particle.END_ROD, c[0] + 0.5, c[1] + 1.1, c[2] + 0.5, 2, 0.25, 0.1, 0.25, 0.01);
            }, 1L + i / 3);
        }
        int[] m = blokken.get(blokken.size() / 2);
        w.playSound(new Location(w, m[0] + 0.5, m[1] + 0.5, m[2] + 0.5), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
    }

    /** De nevel kruipt terug (dicht). */
    void herstel(ZeldaHoofdstuk.Nevel n) {
        n.leegSinds = 0;
        deur(n.nevel, false, binnen(n));
    }

    /**
     * Elke vijf tikken: weg en een minuut niemand in de buurt = de nevel kruipt terug; wie hem ooit wegschoot, vindt hem weg.
     * Dicht: zwarte nevel die voor de muur hangt en drijft (voor wie in de buurt is).
     */
    private void nevel(ZeldaHoofdstuk.Nevel n, long nu) {
        if (n.nevel.isOpen) {
            if (!in(n.gebied.groter(6)).isEmpty()) n.leegSinds = 0;
            else if (n.leegSinds == 0) n.leegSinds = nu;
            else if (nu - n.leegSinds > 60_000L) herstel(n);
            return;
        }
        for (Player p : in(n.gebied))
            if (voortgang.van(p.getUniqueId()).heeft("n:" + n.id)) {
                n.leegSinds = 0;
                if (n.brug) toon(n.nevel, p.getLocation());
                else deur(n.nevel, true, binnen(n));
                return;
            }
        List<Player> bij = in(n.nevel.omvang().groter(20));
        if (bij.isEmpty()) return;
        Particle.DustOptions zwart = new Particle.DustOptions(Color.fromRGB(0x15121c), 2.4f);
        for (int[] c : n.nevel.blokken) {
            if (rnd.nextInt(3) != 0) continue;
            double x = c[0] + 0.5 + rnd.nextDouble(-1.1, 1.1), y = c[1] + (n.brug ? 0.6 + rnd.nextDouble() * 2.2 : rnd.nextDouble()), z = c[2] + 0.5 + rnd.nextDouble(-1.1, 1.1);
            for (Player p : bij) {
                p.spawnParticle(Particle.DUST, x, y, z, 1, 0.1, 0.15, 0.1, 0, zwart);
                if (rnd.nextInt(6) == 0) p.spawnParticle(Particle.LARGE_SMOKE, x, y, z, 0, 0, 0.02, 0, 1);
            }
        }
    }

    // ── sluipen (eiland 5a): wachters met een lantaarn ──────────────────────────────────────

    /** Waar deze wachter nu staat: zijn ritme loopt sinds er iemand in de buurt kwam (in rust: zijn eerste stand). */
    ZeldaRegels.Pose stand(ZeldaHoofdstuk.Sluip sl, ZeldaHoofdstuk.Sluip.Wachter wa, long nu) {
        double f = sl.begin == 0 ? 0 : Math.floorMod(nu - sl.begin, sl.periode) / (double) sl.periode;
        return ZeldaRegels.pose(wa.standen, f);
    }

    /** Ziet deze wachter (in deze stand) iemand die met zijn voeten op x, y, z staat: in zijn lichtkegel, en niets ertussen? */
    boolean ziet(ZeldaHoofdstuk.Sluip.Wachter wa, ZeldaRegels.Pose po, double x, double y, double z) {
        return ziet(wa, po, x, y, z, 1);
    }

    private boolean ziet(ZeldaHoofdstuk.Sluip.Wachter wa, ZeldaRegels.Pose po, double x, double y, double z, double verder) {
        if (!ZeldaRegels.inKegel(po, wa.bereik * verder, wa.hoek, x, y, z)) return false;
        var w = plugin.w;
        Location oog = new Location(w, po.x(), po.y() + 1.5, po.z());
        for (double h : new double[]{0.3, 1.0, 1.6}) {
            org.bukkit.util.Vector v = new org.bukkit.util.Vector(x, y + h, z).subtract(oog.toVector());
            double d = v.length();
            if (d < 0.3) return true;
            if (w.rayTraceBlocks(oog, v.normalize(), d, org.bukkit.FluidCollisionMode.NEVER, true) == null) return true;
        }
        return false;
    }

    /** Ziet een van de wachters van dit stuk iemand op x, y, z (voeten), op tijdstip {@code nu}? */
    boolean gezien(ZeldaHoofdstuk.Sluip sl, double x, double y, double z, long nu) {
        return gezien(sl, x, y, z, nu, 1);
    }

    /** Idem, met een kortere kegel ({@code verder} × het bereik: wie bukt, zien ze pas van dichtbij, {@link #BUKKEN}). */
    boolean gezien(ZeldaHoofdstuk.Sluip sl, double x, double y, double z, long nu, double verder) {
        for (ZeldaHoofdstuk.Sluip.Wachter wa : sl.wachters) if (ziet(wa, stand(sl, wa, nu), x, y, z, verder)) return true;
        return false;
    }

    /** Wie bukt (sluipt), zien de wachters pas op dit deel van hun bereik. */
    static final double BUKKEN = 0.4;

    /** De cellen van het pad van dit sluipstuk (rechte stukken, elk blok één cel); {@code index[i]} = de cel van punt i. Null = geen recht pad. */
    static List<int[]> cellen(ZeldaHoofdstuk.Sluip sl, int[] index) {
        List<int[]> punten = new ArrayList<>();
        for (Location l : sl.pad) punten.add(new int[]{l.getBlockX(), l.getBlockY(), l.getBlockZ()});
        return ZeldaRegels.cellen(punten, index);
    }

    /**
     * Wordt elke cel gezien, op elke tijdstap van het ritme ({@code stappen} gelijke stappen; [tijd][cel])? Voor de zelftest
     * (kun je erlangs sluipen?) en de test (wanneer ga je).
     */
    boolean[][] zichtTabel(ZeldaHoofdstuk.Sluip sl, List<int[]> cellen, int stappen) {
        boolean[][] t = new boolean[stappen][cellen.size()];
        for (int i = 0; i < stappen; i++) {
            List<ZeldaRegels.Pose> standen = new ArrayList<>();
            for (ZeldaHoofdstuk.Sluip.Wachter wa : sl.wachters) standen.add(ZeldaRegels.pose(wa.standen, i / (double) stappen));
            for (int c = 0; c < cellen.size(); c++) {
                int[] k = cellen.get(c);
                for (int j = 0; j < sl.wachters.size() && !t[i][c]; j++) t[i][c] = ziet(sl.wachters.get(j), standen.get(j), k[0] + 0.5, k[1], k[2] + 0.5);
            }
        }
        return t;
    }

    /** Zijn de wachters van dit stuk er voor deze speler (na de binnenplaats zijn ze gevlucht)? */
    boolean actief(ZeldaHoofdstuk.Sluip sl, Player p) {
        return sl.actief == null || sl.actief.test(voortgang.van(p.getUniqueId()));
    }

    private LivingEntity wachterEnt(ZeldaHoofdstuk.Sluip.Wachter wa, Location l) {
        return l.getWorld().spawn(l, org.bukkit.entity.Pillager.class, e -> {
            e.setPersistent(false);
            e.addScoreboardTag(TAG);
            e.addScoreboardTag(NPC);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setAI(false);
            e.setCollidable(false);
            e.setRemoveWhenFarAway(false);
            e.setCanJoinRaid(false);
            e.setPatrolLeader(false);
            e.customName(Component.text(wa.naam, GROEN));
            e.setCustomNameVisible(false);
            e.getEquipment().setItemInMainHand(new ItemStack(Material.LANTERN));
            e.getEquipment().setDropChance(EquipmentSlot.HAND, 0f);
        });
    }

    /**
     * Elke twee tikken: de wachters van een sluipstuk lopen hun ritme (alleen als er iemand in de buurt is voor wie ze er
     * zijn), wie in een lichtkegel komt is gezien, wie de finish haalt is erlangs geslopen; om de vier tikken de lichtkegels
     * (stipjes op de vloer, niet waar iets het licht tegenhoudt).
     */
    private void sluip(ZeldaHoofdstuk.Sluip sl, long nu) {
        List<Player> bij = new ArrayList<>(), actief = new ArrayList<>();
        for (Player p : in(sl.gebied.groter(24))) {
            Sessie ss = sessie(p);
            if (ss == null || ss.vaart != null) continue;
            bij.add(p);
            if (actief(sl, p)) actief.add(p);
        }
        if (actief.isEmpty()) {                                          // niemand: in rust (geen wachters in de wereld)
            sl.begin = 0;
            for (ZeldaHoofdstuk.Sluip.Wachter wa : sl.wachters)
                if (wa.ent != null) {
                    wa.ent.remove();
                    wa.ent = null;
                }
            if (!sl.hek.isOpen && in(sl.hek.omvang()).isEmpty()) deur(sl.hek, true, sl.terug);
            sl.hekOpen = 0;
            return;
        }
        if (sl.begin == 0) sl.begin = nu;
        if (sl.hekOpen != 0 && nu >= sl.hekOpen) {
            sl.hekOpen = 0;
            deur(sl.hek, true, sl.terug);
        }
        var w = plugin.w;
        for (ZeldaHoofdstuk.Sluip.Wachter wa : sl.wachters) {
            ZeldaRegels.Pose po = stand(sl, wa, nu);
            Location l = new Location(w, po.x(), po.y(), po.z(), (float) po.yaw(), 0f);
            if (wa.ent == null || !wa.ent.isValid()) wa.ent = wachterEnt(wa, l);
            else wa.ent.teleport(l);
            wa.ent.setRotation((float) po.yaw(), 0f);
            wa.ent.setBodyYaw((float) po.yaw());
            for (Player p : bij) {                                       // alleen te zien voor wie ze er nog zijn
                boolean zien = actief.contains(p);
                if (zien && !p.canSee(wa.ent)) p.showEntity(plugin, wa.ent);
                else if (!zien && p.canSee(wa.ent)) p.hideEntity(plugin, wa.ent);
            }
        }
        for (Player p : actief) {
            Sessie ss = sessie(p);
            Location l = p.getLocation();
            if (p.isDead() || beheer.is(p.getUniqueId()) || !sl.gebied.in(l)) continue;
            if (!ss.ongezien && gezien(sl, l.getX(), l.getY(), l.getZ(), nu, p.isSneaking() ? BUKKEN : 1)) {
                gezienDoor(p, ss, sl, nu);
                continue;
            }
            ZeldaVoortgang.Stand s = voortgang.van(p.getUniqueId());
            if (sl.finish.in(l) && s.zet("l:" + sl.id)) {                // erlangs geslopen
                voortgang.vuil();
                muziek.speel(p, ZeldaMuziek.OPGELOST);
                if (ss.rij.isEmpty() && ss.nu == null) vertel(p, ZeldaHoofdstuk.r("Wiske", "Gelukt! Niemand zag je."));
            }
        }
        if (tikken % 4 == 0) kegels(sl, actief, nu);
    }

    /** Gezien: een alarm, het hek gaat even dicht, en je staat weer bij het zielvuur. Geen gevecht. Wiske zegt één regel. */
    private void gezienDoor(Player p, Sessie ss, ZeldaHoofdstuk.Sluip sl, long nu) {
        if (nu < ss.gezienTot) return;
        ss.gezienTot = nu + 2000;
        int n = ss.gezien.merge(sl.id, 1, Integer::sum);
        if (sl.hek.isOpen && in(sl.hek.omvang().groter(0)).isEmpty()) deur(sl.hek, false, sl.terug);
        sl.hekOpen = nu + 4000;
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1.5f);
        p.playSound(p, Sound.ENTITY_PILLAGER_CELEBRATE, 0.6f, 1.2f);
        muziek.speel(p, ZeldaMuziek.GEZIEN);
        trekken.remove(p.getUniqueId());
        glijders.remove(p.getUniqueId());
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
        p.setVelocity(new org.bukkit.util.Vector());
        p.setFallDistance(0);
        p.teleport(sl.terug);
        if (!sl.gezien.isEmpty()) {
            ss.rij.clear();
            ss.nu = null;
            ss.tot = 0;
            vertel(p, sl.gezien.get(Math.min(n, sl.gezien.size()) - 1));
        }
    }

    /** De lichtkegels van de wachters: warme stipjes op de vloer waar hun licht komt (tot het ergens tegenaan schijnt). */
    private void kegels(ZeldaHoofdstuk.Sluip sl, List<Player> wie, long nu) {
        var w = plugin.w;
        Particle.DustOptions licht = new Particle.DustOptions(Color.fromRGB(0xffd27a), 0.9f);
        for (ZeldaHoofdstuk.Sluip.Wachter wa : sl.wachters) {
            ZeldaRegels.Pose po = stand(sl, wa, nu);
            Location oog = new Location(w, po.x(), po.y() + 1.5, po.z());
            for (double a = -wa.hoek; a <= wa.hoek + 0.01; a += wa.hoek / 3) {
                double rad = Math.toRadians(po.yaw() + a), kx = -Math.sin(rad), kz = Math.cos(rad);
                for (double d = 1.2; d <= wa.bereik; d += 1.3) {
                    Location vloer = new Location(w, po.x() + kx * d, po.y() + 0.15, po.z() + kz * d);
                    org.bukkit.util.Vector v = vloer.toVector().subtract(oog.toVector());
                    if (w.rayTraceBlocks(oog, v.clone().normalize(), v.length(), org.bukkit.FluidCollisionMode.NEVER, true) != null) break;
                    for (Player p : wie) p.spawnParticle(Particle.DUST, vloer, 1, 0.15, 0, 0.15, 0, licht);
                }
            }
            Location hand = oog.clone().add(-Math.sin(Math.toRadians(po.yaw())) * 0.5, -0.4, Math.cos(Math.toRadians(po.yaw())) * 0.5);
            for (Player p : wie) p.spawnParticle(Particle.SMALL_FLAME, hand, 1, 0.02, 0.02, 0.02, 0);
        }
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
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && geklikt != null && geklikt.getType() == Material.LEVER && e.getHand() != EquipmentSlot.HAND) {
            e.setCancelled(true);                                    // een hendel gaat alleen om via de hoofdhand (en dan door het getij)
            return;
        }
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
            if (b.getType() == Material.LEVER && hier != null)              // een hendel: eb of vloed
                for (ZeldaHoofdstuk.Getij g : alle(hier, h -> h.getijden)) {
                    ZeldaHoofdstuk.Getij.Hendel hd = g.hendel(b.getX(), b.getY(), b.getZ());
                    if (hd != null) {
                        e.setCancelled(true);
                        trek(p, g, hd);
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
        if (e.getEntity().getScoreboardTags().contains(LICHT)) {             // een lichtpijl: nevel verdwijnt waar hij raakt
            Entity pijl = e.getEntity();
            ZeldaHoofdstuk.Nevel n = e.getHitBlock() != null ? nevelBij(e.getHitBlock()) : null;
            if (n == null || n.nevel.isOpen) n = nevelBij(pijl.getLocation());
            pijl.getWorld().spawnParticle(Particle.END_ROD, pijl.getLocation(), 10, 0.15, 0.15, 0.15, 0.04);
            Bukkit.getScheduler().runTask(plugin, pijl::remove);           // licht blijft niet liggen (na de klap: eerst de schade)
            if (n != null) {
                verdrijf(p, n);
                return;
            }
        }
        if (e.getHitEntity() != null) {
            ZeldaBaas baas = baasVan(e.getHitEntity());
            if (baas != null) baas.geraakt();
        }
        Block b = e.getHitBlock();
        if (b == null) return;
        for (ZeldaHoofdstuk h : hoofdstukken)                       // een baas kan iets met een pijl op een blok (Asgrims kuipen)
            for (ZeldaHoofdstuk.Kamer k : h.kamers)
                if (k.baas != null && k.fase == ZeldaHoofdstuk.Fase.BEZIG && k.baas.leeft() && k.binnen.in(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)
                        && k.baas.raakBlok(this, p, b)) {
                    if (e.getEntity() instanceof AbstractArrow a) a.remove();
                    return;
                }
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
        if (isVijand(doel) && isVijand(bron)) {
            e.setCancelled(true);
            return;
        }
        if (v != null && speelt(v) && d instanceof org.bukkit.entity.Trident && isVijand(bron))     // een drietand: hoogstens twee harten
            e.setDamage(Math.min(e.getDamage(), DRIETAND));
        if (v != null && speelt(v) && isVijand(bron)) e.setDamage(e.getDamage() * ZeldaRegels.SCHADE);   // balans: lichter (ZeldaRegels)
        if (d.getScoreboardTags().contains(LICHT) && doel.getScoreboardTags().contains(NEVEL)) {     // licht tegen nevel: dubbel
            e.setDamage(e.getDamage() * 2);
            doel.getWorld().spawnParticle(Particle.END_ROD, doel.getLocation().add(0, 0.4, 0), 8, 0.2, 0.2, 0.2, 0.05);
        }
        ZeldaBaas baas = baasVan(doel);
        if (baas != null) e.setDamage(baas.schade(e.getDamage(), d));
    }

    /**
     * Een zwaai met je zwaard (of met lege hand): staat je in een gevecht met een baas die er iets mee doet (Morgal: een duistere bol
     * terugslaan), dan krijgt die baas hem.
     */
    @EventHandler(ignoreCancelled = true)
    public void onZwaai(org.bukkit.event.player.PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (e.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING || !speelt(p) || beheer.is(p.getUniqueId())) return;
        for (ZeldaHoofdstuk h : hoofdstukken)
            for (ZeldaHoofdstuk.Kamer k : h.kamers)
                if (k.baas != null && k.fase == ZeldaHoofdstuk.Fase.BEZIG && k.baas.leeft() && k.binnen.in(p.getLocation()) && k.baas.zwaai(this, p)) return;
    }

    /** Vuurwerk van het feest (getagd): doet niemand pijn. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVuurwerk(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof org.bukkit.entity.Firework f && f.getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    /** Hoeveel een drietand van een vijand hoogstens doet (halve harten). */
    static final double DRIETAND = 4;

    /** Drietanden van vijanden: niet opgeslagen, en na het neerkomen snel weg. */
    @EventHandler(ignoreCancelled = true)
    public void onWorp(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Trident t && t.getShooter() instanceof Entity s && isVijand(s)) {
            t.addScoreboardTag(TAG);
            t.setPersistent(false);
            t.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    @EventHandler
    public void onDrietandRaak(ProjectileHitEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Trident t && t.getScoreboardTags().contains(TAG))
            Bukkit.getScheduler().runTaskLater(plugin, t::remove, 30L);
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

    /** Een klap (van wat ook): even niet vanzelf op adem komen. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGeraakt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getFinalDamage() > 0) {
            Sessie ss = sessie(p);
            if (ss != null) ss.geraakt = System.currentTimeMillis();
        }
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
            ss.hitte = 0;
            ss.koelplek = null;
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
        ZeldaBaas baas = baasVan(e.getEntity());
        if (baas != null) baas.weg(e.getEntity().getLocation());
        Player k = e.getEntity().getKiller();
        if (k != null && speelt(k) && rnd.nextInt(10) < 5) {
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
        // een pijl van een vorstschutter maakt je in Zelda niet traag (licht vechten)
        if (e.getCause() == EntityPotionEffectEvent.Cause.ARROW && e.getEntity() instanceof Player p && speelt(p) && e.getAction() != EntityPotionEffectEvent.Action.REMOVED)
            e.setCancelled(true);
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
