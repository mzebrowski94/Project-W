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
- `CommanderInput` - one tick of what the player asked for. The only way input enters the
  simulation; `step` copies it into the commander's `Intent` before any system runs.
- `Spawner` - builds the entity kinds the game knows, so no system has to remember which components
  a commander or a projectile is made of.
- `def/` - immutable definitions: what a kind of thing *is*, as data. `UnitKind`/`UnitDef` for
  strains, `WeaponKind`/`WeaponDef` for guns, `PickupKind`/`PickupDef` for what lies on the field,
  and `Defs<K, V>`, the table that holds one definition per enum constant and refuses to be built
  with a hole in it. Also `Look` and `Side`, which are vocabulary rather than state. **This package
  depends on nothing else in the simulation** - components and systems read definitions, definitions
  never read them - and that one-way arrow is what stops a kind acquiring behaviour. ADR 0008 has
  why this is a typed table rather than a marker component or a data file.
- `collision/` - `SpatialGrid`, a dense grid over the arena with no hashing and therefore no
  iteration order that could vary between runs, and `ConstraintSolver`, which decides where bodies
  are allowed to be. Both are purely geometric and read `Collider` flags rather than asking what an
  entity is. Systems still all live in `system/`; this package is the machinery they call.
- `scenario/` - the arenas the menu can launch. `Scenarios` is the registry, and the list in it is
  the menu.

One entity exists that is not in the world at all: the **level entity**, spawned in `Simulation`'s
constructor and never removed, carrying state that belongs to the level rather than to anything
standing in it. It has no position and nothing to draw. It is how a pickup that grants troopers
reaches something to grant them to, without the message queue G0004 rejected - see G0007.

Combat runs on a fixed order of systems, and the order is the design: contact writes damage,
`PickupSystem` gives out whatever was claimed and `EvolutionSystem` turns what a unit has eaten into
what it becomes, `DamageSystem` turns damage owed into lost health and either marks the dead or starts
them dying, `DyingSystem` marks a body that has lain out its throes, reactions such as `RespawnSystem`
run while the dead are still there to react to, and `ReaperSystem` removes them last.

Pickups resolving *before* damage is deliberate: a grunt shot to a sliver that reaches a flower first
gets up whole and takes that tick's damage as the thing it has become. Nothing else in the simulation removes
an entity - `ArchitectureTest` fails the build if it tries.

Two more orderings carry meaning. `EnemyLeapSystem` runs after `EnemyChaseSystem` and overwrites the
velocity of anything mid-leap, which is why neither needs to know the other exists. `StunSystem` runs
after every driver and before movement, so it owns the velocity of anything held still - a driver that
zeroed a stunned unit's velocity itself would silently delete knockback.
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
- `GameContext` - what a screen may ask of the application. Screens are given this rather than
  `PwtGame`, and it deliberately has no `setScreen`: **a screen never switches itself**, it calls
  `queueScreen` and the swap happens once the frame has finished. `setScreen` runs the outgoing
  screen's `hide()` synchronously, so switching mid-frame frees GL resources that frame is still
  drawing with, which crashes the JVM with an access violation rather than an exception.
- `ScenarioSession` / `HarnessControls` / `MenuSelection` - everything the client *decides*, kept out
  of the classes that draw so it can be unit-tested without a GL context. `ClientArchitectureTest`
  enforces both halves of that split.
- `MenuScreen` / `ScenarioScreen` - the scenario list, and one running scenario with the alpha-phase
  harness. Thin by design: they read input, hand commands to the session, and draw.
- `Hud` / `DebugOverlay` - text in screen space, on their own pixel-unit viewport. The font is
  libGDX's built-in one, so the harness needs no art at all.
- `InputReader` - the only class that touches a keyboard or mouse. Produces a `CommanderInput` per
  frame.
- `WorldRenderer` - the placeholder renderer: every entity is a coloured circle. It reads each
  entity's `Appearance` and asks nothing else about it (G0003). Replaced by a sprite renderer once
  the atlas exists; the contract stays the same, which is that the simulation is never asked how to
  draw itself.
- `Palette` - the one place that turns a `Look` into a colour. A table indexed by the enum rather
  than a field per look, so a new kind of thing is a constant and a properties line; `GraphicConfig`
  holds the fallback per look in an exhaustive switch, which means a new look fails the client's
  compile until somebody decides what it looks like.
- `StateTint` - the other axis: how far to shift that colour for what the entity is currently
  *doing* (`Appearance.State`), so the same tell reads the same way on every kind of unit.
- `ChargeMark` - the third: how to draw something that is filling up. The simulation publishes one
  fraction (`Appearance.charge`) and says nothing about how it should look; this is the whole of the
  answer, and one mark serves every mechanic that fills up.
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
