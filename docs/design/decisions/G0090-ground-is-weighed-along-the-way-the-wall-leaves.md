# G0090: Ground is weighed along the way the wall leaves a unit

**Status.** accepted

**Trigger.** Changing how avoidance scores a direction against ground, or proposing that a unit hold short of fire
by a rule of its own - a hold for geysers, a wait order, a flow field that reads fire.

**Enforced by.** `GeyserTest.aTrooperHoldsShortOfAnEruptionAcrossACorridorAndGetsPastOnceItIsQuiet`; every
`AvoidanceSystemTest` and `FireTest` row G0046 names still holds.

**Provisional, for review** - settled in an AFK run (playtest ticket 15), not with Mateusz.

**Amends G0046.** Ground is still given room by whoever it can catch; what changed is which way a direction is
taken to go when it is weighed against ground.

**Constraint.** `AvoidanceSystem` weighs **ground** (a non-solid hazard) along each candidate direction **as the
rock the unit is against leaves it**: the part of the direction that leans into a wall touching him
(probed `WALL_PROBE` - a quarter of his radius - past his edge, through `Solid`, so a shut door is a wall too) is
taken off and the rest made unit length, or the direction counts as going nowhere. Bodies are weighed along the
direction as asked, as before. Four probes a unit with something near, no allocation.

**Why.** Ticket 15 asked whether troopers wait at an erupting geyser by G0046's avoidance alone. On open ground they
do better than wait - they go round (`aTrooperGoesRoundAnEruptionOnOpenGround`). In a corridor two tiles wide that a
geyser's fire all but fills, they did not: pressed to the side, a man chose the way 60 degrees into the wall - it points
past the fire, so it scored clear - and the solver turned it into the way along the wall, into the fire's edge. Weighed
along the way the wall leaves him, that way is the fire, so he holds short against the wall and goes on once it is out.
The same fault was there for a blast's fire in any corridor; a geyser only made it common.

**Why here and not a hold.** The hold would be a second answer to "do not walk into ground that will burn you",
beside avoidance; this makes the one answer see what the solver will do with it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A trooper hold of its own for an erupting geyser* (ticket 15): a second place the rule lives.

**No:** *Rock as a hazard in avoidance.* Walls are the flow field's and the solver's; scored as danger they would bend
every course in every corridor, not only one with fire in it.

**No:** *Fire in the flow field.* G0046 already refused ruined ground there: a route is the map's, ground is a moment.

**Revisit when** units are seen hugging a wall beside fire they could have gone round in the open, or a probe a
quarter of a radius out reads a wall a unit is not actually touching.
