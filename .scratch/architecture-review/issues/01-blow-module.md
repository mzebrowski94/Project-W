# 01 - One blow module owning Damage

Status: ready-for-human

Strength: Strong. Dependency: in-process. Report candidate 1.

## Problem

How a **blow** is recorded is spread across four writers of `Damage`, each copying its own subset of
`ContactDamage`'s fields:

- `CollisionSystem.strike` (`CollisionSystem:329-512`): add, stun, slow, pump, credit, `cameFrom`, lock
  on death, shove.
- `GripSystem.finish` (`GripSystem:138-176`): add and `cameFrom`, no credit.
- `GripSystem.escape`: add with a hard-coded `Harm.WOUND`, plus a shove.
- `BlastSystem` (`BlastSystem:101-114`): add, `cameFrom`, credit.

Rules exist twice: the invulnerability check (`CollisionSystem:359`, `DamageSystem:135`) and the shield
shatter (`CollisionSystem:416-419`, `DamageSystem:151-155`). `DamageSystem` copies 20 fields into locals
and then calls `reset()`.

A rule callers must know: `DamageSystem:110` returns when `amount <= 0` **before** it resets. For a man
only the harm matters, so `GripSystem:151` passes the bite amount just to get past the check,
`AlphaWorlds.A_BLOW = 1` exists (68 uses), and `TrooperCombatTest.shotFrom` sets `amount = 1`.

## Suspected bug (unconfirmed)

Venom's damage is 0 (`sim.weapon.venom.damage=0` in `assets/sim/weapons.properties`). A venom hit on a
man records `cameFrom` (`hasSource = true`) and is never reset. `cameFrom` keeps the first source, so his
next real hit provokes him toward the old frog muzzle (G0016), and the stale fields ride in every
snapshot.

## Direction

One module owns how a blow is recorded, gated and cleared; the writers state the blow, not the fields.
Tested through its interface on components rather than through a whole world.

## First step

A failing test: a venom hit on a man, then a second hit from somewhere else - he should be provoked
toward the second source. If it passes, the bug is not real and this is a pure deepening.

## Deletion test

Deleting `Damage`'s helpers would spread the keep-the-worst rules across four writers: complexity
concentrates in a blow module rather than moving.

## Comments

- 2026-09-30: Suspected bug confirmed (`DamageSystemTest.forgetsWhereAHitThatOwedNothingCameFrom`)
  and fixed. Built as G0057: `Damage.land` is the one recorder, a blow counts because it landed,
  `Harm.NONE`, `sim.weapon.*.harm`, `sim.unit.*.grip.escapeHarm`, a grab on a shield is a harmless
  blow, zero contact damage turns a strain's claws off. Awaiting review and a feel check.
