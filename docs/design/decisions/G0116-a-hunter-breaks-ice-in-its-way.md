# G0116: A hunter breaks ice in its way

**Status.** accepted

**Trigger.** Changing how a creature routes past ice, or past anything else that stands across a way until broken; adding
a third such thing; touching `Solid.routes`, `Door.routedThrough` or the hunters' fields in `NavigationSystem`.

**Enforced by.** `IceTest.aHunterBreaksIceInItsWayEvenWhenThereIsAWayRound`,
`.aHunterStillGoesRoundAShutDoorWhenThereIsAWayRound`, `.isBrokenByALeapingAlienAsADoorIs`; `ShutHouseRouteTest`,
`DoorSiegeTest` (G0075 unchanged for doors).

**Settled with Mateusz, 2026-11-23** (the rift's review after ticket 10): hunters in the rift went the long way round
every block of ice, so the ice was a wall only the squad ever met.

**Amends G0075, ADR 0025 and G0115**, for ice alone. A door keeps G0075 unchanged - round first, the siege only when there is no way round -
and its *Do not build* stands. Ice is the exception: **ground to a hunter's route**.

**Constraint.**

- **A hunter's route runs through ice as if it were broken.** The field a hunter walks (`NavigationSystem`'s ground-now
  fields) is flooded over `Solid.routes()`: the terrain and every shut door, but with ice counted open. So the shortest
  way to a man is taken whether or not a way round exists; where it runs through ice, the hunter walks up to the ice.
- **At the ice it is a siege** (G0069): the ice is the shut door its way runs into, so a leaper leaps at it and lands
  `sim.door.leapDamage` on it, again and again, until it breaks; a hunter without a leap stands at it, as at a door.
- **What sets ice apart is a field, never a branch on a kind** (ADR 0025): `Door.routedThrough`, set by
  `Spawner.spawnIce`, false for every door in a doorway. `Solid.derive` marks the tiles of every shut door that has it,
  and `Solid.routes()` reads them; a straight line, a shot, a blast and the solver still meet the ice as solid.
- Unchanged: a provoked creature walking to a place, and a creature marching to the post, route over the ground as
  before. A trooper's answer to ice in his way is G0117's.

**Why not "round first" for ice too.** It is what the rift had (G0115, ADR 0025): with the maze's many ways round, a
hunter almost never met the ice, and the ice was a wall to the squad alone - a delay for one side, not a place where the
fight happens. Ice is a delay to be broken through; the swarm breaking it too is what makes it one for both sides.

**Why not a ratio** (go round when the way round is no longer than so much): G0075's own rejection - a number to tune and
a judgement the player cannot see.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Ice a hunter goes round* - G0075 extended to ice; the ice then shuts the squad's way and nobody else's.

**No:** *Doors a hunter breaks through in its way* - this record widened to doors: G0075's grunts jammed against a
house's shut door with nobody inside, again.

**No:** *A second route field for ice* - the hunters' field is flooded over `Solid.routes()`; one field, one more
passability, no third family.
