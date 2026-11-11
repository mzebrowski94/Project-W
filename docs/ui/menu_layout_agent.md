# Menu layout - agent specification

**Source of truth for every page, modal and HUD slot the player can see.** Written 2026-09-25 from
Mateusz's `menu_layout.md` outline; the architecture behind it is `ui_and_graphics_action_plan.md`
in this folder. Change this file in the same change as the code it describes.

Status words, used in every table: **built** (works today), **planned** (specified, not built),
**placeholder** (built on purpose as an empty page with Back), **blocked** (specified, cannot be
reached until an open question is answered - the question is named).

## 1. How to use this file

- Every page, modal and HUD slot has an **id** of the form `UI:<path>`. The same id appears in the
  code as the javadoc line of the class that implements it and as the `id()` of its constant in
  `UiPage` or `HudSlot` (module `ui`). To find the code for a page:
  `grep -rn "UI:main/campaign" ui/src core/src`.
- Id grammar: `UI:` + lowercase path segments joined by `/`, kebab-case within a segment
  (`UI:main/proving-ground`). The enum constant is the last segment in upper snake case unless that is
  ambiguous (`UiPage.PROVING_GROUND`, `UiPage.ONLINE_JOIN`).
- `UiSpecTest` (module `ui`) reads this file, collects every
  `UI:` id in the tables of section 3 and section 7, and fails if one is missing from
  `UiPage`/`HudSlot` or if an enum constant has no row here. The doc and the enum cannot drift.
- Labels are never literals. Each label column is an i18n key in `assets/i18n/ui.properties`; the
  English copy after the key is the default text for that file.
- A **Do not build** cell is binding. It names the decision record that already rejected it.

Glossary words this file relies on (all in `docs/glossary.md`; the portrait arrives with stage 8 of
the plan): **page**, **modal**, **entry**, **selection**, **modality**, **letterbox**, **UI canvas**,
**top bar**, **bottom bar**, **scrim**, **paused banner**, **reticle**, **player panel**, **status**,
**proving ground**, **setup**; **terminal** and **dispatch** (stage 6), **tally** (7), **portrait** (8).
Words not used: *item* (say entry), *screen* for a page (a libGDX `Screen` is a class), *lobby* or
*match* (say session), *mount* (say crew / get on a turret), *arena* for the training mode (arena is
the rectangle a level is played inside), *focus* for the selected entry (focus is who the camera
keeps in shot).

## 2. Traceability to the original outline

| Outline | Becomes | Note |
|---|---|---|
| 1. MAIN MENU | `UI:main` | |
| 1.1 CAMPAIGN / 1.1.1 current scenarios | `UI:main/campaign` | Lists `Scenarios.ALL`, the whole registry (alpha-phase.md). |
| 1.2 ARENA / 1.2.1 placeholder | `UI:main/proving-ground`, `UI:main/proving-ground/upgrades` | Renamed 2026-09-25: *arena* is taken by the glossary. Built 2026-10-03 (G0060). |
| 1.3 PLAY ONLINE | `UI:main/online` | |
| 1.3.1 JOIN GAME / 1.3.1.1 | `UI:main/online/join` | The existing `JoinScreen`; restyled later. |
| 1.3.2 HOST GAME / 1.3.2.1 | `UI:main/online/host` | A scenario list whose confirm hosts. No lobby (G0038). |
| 1.4 OPTIONS / 1.4.1 placeholder | `UI:main/options` | Settings live in `assets/*.properties` in the alpha. |
| 1.5 EXIT | `UI:main/exit` | A confirm modal, so Back pressed once too often never quits. |
| - | `UI:connecting`, `UI:play`, `UI:play/pause`, `UI:play/leave`, `UI:play/result` | Not in the outline; needed to get into and out of a level. `UI:play/result` was the blocked `UI:play/game-over` until G0066. |
| - | `UI:main/missions`, `UI:main/online/host/missions` | Built 2026-10-05 (missions ticket 08, G0065, stage 9): the day's three missions, RANDOM and ENTER CODE; the same page opened from HOST GAME hosts what it starts. |

## 3. Page and modal table

