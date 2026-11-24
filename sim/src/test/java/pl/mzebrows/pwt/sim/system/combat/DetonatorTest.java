package pl.mzebrows.pwt.sim.system.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ARENA_TILES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BLAST_DAMAGE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DOORWAY_MIDDLE_X;
import static pl.mzebrows.pwt.sim.AlphaWorlds.TILE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.WALL_BOTTOM;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ground;
import static pl.mzebrows.pwt.sim.AlphaWorlds.harmless;
import static pl.mzebrows.pwt.sim.AlphaWorlds.hold;
import static pl.mzebrows.pwt.sim.AlphaWorlds.on;
import static pl.mzebrows.pwt.sim.AlphaWorlds.simulation;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.walledAtADoorway;
import static pl.mzebrows.pwt.sim.AlphaWorlds.watching;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.map.Terrain;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Life;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Blast;
import pl.mzebrows.pwt.sim.component.Body;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Door;
import pl.mzebrows.pwt.sim.component.Heat;
import pl.mzebrows.pwt.sim.component.Limpet;
import pl.mzebrows.pwt.sim.component.Perception;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Weapon;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.LimpetDef;
import pl.mzebrows.pwt.sim.def.WeaponDef;
import pl.mzebrows.pwt.sim.def.WeaponKind;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeKind;

/**
 * The detonator (G0100): one limpet out, thrown by a fire press and set off by the next; it sticks to the first
 * thing it touches, calibrates, and goes off small or big by whether it has.
 */
class DetonatorTest {

    private static final float X = 30f;

    private static final float Y = 50f;

    /** Fire held, aimed along the lane to his right. */
    private static final CommanderInput PRESS = new CommanderInput(0f, 0f, X + 30f, Y, true, false);

    private static final CommanderInput RELEASE = PRESS.notFiring();

    /** Where something stands for the limpet to reach: well inside its throw, well clear of his muzzle. */
    private static final float IN_REACH = X + 12f;

    /** How close to touching edges counts as stuck on one. */
    private static final float TOUCHING = 0.05f;

    /** A column of rock a short throw to his right, inside the limpet's reach. */
    private static final int WALL_COL = (int) ((X + 10f) / TILE);

    // ---- one limpet, one key ----

    @Test
    void throwsOneLimpetOnAPressAndNoSecondWhileTheTriggerIsHeld() {
        Simulation simulation = simulation();
        holding(simulation);

        steps(simulation, ticksIn(1f), PRESS);

        assertThat(count(simulation, Limpet.class)).isOne();
        assertThat(count(simulation, Blast.class)).isZero();
    }

    @Test
    void setsItsLimpetOffOnAFreshPress() {
        Simulation simulation = simulation();
        holding(simulation);

        simulation.step(PRESS);
        steps(simulation, ticksIn(0.3f), RELEASE);
        simulation.step(PRESS);
        steps(simulation, 2, RELEASE);

        assertWentOff(simulation);
    }

    // ---- its cry ----

    @Test
    void callsWhatIsInEarshotToWhereItStuckAndNoOneFurther() {
        Simulation simulation = watching();
        holding(simulation);
        Entity near = simulation.spawnEnemy(X + 34f, Y + 12f);
        Entity far = simulation.spawnEnemy(5f, Y);

        simulation.step(PRESS);
        steps(simulation, ticksIn(2f), RELEASE);

        Entity limpet = limpet(simulation);
        assertThat(limpet).isNotNull();
        Position landed = limpet.getComponent(Position.class);
        assertThat(near.getComponent(Perception.class).provokedX).isCloseTo(landed.x, within(TOUCHING));
        assertThat(near.getComponent(Perception.class).roused).isTrue();
        assertThat(far.getComponent(Perception.class).provoked).isFalse();
    }

    // ---- where it sticks ----

    @Test
    void ridesTheEdgeOfTheAlienItSticksTo() {
        Simulation simulation = harmless();
        holding(simulation);
        Entity alien = simulation.spawnEnemy(IN_REACH, Y);

        simulation.step(PRESS);
        steps(simulation, ticksIn(1f), RELEASE);

        Entity limpet = limpet(simulation);
        assertThat(limpet).isNotNull();
        assertThat(apart(limpet, alien)).isCloseTo(radius(limpet) + radius(alien), within(TOUCHING));
        assertThat(limpet.getComponent(Limpet.class).stuck).isTrue();
    }

