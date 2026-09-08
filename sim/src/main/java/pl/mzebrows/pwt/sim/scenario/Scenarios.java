package pl.mzebrows.pwt.sim.scenario;

import java.util.List;

/**
 * Every scenario the menu offers, in the order it offers them.
 *
 * <p>This list is the whole registry. A new scenario is a class plus a line here - if you add one
 * and it does not appear in the menu, this is the line you forgot.
 */
public final class Scenarios {

    private static final List<Scenario> ALL =
            List.of(new SkirmishScenario(), new DuelScenario(), new FeedingGroundScenario(), new CrowdScenario());

    private Scenarios() {}

    /** Every registered scenario. Immutable. */
    public static List<Scenario> all() {
        return ALL;
    }
}
