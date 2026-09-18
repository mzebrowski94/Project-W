# G0029: A point taken is a point held, and "forward" is what sends the squad on

**Trigger.** Anything that changes how the squad crosses a level, what an order does, or why a
trooper leaves somewhere.

**Enforced by.** `TrooperAdvanceTest.waitsOnAPointItHasTakenRatherThanWalkingOnToTheNext`,
`.goesOnLookingAroundWhileItWaits`, `.goesOnToTheNextPointWhenHeSaysForward`,
`.waitsAgainOnceItHasTakenTheNextPoint`, `.walksToTheFirstPointWithoutBeingTold`.

**Constraint.** A trooper standing on a **captured** control point holds there - stationary, still
sweeping its arc - instead of walking to the next one. "Forward" is what sends it on, and **one order
buys one point**: the permission is spent the moment it reaches the next point, and it settles again
once that point is taken. A trooper anywhere else advances exactly as it always did, so the first
walk of a level needs no order and a point still being captured is still being captured.

**Why the squad waits.** Ground the squad has taken is now ground somebody is still standing on, and
the level advances at the commander's pace rather than at the trooper's. Before this the squad ran
the level on its own: the bar filled, the men walked off east, and the commander spent the level
catching up with his own squad while the ground behind them was empty again.

**Forward also drops whatever standing order the trooper had.** Holding a place and keeping station
on the commander are both reasons not to advance, and either would swallow the order - a man told to
hold pushes on and then stands still again, a man following stays at your shoulder while you walk.
Having to press cancel first was one press too many for the order that sets a level's pace. Cancel
still exists and is not the same key: it stops the squad without sending it anywhere.

**It is a pace, not a leash.** The reason this is worth a record is what it does to the loop rather
than to the squad. An order reaches only what is within earshot (G0017), so sending the squad on
means **walking up to the men holding the point**. That is the whole rhythm of a long level: take a
point, gather on it, push on together - and it falls out of two rules that already existed rather
than out of a new one.

**`urgency` is no longer the whole of the game's pace.** It still sets how hard a trooper weaves on
its way somewhere, which is what it was always for. How fast a *level* goes is now the commander's,
and that is the point.

**Scope.** Control points. It says nothing about what a squad does with no points left - it still
roams, looking for whatever is alive - and nothing about a point the other side could take back,
which is still an open question.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A new trooper mode for holding a taken point.* `SECURE` already means standing on a point,
stationary, watching outward and not shuffling towards the middle. That posture is the same whether
the bar is filling or full, and the renderer already draws it.

**No:** *Spending the "forward" distance as the permission.* `forwardRemaining` is a burst of pace,
measured in ground, and it expires by being walked off. The next point may be one tile away or thirty,
so a distance cannot express "go to the next one" - the two halves of the order are genuinely two
things, and the flag beside the distance says so.

**No:** *Holding at every point, including one nobody has taken yet.* It would stop the level before
it started: the first walk would need an order given to men who are standing where they spawned, for
no reason a player could infer.

**No:** *One order releasing the squad until it is cancelled.* Cheaper on presses, and it makes
holding the exception rather than the default - which is most of what this record is for, gone after
the first press. Not rejected for ever: if a press per point turns out to be a tax rather than a
rhythm, this is the first thing to try.

**No:** *A countdown on the release, so they advance while a forward is recent.* An order is given
once and then stands on whoever heard it (G0017). A decaying order is a different model, and it would
be the only thing in the game that quietly stops being true.

**Revisit when** a level has been played end to end. The two things to watch: whether a press per
point is a rhythm or an errand, and whether a squad holding a point the commander has already walked
past reads as discipline or as men who have not noticed the war moving on.
