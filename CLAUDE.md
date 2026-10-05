# Project-W

Top-down 2D arcade shooter. Java 25 + libGDX. Inspired by *Triumph! War 2099* (Darksun Games, 2001):
you command a squad, the action is arcade-fast, the thinking tactical. Our own levels, units and
progression - not a remake.

Author: Mateusz Zebrowski. Package root `pl.mzebrows.pwt`.

## Scope of a change

Default: the **smallest coherent patch** that satisfies the task. Touch the code the task names, add
the abstraction it requires, and keep public names, config and dependencies as they are unless asked.
If the task turns out to need a larger structural change: stop, name the dependency chain, wait.

**Small patch, right shape.** The rule above is about code you *happened to open*. It is not an
argument for the surface version of the code the task makes you **write**. Choose the general shape
when a second user already exists - one named in the request counts, a hypothetical one does not - and
when the general version costs a field, a parameter or a small helper rather than a layer. Say the
second user out loud; if you cannot name it, build the surface version and move on.

Two consequences worth knowing before you reach for it. A number put on a shared definition is a
number every caller must honour, or it is a config key that lies - so it arrives with **one place**
that reads it, and every caller goes through that place. And "zero means off" is how this codebase
says *not for me* (`AiChance`, `RangedDef.off()`, `WeaveDef.off()`), so a new capability usually
belongs on the definition every kind already has rather than on the one kind that asked for it.

