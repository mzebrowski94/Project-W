package pl.mzebrows.pwt.sim;

import java.util.List;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Strip;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.component.Waves;

/**
 * One kind of finale as data (G0064, missions ticket 04): a set piece, the same layout every time but for one flip
 * north-south, every position a tile offset from the anchor ({@link Strip#anchor}). The redoubt is a definition with
 * no lair; the keep the same with one. Every finale is won by holding its post alone. Read from {@code
 * sim.finale.<name>} in {@code finales.properties}.
 *
 * @param ground its width - fixed - and base, laid by {@code Strip} with no dealt coat; its rock is its fixed {@code
 *     pieces}
 * @param hold the point that holds afterwards, its watch, and what stands round it
 * @param waves the schedule held back until the post is taken, in order, each from a mouth the hold names
 * @param lair what rises inside the watch - empty but for a keep
 */
public record FinaleDef(BiomeGround ground, Hold hold, List<WaveLine> waves, List<LairLine> lair) {

    public FinaleDef {
        waves = List.copyOf(waves);
        lair = List.copyOf(lair);
        if (ground.widthMin() != ground.widthMax()) {
            throw new IllegalArgumentException("a finale's width is fixed, was " + ground.widthMin() + ".."
                    + ground.widthMax() + ": its layout is offsets from its middle");
        }
        if (ground.houses().max() > 0
                || !ground.patches().isEmpty()
                || ground.crags().density() > 0f
                || ground.outcrops().density() > 0f) {
            throw new IllegalArgumentException("a finale's ground has no dealt coat: its rock is its pieces");
        }
        for (WaveLine wave : waves) {
            if (!hold.hasMouth(wave.mouth())) {
                throw new IllegalArgumentException(
                        "a wave of " + wave.strain() + " comes from " + wave.mouth() + ", a mouth the hold lacks");
            }
        }
        if (waves.size() > Waves.MOST) {
            throw new IllegalArgumentException("a finale sends at most " + Waves.MOST + " waves, had " + waves.size());
        }
    }

    /** The same set piece flipped north-south - its pieces, its hold and its lair - and never east-west. */
    public FinaleDef flipped() {
        BiomeGround g = ground;
        List<TileRect> pieces = g.pieces().stream().map(Offset::flipped).toList();
        return new FinaleDef(
                new BiomeGround(
                        g.widthMin(),
                        g.widthMax(),
                        g.regions(),
                        g.base(),
                        g.fallback(),
                        g.houses(),
                        g.patches(),
                        g.crags(),
                        g.outcrops(),
                        pieces),
                hold.flipped(),
                waves,
                lair.stream().map(LairLine::flipped).toList());
    }
}
