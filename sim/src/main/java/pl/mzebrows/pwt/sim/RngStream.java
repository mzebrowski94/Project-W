package pl.mzebrows.pwt.sim;

/**
 * The concerns that each draw from a stream of their own, and the salt that separates them.
 *
 * <p><strong>One stream per concern</strong> is the reason this exists. With a single generator,
 * every consumer shares one sequence, so adding a draw anywhere - or re-tuning how often one system
 * asks for a number - shifts every number every other system receives afterwards. The run is still
 * reproducible, which is why no test catches it; what breaks is iteration, because a layout you had
 * just settled on reshuffles when you touch something unrelated to it. The {@code game-patterns}
 * skill names this as the usual reason procedural work is miserable to iterate on.
 *
 * <p>Every stream is derived from the one level seed, so a level is still a function of a single
 * number and pillar 6 holds unchanged.
 *
 * <p>The salts are arbitrary 64-bit constants. They are constants rather than ordinals so that
 * reordering or inserting a value here cannot silently re-roll every other stream, and they live in
 * one enum so two concerns cannot quietly end up sharing one.
 */
public enum RngStream {

    /** Map generation: terrain, regions, where rock goes. */
    MAP(0x51ED_270B_6EFC_2FB7L),

    /** How long a unit hesitates before it throws itself at the commander. */
    LEAP(0x2545_F491_4F6C_DD1DL),

    /** Where an idle unit wanders next, and how long it stands still first. */
    WANDER(0x9E37_79B9_7F4A_7C55L),

    /** How a trooper sweeps its attention as it advances, and where it roams with nothing left to take. */
    TROOPER(0xD1B5_4A32_D192_ED03L),

    /** How far off true a spitter's shot leaves its mouth. */
    SPIT(0xA3C5_9AC3_0F8C_2E71L),

    /**
     * Every occasional decision the AI leaves to the dice - everything named in {@code AiChance}.
     *
     * <p>One stream for all of them rather than one each, because they are one concern: how often a
     * unit does something it only sometimes does. Splitting them would mean a salt to keep apart per
     * behaviour, and the thing a separate stream buys - retuning one without reshuffling the others -
     * is already bought by {@code Odds} drawing nothing at all when a chance is zero.
     */
    NERVE(0x6C8E_9CF5_7037_08D3L);

    private final long salt;

    RngStream(long salt) {
        this.salt = salt;
    }

    /** The constant mixed into the level seed to separate this stream from the others. */
    public long salt() {
        return salt;
    }
}
