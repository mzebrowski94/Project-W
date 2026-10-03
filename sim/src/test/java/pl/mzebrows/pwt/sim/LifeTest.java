package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * Where a unit is in its life - on its feet, a casualty, or in its throes - is one question with one
 * answer, whatever is asking.
 */
class LifeTest {

    private static final int CARRIES = 2;

    @Test
    void readsAManOnHisFeetAsNeitherDownNorACasualtyNorDying() {
        Entity trooper = new Simulation(SimConfig.defaults()).spawnTrooper(50f, 50f);

        assertThat(Life.down(trooper)).isFalse();
        assertThat(Life.casualty(trooper)).isFalse();
        assertThat(Life.dying(trooper)).isFalse();
    }

    @Test
    void readsAManPutDownAsACasualtyWhoIsDownButNotDying() {
        Entity trooper = new Simulation(SimConfig.defaults()).spawnTrooper(50f, 50f);

        trooper.getComponent(Vitality.class).take(Harm.DOWN, CARRIES);

        assertThat(Life.down(trooper)).isTrue();
        assertThat(Life.casualty(trooper)).isTrue();
        assertThat(Life.dying(trooper)).isFalse();
    }

    @Test
    void readsACasualtyFinishedOffAsDyingAndNoLongerACasualty() {
        Entity trooper = new Simulation(SimConfig.defaults()).spawnTrooper(50f, 50f);

        trooper.getComponent(Vitality.class).take(Harm.DOWN, CARRIES);
        trooper.getComponent(Dying.class).begin();

        assertThat(Life.down(trooper)).isTrue();
        assertThat(Life.casualty(trooper)).isFalse();
        assertThat(Life.dying(trooper)).isTrue();
    }

    @Test
    void readsAnAlienInItsThroesAsDyingAndDownThoughItCanNeverBeACasualty() {
        Entity grunt = new Simulation(SimConfig.defaults()).spawnUnit(UnitKind.GRUNT, 50f, 50f);

        assertThat(Life.down(grunt)).isFalse();

        grunt.getComponent(Dying.class).begin();

        assertThat(Life.down(grunt)).isTrue();
        assertThat(Life.casualty(grunt)).isFalse();
        assertThat(Life.dying(grunt)).isTrue();
    }
}
