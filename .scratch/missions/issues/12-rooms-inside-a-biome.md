# Rooms inside a biome

Type: prototype, then grilling
Status: resolved (grilled 2026-10-05, no prototype: the look is judged in a played mission)
Blocked by: 03

## Question

How generation lays **rooms** - houses, bases: rectangular or square ground enclosed by thin walls with a way
in - inside a biome, and what may stand in one (a control point, crates). Asked for by Mateusz on 2026-10-04,
in the same breath as the wider biomes of amended 01: bigger biomes are what leave room for something major
inside one. Built as stage 5 (11): after the strip, before the first biome stage, so the biomes and the
`new-biome` skill are built with rooms settled. The stage is `map` only - what stands in a room is placement
(ticket 14), built in the first biome stage. **Gatehouses are built here**, not with the ridges, in the form this
ticket settles.

Run `game-patterns` first - nothing on its shortlist lays enclosures yet, so the technique that wins (stamped
rectangles placed like outcrops, a BSP split of a lot, a prefab with rules) goes on it with its trade-off.
Then `feature`: a room is new ground and new play.

Open, roughly in the order they unblock each other:

- **What "thin" means.** A wall one tile thick - rock, or a new impassable `Terrain` that is drawn as a wall
  rather than as rock (pillar 5) - is ground the map, the flow field, `Sight` and the solver already handle. A
  wall on the edge between two tiles is new geometry for every one of them and an ADR. Prototype both looks
  before choosing.
- **The way in.** How many doorways, how wide (`clearance` says 2 tiles at least), where relative to the
  route and the passes. A doorway is a door of the connectivity check: every one reachable, the inside reachable.
- **Its place in the definition.** A fifth coat of `BiomeGround` (a density or a count, a size range, laid
  before outcrops, kept off aprons), or a sub-variant of a biome ("a quarry with a base in it") - 03 left the
  shape open to both.
- **What goes in it.** A point, crates, a nest? Rules, never coordinates - decided with "placement inside a
  biome" (map, not yet specified), which this ticket may graduate together with.
- **Pillars.** 4 - no roof and no fog: you see into a room from outside it. 1 - a fight at a doorway is a
  reflex fight, not a puzzle. 2 - the commander still walks in; nothing about a room is managed from outside.
- **Rooms on a border.** Gatehouses (13) ship as a proof of concept with the ridges: one pass through a
  clearing. This ticket improves or expands them too - and a room on a border is probably not the same thing as
  a room inside a biome, so the two may be two answers.
- **The word.** *Room* is close to **room code** (a session's name) and *headroom*; *house*, *base*, *compound*
  and *post* (taken - a level's ground to hold) are the others to weigh. Glossary, once chosen.

## Answer

Grilled 2026-10-05. The prototype was skipped: the edge wall was ruled out on cost alone, and the tile wall's look
is judged in a played mission (stage 7), the first place a strip can be seen.

- **The word**: a **house**, entered by **doorways**, enclosed by a **wall**. Not *room* (room code), not *base* (a
  biome's base ground), not *post* (taken). Glossary.
- **The wall**: one tile thick, a new impassable `Terrain.WALL` - blocks a unit, a shot and a clear line exactly as
  rock does, drawn apart from rock (its own `graphic.terrain.wall`, rimmed as rock is) so a house reads as built
  (pillar 5). A wall on the edge between two tiles is ruled out: new geometry for the map, the flow field, `Sight`
  and the solver, and an ADR, for nothing a tile wall cannot do.
- **The shape**: a rectangle 8-12 x 7-10 tiles outside its walls (6-10 x 5-8 inside), strip-wide.
- **The way in**: exactly **2 doorways**, each **3 wide** (one over the clearance: a fight in one is a squeeze, not
  a jam), in two different walls drawn from the four, each drawn along its wall and never at a corner.
- **Its place in the definition**: the first **coat** of `BiomeGround` - `Houses(min, max)`, off at zero - laid
  before patches because it is the big shape (regions before tiles). **A count, not a density**: a house is a
  place, not texture, 0-2 a biome; a density of ~80-tile pieces lands a whole house off. The one exception to
  *density, never a count*. A biome that cannot fit its `min` is a sweep fault, never silently short.
- **What it keeps clear**: the house stands the clearance off every rock already laid (faces, border, other
  houses). Its inside and a **doorstep** - the clearance deep outside each doorway - are kept base ground like an
  apron: no patch, crag or outcrop lands there. Each doorstep is an end of the biome's coats, so a crag is cleared
  rather than cut a house off and an outcrop that would is refused.
- **What goes in it**: placement's (ticket 14), and the **door** with it - Mateusz's spec is in 14. `Strip.Laid`
  reports every house and its doorways; the map knows nothing of doors.
- **Pillars**: 4 - no roof, no fog: a house is drawn open and you see into it. 1 - two doorways 3 wide, a reflex
  fight. 2 - the commander walks in; nothing is managed from outside.
- **Gatehouses** - a house on the border, the second answer: a share of the ridges between two biomes
  (`gatehouseShare` 0.25, drawn per ridge from its own stream), **never the ridge before the finale** (04's straight
  west edge and door point untouched). The ridge widens to the house plus one rock column each side - inside 8 x 9,
  so 12 columns - its faces wander as any ridge's, and its one pass enters by a doorway in the west wall and leaves by
  one in the east, on another row. The pass runs a row in from the border either side, so a doorway is never at a
  corner, and the house stands as high as both doorways need, in the middle of the rows that allow. Its doorways are 3 wide like a house's (the 4-wide pass narrows to the gate):
  one doorway width, one door. Which biome a gatehouse tile is in stays 06's halves.

Strip-wide fields of `StripRules` (keys `sim.mission.ground.*` when `sim` reads them, stage 7; replacing 13's
`chamberWidth`, `chamberHeight`):

```properties
sim.mission.ground.houseWidthMin=8
sim.mission.ground.houseWidthMax=12
sim.mission.ground.houseHeightMin=7
sim.mission.ground.houseHeightMax=10
sim.mission.ground.doorways=2
sim.mission.ground.doorwayWidth=3
sim.mission.ground.gatehouseShare=0.25
sim.mission.ground.gatehouseWidth=8
sim.mission.ground.gatehouseHeight=9
```

Per biome, `sim.biome.<name>.ground.houses.min` / `.max` (stage 7; `ConfigBinder` already leaves a left-out `houses` off, as it does crags and outcrops). The test biomes: quarry 1-2 ("a quarry with a base
in it"), the other four 0-1, so the sweep proves a house survives every coat.
