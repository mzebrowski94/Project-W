package pl.mzebrows.pwt.client.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;

class LocalAimTest {

    private final LocalAim aim = new LocalAim();
    private final float[] out = new float[2];

    @Test
    void facesTheMouseFromWhereTheCommanderIsDrawn() {
        aim.set(2, new CommanderInput(0f, 0f, 13f, 14f, false, false));

        assertThat(aim.facing(2, 10f, 10f, out)).isTrue();
        assertThat(out[0]).isCloseTo(0.6f, within(1e-6f));
        assertThat(out[1]).isCloseTo(0.8f, within(1e-6f));
    }

    @Test
    void facesAlongAStickAsItIs() {
        aim.set(1, new CommanderInput(0f, 0f, 0f, -0.5f, false, false).aimingRelatively());

        assertThat(aim.facing(1, 100f, 100f, out)).isTrue();
        assertThat(out).containsExactly(0f, -1f);
    }

    @Test
    void leavesTheWorldsFacingForASlotPlayedElsewhereOrAnAimThatSaysNothing() {
        aim.set(0, new CommanderInput(0f, 0f, 0f, 0f, false, false).aimingRelatively());

        assertThat(aim.facing(0, 5f, 5f, out)).isFalse();
        assertThat(aim.facing(3, 5f, 5f, out)).isFalse();
    }

    @Test
    void forgetsEveryAimWhenCleared() {
        aim.set(0, new CommanderInput(0f, 0f, 20f, 0f, false, false));
        aim.clear();

        assertThat(aim.facing(0, 0f, 0f, out)).isFalse();
    }
}
