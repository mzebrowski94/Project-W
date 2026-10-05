# G0066: A level ends on its first outcome, and the result screen says what it was

**Status.** accepted

**Trigger.** Anything that changes what happens when a level is won or lost, whether an outcome can be undone,
what the result screen shows or offers, or where leaving a level returns the player to.

**Enforced by.** `OutcomeTest` (won then wiped stays won, lost stays lost, the time is the tick it landed on,
a tie is the loss), `ReadoutTest` (when the screen is due), `UiFlowTest` (RETRY, MENU, the host's confirm,
Back ignored), `LevelTimeTest` (the time as `m:ss.t`); the page MENU returns to is `ScenarioScreen`'s `back`,
untested (it needs GL); `MenuTreeTest.offersCopyCodeOnAMissionsResultOnly`, `UiFlowTest.copiesTheCodeOnlyFromTheResult`.
Built 2026-10-05 for every level with an objective, the time included; a mission's lines and COPY CODE with the DAILY
MISSIONS page (missions stage 9), NEW BEST with the record (stage 10). Missions ticket 08 holds the reasoning.

**Answers** the open questions "what happens when a level's objectives are met?" and "on failure, do you retry
the level, or is the run over?" (`README.md`), for every level with an objective - a mission and an Adventure
alike. **Retires** the blocked `UI:play/game-over` modal: it becomes `UI:play/result`.

**Constraint.**

- **The first outcome is final.** The tick the objective is met is the win; the tick G0055 loses the level is
  the loss. Whichever lands first stands - a squad wiped after the finale is won has still won, and the goal
  line keeps saying so. The simulation decides it on one tick, so every machine agrees.
- **The world runs on.** The goal line pulses, and after `graphic.ui.resultHoldSeconds` (3) - the same for both
  outcomes, not skipped by a press - the **result screen** opens over the scrim. No pause crosses the network.
- **What it shows**: `MISSION COMPLETE` or `MISSION FAILED`, and on a win the time - `m:ss.t`, compared in
  ticks - for every level; for a mission also points taken x/y, the code with its tier's name, `DAILY <date>`
  for a daily, and `NEW BEST` when the time beats the remembered one. Then RETRY (default) and MENU; a mission adds COPY CODE.
- **RETRY is the same level again**: the same scenario and seed, offline or by the host, as RESTART is; a joined
  machine sees it dimmed. **MENU** leaves - the host through the leave confirm, since it ends the session - and
  returns to the page the level was started from, with what was played selected.

**Why the first outcome.** An outcome that a later tick could overturn gives every machine a race to agree on
and makes the time a moving target. The win is the moment the finale fell; what happens to the squad after it
is not the level.

**Why RETRY rather than a run that is over.** A mission is one level played in one go; the record is a ✓ and a
best time, not a life (pillar 3). Losing costs the attempt, and the next one is one press away - arcade first.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Pausing the world under the result screen.* Online it is a command that has to land; nothing under the
screen can change the outcome anyway.

**No:** *A time shown after a loss.* A time to failure records nothing.

**No:** *Skipping the hold with a press.* That press is a combat press carried into a modal.

**No:** *The time on the HUD during play* (missions ticket 01). It is on the pause page and here.

**No:** *"This mission on another tier" from the result screen* - deferred, not rejected; see below.

**Revisit when** a level is meant to go on after its objective (extraction, a lull, a second wave), or players
ask to play one seed at another tier - then a tier choice row beside RETRY, building a new code with its check
recomputed and never filed as a daily (G0065).
