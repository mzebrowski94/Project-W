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
import pl.mzebrows.pwt.sim.Strength;
import pl.mzebrows.pwt.sim.def.TierKind;
import pl.mzebrows.pwt.ui.nav.CodePicker;

/**
 * The DAILY MISSIONS page's choices (missions ticket 08, G0065): what each entry writes into the code line, and
 * what START MISSION would start from it.
 */
class MissionChoicesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    /** A code, as text, and the same with its check character wrong. */
    private static final String TEXT = new MissionCode(TierKind.MEDIUM, 6, 31_337L).text();

    private static final String TYPO = TEXT.substring(0, MissionCode.LENGTH - 1) + (TEXT.endsWith("A") ? "C" : "A");

    private final MovableClock clock = new MovableClock(DAY.atTime(23, 59).toInstant(ZoneOffset.UTC));
    private long drawn = 1_000L;
    private final MissionChoices choices = new MissionChoices(
            clock,
            () -> drawn += 7_919L,
            pl.mzebrows.pwt.sim.SimConfig.defaults().mission());

    @Test
    void stepsTheStrengthRowsRoundTheirRangeAndLeavesTheCodeAlone() {
        choices.open();
        choices.drawRandom();
        choices.chooseCustom();
        String line = choices.typed();

        assertThat(choices.strength()).isEqualTo(Strength.NORMAL);
        choices.stepHealth(1);
        choices.stepCount(-1);
        assertThat(choices.strength()).isEqualTo(new Strength(150, 50));
        choices.stepCount(-1);
        assertThat(choices.strength().countPercent()).isEqualTo(300);
        for (int i = 0; i < 6; i++) {
            choices.stepHealth(1);
        }
        assertThat(choices.strength().healthPercent()).isEqualTo(150);
        assertThat(choices.typed()).isEqualTo(line);
    }

    @Test
    void aDailyIsAlwaysAtANormalStrengthAndItsNoteSaysNothingMore() {
        choices.open();
        choices.chooseCustom();
        choices.stepHealth(1);
        choices.chooseDaily(TierKind.EASY);

        assertThat(choices.custom()).isFalse();
        assertThat(choices.strength()).isEqualTo(Strength.NORMAL);
        assertThat(choices.note()).isEqualTo(choices.line());
    }

    @Test
    void theNoteCarriesAStrengthThatIsNotNormalForTheJoinersToReadBack() {
        choices.open();
        choices.chooseDaily(TierKind.HARD);
        choices.chooseCustom();
        choices.stepHealth(1);
        choices.stepCount(1);

        assertThat(choices.note()).isEqualTo(choices.line() + " h150 c150");
        assertThat(MissionCode.strengthOf("mission easy h150 c150")).isEqualTo(new Strength(150, 150));
        assertThat(MissionCode.strengthOf("mission hard 4")).isEqualTo(Strength.NORMAL);
        assertThat(MissionCode.strengthOf("crowd")).isEqualTo(Strength.NORMAL);
    }

    @Test
    void aMissionCodeIsReadFromANameThatCarriesAStrength() {
        MissionCode code = MissionCode.of("mission hard 4 h150 c200", 5L, 6);

        assertThat(code).isEqualTo(new MissionCode(TierKind.HARD, 4, 5L));
        assertThat(code.scenario(new Strength(150, 200)).name()).isEqualTo("mission hard 4 h150 c200");
    }

    @Test
    void offersTheThreeDailiesOfTheDayThePageOpenedOn() {
        choices.open();

        assertThat(choices.day()).isEqualTo(DAY);
        for (TierKind tier : TierKind.values()) {
            assertThat(choices.daily(tier)).isEqualTo(MissionCode.daily(DAY.toEpochDay(), tier, 6));
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

    /** The row starts at the default and a daily or a RANDOM puts custom, and the length, back to it (G0102, G0106). */
    @Test
    void putsTheLengthBackWhenADailyOrARandomIsChosen() {
        choices.open();
        assertThat(choices.length()).isEqualTo(6);

        choices.chooseCustom();
        choices.stepLength(1);
        choices.stepLength(1);
        assertThat(choices.length()).isEqualTo(8);
        choices.drawRandom();
        assertThat(choices.custom()).isFalse();
        assertThat(choices.code().length()).isEqualTo(6);
        assertThat(choices.origin()).isEqualTo(MissionChoices.Origin.RANDOM);

        choices.chooseCustom();
        choices.stepLength(1);
        choices.chooseDaily(TierKind.HARD);
        assertThat(choices.length()).isEqualTo(6);
        assertThat(choices.code()).isEqualTo(choices.daily(TierKind.HARD));
    }

    @Test
    void changesNeitherLengthNorStrengthUntilCustomIsOn() {
        choices.open();
        choices.chooseDaily(TierKind.MEDIUM);

        choices.stepLength(1);
        choices.stepHealth(1);
        choices.stepCount(1);

        assertThat(choices.length()).isEqualTo(6);
        assertThat(choices.strength()).isEqualTo(Strength.NORMAL);
    }

    @Test
    void showsTheTiersOwnPercentagesAndCustomStartsFromThem() {
        choices.open();

        choices.chooseDaily(TierKind.EASY);
        assertThat(choices.shownHealth()).isEqualTo(85);
        assertThat(choices.shownCount()).isEqualTo(75);
        choices.chooseDaily(TierKind.MEDIUM);
        assertThat(choices.shownHealth()).isEqualTo(100);
        choices.chooseDaily(TierKind.HARD);
        assertThat(choices.shownHealth()).isEqualTo(115);
        assertThat(choices.shownCount()).isEqualTo(125);

        MissionCode hard = choices.code();
        choices.chooseCustom();
        assertThat(choices.code()).isEqualTo(hard);
        choices.stepHealth(1);
        assertThat(choices.shownHealth()).isEqualTo(173);
    }

    @Test
    void customOfARandomKeepsItsCodeAndAnEmptyLineDrawsOne() {
        choices.open();
        choices.drawRandom();
        MissionCode drawn = choices.code();

        choices.chooseCustom();
        assertThat(choices.code()).isEqualTo(drawn);

        choices.open();
        choices.chooseCustom();
        assertThat(choices.code()).isNotNull();
        assertThat(choices.custom()).isTrue();
    }

    /** Changing the length with a daily in the line is another mission - a custom one - at the same seed. */
    @Test
    void makesACustomMissionOfADailyWhoseLengthIsChanged() {
        choices.open();
        choices.chooseDaily(TierKind.EASY);
        MissionCode daily = choices.code();

        choices.chooseCustom();
        choices.stepLength(-1);

        assertThat(choices.code().length()).isEqualTo(5);
        assertThat(choices.code().seed()).isEqualTo(daily.seed());
        assertThat(choices.code().tier()).isEqualTo(daily.tier());
        assertThat(choices.origin()).isEqualTo(MissionChoices.Origin.CUSTOM);
    }

    @Test
    void stepsTheLengthRoundBetweenItsLimitsAndFollowsACodeTyped() {
        var lengths = pl.mzebrows.pwt.sim.SimConfig.defaults().mission();
        choices.open();
        choices.chooseCustom();

        for (int i = 0; i < lengths.biomesMax() - lengths.biomesMin() + 1; i++) {
            choices.stepLength(1);
        }
        assertThat(choices.length()).isEqualTo(6);
        choices.stepLength(-1);
        choices.stepLength(-1);
        choices.stepLength(-1);
        assertThat(choices.length()).isEqualTo(lengths.biomesMin());
        choices.stepLength(-1);
        assertThat(choices.length()).isEqualTo(lengths.biomesMax());
        choices.replaceTyped(new MissionCode(TierKind.HARD, 4, 5L).text());
        assertThat(choices.length()).isEqualTo(4);
    }

    /** A code that checks out but is a length no mission may be is refused like any other (G0102). */
    @Test
    void refusesACodeOfALengthNoMissionMayBe() {
        choices.open();

        choices.replaceTyped(new MissionCode(TierKind.HARD, 2, 5L).text());

        assertThat(choices.code()).isNull();
        assertThat(choices.problem()).isEqualTo(MissionChoices.REFUSED);
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
        MissionCode code = new MissionCode(TierKind.EASY, 6, 123_456L);

        type(" " + code.toString().toLowerCase(Locale.ROOT) + " ");

        assertThat(choices.code()).isEqualTo(code);
        assertThat(choices.problem()).isEmpty();
    }

    /** A paste is a whole code: it takes the line's place rather than adding to what is there. */
    @Test
    void startsAPastedCodeInPlaceOfWhatWasTyped() {
        MissionCode code = new MissionCode(TierKind.HARD, 6, 99L);
        type("ea");

        choices.paste(" " + code + " ");

        assertThat(choices.code()).isEqualTo(code);
    }

    @Test
    void refusesAPasteThatStartsWithNoTiersLetterAndKeepsTheLine() {
        type("ea");

        choices.paste("code " + new MissionCode(TierKind.HARD, 6, 99L));

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
        String text = TEXT;
        for (int at = 0; at < text.length(); at++) {
            char wrong = RoomCode.ALPHABET.charAt(
                    (RoomCode.ALPHABET.indexOf(text.charAt(at)) + 1) % RoomCode.ALPHABET.length());
            choices.replaceTyped(text.substring(0, at) + wrong + text.substring(at + 1));

            assertThat(choices.code()).isNull();
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
        type(TYPO);

        choices.type('\b');

        assertThat(choices.problem()).isEmpty();
        assertThat(choices.typed()).isEqualTo(TEXT.substring(0, MissionCode.LENGTH - 1));
    }

    /** An empty line is no typo, nor is one still being typed: nothing is said. */
    @Test
    void startsNothingAndSaysNothingForAnEmptyLine() {
        type("  ");

        assertThat(choices.code()).isNull();
        assertThat(choices.problem()).isEmpty();
    }

    @Test
    void opensThePickerOnTodaysMediumDailyThenOnTheLastMissionPlayed() {
        choices.open();
        assertThat(choices.openPicker().code())
                .isEqualTo(choices.daily(TierKind.MEDIUM).text());

        MissionCode played = new MissionCode(TierKind.HARD, 6, 5L);
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
        assertThat(choices.dailyDay(new MissionCode(TierKind.EASY, 6, 7L))).isNull();
    }

    /** Choosing a daily puts its code in the line, to be started - or changed - from there. */
    @Test
    void writesADailysCodeIntoTheLineWhenItIsChosen() {
        choices.open();

        choices.chooseDaily(TierKind.HARD);

        assertThat(choices.typed()).isEqualTo(choices.daily(TierKind.HARD).text());
        assertThat(choices.code()).isEqualTo(choices.daily(TierKind.HARD));
        assertThat(choices.origin()).isEqualTo(MissionChoices.Origin.DAILY);
    }

    /** RANDOM confirmed again draws again: the line holds a fresh code on RANDOM's tier each time. */
    @Test
    void writesAFreshRandomIntoTheLineEachTimeItIsDrawn() {
        choices.open();
        choices.stepRandomTier(-1);

        choices.drawRandom();
        MissionCode first = choices.code();
        choices.drawRandom();
        MissionCode second = choices.code();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.tier()).isEqualTo(TierKind.EASY);
        assertThat(second).isNotEqualTo(first);
        assertThat(choices.origin()).isEqualTo(MissionChoices.Origin.RANDOM);
    }

    /**
     * A code changed by hand is a custom mission - unless it lands on one of the day's dailies, which it is
     * however it arrived (G0065).
     */
    @Test
    void callsACodeChangedByHandCustomUnlessItIsADaily() {
        choices.open();
        choices.drawRandom();

        choices.replaceTyped(new MissionCode(TierKind.EASY, 6, 7L).text());
        assertThat(choices.origin()).isEqualTo(MissionChoices.Origin.CUSTOM);

        choices.replaceTyped("");
        type(choices.daily(TierKind.EASY).toString());
        assertThat(choices.origin()).isEqualTo(MissionChoices.Origin.DAILY);
    }

    /** A line that names no mission has no origin to show. */
    @Test
    void namesNoOriginForALineThatIsNoCode() {
        choices.open();
        type("MJG");

        assertThat(choices.code()).isNull();
        assertThat(choices.origin()).isNull();
    }

    /** The refusal comes the moment the eighth character lands - not on START, and not before it. */
    @Test
    void refusesATypoTheMomentItsLastCharacterLands() {

        type(TYPO.substring(0, MissionCode.LENGTH - 1));
        assertThat(choices.problem()).isEmpty();
        choices.type(TYPO.charAt(MissionCode.LENGTH - 1));

        assertThat(choices.problem()).isEqualTo(MissionChoices.REFUSED);
    }

    /** A whole typo pasted or picked in is refused as it lands, as a typed one is. */
    @Test
    void refusesAPastedOrPickedTypoAsItLands() {

        choices.paste(TYPO);
        assertThat(choices.problem()).isEqualTo(MissionChoices.REFUSED);

        choices.replaceTyped(TEXT);
        assertThat(choices.problem()).isEmpty();
        choices.replaceTyped(TYPO);
        assertThat(choices.problem()).isEqualTo(MissionChoices.REFUSED);
    }

    /** Opened from the menu nothing is chosen; back from a level, the mission just played is in the line. */
    @Test
    void opensEmptyFromTheMenuAndOnTheMissionPlayedComingBackFromIt() {
        MissionCode played = new MissionCode(TierKind.HARD, 6, 5L);
        choices.played(played);

        choices.open();
        assertThat(choices.typed()).isEmpty();

        choices.reopen();
        assertThat(choices.code()).isEqualTo(played);
        assertThat(choices.origin()).isEqualTo(MissionChoices.Origin.CUSTOM);
    }

    /** RANDOM's draw played and come back to is still RANDOM: the line is what was drawn. */
    @Test
    void callsTheRandomPlayedRandomComingBackFromIt() {
        choices.open();
        choices.drawRandom();
        MissionCode drawnCode = choices.code();
        assertThat(drawnCode).isNotNull();
        choices.played(drawnCode);

        choices.reopen();

        assertThat(choices.origin()).isEqualTo(MissionChoices.Origin.RANDOM);
    }

    /** The picker opens on what the line holds once it is whole, so changing a chosen code is a few presses. */
    @Test
    void opensThePickerOnTheLinesCodeOnceItIsWhole() {
        choices.open();
        choices.chooseDaily(TierKind.EASY);

        assertThat(choices.openPicker().code())
                .isEqualTo(choices.daily(TierKind.EASY).text());
    }

    /** A key typed over a whole line starts a new code: the old one is replaced, not silently kept. */
    @Test
    void startsANewCodeWhenAKeyIsTypedOverAWholeLine() {
        choices.open();
        choices.chooseDaily(TierKind.EASY);

        choices.type('h');

        assertThat(choices.typed()).isEqualTo("H");
    }

    /** Over a whole line, a first letter no tier has is refused as it is anywhere, and the line kept. */
    @Test
    void keepsAWholeLineWhenTheKeyTypedOverItStartsNoCode() {
        choices.open();
        choices.chooseDaily(TierKind.EASY);

        choices.type('x');

        assertThat(choices.code()).isEqualTo(choices.daily(TierKind.EASY));
        assertThat(choices.problem()).isEqualTo(MissionChoices.FIRST_LETTER);
    }

    /** The picker opens on what was typed so far, the places still empty at their alphabet's first. */
    @Test
    void opensThePickerOnAPartlyTypedLine() {
        choices.open();
        type("hk3");

        String shown = choices.openPicker().code();

        assertThat(shown).startsWith("HK3").hasSize(MissionCode.LENGTH);
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
