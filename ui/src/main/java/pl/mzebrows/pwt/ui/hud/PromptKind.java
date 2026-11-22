package pl.mzebrows.pwt.ui.hud;

/** Which of the few things a commander's one **prompt** says, in the order one beats another (G0098). */
public enum PromptKind {
    /** Nothing to say. */
    NONE,

    /** He is pinned: the strike key, over him - the one way out (G0045). */
    STRIKE,

    /** What the action key would do, over its subject. */
    ACTION,

    /** He has just earned an upgrade: the draft key, over him, for a few seconds (G0049). */
    UPGRADE,

    /**
     * He is down and may redeploy: the redeploy key, over him, above his down mark (G0109). Only while he is down, so
     * it never meets the others.
     */
    REDEPLOY
}
