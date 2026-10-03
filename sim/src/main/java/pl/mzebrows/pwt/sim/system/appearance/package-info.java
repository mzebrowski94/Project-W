/**
 * What the renderer is shown (ADR 0020, G0003): {@code AppearanceSystem} derives each entity's state, tell
 * and charge in one place and one order, and writes nothing but {@code Appearance}. Ports: none.
 *
 * <p>The one fenced package with a single system and no helper - a named exception to ADR 0020's founding
 * rule, because it reads every domain and writes for none of them. If it leaves the simulation (the
 * backlog's "presentation still in the deterministic state"), it leaves as this package.
 *
 * <p>Not here: which way a unit faces ({@code FacingSystem}, in {@code motion}), which rules read as well as
 * draw.
 *
 * <p>Answers to G0003 and pillar 5.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system.appearance;

import org.jspecify.annotations.NullMarked;
