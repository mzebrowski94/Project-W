package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import java.util.Objects;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Crate;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Upgrades;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.upgrade.AdaptationDef;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeView;

class ReadoutWriterTest {

    private static final String MISSION = "Adventure";
    private static final Offset<Float> TOLERANCE = Offset.offset(1e-4f);

    @Test
    void copiesEachCommanderIntoHisOwnSlot() {
        Simulation world = world();
        world.spawnCommander(40f, 40f, 0);
        world.spawnCommander(60f, 40f, 2);
        world.step();

        Readout readout = fill(new ReadoutWriter(), world);

        assertThat(readout.player(0).posture).isEqualTo(Posture.STANDING);
        assertThat(readout.player(2).posture).isEqualTo(Posture.STANDING);
        assertThat(readout.player(2).variant).isEqualTo(2);
        assertThat(readout.player(0).weapon).isEqualTo(WeaponKind.SIDEARM);
        assertThat(readout.mission).isEqualTo(MISSION);
    }

    @Test
    void leavesAnEmptySlotAbsent() {
        Simulation world = world();
        world.spawnCommander(40f, 40f, 0);
        world.step();

        Readout readout = fill(new ReadoutWriter(), world);

        assertThat(readout.player(1).posture).isEqualTo(Posture.ABSENT);
        assertThat(readout.player(7).posture).isEqualTo(Posture.ABSENT);
    }

    @Test
    void countsPointsTakenAndWhatTheLevelRequires() {
        Simulation world = world();
        world.spawnControlPoint(30f, 30f);
        Entity taken = world.spawnControlPoint(90f, 30f);
        world.level().require(Objective.CAPTURE_POINTS);
        taken.getComponent(ControlPoint.class).captured = true;

        Readout readout = fill(new ReadoutWriter(), world);

        assertThat(readout.pointsTaken).isEqualTo(1);
        assertThat(readout.pointsTotal).isEqualTo(2);
        assertThat(readout.requires(Objective.CAPTURE_POINTS)).isTrue();
        assertThat(readout.met(Objective.CAPTURE_POINTS)).isFalse();
        assertThat(readout.complete()).isFalse();
    }

    @Test
    void countsTroopersStandingIncludingTheOnesShotDown() {
        Simulation world = world();
        world.spawnTrooper(30f, 30f);
        Entity down = world.spawnTrooper(40f, 30f);
        down.getComponent(Vitality.class).begin();

        Readout readout = fill(new ReadoutWriter(), world);

        assertThat(readout.troopersStanding).isEqualTo(2);
    }

    /** A pip per wound he carries and per one he could, so the count is shown and never guessed (G0053). */
    @Test
    void publishesTheWoundsHeIsCarryingAndHowManyHeCould() {
        Simulation world = world();
        Entity commander = world.spawnCommander(40f, 40f, 0);
        commander.getComponent(Appearance.class).wounds = 1;

        PlayerReadout player = fill(new ReadoutWriter(), world).player(0);

        assertThat(player.wounds).isEqualTo(1);
        assertThat(player.maxWounds).isEqualTo(SimConfig.defaults().commander().maxWounds());
    }

    /**
     * His maximum is what his upgrades make it from the frame he arrives - a commander coming back is
     * spawned after the hits are weighed, and his panel is filled before the next tick weighs any.
     */
    @Test
    void publishesTheMaximumHisUpgradesGiveHimFromTheFrameHeArrives() {
        Simulation world = world();
        world.engine()
                .getEntitiesFor(Family.all(Upgrades.class).get())
                .first()
                .getComponent(Upgrades.class)
                .held
                .add(new Upgrades.Held(UpgradeKind.THICK_SKIN, Upgrades.SHARED, 1));

        world.spawnCommander(40f, 40f, 0);
        PlayerReadout player = fill(new ReadoutWriter(), world).player(0);

        assertThat(player.maxWounds).isEqualTo(SimConfig.defaults().commander().maxWounds() + 1);
    }

