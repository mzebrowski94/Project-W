package pl.mzebrows.pwt.sim.system.creature;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mzebrows.pwt.sim.AlphaWorlds.BARREL_BLAST_RADIUS;
import static pl.mzebrows.pwt.sim.AlphaWorlds.CERTAIN;
import static pl.mzebrows.pwt.sim.AlphaWorlds.DEATH_THROES;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ENEMY_HEALTH;
import static pl.mzebrows.pwt.sim.AlphaWorlds.MELEE_ACTIVE;
import static pl.mzebrows.pwt.sim.AlphaWorlds.RESPAWN_DELAY;
import static pl.mzebrows.pwt.sim.AlphaWorlds.config;
import static pl.mzebrows.pwt.sim.AlphaWorlds.count;
import static pl.mzebrows.pwt.sim.AlphaWorlds.leapConfig;
import static pl.mzebrows.pwt.sim.AlphaWorlds.steps;
import static pl.mzebrows.pwt.sim.AlphaWorlds.ticksIn;
import static pl.mzebrows.pwt.sim.AlphaWorlds.unhurt;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.component.Appearance;
import pl.mzebrows.pwt.sim.component.Commander;
import pl.mzebrows.pwt.sim.component.Damage;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Gripped;
import pl.mzebrows.pwt.sim.component.Health;
import pl.mzebrows.pwt.sim.component.Invulnerable;
import pl.mzebrows.pwt.sim.component.Melee;
import pl.mzebrows.pwt.sim.component.Position;
import pl.mzebrows.pwt.sim.component.Projectile;
import pl.mzebrows.pwt.sim.component.Shield;
import pl.mzebrows.pwt.sim.component.Trooper;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.def.AiChance;
import pl.mzebrows.pwt.sim.def.ArmourDef;
import pl.mzebrows.pwt.sim.def.GripDef;
import pl.mzebrows.pwt.sim.def.Harm;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * The ant (G0045): a head that turns a bullet, and a leap that takes a man away instead of killing him.
 *
 * <p>Two mechanics, and each one is about where the player has to be. <strong>The head is armoured
 * against gunfire and nothing else</strong>, judged by the way the shot is flying: a line of men in
 * front of one does nothing, somebody behind it or beside it does, and a fist or a blast lands
 * wherever it lands. <strong>Its leap grips</strong>: the man it lands on is carried off with a clock
 * on him, and the only way out is to kill the thing holding him - from behind, with a fist in its face,
 * or with his own fists if it is him in its jaws.
 */
class AntTest {

    private static final float MIDDLE = 50f;

    /** Near enough that its first leap lands on him, and inside the fixture's trigger range. */
    private static final float WITHIN_A_LEAP = 12f;

    /** Far enough off that it takes a long walk to reach him, so a test about a shot is only about that. */
    private static final float FAR_OFF = 40f;

    private static final float HEAD_ARC = 140f;

    private static final float CARRY_SPEED = 10f;

    /** How hard a man is thrown clear of an ant that dies holding him. */
    private static final float ESCAPE_THROW = 20f;

    private static final float HOLD_SECONDS = 1f;

    /** Long enough for a leap from within one to have landed. */
    private static final int UNTIL_IT_LANDS = 60;

    /** A few ticks after it lands: well inside its claws' cooldown, and long before its next leap. */
    private static final int A_BEAT = 10;

    /** Close enough to its face or its back that a shot arrives before it has walked anywhere. */
    private static final float POINT_BLANK = 6f;

    private static final int UNTIL_THE_SHOT_ARRIVES = 12;

    // ---- the head ----

