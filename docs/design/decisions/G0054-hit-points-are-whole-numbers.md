# G0054: Hit points are whole numbers

**Status.** accepted

**Trigger.** Adding a health or a damage anywhere in `sim`, a stat that scales one, or proposing a
fractional hit point - a regeneration rate, damage over time in fractions, a percentage that should not
round.

**Enforced by.** `ArchitectureTest.hitPointsAndDamageAreWholeNumbers` and
`.hitPointsAndDamageAreHandedOnWhole`: no field and no method result named for health or damage, no
`amount` a blow owes and nothing in `Health`, is a `float` or a `double` - checked against a deliberate
violation when it was written. `WholeTest` holds the rounding rule.

**Constraint.**

- **Every hit point and every point of damage the simulation holds is an `int`**: `Health`, `Damage`,
  `ContactDamage`, `Blast`, every definition and config that names one.
- **A fraction arises only where a percentage meets a whole base** - the swarm's health
  (`sim.swarm.healthPercent`), a gun's damage upgraded (`GUN_DAMAGE`) - **and is rounded there, once,
  by `Whole.percentOf`**: to the nearest, halves up, and a base above zero never to zero. Integer
  arithmetic, so the result is exact.
- Positions, speeds, radii, timers in seconds and the charge meters stay `float`. They are continuous,
  never compared against a kill, and deterministic on the JVM.

**Why.** Not speed and not online play. Since Java 17 (JEP 306) every float operation is strict IEEE on
every platform, the simulation bans the transcendental functions that are not, and a peer on a different
build is refused - so floats already give the same answer everywhere, which is what an engine that forbids
them (Photon Quantum's fixed point) is buying on C#. The reason is **the
breakpoint**: how many shots a thing takes is the number the player learns, and a percentage upgrade on a
float (a laser's 7 at +15 % is 8.0499...) moves it by an ulp nobody can see. Whole numbers make "four
sidearm shots kill a grunt" a fact, readable in the config and exact in a test - the same answer
`Fold.scaleTicks` already gave a duration.

**Scope.** Hit points and damage. It says nothing about what a man has - he has wounds (G0053) - and
nothing about the bloat charge, which is a fraction of a body's capacity rather than a hit point.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Fixed-point hit points* - hundredths, or 256ths. Every value the
game ships is already whole; the fraction only ever came from a percentage, and it is rounded where it is
made. Fixed point would be machinery with nothing to hold.

**No:** *Floats with an epsilon on the kill test.* It hides the ulp rather than removing it, and every new
comparison has to remember the epsilon.

**No:** *Rounding where the damage lands* rather than where the percentage is applied. A number rounded in
two places is two rules; the one place is `Whole.percentOf`.

**Revisit when** something wants a hit point smaller than one - regeneration a point at a time is still
whole; a fraction a tick is not - or when a client that is not on the JVM joins a session, which would
put every float in the simulation in question, not only these.
