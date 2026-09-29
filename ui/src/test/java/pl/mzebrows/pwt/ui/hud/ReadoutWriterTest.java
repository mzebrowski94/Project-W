package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import java.util.Objects;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.PlayerInputs;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.Tally;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.upgrade.UpgradeKind;

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
        casualty.bleedOutSeconds = 10f;
        casualty.begin();
        casualty.remainingSeconds = 2.5f;
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
        pending.add(world.engine().createComponent(Respawning.class).set(90, 0f, 0f, 0, 3));
        world.engine().addEntity(pending);

        PlayerReadout player = fill(new ReadoutWriter(), world).player(3);

        assertThat(player.posture).isEqualTo(Posture.RESPAWNING);
        assertThat(player.respawnSeconds).isCloseTo(1.5f, TOLERANCE);
        assertThat(player.variant).isEqualTo(3);
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
        pending.add(world.engine().createComponent(Respawning.class).set(90, 0f, 0f, 0, 1));
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

    private static Readout fill(ReadoutWriter writer, Simulation world) {
        Readout readout = new Readout();
        writer.fill(world, MISSION, "", readout);
        return readout;
    }

    private static Simulation world() {
        return new Simulation(SimConfig.defaults());
    }
}
