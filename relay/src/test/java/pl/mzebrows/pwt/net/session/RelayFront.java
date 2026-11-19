package pl.mzebrows.pwt.net.session;

import java.util.SplittableRandom;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.ManualClock;
import pl.mzebrows.pwt.net.NetConfig;
import pl.mzebrows.pwt.net.log.NetLog;
import pl.mzebrows.pwt.net.session.SessionHarness.Front;
import pl.mzebrows.pwt.net.transport.Transport;
import pl.mzebrows.pwt.relay.Relay;

/**
 * A {@link Relay} for {@link SessionHarness} to play a session through: pumped with the session's manual clock,
 * its sequencers pumped by its own poll. Lives in the {@code relay} module's tests, in the harness's package,
 * because the harness is package-private and {@code net} cannot see the relay.
 */
final class RelayFront implements Front {

    final Relay relay;

    RelayFront(Transport relayEnd, ManualClock clock, NetConfig config) {
        this.relay = new Relay(relayEnd, clock, NetLog.silent(64), config, new SplittableRandom(5));
    }

    @Override
    public void pump(long nowNanos) {
        relay.poll();
    }

    @Override
    public @Nullable Sequencer sequencerOf(String code) {
        return relay.sequencerOf(code);
    }

    @Override
    public void close() {
        relay.close();
    }
}
