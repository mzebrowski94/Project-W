# System order

Systems run in the order they were added to `Simulation`'s constructor. **Changing that order changes
results.** Register deliberately, and say in your reply why you put a system where you put it.

Transcribed from `Simulation`'s constructor and verified 2026-09-12. Re-verify if this list and the
constructor ever disagree; the constructor wins.

## The order

| # | System | # | System |
|---|---|---|---|
| 1 | `PositionHistorySystem` | 16 | `StunSystem` |
| 2 | `CooldownSystem` | 17 | `MovementSystem` |
| 3 | `CommanderControlSystem` | 18 | `StrikeSystem` |
| 4 | `MeleeSystem` | 19 | `CollisionSystem` |
| 5 | `PerceptionSystem` | 20 | `PickupSystem` |
| 6 | `NavigationSystem` | 21 | `EvolutionSystem` |
| 7 | `SquadOrderSystem` | 22 | `DamageSystem` |
| 8 | `TrooperMindSystem` | 23 | `DyingSystem` |
| 9 | `TrooperMoveSystem` | 24 | `RespawnSystem` |
| 10 | `TrooperCombatSystem` | 25 | `ProjectileBoundsSystem` |
| 11 | `WanderSystem` | 26 | `ControlPointSystem` |
| 12 | `AvoidanceSystem` | 27 | `ObjectiveSystem` |
| 13 | `DodgeSystem` | 28 | `AppearanceSystem` |
| 14 | `DriveSystem` | 29 | `ReaperSystem` |
| 15 | `EnemyLeapSystem` | | |

Before system 1, `step()` copies `CommanderInput` into the commander's `Intent`. That is the only way
input enters the simulation.

`TrooperMindSystem`, `TrooperMoveSystem` and `TrooperCombatSystem` share **one** `Rng` stream
(`RngStream.TROOPER`), by the comment in the constructor: roaming and sweeping are the same concern and
separating them would mean two salts to keep apart for no benefit.

## Constraints that carry meaning

Each is a design decision, not an accident. Breaking one changes behaviour silently. All were checked
against the order above on 2026-09-12 and hold.

| Constraint | Why |
|---|---|
| Input reaches `Intent` **before any system runs** | Input is one tick's worth of what the player asked for, and nothing else may enter the simulation. |
| Pickups (20) resolve **before** damage (22) | A grunt shot to a sliver that reaches a flower first gets up whole and takes that tick's damage as the thing it has become. |
| `EvolutionSystem` (21) after pickups, before damage | Turns what a unit has eaten into what it becomes, in time for the damage to land on the new strain. |
| `DyingSystem` (23) after `DamageSystem` (22) | Marks a body that has lain out its throes; damage decides who is dying. |
| Reactions - `RespawnSystem` (24) - run **while the dead are still there** | They react to a body that has not been removed yet. |
| `ReaperSystem` **last** (29) | Nothing else in the simulation removes an entity; `onlyTheReaperRemovesEntities` fails the build if it tries. |
| Drivers write *desired* velocity; `DriveSystem` (14) commits it once and clears it | Several systems may have an opinion without knowing the others exist. A unit no system spoke for stands still, and no driver needs a halt-everything branch (G0010). |
| `AvoidanceSystem` (12) **before** `DodgeSystem` (13) | A dodge bent around a rock would not be the same dodge twice, and G0012 promises it is. A cornered rabbit sidesteps into a wall - what being cornered should cost it. |
| `AvoidanceSystem` (12) after every system that sets a course (5-11) | It can only do its job by seeing what the unit intended before adjusting it. |
| `EnemyLeapSystem` (15) **after** `DriveSystem` (14), overwriting velocity outright | A leap is committed, not one opinion among several. |
| `StunSystem` (16) after every driver, **before** `MovementSystem` (17) | It owns the velocity of anything held still. A driver zeroing a stunned unit's velocity itself would silently delete knockback, so `DriveSystem` leaves a held unit alone rather than writing a zero. |

## Positions that surprise

Worth naming, because a reasonable guess puts each of these somewhere else.

- **`PositionHistorySystem` (1) and `CooldownSystem` (2) run first**, not last. History must snapshot
  where everything was *before* anything moves, or render interpolation reads this tick's position as
  last tick's. Cooldowns tick down before anything tries to fire.
- **`MeleeSystem` (4) runs before perception (5)**, immediately after the commander's control system -
  so a swing spawned this tick is already in the world when enemies look around.
- **`WanderSystem` (11) runs after the trooper block**, not with the other drivers near the top. It is
  the fallback: it ambles whatever nothing else spoke for.
- **`StrikeSystem` (18) sits between movement (17) and collision (19)** - a strike advances along its
  arc with everything else, then contacts resolve once.
- **`ProjectileBoundsSystem` (25) runs after the death chain**, not with collision.

## Debugging a one-tick ordering bug

Symptoms: a value one tick stale, an effect landing on the wrong strain, knockback that disappears, a
dodge that is not reproducible, interpolation that judders. Check the constraint table first - every one
of those is listed there with the ordering that causes it.
