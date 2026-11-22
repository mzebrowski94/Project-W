# G0109: A down commander may redeploy, and the down mark is the one red mark over the view

**Status.** accepted

**Trigger.** How long a commander lies down; letting him give up on being reached; a key that does something while he
is down; a mark over a man who is down; anything red over the view; another held button on the wire.

**Enforced by.** `RedeployTest` - `.doesNothingOnAPressBeforeHisWindowIsOpen`,
`.doesNothingForAKeyHeldFromBeforeHisWindowOpened`, `.endsHisBleedOutOnAPressOnceHisWindowIsOpen`,
`.bringsHimBackAfterTheUsualThroesAndDelayAtTheReservesCost`, `.letsAFriendStabiliseHimAfterHisWindowHasOpened`,
`.opensHisWindowOnlyOnceThatMuchOfHisBleedOutHasRun`, `.bleedsHimOutAsBeforeWhenHeNeverPresses`,
`.neverOpensATroopersWindow`, `.marksACommanderWhoIsDownAndSaysWhenHisWindowOpens`,
`.refusesARedeployPointThatIsNotBeforeTheEndOfHisBleedOut`, `.shipsATenSecondBleedOutWithItsWindowHalfWay`;
`PromptsTest.promptsRedeployOverAManWhoIsDownOnceHisWindowIsOpenAndNothingBefore`,
`.promptsNoRedeployForAManPlayedOnAnotherMachine`, `.promptsOnlyRedeployWhileHeIsDown`;
`ReadoutWriterTest.carriesWhetherAManDownMayRedeploy`; `ControlsConfigTest.redeploysOnTheDragsKeyAndTheDashsButton`;
`PadControlsTest.redeploysWhileTheEastButtonIsHeld`; `PromptKeysTest`;
`WireGoldenTest.writesRedeployAsBitOneHundredAndTwentyEightOfTheButtons`;
`GraphicConfigTest.marksAManDownWithASeeThroughRedXOnABlackDisc`. How the mark looks is judgement.

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 4); the points under
*Provisional* are the implementer's, taken in an AFK run.

**Amends G0098** - its *no red icon or red mark over the view*. The down mark is the one red mark over the view; alert
red is otherwise still the bars' alone.

**Constraint.**

- **The commander's bleed-out is doubled**, `sim.commander.bleedOutSeconds` 5 -> 10. His friends can stabilise him for
  the whole of it, as before. A trooper's stays `sim.casualty.bleedOutSeconds` (8).
- **The old five seconds became the redeploy point**, `sim.commander.redeployAfterSeconds` (5): once that much of his
  bleed-out has run, a **press** of his redeploy key ends it. A player who gives up waits no longer than he did before;
  a friend who comes has twice as long. The loader refuses a point that is not above zero and below the bleed-out.
- **Counted from what has run**, never from the ticks since he went down (`Vitality.redeployOpen`): a clock that
  stops holds the window shut. No commander can be hauled today, so the ticket's "a hauled commander may still
  redeploy, once 5 s have run in all" is the rule that stands if one ever can.
- **Redeploying is dying.** The press ends his bleed-out through `Deaths.kill`, the call running out makes
  (`CasualtySystem`): the throes, `respawnDelaySeconds`, a man from the **reserve** - NO RESERVE included - and the
  respawn shield are all unchanged. It costs the same man running out would.
- **Only his own, only a press, only in the window.** A press before it does nothing; a key held from before it - or
  from before he went down - does nothing until let go and pressed again (`Intent.redeployPressed`, the press edge, as
  the dash's). A trooper's window never opens (`Vitality.redeployAfterTicks` zero is never). Nobody redeploys another.
- **The input** is a held `CommanderInput.redeploying`, on the wire as button bit 128 (`Msg.PROTOCOL` 11). **The held
  byte is now full** - eight buttons, a bit each; a ninth needs another byte of `Bundle.HELD_BYTES`. Bound to the
  drag's `F` and the pad's dash `EAST` (B / Circle) in a context of their own: down, he can neither drag nor dash.
- **The down mark** (`Appearance.downMarked`): a black disc with a bold red X on it, about 65 % opaque, over a
  commander who is down - every player, every machine, for as long as he is down. World state drawn from
  `Appearance` (G0003), not a prompt: the PROMPTS switch never hides it. A trooper down keeps his look.
- **Why a saltire, not a cross**: a red cross on white - or any upright red cross - is the protected emblem of the
  Geneva Conventions, which a game may not use, and `docs/ui/menu_layout_agent.md` already says "never a red cross".
  An X on its diagonals, on black, says "out" at a glance and is no emblem.
- **The prompt** `[F] REDEPLOY` (pad `[B] REDEPLOY`) over him, above the mark, on his own machine only (G0098), once
  his window is open (`Appearance.redeployOpen`, `PromptKind.REDEPLOY`). It obeys the PROMPTS switch.

**Provisional, for review (AFK run).**

- **The mark's size and lift**: `graphic.downMark.radius` 1.6 (a commander's is 2), its foot `lift` 0.8 above his
  body, bars `stroke` 0.5 thick reaching `reach` 0.62 of the radius. *Turned down:* a disc as wide as he is - with the prompt
  above it, it put the text far off his body at the default zoom.
- **The mark is drawn over the trees.** *Turned down:* under them with the brackets - G0072 lets foliage hide what is
  beneath it, but the mark is state every player is promised for as long as he is down, so foliage does not hide it.
- **The second bar of the X stops at the first's edge**, so the middle is not blended twice and the mark is equally
  see-through all over. *Turned down:* two crossing bars, whose middle read as a darker spot at 65 %.
- **The panel's bar does not mark the redeploy point.** `DOWN` and its draining bar stay as they were. *Turned
  down:* a notch at the point - a second signal for the moment his own screen already prompts, on a bar every panel
  shows, where another player's notch says nothing he can act on.
- **`Appearance` names**: `downMarked` and `redeployOpen`; the readout carries `PlayerReadout.redeployOpen`.
- **`PromptKind.REDEPLOY`, not an `ActionKind`.** *Turned down:* a seventh action kind - `ActionKind` is what the
  action key would do to what is in reach (G0021), and redeploy is neither that key nor about anything in reach.
- **The redeploy press is read in `CasualtySystem`**, the one place his bleed-out is counted, after this tick's bleed.
  A friend whose stabilise completes on the same tick wins: `ActionSystem` and `AidSystem` run first, so he is up.
- **The controls page gains a `REDEPLOY (DOWN)` row**, `F` and `B`.
- **`DownMark` is a declared port of `client.draw`** (ADR 0021), so the prompt can sit above the mark.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A red cross, or any upright cross, as the down mark.* The protected emblem; see above.

**No:** *Redeploy at any time while down.* It would make getting down a free respawn whenever the rally point is
nearer than the fight, and would take the rescue - the reason he can be down at all (G0023) - away from his friends.

**No:** *A redeploy that is cheaper than dying* - no throes, no wait, no man from the reserve. It is the same death
sooner, never a way out of the reserve's cost (G0055).

**No:** *Hiding the down mark with the PROMPTS switch, or showing it only to his own machine.* It is state, and every
player needs to know who is down (pillar 5).

**Revisit when** a commander can be hauled, a ninth held button is wanted, or a second red thing is proposed over the
view.
