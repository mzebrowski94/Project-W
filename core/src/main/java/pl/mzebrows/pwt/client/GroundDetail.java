package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;

/**
 * What is drawn <em>on</em> the ground, over the flat colour each tile already gets.
 *
 * <p>Two things, and both exist for the same reason: a floor made of nothing but shaded rectangles
 * tells the player where he is and not what it will cost him. A thicket has to read as something
 * growing on the floor rather than as another colour of floor, and rock has to read as a wall rather
 * than as a darker patch - and neither of those is a colour, it is a mark.
 *
 * <p><strong>The transparency is mixed rather than blended.</strong> Nothing else in this renderer
 * turns GL blending on, and a global state change made for one kind of tile is a state change every
 * other draw then has to be checked against. A hatch is drawn over a colour that is known when the
 * map is bound, so the half-transparent version of it can simply be worked out there - which is
 * exact, costs nothing per frame, and looks the same on every machine.
 *
 * @param thicketCells how many marks across one tile of undergrowth, per side. A few: this has to
 *     read as a texture at a glance and from the pulled-back view as well as from the close one
 * @param thicketDot how much of its own cell each mark fills, as a fraction. Well under one, or the
 *     marks meet and the hatch becomes a second flat colour
 * @param thicketTint what the marks are mixed towards - the growth itself, against the darker floor
 *     it stands on
 * @param thicketAlpha how far towards that the marks are mixed, in {@code [0, 1]}. Half is a hatch
 *     you can see through; one would be a solid pattern and would read as a floor tile again
 * @param rockEdge what the rim round a wall is mixed towards. Lighter than the rock, because what
 *     the player has to see is where the wall <em>ends</em>
 * @param rockEdgeWidth how thick that rim is, as a fraction of a tile. Thin: it is an edge, and a
 *     wide one eats the wall it is drawn on
 * @param rockEdgeAlpha how far the rim is mixed towards {@link #rockEdge}, in {@code [0, 1]}
 */
public record GroundDetail(
        int thicketCells,
        float thicketDot,
        Color thicketTint,
        float thicketAlpha,
        Color rockEdge,
        float rockEdgeWidth,
        float rockEdgeAlpha) {

    public GroundDetail {
        if (thicketCells < 1) {
            throw new IllegalArgumentException("a thicket needs at least one mark per tile, was " + thicketCells);
        }
        if (thicketDot <= 0f || thicketDot > 1f) {
            throw new IllegalArgumentException("a mark must fit inside its own cell, was " + thicketDot);
        }
        if (thicketAlpha < 0f || thicketAlpha > 1f || rockEdgeAlpha < 0f || rockEdgeAlpha > 1f) {
            throw new IllegalArgumentException("a mix must be in [0, 1]");
        }
        if (rockEdgeWidth < 0f || rockEdgeWidth > 0.5f) {
            throw new IllegalArgumentException("a rim is an edge, not half a tile, was " + rockEdgeWidth);
        }
    }

    /** True if a rim round a wall is worth drawing at all. Zero width means not for me. */
    public boolean rimsRock() {
        return rockEdgeWidth > 0f && rockEdgeAlpha > 0f;
    }
}
