# The alpha phase

The POC proved the loop turns: a commander moves, shoots, and collides. This phase builds the
systems the game will actually run on, designed to be built on rather than unpicked.

Two rules separate it from the POC:

- **No shortcuts in logic.** A stand-in is allowed only while it is named in *Still scaffolding*
  below, and removing it is somebody's next session.
- **Visuals stay minimal.** Coloured circles (G0003), except the commander, whose puppet, portrait
  and the UI skin are the first atlas (G0043). Any other look gets art only with a record of its own;
  a missing puppet falls back to the circle.

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
| `P` | Pause and unpause, with no menu: the paused banner shows and `.` single-steps a world you can see |
| `.` | Advance exactly one tick, while paused |
| `R` | Restart the scenario from its first tick |
| `G` | Make the commander invulnerable, or stop. A dev tool - pillar 2 says he can be killed |
| `F1` | Step the stats overlay: its panel in the top bar's right corner, then every line over the view's corner, then off |
| `F2` | Online: draw your own commanders facing your aim at once, or as the world has them (the cosmetic lead, on by default) |
| `F3` | Online, a joining copy: step the simulated lag through `net.conditions.latencySteps` |
| `Esc` | Not the harness's any more: in a level it opens the pause menu, whose LEAVE TO MENU asks first |

Online, pause is anybody's and pauses every machine; restart and `G` are the host's, and do nothing
from anyone else; a single step is offline only (G0038).

The overlay shows frame rate, tick count and a count of each entity kind. Entity counts are the
cheapest leak detector there is: projectiles that never despawn or enemies that never die show up
there long before they are visible on screen.

The harness itself is tested. `ScenarioSession` holds what pause, step and restart actually do, and
`HarnessControls` holds which key means what; both are covered without a GL context. If you change a
binding or add a command, change them there - the screen only draws.

Restarting builds a whole new `Simulation` rather than emptying the old one, so a restarted run is
identical to a cold start by construction.

## Playing online

From the main menu, **PLAY ONLINE → HOST GAME** hosts the chosen scenario and **JOIN GAME** opens the join page, which lists
the games heard on this network - LAN or Hamachi - and takes a room code or an address typed in. The
joining copy plays the host's scenario, tuning and seed, whatever its own files say (ADR 0012). In game,
the overlay's line under the last order says where you are connected, your input delay in ticks and
milliseconds, the simulated lag, and `WAITING FOR THE HOST` when ticks stop arriving; a hosting copy's
next line is its room code and the addresses a friend can type; its pause menu shows them too.

Launch options, for more than one copy on a PC:

| Option | Does |
|---|---|
| `--instance=N` | Which copy this is: its window's title and place (side by side), its connection log's name, and its pads |
| `--pads=all\|none\|0,2` | Which pads this copy reads. Copy 0 reads all and every other copy none, because SDL hands every pad to every copy |

How to start two copies is in `docs/workflow.md`.

### The latency playtest

The gate before the relay (ADR 0012; it was to come before the join screen, which went first by
decision): does the input delay lockstep costs still feel like
pillar 1? Run it with two copies on one PC and play the **joining** one - it is the one with a round
trip to cross. `net.conditions.latencySteps` are one-way, so the steps `0, 25, 50, 100` are round trips
of 0, 50, 100 and 200 ms; 25 is about what the same region costs over the internet (D4), 50 is a bad
evening.

1. Host `Skirmish` in copy 0; join from copy 1 and play it, with the keyboard in its window or a pad
   given to it (`--pads=all` on copy 1, `--pads=none` on copy 0).
2. At each lag step (`F3`), play a couple of minutes: strafe while firing, turn and shoot at something
   fast, dodge a leap. Then press `F2` and play the same again without the cosmetic lead.
3. Note, per step, the input delay the overlay shows and a verdict for **moving**, **aiming** and
   **shooting**: fine, noticeable, or not acceptable. Note anything that jumps or stutters.
4. Repeat on `Adventure` at the step that felt worst but still playable.

**Go** to the relay if 25 ms each way is fine or merely noticeable with the cosmetic lead on.
**Stop** if it is not acceptable there: rollback (deferred in ADR 0012) comes before the relay. Either way,
keep the connection logs from `ai/logs/net/` - each `cat=stats` line of the host's shows
`sealLateMostMs`, how late the sequencer's thread woke on this machine, which is the one timing no
test here can measure.