| Id | Kind | Parent | Code anchor | Implemented by | Status |
|---|---|---|---|---|---|
| `UI:main` | page | - | `UiPage.MAIN` | `MenuScreen` | built |
| `UI:main/missions` | page | `UI:main` | `UiPage.MISSIONS` | `MenuScreen` + `MissionChoices` + `CodePicker` | built |
| `UI:main/campaign` | page | `UI:main` | `UiPage.CAMPAIGN` | `MenuScreen` + scenario list | built |
| `UI:main/proving-ground` | page | `UI:main` | `UiPage.PROVING_GROUND` | `MenuScreen` + `ProvingRows` + preview | built |
| `UI:main/proving-ground/upgrades` | page | `UI:main/proving-ground` | `UiPage.PROVING_UPGRADES` | `MenuScreen` + `ProvingRows` | built |
| `UI:main/online` | page | `UI:main` | `UiPage.ONLINE` | `MenuScreen` | built |
| `UI:main/online/join` | page | `UI:main/online` | `UiPage.ONLINE_JOIN` | `JoinScreen` / `JoinChoices` / `CodePicker` | built (on the canvas; the pad's letter picker) |
| `UI:main/online/host` | page | `UI:main/online` | `UiPage.ONLINE_HOST` | `MenuScreen` + scenario list | built |
| `UI:main/online/host/missions` | page | `UI:main/online/host` | `UiPage.HOST_MISSIONS` | `MenuScreen` + `MissionChoices` + `CodePicker` | built - `UI:main/missions` in host mode |
| `UI:main/options` | page | `UI:main` | `UiPage.OPTIONS` | `MenuScreen` | placeholder |
| `UI:main/exit` | modal | `UI:main` | `UiPage.EXIT_CONFIRM` | `MenuScreen` | built |
| `UI:connecting` | page | - | `UiPage.CONNECTING` | `PlayMenus` (no world yet) | built (a panel on the canvas; Back leaves) |
| `UI:play` | state | - | `UiPage.PLAY` | `ScenarioScreen` | built |
| `UI:play/pause` | modal | `UI:play` | `UiPage.PAUSE` | `PlayMenus` | built |
| `UI:play/leave` | modal | `UI:play/pause` | `UiPage.LEAVE_CONFIRM` | `PlayMenus` | built |
| `UI:play/result` | modal | `UI:play` | `UiPage.RESULT` | `PlayMenus` | built (G0066, G0067) |

`MenuScreen` stays the one libGDX `Screen` for every `UI:main...` page: pages are a stack inside it,
not a `Screen` each. Swapping `Screen`s disposes GL resources (`GameContext.queueScreen`), and a page
change must never cost that. `UI:play...` modals draw over `ScenarioScreen`; hiding that screen would
close the session (`ScenarioScreen.hide` calls `play.close()`).

## 4. Pages in detail

Entry schema, used below: `entry id | label key = default copy | intent on confirm | enabled when`.
Intents are `UiIntent` constants (module `ui`); what an intent does is the transition table in
section 5.

### `UI:main`

Title `ui.main.title = PROJECT-W`. Shows the notice line (`ui.main.notice`, the reason the player is
back here - an online game that ended under him) when there is one; that is today's
`MenuScreen(context, notice)`.

| Entry | Label | Intent | Enabled |
|---|---|---|---|
| `missions` | `ui.main.missions = DAILY MISSIONS` | `OPEN(MISSIONS)` | always - first entry, default selection |
| `campaign` | `ui.main.campaign = CAMPAIGN` | `OPEN(CAMPAIGN)` | always |
| `proving-ground` | `ui.main.provingGround = PROVING GROUND` | `OPEN(PROVING_GROUND)` | always |
| `online` | `ui.main.online = PLAY ONLINE` | `OPEN(ONLINE)` | always |
| `options` | `ui.main.options = OPTIONS` | `OPEN(OPTIONS)` | always |
| `exit` | `ui.main.exit = EXIT` | `OPEN(EXIT_CONFIRM)` | always |

Back on this page moves the selection to `exit`; it does not open the confirm and never quits.
Default selection: `missions`, or the entry selected when the player last left this page - missions are the
game's loop, the Adventures one press down.

### `UI:main/campaign`

Title `ui.campaign.title = CAMPAIGN`. One entry per scenario in `Scenarios.all()`, in registry
order: label is `Scenario.name()`, and the selected entry shows `Scenario.description()` under the
list, wrapped to at most two lines (section 8). Confirm: `START(scenarioIndex)`. Number keys `1`-`9`
jump and confirm, as `MenuScreen` does today. The list scrolls when it is taller than the page;
the selection drives the scroll. Selection logic is `MenuSelection`, which already exists and is tested -
reuse it, do not write a second list cursor.

Default selection: the scenario last played this session, else the first. A one-confirm CONTINUE is plan Q25.

Do not build: a separate campaign registry beside `Scenarios.ALL` (alpha-phase.md: "that list is the
whole registry"); a level-select map; progression or unlocks (open questions on progression).

### `UI:main/proving-ground`

Title `ui.provingGround.title = PROVING GROUND`. The page holds one **setup** (`ProvingSetup`, G0060),
remembered for the run and never saved; a first visit's is `ProvingSetup.first()` - three grunts, the
sidearm, no squad, no others, nothing held. Entries, top to bottom:

| Entry | Label | Value | Intent |
|---|---|---|---|
| `strain` | `ui.provingGround.strain = STRAIN` | `ui.strain.*`, every strain | `CHOOSE(0)` |
| `count` | `ui.provingGround.count = COUNT` | 1, 3, 5, 10, `ui.provingGround.nest = NEST`; held at 1 and dimmed for a rooted strain | `CHOOSE(1)` |
| `gun` | `ui.provingGround.gun = GUN` | `ui.weapon.*`, every gun with a weapon type - the ones a man carries | `CHOOSE(2)` |
| `squad` | `ui.provingGround.squad = SQUAD` | `ui.provingGround.on`/`.off` | `CHOOSE(3)` |
| `others` | `ui.provingGround.others = OTHERS` | `ui.provingGround.others.*`: none, 3 turrets, 3 mines, 3 barrels, 3 crates, 3 grenade caches | `CHOOSE(4)` |
| `upgrades` | `ui.provingGround.upgrades = UPGRADES`, with `ui.provingGround.held = {n} TAKEN` as its value | | `OPEN(PROVING_UPGRADES)` |
| `start` | `ui.provingGround.start = START` | | `PROVE` - default selection |
| `back` | `ui.common.back = BACK` | | `BACK` |

A **choice row** draws its value as `< VALUE >` after its label; left and right step it
(`ProvingRows.step`), and so does a click on either arrow; confirm steps it forward. To the right of the
entries, half a page wide, the **preview** - the panel and the window centred on the canvas as one; where
the two do not fit side by side the panel docks left and the window goes under it if there is room. It is
a window onto `Scenarios.preview`,
one of the chosen strain on sand going about its business, drawn by the world's own renderer and built
again when the strain changes; under it `ui.strain.*`, and `ui.provingGround.fromNest = FROM A NEST`
while the count says nest.

Confirm on `start` builds `Scenarios.provingGround(setup)` offline. Leaving it - LEAVE TO MENU, or a
game that ends - comes back to this page with the setup as it was and `start` selected; RESTART builds
the same setup again.

Do not build: the proving ground online; saving a setup; anything chosen here reaching a campaign level
(G0060).

### `UI:main/proving-ground/upgrades`

Two panels side by side, each half a page wide, centred as a pair: `ui.provingUpgrades.title = UPGRADES`
on the left with one choice row per players' upgrade, `ui.provingUpgrades.swarm = ADAPTATIONS` on the
right with one per swarm adaptation and then `back` - all built from the catalogue
(`ProvingRows.upgrades()`, the players' first). One list underneath: up and down run from the bottom of
the left panel into the right one, and the pointer selects in either. Label `ui.upgrade.*`; value its
rank in roman, `ui.provingGround.rankNone = -` for none, stepping from none to the upgrade's most and
round again. Neither panel scrolls - the catalogue fits the canvas.

### `UI:main/missions`

Missions ticket 08 (G0065, G0066), built in missions stage 9. Title `ui.missions.title = DAILY MISSIONS {date}
UTC`, the date ISO (`2026-10-05`), read when the page opens (`MissionChoices.open`, a UTC `Clock`, in `core`); a
daily keeps the day it was started on. What each entry starts is `MissionChoices` (tested); the page is
`MenuScreen`'s. Entries, top to bottom:

| Entry | Label | Value | Intent |
|---|---|---|---|
| `easy`, `medium`, `hard` | `ui.tier.*` - one per `TierKind`, in order | `ui.missions.won = ✓ {m:ss.t}`, the best time, once that day's daily is won on this machine (`MissionRecords`, G0067), else nothing | `MISSION(daily)` - starts at once |
| `random` | `ui.missions.random = RANDOM` | a choice row `< MEDIUM >`, `ui.tier.*`, left and right step it; remembered for the run, first MEDIUM | `MISSION(random)` - a seed drawn uniformly from [0, 25^6) with the client's own randomness, a new one each confirm |
| `code` | `ui.missions.code = ENTER CODE` | the code line in a code's shape, `MJGF-6___` - the dash there from the start, never typed | opens the picker, or starts what is typed |
| `back` | `ui.common.back = BACK` | | `BACK` |

Under the list, the selected daily's code: `ui.missions.selectedCode = CODE {code}` - the only place a code
is shown before it is played. Default selection: the entry last played from here, else `medium`.

**ENTER CODE** works as join's code line: a keyboard (and the Steam keyboard, STEAM+X) types into it at any
time, forgiven as the room code is - any case, spaces and dashes ignored - into a code's eight places, no more,
and `Ctrl+V` pastes a whole code in the line's place. **The first place takes only a tier's letter**: any
other, typed or starting a paste, is refused as it arrives and the line says `ui.missions.badTier = A MISSION CODE
STARTS WITH {E/M/H}` - the tiers, never the check (`MissionChoices`). With a pad,
confirm opens an eight-slot `CodePicker`, a dash drawn after the fourth: **slot one cycles only the tiers'
letters** (an alphabet per slot), the rest the room code's alphabet. It opens on the last code played this
run, else today's MEDIUM daily, so a code close to a known one is a few presses. Beside it, the tier's name
once the first slot holds one; under the page `ui.missions.pickHint = {confirm}: START  {back}: DONE  D-PAD:
EDIT`. Typing a character, or a paste, moves the selection to the code line and closes the picker. Confirm on a
code that fails its check shows `ui.missions.badCode = NOT A MISSION CODE` on the second line under the list and
starts nothing; the picker stays open on what was entered, with no hint which character
is wrong - showing the right check would let a typo through (G0065).

**A code is a daily when it equals one of the three dailies of the day the page opened**, however it
arrived - typed, pasted or picked; a machine that joined one, or never opened the page, looks on its own day and
the day either side (`MissionCode.dailyNear`). What is remembered of it is missions ticket 09.

From `UI:main/online/host` the same page opens **in host mode** as `UI:main/online/host/missions`: the same
entries, and every start hosts instead (`HOST_MISSION`), as the host list is the campaign list with a different
confirm. Its own id, so leaving a hosted mission comes back to it and Back climbs to HOST GAME.

Do not build: a daily's code on every row; a countdown to the next day; "this mission on another tier"
(deferred, G0066); a hint at which character of a refused code is wrong; correcting a refused code (G0065).

### `UI:main/online`

Title `ui.online.title = PLAY ONLINE`.

| Entry | Label | Intent | Enabled |
|---|---|---|---|
| `join` | `ui.online.join = JOIN GAME` | `OPEN(ONLINE_JOIN)` | always |
| `host` | `ui.online.host = HOST GAME` | `OPEN(ONLINE_HOST)` | always |
| `back` | `ui.common.back = BACK` | `BACK` | always |

### `UI:main/online/join`

Today's `JoinScreen`: sessions heard on this network (LAN or Hamachi), and a room code or an address
typed in; what Enter joins is `JoinChoices` (tested). Confirm: `JOIN(choice)`, which leads to
`UI:connecting`. Title `ui.join.title = JOIN GAME`; one entry per session heard, then the code line.
`JoinScreen` is its own libGDX `Screen` (it listens on the network while open) but draws on the canvas
with the same painter and selection as the other pages; Back returns to `UI:main/online`.

With only a pad, the sessions heard are one press away, and the room code is entered with a
four-slot letter picker (`CodePicker`, module `ui`) - confirm on the code line opens it, up/down
changes the letter, left/right the slot, confirm joins, Back closes it (plan Q6). Valve's Steam Deck
review fails a game whose text entry needs a keyboard. A keyboard types straight into the code line;
while it does, only the arrows move the selection and only `Esc` goes back.

Do not build: a player list, a ready button, choosing a slot, colour or character (G0038); a
password.

### `UI:main/online/host`

Title `ui.host.title = HOST GAME`. A first entry `ui.host.missions = DAILY MISSIONS` opening
`UI:main/online/host/missions`, the missions page in host mode; a mission travels as `SessionSetup("mission
<tier>", seed)` (G0065). Then the same scenario list widget as `UI:main/campaign` with a different confirm - its
number keys still jump to the scenarios: `HOST(scenarioIndex)`, which calls `OnlineSession.host(...)` and goes straight to
`UI:play`. The host is playing from the first frame; friends drop in (G0038). The room code reaches
the player as a terminal dispatch when hosting starts (plan section 8.3) and on `UI:play/pause`, not
only in the dev overlay (retires the alpha-phase scaffolding row about the room code).

Do not build: a waiting room, a host start button, a ready screen, a player list with kick, a slot,
colour or character choice (G0028, G0038); capacity as a setting (G0038).

### `UI:main/options` - placeholder

Title `ui.options.title = OPTIONS`. Body `ui.options.body = Settings live in assets/*.properties
during the alpha.` One entry, `back`.

Do not build now: widgets that write to `assets/*.properties` - those are shipped defaults, and a
player's settings belong in a user file that does not exist yet (open question in the plan).

### `UI:main/exit` - modal

Body `ui.exit.body = Quit Project-W?` Entries `stay` (`ui.common.no = NO`, intent `BACK`, default
selection) and `quit` (`ui.common.yes = YES`, intent `QUIT_APP`). Default selection is `stay` so a repeated
confirm press cannot quit by accident.

### `UI:connecting`

Shown while an online `ScenarioPlay` has no world: `ui.connecting.body = {connection}` - the line
`ScenarioPlay.connection()` already produces - and a hint `ui.hint.back`. Back: `LEAVE` to
`UI:main`. Today this is `DebugOverlay.renderWaiting`.

### `UI:play`

The level. The HUD fills the letterbox (section 7); no page is open; the mouse aims, the system
cursor is hidden and the reticle drawn (`UI:hud/view/reticle`). `Esc` or pad `START` opens
`UI:play/pause`. Paused by somebody else - another machine, or the harness's `P` - with no menu open
here: the paused banner (`UI:hud/view/paused`), no page, no selection taken.

### `UI:play/pause` - modal

**The pause is shared; this menu is not.** `Esc` or `START` opens it on this machine at once and asks
for the pause if the session is not already paused; online the world stops when the command lands a
few ticks later, under the scrim, with this machine's commanders on neutral input meanwhile. It closes
on RESUME or BACK here, and whenever `ScenarioPlay.paused()` turns false, whoever resumed. Other
machines show the paused banner, not this menu (alpha-phase.md: a pause is anybody's and pauses every
machine). Drawn docked to the left third of the view over a scrim (plan 6.5); the bars stay.

| Entry | Label | Intent | Enabled |
|---|---|---|---|
| `resume` | `ui.pause.resume = RESUME` | `RESUME` (sends `HarnessCommand.PAUSE` through `ScenarioPlay.apply`) | always |
| `restart` | `ui.pause.restart = RESTART` | `RESTART` (`HarnessCommand.RESTART`) | offline, or this machine is the host |
| `leave` | `ui.pause.leave = LEAVE TO MENU` | `OPEN(LEAVE_CONFIRM)` | always |

Beside it, docked to the right of the view over the same scrim (`MenuLayout.beside`,
`HeldUpgradesPainter`): `ui.pause.upgrades = UPGRADES` and every upgrade the side holds, one a row -
`ui.upgrade.*` and its rank in words, a personal one prefixed `P{n}` and in his colour, rank pips at
the right - or `ui.pause.upgradesNone = NONE TAKEN YET`. The top bar's aside has room for a few; a
paused player has time for all of them. As wide as its longest row and never wider than the room right
of the pause panel, so the two never overlap; under the pause panel where there is no room beside it.
More rows than it has lines for scroll (`ScrollList`) - the mouse wheel over it, any pad's right stick
at `controls.ui.nav.scrollLinesPerSecond` - with a scrollbar down its right edge. Built (G0049).

Under the entries, one line per local player with his tally in words - `P1  KILL COUNT: 1234  MISSION
GOALS: 2  UNITS HEALED: 2` - the labelled form the panel has no room for, and whole numbers.
In a mission, one more line, `ui.pause.mission = {code}  {tier}  TIME {m:ss.t}` - the only place the code and
the time are seen during play; the HUD's mission line says `ui.hud.missionDaily = DAILY {tier}` or `{tier}`, never
the code or the time (missions stage 9).
Online it also shows `ui.pause.room = ROOM {code}` and the addresses, from `Invitation`, while this
machine hosts. Back = `RESUME`. The press that resumes is spent: it does not fire, act or throw on
the first tick of play (plan 6.2, `ReleaseLatch`).

### `UI:play/leave` - modal

Body `ui.leave.body = Leave the mission?`, and online as host `ui.leave.bodyHost = Leaving ends the
session for everyone.` Entries `stay` (default selection, `BACK`) and `leave` (`LEAVE`). Also opened over
`UI:play/result` by the host's MENU; `stay` goes back to the result there.

### `UI:play/result` - modal

G0066, for every level with an objective. **The first outcome is final**: the tick the objective is met
wins, the tick G0055 loses the level loses, and nothing after it changes either. The world runs on; the
goal line pulses; after `graphic.ui.resultHoldSeconds` (3), the same for both and not skipped by a press,
this opens over the scrim, docked as the pause menu is. Every machine shows its own. The hold is the
world's time since the outcome (`Readout.resultDue`), so a pause holds it too; a pause menu open when it
runs out is left alone and the result opens once it closes. A level that names no objective has no result:
its loss is the goal line's alone. Built for every such level, the time with it; a mission's lines and COPY
CODE built with the DAILY MISSIONS page (stage 9); NEW BEST with the record (stage 10, G0067).

Title `ui.result.won = MISSION COMPLETE` or `ui.result.lost = MISSION FAILED`. Under it, on a win only and
for every level, `ui.result.time = TIME {m:ss.t}` (`LevelTime`, the tenths cut; compared in ticks). In a
mission, also: `ui.result.points = POINTS {taken}/{all}`; on a win only `ui.result.best = NEW BEST` when it
beats the remembered one, or is that daily's first win on this machine (G0067); `ui.result.code = {code}  {tier}`, and `ui.result.daily = DAILY {date}` for a daily.

| Entry | Label | Intent | Enabled |
|---|---|---|---|
| `retry` | `ui.result.retry = RETRY` | `RESTART` - the same scenario and seed | offline, or this machine is the host - default selection |
| `copy` | `ui.result.copy = COPY CODE` | `COPY_CODE` - the code to the clipboard (libGDX `Clipboard`) | a mission only (`MenuTree.result`) |
| `menu` | `ui.result.menu = MENU` | `LEAVE`; as host through `UI:play/leave` | always |

Back does nothing here - a lost level does not close by an accidental press. MENU returns to the page the
level was started from (section 5). It closes on RETRY, and on its own when the world it judged is replaced
- the host's RETRY seen from a joined machine.

Do not build: pausing the world under it; a time after a loss; skipping the hold; another tier from here
(G0066).

## 5. Transition table

`UiFlow` (module `ui`, pure, tested) owns this. `(page, intent) -> (next page, effect)`. Effects are
carried out by the screen that owns the page, through `GameContext.queueScreen` or `ScenarioPlay`.

| From | Intent | To | Effect |
|---|---|---|---|
| any `UI:main...` page | `OPEN(p)` | `p` pushed | - |
| any `UI:main...` page except `UI:main` | `BACK` | pop | - |
| `UI:main` | `BACK` | `UI:main` | the selection moves to `exit` |
| `UI:main/exit` | `QUIT_APP` | - | `Gdx.app.exit()` |
| `UI:main/campaign` | `START(i)` | `UI:play` | `queueScreen(new ScenarioScreen(ctx, new ScenarioSession(...)))` |
| `UI:main/proving-ground`, `.../upgrades` | `CHOOSE(r)` | - | the screen steps row `r` of its setup forward |
| `UI:main/proving-ground` | `PROVE` | `UI:play` | `queueScreen(new ScenarioScreen(ctx, new ScenarioSession(Scenarios.provingGround(setup), ...)))`; leaving comes back to `UI:main/proving-ground` |
| `UI:main/online/host` | `HOST(i)` | `UI:play` | `queueScreen(new ScenarioScreen(ctx, OnlineSession.host(...)))`; on failure stay and show the notice |
| `UI:main/online/join` | `JOIN(c)` | `UI:connecting` | `OnlineSession.join(...)` |
| `UI:connecting` | world arrives | `UI:play` | - |
| `UI:connecting` | `BACK` / ended | `UI:main` | `queueScreen(new MenuScreen(ctx, reason))` |
| `UI:play` | `PAUSE` (Esc, pad START) | `UI:play/pause` at once | `play.setPaused(true)` unless already paused; this machine's commanders on neutral input until it lands |
| `UI:play` | somebody paused (another machine, or `P`) | `UI:play`, paused banner shown | - (derived; no page, no selection taken) |
| `UI:play/pause` | `RESUME` / `BACK` | `UI:play` at once | `play.setPaused(false)` - also for a pause still on its way; the press is spent (`ReleaseLatch`) |
| `UI:play/pause`, `UI:play/leave` | anybody resumed | `UI:play` | - (derived) |
| `UI:play/pause` | `RESTART` | `UI:play` | `play.apply(HarnessCommand.RESTART, ...)` |
| `UI:play/pause` | `OPEN(LEAVE_CONFIRM)` | `UI:play/leave` | - |
| `UI:play/leave` | `LEAVE` | the page the level was started from, as `UI:play/result`'s MENU | `queueScreen(new MenuScreen(ctx, "", back))`; the screen's `hide()` closes the play |
| `UI:play` | `play.ended() != null` | `UI:main` | `queueScreen(new MenuScreen(ctx, reason))` - built today |
| `UI:main/online/host` | `OPEN(HOST_MISSIONS)` | `UI:main/online/host/missions` pushed | - |
| `UI:main/missions` | `MISSION(m)` | `UI:play` | `queueScreen(new ScenarioScreen(ctx, new ScenarioSession(Scenarios.mission(tier), seed), MISSIONS))` |
| `UI:main/online/host/missions` | `MISSION(m)` -> `HOST_MISSION(m)` | `UI:play` | `OnlineSession.host(...)` with `SessionSetup("mission <tier>", seed)`; on failure `UI:main` with the notice |
| `UI:main/missions`, `.../host/missions` | code refused | stay | `ui.missions.badCode`, the picker open on what was entered |
| `UI:play` | first outcome + `graphic.ui.resultHoldSeconds` | `UI:play/result` | - (derived, `UiFlow.observeResult`) |
| `UI:play/result` | `RESTART` | `UI:play` | `play.apply(HarnessCommand.RESTART, ...)` - host or offline |
| `UI:play/result` | `COPY_CODE` | stay | the mission code to the clipboard |
| `UI:play/result` | `LEAVE` | the page the level was started from - `UI:main/missions`, `UI:main/online/host/missions`, `UI:main/campaign`, `UI:main/online/host`, `UI:main/proving-ground` - with what was played selected; a joined machine `UI:main` | `queueScreen(new MenuScreen(ctx, "", back))`; as host via `UI:play/leave` |

A screen never switches itself mid-frame: every `queueScreen` in this table is already the deferred
swap (`PwtGame.queueScreen`).

## 6. Input per context

Bindings are read from `assets/controls.properties` (menu keys proposed in the plan, section 11). Pad
buttons are named by position (ADR 0011); what the player is told to press comes from `PadLabels`.

| Semantic | Keyboard | Mouse | Pad | Context |
|---|---|---|---|---|
| Move the selection | arrows, `W`/`S` (lists), `A`/`D` (rows) | hover | d-pad, left stick (threshold, repeat) | pages, modals |
| Confirm | `Enter`, `Space` | left click on an entry | `SOUTH` (A / Cross) | pages, modals |
| Back | `Esc`, `Backspace` | right click | `EAST` (B / Circle) | pages, modals |
| Jump to entry | `1`-`9` | - | - | scenario lists |
| Paste a code | `Ctrl+V` | - | - | the code line of `UI:main/missions` (built); of `UI:main/online/join` (planned) |
| Step a choice row | `A`/`D`, left/right arrows | left click on `<` or `>` | d-pad left/right, left stick | `UI:main/proving-ground`, `.../upgrades`, `UI:main/missions` (RANDOM's tier) |
| Page tabs (future Options) | `Q` / `E` | - | `L1` / `R1` | pages with tabs |
| Swap panels / roll call | `` ` `` | - | - | `UI:play` |
| Pause | `Esc` | - | `START` | `UI:play` |
| Open his oldest upgrade draft; with it open, take the highlighted card | `C` | - | `NORTH` (Y / Triangle) | `UI:play`, `UI:hud/top/draft` |
| Choose a card | `1`-`3` | - | d-pad right / left | `UI:hud/top/draft`, the seat that opened it only |
| Close the draft and keep it | `4` | - | d-pad down | `UI:hud/top/draft` |
| Open the crate in reach | `E` | - | `SOUTH` (A / Cross) | `UI:play`, then `UI:hud/top/crate` - not sent while this machine's top bar is taken |
| Highlight a card of the crate | `1`-`4` | - | d-pad right / left | `UI:hud/top/crate`, its opener's seat only |
| Take the highlighted card | `E` | - | `SOUTH` (A / Cross) | `UI:hud/top/crate` |
| Shut the crate, taking nothing | `X` (`controls.key.crateClose`) | - | d-pad down | `UI:hud/top/crate` |

While a draft is open its seat's order inputs work it and give no order; his move, aim, fire, strike,
grenade and action stay live (G0049), and every other seat plays on (`DraftKeys`). A menu opening closes
it.

`Esc` in `UI:play` changes meaning: today it is the harness's "back to the scenario menu"
(alpha-phase.md). The same change edits that harness table and the input map in
`docs/design/README.md`. `P` stays the harness pause and opens no menu - the paused banner shows, so
single-stepping with `.` still sees the world.

**A press that changes context is spent** (plan 6.2). Every key, button and trigger held when a menu
opens or closes, a level starts or a page swaps is ignored by the game until it has been released
once - otherwise clicking RESUME fires, `SOUTH` on RESUME acts, and the `Enter` that started a level
reaches its first tick. Pages are not affected: a held direction still repeats.

**Modality** (`InputModality`, module `ui`, pure): the last device that did something meaningful owns
the look of the selection.

- Pointer mode: entered on a mouse move of at least `ui.nav.pointerWakePixels` or any mouse button.
  Cursor shown, the hovered entry is selected, no selection ring when nothing is hovered, `Enter` still
  confirms the last selected entry.
- Digital mode: entered on any navigation key or pad input past the stick threshold. Cursor hidden,
  selection ring shown; if nothing is selected, the page's remembered entry, else its default.
- Footer hints follow the mode and the last pad's label family: keyboard text in pointer mode or after
  a key, `PadLabels.of(button, playStation)` after a pad.
- Any seated pad may drive a page or modal; online, only this machine's devices do.
- In `UI:play` the mouse is the keyboard player's aim and modality is not consulted; opening a modal
  switches to pointer or digital by whichever device opened it.

## 7. HUD slots

| Id | Code anchor | Where | Shows | Status |
|---|---|---|---|---|
| `UI:hud/top/mission` | `HudSlot.MISSION` | top bar, left, line 1, dim | `ui.hud.planet = PLANET:` `{planet}` `ui.hud.mission = MISSION:` `{scenario name}` - a mission's `ui.hud.missionDaily = DAILY {tier}` or `{tier}`, never its code or time; the planet half hidden while `Scenario.planet()` is empty, and the line cut short of the army, which is centred on the bar's span on the same line, bright: `ui.hud.army = ARMY {on field}/{most} RES {reserve}` (G0055) | built |
| `UI:hud/top/goal` | `HudSlot.GOAL` | top bar, left, line 2, bright | `ui.hud.goal = CURRENT GOAL:` `{goal}` - first unmet required objective, or `ui.hud.goal.complete = MISSION COMPLETED`, pulsing twice when it becomes true; over everything else `ui.hud.goal.failed = MISSION FAILED` once the level is lost (G0055); `ui.goal.REGROUP` with how many of how many are in place while a lair waits on some of the players (B.1); on a level whose post is not stood yet - its waves still held back, a mission's march - `ui.goal.CAPTURE_POINTS` over every point, the finale's among them, then `ui.goal.HOLD_POINT` once it is taken (G0064) | built |
| `UI:hud/top/terminal` | `HudSlot.TERMINAL` | top bar, left, lines 3-4 | the last two dispatches - the battlefield's voice, narrative only, nothing the goal line or a panel shows (plan 8.3) - older dimmed, newest typing with a cursor (a high one appears at once) | built (`Terminal`, the reporters, `TopBarPainter`) |
| `UI:hud/top/aside` | `HudSlot.ASIDE` | top bar, right: the block beside the goal, `graphic.hud.overlay.widthShare` of the view's span | what the side holds, one upgrade a row, `ui.upgrade.*` name - a personal one prefixed `P{n}` and in his colour - and its rank as pips, filled to its ranks out of its most; a last row `ui.hud.aside.more = +{n} MORE` when more is held than fits. A first answer to plan Q30; the aliens' adaptations are in it too, prefixed `ui.hud.swarm = SWARM` and written in `graphic.hud.color.swarm` (upgrades S10), as they are in the pause menu's list. The dev overlay's panel takes it while F1 steps it on | built (upgrades S5) |
| `UI:hud/top/overlay` | `HudSlot.OVERLAY` | top bar, right, inside `UI:hud/top/aside` | the F1 dev overlay (`DebugOverlay`), clipped to its panel; off when a level starts | built (F1 steps off / panel / expanded) |
| `UI:hud/top/draft` | `HudSlot.DRAFT` | the whole top bar, instead of mission, goal, terminal and aside, while a draft is open (G0049) - one per machine, opened by `controls.key.draft` / `controls.pad.draft` | on the left the chooser's portrait block and `ui.hud.player`, with `ui.draft.count = {n}/{m}` under it while he holds more than one; across the rest his cards (`DraftLayout`), each framed - the highlighted one in his colour - with `ui.upgrade.*` and the rank it will have once taken, up to two effect lines generated from its definition (`ui.effect.*`), and a hint (`ui.draft.take`, `ui.draft.choose`). Opening pauses a solo game with no banner, closing resumes (D2); the terminal holds its queue while it is open | built (upgrades S5) |
| `UI:hud/top/crate` | `HudSlot.CRATE` | the whole top bar, instead of mission, goal, terminal and aside, while a crate is open (G0056) - one crate or one draft per machine; opened by the action key on a crate within reach, in the world, and followed here (`CrateBar`) | on the left the opener's portrait block and `ui.hud.player`; across the rest the crate's cards (`DraftLayout`, up to four), each framed - the highlighted one in his colour - with the gun's `ui.weapon.*` or `ui.crate.*`, what taking it does (`ui.crate.effect.*`) or, for one he would be turned down for, dimmed with `ui.crate.refused.*` (why: no wound, grenades full), and a hint (`ui.crate.take`, `ui.draft.choose`). Opening pauses a solo game with no banner, as a draft does (D2); the terminal holds its queue while it is open | built (G0056) |
| `UI:hud/bottom/panel` | `HudSlot.PLAYER_PANEL` | bottom bar: four fixed quarters of the view's span. With four players or fewer in the game, local and online, everybody has one - this machine's seats first, then the rest by slot; with more, this machine's seats only, each in its seat's quarter (`PanelAssignment`). A player keeps his quarter while he is in the game; a quarter nobody holds stays black | frame in that player's commander colour, with `ui.hud.player = P{n}` in it; grey with `ui.hud.offline = OFFLINE` while his pad is unplugged or his link is gone (the host seals a dropped link as away, G0052) | built |
| `UI:hud/bottom/portrait` | `HudSlot.PORTRAIT` | inside each player panel, left; dropped in a quarter under 160 UI px (4:3) | animated portrait (plan section 10.5), 32 x 32; a block in his colour until stage 8 | built as a block in his colour; frames planned (stage 8) |
| `UI:hud/bottom/status` | `HudSlot.STATUS` | inside each player panel, row 1, after the label | while he stands: a narrow diamond per wound he can carry, filled in `graphic.hud.color.wound` for each he carries, hollow and dim for the rest (G0053), then his shield and stun; `ui.hud.down = DOWN` with his bleed-out as a draining bar; `ui.hud.kia = KIA {seconds}`, or `ui.hud.kiaNoReserve = KIA NO RESERVE` while nobody is in reserve to bring him back (G0055); `ui.hud.aboard = ABOARD`. `DOWN` and `KIA` in alert. No health bar: an outcome is non-lethal (DOWN) or lethal (KIA) (plan D11) | built |
| `UI:hud/bottom/weapon` | `HudSlot.WEAPON` | inside each player panel, row 2 | `ui.weapon.*` name (≤ 7 characters) and a heat bar in the world's heat colours; while overheated `ui.hud.overheat = OVERHEAT` in alert in the bar's place. At the row's right end a grenade per one he can carry, filled in `graphic.color.grenade` for each he has, hollow and dim for the rest, all dim while he is down (G0077) - the heat bar shortens for them and `OVERHEAT` is cut to what is left | built |
| `UI:hud/bottom/tally` | `HudSlot.TALLY` | inside each player panel, row 3, dim | three icons and per-life numbers - kills, goals, stabilised - `999+` above 999; the labelled form (`ui.hud.kills = KILL COUNT:`, `ui.hud.goals = MISSION GOALS:`, `ui.hud.stabilised = UNITS HEALED:`) is on `UI:play/pause` | built in the panel (`TallyIcon`, G0044); the pause menu's labelled form planned |
| `UI:hud/bottom/upgrade` | `HudSlot.UPGRADE_TELL` | inside each player panel, row 1, right end, in his colour | while his slot holds drafts: `ui.hud.upgrade = UPGRADE {key}`, `ui.hud.upgrades = UPGRADE x{n} {key}`, or beside a status `ui.hud.upgradeBrief = +{n} {key}` (`UpgradeTell`), lit so it is not overlooked - a backing in his colour at `graphic.hud.upgrade.glow`, text breathing towards white by `.lift` at `.pulseHz`, held still with flashes off; never over the status, and a bleed-out bar shortens for it | built (upgrades S5) |
| `UI:hud/view/paused` | `HudSlot.PAUSED` | over the view, top, over a light scrim | `ui.hud.paused = PAUSED` and `ui.hud.pausedHint = {key}: MENU`, while paused with no menu open on this machine | built |
| `UI:hud/view/toast` | `HudSlot.TOAST` | over the view, top, where the paused banner goes; held and not drawn while a menu or the banner is up here | an upgrade earned, passed on or taken, or the swarm adapting - the terminal's own words (`ui.dispatch.DRAFT_EARNED`, `.DRAFT_PASSED`, `.UPGRADE_TAKEN`, `.SWARM_ADAPTS`) - framed and written in that player's colour (the text colour while he is the only player) or `graphic.hud.color.swarm`, typed with a cursor at `graphic.hud.toast.charsPerSecond`, held `.holdSeconds` once typed, then gone; one at a time, `.queue` waiting (`Toast`, `ToastPainter`) | built |
| `UI:hud/bottom/roll-call` | `HudSlot.ROLL_CALL` | bottom bar, instead of the panels, swapped by `controls.key.rollCall` (`` ` ``); `graphic.hud.rollCall` opens a level on it | one compact row per player - colour, status, tally - for games of more than four. `ui.hud.rollCall = ROLL CALL - PLACEHOLDER` for now | placeholder; design open (plan section 19) |
| `UI:hud/view/reticle` | `HudSlot.RETICLE` | over everything: the keyboard player's at the pointer's screen position, a pad player's `graphic.hud.reticle.padDistance` world units along his aim | the player's reticle, in his colour, outlined (plan 7.5). Each off by default (`graphic.hud.reticle.keyboard`, `.pads`); with the keyboard's off, the system pointer shows | built |

Four panels always fit: a quarter is 160 UI px on every 16:9 and 16:10 screen, and the plan's pixel
budget (7.2) holds label, status, weapon and tally in a 19-character text column. A quarter under 160
(4:3) drops the portrait and nothing else. Panels never move when a player joins or leaves
(`PanelLayout`, plan 7.2).

Do not build: a minimap anywhere (G0008); the action prompt in the terminal (it belongs beside the
commander - G0021 and the alpha-phase row); anything drawn over the view in `UI:play` except modals,
the scrim, the paused banner, the toast, the reticle and world-space marks; a full-screen effect for one
player's state - the screen may be shared (plan section 2).

## 8. Text rules (all pages and slots)

- One monospace pixel font, drawn at integer scale on the UI canvas; widths are `chars x advance`, so
  every fit check is arithmetic and testable without GL.
- Legibility floor: capitals ≥ 6 UI px, digits and lowercase ≥ 5, so at `s = 2` (720p, 768p, the
  Steam Deck) nothing is under 10 screen px. The font must also cover Latin Extended-A (plan Q3).
- Single-line slots truncate; the argument is shortened before the template, and the cut ends in
  `...` (ASCII - a pixel font may lack the ellipsis glyph).
- Scenario descriptions wrap on words to two lines, then truncate.
- Numbers in tallies are right-aligned in a fixed width; in the panel, above 999 they show `999+`.
- A glyph the font does not have draws as `?`; a missing i18n key draws as `#key` and is logged once.
- Every template is also checked in a pseudo-locale 30 % longer with diacritics; slots degrade, never
  overflow. HUD and menu labels are capitals, as in the mock-ups.

## 9. Placeholder assets

All paths are constants in `Assets` (nothing else names an asset by string). Sources go in
`assets-raw/` and `./gradlew :core:packTextures` packs them into `assets/atlas/game.atlas`; the region
name is the source path without `.png`.

| Source (`assets-raw/`) | Region | Used by | Size (UI px or texels) |
|---|---|---|---|
| `ui/pixel.png` | `ui/pixel` | bars, fills, lines | 1 x 1 white |
| `ui/panel.9.png` | `ui/panel` | page and modal frames | nine-patch, 12 x 12 |
| `ui/overlay.9.png` | `ui/overlay` | dev overlay border (red, as the mock-up) | nine-patch, 8 x 8 |
| `ui/selection.9.png` | `ui/selection` | selection ring | nine-patch, 8 x 8 |
| `ui/cursor.png` | `ui/cursor` | pointer in pages | 8 x 8 |
| `ui/portrait/commander_<pose>.png` | `ui/portrait/commander_<pose>` | portrait; poses `idle blink fire strain down kia` | 32 x 32, greyscale, tinted per player |
| `ui/reticle.png` | `ui/reticle` | the keyboard player's aim point | 9 x 9, white with a one-texel dark outline, tinted |
| `ui/icon/kills.png`, `goals.png`, `healed.png` | `ui/icon/...` | the tally's compact form - a skull, a flag, a green plus (never a red cross) | 7 x 7 |
| `puppet/commander/...` | see plan section 10.6 | the commander in the world | 32 x 32 per frame |

Not in the atlas: `assets/ui/fonts/monogram/monogram-bitmap.json` (monogram by Vinícius Menézio,
CC0, credits beside it; built into a font at start-up by `UiFont`, falling back to the hand-drawn
`PixelFont`), `assets/ui/skin.json` (scene2d skin naming the regions above),
`assets/i18n/ui.properties` (every label key in this file). Every one of them has a programmatic
fallback, so a missing file degrades the look and never stops the game (plan section 13).

## 10. Motion, feedback and accessibility (all pages and slots)

The rules are the plan's (sections 6.4, 6.5, 7.0, 7.4); what every page and slot must honour:

- Motion says that something changed or it is not built. Pages move within 0.12 s, the scrim within
  0.08 s, the selection instantly - and a press during any of it finishes it and is applied. Nothing moves
  in the bars when nothing changed.
- Everything that moves or flashes reads `graphic.ui.motionScale` / `graphic.ui.flashScale`; 0 turns
  it off. Nothing flashes more than three times a second.
- HUD text is neutral. A player's colour marks only him (his frame, his label, his reticle). Alert red
  is for DOWN, OVERHEAT and KIA, inside the bars, as black text on red. Nothing UI-red over the view.
- Never colour alone: every state has a word or a shape as well.
- Hints show the glyphs of the device the player last used.
