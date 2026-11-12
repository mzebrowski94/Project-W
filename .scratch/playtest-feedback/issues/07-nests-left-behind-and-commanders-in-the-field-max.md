# Nests left behind, and commanders on the field

Type: grilling
Status: resolved
Blocked by: -
Section: The squad

## Question

Two "how many on the field" items:

1. A **nest** with no trooper or commander anywhere near - a **biome** left behind - breeds far, far more
   slowly.
2. Commanders count towards the field's maximum of men (troopers and commanders together).

## From Mateusz (translated)

> Nests near which there are no troopers/commanders any more (e.g. a biome left behind) should spawn units
> much, much more rarely.
> Players should count towards the limit of the maximum number of troopers/commanders on the map.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-system`. G0031 (a nest; *a nest that goes back to sleep* is ruled out), G0062 (wake by
biome and radius), G0063 (nest period per tier, `MissionConfig`), G0055 (field and reserve, the field max,
the **muster**), `docs/glossary.md` *nest*, *reserve*, *muster*.

## Conflicts to settle

- G0031 rules out a nest that sleeps again. A slower period is not sleep - say how it differs: what "near"
  is, and how fast it comes back to pace.
- Counting commanders shrinks the squad's room and what a crate's reinforcements fill (G0030 / G0055); per
  **slot**, sized from capacity (pillar 7). Does a commander waiting in reserve count?

## Done when

Both built, keys in `assets/sim/*.properties`, G-record amendments, `verify.sh` green.

## Answer

Built without a grilling (Mateusz away); every record change is marked provisional, for review.

1. **A nest left behind** (G0081, amends G0031): an awake nest with no man - trooper or commander, down or not -
   within `sim.nest.leftBehindRadius=140` (the wake radius), biome or none, counts down
   `sim.nest.leftBehindSlowdown=5` times slower: a 30 s period becomes 150 s, about a creature a minute per
   nest. Not sleep - it still breeds - and it is back at pace the tick a man is that near; nothing made up or
   held over. Men are filed in a `SpatialGrid` once a tick in `NestSystem`. Radius 0 is off. Five is the middle
   of 4-6. Tiers do not touch either key (G0063 unchanged).
2. **Commanders count towards the field's most** (G0081, amends G0055, notes G0030): every commander on the field,
   any slot, down or not, takes a place - `ReinforcementSystem.onTheField()` is the one definition, read by the
   crate, the muster and the HUD's `ARMY` count (`Simulation.menOnField`). A commander waiting to come back
   does not count, and a returning commander is never kept off a full field. Numbers unchanged (10 + 6 = 16);
   open: in a large co-op game the field starts nearly full - revisit with a headroom per seat if it starves.
