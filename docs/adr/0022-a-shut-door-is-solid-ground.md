# 0022: A shut door is solid ground, not a body

**Status.** accepted

**Trigger.** Building or changing doors; adding anything else that blocks for a while and then does not; adding a
reader of "is this tile solid" in `sim`.

**Enforced by.** Nothing yet - doors are built in missions stage 7b. When they are: a door test that a crowd driven
into a shut door ends with nobody past it, and that every reader named below stops at one.

**Amends G0009.** A door is an entity, as G0009 asks of anything that can die or change; its *shape* while shut is
its doorway's tiles in the terrain clamp rather than a circle in the pairwise path.

**Constraint.** One place in `sim` answers "is this tile solid now?" - the map's terrain, plus the doorway tiles of
every shut door - and every reader of solid ground in a running world goes through it: the solver's terrain clamp,
a shot flying into rock, a blast's clear line, perception's clear line and a shooter's choice of target. A shut door
has no `Body` and no `Collider`; it never enters the pairwise path. A shot or a blast that meets a shut door lands on
the door (G0069) rather than being thrown away as at rock, and a blast's line to the door itself ends at its own
tiles. How the place is held cheaply - it is read inside per-entity loops - is the build's, so long as it is derived
from the doors and is never a second copy a snapshot must carry.

**Scope.** `sim`, while a world runs. **Not** routes: a flow field is the map's, built from terrain alone (ADR 0009),
so a route runs through a shut door and a hunter walks up to it. Not generation: `map` knows doorways and nothing of
doors (missions ticket 12). ADR 0007 is untouched - nothing here is a body of another shape.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A door as circle obstacles* - one per doorway tile, or one wide circle. Separation is the constraint that
gives under crowd pressure (`ConstraintSolver`), so a pack leaning on a door of small circles squeezes through its
seams; one circle half the doorway wide bulges five units either side of the wall, so shots and bodies stop short of
the drawn door.

**No:** *A box body for the door.* A second shape type - ADR 0007's rejected AABB.

**No:** *Writing the door into the `TileMap`.* The map is built once and is a function of its seed (ADR 0009); a world
that edits it carries terrain in its snapshot.

**No:** *Routes that know shut doors.* A route round a shut door leads a hunter away from the man behind it, and the
siege (G0069) would need a second way to find him.

**Revisit when** a second thing blocks for a while - a barricade, a gate that closes - and wants the same place, or
the place's cost shows in a frame.
