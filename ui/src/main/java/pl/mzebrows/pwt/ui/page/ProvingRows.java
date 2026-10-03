package pl.mzebrows.pwt.ui.page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.def.WeaponType;
import pl.mzebrows.pwt.sim.scenario.ProvingSetup;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.ui.hud.RankText;

/**
 * The proving ground's choice rows (G0060, {@code UI:main/proving-ground}): what left and right do to a
 * setup, and what each row shows. Pure - the page holds the setup, this changes it.
 *
 * <p>What there is to choose from is read from the numbers rather than listed: the guns are the ones a
 * man carries (a weapon type), a rooted strain's count is one, and a rank goes as high as its upgrade.
 */
public final class ProvingRows {

    /** A row of {@code UI:main/proving-ground} that left and right step. */
    public enum Row {
        STRAIN,
        COUNT,
        GUN,
        SQUAD,
        OTHERS
    }

    /**
     * What a row shows as its value: words by key, or, with no key, the text itself - a number.
     *
     * @param key a key in {@code i18n/ui.properties}, or empty
     * @param text what to show when there is no key
     */
    public record Choice(String key, String text) {

        static Choice word(String key) {
            return new Choice(key, "");
        }

        static Choice number(String text) {
            return new Choice("", text);
        }
    }

    /** The rows in the order the page lists them: a choice entry's index is its place here. */
    public static final List<Row> ROWS = List.of(Row.values());

    /** How many stand on the field; one past the last is a nest. */
    private static final int[] COUNTS = {1, 3, 5, 10};

    private final SimConfig config;
    private final List<WeaponKind> guns = new ArrayList<>();
    private final List<UpgradeKind> upgrades = new ArrayList<>();

    public ProvingRows(SimConfig config) {
        this.config = config;
        for (WeaponKind gun : WeaponKind.values()) {
            if (config.weapon(gun).type() != WeaponType.NONE) {
                guns.add(gun);
            }
        }
        for (Side side : new Side[] {Side.PLAYER, Side.ALIEN}) {
            for (UpgradeKind kind : UpgradeKind.values()) {
                if (config.upgrades().of(kind).side() == side) {
                    upgrades.add(kind);
                }
            }
        }
    }

    /** The setup with one row stepped by {@code direction} (+1 or -1), wrapping round. */
    public ProvingSetup step(ProvingSetup setup, Row row, int direction) {
        return switch (row) {
            case STRAIN -> setup.withStrain(next(UnitKind.values(), setup.strain(), direction));
            case COUNT -> locked(setup, row) ? setup : stepCount(setup, direction);
            case GUN -> setup.withGun(guns.get(wrap(guns.indexOf(setup.gun()) + direction, guns.size())));
            case SQUAD -> setup.withSquad(!setup.squad());
            case OTHERS -> setup.withOthers(next(ProvingSetup.Others.values(), setup.others(), direction));
        };
    }

    /** Whether a row is fixed by something else: the count of a strain that cannot walk is one. */
    public boolean locked(ProvingSetup setup, Row row) {
        return row == Row.COUNT && config.unit(setup.strain()).rooted();
    }

    /** What a row shows for this setup. */
    public Choice value(ProvingSetup setup, Row row) {
        return switch (row) {
            case STRAIN -> Choice.word("ui.strain." + setup.strain());
            case COUNT ->
                locked(setup, row)
                        ? Choice.number("1")
                        : setup.nest()
                                ? Choice.word("ui.provingGround.nest")
                                : Choice.number(Integer.toString(setup.count()));
            case GUN -> Choice.word("ui.weapon." + setup.gun());
            case SQUAD -> Choice.word(setup.squad() ? "ui.provingGround.on" : "ui.provingGround.off");
            case OTHERS -> Choice.word("ui.provingGround.others." + setup.others());
        };
    }

    /** Every upgrade and adaptation there is, the players' first - the rows of the upgrades page. */
    public List<UpgradeKind> upgrades() {
        return upgrades;
    }

    /** Whether this kind is the swarm's - an adaptation, shown as the aside shows one. */
    public boolean swarm(UpgradeKind kind) {
        return config.upgrades().of(kind).side() == Side.ALIEN;
    }

    /** The setup with one kind's rank stepped by {@code direction}, from none to its most and round again. */
    public ProvingSetup stepRank(ProvingSetup setup, UpgradeKind kind, int direction) {
        int most = config.upgrades().of(kind).ranks();
        return setup.withRank(kind, wrap(setup.rankOf(kind) + direction, most + 1));
    }

    /** What an upgrade's row shows: its rank, or none. */
    public Choice rank(ProvingSetup setup, UpgradeKind kind) {
        int rank = setup.rankOf(kind);
        return rank == 0 ? Choice.word("ui.provingGround.rankNone") : Choice.number(RankText.roman(rank));
    }

    /** How many kinds are held at any rank - what the UPGRADES row counts. */
    public int held(ProvingSetup setup) {
        return setup.ranks().size();
    }

    private static ProvingSetup stepCount(ProvingSetup setup, int direction) {
        int at = COUNTS.length;
        for (int i = 0; !setup.nest() && i < COUNTS.length; i++) {
            if (COUNTS[i] == setup.count()) {
                at = i;
            }
        }
        int next = wrap(at + direction, COUNTS.length + 1);
        return next == COUNTS.length
                ? setup.withNest(true)
                : setup.withNest(false).withCount(COUNTS[next]);
    }

    private static <E extends Enum<E>> E next(E[] values, E current, int direction) {
        return values[wrap(Arrays.asList(values).indexOf(current) + direction, values.length)];
    }

    private static int wrap(int index, int size) {
        return Math.floorMod(index, size);
    }
}
