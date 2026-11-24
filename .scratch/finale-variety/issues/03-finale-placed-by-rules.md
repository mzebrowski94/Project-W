# A finale placed by rules

Type: task
Status: resolved
Label: ready-for-agent
Blocked by: 01 (a green `main`, and the sweep this ticket extends)
Spec: [spec.md](../spec.md) answers 1-5
G-record: **G0120** *A finale is placed by rules round a fixed skeleton* (exists; **amends G0064 and G0087**) - its
placement half; ticket 04 builds its enemies half
Skills: `feature` (steps 1-3), `game-patterns` (scatter), then `/mattpocock-skills:implement` with `new-biome`

## What

Replace the finales' hand-made `layouts` with placement rules, dealt from each finale's own stream through `Scatter`,
round a fixed **skeleton**. Both finales: the redoubt and the keep. Add two turrets to the keep.

## Settled (2026-11-24, not provisional)

- **Skeleton, fixed:** the post, the watch, the mouths, the north-south flip; in the keep the boss's spot and the six lair
  nest spots (their positions; their strains are ticket 04's).
- **Placed by rules, everything else:** rock (cover), turrets, mines, barrels, crates and gun caches - the hold's and the
  lair's alike (the lair's gun caches included; which guns is ticket 04's).
- **Mirrored north-south pairs:** place one half, mirror about the anchor's row; a single item stands on the row.
- **Cover in bands with an open lee:** rock pieces from a short list of shapes in the block (short bars, L shapes); in the
  keep, in a band between the post and the boss inside his barrage's reach; in the redoubt, between the post and each
  mouth. Every piece's lee (the side away from the boss; in the redoubt away from its mouth) open and reached; a minimum
  gap between pieces; nothing on a mouth's route or the lair's.
- **Counts dealt from min-max ranges** in the block; never moved by the tier (G0063) or the players seated (G0119).
- **Turrets fixed:** 4 in the redoubt round the post; **2 in the keep**, a mirrored pair between the post and the boss,
  inside his barrage's reach.
- **The stream's order:** the kind, the flip, then the rules - the layout draw goes away. Codes dealt before this end on
  different finale ground: accepted (G0065); say so in `## Answer`.
- **Delete the layouts**: `sim.finale.*.layouts.*`, their parsing in `FinaleDef`, `ConfigBinder`'s numbered-blocks use if
  nothing else needs it, and the tests that only held layouts. One way to place a finale, not two. Rewrite the header
  comment of `assets/sim/finales.properties`.
- **The sweep checks every rule it can refute** (`MissionFaults.finale`, `MissionSweepTest`): routes and mouths open and
  reached, every lee open and reached, gaps kept, turrets inside the boss's reach in the keep, counts within ranges,
  symmetry, nothing hostile of the last biome inside the watch. Sweep every finale kind and both flips by name, as
  G0087 did for layouts.
- Fill in G0120's *Enforced by*; replace the invariant rows that cite layouts (`map-and-view.md`, `aliens.md`, wherever
  `layout` appears) and their tests; glossary words as already written (*skeleton*, *lee*).

## Open for the implementer

- The rule set's shape in config: band radii, shape list, gaps, per-item ranges. Start from the deleted layouts' numbers
  so a playthrough feels like today's on average (mines about 10-20, barrels 4-8, one crate and one gun cache in the hold).
