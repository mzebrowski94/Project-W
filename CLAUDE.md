# Project-W

A top-down 2D arcade shooter in Java 25 + libGDX, inspired by *Triumph! War 2099* (Darksun Games,
2001): you command a squad, the action is arcade-fast, the thinking is tactical. Our own levels,
units and progression - not a remake.

Author: Mateusz Zebrowski. Package root `pl.mzebrows.pwt`.

## Modules

| Module | What lives there |
|---|---|
| `sim` | The deterministic simulation: entities, components, systems, world state. No rendering, no assets, no I/O. |
| `core` | The libGDX application: rendering, input, assets, screens, config loading. |
| `lwjgl3` | Desktop launcher. |

Dependencies point one way: `lwjgl3` -> `core` -> `sim`. `ArchitectureTest` fails the build if
anything in `sim` reaches for rendering, the wall clock, unseeded randomness or hash iteration
order - so those rules are enforced, not remembered.

## Verify

```
.claude/scripts/verify.sh            # format, compile, lint, test
.claude/scripts/verify.sh :sim:test  # narrower, when iterating
```

~3s warm; the first run in a session bootstraps the toolchain and takes ~35s. Failures print in
full, the log is at `ai/logs/verify.log`. **Run it before claiming anything works.**

The sandbox has no display, so the game itself cannot be run there - `:lwjgl3:run` happens on
Mateusz's Windows machine. See `docs/workflow.md` for everything else about builds.

## Design pillars

What makes it this game. A feature that breaks one of these is wrong regardless of its details -
stop and raise it rather than working around it.

1. **Arcade first.** Fights are decided by reflexes, levels by tactics. A mechanic that makes the
   player stop and compute belongs in a different game.
2. **The commander is a unit, not a cursor.** You stand on the field, you can be killed, and you are
   the squad's biggest weapon. No detached god view, no invulnerability.
3. **The squad is the resource.** Troopers are won and lost during play. No shop, no loadout screen,
   no economy between levels.
4. **One screen, one fight.** A level reads at a glance. No fog of war, no off-screen management.
5. **State is visible.** What an entity can do is legible from how it looks. The player is never
   tracking a number the game does not show.
6. **Every run is reproducible** (ADR 0004). No wall-clock effects, no unseeded variety, no bug that
   cannot be replayed.

Invariants, the input map and the open questions are in `docs/design/README.md`. Read it before any
gameplay change - the `feature` skill has the procedure.

**We are in the alpha phase**: one system per session, built to be built on, with an optional
scenario so Mateusz can run it. `docs/design/alpha-phase.md` has the method and the list of
shortcuts still standing.

These six are inline rather than loaded on demand **by decision, not by oversight**: a contradiction
usually slips in during a session nobody framed as gameplay work, which is exactly when an
on-demand file would not be read. Do not migrate them into a skill.

## How we work

**Agree, then plan, then write.** For non-trivial work, get shared understanding first - ask about
the decisions the request genuinely leaves open, using the question tool rather than prose. Then
present a plan and wait. Small, well-specified changes just get done.

**Tests first, in this order:** (1) one or a few general-level tests saying what the logic should
do, broad strokes; (2) the implementation; (3) back to the tests to fill in the specific cases
found while implementing - boundaries, failure modes, awkward inputs - checking each is a real case
and not noise. Step 3 is the point; specifics discovered while implementing beat specifics guessed
beforehand. Conventions live in the `unit-tests` skill.

**Mateusz commits.** Never run `git commit`, `git add` or `git push`. Leave the tree for review.

**Ask before adding a dependency.** Every library is a decision, not an implementation detail.

**Name friction when you hit it.** If something would be fixed by a change on Mateusz's side - a
permission, an allowlist entry, an access grant, a setting - say so in one line at the moment you
hit it, then carry on. Do not silently work around the same obstacle every session. Known ones are
listed in `docs/workflow.md`.

## Rules that nothing enforces

Everything else is caught by Spotless, Error Prone, NullAway or a test. These are not:

- **No allocation per frame in `sim`.** Pool components, reuse instances, no boxing, no streams or
  lambdas in a per-entity loop. Streams are fine in setup and tooling.
- **No logging inside per-frame or per-entity loops**, at any level.
- **Never invent an asset path.** Every asset is a constant in `Assets`; missing files log and fall
  back rather than crashing.

## Where to look things up

| File | Read it when |
|---|---|
| `docs/conventions.md` | Writing Java here - style, allocation, determinism, nullability. |
| `docs/architecture.md` | Adding a system or component, or crossing the sim/client line. |
| `docs/workflow.md` | Builds, recipes, something broke, or environment friction. |
| `docs/glossary.md` | Naming anything in the game domain. |
| `docs/design/README.md` | Any gameplay change - invariants, input map, open questions. |
| `docs/design/alpha-phase.md` | **Every session right now** - how a phase session goes, how to add a scenario, what is still scaffolding. |
| `docs/adr/README.md` | Before proposing to change a structural decision. |
| `docs/design/decisions/` | Before re-proposing a gameplay alternative. |

Skills: `feature` (design a gameplay change), `new-system` (build it), `unit-tests` (write tests),
`perf-audit` (check a hot path).
