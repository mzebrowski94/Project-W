package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.sim.def.TierKind;

/** A mission is named by its code: a tier's letter, its length, five of seed and one to check the rest (G0065). */
class MissionCodeTest {

    /** A spread of seeds: both ends, every carry between characters, and a stride through the middle. */
    private static final List<Long> SEEDS = seeds();

    @Test
    void readsBackEveryTierAndSeedItWrites() {
        for (TierKind tier : TierKind.values()) {
            for (long seed : SEEDS) {
                MissionCode code = new MissionCode(tier, 6, seed);

                assertThat(MissionCode.parse(code.text())).isEqualTo(code);
                assertThat(MissionCode.parse(code.toString())).isEqualTo(code);
            }
        }
    }

    /** A session plays a code as its tier's mission, the seed travelling as the session's (G0065). */
    @Test
    void playsAsItsTiersMission() {
        for (TierKind tier : TierKind.values()) {
            assertThat(new MissionCode(tier, 6, 77L).scenario().name())
                    .isEqualTo("mission " + tier.name().toLowerCase(Locale.ROOT) + " 6");
        }
    }

    @Test
    void readsWhatAPlayerTypesHoweverHeTypesIt() {
        MissionCode code = new MissionCode(TierKind.HARD, 6, 123_456L);
        String typed = code.text().toLowerCase(Locale.ROOT);

        assertThat(MissionCode.parse(" " + typed.substring(0, 3) + " - " + typed.substring(3) + " "))
                .isEqualTo(code);
    }

    /** The length is one character after the tier's, so one code names one mission of one length (G0102). */
    @Test
    void holdsItsLengthAndNamesAnotherMissionAtAnother() {
        for (int length = 3; length <= 15; length++) {
            MissionCode code = new MissionCode(TierKind.HARD, length, 12_345L);

            assertThat(MissionCode.parse(code.text())).isEqualTo(code);
            assertThat(MissionCode.parse(code.text()).length()).isEqualTo(length);
            assertThat(code.scenario().name()).isEqualTo("mission hard " + length);
        }
        assertThat(new MissionCode(TierKind.HARD, 4, 12_345L).text())
                .isNotEqualTo(new MissionCode(TierKind.HARD, 9, 12_345L).text());
    }

