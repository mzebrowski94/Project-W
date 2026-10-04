# G0031: A nest is a place that produces, and it sleeps until you arrive

**Status.** amended by G0062: a nest wakes only for its own biome, and its first brood comes soon

**Trigger.** Anything that puts creatures on the field after the first tick, or that changes what a
level does while the player is somewhere else.

**Enforced by.** `NestTest.sendsOutNothingAtAllUntilSomebodyComesNear`,
`.startsWorkingOnceSomebodyComesNear`, `.goesOnWorkingAfterWhoeverWokeItHasGone`,
`.goesOnSendingThemOutPeriodAfterPeriod`, `.emptiesTheLastOfItselfWhenItIsDestroyed`,
`.sendsOutWhateverStrainItWasBuiltFor`, `.sendsEverythingWithinEarshotToSeeWhoIsHittingIt`,
`.leavesALevelUnclearedWhileItIsStillStanding`.

**Constraint.** A **nest** is an entity on the field - solid, shootable, on the alien side - that
sends a **brood** of one named strain out of itself every so often. It does nothing at all until
somebody on the player's side comes inside its wake radius, and once woken it never stops. Destroying
one empties a last brood out of it and **rouses** everything within earshot. It carries the same
`Enemy` marker every other hostile thing carries, so a level is not clear while one is standing.

**Why it is a place rather than a schedule.** A `Waves` list is a *clock* the level owns: it happens
to you wherever you are, and the only thing you can do about it is survive it. A nest is a *thing*,
which means it can be found, prioritised, walked to and taken away - and that is the whole of what it
adds. Clearing ground stops being an errand and becomes a question about whether you dealt with what
was filling it in behind you. The same mechanic expressed as a timer would have been a third of the
code and none of the game.

**Why it sleeps.** `AdventureScenario` is six hundred world units across. A nest producing from the
first tick would have sent twenty creatures at empty ground before the squad reached its biome, and
what the player would meet is an accumulation - a wall of grunts with no visible cause, arriving from
a direction rather than from a thing. Sleeping makes the nest legible: you see the hole, and then you
see it work.

**Why waking latches.** A nest that went quiet when you backed out of its radius would be a nest you
could wait out from a stride outside it, and the counter-play to a producer would be standing still.
That is pillar 1's problem: a mechanic whose answer is to stop and wait belongs in a different game.

**Why it is worst when you kill it.** A producer you can switch off from range for free is a chore
with a health bar. The death brood and the shout are what turn destroying one into a question of
*when* - with a squad, with a grenade in hand, with somewhere to back into - rather than a thing you
do whenever you happen to have line of sight.

**Scope.** It says nothing about a cap on how many a nest may have alive at once, nothing about a
nest being buildable or capturable by either side, and nothing about what a nest does on a level with
a **post**. It also does not change what a **wave** is: a level may have both, and they answer
different questions.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A spawner as a timer on the level entity.* That is `Waves`, it already exists, and it is a
different mechanic - the whole value here is that the producer is somewhere on the map.

**No:** *A nest that works from the first tick.* Tried in the reasoning above and rejected on a long
level: what arrives is an accumulation, and the player never meets the nest that caused it.

**No:** *A nest that goes back to sleep when nobody is near.* It makes waiting the counter-play.

**No:** *A cap on how many of its brood may be alive at once.* It reads as a producer that has given
up, and it hides the cost of ignoring one - which is the cost the mechanic exists to charge. If the
count turns out to be unplayable the answer is `periodSeconds`, which is one number in one file.

**No:** *A `NestKind` enum, or one nest kind per strain.* What comes out of a nest is a `UnitKind`
named where it is put down. A second table indexed by the first would have to be kept in step with it
for no new expressiveness (G0008).

**No:** *A second rouse path of its own.* A nest being hit and a point being taken mean the same thing
to a creature, and `Rouse` is the one place that answers it (G0016). Two copies is one chance to
drift.

**No:** *Reading `Damage` to know it has been hit.* Damage is applied and cleared a dozen systems
before anything about a nest runs. A bar that went down since last tick is the evidence that
survives, and it needs no reordering to read.

**Revisit when** a level with nests has been played end to end. Two things to watch: whether thirty
seconds is a pulse or a nuisance, and whether a nest woken by a trooper the player has sent ahead of
him reads as his own doing or as the level cheating.

## Amended by G0062

**A nest wakes for its own biome.** "Comes inside its wake radius" becomes *is in its biome and within its
wake radius*: on a level walled into biomes the radius reached through the wall. Its first brood comes
`sim.nest.firstBroodSeconds` after waking rather than a whole period, because waking on arrival with a
thirty-second first brood was a hole that did nothing while you stood at it. A map that names no biomes is
one biome, so a level without them is unchanged.
