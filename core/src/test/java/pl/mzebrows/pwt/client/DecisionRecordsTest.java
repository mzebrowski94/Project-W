package pl.mzebrows.pwt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A record that no longer holds must say so on the first lines an agent reads, in the same words its
 * index uses, and no source comment may send anybody to one that is superseded ({@code docs/adr/README.md},
 * <em>Status</em>). Without this an amended record reads as current to whoever opens it from a citation.
 */
class DecisionRecordsTest {

    private static final Path ROOT = Path.of("..");

    private static final List<Path> FOLDERS = List.of(Path.of("docs/adr"), Path.of("docs/design/decisions"));

    private static final Pattern RECORD_FILE = Pattern.compile("(\\d{4}|G\\d{4})-[a-z0-9-]+\\.md");

    private static final Pattern ROW = Pattern.compile(
            "^\\| \\[(\\w+)]\\(([^)]+)\\) \\| [^|]* \\| (?<doNotBuild>[^|]*) \\| (?<status>[^|]+) \\|$");

    private static final String ID = "(?:ADR \\d{4}|G\\d{4})";

    private static final Pattern STATUS = Pattern.compile(
            "accepted|superseded by " + ID + ": [^;]+|amended by " + ID + ": [^;]+(?:; amended by " + ID + ": [^;]+)*");

    private static final Pattern NAMED = Pattern.compile("(ADR \\d{4}|G\\d{4})");

    /** {@code **Amends G0006, G0018 and G0023**} - a verb and the records it changes, inside one bold run. */
    private static final Pattern DECLARED = Pattern.compile("\\*\\*(Amends|Supersedes) ([^*]+)\\*\\*");

    private static final String STATUS_LINE = "**Status.** ";

    private static final String TRIGGER_LINE = "**Trigger.** ";

    private static final Path INVARIANTS = Path.of("docs/design/invariants");

    private static final List<String> MODULES = List.of("map", "sim", "core", "ui", "net", "lwjgl3");

    /**
     * {@code `Owner.member`}, a test class on its own ({@code `SomeTest`}), or {@code `.member`} for another
     * member of the last class named. A member is camel case, so {@code `.tmx`} is a file type, not one.
     */
    private static final Pattern CITED =
            Pattern.compile("`(?:([A-Z]\\w*)\\.([a-z]\\w*)|([A-Z]\\w*Test)|\\.([a-z]\\w*[A-Z]\\w*))`");

    /** Where source comments live: every module's main code, and the shipped files' comments. */
    private static final List<String> SOURCES = List.of(
            "map/src/main",
            "sim/src/main",
            "core/src/main",
            "ui/src/main",
            "net/src/main",
            "lwjgl3/src/main",
            "assets");

    @Test
    void indexesEveryRecordAndNothingThatIsNotOne() {
        List<String> wrong = new ArrayList<>();
        for (Path folder : FOLDERS) {
            Set<String> files = recordFiles(folder);
            Set<String> indexed = new TreeSet<>(index(folder).keySet());
            for (String file : files) {
                if (!indexed.contains(file)) {
                    wrong.add(folder + "/" + file + " has no row in its README");
                }
            }
            for (String file : indexed) {
                if (!files.contains(file)) {
                    wrong.add(folder + "/README.md indexes " + file + ", which does not exist");
                }
            }
        }

        assertThat(wrong).isEmpty();
    }

    @Test
    void opensEveryRecordWithTheStatusItsIndexGives() {
        List<String> wrong = new ArrayList<>();
        for (Path folder : FOLDERS) {
            for (Map.Entry<String, String> row : index(folder).entrySet()) {
                List<String> lines = lines(ROOT.resolve(folder).resolve(row.getKey()));
                String expected = STATUS_LINE + row.getValue();
                if (lines.size() < 5 || !lines.get(2).equals(expected)) {
                    wrong.add(row.getKey() + ": line 3 should read " + expected);
                } else if (!lines.get(4).startsWith(TRIGGER_LINE)) {
                    wrong.add(row.getKey() + ": line 5 should be its trigger");
                }
            }
        }

        assertThat(wrong).isEmpty();
    }

