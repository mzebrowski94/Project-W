package pl.mzebrows.pwt.sim.system.commander;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_ACTIVE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_COOLDOWN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WorldCodec;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.component.Stunned;

/**
 * Swinging. The sweep is a real body in the world that travels an arc and is gone (G0005), so what is
 * tested here is where it goes, when, and which way round the arc - never whether it hit anything,
 * which belongs to collision.
 */
class MeleeSystemTest {

    private static final CommanderInput EAST = new CommanderInput(0f, 0f, 60f, 50f, false, true);
    private static final CommanderInput NORTH_AIM_EAST_WALK = new CommanderInput(1f, 0f, 50f, 60f, false, true);
    private static final CommanderInput IDLE_AIMED = new CommanderInput(0f, 0f, 60f, 50f, false, false);

    /** Walking east and swinging east: the case where an anchored sweep is left behind him. */
    private static final CommanderInput EAST_AIM_EAST_WALK = new CommanderInput(1f, 0f, 90f, 50f, false, true);

    /** Two commanders, each pointing east along his own row. */
    private static final float SOUTH_COMMANDER_Y = 42f;

    private static final float NORTH_COMMANDER_Y = 58f;

    private static final CommanderInput SWING_EAST_FROM_SOUTH =
            new CommanderInput(0f, 0f, 60f, SOUTH_COMMANDER_Y, false, true);

    private static final CommanderInput SWING_EAST_FROM_NORTH =
            new CommanderInput(0f, 0f, 60f, NORTH_COMMANDER_Y, false, true);

    @Test
    void putsASweepInTheWorldWhenTheMeleeInputIsPressed() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        simulation.step(EAST);

