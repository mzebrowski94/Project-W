# System order

Systems run in the order they were added to `Simulation`'s constructor. **Changing that order changes
results.** Register deliberately, and say in your reply why you put a system where you put it.

Transcribed from `Simulation`'s constructor and verified 2026-09-20 (44 systems). Re-verify if this list and the
constructor ever disagree; the constructor wins.

## The order

| # | System | # | System |
|---|---|---|---|
| 1 | `PositionHistorySystem` | 23 | `StrikeSystem` |
| 2 | `CooldownSystem` | 24 | `CollisionSystem` |
| 3 | `DragSystem` | 25 | `GrenadeSystem` |
| 4 | `CommanderControlSystem` | 26 | `PickupSystem` |
| 5 | `MeleeSystem` | 27 | `EvolutionSystem` |
| 6 | `TurretSystem` | 28 | `DamageSystem` |
| 7 | `PerceptionSystem` | 29 | `ActionSystem` |
| 8 | `NavigationSystem` | 30 | `AidSystem` |
| 9 | `WeaveSystem` | 31 | `CasualtySystem` |
| 10 | `SquadOrderSystem` | 32 | `DyingSystem` |
| 11 | `TrooperMindSystem` | 33 | `RespawnSystem` |
| 12 | `TrooperMoveSystem` | 34 | `MineSystem` |
| 13 | `TrooperCombatSystem` | 35 | `BlastSystem` |
| 14 | `WanderSystem` | 36 | `SlickSystem` |
| 15 | `AvoidanceSystem` | 37 | `ProjectileBoundsSystem` |
| 16 | `EnemyRangedSystem` | 38 | `ReinforcementSystem` |
| 17 | `HopSystem` | 39 | `WaveSystem` |
| 18 | `DodgeSystem` | 40 | `NestSystem` |
| 19 | `DriveSystem` | 41 | `ControlPointSystem` |
| 20 | `EnemyLeapSystem` | 42 | `ObjectiveSystem` |
| 21 | `StunSystem` | 43 | `AppearanceSystem` |
| 22 | `MovementSystem` | 44 | `ReaperSystem` |

Before system 1, `step(PlayerInputs)` copies each slot's `CommanderInput` into that commander's
`Intent`. That is the only way input enters the simulation, and it is also **the one place a relative
aim becomes a place on the field**: a stick sends an offset because it does not know where its
commander is standing, and his position is added here so every system below reads the absolute point
it has always read.

`TrooperMindSystem` and `TrooperMoveSystem` share **one** `Rng` stream (`RngStream.TROOPER`), by the
comment in the constructor: roaming and sweeping are the same concern and separating them would mean
two salts to keep apart for no benefit.

How loosely a *gun* throws draws from `RngStream.SCATTER`, shared by all four things that fire one - the commander, a trooper, a shooting alien and a gun on a mount. Deliberately not `SPIT`, which is how badly a *creature* aims and narrows as it settles: retuning a shotgun's pattern must not reshuffle where a spitter's next shot goes.

How big a nest's brood is and where it lands draws from `RngStream.NEST`, for the reason `WAVE` has its own: re-tuning what a nest produces must not move where a wave arrives.

Everything the AI leaves to the dice draws from a second stream, `RngStream.NERVE` - the trooper's
sweep at a leaper, its decision to go and fetch somebody, and a creature biting into a barrel. One
stream for all of them because they are one concern: how often a unit does something it only
sometimes does. `TrooperMindSystem`, `TrooperCombatSystem` and `CollisionSystem` are handed it.

Where the circles of a spatter fall around the shot that burst draws from `RngStream.SLICK`, for the reason every other stream exists: how a slick is shaped is a thing somebody will sit and tune, and doing that must not move a nest's brood or a wave.

Where a reinforcement lands draws from `RngStream.REINFORCEMENT`, and so does where a level lays its crates beside the control points - one stream for both halves, because they are one concern.

## Constraints that carry meaning

Each is a design decision, not an accident. Breaking one changes behaviour silently. All were checked
against the order above on 2026-09-13 and hold.

