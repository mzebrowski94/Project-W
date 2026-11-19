# G0101: A hosted session opens on a briefing, and the host starts its first tick

**Status.** accepted

**Trigger.** Anything a hosted session shows before its first tick or between its missions; the HOST GAME and JOIN
GAME pages; what the host's MENU does after a mission; a ready button, a kick, or anything a joiner chooses before he
plays.

**Enforced by.** `SessionTest` (a session waits on its briefing with no world, its players listed host first; START puts every
seated slot on the field at the first tick; a machine arriving after START drops in and one before lands on the briefing;
the session goes back to its briefing, slots kept; a machine whose link is gone stays a row, offline, and takes its slot back),
`OnlineSessionTest` (landing, START, back to the briefing, the host's line shown to a joiner), `BriefingPageTest` and
`UiFlowTest` (rows, START's enablement, a joiner changing nothing, the leave confirm, the host's MENU, HOST GAME's choice rows),
`HostChoicesTest` and `OnlineSessionRelayTest` (HOST GAME's WHERE and PASSWORD, the relay's code at the briefing's head). Built on the LAN
and through the relay;
not tested: how the page is drawn, and a pad plugged in while the briefing shows (it takes its slot once play begins).

**Asked for** by Mateusz, 2026-11-19, at the close of the relay's rooms ticket: a page before the first tick with the
players who have joined and the mission's options, the host starting the game, and drop-in still open mid-game.

**Amends G0038 and G0066.** G0038's host "playing from the first tick", with no lobby and no start button, becomes a
host who starts the first tick from a briefing; G0066's MENU, which ends the session for the host, takes the session
back to its briefing instead. G0028 is untouched: offline there is no briefing, and plugging a pad in is joining.

**Constraint.**

- **A hosted session opens on a briefing**, on the LAN and through the relay alike. Offline there is none: START
  MISSION plays at once, and a pad plugged in is a player at once (G0028).
- **HOST GAME says how the session opens**: a choice row `< INTERNET / THIS NETWORK >` - a room at the relay, or the
  LAN and Hamachi only - a password line for the internet only, optional, picked as a code is, and OPEN. The session
  is open - the room opened, the code handed out - from the moment the briefing shows, before a mission is chosen, so
  friends join while the host chooses.
- **The briefing is the missions page in host mode** (`UI:main/online/host/missions` becomes `UI:briefing`), with the
  room code and password at its head and the players beside its entries. Every entry writes into the code line as on
  DAILY MISSIONS - the dailies, RANDOM, the length (G0102), ENTER CODE - and START MISSION is the host's start, enabled
  whenever the line names a mission, alone or with seven others.
- **A joiner sees the same briefing and changes nothing on it**: the host's code line, tier and length as they
  change, the players, and `WAITING FOR THE HOST`. His machine's pads take slots there as they do anywhere.
- **The players are rows, not entries**: one per slot, in its commander colour - `P3` and the name of the machine it
  plays from, the host's machine first, then by slot; `OFFLINE` while its link is gone, the slot kept (G0038). Nothing
  on a row can be selected.
- **START puts every seated slot on the field at the first tick**, at the spawn, as a scenario's own commanders are -
  not through the join. A machine that arrives after START drops in as G0038 says.
- **After a mission the session goes back to its briefing.** RETRY is the same setup again, straight into play
  (G0066). The host's MENU takes every machine back to the briefing, the session kept, so the next mission needs no
  reconnecting; his Back from the briefing ends the session, through the leave confirm when anybody has joined. A
  joiner's MENU or Back leaves only him. A machine that arrives while the session is on its briefing lands on it.
- **The developer's scenario list hosts straight into play** (`graphic.ui.page.scenarios`, off as shipped) - the one
  hosted session with no briefing.

**Why.** G0038 rejected a lobby because drop-in already does what a lobby is for: nobody has to wait for anybody.
Once a mission is under way that still holds. Before the first tick it did not: the friends of a host who was still
choosing waited anyway, on a JOIN page with nothing to join, and a mission started before they arrived started
without them - they landed mid-march in a fight the host had begun alone. The briefing turns that wait into watching
the choice, and puts everybody on the field at the start. Nobody is held by anybody: the host starts when he likes,
and whoever is late drops in. Pillar 3 is not touched - nothing on the briefing is per player or changes a man's kit;
it chooses the mission, never the squad, so it is not a loadout screen.

**Scope.** A hosted session before its first tick and between its missions. Not drop-in during a mission (G0038),
not offline play (G0028), not how a machine reaches the session - the relay, the room and the password are the
relay's rooms ticket - and not what a mission's length is (G0102).

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A ready button, or a start that waits until everybody is ready.* One slow or absent friend would hold the
rest; the host starts when he likes, and a late friend drops in.

**No:** *Kicking a player from the briefing.* A kicked machine types the code again; a kick that holds needs a ban,
and a ban needs an identity the game does not have - the machine id is random and the code grants nothing.

**No:** *A joiner choosing his slot, colour or character on the briefing.* G0038's reason; the briefing is not the
page that choice was missing.

**No:** *A joiner changing the mission, or a vote on it.* The host's config is the session's (G0038).

**No:** *A second page after the missions page.* Two places that show the mission, and a Back on the second that has
to ask what becomes of the friends on it.

**No:** *A briefing offline.* There is nobody to wait for, and a pad plugged in is already a player (G0028).

**Revisit when** a group has sat on a briefing: whether the host choosing while his friends watch reads as being
shown round or as waiting; and when host migration lands, since whoever takes over the session takes over its
briefing too.
