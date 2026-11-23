# G0117: A trooper shoots down ice in his way

**Status.** accepted

**Trigger.** Changing what a trooper does when his way runs into ice, or into anything else that stands across a way
until broken; his fight's priority over anything else he would shoot; `Trooper.clearingIce` or `sim.ice.clearDistance`.

**Enforced by.** `TrooperIceTest.shootsDownIceHisWayRunsIntoAndGoesThrough`, `.shootsTheSwarmBeforeTheIce`,
`.leavesAloneIceHisWayDoesNotRunInto`; `TrooperDoorTest` (a door is still opened, never shot).

**Settled with Mateusz, 2026-11-23** (the rift's review after ticket 10): troopers stood at ice that shut their way,
waiting for nobody, until the commander shot it down.

**Amends ADR 0025 and G0115** (a trooper whose way ice shuts "waits for nobody at it", and stood there) - and G0069's
door for ice alone:
a door in a doorway is still opened by hand, by one trooper, the rest watching their sectors.

**Constraint.**

- **His route runs through ice**, as every trooper's route does (ADR 0022: a route over the terrain), so he walks up to
  it.
- **Within `sim.ice.clearDistance` (10) of it** - his middle to its nearest part - he stands, turns to face it, and
  fires at its nearest part through his own gun, as at anything else (`TrooperCombatSystem`): his fire arc and aim
  error hold. Every squad shot chips it (ADR 0025), so the squad's fire takes it down in a moment; then his way is open
  and he walks on.
- **Fighting comes first.** Only a man not fighting - no target of the swarm's (`Trooper.Mode.FIGHT`) - shoots ice. A
  man who sees an alien turns on it; the ice waits.
- **Only ice in his way**: the ice his walk runs into, as `Solid.doorAhead` finds a door - never ice he merely sees.
- What sets it apart is the door's fields (ADR 0025): `opensByHand` false. Never a branch on a kind.

**Why not leave it to the commander.** A squad that stands at a wall of ice waiting for its commander to notice is a
squad the player has to manage - and the commander is a unit, not a cursor (pillar 2); the men clearing their own way
is the squad behaving as one.

**Why not shoot every ice in sight.** The rift has a handful of ice a biome; a squad that broke each one would open
the maze for the swarm and spend its fire on walls. Ice in his way is the one he has a reason to break.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A trooper shooting ice while the swarm is his to fight* - fighting comes first, always.

**No:** *A trooper shooting a door* - G0069: a door is opened, by hand.

**No:** *A trooper breaking every ice he sees* - only the ice his way runs into.
