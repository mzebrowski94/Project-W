# Architecture

## The one line that matters

```
lwjgl3  ->  core (client)  ->  net  ->  sim  ->  map
```

Dependencies point one way. The simulation does not know that rendering, input, files, a window or a
network exist. Everything else follows from that. `net` depends on `sim` because a session steps a
`Simulation` and writes its snapshots (ADR 0012) - never the other way.

## map

The ground a level is played on, and nothing else. **This module depends on nothing** - not libGDX,
not Ashley, not `sim` - so a generator cannot reach for an entity even by accident. That is the whole
reason it is a module rather than a package; ADR 0009 has the argument.

- `Terrain` - a kind of ground. Names a role, never a colour and never a number to balance. Carries
  one thing beyond its name, `passable`, because that is geometry rather than gameplay and generation
  cannot check connectivity or find a spot without it. What terrain *costs* a unit is a number in
  `game.properties`, read by a system.
- `TileMap` - the grid: terrain and a region id per tile, immutable once built. **It owns the only
  conversion between world units and tile coordinates**; nothing else may open-code
  `(int) (x / tileSize)`. Anything outside the grid reads as `ROCK` and therefore as impassable, so
  callers need no bounds guard and a mistake at the border fails safe.
- `TileMap.Builder` - how every map is assembled, generated or hand-authored alike, so nothing
  downstream can tell them apart.
- `Scatter` - puts circles on the ground: on terrain of a given kind, clear of each other and of
  anything reserved, **anchored to a tile but never centred in one** (G0009), and it returns how many
  it managed so a full arena and a broken generator do not look identical. Deliberately grid-anchored
  rather than Poisson-disc: the grid is the unit a level is authored in and terrain is per tile, which
  is what makes "mushrooms grow on organic ground" a filter instead of a rejection loop.
- `FlowField` - which way to walk, from any tile, to reach one goal, for a body of one size. A
  breadth-first pass out from the goal, then the gradient of that distance field, sampled with a
  bilinear blend so following it is a curve rather than a staircase. **Clearance is baked in**: a
  field is built for a body radius and a tile is only ground if a circle that size fits on it, which
  is what makes a one-tile crack a route for a grunt and a wall for a brute. Nav data is a function of
  terrain and a goal, so it lives here; who follows it, and how fast, is a system.
- `Maps` - ready-made layouts and the checks that keep one honest. `uniform` for tests; `regions`
  partitions open ground by nearest seed; `reachableFrom`/`connects` flood-fill a map so a layout can
  be validated rather than trusted, and `sealUnreachable` fills in the pockets they find. Four-connected,
  because a unit has width and a diagonal pinch between two rock corners is not a way through.
- `Randomness` - one method, `nextLong`, plus everything derived from it. The caller supplies the
  stream; `sim`'s `Rng` implements this, so there is one algorithm rather than two.
- `Bag` - a shuffle bag over a range: every value once before any repeats. Its whole state is a `long`
  the caller keeps, so a bag on a component is snapshotted like any other field.

libGDX ships its own tiled map API and we deliberately do not use it as the model - `TiledMapTile`
declares `getTextureRegion()`, so it is a rendering model with map data attached. ADR 0010 has the
full argument, and the two parts of it that remain worth adopting later.

**A `TileMap` is world state of the same kind as `SimConfig`**: a value systems read freely and
nothing writes during a tick. Reading one from a system is not a boundary crossing.

## sim

The whole game world. Owns an Ashley `PooledEngine`, the seeded `Rng`, the tick counter, and every
system that changes world state.

- `Simulation` - the world. `step()` advances it by exactly one fixed tick.
- `SimConfig` - the tunables it was built with: exactly `assets/game.properties`, bound by
  `ConfigBinder` from record component names, so a tunable is a component and a line in the file and
  nothing else. `sim` reads no files: `:sim:generateShippedConfig` compiles the file's values into a
  constant, which `SimConfig.defaults()` binds - what every test runs and what a missing or broken key
  falls back to - and any other text (a player's file, a host's) arrives through `SimConfig.parse`.
  There is no literal default anywhere in Java; a second copy of a number is how 21 of them drifted.
