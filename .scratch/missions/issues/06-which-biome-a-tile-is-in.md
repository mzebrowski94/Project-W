# Which biome a tile is in

Type: grilling
Status: resolved

## Question

How the map says which biome a tile is in (beside, or as, its regions), what a map with no biomes
answers, and the exact amendment to G0031 and the nest row of `invariants/aliens.md`: wake on
same-biome and within `wakeRadius`, `sim.nest.firstBroodSeconds`, the tier's period. Also how both
Adventures declare their biomes from their walls.

## Answer

Grilled 2026-10-04 (Q1-Q10). Recorded as **G0062** (amends G0031) and three glossary rows (**biome**,
**finale**, **region**). G0061 was left free: map.md names it for the Adventure finale fix, which is not
in the repository yet.

**Biome is a grouping of regions** (Q1). Every region lies inside one biome, and a tile is in its region's
biome - one partition, so the shading and the biome can never disagree, and "a region never straddles two
biomes" is testable. Not a second per-tile layer (two partitions that can drift), not column spans (they
bake in west to east, and 01 keeps a turning path possible).

| Where | What |
|---|---|
| `TileMap` | `biomeAtTile(col, row)`, `biomeAt(x, y)`, `biomeCount()`, `NO_BIOME = -1` for off the map only; a `regionCount`-long table region -> biome |
| `TileMap.Builder` | `regionInBiome(region, biome)`; every region in biome 0 until told otherwise; `build()` checks the ids are dense |
| `Strip.lay` | gives each biome's regions to it; a ridge tile's region is one of the biome whose ground it shows |
| `NestSystem` | wakes on `map.biomeAt(nest) == map.biomeAt(man)` and within `wakeRadius`; looked up each check, no field on `Nest` - a nest never moves and the lookup is an array read |
| `Nest.wake` | starts the clock at the first-brood ticks, not the period |

**No biomes** (Q2): biome 0 everywhere, `biomeCount() == 1`. `NestSystem` has one path; every existing
scenario, the proving ground included, wakes as it does today.

**Ridge tiles** (Q3): in the biome whose ground they show - what the prototype's `baseOf` already paints.
The ridge's west half is the west biome's, its east half the east's; spill is the biome it spilt into; a
gatehouse clearing is split down the middle; a one-column wall has no middle and goes east. Pillar 5: the
ground under you says where you are.

**The finale** (Q4) is the mission's last biome on the map: `biomeCount = biomes + 1`. Glossary amended. The
dealing rules (none twice, never the same base side by side) are the dealt biomes'; the finale is drawn
from its own pool.

**First brood** (Q5): `sim.nest.firstBroodSeconds` (about 6) in `level.properties`, on the shared
`NestConfig`, so **every** nest honours it - the Adventures, the proving ground and the lair's. G0062's
*Revisit when* watches the lair: six nests waking with the boss used to give thirty seconds of room. Tier
scales the period (07), not the first brood.

**G0062** (Q6, Q7): the constraint, why the biome, why the first brood comes soon, and DO NOT BUILD - the
radius alone; a whole biome on entry; waking on crossing a ridge (an event, not a state - pillar 7); a wake
radius per biome or tier; a tile on the map with no biome.

**Invariant rows** (Q8) - written in the build stage with their tests, never before (an `enforced` row
naming a missing test fails `DecisionRecordsTest`, and so does G0062's *Enforced by*):

- `aliens.md`, the nest row becomes: **A nest sleeps until somebody in its biome comes near, and never
  sleeps again.** Its reason gains: *a nest across a ridge is somebody else's problem until you cross.*
  Tests added: `NestTest.sleepsThroughSomebodyNearButInAnotherBiome`, `.sendsItsFirstBroodSoonAfterWaking`.
- `map-and-view.md`, new: **Every tile is in exactly one biome; a region never straddles two; a ridge tile
  is in the biome whose ground it shows; a map that names none is one biome.** Tests:
  `TileMapTest.putsEveryTileInBiomeZeroUntilToldOtherwise`, `.answersARegionsBiomeForEveryTileInIt`, and a
  `Strip` test for the ridge when `Strip` is built.
- Then G0062's *Enforced by* names them.

**The Adventures** (Q9): each one's `paintRegions` adds
`builder.regionInBiome(region, Math.min(region / REGIONS_PER_BIOME, LAST_BIOME))`, `REGIONS_PER_BIOME = 3`,
`LAST_BIOME = 6`: seven biomes, the redoubt or the keep last, each wall column in the biome to its east (its
21 bands are 10 columns and every wall sits on a band's first column). No `Maps` helper by columns - that is
the rejected column-span shape again. `wakeRadius` stays 140; its `level.properties` comment ("most of a
biome ahead") is rewritten - the radius now works inside a biome. A test each:
`AdventureScenarioTest.putsEachWallInTheBiomeToItsEastAndHasSevenBiomes`, the same in Adventure 2's.

The feel changes on purpose: an Adventure nest woke from the biome before; now it wakes when you come
through the wall, and broods six seconds later.

**`new-biome` skill** (Q10): sketch updated - nests wake for their own biome, membership is `Strip`'s test,
regions are a biome's own.

**Keys**

```properties
# level.properties, beside periodSeconds
sim.nest.firstBroodSeconds=6
```
