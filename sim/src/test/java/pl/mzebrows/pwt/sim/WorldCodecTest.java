package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.ashley.core.Entity;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.Wave;
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

    /** A wave from several mouths (G0087) is a record holding a list of them, and a snapshot carries the list. */
    @Test
    void carriesAWaveFromSeveralPlacesThroughASnapshot() {
        Scenario scenario = Scenarios.all().get(0);
        Simulation world = Scenarios.world(scenario, SimConfig.defaults().withSeed(11L));
        Wave twice = new Wave(
                5f,
                UnitKind.GRUNT,
                List.of(new Wave.Lot(3, new TileRect(1, 1, 2, 2)), new Wave.Lot(4, new TileRect(9, 1, 2, 2))));
        world.waves().holdBack().add(twice);
        Simulation blank = Scenarios.blank(scenario, SimConfig.defaults().withSeed(11L));

        byte[] snapshot = WorldCodec.snapshot(world);
        WorldCodec.restore(blank, snapshot);

        assertThat(blank.waves().total()).isEqualTo(world.waves().total());
        assertThat(WorldCodec.differences(snapshot, WorldCodec.snapshot(blank), 10))
                .isEmpty();
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
