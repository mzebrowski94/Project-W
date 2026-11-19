/**
 * The libGDX application (ADR 0021): {@code PwtGame}, what a screen may ask of it, the three screens and
 * what only they use - a level's pages ({@code PlayMenus}), the controls page's words ({@code ControlsPage}), the harness keys
 * ({@code HarnessControls}) and the proving
 * ground's window ({@code ProvingPreview}).
 * Depends on the sim, never the reverse.
 *
 * <p>Everything else lives in a layer: {@code input}, {@code draw}, {@code hud}, {@code play}, {@code
 * config}. A layer uses another only through its ports, and the root only through the root's: {@link
 * pl.mzebrows.pwt.client.Log} (every layer logs), {@link pl.mzebrows.pwt.client.Assets} (every asset path),
 * {@link pl.mzebrows.pwt.client.LaunchOptions} (which copy of the game this is) and {@link
 * pl.mzebrows.pwt.client.LocalSettings} (what the player has set on this computer, which the prompts read).
 * The root builds and drives each layer's entries.
 */
@NullMarked
package pl.mzebrows.pwt.client;

import org.jspecify.annotations.NullMarked;
