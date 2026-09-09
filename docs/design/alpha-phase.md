# The alpha phase

The POC proved the loop turns: a commander moves, shoots, and collides. This phase builds the
systems the game will actually run on, designed to be built on rather than unpicked.

Two rules separate it from the POC:

- **No shortcuts in logic.** A stand-in is allowed only while it is named in *Still scaffolding*
  below, and removing it is somebody's next session.
- **Visuals stay minimal.** Coloured circles (G0003). Placeholder art is not on the roadmap; when
  sprites arrive they replace one class in the client.

## How a session goes

The system is the deliverable; a scenario is optional and comes second. A session may carry as many
systems as the change genuinely needs - mechanics that only make sense against each other, like a
leap and the strike that answers it, are worth building together, because half of that pair cannot be
judged by feel on its own. Split the work into stages that each stand up on their own and can be
stopped after, rather than splitting it across sessions by count.

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
5. Override `map(SimConfig, Randomness)` if the scenario needs particular ground. The default is open
   ground the size `sim.map.*` asks for, partitioned into regions. **The map is built before the
   simulation**, because its extent is the arena.
6. `populate(Simulation)` spawns the world. Spawn the commander first, and take the arena size from
   `simulation.map()` rather than from config.

`Scenarios.world(scenario, config)` assembles all of that and is the only place a world is built -
use it in tests too rather than constructing a `Simulation` and populating it by hand.

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
| Enemies walk straight at the commander and bump into obstacles, then leap the last of the gap | Whatever pathing the squad work needs, if any. The leap is designed; the walking under it is not. |
| A body in its death throes is a frozen circle in a darker colour | The dying animation. The window is real and configured per unit kind (`*.deathThroesSeconds`); only what plays in it is missing. |
| Two units in a clinch are two circles touching, both drawn the same | The grapple animation, which resolves into one of them dying. `Appearance.State.GRAPPLING` is the hook and is already published for both parties for the whole lock. |
| Nothing reacts to a death except respawning and the killer's lock | More death reactions, as they are designed. The machinery is there: mark `Dead`, add a component, add a small system. |
| Rock is laid by hand in `Warren` and scattered blindly in `Skirmish` | Generated fragments. The terrain and the solver are real; what decides *where* rock goes is not designed yet. |
| **Frozen troopers.** An inert body on the field that an alien eats and the commander rescues. It is a pickup, not a trooper - it does not follow, cannot be commanded and cannot die. | Real troopers. It exists so evolution-by-devouring can be built and played before the squad does, and **nothing about it answers how the squad works**. |
| `Level.pendingReinforcements` counts rescued troopers and nothing reads it | The squad. The number is there so a level-scope grant has somewhere real to land and the path can be tested end to end; it changes nothing in play. |
| A unit that is filling up is drawn as a brighter core inside its circle | Whatever the art does with it. `Appearance.charge` is the hook and is already published as a fraction, so the renderer decides and the simulation never learns how it is shown. |
| The commander's gun is named only in the F1 overlay | A real HUD. What he is holding is visible in what he fires, which is enough to play; it is not enough to be told at a glance. |

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
