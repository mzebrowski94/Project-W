package pl.mzebrows.pwt.ui.page;

/** Every slot of the HUD, by its {@code UI:} id in the specification. */
public enum HudSlot {
    MISSION("UI:hud/top/mission"),
    GOAL("UI:hud/top/goal"),
    TERMINAL("UI:hud/top/terminal"),
    ASIDE("UI:hud/top/aside"),
    OVERLAY("UI:hud/top/overlay"),
    DRAFT("UI:hud/top/draft"),
    CRATE("UI:hud/top/crate"),
    PLAYER_PANEL("UI:hud/bottom/panel"),
    PORTRAIT("UI:hud/bottom/portrait"),
    STATUS("UI:hud/bottom/status"),
    WEAPON("UI:hud/bottom/weapon"),
    TALLY("UI:hud/bottom/tally"),
    UPGRADE_TELL("UI:hud/bottom/upgrade"),
    ROLL_CALL("UI:hud/bottom/roll-call"),
    PAUSED("UI:hud/view/paused"),
    TOAST("UI:hud/view/toast"),
    PROMPT("UI:hud/view/prompt"),
    RETICLE("UI:hud/view/reticle");

    private final String id;

    HudSlot(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
