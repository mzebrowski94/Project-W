# G0130: A blast takes the damage its source declares

**Status.** accepted

**Trigger.** What a blast takes off what it catches; an upgrade or a gun that moves a blast's damage; an explosive
meant to hit softer than the ordinary blast.

**Enforced by.** `BazookaTest.landsItsBlastAsMuchHarderAsItsGunUnderFirepower`,
`DetonatorTest.landsBothItsBlastsAsMuchHarderAsItsGunUnderFirepower`; the ordinary blast's own rows in G0018.

**Provisional, for review (AFK run, 2026-11-24).** Built for G0127's *a blast rises with its gun*: until now every
blast took `sim.blast.damage` whatever made it, so there was nothing for Firepower's `gun_damage` to reach.

**Amends G0018.**

**Constraint.**

- **The damage is the source's**, carried on its `BlastsOnDeath` as its harm is (G0053) and handed to the `Blast` it
  leaves. Every carrier starts at `sim.blast.damage`; one place in `Spawner` gives it.
- **A gun's blast is part of what the gun does.** `Guns.volley` - the one place `gun_damage` is read - folds the gun's
  damage scale into the blast a rocket or a limpet leaves, for the same pull, so an upgrade that moves a gun's damage
  moves its blast's with it.
- **Still one circle**, uniform inside it, on nobody's side, through the one death reaction. What a man takes is still
  the blast kind's harm; only what has health reads the damage.

**Why on the source and not on the blast config.** `sim.blast.*` is what is true of every explosion (G0018); the
number a gun's upgrade moves belongs to whoever fired, as the harm already does.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A second read of `gun_damage` in `BlastSystem`.* The blast does not know whose gun it was; the pull does.

**No:** *A blast damage that falls off with distance.* Still G0018's refusal: one circle, one number inside it.

**Revisit when** a source wants a blast that a man feels differently by its damage - today a man reads only the harm.
