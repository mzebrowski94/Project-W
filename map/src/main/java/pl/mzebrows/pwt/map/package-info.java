/**
 * The terrain field a level is played on, and the algorithms that build one.
 *
 * <p>This module depends on nothing - not libGDX, not Ashley, not {@code sim}. A generator here
 * cannot reach for an entity even by accident, which is the whole reason it is a module rather than
 * a package. {@code MapArchitectureTest} fails the build if that changes.
 *
 * <p>The dividing line, when something new does not obviously belong on one side: <strong>this
 * module owns the field; {@code sim} owns what stands on it and what happens over time.</strong> A
 * pure function of terrain and geometry may live here. Anything that reads a clock, an entity or a
 * component lives in {@code sim}.
 */
@NullMarked
package pl.mzebrows.pwt.map;

import org.jspecify.annotations.NullMarked;
