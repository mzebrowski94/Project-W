# Architecture

## The one line that matters

```
lwjgl3  ->  core (client)  ->  sim
```

Dependencies point one way. The simulation does not know that rendering, input, files, or a window
exist. Everything else follows from that.

## sim

The whole game world. Owns an Ashley `PooledEngine`, the seeded `Rng`, the tick counter, and every
system that changes world state.

- `Simulation` - the world. `step()` advances it by exactly one fixed tick.
- `SimConfig` - the tunables it was built with. A record, handed in already parsed; `sim` reads no
  files.
- `Rng` - SplitMix64. The only randomness source. Its state is one `long`, so a world can be
  snapshotted and replayed exactly.
- `FixedStepClock` - turns a variable frame delta into a whole number of ticks, and exposes `alpha`
  for render interpolation. Long stalls are dropped, not queued.
- `component/` - pooled data. Public mutable fields, `reset()`, no behaviour.
- `system/` - all behaviour. One system, one responsibility.

### Adding to the simulation

A mechanic is a component plus a system, in that order. The `new-system` skill has the procedure.

The one thing worth knowing before you start: **system registration order is part of the
simulation's behaviour.** Systems run in the order they were added to `Simulation`, and changing
that order changes results. Add deliberately.

## core (client)

The libGDX application. Reads config, loads assets, owns the viewport, draws the world, drives the
loop.

- `PwtGame` - `ApplicationAdapter`. Per frame: ask `FixedStepClock` how many steps to run, run them,
  clear, render with `alpha`.
- `GameProperties` / `SimConfigLoader` / `GraphicConfig` - config loading. Every value has a
  fallback and logs when it is used.
- `Assets` - every asset path as a constant, an `AssetManager` behind them. Missing files log and
  are skipped, never thrown.
- `WorldRenderer` - the placeholder renderer: every entity is a coloured circle. Replaced by a
  sprite renderer once the atlas exists; the contract stays the same, which is that the simulation
  is never asked how to draw itself.
- `Log` - the logging facade.

## Coordinates

The simulation works in **world units** and knows nothing about pixels. `graphic.virtualWidth` and
`graphic.virtualHeight` describe how many world units fill the viewport; `FitViewport` handles every
window size and aspect ratio from there. No code outside the viewport setup converts between world
units and pixels.

## Assets

Source art goes in `assets-raw/`. `./gradlew :core:packTextures` packs it into
`assets/atlas/game.atlas`, which is the only image asset the game loads. Run it after changing art;
it is deliberately not wired into every build.

## Rendering vs simulation, in practice

The rule that keeps the boundary honest: **rendering may read simulation state, never write it, and
the simulation may not ask a question whose answer depends on rendering.** If a system needs to know
a sprite's size, that size belongs in a component in world units, not in a texture region.

## What this buys us

Deterministic replay today, and lockstep or rollback networking later without a rewrite. That was
the explicit trade decided in ADR 0004 - single-player now, net-ready simulation from the start.
