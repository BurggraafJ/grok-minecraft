package nl.burggraaf.mc.kitpvp;

import java.util.List;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/** PvP Castle: de kasteelarena ({@link Kasteel}, midden 0/{@link #KZ}) met een kitruimte erachter die over het kasteel uitkijkt. */
final class KasteelModus extends KitModus {

    static final int KZ = -150;

    final Arena kasteel = new Arena("kasteel", "kasteel", "PvP Castle", "pvp");

    private final Kitruimte ruimte = new Kitruimte("kasteel", "kasteel", "kasteel", "PvP Castle", 0, KZ + 31, BlockFace.NORTH, 13,
            new Kitruimte.Palet(Material.STONE_BRICKS, Material.POLISHED_ANDESITE, Material.STONE_BRICKS,
                    Material.DEEPSLATE_BRICKS, Material.SEA_LANTERN, Material.BLUE_CARPET, Material.CHISELED_STONE_BRICKS),
            null, null);

    KasteelModus(KitPvP plugin) {
        super(plugin);
    }

    @Override public String id() { return "kasteel"; }
    @Override public String naam() { return "PvP Castle"; }
    @Override public TextColor kleur() { return TextColor.color(0x6fa8dc); }
    @Override public Material icoon() { return Material.BOW; }
    @Override public String omschrijving() { return "twee kasteelmuren, voor bogen"; }

    @Override
    public PoortStijl poort() {
        return new PoortStijl("noord", Material.STONE_BRICKS, Material.DEEPSLATE_BRICKS, Material.SEA_LANTERN,
                Material.DEEPSLATE_TILES, Material.RED_WALL_BANNER, Material.BLUE_WALL_BANNER, Color.fromRGB(0x5f8fdf));
    }

    @Override public List<Arena> arenas() { return List.of(kasteel); }
    @Override public List<Kitruimte> kitruimtes() { return List.of(ruimte); }

    @Override
    public List<Deel> delen() {
        return List.of(new Deel("kasteel", KitPvP.BOUW_VERSIE, b -> {
            kasteel.spawns.clear();
            Kasteel.bouw(b, KZ, kasteel.spawns);
            ruimte.bouw(b, plugin);
        }));
    }

    @Override
    public void zelftest(List<String> fouten, List<String> info) {
        if (kasteel.spawns.size() != 8) fouten.add("kasteel: " + kasteel.spawns.size() + " spawns (verwacht 8)");
    }
}
