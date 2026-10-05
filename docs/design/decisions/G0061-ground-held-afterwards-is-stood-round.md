# G0061: Ground held afterwards is stood round, and it is never the rally point

**Status.** accepted

**Trigger.** Changing where a man from the reserve comes onto the field, where a squad's walk to a point
ends, who takes the point that starts a level's last fight, or what a squad does once nothing is left to take.

**Enforced by.** `ReserveTest.neverMustersMenOnGroundThatIsHeldAfterwards`,
`TrooperAdvanceTest.stopsOnARingRoundGroundThatIsHeldAfterwards`,
`.leavesTheTakingOfGroundHeldAfterwardsToTheCommander`, `.goesOnToGroundHeldAfterwardsOnceNothingIsLeftToTake`,
`.stillPushesOutFromTheRingWhenHeSaysForward`.

**Amends G0055**: the rally point. **Amends G0040**: what "the next point" is once nothing is left to take.

**Constraint.**

- **A point that holds afterwards never becomes the rally point.** The rally point is the spawn area until a
  point is taken, and the point taken last *of the others* after that. Taking the redoubt, the keep or a
  finale's hold leaves the rally point on the point before it.
- **A march to a point that holds afterwards ends on a ring round it**, `sim.trooper.postRingRadius` (15)
  from its middle - past the point's own radius (6), inside the turrets. A man stops the moment he is on
  it, on whatever side he came from, and the men on it split the circle into sectors as men on any point do.
  The ring is where they hold once the point is taken, too.
- **So the squad never takes it.** Stepping inside is what starts the last fight, and that is the player's.
- **Once it is taken and nothing is left to take, it is where every man goes next.** A man holding a point
  behind it follows the war on to it under G0040's rules - at once with a commander at it, after his hold
  with one ahead of him and none on his own point - and a man on no point walks to it. "Forward" still
  pushes the line out past the ring.

What marks such a point is the field it already had: `ControlPoint.holdsAfterwards`. No new kind and no
branch on one.

**Why not the rally point.** G0055 chose the point taken last because it is ground the side holds, nearest
the fight it has moved on to. Ground held afterwards is the fight: its waves walk at its middle. A muster, a
crate's men and a returning commander put down there arrive inside the last fight rather than onto the
field, and the point before it is still held ground, a short walk off.

**Why a ring.** Every man walking at the middle piles sixteen bodies into a circle a few strides across: they
cannot see past one another, one blast takes the lot, and it is exactly where the waves are headed. A ring
lets each man face outwards over his own sector, with the turrets just behind him. Outside the capture
radius because the redoubt was always meant to start when the player decides (its glossary entry); a squad
that took it on its own walk took that decision from him.

**Why held ground is the next point.** Without it, the rule above strands men: the reserve's men come onto
the field on the point before, nothing is left to take, and G0040 holds them there for the rest of the level.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Slots round the ring, one per man.* Slot assignment round a target is what G0033 and G0041 refuse;
the side he arrives on is his own, and avoidance spaces the rest.

**No:** *A ring round every point.* An ordinary point is somewhere the squad walks to and takes on its own
(G0029); a ring outside its radius would make every capture the commander's.

**No:** *A rally ground of the finale's own, or a rally point that jumps to the held ground once the fight is
won.* G0064 refused the first; the second is a second rally rule beside the point taken last.

**Revisit when** a level has been played to its end: whether 15 reads as a ring or as a squad standing off,
and whether a commander who never steps in leaves a squad waiting forever by design or by accident.
