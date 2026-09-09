package pl.mzebrows.pwt.map;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards the reason this is a module rather than a package: map generation can never reach for an
 * entity, a renderer or a clock. The Gradle graph already keeps those jars off this classpath, so
 * the first rule here is belt to that braces - it is the one that would catch somebody "just adding
 * a dependency" to make a generator easier to write.
 *
 * <p>The rest mirror {@code sim}'s determinism rules, because a level laid out differently on two
 * machines breaks replay exactly as thoroughly as a simulation that does.
 */
@AnalyzeClasses(packages = "pl.mzebrows.pwt.map", importOptions = ImportOption.DoNotIncludeTests.class)
final class MapArchitectureTest {

    @ArchTest
    static final ArchRule mapKnowsNothingOfTheGameAroundIt = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.badlogic..", "pl.mzebrows.pwt.sim..", "pl.mzebrows.pwt.client..")
            .because("this module owns the field; sim owns what stands on it. A generator that can see an"
                    + " Entity will eventually spawn one, and then map generation and gameplay are one thing"
                    + " again");

    @ArchTest
    static final ArchRule mapDoesNotUseUnseededRandomnessOrHashIteration = noClasses()
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(java.util.Random.class, java.util.HashMap.class, java.util.HashSet.class)
            .because("the caller's seeded Randomness is the only source, and hash iteration order is not"
                    + " reproducible - a map must lay out identically on every machine");

    @ArchTest
    static final ArchRule mapDoesNotUseMathRandom = noClasses()
            .should()
            .callMethod(Math.class, "random")
            .because("randomness must come from the seeded stream so a level can be replayed");

    @ArchTest
    static final ArchRule mapDoesNotUseNonReproducibleMath = noClasses()
            .should()
            .callMethod(Math.class, "sin", double.class)
            .orShould()
            .callMethod(Math.class, "cos", double.class)
            .orShould()
            .callMethod(Math.class, "atan2", double.class, double.class)
            .orShould()
            .callMethod(Math.class, "pow", double.class, double.class)
            .orShould()
            .callMethod(Math.class, "exp", double.class)
            .orShould()
            .callMethod(Math.class, "log", double.class)
            .orShould()
            .callMethod(Math.class, "hypot", double.class, double.class)
            .because("Math's transcendentals are only specified to within 1 ulp and may differ between JVM"
                    + " implementations; StrictMath is bit-identical everywhere. Math.sqrt, abs, min, max and"
                    + " floor are exactly specified and remain fine");

    @ArchTest
    static final ArchRule mapDoesNotReadTheWallClock = noClasses()
            .should()
            .callMethod(System.class, "currentTimeMillis")
            .orShould()
            .callMethod(System.class, "nanoTime")
            .because("a map is a function of its seed and nothing else");
}
