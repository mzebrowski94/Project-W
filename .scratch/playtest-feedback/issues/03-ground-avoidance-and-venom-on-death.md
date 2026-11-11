# Ground worth going round, and venom on death

Type: grilling
Status: open
Blocked by: -
Section: Alien behaviour - bugs

## Question

Two items on one system - **avoidance** of ruined ground:

1. **Bug**: units walk into **fire** and **venom** too often instead of going round. A defect in how ground
   is given room (G0046), or its tuning?
2. **Adaptation**: aliens leave a small **venom** **slick** where they die; a trooper steps round it on a
   **chance** (high).

Fix first, then the adaptation on top. "Leave a spatter on dying" is the mechanism 12's leech reuses, so it is
a declaration any strain can name, not a grunt special.

## From Mateusz (translated)

> BUG: units too often walk mindlessly into fire or venom without avoiding it.
> Adaptation: enemies leave venom when they die (a small puddle - troopers should roll whether to avoid it,
> high chance).

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-system` / `new-kind`. G0034, G0035, G0046 (ground is given room by whoever it can
catch), G0039 (chance), G0018 (things explode by dying - the one death reaction), `AvoidanceSystem`,
`AiChance`, the *breed* effect in `docs/glossary.md` *Upgrades*.

## Conflicts to settle

- G0035: venom is *walked through*. A trooper stepping round it on a roll amends that for men.
- G0046 Do not build: *avoidance asking whether something is a slick* - the roll rides on the existing
  declaration, never a type check.

## Done when

Both built, keys in `assets/sim/*.properties`, a decision record for the venom amendment, `verify.sh` green.
