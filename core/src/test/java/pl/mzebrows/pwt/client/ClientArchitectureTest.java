package pl.mzebrows.pwt.client;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;

/**
 * Guards the two rules that keep the client honest, both learned the hard way, and the fences between its
 * layers (ADR 0021).
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
    static final ArchRule theClientReadsUpgradesThroughTheirView = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("pl.mzebrows.pwt.sim.component.Upgrades")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("pl.mzebrows.pwt.sim.component.Drafts")
            .because("the ledger and the drafts are the upgrade package's; the client reads them through"
                    + " UpgradeView alone and changes them only by a pick (ADR 0017, fence 1)");

    private static final String CLIENT = "pl.mzebrows.pwt.client";

    /** The client's layers (ADR 0021); every class lives in one, or is the root's. */
    private static final List<String> LAYERS =
            List.of(CLIENT + ".input", CLIENT + ".draw", CLIENT + ".hud", CLIENT + ".play", CLIENT + ".config");

    /** What lives in the root: the application, its screens and what only they use, and the root's ports. */
    private static final Set<String> ROOT = Set.of(
            CLIENT + ".PwtGame",
            CLIENT + ".GameContext",
            CLIENT + ".MenuScreen",
            CLIENT + ".JoinScreen",
            CLIENT + ".ScenarioScreen",
            CLIENT + ".PlayMenus",
            CLIENT + ".ProvingPreview",
            CLIENT + ".HarnessControls",
            CLIENT + ".Log",
            CLIENT + ".Assets",
            CLIENT + ".LaunchOptions",
            CLIENT + ".LocalMachineId");

    /**
     * The classes a layer may use from another layer or from the root (ADR 0021). Each is named in its own
     * {@code package-info.java} too. What only the root uses - its entries - is public but no port.
     */
    private static final Set<String> PORTS = Set.of(
            // Every layer logs.
            CLIENT + ".Log",
            // Every asset path, in one place.
            CLIENT + ".Assets",
            // Which copy of the game this is; an online session names its log after it.
            CLIENT + ".LaunchOptions",
            // The bindings, which the hints name.
            CLIENT + ".input.ControlsConfig",
            // Who sits where on this machine, which the hints and the play key by.
            CLIENT + ".input.PlayerSeats",
            // What a pad's button is called.
            CLIENT + ".input.PadLabels",
            // A key, as the file and the hints name it.
            CLIENT + ".input.KeyCode",
            // The rendering tunables, every painter's style among them.
            CLIENT + ".draw.GraphicConfig",
            // A thing's colour, a player's among them.
            CLIENT + ".draw.Palette",
            // How much of the dev overlay is on, which lays the top bar out.
            CLIENT + ".play.OverlayMode",
            // The one way to read a config file.
            CLIENT + ".config.ConfigLoader",
            // A file off the classpath.
            CLIENT + ".config.GameProperties",
            // The sim's config, which a session's worlds are parsed with.
            CLIENT + ".config.SimConfigLoader");

    private static final String BECAUSE_0021 = "ADR 0021: the client lives in fenced packages by layer";

    @ArchTest
    static final ArchRule everyClassLivesInALayerOrIsTheRoots = classes()
            .that()
            .resideInAPackage(CLIENT + "..")
            .should(liveInALayerOrBeTheRoots())
            .because(BECAUSE_0021 + "; a package nobody declared is one no fence covers")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule aLayerUsesOnlyThePortsOfAnotherAndOfTheRoot = classes()
            .that()
            .resideInAnyPackage(LAYERS.toArray(String[]::new))
            .should(useOnlyPortsOfOtherPackages())
            .because(BECAUSE_0021 + ": between layers go the declared ports - never a screen, another layer's"
                    + " helper or what only the root drives")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule playAndConfigTouchNoLibGdx = noClasses()
            .that()
            .resideInAnyPackage(CLIENT + ".play", CLIENT + ".config")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.badlogic.gdx..")
            .because(BECAUSE_0021 + ": what the client plays and reads is decided from values, testable without a"
                    + " window, a GL context or a libGDX application")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule whatDrawsReadsNoDevice = noClasses()
            .that()
            .resideInAnyPackage(CLIENT + ".draw", CLIENT + ".hud")
            .should()
            .dependOnClassesThat()
            .haveNameMatching("com\\.badlogic\\.gdx\\.Input.*")
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("com.badlogic.gdx.controllers..")
            .because(BECAUSE_0021 + ": a device is read once a frame, in input, and handed on as values")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule inputDrawsNothing = noClasses()
            .that()
            .resideInAPackage(CLIENT + ".input")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.badlogic.gdx.graphics.g2d..", "com.badlogic.gdx.graphics.glutils..")
            .orShould()
            .dependOnClassesThat()
            .haveNameMatching("com\\.badlogic\\.gdx\\.(graphics\\.(GL\\d+|Texture|Pixmap)|utils\\.ScreenUtils)(\\$.*)?")
            .because(BECAUSE_0021 + ": input reads devices; what is drawn is draw's and hud's")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule decisionLogicDoesNotTouchTheRunningApplication = noClasses()
            .that()
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
                    + " reading a live device; they sit in packages that may touch Gdx, so the name fences them"
                    + " (ADR 0021; play's sessions are fenced by their package)")
            .allowEmptyShould(false);

    /** Directly in a layer, or directly in the root and named in {@link #ROOT}. */
    private static ArchCondition<JavaClass> liveInALayerOrBeTheRoots() {
        return new ArchCondition<>("live in a layer, or be one of the root's") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                String home = type.getPackageName();
                boolean placed = LAYERS.contains(home)
                        || (home.equals(CLIENT)
                                && (type.getSimpleName().equals("package-info")
                                        || ROOT.contains(outermost(type).getName())));
                if (!placed) {
                    events.add(SimpleConditionEvent.violated(
                            type,
                            type.getName() + " is in " + home + ", which is no layer, and the root does not name it"));
                }
            }
        };
    }

    /** Any client class it uses from another package is a declared port. */
    private static ArchCondition<JavaClass> useOnlyPortsOfOtherPackages() {
        return new ArchCondition<>("use only the ports of another client package") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    String home = target.getPackageName();
                    if (!(home.equals(CLIENT) || home.startsWith(CLIENT + "."))
                            || home.equals(origin.getPackageName())
                            || PORTS.contains(outermost(target).getName())) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(origin, dependency.getDescription()));
                }
            }
        };
    }

    private static JavaClass outermost(JavaClass type) {
        JavaClass at = type;
        while (at.getEnclosingClass().isPresent()) {
            at = at.getEnclosingClass().get();
        }
        return at;
    }
}
