package pl.mzebrows.pwt.net;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** What keeps networking a module with one replaceable library in it rather than a library with a game around it. */
@AnalyzeClasses(packages = "pl.mzebrows.pwt.net", importOptions = ImportOption.DoNotIncludeTests.class)
final class NetArchitectureTest {

    @ArchTest
    static final ArchRule netKnowsNothingOfTheWindow = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.badlogic..")
            .because("the network runs headless - under tests, and some day in a relay with no window at all");

    @ArchTest
    static final ArchRule nettyStaysInsideItsAdapter = noClasses()
            .that()
            .resideOutsideOfPackage("pl.mzebrows.pwt.net.netty..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.netty..")
            .because("the library behind the transport port is the replaceable part (ADR 0012), as"
                    + " gdx-controllers is behind the gamepad port (ADR 0011)");

    @ArchTest
    static final ArchRule onlyTheSystemClockReadsTheWallClock = noClasses()
            .that()
            .doNotHaveSimpleName("SystemClock")
            .should()
            .callMethod(System.class, "nanoTime")
            .orShould()
            .callMethod(System.class, "currentTimeMillis")
            .because("time reaches networking through the Clock port, so a test can drive a timeout or a"
                    + " second of lag without waiting for it");

    /**
     * The same rule for the rest of the JDK's clocks. {@code NetLog} stamps its file names and lines with
     * the date, which is for people and decides nothing.
     */
    @ArchTest
    static final ArchRule nothingElseReadsTheCalendarOrSleeps = noClasses()
            .that()
            .doNotHaveSimpleName("NetLog")
            .should()
            .callMethodWhere(new DescribedPredicate<JavaMethodCall>("java.time now() or Thread.sleep") {
                @Override
                public boolean test(JavaMethodCall call) {
                    String owner = call.getTargetOwner().getName();
                    return (call.getName().equals("now") && owner.startsWith("java.time."))
                            || (call.getName().equals("sleep") && owner.equals(Thread.class.getName()));
                }
            })
            .because("time reaches networking through the Clock port, and a thread that waits parks on a"
                    + " deadline from it");

    @ArchTest
    static final ArchRule nothingIsDeserialisedByClassName = noClasses()
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(ObjectInputStream.class, ObjectOutputStream.class)
            .because("every byte from the wire is read by code that knows exactly what it expects (ADR 0012)");
}
