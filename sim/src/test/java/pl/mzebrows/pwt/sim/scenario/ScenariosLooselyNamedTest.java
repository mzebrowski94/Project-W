package pl.mzebrows.pwt.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** What {@code --scenario} opens: a registered scenario by its name typed loosely, and nothing else. */
class ScenariosLooselyNamedTest {

    @Test
    void findsEveryRegisteredScenarioByItsNameLowerCasedWithHyphens() {
        for (Scenario scenario : Scenarios.all()) {
            String typed = scenario.name().toLowerCase(Locale.ROOT).replace(' ', '-');

            assertThat(Scenarios.looselyNamed(typed)).as(typed).isSameAs(scenario);
            assertThat(Scenarios.typedName(scenario)).isEqualTo(typed);
        }
    }

    @Test
    void readsOneNameHoweverItIsSpacedOrCased() {
        Scenario fullHouse = Scenarios.named("Full House");

        assertThat(Scenarios.looselyNamed("full-house")).isSameAs(fullHouse);
        assertThat(Scenarios.looselyNamed("FullHouse")).isSameAs(fullHouse);
        assertThat(Scenarios.looselyNamed("Full House")).isSameAs(fullHouse);
        assertThat(Scenarios.looselyNamed("full_house")).isSameAs(fullHouse);
    }

    @Test
    void findsNothingForANameNoScenarioHas() {
        assertThat(Scenarios.looselyNamed("full-hose")).isNull();
        assertThat(Scenarios.looselyNamed("")).isNull();
    }

    /** A mission is opened by its code and the proving ground from its page, never by this name. */
    @Test
    void neverFindsAMissionOrTheProvingGround() {
        assertThat(Scenarios.looselyNamed("mission 1")).isNull();
        assertThat(Scenarios.looselyNamed("mission easy")).isNull();
        assertThat(Scenarios.looselyNamed("proving-ground")).isNull();
    }
}
