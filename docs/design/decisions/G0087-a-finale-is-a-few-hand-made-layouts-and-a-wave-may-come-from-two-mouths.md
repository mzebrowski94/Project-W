# G0087: A finale is a few hand-made layouts, and a wave may come from two mouths

**Status.** amended by G0120: the hand-made layouts are replaced by placement rules

**Trigger.** Adding or changing a finale's layouts; varying where anything of a finale stands; a wave that comes from
more than one mouth; proposing positions worked out from the seed.

**Enforced by.** `FinaleDefTest` - `.laysEachLayoutOverWhatEveryLayoutShares`, `.flipsEveryLayoutAsItFlipsTheRest`,
`.refusesAWaveFromAMouthItsHoldLacks`, `.sendsHalfTheRedoubtsWavesFromTwoMouthsMostlyLate`;
`MissionTest.dealsEveryLayoutOfEveryFinaleAndLaysTheOneDealt`, `.flipsTheFinaleNorthSouthAndNeverEastWest`,
`.sendsAWaveFromEachMouthItNamesAsOneWave`; `DefendTest.sendsAWaveFromTwoPlacesOnTheOneTickAsOneWave`;
`MissionSweepTest.holdsEveryMissionToTheRules` (every finale, layout and flip by name, `MissionFaults.finale`);
`ConfigBinderTest.readsAListOfBlocksNumberedFromOne`; `WorldCodecTest.carriesAWaveFromSeveralPlacesThroughASnapshot`.

**Settled on Mateusz's behalf, 2026-11-13** (AFK run, provisional, for review), from the playtest list (ticket 10 of
the playtest-feedback map): *the wave finale needs to be made harder, e.g. waves from two sides at once; vary the
layout of turrets, mines and walls in finales, keeping some symmetry or pattern.*

**Amends G0064**: "the same layout every time" becomes "one of a few hand-made layouts, dealt". Its Do-not-build
*a finale laid out by rules* stands.

**Constraint.**

- **A few hand-made layouts.** A finale's block holds numbered `layouts`, each its own pieces, turrets, mines, barrels
  and pickups, every position an offset from the anchor; the ground and the hold keep what every layout shares - the
  post, the watch and the **mouths** always, and whatever else the finale fixes (the redoubt's crate and flamer, the
  keep's barrels and pickups). Each layout is written symmetric north-south about the anchor's row, or to a pattern
  its comment names. The redoubt has three - the square (Adventure 1's), the diamond, the bastions - and the keep three
  of rock and mines; the keep's **lair** stands where it always has.
- **Dealt, then flipped.** The finale's stream draws the kind, then the flip, then the layout, each equally and
  whatever the tier, so adding a layout re-draws no finale or flip. The tier never moves a layout.
- **Mouths stay the finale's**, so every wave's mouth exists in every layout.
- **A wave may come from several mouths at once**, `nw+se`: one wave of the schedule - one stamp, cleared and earned
  once - put down on one tick, each mouth its own count drawn through `Roster.count`. The range is what one mouth
  sends. The redoubt sends half its waves from two opposite corners, most of them late, each mouth about what one
  sent alone, trimmed by up to a fifth where two at once would be a wall.
- **Every layout is swept by name**: the mission sweep takes seeds that end on each finale, layout and flip, never
  trusting the spread to reach them.

**Why a few layouts, not rules.** G0064's reason holds: a finale is learnt, and a layout the player has seen is one he
can plan a march for. Three learnable layouts keep that and stop the end reading the same every mission; rules that
place guns would make each finale noise, which is what G0064 rejected.

**Why from two mouths at once rather than more waves.** The redoubt was too easy because one corner at a time is a
fight the whole squad can face; two corners at once split it, which is the tactic the Adventure's alternating corners
were reaching for. More or bigger waves from one corner only lengthen the same fight.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Mouths that vary with the layout.* A wave names its mouth; mouths that move would let a wave and a layout
disagree, and the corners are what the player learns first.

**No:** *A layout per tier.* The tier moves counts and nothing else (G0063, G0065).

**No:** *A wave from two mouths as two waves.* It would earn two drafts and stamp two waves for one fight.

**Revisit when** a finale needs a layout the flip and a handful of hand-made ones cannot give, or play shows the
layouts too few to stay fresh.
