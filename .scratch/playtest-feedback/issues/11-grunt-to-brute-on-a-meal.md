# A grunt that has eaten a man becomes a brute

Type: grilling
Status: open
Blocked by: -
Section: Enemies

## Question

A grunt that **feeds** on a trooper (one, by default) **evolves** into a **brute** - with a short, drawn
change during which it is still a grunt with a grunt's health and can be shot dead, and only at its end a brute
at full health. What does "eats a trooper" mean today, and where does the change window live?

## From Mateusz (translated)

> BUG/Change: a grunt that has eaten a trooper (1 by default) should change into a BRUTE. During the short,
> animated change it should be vulnerable to shots - we keep the grunt's HP until the last moment, and after the
> change there is a BRUTE with full HP.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-system`. `docs/glossary.md` *evolve*, *feed*, *strain*, *charge* (a unit part-way to
evolving is already on the charge channel), G0026 (*finishing a man who is down* is ruled out - so what is
eaten?), G0053, the feed and evolve code.

## Open

Why it is filed as a bug - what it does now; what counts as a meal of a man; the window's length and look on
the **charge** channel; whether a stun breaks it.

## Done when

Built, keys with values, invariant rows, `verify.sh` green. Ask for a scenario.
