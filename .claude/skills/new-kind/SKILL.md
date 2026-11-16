---
name: new-kind
description: Add a kind to Project-W as data - a weapon or enemy attack, an enemy strain, an upgrade or adaptation. Use once `feature` has cleared the design and the thing is a new member of an existing table, not new behaviour.
---

# Adding a kind

A **kind** is one constant of an enum with a `Defs` table behind it: `WeaponKind`, `UnitKind`,
`PickupKind`, `UpgradeKind`. The game reads a kind as data - no system asks which kind it is holding, and
a new one is a constant, a definition and a block, never a class, a marker, a `spawn` method or a branch
(G0008). The tables refuse a gap: `Defs.build()`, `ConfigBinder` and `Palette` each fail loudly on a kind
left half-added, so a red build names the missing piece.

## 1. Fit

Run `feature` first; it decides whether the thing belongs in the game. Then, for every trait the design
gives the new kind, name the field that carries it: on `WeaponDef`, on `UnitDef` or one of its sub-defs
(`LeapDef`, `RangedDef`, `GripDef` ...), on `PickupDef`, or a `Stat` an upgrade moves. Done when every
trait has a field.

- **Every trait has a field** - continue here.
- **A trait has none** - that trait is new behaviour. It goes on the definition every kind already has,
  off at zero for all of them, built with `new-system`; then come back for the kind itself.
- **A second turret, mine or nest** - those are one of a kind each, not tables (`docs/backlog.md`). Stop
  and raise it: it is the structural change that backlog row waits for.

## 2. Every kind

1. **Constant**, last in its enum, with a Javadoc line on what makes it a different problem from its
   neighbours.
2. **Block** in its `assets/sim/*.properties` file: copy the whole block of the closest existing kind,
   rename, tune. Keep every key the copy has - zero, `off` and `none` switch a trait off - and put the
   reasoning for the numbers in a comment above the block, as the files do.
3. **Look**, when it is drawn as something new: a constant in `sim/.../def/Look.java` and
   `graphic.color.<look>` in `assets/graphic.properties`.
4. **Name** on screen, where its family has one (below), in `assets/i18n/ui.properties`.
5. **Glossary**: its word in `docs/glossary.md`, and only that word afterwards.
6. **Fixture**: nothing, by default. `AlphaWorlds` fills units, guns and pickups over the shipped tables
   (`onShipped`), so a kind it does not name plays with shipped numbers. Put it there only when a test
   needs numbers that tuning must not move. Upgrades are the exception - below.
7. **Tests** pin what the design promises in a number - `SimConfigTest` has the shape ("one disc stops
   after one pull") - and any pairing of traits no existing kind has. The traits themselves already
   have their systems' tests.

## 3. By family

### Weapon or enemy attack

- **A gun a man carries**: `WeaponKind` and a block in `weapons.properties`; `type` is `ballistic`,
  `heavy` or `energy`, which is what an upgrade naming more than one gun reaches. To be found, it needs its
  **cache**: a `PickupKind` `ARMOURY_<GUN>` with its own look and a block in `pickups.properties` -
  `playerGrant=arm <gun>`, and `armoury.cards` for its share of the crate deck (copy `armoury_disc`).
  Name: `ui.weapon.<KIND>`.
- **A gun a creature fires**: `type=none` and no cache. The strain is armed with
  `sim.unit.<strain>.ranged.gun=<gun>`, or a barrage pattern with `...barrage.<pattern>.gun=<gun>`.
- **A blow up close** is the strain's own, not a gun: `contactDamage`, `attackArcDegrees` and
  `attackCooldownSeconds` on its block, or its `leap`, `hop` or `grip` sub-block.

### Enemy

- `UnitKind` and a `sim.unit.<strain>` block in `units.properties`; copy `frog`, whose comments explain
  each sub-block. A sub-block left out is off.
- Name: `ui.strain.<KIND>` in `assets/i18n/ui.properties` - the proving-ground menu and an adaptation's card
  read it; `TextFitTest.namesEveryStrain` fails the build without it.
- It is on the field only where a scenario puts it, in `sim/.../scenario/`: `spawnUnit`, a nest of it, or
  an entry in a wave schedule, or a line in a biome's `roster` in `biomes.properties` for a mission. A scenario's
  change to every strain (`for (UnitKind kind : ...)`) reaches it on its own - and that cuts both ways: the
  mission and every Adventure put each strain down **unaware** (`withAlwaysAggressive(false)`), so a strain that
  hunts with no detection range of its own never notices anyone there, though the proving grounds, on the shipped
  numbers, look fine. Test the new strain in a mission (`Scenarios.world(Scenarios.mission(...))`, as
  `MissionTest.sendsAWormAfterACommanderInItsBiome`), not only in a fixture.

### Upgrade or adaptation

One enum and one file for both: `UpgradeKind` and a `sim.upgrade.<name>` block in `upgrades.properties`,
whose header comment documents every key and every effect form.

- **Upgrade**: `side=player`; each effect names whom it reaches. Name `ui.upgrade.<KIND>`, at most 15
  characters (`TextFitTest`).
- **Adaptation**: `side=alien`, effects as `breed <strain> <stat> <percent>`; `answers=<upgrade>` makes it
  likelier while the players hold that upgrade. Two names: `ui.upgrade.<KIND>` and `ui.adapted.<KIND>`,
  the sentence the swarm's dispatch reads out. It is dealt only in a level that runs the schedule -
  `Scenario.adapting`, or `sim.adaptation.*` above zero.
- **Fixture, always**: a `.put` in `AlphaWorlds.upgrades()`, usually `off(ranks, effect)`. A shipped
  upgrade would be dealt in every fixture draft and checked against the fixture's deck.
- **A number no `Stat` names**: a `Stat` constant, one `@Reads` method on `Modifiers`, one caller where the
  number is used, and its `sim.upgrade.stat.<stat>.*` limits. `ArchitectureTest` holds one reader and one
  caller.

## 4. Finish

`.claude/scripts/verify.sh`, green. Write down what the design settled, as `feature` says. The final
message lists the new keys with their values and what each does to the feel.
