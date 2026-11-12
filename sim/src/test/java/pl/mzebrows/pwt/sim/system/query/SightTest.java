package pl.mzebrows.pwt.sim.system.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Noticing from behind (G0086): one fixed yes or no per noticer and thing, at the chance he declares, out to his
 * sight - asked of {@link Sight#withinNotice}, the one place notice is decided.
 */
class SightTest {

    private static final long SEED = 7L;

    private static final float ARC_COSINE = 0.64f;

    private static final float ALL_ROUND = 12f;

    private static final float SIGHT = 27f;

    private static final float CHANCE = 0.25f;

    /** Behind him, past his all-round range and inside his sight. */
    private static final float BEHIND = -20f;

    private static final int PAIRS = 4000;

    @Test
    void noticesThingsBehindHimForAboutHisChanceOfThePairs() {
        int noticed = 0;

        for (int pair = 0; pair < PAIRS; pair++) {
            if (behind(BEHIND, CHANCE, pair / 64, pair % 64)) {
                noticed++;
            }
        }

        assertThat(noticed / (float) PAIRS).isCloseTo(CHANCE, Offset.offset(0.03f));
    }

    @Test
    void noticesNothingBehindHimPastHisSightWhateverHisChance() {
        assertThat(behind(-(SIGHT + 1f), 1f, 1L, 2L)).isFalse();
    }

    @Test
    void noticesNothingBehindHimPastHisAllRoundRangeWithNoChance() {
        assertThat(behind(BEHIND, 0f, 1L, 2L)).isFalse();
    }

    /** Facing along x from the origin; the thing on the x axis at {@code x}. */
    private static boolean behind(float x, float chance, long noticer, long noticed) {
        return Sight.withinNotice(1f, 0f, 0f, 0f, x, 0f, ARC_COSINE, ALL_ROUND, SIGHT, chance, SEED, noticer, noticed);
    }
}
