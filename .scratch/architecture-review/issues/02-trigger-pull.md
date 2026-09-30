# 02 - Deepen the trigger pull

Status: needs-triage

Strength: Strong. Dependency: in-process. Report candidate 3.

## Problem

`Guns.volley` (`system/Guns.java`, 97 lines) takes 14 parameters for about 12 lines of body. Its four
callers repeat one sequence - `ready`, then `volley` with position, radius, side, credit and scratch (all
readable from the firer), then `spend`:

- `CommanderControlSystem:292-322`
- `EnemyRangedSystem:194-222`
- `TurretSystem:233-254`
- `TrooperCombatSystem:331-361`

`MUZZLE_GAP` is declared six times with two values: 0.05 in `CommanderControlSystem` and `Throws`; 0.1 in
`BarrageSystem`, `EnemyRangedSystem`, `TrooperCombatSystem` and `TurretSystem` - a literal tunable, against
CLAUDE.md's checklist.

`Muzzle` says every gun goes through it, but `BarrageSystem:198-208` works out its own clearance, ignores
`muzzleOffset` and `Guns`, and uses a second `Spawner.spawnProjectile` overload (`Spawner:863`).

## Direction

The **volley** module reads the firer itself and owns ready, heat, muzzle and spend; the boss's barrage
goes through it. One muzzle gap, in `assets/sim/`.

## Deletion test

Remove `Guns` and the cooldown, heat and self-slow logic reappears in four or five places - it earns its
keep, but its interface leaks what it should hide.

## Tests today

`VolleyTest`, `OverheatTest`, through a whole `Simulation`.

## Comments
