# The rift's missing jaguar nests

Type: task
Status: resolved
Label: ready-for-agent
Blocked by: -
Skills: `/mattpocock-skills:diagnosing-bugs` with `docs/workflow.md`'s failure protocol

## What

`main` is red. `.claude/scripts/verify.sh` fails in `MissionSweepTest.holdsEveryMissionToTheRules`:

```
EHJD-YK39: biome 5 (RIFT): 0 of 1 nests of JAGUAR
MHJD-YK3G: biome 5 (RIFT): 0 of 2 nests of JAGUAR
HHJD-YK3D: biome 5 (RIFT): 0 of 2 nests of JAGUAR
3 faults in 88 seeds
```

Every later ticket needs a green verify, so this goes first. Seen on 2026-11-24 after commit `b9bb537` ("carved nooks for
the rift's nests"); not yet diagnosed.

## Settled

- The sweep's rule stands: a biome lays every nest its block asks for, or is placed again (G0070). Fix the generator or
  the rift's block, never the sweep's rule.
- A fix that changes what a rift looks like or holds (fewer jaguar nests, nests outside houses) is a gameplay change:
  write it down - an `## Amended by` on G0115 or G0112, or a spare G-record (G0123 first) - **provisional, for review**.

## Open for the implementer

- The cause. Suspects, cheapest first: the nooks carved for the rift's nests in `b9bb537` taking the room the jaguar's
  nests need; the jaguar's house rule (G0112) meeting the rift's fissure maze (G0115); `Scatter` running out of
  candidates in a `MissionConfig` tier with more nests.
- Added by author: You can change rift nest settings to nest 0 1 if it will fix things

## Read first

- `CLAUDE.md`; `docs/workflow.md` (ladder, failure protocol).
- G0068, G0070, G0112, G0115.
- `assets/sim/biomes.properties` (the rift and the jaguar's blocks), `Mission`, `Scatter`, `MissionFaults`,
  `MissionSweepTest`.

## Done when

- The three codes above lay their jaguar nests, a test reproducing one of them is green, and `verify.sh` is green.
- `## Answer` names the cause, and a mission code with a rift to look at.

## Answer

**Cause.** A bare nook of radius 2 leaves its centre ~7.9 world units off the fissure round it; a hole needs its radius
plus `rockMargin`, 2.4 + 6 = 8.4. A nook held a nest only where a corridor happened to widen it. On seed 2777720
(the rift dealt last) none was widened, and the maze had no room elsewhere, so no attempt fit, on any tier.

**Built.** `sim.biome.rift.ground.clearings.nooks` 10 -> **7**, `.nookRadius` 2 -> **3**: a bare nook of radius 3 leaves
~12.7. Seven because `Coats.nooks` needs each band wider than a nook. No code change. Tests:
`BiomePromisesTest.aBareNookOfTheRiftHoldsANestKeptOffTheFissureRoundIt` (geometry, red at 10/2) and
`MissionTest.laysTheRiftsNestInANookTheMazeLeftBare` (the seed, every tier, red at 10/2). 2192-seed sweep: 0 faults.

**Provisional (AFK run), in G0115's `## Amended by`:** nooks 7 of radius 3. Turned down: `nest jaguar 0 1` (a draw of
one still falls short on such a seed, and the rift loses its nest half the time); a smaller rock margin for the rift
(G0115 rejects loosening a keep-off for one biome).

**Keys.** `sim.biome.rift.ground.clearings.nooks=7` (was 10), `sim.biome.rift.ground.clearings.nookRadius=3` (was 2).

**Look at it.** `--mission=MHJD-YK3G` (the failing seed, rift last, medium); `--mission=MHPC-VVKG` (rift first). Codes are
reprinted from the integration tip at the end of the run, since ticket 03 re-deals the finales.
