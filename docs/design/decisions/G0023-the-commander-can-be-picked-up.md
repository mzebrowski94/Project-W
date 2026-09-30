# G0023: The commander goes down like his men, because now somebody comes for him

**Trigger.** Changing what happens when the commander runs out of health, changing who can revive,
or proposing that the squad do anything with a body on the ground.

**Enforced by.** `CasualtyTest.leavesTheCommanderShotDownOnTheGroundRatherThanDead`,
`.killsTheCommanderOutrightWhenTheBlowIsOneThatKills`, `AidTest.picksTheCommanderUpToo`,
`.putsAFallenComradeBackOnHisFeet`, `.leavesHimWhereHeLiesWhenNobodyIsWilling`,
`.neverBreaksOffAFightToFetchSomebody`, `.holdsWhereItWasToldToRatherThanGoingToFetchSomebody`.

**Constraint.** Gunfire and blasts put the commander on the ground with a bleed-out clock, exactly as
they do a trooper; claws, a sweep and a leap still kill him outright, exactly as they kill everyone.
**This supersedes G0020's rejection of a casualty commander**, whose revisit trigger was precisely
this: a bleed-out nobody can answer is an animation promising a rescue the game cannot deliver, and
now the squad answers it.

A trooper with nothing better to do goes and picks somebody up. Whether it goes is a roll -
`AiChance.AID_A_TROOPER`, or the much higher `AID_THE_COMMANDER` - taken once, on the way in, and
never again while it is on its way. The ranking is **fight, order, casualty, objective**: it never
turns its back on something shooting at it, it never breaks a standing order to do it (G0017), and it
always prefers a body to a square.

The work itself is one piece of code for everybody. The commander on his key and a trooper on its own
judgement both go through `Aid`, so a man picked up by a trooper stands up with exactly what a man
picked up by the commander stands up with: the rules are about the man on the ground, not about who
reached him.

**Him going down still releases everything**, and now "down" means bleeding as well as dead. One
call - `Drivers.livingTarget` - is what that rule is actually made of, so extending it there covered
the pack, the firing line and the squad at once. Without it the things that put him down would finish
him where he lay and the rescue would be something the game showed you and never let you reach.

**Scope.** Who can be a casualty and who will come for one. It says nothing about hauling: only the
commander drags, and nobody drags him.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A trooper that always comes.* Reliable, and it turns a rescue into a queue: the player stops
deciding whether to cross the ground, because somebody else always will. A roll makes the squad's
help a thing that happens to you rather than a service you can count on, which is the only version
that leaves the decision where pillar 3 wants it.

**No:** *Re-rolling every tick of the approach.* Cheaper than committing, and it reads as a bug: men
setting off for a body and thinking better of it halfway. The roll is a decision, so it is taken
once.

**No:** *Aid outranking a standing order.* Tempting, because a body is more urgent than a corner. An
order a body on the ground could override is not an order, and "hold here" would stop meaning
anything the moment anybody was hurt (G0017).

**No:** *Aid outranking a fight.* A squad that turned its back on something shooting at it to fetch a
man would lose both.

**No:** *A separate revive with its own numbers for the squad.* Two copies of "how long it takes, what
he gets back, how long he is protected" is how a man revived by a trooper quietly comes up with a
full bar.

**No:** *Letting a trooper haul a body.* Dragging costs a hand and a mode, and a trooper that could do
it would need rules about where it takes him. Treating him where he fell is the squad's whole
repertoire for now.

**No:** *A commander who bleeds out into a respawn that is faster or slower than the ordinary one.* The
bleed-out already is the second chance. Two clocks doing the same job is one clock too many.

**Revisit when** local co-op lands. A second player changes who is nearest, whether the squad should
bother at all, and whether a man can be hauled by somebody who is not the commander - and all three
are decisions this record deliberately did not take.

## Amended 2026-09-29 by G0053

**What he gets back is every wound cleared**, rather than a fraction of his health: a man has no health
now. Still one revive path, `Aid`, with one set of numbers, and still the same protection afterwards.
`sim.casualty.reviveHealthFraction` is gone.
