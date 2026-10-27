package nl.burggraaf.mc.kitpvp;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Het modusmenu (kompas, /modus) en het kitmenu (ster, /kit), plus de items in de hotbar. */
final class Menus {

    /** Herkenning van onze menu's. */
    static final class Menu implements InventoryHolder {
        final String soort;
        Inventory inv;

        Menu(String soort) {
            this.soort = soort;
        }

        public Inventory getInventory() {
            return inv;
        }
    }

    private final KitPvP plugin;

    Menus(KitPvP plugin) {
        this.plugin = plugin;
    }

    private static Component tekst(String s, net.kyori.adventure.text.format.TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material m, Component naam, List<Component> lore, String soort) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(naam);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        if (soort != null) meta.getPersistentDataContainer().set(plugin.itemKey, PersistentDataType.STRING, soort);
        it.setItemMeta(meta);
        return it;
    }

    private Inventory leeg(Menu m, Component titel) {
        m.inv = Bukkit.createInventory(m, 27, titel);
        ItemStack glas = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glas.getItemMeta();
        gm.setHideTooltip(true);
        glas.setItemMeta(gm);
        for (int i = 0; i < 27; i++) m.inv.setItem(i, glas);
        return m.inv;
    }

    /** Vakken in het midden van een rij van 9 voor n dingen (met een leeg vak ertussen als het past). */
    static int[] midden(int n, int rij) {
        int[] v = new int[n];
        int stap = n <= 4 ? 2 : 1, breed = (n - 1) * stap, start = rij * 9 + (8 - breed) / 2;
        for (int i = 0; i < n; i++) v[i] = start + i * stap;
        return v;
    }

    void modusMenu(Player p) {
        Menu m = new Menu("modus");
        Inventory inv = leeg(m, Component.text("Kies een modus", NamedTextColor.DARK_GRAY));
        List<Modus> alle = plugin.modi.alle();
        int[] vak = midden(alle.size(), 1);
        for (int i = 0; i < alle.size(); i++) {
            Modus mo = alle.get(i);
            boolean open = mo.status() == Modus.Status.OPEN;
            List<Component> lore = new ArrayList<>();
            if (!mo.omschrijving().isEmpty()) lore.add(tekst(mo.omschrijving(), NamedTextColor.GRAY));
            if (!open && !mo.omschrijving().contains("binnenkort")) lore.add(tekst("binnenkort", NamedTextColor.DARK_GRAY));
            ItemStack it = item(mo.icoon(), tekst(mo.naam(), open ? mo.kleur() : NamedTextColor.GRAY), lore, "modus:" + mo.id());
            inv.setItem(vak[i], it);
        }
        p.openInventory(inv);
    }

    void kitMenu(Player p, Kitruimte r, String gekozenArena) {
        Menu m = new Menu("kit");
        Inventory inv = leeg(m, Component.text("Kies je kit", NamedTextColor.DARK_GRAY));
        for (int i = 0; i < Kits.KITS.length; i++) {
            Kits.Kit k = Kits.KITS[i];
            ItemStack it = item(k.icoon(), tekst(k.naam(), k.kleur()), List.of(tekst(k.uitleg(), NamedTextColor.GRAY)), null);
            ItemMeta meta = it.getItemMeta();
            meta.getPersistentDataContainer().set(plugin.kitKey, PersistentDataType.STRING, k.id());
            it.setItemMeta(meta);
            inv.setItem(10 + i, it);
        }
        List<Arena> arenas = plugin.modi.arenasVan(r.modus);
        if (arenas.size() > 1) {                              // bv. PvP Arena: colosseum of oefenarena
            int[] vak = midden(arenas.size(), 2);
            for (int i = 0; i < arenas.size(); i++) {
                Arena a = arenas.get(i);
                boolean gekozen = a.id.equals(gekozenArena);
                ItemStack it = item(a.bots() ? Material.ZOMBIE_HEAD : Material.IRON_SWORD, tekst(a.naam, gekozen ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                        List.of(tekst(gekozen ? "gekozen" : "klik om te kiezen", NamedTextColor.DARK_GRAY)), "arena:" + a.id);
                if (gekozen) {
                    ItemMeta meta = it.getItemMeta();
                    meta.setEnchantmentGlintOverride(true);
                    it.setItemMeta(meta);
                }
                inv.setItem(vak[i], it);
            }
        }
        p.openInventory(inv);
    }

    // ── hotbar ───────────────────────────────────────────────────────────────────────────────

    ItemStack kompas() {
        return item(Material.COMPASS, tekst("Modus kiezen", NamedTextColor.GOLD), List.of(tekst("of /modus", NamedTextColor.GRAY)), "modus");
    }

    ItemStack ster() {
        return item(Material.NETHER_STAR, tekst("Kit kiezen", NamedTextColor.GOLD), List.of(tekst("of /kit", NamedTextColor.GRAY)), "kies");
    }

    /** Vak 8 in een arena en in Zelda: terug naar de hub en de moduskeuze (niet tijdens een gevecht). */
    ItemStack hub() {
        return item(Material.RECOVERY_COMPASS, tekst("Terug naar de hub", NamedTextColor.GRAY), List.of(tekst("of /arena · niet tijdens een gevecht", NamedTextColor.DARK_GRAY)), "hub");
    }

    static final int HUB_VAK = 7;

    ItemStack deur() {
        return item(Material.OAK_DOOR, tekst("Naar de lobby", NamedTextColor.AQUA), List.of(tekst("of /lobby, /survival", NamedTextColor.GRAY)), "lobby");
    }
}
