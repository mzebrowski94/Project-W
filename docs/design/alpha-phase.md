# The alpha phase

The POC proved the loop turns: a commander moves, shoots, and collides. This phase builds the
systems the game will actually run on, one per session, designed to be built on rather than unpicked.

Two rules separate it from the POC:

- **No shortcuts in logic.** A stand-in is allowed only while it is named in *Still scaffolding*
  below, and removing it is somebody's next session.
- **Visuals stay minimal.** Coloured circles (G0003). Placeholder art is not on the roadmap; when
  sprites arrive they replace one class in the client.

## How a session goes

One system per session. The system is the deliverable; a scenario is optional and comes second.

1. **Design it** - the `feature` skill. Check it against the invariants in `README.md`, ask rather
   than invent, and write down whatever it settles.
2. **Build it** - the `new-system` skill. Tests first, in the order `CLAUDE.md` sets out.
3. **Demonstrate it** - *if Mateusz asks*, add a scenario that shows the system working, so he can
   run it and judge it by feel rather than by test names.

Step 3 is not automatic. Ask before building one; a system that is fully visible in an existing
scenario does not need its own.

## Adding a scenario

A scenario is one arena, built to make a system visible. Everything needed to add one:

1. Write a class implementing `Scenario` in `sim/.../scenario/`, package-private.
2. Add it to the list in `Scenarios.ALL`. **That list is the whole registry** - a scenario missing
   from the menu is a scenario missing from that line.
3. `name()` is what the menu shows. `description()` is one line saying *which system this exercises
   and what to look at* - "sixty enemies spawned overlapping, watch them untangle", not "a fight".
4. Override `configure(SimConfig)` to vary tunables - a crowd, an empty arena, a faster weapon -
   using the `with(...)` overloads on `SimConfig`. Do not edit `game.properties` for a scenario.
5. `populate(Simulation)` spawns the world. Spawn the commander first.

`ScenariosTest` applies to every registered scenario automatically: each must describe itself, spawn
exactly one commander, and survive 300 ticks. `SimulationDeterminismTest` replays each one twice and
requires identical results, so a scenario cannot introduce non-reproducible spawning.

**Scenarios are content, not design.** Numbers in one are chosen to make a system visible, not to
balance the game, and a scenario may be unfair or absurd if that is what shows the system working.
Nothing in a scenario answers an open question in `README.md`.

## The harness

Dev controls, available in any scenario. **These are not game bindings** - the real input map is in
`README.md`, and these will be removed or moved when the game gets a real pause and menu.

| Key | Does |
|---|---|
| `P` | Pause and unpause |
| `.` | Advance exactly one tick, while paused |
| `R` | Restart the scenario from its first tick |
| `G` | Make the commander invulnerable, or stop. A dev tool - pillar 2 says he can be killed |
| `F1` | Toggle the stats overlay |
| `Esc` | Back to the scenario menu |

The overlay shows frame rate, tick count and a count of each entity kind. Entity counts are the
cheapest leak detector there is: projectiles that never despawn or enemies that never die show up
there long before they are visible on screen.

The harness itself is tested. `ScenarioSession` holds what pause, step and restart actually do, and
`HarnessControls` holds which key means what; both are covered without a GL context. If you change a
binding or add a command, change them there - the screen only draws.

Restarting builds a whole new `Simulation` rather than emptying the old one, so a restarted run is
identical to a cold start by construction.

## Still scaffolding

Behaviour that looks like design and is not. Each line names what replaces it.

| Scaffolding | Replaced by |
|---|---|
| The commander always respawns | The source game's rule - respawn only while a trooper lives. Needs the squad. |
| Enemies walk straight at the commander and bump into obstacles | Whatever pathing the squad work needs, if any. |
| Nothing reacts to a death except respawning | More death reactions, as they are designed. The machinery is there: mark `Dead`, add a component, add a small system. |
| `Placement` silently places fewer circles than asked when the arena is full | A real level format, which must report the shortfall instead of swallowing it. |

## Known limits, accepted on purpose

- **Crowd pressure pushes units into obstacles.** The constraint solver satisfies arena containment
  absolutely and obstacle separation best-effort; a crowd driven into a dead end is unsatisfiable and
  the obstacle is what gives. Raising `sim.collision.passes` reduces the squeeze without removing it.
  Fixing it properly means a different model, not more passes.
- **Rendering lags the simulation by up to one tick.** That is what interpolation costs, and it is
  the standard trade.

## What this phase does not decide

All seven open questions in `README.md` are still open. If a system needs one of them answered,
that is a question for Mateusz, not a decision to make in passing.