    @Test
    void publishesAShieldInFrontOfHimAndAStunHeIsUnder() {
        Simulation world = world();
        Entity commander = world.spawnCommander(40f, 40f, 0);
        commander.getComponent(Appearance.class).shield = 0.5f;
        commander.getComponent(Appearance.class).state = Appearance.State.STUNNED;

        PlayerReadout player = fill(new ReadoutWriter(), world).player(0);

        assertThat(player.shielded).isTrue();
        assertThat(player.stunned).isTrue();
        assertThat(player.posture).isEqualTo(Posture.STANDING);
    }

    @Test
    void publishesHisBleedOutWhileHeIsDown() {
        Simulation world = world();
        Entity commander = world.spawnCommander(40f, 40f, 0);
        Vitality casualty = commander.getComponent(Vitality.class);
        casualty.bleedOutTicks = 600;
        casualty.begin();
        casualty.remaining = 150 * Vitality.TICK;
        commander.getComponent(Appearance.class).state = Appearance.State.CASUALTY;

        PlayerReadout player = fill(new ReadoutWriter(), world).player(0);

        assertThat(player.posture).isEqualTo(Posture.CASUALTY);
        assertThat(player.bleedOut).isCloseTo(0.25f, TOLERANCE);
    }

    @Test
    void showsTheTurretsGunAndItsHeatWhileHeIsAboard() {
        Simulation world = world();
        Entity commander = world.spawnCommander(40f, 40f, 0);
        Entity turret = world.spawnTurret(42f, 40f);
        commander.getComponent(Crew.class).station = Handles.of(turret);
        commander.getComponent(Appearance.class).state = Appearance.State.ABOARD;
        turret.getComponent(Appearance.class).heat = 0.5f;
        turret.getComponent(Appearance.class).overheated = true;

        PlayerReadout player = fill(new ReadoutWriter(), world).player(0);

        assertThat(player.posture).isEqualTo(Posture.ABOARD);
        assertThat(player.weapon).isEqualTo(WeaponKind.TURRET_GUN);
        assertThat(player.heat).isCloseTo(0.5f, TOLERANCE);
        assertThat(player.overheated).isTrue();
    }

    @Test
    void countsDownToHisReturnWhileHeIsDead() {
        Simulation world = world();
        Entity pending = world.engine().createEntity();
        pending.add(world.engine().createComponent(Respawning.class).set(90, 0, 3));
        world.engine().addEntity(pending);

        PlayerReadout player = fill(new ReadoutWriter(), world).player(3);

        assertThat(player.posture).isEqualTo(Posture.RESPAWNING);
        assertThat(player.respawnSeconds).isCloseTo(1.5f, TOLERANCE);
        assertThat(player.variant).isEqualTo(3);
    }

    /** Nobody in reserve to come back as: no countdown to show, and the panel says why (G0055). */
    @Test
    void saysHeIsWaitingOnTheReserveWhileHisReturnIsUnpaid() {
        Simulation world = world();
        Entity pending = world.engine().createEntity();
        pending.add(world.engine().createComponent(Respawning.class).set(90, 0, 3));
        world.engine().addEntity(pending);

        PlayerReadout waiting = fill(new ReadoutWriter(), world).player(3);
        pending.getComponent(Respawning.class).paid = true;
        PlayerReadout paid = fill(new ReadoutWriter(), world).player(3);

        assertThat(waiting.respawnWaiting).isTrue();
        assertThat(paid.respawnWaiting).isFalse();
    }

    /** The army the top bar shows: men on the field against its most, and the reserve. */
    @Test
    void copiesTheArmyTheFieldAndTheReserve() {
        Simulation world = world();
        world.spawnTrooper(40f, 40f);
        world.spawnTrooper(44f, 40f);
        world.step();

        Readout readout = fill(new ReadoutWriter(), world);

        SimConfig config = world.config();
        assertThat(readout.squadOnField).isEqualTo(2);
        assertThat(readout.squadMost)
                .isEqualTo(config.trooper().squadSize() + config.trooper().squadGrowth());
        assertThat(readout.reserve).isEqualTo(config.reserve().start());
        assertThat(readout.failed).isFalse();
    }

    @Test
    void copiesALostLevel() {
        Simulation world = world();
        world.level().failed = true;

        assertThat(fill(new ReadoutWriter(), world).failed).isTrue();
    }

