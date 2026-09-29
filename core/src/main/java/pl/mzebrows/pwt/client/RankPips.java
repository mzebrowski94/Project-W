package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import pl.mzebrows.pwt.ui.layout.HudMetrics;

/**
 * An upgrade's rank as pips: one filled square per rank held, an empty frame for each still to take.
 * Placeholder shapes, not art (alpha rule); the aside and the pause's list draw the same ones.
 */
final class RankPips {

    private static final int GAP = 2;

    private RankPips() {}

    /** A pip's side for this font. */
    static int size(HudMetrics metrics) {
        return Math.max(2, metrics.fontSize() - 4);
    }

    /** How wide {@code max} pips are. */
    static int width(HudMetrics metrics, int max) {
        return max * (size(metrics) + GAP);
    }

    /** Draws them on a line of text whose capitals start at {@code top}. */
    static void draw(Hud hud, HudMetrics metrics, int x, int top, int ranks, int max, Color filled, Color empty) {
        int pip = size(metrics);
        int y = top - metrics.fontSize() + (metrics.fontSize() - pip) / 2;
        for (int rank = 0; rank < max; rank++) {
            int pipX = x + rank * (pip + GAP);
            if (rank < ranks) {
                hud.fill(pipX, y, pip, pip, filled);
            } else {
                hud.frame(pipX, y, pip, pip, empty);
            }
        }
    }
}
