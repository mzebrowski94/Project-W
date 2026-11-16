package pl.mzebrows.pwt.sim.system.motion;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TICK;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Burrow;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Facing;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PerceptionDef;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The desert worm (G0096): under the ground it is a shadow nothing can hurt, always coming for the nearest man in its
 * own biome; under him it surfaces - a maw closing on where he stood - and the snap wounds whoever is still inside.
 */
class WormTest {

    /** The shipped burrowing pace: faster than the commander, so nobody walks away from it. */
    private static final float SPEED = 14f;

    /** The shipped commander's walk, so stepping out of the maw is tested at the pace that matters. */
    private static final float WALK = 13f;

    private static final float RADIUS = 3.5f;
    private static final int HEALTH = 900;
    private static final int TEETH = 12;
    private static final float TELL_SECONDS = 0.9f;
    private static final float STAND_SECONDS = 1.5f;
    private static final float REST_SECONDS = 15f;
    private static final float STUN_SECONDS = 1f;

    /** The fixture's pace with nobody to come for, or while it rests: half its hunting pace. */
    private static final float IDLE_SPEED = 7f;

    private static final float MIDDLE = ARENA * 0.5f;
    private static final float WEST = 15f;
    private static final float EAST = 80f;

    /** Long enough to cross the arena under the ground, come up and close its maw. */
    private static final int TO_CROSS_AND_SNAP = ticksIn((EAST - WEST) / SPEED + TELL_SECONDS + 1f);

    @Test
    void surfacesUnderAManWhoStandsStillAndWoundsHimOnce() {
        Simulation simulation = new Simulation(worms());
        Entity man = simulation.spawnCommander(EAST, MIDDLE);
        simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);

        steps(simulation, TO_CROSS_AND_SNAP + ticksIn(STAND_SECONDS));

