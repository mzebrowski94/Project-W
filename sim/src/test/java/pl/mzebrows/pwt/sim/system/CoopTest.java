package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.COMMANDER_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.LEASH_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Players;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.def.Order;

/**
 * Four commanders on one field, and the three rules that stop that being four separate games.
 *
 * <p><strong>A slot drives its own commander and nobody else's.</strong> One input record used to
 * reach every commander there was, which was correct while there was one.
 *
 * <p><strong>The louder voice is the higher slot.</strong> Two commanders can shout at the same
 * trooper on the same tick and one of them has to win; which one must not depend on the order Ashley
 * happens to hold the family in, because that order changes every time somebody dies and comes back.
 *
 * <p><strong>Nobody may walk out of the fight.</strong> Pillar 4 says there is one fight and you can
 * always see it, and one shared view cannot frame two players who are free to walk apart for ever.
 * The leash is a distance in world units, deliberately not the camera's - zoom is a rendering
 * setting and must never become extra rope.
 */
class CoopTest {

    private static final float MIDDLE = 50f;
    private static final Offset<Float> TOLERANCE = Offset.offset(1e-3f);

    /** Long enough that an unleashed pair would be off opposite edges of the arena. */
    private static final int A_WHILE = 120;

    @Test
    void drivesEachCommanderFromItsOwnSlot() {
        Simulation simulation = simulation();
        Entity first = simulation.spawnCommander(MIDDLE - 5f, MIDDLE, 0);
        Entity second = simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);

        simulation.step(inputs(walking(0f, 1f), walking(0f, -1f)));

