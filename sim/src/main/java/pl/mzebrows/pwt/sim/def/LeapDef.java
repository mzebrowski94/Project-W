package pl.mzebrows.pwt.sim.def;

import lombok.With;

/**
 * How a unit throws itself at the commander to close the last of the distance.
 *
 * <p>Every field is per strain, because that is the whole point: a fast thing that barely telegraphs
 * and a heavy thing that winds up visibly are the same system with different numbers.
 *
 * @param enabled whether this strain leaps at all; a unit configured off simply walks
 * @param triggerRange how close the commander must be before it commits, in world units
 * @param speed how fast it travels while airborne, in world units per second
 * @param airborneSeconds how long one leap lasts
 * @param sequenceLength how many leaps it strings together before it has to reset
 * @param betweenSeconds pause between the leaps of one sequence, while it lands and re-aims
 * @param cooldownSeconds how long after a whole sequence before it may start another
 * @param hesitationMinSeconds shortest wind-up before the first leap of a sequence
 * @param hesitationMaxSeconds longest wind-up before the first leap of a sequence. Drawn from the
 *     simulation's seeded {@code Rng}, so it varies between leaps and not between runs - the player
 *     has to read the tell rather than count the frames, and a replay still matches.
 * @param deflectChance the chance, per cent, that a trooper it comes at inside his arc meets it with his hand strike
 *     as it lands, so it never takes hold of him (G0086) - keyed once per leap and man. Zero is never
 */
@With
public record LeapDef(
        boolean enabled,
        float triggerRange,
        float speed,
        float airborneSeconds,
        int sequenceLength,
        float betweenSeconds,
        float cooldownSeconds,
        float hesitationMinSeconds,
        float hesitationMaxSeconds,
        int deflectChance) {

    public LeapDef {
        Require.notNegative("leap triggerRange", triggerRange);
        Require.notNegative("leap speed", speed);
        Require.notNegative("leap airborneSeconds", airborneSeconds);
        Require.notNegative("leap betweenSeconds", betweenSeconds);
        Require.notNegative("leap cooldownSeconds", cooldownSeconds);
        Require.notNegative("leap hesitationMinSeconds", hesitationMinSeconds);
        Require.notNegative("leap hesitationMaxSeconds", hesitationMaxSeconds);
        Require.atLeastOne("leap sequenceLength", sequenceLength);
        Require.percent("leap deflectChance", deflectChance);
        if (hesitationMaxSeconds < hesitationMinSeconds) {
            throw new IllegalArgumentException("leap hesitationMaxSeconds must not be below hesitationMinSeconds");
        }
    }

    /** A strain that does not leap. */
    public static LeapDef off() {
        return new LeapDef(false, 0f, 0f, 0f, 1, 0f, 0f, 0f, 0f, 0);
    }

    /** The same leap with a fixed wind-up, for a test that needs to know when the jump comes. */
    public LeapDef withHesitation(float seconds) {
        return withHesitationMinSeconds(seconds).withHesitationMaxSeconds(seconds);
    }
}