    @Test
    void turnsAShotOffItsHead() {
        Simulation simulation = new Simulation(armoured());
        simulation.spawnCommander(MIDDLE - FAR_OFF, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE, MIDDLE);
        steps(simulation, 2);

        simulation.spawnProjectile(at(ant).x - POINT_BLANK, MIDDLE, 1f, 0f);
        steps(simulation, UNTIL_THE_SHOT_ARRIVES);

        assertThat(ant.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
        assertThat(count(simulation, Projectile.class)).isZero();
    }

    @Test
    void takesAShotInTheBack() {
        Simulation simulation = new Simulation(armoured());
        simulation.spawnCommander(MIDDLE - FAR_OFF, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE, MIDDLE);
        steps(simulation, 2);

        simulation.spawnProjectile(at(ant).x + POINT_BLANK, MIDDLE, -1f, 0f);
        steps(simulation, UNTIL_THE_SHOT_ARRIVES);

        assertThat(ant.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
    }

    /** The arc leaves the flanks open: square on from the side is not the head. */
    @Test
    void takesAShotFromTheSide() {
        Simulation simulation = new Simulation(armoured());
        simulation.spawnCommander(MIDDLE - FAR_OFF, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE, MIDDLE);
        steps(simulation, 2);

        // Where it will have walked to by the time the shot arrives: it is on its way west.
        simulation.spawnProjectile(at(ant).x - POINT_BLANK * 0.5f, MIDDLE + POINT_BLANK, 0f, -1f);
        steps(simulation, UNTIL_THE_SHOT_ARRIVES);

        assertThat(ant.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
    }

    /** Only gunfire glances: a fist in its face is how its head is hurt. */
    @Test
    void takesAFistInTheFace() {
        Simulation simulation = new Simulation(armoured());
        simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE + 4f, MIDDLE);
        steps(simulation, 2);

        swingAt(simulation, ant, ticksIn(MELEE_ACTIVE) + 2);

        assertThat(ant.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
    }

    /** And a blast lands whichever way it is facing. */
    @Test
    void goesDownToABlastWhicheverWayItFaces() {
        Simulation simulation = new Simulation(armoured());
        simulation.spawnCommander(MIDDLE - FAR_OFF, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE, MIDDLE);
        steps(simulation, 2);

        simulation.spawnBlast(at(ant).x - 2f, MIDDLE, BARREL_BLAST_RADIUS);
        steps(simulation, 3);

        assertThat(ant.getComponent(Dying.class).begun).isTrue();
    }

    // ---- the grip ----

    @Test
    void takesHoldOfAManItLandsOnRatherThanKillingHim() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);

        stepUntilTaken(simulation, commander);

        assertThat(unhurt(commander)).isTrue();
        assertThat(commander.getComponent(Appearance.class).state).isEqualTo(Appearance.State.GRIPPED);
    }

    @Test
    void carriesHimOff() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTaken(simulation, commander);
        float takenX = at(commander).x;
        float takenY = at(commander).y;

        steps(simulation, ticksIn(HOLD_SECONDS * 0.5f));

        float movedX = at(commander).x - takenX;
        float movedY = at(commander).y - takenY;
        assertThat((float) Math.sqrt(movedX * movedX + movedY * movedY))
                .isGreaterThan(CARRY_SPEED * HOLD_SECONDS * 0.25f);
    }

    /** His time is on him, read backwards, so the player can see how long he has (pillar 5). */
    @Test
    void showsHowLongHeHasLeft() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTaken(simulation, commander);
        simulation.step();
        float early = commander.getComponent(Appearance.class).charge;

        steps(simulation, ticksIn(HOLD_SECONDS * 0.5f));

