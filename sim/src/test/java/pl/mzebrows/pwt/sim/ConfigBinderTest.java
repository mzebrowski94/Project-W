package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A config record is filled from {@code key=value} text by the names of its components, so a tunable
 * is a record field and a line in the file and nothing else. Every key is accounted for both ways: one
 * the records do not have is as much a mistake as one the file does not give.
 */
class ConfigBinderTest {

    enum Size {
        SMALL,
        LARGE
    }

    record Gait(boolean enabled, float speed) {
        static Gait off() {
            return new Gait(false, 0f);
        }
    }

    record Body(
            int count,
            long seed,
            float radius,
            boolean solid,
            Look look,
            @Nullable UnitKind into,
            Gait gait) {}

    record Table(Defs<Size, Gait> sizes) {}

    record Pickup(Grant grant) {}

    record Strain(Chances chance) {}

    record Level(@ConfigKey("ai.chance") Chances chances, Strain grunt) {}

    record Renamed(@ConfigKey("unit") float units) {}

    @Test
    void fillsEveryComponentByName() {
        Body body = strict(Body.class, """
                test.count=3
                test.seed=20260906
                test.radius=1.5
                test.solid=true
                test.look=grunt
                test.into=brute
                test.gait.enabled=true
                test.gait.speed=8.25
                """);

        assertThat(body)
                .isEqualTo(new Body(3, 20_260_906L, 1.5f, true, Look.GRUNT, UnitKind.BRUTE, new Gait(true, 8.25f)));
    }

    @Test
    void readsNoneAsNothingForWhatMayBeNothing() {
        Body body = strict(Body.class, """
                test.count=1
                test.seed=1
                test.radius=1
                test.solid=false
                test.look=grunt
                test.into=none
                test.gait.enabled=false
                test.gait.speed=0
                """);

        assertThat(body.into()).isNull();
    }

    @Test
    void turnsOffABlockTheFileSaysNothingAbout() {
        Body body = strict(Body.class, """
                test.count=1
                test.seed=1
                test.radius=1
                test.solid=false
                test.look=grunt
                test.into=none
                """);

        assertThat(body.gait()).isEqualTo(Gait.off());
    }

    @Test
    void fillsAKindTableOneBlockPerKind() {
        Table table = strict(Table.class, """
                test.sizes.small.enabled=true
                test.sizes.small.speed=2
                test.sizes.large.enabled=true
                test.sizes.large.speed=1
                """);

        assertThat(table.sizes().of(Size.SMALL).speed()).isEqualTo(2f);
        assertThat(table.sizes().of(Size.LARGE).speed()).isEqualTo(1f);
    }

    @Test
    void readsAGrantAsItsNameAndArgument() {
        assertThat(strict(Pickup.class, "test.grant=mutate brute").grant()).isEqualTo(new Grant.Mutate(UnitKind.BRUTE));
        assertThat(strict(Pickup.class, "test.grant=feed 2").grant()).isEqualTo(new Grant.Feed(2));
        assertThat(strict(Pickup.class, "test.grant=none").grant()).isEqualTo(Grant.NOTHING);
        assertThat(strict(Pickup.class, "test.grant=detonate").grant()).isEqualTo(Grant.DETONATE);
    }

    @Test
    void takesEveryChanceAStrainDoesNotNameFromTheSharedBlock() {
        Strain strain = ConfigBinder.bindStrictly(
                        Level.class, "sim", text(sharedChances() + "sim.grunt.chance.bite_a_barrel=0.9\n"))
                .grunt();

        assertThat(strain.chance().of(AiChance.BITE_A_BARREL)).isEqualTo(0.9f);
        assertThat(strain.chance().of(AiChance.SWING_AT_A_LEAP)).isEqualTo(0.5f);
        assertThat(strain.chance().longestGap()).isEqualTo(2f);
    }

    @Test
    void readsAComponentUnderTheKeyItIsRenamedTo() {
        assertThat(strict(Renamed.class, "test.unit=4").units()).isEqualTo(4f);
    }

    @Test
    void refusesAKeyNoRecordHas() {
        assertThatThrownBy(() -> strict(Pickup.class, "test.grant=none\ntest.grnat=feed 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown key test.grnat");
    }

    @Test
    void refusesAMissingValue() {
        assertThatThrownBy(() -> strict(Gait.class, "test.enabled=true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing test.speed");
    }

    @Test
    void refusesAValueThatIsNotANumber() {
        assertThatThrownBy(() -> strict(Gait.class, "test.enabled=true\ntest.speed=fast"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test.speed");
    }

    @Test
    void takesTheFallbackForWhatIsMissingOrBrokenAndSaysSo() {
        ConfigBinder.Bound<Gait> bound = ConfigBinder.bind(
                Gait.class, "test", text("test.speed=fast\ntest.extra=1"), text("test.enabled=true\ntest.speed=3"));

        assertThat(bound.value()).isEqualTo(new Gait(true, 3f));
        assertThat(bound.problems())
                .anySatisfy(problem -> assertThat(problem).contains("missing test.enabled"))
                .anySatisfy(problem -> assertThat(problem).contains("test.speed"))
                .anySatisfy(problem -> assertThat(problem).contains("unknown key test.extra"));
    }

    @Test
    void readsKeyValueLinesAndSkipsCommentsAndBlanks() {
        assertThat(ConfigBinder.parse("# a comment\n\n  a.b = 1 \n! another\nc=two words\n"))
                .containsExactly(java.util.Map.entry("a.b", "1"), java.util.Map.entry("c", "two words"));
    }

    private static <T extends Record> T strict(Class<T> type, String text) {
        return ConfigBinder.bindStrictly(type, "test", text(text));
    }

    private static SortedMap<String, String> text(String text) {
        return new TreeMap<>(ConfigBinder.parse(text));
    }

    private static String sharedChances() {
        return """
                sim.ai.chance.swing_at_a_leap=0.5
                sim.ai.chance.bite_a_barrel=0.8
                sim.ai.chance.aid_a_trooper=0.4
                sim.ai.chance.man_a_turret=2
                sim.ai.chance.aid_the_commander=1.5
                sim.ai.chance.struggle=0.4
                sim.ai.chance.longestGap=2
                """;
    }
}
