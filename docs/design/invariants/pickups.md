# Invariants: Pickups and crates

Part of the invariants indexed in [`../README.md`](../README.md#invariants), which says what a
status means. If a change would break a row here, stop and raise it rather than deciding alone.

| Invariant | Status |
|---|---|
| A pickup that carries health can be shot or struck; one that does not is scenery a weapon passes through. | enforced - `RabbitTest.feedsNobodyOnceItHasBeenShot`, `.canBeDeniedWithTheHandStrike` |
| A pickup that has been killed feeds nobody. Denial at range is a real thing the player can do. | enforced - `RabbitTest.feedsNobodyOnceItHasBeenShot` |
| A pickup worth crossing the ground for glows; the rest is scenery. Zero shine means not for me. | judgement - `PickupDef.shineSeconds`, published by `AppearanceSystem` on the charge channel |
| A pickup is taken by exactly one taker. | enforced - `PickupSystemTest.givesOnePickupToExactlyOneTaker` |
| A pickup a side is offered nothing by is left standing, not consumed. **And a pickup worth nothing to *this* taker is not claimed either** (G0053) - a medkit to a man with no wound - decided at the claim from the grant and the taker, never a flag on the pickup; `MedkitTest.isLeftStandingByAManWithoutAWound` holds it. | enforced - `PickupSystemTest.leavesAPickupStandingForASideItOffersNothing` |
| Nothing in its death throes takes a pickup or evolves. | enforced - `PickupSystemTest.letsNothingInItsDeathThroesTakeAnything` |
| A weapon pickup replaces what the commander is carrying. Nothing about a gun survives his death: he comes back with `sim.commander.weapon`, the sidearm everywhere but the proving ground (G0060). | enforced - `WeaponPickupTest.givesHimBackHisSidearmWhenHeComesBack`, `.givesHimBackTheGunHisConfigNamesWhenHeComesBack` |
| A pickup that would go off is taken only on a roll, and only while the two are actually touching (G0022). | enforced - `BarrelTest.isLeftStandingByAnAlienWithNoAppetiteForOne`, `.goesOffWhenAnAlienBitesIntoIt` |
| **A crate is dealt what it holds the first time it is opened, and never before**; from then on it holds what it was dealt less what was taken, whoever opens it, and it is gone once empty (G0056). | enforced - `CrateTest.dealsWhatItHoldsOnItsFirstOpeningAndNotBefore`, `.keepsWhatItWasDealtLessWhatWasTakenWhenOpenedAgain`, `.takesAnEmptiedCrateOffTheField`, `.dealsNoCardTwiceBeforeEveryCardHasComeUp`, `.dealsEverySizeOnceBeforeRepeatingOne` |
| **One commander has a crate open at a time**, decided in the simulation from the tick's input - two presses on one tick go to the lower slot - so every machine agrees without asking one (G0056, pillar 7). | enforced - `CrateTest.letsOnlyOneCommanderHaveItOpenAtATime`, `.givesItToTheLowerSlotWhenTwoPressOnTheSameTick`, `.changesNothingForATakeFromAnybodyButItsOpener` |
| **An open crate shuts on its opener** the tick he takes a wound or goes down, steps out of arm's length, drops his link, takes something or shuts it; a medkit he would be turned down for is not taken and leaves it open (G0053, G0056). | enforced - `CrateTest.shutsItTheTickHeTakesAWound`, `.shutsItWhenHeGoesDown`, `.shutsItWhenHeStepsOutOfArmsLength`, `.shutsItWhenHisLinkDrops`, `.shutsItWhenHeClosesIt`, `.handsTheOpenerWhatHeTakesAndShutsTheCrate`, `.turnsDownAMedkitForAManWithNoWoundAndLeavesTheCrateOpen` |
| **A grenade cache fills him to his most**, whatever upgrades make that, and is turned down while he carries it - the medkit's rule, asked of his grenades (G0077). | enforced - `CrateTest.fillsHisGrenadesWhenHeTakesAGrenadeCacheFromIt`, `.turnsDownAGrenadeCacheForAManCarryingHisMostAndLeavesTheCrateOpen` |
| **A taken point - any taken site (G0110) - lays a grenade cache for every seated slot**, once, the tick it falls - standing, down or on his way back - just outside it and within its reinforcements' reach (G0078); it may lay a medkit there too (G0086, the next row). | enforced - `ControlPointTest.laysAGrenadeCacheBesideItForEverySeatedSlotWhenItIsTaken`, `.countsASlotWhoseCommanderIsOnHisWayBack`, `.laysThemOnceAndNeverAgainWhileItIsHeld` |
| **A taken point may lay one medkit** (G0086): at `sim.controlPoint.medkitChance` per cent, keyed on the point and the level's seed - the same point on the same seed always does or never does - in the ring its grenade caches lie in, once, the tick it falls. One a point, not a slot: a trooper takes it too. Furniture, so a tier does not scale it. | enforced - `ControlPointTest.laysOneMedkitBesideItWhenItsChanceSaysSo`, `.laysNoMedkitWhenItsChanceSaysNo`, `.laysItsMedkitOnceAndNeverAgainWhileItIsHeld`, `.laysAMedkitAtAboutItsChanceAcrossPointsAndSeeds` |
| **No crate holds two cards of one pickup kind** (G0110): a drawn card of a kind it holds goes back and another is drawn, a deck short of kinds makes a smaller crate, and Firepower's flamer is swapped in only where the deal held none. | enforced - `CrateTest.neverShowsOneKindTwiceInACrateOverManyDeals`, `.dealsASmallerCrateFromADeckShortOfKinds`, `.addsNoSecondFlamerUnderFirepower` |
| **A taken site leaves crates by the seated count** (G0110): `sim.site.crates.byPlayers` - one for one or two players, two for three to five, three for six to nine - in the ring its grenade caches lie in, once, the tick it falls; the finale's hold point leaves none. A biome's own crates lie only in its houses. | enforced - `ControlPointTest.laysCratesBesideItBySeatedCountWhenItIsTaken`, `.laysItsCratesOnceAndNeverAgainWhileItIsHeld`, `.laysNoCratesBesideAPointThatIsHeldAfterwards`, `SimConfigTest.countsTheShippedSiteCratesByPlayers`; houses only - `MissionSweepTest.holdsEveryMissionToTheRules` (`MissionFaults.crates`) |
| **A take by touch on the player's side pops**, in the taken pickup's look, where the taker stands, and is gone after `sim.pop.seconds`; an alien's meal never does (G0078). | enforced - `PopTest.leavesAPopWearingThePickupsLookWhereAManTookIt`, `.isGoneOnceItsMomentIsOver`, `.leavesNoneWhereAnAlienAteSomething` |
| A medkit is taken only by a wounded man and is scenery to everything else. A wounded trooper with nothing to fight goes for one within reach, and drops the walk the moment it stops being worth it. | enforced - `MedkitTest.isLeftStandingByAManWithoutAWound`, `.isLeftStandingByTheAliens`, `.goesToTheWoundedManOfTwoStandingOnIt`, `.walksAWoundedTrooperToAMedkitWithinReach`, `.leavesAMedkitOutOfReachWhereItLies`, `.dropsTheWalkOnceTheMedkitIsGone` |

**A gun is found and lost, never owned.** Picking one up replaces what you were carrying outright and
dying gives you the sidearm back. Slots you switch between were considered and rejected: they need a
binding, and the input map has nothing free - the melee strike took right mouse and the squad
command is still waiting for one. They also turn a moment of "take it or deny it" into a menu the
player carries around, which is what pillar 3 is against even though there is no shop involved. If a
second weapon axis is ever wanted, it is a second *kind* of weapon slot - a melee weapon alongside a
gun - not two guns.

**A pickup carries a grant per side, and either may be nothing.** An item that offers a side nothing
is not consumed by that side, so denial has to be earned rather than handed over: the commander walks
over a mushroom and leaves it, and the only way to stop something eating one is to be standing on it
or to kill what is coming for it.

**Nothing in the table is contested any more, and that is a decision rather than a gap** (2026-09-18).
A supply drop and a weapons cache used to be a mouthful to whatever reached them first, which made
each one a race; they are now the player's alone. What that cost is the race, and what it bought is a
level whose kit is still there when you get to it - on a march four screens long, a cache eaten by a
runner twenty seconds in is a gun the player never sees and cannot plan around, and a crate of men
eaten before he arrives is a squad he is never told he lost. The mechanism stays: both halves are
still declared per kind, `PickupDef.offersSomethingTo` still decides who consumes what, and the day a
contested item is wanted again it is two fields in a table. `AlphaWorlds.contesting` is the world the
test of that mechanism builds for itself.
