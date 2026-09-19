# G0033: A pack spreads by moving the goal, not by pushing each other apart

**Trigger.** Anything that changes how several creatures close on one man, or that reaches for a
steering behaviour to fix how a crowd looks.

**Enforced by.** `ApproachTest.closesOnAPointBesideHimRatherThanOnHim`,
`.leavesAStrainThatDoesNotSpreadWalkingStraightAtHim`, `.walksStraightAtHimForTheLastOfTheApproach`,
`.keepsTheSamePaceWhileItSpreads`, `.bringsTwoInDownOppositeSidesRatherThanOneBehindTheOther`,
`.givesTwoSpawnedApartLinesOfTheirOwn`, `.takesTheSameLineOnEveryRun`.

**Constraint.** A creature closing on what it is hunting walks at a point **beside** the man rather
than at the man: a fixed distance perpendicular to its own line in, on a side taken from where it was
spawned. The offset falls to nothing as it closes, so the last of every approach is straight at him.
Zero switches it off, and a strain with it off behaves exactly as everything did before.

**Why the goal and not the course.** The symptom was a pack arriving in single file down the
commander's row and shoving each other from behind. It looks like a separation problem and it is not.
Avoidance already treats a creature's own kind as a hazard; what it cannot do is help, because what
is in the way is another creature heading for *the identical spot*, so every direction that avoids it
is also a direction away from the goal. And the file itself comes from the flow field: its distances
are four-connected, so in the open its gradient is grid-shaped and everything converges onto one row.
Give each creature a different spot and both halves stop existing, for a field and two multiplies.

**Why the side is seeded and not rolled.** The same argument G0012 makes for a dodge, applied to an
approach: a spread you can learn is a spread you can play around, and a pack that fans out the same
way on every run is a pack a replay reproduces (pillar 6). It is seeded from the spawn point, exactly
as a weave's phase is, so creatures laid out along a line get bearings spread across the whole range
and a spawn row arrives as a fan.

**Why it closes.** A creature that held its offset all the way in would orbit rather than arrive, and
the one thing an arcade pack must do is arrive. The ramp is what keeps this a *spread* - open while
the ground is being crossed, shut by the time anything is in reach.

**Terrain still has the last word.** The offset point is only steered at while the line to it is
clear; where it is not, the flow field answers and routes to the man himself. A corridor therefore
still files, which is correct: single file in a crack is not a defect, it is the crack.

**Scope.** Creatures hunting somebody. It does not touch a provoked unit walking at a place, a unit
going to eat something, a unit marching at the post, or a trooper - and it deliberately does not
touch a shooter, which takes its own ground and has no queue to break up.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A separation force between neighbours, or boids.* It is what the symptom looks like it
needs, and it cannot work: the crowding is caused by a shared destination, and separation fights the
destination rather than changing it. It would also be a second opinion about a course, which is what
`AvoidanceSystem` already is.

**No:** *A flow field per creature, or per offset point.* One grid pass per unit per tick is the
exact cost a flow field exists to avoid, and the codebase already refuses it twice - for provoked
units and for foraging ones.

**No:** *Rolling the side from the `Rng`.* Cheaper to write and it throws away the property that
makes the behaviour learnable (G0012).

**No:** *Slot assignment - a ring of numbered positions around the target, handed out.* It needs
somebody to own the ring, somebody to reassign a slot when a creature dies, and it holds a reference
to the target across ticks (G0010, G0016). A function of the spawn point needs none of that and reads
the same on screen.

**No:** *An offset that holds all the way in.* An orbit, and the pack never lands a blow.

**No:** *Putting the two numbers on `AvoidanceDef`.* Avoidance is about what is in the way; this is
about where the creature is going. One record holding both would be the first place somebody looks
for the wrong thing.

**Revisit when** a pack has been watched crossing open ground on a wide level. The thing to watch is
whether the fan reads as creatures flanking you or as creatures ignoring you until the last second -
if the second, the ramp starts too far out rather than the offset being too wide.
