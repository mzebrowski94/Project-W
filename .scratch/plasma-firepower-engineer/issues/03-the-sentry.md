# The sentry

Type: task
Status: resolved
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

## Answer

Built on `afk/pfe-03-sentry`, merged into `pfe-integration` (green but for the rift seeds).

**What.** `Sentry` component and `SentrySystem` (`system.commander`); `Look.SENTRY` (drawn square, light grey, smaller than a
turret); `WeaponKind.SENTRY_GUN`; `SimConfig.SentryConfig` read from `sim.sentry.*` in `level.properties`;
`Spawner.spawnSentry(x, y, slot)`. It is on the players' side with health, so the aliens hunt it at once; it turns onto
the nearest alien it has a clear shot at in reach and fires through `Guns`; it goes off by dying - out of time it is
marked dead by `SentrySystem`, destroyed by `DamageSystem` - leaving its `BlastsOnDeath` and a stun through
`Surges.stunRound` (the dash surge's harmless stunning blow, factored out of `Surges.surge`). Time left: the body's core
shrinks on the charge channel. Tests: `SentryTest` (11).

**Provisional decisions** (also in G0129 *Amended by*):
- Aim: a turret's turn (rate, slower while firing, `TurretSystem.ON_TARGET` before firing), faster than a turret's.
  Turned down: a trooper's aim - it carries his aim error from the squad's stream; a sentry is a gun on a mount.
- Sparing our men: by the shot's side (players'), not G0084's fouled line. Turned down: the fouled line - it would hold its
  fire behind the squad it covers.
- Stun beside the blast, not in it: the blast stays on nobody's side (G0018). Turned down: a stunning blast.
- `Spawner.spawnSentry`, beside `spawnTurret`: no existing table to spawn it through. Turned down: a branch in
  `spawnTurret`, or the emplacements' table (the aliens', G0113).
- `SentrySystem` at 43, after `RespawnSystem`, before `ProjectileBoundsSystem`: after the damage that destroys it, before
  the blast its going off leaves and before the ledger moves (it fires through `Guns`).

**Keys** (`level.properties`): `sim.sentry.radius=1.4`, `health=300`, `lifeSeconds=60`, `range=30`, `turnRate=8`,
`firingTurnFactor=0.6`, `gun=sentry_gun`, `blastRadius=4`, `blastDamage=150`, `blastHarm=wound`, `stunRadius=10`,
`stunSeconds=1`. `weapons.properties`: `sim.weapon.sentry_gun.*` - `cooldownSeconds=0.2`, `damage=14`,
`projectileSpeed=150`, `projectileRadius=0.35`, `projectileRange=34`, `projectileLook=turret_shot`, no heat.
`graphic.properties`: `graphic.color.sentry=c9ced3ff`.

**Scenario:** `--scenario=sentry-post` - two sentries ahead of the commander, a pack (grunts, runners, two brutes) coming
from the east in four lots.
