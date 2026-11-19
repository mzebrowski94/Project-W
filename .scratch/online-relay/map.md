# Online play through a server - wayfinder map

Label: wayfinder:map
Charted: 2026-11-19

## Destination

A spec for playing over the internet through a server of ours (`spec.md` in this folder): rooms reached by
code, optional passwords, reconnecting after a dropped link and a new host taking over when the host
leaves. Every decision it rests on is recorded where this repository records them: an ADR amending ADR 0012,
a G-record amending G0038, and glossary rows. The build is cut into stages of one session each, and the work
can stop after any of them. Ready to hand to `implement` -> `new-system` sessions. Nothing is built on this map.

## Notes

- **Settled while charting (2026-11-19, Mateusz):**
  - **Platform.** Any cheap host. Render is one candidate, not a requirement.
  - **Server.** Written in Java as a new headless module on top of `net`, with no libGDX, reusing
    `Transport`, the wire format and `NetLog`.
  - **Rooms.** Reached **by code only**: the server publishes no list of rooms.
  - **Passwords.** A room may carry a password the host sets, **separate from the code**. The code stays
    a name that grants nothing (G0038), and the server checks the password before it forwards anything.
  - **Topology.** Settled in "Where the sequencer runs": on the relay for a session with a room (ADR 0023 draft).
- In scope: reconnecting after a dropped link, host migration, abuse limits. Host migration is what makes the
  topology ticket matter: with the sequencer on the host's machine, nobody is left to seal ticks when he goes.
