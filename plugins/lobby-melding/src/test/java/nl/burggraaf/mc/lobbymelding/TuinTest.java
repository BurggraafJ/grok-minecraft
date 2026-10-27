package nl.burggraaf.mc.lobbymelding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** De hele tuin getekend zoals op de server (grond −61) en nagerekend. */
class TuinTest {

    static Tuin t;

    @BeforeAll
    static void teken() {
        t = new Tuin(-61);
        t.bouw();
    }

    @Test
    void tienLevelsElkMetEigenNaam() {
        assertEquals(10, t.banen.size());
        Set<String> namen = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Baan b = t.banen.get(i);
            assertEquals(i + 1, b.nr);
            assertTrue(namen.add(b.naam));
            assertTrue(b.moeite() >= 1 && b.moeite() <= 5);
        }
        // moeilijker naarmate je verder komt
        for (int i = 1; i < 10; i++) assertTrue(t.banen.get(i).moeite() >= t.banen.get(i - 1).moeite());
    }

    @Test
    void elkeSprongHaalbaarEnVrij() {
        List<String> fout = new ArrayList<>();
        for (Baan b : t.banen) fout.addAll(b.controleer(t.p));
        assertTrue(fout.isEmpty(), String.join("\n", fout));
    }

    @Test
    void startEnFinishDrieBijDrie() {
        for (Baan b : t.banen) {
            Baan.Stap s = b.start(), f = b.finish();
            assertEquals(3, s.x2() - s.x1() + 1, b.naam);
            assertEquals(3, s.z2() - s.z1() + 1, b.naam);
            assertEquals(3, f.x2() - f.x1() + 1, b.naam);
            assertEquals(3, f.z2() - f.z1() + 1, b.naam);
            // het midden is groen bij de start en goud bij de finish
            assertEquals("lime_concrete", t.p.naam((s.x1() + s.x2()) / 2, s.y(), (s.z1() + s.z2()) / 2));
            assertEquals("gold_block", t.p.naam((f.x1() + f.x2()) / 2, f.y(), (f.z1() + f.z2()) / 2));
            // je staat bij de start midden op het vlak
            assertEquals((s.x1() + s.x2()) / 2 + 0.5, b.sx, 1e-9);
            assertEquals((s.z1() + s.z2()) / 2 + 0.5, b.sz, 1e-9);
            assertEquals(s.voet(), b.sy, 1e-9);
        }
    }

    @Test
    void elkeBaanLigtHogerDanDeGrond() {
        // grondcontact = voeten lager dan g + 2; dus moet elk vlak waar je op staat minstens op g + 1 liggen
        for (Baan b : t.banen)
            for (Baan.Stap s : b.stappen)
                if (s.soort() != Baan.Soort.LADDER) assertTrue(s.voet() >= t.g + 2, b.naam + " " + s);
    }

    @Test
    void geenTweeLevelsOpDezelfdePlek() {
        Set<Long> gezien = new HashSet<>();
        for (Baan b : t.banen)
            for (Baan.Stap s : b.stappen)
                for (int x = s.x1(); x <= s.x2(); x++)
                    for (int z = s.z1(); z <= s.z2(); z++)
                        for (int y = s.y(); y <= Math.max(s.y(), s.top()); y++)
                            assertTrue(gezien.add(((long) x << 40) ^ ((long) z << 20) ^ (y & 0xfffff)), b.naam + " overlapt op " + x + "," + y + "," + z);
    }

    @Test
    void allesBinnenDeTekening() {
        for (Baan b : t.banen)
            for (Baan.Stap s : b.stappen) {
                assertTrue(t.p.binnen(s.x1(), s.y(), s.z1()) && t.p.binnen(s.x2(), s.y() + 3, s.z2()), b.naam + " buiten de tekening: " + s);
                assertTrue(Math.abs(s.x1()) <= Tuin.RAND + 1 && Math.abs(s.x2()) <= Tuin.RAND + 1 && Math.abs(s.z1()) <= Tuin.RAND + 1
                        && Math.abs(s.z2()) <= Tuin.RAND + 1, b.naam + " buiten de tuinrand: " + s);
            }
    }

    @Test
    void poortenVrijEnDichtBijDeSpawn() {
        assertEquals(2, t.poorten.size());
        Set<String> servers = new HashSet<>();
        for (Tuin.Poort p : t.poorten) {
            servers.add(p.server());
            assertEquals(3, p.x2() - p.x1() + 1);
            for (int x = p.x1(); x <= p.x2(); x++)
                for (int h = 1; h <= 3; h++) assertTrue(t.p.doorloopbaar(x, t.g + h, p.z()), "poort " + p.server() + " dicht");
            double afstand = Math.hypot((p.x1() + p.x2()) / 2.0 + 0.5 - t.spawnX, p.z() + 0.5 - t.spawnZ);
            assertTrue(afstand < 20, "poort te ver van de spawn: " + afstand);
            // vanaf de spawn zie je hem: recht vooruit (noorden), niet meer dan 45° opzij
            assertTrue(p.z() < t.spawnZ);
            assertTrue(Math.abs((p.x1() + p.x2()) / 2.0 + 0.5 - t.spawnX) < Math.abs(p.z() - t.spawnZ));
        }
        assertEquals(Set.of("survival", "kitpvp"), servers);
        // de spawn zelf: vrij, op een vloer
        int sx = (int) Math.floor(t.spawnX), sz = (int) Math.floor(t.spawnZ);
        assertTrue(t.p.doorloopbaar(sx, t.g + 1, sz) && t.p.doorloopbaar(sx, t.g + 2, sz));
        assertFalse(t.p.doorloopbaar(sx, t.g, sz));
    }

    @Test
    void allesHeeftEenBestaandBlok() {
        // elke bloknaam in de tekening bestaat in deze Minecraft-versie (de toestanden controleert de server bij het bouwen)
        List<String> onbekend = new ArrayList<>();
        for (String s : t.p.palet()) {
            String n = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
            if (Material.matchMaterial(n) == null) onbekend.add(n);
        }
        assertTrue(onbekend.isEmpty(), "onbekend: " + onbekend);
    }

    @Test
    void zelfdeTekeningElkeKeer() {
        Tuin t2 = new Tuin(-61);
        t2.bouw();
        for (int x = t.p.x1; x <= t.p.x2; x += 3)
            for (int z = t.p.z1; z <= t.p.z2; z += 3)
                for (int y = t.p.y1; y <= t.p.y2; y++) assertEquals(t.p.get(x, y, z), t2.p.get(x, y, z));
    }

    @Test
    void borden() {
        // elk level heeft een startbord met zijn nummer en naam
        for (Baan b : t.banen) {
            boolean gevonden = false;
            for (Plan.Bord bd : t.p.borden)
                if (bd.regels()[0].equals("Parkour " + b.nr) && bd.regels()[1].equals(b.naam)) {
                    gevonden = true;
                    assertTrue(t.p.naam(bd.x(), bd.y(), bd.z()).endsWith("_sign"));
                }
            assertTrue(gevonden, "geen bord bij " + b.naam);
        }
        for (Plan.Bord bd : t.p.borden) for (String r : bd.regels()) assertTrue(r.length() <= 15, "te lange regel: " + r);
    }
}
