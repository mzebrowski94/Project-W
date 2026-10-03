/**
 * The squad (ADR 0020): what a trooper decides, the orders the commander gives it, and how it fights.
 *
 * <p>Systems: {@link pl.mzebrows.pwt.sim.system.squad.SquadOrderSystem} hands out the commander's orders,
 * {@link pl.mzebrows.pwt.sim.system.squad.TrooperMindSystem} decides what each trooper is doing - through
 * its package-private helpers {@code TrooperAid}, {@code TrooperCrew}, {@code TrooperFight}, {@code
 * TrooperMend}, {@code TrooperObjective}, {@code TrooperOrders} and {@code TrooperRoam} - and {@link
 * pl.mzebrows.pwt.sim.system.squad.TrooperCombatSystem} fires, throws and swings. Ports: none.
 *
 * <p>Not here: how a trooper walks. {@code TrooperMoveSystem} is the squad's steering behaviour and lives
 * with the steering pipeline, as the aliens' hop and weave do. Crewing a gun is {@code Crews}, the
 * commander's and a trooper's alike (G0024).
 *
 * <p>Answers to G0010 (a mind is a field, not a tree), G0017, G0023, G0029 and G0041.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system.squad;

import org.jspecify.annotations.NullMarked;
