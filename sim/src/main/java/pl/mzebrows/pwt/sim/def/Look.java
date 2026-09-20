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
    /** The fast strain that weaves and dodges. It must read as lighter than a grunt at a glance. */
    RUNNER,
    /** The strain that hops and ruins the ground. It must read as kin to what it spits. */
    FROG,
    /** Ground a squad takes by standing on it. */
    CONTROL_POINT,
    /** Ground a level says you must hold, and the other side walks at. */
    POST,
    /**
     * A hole in the ground that keeps sending more of one strain out of it.
     *
     * <p>Drawn as a thing you dig into rather than a creature standing on the ground - the client's
     * business, like every other "how is this drawn" (G0003). What comes out of it is named on the
     * charge channel instead, in that strain's own colour, so the nest says what it makes without the
     * renderer being told what a nest is.
     */
    NEST,
    /** A gun on a mount that a man gets on. Square rather than round, because it is not a creature. */
    TURRET,
    PROJECTILE,
    /** A heavy shot, so a gun is visible in what it fires. */
    SLUG,
    /**
     * One pellet of flame. Fat, short-lived and thrown in numbers, so what the player reads is the
     * cloud rather than any one of them.
     */
    FLAME,
    /**
     * One pellet of buckshot. Deliberately kin to the sidearm's round rather than a colour of its
     * own: it <em>is</em> a small bullet, and what tells a shotgun apart is that six of them arrive
     * at once in a fan.
     */
    PELLET,
    /**
     * What a turret fires: light, very fast, and unmistakably the player's. It arrives faster than
     * anything else on the field, so it must not be read as something coming the other way.
     */
    TURRET_SHOT,
    /** What a spitter fires. It must not be mistakable for anything of the player's. */
    SPIT,
    /**
     * What a spitter fires once it has eaten a barrel. It goes off where it stops, so it must be
     * tellable from an ordinary spit at a glance - the ground beside one is no longer safe.
     */
    CHARGED_SPIT,
    /** What a frog fires. Fat and slow, and unmistakably the same stuff as the slick it leaves. */
    VENOM,
    /**
     * One circle of ground a frog has ruined.
     *
     * <p>Drawn in the same colour family as the shot that made it, because the whole of what the
     * player has to learn is that one becomes the other, and darker, because it is ground rather
     * than a thing standing on it. A control point says the same thing by being spawned first; a
     * slick appears mid-fight and cannot, so the renderer draws it underneath - which is the
     * client's business and settled there (G0003).
     */
    SLICK,
    /**
     * One circle of ground still burning after an explosion.
     *
     * <p>Kin to the blast it came out of and darker, because it is ground rather than a thing
     * standing on it - the same relationship a slick has to the venom that made it. It swells and
     * collapses and it is on nobody's side, so what it has to read as at a glance is <em>not walkable
     * yet</em>, unmistakably, from across a fight somebody else is having.
     */
    FIRE,
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
    /** A crate of men, waiting for somebody on the player's side to reach it. */
    SUPPLY_DROP,
    /**
     * A weapons cache, one look per gun inside it.
     *
     * <p>Three crates rather than one crate with a label, because the renderer decides what to draw
     * from a look and asks nothing else about an entity (G0003) - so "which gun is in this one" costs
     * a constant here and a colour in {@code graphic.properties} rather than any drawing code. A
     * player who cannot tell what he is walking across a level for is guessing (pillar 5).
     */
    ARMOURY_RAILGUN,
    ARMOURY_FLAMER,
    ARMOURY_SHOTGUN,
    /** Something on the field that goes off when it is killed. */
    BARREL,
    /**
     * A small thing lying in the open that goes off when the other side walks into it.
     *
     * <p>Deliberately dull and deliberately small: a mine the player can see from across the level
     * is a mine the other side would obviously never walk into, and it has to read as something
     * lying on the ground rather than as something standing on it. What makes one dangerous is the
     * {@link #FUSE} once it has started, not the dot before it.
     */
    MINE,
    /**
     * The fuse on a mine that has started ticking.
     *
     * <p>A look rather than a colour setting, for the reason {@link #MENDING} is one: the renderer
     * maps a look to a colour and asks nothing else (G0003). It rides the charge channel and is the
     * only thing on it that is darker than what it is drawn on - everything else that fills up is
     * brightened, and a thing that is about to kill you must not look like a thing filling up.
     */
    FUSE,
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
    MENDING,
    /**
     * How hot a gun has got. Not a thing standing on the field, like {@link #MENDING}: it is a look
     * so that the one place that maps a role to a colour maps this one too (G0003).
     */
    HEAT,
    /**
     * A gun that has stopped itself. Told apart from {@link #HEAT} at a glance on purpose - it is the
     * half of the bar that changes what the player should do next.
     */
    OVERHEAT,
    /**
     * The slow pulse on something worth crossing the ground for.
     *
     * <p>A look rather than a flag, for the reason {@link #MENDING} is one: the renderer maps a look
     * to a colour and asks nothing else (G0003), so "this thing shines" costs a colour in
     * {@code graphic.properties} and no drawing code at all. It rides the charge channel, which is
     * already "how full is this" - a thing that is never full and never empty reads as a glow.
     */
    GLEAM
}
