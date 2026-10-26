package nl.burggraaf.mc.kitpvp;

import java.util.List;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Story of Zelda: een avonturenspel (levels, checkpoints, verhaal, alleen of samen) dat in sessie 2 komt. Nu alleen
 * de gesloten grote poort in de hub (west, recht tegenover de binnenkomst) met een bordje «Binnenkort».
 *
 * <p>Sessie 2: zet {@link #status()} op OPEN, geef {@link #delen()} een deel «zelda» dat de map bouwt in
 * {@link #GEBIED} en laat {@link #join} de speler naar binnen brengen met {@link KitPvP#naarSpel}. De poort gaat dan
 * bij de volgende start vanzelf open (de hub hoeft niet herbouwd). Zie {@code MODI.md}.
 */
final class Zelda implements Modus {

    /** Gereserveerd voor de Zelda-map: x 1024..2047, z -512..511 (vier regiobestanden, ver van de rest). */
    static final int[] GEBIED = {1024, -512, 2047, 511};

    private final KitPvP plugin;

    Zelda(KitPvP plugin) {
        this.plugin = plugin;
    }

    @Override public String id() { return "zelda"; }
    @Override public String naam() { return "Story of Zelda"; }
    @Override public TextColor kleur() { return TextColor.color(0x8fcf6a); }
    @Override public Material icoon() { return Material.MAP; }
    @Override public String omschrijving() { return "avontuur · binnenkort"; }
    @Override public Status status() { return Status.BINNENKORT; }

    @Override
    public PoortStijl poort() {
        return new PoortStijl("west", Material.MOSSY_STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.VERDANT_FROGLIGHT,
                Material.MOSSY_STONE_BRICKS, Material.GREEN_WALL_BANNER, Material.GREEN_WALL_BANNER, Color.fromRGB(0x8fcf6a));
    }

    @Override
    public void join(Player p) {
        plugin.binnenkort(p, this);                          // nog dicht (wordt niet aangeroepen zolang BINNENKORT)
    }

    @Override
    public void zelftest(List<String> fouten, List<String> info) {
        info.add("zelda=" + status().name().toLowerCase() + " gebied=" + GEBIED[0] + "," + GEBIED[1] + ".." + GEBIED[2] + "," + GEBIED[3]);
    }
}