- `Rng` - SplitMix64. The only randomness source. Its state is one `long`, so a world can be
  snapshotted and replayed exactly. `Rng.stream` derives one generator per concern from the level
  seed; `RngStream` names them. Sharing one sequence between concerns keeps every run reproducible
  and makes the game miserable to tune, because re-tuning how often anything draws a number reshuffles
  every number every other system gets afterwards.
- `FixedStepClock` - turns a variable frame delta into a whole number of ticks, and exposes `alpha`
  for render interpolation. Long stalls are dropped, not queued.
- `CommanderInput` - one tick of what a player is *holding*. `PlayerInputs` carries one per slot plus
  that tick's *input events* - an order, a join, the harness's invulnerability switch - which are
  applied once and never held (ADR 0012). The only way input enters the simulation; `step` copies it
  into each commander's `Intent`, and seats a joining player, before any system runs.
- `Streams` - every random stream the level draws from, in one table built from the seed, so a
  snapshot can write them (ADR 0014). `ArchitectureTest` refuses a stream started anywhere else.
- `SpawnOrder` - keeps Ashley's entity list and every family in spawn order (ADR 0013). The one class
  that reaches inside Ashley.
- `Handles` - finding an entity again from its `Handle`, a `long` issued once per spawn and never
  reused (ADR 0016). The only way a component refers to another entity across ticks.
- `WorldCodec` - the whole world as bytes and back, and as one hash (ADR 0014): built once by
  reflection from the component classes numbered in `component/Components.ALL`, so a new field needs no
  code here. `snapshot`, `restore` into `Scenarios.blank`, `hash`, and `differences` - two worlds field
  by field, which is what a desync report is made of.
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
  are allowed to be - separating overlapping pairs, pushing bodies out of impassable terrain, and
  clamping them inside the arena, in that order, a few passes per tick. Terrain is a clamp and never
  a body, so circles-only collision is untouched by it. Both are purely geometric and read `Collider` flags rather than asking what an
  entity is. Systems still all live in `system/`; this package is the machinery they call.
- `scenario/` - the arenas the menu can launch. `Scenarios` is the registry, and the list in it is
  the menu. `Scenarios.world` is the **only** place a playable world is assembled - config, then map,
  then simulation, then contents, in that order, because the map's extent is the arena and everything
  is sized against it. The harness, the determinism test and the scenario tests all go through it, so
  they cannot drift into building a world differently.

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

**Driving a unit is a pipeline, and the seam is `Steering`.** `PerceptionSystem` decides what has a
unit's attention - the commander, something edible, or nothing; `NavigationSystem` gives it a
direction, out of a flow field built for its strain's body size when it is hunting and straight at
its dinner when it is not; `WanderSystem` ambles whatever is left around the patch it was spawned in.
Then `AvoidanceSystem` bends that course around anything it was about to walk into, and `DodgeSystem`
overrides it outright for a creature with a shot coming at it. All of them write a *desired velocity*
and none writes a velocity, so several systems can have an opinion without any of them knowing the
others exist - and avoidance can only do its job at all because it can see what the unit intended
before it adjusts it. `DriveSystem` commits that to `Velocity` once, at the end, and clears it - which
is why a unit no system spoke for this tick stands still, and why there is no halt-everything branch
in any driver. G0010 has the argument for deciding in a field rather than in a tree.

**The order inside that pipeline is itself design.** Avoidance runs before the dodge and not after,
because a dodge bent around a rock would not be the same dodge twice and G0012 promises that it is.
A cornered rabbit therefore sidesteps into a wall, which is what being cornered should cost it.

Two more orderings carry meaning. `EnemyLeapSystem` runs after `DriveSystem` and overwrites the
velocity of anything mid-leap outright, because a leap is committed and is not one opinion among
several. `StunSystem` runs after every driver and before movement, so it owns the velocity of anything
held still - a driver that zeroed a stunned unit's velocity itself would silently delete knockback,
which is why `DriveSystem` leaves a held unit entirely alone rather than writing a zero over it.
- `component/` - pooled data. Public mutable fields, `reset()`, no behaviour.
- `system/` - all behaviour. One system, one responsibility.

