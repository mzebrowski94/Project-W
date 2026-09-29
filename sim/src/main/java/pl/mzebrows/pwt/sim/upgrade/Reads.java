package pl.mzebrows.pwt.sim.upgrade;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the {@link Modifiers} method that reads a {@link Stat}. Every stat is on exactly one, and that
 * method has exactly one caller outside the package - {@code ArchitectureTest} holds both.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Reads {

    Stat value();
}
