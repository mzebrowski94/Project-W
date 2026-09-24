package pl.mzebrows.pwt.net.loopback;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.transport.Delivery;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.net.transport.TransportListener;

/**
 * Transports that reach each other in memory: a network in one JVM, with nothing in it but what the
 * test puts there.
 *
 * <p><strong>Perfect on purpose.</strong> A message arrives at the other end's next poll, whole and
 * in order, every time. Bad networks are made by wrapping an endpoint in a {@code ConditionedTransport}
 * - the same wrapper that makes real sockets misbehave on demand - so there is one way to make a
 * network lag rather than two that could disagree.
 *
 * <p>Endpoints are named like machines. Two endpoints with the same name share that machine's ports,
 * which is how a test puts two hosts on one computer.
 *
 * <p>Single-threaded, like the game that polls it.
 */
public final class LoopbackNetwork {

    private final NetConfig config;

    /** Who is listening where: machine name, then port, then the endpoint listening on it. */
    private final Map<String, Map<Integer, Endpoint>> listening = new HashMap<>();

    public LoopbackNetwork(NetConfig config) {
        this.config = config;
    }

    /** A new transport on the named machine. */
    public Transport endpoint(String machine) {
        return new Endpoint(machine);
    }

    private record Far(Endpoint endpoint, int link) {}

    /** One thing waiting in an endpoint's inbox. A class rather than a record because it holds an array. */
    private static final class Event {
        final String kind;
        final int link;
        final String text;
        final Delivery delivery;
        final byte[] data;

        Event(String kind, int link, String text, Delivery delivery, byte[] data) {
            this.kind = kind;
            this.link = link;
            this.text = text;
            this.delivery = delivery;
            this.data = data;
        }
    }

    private final class Endpoint implements Transport {

        private final String machine;
        private final Map<Integer, Far> links = new TreeMap<>();
        private final ArrayDeque<Event> inbox = new ArrayDeque<>();
        private int nextLink = 1;
        private int port;
        private boolean closed;

        Endpoint(String machine) {
            this.machine = machine;
        }

        @Override
        public int listen(int firstPort, int search) {
            Map<Integer, Endpoint> ports = listening.computeIfAbsent(machine, m -> new HashMap<>());
            for (int candidate = firstPort; candidate <= firstPort + search; candidate++) {
                if (!ports.containsKey(candidate)) {
                    ports.put(candidate, this);
                    port = candidate;
                    return candidate;
                }
            }
            throw new IllegalStateException(
                    "every port in " + firstPort + ".." + (firstPort + search) + " on " + machine + " is taken");
        }

        @Override
        public int connect(String host, int remotePort) {
            int link = nextLink++;
            Map<Integer, Endpoint> ports = listening.get(host);
            Endpoint target = ports == null ? null : ports.get(remotePort);
            if (target == null || target.closed) {
                inbox.add(new Event(
                        "closed",
                        link,
                        "refused: nobody listening on " + host + ":" + remotePort,
                        Delivery.RELIABLE,
                        new byte[0]));
                return link;
            }
            int theirs = target.nextLink++;
            links.put(link, new Far(target, theirs));
            target.links.put(theirs, new Far(this, link));
            inbox.add(new Event("opened", link, host + ":" + remotePort, Delivery.RELIABLE, new byte[0]));
            target.inbox.add(new Event("opened", theirs, machine, Delivery.RELIABLE, new byte[0]));
            return link;
        }

        @Override
        public void send(int link, Delivery delivery, byte[] data, int offset, int length) {
            int limit = delivery == Delivery.UNRELIABLE ? config.maxDatagramBytes() : config.maxMessageBytes();
            if (length > limit) {
                throw new IllegalArgumentException(
                        delivery + " message of " + length + " bytes is over the " + limit + "-byte limit");
            }
            Far far = links.get(link);
            if (far == null) {
                return;
            }
            far.endpoint.inbox.add(
                    new Event("received", far.link, "", delivery, Arrays.copyOfRange(data, offset, offset + length)));
        }

        @Override
        public void disconnect(int link, String reason) {
            Far far = links.remove(link);
            if (far == null) {
                return;
            }
            inbox.add(new Event("closed", link, reason, Delivery.RELIABLE, new byte[0]));
            far.endpoint.links.remove(far.link);
            far.endpoint.inbox.add(new Event("closed", far.link, "remote: " + reason, Delivery.RELIABLE, new byte[0]));
        }

        @Override
        public void poll(TransportListener listener) {
            for (int remaining = inbox.size(); remaining > 0; remaining--) {
                Event event = inbox.poll();
                if (event == null) {
                    return;
                }
                switch (event.kind) {
                    case "opened" -> listener.opened(event.link, event.text);
                    case "received" -> listener.received(event.link, event.delivery, event.data, 0, event.data.length);
                    default -> listener.closed(event.link, event.text);
                }
            }
        }

        @Override
        public void close() {
            for (Integer link : links.keySet().toArray(new Integer[0])) {
                disconnect(link, "closed");
            }
            Map<Integer, Endpoint> ports = listening.get(machine);
            if (ports != null && ports.get(port) == this) {
                ports.remove(port);
            }
            inbox.clear();
            closed = true;
        }
    }
}
