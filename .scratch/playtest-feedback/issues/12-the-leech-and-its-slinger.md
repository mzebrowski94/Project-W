# The leech and the slinger that throws it

Type: grilling
Status: open
Blocked by: 03
Section: Enemies

## Question

A new small strain, the **leech**, and a **slinger** that **slings** leeches instead of runners. A slung leech
that lands on a trooper clings to him and, after a while, goes off - a kill - leaving **venom** round it, as a
frog's shot does. One that lands with nobody close by sits still and goes off after a moment, leaving venom.
What is it, as data on what exists?

## From Mateusz (translated)

> New Slingers that throw "leeches" (described below).
> Leeches - a new small enemy type (purple) that, once thrown, attaches to a trooper and after some time explodes
> (lethal hit), leaving venom (like the frog's shot) around. A leech thrown on the ground, if nobody is in close
> contact, does not move, and after a while explodes leaving venom.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-kind` and `new-system`. G0074 (the slinger, riders as a count), G0045 (the grip - the
closest thing to clinging), G0018 (things explode by dying), G0034, 03's answer (a spatter on dying),
`docs/glossary.md` *Units and sides*.

## Open

- Purple is the **spitter**'s colour - pillar 5 wants the leech legible apart from it.
- Clinging: a **grip** that does not carry, or something new? Can the man or his squad get it off (a strike, a
  shot)? Does a commander get one?
- Whose side the venom takes (G0034: venom takes the frog's side).

## Done when

Both built as data where they can be (G0008), glossary rows, `verify.sh` green. Ask for a scenario.
