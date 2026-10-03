/**
 * Dying and what it leaves (ADR 0020): a casualty's clock, the throes, the reaper, a respawn, and the
 * reactions a death sets off.
 *
 * <p>Systems: {@code CasualtySystem} runs a man's bleed-out and mends his wounds; {@code DyingSystem} ends
 * the throes and {@code ReaperSystem} - the only thing that removes an entity - takes what is dead; {@code
 * RespawnSystem} brings a commander back. The death reactions: {@code BloatSystem} bursts a bloated corpse,
 * {@code BlastSystem} and {@code MineSystem} the explosions, {@code SlickSystem} the ground they leave.
 *
 * <p>Ports: {@link pl.mzebrows.pwt.sim.system.death.Deaths} - the one death path, so {@code DamageSystem}
 * in {@code combat} kills the same way a bleed-out does.
 *
 * <p>Not here: the blow that kills ({@code combat}), or a unit's life stage, which is {@code Life} in the
 * {@code sim} root.
 *
 * <p>Answers to G0004, G0018, G0022, G0034, G0035, G0036, G0044 and G0053.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system.death;

import org.jspecify.annotations.NullMarked;
