# The mainframe

Type: task
Status: resolved
Label: ready-for-agent
Blocked by: 05
Spec: [spec.md](../spec.md) section 6
G-record: **G0111** *A mainframe is a site you download, and one man does it while the others watch* (new)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-system`

## What

A new **site**: a **mainframe** standing inside a house, **downloaded** by holding the action key beside it.
The word is in `docs/glossary.md`. Commanders and troopers can both download it. While a trooper works it, the
rest of his men watch round him.

Build the "worked site" mechanism generally. **Ticket 09's relic is its second user**, so this ticket must
make the relic a configuration of the same thing, not a copy (CLAUDE.md, *Small patch, right shape*).

## Settled (2026-11-22, not provisional)

### Placement
- At most **one mainframe per biome**.
- If the biome lays at least one **house** (never a gatehouse), a **50 %** chance (`sim.site.mainframe.chance=50`)
  that one of its sites is the mainframe.
  - It **replaces a control point** within the biome's 2-3 sites, through 05's seam.
  - It is dealt from the biome's own stream, by rule, in G0068's order.
- It stands **inside** the house, against a wall, clear of both doorways.
  - The house's stock (crates, medkits) is laid round it.
  - Add the rule in the order; G0111 says where.
- Which house, if there are two (the quarry): dealt from the stream.

### The object
- Solid, immovable, **unbreakable**: no health. Blasts and shots stop on it.
- About 2.5 across (`sim.site.mainframe.radius`). Faction neutral, so nothing hunts it.
- **Drawn** as a large rectangular mainframe or central unit, dark grey, with a black screen showing a
  **red X**. When downloaded, the screen shows a **green tick**.
  - The red X is on the object, not over the view, so G0098 is untouched.
  - Colours: `graphic.site.mainframe.body`, `.screen`, `.locked=e02a2aff`, `.done=3ad06aff`.

### The work
- **Hold the action key** within reach (`Reach.nearest`, as a crate is reached) for
  `sim.site.mainframe.workSeconds=6`.
- Letting go, stepping out of reach, being **wounded** or dashing **resets it to zero**. Follow the
  `DoorSystem.work` template: `worked` set each tick, `workTicks` zeroed on an idle tick, and the reset on a
  wound via `Vitality.woundedThisTick()`.
- One worker's work at a time, no stacking: a commander's or a trooper's.
- **Each fresh attempt rouses**: `Rouse.within` at `sim.site.mainframe.rouseRadius=58` (the control point's).
- When done it is **taken**: it calls 05's shared "site taken" step (draft point, caches, medkit, crates) and
  shows the tick.
- It is never worked again.

### Text and charge
- New `ActionKind.DOWNLOAD`, a hold. Before the work starts, the prompt is `HOLD [E] DOWNLOAD`
  (`ui.action.DOWNLOAD=DOWNLOAD`).
- **While being worked**, the worker's chip reads **`DECRYPTING…`** with its fill.
  - Add the in-progress verb as its own key (`ui.action.DOWNLOAD.working=DECRYPTING…`), and make it a general
    "verb while working" that 09 reuses as `EXTRACTING…`.
  - Prompts stay this machine's (G0098).
- **Everyone** sees the work as a **charge** on the mainframe (`appearance.charge = progress`, as a point and
  a door publish theirs).

### Troopers
- A trooper goes for an untaken mainframe as he goes for an untaken point. `TrooperObjective.advances` picks
  the nearest untaken **site** by route, opening a shut door on the way as today.
- **One trooper works it** (`DoorSystem.trooperAt`'s one-per-door rule).
- **Up to four others** (`sim.trooper.captureCrowd` − 1) stop near it and watch their **sectors**
  (`splitIntoSectors`, `keepWatching`). Spares beyond that are sent on, as G0086 sends them from a point.
- A trooper hit while working loses the work and fights by the cascade. Another takes it up afterwards.

## Read first

- `CLAUDE.md`.
- `docs/design/invariants/squad-and-objectives.md`, `map-and-view.md` (houses).
- G0016, G0021, G0029, G0068, G0069, G0075, G0086, G0098.
- Ticket 05's Answer (the site seam and the shared step).
- Code:
  - `DoorSystem` (work, `trooperAt`), `Door`;
  - `ActionSystem`, `Action`, `ActionKind`, `Reach`;
  - `Rouse`, `ControlPointSystem`;
  - `Placement.houseStock`, `House`;
  - `TrooperObjective` (`advances`, `countCrowds`, `splitIntoSectors`), `TrooperMoveSystem`;
  - `Prompts`, `ActionChips`, `AppearanceSystem`, `WorldRenderer`.

## Build

1. **Worked-site sim.** A component holding kind, work ticks, worked, taken and attempt state, plus a system
   after `ActionSystem` and `DamageSystem` (say why in the registration).
   - Kind: a small definition table for worked sites, `MAINFRAME` now and `RELIC` in 09, with a
     `sim.site.<kind>.*` block each (ADR 0008).
   - Tests:
     - 6 s held takes it;
     - letting go resets;
     - a wound resets;
     - each attempt rouses once;
     - taking it calls the shared step (crates appear);
     - never taken twice.
2. **Placement:** chance, house, wall, replacing a point. Run the sweeps: a mainframe never blocks a
   doorway, and every biome still has 2-3 sites.
3. **Prompt and charge.**
4. **Troopers.** Tests:
   - squad alone downloads it;
   - exactly one works;
   - the others hold sectors;
   - a sixth is sent on.
5. **Look:** body, screen, X to tick.
6. **Scenario.** "Mainframe", hidden: a house with a mainframe and a shut door, a commander and squad outside,
   grunts round.

## Docs

- **G0111:**
  - What a mainframe is.
  - Download rules.
  - One works while others watch.
  - Placement rule and order.
  - That the worked-site table is the shape 09 fills.
- **Invariant rows:**
  - `squad-and-objectives.md`: "one man works a site at a time", **enforced**;
  - `squad-and-objectives.md`: "a biome has two or three sites, a mainframe among them at most once",
    **enforced** by the sweep.
- **Glossary:** **mainframe** is written. Add **download** only if the code uses the word as a noun.

## Judge by

```
./gradlew lwjgl3:run --args="--scenario=mainframe"
```

Also a mission code with a mainframe in an early biome; print it.

## Open for the implementer (provisional, for review)

- Exact placement against the wall.
- Whether troopers prefer a mainframe or a point when both are near (nearest by route is the default).
- Look details.

## Done when

- Mainframes appear in half the biomes that lay a house, inside it.
- They are downloaded in 6 s by a commander or a trooper.
- The work resets as settled and rouses.
- Taking one leaves crates and caches.
- Squads work one-plus-watchers.
- Prompts read `HOLD [E] DOWNLOAD` then `DECRYPTING…`.
- The screen goes from X to tick.
- G0111 and the rows are written.
- `verify.sh` is green and the branch is committed.
- The final message lists the keys.

## Answer

Built (merged into integration), G0111 (amends G0068, G0098):
- **Worked-site table:** `WorkedSiteKind`/`WorkedSiteDef` (`sim.site.<kind>.*`), `WorkedSite` component; `MAINFRAME` now, 09's relic is one more row with its own action (`EXTRACT`, `ui.action.EXTRACT.working`).
- **Mainframe:** solid, immovable, unbreakable, neutral; shots and blasts stop on it.
- **`WorkedSiteSystem`** after `DoorSystem` (after `ActionSystem` and `DamageSystem`: hands and wounds of the tick known; before draft and objectives: taken counts this tick). Door's work template; letting go, out of reach, dash or wound resets; one tick of work per tick; each fresh attempt rouses once; taken through `Sites.taken`, never again.
- **`ActionKind.DOWNLOAD`:** `HOLD [E] DOWNLOAD`, then `DECRYPTING…` with fill; charge on the mainframe for everyone; brackets include it.
- **Troopers:** `TrooperObjective` nearest untaken site by route; one works, up to four watch sectors, a spare sent on.
- **Placement:** first pass in a biome, its own stream; own house only, 50 %; against a wall clear of both doorways; takes its band; house stock round it.
- **Look:** dark-grey cabinet, black screen, red X then green tick.
- **Objective ring** (asked by Mateusz with the ticket): white/yellow stripes turning and pulsing round every untaken site, incl. the finale hold point; from Site state, same on every machine.
- Mainframe counts for take-every-point; caches and crates never on its body; mission sweep counts sites; two `MissionTest` tests now count sites.
- **Docs:** G0111 + index; G0068/G0098 amended by; two enforced rows in `squad-and-objectives.md`; glossary **worked site**, **objective ring**; input map E row; `components.md`, `simulation-order.md`.

Provisional decisions (AFK run, for review; also in G0111):
1. Radius 2.4 (4.8 across): "about 2.5 across" read as the radius key; 1.25 is smaller than a trooper. 2.4 not 2.5 so it clears the wall tile. Turned down: radius 1.25; flush to the wall.
2. Wall spot: middle of any inside-wall tile, a man's width plus its body from every doorway. Turned down: opposite wall only; house centre.
3. Troopers: nearest site by route, mainframe and point alike. Turned down: preferring the mainframe.
4. After download the squad holds round it as round a taken point. Turned down: leaving at once.
5. Trooper "at" a mainframe within edge + body + arm. Turned down: own radius key.
6. Chip fill from the man's own action progress, set to the mainframe's while held. Turned down: chip reading the site (no `DECRYPTING…` switch).
7. Mainframe counts for take-every-point. Turned down: a new objective.
8. Reinforcements still only beside control points (a replaced point means fewer spares). Turned down: men by the mainframe.
9. Turrets still only beside control points.
10. Ring hidden once taken (turned down: dimmed); under the men (turned down: over); spin and pulse follow `graphic.ui.motionScale`; rings the finale hold point too.
11. Body colour `graphic.color.mainframe` instead of `graphic.site.mainframe.body` (looks take a palette colour; two keys would drift).

Keys:
- `assets/sim/level.properties`: `sim.site.mainframe.radius=2.4`, `.workSeconds=6`, `.rouseRadius=58`, `.chance=50`, `.housed=true`, `.action=download`, `.look=mainframe`.
- `assets/graphic.properties`: `graphic.color.mainframe=3c4046ff`; `graphic.site.ring.first=fffbe8ff`, `.second=ffd21fff`, `.stripes=24`, `.pieces=2`, `.width=0.6`, `.margin=0.8`, `.spinDegrees=30`, `.pulseHz=0.8`, `.pulseDepth=0.35`; `graphic.site.mainframe.screen=0a0c0eff`, `.locked=e02a2aff`, `.done=3ad06aff`, `.width=1.9`, `.height=1.5`, `.screenWidth=1.2`, `.screenHeight=0.8`, `.stroke=0.14`.
- `assets/i18n/ui.properties`: `ui.action.DOWNLOAD=DOWNLOAD`, `ui.action.DOWNLOAD.working=DECRYPTING…`.

Judge by: `./gradlew lwjgl3:run --args="--scenario=mainframe"` (house, shut door, squad of six, grunts, a ringed point in the yard); `--mission=MHPC-VVKG` (mainframe in brake, first biome; hard `HHPC-VVKD`).
