package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/** What the session asks of a snapshot beyond the round trip: a hash of its bytes, and a readable diff. */
class WorldCodecTest {

    @Test
    void hashesTheBytesToWhatItHashesTheWorldTo() {
        Simulation world = playedFor(120);

        assertThat(WorldCodec.hash(WorldCodec.snapshot(world))).isEqualTo(WorldCodec.hash(world));
    }

    @Test
    void namesTheFieldTwoWorldsDisagreeOn() {
        Simulation world = playedFor(120);
        byte[] before = WorldCodec.snapshot(world);
        Entity third = world.engine().getEntities().get(3);
        Position position = third.getComponent(Position.class);
        float was = position.x;
        position.x = Math.nextUp(was);

        assertThat(WorldCodec.differences(before, WorldCodec.snapshot(world), 10))
                .containsExactly("entity 3 Position.x: " + was + " vs " + Math.nextUp(was));
    }

    @Test
    void findsNothingBetweenAWorldAndItself() {
        byte[] snapshot = WorldCodec.snapshot(playedFor(60));

        assertThat(WorldCodec.differences(snapshot, snapshot.clone(), 10)).isEmpty();
    }

    @Test
    void refusesBytesThatAreNotASnapshot() {
        Simulation blank = Scenarios.blank(Scenarios.all().get(0), SimConfig.defaults());

        assertThatThrownBy(() -> WorldCodec.restore(blank, new byte[] {1, 2, 3, 4}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a world snapshot");
    }

    private static Simulation playedFor(int ticks) {
        Scenario scenario = Scenarios.all().get(0);
        Simulation world = Scenarios.world(scenario, SimConfig.defaults().withSeed(11L));
        for (int step = 0; step < ticks; step++) {
            world.step(ScriptedPlay.input(step));
        }
        return world;
    }
}
