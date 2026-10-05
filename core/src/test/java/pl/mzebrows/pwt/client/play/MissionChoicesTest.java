package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.ui.nav.CodePicker;

/** The DAILY MISSIONS page's choices (missions ticket 08, G0065): which code each of its entries starts. */
class MissionChoicesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    private final MovableClock clock = new MovableClock(DAY.atTime(23, 59).toInstant(ZoneOffset.UTC));
    private long drawn = 1_000L;
    private final MissionChoices choices = new MissionChoices(clock, () -> drawn += 7_919L);

    @Test
    void offersTheThreeDailiesOfTheDayThePageOpenedOn() {
        choices.open();

        assertThat(choices.day()).isEqualTo(DAY);
        for (TierKind tier : TierKind.values()) {
            assertThat(choices.daily(tier)).isEqualTo(MissionCode.daily(DAY.toEpochDay(), tier));
        }
    }

    /** A daily keeps the day it was started on: midnight passing under an open page changes nothing. */
    @Test
    void keepsItsDayUntilThePageOpensAgain() {
        choices.open();
        MissionCode before = choices.daily(TierKind.HARD);

        clock.at = clock.at.plusSeconds(120);

        assertThat(choices.daily(TierKind.HARD)).isEqualTo(before);
        choices.open();
        assertThat(choices.day()).isEqualTo(DAY.plusDays(1));
    }

    @Test
    void drawsAFreshMissionOnTheChosenTierForEveryRandom() {
        choices.open();
        choices.stepRandomTier(1);

        MissionCode first = choices.random();
        MissionCode second = choices.random();

        assertThat(first.tier()).isEqualTo(TierKind.HARD);
        assertThat(second.tier()).isEqualTo(TierKind.HARD);
        assertThat(first.seed()).isNotEqualTo(second.seed());
    }

    @Test
    void startsRandomOnMediumAndStepsItRoundTheTiers() {
        assertThat(choices.randomTier()).isEqualTo(TierKind.MEDIUM);

        choices.stepRandomTier(-1);
        assertThat(choices.randomTier()).isEqualTo(TierKind.EASY);
        choices.stepRandomTier(-1);
        assertThat(choices.randomTier()).isEqualTo(TierKind.HARD);
    }

    @Test
    void startsWhatIsTypedHoweverItIsTyped() {
        MissionCode code = new MissionCode(TierKind.EASY, 123_456L);

        type(" " + code.toString().toLowerCase(Locale.ROOT) + " ");

        assertThat(choices.choose()).isEqualTo(code);
        assertThat(choices.problem()).isEmpty();
    }

    /** A paste is a whole code: it takes the line's place rather than adding to what is there. */
    @Test
    void startsAPastedCodeInPlaceOfWhatWasTyped() {
        MissionCode code = new MissionCode(TierKind.HARD, 99L);
        type("ea");

        choices.paste(" " + code + " ");

        assertThat(choices.choose()).isEqualTo(code);
    }

    @Test
    void refusesAPasteThatStartsWithNoTiersLetterAndKeepsTheLine() {
        type("ea");

        choices.paste("code " + new MissionCode(TierKind.HARD, 99L));

        assertThat(choices.typed()).isEqualTo("EA");
        assertThat(choices.problem()).isEqualTo(MissionChoices.FIRST_LETTER);
    }

    /** The line shows a code's shape from the first key: two halves of four, the dash its own. */
    @Test
    void showsTheLineAsTwoHalvesOfFourFromTheStart() {
        assertThat(choices.line()).isEqualTo("____-____");

        type("mjgf-6");

        assertThat(choices.line()).isEqualTo("MJGF-6___");
        assertThat(choices.typed()).isEqualTo("MJGF6");
    }

    @Test
    void takesNoMoreThanACodesEightCharacters() {
        type("MJGF66MQXX");

        assertThat(choices.typed()).isEqualTo("MJGF66MQ");
    }

    /**
     * Only a tier's letter starts a code: any other is refused at once and the line says which ones do. That
     * names the tiers, never the check, so it lets no typo through (G0065).
     */
    @Test
    void refusesAFirstLetterNoTierHasAndSaysWhichDo() {
        assertThat(choices.type('x')).isTrue();

        assertThat(choices.typed()).isEmpty();
        assertThat(choices.problem()).isEqualTo(MissionChoices.FIRST_LETTER);
        choices.type('h');
        assertThat(choices.typed()).isEqualTo("H");
        assertThat(choices.problem()).isEmpty();
        choices.type('x');
        assertThat(choices.typed()).isEqualTo("HX");
    }

    /** A typo starts nothing and the words never say which character is wrong (G0065). */
    @Test
    void refusesATypoInTheSameWordsWhereverItIs() {
        String text = new MissionCode(TierKind.MEDIUM, 31_337L).text();
        for (int at = 0; at < text.length(); at++) {
            char wrong = RoomCode.ALPHABET.charAt(
                    (RoomCode.ALPHABET.indexOf(text.charAt(at)) + 1) % RoomCode.ALPHABET.length());
            choices.replaceTyped(text.substring(0, at) + wrong + text.substring(at + 1));

            assertThat(choices.choose()).isNull();
            assertThat(choices.problem()).isEqualTo(MissionChoices.REFUSED);
        }
    }

    /**
     * Enter arrives as a typed character too, and a space and a dash are the line's own to place - so none is
     * for the line; only what is may pull the selection to it.
     */
    @Test
    void tellsWhichKeysAreForTheLine() {
        assertThat(choices.type('\n')).isFalse();
        assertThat(choices.type('\r')).isFalse();
        assertThat(choices.type(' ')).isFalse();
        assertThat(choices.type('-')).isFalse();
        assertThat(choices.typed()).isEmpty();

        assertThat(choices.type('h')).isTrue();
        assertThat(choices.type('\b')).isTrue();
        assertThat(choices.type('\b')).isFalse();
    }

    /** The refusal stays only until the player types again: it is about what he typed, not about him. */
    @Test
    void forgetsTheRefusalOnTheNextKey() {
        String text = new MissionCode(TierKind.MEDIUM, 31_337L).text();
        String typo = text.substring(0, MissionCode.LENGTH - 1) + (text.endsWith("A") ? "C" : "A");
        type(typo);
        choices.choose();

        choices.type('\b');

        assertThat(choices.problem()).isEmpty();
        assertThat(choices.typed()).isEqualTo(text.substring(0, MissionCode.LENGTH - 1));
    }

    /** An empty line is no typo: the screen opens the picker there, and nothing is said. */
    @Test
    void startsNothingAndSaysNothingForAnEmptyLine() {
        type("  ");

        assertThat(choices.choose()).isNull();
        assertThat(choices.problem()).isEmpty();
    }

    @Test
    void opensThePickerOnTodaysMediumDailyThenOnTheLastMissionPlayed() {
        choices.open();
        assertThat(choices.openPicker().code())
                .isEqualTo(choices.daily(TierKind.MEDIUM).text());

        MissionCode played = new MissionCode(TierKind.HARD, 5L);
        choices.played(played);

        assertThat(choices.openPicker().code()).isEqualTo(played.text());
    }

    /** The picker's first slot holds only the tiers' letters: every turn of it is a tier. */
    @Test
    void turnsThePickersFirstSlotThroughTheTiersOnly() {
        choices.open();
        CodePicker picker = choices.openPicker();

        for (int turn = 0; turn < 2 * TierKind.values().length; turn++) {
            picker.up();

            assertThat(MissionCode.tierOf(picker.code().charAt(0))).isNotNull();
        }
    }

    /** However a code arrived, it is a daily when it is one of the three of the page's day. */
    @Test
    void callsACodeADailyWhenItIsOneOfTheDaysThree() {
        choices.open();
        MissionCode daily = MissionCode.parse(choices.daily(TierKind.EASY).toString());

        assertThat(daily).isNotNull();
        assertThat(choices.dailyDay(daily)).isEqualTo(DAY);
        assertThat(choices.dailyDay(new MissionCode(TierKind.EASY, 7L))).isNull();
    }

    private void type(String text) {
        for (int i = 0; i < text.length(); i++) {
            choices.type(text.charAt(i));
        }
    }

    /** A UTC clock the test moves on. */
    private static final class MovableClock extends Clock {

        Instant at;

        MovableClock(Instant at) {
            this.at = at;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return at;
        }
    }
}
