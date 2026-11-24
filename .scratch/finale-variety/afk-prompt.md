# AFK run: finale variety, the recall and the squad's assault

You orchestrate an **AFK run** (`docs/workflow.md`, *AFK runs*). Mateusz has handed you the five tickets in
`.scratch/finale-variety/issues/` and is away. Nobody answers questions.

The design is **already grilled and agreed** ([spec.md](spec.md), and G0120, G0121 and G0122 in
`docs/design/decisions/`), so there is no charting phase. You build the tickets one after another, each on its own
branch, and gather them on one integration branch that he merges when he is back.

## Before anything

- `main` must hold the grilling's records: `docs/design/decisions/G0120-*.md`, `G0121-*.md`, `G0122-*.md` and this
  folder. If they are missing, stop and say so - there is nothing agreed to build.
- `main` is red on entry (`MissionSweepTest`, the rift's jaguar nests). Ticket 01 fixes that; until it merges, "green"
  for anything else means green but for that one fault.

## Read first, narrow

1. `CLAUDE.md`. It holds the pillars, the checklist under *Before you say it is done*, and *Your final message*.
2. `docs/workflow.md`: *AFK runs*, the verification ladder, the failure protocol.
3. `.scratch/finale-variety/map.md` and `spec.md`.
4. Then **only the ticket being built**: its *Read first* list is that stage's reading.

## Ground rules

- **Branches.** Cut `finale-variety-integration` from `main`, with its worktree at
  `.claude/worktrees/finale-variety-integration`.
  - Each ticket gets a branch `afk/fv-NN-<slug>`, cut from the integration tip, with a worktree of its own under
    `.claude/worktrees/`.
  - Never commit to `main`. Never push.
  - Edit by absolute path inside the ticket's own worktree.
- **Serial, in number order: 01 → 05.** 03 and 04 share `FinaleDef`, `finales.properties`, `Mission` and the sweep; 02
  and 05 share the squad's systems; every ticket needs 01's green sweep. Fanning out would spend the run on merges.
  - A ticket whose blockers did not merge is skipped. Say so in its file and carry on with the next one that can go.
    If 01 stays red, still build 02-05, each judged green but for the rift fault, and say so.
- **Decisions.** What the spec, the three records and a ticket's *Settled* section say is agreed: build it as written.
  - What a ticket leaves under *Open for the implementer*, and anything else you meet, is yours to decide and is
    **provisional, for review (AFK run)**. Write it in the ticket's `## Answer` and in the G-record, invariant row or
    ADR it touches, with the alternative you turned down and why.
  - **Exception:** a choice that would break one of the seven pillars is not settled. Leave the ticket open with the
    question written into it, and build nothing that depends on it.
  - **Never reopen what the grilling closed**: hand-made layouts, sockets, unmirrored placement, turrets in the keep
    behind the post, coming back in the trooper's place, a second lair rising. They are in the records' *Do not build*
    or the open questions.
- **Records.** G0120, G0121 and G0122 exist and are **accepted**, not provisional: do not renumber or rewrite them. Fill
  in their *Enforced by* with the tests that now hold them; anything a ticket decides beyond them goes in an
  `## Amended by` section or a spare record, marked provisional.
- **Numbers** are allocated in `map.md`: G0123-G0126 spare, ADR 0026 spare. Use the next spare, lowest first, only when
  a ticket genuinely needs a new record.
- **Models.** Implementers keep your model. Exploration and merging subagents run on `sonnet`.

## Per ticket

1. Set `Status: claimed` in the ticket file, on the integration branch.
2. Cut the ticket's branch and worktree.
3. Run `feature` steps 1-3 against the ticket: read its invariants, check them, and plan the design writing.
4. Run `/mattpocock-skills:implement` with the project skill the ticket names (01: `diagnosing-bugs` instead).
   - Tests first, as `CLAUDE.md` describes, keeping step 3 (specifics found while implementing). For 01, the smallest
     test that reproduces the fault comes first.
   - Follow the ticket's stages; the work may stop after any.
5. Go through `CLAUDE.md`'s checklist until it is green:
   - no allocation in per-entity loops;
   - tunables in `assets/*.properties`;
   - a new system registered with its reason;
   - new components in `Components.ALL` and `docs/components.md`;
   - glossary words used as written (*skeleton*, *lee*, *role*, *recall*);
   - the design written down: *Enforced by*, invariant rows promoted or replaced.
6. Commit in the ticket's worktree: past tense, one line.
7. Merge the integration tip into the ticket branch and run `verify.sh`.
8. Merge the ticket into integration and run `verify.sh` there, with nothing else running.
9. Append `## Answer` to the ticket:
   - what was built;
   - every provisional decision with its alternative;
   - the keys with their values;
   - the scenario or mission code to judge it by.

   Set `Status: resolved` and add a line under *Decisions so far* in `map.md`. Commit that on integration.

A ticket that stays red after `docs/workflow.md`'s failure protocol is left on its branch, unmerged, with a note in its
file saying where it stopped and why. Carry on with the tickets that do not depend on it.

Nothing runs the game: only the desktop runs it, and Mateusz judges the feel. Every ticket leaves a hidden scenario
(`--scenario=<name>`) or a printed mission code - for 03-05, at least one ending on each finale kind. Ticket 03 re-deals
every finale, so print the codes from the integration tip at the end, not from each branch. Collect them into one
list in your final message.

## At the end

Review the whole integration branch against the three records - each **Constraint** bullet built or named as not, each
**Do not build** item absent - and against `CLAUDE.md`'s checklist. Fix on integration what the review finds, `verify.sh`
green.

## Your final message

Follow `CLAUDE.md`'s *Your final message*, plus:
- each ticket: merged, left on its branch (and why), or skipped (and why);
- 01's diagnosed cause, in one line;
- every provisional decision, one line each, with the alternative;
- the keys added, moved or deleted in `assets/*.properties`, with their values - the finale's rule ranges, the roles,
  the keep's pool, the recall's look;
- every `CLAUDE.md` edit;
- **what to play**, in ticket order: the exact command for each scenario or mission code;
- friction you hit, one line each.
