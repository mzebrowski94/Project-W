package pl.mzebrows.pwt.sim;

/**
 * The concerns that each draw from a stream of their own, and the salt that separates them.
 *
 * <p><strong>One stream per concern</strong>, so retuning one never reshuffles another. All derive
 * from the level seed (pillar 6). Salts are constants, not ordinals, so reordering re-rolls nothing.
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
     * How far off true each pellet of a gun that throws more than one leaves the muzzle.
     *
     * <p>Not {@link #SPIT}, which is how badly a creature aims.
     */
    SCATTER(0x7B3D_1E6A_C420_95F9L),

    /**
     * Every occasional decision the AI leaves to the dice - everything named in {@code AiChance}.
     *
     * <p>One stream: {@code Odds} draws nothing when a chance is zero.
     */
    NERVE(0x6C8E_9CF5_7037_08D3L),

    /** Where the aliens in a wave are put down when it arrives. */
    WAVE(0x8E9C_5AB1_02C7_6D45L),

    /** How big a nest's brood is, and where it is put down around the hole. */
    NEST(0x2A71_C6E4_9B03_58DFL),

    /**
     * Where a reinforcement is put down: a rescued trooper forming up on the commander, and the crate
     * a level lays beside a control point.
     *
     * <p>One concern: where a reinforcement lands.
     */
    REINFORCEMENT(0x4F2C_9D17_A85B_3E61L),

    /**
     * Where the circles of a spatter fall around the shot that burst.
     *
     * <p>Its own, so tuning a slick's shape moves nothing else.
     */
    SLICK(0x3B9A_C74D_1E52_806FL),

    /**
     * The cards a draft is dealt, when it is earned, and any card re-dealt in place (G0049).
     *
     * <p>Its own, so earning a draft never moves where a wave lands or how a man aims.
     */
    DRAFT(0xC2B2_AE3D_27D4_EB4FL),

    MEDKIT(0x94D0_49BB_1331_11EBL),

    /**
     * What a crate holds - how many, and which - dealt from the armoury deck the first time it is opened
     * (G0056). Its own, so opening a crate never moves where a wave lands, and stacking the deck moves
     * nothing else.
     */
    ARMOURY(0x2B99_2DDF_A232_49D5L),

    /**
     * Which adaptation the swarm takes when one is due (ADR 0017). Its own, so the swarm adapting never moves
     * what a draft deals or where a wave lands, and a schedule that is off draws nothing at all.
     */
    ADAPTATION(0x1656_67B1_9E37_79F9L),

    /**
     * Where the riders a slinger still carried spill round its body when it dies (G0074). Its own, for the reason
     * {@link #NEST} has one: retuning a slinger must not move a nest's brood.
     */
    SLING(0x7C15_9E3B_44D2_A6F1L),

    /**
     * What a mission is built from besides its ground (G0065): the order its biomes are dealt in, their counts and
     * where everything stands. Only ever derived from - per sub-concern and per position - and only while the world
     * is built, so it sits here as one more long a snapshot carries and its children never do.
     */
    MISSION(0x6E3F_A1C9_58B2_D40BL),

    /**
     * Where the grenade caches a taken point lays fall round it (G0078). Its own, so taking a point never moves where
     * a reinforcement lands, and laying none draws nothing.
     */
    CACHE(0x5D3A_8F21_C76E_B419L),

    /**
     * How long each geyser lies quiet before it fills again, keyed per geyser and per cycle (G0089) - never advanced,
     * so a geyser's rhythm is its own and retuning it moves nothing else.
     */
    GEYSER(0x8A5D_3C17_E96B_24F3L),

    /**
     * Which way a snake bends next and for how long (G0095). Its own, so a snake on the field never moves where
     * anything else wanders, and a level without one draws nothing.
     */
    SLITHER(0x3E91_C7A5_0D6B_F248L);

    private final long salt;

    RngStream(long salt) {
        this.salt = salt;
    }

    /** The constant mixed into the level seed to separate this stream from the others. */
    public long salt() {
        return salt;
    }
}
