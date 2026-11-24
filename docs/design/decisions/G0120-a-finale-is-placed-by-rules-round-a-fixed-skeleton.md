# G0120: A finale is placed by rules round a fixed skeleton, and its enemies are dealt by role

**Status.** accepted

**Trigger.** Placing anything in a finale; adding or changing a finale's placement rules, its roles or its lair's pool;
turrets in the keep; proposing hand-made layouts again; anything of a finale moved by the tier.

**Enforced by.** Placement (ticket 03): `MissionSweepTest.holdsEveryMissionToTheRules` through `MissionFaults.finale` and
`MissionFaults.finaleRules` (counts within ranges, mirrored pairs of things and cover, every piece's ring - its lee - open
and reached, the ways from the mouths and to the boss clear of rock, the keep's guns, caches and cover inside his
barrage), sweeping every finale both ways by name; `MissionTest.flipsTheFinalesSkeletonNorthSouthAndNeverEastWest`,
`.dealsTheFinalesCoverByItsRulesAndLaysItAsGround`, `.placesEverythingRoundTheFinaleInMirroredPairs`,
`.standsTheKeepsTwoGunsInsideTheBossesBarrage`, `.endsOnTheSameFinaleOnEveryTierButForHowManyComeInAWave`;
`FinaleDefTest.refusesTurretsThatAreDealt`, `.refusesALotThatIsNotDealtInPairs`,
`.refusesPickupsThatAreNotOneOfEachKindNamed`, `.flipsEveryPositionNorthSouthAndNoneEastWest`;
`ScatterTest.placesInMirroredPairsAboutARow`; `ConfigBinderTest.refusesBlocksNumberedUnderAListsKey`.

**Settled with Mateusz, 2026-11-24** (grilling, `grill-with-docs`): *more variety in the finale - walls, especially cover
for the boss fight, turrets, mines, weapons, crates and nests varied per playthrough, reproducibly; vary the nests' strains
and the enemies of each wave; a few turrets in the boss fight.* Not yet built, not yet played.

**Amends G0064 and G0087.** G0064's *a finale laid out by rules* is reversed - its own revisit, "variety the flip cannot
give", is what was asked for - and so is its 2026-10-06 "no guns on mounts" for the keep. G0087's hand-made layouts are
deleted: placement has one way, not two. G0087's waves from several mouths stand, and so does every other rejection of
both records.

**Constraint.**

- **A fixed skeleton.** Each finale's block keeps the post, the watch, the **mouths**, the north-south flip and, in the
  keep, the boss's spot and the six lair nest spots. These are what a player learns of an arena and stay learnt.
- **Everything else is placed by rules** from the finale's own stream through `Scatter`, after the kind and the flip:
  rock, turrets, mines, barrels, crates and gun caches - the hold's and the lair's alike.
- **In mirrored north-south pairs.** One half is placed and mirrored about the anchor's row; something placed alone
  stands on that row. A flank is never bare where the other is full.
- **Cover in bands, each with its lee.** Cover is rock, pieces from a short list of shapes in the block (short bars and
  L shapes), placed in distance bands: in the keep between the post and the boss, inside his barrage's reach; in the
  redoubt between the post and each mouth. Every piece's **lee** is open and reached; pieces keep a gap between them,
  and none stands on a mouth's route or the lair's.
- **Counts dealt from ranges**, each a min-max in the block, never moved by the tier (G0063, G0065) nor by the players
  (G0119: the lair is placed, not counted). **Turrets are fixed**: four in the redoubt round the post, and **two in the
  keep**, a mirrored pair between the post and the boss inside his barrage - a tool that costs exposure to use.
- **Wave enemies by role.** A redoubt wave keeps its time and its mouths and names a **role** in place of a strain; the
  block defines each role once as strains with a count range each, and the finale's stream deals one member per wave.
  The schedule's rhythm is learnt; who comes is not. Each finale still names its own strains (G0064).
- **Lair nests from the keep's own pool**: each of the six spots is dealt a strain that walks from the pool the keep
  names, no strain more than twice. The lair's guns are dealt from the keep's list, none twice.