        assertThat(man.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    /** It goes beneath rock, never round it: a wall between them is no cover. */
    @Test
    void burrowsBeneathRockToTheManItComesFor() {
        TileMap walled = AlphaWorlds.ground()
                .rect(10, 0, 1, AlphaWorlds.ARENA_TILES, Terrain.ROCK)
                .build();
        Simulation simulation = new Simulation(worms(), walled);
        Entity man = simulation.spawnCommander(EAST, MIDDLE);
        simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);

        steps(simulation, TO_CROSS_AND_SNAP + ticksIn(STAND_SECONDS));

        assertThat(man.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    /** Under the ground no blow lands on it - a shot, a blast, anything. */
    @Test
    void cannotBeHurtWhileItBurrows() {
        Simulation simulation = new Simulation(worms());
        simulation.spawnCommander(WEST, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, EAST, MIDDLE);
        Health health = worm.getComponent(Health.class);

        for (int tick = 0; tick < ticksIn(1.5f); tick++) {
            worm.getComponent(Damage.class).land(HEALTH / 10, Harm.KILL);
            simulation.step(new CommanderInput(0f, 0f, EAST, MIDDLE, true, false));
        }

        assertThat(worm.getComponent(Burrow.class).burrowed()).isTrue();
        assertThat(health.current).isEqualTo(HEALTH);
    }

    /** The maw is the tell: a man who keeps walking is out of it before it closes. */
    @Test
    void aManWhoKeepsWalkingStepsOutOfItsMaw() {
        Simulation simulation = new Simulation(worms());
        Entity commander = simulation.spawnCommander(WEST, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, EAST, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);
        CommanderInput walkingEast = new CommanderInput(1f, 0f, ARENA, MIDDLE, false, false);

        boolean cameUp = false;
        for (int tick = 0; tick < ticksIn(4f); tick++) {
            simulation.step(walkingEast);
            cameUp |= !burrow.burrowed();
        }

        assertThat(cameUp).isTrue();
        assertThat(commander.getComponent(Vitality.class).wounds).isZero();
    }

    /** How many teeth its maw has left is how hurt it is, rounded up: the last tooth goes with the last point. */
    @Test
    void losesAToothForEveryToothsWorthOfHealthLost() {
        Simulation simulation = new Simulation(worms());
        Entity worm = simulation.spawnUnit(UnitKind.WORM, MIDDLE, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);
        Health health = worm.getComponent(Health.class);
        int tooth = HEALTH / TEETH;
        simulation.step();
        assertThat(burrow.teethLeft).isEqualTo(TEETH);

        health.current = HEALTH - tooth;
        simulation.step();
        assertThat(burrow.teethLeft).isEqualTo(TEETH - 1);

        health.current = 1;
        simulation.step();
        assertThat(burrow.teethLeft).isEqualTo(1);
    }

    /** Coming up it can be hurt at once - before the snap, the only time it can be stopped from biting. */
    @Test
    void canBeHurtFromTheMomentItComesUp() {
        Simulation simulation = new Simulation(worms());
        simulation.spawnCommander(EAST, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);
        untilItComesUp(simulation, burrow);

        worm.getComponent(Damage.class).land(HEALTH / 10, Harm.KILL);
        simulation.step();

        assertThat(burrow.phase).isEqualTo(Burrow.Phase.SURFACING);
        assertThat(worm.getComponent(Health.class).current).isLessThan(HEALTH);
    }

    /**
     * Not solid while its maw closes, so its man is not pushed out of it; solid from the snap, so he is put out of the
     * hole.
     */
    @Test
    void putsWhoeverIsInItsHoleOutOfItOnlyAtTheSnap() {
        Simulation simulation = new Simulation(worms());
        Entity man = simulation.spawnCommander(EAST, MIDDLE);
        Position his = man.getComponent(Position.class);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);
        untilItComesUp(simulation, burrow);
        float stoodX = his.x;
        float stoodY = his.y;

        steps(simulation, ticksIn(TELL_SECONDS) - 2);
        float inTheTell = (float) StrictMath.hypot(his.x - stoodX, his.y - stoodY);
        steps(simulation, ticksIn(0.5f));
        Position hole = worm.getComponent(Position.class);

        assertThat(inTheTell).isLessThan(0.5f);
        assertThat((float) StrictMath.hypot(his.x - hole.x, his.y - hole.y)).isGreaterThan(RADIUS);
    }

    /** A declared stun landed while it comes up breaks the snap off and holds it up, then it dives. */
    @Test
    void isHeldUpWithoutSnappingByADeclaredStunInItsTell() {
        Simulation simulation = new Simulation(worms());
        Entity man = simulation.spawnCommander(EAST, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);
        untilItComesUp(simulation, burrow);

        worm.getComponent(Stunned.class).daze(ticksIn(STUN_SECONDS));
        steps(simulation, ticksIn(STUN_SECONDS * 0.5f));
        boolean upHalfWay = !burrow.burrowed();
        steps(simulation, ticksIn(STUN_SECONDS));

        assertThat(upHalfWay).isTrue();
        assertThat(burrow.burrowed()).isTrue();
        assertThat(man.getComponent(Vitality.class).wounds).isZero();
    }

    /** After it dives it rests: the man it bit, standing where he was, is not bitten again before the rest is out. */
    @Test
    void doesNotComeUpAgainUntilItsRestIsOut() {
        Simulation simulation = new Simulation(worms());
        Entity man = simulation.spawnCommander(EAST, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);
        untilItComesUp(simulation, burrow);
        while (!burrow.burrowed()) {
            simulation.step();
        }

        boolean cameUpAgain = false;
        for (int tick = 0; tick < ticksIn(REST_SECONDS - 1f); tick++) {
            simulation.step();
            cameUpAgain |= !burrow.burrowed();
        }

        assertThat(cameUpAgain).isFalse();
        assertThat(man.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    /** Burrowed, it never stops: under its man it goes round him until it may come up. */
    @Test
    void neverStopsWhileItBurrows() {
        Simulation simulation = new Simulation(worms());
        simulation.spawnCommander(EAST, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);
        Position at = worm.getComponent(Position.class);
        steps(simulation, TO_CROSS_AND_SNAP + ticksIn(STAND_SECONDS + 0.5f));

        for (int tick = 0; tick < ticksIn(3f); tick++) {
            float fromX = at.x;
            float fromY = at.y;
            simulation.step();
            assertThat(burrow.burrowed()).isTrue();
            assertThat((float) StrictMath.hypot(at.x - fromX, at.y - fromY)).isGreaterThan(IDLE_SPEED * TICK * 0.9f);
        }
    }

    /** Where there is no room for its body - rock beside him - it does not come up, and stays under him. */
    @Test
    void doesNotComeUpWhereThereIsNoRoomForItsBody() {
        TileMap besideRock = AlphaWorlds.ground()
                .rect(16, 0, 4, AlphaWorlds.ARENA_TILES, Terrain.ROCK)
                .build();
        Simulation simulation = new Simulation(worms(), besideRock);
        Entity man = simulation.spawnCommander(16 * AlphaWorlds.TILE - RADIUS * 0.5f, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);

        boolean cameUp = false;
        for (int tick = 0; tick < TO_CROSS_AND_SNAP; tick++) {
            simulation.step();
            cameUp |= !burrow.burrowed();
        }

        assertThat(cameUp).isFalse();
        assertThat(man.getComponent(Vitality.class).wounds).isZero();
    }

    /** With nobody in its biome it bends about home, always moving, and never crosses into the next. */
    @Test
    void bendsAboutItsOwnBiomeWithNobodyInIt() {
        Simulation simulation = new Simulation(worms(), AlphaWorlds.twoBiomes());
        simulation.spawnCommander(EAST, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, WEST + 10f, MIDDLE);
        Position at = worm.getComponent(Position.class);

        for (int tick = 0; tick < ticksIn(10f); tick++) {
            float fromX = at.x;
            float fromY = at.y;
            simulation.step();
            assertThat(at.x).isLessThan(MIDDLE);
            assertThat((float) StrictMath.hypot(at.x - fromX, at.y - fromY)).isGreaterThan(0f);
        }
        assertThat(worm.getComponent(Perception.class).aggro).isFalse();
    }

    /** No trooper fires at a shadow. */
    @Test
    void isNotFiredAtWhileItBurrows() {
        Simulation simulation = new Simulation(worms());
        simulation.spawnCommander(EAST, MIDDLE);
        simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);

        steps(simulation, ticksIn(2f));

        assertThat(AlphaWorlds.count(simulation, Projectile.class)).isZero();
    }

    /**
     * A mission puts every strain down unaware (G0063); a worm's biome is its range, so a man who enters it is noticed
     * all the same (G0096).
     */
    @Test
    void comesForAManInItsBiomeThoughItWasPutDownUnaware() {
        Simulation simulation = new Simulation(worms().withUnit(
                        UnitKind.WORM,
                        worm -> worm.withPerception(worm.perception().withAlwaysAggressive(false))));
        Entity man = simulation.spawnCommander(EAST, MIDDLE);
        simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);

        steps(simulation, TO_CROSS_AND_SNAP + ticksIn(STAND_SECONDS));

        assertThat(man.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    /** Resting, it goes after nobody: it roams about at its idle pace until it may come up again. */
    @Test
    void roamsRatherThanFollowsItsManWhileItRests() {
        Simulation simulation = new Simulation(worms());
        simulation.spawnCommander(EAST, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);
        Position at = worm.getComponent(Position.class);
        untilItComesUp(simulation, burrow);
        while (!burrow.burrowed()) {
            simulation.step();
        }

        for (int tick = 0; tick < ticksIn(REST_SECONDS - 1f); tick++) {
            float fromX = at.x;
            float fromY = at.y;
            simulation.step();
            assertThat((float) StrictMath.hypot(at.x - fromX, at.y - fromY))
                    .isLessThanOrEqualTo(IDLE_SPEED * TICK * 1.05f);
        }
    }

    /** Up in its hole it is not frozen: it turns to look at its man wherever he goes. */
    @Test
    void looksAtItsManWhileItStandsInItsHole() {
        Simulation simulation = new Simulation(worms());
        Entity man = simulation.spawnCommander(EAST, MIDDLE);
        Entity worm = simulation.spawnUnit(UnitKind.WORM, WEST, MIDDLE);
        Burrow burrow = worm.getComponent(Burrow.class);
        untilItComesUp(simulation, burrow);
        steps(simulation, ticksIn(TELL_SECONDS) + 1);
        assertThat(burrow.phase).isEqualTo(Burrow.Phase.STANDING);
        Position hole = worm.getComponent(Position.class);

        man.getComponent(Position.class).set(hole.x, hole.y + RADIUS * 3f);
        steps(simulation, ticksIn(1f));

        Facing facing = worm.getComponent(Facing.class);
        assertThat(burrow.phase).isEqualTo(Burrow.Phase.STANDING);
        assertThat(facing.y).isGreaterThan(0.9f);
    }

    private static void untilItComesUp(Simulation simulation, Burrow burrow) {
        for (int tick = 0; tick < TO_CROSS_AND_SNAP && burrow.burrowed(); tick++) {
            simulation.step();
        }
        assertThat(burrow.phase).isEqualTo(Burrow.Phase.SURFACING);
    }

    private static SimConfig worms() {
        SimConfig config = config();
        return config.withCommander(config.commander().withSpeed(WALK))
                .withUnit(
                        UnitKind.WORM,
                        worm -> worm.withSpeed(SPEED)
                                .withRadius(RADIUS)
                                .withHealth(HEALTH)
                                .withContactDamage(0)
                                .withPerception(new PerceptionDef(true, false, 0f, 0f, 0f, 360f, false, 0f))
                                .withBurrow(AlphaWorlds.burrowing()));
    }
}
