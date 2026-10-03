/**
 * The steering pipeline (ADR 0020): what each unit asks to do with its feet, and how that becomes where it
 * is.
 *
 * <p>Behaviours write {@code Steering} - {@code AvoidanceSystem}, {@code DodgeSystem}, {@code HopSystem},
 * {@code WanderSystem}, {@code WeaveSystem}, the aliens' route in {@code NavigationSystem} and the squad's
 * in {@code TrooperMoveSystem}; {@code DriveSystem} turns a unit's steering into its velocity, {@code
 * StunSystem} lets a shove run out, {@code MovementSystem} moves it, {@code FacingSystem} turns it where it
 * ended the tick looking, and {@code PositionHistorySystem} keeps where it was. Ports: none.
 *
 * <p>Not here: what a unit decides to steer at. That is its mind - {@code TrooperMindSystem} in {@code
 * squad}, {@code PerceptionSystem} in {@code creature} - and this package only carries it out.
 *
 * <p>Answers to G0002, G0010, G0012, G0026, G0033, G0037, G0041 and G0042.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system.motion;

import org.jspecify.annotations.NullMarked;
