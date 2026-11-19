package pl.mzebrows.pwt.relay;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** What keeps the relay a headless machine that knows the wire and nothing of the game (ADR 0023). */
@AnalyzeClasses(packages = "pl.mzebrows.pwt.relay", importOptions = ImportOption.DoNotIncludeTests.class)
final class RelayArchitectureTest {

    @ArchTest
    static final ArchRule relayKnowsNothingOfTheWindow = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.badlogic..")
            .because("the relay runs headless, on a machine with no window");

    @ArchTest
    static final ArchRule nettyStaysInsideItsAdapter = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.netty..")
            .because("the library behind the transport port is the replaceable part (ADR 0012); the relay is"
                    + " handed a Transport and never builds one");

    @ArchTest
    static final ArchRule theRelayReadsTheClockThroughTheClockPort = noClasses()
            .should()
            .callMethod(System.class, "nanoTime")
            .orShould()
            .callMethod(System.class, "currentTimeMillis")
            .because("a test drives the relay with a ManualClock");

    @ArchTest
    static final ArchRule theRelayReachesNothingOfTheGame = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("pl.mzebrows.pwt.sim..", "pl.mzebrows.pwt.map..")
            .because("the relay is blind to the game (ADR 0023); the config plumbing is NetConfig's, not the relay's");
}
