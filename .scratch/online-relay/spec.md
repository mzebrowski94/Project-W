# Online play through a relay - the spec

Charted 2026-11-19. Phase 1 of the AFK run: every decision below that Mateusz did not grill himself is **provisional, for
review (AFK run)** and says what it turned down. Agreed, not provisional: tickets 01-05, G0101, G0102, ADR 0023 and
G0103 (the rooms text). Provisional: G0104, G0105, the abuse limits, the client's pages, the testing shape, the stages.

## Destination

Two to eight machines play one session over the internet through a **relay** of ours: a **room** reached by its **room code**,
an optional **password**, the **sequencer** on the relay (ADR 0023), a machine that blinks comes back (G0105), and a new
**host** takes over when the host's link is gone for good (G0104). A session without a room is the LAN session of today.

## Words

All in `docs/glossary.md`: peer, host, sequencer, seat, slot, room, relay, room code, password, briefing, tick bundle - and,
new here, **rejoin token** and **rejoin window**. Never *lobby*, *match* or *server* (for the relay).

## The shape

```
machine --Transport--> relay [ door | room: SequencerThread, links, token table ] <--Transport-- machine
host's peer is one link among the others: reference world, snapshot source, settings owner
```

- **A new Gradle module `relay`**, headless, depends on `net` (and what `net` needs). No libGDX. Package
  `pl.mzebrows.pwt.relay`. `lwjgl3` and `core` never see it. A `RelayArchitectureTest` keeps libGDX out, Netty inside
  `net.netty`, the clock behind `Clock`, and - ADR 0023 - the relay, the sequencer and the wire format reach nothing in
  `pl.mzebrows.pwt.sim` but the config plumbing (`ConfigBinder`, `ConfigFile`, `ConfigKey`, which only `NetConfig` uses).
  `CLAUDE.md`'s module table and dependency line gain `relay` (`lwjgl3` -> `core` -> {ui, net} -> sim -> map, and `relay` -> `net`).
- **The relay is an object before it is a process.** `Relay` takes a `Transport`, a `Clock`, a `NetLog` and its config; a test
  drives it in-process over `LoopbackNetwork` with a `ManualClock`. `RelayMain` is the thin process: Netty transport, real clock,
  config off the classpath and a local file (`GameProperties`).
- **The door** (G0103): the first message of a link is a `HELLO` for a room (join) or an `OPEN` (host: build id, build version,
  host key, session config, password or none). `OPEN` answers with the room's code or a refusal; `HELLO` is checked in order -
  room exists, password, build, capacity - and a refusal is `refused by the relay: <reason>` (the five texts in ticket 04) and the
  link is closed. A link holding a rejoin token skips the password (G0105). A link that says nothing within
  `net.relay.helloTimeoutSeconds` is dropped.
- **A room** owns one `SequencerThread` (or, in a test, a pumped `Sequencer`), its links, its code, its password (memory only,
  never logged: `NetLog` writes `password=yes|no`), its host peer number, its token table and the bytes of the last START's setup.
  It closes when its last link has gone and the rejoin window has passed.
- **The sequencer is blind** (ADR 0023): held input is bytes it repeats, an event is number, slot, type and an opaque value, redundant
  copies diff bytes. The typed side (`CommanderInput`, `Order`, `PickupKind`, `UpgradeKind`) moves to the peer's codec.
  Golden bytes per message type; `Msg.PROTOCOL` bumps with any change.

## Provisional decisions of the charting (each with what was turned down)

1. **Relay keys live in `assets/network.properties`**, scoped per key (ADR 0018): the relay's own (`net.relay.*` limits, the
   pacing keys it already reads where the sequencer runs) and the client's (`net.relay.host`, `net.relay.port`). One file, one
   loader, a deploy ships the same file. *Turned down: a separate `relay.properties`* - a second way to say what `ConfigFile` does.