    @Test
    void sticksToTheGroundWhereItsReachRunsOut() {
        Simulation simulation = simulation();
        holding(simulation);
        float reach = simulation.config().weapon(WeaponKind.DETONATOR).projectileRange();

        simulation.step(PRESS);
        steps(simulation, ticksIn(2f), RELEASE);

        Entity limpet = limpet(simulation);
        assertThat(limpet).isNotNull();
        assertThat(limpet.getComponent(Limpet.class).stuck).isTrue();
        assertThat(limpet.getComponent(Position.class).x - X).isBetween(reach * 0.9f, reach * 1.1f);
    }

    // ---- two circles ----

    @Test
    void goesOffSmallBeforeItHasCalibratedAndBigAfter() {
        WeaponDef gun = simulation().config().weapon(WeaponKind.DETONATOR);
        LimpetDef limpet = gun.limpet();
        // Its whole flight first: it calibrates only once it has landed.
        float landed = gun.projectileRange() / gun.projectileSpeed();

        Entity small = blastAfter(landed + limpet.calibrateSeconds() * 0.5f);
        Entity big = blastAfter(landed + limpet.calibrateSeconds() + 0.5f);

        assertThat(radius(small)).isEqualTo(limpet.blastRadius());
        assertThat(small.getComponent(Blast.class).harm).isEqualTo(Harm.WOUND);
        assertThat(radius(big)).isEqualTo(limpet.calibratedBlastRadius());
        assertThat(big.getComponent(Blast.class).harm).isEqualTo(Harm.DOWN);
    }

    /** Both its blasts are the gun's: Firepower's two ranks land each 30 % harder (G0127). */
    @Test
    void landsBothItsBlastsAsMuchHarderAsItsGunUnderFirepower() {
        WeaponDef gun = simulation().config().weapon(WeaponKind.DETONATOR);
        float landed = gun.projectileRange() / gun.projectileSpeed();
        float calibrated = landed + gun.limpet().calibrateSeconds() + 0.5f;

        assertThat(damage(blastAfter(0.2f, 0))).isEqualTo(BLAST_DAMAGE);
        assertThat(damage(blastAfter(0.2f, 2))).isEqualTo(BLAST_DAMAGE * 130 / 100);
        assertThat(damage(blastAfter(calibrated, 2))).isEqualTo(BLAST_DAMAGE * 130 / 100);
    }

    @Test
    void goesOffSmallInTheAir() {
        LimpetDef limpet = simulation().config().weapon(WeaponKind.DETONATOR).limpet();

        assertThat(radius(blastAfter(0.2f))).isEqualTo(limpet.blastRadius());
    }

    // ---- going off by dying ----

