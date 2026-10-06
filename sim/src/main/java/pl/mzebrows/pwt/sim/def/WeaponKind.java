package pl.mzebrows.pwt.sim.def;

/**
 * Every gun the commander can be carrying.
 *
 * <p>{@link #SIDEARM} is what he starts and respawns with; anything better is found and lost (pillar 3).
 */
public enum WeaponKind {
    /** The gun he starts every life with. Two shots kill a grunt. */
    SIDEARM,
    /** Slow, heavy and fast-flying. One shot kills anything a sidearm needs five for. */
    RAILGUN,
    /**
     * A wall of fire a few strides long: several short-lived pellets per pull in a wide cone. It runs
     * hot, and trades reach.
     */
    FLAMER,
    /**
     * A handful of pellets thrown wide with every pull, slowly: devastating up close. It stays cold;
     * it costs the wait between pulls.
     */
    SHOTGUN,
    /**
     * A beam: a stream of very small, very fast shots with a reach.
     *
     * <p><strong>It stops at the first thing it touches</strong>, and the drawn line stops with it.
     * It slows what it lands on, slows the man holding it, runs hottest of anything carried, and
     * inflates a body it is held on ({@code Bloat}).
     */
    LASER,
    /**
     * One fast rocket that goes off where it stops - on the first thing it touches or at its reach - in
     * a blast on nobody's side, which wounds a man caught in it as a grenade's does.
     *
     * <p>Every pull runs it to the stop, so the wait for the next rocket is its heat bar, drawn over him.
     * Every pull also kicks him back along his aim.
     */
    BAZOOKA,
    /**
     * Fast, small, quick bolts - a turret's kind of fire, less of it - that run hot, slower than the beam.
     *
     * <p>Each bolt that lands builds <em>shock</em> on what it hits ({@code Shock}); full shock stuns it,
     * and a bigger body takes more.
     */
    PULSE,
    /**
     * A slow-repeating disc that comes off what it hits - rock, the arena's edge, scenery and the enemy
     * alike - landing a blow on every body it comes off and passing through its own side, until its bounces
     * or its reach run out.
     *
     * <p>Like the bazooka, every pull runs it to the stop and kicks him back, and the ground shakes.
     */
    DISC_GUN,
    /**
     * What a turret fires: light, very fast and quick to repeat. No pickup grants it.
     */
    TURRET_GUN,
    /**
     * What a spitter fires: slow enough to be seen coming and walked out of. No pickup grants it.
     */
    SPIT,
    /**
     * What a frog fires: a fat, slow, short-lived shot that bursts into a spatter of ruined ground.
     *
     * <p>A miss bursts short at its reach. The spatter is the gun's {@code SlickDef} (G0018). No pickup
     * grants it.
     */
    VENOM,
    /**
     * What a spitter fires after it has eaten something explosive. The same slow shot, and it goes
     * off where it stops (G0022).
     *
     * <p>A kind, not a flag, so the power-up is a definition (G0008).
     */
    CHARGED_SPIT,
    /**
     * What the boss throws in its patterns: slower than a spit and half as hard, because it comes
     * twenty at a time and the pattern is the threat rather than any one shot (G0048). No pickup
     * grants it.
     */
    SPEW,
    /**
     * What a searer sprays (G0071): a short beam that wounds a man and slows him, fired a cone at a time.
     * Shorter than the laser it is drawn like, and no bloat. No pickup grants it.
     */
    SEAR,
    /**
     * What a thumper slams (G0071): a ring of short, fast shots all the way round it that wound and stun a man,
     * read as the one circle they cover. No pickup grants it.
     */
    SLAM
}
