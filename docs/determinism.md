# Determinism

The simulation must produce identical output from identical input on every run and every machine. It is
what makes replays, deterministic debugging, scenario tests and lockstep online play possible
(ADR 0004, ADR 0012, pillars 6 and 7). Cheap to keep, expensive to recover.

## The rules

The bans - unseeded randomness, the wall clock, `Math`'s transcendentals, hash iteration order - are
rows in `docs/conventions.md`'s table, with their substitutes. Enforced by `ArchitectureTest` and
`MapArchitectureTest`, so a violation fails the build rather than needing to be remembered.

Three that a table cannot express:

- **Advance only through `Simulation.step()`**, always by `SimConfig.tickSeconds()`. Never a variable
  delta, never a partial tick.
- **Keep floating-point operation order stable.** Do not reorder arithmetic "for readability".
- **Rendering may read simulation state, never write it.** It may interpolate between two states using
  `alpha`; it may not feed a value back. If a system needs a sprite's size, that size is a component in
  world units, not a texture region.

## One RNG stream per concern

`Rng.stream` derives a generator per concern from the level seed; `RngStream` names them. Terrain,
scatter and gameplay each get their own. Every stream the simulation draws from lives in one table,
`Streams`, one instance per concern, so a snapshot can write them all (ADR 0014).

Sharing one sequence keeps every run reproducible and makes the game miserable to tune: re-tuning how
often anything draws a number reshuffles every number every other system gets afterwards. A level stays
reproducible from a single seed either way.

`Rng` is SplitMix64 and its state is one `long`, so a world can be snapshotted and replayed exactly.

**Derived streams split a concern while a world is built** (G0065, missions ticket 05). `Randomness.derive(salt)`
makes a child from a stream's current state without advancing it - the same mixing `Rng.stream` uses, which is
built on it - so `map` can split the MAP stream per biome and per coat while still owning no generator, and a
mission's deal splits the MISSION concern per biome. Retuning one coat of one biome then moves nothing else.
Three rules:

- **A stream either derives or draws, never both.** A child is made from its parent's state, so a parent that
  had drawn first would make its children depend on how much it drew. No two call sites derive under one salt
  path: two children from one path are one sequence twice.
- **A derived stream lives only during the build.** Everything drawn once the first tick runs comes from a
  concern in `Streams`, so a snapshot carries it (ADR 0014). A restored world is the build plus the snapshot.
- **Key a child by position, not by kind.** A biome's coats derive from its place in the march.

## What catches a break

| Guard | Catches |
|---|---|
| `ArchitectureTest` | The mechanical bans, in `sim`. |
| `MapArchitectureTest` | The same in `map`, plus all of `com.badlogic` and any reference to an entity. |
| `SimulationDeterminismTest` | Everything else - replays every registered scenario twice and compares whole worlds, every field of every component and every stream. |
| `SnapshotRoundTripTest` | State a snapshot does not carry - a counter in a system, a cache that is not a pure function of the world (ADR 0014). |
| `DeterminismFuzzTest` | Both, over five minutes of every scenario under random input with restores at random ticks. Opt-in: `PWT_FUZZ=1`, about a minute. |
| `SessionTest` | The same across machines: peers over a bad network ending on one world (ADR 0012). |
| `ScenariosTest` | Nothing spawned in or against rock; nothing spawned where the commander cannot walk. |

A failure in `SimulationDeterminismTest` is a design bug, not a flake. Treat it that way.

**Across machines** the check runs during play: every `checksumTicks` each peer hashes a snapshot of its
world, and one that disagrees with the host is dumped and healed (ADR 0012). Windows and Linux have been
shown to agree bit for bit over every scenario (2026-09-24); the dumps are what find the first case where
they do not.

## The client has no such guard

`core` is not covered by these rules and cannot be: it reads a real frame delta and a real clock. The
failure worth looking for there is rendering code that feeds a value back into the simulation -
`FixedStepClock` is the only sanctioned bridge, and it turns a variable delta into a whole number of
ticks. Long stalls are dropped, not queued.
