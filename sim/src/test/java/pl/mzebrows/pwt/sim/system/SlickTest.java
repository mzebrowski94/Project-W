package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SLICK_BLOBS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.SLICK_SECONDS;
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
