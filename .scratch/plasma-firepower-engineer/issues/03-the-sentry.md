# The sentry

Type: task
Status: open
Label: ready-for-agent
Blocked by: -
Spec: [spec.md](../spec.md) C1
G-record: **G0129** (exists)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-system` and `new-kind`

## What

A new kind of thing on the field: the **sentry**, a players' standing gun nobody crews. This ticket builds the thing and
puts it down in a scenario; ticket 04 gives the commander a way to make one.

## Settled (2026-11-24, not provisional)

- **Small, light-grey**, drawn as equipment (as the turret is drawn square), clearly smaller than a turret.
- **Stationary**; turns **fast** onto the nearest alien in **medium reach** (start well under the turret's 55 - near a
  trooper's sight) and fires a light rapid gun (`sim.weapon.sentry_gun.*`, start near `turret_gun`'s rate with less
  damage). Its fire spares our men as a trooper's does (G0084's fouled line, or the shot's side - say which).
- **Has health; the aliens hunt it from the moment it stands** - a target as a man is, not "manned once".
- **Lives ~60 s** (`sim.sentry.lifeSeconds=60`), its time left shown (pillar 5, e.g. on the charge channel or a fuse-like
  tell - never a number).
- **Going off** - destroyed or out of time: a **small ordinary blast** (nobody's side, wounds a man, small damage) and a
  brief **stun** on the aliens round it (never our men), the dash surge's way (G0108, through `Damage` as a harmless
  blow). One path for both endings.
- Nobody **crews** it, nobody **hauls** it; it is no **subject** of the action key.
- A new kind per G0008: a constant, a definition, a block in `assets/sim/` (`level.properties` beside the turret, or its
  own), through `Spawner` - no `spawn` method of its own, no marker class. A component and system only for what is
  genuinely new (standing fire on a timer); reuse what the turret's aiming and the emplacement's standing fire already
  have before writing any.
- Glossary word *sentry* as written; never *mini-turret* in code or prose.

## Open for the implementer

- Whether its aim reuses the turret's turn (`turnRate`, `firingTurnFactor`) or a trooper's - say which, and why.
- Its health, radius, blast radius and damage, stun seconds. Starting values, in the *Answer*.
- Where its system sits in `docs/simulation-order.md`, with the reason.

## Read first

- `CLAUDE.md`; `docs/components.md`; `docs/simulation-order.md`; `docs/performance.md`;
  `docs/design/invariants/combat.md`, `squad-and-objectives.md`.
- G0008, G0018, G0024, G0082, G0084, G0108, G0113, G0129.
- The turret's block in `level.properties`, `turret_gun` in `weapons.properties`, the turret's and emplacement's
  systems, `Spawner`, `Damage`.

## Done when

- Tests: it fires at the nearest alien in reach and not beyond; aliens target it at once; it goes off at its life's end
  and when destroyed, the same way; its blast wounds a man and stuns aliens but never a man. `verify.sh` green.
- A hidden scenario (`--scenario=<name>`) with two sentries standing and a pack coming.