- **Every rule a sweep can refute is checked by `MissionFaults`**: routes and mouths open, the lee of every piece open and
  reached, nothing hostile of the last biome inside the watch.

**Why rules, after G0064 called them noise.** Mateusz chose them over sockets in hand-made layouts and over more
layouts. What G0064 feared - a finale that cannot be learnt - is answered by the skeleton and the symmetry: the player
learns the arena's shape and the waves' rhythm, and reads the cover and the guns fresh each time, as he reads a biome.

**Why turrets inside the boss's reach.** Behind the post they turn the fight into sitting on guns - the reason they were
dropped on 2026-10-06. Inside his barrage a man who gets on one takes the patterns for it.

**Accepted cost.** Every mission code dealt before this ends on different ground (G0065); the finale's stream draws
more. A playthrough's difficulty varies with the counts drawn.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Hand-made layouts beside the rules.* A second way to place a finale.

**No:** *Sockets in hand-made layouts.* Offered and declined for rules (Mateusz, 2026-11-24).

**No:** *Free placement, unmirrored.* One flank bare and the other cluttered.

**No:** *Any strain per wave by a weight budget.* A wave of brutes where the warm-up was meant; roles keep the rhythm.

**No:** *Turret counts dealt.* The turrets are each finale's backbone.

**Revisit when** play shows the rules give arenas that read the same, or cover the boss's barrage makes useless.

## Amended by

**Finale-variety ticket 03, 2026-11-24 - provisional, for review (AFK run).** How the placement half was built, where
the record left it open:

1. **A lot is a count range and a ring.** `fewest most near far west east`: so many, in a band `near`..`far` tiles
   from the post on column offsets `west`..`east`. Both ends even, every lot dealt in **pairs** - nothing is ever
   placed alone on the anchor's row, so the record's "something placed alone stands on that row" never arises.
   Turned down: boxes in offsets (a ring reads "round the post" and "between the post and the boss" with one shape);
   odd counts with the last on the row (on the keep's row it would stand on the way to the boss).
2. **The rules live in `sim`** (`FinalePlacement`, beside `Placement`): the keep's band reads the boss's barrage
   reach, a strain's number. `map` gained only `Scatter.mirroredAbout`, a mirrored pass every lot uses. Turned down:
   `Mission` mirroring `Scatter`'s output (a mirror that does not fit would land anyway).
3. **Cover is dealt on the finale's bare ground before the strip is laid** - its base, a border of rock - in anchor
   offsets, since rock is ground. An attempt that cannot lay every pair is dealt again from the next attempt's
   stream, up to 32, its count kept (G0070's rule). Measured: ~6 % of keeps fell short at once; none of 1500 after.
4. **Gaps.** `apart=1.5` tiles between anything placed round the post; `gap=2` tiles between pieces, kept off the
   rock border, a piece's first of a pair far enough south that the rows between it and its mirror are at least the
   gap (one fewer left a one-tile slot, which the strip refuses). Routes are straight lines - each mouth's middle to
   the post, the post to the lair's boss - kept a tile clear.
5. **Shapes**: `bar 3 1; bar 1 3; bar 2 1; ell 2 2; ell 3 2; ell 2 3`. An L's corner turns to the lair's boss, else
   the nearest mouth. Turned down: `ell 3 3` (too wide for the keep's band beside the nests).
6. **The barrage's reach** is the shortest positive range of the guns its patterns fire (a range of 0 flies on, so
   it does not limit): 110, the spew's.
7. **Numbers**, from the deleted layouts: redoubt cover 4-6 in 5-12 tiles, turrets 4 in 4-8, mines 16-20 in 3-13,
   barrels 6-8 in 3-12, a crate and a flamer in 2-4; keep cover **2-4** in 3-11 east of the post (4-6 did not fit
   its band beside the nests), turrets 2 in 3-9 east, mines 8-12 and barrels 4-6 no further east than 8 and 6 - the
   boss's first ring never sets one off - a crate and a laser in 2-4, the lair's four caches in 2-11, 4-12 east.
8. **The lair's caches** are dealt positions for the four kinds the keep names, in that order (ticket 04 deals the
   kinds); its holes and boss stay at their spots.
