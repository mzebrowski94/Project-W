# G0121: With the reserve empty, a trooper is recalled to bring a commander back

**Status.** accepted

**Trigger.** What a commander's return costs when the reserve is empty; how a level is lost; taking a man off the field
by anything but death.

**Enforced by.** `ReserveTest.recallsATrooperToBringACommanderBackOnAnEmptyReserve`, `.paysFromTheReserveBeforeRecallingAnybody`,
`.recallsTheTrooperNearestTheRallyPoint`, `.neverRecallsAManWhoIsDown`, `.breaksATieByHandleOrder`,
`.recallsOnePerWaitingCommanderLowestSlotFirst`, `.bringsEveryWaitingCommanderBackWhileThereAreTroopersToRecall`,
`.recallsHimWithoutADeath`, `.losesTheLevelWhenNoTrooperIsOnHisFeet`, and the crate-first rule still by
`.bringsHimBackWithACratesManWhileHeWaitsOnAnEmptyReserve`; `ReadoutWriterTest.saysHeWasPaidForByARecall`;
`RecallScenarioTest`.

**Settled with Mateusz, 2026-11-24** (grilling): *the mission should not fail with no reserve left while troopers are on
the map; the commander comes back at the cost of one of them.* Not yet built, not yet played.

**Amends G0055.** Its lost level gains a fourth condition, and a commander waiting on an empty reserve no longer simply
waits. Its *commanders who come back free* stands: the recall is the price.

**Constraint.**

- **The reserve pays first.** A commander's return takes a man from the reserve as G0055 says, and a crate fetched while
  he waits still pays for him first.
- **With nobody in reserve, a trooper is recalled**: the trooper **on his feet** nearest the rally point - the rear of the
  squad, so the line keeps its shooters - leaves the field and pays for him, the tick the reserve would have. A down
  trooper is never recalled. Ties go by handle order, never `Rng`.
- **He comes back at the rally point**, as every man from the reserve does (G0055, G0061 unchanged).
- **In co-op, one recall per waiting commander**, lowest slot first.
- **Shown** (pillar 5): the recalled trooper fades out with an effect of his own, never a death's, and a commander
  waiting on a recall says `RECALL` where he said `NO RESERVE`.
- **The level is lost** when every commander is out, somebody is waiting, the reserve is empty **and no trooper is on
  his feet**. Latched, as before.
- **Every level**, not the finale alone. Promotes the deferred row in `players.md`: the commander comes back while any
  trooper still lives.

**Why the rally point, not the trooper's place.** Mateusz kept G0055's one place for every man the reserve sends; the
recall is a payment, not a possession.

**Why the trooper nearest the rally point.** He is the one the fight misses least. The most wounded is often in the
middle of the fight; the least engaged is a judgement the player cannot read.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Coming back in the trooper's place.* Offered and declined: it would be a second place a man comes onto the field.

**No:** *Coming back where he fell.* Straight back into what killed him.

**No:** *Recalling the most wounded, or the least engaged.*

**No:** *A recall that leaves a corpse or counts as a kill.* Nothing killed him; nothing earns a tally.

**Revisit when** play shows a squad bled to one man by recalls reads as a slow loss rather than a last stand.

## Amended by

**Finale-variety ticket 02, 2026-11-24 - provisional, for review (AFK run).** What the record left to the build:

1. **When.** The recall lands the tick the reserve would have paid: the tick after he is gone, after his throes
   (G0055's default). Turned down: recalling the tick he falls (his throes would run with the trooper already gone,
   and the reserve's own timing would differ from the recall's).
2. **Distance.** Crow-flies to the middle of the rally area (`RallyPoint.area`), the tick of the recall. Route
   distance is not at hand in `RespawnSystem` - a flow field would be a second router for one choice.
3. **How he leaves.** `Spawner.recall`: his blast, slick and ticket reactions taken off, then marked to be reaped -
   so no death reaction runs, and with no throes there is no corpse. What he held is let go as by any holder gone.
   Turned down: a `Recalled` marker every death reaction excludes (each new reaction would have to remember it).
4. **The fade.** A pop (G0078) in a look of its own, `Look.RECALL`, his size, rising and fading where he stood for
   `sim.reserve.recallSeconds=0.8`, in `graphic.color.recall=9fe8ffff` (pale cyan); its rise and alpha are the pop's
   (`graphic.pop.*`). Turned down: a new effect component and painter (the pop already draws exactly this).
5. **The HUD.** `RECALL` alone (`ui.hud.recall`) for the whole of his paid wait, in place of `REDEPLOY` and its
   seconds; `NO RESERVE` stays for nobody to recall. Turned down: `RECALL` with the seconds (the record writes the
   bare word, and the seconds run as the reserve's would).
6. **Snapshot.** `Respawning.recalled` is a field on the ticket, which the snapshot already carries
   (`WorldCodec`'s reflective plan), so a peer joining mid-wait shows `RECALL` too.
