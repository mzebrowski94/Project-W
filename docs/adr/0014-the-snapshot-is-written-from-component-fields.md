# 0014: The snapshot is written from the components' own fields

**Trigger.** Adding a field to a component or a component to the game; giving a system a field; anything
that writes or restores a snapshot; a desync that appears only after somebody dropped in.

**Enforced by.** `ArchitectureTest.componentFieldsAreWhatASnapshotCanWrite`,
`.systemsHoldNoStateAcrossTicks` (a field that is neither final nor a family view says `@NotState`),
`.onlyTheStreamTableStartsARandomStream`; `ComponentResetTest` - every component, after `reset()`,
equal to a new one, private fields included, and `.numbersEveryComponentForTheSnapshot` - every
component class is in `Components.ALL`. `SnapshotRoundTripTest` - every registered scenario snapshotted
at tick 300, restored into a blank world, written again byte for byte, then both stepped 300 ticks with
the hashes compared every tick and every family's order compared at both ends: what proves a
`@NotState` cache pure. `WorldCodecTest` - a snapshot's bytes hash as its world does, and a diff names
the field. Opt-in, `DeterminismFuzzTest` (`PWT_FUZZ=1`) - every scenario for five minutes of random
input, a twin restored at random ticks stepping alongside.

**Constraint.**

- **The codec is derived from the component classes, not written for them.** When `WorldCodec` loads,
  reflection builds a plan per component class: its fields sorted by name, each with a writer. A
  component is numbered by its place in `Components.ALL`, sorted by simple name, so no class name
  crosses the wire. A snapshot writes the stream table, then walks the entities in spawn order
  (ADR 0013) and runs the plans. The same walk feeds a hash instead of bytes, so the checksum and the
  snapshot cannot disagree about what the world is. A new field needs no codec code at all; a new
  component is one line in `Components.ALL`, and `ComponentResetTest` fails until it is there.
- **A snapshot is restored into a blank world** - `Scenarios.blank`: the map rebuilt from the scenario
  and the seed, nothing on it but the level entity. The snapshot overwrites the level entity's components
  and spawns everything else in the order it was written, then fills in `Entity` fields once every
  entity exists.
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
- **A snapshot carries nothing about versions** - a magic number, so something that is not a snapshot
  is refused rather than misread. Peers that disagree on the build were refused at the handshake
  (ADR 0012), so the codec never reads an older layout; a desync dump written to disk carries the build
  id beside the snapshot.

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

**No:** *Reflection during a tick.* A tick never touches the codec. The plan is built once; a snapshot
or a checksum reads fields through it and allocates nothing per entity.

**No:** *A counter, a phase or a stream held in a system.* It is exactly the state a snapshot misses.

A plan generated at build time by an annotation processor is not rejected, deferred: it removes the
startup reflection and is the better choice if the component count or startup time ever makes it matter.

Measured when it was built: Adventure at 285 entities is 128 KB, 1.4 ms to write and 0.35 ms to hash;
restoring it takes about 20 ms. Every value is 4 or 8 bytes, booleans included; compressing is left for
when a size matters.

**Revisit when** a component needs a field type the codec cannot name, startup reflection shows up in
the time to the first frame, or a snapshot is slow enough to send that a drop-in waits on it.
