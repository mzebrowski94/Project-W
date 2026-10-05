# G0064: A finale is a set piece, and the last biome's last point stands at its approach

**Status.** accepted

**Trigger.** Adding or changing a finale kind; laying anything in a finale from rules or the `Rng`; proposing a
second objective for a finale, a rally ground of its own, or waves that change with the march.

**Enforced by.** `MissionSweepTest.holdsEveryMissionToTheRules` (`MissionFaults.finale`: everything the set piece
stands and every mouth's tiles open and reached from the start, nothing hostile of the last biome inside the watch;
`MissionFaults.bands`: a point in the last `approachColumns`), `FinaleDefTest.refusesAWaveFromAMouthItsHoldLacks`,
`.flipsEveryPositionNorthSouthAndNoneEastWest`, `MissionTest.flipsTheFinaleNorthSouthAndNeverEastWest`,
`.endsOnTheSameFinaleOnEveryTierButForHowManyComeInAWave`, `.holdsTheFinalesWavesBackAndAsksOnlyThatItsPostBeHeld`,
`StripTest.laysAFinalesPiecesAsRockAtTheirOffsetsFromItsAnchor`. Missions ticket 04 holds the shape and both blocks.

**Amended 2026-10-05 (missions stage 8).** The last biome keeps **everything hostile** outside the watch - packs as
well as nests: a sleeper left inside it behind the ridge blocks the win where the player cannot see why. And the goal
line across a mission leads through the points, the finale's among them, while its waves are held back, then says the
hold - no goal word of its own.

**Constraint.**

- **One shape, parts off.** A finale is ground, a **hold** (the point that holds afterwards, its watch, turrets,
  mines, barrels, pickups and **mouths**), a wave schedule, and a **lair** that is empty unless it is a keep. The
  keep is the redoubt with a lair. Every finale is won by `HOLD_POINT` alone; a lair sits inside the watch.
- **The same layout every time.** Every position is a tile offset from the finale's anchor, written in its block.
  The only variety is one bit from the finale's stream that flips it north-south - never east-west, so west stays
  the way in. Its rock is fixed `pieces` of its ground; nothing dealt lands in a finale, and the ridge before it
  spills only westward.
- **Waves count like a roster.** A wave's count is a range drawn through `Roster.count` with the tier's
  `enemyPercent` and no ramp (G0063); its timing, its strain and its mouth are the block's. A lair's contents are
  positions and are never scaled by count.
- **Nothing hostile of the last biome stands inside the watch** - no nest and no pack - so holding the post is
  the finale's fight and never a search behind the ridge.
- **The rally point stays G0055's.** One of the last biome's points stands in its last `approachColumns` columns, so
  the finale's approach is ground the side can take. A finale has no rally ground of its own, and its held point
  never becomes the rally point (G0061).

**Why a set piece.** The finale is the fight the whole mission walks toward. A layout that is learnt is one the
player can plan a march for - where to put the mines he hauls, which corner he answers - the reason a barrage is
counted rather than dealt (G0048). Variety belongs to the march, which is generated; the end is an arena.

**Why a point at the approach, not a rally ground.** A point already earns a rally (G0055) and the goal line already
leads through points. Putting one by the finale gives a player who takes it everything a rally ground would, and
a player who skips it a cost he chose and can see.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A finale laid out by rules* - counts and distances worked out from the seed. A finale is learnt; dealt
turrets are noise, and rules that reproduce the Adventures' layout are coordinates with extra steps.

**No:** *A rally ground that jumps to the finale* when somebody enters it. A second rally rule beside the point
taken last, and it gives away what the last biome's point is for.

**No:** *A boss-dead objective.* The keep is held like the redoubt; the boss inside the watch is what makes
holding it mean killing him.

**No:** *Wave strains drawn from the march's biomes.* It couples the finale to the deal; each finale names its
own strains.

**No:** *A watch measured as the finale biome.* It changes what `HOLD_POINT` measures and what a post is (G0027)
for a radius that already does the job, with the last biome's nests kept outside it.

**Revisit when** a finale needs two posts, a wave that arrives anywhere but a mouth, or variety the flip cannot
give - or when play shows the same arena every mission reads as stale.
