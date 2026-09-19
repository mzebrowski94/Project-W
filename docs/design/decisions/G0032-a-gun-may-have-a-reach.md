# G0032: A gun may have a reach; an aim may not

**Trigger.** Adding a weapon, or anything that wants a distance in the firing path.

**Enforced by.** `VolleyTest.sendsAsManyShotsAsTheGunNamesFromOnePull`, `.sendsOneFromAGunThatNamesOne`,
`.throwsEveryPelletStraightForAGunWithNoConeOfItsOwn`, `.scattersThemInsideTheConeForAGunThatNamesOne`,
`.sendsEveryPelletOutAtTheSameSpeed`, `.dropsAShotOnceItHasFlownAsFarAsItsGunAllows`,
`.leavesAShotFromAGunWithNoReachFlying`, `.carriesItTheWholeOfTheReachBeforeItGoesOut`; and
`CommanderControlSystemTest.firesInTheSameDirectionHoweverFarAwayTheAimPointIs`, which still holds
the other half.

**Constraint.** A `WeaponDef` names three things the firing path honours: how many **pellets** one
pull sends, the cone the gun throws them into, and how far one of them flies before it goes out.
All three default to "one round, dead straight, until it hits something", which is what every gun
that existed before this declared. One place - `Guns.volley` - turns a pull into shots, and all four
things that fire go through it.

**Why a reach on the gun is not a reach on the aim.** The enforced invariant is that *aim* is a
direction and never a distance, and the reason is the camera: a player who pulls the view back with
Tab must not thereby be able to shoot further. A **flight length on the shot** cannot do that. It is
measured from the muzzle, it is the same number wherever the aim point is, and a grenade has carried
one since G0019 without anybody thinking the rule was in danger. The distinction is worth a record
because the two read alike in a changelog and only one of them is safe.

**Why the gun's cone is not the shooter's.** A spitter already has a `spreadDegrees`: it is how badly
the creature aims, it narrows as the thing settles, and losing its target throws it away. A
scattergun's cone is a fact about the weapon and nothing improves it. They compose - the shooter
turns the aim once for the whole volley, because a bad guess is one guess, and the gun scatters each
pellet around the result. `RangedDef`'s own javadoc predicted this move the day a scattergun existed;
this is that day, and the creature's half stayed where it was.

**Why one place puts the shots in the air.** Four things fire: the commander, a trooper, a shooting
alien and a gun on a mount. Before this each worked out its own muzzle point and spawned its own
single projectile - and the trooper did not even use `Muzzle`, so a twin mount in its hands would
have fired from the middle. A gun whose behaviour depends on who is holding it is not a gun, it is
three guns with one name.

**Scope.** It says nothing about a gun that reloads, a gun with an ammunition count, or a second
weapon slot - all of those are still what pillar 3 and the design's weapon paragraph say they are.
It says nothing about damage falloff, which does not exist and is not implied by a reach: a pellet
that lands does its full damage, and one that does not land does nothing.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A maximum range on the aim, or a clamp on the aim point.* This is the rule the camera would
immediately break, and it is enforced.

**No:** *A range on the unit, as `RangedDef.range` is.* That number is how close a *creature* chooses
to get before it opens up - a fact about the creature. Handing the commander one would cap him with
a number he cannot see and did not choose.

**No:** *Damage falloff over the flight.* A curve the player cannot see, to express a thing the
spread already expresses: at distance the cone is wider than the target, so fewer pellets land. That
is falloff the player can read off the screen (pillar 5).

**No:** *A hitscan short-range gun, or a cone-shaped overlap query for the flame.* No hitscan, for
the three reasons the no-hitscan rows are load-bearing for. A flamethrower is pellets like everything
else; that is why it needed no new firing path.

**No:** *A `pellets` count on the shooter rather than on the gun.* It would make a shotgun a different
gun on a mount than in his hands, which is the defect this record's last section is about.

**No:** *Expiring a shot by a lifetime in seconds rather than by a distance.* Same reach at the same
speed today, and a number that silently changes meaning the first time a gun's projectile speed is
retuned. `Projectile` already carries its origin for G0016's sake, so a distance costs nothing.

**Revisit when** a third scattergun is wanted, or when a gun wants a reach *and* a blast - the second
is already possible (a shot that expires dies, and a death is what leaves a blast, G0018) and has
never been tried.
