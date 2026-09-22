package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SLICK_BLOBS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SLICK_SECONDS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.VENOM_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.countLiving;
import static pl.mzebrows.pwt.sim.AlphaWorlds.everyStrain;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.map.TileMap;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Enemy;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * Ruined ground: what a frog's shot leaves where it bursts.
 *
 * <p>A slick is not a blast, and the two differences are the whole of what these tests are about.
 * <strong>It is a place rather than an event</strong>, so it catches whatever walks into it for as
 * long as it lasts instead of only what was standing there on one tick; and <strong>it takes a
 * side</strong>, where a blast is on nobody's, so venom is a problem for the player's men and not
 * for the things that spat it.
 *
 * <p>The commander stands in for a man because he is one in this too - gunfire and blasts put him
 * on the ground exactly as they do his troopers (G0023) - and because with nothing driving him he
 * stands where he was put, which a trooper with a level to get on with does not.
 *
 * <p>Everything else about it is borrowed rather than invented. It arrives through the one death
 * reaction everything that leaves something behind goes through (G0018), each circle is an ordinary
 * body carrying an ordinary blow, and the blow is the wounding kind - so a man goes down as he does
 * to gunfire, and the rules that decide all of that are the ones the game already had.
 */
class SlickTest {

    private static final float SHOT_X = 20f;
    private static final float Y = 50f;

    /** Straight down the lane from the shot, well inside the arena. */
    private static final float VICTIM_X = 50f;

    /** Long enough for a shot at sixty a second to cross thirty world units and burst. */
    private static final int UNTIL_IT_BURSTS = 40;

    /** Long enough for one that hits nothing at all to fly its whole reach and burst on its own. */
    private static final int UNTIL_IT_RUNS_OUT = 50;

    /** Just short of where a shot that hits nothing bursts, so a second shot has to cross the mess. */
    private static final float SHORT_OF_THE_SPATTER = SHOT_X + VENOM_REACH - 10f;

    /** Comfortably past it, and comfortably inside the arena. */
    private static final float PAST_THE_SPATTER = SHOT_X + VENOM_REACH + 5f;

    /** Long enough for a shot at sixty a second to cross the spatter and come out the far side. */
    private static final int ACROSS_THE_SPATTER = 20;

    @Test
    void ruinsTheGroundWhereItsShotBurst() {
        Simulation simulation = harmless();
        simulation.spawnCommander(VICTIM_X, Y);
        venomAt(simulation);

        steps(simulation, UNTIL_IT_BURSTS);

        assertThat(count(simulation, Slick.class)).isEqualTo(SLICK_BLOBS);
    }

    @Test
    void putsAManStandingInItOnTheGroundRatherThanKillingHim() {
        Simulation simulation = harmless();
        Entity commander = simulation.spawnCommander(VICTIM_X, Y);
        venomAt(simulation);

        steps(simulation, UNTIL_IT_BURSTS);

        assertThat(commander.getComponent(Casualty.class).begun).isTrue();
        assertThat(commander.getComponent(Dying.class).begun).isFalse();
    }

    /** The one way a slick is not a blast. A blast catches the side that made it exactly as readily. */
    @Test
    void leavesTheSideThatMadeItAlone() {
        Simulation simulation = harmless();
        simulation.spawnCommander(VICTIM_X, Y);
        simulation.spawnEnemy(VICTIM_X, Y + 2f);
        venomAt(simulation);

        steps(simulation, UNTIL_IT_BURSTS);

        assertThat(countLiving(simulation, Enemy.class)).isEqualTo(1);
    }

    /**
     * <strong>A miss has to ruin the ground too.</strong> What a frog throws does nothing on its own
     * - the spatter is the weapon - so a shot that lands nowhere near anybody is not a wasted shot,
     * it is ground you now have to go round. It ends by <em>dying</em> like every other end (G0018),
     * so it arrives at the same reaction a burst on a man arrives at.
     *
     * <p>It did not, for as long as the gun existed: the system that throws away a shot which has
     * flown its reach ran <em>after</em> the death reactions, so the shot was marked dead past the
     * window that lays a spatter and reaped on the same tick. The fixture never caught it because
     * the fixture's venom had no reach to run out of.
     */
    @Test
    void ruinsTheGroundWhereItsShotRanOutOfFlight() {
        Simulation simulation = harmless();
        venomAt(simulation);

        steps(simulation, UNTIL_IT_RUNS_OUT);

        assertThat(count(simulation, Slick.class)).isEqualTo(SLICK_BLOBS);
    }

    /**
     * <strong>Ruined ground is not cover.</strong> A slick is a circle lying on the floor rather than
     * a thing standing on it, so a shot fired across one comes out the far side - otherwise a frog
     * fencing off the ground fences off the gun with it, and the one place you must not stand
     * becomes the one place you cannot shoot through.
     */
    @Test
    void letsAShotFlyStraightOverIt() {
        Simulation simulation = harmless();
        venomAt(simulation);
        steps(simulation, UNTIL_IT_RUNS_OUT);

        Entity shot = simulation.spawnProjectile(SHORT_OF_THE_SPATTER, Y, 1f, 0f, Side.PLAYER);
        steps(simulation, ACROSS_THE_SPATTER);

        assertThat(count(simulation, Projectile.class)).isEqualTo(1);
        assertThat(shot.getComponent(Position.class).x).isGreaterThan(PAST_THE_SPATTER);
    }

    /** A place rather than an event: it is still there long after the tick the shot ended on. */
    @Test
    void staysOnTheGroundLongAfterTheShotThatMadeIt() {
        Simulation simulation = harmless();
        simulation.spawnCommander(VICTIM_X, Y);
        venomAt(simulation);

        steps(simulation, UNTIL_IT_BURSTS + ticksIn(SLICK_SECONDS * 0.5f));

        assertThat(count(simulation, Slick.class)).isEqualTo(SLICK_BLOBS);
    }

    @Test
    void cleansTheGroundOnceItHasHadItsTime() {
        Simulation simulation = harmless();
        simulation.spawnCommander(VICTIM_X, Y);
        venomAt(simulation);

        steps(simulation, UNTIL_IT_BURSTS + ticksIn(SLICK_SECONDS) + 2);

        assertThat(count(simulation, Slick.class)).isZero();
    }

    /**
     * A shot is thrown away the moment its middle reaches rock, so a burst against a wall happens
     * just short of one - and the circles are thrown round that point, some of them into the wall.
     */
    @Test
    void leavesNothingInsideRock() {
        Simulation simulation = new Simulation(config(), wall());
        venomAt(simulation);

        steps(simulation, UNTIL_IT_BURSTS);

        TileMap map = simulation.map();
        for (Entity slick : simulation
                .engine()
                .getEntitiesFor(Family.all(Slick.class, Position.class).get())) {
            Position at = slick.getComponent(Position.class);
            assertThat(map.passableAt(at.x, at.y)).isTrue();
        }
    }

    /** Nothing on the field hits back, so a test about ruined ground is only about ruined ground. */
    private static Simulation harmless() {
        return new Simulation(everyStrain(config(), strain -> strain.withContactDamage(0f)));
    }

    /** One shot of venom on the alien side, thrown east along the lane. */
    private static void venomAt(Simulation simulation) {
        simulation.spawnProjectile(SHOT_X, Y, 1f, 0f, WeaponKind.VENOM, Side.ALIEN);
    }

    /** A rock column across the lane, with open ground either side of it. */
    private static TileMap wall() {
        return ground().rect(8, 0, 1, ARENA_TILES, Terrain.ROCK).build();
    }
}
