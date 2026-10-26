package nl.burggraaf.mc.kitpvp;

import org.bukkit.Color;
import org.bukkit.Material;

/**
 * Uiterlijk van een poort in de hub. {@code plek}: {@code west} (recht tegenover de binnenkomst), {@code zuid}
 * (links) of {@code noord} (rechts). {@code vaandel}: een wandbanier (bv. {@code RED_WALL_BANNER}) links en rechts
 * van de poort, of null.
 */
public record PoortStijl(String plek, Material rand, Material accent, Material licht, Material vloer,
                         Material vaandelLinks, Material vaandelRechts, Color deeltjes) {
}
