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
- `map` owns terrain, ridges, passes and which biome a tile is in (pure function of tier + seed); `sim`
  owns what stands on it (ADR 0009). The UTC date is read only in `core`.
- Done separately in the charting session, not a step on this route: the Adventure finale fix - a point
  that holds afterwards never becomes the rally point, and a march ends on a ring round the post rather
  than its middle (G0055 amended, G0061).

## Decisions so far

- [What a mission is](issues/01-what-a-mission-is.md): one level of 4 wide biomes (50-58 columns, 30 rows,
  none twice) + a finale in a strip - amended 2026-10-04 from 6 biomes of 29,
  built wholly from (tier, mission seed); daily = UTC date + tier, three maps a day; won at the finale,
  points optional but led to; tier scales numbers only; online from the start; local ✓ + best time.
- [How a biome's ground is laid](issues/02-how-a-biome-s-ground-is-laid.md): base, patches (rect or grown
  blob, `neighbour` ground), crags, outcrops - each a density, never a count; clearance 2 everywhere, so a gap
  is never sealed by construction. Prototype in `prototype/biome-ground.html`.
- [What a biome definition holds](issues/03-what-a-biome-definition-holds.md): `BiomeGround` in `map` behind
  one `Strip.lay`; `BiomeDef` = ground + one-line roster (loose/nest) + furniture; every roster count through
  `Roster.count`, pressure = tier% x (1 + ramp x position); never the same base side by side; 2-3 points a
  biome. First stage: plain, quarry, brake, anthill, badlands + redoubt. Rooms follow as a stage of their own
  ([12](issues/12-rooms-inside-a-biome.md)).
- [What lies between two biomes](issues/13-what-lies-between-two-biomes.md): a ragged rock **ridge** crossed by
  1-2 bent **passes**, about one in four a **gatehouse** (a pass through a clearing - a proof of concept that 12
  takes on). Belt and wall stay in the prototype; a chasm with bridges is an idea for later.
- [Which biome a tile is in](issues/06-which-biome-a-tile-is-in.md): a biome is a grouping of regions - every region
  in one, a tile in its region's; a ridge tile in the biome whose ground it shows; the finale is the last biome; a
  map with none is one. A nest wakes in its biome within `wakeRadius`, first brood `sim.nest.firstBroodSeconds` 6
  then the period, every nest (G0062, amends G0031). Adventures: three bands a biome, walls go east.
- [The tiers in numbers](issues/07-the-tiers-in-numbers.md): count 75/100/125 %, health 85/100/115 % on the swarm's,
  nest period 130/100/75 %, adaptation off / 240 s + 4 cards / 150 s + 3; ramp 10 % a biome position, finale unramped.
  No damage % - a man reads harm, not amount. `MissionConfig` the one reader, `Whole` the one rounding (two-percent
  overload, counts too); `sim.melee.damage` 190 -> 220 so one swing still deletes a hard grunt (G0063).
- [What a finale definition holds](issues/04-what-a-finale-definition-holds.md): a set piece (G0064) - one `FinaleDef`,
  ground + hold + waves + lair, at fixed offsets from an anchor, flipped north-south by the seed; the keep is the redoubt
  with a lair, both won by `HOLD_POINT`. Width 34, rock as fixed `pieces`, the last ridge spills west only. Waves
  `after strain min max mouth` through `Roster.count` unramped; watch 118; no finale rally ground - the last biome's
  last point stands in its last `doorColumns` (8). Needs G0061 first. Both Adventure finales ported as blocks.
- [The mission seed and its code](issues/05-the-mission-seed-and-its-code.md): the code is `T SSSSSS C` (`HK3X-Q7RA`) -
  tier letter, seed in base 25, one check that refuses every single slip; room code's alphabet; the seed is the sim
  seed and alone builds the map, the tier moves numbers (G0065). Daily = pure `daily(epochDay, tier)` in `core`'s
  `MissionCode`. Online as `SessionSetup("mission <tier>", seed)` via `Scenarios.mission`. Streams: `Randomness.derive`,
  keyed by position, build-time only; `RngStream.MISSION` for the deal.
- [Menu pages and the result screen](issues/08-menu-pages-and-the-result-screen.md): DAILY MISSIONS first in the main
  menu - three dailies with ✓ and best, the selected one's code under them, RANDOM a tier choice row, ENTER CODE an
  inline line with an 8-slot picker whose first slot is tiers only, a refused code never hinted at; a code equal to a
  daily is that daily; HOST reuses the page. The result screen for every level (G0066): first outcome final, world
  runs on, 3 s hold, time and NEW BEST on a win only, RETRY / COPY CODE / MENU back to where it was started.
  Another tier deferred.
- [What is remembered and where](issues/09-what-is-remembered-and-where.md): one key per won (UTC day, tier), best ticks, in a
  libGDX `Preferences` store of its own behind `core`'s `MissionRecords`, kept forever (G0067). Written on the outcome
  tick by every machine present, a late joiner too; a joiner matches the seed against its day and the days either
  side. A build that moves a daily's map leaves the record standing; two copies on one PC, last flush wins.

## Not yet specified

- **Placement inside a biome**: where points, crates, medkits, nests and loose creatures go relative
  to each other, the gap and the walls - rules rather than coordinates. Unblocked: 02 and 03 are settled,
  so it can graduate to a ticket. 04 hands it two rules: a point in the last biome's last `doorColumns`, and
  that biome's nests outside the finale's watch.
- **How big the field gets**: the size is settled (about 255 x 30 tiles: 4 biomes of 50-58 columns + the
  finale); entity counts and frame time with hard tier numbers are not - the ported rosters are about 1.8 times
  an Adventure biome's - nor whether the avoidance and mine scans (`alpha-phase.md`, known limits) survive it.
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