- Standing structure to respect: ADR 0012 (lockstep, star through the sequencer, `Transport` is a port, build
  id at the handshake, no mesh, no state replication). G0038 (peer / host / seat / slot, drop-in, the host's
  config is the session's) as amended by G0101 (a briefing before the first tick, no ready button). ADR 0018 (one scope per config key). The `net`
  architecture tests (no libGDX, Netty only in `net.netty`, the clock behind `Clock`).
- Vocabulary: **peer**, **host**, **sequencer**, **seat**, **slot**, **room code**, **tick bundle** are settled
  in `docs/glossary.md` and G0038; **room**, **relay** and **password** *(of a room)* by the rooms ticket.
  Never use *lobby*, *match* or *server* for the game's session, nor *server* for the relay.
- Skills every session consults: `grilling` + `domain-modeling` for grilling tickets, `research` for research
  tickets, and `feature` when a decision touches what a player sees or does (the Join and Host screens, the
  host overlay). Glossary terms go to `docs/glossary.md`, never a `CONTEXT.md`.
- Research findings go to `research/` in this folder, uncommitted, rather than a pushed `research/*`
  branch. On the desktop Mateusz commits (CLAUDE.md).

## Tickets

<!-- a glance, not the source: each ticket's own Status and Blocked-by lines win. Update a row when you claim or resolve. -->

| Ticket | Type | Status | Waiting on |
|---|---|---|---|
| [Where a game server can run](issues/01-where-a-game-server-can-run.md) | research | resolved | - |
| [Lockstep over a stream](issues/02-lockstep-over-a-stream.md) | research | resolved | - |
| [Direct paths between peers](issues/03-direct-paths-between-peers.md) | research | resolved | - |
| [Rooms, codes and passwords](issues/04-rooms-codes-and-passwords.md) | grilling | resolved | - |
| [Where the sequencer runs](issues/05-where-the-sequencer-runs.md) | grilling | resolved | - |
| [A new host when the host leaves](issues/06-a-new-host-when-the-host-leaves.md) | grilling | **open - frontier** | - |
| [Coming back after a dropped link](issues/07-coming-back-after-a-dropped-link.md) | grilling | **open - frontier** | - |

## Decisions so far

- [Direct paths between peers](issues/03-direct-paths-between-peers.md): deferred - hole punching fails for ~30% of pairs (more on mobile CGNs), so the relay is built first regardless; later it is a hand-rolled punch on the Netty UDP channel that moves a link's unreliable traffic off the relay, behind an unchanged `Transport`, and needs a UDP reflector an HTTP-only platform cannot give.
- [Where a game server can run](issues/01-where-a-game-server-can-run.md): UDP reaches Fly.io (dedicated IPv4) and any VPS (Hetzner, OVH, Oracle free); Railway and Koyeb give raw TCP only; Render forces WebSocket and drops it on every deploy; at ~2.3 GB/hour full, egress dominates, so a EUR 5 VPS (20 TB/unlimited) or Fly ($0.02/GB) is cheapest.
- [Lockstep over a stream](issues/02-lockstep-over-a-stream.md): a stream is correct but each lost segment stalls ticks ~1.25 RTT about once a second at 1-2 % loss, so it suits pillar 1 only below ~30 ms RTT (or near-lossless lines) and above ~45 ms costs lost input via `spikeTicks`; UDP stays the main path, a WebSocket `Transport` (two connections per link so snapshots never block ticks) is only the fallback, and QUIC needs UDP anyway.
- [Rooms, codes and passwords](issues/04-rooms-codes-and-passwords.md): a room is the relay's record of one session, opened only when the host asks, before the first tick or hosting fails; the relay hands out the session's one four-character code; an optional four-character password in the code's alphabet, set when hosting, asked once and only at the relay's door, held in memory unlogged; five refusals before forwarding; the G0038 amendment's words agreed.
- [Where the sequencer runs](issues/05-where-the-sequencer-runs.md): a session with a room is sequenced on the relay (one `SequencerThread` per room, living as long as the room), one without stays on the host's machine; 3 ticks for everybody in one region instead of host 2 / joiner ~4; the sequencer becomes blind to input, so the relay depends on `Msg.PROTOCOL`, not the build, and a room runs its host's build; the host's peer stays reference world and snapshot source; LAN machines of a roomed session join through the relay, handed the password; platform picked, unnamed in the repository; WebSocket deferred. Draft: [ADR 0023](adr-0023-draft.md).

## Not yet specified

- **Abuse limits.** Whatever keeps a public server alive: rooms per address, a throttle on code and password
  guesses per room and per address (four-character codes are guessable by design, rooms ticket), room caps, a ceiling on bytes per peer, idle rooms reaped. The relay
  runs each room's sequencer, so it can tell links, slots and ticks apart and meter them; a shared public address
  and a CPU that may be throttled (ADR 0023 draft) mean it rate-limits for itself and caps rooms by what one CPU seals on time.
- **Which builds the relay serves**: settled by "Where the sequencer runs" - any build of the protocol version it
  speaks; a room runs its host's build.
- **The client side.** Reshaped by the briefing (G0101): HOST GAME becomes how a session opens - `< INTERNET / THIS
  NETWORK >`, a password line for the internet only, picked as a code is, and OPEN, which opens the room and the
  **briefing** (`UI:main/online/host/missions` becomes `UI:briefing`, with the room code and password at its head and
  the players beside its entries). So the room opens before a mission is chosen, which the rooms ticket's "before the
  first tick" allows. A joiner lands on the briefing, or mid-mission if the host has started. Still open: how the Join
  page asks for the password after a code the relay knows (a code heard on the network still wins - rooms ticket);
  where the relay's address lives (`network.properties`); what a hosting failure at OPEN says; what the pause page's
  room line reads out once the briefing shows the code. Needs `feature` and `docs/ui/menu_layout_agent.md`, whose Host
  and Join **Do not build** cells still name a host start button and a player list - they go with the build.
- **A page before the first tick**: settled - [G0101](../../docs/design/decisions/G0101-a-hosted-session-opens-on-a-briefing.md) (the briefing), with [G0102](../../docs/design/decisions/G0102-a-missions-length-is-chosen-and-held-in-its-code.md) (a mission's length).
- **Testing.** How `SessionHarness`, `LoopbackNetwork` and `ConditionedTransport` stand in for the server and
  the internet path, and whether a test drives the server module in-process.
- **The write-up and the stages.** The spec, the ADR and the G-record text, and the build cut into stages that
  can each stop. Comes last, once the grilling tickets are closed.

## Out of scope

- **Server operations**: the deploy runbook, health checks, dashboards, and reading the server's logs in
  production. Not chosen for this effort (2026-11-19). The platform was chosen in "Where the sequencer runs", and
  the repository names only what the relay needs of one, never the provider.
- **A public list of rooms**: rooms are reached by code only (2026-11-19).
- **Accounts, logins, friends lists, skill-based matchmaking and chat**: none was asked for. (A page before the
  first tick is G0101's briefing.)