    @Test
    void writesEveryStatusInOneOfItsThreeForms() {
        List<String> wrong = new ArrayList<>();
        Set<String> known = knownIds();
        for (Path folder : FOLDERS) {
            for (Map.Entry<String, String> row : index(folder).entrySet()) {
                String status = row.getValue();
                if (!STATUS.matcher(status).matches()) {
                    wrong.add(row.getKey() + ": " + status);
                }
                Matcher named = NAMED.matcher(status);
                while (named.find()) {
                    if (!known.contains(named.group(1))) {
                        wrong.add(row.getKey() + " names " + named.group(1) + ", which does not exist");
                    }
                }
            }
        }

        assertThat(wrong)
                .as("accepted | amended by <id>: <what> | superseded by <id>: <why>")
                .isEmpty();
    }

    /**
     * A record that changes another says so as {@code **Amends G0020**} or {@code **Supersedes G0050**}, and
     * the other record's status names it back - so neither side of an amendment can be written alone.
     */
    @Test
    void recordsEveryAmendmentOnBothRecords() {
        Map<String, String> statuses = new TreeMap<>();
        Map<String, Path> paths = new TreeMap<>();
        for (Path folder : FOLDERS) {
            for (Map.Entry<String, String> row : index(folder).entrySet()) {
                statuses.put(id(row.getKey()), row.getValue());
                paths.put(id(row.getKey()), ROOT.resolve(folder).resolve(row.getKey()));
            }
        }
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Path> record : paths.entrySet()) {
            String self = record.getKey();
            Matcher declared = DECLARED.matcher(String.join("\n", lines(record.getValue())));
            while (declared.find()) {
                String verb = declared.group(1).equals("Amends") ? "amended by " : "superseded by ";
                Matcher named = NAMED.matcher(declared.group(2));
                while (named.find()) {
                    String other = named.group(1);
                    if (!statuses.getOrDefault(other, "").contains(verb + self + ":")) {
                        wrong.add(self + " declares " + declared.group(1) + " " + other + ", whose status does not say "
                                + verb + self);
                    }
                }
            }
            Matcher cited = NAMED.matcher(statuses.get(self));
            while (cited.find()) {
                String other = cited.group(1);
                Path by = paths.get(other);
                boolean declares = by != null
                        && Pattern.compile("\\*\\*(?:Amends|Supersedes) [^*]*\\b" + Pattern.quote(self) + "\\b")
                                .matcher(String.join("\n", lines(by)))
                                .find();
                if (!declares) {
                    wrong.add(self + "'s status names " + other + ", which does not declare **Amends " + self + "**");
                }
            }
        }

