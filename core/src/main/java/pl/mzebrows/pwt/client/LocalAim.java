package pl.mzebrows.pwt.client;

import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Players;

/**
 * Where this machine's own players are aiming this frame - the <strong>cosmetic lead</strong> (ADR
 * 0012). Online a commander's facing reaches the world an input delay after the stick or mouse moved;
 * drawn from here instead, it follows the hand at once. It is drawn and nothing else: the world, and so
 * every shot, still goes where the world says.
 *
 * <p>It can be drawn without contradicting the world because a commander's facing <em>is</em> the
 * direction to its aim, with no turn rate between (G0002): the same rule applied to newer input. A
 * relative aim - a pad's stick - is a direction; an absolute one - the mouse - is a point in the world,
 * taken from where the commander is drawn. An aim too short to mean anything leaves the world's facing,
 * as the world itself does.
 */
final class LocalAim {

    /** What {@code Facing.face} treats as no direction at all. */
    private static final float SHORTEST = 1e-4f;

    private final boolean[] aiming = new boolean[Players.MAX];
    private final boolean[] relative = new boolean[Players.MAX];
    private final float[] aimX = new float[Players.MAX];
    private final float[] aimY = new float[Players.MAX];

    /** Forgets every aim: nothing is drawn ahead until {@link #set} says so again. */
    void clear() {
        java.util.Arrays.fill(aiming, false);
    }

    /** What the player in this slot is aiming with this frame. */
    void set(int slot, CommanderInput input) {
        if (slot < 0 || slot >= aiming.length) {
            return;
        }
        aiming[slot] = true;
        relative[slot] = input.aimRelative();
        aimX[slot] = input.aimX();
        aimY[slot] = input.aimY();
    }

    /**
     * The facing to draw for this slot's commander standing at ({@code x}, {@code y}), written into
     * {@code out} as a unit vector. False, with {@code out} untouched, when the world's own facing should
     * be drawn: nobody here plays the slot, or the aim says nothing.
     */
    boolean facing(int slot, float x, float y, float[] out) {
        if (slot < 0 || slot >= aiming.length || !aiming[slot]) {
            return false;
        }
        float dx = relative[slot] ? aimX[slot] : aimX[slot] - x;
        float dy = relative[slot] ? aimY[slot] : aimY[slot] - y;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < SHORTEST) {
            return false;
        }
        float scale = 1f / (float) Math.sqrt(lengthSquared);
        out[0] = dx * scale;
        out[1] = dy * scale;
        return true;
    }
}
