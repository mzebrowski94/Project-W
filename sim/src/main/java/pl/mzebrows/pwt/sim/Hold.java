package pl.mzebrows.pwt.sim;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A finale's point that holds afterwards and what stands round it (G0064, G0061): every position an offset from the
 * anchor, none of it scaled by tier. Taking the post puts the post down and lets the waves go; holding it means every
 * wave sent and nothing hostile within {@code watch}.
 *
 * <p>As a finale's block reads it, its turrets, mines, barrels and pickups are what every {@link Layout} shares; laid,
 * they are the dealt layout's as well (G0087). Its post, watch and mouths are the finale's, whichever is dealt.
 *
 * @param post where the point stands
 * @param watch how far round the post must be clear for it to be held, in world units
 * @param turrets a gun on a mount each
 * @param mines a mine each, which only the other side sets off
 * @param barrels an explosive barrel each
 * @param pickups what lies about, {@code pickup <kind> dx dy} - a gun's cache among them, which a biome never holds
 * @param mouths where the waves come onto the field, each named once
 */
public record Hold(
        Offset post,
        float watch,
        List<Offset> turrets,
        List<Offset> mines,
        List<Offset> barrels,
        List<LairLine> pickups,
        List<Mouth> mouths) {

    public Hold {
        if (watch <= 0f) {
            throw new IllegalArgumentException("a hold watches some ground round its post, was " + watch);
        }
        turrets = List.copyOf(turrets);
        mines = List.copyOf(mines);
        barrels = List.copyOf(barrels);
        pickups = List.copyOf(pickups);
        mouths = List.copyOf(mouths);
        for (LairLine line : pickups) {
            if (!(line instanceof LairLine.Pickup)) {
                throw new IllegalArgumentException("a hold's pickups are pickups, was " + line);
            }
        }
        Set<String> names = new TreeSet<>();
        for (Mouth mouth : mouths) {
            if (!names.add(mouth.name())) {
                throw new IllegalArgumentException("a hold names each mouth once, " + mouth.name() + " twice");
            }
        }
    }

    /** Whether it has a mouth of this name. */
    public boolean hasMouth(String name) {
        for (Mouth mouth : mouths) {
            if (mouth.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** The mouth of this name; one it has. */
    public Mouth mouth(String name) {
        for (Mouth mouth : mouths) {
            if (mouth.name().equals(name)) {
                return mouth;
            }
        }
        throw new IllegalArgumentException("no mouth called " + name);
    }

    /** The same hold with the set piece flipped north-south. */
    public Hold flipped() {
        return new Hold(
                post.flipped(),
                watch,
                turrets.stream().map(Offset::flipped).toList(),
                mines.stream().map(Offset::flipped).toList(),
                barrels.stream().map(Offset::flipped).toList(),
                pickups.stream().map(LairLine::flipped).toList(),
                mouths.stream().map(Mouth::flipped).toList());
    }
}
