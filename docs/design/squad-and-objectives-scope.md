# Squad, objectives and orders — scoped work

**Status: done, 2026-09-10. Safe to delete once Mateusz has read it.** All nine stages landed and
what they settled has moved where it belongs - invariants and the input map into `README.md`, terms
into `glossary.md`, scaffolding and known limits into `alpha-phase.md`, and the four decisions into
`decisions/` as G0015, G0016 and G0017. Nothing below is load-bearing any more; it is kept only as
the record of *why* the build order was what it was, which is the one thing the other files do not
carry.

**This doc expires when the squad ships.** It carries the plan for one run of work: AI troopers, map
objectives, and the commander's orders. What it settles moves into `README.md`'s invariants table,
the input map and `docs/design/decisions/` as it lands; what is left here at the end is deleted.

Written 2026-09-10, before any code, from the three goals Mateusz set out. Four design forks were put
to him and answered; they are recorded in *Decisions taken* below.

## What this run settles

Two of the seven open questions in `README.md` are answered by this work. Both have been open since
the design surface was written, and both were deliberately left alone by the POC and by the alpha
phase so far.

| Open question | Answer |
|---|---|
| What ends a level — an objective, an extraction point, elimination, or a timer? | **An objective, and a level may name more than one.** Two exist: clear the map of enemies, and capture every control point. A level names a set and requires **all** of them. |
| How does the player command the squad, and on what input? | **Positional orders.** Four verbs — follow, push forward, stop, cancel — reaching only the troopers near the commander, not the whole squad. Keys `1`–`4` for now. |

Neither answer closes the questions around them: what happens *after* the objectives are met is still
open (alpha does nothing), and whether the squad persists between levels is untouched.

## Invariant check

The `feature` skill requires every invariant and pillar the change touches to be named. Three
outcomes: consistent, conflicting, or the design does not say.

### Consistent, no change needed

| Row | Why it holds |
|---|---|
| Systems that drive a unit write a desired velocity; one system commits it (G0010) | Every trooper behaviour is a driver writing `Steering`. No exceptions asked for. |
| What a unit is doing is a field, never a component added or removed (G0010) | A trooper's mode is an enum field. See *G0010's revisit trigger* below — it fires, and the answer is still no tree. |
| Damage only ever crosses faction lines | Troopers are on the commander's side; nothing new crosses. |
| Every projectile / every strike is a simulated entity | Troopers fire and swing through the paths that already exist. No hitscan is introduced. |
| A blow that kills holds both parties (G0013/G0014) | A trooper's killing sweep on a committed leap earns a clinch, exactly as the commander's does. This is existing behaviour newly applying to a new unit — worth watching in play, not changing. |
| Nothing is spawned inside rock, or anywhere the commander cannot reach | `ScenariosTest` polices troopers and control points automatically the moment they are spawned by a scenario. |
| Everything drawn carries a `PreviousPosition` seeded at its spawn point | Troopers and control points spawn through `Spawner` like everything else. |
| Every run is reproducible (pillar 6) | The one new random draw — the rare hand strike, 1.e — takes its own stream off the level seed. |
| The commander is a unit, not a cursor (pillar 2) | Orders are issued *from where he is standing* and reach only what is near him. This strengthens the pillar rather than testing it. |
| The squad is the resource (pillar 3) | Nothing is bought, equipped or selected. An order is a verb, not a loadout. |

### Conflicts found

**1. Detection is range-only (G0011) vs. a trooper's vision cone (1.c).**
G0011 fixes detection as distance alone, with no line-of-sight test anywhere in perception. Goal 1.c
asks for a trooper that engages what is in front of it, at close range. G0011 scopes itself to
*noticing the commander* and says outright that "a strain whose whole idea is that it only reacts to
what it can see is a strain with its own rule, not a change to this one" — so a trooper rule is
permitted. Mateusz chose cone **plus** line of sight, which does reopen G0011's third objection.
Resolved by architecture, not by exception — see *Detection is a list of tests* below.

**2. Aggro is a boolean meaning "hunting the commander" (1.f).**
Four invariant rows are phrased around the commander: he outranks food absolutely, him going down
releases everything hunting him, a strain that never loses interest hunts him forever, and a unit
that has not noticed *him* is drawn differently. Goal 1.f needs an enemy to aggro onto whoever shot
it. Resolved by latching a **place** rather than a unit — see *Decisions taken*.

**3. G0010's revisit trigger fires, and the answer is still no behaviour tree.**
G0010 says to revisit when a unit needs "a sequence it must run to completion, an interruption it
must resume from, or a priority ordering somebody wants to tune". Goal 3.e is exactly that shape:
orders outrank objectives but not combat, and are carried out once the fight ends. It is still not a
tree, because the ordering is **fixed in code and not tunable**, the modes stay mutually exclusive
once chosen, and resuming costs nothing — the objective behaviour is a pure function of where the
trooper is standing and which points are uncaptured, so there is no progress to save and restore. One
system picks the mode each tick; every driver downstream reads the answer. If a fourth axis of
priority arrives, or if somebody wants to reorder these without recompiling, *that* is the trigger.

