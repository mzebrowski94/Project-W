# 08 - One question for a man's life stage

Status: needs-triage

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
