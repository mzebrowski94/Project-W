package pl.mzebrows.pwt.sim.def;

/**
 * The role a renderer draws. Names a part in the game, never a colour or a shape - what a look turns
 * into on screen is decided entirely in the client module (G0003).
 *
 * <p>What a thing <em>is</em>, from its definition; {@code Appearance.State} is what it is doing.
 * Adding a value here means adding a {@code graphic.color.<name>} key to
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
    /**
     * The strain in two segments, drawn as two: a body and, along its facing, a head a shade darker -
     * the part a bullet glances off (pillar 5).
     */
    ANT,
    /** The boss: a grunt grown huge. Kin to a grunt at a glance, and darker, because it does not move. */
    BOSS_GRUNT,
    /** A giant that sears: a brute's darker, bigger cousin (G0071). */
    SEARER,
    /** A giant that thumps: the same body, paler, as the circle it slams (G0071). */
    THUMPER,
    /** Ground a squad takes by standing on it. */
    CONTROL_POINT,
    /** Ground a level says you must hold, and the other side walks at. */
    POST,
    /**
     * A hole in the ground that keeps sending more of one strain out of it.
     *
     * <p>What comes out of it is named on the charge channel, in that strain's colour.
     */
    NEST,
    /** A gun on a mount that a man gets on. Square rather than round, because it is not a creature. */
    TURRET,
    /** A shut door: its doorway's squares, apart from the wall either side of it so it reads as the way in (G0069). */
    DOOR,
    PROJECTILE,
    /** A heavy shot, so a gun is visible in what it fires. */
    SLUG,
    /** A bazooka's rocket: the one shot on the field that is about to be an explosion. */
    ROCKET,
    /** A pulse rifle's bolt: small, quick and electric, the colour of the shock it builds. */
    BOLT,
    /** A disc gun's disc: white-gold, and the one shot on the field that comes back off what it hits. */
    DISC,
    /**
     * One pellet of flame, read as the cloud rather than any one of them.
     */
    FLAME,
    /**
     * One pellet of buckshot, kin to the sidearm's round: the fan is what tells a shotgun apart.
     */
    PELLET,
    /**
     * One shot of the beam, read as the line rather than any one of them.
     */
    LASER,
    /**
     * What a turret fires: unmistakably the player's.
     */
    TURRET_SHOT,
    /** What a spitter fires. It must not be mistakable for anything of the player's. */
    SPIT,
    /**
     * What a spitter fires once it has eaten a barrel; tellable from an ordinary spit at a glance.
     */
    CHARGED_SPIT,
    /** A searer's beam: the laser's kind of line, in a colour no gun of the player's has. */
    SEAR,
    /** A thumper's slam, and the circle it is read as: white. */
    SLAM,
    /** What a frog fires. Fat and slow, and unmistakably the same stuff as the slick it leaves. */
    VENOM,
    /** What the boss throws, twenty at a time. Must read against every other shot, the player's and a spit alike. */
    SPEW,
    /**
     * One circle of ground a frog has ruined.
     *
     * <p>Kin to the shot that made it, and darker: it is ground.
     */
    SLICK,
    /**
     * One circle of ground still burning after an explosion.
     *
     * <p>Kin to the blast it came out of, and darker. Reads as <em>not walkable yet</em>.
     */
    FIRE,
    STRIKE,
    /** Something thrown that bounces and goes off where it stops. */
    GRENADE,
    /** The circle an explosion makes lethal, for a moment. */
    BLAST,
    /**
     * A body being pumped up by a beam, on the charge channel.
     *
     * <p>Kin to the blast it will become: "that one is a bomb now".
     */
    BLOAT,
    /**
     * Shock building on a body the pulse rifle is held on, on the charge channel: full is a stun.
     *
     * <p>The colour of the bolts that build it.
     */
    SHOCK,
    OBSTACLE,
    /** Alien food growing on the field. */
    MUSHROOM,
    /** Alien food that transforms whatever eats it. */
    FLOWER,
    /** A crate of men, waiting for somebody on the player's side to reach it. */
    SUPPLY_DROP,
    /** A medkit, waiting for a wounded man (G0053). */
    MEDKIT,
    /**
     * A weapons cache, one look per gun inside it.
     *
     * <p>A look per gun, so which gun is inside is a colour, not drawing code (G0003, pillar 5).
     */
    ARMOURY_RAILGUN,
    ARMOURY_FLAMER,
    ARMOURY_SHOTGUN,
    ARMOURY_LASER,
    ARMOURY_BAZOOKA,
    ARMOURY_PULSE,
    ARMOURY_DISC,
    /**
     * A crate (G0056): a small square with a dot, because what it holds is nobody's to see until it is
     * opened - one look for every crate, whatever is inside.
     */
    CRATE,
    /** Something on the field that goes off when it is killed. */
    BARREL,
    /**
     * A small thing lying in the open that goes off when the other side walks into it.
     *
     * <p>Deliberately dull and small; the {@link #FUSE} is the warning.
     */
    MINE,
    /**
     * The fuse on a mine that has started ticking.
     *
     * <p>On the charge channel, and the only thing there darker than what it is drawn on.
     */
    FUSE,
    /** Alien food on legs. */
    RABBIT,
    /**
     * The work of putting a man back on his feet, drawn on whoever is doing it.
     *
     * <p>A charge look ({@code Appearance.chargeLook}), not a thing standing on the field.
     */
    MENDING,
    /**
     * How hot a gun has got. A charge look, like {@link #MENDING}.
     */
    HEAT,
    /**
     * A gun that has stopped itself; told apart from {@link #HEAT} at a glance.
     */
    OVERHEAT,
    /**
     * The slow pulse on something worth crossing the ground for.
     *
     * <p>A charge look that is never full and never empty, so it reads as a glow.
     */
    GLEAM
}
