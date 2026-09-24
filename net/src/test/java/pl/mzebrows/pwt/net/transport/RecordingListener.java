package pl.mzebrows.pwt.net.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Writes down everything a transport reports, in order, so a test can read it back as plain values. */
public final class RecordingListener implements TransportListener {

    /**
     * One thing a transport said. A message is kept as its length, its first byte and a hash of all of
     * it - enough to tell messages apart and to prove a large one arrived whole.
     */
    public record Heard(String kind, int link, String text, Delivery delivery, int length, int firstByte, int hash) {}

    private final List<Heard> heard = new ArrayList<>();

    @Override
    public void opened(int link, String remote) {
        heard.add(new Heard("opened", link, remote, Delivery.RELIABLE, 0, -1, 0));
    }

    @Override
    public void received(int link, Delivery delivery, byte[] data, int offset, int length) {
        byte[] copy = Arrays.copyOfRange(data, offset, offset + length);
        heard.add(new Heard("received", link, "", delivery, length, length == 0 ? -1 : copy[0], Arrays.hashCode(copy)));
    }

    @Override
    public void closed(int link, String reason) {
        heard.add(new Heard("closed", link, reason, Delivery.RELIABLE, 0, -1, 0));
    }

    public List<Heard> heard() {
        return heard;
    }

    public List<Heard> of(String kind) {
        return heard.stream().filter(h -> h.kind().equals(kind)).toList();
    }

    public void forget() {
        heard.clear();
    }
}
