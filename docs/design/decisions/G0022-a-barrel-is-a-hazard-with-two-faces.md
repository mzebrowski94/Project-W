# G0022: A barrel is a hazard the aliens respect, and worth two different things to them

**Trigger.** Changing how a barrel behaves, adding another pickup that is dangerous to take, or
proposing that avoidance treat some pickups differently from others.

**Enforced by.** `BarrelTest.isLeftStandingByAnAlienWithNoAppetiteForOne`,
`.leavesASmallerCircleWhenItIsBittenIntoThanWhenItIsShot`, `.catchesTheSameBystanderWhenItIsShotInstead`,
`AvoidanceSystemTest.leansOffABarrelAsReadilyAsOffARock`, `.walksStraightOverItsOwnDinner`,
`SpitterTest.swallowsABarrelAndFiresShotsThatGoOff`.

**Constraint.** A barrel is still a pickup and still explodes by dying (G0018), and three things
about who takes it and what they get are now true.

*It is shunned.* Avoidance steers a unit around it as it steers around a rock, and an idle unit no
longer walks over to eat it. That is a **narrowing of the invariant "nothing steers around a
pickup"**, which existed so a hungry alien would not circle its own dinner - which is still true of
everything edible. A barrel is not dinner.

*Touching one is an opportunity, not an outcome.* Taking a shunned pickup is rolled on
`AiChance.BITE_A_BARREL` while the two are actually in contact. Avoidance is an opinion about a
course rather than a wall, so a creature shoved into one in a crowd, or halfway through a charge,
still ends up against it - and then the dice decide. The picture is a field the aliens mostly
respect and occasionally blunder into.

*What it is worth depends on what reached it.* Something that merely bites into one had its mouth on
the thing: the blast is far smaller than the one a shot from across the field leaves, and most of it
goes into the biter. Something with a gun a barrel can charge **swallows it whole** - no blast at all
- and carries a weapon whose shots go off where they land. So a barrel by a grunt is a gift, and a
barrel by a shooter is a problem you have to solve before the shooting starts.

**Scope.** Pickups that are dangerous to take, which today is the barrel alone. Everything edible is
unchanged: walked over, never steered around, never rolled for.

**One blast path, still.** None of this adds a second way to make an explosion. Every case is the
same `BlastsOnDeath` reaction on the same one death; what changed is a radius read *before* the
death - full, small, or none - and which of the three is a fact about the taker.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A barrel as an obstacle.* The original shape, and the reason it is a pickup at all. As an
obstacle it can never be consumed, which deletes both halves of what it is worth to the other side
and leaves it a rock that happens to explode.

**No:** *An "avoided" flag on `PickupDef` alongside the grants.* A second thing saying what the grant
already says. There is exactly one reason to give a pickup a wide berth - what it hands whoever takes
it is an explosion - and the first time the flag and the grant disagreed, the barrel would be walked
into cheerfully.

**No:** *Deciding at spawn which creatures are barrel-eaters.* Steadier to look at, and it was the
first design. Rejected because it makes the roll invisible: a unit either walks into barrels all
level or never does, so the player cannot learn anything from watching one, and the tuning dial stops
meaning "how often does this happen" and starts meaning "how many of them are like this".

**No:** *Rolling the bite once per contact rather than per second of it.* Contact lasts several ticks,
so "once per contact" is really once per tick, and a small number becomes a certainty. Every other
occasional behaviour in the game is a chance per second of opportunity, and this is no different.

**No:** *A blast that spares whoever set it off.* Would make eating a barrel strictly good for the
aliens and remove the reason a bitten barrel is smaller. A blast is on nobody's side (G0018), and the
swallow already covers the case where the creature is meant to come away with something.

**No:** *Making the power-up a second grant on the pickup.* A pickup grants one thing per side, and
"detonate and also arm the taker" would need either a compound grant or a second field nobody else
uses. What a creature gets out of an explosive is a fact about the creature, so it is named on its own
definition - `RangedDef.chargedGun` - and a strain that names none gets nothing.

## Amended 2026-09-17, after playtesting

**Shunned is about the pair, not about the thing.** As written, the record made a barrel shunned by
everything, and that quietly cancelled the third paragraph of its own constraint: a shooter that
swallows barrels could never go and get one, because avoidance steered it away and foraging skipped
it outright. Two lines, in `AvoidanceSystem` and `PerceptionSystem`, now ask whether *this* unit can
use *that* explosive - so a creature that would only set one off in its own face stays wary of it, and
one that can carry it off as ammunition walks straight at it like any other food.

That also means the shipped spitter is **no longer always aggressive**: foraging is only ever reached
by a unit that has noticed nobody, because the commander outranks food absolutely, so a strain that
hunted from the first tick could never be hungry. Enforced by
`SpitterTest.crossesTheGroundToABarrelItCanUse` and
`BarrelTest.isNotWorthCrossingTheGroundForToSomethingThatCannotUseOne`.

**Revisit when** a second shunned pickup exists, or when something on the player's side can be worth
a barrel. Both would turn "shunned" from a property of the one dangerous pickup into a real category,
and the first of them should probably bring a grant that says so outright.
