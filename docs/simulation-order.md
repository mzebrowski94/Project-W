# System order

Systems run in the order they were added to `Simulation`'s constructor. **Changing that order changes
results.** Register deliberately, and say in your reply why you put a system where you put it.

Transcribed from `Simulation`'s constructor and verified 2026-09-13 (36 systems). Re-verify if this list and the
constructor ever disagree; the constructor wins.

## The order

| # | System | # | System |
|---|---|---|---|
| 1 | `PositionHistorySystem` | 19 | `MovementSystem` |
| 2 | `CooldownSystem` | 20 | `StrikeSystem` |
| 3 | `DragSystem` | 21 | `CollisionSystem` |
| 4 | `CommanderControlSystem` | 22 | `GrenadeSystem` |
| 5 | `MeleeSystem` | 23 | `PickupSystem` |
| 6 | `PerceptionSystem` | 24 | `EvolutionSystem` |
| 7 | `NavigationSystem` | 25 | `DamageSystem` |
| 8 | `SquadOrderSystem` | 26 | `ActionSystem` |
| 9 | `TrooperMindSystem` | 27 | `AidSystem` |
| 10 | `TrooperMoveSystem` | 28 | `CasualtySystem` |
| 11 | `TrooperCombatSystem` | 29 | `DyingSystem` |
| 12 | `WanderSystem` | 30 | `RespawnSystem` |
| 13 | `AvoidanceSystem` | 31 | `BlastSystem` |
| 14 | `EnemyRangedSystem` | 32 | `ProjectileBoundsSystem` |
| 15 | `DodgeSystem` | 33 | `ControlPointSystem` |
| 16 | `DriveSystem` | 34 | `ObjectiveSystem` |
| 17 | `EnemyLeapSystem` | 35 | `AppearanceSystem` |
| 18 | `StunSystem` | 36 | `ReaperSystem` |

Before system 1, `step()` copies `CommanderInput` into the commander's `Intent`. That is the only way
input enters the simulation.

`TrooperMindSystem` and `TrooperMoveSystem` share **one** `Rng` stream (`RngStream.TROOPER`), by the
comment in the constructor: roaming and sweeping are the same concern and separating them would mean
two salts to keep apart for no benefit.

Everything the AI leaves to the dice draws from a second stream, `RngStream.NERVE` - the trooper's
sweep at a leaper, its decision to go and fetch somebody, and a creature biting into a barrel. One
stream for all of them because they are one concern: how often a unit does something it only
sometimes does. `TrooperMindSystem`, `TrooperCombatSystem` and `CollisionSystem` are handed it.

## Constraints that carry meaning

Each is a design decision, not an accident. Breaking one changes behaviour silently. All were checked
against the order above on 2026-09-13 and hold.

| Constraint | Why |
|---|---|
| Input reaches `Intent` **before any system runs** | Input is one tick's worth of what the player asked for, and nothing else may enter the simulation. |
| Pickups (23) resolve **before** damage (25) | A grunt shot to a sliver that reaches a flower first gets up whole and takes that tick's damage as the thing it has become. |
| `EvolutionSystem` (24) after pickups, before damage | Turns what a unit has eaten into what it becomes, in time for the damage to land on the new strain. |
| `DyingSystem` (29) after `DamageSystem` (25) | Marks a body that has lain out its throes; damage decides who is dying. |
| Reactions - `RespawnSystem` (30) - run **while the dead are still there** | They react to a body that has not been removed yet. |
| `ReaperSystem` **last** (36) | Nothing else in the simulation removes an entity; `onlyTheReaperRemovesEntities` fails the build if it tries. |
| Drivers write *desired* velocity; `DriveSystem` (16) commits it once and clears it | Several systems may have an opinion without knowing the others exist. A unit no system spoke for stands still, and no driver needs a halt-everything branch (G0010). |
| `AvoidanceSystem` (13) **before** `DodgeSystem` (15) | A dodge bent around a rock would not be the same dodge twice, and G0012 promises it is. A cornered rabbit sidesteps into a wall - what being cornered should cost it. |
| `AvoidanceSystem` (13) after every system that sets a course (6-12) | It can only do its job by seeing what the unit intended before adjusting it. |
| `EnemyLeapSystem` (17) **after** `DriveSystem` (16), overwriting velocity outright | A leap is committed, not one opinion among several. |
| `StunSystem` (18) after every driver, **before** `MovementSystem` (19) | It owns the velocity of anything held still. A driver zeroing a stunned unit's velocity itself would silently delete knockback, so `DriveSystem` leaves a held unit alone rather than writing a zero. |
| `GrenadeSystem` (22) **immediately after** `CollisionSystem` (21) | A bounce is recorded by the solver and answered here, with nothing having moved in between. One system later and a grenade would be answering last tick's contact against this tick's course. |
| `BlastSystem` (31) after `RespawnSystem` (30), inside the death-reaction window | A blast is what a death leaves behind, so it has to run while the dead are still on the field - the same window, and the same reason, as respawning. |
| `BlastSystem` (31) after `DamageSystem` (25), **knowingly** | A blast owes damage that therefore lands on the following tick. That delay is not a defect: it is what makes a row of barrels go off as a cascade, one link per tick, instead of as one flat bang. |
| `DragSystem` (3) **before** `CommanderControlSystem` (4) | It publishes which way the load lies; the system that owns his velocity is the only one that may constrain it. Two systems writing one velocity is how a unit travels somewhere neither asked for. |
| `EnemyRangedSystem` (14) after `AvoidanceSystem` (13), before `DriveSystem` (16) | Planting a shooter has to beat the course avoidance has just bent, and a desired velocity is spent on the tick it is written. |
| `ActionSystem` (26) and `AidSystem` (27) **after** `DamageSystem` (25) | A man who goes down this tick must be on offer this tick. Decided any earlier and the prompt is permanently one tick behind the body hitting the ground. It also reads positions the solver has already settled, so arm's length means where things ended up. |
| `ActionSystem` (26) and `AidSystem` (27) **before** `CasualtySystem` (28) | A man picked up on the tick his clock would have run out is picked up rather than buried. |
| `CasualtySystem` (28) between damage (25) and dying (29) | Damage decides who goes down; this decides who stayed down too long; `DyingSystem` takes the body away. All three end at the same `Deaths.kill`, so a bleed-out leaves exactly the corpse a bullet would have. |

## Positions that surprise

Worth naming, because a reasonable guess puts each of these somewhere else.

- **`PositionHistorySystem` (1) and `CooldownSystem` (2) run first**, not last. History must snapshot
  where everything was *before* anything moves, or render interpolation reads this tick's position as
  last tick's. Cooldowns tick down before anything tries to fire.
- **`MeleeSystem` (5) runs before perception (6)**, immediately after the commander's control system -
  so a swing spawned this tick is already in the world when enemies look around.
- **`WanderSystem` (12) runs after the trooper block**, not with the other drivers near the top. It is
  the fallback: it ambles whatever nothing else spoke for.
- **`StrikeSystem` (20) sits between movement (19) and collision (21)** - a strike advances along its
  arc with everything else, then contacts resolve once.
- **`BlastSystem` (31) runs after everything that could have killed something**, not near the collision it looks like it belongs with. It is a death reaction first and a damage source second, and it reads the two families in that order.
- **`ProjectileBoundsSystem` (32) runs after the death chain**, not with collision.

## Debugging a one-tick ordering bug

Symptoms: a value one tick stale, an effect landing on the wrong strain, knockback that disappears, a
dodge that is not reproducible, interpolation that judders. Check the constraint table first - every one
of those is listed there with the ordering that causes it.
