# G0115: The rift is a fissure maze shut by ice, with two clearings

**Status.** amended by G0116: hunters break ice in their way; amended by G0117: troopers shoot down ice in theirs

**Trigger.** The rift's ground, roster or furniture; a terrain laid as crags that is not rock or lava; a round
stretch of a biome kept clear of rock; sites or packs placed by a biome's clearings; anything that shuts a way for a
while and is broken through - ice.

**Enforced by.** `CoatsTest.carvesTheRiftIntoLongNarrowLevelCorridors`, `.laysNoCragInAClearingAndKeepsEveryClearingReached`,
`.centresTwoClearingsEachInAnOuterThirdOfTheBiome`, `.icesChokepointsOfTheMazeAndNeverAClearingOrAWayIn`; `IceTest`;
`TerrainCollisionTest.stopsAUnitWalkedIntoAFissureAsIntoRock`, `.stopsAProjectileThatFliesIntoAFissureAsIntoRock`;
`MissionTest.standsAControlPointAtTheCentreOfEachOfTheRiftsClearings`,
`.putsMoreOfTheSwarmToATileInTheRiftsClearingsThanInItsMaze`, `.standsIceAcrossEveryWayTheStripIcedAndNoneOnAWayIn`;
`BiomePromisesTest.theRiftIsTheOneBiomeOfFissuresOnFrostWithItsSitesInTwoClearings`;
`MissionSweepTest.holdsEveryMissionToTheRules` through `StripFaults.clearings` (each clearing open ground, as many as
its ground asks for), `MissionFaults.clearings` (a point on each clearing's centre tile) and `MissionFaults.ice` (across a
narrow way, never in a clearing or by kept ground, nothing standing in it); `GraphicConfigTest.mapsEveryTerrainToAColour`.

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 8, and ticket 10). The points
under *Provisional* are the implementer's, taken in an AFK run, for review.

**Amended 2026-11-23, with Mateusz** (after playing the crag maze: blobs, not ways):

