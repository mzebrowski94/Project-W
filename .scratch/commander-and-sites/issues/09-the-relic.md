# The desert relic and its wardens

Type: task
Status: claimed
Label: ready-for-agent
Blocked by: 05 (sites), 06 (the worked site), 08 (emplacements)
Spec: [spec.md](../spec.md) section 10
G-record: **G0114** *The desert's relic is extracted under its wardens' fire, and its taking wakes snakes* (new)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-kind` (the relic as a worked
site, the warden as an emplacement) and `new-system` only for what those cannot say

## What

The desert's own **site**: a **relic**, a glowing square turning slowly over a stone **plinth**. It is
**extracted** as a mainframe is downloaded, but the attempt rouses far wider. It is guarded by four
**wardens** that start firing on the first attempt. Taking it hatches snakes nearby.

All of these words are in `docs/glossary.md`. This ticket is mostly configuration of 06's worked site and 08's
emplacement. If it needs a copy of either, stop: that means the general shape was missed, and should be fixed
there.

## Settled (2026-11-22, not provisional)

### Placement
- **1-2 relics** per desert (`sim.biome.desert.furniture.relics=1 2`), **among its 2-3 sites**, through 05's
  seam. They replace control points: a desert dealt 2 relics and 2 sites has no point.
- Each relic is placed by rule in G0068's order, in its own band, where a point would have gone.
- Its whole layout (plinth plus wardens plus the ring) needs open sand of about 7 units radius: off stones,
  mud blobs, the pass and nests. G0114 says how placement guarantees that room.

### The relic and plinth
- **Plinth:** solid, immovable, unbreakable, radius about 1.5 (`sim.site.relic.radius`). It stays after the
  relic is taken.
- **Relic:** the hovering square is drawn over the plinth, glowing and turning slowly (client clock on sim
  time, G0043). It is **gone once taken**: the plinth stays bare.
  - `graphic.site.relic.glow`: a warm gold.
  - `graphic.site.relic.spinDegreesPerSecond` about 30.
  - `graphic.site.relic.size`.
  - Plinth colour: a desert stone darker than `sand`.
- The red-X/tick screen is the mainframe's; the relic has none. It shows its work as the mainframe does, as a
  **charge** everyone sees.

### Extraction (06's worked site, kind `RELIC`)
- **Hold the action key** within reach for `sim.site.relic.workSeconds=6`, with the same resets: letting go,
  out of reach, wounded, dashing.
- Prompts: `HOLD [E] EXTRACT` (`ui.action.EXTRACT=EXTRACT`), and while worked **`EXTRACTING…`** (06's
  "verb while working").
- **Each fresh attempt rouses** at **twice** a point's radius: `sim.site.relic.rouseRadius=116`.
- **Troopers** extract it as they download a mainframe: one works, the others watch. This is by analogy with
  the mainframe ("extraction matches computer data download"), provisional, for review.
- **Taken:** 05's shared "site taken" step (draft point, caches, medkit, crates), then the snakes.

### Snakes on taking
- `Brood.hatch` of **2-3 `SNAKE`** (`sim.site.relic.snakesMin=2`, `snakesMax=3`) round the plinth, kept off the
  men, as a nest's brood is.
- A snake is a long body. Hatch them at `sim.site.relic.hatchRadius` about 14, so none spawns inside the warden
  ring. Check the snake's `slither.parts` build at spawn, which the explorer says any `spawnUnit(SNAKE, …)`
  gives.
- They are ordinary snakes and hunt as the desert's do.

### The wardens (08's emplacement, kind `WARDEN`)
- **Four**, at the relic's **diagonals**: NE, NW, SE, SW, about **5 units** from its centre
  (`sim.site.relic.wardenOffset=5`, along the diagonal).
  - The ground between plinth and wardens is **tight but walkable**: a commander (radius 2) fits between the
    plinth's edge and a warden, and between two wardens.
  - Check with a test that the plinth is reachable for the action key from inside the ring.
- Body: oval, drawn as an ellipse, sand-coloured (`graphic.color.warden`, near `sand` but lighter).
  Collision is circles only (ADR 0007), so its body is a circle of about 1.2 and the oval is only the look.
- Solid, immovable, unbreakable, **alien-side but not an enemy** (08's rule). Shots **hurt only troopers and
  commanders**.
- **Cycle:** a **volley** of four bearings, **N, E, S and W** (0, 90, 180, 270), every
  **`periodSeconds=0.75`**. Placed at the diagonals, their axis shots cross-hatch the ring in dodgeable lanes.
- **Waking:** `wakeRadius=0`. They are woken by **the first attempt to extract** their relic: the worked
  site's attempt tells its four wardens, by handle (ADR 0016).
- They **fire continuously** from then on, even when nobody is working, and **stop when the relic is taken**.
- **`sim.weapon.warden_shot`:** small, a normal wound, range about 22, speed about 26.

## Read first

- `CLAUDE.md`.
- Tickets 05, 06 and 08's Answers (what was built and where).
- G0068, G0074 (Brood), G0095 (the desert and snake), and the G-records 06/08 wrote (G0111, G0113).
- `docs/design/invariants/aliens.md`, `squad-and-objectives.md`.
- `assets/sim/biomes.properties` desert block.
- Code: 06's worked-site component and system, 08's emplacement, `Brood`, `Placement`.

## Build

1. **Relic as a worked-site kind**, and placement in the desert. The sweeps cover:
   - 1-2 relics;
   - 2-3 sites;
   - room for the ring;
   - passes open.
2. **Wardens as an emplacement kind**, woken by the relic's first attempt. Tests:
   - silent before any attempt;
   - all four fire N/E/S/W every 0.75 s after it;
   - keep firing while nobody works;
   - stop when taken;
   - shots wound a commander and pass an alien.
3. **Snakes on taking.** Test: 2-3 snakes appear outside the ring, off the men.
4. **Looks:** relic spin and glow, plinth, oval wardens.
5. **Scenario.** "Relic", hidden: sand, one relic with its wardens, a commander and squad, a few runners.

## Docs

- **G0114:**
  - The relic.
  - Why the rouse is wider.
  - The warden ring and its cross-hatch.
  - Waking on the first attempt and stopping on taking.
  - Snakes.
  - That troopers extract by analogy (provisional).
- **Invariant rows:**
  - `squad-and-objectives.md`: "a relic's plinth is always reachable from inside its wardens",
    **enforced** by the sweep;
  - `aliens.md`: "a relic's wardens never fire before its first attempt, nor after it is taken",
    **enforced**.
- **Glossary:** check **relic**, **plinth** and **warden** match what was built.

## Judge by

```
./gradlew lwjgl3:run --args="--scenario=relic"
```

Also a mission code with the desert early; print it.

## Open for the implementer (provisional, for review)

- Troopers extracting.
- The exact offsets, ranges and speeds.
- How placement guarantees the ring's room.

## Done when

- Deserts hold 1-2 relics among their sites.
- Extracting takes 6 s held and rouses widely.
- Four wardens wake on the first attempt and fire N/E/S/W every 0.75 s until it is taken.
- Taking it lays the site's crates and caches and hatches 2-3 snakes.
- The relic vanishes and the plinth stays.
- G0114 and the rows are written.
- `verify.sh` is green and the branch is committed.
- The final message lists the keys.
