package pl.mzebrows.pwt.sim;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.mzebrows.pwt.sim.upgrade.Reads;
import pl.mzebrows.pwt.sim.upgrade.Stat;

/**
 * Guards the one boundary the whole architecture rests on: the simulation stays headless and
 * deterministic. The Gradle module split keeps rendering libraries off this classpath; these rules
 * close the remaining gap, because gdx.jar itself contains graphics classes.
 */
@AnalyzeClasses(packages = "pl.mzebrows.pwt.sim", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    @ArchTest
    static final ArchRule simulationIsHeadless = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.badlogic.gdx.graphics..",
                    "com.badlogic.gdx.scenes..",
                    "com.badlogic.gdx.assets..",
                    "com.badlogic.gdx.audio..",
                    "com.badlogic.gdx.files..",
                    "com.badlogic.gdx.backends..")
            .because("simulation tests must run without a GL context or an Application instance");

    @ArchTest
    static final ArchRule simulationDoesNotTouchTheRunningApplication = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.badlogic.gdx.Gdx")
            .because("Gdx is the live application; the simulation must not know it exists");

    @ArchTest
    static final ArchRule simulationDoesNotReadInputDirectly = noClasses()
            .should()
            .dependOnClassesThat()
            .haveNameMatching("com\\.badlogic\\.gdx\\.Input.*")
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("com.badlogic.gdx.controllers..")
            .because("input reaches the simulation as a per-tick value object, never by polling a device -"
                    + " see the input invariant in docs/design/README.md");

    @ArchTest
    static final ArchRule simulationDoesNotUseUnseededRandomnessOrHashIteration = noClasses()
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(java.util.Random.class, java.util.HashMap.class, java.util.HashSet.class)
            .because("Rng is the only randomness source, and hash iteration order is not reproducible");

    @ArchTest
    static final ArchRule simulationDoesNotUseMathRandom = noClasses()
            .should()
            .callMethod(Math.class, "random")
            .because("randomness must come from the seeded Rng so runs can be replayed");

    @ArchTest
    static final ArchRule onlyTheReaperRemovesEntities = noClasses()
            .that()
            .doNotHaveSimpleName("ReaperSystem")
            .should()
            .callMethod(com.badlogic.ashley.core.Engine.class, "removeEntity", com.badlogic.ashley.core.Entity.class)
            .orShould()
            .callMethod(
                    com.badlogic.ashley.core.PooledEngine.class, "removeEntity", com.badlogic.ashley.core.Entity.class)
            .because("the gap between being marked dead and being reaped is where every death reaction runs;"
                    + " an entity that vanishes mid-tick takes its own funeral with it - mark it Dead instead");

    @ArchTest
    static final ArchRule onlyTheSpawnerChangesWhatAnEntityIsMadeOf = noClasses()
            .that()
            .doNotHaveSimpleName("Spawner")
            // A snapshot being restored is entities being spawned from what another machine wrote down.
            .and()
            .doNotHaveSimpleName("WorldCodec")
            .should()
            .callMethod(com.badlogic.ashley.core.Entity.class, "add", com.badlogic.ashley.core.Component.class)
            .orShould()
            .callMethod(com.badlogic.ashley.core.Entity.class, "remove", Class.class)
            .because("adding or removing a component mid-tick mutates the families being iterated and silently"
                    + " skips entities; state that changes belongs in a field on a component the entity already"
                    + " carries - a strain, a phase, a timer - which is why Damage, Dying, Stunned and Strain are"
                    + " all permanent");

    @ArchTest
    static final ArchRule scenariosCannotSwitchOnGodMode = noClasses()
            .that()
            .resideInAPackage("pl.mzebrows.pwt.sim.scenario..")
            .should()
            .callMethod(PlayerInputs.class, "switchInvulnerability", boolean.class)
            .orShould()
            .accessField(pl.mzebrows.pwt.sim.component.Level.class, "commandersInvulnerable")
            .because("god mode is a harness switch for testing by hand; pillar 2 says the commander can be"
                    + " killed, and content that turns that off would quietly repeal it");

    /**
     * Kept deliberately identical to {@code MapArchitectureTest#mapDoesNotUseNonReproducibleMath}: a level laid out
     * differently on two machines breaks replay exactly as thoroughly as a simulation that does. Add a
     * method to both or to neither.
     */
    @ArchTest
    static final ArchRule simulationDoesNotUseNonReproducibleMath = noClasses()
            .should()
            .callMethod(Math.class, "sin", double.class)
            .orShould()
            .callMethod(Math.class, "cos", double.class)
            .orShould()
            .callMethod(Math.class, "tan", double.class)
            .orShould()
            .callMethod(Math.class, "asin", double.class)
            .orShould()
            .callMethod(Math.class, "acos", double.class)
            .orShould()
            .callMethod(Math.class, "atan", double.class)
            .orShould()
            .callMethod(Math.class, "atan2", double.class, double.class)
            .orShould()
            .callMethod(Math.class, "sinh", double.class)
            .orShould()
            .callMethod(Math.class, "cosh", double.class)
            .orShould()
            .callMethod(Math.class, "tanh", double.class)
            .orShould()
            .callMethod(Math.class, "exp", double.class)
            .orShould()
            .callMethod(Math.class, "expm1", double.class)
            .orShould()
            .callMethod(Math.class, "log", double.class)
            .orShould()
            .callMethod(Math.class, "log10", double.class)
            .orShould()
            .callMethod(Math.class, "log1p", double.class)
            .orShould()
            .callMethod(Math.class, "pow", double.class, double.class)
            .orShould()
            .callMethod(Math.class, "cbrt", double.class)
            .orShould()
            .callMethod(Math.class, "hypot", double.class, double.class)
            .because("Math's transcendentals are only specified to within 1 ulp - 2.5 for the hyperbolic"
                    + " functions - and may differ between JVM implementations; StrictMath is bit-identical"
                    + " everywhere. Math.sqrt, abs, min, max, floor, ceil and round are exactly specified and"
                    + " remain fine");

    @ArchTest
    static final ArchRule simulationDoesNotReadTheWallClock = noClasses()
            .should()
            .callMethod(System.class, "currentTimeMillis")
            .orShould()
            .callMethod(System.class, "nanoTime")
            .because("the simulation advances by fixed steps only, never by real time");

    @ArchTest
    static final ArchRule onlyTheStreamTableStartsARandomStream = noClasses()
            .that()
            .doNotHaveSimpleName("Streams")
            .and()
            .doNotHaveSimpleName("Rng")
            .and()
            .doNotHaveSimpleName("Scenarios")
            .should()
            .callMethod(Rng.class, "stream", long.class, RngStream.class)
            .orShould()
            .callConstructor(Rng.class, long.class)
            .because("every stream a level draws from lives in one table a snapshot can write (ADR 0014);"
                    + " a stream started anywhere else is state no snapshot sees. Scenarios lays the map out"
                    + " before the world exists, and the map is rebuilt from the seed rather than carried");

    @ArchTest
    static final ArchRule systemsHoldNoStateAcrossTicks = fields().that()
            .areDeclaredInClassesThat()
            .areAssignableTo(EntitySystem.class)
            // And every helper a system hands part of its work to - TrooperFight and its siblings - so
            // splitting a system into classes cannot move its state out of this rule's sight.
            .or()
            .areDeclaredInClassesThat()
            .resideInAPackage("..sim.system..")
            // And the upgrade package, whose read port is called by systems on every tick (ADR 0017).
            .or()
            .areDeclaredInClassesThat()
            .resideInAPackage("..sim.upgrade..")
            .and()
            .doNotHaveModifier(JavaModifier.STATIC)
            .and()
            .doNotHaveModifier(JavaModifier.FINAL)
            .and()
            .doNotHaveRawType(ImmutableArray.class)
            .should()
            .beAnnotatedWith(NotState.class)
            .because("what must survive a tick lives on a component, where a snapshot carries it (ADR 0014);"
                    + " a system may keep scratch it overwrites before reading, or a pure cache, and says"
                    + " which with @NotState");

    @ArchTest
    static final ArchRule onlyTheUpgradePackageTouchesTheLedgerAndTheDrafts = noClasses()
            .that()
            .resideOutsideOfPackages("..sim.upgrade..", "..sim.component..")
            .and()
            .doNotBelongToAnyOf(WorldCodec.class, Spawner.class)
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(pl.mzebrows.pwt.sim.component.Upgrades.class, pl.mzebrows.pwt.sim.component.Drafts.class)
            .because("the ledger and the drafts are written by the upgrade package alone and read through"
                    + " Modifiers (ADR 0017); the codec writes them and the spawner creates them");

    @ArchTest
    static final ArchRule upgradesAreCalledAndDoNotCall = noClasses()
            .that()
            .resideInAPackage("..sim.upgrade..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..sim.system..", "..sim.scenario..")
            .because("the upgrade package is called and does not call (ADR 0017): a rule asks Modifiers for a"
                    + " number, and Modifiers asks nothing of the rules");

    /**
     * Every number an upgrade may move is read in exactly one place (ADR 0017): one {@code @Reads} method
     * per {@link Stat}, with one call to it outside the upgrade package. Two readers are two places that
     * must agree about one number; none is a number somebody tunes to no effect.
     */
    @ArchTest
    static void everyStatIsReadInExactlyOnePlace(JavaClasses classes) {
        Map<Stat, List<JavaMethod>> readers = new EnumMap<>(Stat.class);
        for (JavaClass type : classes) {
            for (JavaMethod method : type.getMethods()) {
                method.tryGetAnnotationOfType(Reads.class)
                        .ifPresent(reads -> readers.computeIfAbsent(reads.value(), stat -> new ArrayList<>())
                                .add(method));
            }
        }
        List<String> problems = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            List<JavaMethod> methods = readers.getOrDefault(stat, List.of());
            if (methods.size() != 1) {
                problems.add(stat + " is on " + methods.size() + " @Reads methods, not one");
                continue;
            }
            JavaMethod method = methods.get(0);
            long calls = method.getCallsOfSelf().stream()
                    .filter(call -> !call.getOriginOwner().getPackageName().startsWith(UPGRADE))
                    .count();
            if (calls != 1) {
                problems.add(stat + " is read through " + method.getFullName() + " from " + calls
                        + " places outside the upgrade package, not one");
            }
        }
        org.assertj.core.api.Assertions.assertThat(problems).isEmpty();
    }

    private static final String UPGRADE = "pl.mzebrows.pwt.sim.upgrade";

    /** The collections the snapshot codec knows how to write, by the field that holds each. */
    private static final Set<String> NAMED_COLLECTIONS = Set.of(
            "Waves.pending",
            "Lair.pending",
            "Upgrades.held",
            "Drafts.pending",
            "Drafts.cards",
            "Drafts.passed",
            "Drafts.submitted",
            "Presence.away");

    @ArchTest
    static final ArchRule componentFieldsAreWhatASnapshotCanWrite = fields().that()
            .areDeclaredInClassesThat()
            .areAssignableTo(Component.class)
            .and()
            .doNotHaveModifier(JavaModifier.STATIC)
            .should(haveATypeTheSnapshotCanWrite())
            .because("a snapshot is written from the components' own fields (ADR 0014): a primitive, an enum,"
                    + " an Entity, an immutable record, or a collection the codec names");

    private static ArchCondition<JavaField> haveATypeTheSnapshotCanWrite() {
        DescribedPredicate<JavaClass> writable = DescribedPredicate.describe(
                "writable",
                type -> type.isPrimitive() || type.isEnum() || type.isRecord() || type.isEquivalentTo(Entity.class));
        return new ArchCondition<>("have a type the snapshot can write") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                String name = field.getOwner().getSimpleName() + "." + field.getName();
                boolean ok = writable.test(field.getRawType()) || NAMED_COLLECTIONS.contains(name);
                if (!ok) {
                    events.add(SimpleConditionEvent.violated(
                            field, name + " is a " + field.getRawType().getName() + ", which no snapshot writes"));
                }
            }
        };
    }
}
