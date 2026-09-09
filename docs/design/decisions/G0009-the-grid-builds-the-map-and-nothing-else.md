# G0009: The grid builds the map and nothing else

**Constraint.** The tile grid is how a level is *laid out*. Nothing in the game is quantised to it.
Units move in continuous world units, objects stand at arbitrary positions, and no driver, no AI and
no gameplay system ever asks which tile something is in.

Two consequences that are easy to get wrong:

- **An object placed by the grid is not centred in its tile.** The grid chooses *which* tile
  something goes in; the position within that tile is jittered. Centred objects read as a grid to the
  player immediately, which is the tell that separates a generated level from an authored one.
- **A tile is not an entity.** Ground is data read by the renderer and by geometry. Obstacles remain
  ordinary entities with a `Position` and a `Body`, and collision keeps one path.

**Scope.** Layout, terrain and rendering of the ground. The one thing outside layout that is allowed
to ask the grid a question is *geometry* - where a body may be - because the arena boundary already
worked that way: containment has always been a clamp against a rectangle, never a pairwise test, and
impassable terrain is that same clamp against many smaller rectangles. That is a constraint on
position, not a quantisation of movement: a unit walking beside a wall is at whatever fractional
coordinate it walked to, not snapped to anything.

It does not permit grid-stepped movement, tile-by-tile pathing that moves units between tile centres,
attacks or abilities that resolve per tile, or a spawn that snaps to a tile centre.

**Rejected, and likely to be re-proposed.**

*Making obstacles tiles.* Rejected: it would give collision a second path for something that already
works as an entity, and an obstacle would stop being able to move, die, or be anything other than
scenery. A rock that can be destroyed is an entity; a cliff is terrain.

*Snapping spawns to tile centres because it is simpler.* Rejected - see the first consequence above.
The jitter is the feature.

*Tile-based pathfinding that moves units tile to tile.* Rejected as stated, but the near neighbour is
fine and will probably be right: a flow field computed *over* the grid gives each unit a direction,
and the unit then moves continuously along it. The grid informs the movement; it does not step it.

**Revisit when** a mechanic genuinely needs a discrete cell - area control, a spreading hazard,
terrain that is captured. That is a real category and this decision does not rule it out; it rules
out reaching for the grid when continuous space would have done.
