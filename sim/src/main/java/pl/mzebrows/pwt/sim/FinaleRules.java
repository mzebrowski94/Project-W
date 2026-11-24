package pl.mzebrows.pwt.sim;

import java.util.List;
import pl.mzebrows.pwt.map.Randomness;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Require;

/**
 * How a finale places everything round its skeleton (G0120): its cover, its guns on mounts, its mines, barrels and
 * pickups, and the gun caches its lair raises - each a {@link Lot}, dealt from the finale's own stream through
 * {@code Scatter} in mirrored north-south pairs. Nothing here is moved by the tier (G0063) or by the players seated
 * (G0119). Read from {@code sim.finale.<name>.rules}.
 *
 * @param apart how far apart, in tiles, any two things it places stand, edge to edge
 * @param gap how far, in tiles, a piece of cover keeps off every other piece, off the post, the mouths, the lair and
 *     the routes from the mouths and to the lair - so every piece's lee is open and reached
 * @param shapes the pieces cover is dealt from, each equally
 * @param cover how many pieces of cover, and where
 * @param turrets how many guns on mounts - fixed, never dealt - and where
 * @param mines how many mines, and where
 * @param barrels how many explosive barrels, and where
 * @param pickups how many pickups lie about the hold - one of each kind in {@code pickupKinds} - and where
 * @param pickupKinds what they are, in the order they are put down
 * @param lair how many gun caches the lair raises - fixed - and where; none outside a keep
 * @param lairKinds the kinds they are dealt from, none twice (G0120)
 * @param inReach whether the cover and the guns stand inside the barrage of the lair's unit - the keep's boss - so a
 *     man who uses them takes the patterns for it
 */
public record FinaleRules(
        float apart,
        float gap,
        List<CoverShape> shapes,
        Lot cover,
        Lot turrets,
        Lot mines,
        Lot barrels,
        Lot pickups,
        List<Pick> pickupKinds,
        Lot lair,
        List<Pick> lairKinds,
        boolean inReach) {

    public FinaleRules {
        shapes = List.copyOf(shapes);
        pickupKinds = List.copyOf(pickupKinds);
        lairKinds = List.copyOf(lairKinds);
        Require.notNegative("finale rules apart", apart);
        Require.notNegative("finale rules gap", gap);
        if (cover.max() > 0 && shapes.isEmpty()) {
            throw new IllegalArgumentException("a finale that lays cover names the shapes it is dealt from");
        }
        if (turrets.min() != turrets.max()) {
            throw new IllegalArgumentException(
                    "a finale's turrets are fixed, never dealt, were " + turrets.min() + ".." + turrets.max());
        }
        fixed("pickups", pickups, pickupKinds);
        if (lair.min() != lair.max() || lair.max() > lairKinds.size()) {
            throw new IllegalArgumentException(
                    "a finale's lair raises a fixed count of caches, none twice, so no more than " + lairKinds.size()
                            + " kinds, were " + lair.min() + ".." + lair.max());
        }
    }

    /** The places of each pickup of the block's list, in pairs: one of each, no more and no fewer. */
    private static void fixed(String what, Lot lot, List<Pick> kinds) {
        if (lot.min() != kinds.size() || lot.max() != kinds.size()) {
            throw new IllegalArgumentException("a finale's " + what + " are one of each kind named, " + kinds.size()
                    + ", were " + lot.min() + ".." + lot.max());
        }
    }

    /**
     * How many of something, and where: a count dealt from a range - in pairs, so both ends are even - in a band round
     * the post, every distance in tiles from the post and every limit a column offset from the anchor. One of each pair
     * is placed south of the anchor's row and the other mirrored north of it (G0120).
     *
     * @param min the fewest, even
     * @param max the most, even
     * @param near how close to the post one may stand, in tiles
     * @param far how far from it, in tiles
     * @param west the westmost column offset one may stand on
     * @param east the eastmost
     */
    public record Lot(int min, int max, float near, float far, int west, int east) {

        public Lot {
            if (min < 0 || max < min || min % 2 != 0 || max % 2 != 0) {
                throw new IllegalArgumentException(
                        "a finale's lot is dealt in mirrored pairs, from an even fewest to an even most, was " + min
                                + ".." + max);
            }
            if (near < 0f || far < near) {
                throw new IllegalArgumentException("a lot's band runs out from near to far, was " + near + ".." + far);
            }
            if (east < west) {
                throw new IllegalArgumentException("a lot's band runs west to east, was " + west + ".." + east);
            }
        }

        /** How many pairs, drawn. */
        public int pairs(Randomness rng) {
            int fewest = min / 2;
            return fewest + rng.nextInt(max / 2 - fewest + 1);
        }

        /** Whether a tile this far from the post, in tiles, on this column offset, is in the band. */
        public boolean holds(float fromThePost, int dx) {
            return fromThePost >= near && fromThePost <= far && dx >= west && dx <= east;
        }
    }

    /** A pickup a lot puts down: {@code crate}, {@code armoury_flamer}. */
    public record Pick(PickupKind kind) {}

    /**
     * A piece of cover (G0120), from the block's short list: a short bar, or an L whose corner faces what the piece
     * shelters from - so its lee is inside the L.
     */
    public sealed interface CoverShape {

        /** How wide its box is, in tiles. */
        int width();

        /** How tall its box is, in tiles. */
        int height();

        /** A straight bar, {@code bar 3 1}: so many tiles across and so many up. */
        record Bar(int width, int height) implements CoverShape {

            public Bar {
                if (width < 1 || height < 1) {
                    throw new IllegalArgumentException("a bar is at least a tile, was " + width + "x" + height);
                }
            }
        }

        /** An L one tile thick, {@code ell 3 2}: an arm so many tiles across and one so many up, meeting at a corner. */
        record Ell(int width, int height) implements CoverShape {

            public Ell {
                if (width < 2 || height < 2) {
                    throw new IllegalArgumentException(
                            "an L's arms are at least two tiles, was " + width + "x" + height);
                }
            }
        }
    }
}
