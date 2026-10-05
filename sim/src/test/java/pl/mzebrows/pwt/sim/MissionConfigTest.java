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

    private static final int RAMP = 10;

    private static final int HARD_ENEMIES = 125;

    private static final int HARD_HEALTH = 115;

    private static final int HARD_NEST_PERIOD = 75;

    private static final float HARD_ADAPTS_EVERY = 150f;

    private static final int HARD_ADAPTS_PER_UPGRADES = 3;

    private static final int ANSWER_WEIGHT = 2;

    private static final int SWARM_HEALTH = 125;

    private static final int COUNT = 40;

    private static final int LAST_POSITION = 3;

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
                .isEqualTo(Whole.percentOf(COUNT, HARD_ENEMIES, 100 + RAMP * LAST_POSITION));
    }

    /** A roster line that may be empty stays one that may be empty, and one that may not stays one that may not. */
    @Test
    void neverTurnsACountIntoNothingOrNothingIntoACount() {
        MissionConfig mission = mission();

        assertThat(mission.pressure(TierKind.EASY, 0).scale(1)).isEqualTo(1);
        assertThat(mission.pressure(TierKind.HARD, LAST_POSITION).scale(0)).isZero();
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
        return new MissionConfig(
                RAMP,
                Defs.<TierKind, TierDef>builder(TierKind.class)
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
