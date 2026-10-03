package pl.mzebrows.pwt.client.draw;

import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.PreviousPosition;

/**
 * Where an entity is drawn this frame: between where it was before the last tick and where it is now, by how
 * far the frame has got towards the next tick. The renderer, the camera and the reticles all ask it, so what
 * is drawn, what is framed and what is aimed at never disagree by a fraction of a tick.
 */
public final class Drawn {

    private Drawn() {}

    public static float x(Position at, PreviousPosition was, float alpha) {
        return was.x + (at.x - was.x) * alpha;
    }

    public static float y(Position at, PreviousPosition was, float alpha) {
        return was.y + (at.y - was.y) * alpha;
    }
}
