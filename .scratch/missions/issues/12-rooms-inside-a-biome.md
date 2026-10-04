# Rooms inside a biome

Type: prototype, then grilling
Status: open
Blocked by: 03

## Question

How generation lays **rooms** - houses, bases: rectangular or square ground enclosed by thin walls with a way
in - inside a biome, and what may stand in one (a control point, crates). Asked for by Mateusz on 2026-10-04,
in the same breath as the wider biomes of amended 01: bigger biomes are what leave room for something major
inside one. Built as a stage after the first biome stage (11).

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
