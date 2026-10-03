package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.ConfigFile;
import pl.mzebrows.pwt.sim.Players;

/** What {@code graphic.properties} holds beyond what every config file is held to. */
class GraphicConfigTest {

    @Test
    void namesAColourThatIsNotOneAndKeepsTheShippedColour() {
        ConfigFile.Parsed<GraphicConfig> parsed = GraphicConfig.FILE.read("graphic.shield.color=bluish\n");

        assertThat(parsed.problems()).contains("cannot read graphic.shield.color=bluish as Color (not a colour)");
        assertThat(parsed.config().shield().color())
                .isEqualTo(GraphicConfig.defaults().shield().color());
    }

    /** A colour for every player the game can hold, so the ceiling cannot move without the file following. */
    @Test
    void holdsAColourForEveryPlayerAGameCanSeat() {
        assertThat(GraphicConfig.Variants.class.getRecordComponents()).hasSize(Players.MAX - 1);
    }
}