**4. "Spawn tile" vs. nothing is quantised to the grid (G0009).**
Goal 1.a asks for a spawn tile. G0009 says the grid lays a level out and nothing else, and nothing
stands at a tile centre. Consistent as long as a spawn area is *a rectangle of tiles that units are
jittered inside*, which is precisely the tile-anchored scatter pattern already built and enforced by
`ScatterTest.standsThingsInsideTheirTilesRatherThanAtTheCentre`. No new concept; reuse `Scatter`.

### The design did not say, and now does

- **Capture progress must be published as a fraction, not left as a number only the simulation
  knows.** Pillar 5, and the `Appearance.charge` hook already exists for exactly this — the glossary
  defines charge as "how full something is [...] and later anything else that fills up. One tell,
  drawn the same way wherever it appears." A control point filling up is that, so it uses charge and
  the renderer needs no new idea.
- **Nearest control point means nearest *by route*, not by straight line** (2.e). A point twenty
  units away through a wall is not nearer than one forty units away down a corridor, and Euclidean
  distance would send a squad to stand against rock. The flow field already computes a distance grid
  per goal, so the honest answer is also the cheap one.

## Decisions taken

Four forks, answered by Mateusz on 2026-09-10. Each becomes a record in `docs/design/decisions/`.

**Aggro latches a place, not a unit.** An enemy that is hurt latches *where the shot came from* and
goes to fight whatever it finds there. No entity reference survives a tick, no id registry is needed,
and G0010 is untouched. The trade is explicit and accepted: "focus the first attacker until it dies,
then take the next" (1.f as written) becomes **emergent rather than guaranteed** — a second trooper
standing closer to the latched place can pull the enemy off the first. In practice a squad firing
from one position reads the same; a squad spread wide will see enemies switch. Revisit if that reads
as indecision in play.

**Detection is a list of tests a strain declares.** See below.

**Urgency blends route against scanning.** One number, 0..1, between "head straight down the flow
field" and "amble sideways looking around". Walking **speed is not what urgency changes** — that is
deliberate, so that "forward" (3.b), which *does* change speed, stays a distinct and visible thing
rather than a second urgency dial.

**Two deferred invariants get promoted; the third does not.** "Troopers lost during a level stay lost
for that level" and "squad size never exceeds its configured maximum" become enforced now that there
is a squad. **Squad size is set by the scenario**, through a `with…` helper on `SimConfig` like every
other tunable, with the default in `game.properties`. The third row — the commander respawning only
while a trooper lives — stays scaffolding for now: it is the source game's real rule and it is a
genuine change to how the game plays, and this run is already large.

## Detection is a list of tests a strain declares

The maintainable shape, and the reason this is worth doing properly rather than special-casing the
trooper.

`PerceptionDef` already holds two numbers. It gains two more fields, and `PerceptionSystem` runs the
tests each def declares, **cheapest first, short-circuiting**:

| Test | Cost | Declared by |
|---|---|---|
| Range — squared distance against detection range | trivial | everything |
| Arc — dot product against the unit's facing; 360° means no arc | trivial, no trig | troopers |
| Clear line — DDA walk of the tile grid between the two points | real, but reached last | troopers |

An alien grunt declares range only and behaves **exactly as it does today** — the arc is 360° and the
line test is off, so this stage is a refactor with no behaviour change, and the existing suite staying
green is what proves it.

This answers G0011's objections rather than ignoring them:

- *"It turns every wall into a place where standing still is safe."* Still true — for the side it was
  written about. Aliens do not gain a line test, so the player cannot hide behind rock from anything
  hunting him. What changes is that a **trooper** will not shoot through a wall, which is the
  complaint nobody has yet but everybody would have on first sight.
- *"A ray per unit per tick."* No. The line test runs only against candidates that have already
  passed range and arc, which at any real spacing is nearly nothing. Cost is proportional to fights,
  not to unit count.
- *"What a unit will do becomes unreadable."* This is why the facing notch (the last item in the
  goals) is not cosmetic and is built **before** the cone, not after it. A cone the player cannot see
  the direction of is exactly the unreadable case G0011 rejected; a cone with a visible facing is a
  tell the player can stand behind and use.

So this **narrows G0011 rather than contradicting it**, the same way G0007 narrowed G0004: G0011's
constraint stands unchanged for the side it was written about, and its revisit trigger — "when cover
becomes a mechanic the player is asked to use deliberately" — has not fired.

**Where the ray lives: `map`.** A clear-line test between two world points is a pure function of
terrain and geometry, which ADR 0009 puts in `map` alongside `TileMap`. It reads a grid, not an
entity and not a clock, so it stays inside `MapArchitectureTest`'s rules. An integer DDA walk with no
transcendentals keeps determinism. It is testable with no entities at all, and it is immediately
reusable — "does this weapon have a shot" is the same question.

