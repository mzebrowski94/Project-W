# Where the sequencer runs

Type: grilling
Status: open
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
