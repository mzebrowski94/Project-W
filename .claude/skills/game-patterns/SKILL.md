---
name: game-patterns
description: Named production techniques for the parts of Project-W that have a standard answer - terrain grids, scatter, cameras, pathing, procedural generation, rendering many small things. Use before inventing an algorithm for level layout, object placement, camera behaviour, enemy navigation or map generation.
---

# Reach for the named thing

Most of what this game still needs has a standard answer that shipped games converged on.
Inventing a worse one costs a rewrite later, and the invented version is always harder to read
because nobody can look it up.

**This skill is a shortlist, not a library.** Each entry says what the technique is, why it is the
right pick *here*, and what it would cost to get wrong. If a task matches one, use it and name it in
the code comment so the next session recognises it.

If a task looks like it needs a technique that is not here, say so and add it - with the trade-off,
not just the name.

## Terrain and level layout

**One place converts world coordinates to tile coordinates.** `TileMap` owns `colAt`/`rowAt` and
nothing else open-codes `(int)(x / tileSize)`. Off-by-one and negative-coordinate truncation bugs in
grid conversion are subtle, reproduce rarely, and get fixed independently in four places.

**Out of bounds reads as impassable.** A grid lookup outside the map returns the blocking terrain
rather than throwing or returning a default. Every caller then handles the edge without a guard, and
a bug at the border fails safe instead of letting something walk off the map.

**A zeroed grid is open ground.** The first enum constant of `Terrain` is the passable default, so an
uninitialised or partially built map is empty ground rather than solid rock. Getting this backwards
means a half-built map looks like a bug in the generator rather than in the caller.

**Gather pushes, then resolve; never apply one per tile.** Resolving a body against each solid tile
as you find it looks correct and is not. A body in a gap narrower than itself gets an equal push from
the rock on each side, the two cancel exactly, and it slides through a wall it does not fit through.
Accumulate the maximum push per direction, then decide: opposing demands on one axis mean there is no
room on that axis, and the answer is to leave the way it came in - the nearer end of the tiles doing
the squeezing.

**Suppress internal edges.** A body sliding along a tiled wall is resolved against each tile in turn,
and the tile it is half off pushes it sideways from its own corner - the player feels this as catching
on every seam. A face buried in the next tile of the same wall is not a surface; check the neighbour
before pushing away from it.

## Placing objects

**Poisson-disc sampling (Bridson's algorithm)** for scattering objects over an area - rocks, growths,
spawn points. Every sample is at least `r` from every other, and the result looks deliberately
spaced rather than clumped. Naive rejection sampling clumps visibly, degrades badly as the area
fills, and cannot tell you whether it failed or the area was full.

**A placement routine reports what it could not place.** Silently placing fewer things than asked is
right for a throwaway test arena and wrong for a level: the caller cannot tell a full arena from a
broken generator.

**Jitter inside the cell, never the centre.** When a grid chooses *which* cell an object goes in, the
object's actual position is a random offset within that cell. Centred objects read as a grid to the
player instantly, which is the tell that separates a generated level from an authored one.

## Randomness

**One stream per concern, split from the seed.** Terrain, scatter and gameplay each get their own
generator derived from the level seed. Sharing one stream means re-tuning scatter reshuffles the
terrain you had just decided you liked - the classic reason procedural generation is miserable to
iterate on.

Streams are derived deterministically from the one seed, so a level is still reproducible from a
single number (pillar 6).

## Cameras

**The follow rig is four things, in this order:** a dead zone the target moves inside without the
camera reacting; smoothing (exponential ease toward the goal, framerate-independent); look-ahead
that leans the view the way the player is aiming or moving; and a clamp to the map bounds applied
*last*. Skipping the dead zone makes the camera jitter on every small movement; applying the clamp
before the smoothing makes it stick at map edges.

**Multiple targets: bounding box, then fit.** For local co-op the camera centres the box containing
every target and zooms to fit it, with a maximum separation past which it stops rather than zooming
out forever. This is why the camera returns *centre and zoom together* - they are one decision, and
splitting them means the zoom fights the follow.

**Who the camera watches is data, not a lookup.** A marker component on the entities that matter beats
"find the player" in the client: co-op becomes a second marker and no client change, and the view can
be drawn to something that is not a player without inventing a second mechanism.

**Read input against the camera the player last saw.** Aim is unprojected through the camera, so the
order within a frame is: read input, step the simulation, move the camera, draw. Moving the camera
first interprets the mouse against a view nobody has seen yet.

**Look-ahead follows movement, never aim.** Aim is a world point unprojected through this camera, so
leaning the view toward it moves the point, which leans it further - a feedback loop that reads as
the camera drifting off on its own. Movement input comes from the keyboard and cannot do that.

**Ease the lead itself, well slower than the camera follows.** Feeding raw input into look-ahead
snaps the goal the whole lead distance the instant the player reverses, and tapping left-right twice
a second shakes the view hard. A separate time constant of around half a second means a flick
averages out to almost nothing while a sustained run still earns the full lead - and behind a dead
zone, fidgeting on the spot moves the camera not at all. **Every test that holds one direction passes
either way**, so this is only found by playing it or by a test that deliberately reverses.

## Navigation

**Flow field, not A* per unit.** Many units converging on one target is exactly what a flow field is
for: cost is one grid pass per rebuild regardless of unit count, and every unit reads a direction
from its own tile. Per-unit A* costs a search each and produces units that walk in single file.

Rebuild it when the goal moves more than a tile, not every tick.

**Nav data is not the AI that uses it.** The field is a pure function of terrain and a goal, so it
lives with the terrain; deciding who follows it, and how fast, is a system.

## Rendering many small things

**Cull to the visible rect first.** Draw only the tiles the camera can see. This is what makes a map
larger than the view cost the same as one that fits.

**Merge runs.** Adjacent tiles that draw identically become one quad. A row of thirty identical
ground tiles is one rectangle, not thirty, and the scan costs nothing.

**Chunk and cache when it stops being free.** Bake tile geometry into per-chunk meshes rebuilt only
when a chunk changes. Not needed while terrain is flat-coloured rectangles; the moment it is a
texture atlas, it is.

## Procedural generation

**Generate, then validate, then repair - never trust the first result.** A generated level must be
checked for the properties it promised (every spawn point reachable, no sealed pockets) and either
repaired or regenerated. Flood fill from the player's start is the cheap connectivity check.

**Regions before tiles.** Decide the large shapes first - a Voronoi partition, a BSP split, room
graphs - then fill them. Deciding tile by tile produces noise; deciding region by region produces
places, and gives the player something to recognise.

**Keep the authored path open.** Whatever generates a map must produce the same type a hand-written
one does, so a fixed level and a generated one are indistinguishable downstream.
