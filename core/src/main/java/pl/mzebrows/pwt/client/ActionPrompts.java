package pl.mzebrows.pwt.client;

import java.util.function.Consumer;
import java.util.function.IntPredicate;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.ui.hud.DispatchSink;
import pl.mzebrows.pwt.ui.hud.PlayerReadout;
import pl.mzebrows.pwt.ui.hud.Readout;
import pl.mzebrows.pwt.ui.hud.Reporter;

/**
 * Says on the console what the action key would do for each player here, each time that changes.
 *
 * <p>Alpha-phase scaffolding standing in for an on-screen prompt beside the commander (G0021) - not the
 * terminal, which is for what happened, not what could. It is a reporter so the client has one way of
 * noticing a change; what it stands in for is a label, since the simulation already decides and
 * publishes the action.
 */
final class ActionPrompts implements Reporter {

    private final IntPredicate local;
    private final Consumer<String> console;

    /**
     * @param local whether a slot is played on this machine - another machine's player is prompted there
     * @param console where the line goes
     */
    ActionPrompts(IntPredicate local, Consumer<String> console) {
        this.local = local;
        this.console = console;
    }

    @Override
    public void report(Readout previous, Readout now, DispatchSink out) {
        for (int slot = 0; slot < Readout.SLOTS; slot++) {
            PlayerReadout player = now.player(slot);
            if (player.action == previous.player(slot).action || !local.test(slot)) {
                continue;
            }
            String who = slot == 0 ? "" : "player " + (slot + 1) + " ";
            console.accept(
                    player.action == ActionKind.NONE
                            ? who + "action: nothing in reach"
                            : who + "action: " + player.action);
        }
    }
}
