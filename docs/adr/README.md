# Architecture decisions

One line each. Open a record only when you are about to work against it or propose changing it - each
one opens with its **Status**, then a **Trigger** line saying when that is.

The **Do not build** column is the negative index: if what you are about to write appears there, the
decision has already been made and the record says why. Check it before proposing a structural change.
The Status column says whether a record still holds - see *Status* below.

| # | Decision | Do not build | Status |
|---|---|---|---|
| [0001](0001-ashley-as-entity-framework.md) | Ashley `PooledEngine` is the entity framework; entity views and mappers are cached. | artemis-odb; a hand-rolled ECS | accepted |
| [0002](0002-no-box2d.md) | No Box2D and no box2dlights; collision is ours. | any physics engine; any dependency from JitPack | amended by ADR 0007: every body is a circle, so there is no AABB and no shape interface |
| [0003](0003-sim-as-separate-module.md) | The simulation is its own Gradle module, with `ArchitectureTest` closing what the module graph cannot. | packages plus an ArchUnit rule instead of a module | amended by ADR 0017: a subsystem with nothing to wall off from rendering may be a fenced package instead of a module |
| [0004](0004-net-ready-single-player.md) | Single-player, but the simulation stays deterministic and net-ready. | `Gdx.graphics.getDeltaTime()` inside a system | amended by ADR 0012: its scope no longer rules out networking code |
| [0005](0005-quality-tooling.md) | Spotless, Error Prone, NullAway and Lombok run on every build. | Checkstyle | accepted |
| [0006](0006-gradle-in-the-sandbox.md) | Claude runs the real Gradle build in its sandbox. | compiling with plain `javac`; `./gradlew` in the sandbox | accepted |
| [0007](0007-circles-only-collision.md) | Every body is a circle; no AABBs. Narrows 0002. | AABBs; polygons; a shape interface | accepted |
| [0008](0008-kinds-are-typed-definition-tables.md) | A kind of thing is an enum plus a definition record in a table; not a marker component, and not a data file. | a marker component per kind; data-driven archetypes; `HashMap<Kind, Def>` | accepted |
| [0009](0009-map-as-a-separate-module.md) | The terrain field and map generation are their own module, depending on nothing. | a `sim.map` package; `TileMap` in `sim`; `Rng` moved into `map` | accepted |
| [0010](0010-libgdx-tiled-maps-renderer-not-model.md) | libGDX's tiled map API is not our model - its tile carries a `TextureRegion`. Its renderer and the Tiled editor stay open. | `gdx.maps.tiled` as the model; relaxing the graphics ban in `sim`; our own `.tmx` parser | accepted |
| [0011](0011-a-gamepad-reaches-the-game-through-our-own-port.md) | Gamepads reach the game through a port of our own; gdx-controllers sits behind it and nothing else sees it. | calling gdx-controllers outside the adapter; an event-stream port; binding buttons by letter | accepted |
| [0012](0012-online-play-is-host-relayed-lockstep.md) | Online play is lockstep: every peer runs the simulation and only inputs cross the wire; a sequencer on its own thread deadlines them, held input repeats and events arrive once; the host ships its config; a desync is dumped and healed. Netty behind a transport port of our own. | replicating state every tick; a peer-to-peer mesh; waiting for a late input; the sequencer in the host's frame loop; a one-off input as a held flag; requiring matching `game.properties`; quantised or delta snapshots; a network type outside the adapter; reflective or class-naming decoders; KryoNet, Aeron, jvm-libp2p, Nakama | amended by ADR 0016: another entity is named by its Handle, never by an Entity field; amended by ADR 0018: every config key has one scope, and the host sends its session config too |
| [0013](0013-entity-order-is-spawn-order.md) | The engine's entities and every family iterate in spawn order; a shim makes Ashley's lists ordered, and composition is fixed at spawn, so a snapshot carries one order. | per-family orders written back by reflection; a stable id and a sort in every system; forking Ashley; a component added after spawn; sorting only at snapshot time | accepted |
| [0014](0014-the-snapshot-is-written-from-component-fields.md) | The snapshot codec is derived once from the component classes; a component field is a primitive, an enum, an `Entity` or a named collection; a system holds no state across ticks. | a hand-written read/write per component; Java serialization, Kryo, class names in the payload; reflection during a tick; a counter, phase or stream held in a system | amended by ADR 0016: a component field may no longer be an Entity |
| [0015](0015-the-interface-logic-is-its-own-module.md) | What the player is shown and how he moves through it is decided in the `ui` module, which reads `sim`, never writes it, and has no drawing or devices; `core` draws. | UI decisions in `Screen`s or painters; packages plus an ArchUnit rule instead of a module; `ui` importing `Gdx`, graphics, scene2d, input or `net`; widgets reading `sim` directly; an event bus in `sim` for the HUD | amended by ADR 0017: a subsystem with nothing to wall off from rendering may be a fenced package instead of a module |
| [0016](0016-an-entity-is-named-by-its-handle.md) | An entity another one must find again is named by its `Handle` - a `long` issued at spawn, never reused - and found with `Handles.resolve`; no component holds an `Entity`. | a generation counter; an id-to-entity map beside the engine; replacing Ashley to get handles | accepted |
| [0017](0017-upgrades-are-a-fenced-package-reading-one-ledger.md) | Upgrades and adaptations are data held in one ledger on the level entity and folded at the one reader of each number, through `Modifiers`; `sim.upgrade` is a fenced package with four entry points, `UpgradeSystem` among them. | a per-entity current-stats component; a dispatcher that applies effects to entities; an on-spawn hook; adding or removing a component for a tactic or ability; a running total edited in place; a stat with two readers; a Gradle module for the subsystem now | amended by ADR 0020: sim.upgrade is sim.system.upgrade, one fenced package among several |
| [0018](0018-a-config-key-has-one-scope.md) | Every config key has one scope: world config (the host ships the text it bound), session config (the host sends its values in each welcome, a joiner never reads its own) or local config (never sent). | sending every file to every peer; session config as text for the joiner to parse; requiring session config to match at the handshake | accepted |
| [0019](0019-a-component-names-its-writers.md) | Who writes each component is the *Written by* column of `docs/components.md`, checked against the bytecode either way: a write is a field set, a changing method or a changed collection, credited to the system that reaches it through any helper; building a world is not a write. | an annotation on a component naming its writers; writers in a table in test code; ownership per field for every component; credit to the fenced package; reading writes off source text; a required "why several" note; excluding every call into `Spawner` | accepted |
| [0020](0020-systems-live-in-fenced-packages-by-domain.md) | Every system lives in a fenced package by domain under `sim.system`; package-private by default, public only its systems and declared ports; packages meet only through `query` (public, stateless, writing nothing), the shared packages and ports; a system goes with the helpers it uses. | `@Internal`; `.internal` subpackages; packages by tick phase; splitting `component` or `def` by domain; fine-grained packages; a facade per package; a Gradle module per domain; an `annotation` package | accepted |

