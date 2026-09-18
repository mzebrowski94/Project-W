# System order

Systems run in the order they were added to `Simulation`'s constructor. **Changing that order changes
results.** Register deliberately, and say in your reply why you put a system where you put it.

Transcribed from `Simulation`'s constructor and verified 2026-09-13 (36 systems). Re-verify if this list and the
constructor ever disagree; the constructor wins.

## The order

| # | System | # | System |
|---|---|---|---|
| 1 | `PositionHistorySystem` | 21 | `MovementSystem` |
| 2 | `CooldownSystem` | 22 | `StrikeSystem` |
| 3 | `DragSystem` | 23 | `CollisionSystem` |
| 4 | `CommanderControlSystem` | 24 | `GrenadeSystem` |
| 5 | `MeleeSystem` | 25 | `PickupSystem` |
| 6 | `TurretSystem` | 26 | `EvolutionSystem` |
| 7 | `PerceptionSystem` | 27 | `DamageSystem` |
| 8 | `NavigationSystem` | 28 | `ActionSystem` |
| 9 | `WeaveSystem` | 29 | `AidSystem` |
| 10 | `SquadOrderSystem` | 30 | `CasualtySystem` |
| 11 | `TrooperMindSystem` | 31 | `DyingSystem` |
| 12 | `TrooperMoveSystem` | 32 | `RespawnSystem` |
| 13 | `TrooperCombatSystem` | 33 | `BlastSystem` |
| 14 | `WanderSystem` | 34 | `ProjectileBoundsSystem` |
| 15 | `AvoidanceSystem` | 35 | `WaveSystem` |
| 16 | `EnemyRangedSystem` | 36 | `ControlPointSystem` |
| 17 | `DodgeSystem` | 37 | `ObjectiveSystem` |
| 18 | `DriveSystem` | 38 | `AppearanceSystem` |
| 19 | `EnemyLeapSystem` | 39 | `ReaperSystem` |
| 20 | `StunSystem` | | |

