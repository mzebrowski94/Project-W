package pl.mzebrows.pwt.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pl.mzebrows.pwt.sim.upgrade.Effect;
import pl.mzebrows.pwt.sim.upgrade.Target;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeView;
import pl.mzebrows.pwt.ui.hud.RankText;

/**
 * An upgrade's words: its name ({@code ui.upgrade.<KIND>}), and each effect's line generated from its
 * definition ({@code ui.effect.<form>.<stat>}, {@code ui.effect.chance.<chance>}, {@code
 * ui.effect.deal.<cache>}), so tuning a number
 * cannot leave a card lying (pillar 5). Built when a draft changes, never per frame.
 *
 * <p>Every effect template may use {@code {0}} its signed number, {@code {1}} who it reaches ({@code
 * ui.target.<TARGET>}, a prefix, empty for everyone) and {@code {2}} a gun's name.
 */
final class CardText {

    /** Effect lines a card shows at most. */
    static final int EFFECT_LINES = 2;

    private final UiStrings strings;
    private final Map<UpgradeKind, String> names = new EnumMap<>(UpgradeKind.class);
    private final Map<Target, String> targets = new EnumMap<>(Target.class);

    CardText(UiStrings strings) {
        this.strings = strings;
        for (UpgradeKind kind : UpgradeKind.values()) {
            names.put(kind, strings.get("ui.upgrade." + kind.name()));
        }
        for (Target target : Target.values()) {
            targets.put(target, strings.get("ui.target." + target.name()).replace('_', ' '));
        }
    }

    /** Its name alone. */
    String name(UpgradeKind kind) {
        return names.getOrDefault(kind, kind.name());
    }

    /** Its name with a rank: {@code LARGER ARMY II}. */
    String title(UpgradeKind kind, int rank) {
        return name(kind) + " " + RankText.roman(rank);
    }

    /** The line for this kind's effect at this place, or empty past its last. */
    String effect(UpgradeView view, UpgradeKind kind, int index) {
        List<Effect> effects = view.effects(kind);
        return index < effects.size() ? effect(effects.get(index)) : "";
    }

    String effect(Effect effect) {
        return switch (effect) {
            case Effect.Scale scale ->
                strings.format(
                        "ui.effect.scale." + scale.stat().name(), signed(scale.percent()), who(scale.target()), "");
            case Effect.Count count ->
                strings.format(
                        "ui.effect.count." + count.stat().name(), signed(count.amount()), who(count.target()), "");
            case Effect.Chance chance ->
                strings.format(
                        "ui.effect.chance." + chance.kind().name(),
                        signed(chance.perSecond()),
                        who(chance.target()),
                        "");
            case Effect.Gun gun ->
                strings.format(
                        "ui.effect.gun." + gun.stat().name(),
                        signed(gun.percent()),
                        who(gun.target()),
                        strings.get("ui.weapon." + gun.gun().name()));
            case Effect.Deal deal ->
                strings.format("ui.effect.deal." + deal.card().name(), signed(deal.amount()), "", "");
        };
    }

    private String who(Target target) {
        String word = targets.getOrDefault(target, "");
        return word.isBlank() ? "" : word.strip() + " ";
    }

    static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    /** A chance a second, to two places at most: {@code +0.2}, {@code +0.35}. */
    static String signed(float value) {
        String text = String.format(Locale.ROOT, "%.2f", Math.abs(value));
        text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return (value < 0f ? "-" : "+") + text;
    }
}
