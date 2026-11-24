# G0122: The squad assaults the lair, its nests first and then the boss

**Status.** accepted

**Trigger.** What troopers do in a finale once its lair has risen; walking troopers onto nests or a boss; changing what
outranks the post's ring.

**Enforced by.** `TrooperAssaultTest.staysOffTheLairUntilItRises`,
`.walksForTheNearestNestOnceTheLairRises`, `.goesForTheBossOnceItsNestsAreGone`, `.stopsAtHisReachRatherThanWalkingOntoIt`,
`.holdsAnOrderOverTheAssault`, `.fightsWhatIsInsideHisAllRoundRangeFirst`, `.neverAssaultsOnALevelWithNoLair`.

**Settled with Mateusz, 2026-11-24** (grilling): *troopers should be more proactive in the finale - nests first, then the
boss.* Not yet built, not yet played.

**Amends G0061.** In a finale whose lair has risen, the post's ring is no longer where a man with nothing to do stands.

**Constraint.**

- **Once the lair has risen**, a trooper with no order and nothing in his all-round range walks to his gun's reach of
  the **nearest living nest the lair raised**, and with none left, of the **boss**. Not closer: reach, then he fights.
- **Orders and self-defence outrank it**: an order (G0017), anything inside his all-round range and a nest he keeps
  (G0085) come first, as they outrank the ring today. A nest is never always first (G0085's rejection stands).
- **The redoubt is unchanged**: its men hold the post's ring between waves (G0061). Before the lair rises the squad
  patrols the way in (G0094).

**Why from reach, not on top.** A man at a nest's mouth is a man in its brood and the boss's ring; his gun is what
the assault is for.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Pushing out to the redoubt's mouths between waves.* Offered and declined (Mateusz, 2026-11-24).

**No:** *Weighting alone, the men staying on the ring.* Offered and declined: least visible, and the ask was movement.

**Revisit when** a finale has a lair and waves at once, or play shows the squad walking into the barrage to die.

## Amended by

**Finale-variety ticket 05, 2026-11-24 - provisional, for review (AFK run).** What the record left to the build:

1. **Where it lives**: `TrooperObjective.assaults`, in `TrooperMindSystem`'s cascade after the errands (aid, mend, crew)
   and before the post's ring - where the ring and the way in already live, so what outranks the ring outranks the
   assault by the cascade's order. No new system.
2. **What a lair raised is known by `Enemy.raised`**, set by `LairSystem` on every nest and unit it puts down; a nest
   is told from the boss by carrying `Nest`, no branch on a strain. Turned down: the lair's own list of handles (a
   second list the snapshot must carry beside the components).
3. **Nearest crow-flies**: the lair stands in the finale's open ground, and `TrooperObjective`'s flow fields are one a
   point; one a nest would be a router for one choice. Ties go to the first spawned.
4. **Reach**: his gun's `projectileRange`; a shot that flies until it hits (the sidearm, range 0) stops at
   `sim.trooper.assaultStandOff=40`. Either way never past where the target stands out from his sight (his detection
   range plus its `standsOut`, 27 + 27 on the shipped numbers) - a man standing farther off would never pick it out,
   and the fight would never take him up. Turned down: the bare reach (a railgun's reach leaves him blind to it).
5. **`Trooper.Mode.ASSAULT`**, walking with a target and standing without one; a man in it stands still at reach,
   facing the way he walked - at the nest.
