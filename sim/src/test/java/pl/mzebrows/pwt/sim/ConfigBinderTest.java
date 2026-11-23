package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.BiomeGround;
import pl.mzebrows.pwt.map.Clearings;
import pl.mzebrows.pwt.map.Corridors;
import pl.mzebrows.pwt.map.Crags;
import pl.mzebrows.pwt.map.Houses;
import pl.mzebrows.pwt.map.Ice;
import pl.mzebrows.pwt.map.Outcrops;
import pl.mzebrows.pwt.map.Patch;
import pl.mzebrows.pwt.map.PatchGround;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Chances;
import pl.mzebrows.pwt.sim.def.Defs;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * A config record is filled from {@code key=value} text by the names of its components, so a tunable
 * is a record field and a line in the file and nothing else. Every key is accounted for both ways: one
 * the records do not have is as much a mistake as one the file does not give.
 */
class ConfigBinderTest {

    enum Size {
        SMALL,
        LARGE
    }

    record Gait(boolean enabled, float speed) {
        static Gait off() {
            return new Gait(false, 0f);
        }
    }

    record Body(
            int count,
            long seed,
            float radius,
            boolean solid,
            Look look,
            @Nullable UnitKind into,
            Gait gait) {}

    record Table(Defs<Size, Gait> sizes) {}

    record Pickup(Grant grant) {}

    record Strain(Chances chance) {}

    record Level(@ConfigKey("ai.chance") Chances chances, Strain grunt) {}

    record Renamed(@ConfigKey("unit") float units) {}

    /** A sealed family whose records take several arguments, as an upgrade's effects do. */
    sealed interface Change permits Change.Count, Change.Scale, Change.Reset {

        record Count(Size size, int amount) implements Change {}

        record Scale(Size size, @Nullable Size only, float factor) implements Change {}

        record Reset() implements Change {}
    }

    /** A type the binder has no reading of - a record of one value, as a key code is - read by a conversion. */
    record Hue(int code) {}

    record Tinted(Hue hue, float alpha) {}

    private static final ConfigBinder.Conversions HUES = new ConfigBinder.Conversions(Map.of(Hue.class, text -> {
        return switch (text) {
            case "red" -> new Hue(1);
            case "blue" -> new Hue(2);
            default -> throw new IllegalArgumentException("no hue called " + text);
        };
    }));

    record OneChange(Change change) {}

    record Changes(List<Change> changes) {}

    /** A plain record in a list, read by its arguments alone, as a finale's turrets and mouths are. */
    record Spot(String name, int dx, int dy) {}

    record Spots(List<Spot> spots, List<Change> changes) {}

    record Placed(Spot at) {}

    /** A block of its own in a list, as a finale's layouts are: numbered from one under the list's key. */
    record Area(float watch, List<Spot> spots) {}

    record Areas(List<Area> areas) {}

    @Test
    void readsATypeThroughTheConversionItIsGivenRatherThanAsABlock() {
        ConfigBinder.Bound<Tinted> bound = ConfigBinder.bind(
                Tinted.class, "t", ConfigBinder.parse("t.hue=blue\nt.alpha=0.5"), new TreeMap<>(), HUES);

        assertThat(bound.problems()).isEmpty();
        assertThat(bound.value()).isEqualTo(new Tinted(new Hue(2), 0.5f));
    }

    @Test
    void namesAValueItsConversionCannotReadAndTakesTheFallback() {
        ConfigBinder.Bound<Tinted> bound = ConfigBinder.bind(
                Tinted.class,
                "t",
                ConfigBinder.parse("t.hue=mauve\nt.alpha=0.5"),
                ConfigBinder.parse("t.hue=red\nt.alpha=1"),
                HUES);

        assertThat(bound.problems()).containsExactly("cannot read t.hue=mauve as Hue (no hue called mauve)");
        assertThat(bound.value()).isEqualTo(new Tinted(new Hue(1), 0.5f));
    }

    @Test
    void fillsEveryComponentByName() {
        Body body = strict(Body.class, """
                test.count=3
                test.seed=20260906
                test.radius=1.5
                test.solid=true
                test.look=grunt
                test.into=brute
                test.gait.enabled=true
                test.gait.speed=8.25
                """);

        assertThat(body)
                .isEqualTo(new Body(3, 20_260_906L, 1.5f, true, Look.GRUNT, UnitKind.BRUTE, new Gait(true, 8.25f)));
    }

    @Test
    void readsNoneAsNothingForWhatMayBeNothing() {
        Body body = strict(Body.class, """
                test.count=1
                test.seed=1
                test.radius=1
                test.solid=false
                test.look=grunt
                test.into=none
                test.gait.enabled=false
                test.gait.speed=0
                """);

        assertThat(body.into()).isNull();
    }

    @Test
    void turnsOffABlockTheFileSaysNothingAbout() {
        Body body = strict(Body.class, """
                test.count=1
                test.seed=1
                test.radius=1
                test.solid=false
                test.look=grunt
                test.into=none
                """);

        assertThat(body.gait()).isEqualTo(Gait.off());
    }

