package nl.burggraaf.mc.kitpvp;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Kitruimte van een modus: een galerij van 11 breed en 21 diep, hoog boven de grond, die uitkijkt over de arena.
 * Je komt achterin binnen, loopt tussen de zeven pantserstandaards door (4 links, 3 rechts) naar het balkon; klik
 * op een standaard = die kit, en je springt in {@link #arena}. Achterin een deur terug naar de hub, eventueel een
 * zijdeur naar een andere kitruimte (PvP Arena ↔ Oefenarena).
 *
 * <p>Coördinaten in de ruimte: f vooruit (naar de arena, balkon op f = 0), s naar rechts. De vloer ligt op
 * g + {@code hoogte}.
 */
final class Kitruimte {

    record Palet(Material vloer, Material vloer2, Material muur, Material rand, Material licht, Material loper, Material sokkel) {}

    private static final int[] LINKS = {-17, -13, -9, -5}, RECHTS = {-15, -11, -7};

    final String id, modus, arena, naam;
    final int x0, z0, hoogte;
    final BlockFace kijk;
    final Palet palet;
    final String zijdeur, zijdeurLabel;           // kitruimte-id achter de zijdeur, of null
    private final int fx, fz, rx, rz;

    private World w;
    private int y;
    Location aankomst;

    Kitruimte(String id, String modus, String arena, String naam, int x0, int z0, BlockFace kijk, int hoogte, Palet palet,
              String zijdeur, String zijdeurLabel) {
        this.id = id;
        this.modus = modus;
        this.arena = arena;
        this.naam = naam;
        this.x0 = x0;
        this.z0 = z0;
        this.kijk = kijk;
        this.hoogte = hoogte;
        this.palet = palet;
        this.zijdeur = zijdeur;
        this.zijdeurLabel = zijdeurLabel;
        fx = kijk.getModX();
        fz = kijk.getModZ();
        rx = -fz;
        rz = fx;
    }

    private int wx(int f, int s) {
        return x0 + f * fx + s * rx;
    }

    private int wz(int f, int s) {
        return z0 + f * fz + s * rz;
    }

    private Location punt(double f, double s, double yy) {
        return new Location(w, x0 + f * fx + s * rx + 0.5, yy, z0 + f * fz + s * rz + 0.5);
    }

    private static BlockFace face(int x, int z) {
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            if (f.getModX() == x && f.getModZ() == z) return f;
        throw new IllegalArgumentException(x + "," + z);
    }

    private Bouwer b;

    private void k(int f, int s, int yy, Material m) {
        b.zet(wx(f, s), yy, wz(f, s), m);
    }

    private void k(int f, int s, int yy, org.bukkit.block.data.BlockData d) {
        b.zet(wx(f, s), yy, wz(f, s), d);
    }

    /** Bouw (als het deel herbouwd wordt) en zet de standaards en labels neer (altijd). */
    void bouw(Bouwer b, KitPvP plugin) {
        this.b = b;
        this.w = b.w;
        this.y = b.g + hoogte;
        aankomst = punt(-20, 0, y + 1);
        aankomst.setDirection(new org.bukkit.util.Vector(fx, 0, fz));
        Palet p = palet;
        BlockFace vooruit = face(fx, fz), rechts = face(rx, rz);
        if (b.plaats) {
            for (int f = -25; f <= 1; f++)
                for (int s = -9; s <= 9; s++) {
                    b.vul(wx(f, s), y, wz(f, s), wx(f, s), y + 7, wz(f, s), Material.AIR);
                }
            // poten naar de grond
            if (hoogte > 1)
                for (int f : new int[]{-22, -11, 0})
                    for (int s : new int[]{-6, 6}) b.vul(wx(f, s), b.g + 1, wz(f, s), wx(f, s), y - 1, wz(f, s), p.rand());
            // vloer met een loper en lichtjes
            for (int f = -22; f <= 0; f++)
                for (int s = -6; s <= 6; s++) {
                    Material m = Math.abs(s) == 6 || f == -22 || f == 0 ? p.rand()
                            : Math.abs(s) == 2 && Math.floorMod(f, 4) == 0 ? p.licht()
                            : Math.abs(s) <= 1 ? p.vloer2() : p.vloer();
                    k(f, s, y, m);
                }
            for (int f = -19; f <= -1; f++)
                for (int s = -1; s <= 1; s++) k(f, s, y + 1, p.loper());
            // zijmuren met ramen en zuiltjes met een lantaarn
            var ruitLangs = Bouwer.ruit(Material.GLASS_PANE, vooruit, vooruit.getOppositeFace());
            for (int s : new int[]{-6, 6})
                for (int f = -22; f <= 0; f++) {
                    boolean zuil = Math.floorMod(f, 4) == 2 || f == 0;
                    for (int yy = y + 1; yy <= y + 4; yy++) {
                        if (zuil) k(f, s, yy, p.rand());
                        else if (yy == y + 2 || yy == y + 3) k(f, s, yy, ruitLangs);
                        else k(f, s, yy, p.muur());
                    }
                    if (zuil) {
                        k(f, s, y + 5, p.rand());
                        k(f, s, y + 6, Material.LANTERN);
                    }
                }
            // achterwand
            for (int s = -5; s <= 5; s++)
                for (int yy = y + 1; yy <= y + 4; yy++) k(-22, s, yy, p.muur());
            // balkon: glas op twee hoog
            var ruitDwars = Bouwer.ruit(Material.GLASS_PANE, rechts, rechts.getOppositeFace());
            for (int s = -5; s <= 5; s++) {
                k(0, s, y + 1, ruitDwars);
                k(0, s, y + 2, ruitDwars);
            }
            // twee balken dwars erover met een hanglamp
            for (int f : new int[]{-18, -6}) {
                for (int s = -6; s <= 6; s++) k(f, s, y + 5, p.rand());
                k(f, 0, y + 4, Bouwer.hangend(Material.LANTERN));
            }
            // deur naar de hub (achterin) met een verlichte nis
            deur(-22, 0, true);
            if (zijdeur != null) deur(-19, 6, false);
            for (int f : LINKS) k(f, -4, y, p.sokkel());
            for (int f : RECHTS) k(f, 4, y, p.sokkel());
        }
        // standaards en labels (niet opgeslagen, elke start nieuw)
        int i = 0;
        for (Kits.Kit kit : Kits.KITS) {
            boolean links = i < LINKS.length;
            int f = links ? LINKS[i] : RECHTS[i - LINKS.length], s = links ? -4 : 4;
            i++;
            Location l = punt(f, s, y + 1);
            l.setDirection(new org.bukkit.util.Vector(links ? rx : -rx, 0, links ? rz : -rz));
            w.spawn(l, ArmorStand.class, a -> {
                a.setPersistent(false);
                a.setInvulnerable(true);
                a.setGravity(false);
                a.setArms(true);
                a.setBasePlate(false);
                a.addScoreboardTag("kitpvp");
                a.getPersistentDataContainer().set(plugin.kitKey, PersistentDataType.STRING, kit.id());
                a.getPersistentDataContainer().set(plugin.ruimteKey, PersistentDataType.STRING, id);
                ItemStack[] uit = Kits.uitrusting(kit.id());
                a.getEquipment().setHelmet(uit[0]);
                a.getEquipment().setChestplate(uit[1]);
                a.getEquipment().setLeggings(uit[2]);
                a.getEquipment().setBoots(uit[3]);
                a.getEquipment().setItemInMainHand(new ItemStack(kit.icoon()));
            });
            b.label(l.clone().add(0, 2.35, 0), Component.text(kit.naam(), kit.kleur())
                    .append(Component.newline()).append(Component.text(kit.uitleg(), NamedTextColor.GRAY)), 0.65f);
        }
        b.label(punt(-21.6, 0, y + 4.6), Component.text("Hub", NamedTextColor.GRAY), 0.7f);
        if (zijdeur != null) b.label(punt(-19, 5.6, y + 4.6), Component.text(zijdeurLabel, NamedTextColor.GRAY), 0.7f);
        plugin.ranglijst(punt(-3, 4, y + 2.4));
    }

    /** Deur in de achterwand (achter = true, op f) of in de rechtermuur (op f, s = 6): 3 breed, 3 hoog, met een lichtnis. */
    private void deur(int f, int s, boolean achter) {
        Palet p = palet;
        for (int d = -1; d <= 1; d++)
            for (int yy = y + 1; yy <= y + 3; yy++) {
                if (achter) k(f, d, yy, Material.AIR);
                else k(f + d, s, yy, Material.AIR);
            }
        for (int d = -2; d <= 2; d++)
            for (int yy = y; yy <= y + 4; yy++) {
                boolean rand = Math.abs(d) == 2 || yy == y + 4 || yy == y;
                // nis: 1 diep, daarachter een lichtwand
                int nf = achter ? f - 1 : f + d, ns = achter ? d : s + 1;
                int af = achter ? f - 2 : f + d, as = achter ? d : s + 2;
                k(nf, ns, yy, rand ? (yy == y && Math.abs(d) < 2 ? p.licht() : p.rand()) : Material.AIR);
                k(af, as, yy, yy > y && yy < y + 4 && d == 0 ? p.licht() : p.rand());
            }
    }

    /** "hub", de id van de kitruimte achter de zijdeur, of null. */
    String deurBij(Location l) {
        if (w == null || !l.getWorld().equals(w)) return null;
        int dx = l.getBlockX() - x0, dz = l.getBlockZ() - z0, by = l.getBlockY();
        if (by < y + 1 || by > y + 3) return null;
        int f = dx * fx + dz * fz, s = dx * rx + dz * rz;
        if (f <= -23 && f >= -24 && Math.abs(s) <= 1) return "hub";
        if (zijdeur != null && s >= 7 && s <= 8 && f >= -20 && f <= -18) return zijdeur;
        return null;
    }

    boolean binnen(Location l) {
        if (w == null || !l.getWorld().equals(w)) return false;
        double dx = l.getX() - x0 - 0.5, dz = l.getZ() - z0 - 0.5;
        double f = dx * fx + dz * fz, s = dx * rx + dz * rz;
        return f >= -25 && f <= 1.5 && Math.abs(s) <= 9 && l.getY() >= y - 2 && l.getY() <= y + 9;
    }

    void zelftest(KitPvP plugin, List<String> fouten) {
        if (w == null || aankomst == null) {
            fouten.add("kitruimte " + id + ": niet gebouwd");
            return;
        }
        Block onder = aankomst.clone().add(0, -1, 0).getBlock();
        if (!onder.getType().isSolid()) fouten.add("kitruimte " + id + ": geen vloer bij de binnenkomst (" + onder.getType() + ")");
        if (!aankomst.getBlock().isPassable() || !aankomst.clone().add(0, 1, 0).getBlock().isPassable()) fouten.add("kitruimte " + id + ": binnenkomst zit vast");
        int standaards = 0;
        for (Entity e : w.getNearbyEntities(punt(-11, 0, y + 1), 14, 6, 14))
            if (e instanceof ArmorStand a && id.equals(a.getPersistentDataContainer().get(plugin.ruimteKey, PersistentDataType.STRING))) standaards++;
        if (standaards != Kits.KITS.length) fouten.add("kitruimte " + id + ": " + standaards + " standaards (verwacht " + Kits.KITS.length + ")");
        if (!"hub".equals(deurBij(punt(-23, 0, y + 1)))) fouten.add("kitruimte " + id + ": deur naar de hub werkt niet");
        if (punt(-23, 0, y + 1).getBlock().getType().isSolid()) fouten.add("kitruimte " + id + ": deur naar de hub is dicht");
        if (zijdeur != null) {
            if (!zijdeur.equals(deurBij(punt(-19, 7, y + 1)))) fouten.add("kitruimte " + id + ": zijdeur werkt niet");
            if (plugin.modi.kitruimte(zijdeur) == null) fouten.add("kitruimte " + id + ": zijdeur naar onbekende ruimte " + zijdeur);
        }
    }
}
