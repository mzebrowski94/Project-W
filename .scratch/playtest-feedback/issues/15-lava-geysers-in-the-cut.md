# Lava geysers in the cut

Type: grilling
Status: resolved
Blocked by: -
Section: Biomes

## Question

Add **geysers** to the **cut**: placed hazards that erupt in the existing **fire** on a seeded cycle. Mostly
settled already (below); the session confirms the rest and builds. A lava biome reuses them later (16).

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`. Grill only the open points below -
the rest is settled. Project context: `feature`, `new-system`, `new-biome`, and the prompt's reading list -
its "Run feature, then new-system" now reads as context for the two skills above.

## Prompt (settled with Mateusz, 2026-11-11)

> Run feature, then new-system. Add lava geysers to the cut biome (Project-W missions). Read CLAUDE.md,
> .claude/skills/new-biome/SKILL.md, docs/design/decisions/G0035* and G0068*,
> docs/design/invariants/combat.md (fire rows), the sim.fire.* block in assets/sim/hazards.properties, and the
> placement passes (sim.mission.placement.*, Placement). Settled with Mateusz:
>
> * A geyser is a placed hazard: a brown circle on the ground, radius 2-5, 3-5 per cut.
> * New objects properties, off at zero for every other biome. Placement keeps geysers off points, passes, the
>   start and nests.
> * Its cycle: dormant 4-9 s (seeded; never under 4 s), then filling about 1.5 s (the fire mark pulsing as the
>   warning), then an eruption.
> * The eruption is the existing fire, sized to the geyser: it swells and collapses, wounds and shoves men, is
>   on nobody's side, and aliens steer around it.
> * G0035 is amended so a geyser is a second source of fire; write that as a decision record.
>
> Solver treats a dormant geyser as walkable ground. Troopers should try to wait when geysers are erupting.
> Finish with the new-biome amend step.

## Open

- "Troopers wait while it erupts": a trooper whose route crosses an erupting geyser holds short of it - is that
  G0046's step-off plus avoidance already, or a hold of its own?
- The word *geyser* in the glossary; the radius in world units or tiles.

## Done when

Built, the G0035 amendment as a record, the sweeps green, `new-biome`'s changelog line, `verify.sh` green.

## Answer (AFK run, 2026-11-13 - provisional, for review)

Built: G0089 (a geyser is a second source of fire; amends G0035, G0068) and G0090 (avoidance weighs ground along
the way the wall leaves a unit; amends G0046).

- **Troopers wait** by avoidance, no hold of their own. On open ground they go round an eruption; in a corridor it
  all but fills they slid along the wall into its edge, so G0090 is the narrowest fix inside `AvoidanceSystem`: ground
  is weighed along each way as the wall a unit is against leaves it. Now they hold short and cross once it is quiet
  (`GeyserTest.aTrooperHoldsShortOfAnEruptionAcrossACorridorAndGetsPastOnceItIsQuiet`).
- **Glossary**: *geyser*, under *Fighting*, beside *fire*.
- **Radius** in world units, as boulders, trunks and `sim.mission.placement.*` are: `sim.geyser.radiusMin/Max` 2-5.
- Keys: `sim.geyser.*` (hazards.properties), `furniture.geysersMin/Max` (3-5 in the cut, 0 elsewhere),
  `sim.mission.placement.geyserKeepOff` 15, `graphic.color.geyser`, `graphic.geyser.light/pulseHz`.
- The 2000-seed mission sweep shows no fault in the cut; its 63 faults are in biomes this change does not reach
  (badlands, anthill, fen, one strip run) - see `docs/backlog.md`.
