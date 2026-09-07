package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;

/**
 * One arena the alpha-phase menu can launch, built to demonstrate and test a system by hand.
 *
 * <p>A scenario is a Java class rather than a data file on purpose: it can script a spawn sequence,
 * place things in a deliberately awkward arrangement, or set up the exact situation a system is
 * meant to handle - none of which a spawn list expresses. Adding one means a recompile, which costs
 * nothing here.
 *
 * <p>Scenarios are content, not design. Numbers in one are chosen to make a system visible, not to
 * balance the game, and a scenario is free to be unfair or absurd if that is what shows the system
 * working.
 */
public interface Scenario {

    /** Short name, as it appears in the menu. */
    String name();

    /** One line saying what this scenario is for - which system it exercises, and what to look at. */
    String description();

    /**
     * Adjusts the config this scenario runs with. The default changes nothing; override to vary one
     * thing - a crowd of enemies, an empty arena - without restating the rest.
     */
    default SimConfig configure(SimConfig base) {
        return base;
    }

    /** Fills an empty simulation. Spawn the commander first, so it is always present. */
    void populate(Simulation simulation);
}
