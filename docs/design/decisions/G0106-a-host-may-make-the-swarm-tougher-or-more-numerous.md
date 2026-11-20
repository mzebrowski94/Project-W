# G0106: A host may make a mission's swarm tougher or more numerous, beside its tier

**Status.** accepted

**Trigger.** Adding or changing what a host may set on a mission besides its code, tier and length; putting a health
or count number on a mission's swarm; changing what is recorded for a daily.

**Enforced by.** `MissionStrengthTest` (the fold, the refusal of a percentage the config does not offer, the name, the map
left alone), `MissionConfig.hasStrength` (the one reading of the range), `WholeTest.multipliesThreePercentsBeforeRoundingOnce`,
`MissionChoicesTest` (the rows, the note), `MenuTreeTest`.

**Asked for** by Mateusz, 2026-11-20: enemy health and enemy count, 50 % to 300 % in steps of 50 %, under the length row, on
the hosting page and the daily missions page alike - and, after the first build, that they and the length are
unlocked only by a **CUSTOM** level, the rows showing the chosen tier's own percentages otherwise.

**Amends G0063 and G0102.** G0063's tier is still four numbers on the swarm; a strength is two more, *the host's*, on top of
the tier's. G0102 turned down a length outside the code because one code must name one mission; this keeps to it by
what the code names: the **map** - the biomes, the ground, the points - and not how many creatures stand on it.

**Constraint.**

- **A strength is two whole percentages**, health and count, each `sim.mission.strengthMinPercent` (50) to
  `strengthMaxPercent` (300) in steps of `strengthStepPercent` (50), a hundred among them. `MissionConfig.hasStrength`
  is the one reading of that; a scenario whose strength the config does not offer refuses to build.
- **It sits beside the code, not in it.** It travels in the session's scenario name - `mission hard 4 h150 c200`, each
  part named only when it is not a hundred (`MissionName`, the one place a name is written and read) - and the host's
  note says it in words to a joiner on the briefing. A name with none is as it was.
- **One fold, one rounding.** `MissionConfig.apply(tier, strength, config)` puts health into `SwarmConfig`
  (`strengthHealthPercent`, read by `SimConfig.healthOf` and `nestHealth`); count is `strengthCountPercent`, read where
  a mission's placement and its finale's waves are scaled (`Pressure.times`). A percentage meeting a whole number
  meets the tier's and the ramp's in `Whole.percentOf`, once.
- **The map is the code's.** The placement attempt is chosen by what fits every *tier*, as before, without the strength: the
  ground, the order, the points stay where the code puts them at any strength. Past what the room holds creatures are
  not placed (a shortfall, as before) - at 300 % on hard the biome says so in its shortfalls and stands fewer than asked.
- **CUSTOM unlocks them.** The missions page has a CUSTOM entry after RANDOM, a switch that starts OFF and that each press
  turns ON or OFF. On, it takes the code the line holds - else the last one played, else a fresh draw - and only then
  do the length, health and count rows change; otherwise they are dim and passed over. It is session only: a daily, a
  RANDOM, a typed, pasted or picked code puts it, the strength and the length back. While it is off the rows show
  the tier's own percentages (`MissionConfig.healthPercentOf`/`countPercentOf`): easy 85 % health and 75 % count, medium
  100 and 100, hard 115 and 125. Turning it on puts both rows at 100 %, the strength, laid on the tier's; words
  elsewhere say the strength.
- **A daily is a hundred and a hundred.** Choosing one puts both rows there; a daily played at another strength keeps
  no best time (G0067). The pause page and the top bar say the strength in words when it is not normal (pillar 5).
- **No relational guarantee is held off a hundred.** G0063's "what a player learns holds at every tier" holds for the
  tiers; a host who makes grunts 300 % tougher has chosen a fist that does not delete one.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *The strength in the mission code.* Longer codes to read out for a number the session can carry - and a record
of a daily that could not tell its strengths apart.

**No:** *A damage or speed percentage here.* The player's side is the same on every tier (G0063); this is the swarm's
health and count only.

**No:** *Scaling the count to the room* so it always fits. A shortfall is said, never hidden (G0070).

**Revisit when** a long mission at 300 % has been played: whether the biomes' room holds, and whether the extremes want
the relational guarantees to be checked against them.
