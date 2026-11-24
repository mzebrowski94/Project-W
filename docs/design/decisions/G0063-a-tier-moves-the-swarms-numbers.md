# G0063: A tier moves the swarm's numbers, never what a blow does to a man

**Status.** amended by G0102: the ramp spans the march, whatever its length; amended by G0106: a host's strength rides on the tier's; amended by G0118: the adaptation pace is a count and a share of the pool on the drafts' schedule; amended by G0119: the players seated fold in beside it

**Trigger.** Adding or changing what a mission's tier scales; proposing a tier number for damage, for the
squad, the reserve, crates or medkits; changing `sim.melee.damage`, a strain's health or
`sim.swarm.healthPercent` in a way that could break a relational guarantee at some tier.

**Enforced by.** `SimConfigTest`'s relational guarantees, each run off a mission and at every tier through
`MissionConfig.apply`, the fold the game uses; `WholeTest.multipliesBothPercentsBeforeRoundingOnce`;
`ArchitectureTest.onlyMissionConfigReadsATier`; `MissionConfigTest` for the fold and the pressure. Missions
ticket 07 holds the numbers.

**Constraint.**

- **A tier is four numbers on the swarm**: how many (`enemyPercent`, every roster count), how tough
  (`healthPercent`, on top of `sim.swarm.healthPercent`), how often a nest breeds (`nestPeriodPercent`)
  and how fast it adapts (`adaptation.everySeconds`, `adaptation.perPlayerUpgrades`). Plus one **ramp**, the same for
  every tier: a biome's counts rise with its position in the march. Never which strains (missions ticket 01).
- **One reader, one rounding.** `MissionConfig` reads a tier and folds it into the `SimConfig` a world is
  built from, so no system learns what a tier is. A percentage meeting a whole number - hit points or a
  count - rounds once, in `Whole`, both percents multiplied before it (G0054).
- **What a player learns holds at every tier.** Every relational guarantee in `SimConfigTest` - a swing
  deletes a grunt and not a brute, among them - holds on every tier's numbers. A tier that would break one
  moves the base number instead (`sim.melee.damage` goes to 220 with the tiers, for hard's 115 %).

**Why no damage.** A man reads a blow's harm and never its amount (G0053, G0057). An alien's amount reaches
only what has health on the player's side - a turret, a rabbit - and turrets stand only in a finale. A tier
damage key would move nothing a man feels and read as if it did (pillar 5).

**Why the guarantees hold.** Tier changes how much of the swarm there is, not what the player has learned
about it. "A fist deletes a grunt" is a lesson; on hard it would quietly become a lie, and the player would
learn it by dying.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A tier damage percent.* It moves turrets and rabbits only. Hurting a man harder is a count on a
harm (G0053's revisit), and that is a decision about men, not about tiers.

**No:** *Attack pace as the tier's damage* - claw recovery and spit cooldown scaled per tier. A second fold
on numbers adaptations already move, so a strain's cooldown would have two owners.

**No:** *Health that only goes down* - hard at 100 %. Leaves hard harder by count alone, and a crowd is not
the same thing as a tougher one.

**No:** *Guarantees held at medium only.* See *Why the guarantees hold*.

**No:** *Rounding each percent in turn*, or multiplying the percents into a rounded percent. Two roundings
are two rules (G0054).

**No:** *A tier number on the squad, the reserve, crates or medkits* (missions ticket 01) - the player's side
is the same on every tier; nor on `wakeRadius` or `firstBroodSeconds` (G0062).

**Revisit when** a man can be hurt more than one step at once - then a tier might scale that count - or when
the adaptation pool is big enough that its pace is the tier's strongest lever rather than its weakest.
