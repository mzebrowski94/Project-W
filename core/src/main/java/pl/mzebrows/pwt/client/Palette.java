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
 * <p>What an entity is <em>doing</em> is a separate axis, and lives in {@link StateTint} rather than
 * in another dozen colours here - a brute winding up and an enemy winding up should read as the same
 * thing happening to two different units, which is only true if the state is a modifier.
 *
 * @param commander colour of the player-controlled unit
 * @param enemy colour of a chasing unit
 * @param brute colour of a heavy chasing unit
 * @param projectile colour of a fired projectile
 * @param strike colour of a melee sweep
 * @param obstacle colour of a static blocker
 * @param unknown colour for an entity whose look was never set - deliberately loud
 */
public record Palette(
        Color commander, Color enemy, Color brute, Color projectile, Color strike, Color obstacle, Color unknown) {

    /** The colour to draw a given look in, before anything it is doing is taken into account. */
    public Color colorFor(Appearance.Look look) {
        return switch (look) {
            case COMMANDER -> commander;
            case ENEMY -> enemy;
            case BRUTE -> brute;
            case PROJECTILE -> projectile;
            case STRIKE -> strike;
            case OBSTACLE -> obstacle;
            case NONE -> unknown;
        };
    }
}
