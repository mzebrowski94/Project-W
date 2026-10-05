package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;

/**
 * One thing a mission's placement put down (G0068), where, and in which biome. What a {@code Mission} plans and its
 * scenario spawns, so the sweep holds exactly what is spawned to the rules.
 */
public sealed interface Placed {

    /** Where it stands, in world units. */
    float x();

    /** Where it stands, in world units. */
    float y();

    /** The position in the march of the biome it was put down in, the first zero. */
    int biome();

    /** A control point: the goal line leads east through them. */
    record Point(float x, float y, int biome) implements Placed {}

    /** A hole of one strain, asleep until somebody in its biome comes near (G0062). */
    record Nest(UnitKind strain, float x, float y, int biome) implements Placed {}

    /** A creature put down unaware, one of a pack. */
    record Loose(UnitKind strain, float x, float y, int biome) implements Placed {}

    /** A pickup lying about: a crate, a medkit, food. */
    record Pickup(PickupKind kind, float x, float y, int biome) implements Placed {}

    /** An explosive barrel, by a hole or a pack. */
    record Barrel(float x, float y, int biome) implements Placed {}

    /** A boulder, round rock. */
    record Boulder(float x, float y, int biome) implements Placed {}

    /** A rabbit, food that runs. */
    record Rabbit(float x, float y, int biome) implements Placed {}
}
