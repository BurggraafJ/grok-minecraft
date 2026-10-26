package nl.burggraaf.mc.huisdieren;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;

import java.util.UUID;

/**
 * `huisdier zelftest` (console): momentopname → briefje → controle → terug naar een dier, zonder iets in de wereld te
 * laten staan. Met een spelernaam op lobby/kitpvp: zet een testwolf als zijn huisdier (alleen voor tests).
 */
final class Zelftest {

    static String draai(Huisdieren plugin, String spelerNaam) {
        StringBuilder sb = new StringBuilder("huisdieren zelftest (" + plugin.modus() + "):");
        byte[] sleutel = Pakket.sleutel(plugin.getServer().getWorldContainer().getAbsoluteFile());
        sb.append(" sleutel=").append(sleutel != null);
        if (sleutel == null) sleutel = "test".getBytes();
        Location l = Bukkit.getWorlds().get(0).getSpawnLocation();
        UUID eigenaar = UUID.randomUUID();
        Wolf w = l.getWorld().spawn(l, Wolf.class, x -> {
            x.setPersistent(false);
            x.setTamed(true);
            x.setOwner(Bukkit.getOfflinePlayer(eigenaar));
            x.customName(net.kyori.adventure.text.Component.text("Testwolf"));
        });
        try {
            byte[] data = Bukkit.getUnsafe().serializeEntity(w, io.papermc.paper.entity.EntitySerializationFlag.FORCE);
            Pakket p = new Pakket(System.currentTimeMillis(), w.getUniqueId().toString(), data);
            byte[] b = p.maak(sleutel, eigenaar);
            sb.append(" nbt=").append(data.length).append("B briefje=").append(b.length).append("B");
            Pakket terug = Pakket.lees(sleutel, eigenaar, b);
            sb.append(" lees=").append(terug != null && terug.dier.equals(p.dier) && java.util.Arrays.equals(terug.data, data));
            sb.append(" ander-speler-geweigerd=").append(Pakket.lees(sleutel, UUID.randomUUID(), b) == null);
            b[20] ^= 1;
            sb.append(" vervalst-geweigerd=").append(Pakket.lees(sleutel, eigenaar, b) == null);
            Entity e = Bukkit.getUnsafe().deserializeEntity(data, l.getWorld(), false);
            sb.append(" terug=").append(e instanceof Wolf t && t.isTamed() && eigenaar.equals(t.getOwnerUniqueId())
                    && "Testwolf".equals(plugin.naam(t)));
            if (spelerNaam != null && plugin.kopie() != null) {
                Player sp = Bukkit.getPlayerExact(spelerNaam);
                if (sp == null) {
                    sb.append(" speler-niet-online");
                } else {
                    w.setOwner(sp);
                    byte[] echt = new Pakket(System.currentTimeMillis(), w.getUniqueId().toString(), Bukkit.getUnsafe().serializeEntity(w, io.papermc.paper.entity.EntitySerializationFlag.FORCE)).maak(sleutel, sp.getUniqueId());
                    plugin.kopie().testOntvang(sp, echt);
                    sb.append(" kopie-gezet ").append(plugin.kopie().status());
                }
            }
        } catch (Exception ex) {
            sb.append(" FOUT ").append(ex);
        } finally {
            w.remove();
        }
        return sb.toString();
    }
}
