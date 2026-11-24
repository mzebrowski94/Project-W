# Firepower: heavy damage, no flamer stock

Type: task
Status: open
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