| Constraint | Why |
|---|---|
| Input reaches `Intent` **before any system runs** | Input is one tick's worth of what the player asked for, and nothing else may enter the simulation. |
| The leash is inside `CommanderControlSystem` (4), not a system of its own | That system owns a commander's velocity, and two systems writing one velocity is how a unit ends up travelling somewhere neither asked for. The middle of the commanders is worked out once in its `update`, before any of them is driven, so the first one leashed and the last one are leashed to the same place. |
| `RespawnSystem` (33) reads the commanders | Only to answer *where*: a man comes back beside whoever is still standing. *When* and *whether* are still read off the reaction on the dying unit, so a trooper that respawns needs no change to either. |
| Pickups (26) resolve **before** damage (28) | A grunt shot to a sliver that reaches a flower first gets up whole and takes that tick's damage as the thing it has become. |
| `EvolutionSystem` (27) after pickups, before damage | Turns what a unit has eaten into what it becomes, in time for the damage to land on the new strain. |
| `DyingSystem` (32) after `DamageSystem` (28) | Marks a body that has lain out its throes; damage decides who is dying. |
| Reactions - `RespawnSystem` (33) - run **while the dead are still there** | They react to a body that has not been removed yet. |
| `WaveSystem` (39) **before** `ObjectiveSystem` (42) | A level whose last wave lands this tick must not read as complete for the tick before the aliens appear. It sits with the other systems that are about the level rather than about anything standing in it, at the end of the tick. |
| `NestSystem` (40) **after** the death chain (28-35) and **before** `ReaperSystem` (44) | A destroyed nest empties the last of itself while its body is still on the field - the same window, and the same reason, as respawning and blasts. One system earlier and the brood would be owed to a tick that had already gone; one later and there would be nothing left to react to. |
| `NestSystem` (40) **before** `ObjectiveSystem` (42) | The same reason `WaveSystem` is: a level whose last nest dies this tick must not read as clear for the tick before its brood appears. |
| `ReaperSystem` **last** (44) | Nothing else in the simulation removes an entity; `onlyTheReaperRemovesEntities` fails the build if it tries. |
| Drivers write *desired* velocity; `DriveSystem` (19) commits it once and clears it | Several systems may have an opinion without knowing the others exist. A unit no system spoke for stands still, and no driver needs a halt-everything branch (G0010). |
| `WeaveSystem` (9) **after** `NavigationSystem` (8) and **before** `AvoidanceSystem` (15) | It bends the course navigation has just written, and avoidance keeps the last word about it - a weave that could steer a creature into rock would stop being a weave the moment a level had walls in it. Its phase advances with ground covered rather than with the clock, which is why it reads the desired velocity before anything has moved. |
| `AvoidanceSystem` (15) **before** `DodgeSystem` (18) | A dodge bent around a rock would not be the same dodge twice, and G0012 promises it is. A cornered rabbit sidesteps into a wall - what being cornered should cost it. |
| `AvoidanceSystem` (15) after every system that sets a course (7-14) | It can only do its job by seeing what the unit intended before adjusting it. |
| `EnemyLeapSystem` (20) **after** `PerceptionSystem` (7) | Who it throws itself at is read off its own `Perception`, which perception wrote this tick (G0026). Asking the question a second time here is how every leaper in a level ended up aimed at the first commander standing - the second player was never leapt at, and neither was a trooper. |
| `EnemyLeapSystem` (20) **after** `DriveSystem` (19), overwriting velocity outright | A leap is committed, not one opinion among several. |
| `StunSystem` (21) after every driver, **before** `MovementSystem` (22) | It owns the velocity of anything held still. A driver zeroing a stunned unit's velocity itself would silently delete knockback, so `DriveSystem` leaves a held unit alone rather than writing a zero. |
| `TurretSystem` (6) after `CommanderControlSystem` (4) and `MeleeSystem` (5), **before** `PerceptionSystem` (7) | A gun on a mount is a pair of hands like his own: it reads the intent that arrived this tick, and a shot it fires is in the world before anything looks around - the same window a melee sweep is spawned in. It is also the only thing that writes the position of a man who is aboard, so it must run before everything that would otherwise drive him. |
| `GrenadeSystem` (25) **immediately after** `CollisionSystem` (24) | A bounce is recorded by the solver and answered here, with nothing having moved in between. One system later and a grenade would be answering last tick's contact against this tick's course. |
| `HopSystem` (17) after everything that chooses a course (7-16), **before** `DodgeSystem` (18) | A gait paces a course rather than choosing one, so everything with an opinion about where the creature is going - including `EnemyRangedSystem` planting a shooter - has to have spoken first. Before the dodge because a dodge overwrites it outright: a sidestep from a shot it saw coming is a commitment and must not land in the middle of a sit. It scales the *desired* velocity rather than writing one, so `DriveSystem` is still the only thing that commits one (G0010). |
| `SlickSystem` (36) **immediately after** `BlastSystem` (35), inside the death-reaction window | Everything that leaves something behind does so by dying (G0018), so a shot that burst on a wall, one that burst on a man and one that ran out of flight all arrive here the same way. Unlike a blast it owes no damage of its own: what it leaves is ordinary bodies carrying ordinary blows, so a circle laid this tick catches what walks into it from the next one - which is what makes it a place rather than an event. |
| `BlastSystem` (35) after `RespawnSystem` (33), inside the death-reaction window | A blast is what a death leaves behind, so it has to run while the dead are still on the field - the same window, and the same reason, as respawning. |
| `BlastSystem` (35) after `DamageSystem` (28), **knowingly** | A blast owes damage that therefore lands on the following tick. That delay is not a defect: it is what makes a row of barrels go off as a cascade, one link per tick, instead of as one flat bang. |
| `MineSystem` (34) after the solver (24) and **immediately before** `BlastSystem` (35) | "Near enough to set one off" has to mean where things actually ended up, and a fuse that runs out this tick has to reach the death-reaction window on this tick rather than being owed to the next one. It spawns nothing itself: a mine explodes by dying, through the one reaction (G0018). |
| `ControlPointSystem` (41) is the only thing that puts a **post** down mid-level | Taking ground the level says is held afterwards is the same event this system already decides, so a second system watching for it would be a second place the rule can drift. It runs before `ObjectiveSystem` (42), so the post exists on the tick it is asked about - and **after** `WaveSystem` (39), so a schedule armed this tick sends its first wave on the next one, which is a tick against a clock measured in seconds. |
| `DragSystem` (3) **before** `CommanderControlSystem` (4) | It publishes which way the load lies; the system that owns his velocity is the only one that may constrain it. Two systems writing one velocity is how a unit travels somewhere neither asked for. |
| `EnemyRangedSystem` (16) after `AvoidanceSystem` (15), before `DriveSystem` (19) | Planting a shooter has to beat the course avoidance has just bent, and a desired velocity is spent on the tick it is written. |
| `ActionSystem` (29) and `AidSystem` (30) **after** `DamageSystem` (28) | A man who goes down this tick must be on offer this tick. Decided any earlier and the prompt is permanently one tick behind the body hitting the ground. It also reads positions the solver has already settled, so arm's length means where things ended up. |
| `ActionSystem` (29) and `AidSystem` (30) **before** `CasualtySystem` (31) | A man picked up on the tick his clock would have run out is picked up rather than buried. |
| `ReinforcementSystem` (38) after the solver (24) and after the death chain (28-35) | "Beside the commander" means where he actually ended up, not where he was aiming; and a man who arrives this tick is neither counted among the dead this tick nor swept by this tick's reaper. It sits with the systems that are about the level rather than about anything standing in it. |
| `CasualtySystem` (31) between damage (28) and dying (32) | Damage decides who goes down; this decides who stayed down too long; `DyingSystem` takes the body away. All three end at the same `Deaths.kill`, so a bleed-out leaves exactly the corpse a bullet would have. |