    /** The alphabet's first character is no length, and a code too long for one is never built. */
    @Test
    void refusesALengthNoCharacterCanHold() {
        String text = new MissionCode(TierKind.HARD, 6, 77L).text();

        assertThat(MissionCode.lengthOf('A')).isZero();
        assertThatThrownBy(() -> new MissionCode(TierKind.HARD, 0, 77L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MissionCode(TierKind.HARD, RoomCode.ALPHABET.length(), 77L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(text).hasSize(MissionCode.LENGTH);
    }

    @Test
    void showsItsEightCharactersInTwoHalves() {
        MissionCode code = new MissionCode(TierKind.EASY, 6, 0L);

        assertThat(code.text()).hasSize(MissionCode.LENGTH);
        assertThat(code.toString()).matches("[" + RoomCode.ALPHABET + "]{4}-[" + RoomCode.ALPHABET + "]{4}");
        assertThat(code.text()).startsWith("E");
    }

    /** Every machine, every build of this code, deals the same three dailies for a date (G0065). */
    @Test
    void dealsTheSameDailyForADate() {
        long day = LocalDate.of(2026, 10, 5).toEpochDay();

        // Pinned: a change here moves every player's daily, and their ticks with it (G0067).
        assertThat(MissionCode.daily(day, TierKind.MEDIUM, 6).toString()).isEqualTo("MHGF-66MN");
    }

    @Test
    void dealsEachTierAndEachDayADifferentDaily() {
        long day = LocalDate.of(2026, 10, 5).toEpochDay();
        Set<Long> seeds = new HashSet<>();

        for (TierKind tier : TierKind.values()) {
            assertThat(MissionCode.daily(day, tier, 6).tier()).isEqualTo(tier);
            assertThat(seeds.add(MissionCode.daily(day, tier, 6).seed()))
                    .as("%s", tier)
                    .isTrue();
            assertThat(seeds.add(MissionCode.daily(day + 1, tier, 6).seed()))
                    .as("%s the next day", tier)
                    .isTrue();
        }
    }

    /** A joiner's day may be a neighbour of the host's: a daily is found on the day either side too. */
    @Test
    void findsTheDayADailyBelongsTo() {
        LocalDate today = LocalDate.of(2026, 10, 5);
        MissionCode yesterdays = MissionCode.daily(today.minusDays(1).toEpochDay(), TierKind.HARD, 6);

        assertThat(MissionCode.daily(today.toEpochDay(), TierKind.HARD, 6).dailyNear(today, 6))
                .isEqualTo(today);
        assertThat(yesterdays.dailyNear(today, 6)).isEqualTo(today.minusDays(1));
        assertThat(yesterdays.dailyNear(today.plusDays(1), 6)).isNull();
        assertThat(new MissionCode(TierKind.HARD, 6, 77L).dailyNear(today, 6)).isNull();
    }

    /** What a session plays is a mission when its scenario is one; the code is then its tier and its seed. */
    @Test
    void namesTheMissionASessionPlays() {
        MissionCode code = new MissionCode(TierKind.EASY, 6, 4_242L);

        assertThat(MissionCode.of(code.scenario().name(), code.seed(), 6)).isEqualTo(code);
        assertThat(MissionCode.of("mission easy", 4_242L, 9)).isEqualTo(new MissionCode(TierKind.EASY, 9, 4_242L));
        assertThat(MissionCode.of("Skirmish", 4_242L, 6)).isNull();
        assertThat(MissionCode.of("mission easy x", 4_242L, 6)).isNull();
    }

    /** A typo is refused, never played as some other mission - whichever character it lands on. */
    @Test
    void refusesEverySingleWrongCharacter() {
        for (TierKind tier : TierKind.values()) {
            for (long seed : SEEDS) {
                String text = new MissionCode(tier, 6, seed).text();
                for (int at = 0; at < MissionCode.LENGTH; at++) {
                    for (char wrong : RoomCode.ALPHABET.toCharArray()) {
                        if (wrong != text.charAt(at)) {
                            String typo = text.substring(0, at) + wrong + text.substring(at + 1);

                            assertThat(MissionCode.parse(typo))
                                    .as("%s typed as %s", text, typo)
                                    .isNull();
                        }
                    }
                }
            }
        }
    }

    @Test
    void refusesEverySwappedNeighbourPair() {
        for (TierKind tier : TierKind.values()) {
            for (long seed : SEEDS) {
                String text = new MissionCode(tier, 6, seed).text();
                for (int at = 0; at + 1 < MissionCode.LENGTH; at++) {
                    if (text.charAt(at) != text.charAt(at + 1)) {
                        String swapped =
                                text.substring(0, at) + text.charAt(at + 1) + text.charAt(at) + text.substring(at + 2);

                        assertThat(MissionCode.parse(swapped))
                                .as("%s typed as %s", text, swapped)
                                .isNull();
                    }
                }
            }
        }
    }

    /** A letter no tier starts with is no mission, whatever the check character says. */
    @Test
    void refusesACodeOfNoTier() {
        String seed = new MissionCode(TierKind.MEDIUM, 6, 4_321L).text().substring(1, MissionCode.LENGTH - 1);
        Set<Character> tiers = new HashSet<>();
        for (TierKind tier : TierKind.values()) {
            tiers.add(new MissionCode(tier, 6, 0L).text().charAt(0));
        }

        for (char letter : RoomCode.ALPHABET.toCharArray()) {
            for (char check : RoomCode.ALPHABET.toCharArray()) {
                if (!tiers.contains(letter)) {
                    assertThat(MissionCode.parse(letter + seed + check)).isNull();
                }
            }
        }
    }

    @Test
    void givesEveryTierALetterOfTheAlphabetThatNoOtherTierHas() {
        Set<Character> letters = new HashSet<>();

        for (TierKind tier : TierKind.values()) {
            char letter = new MissionCode(tier, 6, 0L).text().charAt(0);

            assertThat(RoomCode.ALPHABET).contains(String.valueOf(letter));
            assertThat(letters.add(letter)).as("%s shares its letter", tier).isTrue();
        }
    }

    @Test
    void refusesWhatCannotBeACodeAtAll() {
        String text = new MissionCode(TierKind.HARD, 6, 77L).text();

        assertThat(MissionCode.parse(text.substring(1))).isNull();
        assertThat(MissionCode.parse(text + "A")).isNull();
        assertThat(MissionCode.parse(text.substring(0, 3) + "0" + text.substring(4)))
                .isNull();
        assertThat(MissionCode.parse("")).isNull();
    }

    @Test
    void refusesASeedFiveCharactersCannotHold() {
        assertThatThrownBy(() -> new MissionCode(TierKind.EASY, 6, -1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MissionCode(TierKind.EASY, 6, MissionCode.SEEDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Long> seeds() {
        long base = RoomCode.ALPHABET.length();
        List<Long> seeds = new ArrayList<>(List.of(0L, 1L, MissionCode.SEEDS - 1));
        for (long power = base; power < MissionCode.SEEDS; power *= base) {
            seeds.add(power - 1);
            seeds.add(power);
        }
        for (long seed = 7L; seed < MissionCode.SEEDS; seed += MissionCode.SEEDS / 61) {
            seeds.add(seed);
        }
        return List.copyOf(seeds);
    }
}
