package pl.mzebrows.pwt.net.session;

import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.Clock;
import pl.mzebrows.pwt.net.log.NetLog;

/**
 * Runs a {@link Sequencer} on a thread of its own, so the host's game stalling - a window dragged, a
 * frame that takes too long - does not stall the session (ADR 0012). It pumps the sequencer at least
 * once a millisecond, so input is heard as soon as it arrives, and more often when a tick is due sooner.
 *
 * <p>The sequencer is touched by this thread only, from {@link #start()} to {@link #close()}.
 */
public final class SequencerThread implements AutoCloseable {

    private static final long MOST_SLEEP_NANOS = 1_000_000L;

    private final Sequencer sequencer;
    private final Clock clock;
    private final NetLog log;
    private final Thread thread;
    private volatile boolean running = true;
    private volatile @Nullable Throwable failure;

    public SequencerThread(Sequencer sequencer, Clock clock, NetLog log) {
        this.sequencer = sequencer;
        this.clock = clock;
        this.log = log;
        this.thread = new Thread(this::run, "pwt-sequencer");
        this.thread.setDaemon(true);
    }

    public SequencerThread start() {
        thread.start();
        return this;
    }

    /** What stopped the thread, if something did: the session is over, and this says why. */
    public @Nullable Throwable failure() {
        return failure;
    }

    @Override
    public void close() {
        running = false;
        LockSupport.unpark(thread);
        try {
            thread.join(2_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        try {
            while (running) {
                long now = clock.nanos();
                sequencer.pump(now);
                long wait = Math.min(MOST_SLEEP_NANOS, sequencer.nextDeadlineNanos() - clock.nanos());
                if (wait > 0) {
                    LockSupport.parkNanos(wait);
                }
            }
            sequencer.close();
        } catch (RuntimeException | Error e) {
            failure = e;
            log.error("sequencer", "event=failed", e);
            sequencer.end("the host's session failed: " + e);
        }
    }
}
