# G0070: A biome short of room is placed again, and its counts fit the room it has

**Status.** accepted

**Trigger.** Changing what happens when a biome's placement cannot put down all it was dealt; changing a block's
roster ranges or the `sim.mission.placement.*` numbers; proposing that a count shrink to fit.

**Enforced by.** `MissionSweepTest.holdsEveryMissionToTheRules` (every seed on every tier: no pass short),
`MissionTest.laysTheSameGroundAndPointsOnEveryTier`, `MissionTest.putsMoreCreaturesDownOnAHarderTier`.

**Amends G0068**: what a pass that falls short does, and what a block's counts must fit. Taken as the default in
missions stage 7 while Mateusz was away - his to confirm after play.

**Constraint.**

- **A biome that falls short is placed again.** Its counts are drawn once; its passes draw from an attempt stream,
  and a biome any of whose passes falls short on any tier is placed again from the next attempt, up to 32. Every
  tier then places from the first attempt that fit them all, so one seed stands its points in the same places on
  every tier. A biome no attempt fits puts down what fits and reports it - a generator fault, which the sweep fails.
- **A block's counts fit the room placement leaves**, on hard at the last position (x1.625, or x1.25 dealt first)
  in the worst of 2000 seeds. Measured in stage 7 with the placement numbers below: a biome holds 3-7 holes and
  35-75 loose creatures at its median seed, and its worst seeds 1-2 and 20-50 - the first biome least, since
  nothing hostile stands within `startKeepOff` of the start. So a block deals 1-2 holes and at most about 25 loose
  creatures. The x1.8 ports of ticket 03 dealt 4-8 holes and up to 39.
- **Pack anchors keep `nestKeepOff` off the passes into a biome**, as its nests do; the start keeps its
  `startKeepOff`. `packApart` is 30; a nest and a boulder stand `rockMargin` (6) off rock.

**Why again, rather than fewer.** The room a biome leaves is a property of its ground and of where its points fell:
its worst seed of two thousand had none for a single hole where its median had five. Placing again keeps every
rule a rule - nothing goes down nearer than it may - and keeps a short biome a fault rather than a quieter biome.

**Why every tier.** Choosing the attempt on one tier let another fall short from it, and choosing per tier would
move the points between tiers: a mission on hard would no longer be the same mission (G0065).

**Why the counts gave, not the radii.** The radii mean something - above the wake radius, above a point's rouse -
and the ported counts were already marked for recalibration once the tiers set their numbers (missions tickets 03
and 07). At the grilled numbers, 63 of 64 missions fell short even on easy.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Scaling a count down to the room.* A biome quieter than its block says, with nothing to say so - G0068's
*silently placing fewer* by another name.

**No:** *A sweep that tolerates a share of short biomes.* A short biome is a fault the player meets as a quieter
place; a rate makes it a feature nobody chose.

**No:** *Drawing the counts again on a new attempt.* Retries would select for small draws, and the biome's numbers
would no longer be its block's.

**No:** *Choosing the attempt on the hardest tier alone.* Measured: easier tiers then fell short from it.

**Revisit when** play says the biomes are too quiet - then loosen a radius (the start's keep-off for loose creatures,
which no nest wakes by) rather than raise a count past the room - or when the first biome should differ from the
rest by rule, which would be a field on `PlacementRules`, not a block per position.
