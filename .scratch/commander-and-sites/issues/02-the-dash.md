# The dash

Type: task
Status: claimed
Label: ready-for-agent
Blocked by: none
Spec: [spec.md](../spec.md) section 1
G-record: **G0107** *The commander dashes where he is going* (new)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-system`

## What

A commander's **dash**, as defined in `docs/glossary.md`: a short burst along the way he is moving, a few held
at once, each recharging on its own clock.

The work: a new held input from keyboard and pad to the sim, the sim mechanic, purple pips on the player panel,
and the look. The look is two small thrusters while he dashes, plus a **puff** where it began. A press with no
dash left plays only the puff.

The perks that build on it (Rocketman, Pantera) are ticket 03. Leave the stats they will move in place:
`DASH_MOST` and `DASH_RECHARGE`.

## Settled (2026-11-22, not provisional)

### Input
- **Keyboard:** `controls.key.dash=Space` and `controls.key.dashAlias=<left shift>`, on the
  `moveUp`/`moveUpAlias` pattern.
  - libGDX's name for left shift is probably `L-Shift`, not `Shift`. `KeyCode.named` uses
    `Input.Keys.valueOf`, so **pin the right name with a test** before trusting it.
  - Space is bound in game context to nothing today. It is only `controls.ui.key.confirmAlias`, and the
    release latch already spends a press that changes context.
- **Pad:** `controls.pad.dash=EAST`, `controls.pad.dashTrigger=NONE`.
- **Drag is released on the pad:** `controls.pad.drag=NONE`. Keyboard `F` stays drag.
  - The input map's pad column for drag becomes "- (released for the dash, 2026-11-22)".
  - The pad briefing in `PadLabels` drops "pull" and says "dash".
- **Back paddle.** The input layer has no paddle (R4) button. The input map notes that a pad's back paddle is
  mapped to B in the pad's own software.

### Direction
- Along the **move** direction at the press, never the aim (G0002 holds: aim and movement stay independent).
- With the stick or keys neutral, along the **last non-zero move direction**. Keep it on the commander and
  carry it in the snapshot.
- If he has never moved, along his aim (his `Facing`).
- The direction is fixed at launch, as a leap's is. Never steered.

### Physics
- A dash lasts `durationSeconds` ticks at `distance / duration` speed.
- It goes through the ordinary movement and collision path: `MovementSystem` integrates and
  `CollisionSystem`/`ConstraintSolver` pushes out. So rock, fissures, walls, shut doors, ice and bodies stop
  it, and it never crosses impassable ground.
- **No invulnerability, no shield, no i-frames** (pillar 2).
- `CommanderControlSystem.drive` zeroes velocity on an idle stick. While a dash runs, the dash owns his
  velocity: drive must not overwrite it, and the haul reshaping must not cap it.
- **Refused** when `Drivers.held(entity)`: down, stunned, gripped, clinched or aboard. A refused press spends
  nothing and shows nothing.
- **Allowed:**
  - with a crate or draft open, since move stays live there;
  - while hauling: he keeps hold, and the load follows as a drag already makes it follow.
- **Interrupts work.** Starting a dash ends any stabilise or door-open he was doing, and the work is lost, as
  letting go loses it today.
- **Slowing ground, per tick:** on any tick he is on ground that slows him, dash speed is multiplied by
  `slowFactor` (0.5).
  - "Slows him" means the terrain's `playerFactor` < 1 (thicket, mud), or `Slowed` is active from glue.
  - Fire is not a slow and does not count.
  - Wounds (`Vitality.paceFactor`) do **not** shorten a dash.
  - Read the ground through the one place that knows it (`Pace.of`, or its parts). Add no second reader of
    terrain pace.
- **Counting:**
  - `most` dashes, kept as **spent**, as `Thrower.spent` is (G0077), so raising `most` hands him the extra
    without a write;
  - one recharge clock runs while `spent > 0`, giving one back every `rechargeSeconds`, one at a time;
  - he starts full, and is full again on every return.
- **Ticks:** every duration goes through `Ticks.of`.

### Numbers
In `assets/sim/commander.properties`, with comments in the file's voice:
- `sim.commander.dash.distance=15` (three tiles);
- `sim.commander.dash.durationSeconds=0.15`;
- `sim.commander.dash.most=2`;
- `sim.commander.dash.rechargeSeconds=3`;
- `sim.commander.dash.slowFactor=0.5`.

### Panel
- The dashes left show as **purple pips** on the player panel's first row, **next to the wound pips**.
- Reuse `BottomBarPainter.pips` with two new `TallyIcon`s (lit and spare), and add a `PlayerReadout` pair,
  `dashes` and `dashMost`, written by `ReadoutWriter` from the folded values.
- Colour `graphic.hud.color.dash=b060ffff`.
- When a status word replaces the wounds (DOWN, STUNNED and so on), the dash pips go with them.
- Check `TextFitTest` and the panel's width with 8 players.

### Look
Through `Appearance` (G0003): the renderer reads a component and never a sim system. No particle system exists
and none is to be built.
- **Thrusters:** while a dash runs, plus a short tail (`graphic.dash.thruster.tailSeconds`), two small flame
  cones behind his shoulders, opposite the dash direction.
  - Orange core `ffb347ff`, white tip.
  - Sizes in `graphic.properties` (`graphic.dash.thruster.*`).
- **Puff:** a small translucent grey disc at the dash's start position, growing and fading over about 0.6 s.
  - It stays where it was left while he moves away, so it is an entity of its own: a short-lived transient
    through `Spawner`, on the `Pop` precedent (G0078).
  - `graphic.dash.puff.*` holds radius from/to, alpha `0.45` → 0, and colour `c8c8c8ff`. The lifetime is a sim
    key, `sim.commander.dash.puffSeconds=0.6`.
- **A press with no dash left** (and not refused) spawns only the puff, at his feet. No thrusters, no
  movement, nothing spent.

### Wire
- `CommanderInput` gains a held `dashing` boolean, with the press edge derived in `Intent.set`, as
  `firePressed` is.
- `InputCodec` packs it at **button bit 64**: `BUTTON_BITS` becomes 127, and `buttons()`, `readHeld()` and
  `same()` learn it.
- Bump `Msg.PROTOCOL`, update `WireGoldenTest`.
- Keep the 6-argument `CommanderInput` constructor for the tests that use it.

## Read first

- `CLAUDE.md`.
- `docs/design/README.md` (input map).
- `docs/design/invariants/players.md`, `movement.md`, `combat.md`.
- G0002, G0003, G0021, G0077.
- `docs/components.md` (Intent, Thrower, Slowed rows).
- `docs/simulation-order.md`.
- Code:
  - `CommanderControlSystem` (`drive`, `haul`, `processEntity`);
  - `Drivers.held`, `Pace.of`;
  - `Thrower` and `Throws` (the count pattern);
  - `Intent`, `CommanderInput`, `Simulation.step` (the copy into `Intent`);
  - `KeyControls`, `PadControls`, `ControlsConfig`, `ControlsTable`, `PadLabels`;
  - `InputCodec`, `Msg`;
  - `BottomBarPainter.panel`/`pips`, `PlayerReadout`, `ReadoutWriter`;
  - `Spawner.spawnCommander`, `Pop`/`PopMark`, `WorldRenderer`.
- Explorer notes: input reaches sim in `CommanderInput` (held only), copied into `Intent` by `Simulation`.
  Bits 64/128 of the button byte are free. Components are added at spawn only, never mid-tick. `Components.ALL`
  plus `ComponentResetTest`. `docs/components.md` *Written by* is test-enforced (ADR 0019).

## Build (stages; the work may stop after any)

1. **Sim.**
   - A `Dash` component on every commander (added in `Spawner.spawnCommander`): remaining ticks, direction,
     last move direction, spent, recharge ticks.
   - `reset()` clears every field.
   - Its clock is counted where the condition is decided (`CooldownSystem` javadoc).
   - Stats `DASH_MOST` (COUNT, UNIT) and `DASH_RECHARGE` (SCALE, UNIT), each read in exactly one `@Reads`
     method of `Modifiers` with one caller. The `sim.upgrade.stat.*` limits go in `upgrades.properties`.
   - Who launches the dash: `CommanderControlSystem`, or a new system just before it. Say why in the
     registration.
   - Tests first:
     - a dash covers `distance` on open ground;
     - it goes along move, not aim;
     - a neutral stick uses the last move;
     - it is refused while held;
     - two dashes, then a third press spends nothing;
     - the recharge comes back one at a time;
     - half speed on glue and thicket;
     - a wall stops it;
     - it interrupts a stabilise.
2. **Input and wire.** Config, `KeyControls`/`PadControls`, the wire bit, the protocol bump, the input-map row
   and the drag row, `ui.controls.dash=DASH`, `ControlsTable`, and the tests `ControlsConfigTest`,
   `PadControlsTest`, `ControlsTableTest`, `WireGoldenTest`, `BundleTest`.
3. **Panel.** The readout pair and pips.
4. **Look.** Thrusters, puff, and the dry puff.
5. **Scenario.** "Dash Yard", hidden, in `Scenarios.ALL`:
   - open sand;
   - a few glue slicks and a thicket patch to dash across;
   - a wall to dash into;
   - a handful of grunts at the far side;
   - no squad, to keep the read clean.

## Docs

- **G0107.** What a dash is and why along movement. Why no invulnerability (pillar 2). Per-tick slowing.
  Spent-count recharge one at a time. That the pad lost drag.
  - Rejected and re-proposable: i-frames; dashing along aim; a start-based halving on goo.
- **Invariant rows** in `players.md` (or `movement.md` where it fits):
  - "a dash never protects him", **enforced**;
  - "a dash goes along movement, never the aim", **enforced**.
- **Input map:** a row for the dash, and the drag row's pad column.

## Judge by

```
./gradlew lwjgl3:run --args="--scenario=dash-yard"
```

Also on a pad.

## Open for the implementer (provisional, for review)

- Which system owns the launch.
- Exactly how the haul and the dash combine on the tick a dash starts while hauling.
- The thruster drawing details.

## Done when

- Keyboard Space and left Shift, and pad B, dash along movement over 15 units in 0.15 s.
- Two dashes, each coming back after 3 s, one at a time.
- Half reach over slowing ground; refused while held; no protection.
- Purple pips beside the wounds.
- Thrusters while dashing, a puff where it began, and only a puff with none left.
- Pad drag released.
- The wire carries it.
- G0107, the invariant rows and the input map are written.
- "Dash Yard" is reachable with `--scenario`.
- `verify.sh` is green and the branch is committed.
- The final message lists the keys and their values.
