package pl.mzebrows.pwt.ui.hud;

import java.util.Arrays;
import java.util.function.IntPredicate;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.def.ActionKind;

/**
 * The one **prompt** each commander played on this machine is shown, chosen once a frame from the {@link
 * Readout} (G0098): the strike while he is pinned - an ant's pin, never a leech's cling - else what the action
 * key would do, else his upgrade for a few seconds after he earns one. What it says, whether it is a hold and how
 * far through, and what it is drawn over - by handle, the simulation's own choice of subject; nothing here looks
 * for one.
 *
 * <p>Prompts over the same thing are stacked, lowest slot nearest it. Nothing here allocates.
 */
public final class Prompts {

    private static final int SLOTS = Players.MAX;

    private final float upgradeSeconds;

    private final PromptKind[] kinds = new PromptKind[SLOTS];
    private final ActionKind[] actions = new ActionKind[SLOTS];
    private final long[] anchors = new long[SLOTS];
    private final float[] fills = new float[SLOTS];
    private final int[] stacks = new int[SLOTS];
    private final int[] drafts = new int[SLOTS];
    private final float[] upgradeLeft = new float[SLOTS];
    private long worldSerial = -1;
    private boolean framed;

    /** @param upgradeSeconds how long a draft just earned is prompted for; zero never prompts it */
    public Prompts(float upgradeSeconds) {
        this.upgradeSeconds = upgradeSeconds;
        Arrays.fill(kinds, PromptKind.NONE);
        Arrays.fill(actions, ActionKind.NONE);
    }

    /** Chooses every local commander's prompt from the frame's readout; {@code local} says whose are played here. */
    public void update(Readout readout, IntPredicate local, float delta) {
        boolean fresh = readout.worldSerial != worldSerial;
        worldSerial = readout.worldSerial;
        int players = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            PlayerReadout player = readout.player(slot);
            if (player.posture != Posture.ABSENT) {
                players++;
            }
            earn(slot, player.drafts, fresh, delta);
            choose(slot, player, local.test(slot));
        }
        framed = players > 1;
        stack();
    }

    /**
     * Whether this man may act on what is round him, and so whether what is within the notice distance is
     * bracketed for him: on his feet, held by nothing, stunned by nothing, with no crate open.
     */
    public static boolean noticing(PlayerReadout player) {
        return player.posture == Posture.STANDING && !player.pinned && !player.stunned && player.crateCount == 0;
    }

    public PromptKind kind(int slot) {
        return kinds[slot];
    }

    /** What the action key would do, while {@link #kind} is {@link PromptKind#ACTION}. */
    public ActionKind action(int slot) {
        return actions[slot];
    }

    /** The handle of what the prompt is drawn over: the subject, or the commander himself. */
    public long anchor(int slot) {
        return anchors[slot];
    }

    /** The i18n key of what the prompt says the key does - a constant, so a frame never builds a string. */
    public String verb(int slot) {
        return switch (kinds[slot]) {
            case STRIKE -> "ui.action.STRIKE";
            case UPGRADE -> "ui.action.UPGRADE";
            case ACTION, NONE ->
                switch (actions[slot]) {
                    case NONE -> "ui.action.NONE";
                    case STABILISE -> "ui.action.STABILISE";
                    case MAN_TURRET -> "ui.action.MAN_TURRET";
                    case LEAVE_TURRET -> "ui.action.LEAVE_TURRET";
                    case OPEN_CRATE -> "ui.action.OPEN_CRATE";
                    case OPEN_DOOR -> "ui.action.OPEN_DOOR";
                };
        };
    }

    /** Whether the key must be held rather than pressed - putting a man on his feet, opening a door. */
    public boolean hold(int slot) {
        return kinds[slot] == PromptKind.ACTION
                && (actions[slot] == ActionKind.STABILISE || actions[slot] == ActionKind.OPEN_DOOR);
    }

    /** How far through the hold he is, from the readout he was chosen from. */
    public float fill(int slot) {
        return fills[slot];
    }

    /** The prompt to get off the gun he is on: always there while he is aboard, so drawn smaller and dim. */
    public boolean minor(int slot) {
        return kinds[slot] == PromptKind.ACTION && actions[slot] == ActionKind.LEAVE_TURRET;
    }

    /** Where in the stack over its anchor this slot's prompt goes: zero nearest it. */
    public int stack(int slot) {
        return stacks[slot];
    }

    /** Whether more than one player is in the game, so each prompt is framed in its player's colour. */
    public boolean framed() {
        return framed;
    }

    private void earn(int slot, int now, boolean fresh, float delta) {
        if (fresh) {
            upgradeLeft[slot] = 0f;
        } else if (now > drafts[slot]) {
            upgradeLeft[slot] = upgradeSeconds;
        } else {
            upgradeLeft[slot] = now == 0 ? 0f : Math.max(0f, upgradeLeft[slot] - delta);
        }
        drafts[slot] = now;
    }

    private void choose(int slot, PlayerReadout player, boolean local) {
        kinds[slot] = PromptKind.NONE;
        actions[slot] = ActionKind.NONE;
        anchors[slot] = Handles.NOBODY;
        fills[slot] = 0f;
        boolean up = player.posture == Posture.STANDING || player.posture == Posture.ABOARD;
        if (!local || !up) {
            return;
        }
        if (player.pinned) {
            set(slot, PromptKind.STRIKE, player.self);
        } else if (player.action != ActionKind.NONE && player.subject != Handles.NOBODY) {
            set(slot, PromptKind.ACTION, player.subject);
            actions[slot] = player.action;
            fills[slot] = player.progress;
        } else if (upgradeLeft[slot] > 0f) {
            set(slot, PromptKind.UPGRADE, player.self);
        }
    }

    private void set(int slot, PromptKind kind, long anchor) {
        kinds[slot] = kind;
        anchors[slot] = anchor;
    }

    /** Lowest slot nearest the anchor: each counts the lower slots prompting over the same thing. */
    private void stack() {
        for (int slot = 0; slot < SLOTS; slot++) {
            int below = 0;
            if (kinds[slot] != PromptKind.NONE) {
                for (int lower = 0; lower < slot; lower++) {
                    if (kinds[lower] != PromptKind.NONE && anchors[lower] == anchors[slot]) {
                        below++;
                    }
                }
            }
            stacks[slot] = below;
        }
    }
}
