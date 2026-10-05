# new-biome - changelog

Not loaded with the skill. One dated line an amendment: the run or stage; what was wrong, missing or slow; what
changed. Git carries the diff.

- 2026-10-05, ticket 10 grilling - draft settled: finale family added, sweeps split by module, Java measuring,
  printed codes to play, the amend step.
- 2026-10-05, stage 3 (biome ground) - missing: what a density actually lands at (half a piece over; patches read
  low under heavy rock; a greedy coat stops short silently) and the coats test's tolerance; slow: the
  distributions took a throwaway probe test, which `BiomeMeasureTest` (`STAGE 7`) replaces - added both to *Ground*
  and *Tests*. Nothing wrong.
- 2026-10-05, stage 4 (the strip) - wrong: the big-sweep command named `:sim:test`, which has no sweep yet and
  fails the filter, and lacked `--rerun`, without which Gradle skips the sweep as up to date; missing: what the
  strip does round a biome (faces, aprons, a rock share measured on the rect reading the faces), `StripSweepTest`'s
  checks and output, the five blocks' new home `TestBiome`; slow: checking a strip by eye took a throwaway ASCII
  dump - not kept, since a `new-biome` run looks through a played code. Fixed in *Ground* and *Tests*.
- 2026-10-05, stage 5 (houses and gatehouses) - wrong: *Ground* said a coat is always a density, never a count -
  houses are counted; missing: the houses coat, its keys, what it takes from the other coats, the gatehouse, and the
  sweep's house checks and summary; slow: checking a house by eye took the throwaway ASCII dump again - still not
  kept, stage 5 being the last stage with no code to play. Fixed in *Fit*, *Ground*, *Tests* and *Reviewer checks*.
- 2026-10-05, stage 7 (first biome stage; draft moved here) - wrong: "port at x1.8" - the ported rosters and the
  grilled placement numbers never fit together (63 of 64 missions short even on easy), now *counts fit the room*
  with the retry and the cut (G0070); the draft put `MissionSweepTest` in `sim`, where no code can be printed - it is
  in `core`, over the shipped pool through `StripFaults` as a `map` test fixture; "`STAGE 7` swaps `TestBiome` for
  the table" - `map` cannot read the table, so `TestBiome` stays `map`'s fixture and the shipped pool is swept from
  `core`; the big-sweep command needs `--tests` and `--rerun` after each task. Missing: placement's passes and
  where a block's lines go, the room each biome leaves and how to fit a block to it, `BiomeMeasureTest.ports()`,
  `--mission` to play, the promises test's home. Slow: finding the room took three throwaway probes - now
  `BiomeMeasureTest.printsTheRoomEachBiomeLeavesForCreatures`. Fixed throughout; *Finale* waits for stage 8.