- Where the rules live: `map` if they are a pure function of terrain and geometry (ADR 0009), `sim` (`Mission`) if they
  read a strain's or a barrage's numbers (the boss's reach) - pass the number in rather than crossing the line.
- Whether `Scatter` needs a mirrored pass or `Mission` mirrors its output: reuse before you create.

## Read first

- `CLAUDE.md`; `docs/determinism.md`.
- `docs/design/invariants/map-and-view.md`, `aliens.md` (lair, boss), `combat.md` (turrets, mines).
- G0024, G0048, G0064, G0068, G0082, G0087, G0120.
- `assets/sim/finales.properties`, `FinaleDef`, `FinaleKind`, `Mission`, `Scatter`, `FinaleDefTest`, `MissionTest`,
  `MissionFaults`, `MissionSweepTest`.

## Done when

- No `layouts` left in config or code; both finales placed by rules; the keep has its two turrets; the sweep holds every
  rule over its seeds and `verify.sh` is green.
- `## Answer` gives four mission codes: each finale kind, both flips.

## Answer

**Built.** `FinaleDef(ground, hold, rules, waves, lair)`: the skeleton (`Hold(post, watch, mouths)`, the keep's holes and
boss in `lair`) and `FinaleRules`, a lot per thing - `fewest most near far west east`, both ends even, dealt in pairs.
`FinalePlacement` (sim, beside `Placement`) deals the cover on the finale's bare ground before the strip is laid (32
attempts, counts kept, G0070's rule) and the turrets, pickups, the lair's caches, barrels and mines on the strip as laid,
each lot from its own stream, all through `Scatter`'s new mirrored mode (`mirroredAbout`). The keep has 2 turrets inside
the boss's barrage. Layouts are gone: `Layout`, `sim.finale.*.layouts.*`, `FinaleDef.laid`/`layoutCount`,
`Mission.Finale.layout` (now `cover`), the hold's turrets/mines/barrels/pickups, and `ConfigBinder`'s numbered blocks
(a numbered block under a list's key is now an unknown key). `MissionFaults.finaleRules` holds counts, mirrors, every
piece's ring (its lee) open and reached, the ways from the mouths and to the boss, and the keep's reach. The sweep picks
seeds by finale and flip. 2064-seed sweep: 0 faults.

**Accepted (G0065):** every mission code dealt before this ends on different finale ground.

**Provisional** (G0120 `## Amended by`, items 1-8): lots as rings in tiles from the post, every lot in pairs (nothing
alone on the row; alt: odd counts with one on the row). Rules in `sim`, only the mirrored pass in `map` (alt: `Mission`
mirroring `Scatter`'s output). Cover dealt before the strip, with 32 attempts. `apart=1.5`, `gap=2`, pieces held off
the anchor row so no one-tile slot opens. Shapes: bars and small Ls, no `ell 3 3`. Reach = the shortest positive range
of the barrage's guns. Keep cover 2-4 (4-6 did not fit). The lair's cache kinds stay a fixed list in 03.

**Keys** (`assets/sim/finales.properties`; deleted: every `layouts.*`, `hold.turrets/mines/barrels/pickups`, the lair's
`pickup` lines). New, per finale `sim.finale.<k>.rules.`:
- redoubt: `apart=1.5`, `gap=2`, `shapes=bar 3 1; bar 1 3; bar 2 1; ell 2 2; ell 3 2; ell 2 3`, `cover=4 6 5 12 -13 13`,
  `turrets=4 4 4 8 -8 8`, `mines=16 20 3 13 -14 14`, `barrels=6 8 3 12 -13 13`, `pickups=2 2 2 4 -5 5`,
  `pickupKinds=crate; armoury_flamer`, `lair=0 0 0 0 0 0`, `inReach=false`.
- keep: the same `apart`, `gap`, `shapes`; `cover=2 4 3 11 1 11`, `turrets=2 2 3 9 2 10`, `mines=8 12 3 13 -14 8`,
  `barrels=4 6 3 11 -13 6`, `pickups=2 2 2 4 -5 5`, `pickupKinds=crate; armoury_laser`, `lair=4 4 2 11 4 12`,
  `lairKinds=armoury_shotgun; armoury_railgun; armoury_laser; armoury_flamer`, `inReach=true`.

**Play** (printed from the integration tip after 05): `./gradlew lwjgl3:run --args="--mission=<code>"` with redoubt
`MHAA-YX34` (plain), `MHC3-G4AH` (flipped); keep `MHNA-GY9H` (plain), `MH6F-ECPV` (flipped).
