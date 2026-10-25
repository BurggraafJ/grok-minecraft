package nl.burggraaf.mc.lobbyreis;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /parkour (/parcours) in survival: onthoud je plek, meld je in de lobby aan als vrijwillige bezoeker (blijft tot
 * /terug, wordt niet automatisch teruggestuurd) en ga via Velocity (BungeeCord-kanaal) naar de lobby.
 * Bij terugkomst in survival zet hij je weer op die plek.
 */
public final class Lobbyreis extends JavaPlugin implements Listener {

    private static final String KANAAL = "BungeeCord";
    private NamespacedKey plekKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        plekKey = new NamespacedKey(this, "terugplek");
        getServer().getMessenger().registerOutgoingPluginChannel(this, KANAAL);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("lobbyreis")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("aftellen")) {
                int n = 5;
                try {
                    if (args.length >= 2) n = Math.max(1, Math.min(10, Integer.parseInt(args[1])));
                } catch (NumberFormatException ignored) {
                }
                aftellen(n);
                sender.sendMessage("aftellen " + n + " s gestart voor " + Bukkit.getOnlinePlayers().size() + " spelers");
            } else {
                sender.sendMessage("Gebruik: /lobbyreis aftellen [seconden]");
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Alleen voor spelers.");
            return true;
        }
        Location l = p.getLocation();
        p.getPersistentDataContainer().set(plekKey, PersistentDataType.STRING,
                l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch());
        p.saveData();
        meldBezoeker(p);
        p.sendMessage(Component.text("Op naar de parkour! Je plek hier is onthouden; typ /terug in de lobby om terug te komen.", NamedTextColor.AQUA));
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buf);
            out.writeUTF("Connect");
            out.writeUTF(getConfig().getString("lobby", "lobby"));
            p.sendPluginMessage(this, KANAAL, buf.toByteArray());
        } catch (IOException e) {
            p.sendMessage(Component.text("Dat lukte niet: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private static final TextColor[] KLEUR = {NamedTextColor.AQUA, NamedTextColor.GREEN, NamedTextColor.YELLOW,
            NamedTextColor.GOLD, NamedTextColor.RED};

    /** Groot aftellen vóór een herlading: titels n..1 met geluid (door mc vlak voor iedereen naar de lobby gaat). */
    private void aftellen(int seconden) {
        for (int i = 0; i < seconden; i++) {
            final int n = seconden - i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    TextColor kleur = KLEUR[Math.min(n, KLEUR.length) - 1];
                    p.showTitle(Title.title(Component.text(String.valueOf(n), kleur, TextDecoration.BOLD),
                            Component.text("Herladen… je wacht even veilig in de lobby", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(150))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f + 0.15f * (seconden - n));
                }
            }, i * 20L);
        }
    }

    private void meldBezoeker(Player p) {
        File f = new File(Bukkit.getWorldContainer(), getConfig().getString("bezoekers-bestand", "../lobby/plugins/LobbyMelding/bezoekers.txt"));
        try {
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), p.getUniqueId() + " " + System.currentTimeMillis() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            getLogger().warning("bezoeker niet te melden: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String s = pdc.get(plekKey, PersistentDataType.STRING);
        if (s == null) return;
        pdc.remove(plekKey);
        String[] d = s.split(";");
        World w = d.length == 6 ? Bukkit.getWorld(d[0]) : null;
        if (w == null) return;
        try {
            Location l = new Location(w, Double.parseDouble(d[1]), Double.parseDouble(d[2]), Double.parseDouble(d[3]),
                    Float.parseFloat(d[4]), Float.parseFloat(d[5]));
            Bukkit.getScheduler().runTask(this, () -> p.teleport(l));
        } catch (NumberFormatException ignored) {
        }
    }
}
