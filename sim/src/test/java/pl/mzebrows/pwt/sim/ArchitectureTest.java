package pl.mzebrows.pwt.sim;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
            .callMethod(pl.mzebrows.pwt.sim.Simulation.class, "setCommanderInvulnerable", boolean.class)
            .because("god mode is a harness switch for testing by hand; pillar 2 says the commander can be"
                    + " killed, and content that turns that off would quietly repeal it");

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
            .callMethod(Math.class, "pow", double.class, double.class)
            .orShould()
            .callMethod(Math.class, "exp", double.class)
            .orShould()
            .callMethod(Math.class, "log", double.class)
            .orShould()
            .callMethod(Math.class, "log10", double.class)
            .orShould()
            .callMethod(Math.class, "cbrt", double.class)
            .orShould()
            .callMethod(Math.class, "hypot", double.class, double.class)
            .because("Math's transcendentals are only specified to within 1 ulp and may differ between JVM"
                    + " implementations; StrictMath is bit-identical everywhere. Math.sqrt, abs, min, max,"
                    + " floor, ceil and round are exactly specified and remain fine");

    @ArchTest
    static final ArchRule simulationDoesNotReadTheWallClock = noClasses()
            .should()
            .callMethod(System.class, "currentTimeMillis")
            .orShould()
            .callMethod(System.class, "nanoTime")
            .because("the simulation advances by fixed steps only, never by real time");
}
