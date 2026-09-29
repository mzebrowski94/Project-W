# G0030: A reinforcement is a man won where the fighting is, and nothing is banked

**Trigger.** Anything that changes how the squad grows, where a reinforcement comes from, or what a
level offers for taking ground.

**Enforced by.** `ReinforcementSystemTest.turnsARescuedTrooperIntoAManOnTheField`,
`.putsHimDownAtTheRallyPoint` (G0055), `.neverPutsMoreMenOnTheFieldThanTheSquadAllows`,
`.takesAsManyAsTheSquadHasRoomForAndNoMore`, `.fillsAGapLeftByAManTheLevelLost`,
`ReinforcementDeployTest.laysOneReinforcementBesideEveryControlPoint`, `.keepsItOffThePointItself`.

**Superseded in part by G0055 (2026-09-29).** Mateusz overruled *nothing is banked*, and the DO NOT
BUILD entries for a reserve and for a timed arrival: a crate's men go onto the field while it has room,
then into a reserve shown on the HUD, and the reserve musters men onto the field on a clock. They now
arrive at the rally point, not beside the commander. Where crates lie, and what one is worth, stand.

**Amended 2026-09-18**, the same day it was written and before anything was built on it: a point is
worth two or three crates rather than one, a crate is worth three men rather than one, and the men
arrive a tick apart. None of that changes what the record decided - where men come from, and that
nothing is banked - so it is an amendment rather than a record superseding this one.

**Constraint.** A level lays two or three reinforcements just outside every control point it names,
how many drawn per point from the level seed. Taking one is worth three men, put down beside the
commander **at once** - a man a tick, which is a twentieth of a second for the lot and is a queue
rather than a wait. The squad has a ceiling - the size the
level started with plus its configured headroom (`sim.trooper.squadGrowth`) - and a reinforcement
taken past that ceiling is **wasted**, not stored. The one thing that survives a tick is a
reinforcement nobody can form up on, because the commander is down: that is held until he is back up.

**Why beside a point.** Pillar 3 says the squad is the resource and that troopers are won and lost
during play. A crate lying beside the ground the level already asks you to walk into and hold is that
sentence as a place on the map: the men you fight with are the reward for the fighting. Scattered
loose they are litter, and in the spawn area they are a free top-up you collect on the way out.

**Why the ceiling, and why waste is the right answer.** A squad that can be topped up without limit
is not a resource, and losing a man stops costing anything. Waste is the arcade answer: the same
crate is worth everything after a bad fight and nothing after a clean one, and the player can see
which he is in by counting the men he can see (pillar 5).

**Scope.** It says nothing about whether the squad or its ceiling carry between levels - that is the
same open question as whether the squad persists at all - and nothing about an alien taking a point
back. A reinforcement is still a pickup like any other: contested, edible, and lost if something
hungry gets there first (G0006).

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A reserve that pays out when a man dies.* The obvious fix for waste, and it is a number the
player would be tracking that the game is not showing him - pillar 5, and an economy between fights
that pillar 3 rules out. Waste is legible; a balance is not.

**No:** *Leaving the crate standing when the squad is full.* It sounds kinder and it is the same
economy wearing a hat: the crate becomes a stored man, sitting where you left it until you need it.
It also costs the claim its simplicity - a pickup is taken by exactly one taker on contact (G0006),
and a grant that sometimes declines would put a squad count inside the collision path.

**No:** *A reinforcement that arrives on a timer, a drop ship, or something to defend while it lands.*
A mechanic that makes the player stop and wait is pillar 1's problem. If a level ever wants an arrival
worth defending, that is a wave with a different roster, not a change to what a crate does. The men a
crate is worth are put down a tick apart, which is a property of the work rather than of the game -
nobody can see a twentieth of a second, and nothing in the simulation may wait on it.

**No:** *Spawning the man where the crate was.* It reads well for one crate and badly for the case
that matters - a crate fetched by a trooper, or by a commander who has already moved on - and it puts
a new man at the edge of the fight rather than in it. Beside the commander is the answer
`RespawnSystem` already gives for where a man belongs, and one answer is enough.

**No:** *A reinforcement granted by capturing the point rather than by taking a crate.* It removes the
race: an alien can eat a crate, and that is what makes walking to one a decision rather than a
formality. A capture reward would also be invisible - nothing on the field to see, and pillar 5 again.

**Revisit when** a level has been played end to end with a squad that has been chewed on. The two
things to watch: whether the headroom is a reason to take the far point or an afterthought, and
whether a wasted crate reads as a decision the player got wrong or as the game refusing him.
