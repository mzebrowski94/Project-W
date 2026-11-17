/**
 * The devices and what they mean (ADR 0021): the keyboard, the mouse and every gamepad read once a frame,
 * the bindings, who sits where on this machine, and the controls that turn presses into a tick's input or
 * a page's selection.
 *
 * <p>Ports: {@link pl.mzebrows.pwt.client.input.ControlsConfig} (the bindings, which the hints name),
 * {@link pl.mzebrows.pwt.client.input.PlayerSeats} (who is sitting where, which the hints and the play key
 * by), {@link pl.mzebrows.pwt.client.input.PadLabels} (what a pad's button is called), {@link
 * pl.mzebrows.pwt.client.input.KeyCode} (a key, as the file and the hints name it) and {@link
 * pl.mzebrows.pwt.client.input.MouseButton} (a mouse button, as the file and the prompts name it). Entries, for
 * the root:
 * {@code InputReader}, {@code NavInputReader}, {@code GdxGamepads}, {@code ChosenGamepads}, {@code
 * GamepadSource}, {@code PadPolicy}, {@code KeyHolds}, {@code KeyPresses}, {@code PageControls}.
 *
 * <p>Not here: the harness keys ({@code HarnessControls}, the scenario screen's, in the root), what a hint
 * says ({@code hud}), or anything drawn - input reads devices and hands values on. The deciding classes
 * ({@code *Controls}, {@code MenuSelection}) touch no {@code Gdx}, which their names fence.
 *
 * <p>Answers to ADR 0011, G0038 and G0049.
 */
@NullMarked
package pl.mzebrows.pwt.client.input;

import org.jspecify.annotations.NullMarked;
