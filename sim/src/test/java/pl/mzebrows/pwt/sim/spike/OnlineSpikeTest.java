package pl.mzebrows.pwt.sim.spike;

import static pl.mzebrows.pwt.sim.AlphaWorlds.godMode;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pl.mzebrows.pwt.sim.CommanderInput;
import pl.mzebrows.pwt.sim.Rng;
import pl.mzebrows.pwt.sim.SimConfig;
import pl.mzebrows.pwt.sim.Simulation;
import pl.mzebrows.pwt.sim.scenario.Scenario;
import pl.mzebrows.pwt.sim.scenario.Scenarios;

/**
 * THROWAWAY - online plan, stage 1. Delete once the cross-machine comparison and the step-time
 * numbers are recorded. Writes results under {@code ai/logs/net/} and asserts nothing.
 *
 * <p>Runs only with {@code PWT_SPIKE=1} in the environment, so {@code verify.sh} skips it. On Windows
 * (PowerShell): {@code $env:PWT_SPIKE=1; .\gradlew.bat :sim:test --tests "pl.mzebrows.pwt.sim.spike.OnlineSpikeTest"}
 */
@EnabledIfEnvironmentVariable(named = "PWT_SPIKE", matches = "1")
class OnlineSpikeTest {

    private static final long SEED = 20_260_906L;
    private static final int[] CHECKPOINTS = {600, 1800, 3600};

