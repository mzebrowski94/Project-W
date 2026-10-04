# G0062: A nest wakes for its own biome, and its first brood comes soon

**Status.** accepted

**Trigger.** Changing what wakes a nest or when its first brood comes, or asking which biome a tile is in.

**Enforced by.** `NestTest` (`.sleepsThroughSomebodyNearButInAnotherBiome`,
`.wakesForSomebodyNearInAnotherRegionOfItsOwnBiome`, `.sendsItsFirstBroodSoonAfterWaking`), `TileMapTest`
(`.putsEveryTileInBiomeZeroUntilToldOtherwise`, `.answersARegionsBiomeForEveryTileInIt`), and the
Adventures' `AdventureScenarioTest.putsEachWallInTheBiomeToItsEastAndHasSevenBiomes` and
`AdventureTwoScenarioTest.putsEachWallInTheBiomeToItsEastAndHasSevenBiomes`. The ridge tile waits for a
test of `Strip`.

**Constraint.** **Amends G0031.** A nest wakes when somebody on the player's side is **in its biome and
within `wakeRadius`** - not within the radius alone. Its first brood comes `sim.nest.firstBroodSeconds`
after waking, then one every `periodSeconds`; every nest honours it, the lair's included. Waking still
latches.

Which biome a tile is in is the map's (ADR 0009): every region lies inside one biome, so a tile is in its
region's biome. A **ridge** tile is in the biome whose ground it shows - the west half of the ridge the west
biome's, the east half the east's, a one-column wall the east's. The **finale** is a mission's last biome. A
map that names no biomes is one biome, so a level without them wakes its nests exactly as before.

**Why the biome.** On a mission the radius reaches through a ridge, so a nest would work behind rock in a
biome nobody has entered, and what came through the pass would be the accumulation G0031 exists to prevent.
The biome is the place the player recognises - the ground he stands on says which one he is in (pillar 5);
the radius keeps a wide biome's far end quiet until the march gets there.

**Why the first brood comes soon.** A nest now wakes when you arrive rather than a biome ahead, so a first
brood a whole period off would be a hole you walk up to that does nothing. A few seconds means you see the
hole and then see it work, which is what G0031's sleep was for.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Waking on the radius alone.* On a level whose biomes are walled apart, it wakes what is behind the
wall.

**No:** *Waking the whole biome on entry, whatever the distance.* A biome fifty columns wide would wake a nest
two hundred and fifty world units off, and the far end would be an accumulation by the time you got there.

**No:** *Waking on crossing a ridge.* An event, not a state: nothing a snapshot carries says it happened,
and a man who crossed and came back would have woken a biome he is not in (pillar 7).

**No:** *A wake radius per biome or per tier.* Tier moves the period and nothing else about a nest (missions
ticket 01); a biome's nests differ in their strain and their count.

**No:** *A "no biome" answer for a tile on the map.* Two code paths, and two tiles with no biome would read
as the same one. Off the map is the only place without one.

**Revisit when** the lair has been played with its first broods at six seconds (G0048): six nests waking on
the tick the boss rises was thirty seconds of room before.
