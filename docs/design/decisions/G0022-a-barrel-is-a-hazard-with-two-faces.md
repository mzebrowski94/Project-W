# G0022: A barrel is a hazard the aliens respect, and worth two different things to them

**Status.** accepted

**Trigger.** Changing how a barrel behaves, adding another pickup that is dangerous to take, or
proposing that avoidance treat some pickups differently from others.

**Enforced by.** `BarrelTest.isLeftStandingByAnAlienWithNoAppetiteForOne`,
`.leavesASmallerCircleWhenItIsBittenIntoThanWhenItIsShot`, `.catchesTheSameBystanderWhenItIsShotInstead`,
`.isNotWorthCrossingTheGroundForToSomethingThatCannotUseOne`,
`AvoidanceSystemTest.leansOffABarrelAsReadilyAsOffARock`, `.walksStraightOverItsOwnDinner`,
`SpitterTest.swallowsABarrelAndFiresShotsThatGoOff`, `.crossesTheGroundToABarrelItCanUse`.

**Constraint.** A barrel is still a pickup and still explodes by dying (G0018), and three things about
who takes it and what they get are true.

*It is shunned by whatever cannot use it.* Avoidance steers such a unit around it as around a rock, and
foraging skips it. Whether it is shunned is a question about the pair - can *this* unit use *that*
explosive - asked in `AvoidanceSystem` and `PerceptionSystem`: a creature that would only set one off
in its own face stays wary, and one that can carry it off as ammunition walks straight at it like food.
This narrows the invariant "nothing steers around a pickup", which exists so a hungry alien does not
circle its own dinner; a barrel is not dinner.

*Touching one is an opportunity, not an outcome.* Taking a shunned pickup is rolled on
`AiChance.BITE_A_BARREL` while the two are in contact. Avoidance is an opinion about a course rather than
a wall, so a creature shoved into one in a crowd still ends up against it - and then the dice decide.
The aliens mostly respect the field and occasionally blunder into it.

*What it is worth depends on what reached it.* Something that bites into one gets a far smaller blast
than a shot from across the field leaves, and most of it goes into the biter. Something with a gun a
barrel can charge **swallows it whole** - no blast - and carries a weapon whose shots go off where they
land. A barrel by a grunt is a gift; a barrel by a shooter is a problem to solve before the shooting
starts. Foraging is reached only by a unit that has noticed nobody, so a strain that swallows barrels
cannot also hunt from the first tick.

**One blast path, still.** Every case is the same `BlastsOnDeath` reaction on the same one death; what
differs is a radius read *before* the death - full, small, or none - which is a fact about the taker.

**Scope.** Pickups that are dangerous to take, which today is the barrel alone. Everything edible is
walked over, never steered around, never rolled for.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A barrel as an obstacle.* It could never be consumed, which deletes both halves of what it is
worth to the other side and leaves a rock that happens to explode.

**No:** *An "avoided" flag on `PickupDef` alongside the grants.* A second thing saying what the grant
already says; the first time the two disagreed, the barrel would be walked into cheerfully.

**No:** *Deciding at spawn which creatures are barrel-eaters.* It makes the roll invisible: a unit walks
into barrels all level or never, so the player learns nothing from watching one.

**No:** *Rolling the bite once per contact rather than per second of it.* Contact lasts several ticks, so
"once per contact" is once per tick, and a small number becomes a certainty.

**No:** *A blast that spares whoever set it off.* Eating a barrel would be strictly good for the aliens.
A blast is on nobody's side (G0018), and the swallow covers the creature coming away with something.

**No:** *Making the power-up a second grant on the pickup.* What a creature gets out of an explosive is a
fact about the creature, named on its own definition - `RangedDef.chargedGun` - and a strain that names
none gets nothing.

**Revisit when** a second shunned pickup exists, or something on the player's side can be worth a barrel:
either turns "shunned" into a real category, which should bring a grant that says so outright.
