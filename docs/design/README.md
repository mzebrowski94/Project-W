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
| The commander is an ordinary entity - killable, collidable, standing on the field. | deferred - needs health |
| Losing the commander ends the level. | deferred - needs level lifecycle |
| Troopers lost during a level stay lost for that level. | deferred - needs squad |
| Squad size never exceeds its configured maximum. | deferred - needs squad |
| Every projectile is a simulated entity with a `Position` and a `Body`. No hitscan. | deferred - needs weapons |
| Everything that can be hit has a `Body`. | deferred - needs combat |

The no-hitscan row is load-bearing in three directions at once: it keeps collision uniform, keeps
the simulation deterministic, and keeps every threat visible to the player. It is the row most
likely to be argued away for convenience.

## Input map

One key, one action. **Adding a binding means editing this table in the same change** - this is the
cheapest guard against two features silently claiming the same input.

Proposed, nothing implemented yet:

| Input | Action |
|---|---|
| `WASD` / left stick | Move the commander |
| Mouse / right stick | Aim |
| Left mouse / right trigger | Fire |
| Right mouse / left bumper | Squad command (contextual) |
| `Esc` | Pause |

## Open questions

Deliberately undecided. **Ask rather than inventing an answer** - a guess here becomes a
contradiction three features later.

- How does the player command the squad? Auto-follow, point-to-move, formations, or stances?
- What ends a level - an objective, an extraction point, elimination, or a timer?
- The source game had four commanders because it was four-player co-op. In single-player, is the
  commander a choice with distinct kits, or one fixed protagonist?
- Does the squad persist between levels, or does each level grant a fresh one?
- Does a weapon pickup replace what you are carrying, or fill a slot?
- On failure, do you retry the level, or is the run over?
- Given "no between-level management", what does progression across the nine levels actually consist
  of - only what you carry out of a level?
