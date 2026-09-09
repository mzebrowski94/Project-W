# G0014: The clinch carries the body in; it never places it

**Constraint.** When a lethal blow clinches, the body **keeps travelling**. It is given the heading it
died on - or the line to whoever killed it, if that heading no longer points at him - at a
configured carry speed, and decays to rest. Nothing writes either body's position directly. The two
end up connected because the constraint solver already handles it: a corpse is immovable and the
commander is not, so a body still moving when it arrives shoves him back, and they travel the last of
it together until the carry bleeds off.

**Supersedes the third rejected alternative in [G0013](G0013-the-clinch-is-what-a-parry-earns.md)**,
which placed the body against the commander at the moment of death and dismissed carrying it as
needing a second rule in the solver. That was wrong on both counts: no second rule was needed, and
placing it looked like exactly what it was. G0013's actual constraint - that a melee kill clinches
only something already committed to attacking - stands unchanged.

Two things follow:

- **The carry must be able to cross the gap, and that is checked.** A sweep sits `reach` out and is
  `radius` across, so it can kill something as much as `reach + radius - commanderRadius` further out
  than the commander's own body. `SimConfig` refuses a config whose carry stops short of that, because
  nothing downstream can notice: the numbers are legal, every system works, and it shows up only as
  the beat looking wrong.
- **Nothing is carried when the two are already touching**, which is every exchange decided by contact
  rather than by reach. A commander killed by claws is at arm's length by definition, and his body is
  left exactly where it fell.

**Scope.** The clinch. It does not reopen the knockback rule: a blow that kills still throws nothing
*away* from the survivor, and the carry is the opposite direction by construction.

**Rejected, and likely to be re-proposed.**

*Place the body and be done with it.* One line, no tuning, no config check. It reads as a teleport,
and at the exact moment the game most wants the player looking at the two of them.

*Pull the commander onto the body instead of pushing him back.* Closes the same gap and is cheaper -
one entity moves rather than a chain of two. Rejected because the leaper is the thing with the
momentum, and a commander who slid *forward* onto something he had just killed would read as him
lunging at a corpse. Being shoved back is also the half the player feels.

*Give the corpse a mass and let the solver do an inelastic collision.* The physical answer, and it
needs mass, which the collision model deliberately does not have (ADR 0007). An immovable body with a
velocity is the same outcome for none of the machinery.

**Revisit when** a body needs to carry something other than a commander - a leaper killed against a
wall currently slides that last stride into rock, because terrain and the arena are clamps that skip
immovable bodies. It is under a unit at the shipped numbers and it is scenery for a second, but a
longer carry would need the clamp to apply to corpses too.
