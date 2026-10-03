package pl.mzebrows.pwt.sim.scenario;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lombok.With;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * One configuration of the proving ground (G0060): what the player chose to fight, and with what. A
 * value, so the menu can hold it for the run and a restart builds the same world from it.
 *
 * <p>Nothing here is checked against the numbers: a rooted strain is one whatever {@code count} says,
 * and a rank above an upgrade's most is its most. The world decides, so the setup never has to know
 * which strain is which.
 *
 * @param strain what he fights
 * @param count how many of it stand on the field from the first tick; ignored for a nest
 * @param nest whether it comes out of a nest instead
 * @param gun what he spawns and comes back with
 * @param squad whether his squad comes too
 * @param others what else stands on his side of the field
 * @param ranks how many ranks of each upgrade and adaptation are held from the first tick; a kind not
 *     named holds none
 */
@With
public record ProvingSetup(
        UnitKind strain,
        int count,
        boolean nest,
        WeaponKind gun,
        boolean squad,
        Others others,
        Map<UpgradeKind, Integer> ranks) {

    /** What else stands on his side of the field, a few of one kind. */
    public enum Others {
        NONE,
        TURRETS,
        MINES,
        BARRELS
    }

    public ProvingSetup {
        if (count < 1) {
            throw new IllegalArgumentException("a proving ground fights at least one, not " + count);
        }
        EnumMap<UpgradeKind, Integer> copy = new EnumMap<>(UpgradeKind.class);
        for (Map.Entry<UpgradeKind, Integer> entry : ranks.entrySet()) {
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException(entry.getKey() + " at rank " + entry.getValue());
            }
            if (entry.getValue() > 0) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        // An EnumMap iterates in declaration order, so the ledger is filled the same way every time.
        ranks = Collections.unmodifiableMap(copy);
    }

    /** What the page shows on a first visit: three grunts, the sidearm, alone, nothing held. */
    public static ProvingSetup first() {
        return new ProvingSetup(UnitKind.GRUNT, 3, false, WeaponKind.SIDEARM, false, Others.NONE, Map.of());
    }

    /** How many ranks of this kind are held from the first tick. */
    public int rankOf(UpgradeKind kind) {
        return ranks.getOrDefault(kind, 0);
    }

    /** This setup with one kind held at this rank; zero holds none. */
    public ProvingSetup withRank(UpgradeKind kind, int rank) {
        EnumMap<UpgradeKind, Integer> changed = new EnumMap<>(UpgradeKind.class);
        changed.putAll(ranks);
        changed.put(kind, rank);
        return withRanks(changed);
    }
}
