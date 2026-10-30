package nl.burggraaf.mc.zelda;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** De verhuizing uit KitPvP: alles komt over, er gaat niets weg, en een tweede keer doet niets. */
class ZeldaMigratieTest {

    private static final Logger LOG = Logger.getLogger("test");

    @TempDir
    Path tmp;

    private File kitpvp() throws IOException {
        File k = tmp.resolve("plugins/KitPvP").toFile();
        k.mkdirs();
        // een echte stand van 2.16: twee spellen, een niveau, hartstukjes en een kaart
        ZeldaVoortgang v = new ZeldaVoortgang(new File(k, "zelda.yml"));
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        v.van(id).bereik("h1", 3);
        v.van(id).zet("zwaard");
        v.van(id).zet("hs:lindewoud-1");
        v.van(id).laatste = "h1:2";
        v.van(id).niveau = ZeldaRegels.Niveau.HELD.ordinal();
        v.nieuw(id, 2, null).zet("boog");
        v.bewaar();
        Files.writeString(new File(k, "zelda-kaarten.yml").toPath(), "00000000-0000-0000-0000-000000000001: 7\n");
        new File(k, "zelda-kaart").mkdirs();
        Files.writeString(new File(k, "zelda-kaart/lindewoud.bin").toPath(), "gemeten");
        Files.writeString(new File(k, "stats.yml").toPath(), "kills: 3\n");
        YamlConfiguration g = new YamlConfiguration();
        g.set("grond", -60);
        g.set("delen.hub", 1);
        g.set("delen.arena", 4);
        g.set("delen.zelda", 4);
        g.set("delen.zelda-e5b", 1);
        g.set("delen.zelda-zee", 2);
        g.save(new File(k, "gebouwd.yml"));
        return k;
    }

    @Test
    void voortgangKomtOverEnDeOudeBlijft() throws IOException {
        File k = kitpvp();
        byte[] oud = Files.readAllBytes(new File(k, "zelda.yml").toPath());
        File z = tmp.resolve("plugins/Zelda").toFile();
        ZeldaMigratie m = new ZeldaMigratie(z, k, LOG);
        assertTrue(m.data().startsWith("overgezet"));
        assertArrayEquals(oud, Files.readAllBytes(new File(z, "zelda.yml").toPath()));
        assertTrue(new File(z, "zelda-kaarten.yml").isFile());
        assertTrue(new File(z, "zelda-kaart/lindewoud.bin").isFile());
        assertFalse(new File(z, "stats.yml").exists(), "KitPvP-statistieken blijven bij KitPvP");
        // de oude bestanden staan er nog, en er is een backup
        assertArrayEquals(oud, Files.readAllBytes(new File(k, "zelda.yml").toPath()));
        File[] backups = new File(z, "migratie").listFiles();
        assertEquals(1, backups.length);
        assertArrayEquals(oud, Files.readAllBytes(new File(backups[0], "zelda.yml").toPath()));
        // en het nieuwe bestand leest als dezelfde stand
        ZeldaVoortgang v = new ZeldaVoortgang(new File(z, "zelda.yml"));
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals(2, v.spellen(id).size());
        assertTrue(v.spellen(id).get(1).heeft("hs:lindewoud-1"));
        assertEquals(ZeldaRegels.Niveau.HELD, ZeldaRegels.niveau(v.spellen(id).get(1)));
        assertTrue(v.spellen(id).get(2).heeft("boog"));
    }

    @Test
    void eenTweedeKeerDoetNietsEnOverschrijftNiets() throws IOException {
        File k = kitpvp();
        File z = tmp.resolve("plugins/Zelda").toFile();
        new ZeldaMigratie(z, k, LOG).data();
        Files.writeString(new File(z, "zelda.yml").toPath(), "nieuw: gespeeld\n");          // er werd daarna in Zelda gespeeld
        String tweede = new ZeldaMigratie(z, k, LOG).data();
        assertTrue(tweede.startsWith("overgezet"), "de stand van de eerste keer: " + tweede);
        assertEquals("nieuw: gespeeld\n", Files.readString(new File(z, "zelda.yml").toPath()));
        assertEquals(1, new File(z, "migratie").listFiles().length, "geen tweede backup");
    }

    @Test
    void staatErAlEenZeldaBestandDanBlijftDat() throws IOException {
        File k = kitpvp();
        File z = tmp.resolve("plugins/Zelda").toFile();
        z.mkdirs();
        Files.writeString(new File(z, "zelda.yml").toPath(), "al: hier\n");
        assertTrue(new ZeldaMigratie(z, k, LOG).data().contains("stond er al"));
        assertEquals("al: hier\n", Files.readString(new File(z, "zelda.yml").toPath()));
    }

