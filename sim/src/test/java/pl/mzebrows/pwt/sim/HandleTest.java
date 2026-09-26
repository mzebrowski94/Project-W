package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.component.Components;
import pl.mzebrows.pwt.sim.component.Handle;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Strike;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * An entity is referred to by its handle, never by the {@code Entity} object: the engine pools those,
 * so a kept reference comes back aimed at whatever was spawned next. A handle is never reused, so
 * holding one across ticks is always safe - it finds the entity, or it finds nobody.
 */
class HandleTest {

    private static final float MIDDLE = 50f;

    private static final float FAR = 120f;

    @Test
    void givesEveryEntityItsOwnHandleInSpawnOrder() {
        Simulation simulation = new Simulation(SimConfig.defaults());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnTrooper(MIDDLE, MIDDLE + 5f);
        simulation.spawnUnit(UnitKind.GRUNT, FAR, MIDDLE);

        ImmutableArray<Entity> handled = handled(simulation);

        assertThat(handled.size()).isEqualTo(simulation.engine().getEntities().size());
        long previous = Handles.NOBODY;
        for (Entity entity : handled) {
            assertThat(Handles.of(entity)).isGreaterThan(previous);
            previous = Handles.of(entity);
        }
    }

    @Test
    void findsAnEntityByItsHandle() {
        Simulation simulation = new Simulation(SimConfig.defaults());
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);

        assertThat(Handles.resolve(handled(simulation), Handles.of(trooper))).isSameAs(trooper);
    }

    @Test
    void findsNobodyOnceTheEntityIsGoneEvenWhenItsObjectComesBack() {
        Simulation simulation = new Simulation(SimConfig.defaults());
        Entity first = simulation.spawnUnit(UnitKind.GRUNT, MIDDLE, MIDDLE);
        long handle = Handles.of(first);
        simulation.spawner().markDead(first);
        simulation.step();

        Entity next = simulation.spawnUnit(UnitKind.GRUNT, FAR, MIDDLE);

        assertThat(next).as("the engine pools entities").isSameAs(first);
        assertThat(Handles.resolve(handled(simulation), handle)).isNull();
        assertThat(Handles.of(next)).isNotEqualTo(handle);
    }

    /** A swing whose owner died follows nobody - it used to follow whatever reused his entity. */
    @Test
    void leavesASwingWhereItWasWhenTheManWhoSwungItIsGone() {
        Simulation simulation = new Simulation(SimConfig.defaults());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity swing = simulation
                .spawner()
                .spawnStrike(commander, MIDDLE, MIDDLE, 0f, 1f, commander.getComponent(Melee.class), Side.PLAYER);
        simulation.spawner().markDead(commander);
        simulation.step();
        simulation.spawnUnit(UnitKind.GRUNT, FAR, FAR);

        simulation.step();

        assertThat(swing.getComponent(Strike.class).originX).isEqualTo(MIDDLE);
        assertThat(swing.getComponent(Position.class).x).isLessThan(FAR / 2f);
    }

    /** Pooling is exactly why a component may not keep one: it comes back aimed at something else. */
    @Test
    void noComponentHoldsAnEntity() {
        for (Class<?> component : Components.ALL) {
            for (Field field : component.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    assertThat(field.getType())
                            .as("%s.%s", component.getSimpleName(), field.getName())
                            .isNotEqualTo(Entity.class);
                }
            }
        }
    }

    private static ImmutableArray<Entity> handled(Simulation simulation) {
        return simulation.engine().getEntitiesFor(Family.all(Handle.class).get());
    }
}
