# G0041: A squad spreads as a pack does, and follows where he is going

**Status.** accepted

**Trigger.** Anything that changes how several troopers walk somewhere together, or that reaches for a
formation, a separation force or a slot to fix how a squad looks on the move.

**Enforced by.** `SquadSpreadTest.walksTwoMenToAPointAbreastRatherThanOneBehindTheOther`,
`.fansOutBesideHimRatherThanQueueingOnHisHeels`, `.walksAtWhereHeIsGoingRatherThanWhereHeIs`,
`.stillEndsUpStandingOnThePoint`.

**Constraint.** A trooper carries the pack's own `Approach` (G0033): a side seeded from where he was put
down, an offset that is widest far out and spent by `closeRange`. It is used for the two walks a squad
makes as a group - **to a point**, and **after the commander** - and only while the line to the offset
point is clear; out of it the route answers and a corridor still files.

A follower aims at where the commander **will be** - his position plus his velocity times
`followAheadSeconds` - until it is within its following distance of him, and at the man himself after
that. The point ahead is dropped where it would be inside rock.

**Why the pack's spread and not something of the squad's own.** The symptom was the one G0033 fixed for
aliens - men converging on one goal down one line - and G0033's diagnosis holds word for word: the file
comes from a shared destination, so the answer is a different destination each, not a force between
neighbours. One spread, reused through the same method, rather than a second way of doing it.

**Why where he is going.** A follower walking at the man chases the spot he has just left: the squad
strings out on his heels and cuts every corner behind him. *Company of Heroes* aims followers at an offset
from the leader's position about two seconds ahead; this is that, shrunk to one multiply, and switched to
the man himself close in so a follower never overtakes him into his own line of fire.

**Scope.** Walking to a point and following him. Roaming, going to a casualty and going to a gun are a
man's own errands to a place of his own, and do not spread. It says nothing about formations as an order,
which G0017 defers.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Formation slots round the commander.* Somebody has to own the ring and reassign a slot when a man
dies, and a slot is a reference to him held across ticks (G0010, G0016). A side seeded from the spawn point
needs none of it and reads the same.

**No:** *A separation force or boids.* G0033's reason: it fights the shared destination instead of
removing it, and it would be a second opinion about a course beside `AvoidanceSystem`.

**No:** *Aiming at the point ahead all the way in.* The squad ends up standing in front of him, where he
is shooting.

**Revisit when** a squad has been watched crossing an open biome. Two men put down close together can land
on the same side; if that reads as a pair walking in file, the seeding wants a stride tuned for troopers
rather than a new mechanism.
