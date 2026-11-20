package pl.mzebrows.pwt.net.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.net.NetConfig.LogConfig;

/**
 * The connection log: one file per running instance, one line per event, every line {@code key=value}
 * so it can be grepped by a person and read by Claude.
 *
 * <p>Built on {@code java.util.logging}, so a library that logs there - Netty does - lands in the same
 * file, leak reports included, with no bridge to maintain.
 *
 * <p><strong>Never called per message.</strong> CLAUDE.md bans a log call inside a per-frame loop, and a
 * network sends several messages a frame. What happens per message goes into a {@link NetTrace} - a
 * ring of plain numbers, no allocation - and is written out only when a link closes; each link's
 * numbers are written once every {@code statsSeconds} from {@link LinkStats}. The log itself is for events:
 * a link opening, a handshake stalling, a timeout, a reason.
 *
 * <p>Every line looks like {@code t=2026-09-24T10:11:12.345 lvl=INFO inst=host cat=link link=3
 * event=opened remote=127.0.0.1:50123}. Callers write the part after {@code cat=}; a library's own lines
 * arrive as {@code cat=netty msg="..."}.
 */
public final class NetLog implements AutoCloseable {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String instance;
    private final Logger logger;
    private final @Nullable Handler handler;
    private final int traceSize;
    private final List<Logger> adopted = new ArrayList<>();
    private final @Nullable Path file;

    private NetLog(String instance, Logger logger, @Nullable Handler handler, int traceSize, @Nullable Path file) {
        this.instance = instance;
        this.logger = logger;
        this.handler = handler;
        this.traceSize = traceSize;
        this.file = file;
    }

    /**
     * Opens a new log file for this instance in the configured directory, named after the instance and
     * the moment it started - so two instances on one machine, and two runs of one, never share a file.
     * Falls back to a log that writes nothing, and says so on standard error, if the file cannot be made:
     * a game that cannot log still plays.
     */
    public static NetLog open(LogConfig config, String instance) {
        return open(config, instance, false);
    }

    /**
     * As {@link #open(LogConfig, String)}, and with {@code echo} the lines that say what a link or a room did - the
     * relay's and the sequencer's, INFO and up - go to standard output too, for an operator watching a console. The
     * per-second numbers and the per-message detail stay in the file only.
     */
    public static NetLog open(LogConfig config, String instance, boolean echo) {
        Level level = level(config.level());
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(level);
        try {
            Path dir = Path.of(config.dir());
            Files.createDirectories(dir);
            String name =
                    instance + "-" + LocalDateTime.now(ZoneId.systemDefault()).format(FILE_STAMP) + ".log";
            Path file = dir.resolve(name).toAbsolutePath();
            FileHandler handler = new FileHandler(file.toString(), true);
            handler.setLevel(level);
            handler.setFormatter(new LineFormatter(instance));
            handler.setEncoding("UTF-8");
            logger.addHandler(handler);
            if (echo) {
                logger.addHandler(echoHandler(instance));
            }
            return new NetLog(instance, logger, handler, config.traceSize(), file);
        } catch (IOException | RuntimeException e) {
            System.err.println("[net] could not open a log in " + config.dir() + ": " + e);
            return new NetLog(instance, logger, null, config.traceSize(), null);
        }
    }

    /** Standard output, one line per record in the file's own format, of the categories an operator reads. */
    private static Handler echoHandler(String instance) {
        StreamHandler echo = new StreamHandler(System.out, new LineFormatter(instance)) {
            @Override
            public synchronized void publish(LogRecord record) {
                String message = record.getMessage();
                if (message != null && (message.startsWith("cat=relay ") || message.startsWith("cat=sequencer "))) {
                    super.publish(record);
                    flush();
                }
            }

            // Standard output is not this handler's to close.
            @Override
            public synchronized void close() {
                flush();
            }
        };
        echo.setLevel(Level.INFO);
        return echo;
    }

    /** A log that writes nothing, for tests that are not about the log. Its traces still record. */
    public static NetLog silent(int traceSize) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);
        return new NetLog("silent", logger, null, traceSize, null);
    }

    /** The file being written, or null for a log that writes nothing. */
    public @Nullable Path file() {
        return file;
    }

    /**
     * A new ring for one transport to record every message into, named {@code owner} in its dumps. One
     * per transport: a ring is not shared between threads.
     */
    public NetTrace trace(String owner) {
        return new NetTrace(owner, traceSize);
    }

    /**
     * Sends another {@code java.util.logging} logger's lines - a library's - to this file too, for as long
     * as this log is open.
     */
    public void adopt(String loggerName) {
        if (handler == null) {
            return;
        }
        Logger foreign = Logger.getLogger(loggerName);
        foreign.addHandler(handler);
        adopted.add(foreign);
    }

    /** Detail: handshake steps, retries. */
    public void debug(String category, String keyValues) {
        write(Level.FINE, category, keyValues, null);
    }

    /** What a link did: opened, closed, why. */
    public void info(String category, String keyValues) {
        write(Level.INFO, category, keyValues, null);
    }

    /** Something went wrong and was survived: a timeout, a refused port, a stray datagram. */
    public void warn(String category, String keyValues) {
        write(Level.WARNING, category, keyValues, null);
    }

    /** Something went wrong and was not survived, with its cause. */
    public void error(String category, String keyValues, @Nullable Throwable cause) {
        write(Level.SEVERE, category, keyValues, cause);
    }

    /** Whether a line at this level would be written, so a caller can skip building one that would not. */
    public boolean writes(Level level) {
        return handler != null && logger.isLoggable(level);
    }

    @Override
    public void close() {
        if (handler == null) {
            return;
        }
        for (Logger foreign : adopted) {
            foreign.removeHandler(handler);
        }
        adopted.clear();
        for (Handler each : logger.getHandlers()) {
            logger.removeHandler(each);
            if (!each.equals(handler)) {
                each.close();
            }
        }
        handler.close();
    }

    private void write(Level level, String category, String keyValues, @Nullable Throwable cause) {
        if (handler == null || !logger.isLoggable(level)) {
            return;
        }
        LogRecord record = new LogRecord(level, "cat=" + category + " " + keyValues);
        record.setLoggerName("pwt.net." + instance);
        record.setThrown(cause);
        logger.log(record);
    }

    private static Level level(String name) {
        try {
            return Level.parse(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }

    /** One line per record, {@code key=value}, and a library's free text quoted as {@code msg}. */
    static final class LineFormatter extends Formatter {

        private final String instance;

        LineFormatter(String instance) {
            this.instance = instance;
        }

        @Override
        public String format(LogRecord record) {
            StringBuilder line = new StringBuilder(160);
            line.append("t=")
                    .append(LocalDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault())
                            .format(STAMP))
                    .append(" lvl=")
                    .append(record.getLevel().getName())
                    .append(" inst=")
                    .append(instance)
                    .append(' ');
            String message = formatMessage(record);
            String source = record.getLoggerName();
            if (source != null && source.startsWith("pwt.net.")) {
                line.append(message);
            } else {
                line.append("cat=")
                        .append(source == null ? "library" : source.startsWith("io.netty") ? "netty" : source)
                        .append(" msg=\"")
                        .append(message.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n"))
                        .append('"');
            }
            Throwable thrown = record.getThrown();
            if (thrown != null) {
                line.append(" error=\"").append(thrown).append('"');
            }
            return line.append(System.lineSeparator()).toString();
        }
    }
}
