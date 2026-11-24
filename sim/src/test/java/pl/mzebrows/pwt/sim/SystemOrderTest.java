package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.system.combat.CollisionSystem;
import pl.mzebrows.pwt.sim.system.combat.CooldownSystem;
import pl.mzebrows.pwt.sim.system.combat.DamageSystem;
import pl.mzebrows.pwt.sim.system.combat.GrenadeSystem;
import pl.mzebrows.pwt.sim.system.combat.LimpetSystem;
import pl.mzebrows.pwt.sim.system.combat.OrbSystem;
import pl.mzebrows.pwt.sim.system.combat.PickupSystem;
import pl.mzebrows.pwt.sim.system.combat.ProjectileBoundsSystem;
import pl.mzebrows.pwt.sim.system.combat.ShockSystem;
import pl.mzebrows.pwt.sim.system.commander.ActionSystem;
import pl.mzebrows.pwt.sim.system.commander.AidSystem;
import pl.mzebrows.pwt.sim.system.commander.CommanderControlSystem;
import pl.mzebrows.pwt.sim.system.commander.CrateSystem;
import pl.mzebrows.pwt.sim.system.commander.DragSystem;
import pl.mzebrows.pwt.sim.system.commander.MeleeSystem;
import pl.mzebrows.pwt.sim.system.commander.SentrySystem;
import pl.mzebrows.pwt.sim.system.commander.StrikeSystem;
import pl.mzebrows.pwt.sim.system.commander.TurretSystem;
import pl.mzebrows.pwt.sim.system.creature.BarrageSystem;
import pl.mzebrows.pwt.sim.system.creature.EnemyLeapSystem;
import pl.mzebrows.pwt.sim.system.creature.EnemyRangedSystem;
import pl.mzebrows.pwt.sim.system.creature.EvolutionSystem;
import pl.mzebrows.pwt.sim.system.creature.GripSystem;
import pl.mzebrows.pwt.sim.system.creature.PerceptionSystem;
import pl.mzebrows.pwt.sim.system.creature.SlingSystem;
import pl.mzebrows.pwt.sim.system.death.BlastSystem;
import pl.mzebrows.pwt.sim.system.death.BloatSystem;
import pl.mzebrows.pwt.sim.system.death.CasualtySystem;
import pl.mzebrows.pwt.sim.system.death.DyingSystem;
import pl.mzebrows.pwt.sim.system.death.GeyserSystem;
import pl.mzebrows.pwt.sim.system.death.MineSystem;
import pl.mzebrows.pwt.sim.system.death.ReaperSystem;
import pl.mzebrows.pwt.sim.system.death.RespawnSystem;
import pl.mzebrows.pwt.sim.system.death.SlickSystem;
import pl.mzebrows.pwt.sim.system.level.ControlPointSystem;
import pl.mzebrows.pwt.sim.system.level.DoorSystem;
import pl.mzebrows.pwt.sim.system.level.EmplacementSystem;
import pl.mzebrows.pwt.sim.system.level.LairSystem;
import pl.mzebrows.pwt.sim.system.level.NestSystem;
import pl.mzebrows.pwt.sim.system.level.ObjectiveSystem;
import pl.mzebrows.pwt.sim.system.level.ReinforcementSystem;
import pl.mzebrows.pwt.sim.system.level.WaveSystem;
import pl.mzebrows.pwt.sim.system.level.WorkedSiteSystem;
import pl.mzebrows.pwt.sim.system.motion.AvoidanceSystem;
import pl.mzebrows.pwt.sim.system.motion.DodgeSystem;
import pl.mzebrows.pwt.sim.system.motion.DriveSystem;
import pl.mzebrows.pwt.sim.system.motion.FacingSystem;
import pl.mzebrows.pwt.sim.system.motion.HopSystem;
import pl.mzebrows.pwt.sim.system.motion.MovementSystem;
import pl.mzebrows.pwt.sim.system.motion.NavigationSystem;
import pl.mzebrows.pwt.sim.system.motion.PositionHistorySystem;
import pl.mzebrows.pwt.sim.system.motion.SlitherSystem;
import pl.mzebrows.pwt.sim.system.motion.StunSystem;
import pl.mzebrows.pwt.sim.system.motion.TrooperMoveSystem;
import pl.mzebrows.pwt.sim.system.motion.WanderSystem;
import pl.mzebrows.pwt.sim.system.motion.WeaveSystem;
import pl.mzebrows.pwt.sim.system.squad.SquadOrderSystem;
import pl.mzebrows.pwt.sim.system.squad.TrooperCombatSystem;
import pl.mzebrows.pwt.sim.system.squad.TrooperMindSystem;
import pl.mzebrows.pwt.sim.system.upgrade.Modifiers;
import pl.mzebrows.pwt.sim.system.upgrade.UpgradeSystem;

