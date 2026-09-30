# G0036: A corpse is ammunition, and the beam is what loads it

**Status.** accepted

**Trigger.** Adding or changing anything that does something to a body after it is dead, proposing
that the laser kill by inflation, or proposing a second gun that pumps.

**Enforced by.** `BloatTest.chargesUpAThingTheBeamHasBeenHeldOn`, `.neverTouchesTheBodyItIsDrawnOn`,
`.showsItOnSomethingStillOnItsFeet`, `.leavesSomethingStillOnItsFeetStanding`,
`.takesLongerToFillABiggerBody`, `.leavesAWiderCircleForABiggerBody`,
`.burstsOneThatFillsAndCatchesWhatIsStandingBesideIt`,
`.leavesABystanderOutsideTheCircleStanding`, `.leavesNothingBurningWhereItWentOff`,
`.stillBurnsTheGroundWhereAnOrdinaryExplosionWent`,
`.keepsAChargedCorpseOnTheFieldLongAfterItsThroesWouldHaveEnded`,
`.letsItDownAgainOnceTheBeamComesOff`, `.doesNothingAtAllForAGunThatDoesNotPump`,
`SimConfigTest.runsHotterThanAnyOtherGunAManCanCarry`.

**Constraint.** Sustained **laser** on a thing fills a `Bloat` - a fraction per shot, declared on the
gun as `bloatPerShot`, zero on every other gun. Each rule is a field rather than a branch:

- **The charge builds on the living too, at a fraction of the rate, and is drawn there.** Burning
  something down leaves its body part of the way there and a decision still to make about it. At one,
  anything killed with the beam arrives nearly full and there is nothing left to choose; at nothing,
  every corpse starts from scratch.
- **A thing being charged does not settle.** Only what nothing is pointed at falls away. Draining it
  while the beam is on it makes the gun fight the clock rather than the body, and on a brute the clock
  wins: it takes the beam more slowly and would lose charge while being burnt.
- **What fills is drawn on the thing, and the body is never touched.** A bright half-transparent ring
  that starts at the body's own size and grows outwards, on the charge channel, sized entirely in the
  client - a halo from the first shot, not a dot hidden inside the creature. It is the one mark in the
  game bigger than what it is drawn on, because it is the one about to kill whatever stands nearby. A
  body that swelled instead would shove everything near it and make the blast's footprint depend on
  when you looked.
- **Only something already dying bursts.** A creature at the brim is still a creature: a gun that
  killed by inflation would have two ways of killing and one bar to pay for both.
- **A bigger body takes proportionally longer and leaves a proportionally wider circle**, off one
  reference radius - in proportion to the radius, not the area, so a brute is *a bit* dearer rather
  than a different mechanic.
- **It bursts by dying**, through the one death reaction everything that explodes uses (G0018).
  `BloatSystem` writes a radius onto the `BlastsOnDeath` every unit carries at zero and marks the body
  dead. There is no second path to a blast.
- **It leaves nothing burning.** Whether a blast leaves fire is a declaration (G0035); a corpse bomb
  that fenced off the ground it went off on is a tool the player cannot use twice in one place.
- **Let go and the charge falls away**, and while there is any left the corpse waits: a half-filled
  body is something to come back to.

**The balance is the heat bar, and it is the only balance there is.** Filling a body costs about three
seconds of beam against about four seconds of bar, so a corpse bomb is three quarters of a bar not spent
on what is still walking at you. That ratio is one number, `sim.weapon.laser.bloatPerShot`, the
difficulty knob for the whole mechanic - which is why "the beam runs hotter than anything else he can
pick up" is enforced: the day another gun runs hotter, none of this costs anything.

**Scope.** Every unit, the commander included - the component is on everything that can be killed, and
nothing on the alien side carries a gun that fills it. How long a body waits and how wide the circle is
are one `sim.bloat.*` block. It does not touch what kills: health still decides that.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A `Corpse` or `Inflating` component and a system of its own for the explosion.* Everything it
would need exists: the body, the throes, the death reaction. A second one is a second place for the
rules to drift apart.

**No:** *A living unit that bursts when its charge fills.* It makes health beside the point and gives one
gun two ways of killing. The charge may reach the brim on something alive and sits there until it dies.

**No:** *A blast radius per strain, and a fill rate per strain.* Both already vary with the body, which is
what differs between strains; a table of them is a table nobody tunes.

**No:** *A body that physically swells.* Built once and taken out: the cost lands on the solver and on
every unit standing near the corpse, and none of it reads as a decision.

**No:** *Fire behind a corpse bomb.* The point of blowing a body in a doorway is the doorway.

**No:** *A corpse that bursts on its own once charged.* It turns a decision into a trap the player set by
accident; the last of the charge is the part you choose to spend.

**No:** *Charge that decays while the thing is still alive.* It makes the mechanic a reflex test rather
than a decision about where a heat bar goes.

**No:** *A blast that spares the side that made it.* G0018 refused it and still does: a body you inflated
goes off on nobody's side.

**Revisit when** something on the alien side gets a gun that pumps, which would make a trooper's body
something the other side can use, or a strain wants a corpse worth visibly more than another's.
