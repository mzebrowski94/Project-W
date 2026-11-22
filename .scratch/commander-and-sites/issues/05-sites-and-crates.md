# Sites, and the crates a taken site leaves

Type: task
Status: resolved
Label: ready-for-agent
Blocked by: none
Spec: [spec.md](../spec.md) section 5
G-record: **G0110** *A taken site leaves the crates* (new; **amends G0056, G0068 and G0078**)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-system` and `new-kind` (Looter)

## What

This ticket lays the ground for tickets 06 (mainframe), 09 (relic) and 10 (rift).

- Bring in the **site**, the umbrella for a control point, a mainframe and a relic. A biome has two or three.
  Only control points exist after this ticket, but everything a taken point earns is now earned by a taken
  **site**, through one place.
- Rework crates:
  - furniture crates go **only into houses**;
  - every taken site lays **crates by player count** beside its grenade caches and medkit;
  - no crate holds two cards of one kind.
- Add the **Looter** personal upgrade.

## Settled (2026-11-22, not provisional)

### Sites
- A biome's site count is the existing `sim.biome.<name>.furniture.pointsMin/pointsMax`, **2-3** everywhere
  as today.
  - Rename the keys to `sitesMin/sitesMax`, so the key does not lie once a mainframe or relic replaces a
    point. Every biome block changes, and `new-biome`'s skill text with them.
- Tickets 06 and 09 **replace** control points with their sites inside that count. This ticket builds the
  seam they need: a placement step that is told how many sites the biome has and which of them are not
  points, and lays control points for the rest, one per band as today. Today it is always zero.
- **"What a taking leaves" becomes one step for any site taken**, called by `ControlPointSystem` today and by
  06's and 09's systems tomorrow. Today's `layWhatATakingLeaves` and `creditTheTaking` move into one shared
  place, for example `Sites.taken(...)` in `system/level`. No system references another (ADR 0016).
- It earns and lays:
  - the **draft point** (`pointsPerDraft` counts sites);
  - the goal line's taken/total, which counts sites;
  - `sim.controlPoint.grenadeCaches` per seated slot;
  - the `medkitChance` medkit;
  - and **crates**: **new**.
- The finale's hold point, and any point that `holdsAfterwards`, stays exactly as it is. It is not a biome
  site.

### Crates a taken site leaves
- **Count:** by `Seats.count` (as the grenade caches count) over thresholds
  `sim.site.crates.byPlayers=1,3,6,10`. A list of the smallest player count for each next crate:
  - 1-2 players: 1 crate;
  - 3-5: 2;
  - 6-9: 3;
  - 10 and more: 4.
  - `Players.MAX` is 8 today, so 10+ only future-proofs.
  - The loader refuses a list that is not strictly increasing or does not start at 1.
- **Where:** in the same ring as the caches (`reinforcementRadius`), off every point radius and every lying
  pickup, by the existing rule.
- **Dealt** like any crate, on its first opening (G0056).

### Furniture crates
- The biome's `pickup crate min max` count is honoured **only as house stock** (`houseStock`, crates first, as
  today).
- **Crates are no longer laid "by a point"** (`Placement.crates`/`byAPoint`): delete that step.
- Biomes that lay no house (cut, fen, caldera, desert) **drop their `pickup crate` line**, so no key lies.
  Biomes with 0-1 houses keep theirs, and it means "crates in the house, when there is one". Say so in the
  `biomes.properties` header.
- Finales' hand-placed crates are untouched.

### No duplicates
- When dealing, a drawn card whose **pickup kind** is already in the crate goes back, and another is drawn.
- If the deck runs out of other kinds, the crate is smaller than its drawn size.
- Firepower's flamer swap (`CrateSystem.stock`) applies only if no flamer is already in the crate.
- The armoury deck's even deal (a bag over cards) is kept. Only the per-crate kind is unique.

### Looter
- `UpgradeKind.LOOTER`, personal (`chooser`), **1 rank**.
- The **first time a commander holding Looter opens a given crate**, it holds one more card, of a kind not
  already in it, if it has room under `CrateConfig.CAPACITY` (4).
- **Once per crate, ever**: a `looted` flag on `Crate`, cleared in `reset()`.
- If the crate is being dealt at this opening, the deal's size is +1. If it was dealt earlier by somebody
  else, one more card is drawn now.
- The extra card stays in the crate for anyone (G0056: it keeps what it holds for whoever opens it next).
- Stat `CRATE_EXTRA` (COUNT, UNIT), one `@Reads` method taking the opener, one caller in `CrateSystem`.
- `ui.upgrade.LOOTER=LOOTER`, effect line, fixture.

## Read first

- `CLAUDE.md`.
- `docs/design/invariants/pickups.md`, `squad-and-objectives.md`, `progression.md`.
- G0049, G0056, G0068, G0077, G0078, G0086.
- Code:
  - `ControlPointSystem` (`creditTheTaking`, `layWhatATakingLeaves`, `laysAMedkit`);
  - `Placement` (`points`, `houseStock`, `crates`, `byAPoint`, `nextForAHouse`);
  - `Furniture`, `PlacementRules`, `MissionScenario`;
  - `CrateSystem` (`deal`, `size`, `card`, `stock`, `take`), `Crate`, `Armoury`, `Crates`;
  - `Seats`, `Modifiers`, `Stat`, `upgrades.properties`;
  - `GoalLine`, `ReadoutWriter`.

## Build

1. **The shared "site taken" step**, with `ControlPointSystem` calling it. Existing `ControlPointTest` stays
   green unchanged, plus crates.
2. **Crate count by players.** Tests: 1, 2, 3, 5, 6 and 8 seated slots give 1, 1, 2, 2, 3, 3 crates.
3. **Furniture crates as house stock only.** The `byAPoint` step goes, and lines are dropped from houseless
   biomes. Run the placement sweeps and `BiomeMeasureTest`; update any measure that counted crates.
4. **No duplicates.** `CrateTest`:
   - a crate never shows one kind twice over many deals;
   - a short deck gives a smaller crate;
   - Firepower does not add a second flamer.
5. **The site-count seam and the key rename.** The sweeps stay green.
6. **Looter.** Tests:
   - +1 card on the holder's first opening;
   - nothing on his second;
   - nothing on another Looter's later opening of the same crate;
   - +1 on a crate a non-Looter opened first;
   - never above 4;
   - the extra card's kind is new to the crate.

## Docs

- **G0110:**
  - Site as the umbrella.
  - Crates from takings instead of the field, counted by players.
  - House stock kept.
  - No duplicate kinds.
  - Looter.
  - Amends G0056 (where crates come from, unique kinds), G0068 (the placement order loses "crates by
    points"), G0078 (what a taking leaves). Mark each *amended by G0110*.
- **Invariant rows:**
  - `pickups.md`: "no crate holds two cards of one pickup kind", **enforced**;
  - `pickups.md`: "a taken site leaves crates by the seated count", **enforced**;
  - `progression.md`: "Looter adds at most one card to a crate, ever", **enforced**.
- **Glossary:** **site** is already written. Amend **crate** (where it comes from) and **control point** (one
  kind of site).

## Judge by

- A printed mission code whose first biome lays a house. Find one with the mission sweep and print it in the
  Answer.
- Play it with a pad seated (`--pads=all`) to see 1 vs 2 players.

## Open for the implementer (provisional, for review)

- Where the shared step lives and its name.
- Whether `controlPoint.grenadeCaches` and `medkitChance` keys move under `sim.site.*`. Moving them is
  preferred if cheap, since they are a site's now.

## Done when

- Every taken point lays crates by player count with its caches.
- Furniture crates appear only in houses.
- No crate repeats a kind.
- Looter works once per crate.
- The site seam exists for 06 and 09.
- G0110 and the rows are written, and the amended records marked.
- `verify.sh` is green and the branch is committed.
- The final message lists keys, including the rename.

## Answer

Built (merged into integration), G0110 (amends G0056, G0068, G0078):
- **Site seam:** `Site` component (`taken`) on every control point; draft count and goal line count sites. `Placement.Biome.sites(count, others, rng)` lays a point in every band whose bit in `others` is unset (`NO_OTHER_SITES` today; 06/09 fill it).
- **Shared step:** `Sites` in `system/level`, `Sites.taken(site, at, reach, laysCrates)`: marks taken, credits commanders on it, lays caches, medkit, then crates. `ControlPointSystem` holds one.
- **Crates by seated count** (1,2,3,5,6,8 seated -> 1,1,2,2,3,3).
- **Furniture crates house stock only**; by-a-point pass deleted; sweep rule "no biome crate outside a house".
- **No duplicates**; short deck gives a smaller crate; Firepower's flamer only if none dealt.
- **Looter:** `UpgradeKind.LOOTER`, `Stat.CRATE_EXTRA`, `Crate.looted`, fixture, name, effect line.
- **Docs:** G0110 + index; G0056/G0068/G0078 amended by G0110; three enforced invariant rows (pickups x2, progression); glossary crate, control point, grenade cache; `components.md`; `new-biome` skill.
- Sweeps: StripSweep 2000 seeds 0 faults; MissionSweep 2192 seeds 0 faults; `BiomeMeasureTest` green.

Provisional decisions (AFK run, for review; also in G0110):
1. Shared step as a helper object each site-holding system holds. Turned down: a polling system (a tick late); a static helper (families passed every call).
2. `Site` as a component. Turned down: counting `ControlPoint.captured`; a counter on `Level`.
3. Finale hold point carries a `Site` but lays no crates.
4. `grenadeCaches`/`medkitChance` stay under `sim.controlPoint.*` (moving would change `ControlPointTest`, kept unchanged).
5. Crates laid after caches and medkit; a cornered site lays fewer.
6. A shuffle left with only kinds already in the crate is reshuffled; smaller crate only when the whole deck lacks another kind.
7. Looter fires only on the opening tick; `looted` set even when full. Turned down: leaving a full crate unlooted.
8. Looter tier 1, weight 10; card `OWN +1 CRATE CARD`.
9. Seam marks bands with a bit mask. Turned down: a site-kind enum (06's table).
10. Readout keeps `pointsTaken`/`pointsTotal` names, now counting sites.

Follow-ups: Firepower rank 2 no longer adds a second flamer (its stock line overstates rank 2; numbers left for Mateusz). Crate lines overstate with `houseStock=2` (quarry `4 5` lays at most 4; header says so). Relic-only desert would crash `Placement.turrets` (`points.size()` 0) - ticket 09 guards it. `Sites` package-private; a port if 06's system lives elsewhere.

Keys:
- `assets/sim/level.properties`: `sim.site.crates.byPlayers=1,3,6,10` (new).
- `assets/sim/biomes.properties`: `furniture.pointsMin/pointsMax` -> `sitesMin/sitesMax` in all 9 biomes (2/3); `pickup crate` dropped from cut, fen, caldera, desert.
- `assets/sim/upgrades.properties`: `sim.upgrade.looter.*` (player, weight 10, ranks 1, tier 1, `count crate_extra chooser 1`); `sim.upgrade.stat.crate_extra.*` 0/0/0.
- `assets/i18n/ui.properties`: `ui.upgrade.LOOTER`, `ui.effect.count.CRATE_EXTRA`.

Judge by: `./gradlew lwjgl3:run --args="--mission=MH3K-6GPX --pads=all"` (quarry first, always a house; hard `HH3K-6GPU`).
