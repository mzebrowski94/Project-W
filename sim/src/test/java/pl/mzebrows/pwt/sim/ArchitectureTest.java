package pl.mzebrows.pwt.sim;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pl.mzebrows.pwt.sim.component.Dying;
import pl.mzebrows.pwt.sim.component.Vitality;
import pl.mzebrows.pwt.sim.system.upgrade.Reads;
import pl.mzebrows.pwt.sim.system.upgrade.Stat;

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
                    + " see the input invariant in docs/design/invariants/players.md");

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
            .and()
            .doNotHaveSimpleName("Mission")
            .should()
            .callMethod(Rng.class, "stream", long.class, RngStream.class)
            .orShould()
            .callConstructor(Rng.class, long.class)
            .because("every stream a level draws from lives in one table a snapshot can write (ADR 0014);"
                    + " a stream started anywhere else is state no snapshot sees. Scenarios lays the map out"
                    + " before the world exists, and the map is rebuilt from the seed rather than carried; a"
                    + " Mission is laid out and placed before it too, from the map stream and from MISSION,"
                    + " which is only ever derived from, so a fresh one is the table's (G0065)");

    @ArchTest
    static final ArchRule systemsHoldNoStateAcrossTicks = fields().that()
            .areDeclaredInClassesThat()
            .areAssignableTo(EntitySystem.class)
            // And every helper a system hands part of its work to - TrooperFight and its siblings - so
            // splitting a system into classes cannot move its state out of this rule's sight.
            .or()
            .areDeclaredInClassesThat()
            // The upgrade package among them, whose read port is called by systems on every tick (ADR 0017).
            .resideInAPackage("..sim.system..")
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
                    + " which with @NotState")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule onlyTheUpgradePackageTouchesTheLedgerAndTheDrafts = noClasses()
            .that()
            .resideOutsideOfPackages("..sim.system.upgrade..", "..sim.component..")
            .and()
            .doNotBelongToAnyOf(WorldCodec.class, Spawner.class)
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(pl.mzebrows.pwt.sim.component.Upgrades.class, pl.mzebrows.pwt.sim.component.Drafts.class)
            .because("the ledger and the drafts are written by the upgrade package alone and read through"
                    + " Modifiers (ADR 0017); the codec writes them and the spawner creates them")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule upgradesAreCalledAndDoNotCall = noClasses()
            .that()
            .resideInAPackage("..sim.system.upgrade..")
            .should()
            .dependOnClassesThat(JavaClass.Predicates.resideInAPackage("..sim.scenario..")
                    .or(JavaClass.Predicates.resideInAPackage("..sim.system..")
                            .and(JavaClass.Predicates.resideOutsideOfPackage("..sim.system.upgrade..")))
                    .as("reside in a scenario or another package under sim.system"))
            .because("the upgrade package is called and does not call (ADR 0017): a rule asks Modifiers for a"
                    + " number, and Modifiers asks nothing of the rules")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule onlyLifeReadsWhetherAUnitIsDownOrDying = noClasses()
            .that()
            .doNotBelongToAnyOf(Life.class, Vitality.class, Dying.class)
            .should()
            .accessField(Vitality.class, "begun")
            .orShould()
            .accessField(Dying.class, "begun")
            .because("two flags both named begun answer one question - on his feet, a casualty, or in his"
                    + " throes - and every copy of the answer was a chance to get it wrong; ask Life");

    @ArchTest
    static final ArchRule onlyMissionConfigReadsATier = noClasses()
            .that()
            .doNotBelongToAnyOf(MissionConfig.class, TierDef.class)
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(TierDef.class)
            .orShould()
            .callMethod(MissionConfig.class, "tiers")
            .orShould()
            .callMethod(MissionConfig.class, "rampPercent")
            .because("a tier is read in one place, MissionConfig, and folded into the config a world is built from"
                    + " (G0063): no system learns what a tier is, and its percents meet a whole quantity only in"
                    + " Whole");

    private static final String SYSTEM = "pl.mzebrows.pwt.sim.system";

    /** The fenced packages under {@code sim.system} (ADR 0020); nothing lives there outside one. */
    private static final List<String> FENCED = List.of(
            SYSTEM + ".query",
            SYSTEM + ".squad",
            SYSTEM + ".motion",
            SYSTEM + ".commander",
            SYSTEM + ".creature",
            SYSTEM + ".combat",
            SYSTEM + ".death",
            SYSTEM + ".level",
            SYSTEM + ".appearance",
            SYSTEM + ".upgrade");

    /**
     * The classes of a package that another fenced package may use, besides everything in {@code query} (ADR
     * 0020). Each is named in its own {@code package-info.java} too.
     */
    private static final Set<String> PORTS = Set.of(
            // Getting on a gun and off it, the commander's and a trooper's alike (G0024).
            SYSTEM + ".commander.Crews",
            // The one death path, for whatever kills (G0004).
            SYSTEM + ".death.Deaths",
            // What a pickup's grant does to whoever takes it, from the ground or from a crate.
            SYSTEM + ".combat.Grants",
            // The trigger pull, for everything that fires.
            SYSTEM + ".combat.Guns",
            // A throw, for everything that throws.
            SYSTEM + ".combat.Throws",
            // A brood put down round a place and off the men by it: a nest's, and a dead slinger's riders (G0074).
            SYSTEM + ".level.Brood",
            SYSTEM + ".level.Rouse",
            // The one way a rule asks for a number an upgrade may move (ADR 0017).
            SYSTEM + ".upgrade.Modifiers");

    private static final String BECAUSE_0020 = "ADR 0020: systems live in fenced packages by domain";

    @ArchTest
    static final ArchRule nothingUnderSystemLivesOutsideADeclaredPackage = classes()
            .that()
            .resideInAPackage(SYSTEM + "..")
            .should()
            .resideInAnyPackage(FENCED.toArray(String[]::new))
            .because(BECAUSE_0020 + "; a package nobody declared is one no fence covers")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule everySystemLivesUnderSystem = classes()
            .that()
            .areAssignableTo(EntitySystem.class)
            .should()
            .resideInAPackage(SYSTEM + "..")
            .because(BECAUSE_0020)
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule aFencedPackageUsesOnlyQueryAndPortsOfAnother = classes()
            .that()
            .resideInAPackage(SYSTEM + "..")
            .should(useOnlyQueryAndPortsOfOtherPackages())
            .because(BECAUSE_0020 + ": between packages go query, component, def, collision, the sim root and"
                    + " declared ports - never another package's system or helper")
            .allowEmptyShould(false);

    @ArchTest
    static final ArchRule queryIsPublicAndHoldsNothing = classes()
            .that()
            .resideInAPackage(SYSTEM + ".query")
            .and()
            .doNotHaveSimpleName("package-info")
            .and()
            .areTopLevelClasses()
            .should()
            .bePublic()
            .andShould()
            .haveOnlyFinalFields()
            .because(BECAUSE_0020 + ": query holds public, stateless questions about components")
            .allowEmptyShould(false);

    /** Nothing in {@code query} writes a component, itself or through what it calls (ADR 0020, ADR 0019's analysis). */
    @ArchTest
    static void queryWritesNothing(JavaClasses classes) {
        Map<String, Set<String>> writes = new TreeMap<>();
        for (JavaClass type : classes) {
            if (type.getPackageName().equals(SYSTEM + ".query")
                    && !type.getSimpleName().equals("package-info")) {
                writes.put(
                        type.getName().substring(SYSTEM.length() + ".query.".length()),
                        ComponentWriters.writtenBy(classes, type));
            }
        }
        org.assertj.core.api.Assertions.assertThat(writes)
                .as("classes in query")
                .isNotEmpty();
        org.assertj.core.api.Assertions.assertThat(writes)
                .as(BECAUSE_0020 + ": what writes is a port of the package it belongs to, not a query")
                .allSatisfy((type, written) -> org.assertj.core.api.Assertions.assertThat(written)
                        .as(type)
                        .isEmpty());
    }

    /** Any class under {@code sim.system} it uses from another package there is in {@code query} or a port. */
    private static ArchCondition<JavaClass> useOnlyQueryAndPortsOfOtherPackages() {
        return new ArchCondition<>("use only query and the ports of another package under sim.system") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    String home = target.getPackageName();
                    if (!home.startsWith(SYSTEM)
                            || home.equals(origin.getPackageName())
                            || home.equals(SYSTEM + ".query")
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

    private static final String UPGRADE = "pl.mzebrows.pwt.sim.system.upgrade";

    private static final Path COMPONENTS_DOC = Path.of("..", "docs", "components.md");

    private static final String WRITTEN_BY = "Written by";

    /**
     * Who writes each component is declared in the <em>Written by</em> column of {@code docs/components.md}
     * and measured from the bytecode by {@link ComponentWriters} (ADR 0019). Any difference fails, either
     * way: a writer nobody declared, and a declared one that no longer writes. The message is the cell to
     * put in the column.
     */
    @ArchTest
    static void everyComponentIsWrittenByExactlyItsDeclaredWriters(JavaClasses classes) throws IOException {
        Map<String, Set<String>> measured = ComponentWriters.of(classes);
        Map<String, Set<String>> declared = declaredWriters(Files.readAllLines(COMPONENTS_DOC));
        org.assertj.core.api.Assertions.assertThat(measured)
                .as("components measured")
                .isNotEmpty();
        org.assertj.core.api.Assertions.assertThat(declared)
                .as("components declared")
                .isNotEmpty();
        // Rows the index keeps for what is not a component - the Components table itself - are not compared.
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : measured.entrySet()) {
            String component = entry.getKey();
            Set<String> actual = entry.getValue();
            Set<String> claimed = declared.get(component);
            if (claimed == null) {
                problems.add(component + " has no row in docs/components.md");
            } else if (!actual.equals(claimed)) {
                problems.add(component + " is written by " + cell(actual) + ", not " + cell(claimed));
            }
        }
        org.assertj.core.api.Assertions.assertThat(problems)
                .as("the Written by column of docs/components.md (ADR 0019)")
                .isEmpty();
    }

    /** Each component's row of the index, to the names in backticks in its Written by cell. */
    private static Map<String, Set<String>> declaredWriters(List<String> doc) {
        Map<String, Set<String>> declared = new TreeMap<>();
        int column = -1;
        for (String line : doc) {
            if (!line.startsWith("|")) {
                continue;
            }
            List<String> cells = List.of(line.split("\\|", -1));
            if (cells.contains(" " + WRITTEN_BY + " ")) {
                column = cells.indexOf(" " + WRITTEN_BY + " ");
                continue;
            }
            Matcher row = COMPONENT_ROW.matcher(cells.get(1));
            if (column < 0 || !row.matches()) {
                continue;
            }
            Set<String> writers = new TreeSet<>();
            Matcher name = BACKTICKED.matcher(cells.get(column));
            while (name.find()) {
                writers.add(name.group(1));
            }
            declared.put(row.group(1), writers);
        }
        return declared;
    }

    private static final Pattern COMPONENT_ROW = Pattern.compile(" \\*\\*(\\w+)\\*\\* ");

    private static final Pattern BACKTICKED = Pattern.compile("`(\\w+)`");

    /** Writers the way the column writes them. */
    private static String cell(Set<String> writers) {
        if (writers.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        for (String writer : writers) {
            out.append(out.isEmpty() ? "" : ", ").append('`').append(writer).append('`');
        }
        return out.toString();
    }

    /** The collections the snapshot codec knows how to write, by the field that holds each. */
    private static final Set<String> NAMED_COLLECTIONS = Set.of(
            "Waves.pending",
            "Lair.pending",
            "Upgrades.held",
            "Drafts.pending",
            "Drafts.cards",
            "Drafts.passed",
            "Drafts.submitted",
            "Presence.away",
            "Presence.seats");

    /**
     * Hit points and damage are whole numbers (G0054). A fraction arises only inside a percentage and is
     * rounded there, once, by {@code Whole}; a float anywhere a hit point is held or handed on would let
     * one back in - and with it a breakpoint that moves by an ulp. Held by name, because that is how the
     * codebase names them: every field and every method result whose name says health or damage, the
     * amount a blow owes, and the whole of {@code Health}.
     */
    @ArchTest
    static final ArchRule hitPointsAndDamageAreWholeNumbers = fields().that(holdHitPoints())
            .should(notBeAFraction())
            .because("hit points and damage are whole numbers, rounded once where a percentage is applied"
                    + " (G0054, Whole.percentOf) - never a float that a breakpoint can slip an ulp against");

    @ArchTest
    static final ArchRule hitPointsAndDamageAreHandedOnWhole = methods()
            .that(returnHitPoints())
            .should(notReturnAFraction())
            .because("a method that answers with hit points or damage answers with a whole number (G0054)");

    private static final String HIT_POINTS = "(?i).*(health|damage).*|amount";

    private static DescribedPredicate<JavaField> holdHitPoints() {
        return DescribedPredicate.describe(
                "hold hit points or damage",
                field -> !field.getModifiers().contains(JavaModifier.STATIC)
                        && (field.getName().matches(HIT_POINTS)
                                || field.getOwner().isEquivalentTo(pl.mzebrows.pwt.sim.component.Health.class)));
    }

    private static DescribedPredicate<JavaMethod> returnHitPoints() {
        return DescribedPredicate.describe(
                "answer with hit points or damage", method -> method.getName().matches(HIT_POINTS));
    }

    private static ArchCondition<JavaField> notBeAFraction() {
        return new ArchCondition<>("not be a float or a double") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                if (fraction(field.getRawType())) {
                    events.add(SimpleConditionEvent.violated(
                            field,
                            field.getFullName() + " is a " + field.getRawType().getName()));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notReturnAFraction() {
        return new ArchCondition<>("not return a float or a double") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (fraction(method.getRawReturnType())) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            method.getFullName() + " returns a "
                                    + method.getRawReturnType().getName()));
                }
            }
        };
    }

    private static boolean fraction(JavaClass type) {
        return type.isEquivalentTo(float.class)
                || type.isEquivalentTo(double.class)
                || type.isEquivalentTo(Float.class)
                || type.isEquivalentTo(Double.class);
    }

    /**
     * A timer counts whole ticks ({@code Ticks}). A duration may sit on a component in seconds, as the config
     * writes it, but only as a copy its own setter makes: a system writing one is counting time by adding a
     * float tick, and 1/60 is not exact in a float, so it runs a tick long. Held by name, as hit points are:
     * a float field whose name ends in {@code Seconds}.
     */
    @ArchTest
    static final ArchRule timeIsCountedInWholeTicks = fields().that(holdSeconds())
            .should(beWrittenOnlyByTheirOwnComponent())
            .because("a timer is a count of whole ticks set once from a duration (Ticks); adding float seconds"
                    + " tick by tick runs a tick long");

    private static DescribedPredicate<JavaField> holdSeconds() {
        return DescribedPredicate.describe(
                "hold a duration in seconds on a component",
                field -> !field.getModifiers().contains(JavaModifier.STATIC)
                        && field.getOwner().isAssignableTo(Component.class)
                        && field.getName().endsWith("Seconds")
                        && fraction(field.getRawType()));
    }

    private static ArchCondition<JavaField> beWrittenOnlyByTheirOwnComponent() {
        return new ArchCondition<>("be written only by their own component") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                for (JavaFieldAccess access : field.getAccessesToSelf()) {
                    if (access.getAccessType() == JavaFieldAccess.AccessType.SET
                            && !access.getOriginOwner().equals(field.getOwner())) {
                        events.add(SimpleConditionEvent.violated(
                                access, access.getOriginOwner().getSimpleName() + " writes " + field.getFullName()));
                    }
                }
            }
        };
    }

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
