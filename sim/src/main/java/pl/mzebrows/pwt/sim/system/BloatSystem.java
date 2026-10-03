package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.BlastsOnDeath;
import pl.mzebrows.pwt.sim.component.Bloat;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Ticks;

/**
 * Pumps a body up while a beam is on it, lets it down again when the beam comes off, and bursts one
 * that fills.
 *
 * <p><strong>A body pumped full explodes by dying</strong> (G0018): this writes a radius on the
 * reaction and knows nothing about blasts. <strong>Only something already dying bursts</strong>,
 * though it fills on the living too. <strong>It never touches the body</strong>: the fill is drawn.
 * The burst is sized off the body and leaves nothing burning.
 *
 * <p>Holds the throes open while any charge is left - the one writer of that clock besides
 * {@code DyingSystem}.
 */
public final class BloatSystem extends IteratingSystem {

    private static final ComponentMapper<Bloat> BLOAT = ComponentMapper.getFor(Bloat.class);
    private static final ComponentMapper<Dying> DYING = ComponentMapper.getFor(Dying.class);
    private static final ComponentMapper<Body> BODY = ComponentMapper.getFor(Body.class);
    private static final ComponentMapper<BlastsOnDeath> EXPLODES = ComponentMapper.getFor(BlastsOnDeath.class);

    private final Spawner spawner;
    private final SimConfig.BloatConfig config;

    /** How long a charged body is held, in the ticks its throes count. */
    private final int holdTicks;

    public BloatSystem(SimConfig config, Spawner spawner) {
        super(Family.all(Bloat.class, Body.class).get());
        this.spawner = spawner;
        this.config = config.bloat();
        this.holdTicks = Ticks.of(config.bloat().holdSeconds(), config.tickSeconds());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Bloat bloat = BLOAT.get(entity);
        boolean fed = bloat.takeFed();
        if (!bloat.charged()) {
            return;
        }
        boolean dying = Life.dying(entity);
        if (dying && bloat.full() && config.bursts()) {
            burst(entity);
            return;
        }
        if (!fed) {
            // A thing being charged does not settle; only what nobody is pointing at does.
            bloat.settle(config.settlePerSecond(), deltaTime);
        }
        if (!dying || !bloat.charged()) {
            return;
        }
        // Still worth something, so the body waits; refreshed every tick.
        Dying throes = DYING.get(entity);
        if (throes.remainingTicks < holdTicks) {
            throes.remainingTicks = holdTicks;
        }
    }

    /**
     * Full: it goes off. The radius is written onto the reaction the unit was already carrying and
     * the body is marked dead, so the explosion arrives through the one path (G0018).
     *
     * <p>Sized off the body, leaves nothing burning, drawn in the colour of the ring that charged it.
     */
    private void burst(Entity entity) {
        if (EXPLODES.has(entity)) {
            float radius = config.blastFor(BODY.get(entity).radius);
            EXPLODES.get(entity).set(radius, radius).withoutFire().drawnAs(Look.BLOAT);
        }
        spawner.markDead(entity);
    }
}
