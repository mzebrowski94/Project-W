# 0012: Online play is host-relayed lockstep, over a transport port of our own

**Trigger.** Anything that crosses the network; how a tick's inputs reach `Simulation.step` during a
session; proposing state replication, prediction, rollback or a different transport library.

**Enforced by.** `NetArchitectureTest.netKnowsNothingOfTheWindow`, `.nettyStaysInsideItsAdapter`,
`.onlyTheSystemClockReadsTheWallClock`, `.nothingIsDeserialisedByClassName`; the module graph keeps `sim`
from seeing `net`. `NettySessionTest` - the sequencer's thread, the host's peer and a joiner on real
sockets, UDP and TCP, ending on one world. `SessionTest` - a sequencer, the host's peer and others in one test over a lagging,
lossy network: every world the same at the same tick, a player dropped in mid-game, eight slots from four
machines, a machine knocked out of step found, dumped and healed, restart and god mode refused to anyone
but the host. `SequencerTest` - a peer written byte by byte: an order sealed once however often it is
resent, orders in the order given, a late player's input repeated, garbage and another protocol dropped.
`BundleTest` - held input to the bit. `SnapshotRoundTripTest` and, opt-in, `DeterminismFuzzTest`
(ADR 0014).

**Supersedes** the Scope clause of ADR 0004 - "no networking code exists and none should be written".
Everything else 0004 says stands, and is now what multiplayer rests on rather than what keeps it possible.

**Constraint.**

- **Every peer runs the whole simulation. Only inputs cross the wire during play.** World state crosses
  once, as a snapshot, when a player drops in (G0038) - and in a desync dump, which is for us.
- **The sequencer decides which input applies to a tick, never what happened in it.** It collects each
  peer's inputs, seals each tick when its time comes, and broadcasts the sealed **tick bundle**. It runs on a thread of its
  own and holds no simulation: the host's own world consumes its bundles exactly as every other peer's
  does, so a host whose frame loop stalls - a window being dragged, a pause for garbage collection - falls
  behind and catches up like anyone else instead of freezing the session. A peer plays sealed ticks at the
  world's rate, plays one extra a frame when it holds more than it should, and never skips one. Today it runs on the host's
  machine; nothing in it needs to, and moving it to the relay is how the host's advantage and the host's
  leaving stop mattering. A peer steps tick T only once it holds T's bundle; nothing is predicted.
- **An input is held state plus events.** Held state - the sticks, the aim, the buttons being held - is
  what a late peer's slot repeats from its previous tick, so one slow link never stalls the others.
  Events - an order given, and every one-off press to come - are numbered, sent again until the sequencer
  acknowledges them, and applied once, at the first tick the sequencer holds them: never repeated by the
  deadline, never lost to it.
- **Each peer sends its input a lead of ticks ahead** - tagged for the tick it is playing plus the lead -
  and the sequencer reports how early each arrived. The lead follows: up at once when input is late, down
  a tick after a second of arriving comfortably early, so it settles at the round trip plus a margin. The
  lead is the input delay that peer's players feel.
- **A local input goes through the same encode and decode as a remote one** before the simulation sees
  it: the host's own game is a peer on a link to the sequencer like the others, and held input crosses
  as raw float bits. The host is a peer like the others in this respect, or it drifts by a rounding.
- **Nothing changes simulation state outside `Simulation.step` from a tick's input during a session.**
  Restart and the harness's invulnerability are session commands, stamped on a tick by the sequencer and
  carried in its bundle; pause is the sequencer sealing nothing until it is resumed, which every peer sees
  alike (who may ask for which is G0038). Offline play keeps doing what it does today.
- **Networking is its own module**, `net`: `lwjgl3 -> core -> net -> sim -> map`. `sim` does not know a
  network exists; `net` has no libGDX.
- **The transport is a port.** `Transport` lives in `net`; Netty 4.2 sits behind it - UDP for tick
  traffic, TCP for control and snapshots, the config and snapshot a join waits for deflated - and an
  in-memory loopback serves the tests. No Netty type
  crosses it, for the reason ADR 0011 gives for gamepads: the library is the replaceable part.
- **Peers run the same thing.** The build id - a hash of the `map`, `sim` and `net` sources, generated at
  build time - and the protocol version must match at the handshake, or the peer is refused. The config
  does not have to: the host ships its `game.properties` as its parser reads it - every key and value, the
  comments left out - with its scenario and seed, and every
  peer parses it with its own loader - which the build id says is the same one - so a tuning playtest
  needs nobody to copy a file.
- **A desync is detected, recorded and healed.** Every `checksumTicks` each peer snapshots its world,
  keeps it as a keyframe and sends its hash; the host's is the reference. On a mismatch the peer that
  diverged and the host each write a dump beside the connection log - the disputed keyframe, the last
  agreed one and every bundle between - and the host's world is sent to the peer by the same path a
  drop-in takes. `DesyncReport` reads the pair: the fields that differ, which side a replay lands on, and
  the first tick a replay through snapshots parts from a straight one. The session carries on; the dump
  is how the bug gets fixed.

**Why lockstep.** The simulation was built for it: one `CommanderInput` per slot per tick is already the
only input, randomness is one seeded `long` per stream, the transcendentals are `StrictMath`, nothing
iterates a hash, and since Java 17 floating point is strict on every platform (JEP 306). What that buys:
a per-feature cost of nothing on the network - beyond the rule 0004 already enforces, the one thing a new
mechanic owes is that its state is carried by the snapshot, which ADR 0014 makes automatic - a few tens of
KB/s per peer, and any session replayable from a seed and a log. What it costs is input delay - roughly the round trip to the
host plus a margin, about four to six ticks for a peer in the same region and two for the host. That
is the number pillar 1 will judge, and it is why rollback is deferred rather than rejected.

