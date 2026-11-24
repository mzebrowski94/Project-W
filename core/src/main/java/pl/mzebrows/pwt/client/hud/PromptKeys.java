package pl.mzebrows.pwt.client.hud;

import pl.mzebrows.pwt.client.input.ControlsConfig;
import pl.mzebrows.pwt.client.input.PadLabels;
import pl.mzebrows.pwt.ui.hud.PromptKind;

/**
 * The keys a seat's **prompt** tells it to press, in its own device's words (G0098): the action key, the strike, the
 * draft key and redeploy (G0109). The keyboard's seat - zero, unless player one is on a pad - its mouse buttons named short - {@code LMB}, {@code RMB} - and every
 * other seat a pad, through {@link PadLabels}. Named again only when the seat or its pad's family changes. {@link
 * CrateHints}' pattern, for the prompt.
 */
public final class PromptKeys {

    private final ControlsConfig controls;
    private String action = "";
    private String strike = "";
    private String draft = "";
    private String redeploy = "";
    private int seat = -2;
    private boolean keyboard;
    private boolean playStation;

    public PromptKeys(ControlsConfig controls) {
        this.controls = controls;
    }

    /**
     * Names the keys of this seat - the keyboard's, or a pad's that is a PlayStation pad or not.
     *
     * @param keyboard whether the seat is the keyboard and mouse's, which seat zero is unless player one is on a pad
     */
    public void follow(int seat, boolean keyboard, boolean playStation) {
        if (seat == this.seat && keyboard == this.keyboard && playStation == this.playStation) {
            return;
        }
        this.seat = seat;
        this.keyboard = keyboard;
        this.playStation = playStation;
        if (keyboard) {
            action = controls.key().action().name();
            strike = mouse(controls.mouse().strike().name());
            draft = controls.key().draft().name();
            redeploy = controls.key().redeploy().name();
        } else {
            ControlsConfig.Pad pad = controls.pad();
            action = PadLabels.of(pad.action(), pad.actionTrigger(), playStation);
            strike = PadLabels.of(pad.strike(), pad.strikeTrigger(), playStation);
            draft = PadLabels.of(pad.draft(), pad.draftTrigger(), playStation);
            redeploy = PadLabels.of(pad.redeploy(), pad.redeployTrigger(), playStation);
        }
    }

    /** What this prompt tells him to press; empty for none. */
    public String of(PromptKind kind) {
        return switch (kind) {
            case ACTION -> action;
            case STRIKE -> strike;
            case UPGRADE -> draft;
            case REDEPLOY -> redeploy;
            case NONE -> "";
        };
    }

    /** A mouse button as a prompt names it, from the name {@code controls.properties} gives it. */
    static String mouse(String name) {
        return switch (name) {
            case "LEFT" -> "LMB";
            case "RIGHT" -> "RMB";
            default -> "MMB";
        };
    }
}
