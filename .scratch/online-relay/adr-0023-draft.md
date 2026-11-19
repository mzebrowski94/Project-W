<!-- Draft from "Where the sequencer runs" (issues/05), agreed with Mateusz 2026-11-19. At the write-up stage it goes to
docs/adr/0023-a-session-with-a-room-is-sequenced-on-the-relay.md with: Status `accepted`; ADR 0012's status (record and
index) gaining "; amended by ADR 0023: a session with a room is sequenced on the relay, blind to its input"; and an
index row - Decision: "A session with a room has its sequencer on the relay, one per room, blind to input: it depends
on the protocol version, not the build; the host's peer stays the reference world and the snapshot source."
Do not build: "a plain relay to the host's sequencer; a relay serving only its own build; several relay builds side
by side; a world on the relay; two sequencers for one session; the host sending the sequencer's pacing keys". -->

# 0023: A session with a room is sequenced on the relay, blind to its input

**Status.** draft - accepted when written into `docs/adr/` at the write-up stage

**Trigger.** Building the relay or traffic through it; changing `Sequencer`, `Bundle` or `Msg`; anything that makes
the sequencer read a game type; moving the reference world or the snapshot source; choosing where the relay runs.

**Enforced by.** Nothing yet - not built; due with the build: an architecture test that the sequencer, the wire format
it reads and the relay module reach nothing in `pl.mzebrows.pwt.sim`; golden bytes for every message type, so a
layout change without a `Msg.PROTOCOL` bump fails (the backlog's "`Msg.PROTOCOL` is not kept", brought back here); a
session test with the sequencer behind an in-process relay over `LoopbackNetwork`, the host's peer one link among
the others.

**Amends ADR 0012.** "The sequencer runs on the host's machine today; nothing in it needs to" becomes: it runs on the
relay for a session with a room, and on the host's machine for a session without one. The build id still has to
match between peers; the sequencer no longer has to share it. Everything else in ADR 0012 stands - lockstep, held
input repeated and events applied once, the lead, the host's config shipped, the desync check and heal - and so does
its **Do not build**.

**Constraint.**

- **Where the sequencer runs is where the session's room is.** A session with a room (the rooms amendment of G0038)
  is sequenced on the relay; one played only on its own network keeps it on the host's machine. One session, one
  sequencer: the machines on the host's network in a session with a room join through the relay too, like everybody
  else.
- **It is the same sequencer in both places.** One `SequencerThread` per room on the relay; the class does not know
  where it runs. It lives as long as the room - through every mission and back to the briefing (G0101).
- **The sequencer is blind to input.** Held input is a run of bytes it stores and repeats for a late slot; an input
  event is a number, a slot, a type and a value it does not read; the redundant copies in a bundle diff bytes, not
  fields; it seals at most one event of each type per slot per tick, as now. It reads no game type and depends on the
  wire format alone, whose version is `Msg.PROTOCOL` - kept honestly: any change to a message's bytes bumps it.
- **A room runs its host's build.** The host states its build id and build version when it opens the room; the
  relay's door refuses a machine of another build by those (the rooms amendment); the sequencer's handshake checks
  the protocol version, and the build id against the room's, never its own. The relay is redeployed only when the
  protocol version moves, never for a game build.
- **What the host hands the relay, and when.** When it opens the room: build id and version, its host key, its
  **session config** (ADR 0018). At each START: the mission's setup - world config text, scenario, seed - with the
  tick rate from its world config; the same message that carries it on the host's own network.
- **The host's peer is still the reference world and the snapshot source**, one link among the others. A drop-in or
  a heal is asked of the host's peer and crosses host -> relay -> joiner; the relay forwards it and keeps nothing.
  Desync dumps are written by the host's and the diverged peer's machines; the relay's connection log records the
  desync. Until a new host can take over, the host's peer leaving ends the room as it ends the session today.
- **The sequencer's own pacing keys are the relay's.** `historyTicks`, `redundantTicks` and `spikeTicks` are read
  where the sequencer runs - on the relay from its own `network.properties`. The host does not send them.
- **The relay asks of its platform:** TCP and UDP on one public port (the host listens on one port for both today),
  an address and port each machine reads from its local config, no dedicated address assumed, and a CPU that may be
  shared or throttled - its lateness is measured, not assumed away.

**Why.** At about 20 ms from every machine to a relay in the same region, a lead of round trip plus a margin is
3 ticks (50 ms) for everybody. With a plain relay to the host's sequencer the host plays at the 2-tick floor and
every joiner at about 4 (67 ms): the detour is paid by everyone but one. On the relay the host leaving no longer
takes the sequencer with it, so a new host is a choice of reference peer and snapshot source, not a sealed history
moved between machines. The host's upload falls from a bundle stream per joiner to its own input. A blind sequencer
is what keeps that from costing a relay deploy for every game build: the relay knows the wire, the peers know the
game, and the build id still guards the one mismatch that cannot heal - two peers' worlds.

**Scope.** Where the sequencer runs and what it may know. Not how a room is opened, named, entered or refused - the
rooms amendment of G0038. Not who becomes host when the host leaves, nor how a dropped machine proves it is back
(G0104, G0105). Not which provider runs the relay. Not the transport between machines: UDP for tick traffic and
TCP for control and snapshots, as ADR 0012.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A plain relay forwarding bytes to the host's sequencer.* Every joiner pays the detour - about 4 ticks to the
host's 2 in one region - and the host leaving takes the sealed history with it.

**No:** *A relay that serves only its own build.* Every playtest build would be a relay deploy, and every deploy
closes every room.

**No:** *Several relay builds side by side.* One public port makes it a front door routing to processes per build -
a deployment problem to avoid a wire format kept honestly.

**No:** *A world on the relay* - a reference world, a snapshot source or a desync arbiter that simulates. The relay
would be a peer of every build, tied to `sim`, and one step from state replication (ADR 0012).

**No:** *Two sequencers for one session* - the host's for its own network, the relay's for the internet. Two arbiters
for one tick.

**No:** *The host sending the sequencer's pacing keys.* They are the sequencer's machine's own (ADR 0018); the relay's
operator tunes them for the relay.

Not rejected, deferred: **direct paths** between peers, moving a link's unreliable traffic off the relay behind an
unchanged `Transport` (hole punching fails for about 30% of pairs, so the relay comes first); **a WebSocket
transport** for networks that block UDP (a stream stalls lockstep about once a second at 1-2% loss, so it is a
fallback only); **one thread pumping every room**, or a dedicated CPU, if a shared one seals late.

**Revisit when** a real session's `sealLateMostMs` on the relay passes half a tick (8 ms): a dedicated CPU, or one
thread for all rooms; a player cannot reach the relay on UDP (the WebSocket transport); the relay's detour measurably
hurts a group far from it or on one network (direct paths); protocol bumps come often enough that relay deploys get
in the way.