        assertThat(commander.getComponent(Appearance.class).charge).isBetween(0f, early);
        assertThat(early).isGreaterThan(0.9f);
    }

    @Test
    void finishesHimWhenHisTimeIsUp() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTaken(simulation, commander);

        steps(simulation, ticksIn(HOLD_SECONDS) + 2);

        assertThat(commander.getComponent(Dying.class).begun).isTrue();
    }

    /** Killing the thing holding him is how he is freed, and he comes out of it on his feet. */
    @Test
    void letsGoOfHimWhenItDies() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTaken(simulation, commander);

        ant.getComponent(Damage.class).land(ENEMY_HEALTH * 2, Harm.KILL);
        steps(simulation, ticksIn(HOLD_SECONDS) + 2);

        assertThat(commander.getComponent(Gripped.class).held()).isFalse();
        assertThat(commander.getComponent(Dying.class).begun).isFalse();
    }

    /** Getting out costs him a wound, and he is thrown clear of the body (G0053). */
    @Test
    void woundsAManItDiesHoldingRatherThanLettingHimGoFree() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTaken(simulation, commander);

        ant.getComponent(Damage.class).land(ENEMY_HEALTH * 2, Harm.KILL);
        steps(simulation, 3);

        assertThat(commander.getComponent(Vitality.class).wounds).isEqualTo(1);
        assertThat(commander.getComponent(Vitality.class).begun).isFalse();
        assertThat(commander.getComponent(Gripped.class).held()).isFalse();
    }

    /** A shield takes a grab as it takes any hit, and breaks: the ant simply lands on him (G0053). */
    @Test
    void takesNobodyWhoHasAShieldInFrontOfHim() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        commander.getComponent(Shield.class).raise();
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);

        for (int i = 0; i < UNTIL_IT_LANDS && commander.getComponent(Shield.class).up; i++) {
            simulation.step();
        }

        assertThat(commander.getComponent(Shield.class).up).isFalse();
        assertThat(commander.getComponent(Gripped.class).held()).isFalse();
        assertThat(unhurt(commander)).isTrue();
    }

    /**
     * The shield takes its leap and its claws with it: an ant that lands on a shielded man does not bite him on the
     * next tick, or the shield bought him nothing (G0053).
     */
    @Test
    void bitesNobodyTheMomentItLandsOnAShield() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        commander.getComponent(Shield.class).raise();
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTheShieldBreaks(simulation, commander);

        steps(simulation, A_BEAT);

        assertThat(commander.getComponent(Gripped.class).held()).isFalse();
        assertThat(unhurt(commander)).isTrue();
    }

    /** The playtest: back from the dead behind his shield, with an ant waiting at the rally point. */
    @Test
    void neitherTakesNorBitesAManBackBehindHisShield() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        commander.getComponent(Damage.class).land(0, Harm.KILL);
        commander.getComponent(Invulnerable.class).remainingTicks = 0;
        steps(simulation, ticksIn(DEATH_THROES + RESPAWN_DELAY) + 2);
        Entity back = simulation
                .engine()
                .getEntitiesFor(Family.all(Commander.class).get())
                .first();
        // Long enough for the leap to land before it runs out on its own: this is about the hit, not the clock.
        back.getComponent(Shield.class).remainingTicks = UNTIL_IT_LANDS * 2;
        simulation.spawnUnit(UnitKind.ANT, at(back).x + WITHIN_A_LEAP, at(back).y);
        stepUntilTheShieldBreaks(simulation, back);

        steps(simulation, A_BEAT);

        assertThat(back.getComponent(Gripped.class).held()).isFalse();
        assertThat(unhurt(back)).isTrue();
    }

    /** Held, he drives nothing and fires nothing... */
    @Test
    void leavesAManInItsJawsNoGun() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTaken(simulation, commander);

        steps(simulation, 20, new CommanderInput(1f, 0f, MIDDLE + 20f, MIDDLE, true, false));

        assertThat(count(simulation, Projectile.class)).isZero();
    }

    /** ...and the one thing he can still do is swing, which lands on what is holding him. */
    @Test
    void letsAManInItsJawsSwingAtIt() {
        Simulation simulation = new Simulation(gripping(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTaken(simulation, commander);

        swingAt(simulation, ant, ticksIn(MELEE_ACTIVE) + 2);

        assertThat(ant.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
    }

    /** A trooper gets himself out the same way, when the dice say so (AiChance.STRUGGLE). */
    @Test
    void hasATrooperInItsJawsSwingAtIt() {
        Simulation simulation = new Simulation(gripping(config().withChance(AiChance.STRUGGLE, CERTAIN)));
        simulation.spawnCommander(95f, 95f);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTaken(simulation, trooper);

        steps(simulation, ticksIn(MELEE_ACTIVE) + 3);

        assertThat(ant.getComponent(Health.class).current).isLessThan(ENEMY_HEALTH);
    }

    /** With the dice switched off he never does, so the swing above is the struggle and nothing else. */
    @Test
    void leavesATrooperWhoNeverStrugglesInItsJaws() {
        Simulation simulation = new Simulation(gripping(config()));
        simulation.spawnCommander(95f, 95f);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        Entity ant = simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        stepUntilTaken(simulation, trooper);

        steps(simulation, ticksIn(HOLD_SECONDS * 0.5f));

        assertThat(ant.getComponent(Health.class).current).isEqualTo(ENEMY_HEALTH);
    }

    // ---- the deflect (G0086) ----

    /**
     * A trooper facing it meets its leap with his hand strike: it never takes hold of him. Every other chance is off in
     * the fixture, so a swing of his is the deflect.
     */
    @Test
    void isDeflectedByATrooperItLeapsAtInsideHisArc() {
        Simulation simulation = new Simulation(deflected(config()));
        simulation.spawnCommander(95f, 95f);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);
        boolean taken = false;
        boolean swung = false;

        for (int tick = 0; tick < UNTIL_IT_LANDS; tick++) {
            simulation.step();
            taken |= trooper.getComponent(Gripped.class).held();
            swung |= trooper.getComponent(Melee.class).remainingTicks > 0;
        }

        assertThat(taken).isFalse();
        assertThat(swung).isTrue();
        assertThat(unhurt(trooper)).isTrue();
    }

    /** From behind, never: he has to be facing it, and here he turns too slowly to have turned in time. */
    @Test
    void takesATrooperItLeapsAtFromBehindHoweverSureItsDeflect() {
        SimConfig base = config();
        Simulation simulation =
                new Simulation(deflected(base.withTrooper(base.trooper().withTurnDegreesPerSecond(1f))));
        simulation.spawnCommander(95f, 95f);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        trooper.getComponent(Trooper.class).look(-1f, 0f);
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);

        stepUntilTaken(simulation, trooper);

        assertThat(trooper.getComponent(Gripped.class).held()).isTrue();
    }

    /** No strike, no deflect: a man whose hand strike is still coming back is taken, however sure the deflect. */
    @Test
    void takesATrooperWhoseStrikeIsSpentHoweverSureItsDeflect() {
        Simulation simulation = new Simulation(deflected(config()));
        simulation.spawnCommander(95f, 95f);
        Entity trooper = simulation.spawnTrooper(MIDDLE, MIDDLE);
        trooper.getComponent(Trooper.class).look(1f, 0f);
        trooper.getComponent(Melee.class).remainingTicks = UNTIL_IT_LANDS * 2;
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);

        stepUntilTaken(simulation, trooper);

        assertThat(trooper.getComponent(Gripped.class).held()).isTrue();
    }

    /** Not the commander's: he has his own parry. */
    @Test
    void takesTheCommanderHoweverSureItsDeflect() {
        Simulation simulation = new Simulation(deflected(config()));
        Entity commander = simulation.spawnCommander(MIDDLE, MIDDLE);
        simulation.spawnUnit(UnitKind.ANT, MIDDLE + WITHIN_A_LEAP, MIDDLE);

        stepUntilTaken(simulation, commander);

        assertThat(commander.getComponent(Gripped.class).held()).isTrue();
    }

    /** The gripping ant, every leap of which a trooper facing it deflects. */
    private static SimConfig deflected(SimConfig config) {
        SimConfig gripping = gripping(config);
        return gripping.withUnit(UnitKind.ANT, ant -> ant.withLeap(ant.leap().withDeflectChance(100)));
    }

    /** An ant with its head armoured and nothing else special, which bites nobody. */
    private static SimConfig armoured() {
        return config().withUnit(
                        UnitKind.ANT, ant -> ant.withContactDamage(0).withArmour(new ArmourDef(true, HEAD_ARC, 0f)));
    }

    /** The ant as it ships, in shape: an armoured head, and a leap that grips. */
    private static SimConfig gripping(SimConfig config) {
        return config.withUnit(
                UnitKind.ANT,
                ant -> ant.withLeap(leapConfig())
                        .withGrip(new GripDef(
                                true, CARRY_SPEED, HOLD_SECONDS, ESCAPE_THROW, Harm.WOUND, true, false, false))
                        .withArmour(new ArmourDef(true, HEAD_ARC, 0f)));
    }

    private static void stepUntilTheShieldBreaks(Simulation simulation, Entity man) {
        for (int i = 0; i < UNTIL_IT_LANDS; i++) {
            if (!man.getComponent(Shield.class).up) {
                return;
            }
            simulation.step();
        }
        throw new IllegalStateException("nothing broke his shield");
    }

    private static void stepUntilTaken(Simulation simulation, Entity man) {
        for (int i = 0; i < UNTIL_IT_LANDS; i++) {
            if (man.getComponent(Gripped.class).held()) {
                return;
            }
            simulation.step();
        }
        throw new IllegalStateException("the ant never took hold of him");
    }

    /** The commander swinging at wherever it is, every tick, for this many ticks. */
    private static void swingAt(Simulation simulation, Entity ant, int ticks) {
        for (int i = 0; i < ticks; i++) {
            simulation.step(new CommanderInput(0f, 0f, at(ant).x, at(ant).y, false, true));
        }
    }

    private static Position at(Entity entity) {
        return entity.getComponent(Position.class);
    }
}
