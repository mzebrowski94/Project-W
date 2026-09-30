# 11 - Session config from the host

Status: resolved

Strength: Strong - a live bug. Dependency: in-process (`net`, `core`). Found grilling 03. Independent of 03.

## Problem

Every key has one scope (glossary: **world config**, **session config**, **local config**). Only world
config crosses today - `SessionSetup.configText` in `WELCOME`. Session config does not:

- `net.session.checksumTicks` is read by every peer from its own `network.properties`. A peer checksums at
  `tick % its value` (`PeerSession:391`); the sequencer files hashes by `tick / the host's value` and
  compares only at equal ticks (`Sequencer:665-685`). A joiner whose value differs loses some or all
  comparisons, silently, and its dump looks for the agreed keyframe with its own value (`PeerSession:792`).
- `net.maxMessageBytes` is per machine too: a joiner with a lower limit drops the host's snapshot at its
  frame decoder. It cannot come from the host - the decoder is built before `WELCOME`, which passes through
  it.
- `net.session.*` is misnamed: the code calls the record `pacing`, and all of it but `checksumTicks` is
  local (lead, margin, buffer, catch-up) or read only by the sequencer (`historyTicks`, `redundantTicks`).

## Direction

- **Session config is a record**, `NetConfig.Session`, holding `checksumTicks`, keyed
  `net.session.checksumTicks`. The sequencer writes it into `WELCOME` beside `SessionSetup` (not inside it:
  `SessionSetup` is what a world is built from); a joiner uses the host's values, never its own file's. The
  ring sized from `checksumTicks` is already built in `PeerSession.welcome`.
- **The rest of `net.session.*` becomes `net.pacing.*`**, record `Pacing`. `historyTicks` and
  `redundantTicks` go with it, documented as read only where the sequencer runs. The rule
  `historyTicks > leadMaxTicks + checksumTicks` stays in `NetConfig`'s constructor. No aliases for the old
  names: a player's old copy logs each unknown key once and the shipped value stands.
- **`maxMessageBytes` becomes a constant**, `Transport.MAX_MESSAGE_BYTES` - a wire-format limit, not tuning.
- **Tests**: a joiner with a different `checksumTicks` in its own file is still compared at the host's ticks
  and caught when it diverges (`SessionTest`); `NetConfig.Session` round-trips through `WELCOME`, so a field
  added to the record cannot be left out of the message (`WireTest`).
- **Record**: ADR 0018 (config scopes), amending ADR 0012.

## Comments

**Done.** As directed. `Msg.PROTOCOL` 3 -> 4 (the welcome changed shape). The too-big-world test keeps its
small limit through a package-private `PeerSession` constructor, since the limit is no longer config.
