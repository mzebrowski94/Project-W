package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * The drafts waiting to be picked, their cards, and what has earned them (G0049). Level state on the
 * level entity (G0007). Written only by the upgrade package; {@code ArchitectureTest} fences it.
 */
public final class Drafts implements Component, Pool.Poolable {

    /** One draft, belonging to its chooser's slot until he takes a card or it passes on. */
    public record Draft(int serial, int chooser, long earnedTick, int revision) {}

    /** One card on a draft, at its place in deal order. */
    public record Card(int serial, int position, UpgradeKind kind) {}

    /** A kind a slot passed over on its last pick, which it is not dealt next time if the pool allows. */
    public record Passed(int slot, UpgradeKind kind) {}

    /** A pick handed in this tick, applied at the end of it and then forgotten. */
    public record Pick(int slot, int serial, UpgradeKind kind) {}

    /** Waiting to be picked, oldest first. */
    public final Array<Draft> pending = new Array<>();

    /** Every pending draft's cards. */
    public final Array<Card> cards = new Array<>();

    public final Array<Passed> passed = new Array<>();

    /** Emptied every tick. */
    public final Array<Pick> submitted = new Array<>();

    /** The serial the next draft is given, never reused. Zero names no draft. */
    public int nextSerial = 1;

    /** Who chose last, so the next draft goes to the slot after him; -1 before the first. */
    public int lastChooser = -1;

    /** Bumped whenever a draft is earned, picked, passed on or re-dealt, so whoever shows them rebuilds only then. */
    public int revision;

    /** How many drafts have been earned this level, which is what unlocks a tier. */
    public int earned;

    /** How many control points taken, hold waves cleared and bosses killed have been credited so far. */
    public int fromPoints;

    public int fromWaves;

    public int fromBosses;

    /**
     * How many drafts a source was owed that the pool could not deal - nothing left to learn. The source
     * is credited all the same; this is only so the interface can say so.
     */
    public int dry;

    @Override
    public void reset() {
        pending.clear();
        cards.clear();
        passed.clear();
        submitted.clear();
        revision = 0;
        nextSerial = 1;
        lastChooser = -1;
        earned = 0;
        fromPoints = 0;
        fromWaves = 0;
        fromBosses = 0;
        dry = 0;
    }
}
