package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BRUTE_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.NEST_PERIOD;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.Whole;
import pl.mzebrows.pwt.sim.system.upgrade.AdaptationDef;

/**
 * A tier is four numbers on the swarm and a ramp (G0063), read by {@code MissionConfig} alone: folded into the
 * config a mission's world is built from, and scaling the counts its biomes are dealt.
 */
class MissionConfigTest {

    private static final int RAMP_TOP = 150;

    private static final int HARD_ENEMIES = 125;

    private static final int HARD_HEALTH = 115;

    private static final int HARD_NEST_PERIOD = 75;

    private static final float HARD_ADAPTS_EVERY = 150f;

    private static final int HARD_ADAPTS_PER_UPGRADES = 3;

    private static final int ANSWER_WEIGHT = 2;

    private static final int SWARM_HEALTH = 125;

    private static final int COUNT = 40;

    private static final int LAST_POSITION = 5;

    private static final int LENGTH = 6;

    @Test
    void foldsATiersHealthIntoEveryStrainAndNestOnTopOfTheSwarms() {
        SimConfig config = config();

        SimConfig hard = config.mission().apply(TierKind.HARD, config);

        assertThat(hard.healthOf(UnitKind.GRUNT)).isEqualTo(Whole.percentOf(ENEMY_HEALTH, SWARM_HEALTH, HARD_HEALTH));
        assertThat(hard.healthOf(UnitKind.BRUTE)).isEqualTo(Whole.percentOf(BRUTE_HEALTH, SWARM_HEALTH, HARD_HEALTH));
        assertThat(hard.nestHealth()).isEqualTo(Whole.percentOf(NEST_HEALTH, SWARM_HEALTH, HARD_HEALTH));
    }

    @Test
    void breedsANestOnTheTiersPeriod() {
        SimConfig config = config();

        SimConfig hard = config.mission().apply(TierKind.HARD, config);

        assertThat(hard.nest().periodSeconds()).isCloseTo(NEST_PERIOD * HARD_NEST_PERIOD / 100f, within(1e-6f));
    }

    /** The tier's pace replaces whatever the level had; how much an answer weighs is not the tier's. */
    @Test
    void adaptsTheSwarmAtTheTiersPace() {
        SimConfig config = config().withAdaptation(new AdaptationDef(0f, 0, ANSWER_WEIGHT));

        SimConfig hard = config.mission().apply(TierKind.HARD, config);

        assertThat(hard.adaptation())
                .isEqualTo(new AdaptationDef(HARD_ADAPTS_EVERY, HARD_ADAPTS_PER_UPGRADES, ANSWER_WEIGHT));
    }

    @Test
    void changesNothingButTheSwarmsNumbers() {
        SimConfig config = config();

        SimConfig hard = config.mission().apply(TierKind.HARD, config);

        assertThat(hard.withSwarm(config.swarm()).withNest(config.nest()).withAdaptation(config.adaptation()))
                .isEqualTo(config);
    }

    /** Both percents meet the count once, in {@code Whole}, as they meet hit points (G0054). */
    @Test
    void scalesACountByTheTierAndRampsItWithThePositionInTheMarch() {
        MissionConfig mission = mission();

        assertThat(mission.pressure(TierKind.HARD, 0).scale(COUNT)).isEqualTo(Whole.percentOf(COUNT, HARD_ENEMIES));
        assertThat(mission.pressure(TierKind.HARD, LAST_POSITION).scale(COUNT))
                .isEqualTo(Whole.percentOf(COUNT, HARD_ENEMIES, RAMP_TOP));
    }

    /** The last biome is as hard at every length, those between evenly spaced (G0102). */
    @Test
    void spansTheRampOverTheMarchWhateverItsLength() {
        MissionConfig mission = mission();

        for (int length = mission.biomesMin(); length <= mission.biomesMax(); length++) {
            MissionConfig of = mission.withBiomes(length);

            assertThat(of.pressure(TierKind.MEDIUM, 0).rampedPercent()).isEqualTo(100);
            assertThat(of.pressure(TierKind.MEDIUM, length - 1).rampedPercent()).isEqualTo(RAMP_TOP);
            for (int p = 1; p < length; p++) {
                assertThat(of.pressure(TierKind.MEDIUM, p).rampedPercent())
                        .as("length %d position %d", length, p)
                        .isGreaterThanOrEqualTo(
                                of.pressure(TierKind.MEDIUM, p - 1).rampedPercent());
            }
        }
        assertThat(mission.withBiomes(LENGTH).pressure(TierKind.MEDIUM, 1).rampedPercent())
                .isEqualTo(110);
    }

