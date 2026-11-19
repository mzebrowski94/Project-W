# AFK run: online play through a relay

You orchestrate an **AFK run** (`docs/workflow.md`, *AFK runs*). Mateusz has handed you the effort charted in
`.scratch/online-relay/map.md` and is away. Nobody answers questions. You finish charting the map, then build
it in stages, each one able to stop on its own. Everything lands on one integration branch that he merges
when he is back.

## Read first, narrow

`CLAUDE.md` (it holds: the pillars, the checklist under *Before you say it is done*, *Your final message*),
`docs/workflow.md` (*AFK runs*, the verification ladder, the failure protocol), `.scratch/online-relay/map.md`
and every ticket under `.scratch/online-relay/issues/`, then `research/` in that folder. Read ADR 0012, ADR 0018,
G0038 (as amended), G0101 and G0102. Read nothing else until a stage needs it.

## Ground rules

- **Branches.** Cut `online-relay-integration` from `main`, with its worktree at
  `.claude/worktrees/online-relay-integration`. Each stage gets a branch cut from the integration tip and a
  worktree of its own under `.claude/worktrees/`. Never commit to `main`. Never push. Edit by absolute path
  inside the stage's own worktree.
- **Decisions are yours, and provisional.** Each one you take is written down as
  *provisional, for review (AFK run)*, along with the alternative you turned down and why. Where it lives:
  - the ticket's `## Answer`,
  - the map's Decisions-so-far line,
  - any ADR, G-record or invariant row it touches.

  **Exception:** a question that would break one of the seven pillars is not settled. Leave that ticket open
  with the question written into it, and build nothing that depends on it.
- **What is already agreed, and is not provisional:** tickets 01-04, G0101 and G0102, and the G0038
  amendment text in ticket 04's Answer. Build them as written.
- **Numbers.** These are allocated to this run, so no branch has to guess:
  - ADR **0023** is the topology decision, amending ADR 0012; **0024** is spare.
  - G-records: **G0103** is the rooms amendment of G0038, using ticket 04's agreed text word for word.
    **G0104** is a new host when the host leaves. **G0105** is coming back after a dropped link.
    **G0106-G0109** are spare, handed out one block per stage.
- **Models.** Implementers keep your model. Exploration and merging subagents run on `sonnet`.

## Code Mode (experimental) - how to use it here

- **Use it for** batching reads and searches, filtering their output before it reaches your context, and the
  same mechanical edit across many files. Keep each script short and its output small. Scope every search to
  `sim/src core/src map/src ui/src net/src docs` (plus the relay module once it exists).
- **Keep these as single, visible tool calls, never inside a script:**
  - `.claude/scripts/verify.sh`, because code stops after ten minutes, and a full run is the evidence of
    done.
  - Every git write (`commit`, `merge`, `worktree add`). The auto-mode classifier reviews the script, not
    the values it computes at run time.
- **Smoke test, first thing.** Start one subagent with `isolation: "worktree"` that creates a file, commits
  it on its own branch, and reports back. If that fails from Code Mode, don't fan out: run the stages
  yourself, one after another, each on its own branch and worktree. Say which way you went in the final
  message.

## Phase 1 - finish the map (decisions only, no code)

Work the frontier in the map's order. Wayfinder's rules hold, except that you speak for both sides of each
grilling and mark the outcome provisional.

1. **Where the sequencer runs.** Skip this if it is already resolved when you start. Otherwise weigh plain
   relay, sequencer on the relay, and direct paths with a relay fallback against tickets 01-03's findings.
   - Work out the input delay for a host and for a joiner in the same region, from ADR 0012's pacing.
   - Say what the sequencer would need from the host, and where it would get it.
   - Say what the relay's build must match: does every game build force a relay deploy?
   - Say whether a shared container can seal ticks on time (`sealLateMostMs`).

   ADR 0012's **Do not build** still holds: no mesh, no state replication, no waiting for a late input.
   Write the result as ADR 0023.
2. **A new host when the host leaves**, then **Coming back after a dropped link**. Write the results as
   G0104 and G0105.
3. **Graduate the fog.** Settle abuse limits, which builds the relay serves, the client side (Host, Join, the
   host overlay, the relay address in `network.properties`, all under G0101) and testing, either as tickets
   or straight into the spec.
4. **Write the destination.** Write `.scratch/online-relay/spec.md` and G0103. Write the glossary rows for
   any new word, and use only that word afterwards. Cut the build into stages: each is one session's worth,
   stands on its own, and lists its tests and its `verify.sh` gate.
5. Commit Phase 1 on the integration branch as its own stage (`Charted online play through a relay to a
   spec`). **The run may stop here and still be worth something.**

## Phase 2 - build, stage by stage

Follow the spec's stages. A likely order, which the spec overrides:
- G0102 (a mission's length, held in its code);
- G0101 (the briefing, on the LAN);
- the relay module, with the room's door: codes, password, refusals, tested in-process over
  `LoopbackNetwork`;
- traffic through the relay, in the shape ADR 0023 picked;
- the client's Host and Join through the relay;
- coming back;
- a new host;
- abuse limits.

Topology-free stages go first, so that a wrong provisional ADR 0023 costs the least.

For every stage:
- `implement` with `new-system` (or `new-kind`). Tests first, as `CLAUDE.md` describes, with step 3 kept:
  specifics found while implementing.
- `SessionHarness`, `LoopbackNetwork` and `ConditionedTransport` stand in for the internet. A relay test
  drives the module in-process.
- `CLAUDE.md`'s checklist goes green before the stage reports: no allocation in per-entity loops, tunables in
  `assets/*.properties`, a new system registered with its reason, the relay module fenced by an architecture
  test like the `net` ones, and the module table in `CLAUDE.md` updated.
- Commit in the stage's worktree (past tense, one line). Merge the integration tip into the stage branch,
  run `verify.sh`, then merge the stage into integration and run `verify.sh` there, with nothing else
  running at the same time.
- A stage that stays red after `docs/workflow.md`'s failure protocol is left on its branch, unmerged, with a
  note in its ticket saying where it stopped and why. Carry on with the stages that don't depend on it.

Nothing runs the game: only the desktop runs it, and Mateusz judges the feel. Leave him a short list of what
to play: two copies through a local relay, as `docs/design/alpha-phase.md` describes playing two copies
online.

## Your final message

Follow `CLAUDE.md`'s *Your final message*, plus:
- every provisional decision, one line each, with the alternative;
- each stage: merged, left on its branch, or not started;
- the keys added in `assets/*.properties`, with their values;
- each `CLAUDE.md` edit;
- whether Code Mode ran the fan-out or you ran the stages yourself, and any friction it caused.
