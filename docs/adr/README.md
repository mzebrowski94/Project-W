# Architecture decisions

One line each. Open a record only when you are about to work against it or propose changing it - each
one opens with a **Trigger** line saying when that is.

The **Do not build** column is the negative index: if what you are about to write appears there, the
decision has already been made and the record says why. Check it before proposing a structural change.

| # | Decision | Do not build | Status |
|---|---|---|---|
| [0001](0001-ashley-as-entity-framework.md) | Ashley `PooledEngine` is the entity framework; entity views and mappers are cached. | artemis-odb; a hand-rolled ECS | accepted |
| [0002](0002-no-box2d.md) | No Box2D and no box2dlights; collision is ours. | any physics engine; any dependency from JitPack | accepted |
| [0003](0003-sim-as-separate-module.md) | The simulation is its own Gradle module, with `ArchitectureTest` closing what the module graph cannot. | packages plus an ArchUnit rule instead of a module | accepted |
| [0004](0004-net-ready-single-player.md) | Single-player, but the simulation stays deterministic and net-ready. | `Gdx.graphics.getDeltaTime()` inside a system; any networking code | accepted |
| [0005](0005-quality-tooling.md) | Spotless, Error Prone, NullAway and Lombok run on every build. | Checkstyle | accepted |
| [0006](0006-gradle-in-the-sandbox.md) | Claude runs the real Gradle build in its sandbox. | compiling with plain `javac`; `./gradlew` in the sandbox | accepted |
| [0007](0007-circles-only-collision.md) | Every body is a circle; no AABBs. Narrows 0002. | AABBs; polygons; a shape interface | accepted |
| [0008](0008-kinds-are-typed-definition-tables.md) | A kind of thing is an enum plus a definition record in a table; not a marker component, and not a data file. | a marker component per kind; data-driven archetypes; `HashMap<Kind, Def>` | accepted |
| [0009](0009-map-as-a-separate-module.md) | The terrain field and map generation are their own module, depending on nothing. | a `sim.map` package; `TileMap` in `sim`; `Rng` moved into `map` | accepted |
| [0010](0010-libgdx-tiled-maps-renderer-not-model.md) | libGDX's tiled map API is not our model - its tile carries a `TextureRegion`. Its renderer and the Tiled editor stay open. | `gdx.maps.tiled` as the model; relaxing the graphics ban in `sim`; our own `.tmx` parser | accepted |
| [0011](0011-a-gamepad-reaches-the-game-through-our-own-port.md) | Gamepads reach the game through a port of our own; gdx-controllers sits behind it and nothing else sees it. | calling gdx-controllers outside the adapter; an event-stream port; binding buttons by letter | accepted |

Decisions about what the game *is*, rather than how it is built, live in
`docs/design/decisions/`.

## Writing one here

An ADR answers *why*, looking backward, and is not edited as the code evolves - a changed decision
gets a new record that supersedes the old one. `docs/workflow.md` answers *how to work here*,
looking forward. Neither restates the other.

Write an ADR only when the decision is structural and expensive to reverse: a dependency, a module
boundary, how the simulation advances, a build-level constraint. Not for naming, refactors, or
anything the code shows plainly.

Keep six things and nothing else:

- **Trigger** - the work that should cause this file to be opened. One line, first.
- **Enforced by** - the test that fails if the constraint is broken, or `Nothing mechanical - caught
  by reading`. Say which; a rule nothing enforces needs more attention, not less.
- **Constraint** - what a future session must respect, stated as a rule rather than a story.
- **Scope** - where it applies, and where it deliberately does not. The second half is usually the
  more useful.
- **DO NOT BUILD** - the rejected alternatives likely to be proposed again, one reason each, each
  prefixed `**No:**` so a skim cannot mistake one for the design. Add the name to the table above.
- **Revisit when** - the trigger, if there is one.

Leave out how the decision was reached, dates, version numbers that belong in `gradle.properties`,
and consequences that turned out not to matter.

An item that is deferred rather than rejected is *not* prefixed `No:` - say "not rejected, deferred"
and leave it unmarked, as G0017's formations item does.

This format is provisional. Propose a different shape if one would serve better.
