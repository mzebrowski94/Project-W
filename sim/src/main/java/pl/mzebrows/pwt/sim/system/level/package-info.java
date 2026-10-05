/**
 * The level (ADR 0020): what it sends, what it holds back, where it is taken, and when it is won.
 *
 * <p>Systems: {@code WaveSystem} lets the wave schedule go; {@code NestSystem} has a nest produce; {@code
 * LairSystem} wakes a lair; {@code ControlPointSystem} takes a point by dwelling on it; {@code
 * ReinforcementSystem} puts men on the field from the reserve; {@code DoorSystem} counts the work on a door, opens
 * it and sinks it (G0069); {@code ObjectiveSystem} says when the level is won. {@code Rouse} - sending everything in earshot to look - is shared by a point and a nest. Ports:
 * none.
 *
 * <p>Not here: the level entity's components ({@code component}), how a level is laid out ({@code
 * scenario}, {@code map}), or a commander's respawn ({@code death}).
 *
 * <p>Answers to G0007, G0027, G0029, G0030, G0031, G0048, G0055 and G0069.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system.level;

import org.jspecify.annotations.NullMarked;