**Why Netty.** No library for the JVM offers lockstep sessions - Photon Quantum does, and is C# only -
so the session protocol is ours whichever library carries it. Netty is the most proven networking stack
on the JVM, it serves a relay with the same code, and a TCP channel beside the UDP one delivers the
handshake, the config and a snapshot of tens or hundreds of kilobytes reliably, with no code of ours.

**Scope.** How a session stays in step and what crosses the wire. Not who sits in which slot, what
joining looks like or how many players there may be - that is G0038. Not how a player on the internet
finds a host: room codes and LAN discovery are G0038's, the relay is a later record, and a relay is only
another way for the same packets to arrive. Not offline play, which steps from local inputs through `FixedStepClock` exactly as
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

**No:** *Quantised or delta-encoded snapshots.* A snapshot has to restore bit-exact or the joiner is
desynced from its first tick, and it is sent once a join; deflate already takes it to a tenth.

**No:** *KryoNet* - no release since August 2024, reflective serialization, an object per message.
*Aeron* - built for datacentre streams, with no notion of a player or a connection, and JVM flags to
run. *jvm-libp2p* - streams only, no hole punching, its relay in beta. *Nakama* - realtime over
WebSocket, a database to host, and a Java client that targets Java 8 to 11.

Rollback on top of this stream is not rejected, deferred: it is the answer if a playtest finds the input
delay sluggish. **The first latency playtest (2026-09-24, two and three copies on one PC, lag simulated on
the joiner)** found it fine up to 50 ms each way - five ticks of input delay at 25, eight at 50 - and at
100 ms each way, fourteen ticks (234 ms), walking noticeably late: not critical, should be better. The
round trip is 200 of those 234 ms, so only prediction would hide it. Decided: measure real round trips
over the relay first, and plan rollback only if real games land near 100 ms each way. The same playtest
showed the sequencer's thread waking on time on Windows - median 1 ms late, worst 5 - and no desync in
five sessions, drop-ins, restarts and god mode among them. The snapshot codec G0038 needs is a part of it and no more - rollback restores the world in
place every tick, and a drop-in rebuilds it once. The latency playtest comes before the relay (stage 7)
so that this is known before friends play across the internet - it was to come before the join screen,
and the join screen went first by decision (2026-09-24), because rollback would change the session under
the screens, not the screens; the cosmetic lead - the client drawing its own commanders facing their
players' aim at once, feeding nothing back - is built and is the first thing tried (F2 switches it). A second transport - webrtc-java for direct
peer-to-peer through NAT, or Steam's - is also deferred, and the port is what keeps either cheap.

**The second playtest (2026-09-27, friends over Hamachi and ZeroTier)** measured what the first could
not: real round trips of 15-80 ms, so rollback is still not indicated. It found joins, not ticks, to be
the problem. A drop-in sent about 213 KB over TCP - the config with its comments and the world - and
over Hamachi it took six to eight seconds (the fourth playtest found why: full-size TCP packets from that
host were dropped for about three seconds, then crawled - not a slow link), while the sequencer streamed
it ticks it could not yet place; a round-trip estimate timed from those ticks' first sends read ten
seconds, held back their resends, and the joiner was sent the world every ten seconds for as long as it
stayed. Decided, after the literature (Age of Empires' lockstep, Factorio's join, Photon Quantum's late
join, Fiedler, RFC 6298 - `claude/project-w-join-cost-and-netcode-literature.md`): a join's config and
world cross deflated and the config without comments, about 16 KB in all; a peer sent a world is sent no
ticks until its first input about that world says it has built it, then every tick since, oldest first; a
peer that has not built it before those ticks leave the history is let go and told why, as Factorio does,
rather than sent it again; a round-trip sample is capped at twice the lead ceiling; and the log says how
long each join took to load and to catch up. TCP beside UDP stays: the literature warns that a TCP burst
costs a shared link's datagrams, and the burst is now a thirteenth of what it was.

**The third playtest (2026-09-28, ZeroTier both ways, 60 ms round trip)** saw joins take under half a
second and the log say so, and found the lead judged on input that was late for reasons of the peer's
own: its first seconds after a join spent at 17 and 18 ticks, and a 180 ms gap in delivery raising it
from 8 to 18. Decided: a peer sends no newest tag while it is catching up - after a join, a heal or a
stall of its own - and the sequencer judges a tag only when it first hears it, so one said again by a
peer with nothing new to tag is not taken for late input. The lead rises for input that was late in
step, as before; a peer that runs dry on a gap in the network is still left with a cushion of a tick or
none, and whether one more tick of standing delay is worth the stalls it would save is for a later
playtest to say.

**The fourth playtest (2026-09-28, Hamachi both ways)** confirmed the lead fixes - a joiner 199 ticks
behind caught up without its lead moving - and found what had made Hamachi joins slow: from one host, the
first TCP data to a joiner - even a 4.5 KB welcome - arrived only after about three seconds, every time,
while that link's small datagrams crossed at 60 ms without loss and the same joiner's own host was
reached in a tenth of a second. Full-size TCP packets on one side of that Hamachi path were being
dropped until the sender's TCP gave up on them - a path-MTU black hole - which datagrams kept under
`maxDatagramBytes` never meet. A join over that path now takes about three and a half seconds rather than
half a second; it completes, and the history leaves room for it. Not yet decided: whether that is worth
sending the welcome and the snapshot as paced UDP slices, which the next trigger below already names.

**Revisit when** a playtest in the same region reads as sluggish (rollback); the log shows ticks lost
around joins, or a join held up by TCP on a path that carries datagrams (the welcome and the snapshot as
paced UDP slices, Fiedler's large blocks); the relay's detour measurably
hurts (hole punching, or WebRTC behind the port); or a platform other than the desktop matters.
