package pl.mzebrows.pwt.client;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards the two rules that keep the client honest, both learned the hard way.
 *
 * <p>The first is a crash: a screen that switches itself does so from inside its own {@code render},
 * and {@code setScreen} runs the outgoing screen's {@code hide()} synchronously. That frees GL
 * resources the rest of the frame is still drawing with, and the JVM dies with an access violation -
 * no exception, no stack trace, nothing a test could have caught after the fact.
 *
 * <p>The second is the reason the first went unnoticed: decisions living inside classes that draw
 * cannot be tested, because a test has no GL context. Keeping them out of those classes is what
 * makes {@code ScenarioSessionTest} and {@code HarnessControlsTest} possible at all.
 */
@AnalyzeClasses(packages = "pl.mzebrows.pwt.client", importOptions = ImportOption.DoNotIncludeTests.class)
final class ClientArchitectureTest {

    @ArchTest
    static final ArchRule screensDoNotSwitchThemselves = noClasses()
            .that()
            .areAssignableTo(Screen.class)
            .should()
            .callMethod(Game.class, "setScreen", Screen.class)
            .because("switching inside a frame frees GL resources that frame is still drawing with, which"
                    + " crashes the JVM outright - ask GameContext.queueScreen instead and let the frame end");

    @ArchTest
    static final ArchRule screensCannotReachPastTheirContext = noClasses()
            .that()
            .areAssignableTo(Screen.class)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Game.class)
            .because("a screen gets GameContext, which deliberately has no setScreen on it; taking the Game"
                    + " itself puts the crash above back within reach");

    @ArchTest
    static final ArchRule decisionLogicDoesNotTouchTheRunningApplication = noClasses()
            .that()
            .haveSimpleNameEndingWith("Session")
            .or()
            .haveSimpleNameEndingWith("Controls")
            .or()
            .haveSimpleNameEndingWith("Selection")
            .or()
            .haveSimpleNameEndingWith("Director")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.badlogic.gdx.Gdx")
            .because("these hold what the client decides rather than what it draws, and they are only"
                    + " testable while they take their input as values - a KeyPresses, a delta - rather than"
                    + " reading a live device");
}
