# G0029: A point taken is a point held, and "forward" is what sends the squad on

**Status.** amended by G0040: a squad holding a taken point may leave it to follow a commander who is ahead

**Trigger.** Anything that changes how the squad crosses a level, what an order does, or why a trooper
leaves somewhere.

**Enforced by.** `TrooperAdvanceTest.waitsOnAPointItHasTakenRatherThanWalkingOnToTheNext`,
`.goesOnLookingAroundWhileItWaits`, `.goesOnToTheNextPointWhenHeSaysForward`,
`.waitsAgainOnceItHasTakenTheNextPoint`, `.walksToTheFirstPointWithoutBeingTold`.

**Constraint.** A trooper standing on a **captured** control point holds there - stationary, still
sweeping its arc - instead of walking to the next one. "Forward" sends it on, and **one order buys one
point**: the permission is spent the moment it reaches the next point, and it settles again once that
point is taken. A trooper anywhere else advances as before, so the first walk of a level needs no order.

- **Forward also drops whatever standing order the trooper had.** Holding a place and keeping station on
  the commander are both reasons not to advance, and either would swallow the order. Cancel is a
  different key: it stops the squad without sending it anywhere.
- **The level goes at the commander's pace.** `urgency` sets only how hard a trooper weaves on its way.

**Why.** Ground the squad has taken stays held, rather than emptying behind a squad that ran the level
on its own. And an order reaches only what is within earshot (G0017), so sending the squad on means
**walking up to the men holding the point**: take a point, gather on it, push on together - a rhythm
that falls out of two existing rules rather than a new one.

**Scope.** Control points. Not what a squad does with no points left - it roams, looking for whatever is
alive - nor a point the other side could take back, an open question.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A new trooper mode for holding a taken point.* `SECURE` already means standing on a point,
stationary, watching outward, whether the bar is filling or full, and the renderer draws it.

**No:** *Spending the "forward" distance as the permission.* `forwardRemaining` is a burst of pace
measured in ground; the next point may be one tile away or thirty, so a distance cannot say "go to the
next one".

**No:** *Holding at every point, including one nobody has taken yet.* The first walk would need an order
given to men standing where they spawned, for no reason a player could infer.

**No:** *One order releasing the squad until it is cancelled.* It makes holding the exception rather than
the default. If a press per point turns out to be a tax rather than a rhythm, it is the first thing to
try.

**No:** *A countdown on the release, so they advance while a forward is recent.* An order stands on
whoever heard it (G0017); a decaying order would be the only thing in the game that quietly stops being
true.

**Revisit when** a level has been played end to end: whether a press per point is a rhythm or an errand.
