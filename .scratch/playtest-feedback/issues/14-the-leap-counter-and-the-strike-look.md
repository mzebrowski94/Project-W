# The leap counter's lock, and the strike's look

Type: grilling
Status: open
Blocked by: -
Section: Weapons and the hand

## Question

1. A **strike** that kills something mid-**leap** holds the man who struck it for a shorter **lock** than now;
   the body's **death throes** stay as long as they are.
2. The hand strike is drawn as a swing of something with corners, not a circle.

## From Mateusz (translated)

> A successful counter to a LEAP (a melee hit during the leap) should pause the defender for a shorter time (the
> enemy's body stays present as long as before).
> The attack (hand strike) should look more like a swing of an angular object - not a circle.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`. G0005 (the strike is an entity), G0013 / G0014 (the clinch), `docs/glossary.md` *lock*, *clinch*,
*strike*, *death throes*, the `sim.melee.*` block, the strike's drawing in `core` (G0003: the renderer reads a
component). Item 2 is a look only.

## Open

Whether the shorter lock is the defender's alone (a lock is on *both* parties today - the creature is dying
anyway); its number.

## Done when

The key with its value, the look, an invariant row if the clinch's changes, `verify.sh` green.
