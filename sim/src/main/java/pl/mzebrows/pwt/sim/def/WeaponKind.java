package pl.mzebrows.pwt.sim.def;

/**
 * Every gun the commander can be carrying.
 *
 * <p>{@link #SIDEARM} is what he starts with and what he comes back with, so a gun is something you
 * find and then have to keep hold of rather than something you own. That is the whole of the
 * progression a weapon pickup adds, and it is deliberately the whole of it: pillar 3 says nothing is
 * bought, equipped or configured between levels.
 */
public enum WeaponKind {
    /** The gun he starts every life with. Two shots kill a grunt. */
    SIDEARM,
    /** Slow, heavy and fast-flying. One shot kills anything a sidearm needs five for. */
    RAILGUN,
    /**
     * A wall of fire a few strides long. It throws several fat, short-lived pellets per pull into a
     * wide cone, so it covers ground rather than a point - and the ground it covers is right in front
     * of him, because the flame goes out.
     *
     * <p>It runs hot, which is the whole of what stops it being the answer to everything: held down
     * it takes itself away, and it is the gun most likely to be held down. Reach, not damage, is what
     * it trades - anything at all past its range is a problem for a different gun.
     */
    FLAMER,
    /**
     * A handful of pellets thrown wide with every pull, slowly. Devastating with the muzzle against
     * something and close to useless across a room: the cone is the same width at every distance, so
     * how much of it lands is entirely how close he was willing to get.
     *
     * <p>It stays cold. What it costs is the wait between pulls, which is long enough that a miss is
     * a decision the player has to live with.
     */
    SHOTGUN,
    /**
     * What a turret fires. Light, very fast and quick to repeat: it trades the damage of a carried
     * gun for a rate and a flight time neither side can step out of.
     *
     * <p>It is in the same table as the commander's guns and cannot be picked up, because nothing
     * grants it - a turret is somewhere you stand, not something you carry away.
     */
    TURRET_GUN,
    /**
     * What a spitter fires. Slow enough in the air to be seen coming and walked out of, which is
     * what makes a firing line something to read rather than something to be hit by.
     *
     * <p>It is in the same table as the commander's guns and cannot be picked up, because nothing
     * grants it: what a pickup offers each side is in its {@code PickupDef}, and no pickup offers
     * this.
     */
    SPIT,
    /**
     * What a spitter fires after it has eaten something explosive. The same slow shot, and it goes
     * off where it stops (G0022).
     *
     * <p>A kind rather than a flag on the spitter, so the power-up is a definition and a block in
     * {@code game.properties} - what it costs the player is that the ground beside a shot is no
     * longer safe, and every number in that is tunable without touching a system.
     */
    CHARGED_SPIT
}
