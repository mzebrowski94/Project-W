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
| Everything that can be hit has a `Body`. | enforced - `SpawnerTest.givesEveryUnitABodySoItCanBeHit` |

The no-hitscan row is load-bearing in three directions at once: it keeps collision uniform, keeps
the simulation deterministic, and keeps every threat visible to the player. It is the row most
likely to be argued away for convenience.

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
| Right mouse / left bumper | Squad command (contextual) | proposed - depends on the command model |
| `Esc` | Pause | proposed |

## Open questions

Deliberately undecided. **Ask rather than inventing an answer** - a guess here becomes a
contradiction three features later.

The alpha phase answers none of these. Where a system needs one of them to have an answer it uses
scaffolding instead, listed in [`alpha-phase.md`](alpha-phase.md) so the scaffolding is not later
mistaken for a decision.

- How does the player command the squad? Auto-follow, point-to-move, formations, or stances?
- What ends a level - an objective, an extraction point, elimination, or a timer?
- The source game had four commanders because it was four-player co-op. In single-player, is the
  commander a choice with distinct kits, or one fixed protagonist?
- Does the squad persist between levels, or does each level grant a fresh one?
- Does a weapon pickup replace what you are carrying, or fill a slot?
- On failure, do you retry the level, or is the run over?
- Given "no between-level management", what does progression across the nine levels actually consist
  of - only what you carry out of a level?