    @Test
    void fillsAKindTableOneBlockPerKind() {
        Table table = strict(Table.class, """
                test.sizes.small.enabled=true
                test.sizes.small.speed=2
                test.sizes.large.enabled=true
                test.sizes.large.speed=1
                """);

        assertThat(table.sizes().of(Size.SMALL).speed()).isEqualTo(2f);
        assertThat(table.sizes().of(Size.LARGE).speed()).isEqualTo(1f);
    }

    @Test
    void readsAGrantAsItsNameAndArgument() {
        assertThat(strict(Pickup.class, "test.grant=mutate brute").grant()).isEqualTo(new Grant.Mutate(UnitKind.BRUTE));
        assertThat(strict(Pickup.class, "test.grant=feed 2").grant()).isEqualTo(new Grant.Feed(2));
        assertThat(strict(Pickup.class, "test.grant=none").grant()).isEqualTo(Grant.NOTHING);
        assertThat(strict(Pickup.class, "test.grant=detonate").grant()).isEqualTo(Grant.DETONATE);
    }

    @Test
    void readsASealedRecordThatTakesSeveralArgumentsOneWordEach() {
        assertThat(strict(OneChange.class, "test.change=count large 2").change())
                .isEqualTo(new Change.Count(Size.LARGE, 2));
        assertThat(strict(OneChange.class, "test.change=scale small none 1.5").change())
                .isEqualTo(new Change.Scale(Size.SMALL, null, 1.5f));
    }

