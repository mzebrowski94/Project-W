package pl.mzebrows.pwt.sim;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

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

    /** The collections the snapshot codec knows how to write, by the field that holds each. */
    private static final Set<String> NAMED_COLLECTIONS = Set.of("Waves.pending");

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
