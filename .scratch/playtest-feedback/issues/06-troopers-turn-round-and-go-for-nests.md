# Troopers who turn round, and go for nests

Type: grilling
Status: open
Blocked by: -
Section: The squad

## Question

Two items on the trooper's attention and target choice:

1. A trooper turns and fights something coming at him from behind at short range, before it has hit him.
2. Troopers go for **nests** more readily - maybe only a longer reach on a nest as a target.

## From Mateusz (translated)

> Troopers should turn round and fight if an enemy comes at them from behind (at short range).
> Troopers should attack nests more willingly (maybe just raise their range).

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-system`. `docs/glossary.md` *Driving a unit* (facing, field of view, scan, sector,
alert, threat, clear shot), G0015 (detection is a list of tests a strain declares), G0016, the trooper's
target choice.

## Open

- Short range all round as one more detection test, or a wider **alert**? It must not make his arc
  meaningless (pillar 5: his facing is worth drawing).
- Nests: a reach on the target, a **threat** weight, or both?

## Done when

Built with tests, keys in `assets/sim/*.properties`, invariant rows, `verify.sh` green. 08 tunes on top.
