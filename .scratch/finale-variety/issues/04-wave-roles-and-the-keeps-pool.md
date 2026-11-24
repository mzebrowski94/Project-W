# Wave roles and the keep's pool

Type: task
Status: claimed
Label: ready-for-agent
Blocked by: 03 (same block, same `FinaleDef`, same stream)
Spec: [spec.md](../spec.md) answers 6-7
G-record: **G0120** (exists) - its enemies half
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-biome`

## What

Vary who comes in a finale: the redoubt's waves by **role**, the keep's six nests from a pool of its own, the lair's guns
from a list.

## Settled (2026-11-24, not provisional)

- **Roles:** a redoubt wave keeps its time and its mouths (`nw+se` and all) and names a role in place of a strain. The
  block defines each role once - fodder, fast, ranged, heavy - as strains with a count range each, e.g.
  `roles.fast=runner 7 9; jaguar 4 6; leech 5 7`. The finale's stream deals one member per wave. The count is drawn as
  today, through `Roster.count`, tier's `enemyPercent`, players and strength (G0063, G0106, G0119), no ramp.
- **Each finale names its own strains** (G0064's rejection of wave strains drawn from the march stands).
- **The keep's nests:** each of the six fixed spots is dealt a strain from the keep's own pool of about nine strains that
  walk (grunt, spitter, runner, frog, ant, brute, sludger, slinger, magma spitter - check each walks and has a nest
  definition), **no strain more than twice**.
- **The lair's guns:** dealt from the keep's list, none twice; how many is ticket 03's range.
- **Nothing here moves with the tier** but the wave counts, as today.
- Dealt after ticket 03's rules, from the same stream, so 03's placement does not move when a role list changes - or say
  why not.
- Glossary *role* is written; use it. Fill in G0120's *Enforced by* for this half.

## Open for the implementer

- The four roles' members and ranges. Start from today's schedule: each wave's current strain belongs to its role, with
  today's range, so the old schedule is one deal among many; add members from strains already in the game.
- A strain whose nest or pack does not suit a mouth (a rooted or burrowing strain): leave it out of the roles, say which.

## Read first

- `CLAUDE.md`.
- `docs/design/invariants/aliens.md` (nests, lairs, the boss), `squad-and-objectives.md` (waves), `progression.md`.
- G0031, G0048, G0062, G0063, G0064, G0087, G0093, G0120.
- `assets/sim/finales.properties`, `assets/sim/units.properties`, `FinaleDef`, `Mission`, `Roster`, `FinaleDefTest`,
  `MissionTest`, `LairTest`.

## Done when

- Tests hold: one member dealt per wave from its role, the same from one seed; no keep strain more than twice; no gun
  twice; counts still tiered as before. `verify.sh` green.
- `## Answer` gives two redoubt and two keep codes that deal visibly different enemies.
