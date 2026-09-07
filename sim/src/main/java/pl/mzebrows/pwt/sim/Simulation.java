package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Velocity;
import pl.mzebrows.pwt.sim.system.MovementSystem;

/**
 * The whole game world and everything that happens in it.
 *
 * <p>Advances only through {@link #step()}, always by exactly {@link SimConfig#tickSeconds()}. Given
 * the same config and the same sequence of calls it produces byte-identical state on every run,
 * which is what deterministic replay and any future lockstep networking depend on.
 */
public final class Simulation {

    private final SimConfig config;
    private final Rng rng;
    private final PooledEngine engine;

    private long tick;

    public Simulation(SimConfig config) {
        this.config = config;
        this.rng = new Rng(config.seed());
        this.engine = new PooledEngine();
        this.engine.addSystem(new MovementSystem());
    }

    /** Advances the world by exactly one fixed step. */
    public void step() {
        engine.update(config.tickSeconds());
        tick++;
    }

    /** Creates a moving, collidable entity and adds it to the world. */
    public Entity spawn(float x, float y, float velocityX, float velocityY, float radius) {
        Entity entity = engine.createEntity();
        entity.add(engine.createComponent(Position.class).set(x, y));
        entity.add(engine.createComponent(Velocity.class).set(velocityX, velocityY));
        entity.add(engine.createComponent(Body.class).set(radius));
        engine.addEntity(entity);
        return entity;
    }

    /** The entity engine; the client module reads it to draw the world. */
    public PooledEngine engine() {
        return engine;
    }

    /** Number of fixed steps executed so far. */
    public long tick() {
        return tick;
    }

    public Rng rng() {
        return rng;
    }

    public SimConfig config() {
        return config;
    }
}