**Rock only, not obstacles or corpses.** Terrain blocks line; entities do not. That keeps the test
pure and in `map`, and it is the same trade the project already accepts and documents for routing
("a route plans around rock and steers around everything else"). It goes in *Known limits* with that
one, in the same words, so the two are recognisably one decision.

## Interdependencies

The goals were given in the order 1, 2, 3. **The dependencies run partly the other way**, and this is
the main finding of the design pass:

- **Goal 2 must precede Goal 1.b.** A trooper cannot advance-while-scanning until there is something
  to advance toward. Control points and the objective set come first; the driver reads them.
- **Facing must precede Goal 1.c.** A cone is an angle *relative to a facing*, so the facing value has
  to exist before the cone can be written — and per G0011 above, the player has to be able to see it
  before the cone is legitimate. The visual asked for last is therefore built nearly first.
- **Goal 1.b partly precedes Goal 2.c.** "Proceed to the next point, or explore freely" is the
  advance driver again with a different goal, so 2.c is wiring, not a new behaviour — but it cannot
  be wired before the driver exists. Build the point and the objective set, then the driver, then
  wire 2.c back into it.
- **Goal 1.f precedes Goal 2.b.** Capturing a point generating aggro is the *same mechanism* as being
  shot generating aggro: something latches a place. Build the mechanism once, on the enemy side where
  it can be tested against the commander alone with no troopers in the world, and 2.b becomes a
  second caller.
- **Goal 3 depends on nearly all of Goal 1 and 2.** 3.e's ordering needs a combat mode and an
  objective mode to both exist before there is anything to order between. Goal 3 is genuinely last,
  and 3.c/3.d are trivial once the modes exist.
- **Squad size from the scenario is needed by stage 1**, since it decides how many troopers spawn.

## Build order

Nine stages. Each stands on its own and the work can stop after any of them.

| # | Stage | Covers | Stands alone because |
|---|---|---|---|
| 1 | Trooper and spawn area | 1.a, squad size | A trooper spawns, is drawn, collides and can be killed. Nothing drives it yet. |
| 2 | Facing, published and drawn | the visual ask | The commander's aim becomes visible immediately. Verifiable by eye before any AI reads it. |
| 3 | Detection as declared tests | groundwork for 1.c | Pure refactor. Aliens declare range only; the suite staying green *is* the test. |
| 4 | Aggro latches a place | 1.f | Enemy-side only. Testable against the commander with no troopers in the world. |
| 5 | Control points and objectives | 2.a, 2.b, 2.d | Points capture and reset; the level entity knows whether its objectives are met. The commander can capture one on his own. |
| 6 | Advance and scan | 1.b, 2.c, 2.e | Troopers walk the level and take points. The game has a pace to judge. |
| 7 | Trooper combat | 1.c, 1.d, 1.e | Troopers stop, fire, and go back to work. |
| 8 | Orders | 3.a–3.f | The commander steers the squad. |
| 9 | Scenario, docs, verify | — | One scenario showing all three goals; the design surface updated in the same change. |

Stage 3 before stage 7 is the one ordering that is not negotiable — the cone needs the test machinery
and the facing to both be in place.

## Not in scope, with a reason each

- **The level ending or changing when its objectives are met.** Mateusz's instruction: alpha shows the
  objectives being met and does nothing about it. The level entity will *know*; nothing reads it.
- **The commander's respawn rule.** The deferred invariant stays deferred, by decision above.
- **A radial menu for orders.** Keys `1`–`4` are scaffolding and are named as such; the real binding
  is a button plus a radial menu and needs the interface work.
- **Reinforcements becoming real troopers.** `Level.pendingReinforcements` counts rescued troopers
  and nothing reads it. The squad now exists, so this is finally buildable — and it is a separate
  piece of work, not a rider on this one.
- **The frozen trooper being replaced.** Same reason. It is scaffolding whose named replacement is
  "real troopers", but replacing it changes the feeding-ground scenario and belongs on its own.
- **Line of sight for anything but a trooper's detection.** No weapon checks it, and aliens do not
  gain it.
- **Obstacles and corpses blocking line.** Named as a known limit above.

## Scaffolding introduced by this run

Behaviour that will look like design later and is not. Each names what replaces it.

| Scaffolding | Replaced by |
|---|---|
| Orders are issued on keys `1`–`4` | A button and a radial menu, per Mateusz. The verbs are designed; the binding is not. |
| A trooper under orders is drawn with a marker on its circle | Whatever the art does with it. The value is published on `Appearance`; the renderer decides. |
| A control point is a circle on the ground that fills up | Whatever a control point looks like. The dwell, the reset and the radius are real. |
| Objectives are met and nothing happens | The level lifecycle — what a completed level does is still an open question. |
| A scenario names its control points by hand | Generated fragments, the same way rock is hand-laid in `Warren` today and waiting on generation. |