### Adding to the simulation

A mechanic is a component plus a system, in that order. The `new-system` skill has the procedure.

The one thing worth knowing before you start: **system registration order is part of the
simulation's behaviour.** Systems run in the order they were added to `Simulation`, and changing
that order changes results. Add deliberately.

## net

Networking, and nothing the game's rules depend on (ADR 0012). No libGDX, and Netty only inside
`net.netty` - `NetArchitectureTest` enforces both, and that the clock is read only through `Clock`.

- `NetConfig` - the tunables: `network.properties` bound by component name (`ConfigBinder`), the shipped
  file compiled in as `defaults()`; the client finds a player's copy and hands its text to `parse`.
- `transport/` - the **port**. `Transport` opens **links** to other machines and carries
  `Delivery.RELIABLE` and `Delivery.UNRELIABLE` messages across them; everything that happens is handed
  over on the caller's thread by `poll`, never pushed from a network thread - the gamepad port's lesson
  (ADR 0011) applied again. `ConditionedTransport` wraps any transport and makes it late, jittery, lossy
  or repetitive, both ways and from a seed, with conditions that can change while it runs - which is how
  two instances on one machine, the latency playtest and a test all get a bad network on demand.
- `loopback/` - `LoopbackNetwork`: transports that reach each other in memory, perfect on purpose, for
  tests of everything above the port. Bad networks come from wrapping it, so there is one way to lag.
- `netty/` - `NettyTransport`: TCP for reliable messages, UDP for unreliable ones, one port number. A
  link opens only after a handshake proves the joiner's datagrams reach the host, so "cannot connect"
  arrives with the step that failed. Heartbeats measure round trip and close a link that goes quiet.
- `log/` - the connection log. `NetLog` writes one `key=value` line per event, one file per instance,
  on `java.util.logging` so Netty's own lines land there too. **Nothing is logged per message:**
  `NetTrace` records every message into a preallocated ring and dumps a link's part of it when the link
  closes badly, and `LinkStats` writes each link's numbers every `statsSeconds`.
- `lan/` - finding a session on the local network (G0038). A host's `LanBeacon` sends a
  `LanAnnouncement` once a second to every interface's broadcast address, and to this PC, as
  `LanAddresses` finds them; a joiner's `LanListener` hears them on a shared port and keeps one entry per
  session. `RoomCode` is what a player types to name one. JDK datagram sockets, polled like the transport.
- `session/` - lockstep itself (ADR 0012). `Sequencer` seals one `Bundle` per tick - every seated slot's
  held input, the orders that have arrived, a restart or god mode switch - and holds no world;
  `SequencerThread` runs it beside the host's game. `PeerSession` is one machine's place in a session:
  it tags its players' input a lead of ticks ahead, steps its `Simulation` only on sealed bundles, sends a
  checksum every `checksumTicks`, and restores a snapshot to drop in or be healed. The host's own game is a
  `PeerSession` on a link to its sequencer like everyone else's. `WorldFactory` is how a session turns
  the host's shipped `SessionSetup` - config text, scenario, seed - into worlds; the client's is
  `ScenarioWorlds`. `BuildId` reads the id `:net:generateBuildId` writes. `DesyncDump` and
  `DesyncReport` are what a desync leaves behind and how it is read. Messages are bytes written and read
  by `WireWriter` and `WireReader` against `Msg`'s numbers - nothing on the wire names a class.

## core (client)

The libGDX application. Reads config, loads assets, owns the viewport, draws the world, drives the
loop.

- `PwtGame` - `ApplicationAdapter`. Per frame: ask `FixedStepClock` how many steps to run, run them,
  clear, render with `alpha`.