    @Test
    void knowsWhichLengthsAMissionMayBe() {
        MissionConfig mission = mission();

        assertThat(mission.hasLength(mission.biomesMin() - 1)).isFalse();
        assertThat(mission.hasLength(mission.biomesMin())).isTrue();
        assertThat(mission.hasLength(mission.biomesMax())).isTrue();
        assertThat(mission.hasLength(mission.biomesMax() + 1)).isFalse();
    }

    /** A roster line that may be empty stays one that may be empty, and one that may not stays one that may not. */
    @Test
    void neverTurnsACountIntoNothingOrNothingIntoACount() {
        MissionConfig mission = mission();

        assertThat(mission.pressure(TierKind.EASY, 0).scale(1)).isEqualTo(1);
        assertThat(mission.pressure(TierKind.HARD, LAST_POSITION).scale(0)).isZero();
    }

    /**
     * A host's count on creatures with no nest is laid on what the tier and the ramp dealt and rounded down (G0106): a
     * line of one may go, and at a hundred nothing moves. A nest's line keeps the one rounding and is never none.
     */
    @Test
    void laysAHostsCountOnLooseCreaturesRoundedDownAndOnNestsAsBefore() {
        MissionConfig mission = mission();
        MissionConfig.Pressure medium = mission.pressure(TierKind.MEDIUM, 0);

        assertThat(medium.times(50).scaleLoose(1)).isZero();
        assertThat(medium.times(150).scaleLoose(3)).isEqualTo(4);
        assertThat(medium.times(100).scaleLoose(COUNT)).isEqualTo(medium.scale(COUNT));
        assertThat(mission.pressure(TierKind.EASY, 0).times(100).scaleLoose(1))
                .as("the tier's own rounding stands")
                .isEqualTo(1);
        assertThat(medium.times(50).scale(1)).as("a nest").isEqualTo(1);
    }

    @Test
    void refusesAPositionBeforeTheFirst() {
        MissionConfig mission = mission();

        assertThatThrownBy(() -> mission.pressure(TierKind.MEDIUM, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Medium's first biome is its block as written: the blocks are ported at that strength (missions ticket 07). */
    @Test
    void dealsTheFirstBiomeOnMediumAtItsWrittenCounts() {
        MissionConfig shipped = SimConfig.defaults().mission();

        assertThat(shipped.pressure(TierKind.MEDIUM, 0).scale(COUNT)).isEqualTo(COUNT);
    }

    /** Off a mission there is no tier, and the swarm is as tough as its own factor says. */
    @Test
    void leavesEveryOtherLevelAtTheSwarmsOwnHealth() {
        SimConfig shipped = SimConfig.defaults();

        assertThat(shipped.healthOf(UnitKind.GRUNT))
                .isEqualTo(Whole.percentOf(
                        shipped.unit(UnitKind.GRUNT).health(), shipped.swarm().healthPercent()));
    }

    private static SimConfig config() {
        SimConfig config = AlphaWorlds.config().withMission(mission());
        return config.withSwarm(config.swarm().withHealthPercent(SWARM_HEALTH));
    }

    private static MissionConfig mission() {
        return SimConfig.defaults()
                .mission()
                .withRampTopPercent(RAMP_TOP)
                .withTiers(Defs.<TierKind, TierDef>builder(TierKind.class)
                        .put(TierKind.EASY, new TierDef(75, 85, 130, 0f, 0))
                        .put(TierKind.MEDIUM, new TierDef(100, 100, 100, 240f, 4))
                        .put(
                                TierKind.HARD,
                                new TierDef(
                                        HARD_ENEMIES,
                                        HARD_HEALTH,
                                        HARD_NEST_PERIOD,
                                        HARD_ADAPTS_EVERY,
                                        HARD_ADAPTS_PER_UPGRADES))
                        .build());
    }
}
