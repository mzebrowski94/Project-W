/**
 * Fighting (ADR 0020): what touches what, what a blow does when it lands, and the guns and throws that
 * deliver one.
 *
 * <p>Systems: {@code CollisionSystem} resolves contact - a claw, a shot, a pickup reached - into a blow on
 * {@code Damage} or a claim; {@code DamageSystem} applies a tick's blows; {@code ShockSystem} builds a
 * shock stun; {@code GrenadeSystem} and {@code ProjectileBoundsSystem} fly what was thrown and fired;
 * {@code PickupSystem} applies what was claimed; {@code CooldownSystem} counts every timer down.
 *
 * <p>Ports: {@link pl.mzebrows.pwt.sim.system.combat.Guns} (the trigger pull, for everything that fires),
 * {@link pl.mzebrows.pwt.sim.system.combat.Throws} (a throw) and {@link
 * pl.mzebrows.pwt.sim.system.combat.Grants} (what a pickup's or a crate's grant does to the man who takes
 * it). {@code Muzzle} and {@code Bounce} stay inside.
 *
 * <p>Not here: what a death leaves behind ({@code death}), or who decides to fire ({@code commander},
 * {@code squad}, {@code creature}).
 *
 * <p>Answers to G0006, G0018, G0019, G0020, G0025, G0032, G0044, G0053, G0054, G0057, G0058 and G0059.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system.combat;

import org.jspecify.annotations.NullMarked;
