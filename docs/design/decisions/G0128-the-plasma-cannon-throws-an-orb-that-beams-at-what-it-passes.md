# G0128: The plasma cannon throws an orb that beams at what it passes

**Status.** accepted

**Trigger.** A new found gun; a shot that shoots; a shot that goes off by what it has spent.

**Enforced by.** Tests written with the code.

**Settled with Mateusz, 2026-11-24** (grilling). Not yet built, not yet played.

**Constraint.**

- **A found gun**: a weapons cache card (`ARMOURY_PLASMA`) dealt into crates like the others, lost with the commander.
  Type `energy`.
- **One orb a reload**: each pull throws one **orb** and runs the heat bar to the stop, as the bazooka (G0058). Several
  may be in the air.
- **The orb flies straight** along the aim at about twice the commander's pace, with a flight.
- **It beams** at up to three aliens within its **reach** (about three tiles), nearest first, re-picked as it flies,
  found through `SpatialGrid`. A beam is the laser's kind of beam - a stream of short fast shots drawn as a line - and
  stops on, and lands on, the first thing in its way: another alien, scenery, rock. **Beams never pick a man and pass
  our men harmlessly.**
- **It goes off** on touching an alien, a man of ours, scenery or rock, at its flight's end, and when its beams have
  landed its **power** of damage in all: a small **ordinary blast** (G0018) on nobody's side, wounding a man, moderate
  damage.
- **Shown** (pillar 5): a green orb in a thin offset ring flickering green and white, green beams, a green blast.
- Built from what is there: the shot, the laser's beam, `Damage.land`, the blast. A new piece of machinery - the orb's
  emitter and its power - is a component and a system, and a gun that does not beam says `off`.

**Why the beams spare our men and the blast does not.** Beams choose aliens and a beam that cut through a trooper would
make the gun unusable beside the squad; the blast is an explosion, and an explosion is on nobody's side (G0018).

**Why the power is damage landed.** It makes the orb worth more in a thin crowd than a thick one - it flies further and
beams longer - and it is a number the player sees spent, not a timer.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *An instant ray cast each tick.* A second way to draw a beam beside the laser's.

**No:** *One orb out at a time.* That is the detonator's rationing, not this gun's.

**No:** *Beams that hurt our men.*

**Revisit when** play shows the orb clearing whole waves on its own, or never reaching its power.
