package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mzebrows.pwt.sim.system.ActionSystem;
import pl.mzebrows.pwt.sim.system.AidSystem;
import pl.mzebrows.pwt.sim.system.AvoidanceSystem;
import pl.mzebrows.pwt.sim.system.BarrageSystem;
import pl.mzebrows.pwt.sim.system.BlastSystem;
import pl.mzebrows.pwt.sim.system.BloatSystem;
import pl.mzebrows.pwt.sim.system.CasualtySystem;
import pl.mzebrows.pwt.sim.system.CollisionSystem;
import pl.mzebrows.pwt.sim.system.CommanderControlSystem;
import pl.mzebrows.pwt.sim.system.ControlPointSystem;
import pl.mzebrows.pwt.sim.system.CooldownSystem;
import pl.mzebrows.pwt.sim.system.CrateSystem;
import pl.mzebrows.pwt.sim.system.DamageSystem;
import pl.mzebrows.pwt.sim.system.DodgeSystem;
import pl.mzebrows.pwt.sim.system.DragSystem;
import pl.mzebrows.pwt.sim.system.DriveSystem;
import pl.mzebrows.pwt.sim.system.DyingSystem;
import pl.mzebrows.pwt.sim.system.EnemyLeapSystem;
import pl.mzebrows.pwt.sim.system.EnemyRangedSystem;
import pl.mzebrows.pwt.sim.system.EvolutionSystem;
import pl.mzebrows.pwt.sim.system.FacingSystem;
import pl.mzebrows.pwt.sim.system.GrenadeSystem;
import pl.mzebrows.pwt.sim.system.GripSystem;
import pl.mzebrows.pwt.sim.system.HopSystem;
import pl.mzebrows.pwt.sim.system.LairSystem;
import pl.mzebrows.pwt.sim.system.MeleeSystem;
import pl.mzebrows.pwt.sim.system.MineSystem;
import pl.mzebrows.pwt.sim.system.MovementSystem;
import pl.mzebrows.pwt.sim.system.NavigationSystem;
import pl.mzebrows.pwt.sim.system.NestSystem;
import pl.mzebrows.pwt.sim.system.ObjectiveSystem;
import pl.mzebrows.pwt.sim.system.PerceptionSystem;
import pl.mzebrows.pwt.sim.system.PickupSystem;
import pl.mzebrows.pwt.sim.system.PositionHistorySystem;
import pl.mzebrows.pwt.sim.system.ProjectileBoundsSystem;
import pl.mzebrows.pwt.sim.system.ReaperSystem;
import pl.mzebrows.pwt.sim.system.ReinforcementSystem;
import pl.mzebrows.pwt.sim.system.RespawnSystem;
import pl.mzebrows.pwt.sim.system.SlickSystem;
import pl.mzebrows.pwt.sim.system.SquadOrderSystem;
import pl.mzebrows.pwt.sim.system.StrikeSystem;
import pl.mzebrows.pwt.sim.system.StunSystem;
import pl.mzebrows.pwt.sim.system.TrooperCombatSystem;
import pl.mzebrows.pwt.sim.system.TrooperMindSystem;
import pl.mzebrows.pwt.sim.system.TrooperMoveSystem;
import pl.mzebrows.pwt.sim.system.TurretSystem;
import pl.mzebrows.pwt.sim.system.WanderSystem;
import pl.mzebrows.pwt.sim.system.WaveSystem;
import pl.mzebrows.pwt.sim.system.WeaveSystem;
import pl.mzebrows.pwt.sim.upgrade.UpgradeSystem;

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
        rows.add(new Before(DyingSystem.class, RespawnSystem.class, "reactions run while the dead are still there"));
        rows.add(new Before(RespawnSystem.class, ProjectileBoundsSystem.class, "a spent shot joins the death chain"));
        rows.add(new Before(
                ProjectileBoundsSystem.class,
                BloatSystem.class,
                "a miss is marked dead while the reactions can still see it"));
        rows.add(new Before(BloatSystem.class, BlastSystem.class, "a body that fills this tick explodes this tick"));
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
        rows.add(new Before(WaveSystem.class, ControlPointSystem.class, "a schedule armed this tick sends next tick"));
        rows.add(new Before(
                ControlPointSystem.class, ObjectiveSystem.class, "the post exists on the tick it is asked about"));
        rows.add(new Before(
                CollisionSystem.class,
                FacingSystem.class,
                "a facing is where the tick ended looking - perception reads it next tick, the arc the renderer drew"));
        rows.add(new Before(
                WaveSystem.class, UpgradeSystem.class, "a wave cleared this tick earns its draft this tick"));
        rows.add(new Before(
                BarrageSystem.class, UpgradeSystem.class, "a boss felled this tick earns its draft this tick"));
        rows.add(new Before(
                ControlPointSystem.class, UpgradeSystem.class, "a point taken this tick earns its draft this tick"));
        for (Class<? extends EntitySystem> reader : List.of(
                CommanderControlSystem.class,
                TurretSystem.class,
                TrooperMindSystem.class,
                TrooperCombatSystem.class,
                EnemyRangedSystem.class,
                CollisionSystem.class,
                GrenadeSystem.class,
                PickupSystem.class,
                ReinforcementSystem.class,
                DriveSystem.class,
                ActionSystem.class,
                AidSystem.class)) {
            rows.add(new Before(reader, UpgradeSystem.class, "every reader in a tick sees the same ledger"));
        }
        return rows.stream().map(Arguments::of);
    }

    private static void assertImmediatelyAfter(Class<?> later, Class<?> earlier) {
        List<Class<?>> order = registered();

        assertThat(order.indexOf(later)).isEqualTo(order.indexOf(earlier) + 1);
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
