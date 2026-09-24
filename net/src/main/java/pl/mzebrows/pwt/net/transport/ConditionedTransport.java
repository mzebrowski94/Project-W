package pl.mzebrows.pwt.net.transport;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SplittableRandom;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.NetConfig.Conditions;

/**
 * A transport that makes whatever it wraps behave like a worse network: late, jittery, lossy,
 * repetitive (ADR 0012).
 *
 * <p>Two instances on one machine talk over a network with no delay at all, which hides every bug
 * lockstep has; so does a test over a perfect in-memory network. Wrapped in this, both behave like a
 * link across a city, and the same seed makes them misbehave the same way twice.
 *
 * <p><strong>Both ways.</strong> A message is held on its way out, until it is due, and handed to the
 * wrapped transport on the first {@link #poll} after that; one arriving is held the same way before the
 * listener hears it. So one machine wrapped in this has a whole bad round trip to everyone - twice the
 * latency - and the machines it talks to need nothing. Latency and jitter apply to both kinds of
 * message; loss and duplication only to unreliable ones, because a reliable message is by definition
 * neither lost nor repeated. Reliable messages keep their order however each one was delayed. A link
 * that closes while messages on it are held drops them, as a real one would.
 *
 * <p>The conditions can be changed while it runs - a playtest steps through latencies without
 * restarting - and are {@link Conditions#NONE} for a straight pass-through.
 */
public final class ConditionedTransport implements Transport {

    private final Transport inner;
    private final Clock clock;
    private final PriorityQueue<Held> outgoing = new PriorityQueue<>();
    private final PriorityQueue<Held> incoming = new PriorityQueue<>();
    private final Map<Integer, Long> lastReliableOut = new HashMap<>();
    private final Map<Integer, Long> lastReliableIn = new HashMap<>();
    private final Arrivals arrivals = new Arrivals();
    private Conditions conditions;
    private SplittableRandom rng;
    private long sequence;

    public ConditionedTransport(Transport inner, Conditions conditions, Clock clock) {
        this.inner = inner;
        this.conditions = conditions;
        this.clock = clock;
        this.rng = new SplittableRandom(conditions.seed());
    }

    /** The conditions now in force. */
    public Conditions conditions() {
        return conditions;
    }

    /**
     * Changes the conditions from here on. What is already held keeps the delay it was given; a new
     * seed starts a new sequence of draws.
     */
    public void conditions(Conditions next) {
        if (next.seed() != conditions.seed()) {
            rng = new SplittableRandom(next.seed());
        }
        conditions = next;
    }

    @Override
    public int listen(int firstPort, int search) {
        return inner.listen(firstPort, search);
    }

    @Override
    public int connect(String host, int port) {
        return inner.connect(host, port);
    }

    @Override
    public void send(int link, Delivery delivery, byte[] data, int offset, int length) {
        if (!conditions.any() && outgoing.isEmpty()) {
            inner.send(link, delivery, data, offset, length);
            return;
        }
        hold(outgoing, lastReliableOut, link, delivery, data, offset, length);
    }

    @Override
    public void disconnect(int link, String reason) {
        drop(link);
        inner.disconnect(link, reason);
    }

    @Override
    public void poll(TransportListener listener) {
        long now = clock.nanos();
        for (Held next = outgoing.peek(); next != null && next.due <= now; next = outgoing.peek()) {
            outgoing.poll();
            inner.send(next.link, next.delivery, next.data, 0, next.data.length);
        }
        arrivals.listener = listener;
        inner.poll(arrivals);
        for (Held next = incoming.peek(); next != null && next.due <= now; next = incoming.peek()) {
            incoming.poll();
            listener.received(next.link, next.delivery, next.data, 0, next.data.length);
        }
    }

    @Override
    public void close() {
        outgoing.clear();
        incoming.clear();
        inner.close();
    }

    private void hold(
            PriorityQueue<Held> queue,
            Map<Integer, Long> lastReliable,
            int link,
            Delivery delivery,
            byte[] data,
            int offset,
            int length) {
        byte[] copy = Arrays.copyOfRange(data, offset, offset + length);
        if (delivery == Delivery.RELIABLE) {
            long due = Math.max(dueFromNow(), lastReliable.getOrDefault(link, Long.MIN_VALUE));
            lastReliable.put(link, due);
            queue.add(new Held(due, sequence++, link, delivery, copy));
            return;
        }
        if (rng.nextFloat() < conditions.loss()) {
            return;
        }
        queue.add(new Held(dueFromNow(), sequence++, link, delivery, copy));
        if (rng.nextFloat() < conditions.duplicate()) {
            queue.add(new Held(dueFromNow(), sequence++, link, delivery, copy));
        }
    }

    private void drop(int link) {
        outgoing.removeIf(message -> message.link == link);
        incoming.removeIf(message -> message.link == link);
        lastReliableOut.remove(link);
        lastReliableIn.remove(link);
    }

    private long dueFromNow() {
        long delayMillis = (long) conditions.latencyMillis()
                + (conditions.jitterMillis() == 0 ? 0 : rng.nextInt(conditions.jitterMillis() + 1));
        return clock.nanos() + delayMillis * 1_000_000L;
    }

    /** What the wrapped transport hears, held back as the conditions say before the real listener does. */
    private final class Arrivals implements TransportListener {
        @SuppressWarnings("NullAway.Init") // set at the top of every poll, before the wrapped one can call
        TransportListener listener;

        @Override
        public void opened(int link, String remote) {
            listener.opened(link, remote);
        }

        @Override
        public void received(int link, Delivery delivery, byte[] data, int offset, int length) {
            if (!conditions.any() && incoming.isEmpty()) {
                listener.received(link, delivery, data, offset, length);
                return;
            }
            hold(incoming, lastReliableIn, link, delivery, data, offset, length);
        }

        @Override
        public void closed(int link, String reason) {
            drop(link);
            listener.closed(link, reason);
        }
    }

    /** A message waiting for its time. A class rather than a record because it holds an array. */
    private static final class Held implements Comparable<Held> {
        final long due;
        final long order;
        final int link;
        final Delivery delivery;
        final byte[] data;

        Held(long due, long order, int link, Delivery delivery, byte[] data) {
            this.due = due;
            this.order = order;
            this.link = link;
            this.delivery = delivery;
            this.data = data;
        }

        @Override
        public int compareTo(Held other) {
            int byDue = Long.compare(due, other.due);
            return byDue != 0 ? byDue : Long.compare(order, other.order);
        }
    }
}
