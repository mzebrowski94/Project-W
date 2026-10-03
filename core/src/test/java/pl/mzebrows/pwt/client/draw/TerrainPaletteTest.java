package pl.mzebrows.pwt.client.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;

class TerrainPaletteTest {

    private static final int SHADES = 4;

    /** Three tiles across, as the game ships. */
    private static final int PATCH = 3;

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
        assertThatThrownBy(() -> new TerrainPalette(new Color[] {Color.GRAY}, VARIANCE, 0f, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TerrainPalette palette(float variance) {
        Color[] colors = new Color[Terrain.values().length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = new Color(0.1f * (i + 1), 0.2f, 0.3f, 1f);
        }
        return new TerrainPalette(colors, variance, 0f, 1, 1);
    }

    /** With one shade there is no per-tile variation at all, which is what the old palette was. */
    @Test
    void leavesEveryTileTheSameShadeWhenThereIsOnlyOne() {
        TerrainPalette palette = palette(VARIANCE);

        assertThat(palette.shadeStepFor(3, 7)).isZero();
        assertThat(palette.tileShadeFor(0)).isZero();
    }

    /** Where a tile is decides how it is lit, so the ground looks the same on every run. */
    @Test
    void givesATileTheSameShadeEveryTime() {
        TerrainPalette palette = varied();

        assertThat(palette.shadeStepFor(12, 5)).isEqualTo(palette.shadeStepFor(12, 5));
        assertThat(palette.shadeStepFor(12, 5)).isBetween(0, SHADES - 1);
    }

    /**
     * Neighbours agree, which is the difference between mottled ground and static. Hashing every tile
     * on its own is by construction white noise, and it looked like it.
     */
    @Test
    void givesAWholePatchOfTilesTheOneShade() {
        TerrainPalette palette = varied();

        int shade = palette.shadeStepFor(PATCH * 4, PATCH * 2);
        for (int col = 0; col < PATCH; col++) {
            for (int row = 0; row < PATCH; row++) {
                assertThat(palette.shadeStepFor(PATCH * 4 + col, PATCH * 2 + row))
                        .isEqualTo(shade);
            }
        }
    }

    /** A patch may not widen where the coordinates cross zero, which plain division would do. */
    @Test
    void keepsPatchesTheSameWidthEitherSideOfTheOrigin() {
        TerrainPalette palette = varied();

        assertThat(palette.shadeStepFor(-1, 0)).isEqualTo(palette.shadeStepFor(-PATCH, 0));
    }

    /** And a flat field is not one flat colour, or none of this was worth doing. */
    @Test
    void doesNotGiveEveryTileOfAFieldTheSameShade() {
        TerrainPalette palette = varied();

        int first = palette.shadeStepFor(0, 0);
        boolean varies = false;
        for (int col = 1; col < 128 && !varies; col++) {
            varies = palette.shadeStepFor(col, 0) != first;
        }

        assertThat(varies).isTrue();
    }

    @Test
    void refusesAPaletteWithNoShadesAtAll() {
        Color[] colors = new Color[Terrain.values().length];
        java.util.Arrays.fill(colors, Color.GRAY);

        assertThatThrownBy(() -> new TerrainPalette(colors, VARIANCE, 0.02f, 0, PATCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TerrainPalette varied() {
        Color[] colors = new Color[Terrain.values().length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = new Color(0.1f * (i + 1), 0.2f, 0.3f, 1f);
        }
        return new TerrainPalette(colors, VARIANCE, 0.03f, SHADES, PATCH);
    }
}
