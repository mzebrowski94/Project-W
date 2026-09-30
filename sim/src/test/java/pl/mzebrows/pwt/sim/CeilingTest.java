package pl.mzebrows.pwt.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Anything per player is sized from the capacity a world was built with, never from the ceiling (pillar 7,
 * G0038). {@link Players#MAX} is a compile-time constant, so javac copies its value into every reader and
 * no bytecode rule can see who reads it; the sources can.
 */
class CeilingTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /** Where a capacity is chosen: the ceiling itself, and the default of each thing built to one. */
    private static final Set<String> CHOOSERS = Set.of("Players.java", "PlayerInputs.java", "Simulation.java");

    private static final String CEILING = "Players.MAX";

    @Test
    void sizesNothingByTheCeilingOutsideWhereACapacityIsChosen() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                if (CHOOSERS.contains(file.getFileName().toString())) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (code(lines.get(i)).contains(CEILING)) {
                        found.add(SOURCES.relativize(file) + ":" + (i + 1));
                    }
                }
            }
        }

        assertThat(found)
                .as("size it from the capacity the world was built with")
                .isEmpty();
    }

    /** A line without its comment: a comment may name the ceiling, code may not read it. */
    private static String code(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
            return "";
        }
        int comment = line.indexOf("//");
        return comment < 0 ? line : line.substring(0, comment);
    }
}
