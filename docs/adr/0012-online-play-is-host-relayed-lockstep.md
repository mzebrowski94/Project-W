# 0012: Online play is host-relayed lockstep, over a transport port of our own

**Status.** amended by ADR 0016: another entity is named by its Handle, never by an Entity field

**Trigger.** Anything that crosses the network; how a tick's inputs reach `Simulation.step` during a
session; proposing state replication, prediction, rollback or a different transport library.

**Enforced by.** `NetArchitectureTest.netKnowsNothingOfTheWindow`, `.nettyStaysInsideItsAdapter`,
`.onlyTheSystemClockReadsTheWallClock`, `.nothingIsDeserialisedByClassName`; the module graph keeps `sim`
from seeing `net`. `NettySessionTest` - the sequencer's thread, the host's peer and a joiner on real
sockets, UDP and TCP, ending on one world. `SessionTest` - a sequencer, the host's peer and others over a
lagging, lossy network: every world the same at the same tick, a player dropped in mid-game, eight slots
from four machines, a machine knocked out of step found, dumped and healed, restart and god mode refused to
anyone but the host. `SequencerTest` - a peer written byte by byte: an order sealed once however often it
is resent, orders in the order given, a late player's input repeated, garbage and another protocol
dropped. `BundleTest` - held input to the bit. `SnapshotRoundTripTest` and, opt-in, `DeterminismFuzzTest`
(ADR 0014).

**Constraint.** **Amends ADR 0004**: its "no networking code" goes; everything else it says is what this rests on.

- **Every peer runs the whole simulation. Only inputs cross the wire during play.** World state crosses
  once, as a snapshot, when a player drops in (G0038) - and in a desync dump, which is for us.
- **The sequencer decides which input applies to a tick, never what happened in it.** It collects each
  peer's inputs, seals each tick when its time comes and broadcasts the sealed **tick bundle**. It runs on
  a thread of its own and holds no simulation: the host's world consumes its bundles as every other peer's
  does, so a host whose frame loop stalls falls behind and catches up instead of freezing the session. A
  peer steps tick T only once it holds T's bundle, plays sealed ticks at the world's rate, plays one extra
  a frame when it holds more than it should, and never skips one; nothing is predicted. The sequencer runs
  on the host's machine today; nothing in it needs to.
- **An input is held state plus events.** Held state - sticks, aim, buttons held - is what a late peer's
  slot repeats from its previous tick, so one slow link never stalls the others. Events - an order, every
  one-off press - are numbered, resent until acknowledged, and applied once, at the first tick the
  sequencer holds them.
- **Each peer sends its input a lead of ticks ahead**, and the sequencer reports how early each arrived.
  The lead rises at once when input is late and falls a tick after a second of arriving comfortably early,
  settling at the round trip plus a margin; it is the input delay that peer's players feel. A tag is judged
  only when the sequencer first hears it, and a peer catching up - after a join, a heal or a stall of its
  own - sends no newest tag, so its own catching up is never taken for late input.
- **A local input goes through the same encode and decode as a remote one**, held input as raw float bits:
  the host's game is a peer on a link to the sequencer like the others, or it drifts by a rounding.
- **Nothing changes simulation state outside `Simulation.step` from a tick's input during a session.**
  Restart and the harness's invulnerability are session commands stamped on a tick by the sequencer; pause
  is the sequencer sealing nothing until resumed (who may ask for which is G0038).
- **Networking is its own module**, `net`: `sim` does not know a network exists; `net` has no libGDX.
- **The transport is a port.** `Transport` lives in `net`; Netty 4.2 sits behind it - UDP for tick
  traffic, TCP for control and snapshots - and an in-memory loopback serves the tests. No Netty type
  crosses it, for the reason ADR 0011 gives for gamepads: the library is the replaceable part.
- **A join is cheap and bounded.** The config (without comments) and the world cross deflated. A peer sent
  a world gets no ticks until its first input about that world says it has built it, then every tick since,
  oldest first; one that has not built it before those ticks leave the history is let go and told why. A
  round-trip sample is capped at twice the lead ceiling. The log says how long each join took to load and
  to catch up.
