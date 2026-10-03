package pl.mzebrows.pwt.sim;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.EntitySystem;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which classes write each component, measured from the bytecode (ADR 0019).
 *
 * <p>A write is a field of a component set from outside it, a call to one of its <em>changing</em>
 * methods, or a mutating call on a collection one of its fields holds. A method is changing if it does
 * any of those to its own class, or calls another changing method of it. A write is credited to every
 * system whose code reaches it, following calls through any class in {@code sim} that is not a
 * component; a write no system reaches is credited to the class that makes it.
 *
 * <p>Building a world is not a write: a {@code spawn} method of {@code Spawner} or {@code Simulation}, a
 * scenario laying out its level, {@code WorldCodec} restoring a snapshot, and whatever {@code Spawner}
 * does that no system asked for. A system asking {@code Spawner} to change an entity that already exists
 * - {@code applyWeapon}, {@code applyStrain} - is that system writing it.
 */
final class ComponentWriters {

    private static final String SIM = "pl.mzebrows.pwt.sim";

    private static final String SCENARIO = SIM + ".scenario";

    /** What a call on a collection is called when it changes the collection. */
    private static final Set<String> MUTATORS = Set.of(
            "add",
            "addAll",
            "insert",
            "set",
            "put",
            "remove",
            "removeIndex",
            "removeValue",
            "removeRange",
            "removeAll",
            "removeIf",
            "pop",
            "clear",
            "swap",
            "sort",
            "reverse",
            "shuffle",
            "truncate",
            "setSize",
            "replaceAll");

    private final JavaClasses classes;
    private final Map<JavaClass, Set<JavaMethod>> changing = new LinkedHashMap<>();

    private ComponentWriters(JavaClasses classes) {
        this.classes = classes;
    }

    /** Every component among {@code classes}, by simple name, to the simple names of what writes it. */
    static Map<String, Set<String>> of(JavaClasses classes) {
        return new ComponentWriters(classes).measure();
    }

    /**
     * Every component one class writes, itself or through anything it calls in {@code sim}, by simple name -
     * what a {@code query} must keep empty (ADR 0020).
     */
    static Set<String> writtenBy(JavaClasses classes, JavaClass origin) {
        ComponentWriters measure = new ComponentWriters(classes);
        for (JavaClass type : classes) {
            if (component(type)) {
                measure.changingMethods(type);
            }
        }
        Set<String> written = new TreeSet<>();
        for (JavaCodeUnit unit : measure.reachedFrom(origin)) {
            for (JavaClass type : measure.writes(unit)) {
                written.add(type.getSimpleName());
            }
        }
        return written;
    }

    private Map<String, Set<String>> measure() {
        Map<String, Set<String>> writers = new TreeMap<>();
        for (JavaClass type : classes) {
            if (component(type)) {
                writers.put(type.getSimpleName(), new TreeSet<>());
                changingMethods(type);
            }
        }
        Set<JavaCodeUnit> reached = new LinkedHashSet<>();
        for (JavaClass type : classes) {
            if (system(type)) {
                for (JavaCodeUnit unit : reachedFrom(type)) {
                    reached.add(unit);
                    credit(writers, unit, type.getSimpleName());
                }
            }
        }
        for (JavaClass type : classes) {
            if (followed(type) && !nestedIn(type, "Spawner")) {
                for (JavaCodeUnit unit : type.getCodeUnits()) {
                    if (!reached.contains(unit) && !builds(unit)) {
                        credit(writers, unit, maker(type).getSimpleName());
                    }
                }
            }
        }
        return writers;
    }

    private void credit(Map<String, Set<String>> writers, JavaCodeUnit unit, String writer) {
        for (JavaClass written : writes(unit)) {
            writers.computeIfAbsent(written.getSimpleName(), name -> new TreeSet<>())
                    .add(writer);
        }
    }

    /** Every code unit of a class, of the classes nested in it, and of everything they call in {@code sim}. */
    private Set<JavaCodeUnit> reachedFrom(JavaClass system) {
        Set<JavaCodeUnit> seen = new LinkedHashSet<>();
        Deque<JavaCodeUnit> next = new ArrayDeque<>();
        for (JavaClass type : classes) {
            if (sim(type) && maker(type).equals(system)) {
                next.addAll(type.getCodeUnits());
            }
        }
        while (!next.isEmpty()) {
            JavaCodeUnit unit = next.pop();
            if (!seen.add(unit)) {
                continue;
            }
            for (JavaCall<?> call : unit.getCallsFromSelf()) {
                for (JavaCodeUnit target : targets(call.getTarget())) {
                    if (followed(target.getOwner()) && !builds(target)) {
                        next.push(target);
                    }
                }
            }
        }
        return seen;
    }

