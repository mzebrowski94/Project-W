# G0030: A reinforcement is a man won where the fighting is, and nothing is banked

**Status.** amended by G0055: a reserve banks the men the field has no room for, and they arrive by muster

**Trigger.** Anything that changes how the squad grows, where a reinforcement comes from, or what a
level offers for taking ground.

**Enforced by.** `ReinforcementSystemTest.turnsARescuedTrooperIntoAManOnTheField`,
`.putsHimDownAtTheRallyPoint` (G0055), `.neverPutsMoreMenOnTheFieldThanTheSquadAllows`,
`.takesAsManyAsTheSquadHasRoomForAndNoMore`, `.fillsAGapLeftByAManTheLevelLost`,
`ReinforcementDeployTest.laysAHandfulOfReinforcementsBesideEveryControlPoint`, `.keepsItOffThePointItself`.

**Constraint.** A level lays two or three reinforcements just outside every control point it names, how
many drawn per point from the level seed. Taking one is worth three men, put onto the field a man a
tick - a queue rather than a wait. The field has a ceiling - the size the level started with plus its
configured headroom (`sim.trooper.squadGrowth`). What happens past the ceiling, and where the men appear,
are G0055's.

**Why beside a point.** Pillar 3 says the squad is the resource and that troopers are won and lost during
play. A crate beside the ground the level already asks you to take and hold is that sentence as a place:
the men you fight with are the reward for the fighting. Scattered loose they are litter; in the spawn
area they are a free top-up collected on the way out.

**Why a ceiling.** A squad that can be topped up without limit is not a resource, and losing a man stops
costing anything.

**Scope.** Not whether the squad or its ceiling carry between levels - the same open question as whether
the squad persists at all - and not an alien taking a point back. A reinforcement is a pickup like any
other: contested, edible, and lost if something hungry gets there first (G0006).

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**Reversed by G0055:** *A reserve that pays out later.* It is on the HUD, so the pillar 5 objection is
answered.

**No:** *Leaving the crate standing when the squad is full.* It is a stored man wearing a hat, and a pickup
is taken by exactly one taker on contact (G0006); a grant that sometimes declines would put a squad
count inside the collision path.

**Reversed by G0055:** *A reinforcement that arrives on a timer.* The reserve musters men on a clock.

**No:** *A drop ship, or something to defend while it lands.* A mechanic that makes the player stop and
wait is pillar 1's problem; an arrival worth defending is a wave with a different roster.

**No:** *Spawning the man where the crate was.* It reads badly for a crate fetched by a trooper or by a
commander who has moved on, and it puts a new man at the edge of the fight.

**No:** *A reinforcement granted by capturing the point rather than by taking a crate.* It removes the race
- an alien can eat a crate, which makes walking to one a decision - and a capture reward would be
invisible (pillar 5).

**Revisit when** a level has been played end to end with a squad that has been chewed on: whether the
headroom is a reason to take the far point or an afterthought.
