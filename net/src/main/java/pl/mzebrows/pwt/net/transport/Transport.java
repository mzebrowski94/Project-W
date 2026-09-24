package pl.mzebrows.pwt.net.transport;

/**
 * Links between machines, and messages across them (ADR 0012).
 *
 * <p><strong>A port of our own, polled rather than called back.</strong> The library behind it - Netty
 * today - is the replaceable part, exactly as {@code GamepadSource} makes gdx-controllers replaceable
 * (ADR 0011), and for the same reason events are collected with {@link #poll} instead of pushed: every
 * library has its own idea of which thread a callback arrives on, and asking "what has happened since
 * I last looked" is something all of them can answer.
 *
 * <p>A <strong>link</strong> is this machine's connection to one other: an int, never reused within one
 * transport, carrying {@link Delivery#RELIABLE reliable} and {@link Delivery#UNRELIABLE unreliable}
 * messages. It is reported {@link TransportListener#opened opened} once both kinds can cross it, and
 * {@link TransportListener#closed closed} exactly once.
 *
 * <p>Not thread-safe: one thread - the game's - calls everything here.
 */
public interface Transport extends AutoCloseable {

    /**
     * Starts accepting links. Tries {@code firstPort}, then each of the next {@code search} ports, and
     * returns the one it got - so a second instance on the same machine finds one of its own.
     *
     * @throws IllegalStateException when every port in the range is taken
     */
    int listen(int firstPort, int search);

    /**
     * Starts opening a link to a transport listening at {@code host:port} and returns its number at
     * once. The outcome arrives through {@link #poll}: opened, or closed with the reason it failed.
     */
    int connect(String host, int port);

    /**
     * Sends a message. The bytes are copied before this returns, so the caller may reuse its array.
     * A message to a link that is not open is dropped, which is what a link that has just closed
     * would have done with it anyway.
     *
     * @throws IllegalArgumentException for an unreliable message longer than a datagram may be, or a
     *     reliable one longer than a message may be
     */
    void send(int link, Delivery delivery, byte[] data, int offset, int length);

    /** Closes one link. Its closed event still arrives through {@link #poll}, with this reason. */
    void disconnect(int link, String reason);

    /** Hands everything that has happened since the last call to {@code listener}, on this thread. */
    void poll(TransportListener listener);

    /** Closes every link and stops listening. Nothing is reported afterwards. */
    @Override
    void close();
}
