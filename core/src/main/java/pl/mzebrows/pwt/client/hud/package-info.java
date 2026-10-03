/**
 * Everything drawn in screen space (ADR 0021): the UI canvas and the reticle, the top and bottom bars, the
 * pages and the toast, the fonts, and every word the player reads - the cards, the terminal's lines, the
 * hints, the dev overlay.
 *
 * <p>No ports: nothing outside the root reads the hud. Entries, for the root: {@code Hud}, the five
 * painters, {@code UiStrings}, {@code CrateHints}, {@code DraftHints}, {@code PageHints}, {@code
 * ActionPrompts}, {@code DispatchText}, {@code DebugOverlay} and {@code SystemPointer} (the launcher's to
 * implement).
 *
 * <p>Not here: what the bars show, decided as data ({@code ui}, ADR 0015), the field ({@code draw}), or any
 * device - a hint names a binding from {@code input}'s ports and reads nothing.
 *
 * <p>Answers to ADR 0015, G0049 and G0056.
 */
@NullMarked
package pl.mzebrows.pwt.client.hud;

import org.jspecify.annotations.NullMarked;
