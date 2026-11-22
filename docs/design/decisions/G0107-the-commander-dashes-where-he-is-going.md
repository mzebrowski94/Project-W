# G0107: The commander dashes where he is going

**Status.** accepted

**Trigger.** A burst of movement for anybody on the player's side; dodging, rolling, blinking or sprinting; a count he
holds that comes back over time; anything that would make him harder to hit for a moment; moving drag off the pad or
putting something on the pad's B; Rocketman, Pantera or any other upgrade on the dash.

**Enforced by.** `DashTest` - `.coversItsDistanceOnOpenGround`, `.goesAlongHisMovementRatherThanHisAim`,
`.goesTheWayHeLastMovedWithTheStickAtRest`, `.goesAlongHisAimIfHeHasNeverMoved`, `.isRefusedWhileHeIsHeld`,
`.spendsNothingAndGoesNowhereOnAPressWithNoneLeft`, `.leavesOnlyAPuffOnAPressWithNoneLeft`,
`.leavesAPuffWhereTheDashBegan`, `.givesThemBackOneAtATime`, `.ignoresAPressWhileABurstStillRuns`,
`.reachesHalfAsFarAcrossAThicket`, `.reachesHalfAsFarThroughGlue`, `.goesItsWholeDistanceWounded`,
`.isStoppedByAWall`, `.neverProtectsHim`, `.losesTheWorkOfAStabiliseInHand`, `.keepsHoldOfWhatHeHauls`;
`ControlsConfigTest.dashesOnSpaceOrTheLeftShift`, `.dashesOnThePadsEastAndLeavesDragOffThePad`;
`PadControlsTest.dashesWhileTheEastButtonIsHeld`; `WireGoldenTest.writesTheDashAsBitSixtyFourOfTheButtons`;
`GraphicConfigTest.readsADashsThrustersAndPuff`; `ReadoutWriterTest.publishesTheDashesHeHoldsAndHisMost`.

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 1); the points under
*Provisional* are the implementer's, taken in an AFK run.

**Constraint.**

- **A dash is a fixed-duration burst of velocity** - `sim.commander.dash.distance` (15, three tiles) over
  `durationSeconds` (0.15), its speed the distance over exactly the ticks it lasts. It goes through the ordinary
  movement and collision path: rock, fissures, walls, shut doors, ice and bodies stop it, and it never crosses
  impassable ground.
- **Along his movement, never his aim** (G0002 holds). With the stick or keys at rest, along the last way he moved,
  kept on the commander and carried in a snapshot; if he never has, along his facing. Fixed at the press, never
  steered.
- **No invulnerability, no shield, no i-frames** (pillar 2). A dash moves him; it protects him from nothing.
- **A count, kept as spent** (G0077's shape): `most` (2) held, `DASH_MOST` folded at the read. One clock runs while
  anything is spent and gives one back every `rechargeSeconds` (3), `DASH_RECHARGE` folded where it is wound - one at
  a time. He starts full and is full again on every return.
- **Refused while he is held** (`Drivers.held`: down, stunned, gripped, clinched, aboard): nothing spent, nothing
  shown. **Allowed** with a crate or a draft open, and while hauling - he keeps hold and the load follows, at his pace
  rather than his walk's.
- **A dash ends work in hand**: a stabilise or a door being opened loses its work, as letting go loses it.
- **Slowing ground, per tick**: every tick of a dash spent on ground that slows him - a terrain's `playerFactor`
  below one (thicket, mud), or a slow done to him (`Slowed`: glue) - goes at `slowFactor` (0.5) of its speed. Fire
  is no slow. A wound does not shorten a dash. The ground is read through `Pace`, the one place that knows it.
- **Inputs**: `Space` or left Shift on the keyboard; `EAST` (B / Circle) on the pad. **The pad lost "take hold / let
  go"**: `controls.pad.drag=NONE`, the keyboard's `F` alone hauls. A back paddle is mapped to B in the pad's own
  software. On the wire, button bit 64 (`Msg.PROTOCOL` 10).
- **Shown**: purple pips beside his wounds on his player panel, gone with the wounds when a status word replaces
  them; two small thrusters on his back while it runs and for a short tail; a **puff** where it began - and only the
  puff on a press with no dash left.

**Provisional, for review (AFK run).**

- **`CommanderControlSystem` launches the dash** and counts its recharge. *Rejected:* a `DashSystem` just before it -
  the commander's velocity has one owner (G0010's named exception), and a second system would have had to tell the
  first to keep its hands off, which is two writers of one velocity.
- **A press while a burst is still running does nothing** - no second dash, no turn, nothing spent. *Rejected:*
  chaining a second burst from the first - a mashed key would spend both in a third of a second.
- **On the tick a dash starts while hauling**, the dash owns his velocity outright, and the load is pulled at the pace
  he is actually going (`DragSystem` caps its pull at the larger of his walk and his speed last tick), so it trails a
  tick behind and the grip holds. *Rejected:* cutting the dash to the haul's pace - the spec says the haul must not
  cap it - and letting go.
- **Any active `Slowed` halves a dash**, a laser's self-slow included: one question, asked of `Pace`. *Rejected:*
  asking whether the slow came from glue, which would be a second reader of what slowed him.
- **A dash keeps his hands off work for the whole burst**, not just its first tick; the key's press edge is still
  counted, so a key held through a dash does not board a gun when it ends.
- **The puff's colour is `graphic.color.puff`**, the palette's, where every look's colour is; `graphic.dash.puff.*`
  holds its sizes and alpha. *Rejected:* a second colour key under `graphic.dash.puff`.
- **The thrusters**: two cones from behind his shoulders, `apart` radii either side of his middle, pointing back
  along the dash, orange with a white-hot nozzle; shrinking and fading over `graphic.dash.thruster.tailSeconds`.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *I-frames, a shield or damage reduction while dashing.* Pillar 2: the commander is a unit you can kill, and a
dodge that cannot be hit is a dodge nobody has to aim. The dash buys distance, nothing else.

**No:** *Dashing along the aim.* G0002: aim and movement are independent, and a dash that went where he points would
make the mouse a second movement stick. A dash goes where he was already going.

**No:** *Halving a dash that starts on goo* - deciding the reach once at the press. A burst that starts in a puddle and
leaves it would be punished for ground it has left, and one that starts outside and crosses it would not be at all;
per tick is the same rule as walking.

**Revisit when** a second unit dashes, or an upgrade would change a dash's speed - Pantera deliberately does not.