**Run 2026-09-24**, two and three copies: fine up to 50 ms each way; walking noticeably late at 100 ms
each way (234 ms of input delay), not critical. Go, with rollback left until the relay gives real round
trips (ADR 0012). It also found a copy that stalled on its own playing on with an inflated input delay
for 25 seconds, and players left in a frozen world when the host quit - both fixed.

## Still scaffolding

Behaviour that looks like design and is not. Each line names what replaces it.

| Scaffolding | Replaced by |
|---|---|
| The commander always respawns | The source game's rule - respawn only while a trooper lives. Needs the squad. |
| Rabbits are spawned by a scenario and never replaced | Whatever a level does about them - breeding, a population, or nothing at all. The creature is designed; where they come from is an open question in `README.md`. |
| A body in its death throes is a frozen circle in a darker colour | The dying animation. The window is real and configured per unit kind (`*.deathThroesSeconds`); only what plays in it is missing. |
| Two units in a clinch are two circles touching, both drawn the same | The grapple animation, which resolves into one of them dying. `Appearance.State.GRAPPLING` is the hook and is already published for both parties for the whole lock. |
| Nothing reacts to a death except respawning and the killer's lock | More death reactions, as they are designed. The machinery is there: mark `Dead`, add a component, add a small system. |
| Rock is laid by hand in `Defend` and scattered blindly in `Skirmish` | Generated fragments. The terrain and the solver are real; what decides *where* rock goes is not designed yet. |
| A post is a dark green disc, and a level's waves are a list written out by hand in `Defend` | Whatever a level does about both. The post, the march, the schedule and the objective are all real; where a post goes and what a level throws at it is content, and generated fragments will eventually say. |
| Every crate is the same crate, and a point is worth two or three of them whatever else is true of it | Whatever a level does about reinforcements - a crate worth more on a harder biome, a kind that arrives under fire, points that keep producing. What a crate is worth is already a number (`Grant.Reinforce`) and the placement is one method, so all of that is content rather than machinery. |
| Squad orders are given on keys `1`-`4`, and on the d-pad | A button and a radial menu, on both devices. The verbs and who hears them are designed (G0017); the input is not, and the d-pad is the same stand-in wearing a different shape. |
| **A squad holding a point it has taken looks exactly like a squad capturing one** - both are men standing still and watching | Whatever says "we are holding, waiting for you". The rule is settled (G0029) and the posture is right; what is missing is the tell that the bar is full and the squad is now waiting on the commander rather than on the clock. Until it exists a player learns the difference by walking over and pressing forward. Since G0040 the squad also leaves by itself to follow a commander who is ahead, after a hold nothing on screen counts down. |
| Every AI chance is one number for the whole game, with one strain override | Whatever tuning turns out to need. The index is real and complete - `AiChance` names every occasional behaviour and `Odds` is the only caller of the dice - but nothing yet varies a chance by difficulty, by level, or by how a fight is going. |
| **The available action is announced in the console** when it changes. | An on-screen prompt beside the commander. The decision is already made in the simulation and published on `Action.available` (G0021); what is missing is a label, not a system. The console is not where a player looks mid-fight, so pillar 5 is only half-served until it moves. |
| A casualty is a brightened circle with his bleed-out on the `charge` ring | The going-down and getting-up animations. The window, the clock, the stabilise and the hauling are all real; what plays in them is not. The colour is deliberately the opposite shift from a corpse - the two states are the most expensive in the game to confuse. |
| The stabilise shows as a green `charge` filling inside whoever is doing it | Whatever the art does with it. The hook is `Action.progress` and `Appearance.chargeLook`, both published, so the renderer decides. The colour is the one thing here that is a decision rather than a stand-in: every other charge in the game is a clock the player is losing and this is the one he is winning. |
| A trooper that has decided to fetch somebody just walks there and stands over him | Whatever a man carrying out a rescue looks like. The ranking, the roll and the work are all real; it has no call, no posture and no way of telling the player it is going. |
| A runner is an orange circle, slightly smaller than a grunt's | Whatever the fast strain looks like. The weave, the dodge and the dash are all real. The colour is deliberately off both the grunt's red and the spitter's purple: what will not hold still has to be tellable at a glance from what is walking straight at you. |
| A spitter is a purple circle and its spit a purple dot | Whatever a shooting alien looks like. The halt, the cone and the range are real. The colour is deliberately off both reds: what is coming for you and what is shooting at you need different answers, and a field where they look alike is the confusion pillar 5 exists to prevent. |
| Spitters, their barrels and the cover they stand behind are placed by hand in `Casualty` | Whatever a level does about them. Same standing as hand-laid rock and hand-placed barrels. |
| A charged spitter is the same purple circle inside an orange-red ring | Whatever a creature carrying an explosive looks like. The swallow, the gun and the blast are all real, and the ring is the colour of the thing it swallowed. `Appearance.charged` is the hook and is already published, so the renderer decides. |
| A trooper under orders wears a ring | Whatever the art does with it. `Appearance.ordered` is the hook and is already published. |
| A control point is a dark disc that fills up, drawn under everything standing on it | Whatever a control point looks like. The dwell, the reset and the radius are real, and the fill rides the same `charge` channel as everything else that fills up. |
| A level's objectives are met and nothing happens | The level lifecycle. What a completed level *does* is an open question in `README.md`, and inventing an answer inside a system would settle it by accident. |
| A scenario names its control points by hand, and the spawn area is a config value | Generated fragments saying where their own entrances and objectives are. Same standing as hand-laid rock. |
| **Where you are connected, and your input delay, are a line in the stats overlay** | Whatever tells a player the link is slow without reading numbers (pillar 5). The numbers are real and published by `OnlineSession`; what is missing is a tell. |
| Troopers roam to seeded random points when nothing is left to take | Something that actually searches - a sweep, a last-known-position, a patrol route. Roaming is enough to see the squad clear a map and is not a decision about how they search. |
| A unit that is filling up is drawn as a brighter core inside its circle | Whatever the art does with it. `Appearance.charge` is the hook and is already published as a fraction, so the renderer decides and the simulation never learns how it is shown. |
| **Grenades are unlimited.** No count, no pickup, no cost but the cooldown. | Whatever ammunition turns out to be. The throw itself is designed (G0019); how many you have is not, and a count is one more number in `Thrower` rather than a change to any of it. |
| **Fire is a handful of dull orange-red circles that swell, sit and collapse**, with a brightened core that shrinks as each one burns down | Whatever burning ground looks like. The circles, the lives, the ramps and the side are all real and so is the rule that the body is the drawing; what is missing is flame. The colour is deliberately off the blast's amber and pulled towards the barrel's orange-red - the flash lasts a quarter of a second and this is there for ten, and a player who reads one as the other walks into it. |
| **A mine is a small dark green dot, and its fuse a near-black core that flashes faster as it runs out** | Whatever a mine looks like. The trigger, the fuse, the latch and the chain are all real. The near-black is the one thing on the `charge` channel that is darker than what it is drawn on, deliberately: everything else that fills up is good news or a clock, and this is the only one that is about to go off. `Appearance.chargeLook` is the hook and is already published, so the renderer decides. |
| Turrets and mines in the redoubt are placed by hand, and its waves are a list written out by hand in `Adventure` | Whatever a level does about all three. Same standing as hand-laid rock and the `Defend` schedule beside it: the gun, the mine, the post and the schedule are all real, and where a level puts them is content that generated fragments will eventually say. |
| A blast is a coloured circle with a core that collapses as it fades | The explosion animation. The radius, the timing, the one lethal tick and the shake are all real; only what plays inside the circle is missing. |
| **A turret is a blue square, brighter when somebody is on it, and the man on it is not drawn at all.** | Whatever a gun emplacement and its crew look like. The square is the only shape in the game that is not a circle, and it is deliberate: everything alive is round and the one piece of equipment is not. `Appearance.State.CREWED` and `ABOARD` are the hooks and are already published, so the renderer decides. |
| A gun's heat is an arc of straight pieces drawn over whatever is holding it, amber and then red | Whatever the art does with it. The bar, the stop and the two colours are real and published on `Appearance.heat` and `.overheated`; the arc is drawn in pieces only because `ShapeRenderer`'s own arc is a pie slice. |
| Turrets are placed by hand in scenarios | Whatever a level does about them. Same standing as hand-laid rock and hand-placed barrels: the turret is designed, where turrets come from is not. |
| A barrel is a small orange-red circle | Whatever a barrel looks like. The colour is deliberately off the grunt's red - a field where some red circles chase you and the others kill you when you shoot them is exactly the confusion pillar 5 exists to prevent. |
| Barrels are placed by hand in `Demolition` and `Casualty` and nowhere else | Whatever a level does about them. Same standing as hand-laid rock: the barrel is designed, where barrels come from is not. |
| **Each player's commander is a circle in a colour of its own** - cyan, amber, green, pink, and four provisional ones for the players a game on several machines can hold - and nothing else tells them apart | Whatever eight commanders look like. The colour is doing the whole job of pillar 5 here, which is enough to play and not enough to be one of four men in a fight: no number, no name, no marker over your own. `Appearance.variant` is the hook and is already published, so the renderer decides. |
| **The leash is invisible.** A commander at the end of it simply stops walking outwards, with nothing on screen saying why | Whatever tells a player he has reached the edge of the fight. The rule itself is settled for the alpha and listed as an open question in `README.md`; what is certainly missing either way is the tell, and a wall with no tell is the one way this reads as a bug. |
| **The action prompt is one console, for four players** | The on-screen prompt it is waiting for. With one player the console was half-served; with four, three of them cannot see a prompt addressed to them at all. The decision is per player already - each player's action is in the readout, and `ActionPrompts` says it when it changes - so what is missing is still a label rather than a system. |

