package pl.mzebrows.pwt.net.transport;

/** How a message crosses a link. */
public enum Delivery {

    /**
     * Arrives, once, in the order it was sent relative to every other reliable message on the link, or
     * the link closes. For whatever must not be lost: the handshake, config, a snapshot, anything a
     * peer would be wrong without.
     */
    RELIABLE,

    /**
     * Arrives once, late, twice, out of order or not at all. For what is sent again anyway - every
     * tick's input goes out with the ticks before it still unacknowledged - where waiting for a lost
     * packet to be resent would only make the fresh one late. At most {@code maxDatagramBytes}.
     */
    UNRELIABLE
}
