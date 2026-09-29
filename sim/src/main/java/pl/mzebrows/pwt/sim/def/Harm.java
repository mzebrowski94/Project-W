package pl.mzebrows.pwt.sim.def;

/**
 * What a blow does to a man on the player's side, declared by its source (G0020, G0053).
 *
 * <p>Named for what it does to a man with no wounds. Anything with health reads the blow's amount
 * instead and never this. Each carries its severity, so the worst of a tick's blows wins: {@link #worse}.
 */
public enum Harm {
    /** One wound. One past his maximum puts him down; several on one tick are one. */
    WOUND(1),
    /** Puts him down whatever his wounds, and finishes a man who is down already. */
    DOWN(2),
    /** Kills him outright, standing or down. */
    KILL(3);

    private final int severity;

    Harm(int severity) {
        this.severity = severity;
    }

    /** The more severe of the two. */
    public static Harm worse(Harm first, Harm second) {
        return first.severity >= second.severity ? first : second;
    }
}
