package pl.mzebrows.pwt.sim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The key a config record component is read from when it is not the component's own name - {@code
 * SimConfig.units} is written {@code sim.unit.<kind>} in {@code assets/sim/*.properties}. {@link ConfigBinder}
 * reads it; nothing else should need it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface ConfigKey {

    /** The key segment, which may itself contain dots: {@code "ai.chance"}. */
    String value();
}
