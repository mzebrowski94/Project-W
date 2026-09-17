# Project-W

Top-down 2D arcade shooter. Java 25 + libGDX. Inspired by *Triumph! War 2099* (Darksun Games, 2001):
you command a squad, the action is arcade-fast, the thinking tactical. Our own levels, units and
progression - not a remake.

Author: Mateusz Zebrowski. Package root `pl.mzebrows.pwt`.

## Scope of a change

Default: the **smallest coherent patch** that satisfies the task. No opportunistic refactor of code you
happened to open, no abstraction the task does not require, no renaming a public concept, no config
change, no new dependency - unless asked. If the task turns out to need a larger structural change:
stop, name the dependency chain, wait.

**Reuse before you create.** A second way to do what already has one way is a defect. Already here:
`Spawner` (entity kinds), `Defs` (kind definitions), `Pool` (transients), `Rng` (randomness),
`SpatialGrid` (neighbour queries), `ConstraintSolver` (where bodies may be), `Sight` (detection tests),
`FlowField` (routes), `Scatter` (placement), `Maps` (layouts and their validity checks), `Log`,
`GameProperties` (config), `AlphaWorlds` (test fixtures) - plus a component or system for most
behaviour. `docs/glossary.md` is the cheap check: if the word exists, the thing usually does too.

**Read narrow.** The target module, the classes the task names, their nearest tests. Widen only when a
symbol will not resolve, a dependency's behaviour is unclear, you cross a module boundary, or a test
failure says the picture is incomplete. Never read the tree wholesale, and scope every recursive search
to `sim/src core/src map/src docs` - `grep` across the repo times out on the Windows mount.

## Before you say it is done

Each line is a yes/no you can check yourself. A no is not done.

- [ ] `.claude/scripts/verify.sh` green. Reading the code is not evidence it compiles.
- [ ] Nothing allocates **inside a per-entity loop** - no `new`, boxing, varargs, capturing lambda,
      stream, iterator. Once per system per tick is fine.
- [ ] No log call inside a per-frame or per-entity loop, any level.
- [ ] No wall clock, `Math.random`, `java.util.Random`, `HashMap`/`HashSet` iteration, or
      `Math.sin`/`cos`/`atan2` anywhere in `sim` or `map`.
- [ ] No O(N²) entity-pair loop - go through `SpatialGrid`.
- [ ] New tunables in `assets/game.properties` or `assets/graphic.properties`, never a literal.
- [ ] New domain words in `docs/glossary.md`, and only that word used afterwards.
- [ ] A new pooled component's `reset()` clears **every** field.
- [ ] A new system registered in `Simulation` at a position that breaks none of
      `docs/simulation-order.md`'s constraints, and you said why.
- [ ] No system holds a reference to another, and no component holds an `Entity` across ticks
      (G0004, G0010, G0016 - pooling makes a kept reference come back aimed at something else).
- [ ] A new kind of thing is a constant, a definition and a `game.properties` block - never a new
      class, marker, `spawn` method or branch (G0008).
- [ ] Nothing in a dodge is drawn from the `Rng` - not the side, not the timing, not the distance
      (G0012).
- [ ] Nothing you built appears in the **Do not build** column of `docs/adr/README.md` or
      `docs/design/decisions/README.md`.
- [ ] Diff contains nothing the task did not need.
- [ ] A gameplay change wrote down what it settled: invariant row, decision record, or input-map row.
      If none, say so and why.

## Modules

| Module | What lives there |
|---|---|
| `map` | The ground a level is played on: tile grid, terrain, regions, generation. Depends on nothing at all. |
| `sim` | The deterministic simulation: entities, components, systems, world state. No rendering, assets or I/O. |
| `core` | The libGDX application: rendering, input, assets, screens, config loading. |
| `lwjgl3` | Desktop launcher. |

`lwjgl3` -> `core` -> `sim` -> `map`, one way. `ArchitectureTest` fails the build if `sim` reaches for
rendering, the wall clock, unseeded randomness or hash iteration order; `MapArchitectureTest` does the
same for `map`, which may not reach for those *or* for an entity. Enforced, not remembered.

**`map` owns the field; `sim` owns what stands on it and what happens over time** (ADR 0009). Pure
function of terrain and geometry -> `map`. Reads a clock, an entity or a component -> `sim`.

## Verify

```
.claude/scripts/verify.sh            # format, compile, lint, test
.claude/scripts/verify.sh :sim:test  # narrower, when iterating
```

~3s warm; the first run of a session bootstraps the toolchain, ~35s. Failures print in full; log at
`ai/logs/verify.log`. **Run it before claiming anything works.** Start at the cheapest level that can
refute the change and escalate on a trigger - the ladder is in `docs/workflow.md`, along with the
protocol for reacting to a failure. Never claim done without a full green run.

No display in the sandbox, so the game cannot be run there. `:lwjgl3:run` is Mateusz's Windows machine.

## Design pillars

What makes it this game. A feature that breaks one is wrong regardless of its details - stop and raise
it rather than working around it.

1. **Arcade first.** Fights decided by reflexes, levels by tactics. A mechanic that makes the player
   stop and compute belongs in a different game.
