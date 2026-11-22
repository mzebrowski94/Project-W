package pl.mzebrows.pwt.client.draw;

import com.badlogic.gdx.graphics.Color;

/**
 * How to draw the rim every commander has: a disc a little wider than his body, under it, so he is told from his
 * troopers at a glance whatever colour his slot gives him (pillar 5).
 *
 * @param width how much wider than his body the rim reaches, in world units; zero draws none
 * @param color what the rim is drawn in
 */
public record BorderMark(float width, Color color) {

    public BorderMark {
        if (width < 0f) {
            throw new IllegalArgumentException("border width must not be negative, was " + width);
        }
    }

    /** Whether there is a rim to draw at all. */
    public boolean drawn() {
        return width > 0f;
    }
}
