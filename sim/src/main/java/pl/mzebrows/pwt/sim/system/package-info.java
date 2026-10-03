/**
 * ECS systems not yet in a fenced package (ADR 0020): ticket 05 moves the rest into theirs, and this package
 * then holds none. {@code query}, {@code squad}, {@code motion}, {@code commander}, {@code creature} and {@code combat} are fenced already.
 *
 * <p>Ports - what a fenced package may use from here, besides {@code query}: {@link
 * pl.mzebrows.pwt.sim.system.Deaths} (the one death path). {@code ArchitectureTest.PORTS} lists the same.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system;

import org.jspecify.annotations.NullMarked;
