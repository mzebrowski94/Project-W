# Missions - wayfinder map

Label: wayfinder:map
Charted: 2026-10-04

## Destination

A spec for **missions** (`spec.md` in this folder), with every decision it rests on recorded where this
repository records decisions - G-records, glossary rows, invariant rows - and the build cut into stages
of one session each, any of which the work can stop after. Plus what the `new-biome` skill must say, so
it can be written with the first biome stage. Ready to hand to `feature` -> `new-system` sessions.

## Notes

- Vocabulary (settled, in `docs/glossary.md`): **mission**, **mission seed**, **daily mission**, **tier**,
  **biome**, **finale**. Never *sector* (a trooper's arc round a point), *run*, *map* or *difficulty* for these.
- Skills every session consults: `feature` (any gameplay decision), `game-patterns` (before inventing a
  generation technique), `new-kind` (biome, finale and tier are kinds as data, G0008), and `grilling` +
  `domain-modeling` for grilling tickets. Glossary terms go to `docs/glossary.md`, never a `CONTEXT.md`.
- Pillars in play: 4 (a mission is wider than the view; one fight), 6 (one seed builds everything),
  7 (online is core: a mission is built from what `SessionSetup` already carries - scenario name, seed,
  config text), 3 (the record is not an economy).
- `map` owns terrain, walls, gaps and which biome a tile is in (pure function of tier + seed); `sim`
  owns what stands on it (ADR 0009). The UTC date is read only in `core`.
- Done separately in the charting session, not a step on this route: the Adventure finale fix - a point
  that holds afterwards never becomes the rally point, and a march ends on a ring round the post rather
  than its middle (G0055 amended, G0061).

## Decisions so far

- [What a mission is](issues/01-what-a-mission-is.md): one level of 6 biomes + a finale in a strip,
  built wholly from (tier, mission seed); daily = UTC date + tier, three maps a day; won at the finale,
  points optional but led to; tier scales numbers only; online from the start; local ✓ + best time.

## Not yet specified

- **Placement inside a biome**: where points, crates, medkits, nests and loose creatures go relative
  to each other, the gap and the walls - rules rather than coordinates. Graduates once the ground
  technique (02) and the biome definition (03) are settled.
- **How big the field gets**: map size, entity counts and frame time for a 6-biome mission with hard
  tier numbers; whether the avoidance and mine scans (`alpha-phase.md`, known limits) survive it.
- **Performance of generation itself**: how long building a mission takes, and whether a join waits on it.
- **What the goal line says** across a mission: point by point, then the finale; wording with the HUD.
- **Testing a seed sweep cheaply** enough to run in `verify.sh` - how many seeds, which assertions.

## Out of scope

- Leaderboards and comparing results between players - needs a server.
- Biome sub-variants and variations (M) - the definition should not prevent them; designing them is later.
- New strains for biomes - added later, each through `new-kind`.
- Loose weapons lying on the map (I).
- Seeds staying the same mission across builds (F).
- A campaign of levels, carrying anything between missions, distinct commander kits.
