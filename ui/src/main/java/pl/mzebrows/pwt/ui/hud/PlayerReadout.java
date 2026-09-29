package pl.mzebrows.pwt.ui.hud;

import java.util.Arrays;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

/**
 * One player's facts, by slot, as his panel shows them. Plain fields, filled only by {@link
 * ReadoutWriter}; kept by slot rather than by entity, because a pooled entity held from one frame to the
 * next comes back as something else (G0016).
 */
public final class PlayerReadout {

    /** Most cards a draft shows. */
    public static final int CARDS = 3;

    public Posture posture = Posture.ABSENT;

    /** The gun in his hands, or the turret's while he is aboard one. */
    public WeaponKind weapon = WeaponKind.SIDEARM;

    /** How hot that gun is, in {@code [0, 1]}, and whether it has stopped itself. */
    public float heat;

    public boolean overheated;

    /** How much of his bleed-out is left while he is a casualty, in {@code [0, 1]}. */
    public float bleedOut;

    /**
     * The wounds he carries on his feet, and how many he can (G0053). Zero wounds while he is down:
     * his bleed-out says more.
     */
    public int wounds;

    public int maxWounds;

    /** Whether a shield is in front of him, and whether something has stunned him (G0053). */
    public boolean shielded;

    public boolean stunned;

    /** Seconds until he is back, while he is respawning. */
    public float respawnSeconds;

    /** Which of the commander's colours he wears. */
    public int variant;

    /** What the action key would do for him right now. */
    public ActionKind action = ActionKind.NONE;

    /**
     * His tally for this life (G0044) - kills, goals, men stabilised; the life that just ended while he
     * waits to come back.
     */
    public int kills;

    public int goals;
    public int stabilised;

    /**
     * Whether his link has dropped and nobody has taken his slot since (G0052): his commander stands idle,
     * and he does not count as in the game for the solo pause (D2).
     */
    public boolean away;

    /** How many drafts his slot holds (G0049). */
    public int drafts;

    /** His oldest draft - the one his draft key opens - by serial; zero while he holds none. */
    public int draftSerial;

    /** Its cards, in deal order, and the rank each will have once he takes it. */
    public int cardCount;

    public final UpgradeKind[] cards = new UpgradeKind[CARDS];
    public final int[] cardRanks = new int[CARDS];

    public PlayerReadout() {
        Arrays.fill(cards, UpgradeKind.LARGER_ARMY);
    }

    void clear() {
        posture = Posture.ABSENT;
        weapon = WeaponKind.SIDEARM;
        heat = 0f;
        overheated = false;
        bleedOut = 0f;
        wounds = 0;
        maxWounds = 0;
        shielded = false;
        stunned = false;
        respawnSeconds = 0f;
        variant = 0;
        action = ActionKind.NONE;
        kills = 0;
        goals = 0;
        stabilised = 0;
        away = false;
        drafts = 0;
        draftSerial = 0;
        cardCount = 0;
    }

    void copyFrom(PlayerReadout other) {
        posture = other.posture;
        weapon = other.weapon;
        heat = other.heat;
        overheated = other.overheated;
        bleedOut = other.bleedOut;
        wounds = other.wounds;
        maxWounds = other.maxWounds;
        shielded = other.shielded;
        stunned = other.stunned;
        respawnSeconds = other.respawnSeconds;
        variant = other.variant;
        action = other.action;
        kills = other.kills;
        goals = other.goals;
        stabilised = other.stabilised;
        drafts = other.drafts;
        draftSerial = other.draftSerial;
        cardCount = other.cardCount;
        System.arraycopy(other.cards, 0, cards, 0, CARDS);
        System.arraycopy(other.cardRanks, 0, cardRanks, 0, CARDS);
    }
}
