# 08 - One question for a man's life stage

Status: done

Strength: Worth exploring. Dependency: in-process. Report candidate 9.

## Problem

Whether a man is standing, a **casualty** or in his throes is asked by hand in 28 places across 20
system files, through two booleans both named `begun`: `Vitality.begun` means down, `Dying.begun` means in
throes. The same question has four differently named wrappers: `Drivers.held` and `Drivers.down`,
`Players.down:78`, `CollisionSystem.inert:260`, `DamageSystem.standing:250`.

`TrooperAid.nearestCasualty:113-135` duplicates the filter in `Aid.nearest:41-62`.

## Direction

One query answers the stage; the raw flags stop being read outside it.

## Tests today

`CasualtyTest`, `DeathThroesTest`, through a whole world.

## Comments

- 2026-10-03: Gates `.scratch/system-modules/` - decide this before `sim.system` is split into fenced
  packages (its ticket 02), so the helpers are deepened once rather than moved and then changed.
- 2026-10-03: Taken and built (system-modules 02). `Life` in the `sim` root answers `down`, `casualty`
  and `dying`; `Players.down`, `Drivers.down`, `DamageSystem.standing` and `GripSystem.dying` are gone,
  `CollisionSystem.inert` and `Drivers.held` ask `Life`, and `Aid`'s and `TrooperAid`'s filters are one
  call to `Life.casualty`. `ArchitectureTest.onlyLifeReadsWhetherAUnitIsDownOrDying` keeps the two
  `begun` flags behind it; `ui` (`ReadoutWriter`) and `core` (`DebugOverlay`) ask it too. One behaviour
  change: a man killed while a casualty no longer publishes his bleed-out as `Appearance.charge` during
  his throes.
