# G0047: A gun from a crate arrives cold

**Status.** accepted

**Trigger.** Changing what happens to heat when a man's gun changes.

**Enforced by.** `OverheatTest.handsAManWhoseLastGunStoppedItselfAColdOneFromACrate`, plus G0025's
tests, which still hold.

**Constraint.** **Amends G0025** for a gun from a crate. `Spawner.applyWeapon` - the single writer for a carried gun - resets the holder's
`Heat` along with the gun. The bar is still the gun's (G0025); the gun that ran hot is the one he let go
of, and a crate hands him one nobody has fired. A mount never goes through `applyWeapon`, so stepping
off a hot turret still cools nothing.

**Why.** In play, a man who took a new gun while his old one had stopped itself was still
stopped, which read as the new gun being broken. The bar lives on the holder only because a turret
is its own holder; for a man it was heat following him from gun to gun - G0025's own first **No**.

**Accepted cost.** A second crate of the *same* gun, taken while that gun is hot, hands back a cold
one. A crate is a walk and there are few of them; it is a gun, not a cooldown.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Heat stored per gun kind on the man, so a gun he swaps back to comes back hot.* There is no
swapping back - a crate replaces, and the old gun is gone.

**Revisit when** a man can carry two guns and switch between them. Then heat is per gun in hand, and
this record and G0025 are both what that design has to answer.
