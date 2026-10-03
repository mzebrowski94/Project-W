/**
 * Questions about components that every fenced package may ask (ADR 0020): public, stateless, and writing
 * nothing - {@code ArchitectureTest.queryWritesNothing} measures it with ADR 0019's analysis.
 *
 * <p>Who drives itself and who may be hunted ({@link pl.mzebrows.pwt.sim.system.query.Drivers}), range,
 * arc and line of sight ({@link pl.mzebrows.pwt.sim.system.query.Sight}), a slot's bearing ({@link
 * pl.mzebrows.pwt.sim.system.query.Compass}), what is within arm's length ({@link
 * pl.mzebrows.pwt.sim.system.query.Reach}), how fast the ground lets a unit go ({@link
 * pl.mzebrows.pwt.sim.system.query.Pace}), a gun coming round onto a bearing ({@link
 * pl.mzebrows.pwt.sim.system.query.Aim}) and where a man is put down ({@link
 * pl.mzebrows.pwt.sim.system.query.RallyPoint}). A unit's life stage is the same kind of question and
 * lives in the {@code sim} root as {@code Life}, because {@code Players} and {@code ui} ask it.
 *
 * <p>Not here: anything that writes a component, or holds state - that is a port of the package it
 * belongs to ({@code Deaths.kill}), and {@code Odds}, which advances a stream, lives beside {@code Rng}.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system.query;

import org.jspecify.annotations.NullMarked;