## Positions that surprise

Worth naming, because a reasonable guess puts each of these somewhere else.

- **`PositionHistorySystem` (1) and `CooldownSystem` (2) run first**, not last. History must snapshot
  where everything was *before* anything moves, or render interpolation reads this tick's position as
  last tick's. Cooldowns tick down before anything tries to fire.
- **`MeleeSystem` (5) runs before perception (7)**, immediately after the commander's control system -
  so a swing spawned this tick is already in the world when enemies look around.
- **`WanderSystem` (14) runs after the trooper block**, not with the other drivers near the top. It is
  the fallback: it ambles whatever nothing else spoke for.
- **`StrikeSystem` (23) sits between movement (22) and collision (24)** - a strike advances along its
  arc with everything else, then contacts resolve once.
- **`BlastSystem` (35) runs after everything that could have killed something**, not near the collision it looks like it belongs with. It is a death reaction first and a damage source second, and it reads the two families in that order.
- **`ProjectileBoundsSystem` (37) runs after the death chain**, not with collision.
- **`ReinforcementSystem` (38) is nowhere near `PickupSystem` (26)**, though a crate is what
  starts it. The pickup writes a count on the level entity and this reads it at the end of the
  tick, which is the point of a level-scope grant: the two halves do not have to be adjacent,
  and the far half gets to run once the field has stopped moving.

## Debugging a one-tick ordering bug

Symptoms: a value one tick stale, an effect landing on the wrong strain, knockback that disappears, a
dodge that is not reproducible, interpolation that judders. Check the constraint table first - every one
of those is listed there with the ordering that causes it.