- `GameProperties` / `SimConfigLoader` / `GraphicConfig` - config loading. Every value has a
  fallback and logs when it is used. `SimConfigLoader` only finds `game.properties` and logs what
  `SimConfig.parse` says was wrong with it; the shipped value stands for each.
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
- `ScenarioPlay` - what the scenario screen plays through: `ScenarioSession` offline, `OnlineSession`
  online (ADR 0012). `OnlineSession` is this machine's place in a session: hosting starts a `Sequencer`
  on its own thread and joins it over `127.0.0.1`; joining connects through a `ConditionedTransport`
  whose lag the playtest steps. Input arrives by local seat and the session says which slot each seat
  plays; `local(slot)` is what the camera frames and whom the console's action prompt is for (G0038).
  `ScenarioWorlds` builds a session's worlds from the host's shipped config.
- `LaunchOptions` / `PadPolicy` / `ChosenGamepads` - the command line: which copy of the game this is,
  and which pads it reads.
- `JoinScreen` / `JoinChoices` - the join screen: sessions heard on the network, and a room code or an
  address typed in; what Enter joins is `JoinChoices`, tested. A hosting `OnlineSession` holds an
  `Invitation`: the beacon, and the line its overlay reads out to a friend.
- `MenuScreen` / `ScenarioScreen` - the scenario list, and one running scenario with the alpha-phase
  harness. Thin by design: they read input, hand commands to the play, and draw. `LocalAim` is this
  frame's local aim, which `WorldRenderer` draws local commanders facing - the cosmetic lead.
- `Hud` / `DebugOverlay` - text in screen space, on their own pixel-unit viewport. The font is
  libGDX's built-in one, so the harness needs no art at all.
- `InputReader` - the only class that touches a keyboard or mouse for the *simulation*. Produces a
  `CommanderInput` per frame.
- `CameraDirector` / `ViewControls` - where the view is and how far out it sits, and the binding that
  swaps it. The simulation marks entities with `Focus` and stops there; everything about how the view
  behaves is here, unit-tested with no GL context. Centre and zoom are decided together, because
  framing several targets is one decision - which is why local co-op costs nothing but a second
  `Focus`.
  **Order within a frame matters**: input is read first, so the mouse is interpreted against the
  camera as it was when the frame the player reacted to was drawn; the camera moves after the
  simulation step and before drawing. Look-ahead follows movement rather than aim, because aim is
  unprojected through this camera and leaning toward it would chase its own tail.
- `WorldRenderer` - the placeholder renderer: every entity is a coloured circle. It reads each
  entity's `Appearance` and asks nothing else about it (G0003). Replaced by a sprite renderer once
  the atlas exists; the contract stays the same, which is that the simulation is never asked how to
  draw itself.
- `TerrainRenderer` / `TerrainPalette` - the ground, drawn under everything else. `TerrainPalette` is
  the terrain half of G0003 and the exact counterpart of `Palette`: the map says `ORGANIC`, only the
  client knows that means green. Regions of the same terrain are shaded slightly apart from their id
  alone, deterministically, so a level reads as places without the map holding any visual data. The
  renderer culls to the visible rectangle and merges runs of identical tiles into one quad, which is
  what makes a map larger than the view cost the same as one that fits.
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

The simulation works in **world units** and knows nothing about pixels. The screen is a letterbox
(`ScreenLayout`, module `ui`): a top bar, the **view** and a bottom bar, on a UI canvas scaled by a
whole number. `graphic.virtualHeight` is how many world units fill the view top to bottom; its width
follows the view's shape on this screen (`LetterboxViewport`), kept inside `graphic.view.aspectMin`
.. `aspectMax`. No code outside the viewport setup converts between world units and pixels.

There is a third space, **tile coordinates**, and exactly one class converts to it: `TileMap`. World
units are the currency everywhere else - a tile size is a number a map was built with, not something
any system multiplies by.

**The arena is the map's own extent.** `SimConfig.MapConfig` says how many tiles of what size a level
defaults to; once a `TileMap` exists it is the single answer to how big the level is, and nothing
holds a second copy that could disagree.

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
