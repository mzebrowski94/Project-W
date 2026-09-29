package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BRUTE_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BRUTE_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.FEED_TO_EVOLVE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.evolving;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Feed;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Evolution is the mechanic the whole strain refactor exists for, so these tests are as much about
 * the shape of the change as about the rule: a unit that turns into something else must end up
 * indistinguishable from one that spawned as that thing, or the two paths will drift.
 */
class EvolutionSystemTest {

    private static final float X = 50f;
    private static final float Y = 50f;
    private static final int SLIVER = 1;
    private static final Offset<Float> TOLERANCE = Offset.offset(1e-5f);

    @Test
    void turnsAUnitIntoTheStrainItHasEatenEnoughFor() {
        Simulation simulation = evolving();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Feed.class).add(FEED_TO_EVOLVE);

        simulation.step();

        assertThat(grunt.getComponent(Strain.class).kind).isEqualTo(UnitKind.BRUTE);
    }

    @Test
    void leavesAUnitAloneUntilItHasEatenEnough() {
        Simulation simulation = evolving();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Feed.class).add(FEED_TO_EVOLVE - 1);

        simulation.step();

        assertThat(grunt.getComponent(Strain.class).kind).isEqualTo(UnitKind.GRUNT);
    }

    /** Eating the thing that transforms you transforms you, whatever you had eaten before it. */
    @Test
    void turnsAUnitIntoWhateverAnItemNamedAtOnce() {
        Simulation simulation = evolving();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Feed.class).mutateInto(UnitKind.BRUTE);

        simulation.step();

        assertThat(grunt.getComponent(Strain.class).kind).isEqualTo(UnitKind.BRUTE);
    }

    @Test
    void givesAnEvolvedUnitTheNewStrainsBodyAndLook() {
        Simulation simulation = evolving();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Feed.class).mutateInto(UnitKind.BRUTE);

        simulation.step();

        assertThat(grunt.getComponent(Body.class).radius).isCloseTo(BRUTE_RADIUS, TOLERANCE);
        assertThat(grunt.getComponent(Appearance.class).look).isEqualTo(Look.BRUTE);
    }

    /** A unit that has just become something else arrives whole, however little was left of it. */
    @Test
    void startsTheNewStrainAtFullHealth() {
        Simulation simulation = evolving();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Health.class).current = SLIVER;
        grunt.getComponent(Feed.class).mutateInto(UnitKind.BRUTE);

        simulation.step();

        assertThat(grunt.getComponent(Health.class).current).isEqualTo(BRUTE_HEALTH);
    }

    @Test
    void clearsWhatItAteSoTheNextStrainStartsEmpty() {
        Simulation simulation = evolving();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Feed.class).add(FEED_TO_EVOLVE);

        simulation.step();

        assertThat(grunt.getComponent(Feed.class).count).isZero();
        assertThat(grunt.getComponent(Feed.class).mutateInto).isNull();
    }

    @Test
    void neverEvolvesAStrainWithNowhereToGo() {
        Simulation simulation = evolving();
        Entity brute = simulation.spawnUnit(UnitKind.BRUTE, X, Y);
        brute.getComponent(Feed.class).add(FEED_TO_EVOLVE * 10);

        simulation.step();

        assertThat(brute.getComponent(Strain.class).kind).isEqualTo(UnitKind.BRUTE);
    }

    @Test
    void letsNothingInItsDeathThroesEvolve() {
        Simulation simulation = evolving();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Dying.class).begin();
        grunt.getComponent(Feed.class).add(FEED_TO_EVOLVE);

        simulation.step();

        assertThat(grunt.getComponent(Strain.class).kind).isEqualTo(UnitKind.GRUNT);
    }

    /** Pillar 5: how much a unit has eaten is a number, so the game has to show it. */
    @Test
    void showsHowFarAlongAUnitIsTowardsEvolving() {
        Simulation simulation = evolving();
        Entity grunt = simulation.spawnUnit(UnitKind.GRUNT, X, Y);
        grunt.getComponent(Feed.class).add(FEED_TO_EVOLVE - 1);

        simulation.step();

        assertThat(grunt.getComponent(Appearance.class).charge)
                .isCloseTo((FEED_TO_EVOLVE - 1f) / FEED_TO_EVOLVE, TOLERANCE);
    }

    @Test
    void showsNothingForAStrainThatCannotEvolve() {
        Simulation simulation = evolving();
        Entity brute = simulation.spawnUnit(UnitKind.BRUTE, X, Y);
        brute.getComponent(Feed.class).add(1);

        simulation.step();

        assertThat(brute.getComponent(Appearance.class).charge).isZero();
    }
}
