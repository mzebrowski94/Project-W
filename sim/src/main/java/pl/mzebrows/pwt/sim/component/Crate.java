package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * A crate (G0056): what it holds, once it has been dealt, and who has it open.
 *
 * <p><strong>Dealt on its first opening, never before</strong>: until somebody opens it a crate holds
 * nothing at all, so a level lays out no contents, a snapshot carries none, and an upgrade taken on the
 * way there counts. Once dealt it keeps what it holds; what is taken is gone from it.
 *
 * <p><strong>One commander at a time.</strong> {@link #openedBy} is the slot that has it open, or {@link
 * #NOBODY}. Every machine steps the same presses in the same order, so every machine agrees who got there
 * first without anybody being asked (pillar 7) - two presses on one tick go to the lower slot.
 *
 * <p>Up to {@link SimConfig.CrateConfig#CAPACITY} pickups, held in four fields rather than an array because
 * a snapshot writes a component field by field; {@link #count} says how many of them mean anything.
 */
public final class Crate implements Component, Pool.Poolable {

    /** Nobody has it open. */
    public static final int NOBODY = -1;

    /** Whether it has been dealt its contents. */
    public boolean dealt;

    /** How many pickups it holds now; meaningless until it is dealt. */
    public int count;

    public PickupKind first = PickupKind.MEDKIT;
    public PickupKind second = PickupKind.MEDKIT;
    public PickupKind third = PickupKind.MEDKIT;
    public PickupKind fourth = PickupKind.MEDKIT;

    /** The slot that has it open, or {@link #NOBODY}. */
    public int openedBy = NOBODY;

    /** The tick it was opened on, so a wound on that same tick does not shut it again. */
    public long openedOn;

    /** The opener's wounds as of the last tick, so a new one closes it. */
    public int openerWounds;

    /**
     * Whether a commander holding Looter has opened it (G0110): it gave its one more card then, and never gives
     * another, whoever's Looter opens it next.
     */
    public boolean looted;

    /** Whether somebody has it open. */
    public boolean open() {
        return openedBy != NOBODY;
    }

    /** The pickup at this place, of the {@link #count} it holds. */
    public PickupKind get(int index) {
        return switch (index) {
            case 0 -> first;
            case 1 -> second;
            case 2 -> third;
            case 3 -> fourth;
            default -> throw new IndexOutOfBoundsException("a crate holds at most four, not " + (index + 1));
        };
    }

    /** Puts a pickup at this place. */
    public void put(int index, PickupKind kind) {
        switch (index) {
            case 0 -> first = kind;
            case 1 -> second = kind;
            case 2 -> third = kind;
            case 3 -> fourth = kind;
            default -> throw new IndexOutOfBoundsException("a crate holds at most four, not " + (index + 1));
        }
    }

    /** The place of the first pickup of this kind it holds, or -1. */
    public int indexOf(PickupKind kind) {
        for (int i = 0; i < count; i++) {
            if (get(i) == kind) {
                return i;
            }
        }
        return -1;
    }

    /** Takes the pickup at this place out, closing the gap so the rest keep their order. */
    public void remove(int index) {
        for (int i = index; i < count - 1; i++) {
            put(i, get(i + 1));
        }
        count--;
    }

    /** Nobody has it open any more. */
    public void close() {
        openedBy = NOBODY;
        openedOn = 0L;
        openerWounds = 0;
    }

    @Override
    public void reset() {
        dealt = false;
        count = 0;
        first = PickupKind.MEDKIT;
        second = PickupKind.MEDKIT;
        third = PickupKind.MEDKIT;
        fourth = PickupKind.MEDKIT;
        looted = false;
        close();
    }
}
