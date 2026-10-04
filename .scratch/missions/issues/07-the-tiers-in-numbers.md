# The tiers in numbers

Type: grilling
Status: resolved
Blocked by: 03

## Question

The first numbers for easy, medium and hard - enemy count %, health %, damage %, adaptation every-seconds
and per-cards, nest period % - and the ramp factor per biome position. Where damage % is applied so it
goes through `Damage.land` and `Whole` (G0054, G0057) rather than beside them.

## Answer

Grilled 2026-10-04 (G0063). A tier is **four numbers on the swarm** plus one ramp shared by every tier.

**Enemy damage is not a tier number.** A man reads a blow's harm, never its amount (G0053, G0057), so an
alien's damage reaches only what has health on the player's side - turrets and rabbits, and turrets stand
only in a finale. A key that moves nothing a man feels would be a key that lies; 01 is amended.

**The numbers**

| | easy | medium | hard |
|---|---|---|---|
| `enemyPercent` - every roster count, loose and nest | 75 | 100 | 125 |
| `healthPercent` - on top of `sim.swarm.healthPercent` | 85 | 100 | 115 |
| `nestPeriodPercent` - of `sim.nest.periodSeconds` (30 s) | 130 (39 s) | 100 | 75 (22.5 s) |
| `adaptation.everySeconds` | 0 (off) | 240 | 150 |
| `adaptation.perPlayerUpgrades` | 0 (off) | 4 | 3 |

`sim.mission.rampPercent=10`, one for every tier: a biome's counts are x(1 + 0.10 x position), position 0-3
- x1.0, 1.1, 1.2, 1.3. The finale is not ramped; its waves take the tier's `enemyPercent` through its own
definition (04). Ticket 03's blocks stay as written, so medium's first biome is the ported block at the
Adventure's density, and hard's last is x1.625 - which feeds "how big the field gets", not this ticket.
Recalibrate after play.

`firstBroodSeconds` and `wakeRadius` are the same on every tier (G0062). Adaptation pace replaces
`Scenario.adapting` for missions only. **Weak until the pool grows**: the swarm has one adaptation
(`rapid_spit`, 3 ranks), so hard runs it dry in about seven minutes and medium in about twelve.

**The one-swing guarantee holds at every tier.** `sim.melee.damage` 190 -> **220**: a hard grunt is
150 x 125% x 115% = 216, still one swing; an easy brute is 250 x 125% x 85% = 266, still more than one.
Every relational guarantee in `SimConfigTest` runs at each tier.

**Where each is applied - one rounding, one reader**

- **Health**: `SwarmConfig` gains `tierPercent`, 100 off a mission. `SimConfig.healthOf` and `.nestHealth`
  stay the only readers and call a new `Whole.percentOf(base, percent, percent)` - both percents multiplied
  in integer arithmetic, rounded once (G0054). Every strain, nest and the boss, finale included.
- **Counts**: `Roster.count(rng, pressure)` scales a line's **bounds** with
  `Whole.percentOf(bound, enemyPercent, 100 + rampPercent x position)` and draws between them. `Whole` is
  the rounding rule for counts too - its javadoc widens to "a whole quantity" - and its never-to-zero keeps
  every roster line on easy.
- **Nest period**: folded into the nest config before the world exists, reaching ticks through `Ticks`.
- **Damage**: nowhere - see above. `Damage.land` and `Whole` are untouched by tier.

**As data** (a kind, G0008): `TierKind` (EASY, MEDIUM, HARD); `TierDef(enemyPercent, healthPercent,
nestPeriodPercent, float adaptationEverySeconds, int adaptationPerPlayerUpgrades)` as `Defs<TierKind, TierDef>`, root `sim.mission.tier`, in
`assets/sim/missions.properties`; `sim.mission.rampPercent` beside it. **`MissionConfig` is the one reader**:
`pressure(tier, position)` for counts, and `apply(tier, SimConfig)` folding swarm health, nest period and
adaptation into the config the world is built from - so every system reads the config it reads today.

```properties
# Ramp: a biome's roster counts rise by this per cent per position (0-3); the finale is not ramped.
sim.mission.rampPercent=10

sim.mission.tier.easy.enemyPercent=75
sim.mission.tier.easy.healthPercent=85
sim.mission.tier.easy.nestPeriodPercent=130
sim.mission.tier.easy.adaptation.everySeconds=0
sim.mission.tier.easy.adaptation.perPlayerUpgrades=0

sim.mission.tier.medium.enemyPercent=100
sim.mission.tier.medium.healthPercent=100
sim.mission.tier.medium.nestPeriodPercent=100
sim.mission.tier.medium.adaptation.everySeconds=240
sim.mission.tier.medium.adaptation.perPlayerUpgrades=4

sim.mission.tier.hard.enemyPercent=125
sim.mission.tier.hard.healthPercent=115
sim.mission.tier.hard.nestPeriodPercent=75
sim.mission.tier.hard.adaptation.everySeconds=150
sim.mission.tier.hard.adaptation.perPlayerUpgrades=3
```

`answerWeight` stays the global `sim.adaptation.answerWeight`; `apply` builds the `AdaptationDef` from the tier's two and it.
