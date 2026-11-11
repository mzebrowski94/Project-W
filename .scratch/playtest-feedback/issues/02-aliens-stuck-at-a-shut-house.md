# Aliens stuck at a shut house

Type: task
Status: resolved
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

## Answer

**Cause.** A hunter's route was flooded from terrain alone (ADR 0022, on purpose, so a hunter shut out reaches the
door), and its straight-line shortcut asked the terrain too. With a house between it and a man in the open, the
shortest way ran in through the shut door and the grunt leaned on it. Reproduced both ways: man off the doorways'
line (the field), and man in line with both doorways (the shortcut).

**Decided with Mateusz: round first, the siege as fallback** - [G0075](../../../docs/design/decisions/G0075-a-hunter-goes-round-a-shut-door.md),
amending ADR 0022 (its *Routes that know shut doors* rejection reversed). Not the ticket's "goal not inside" test:
G0069 rejects a someone-inside test.

**Built.** `FlowField.over(map, passability, radius)` and `invalidate()`; `Solid.revision()` changes when the set of
shut doors does; `NavigationSystem` takes `Solid`, routes over it first, falls back to the terrain field when that
reaches nobody, and asks `Solid.isClearPath` for the straight line. `ShutHouseRouteTest` (3), two `FlowFieldTest`
cases; the open-door case proved red with the re-flood removed. `DoorSiegeTest` unchanged and green. `verify.sh` green.

**Left as it was.** A creature marching to the post (noticed nobody) still takes the terrain's route; not in the report.
