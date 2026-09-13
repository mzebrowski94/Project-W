# System order

Systems run in the order they were added to `Simulation`'s constructor. **Changing that order changes
results.** Register deliberately, and say in your reply why you put a system where you put it.

Transcribed from `Simulation`'s constructor and verified 2026-09-12 (31 systems). Re-verify if this list and the
constructor ever disagree; the constructor wins.

## The order

| # | System | # | System |
|---|---|---|---|
| 1 | `PositionHistorySystem` | 17 | `MovementSystem` |
| 2 | `CooldownSystem` | 18 | `StrikeSystem` |
| 3 | `CommanderControlSystem` | 19 | `CollisionSystem` |
| 4 | `MeleeSystem` | 20 | `GrenadeSystem` |
| 5 | `PerceptionSystem` | 21 | `PickupSystem` |
| 6 | `NavigationSystem` | 22 | `EvolutionSystem` |
| 7 | `SquadOrderSystem` | 23 | `DamageSystem` |
| 8 | `TrooperMindSystem` | 24 | `DyingSystem` |
| 9 | `TrooperMoveSystem` | 25 | `RespawnSystem` |
| 10 | `TrooperCombatSystem` | 26 | `BlastSystem` |
| 11 | `WanderSystem` | 27 | `ProjectileBoundsSystem` |
| 12 | `AvoidanceSystem` | 28 | `ControlPointSystem` |
| 13 | `DodgeSystem` | 29 | `ObjectiveSystem` |
| 14 | `DriveSystem` | 30 | `AppearanceSystem` |
| 15 | `EnemyLeapSystem` | 31 | `ReaperSystem` |
| 16 | `StunSystem` | | |

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
| `GrenadeSystem` (20) **immediately after** `CollisionSystem` (19) | A bounce is recorded by the solver and answered here, with nothing having moved in between. One system later and a grenade would be answering last tick's contact against this tick's course. |
| `BlastSystem` (26) after `RespawnSystem` (25), inside the death-reaction window | A blast is what a death leaves behind, so it has to run while the dead are still on the field - the same window, and the same reason, as respawning. |
| `BlastSystem` (26) after `DamageSystem` (23), **knowingly** | A blast owes damage that therefore lands on the following tick. That delay is not a defect: it is what makes a row of barrels go off as a cascade, one link per tick, instead of as one flat bang. |

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
- **`BlastSystem` (26) runs after everything that could have killed something**, not near the collision it looks like it belongs with. It is a death reaction first and a damage source second, and it reads the two families in that order.
- **`ProjectileBoundsSystem` (27) runs after the death chain**, not with collision.

## Debugging a one-tick ordering bug

Symptoms: a value one tick stale, an effect landing on the wrong strain, knockback that disappears, a
dodge that is not reproducible, interpolation that judders. Check the constraint table first - every one
of those is listed there with the ordering that causes it.
