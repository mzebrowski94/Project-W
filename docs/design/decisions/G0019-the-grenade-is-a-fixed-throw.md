# G0019: The grenade is a fixed throw

**Status.** amended by G0077: he carries his grenades as a count

**Trigger.** Adding or changing anything thrown, or proposing that a throw land where the player is
pointing.

**Enforced by.** `GrenadeTest.throwsAlongTheAimDirectionRatherThanAtTheAimPoint`,
`.travelsItsFullRangeWhenNothingIsInTheWay`, `.spendsTheSameFlightWhetherItBouncesOrNot`,
`.bouncesOffAWallInsteadOfFlyingThroughIt`, `.passesThroughAnEnemyInsteadOfComingOffIt`,
`.comesOffAnObstacle`, `.passesThroughABodyInItsThroes`.

**Constraint.** A grenade is thrown along the aim **direction** and travels a fixed **distance**. It
comes off rock, the arena edge and scenery that never moves, keeping its speed; it passes through
anything alive, and through a body in its throes. It blasts where the distance runs out. Nothing about
the throw depends on how far away the aim point is.

**Scope.** The commander's grenade, and anything else thrown afterwards. It does not say how far, how
often or how big - those are `sim.grenade.*`. It does not say anything about ammunition: unlimited
grenades are alpha-phase scaffolding and a count would be one more number rather than a change here.

**Why a direction.** The standing rule that aim is a direction and never a distance exists so that
zoom cannot become range. A throw that landed at the cursor would break it in the most direct way
available: pull the view back with Tab and the same click reaches half the level. The fixed reach is
also what makes the throw learnable at all - the arc is the same every time, so the skill is choosing
the bearing rather than judging a distance the interface is not showing you.

**Why a distance rather than a timer.** They are the same number while speed is constant, and the
distance is the one that says what the player can rely on: the throw covers the same ground however
many walls it comes off, so banking one round a corner costs reach only where the corner is.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Throwing to the aim point.* The obvious implementation, and the one every player who has met
a twin-stick game will expect. It makes a rendering setting into extra range, which the aim invariant
was written to forbid, and it deletes the wall bank - a throw that lands where you clicked has no
reason to bounce.

**No:** *Holding the key to throw further, or cooking one before release.* Both are real mechanics and
both are the same objection as a damage curve: the player is now timing a number the game is not
showing them, and pillar 1 says a mechanic that makes you stop and compute belongs in a different
game. A charge bar would answer that and is a HUD the alpha does not have.

**No:** *Detonating on contact with an enemy.* It sounds better than bouncing off one and it is worse:
the grenade becomes a slow projectile, the bank disappears, and the interesting throw - past the thing
in front of you at the thing behind it - stops existing.

**No:** *A grenade that is solid, so the solver keeps it out of rock and inside the arena for free.* It
would shove units around as it flew, which turns a thrown object into a battering ram and moves a
fight the player did not mean to move. Non-solid costs one method that comes off rock and the arena
edge, which is a cheaper price than a weapon that pushes people.

**No:** *Coming off units as well as scenery.* This is what was built first - "bounces off obstacles
and enemies" was the original ask - and one playtest removed it. In the open it is fine; in a crowd it
is noise. With a dozen bodies between the commander and where he was aiming, where a throw ends up
stops being anything a player can predict, and the wall bank that makes the throw worth having is lost
in it. A corpse is excluded for that reason and one more: a body is scenery for a second and then is
not, so it is a surface that appears and vanishes unannounced. Nothing was added to express any of
this - a unit is solid *and movable*, so the test for scenery excludes it on its own.

**No:** *A `WeaponKind` for the grenade.* Guns are found on the field and lost with the body carrying
them; the grenade is something he can always do. Making it a gun would mean picking one up replaced
it, which is the opposite of what it is for.

**Revisit when** a second throwable exists - a smoke, a charge that sticks - because "it goes off
where the distance runs out" is the part of this that a sticky charge cannot honour, and that is a
different `Thrown` rather than a change to this one.
