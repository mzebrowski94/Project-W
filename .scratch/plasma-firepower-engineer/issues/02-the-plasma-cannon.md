# The plasma cannon

Type: task
Status: resolved
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

## Answer

Built on `afk/pfe-02-plasma-cannon`, merged into `pfe-integration`; `verify.sh` green but for the rift seeds; the
performance guard steady (no orb in it; `OrbSystem` costs nothing with none out).

- **The gun**: `WeaponKind.PLASMA` (`energy`), its card `ARMOURY_PLASMA` (`armoury.cards=3`, `arm plasma`). One orb a
  pull, heat to the stop (`perShot=1`, cools 0.5/s: two seconds). Several orbs may be out.
- **The beam** is `BeamDef` on every gun's definition, `off()` on all but the plasma cannon: count, reach, wait, power,
  and the gun its shots are - `WeaponKind.PLASMA_BEAM`, a laser-like block. `OrbSystem` (new, straight after
  `LimpetSystem`) picks the nearest aliens through `SpatialGrid` into fixed scratch arrays - no allocation - and fires
  one shot at each through `Spawner.spawnBeamShot`.
- **Power**: `Orb` (new component, in `Components.ALL` and `docs/components.md`, carried by the snapshot). `Damage.land`
  now returns what it recorded; `CollisionSystem` takes it off the orb named on the shot (`Projectile.beamedFrom`), on
  anything not in its throes. Spent, `OrbSystem` marks the orb dead and it goes off through the one reaction.
- **Look**: `ORB` (ball dims with power on the charge channel; offset ring flickering green-white, `OrbMark`),
  `PLASMA_BEAM` (green line), `ORB_BLAST` (green blast), `ARMOURY_PLASMA`.

**Provisional, for review (AFK run):**

- **How our men are spared**: the laser's shots are on his side and the collision rule does nothing within a side, so a
  trooper in the line takes nothing - matched. The orb itself is on **nobody's** side (as a limpet), so it goes off on
  our men; its own touch is a harmless blow, the blast the hurt. *Turned down:* a player-side orb with an exception in
  the collision rule.
- **Only the ball collides** (Mateusz, mid-run): `projectileRadius=1` is its body; the ring is drawn at 1.6 radii.
- **Beam shots inherit the gun's damage scale**, folded at the throw in `Guns.volley`'s one read. *Turned down:* none.
- **A gun names its own `blastDamage`** (G0130 updated): every gun block gained the key - 1000 for the bazooka,
  detonator, charged and magma spit, 0 for guns that leave no blast, 300 for the plasma cannon ("moderate": a grunt's
  death, not a brute's). *Turned down:* the orb's blast at `sim.blast.damage`'s decisive 1000, not moderate.
- **Picking ignores lines of sight**; a beam into rock stops on it and spends nothing. *Turned down:* a clear-line test
  per candidate - the spec puts the stop on the shot.
- **Power needs a snapshot field**: yes, `Orb.power` (`PlasmaTest.carriesWhatIsLeftOfItsPowerThroughASnapshot`).

**Keys:** `sim.weapon.plasma.*` - `projectileSpeed=26`, `projectileRadius=1`, `projectileRange=60`, `damage=0`,
`harm=none`, `blastRadius=4`, `blastDamage=300`, `blastHarm=wound`, `heat.perShot=1`, `heat.coolPerSecond=0.5`,
`heat.resumeBelow=0`, `type=energy`, `beam.count=3`, `beam.reach=15`, `beam.cooldownSeconds=0.1`, `beam.power=600`,
`beam.gun=plasma_beam`; `sim.weapon.plasma_beam.*` - `projectileSpeed=190`, `projectileRadius=0.3`,
`projectileRange=18`, `damage=10`, `trailLength=18`, `type=none`; `sim.weapon.<every gun>.blastDamage` (new);
`sim.pickup.armoury_plasma.*`; `graphic.color.orb=4dff6aff`, `.plasma_beam=a6ffb4ff`, `.orb_blast=5cff7cff`,
`.armoury_plasma=2ec85aff`; `graphic.orb.ring=1.6`, `.ringWidth=0.18`, `.ringOffset=0.2`, `.flickerHz=3`,
`.dimmest=0.35`.

**Scenario:** `./gradlew lwjgl3:run --args="--scenario=plasma-range"` - the plasma cannon in hand, two more caches,
his squad, boulders, and a crowd of grunts with brutes among them.
