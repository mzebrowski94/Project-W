# G0048: The boss is a rooted strain with a barrage, and it rises from a lair

**Status.** accepted

**Trigger.** Adding a second boss, a pattern that is not a spread of shots, anything that makes a
strain stand still, or anything else a level keeps off the field until the players arrive.

**Enforced by.** `BarrageTest`, `LairTest`, `AdventureTwoScenarioTest.keepsTheBossOffTheFieldUntilEveryPlayerIsInTheKeep`
and `.raisesTheBossAmongANestOfEveryStrainAndEveryGunClearOfRockAndOfEachOther`.

**Constraint.**

- **The boss is a strain** (`UnitKind.BOSS_GRUNT`), a definition and a block - never a class, marker
  or spawn method (G0008). What makes it a boss is two numbers every strain already has or now has:
  **speed zero is rooted** - it never walks, the solver treats it as scenery, and a blow does not throw
  it - and an enabled **`BarrageDef`**, off for everything else.
- **A barrage is a cycle of patterns, each a count of salvos of a spread of shots.** Ring, fan, spiral
  and stream are the same record with different numbers. Each pattern is told by the winding-up state
  a leap already uses, and followed by a rest. **Nothing is rolled**: the order, the turn - which flips
  every time round the cycle - and every wait are counted, so a barrage can be learnt and replays.
- **It shoots what perception published** (G0026): whoever it is hunting, or the place it was last hurt
  from, and holds fire with neither. **A blow does not interrupt it**; going down does.
- **Its health is drawn on its charge core**, read down (pillar 5).
- **A lair is level state** (G0007): a patch of ground and a list of arrivals, carried by the level
  entity. **Nothing it holds is on the field until every commander on his feet stands on its ground**,
  and then everything it holds is put down on that tick, once. A player who is down does not hold it up.

**Why.** Adventure 2 needed an end, and the brief was a boss that does not move, shoots in sequences
like the bosses of other top-down shooters, sits among nests, and does not show in the pulled-back view
until everybody has arrived. A pattern the player is meant to read has to be counted rather than dealt
(the reason G0012 gives for a dodge). Waking on *every* commander rather than the first keeps a player
still in the last biome from meeting the boss with half his friends missing.

**Accepted cost.** Something rooted is scenery to a grenade: one bounces off the boss rather than
passing through it. Every alien strain carries an inert `Barrage`, as every strain carries an inert
`Grip`. A boss shot at from out of its range answers at the place the shot came from, for as long as
nobody walks into it - which is also how a player learns he has been noticed.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A `Boss` component, class or system that knows it is a boss.* A boss is its numbers.

**No:** *A barrage that pauses while the boss is stunned.* A fist would hold it still for ever.

**No:** *Patterns rolled from the `Rng`.* A pattern is learnt; a dealt one is noise (pillar 5).

**No:** *The boss spawned asleep or invisible from the first tick.* Hidden is a renderer lie and asleep
is still a circle on the field; a lair keeps it off the field entirely.

**No:** *A lair that wakes on the first commander in.* It would split the players at the one fight
that most needs all of them.

**Revisit when** a pattern has to do something a spread of shots cannot (a beam, a shot that turns in
flight, a pattern that moves the boss), or a lair has to wait for something other than where the
players are standing.
