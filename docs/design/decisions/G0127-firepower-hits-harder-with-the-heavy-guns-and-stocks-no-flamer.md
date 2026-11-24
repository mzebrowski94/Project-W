# G0127: Firepower hits harder with the heavy guns and stocks no flamer

**Status.** accepted

**Trigger.** What Firepower does; what a crate is dealt under it; how an upgrade reaches more than one gun.

**Enforced by.** `UpgradeTest.hitsHarderWithTheHeavyGunsAndWithNoOtherGunUnderFirepower`,
`.dealsEveryCrateUnderFirepowerAsWithoutIt`; `BazookaTest.landsItsBlastAsMuchHarderAsItsGunUnderFirepower`,
`DetonatorTest.landsBothItsBlastsAsMuchHarderAsItsGunUnderFirepower` (the blast through G0130).

**Settled with Mateusz, 2026-11-24** (grilling): *remove the bonus flamer from crates; slightly increase the damage of the
explosive and fire guns.* Not yet built, not yet played.

**Amends G0056 and G0110.** Their Firepower stock - a flamer in every crate opened after the pick - is gone, and with it
G0110's *Firepower's flamer is swapped in only where the deal held none*.

**Constraint.**

- **No stock.** A crate opened under Firepower is dealt as any other: the flamer at the armoury deck's baseline, the
  same as every gun.
- **+15 % damage a rank** for the **flamer, bazooka and detonator**, for whoever holds one (`team`), two ranks: three
  `gun <kind> gun_damage team 15` effects, through `Modifiers` as every gun stat is.
- **A blast rises with its gun.** The rocket's blast and both of the limpet's are part of what the gun does: the one
  place that reads `gun_damage` for them reaches their blast damage too.

**Why three gun effects, not a by-type effect.** `WeaponType.HEAVY` names exactly these three, but nothing else asks for
a by-type effect; a new effect form is a layer, three effects are a line.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A flamer stocked at a higher rate rather than every crate.* The point was the flamer at baseline.

**No:** *A `type heavy` effect form* until a second upgrade or adaptation asks for one.

**Revisit when** a fourth heavy gun is added, or a second card wants a whole weapon type.
