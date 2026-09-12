# ADR 0001: Ashley is the entity framework

**Trigger.** Creating an entity or component, adding a system, or proposing a different ECS.

**Enforced by.** Nothing mechanical for the caching rules - caught by reading and the `perf-audit` skill.

**Constraint.** Entities are Ashley entities, created through `PooledEngine`. Components implement
`Component` and `Pool.Poolable`. Every system caches its `ImmutableArray` once at construction or
`addedToEngine` and holds `ComponentMapper`s in `private static final` fields - Ashley's `Family`
matching and `getEntitiesFor` allocate when called per frame.

**Scope.** All simulation state. Does not extend to rendering: `WorldRenderer` reads the engine but
is not an Ashley system, because rendering must not be able to advance the world.

**DO NOT BUILD** - rejected, and likely to be re-proposed. artemis-odb - faster archetype storage and actually
maintained, but a second set of conventions to learn for a gain we cannot yet measure. A hand-rolled
ECS - infrastructure work before there is a game.

**Revisit when** entity counts reach the tens of thousands and profiling names Ashley iteration as
the bottleneck. Ashley is unmaintained since 2021, so this is expected eventually rather than
hypothetically. All behaviour sits behind `Simulation`, which keeps the swap contained.
