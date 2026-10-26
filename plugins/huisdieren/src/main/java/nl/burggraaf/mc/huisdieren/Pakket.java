package nl.burggraaf.mc.huisdieren;

import org.bukkit.configuration.file.YamlConfiguration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

/**
 * Het reisbriefje van een huisdier: een momentopname van het dier (Paper-entity-NBT), wanneer hij gemaakt is en welk
 * dier het is, ondertekend met HMAC-SHA256. Survival zet het briefje als cookie bij de speler (de client bewaart het
 * zolang hij verbonden is, Velocity geeft cookies gewoon door); lobby en kitpvp lezen het bij binnenkomst en bewaren
 * het zelf. De sleutel is het Velocity-forwarding-geheim uit config/paper-global.yml, dat elke backend al heeft: een
 * speler kan het briefje dus niet vervalsen (anders zou je met een aangepaste client elk wezen kunnen laten spawnen).
 */
final class Pakket {

    static final int MAX = 5120;            // grens van de Minecraft-cookie
    private static final byte VERSIE = 1;

    final long tijd;                        // ms, nieuwer wint
    final String dier;                      // uuid van het echte dier op survival, "" = geen huisdier mee
    final byte[] data;                      // Paper-serializeEntity, leeg = geen

    Pakket(long tijd, String dier, byte[] data) {
        this.tijd = tijd;
        this.dier = dier == null ? "" : dier;
        this.data = data == null ? new byte[0] : data;
    }

    boolean leeg() {
        return dier.isEmpty() || data.length == 0;
    }

    /** Het Velocity-geheim (of null als deze server niet achter Velocity staat). */
    static byte[] sleutel(File serverMap) {
        File f = new File(serverMap, "config/paper-global.yml");
        if (!f.isFile()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (!y.getBoolean("proxies.velocity.enabled", false)) return null;
        String s = y.getString("proxies.velocity.secret", "");
        if (s == null || s.isBlank()) return null;
        return ("huisdieren:" + s).getBytes(StandardCharsets.UTF_8);
    }

    byte[] maak(byte[] sleutel, UUID speler) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeByte(VERSIE);
        out.writeLong(tijd);
        out.writeUTF(dier);
        out.writeInt(data.length);
        out.write(data);
        out.flush();
        byte[] inhoud = buf.toByteArray();
        byte[] mac = mac(sleutel, speler, inhoud);
        byte[] alles = Arrays.copyOf(inhoud, inhoud.length + mac.length);
        System.arraycopy(mac, 0, alles, inhoud.length, mac.length);
        return alles;
    }

    /** Leest en controleert een briefje; null als het niet klopt (verkeerde sleutel, andere speler, kapot). */
    static Pakket lees(byte[] sleutel, UUID speler, byte[] alles) {
        if (alles == null || alles.length < 1 + 8 + 2 + 4 + 32 || alles.length > MAX) return null;
        byte[] inhoud = Arrays.copyOf(alles, alles.length - 32);
        byte[] mac = Arrays.copyOfRange(alles, alles.length - 32, alles.length);
        if (!MessageDigest.isEqual(mac, mac(sleutel, speler, inhoud))) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(inhoud))) {
            if (in.readByte() != VERSIE) return null;
            long t = in.readLong();
            String dier = in.readUTF();
            int n = in.readInt();
            if (n < 0 || n > MAX) return null;
            byte[] data = new byte[n];
            in.readFully(data);
            if (in.available() != 0) return null;
            return new Pakket(t, dier, data);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] mac(byte[] sleutel, UUID speler, byte[] inhoud) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(sleutel, "HmacSHA256"));
            m.update(speler.toString().getBytes(StandardCharsets.UTF_8));
            m.update((byte) 0);
            return m.doFinal(inhoud);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
