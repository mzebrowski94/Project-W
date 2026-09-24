# 0012: Online play is host-relayed lockstep, over a transport port of our own

**Trigger.** Anything that crosses the network; how a tick's inputs reach `Simulation.step` during a
session; proposing state replication, prediction, rollback or a different transport library.

**Enforced by.** Nothing mechanical yet - caught by reading. The guards arrive with the module: a
`NetArchitectureTest` (no libGDX in `net`, nothing in `sim` that sees `net`, no network-library type
outside the adapter), a multi-peer determinism test over every scenario, and a snapshot round-trip test.
Promote this line when they exist.

**Supersedes** the Scope clause of ADR 0004 - "no networking code exists and none should be written".
Everything else 0004 says stands, and is now what multiplayer rests on rather than what keeps it possible.

**Constraint.**

- **Every peer runs the whole simulation. Only inputs cross the wire during play.** World state crosses
  once, as a snapshot, when a player drops in (G0038) - and in a desync dump, which is for us.
- **The sequencer decides which input applies to a tick, never what happened in it.** It collects each
  peer's inputs, holds a deadline, and broadcasts the confirmed **tick bundle**. It runs on a thread of its
  own and holds no simulation: the host's own world consumes its bundles exactly as every other peer's
  does, so a host whose frame loop stalls - a window being dragged, a pause for garbage collection - falls
  behind and catches up like anyone else instead of freezing the session. Today it runs on the host's
  machine; nothing in it needs to, and moving it to the relay is how the host's advantage and the host's
  leaving stop mattering. A peer steps tick T only once it holds T's bundle; nothing is predicted.
- **An input is held state plus events.** Held state - the sticks, the aim, the buttons being held - is
  what a late peer's slot repeats from its previous tick, so one slow link never stalls the others.
  Events - an order given, and every one-off press to come - are numbered, sent again until the sequencer
  acknowledges them, and applied once, at the first tick the sequencer holds them: never repeated by the
  deadline, never lost to it.
- **A local input goes through the same encode and decode as a remote one** before the simulation sees
  it. The host is a peer like the others in this respect, or it drifts from the rest by a rounding.
- **Nothing changes simulation state outside `Simulation.step` from a tick's input during a session.**
  Pause, restart and the harness's invulnerability are session commands, tick-stamped by the sequencer
  and carried in the same stream (who may ask for which is G0038). Offline play keeps doing what it does
  today.
- **Networking is its own module**, `net`: `lwjgl3 -> core -> net -> sim -> map`. `sim` does not know a
  network exists; `net` has no libGDX.
- **The transport is a port.** `Transport` lives in `net`; Netty 4.2 sits behind it - UDP for tick
  traffic, TCP for control and snapshots - and an in-memory loopback serves the tests. No Netty type
  crosses it, for the reason ADR 0011 gives for gamepads: the library is the replaceable part.
- **Peers run the same thing.** The build hash and protocol version must match at the handshake, or the
  peer is refused. The config does not have to: the host sends its parsed config and every peer runs the
  session on it, so a tuning playtest needs nobody to copy a file.
- **A desync is detected, recorded and healed.** A world checksum every few ticks; on a mismatch the peer
  that diverged writes a dump - snapshot, inputs since it, checksum history - under `ai/logs/net/`, which
  replays headless, and the host sends it a fresh snapshot by the same path a drop-in takes. The session
  carries on; the dump is how the bug gets fixed.

**Why lockstep.** The simulation was built for it: one `CommanderInput` per slot per tick is already the
only input, randomness is one seeded `long` per stream, the transcendentals are `StrictMath`, nothing
iterates a hash, and since Java 17 floating point is strict on every platform (JEP 306). What that buys:
a per-feature cost of nothing on the network - beyond the rule 0004 already enforces, the one thing a new
mechanic owes is that its state is carried by the snapshot, which ADR 0014 makes automatic - a few tens of
KB/s per peer, and any session replayable from a seed and a log. What it costs is input delay - roughly the round trip to the
host plus a buffer, about four or five ticks for a peer in the same region and one for the host. That
is the number pillar 1 will judge, and it is why rollback is deferred rather than rejected.

**Why Netty.** No library for the JVM offers lockstep sessions - Photon Quantum does, and is C# only -
so the session protocol is ours whichever library carries it. Netty is the most proven networking stack
on the JVM, it serves a relay with the same code, and a TCP channel beside the UDP one delivers the
handshake, the config and a snapshot of tens or hundreds of kilobytes reliably, with no code of ours.

**Scope.** How a session stays in step and what crosses the wire. Not who sits in which slot, what
joining looks like or how many players there may be - that is G0038. Not how a player on the internet
finds a host: room codes and the relay are a later record, and a relay is only another way for the same
packets to arrive. Not offline play, which steps from local inputs through `FixedStepClock` exactly as
now; online, a tick is never dropped, because a dropped tick is a desync.

**Known hazards for the snapshot.** Two pooled components hold an `Entity`: `Strike.owner`, for the
life of a swing, and `Damage.locksOnDeath`. A snapshot carries them as positions in the engine's entity
list, never as references. A pooled component whose `reset()` misses a field was a stale value until
now; online it is a desync between a host that recycled the component and a joiner that did not.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Replicating state every tick, with prediction on each peer.* Every component would need a
replication story, forever, and every peer but the host would stop running the simulation it draws. It is the model for a game
whose state is small and whose players are many; this one is the other way round.

**No:** *A full peer-to-peer mesh.* Twenty-eight links for eight machines, a NAT to cross for each, and
the slowest link paces everybody. A star through the host has one arbiter for a late input.

**No:** *Waiting for a late input.* One player on bad Wi-Fi would freeze seven others. The deadline
repeats his previous held input; he feels his own link and nobody else does.

**No:** *The sequencer inside the host's frame loop.* Every stall of one window would be a stall of eight.

**No:** *A one-off input as a held flag.* The deadline would repeat it - an order given twice - or a tap
shorter than a late window would never arrive.

**No:** *Requiring every peer's `game.properties` to match.* Every tuning change would mean copying a file
to every friend before the next playtest.

**No:** *A network-library type outside the adapter,* or a `Gdx` call inside `net`.

**No:** *Java serialization, Kryo's reflective mode, or any decoder that instantiates a class a packet
names.* Every byte from the wire is read by code that knows exactly what it expects.

**No:** *KryoNet* - no release since August 2024, reflective serialization, an object per message.
*Aeron* - built for datacentre streams, with no notion of a player or a connection, and JVM flags to
run. *jvm-libp2p* - streams only, no hole punching, its relay in beta. *Nakama* - realtime over
WebSocket, a database to host, and a Java client that targets Java 8 to 11.

Rollback on top of this stream is not rejected, deferred: it is the answer if a playtest finds the input
delay sluggish. The snapshot codec G0038 needs is a part of it and no more - rollback restores the world in
place every tick, and a drop-in rebuilds it once. The latency playtest comes before the join screen so
that this is known early; a cosmetic lead - the client drawing its own players' aim and facing from local
input at once, feeding nothing back - is the first thing to try. A second transport - webrtc-java for direct
peer-to-peer through NAT, or Steam's - is also deferred, and the port is what keeps either cheap.

**Revisit when** a playtest in the same region reads as sluggish (rollback); the relay's detour measurably
hurts (hole punching, or WebRTC behind the port); or a platform other than the desktop matters.