/**
 * System order is behaviour, and {@code docs/simulation-order.md} says which orderings are decisions.
 * This is that table as data, checked against the order {@code Simulation} actually registers - so a
 * reorder that breaks one fails with the reason it was there, instead of as a one-tick bug somebody
 * finds by playing. A new constraint is a row here and a row in the doc.
 */
class SystemOrderTest {

    private static final Path ORDER_DOC = Path.of("..", "docs", "simulation-order.md");

    /** One ordering the doc names, and the doc's reason for it, which a failure prints. */
    private record Before(Class<? extends EntitySystem> first, Class<? extends EntitySystem> then, String why) {

        @Override
        public String toString() {
            return first.getSimpleName() + " before " + then.getSimpleName();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("constraints")
    void keepsEveryOrderingTheDesignDependsOn(Before constraint) {
        List<Class<?>> order = registered();

        assertThat(order.indexOf(constraint.first()))
                .as("%s: %s", constraint, constraint.why())
                .isNotNegative()
                .isLessThan(order.indexOf(constraint.then()));
    }

    @Test
    void recordsHistoryAndTicksTimersDownBeforeAnythingElse() {
        assertThat(registered().subList(0, 2)).containsExactly(PositionHistorySystem.class, CooldownSystem.class);
    }

    @Test
    void reapsLast() {
        List<Class<?>> order = registered();

        assertThat(order.getLast()).isEqualTo(ReaperSystem.class);
    }

    @Test
    void answersABounceTheSystemAfterTheSolverRecordsIt() {
        assertImmediatelyAfter(GrenadeSystem.class, CollisionSystem.class);
    }

    @Test
    void sticksALimpetStraightAfterTheGrenadesBounceIsAnswered() {
        assertImmediatelyAfter(LimpetSystem.class, GrenadeSystem.class);
    }

    /** An orb's power is spent by this tick's contacts, and one spent goes off inside this tick's death window (G0128). */
    @Test
    void beamsFromAnOrbStraightAfterTheLimpetBeforeTheDeathChain() {
        assertImmediatelyAfter(OrbSystem.class, LimpetSystem.class);
    }

    @Test
    void drainsShockTheSystemAfterTheHitsThatBuiltIt() {
        assertImmediatelyAfter(ShockSystem.class, DamageSystem.class);
    }

    @Test
    void laysASpatterInTheSameWindowAsTheBlast() {
        assertImmediatelyAfter(SlickSystem.class, BlastSystem.class);
    }

    @Test
    void setsOffAMineImmediatelyBeforeTheBlastItBecomes() {
        assertImmediatelyAfter(BlastSystem.class, MineSystem.class);
    }

    /** The doc's numbered table is read by people planning a change; it must be the order that runs. */
    @Test
    void numbersTheSystemsInTheDocAsTheyRun() throws IOException {
        TreeMap<Integer, String> documented = new TreeMap<>();
        Matcher row = Pattern.compile("\\|\\s*(\\d+)\\s*\\|\\s*`(\\w+System)`\\s*(?=\\|)")
                .matcher(Files.readString(ORDER_DOC));
        while (row.find()) {
            documented.put(Integer.parseInt(row.group(1)), row.group(2));
        }

        assertThat(List.copyOf(documented.values()))
                .containsExactlyElementsOf(
                        registered().stream().map(Class::getSimpleName).toList());
    }

    static Stream<Arguments> constraints() {
        List<Before> rows = new ArrayList<>();
        rows.add(new Before(
                DragSystem.class,
                CommanderControlSystem.class,
                "it publishes where the load lies; only his control system may constrain his velocity"));
        rows.add(new Before(CommanderControlSystem.class, MeleeSystem.class, "a swing reads this tick's intent"));
        rows.add(new Before(
                CommanderControlSystem.class,
                DamageSystem.class,
                "a Rocketman's surge stuns on the tick the dash launches (G0108)"));
        rows.add(new Before(CommanderControlSystem.class, TurretSystem.class, "a gun on a mount is a pair of hands"));
        rows.add(new Before(MeleeSystem.class, TurretSystem.class, "the same window a sweep is spawned in"));
        rows.add(
                new Before(MeleeSystem.class, PerceptionSystem.class, "a swing is in the world before anything looks"));
        rows.add(
                new Before(TurretSystem.class, PerceptionSystem.class, "a shot is in the world before anything looks"));
        rows.add(new Before(NavigationSystem.class, WeaveSystem.class, "a weave bends the course navigation wrote"));
        rows.add(new Before(WeaveSystem.class, AvoidanceSystem.class, "avoidance keeps the last word over a weave"));
        rows.add(
                new Before(WeaveSystem.class, GripSystem.class, "a carry overwrites the course a creature set itself"));
        rows.add(new Before(
                GripSystem.class, TrooperMindSystem.class, "the carry is the only thing moving a man it holds"));
        rows.add(new Before(GripSystem.class, AvoidanceSystem.class, "a carry is bent round rock like any other walk"));
        for (Class<? extends EntitySystem> course : List.of(
                PerceptionSystem.class,
                NavigationSystem.class,
                WeaveSystem.class,
                SquadOrderSystem.class,
                TrooperMindSystem.class,
                TrooperMoveSystem.class,
                TrooperCombatSystem.class,
                WanderSystem.class)) {
            rows.add(new Before(course, AvoidanceSystem.class, "avoidance must see what the unit intended"));
        }
        rows.add(
                new Before(TrooperCombatSystem.class, WanderSystem.class, "wander ambles what nothing else spoke for"));
        rows.add(
                new Before(AvoidanceSystem.class, EnemyRangedSystem.class, "planting a shooter beats the bent course"));
        rows.add(new Before(EnemyRangedSystem.class, HopSystem.class, "a gait paces a course, it does not choose one"));
        rows.add(new Before(HopSystem.class, DodgeSystem.class, "a sidestep must not land in the middle of a sit"));
        rows.add(new Before(DodgeSystem.class, SlitherSystem.class, "a slither's course is wholly its own (G0095)"));
        rows.add(new Before(SlitherSystem.class, DriveSystem.class, "a slither's course is committed like any other"));
        rows.add(new Before(
                AvoidanceSystem.class, DodgeSystem.class, "a dodge bent round a rock is not the same dodge (G0012)"));
        rows.add(new Before(
                DodgeSystem.class, DriveSystem.class, "drivers write a desired velocity; DriveSystem commits it"));
        rows.add(new Before(
                EnemyRangedSystem.class, DriveSystem.class, "a desired velocity is spent on the tick it is written"));
        rows.add(new Before(
                PerceptionSystem.class, EnemyLeapSystem.class, "who it leaps at is what perception wrote (G0026)"));
        rows.add(new Before(
                DriveSystem.class, EnemyLeapSystem.class, "a leap is committed and overwrites velocity outright"));
        rows.add(new Before(EnemyLeapSystem.class, StunSystem.class, "stun owns the velocity of anything held still"));
        rows.add(new Before(StunSystem.class, MovementSystem.class, "knockback must reach movement intact"));
        rows.add(new Before(
                MovementSystem.class, StrikeSystem.class, "a strike advances along its arc with everything else"));
        rows.add(new Before(StrikeSystem.class, CollisionSystem.class, "then contacts resolve once"));
        rows.add(new Before(PickupSystem.class, EvolutionSystem.class, "what it ate becomes what it is"));
        rows.add(new Before(EvolutionSystem.class, DamageSystem.class, "damage lands on the new strain"));
        rows.add(new Before(DamageSystem.class, ActionSystem.class, "a man down this tick is on offer this tick"));
        rows.add(new Before(DamageSystem.class, AidSystem.class, "a man down this tick is on offer this tick"));
        rows.add(new Before(
                ActionSystem.class, CrateSystem.class, "a crate opened this tick is dealt this tick (G0056)"));
        rows.add(new Before(DamageSystem.class, CrateSystem.class, "a man hurt this tick has his crate shut (G0056)"));
        rows.add(new Before(
                ActionSystem.class, CasualtySystem.class, "a man picked up as his clock runs out is not buried"));
        rows.add(new Before(
                AidSystem.class, CasualtySystem.class, "a man picked up as his clock runs out is not buried"));
        rows.add(new Before(CasualtySystem.class, DyingSystem.class, "who stayed down too long, then the body"));
        rows.add(new Before(
                ActionSystem.class,
                DoorSystem.class,
                "a commander's hands on a door this tick count this tick (G0069)"));
        rows.add(new Before(
                TrooperMoveSystem.class,
                DoorSystem.class,
                "a door goes to a man whose way it shuts this tick (G0069)"));
        rows.add(new Before(
                DamageSystem.class, DoorSystem.class, "a door broken this tick stops being a wall this tick (G0069)"));
        rows.add(new Before(
                ActionSystem.class,
                WorkedSiteSystem.class,
                "a commander's hands on a worked site this tick count this tick (G0111)"));
        rows.add(new Before(
                DamageSystem.class,
                WorkedSiteSystem.class,
                "a wound this tick loses the work on a worked site this tick (G0111)"));
        rows.add(new Before(
                WorkedSiteSystem.class,
                UpgradeSystem.class,
                "a worked site taken this tick earns its draft point this tick (G0111)"));
        rows.add(new Before(
                WorkedSiteSystem.class,
                ObjectiveSystem.class,
                "a worked site taken this tick counts for the objectives this tick (G0111)"));
        rows.add(new Before(DyingSystem.class, RespawnSystem.class, "reactions run while the dead are still there"));
        rows.add(new Before(RespawnSystem.class, ProjectileBoundsSystem.class, "a spent shot joins the death chain"));
        rows.add(new Before(
                ProjectileBoundsSystem.class,
                BloatSystem.class,
                "a miss is marked dead while the reactions can still see it"));
        rows.add(new Before(BloatSystem.class, BlastSystem.class, "a body that fills this tick explodes this tick"));
        rows.add(new Before(
                GeyserSystem.class,
                SlickSystem.class,
                "an eruption is burning on the tick its filling runs out (G0089)"));
        rows.add(
                new Before(DamageSystem.class, BlastSystem.class, "a blast owes damage to the next tick: the cascade"));
        rows.add(new Before(
                SlickSystem.class, ReinforcementSystem.class, "a man who arrives is not counted among the dead"));
        rows.add(new Before(CollisionSystem.class, ReinforcementSystem.class, "beside where the commander ended up"));
        rows.add(new Before(SlickSystem.class, NestSystem.class, "a nest empties itself inside the death window"));
        rows.add(new Before(WaveSystem.class, ObjectiveSystem.class, "a last wave landing this tick is not a win"));
        rows.add(new Before(
                NestSystem.class, ObjectiveSystem.class, "a last nest dying this tick is not a clear level"));
        rows.add(new Before(
                SlickSystem.class, LairSystem.class, "a lair reads the commanders once the death chain has run"));
        rows.add(new Before(LairSystem.class, ObjectiveSystem.class, "a boss rising this tick counts this tick"));
        rows.add(new Before(
                PerceptionSystem.class, BarrageSystem.class, "a boss shoots at what perception wrote (G0026)"));
        rows.add(new Before(
                PerceptionSystem.class, SlingSystem.class, "a slinger slings at what perception wrote (G0026)"));
        rows.add(new Before(
                SlickSystem.class, SlingSystem.class, "a dead slinger spills its riders inside the death window"));
        rows.add(
                new Before(SlingSystem.class, ObjectiveSystem.class, "riders spilled this tick are not a clear level"));
        rows.add(new Before(
                CollisionSystem.class,
                EmplacementSystem.class,
                "an emplacement wakes on where the men ended the tick"));
        rows.add(new Before(
                EmplacementSystem.class,
                UpgradeSystem.class,
                "an emplacement's gun reads the ledger every reader sees"));
        rows.add(new Before(WaveSystem.class, ControlPointSystem.class, "a schedule armed this tick sends next tick"));
        rows.add(new Before(
                ControlPointSystem.class, ObjectiveSystem.class, "the post exists on the tick it is asked about"));
        rows.add(new Before(
                CollisionSystem.class,
                FacingSystem.class,
                "a facing is where the tick ended looking - perception reads it next tick, the arc the renderer drew"));
        rows.add(new Before(
                WorkedSiteSystem.class,
                UpgradeSystem.class,
                "a site worked to its end this tick earns its draft this tick"));
        rows.add(new Before(
                ControlPointSystem.class, UpgradeSystem.class, "a point taken this tick earns its draft this tick"));
        rows.add(new Before(
                CollisionSystem.class, UpgradeSystem.class, "a ridge crossed this tick earns its draft this tick"));
        rows.add(new Before(
                OrbSystem.class, BlastSystem.class, "an orb spent this tick goes off in this tick's death window"));
        rows.add(new Before(
                DamageSystem.class,
                SentrySystem.class,
                "a sentry destroyed this tick goes off while its body is here"));
        rows.add(new Before(
                SentrySystem.class, BlastSystem.class, "a sentry out of time this tick leaves its blast this tick"));
        rows.add(new Before(
                SentrySystem.class, UpgradeSystem.class, "a sentry fires through Guns, which reads the ledger"));
        for (Class<? extends EntitySystem> reader : readersOfTheLedger()) {
            rows.add(new Before(reader, UpgradeSystem.class, "every reader in a tick sees the same ledger"));
        }
        return rows.stream().map(Arguments::of);
    }

    private static void assertImmediatelyAfter(Class<?> later, Class<?> earlier) {
        List<Class<?>> order = registered();

        assertThat(order.indexOf(later)).isEqualTo(order.indexOf(earlier) + 1);
    }

    /**
     * Every registered system that is handed the read port, found rather than listed, so a new reader is
     * held to the ledger's one position without anybody remembering to add it (ADR 0017).
     */
    private static List<Class<? extends EntitySystem>> readersOfTheLedger() {
        List<Class<? extends EntitySystem>> readers = new ArrayList<>();
        for (EntitySystem system : new Simulation(SimConfig.defaults()).engine().getSystems()) {
            for (Constructor<?> constructor : system.getClass().getDeclaredConstructors()) {
                if (Arrays.asList(constructor.getParameterTypes()).contains(Modifiers.class)) {
                    readers.add(system.getClass());
                    break;
                }
            }
        }
        return readers;
    }

    private static List<Class<?>> registered() {
        ImmutableArray<EntitySystem> systems =
                new Simulation(SimConfig.defaults()).engine().getSystems();
        List<Class<?>> order = new ArrayList<>();
        for (EntitySystem system : systems) {
            order.add(system.getClass());
        }
        return order;
    }
}
