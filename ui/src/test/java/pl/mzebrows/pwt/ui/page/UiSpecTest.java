package pl.mzebrows.pwt.ui.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The specification and the code cannot drift: every page and slot row in
 * {@code docs/ui/menu_layout_agent.md} (the tables of sections 3 and 7) is a constant of {@link UiPage}
 * or {@link HudSlot}, and every constant has its row.
 */
class UiSpecTest {

    private static final Pattern ROW_ID = Pattern.compile("^\\| `(UI:[a-z0-9/-]+)` \\|");

    @Test
    void namesEveryPageOfTheSpecificationAndNothingElse() {
        Set<String> code = new TreeSet<>();
        for (UiPage page : UiPage.values()) {
            code.add(page.id());
        }

        assertThat(code).isEqualTo(rows("## 3.", "UI:hud"));
    }

    @Test
    void namesEveryHudSlotOfTheSpecificationAndNothingElse() {
        Set<String> code = new TreeSet<>();
        for (HudSlot slot : HudSlot.values()) {
            code.add(slot.id());
        }

        assertThat(code).isEqualTo(rows("## 7.", ""));
    }

    /** The ids in the first column of the section's table; with a prefix to skip, those rows left out. */
    private static Set<String> rows(String section, String skip) {
        List<String> lines = spec();
        Set<String> ids = new TreeSet<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.startsWith("## ")) {
                inside = line.startsWith(section);
                continue;
            }
            Matcher row = ROW_ID.matcher(line);
            if (inside && row.find() && (skip.isEmpty() || !row.group(1).startsWith(skip))) {
                ids.add(row.group(1));
            }
        }
        return ids;
    }

    private static List<String> spec() {
        try {
            return Files.readAllLines(Path.of("../docs/ui/menu_layout_agent.md"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("docs/ui/menu_layout_agent.md must be readable from the ui module", e);
        }
    }
}
