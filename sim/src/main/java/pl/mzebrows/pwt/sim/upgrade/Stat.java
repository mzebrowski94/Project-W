package pl.mzebrows.pwt.sim.upgrade;

/**
 * Every number an upgrade may move. Each is read in exactly one place: one {@link Reads} method of
 * {@link Modifiers}, with one caller. A stat nobody reads fails the build, rather than being a number
 * somebody tunes to no effect (ADR 0017).
 */
public enum Stat {
    /** How long a gun waits between pulls. Read where every shooter's pull is charged. */
    FIRE_COOLDOWN(Form.SCALE, Reader.GUN),
    /** What each shot of one pull does. Read once per volley. */
    GUN_DAMAGE(Form.SCALE, Reader.GUN),
    /** How many men a crate of reinforcements is worth. Read where the crate is spent. */
    MEN_PER_CRATE(Form.COUNT, Reader.SIDE),
    /** How many men past its starting size the squad may hold. Read where a man is landed. */
    SQUAD_HEADROOM(Form.COUNT, Reader.SIDE),
    /** How long a thrower waits between grenades. Read where a grenade leaves the hand. */
    GRENADE_COOLDOWN(Form.SCALE, Reader.UNIT),
    /** How often an occasional behaviour happens, one per {@code AiChance}. Read where the dice are asked. */
    CHANCE(Form.CHANCE, Reader.UNIT),
    /** How fast a driven unit goes, as a share of its pace. Read where a course is committed. */
    SPEED(Form.SCALE, Reader.DRIVEN),
    /** How fast a man's work on a casualty goes. Read where the work is done, for whoever is doing it. */
    STABILISE_SPEED(Form.SCALE, Reader.UNIT),
    /** How long a stun leaves the unit it lands on unable to act. Read where a stun is taken, for whoever takes it. */
    STUN_DURATION(Form.SCALE, Reader.UNIT),
    /**
     * How many wounds a man carries on his feet (G0053). Read where a hit is weighed, for the man it lands
     * on - every tick, so the pips he is drawn with are the number the next hit will be weighed against.
     */
    MAX_WOUNDS(Form.COUNT, Reader.UNIT),
    /** How long after his last wound a man's wounds clear on their own, in seconds; zero never. Read where it runs. */
    MEND_SECONDS(Form.COUNT, Reader.UNIT);

    /** How an effect moves the number. */
    public enum Form {
        /** By a whole percentage of its base, held inside the stat's range. */
        SCALE,
        /** By whole amounts added to its base, held at the stat's floor. */
        COUNT,
        /** By chances per second added to its base, never below zero. */
        CHANCE
    }

    /** Who reads it, which decides whom an effect on it may target. */
    public enum Reader {
        /** A unit, about the gun it is holding: an effect may name the gun. */
        GUN,
        /** A unit, about itself. */
        UNIT,
        /**
         * A unit whose course {@code DriveSystem} commits - a trooper or an alien. Never a commander,
         * whose velocity is his own control's (G0010), so no effect reaching one may name it.
         */
        DRIVEN,
        /** Nobody in particular: the side's own number, such as what a crate is worth. */
        SIDE
    }

    private final Form form;
    private final Reader reader;

    Stat(Form form, Reader reader) {
        this.form = form;
        this.reader = reader;
    }

    public Form form() {
        return form;
    }

    public Reader reader() {
        return reader;
    }
}
