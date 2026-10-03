/**
 * The libGDX application (ADR 0021): {@code PwtGame}, what a screen may ask of it, the three screens and
 * what only they use - a level's pages ({@code PlayMenus}) and the harness keys ({@code HarnessControls}).
 * Depends on the sim, never the reverse.
 *
 * <p>Everything else lives in a layer: {@code input}, {@code draw}, {@code hud}, {@code play}, {@code
 * config}. A layer uses another only through its ports, and the root only through the root's: {@link
 * pl.mzebrows.pwt.client.Log} (every layer logs), {@link pl.mzebrows.pwt.client.Assets} (every asset path)
 * and {@link pl.mzebrows.pwt.client.LaunchOptions} (which copy of the game this is). The root builds and
 * drives each layer's entries.
 */
@NullMarked
package pl.mzebrows.pwt.client;

import org.jspecify.annotations.NullMarked;
