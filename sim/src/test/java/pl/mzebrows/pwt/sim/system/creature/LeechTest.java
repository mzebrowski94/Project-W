package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CERTAIN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.killed;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.AlphaWorlds;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Handles;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Faction;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Leap;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Slick;
import pl.mzebrows.pwt.sim.component.Sling;
import pl.mzebrows.pwt.sim.component.Strain;
import pl.mzebrows.pwt.sim.component.Stunned;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.Look;
import pl.mzebrows.pwt.sim.def.Side;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.system.query.Drivers;

/**
 * The leech (G0083): a creature that never walks, clings to the first man it touches and, when its time on him is up,
 * kills him and goes off in venom. Left alone it goes off where it lies, when its fuse runs out.
 *
 * <p>Clinging is a grip that holds nobody still: he walks and fires with it on him, and killing it is the only way to
 * get it off - which leaves its venom on him.
 */
class LeechTest {

    private static final float MIDDLE = 50f;

    /** East of him, a few strides off. */
    private static final float A_FEW_STRIDES = 6f;

    private static final float HOLD_SECONDS = 1f;
    private static final float FUSE_SECONDS = 0.5f;
    private static final int LEECH_HEALTH = 150;

    /** Inside a slinger's range, so it winds up on the first tick it sees him. */
    private static final float SLING_RANGE = 20f;

    private static final float SLING_WIND_UP = 0.5f;
    private static final float SLING_SPEED = 30f;

    /** A boulder small enough to sit between it and a man inside its reach. */
    private static final float BOULDER = 0.6f;

    /** Long enough to walk into it from a few strides off. */
    private static final int UNTIL_HE_REACHES_IT = 60;

    private static final CommanderInput WALK_EAST = new CommanderInput(1f, 0f, MIDDLE + 100f, MIDDLE, false, false);

    /** A trooper's pace as it ships, out of the fixture commander's: a man walking rather than running past. */
    private static final float WALKING = 13.5f / AlphaWorlds.COMMANDER_SPEED;

    private static final CommanderInput WALK_EAST_SLOWLY =
            new CommanderInput(WALKING, 0f, MIDDLE + 100f, MIDDLE, false, false);
    /** Firing back the way he came, so his shots are not spent on the leech on his front. */
    private static final CommanderInput WALK_AND_FIRE = new CommanderInput(1f, 0f, MIDDLE - 100f, MIDDLE, true, false);

    /** How far off his edge it may be and still be on him: one tick's lag at a run, and some. */
    private static final float ON_HIM = 0.6f;

    @Test
    void clingsToAManWhoWalksIntoItAndGoesWhereHeGoes() {
        Simulation simulation = new Simulation(leeching());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity leech = simulation.spawnUnit(UnitKind.LEECH, MIDDLE + A_FEW_STRIDES, MIDDLE);
        walkUntilTaken(simulation, commander);
        float takenAt = at(commander).x;

        steps(simulation, ticksIn(HOLD_SECONDS * 0.5f), WALK_EAST);

        assertThat(at(commander).x - takenAt).isGreaterThan(2f);
        assertThat(apart(commander, leech)).isLessThan(touching(commander, leech) + ON_HIM);
        assertThat(commander.getComponent(Gripped.class).held()).isTrue();
    }

    /** It holds nobody still: his gun is as free as his legs. */
    @Test
    void leavesTheManItClingsToFreeToFire() {
        Simulation simulation = new Simulation(leeching());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.LEECH, MIDDLE + A_FEW_STRIDES, MIDDLE);
        walkUntilTaken(simulation, commander);

        steps(simulation, ticksIn(HOLD_SECONDS * 0.5f), WALK_AND_FIRE);

