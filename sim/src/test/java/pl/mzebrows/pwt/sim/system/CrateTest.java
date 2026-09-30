package pl.mzebrows.pwt.sim.system;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ACTION_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.A_BLOW;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CRATE_FEWEST;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CRATE_MOST;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PickupKind;

/**
 * A crate (G0056): opened with the action key by one commander at a time, dealt what it holds on its first
 * opening and never before, holding what it was dealt from then on less what is taken, and shut on him the
 * moment he is hurt, walks off or says so.
 */
class CrateTest {

    private static final float MIDDLE = 50f;

    /** Comfortably inside arm's length once both bodies are counted. */
    private static final float BESIDE = 2f;

    private static final CommanderInput STILL = CommanderInput.IDLE;

    private static final CommanderInput ACT = STILL.acting(true);

    @Test
    void offersToOpenACrateWithinArmsLength() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);

        simulation.step(STILL);

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.OPEN_CRATE);
    }

    /** Nothing is taken from a crate by walking onto it: it is opened, not claimed. */
    @Test
    void isLeftStandingByACommanderWhoWalksOntoIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE, MIDDLE);

        steps(simulation, 5, STILL);

        assertThat(crate.getComponent(Dead.class)).isNull();
        assertThat(crate(crate).open()).isFalse();
    }

    /** Lazy: a crate nobody has opened holds nothing at all, and is dealt two or three on the press. */
    @Test
    void dealsWhatItHoldsOnItsFirstOpeningAndNotBefore() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        steps(simulation, 3, STILL);

        assertThat(crate(crate).dealt).isFalse();

        simulation.step(ACT);

        assertThat(crate(crate).dealt).isTrue();
        assertThat(crate(crate).openedBy).isZero();
        assertThat(crate(crate).count).isBetween(CRATE_FEWEST, CRATE_MOST);
    }

    @Test
    void handsTheOpenerWhatHeTakesAndShutsTheCrate() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);
        PickupKind gun = aGunIn(simulation, crate);
        int held = crate(crate).count;

        simulation.step(new PlayerInputs().set(0, ACT).takeFromCrate(0, gun));

        Grant.Arm arm = (Grant.Arm) simulation.config().pickup(gun).playerGrant();
        assertThat(commander.getComponent(Weapon.class).kind).isEqualTo(arm.weapon());
        assertThat(crate(crate).count).isEqualTo(held - 1);
        assertThat(crate(crate).open()).isFalse();
    }

    /** Contents are fixed once dealt: whoever opens it next finds what was left, never a fresh deal. */
    @Test
    void keepsWhatItWasDealtLessWhatWasTakenWhenOpenedAgain() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);
        PickupKind gun = aGunIn(simulation, crate);
        simulation.step(new PlayerInputs().set(0, ACT).takeFromCrate(0, gun));
        PickupKind[] left = contents(crate);

        simulation.step(STILL);
        simulation.step(ACT);

        assertThat(crate(crate).open()).isTrue();
        assertThat(contents(crate)).containsExactly(left);
    }

    @Test
    void takesAnEmptiedCrateOffTheField() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);

        simulation.step(ACT);
        int held = crate(crate).count;
        takeWhateverHeCan(simulation, crate);
        for (int round = 1; round < held; round++) {
            simulation.step(STILL);
            simulation.step(ACT);
            takeWhateverHeCan(simulation, crate);
        }

        assertThat(simulation.engine().getEntitiesFor(Family.all(Crate.class).get()))
                .isEmpty();
    }

    /** One commander at a time: the second is not offered it, and his press opens nothing. */
    @Test
    void letsOnlyOneCommanderHaveItOpenAtATime() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        Entity second = simulation.spawnCommander(MIDDLE + 2f * BESIDE, MIDDLE, 1);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(new PlayerInputs().set(0, ACT));

        simulation.step(new PlayerInputs().set(0, ACT).set(1, ACT));

        assertThat(crate(crate).openedBy).isZero();
        assertThat(second.getComponent(Action.class).available).isNotEqualTo(ActionKind.OPEN_CRATE);
    }

    /** Two presses on one tick go to the lower slot, whatever order the commanders are held in. */
    @Test
    void givesItToTheLowerSlotWhenTwoPressOnTheSameTick() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + 2f * BESIDE, MIDDLE, 3);
        simulation.spawnCommander(MIDDLE, MIDDLE, 1);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);

        simulation.step(new PlayerInputs().set(1, ACT).set(3, ACT));

        assertThat(crate(crate).openedBy).isEqualTo(1);
    }

    /**
     * With a second crate in reach, the higher slot is not left with nothing: he opens the one he would
     * reach next, whichever of the two commanders was spawned first.
     */
    @Test
    void givesTheHigherSlotTheNextCrateWhenTheLowerTakesTheNearestOnTheSameTick() {
        for (boolean lowerFirst : new boolean[] {true, false}) {
            Simulation simulation = simulation();
            spawnPair(simulation, lowerFirst);
            Entity nearest = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
            Entity next = simulation.spawnPickup(PickupKind.CRATE, MIDDLE - BESIDE - 0.5f, MIDDLE);
            simulation.step(new PlayerInputs());

            simulation.step(new PlayerInputs().set(0, ACT).set(1, ACT));

            assertThat(crate(nearest).openedBy)
                    .as("lower slot spawned first: %s", lowerFirst)
                    .isZero();
            assertThat(crate(next).openedBy)
                    .as("lower slot spawned first: %s", lowerFirst)
                    .isEqualTo(1);
        }
    }

    @Test
    void shutsItWhenHeClosesIt() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);

        simulation.step(new PlayerInputs().set(0, ACT).closeCrate(0));

        assertThat(crate(crate).open()).isFalse();
        assertThat(crate(crate).count).isBetween(CRATE_FEWEST, CRATE_MOST);
    }

    @Test
    void shutsItTheTickHeTakesAWound() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);

        commander.getComponent(Damage.class).add(A_BLOW, 0, Harm.WOUND);
        simulation.step(ACT);

        assertThat(commander.getComponent(Vitality.class).wounds).isEqualTo(1);
        assertThat(crate(crate).open()).isFalse();
    }

    @Test
    void shutsItWhenHeGoesDown() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);

        commander.getComponent(Damage.class).add(A_BLOW, 0, Harm.DOWN);
        simulation.step(ACT);

        assertThat(crate(crate).open()).isFalse();
    }

    @Test
    void shutsItWhenHeStepsOutOfArmsLength() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);

        commander.getComponent(Position.class).set(MIDDLE - 3f * ACTION_REACH, MIDDLE);
        simulation.step(STILL);

        assertThat(crate(crate).open()).isFalse();
    }

    @Test
    void shutsItWhenHisLinkDrops() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);

        simulation.step(new PlayerInputs().away(0));

        assertThat(crate(crate).open()).isFalse();
    }

    /** G0053 inside a crate: a medkit is worth nothing to a man with no wound, so it stays, and so does he. */
    @Test
    void turnsDownAMedkitForAManWithNoWoundAndLeavesTheCrateOpen() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);
        crate(crate).put(0, PickupKind.MEDKIT);
        int held = crate(crate).count;

        simulation.step(new PlayerInputs().set(0, ACT).takeFromCrate(0, PickupKind.MEDKIT));

        assertThat(crate(crate).count).isEqualTo(held);
        assertThat(crate(crate).open()).isTrue();
    }

    @Test
    void mendsAWoundedManWhoTakesAMedkitFromIt() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        commander.getComponent(Damage.class).add(A_BLOW, 0, Harm.WOUND);
        simulation.step(STILL);
        simulation.step(ACT);
        crate(crate).put(0, PickupKind.MEDKIT);

        simulation.step(new PlayerInputs().set(0, ACT).takeFromCrate(0, PickupKind.MEDKIT));

        assertThat(commander.getComponent(Vitality.class).wounds).isZero();
    }

    /** A take from somebody without the crate open, or of something it does not hold, changes nothing. */
    @Test
    void changesNothingForATakeFromAnybodyButItsOpener() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        Entity other = simulation.spawnCommander(MIDDLE + 2f * BESIDE, MIDDLE, 1);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(new PlayerInputs().set(0, ACT));
        PickupKind gun = aGunIn(simulation, crate);
        int held = crate(crate).count;

        simulation.step(new PlayerInputs().set(0, ACT).takeFromCrate(1, gun));

        assertThat(crate(crate).count).isEqualTo(held);
        assertThat(other.getComponent(Weapon.class).kind)
                .isNotEqualTo(((Grant.Arm) simulation.config().pickup(gun).playerGrant()).weapon());
    }

    /** While he has one open the key belongs to it: he is offered nothing else, and opens nothing else. */
    @Test
    void offersHimNothingElseWhileHeHasACrateOpen() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        Entity next = simulation.spawnPickup(PickupKind.CRATE, MIDDLE - BESIDE, MIDDLE);
        simulation.step(ACT);

        simulation.step(STILL);
        simulation.step(ACT);

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.NONE);
        assertThat(crate(next).open()).isFalse();
    }

    /**
     * The deck is a bag (G0039): the fixture holds one card of each gun and a medkit, so crates opened one
     * after another deal every card once before any twice.
     */
    @Test
    void dealsNoCardTwiceBeforeEveryCardHasComeUp() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Map<PickupKind, Integer> dealt = new EnumMap<>(PickupKind.class);
        int cards = 0;
        while (cards < DECK) {
            Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
            simulation.step(STILL);
            simulation.step(ACT);
            for (int i = 0; i < crate(crate).count && cards < DECK; i++, cards++) {
                dealt.merge(crate(crate).get(i), 1, Integer::sum);
            }
            simulation.step(new PlayerInputs().set(0, ACT).closeCrate(0));
            crate.getComponent(Position.class).set(MIDDLE, MIDDLE + 40f);
            commander.getComponent(Position.class).set(MIDDLE, MIDDLE);
        }

        assertThat(dealt)
                .hasSize(DECK)
                .allSatisfy((kind, times) -> assertThat(times).isEqualTo(1));
    }

    /** How many a crate holds comes from a bag too: two crates in a row are one of each size. */
    @Test
    void dealsEverySizeOnceBeforeRepeatingOne() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity first = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);
        simulation.step(new PlayerInputs().set(0, ACT).closeCrate(0));
        first.getComponent(Position.class).set(MIDDLE, MIDDLE + 40f);
        Entity second = simulation.spawnPickup(PickupKind.CRATE, MIDDLE - BESIDE, MIDDLE);
        commander.getComponent(Position.class).set(MIDDLE, MIDDLE);
        simulation.step(STILL);

        simulation.step(ACT);

        assertThat(new int[] {crate(first).count, crate(second).count})
                .containsExactlyInAnyOrder(CRATE_FEWEST, CRATE_MOST);
    }

    /** The fixture's deck: one card for each of four guns and the medkit. */
    private static final int DECK = 5;

    static Crate crate(Entity entity) {
        return entity.getComponent(Crate.class);
    }

    static PickupKind[] contents(Entity entity) {
        Crate crate = crate(entity);
        PickupKind[] kinds = new PickupKind[crate.count];
        for (int i = 0; i < crate.count; i++) {
            kinds[i] = crate.get(i);
        }
        return kinds;
    }

    /** Two commanders side by side across the crates' line, spawned in either order. */
    private static void spawnPair(Simulation simulation, boolean lowerFirst) {
        float lowerY = MIDDLE - 1.2f;
        float higherY = MIDDLE + 1.2f;
        if (lowerFirst) {
            simulation.spawnCommander(MIDDLE, lowerY, 0);
            simulation.spawnCommander(MIDDLE, higherY, 1);
        } else {
            simulation.spawnCommander(MIDDLE, higherY, 1);
            simulation.spawnCommander(MIDDLE, lowerY, 0);
        }
    }

    /** A gun the crate holds; puts one at the front if the deal happened to hold none. */
    private static PickupKind aGunIn(Simulation simulation, Entity entity) {
        Crate crate = crate(entity);
        for (int i = 0; i < crate.count; i++) {
            if (simulation.config().pickup(crate.get(i)).playerGrant() instanceof Grant.Arm) {
                return crate.get(i);
            }
        }
        crate.put(0, PickupKind.ARMOURY_RAILGUN);
        return PickupKind.ARMOURY_RAILGUN;
    }

    /** Takes the first thing in it he may take: a gun always, a medkit never for an unwounded man. */
    private static void takeWhateverHeCan(Simulation simulation, Entity entity) {
        simulation.step(new PlayerInputs().set(0, ACT).takeFromCrate(0, aGunIn(simulation, entity)));
    }
}