    @Test
    void copiesHisTally() {
        Simulation world = world();
        Entity commander = world.spawnCommander(40f, 40f, 2);
        commander.getComponent(Tally.class).kills = 7;
        commander.getComponent(Tally.class).goals = 1;
        commander.getComponent(Tally.class).stabilised = 2;

        PlayerReadout player = fill(new ReadoutWriter(), world).player(2);

        assertThat(player.kills).isEqualTo(7);
        assertThat(player.goals).isEqualTo(1);
        assertThat(player.stabilised).isEqualTo(2);
    }

    /** Dead and waiting, he still sees what his last life came to - until the new one starts from nothing. */
    @Test
    void keepsTheLastLifeUntilHeComesBack() {
        ReadoutWriter writer = new ReadoutWriter();
        Simulation world = world();
        Entity commander = world.spawnCommander(40f, 40f, 1);
        commander.getComponent(Tally.class).kills = 5;
        fill(writer, world);
        world.engine().removeEntity(commander);
        Entity pending = world.engine().createEntity();
        pending.add(world.engine().createComponent(Respawning.class).set(90, 0, 1));
        world.engine().addEntity(pending);

        PlayerReadout waiting = fill(writer, world).player(1);
        world.engine().removeEntity(pending);
        world.spawnCommander(40f, 40f, 1);
        PlayerReadout back = fill(writer, world).player(1);

        assertThat(waiting.posture).isEqualTo(Posture.RESPAWNING);
        assertThat(waiting.kills).isEqualTo(5);
        assertThat(back.kills).isZero();
    }

    @Test
    void keepsNothingFromAPreviousWorld() {
        ReadoutWriter writer = new ReadoutWriter();
        Simulation first = world();
        first.spawnControlPoint(30f, 30f);
        first.spawnCommander(40f, 40f, 1);
        first.step();
        Readout readout = new Readout();
        writer.fill(first, MISSION, "", readout);
        long firstSerial = readout.worldSerial;

        writer.fill(world(), MISSION, "", readout);

        assertThat(readout.worldSerial).isNotEqualTo(firstSerial);
        assertThat(readout.pointsTotal).isZero();
        assertThat(readout.player(1).posture).isEqualTo(Posture.ABSENT);
    }

    @Test
    void keepsItsSerialWhileTheWorldIsTheSame() {
        ReadoutWriter writer = new ReadoutWriter();
        Simulation world = world();
        Readout readout = new Readout();
        writer.fill(world, MISSION, "", readout);
        long serial = readout.worldSerial;
        world.step();

        writer.fill(world, MISSION, "", readout);

        assertThat(readout.worldSerial).isEqualTo(serial);
    }

    @Test
    void showsWhoHoldsADraftAndItsCardsWithTheRankEachWouldHave() {
        Simulation world = world();
        world.spawnCommander(40f, 40f, 0);
        world.spawnControlPoint(30f, 30f).getComponent(ControlPoint.class).captured = true;
        world.step();

        Readout readout = fill(new ReadoutWriter(), world);

        PlayerReadout player = readout.player(0);
        assertThat(player.drafts).isEqualTo(1);
        assertThat(player.draftSerial).isEqualTo(world.upgradeView().oldestDraftOf(0));
        assertThat(player.cardCount).isEqualTo(world.config().draft().options());
        for (int i = 0; i < player.cardCount; i++) {
            assertThat(player.cards[i]).isEqualTo(world.upgradeView().cardOf(player.draftSerial, i));
            assertThat(player.cardRanks[i]).isEqualTo(1);
        }
        assertThat(readout.player(1).drafts).isZero();
        assertThat(readout.upgradeRevision).isNotZero();
    }

    /** A slot whose link dropped reads as away, so the solo pause does not count him (G0052, D2). */
    @Test
    void marksAPlayerWhoseLinkDroppedAsAway() {
        Simulation world = world();
        world.spawnCommander(40f, 40f, 0);
        world.spawnCommander(60f, 40f, 1);
        world.step(new PlayerInputs().away(1));

        Readout readout = fill(new ReadoutWriter(), world);

        assertThat(readout.player(1).away).isTrue();
        assertThat(readout.player(0).away).isFalse();
    }

