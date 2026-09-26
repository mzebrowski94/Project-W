package pl.mzebrows.pwt.ui.hud;

import pl.mzebrows.pwt.sim.def.WeaponKind;

/**
 * One player's facts, by slot, as his panel shows them. Plain fields, filled only by {@link
 * ReadoutWriter}; kept by slot rather than by entity, because a pooled entity held from one frame to the
 * next comes back as something else (G0016).
 */
public final class PlayerReadout {

    public Posture posture = Posture.ABSENT;

    /** The gun in his hands, or the turret's while he is aboard one. */
    public WeaponKind weapon = WeaponKind.SIDEARM;

    /** How hot that gun is, in {@code [0, 1]}, and whether it has stopped itself. */
    public float heat;

    public boolean overheated;

    /** How much of his bleed-out is left while he is a casualty, in {@code [0, 1]}. */
    public float bleedOut;

    /** Seconds until he is back, while he is respawning. */
    public float respawnSeconds;

    /** Which of the commander's colours he wears. */
    public int variant;

    void clear() {
        posture = Posture.ABSENT;
        weapon = WeaponKind.SIDEARM;
        heat = 0f;
        overheated = false;
        bleedOut = 0f;
        respawnSeconds = 0f;
        variant = 0;
    }

    void copyFrom(PlayerReadout other) {
        posture = other.posture;
        weapon = other.weapon;
        heat = other.heat;
        overheated = other.overheated;
        bleedOut = other.bleedOut;
        respawnSeconds = other.respawnSeconds;
        variant = other.variant;
    }
}
