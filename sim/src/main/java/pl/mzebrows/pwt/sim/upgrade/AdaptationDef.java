package pl.mzebrows.pwt.sim.upgrade;

import pl.mzebrows.pwt.sim.def.Require;

/**
 * When the swarm adapts (D11): on a schedule, at random from the aliens' pool, with nobody choosing.
 * Both halves of the schedule are off at zero, and a level may turn either on.
 *
 * @param everySeconds one adaptation each time this much level time has passed - the first after it,
 *     not at once; zero is off
 * @param perPlayerUpgrades one adaptation each time the players have taken this many upgrades; zero is
 *     off
 * @param answerWeight how much likelier an adaptation that answers an upgrade the players hold is dealt:
 *     its weight times one plus this, per rank they hold; zero is off - the reactive hook, not yet used
 */
public record AdaptationDef(float everySeconds, int perPlayerUpgrades, int answerWeight) {

    public AdaptationDef {
        Require.notNegative("adaptation everySeconds", everySeconds);
        Require.notNegative("adaptation perPlayerUpgrades", perPlayerUpgrades);
        Require.notNegative("adaptation answerWeight", answerWeight);
    }
}
