# G0112: The jaguar lives in houses

**Status.** accepted

**Trigger.** Changing the jaguar's numbers or its glob; putting anything hostile in a house; the `house` roster form
or `sim.mission.placement.housePackChance`; how a creature laid in a house idles, or how it gets out.

**Enforced by.** `SimConfigTest.makesTheJaguarARunnerOnAGruntsBodyThatSpitsHarmlessGlue`; `JaguarTest` (leaps
faster than a runner, survives what kills one, fires a burst of three globs, its glob hurts nobody and leaves glue);
`HousePackTest` (about half the houses hold a pack, one to three on medium, only jaguars, inside a house of its own
biome and never a gatehouse, nothing else hostile in a house); `HousePackBehaviourTest` (stays inside its house for a
minute while it has noticed nobody, walks about it, leaps the door down and gets out to a man within its detection);
`MissionSweepTest.holdsEveryMissionToTheRules` through `MissionFaults.houses`, `.start` and `.finale`;
`JaguarHouseScenarioTest`.

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 7, and ticket 07). The points
under *Provisional* are the implementer's, taken in an AFK run, for review.

**Amends G0068** - "a pack waiting inside a house" is no longer rejected outright: a house may hold a **house pack** of
one strain, laid by the `house` roster form; points and loose packs still never go in a house.

**Constraint.**

- **The strain.** `UnitKind.JAGUAR`, its `sim.unit.jaguar` block a copy of the runner's key for key - its weave,
  dodge, approach and avoidance, a leap in threes, 0.24 s in the air, nothing deflects it - but for a grunt's body
  (`radius=1.8`), `health=350`, `speed=15.6` (the runner's 12 x 1.3) and a quicker leap (`leap.speed=44` against 34,
  `leap.cooldownSeconds=0.6` against 0.9). It notices as a **grunt** does, 51 and loses at 77, not at the runner's
  78/104 - perception is range-only (G0011), so it notices through walls, and with a runner's reach a house's jaguars
  would break out for a man most of a level away.
- **Its shots.** A barrage of one pattern in the sludger's stream shape (G0073): three single `jaguar_glob`s 0.1 s
  apart down a locked line, after a 0.25 s tint it stands still for (`holdsStill` - the stop is the tell), 24 reach,
  not stepped, 3 s rest, a declared stun breaks it off. **`sim.weapon.jaguar_glob` harms nobody**: the sludger's
  glob with `damage=0` and `harm=none` - a blow that does nothing still lands (G0057) - and the glob's own glue,
  every `slicks.*` key the same.
- **Look.** `Look.JAGUAR`, `graphic.color.jaguar=9b1fe8ff`: purple says "shoots at you", deeper than the spitter's,
  brighter than the magma spitter's. Its glob is drawn as the glob is, in the same purple (`Look.JAGUAR_GLOB`).
- **House packs.** A roster line `house <strain> min max`, beside `loose` and `nest`. Each house of a biome -
  never a **gatehouse** - holds a pack of each house line on `sim.mission.placement.housePackChance=50`, rolled house
  by house from the biome's **own** stream (`stream.derive(HOUSE_PACKS)`), so whether a house holds one is the same on
  every attempt; its count is drawn after the roll from the same stream and scaled as a loose line is (G0063,
  `Roster.count`). The five biomes that lay houses - plain, quarry, brake, anthill, badlands - have `house jaguar
  1 3`, last in the roster, so every other line draws as it did.
- **Their place in G0068's order:** straight after the house's stock - worked site, points, stock, **house packs**,
  then nests and the rest - by `Scatter` in `House.inside()`, round the stock and any worked site and off every
  tile of the house's doorways, where its doors stand. What comes after keeps off them as off anything standing.
- **Confined wander.** A creature of a house pack is put down with its patch the house (`HousePacks`,
  `Wander.confine`): centred on the inside, its wander radius capped to half the inside's shorter side, and **drift
  0**. The shut doors hold it in.
- **The breakout is G0075's siege**, nothing new: noticing a man it cannot reach with the doors shut, it takes the
  terrain's route to the door and leaps it down (G0069: `sim.door.health`, `leapDamage` a leap). A door a trooper or
  commander opens lets them out at once.

**Provisional (AFK run, for review).**

1. **Grunt perception** (51/77), the ticket's default. Turned down: the runner's 78/104 (breaks out for a man most of
   a level away).
2. **The glob:** `projectileRadius=0.5` (about 60 % of the glob's 0.8), `projectileSpeed=26.4` (the glob's + 20 %),
   `projectileRange=26` (a little past the reach, as the glob's is). Turned down: the glob's own size and speed.
3. **Hesitation 0.04-0.10 s** against the runner's 0.06-0.16. Turned down: the runner's (the quicker leap would then
   be the only difference in the tell).
4. **`holdsStill=true`**, as the sludger's: the brief stop is the tell. Not tried: firing on the move.
5. **The confined wander is a house-anchored patch**, written once at the spawn by `Wander.confine` - home the house's
   middle, the radius the lesser of the strain's and half the inside's shorter side, drift 0 - and kept for its life:
   one that broke out and lost its man idles in a small patch where it gave up, and never drifts. Turned down: a
   per-unit cap kept on a new field (a second number every reader must honour), and restoring the strain's patch once
   it is out (a "has left its house" state nothing else needs).
6. **A house that is not wholly `startKeepOff` off the start, or that reaches into the finale's watch, holds no pack**
   - it still rolls, so the stream stays put - as nothing hostile stands there (G0068, G0064). Turned down: laying the
   pack anyway (a pack awake on the first tick), or as much of it as fits (G0068's "silently placing fewer").
7. **A pack keeps off every tile of the house's doorways**, so no door is put down on a body. Turned down: off the
   doorway's middle only (a body still lay half in it).
8. **`ui.weapon.JAGUAR_GLOB=GLOB`**: what it is, as the sludger's is named.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Loose packs or points in a house* - G0068 still holds for them: a house is a cache you open, and a house pack
is the one exception, laid by its own line.

**No:** *A house pack that notices only through a doorway* - perception is range-only (G0011); a clear-line test for
one strain is G0015's list, and the door siege is how it gets out.

**No:** *A gatehouse pack* - a gatehouse is a biome's way in, and a pack behind its door would be a fight at the pass
nobody chose.
