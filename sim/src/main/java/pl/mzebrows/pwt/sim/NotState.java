package pl.mzebrows.pwt.sim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A system field that is not state: scratch overwritten before it is read on every tick, or a cache
 * that is a pure function of its key.
 *
 * <p>A system holds no state across ticks (ADR 0014) - what must survive a tick lives on a component,
 * where a snapshot carries it. {@code ArchitectureTest} therefore refuses a system any field that is
 * neither {@code final} nor a family view, unless it says here which of the two it is. The annotation
 * is a claim; the snapshot round-trip test is what checks it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotState {

    /** Which of the two, and why: "scratch" or "cache of ...". */
    String value();
}
