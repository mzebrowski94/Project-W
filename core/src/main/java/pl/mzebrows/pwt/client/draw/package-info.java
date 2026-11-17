/**
 * The field as it is drawn (ADR 0021): the ground, every entity as a placeholder shape and the marks on it,
 * the colours that say what a thing is and what it is doing, the camera and the rendering tunables.
 *
 * <p>Ports: {@link pl.mzebrows.pwt.client.draw.GraphicConfig} (every rendering tunable, the painters' style
 * among them), {@link pl.mzebrows.pwt.client.draw.Palette} (a thing's colour, a player's among them), {@link
 * pl.mzebrows.pwt.client.draw.Drawn} (where a thing is drawn this frame) and {@link
 * pl.mzebrows.pwt.client.draw.ActionMark} (how the action's brackets stand round a thing) - the last two so a
 * prompt sits over what is drawn (G0098). Entries, for the root: {@code WorldRenderer}, {@code TerrainRenderer},
 * {@code ActionBrackets}, {@code CameraDirector}, {@code Drawn}, {@code LocalAim}, {@code LetterboxViewport}. The marks, {@code StateTint}, {@code TerrainPalette}
 * and {@code GroundDetail} stay inside: only {@code GraphicConfig} builds them and only the renderers read
 * them.
 *
 * <p>Not here: what is drawn in screen space ({@code hud}), or any device - draw reads no input. {@code
 * CameraDirector} decides and touches no {@code Gdx}, which its name fences.
 *
 * <p>Answers to G0003, ADR 0007 and ADR 0012 (the cosmetic lead).
 */
@NullMarked
package pl.mzebrows.pwt.client.draw;

import org.jspecify.annotations.NullMarked;
