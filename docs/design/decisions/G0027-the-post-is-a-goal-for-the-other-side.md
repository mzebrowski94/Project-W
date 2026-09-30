# G0027: The post is a goal the level gives the aliens, not a point they capture

**Status.** accepted

**Trigger.** Adding anything that tells the alien side where to be, or proposing that the post be
taken, damaged or defended by a bar.

**Enforced by.** `DefendTest.walksTheAliensAtThePostWhenTheyHaveNoticedNobody`,
`.leavesThePostForSomebodyItHasNoticed`, `.holdsThePostOnlyOnceTheLastWaveHasBeenSeenOff`,
`.neverHoldsAPostOnALevelThatHasNone`.

**Constraint.** A post is ground with a position and nothing else: no health, no faction, no capture
bar, nothing in the collision path. Its whole effect is that an alien which has noticed nobody walks
at it instead of ambling about its patch - the lowest thing that can have a creature's attention,
below anything it has noticed, anything that hurt it and anything it could eat. Holding it is the
objective: every wave sent, and nothing hostile standing.

**Scope.** One per level, and it never moves - which is what lets the route to it be a flow field per
strain built once, where everything else that is not the commander steers straight (G0026). It says
nothing about what a squad does with the post; troopers have no special rule about standing on it,
and if that turns out to matter it is a change to what an order means rather than to this.

**Why it is not a control point.** A point is somewhere the squad goes and takes, with a dwell and a
bar; a post is somewhere it stays. Sharing one component would mean a flag saying which of the two it
is, and a flag that changes what a thing fundamentally is is a second kind of thing wearing the first
one's name (ADR 0008).

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A post with health, that the aliens destroy.* The obvious lose condition, and the game has no
lose conditions yet - what a failed level does is untouched by any of this and is still open. It also
makes the post a thing to stand in front of rather than ground to hold, which is a different level.

**No:** *A capture bar the aliens fill by standing on it.* The mirror of a control point, and it needs
the open question "can the other side take a point back" answered first. Nothing in the game has ever
said what the aliens are *trying* to do, and inventing it inside a scenario would settle that by
accident.

**No:** *Marching handled by the wander system, or by a flag on the scenario.* The march is somewhere
to be, which is exactly what `Perception` already answers for a grudge and for food. Adding it there
cost one field and one branch; anywhere else it would have been a second idea of what a unit has its
attention on.

**No:** *A wave schedule that spawns from a scenario's own timer.* A scenario is content and runs once,
at populate time. What is still to come is level state, it decides half of an objective, and it has to
survive a restart identically - so it is a component on the level entity like everything else that
belongs to the level rather than to anything standing in it (G0007).

**Revisit when** a level needs two posts, or one that moves. Both are cheap in the data and neither is
cheap in the routing: the field is built once precisely because there is one goal and it stays put.
