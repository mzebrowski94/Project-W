# Architecture decisions

One line each. Open a record only when you are about to work against it or propose changing it.

| # | Decision | Status |
|---|---|---|
| [0001](0001-ashley-as-entity-framework.md) | Ashley `PooledEngine` is the entity framework; entity views and mappers are cached. | accepted |
| [0002](0002-no-box2d.md) | No Box2D and no box2dlights; collision is ours. | accepted |
| [0003](0003-sim-as-separate-module.md) | The simulation is its own Gradle module, with `ArchitectureTest` closing what the module graph cannot. | accepted |
| [0004](0004-net-ready-single-player.md) | Single-player, but the simulation stays deterministic and net-ready. | accepted |
| [0005](0005-quality-tooling.md) | Spotless, Error Prone, NullAway and Lombok run on every build. | accepted |
| [0006](0006-gradle-in-the-sandbox.md) | Claude runs the real Gradle build in its sandbox. | accepted |
| [0007](0007-circles-only-collision.md) | Every body is a circle; no AABBs. Narrows 0002. | accepted |
| [0008](0008-kinds-are-typed-definition-tables.md) | A kind of thing is an enum plus a definition record in a table; not a marker component, and not a data file. | accepted |

Decisions about what the game *is*, rather than how it is built, live in
`docs/design/decisions/`.

## Writing one here

An ADR answers *why*, looking backward, and is not edited as the code evolves - a changed decision
gets a new record that supersedes the old one. `docs/workflow.md` answers *how to work here*,
looking forward. Neither restates the other.

Write an ADR only when the decision is structural and expensive to reverse: a dependency, a module
boundary, how the simulation advances, a build-level constraint. Not for naming, refactors, or
anything the code shows plainly.

Keep four things and nothing else:

- **Constraint** - what a future session must respect, stated as a rule rather than a story.
- **Scope** - where it applies, and where it deliberately does not. The second half is usually the
  more useful.
- **Rejected alternatives** - only the ones likely to be proposed again, one reason each.
- **Revisit when** - the trigger, if there is one.

Leave out how the decision was reached, dates, version numbers that belong in `gradle.properties`,
and consequences that turned out not to matter.

This format is provisional. Propose a different shape if one would serve better.
