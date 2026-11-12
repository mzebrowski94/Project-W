package pl.mzebrows.pwt.sim.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The table's whole job is to be complete and to be cheap to vary. Both are load-bearing: a hole in
 * it would mean every lookup site had to decide what to do with a missing kind, and a change that
 * was not isolated would mean a scenario tuning one strain silently resetting another.
 */
class DefsTest {

    private static final float RADIUS = 2f;
    private static final float SPEED = 10f;
    private static final int HEALTH = 100;

    @Test
    void refusesToBuildATableThatIsMissingAKind() {
        Defs.Builder<UnitKind, UnitDef> builder =
                Defs.<UnitKind, UnitDef>builder(UnitKind.class).put(UnitKind.GRUNT, strain(RADIUS, Look.GRUNT));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BRUTE");
    }

    @Test
    void changesOneKindAndLeavesEveryOtherAlone() {
        Defs<UnitKind, UnitDef> defs = complete();

        Defs<UnitKind, UnitDef> changed = defs.map(UnitKind.GRUNT, grunt -> grunt.withHealth(1));

        assertThat(changed.of(UnitKind.GRUNT).health()).isEqualTo(1);
        assertThat(changed.of(UnitKind.BRUTE)).isEqualTo(defs.of(UnitKind.BRUTE));
    }

    @Test
    void leavesTheTableItChangedUntouched() {
        Defs<UnitKind, UnitDef> defs = complete();

        defs.map(UnitKind.GRUNT, grunt -> grunt.withHealth(1));

        assertThat(defs.of(UnitKind.GRUNT).health()).isEqualTo(HEALTH);
    }

    @Test
    void listsEveryDefinitionInTheEnumsOwnOrder() {
        assertThat(complete().values())
                .containsExactly(
                        strain(RADIUS, Look.GRUNT),
                        strain(RADIUS * 2f, Look.BRUTE),
                        strain(RADIUS, Look.SPITTER),
                        strain(RADIUS, Look.RUNNER),
                        strain(RADIUS, Look.FROG),
                        strain(RADIUS, Look.ANT),
                        strain(RADIUS * 3f, Look.BOSS_GRUNT),
                        strain(RADIUS * 2f, Look.SEARER),
                        strain(RADIUS * 2f, Look.THUMPER),
                        strain(RADIUS, Look.SLUDGER),
                        strain(RADIUS * 2f, Look.SLINGER),
                        strain(RADIUS, Look.LEECH),
                        strain(RADIUS * 2f, Look.SLINGER));
    }

    @Test
    void comparesByValueSoAConfigLoadedFromDefaultsEqualsTheDefaults() {
        assertThat(complete()).isEqualTo(complete());
    }

    private static Defs<UnitKind, UnitDef> complete() {
        return Defs.<UnitKind, UnitDef>builder(UnitKind.class)
                .put(UnitKind.GRUNT, strain(RADIUS, Look.GRUNT))
                .put(UnitKind.BRUTE, strain(RADIUS * 2f, Look.BRUTE))
                .put(UnitKind.SPITTER, strain(RADIUS, Look.SPITTER))
                .put(UnitKind.RUNNER, strain(RADIUS, Look.RUNNER))
                .put(UnitKind.FROG, strain(RADIUS, Look.FROG))
                .put(UnitKind.ANT, strain(RADIUS, Look.ANT))
                .put(UnitKind.BOSS_GRUNT, strain(RADIUS * 3f, Look.BOSS_GRUNT))
                .put(UnitKind.SEARER, strain(RADIUS * 2f, Look.SEARER))
                .put(UnitKind.THUMPER, strain(RADIUS * 2f, Look.THUMPER))
                .put(UnitKind.SLUDGER, strain(RADIUS, Look.SLUDGER))
                .put(UnitKind.SLINGER, strain(RADIUS * 2f, Look.SLINGER))
                .put(UnitKind.LEECH, strain(RADIUS, Look.LEECH))
                .put(UnitKind.LEECH_SLINGER, strain(RADIUS * 2f, Look.SLINGER))
                .build();
    }

    private static UnitDef strain(float radius, Look look) {
        return new UnitDef(
                radius,
                SPEED,
                1,
                HEALTH,
                0,
                PerceptionDef.ALL_ROUND,
                0f,
                0f,
                0f,
                0f,
                0,
                null,
                PerceptionDef.alwaysAggressive(0f, 0f),
                WanderDef.off(),
                AvoidanceDef.off(),
                LeapDef.off(),
                HopDef.off(),
                RangedDef.off(),
                ApproachDef.off(),
                WeaveDef.off(),
                DodgeDef.off(),
                GripDef.off(),
                ArmourDef.off(),
                BarrageDef.off(),
                SlingDef.off(),
                FuseDef.off(),
                0,
                true,
                Chances.none(),
                look);
    }
}
