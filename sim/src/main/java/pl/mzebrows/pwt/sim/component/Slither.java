package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.def.SlitherDef;
import pl.mzebrows.pwt.sim.def.Ticks;

/**
 * A strain that slithers (G0087): its heading, the bend it is in, and where the parts trailing its head lie.
 *
 * <p>The configuration half is per strain, written with it by {@code applyStrain}; the rest is {@code
 * SlitherSystem}'s. Each part behind the head is a pair of fields rather than an array, because a snapshot carries
 * scalars - which is why a strain is in at most {@link SlitherDef#MOST} parts. Part zero is the head, which is the
 * entity's own position and body; parts one to three are drawn, never hit.
 */
public final class Slither implements Component, Pool.Poolable {

    /** Whether this strain slithers at all. */
    public boolean enabled;

    /** How many parts it is in when whole, the head among them. */
    public int parts;

    /** How many it is in now: one part's worth of health lost takes the last one off, the head last (G0087). */
    public int partsLeft;

    /** How big each part is beside the one in front of it. */
    public float partScale;

    /** How far apart two parts' middles are, as a fraction of their radii added together. */
    public float spacing;

    /** One tick's bend in the open, as the cosine and sine of the angle: worked out once, not every tick. */
    public float turnCos;

    public float turnSin;

    /** One tick's turn where its way is shut, the same way. */
    public float tightCos;

    public float tightSin;

    /** One tick's turn onto the man it picked, the same way. */
    public float huntCos;

    public float huntSin;

    /** Whether nothing slows it - a beam, glue, mud (G0087). Read by {@code Pace}. */
    public boolean unslowed;

    /** Whether it picks a man to go through from time to time. */
    public boolean hunts;

    /** How long it goes between picking a man, drawn between these; how far off he may be; how long it goes for him. */
    public int huntMinTicks;

    public int huntMaxTicks;
    public float huntRange;
    public int huntTicks;

    /** The man it is going for, by handle, or nobody. */
    public long prey = Handle.NOBODY;

    /** Ticks left of going for him before it gives up. */
    public int preyTicks;

    /** Ticks left before it picks a man again; below zero, not yet drawn. */
    public int waitTicks;

    /** How many ticks of turning hard take it a quarter of the way round: one shut look turns it that far. */
    public int tightSpan;

    /** How far ahead it looks for its way being shut, and how far from home it goes, in world units. */
    public float lookAhead;

    public float roam;

    /** How long one bend lasts, in ticks: drawn between these each time it picks another. */
    public int bendMinTicks;

    public int bendMaxTicks;

    /** Where it was put down: home, which it bends back towards from further than {@link #roam}. */
    public float homeX;

    public float homeY;

    /** Which way it is going, of unit length; set from its facing the first tick it moves. */
    public float headingX;

    public float headingY;

    /** The bend it is in: -1 to the right, 0 straight on, 1 to the left. */
    public int bend;

    /** Ticks left of the bend it is in. */
    public int bendTicks;

    /** Ticks left of turning hard after it found its way shut, so one shut look turns it away rather than a degree. */
    public int tightTicks;

    /** Which way it turns while turning hard: 1 to the left, -1 to the right. */
    public int tightSide;

    /**
     * Who its head is passing through, by handle, so a man it is still inside is bitten once a pass rather than again
     * once his grace runs out. Freed by {@code SlitherSystem} once he is clear of the head.
     */
    public long bitten0 = Handle.NOBODY;

    public long bitten1 = Handle.NOBODY;
    public long bitten2 = Handle.NOBODY;
    public long bitten3 = Handle.NOBODY;

    /** Where its head was the last tick it was driven, so a head that did not get where it was going is seen. */
    public float lastX;

    public float lastY;

    /** Where the parts behind its head lie. Parts past {@link #parts} are unused. */
    public float part1X;

    public float part1Y;
    public float part2X;
    public float part2Y;
    public float part3X;
    public float part3Y;

    /** Sets the configuration from the strain's definition, for spawning and evolving alike. */
    public Slither set(SlitherDef def, float tickSeconds) {
        this.enabled = def.enabled();
        this.parts = def.parts();
        this.partsLeft = def.parts();
        this.partScale = def.partScale();
        this.spacing = def.spacing();
        float turn = (float) StrictMath.toRadians(def.turnDegrees()) * tickSeconds;
        float tight = (float) StrictMath.toRadians(def.tightTurnDegrees()) * tickSeconds;
        float hunt = (float) StrictMath.toRadians(def.huntTurnDegrees()) * tickSeconds;
        this.turnCos = (float) StrictMath.cos(turn);
        this.turnSin = (float) StrictMath.sin(turn);
        this.tightCos = (float) StrictMath.cos(tight);
        this.tightSin = (float) StrictMath.sin(tight);
        this.huntCos = (float) StrictMath.cos(hunt);
        this.huntSin = (float) StrictMath.sin(hunt);
        this.tightSpan = tight > 0f ? (int) Math.ceil(StrictMath.PI * 0.5 / tight) : 0;
        this.lookAhead = def.lookAhead();
        this.roam = def.roam();
        this.bendMinTicks = Ticks.of(def.bendMinSeconds(), tickSeconds);
        this.bendMaxTicks = Ticks.of(def.bendMaxSeconds(), tickSeconds);
        this.unslowed = def.unslowed();
        this.hunts = def.enabled() && def.hunts();
        this.huntMinTicks = Ticks.of(def.huntMinSeconds(), tickSeconds);
        this.huntMaxTicks = Ticks.of(def.huntMaxSeconds(), tickSeconds);
        this.huntRange = def.huntRange();
        this.huntTicks = Ticks.of(def.huntSeconds(), tickSeconds);
        this.bend = 0;
        this.bendTicks = 0;
        this.tightTicks = 0;
        this.tightSide = 1;
        this.prey = Handle.NOBODY;
        this.preyTicks = 0;
        // The first wait is drawn on its first tick, so a snake put down never goes for a man at once.
        this.waitTicks = -1;
        this.bitten0 = Handle.NOBODY;
        this.bitten1 = Handle.NOBODY;
        this.bitten2 = Handle.NOBODY;
        this.bitten3 = Handle.NOBODY;
        return this;
    }

