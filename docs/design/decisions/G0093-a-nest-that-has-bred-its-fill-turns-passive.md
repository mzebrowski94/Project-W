# G0093: A nest that has bred its fill turns passive

**Status.** accepted

**Trigger.** Capping, pausing or speeding up what a nest sends; counting a nest's creatures; a second way for a nest
to slow down.

**Enforced by.** `NestTest.breedsFarSlowerOnceItHasPutItsFillOnTheField`,
`NestTest.keepsItsPaceForGoodWhenThereIsNoActiveBrood`.

**Provisional, for review** - asked for by Mateusz after a playtest ("15-20, then very, very slowly"); the numbers
are not yet played.

**Constraint.** A nest counts the creatures its broods put on the field (`Nest.hatched`). On the brood that brings
the count to `sim.nest.activeBrood` (18) it turns **passive**, for good: its period is `sim.nest.passiveSlowdown` (10)
times as long from that brood on - thirty seconds becomes five minutes. It stacks with the left-behind slowdown
(G0081). Its death brood is not counted and is never slowed: a nest is still at its worst the moment you kill it.
Zero `activeBrood` is off.

**Why a slowdown and not a stop.** G0031: a nest left alone still costs. A passive nest still breeds, so closing it is
still worth something, but camping one for kills is no longer an endless stream.

**Why counted from what it hatched, not what is alive.** Counting the living would make a nest a spawner topped up to
a level - kill its brood and it is back at full pace - which rewards farming it. What it has already sent is a fact
about the nest alone, needs no scan of the field, and is the same on every machine.

**Scope.** Every nest, the finale's lair nests included. Not waves, not the lair's own arrivals.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A cap on the living creatures from one nest.* See above; it also needs every creature to remember its nest.

**No:** *A passive nest that stops breeding.* G0031.

**Revisit when** a level wants a nest that never tires, or the cap is played and found to make nests ignorable.
