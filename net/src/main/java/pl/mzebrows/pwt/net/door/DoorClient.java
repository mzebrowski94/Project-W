package pl.mzebrows.pwt.net.door;

import org.jspecify.annotations.Nullable;

/**
 * The client's half of the door (G0103, ADR 0023): what a machine says first on a link to a relay - an {@link
 * Door.Open} to host, a {@link Door.Join} to join - and what it makes of the answer. A state machine and no
 * more: whoever owns the link sends {@link #first()} when the link opens and hands every message after it to
 * {@link #hear} until the state is no longer {@link State#WAITING}. The session's peer does so, and so
 * will the pages that host and join (stage 6); a test harness uses it the same way.
 */
public final class DoorClient {

    /** Where the exchange stands. */
    public enum State {
        /** The request has not been answered. */
        WAITING,
        /** The relay let this machine in: {@link #code()} is its room. Whatever comes now is the session's. */
        IN,
        /** The relay said no, and {@link #refusal()} says why; it closes the link. */
        REFUSED
    }

    private final byte[] first;
    private State state = State.WAITING;
    private @Nullable String code;
    private @Nullable String refusal;

    private DoorClient(Door.Message request) {
        this.first = Door.encode(request);
    }

    /** A host asking for a room; {@code password} is as typed, or empty for none. */
    public static DoorClient open(
            String buildId, String buildVersion, long hostKey, byte[] sessionConfig, String password) {
        return new DoorClient(new Door.Open(buildId, buildVersion, hostKey, sessionConfig, password));
    }

    /** A machine asking into the room of this code; {@code password} is as typed, or empty for none. */
    public static DoorClient join(String code, String password, String buildId, String buildVersion) {
        return new DoorClient(new Door.Join(code, password, buildId, buildVersion));
    }

    /** What to send when the link opens. */
    public byte[] first() {
        return first.clone();
    }

    public State state() {
        return state;
    }

    /** The room's code once {@link State#IN}, else null. */
    public @Nullable String code() {
        return code;
    }

    /** The relay's whole refusal text once {@link State#REFUSED}, else null. */
    public @Nullable String refusal() {
        return refusal;
    }

    /**
     * Takes one message from the relay.
     *
     * @throws IllegalArgumentException for a message that is not an answer the door gives
     */
    public void hear(byte[] data, int offset, int length) {
        if (state != State.WAITING) {
            return;
        }
        switch (Door.read(data, offset, length)) {
            case Door.Opened opened -> {
                code = opened.code();
                state = State.IN;
            }
            case Door.Accepted accepted -> {
                code = accepted.code();
                state = State.IN;
            }
            case Door.Refused refused -> {
                refusal = refused.text();
                state = State.REFUSED;
            }
            default -> throw new IllegalArgumentException("a request where an answer was due");
        }
    }
}