    @Test
    void writesAWorldFingerprintPerScenario() throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# ")
                .append(System.getProperty("os.name"))
                .append(' ')
                .append(System.getProperty("os.arch"))
                .append(" | ")
                .append(System.getProperty("java.vendor"))
                .append(' ')
                .append(System.getProperty("java.runtime.version"))
                .append('\n');
        for (Scenario scenario : Scenarios.all()) {
            Simulation simulation =
                    Scenarios.world(scenario, SimConfig.defaults().withSeed(SEED));
            int checkpoint = 0;
            for (int step = 1; step <= CHECKPOINTS[CHECKPOINTS.length - 1]; step++) {
                simulation.step(scriptedInput(step));
                if (step == CHECKPOINTS[checkpoint]) {
                    out.append(scenario.name())
                            .append(" @")
                            .append(step)
                            .append(' ')
                            .append(Long.toHexString(fingerprint(simulation)))
                            .append(" entities=")
                            .append(simulation.engine().getEntities().size())
                            .append('\n');
                    checkpoint++;
                }
            }
        }
        write("fingerprint-" + osName() + ".txt", out.toString());
    }

    @Test
    void measuresStepTime() throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# ")
                .append(System.getProperty("os.name"))
                .append(" | cores=")
                .append(Runtime.getRuntime().availableProcessors())
                .append('\n');
        for (Scenario scenario : Scenarios.all()) {
            if (!List.of("Defend", "Crowd", "Skirmish", "Adventure").contains(scenario.name())) {
                continue;
            }
            for (boolean idle : new boolean[] {true, false}) {
                Simulation simulation =
                        Scenarios.world(scenario, SimConfig.defaults().withSeed(SEED));
                godMode(simulation);
                for (int step = 0; step < 1_000; step++) {
                    simulation.step(idle ? CommanderInput.IDLE : scriptedInput(step));
                }
                int ticks = 12_000;
                long[] nanos = new long[ticks];
                int maxEntities = 0;
                int entitiesAtWorst = 0;
                long worst = 0;
                for (int step = 0; step < ticks; step++) {
                    CommanderInput input = idle ? CommanderInput.IDLE : scriptedInput(step);
                    long start = System.nanoTime();
                    simulation.step(input);
                    long took = System.nanoTime() - start;
                    nanos[step] = took;
                    int entities = simulation.engine().getEntities().size();
                    maxEntities = Math.max(maxEntities, entities);
                    if (took > worst) {
                        worst = took;
                        entitiesAtWorst = entities;
                    }
                }
                long[] sorted = nanos.clone();
                Arrays.sort(sorted);
                double mean = Arrays.stream(nanos).average().orElse(0) / 1e6;
                out.append(String.format(
                        "%-10s %-8s mean=%.3fms p50=%.3fms p99=%.3fms max=%.3fms maxEntities=%d entitiesAtMax=%d%n",
                        scenario.name(),
                        idle ? "idle" : "scripted",
                        mean,
                        sorted[ticks / 2] / 1e6,
                        sorted[ticks * 99 / 100] / 1e6,
                        worst / 1e6,
                        maxEntities,
                        entitiesAtWorst));
            }
        }
        write("step-time-" + osName() + ".txt", out.toString());
    }

    private static String osName() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "windows" : "linux";
    }

    private static CommanderInput scriptedInput(int step) {
        double angle = step * 0.05;
        return new CommanderInput(
                        (float) StrictMath.cos(angle),
                        (float) StrictMath.sin(angle),
                        80f,
                        45f,
                        step % 7 != 0,
                        step % 11 == 0)
                .acting(step % 13 == 0)
                .dragging(step % 17 == 0);
    }

    /** Every public and private field of every component of every entity, plus every system's streams. */
    private static long fingerprint(Simulation simulation) {
        Hash hash = new Hash();
        ImmutableArray<Entity> entities = simulation.engine().getEntities();
        hash.add(simulation.tick());
        hash.add(simulation.rng().state());
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            List<Component> components = new ArrayList<>();
            for (Component component : entity.getComponents()) {
                components.add(component);
            }
            components.sort(Comparator.comparing(c -> c.getClass().getName()));
            for (Component component : components) {
                hash.add(component.getClass().getName().hashCode());
                visit(hash, component, entities, 0);
            }
        }
        for (EntitySystem system : simulation.engine().getSystems()) {
            for (Field field : sortedFields(system.getClass())) {
                if (field.getType() == Rng.class) {
                    hash.add(((Rng) read(field, system)).state());
                }
            }
        }
        return hash.value;
    }

    private static void visit(Hash hash, @Nullable Object value, ImmutableArray<Entity> entities, int depth) {
        if (value == null) {
            hash.add(0x5EEDL);
        } else if (value instanceof Float f) {
            hash.add(Float.floatToRawIntBits(f));
        } else if (value instanceof Double d) {
            hash.add(Double.doubleToRawLongBits(d));
        } else if (value instanceof Number n) {
            hash.add(n.longValue());
        } else if (value instanceof Boolean b) {
            hash.add(b ? 1 : 2);
        } else if (value instanceof Character c) {
            hash.add(c);
        } else if (value instanceof Enum<?> e) {
            hash.add(ordinalOf(e));
        } else if (value instanceof String s) {
            hash.add(s.hashCode());
        } else if (value instanceof Entity e) {
            hash.add(entities.indexOf(e, true));
        } else if (value instanceof float[] a) {
            for (float f : a) {
                hash.add(Float.floatToRawIntBits(f));
            }
        } else if (value instanceof int[] a) {
            for (int v : a) {
                hash.add(v);
            }
        } else if (value instanceof long[] a) {
            for (long v : a) {
                hash.add(v);
            }
        } else if (value instanceof boolean[] a) {
            for (boolean v : a) {
                hash.add(v ? 1 : 2);
            }
        } else if (value instanceof Object[] a) {
            for (Object v : a) {
                visit(hash, v, entities, depth + 1);
            }
        } else if (depth > 4) {
            hash.add(value.getClass().getName().hashCode());
        } else if (value.getClass().isRecord()) {
            for (RecordComponent part : value.getClass().getRecordComponents()) {
                try {
                    visit(hash, part.getAccessor().invoke(value), entities, depth + 1);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        } else {
            for (Field field : sortedFields(value.getClass())) {
                visit(hash, read(field, value), entities, depth + 1);
            }
        }
    }

    @SuppressWarnings("EnumOrdinal") // a fingerprint, not a mapping
    private static int ordinalOf(Enum<?> e) {
        return e.ordinal();
    }

    private static List<Field> sortedFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
        }
        fields.sort(Comparator.comparing(f -> f.getDeclaringClass().getName() + "." + f.getName()));
        return fields;
    }

    private static @Nullable Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return field.getName();
        }
    }

    private static void write(String name, String content) throws IOException {
        Path dir = root().resolve("ai/logs/net");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static Path root() {
        Path here = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("settings.gradle"))) {
                return p;
            }
        }
        return here;
    }

    private static final class Hash {
        long value = 0xcbf29ce484222325L;

        void add(long v) {
            for (int i = 0; i < 8; i++) {
                value ^= (v >>> (i * 8)) & 0xff;
                value *= 0x100000001b3L;
            }
        }
    }
}
