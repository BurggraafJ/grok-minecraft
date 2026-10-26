package nl.burggraaf.mc.kitpvp;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/** De zeven kits: uitrusting (ook voor de pantserstandaards) en wat je in je inventaris krijgt. */
final class Kits {

    private Kits() {
    }

    record Kit(String id, String naam, Material icoon, TextColor kleur, String uitleg) {}

    static final Kit[] KITS = {
            new Kit("krijger", "Krijger", Material.IRON_SWORD, NamedTextColor.WHITE, "IJzeren harnas, zwaard en schild"),
            new Kit("boog", "Boogschutter", Material.BOW, NamedTextColor.GREEN, "Boog (oneindig), maliënkolder, stenen zwaard"),
            new Kit("tank", "Tank", Material.DIAMOND_CHESTPLATE, NamedTextColor.AQUA, "Diamanten borst, bijl en schild, wat trager"),
            new Kit("ninja", "Ninja", Material.FEATHER, NamedTextColor.DARK_GRAY, "Snel en hoog springen, scherp gouden zwaard"),
            new Kit("speer", "Speer", Material.TRIDENT, NamedTextColor.DARK_AQUA, "Drietand die terugkomt, ijzer en maliën"),
            new Kit("heler", "Heler", Material.SPLASH_POTION, NamedTextColor.LIGHT_PURPLE, "Genezende drankjes en regeneratie"),
            new Kit("hamer", "Hamer", Material.MACE, NamedTextColor.GOLD, "Strijdhamer met windladingen: spring en sla!"),
    };

    static Kit kit(String id) {
        for (Kit k : KITS) if (k.id().equals(id)) return k;
        return null;
    }

    static ItemStack ding(Material m, int n) {
        return new ItemStack(m, n);
    }

    static ItemStack betoverd(Material m, Object... ench) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        for (int i = 0; i + 1 < ench.length; i += 2) meta.addEnchant((Enchantment) ench[i], (Integer) ench[i + 1], true);
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    static ItemStack leer(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(c);
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    static ItemStack drank(Material m, PotionType t, int n) {
        ItemStack it = new ItemStack(m, n);
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        meta.setBasePotionType(t);
        it.setItemMeta(meta);
        return it;
    }

    /** helm, borst, broek, schoenen */
    static ItemStack[] uitrusting(String id) {
        return switch (id) {
            case "krijger" -> new ItemStack[]{betoverd(Material.IRON_HELMET), betoverd(Material.IRON_CHESTPLATE), betoverd(Material.IRON_LEGGINGS), betoverd(Material.IRON_BOOTS)};
            case "boog" -> new ItemStack[]{betoverd(Material.CHAINMAIL_HELMET), betoverd(Material.CHAINMAIL_CHESTPLATE), betoverd(Material.CHAINMAIL_LEGGINGS), betoverd(Material.CHAINMAIL_BOOTS)};
            case "tank" -> new ItemStack[]{betoverd(Material.IRON_HELMET), betoverd(Material.DIAMOND_CHESTPLATE), betoverd(Material.IRON_LEGGINGS), betoverd(Material.IRON_BOOTS)};
            case "ninja" -> new ItemStack[]{leer(Material.LEATHER_HELMET, Color.fromRGB(0x202020)), leer(Material.LEATHER_CHESTPLATE, Color.fromRGB(0x202020)),
                    leer(Material.LEATHER_LEGGINGS, Color.fromRGB(0x202020)), betoverd(Material.LEATHER_BOOTS, Enchantment.FEATHER_FALLING, 3)};
            case "speer" -> new ItemStack[]{betoverd(Material.CHAINMAIL_HELMET), betoverd(Material.IRON_CHESTPLATE), betoverd(Material.CHAINMAIL_LEGGINGS), betoverd(Material.CHAINMAIL_BOOTS)};
            case "heler" -> new ItemStack[]{betoverd(Material.GOLDEN_HELMET), betoverd(Material.GOLDEN_CHESTPLATE), betoverd(Material.GOLDEN_LEGGINGS), betoverd(Material.GOLDEN_BOOTS)};
            case "hamer" -> new ItemStack[]{betoverd(Material.IRON_HELMET), betoverd(Material.CHAINMAIL_CHESTPLATE), betoverd(Material.IRON_LEGGINGS), betoverd(Material.IRON_BOOTS, Enchantment.FEATHER_FALLING, 2)};
            default -> new ItemStack[4];
        };
    }

    static void geef(Player p, String id) {
        var inv = p.getInventory();
        inv.clear();
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        ItemStack[] uit = uitrusting(id);
        inv.setHelmet(uit[0]);
        inv.setChestplate(uit[1]);
        inv.setLeggings(uit[2]);
        inv.setBoots(uit[3]);
        switch (id) {
            case "krijger" -> {
                inv.setItem(0, betoverd(Material.IRON_SWORD, Enchantment.SHARPNESS, 1));
                inv.setItemInOffHand(betoverd(Material.SHIELD));
            }
            case "boog" -> {
                inv.setItem(0, betoverd(Material.STONE_SWORD));
                inv.setItem(1, betoverd(Material.BOW, Enchantment.POWER, 2, Enchantment.INFINITY, 1, Enchantment.PUNCH, 1));
                inv.setItem(9, ding(Material.ARROW, 1));
            }
            case "tank" -> {
                inv.setItem(0, betoverd(Material.IRON_AXE));
                inv.setItemInOffHand(betoverd(Material.SHIELD));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            }
            case "ninja" -> {
                inv.setItem(0, betoverd(Material.GOLDEN_SWORD, Enchantment.SHARPNESS, 3));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, 1, false, false, true));
            }
            case "speer" -> {
                inv.setItem(0, betoverd(Material.TRIDENT, Enchantment.LOYALTY, 3, Enchantment.IMPALING, 1));
                inv.setItem(1, betoverd(Material.STONE_SWORD));
            }
            case "heler" -> {
                inv.setItem(0, betoverd(Material.STONE_SWORD, Enchantment.SHARPNESS, 1));
                inv.setItem(1, drank(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 4));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            }
            case "hamer" -> {
                inv.setItem(0, betoverd(Material.MACE, Enchantment.DENSITY, 1));
                inv.setItem(1, ding(Material.WIND_CHARGE, 6));
                inv.setItem(2, betoverd(Material.STONE_SWORD));
            }
            default -> { }
        }
        inv.setItem(8, ding(Material.GOLDEN_APPLE, 1));
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(18);
        p.setSaturation(0f);
    }

    /** Beloning bij een kill (alleen als de killer nog in een arena staat). */
    static void beloon(Player killer, String kit) {
        if (!killer.getInventory().containsAtLeast(new ItemStack(Material.GOLDEN_APPLE), 3)) killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
        if ("hamer".equals(kit)) killer.getInventory().addItem(new ItemStack(Material.WIND_CHARGE, 2));
        if ("heler".equals(kit)) killer.getInventory().addItem(drank(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1));
    }
}
