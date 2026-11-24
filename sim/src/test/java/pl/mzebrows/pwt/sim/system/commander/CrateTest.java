package pl.mzebrows.pwt.sim.system.commander;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ACTION_REACH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CRATE_FEWEST;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CRATE_MOST;
import static pl.mzebrows.pwt.sim.AlphaWorlds.GRENADE_MOST;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Action;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dead;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.ActionKind;
import pl.mzebrows.pwt.sim.def.ArmouryDef;
import pl.mzebrows.pwt.sim.def.Grant;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.system.upgrade.Effect;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeDef;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

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

    /** One throw, away from the crate, so he is a grenade short of his most. */
    private static final CommanderInput THROWING_AWAY =
            new CommanderInput(0f, 0f, MIDDLE - 10f, MIDDLE, false, false, true, false, false, false);

    @Test
    void offersToOpenACrateWithinArmsLength() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);

        simulation.step(STILL);

        assertThat(commander.getComponent(Action.class).available).isEqualTo(ActionKind.OPEN_CRATE);
    }

    @Test
    void namesTheCrateBesideHimAsTheSubject() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);

        simulation.step(STILL);

        assertThat(commander.getComponent(Action.class).subject).isEqualTo(Handles.of(crate));
    }

    /** A crate he has open has the key's next press, in the top bar - nothing in the world is on offer. */
    @Test
    void namesNobodyWhileHeHasTheCrateOpen() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);

        simulation.step(ACT);
        simulation.step(STILL);

        assertThat(crate(crate).open()).as("open, and still within his reach").isTrue();
        assertThat(commander.getComponent(Action.class).subject).isEqualTo(Handles.NOBODY);
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

        commander.getComponent(Damage.class).land(0, Harm.WOUND);
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

        commander.getComponent(Damage.class).land(0, Harm.DOWN);
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
        commander.getComponent(Damage.class).land(0, Harm.WOUND);
        simulation.step(STILL);
        simulation.step(ACT);
        crate(crate).put(0, PickupKind.MEDKIT);

        simulation.step(new PlayerInputs().set(0, ACT).takeFromCrate(0, PickupKind.MEDKIT));

        assertThat(commander.getComponent(Vitality.class).wounds).isZero();
    }

    /** A grenade cache fills him to his most (G0077), whatever he has thrown. */
    @Test
    void fillsHisGrenadesWhenHeTakesAGrenadeCacheFromIt() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(THROWING_AWAY);
        simulation.step(ACT);
        crate(crate).put(0, PickupKind.ARMOURY_GRENADES);

        simulation.step(new PlayerInputs().set(0, ACT).takeFromCrate(0, PickupKind.ARMOURY_GRENADES));

        assertThat(simulation.grenades(commander)).isEqualTo(GRENADE_MOST);
    }

    /** The medkit's rule for a man with no wound (G0053), asked of a man with every grenade he can carry (G0077). */
    @Test
    void turnsDownAGrenadeCacheForAManCarryingHisMostAndLeavesTheCrateOpen() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(ACT);
        crate(crate).put(0, PickupKind.ARMOURY_GRENADES);
        int held = crate(crate).count;

        simulation.step(new PlayerInputs().set(0, ACT).takeFromCrate(0, PickupKind.ARMOURY_GRENADES));

        assertThat(crate(crate).count).isEqualTo(held);
        assertThat(crate(crate).open()).isTrue();
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

    /** G0110: a drawn card of a kind the crate already holds goes back, however stacked the deck is. */
    @Test
    void neverShowsOneKindTwiceInACrateOverManyDeals() {
        Simulation simulation = new Simulation(stacked(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        AlphaWorlds.hold(simulation, UpgradeKind.EXPLOSIVES, 1);
        AlphaWorlds.hold(simulation, UpgradeKind.FULL_CRATES, 1);

        for (int round = 0; round < DEALS; round++) {
            assertThat(openOne(simulation, commander)).as("crate %d", round).doesNotHaveDuplicates();
        }
    }

    /** A deck with fewer kinds than the crate's size deals a smaller crate rather than a second of one. */
    @Test
    void dealsASmallerCrateFromADeckShortOfKinds() {
        SimConfig config = config();
        for (PickupKind kind :
                new PickupKind[] {PickupKind.ARMOURY_RAILGUN, PickupKind.ARMOURY_SHOTGUN, PickupKind.ARMOURY_LASER}) {
            config = config.withPickups(config.pickups().map(kind, pickup -> pickup.withArmoury(ArmouryDef.off())));
        }
        Simulation simulation = new Simulation(config);
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        AlphaWorlds.hold(simulation, UpgradeKind.FULL_CRATES, 1);

        List<PickupKind> dealt = openOne(simulation, commander);

        assertThat(dealt).containsExactlyInAnyOrder(PickupKind.ARMOURY_FLAMER, PickupKind.MEDKIT);
    }

    /** A stocked card is swapped in only where the deal held none: never a second (G0110). */
    @Test
    void addsNoSecondCardOfTheKindAStockNames() {
        SimConfig config = stacked(config());
        config = config.withUpgrades(config.upgrades()
                .map(
                        UpgradeKind.FIREPOWER,
                        firepower -> new UpgradeDef(
                                Side.PLAYER,
                                0,
                                1,
                                1,
                                null,
                                0,
                                List.of(new Effect.Stock(PickupKind.ARMOURY_FLAMER, 1)))));
        Simulation simulation = new Simulation(config);
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        AlphaWorlds.hold(simulation, UpgradeKind.EXPLOSIVES, 1);
        AlphaWorlds.hold(simulation, UpgradeKind.FIREPOWER, 1);

        for (int round = 0; round < DEALS; round++) {
            assertThat(openOne(simulation, commander))
                    .as("crate %d", round)
                    .filteredOn(kind -> kind == PickupKind.ARMOURY_FLAMER)
                    .hasSize(1);
        }
    }

    /** G0110: Looter's holder finds one card more in a crate on his first opening of it. */
    @Test
    void holdsOneCardMoreOnALooterHoldersFirstOpening() {
        Simulation plain = simulation();
        Simulation looting = simulation();
        Entity crate = firstOpening(plain, false);

        Entity looted = firstOpening(looting, true);

        assertThat(crate(looted).count).isEqualTo(crate(crate).count + 1);
        assertThat(contents(looted)).doesNotHaveDuplicates();
    }

    @Test
    void givesNothingMoreOnTheLooterHoldersSecondOpening() {
        Simulation simulation = simulation();
        Entity crate = firstOpening(simulation, true);
        int held = crate(crate).count;
        simulation.step(new PlayerInputs().set(0, ACT).closeCrate(0));

        simulation.step(STILL);
        simulation.step(ACT);

        assertThat(crate(crate).open()).isTrue();
        assertThat(crate(crate).count).isEqualTo(held);
    }

    /** Once per crate, ever: whoever's Looter set it off, another's opening later adds nothing. */
    @Test
    void givesNothingMoreOnAnotherLooterHoldersLaterOpening() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + 2f * BESIDE, MIDDLE, 1);
        hold(simulation, UpgradeKind.LOOTER, 1);
        Entity crate = firstOpening(simulation, true);
        int held = crate(crate).count;

        reopenAsTheSecond(simulation);

        assertThat(crate(crate).openedBy).isEqualTo(1);
        assertThat(crate(crate).count).isEqualTo(held);
    }

    /** A crate somebody without Looter opened first still gives its one card more, of a kind new to it. */
    @Test
    void addsACardOfANewKindToACrateANonLooterOpenedFirst() {
        Simulation simulation = simulation();
        simulation.spawnCommander(MIDDLE + 2f * BESIDE, MIDDLE, 1);
        hold(simulation, UpgradeKind.LOOTER, 1);
        Entity crate = firstOpening(simulation, false);
        PickupKind[] before = contents(crate);

        reopenAsTheSecond(simulation);

        assertThat(crate(crate).openedBy).isEqualTo(1);
        assertThat(contents(crate))
                .hasSize(before.length + 1)
                .startsWith(before)
                .doesNotHaveDuplicates();
        assertThat(crate(crate).looted).isTrue();
    }

    @Test
    void neverHoldsMoreThanACrateCanUnderLooter() {
        Simulation simulation = simulation();
        AlphaWorlds.hold(simulation, UpgradeKind.FULL_CRATES, 1);

        Entity crate = firstOpening(simulation, true);

        assertThat(crate(crate).count).isEqualTo(SimConfig.CrateConfig.CAPACITY);
        assertThat(crate(crate).looted).isTrue();
    }

    /** The fixture's deck: one card for each of four guns and the medkit. */
    private static final int DECK = 5;

    /** Crates enough for a stacked deck to come round several times. */
    private static final int DEALS = 40;

    /** Cards a stacking Explosives adds to the flamer's and the medkit's, so a deal draws either again and again. */
    private static final int STACKED = 10;

    /** The fixture with Explosives turned into a deal that stacks two kinds of the deck high. */
    private static SimConfig stacked(SimConfig config) {
        return config.withUpgrades(config.upgrades()
                .map(
                        UpgradeKind.EXPLOSIVES,
                        explosives -> new UpgradeDef(
                                Side.PLAYER,
                                0,
                                1,
                                1,
                                null,
                                0,
                                List.of(
                                        new Effect.Deal(PickupKind.ARMOURY_FLAMER, STACKED),
                                        new Effect.Deal(PickupKind.MEDKIT, STACKED)))));
    }

    /** Lays a crate beside him, opens it, says what it was dealt, and shuts it and puts it out of his way. */
    private static List<PickupKind> openOne(Simulation simulation, Entity commander) {
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        simulation.step(STILL);
        simulation.step(ACT);
        List<PickupKind> dealt = List.of(contents(crate));
        simulation.step(new PlayerInputs().set(0, ACT).closeCrate(0));
        crate.getComponent(Position.class).set(MIDDLE, MIDDLE + 40f);
        commander.getComponent(Position.class).set(MIDDLE, MIDDLE);
        return dealt;
    }

    /** Slot zero beside a crate, holding Looter or not, opens it for the first time. */
    private static Entity firstOpening(Simulation simulation, boolean looter) {
        simulation.spawnCommander(MIDDLE, MIDDLE, 0);
        Entity crate = simulation.spawnPickup(PickupKind.CRATE, MIDDLE + BESIDE, MIDDLE);
        if (looter) {
            simulation
                    .engine()
                    .getEntitiesFor(Family.all(Upgrades.class).get())
                    .first()
                    .getComponent(Upgrades.class)
                    .held
                    .add(new Upgrades.Held(UpgradeKind.LOOTER, 0, 1));
        }
        simulation.step(STILL);
        simulation.step(ACT);
        return crate;
    }

    /** Slot one's personal rank of {@code kind}. */
    private static void hold(Simulation simulation, UpgradeKind kind, int ranks) {
        simulation
                .engine()
                .getEntitiesFor(Family.all(Upgrades.class).get())
                .first()
                .getComponent(Upgrades.class)
                .held
                .add(new Upgrades.Held(kind, 1, ranks));
    }

    /** Slot zero shuts the crate he has open, and slot one, beside it, opens it. */
    private static void reopenAsTheSecond(Simulation simulation) {
        simulation.step(new PlayerInputs().set(0, ACT).closeCrate(0));
        simulation.step(new PlayerInputs());
        simulation.step(new PlayerInputs().set(1, ACT));
    }

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
