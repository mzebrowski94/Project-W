/**
 * ECS systems not yet in a fenced package (ADR 0020): ticket 05 moves the rest into theirs, and this package
 * then holds none. {@code query} and {@code squad} are fenced already.
 *
 * <p>Ports - what a fenced package may use from here, besides {@code query}: {@link
 * pl.mzebrows.pwt.sim.system.Crews} (getting on a gun and off it, the commander's and a trooper's alike,
 * G0024), {@link pl.mzebrows.pwt.sim.system.Guns} (the trigger pull) and {@link
 * pl.mzebrows.pwt.sim.system.Throws} (a throw). {@code ArchitectureTest.PORTS} lists the same.
 */
@NullMarked
package pl.mzebrows.pwt.sim.system;

import org.jspecify.annotations.NullMarked;
