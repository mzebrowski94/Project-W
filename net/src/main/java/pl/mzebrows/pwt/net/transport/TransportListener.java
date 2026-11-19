package pl.mzebrows.pwt.net.transport;

/**
 * What a {@link Transport} reports, handed over on the caller's thread during {@link Transport#poll}.
 *
 * <p>Nothing arrives between polls and nothing arrives on a network thread, which is what lets the
 * code above the port be single-threaded like the rest of the game.
 */
public interface TransportListener {

    /**
     * A link is open: both kinds of message can now cross it. {@code remote} is for the log - and for the relay,
     * which reads the address from it: the text after any {@code /}, without a trailing {@code :port}.
     */
    void opened(int link, String remote);

    /**
     * A message arrived on a link. The bytes are valid only for the duration of the call - copy what
     * has to be kept, because the array is reused for the next message.
     */
    void received(int link, Delivery delivery, byte[] data, int offset, int length);

    /**
     * A link is gone, or never opened, and why. Said exactly once per link, and nothing more arrives
     * on it afterwards.
     */
    void closed(int link, String reason);
}