        assertThat(count(simulation, Projectile.class)).isPositive();
    }

    @Test
    void killsHimWhenHisTimeIsUpAndGoesOffInVenom() {
        Simulation simulation = new Simulation(leeching());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.LEECH, MIDDLE + A_FEW_STRIDES, MIDDLE);
        walkUntilTaken(simulation, commander);

        steps(simulation, ticksIn(HOLD_SECONDS) + 2);

        assertThat(killed(commander)).isTrue();
        assertThat(leeches(simulation)).isZero();
        assertThat(alienVenom(simulation)).isPositive();
    }

    /** Lying in wait it does not walk, but a man who walks past close by it is leapt on (from playtest, G0083). */
    @Test
    void leapsOntoAManWalkingPastCloseBy() {
        Simulation simulation = new Simulation(lyingInWait());
        float closeBy = reach(simulation) * 0.7f;
        Entity commander = simulation.spawnCommander(MIDDLE - A_FEW_STRIDES * 2f, MIDDLE + closeBy);
        simulation.spawnUnit(UnitKind.LEECH, MIDDLE, MIDDLE);

        for (int tick = 0;
                tick < UNTIL_HE_REACHES_IT * 2
                        && !commander.getComponent(Gripped.class).held();
                tick++) {
            simulation.step(WALK_EAST_SLOWLY);
        }

        assertThat(commander.getComponent(Gripped.class).held()).isTrue();
    }

    /** Aimed once, where he was (G0012): a man who runs past as it coils is gone by the time it lands. */
    @Test
    void missesAManWhoRunsPastAsItCoils() {
        Simulation simulation = new Simulation(lyingInWait());
        float closeBy = reach(simulation) * 0.7f;
        Entity commander = simulation.spawnCommander(MIDDLE - A_FEW_STRIDES * 2f, MIDDLE + closeBy);
        simulation.spawnUnit(UnitKind.LEECH, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_HE_REACHES_IT, WALK_EAST);

        assertThat(commander.getComponent(Gripped.class).held()).isFalse();
    }

    @Test
    void leavesAManPassingFurtherOffAlone() {
        Simulation simulation = new Simulation(lyingInWait());
        float furtherOff = reach(simulation) * 2f;
        Entity commander = simulation.spawnCommander(MIDDLE - A_FEW_STRIDES * 2f, MIDDLE + furtherOff);
        Entity leech = simulation.spawnUnit(UnitKind.LEECH, MIDDLE, MIDDLE);

        steps(simulation, UNTIL_HE_REACHES_IT, WALK_EAST);

        assertThat(commander.getComponent(Gripped.class).held()).isFalse();
        assertThat(at(leech).x).isEqualTo(MIDDLE);
        assertThat(at(leech).y).isEqualTo(MIDDLE);
    }

    /** Rooted on the ground, it is a body in the air like any leaper's: scenery stops it. */
    @Test
    void comesDownShortOfSceneryInItsLeap() {
        Simulation simulation = new Simulation(lyingInWait());
        float reach = reach(simulation);
        Entity commander = simulation.spawnCommander(MIDDLE + reach * 0.9f, MIDDLE);
        Entity leech = simulation.spawnUnit(UnitKind.LEECH, MIDDLE, MIDDLE);
        float boulderX = MIDDLE + reach * 0.45f;
        simulation.spawnObstacle(boulderX, MIDDLE, BOULDER, Look.OBSTACLE);

        steps(simulation, UNTIL_HE_REACHES_IT);

        assertThat(at(leech).x).isLessThan(boulderX);
        assertThat(commander.getComponent(Gripped.class).held()).isFalse();
    }

    /** Killing it frees him, and its venom is what getting it off costs: a wound, not his life. */
    @Test
    void costsAManWhoGetsItOffAWoundOfVenom() {
        Simulation simulation = new Simulation(leeching());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity leech = simulation.spawnUnit(UnitKind.LEECH, MIDDLE + A_FEW_STRIDES, MIDDLE);
        walkUntilTaken(simulation, commander);

        leech.getComponent(Damage.class).land(LEECH_HEALTH * 2, Harm.KILL);
        steps(simulation, ticksIn(HOLD_SECONDS) + 2);

        assertThat(commander.getComponent(Gripped.class).held()).isFalse();
        assertThat(killed(commander)).isFalse();
        assertThat(commander.getComponent(Vitality.class).wounds).isEqualTo(1);
    }

    @Test
    void sitsStillAndGoesOffInVenomWhenNobodyComes() {
        Simulation simulation = new Simulation(leeching());
        simulation.spawnCommander(MIDDLE - 40f, MIDDLE);
        Entity leech = simulation.spawnUnit(UnitKind.LEECH, MIDDLE, MIDDLE);

        steps(simulation, ticksIn(FUSE_SECONDS) - 2);
        assertThat(leeches(simulation)).isOne();
        assertThat(at(leech).x).isEqualTo(MIDDLE);

        steps(simulation, 4);
        assertThat(leeches(simulation)).isZero();
        assertThat(alienVenom(simulation)).isPositive();
    }

    /** Once it has hold of a man, his clock is the one that runs: it does not go off early on him. */
    @Test
    void stopsItsFuseOnceItHasHoldOfAMan() {
        Simulation simulation = new Simulation(leeching());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.LEECH, MIDDLE + A_FEW_STRIDES, MIDDLE);
        walkUntilTaken(simulation, commander);

        steps(simulation, ticksIn(FUSE_SECONDS) + 2);

        assertThat(leeches(simulation)).isOne();
        assertThat(commander.getComponent(Gripped.class).held()).isTrue();
    }

    /** One to a man: a second that touches him merely touches him, and sits on its fuse. */
    @Test
    void takesNoHoldOfAManAnotherAlreadyClingsTo() {
        Simulation simulation = new Simulation(leeching());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity first = simulation.spawnUnit(UnitKind.LEECH, MIDDLE + A_FEW_STRIDES, MIDDLE);
        simulation.spawnUnit(UnitKind.LEECH, MIDDLE + A_FEW_STRIDES * 2f, MIDDLE);
        walkUntilTaken(simulation, commander);

        steps(simulation, ticksIn(HOLD_SECONDS * 0.6f), WALK_EAST);

        assertThat(commander.getComponent(Gripped.class).by).isEqualTo(Handles.of(first));
        assertThat(killed(commander)).isFalse();
    }

    /** A man put down with one on him is let go; it drops beside him and goes off there on a fresh fuse. */
    @Test
    void dropsOffAManWhoGoesDownAndGoesOffBesideHimOnAFreshFuse() {
        Simulation simulation = new Simulation(leeching());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.LEECH, MIDDLE + A_FEW_STRIDES, MIDDLE);
        walkUntilTaken(simulation, commander);
        steps(simulation, ticksIn(FUSE_SECONDS));

        commander.getComponent(Damage.class).land(0, Harm.DOWN);
        steps(simulation, ticksIn(FUSE_SECONDS) - 3);

        assertThat(commander.getComponent(Gripped.class).held()).isFalse();
        assertThat(leeches(simulation)).isOne();

        steps(simulation, 6);
        assertThat(leeches(simulation)).isZero();
        assertThat(alienVenom(simulation)).isPositive();
    }

    /** Aimed where he stands and live the moment it lands, so a man standing still has it on him. */
    @Test
    void isSlungOntoAManStandingStillAndClingsToHim() {
        Simulation simulation = new Simulation(slingingLeeches());
        Entity commander = simulation.spawnCommander(MIDDLE + SLING_RANGE, MIDDLE);
        simulation.spawnUnit(UnitKind.LEECH_SLINGER, MIDDLE, MIDDLE);

        for (int tick = 0; tick < ticksIn(SLING_WIND_UP + 2f); tick++) {
            if (commander.getComponent(Gripped.class).held()) {
                break;
            }
            simulation.step();
        }

        assertThat(commander.getComponent(Gripped.class).held()).isTrue();
        assertThat(Drivers.gripped(commander)).isFalse();
    }

    /** In the air it passes through bodies but not scenery, as every rider does (G0074): it comes down where it stops. */
    @Test
    void comesDownWhereSceneryStopsIt() {
        Simulation simulation = new Simulation(slingingLeeches());
        simulation.spawnCommander(MIDDLE + SLING_RANGE, MIDDLE);
        simulation.spawnUnit(UnitKind.LEECH_SLINGER, MIDDLE, MIDDLE);
        float obstacleX = MIDDLE + SLING_RANGE * 0.5f;
        simulation.spawnObstacle(obstacleX, MIDDLE);

        Entity leech = untilItHasLanded(simulation);

        assertThat(at(leech).x).isLessThan(obstacleX);
    }

    /** Held still by a blow, it is still on him: it is carried, not walking. */
    @Test
    void staysOnAManWhileItIsStunned() {
        Simulation simulation = new Simulation(leeching());
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity leech = simulation.spawnUnit(UnitKind.LEECH, MIDDLE + A_FEW_STRIDES, MIDDLE);
        walkUntilTaken(simulation, commander);

        leech.getComponent(Stunned.class).daze(ticksIn(HOLD_SECONDS));
        steps(simulation, ticksIn(HOLD_SECONDS * 0.5f), WALK_EAST);

        assertThat(apart(commander, leech)).isLessThan(touching(commander, leech) + ON_HIM);
    }

    /** What a dead leech slinger still carries spills round it, and each sits on its fuse and goes off. */
    @Test
    void spillsFromADeadSlingerAndGoesOffOnItsFuse() {
        Simulation simulation = new Simulation(slingingLeeches());
        Entity slinger = simulation.spawnUnit(UnitKind.LEECH_SLINGER, MIDDLE, MIDDLE);
        int riders = slinger.getComponent(Sling.class).riders;

        slinger.getComponent(Damage.class).land(LEECH_HEALTH * 10, Harm.KILL);
        int spilled = 0;
        for (int tick = 0; tick < ticksIn(SLING_WIND_UP + 2f) && spilled == 0; tick++) {
            simulation.step();
            spilled = leeches(simulation);
        }
        assertThat(spilled).isEqualTo(riders);

        steps(simulation, ticksIn(FUSE_SECONDS) + 2);
        assertThat(leeches(simulation)).isZero();
        assertThat(alienVenom(simulation)).isPositive();
    }

    /** Now and then a trooper with one on him swings at it, and one swing has it off him. */
    @Test
    void hasATrooperItClingsToSwingItOff() {
        Simulation simulation = new Simulation(leeching().withChance(AiChance.STRUGGLE, CERTAIN));
        simulation.spawnCommander(MIDDLE - 40f, MIDDLE - 40f);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.LEECH, MIDDLE + touchingATrooper(simulation, trooper), MIDDLE);
        steps(simulation, 2);
        assertThat(trooper.getComponent(Gripped.class).held()).isTrue();

        steps(simulation, ticksIn(HOLD_SECONDS * 0.8f));

        assertThat(leeches(simulation)).isZero();
        assertThat(killed(trooper)).isFalse();
    }

    /** The leech as it ships, on a short clock and a short fuse. */
    private static SimConfig leeching() {
        return config().withUnit(
                        UnitKind.LEECH,
                        leech -> leech.withHealth(LEECH_HEALTH)
                                .withGrip(leech.grip().withHoldSeconds(HOLD_SECONDS))
                                .withFuse(leech.fuse().withSeconds(FUSE_SECONDS)));
    }

    /** The same, on a fuse long enough to still be lying there when a man comes by. */
    private static SimConfig lyingInWait() {
        return leeching()
                .withUnit(UnitKind.LEECH, leech -> leech.withFuse(leech.fuse().withSeconds(HOLD_SECONDS * 5f)));
    }

    /** A slinger whose riders are leeches, aimed onto him: nothing behind, and no moment harmless on landing. */
    private static SimConfig slingingLeeches() {
        return leeching()
                .withUnit(
                        UnitKind.LEECH_SLINGER,
                        slinger -> slinger.withContactDamage(0)
                                .withSling(slinger.sling()
                                        .withMinRange(SLING_RANGE * 0.5f)
                                        .withMaxRange(SLING_RANGE * 2f)
                                        .withWindUpSeconds(SLING_WIND_UP)
                                        .withSpeed(SLING_SPEED)
                                        .withLandBehind(0f)
                                        .withLandingSeconds(0f)));
    }

    /** Steps until the first leech slung has come down, and returns it. */
    private static Entity untilItHasLanded(Simulation simulation) {
        for (int tick = 0; tick < ticksIn(SLING_WIND_UP + 2f); tick++) {
            simulation.step();
            for (Entity unit : simulation
                    .engine()
                    .getEntitiesFor(Family.all(Strain.class, Leap.class).get())) {
                if (unit.getComponent(Strain.class).kind == UnitKind.LEECH
                        && !unit.getComponent(Leap.class).slung()) {
                    return unit;
                }
            }
        }
        throw new AssertionError("no leech came down");
    }

    /** How near a man must come for it to leap at him: its leap's trigger, as it ships. */
    private static float reach(Simulation simulation) {
        return simulation.config().unit(UnitKind.LEECH).leap().triggerRange();
    }

    /** Just inside touching distance of him: the leech's radius and his, less a little. */
    private static float touchingATrooper(Simulation simulation, Entity trooper) {
        return trooper.getComponent(Body.class).radius
                + simulation.config().unit(UnitKind.LEECH).radius()
                - 0.1f;
    }

    private static void walkUntilTaken(Simulation simulation, Entity man) {
        for (int i = 0; i < UNTIL_HE_REACHES_IT; i++) {
            if (man.getComponent(Gripped.class).held()) {
                return;
            }
            simulation.step(WALK_EAST);
        }
        throw new IllegalStateException("the leech never took hold of him");
    }

    /** How many leeches are on the field: one that has gone off is reaped, and its components with it. */
    private static int leeches(Simulation simulation) {
        int leeches = 0;
        for (Entity unit :
                simulation.engine().getEntitiesFor(Family.all(Strain.class).get())) {
            if (unit.getComponent(Strain.class).kind == UnitKind.LEECH && !killed(unit)) {
                leeches++;
            }
        }
        return leeches;
    }

    private static int alienVenom(Simulation simulation) {
        int venom = 0;
        for (Entity circle : simulation
                .engine()
                .getEntitiesFor(Family.all(Slick.class, Faction.class).get())) {
            if (circle.getComponent(Faction.class).side == Side.ALIEN) {
                venom++;
            }
        }
        return venom;
    }

    private static float apart(Entity first, Entity second) {
        float dx = at(first).x - at(second).x;
        float dy = at(first).y - at(second).y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float touching(Entity first, Entity second) {
        return first.getComponent(Body.class).radius + second.getComponent(Body.class).radius;
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }
}