- **Peers run the same thing.** The build id - a hash of the `map`, `sim` and `net` sources - and the
  protocol version must match at the handshake, or the peer is refused. The config need not: the host ships
  its `game.properties` as its parser reads it, with its scenario and seed, and every peer parses it with
  its own loader, so a tuning playtest needs nobody to copy a file.
- **A desync is detected, recorded and healed.** Every `checksumTicks` each peer snapshots its world, keeps
  it as a keyframe and sends its hash; the host's is the reference. On a mismatch the diverged peer and the
  host each write a dump beside the connection log - the disputed keyframe, the last agreed one and every
  bundle between - and the host's world is sent to the peer by the drop-in path. `DesyncReport` reads the
  pair. The session carries on.

**Why lockstep.** The simulation was built for it: one `CommanderInput` per slot per tick is the only
input, randomness is one seeded `long` per stream, the transcendentals are `StrictMath`, nothing iterates
a hash, and floating point is strict on every platform (JEP 306). A new mechanic owes the network nothing
beyond its state being in the snapshot, which ADR 0014 makes automatic; a peer costs a few tens of KB/s;
any session replays from a seed and a log. The price is input delay - about the round trip to the host
plus a margin - which pillar 1 judges, and why rollback is deferred rather than rejected.

**Why Netty.** No JVM library offers lockstep sessions, so the session protocol is ours whichever library
carries it. Netty is the most proven networking stack on the JVM, serves a relay with the same code, and
its TCP channel beside the UDP one delivers the handshake, the config and a snapshot reliably.

**Scope.** How a session stays in step and what crosses the wire. Not who sits in which slot, what joining
looks like or how many may play - G0038. Not how a player on the internet finds a host: room codes and LAN
discovery are G0038's, and a relay is another way for the same packets to arrive. Not offline play, which
steps from local inputs through `FixedStepClock`.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Replicating state every tick, with prediction on each peer.* Every component would need a
replication story, forever, and every peer but the host would stop running the simulation it draws. It is
the model for a game whose state is small and whose players are many; this one is the other way round.

**No:** *A full peer-to-peer mesh.* Twenty-eight links for eight machines, a NAT to cross for each, and the
slowest link paces everybody. A star through the host has one arbiter for a late input.

**No:** *Waiting for a late input.* One player on bad Wi-Fi would freeze seven others. The deadline repeats
his previous held input; he feels his own link and nobody else does.

**No:** *The sequencer inside the host's frame loop.* Every stall of one window would be a stall of eight.

**No:** *A one-off input as a held flag.* The deadline would repeat it - an order given twice - or a tap
shorter than a late window would never arrive.

**No:** *Requiring every peer's `game.properties` to match.* Every tuning change would mean copying a file
to every friend before the next playtest.

**No:** *A network-library type outside the adapter,* or a `Gdx` call inside `net`.

**No:** *Java serialization, Kryo's reflective mode, or any decoder that instantiates a class a packet
names.* Every byte from the wire is read by code that knows exactly what it expects.

**No:** *Quantised or delta-encoded snapshots.* A snapshot has to restore bit-exact or the joiner is
desynced from its first tick; deflate already takes it to a tenth.

**No:** *KryoNet* - unmaintained, reflective serialization, an object per message. *Aeron* - datacentre
streams, no notion of a player or a connection. *jvm-libp2p* - streams only, no hole punching.
*Nakama* - WebSocket, a database to host, a Java client for Java 8 to 11.

Not rejected, deferred: **rollback** on top of this stream - playtests over Hamachi and ZeroTier measured
real round trips of 15-80 ms and found the delay fine up to 50 ms each way; **a second transport**
(webrtc-java, Steam) behind the same port.

**Revisit when** a playtest in the same region reads as sluggish (rollback); the log shows ticks lost
around joins, or a join held up by TCP on a path that carries datagrams - a path-MTU black hole on one
Hamachi host already costs three seconds (send the welcome and snapshot as paced UDP slices); the relay's
detour measurably hurts (hole punching, or WebRTC behind the port); or a platform other than the desktop
matters.