        assertThat(wrong).isEmpty();
    }

    @Test
    void titlesASupersededRecordAsOne() {
        List<String> wrong = new ArrayList<>();
        for (Path folder : FOLDERS) {
            for (Map.Entry<String, String> row : index(folder).entrySet()) {
                String title = lines(ROOT.resolve(folder).resolve(row.getKey())).get(0);
                boolean superseded = row.getValue().startsWith("superseded by ");
                if (superseded != title.contains("(superseded by ")) {
                    wrong.add(row.getKey() + ": " + title);
                }
            }
        }

        assertThat(wrong).isEmpty();
    }

    /** Nothing in a superseded record holds, its rejections included, so none is in the negative index. */
    @Test
    void keepsASupersededRecordOutOfTheNegativeIndex() {
        List<String> wrong = new ArrayList<>();
        for (Path folder : FOLDERS) {
            for (String line : lines(ROOT.resolve(folder).resolve("README.md"))) {
                Matcher row = ROW.matcher(line);
                if (row.matches() && row.group("status").strip().startsWith("superseded by ")) {
                    if (!row.group("doNotBuild").strip().equals("-")) {
                        wrong.add(row.group(1) + ": " + line);
                    }
                }
            }
        }

        assertThat(wrong).as("a superseded record's Do not build cell is -").isEmpty();
    }

    @Test
    void citesNoSupersededRecordInASource() {
        Set<String> superseded = supersededIds();
        List<String> found = new ArrayList<>();
        for (Path file : files()) {
            List<String> lines = lines(file);
            for (int i = 0; i < lines.size(); i++) {
                Matcher named = NAMED.matcher(lines.get(i));
                while (named.find()) {
                    if (superseded.contains(named.group(1))) {
                        found.add(ROOT.relativize(file) + ":" + (i + 1) + " " + named.group(1));
                    }
                }
            }
        }

        assertThat(found).as("cite the record that superseded it").isEmpty();
    }

    /**
     * "Enforced by" is a claim, so it names tests that exist: {@code `CasualtyTest.leaves...`}, and after
     * it {@code `.kills...`} for another method of the same class. The invariants make the same claim.
     */
    @Test
    void namesOnlyTestsThatExistAsWhatEnforcesIt() {
        Map<String, String> tests = testSources();
        Set<String> superseded = supersededFiles();
        List<Path> claims = new ArrayList<>();
        for (Path folder : FOLDERS) {
            for (String file : recordFiles(folder)) {
                if (!superseded.contains(file)) {
                    claims.add(ROOT.resolve(folder).resolve(file));
                }
            }
        }
        claims.addAll(markdownIn(INVARIANTS));
        List<String> missing = new ArrayList<>();
        for (Path file : claims) {
            String owner = null;
            Matcher cited = CITED.matcher(String.join("\n", lines(file)));
            while (cited.find()) {
                if (cited.group(3) != null) {
                    owner = cited.group(3);
                    continue;
                }
                if (cited.group(1) != null) {
                    owner = cited.group(1);
                }
                String method = cited.group(1) != null ? cited.group(2) : cited.group(4);
                String source = owner == null ? null : tests.get(owner);
                if (source != null
                        && !Pattern.compile("\\b" + method + "\\b")
                                .matcher(source)
                                .find()) {
                    missing.add(file.getFileName() + ": " + owner + "." + method);
                }
            }
        }

        assertThat(missing).as("name the test that enforces it now").isEmpty();
    }

    /** Every record in a folder, by file name. */
    private static Set<String> recordFiles(Path folder) {
        try (Stream<Path> list = Files.list(ROOT.resolve(folder))) {
            Set<String> files = new TreeSet<>();
            list.map(path -> path.getFileName().toString())
                    .filter(name -> RECORD_FILE.matcher(name).matches())
                    .forEach(files::add);
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A folder's index: each record's file name, and the status its row gives. */
    private static Map<String, String> index(Path folder) {
        Map<String, String> rows = new TreeMap<>();
        for (String line : lines(ROOT.resolve(folder).resolve("README.md"))) {
            Matcher row = ROW.matcher(line);
            if (row.matches()) {
                rows.put(row.group(2), row.group("status").strip());
            }
        }
        return rows;
    }

    /** Every record's id as a status names it: {@code ADR 0012} or {@code G0053}. */
    private static Set<String> knownIds() {
        Set<String> ids = new TreeSet<>();
        for (Path folder : FOLDERS) {
            for (String file : recordFiles(folder)) {
                ids.add(id(file));
            }
        }
        return ids;
    }

    private static Set<String> supersededIds() {
        Set<String> ids = new TreeSet<>();
        for (Path folder : FOLDERS) {
            for (Map.Entry<String, String> row : index(folder).entrySet()) {
                if (row.getValue().startsWith("superseded by ")) {
                    ids.add(id(row.getKey()));
                }
            }
        }
        return ids;
    }

    private static Set<String> supersededFiles() {
        Set<String> files = new TreeSet<>();
        for (Path folder : FOLDERS) {
            for (Map.Entry<String, String> row : index(folder).entrySet()) {
                if (row.getValue().startsWith("superseded by ")) {
                    files.add(row.getKey());
                }
            }
        }
        return files;
    }

    /** Every test class in the repository by simple name, with its source. */
    private static Map<String, String> testSources() {
        Map<String, String> tests = new TreeMap<>();
        for (String module : MODULES) {
            Path root = ROOT.resolve(module).resolve("src/test/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> path.getFileName().toString().endsWith("Test.java"))
                        .sorted()
                        .forEach(path -> tests.put(
                                path.getFileName().toString().replace(".java", ""), String.join("\n", lines(path))));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return tests;
    }

    private static List<Path> markdownIn(Path folder) {
        try (Stream<Path> list = Files.list(ROOT.resolve(folder))) {
            return list.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String id(String file) {
        String number = file.substring(0, file.indexOf('-'));
        return number.startsWith("G") ? number : "ADR " + number;
    }

    private static List<Path> files() {
        List<Path> files = new ArrayList<>();
        for (String root : SOURCES) {
            try (Stream<Path> walk = Files.walk(ROOT.resolve(root))) {
                walk.filter(DecisionRecordsTest::isSource).sorted().forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    private static boolean isSource(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file) && (name.endsWith(".java") || name.endsWith(".properties"));
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
