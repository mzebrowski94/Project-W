# G0018: The blast is one circle, and things explode by dying

**Status.** amended by G0053: each blast kind declares its harm to a man; amended by G0082: a thing with health shrugs off its blast resistance; amended by G0108: a dash's surge is a second, non-lethal radial effect and no blast; amended by G0130: a blast takes the damage its source declares

**Trigger.** Adding or changing anything explosive - a weapon with an area, a hazard, a death that
hurts what is near it - or proposing that damage fall off with distance.

**Enforced by.** `BlastTest.killsEverythingStandingInsideIt`,
`.leavesAnythingStandingOutsideItUntouched`, `.catchesTheCommanderAsReadilyAsAnEnemy`,
`.killsABruteAsReadilyAsAGrunt`, `.doesNotReachThroughRock`,
`.catchesWhatWasStandingInItAndNothingThatWalksInAfterwards`.

**Constraint.** An explosion is a `Blast` entity: one circle, uniformly fatal inside it and harmless
outside it, on nobody's side, blocked by rock, and dangerous on exactly the one tick it appears. The
circle that kills is the entity's own `Body`, which is the same circle the renderer draws.

Anything that explodes does so by **dying**. It carries `BlastsOnDeath`, and one system leaves a blast
where a carrier fell. A grenade out of flight, a barrel that is shot, swept or bitten into - all of
them reach the same explosion through the same death.

**Scope.** Every explosion in the game. It says nothing about how big any particular blast is: a
grenade and a barrel name their own radius, and `sim.blast.*` holds only what is true of all of them.
It says nothing about what a blast looks like either - the flash rides `Appearance.charge` and the
jolt rides `Shake`, and both are the client's to interpret.

**Why uniformly fatal.** Pillar 1. A curve the player cannot see turns "am I clear of that?" into
arithmetic, and the answer to a damage curve is to stop and compute rather than to move. One circle is
a rule that can be read off the screen at a glance and learnt in one death.

**Why on nobody's side.** Pillar 2 says the commander is a unit standing on the field. A grenade that
could not kill him would be a free button, and the existing rule that damage crosses faction lines
already permits it: a neutral source is friendly to nobody, so no exception had to be written.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Damage that falls off with distance, or a lethal core inside a wounding ring.* The realistic
answer, and the first thing anybody will propose. Rejected on pillar 1: two circles the player cannot
see separately is a calculation, and whether you survived then looks arbitrary rather than earnt. It
also quietly requires every blast damage number to be balanced against every unit's health, where one
number far above all of them needs balancing against nothing.

**No:** *A blast that spares the side that caused it.* It makes the grenade a button with no cost,
removes the only thing that makes where you are standing matter when you throw one, and needs a new
exception in a damage path that currently has none.

**No:** *The blast as an ordinary `ContactDamage` body in the collision path.* Structurally the tidiest
answer - a strike already works exactly this way (G0005) - and it was very nearly taken. Rejected
because `SimConfig.largestRadius` sets the collision grid's cell size from the widest body in the
game, so a twelve-unit blast would triple the cell size for the whole level in order to serve a
quarter of a second of effect. A control point already makes the same trade for the same reason, and
says so.

**No:** *Spawning the blast directly from whatever caused it.* One line in the grenade system and one
in the pickup system, instead of a component and a reaction. Rejected because it is two paths to one
explosion on the day it is written and four by the time anything else explodes, and each of them is
somewhere a rule about blasts can quietly differ.

**No:** *A blast that keeps hurting for as long as it is drawn.* The flash lasts a quarter of a second
and it would be free to make the whole of it lethal. Rejected because it leaves a trap on the ground
whose edge in *time* the player cannot see - running through the tail of an explosion has to be safe,
or the tell has stopped being honest.

**Revisit when** something wants an explosion that is not fatal - a concussion, a door charge, a
weapon meant to wound - because that is the first thing this shape genuinely cannot express, and the
answer then is a second kind of blast rather than a curve added to this one.

## Amended by G0053

**The revisit this record asked for.** A grenade is now meant to wound a man rather than put him down,
and the answer is the one given above: a second *kind* of blast, not a curve. Each blast kind declares
the harm it does to a man - a grenade's `WOUND`, a barrel's, a mine's and a charged corpse's `DOWN`.
Inside the circle there is still one meaning for a man and outside it none; everything with health is
still caught by the same decisive amount, so a grenade kills every alien it catches exactly as before.
It is still not a free button: it costs its thrower a wound, and a second one puts him down.

## Amended by G0108

**The second radial kind, and not a blast.** A Rocketman's surge stuns every alien round a launching dash and harms
nothing. It is not a `Blast`: it is on one side, lands a harmless blow through `Damage` and leaves no explosion. A blast
is still one circle, on nobody's side, fatal inside it.

## Amended by G0082

The circle is still uniform, but not uniformly fatal: a thing with health takes the blast's damage less its
`Health.blastResistance` per cent, rounded by `Whole`, read in `BlastSystem` alone. Zero for everything but a
turret (90), which a blast scars rather than takes. A man has no health and takes the blast's harm as before.
Provisional, for review.

## Amended by G0130

**A blast takes the damage its source declares**, as G0053 made it take its harm. Still one circle, uniform inside
it, on nobody's side, by dying. `sim.blast.damage` is what every source declares unless a gun moves it: a rocket's
and a limpet's are their gun's, and Firepower raises them with it (G0127). Provisional, for review (AFK run).
