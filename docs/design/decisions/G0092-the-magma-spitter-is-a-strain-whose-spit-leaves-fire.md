# G0092: The magma spitter is a strain whose spit leaves fire

**Status.** accepted

**Trigger.** Adding an "upgraded" version of an existing strain; proposing that the magma spitter be an adaptation,
an evolution or a spitter with a flag; giving a creature's shot fire by any way but a blast; giving it a biome
other than the caldera.

**Enforced by.** `SimConfigTest.makesTheMagmaSpitterASpitterWhoseSpitLeavesFire`;
`BiomePromisesTest.theCalderaIsTheOneBiomeWithMagmaSpitters`; `FireTest.burnsTheGroundWhereAnExplosionWent` (a blast
leaves fire, whatever fired it); `SpitterTest.showsThatItIsCarryingABarrel` (a gun that goes off where it lands is
drawn as one).

**Provisional, for review** - settled in an AFK run (playtest ticket 16), not with Mateusz.

**Constraint.** `UnitKind.MAGMA_SPITTER`, a `sim.unit.magma_spitter` block that is the spitter's key for key - body,
senses, amble, cone, position, barrel appetite - but for three: `health` 260 against 200, `ranged.gun=magma_spit`,
and `look=magma_spitter`. Its gun, `WeaponKind.MAGMA_SPIT`, is a spit that names a `blastRadius` (4, under a charged
spit's 5), `blastHarm=wound`, firing every 1.3 s against a spit's 1.0. **A gun with a blast leaves fire** (G0035): so
every shot that lands or misses leaves a patch of neutral burning ground, and the creature wears the ring of a gun that
goes off where it lands (pillar 5) - no new code path, no `Fire` component, no fire with a side. A barrel still charges
it (`chargedGun=charged_spit`). It stands in the caldera's roster alone (G0091), its candidate signature (ticket 19,
open).

**Why a strain and not an adaptation.** An adaptation is the swarm's, dealt over the whole mission on a schedule and
moving a number on every creature of a strain (D11); "spitters in the lava biome are upgraded" is a fact about one
biome, from its first tick. A strain is a constant, a definition and a block (G0008), and a biome's roster names it.

**Why tougher and slower rather than the same numbers.** The fire is what it adds. Kept at a spitter's numbers it is
strictly more; a little tougher and a little slower to fire, it is a different trade - fewer shots that each leave
ground burning - and the spitter's answer (get to it) still works.

**Scope.** The magma spitter. Not a creature shot that leaves fire without a blast, not sided fire.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *The spitter with an adaptation that gives its spit a blast.* An adaptation is mission-wide and scheduled; this
is a biome's from the start, and an adaptation moving a gun's blast would be a `Stat` with one caller for one strain.

**No:** *A flag on the spitter, or a branch on the biome it stands in.* G0008: a new kind is data.

**No:** *A spit that lays fire itself, or a fire of the swarm's side.* G0035: fire comes from a blast dying and is on
nobody's side - a magma spitter's own fire catches the creatures beside it as readily as the squad.

**Revisit when** ticket 19 settles a signature strain per biome, or a creature wants to leave fire without a blast.
