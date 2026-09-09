# Map and view: what this work is, and is not

**This doc expires.** It covers one run of work - the tile map, terrain, the camera and the zoom -
and is deleted when the last stage lands. Nothing here is a permanent rule; the permanent parts are
in ADR 0009, G0008, G0009 and the invariants table.

Agreed with Mateusz on 2026-09-08.

## Why this work exists

The goal is **generated map fragments**. Everything here is groundwork for that: a grid to generate
into, terrain to make the pieces different from each other, and a camera that can show a level that
is larger than one screen - because a generator confined to a single screen has almost no room to
produce anything the player would recognise as different places.

## The decisions this settled

| Question | Answer |
|---|---|
| Is a level allowed to be bigger than the view? | Yes. Pillar 4 amended - G0008. |
| Where does map generation live? | Its own module, depending on nothing - ADR 0009. |
| Does anything move or think in tiles? | No. The grid lays a level out and nothing else - G0009. |
| How big is a tile? | 5 world units. 32 x 18 of them is the 160 x 90 the game has always used. |
| Can terrain be impassable? | Yes, and the solver clamps against it exactly as it already clamps against the arena edge. |
| Does terrain do anything else to a unit? | Not yet. The hook is designed in and left empty. |
| How does zooming out work? | A toggle between two fixed levels. |

## Stages

Each stands alone and the work can stop after any of them.

1. **The `map` module and the ground.** Terrain, tile map, region partition, terrain rendering.
   No gameplay change at all. *(done)*
2. **Impassable terrain.** Rock the constraint solver pushes bodies out of. *(done)*
3. **Terrain-gated placement.** Scatter that reports what it could not place, positions jittered
   inside their tile, and what may spawn on a terrain decided by the scenario. Scenarios ported. *(done)*
4. **The camera follows.** A focus marker in `sim`, a tested camera director in `core`, a map larger
   than the view, and a scenario that shows it. *(done)*
5. **Zoom.** Two fixed levels, toggled. *(done)*
6. **Generated fragments.** A later session. Stages 1-3 exist to make it cheap.

## Not in scope, with the reason

- **Pathfinding.** Walls will make "enemies walk straight at you" visibly worse. That is a real
  problem and it is not this work's - the answer is a flow field over the grid, and it needs the
  squad work to know what it is serving.
- **Terrain that slows, hurts or hides a unit.** Decided against for now: it is a new mechanic on top
  of an already large change, and it interacts with the leap and with knockback in ways worth playing
  before designing. The hook - a config block per terrain, keyed the way strains are - is designed in
  and empty.
- **A minimap.** Ruled out permanently, in G0008. It is off-screen management with a nicer name.
- **A level file format.** Maps are built in code, as scenarios are. The format question arrives with
  generation and is a third thing, neither geography nor simulation. When it does arrive, start by
  trying Tiled and `TmxMapLoader` converting into a `TileMap` - see ADR 0010, which also records why
  libGDX's tile *model* can never be ours.
- **Destructible terrain.** `TileMap` is immutable once built, deliberately. A rock that can be
  destroyed is an entity, not terrain.
- **Sprites for the ground.** Flat colours, like everything else in the alpha phase. The renderer
  already culls and merges runs, which is what a texture atlas will need.

## Scaffolding - looks like a decision, is not

- **Every region is open ground and differs only in shade.** The regions are real and the partition
  is real; that they all have the same terrain is a stage-1 stopping point, not a statement that
  terrain is cosmetic. Stage 3 gives regions their own ground.
- **Six regions, from a nearest-seed partition.** A number and an algorithm chosen to look like
  places while the renderer was being written. Neither is a decision about how levels are shaped.
- **The four terrain kinds** - plain, dust, organic, rock. Enough to build and see the system. What
  kinds the game actually wants is a question for when fragments exist and someone has played them.
- **Where rock goes.** Hand-laid in `Warren`, scattered blindly in `Skirmish` and `Expanse`. All
  placeholders for generation; none of them says anything about how levels should be shaped.
- **Which ground a region gets.** Drawn from a weighted list of four terrains. The mechanism is real;
  the weights are a guess made to see the system work.

## Found while building, worth knowing

- **A one-tile gap takes grunts and refuses brutes.** A tile is 5 world units and a brute is 5.2
  across, so it cannot be squeezed through a single-tile crack by any amount of pushing. This fell out
  of the numbers rather than being designed. It is a genuine tactical rule and it is now an open
  question in `README.md`: keep it, or make a tile at least as wide as the widest body.
- **Enemies jam on wall corners.** They walk straight at the commander, so terrain makes the existing
  pathing scaffolding visibly worse. Expected, named in `alpha-phase.md`, and deliberately not fixed
  here - the answer is a flow field over the grid and it needs the squad work to know what it serves.
