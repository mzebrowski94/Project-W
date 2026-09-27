# System order

Systems run in the order they were added to `Simulation`'s constructor. **Changing that order changes
results.** Register deliberately, and say in your reply why you put a system where you put it.

The constructor is the truth; `SystemOrderTest` fails if this table and it disagree, and holds every
row of *Constraints that carry meaning* below as data - a reorder that breaks one fails with that
row's reason. A new constraint is a row here and a row there.

## The order

| # | System | # | System |
|---|---|---|---|
| 1 | `PositionHistorySystem` | 26 | `GrenadeSystem` |
| 2 | `CooldownSystem` | 27 | `PickupSystem` |
| 3 | `DragSystem` | 28 | `EvolutionSystem` |
| 4 | `CommanderControlSystem` | 29 | `DamageSystem` |
| 5 | `MeleeSystem` | 30 | `ActionSystem` |
| 6 | `TurretSystem` | 31 | `AidSystem` |
| 7 | `PerceptionSystem` | 32 | `CasualtySystem` |
| 8 | `NavigationSystem` | 33 | `DyingSystem` |
| 9 | `WeaveSystem` | 34 | `RespawnSystem` |
| 10 | `GripSystem` | 35 | `ProjectileBoundsSystem` |
| 11 | `SquadOrderSystem` | 36 | `BloatSystem` |
| 12 | `TrooperMindSystem` | 37 | `MineSystem` |
| 13 | `TrooperMoveSystem` | 38 | `BlastSystem` |
| 14 | `TrooperCombatSystem` | 39 | `SlickSystem` |
| 15 | `WanderSystem` | 40 | `ReinforcementSystem` |
| 16 | `AvoidanceSystem` | 41 | `WaveSystem` |
| 17 | `EnemyRangedSystem` | 42 | `NestSystem` |
| 18 | `HopSystem` | 43 | `LairSystem` |
| 19 | `DodgeSystem` | 44 | `BarrageSystem` |
| 20 | `DriveSystem` | 45 | `ControlPointSystem` |
| 21 | `EnemyLeapSystem` | 46 | `ObjectiveSystem` |
| 22 | `StunSystem` | 47 | `FacingSystem` |
| 23 | `MovementSystem` | 48 | `AppearanceSystem` |
| 24 | `StrikeSystem` | 49 | `ReaperSystem` |
| 25 | `CollisionSystem` |  |  |

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
| `RespawnSystem` (34) reads the commanders | Only to answer *where*: a man comes back beside whoever is still standing. *When* and *whether* are still read off the reaction on the dying unit, so a trooper that respawns needs no change to either. |
| Pickups (27) resolve **before** damage (29) | A grunt shot to a sliver that reaches a flower first gets up whole and takes that tick's damage as the thing it has become. |
| `EvolutionSystem` (28) after pickups, before damage | Turns what a unit has eaten into what it becomes, in time for the damage to land on the new strain. |
| `DyingSystem` (33) after `DamageSystem` (29) | Marks a body that has lain out its throes; damage decides who is dying. |
| Reactions - `RespawnSystem` (34) - run **while the dead are still there** | They react to a body that has not been removed yet. |
| `WaveSystem` (41) **before** `ObjectiveSystem` (46) | A level whose last wave lands this tick must not read as complete for the tick before the aliens appear. It sits with the other systems that are about the level rather than about anything standing in it, at the end of the tick. |
| `NestSystem` (42) **after** the death chain (29-39) and **before** `ReaperSystem` (49) | A destroyed nest empties the last of itself while its body is still on the field - the same window, and the same reason, as respawning and blasts. One system earlier and the brood would be owed to a tick that had already gone; one later and there would be nothing left to react to. |
| `NestSystem` (42) **before** `ObjectiveSystem` (46) | The same reason `WaveSystem` is: a level whose last nest dies this tick must not read as clear for the tick before its brood appears. |
| `LairSystem` (43) **after** the death chain (29-39) and **before** `ObjectiveSystem` (46) | The reason `WaveSystem` and `NestSystem` sit there: a boss that rises this tick counts against the level on this tick, and a lair is level state like the schedule beside it (G0048). It reads where the commanders ended up, which is after the solver. |
| `BarrageSystem` (44) **after** `PerceptionSystem` (7) | Whom a boss shoots at is what perception wrote this tick (G0026), the same rule as every other alien gun. It sits late, after `LairSystem` rather than with `EnemyRangedSystem` (17), because it owes no desired velocity to anything and a boss that rises this tick has its first salvo counted from the next one. A salvo fired here first moves on the next tick: one tick against patterns timed in tenths of a second. |
| `ReaperSystem` **last** (49) | Nothing else in the simulation removes an entity; `onlyTheReaperRemovesEntities` fails the build if it tries. |
| `FacingSystem` (47) **after** every aim and move has settled - `CollisionSystem` (25) is the last of them | A facing is where the thing ended the tick looking. `PerceptionSystem` (7) reads it on the next tick, so the arc the game notices within is exactly the one the renderer drew (G0015, pillar 5). |
| Drivers write *desired* velocity; `DriveSystem` (20) commits it once and clears it | Several systems may have an opinion without knowing the others exist. A unit no system spoke for stands still, and no driver needs a halt-everything branch (G0010). |
| `GripSystem` (10) **after** the course setters (7-9), **before** the squad block (11-15) and `AvoidanceSystem` (16) | A creature with a man in its jaws is carrying him off, not hunting, so it overwrites the course navigation wrote - and avoidance still bends that, so a carry goes round rock and ruined ground like any other walk. It moves the man in its jaws by velocity, which the squad block and `DriveSystem` then leave alone because he is held: it is the only thing that moves him (G0045). |
| `WeaveSystem` (9) **after** `NavigationSystem` (8) and **before** `AvoidanceSystem` (16) | It bends the course navigation has just written, and avoidance keeps the last word about it - a weave that could steer a creature into rock would stop being a weave the moment a level had walls in it. Its phase advances with ground covered rather than with the clock, which is why it reads the desired velocity before anything has moved. |
| `AvoidanceSystem` (16) **before** `DodgeSystem` (19) | A dodge bent around a rock would not be the same dodge twice, and G0012 promises it is. A cornered rabbit sidesteps into a wall - what being cornered should cost it. |
| `AvoidanceSystem` (16) after every system that sets a course (7-15) | It can only do its job by seeing what the unit intended before adjusting it. |
| `EnemyLeapSystem` (21) **after** `PerceptionSystem` (7) | Who it throws itself at is read off its own `Perception`, which perception wrote this tick (G0026). Asking the question a second time here is how every leaper in a level ended up aimed at the first commander standing - the second player was never leapt at, and neither was a trooper. |
| `EnemyLeapSystem` (21) **after** `DriveSystem` (20), overwriting velocity outright | A leap is committed, not one opinion among several. |
| `StunSystem` (22) after every driver, **before** `MovementSystem` (23) | It owns the velocity of anything held still. A driver zeroing a stunned unit's velocity itself would silently delete knockback, so `DriveSystem` leaves a held unit alone rather than writing a zero. |
| `TurretSystem` (6) after `CommanderControlSystem` (4) and `MeleeSystem` (5), **before** `PerceptionSystem` (7) | A gun on a mount is a pair of hands like his own: it reads the intent that arrived this tick, and a shot it fires is in the world before anything looks around - the same window a melee sweep is spawned in. It is also the only thing that writes the position of a man who is aboard, so it must run before everything that would otherwise drive him. |
| `GrenadeSystem` (26) **immediately after** `CollisionSystem` (25) | A bounce is recorded by the solver and answered here, with nothing having moved in between. One system later and a grenade would be answering last tick's contact against this tick's course. |
| `HopSystem` (18) after everything that chooses a course (7-17), **before** `DodgeSystem` (19) | A gait paces a course rather than choosing one, so everything with an opinion about where the creature is going - including `EnemyRangedSystem` planting a shooter - has to have spoken first. Before the dodge because a dodge overwrites it outright: a sidestep from a shot it saw coming is a commitment and must not land in the middle of a sit. It scales the *desired* velocity rather than writing one, so `DriveSystem` is still the only thing that commits one (G0010). |
| `SlickSystem` (39) **immediately after** `BlastSystem` (38), inside the death-reaction window | Everything that leaves something behind does so by dying (G0018), so a shot that burst on a wall, one that burst on a man and one that ran out of flight all arrive here the same way. Unlike a blast it owes no damage of its own: what it leaves is ordinary bodies carrying ordinary blows, so a circle laid this tick catches what walks into it from the next one - which is what makes it a place rather than an event. |
| `BlastSystem` (38) after `RespawnSystem` (34), inside the death-reaction window | A blast is what a death leaves behind, so it has to run while the dead are still on the field - the same window, and the same reason, as respawning. |
| `BlastSystem` (38) after `DamageSystem` (29), **knowingly** | A blast owes damage that therefore lands on the following tick. That delay is not a defect: it is what makes a row of barrels go off as a cascade, one link per tick, instead of as one flat bang. |
| `MineSystem` (37) after the solver (25) and **immediately before** `BlastSystem` (38) | "Near enough to set one off" has to mean where things actually ended up, and a fuse that runs out this tick has to reach the death-reaction window on this tick rather than being owed to the next one. It spawns nothing itself: a mine explodes by dying, through the one reaction (G0018). |
| `ControlPointSystem` (45) is the only thing that puts a **post** down mid-level | Taking ground the level says is held afterwards is the same event this system already decides, so a second system watching for it would be a second place the rule can drift. It runs before `ObjectiveSystem` (46), so the post exists on the tick it is asked about - and **after** `WaveSystem` (41), so a schedule armed this tick sends its first wave on the next one, which is a tick against a clock measured in seconds. |
| `DragSystem` (3) **before** `CommanderControlSystem` (4) | It publishes which way the load lies; the system that owns his velocity is the only one that may constrain it. Two systems writing one velocity is how a unit travels somewhere neither asked for. |
| `EnemyRangedSystem` (17) after `AvoidanceSystem` (16), before `DriveSystem` (20) | Planting a shooter has to beat the course avoidance has just bent, and a desired velocity is spent on the tick it is written. |
| `ActionSystem` (30) and `AidSystem` (31) **after** `DamageSystem` (29) | A man who goes down this tick must be on offer this tick. Decided any earlier and the prompt is permanently one tick behind the body hitting the ground. It also reads positions the solver has already settled, so arm's length means where things ended up. |
| `ActionSystem` (30) and `AidSystem` (31) **before** `CasualtySystem` (32) | A man picked up on the tick his clock would have run out is picked up rather than buried. |
| `ReinforcementSystem` (40) after the solver (25) and after the death chain (29-39) | "Beside the commander" means where he actually ended up, not where he was aiming; and a man who arrives this tick is neither counted among the dead this tick nor swept by this tick's reaper. It sits with the systems that are about the level rather than about anything standing in it. |
| `CasualtySystem` (32) between damage (29) and dying (33) | Damage decides who goes down; this decides who stayed down too long; `DyingSystem` takes the body away. All three end at the same `Deaths.kill`, so a bleed-out leaves exactly the corpse a bullet would have. |
| `ProjectileBoundsSystem` (35) **before** the death reactions (36-39), not after them | A shot that runs out of flight, flies into rock or leaves the arena ends by *dying* like every other end (G0018), so it has to be marked while the reactions can still see it. Registered after the death chain - which is where it sat until 2026-09-20 - it was marked dead past that window and reaped on the same tick, which silently deleted the blast and the spatter every **miss** should have left. A shot that hit something is marked by the solver's contact long before this and is unaffected. |
| `BloatSystem` (36) after the damage chain (29-34) and **before** `BlastSystem` (38) | The same two reasons `MineSystem` has: this tick's hits are already on the component, and a body that fills this tick has to reach the death-reaction window on this tick rather than being owed to the next one. It spawns nothing itself - a body pumped full explodes by dying, through the one reaction (G0018) - and it is the only thing outside `DyingSystem` that touches the throes clock, which it holds open while a corpse still has charge on it. |
| `DriveSystem` (20) scales what it commits by `Pace`, and `CommanderControlSystem` (4) scales his speed by the same | Two callers rather than one place in the pipeline, because the commander's velocity is owned by his own control system (G0010's named exception) and ground the player was exempt from is the opposite of what a thicket is for. Scaling the speed rather than the finished velocity is what keeps hauling honest: a man dragging a barrel through undergrowth is capped off the speed he actually has. |