        assertThat(count(simulation, Strike.class)).isEqualTo(1);
    }

    @Test
    void putsNothingInTheWorldWithoutTheInput() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        simulation.step(IDLE_AIMED);

        assertThat(count(simulation, Strike.class)).isZero();
    }

    @Test
    void keepsTheSweepAtArmsLength() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        simulation.step(EAST);

        assertThat(distanceBetween(commander, sweep(simulation))).isCloseTo(MELEE_REACH, Offset.offset(0.2f));
    }

    /** Halfway through the arc the sweep is where the player pointed; the ends are the wind and follow. */
    @Test
    void carriesTheSweepThroughWhereThePlayerIsAiming() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) / 2, EAST);

        Position position = sweep(simulation).getComponent(Position.class);
        assertThat(position.x).isCloseTo(50f + MELEE_REACH, Offset.offset(0.2f));
        assertThat(position.y).isCloseTo(50f, Offset.offset(0.2f));
    }

    /** G0002: the swing goes where he points, not where he is running. */
    @Test
    void aimsTheSweepWhereTheCommanderPointsNotWhereHeWalks() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) / 2, NORTH_AIM_EAST_WALK);

        Position position = sweep(simulation).getComponent(Position.class);
        assertThat(position.y).isGreaterThan(50f + MELEE_REACH / 2f);
    }

    /**
     * <strong>The sweep is in front of him, not behind where he set off from.</strong> The arc used
     * to be centred where he stood when the swing was released, and a commander covers several
     * strides while one is travelling - so using the hand strike while running put the sweep out of
     * his back. Half an arc in, walking east, it must be east of him.
     */
    @Test
    void keepsTheSweepInFrontOfHimWhileHeIsWalking() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) / 2, EAST_AIM_EAST_WALK);

        assertThat(sweep(simulation).getComponent(Position.class).x)
                .isCloseTo(commander.getComponent(Position.class).x + MELEE_REACH, Offset.offset(0.3f));
    }

    /**
     * A swing sweeps the opposite way to the one before it, like a blade. The sweep is a real body in
     * the world (G0005), so which side of the aim it starts on is not a picture: it is where the blow
     * is, and it decides what the swing reaches first.
     */
    @Test
    void sweepsTheOtherWayOnTheNextSwing() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        simulation.step(EAST);
        boolean firstStartsNorth = startsNorthOfHisAim(simulation, commander);
        steps(simulation, ticksIn(MELEE_COOLDOWN) + 2, IDLE_AIMED);
        simulation.step(EAST);

        assertThat(firstStartsNorth).isFalse();
        assertThat(startsNorthOfHisAim(simulation, commander)).isTrue();
    }

    /** Nothing puts it back: not a pause, not a long one. It alternates for the whole of his life. */
    @Test
    void keepsAlternatingHoweverLongHeWaitsBetweenSwings() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);

        boolean first = waitsOutTheCooldownAndSweepsNorth(simulation, commander);
        boolean second = waitsOutTheCooldownAndSweepsNorth(simulation, commander);
        boolean third = waitsOutTheCooldownAndSweepsNorth(simulation, commander);

        assertThat(first).isFalse();
        assertThat(second).isTrue();
        assertThat(third).isFalse();
    }

    /**
     * The alternation belongs to the man. Two commanders both start on the default side rather than
     * one either way, and each turns on his own swings rather than on anybody else's.
     */
    @Test
    void countsEveryCommandersOwnSwings() {
        Simulation simulation = simulation();
        Entity south = simulation.spawnCommander(50f, SOUTH_COMMANDER_Y, 0);
        Entity north = simulation.spawnCommander(50f, NORTH_COMMANDER_Y, 1);

        simulation.step(bothSwinging());
        boolean southStartsNorth = startsNorthOfHisAim(simulation, south);
        boolean northStartsNorth = startsNorthOfHisAim(simulation, north);
        steps(simulation, ticksIn(MELEE_COOLDOWN) + 2);
        simulation.step(bothSwinging());

        assertThat(southStartsNorth).isFalse();
        assertThat(northStartsNorth).isFalse();
        assertThat(startsNorthOfHisAim(simulation, south)).isTrue();
        assertThat(startsNorthOfHisAim(simulation, north)).isTrue();
    }

    /** Which way the next sweep goes is world state (pillar 7): a snapshot carries it. */
    @Test
    void carriesWhichWayTheNextSweepGoesThroughASnapshot() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        simulation.step(EAST);
        byte[] before = WorldCodec.snapshot(simulation);

        commander.getComponent(Melee.class).swingsReleased++;

        assertThat(WorldCodec.differences(before, WorldCodec.snapshot(simulation), 10))
                .anySatisfy(line -> assertThat(line).contains("Melee.swingsReleased"));
    }

    @Test
    void takesTheSweepAwayOnceItHasTravelledItsArc() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 2, EAST);

        assertThat(count(simulation, Strike.class)).isZero();
    }

    @Test
    void swingsOnlyOnceWithinItsCooldown() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);

        int swings = countSwings(simulation, ticksIn(MELEE_COOLDOWN) - 4);

        assertThat(swings).isEqualTo(1);
    }

    @Test
    void swingsAgainOnceTheCooldownHasPassed() {
        Simulation simulation = harmless();
        simulation.spawnCommander(50f, 50f);

        int swings = countSwings(simulation, ticksIn(MELEE_COOLDOWN * 2f));

        assertThat(swings).isEqualTo(2);
    }

    @Test
    void swingsNothingWhileTheCommanderIsHeld() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(50f, 50f);
        commander.getComponent(Stunned.class).apply(AlphaWorlds.ticksIn(AlphaWorlds.MELEE_LOCK), 0f);

        simulation.step(EAST);

        assertThat(count(simulation, Strike.class)).isZero();
    }

    /** A weapon with a wind-up commits the player before it commits the sweep. The hand strike has none. */
    @Test
    void holdsBackTheSweepUntilAWeaponWithAWindUpHasFinishedIt() {
        SimConfig config = AlphaWorlds.config();
        Simulation simulation = new Simulation(config.withMelee(config.melee().withWindupSeconds(0.2f)));
        simulation.spawnCommander(50f, 50f);

        simulation.step(EAST);

        assertThat(count(simulation, Strike.class)).isZero();
    }

    @Test
    void keepsTheSweepInTheWorldForExactlyItsActiveTicks() {
        Simulation simulation = simulation();
        simulation.spawnCommander(50f, 50f);
        simulation.step(EAST);

        int lifetime = ticksUntilTheSweepIsGone(simulation);

        assertThat(lifetime).isEqualTo(ticksIn(MELEE_ACTIVE));
    }

    @Test
    void releasesTheSweepOnceTheWindUpIsOver() {
        SimConfig config = AlphaWorlds.config();
        Simulation simulation = new Simulation(config.withMelee(config.melee().withWindupSeconds(0.2f)));
        simulation.spawnCommander(50f, 50f);

        steps(simulation, ticksIn(0.2f) + 2, EAST);

        assertThat(count(simulation, Strike.class)).isEqualTo(1);
    }

    /** Counts separate swings rather than ticks a sweep existed for. */
    private static int countSwings(Simulation simulation, int ticks) {
        int swings = 0;
        boolean swinging = false;
        for (int i = 0; i < ticks; i++) {
            simulation.step(EAST);
            boolean present = count(simulation, Strike.class) > 0;
            if (present && !swinging) {
                swings++;
            }
            swinging = present;
        }
        return swings;
    }

    /** Ticks from the swing until no sweep is left, counting the one it was put in the world on. */
    private static int ticksUntilTheSweepIsGone(Simulation simulation) {
        int ticks = 1;
        while (count(simulation, Strike.class) > 0) {
            simulation.step(IDLE_AIMED);
            ticks++;
        }
        return ticks;
    }

    private static Entity sweep(Simulation simulation) {
        return simulation
                .engine()
                .getEntitiesFor(Family.all(Strike.class).get())
                .first();
    }

    /** The sweep this man swung, by the handle the strike carries - with two commanders, "the first one" is no answer. */
    private static Entity sweepOf(Simulation simulation, Entity man) {
        long owner = Handles.of(man);
        for (Entity swing :
                simulation.engine().getEntitiesFor(Family.all(Strike.class).get())) {
            if (swing.getComponent(Strike.class).owner == owner) {
                return swing;
            }
        }
        throw new IllegalStateException("this commander swung nothing");
    }

    /** Waits out the cooldown, swings, and says which side of his aim the sweep started on. */
    private static boolean waitsOutTheCooldownAndSweepsNorth(Simulation simulation, Entity commander) {
        steps(simulation, ticksIn(MELEE_COOLDOWN) + 2, IDLE_AIMED);
        simulation.step(EAST);
        return startsNorthOfHisAim(simulation, commander);
    }

    /**
     * Which side of his aim the sweep stands on the tick it appears. Every test here aims him due east,
     * so north of him is the half of the arc the default swing finishes in rather than starts in.
     */
    private static boolean startsNorthOfHisAim(Simulation simulation, Entity commander) {
        return sweepOf(simulation, commander).getComponent(Position.class).y > commander.getComponent(Position.class).y;
    }

    /** One tick with both commanders swinging east, and nobody else doing anything. */
    private static PlayerInputs bothSwinging() {
        return new PlayerInputs().set(0, SWING_EAST_FROM_SOUTH).set(1, SWING_EAST_FROM_NORTH);
    }

    private static float distanceBetween(Entity first, Entity second) {
        Position a = first.getComponent(Position.class);
        Position b = second.getComponent(Position.class);
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
