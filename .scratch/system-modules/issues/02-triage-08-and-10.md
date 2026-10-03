# 02 - Decide architecture-review 08 and 10 before anything moves

Type: grilling

## Question

`.scratch/architecture-review/issues/08-life-stage.md` (one question for a man's life stage) and
`10-nearest-interactable.md` (one query in place of `Aid.nearest`, `Crews.nearestFree`,
`Crates.nearestOpenable`) change which helpers exist in `sim.system`. Take each, or close it?

## Why now

Ticket 03 measures who uses which helper to choose the split. Deepening after the move changes the same
code twice, and a merged query is a candidate for `sim.system.query` that does not exist yet.

## Read first

Both tickets, `.scratch/system-modules/map.md`. Skill: `grilling`.

## Settled

Taken ones are built before 03 runs. 03-06 and 09 of `architecture-review` stay outside this effort.

## Done when

Each of 08 and 10 has a status (`ready-for-agent`, `ready-for-human` or `wontfix`), taken ones are built,
and the outcome is in `map.md`.

## Answer

Done 2026-10-03, asked with the question tool.

- **08 taken, with its ArchUnit rule.** `Life` (`sim` root, public): `down`, `casualty`, `dying`. The 36
  raw reads of `Vitality.begun` and `Dying.begun` and the four wrappers go through it;
  `ArchitectureTest.onlyLifeReadsWhetherAUnitIsDownOrDying` refuses a new one. For 03: `Life` is a
  `query` member already, in the root because `Players` and `ui` need it.
- **10 taken narrow.** `Reach` (package-private in `sim.system`): one nearest scan with `ActionKind` as the
  filter, one `gap`. The ranking query is deferred to `docs/backlog.md` (trigger: a fourth interactable).
  For 03: `Reach` is a `query` candidate, used by `ActionSystem`, `AidSystem`, `CrateSystem`,
  `TrooperAid` and `TrooperCrew`.
