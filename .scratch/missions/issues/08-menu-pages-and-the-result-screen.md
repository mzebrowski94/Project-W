# Menu pages and the result screen

Type: prototype
Status: resolved
Blocked by: 05

## Question

What the player sees, per `docs/ui/menu_layout_agent.md`: the DAILY MISSIONS page (three tiers, date,
✓, best time, RANDOM, ENTER CODE), HOST GAME's mission choice, the mission time on the pause page, and
the result screen (success/failure, points x/y, time, code, RETRY, MENU) - which also answers the open
question "what happens when a level's objectives are met" for a mission. Prototype the pages as data.

## Answer

Grilled 2026-10-05 (Q1-Q17, G0066). Settled before the session: the DAILY MISSIONS page shows three tiers with the
date, ✓ and best time, plus RANDOM and ENTER CODE; a code is `T SSSSSS C` in the room code's alphabet and a typo is
refused; RANDOM draws the seed and the player picks the tier; the result screen shows success or failure, points
x/y, time, the code, RETRY and MENU; time only on the pause page during play. Written into
`docs/ui/menu_layout_agent.md` as **planned** (section 4 and 5; section 3 rows arrive with the `UiPage` constants,
since `UiSpecTest` holds the table to the enum).

**The DAILY MISSIONS page** (`UI:main/missions`)
- The main menu's first entry, and its default selection once built - missions are the loop; the Adventures one
  press down.
- Title `DAILY MISSIONS 2026-10-05 UTC`, the date read when the page opens. Rows EASY / MEDIUM / HARD, each with ✓
  and its best time once won; confirm starts at once. Under the list, **the selected daily's code** - the one place a
  code shows before play.
- **RANDOM is a choice row** `RANDOM < MEDIUM >`: left/right step the tier (remembered for the run, first MEDIUM),
  confirm draws a fresh seed and starts.
- **ENTER CODE is an inline code line**, as join's: keyboard and Steam keyboard (STEAM+X) type, `Ctrl+V` pastes,
  forgiving reading. A pad opens an 8-slot `CodePicker` with a dash after slot 4; **slot one cycles only the tier
  letters** (`CodePicker` gains an alphabet per slot), starting on the last code played this run, else today's
  MEDIUM daily; the tier's name shows once slot one holds one. A refused code says NOT A MISSION CODE and keeps the
  picker open on what was entered - **never a hint at the wrong character**, since showing the right check would let
  a typo through (G0065).
- **A code is a daily when it equals one of that day's three**, however it arrived. What is written is 09's.
- HOST GAME's first entry, DAILY MISSIONS, opens the same page in host mode.

**During play** - the HUD's mission line says `DAILY HARD` or `HARD`, never code or time; the pause page adds
`HK3X-Q7RA  HARD  TIME 12:34.5`.

**The result screen** (`UI:play/result`, G0066) - for **every level with an objective**, retiring the blocked
`UI:play/game-over` and answering both open questions in `docs/design/README.md` (objectives met; retry or over):
- **The first outcome is final**: the win's tick (or G0055's loss) stands; a wipe after the finale is still a win.
- The world runs on; after `graphic.ui.resultHoldSeconds` 3, the same both ways, no skip, the modal opens over
  the scrim. No pause crosses the network.
- MISSION COMPLETE / MISSION FAILED; for a mission POINTS x/y, the code and tier, DAILY date; TIME `m:ss.t`
  (compared in ticks) and NEW BEST **on a win only**.
- RETRY (default; offline or host, dimmed for a joined machine), COPY CODE (mission only, libGDX `Clipboard`),
  MENU (host via the leave confirm). Back does nothing. MENU returns to **the page the level was started from**,
  with what was played selected.

**Out** - "this mission on another tier" (deferred in G0066, with its trigger: a tier row beside RETRY, check
recomputed, never a daily); a countdown to the next day; codes on every daily row; a clickable prototype (the pages
copy the proving ground and join patterns).
