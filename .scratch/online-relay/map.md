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
  - **Topology.** Where the sequencer runs is decided in a ticket, after research.
- In scope: reconnecting after a dropped link, host migration, abuse limits. Host migration is what makes the
  topology ticket matter: with the sequencer on the host's machine, nobody is left to seal ticks when he goes.
- Standing structure to respect: ADR 0012 (lockstep, star through the sequencer, `Transport` is a port, build
  id at the handshake, no mesh, no state replication). G0038 (peer / host / seat / slot, drop-in, no lobby or
  ready screen, the host's config is the session's). ADR 0018 (one scope per config key). The `net`
  architecture tests (no libGDX, Netty only in `net.netty`, the clock behind `Clock`).
- Vocabulary: **peer**, **host**, **sequencer**, **seat**, **slot**, **room code**, **tick bundle** are settled
  in `docs/glossary.md` and G0038. *Room* and *relay* are not defined yet: the rooms ticket settles them.
  Never use *lobby*, *match* or *server* for the game's session.
- Skills every session consults: `grilling` + `domain-modeling` for grilling tickets, `research` for research
  tickets, and `feature` when a decision touches what a player sees or does (the Join and Host screens, the
  host overlay). Glossary terms go to `docs/glossary.md`, never a `CONTEXT.md`.
- Research findings go to `research/` in this folder, uncommitted, rather than a pushed `research/*`
  branch. On the desktop Mateusz commits (CLAUDE.md).

## Decisions so far

- [Direct paths between peers](issues/03-direct-paths-between-peers.md): deferred - hole punching fails for ~30% of pairs (more on mobile CGNs), so the relay is built first regardless; later it is a hand-rolled punch on the Netty UDP channel that moves a link's unreliable traffic off the relay, behind an unchanged `Transport`, and needs a UDP reflector an HTTP-only platform cannot give.
- [Where a game server can run](issues/01-where-a-game-server-can-run.md): UDP reaches Fly.io (dedicated IPv4) and any VPS (Hetzner, OVH, Oracle free); Railway and Koyeb give raw TCP only; Render forces WebSocket and drops it on every deploy; at ~2.3 GB/hour full, egress dominates, so a EUR 5 VPS (20 TB/unlimited) or Fly ($0.02/GB) is cheapest.
- [Lockstep over a stream](issues/02-lockstep-over-a-stream.md): a stream is correct but each lost segment stalls ticks ~1.25 RTT about once a second at 1-2 % loss, so it suits pillar 1 only below ~30 ms RTT (or near-lossless lines) and above ~45 ms costs lost input via `spikeTicks`; UDP stays the main path, a WebSocket `Transport` (two connections per link so snapshots never block ticks) is only the fallback, and QUIC needs UDP anyway.

## Not yet specified

- **Abuse limits.** Whatever keeps a public server alive: rooms per address, a throttle on password guesses
  per room and per address, room and peer caps, a ceiling on bytes per peer, idle rooms reaped. Depends on the
  topology (what the server can tell apart) and on the platform (what it already rate-limits).
- **Which builds the server serves.** The build id must match at the handshake (ADR 0012). If the server runs
  the sequencer, every game build may force a server deploy, or the server has to carry several builds. A plain
  relay may not care. This may fold into the topology ticket or need one of its own.
- **The client side.** How the Join screen takes a code plus a password for an online room next to the LAN
  ones (G0038: a typed code joins the session heard with it). Where the server's address lives
  (`network.properties`). What the host's overlay reads out. How a host chooses a password. Needs the
  rooms ticket first, then `feature` and `docs/ui/menu_layout_agent.md`.
- **Testing.** How `SessionHarness`, `LoopbackNetwork` and `ConditionedTransport` stand in for the server and
  the internet path, and whether a test drives the server module in-process.
- **The write-up and the stages.** The spec, the ADR and the G-record text, and the build cut into stages that
  can each stop. Comes last, once the grilling tickets are closed.

## Out of scope

- **Server operations**: the deploy runbook, health checks, dashboards, and reading the server's logs in
  production. Not chosen for this effort (2026-11-19). The platform is still chosen here.
- **A public list of rooms**: rooms are reached by code only (2026-11-19).
- **Accounts, logins, friends lists, skill-based matchmaking and chat**: none was asked for, and a lobby is
  ruled out by G0038.
