# G0012: A dodge is a function of the shot, not a roll

**Status.** accepted

**Trigger.** Writing or changing dodge behaviour.

**Enforced by.** `SimulationDeterminismTest` catches a dodge drawn from the `Rng` only if it changes a replay; otherwise reading.

**Constraint.** A creature that steps aside from an incoming shot decides where to step from the
shot's geometry alone: perpendicular to its heading, to the side of its line the creature is already
on. Nothing about a dodge is drawn from the `Rng` - not the side, not the timing, not the distance.
The same shot from the same place gets the same answer every time.

What keeps that from making the creature trivially killable is a **commit window**: once it has
broken one way it cannot answer anything else until the window closes. The counter to a dodge is a
second shot, not a perfectly led first one.

**Scope.** Dodging a projectile. It says nothing about the rest of a creature's behaviour - the same
rabbit wanders from a seeded stream, and a leap's hesitation is deliberately varied for the opposite
reason: a leap is a threat the player must *read*, and a dodge is a pattern the player must *learn*.
That difference is the whole of why one draws from the `Rng` and the other must not.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A seeded wobble - the same dodge, plus a little variance.* It will be proposed the first time
somebody leads a rabbit five times running and it stops feeling like a skill. Rejected because it
converts a miss from "I read that wrong" into "I was unlucky", and a player cannot tell the two apart
from inside the game. Variance would also be invisible in a replay, which is where the seeded-versus-
deterministic distinction usually gets waved through: both replay fine, and only one is learnable.

**No:** *Dodging continuously - re-deciding the direction every tick while the shot closes.* It curves away
from a shot rather than stepping off its line, which cannot be led at all. It also reads as the
creature knowing exactly where the bullet is at every instant, which is a stronger claim about what it
can see than the game wants to make.

**No:** *Alternating sides, or a longer memory of which way it went last.* Deterministic, and learnable in
principle, but the state that decides it is invisible: the player would have to have watched the
previous dodge to predict this one. Geometry is legible from the shot alone.

**Revisit when** something other than a rabbit dodges - a unit whose whole identity is being hard to
hit, where predictability is the wrong promise. That is a different creature with a different rule,
not an amendment to this one.
