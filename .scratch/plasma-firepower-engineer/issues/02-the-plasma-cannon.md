# The plasma cannon

Type: task
Status: claimed
Label: ready-for-agent
Blocked by: 01 (same config files; serial only)
Spec: [spec.md](../spec.md) A
G-record: **G0128** (exists)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-system` (the orb's beams and power) and
`new-kind` (the gun and its card)

## What

A found gun that throws an **orb**; the orb beams the laser's way at up to three aliens as it flies and goes off in a
small blast.

## Settled (2026-11-24, not provisional)

- **The gun**: `WeaponKind.PLASMA`, `sim.weapon.plasma.*`, type `energy`, one orb a pull, heat `perShot=1` with the
  bazooka's reload shape (G0058). **The card**: `ARMOURY_PLASMA` in `pickups.properties`, `armoury.cards=3`,
  `crate=false`, `playerGrant=arm plasma`.
- **The orb**: speed about **26** (twice `sim.commander.speed=13`), a flight; goes off on touching an alien, a man of
  ours, scenery or rock, at its flight's end, or when its beams have landed its **power** (glossary).
- **Its blast**: a small **ordinary blast** (G0018), nobody's side, `harm` wound, moderate damage - the bazooka rocket's
  machinery (`blastRadius` on the shot), smaller.
- **The beams**: up to **3** aliens within **reach ~15** world units (three tiles of `sim.map.tileSize=5`), nearest first,
  re-picked as it flies, found through `SpatialGrid` (never a loop over all units). Each beam is the laser's: a stream of
  short fast shots from the orb, each drawn as a line back to the orb, stopping on and landing on the first thing they
  touch - an alien in the way, scenery, rock. **Beams never pick a man, and a man in the line takes nothing.**
- **Power**: every point a beam shot lands on anything alive is taken off the orb's power, through `Damage.land`'s
  result, never a second accounting.
- **Zero means off**: the beam's numbers (count, reach, rate, power) live on the definition every shot kind already has
  - e.g. a `BeamDef.off()` beside `RangedDef.off()`/`WeaveDef.off()` - and every other gun says `off`.
- **Look** (`graphic.properties`, pillar 5): green orb, thin slightly offset outer ring flickering green and white, green
  beam lines, green blast; the orb **dims as its power is spent**, so what is left is seen.
- Glossary words as written: *plasma cannon*, *orb*, *power*.

## Open for the implementer

- How a beam shot is spared by our men: the side its shots are on, matching how the commander's own laser treats a
  trooper. Say what you found.
- Whether beam shots inherit the gun's `gun_damage` modifiers (default: yes, the gun's damage scale is the gun's).
- The numbers: power (start near three bazooka rockets' worth), beam shot rate and damage, blast radius (under the
  bazooka's 7) and damage, heat. Starting values, said in the *Answer*.
- Whether the orb's power needs a snapshot field (pillar 7; `WorldCodecTest`). It almost certainly does.

## Read first

- `CLAUDE.md`; `docs/performance.md`; `docs/components.md`; `docs/simulation-order.md`;
  `docs/design/invariants/combat.md`.
- G0018, G0025, G0057, G0058, G0128.
- The laser's and bazooka's blocks in `weapons.properties`, `WeaponDef`, the shot and blast systems, `Damage`,
  `SpatialGrid`, the laser's beam drawing in `core`.

## Done when

- Tests: an orb beams at no more than three aliens in reach, nearest first; a beam stops on the first alien or scenery
  in its way; a man in the line takes nothing; the orb goes off on contact, at its flight's end and at its power; its
  blast wounds a man. `verify.sh` green; no allocation in the per-orb loop.
- A hidden scenario (`--scenario=<name>`) with the plasma cannon in hand and a crowd to fire into.
