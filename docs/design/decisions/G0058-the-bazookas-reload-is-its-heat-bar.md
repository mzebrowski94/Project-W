# G0058: The bazooka's reload is its heat bar, and its kick is not a blow

**Status.** amended by G0079: a reload may take more than one pull

**Trigger.** Changing how a gun that fires once and then waits shows the wait, or making a gun move the
man who fires it.

**Enforced by.** `BazookaTest.stopsItselfWithTheOneRocketItFires`,
`.throwsTheManFiringItBackFromWhereHeAimed`, `.leavesAManFiringAGunThatNamesNoKickWhereHeStood`,
`SimConfigTest.runsHotterThanAnyOtherGunAManCanCarry`.

**Constraint.** **Amends G0025**: a bar may be a reload. A gun may name `heat.perShot` of one: every pull runs
it to the stop, and the next shot waits until it has cooled to `resumeBelow`. The arc over the holder is
the reload (pillar 5). Such a gun is reloading rather than being held down, so the beam's row - it runs
out sooner than anything else he can pick up - is asked only of guns that fire more than once before they
stop.

A gun may name a **recoil**: one pull throws its holder back along his aim, through `Guns.spend` - the one
place a pull is charged - as a blow throws what it lands on, his velocity and a short hold that bleeds it
off. It is not a blow: nothing is recorded on `Damage`, so no shield breaks and nothing is provoked.
Something the solver may not move - a mount, the boss - is not moved by it.

**Why.** Mateusz asked for a long cooldown felt as an instant overheat. A cooldown alone is invisible: the
player would track a wait the game does not show (pillar 5). The heat arc is already drawn over whoever
holds a gun.

**Accepted cost.** A bazooka never reports a burst length worth comparing with the beam's, so the row
that keeps the beam's slow and bloat expensive no longer looks at it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A plain long `cooldownSeconds` and no heat.* Nothing shows when the next rocket is ready.

**No:** *Two or three rockets per bar.* To keep the beam's row honest the gun would have to fire about once
every 1.2 seconds and lock on the third - neither an instant overheat nor a burst.

**No:** *The kick as a shove recorded on `Damage`.* A blow breaks a shield and provokes (G0057); firing
a gun is neither.

**Revisit when** a gun has a magazine, or a reload of its own that is not heat.
