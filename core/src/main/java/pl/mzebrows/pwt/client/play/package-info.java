/**
 * What a scenario is played through (ADR 0021): offline, a world stepped on this machine; online, this
 * machine's place in a session, its worlds, its invitation and the join list's choice - and the harness
 * state around either.
 *
 * <p>Ports: {@link pl.mzebrows.pwt.client.play.OverlayMode} (how much of the dev overlay is on, which lays
 * the top bar out). Entries, for the root: {@code ScenarioPlay}, {@code ScenarioSession}, {@code
 * OnlineSession}, {@code JoinChoices}, {@code HarnessCommand}.
 *
 * <p>Not here: the keys that ask for a harness command ({@code HarnessControls}, the scenario screen's), the
 * devices ({@code input}), anything drawn. No libGDX: everything here is decided from values, testable
 * without a window - which is what makes {@code ScenarioSessionTest} and {@code OnlineSessionTest}
 * possible.
 *
 * <p>Answers to ADR 0012, G0038, G0049 and G0056.
 */
@NullMarked
package pl.mzebrows.pwt.client.play;

import org.jspecify.annotations.NullMarked;
