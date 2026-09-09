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
| Every concern draws random numbers from its own stream, derived from the one level seed. | enforced - `RngTest.streamsForDifferentConcernsDiverge` |
| A unit's lose range is never shorter than its detection range, so a commander on the boundary cannot flicker a pack between hunting and idling. | enforced - `PerceptionDefTest.refusesALoseRangeShorterThanTheDetectionRange` |
| A unit that has not noticed the commander is drawn differently from one that has. | enforced - `AppearanceSystemTest.showsAUnitThatHasNotNoticedHimAsUnaware` |
| A unit that has noticed nobody stays in its patch, and walks back to it if it finds itself outside. | enforced - `WanderSystemTest.keepsAnIdleUnitNearWhereItWasSpawned`, `.walksBackToItsSpawnPointWhenItFindsItselfAwayFromIt` |
| A unit leans off what it is about to walk into before it touches it; discovering an obstacle by collision is a failure. | enforced - `AvoidanceSystemTest.leansOffALineThatWouldRunIntoSomethingLongBeforeItGetsThere` |
| Nothing steers around what it is hunting, and nothing steers around a pickup. | enforced - `AvoidanceSystemTest.neverAvoidsTheThingItIsHunting`; judgement for the pickup half - `AvoidanceSystem` skips them, or a hungry alien would walk round its dinner |
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
| Nothing is spawned inside rock, and nothing is spawned anywhere the commander cannot reach. | enforced - `ScenariosTest.spawnsNothingInsideRock`, `ScenariosTest.leavesEverySpawnReachableFromTheCommander` |
| The commander is an ordinary entity - killable, collidable, standing on the field. | enforced - `CombatTest.killsTheCommanderTheMomentAnEnemyTouchesIt` |
| The commander comes back while any trooper still lives. | deferred - needs squad; with no troopers it always comes back |
| Only `ReaperSystem` removes an entity; anything that kills only marks it `Dead`. | enforced - `ArchitectureTest.onlyTheReaperRemovesEntities` |
| Damage only ever crosses faction lines, never stays inside one. | enforced - `CombatTest.neverLetsEnemiesHurtEachOther` |
| Invulnerability is reachable from the harness only, never from a scenario or config. | enforced - `ArchitectureTest.scenariosCannotSwitchOnGodMode` |
| Troopers lost during a level stay lost for that level. | deferred - needs squad |
| Squad size never exceeds its configured maximum. | deferred - needs squad |
| Every projectile is a simulated entity with a `Position` and a `Body`. No hitscan. | enforced - `SpawnerTest.givesEveryProjectileAPositionAndABody` |
| Every melee strike is a simulated entity with a `Position` and a `Body`. No melee hitscan (G0005). | enforced - `SpawnerTest.givesEveryStrikeAPositionAndABodySoThereIsNoMeleeHitscan` |
| A blow that kills holds both the attacker and its victim - that is the clinch. A melee kill does so only when its victim had already committed to attacking; a swat on something merely walking holds only the corpse (G0013). | enforced - `StunTest.holdsBothTheAttackerAndItsVictimWhenTheBlowKills`, `StrikeCombatTest.tanglesHimUpWhenHisSweepKillsSomethingMidLeap`, `.leavesHimFreeWhenHisSweepKillsSomethingThatWasNotComingAtHim` |
| A lethal exchange ends with the two of them touching, whatever distance the blow was struck at - and the body gets there under its own momentum, shoving the survivor back, rather than being placed (G0014). | enforced - `StrikeCombatTest.endsALethalExchangeWithTheTwoOfThemTouching`, `.shovesHimBackAsTheBodyArrives` |
| A clinch's carry must cross the longest gap a sweep can leave. A config where it stops short is refused rather than shipped. | enforced - `SimConfigTest.refusesAClinchCarryThatWouldStopShortOfTheCommander` |
| A strain that does not lose interest keeps hunting him however far he gets. | enforced - `PerceptionSystemTest.keepsHuntingHimForeverWhenItNeverLosesInterest` |
| Him going down releases everything hunting him, whatever its switches say. Nothing stands to attention over a corpse. | enforced - `PerceptionSystemTest.letsGoOfHimWhenHeGoesDownEvenThoughItNeverGivesUp`, `.losesHimWhenHeGoesDown` |
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
| The view never shows anything past the edge of the map, and centres on a level smaller than itself. | enforced - `CameraDirectorTest.keepsTheViewInsideTheMap`, `.centresOnAMapSmallerThanTheView` |
| **Aim is a direction, never a distance.** Zoom is a rendering setting and cannot become extra range. | enforced - `CommanderControlSystemTest.firesInTheSameDirectionHoweverFarAwayTheAimPointIs` |
| The view is told who to watch by a component; the client never asks which entity is the commander. | judgement - `Focus`; local co-op is two markers and no client change |
| The camera feels the same at every frame rate. | enforced - `CameraDirectorTest.movesTheSameDistanceWhateverTheFrameRate` |
| What may be spawned on a patch of ground is decided by its terrain. | enforced - `ScatterTest.keepsEverythingOffGroundItWasNotOfferedTo` |
| Nothing placed by the grid stands at a tile centre. | enforced - `ScatterTest.standsThingsInsideTheirTilesRatherThanAtTheCentre` |
| A placement routine reports what it could not place rather than silently placing fewer. | enforced - `ScatterTest.reportsWhatItCouldNotPlace` |

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

