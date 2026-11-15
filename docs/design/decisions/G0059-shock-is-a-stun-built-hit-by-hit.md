# G0059: Shock is a stun built hit by hit, and the boss takes none

**Status.** amended by G0095: shock scales by the body's area, from the smallest body to the largest

**Trigger.** Giving a gun a stun that needs several hits, changing what shock does while a stun runs, or
letting the boss be stunned.

**Enforced by.** `ShockTest.stunsAThingOnceEnoughBoltsHaveLandedOnIt`, `.leavesAThingActingUntilTheLastBoltLands`,
`.takesMoreBoltsToStunABiggerBody`, `.emptiesWhenTheStunLands`, `.buildsNothingWhileTheStunRuns`,
`.drainsOnceNothingHitsIt`, `.showsItOnTheChargeRing`, `.neverBuildsOnTheBoss`,
`.stunsOnEveryHitForAGunThatNamesOneHit`, `SystemOrderTest.drainsShockTheSystemAfterTheHitsThatBuiltIt`.

**Constraint.** A gun's `stunAfterHits` is how many of its shots fill a body of `sim.shock.referenceRadius`
with **shock** (`Shock`); full shock is the gun's ordinary declared stun of `stunSeconds` (G0053). Each
hit is worth `1/stunAfterHits` scaled by reference radius over body radius - a bigger body takes more, in
proportion to its radius, as a bloat does (G0036). Zero or one is a stun on every hit, which is what every
gun but the pulse rifle names. It is built where the blow lands (`DamageSystem`), after the shield: a
shield takes the hit, the shock included.

- **The stun empties it, and nothing builds while a declared stun runs**, so a held trigger stuns, lets
  go, and has to build again - an opening, never a stun-lock.
- **It drains** at `sim.shock.drainPerSecond` on any tick no hit built it (`ShockSystem`, straight after
  `DamageSystem`), and empties on anything already down.
- **Drawn on the charge ring** in its own look, below a bloat (pillar 5).
- **The boss takes none**: a strain's `shockable`, false only for it. G0048 rules out a barrage that pauses
  when the boss is stunned, so a stun on it would be a state drawn on it that does nothing.

**Why.** Mateusz asked for a gun that stuns, without the stun on every hit that would hold anything it is
pointed at still for ever. Building the stun out of hits makes it a reward for staying on one body, which
is what the beam already rewards, and costs no new state on the man or the gun.

**Accepted cost.** A grunt usually dies a few bolts after its stun lands, so the gun's stun is mostly
worth something on a brute. Shock is carried by every man too, and nothing on the alien side builds it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A stun on every bolt.* A held trigger is then a stun-lock.

**No:** *Shock that builds while the stun it filled to runs.* The same stun-lock, one stun later.

**No:** *A boss that takes shock.* Its barrage does not pause (G0048); the stun would be a lie on the screen.

**No:** *A `Boss` check, or a shock count per strain.* What the boss is, is its numbers (G0048); what
differs between the rest is already their size.

**Revisit when** something on the alien side gets a gun that shocks, or a boss whose patterns a stun
should interrupt.
