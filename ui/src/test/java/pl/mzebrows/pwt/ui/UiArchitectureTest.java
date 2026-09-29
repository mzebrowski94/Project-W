package pl.mzebrows.pwt.ui;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The wall around the interface logic (ADR 0015): the module graph keeps {@code core}'s backends off
 * {@code ui}'s classpath, and these rules close what the classpath still offers - {@code gdx.jar} comes
 * with the simulation, and it carries {@code Gdx}, graphics and scene2d along with the math.
 */
@AnalyzeClasses(packages = "pl.mzebrows.pwt.ui", importOptions = ImportOption.DoNotIncludeTests.class)
final class UiArchitectureTest {

    private static final String SIM_COMPONENTS = "pl.mzebrows.pwt.sim.component";

    @ArchTest
    static final ArchRule theInterfaceLogicDrawsNothingAndReadsNoDevice = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.badlogic.gdx.Gdx")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.badlogic.gdx.Input")
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.badlogic.gdx.graphics..",
                    "com.badlogic.gdx.scenes..",
                    "com.badlogic.gdx.backends..",
                    "com.badlogic.gdx.controllers..")
            .because("what is decided here is tested without a display; core draws it and owns the devices"
                    + " (ADR 0015)");

    @ArchTest
    static final ArchRule theInterfaceLogicKnowsNeitherTheClientNorTheNetwork = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("pl.mzebrows.pwt.client..", "pl.mzebrows.pwt.net..")
            .because("the graph is core -> {ui, net} -> sim; the client hands ui values, and ui never sees"
                    + " how the world got here (ADR 0015)");

    @ArchTest
    static final ArchRule theInterfaceNeverWritesTheSimulation = noClasses()
            .should()
            .setFieldWhere(DescribedPredicate.describe(
                    "the field belongs to a simulation component",
                    (JavaFieldAccess access) ->
                            access.getTargetOwner().getPackageName().startsWith(SIM_COMPONENTS)))
            .because("the interface observes published state (G0004); a write from here would be a change"
                    + " to the world no tick made, and a desync online");

    @ArchTest
    static final ArchRule theInterfaceReadsUpgradesThroughTheirView = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("pl.mzebrows.pwt.sim.component.Upgrades")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("pl.mzebrows.pwt.sim.component.Drafts")
            .because("the ledger and the drafts are the upgrade package's; the interface reads them through"
                    + " UpgradeView alone (ADR 0017, fence 1)");

    @ArchTest
    static final ArchRule theInterfaceNeverStepsTheSimulation = noClasses()
            .should()
            .callMethodWhere(DescribedPredicate.describe(
                    "the call steps the simulation",
                    (JavaMethodCall call) ->
                            call.getTargetOwner().getFullName().equals("pl.mzebrows.pwt.sim.Simulation")
                                    && call.getName().equals("step")))
            .because("advancing the world is the session's job; the interface reads what a tick left behind");
}
