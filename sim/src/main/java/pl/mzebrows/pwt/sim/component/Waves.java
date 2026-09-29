package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.map.TileRect;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.Wave;

/**
 * What is still to arrive, and how long until the next of it.
 *
 * <p>Carried by the level entity, because when the other side arrives is a fact about the level
 * rather than about anything standing in it (G0007). A level with an empty list is every level built
 * so far: everything is on the field from the first tick.
 *
 * <p><strong>Whether the list is spent is half of an objective.</strong> "Hold the post" cannot be a
 * question about the world alone - an empty field means you have won if there is nothing left to
 * come and means you are between waves if there is - so the schedule is level state exactly as the
 * objectives themselves are, and {@code ObjectiveSystem} reads both.
 */
public final class Waves implements Component, Pool.Poolable {

    /** Still to come, in order. Filled by a scenario before the first tick and only read afterwards. */
    private final Array<Wave> pending = new Array<>();

    /** Ticks waited since the last one was sent, counting up to the next one's delay. */
    public int waited;

    /**
     * A bit per wave sent, set once nothing it sent is still on its feet: bit {@code w} for the wave
     * sent {@code w}-th, counting from zero. Written by {@code WaveSystem}; read by the upgrade package,
     * which earns drafts from cleared waves (G0049).
     */
    public long cleared;

    /** The most waves a level may hold, one bit of {@link #cleared} each. */
    public static final int MOST = 63;

    /** How far down the list we are. */
    private int sent;

    /**
     * Whether the schedule has started at all.
     *
     * <p>True unless a level holds them back until the player does something; until then the list is
     * neither running nor spent.
     */
    private boolean armed = true;

    /** Adds one to the end of the list, for a scenario describing what it is going to throw at you. */
    public Waves add(float afterSeconds, UnitKind kind, int count, TileRect where) {
        return add(new Wave(afterSeconds, kind, count, where));
    }

    /** The same, from a wave a caller already has in hand. */
    public Waves add(Wave wave) {
        if (pending.size >= MOST) {
            throw new IllegalStateException("a level holds at most " + MOST + " waves");
        }
        pending.add(wave);
        return this;
    }

    /**
     * Holds the whole schedule back until something arms it.
     *
     * <p>Called by a scenario while it is describing what it is going to throw at you, so a level
     * that waits reads as one line beside the waves rather than as a flag somebody has to find.
     */
    public Waves holdBack() {
        armed = false;
        return this;
    }

    /** Starts the schedule. Does nothing to one that is already running. */
    public void arm() {
        armed = true;
    }

    /** Whether the clock is running at all. */
    public boolean armed() {
        return armed;
    }

    /** How many have been sent in so far. Published for the interface: pillar 5 covers the clock too. */
    public int sent() {
        return sent;
    }

    /** How many there are altogether. Zero on a level where everything is on the field from the start. */
    public int total() {
        return pending.size;
    }

    /** True once every wave has been sent in. Not "and beaten" - that is the objective's question. */
    public boolean spent() {
        return sent >= pending.size;
    }

    /** The one that is due, or null if the list is spent. */
    public Wave next() {
        return pending.get(sent);
    }

    /** Marks the due one as sent and starts the clock on the one after it. */
    public void advance() {
        sent++;
        waited = 0;
    }

    @Override
    public void reset() {
        pending.clear();
        waited = 0;
        sent = 0;
        armed = true;
        cleared = 0L;
    }
}
