# G0110: A taken site leaves the crates, counted by players; a crate holds no kind twice; Looter

**Status.** amended by G0127: Firepower stocks no flamer

**Trigger.** Anything a taken site - a control point, a mainframe, a relic - earns or lays; where crates come from;
a biome's crate line; how many crates a taking leaves for how many players; two cards of one kind in a crate; an
upgrade that adds a card to a crate; adding a kind of site.

**Enforced by.** `ControlPointTest.laysCratesBesideItBySeatedCountWhenItIsTaken` (1, 2, 3, 5, 6, 8 seated: 1, 1, 2,
2, 3, 3), `.laysItsCratesOnceAndNeverAgainWhileItIsHeld`, `.laysNoCratesBesideAPointThatIsHeldAfterwards`,
`.marksItsSiteTakenOnTheTickItIsTaken`, and every older `ControlPointTest` row of G0078 and G0086 through the shared
step; `SimConfigTest.refusesSiteCrateThresholdsThatDoNotStartAtOneOrDoNotRise`, `.countsTheShippedSiteCratesByPlayers`;
`CrateTest.neverShowsOneKindTwiceInACrateOverManyDeals`, `.dealsASmallerCrateFromADeckShortOfKinds`,
`.addsNoSecondCardOfTheKindAStockNames`, `.holdsOneCardMoreOnALooterHoldersFirstOpening`,
`.givesNothingMoreOnTheLooterHoldersSecondOpening`, `.givesNothingMoreOnAnotherLooterHoldersLaterOpening`,
`.addsACardOfANewKindToACrateANonLooterOpenedFirst`, `.neverHoldsMoreThanACrateCanUnderLooter`;
`MissionSweepTest.holdsEveryMissionToTheRules` through `MissionFaults.crates` (no biome crate outside a house).

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 5); the points under
*Provisional* are the implementer's, taken in an AFK run.

**Amends G0056, G0068 and G0078** - G0056's crates now come from takings and houses, and hold no kind twice; G0068's
order loses "crates by points" and counts sites, not points; G0078's "what a taken point lays" is any taken site's.

**Constraint.**

- **A site is the umbrella** for a control point, a mainframe and a relic: a place in a biome the squad takes. A
  biome has `sim.biome.<name>.furniture.sitesMin`..`sitesMax` of them - the old `pointsMin/pointsMax`, renamed so
  the key does not lie once another kind replaces a point - two or three in every biome. Placement lays them one a
  column band as it laid points (`Placement.Biome.sites`): it is told how many the biome has and which bands
  another kind of site takes, and stands a control point in every other band. No band is another kind's yet; the
  mainframe (G0111) and the relic (G0114) fill it.
- **What a taking earns and leaves is one step for any site**, `Sites.taken` in `system.level`, which
  `ControlPointSystem` calls today and a worked site's system will tomorrow. It marks the site's `Site` taken, puts a
  goal on the tally of every commander standing within its reach (G0044), and lays once, in the ring a point's
  reinforcements lie in (`sim.controlPoint.reinforcementRadius`), off every point and every pickup lying there: the
  grenade caches, `sim.controlPoint.grenadeCaches` a seated slot (G0078); the medkit on its keyed
  `sim.controlPoint.medkitChance` (G0086); and **crates**.
- **The draft and the goal line count sites**: `pointsPerDraft` counts every taken `Site`, and the goal line's
  taken/total counts every `Site` on the field. A new kind of site is counted by carrying one.
- **Crates by players.** A taken site of a biome leaves crates by `Seats.count` - as the caches count - over
  `sim.site.crates.byPlayers=1,3,6,10`, the fewest players for each next crate: one crate for one or two players,
  two for three to five, three for six to nine, four for ten and more (`Players.MAX` is eight, so ten only
  future-proofs). The loader refuses a list that does not start at one or does not strictly rise. Each crate is dealt
  on its first opening like any crate (G0056).
- **The finale's hold point is no biome's site** and stays as it was: its caches and its medkit before the hold, its
  draft and its place on the goal line. It lays no crates.
- **Furniture crates are house stock only.** A biome's `pickup crate min max` is honoured only as `houseStock`, crates
  first, as before; a crate no house takes is not laid, and the "crates by a point" pass is gone. Biomes that lay no
  house - the cut, the fen, the caldera, the desert - have no crate line; the ones with 0-1 houses keep theirs, which
  means "crates in the house, when there is one". The finales' hand-placed crates are untouched.
