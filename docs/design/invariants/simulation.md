# Invariants: The simulation

Part of the invariants indexed in [`../README.md`](../README.md#invariants), which says what a
status means. If a change would break a row here, stop and raise it rather than deciding alone.

| Invariant | Status |
|---|---|
| Every concern draws random numbers from its own stream, derived from the one level seed. | enforced - `RngTest.streamsForDifferentConcernsDiverge` |
| What a unit is doing is a field on a component, never a component added or removed (G0010). | enforced - `ArchitectureTest.onlyTheSpawnerChangesWhatAnEntityIsMadeOf` |
| Same seed and same input sequence produce the same run. | enforced - `SimulationDeterminismTest` |
| No gameplay effect depends on frame rate. | enforced - fixed timestep + `SimulationDeterminismTest` |
| Every gameplay number comes from `assets/sim/*.properties`; no tuning literals in systems. | judgement |
| Rendering reads an appearance component and never branches on what an entity is (G0003). | judgement |
| Systems communicate through components, never a message bus (G0004). | judgement |
| Everything drawn carries a `PreviousPosition` seeded at its spawn point. | enforced - `SpawnerTest.seedsPositionHistoryAtTheSpawnPointSoNothingStreaksIn` |
| How an entity collides is data on a `Collider`, not a question about what it is. | judgement |
| Only `ReaperSystem` removes an entity; anything that kills only marks it `Dead`. | enforced - `ArchitectureTest.onlyTheReaperRemovesEntities` |
| Invulnerability is reachable from the harness only, never from a scenario or config. | enforced - `ArchitectureTest.scenariosCannotSwitchOnGodMode` |
| Contact resolution records what is owed; a later system decides what it costs. Nothing in the collision path applies damage, grants a pickup or removes an entity. | judgement |
| What an entity *is* - its strain, its gun - is a field on a component. No system adds or removes a component; only `Spawner` builds one. | enforced - `ArchitectureTest.onlyTheSpawnerChangesWhatAnEntityIsMadeOf` |
| Every kind named by an enum has a definition. A table with a hole in it cannot be built. | enforced - `DefsTest.refusesToBuildATableThatIsMissingAKind` |
| The collision grid's cell size covers the largest body any entity can reach, including strains only evolution reaches. | enforced - `SimConfigTest.measuresTheLargestBodyEvenWhenOnlyEvolvingCanReachIt` |
| Every gameplay speed is retuned together, so the ratios between units survive a pace change. | judgement - the PACE block in `assets/sim/commander.properties` |
| Everything the AI does only *sometimes* is a named constant in `AiChance` with a number in `assets/sim/*.properties`, stated per second of opportunity. There is no conditional behaviour that is not on that list. | judgement - `AiChance` is the index; `Odds` is the only caller of the dice |
| A behaviour switched off draws no random number at all, so turning one off cannot reshuffle what every other behaviour rolls. | enforced - `OddsTest.drawsNothingAtAllForABehaviourThatIsSwitchedOff` |
| **A chance is dealt, not tossed** (G0039). The number a behaviour states per second of opportunity is its average exactly, and no unit waits longer than `sim.ai.chance.longestGap` times that average for something it has the opportunity to do. | enforced - `OddsTest.keepsToTheStatedRateHoweverItIsDealt`, `.neverLetsMoreThanTheLongestGapGoBy`, `.banksNothingFromASpellAtAChanceFarAboveOnePerTick` |
| What a unit is still **due** is hidden, and stays that way only because it trims runs of luck and holds nothing to learn. A behaviour the player is meant to read as a pattern is not dealt this way (G0012). | judgement - pillar 5 |
| **A count drawn over and over comes from a bag**: every value in its range once before any comes up again - a nest's brood sizes, the crates across a level's points. Drawn independently, the smallest three times running is a streak the player blames on the level. | enforced - `NestTest.sendsEveryBroodSizeInItsRangeOnceBeforeRepeatingOne`, `BagTest.dealsEveryValueOnceBeforeAnyComesUpAgain` |
