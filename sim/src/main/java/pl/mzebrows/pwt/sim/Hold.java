package pl.mzebrows.pwt.sim;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A finale's point that holds afterwards (G0064, G0061): part of its fixed skeleton (G0120), every position an offset
 * from the anchor, none of it scaled by tier. Taking the post puts the post down and lets the waves go; holding it
 * means every wave sent and nothing hostile within {@code watch}. What stands round it is placed by the finale's
 * {@link FinaleRules}.
 *
 * @param post where the point stands
 * @param watch how far round the post must be clear for it to be held, in world units
 * @param mouths where the waves come onto the field, each named once
 */
public record Hold(Offset post, float watch, List<Mouth> mouths) {

    public Hold {
        if (watch <= 0f) {
            throw new IllegalArgumentException("a hold watches some ground round its post, was " + watch);
        }
        mouths = List.copyOf(mouths);
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
                post.flipped(), watch, mouths.stream().map(Mouth::flipped).toList());
    }
}