- **No crate holds two cards of one pickup kind.** A card of a kind the crate already holds is never drawn: it stays
  in the armoury bag for another crate, and another card is drawn. A deck with no other kind left makes a smaller
  crate than its drawn size. A stocked card is swapped in only when the deal holds none. The armoury deck's even
  deal - a bag over cards - is kept.
- **Looter** (`UpgradeKind.LOOTER`, personal, one rank, `Stat.CRATE_EXTRA`): the first time a commander holding it
  opens a given crate, the crate holds one card more of a kind it does not hold, if it has room under four. Once per
  crate, ever (`Crate.looted`), whoever's Looter set it off. Dealt at this opening, the deal is one bigger; dealt
  before by somebody else, one more card is drawn now. The extra card stays for whoever opens it next (G0056).

**Provisional, for review (AFK run).**

- **The shared step is `Sites`, a helper each system holding sites builds** and binds in its `addedToEngine`, with
  `taken(site, at, reach, laysCrates)`. *Turned down:* a system of its own polling a "just taken" flag - a tick late,
  and a second system reacting to the first's output; and a static helper - every family passed in on every call.
- **`Site` is a component**, one `taken` flag, on every control point the spawner lays. *Turned down:* counting
  `ControlPoint.captured` where the draft and the goal line count - each of 06's and 09's kinds would then be added
  to every counter; and a count on `Level` - the goal line needs the total too.
- **The hold point carries a `Site`** (so its draft and goal line are unchanged) **and passes `laysCrates` false**.
  *Turned down:* taking it out of `Site` - its draft and its place on the goal line would go.
- **`grenadeCaches` and `medkitChance` stay under `sim.controlPoint.*`**; only the crates are `sim.site.*`.
  *Turned down:* moving them - the ticket holds `ControlPointTest` unchanged, and it sets both through
  `ControlPointConfig`.
- **Crates are laid after the caches and the medkit, from the same scatter**, so those two fall where they fell
  before; a cornered site lays fewer crates, as it lays fewer caches.
- **A shuffle with only kinds the crate holds left in it is shuffled afresh**, and a crate is smaller only when the
  whole deck holds no other kind. *Turned down:* a smaller crate whenever the shuffle runs short - on the shipped deck
  that would shrink a crate near the end of every shuffle.
- **Looter fires on the opening tick only, and `looted` is set even when the crate had no room.** *Turned down:*
  leaving a full crate unlooted for a later Looter after a take - "once per crate, ever" reads as the first Looter
  opening. Its extra card is drawn with the deal, before Firepower's stock, so a stocked flamer may stand where the
  last drawn card was; the crate still holds one card more.
- **Looter is tier 1, weight 10**, as Pantera; its card line reads `OWN +1 CRATE CARD`
  (`ui.effect.count.CRATE_EXTRA`).
- **The site seam marks other kinds' bands with a bit a band**, `NO_OTHER_SITES` today. *Turned down:* a site-kind
  enum in the seam - the worked-site table is 06's to make.
- **The readout's `pointsTaken` and `pointsTotal` keep their names** and count sites. *Turned down:* renaming them -
  a wider diff for a name only the HUD reads.
- **Firepower's second rank adds no second flamer** (no kind twice); its damage still rises a rank. Its card's stock
  line still reads per rank - left for Mateusz to retune (one rank, or the stock off its second).

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Crates laid by the points as furniture beside crates from takings.* Two sources of the same reward on the
same ground, one of them before the fight; the taking is where the reward is.

**No:** *One crate a taking whatever the player count.* Co-op would be a race for the one crate, as G0078 refused for
the caches.

**No:** *Two cards of one kind in a crate.* A choice between two of the same card is no choice (pillar 1).

**No:** *A Looter that adds a card on every opening, or once per Looter.* Every opening would be a slot machine
(G0056), and a squad of Looters would empty the deck into one crate.

**Revisit when** a site is not a control point (G0111, G0114), a game seats more than eight, or a crate should hold
more than four.

## Amended by G0127

Firepower stocks no flamer (G0127), so no shipped card stocks a crate: the rule that a stocked card is swapped in only
where the deal holds none stands for the `stock` form, which stays a card's grammar (`CrateTest.addsNoSecondCardOfTheKindAStockNames`).
The two bullets above on Firepower's second rank and on Looter's card before *Firepower's stock* read as history; the
stock line Mateusz was to retune is gone.
