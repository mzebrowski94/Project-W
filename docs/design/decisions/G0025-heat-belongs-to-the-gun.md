# G0025: Heat belongs to the gun, and coming back costs more than stopping

**Status.** amended by G0047: a gun from a crate arrives cold; amended by G0058: a bar one pull fills is a reload

**Trigger.** Adding a weapon that limits its own rate of fire, or proposing that heat live on the man,
reset when he lets go, or share the channel everything else that fills up is drawn on.

**Enforced by.** `OverheatTest.goesOnCoolingWhileNobodyIsOnIt`, `.keepsWhatItHadWhenSomebodyGetsBackOn`,
`.staysLockedOutUntilItHasCooledWellBelowFull`, `.leavesAGunThatNamesNoHeatCold`.

**Constraint.** A gun names three numbers: what a shot adds, what comes off per second, and how far
the bar must fall before a gun that stopped itself will fire again. All three zero - every gun in the
game bar two - and it never runs hot. The bar lives on whatever is *holding* the gun, which for a
turret is the turret, and it cools at the same rate whoever is holding it and whether or not anybody
is.

**Scope.** Anything that fires. The heat gate is asked in the one place every shooter asks whether it
may fire at all (`Guns.ready`), so an alien gun that named heat would obey it without a line of code.
It says nothing about ammunition, which does not exist: heat is a rate limit, not a supply.

**The hysteresis is the mechanic.** `resumeBelow` is well under one on purpose. A gun that came back
the moment it stopped being full would be a gun you tap through the stop, which is no cost at all.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Heat on the man, so it follows him from gun to gun.* The obvious place for it, and it breaks
the turret outright: stepping off a hot gun and back on would hand the player a cold one, which is the
exact exploit the mechanic exists to price. It also makes "what does a gun cost to fire" a fact about
whoever picked it up.

**No:** *Resetting the bar when a crew leaves, or when a gun is picked up.* Same hole, stated as an
event instead of as a place. A gun that cools only while somebody is on it is a gun you cool by
getting off it.

**No:** *Drawing it on the `charge` channel with a look of its own.* Cheaper - the channel and the
colour machinery both exist. Rejected because everything on that channel is something happening **to**
the thing it is drawn on, and this is the one bar that fills up because of what the player **did**;
and because a manned gun may need to show both at once, which one channel cannot.

**No:** *Locking until the bar is completely empty.* Reads as a punishment with no shape to it - the
player cannot tell how close the gun is to coming back, and the number that decides it stops being
tunable against the rate of fire.

**No:** *A cooldown multiplier that rises with heat instead of a stop.* Considered: it is smoother and
it never takes the gun away. Rejected because pillar 5 wants a state the player can read, and "firing
slightly slower than a moment ago" is not one. The stop is the tell.

**Revisit when** a gun wants a third state between firing and stopped - a wind-down, a shorter burst
limit - at which point the bar is a phase machine rather than a number, and this record is the thing
it has to argue with.
