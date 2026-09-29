package pl.mzebrows.pwt.ui.hud;

import java.util.Arrays;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * Every fact the HUD shows, copied out of the simulation once a frame by {@link ReadoutWriter} - the one
 * class that reads {@code sim} for the interface. Nothing on screen is a number this does not carry
 * (pillar 5), and nothing here is decided: it is what the world said.
 *
 * <p>Allocated once. Filling it again overwrites every field, so a frame can never show half of the last
 * world.
 */
public final class Readout {

    /** Player slots, empty or not. */
    public static final int SLOTS = Players.MAX;

    /** Changes whenever the world changes - a restart, a heal, the first world online. */
    public long worldSerial;

    public String mission = "";
    public String planet = "";

    /** Which objectives the level requires and which are met right now, one bit per {@link Objective}. */
    public int objectivesRequired;

    public int objectivesMet;

    public int pointsTaken;
    public int pointsTotal;
    public int wavesSent;
    public int wavesTotal;
    public boolean wavesArmed;
    public int nestsStanding;

    /** Troopers on the field who are not dying. A man shot down is still standing in this count. */
    public int troopersStanding;

    /** Entries the ledger can hold at most: every kind, once for the side and once per slot. */
    public static final int HELD = UpgradeKind.values().length * (Players.MAX + 1);

    /**
     * What the side holds (the ledger), in the order it was first taken: the kind, whose it is - a slot,
     * or -1 for the side's - its ranks, and the most it can have.
     */
    public int heldCount;

    public final UpgradeKind[] heldKind = new UpgradeKind[HELD];
    public final int[] heldSlot = new int[HELD];
    public final int[] heldRanks = new int[HELD];
    public final int[] heldMaxRanks = new int[HELD];

    /** Moves whenever a draft or the ledger changes, so text built from them is rebuilt only then. */
    public long upgradeRevision;

    /** How many drafts a source was owed that nothing was left to deal. */
    public int draftsDry;

    private final PlayerReadout[] players = new PlayerReadout[Players.MAX];

    public Readout() {
        for (int slot = 0; slot < players.length; slot++) {
            players[slot] = new PlayerReadout();
        }
        Arrays.fill(heldKind, UpgradeKind.LARGER_ARMY);
    }

    /** The facts about one slot's player. Every slot has one; an empty seat reads {@link Posture#ABSENT}. */
    public PlayerReadout player(int slot) {
        return players[slot];
    }

    public boolean requires(Objective objective) {
        return (objectivesRequired & bit(objective)) != 0;
    }

    public boolean met(Objective objective) {
        return (objectivesMet & bit(objective)) != 0;
    }

    /** Every objective the level named is met. A level that names none is never complete. */
    public boolean complete() {
        return objectivesRequired != 0 && (objectivesMet & objectivesRequired) == objectivesRequired;
    }

    /** Makes this an exact copy of another, for a reporter keeping the last frame. Allocates nothing. */
    public void copyFrom(Readout other) {
        worldSerial = other.worldSerial;
        mission = other.mission;
        planet = other.planet;
        objectivesRequired = other.objectivesRequired;
        objectivesMet = other.objectivesMet;
        pointsTaken = other.pointsTaken;
        pointsTotal = other.pointsTotal;
        wavesSent = other.wavesSent;
        wavesTotal = other.wavesTotal;
        wavesArmed = other.wavesArmed;
        nestsStanding = other.nestsStanding;
        troopersStanding = other.troopersStanding;
        heldCount = other.heldCount;
        System.arraycopy(other.heldKind, 0, heldKind, 0, other.heldCount);
        System.arraycopy(other.heldSlot, 0, heldSlot, 0, other.heldCount);
        System.arraycopy(other.heldRanks, 0, heldRanks, 0, other.heldCount);
        System.arraycopy(other.heldMaxRanks, 0, heldMaxRanks, 0, other.heldCount);
        upgradeRevision = other.upgradeRevision;
        draftsDry = other.draftsDry;
        for (int slot = 0; slot < players.length; slot++) {
            players[slot].copyFrom(other.players[slot]);
        }
    }

    void clear() {
        objectivesRequired = 0;
        objectivesMet = 0;
        pointsTaken = 0;
        pointsTotal = 0;
        wavesSent = 0;
        wavesTotal = 0;
        wavesArmed = false;
        nestsStanding = 0;
        troopersStanding = 0;
        heldCount = 0;
        upgradeRevision = 0L;
        draftsDry = 0;
        for (PlayerReadout player : players) {
            player.clear();
        }
    }

    @SuppressWarnings("EnumOrdinal")
    static int bit(Objective objective) {
        return 1 << objective.ordinal();
    }
}
