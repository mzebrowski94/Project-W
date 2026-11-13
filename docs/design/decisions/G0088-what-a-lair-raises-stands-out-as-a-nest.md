# G0088: What a lair raises stands out as a nest does

**Status.** accepted

**Trigger.** Changing how troopers pick out the boss or anything else a lair raises; giving a strain a reach of its
own in a trooper's choice; retuning `sim.nest.standsOut`, `threat` or `kept` with the keep in mind.

**Enforced by.** `LairTest.raisesABossTheSquadPicksOutAsItWouldANest`, `.raisesItsCreaturesWeighedAndKeptAsItsNestsAre`.

**Settled on Mateusz's behalf, 2026-11-13** (AFK run, provisional, for review), from a bug he reported on
2026-11-12 (ticket 10 of the playtest-feedback map): *in the keep, troopers do not fight the boss or the nests round
it.*

**The cause.** Nothing about the lair kept them off: everything it raises is on the field the tick it wakes, and the
nests stand out (G0080, G0085). The **boss out-ranged a trooper's notice**: a man picks a target out within his
sight (27) of its centre, but the boss is six across, notices at 58 and spews to 32 from his body - so a squad stood
inside his barrage, being hit, saw nothing to shoot, and once the nests round him were down shot nothing at all.
Measured on the shipped keep: four troopers 38 from the boss in five seconds took 50 off him.

**Constraint.** **A trooper notices what a lair raises on the nest's terms** (G0085): `LairSystem` gives every creature
it puts down the nest's `standsOut`, `threat` and `kept`, on the `Enemy` every target carries - picked out from 54,
weighed at its own distance, kept until it dies or something is inside a man's all-round range. Nothing new is
tuned: the boss is on the same terms as the holes round him, and they move together. What the lair's nests breed is
not the lair's and walks as anything else does.

**Why the lair's, not the strain's.** A reach on a strain's definition would be a field on every unit block for the
one strain that asked for it, and the boss in a proving-ground setup is fought from wherever the player puts him.
In the keep he is the place the march ends, as a nest is a place - the lair is what makes him one.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A trooper's sight raised for the keep.* Ticket 18 brought it down to 27 on purpose (G0085); the boss alone
out-ranged it.

**No:** *Measuring a target's distance to its edge.* It moves every target's pick-out at once to fix one; revisit if
a big walking strain shows the same gap.

**Revisit when** a strain outside a lair out-ranges a trooper's notice the same way.