Before system 1, `step(PlayerInputs)` copies each slot's `CommanderInput` into that commander's
`Intent`. That is the only way input enters the simulation, and it is also **the one place a relative
aim becomes a place on the field**: a stick sends an offset because it does not know where its
commander is standing, and his position is added here so every system below reads the absolute point
it has always read.

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
| The leash is inside `CommanderControlSystem` (4), not a system of its own | That system owns a commander's velocity, and two systems writing one velocity is how a unit ends up travelling somewhere neither asked for. The middle of the commanders is worked out once in its `update`, before any of them is driven, so the first one leashed and the last one are leashed to the same place. |
| `RespawnSystem` (32) reads the commanders | Only to answer *where*: a man comes back beside whoever is still standing. *When* and *whether* are still read off the reaction on the dying unit, so a trooper that respawns needs no change to either. |
| Pickups (25) resolve **before** damage (27) | A grunt shot to a sliver that reaches a flower first gets up whole and takes that tick's damage as the thing it has become. |
| `EvolutionSystem` (26) after pickups, before damage | Turns what a unit has eaten into what it becomes, in time for the damage to land on the new strain. |
| `DyingSystem` (31) after `DamageSystem` (27) | Marks a body that has lain out its throes; damage decides who is dying. |
| Reactions - `RespawnSystem` (32) - run **while the dead are still there** | They react to a body that has not been removed yet. |
| `WaveSystem` (35) **before** `ObjectiveSystem` (37) | A level whose last wave lands this tick must not read as complete for the tick before the aliens appear. It sits with the other systems that are about the level rather than about anything standing in it, at the end of the tick. |
| `ReaperSystem` **last** (39) | Nothing else in the simulation removes an entity; `onlyTheReaperRemovesEntities` fails the build if it tries. |
| Drivers write *desired* velocity; `DriveSystem` (18) commits it once and clears it | Several systems may have an opinion without knowing the others exist. A unit no system spoke for stands still, and no driver needs a halt-everything branch (G0010). |
| `WeaveSystem` (9) **after** `NavigationSystem` (8) and **before** `AvoidanceSystem` (15) | It bends the course navigation has just written, and avoidance keeps the last word about it - a weave that could steer a creature into rock would stop being a weave the moment a level had walls in it. Its phase advances with ground covered rather than with the clock, which is why it reads the desired velocity before anything has moved. |
| `AvoidanceSystem` (15) **before** `DodgeSystem` (17) | A dodge bent around a rock would not be the same dodge twice, and G0012 promises it is. A cornered rabbit sidesteps into a wall - what being cornered should cost it. |
| `AvoidanceSystem` (15) after every system that sets a course (7-14) | It can only do its job by seeing what the unit intended before adjusting it. |
| `EnemyLeapSystem` (19) **after** `DriveSystem` (18), overwriting velocity outright | A leap is committed, not one opinion among several. |
| `StunSystem` (20) after every driver, **before** `MovementSystem` (21) | It owns the velocity of anything held still. A driver zeroing a stunned unit's velocity itself would silently delete knockback, so `DriveSystem` leaves a held unit alone rather than writing a zero. |
| `TurretSystem` (6) after `CommanderControlSystem` (4) and `MeleeSystem` (5), **before** `PerceptionSystem` (7) | A gun on a mount is a pair of hands like his own: it reads the intent that arrived this tick, and a shot it fires is in the world before anything looks around - the same window a melee sweep is spawned in. It is also the only thing that writes the position of a man who is aboard, so it must run before everything that would otherwise drive him. |
| `GrenadeSystem` (24) **immediately after** `CollisionSystem` (23) | A bounce is recorded by the solver and answered here, with nothing having moved in between. One system later and a grenade would be answering last tick's contact against this tick's course. |
| `BlastSystem` (33) after `RespawnSystem` (32), inside the death-reaction window | A blast is what a death leaves behind, so it has to run while the dead are still on the field - the same window, and the same reason, as respawning. |
| `BlastSystem` (33) after `DamageSystem` (27), **knowingly** | A blast owes damage that therefore lands on the following tick. That delay is not a defect: it is what makes a row of barrels go off as a cascade, one link per tick, instead of as one flat bang. |
| `DragSystem` (3) **before** `CommanderControlSystem` (4) | It publishes which way the load lies; the system that owns his velocity is the only one that may constrain it. Two systems writing one velocity is how a unit travels somewhere neither asked for. |
| `EnemyRangedSystem` (16) after `AvoidanceSystem` (15), before `DriveSystem` (18) | Planting a shooter has to beat the course avoidance has just bent, and a desired velocity is spent on the tick it is written. |
| `ActionSystem` (28) and `AidSystem` (29) **after** `DamageSystem` (27) | A man who goes down this tick must be on offer this tick. Decided any earlier and the prompt is permanently one tick behind the body hitting the ground. It also reads positions the solver has already settled, so arm's length means where things ended up. |
| `ActionSystem` (28) and `AidSystem` (29) **before** `CasualtySystem` (30) | A man picked up on the tick his clock would have run out is picked up rather than buried. |
| `CasualtySystem` (30) between damage (27) and dying (31) | Damage decides who goes down; this decides who stayed down too long; `DyingSystem` takes the body away. All three end at the same `Deaths.kill`, so a bleed-out leaves exactly the corpse a bullet would have. |

## Positions that surprise

Worth naming, because a reasonable guess puts each of these somewhere else.

- **`PositionHistorySystem` (1) and `CooldownSystem` (2) run first**, not last. History must snapshot
  where everything was *before* anything moves, or render interpolation reads this tick's position as
  last tick's. Cooldowns tick down before anything tries to fire.
- **`MeleeSystem` (5) runs before perception (7)**, immediately after the commander's control system -
  so a swing spawned this tick is already in the world when enemies look around.
- **`WanderSystem` (14) runs after the trooper block**, not with the other drivers near the top. It is
  the fallback: it ambles whatever nothing else spoke for.
- **`StrikeSystem` (22) sits between movement (21) and collision (23)** - a strike advances along its
  arc with everything else, then contacts resolve once.
- **`BlastSystem` (33) runs after everything that could have killed something**, not near the collision it looks like it belongs with. It is a death reaction first and a damage source second, and it reads the two families in that order.
- **`ProjectileBoundsSystem` (34) runs after the death chain**, not with collision.

## Debugging a one-tick ordering bug

Symptoms: a value one tick stale, an effect landing on the wrong strain, knockback that disappears, a
dodge that is not reproducible, interpolation that judders. Check the constraint table first - every one
of those is listed there with the ordering that causes it.
