package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A citation points at something that lasts ({@code docs/conventions.md}): a G record, an ADR, a doc that
 * exists. A scope doc's numbering - a decision {@code (D11)}, a session {@code upgrades S5}, a step
 * {@code H3}, a lettered choice {@code (B)} - dies with the doc when its work lands, and leaves every
 * comment that cites it pointing at nothing.
 */
class SourceCitationsTest {

    private static final Path ROOT = Path.of("..");

    /** Where source comments live: every module's main code, and the shipped files' comments. */
    private static final List<String> SOURCES = List.of(
            "map/src/main",
            "sim/src/main",
            "core/src/main",
            "ui/src/main",
            "net/src/main",
            "lwjgl3/src/main",
            "assets");

    /** Where a path to a doc may be written down, besides the sources. */
    private static final List<String> WRITING = List.of("CLAUDE.md", "README.md", "docs", ".claude/skills");

    /** A scope doc's number, not preceded or followed by anything that would make it a word or a version. */
    private static final Pattern PLAN_NUMBER =
            Pattern.compile("(?<![A-Za-z0-9_.-])[DSH]\\d{1,2}(?![0-9A-Za-z_])|\\(B\\)");

    /** The UI plan's own numbering, which a citation may use for as long as that plan exists. */
    private static final String UI_PLAN_CITATION = "plan ";

    private static final Path UI_PLAN = Path.of("docs/ui/ui_and_graphics_action_plan.md");

    private static final Pattern DOC = Pattern.compile("docs/[A-Za-z0-9_][A-Za-z0-9_/.-]*\\.md");

    @Test
    void citesNoScopeDocsNumbersInAComment() {
        List<String> found = new ArrayList<>();
        for (Path file : files(SOURCES)) {
            List<String> lines = lines(file);
            for (int i = 0; i < lines.size(); i++) {
                String comment = comment(file, lines.get(i));
                Matcher number = PLAN_NUMBER.matcher(comment);
                while (number.find()) {
                    if (!citesTheUiPlan(comment, number.start())) {
                        found.add(ROOT.relativize(file) + ":" + (i + 1) + " " + number.group());
                    }
                }
            }
        }

        assertThat(found)
                .as("cite the G record or ADR that holds the decision instead")
                .isEmpty();
    }

    @Test
    void namesOnlyDocsThatExist() {
        List<String> found = new ArrayList<>();
        List<Path> files = new ArrayList<>(files(SOURCES));
        files.addAll(files(WRITING));
        for (Path file : files) {
            List<String> lines = lines(file);
            for (int i = 0; i < lines.size(); i++) {
                Matcher doc = DOC.matcher(lines.get(i));
                while (doc.find()) {
                    if (!Files.exists(ROOT.resolve(doc.group()))) {
                        found.add(ROOT.relativize(file) + ":" + (i + 1) + " " + doc.group());
                    }
                }
            }
        }

        assertThat(found).isEmpty();
    }

    /** Whether this number is the UI plan's, cited as {@code plan D14} while that plan is still there. */
    private static boolean citesTheUiPlan(String comment, int at) {
        int from = at - UI_PLAN_CITATION.length();
        return from >= 0 && comment.startsWith(UI_PLAN_CITATION, from) && Files.exists(ROOT.resolve(UI_PLAN));
    }

    /** The comment on a line, or nothing: a Java comment line or trailing comment, a properties comment. */
    private static String comment(Path file, String line) {
        String trimmed = line.strip();
        if (file.toString().endsWith(".properties")) {
            return trimmed.startsWith("#") || trimmed.startsWith("!") ? trimmed : "";
        }
        if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
            return trimmed;
        }
        int trailing = line.indexOf("//");
        return trailing < 0 ? "" : line.substring(trailing);
    }

    private static List<Path> files(List<String> roots) {
        List<Path> files = new ArrayList<>();
        for (String root : roots) {
            Path path = ROOT.resolve(root);
            if (Files.isRegularFile(path)) {
                files.add(path);
                continue;
            }
            try (Stream<Path> walk = Files.walk(path)) {
                walk.filter(SourceCitationsTest::isText).sorted().forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    private static boolean isText(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file)
                && (name.endsWith(".java") || name.endsWith(".properties") || name.endsWith(".md"));
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
