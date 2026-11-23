package pl.mzebrows.pwt.map;

/**
 * A biome's ice (G0115, ADR 0025, provisional, for review): how much of its maze's chokepoints are shut by ice once
 * every other coat is laid. The map only marks where ice stands, a run of tiles across a narrow way; the ground
 * under it stays its own, so every connectivity check and pass counts an iced way as open - it can always be broken.
 * What stands there, and how it breaks, is {@code sim}'s.
 *
 * @param share the fraction of the chokepoints iced, from 0 to 1; zero is none
 */
public record Ice(float share) {

    public Ice {
        if (share < 0f || share > 1f) {
            throw new IllegalArgumentException("an ice share is a fraction of the chokepoints, was " + share);
        }
    }

    /** No ice: what a biome whose block leaves it out has. */
    public static Ice off() {
        return new Ice(0f);
    }
}
