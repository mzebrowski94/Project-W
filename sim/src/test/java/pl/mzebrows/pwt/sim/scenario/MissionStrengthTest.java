package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Strength;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/** What a host's strength does to a mission and its name (G0106). */
class MissionStrengthTest {

    private static final long SEED = 7L;

    private static SimConfig configured(Strength strength) {
        return Scenarios.mission(TierKind.MEDIUM, 0, strength)
                .configure(SimConfig.defaults().withSeed(SEED));
    }

    private static int creatures(Mission mission) {
        int count = 0;
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Loose) {
                count++;
            }
        }
        return count;
    }

    private static List<String> points(Mission mission) {
        List<String> points = new ArrayList<>();
        for (Placed placed : mission.placed()) {
            if (placed instanceof Placed.Point) {
                points.add(placed.x() + "," + placed.y());
            }
        }
        return points;
    }

    @Test
    void aNormalStrengthChangesNothing() {
        assertThat(configured(Strength.NORMAL))
                .isEqualTo(Scenarios.mission(TierKind.MEDIUM)
                        .configure(SimConfig.defaults().withSeed(SEED)));
    }

    @Test
    void moreCountDealsMoreCreaturesAndFewerDealsFewerOnTheSameMap() {
        Mission usual = Mission.build(configured(Strength.NORMAL), TierKind.MEDIUM);
        Mission many = Mission.build(configured(new Strength(100, 200)), TierKind.MEDIUM);
        Mission few = Mission.build(configured(new Strength(100, 50)), TierKind.MEDIUM);

        assertThat(creatures(many)).isGreaterThan(creatures(usual));
        assertThat(creatures(few)).isLessThan(creatures(usual));
        assertThat(points(many)).isEqualTo(points(usual));
        assertThat(many.order()).isEqualTo(usual.order());
    }

    @Test
    void moreHealthIsFoldedIntoEveryStrainAndNest() {
        SimConfig usual = configured(Strength.NORMAL);
        SimConfig tough = configured(new Strength(200, 100));

        assertThat(tough.healthOf(UnitKind.GRUNT)).isEqualTo(2 * usual.healthOf(UnitKind.GRUNT));
        assertThat(tough.nestHealth()).isEqualTo(2 * usual.nestHealth());
    }

    @Test
    void refusesAStrengthTheConfigDoesNotOffer() {
        assertThatThrownBy(() -> configured(new Strength(125, 100))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configured(new Strength(100, 350))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configured(new Strength(0 + 25, 100))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissionsNameCarriesItsStrengthOnlyWhenItIsNotNormal() {
        assertThat(Scenarios.mission(TierKind.HARD, 4, Strength.NORMAL).name()).isEqualTo("mission hard 4");
        assertThat(Scenarios.mission(TierKind.HARD, 4, new Strength(150, 100)).name())
                .isEqualTo("mission hard 4 h150");
        assertThat(Scenarios.mission(TierKind.EASY, 0, new Strength(50, 300)).name())
                .isEqualTo("mission easy h50 c300");
    }

    @Test
    void aNameIsReadBackAsTheMissionItNamed() {
        MissionName name = new MissionName(TierKind.HARD, 9, new Strength(250, 50));

        assertThat(MissionName.parse(name.text())).isEqualTo(name);
        assertThat(Scenarios.named(name.text()).name()).isEqualTo(name.text());
    }

    @Test
    void refusesANameItCannotRead() {
        assertThat(MissionName.parse("mission hard h")).isNull();
        assertThat(MissionName.parse("mission hard 4 x9")).isNull();
        assertThat(MissionName.parse("mission huge")).isNull();
        assertThat(MissionName.parse("crowd")).isNull();
        assertThatThrownBy(() -> Scenarios.named("mission hard h0")).isInstanceOf(IllegalArgumentException.class);
    }
}
