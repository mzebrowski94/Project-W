package pl.mzebrows.pwt.sim.scenario;

import pl.mzebrows.pwt.sim.def.Cycle;
import pl.mzebrows.pwt.sim.def.EmplacementKind;
import pl.mzebrows.pwt.sim.def.PickupKind;
import pl.mzebrows.pwt.sim.def.UnitKind;
import pl.mzebrows.pwt.sim.def.WorkedSiteKind;

/**
 * One thing a mission's placement put down (G0068) or its finale's set piece stands (G0064), where, and in which
 * biome - the finale's position being the march's length. What a {@code Mission} plans and its scenario spawns, so
 * the sweep holds exactly what is spawned to the rules.
 */
public sealed interface Placed {

    /** Where it stands, in world units. */
    float x();

    /** Where it stands, in world units. */
    float y();

    /** The position in the march of the biome it was put down in, the first zero; the finale's after the last. */
    int biome();

    /** A control point: the goal line leads east through them. */
    record Point(float x, float y, int biome) implements Placed {}

    /** A hole of one strain, asleep until somebody in its biome comes near (G0062). */
    record Nest(UnitKind strain, float x, float y, int biome) implements Placed {}

    /** A creature put down unaware, one of a pack. */
    record Loose(UnitKind strain, float x, float y, int biome) implements Placed {}

    /**
     * A creature of a house pack (G0112), put down unaware inside a house: its patch is the house - centred on
     * {@code homeX, homeY}, no wider than {@code roam} - and it never drifts, so the shut doors hold it in.
     */
    record Housed(UnitKind strain, float x, float y, float homeX, float homeY, float roam, int biome)
            implements Placed {}

    /** A pickup lying about: a crate, a medkit, food. */
    record Pickup(PickupKind kind, float x, float y, int biome) implements Placed {}

    /** An explosive barrel, by a hole or a pack. */
    record Barrel(float x, float y, int biome) implements Placed {}

    /** A boulder, round rock. */
    record Boulder(float x, float y, int biome) implements Placed {}

    /** A tree: a trunk of this radius that stands as a boulder does, under its foliage (G0072). */
    record Tree(float x, float y, float trunk, int biome) implements Placed {}

    /** A geyser this wide, ground that erupts in fire on a cycle of its own (G0089). */
    record Geyser(float x, float y, float radius, int biome) implements Placed {}
    /** A stone: rock of this radius, drawn as a pentagon, that stands as a boulder does. */
    record Stone(float x, float y, float radius, int biome) implements Placed {}

    /** A rabbit, food that runs. */
    record Rabbit(float x, float y, int biome) implements Placed {}

    /**
     * A finale's point that holds afterwards (G0061): taking it puts the post down and lets the waves go, and
     * holding it means nothing hostile within {@code watch}, in world units.
     */
    record Hold(float x, float y, float watch, int biome) implements Placed {}

    /** A gun on a mount, a finale's. */
    record Turret(float x, float y, int biome) implements Placed {}

    /** A mine, a finale's, which only the other side sets off. */
    record Mine(float x, float y, int biome) implements Placed {}

    /**
     * An emplacement of this kind (G0113): a spire, and the cycle it was dealt as it was put down - the one it fires for
     * the whole level.
     */
    record Emplacement(EmplacementKind kind, float x, float y, Cycle cycle, int biome) implements Placed {}

    /** A worked site of this kind (G0111): a mainframe, standing in a house against a wall. A site, as a point is. */
    record Worked(WorkedSiteKind kind, float x, float y, int biome) implements Placed {}
}
