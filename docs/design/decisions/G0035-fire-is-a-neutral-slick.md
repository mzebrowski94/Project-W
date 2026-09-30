# G0035: Fire is a neutral slick left by the blast dying

**Status.** amended by G0046: whatever a slick can catch gives it room, and a unit standing still steps off it; amended by G0053: fire wounds a man rather than putting him down

**Trigger.** Adding or changing anything that leaves ground dangerous after an explosion, or proposing
that a slick's side stop being a declaration and go back to being a rule.

**Enforced by.** `FireTest.burnsTheGroundWhereAnExplosionWent`,
`.leavesNothingBurningWhereNothingExploded`, `.burnsWhoeverWalksIntoItWhicheverSideHeIsOn`,
`.woundsAManStandingInItRatherThanPuttingHimDown`, `.goesOutOnceItHasBurnedDown`,
`.leansOffBurningGroundAsReadilyAsOffARock`,
`.swellsAndThenCollapsesRatherThanBeingOneCircleThroughout`, `.leavesNothingBurningInsideRock`.

**Constraint.** **Amends G0034**, answering its revisit trigger; G0018 is untouched. Fire is a
**slick**, not a second mechanic: each circle is an ordinary entity with a `Position`, a `Body`, a
`Collider` and a `ContactDamage`, non-solid and immovable. Three things are declared differently from
venom, each a field rather than a branch:

- **It carries no side.** A `Blast` has no `Faction`, and the spatter takes the faction of whatever
  died, so fire is neutral without anything saying so. A slick belongs to whatever fired the shot, which
  for an explosion is nobody.
- **It is steered around.** `Collider.avoided` says whether a unit gives something room, defaulting to
  `solid`. Ground that catches everybody is given room.
- **It swells and collapses.** `Slick` holds a peak radius and two ramp fractions and writes the body
  each tick, so the circle that burns is the circle drawn **at that instant**.

It arrives as everything left behind arrives: the `Blast` carries `SlicksOnDeath`, and the death reaction
lays the spatter when the flash burns out. **Nothing that explodes had to learn that fire exists**, and a
gun that leaves no blast leaves no fire - which is why the flamethrower is an exception without a clause.
Every size is a fraction of the blast's own radius, in one `sim.fire.*` block, so a new explosive gets
fire without naming a number.

**Why the side is a declaration.** Venom is sided because nothing on the player's side fires it, so
there is no free button to make. Fire is neutral for the reason a blast is: it is what a blast leaves,
and a throw that could not catch the thrower is a free button.

**Why swelling is not a damage curve.** A ramp changes the circle, not what being inside it costs:
inside is decisive and outside is nothing at every instant, and the instant's circle is on the screen.

**Scope.** Every explosion. Not how long a fire burns - one block does - nor what one looks like beyond
the body being the drawing. Venom declares the same numbers it always did.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A `Fire` component and a `FireSystem` of its own.* Every rule it would need exists on the slick;
a second place for them is a place for them to drift apart.

**No:** *A blast that lingers, or a second lethal window on the `Blast` itself.* G0018 refused it:
running through the tail of an explosion has to be safe. Fire is a different circle at a different
moment.

**No:** *Fire that spares the side that caused it.* It would make his own grenade a free button (G0018).

**No:** *An exception for the flamethrower.* It leaves no blast, so it leaves no fire; the day a flame
pellet names a `blastRadius` it gets fire, which is correct.

**No:** *A `growFraction` per explosive.* What makes a barrel's fire worse is that its blast was wider,
which the fractions already say.

**No:** *Avoidance asking whether something is a slick.* `Collider.avoided` is decided where the thing
was spawned, and `AvoidanceSystem` asks no question about what anything is.

**Revisit when** something wants ground that is dangerous to one side and steered around by the other,
or a level wants fire that spreads. The first makes the avoidance flag a pair question, as `shunned`
is; the second is a different mechanic.
