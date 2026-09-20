# G0035: Fire is a neutral slick left by the blast dying

**Trigger.** Adding or changing anything that leaves ground dangerous after an explosion, or
proposing that a slick's side stop being a declaration and go back to being a rule. This is the
answer to G0034's own revisit trigger and narrows it; G0018 is untouched.

**Enforced by.** `FireTest.burnsTheGroundWhereAnExplosionWent`,
`.leavesNothingBurningWhereNothingExploded`, `.burnsWhoeverWalksIntoItWhicheverSideHeIsOn`,
`.putsAManStandingInItOnTheGroundRatherThanKillingHim`, `.goesOutOnceItHasBurnedDown`,
`.leansOffBurningGroundAsReadilyAsOffARock`,
`.swellsAndThenCollapsesRatherThanBeingOneCircleThroughout`, `.leavesNothingBurningInsideRock`.

**Constraint.** Fire is a **slick**, not a second mechanic: each circle is an ordinary entity with a
`Position`, a `Body`, a `Collider` and a `ContactDamage`, non-solid and immovable, and the blow it
lands is the wounding kind. Three things are declared differently from venom, and each is a field
rather than a branch:

- **It carries no side.** A `Blast` has no `Faction`, and the spatter takes the faction of whatever
  died, so fire is neutral without anything saying so. G0034's side rule therefore becomes a
  declaration rather than a property of the mechanic: a slick belongs to whatever fired the shot,
  which for an explosion is nobody.
- **It is steered around.** `Collider.avoided` says whether a unit gives something room, defaulting
  to `solid` so every existing body behaves as it did. Ground that catches everybody is given room;
  ground that has taken a side is not, and the things that made it walk through it.
- **It swells and collapses.** `Slick` holds a peak radius and two ramp fractions and writes the
  body each tick, so the circle that burns is the circle drawn **at that instant**.

It arrives the way everything that leaves something behind arrives: the `Blast` carries
`SlicksOnDeath`, and the death reaction lays the spatter when the flash burns out. **Nothing that
explodes had to learn that fire exists**, and a gun that leaves no blast leaves no fire - which is
why the flamethrower is an exception without a clause anywhere.

Every size is a fraction of the blast's own radius, in one `sim.fire.*` block, so a new explosive
gets fire without naming a number.

**Scope.** Every explosion in the game. It says nothing about how long any particular fire burns -
one block does - and nothing about what one looks like beyond the body being the drawing. It does
not touch G0018: a blast is still one circle, still on nobody's side, still dangerous for exactly one
tick. It does not touch venom, which declares the same numbers it always did.

**Why the side had to become a declaration.** G0034 rejected "a slick on nobody's side, to match the
blast", and its revisit trigger named the case that would reopen it: something on the player's side
wanting to ruin ground. That case is here - his own grenade - and the reasoning behind the original
rejection survives it intact. Venom is sided because nothing on the player's side fires venom, so
there was no free button to make; fire is neutral for exactly the reason a blast is, because it is
what a blast leaves and a throw that could not catch the thrower is a free button. One mechanic, two
declarations, and each is right for its own reason.

**Why swelling is not a damage curve.** G0034 refused damage that ticks or falls off, on pillar 1:
"am I in it" must be a question the screen answers. A ramp changes the circle, not what being inside
it costs - inside is still decisive and outside is still nothing, at every instant, and the instant's
circle is on the screen. It is the same rule applied to something that moves.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A `Fire` component and a `FireSystem` of its own.* The tidiest-looking answer and it was
nearly taken. Rejected because every rule it would need already exists on the slick, so what it buys
is a second place for those rules to drift apart - and the day somebody tunes one, the other stops
matching.

**No:** *A blast that lingers, or a second lethal window on the `Blast` itself.* G0018 refused it and
still does: running through the tail of an explosion has to be safe, and a flash that kills for its
whole life is a trap whose edge in *time* the player cannot see. Fire is a different circle in a
different place at a different moment.

**No:** *Fire that spares the side that caused it.* It would make his own grenade a free button, which
is the whole of why a blast is on nobody's side (G0018), and would be the second rule in the game
that has to be learnt by being caught by it.

**No:** *Fire on the flamethrower.* Asked for and already true: it leaves no blast, so it leaves no
fire, and an exception written anywhere would be a second answer to a question nothing asks. The day
a flame pellet names a `blastRadius` it gets fire, which is correct rather than a bug.

**No:** *A `growFraction` per explosive - a barrel's fire different in shape from a grenade's.* What
makes a barrel's fire worse is that the barrel's blast was wider, which the fractions already say.
One block, or a new explosive arrives with a hole in it.

**No:** *Avoidance asking whether something is a slick.* G0034 refused it and this does not do it:
`Collider.avoided` is a flag decided where the thing was spawned, and `AvoidanceSystem` still asks no
question about what anything is.

**Revisit when** something wants ground that is dangerous to one side and steered around by the
other, or when a level wants fire that spreads. The first needs the avoidance flag to become a pair
question the way `shunned` already is; the second is a different mechanic and this shape cannot
express it.
