package pl.mzebrows.pwt.sim;

import java.util.ArrayList;
import java.util.List;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.component.Waves;

/**
 * One kind of finale as data (G0064, missions ticket 04): a set piece, one of a few hand-made {@link Layout}s dealt a
 * mission and then flipped north-south or not (G0087), every position a tile offset from the anchor ({@link
 * Strip#anchor}). The redoubt is a definition with no lair; the keep the same with one. Every finale is won by holding
 * its post alone. Read from {@code sim.finale.<name>} in {@code finales.properties}.
 *
 * <p>As read, its ground and hold carry what every layout shares and {@code layouts} what varies; {@link #laid} lays
 * one over the rest, and what comes out has no layouts of its own - a finale as laid is a finale of one layout.
 *
 * @param ground its width - fixed - and base, laid by {@code Strip} with no dealt coat; its rock is its {@code pieces}
 *     and its layout's
 * @param hold the point that holds afterwards, its watch, its mouths, and what stands round it in every layout
 * @param layouts the hand-made arrangements one is dealt from, numbered blocks from one; none is a finale as laid
 * @param waves the schedule held back until the post is taken, in order, each from mouths the hold names
 * @param lair what rises inside the watch - empty but for a keep
 */
public record FinaleDef(
        BiomeGround ground, Hold hold, List<Layout> layouts, List<WaveLine> waves, List<LairLine> lair) {

    public FinaleDef {
        layouts = List.copyOf(layouts);
        waves = List.copyOf(waves);
        lair = List.copyOf(lair);
        if (ground.widthMin() != ground.widthMax()) {
            throw new IllegalArgumentException("a finale's width is fixed, was " + ground.widthMin() + ".."
                    + ground.widthMax() + ": its layout is offsets from its middle");
        }
        if (ground.houses().max() > 0
                || !ground.patches().isEmpty()
                || ground.crags().density() > 0f
                || ground.outcrops().density() > 0f
                || ground.clearings().count() > 0
                || ground.ice().share() > 0f) {
            throw new IllegalArgumentException("a finale's ground has no dealt coat: its rock is its pieces");
        }
        for (WaveLine wave : waves) {
            for (String mouth : wave.mouthNames()) {
                if (!hold.hasMouth(mouth)) {
                    throw new IllegalArgumentException(
                            "a wave of " + wave.strain() + " comes from " + mouth + ", a mouth the hold lacks");
                }
            }
        }
        if (waves.size() > Waves.MOST) {
            throw new IllegalArgumentException("a finale sends at most " + Waves.MOST + " waves, had " + waves.size());
        }
    }

    /** How many layouts its stream deals from: one, itself, for a finale as laid. */
    public int layoutCount() {
        return Math.max(1, layouts.size());
    }

    /**
     * The finale with one of its layouts laid over what every layout shares, counting from zero: its pieces added to
     * the ground's, its turrets, mines, barrels and pickups to the hold's. A finale as laid lays itself.
     */
    public FinaleDef laid(int layout) {
        if (layouts.isEmpty()) {
            return this;
        }
        Layout chosen = layouts.get(layout);
        return new FinaleDef(
                withPieces(ground, joined(ground.pieces(), chosen.pieces())),
                new Hold(
                        hold.post(),
                        hold.watch(),
                        joined(hold.turrets(), chosen.turrets()),
                        joined(hold.mines(), chosen.mines()),
                        joined(hold.barrels(), chosen.barrels()),
                        joined(hold.pickups(), chosen.pickups()),
                        hold.mouths()),
                List.of(),
                waves,
                lair);
    }

    /** The same set piece flipped north-south - its pieces, its hold, its layouts and its lair - never east-west. */
    public FinaleDef flipped() {
        return new FinaleDef(
                withPieces(ground, ground.pieces().stream().map(Offset::flipped).toList()),
                hold.flipped(),
                layouts.stream().map(Layout::flipped).toList(),
                waves,
                lair.stream().map(LairLine::flipped).toList());
    }

    private static BiomeGround withPieces(BiomeGround g, List<TileRect> pieces) {
        return new BiomeGround(
                g.widthMin(),
                g.widthMax(),
                g.regions(),
                g.base(),
                g.fallback(),
                g.houses(),
                g.patches(),
                g.crags(),
                g.outcrops(),
                g.clearings(),
                g.ice(),
                pieces);
    }

    private static <T> List<T> joined(List<T> shared, List<T> own) {
        List<T> all = new ArrayList<>(shared);
        all.addAll(own);
        return List.copyOf(all);
    }
}
