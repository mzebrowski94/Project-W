# 02 - Fence and move the client

Type: task

Blocked by: 01

## Question

Move every client class into its layer package behind the fences, one package per stage.

## Read first

`.scratch/client-packages/map.md` (01's split), `.scratch/system-modules/issues/04-pilot-squad.md` and
`05-remaining-packages.md` (*Answer* - what moving broke last time), `ClientArchitectureTest`.

## Steps

1. The fences first, red: `FENCED`, `PORTS` and entries, every class in a declared package, the libGDX rules
   by package and the suffix rule kept for the decision classes outside `play` (01's answer, section 4) -
   each `.because` naming the new ADR, failing on zero classes.
2. Before the moves, from 01's answer: `OnlineSession.host`/`.join` take the machine id from their screens
   (`LocalMachineId` stops being a port); `ConfigDefaultsTest` splits three ways; `generateShippedClientConfig`
   writes each `Shipped*Config` into its record's package; `component-index.py` reads `client` recursively.
3. One package per stage with `.claude/scripts/move-classes.py`; compile; make public what it names;
   `package-info.java`; `verify.sh`; commit.
4. The new ADR, 0021 (amends 0020's *Scope*) with its row in `docs/adr/README.md` and 0020's status;
   `docs/architecture.md`'s *core (client)* section as a package table.

## Done when

`verify.sh` green, no class directly in `client` but what 01 left in the root, the ADR in `docs/adr/`.

## Answer

Done 2026-10-03: the pre-move changes, then one commit per layer - config, input, draw, play, then hud with
the fences - then the docs. The root holds the eleven classes 01 left there; `split.txt` and the tree agree
(`system-graph.py --split` reports 13 ports, exactly `ClientArchitectureTest.PORTS`, and 35 entries).

- **Pre-move:** `OnlineSession.host`/`.join` take the machine id, and `MenuScreen`/`JoinScreen` pass
  `LocalMachineId.load()` - it is no longer a port. `ConfigDefaultsTest` split three ways; two invariant
  rows (`map-and-view.md`, `players.md`) now cite `GraphicConfigTest`, which `DecisionRecordsTest` caught.
  `generateShippedClientConfig` takes a package per file and clears its output first, so a file left from
  an earlier package cannot compile beside its successor. `component-index.py` reads `client` recursively;
  `docs/components.md` unchanged.
- **The fences** (each `.because` naming ADR 0021, failing on zero classes) were red on the flat package,
  then committed with the last move so every commit is green. Seen failing on planted violations: `play`
  reading `Gdx.app`, a `hud` class taking an `input` entry. Not caught: an array of another layer's class
  (`new KeyHolds[0]`) - ArchUnit records no dependency for it; a parameter, field or call of that type is.
- **What compile asked for:** entries public as predicted, and their members the root calls -
  `PageControls` (nine members), `NavInputReader` (constructor, `read`), `Drawn.x`/`.y`, `LocalAim`'s
  constructor, `clear`, `set`, the painters' `render`/`draw` and `MenuPainter`'s page helpers,
  `HeldUpgradesPainter.glide`/`home`/`wheel`, the hints' `follow` and `PageHints`' three. Two public methods
  gained the javadoc summary Error Prone requires (`NavInputReader.read`, `BottomBarPainter.render`). No
  class was made public that 01 did not name.
- **Docs:** ADR 0021 with its row; 0020's status says it is amended. `docs/architecture.md`'s *core
  (client)* opens with the package table; the `unit-tests` skill's exemplar path and its client rules
  follow.
