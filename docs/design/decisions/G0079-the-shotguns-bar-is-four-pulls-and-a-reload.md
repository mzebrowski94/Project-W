# G0079: The shotgun's bar is four pulls and a reload

**Status.** accepted

**Trigger.** Changing how many pulls a gun fires before it stops, what decides that a gun's bar is a reload
rather than a gun held down, or giving the stop of a gun that fires more than once a `resumeBelow` of zero.

**Enforced by.** `SimConfigTest.stopsTheShotgunOnTheFourthPullFiredCloseTogether`,
`.reloadsTheShotgunFromEmptyWithALightKickAndNoShake`, `.runsHotterThanAnyOtherGunAManCanCarry`.

**Constraint.** **Amends G0025 and G0058**: a reload may take more than one pull. A gun whose stop holds until
its bar is empty - `heat.resumeBelow` of zero - is reloading, however many pulls fill it; the beam's row is
asked only of guns that do not reload. It is G0025's three numbers and nothing else: no magazine, no counter,
no new rule - the shotgun's `heat.perShot` is set so the fourth pull fired as soon as it is ready stops it and
the third never does, at its own cadence and at Weapon Handling's shortest, and its cooling so that pulls a
second or more apart never fill it. The arc over the holder is the reload (pillar 5).

Its pull hits harder (36 a pellet, from 22, so a whole pull kills a grunt as it arrives on every tier), its
cadence is shorter (0.5 s, from 0.75) so four pulls *are* close together, and it names a light kick through
G0058's recoil. It names no shake.

**Why.** Mateusz asked for a heat bar worth four pulls in quick succession that must fall to zero, more
damage, and a slight kick without screen shake. G0025 rules out locking until empty for a gun held down,
because the player could not tell how close it is to coming back; G0058 already let a gun whose wait is a
reload lock until empty, with the arc as the tell. Four pulls then a wait is a reload of four, not a gun
being held down, so it goes on G0058's side of the line.

**Accepted cost.** The `perShot` band is narrow - about 0.38 to 0.41 against this cadence and cooling - so
the three numbers are retuned together; the test holds the count at both cadences. The shotgun no longer stays
cold: its glossary row changes.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A shell count or magazine.* The heat bar already shows the four pulls and the wait; a second bar
for the same rate limit is a second way to do one thing.

**No:** *A `resumeBelow` just above zero to stay inside G0025's letter.* A number that only dodges a record
shows the player nothing different, and the reload is what the record now names.

**No:** *Shake on the pull.* Asked against; the kick moves him, not the view.

**Revisit when** a second gun wants a burst of a few pulls that is not a reload - at which point G0025's
revisit (a phase machine rather than a number) is the thing to argue with.
