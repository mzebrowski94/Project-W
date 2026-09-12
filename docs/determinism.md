# Determinism

The simulation must produce identical output from identical input on every run and every machine. It is
what makes replays, deterministic debugging, scenario tests and any future lockstep networking possible
(ADR 0004, pillar 6). Cheap to keep, expensive to recover.

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
scatter and gameplay each get their own.

Sharing one sequence keeps every run reproducible and makes the game miserable to tune: re-tuning how
often anything draws a number reshuffles every number every other system gets afterwards. A level stays
reproducible from a single seed either way.

`Rng` is SplitMix64 and its state is one `long`, so a world can be snapshotted and replayed exactly.

## What catches a break

| Guard | Catches |
|---|---|
| `ArchitectureTest` | The mechanical bans, in `sim`. |
| `MapArchitectureTest` | The same in `map`, plus all of `com.badlogic` and any reference to an entity. |
| `SimulationDeterminismTest` | Everything else - replays every registered scenario twice and compares. |
| `ScenariosTest` | Nothing spawned in or against rock; nothing spawned where the commander cannot walk. |

A failure in `SimulationDeterminismTest` is a design bug, not a flake. Treat it that way.

## The client has no such guard

`core` is not covered by these rules and cannot be: it reads a real frame delta and a real clock. The
failure worth looking for there is rendering code that feeds a value back into the simulation -
`FixedStepClock` is the only sanctioned bridge, and it turns a variable delta into a whole number of
ticks. Long stalls are dropped, not queued.