## Positions that surprise

Worth naming, because a reasonable guess puts each of these somewhere else.

- **`PositionHistorySystem` (1) and `CooldownSystem` (2) run first**, not last. History must snapshot
  where everything was *before* anything moves, or render interpolation reads this tick's position as
  last tick's. Cooldowns tick down before anything tries to fire.
- **`MeleeSystem` (5) runs before perception (7)**, immediately after the commander's control system -
  so a swing spawned this tick is already in the world when enemies look around.
- **`WanderSystem` (15) runs after the trooper block**, not with the other drivers near the top. It is
  the fallback: it ambles whatever nothing else spoke for.
- **`StrikeSystem` (24) sits between movement (23) and collision (25)** - a strike advances along its
  arc with everything else, then contacts resolve once.
- **`BlastSystem` (38) runs after everything that could have killed something**, not near the collision it looks like it belongs with. It is a death reaction first and a damage source second, and it reads the two families in that order.
- **`ProjectileBoundsSystem` (35) runs in the death chain**, not with collision (25) where it looks
  like it belongs and not after the reactions, where it used to be. It is the system that ends a
  shot nothing stopped, and ending is dying.
- **`ReinforcementSystem` (40) is nowhere near `PickupSystem` (27)**, though a crate is what
  starts it. The pickup writes a count on the level entity and this reads it at the end of the
  tick, which is the point of a level-scope grant: the two halves do not have to be adjacent,
  and the far half gets to run once the field has stopped moving.

## Debugging a one-tick ordering bug

Symptoms: a value one tick stale, an effect landing on the wrong strain, knockback that disappears, a
dodge that is not reproducible, interpolation that judders. Check the constraint table first - every one
of those is listed there with the ordering that causes it.
