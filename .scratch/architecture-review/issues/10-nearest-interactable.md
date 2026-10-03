# 10 - One nearest-interactable query for the action key

Status: done

Strength: Speculative. Dependency: in-process. Report candidate 11.

## Problem

`Aid.nearest`, `Crews.nearestFree` and `Crates.nearestOpenable` are one loop with three filters. Three
`gapTo` aliases are used interchangeably - `ActionSystem` calls `Crates.gapTo(commander, subject)` on a
casualty. The tie order between offers is hand-written at `ActionSystem:96-113`, so each new interactable
edits it.

## Direction

One query ranks what the action key could do for this commander; the kinds become filters inside it
(G0021: one key, and the game chooses).

## Tests today

`ActionTest`.

## Comments

- 2026-10-03: Gates `.scratch/system-modules/` - decide this before `sim.system` is split into fenced
  packages (its ticket 02), so the helpers are deepened once rather than moved and then changed.
- 2026-10-03: Taken narrow (system-modules 02). `Reach.nearest(candidates, man, reach, ActionKind)` is the
  one scan, the action kind its filter; `Reach.gap` the one surface-to-surface distance. `Aid.nearest`,
  `Aid.gapBetween`, `Crews.nearestFree`, `Crews.gapTo`, `Crates.nearestOpenable` and `Crates.gapTo` are
  gone. `ActionSystem`'s tie order stays hand-written; the ranking query is in `docs/backlog.md`, brought
  back by a fourth interactable.
