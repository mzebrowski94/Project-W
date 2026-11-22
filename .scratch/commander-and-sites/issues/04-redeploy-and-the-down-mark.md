# Redeploy and the down mark

Type: task
Status: open
Label: ready-for-agent
Blocked by: 02 (the pad's B is the dash's when standing; the wire's bit 64 is the dash's)
Spec: [spec.md](../spec.md) section 4
G-record: **G0109** *A down commander may redeploy, and the down mark is the one red mark over the view*
(new; **amends G0098**)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-system`

## What

Rework what a commander sees and can do while **down**:
- a **down mark** over him that every player sees;
- a bleed-out twice as long;
- from half-way through, the choice to **redeploy** himself rather than wait.

Both words are already in `docs/glossary.md`.

## Settled (2026-11-22, not provisional)

### The down mark
- **Shape and look:**
  - a **black-ringed red X (a saltire)** over a down commander: a black filled circle with a bold red X on
    it, drawn above the body;
  - **never a red cross** (the protected emblem; `docs/ui/menu_layout_agent.md` "never a red cross" stands);
  - about **65 % opacity**.
- **Who sees it:** every player, on every machine, for as long as he is down. It is world state drawn from
  `Appearance` (G0003), not a `Prompt`. The PROMPTS switch never hides it.
- **Commanders only:** a down trooper keeps today's look.
- **G0098 amendment.** G0098 says "no red icon or red mark over the view; alert red is the bars' alone". G0109
  amends it: the down mark is the one red mark over the view. Mark G0098 *amended by G0109*.
- **Config** in `graphic.properties`, next to `graphic.action.chip.*`:
  - `graphic.downMark.alpha=0.65`;
  - `graphic.downMark.radius` (world units, about the commander's radius);
  - `graphic.downMark.lift` (how far above him);
  - `graphic.downMark.ring=000000ff`;
  - `graphic.downMark.cross=e02a2aff`;
  - `graphic.downMark.stroke`.

### The window
- `sim.commander.bleedOutSeconds` goes **5 → 10** (`assets/sim/commander.properties`). Friends can
  **stabilise** him for the whole 10 s.
- New `sim.commander.redeployAfterSeconds=5`: how much of the bleed-out must run before he may redeploy.
  The loader refuses a value not below `bleedOutSeconds`.
- **Pressing redeploy** once that much has run ends his bleed-out through the **ordinary death path**
  (`Deaths.kill`, as running out does). Throes, `respawnDelaySeconds`, the reserve cost (NO RESERVE included)
  and the respawn shield are all unchanged.
- **Troopers** keep `sim.casualty.bleedOutSeconds=8` and cannot redeploy.
- A dragged or hauled commander may still redeploy. His bleed-out stops while hauled
  (`draggedBleedFactor=0`), so he can use it only once 5 s have run in total.

### Input
- A new held `redeploying` boolean on `CommanderInput`, at wire **button bit 128**.
  - That fills the held byte: say so in G0109 and in a comment beside `BUTTON_BITS`.
  - Bump `Msg.PROTOCOL`, update `WireGoldenTest`.
- Bindings: `controls.key.redeploy=F` and `controls.pad.redeploy=EAST`. These are the same buttons as drag
  (keyboard) and dash (pad), in a context of their own.
  - Down, he can neither drag nor dash, so nothing clashes.
  - `ControlsConfigTest`'s checks for duplicate bindings, if any, must allow this pairing by context.
  - The input-map row says "while down".
- Only the press edge counts, and only once the window is open. A press before 5 s does nothing. A held
  button from before he went down is spent by the existing latch, or must be released once (check
  `LatchedGamepad`/`latch.filter`).

### The prompt
- On **his own machine only** (G0098: prompts are this machine's), once the window is open: `[F] REDEPLOY`
  (pad `[B] REDEPLOY`) over the down mark.
- `Prompts.choose` today gives a down commander nothing; it gains a `REDEPLOY` case for `Posture.CASUALTY`.
- `PromptKind`/`ActionKind`, `ui.action.REDEPLOY=REDEPLOY`, `ui.prompt.press`.
- Anchored over himself, as the STRIKE/UPGRADE prompts are, lifted above the down mark.
- It obeys the PROMPTS switch, as every chip does.

## Read first

- `CLAUDE.md`.
- `docs/design/invariants/players.md` (rows on coming back), `wounds.md` (bleed-out, stabilise, the shield).
- G0023, G0053, G0055, G0098.
- `docs/ui/menu_layout_agent.md` (prompt slot, "never a red cross").
- Code:
  - `CasualtySystem`, `Vitality`, `Deaths`, `RespawnSystem`, `Drivers.held`;
  - `Prompts`, `PromptKind`, `ActionChips`, `PromptKeys`;
  - `WorldRenderer`, `GraphicConfig`;
  - `CommanderInput`, `Intent`, `InputCodec`;
  - `KeyControls`, `PadControls`, `ControlsConfig`.
- 02's input/wire changes, which this ticket follows exactly.

## Build

1. **Sim.**
   - The longer window, the `redeployAfterSeconds` key, and the redeploy press ending the bleed-out.
   - Publish on `Appearance` whatever the renderer needs, for example a `downMarked` flag. Also publish
     whether the redeploy window is open, for the readout and prompt.
   - Tests:
     - a press before 5 s does nothing;
     - a press after 5 s kills him through `Deaths.kill`, and he comes back after the usual throes and
       delay, at the reserve's cost;
     - a friend can still stabilise him at 9 s;
     - a trooper cannot redeploy;
     - running out at 10 s behaves as before.
2. **Input and wire**, input-map row.
3. **Down mark** (core).
4. **Prompt** (ui), `PromptsTest` cases:
   - a down commander's own machine gets `REDEPLOY` after the window opens and nothing before;
   - another machine gets nothing.
5. **Panel.** The `DOWN` status and its draining bar stay. Optionally the bar marks the redeploy point; take
   that as provisional.

## Docs

- **G0109:**
  - The doubled window and why the old 5 s became the redeploy point.
  - Redeploy costs the same man from the reserve.
  - The down mark is the one red mark (amends G0098), and why a saltire, not a cross.
  - The held byte is now full.
- **Invariant rows:**
  - `players.md`: "a commander can redeploy only after `redeployAfterSeconds` of his bleed-out", **enforced**;
  - `wounds.md`: amend the commander bleed-out row to the new number and the redeploy.
- **Input map:** a "while down" row.
- **Glossary:** **redeploy** and **down mark** are already there. Check that the built thing matches them.

## Judge by

```
./gradlew lwjgl3:run --args="--scenario=full-house"
```

Get downed (harness `G` off); several commanders show the mark at once. `--scenario=casualty` also works.

## Open for the implementer (provisional, for review)

- The mark's exact size and lift.
- Whether the panel's bar marks the redeploy point.
- The `Appearance` field names.

## Done when

- A down commander shows a black-ringed red X to everybody.
- He bleeds out over 10 s, and may redeploy with F / pad B from 5 s.
- His own screen prompts `[F] REDEPLOY` then.
- Redeploying costs what dying costs.
- G0109 is written, and G0098 is marked amended.
- The invariant rows and input map are written.
- `verify.sh` is green and the branch is committed.
- The final message lists the keys.
