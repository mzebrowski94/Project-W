package pl.mzebrows.pwt.client.input;

import java.util.Locale;

/**
 * What to call a button when telling the player to press it.
 *
 * <p>The bindings are by position ({@link GamepadButton}); this is the only place that turns a
 * position into the letter or shape printed on the pad in front of the player. One binding table,
 * two label sets - rather than two binding tables, which is how a game ends up with the PlayStation
 * map a patch behind the Xbox one.
 */
public final class PadLabels {

    private PadLabels() {}

    /** Which family of labels a pad calling itself this wants. */
    public static boolean isPlayStation(String padName) {
        String lower = padName.toLowerCase(Locale.ROOT);
        return lower.contains("playstation")
                || lower.contains("dualshock")
                || lower.contains("dualsense")
                || lower.contains("sony")
                || lower.contains("ps3")
                || lower.contains("ps4")
                || lower.contains("ps5");
    }

    /** What this button is called on that pad. Xbox labels are the fallback for anything unrecognised. */
    public static String of(GamepadButton button, boolean playStation) {
        return switch (button) {
            case NONE -> "-";
            case SOUTH -> playStation ? "Cross" : "A";
            case EAST -> playStation ? "Circle" : "B";
            case WEST -> playStation ? "Square" : "X";
            case NORTH -> playStation ? "Triangle" : "Y";
            case L1 -> playStation ? "L1" : "LB";
            case R1 -> playStation ? "R1" : "RB";
            case L3 -> playStation ? "L3" : "LS";
            case R3 -> playStation ? "R3" : "RS";
            case START -> playStation ? "Options" : "Start";
            case SELECT -> playStation ? "Share" : "Back";
            case DPAD_UP -> "D-pad up";
            case DPAD_DOWN -> "D-pad down";
            case DPAD_LEFT -> "D-pad left";
            case DPAD_RIGHT -> "D-pad right";
            case R4 -> "R4";
            case L4 -> "L4";
            case R5 -> "R5";
            case L5 -> "L5";
        };
    }

    /**
     * One line telling a player what his pad does, in his pad's own labels.
     *
     * <p>Alpha-phase scaffolding: a player who has just plugged a pad in has nowhere on screen to read
     * its controls, and the console is at least somewhere. It is also what keeps the two label sets honest - a binding added without a label
     * shows up here as a dash rather than staying invisible until somebody builds a HUD.
     */
    public static String briefing(ControlsConfig.Pad pad, boolean playStation) {
        return "move " + of(pad.moveX(), playStation)
                + ", aim " + of(pad.aimX(), playStation)
                + ", fire " + of(pad.fire(), pad.fireTrigger(), playStation)
                + ", strike " + of(pad.strike(), pad.strikeTrigger(), playStation)
                + ", grenade " + of(pad.grenade(), pad.grenadeTrigger(), playStation)
                + ", act " + of(pad.action(), pad.actionTrigger(), playStation)
                + ", dash " + dash(pad, playStation)
                + ", zoom " + of(pad.zoom(), pad.zoomTrigger(), playStation)
                + ", pause " + of(pad.pause(), pad.pauseTrigger(), playStation)
                + ", upgrades " + of(pad.draft(), pad.draftTrigger(), playStation)
                + ", orders on the d-pad";
    }

    /** What the dash is called on that pad: its button and trigger, then its alias, a space between. */
    public static String dash(ControlsConfig.Pad pad, boolean playStation) {
        String dash = of(pad.dash(), pad.dashTrigger(), playStation);
        return pad.dashAlias() == GamepadButton.NONE ? dash : dash + " " + of(pad.dashAlias(), playStation);
    }

    /** What a whole binding is called - a button, a trigger, or both, in that pad's labels. */
    public static String of(GamepadButton button, GamepadAxis trigger, boolean playStation) {
        if (button == GamepadButton.NONE) {
            return of(trigger, playStation);
        }
        if (trigger == GamepadAxis.NONE) {
            return of(button, playStation);
        }
        return of(button, playStation) + "/" + of(trigger, playStation);
    }

    /** What this analogue control is called on that pad. */
    public static String of(GamepadAxis axis, boolean playStation) {
        return switch (axis) {
            case NONE -> "-";
            case LEFT_X, LEFT_Y -> "left stick";
            case RIGHT_X, RIGHT_Y -> "right stick";
            case LEFT_TRIGGER -> playStation ? "L2" : "LT";
            case RIGHT_TRIGGER -> playStation ? "R2" : "RT";
        };
    }
}
