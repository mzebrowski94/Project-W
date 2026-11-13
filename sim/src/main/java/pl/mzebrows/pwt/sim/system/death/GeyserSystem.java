package pl.mzebrows.pwt.sim.system.death;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Spawner;
import pl.mzebrows.pwt.sim.component.Geyser;
import pl.mzebrows.pwt.sim.component.Position;

/**
 * Runs every geyser's cycle (G0089): dormant, filling, erupting, dormant again.
 *
 * <p>It lands no damage and lays no fire of its own. <strong>An eruption is a carrier of the geyser's fire born
 * dead</strong>, so the fire arrives through the one death reaction every fire arrives through (G0018, G0035) - which
 * is why a geyser's fire is a blast's fire in every way: neutral, steered round, wounding, swelling and collapsing.
 *
 * <p>It sits inside the death window, before {@code SlickSystem}, so an eruption whose filling runs out this tick is
 * burning this tick; and before {@code MineSystem}, which must stay immediately before the blast.
 */
public final class GeyserSystem extends EntitySystem {

    private static final ComponentMapper<Geyser> GEYSER = ComponentMapper.getFor(Geyser.class);
    private static final ComponentMapper<Position> POSITION = ComponentMapper.getFor(Position.class);

    private final SimConfig.GeyserConfig config;
    private final long levelSeed;
    private final float tickSeconds;
    private final Spawner spawner;

    private ImmutableArray<Entity> geysers = new ImmutableArray<>(new Array<>());

    public GeyserSystem(SimConfig config, Spawner spawner) {
        this.config = config.geyser();
        this.levelSeed = config.seed();
        this.tickSeconds = config.tickSeconds();
        this.spawner = spawner;
    }

    @Override
    public void addedToEngine(Engine engine) {
        geysers = engine.getEntitiesFor(Family.all(Geyser.class, Position.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < geysers.size(); i++) {
            Entity entity = geysers.get(i);
            Geyser geyser = GEYSER.get(entity);
            if (geyser.remainingTicks > 0) {
                geyser.remainingTicks--;
            }
            if (geyser.remainingTicks > 0) {
                continue;
            }
            switch (geyser.phase) {
                case DORMANT -> {
                    geyser.phase = Geyser.Phase.FILLING;
                    geyser.remainingTicks = geyser.fillingTicks;
                }
                case FILLING -> {
                    Position at = POSITION.get(entity);
                    spawner.spawnEruption(at.x, at.y, geyser.eruption);
                    geyser.phase = Geyser.Phase.ERUPTING;
                    geyser.remainingTicks = geyser.burningTicks;
                }
                case ERUPTING -> {
                    geyser.cycle++;
                    geyser.phase = Geyser.Phase.DORMANT;
                    geyser.remainingTicks =
                            config.dormantTicks(levelSeed, Handles.of(entity), geyser.cycle, tickSeconds);
                }
            }
        }
    }
}
