package pl.mzebrows.pwt.client;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.client.input.PadPolicy;
import pl.mzebrows.pwt.client.play.MissionCode;

/**
 * What the game was started with, from the command line.
 *
 * <ul>
 *   <li>{@code --instance=N} - which copy of the game this is, when more than one runs on a PC: its
 *       window's title and place, its connection log's name, and which pads it reads. Zero when absent.
 *   <li>{@code --pads=all|none|0,2} - see {@link PadPolicy}.
 *   <li>{@code --mission=<code>} - a mission, by its code (G0065). A code that fails its check is refused.
 * </ul>
 *
 * <p>For a developer's PC; a player needs none of them. Something this does not understand is reported in
 * {@link #problems()} and otherwise ignored, so a typo does not stop the game.
 *
 * @param instance which copy this is
 * @param instanceGiven whether {@code --instance} was given; only then are windows placed side by side
 * @param mission the mission {@code --mission} named, or null for none
 */
public record LaunchOptions(
        int instance,
        boolean instanceGiven,
        PadPolicy pads,
        @Nullable MissionCode mission,
        List<String> problems) {

    public LaunchOptions {
        problems = List.copyOf(problems);
    }

    /** A copy started with nothing on its command line. */
    public static LaunchOptions none() {
        return parse();
    }

    public static LaunchOptions parse(String... args) {
        int instance = 0;
        boolean instanceGiven = false;
        PadPolicy pads = null;
        MissionCode mission = null;
        List<String> problems = new ArrayList<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            String name = equals < 0 ? arg : arg.substring(0, equals);
            String value = equals < 0 ? "" : arg.substring(equals + 1);
            try {
                switch (name) {
                    case "--instance" -> {
                        instance = Integer.parseInt(value.trim());
                        if (instance < 0 || instance > 9) {
                            throw new IllegalArgumentException("an instance is 0 to 9");
                        }
                        instanceGiven = true;
                    }
                    case "--pads" -> pads = PadPolicy.parse(value);
                    case "--mission" -> {
                        mission = MissionCode.parse(value);
                        if (mission == null) {
                            throw new IllegalArgumentException("not a mission code");
                        }
                    }
                    default -> problems.add("ignored " + arg + ": not an option this game has");
                }
            } catch (IllegalArgumentException e) {
                problems.add("ignored " + arg + ": " + e.getMessage());
            }
        }
        return new LaunchOptions(
                instance, instanceGiven, pads == null ? PadPolicy.defaultFor(instance) : pads, mission, problems);
    }

    /** What this copy's connection log is called. */
    public String logName() {
        return "instance" + instance;
    }
}
