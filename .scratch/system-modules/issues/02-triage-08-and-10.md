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