## Known limits, accepted on purpose

- **Every alien scores every living thing on the player's side, once per tick,** to decide who it is
  coming for (G0026). That is a commander, a handful of men and whatever guns a level put down - a
  small list against a big one rather than a pair loop, and cheaper than the avoidance scan below it.
  It wants the same answer as that one if either ever needs a broadphase.
- **Avoidance looks at everything on the field, once per unit.** There is no broadphase for it: the
  collision grid is built after the AI has run, so a unit scores every other body every tick. At a few
  dozen entities that is free. Past a couple of hundred it wants either a second grid or the collision
  grid moved earlier in the tick, and it will show up as frame time rather than as a bug. **`Defend` and the `Adventure`'s redoubt are
  the levels that will find out**: each sends about a hundred aliens in a dozen-odd waves, and if the
  squad is losing, sixty of them can be standing at once. Watch the frame time on `F1` there before
  reaching for a profiler anywhere else.
- **A provoked unit walks straight at what provoked it, and so does anything hunting somebody who is
  not the commander.** No flow field for either: routes are one grid pass per goal, and each of those
  units has a goal of its own - the per-unit cost a flow field exists to avoid. One that sets off from
  behind a wall walks into the wall and mills about. At the ranges a fight happens at that is rare; it
  stops being acceptable the moment something can be summoned from across the level.