**Both halves of a shared item are the point.** A pickup carries a grant per side, and either may be
nothing. That is what makes a frozen trooper a race rather than a collectable: reach it and it is a
reinforcement, lose it and the thing chasing you is one mouthful closer to being a brute. An item
that offers a side nothing is not consumed by that side, so denial has to be earned rather than
handed over.

## Input map

One key, one action. **Adding a binding means editing this table in the same change** - this is the
cheapest guard against two features silently claiming the same input.

*Built* rows work today. *Settled* rows are fixed by a decision but not yet wired up; *proposed*
rows are still a guess. Gamepad support is not built at all - the stick and trigger columns are the
intended bindings, not working ones.

The alpha-phase harness has its own keys - pause, single-step, restart - listed in
[`alpha-phase.md`](alpha-phase.md). Those are dev controls and deliberately kept out of this table;
do not treat one as taken when adding a game binding.

| Input | Action | Status |
|---|---|---|
| `WASD`, arrow keys / left stick | Move the commander | built (keyboard) |
| Mouse / right stick | Aim | built (mouse) - G0002 |
| Left mouse / right trigger | Fire | built (mouse) |
| Right mouse / left bumper | Melee strike | built (mouse) |
| *(unassigned)* | Squad command (contextual) | proposed - had right mouse, lost it to the strike |
| `Tab` | Pull the view back for a look, and put it back | built (keyboard) |
| `Esc` | Pause | proposed |

## Open questions

Deliberately undecided. **Ask rather than inventing an answer** - a guess here becomes a
contradiction three features later.

The alpha phase answers none of these. Where a system needs one of them to have an answer it uses
scaffolding instead, listed in [`alpha-phase.md`](alpha-phase.md) so the scaffolding is not later
mistaken for a decision.

- How does the player command the squad? Auto-follow, point-to-move, formations, or stances? **And
  on what input** - the melee strike took right mouse, which this had provisionally claimed, so
  whatever the command model turns out to be needs a binding of its own.
- A body is scenery for its throes - solid, immovable, and it stops bullets. That is realistic and it
  is what makes the clinch settle at touching distance, but it also means shooting into a crowd now
  leaves obstacles for a second at a time. Whether that reads as good feedback or as clutter is a
  question for play, not for a test.
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
- What ends a level - an objective, an extraction point, elimination, or a timer?
- The source game had four commanders because it was four-player co-op. In single-player, is the
  commander a choice with distinct kits, or one fixed protagonist?
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
- What happens to a reinforcement? It lands on the level entity and is counted; who it becomes needs
  the squad.
