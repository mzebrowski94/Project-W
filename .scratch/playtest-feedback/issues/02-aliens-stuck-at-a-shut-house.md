# Aliens stuck at a shut house

Type: task
Status: open
Blocked by: -
Section: Alien behaviour - bugs

## Question

Grunts and the rest jam against a **house** whose **door** is shut, trying to route through it although
nobody is inside to leap at. What should a route do with a shut door when the goal is not behind it, and
what is the smallest change that sends them round?

## From Mateusz (translated)

> Enemies ("Grunts" etc.) get stuck trying to go through rooms (they don't try to break in because nobody is
> inside) - in that case they should pick another path.

## Read first

`mattpocock-skills:diagnosing-bugs`. G0069 and ADR 0022 (a door is a wall that opens; *a way shut by a
door*; a route still runs through it so a hunter shut out leaps at it), `Solid`, `FlowField`,
`docs/glossary.md` *door*, *house*.

## Conflict to settle

G0069 keeps a route through a shut door on purpose, so a hunter shut out leaps at it. The fix keeps that and
drops it only when the goal is not inside - an amendment if it changes the record.

## Done when

A test with a shut house between a grunt and a man in the open: the grunt walks round. `verify.sh` green.
