# Rocketman and Pantera

Type: task
Status: resolved
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

## Answer

Built (merged into integration), G0108:
- **Upgrades:** `UpgradeKind.ROCKETMAN`, `PANTERA` (personal, 2 ranks), draft blocks, names, effect lines, `AlphaWorlds.upgrades()` fixtures.
- **Surge** (new glossary word: Rocketman's stun round a launching dash): `Stat.SURGE_REACH` (base 0), one reader `Modifiers.surgeReach`; `Surges` helper called from `CommanderControlSystem.launch` only when a dash launches; aliens filed in a `SpatialGrid` only on a surge tick; `Sight.withinRange` + `Sight.clearLine`; aliens only. Lands via new `Damage.landStunning(ticks, slot)`; `DamageSystem` dazes the same tick.
- **Ring:** `Puff.reach` (reset), `Spawner.spawnSurge`, `Look.ROCKETMAN`, published as `burst`/`burstFade`, drawn in `graphic.color.rocketman`.
- **Scenario:** Dash Yard Upgraded (`DashYardScenario(true)`): 4×4 grunt pack, Rocketman 2 + Pantera 2 for every slot; `UpgradeSystem.hold` slot overload.
- **Docs:** G0108 + index; G0018 amended by G0108; `combat.md` row "Rocketman never stuns the squad's side" (enforced); glossary; `simulation-order.md` constraint (control before `DamageSystem`, `SystemOrderTest` row); `components.md`.
- Boss not exempt from the stun, as the ticket says; noted in G0108.

Provisional decisions (AFK run, for review; also in G0108):
1. Stun inside the control system's launch (`Surges` helper). Turned down: a separate system - needs a launched-this-tick flag on `Dash` and an order renumbering.
2. `Damage.landStunning(ticks, slot)`, provoking toward him, credited to his slot. Turned down: making `stun` public (G0057).
3. Pantera -13 % recharge per rank (-26 % at rank 2): effect percentages are whole. Turned down: -12 (24 %).
4. Rocketman reach 5 / 10 (linear fold cannot give 6 / 10 with 0 unheld).
5. Weight 10, tier 1, as Bandolier.
6. Distances measured from where everyone stood at the start of the press tick (the puff's spot). Turned down: after movement.
7. Ring stays where the surge went off, 0.35 s, grows from 0.4 of reach to all. Turned down: ring following him.
8. Scenario holds the upgrades for every slot up to capacity. Turned down: slot 0 only.
9. `GraphicConfigTest.readsACommandersRim` was red on the integration tip (pinned `f8fafcff` after `graphic.border.color` was retuned to `949697ff`); now checks the rim is opaque.

Keys:
- `assets/sim/commander.properties`: `sim.commander.dash.stunSeconds=1`, `sim.commander.dash.surgeSeconds=0.35` (ring life).
- `assets/sim/upgrades.properties`: `sim.upgrade.stat.surge_reach.minPercent=0`, `.maxPercent=0`, `.min=0`; `sim.upgrade.rocketman.*` (player, weight 10, ranks 2, tier 1, effects `count surge_reach chooser 5`); `sim.upgrade.pantera.*` (same, effects `count dash_most chooser 1; scale dash_recharge chooser -13`).
- `assets/graphic.properties`: `graphic.color.rocketman=8f7bffff`, `graphic.dash.surge.grownFrom=0.4`.
- `assets/i18n/ui.properties`: `ui.upgrade.ROCKETMAN`, `ui.upgrade.PANTERA`, `ui.effect.count.DASH_MOST`, `ui.effect.scale.DASH_RECHARGE`, `ui.effect.count.SURGE_REACH`.

Judge by: `./gradlew lwjgl3:run --args="--scenario=dash-yard-upgraded"`
