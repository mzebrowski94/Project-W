package pl.mzebrows.pwt.sim.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
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
 * <p>It spawns no explosion and lands no damage. <strong>A body pumped full explodes by dying</strong>,
 * through the one death reaction everything that explodes uses (G0018) - so this system knows nothing
 * about blasts, and a corpse that bursts, a barrel that is shot and a mine that runs out of fuse all
 * end in the same circle. What it writes is a radius on the reaction the unit was already carrying,
 * which is zero on everything until the moment one of them is worth anything.
 *
 * <p><strong>Only something already dying bursts.</strong> The charge builds on the living as
 * readily as on the dead - that is what makes holding the beam through a kill worth doing rather
 * than starting again on the body, and the ring is drawn the whole time so the player can see it
 * filling - but a gun that killed by inflation would be a gun with two ways of killing and one bar
 * to pay for both.
 *
 * <p><strong>It never touches the body.</strong> What fills is drawn on the thing, on the charge
 * channel, and sized by the client. A corpse that physically swelled was a corpse that shoved
 * everything standing near it around and quietly changed what a doorway was worth, which is a great
 * deal of consequence for a tell.
 *
 * <p>What it leaves is sized off the body and leaves nothing burning: a bigger creature makes a
 * bigger bang for the same reason it took longer to fill, and a corpse bomb that fenced off the
 * ground it went off on would be a tool the player could not use twice in the same place.
 *
 * <p>It holds the throes open while there is any charge at all, which is what makes a half-filled
 * corpse something to come back to rather than something to finish in one go - and it is the only
 * thing in the game that touches that clock from outside {@code DyingSystem}, which is why it is
 * written here rather than as a second field on {@code Dying}.
 *
 * <p>Registered after the damage chain, so this tick's hits are already on the component, and
 * <strong>before {@code BlastSystem}</strong>, so a body that fills this tick goes off in this
 * tick's death-reaction window rather than being owed to the next one - exactly where
 * {@code MineSystem} sits and for the same reason.
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
        boolean dying = DYING.has(entity) && DYING.get(entity).begun;
        if (dying && bloat.full() && config.bursts()) {
            burst(entity);
            return;
        }
        if (!fed) {
            // <strong>A thing being charged does not settle.</strong> Draining it on the very ticks
            // the beam is on it makes the gun fight the clock rather than the body: a grunt's corpse
            // gained almost nothing per second and a brute's, whose bigger body takes the beam more
            // slowly, actually went DOWN while it was being burnt. What settles is what nobody is
            // pointing at.
            bloat.settle(config.settlePerSecond(), deltaTime);
        }
        if (!dying || !bloat.charged()) {
            return;
        }
        // Still worth something, so the body waits. Refreshed every tick rather than set once, so a
        // corpse the beam keeps coming back to keeps waiting.
        Dying throes = DYING.get(entity);
        if (throes.remainingTicks < holdTicks) {
            throes.remainingTicks = holdTicks;
        }
    }

    /**
     * Full: it goes off. The radius is written onto the reaction the unit was already carrying and
     * the body is marked dead, so the explosion arrives through the one path (G0018).
     *
     * <p>Sized off the body, so a brute leaves a bigger circle than a grunt for the same reason it
     * took longer to fill; declared to leave nothing burning; and drawn in the colour of the ring
     * that charged it, so the flash reads as that ring's payoff rather than as some other explosion
     * that happened to go off in the same place.
     */
    private void burst(Entity entity) {
        if (EXPLODES.has(entity)) {
            float radius = config.blastFor(BODY.get(entity).radius);
            EXPLODES.get(entity).set(radius, radius).withoutFire().drawnAs(Look.BLOAT);
        }
        spawner.markDead(entity);
    }
}
