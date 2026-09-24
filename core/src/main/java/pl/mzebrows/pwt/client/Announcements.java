package pl.mzebrows.pwt.client;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.def.ActionKind;

/**
 * What the console has already been told about one world, so that each change is said once.
 *
 * <p>Alpha-phase scaffolding: the action on offer and the wave that has arrived belong on screen, where a
 * player can see them without reading a log (pillar 5). Until there is an interface to put them in, each
 * is announced once each time it changes - and once each time, rather than every frame, is the whole
 * reason this exists. A line per frame is not a prompt, it is a flood.
 *
 * <p>Asking is what marks a change reported, so a caller that asks twice in a frame is told once. A
 * different world - a restart, a heal, the first one arriving online - starts it over.
 */
final class Announcements {

    /** Cached, because a {@code Family} built per frame is an allocation per frame. */
    private static final ComponentMapper<Action> ACTION = ComponentMapper.getFor(Action.class);

    private static final Family COMMANDERS =
            Family.all(Commander.class, Action.class).get();

    private final ActionKind[] announcedAction = new ActionKind[Players.MAX];
    private int announcedWave;
    private @Nullable Simulation following;

    Announcements() {
        Arrays.fill(announcedAction, ActionKind.NONE);
    }

    /**
     * The action this slot's commander could take right now, but only if it has changed since this was
     * last asked; null when it has not. Asked per player, because what is within one man's arm's length
     * says nothing about what is within another's.
     */
    @Nullable
    ActionKind newlyOfferedAction(Simulation world, int slot) {
        follow(world);
        Entity commander = Players.inSlot(world.engine().getEntitiesFor(COMMANDERS), slot);
        ActionKind offered = commander == null ? ActionKind.NONE : ACTION.get(commander).available;
        if (offered == announcedAction[slot]) {
            return null;
        }
        announcedAction[slot] = offered;
        return offered;
    }

    /**
     * The number of the wave that has just arrived, or zero if none has since this was last asked. Zero
     * rather than null because it is a count, and a level with no waves need not be told about in a
     * different type.
     */
    int newlyArrivedWave(Simulation world) {
        follow(world);
        int sent = world.waves().sent();
        if (sent == announcedWave) {
            return 0;
        }
        announcedWave = sent;
        return sent;
    }

    private void follow(Simulation world) {
        if (world != following) {
            following = world;
            Arrays.fill(announcedAction, ActionKind.NONE);
            announcedWave = 0;
        }
    }
}