2. **Abuse limits** (relay scope, values provisional - tuned by the first real session's `sealLateMostMs`):
   | key | value | does |
   |---|---|---|
   | `net.relay.roomsMax` | 32 | rooms at once; what one shared CPU seals on time is guessed, measured later |
   | `net.relay.roomsPerAddressMax` | 3 | rooms one address may open |
   | `net.relay.linksPerAddressMax` | 8 | links from one address (`Players.MAX`) |
   | `net.relay.helloTimeoutSeconds` | 5 | a silent new link is dropped |
   | `net.relay.refusalsPerAddressMax` / `refusalWindowSeconds` / `silenceSeconds` | 10 / 60 / 120 | refusals (no room, wrong password) an address may collect in a window; past it, the address is ignored for the silence |
   | `net.relay.wrongPasswordsPerRoomMax` / `roomLockSeconds` | 5 / 60 | wrong passwords one room hears in a window; then it turns new machines away (a held token is unaffected) |
   | `net.relay.bytesPerSecondPerLink` | 65536 | inbound token bucket per link, snapshots excluded (they are one-per-joiner, bounded by `Transport.MAX_MESSAGE_BYTES`) |
   | `net.relay.emptyRoomSeconds` | 30 | a room with no link closes (after the rejoin window) |
   | `net.relay.idleRoomMinutes` | 30 | a room whose links send nothing but acknowledgements closes |
   | `net.relay.sealLateWarnMs` | 8 | `sealLateMostMs` over this (half a tick) is logged as `event=seal-late` - ADR 0023's revisit |
   A meter that refuses says why in the refusal text where there is a person to read it, and silently drops where there is not.
   *Turned down: IP bans or accounts* - nothing durable to keep; the throttle is in memory and a restart clears it.
3. **Builds served**: any build of the protocol the relay speaks; the room runs its host's build, checked at the door by build id,
   named by version. (Settled by ADR 0023.)
4. **Client side** (needs `feature` and `docs/ui/menu_layout_agent.md`, whose Host and Join **Do not build** cells name a host start
   button and a player list - they are G0101's to retire):
   - `net.relay.host` (empty as shipped: the row says `INTERNET (NO RELAY SET)` and cannot be picked) and `net.relay.port` (24741),
     read like the existing address keys, locally overridable.
   - HOST GAME, G0101's rows: `< INTERNET / THIS NETWORK >`, a password line (internet only, blank is none, remembered in
     preferences like the last address), OPEN. At OPEN the machine connects to the relay and waits for its code; a failure leaves
     the page up with the reason on the line below: `THE RELAY CANNOT BE REACHED` or `refused by the relay: <reason>`. No fallback.
   - JOIN GAME: a code heard on the network joins that session; any other four characters go to the relay. A refusal `room ACDE has a
     password` opens a PASSWORD line on the page (same entry as the code, same forgiving read); one try per entry; the relay's other
     refusals go back to the menu with the reason. The password is asked once per machine run - not stored.
   - The briefing shows the code and, if set, the password at its head. The pause page's room line reads them the same, and the
     host's name once a hand-over has happened.
   - A machine on the host's network in a session with a room hears the announcement (code and password), and joins through the relay.
   *Turned down: a browser of rooms; typing the relay's address on the Join page.* (G0103: no list; the address is config.)
5. **Testing.** Door and room: `RelayTest`s in-process over `LoopbackNetwork` + `ManualClock`. A whole session through a relay:
   `SessionHarness` gains a `withRelay()` form - the harness's sequencer is replaced by a `Relay` and every peer, host included,
   connects to it - with `ConditionedTransport` on each link for lag and loss; the existing session tests run against both forms.
   One real-socket test: a `Relay` on `NettyTransport` and two peers on loopback ending on one world (as `NettySessionTest`).
   Nothing runs the game: Mateusz plays two copies through a local relay (end of this file).
6. **Where `sealLateMostMs` is read.** Per room, in `NetLog` at the room's close and on `sealLateWarnMs`; the relay keeps no
   metrics endpoint (server operations are out of scope).
7. **The rejoin window and token** are G0105's; `net.rejoin.windowSeconds` (30) is sequencer scope, so a LAN session has it too.

## Stages

Each is one session's worth, stands alone, and the work can stop after any of them. Topology-free stages first. Every stage: tests
first (step 3 kept), `verify.sh` green, the `CLAUDE.md` checklist, glossary words used, and the invariant row or decision record it
settles named. A stage that stays red is left on its branch with a note in its ticket.

| # | Stage | Depends on | Tests (named, new) | Gate |
|---|---|---|---|---|
| 1 | **A mission's length** (G0102): length character in `MissionCode`, five of seed, `Mission` bag dealing past the pool, the ramp over the march, the choice row on DAILY MISSIONS | - | `MissionCodeTest`, `MissionDealTest`, `MissionConfigTest` ramp, a `ui` page test for the row | `verify.sh` |
| 2 | **The briefing on the LAN** (G0101): host-mode missions page as `UI:briefing`, players as rows, START MISSION, `SessionSetup` carrying the START, the session back to its briefing, HOST GAME's rows (no relay row yet) | 1 | `SessionTest` (start puts every seated slot on at tick one, a late machine lands on the briefing), `BriefingPageTest` in `ui`, `OnlineSessionTest` | `verify.sh` |
| 3 | **The relay and its door** (G0103): module `relay`, `Relay`, `Room` (no sequencer yet), codes, password, the five refusals, `NetConfig.Relay`, architecture test, `CLAUDE.md` module row | - | `RelayDoorTest` (each refusal text, code drawn from free codes, password never in the log), `RelayArchitectureTest` | `verify.sh :relay:test :net:test`, then full |
| 4 | **A blind sequencer, a protocol kept honestly** (ADR 0023): `Sequencer`/wire format read no game type; golden bytes per message; `NetArchitectureTest` fence | - | `WireGoldenTest`, `SequencerTest` (opaque events), `BlindFenceTest` | `verify.sh :net:test`, then full |
| 5 | **Traffic through the relay** (ADR 0023): a room runs its `SequencerThread`, forwarding, the host's peer as reference, snapshots host -> relay -> joiner, `SessionHarness.withRelay` | 3, 4, 2 | `RelaySessionTest` (eight slots, a drop-in, a desync heal, lag on each link), `NettyRelayTest` | `verify.sh` |
| 6 | **Host and Join through the relay** (G0103, G0101): client keys, HOST GAME row and password line, JOIN's password line, announcements carrying code and password, the LAN machine joining through the relay | 5 | `JoinChoicesTest` (relay path), `HostPageTest`, `OnlineSessionTest` (refusal texts) | `verify.sh` |
| 7 | **Coming back** (G0105): token in the welcome, the window, seats kept and freed, snapshot on return, the client's retry | 5 | `SessionTest` (blip returns to its slots, window ends frees them, a second link replaces the first, a stranger's token is refused) | `verify.sh` |
| 8 | **A new host** (G0104): the sequencer's choice sealed as an event, host powers and snapshot source move, the banner and briefing line | 7 | `SessionTest` (host's link gone past the window; the next-oldest takes over; the old host returns a peer; a host who ends it ends it) | `verify.sh` |
| 9 | **Abuse limits**: the meters above | 3 (5 for bytes) | `RelayLimitsTest` (each key, at its edge, on a `ManualClock`) | `verify.sh` |

