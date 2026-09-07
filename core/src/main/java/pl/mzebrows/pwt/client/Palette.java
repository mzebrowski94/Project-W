package pl.mzebrows.pwt.client;

import com.badlogic.gdx.graphics.Color;
import pl.mzebrows.pwt.sim.component.Appearance;

/**
 * The one place that turns what an entity <em>is</em> into something visible.
 *
 * <p>This is the client half of G0003: the simulation says {@code ENEMY}, and only this class knows
 * that means red. Swapping placeholder circles for sprites replaces the renderer and this mapping,
 * and touches nothing in {@code sim}.
 *
 * @param commander colour of the player-controlled unit
 * @param enemy colour of a chasing unit
 * @param projectile colour of a fired projectile
 * @param obstacle colour of a static blocker
 * @param unknown colour for an entity whose look was never set - deliberately loud
 */
public record Palette(Color commander, Color enemy, Color projectile, Color obstacle, Color unknown) {

    /** The colour to draw a given look in. */
    public Color colorFor(Appearance.Look look) {
        return switch (look) {
            case COMMANDER -> commander;
            case ENEMY -> enemy;
            case PROJECTILE -> projectile;
            case OBSTACLE -> obstacle;
            case NONE -> unknown;
        };
    }
}
