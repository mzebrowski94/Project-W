# Emplacements, and the fen's spires

Type: task
Status: resolved
Label: ready-for-agent
Blocked by: none
Spec: [spec.md](../spec.md) section 9
G-record: **G0113** *An emplacement fires a fixed cycle, and the fen has spires* (new)
ADR: **0024** if a new definition table needs recording beside ADR 0008
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-system`

## What

A standing shooter nobody crews, firing a **fixed, learnable cycle** of unaimed shots. Its first kind is the
fen's **spire**. **Ticket 09's warden is its second user**, so build the general shape now: one component,
one system, and a definition per kind (CLAUDE.md, *Small patch, right shape*).

Coin the umbrella word **emplacement** in `docs/glossary.md` (Levels and objectives, beside **spire**): "a
standing shooter nobody crews: a spire or a warden". Use only that word in code and docs.

## Settled (2026-11-22, not provisional)

### The emplacement (general)
- A definition table, `EmplacementKind` (`SPIRE` now, `WARDEN` in 09), with a `sim.emplacement.<kind>.*` block
  each:
  - radius, look, gun;
  - `periodSeconds`;
  - `wakeRadius` (0 means woken by something else, as 09's wardens are);
  - and the shape of its cycle.
- Per kind, the cycle is either:
  - **dealt**: a cycle of `cycleMin`-`cycleMax` bearings drawn from the eight compass points when the level
    is built, from the placing stream; or
  - **fixed**: a set of bearings fired together every period. 09's warden fires N, E, S and W.
  - Say which in config, for example `cycle=dealt 4 6` / `cycle=volley 0 90 180 270`.
- The dealt cycle is stored on the entity, so the snapshot carries it (ADR 0014). **Never re-rolled**: one
  spire's cycle stays the same for the whole level, so it can be learnt (pillar 5). No `Rng` after
  placement.
- **Fires** through `Guns.volley`. The firer needs `Weapon`, `Position`, `Body` and `Faction`. Bearings are
  unaimed, and the rule is one shot per bearing of the current step, every `periodSeconds`.
- **Body and status:**
  - Solid, immovable, **unbreakable**: no `Health`, nothing to kill. Shots and blasts stop on it, and it is
    never a target.
  - Faction **ALIEN**, so its shots **hurt only troopers and commanders** (Mateusz). But it is **not an
    enemy**:
    - never counted by `CLEAR_ENEMIES`;
    - never perceived or targeted by troopers;
    - never a nest-like **threat**;
    - not shot by turrets.
  - Find the one place each of those asks "is this an enemy", and make it say no, by a field on the
    definition, never a branch on the kind (G0008).
- **Waking:** a `wakeRadius` kind fires only while a player-side unit is within that radius; otherwise it
  idles and its cycle does not advance. Neighbours come from `SpatialGrid`.

### The spire
- **Where:** the **fen only**, `sim.biome.fen.furniture.spires=3 5`.
  - Laid by a placement rule in G0068's order (G0113 says where).
  - Off the passes, aprons, sites (point radius plus a margin) and nests.
  - Never in a thicket blob's middle; on mire is fine.
- **Numbers** (`sim.emplacement.spire.*`):
  - radius 1.4;
  - `periodSeconds=1`;
  - `cycle=dealt 4 6`;
  - `wakeRadius=45`;
  - gun `spire_shot`.
- **`sim.weapon.spire_shot`:**
  - small: radius about half a rifle round's;
  - range 30, speed 25;
  - harm wound, as gunfire is.
- **Look:**
  - `Look.SPIRE`, a tall dark totem-like column, drawn as a disc with a lighter cap. `graphic.color.spire`
    is a mossy grey-green, not the frog's green.
  - Its shot is a small pale-green bead.
  - Optionally, a faint tick shows on the spire's edge at the bearing it will fire next, so the pattern
    reads (pillar 5). Provisional.

## Read first

- `CLAUDE.md`.
- `docs/adr/0008-*`, `0014-*`, `0016-*`.
- G0008, G0020, G0024, G0035, G0068, G0071 (barrage: counted, never rolled), G0089 (the geyser, a neutral
  thing on its own clock).
- `docs/design/invariants/combat.md`, `squad-and-objectives.md`.
- Code:
  - `Guns.volley`, `BarrageSystem` and `PatternDef` (as a reference; do not bend the barrage, which needs
    `Perception` and `Health`);
  - `GeyserSystem` and `Geyser` (the shape of a clock on a placed thing);
  - `TurretSystem`;
  - `Spawner` (`spawnTurret`, `spawnGeyser`);
  - `Placement` (geysers, turrets);
  - `ObjectiveSystem` (enemies counted), `TrooperCombatSystem` (targets), `CollisionSystem.foe`.

## Build

1. **Emplacement sim.** Tests:
   - a dealt cycle is the same across two runs of one seed, and differs between seeds;
   - it fires one shot per period along the next bearing;
   - it is idle with nobody near;
   - its shot wounds a commander and passes an alien;
   - it is not counted as an enemy and no trooper aims at it;
   - a volley kind fires all its bearings at once (for 09).
2. **Spire placement in the fen.** The sweeps stay green and spires keep off the sites and passes.
3. **Look.**
4. **Scenario.** "Spire Fen", hidden: a patch of mire with three spires, a commander and squad.

## Docs

- **G0113:**
  - Emplacements.
  - Why unaimed fixed cycles (learnable, pillar 5).
  - Why alien-side but not an enemy.
  - Why unbreakable.
  - The spire and its placement.
- ADR 0024 only if `EmplacementKind` is judged a structural addition beside ADR 0008's tables. The new-kind
  skill flags "a second turret, mine or nest" as structural.
- **Invariant rows:**
  - `combat.md`: "an emplacement's cycle never changes within a level", **enforced**;
  - `squad-and-objectives.md`: "an emplacement is never an enemy to clear or a target", **enforced**.
- **Glossary:** add **emplacement**; check **spire** matches.

## Judge by

```
./gradlew lwjgl3:run --args="--scenario=spire-fen"
```

Also a mission code with the fen early.

## Open for the implementer (provisional, for review)

- The config grammar for cycles.
- The next-bearing tell.
- The exact keep-off distances.

## Done when

- Spires stand in every fen, 3-5 of them, firing fixed learnable cycles every second at the squad's side
  only, while somebody is near.
- They are not enemies and cannot be broken.
- The emplacement mechanism takes a volley kind for 09.
- G0113 and the rows are written.
- `verify.sh` is green and the branch is committed.
- The final message lists the keys.

## Answer

Built (merged into integration), G0113 (amends G0068); no ADR 0024 needed:
- **Emplacement table:** `EmplacementKind`/`EmplacementDef`, `sim.emplacement.<kind>.*`; cycle `dealt <fewest> <most>` or `volley <bearings>`, packed into one number on the `Emplacement` component (snapshot carries it; never re-dealt).
- **`EmplacementSystem`** (`system/level`) at 54, after `SlingSystem`, before `ControlPointSystem`: wakes on where men ended the tick, its gun reads the upgrade ledger so it runs before `UpgradeSystem`. Fires through `Guns.volley`; wake radius via `SpatialGrid`; radius 0 = woken by something else (09's warden).
- **Body:** solid `Obstacle`, no `Health`, Faction ALIEN, no `Enemy` - nothing hunts it, no branch needed.
- **Spire placement:** pass after geysers, as a boulder; `emplacementKeepOff` off ways in, nests, finale watch, site edges, never on thicket; cycle from the attempt's stream. `MissionFaults.emplacements` checks these and the 3-5 count.
- **Look:** disc with lighter cap, edge tick at each bearing the next step fires (`SpireMark`).
- **Scenario** "Spire Fen": mire, three spires, squad at the west edge, a point beyond.
- **Docs:** G0113 + index; G0068 amended by; two enforced invariant rows; glossary **emplacement** (spire, warden rows point to it; the "words we do not use" entry rewritten); `components.md`, `simulation-order.md`, `new-biome` skill.
- Sweeps: MissionSweep 2192 seeds 0 faults, StripSweep 2000 seeds 0 faults. `EmplacementTest`/`CycleDefTest` passed on first run (never seen red).

Provisional decisions (AFK run, for review; also in G0113):
1. Volley grammar with commas `volley 0,90,180,270`. Turned down: spaces (new `ConfigBinder` reading).
2. A dealt cycle's bearings all differ. Turned down: repeats (N,N,N,E).
3. Bearings are compass points (multiples of 45). Turned down: free angles.
4. Next-bearing tell: edge tick, always shown. Turned down: none; only while awake; turning `Facing`.
5. Only a man on his feet wakes it. Turned down: the down too.
6. Asleep, its clock stops; first shot one period after waking. Turned down: running clock.
7. Keep-off one key, 15. Turned down: wake radius 45 or range 30 (no room in the fen).
8. Alien shots pass a spire. Turned down: stopping them (collision exception).
9. Spire Fen's cycles hand-laid (4, 5, 6 bearings): a scenario may not start a random stream.
10. No ADR 0024: one more kind table on ADR 0008.
11. Bead damage 50 (only turrets and barrels care; a man takes the wound).

Keys:
- `assets/sim/level.properties`: `sim.emplacement.spire.radius=1.4`, `.look=spire`, `.gun=spire_shot`, `.periodSeconds=1`, `.wakeRadius=45`, `.cycle=dealt 4 6` (wake wider than reach: first beads land short, a warning).
- `assets/sim/weapons.properties`: `sim.weapon.spire_shot.*` (spit block) `projectileRadius=0.15`, `projectileSpeed=25` (step-round slow), `projectileRange=30`, `damage=50`, `harm=wound`, `projectileLook=spire_shot`.
- `assets/sim/biomes.properties`: `furniture.spires=3 5` fen, `0 0` the other eight.
- `assets/sim/missions.properties`: `sim.mission.placement.emplacementKeepOff=15`.
- `assets/graphic.properties`: `graphic.color.spire=4a5a48ff`, `graphic.color.spire_shot=c8f0b0ff`, `graphic.spire.cap=8a9a84ff`, `.capShare=0.45`, `.tell=d8e8c8ff`, `.tellLength=0.45`, `.tellWidth=0.2`.
- `assets/i18n/ui.properties`: `ui.weapon.SPIRE_SHOT=BEAD`.

Judge by: `./gradlew lwjgl3:run --args="--scenario=spire-fen"`; `--mission=MHAA-AAA4` (fen first; hard `HHAA-AAAX`; generated, not launched).
