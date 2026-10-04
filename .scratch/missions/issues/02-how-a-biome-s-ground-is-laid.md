# How a biome's ground is laid

Type: prototype
Status: resolved

## Question

Which technique lays one biome's ground from a seed - base terrain, patches of a neighbouring ground,
rock features of a given density - so it reads like the hand-painted Adventure biomes (plain, quarry,
brake) and can never seal its own gaps? Run `game-patterns` first. Prototype: generate the three from a
handful of seeds, render each to an image, and react to them against `AdventureScenario`'s versions.
Settles the fields the ground part of a biome definition needs.

## Prototype (2026-10-04)

`.scratch/missions/prototype/biome-ground.html` - double-click. The Adventure's plain, quarry and brake beside
six seeds each, the anthill and badlands candidates, a whole strip, and the definitions as editable keys in
the form ticket 03 settled. The generator module in it is what lifts into `map`; the page stays as the
measuring tool `new-biome` names for porting an Adventure biome.

**Tried**

| Coat | Technique | Reads like the Adventure? | Gaps |
|---|---|---|---|
| rock | **outcrops**: a bar, sometimes an arm bent off one end, placed by dart throwing, `clearance` free tiles from all other rock | yes - the Adventure's own shape, parametrised | sealed never: bars and Ls enclose nothing, and spaced they cannot ring anything. Flood fill after each outcrop as the net - 0 rejections in 1500 biomes + 300 strips |
| rock | cellular automaton (4-5 rule) | no - caves and worms, not rock bent round a corner; density 4-18% at one setting | needs a repair pass (open doors, seal pockets) |
| rock | **crags**: white noise smoothed, thresholded at the density quantile, repaired (doors, clearance, pockets) | not the Adventure's look - irregular masses - but kept as a second coat for biomes that want it | 0 failures, 0 slots; density 9.3-10.3% for 10%, about 0.6 tiles repaired a biome |
| patches | rectangles | yes, for small (3-4) patches of colour | - |
| patches | ragged rectangles | no - a 3-4 patch frays into a cross | - |
| patches | **grown blobs** (Eden growth, best of three toward the middle, one smoothing pass) | better than the Adventure's squares for thicket | - |

**Numbers** (500 seeds a biome, Adventure in brackets)

- count ranges: quarry rock 10.5-16.7% p10-p90 (9.5%); brake thicket 11-18.5% (18.6%).
- **density** (lay until a fraction of the biome is done): quarry rock 10.1-12.2%; brake thicket 17.3-20.8%.
- 0 cut-off doors, 0 pockets, 0 one-tile slots with `clearance=2`. The Adventure's quarry has 3 slot tiles.
- About 1.5 ms per 7-biome strip in a browser.

**Found on the way**

- Two biomes with the same base dealt side by side read as one stretch (plain -> brake -> plain): the wall is
  the only divide. The Adventures never do it.
- Patches of a `neighbour` ground (the base of the biome on the nearer side) do by rule what the Adventure's
  comment says it does by hand.
- `GroundDef` already names what standing on a terrain costs; the biome's ground record needs another name.

**Verdict** (confirmed in the 03 grilling): outcrops for rock, and crags as a second rock coat; rectangles or
grown blobs for patches; a density for how much; a global clearance of 2. The fields are ticket 03's.
