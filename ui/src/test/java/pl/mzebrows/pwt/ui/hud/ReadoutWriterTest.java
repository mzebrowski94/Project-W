package pl.mzebrows.pwt.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.Entity;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Casualty;
import pl.mzebrows.pwt.sim.component.ControlPoint;
import pl.mzebrows.pwt.sim.component.Crew;
import pl.mzebrows.pwt.sim.component.Respawning;
import pl.mzebrows.pwt.sim.component.Turret;
import pl.mzebrows.pwt.sim.def.Objective;
import pl.mzebrows.pwt.sim.def.WeaponKind;

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
        down.getComponent(Casualty.class).begin();

        Readout readout = fill(new ReadoutWriter(), world);

        assertThat(readout.troopersStanding).isEqualTo(2);
    }

    @Test
    void publishesHisBleedOutWhileHeIsDown() {
        Simulation world = world();
        Entity commander = world.spawnCommander(40f, 40f, 0);
        Casualty casualty = commander.getComponent(Casualty.class);
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
        commander.getComponent(Crew.class).station = turret.getComponent(Turret.class).id;
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

    private static Readout fill(ReadoutWriter writer, Simulation world) {
        Readout readout = new Readout();
        writer.fill(world, MISSION, "", readout);
        return readout;
    }

    private static Simulation world() {
        return new Simulation(SimConfig.defaults());
    }
}