    /** Puts its home and every part where it stands. Its heading is taken from its facing the first tick it moves. */
    public Slither home(float x, float y) {
        this.homeX = x;
        this.homeY = y;
        this.headingX = 0f;
        this.headingY = 0f;
        this.lastX = x;
        this.lastY = y;
        this.part1X = x;
        this.part1Y = y;
        this.part2X = x;
        this.part2Y = y;
        this.part3X = x;
        this.part3Y = y;
        return this;
    }

    /**
     * Whether its head may bite this man now - not while it is still passing through him from the last bite - and, if
     * so, that it has. With every slot taken it bites anyway: four men inside one head at once is a crowd, not a pass.
     */
    public boolean bite(long handle) {
        if (!enabled) {
            return true;
        }
        if (bitten0 == handle || bitten1 == handle || bitten2 == handle || bitten3 == handle) {
            return false;
        }
        if (bitten0 == Handle.NOBODY) {
            bitten0 = handle;
        } else if (bitten1 == Handle.NOBODY) {
            bitten1 = handle;
        } else if (bitten2 == Handle.NOBODY) {
            bitten2 = handle;
        } else {
            bitten3 = handle;
        }
        return true;
    }

    /** Who is in bite slot {@code index}, zero to three. */
    public long bitten(int index) {
        return switch (index) {
            case 0 -> bitten0;
            case 1 -> bitten1;
            case 2 -> bitten2;
            default -> bitten3;
        };
    }

    /** Frees bite slot {@code index}: he is clear of the head. */
    public void clear(int index) {
        switch (index) {
            case 0 -> bitten0 = Handle.NOBODY;
            case 1 -> bitten1 = Handle.NOBODY;
            case 2 -> bitten2 = Handle.NOBODY;
            default -> bitten3 = Handle.NOBODY;
        }
    }

    /** Where part {@code index} lies, one to three. */
    public float partX(int index) {
        return switch (index) {
            case 1 -> part1X;
            case 2 -> part2X;
            default -> part3X;
        };
    }

    public float partY(int index) {
        return switch (index) {
            case 1 -> part1Y;
            case 2 -> part2Y;
            default -> part3Y;
        };
    }

    /** Lays part {@code index}, one to three. */
    public void lay(int index, float x, float y) {
        switch (index) {
            case 1 -> {
                part1X = x;
                part1Y = y;
            }
            case 2 -> {
                part2X = x;
                part2Y = y;
            }
            default -> {
                part3X = x;
                part3Y = y;
            }
        }
    }

    /** How big part {@code index} is for a head of this radius: each a {@link #partScale} of the one before it. */
    public float radiusOf(int index, float headRadius) {
        float radius = headRadius;
        for (int i = 0; i < index; i++) {
            radius *= partScale;
        }
        return radius;
    }

    @Override
    public void reset() {
        enabled = false;
        parts = 0;
        partsLeft = 0;
        partScale = 0f;
        spacing = 0f;
        turnCos = 0f;
        turnSin = 0f;
        tightCos = 0f;
        tightSin = 0f;
        tightSpan = 0;
        huntCos = 0f;
        huntSin = 0f;
        unslowed = false;
        hunts = false;
        huntMinTicks = 0;
        huntMaxTicks = 0;
        huntRange = 0f;
        huntTicks = 0;
        prey = Handle.NOBODY;
        preyTicks = 0;
        waitTicks = 0;
        lookAhead = 0f;
        roam = 0f;
        bendMinTicks = 0;
        bendMaxTicks = 0;
        homeX = 0f;
        homeY = 0f;
        headingX = 0f;
        headingY = 0f;
        bend = 0;
        bendTicks = 0;
        tightTicks = 0;
        tightSide = 0;
        lastX = 0f;
        lastY = 0f;
        bitten0 = Handle.NOBODY;
        bitten1 = Handle.NOBODY;
        bitten2 = Handle.NOBODY;
        bitten3 = Handle.NOBODY;
        part1X = 0f;
        part1Y = 0f;
        part2X = 0f;
        part2Y = 0f;
        part3X = 0f;
        part3Y = 0f;
    }
}
