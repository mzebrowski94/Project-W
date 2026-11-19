# Where the sequencer runs

Type: grilling
Status: resolved
Blocked by: 01, 02, 03

## Question

Once a server exists, which shape does online play take? Pick one, so that the ADR amending ADR 0012 can
be written.

1. **Plain relay.** The sequencer stays on the host's machine. The server forwards bytes between links and
   never knows what a tick is. A joiner's round trip runs joiner -> server -> host and back, the relay's
   detour.
2. **Sequencer on the server.** The server seals the ticks, and every peer, the host included, is one hop
   from it. The host is just the peer whose config and world are the session's. This makes host migration
   (in scope) a matter of choosing another reference peer rather than moving the sequencer.
3. **Direct paths with the relay as fallback** (ticket 03), with either 1 or 2 underneath.

Weigh each against what the research found: the transport the platform allows (01), what a stream costs
lockstep (02), and how often direct paths succeed (03). Then:

- The input delay each shape costs for a host and a joiner in the same region, worked out from ADR 0012's
  pacing (lead = round trip + margin).
- What the sequencer needs from the host today and where it would get it on a server: the session config
  (ADR 0018), the scenario and seed (`SessionSetup`), the world for a drop-in snapshot (the sequencer holds
  no simulation), the reference hash for the desync check. Read `net/.../session/Sequencer.java`,
  `SequencerThread.java`, `PeerSession.java`, `SessionSetup.java`.
- What the server's build has to match. The build id hashes `map`, `sim` and `net` (ADR 0012). Does every
  game build need a server deploy? Settle it here, or graduate it to a ticket.
- Whether the server's tick clock (a container on shared hardware) can seal on time. `sealLateMostMs` in the
  host's stats is the measure we already have.

ADR 0012's **Do not build** still holds: no mesh, no state replication, no waiting for a late input.

## Answer

Grilled with Mateusz, 2026-11-19. Every recommendation accepted except the platform, which is his pick. Draft:
[ADR 0023](../../../docs/adr/0023-a-session-with-a-room-is-sequenced-on-the-relay.md), amending ADR 0012.

**Shape 2: a session with a room is sequenced on the relay.** A session without a room keeps the sequencer on the
host's machine. It is the same `Sequencer`, one `SequencerThread` per room, and it lives as long as the room. Direct
paths stay deferred ("Direct paths between peers"). Machines on the host's network in a session with a room join
through the relay too: one session, one sequencer.

**Input delay**, from ADR 0012's lead = round trip + margin (`leadMin` 2, `margin` 1, 16.7 ms a tick), every machine
about 20 ms RTT from a relay in the same region:

| | Host | Joiner |
|---|---|---|
| Plain relay (sequencer on the host's machine) | 2 ticks, 33 ms (the floor) | ~4 ticks, 67 ms (40 ms detour) |
| Sequencer on the relay | 3 ticks, 50 ms | 3 ticks, 50 ms |

**What the relay's build must match: only the protocol.** Settled here, not graduated. Today `Sequencer` and
`Bundle` read `CommanderInput`, `Order`, `PickupKind` and `UpgradeKind`. They become **blind to input**:
- held input is bytes the sequencer repeats;
- an event is a number, a slot, a type and an opaque value;
- redundant copies diff bytes, not fields.

The sequencer then depends on `Msg.PROTOCOL` alone. This is fenced by an architecture test: the sequencer, its wire
format and the relay touch nothing in `sim`. The protocol is kept honestly with golden bytes per message type, which
brings the backlog's "`Msg.PROTOCOL` is not kept" back. A room runs its host's build, checked at the door. The relay
is redeployed only when the protocol moves. This is its own build stage, before traffic goes through the relay.

**What the sequencer needs from the host, and where it gets it:**
- **At OPEN:** build id and version, host key, session config.
- **At each START:** the mission's setup (config text, scenario, seed) and the tick rate. It is the same message as on
  the LAN, and G0101's stage defines it.
- **From the relay's own `network.properties`:** `historyTicks`, `redundantTicks` and `spikeTicks`.
- **Unchanged:** the host's peer stays the reference hash and the snapshot source. Snapshots cross
  host -> relay -> joiner, with nothing kept on the relay.
- **Until G0104:** the host's peer leaving ends the room.

**LAN machines in a session with a room.** The host's announcement carries the relay's code **and the password**, so
the joiner's game supplies it and nobody types it. Ticket 04's wording is amended to "joins without typing it".

**Platform.** One chosen by Mateusz for alpha, deliberately left unnamed in the repository. The relay needs:
- TCP + UDP on one public port, which `NettyTransport` already uses for both;
- an address and port from each machine's local config;
- no dedicated IPv4;
- a CPU that may be throttled.

**Sealing on time.** On a shared, possibly throttled CPU this is measured, not assumed: `sealLateMostMs`, per room.
ADR 0023 revisits it if a real session passes half a tick (8 ms): a dedicated CPU, or one thread pumping every room.

**WebSocket fallback.** Out of this destination. Deferred in `docs/backlog.md` with the trigger "a player who cannot
reach the relay on UDP".

**Unblocks** "A new host when the host leaves": with the sequencer surviving the host, a new host is a new reference
peer and snapshot source, and nothing sealed has to move. It also unblocks "Coming back after a dropped link": the
sequencer on the relay notices the drop and restores the seats.
