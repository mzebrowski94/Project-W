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
 * <p>Everything happens on the way <em>out</em>: a message is held here until it is due and handed to
 * the wrapped transport on the first {@link #poll} after that. Latency and jitter apply to both kinds of
 * message; loss and duplication only to unreliable ones, because a reliable message is by definition
 * neither lost nor repeated. Reliable messages leave in the order they were sent, however each one
 * was delayed. A link disconnected while messages to it are held drops them, as a real one would.
 */
public final class ConditionedTransport implements Transport {

    private final Transport inner;
    private final Conditions conditions;
    private final Clock clock;
    private final SplittableRandom rng;
    private final PriorityQueue<Held> held = new PriorityQueue<>();
    private final Map<Integer, Long> lastReliableDue = new HashMap<>();
    private long sequence;

    public ConditionedTransport(Transport inner, Conditions conditions, Clock clock) {
        this.inner = inner;
        this.conditions = conditions;
        this.clock = clock;
        this.rng = new SplittableRandom(conditions.seed());
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
        if (!conditions.any()) {
            inner.send(link, delivery, data, offset, length);
            return;
        }
        byte[] copy = Arrays.copyOfRange(data, offset, offset + length);
        if (delivery == Delivery.RELIABLE) {
            long due = Math.max(dueFromNow(), lastReliableDue.getOrDefault(link, Long.MIN_VALUE));
            lastReliableDue.put(link, due);
            held.add(new Held(due, sequence++, link, delivery, copy));
            return;
        }
        if (rng.nextFloat() < conditions.loss()) {
            return;
        }
        held.add(new Held(dueFromNow(), sequence++, link, delivery, copy));
        if (rng.nextFloat() < conditions.duplicate()) {
            held.add(new Held(dueFromNow(), sequence++, link, delivery, copy));
        }
    }

    @Override
    public void disconnect(int link, String reason) {
        held.removeIf(message -> message.link == link);
        lastReliableDue.remove(link);
        inner.disconnect(link, reason);
    }

    @Override
    public void poll(TransportListener listener) {
        long now = clock.nanos();
        for (Held next = held.peek(); next != null && next.due <= now; next = held.peek()) {
            held.poll();
            inner.send(next.link, next.delivery, next.data, 0, next.data.length);
        }
        inner.poll(listener);
    }

    @Override
    public void close() {
        held.clear();
        inner.close();
    }

    private long dueFromNow() {
        long delayMillis = (long) conditions.latencyMillis()
                + (conditions.jitterMillis() == 0 ? 0 : rng.nextInt(conditions.jitterMillis() + 1));
        return clock.nanos() + delayMillis * 1_000_000L;
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
