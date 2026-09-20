# Game design

**Everything below the pillars is a first draft written by Claude and not yet reviewed by Mateusz.**
Treat it as a proposal to correct, not as settled design.

The **pillars** live in `CLAUDE.md` because they apply to every change, including ones nobody
planned as gameplay work. They are not repeated here.

## What belongs here, and what does not

This file carries **constraints and intent**. It does not describe how systems behave - that is what
the code is for, and a description that disagrees with `sim/system/*.java` is worse than no
description at all.

| Question | Where the answer lives |
|---|---|
| What must always be true? | Invariants, below |
| How does a phase session go, and what is still scaffolding? | [`alpha-phase.md`](alpha-phase.md) |
| What does this system do? | `sim/src/main/java/.../system/` |
| What is this number? | `assets/game.properties` |
| Why did we reject the obvious alternative? | `docs/design/decisions/` |
| What does this word mean? | `docs/glossary.md` |

## Invariants

Statements that must hold across every feature. **Before adding a mechanic, check it against this
table**; if it would break a row, stop and raise it rather than deciding alone.

`enforced` rows have a test and cannot drift. `judgement` rows need a human to notice. `deferred`
rows become enforceable once the named feature exists - promote them then.

| Invariant | Status |
|---|---|
| Input reaches the simulation as a per-tick value object; no system polls a device. | enforced - `ArchitectureTest.simulationDoesNotReadInputDirectly` |
| **A slot drives one commander and nobody else's.** One input per player per tick, handed out by `Simulation.step` before any system runs. | enforced - `CoopTest.drivesEachCommanderFromItsOwnSlot`, `.leavesASlotNobodyIsSittingInIdle` |
| A stick sends its aim as an **offset from the commander**, because it does not know where he is standing; exactly one place turns that into a point on the field, and no system below it sees two kinds of aim. | enforced - `CoopTest.aimsRelativeToWhicheverCommanderIsAiming`; one writer - `Simulation.step` |
| **No commander may walk further than the leash from the middle of the commanders.** It refuses the outward part of a step and **never pushes**, so nobody is shoved and a man at the end of the rope walks home himself. A backstop against two players ending up a level apart, deliberately far wider than the view - **so it does not hold pillar 4 on its own, and a player may be off screen.** | enforced - `CoopTest.refusesToLetTwoCommandersWalkFurtherApartThanTheLeash`, `.letsHimWalkBackTowardsTheGroupFromTheEndOfTheLeash`, `.leavesASoleCommanderFreeToWalkWhereverHeLikes` |
| The leash is a **distance in world units**, never read from the camera. Zoom is a rendering setting and must not become extra rope. | judgement - `sim.commander.leashRadius`; the row above holds the half that is testable |
| A commander comes back **beside whoever of the players is still on his feet**, and only at the arena edge when nobody is. | enforced - `CoopTest.bringsHimBackBesideACommanderWhoIsStillOnHisFeet`, `RespawnSystemTest.bringsItBackAtTheLeftOfTheArena` |
| A commander comes back **into the slot he left**, so a death does not reseat a player. | enforced - `CoopTest.bringsHimBackIntoTheSlotHeLeft` |
| Every player's commander is drawn in a colour of his own. Four identical circles is not a field anybody can read (pillar 5). | enforced - `ConfigDefaultsTest.givesEveryPlayerACommanderColourOfHisOwn` |
| Every concern draws random numbers from its own stream, derived from the one level seed. | enforced - `RngTest.streamsForDifferentConcernsDiverge` |
| A unit's lose range is never shorter than its detection range, so a commander on the boundary cannot flicker a pack between hunting and idling. | enforced - `PerceptionDefTest.refusesALoseRangeShorterThanTheDetectionRange` |
| **A frog lets go of him sooner than anything else on the field does.** "Its aggro is easy to break" is two numbers and nothing else - how far past the range it noticed you it will keep hunting - and no system would notice if somebody widened that gap while tuning. The mechanic would simply stop existing, silently. | enforced - `SimConfigTest.letsGoOfHimSoonerThanAnythingElseOnTheFieldDoes` |
| A unit that has not noticed the commander is drawn differently from one that has. | enforced - `AppearanceSystemTest.showsAUnitThatHasNotNoticedHimAsUnaware` |
| A unit that has noticed nobody stays in its patch, and walks back to it if it finds itself outside. | enforced - `WanderSystemTest.keepsAnIdleUnitNearWhereItWasSpawned`, `.walksBackToItsSpawnPointWhenItFindsItselfAwayFromIt` |
| A unit leans off what it is about to walk into before it touches it; discovering an obstacle by collision is a failure. | enforced - `AvoidanceSystemTest.leansOffALineThatWouldRunIntoSomethingLongBeforeItGetsThere` |
| Nothing steers around what it is hunting, and nothing steers around a pickup **it wants**. What would go off in your face is given as wide a berth as rock - **and wariness is about the pair, not the thing**: something that can swallow an explosive walks straight at one (G0022, amended 2026-09-17). | enforced - `AvoidanceSystemTest.neverAvoidsTheThingItIsHunting`, `.walksStraightOverItsOwnDinner`, `.leansOffABarrelAsReadilyAsOffARock`, `SpitterTest.crossesTheGroundToABarrelItCanUse`, `BarrelTest.isNotWorthCrossingTheGroundForToSomethingThatCannotUseOne` |
| A weave is a function of the ground a creature has covered and of nothing else: no clock, no dice. Two spawned in different places are out of step with each other, and the same level weaves the same way on every run (G0012's argument, applied to a course). | enforced - `RunnerTest.weavesTheSameWayOnEveryRun`, `.doesNotWeaveInLockstepWithAnother` |
| **A pack closes on one man along lines of its own, not down one line.** Each creature walks at a point beside him, on a side seeded from where it was spawned rather than rolled - so the fan is the same on every run - and the offset is spent by the time it is close, so the last of every approach is straight at him. Terrain still has the last word: where the line to its own point is not clear the flow field answers instead, and a corridor still files. | enforced - `ApproachTest.closesOnAPointBesideHimRatherThanOnHim`, `.walksStraightAtHimForTheLastOfTheApproach`, `.bringsTwoInDownOppositeSidesRatherThanOneBehindTheOther`, `.givesTwoSpawnedApartLinesOfTheirOwn`, `.takesTheSameLineOnEveryRun` |
| A spread moves the goal, never the pace. A creature taking its own line in travels at exactly the speed whatever was driving it asked for. | enforced - `ApproachTest.keepsTheSamePaceWhileItSpreads` |
| A weave turns a course; it never adds to it. A creature that weaves travels at exactly the speed whatever was driving it asked for. | enforced - `RunnerTest.keepsTheSamePaceWhileItWeaves` |
| **A gait paces a course; it never chooses one, and it never changes what the course is worth.** A hopping creature bursts and sits, and over one whole cycle covers exactly the ground a walk would - so `speed` on a strain means one thing across the table and a frog can be tuned against a grunt without arithmetic. This is the one thing in the game whose pace changes from tick to tick, which is why it is a row rather than an assumption. | enforced - `HopTest.coversTheSameGroundInHopsAsItWouldAtAWalk`, `.goesNowhereAtAllBetweenHops` |
| A gait is a **clock**, where a weave is a function of ground covered. Legs have a rhythm and a creature pressed against a wall is still trying to hop. Its phase comes from where the unit was spawned, so a pack is out of step with itself without a number being drawn and the same level hops the same way on every run. | enforced - `HopTest.putsTwoSpawnedApartOutOfStepWithEachOther`, `.hopsTheSameWayOnEveryRun` |
| A dodge is a function of the shot's geometry alone. The same shot from the same place gets the same answer, always (G0012). | enforced - `DodgeSystemTest.answersTheSameShotTheSameWayEveryTime`, `.breaksAwayFromTheSideTheShotIsOn` |
| A creature committed to a dodge cannot answer anything else until the window closes. | enforced - `DodgeSystemTest.willNotAnswerASecondShotWhileItIsCommittedToTheFirst` |
| The commander outranks food absolutely. A unit that has noticed him never diverts to eat. | enforced - `PerceptionSystemTest.walksPastFoodWhileItIsHuntingHim` |
| A pickup that carries health can be shot or struck; one that does not is scenery a weapon passes through. | enforced - `RabbitTest.feedsNobodyOnceItHasBeenShot`, `.canBeDeniedWithTheHandStrike` |
| A pickup that has been killed feeds nobody. Denial at range is a real thing the player can do. | enforced - `RabbitTest.feedsNobodyOnceItHasBeenShot` |
| A route is a function of terrain and a body size. Nothing is routed through a gap it does not fit through. | enforced - `FlowFieldTest.refusesAGapNarrowerThanTheBodyItWasBuiltFor`, `NavigationSystemTest.takesAGruntThroughACrackAndLeavesABruteOnTheWrongSideOfIt` |
| A desired velocity is spent on the tick it was written; a unit no system spoke for stops. | enforced - `DriveSystemTest.spendsADesiredVelocityOnTheTickItWasWritten`, `.stopsAUnitThatAsksForNothing` |
| Nothing drives a held unit. Whatever is holding it owns its velocity until it comes round. | enforced - `DriveSystemTest.leavesAHeldUnitToWhateverIsHoldingIt` |
| Systems that drive a unit write a desired velocity; one system turns that into a velocity (G0010). | judgement - `Steering` and `DriveSystem`; the exceptions are the commander's own control and a committed leap, both of which overwrite outright |
| What a unit is doing is a field on a component, never a component added or removed (G0010). | enforced - `ArchitectureTest.onlyTheSpawnerChangesWhatAnEntityIsMadeOf` |
| Same seed and same input sequence produce the same run. | enforced - `SimulationDeterminismTest` |
| No gameplay effect depends on frame rate. | enforced - fixed timestep + `SimulationDeterminismTest` |
| Every gameplay number comes from `game.properties`; no tuning literals in systems. | judgement |
| Nothing is bought, equipped or configured between levels. | judgement |
| Aim is independent of movement; no system assumes facing follows heading (G0002). | enforced - `CommanderControlSystemTest.firesWhereItAimsNoMatterWhereItIsWalking` |
| Rendering reads an appearance component and never branches on what an entity is (G0003). | judgement |
| Systems communicate through components, never a message bus (G0004). | judgement |
| Everything drawn carries a `PreviousPosition` seeded at its spawn point. | enforced - `SpawnerTest.seedsPositionHistoryAtTheSpawnPointSoNothingStreaksIn` |
| Nothing ends a tick outside the arena. | enforced - `ArenaBoundsTest.neverLetsACrowdSqueezeAnythingOutOfTheArena` |
| How an entity collides is data on a `Collider`, not a question about what it is. | judgement |
| Obstacles are ordinary entities with a `Position` and a `Body`. Nothing gets a second pairwise path. | enforced - `SpawnerTest.makesAnObstacleAnOrdinaryEntityWithAPositionAndABody` |
| Impassable terrain is not an entity. It constrains where a body may be by clamping, exactly as the arena edge already does, and never enters the pairwise path (G0009). | enforced - `TerrainCollisionTest` |
| A wall holds under crowd pressure. When constraints conflict, bodies overlap each other rather than one ending up inside rock. | enforced - `TerrainCollisionTest.keepsEveryoneOutOfRockWhenACrowdIsDrivenIntoAWall` |
| A body never passes through a gap narrower than itself, however hard it is pushed. | enforced - `TerrainCollisionTest.refusesAGapNarrowerThanTheBodyTryingToFitThrough` |
| A wall is one surface, not a row of blocks: nothing catches on a tile seam sliding along it. | enforced - `TerrainCollisionTest.letsAUnitSlideAlongAWallWithoutSnaggingOnTileSeams` |
| Rock stops a shot, as an obstacle does. | enforced - `TerrainCollisionTest.stopsAProjectileThatFliesIntoRock` |
| Nothing is spawned inside rock, and nothing is spawned anywhere the commander cannot reach. **Checked over a spread of seeds and against the numbers the game ships, not the fixture's** - amended 2026-09-19. A level that scatters its rock at random and then hand-places a crate is right or wrong depending on the seed, and a body is a different size in the game from in the test config, so one world built one way proved nothing. Both holes were real: two armouries in the Expanse and a brute in the Advance. | enforced - `ScenariosTest.spawnsNothingInsideRock`, `ScenariosTest.leavesEverySpawnReachableFromTheCommander` |
| **A generated level does not hand-place anything.** Where the rock is decided by a seed, so is where there is room; a tile chosen by hand is a tile that is sometimes inside a wall. Scatter into a band instead and the map answers. | judgement - `ExpanseScenario.armouries`; the row above holds the half that is testable |
| The commander is an ordinary entity - killable, collidable, standing on the field. | enforced - `CombatTest.killsTheCommanderTheMomentAnEnemyTouchesIt` |
| The commander comes back while any trooper still lives. | deferred - needs squad; with no troopers it always comes back |
| Only `ReaperSystem` removes an entity; anything that kills only marks it `Dead`. | enforced - `ArchitectureTest.onlyTheReaperRemovesEntities` |
| Damage only ever crosses faction lines, never stays inside one - and a source on **nobody's** side crosses every one of them, which is how a blast reaches the commander who caused it. | enforced - `CombatTest.neverLetsEnemiesHurtEachOther`, `BarrelTest.putsDownTheCommanderWhoShootsOneFromTooCloseUp` |
| Invulnerability is reachable from the harness only, never from a scenario or config. | enforced - `ArchitectureTest.scenariosCannotSwitchOnGodMode` |
| Troopers lost during a level stay lost for that level. **A reinforcement is a new man won from the field, never the one you lost** - nothing revives a casualty who bled out, and the crate that replaces him had to be walked to and could have been eaten first. | enforced - `SquadTest.keepsATrooperLostDuringALevelLost` |
| Squad size never exceeds its configured maximum: the squad the level started with plus its configured headroom. A reinforcement past it is **wasted, not banked** - a reserve that paid out later would be a number the player tracks and the game does not show (pillar 5). | enforced - `SquadTest.neverPutsMoreTroopersOnTheFieldThanTheSquadAllows`, `ReinforcementSystemTest.neverPutsMoreMenOnTheFieldThanTheSquadAllows`, `.takesAsManyAsTheSquadHasRoomForAndNoMore`; a scenario sets both numbers, not the build |
| A reinforcement becomes men beside the commander and nowhere else (G0030), **one a tick** - a crate is worth several and they arrive in a queue rather than in a heap. With nobody standing it is owed rather than lost, and it pays out on the tick somebody is back on his feet. | enforced - `ReinforcementSystemTest.putsHimDownBesideTheCommander`, `.putsOneManDownPerTickRatherThanTheWholeCrateAtOnce`, `.keepsAReinforcementOwedWhileNobodyIsStandingToFormUpOn`, `.deliversWhatItOwedOnceSomebodyIsStandingAgain` |
| Every control point a level names has a handful of reinforcements lying just outside it, how many drawn per point. Men are where the ground you have to take is (G0030). | enforced - `ReinforcementDeployTest.laysAHandfulOfReinforcementsBesideEveryControlPoint`, `.keepsItOffThePointItself`, `.keepsItWithinReachOfThePointItBelongsTo` |
| **A trooper taking or holding a point still watches and still shoots.** A fight outranks the ground it is standing on, so the squad is never caught watching a bar fill while something walks into it. | enforced - `TrooperAdvanceTest.goesOnLookingAroundWhileItTakesAPoint`, `.firesFromAPointItIsStandingOnWhenSomethingComesAtIt`, `.goesBackToHoldingThePointOnceTheFightIsOver` |
| **A creature leaps at whoever it is hunting**, which is whoever on the player's side is nearest to it (G0026) - any commander, or a trooper. Never "the commander", which meant the first one standing. | enforced - `EnemyLeapSystemTest.leapsAtTheSecondPlayerWhenHeIsTheOneStandingInFrontOfIt`, `.leapsAtATrooperWhenItIsTheNearestManOnTheField`, `.leapsAtNobodyOnceThereIsNobodyLeftToHunt` |
| A pickup worth crossing the ground for glows; the rest is scenery. Zero shine means not for me. | judgement - `PickupDef.shineSeconds`, published by `AppearanceSystem` on the charge channel |
| Every projectile is a simulated entity with a `Position` and a `Body`. No hitscan. | enforced - `SpawnerTest.givesEveryProjectileAPositionAndABody` |
| Every melee strike is a simulated entity with a `Position` and a `Body`. No melee hitscan (G0005). | enforced - `SpawnerTest.givesEveryStrikeAPositionAndABodySoThereIsNoMeleeHitscan` |
| A blow that kills holds both the attacker and its victim - that is the clinch. A melee kill does so only when its victim had already committed to attacking; a swat on something merely walking holds only the corpse (G0013). | enforced - `StunTest.holdsBothTheAttackerAndItsVictimWhenTheBlowKills`, `StrikeCombatTest.tanglesHimUpWhenHisSweepKillsSomethingMidLeap`, `.leavesHimFreeWhenHisSweepKillsSomethingThatWasNotComingAtHim` |
| A lethal exchange ends with the two of them touching, whatever distance the blow was struck at - and the body gets there under its own momentum, shoving the survivor back, rather than being placed (G0014). | enforced - `StrikeCombatTest.endsALethalExchangeWithTheTwoOfThemTouching`, `.shovesHimBackAsTheBodyArrives` |
| A clinch's carry must cross the longest gap a sweep can leave. A config where it stops short is refused rather than shipped. | enforced - `SimConfigTest.refusesAClinchCarryThatWouldStopShortOfTheCommander` |
| A strain that does not lose interest keeps hunting him however far he gets. | enforced - `PerceptionSystemTest.keepsHuntingHimForeverWhenItNeverLosesInterest` |
| **Whoever it was hunting going down releases it**, whatever its switches say. Nothing stands to attention over a corpse - and **down means bleeding as well as dead**, or the things that put him there would finish him where he lay (G0023). Amended 2026-09-18 (G0026): it was *him* going down, and an alien now comes for whoever is nearest, so his death releases what was coming for him and leaves the rest of the field as it was. | enforced - `PerceptionSystemTest.letsGoOfHimWhenHeGoesDownEvenThoughItNeverGivesUp`, `.losesHimWhenHeGoesDown`, `AlienTargetingTest.leavesAManWhoIsDownForWhoeverIsStillOnHisFeet`; one writer - `Drivers.nearestLivingTarget` |
| **An alien comes for whoever on the player's side is nearest and still on his feet** - the commander, a trooper, or a gun a level put down - asked again every tick and published as a place rather than latched (G0010, G0026). | enforced - `AlienTargetingTest.comesForWhoeverIsNearestRatherThanForTheCommander`, `.takesUpWithWhoeverIsLeftWhenTheOneItWasAfterGoesDown`, `.comesForATurretAsReadilyAsForAMan` |
| One place decides who an alien is hunting; the systems that walk it and fire its gun read that answer rather than asking again. | judgement - `Drivers.nearestLivingTarget` is called only by `PerceptionSystem`; `NavigationSystem` and `EnemyRangedSystem` read `Perception.targetX/Y` |
| A unit that gives up idles where it gave up, not where it was spawned. Its patch follows it while it has something better to do. | enforced - `WanderSystemTest.settlesWhereItGaveUpRatherThanWhereItWasSpawned` |
| A clinch stops both parties dead. A killing blow throws nothing, so there is no throw for the freeze to bleed off. | enforced - `StunTest.clearsTheThrowWhenAHoldBecomesAClinch`, `StrikeCombatTest.stopsHimDeadSoTheClinchDoesNotDriftApart` |
| A blow that is survived holds only the one that took it; the one that landed it stays free. | enforced - `StunTest.holdsOnlyTheVictimWhenTheBlowIsSurvived` |
| A stunned entity acts on nothing, but is still moved and still shoved by the solver. | enforced - `StunTest.stillLetsAHeldUnitBeShovedOutOfTheWay` |
| Only a target that survives a blow is thrown *away*; a killing blow leaves the body where it was struck, except that a clinch carries it the last stride *into* the survivor (G0014). | enforced - `StrikeCombatTest.throwsNothingWhenTheBlowKills`, `.shovesHimBackAsTheBodyArrives` |
| A dying body is scenery: solid, and immovable like an obstacle. | enforced - `DeathThroesTest.turnsABodyIntoAnObstacle` |
| A dying body is harmless as well as immovable. Nothing it touches is hurt by it. | enforced - `DeathThroesTest.landsNoBlowOnceItIsDead` |
| Both parties to a *fatal* exchange look the same for its whole duration, winner and loser alike - and only a fatal lock does that, so anything swatted reads as dying. | enforced - `AppearanceSystemTest.showsALostExchangeAsLockedRatherThanDyingWhileTheLockHolds` |
| A leap is aimed once at launch and never steered afterwards. | enforced - `EnemyLeapSystemTest.keepsGoingWhereItAimedEvenIfTheCommanderMoves` |
| A killing blow is instant; only the removal of the body waits. | enforced - `CombatTest.killsTheCommanderTheMomentAnEnemyTouchesIt` |
| One swing lands at most one blow. | enforced - `StrikeCombatTest.landsAtMostOneBlowPerSwing` |
| **A sweep is centred on whoever swung it, tick by tick**, not on where he stood when it was released - amended 2026-09-19. The anchor was argued for on the grounds that a tenth of a second is too short to tell the difference; the commander covers several strides in a tenth of a second, so using the hand strike while running put the sweep out of his back. A swing whose owner has left the world keeps the last centre it had. | enforced - `MeleeSystemTest.keepsTheSweepInFrontOfHimWhileHeIsWalking` |
| Everything that can be hit has a `Body`. | enforced - `SpawnerTest.givesEveryUnitABodySoItCanBeHit` |
| Contact resolution records what is owed; a later system decides what it costs. Nothing in the collision path applies damage, grants a pickup or removes an entity. | judgement |
| What an entity *is* - its strain, its gun - is a field on a component. No system adds or removes a component; only `Spawner` builds one. | enforced - `ArchitectureTest.onlyTheSpawnerChangesWhatAnEntityIsMadeOf` |
| Every kind named by an enum has a definition. A table with a hole in it cannot be built. | enforced - `DefsTest.refusesToBuildATableThatIsMissingAKind` |
| The collision grid's cell size covers the largest body any entity can reach, including strains only evolution reaches. | enforced - `SimConfigTest.measuresTheLargestBodyEvenWhenOnlyEvolvingCanReachIt` |
| A pickup is taken by exactly one taker. | enforced - `PickupSystemTest.givesOnePickupToExactlyOneTaker` |
| A pickup a side is offered nothing by is left standing, not consumed. | enforced - `PickupSystemTest.leavesAPickupStandingForASideItOffersNothing` |
| Nothing in its death throes takes a pickup or evolves. | enforced - `PickupSystemTest.letsNothingInItsDeathThroesTakeAnything` |
| A unit that evolves arrives at the new strain's full health, and is otherwise identical to one spawned in it. | enforced - `EvolutionSystemTest.startsTheNewStrainAtFullHealth` |
| How full a unit is towards evolving is published for the renderer, never left as a number only the simulation knows. | enforced - `EvolutionSystemTest.showsHowFarAlongAUnitIsTowardsEvolving` |
| A weapon pickup replaces what the commander is carrying. Nothing about a gun survives his death. | enforced - `WeaponPickupTest.givesHimBackHisSidearmWhenHeComesBack` |
| A map is a function of its seed: the same seed lays out the same level. | enforced - `MapsTest.buildsTheSameMapTwiceFromTheSameStream`, `SimulationDeterminismTest` |
| Map generation cannot see an entity, a component or a renderer. | enforced - `MapArchitectureTest.mapKnowsNothingOfTheGameAroundIt` |
| Anything outside the map reads as impassable, rather than as open ground or an error. | enforced - `TileMapTest.readsAnythingOutsideItselfAsImpassable` |
| Every kind of terrain has a colour, so a new one cannot be invisible. | enforced - `ConfigDefaultsTest.mapsEveryTerrainToAColour` |
| A level's size is its map's extent. Nothing holds a second arena size that could disagree. | judgement - there is one type; `SimConfig.MapConfig` only says what to build |
| Nothing is quantised to the tile grid - no movement, no logic, and no spawn snapped to a tile centre (G0009). | judgement |
| Only `TileMap` converts a world position into a tile. | judgement |
| Map generation draws from its own stream, so changing a layout never reshuffles what happens in play. | judgement - `Scenarios.world` derives it; one seed still decides the whole level |
| A level may be larger than the view, and the camera keeps every player in it (G0008). | enforced - `CameraDirectorTest.framesBothTargetsWhenThereAreTwo` |
| The view never comes to **rest** showing anything past the edge of the map, and centres on a level smaller than itself. **Only a shake may take it outside, and never further than the shake displaces it** - amended 2026-09-13, because the clamp pins the view on a level no bigger than itself and was silently deleting every screen shake on the default map size. | enforced - `CameraDirectorTest.keepsTheViewInsideTheMap`, `.centresOnAMapSmallerThanTheView`, `.letsAShakeLeaveTheMapButNeverByMoreThanTheShake`, `.shakesEvenOnALevelNoBiggerThanTheView` |
| **Aim is a direction, never a distance.** Zoom is a rendering setting and cannot become extra range. | enforced - `CommanderControlSystemTest.firesInTheSameDirectionHoweverFarAwayTheAimPointIs` |
| The **view** is told who to watch by a component and never asks which entity is a commander; four of them are four `Focus` markers and no renderer change. The harness asks by **slot** - only to know whether a joining player already has a man on the field - and never to decide what to draw. | judgement - `Focus`; `ScenarioSession.seat` is the only place that looks a commander up |
| The camera feels the same at every frame rate. | enforced - `CameraDirectorTest.movesTheSameDistanceWhateverTheFrameRate` |
| What may be spawned on a patch of ground is decided by its terrain. | enforced - `ScatterTest.keepsEverythingOffGroundItWasNotOfferedTo` |
| Nothing placed by the grid stands at a tile centre. | enforced - `ScatterTest.standsThingsInsideTheirTilesRatherThanAtTheCentre` |
| A placement routine reports what it could not place rather than silently placing fewer. | enforced - `ScatterTest.reportsWhatItCouldNotPlace` |
| A unit publishes which way it is looking, and anything that does not steer itself publishes none. | enforced - `FacingTest.publishesNoFacingForSomethingThatDoesNotSteerItself` |
| The commander's facing is his aim and never his heading (G0002). | enforced - `FacingTest.facesWhereTheCommanderIsAimingRatherThanWhereHeIsWalking` |
| A trooper's facing is its own decision, so it can stand still and go on watching. | enforced - `TrooperAdvanceTest.sweepsItsAttentionFromSideToSideAsItGoes`, `SquadOrderTest.holdsATrooperStillWhileItGoesOnLookingAround` |
| What a unit can notice is the list of tests its definition declares, run cheapest first (G0015). A strain that declares only a range behaves exactly as detection always did. | enforced - `PerceptionSystemTest.noticesWhatIsInsideItsArcAndNotWhatIsBehindIt`, `.noticesNothingThroughRockWhenItNeedsAClearLine` |
| Once a unit is awake, losing what it noticed is a range test alone. Turning your head never makes you forget. | enforced - `PerceptionSystemTest.keepsHuntingHimAfterItTurnsAwayFromHim`, `TrooperCombatTest.keepsFightingSomethingThatSlipsOutOfItsArc` |
| A sight line is exact: every tile the line crosses is tested, including the corner where two walls meet. | enforced - `TileMapTest.seesNothingThroughTheCornerWhereTwoWallsMeet` |
| Being hurt latches a **place**, never a unit - and the place is the muzzle, not the impact (G0016). | enforced - `AggroTest.sendsAHurtUnitAtWhereTheShotCameFrom`, `.walksTowardsTheMuzzleRatherThanStandingWhereItWasHit` |
| The commander outranks a grudge exactly as absolutely as he outranks food. | enforced - `AggroTest.dropsTheGrudgeWhenItNoticesTheCommander` |
| A control point's capture resets when the last capturer leaves. It never pauses. | enforced - `ControlPointTest.startsTheCaptureAgainWhenTheLastCapturerLeaves` |
| The post is a goal and never a target: no health, no side, no capture, nothing in the collision path. Anything that has noticed nobody walks at it, below everything else that can have its attention (G0027). | enforced - `DefendTest.walksTheAliensAtThePostWhenTheyHaveNoticedNobody`, `.leavesThePostForSomebodyItHasNoticed` |
| Holding the post means every wave sent **and** nothing hostile standing **within the post's own watch**, and a level with no post can never mean it. Zero watch means the whole level, which is every level the size of one fight; a march names a distance instead, because seven screens with nests still working four biomes back can never be clear and an objective that cannot be met is a lie. Still a pure function of the world plus the level's own schedule - nothing is latched. | enforced - `DefendTest.holdsThePostOnlyOnceTheLastWaveHasBeenSeenOff`, `.neverHoldsAPostOnALevelThatHasNone`, `.holdsAPostWhoseOwnGroundIsClearWithSomethingStillAliveAcrossTheLevel`, `.refusesToHoldAPostWithSomethingStandingOnItsOwnGround`, `.stillMeansTheWholeLevelForAPostThatNamesNoWatch` |
| What is still to arrive belongs to the level entity, not to a scenario's own timer (G0007). | enforced - `DefendTest.sendsInAWaveWhenItsTimeComes`, `.sendsEachWaveInItsOwnTime` |
| **A level may hold its whole schedule back.** Nothing arrives until something arms it, and until then the list is neither running nor spent - so an objective that asks whether it is spent says no. Every level built before this one arms from the first tick, which is what the default says. | enforced - `ControlPointTest.sendsNothingInWhileTheLevelIsStillHoldingItsScheduleBack`, `.letsGoOfThatScheduleOnceTheGroundIsTaken`, `AdventureScenarioTest.sendsNothingInAndNamesNoPostUntilThatGroundIsTaken` |
| **One point on a march is ground that is held afterwards**: taking it puts a post down where it stands and lets go of whatever the level was holding back, on the tick the bar fills. **There is no post before that**, and that is the decision rather than a detail - a post on the field from the first tick sends everything that has noticed nobody walking at it from seven screens away, and turns "forward" round for the whole march (G0027). | enforced - `ControlPointTest.putsAPostDownOnGroundThatIsHeldAfterwards`, `.namesNoPostWhileThatGroundIsStillBeingTaken`, `.leavesAnOrdinaryPointNamingNoPostWhenItIsTaken`, `AdventureScenarioTest.endsOnGroundThatIsHeldRatherThanMerelyTaken` |
| On a level that names a post, the squad's patch is the post: with nothing in front of them, troopers roam around it rather than across the level. A fight still outranks it, and "forward" still pushes past it. | enforced - `DefendTest.keepsItsRoamingAroundThePost`, `.pushesTheFrontLineAwayFromThePost` |
| On a level that names a post, "forward" is away from it. Everywhere else it is a burst of pace towards the objective; a defence has no objective to walk to, so the order needs a direction of its own or it spends itself on a seeded corner. | enforced - `DefendTest.pushesTheFrontLineAwayFromThePost` |
| A level requires every objective it names; one that names none is never complete. | enforced - `ControlPointTest.requiresEveryObjectiveALevelNames`, `.neverCompletesALevelThatNamesNoObjectives` |
| Objectives are recomputed from the world every tick, never latched. "The map is clear" must be able to stop being true. | judgement - `ObjectiveSystem` |
| Nearest objective means nearest **by route**, never by straight line. | enforced - `TrooperAdvanceTest.picksThePointThatIsTheShorterWalkRatherThanTheNearerOne`, `FlowFieldTest.countsTheWalkRatherThanTheStraightLine` |
| Urgency blends the route against the sweep and never changes speed; "forward" is the only thing that changes speed. | enforced - `TrooperAdvanceTest.keepsTheSameWalkingPaceWhateverTheUrgency` |
| A trooper stands still to shoot. | enforced - `TrooperCombatTest.standsItsGroundWhileItIsShooting` |
| A trooper hardly ever answers a leap with a sweep. Answering one is the commander's moment (G0013). | judgement - `sim.trooper.strikeChancePerSecond`; `TrooperCombatTest.neverSwingsAtSomethingThatIsMerelyWalkingAtIt` holds the half that is a rule |
| An order reaches only the troopers within earshot of the commander, and is delivered once (G0017). | enforced - `SquadOrderTest.reachesNobodyOutOfEarshot`, `.deliversAnOrderOnceRatherThanForAsLongAsItIsHeld` |
| **Forward drops whatever standing order a trooper had** - holding a place, or keeping station on him - because every one of them is a reason not to advance. Cancel is still the key that gives a man nothing to do instead. | enforced - `SquadOrderTest.dropsFollowMeWhenHeSaysForward`, `.dropsDefenceWhenHeSaysForward` |
| **A trooper standing on a captured point holds it until a forward order reaches it**, and one order buys one point (G0029). Ground the squad has taken is ground somebody is still standing on, and the level advances at the commander's pace. Because an order carries only as far as earshot, sending them on means walking up to them. | enforced - `TrooperAdvanceTest.waitsOnAPointItHasTakenRatherThanWalkingOnToTheNext`, `.goesOnToTheNextPointWhenHeSaysForward`, `.waitsAgainOnceItHasTakenTheNextPoint`, `.walksToTheFirstPointWithoutBeingTold` |
| When two commanders order one trooper on the same tick, **the higher slot is heard last and wins** (G0028). Ascending slot order, deliberately, and never whatever order Ashley holds the family in - that order changes every time somebody is killed and respawned. | enforced - `CoopTest.letsTheHigherSlotsOrderStandWhenTwoCommandersSpeakAtOnce`, `.letsTheHigherSlotsOrderStandWhicheverOrderItIs` |
| A trooper follows **the man who told it to**, not whichever commander is first. | judgement - `Trooper.orderedBy`; `TrooperMindSystem.obeys` is the single reader |
| An order outranks the level's objectives and never an ongoing fight; it is carried out when the fight ends. | enforced - `SquadOrderTest.neverInterruptsAFightAndObeysAsSoonAsItIsOver` |
| A unit acting on an order is drawn differently from one acting on its own judgement. | enforced - `SquadOrderTest.showsWhichTroopersHeardHim` |
| An order given on any frame reaches the simulation, whatever the refresh rate, and one press is one order. | enforced - `ScenarioSessionTest.deliversAnOrderGivenOnAFrameTooShortToRunATick`, `.spendsAnOrderOnceEvenWhenAFrameRunsSeveralTicks` |
| **His going down releases his own men.** Every order *he* gave drops when he dies, and none of it is restored when he comes back. The mirror of the rule that nothing stands to attention over a corpse. Narrowed from "the squad" by G0028: with four commanders, releasing the lot when any of them fell would let a death across the map cancel the formation somebody else was holding. | enforced - `SquadOrderTest.releasesTheSquadWhenTheCommanderGoesDown`, `.doesNotResumeAnOldOrderWhenHeComesBack`, `.takesTheOrderRingOffWhenHeGoesDown` |
| Everyone put down in a spawn area is jittered inside its tiles; nobody stands at a tile centre (G0009). | enforced - `SquadTest.standsNobodyAtATileCentre` |
| A trooper inside a control point holds where it stands rather than walking at its middle. Everyone in the radius counts equally. | enforced - `TrooperAdvanceTest.standsOnAPointRatherThanWalkingAtItsMiddle` |
| A trooper that stops takes a bearing of its own to watch. A squad at rest never all faces the same way. | enforced - `TrooperAdvanceTest.givesEveryManHoldingAPointHisOwnBearingToWatch`, `SquadOrderTest.spreadsTheSquadsAttentionInsteadOfAllStaringAtHim` |
| A blast is uniformly **decisive** inside one circle and harmless outside it, and that circle is the one the renderer draws (G0018). Everything standing in it goes down; what that leaves behind is a fact about the victim, not about the blast (G0020). | enforced - `BlastTest.killsEverythingStandingInsideIt`, `.leavesAnythingStandingOutsideItUntouched`, `.killsABruteAsReadilyAsAGrunt` |
| A blast is on nobody's side. It catches the commander and his troopers exactly as readily as it catches the aliens; what it leaves behind is a fact about the victim (G0020). | enforced - `BlastTest.catchesTheCommanderAsReadilyAsAnEnemy`, `BarrelTest.putsDownTheCommanderWhoShootsOneFromTooCloseUp` |
| A blast does not reach through rock, by the same clear-line test perception uses. | enforced - `BlastTest.doesNotReachThroughRock` |
| **A slick takes a side; a blast does not.** Each circle of a spatter carries the faction of whatever fired the shot, so venom is a problem for the player's men and not for the frogs that spat it. The one difference between the two, and it costs no code: the collision rule already refuses to let damage stay inside a side. | enforced - `SlickTest.leavesTheSideThatMadeItAlone` |
| **A slick is a place, not an event.** Where a blast catches what was standing in it on one tick and nothing after, a slick catches whatever walks into it for as long as it lasts - and then the ground is clean again. That is why it is an ordinary body in the pairwise path and a blast is not. | enforced - `SlickTest.staysOnTheGroundLongAfterTheShotThatMadeIt`, `.cleansTheGroundOnceItHasHadItsTime`, `.ruinsTheGroundWhereItsShotBurst` |
| A slick leaves a **casualty**, as gunfire and blasts do, and grants its victim a window afterwards - without which the next circle of the same spatter would finish him on the same tick and the wounding blow would be an outright kill everywhere it mattered. | enforced - `SlickTest.putsAManStandingInItOnTheGroundRatherThanKillingHim` |
| Nothing lands inside rock at **runtime** either: every circle of a spatter thrown against a wall that would fall in the wall is simply not laid. | enforced - `SlickTest.leavesNothingInsideRock` |
| **Fire is a slick with a different declaration, not a second mechanic** (G0035). It arrives the same way - the blast carries the reaction and dies - so nothing that explodes had to learn that fire exists, and a gun that leaves no blast leaves none: the flamethrower is an exception without a clause anywhere. | enforced - `FireTest.burnsTheGroundWhereAnExplosionWent`, `.leavesNothingBurningWhereNothingExploded`, `.leavesNothingBurningInsideRock` |
| **A blast's fire is on nobody's side, as the blast was.** The side is now a declaration rather than a property of the mechanic: venom belongs to the frog that spat it, and fire belongs to nobody, because a throw that could not catch the thrower is a free button (G0018, G0035). | enforced - `FireTest.burnsWhoeverWalksIntoItWhicheverSideHeIsOn`, `.putsAManStandingInItOnTheGroundRatherThanKillingHim`, `SlickTest.leavesTheSideThatMadeItAlone` |
| **Being in the way and being worth going round are two questions.** A unit steers around whatever its collider says to, decided where the thing was spawned and defaulting to solid: burning ground pushes nobody and is given room, and a mine is solid and is deliberately not, because the whole of what a mine is is something the other side walks into. Avoidance still asks no question about what anything is. | enforced - `FireTest.leansOffBurningGroundAsReadilyAsOffARock`, `MineTest.killsWhateverWalkedIntoIt`, `AvoidanceSystemTest.leansOffALineThatWouldRunIntoSomethingLongBeforeItGetsThere` |
| **A circle that changes size is dangerous exactly where it is drawn, at every instant.** The body is the drawing, ramps included, so fire that is still swelling has not reached you and fire that is collapsing has let go - which is G0034's "drawn and dangerous are the same interval" asked of something that moves. Inside is still decisive and outside still nothing: a ramp changes the circle, never what being in it costs (pillar 1). | enforced - `FireTest.swellsAndThenCollapsesRatherThanBeingOneCircleThroughout` |
| **Only the other side arms a mine.** A commander walking over one and a trooper standing on one do nothing at all, which is what lets a level lay a field of them in front of the men who have to cross it - and arming latches, so a fuse cannot be walked away from. What it still costs him is that the blast is on nobody's side like every other. | enforced - `MineTest.liesThereWhileTheCommanderStandsOnIt`, `.liesThereWhileATrooperStandsOnIt`, `.goesOnTickingAfterWhateverSetItOffIsGone`, `.catchesTheCommanderStandingBesideOneAsReadilyAsAnEnemy` |
| A mine explodes by **dying**, through the one death reaction (G0018), so being shot, being caught in somebody else's blast and running out of fuse all end in the same circle - and a mine beside a mine chains with no rule anywhere saying it should. It is hauled by the same hands and the same component that haul a barrel. | enforced - `MineTest.goesOffOnceItsFuseHasRunOut`, `.goesUpWithAnythingThatExplodesBesideIt`, `.haulsAMineAsReadilyAsABarrel` |
| A blast catches what is standing in it on the tick it goes off, and nothing that walks in afterwards. Running through the tail of one is safe. | enforced - `BlastTest.catchesWhatWasStandingInItAndNothingThatWalksInAfterwards` |
| Anything that explodes does so by **dying**, through one death reaction (G0018). There is no second path to a blast. | enforced - `BarrelTest.goesOffWhenItIsShot`, `.goesOffWhenAnAlienBitesIntoIt`, `GrenadeTest.travelsItsFullRangeWhenNothingIsInTheWay` |
| A grenade comes off rock, the arena edge and scenery that never moves, and passes through anything alive (G0019). | enforced - `GrenadeTest.passesThroughAnEnemyInsteadOfComingOffIt`, `.comesOffAnObstacle`, `.passesThroughABodyInItsThroes` |
| **A shot that pierces is stopped by scenery alone** and passes through anything alive - the same rule a thrown grenade has followed since G0019, asked of a bullet. Rock is not in it and needs no clause: a shot flying into rock is thrown away by the system that asks nothing about what kind of shot it is. Whether a gun pierces is one number on the gun, zero on every other. | enforced - `PierceTest.killsBothOfTwoStandingInOneLine`, `.goesOnFlyingAfterItHasPassedThroughSomething`, `.stopsAtAnObstacleAndLeavesWhatIsBehindItStanding`, `.stopsAtRock`, `.leavesAnOrdinaryShotStoppingAtTheFirstThingItTouches` |
| A piercing shot lands **one blow per body**, not one per tick it overlaps one. | enforced - `PierceTest.landsOneBlowOnOneBodyRatherThanOneEveryTick` |
| A grenade travels the same total distance whatever it bounces off (G0019). | enforced - `GrenadeTest.spendsTheSameFlightWhetherItBouncesOrNot`, `.travelsItsFullRangeWhenNothingIsInTheWay` |
| A grenade is thrown along the aim **direction** and never to the aim point, so pulling the view back can never reach further (G0019). | enforced - `GrenadeTest.throwsAlongTheAimDirectionRatherThanAtTheAimPoint` |
| The simulation never moves the camera. It publishes what happened and how violent it was; what the view does about it is the client's. | judgement - `Shake`, the same split `Focus` already makes |
| Whether a blow kills outright or leaves a casualty is declared by the **source**, never derived from the amount (G0020). | enforced - `CasualtyTest.leavesATrooperShotDownOnTheGroundRatherThanDead`, `.killsATrooperOutrightWhenTheBlowIsOneThatKills` |
| Gunfire and blasts leave a casualty; claws, a sweep and a leap kill. That asymmetry is what makes distance worth keeping. | enforced - the two rows' tests, plus `CombatTest.killsTheCommanderTheMomentAnEnemyTouchesIt` |
| A casualty is at zero health, so anything that reaches him finishes him. Being shot while down and being eaten while down are the same rule, and neither is a special case. | enforced - `CasualtyTest.finishesACasualtyWithTheNextHitThatReachesHimWhateverItIs` |
| Nothing on the alien side can be a casualty. Being rescuable requires somebody on your side who would come. | enforced - `CasualtyTest.killsAnAlienOutrightHoweverItWasHurt` |
| **The commander is one of his men in this too**: gunfire and blasts put him on the ground, claws and sweeps and leaps still kill him outright (G0023). | enforced - `CasualtyTest.leavesTheCommanderShotDownOnTheGroundRatherThanDead`, `.killsTheCommanderOutrightWhenTheBlowIsOneThatKills` |
| A man is put back on his feet by one path, whoever reached him. How long it takes, what he gets back and how long he is protected are facts about him, not about his rescuer. | judgement - `Aid` is the single writer, used by `ActionSystem` and `AidSystem` |
| **How long he has on the ground is a fact about him too.** The commander's bleed-out is his own number and shorter than the squad's: what a player sits out is the bleed-out plus the throes plus the respawn, and three windows sized for a trooper added up to most of a fight. Shortening the shared one would have shortened every rescue on the field, which is a different decision about a different mechanic. | enforced - `CasualtyTest.countsTheCommanderDownOnHisOwnClockRatherThanHisMens`; one reader - `Spawner.spawnCommander` |
| A trooper will leave the level's objectives to fetch somebody, will not leave a standing order to, and will never leave a fight. | enforced - `AidTest.holdsWhereItWasToldToRatherThanGoingToFetchSomebody`, `.neverBreaksOffAFightToFetchSomebody`, `.putsAFallenComradeBackOnHisFeet` |
| Whether a trooper goes at all is a roll, taken once on the way in and never re-taken while it is on its way. Nobody is owed a rescue. | enforced - `AidTest.leavesHimWhereHeLiesWhenNobodyIsWilling`, `.walksAcrossToAManWhoIsDownOutOfReach` |
| A casualty is out of the fight entirely - not driven, not shooting, not holding ground - and is visibly distinct from a corpse. | enforced - `CasualtyTest.leavesACasualtyOutOfTheFight`, `.showsACasualtyAsOneAndCountsHisBleedOutDownWhereThePlayerCanSeeIt` |
| A bleed-out nobody answered leaves exactly the corpse a bullet would have. There is one death path. | enforced - `CasualtyTest.killsACasualtyNobodyReachedInTime`; `Deaths.kill` is the single writer |
| The action key does whatever is within arm's length, nearest first; the player never selects (G0021). **Two things are now on that list**, so nearest is doing real work: a tie goes to the gun, because getting on one is the reversible half of the pair. | enforced - `ActionTest.offersToPickUpAManWhoIsDownBesideHim`, `.offersNothingForAManWhoIsDownOutOfReach`, `.stopsOfferingWhenHeWalksOutOfReach`, `TurretTest.offersTheTurretToAManStandingBesideIt` |
| **A man aboard a station is not on the field.** Nothing drives him, nothing shoves, shoots or claws him, and the gun is the only thing anything can hit - but a blast still reaches him, because a blast is a radius test over everything that can be hurt rather than a contact (G0018, G0024). | enforced - `TurretTest.drivesNobodyWhoIsOnATurret`, `.catchesTheCrewInTheBlastWhenItGoesUp` |
| A trooper crews a gun only with something already inside that gun's reach, and gets off once there has been nothing in it for long enough. Where a gun ranks is fight, order, casualty, **gun**, objective. **A man walking to a gun drops that walk the moment the gun stops being worth it** - somebody else got there first, or there is nothing left in reach - rather than finishing the journey and standing over it. | enforced - `TrooperCrewTest.goesForAFreeGunWhenThereIsSomethingItCouldShootFromIt`, `.willNotGoForOneWithNothingToShootAtFromIt`, `.getsOffAgainOnceThereHasBeenNothingToShootAtForAWhile`, `.staysOnItWhileSomethingIsStillInRange`, `.goesBackToWhatItWasDoingWhenSomebodyElseGetsTheGunFirst` |
| A crew of its own judgement fires only when the gun has come round; the commander fires wherever his is pointing. A squad may not spray the arena, and his aim is his to get wrong. | judgement - `TurretSystem.ON_TARGET`; `TrooperCrewTest.firesAtSomethingWellBeyondItsOwnSight` holds the half that is a rule |
| A gun on a mount fires where **it** is pointing, never where its crew is pointing, and comes round slower while the trigger is down. Tracking and firing are two decisions. | enforced - `TurretTest.swingsOntoANewBearingRatherThanSnappingToIt`, `.turnsSlowerWhileItIsFiring` |
| **Heat belongs to the gun and to whoever is holding it, never to the man who was holding it.** A turret cools at the same rate empty as manned, so stepping off a hot gun and back on is not a way of putting the bar back (G0025). | enforced - `OverheatTest.goesOnCoolingWhileNobodyIsOnIt`, `.keepsWhatItHadWhenSomebodyGetsBackOn` |
| A gun that has stopped itself stays stopped until its heat has fallen well below full, not merely below the top. | enforced - `OverheatTest.staysLockedOutUntilItHasCooledWellBelowFull`, `.stopsFiringOnceItIsFull` |
| Every shooter asks the same two questions before firing - is the gun off cooldown, and has it stopped itself - and pays the same two costs afterwards. | judgement - `Guns.ready` and `Guns.spend` are the single writers, used by the commander, a shooting alien and a turret |
| A station is named by an issued id, never by an entity reference, and which station is crewed is derived from the riders every tick rather than latched (G0010, G0024). | judgement - `Turret.id` and `Crew.station`; `TurretSystem` clears every `crewed` flag before any rider claims one |
| Which muzzle a shot leaves from is a property of the gun. A twin mount is a number in a `WeaponDef`, and every shooter in the game goes through the one place that answers it. | judgement - `Muzzle.next` is the single writer, used by the commander, by a shooting alien and by a turret |
| Stabilising is a commitment, not a total: letting go loses the work rather than banking it. | enforced - `ActionTest.losesTheWorkDoneSoFarWhenHeLetsGo` |
| Work being done on a man is drawn in a colour of its own; every other thing that fills up is the body's colour brightened. Two circles that ask for opposite reactions must not read alike. | enforced - `ActionTest.drawsTheWorkInItsOwnColourAndTheBleedOutInTheOrdinaryOne`, `.stopsColouringHimGreenTheMomentHeLetsGo` |
| Only something with nothing else driving it can be hauled - a man who is down, a barrel, or a turret nobody is on. A trooper on his feet cannot be picked up, and neither can a gun somebody is firing. | enforced - `DragTest.willNotTakeHoldOfAManStillOnHisFeet`, `.haulsABarrelAsReadilyAsAMan`, `TurretTest.willNotTakeHoldOfATurretSomebodyIsOnIt`, `.haulsAnEmptyTurretAsReadilyAsABarrel` |
| A drag names its load by an issued id, never by an entity reference (G0010, G0016). | judgement - `Drag.load` and `Dragged.by`; a recycled component resets to zero, so a stale id can never match |
| Hauling costs a hand: no firing, no sweep, no throw while something is in them. | enforced - `DragTest.leavesHimNoHandForTheGun` |
| A man hauling something can only back away from it, with a little sideways lean, and slower than he walks. | enforced - `DragTest.barelyMovesAtAllWhenHePressesStraightAtTheManHeIsDragging`, `.haulsHimSlowerThanTheCommanderWalks` |
| A load is moved by writing its velocity, never its position, so terrain still has the last word about where it may go. | enforced - `DragTest.putsABarrelBackToSceneryWhenHeLetsGoOfIt` holds the half that can drift; `DragSystem` never writes a position |
| A spitter fires at what it is hunting, inside a cone, and never through rock. | enforced - `SpitterTest.firesOnceWhatItIsHuntingComesInsideItsRange`, `.keepsEveryShotInsideTheConeItDeclares`, `.willNotFireThroughRock` |
| **A trooper never fires through rock, including at something it is already fighting.** Staying in a fight forgives the arc, because where a man is looking is his own doing and changes twice a second; it never forgives the wall, because rock is not his doing and does not move. | enforced - `TrooperCombatTest.doesNotShootThroughRock`, `.stopsFightingSomethingThatStepsBehindRock`, `.keepsFightingSomethingThatSlipsOutOfItsArc` |
| **An alien routes to whoever on the player's side is nearest by route**, not to the commander alone: one field per strain, flooded from every one of them at once, which costs what flooding from one costs (G0026, amended). | enforced - `NavigationSystemTest.walksAnEnemyRoundAWallToATrooperJustAsReadily`, `FlowFieldTest.leadsToWhicheverGoalIsNearestByRoute` |
| A cone is applied by turning the shot, so every shot is the same length and the cone the same width at every distance. | enforced - `SpitterTest.sendsEveryShotOutAtTheSameSpeedHoweverFarOffTrueItIs` |
| Every gameplay speed is one block in `game.properties` and is retuned together, so the ratios between units survive a pace change. | judgement - the PACE block |
| Everything the AI does only *sometimes* is a named constant in `AiChance` with a number in `game.properties`, stated per second of opportunity. There is no conditional behaviour that is not on that list. | judgement - `AiChance` is the index; `Odds` is the only caller of the dice |
| A behaviour switched off draws no random number at all, so turning one off cannot reshuffle what every other behaviour rolls. | enforced - `OddsTest.drawsNothingAtAllForABehaviourThatIsSwitchedOff` |
| A creature's claws reach into an arc in front of it, and **which way it is going is which way it is facing** - the same derivation the renderer makes, so a thing drawn walking away cannot bite you. Something standing still reaches all round, because it can turn on the spot. | enforced - `CombatTest.landsNoBlowOnWhatIsBehindIt`, `.landsOneOnWhatItIsWalkingAt`, `.reachesAllRoundWhileItIsNotGoingAnywhere` |
| **A man who is off his feet is driven by nobody, the player included.** Every driver asks one question in one place; a private copy of it is how a commander shot off his feet went on walking and firing for four days. | enforced - `CasualtyTest.leavesACommanderWhoIsDownWhereHeFell`, `.takesNoShotFromACommanderWhoIsDown`; one writer - `Drivers.held` |
| A shooter ambles inside its patch **once it is in position** - and only then, because everywhere else it has somewhere to be and the system that drives it there runs first. With nothing driving its legs it reads as a turret rather than a creature. | enforced - `SpitterTest.goesOnAmblingAboutWhileItShoots`, `.neverAmblesFurtherThanItsOwnPatch` |
| A pickup that would go off is taken only on a roll, and only while the two are actually touching (G0022). | enforced - `BarrelTest.isLeftStandingByAnAlienWithNoAppetiteForOne`, `.goesOffWhenAnAlienBitesIntoIt` |
| How big a barrel's blast is depends on what set it off: a shot leaves the full circle, a bite a far smaller one, and a swallow none at all. Still one reaction and one death (G0018, G0022). | enforced - `BarrelTest.leavesASmallerCircleWhenItIsBittenIntoThanWhenItIsShot`, `.catchesTheSameBystanderWhenItIsShotInstead`, `SpitterTest.swallowsABarrelAndFiresShotsThatGoOff` |
| **A shooter moves for somewhere to be, never for somebody to follow** - amended 2026-09-17, twice. It answers a provocation, because that is a place and not a target (G0016); it crosses the gap between noticing you and being able to fight you, once; and from the moment it has the ground it wanted it holds that ground however far you then go. Refusing to move at all left it standing and watching, and left the point's rouse radius dead against the one strain a level most needs it for. | enforced - `SpitterTest.comesToAPointBeingTakenOffIt`, `.movesUpToItsFiringRangeAndNoFurther`, `.neverFollowsOnceItIsInPosition` |
| It walks to the distance it **shoots best from**, not to the edge of what it can reach - stopping at the edge leaves it ambling in and out of its own range between shots. | enforced - `SpitterTest.walksToTheDistanceItShootsBestFrom` |
| Getting out of a shooter's way is not getting away. Losing its range costs it the cone it had walked in; only losing it altogether lets it take a position again. | enforced - `SpitterTest.takesAPositionAgainOnceItHasLostHimAltogether`, `RangedTest.goesBackToTheWildConeWhenItLosesWhatItWasShootingAt` |
| A shooter's facing **is** its aim, and with nothing to shoot at the aim follows where it is going - so one crossing the ground to eat something faces the thing it is going to eat. A stale aim on a walking creature reads as walking backwards. | enforced - `SpitterTest.facesTheThingItIsWalkingOverToEat`, `.swingsOntoItAndThenFires` |
| A creature carrying a gun that goes off where its shots land is drawn as carrying one. The shots alone are a tell you learn by being caught in one. | enforced - `SpitterTest.showsThatItIsCarryingABarrel` |
| A shooter fires only into the field of view it declares, and its aim swings onto a target at a rate rather than snapping. | enforced - `SpitterTest.willNotFireAtSomethingOutsideItsFieldOfView`, `.swingsOntoItAndThenFires` |
| A shooter's cone narrows with every shot it lands on the same target and goes back to its widest the moment it loses one. | enforced - `RangedTest.narrowsTheConeWithEveryShotItLands`, `.stopsNarrowingOnceItsAimIsWalkedIn`, `.goesBackToTheWildConeWhenItLosesWhatItWasShootingAt` |
| **What one pull of a trigger puts in the air is a property of the gun, not of who is holding it.** A gun that names several pellets sends that many from every shooter there is, each turned inside the gun's own cone - which is a different cone from the one a creature's bad aim makes, and composes with it rather than replacing it. One place decides it, and the four things that fire all go through it. | enforced - `VolleyTest.sendsAsManyShotsAsTheGunNamesFromOnePull`, `.sendsOneFromAGunThatNamesOne`, `.throwsEveryPelletStraightForAGunWithNoConeOfItsOwn`, `.scattersThemInsideTheConeForAGunThatNamesOne`, `.sendsEveryPelletOutAtTheSameSpeed`; one writer - `Guns.volley` |
| **A gun may have a reach; an aim may not.** How far a shot flies before it goes out is a number on the weapon, exactly as a grenade's throw has been since G0019 - so a short-ranged gun is possible without the aim ever becoming a distance. Zero means until it hits something, which is what almost every gun declares. | enforced - `VolleyTest.dropsAShotOnceItHasFlownAsFarAsItsGunAllows`, `.leavesAShotFromAGunWithNoReachFlying`, `.carriesItTheWholeOfTheReachBeforeItGoesOut`, and `CommanderControlSystemTest.firesInTheSameDirectionHoweverFarAwayTheAimPointIs` still holds the aim half |
| **A nest sleeps until somebody comes near, and never sleeps again.** A level larger than the view cannot have a producer working from the first tick - the player would meet the accumulation rather than the nest - and a producer that stopped when he backed off would be one he could wait out from just outside its radius. | enforced - `NestTest.sendsOutNothingAtAllUntilSomebodyComesNear`, `.startsWorkingOnceSomebodyComesNear`, `.goesOnWorkingAfterWhoeverWokeItHasGone` |
| **A nest is at its worst the moment you kill it**: destroying one empties a last brood out of it and rouses everything within earshot. That is what makes taking one away a decision about *when* rather than a chore, and it is the same death-reaction window a blast and a respawn run in. | enforced - `NestTest.emptiesTheLastOfItselfWhenItIsDestroyed`, `.sendsOutWhateverStrainItWasBuiltFor`, `.sendsEverythingWithinEarshotToSeeWhoIsHittingIt` |
| A level is never clear while something on it is still producing. A nest carries the same marker every other hostile thing carries, so it counts - the alternative is an objective that reads as met and unmeets itself every half minute. | enforced - `NestTest.leavesALevelUnclearedWhileItIsStillStanding` |
| Being provoked towards a place is one mechanism, wherever the shout came from - a point being taken, or a nest being hit. Loud once per event, never every tick (G0016). | judgement - `Rouse.within` is the single writer, used by `ControlPointSystem` and `NestSystem` |
| A gun that names a blast radius leaves one where its shot stops, through the one death reaction everything else explodes through (G0018). | enforced - `SpitterTest.swallowsABarrelAndFiresShotsThatGoOff` |

The no-hitscan rows are load-bearing in three directions at once: they keep collision uniform, keep
the simulation deterministic, and keep every threat visible to the player. They are the rows most
likely to be argued away for convenience - the melee one was very nearly argued away the day it was
written, which is why G0005 exists.

**A blow that kills and a blow that is survived are two different events**, and the three rows above
are the whole of the difference. Killing something tangles you up with it: neither of you moves, the
body stops where it was struck, and you are committed to the moment. Failing to kill it shoves it
away: it staggers, you do not, and you are free to follow up. That asymmetry is the reward for the
parry, and collapsing it back into one rule - locking both parties on every landed blow, as the first
version did - quietly makes every successful defence cost you a second you did not spend.

The grapple row is a **deliberate, narrow exception to pillar 5**, and the only one in the game. For
as long as a *fatal* lock holds, the two units in it are drawn identically whether they won or lost,
so the player does not know which of them is getting back up until it ends. A survived blow is not
hidden at all: the one that took it reads as staggered and the one that landed it reads as normal. It is written down here rather
than left as a quirk of `AppearanceSystem` precisely because it looks like a bug: everything else in
the game says state must be visible, and this says one thing is hidden, for one second, on purpose.
It stays legitimate only while it is that narrow - a second hidden state would not be a beat, it
would be the game lying to the player.

The lock and stun rows are what make a fight an exchange rather than a sequence of hits, and they are
easy to break by accident: an "obvious" tidy-up that stops a stunned unit being pushed, or that zeroes
a stunned unit's velocity in its driver rather than leaving it to `StunSystem`, breaks knockback
without breaking any other test.

**A gun is found and lost, never owned.** Picking one up replaces what you were carrying outright and
dying gives you the sidearm back. Slots you switch between were considered and rejected: they need a
binding, and the input map below has nothing free - the melee strike took right mouse and the squad
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

**A reinforcement is won where the fighting is.** One lies beside every control point, which is the
only placement that makes pillar 3 a place on the map rather than a rule in a document: the ground
the level already asks you to walk into and hold is the ground the next man is standing on. Scattering
them loose would have made them litter, and putting them in the spawn area would have made them a
free top-up. Taking one puts a man down beside the commander at once - there is no timer, no drop ship
and nothing to defend, because a mechanic that makes the player stop and wait is pillar 1's problem.

## Input map

One key, one action. **Adding a binding means editing this table in the same change** - this is the
cheapest guard against two features silently claiming the same input.

*Built* rows work today. *Settled* rows are fixed by a decision but not yet wired up; *proposed*
rows are still a guess.

**Both columns are live.** The keyboard is player one's and no pad takes it in the alpha (1.a); every
other player is a pad. Every binding below is in [`assets/controls.properties`](../../assets/controls.properties)
and can be changed there without touching code.

Pad buttons are named by **where they are**, never by what is printed on them: `SOUTH` is A on an Xbox
pad and Cross on a PlayStation one, `WEST` is X and Square, `EAST` is B and Circle. The Xbox and
PlayStation names below are the two label sets for one binding, not two bindings (ADR 0011).

The alpha-phase harness has its own keys - pause, single-step, restart - listed in
[`alpha-phase.md`](alpha-phase.md). Those are dev controls and deliberately kept out of this table;
do not treat one as taken when adding a game binding. None of them is on the pad.

| Keyboard | Pad (Xbox / PlayStation) | Action | Status |
|---|---|---|---|
| `WASD`, arrow keys | Left stick | Move the commander | built |
| Mouse | Right stick | Aim | built - G0002. The stick sends a direction; the mouse sends a place |
| Left mouse | `X` / `Square`, or `RT` / `R2` | Fire | built |
| Right mouse | `LT` / `L2` | Melee strike | built |
| `Q` | `B` / `Circle` | Throw a grenade | built - G0019 |
| `E` | `A` / `Cross` | Act on what is in reach - stabilise a casualty, get on or off a turret | built - G0021, G0024 |
| `F` | `RB` / `R1` | Take hold of a load, or let go of it | built - G0021 |
| `1` | D-pad up | Squad order: follow me | built - scaffolding, see below |
| `2` | D-pad right | Squad order: forward | built - scaffolding, see below |
| `3` | D-pad down | Squad order: defence | built - scaffolding, see below |
| `4` | D-pad left | Squad order: cancel | built - scaffolding, see below |
| `Tab` | `LB` / `L1` | Pull the view back for a look, and put it back. **One camera, so anybody's press moves everybody's view** | built |
| - | `Start` / `Options` | Pause | built (pad only; `Esc` is the harness's, and `P` pauses there) |
| - | - | Unbound on the pad: `Y`/`Triangle`, `Back`/`Share`, both stick clicks. Left free on purpose | - |

Plugging a pad in **is** joining, and unplugging is not leaving (G0028). There is no binding for
either and there is not going to be one.

The four order keys, and the d-pad that mirrors them, are **alpha-phase scaffolding, not a
decision**. The command *model* is settled -
an order reaches the troopers near the commander and names a verb, never a target (G0017) - but the
input is not: the intended binding is one button plus a radial menu, which needs interface work that
does not exist yet. Four keys make the verbs playable in the meantime. They are listed here rather
than with the harness keys because they drive the game rather than the dev loop, so nothing else may
claim them.

## Open questions

Deliberately undecided. **Ask rather than inventing an answer** - a guess here becomes a
contradiction three features later.

- **What does the view do with a player it cannot frame?** The leash was tried at the width of the
  view, which is what pillar 4 asks for, and it played badly - being stopped by an invisible wall
  because somebody else walked the other way is not a fight, it is a tether. It is now far wider than
  the screen and is a backstop rather than a frame, so **a player can be off screen**, which pillar 4
  says should not happen. That is a knowing gap, and the question it leaves is which of the answers
  fills it: the camera giving up at far zoom and a marker at the edge pointing at whoever is missing;
  splitting the view; or a soft pull that only starts once somebody is actually out of shot. None is
  chosen, and a marker is the cheapest thing to try first.

The alpha phase answers none of these. Where a system needs one of them to have an answer it uses
scaffolding instead, listed in [`alpha-phase.md`](alpha-phase.md) so the scaffolding is not later
mistaken for a decision.

- ~~How does the player command the squad?~~ **Answered 2026-09-10 (G0017):** an order reaches the
  troopers near the commander, names a verb and never a target, outranks the level's objectives and
  never an ongoing fight. Four verbs: follow me, forward, defence, cancel - the middle two renamed
  from *move out* and *stop* on 2026-09-13, because what a squad does when it stops is hold a place. The **input is still open** -
  keys `1`-`4` are scaffolding and the intended binding is a button plus a radial menu.
- A body is scenery for its throes - solid, immovable, and it stops bullets. That is realistic and it
  is what makes the clinch settle at touching distance, but it also means shooting into a crowd now
  leaves obstacles for a second at a time. Whether that reads as good feedback or as clutter is a
  question for play, not for a test.
- ~~Should an alien ever aim at a trooper?~~ **Answered 2026-09-18 (G0026):** yes - it comes for
  whoever on the player's side is nearest and still on his feet, the commander included, and a gun a
  level put down is on that list too. It is not a second targeting concern but the same one asked
  about a longer list, in the one place that already answered it. What it costs is that his death no
  longer empties the field.
- ~~Who picks the commander up?~~ **Answered 2026-09-13 (G0023):** his troopers do, sometimes. He is
  a casualty like his men, gunfire and blasts put him on the ground, and a trooper with nothing more
  urgent in front of it rolls for whether to come - far more readily for him than for one of its own.
  The co-op half is now live and **still unanswered**: a second player can reach a downed commander
  with the same action key his troopers use, which works today because a commander is a casualty like
  any other. What nobody has decided is whether a trooper should still bother when another *player* is
  closer - a squad and a friend racing for the same body is either the best moment in the game or a
  waste of a trooper, and only play will say which.
- **Should noise pull aggro?** Detection is a radius around each unit and nothing the player does
  changes it, so firing a gun in a corridor wakes nobody who could not already have noticed you. A
  shot that wakes things beyond the detection radius is a big arcade lever and it is cheap to add now
  and awkward to retrofit later, but it is a decision about how loud the game wants to be.
- **Can a rabbit be killed with the hand strike?** Only a projectile reaches the damage path through
  a pickup, so a sweep passes through one today. Denial at range is the interesting half and it works;
  whether denial in your face should also work is undecided, and it is a rule about what a melee
  weapon is for rather than a gap in the rabbit.
- **Where do rabbits come from?** A scenario spawns a handful and that is all there ever are. Whether
  a level breeds them, whether running out matters, and whether the aliens are meant to clear them
  before you can get to them are all still open - and they decide whether the rabbit is a resource or
  a set piece.
- ~~What ends a level - an objective, an extraction point, elimination, or a timer?~~ **Answered
  2026-09-10:** an objective, and a level may name more than one. Two exist - clear the map of
  enemies, and capture every control point - and a level requires **all** of the set it names.
- **What happens when a level's objectives are met?** The alpha deliberately does nothing: the level
  entity knows, and nothing reads it. Extraction, an immediate cut, a lull before the next wave and a
  score screen are all still open, and this is now the question standing between the alpha and a
  playable level.
- **Should a trooper ever break off a fight?** It stands still and shoots until whatever it noticed
  is dead or out of range (1.d), which is exactly right for an arcade squad and exactly wrong if the
  thing shooting back is a brute. Retreating, repositioning and taking cover are all unaddressed, and
  the first of them that gets added needs this answered rather than guessed.
- **Can the other side take a control point back?** Capture is one-way today. Whether an alien can
  stand on a point and undo it is a question about what the aliens are *trying to do*, which nothing
  in the game has yet said.
- The source game had four commanders because it was four-player co-op, which this now is (G0028).
  **Are the four the same man in four colours, or four commanders with distinct kits?** Today they are
  identical but for colour. Distinct kits would give a reason to pick one and a reason to want a
  second player, and it is the question standing between "co-op works" and "co-op is worth doing".
  In single-player it is the same question asked once.
- Does the squad persist between levels, or does each level grant a fresh one?
- On failure, do you retry the level, or is the run over?
- Given "no between-level management", what does progression across the nine levels actually consist
  of - only what you carry out of a level?
- **A one-tile gap takes grunts and refuses brutes**, because a tile is 5 world units and a brute is
  5.2 across. That fell out of the tile size rather than being designed, and it is a real tactical
  rule the player would learn: a crack you can back into is a crack the heavy thing cannot follow you
  through. Keep it as a feature, or make a tile at least as wide as the widest body so a gap admits
  everything and corridors are measured only in whole tiles? Deciding to keep it means unit sizes and
  tile size are now coupled, and a future unit larger than a tile needs this answered again.
- What does alien food do for the commander? Mushrooms and flowers currently offer him nothing, so he
  walks over them. Denial by eating them, a use of his own, or nothing at all are all still open - and
  "nothing at all" is a real answer, not a gap.
- Can a pickup be destroyed? Shooting a mushroom would make denial something the player can do at
  range rather than only by standing on it, and would change how a feeding ground reads. Nothing
  supports it today.
- ~~What happens to a reinforcement?~~ **Answered 2026-09-18:** it becomes a trooper, put down beside
  the commander, up to a ceiling of the starting squad plus its configured headroom. Crates are laid
  beside the control points, so the squad you fight with is the reward for taking ground. Still open
  underneath it: whether the ceiling should be per level or carried between them, which is the same
  question as whether the squad persists between levels.
