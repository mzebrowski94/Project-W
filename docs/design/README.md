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
| Obstacles and level geometry are ordinary entities with a `Position` and a `Body`. Collision has one path. | enforced - `SpawnerTest.makesAnObstacleAnOrdinaryEntityWithAPositionAndABody` |
| The commander is an ordinary entity - killable, collidable, standing on the field. | enforced - `CombatTest.killsTheCommanderTheMomentAnEnemyTouchesIt` |
| The commander comes back while any trooper still lives. | deferred - needs squad; with no troopers it always comes back |
| Only `ReaperSystem` removes an entity; anything that kills only marks it `Dead`. | enforced - `ArchitectureTest.onlyTheReaperRemovesEntities` |
| Damage only ever crosses faction lines, never stays inside one. | enforced - `CombatTest.neverLetsEnemiesHurtEachOther` |
| Invulnerability is reachable from the harness only, never from a scenario or config. | enforced - `ArchitectureTest.scenariosCannotSwitchOnGodMode` |
| Troopers lost during a level stay lost for that level. | deferred - needs squad |
| Squad size never exceeds its configured maximum. | deferred - needs squad |
| Every projectile is a simulated entity with a `Position` and a `Body`. No hitscan. | enforced - `SpawnerTest.givesEveryProjectileAPositionAndABody` |
| Every melee strike is a simulated entity with a `Position` and a `Body`. No melee hitscan (G0005). | enforced - `SpawnerTest.givesEveryStrikeAPositionAndABodySoThereIsNoMeleeHitscan` |
| A blow that kills holds both the attacker and its victim - that is the clinch. | enforced - `StunTest.holdsBothTheAttackerAndItsVictimWhenTheBlowKills` |
| A blow that is survived holds only the one that took it; the one that landed it stays free. | enforced - `StunTest.holdsOnlyTheVictimWhenTheBlowIsSurvived` |
| A stunned entity acts on nothing, but is still moved and still shoved by the solver. | enforced - `StunTest.stillLetsAHeldUnitBeShovedOutOfTheWay` |
| Only a target that survives a blow is thrown; a killing blow leaves the body where it was struck. | enforced - `StrikeCombatTest.throwsNothingWhenTheBlowKills` |
| A dying body is scenery: solid, and immovable like an obstacle. | enforced - `DeathThroesTest.turnsABodyIntoAnObstacle` |
| Both parties to a *fatal* exchange look the same for its whole duration, winner and loser alike. | enforced - `AppearanceSystemTest.showsALostExchangeAsLockedRatherThanDyingWhileTheLockHolds` |
| A leap is aimed once at launch and never steered afterwards. | enforced - `EnemyLeapSystemTest.keepsGoingWhereItAimedEvenIfTheCommanderMoves` |
| A killing blow is instant; only the removal of the body waits. | enforced - `CombatTest.killsTheCommanderTheMomentAnEnemyTouchesIt` |
| One swing lands at most one blow. | enforced - `StrikeCombatTest.landsAtMostOneBlowPerSwing` |
| Everything that can be hit has a `Body`. | enforced - `SpawnerTest.givesEveryUnitABodySoItCanBeHit` |

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
- What ends a level - an objective, an extraction point, elimination, or a timer?
- The source game had four commanders because it was four-player co-op. In single-player, is the
  commander a choice with distinct kits, or one fixed protagonist?
- Does the squad persist between levels, or does each level grant a fresh one?
- Does a weapon pickup replace what you are carrying, or fill a slot?
- On failure, do you retry the level, or is the run over?
- Given "no between-level management", what does progression across the nine levels actually consist
  of - only what you carry out of a level?
