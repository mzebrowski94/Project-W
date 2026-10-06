package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.BarrageDef;
import pl.mzebrows.pwt.sim.def.PatternDef;

/**
 * The cycle of patterns a boss or a giant shoots, and how far through it it is (G0048).
 *
 * <p>Carried by every strain and switched off for all but one, like every other ability. The patterns
 * are the definition's own records, so a snapshot carries them as it carries everything else; where
 * it is in the cycle is counted in ticks and salvos, never rolled.
 */
public final class Barrage implements Component, Pool.Poolable {

    /** Where in the cycle it is. */
    public enum Phase {
        /** Nobody to shoot at. It picks up where it left off when somebody turns up. */
        IDLE,
        /** Showing what is coming. The window the player has to get out of the way. */
        WINDING_UP,
        /** Firing the current pattern's salvos. */
        FIRING,
        /** Holding fire after a pattern: the gap to walk in through. */
        RESTING
    }

    /** Whether this strain has a barrage at all. Configuration. */
    public boolean enabled;

    /** How long a wind-up lasts, in ticks, before anything makes it faster. Configuration. */
    public int windUpTicks;

    /** How long a rest lasts, in ticks, before anything makes it faster. Configuration. */
    public int restTicks;

    /** The fraction of its health below which it works faster. Configuration. */
    public float enragedBelow;

    /** What its timings are multiplied by once it does. Configuration. */
    public float enragedFactor = 1f;

    /** The first pattern of the cycle. Configuration. */
    public PatternDef ring = PatternDef.off();

    /** The second. Configuration. */
    public PatternDef fan = PatternDef.off();

    /** The third. Configuration. */
    public PatternDef spiral = PatternDef.off();

    /** The fourth. Configuration. */
    public PatternDef stream = PatternDef.off();

    /** How near its target must be before it starts a pattern; zero is any distance. Configuration. */
    public float reach;

    /** Whether it stands still while winding up and firing. Configuration. */
    public boolean holdsStill;

    /** Whether a declared stun breaks off the pattern it is on. Configuration. */
    public boolean stunCancels;

    /** How hard the ground shakes at it when it starts winding up - its roar. Configuration. */
    public float roarStrength;

    /** How far from it the roar is felt. Configuration. */
    public float roarRadius;

    /** How long the roar's shake lasts, in ticks. Configuration. */
    public int roarTicks;

    public Phase phase = Phase.IDLE;

    /** How far from its middle the circle of its last salvo reaches, in world units, while it is drawn. */
    public float burstReach;

    /** Ticks the circle of its last salvo is still drawn for; zero is none. */
    public int burstTicks;

    /** How long that circle was drawn for when it began, in ticks. */
    public int burstTotal;

    /** Which pattern it is winding up, firing or has just fired, as its place in the cycle. */
    public int pattern;

    /** Ticks until the phase changes or the next salvo leaves. */
    public int remainingTicks;

    /** How long the current phase was when it began, in ticks: what a wind-up is a fraction of. */
    public int phaseTicks;

    /** How many salvos of the current pattern have left. */
    public int salvo;

    /** How many times it has been all the way round, which is what decides which way a pattern turns. */
    public int cycles;

    /** Whether its going down has been counted on the level, so a boss is counted once. */
    public boolean fallen;

    /**
     * Which way it is shooting - at whoever it hunts, or the place it was hurt from - as a direction of any
     * length; {@code (0, 0)} until it has had something to shoot at. Kept while it idles, so it goes on
     * looking where it last shot. What {@code FacingSystem} turns it by: nothing else turns a thing that
     * never walks.
     */
    public float aimX;

    public float aimY;

    /** Sets the barrage from its definition and returns this component. Starts nothing. */
    public Barrage set(BarrageDef def, int newWindUpTicks, int newRestTicks, int newRoarTicks) {
        this.enabled = def.enabled();
        this.windUpTicks = newWindUpTicks;
        this.restTicks = newRestTicks;
        this.enragedBelow = def.enragedBelow();
        this.enragedFactor = def.enragedFactor();
        this.ring = def.ring();
        this.fan = def.fan();
        this.spiral = def.spiral();
        this.stream = def.stream();
        this.reach = def.reach();
        this.holdsStill = def.holdsStill();
        this.stunCancels = def.stunCancels();
        this.roarStrength = def.roar().shakes() ? def.roar().strength() : 0f;
        this.roarRadius = def.roar().radius();
        this.roarTicks = newRoarTicks;
        return this;
    }

    /** The pattern at this place in the cycle. */
    public PatternDef patternAt(int place) {
        return switch (Math.floorMod(place, BarrageDef.PATTERNS)) {
            case 0 -> ring;
            case 1 -> fan;
            case 2 -> spiral;
            default -> stream;
        };
    }

    /** The pattern it is on. */
    public PatternDef current() {
        return patternAt(pattern);
    }

    /** True once it has had something to shoot at. */
    public boolean aiming() {
        return aimX != 0f || aimY != 0f;
    }

    /** True while it is showing what is coming. */
    public boolean windingUp() {
        return enabled && phase == Phase.WINDING_UP;
    }

    /**
     * How much of its own speed it keeps this tick: none while a barrage that holds still is winding up or
     * firing, all of it otherwise. Read by {@code Pace} alone.
     */
    public float paceFactor() {
        return enabled && holdsStill && (phase == Phase.WINDING_UP || phase == Phase.FIRING) ? 0f : 1f;
    }

    /** How much of the circle of its last salvo is left to draw, in {@code [0, 1]}; zero is none. */
    public float burstFade() {
        return burstTotal <= 0 || burstTicks <= 0 ? 0f : burstTicks / (float) burstTotal;
    }

    /** Which way round the current pattern turns this time through: one way, then the other. */
    public float spin() {
        return (cycles & 1) == 0 ? 1f : -1f;
    }

    @Override
    public void reset() {
        enabled = false;
        windUpTicks = 0;
        restTicks = 0;
        enragedBelow = 0f;
        enragedFactor = 1f;
        ring = PatternDef.off();
        fan = PatternDef.off();
        spiral = PatternDef.off();
        stream = PatternDef.off();
        reach = 0f;
        holdsStill = false;
        stunCancels = false;
        roarStrength = 0f;
        roarRadius = 0f;
        roarTicks = 0;
        phase = Phase.IDLE;
        burstReach = 0f;
        burstTicks = 0;
        burstTotal = 0;
        pattern = 0;
        remainingTicks = 0;
        phaseTicks = 0;
        salvo = 0;
        cycles = 0;
        fallen = false;
        aimX = 0f;
        aimY = 0f;
    }
}
