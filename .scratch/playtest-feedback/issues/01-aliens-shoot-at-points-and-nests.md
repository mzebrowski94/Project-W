# Aliens shoot at control points and nests

Type: task
Status: open
Blocked by: -
Section: Alien behaviour - bugs

## Question

Why do aliens fire at **control points** and **nests** - and keep at it, as if either were on the player's
side - and what is the smallest fix that makes them fire only at men? Mateusz starts the round here.

## From Mateusz (translated)

> BUG: enemies for some reason shoot at control points and nests, and focus on attacking them as if they
> were their enemy.

## Read first

`mattpocock-skills:diagnosing-bugs`. `docs/glossary.md` *Driving a unit* (aggro, rouse, hold ground,
settle). G0016 (aggro latches a place), G0026 (who an alien hunts), G0027 (the post). The ranged and barrage
aiming path and `Perception`'s published target.

## A lead (read, not run)

A **rouse** sends what is in earshot to a *place* - the point being taken, the nest being hit - and G0016
latches that place, not a man. If a shooter aims at its latched place while nobody stands there, it fires
at exactly the point or the nest. Check that first; then whether points or nests carry anything a target
query reads as the player's side.

## Done when

The smallest reproducing test first (a roused spitter by an empty point fires nothing at it), then the fix;
`verify.sh` green. An invariant row if the rule was unwritten.
