# AFK run: plasma cannon, Firepower, Engineer

You orchestrate an **AFK run** (`docs/workflow.md`, *AFK runs*). Mateusz has handed you the four tickets in
`.scratch/plasma-firepower-engineer/issues/` and is away. Nobody answers questions.

The design is **already grilled and agreed** ([spec.md](spec.md), and G0127, G0128 and G0129 in
`docs/design/decisions/`), so there is no charting phase. You build the tickets one after another, each on its own
branch, and gather them on one integration branch that he merges when he is back.

## Before anything

- `main` must hold the grilling's records: `docs/design/decisions/G0127-*.md`, `G0128-*.md`, `G0129-*.md`, the glossary
  rows *sentry*, *plasma cannon*, *orb*, *power*, and this folder. If they are missing, stop and say so.
- **Ignore failing "rift" biome seed tests** (the mission sweep's rift seeds): they will be corrected later, outside
  this run. Do not fix them; judge every ticket "green but for the rift seeds".
- Otherwise `main` should be green. If something else is red, say where, and judge every ticket "green but for that
  fault".
- The finale-variety run may be running or merged. Its numbers (G0123-G0126, ADR 0026) are not yours.

## Read first, narrow

1. `CLAUDE.md`: the pillars, the checklist under *Before you say it is done*, *Your final message*.
2. `docs/workflow.md`: *AFK runs*, the verification ladder, the failure protocol.
3. `.scratch/plasma-firepower-engineer/map.md` and `spec.md`.
4. Then **only the ticket being built**: its *Read first* list is that stage's reading.

## Ground rules

- **Branches.** Cut `pfe-integration` from `main`, its worktree at `.claude/worktrees/pfe-integration`.
  - Each ticket gets a branch `afk/pfe-NN-<slug>`, cut from the integration tip, with a worktree of its own under
    `.claude/worktrees/`.
  - Never commit to `main`. Never push.
  - Edit by absolute path inside the ticket's own worktree.
- **Serial, in number order: 01 → 04.** 01 and 02 share the weapon, upgrade and pickup config and the crate/upgrade
  tests; 04 needs 03.
  - A ticket whose blockers did not merge is skipped: say so in its file and carry on. 02 does not need 01's
    behaviour, only its files, so if 01 is left red, still build 02 from the integration tip. 04 is skipped if 03 did
    not merge.
- **Decisions.** What the spec, the three records and a ticket's *Settled* section say is agreed: build it as written.
  - What a ticket leaves under *Open for the implementer*, and anything else you meet, is yours to decide and is
    **provisional, for review (AFK run)**. Write it in the ticket's `## Answer` and in the G-record, invariant row or
    ADR it touches, with the alternative you turned down and why.
  - **Exception:** a choice that would break one of the seven pillars is not settled. Leave the ticket open with the
    question written into it, and build nothing that depends on it.
  - **Never reopen what the grilling closed** - the records' *Do not build*: a by-type effect form, a stocked flamer at
    any rate, an instant ray, one orb out at a time, beams that hurt our men, a sentry a man can get on or haul, a sentry
    count or recharge of its own, a thrown sentry, a cap on standing sentries. And never call the sentry a turret or a
    mini-turret.
- **Records.** G0127-G0129 are **accepted**: do not renumber or rewrite them. Fill in their *Enforced by* with the tests
  that now hold them; what a ticket decides beyond them goes in an `## Amended by` section or a spare record, marked
  provisional. Add G0127 as *Amended by* on G0056 and G0110.
- **Numbers**: G0130-G0131 spare, ADR 0027 spare, lowest first, only when genuinely needed.
- **Models.** Implementers keep your model. Exploration and merging subagents run on `sonnet`.

## Per ticket

1. Set `Status: claimed` in the ticket file, on the integration branch.
2. Cut the ticket's branch and worktree.
3. Run `feature` steps 1-3 against the ticket: read its invariants, check them, plan the design writing.
4. Run `/mattpocock-skills:implement` with the project skill the ticket names.
   - Tests first, as `CLAUDE.md` describes, keeping step 3 (specifics found while implementing).
5. Go through `CLAUDE.md`'s checklist until it is green:
   - no allocation in per-entity loops (the orb's beam pick and the sentry's aim above all), pairs through
     `SpatialGrid`;
   - tunables in `assets/*.properties`;
   - a new system registered with its reason; new components in `Components.ALL` and `docs/components.md`, `reset()`
     clearing every field;
   - a new kind is a constant, a definition and a block (G0008);
   - glossary words used as written (*sentry*, *plasma cannon*, *orb*, *power*);
   - the design written down: *Enforced by*, invariant rows.
6. Commit in the ticket's worktree: past tense, one line.
7. Merge the integration tip into the ticket branch and run `verify.sh`.
8. Merge the ticket into integration and run `verify.sh` there, with nothing else running.
9. Append `## Answer` to the ticket: what was built; every provisional decision with its alternative; the keys with
   their values; the scenario to judge it by. Set `Status: resolved` and add a line under *Decisions so far* in
   `map.md`. Commit that on integration.

A ticket that stays red after `docs/workflow.md`'s failure protocol is left on its branch, unmerged, with a note in its
file saying where it stopped and why. Carry on with the tickets that do not depend on it.

Nothing runs the game: only the desktop runs it, and Mateusz judges the feel. Every ticket leaves a hidden scenario
(`--scenario=<name>`).

## At the end

Review the whole integration branch against the three records - each **Constraint** bullet built or named as not, each
**Do not build** item absent - and against `CLAUDE.md`'s checklist. Fix on integration what the review finds,
`verify.sh` green.

## Your final message

Follow `CLAUDE.md`'s *Your final message*, plus:
- each ticket: merged, left on its branch (and why), or skipped (and why);
- every provisional decision, one line each, with the alternative;
- the keys added, moved or deleted in `assets/*.properties`, with their values - Firepower's effects, the plasma
  cannon's gun, orb, beam and power, the sentry's life, reach, gun, blast and stun, Engineer's ranks;
- every `CLAUDE.md` edit;
- **what to play**, in ticket order: the exact command for each scenario;
- friction you hit, one line each.
