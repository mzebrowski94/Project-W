# What is remembered and where

Type: grilling
Status: resolved
Blocked by: 05

## Question

Where the local record of (UTC date, tier) -> ✓ + best ticks is kept (which existing file mechanism -
`GameProperties`, `ConfigFile`), how long old days are kept, what a machine writes after an online
mission, and what happens to a record when the build changes a daily's map.

## Answer

Grilled 2026-10-05 (Q1-Q8, G0067). Settled before the session: per (UTC day, tier) a ✓ and the best time in ticks; a
code equal to one of that day's three dailies is that daily, however it was entered; a daily keeps the day it was
started on; the first outcome is final and its tick is the time; codes are not remembered; every machine in a
session writes its own.

**Where** - neither `GameProperties` nor `ConfigFile` writes: both read shipped text. The one thing that writes today
is `LocalMachineId`, through libGDX `Preferences`. The record follows it, in a store of its own,
`pl.mzebrows.pwt.missions` (on Windows `%USERPROFILE%\.prefs\`), so it can be deleted apart from the machine id.
One `core` class, `MissionRecords` - `best(day, tier)`, `offer(day, tier, ticks)` - reads and writes it; `ui` is
handed values.

**What** - one key per won (day, tier): `20261005.H = 41237`, the day as `yyyymmdd`, the tier's letter as in the
code. A key there is the ✓. A loss, and any mission that is not a daily, writes nothing. NEW BEST only for a time
strictly lower.

**How long** - forever. Three short keys a day; nothing reads old days, so nothing prunes.

**When** - on the outcome tick, as the client sees it in the readout, not when the result screen opens: a machine
that drops in the 3 s hold still writes, one gone before the tick does not.

**Online** - every machine present at the win writes the ✓ and the time, a late joiner too: the time counts from
the world's first tick, the same on every machine. A joiner files a daily by matching the seed against
`daily(d, tier)` for its own UTC day and the days either side - the host's start day, without a protocol change.
Offline ENTER CODE keeps 08's rule: only the day the page opened.

**A build that changes a daily's map** - the record stands; that day's next time is compared against a slightly
different map. Written down as the cost, revisited if players report a best they cannot beat after an update.

**Faults** - a garbled key is logged and skipped, an unreadable file is read as empty; play never stops. Two copies
on one computer share the store and the last flush wins - accepted, in `alpha-phase.md`'s known limits.

**Out** - pruning; the `BuildId` on a best; the day in `SessionSetup`; withholding a late joiner's time; a "forget
my records" setting (deleting the file does it).
