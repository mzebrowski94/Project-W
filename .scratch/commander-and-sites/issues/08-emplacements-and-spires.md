# Emplacements, and the fen's spires

Type: task
Status: claimed
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
