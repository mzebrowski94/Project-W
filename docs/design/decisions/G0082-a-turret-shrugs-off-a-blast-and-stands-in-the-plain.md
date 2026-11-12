# G0082: A turret shrugs off a blast, and the plain lays turrets

**Status.** accepted

**Trigger.** Changing what a blast takes from something with health, or which biome lays a turret and where.

**Enforced by.** `TurretTest` (`.shrugsOffMostOfABlast`, `.goesUpOnTheBlastThatTakesTheLastOfIt`,
`.leavesTheCrewToTakeABlastTheGunSurvives`), `MissionTest.putsBetweenItsFewestAndMostTurretsInEveryBiomeEachByAPoint`,
`BiomePromisesTest.thePlainIsTheOneBiomeThatLaysTurrets`, `MissionFaults.crates` (every turret of a biome by a
point, in `MissionSweepTest`).

**Constraint.** **Amends G0018 and G0068, and the `new-biome` rule that a turret in a biome is a finale's.**
Provisional, for review: built from playtest feedback with Mateusz away, without a grilling.

1. **Blast resistance.** `Health.blastResistance` is the per cent of a blast's damage a thing shrugs off: zero,
   which is off, for everything but what its kind names. A turret names `sim.turret.blastResistance` (90). The
   one reader is `Health.fromBlast`, on the one path every blast's damage takes - `BlastSystem`, before
   `Damage.land` - rounded once by `Whole` (G0054). At 90 a blast takes 100 of a turret's 360: it is scarred by
   one and taken by the fourth. Gunfire, fire, claws and a leap land in full.
2. **The crew still takes the blast.** The man on a turret is a man, with no health to resist with; a blast
   reaches him as G0024 says, and puts him down whether or not the gun survives it.
3. **The plain lays turrets.** `furniture.turretsMin/Max` is a line on every biome's block, zero in every biome
   but the plain (1-2). Placement puts each within `pointReach` of a point, a point at a time from a drawn one,
   off every nest, right after the crates by points (G0068). The count is drawn last from the biome's furniture
   stream, so every biome without turrets draws as it did.

**Why on `Health` rather than on the turret.** Every damageable kind has health and nothing else in common: a
turret, mine, nest, barrel and door each have a config record of their own and a strain has its `UnitDef`
(*Turret, mine and nest are one of a kind each*, `docs/backlog.md`). The number lives where the one reader can
find it on any victim, so a second kind that resists a blast is a key and a `.resisting(...)` at its spawn, not
a branch.

**Why not G0045's armour against blasts.** That is a side - the ant's head turns gunfire by the way it faces,
and a blast has no side. This is how much of the blow lands, whatever direction it comes from.

**Why one or two, by a point.** Open ground is where a gun's 55-unit range pays, and the plain is the
openest biome. A point is where the level tells you to stand, which is what a turret is (G0024). With two or
three points, at most two guns leaves a point held on foot, and the redoubt keeps its four as the place built
round guns.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A `blastResistance` on the turret's own component, or a branch on `Turret` in `BlastSystem`.* One
number on what every victim has.

**No:** *Resistance by the side the blast arrives from.* That is armour (G0045), and a blast has none.

**No:** *Sparing the crew with the gun.* He is a man on the field, and a blast is a radius test (G0024).

**No:** *Turrets anywhere in the biome.* A gun out of the fight is a find nobody makes.

**Revisit when** a second kind asks to resist a blast or something other than a blast: the per cent may want to
become a `Harm`-style table on the definition, read by the same one place. And when the plain has been played
with its guns: whether two is a plain held from a mount.
