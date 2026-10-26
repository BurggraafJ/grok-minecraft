package nl.burggraaf.mc.kitpvp;

import java.util.function.Consumer;

/**
 * Een stuk wereld uit code (hub, colosseum, kasteel, ...). {@code bouw} draait bij elke start: dan berekent het deel
 * zijn spawns en zet het zijn labels en standaards neer. Blokken zet het alleen als {@link Bouwer#plaats} aan staat:
 * de eerste keer, bij een hogere {@code versie} of met {@code kitpvp bouw}. De versies staan in {@code gebouwd.yml}.
 * Delen mogen elkaars gebied niet raken (elk deel maakt zijn eigen gebied eerst leeg).
 */
record Deel(String id, int versie, Consumer<Bouwer> bouw) {
}
