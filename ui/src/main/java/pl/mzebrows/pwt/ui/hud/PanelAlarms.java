package pl.mzebrows.pwt.ui.hud;

import pl.mzebrows.pwt.sim.Players;

/**
 * When a player's panel raises the alarm, and how its alert pulses.
 *
 * <p>A commander has no chip damage to react to - a hostile blow downs or kills him - so the panel
 * reacts to the <b>transition</b>: entering DOWN, KIA or OVERHEAT flashes his frame and shakes it for a
 * moment, and the status turns alert. Staying in the state does not raise it again; nothing moves when
 * nothing changed (plan 6.4). A new world is silent: whatever state its players are in, they did not
 * just enter it here.
 *
 * <p>Frame time, not simulation time: a paused game keeps the alert pulsing and lets a flash finish.
 */
public final class PanelAlarms {

    /** What a panel can be alarmed about. */
    public enum Alarm {
        NONE,
        DOWN,
        KIA,
        OVERHEAT
    }

    private static final int SHAKE_FRAMES_PER_SECOND = 30;

    private final float alarmSeconds;
    private final int shake;
    private final float pulseHz;
    private final boolean flashes;
    private final Alarm[] alarm = new Alarm[Players.MAX];
    private final float[] since = new float[Players.MAX];
    private long worldSerial = Long.MIN_VALUE;
    private float clock;

    /**
     * @param alarmSeconds how long a flash and a shake last
     * @param shake how far a frame shakes, in UI pixels, before the motion scale
     * @param pulseHz how fast an alert pulses
     * @param motionScale multiplies the shake; 0 keeps every frame still
     * @param flashScale 0 turns flashes and pulses into a steady change
     */
    public PanelAlarms(float alarmSeconds, int shake, float pulseHz, float motionScale, float flashScale) {
        this.alarmSeconds = alarmSeconds;
        this.shake = Math.round(shake * motionScale);
        this.pulseHz = pulseHz;
        this.flashes = flashScale > 0f;
        for (int slot = 0; slot < Players.MAX; slot++) {
            alarm[slot] = Alarm.NONE;
            since[slot] = Float.MAX_VALUE;
        }
    }

    /** What a player's panel is alarmed about right now. */
    public static Alarm of(PlayerReadout player) {
        return switch (player.posture) {
            case CASUALTY -> Alarm.DOWN;
            case DYING, RESPAWNING -> Alarm.KIA;
            case STANDING, ABOARD -> player.overheated ? Alarm.OVERHEAT : Alarm.NONE;
            case ABSENT -> Alarm.NONE;
        };
    }

    /** Follows the readout. Call once a frame. */
    public void update(Readout readout, float frameSeconds) {
        clock += frameSeconds;
        boolean newWorld = readout.worldSerial != worldSerial;
        worldSerial = readout.worldSerial;
        for (int slot = 0; slot < Players.MAX; slot++) {
            Alarm now = of(readout.player(slot));
            if (now != alarm[slot] && now != Alarm.NONE && !newWorld) {
                since[slot] = 0f;
            } else if (since[slot] < Float.MAX_VALUE) {
                since[slot] += frameSeconds;
            }
            alarm[slot] = now;
        }
    }

    /** Whether a panel's frame is drawn in the alert colour this frame. */
    public boolean flashing(int slot) {
        return flashes && since[slot] < alarmSeconds;
    }

    /** How far a panel is pushed sideways this frame, in whole UI pixels. */
    public int shakeOffset(int slot) {
        if (shake == 0 || since[slot] >= alarmSeconds) {
            return 0;
        }
        return ((int) (since[slot] * SHAKE_FRAMES_PER_SECOND) & 1) == 0 ? shake : -shake;
    }

    /** Whether alerts are in the bright half of their pulse; always, with flashes off. */
    public boolean alertBright() {
        if (!flashes || pulseHz <= 0f) {
            return true;
        }
        float cycles = clock * pulseHz;
        return cycles - (int) cycles < 0.5f;
    }
}
