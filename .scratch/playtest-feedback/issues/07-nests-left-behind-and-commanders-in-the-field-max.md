# Nests left behind, and commanders on the field

Type: grilling
Status: open
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
