package pl.mzebrows.pwt.ui.hud;

import java.util.Arrays;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

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

    /**
     * The army (G0055): troopers on the field - down or not, the count the field's most is held against
     * - that most, and the men in reserve.
     */
    public int squadOnField;

    public int squadMost;
    public int reserve;

    /** The level is lost - every commander out, nobody left in reserve (G0055). Latched in the world. */
    public boolean failed;

    /** The level is won - every objective met on one tick (G0066). Latched in the world, never with failed. */
    public boolean won;

    /** The level's time: the ticks to its outcome, the same on every machine (G0066). Meaningless before. */
    public long outcomeTick;

    /** The length of one of the world's ticks: what turns {@link #outcomeTick} into a time to read. */
    public float tickSeconds;

    /** How long the world has run since the level was won or lost; meaningless before. */
    public float sinceOutcomeSeconds;

    /**
     * The lair waiting on players (B.1): how many commanders on their feet stand on its ground, and how many
     * it is waiting for. Both zero while nothing waits.
     */
    public int regroupPresent;

    public int regroupNeeded;

    /** Entries the ledger can hold at most: every kind, once for the side and once per slot. */
    public static final int HELD = UpgradeKind.values().length * (Players.MAX + 1);

    /**
     * What each side holds (the ledger), in the order it was first taken: the kind, whose it is - a slot,
     * or -1 for the side's - its ranks, the most it can have, and whether it is the swarm's adaptation
     * rather than the players' upgrade.
     */
    public int heldCount;

    public final UpgradeKind[] heldKind = new UpgradeKind[HELD];
    public final int[] heldSlot = new int[HELD];
    public final int[] heldRanks = new int[HELD];
    public final int[] heldMaxRanks = new int[HELD];
    public final boolean[] heldAlien = new boolean[HELD];

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

    /**
     * Whether the result screen should be open: a level with an objective, won or lost, and the world has
     * run on for {@code holdSeconds} since (G0066).
     */
    public boolean resultDue(float holdSeconds) {
        return objectivesRequired != 0 && (won || failed) && sinceOutcomeSeconds >= holdSeconds;
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
        squadOnField = other.squadOnField;
        squadMost = other.squadMost;
        reserve = other.reserve;
        failed = other.failed;
        won = other.won;
        outcomeTick = other.outcomeTick;
        tickSeconds = other.tickSeconds;
        sinceOutcomeSeconds = other.sinceOutcomeSeconds;
        regroupPresent = other.regroupPresent;
        regroupNeeded = other.regroupNeeded;
        heldCount = other.heldCount;
        System.arraycopy(other.heldKind, 0, heldKind, 0, other.heldCount);
        System.arraycopy(other.heldSlot, 0, heldSlot, 0, other.heldCount);
        System.arraycopy(other.heldRanks, 0, heldRanks, 0, other.heldCount);
        System.arraycopy(other.heldMaxRanks, 0, heldMaxRanks, 0, other.heldCount);
        System.arraycopy(other.heldAlien, 0, heldAlien, 0, other.heldCount);
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
        squadOnField = 0;
        squadMost = 0;
        reserve = 0;
        failed = false;
        won = false;
        outcomeTick = 0L;
        tickSeconds = 0f;
        sinceOutcomeSeconds = 0f;
        regroupPresent = 0;
        regroupNeeded = 0;
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