2. **The commander is a unit, not a cursor.** You stand on the field, you can be killed, you are the
   squad's biggest weapon. No detached god view, no invulnerability.
3. **The squad is the resource.** Troopers won and lost during play. No shop, no loadout screen, no
   economy between levels.
4. **One fight, and you can always see it.** No fog of war, no off-screen management, no second front.
   A level may run wider than the view - the camera follows you, one key pulls it back - but the fight
   is the one you are standing in. (Amended by G0008; a minimap is off-screen management with a nicer
   name and is ruled out there.)
5. **State is visible.** What an entity can do is legible from how it looks. The player never tracks a
   number the game does not show.
6. **Every run is reproducible** (ADR 0004). No wall-clock effects, no unseeded variety, no bug that
   cannot be replayed.

Invariants, input map and open questions: `docs/design/README.md`. Read before any gameplay change -
the `feature` skill has the procedure.

The six are inline rather than on-demand by decision: a contradiction usually slips in during a session
nobody framed as gameplay work, exactly when an on-demand file would not be read.

## How we work

**Agree, then plan, then write.** Non-trivial work: ask about the decisions the request genuinely leaves
open, with the question tool rather than prose. Then present a plan and wait. Small, well-specified
changes just get done.

**Tests first:** (1) one or a few general-level tests saying what the logic should do, broad strokes;
(2) the implementation; (3) back to the tests for the specific cases found while implementing. Step 3
is the point - specifics discovered while implementing beat specifics guessed beforehand. Check each is
a real case, not noise. For a bug fix, start instead with the smallest test that reproduces it.
Conventions: `unit-tests` skill.

**Mateusz commits.** Never `git commit`, `git add`, `git push`. Leave the tree for review. Suggest a
commit message when done: past tense, one line, imperative, what changed rather than how - `Add enemy leap and
player hand strike`. No body unless something cannot be inferred from the diff.

Reading git state is fine, but **always `git --no-optional-locks status --porcelain`** - a bare
`git status` takes `.git/index.lock`, and if delete permission has lapsed it cannot give it back,
which strands a lock that breaks Mateusz's own `git add` until somebody deletes it by hand.

**Ask for delete permission in your first message.** It resets every session; without it Spotless
cannot rewrite a file and nothing can be renamed or removed. Mid-task is when Mateusz is least likely
to be watching and the task stalls on an unanswered prompt. Ask once, up front, whether or not you
expect to need it.

**Name friction when you hit it.** Anything a change on Mateusz's side would fix - a permission, an
allowlist entry, an access grant, a setting - one line at the moment you hit it, then carry on. Do not
silently work around the same obstacle every session. Known ones: `docs/workflow.md`.

**Answer in the fewest words that carry the information.** No preamble, no restating the request, no
summary of what the diff shows. Sacrifice grammar for concision; Mateusz asks when he wants more.
Always worth saying, however short: which invariant or pillar a gameplay change touches, a cause you
diagnosed, a decision you took that he did not, friction you hit. Not summary - the part he cannot read
off the diff.

## Phase

**Alpha phase**: systems built to be built on, with an optional scenario so Mateusz can run it. Take as
many systems in one session as the change genuinely needs, staged so work can stop after any stage.
`docs/design/alpha-phase.md` has the method and the shortcuts still standing.

## Where to look things up

Read the row that matches. On-demand by design - loading all of them costs more attention than it buys.

| File | Read it when |
|---|---|
| `docs/conventions.md` | Writing Java here - banned APIs, style, architecture, nullability, logging. |
| `docs/performance.md` | Writing or reviewing anything in a per-entity loop. |
| `docs/determinism.md` | Touching `sim` or `map` logic, randomness, time or iteration order. |
| `docs/architecture.md` | Adding a system or component, or crossing the sim/client line. |
| `docs/components.md` | Before adding a component - what the 39 existing ones hold and what touches each. |
| `docs/simulation-order.md` | Registering a system, or debugging a one-tick ordering bug. |
| `docs/workflow.md` | Builds, the verification ladder, something broke, environment friction. |
| `docs/glossary.md` | Naming anything in the game domain. Read the whole section, not just the word you had in mind. |
| `docs/design/README.md` | Any gameplay change - invariants, input map, open questions. |
| `docs/design/alpha-phase.md` | **Every session right now** - how a phase session goes, how to add a scenario, what is still scaffolding. |
| `docs/adr/README.md` | Before proposing to change a structural decision. |
| `docs/design/decisions/` | Before re-proposing a gameplay alternative. |

| Skill | Use it before |
|---|---|
| `feature` | Any gameplay change - mechanic, weapon, enemy, level rule, pickup, input binding, progression. Runs before `new-system`. |
| `new-system` | Writing a component, an Ashley system, or any new logic in `sim`. |
| `unit-tests` | Writing, reviewing or refactoring any Java test. |
| `perf-audit` | Reviewing a hot path, or when frame time regresses. |
| `game-patterns` | Inventing an algorithm for level layout, scatter, cameras, pathing, generation or drawing many small things. The shortlist of what to reach for instead. |
