package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * The drafts waiting to be picked, their cards, and what has earned them: every slot his own, on the schedule
 * planned when the level was built (G0049, G0118). Level state on the level entity (G0007). Written only by the
 * upgrade package; {@code ArchitectureTest} fences it.
 */
public final class Drafts implements Component, Pool.Poolable {

    /** One draft, belonging to its chooser's slot until he takes a card - away or not, it never passes on (G0118). */
    public record Draft(int serial, int chooser, long earnedTick, int revision) {}

    /** One card on a draft, at its place in deal order. */
    public record Card(int serial, int position, UpgradeKind kind) {}

    /** A kind a slot passed over on its last pick, which it is not dealt next time if the pool allows. */
    public record Passed(int slot, UpgradeKind kind) {}

    /**
     * How far one seated slot has come (G0118): the ridges his own commander has crossed, and how many of the
     * schedule's thresholds he has been credited with - a draft dealt, or one the pool could not fill.
     */
    public record Progress(int slot, int crossings, int earned) {}

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

    /** Bumped whenever a draft is earned, picked, passed on or re-dealt, so whoever shows them rebuilds only then. */
    public int revision;

    /** Every slot that has been seated, in the order each first was; a slot never seated has none. */
    public final Array<Progress> progress = new Array<>();

    /** Whether the schedule has been planned - on the level's first tick, once everything on it stands there. */
    public boolean scheduled;

    /** The level's weighed sources - every site and every ridge, at their weights - when it was planned. */
    public int sources;

    /** How many drafts each slot is planned, from {@link #sources}. */
    public int planned;

    /**
     * How many drafts a threshold was owed that the pool could not deal - nothing left to learn. The slot is
     * credited all the same; this is only so the interface can say so.
     */
    public int dry;

    @Override
    public void reset() {
        pending.clear();
        cards.clear();
        passed.clear();
        submitted.clear();
        progress.clear();
        revision = 0;
        nextSerial = 1;
        scheduled = false;
        sources = 0;
        planned = 0;
        dry = 0;
    }
}