    @Test
    void refusesASealedRecordGivenTheWrongNumberOfArguments() {
        assertThatThrownBy(() -> strict(OneChange.class, "test.change=count large"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test.change");
        assertThatThrownBy(() -> strict(OneChange.class, "test.change=count large 2 3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test.change");
    }

    @Test
    void readsAListOfSealedRecordsSeparatedBySemicolons() {
        Changes changes = strict(Changes.class, "test.changes=count small 1; reset ;scale large small 2");

        assertThat(changes.changes())
                .containsExactly(
                        new Change.Count(Size.SMALL, 1),
                        new Change.Reset(),
                        new Change.Scale(Size.LARGE, Size.SMALL, 2f));
    }

    @Test
    void refusesAListWithOneRecordItCannotRead() {
        assertThatThrownBy(() -> strict(Changes.class, "test.changes=count small 1; count huge 2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test.changes");
    }

    @Test
    void readsAListOfPlainRecordsByTheirArgumentsAlone() {
        Spots spots = strict(Spots.class, "test.spots=nw -14 -13; se 12 8\ntest.changes=reset");

        assertThat(spots.spots()).containsExactly(new Spot("nw", -14, -13), new Spot("se", 12, 8));
    }

    @Test
    void readsARecordGivenAValueAtItsOwnKeyAsItsArgumentsOnOneLine() {
        assertThat(strict(Placed.class, "test.at=post 0 -3").at()).isEqualTo(new Spot("post", 0, -3));
    }

    @Test
    void readsAListOfRecordsTheFileSaysNothingAboutAsEmpty() {
        Spots spots = strict(Spots.class, "test.changes=reset");

        assertThat(spots.spots()).isEmpty();
    }

    @Test
    void readsAListOfBlocksNumberedFromOne() {
        Areas areas = strict(Areas.class, "test.areas.1.watch=5\ntest.areas.1.spots=nw -14 -13\ntest.areas.2.watch=6");

        assertThat(areas.areas())
                .containsExactly(new Area(5f, List.of(new Spot("nw", -14, -13))), new Area(6f, List.of()));
    }

    /** A gap ends the list, and what lies past it is a key no record has - never a layout quietly dropped. */
    @Test
    void endsAListOfBlocksAtTheFirstNumberTheFileDoesNotMention() {
        assertThatThrownBy(() -> strict(Areas.class, "test.areas.1.watch=5\ntest.areas.3.watch=6"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown key test.areas.3.watch");
    }

    @Test
    void refusesAPlainRecordGivenTheWrongNumberOfArguments() {
        assertThatThrownBy(() -> strict(Spots.class, "test.spots=nw -14\ntest.changes=reset"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spot takes 3 argument(s)");
    }

    @Test
    void reportsAKeyGivenTwiceRatherThanLettingTheLastOneWinQuietly() {
        ConfigBinder.Bound<Gait> bound =
                ConfigBinder.bind(Gait.class, "test", "test.enabled=true\ntest.speed=2\ntest.speed=3\n", text(""));

        assertThat(bound.problems()).containsExactly("key given twice: test.speed");
    }

    @Test
    void refusesAKeyGivenTwiceWhenBindingStrictly() {
        assertThatThrownBy(() -> ConfigBinder.bindStrictly(
                        Gait.class, "test", "test.enabled=true\ntest.enabled=true\ntest.speed=1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key given twice: test.enabled");
    }

    @Test
    void takesEveryChanceAStrainDoesNotNameFromTheSharedBlock() {
        Strain strain = ConfigBinder.bindStrictly(
                        Level.class, "sim", text(sharedChances() + "sim.grunt.chance.bite_a_barrel=0.9\n"))
                .grunt();

        assertThat(strain.chance().of(AiChance.BITE_A_BARREL)).isEqualTo(0.9f);
        assertThat(strain.chance().of(AiChance.SWING_AT_A_LEAP)).isEqualTo(0.5f);
        assertThat(strain.chance().longestGap()).isEqualTo(2f);
    }

    @Test
    void readsAComponentUnderTheKeyItIsRenamedTo() {
        assertThat(strict(Renamed.class, "test.unit=4").units()).isEqualTo(4f);
    }

    @Test
    void refusesAKeyNoRecordHas() {
        assertThatThrownBy(() -> strict(Pickup.class, "test.grant=none\ntest.grnat=feed 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown key test.grnat");
    }

    @Test
    void refusesAMissingValue() {
        assertThatThrownBy(() -> strict(Gait.class, "test.enabled=true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing test.speed");
    }

    @Test
    void refusesAValueThatIsNotANumber() {
        assertThatThrownBy(() -> strict(Gait.class, "test.enabled=true\ntest.speed=fast"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test.speed");
    }

    @Test
    void takesTheFallbackForWhatIsMissingOrBrokenAndSaysSo() {
        ConfigBinder.Bound<Gait> bound = ConfigBinder.bind(
                Gait.class, "test", text("test.speed=fast\ntest.extra=1"), text("test.enabled=true\ntest.speed=3"));

        assertThat(bound.value()).isEqualTo(new Gait(true, 3f));
        assertThat(bound.problems())
                .anySatisfy(problem -> assertThat(problem).contains("missing test.enabled"))
                .anySatisfy(problem -> assertThat(problem).contains("test.speed"))
                .anySatisfy(problem -> assertThat(problem).contains("unknown key test.extra"));
    }

    record Biome(BiomeGround ground) {}

    @Test
    void readsABiomesGroundWithItsPatchesAndLeavesALeftOutCoatOff() {
        Biome brake = strict(Biome.class, """
                test.ground.widthMin=50
                test.ground.widthMax=58
                test.ground.regions=3
                test.ground.base=plain
                test.ground.fallback=organic
                test.ground.patches=rect neighbour 0.035 4 6; blob thicket 0.18 6 11
                test.ground.outcrops.density=0.015
                test.ground.outcrops.lengthMin=2
                test.ground.outcrops.lengthMax=4
                test.ground.outcrops.thicknessMin=1
                test.ground.outcrops.thicknessMax=2
                test.ground.outcrops.arms=0.3
                test.ground.outcrops.ragged=0
                """);

        assertThat(brake.ground())
                .isEqualTo(new BiomeGround(
                        50,
                        58,
                        3,
                        Terrain.PLAIN,
                        Terrain.ORGANIC,
                        Houses.off(),
                        List.of(
                                new Patch.Rect(PatchGround.neighbour(), 0.035f, 4, 6),
                                new Patch.Blob(PatchGround.of(Terrain.THICKET), 0.18f, 6, 11)),
                        Crags.off(),
                        Corridors.off(),
                        new Outcrops(0.015f, 2, 4, 1, 2, 0.3f, 0f),
                        Clearings.off(),
                        Ice.off(),
                        List.of()));
    }

    @Test
    void refusesPatchGroundThatIsNotGround() {
        ConfigBinder.Bound<Biome> bound = ConfigBinder.bind(Biome.class, "test", """
                        test.ground.widthMin=50
                        test.ground.widthMax=58
                        test.ground.regions=3
                        test.ground.base=plain
                        test.ground.fallback=organic
                        test.ground.patches=rect rock 0.04 4 6
                        """, new TreeMap<>());

        assertThat(bound.problems()).anySatisfy(problem -> assertThat(problem).contains("test.ground.patches"));
    }

    @Test
    void readsKeyValueLinesAndSkipsCommentsAndBlanks() {
        assertThat(ConfigBinder.parse("# a comment\n\n  a.b = 1 \n! another\nc=two words\n"))
                .containsExactly(java.util.Map.entry("a.b", "1"), java.util.Map.entry("c", "two words"));
    }

    private static <T extends Record> T strict(Class<T> type, String text) {
        return ConfigBinder.bindStrictly(type, "test", text(text));
    }

    private static SortedMap<String, String> text(String text) {
        return new TreeMap<>(ConfigBinder.parse(text));
    }

    private static String sharedChances() {
        return """
                sim.ai.chance.swing_at_a_leap=0.5
                sim.ai.chance.bite_a_barrel=0.8
                sim.ai.chance.aid_a_trooper=0.4
                sim.ai.chance.man_a_turret=2
                sim.ai.chance.aid_the_commander=1.5
                sim.ai.chance.struggle=0.4
                sim.ai.chance.throw_a_grenade=0
                sim.ai.chance.longestGap=2
                """;
    }
}
