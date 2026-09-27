package pl.mzebrows.pwt.sim.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

/**
 * What one commander has done in this life (G0044): enemies he killed, control points taken while he
 * stood on them, and men he put back on their feet.
 *
 * <p><strong>A tally belongs to a life.</strong> It lives on the commander from the moment he is spawned
 * and is reaped with him; the man who respawns is a new entity with a new tally, so nothing anywhere
 * resets one. Shown, never spent (pillar 3).
 *
 * <p>Credit for a kill is a <em>slot</em>, never an entity (G0016): whatever lands a blow carries the
 * slot of the player behind it, or {@link #NOBODY}.
 */
public final class Tally implements Component, Pool.Poolable {

    /** The credit a blow carries when no player is behind it - a creature's, a trooper's, a mine's. */
    public static final int NOBODY = -1;

    /** Enemies whose killing blow was his - his shot, his sweep, his grenade, his turret, his blast. */
    public int kills;

    /** Control points taken while he stood inside them. */
    public int goals;

    /** Casualties he put back on their feet - a trooper or another player's commander alike. */
    public int stabilised;

    @Override
    public void reset() {
        kills = 0;
        goals = 0;
        stabilised = 0;
    }
}
