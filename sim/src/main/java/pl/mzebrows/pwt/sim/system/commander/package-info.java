/**
 * The commander (ADR 0020): his controls, his hands, and what he does with the action key.
 *
 * <p>Systems: {@code CommanderControlSystem} turns a slot's input into his move, aim and trigger; {@code
 * MeleeSystem} and {@code StrikeSystem} are the hand strike; {@code DragSystem} hauls a load; {@code
 * ActionSystem} decides what the key means and does it; {@code CrateSystem} deals and takes a crate's cards;
 * {@code TurretSystem} works a crewed gun; {@code AidSystem} is a trooper doing the same work on a casualty
 * the commander does, which is why it lives beside the one copy of that work, {@code Aid}.
 *
 * <p>Ports: {@link pl.mzebrows.pwt.sim.system.commander.Crews} - getting on a gun and off it, the
 * commander's and a trooper's alike (G0024), so {@code squad} uses it too.
 *
 * <p>Not here: what a trooper decides to do ({@code squad}), firing a gun ({@code Guns}, in {@code
 * combat}), or what an open crate's card does to the man who took it ({@code Grants}).
 *
 * <p>Answers to G0005, G0013, G0014, G0021, G0023, G0024, G0028 and G0056.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system.commander;

import org.jspecify.annotations.NullMarked;
