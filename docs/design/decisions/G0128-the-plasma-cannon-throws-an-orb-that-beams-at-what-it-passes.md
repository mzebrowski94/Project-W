# G0128: The plasma cannon throws an orb that beams at what it passes

**Status.** accepted

**Trigger.** A new found gun; a shot that shoots; a shot that goes off by what it has spent.

**Enforced by.** `PlasmaTest` - `.throwsOneOrbAPullAndRunsItsHeatToTheStop`, `.beamsAtTheThreeNearestAliensInItsReachAndNoOthers`,
`.neverBeamsAtAMan`, `.stopsABeamOnAnAlienInTheWayOfTheOneItIsAimedAt`, `.stopsABeamOnSceneryInTheWay`,
`.passesAManInTheLineOfItsBeamHarmlessly`, `.goesOffOnTouchingAnAlienInABlastOfItsOwnDamage`,
`.goesOffOnTouchingAManOfOursAndItsBlastWoundsHim`, `.goesOffAtTheEndOfItsFlightWithNothingInTheWay`,
`.goesOffOnceItsBeamsHaveLandedItsPower`, `.spendsItsPowerByWhatItsBeamsLandOnTheLivingAlone`,
`.carriesWhatIsLeftOfItsPowerThroughASnapshot`; `SystemOrderTest.beamsFromAnOrbStraightAfterTheLimpetBeforeTheDeathChain`.

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

## Amended by

Ticket 02 of the AFK run (2026-11-24), building this record - provisional, for review:

- **The orb is a shot on nobody's side**, as a limpet is, so it goes off on our men; its `Orb` carries the side it was
  thrown from, and its beam shots are on that side, so the collision rule's own *nothing happens within a side* passes
  them through our men - the laser's way with a trooper in the line, checked and matched. Its own touch is a harmless
  blow (damage 0, harm none, G0057); the blast is the hurt. *Turned down:* a player-side orb with a "touches our men"
  exception in the collision rule.
- **Only the ball collides**: `projectileRadius` is its body; the ring is drawn outside it and touches nothing
  (Mateusz, mid-run).
- **Its beam shots inherit the gun's damage scale**, folded at the throw in the one `gun_damage` read (`Guns.volley`).
- **Power is drained where the blow is recorded**: `Damage.land` returns what it recorded and `CollisionSystem` takes
  that off the orb named on the shot (`Projectile.beamedFrom`), on anything not in its throes.
- **A gun's blast takes the gun's own `blastDamage`** (G0130): the orb's is 300 - "moderate", a grunt's death and not a
  brute's - where a rocket's and a limpet's stay 1000. *Turned down:* the orb's blast at `sim.blast.damage`, which is not
  moderate.
- **`OrbSystem` sits straight after `LimpetSystem`**; picking ignores lines of sight - a beam into rock stops on the
  rock and spends nothing.
