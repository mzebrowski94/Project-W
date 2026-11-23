# The rift

Type: task
Status: resolved
Label: ready-for-agent
Blocked by: 05 (sites and the site seam), 07 (the jaguar)
Spec: [spec.md](../spec.md) section 8
G-record: **G0115** *The rift is a fissure maze shut by ice, with two clearings*
ADR: **0025** *Ice is solid ground until broken*, which extends ADR 0022's door to a second thing
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-biome` (the biome as data) and
`new-system` (ice), plus `game-patterns` before inventing any maze or chokepoint algorithm

## What

A cold biome, the **rift** (`docs/glossary.md` has every word used here).
- **Ground:** **frost** ground cut into a maze by dark **fissures**.
- **Ice:** light-blue **ice** walls across ways through it, to be broken through.
- **Clearings:** two round **clearings** with no fissure, each holding a **control point** and more of the swarm.
- **Roster:** sludgers and jaguars from nests, thumpers and sludgers on the loose.

**Staged.** Stage A (ground, clearings, roster) is a playable biome without ice. Stage B adds ice. The work
may stop after A.

## Settled (2026-11-22, not provisional)

### Ground (stage A)
- `BiomeKind.RIFT`, appended last. Its `sim.biome.rift` block follows `new-biome`.
- **Base:** a new passable `Terrain.FROST`, appended last.
  - Cold, stony, grey-blue: `graphic.terrain.frost` about `3a4552ff`.
  - Not pure snow: optionally a sparse `patch` of a lighter frost tone for texture. If a second terrain is
    needed, it is passable and pace 1. Provisional.
  - Fallback `dust`.
- **Fissures:** a new **impassable** `Terrain.FISSURE`, appended last.
  - Dark: `graphic.terrain.fissure` about `0b0e14ff`.
  - Laid as **crags** (`crags.terrain=fissure`, as the caldera lays lava), **dense**: density about 0.18-0.22,
    smoothing low enough to give cracks rather than blobs. Tune by `BiomeMeasureTest` until the biome reads as
    a maze with passes open.
  - Like lava, it is solid to units, shots and sight (one `Solid`, G0091). Shots across a fissure stay
    deferred, as for lava.
- **No houses, no outcrops.** Never dealt beside a biome with the same base (`Mission.deal`'s rule).
- Width as the others (about 52-60 columns).

### Clearings (stage A)
- **Two** round clearings of radius about **8 tiles** (`sim.biome.rift.ground.clearings=2`,
  `clearingRadius=8`), centred near 1/3 and 2/3 of the biome's width, and vertically within the playable
  rows. Exact centres dealt from the biome stream inside a band.
- **No fissure** inside a clearing: the crag coat skips the disc.
  - Generation must still keep every pass open and every clearing reachable from the passes.
  - The crag repair already guarantees passes; add the clearings as places that must be reached.
- **Sites:** the rift's 2-3 sites. **One control point at the centre of each clearing** (2 of them); a third
  site, if dealt, goes by the ordinary band rule.
  - This is the rift's placement rule, for example a biome flag `sites.inClearings=true`, read by 05's
    seam. Not a branch on the kind (G0008).
- **Denser enemies in the clearings:** loose packs are placed inside the clearings first, about 60 %
  (`sim.biome.rift.furniture.clearingPackShare=60`), the rest anywhere. Nests keep their keep-offs from
  points as today, so they sit in the maze.

### Roster (stage A)
- `nest sludger 2 3`
- `nest jaguar 2`
- `loose thumper 2 3`
- `loose sludger 4 5`

Plus furniture:
- barrels 1-2;
- boulders 0-2;
- no trees;
- no geysers;
- a medkit.

No `pickup crate` line, since the rift has no house (ticket 05).

### Ice (stage B)
- **What it is:** an **entity** that `Solid` treats as solid ground until broken, then gone for good, on the
  **door** pattern (ADR 0022). Never a write into the `TileMap`, which is immutable (ADR 0022's Do-not-build).
  ADR 0025 records ice as the second such thing beside the door: `Solid.derive` takes both.
- **Where:**
  - After the fissure crags are laid and repaired, find the maze's **chokepoints**: narrow ways, at most about
    the clearance plus one tile wide.
  - Seal a share of them with ice (`sim.biome.rift.ground.ice.share` about 0.4), never the biome's entry or
    exit pass and never inside a clearing.
  - Generation counts an iced way as **open** for every connectivity and pass check, since it can always be
    broken. Flow fields treat it as a shut door is treated today (G0075: hunters go round when they can, and
    siege when they cannot).
  - The map module may mark the tiles, as it marks doorways, so placement can spawn one ice entity per run of
    tiles.
- **Breaking:**
  - **Any player-side shot** chips it: `sim.ice.health=300`, taking each shot's damage.
  - A **blast** breaks it outright (as a door).
  - An **alien leap** at it lands `sim.door.leapDamage`, or its own key, as a door siege does.
  - Alien shots do not chip it, as alien-side fire does not open doors.
- **Look:**
  - Light blue, `graphic.color.ice` about `a8dcf0ff`, a little translucent.
  - It shows cracks, or dims, as health falls, so its state is visible (pillar 5).
  - Broken, it vanishes. A shatter is optional.

## Read first

- `CLAUDE.md`.
- `.claude/skills/new-biome/SKILL.md`, `.claude/skills/game-patterns/SKILL.md`.
- `docs/adr/0009-*`, `0022-*`.
- G0009, G0068, G0069, G0070, G0075, G0091, G0095.
- `docs/design/invariants/map-and-view.md`, `aliens.md`.
- `assets/sim/biomes.properties`: the header, the caldera block (lava as crags) and the desert block.
- Tickets 05 and 07's Answers.
- Code: `map` (`Terrain`, `Crags`, `Coats`, `BiomeGround`, `Strip`, `StripFaults`), `Solid`, `Door`/`DoorSystem`,
  `Spawner.spawnDoor`, `FlowField`, `Placement`, `ProvingGroundScenario` (its disc test at tile centres, for
  the clearing shape).

## Build

### Stage A
1. Terrains and colours.
2. Biome block.
3. Clearings in the crag coat.
4. Sites in clearings.
5. Pack share.
6. Roster.

Sweeps and `BiomeMeasureTest`:
- no fault (`StripFaults`);
- both clearings reachable;
- 2 points in clearings;
- passes open;
- enemy density in clearings above the maze's.

Print a mission code with the rift first.

### Stage B
1. Ice: map marks, entity, `Solid`, flow fields, breaking, look.
2. Tests:
   - ice blocks movement and shots until broken;
   - rifles chip it to nothing at 300;
   - a grenade breaks it outright;
   - a leaping alien breaks it;
   - alien shots do not;
   - a hunter goes round ice when it can;
   - generation never ices the entry pass;
   - the sweeps stay green with ice counted open.

## Docs

- **G0115:**
  - The rift: ground, fissures as crags, clearings, sites in clearings, pack share, roster.
  - Why fissures stop shots (G0091's shortcut).
  - Ice placement and breaking.
- **ADR 0025:** ice as solid ground until broken, beside the door.
- **Invariant rows:**
  - `map-and-view.md`: "no fissure inside a clearing", **enforced**;
  - `map-and-view.md`: "an iced way counts as open, and the entry pass is never iced", **enforced**;
  - `map-and-view.md`: "ice is solid as a shut door until broken", **enforced**.
- **Glossary:** **frost**, **fissure**, **ice**, **clearing** and **rift** are written; check them against
  what was built.
- **`new-biome` skill:** if the clearings needed a new coat or a rule, add a line to it.

## Judge by

The printed mission code (rift first), for example `--mission=<code>`.

## Open for the implementer (provisional, for review)

- Crag density and smoothing.
- The chokepoint rule.
- Ice share.
- Whether a second frost tone is needed.
- The clearing centres.

## Done when

- The rift is in the biome pool: frost cut by dark fissures into a maze, iced chokepoints to break through,
  two clearings each with a control point and denser enemies.
- Its roster is as settled.
- Sweeps are green.
- G0115, ADR 0025 and the rows are written.
- `verify.sh` is green and the branch is committed.
- The final message lists the keys and a mission code.

## Answer

Built (merged into integration) in two commits, stage A d3916ce, stage B e9b8ece. G0115 (amends G0068), ADR 0025 (amends ADR 0022). No new system or component.
- **Stage A, ground:** `Terrain.FROST`, `Terrain.FISSURE`, `BiomeKind.RIFT` (all appended last). `map` records `Clearings`/`Clearing`, laid in `Coats` after the fixed pieces and before every other coat; no crag or outcrop in the disc, its centre counted as a way in so crag repairs keep it reached; `Strip.Laid.clearings()`. Furniture fields on every biome `sitesInClearings`, `clearingPackShare`, read by `Placement` as data: a point at each clearing centre taking that band; 60 % of packs anchored in the clearings first. `StripFaults.clearings`, `MissionFaults.clearings`; tests in `CoatsTest`, `MissionTest`, `TerrainCollisionTest`, `BiomePromisesTest`.
- **Stage B, ice:** an entity with a `Door` whose `openTicks` is 0 (nobody opens it) and a new `Door.chipped` flag, so `Solid.derive`, siege, blasts, routes (G0075) and `DoorSystem` take it unchanged. `map` marks ice runs (`Ice`, `Strip.Laid.ice()`), never writes the `TileMap`, so every pass check counts an iced way open. `Spawner.spawnIce`; `ProjectileBoundsSystem`: squad shot chips, alien shot nothing. `Appearance.wear` + cracks in `WorldRenderer` (pillar 5). `MissionFaults.ice`; `IceTest` (8).
- Full verify green (3507); MissionSweep 2192 seeds 0 faults; StripSweep 2000 seeds 0 faults (its fixture pool has no rift; the rift is swept through MissionSweep).

**Departures from Settled (for review):**
- **Roster** `nest jaguar 1 1; loose thumper 2 3; loose sludger 5 6` instead of `nest sludger 2 3; nest jaguar 2; loose thumper 2 3; loose sludger 4 5`: the dense maze plus point keep-offs (12 tiles) in the clearings leave room for about 2 nests at the worst seed; 4-5 nests become 8 at hard in the last position, and even `nest sludger 1 2; nest jaguar 1 1` fell short on dozens of 2000 seeds. Kept the jaguar nest, folded the sludger nest into one more loose sludger.
- **Width 64-72** instead of 52-60, for the room (as the desert, G0095).
- `BiomePromisesTest`: "caldera has fewer boulders than any biome that has them" now "no more than", since the rift's settled 0-2 equals the caldera's.
- Adding RIFT to the pool re-deals every seed: earlier tickets' mission codes changed (fresh ones in the run's final message).

Provisional decisions (AFK run, for review; in G0115 / ADR 0025):
1. Fissure crags density 0.16, smoothing 2 (~21 % of the maze). Turned down: 0.20 smoothed once (crackier, some seeds no nest); denser merges into a mass the repairs clear whole.
2. Clearing centres within a twelfth of the width of 1/3 and 2/3, pushed outward, one radius in from the edge row - each in its own site band. Turned down: jitter either side (both can land in the middle third).
3. Flag and pack share on furniture (`map` knows nothing of sites). Turned down: inferred from clearings.
4. No second frost tone; small neighbour-ground patches give texture.
5. Ice is a `Door`. Turned down: own `Ice` component (a second family in four systems), or a door table (too much for two kinds).
6. Chokepoint: a run across a way at most clearance + 1 long, solid both ends, open both sides, whose two sides do not join within 16 steps when shut; never within clearance of a kept tile; one per way. Entry and exit passes and clearings never iced.
7. Ice share 0.4 (~4 per rift), health 300 (six sidearm shots).
8. Wear shown on every door, not only ice, so a door under siege cracks too. Turned down: ice only.
9. A rift dealt last gets clearings + 1 sites, the extra for the approach columns.

Keys:
- `biomes.properties` rift: `widthMin=64`, `widthMax=72`, `base=frost`, `fallback=dust`, `patches=rect neighbour 0.03 4 6`, `crags.density=0.16`, `crags.smoothing=2`, `crags.terrain=fissure`, `clearings.count=2`, `clearings.radius=8`, `ice.share=0.4`, barrels 1 2, boulders 0 2, `sitesInClearings=true`, `clearingPackShare=60`, a medkit; the other nine `sitesInClearings=false`, `clearingPackShare=0`.
- `level.properties`: `sim.ice.health=300`.
- `graphic.properties`: `graphic.terrain.frost=3a4552ff`, `graphic.terrain.fissure=0b0e14ff`, `graphic.color.ice=a8dcf0d0`, `graphic.door.crackWidth=0.06`, `graphic.door.crackDim=0.35`.

Judge by: `./gradlew lwjgl3:run --args="--mission=MHPC-VVKG"` (rift first; hard `HHPC-VVKD`). No ice scenario; no shatter.
