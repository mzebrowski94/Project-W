package pl.mzebrows.pwt.ui.page;

import org.jspecify.annotations.Nullable;

/** Every page, modal and state the player can be in, by its {@code UI:} id in the specification. */
public enum UiPage {
    MAIN("UI:main", Kind.PAGE, null),
    CAMPAIGN("UI:main/campaign", Kind.PAGE, MAIN),
    PROVING_GROUND("UI:main/proving-ground", Kind.PAGE, MAIN),
    PROVING_UPGRADES("UI:main/proving-ground/upgrades", Kind.PAGE, PROVING_GROUND),
    ONLINE("UI:main/online", Kind.PAGE, MAIN),
    ONLINE_JOIN("UI:main/online/join", Kind.PAGE, ONLINE),
    ONLINE_HOST("UI:main/online/host", Kind.PAGE, ONLINE),
    OPTIONS("UI:main/options", Kind.PAGE, MAIN),
    EXIT_CONFIRM("UI:main/exit", Kind.MODAL, MAIN),
    CONNECTING("UI:connecting", Kind.PAGE, null),
    PLAY("UI:play", Kind.STATE, null),
    PAUSE("UI:play/pause", Kind.MODAL, PLAY),
    LEAVE_CONFIRM("UI:play/leave", Kind.MODAL, PAUSE),
    /** How a level with an objective ended, opened by the flow after the hold, never by an entry (G0066). */
    RESULT("UI:play/result", Kind.MODAL, PLAY);

    /** How a page is drawn: instead of what is behind it, over it, or not at all. */
    public enum Kind {
        PAGE,
        MODAL,
        STATE
    }

    private final String id;
    private final Kind kind;
    private final @Nullable UiPage parent;

    UiPage(String id, Kind kind, @Nullable UiPage parent) {
        this.id = id;
        this.kind = kind;
        this.parent = parent;
    }

    /** The page's id in {@code docs/ui/menu_layout_agent.md}. */
    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public @Nullable UiPage parent() {
        return parent;
    }
}
