package nl.burggraaf.mc.kitpvp;

import java.util.List;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Een modus op kitpvp (Story of Zelda, PvP Arena, PvP Castle, ...). Registreer hem in {@link KitPvP#onEnable} met
 * {@code modi.registreer(...)}: de hub bouwt dan zijn poort (op {@link PoortStijl#plek}), het modusmenu en
 * {@code /modus} tonen hem, en de zelftest controleert hem. Zie {@code MODI.md}.
 *
 * <p>Een modus met kits (arena's en kitruimtes) erft van {@link KitModus}. Een modus met een eigen spel (zoals Zelda
 * straks) zet zijn spelers in {@link Toestand.Plek#SPEL} via {@link KitPvP#naarSpel} en regelt dan zelf schade,
 * honger, doodgaan en grenzen met eigen listeners (registreer die in {@link #aan}).
 */
public interface Modus {

    enum Status { OPEN, BINNENKORT }

    /** Kort, vast, kleine letters (gebruikt in data en commando's). */
    String id();

    /** Zoals spelers hem zien. */
    String naam();

    TextColor kleur();

    Material icoon();

    /** Eén korte grijze regel in het menu (mag leeg). */
    default String omschrijving() {
        return "";
    }

    /** BINNENKORT: poort dicht met een bordje, menu-item grijs; tegen de poort lopen geeft één grijze regel. */
    Status status();

    /** Hoe en waar zijn poort in de hub eruitziet. */
    PoortStijl poort();

    /** Eigen stukken wereld (arena, kitruimte, map). */
    default List<Deel> delen() {
        return List.of();
    }

    default List<Arena> arenas() {
        return List.of();
    }

    default List<Kitruimte> kitruimtes() {
        return List.of();
    }

    /** Bij het aanzetten van de plugin (na het registreren): eigen listeners, taken. */
    default void aan(KitPvP plugin) {
    }

    /** Bij het uitzetten van de plugin. */
    default void uit() {
    }

    /** Elke seconde. */
    default void tik() {
    }

    /** Speler kiest deze modus (poort of menu), alleen als hij OPEN is. Neem hem mee naar binnen. */
    void join(Player p);

    /** Speler verlaat deze modus (naar de hub, een andere modus, of uitloggen). */
    default void leave(Player p) {
    }

    /** Metadata kitpvp-stand voor Huisdieren als de speler in {@link Toestand.Plek#SPEL} van deze modus is. */
    default String stand(Player p, Toestand t) {
        return "wacht";
    }

    /** Respawn na doodgaan in {@link Toestand.Plek#SPEL} van deze modus; null = de hub. */
    default Location respawn(Player p, Toestand t) {
        return null;
    }

    /** Eigen controles voor {@code kitpvp zelftest}: voeg een regel toe aan {@code fouten} als iets niet klopt. */
    default void zelftest(List<String> fouten, List<String> info) {
    }
}
