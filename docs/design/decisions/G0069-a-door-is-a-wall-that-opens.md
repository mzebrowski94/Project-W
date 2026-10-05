# G0069: A door is a wall that opens, and a creature shut out leaps at it

**Status.** accepted

**Trigger.** Adding or changing what stands in a doorway; what opens, breaks or blocks one; what a creature does
when its way is shut.

**Enforced by.** `DoorTest` (opened on the held key, lost to letting go, a wound or going down, last on a tie; broken
by a blast, a rocket and the flamer, whole under a rifle; sunk and gone), `ShutDoorTest` (ADR 0022's), `DoorSiegeTest`
(a hunter shut out leaps at it, is thrown back, shakes the ground, breaks it in the end; one that cannot leap waits),
`TrooperDoorTest` (one trooper opens while the others watch their sectors, no faster with a commander), and
`MissionTest.standsAShutDoorInEveryDoorwayOfEveryHouse`. Missions ticket 14 holds the reasoning and the keys; ADR 0022
how a shut door is solid.

**Amends G0026.** A creature hunts whoever is nearest by route, and a route runs through a shut door; a hunter whose
way is shut by a door leaps at the **door**.

**Constraint.**

- **One thing**, a `sim.door.*` block as a nest's or a mine's: an entity with a `Handle`, its doorway, its health and
  its opening work. Every door starts shut, houses and gatehouses alike, and is **never shut again**.
- **Shut, it is a wall** to everything that moves, shoots, explodes or looks (ADR 0022). A route still runs through
  it.
- **Opened by hand** on stabilise's rules: held action key, arm's length, `openSeconds`; a wound, going down or a
  step away loses the work. A commander and at most one trooper together, never faster than one. The bar is the
  door's, green, over it.
- **A trooper opens whatever door his route needs**; one per door, the first there, a tie to the lower handle, and
  the others whose route needs it wait by it and watch their sectors.
- **A creature shut out leaps at it**: aimed once, `leapDamage` landed through `Damage.land`, thrown back, a shake
  felt by commanders near it. A strain that cannot leap waits.
- **Broken by a leap, a blast or a heavy gun** - nothing else. A blast breaks it outright.
- **Gone once open.** An opened door sinks into the ground - its squares seen shrinking for `sinkSeconds`, then
  nothing - and leaves the field; the doorway is open ground on the tick the work is done, the sinking only a look. A
  broken door disappears on the tick it breaks.

**Why a wall that opens.** A door that only some blows reach and that crowds can lean through would be a wall with
exceptions. A door that is the wall until it is not is learnt in one look, and the fight it makes is the one at the
doorway left open.

**Why the hunter's way, not "somebody inside".** An alien already walks the route to whoever is nearest (G0026); one
hunting a man behind a shut door arrives at it without being told. A test of who is inside a house would be a second
answer to "whom does it want", and would need the house's rectangle in `sim` for nothing the route does not say.

**Why only leaps, blasts and heavy guns.** A door is opened, not shot open: a rifle that breaks one turns every house
into a wall of shooting. Leaps let a siege end on its own, so a shut door is a delay, never a refuge.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A door that shuts again.* A room that can be sealed behind you is a refuge, and pillar 2 has none.

**No:** *An open door left standing beside its doorway.* It reads as a door that could be shut again, and it is
scenery in the way of the fight at the doorway.

**No:** *A "someone inside" test* - the house's rectangle in `sim`, or a flag on whoever entered. The route already
says it.

**No:** *Every gun hurting a door.* See *why only*.

**No:** *A damage number per strain's leap.* One number on the door; a brute is no better at doors than a grunt
until play says otherwise.

**No:** *Openers adding up*, or *more than one trooper at a door.* Two men at one door do not open it faster; the
rest keep a lookout.

**No:** *A door kind table* while one door fits every doorway. A second door is a constant, a definition and a block
then (ADR 0008).

**No:** *Doors drawn open or shut by the seed.* Every door starts shut; a gatehouse is a stop the squad gets through
on its own.

**Revisit when** a door is wanted that is not 3 wide, a strain plays as wrong against doors, or a gatehouse's two
openings read as a chore rather than a beat.
