# Wave roles and the keep's pool

Type: task
Status: resolved
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

## Answer

**Built.** `WaveLine(afterSeconds, role, mouths)` with `WaveRole` {fodder, fast, ranged, heavy}; `FinaleDef.roles`
(`roles.<role>=strain min max; ...`, a `WaveLine.Member` each) deals one member per wave from the finale stream's own
`ROLES` branch; counts drawn as before (`Roster.count`, the tier's pressure, no ramp). The keep's lair names six
`hole dx dy` spots (`LairLine.Hole`), each dealt from `pool.strains` with no strain more than `pool.most` (own `POOL`
branch). The lair's caches deal their kinds from `rules.lairKinds`, none twice (own `GUNS` branch of the rules' stream),
so 03's placement does not move when a list changes. Sanity: 8 keeps raised and stepped 20 s, no fault.

**Also (Mateusz, mid-run, not provisional):** finale mines cut. Redoubt `rules.mines=4 6 ...` (was 16 20), keep `2 4 ...`
(was 8 12).

**Provisional** (G0120 `## Amended by` 9-12):
- Roles' members and ranges, listed below. Left out: leech (rooted) and worm (burrows); snake, searer and leech slinger
  to keep each role to the redoubt's weight.
- A two-mouth wave sends a member's range from each mouth. Alt: a per-wave share, the old schedule's "trimmed by a fifth".
- `hole dx dy` as a lair form. Alt: treating a `nest <strain>` line's strain as a placeholder.
- The keep's gun list is its seven guns; grenades and the detonator left out.

**Keys** (`assets/sim/finales.properties`):
- `sim.finale.redoubt.waves=6 fodder nw; 5 fodder se; 4 fast ne; 7 ranged sw+ne; 4 fast sw; 8 heavy ne+sw; 5 fast se;
  4 fast nw+se; 8 fodder sw+ne; 4 ranged nw; 8 heavy se+nw; 4 fast ne+sw`
- `roles.fodder=grunt 9 11; ant 9 11`, `roles.fast=runner 8 10; frog 5 7; jaguar 4 6`,
  `roles.ranged=spitter 4 6; sludger 4 5; magma_spitter 3 5`, `roles.heavy=brute 4 5; thumper 2 3; slinger 2 3`
- `sim.finale.keep.lair=hole 13 -6; hole 9 -3; hole 16 -3; hole 10 5; hole 16 3; hole 13 6; unit boss_grunt 13 0`
- `sim.finale.keep.pool.strains=grunt; spitter; runner; frog; ant; brute; sludger; slinger; magma_spitter`, `pool.most=2`
- `sim.finale.keep.rules.lairKinds=armoury_shotgun; armoury_railgun; armoury_laser; armoury_flamer; armoury_bazooka;
  armoury_pulse; armoury_disc`

**Play.** Two redoubt and two keep codes with different enemies are printed from the integration tip at the end (below).
