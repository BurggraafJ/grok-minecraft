package nl.burggraaf.mc.zelda;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Een eiland van de Glazen Zee (zie ZELDA.md): een hub met meerdere dingen erop, gebouwd uit één of twee delen
 * ({@link ZeldaHoofdstuk}, elk een eigen bouwdeel met een eigen versie). Je komt aan bij de {@code aanleg} (een
 * rustplaats bij de steiger) en vaart met de Zilvermeeuw naar elk eiland dat {@code open} is (kaart, vak 9). Een eiland
 * dat je af hebt, kun je altijd weer bezoeken.
 *
 * @param id        kort en vast ({@code lindewoud}, {@code zilverstrand}, …)
 * @param naam      zoals spelers het zien
 * @param icoon     op de kaart
 * @param delen     de delen, in de volgorde van het eiland (het eerste deel heeft de aanleg)
 * @param aanleg    id van de rustplaats waar het schip aanlegt (in {@code delen.get(0)})
 * @param openNa    vlag die het eiland op de zeekaart zet (null = altijd open: het eerste eiland)
 * @param klaar     vlag als de hoofdopdracht van het eiland af is (het baken brandt); opent het volgende eiland
 * @param overtocht wat er gezegd wordt tijdens de eerste overtocht hierheen (Wiske, Brecht), mag leeg
 */
record ZeldaEiland(String id, String naam, Material icoon, List<ZeldaHoofdstuk> delen, String aanleg, String openNa, String klaar,
                   List<ZeldaHoofdstuk.Regel> overtocht) {

    /**
     * Een eiland dat eraan komt maar nog niet gebouwd is: zodra je {@code openNa} hebt (een baken brandt), staat het grijs
     * op de zeekaart en bij Brecht («nog even geduld»), en zegt Wiske dat jullie er straks heen varen. De sessie die het
     * eiland bouwt, haalt het hier weg en zet het echte eiland in {@code Zelda.EILANDEN}.
     *
     * <p>Ook een <em>deel</em> van een eiland kan eraan komen ({@code eiland} = het id van dat eiland, bv. de Smidse op de
     * Gloedberg na {@code e4a}): grijs op de kaart, niet bij Brecht (daar vaar je niet heen), en Wiske zegt dat het straks
     * opengaat.
     */
    record Komt(String id, String naam, Material icoon, String openNa, String eiland) {
        /** Een eiland dat eraan komt (om naartoe te varen). */
        Komt(String id, String naam, Material icoon, String openNa) {
            this(id, naam, icoon, openNa, null);
        }
    }

    ZeldaEiland {
        if (id == null || !id.matches("[a-z][a-z0-9-]*")) throw new IllegalArgumentException("ongeldig eiland-id: " + id);
        if (delen.isEmpty()) throw new IllegalArgumentException(id + ": geen delen");
        for (ZeldaHoofdstuk h : delen) {
            if (h.eiland != null) throw new IllegalArgumentException(h.id() + " hoort al bij " + h.eiland.id());
            h.eiland = this;
        }
    }

    boolean open(ZeldaVoortgang.Stand s) {
        return openNa == null || s.heeft(openNa);
    }

    /** De rustplaats bij de steiger (null zolang het deel niet gebouwd is). */
    ZeldaHoofdstuk.Rust aanlegRust() {
        return delen.get(0).rust(aanleg);
    }

    Location aanlegPlek() {
        ZeldaHoofdstuk.Rust r = aanlegRust();
        return r == null ? null : r.plek;
    }

    /** Het deel waar deze plek in ligt (in zijn speelgebied), of null. */
    ZeldaHoofdstuk deelBij(Location l) {
        for (ZeldaHoofdstuk h : delen) if (h.speelgebied != null && h.w != null && h.w.equals(l.getWorld()) && h.speelgebied.in(l)) return h;
        return null;
    }
}
