package pl.mzebrows.pwt.net.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What one machine knew about a tick the peers disagreed on (ADR 0012): its world at that tick, the
 * last world everybody agreed on before it, and every bundle in between. The desynced peer writes one
 * and the host writes one, and {@link DesyncReport} reads the pair: which fields differ, and whether
 * replaying the interval here reproduces either side.
 *
 * <p>Written beside the connection log, with a {@code .txt} of the same name that a person - or Claude -
 * can read without the tool. A class rather than a record because it holds arrays.
 */
public final class DesyncDump {

    /** "PWD1". */
    private static final int MAGIC = 0x5057_4431;

    private final String build;
    private final int peer;
    private final String role;
    private final SessionSetup setup;
    private final int agreedTick;
    private final byte[] agreed;
    private final int tick;
    private final byte[] world;
    private final long hash;
    private final List<byte[]> bundles;

    /**
     * @param role "desynced" for the peer that disagreed, "reference" for the host it disagreed with
     * @param agreedTick the keyframe before the disputed one, -1 if there was none
     * @param bundles every bundle from {@code agreedTick + 1} to {@code tick}, empty if not all were kept
     */
    DesyncDump(
            String build,
            int peer,
            String role,
            SessionSetup setup,
            int agreedTick,
            byte[] agreed,
            int tick,
            byte[] world,
            long hash,
            List<byte[]> bundles) {
        this.build = build;
        this.peer = peer;
        this.role = role;
        this.setup = setup;
        this.agreedTick = agreedTick;
        this.agreed = agreed;
        this.tick = tick;
        this.world = world;
        this.hash = hash;
        this.bundles = List.copyOf(bundles);
    }

    public String build() {
        return build;
    }

    public int peer() {
        return peer;
    }

    public String role() {
        return role;
    }

    public SessionSetup setup() {
        return setup;
    }

    public int agreedTick() {
        return agreedTick;
    }

    byte[] agreed() {
        return agreed;
    }

    public int tick() {
        return tick;
    }

    byte[] world() {
        return world;
    }

    public long hash() {
        return hash;
    }

    List<byte[]> bundles() {
        return bundles;
    }

    /** Writes this and its readable summary beside it. */
    void write(Path file) throws IOException {
        WireWriter out = new WireWriter(world.length + agreed.length + 4096);
        out.putInt(MAGIC)
                .putString(build)
                .putInt(peer)
                .putString(role)
                .putBytes(setup.configText().getBytes(StandardCharsets.UTF_8))
                .putString(setup.scenario())
                .putLong(setup.seed())
                .putInt(agreedTick)
                .putBytes(agreed)
                .putInt(tick)
                .putBytes(world)
                .putLong(hash)
                .putInt(bundles.size());
        for (byte[] bundle : bundles) {
            out.putBytes(bundle);
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, out.toBytes());
        Files.writeString(summaryOf(file), summary(), StandardCharsets.UTF_8);
    }

    /** Reads a dump {@link #write} wrote. */
    public static DesyncDump read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        WireReader in = new WireReader().wrap(bytes, 0, bytes.length);
        if (in.getInt() != MAGIC) {
            throw new IOException(file + " is not a desync dump");
        }
        String build = in.getString(Msg.MAX_TEXT);
        int peer = in.getInt();
        String role = in.getString(Msg.MAX_TEXT);
        String config = new String(in.getBytes(Msg.MAX_CONFIG), StandardCharsets.UTF_8);
        String scenario = in.getString(Msg.MAX_TEXT);
        long seed = in.getLong();
        int agreedTick = in.getInt();
        byte[] agreed = in.getBytes(bytes.length);
        int tick = in.getInt();
        byte[] world = in.getBytes(bytes.length);
        long hash = in.getLong();
        int count = in.getInt();
        List<byte[]> bundles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bundles.add(in.getBytes(bytes.length));
        }
        in.expectEnd();
        return new DesyncDump(
                build,
                peer,
                role,
                new SessionSetup(config, scenario, seed),
                agreedTick,
                agreed,
                tick,
                world,
                hash,
                bundles);
    }

    /** The {@code .txt} beside a dump. */
    static Path summaryOf(Path file) {
        String name = file.getFileName().toString();
        String base = name.endsWith(".bin") ? name.substring(0, name.length() - 4) : name;
        return file.resolveSibling(base + ".txt");
    }

    private String summary() {
        return String.format(
                Locale.ROOT,
                "desync dump%nrole=%s peer=%d build=%s%nscenario=\"%s\" seed=%d%ntick=%d hash=%s bytes=%d%n"
                        + "agreedTick=%d agreedBytes=%d bundles=%d%n"
                        + "Read with DesyncReport, beside the other side's dump for the same tick.%n",
                role,
                peer,
                build,
                setup.scenario(),
                setup.seed(),
                tick,
                Long.toHexString(hash),
                world.length,
                agreedTick,
                agreed.length,
                bundles.size());
    }
}
