package pl.mzebrows.pwt.ui.hud;

/**
 * The one word a player panel's first row says after his label ({@code UI:hud/bottom/status}) - nothing while
 * he stands. Worst news first, the order the simulation's own appearance states keep: what has taken him out,
 * then what has him down, then what holds him until somebody acts, then what passes by itself.
 *
 * <p>{@link #alert()} is the one answer to whether a status is red: the panel draws its word in alert by it, and
 * {@link PanelAlarms} raises the alarm on entering one by it.
 */
public enum PanelStatus {
    /** On his feet and free: the row shows his wounds. */
    NONE(false),
    /** Dying or waiting to come back, with the seconds to his return - or that nobody is in reserve (G0055). */
    REDEPLOY(true),
    /** Shot down and bleeding out, with his bleed-out as a draining bar. */
    DOWN(true),
    /** An ant's jaws hold him still (G0045) - never a leech's cling (G0083). Somebody must strike it off. */
    PINNED(true),
    /** Under a stun a source declared (G0053) - never a stagger - with what is left of it as a draining bar. */
    STUNNED(false),
    /** On a turret. */
    ABOARD(false);

    private final boolean alert;

    PanelStatus(boolean alert) {
        this.alert = alert;
    }

    /** Whether it is drawn in alert, black on red, and entering it raises the panel's alarm. */
    public boolean alert() {
        return alert;
    }

    /** What his panel's status says now. */
    public static PanelStatus of(PlayerReadout player) {
        return switch (player.posture) {
            case ABSENT -> NONE;
            case DYING, RESPAWNING -> REDEPLOY;
            case CASUALTY -> DOWN;
            case STANDING, ABOARD -> {
                if (player.pinned) {
                    yield PINNED;
                }
                if (player.stunned) {
                    yield STUNNED;
                }
                yield player.posture == Posture.ABOARD ? ABOARD : NONE;
            }
        };
    }
}
