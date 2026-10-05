package pl.mzebrows.pwt.client.play;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.net.lan.RoomCode;
import pl.mzebrows.pwt.sim.def.TierKind;

/** A mission is named by its code: a tier's letter, six of seed and one to check the rest (G0065). */
class MissionCodeTest {

    /** A spread of seeds: both ends, every carry between characters, and a stride through the middle. */
    private static final List<Long> SEEDS = seeds();

    @Test
    void readsBackEveryTierAndSeedItWrites() {
        for (TierKind tier : TierKind.values()) {
            for (long seed : SEEDS) {
                MissionCode code = new MissionCode(tier, seed);

                assertThat(MissionCode.parse(code.text())).isEqualTo(code);
                assertThat(MissionCode.parse(code.toString())).isEqualTo(code);
            }
        }
    }

    /** A session plays a code as its tier's mission, the seed travelling as the session's (G0065). */
    @Test
    void playsAsItsTiersMission() {
        for (TierKind tier : TierKind.values()) {
            assertThat(new MissionCode(tier, 77L).scenario().name())
                    .isEqualTo("mission " + tier.name().toLowerCase(Locale.ROOT));
        }
    }

    @Test
    void readsWhatAPlayerTypesHoweverHeTypesIt() {
        MissionCode code = new MissionCode(TierKind.HARD, 123_456L);
        String typed = code.text().toLowerCase(Locale.ROOT);

        assertThat(MissionCode.parse(" " + typed.substring(0, 3) + " - " + typed.substring(3) + " "))
                .isEqualTo(code);
    }

    @Test
    void showsItsEightCharactersInTwoHalves() {
        MissionCode code = new MissionCode(TierKind.EASY, 0L);

        assertThat(code.text()).hasSize(MissionCode.LENGTH);
        assertThat(code.toString()).matches("[" + RoomCode.ALPHABET + "]{4}-[" + RoomCode.ALPHABET + "]{4}");
        assertThat(code.text()).startsWith("E");
    }

    /** A typo is refused, never played as some other mission - whichever character it lands on. */
    @Test
    void refusesEverySingleWrongCharacter() {
        for (TierKind tier : TierKind.values()) {
            for (long seed : SEEDS) {
                String text = new MissionCode(tier, seed).text();
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
                String text = new MissionCode(tier, seed).text();
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
        String seed = new MissionCode(TierKind.MEDIUM, 4_321L).text().substring(1, MissionCode.LENGTH - 1);
        Set<Character> tiers = new HashSet<>();
        for (TierKind tier : TierKind.values()) {
            tiers.add(new MissionCode(tier, 0L).text().charAt(0));
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
            char letter = new MissionCode(tier, 0L).text().charAt(0);

            assertThat(RoomCode.ALPHABET).contains(String.valueOf(letter));
            assertThat(letters.add(letter)).as("%s shares its letter", tier).isTrue();
        }
    }

    @Test
    void refusesWhatCannotBeACodeAtAll() {
        String text = new MissionCode(TierKind.HARD, 77L).text();

        assertThat(MissionCode.parse(text.substring(1))).isNull();
        assertThat(MissionCode.parse(text + "A")).isNull();
        assertThat(MissionCode.parse(text.substring(0, 3) + "0" + text.substring(4)))
                .isNull();
        assertThat(MissionCode.parse("")).isNull();
    }

    @Test
    void refusesASeedSixCharactersCannotHold() {
        assertThatThrownBy(() -> new MissionCode(TierKind.EASY, -1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MissionCode(TierKind.EASY, MissionCode.SEEDS))
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