    /** What a call may run: the member it names, and every override of it among the imported classes. */
    private List<JavaCodeUnit> targets(AccessTarget.CodeUnitCallTarget call) {
        Optional<? extends JavaCodeUnit> named = call.resolveMember();
        if (named.isEmpty()) {
            return List.of();
        }
        List<JavaCodeUnit> all = new ArrayList<>();
        all.add(named.get());
        if (named.get() instanceof JavaMethod method && !method.getModifiers().contains(JavaModifier.STATIC)) {
            for (JavaClass type : classes) {
                if (!type.equals(method.getOwner())
                        && type.isAssignableTo(method.getOwner().getName())) {
                    type.tryGetMethod(method.getName(), parameterNames(method))
                            .filter(override -> override.getOwner().equals(type))
                            .ifPresent(all::add);
                }
            }
        }
        return all;
    }

    private static String[] parameterNames(JavaMethod method) {
        return method.getRawParameterTypes().stream().map(JavaClass::getName).toArray(String[]::new);
    }

    /** The components a code unit writes directly, other than its own class. */
    private Set<JavaClass> writes(JavaCodeUnit unit) {
        Set<JavaClass> written = new LinkedHashSet<>();
        for (JavaClass type : changes(unit)) {
            if (!type.equals(unit.getOwner())) {
                written.add(type);
            }
        }
        return written;
    }

    /** Every component a code unit changes, its own class included. */
    private Set<JavaClass> changes(JavaCodeUnit unit) {
        Set<JavaClass> changed = new LinkedHashSet<>();
        for (JavaFieldAccess access : unit.getFieldAccesses()) {
            Optional<JavaField> field = access.getTarget().resolveMember();
            if (field.isEmpty() || field.get().getModifiers().contains(JavaModifier.STATIC)) {
                continue;
            }
            JavaClass owner = field.get().getOwner();
            if (!component(owner)) {
                continue;
            }
            if (access.getAccessType() == JavaFieldAccess.AccessType.SET || mutatesWhatItHolds(unit, field.get())) {
                changed.add(owner);
            }
        }
        for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
            Optional<JavaMethod> method = call.getTarget().resolveMember();
            if (method.isPresent()
                    && changing.getOrDefault(method.get().getOwner(), Set.of()).contains(method.get())) {
                changed.add(method.get().getOwner());
            }
        }
        return changed;
    }

    /** True if the code unit makes a changing call on the type of collection the field holds. */
    private static boolean mutatesWhatItHolds(JavaCodeUnit unit, JavaField field) {
        JavaClass held = field.getRawType();
        if (held.isPrimitive() || held.isEnum() || held.isRecord()) {
            return false;
        }
        for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
            if (MUTATORS.contains(call.getName())
                    && held.isAssignableTo(call.getTargetOwner().getName())) {
                return true;
            }
        }
        return false;
    }

    /** A component's changing methods: a fixed point, since one may change it only by calling another. */
    private Set<JavaMethod> changingMethods(JavaClass type) {
        Set<JavaMethod> found = new LinkedHashSet<>();
        changing.put(type, found);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (JavaMethod method : type.getMethods()) {
                if (!found.contains(method) && changes(method).contains(type)) {
                    found.add(method);
                    grew = true;
                }
            }
        }
        return found;
    }

    /** The class a write is credited to when no system reaches it: the system it is nested in, if any. */
    private static JavaClass maker(JavaClass type) {
        JavaClass named = type;
        for (Optional<JavaClass> at = Optional.of(type);
                at.isPresent();
                at = at.get().getEnclosingClass()) {
            if (system(at.get())) {
                return at.get();
            }
            if (named.getSimpleName().isEmpty()) {
                named = at.get();
            }
        }
        return named;
    }

    /** True if the code unit builds a world rather than runs one. */
    private static boolean builds(JavaCodeUnit unit) {
        JavaClass owner = unit.getOwner();
        boolean spawns =
                unit.getName().startsWith("spawn") && (nestedIn(owner, "Spawner") || nestedIn(owner, "Simulation"));
        return spawns || nestedIn(owner, "WorldCodec") || owner.getPackageName().startsWith(SCENARIO);
    }

    private static boolean nestedIn(JavaClass type, String simpleName) {
        for (Optional<JavaClass> at = Optional.of(type);
                at.isPresent();
                at = at.get().getEnclosingClass()) {
            if (at.get().getSimpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean followed(JavaClass type) {
        return sim(type) && !component(type);
    }

    private static boolean component(JavaClass type) {
        return sim(type) && !type.isInterface() && type.isAssignableTo(Component.class);
    }

    private static boolean system(JavaClass type) {
        return sim(type) && type.isAssignableTo(EntitySystem.class);
    }

    private static boolean sim(JavaClass type) {
        return type.getPackageName().startsWith(SIM);
    }
}