- **The maze is corridors, not crags.** A new coat, `Corridors` (`ground.corridors.*`, off in every other biome): the
  biome filled with fissure and long, narrow (2-3 tiles), mostly level **corridors** carved back out of it, their
  walls wandering a row along their length like rock (`wander=1`), joined through short **links** down the walls - a
  braided maze, a randomised spanning tree over cells 8 columns long (Kruskal's) with `breaks=0.35` of the joins along
  a corridor shut first and `loops=0.3` of the spare links opened after, `chambers=0.15` of the links opened across
  their cell. Every way in opens onto the clearance round its apron; the crag repairs (ends tunnelled to, no passage
  under the clearance, pockets sealed) net the rest. The crag coat stays for the other biomes; the rift lays none.
- **Fissure is dark brown**, `graphic.terrain.fissure=2e2218ff`, not near-black.
- **Clearings of radius 5**, not 8.
- **Roster** `loose jaguar 1 2; loose thumper 2 3; loose sludger 5 6`: no nest. A hole wants `rockMargin` off
  fissure, `pointKeepOff` off the points and `nestKeepOff` off the passes, and corridors leave that on too few seeds
  (measured: dozens of 2192 short with chambers in a quarter of the cells, 50 with chambers in most of the middle
  third, which read as rooms, not corridors). The jaguars are loose instead. Turned down: chambers enough for a nest
  (the maze stops being corridors); a wider rift; loosening a keep-off for one biome (G0115's own rejection).
- **Ice share 0.1** of the narrow ways, not 0.4: in corridors every link is a chokepoint, so 0.4 iced most of them -
  about three to five ice a rift (measured over 64 rigs).
- **Ice drawn as shards**: five uneven fragments a tile, their rim wandering in and out like broken rock, seams of the
  ground between them (`graphic.door.shardGap=0.12`); cracks thinner and shorter (`graphic.door.crackWidth=0.035`,
  `.crackLength=0.6`). Its wear still darkens and cracks it (pillar 5).

**Amends G0068** - the order: a biome whose sites are in its clearings stands a point at each clearing's centre after
its worked sites and before its other points, and anchors a share of its loose packs in its clearings first.

**Constraint.**

- **The rift**, `BiomeKind.RIFT` and `sim.biome.rift`: a cold biome of **frost** (`Terrain.FROST`, passable, pace
  1, `graphic.terrain.frost=3a4552ff`) cut into a maze by **fissures** (`Terrain.FISSURE`, impassable,
  `graphic.terrain.fissure=0b0e14ff`), with two **clearings**. No house, no outcrop, no tree, no geyser; never dealt
  beside a biome of the same base, which no other has.
- **Fissures are crags** (superseded 2026-11-23: corridors, above), as the caldera's lava is (G0091): `crags.terrain=fissure`, laid with the crag repairs, so a
  pass is never closed and no passage is left under the clearance. Solid to units, shots and sight alike - **why it
  stops shots**: there is one solidity (`Solid`, G0091's shortcut); shots across a fissure stay deferred, as across
  lava, until a second terrain wants a second solidity.
- **Clearings**, `ground.clearings.count` and `.radius` (`Clearings`, off in every other biome): laid after the
  fixed pieces and before every other coat, each a disc judged at tile centres (as the proving ground's sand) where no
  crag and no outcrop is laid, its centre tile an end of the biome so the crag repairs keep it reached from the
  passes. `Strip.Laid.clearings` hands them to placement.
- **Sites in clearings**, `furniture.sitesInClearings` (false in every other biome): a control point on the tile
  nearest each clearing's centre, kept `pointApart` off any site; the band each falls in is taken, and a third site,
  when dealt, goes by the ordinary band rule. A rift dealt last always has one more than its clearings - its approach
  columns' (G0064) - so its sites are `max(drawn, clearings + 1)` there; `BiomeDef` refuses a block whose
  `sitesMax` has no room for it.
- **More of the swarm in the clearings**, `furniture.clearingPackShare=60` (0 in every other biome): pack by pack from
  the first, a pack is anchored in a clearing while fewer than 60 % of those dealt so far are, the clearings in turn;
  a pack a clearing has no room for goes anywhere. Nests keep `pointKeepOff` off the points as everywhere, so they
  stand in the maze.
- **Roster** (superseded 2026-11-23, above): `nest jaguar 1 1; loose thumper 2 3; loose sludger 5 6`. Furniture: 2-3 sites,
  barrels 1-2, boulders 0-2, a medkit; no crate line (no house, G0110).

**Provisional, for review (AFK run).**

1. **Fissure density 0.16, smoothing 2.** Lands about 0.21 of the maze outside the clearings (measured over 32 strips;
   the ticket's 0.18-0.22 is of the maze, the clearings taking about a quarter of the biome). Turned down: 0.20
   smoothed once - the crackiest look (0.23 of the maze) but no room for even one nest on some seeds; 0.30 smoothed
   twice and 0.35 smoothed once, which run together into one mass the end repair clears whole, leaving open ground.
2. **Dealt 64-72 columns**, not the others' 52-60: as the desert is dealt wider for its crowd (G0095). Measured over
   300 missions, the rift at 52-60 and 0.20 held 0 holes at its worst seed and 2 at the median, at 64-72 and 0.16 two
   and five. A dense maze leaves little ground `rockMargin` off rock for a nest, and the points in the clearings keep
   `pointKeepOff` round most of what is left.
3. **Roster cut to the room** (G0070): one hole, `nest jaguar 1 1`, for the ticket's `nest sludger 2 3; nest jaguar 2`,
   and the sludgers' nest folded into one more loose sludger (`loose sludger 5 6`). The ticket's four to five holes are
   eight at hard in the last position; the rift holds two at its worst seed (measured), and `nest sludger 1 2; nest
   jaguar 1 1` still fell short on dozens of the 2000 seeds. The jaguar's nest kept over the sludger's: the rift has no
   house, and a jaguar's den in the maze is its own. Turned down: keeping the counts and loosening a placement rule for
   one biome (nests in the clearings, a smaller `rockMargin` or `pointKeepOff`); `nest sludger 1 1` with jaguars loose.
4. **Clearing centres**: clearing `k` of `n` within a twelfth of the width of `k / (n + 1)` of it, on the side away
   from the middle - the west one in the west third, the east one in the east third - on a row a radius in from the
   top and bottom. So each falls in a site band of its own whether the biome is banded in halves, thirds, or as the
   last biome (its approach columns apart), and the two are always more than `pointApart` apart. Turned down: centres
   anywhere within a twelfth either side, which put both in the middle third of a three-site biome.
5. **No second frost tone.** Neighbour ground in small rects (0.03) for colour, as most biomes; a lighter frost was
   not needed for the maze to read - its fissures carry it.
6. **The flag and the share on the furniture**, every block carrying both: the ground (`map`) lays clearings and knows
   nothing of sites or packs (ADR 0009). Turned down: reading "a biome with clearings has its sites in them" off the
   ground alone - a clearing is ground, and a later biome may want one without a point in it.
7. **The caldera's boulders against the rift's**: `BiomePromisesTest`'s "fewer boulders than any biome that has them"
   now reads "no more than": the rift's are its ticket's 0-2, the caldera's.

8. **Ice**: its chokepoint rule (a narrow crossing whose two sides, shut, are not joined within 16 steps), its share
   (0.4 - about four a rift), its health (300) and its wear on every door are ADR 0025's provisional points, with the
   alternatives turned down.
**Ice** (stage B, ADR 0025 for the structure).

- **Ice is a door nobody opens**, standing across a chokepoint of the maze: solid ground to everything until it is
  broken, then gone for good. `map` marks the runs (`ground.ice.share=0.4` of the chokepoints, laid last); the ground
  under it stays frost, so generation counts every iced way as open, and `StripFaults.reach` and every pass check hold
  with it. Never in a clearing, never within the clearance of a pass's apron or the start - no way into the rift is
  iced.
- **Breaking**: every shot from the squad's side chips it by its damage (`sim.ice.health=300`, six sidearm shots); a
  blast - a grenade, a barrel - breaks it outright; an alien's leap lands `sim.door.leapDamage` (100) on it, as in a
  door siege; the swarm's shots do nothing. Nobody opens it with the action key, and a trooper waits for nobody at it.
- **Hunters** (superseded by G0116: a hunter breaks ice in its way) take the ground as it is now (G0075): round the ice while there is a way round, and when there is none,
  up to it and leaping at it, as at a shut door.
- **Look**: light blue, a little translucent (`graphic.color.ice=a8dcf0d0`), darker and cracked across each square as
  it is chipped (pillar 5); a door leapt at shows its wear the same way. No shatter.

**Scope.** The rift's ground, clearings, sites, packs, roster and ice. Not snow, not slippery ground, not a fissure
that shots fly over, not ice that reforms.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Shots or sight across a fissure* - a second solidity for one terrain (G0091's deferral holds).

**No:** *A clearings coat of rock rings* - a clearing is open ground kept clear of the coats, never a wall round it.

**No:** *A fissure laid as a patch* - solid ground is laid only as crags, whose repairs keep the passes open.

**No:** *Ice on the rift's way in* - its entry or exit pass, its start, a pass's apron.

**No:** *Ice the swarm's shots chip* - alien-side fire breaks no door, and ice is one.

**Revisit when** the maze reads as blobs rather than cracks on the screen, or a second biome wants clearings.
