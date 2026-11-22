# Rocketman and Pantera

Type: task
Status: claimed
Label: ready-for-agent
Blocked by: 02
Spec: [spec.md](../spec.md) section 1
G-record: **G0108** *A dash can stun what is round him* (new: the second non-lethal radial effect, by G0018's
revisit rule)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-kind` (the upgrades) and
`new-system` (the stun burst)

## What

Two **personal** upgrades on the dash (ticket 02), drafted like any other (G0049).

- **Rocketman:** at the **start** of every dash, a small magnetic field round the commander **stuns the
  aliens near him**.
- **Pantera:** more dashes, recharged faster.

## Settled (2026-11-22, not provisional)

### Rocketman
`UpgradeKind.ROCKETMAN`, personal (`chooser`), **2 ranks**.
- **Trigger:** on the tick a dash **launches**. A dash refused, or a dry press with no dash left, stuns
  nothing.
- **Reach:**
  - Every **alien** whose centre is within the radius, with a clear line from the commander (`Sight.withinRange`
    plus `Sight.clearLine`, as a blast checks).
  - Never troopers, never commanders, never anything neutral (barrels, slicks), never a door.
  - Neighbours come from `SpatialGrid`, never a scan over all.
- **Radius:**
  - 6 at rank 1, 10 at rank 2.
  - The upgrade fold is **linear per rank** (`Modifiers.sum` × ranks). If a base plus a per-rank count
    cannot give 6/10 with radius 0 when the upgrade is not held, use **5 per rank (5 / 10)**. Write which in
    G0108.
- **Stun:**
  - A **declared stun** (`Stunned.daze`, through the existing duration fold `Modifiers.stunTicks`) of
    `sim.commander.dash.stunSeconds=1`.
  - The boss is not exempt, since a declared stun lands on it today. Leave that as it is and note it.
- **How it lands:** through the `Damage` path, so it behaves as every other blow does. That means a harmless
  blow that lands (G0057), plus the stun, and a shield it meets breaks.
  - Today `Damage.stun` is private. Add the smallest public entry that records a stun-only, no-harm blow, and
    have `DamageSystem` daze it, as `ContactDamage.stunning` already does.
  - **Not** `Blast`: a blast is lethal by G0018, and this is the second non-lethal radial kind, recorded by
    G0108.
- **Look:**
  - A short expanding ring round him on the launch tick.
  - Reuse the slam's `BurstMark` outline (`Appearance.burst`/`burstFade`), tinted a magnetic blue-violet,
    `graphic.color.rocketman=8f7bffff`.
  - The struck aliens already show their `DazeMark`.

### Pantera
`UpgradeKind.PANTERA`, personal, **2 ranks**.
- **Rank 1:** +1 dash. **Rank 2:** +1 more dash and recharge 25 % faster.
- The fold is linear per rank, so the expressible shape is **each rank: `count dash_most chooser 1;
  scale dash_recharge chooser -12.5`**. That gives rank 1 +1 dash at −12.5 %, and rank 2 +2 dashes at −25 %.
  Build that, and say in G0108 that rank 1 also shortens the recharge slightly.
- Dash **speed** and distance are **not** upgraded.

### Both
- Draft blocks in `assets/sim/upgrades.properties`: `side=player`, `ranks=2`, `tier` as Bandolier's,
  `requires=none`, `group=0`, `answers=none`, and `weight` as the other personal upgrades.
- Their names in `assets/i18n/ui.properties`, as `ui.upgrade.<KIND>` (15 characters at most, `TextFitTest`):
  `ROCKETMAN`, `PANTERA`.
- Effect lines as `ui.effect.<form>.<STAT>`.
- Fixtures in `AlphaWorlds.upgrades()`.

## Read first

- `CLAUDE.md`.
- `docs/adr/0017-*`, G0018, G0049, G0057, G0059.
- `docs/design/invariants/progression.md`, `combat.md` (stun rows), `wounds.md` (shield rows).
- Code:
  - `upgrades.properties` header and Bandolier's block;
  - `Effect`, `Stat`, `Modifiers` (`grenadeMost`, `stunTicks`);
  - `Damage`, `ContactDamage.stunning`, `DamageSystem.daze`;
  - `Sight`, `SpatialGrid`, `BlastSystem.catchEveryoneInside` (the shape, not the class);
  - the `Dash` component and system from 02.

## Build

1. **Pantera first** (data only). Tests: rank 1 gives 3 dashes; rank 2 gives 4 and a faster recharge; a
   commander without it is unchanged; it reaches only its chooser.
2. **Rocketman.** Tests:
   - stuns an alien inside the radius on the launch tick;
   - spares one outside it, and one behind rock;
   - never stuns a trooper or another commander;
   - a dry press stuns nothing;
   - rank 2 reaches farther;
   - breaks an alien's shield if one exists.
3. **The ring look.**
4. **Scenario.** "Dash Yard Upgraded", hidden: Dash Yard with Rocketman 2 and Pantera 2 held by every seated
   slot at the start, and a denser pack of grunts to dash into.

## Docs

- **G0108:**
  - Rocketman as the second non-lethal radial effect (G0018 amended by it).
  - Why it stuns at the start: an escape, not an engage.
  - Why aliens only.
  - The linear-rank compromise for both upgrades.
- **Invariant row** in `combat.md`: "Rocketman never stuns the squad's side", **enforced**.

## Judge by

```
./gradlew lwjgl3:run --args="--scenario=dash-yard-upgraded"
```

## Open for the implementer (provisional, for review)

- Exact `Damage` entry name.
- The ring's size and timing.
- The `weight`/`tier`.

## Done when

- Both upgrades are drafted and folded through `Modifiers` with one reader each.
- Rocketman stuns aliens round a launching dash.
- Pantera adds dashes and a faster recharge.
- G0108 and the invariant row are written.
- `verify.sh` is green and the branch is committed.
- The final message lists the keys.
