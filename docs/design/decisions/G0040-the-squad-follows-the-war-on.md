# G0040: The squad follows the war on; it never runs ahead of it

**Status.** amended by G0061: with nothing left to take, the next point is the ground held afterwards

**Trigger.** Anything that changes when a trooper leaves a point, what "forward" is for, or how the
squad paces a level.

**Enforced by.** `TrooperAdvanceTest.goesOnAtOnceWhenHeIsAlreadyAtTheNextPoint`,
`.goesOnAfterHoldingAWhileWhenHeHasGoneOnAhead`, `.neverLeavesACommanderBehind`,
`.staysWhileHeIsStandingOnThePointWithThem`, `.peelsOffThePointOneManAtATimeRatherThanInStep`, and
G0029's own tests, which all still pass.

**Amends G0029** in one respect: a squad holding a taken point may now leave it without an order.
Everything else G0029 settled stands - a point taken is still a point held, "forward" still sends the
squad on at once and one press still buys one point.

**Constraint.** A trooper standing on a **captured** point, not sent on, goes on to the next point by
itself in exactly two cases:

- **at once**, when a commander on his feet is within the **rally radius** of the next point;
- **after his hold** - `holdSeconds`, give or take `holdJitterSeconds` drawn per man when he starts
  holding - when no commander is within the rally radius of *this* point **and** some commander on his
  feet is **ahead** of him: a shorter walk to the next point, by route, than he is.

A commander behind the squad keeps it where it is however long it has held. Leaving is exactly what
"forward" gives - the same `sentOn`, spent on reaching the next point - so the squad settles again there.

**Why.** G0029's revisit trigger fired in the first end-to-end playtest of the Adventure: a squad holding
a point the commander had already walked past read as men who had not noticed the war moving on, and
walking back to press forward at every point was an errand rather than a rhythm. What G0029 was written
against - the squad running the level on its own while the commander caught up - is still refused, by
the "ahead" test: the squad only ever closes on a commander, never opens a gap from one.

**Why "ahead by route" and not a heading.** Which way a man is walking flickers every time he dodges.
Whether he is a shorter walk from the next point is a place and a flood the mind already has for every
point, so the question costs two table lookups and means the same on every replay.

**Why the hold is drawn per man.** A squad that leaves a point on one tick reads as a machine. Drawn
from the squad's own stream when each man starts holding, so it replays (pillar 6), and a man who leaves
and comes back starts a fresh one.

**Scope.** Control points on a march. It says nothing about a squad with no point left to take (it holds
the last one until told, and roams once sent), nothing about a post (a defence has no next point), and
nothing about what the other side could take back.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Leaving after the hold unless a commander is on this point* - the rule as first proposed.
It sends a squad on ahead of a commander who is still behind it, which is G0029's original failure: the
bar fills, the men walk off, and he spends the level catching up with ground that is empty again.

**No:** *Deciding "approaching" from his heading or velocity.* Flickers with every sidestep; a place and a
route distance do not.

**No:** *One hold for the whole squad.* It needs something to own it and it leaves in lockstep.

**No:** *A countdown on "forward"* - still G0029's rejection, for G0029's reason: an order is given once
and stands; it never quietly stops being true.

**Revisit when** the Adventure has been played end to end again. Watch whether ten seconds reads as
discipline or as delay, and whether "ahead by route" misfires when a commander takes a side path that
happens to be a shorter walk to the next point without being on his way there.
