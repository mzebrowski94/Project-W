package pl.mzebrows.pwt.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.Effect;
import pl.mzebrows.pwt.sim.system.upgrade.Stat;
import pl.mzebrows.pwt.sim.system.upgrade.Target;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;
import pl.mzebrows.pwt.ui.hud.DraftLayout;

/**
 * A card says exactly what its definition does (pillar 5): every shipped upgrade has a name and every
 * effect it has a line, and each fits a card on the 640-wide canvas with the rank at its highest - an
 * adaptation's lines too, though it is never dealt on a card.
 */
class CardTextTest {

    private static final CardText TEXT = new CardText(shipped());
    private static final DraftLayout LAYOUT = new DraftLayout(4, 32, 6);

    @Test
    void writesAnEffectsNumberSignedAndSaysWhoItReaches() {
        assertThat(TEXT.effect(new Effect.Count(Stat.MEN_PER_CRATE, Target.SIDE, 1)))
                .isEqualTo("+1 MAN PER CRATE");
        assertThat(TEXT.effect(new Effect.Scale(Stat.FIRE_COOLDOWN, Target.TEAM, -12)))
                .isEqualTo("FIRE COOLDOWN -12%");
        assertThat(TEXT.effect(new Effect.Scale(Stat.GUN_DAMAGE, Target.SQUAD, 15)))
                .isEqualTo("SQUAD DAMAGE +15%");
        assertThat(TEXT.effect(new Effect.Chance(AiChance.AID_A_TROOPER, Target.SQUAD, 0.2f)))
                .isEqualTo("SQUAD HELPS MEN +0.2/S");
        assertThat(TEXT.effect(new Effect.Gun(WeaponKind.FLAMER, Stat.GUN_DAMAGE, Target.TEAM, 25)))
                .isEqualTo("FLAMER DAMAGE +25%");
        assertThat(TEXT.effect(new Effect.Deal(PickupKind.ARMOURY_FLAMER, 2))).isEqualTo("+2 FLAMERS IN CRATES");
        assertThat(TEXT.effect(new Effect.Stock(PickupKind.ARMOURY_FLAMER, 1))).isEqualTo("+1 FLAMER PER CRATE");
    }

    /** Firepower's three heavy guns (G0127): one line naming each, where three would not fit a card. */
    @Test
    void foldsGunEffectsAlikeButForTheGunIntoOneLine() {
        List<Effect> heavy = List.of(
                new Effect.Gun(WeaponKind.FLAMER, Stat.GUN_DAMAGE, Target.TEAM, 15),
                new Effect.Gun(WeaponKind.BAZOOKA, Stat.GUN_DAMAGE, Target.TEAM, 15),
                new Effect.Gun(WeaponKind.DETONATOR, Stat.GUN_DAMAGE, Target.TEAM, 15));

        assertThat(TEXT.lines(heavy)).containsExactly("FLAMER/BAZOOKA/LIMPET DMG +15%");
    }

    @Test
    void keepsGunEffectsThatDifferInTheirNumberOnLinesOfTheirOwn() {
        List<Effect> apart = List.of(
                new Effect.Gun(WeaponKind.FLAMER, Stat.GUN_DAMAGE, Target.TEAM, 15),
                new Effect.Gun(WeaponKind.BAZOOKA, Stat.GUN_DAMAGE, Target.TEAM, 10));

        assertThat(TEXT.lines(apart)).containsExactly("FLAMER DAMAGE +15%", "BAZOOKA DAMAGE +10%");
    }

    @Test
    void namesAnUpgradeWithTheRankItWillHave() {
        assertThat(TEXT.title(UpgradeKind.LARGER_ARMY, 2)).isEqualTo("LARGER ARMY II");
    }

    @Test
    void hasWordsForEveryShippedUpgradeThatFitACard() {
        int columns = LAYOUT.cardColumns(640, SimConfig.defaults().draft().options());

        for (UpgradeKind kind : UpgradeKind.values()) {
            UpgradeDef def = SimConfig.defaults().upgrades().of(kind);
            List<String> lines = new ArrayList<>();
            lines.add(TEXT.title(kind, def.ranks()));
            List<String> effects = TEXT.lines(def.effects());
            lines.addAll(effects);

            if (def.side() == Side.PLAYER) {
                // Only a players' upgrade is dealt on a card; an adaptation is shown by its name and rank.
                assertThat(effects.size()).as("%s effects on a card", kind).isLessThanOrEqualTo(CardText.EFFECT_LINES);
            }
            for (String line : lines) {
                assertThat(line).as(kind.name()).doesNotContain("#ui.").doesNotContain("{");
                assertThat(line.length()).as(line).isLessThanOrEqualTo(columns);
            }
        }
    }

    @Test
    void writesAChanceASecondToTwoPlacesAtMost() {
        assertThat(CardText.signed(0.2f)).isEqualTo("+0.2");
        assertThat(CardText.signed(0.35f)).isEqualTo("+0.35");
        assertThat(CardText.signed(1f)).isEqualTo("+1");
        assertThat(CardText.signed(-3)).isEqualTo("-3");
    }

    private static UiStrings shipped() {
        try {
            return UiStrings.parse(Files.readString(Path.of("../assets/i18n/ui.properties"), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
