# G0075: A hunter goes round a shut door when there is a way round

**Status.** accepted

**Trigger.** Changing how a creature routes or picks its line to a man; adding anything else that blocks for a while
- a barricade, a gate - that a route should see; marching creatures past houses.

**Enforced by.** `ShutHouseRouteTest`, `DoorSiegeTest`, `FlowFieldTest.walksOnlyWhereTheGroundItWasGivenSaysItMay`,
`.floodsAgainForTheSameGoalOnceInvalidated`.

**Settled with Mateusz, 2026-11-11**, from the playtest list (ticket 02): grunts jammed against a house's shut door
on their way to a man in the open, although nobody was inside.

**Amends ADR 0022.** A hunter's route and its straight line are taken over the ground **as it is now** (`Solid`):
terrain and every shut door. Its route floods again whenever the set of shut doors changes.

**Constraint.**

- **Round first.** A hunter walks the route over the ground now while that route reaches any man on the player's
  side. A shut house between it and a man in the open is walked round, and a line through both of a house's doorways
  is no line while a door shuts it.
- **The siege when there is no way round.** A hunter that cannot reach any man with the doors shut takes the
  terrain's route, which runs through the shut door to the man behind it - and so walks up to the door and leaps at
  it, exactly as G0069 has it.
- **Nothing asks who is inside** (G0069 holds): the choice is whether a route reaches somebody with the doors shut,
  never whether the house holds him.
- A creature that has noticed nobody and marches to the post keeps the terrain's route; that march was not part of
  the report, and changes when it is.

**Why not "is the goal inside".** G0069 rejects a someone-inside test, and it would tie doors to houses where a
doorway in a ridge has no house. "Is there a way round" asks the field that is already built.

**Why not ADR 0022's objection.** It rejected routes that know shut doors because such a route leads a hunter away
from the man behind the door and the siege would need a second way to find him. The terrain's route is that second
way, and it is the field that existed already: nothing new finds him.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A "goal inside the house" test* - G0069's someone-inside test again, under the route's name.

**No:** *A siege when the way round is much longer* - a ratio to tune and a judgement the player cannot see. The door
breaks in the end anyway.