**Reuse before you create.** A second way to do what already has one way is a defect. Already here:
`Spawner` (entity kinds), `Defs` (kind definitions), `Pool` (transients), `Rng` (randomness),
`SpatialGrid` (neighbour queries), `ConstraintSolver` (where bodies may be), `Sight` (detection tests),
`FlowField` (routes), `Scatter` (placement), `Maps` (layouts and their validity checks), `Log`,
`ConfigFile` and `ConfigLoader` (a config file and its record), `ConfigBinder` (config records from text),
`GameProperties` (a file off the classpath), `AlphaWorlds` (test fixtures),
`Transport` (links between machines), `LoopbackNetwork` and `ConditionedTransport` (networks for tests
and lag on demand), `NetLog` (the connection log), `WorldCodec` (a world as bytes, a hash, a diff),
`SessionHarness` (a whole online session in one test), `Modifiers` (any number an upgrade may move),
`Grants` (what a pickup's grant does to a taker, and whether it is worth anything to him), `Damage.land`
(the one way a blow is recorded, G0057), `Whole` (the one rounding rule for a whole quantity - hit points,
damage, a count - G0054), `MissionConfig` (what a tier does to the numbers, G0063), `MissionCode` (a mission
as text and back, G0065), `Mission` (a mission's deal, strip and placement from its seed, G0068), `Ticks` (a
duration as whole ticks), `StripFaults` (what a laid strip may never show, a `map` test fixture) - plus a component or
system for most behaviour. `docs/glossary.md` is the cheap check: if the word exists, the thing usually does too.

**Read narrow.** The target module, the classes the task names, their nearest tests. Widen only when a
symbol will not resolve, a dependency's behaviour is unclear, you cross a module boundary, or a test
failure says the picture is incomplete. Scope every recursive search to `sim/src core/src map/src
ui/src net/src docs`.

## Before you say it is done

Each line is a yes/no you can check yourself. A no is not done. What the build already refuses -
the wall clock, unseeded randomness, hash order and `Math`'s transcendentals in `sim`/`map`, a
component holding an `Entity` - is left to `verify.sh`.

- [ ] `.claude/scripts/verify.sh` green. Reading the code is not evidence it compiles.
- [ ] Cloud session: committed and pushed to the `claude/*` branch with `cloud-commit.sh`, tree clean.
      Desktop or Cowork: nothing committed.
- [ ] Inside a per-entity loop: no allocation (`new`, boxing, varargs, capturing lambda, stream,
      iterator) and no log call. Once per system per tick is fine.
- [ ] Entity pairs go through `SpatialGrid`, never an O(N²) loop.
- [ ] New tunables in `assets/sim/*.properties`, `assets/graphic.properties` or
      `assets/network.properties`, never a literal.
- [ ] New domain words in `docs/glossary.md`, and only that word used afterwards.
- [ ] A new pooled component's `reset()` clears **every** field.
- [ ] A new system registered in `Simulation` at a position that breaks none of
      `docs/simulation-order.md`'s constraints, and you said why.
- [ ] A system holds no reference to another system; another entity is named by its `Handle` and
      found with `Handles.resolve` (ADR 0016).
- [ ] A new kind of thing is a constant, a definition and a block in `assets/sim/` - never a new
      class, marker, `spawn` method or branch (G0008) - the `new-kind` skill has the steps.
- [ ] A dodge's side, timing and distance come from its inputs, never the `Rng` (G0012).
- [ ] Nothing you built appears in the **Do not build** column of `docs/adr/README.md` or
      `docs/design/decisions/README.md`.
- [ ] The diff holds only what the task needed.
- [ ] A gameplay change wrote down what it settled: invariant row, decision record, or input-map row.
      If none, say so and why.

## Modules

| Module | What lives there |
|---|---|
| `map` | The ground a level is played on: tile grid, terrain, regions, generation. Depends on nothing at all. |
| `sim` | The deterministic simulation: entities, components, systems, world state. No rendering, assets or I/O. |
| `core` | The libGDX application: rendering, input, assets, screens, config loading. |
| `ui` | What the player is shown and how he moves through it, as data (ADR 0015): screen layout, pages, navigation, the HUD readout, poses. No drawing, no devices, no `net`; reads `sim`, never writes it. |
| `net` | Networking (ADR 0012): the transport port and its Netty adapter, the in-memory test network, the connection log, LAN discovery, and the lockstep session on top - sequencer, peer, wire format. No libGDX; Netty only inside `net.netty`. |
| `lwjgl3` | Desktop launcher. |

`lwjgl3` -> `core` -> {`ui`, `net`} -> `sim` -> `map`, one way; `sim` never sees `net` or `ui`, and `ui`
and `net` never see each other. `ArchitectureTest` fails the build if `sim` reaches for rendering, the
wall clock, unseeded randomness or hash iteration order; `MapArchitectureTest` does the same for `map`,
which may not reach for those *or* for an entity; `NetArchitectureTest` keeps libGDX out of `net`, Netty
inside its adapter and the clock behind `Clock`; `UiArchitectureTest` keeps drawing, devices and `net`
out of `ui`, and stops it writing a component or stepping the simulation. Enforced, not remembered.

**`map` owns the field; `sim` owns what stands on it and what happens over time** (ADR 0009). Pure
function of terrain and geometry -> `map`. Reads a clock, an entity or a component -> `sim`.

## Verify

```
.claude/scripts/verify.sh            # format, compile, lint, test
.claude/scripts/verify.sh :sim:test  # narrower, when iterating
```

The same command in every environment - the desktop, Cowork and the cloud (`docs/workflow.md`,
*Environments*). Failures print in full; the log is `ai/logs/verify.log`. Start at the cheapest level
that can refute the change and escalate on a trigger - the ladder and the protocol for a failure are in
`docs/workflow.md`. Never claim done without a full green run. The game runs only on the desktop, and
Mateusz judges the feel.

## Design pillars

What makes it this game. A feature that breaks one is wrong regardless of its details - stop and raise
it rather than working around it.

1. **Arcade first.** Fights decided by reflexes, levels by tactics. A mechanic that makes the player
   stop and compute belongs in a different game. Two named exceptions, on the same terms: choosing an
   upgrade, and choosing what to take from a crate - he picks the moment, it shows a few cards in the top
   bar (three for a draft, four at most for a crate), and it pauses only a solo game (G0049, G0056).
2. **The commander is a unit, not a cursor.** You stand on the field, you can be killed, you are the
   squad's biggest weapon. No detached god view, no invulnerability.
3. **The squad is the resource.** Troopers won and lost during play. No shop, no loadout screen, no
   economy between levels. Upgrades are earned on the field, never bought, and last the level (G0049).
4. **One fight, and you can always see it.** No fog of war, no off-screen management, no second front.
   A level may run wider than the view - the camera follows you, one key pulls it back - but the fight
   is the one you are standing in. (Amended by G0008; a minimap is off-screen management with a nicer
   name and is ruled out there.)
5. **State is visible.** What an entity can do is legible from how it looks. The player never tracks a
   number the game does not show.
6. **Every run is reproducible** (ADR 0004). No wall-clock effects, no unseeded variety, no bug that
   cannot be replayed.
7. **Online is the same game on every machine** (ADR 0012-0014, G0038 - draft until the online work lands).
   Every machine runs the whole simulation from the same inputs, so a feature is online-ready by being
   deterministic, not by being networked: state changes only inside `Simulation.step` from a tick's
   input, lives where a snapshot carries it, and holds no `Entity` across ticks. Anything per player is
   keyed by slot and sized from capacity - never assumed to be one player, or four.

Invariants (indexed per domain), input map and open questions: `docs/design/README.md`. Read before any
gameplay change - the `feature` skill has the procedure.

The seven are inline rather than on-demand by decision: a contradiction usually slips in during a session
nobody framed as gameplay work, exactly when an on-demand file would not be read.

## Phase

**Alpha**: systems built to be built on. A session takes as many systems as the change genuinely needs
(a leap and the strike that answers it are worth building together), staged so the work can stop after
any stage. Each system goes: design it (`feature`), build it tests first (`new-system`), then **ask**
whether Mateusz wants a scenario to judge it by feel. A stand-in is allowed only while it is listed as
scaffolding in `docs/design/alpha-phase.md`.

## How we work

**Agree, then plan, then write.** Non-trivial work: ask about the decisions the request genuinely leaves
open, with the question tool rather than prose. Then present a plan and wait. Small, well-specified
changes just get done.

**Tests first:** (1) one or a few general-level tests saying what the logic should do, broad strokes;
(2) the implementation; (3) back to the tests for the specific cases found while implementing. Step 3
is the point - specifics discovered while implementing beat specifics guessed beforehand. Check each is
a real case, not noise. For a bug fix, start instead with the smallest test that reproduces it.
Conventions: `unit-tests` skill.

**Mateusz commits - except in a cloud session.**

- **Desktop and Cowork** (Mateusz's machine): leave the tree for review; `git add`, `commit` and `push`
  are his.
- **Cloud** (`CLAUDE_CODE_REMOTE=true`): unpushed work is lost with the container, so Claude **commits
  and pushes its own `claude/*` branch** through `.claude/scripts/cloud-commit.sh`, at each stage the
  work could stop after; no pull request unless asked. Mateusz merges. `docs/workflow.md`, *Cloud
  sessions*.

A commit message is past tense, one line, what changed rather than how - `Added enemy leap and player
hand strike`. No body unless something cannot be inferred from the diff.

Read git state with **`git --no-optional-locks status --porcelain`** (and `diff`), in every
environment - `docs/workflow.md` has why.

**Cowork: ask for delete permission in your first message**, whether or not you expect to need it. It
resets every session there, and without it Spotless cannot rewrite a file.

**Name friction when you hit it.** Anything a change on Mateusz's side would fix - a permission, an
allowlist entry, an access grant, a setting - one line at the moment you hit it, then carry on. Known
ones: `docs/workflow.md`.

**`CLAUDE.md` may be edited when a change needs it** - a new module, a new rule, a row in a table here.
Mateusz has approved that standing.

## Your final message

**The fewest words that carry the information.** No preamble, no restating the request, no summary of
what the diff shows. Sacrifice grammar for concision; Mateusz asks when he wants more. Always say,
however short - it is the part he cannot read off the diff:

- which invariant or pillar a gameplay change touches;
- a cause you diagnosed, a decision you took that he did not, friction you hit;
- for a gameplay change, the keys it added or moved in `assets/sim/*.properties`,
  `assets/graphic.properties` and `assets/network.properties`, with values and a few words on what each
  does to the feel where the name does not say - the handful he would otherwise go and find before
  tuning what he has just played;
- every `CLAUDE.md` edit, one line each;
- on the desktop or in Cowork, a suggested commit message;
- at a stage the work could stop after, whether the next step would go better in a fresh session - another
  subsystem, a new grilling, or a context long with detail the next step does not need - and if so, the
  prompt to start it: standalone, naming the files to read, what is settled, what is open and the skill to
  run.

## Where to look things up

Read the row that matches. On-demand by design - loading all of them costs more attention than it buys.

| File | Read it when |
|---|---|
| `docs/conventions.md` | Writing Java here - banned APIs, style, architecture, nullability, logging. |
| `docs/performance.md` | Writing or reviewing anything in a per-entity loop. |
| `docs/determinism.md` | Touching `sim` or `map` logic, randomness, time or iteration order. |
| `docs/architecture.md` | Adding a system or component - which fenced package it goes in - or crossing the sim/client line. |
| `docs/components.md` | Before adding a component - what the existing ones hold and what touches each. |
| `docs/simulation-order.md` | Registering a system, or debugging a one-tick ordering bug. |
| `docs/workflow.md` | Builds, the verification ladder, something broke, which environment you are in, environment friction. |
| `docs/glossary.md` | Naming anything in the game domain. Read the whole section, not just the word you had in mind. |
| `docs/design/README.md` | Any gameplay change - input map, open questions, and the index of `docs/design/invariants/`: read the files the change touches. |
| `docs/design/alpha-phase.md` | Adding a scenario; putting in or taking out a stand-in; the harness keys; playing two copies online. |
| `docs/adr/README.md` | Before proposing to change a structural decision. |
| `docs/backlog.md` | Before proposing a refactor or perf fix - it may already be deferred, with the trigger that brings it back. Add what you defer. |
| `docs/design/decisions/` | Before re-proposing a gameplay alternative. |
| `docs/ui/menu_layout_agent.md` | Adding or changing any page, modal or HUD slot - ids, entries, transitions, input per context. |

## Agent skills

The engineering skills from `mattpocock-skills` read three files: `docs/agents/issue-tracker.md` (issues
are local markdown under `.scratch/<feature>/`), `docs/agents/triage-labels.md` (the five default labels,
as `Status:` lines) and `docs/agents/domain.md` (single context; the glossary is `docs/glossary.md`, never
a root `CONTEXT.md`). Where one of them and a skill of this repository cover the same ground, this
repository's wins.
