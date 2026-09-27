package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How a boss shoots: a <strong>barrage</strong>, a cycle of {@link PatternDef patterns} it works
 * through for as long as it has somebody to shoot at (G0048).
 *
 * <p>Each pattern is told before it is fired - a wind-up, drawn as the winding-up state every leap
 * already shows (pillar 5) - and followed by a rest, which is the gap a player walks in through. The
 * patterns come round in the order they are declared here, and one left out of the file is skipped.
 *
 * <p><strong>Nothing about it is rolled</strong>: which pattern is next, which way a spiral turns and
 * when the next salvo leaves are all counted, so a barrage can be learnt, as the genre asks, and replays
 * exactly (pillar 6).
 *
 * @param enabled whether this strain has a barrage at all. Off for every strain but the boss
 * @param windUpSeconds how long it shows what is coming before a pattern's first salvo
 * @param restSeconds how long it holds fire after a pattern's last salvo
 * @param enragedBelow how much of its health it has left, as a fraction, when it starts working faster.
 *     Zero never does
 * @param enragedFactor what wind-up, rest and every interval are multiplied by once it has, in
 *     {@code (0, 1]}
 * @param ring the first pattern of the cycle
 * @param fan the second
 * @param spiral the third
 * @param stream the fourth
 */
@With
public record BarrageDef(
        boolean enabled,
        float windUpSeconds,
        float restSeconds,
        float enragedBelow,
        float enragedFactor,
        PatternDef ring,
        PatternDef fan,
        PatternDef spiral,
        PatternDef stream) {

    /** How many patterns a barrage cycles through. */
    public static final int PATTERNS = 4;

    public BarrageDef {
        Require.notNegative("barrage windUpSeconds", windUpSeconds);
        Require.notNegative("barrage restSeconds", restSeconds);
        if (enragedBelow < 0f || enragedBelow > 1f) {
            throw new IllegalArgumentException("barrage enragedBelow must be in [0, 1], was " + enragedBelow);
        }
        if (enragedFactor <= 0f || enragedFactor > 1f) {
            throw new IllegalArgumentException("barrage enragedFactor must be in (0, 1], was " + enragedFactor);
        }
        if (enabled && !ring.enabled() && !fan.enabled() && !spiral.enabled() && !stream.enabled()) {
            throw new IllegalArgumentException("a barrage needs at least one pattern in it");
        }
    }

    /** No barrage. What every strain but the boss declares. */
    public static BarrageDef off() {
        return new BarrageDef(
                false, 0f, 0f, 0f, 1f, PatternDef.off(), PatternDef.off(), PatternDef.off(), PatternDef.off());
    }
}
