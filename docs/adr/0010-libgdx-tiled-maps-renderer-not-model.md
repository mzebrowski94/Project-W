# 0010: libGDX's tiled map API — its renderer, maybe; its model, never

**Constraint.** `com.badlogic.gdx.maps.tiled` is not the game's map model. `TileMap` in the `map`
module is, and it stays free of every graphics type.

The reason is not preference, it is the type signature. **`TiledMapTile` — the interface, not an
implementation — declares `getTextureRegion()` and `setTextureRegion(TextureRegion)`.** Verified
against `gdx-1.14.2.jar` rather than the wiki, which does not mention it. So libGDX's tile is bound
to `com.badlogic.gdx.graphics.g2d`, and by extension so are `TiledMapTileLayer`, `TiledMap` and
every loader that returns one.

That collides with two rules already in the build:

- `ArchitectureTest.simulationIsHeadless` keeps `com.badlogic.gdx.graphics..` out of `sim`.
- `MapArchitectureTest.mapKnowsNothingOfTheGameAroundIt` keeps all of `com.badlogic..` out of `map`.

A `TiledMap` could therefore only live in `core` — which is precisely where the thing every system
reads each tick must not live. libGDX's is a rendering model with map-shaped data attached; ours is
map data that the client decides how to draw. That is the same split as `Look`/`Palette` (G0003), and
adopting theirs would undo it.

**Scope.** The *model*. Two other halves of that API are untouched by this and remain live options:

- **`OrthogonalTiledMapRenderer`** is a real candidate once a texture atlas exists. It batches and
  culls properly, and it costs nothing structurally: it takes a `TiledMap`, so the shape would be to
  keep `TileMap` as the model and build a throwaway `TiledMap` view from it in `core` when a map is
  bound. `TerrainRenderer` already culls to the visible rect and merges runs, so the work carries
  over rather than being thrown away.
- **`TmxMapLoader` and the Tiled editor** are the more interesting option, and the reason to keep
  this door open. Hand-authoring map *fragments* in a real editor and assembling them procedurally is
  a well-trodden pattern, and it would beat writing layouts as Java literals the moment there is more
  than a handful of them. `MapObjects` is the natural home for spawn points and trigger areas.

Neither is adopted now. Both would be a conversion at the edge, never a change to what `sim` reads.

**Rejected, and likely to be re-proposed.**

*"libGDX already has tile maps, use those."* The honest version of this proposal is "put a
`TextureRegion` in the thing the simulation reads every tick", and it fails the build. Worth writing
down because the API looks like exactly what we built and somebody will reasonably ask.

*Relaxing the graphics ban in `sim` so `TiledMap` can be shared.* Rejected: that ban is what makes
the simulation headless and testable without a GL context, which is the whole of ADR 0003.

*Writing our own `.tmx` parser to get Tiled authoring without the dependency.* Premature. If Tiled
authoring is wanted, `TmxMapLoader` in `core` converting into a `TileMap` is less code and less to
maintain than a parser of our own.

**Revisit when** either (a) the texture atlas lands, at which point compare `OrthogonalTiledMapRenderer`
against extending `TerrainRenderer`; or (b) a level file format is needed — which is the moment to
try Tiled properly, since the format question and the authoring question are the same question.
