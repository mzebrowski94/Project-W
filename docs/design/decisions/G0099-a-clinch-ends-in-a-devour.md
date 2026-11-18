# G0099: A clinch ends in a devour

**Status.** accepted

**Trigger.** Drawing a clinch or the body a clinch leaves; telling the renderer who is clinched with whom; changing a
lock's or a death throes' length; giving a kill that is not a clinch a look of its own.

**Enforced by.** `StunTest` (each party names the other; a survived blow, an immune body and a reset name nobody), `DevourMarkTest` (the shrink, the sway's bound and both sides, its rest at the lock's end and with the throes, motion off); the look by judgement. The throes reach the renderer as `Appearance.throes`.

**Asked for** by Mateusz, 2026-11-17: a man killed by a leap that lands should shrink and sway as if eaten and tugged,
and what killed him should sway with him; and the same, the other way round, for a leaping alien a man kills.
Grilled the same day.

**Constraint.** In every **clinch** (G0013, G0014) the one killed is **devoured**: through his own **death throes** his
circle shrinks to `graphic.devour.endScale` of its size and is then removed as now, and while both are still there and
the one who lived is still in his **lock**, the pair **sway** together from side to side, across the line joining them
(amended 2026-11-18 by Mateusz: side to side, not back and forth along the line).

- **One rule, no side check.** A grunt clawing a man and a man's fist killing a leaping grunt look the same way round:
  the loser shrinks, both sway. Commander and trooper alike - a trooper's melee clinches with the same lock.
- **Only a clinch.** A leap shot out of the air, deflected, or a blow survived is not a clinch and is not devoured.
- **The clocks already there.** The shrink runs over the loser's death throes, the sway over the winner's lock, fading
  with the throes; no duration moves. A trooper's throes (1.0 s) are shorter than a grunt's lock (1.5 s): his body goes first and the grunt
  stands still for the rest of its lock, as it does now - the sway has faded out with his throes.
- **The clinch names its partner by handle**, on each party's lock, set where the clinch is made and cleared with it
  (ADR 0016). It is the only change to the simulation, and nothing there reads it. The renderer never pairs touching
  grapplers itself - G0098's *one place chooses*.
- **A look, never simulated.** Both bodies keep their size and place for the solver and for shots. The sway's phase is
  counted from the lock's own ticks, so the pair agree on every machine and a replay looks the same; its amplitude, a
  share of the loser's radius, and its pace, at most three swings a second, are in `graphic.properties`.

**Scope.** How a clinch ends on screen. Not the clinch's rules, its lock or the throes' lengths; not a grip's end
(G0045), where the creature lets go with its blow.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Shrinking to nothing.* A dot that vanishes is a pickup taken (G0078's pop); it shrinks to a share and is
removed as every body is.

**No:** *Matching a trooper's throes to the lock.* A gameplay change for a look: his body would stop shots half a second
longer.

**No:** *The renderer finding the partner* - the nearest touching body that is also grappling. A second way to decide
who is clinched with whom.

**No:** *Moving the bodies in the simulation.* A sway the solver sees would shove what stands round the pair, and the
clinch is the one moment nothing is meant to move.

**Revisit when** a clinch can hold more than two, or a kill other than a clinch wants a look of its own.
