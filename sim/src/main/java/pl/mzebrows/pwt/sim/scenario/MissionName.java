package pl.mzebrows.pwt.sim.scenario;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Strength;
import pl.mzebrows.pwt.sim.def.TierKind;

/**
 * What a session calls the mission it plays (G0065, G0102, G0106): {@code mission hard}, {@code mission hard 4} for a
 * length, and {@code mission hard 4 h150 c200} when the host made the swarm tougher or more numerous - health and count
 * percentages, each named only when it is not a hundred. The one place a mission's name is written and read, so what
 * a host's machine says every other machine understands.
 *
 * @param tier how hard the mission is
 * @param length how many biomes, or {@link MissionScenario#CONFIGURED} for the config's own
 * @param strength the swarm's strength beside the tier
 */
public record MissionName(TierKind tier, int length, Strength strength) {

    private static final String HEALTH = "h";
    private static final String COUNT = "c";

    /** Whether it leaves the length to the config's own: the name then has none. */
    public boolean configured() {
        return length == MissionScenario.CONFIGURED;
    }

    /** The name a mission is called by. */
    public String text() {
        StringBuilder name = new StringBuilder("mission ").append(tier.name().toLowerCase(Locale.ROOT));
        if (length != MissionScenario.CONFIGURED) {
            name.append(' ').append(length);
        }
        if (strength.healthPercent() != Strength.NORMAL.healthPercent()) {
            name.append(' ').append(HEALTH).append(strength.healthPercent());
        }
        if (strength.countPercent() != Strength.NORMAL.countPercent()) {
            name.append(' ').append(COUNT).append(strength.countPercent());
        }
        return name.toString();
    }

    /** The mission a name says, or null if it names none: a registered scenario's, or one with a part it cannot read. */
    public static @Nullable MissionName parse(String name) {
        String[] parts = name.split(" ", -1);
        if (parts.length < 2 || !parts[0].equals("mission")) {
            return null;
        }
        TierKind tier = null;
        for (TierKind each : TierKind.values()) {
            if (each.name().toLowerCase(Locale.ROOT).equals(parts[1])) {
                tier = each;
            }
        }
        if (tier == null) {
            return null;
        }
        int length = MissionScenario.CONFIGURED;
        int health = Strength.NORMAL.healthPercent();
        int count = Strength.NORMAL.countPercent();
        try {
            for (int i = 2; i < parts.length; i++) {
                String part = parts[i];
                if (part.startsWith(HEALTH)) {
                    health = Integer.parseInt(part.substring(HEALTH.length()));
                } else if (part.startsWith(COUNT)) {
                    count = Integer.parseInt(part.substring(COUNT.length()));
                } else {
                    length = Integer.parseInt(part);
                }
            }
            return new MissionName(tier, length, new Strength(health, count));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