    @Test
    void goesOffWhenItsOwnerTakesAnotherGun() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation);

        simulation.step(PRESS);
        steps(simulation, ticksIn(2f), RELEASE);
        arm(simulation, commander, WeaponKind.SIDEARM);
        steps(simulation, 2, RELEASE);

        assertWentOff(simulation);
    }

    @Test
    void goesOffWhenItsOwnerIsKilled() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation);

        simulation.step(PRESS);
        steps(simulation, ticksIn(2f), RELEASE);
        commander.getComponent(Damage.class).land(0, Harm.KILL);
        steps(simulation, 3, RELEASE);

        assertWentOff(simulation);
    }

    /** A man down cannot fire, so he cannot set it off - but it is still his, and waits for him (G0100). */
    @Test
    void keepsItWhileItsOwnerIsDown() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation);

        simulation.step(PRESS);
        steps(simulation, ticksIn(2f), RELEASE);
        commander.getComponent(Damage.class).land(0, Harm.DOWN);
        // The blow lands after his hands have had this tick's press, so one tick for him to go down.
        simulation.step(RELEASE);
        // Short of his bleed-out, and pressing all the while.
        steps(simulation, ticksIn(0.5f), PRESS);

        assertThat(Life.casualty(commander)).isTrue();
        assertThat(count(simulation, Limpet.class)).isOne();
        assertThat(count(simulation, Blast.class)).isZero();
    }

    @Test
    void goesOffWhenWhatItRidesIsKilled() {
        Simulation simulation = harmless();
        holding(simulation);
        Entity alien = simulation.spawnEnemy(IN_REACH, Y);

        simulation.step(PRESS);
        steps(simulation, ticksIn(1f), RELEASE);
        alien.getComponent(Damage.class).land(100_000, Harm.WOUND);
        steps(simulation, 3, RELEASE);

        assertWentOff(simulation);
    }

    @Test
    void goesOffWhenTheDoorItIsStuckToIsOpened() {
        Simulation simulation = on(walledAtADoorway());
        Entity door = simulation.spawnDoor(DOORWAY);
        Entity commander = simulation.spawnCommander(DOORWAY_MIDDLE_X, WALL_BOTTOM - 10f);
        arm(simulation, commander, WeaponKind.DETONATOR);
        CommanderInput up = new CommanderInput(0f, 0f, DOORWAY_MIDDLE_X, WALL_BOTTOM + 30f, true, false);

        simulation.step(up);
        steps(simulation, ticksIn(1f), up.notFiring());
        Entity limpet = limpet(simulation);
        assertThat(limpet).isNotNull();
        assertThat(limpet.getComponent(Limpet.class).onWall).isTrue();
        assertThat(limpet.getComponent(Position.class).y).isLessThan(WALL_BOTTOM);

        door.getComponent(Door.class).open = true;
        steps(simulation, 3, up.notFiring());

        assertWentOff(simulation);
    }

    /** It explodes by dying, so a blast beside it sets it off as one sets off a mine (G0018). */
    @Test
    void goesOffWhenABlastCatchesIt() {
        Simulation simulation = simulation();
        holding(simulation);

        simulation.step(PRESS);
        steps(simulation, ticksIn(2f), RELEASE);
        Position at = limpet(simulation).getComponent(Position.class);
        simulation.spawnBlast(at.x, at.y + 1f, 2f);
        steps(simulation, 3, RELEASE);

        assertThat(count(simulation, Limpet.class)).isZero();
        assertThat(count(simulation, Blast.class)).isEqualTo(2);
    }

    @Test
    void waitsOnceCalibratedAndNeverGoesOffByItself() {
        Simulation simulation = simulation();
        holding(simulation);

        simulation.step(PRESS);
        steps(simulation, ticksIn(10f), RELEASE);

        Entity limpet = limpet(simulation);
        assertThat(limpet).isNotNull();
        assertThat(limpet.getComponent(Limpet.class).calibrated).isTrue();
        assertThat(count(simulation, Blast.class)).isZero();
    }

    // ---- what passes through ----

    @Test
    void passesThroughAManOnItsOwnSide() {
        Simulation simulation = simulation();
        holding(simulation);
        Entity trooper = simulation.spawnTrooper(X + 6f, Y);

        simulation.step(PRESS);
        steps(simulation, ticksIn(0.4f), RELEASE);

        Limpet limpet = limpet(simulation).getComponent(Limpet.class);
        assertThat(limpet.stuck).isFalse();
        assertThat(limpet(simulation).getComponent(Position.class).x)
                .isGreaterThan(trooper.getComponent(Position.class).x);
    }

    @Test
    void sticksToSceneryItFliesInto() {
        Simulation simulation = simulation();
        holding(simulation);
        Entity boulder = simulation.spawnObstacle(IN_REACH, Y);

        simulation.step(PRESS);
        steps(simulation, ticksIn(1f), RELEASE);

        Entity limpet = limpet(simulation);
        assertThat(limpet).isNotNull();
        assertThat(limpet.getComponent(Limpet.class).stuck).isTrue();
        assertThat(apart(limpet, boulder)).isCloseTo(radius(limpet) + radius(boulder), within(TOUCHING));
    }

    @Test
    void passesThroughATurretOnItsOwnSide() {
        Simulation simulation = simulation();
        holding(simulation);
        simulation.spawnTurret(IN_REACH, Y);

        simulation.step(PRESS);
        steps(simulation, ticksIn(0.5f), RELEASE);

        Entity limpet = limpet(simulation);
        assertThat(limpet).isNotNull();
        assertThat(limpet.getComponent(Limpet.class).stuck).isFalse();
        assertThat(limpet.getComponent(Position.class).x).isGreaterThan(IN_REACH);
    }

    @Test
    void sticksAtTheArenaEdge() {
        Simulation simulation = simulation();
        Entity commander = simulation.spawnCommander(ARENA - 10f, Y);
        arm(simulation, commander, WeaponKind.DETONATOR);
        CommanderInput east = new CommanderInput(0f, 0f, ARENA + 30f, Y, true, false);

        simulation.step(east);
        steps(simulation, ticksIn(1f), east.notFiring());

        Entity limpet = limpet(simulation);
        assertThat(limpet).isNotNull();
        assertThat(limpet.getComponent(Limpet.class).stuck).isTrue();
        assertThat(limpet.getComponent(Position.class).x).isEqualTo(ARENA - radius(limpet));
    }

    @Test
    void sticksToTheFaceOfRockItFliesInto() {
        Simulation simulation =
                on(ground().rect(WALL_COL, 0, 1, ARENA_TILES, Terrain.ROCK).build());
        holding(simulation);

        simulation.step(PRESS);
        steps(simulation, ticksIn(1f), RELEASE);

        Entity limpet = limpet(simulation);
        assertThat(limpet).isNotNull();
        assertThat(limpet.getComponent(Limpet.class).onWall).isTrue();
        assertThat(limpet.getComponent(Position.class).x).isBetween(WALL_COL * TILE - 1f, WALL_COL * TILE);
    }

    // ---- the bar and the panel ----

    /** Setting one off is no pull: the reload the throw started does not hold it back (G0100). */
    @Test
    void setsItOffWhileItsBarIsStillHot() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation);

        simulation.step(PRESS);
        steps(simulation, 2, RELEASE);
        assertThat(commander.getComponent(Heat.class).locked).isTrue();
        simulation.step(PRESS);
        steps(simulation, 2, RELEASE);

        assertThat(count(simulation, Blast.class)).isOne();
    }

    @Test
    void coolsItsBarWhileItsLimpetIsOut() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation);
        float reload =
                1f / simulation.config().weapon(WeaponKind.DETONATOR).heat().coolPerSecond();

        simulation.step(PRESS);
        steps(simulation, ticksIn(reload + 0.2f), RELEASE);

        assertThat(count(simulation, Limpet.class)).isOne();
        assertThat(commander.getComponent(Heat.class).locked).isFalse();
    }

    @Test
    void saysDetonateWhileALimpetIsOutAndNotOnceItHasGoneOff() {
        Simulation simulation = simulation();
        Entity commander = holding(simulation);

        simulation.step(PRESS);
        steps(simulation, 2, RELEASE);
        boolean out = commander.getComponent(Appearance.class).detonating;
        simulation.step(PRESS);
        steps(simulation, 2, RELEASE);

        assertThat(out).isTrue();
        assertThat(commander.getComponent(Appearance.class).detonating).isFalse();
    }

    // ---- helpers ----

    /** His limpet has gone off: none left out, and the one blast it left. */
    private static void assertWentOff(Simulation simulation) {
        assertThat(count(simulation, Limpet.class)).isZero();
        assertThat(count(simulation, Blast.class)).isOne();
    }

    /** The blast a limpet leaves when it is set off this long after it was thrown. */
    private static Entity blastAfter(float seconds) {
        return blastAfter(seconds, 0);
    }

    /** The same, with this many ranks of Firepower held. */
    private static Entity blastAfter(float seconds, int firepower) {
        Simulation simulation = simulation();
        holding(simulation);
        if (firepower > 0) {
            hold(simulation, UpgradeKind.FIREPOWER, firepower);
        }
        simulation.step(PRESS);
        steps(simulation, ticksIn(seconds), RELEASE);
        simulation.step(PRESS);
        steps(simulation, 2, RELEASE);
        ImmutableArray<Entity> blasts =
                simulation.engine().getEntitiesFor(Family.all(Blast.class).get());
        assertThat(blasts.size()).isOne();
        return blasts.get(0);
    }

    private static Entity holding(Simulation simulation) {
        Entity commander = simulation.spawnCommander(X, Y);
        arm(simulation, commander, WeaponKind.DETONATOR);
        return commander;
    }

    private static void arm(Simulation simulation, Entity commander, WeaponKind gun) {
        commander
                .getComponent(Weapon.class)
                .set(gun, simulation.config().weapon(gun), simulation.config().tickSeconds());
    }

    private static @Nullable Entity limpet(Simulation simulation) {
        ImmutableArray<Entity> limpets =
                simulation.engine().getEntitiesFor(Family.all(Limpet.class).get());
        return limpets.size() == 0 ? null : limpets.get(0);
    }

    private static float apart(Entity a, Entity b) {
        Position one = a.getComponent(Position.class);
        Position two = b.getComponent(Position.class);
        float x = one.x - two.x;
        float y = one.y - two.y;
        return (float) Math.sqrt(x * x + y * y);
    }

    private static int damage(Entity blast) {
        return blast.getComponent(Blast.class).damage;
    }

    private static float radius(Entity entity) {
        return entity.getComponent(Body.class).radius;
    }
}