Decision-record numbers: **G0106** is spare for stage 1, **G0107** for stage 2, **G0108** for stage 6, **G0109** for stage 9 (the limits, if a
stage wants a record - none is promised). ADR 0024 is spare.

## Stage 3 notes (AFK run, provisional, for review)

- The door's wire is `net.door.Door` (types 40-44), not the session's package-private `Msg`: the client in `core` cannot see `relay`, and stage 4 owns `Msg`. *Turned down: the five types in `Msg`.*
- `Relay` does not listen; the caller hands it a listening `Transport`. `RelayMain` is left to stage 5 (needs Netty, `SystemClock`, `SecureRandom`, a file).
- `Room.MACHINES_MAX` (8) is held to `Players.MAX` by `RelayDoorTest`, since the relay may not read `sim`. *Turned down: a config key.*
- Only `net.relay.roomsMax` (32) is added; a host past it is refused `refused by the relay: no room is free`. A bad password at OPEN is refused likewise. The client keys `net.relay.host`/`port` wait for stage 6, which reads them.
- A room closes at once when its last link leaves (stage 7 replaces that with the rejoin window).

## What to play (Mateusz, on the desktop - only he runs the game)

Two copies on one computer through a local relay (`docs/design/alpha-phase.md`, *Playing online*, then the relay's address in `network.properties`):
HOST GAME, INTERNET, a password; JOIN with the code from the second copy, once with a wrong password and once right; start a mission from the
briefing; drop the second copy's link (F3's lag step, or close it fast and reopen within thirty seconds) and see it come back to its slots;
close the host copy's window, wait out the window, see who is host. A pause from the second copy. Feel of the 3-tick lead for both.

## Stage 4 notes - provisional, for review (AFK run)

- Blind side kept in `net.session` (fenced by class name in `NetArchitectureTest`), typed side is `InputCodec`; turned down: a `net.session.wire` package (every wire class public, a large diff).
- A sequencer no longer refuses an order, card or pickup code that names nothing: it seals it, and every peer ignores it alike (`InputCodec` returns null). Turned down: a code table in the sequencer. Out-of-range pushes and button bits are clamped or masked at the peer instead of refused.
- Event values must fit their wire bytes (`Bundle.checkEvent`); the wire is byte-identical, `Msg.PROTOCOL` stays 6.
- `WireGoldenTest` pins message numbers, primitives, held bytes, session config and the bundle (whole and changed); the messages built inline in `PeerSession`/`Sequencer` (HELLO, WELCOME, ...) are not yet golden - a captured-session golden is left for stage 3's relay harness.
