package pl.mzebrows.pwt.sim.def;

/**
 * The role a renderer draws. Names a part in the game, never a colour or a shape - what a look turns
 * into on screen is decided entirely in the client module (G0003).
 *
 * <p>It lives here rather than on {@code Appearance} because a look is part of what a thing
 * <em>is</em>, not part of what it is doing: a unit's look comes from its strain definition and
 * changes only when the strain does. {@code Appearance.State} is the other axis and stays with the
 * component, because that genuinely is runtime state.
 *
 * <p>Adding a value here means adding a {@code graphic.color.<name>} key to
 * {@code graphic.properties}; {@code ConfigDefaultsTest.mapsEveryLookToAColour} fails the build if
 * one is missed.
 */
public enum Look {
    /** Nothing meaningful set - a pooled component that was reset and not yet reused. */
    NONE,
    COMMANDER,
    /** One AI-controlled member of the squad. */
    TROOPER,
    /** The basic chasing strain. */
    GRUNT,
    /** The heavy chasing strain. */
    BRUTE,
    /** The strain that holds its ground and shoots. */
    SPITTER,
    /** Ground a squad takes by standing on it. */
    CONTROL_POINT,
    PROJECTILE,
    /** A heavy shot, so a gun is visible in what it fires. */
    SLUG,
    /** What a spitter fires. It must not be mistakable for anything of the player's. */
    SPIT,
    /**
     * What a spitter fires once it has eaten a barrel. It goes off where it stops, so it must be
     * tellable from an ordinary spit at a glance - the ground beside one is no longer safe.
     */
    CHARGED_SPIT,
    STRIKE,
    /** Something thrown that bounces and goes off where it stops. */
    GRENADE,
    /** The circle an explosion makes lethal, for a moment. */
    BLAST,
    OBSTACLE,
    /** Alien food growing on the field. */
    MUSHROOM,
    /** Alien food that transforms whatever eats it. */
    FLOWER,
    /** A trooper in stasis, waiting to be thawed or eaten. */
    FROZEN_TROOPER,
    /** A weapons cache. */
    ARMOURY,
    /** Something on the field that goes off when it is killed. */
    BARREL,
    /** Alien food on legs. */
    RABBIT,
    /**
     * The work of putting a man back on his feet, drawn on whoever is doing it.
     *
     * <p>A look rather than a second colour setting, because the renderer already maps a look to a
     * colour and nothing else needs inventing (G0003). It is the first look that is not a thing
     * standing on the field, which is the point: {@code Appearance.chargeLook} names what a charge
     * <em>is</em>, and the general channel had until now been drawn in one colour whatever it meant.
     */
    MENDING
}
