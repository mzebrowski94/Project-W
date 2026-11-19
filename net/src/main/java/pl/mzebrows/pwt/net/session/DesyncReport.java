package pl.mzebrows.pwt.net.session;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.WorldCodec;

/**
 * Reads the two dumps of one desync - the host's and the peer's that disagreed with it - and says what
 * can be said from here (ADR 0012):
 *
 * <ul>
 *   <li>which fields differ between the two worlds at the disputed tick, first ones first;
 *   <li>which side a replay on this machine lands on, from the last agreed keyframe through the same
 *       bundles. Landing on the host's means the peer computed differently from the same start - another
 *       machine's arithmetic, or state it carried that a snapshot does not. Landing on the peer's means the
 *       host held something the keyframe did not carry, and so did not reach the peer either;
 *   <li>whether that interval hides state: the same replay again, but through a snapshot round trip every
 *       tick. The first tick the two part is where a {@code @NotState} cache or a system field stopped
 *       being a pure function of the world (ADR 0014).
 * </ul>
 */
public final class DesyncReport {

    private static final int MOST_DIFFERENCES = 30;

    private DesyncReport() {}

    public static String compare(Path reference, Path desynced, WorldFactory worlds) throws IOException {
        return compare(DesyncDump.read(reference), DesyncDump.read(desynced), worlds);
    }

    public static String compare(DesyncDump reference, DesyncDump desynced, WorldFactory worlds) {
        StringBuilder report = new StringBuilder()
                .append("desync at tick ")
                .append(desynced.tick())
                .append(": reference peer ")
                .append(reference.peer())
                .append(" hash ")
                .append(Long.toHexString(reference.hash()))
                .append(", desynced peer ")
                .append(desynced.peer())
                .append(" hash ")
                .append(Long.toHexString(desynced.hash()))
                .append('\n');
        if (reference.tick() != desynced.tick()) {
            return report.append("the dumps are of different ticks: ")
                    .append(reference.tick())
                    .append(" and ")
                    .append(desynced.tick())
                    .append('\n')
                    .toString();
        }
        List<String> differences = WorldCodec.differences(reference.world(), desynced.world(), MOST_DIFFERENCES);
        report.append("fields that differ (reference vs desynced), first ")
                .append(differences.size())
                .append(":\n");
        for (String difference : differences) {
            report.append("  ").append(difference).append('\n');
        }
        if (reference.agreedTick() < 0 || reference.bundles().isEmpty()) {
            return report.append("no agreed keyframe with its bundles was kept, so nothing to replay\n")
                    .toString();
        }
        if (reference.agreedTick() != desynced.agreedTick() || !Arrays.equals(reference.agreed(), desynced.agreed())) {
            report.append("the two sides' agreed keyframes are not the same bytes - the hashes that agreed collided,"
                    + " or a dump is from another session\n");
        }
        long[] straight = replay(reference, worlds, false);
        long landed = straight[straight.length - 1];
        report.append("replaying ticks ")
                .append(reference.agreedTick() + 1)
                .append("..")
                .append(reference.tick())
                .append(" here gives ")
                .append(Long.toHexString(landed))
                .append(": ");
        if (landed == reference.hash()) {
            report.append("the reference's world - the desynced peer computed differently from the same start\n");
        } else if (landed == desynced.hash()) {
            report.append("the desynced peer's world - the reference held something its keyframe did not carry\n");
        } else {
            report.append("neither side's world\n");
        }
        long[] roundTripped = replay(reference, worlds, true);
        for (int i = 0; i < straight.length; i++) {
            if (straight[i] != roundTripped[i]) {
                return report.append("hidden state: a replay through a snapshot every tick parts from a straight one at"
                                + " tick ")
                        .append(reference.agreedTick() + 1 + i)
                        .append(" - some state there is not in the snapshot (ADR 0014)\n")
                        .toString();
            }
        }
        return report.append("no hidden state: a replay through a snapshot every tick stays with a straight one\n")
                .toString();
    }

    /** The world's hash after each bundle, from the agreed keyframe - optionally rebuilt from a snapshot each tick. */
    private static long[] replay(DesyncDump dump, WorldFactory worlds, boolean roundTripEveryTick) {
        Simulation world = worlds.blank(dump.setup());
        WorldCodec.restore(world, dump.agreed());
        List<byte[]> bundles = dump.bundles();
        long[] hashes = new long[bundles.size()];
        Bundle bundle = new Bundle(world.capacity());
        InputCodec codec = new InputCodec(world.capacity());
        PlayerInputs inputs = new PlayerInputs(world.capacity());
        WireReader in = new WireReader();
        for (int i = 0; i < bundles.size(); i++) {
            byte[] bytes = bundles.get(i);
            bundle.read(in.wrap(bytes, 0, bytes.length));
            if (bundle.restart()) {
                world = worlds.fresh(dump.setup());
            }
            codec.applyTo(bundle, inputs);
            world.step(inputs);
            if (roundTripEveryTick) {
                Simulation rebuilt = worlds.blank(dump.setup());
                WorldCodec.restore(rebuilt, WorldCodec.snapshot(world));
                world = rebuilt;
            }
            hashes[i] = WorldCodec.hash(world);
        }
        return hashes;
    }
}
