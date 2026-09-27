# Prompt: critical review of one Project-W module

Copy everything below the line into a new session. Replace `<MODULE>` with `map`, `net`, `ui` or `core`.
The prompt was built from the `sim` review of 2026-09-26/27 (`Claude outputs/sim-review.md`). Each check
below is something that review actually found in `sim`, stated so it can be looked for anywhere.

---

Act as a senior 2D-engine and gamedev engineer doing a **critical review of the `<MODULE>` module**, with
an eye for junior and mid-level mistakes. We are in the alpha phase and can change anything. Four domains:
**performance, maintainability/extensibility, LLM-workflow efficiency, and the architecture decisions
themselves** (propose better alternatives where they are genuinely better).

## How to run it

1. **Stage 1: verify, propose, plan. No production code.** Read `CLAUDE.md`, then only the rows of its
   lookup table that apply. Always read `docs/backlog.md`, so nothing already deferred is re-proposed as
   new. Back every finding with evidence: a throwaway probe test (name it `Zz*ProbeTest`, never commit
   it), a measurement, or a `file:line`. Write `Claude outputs/<MODULE>-review.md` with these sections:
   verified defects (evidence and severity), then performance, maintainability, architecture and
   workflow tables, then a staged plan where each stage ships on its own, then the decisions you need
   from me. Ask those decisions with the question tool, then stop.
2. **Later stages, one per "go to next stage".** Tests first. Each stage is committed and pushed
   through `.claude/scripts/cloud-commit.sh`. Each stage adds a progress table to the review file
   (item, what was done, evidence).
3. **Anything we decide to defer goes into `docs/backlog.md`**, with why it waited and what brings it
   back.

## Proof, not assertion. Use these methods; they worked in `sim`.

- **Refactors must be behaviour-identical, and shown to be.** Before touching code, record a fingerprint
  of the output. After the change, the fingerprint must be identical.
  - `sim` / `map`: `WorldCodec.hash` over every scenario (`Scenarios.all()` with `ScriptedPlay.input`)
    plus a crowd. If the snapshot layout changes, fingerprint positions and health instead.
  - `map`: hash the generated tile grids for a range of seeds.
  - `net`: `SessionHarness` or `LoopbackNetwork` transcripts.
  - `ui`: the output of `ScreenLayout.solve` and of the readout writer.
- **A comment-only change is proven by stripping comments and whitespace and comparing** against the
  previous commit, file by file.
- **A new rule or guard is shown to bite.** Break the code on purpose, watch the test fail, restore it.
- **Performance claims come with before-and-after numbers.** `PWT_BENCH=1` `SimulationBenchmarkTest`
  checks time, O(N²) growth and per-entity allocation. For a hot path outside `sim`, build the equivalent
  (opt-in, machine-independent checks preferred) and list it in `docs/workflow.md`.
- **Determinism:** `PWT_FUZZ=1` `DeterminismFuzzTest` after anything touching `sim` or `map` state.
- **Never claim done without a green `.claude/scripts/verify.sh`.**

## Defect classes found in `sim`. Look for each one in `<MODULE>`.

1. **Two sources of truth for a tunable.** In `sim`, the tests ran different numbers from the shipped
   `game.properties` (21 values), because the Java defaults and the file were edited separately. The fix:
   `ConfigBinder` binds records from the file by component name, the file is compiled in
   (`generateShippedConfig`), and a strict test rejects unknown or missing keys. **`graphic.properties`
   (`GraphicConfig`) and `network.properties` (`NetConfigLoader` / `NetConfig`) still use hand-written
   loaders**: check them for drift and for literal defaults first.
2. **Time as accumulated float `dt`.** In `sim`, 71 durations ran one tick long because a float was
   subtracted down to zero. The fix: convert once, where a duration enters (`Ticks.of`), then count
   whole ticks. In `net`, look for timeouts, heartbeats and lag windows. Time belongs behind `Clock`; is
   it integer-exact?
3. **A reference kept to something that gets recycled.** Pooled `Entity` objects came back as another
   entity (`Strike.owner` followed a stranger). The fix: `Handle` and `Handles.resolve` (ADR 0016). Look
   for `ui` or `core` code that holds an `Entity` across frames, and `net` code that holds a peer or
   channel object past its lifetime.
