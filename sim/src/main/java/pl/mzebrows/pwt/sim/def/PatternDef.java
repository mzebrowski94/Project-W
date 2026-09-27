package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * One <strong>pattern</strong> of a {@link BarrageDef}: a number of <strong>salvos</strong>, each a
 * spread of shots leaving together, one after another at an interval.
 *
 * <p>Every shape a top-down boss is known for is these numbers and no code: a ring is a salvo spread
 * all the way round, a spiral is a thin ring that turns between salvos, a fan is a narrow spread
 * centred on whoever it is hunting, and a stream is a fan of one or three fired fast.
 *
 * @param enabled whether this pattern is in the barrage at all; one left out is skipped
 * @param gun which entry in the weapon table each shot is - what it looks like, how fast it flies and
 *     what it does to whoever it reaches
 * @param shots how many shots leave together in one salvo
 * @param arcDegrees how wide they are spread, centred on the salvo's bearing. 360 is a full ring with
 *     no two shots on top of each other; zero puts them all on one line
 * @param aimed whether the salvo is centred on what the barrage is hunting, looked up afresh for every
 *     salvo, rather than on a bearing of its own that only the turn moves
 * @param turnDegrees how far each salvo turns from the one before it. Which way round alternates every
 *     time the barrage comes back to this pattern, so a spiral is never learnt by heart
 * @param salvos how many salvos the pattern fires before the barrage rests
 * @param intervalSeconds how long between one salvo and the next
 */
@With
public record PatternDef(
        boolean enabled,
        WeaponKind gun,
        int shots,
        float arcDegrees,
        boolean aimed,
        float turnDegrees,
        int salvos,
        float intervalSeconds) {

    private static final float FULL_CIRCLE = 360f;

    public PatternDef {
        Require.notNegative("pattern shots", shots);
        Require.notNegative("pattern salvos", salvos);
        Require.notNegative("pattern intervalSeconds", intervalSeconds);
        if (arcDegrees < 0f || arcDegrees > FULL_CIRCLE) {
            throw new IllegalArgumentException("pattern arcDegrees must be in [0, 360], was " + arcDegrees);
        }
        if (enabled && (shots < 1 || salvos < 1)) {
            throw new IllegalArgumentException("a pattern in the barrage fires at least one shot in one salvo");
        }
    }

    /** A pattern the barrage skips. */
    public static PatternDef off() {
        return new PatternDef(false, WeaponKind.SPIT, 0, 0f, false, 0f, 0, 0f);
    }

    /** Whether the spread closes on itself: the last shot of a full ring would land on the first. */
    public boolean ring() {
        return arcDegrees >= FULL_CIRCLE;
    }
}
