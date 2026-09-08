/**
 * Immutable definitions: what a kind of thing <em>is</em>, as data.
 *
 * <p>Nothing here has behaviour and nothing here is mutable. A definition is read once at spawn or
 * once at an evolution and copied into the pooled components that carry the runtime state, which is
 * why a definition can be shared by every entity of its kind without any of them being able to
 * change it.
 *
 * <p>This package depends on nothing else in the simulation. Components and systems read definitions;
 * definitions never read them. That one-way arrow is what keeps a kind from quietly acquiring
 * behaviour.
 */
@NullMarked
package pl.mzebrows.pwt.sim.def;

import org.jspecify.annotations.NullMarked;
