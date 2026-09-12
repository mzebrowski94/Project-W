# 0009: The terrain field is its own module

**Trigger.** Deciding whether new code belongs in `map` or `sim`; writing map generation.

**Enforced by.** `MapArchitectureTest` - `map` may reach for none of `com.badlogic`, and no entity.

**Constraint.** The ground a level is played on - the tile grid, its terrain, its regions, and every
algorithm that produces one - lives in the `map` Gradle module. That module **depends on nothing**:
not libGDX, not Ashley, not `sim`. Dependencies run `lwjgl3 -> core -> sim -> map`.

The line, for anything whose side is not obvious: **`map` owns the field; `sim` owns what stands on
it and what happens over time.** A pure function of terrain and geometry may live in `map`. Anything
that reads a clock, an entity or a component lives in `sim`.

**Scope.** Level geography. A `TileMap` is world state of the same kind as `SimConfig` - a value the
simulation reads freely, every tick, from any system - so reading one from a system is not a boundary
crossing and needs no ceremony. `Simulation` is constructed with a map and its extent *is* the arena;
there is no separate arena size that could disagree with it.

It does not cover what terrain *costs* a unit. Binary passability is here because it is geometry and
generation cannot work without it - connectivity and finding somewhere to put an object are questions
about shape. Anything else terrain might do - slow a unit, hurt it - is a gameplay number in
`game.properties`, read by a system, exactly as a strain's numbers are. This is the third time the
codebase runs the same split: `Look` names a role and `Palette` decides the colour; `UnitKind` names
a strain and `UnitDef` decides its numbers; `Terrain` names a ground and `sim` and `core` decide the
rest.

Nor does it cover navigation *behaviour*. When walls force the pathing question, the field itself - a
flow field is a pure function of terrain and a goal - belongs here, and deciding who follows it and
how fast is a system in `sim`.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A `sim.map` package with an ArchUnit rule instead of a module.* Cheaper, and the rule would be as
trusted as any other here. Rejected because the failure mode it does not catch is the expensive one:
generation code that can see an `Entity` will eventually spawn one, and then map generation and
gameplay are one thing again - which is the exact outcome this separation exists to prevent. A
package rule can be satisfied while the import is added one class at a time.

**No:** *Putting `TileMap` in `sim` and only the generator in a module.* Rejected because it inverts the
dependency: a generator that constructs a `TileMap` would have to depend on `sim`, which puts all of
Ashley back on its classpath and achieves nothing.

**No:** *Moving `Rng` down into `map` so generation has a generator.* Rejected because `map` would then own
the game's randomness while being named for terrain, and `Rng`'s snapshot state belongs with the
simulation it snapshots. Instead `map` declares `Randomness` - one method - and `Rng` implements it,
so there is exactly one algorithm and map generation draws numbers the way the simulation does.

**Revisit when** something genuinely belongs to neither side - a piece of level data that is not
geography but is also not per-tick simulation state. A level *format* on disk is the likely one, and
it is a third thing rather than a reason to merge these two.
