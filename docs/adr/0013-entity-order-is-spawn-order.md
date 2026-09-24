# 0013: Entity order is spawn order

**Trigger.** Iterating entities anywhere the order can change a result; upgrading or replacing Ashley;
writing or restoring a snapshot; registering a family after the simulation has been built.

**Enforced by.** `SpawnOrderTest.keepsTheSurvivorsInSpawnOrderWhenOthersDieOutOfOrder`,
`.keepsAFamilyAskedForAfterTheWorldWasBuiltInSpawnOrderToo`; `CompositionTest` - every registered
scenario, 600 ticks, and no entity's components change after the tick it appears. `SpawnOrder` itself
fails the construction of every `Simulation` if the Ashley internals it reaches for have moved.
`SnapshotRoundTripTest` - every registered scenario restored from a snapshot, every family's order
compared with the original's, before and after both step on.

**Constraint.** The engine's entity list and every family's list are kept in the order their entities
were spawned, and a removal closes the gap rather than moving the last entity into it. Ashley 1.7.4
keeps both in unordered gdx `Array`s (`new Array(false, 16)`), so a small shim sets `ordered` on them
when the `Simulation` is built. It is the one place in the codebase that reaches inside Ashley.

What that buys - **provided an entity's composition is fixed when it is spawned.** `ArchitectureTest`
already says *who* may change it: the `Spawner`, and `WorldCodec` restoring a snapshot, which is
spawning from what another machine wrote down. This record adds *when*: at spawn, and never after, with
one exception - `Dead`, which is reaped before the tick ends. A component added to a living entity would
put it at the end of that family rather than in its spawn place, and nothing would notice until somebody
dropped in. With that held, at every tick boundary **every family is the engine's list, filtered**. A snapshot records one order, and restoring it by adding entities in that
order restores every family with it.

**Why.** Order is state here. Which of two takers claims a pickup, the order pairs are resolved in, the
order floats are summed in and which entity draws which number from a stream all follow it. With
swap-on-remove, order after the first death is a function of history - reproducible on one machine,
which is why nothing noticed, and impossible to carry in a snapshot, which is what G0038's drop-in needs.

**Scope.** The simulation's engine. Not families the client registers for drawing: nothing reads those
for a result. It supplements ADR 0001 rather than changing the choice of framework. The cost is a
removal that shifts the tail of each list it leaves - nothing at a few hundred entities, where a whole
step measured 0.8 ms - and one change of behaviour: iteration after a death no longer matches today's.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Recording each family's order in the snapshot and writing it back by reflection.* One order per
family to keep consistent instead of one order, and a family registered after the restore breaks it.

**No:** *A stable id on every entity and a sort in every system.* Every system touched, and a cost every
tick, for what one flag achieves once.

**No:** *Forking Ashley.* A fork to change one boolean, and every later upgrade becomes a merge.

**No:** *A component added to an entity after it was spawned.* A state is a field on a component the
entity already has (G0010); a new family membership mid-life is an order no snapshot reproduces.

**No:** *Sorting only when a snapshot is taken.* The host's running order and the joiner's restored one
would then differ, which is the desync this record exists to prevent.

**Revisit when** Ashley is upgraded or replaced - the shim's reach is the first thing to check - or entity
counts reach the thousands and an ordered removal shows up in frame time.
