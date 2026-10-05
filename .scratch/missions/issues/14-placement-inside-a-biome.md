# Placement inside a biome

Type: grilling
Status: open
Blocked by: 12

## Question

Where everything a biome's roster and furniture count goes, relative to each other, the passes, the ridges and
rooms - as **rules, never coordinates**. Graduated from map.md's *Not yet specified* by 11. Grilled after 12 (a
room's contents are placement rules; the two may merge) and **before stage 7**, the first biome stage, which waits
for it. Run `game-patterns` first (`Scatter` is the house tool), then `feature`.

## Handed down

- **Counts are settled elsewhere**: `Roster.count` for creatures (tier x ramp, 03, G0063); furniture ranges are
  fixed (03). This ticket says *where*, never *how many*.
- **Rules are strip-wide** (`sim.mission.placement.*`) unless a biome genuinely needs its own - then a field on
  `BiomeDef`, off at zero for every biome (10, Q1).
- **From 04 / G0064**: one of the last biome's points stands in its last `sim.mission.finale.doorColumns` (8)
  columns; the last biome's nests stand outside the finale's watch.
- **From 01, 02, 03, 13**: nothing but base ground in a pass's apron, and no scatter there either; clearance 2.
- **From G0062**: a nest wakes only for somebody in its own biome and within `wakeRadius`.
- **Built from `RngStream.MISSION`**, derived per sub-concern and per position (05); build-time only.
- **Checked by `MissionSweepTest`** (10): every point and crate reachable, the two rules from 04.

## Open, roughly in the order they unblock each other

- **Where the squad starts** - the first biome's west edge, and what may stand near it.
- **Points** - 2-3 a biome: spread along the march or free; distance from passes, ridges and each other; in a room
  or never in one.
- **Nests** - distance from the pass you come in by, from points, from each other; how near a ridge (a nest by the
  far side of a ridge is quiet until you cross, G0062).
- **Loose creatures** - put down unaware (03): spread, clumped round something, kept off the entry pass.
- **Crates, medkits, mushrooms, flowers** - near points, in rooms, or anywhere; how far from nests.
- **Barrels, boulders, rabbits** - by rock, by points, anywhere.
- **What a room holds** - with 12.
- **The technique** - one `Scatter` pass per kind with exclusion radii, a ranked list of candidate spots, or zones
  per biome; and what happens when a rule cannot be met.
