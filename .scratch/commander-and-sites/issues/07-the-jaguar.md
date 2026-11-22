# The jaguar

Type: task
Status: open
Label: ready-for-agent
Blocked by: none
Spec: [spec.md](../spec.md) section 7
G-record: **G0112** *The jaguar lives in houses* (new; **amends G0068**'s "no pack in a house")
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-kind` (the strain, its glob), and
`new-system` only if a trait has no field

## What

A new strain, the **jaguar** (defined in `docs/glossary.md`): a runner's mechanics on a grunt-sized, much
tougher, faster body with a quicker leap, and a burst of three harmless globs that leave glue.

It **lives in houses**: it wanders inside until it notices somebody, then leaps the door down to get out.
Ticket 10's rift also breeds them from nests.

## Settled (2026-11-22, not provisional)

### The strain
`UnitKind.JAGUAR`, appended last. Its `sim.unit.jaguar` block is a **copy of the runner's**, key for key, then:
- **Body:** `radius=1.8` (grunt-sized), **`health=350`**, **`speed=15.6`** (runner 12 × 1.3).
- **Leap:** `leap.speed=44` (runner 34), `leap.cooldownSeconds=0.6` (runner 0.9), `hesitationMin/MaxSeconds`
  about 0.04/0.10 (runner 0.06/0.16). The rest stays as the runner's: sequenceLength 3, airborne 0.24,
  deflect 0.
- **Weave, dodge, approach, avoidance:** the runner's.
- **Perception:** take the **grunt's** detection and lose ranges (51/77), not the runner's 78/104. Otherwise a
  house's jaguars would break out for a man most of a level away. Write this into G0112; it is provisional,
  for review.
- **Shots:** a `barrage` of one pattern, the sludger's `stream` shape:
  - `gun=jaguar_glob`, `shots=1`, **`salvos=3`**, **`intervalSeconds=0.1`**, `aimed=true`, `locked=true`,
    `stepped=false`;
  - `reach=24`, `windUpSeconds=0.25`, **`restSeconds=3`**, `stunCancels=true`;
  - `holdsStill` as the sludger's (true). A brief stop to fire is the tell. If the barrage supports firing on
    the move cheaply, leave it true anyway: provisional, for review.
- **`sim.weapon.jaguar_glob`**: the sludger's `glob` with **`damage=0`** and **harm none**. Its `slicks.*` are
  the glob's (glue: slowFactor 0.4, slowSeconds 0.3, seconds 6). Smaller and quicker: `radius` about 60 % of
  the glob's, speed +20 %.
- **Look:**
  - `Look.JAGUAR`, `graphic.color.jaguar=9b1fe8ff` (strong purple), with a comment in the file's voice:
    purple says "shoots at you", and this one is deeper than the spitter's `a05cf0` and brighter than the
    magma spitter's `6e30b4`.
  - Its glob is drawn as the glob is, tinted the jaguar's purple.
- **Names:** `ui.strain.JAGUAR=JAGUAR`; the glossary row exists.

### Houses
- In every biome that lays houses (today the plain, quarry, brake, anthill and badlands), **each house** (never
  a **gatehouse**) holds jaguars with chance **50 %**, **1-3** of them.
- As data, a new roster form `house <strain> min max`, beside `loose` and `nest`.
  - The chance is a global key, `sim.mission.placement.housePackChance=50`.
  - Each house rolls it from the biome stream.
  - Add the line to the five biomes. The count is scaled by tier and ramp as roster counts are (G0063).
- **Placement:** inside the house (`House.inside()`), by `Scatter`, after the house stock so they do not land
  on a crate or mainframe. Its place in G0068's order is written in G0112.
- **Idle, they wander inside:** the `patch` is anchored in the house, the wander radius is capped to the
  inside (about half its shorter side), and **drift is 0** for a creature laid in a house. The shut doors
  hold them in.
- **Getting out:** perception is range-only (G0011), so a jaguar notices through walls. Shut in with nobody
  reachable, it takes the route to the door and **leaps it down** (G0075's siege, `EnemyLeapSystem`, door
  health 600, 100 per leap). Nothing new is needed; test it.
- A door a trooper or commander opens lets them out at once.

### Amendment
G0068 rejects "a pack in a house". G0112 amends it: a house may hold a **house pack** of one strain, laid by the
`house` roster form; points and loose packs still never go in a house. Mark G0068 *amended by G0112*.

## Read first

- `CLAUDE.md`.
- `.claude/skills/new-kind/SKILL.md` (Enemy family).
- `docs/design/invariants/aliens.md`, `map-and-view.md`.
- G0011, G0045, G0063, G0068, G0069, G0073, G0075.
- `assets/sim/units.properties`: the runner block (about lines 138-212) and the sludger's barrage (about
  879-897).
- `weapons.properties`: glob (about 821-861).
- Code: `Roster`, `Placement` (packs, `houseStock`), `House`, `Scatter`, `EnemyLeapSystem` (door siege),
  `WanderDef`, `BarrageSystem`.

## Build

1. **The strain and glob as data.** Tests:
   - `Defs` builds it;
   - the glob harms nobody and leaves glue;
   - a jaguar in an open fixture leaps faster than a runner and survives what kills a runner.
2. **The `house` roster form and placement.** Tests:
   - over many seeds about half the houses hold 1-3 jaguars;
   - never a gatehouse;
   - never outside the house;
   - the sweeps stay green.
3. **Wander confined.** Test: an idle house jaguar stays inside for a minute with doors shut.
4. **Breakout.** Test: a commander outside a shut house within detection; a jaguar inside breaks the door by
   leaping and gets out.
5. **Scenario.** "Jaguar House", hidden: one shut house with three jaguars, a commander and squad outside.

## Docs

- **G0112:**
  - The strain, and why grunt perception.
  - House packs and the `house` roster form.
  - Confined wander.
  - The breakout through G0075.
  - Amends G0068.
- **Invariant rows** in `aliens.md`:
  - "a house pack is laid only inside a house, never a gatehouse", **enforced**;
  - "a jaguar's glob harms nobody", **enforced**.

## Judge by

```
./gradlew lwjgl3:run --args="--scenario=jaguar-house"
```

Also the proving ground, if it lists every strain.

## Open for the implementer (provisional, for review)

- The perception choice above.
- Glob size and speed.
- Hesitation values.
- How the confined wander is expressed: a per-unit cap, or a house-anchored patch.

## Done when

- Jaguars spawn in half the houses, 1-3 each.
- They wander inside until they notice somebody, then leap the door down.
- They weave, dodge, leap fast and fire three harmless globs that leave glue.
- They are strong purple.
- G0112 is written, and G0068 is marked amended.
- `verify.sh` is green and the branch is committed.
- The final message lists the keys.
