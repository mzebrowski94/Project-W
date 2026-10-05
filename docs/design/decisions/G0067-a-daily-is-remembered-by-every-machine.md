# G0067: A daily is remembered as a ✓ and a best time, by every machine that saw it won

**Status.** accepted

**Trigger.** Changing what a machine remembers of a mission, where it keeps it, when it writes it, who writes it,
or how long it is kept; tying a record to a build; carrying anything about the record in a session.

**Enforced by.** Nothing yet - missions are not built. When they are: a `MissionRecords` test that only a won daily
is written, that a better time replaces a worse and an equal one does not, that a garbled key is skipped and the
rest still read; a test that a joiner files a daily started the day before its own under that day. Missions
ticket 09 holds the reasoning.

**Constraint.**

- **The mission record** is one key per won (UTC day, tier) - `20261005.H = 41237`, the day as `yyyymmdd`, the
  tier's letter as in the code, the best time in ticks. A key there is the ✓. A loss writes nothing; a mission that
  is not a daily writes nothing (codes are not remembered).
- **Kept in libGDX `Preferences`**, a store of its own (`pl.mzebrows.pwt.missions`) beside the machine id's, in
  libGDX's per-OS prefs folder. One `core` class, `MissionRecords`, reads and writes it; `ui` is handed values.
- **Kept forever.** Three short keys a day; nothing prunes.
- **Written on the outcome tick**, as the client sees it in the readout - the moment `NEW BEST` is decided. A
  machine that drops or quits during the hold still writes; one gone before that tick does not.
- **Every machine present at the win writes its own**, a late joiner too. The time counts from the world's first
  tick, so it is the same number on every machine (pillar 7).
- **A joiner files a daily** by matching the seed against `daily(d, tier)` for its own UTC day and the days either
  side; a match is the daily of that day. Offline, ENTER CODE matches only the day the page opened (G0065).
- **NEW BEST** only for a time strictly lower than the remembered one.
- **An unreadable key** is logged and skipped; an unreadable file is read as empty. Records degrade; play never
  stops.

**Accepted costs.**

- A new build may lay a different map from a day's seed (F, out of scope). The ✓ and best stand, and that day's
  next time is measured against a slightly different map.
- Two copies on one computer share the store; libGDX loads it once and flushes it whole, so the last to flush
  wins. Both saw the same outcome on the same tick, so they write the same value.

**Why the outcome tick, not the screen.** The outcome is final on its tick (G0066); the three seconds after are
presentation. A record that depended on staying for them would be lost to a dropped link after a won fight.

**Why a late joiner counts.** It is one fight and the squad's time. The record is a ✓ and a number, not a prize
(pillar 3); guarding it would mean a machine remembering what it missed.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Pruning old days*, by a key or otherwise. Nothing reads them and they cost nothing.

**No:** *The `BuildId` filed with a best.* A field and a rule for a one-day annoyance.

**No:** *The day carried in `SessionSetup`.* A protocol change for what a three-day match settles (G0065).

**No:** *A late joiner's time withheld*, or a ✓ without a best for him.

**No:** *A record of a code that is not a daily.*

**No:** *A record through `GameProperties` or `ConfigFile`.* Both read shipped text; neither writes.

**Revisit when** players report a best they cannot beat after an update, the store grows big enough to notice,
something shows past days, or results are compared between players (needs a server, out of scope).
