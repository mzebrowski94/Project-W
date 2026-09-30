# 10 - One nearest-interactable query for the action key

Status: needs-triage

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
