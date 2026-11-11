# A harder, varied redoubt

Type: grilling
Status: open
Blocked by: -
Section: Turrets and the finale

## Question

1. The **redoubt**'s **waves** are too easy: send them from two **mouths** at once.
2. Vary where its turrets, mines and **pieces** stand from mission to mission, keeping a symmetry or pattern.

How does variety fit a **finale** that is a set piece?

## From Mateusz (translated)

> Finale: the wave finale needs to be made harder somehow, e.g. waves should appear from two sides at once.
> Finale: vary the layout of turrets, mines and walls in finales (it would be good to keep some symmetry/pattern
> of turrets and mines).

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-biome` (finale family). G0064 (a finale is a set piece; Do not build *a finale laid out by
rules*), G0027, G0061, `finales.properties`, `Strip.anchor`, `docs/glossary.md` *Levels and objectives*.

## Conflict to settle

G0064 rules out a finale laid out by rules. A few symmetric hand-made layouts per finale, one dealt by the seed,
keeps it a set piece - propose that and amend G0064 if Mateusz agrees.

## Done when

Built as data, the sweeps green over every layout, the decision record, `verify.sh` green. Ask for a scenario.
