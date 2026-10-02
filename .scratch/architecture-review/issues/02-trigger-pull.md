# 02 - Deepen the trigger pull

Status: done

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

### 2026-10-02 - grilled and built

Settled with Mateusz:

- **Interface.** One `Guns` per simulation holds the spawner, the modifiers, the shared `SCATTER` stream and
  the gap. A shooter asks `guns.ready(firer)` and calls `guns.pull(firer, aimX, aimY, credit)`; an alien that
  fires faster up close passes its own cooldown. Position, body, side and gun are read off the firer. Credit
  stays an argument because a turret's is its crew's (G0044). `ready` stays separate because an alien draws
  its aim-cone roll only once the gun is ready, and folding it in would move the roll and change replays.
- **One gap**, 0.1, as `sim.muzzleGap`; grenades leave by it too (`Throws` is now an instance as well).
  The commander's shots and throws move 0.05 further out.
- **The barrage holds a `Weapon`**: each pattern's gun is put in the boss's hands through
  `Spawner.applyWeapon`, and every shot goes through `Guns.volley` - same muzzle, same gap. The pattern keeps
  the time, so it pays no cooldown or heat. Alien-side upgrades now scale its damage, as they do every other
  alien's gun. The `WeaponDef` overload of `Spawner.spawnProjectile` is gone; the test hook in `Simulation`
  goes through the one path. A strain may not have both a barrage and a ranged gun (`UnitDef` refuses it):
  they would share one pair of hands.