    @Test
    void zonderKitpvpIsErNietsTeDoen() {
        File z = tmp.resolve("plugins/Zelda").toFile();
        ZeldaMigratie m = new ZeldaMigratie(z, tmp.resolve("plugins/KitPvP").toFile(), LOG);
        assertEquals("geen oude voortgang", m.data());
        assertTrue(m.wereldNodig());
        m.wereld(tmp.resolve("leeg").toFile(), tmp.resolve("zelda").toFile());
        assertFalse(m.wereldNodig());
        assertFalse(new File(z, "gebouwd.yml").exists(), "zonder oude eilanden wordt alles gebouwd");
    }

    @Test
    void deVierRegiosEnDeBouwversiesKomenMee() throws IOException {
        File k = kitpvp();
        File van = tmp.resolve("world/dimensions/minecraft/overworld").toFile(), naar = tmp.resolve("world/dimensions/minecraft/zelda").toFile();
        for (String soort : ZeldaMigratie.SOORTEN) {
            new File(van, soort).mkdirs();
            for (int[] r : ZeldaMigratie.REGIOS) Files.writeString(new File(van, soort + "/r." + r[0] + "." + r[1] + ".mca").toPath(), soort + r[0] + r[1]);
            Files.writeString(new File(van, soort + "/r.-1.0.mca").toPath(), "kitpvp-hub");   // de hub en arena's: blijven waar ze zijn
        }
        new File(naar, "region").mkdirs();
        Files.writeString(new File(naar, "region/r.0.0.mca").toPath(), "nieuwe wereld");
        File z = tmp.resolve("plugins/Zelda").toFile();
        ZeldaMigratie m = new ZeldaMigratie(z, k, LOG);
        m.wereld(van, naar);
        for (String soort : ZeldaMigratie.SOORTEN)
            for (int[] r : ZeldaMigratie.REGIOS)
                assertEquals(soort + r[0] + r[1], Files.readString(new File(naar, soort + "/r." + r[0] + "." + r[1] + ".mca").toPath()));
        assertFalse(new File(naar, "region/r.-1.0.mca").exists());
        assertEquals("nieuwe wereld", Files.readString(new File(naar, "region/r.0.0.mca").toPath()));
        assertTrue(new File(van, "region/r.2.0.mca").isFile(), "de kitpvp-wereld houdt alles");
        YamlConfiguration g = YamlConfiguration.loadConfiguration(new File(z, "gebouwd.yml"));
        assertEquals(-60, g.getInt("grond"));
        assertEquals(4, g.getInt("delen.zelda"));
        assertEquals(1, g.getInt("delen.zelda-e5b"));
        assertEquals(2, g.getInt("delen.zelda-zee"));
        assertFalse(g.contains("delen.hub") || g.contains("delen.arena"), "KitPvP-delen horen niet bij Zelda");
        assertFalse(m.wereldNodig());
    }

    @Test
    void eenRegioDieErAlStaatWordtNietOverschrevenEnDanWordtErGebouwd() throws IOException {
        File k = kitpvp();
        File van = tmp.resolve("oud").toFile(), naar = tmp.resolve("nieuw").toFile();
        new File(van, "region").mkdirs();
        new File(naar, "region").mkdirs();
        for (int[] r : ZeldaMigratie.REGIOS) Files.writeString(new File(van, "region/r." + r[0] + "." + r[1] + ".mca").toPath(), "oud");
        Files.writeString(new File(naar, "region/r.2.0.mca").toPath(), "al hier");
        File z = tmp.resolve("plugins/Zelda").toFile();
        new ZeldaMigratie(z, k, LOG).wereld(van, naar);
        assertEquals("al hier", Files.readString(new File(naar, "region/r.2.0.mca").toPath()));
        assertFalse(YamlConfiguration.loadConfiguration(new File(z, "gebouwd.yml")).contains("delen.zelda"), "half overgezet: alles opnieuw bouwen");
    }

    @Test
    void alleenZeldaDelen() {
        assertTrue(ZeldaMigratie.zeldaDeel("zelda"));
        assertTrue(ZeldaMigratie.zeldaDeel("zelda-e3b"));
        assertTrue(ZeldaMigratie.zeldaDeel("zelda-zee"));
        assertFalse(ZeldaMigratie.zeldaDeel("hub"));
        assertFalse(ZeldaMigratie.zeldaDeel("kasteel"));
    }
}
