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
| Rabbits are spawned by a scenario and never replaced | Whatever a level does about them - breeding, a population, or nothing at all. The creature is designed; where they come from is an open question in `README.md`. |
| A body in its death throes is a frozen circle in a darker colour | The dying animation. The window is real and configured per unit kind (`*.deathThroesSeconds`); only what plays in it is missing. |
| Two units in a clinch are two circles touching, both drawn the same | The grapple animation, which resolves into one of them dying. `Appearance.State.GRAPPLING` is the hook and is already published for both parties for the whole lock. |
| Nothing reacts to a death except respawning and the killer's lock | More death reactions, as they are designed. The machinery is there: mark `Dead`, add a component, add a small system. |
| Rock is laid by hand in `Warren` and scattered blindly in `Skirmish` | Generated fragments. The terrain and the solver are real; what decides *where* rock goes is not designed yet. |
| **Frozen troopers.** An inert body on the field that an alien eats and the commander rescues. It is a pickup, not a trooper - it does not follow, cannot be commanded and cannot die. | Real troopers, which now exist. Turning a rescued one into a member of the squad is buildable and is somebody's next session; **nothing about the pickup answers how the squad works**, and it still does not. |
| `Level.pendingReinforcements` counts rescued troopers and nothing reads it | The squad exists now, so this is finally connectable - a rescued trooper becoming a real one. Still nothing reads it. |
| Squad orders are given on keys `1`-`4` | A button and a radial menu. The verbs and who hears them are designed (G0017); the input is not. |
| A trooper under orders wears a ring | Whatever the art does with it. `Appearance.ordered` is the hook and is already published. |
| A control point is a dark disc that fills up, drawn under everything standing on it | Whatever a control point looks like. The dwell, the reset and the radius are real, and the fill rides the same `charge` channel as everything else that fills up. |
| A level's objectives are met and nothing happens | The level lifecycle. What a completed level *does* is an open question in `README.md`, and inventing an answer inside a system would settle it by accident. |
| A scenario names its control points by hand, and the spawn area is a config value | Generated fragments saying where their own entrances and objectives are. Same standing as hand-laid rock. |
| Troopers roam to seeded random points when nothing is left to take | Something that actually searches - a sweep, a last-known-position, a patrol route. Roaming is enough to see the squad clear a map and is not a decision about how they search. |
| A unit that is filling up is drawn as a brighter core inside its circle | Whatever the art does with it. `Appearance.charge` is the hook and is already published as a fraction, so the renderer decides and the simulation never learns how it is shown. |
| **Grenades are unlimited.** No count, no pickup, no cost but the cooldown. | Whatever ammunition turns out to be. The throw itself is designed (G0019); how many you have is not, and a count is one more number in `Thrower` rather than a change to any of it. |
| A blast is a coloured circle with a core that collapses as it fades | The explosion animation. The radius, the timing, the one lethal tick and the shake are all real; only what plays inside the circle is missing. |
| A barrel is a small orange-red circle | Whatever a barrel looks like. The colour is deliberately off the grunt's red - a field where some red circles chase you and the others kill you when you shoot them is exactly the confusion pillar 5 exists to prevent. |
| Barrels are placed by hand in `Demolition` and nowhere else | Whatever a level does about them. Same standing as hand-laid rock: the barrel is designed, where barrels come from is not. |
| The commander's gun is named only in the F1 overlay | A real HUD. What he is holding is visible in what he fires, which is enough to play; it is not enough to be told at a glance. |

## Known limits, accepted on purpose

- **Avoidance looks at everything on the field, once per unit.** There is no broadphase for it: the
  collision grid is built after the AI has run, so a unit scores every other body every tick. At a few
  dozen entities that is free. Past a couple of hundred it wants either a second grid or the collision
  grid moved earlier in the tick, and it will show up as frame time rather than as a bug.
- **A provoked unit walks straight at what provoked it.** No flow field: each one is cross about a
  different place, so routing them properly would cost a grid pass per unit per tick, which is the
  per-unit cost a flow field exists to avoid. One provoked from behind a wall walks into the wall and
  mills about. At the ranges a fight happens at that is rare; it stops being acceptable the moment
  something can provoke from across the level.
- **A sight line is blocked by rock and by nothing else.** Obstacles, corpses and other units do not
  hide anything, because they are entities and the test is a pure function of terrain (ADR 0009).
  Blocking on them needs the collision grid, which is built *after* the AI has run. Same asymmetry as
  the routing limit below, and the same reasoning.
- **A route plans around rock and steers around everything else.** The flow field is built from the
  map alone, so obstacles, corpses and other units never enter a route - avoidance handles them, which
  means a pack still funnels through a doorway rather than queueing for it.
- **Crowd pressure pushes units into obstacles.** The constraint solver satisfies arena containment
  absolutely and obstacle separation best-effort; a crowd driven into a dead end is unsatisfiable and
  the obstacle is what gives. Raising `sim.collision.passes` reduces the squeeze without removing it.
  Fixing it properly means a different model, not more passes.
- **Rendering lags the simulation by up to one tick.** That is what interpolation costs, and it is
  the standard trade.

## What this phase does not decide

All seven open questions in `README.md` are still open. If a system needs one of them answered,
that is a question for Mateusz, not a decision to make in passing.