Decisions about what the game *is*, rather than how it is built, live in
`docs/design/decisions/`.

## Writing one here

An ADR answers *why*, looking backward; `docs/workflow.md` answers *how to work here*, looking forward.
Neither restates the other. Write an ADR only when the decision is structural and expensive to reverse:
a dependency, a module boundary, how the simulation advances, a build-level constraint. Not for naming,
refactors, or anything the code shows plainly.

A record is its title, its status and six fields, and nothing else:

```
# 0016: An entity is named by its handle

**Status.** accepted

**Trigger.** ...
```

- **Status** - line 3, word for word the index's Status cell. See *Status* below.
- **Trigger** - line 5: the work that should cause this file to be opened. One line.
- **Enforced by** - the test that fails if the constraint is broken, or `Nothing mechanical - caught
  by reading`. Say which; a rule nothing enforces needs more attention, not less.
- **Constraint** - what a future session must respect, stated as a rule rather than a story.
- **Scope** - where it applies, and where it deliberately does not. The second half is usually the
  more useful.
- **DO NOT BUILD** - the rejected alternatives likely to be proposed again, one reason each, each
  prefixed `**No:**` so a skim cannot mistake one for the design. Add the name to the table above.
- **Revisit when** - the trigger, if there is one.

Leave out how the decision was reached, dates, version numbers that belong in `gradle.properties`,
and consequences that turned out not to matter. An item that is deferred rather than rejected is *not*
prefixed `No:` - say "not rejected, deferred" and leave it unmarked, as G0017's formations item does.

## Status

What a reader must know before trusting a record, in one of three forms - the same in the index cell and
on the record's line 3:

| Status | Means |
|---|---|
| `accepted` | In force as written. |
| `amended by <id>: <what changed>` | In force except for what `<id>` changes - read `<id>` before relying on that part. Several are joined with `; `. |
| `superseded by <id>: <why>` | Nothing in it holds; kept for the reasoning. Its title says `(superseded by <id>)` too, and no source comment may cite it. |

`<id>` is `ADR 0012` or `G0053`. *Narrowed*, *scoped* and *superseded in part* are all `amended by`: to
a reader they mean one thing - read the other record too. Whether a decision is built yet is not a
status; the code says that.

**A record is not edited as the code evolves.** A changed decision gets a new record, which says
`**Amends <id>**` or `**Supersedes <id>**` where it changes the old one; the change is written there, or -
when it only makes sense against the old record - in an `## Amended by <id>` section at the old record's
end. Beyond that the old record gets three edits and no others: its status, in the record and the index;
`(superseded by <id>)` in its title when nothing of it holds; and `**Reversed by <id>:**` in place of
`**No:**` on a rejected alternative the new record takes back, whose name also leaves the **Do not build**
column - so a reversed rejection never reads as one. A superseded record's **Do not build** cell is `-`. `DecisionRecordsTest` holds both sides of every amendment, the status, the title, the citations and
the tests named under *Enforced by*; a reversal is caught by reading.

