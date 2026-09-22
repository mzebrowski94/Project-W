# G0036: A corpse is ammunition, and the beam is what loads it

**Trigger.** Adding or changing anything that does something to a body after it is dead, proposing
that the laser kill by inflation, or proposing a second gun that pumps. It narrows nothing and
touches G0018 only by using it.

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
gun as `bloatPerShot`, zero on every other gun. Four rules, and each is a field rather than a branch:

- **The charge builds on the living too, at a fraction of the rate, and is drawn there.** That
  fraction is where "partly charged beforehand and finished later" lives: burning something down
  leaves its body part of the way there and a decision still to make about it. At one, anything
  killed with the beam arrives nearly full and there is nothing left to choose; at nothing, the kill
  counts for nothing and every corpse starts from scratch.
- **A thing being charged does not settle.** Only what nothing is pointed at falls away. Draining it
  on the ticks the beam is *on* it makes the gun fight the clock rather than the body, and the clock
  wins on anything large: a bigger body takes the beam more slowly while the clock empties it at the
  same rate.
- **What fills is drawn on the thing, and the body is never touched.** A bright half-transparent
  ring growing out past the body, on the charge channel, sized entirely in the client. It is the one
  mark in the game bigger than what it is drawn on, because it is the one that is about to kill
  whatever is standing nearby rather than something the creature is doing.
- **Only something already dying bursts.** A creature at the brim is still a creature: a gun that
  killed by inflation would be a gun with two ways of killing and one bar to pay for both, and
  health would stop deciding anything.
- **A bigger body takes proportionally longer and leaves a proportionally wider circle**, off one
  reference radius. In proportion to the radius rather than the area, deliberately: squaring it makes
  a brute four times the job rather than half again, and what the player has to feel is that a bigger
  creature is *a bit* dearer, not that it is a different mechanic.
- **It bursts by dying**, through the one death reaction everything that explodes uses (G0018).
  `BloatSystem` writes a radius onto the `BlastsOnDeath` every unit already carries at zero and marks
  the body dead. There is no second path to a blast.
- **It leaves nothing burning.** G0035 made the *side* of what a blast leaves a declaration rather
  than a property of the mechanic; this makes its *existence* one, for the one case that wanted it. A
  corpse bomb that fenced off the ground it went off on is a tool the player cannot use twice in the
  same place. Still one reaction and one death - a field read before the death, not a second path.
- **Let go and the charge falls away**, and while there is any of it left the corpse waits. A
  half-filled body is something to come back to rather than something that had to be finished in one
  go.

**Scope.** Every unit in the game, the commander included - the component is on everything that can
be killed, and nothing on the alien side happens to carry a gun that fills it. It says nothing about
how long any particular body waits or how wide the circle is; one `sim.bloat.*` block does. It does
not touch what kills: health still decides that, and a body has to be dead before any of this is
worth anything.

**The balance is the heat bar, and it is the only balance there is.** Filling a body costs about
three seconds of continuous beam against about four seconds of bar - so turning a corpse into a bomb
is three quarters of a bar not spent on what is still walking at you, and there is no way to have
both. That ratio is one number, `sim.weapon.laser.bloatPerShot`, and it is the difficulty knob for
the whole mechanic. It is why the invariant that the beam runs hotter than anything else he can pick
up is enforced rather than trusted: the day another gun runs hotter, none of this costs anything.

**Why a charged thing does not settle** - amended 2026-09-22, after a playtest. The first version
settled every tick a body was dying, fed or not. The arithmetic nobody did: a grunt's corpse gained
0.34 of a ring per second from the beam and lost 0.32 to the clock, and a brute's - whose bigger body
takes the beam proportionally more slowly - gained 0.24 and lost the same 0.32, so it went *down*
while it was being burnt. The mechanic was unreachable and read as broken rather than as expensive.
The rule it needed is one bit on the component and one sentence: what settles is what nobody is
pointing at.

**Why the ring ramps from the body rather than from nothing** - same playtest. Scaled from zero, the
ring spends every charge under two thirds *inside* the creature it is drawn on; nothing reached two
thirds while alive, so on a runner it was a dot a third the size of the unit and on everything else
it was hidden. A ring that starts at the body's own size and grows outwards is a halo from the first
shot, which is what a charge meter drawn on a moving target has to be.

**Why the ring and not the body** - amended 2026-09-22. The body swelled to nearly twice its size
when this was first built, on the argument that the body *is* the drawing, exactly as a slick's is.
That argument is right about a slick and wrong here: a slick's body is the circle that hurts you, and
a corpse's body is a thing every other body in the world has to be solved against. A swelling corpse
shoved everything standing near it, changed what a doorway was worth for a second at a time, and made
the blast's own footprint depend on when you looked at it. The tell needed to be big; the *body* did
not, and a ring drawn over the top is the same tell at none of the cost.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A `Corpse` or `Inflating` component and a system of its own for the explosion.* Everything
it would need exists: the body, the throes, the death reaction. What a second one buys is a second
place for the rules to drift apart.

**No:** *A living unit that bursts when its charge fills.* It makes health beside the point and gives
one gun two ways of killing. The charge is allowed to reach the brim on something alive - that is the
reward for committing to the kill - and it sits there until the thing dies.

**No:** *A blast radius per strain, and a fill rate per strain.* Both already vary, because both are
quoted against the body and the body is what differs between strains. A table of them would be a
table nobody tunes and a new strain arriving with two holes in it.

**No:** *A body that physically swells.* Tried, shipped, and taken out again - see above. The cost
lands on the solver and on every unit standing near the corpse, and none of it is visible as a
decision.

**No:** *Fire behind a corpse bomb, because every other explosion leaves some.* The point of blowing
a body in a doorway is the doorway; fire would take it away for the next ten seconds from whoever set
it off. It is a declaration on the reaction rather than a clause in a system, so nothing else had to
learn that the exception exists.

**No:** *A corpse that bursts on its own once charged, with no second look from the player.* It would
turn a decision into a delayed-action trap the player set by accident, and the whole of what makes
the mechanic hard to master is that the last of the charge is the part you have to choose to spend.

**No:** *Charge that decays while the thing is still alive.* It would make the mechanic a reflex test
- kill it within a second of the last shot or lose the work - rather than a decision about where a
heat bar goes.

**No:** *A blast that spares the side that made it.* G0018 refused it and still does. A body you
inflated goes off on nobody's side, so standing over your own work is your own doing.

**Revisit when** something on the alien side gets a gun that pumps, which would make a trooper's body
something the other side can use, or when a strain wants a corpse worth visibly more than another's.
The first works today and has never been played; the second is the `UnitDef` field above.