        assertThat(at(first).y).isGreaterThan(MIDDLE);
        assertThat(at(second).y).isLessThan(MIDDLE);
    }

    @Test
    void leavesASlotNobodyIsSittingInIdle() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE, 1);

        simulation.step(new PlayerInputs().set(0, walking(1f, 0f)));

        assertThat(at(commander).x).isCloseTo(MIDDLE, TOLERANCE);
    }

    /**
     * A stick has a direction and no idea where the commander is standing, so the aim it sends is an
     * offset from him. Two commanders pushing their sticks the same way face the same way, wherever
     * on the field they are - which an absolute aim point cannot express for more than one of them.
     */
    @Test
    void aimsRelativeToWhicheverCommanderIsAiming() {
        Simulation simulation = simulation();
        Entity first = simulation.spawnCommander(20f, MIDDLE, 0);
        Entity second = simulation.spawnCommander(80f, MIDDLE, 1);

        simulation.step(inputs(pointing(1f, 0f), pointing(1f, 0f)));

        assertFacing(first, 1f, 0f);
        assertFacing(second, 1f, 0f);
    }

    @Test
    void letsTheHigherSlotsOrderStandWhenTwoCommandersSpeakAtOnce() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE - 2f, MIDDLE, 0);
        simulation.spawnCommander(MIDDLE + 2f, MIDDLE, 1);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE + 2f);

        simulation.step(inputs(ordering(Order.DEFENCE), ordering(Order.FOLLOW_ME)));

        assertThat(orderOf(trooper)).isEqualTo(Order.FOLLOW_ME);
    }

    /** The mirror, so the rule is read as "the higher slot" rather than as "follow me wins". */
    @Test
    void letsTheHigherSlotsOrderStandWhicheverOrderItIs() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE - 2f, MIDDLE, 0);
        simulation.spawnCommander(MIDDLE + 2f, MIDDLE, 1);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE + 2f);

        simulation.step(inputs(ordering(Order.FOLLOW_ME), ordering(Order.DEFENCE)));

        assertThat(orderOf(trooper)).isEqualTo(Order.DEFENCE);
    }

    @Test
    void refusesToLetTwoCommandersWalkFurtherApartThanTheLeash() {
        Simulation simulation = simulation();
        Entity first = simulation.spawnCommander(MIDDLE - 5f, MIDDLE, 0);
        Entity second = simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);

        steps(simulation, A_WHILE, inputs(walking(-1f, 0f), walking(1f, 0f)));

        assertThat(at(second).x - at(first).x).isCloseTo(2f * LEASH_RADIUS, TOLERANCE);
    }

    /** It refuses the step out and never takes one in: a man at the edge walks home unaided. */
    @Test
    void letsHimWalkBackTowardsTheGroupFromTheEndOfTheLeash() {

        Simulation simulation = simulation();
        Entity first = simulation.spawnCommander(MIDDLE - 5f, MIDDLE, 0);
        Entity second = simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);
        steps(simulation, A_WHILE, inputs(walking(-1f, 0f), walking(1f, 0f)));
        float stretched = at(second).x - at(first).x;

        steps(simulation, 30, inputs(CommanderInput.IDLE, walking(-1f, 0f)));

        assertThat(at(second).x - at(first).x).isLessThan(stretched);
    }

    @Test
    void leavesASoleCommanderFreeToWalkWhereverHeLikes() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE, 0);

        steps(simulation, A_WHILE, inputs(walking(1f, 0f)));

        assertThat(at(commander).x).isGreaterThan(MIDDLE + 2f * LEASH_RADIUS);
    }

    /**
     * Coming back at the edge of the arena is a long walk in single player and a dead player watching
     * his friends fight in co-op. He comes back on whoever is still standing instead.
     */
    @Test
    void bringsHimBackBesideACommanderWhoIsStillOnHisFeet() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE - 5f, MIDDLE, 0);
        Entity second = simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);

        kill(second);
        steps(simulation, ticksIn(DEATH_THROES + RESPAWN_DELAY) + 2, inputs(CommanderInput.IDLE));

        assertThat(at(inSlot(simulation, 1)).x).isCloseTo(MIDDLE - 5f, Offset.offset(3f));
    }

    @Test
    void bringsHimBackIntoTheSlotHeLeft() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE - 5f, MIDDLE, 0);
        Entity second = simulation.spawnCommander(MIDDLE + 5f, MIDDLE, 1);

        kill(second);
        steps(simulation, ticksIn(DEATH_THROES + RESPAWN_DELAY) + 2, inputs(CommanderInput.IDLE));

        assertThat(inSlot(simulation, 1)).isNotNull();
        assertThat(inSlot(simulation, 0)).isNotNull();
    }

    private static PlayerInputs inputs(CommanderInput... bySlot) {
        PlayerInputs inputs = new PlayerInputs();
        for (int slot = 0; slot < bySlot.length; slot++) {
            inputs.set(slot, bySlot[slot]);
        }
        return inputs;
    }

    private static CommanderInput walking(float x, float y) {
        return new CommanderInput(x, y, 0f, 0f, false, false);
    }

    private static CommanderInput pointing(float x, float y) {
        return new CommanderInput(0f, 0f, x, y, false, false).aimingRelatively();
    }

    private static CommanderInput ordering(Order order) {
        return CommanderInput.IDLE.ordering(order);
    }

    private static void steps(Simulation simulation, int count, PlayerInputs inputs) {
        for (int i = 0; i < count; i++) {
            simulation.step(inputs);
        }
    }

    private static Entity inSlot(Simulation simulation, int slot) {
        return Players.inSlot(
                simulation.engine().getEntitiesFor(Family.all(Commander.class).get()), slot);
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }

    private static Order orderOf(Entity trooper) {
        return trooper.getComponent(Trooper.class).order;
    }

    private static void assertFacing(Entity commander, float x, float y) {
        Appearance appearance = commander.getComponent(Appearance.class);
        assertThat(appearance.facingX).isCloseTo(x, TOLERANCE);
        assertThat(appearance.facingY).isCloseTo(y, TOLERANCE);
    }

    private static void kill(Entity commander) {
        commander.getComponent(Damage.class).add(COMMANDER_HEALTH, 0f);
        commander.getComponent(Invulnerable.class).remainingSeconds = 0f;
    }
}
