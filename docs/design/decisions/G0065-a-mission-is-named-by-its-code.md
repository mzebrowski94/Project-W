# G0065: A mission is named by its code, and the tier never moves the map

**Status.** accepted

**Trigger.** Changing what a mission code holds or how it is checked; letting a tier change a mission's layout;
reading the date anywhere but the client; carrying a mission online as anything but `SessionSetup`'s scenario
and seed.

**Enforced by.** `MissionCodeTest` (`.readsBackEveryTierAndSeedItWrites`, `.refusesEverySingleWrongCharacter`,
`.refusesEverySwappedNeighbourPair`, `.givesEveryTierALetterOfTheAlphabetThatNoOtherTierHas`);
`LaunchOptionsTest.refusesAMissionCodeThatFailsItsCheck`. `MissionTest.laysTheSameGroundAndPointsOnEveryTier` (one seed
at three tiers, the same ground and points), `.namesAMissionByItsTierForASession`, `MissionCodeTest.playsAsItsTiersMission`.
`MissionCodeTest.dealsTheSameDailyForADate` (pinned for 2026-10-05), `.dealsEachTierAndEachDayADifferentDaily`;
`MissionChoicesTest` (`.keepsItsDayUntilThePageOpensAgain`, `.refusesATypoInTheSameWordsWhereverItIs`,
`.callsACodeADailyWhenItIsOneOfTheDaysThree`). Missions ticket 05 holds the reasoning.

**Constraint.**

- **The code is the mission.** Eight characters, shown `HK3X-Q7RA`: the **tier**'s letter, six characters of
  **mission seed** in base 25 (about 244 million a tier), one check character over the seven before it. The
  mission seed is that number, and it is the simulation's seed. The alphabet and the forgiving reading - any
  case, spaces and dashes ignored - are the **room code**'s, one alphabet for every code the player reads out.
- **A typo is refused, never played.** A code that fails its check is not a mission; ENTER CODE says so and
  starts nothing. It does not guess the code meant.
- **The tier moves numbers, never the map.** The seed alone deals the order, lays the ground and the finale and
  chooses where things stand; one seed at another tier is the same mission at another strength. A tier's letter
  is the first letter of its name.
- **A daily is a code like any other.** Its seed is a pure function of the UTC day and the tier, so the three
  dailies differ by tier. The client reads the date when the daily page opens; a daily keeps the day it was
  started on.
- **Online is the scenario and the seed.** A mission travels as `SessionSetup` with scenario `mission <tier>`
  and its seed - no protocol change; RETRY is the same setup again.

**Why a check character.** A code is for saying "beat my time on this". A typo that plays some other mission
breaks that silently, and the player never learns the time he set was on a different map. One character buys
certainty for every single slip.

**Why the tier keeps the map.** A tier is numbers on the swarm (G0063); a map that changed with it would make
"the same mission on hard" mean nothing, and would put a second axis of variety where the seed already is one.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A code without a check*, all eight characters seed. A mistyped code is a silent wrong mission.

**No:** *Correcting a mistyped code* to the nearest valid one. A guess the player did not ask for is a wrong
mission he does not know he is on.

**No:** *The tier mixed into the seed.* Every tier a different map, so a code cannot be tried on another tier.

**No:** *The code as the scenario name.* The seed would be carried twice, in two places that could disagree.

**No:** *A second alphabet* for mission codes beside the room code's.

**Revisit when** codes must survive a build change (out of scope, F), a fourth tier's name starts with a letter
another tier has or the alphabet lacks, or players need more than about 244 million missions a tier.
