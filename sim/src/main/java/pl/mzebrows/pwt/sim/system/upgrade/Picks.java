package pl.mzebrows.pwt.sim.system.upgrade;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.component.Drafts;

/**
 * Where a tick's picks enter the upgrade package: {@code Simulation.step} hands over the tick's input
 * before any system runs, and {@link UpgradeSystem} applies what was handed in at the end of the tick
 * (G0049). A pick names a draft and a kind, never a card's place; whether it is any good is decided
 * there, identically on every machine.
 */
public final class Picks {

    private static final ComponentMapper<Drafts> DRAFTS = ComponentMapper.getFor(Drafts.class);

    private final ImmutableArray<Entity> levels;

    public Picks(Engine engine) {
        this.levels = engine.getEntitiesFor(Family.all(Drafts.class).get());
    }

    /** Hands in this tick's picks, one per slot at most, in slot order. Allocates once per pick. */
    public void submit(PlayerInputs inputs) {
        for (int slot = 0; slot < inputs.capacity(); slot++) {
            UpgradeKind kind = inputs.pickOf(slot);
            if (kind == null) {
                continue;
            }
            for (int i = 0; i < levels.size(); i++) {
                DRAFTS.get(levels.get(i)).submitted.add(new Drafts.Pick(slot, inputs.pickSerialOf(slot), kind));
            }
        }
    }
}
