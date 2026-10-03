package pl.mzebrows.pwt.client.input;

/**
 * Where gamepads come from. One implementation wraps a library; the rest of the game knows only this.
 *
 * <p><strong>Deliberately a snapshot and not an event stream.</strong> Every controller library has
 * its own idea of connect and disconnect callbacks - which thread they arrive on, whether they
 * survive a re-plug, whether they fire at all on the platform you are on - and depending on them
 * would make the port as wide as the widest library. Asking "who is plugged in right now" is
 * something every library can answer, and {@link PlayerSeats} works out who is new and who has gone
 * by comparing one frame's answer with the last. A library with no hot-plug events at all is a
 * perfectly good implementation of this.
 */
public interface GamepadSource {

    /** How many pads are plugged in right now. */
    int count();

    /**
     * One of them. <strong>The index is not stable</strong> - unplugging the first pad renumbers the
     * rest - so anything that has to remember a pad remembers its {@link Gamepad#id()}.
     */
    Gamepad get(int index);
}