- **Every mine scores every alien on the field, once per tick,** to decide whether anything has come
  near enough. A short list against a long one rather than a pair loop, the same trade
  `ControlPointSystem` already makes for the same reason - but the redoubt lays twenty-four of them
  and can have sixty aliens standing at once, so it is the second thing on this page that level will
  find out about, after the avoidance scan below. It wants the same answer as that one if either ever
  needs a broadphase.
- **A sight line is blocked by rock and by nothing else.** Obstacles, corpses and other units do not
  hide anything, because they are entities and the test is a pure function of terrain (ADR 0009).
  Blocking on them needs the collision grid, which is built *after* the AI has run. Same asymmetry as
  the routing limit below, and the same reasoning.
- **A route plans around rock and steers around everything else.** The flow field is built from the
  map alone, so obstacles, corpses and other units never enter a route - avoidance handles them, which
  means a pack still funnels through a doorway rather than queueing for it.
- **Where a thing that never moves stands nearly flush against rock, the route and steering are blind
  together** - the route knows rock, steering knows entities - and a unit walks into the slot. A trooper
  detours out of it (G0042); an alien does not. The Adventure's boulders are laid off rock now; **Duel,
  Expanse and Skirmish** still scatter obstacles and units from one scatter with no rock margin, so the
  slot can still happen there. Stamping immovable things into the routes is the deferred fix.
- **Crowd pressure pushes units into obstacles.** The constraint solver satisfies arena containment
  absolutely and obstacle separation best-effort; a crowd driven into a dead end is unsatisfiable and
  the obstacle is what gives. Raising `sim.collision.passes` reduces the squeeze without removing it.
  Fixing it properly means a different model, not more passes.
- **Rendering lags the simulation by up to one tick.** That is what interpolation costs, and it is
  the standard trade.

## What this phase does not decide

All seven open questions in `README.md` are still open. If a system needs one of them answered,
that is a question for Mateusz, not a decision to make in passing.
