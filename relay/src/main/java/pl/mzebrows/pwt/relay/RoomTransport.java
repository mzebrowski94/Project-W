package pl.mzebrows.pwt.relay;

import java.util.concurrent.ConcurrentLinkedQueue;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;

/**
 * The relay's one {@link Transport}, seen from a room (ADR 0023): the view its {@code Sequencer} is built over,
 * which is how the sequencer stays free of any knowledge of rooms. It holds the links the door has let into
 * this room and nothing else. What the relay hears on them it hands in ({@link #opened}, {@link #received},
 * {@link #closed}) and the sequencer finds on its next {@code poll}; what the sequencer sends, drops or ends
 * waits in a queue for the relay to put on the real transport ({@link #drainTo}).
 *
 * <p>Two queues, because the real transport is polled by the relay's thread alone and the sequencer may run on
 * a thread of its own ({@code SequencerThread}); they are the only thing the two threads share. Messages are
 * copied once on the way in and once on the way out, which a relay can afford: per message, never per entity.
 */
final class RoomTransport implements Transport {

    private enum Kind {
        OPENED,
        RECEIVED,
        CLOSED,
        SEND,
        DISCONNECT,
        END
    }

    /** One thing that happened, or one thing asked for; `delivery` and `data` mean something for a message only. */
    private static final class Event {
        final Kind kind;
        final int link;
        final Delivery delivery;
        final byte[] data;
        final String text;

        Event(Kind kind, int link, Delivery delivery, byte[] data, String text) {
            this.kind = kind;
            this.link = link;
            this.delivery = delivery;
            this.data = data;
            this.text = text;
        }
    }

    private static final byte[] NONE = new byte[0];

    private final ConcurrentLinkedQueue<Event> inbound = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Event> outbound = new ConcurrentLinkedQueue<>();

    /** The relay, on its thread: a link the door let in. */
    void opened(int link, String remote) {
        inbound.add(new Event(Kind.OPENED, link, Delivery.RELIABLE, NONE, remote));
    }

    /** The relay, on its thread: a message from a link in this room. The bytes are copied. */
    void received(int link, Delivery delivery, byte[] data, int offset, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(data, offset, copy, 0, length);
        inbound.add(new Event(Kind.RECEIVED, link, delivery, copy, ""));
    }

    /** The relay, on its thread: a link of this room is gone. */
    void closed(int link, String reason) {
        inbound.add(new Event(Kind.CLOSED, link, Delivery.RELIABLE, NONE, reason));
    }

    /**
     * The relay, on its thread: puts what the sequencer has sent on {@code real}, in the order it was said.
     *
     * @return whether the sequencer has ended: it closed this transport, and the room is over
     */
    boolean drainTo(Transport real) {
        boolean ended = false;
        for (Event event = outbound.poll(); event != null; event = outbound.poll()) {
            switch (event.kind) {
                case SEND -> real.send(event.link, event.delivery, event.data, 0, event.data.length);
                case DISCONNECT -> real.disconnect(event.link, event.text);
                case END -> ended = true;
                default -> throw new IllegalStateException("not an outbound event: " + event.kind);
            }
        }
        return ended;
    }

    @Override
    public int listen(int firstPort, int search) {
        throw new UnsupportedOperationException("a room's links are the relay's, which listens");
    }

    @Override
    public int connect(String host, int port) {
        throw new UnsupportedOperationException("a room's sequencer only hears links, it never opens one");
    }

    @Override
    public void send(int link, Delivery delivery, byte[] data, int offset, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(data, offset, copy, 0, length);
        outbound.add(new Event(Kind.SEND, link, delivery, copy, ""));
    }

    @Override
    public void disconnect(int link, String reason) {
        outbound.add(new Event(Kind.DISCONNECT, link, Delivery.RELIABLE, NONE, reason));
    }

    @Override
    public void poll(TransportListener listener) {
        for (Event event = inbound.poll(); event != null; event = inbound.poll()) {
            switch (event.kind) {
                case OPENED -> listener.opened(event.link, event.text);
                case RECEIVED -> listener.received(event.link, event.delivery, event.data, 0, event.data.length);
                case CLOSED -> listener.closed(event.link, event.text);
                default -> throw new IllegalStateException("not an inbound event: " + event.kind);
            }
        }
    }

    /** The sequencer has ended the session: every link has been told, and the room is over. */
    @Override
    public void close() {
        outbound.add(new Event(Kind.END, -1, Delivery.RELIABLE, NONE, ""));
    }
}
