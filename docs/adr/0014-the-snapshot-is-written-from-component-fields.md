# 0014: The snapshot is written from the components' own fields

**Trigger.** Adding a field to a component or a component to the game; giving a system a field; anything
that writes or restores a snapshot; a desync that appears only after somebody dropped in.

**Enforced by.** `ArchitectureTest.componentFieldsAreWhatASnapshotCanWrite`,
`.systemsHoldNoStateAcrossTicks` (a field that is neither final nor a family view says `@NotState`),
`.onlyTheStreamTableStartsARandomStream`; `ComponentResetTest` - every component, after `reset()`,
equal to a new one, private fields included. Still to come: the snapshot round-trip test over every
registered scenario - snapshot at a tick, restore into a fresh world, step both, compare the whole world
and every family's order - which is what proves a `@NotState` cache pure.

**Constraint.**

- **The codec is derived from the component classes, not written for them.** Once, when the simulation
  starts, reflection builds a plan per component class: its fields, in a fixed order, each with a writer.
  A snapshot walks the entities in spawn order (ADR 0013) and runs the plans. Nothing reflects during a
  tick, and a new component or a new field needs no codec code at all.
- **A component field is a primitive, an enum, an `Entity`, an immutable record of those, or a
  collection the codec names.** An `Entity` is written as its position in spawn order; a record - a
  definition such as `Weapon.slicks` - is written as its values. The collections are the ones the codec knows how to write -
  today one, `Waves.pending` - and adding another is a line in the codec and a reason in the review.
  Anything else fails the build, not a session. Private fields count: a snapshot is the whole component.
- **A system holds no state across ticks.** What must survive a tick lives on a component - on the level
  entity when it belongs to the level (G0007) - and every random stream lives in one table on
  `Simulation`, which the snapshot writes as it writes a component. What a system may keep is scratch it
  overwrites before reading, and caches that are a pure function of their key; the round-trip test is
  what proves a cache pure.
- **A snapshot carries a build hash and nothing else about versions.** Peers that disagree on the build
  were refused at the handshake (ADR 0012), so the codec never reads an older layout.

**Why.** Drop-in (G0038), desync recovery and desync dumps (ADR 0012) all need the whole world written
down. The components were already shaped for it - public mutable fields and a `reset()`, no behaviour -
and at the time of writing their public fields are 256 floats, 65 booleans, 17 ints, 4 longs, enums, two
`Entity` references, one definition record and nothing else, and the only collection anywhere is
`Waves.pending`. Writing that by hand would be a read and a write per component, kept
in step with every field forever: the tax a new mechanic would pay online, and a source of desyncs that
only a drop-in reveals.

**Scope.** What a snapshot contains and how it is produced. Not how it travels - ADR 0012's control channel
- and not when one is taken. Not rollback, which would restore in place every tick and needs its own
record if it is ever built. The map is not in the snapshot: it is rebuilt from the scenario and the seed.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A hand-written read and write per component.* The per-mechanic tax this record exists to remove,
and a silent desync every time the two drift apart.

**No:** *Java serialization, Kryo, or any format that names a class in the payload.* ADR 0012's rule about
the wire applies to a snapshot, which crosses it.

**No:** *Reflection during a tick.* The plan is built once; a tick runs plain field access.

**No:** *A counter, a phase or a stream held in a system.* It is exactly the state a snapshot misses.

A plan generated at build time by an annotation processor is not rejected, deferred: it removes the
startup reflection and is the better choice if the component count or startup time ever makes it matter.

**Revisit when** a component needs a field type the codec cannot name, or startup reflection shows up in
the time to the first frame.
