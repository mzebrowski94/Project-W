# Firepower: heavy damage, no flamer stock

Type: task
Status: resolved
Label: ready-for-agent
Blocked by: -
Spec: [spec.md](../spec.md) B
G-record: **G0127** (exists; **amends G0056, G0110**)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-kind`

## What

Firepower stops stocking a flamer in every crate and instead makes the three heavy guns hit harder.

## Settled (2026-11-24, not provisional)

- `sim.upgrade.firepower.effects` becomes three gun effects, **+15 a rank**, two ranks as now:
  `gun flamer gun_damage team 15; gun bazooka gun_damage team 15; gun detonator gun_damage team 15`.
  The `stock armoury_flamer 1` effect goes. Rewrite the block's comment.
- The flamer is dealt at the armoury deck's baseline, as every gun (`armoury.cards=3` already; check nothing else
  favours it).
- **Blasts rise with the gun**: the rocket's blast and both limpet blasts (`limpet.*` damage) take the gun's
  `gun_damage`. If they do not today, make the **one place** that reads `gun_damage` for a shot's blast do so - never a
  second read beside it.
- Retire `CrateTest.addsNoSecondFlamerUnderFirepower` and `UpgradeTest.stocksEveryCrateWithAFlamerUnderFirepower`
  (or turn them into "a crate under Firepower is dealt as any other"), the Firepower wording in
  `docs/design/invariants/pickups.md` and `CrateSystem`'s javadoc, and add G0127 as *Amended by* on G0056 and G0110.
- `AdventureScenario`'s comment on Firepower stocking crates.

## Open for the implementer

- Whether the `Stock` effect form, left with no user, stays (default: **stays**, with its test - it is a card's
  grammar, and Full Crates-like cards may want it) or goes. Say which.
- How the limpet's blast learns its gun's damage scale, if it does not already.

## Read first

- `CLAUDE.md`; `docs/design/invariants/pickups.md`, `progression.md`.
- G0056, G0058, G0100, G0110, G0127.
- `assets/sim/upgrades.properties` (Firepower), `weapons.properties` (flamer, bazooka, detonator, limpet), `Effect`,
  `Modifiers`, `CrateSystem`, `UpgradeTest`, `CrateTest`.

## Done when

- A test: under Firepower each of the three guns, and the bazooka's and limpet's blasts, land 15 % more a rank; no crate
  is stocked. `verify.sh` green.
- A hidden scenario (`--scenario=<name>`) with Firepower held at rank 2 and the three guns to hand.

## Answer

Built on `afk/pfe-01-firepower`, merged into `pfe-integration`; `verify.sh` green but for the rift seeds.

- **Firepower** is three gun effects, +15 % a rank for the flamer, bazooka and detonator, `team`, two ranks. The stock
  is gone; a crate under Firepower is dealt card for card as without it (`UpgradeTest.dealsEveryCrateUnderFirepowerAsWithoutIt`).
- **A blast takes the damage its source declares** (G0130, new, amends G0018): `BlastsOnDeath.damage`, given
  `sim.blast.damage` in one place in `Spawner` and handed to the `Blast`. `Guns.volley` scales the rocket's and the
  limpet's blast with the same private `scaled` read that scales the shot - the one caller of `Modifiers.gunDamage`.
- **Card text**: a run of gun effects alike but for the gun is one line, `FLAMER/BAZOOKA/LIMPET DMG +15%`
  (`ui.effect.guns.GUN_DAMAGE`): a card shows two effect lines and Firepower now has three effects.
- Retired `CrateTest.addsNoSecondFlamerUnderFirepower` and `UpgradeTest.stocksEveryCrateWithAFlamerUnderFirepower`;
  their rule lives on as `.addsNoSecondCardOfTheKindAStockNames` and `.stocksEveryCrateWithTheCardAStockNames` on a
  test-only stock card. G0056 and G0110 amended by G0127.

**Provisional, for review (AFK run):**

- **The `stock` effect form stays**, with its tests, no shipped user. *Turned down:* deleting it - a card's grammar
  Full Crates-like cards may want, and the deletion would be a wider diff than the ticket.
- **The limpet learns its gun's scale at the throw**: `Guns.volley` passes the scaled blast damage to `spawnLimpet`,
  which puts it on the limpet's `BlastsOnDeath`; calibrating changes radius and harm, never the damage. *Turned down:*
  reading `gun_damage` again when it goes off - a second read, and its owner may hold another gun by then.
- **A blast's damage lives on its source** (G0130). *Turned down:* a per-gun `blastDamage` key - `sim.blast.damage`
  is the base for every gun today, and tickets 02 and 03 decide what their own blasts need.
- **Folded card line** `{1}{2} DMG {0}%`. *Turned down:* raising `CardText.EFFECT_LINES` to three (a top-bar layout
  change) and `HEAVY DAMAGE` (the card would have to know weapon types).

**Keys:** `sim.upgrade.firepower.effects=gun flamer gun_damage team 15; gun bazooka gun_damage team 15; gun detonator
gun_damage team 15` (was `stock armoury_flamer 1; gun flamer gun_damage team 25`); `ui.effect.guns.GUN_DAMAGE` new.
At rank 2 a bazooka's or limpet's blast takes 1300 rather than 1000 - felt only by the boss (9750), searer and thumper
(2000), the snake (1200) and a turret.

**Scenario:** `./gradlew lwjgl3:run --args="--scenario=heavy-range"` - Firepower II held, three caches each of the
flamer, bazooka and detonator, a pack of brutes and a thumper.
