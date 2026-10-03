# 07 - Odds that keep their own due

Status: resolved

Strength: Worth exploring. Dependency: in-process. Report candidate 8.

## Problem

Six call sites repeat one pattern:

```java
x.due = Odds.spend(modifiers, entity, kind, rng, x.due, chances, dt);
if (!Odds.spent(x.due)) ...
```

at `CollisionSystem:234`, `TrooperAid:78`, `TrooperCrew:84` and `TrooperCombatSystem:177, 205, 260`. Three
of the seven arguments are the same world-wide - `modifiers`, `chances`, and the `NERVE` stream wired in
`Simulation:186-201` - and using the right stream is left to each caller.

The helper is deep; its interface is not: the constant context and the store-back leak to every caller.

## Direction

An odds module built once per world, holding its context and its stream: `odds.roll(entity, kind, due)`.

## Constraint

`ArchitectureTest.systemsHoldNoStateAcrossTicks`: a system may hold a final reference; the due stays on
the component (ADR 0014).

## Tests today

`OddsTest` builds `Modifiers` directly; it would get simpler.

## Comments

**2026-10-03 - done.** `Odds` is one instance per world, built in `Simulation` beside the streams and
holding `Modifiers`, `Chances` and the `NERVE` stream: `odds.roll(unit, kind, due, deltaTime)`, with an
overload for a number in hand (`Taking.biteChance`), and `odds.possible(unit, kind)`. The tick length
stays an argument - every caller already has it, and folding it in would have reshaped three method
signatures in the mind for no gain. All six callers already rolled on `NERVE`, so no run changes.
