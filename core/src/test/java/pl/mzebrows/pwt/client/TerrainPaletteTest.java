package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;

class TerrainPaletteTest {

    private static final float VARIANCE = 0.06f;
    private static final Offset<Float> TOLERANCE = Offset.offset(1e-5f);

    @Test
    void drawsEachKindOfGroundInItsOwnColour() {
        TerrainPalette palette = palette(VARIANCE);

        assertThat(palette.colorFor(Terrain.PLAIN)).isNotEqualTo(palette.colorFor(Terrain.ROCK));
    }

    @Test
    void givesTheSameRegionTheSameShadeEveryTime() {
        TerrainPalette palette = palette(VARIANCE);

        assertThat(palette.shadeFor(3)).isCloseTo(palette.shadeFor(3), TOLERANCE);
    }

    @Test
    void shadesNeighbouringRegionsApart() {
        TerrainPalette palette = palette(VARIANCE);

        assertThat(palette.shadeFor(0)).isNotCloseTo(palette.shadeFor(1), TOLERANCE);
    }

    @Test
    void keepsEveryShadeInsideTheVarianceItWasGiven() {
        TerrainPalette palette = palette(VARIANCE);

        for (int region = 0; region < 64; region++) {
            assertThat(palette.shadeFor(region)).isBetween(-VARIANCE, VARIANCE);
        }
    }

    @Test
    void drawsEveryRegionAlikeWhenThereIsNoVarianceToSpend() {
        TerrainPalette palette = palette(0f);

        assertThat(palette.shadeFor(0)).isZero();
        assertThat(palette.shadeFor(9)).isZero();
    }

    /** A tile outside any region still has to be drawable rather than throwing mid-frame. */
    @Test
    void leavesATileThatBelongsToNoRegionUnshaded() {
        TerrainPalette palette = palette(VARIANCE);

        assertThat(palette.shadeFor(-1)).isZero();
    }

    @Test
    void refusesAPaletteThatIsMissingATerrain() {
        assertThatThrownBy(() -> new TerrainPalette(new Color[] {Color.GRAY}, VARIANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TerrainPalette palette(float variance) {
        Color[] colors = new Color[Terrain.values().length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = new Color(0.1f * (i + 1), 0.2f, 0.3f, 1f);
        }
        return new TerrainPalette(colors, variance);
    }
}
