# A lava biome with upgraded spitters

Type: grilling
Status: resolved
Blocked by: 15
Section: Biomes

## Question

A new **biome** on lava, using 15's **geysers**, with an upgraded **spitter** strain in its roster. What is its
ground, its roster and furniture, and what is the upgraded spitter - a new strain as data, or the spitter with an
adaptation?

## From Mateusz (translated)

> A lava biome + upgraded spitters in it.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-biome` and `new-kind`. 15's answer. G0068, G0070, the cut's and fen's blocks in
`biomes.properties`, `docs/glossary.md` *spitter*, *fire*, *biome*.

## Open

Its ground (a new **terrain**? lava as ground nothing stands on, or only a colour?), how it differs from the cut
now that the cut has geysers, what "upgraded" spitters do (a fire spit? a slick of fire where it lands?).

## Done when

Built as data, the sweeps green, the new-biome amend step, `verify.sh` green. Mateusz judges a printed code.

## Answer (AFK run, 2026-11-13 - provisional, for review)

Built: G0091 (lava is ground nothing stands on, and the caldera is laid on it; amends G0089) and G0092 (the magma
spitter is a strain whose spit leaves fire).

- **Ground**: `Terrain.LAVA`, impassable. The codebase has one solidity (`Solid`, for moving, shooting and seeing),
  so lava stops shots as rock does; shots across a pool are deferred (G0091, `docs/backlog.md`). Laid as **crags**
  with a new `crags.terrain` (`rock` in the anthill and badlands, `lava` here), so the crag repairs keep every pass
  open - a patch has no repairs and `PatchGround` still refuses solid ground. Drawn `graphic.terrain.lava` b3300f.
- **The caldera** (`BiomeKind.CALDERA`): ash, lava crags 0.10 smoothed 5, neighbour blobs 0.02, no house, no tree,
  0-2 boulders, 4-6 barrels, 5-7 geysers, crates 2-3, a medkit. Roster: magma spitter 3-5, spitter 2-3, grunt 6-8,
  ant 2-3, a grunt nest. Dealt from the pool as any biome; never beside the badlands (same ash base).
- **The magma spitter** (`UnitKind.MAGMA_SPITTER`): the spitter's block key for key but health 260 and
  `ranged.gun=magma_spit` - a spit with `blastRadius` 4 (`blastHarm=wound`, 1.3 s, speed 36) - so it leaves the
  ordinary neutral fire (G0035) and wears the charged ring. Deep violet body, molten-orange shot. The caldera's
  candidate signature strain (ticket 19, open).
- 2000-seed sweep: no fault in the caldera; its 70 faults are the known ones (badlands 52, anthill 15, the strip
  run at (237,1) on three tiers). Mission code to judge: `MAAA-AGDN` (medium, the caldera first).
