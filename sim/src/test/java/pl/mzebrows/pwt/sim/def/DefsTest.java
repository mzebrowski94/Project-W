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
    private static final float HEALTH = 100f;

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

        Defs<UnitKind, UnitDef> changed = defs.map(UnitKind.GRUNT, grunt -> grunt.withHealth(1f));

        assertThat(changed.of(UnitKind.GRUNT).health()).isEqualTo(1f);
        assertThat(changed.of(UnitKind.BRUTE)).isEqualTo(defs.of(UnitKind.BRUTE));
    }

    @Test
    void leavesTheTableItChangedUntouched() {
        Defs<UnitKind, UnitDef> defs = complete();

        defs.map(UnitKind.GRUNT, grunt -> grunt.withHealth(1f));

        assertThat(defs.of(UnitKind.GRUNT).health()).isEqualTo(HEALTH);
    }

    @Test
    void listsEveryDefinitionInTheEnumsOwnOrder() {
        assertThat(complete().values())
                .containsExactly(
                        strain(RADIUS, Look.GRUNT), strain(RADIUS * 2f, Look.BRUTE), strain(RADIUS, Look.SPITTER));
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
                .build();
    }

    private static UnitDef strain(float radius, Look look) {
        return new UnitDef(
                radius,
                SPEED,
                1,
                HEALTH,
                0f,
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
                RangedDef.off(),
                look);
    }
}
