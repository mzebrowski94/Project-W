# G0055: The army is a field and a reserve, and every man after the first tick comes from the reserve

**Trigger.** Anything that changes how many men a side has, where a man comes onto the field, what a
commander's return costs, what a crate of men is worth, or how a level is lost.

**Enforced by.** `ReserveTest` (the reserve, the muster, the rally point, a commander paid for from the
reserve, the lost level), `ReinforcementSystemTest` (a crate: the field first, then the reserve, then
waste), `RespawnSystemTest.bringsItBackInTheSpawnAreaWhileNoPointIsTaken`,
`CoopTest.bringsHimBackAtTheRallyPointEvenWithAFriendStillOnHisFeet`,
`ControlPointTest.numbersThePointsInTheOrderTheyAreTaken`, `GoalLineTest.saysFailedOverEverythingOnceTheLevelIsLost`.

**Supersedes in part G0030**: its *nothing is banked* and two of its DO NOT BUILD entries - *a reserve
that pays out later* and *a reinforcement that arrives on a timer* - are overruled by Mateusz,
2026-09-29. What G0030 decided about where crates lie (beside the points) and that a crate is worth
several men stands. **Changes the respawn place** the invariants recorded (beside a commander still on
his feet): a commander now comes back at the rally point, even in co-op.

**Constraint.** An army is four numbers, each a scenario may set and the first two of which upgrades
may raise:

| | What | Key | Default |
|---|---|---|---|
| A | men on the field when the level starts | `sim.trooper.squadSize` | 10 |
| B | the most the field holds - A plus headroom plus upgrades | `sim.trooper.squadGrowth` (headroom) | 6, so 16 |
| C | men in reserve when the level starts | `sim.reserve.start` | 20 |
| D | the most the reserve holds | `sim.reserve.max` | 100 |

- **Every man who comes onto the field after the first tick is a man from the reserve**, whatever he
  is: a trooper called up by a muster, or a commander coming back. A commander's return takes one man
  from the reserve the tick after he is gone - **before** the muster, so a player is heard before a
  trooper - and his countdown runs only once it is paid.
- **A muster** every `sim.reserve.musterSeconds` (30) calls 2-3 men (dealt from a bag) out of the
  reserve onto the field, from the first tick, as many as the field has room for. A full field leaves
  them in reserve; nothing is spent.
- **A crate of men** goes onto the field one a tick while it has room, then into the reserve, and past
  the reserve's most it is wasted.
- **The rally point** is where all of them appear: the spawn area until a point is taken, the point
  taken last after that (`RallyPoint`).
- **The level is lost** when every commander is out, somebody is waiting to come back, and the reserve
  is empty. Latched - a crate a trooper reaches afterwards does not bring it back. The goal line says
  MISSION FAILED; like MISSION COMPLETED, there is no end screen yet.
- **Both counts are on the HUD** - `ARMY 12/16 RES 20`, centred on the top bar's first line - and a
  commander waiting on an empty reserve says `KIA NO RESERVE`. The muster's countdown is not shown
  (Mateusz, 2026-09-29: the aside's room is for upgrades); it is a steady clock, not a number to track.

**Why a reserve now, when G0030 refused one.** G0030's objection was pillar 5: a balance the player
tracks and the game does not show. The reserve is shown, beside the men on the field, and the next
muster with it - so the objection is answered rather than overruled. Pillar 3 holds: the squad is still
the resource, now with a visible pool behind it that runs out; losing men still costs, because every
man lost is one fewer in the pool that brings commanders back.

**Why the rally point, and not beside a commander.** One answer for every man from the reserve, and the
answer is ground the side holds. In co-op it means a dead player walks back to the fight from the last
point taken - Mateusz's call (3.a), accepted with that cost in view.

**Scope.** Says nothing about the reserve carrying between levels (the same open question as the squad
persisting at all), nor about upgrades to the reserve's own numbers - none exist yet; the muster and
the reserve's most are the natural stats if one is wanted. A scenario built round the commander alone
switches the muster off (`Scenario.withoutMuster`) and keeps the reserve, so a return still costs.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A hidden reserve, or one shown only in a menu.* The whole case for having one is that it is
on screen. A reserve off the HUD is G0030's objection back again.

**No:** *Commanders who come back free.* Offered and declined: a return that costs nothing is a player
who cannot lose, and a reserve that only troopers draw on is a number the players have no reason to watch.

**No:** *A crate that always banks, or always lands.* The field first is the arcade answer - the men you
won are in the fight - and the reserve behind it is what stops a full squad wasting the crate.

**No:** *A muster that spends men into a full field, or that waits for someone to be standing.* A
muster onto a full field would be waste nobody chose; the rally point is ground, not a man, so nothing
waits on anybody.

**Revisit when** a level has been played end to end with a squad that has been chewed on - whether 30
seconds is a trickle or a flood, whether 20 in reserve is tension or a formality, and whether respawning
at the last point reads as fair in co-op.
