# AFK run: the commander and the sites

You orchestrate an **AFK run** (`docs/workflow.md`, *AFK runs*). Mateusz has handed you the ten tickets in
`.scratch/commander-and-sites/issues/` and is away. Nobody answers questions.

The design is **already grilled and agreed** ([spec.md](spec.md)), so there is no charting phase. You build
the tickets one after another, each on its own branch, and gather them on one integration branch that he
merges when he is back.

## Read first, narrow

1. `CLAUDE.md`. It holds the pillars, the checklist under *Before you say it is done*, and *Your final
   message*.
2. `docs/workflow.md`: *AFK runs*, the verification ladder, the failure protocol.
3. `.scratch/commander-and-sites/map.md` and `spec.md`.
4. Then **only the ticket being built**: its *Read first* list is that stage's reading.

## Ground rules

- **Branches.** Cut `commander-and-sites-integration` from `main`, with its worktree at
  `.claude/worktrees/commander-and-sites-integration`.
  - Each ticket gets a branch `afk/cs-NN-<slug>`, cut from the integration tip, with a worktree of its own
    under `.claude/worktrees/`.
  - Never commit to `main`. Never push.
  - Edit by absolute path inside the ticket's own worktree.
- **Serial, in number order: 01 → 10.** The tickets share `biomes.properties`, `Placement`, `units.properties`,
  `CommanderInput` and the wire, the input map and the glossary. Fanning out would spend the run on merges.
  - A ticket whose blockers did not merge is skipped. Say so in its file and carry on with the next one that
    can go.
- **Decisions.** What the spec and a ticket's *Settled* section say is agreed: build it as written.
  - What a ticket leaves under *Open for the implementer*, and anything else you meet, is yours to decide and
    is **provisional, for review (AFK run)**. Write it in the ticket's `## Answer` and in any G-record,
    invariant row or ADR it touches, with the alternative you turned down and why.
  - **Exception:** a choice that would break one of the seven pillars is not settled. Leave the ticket open
    with the question written into it, and build nothing that depends on it.
- **Numbers** are allocated in `map.md`:
  - G0107-G0115 one per ticket, G0116-G0120 spare;
  - ADR 0024 (ticket 08) and 0025 (ticket 10), 0026 spare.
  - Use the next spare, lowest first, only when a ticket genuinely needs a second record.
- **Models.** Implementers keep your model. Exploration and merging subagents run on `sonnet`.

## Per ticket

1. Set `Status: claimed` in the ticket file, on the integration branch.
2. Cut the ticket's branch and worktree.
3. Run `feature` steps 1-3 against the ticket: read its invariants, check them, and plan the design writing.
4. Run `/mattpocock-skills:implement` with the project skill the ticket names.
   - Tests first, as `CLAUDE.md` describes, keeping step 3 (specifics found while implementing).
   - Follow the ticket's stages; the work may stop after any.
5. Go through `CLAUDE.md`'s checklist until it is green:
   - no allocation in per-entity loops;
   - tunables in `assets/*.properties`;
   - a new system registered with its reason;
   - new components in `Components.ALL` and `docs/components.md`;
   - glossary words used as written;
   - the design written down.
6. Commit in the ticket's worktree: past tense, one line.
7. Merge the integration tip into the ticket branch and run `verify.sh`.
8. Merge the ticket into integration and run `verify.sh` there, with nothing else running.
9. Append `## Answer` to the ticket:
   - what was built;
   - every provisional decision with its alternative;
   - the keys with their values;
   - the scenario or mission code to judge it by.

   Set `Status: resolved` and add a line under *Decisions so far* in `map.md`. Commit that on integration.

A ticket that stays red after `docs/workflow.md`'s failure protocol is left on its branch, unmerged, with a
note in its file saying where it stopped and why. Carry on with the tickets that do not depend on it.

Nothing runs the game: only the desktop runs it, and Mateusz judges the feel. Every ticket leaves a hidden
scenario (`--scenario=<name>`, built by ticket 01) or a printed mission code. Collect them into one list in
your final message.

## Your final message

Follow `CLAUDE.md`'s *Your final message*, plus:
- each ticket: merged, left on its branch (and why), or skipped (and why);
- every provisional decision, one line each, with the alternative;
- the keys added or moved in `assets/*.properties`, with their values;
- every `CLAUDE.md` edit;
- **what to play**, in ticket order: the exact command for each scenario or mission code;
- friction you hit, one line each.