4. **Hidden O(N²) and linear scans.** In `sim`: avoidance checked every body (28.9 ms at 800 entities,
   down to 4 ms), and perception scanned every alien to find about ten targets. Look for loops nested
   over entities, draw calls, peers or messages, and prefer `SpatialGrid` or a per-tick index.
5. **Allocation or logging in a per-item loop.** Check the renderer's draw loop, the HUD readout (string
   building every frame), and per-packet encode/decode in `net`. Apply `docs/performance.md` and the
   `perf-audit` skill.
6. **Latent capacity or size assumptions.** The grid cell size silently left out two body kinds, so
   contacts would drop without anything failing. The fix: derive the size from every registry, and throw
   loudly on a violation. Look for fixed buffer sizes, `Players.MAX` written as a literal, and wire
   fields that could overflow.
7. **Sampling only at discrete moments.** Fast shots tunnelled through targets; the fix was a swept test.
   Look for timeouts checked only on a message arriving, input edges lost between frames, and hit-tests
   against a position from the wrong frame.
8. **Rules reading presentation state.** Perception read `Appearance.facing`; the fix was a `Facing`
   gameplay component written by its own system. Check that `core` and `ui` never feed back into rules,
   and that nothing a rule needs lives only in a drawing class.
9. **Order-dependent pipelines documented only in prose.** In `sim`, system order is now a test:
   `SystemOrderTest` holds each constraint as data together with the doc's reason. Look for the frame
   order in `docs/ui/ui_and_graphics_action_plan.md` §3.3, the session phases in `net`, and the render
   passes in `core`.
10. **God classes.** `TrooperMindSystem` went from 888 to 124 lines: the cascade stayed and each behaviour
    became its own class, with the code moved verbatim and the world hash unchanged. In `net`, the
    candidates are `Sequencer` (817 lines), `PeerSession` (782) and `NettyTransport` (661). Aim for
    about 400 lines. Split along responsibilities, never rewrite during a split.
11. **Enforcement that is narrower than the rule.** `systemsHoldNoStateAcrossTicks` only covered some
    classes until it was widened to the whole package. Check each `*ArchitectureTest` against the rule it
    claims to enforce.
12. **Stale documentation.** Found in `sim`: `int` fields documented "in seconds" that count ticks; docs
    describing references that had become handles; a class doc describing code that had moved; a
    paragraph repeated word for word. Treat documented units and ownership claims as suspects.
13. **Comment weight.** Comments were 39% of `sim` lines. Apply the `conventions.md` rule: contract,
    invariant and a G/ADR link only; history goes to the record and git; tuning rationale goes next to the
    number in the `.properties` file. `sim` went from 8,738 to 6,325 comment lines, with an identical
    stripped-code check on every commit.

## Module notes, so you start in the right place

- **`map`**: must stay pure (`MapArchitectureTest`). Determinism means `StrictMath`, seeded `Rng`, no
  hash iteration. Look at the cost of flow-field rebuilds, `Scatter` and `Maps` validity checks, and at
  whether generation is covered by a per-seed golden hash.
- **`net`**: lockstep (ADR 0012-0014), with the clock behind `Clock` and Netty only in `net.netty`
  (`NetArchitectureTest`). Look at allocation per packet, a version tag in the wire format, the timeout
  arithmetic, and what a desync report gives you (`WorldCodec.differences`). Test through `SessionHarness` and
  `ConditionedTransport` rather than new fakes.
- **`ui`**: data only (ADR 0015, `UiArchitectureTest`). Reads `sim`, never writes it. Look at
  per-frame allocation in readouts and layout, the correctness of the layout solver at odd resolutions,
  and whether every page and slot in `docs/ui/menu_layout_agent.md` has a test.
- **`core`**: libGDX. Look at the draw loop (per-entity allocation, batch and shape-renderer switching,
  texture binds), `dispose()` leaks, config loaders (item 1), input edges, and anything that could let
  `core` write the world (`Simulation.engine()` is public, as noted in the backlog).

## Rules that still apply

The smallest coherent patch for each stage. Reuse what exists (`CLAUDE.md` lists it). Name every
`CLAUDE.md` edit in the summary, one line each. Name the friction you hit. List any keys added or moved in
`game.properties`, `graphic.properties` or `network.properties`, with values. Answer in the fewest words
that carry the information.
