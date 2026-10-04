# What a mission is

Type: grilling
Status: resolved

## Question

What is a daily mission, as a whole - its shape, its seed, its tiers, how it is won and lost, where it
is played, what is remembered - before any one part of it is designed?

## Answer

Settled in the charting session, 2026-10-04 (Q1-Q31).

**Shape**
- One **level**, played in one go: a strip west to east of **4 wide biomes + a finale** (amended, below).
  Upgrades (G0049), the reserve and adaptations run on across biomes; losing loses all of it.
- Biome count is one key, the same for every tier.
- The generator thinks in an ordered list of biomes, not coordinates, so a turning path stays possible.
- Biomes are separated by a rock wall with **1-2 gaps** (drawn per transition) - amended: a **ridge** crossed
  by 1-2 **passes** (13) - each at least a key's
  width (4 tiles today); no scatter in a strip either side of a gap; reachability start -> finale, every
  gap, point and crate checked by a headless test over many seeds x 3 tiers.
- Biomes are drawn from a pool by seed, **none twice in a mission** and never the same base ground side by
  side (amended, below). A **slight ramp**:
  enemy numbers rise with a biome's position by one tunable factor.
- The **finale** is a kind drawn from a pool by seed, independent of tier: redoubt (waves) and keep +
  lair (boss) are its first two definitions.

**Seed**
- **One mission seed builds everything** - layout, contents, every stream. Attempts drift apart through
  play (crates are dealt on opening), which satisfies L without a second seed. Pillar 6 holds.
- **Daily**: seed from the **UTC** date + tier; three different maps a day, not one at three strengths.
  The clock is read in `core` only.
- **Random / shared**: a short code (about 8 characters, no 0/O/1/I) that **includes the tier**; shown on
  the result screen and the menu; RANDOM draws one, ENTER CODE takes one. Dailies have codes too.
- No compatibility across builds (F).

**Tier** - numbers only, never which strains (strains belong to the biome):
enemy count (% of a biome's ranges), enemy health (on top of `sim.swarm.healthPercent`), enemy damage,
adaptation pace (replaces the `Scenario.adapting` scaffolding for missions only), nest period. Squad,
reserve, crates and medkits are the same on every tier. One definition per tier in
`assets/sim/missions.properties`, read in one place.

**Biome definition** (a kind as data, G0008): ground (base terrain, patches, how much rock), roster
(strains, how - loose, nests, asleep - and count ranges), furniture (2-3 points - amended, below - crates, medkits,
barrels, rabbits/food), a width range. No turrets or mines outside a finale. First stage ships **plain,
quarry, brake, anthill, badlands + redoubt** (03); the rest of the Adventures' biomes (dust, growth, mire, ashes, scrub, sand,
anthill, fen, cut) and the keep follow one `new-biome` run each.

**Nests** (amends G0031): wake when somebody on the player's side is **in the nest's biome and within
`wakeRadius`**; a map with no biomes is one biome, so every existing scenario is unchanged; both
Adventures declare their biomes from their walls. New key `sim.nest.firstBroodSeconds` (about 6): the
first brood comes soon after waking, then every `periodSeconds`. Tier scales the period.

**Winning and losing**
- The **objective** is the finale's only (hold the redoubt / kill the boss). **Control points are not an
  objective** but the goal line leads through them, and the result screen counts them taken x/y.
- Failure is G0055 as it stands. A minimal result screen: success/failure, points x/y, time, RETRY (same
  seed), MENU. Extraction, scoring and the rest stay open.
- Time is simulation ticks, first tick to objective met; pause does not count; the same on every machine.
  **During play it is shown only on the pause page**, never on the HUD.

**Where it is played**
- Main menu **DAILY MISSIONS**: the three tiers with the date, ✓ and best time; RANDOM; ENTER CODE.
  Modelled on the proving ground's page.
- **Online is core**: HOST GAME offers a scenario or a mission (daily tier / code). It travels as
  `SessionSetup.scenario` + `seed`, no protocol change.
- **Remembered** locally per (UTC date, tier): ✓ + best time. Every machine in the session when the
  objective is met writes its own. Codes are not remembered.

**The Adventures** stay hand-built as a measuring stick; their biomes become the first definitions.

## Amended

2026-10-04, in the 02/03 session, by Mateusz: **4 biomes** (was 6), each **50-58 columns** wide (was 29, the
Adventure's), 30 rows as before - fewer and bigger places, which look more like places and leave room for
something major inside one later (rooms, ticket 12). A mission is about 255 columns against an Adventure's 216.
**No biome twice in a mission** (was: never twice in a row), and 03 adds never the same base side by side.
**2-3 points a biome** (was 1-2), keeping the Adventure's spacing of a point about every fifteen columns.

Same day, ticket 13: the one-column wall between biomes becomes a **ridge** - a ragged band of rock crossed by
1-2 bent **passes** - with a **gatehouse** (a pass through a clearing) on about one ridge a mission.