    @Test
    void listsWhatIsHeldWithItsRanksAndTheMostItCanHave() {
        Simulation world = world();
        world.spawnCommander(40f, 40f, 0);
        world.spawnControlPoint(30f, 30f).getComponent(ControlPoint.class).captured = true;
        world.step();
        int serial = world.upgradeView().oldestDraftOf(0);
        UpgradeKind taken = Objects.requireNonNull(world.upgradeView().cardOf(serial, 0));
        world.step(new PlayerInputs().pick(0, serial, taken));

        Readout readout = fill(new ReadoutWriter(), world);

        assertThat(readout.heldCount).isEqualTo(1);
        assertThat(readout.heldKind[0]).isEqualTo(taken);
        assertThat(readout.heldRanks[0]).isEqualTo(1);
        assertThat(readout.heldMaxRanks[0])
                .isEqualTo(world.config().upgrades().of(taken).ranks());
        assertThat(readout.player(0).drafts).isZero();
    }

    /** The swarm's adaptation is in the same list, told apart from the side's upgrades (D11). */
    @Test
    void marksWhatTheSwarmHoldsApartFromWhatTheSideHolds() {
        SimConfig config = SimConfig.defaults();
        Simulation world = new Simulation(config.withAdaptation(new AdaptationDef(config.tickSeconds(), 0, 0)));
        world.spawnCommander(40f, 40f, 0);
        world.step();

        Readout readout = fill(new ReadoutWriter(), world);

        assertThat(readout.heldCount).isEqualTo(1);
        assertThat(readout.heldKind[0]).isEqualTo(UpgradeKind.RAPID_SPIT);
        assertThat(readout.heldAlien[0]).isTrue();
        assertThat(readout.heldSlot[0]).isEqualTo(UpgradeView.SHARED);
    }

    @Test
    void copiesTheDraftsAndWhatIsHeldIntoAnotherReadout() {
        Readout from = new Readout();
        from.heldCount = 1;
        from.heldKind[0] = UpgradeKind.MARKSMEN;
        from.heldSlot[0] = 2;
        from.heldRanks[0] = 2;
        from.player(2).drafts = 3;
        from.player(2).cardCount = 1;
        from.player(2).cards[0] = UpgradeKind.GRENADIER;
        Readout to = new Readout();

        to.copyFrom(from);

        assertThat(to.heldKind[0]).isEqualTo(UpgradeKind.MARKSMEN);
        assertThat(to.heldSlot[0]).isEqualTo(2);
        assertThat(to.heldRanks[0]).isEqualTo(2);
        assertThat(to.player(2).drafts).isEqualTo(3);
        assertThat(to.player(2).cards[0]).isEqualTo(UpgradeKind.GRENADIER);
    }

    /**
     * The crate a commander has open is on his readout (G0056): its cards in order, and which of them he
     * would be turned down for - a medkit, to a man with no wound.
     */
    @Test
    void listsTheCardsOfTheCrateHeHasOpenAndWhichHeWouldBeTurnedDownFor() {
        Simulation world = world();
        world.spawnCommander(40f, 40f, 0);
        Entity crate = world.spawnPickup(PickupKind.CRATE, 42f, 40f);
        world.step(CommanderInput.IDLE.acting(true));
        Crate held = crate.getComponent(Crate.class);
        held.put(0, PickupKind.MEDKIT);
        held.put(1, PickupKind.ARMOURY_FLAMER);

        Readout readout = fill(new ReadoutWriter(), world);

        PlayerReadout player = readout.player(0);
        assertThat(player.crateCount).isEqualTo(held.count);
        assertThat(player.crate[0]).isEqualTo(PickupKind.MEDKIT);
        assertThat(player.crate[1]).isEqualTo(PickupKind.ARMOURY_FLAMER);
        assertThat(player.crateRefused[0]).isTrue();
        assertThat(player.crateRefused[1]).isFalse();
        assertThat(readout.player(1).crateCount).isZero();
    }

    private static Readout fill(ReadoutWriter writer, Simulation world) {
        Readout readout = new Readout();
        writer.fill(world, MISSION, "", readout);
        return readout;
    }

    private static Simulation world() {
        return new Simulation(SimConfig.defaults());
    }
}
