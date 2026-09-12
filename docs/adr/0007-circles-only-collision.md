# ADR 0007: Every body is a circle

**Trigger.** Adding a `Collider`, an obstacle or a hit shape; proposing AABBs or polygons.

**Enforced by.** Nothing mechanical - caught by reading.

**Constraint.** A `Body` is a radius. Collision tests are circle-circle, everywhere, for every kind
of entity - units, projectiles and obstacles alike. There is no second shape type, no shape
interface, and no dispatch on shape. This narrows ADR 0002, which anticipated "circle and AABB
overlap tests"; the AABB half is not being built.

**Scope.** The simulation's collision. The arena boundary is the one exception and is not a body at
all: it is a rectangle, handled by clamping a position, because there is exactly one of it and it is
never tested pairwise.

Nothing here constrains *rendering*. A future sprite can be any shape it likes over a circular body,
which is normal for top-down games and is why the mismatch is affordable.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *AABBs for obstacles.* Walls are rectangles in almost every top-down game, and the first time a
corridor is built out of circles this will look obviously wrong. Rejected for now because two shape
types means three overlap functions and a dispatch on every pair, and because circle-circle
separation is the one case with no corner case - no axis choice, no ambiguity about which face was
hit, one vector between two centres. Nothing on the roadmap needs a second shape to work, and shape
variety would not make any of it easier to build.

**No:** *Polygons, or a shape interface to "keep it open".* An interface with one implementation is not
flexibility, it is a guess about which axis the change will come along. The circle test is a few
lines; replacing it later is cheaper than maintaining an abstraction over it in the meantime.

**Revisit when** level geometry stops being decoration and starts being the thing fights are fought
around - the first level built from corridors and cover rather than scattered blockers. That is a
level-design milestone, not a code-quality one; do not revisit it because the collision code looks
too simple.
