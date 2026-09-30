# G0037: Ground slows, and it slows his side harder

**Status.** accepted

**Trigger.** Adding a kind of ground that does anything to what stands on it, proposing that a
terrain's cost be symmetric, or proposing that terrain block, hide or hurt rather than slow. It does
not touch G0009 - nothing here is quantised to the grid - and it does not touch the routing rules.

**Enforced by.** `SlowTest.makesGroundWorthGoingRoundSlowerToCrossThanOpenGround`,
`.keepsItOnExactlyTheBearingItAskedFor`, `.leavesEveryOtherKindOfGroundAlone`,
`SimConfigTest.costsThePlayersSideMoreToWadeThroughThanItCostsTheAliens`.

**Constraint.** A kind of ground may take a fraction of the speed of whatever is standing on it, and
may do nothing else at all. Four rules:

- **The number is not in `map`.** `Terrain` says `THICKET` and whether it can be stood on, which is
  geometry and the one thing generation cannot work without. What it costs is a `GroundDef` in
  `sim`, in one `sim.ground.*` block - exactly the split `Terrain`'s own class note has always
  declared, and the first thing to actually use it.
- **It scales a pace and never a course.** Nothing turns; a wading unit walks exactly where it meant
  to. A route through a thicket is the route it always was, and the flow field, every connectivity
  check and every spawn treat it as the open ground it is.
- **One place spends it**, and the same place spends a slow from a gun, so a creature wading with a
  beam on it is slow twice over and nothing had to say so. That place is `Pace`, not `DriveSystem`,
  because the commander's velocity is owned by his own control system - and ground the player was
  exempt from is the exact opposite of what this is for.
- **The two sides declare their own factor.** The player's is much harsher.

**Scope.** Every terrain has a `GroundDef` because a table with a hole in it cannot be built, and all
of them but the thicket declare `clear()`. It says nothing about what any ground looks like, and
nothing about the mire, the ash or the dust, which are still a colour and a mood.

**Why the asymmetry is the mechanic rather than a balance number.** Ground that cost both sides the
same is scenery. Everybody arrives at the fight in the order they would have arrived on open ground,
nothing about the shape of the fight changes, and the only thing the player has learnt is that the
walk took longer - which is pillar 1's problem, not a mechanic. Slowing his side harder makes a
thicket ground he has to *decide* about: going round costs seconds, and going through costs the
seconds and the exchange he has to have while wading. It is the first thing on the field that is
worse for him than for them by declaration, and it is deliberately the only one.

**Why it is the pace and not a wall.** A thicket that blocked would be rock with a different colour,
and the level already has rock. What is interesting about undergrowth is that you *can* go through
it and will wish you had not.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A speed number on `Terrain`.* The class note has refused it since the enum was written, and
the reason still holds: `map` may not hold a gameplay number, and a number there is a number no
`game.properties` can turn.

**No:** *Ground that costs both sides the same.* It is scenery - see above. If a terrain is ever
wanted that is only atmospheric, it declares `clear()` and costs nothing, which is what all the
others do.

**No:** *A per-strain ground factor, so a runner wades differently from a brute.* Two numbers per
terrain is a table anybody can read; nine is one nobody tunes. The day a strain genuinely needs its
own, it is a field on `UnitDef` multiplying the side's.

**No:** *Routing around a thicket - a flow field that prices the ground.* It would make "the shorter
walk" mean something the player cannot see on the map, and `FlowFieldTest` promises a route is a
function of terrain and a body size. Walking round is a decision he makes, not one the squad makes
for him.

**No:** *Impassable undergrowth, or undergrowth that hides a unit.* The first is rock. The second is
line of sight through a terrain, which G0011 and G0015 already settled: what a unit can notice is the
list of tests its strain declares, and none of them is about the floor.

**No:** *Slowing applied by each driver rather than in one place.* Then a slow would depend on which
system happened to write the course last, and the commander - who has no driver - would be exempt.

**Revisit when** a level wants ground that hurts rather than slows, which is a slick and already
exists, or ground that one side is faster on rather than slower - the same two numbers, read the
other way, and nothing in this shape refuses it.
