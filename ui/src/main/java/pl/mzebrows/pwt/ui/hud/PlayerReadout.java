package pl.mzebrows.pwt.ui.hud;

import java.util.Arrays;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * One player's facts, by slot, as his panel shows them. Plain fields, filled only by {@link
 * ReadoutWriter}; kept by slot rather than by entity, because a pooled entity held from one frame to the
 * next comes back as something else (G0016).
 */
public final class PlayerReadout {

    /** Most cards a draft shows. */
    public static final int CARDS = 3;

    /** Most cards a crate shows (G0056): everything it can hold. */
    public static final int CRATE_CARDS = SimConfig.CrateConfig.CAPACITY;

    public Posture posture = Posture.ABSENT;

    /** The gun in his hands, or the turret's while he is aboard one. */
    public WeaponKind weapon = WeaponKind.SIDEARM;

    /** How hot that gun is, in {@code [0, 1]}, and whether it has stopped itself. */
    public float heat;

    public boolean overheated;

    /** Whether he has a limpet out, so his next press sets it off (G0100): DETONATE where OVERHEAT would be. */
    public boolean detonating;

    /** How much of his bleed-out is left while he is a casualty, in {@code [0, 1]}. */
    public float bleedOut;

    /** Whether he is down and enough of his bleed-out has run for him to redeploy (G0109): his own prompt's. */
    public boolean redeployOpen;

    /**
     * The wounds he carries on his feet, and how many he can (G0053). Zero wounds while he is down:
     * his bleed-out says more.
     */
    public int wounds;

    public int maxWounds;

    /** The grenades he carries, and the most he can (G0077) - one pip each, filled for those he has. */
    public int grenades;

    public int grenadeMost;

    /** Whether his grenade key sets a sentry down instead (G0129): the slot shows sentries, the count the same. */
    public boolean sentries;

    /** The dashes he holds, and the most he can (G0107) - one pip each, filled for those he has. */
    public int dashes;

    public int dashMost;

    /** Whether a shield is in front of him (G0053). */
    public boolean shielded;

    /** Whether nothing can hurt him for now - the protection a respawn or a revive gives him. */
    public boolean invulnerable;

    /** Whether a stun a source declared holds him (G0053) - never a stagger - and how much of it is left, in {@code [0, 1]}. */
    public boolean stunned;

    public float stunLeft;

    /** Seconds until he is back, while he is respawning. */
    public float respawnSeconds;

    /** Waiting on the reserve to come back: nobody in it, so his countdown has not started (G0055). */
    public boolean respawnWaiting;

    /** Paid for by recalling a trooper (G0121) rather than from the reserve: says {@code RECALL} while he waits. */
    public boolean respawnRecalled;

    /** Which of the commander's colours he wears. */
    public int variant;

    /** What the action key would do for him right now. */
    public ActionKind action = ActionKind.NONE;

    /**
     * The handle of what the action key would act on - the simulation's choice, never the interface's (G0098) -
     * and {@link Handles#NOBODY} while nothing is on offer.
     */
    public long subject = Handles.NOBODY;

    /** How far through a hold he is, in {@code [0, 1]}: a man being put back on his feet. */
    public float progress;

    /** His commander's own handle, for what is drawn over him - a strike or an upgrade prompt. */
    public long self = Handles.NOBODY;

    /** Whether something holds him still in its jaws (G0045) - a pin, never a leech's cling (G0083). */
    public boolean pinned;

    /**
     * His tally for this life (G0044) - kills, goals, men stabilised; the life that just ended while he
     * waits to come back.
     */
    public int kills;

    public int goals;
    public int stabilised;

    /**
     * Whether his link has dropped and nobody has taken his slot since (G0052): his commander stands idle,
     * and he does not count as in the game for the solo pause (G0049).
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

    /**
     * The crate he has open (G0056): how many it holds - zero while he has none open - what, in order, and
     * which of them he would be turned down for, a medkit to a man with no wound (G0053).
     */
    public int crateCount;

    public final PickupKind[] crate = new PickupKind[CRATE_CARDS];
    public final boolean[] crateRefused = new boolean[CRATE_CARDS];

    public PlayerReadout() {
        Arrays.fill(cards, UpgradeKind.LARGER_ARMY);
        Arrays.fill(crate, PickupKind.MEDKIT);
    }

    void clear() {
        posture = Posture.ABSENT;
        weapon = WeaponKind.SIDEARM;
        heat = 0f;
        overheated = false;
        detonating = false;
        bleedOut = 0f;
        redeployOpen = false;
        wounds = 0;
        maxWounds = 0;
        grenades = 0;
        grenadeMost = 0;
        sentries = false;
        dashes = 0;
        dashMost = 0;
        shielded = false;
        invulnerable = false;
        stunned = false;
        stunLeft = 0f;
        respawnSeconds = 0f;
        respawnWaiting = false;
        respawnRecalled = false;
        variant = 0;
        action = ActionKind.NONE;
        subject = Handles.NOBODY;
        progress = 0f;
        self = Handles.NOBODY;
        pinned = false;
        kills = 0;
        goals = 0;
        stabilised = 0;
        away = false;
        drafts = 0;
        draftSerial = 0;
        cardCount = 0;
        crateCount = 0;
    }

    void copyFrom(PlayerReadout other) {
        posture = other.posture;
        weapon = other.weapon;
        heat = other.heat;
        overheated = other.overheated;
        detonating = other.detonating;
        bleedOut = other.bleedOut;
        redeployOpen = other.redeployOpen;
        wounds = other.wounds;
        maxWounds = other.maxWounds;
        grenades = other.grenades;
        grenadeMost = other.grenadeMost;
        sentries = other.sentries;
        dashes = other.dashes;
        dashMost = other.dashMost;
        shielded = other.shielded;
        invulnerable = other.invulnerable;
        stunned = other.stunned;
        stunLeft = other.stunLeft;
        respawnSeconds = other.respawnSeconds;
        respawnWaiting = other.respawnWaiting;
        respawnRecalled = other.respawnRecalled;
        variant = other.variant;
        action = other.action;
        subject = other.subject;
        progress = other.progress;
        self = other.self;
        pinned = other.pinned;
        kills = other.kills;
        goals = other.goals;
        stabilised = other.stabilised;
        drafts = other.drafts;
        draftSerial = other.draftSerial;
        cardCount = other.cardCount;
        System.arraycopy(other.cards, 0, cards, 0, CARDS);
        System.arraycopy(other.cardRanks, 0, cardRanks, 0, CARDS);
        crateCount = other.crateCount;
        System.arraycopy(other.crate, 0, crate, 0, CRATE_CARDS);
        System.arraycopy(other.crateRefused, 0, crateRefused, 0, CRATE_CARDS);
    }
}
