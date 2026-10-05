# Invariants: Map and view

Part of the invariants indexed in [`../README.md`](../README.md#invariants), which says what a
status means. If a change would break a row here, stop and raise it rather than deciding alone.

| Invariant | Status |
|---|---|
| Nothing ends a tick outside the arena. | enforced - `ArenaBoundsTest.neverLetsACrowdSqueezeAnythingOutOfTheArena` |
| Obstacles are ordinary entities with a `Position` and a `Body`. Nothing gets a second pairwise path. | enforced - `SpawnerTest.makesAnObstacleAnOrdinaryEntityWithAPositionAndABody` |
| Impassable terrain is not an entity. It constrains where a body may be by clamping, exactly as the arena edge already does, and never enters the pairwise path (G0009). | enforced - `TerrainCollisionTest` |
| A wall holds under crowd pressure. When constraints conflict, bodies overlap each other rather than one ending up inside rock. | enforced - `TerrainCollisionTest.keepsEveryoneOutOfRockWhenACrowdIsDrivenIntoAWall` |
| A body never passes through a gap narrower than itself, however hard it is pushed. | enforced - `TerrainCollisionTest.refusesAGapNarrowerThanTheBodyTryingToFitThrough` |
| A wall is one surface, not a row of blocks: nothing catches on a tile seam sliding along it. | enforced - `TerrainCollisionTest.letsAUnitSlideAlongAWallWithoutSnaggingOnTileSeams` |
| Rock stops a shot, as an obstacle does. | enforced - `TerrainCollisionTest.stopsAProjectileThatFliesIntoRock` |
| Nothing is spawned inside rock, and nothing is spawned anywhere the commander cannot reach. **Checked over a spread of seeds and against the numbers the game ships, not the fixture's** - amended 2026-09-19. A level that scatters its rock at random and then hand-places a crate is right or wrong depending on the seed, and a body is a different size in the game from in the test config, so one world built one way proved nothing. Both holes were real: two armouries in the Expanse and a brute in the Advance. | enforced - `ScenariosTest.spawnsNothingInsideRock`, `ScenariosTest.leavesEverySpawnReachableFromTheCommander` |
| **A generated level does not hand-place anything.** Where the rock is decided by a seed, so is where there is room; a tile chosen by hand is a tile that is sometimes inside a wall. Scatter into a band instead and the map answers. | judgement - `ExpanseScenario.armouries`; the row above holds the half that is testable |
| A map is a function of its seed: the same seed lays out the same level. | enforced - `MapsTest.buildsTheSameMapTwiceFromTheSameStream`, `SimulationDeterminismTest` |
| Map generation cannot see an entity, a component or a renderer. | enforced - `MapArchitectureTest.mapKnowsNothingOfTheGameAroundIt` |
| Anything outside the map reads as impassable, rather than as open ground or an error. | enforced - `TileMapTest.readsAnythingOutsideItselfAsImpassable` |
| **Every tile is in exactly one biome; a region never straddles two; a ridge tile is in the biome whose ground it shows; a map that names none is one biome** (G0062). A biome is a grouping of regions, so the shading and the biome cannot disagree; off the map is the only place without one. | enforced - `TileMapTest.putsEveryTileInBiomeZeroUntilToldOtherwise`, `.answersARegionsBiomeForEveryTileInIt`, `StripTest.putsEveryRidgeTileInTheBiomeWhoseGroundItShows`, `.putsAOneColumnRidgeInTheBiomeToItsEast` |
| **A house is never cut off**: every doorway of every house - in a biome or a gatehouse - is reached from the strip's first pass, and so is the whole of its inside; its wall is whole but for its doorways, and its inside, doorways and doorsteps are its biome's base ground (missions ticket 12). Checked over 64 seeds on every verify and 2000 under `PWT_SWEEP=1`. | enforced - `StripSweepTest.holdsEveryStripToTheRules` (`StripFaults.houses`, `.reach`), `StripTest.wallsEveryHouseWithItsDoorwaysInWallsOfTheirOwnAndItsInsideOpen` |
| Every kind of terrain has a colour, so a new one cannot be invisible. | enforced - `GraphicConfigTest.mapsEveryTerrainToAColour` |
| A level's size is its map's extent. Nothing holds a second arena size that could disagree. | judgement - there is one type; `SimConfig.MapConfig` only says what to build |
| Nothing is quantised to the tile grid - no movement, no logic, and no spawn snapped to a tile centre (G0009). | judgement |
| Only `TileMap` converts a world position into a tile. | judgement |
| Map generation draws from its own stream, so changing a layout never reshuffles what happens in play. | judgement - `Scenarios.world` derives it; one seed still decides the whole level |
| A level may be larger than the view, and the camera keeps every player in it (G0008). | enforced - `CameraDirectorTest.framesBothTargetsWhenThereAreTwo` |
| The view never comes to **rest** showing anything past the edge of the map, and centres on a level smaller than itself. **Only a shake may take it outside, and never further than the shake displaces it** - amended 2026-09-13, because the clamp pins the view on a level no bigger than itself and was silently deleting every screen shake on the default map size. | enforced - `CameraDirectorTest.keepsTheViewInsideTheMap`, `.centresOnAMapSmallerThanTheView`, `.letsAShakeLeaveTheMapButNeverByMoreThanTheShake`, `.shakesEvenOnALevelNoBiggerThanTheView` |
| The **view** is told who to watch by a component and never asks which entity is a commander; four of them are four `Focus` markers and no renderer change. The harness asks by **slot** - only to know whether a joining player already has a man on the field - and never to decide what to draw. | judgement - `Focus`; `ScenarioSession.seat` is the only place that looks a commander up |
| The camera feels the same at every frame rate. | enforced - `CameraDirectorTest.movesTheSameDistanceWhateverTheFrameRate` |
| What may be spawned on a patch of ground is decided by its terrain. | enforced - `ScatterTest.keepsEverythingOffGroundItWasNotOfferedTo` |
| Nothing placed by the grid stands at a tile centre. | enforced - `ScatterTest.standsThingsInsideTheirTilesRatherThanAtTheCentre` |
| A placement routine reports what it could not place rather than silently placing fewer. | enforced - `ScatterTest.reportsWhatItCouldNotPlace` |
| A sight line is exact: every tile the line crosses is tested, including the corner where two walls meet. | enforced - `TileMapTest.seesNothingThroughTheCornerWhereTwoWallsMeet` |
| The simulation never moves the camera. It publishes what happened and how violent it was; what the view does about it is the client's. | judgement - `Shake`, the same split `Focus` already makes |
