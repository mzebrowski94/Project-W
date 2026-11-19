# Lockstep over a stream

Research for [issue 02](../issues/02-lockstep-over-a-stream.md), 2026-11-19. Question: if the server is reachable
only over TCP (raw, or WebSocket behind a platform proxy), what does that cost this game's lockstep, and
what does it change in the `Transport` port?

**Short answer.** A stream is correct but not free: every lost segment holds up the tick traffic behind it
for about **1.25 round trips**. At 1-2 % loss that happens about once a second in each direction. Where the
lead and the playback cushion cover it, the felt delay goes from about one round trip (UDP today) to roughly
**three to three and a half**. Where they do not, the player loses input (the spike rule, above about 45 ms
RTT) or the picture hitches, about once a second. With the ADR 0012 playtest budget (fine up to
50 ms each way) a stream is good enough for pillar 1 only up to about **30 ms RTT on a lossy line**, or on a
line with almost no loss (wired, well under 0.5 %) at any latency UDP would be fine at. A WebSocket `Transport`
keeps the port's contract - reliable in-order is one of the behaviours `UNRELIABLE` allows - but the adapter
must not put a snapshot in front of tick traffic on one stream. Use it as the fallback, not the main path.

## 1. What the port and the adapter do today

Read: `net/src/main/java/pl/mzebrows/pwt/net/transport/*.java`, `net/netty/NettyTransport.java`, ADR 0012.

- `Delivery.RELIABLE`: "arrives, once, in the order it was sent relative to every other reliable message
  on the link, or the link closes". `Delivery.UNRELIABLE`: "arrives once, late, twice, out of order or not at
  all", at most `maxDatagramBytes` (1200).
- `NettyTransport` carries RELIABLE on a TCP channel (`TCP_NODELAY` set on both ends) and UNRELIABLE on a
  UDP channel bound to the same port number. A link opens only when the joiner's UDP token has reached the
  host. The heartbeat (`UDP_PING`/`UDP_PONG`, every 250 ms) runs over UDP and is answered on the event loop.
- Tick traffic: the sequencer sends a `BUNDLES` datagram per peer each time it seals (60 ticks a second),
  repeating the newest `net.pacing.redundantTicks` (2) unacknowledged ticks; `PeerSession.sendInput` sends
  input as UNRELIABLE each step. Snapshots, config and control go RELIABLE, up to `MAX_MESSAGE_BYTES` (8 MB).
- The two kinds never share a queue: a snapshot on TCP cannot delay a bundle on UDP.

## 2. How large the head-of-line stall is

There is no published measurement of a 60 Hz lockstep input stream over TCP at 1-2 % loss and 30-80 ms RTT.
What follows is derived from the loss-recovery mechanics in the RFCs and kernel documentation, then checked
against the one published observation that matches this game's shape.

**Why the stall exists.** TCP delivers bytes in order, so when one segment is lost every segment behind it
waits in the receiver's buffer until the retransmission arrives. The question is only how fast the sender
notices.

**How fast the sender notices (modern stacks).**

- Linux enables RACK-TLP by default: `tcp_recovery` default `0x1` (RACK on), `tcp_early_retrans` default 3
  (TLP on) ([kernel ip-sysctl](https://docs.kernel.org/networking/ip-sysctl.html)).
- RACK marks a segment lost once a later-sent segment has been delivered and the lost one is still
  missing after RTT plus a reordering window; the window starts at `min_RTT/4` (RFC 8985 §3.3.1, §6.2,
  [rfc-editor](https://www.rfc-editor.org/rfc/rfc8985.html)).
- If nothing later is delivered, the Tail Loss Probe fires at `PTO = 2 x SRTT` (plus a delayed-ACK budget
  when one segment is in flight) (RFC 8985 §7.2).
- Windows: TLP has been on by default since Windows Server 2016; a RACK that also recovers lost
  retransmissions shipped in Server 2022, built during the Windows 10 May 2020 Update work
  ([Microsoft Networking Blog](https://techcommunity.microsoft.com/blog/networkingblog/algorithmic-improvements-boost-tcp-performance-on-the-internet/2347061)).
  The desktop clients are Windows.
- The fallback is the retransmission timeout: RFC 6298 says at least 1 s and doubling on each repeat (§2.4,
  §5.5, [rfc-editor](https://www.rfc-editor.org/rfc/rfc6298.html)); Linux uses 200 ms (`tcp_rto_min_us`
  default 200000, kernel ip-sysctl).

**Applied to this game's traffic.** Each direction carries one small message every 16.7 ms, so at
30-80 ms RTT there are 2-5 segments in flight. That is exactly the "thin stream" the kernel documents as the
case where "retransmission mechanisms of the transport protocol are not fully effective" and recovery
falls back on timeouts ([kernel tcp-thin](https://docs.kernel.org/networking/tcp-thin.html)). RACK is what
rescues it: the next message 16.7 ms later is SACKed, so the lost one is resent about `RTT + RTT/4` after it
was first sent and arrives one way later. Stall for the lost message and everything behind it:

| RTT | Normal case (RACK) ~ 1.25 x RTT, + up to one 16.7 ms send gap | TLP case ~ 2-3 x RTT | RTO case (Linux) |
|---|---|---|---|
| 30 ms | ~40-55 ms (3 ticks) | ~60-90 ms | >= 200 ms, then 400 |
| 50 ms | ~65-80 ms (4-5 ticks) | ~100-150 ms | >= 200 ms |
| 80 ms | ~100-120 ms (6-7 ticks) | ~160-240 ms | >= 200 ms |

These figures are derived, not measured. The TLP and RTO columns are the tail: a lost ACK burst, a lost
retransmission on a stack without the new RACK, or Wi-Fi's correlated losses.

**How often.** At 60 messages a second, 1 % loss is 0.6 lost segments a second per direction and 2 % is 1.2.
So a stall comes roughly every 0.8-1.7 s each way. ADR 0012's lead falls only "a tick after a second of
arriving comfortably early", and the cushion gives a tick back only after `cushionCalmTicks` (180, 3 s). Both
therefore **sit at the stall size** whenever they cover it. The session does not freeze or desync: a late
input repeats the held state (ADR 0012).

**The spike rule changes how the upstream stall shows.** `net.pacing.spikeTicks` = 3 (network.properties):
"a peer that goes silent for spikeTicks longer than its usual gap and then sends a burst was held by a
stalling link: that input is late and repeated, but its lead does not rise to cover the spike". A
head-of-line stall looks exactly like that: silence, then a burst. So:

- **Gap no more than 3 ticks over the usual one (stall up to about 65 ms, RTT up to about 45 ms):** the lead
  rises to cover it and stays there. This is extra input delay of about 1.25 RTT.
- **Longer gap (RTT over about 45 ms):** the lead does not rise. About once a second, the 4-7 ticks of input
  behind the lost segment arrive late. The sequencer repeats the previous held state for them and
  discards the real ones; events still land, a few ticks late. The player's stick is ignored for about
  70-120 ms every second. For an arcade shooter that is worse than steady delay. `spikeTicks` = 0 would turn
  it back into steady delay of about 1.25 RTT.

**What the player feels.** Today over UDP, input-to-screen is about one RTT plus margins, because a loss is
covered by the redundant copy in the next datagram. Over a stream, either the lead covers the upstream stall
(about 1.25 RTT) or the player loses input as above. The playback cushion covers the downstream stall (about
1.25 RTT, up to `cushionMaxTicks` = 8 ticks = 133 ms), or the picture hitches about once a second. With both
covered the total is roughly **3.5 x RTT**: about 105 ms at 30 ms RTT and 280 ms at 80 ms. ADR 0012's
playtests found delay fine up to 50 ms each way, about 100 ms round trip plus margins. A lossy stream
therefore stays inside that budget only **below about 30 ms RTT**. Above that it costs lost input
(upstream) and either hitches or a cushion past 100 ms (downstream).

**The one published observation that matches.** Glenn Fiedler's deterministic-lockstep demo sends inputs over
TCP and over UDP with every unacknowledged input repeated in each packet, which is this game's scheme. At
100 ms latency and 1 % loss the TCP side "hitches every few seconds"; it gets "significantly worse" at 250 ms
and 5 %. The UDP side stays smooth at 2 s and 25 % loss
([Gaffer On Games, Deterministic Lockstep](https://gafferongames.com/post/deterministic_lockstep/)). He
gives no stall durations. The Simula thin-stream studies of Anarchy Online (Griwodz and Halvorsen, NOSSDAV
2006, best paper) found "almost only repeated timeouts ruin the game experience". That was on pre-RACK stacks,
and the PDF could not be read to quote its numbers.

**Loss matters more than latency.** At 0.1 % loss (a good wired line) a stall comes about every 16 s per
direction, and the lead and cushion fall back between stalls. A stream then costs one ~1.25 RTT hiccup every
quarter-minute, which is acceptable at any RTT where UDP is acceptable. On Wi-Fi or mobile at 1-2 % it does not.

**Behind a platform proxy** (Render and similar): the proxy ends the client's TCP connection at its edge and
opens a second one to the app. A loss on the client's access leg is repaired on that leg's RTT, not the full
path's, which helps when the edge is near the player. Whether the proxy sets `TCP_NODELAY` on its own legs is
not documented; if it does not, Nagle's algorithm would add delay. Measure before relying on it.

## 3. What others do

| Game / system | Model | Transport | Source |
|---|---|---|---|
| Factorio | lockstep, "only player actions are transferred" | "UDP only", port 34197; since 0.12 simulates some of the player's own actions locally to hide latency | [Factorio wiki, Multiplayer](https://wiki.factorio.com/Multiplayer) |
| GGPO | rollback over input exchange | UDP: `socket(AF_INET, SOCK_DGRAM, 0)` | [ggpo `network/udp.cpp`](https://raw.githubusercontent.com/pond3r/ggpo/master/src/lib/ggpo/network/udp.cpp) |
| Steam Datagram Relay | relay network for game traffic | UDP to the server's `SDR_LISTEN_PORT`; Valve says the relayed route is "never too much slower" than the direct one near its data centres | [Steamworks SDR](https://partner.steamgames.com/doc/features/multiplayer/steamdatagramrelay) |
| OpenTTD | lockstep: `ServerFrame` "tells the client ... to where the client may progress", `ClientCommand` / `ServerCommand` | **TCP** ("TCP packets for game purposes"); last-resort TURN relay through the Game Coordinator | [`tcp_game.h`](https://raw.githubusercontent.com/OpenTTD/OpenTTD/master/src/network/core/tcp_game.h), [game_coordinator.md](https://raw.githubusercontent.com/OpenTTD/OpenTTD/master/docs/game_coordinator.md) |
| OpenRA | lockstep, server relays orders | **TCP**, `new TcpClient(...) { NoDelay = true }` | [`Connection.cs`](https://raw.githubusercontent.com/OpenRA/OpenRA/bleed/OpenRA.Game/Network/Connection.cs) |

The pattern: the **strategy** games, with slow orders and the delay hidden in command latency, run lockstep
over TCP and are fine. Fast games (Factorio's character, fighting games) and the relay networks built for
them are UDP. This game's commander moves on the stick every tick, which puts it with the second group. No
primary source turned up for a fast browser lockstep game over WebSocket; browser games of this pace use
WebRTC data channels or WebTransport when they need loss tolerance.

## 4. A WebSocket `Transport` with Netty 4.2

**What Netty provides.** `io.netty.handler.codec.http.websocketx` (artifact `netty-codec-http`, not yet a
dependency of `net`, which has `netty-transport` and `netty-codec-base` at 4.2.18.Final) has
`WebSocketServerProtocolHandler` and `WebSocketClientProtocolHandler`, the handshaker factories (RFC 6455 is
version 13), `BinaryWebSocketFrame`, `ContinuationWebSocketFrame`, ping/pong/close frames and
`WebSocketFrameAggregator`
([package summary](https://netty.io/4.2/api/io/netty/handler/codec/http/websocketx/package-summary.html)).
`WebSocketDecoderConfig.DEFAULT` caps a frame payload at **65536** bytes
([source](https://raw.githubusercontent.com/netty/netty/4.2/codec-http/src/main/java/io/netty/handler/codec/http/websocketx/WebSocketDecoderConfig.java)).
An 8 MB snapshot therefore needs the cap raised to `MAX_MESSAGE_BYTES` or fragmentation plus the
aggregator. It is the same event-loop model as `NettyTransport`, so the polled queue and the heartbeat
answered on the event loop carry over unchanged.

**Does `UNRELIABLE` arriving reliably make the port lie?** No. The contract is permissive: "arrives once,
late, twice, out of order or not at all". On time, once and in order is one of those outcomes, and the
session is already written to tolerate every one of them. The redundant ticks become duplicate bytes, which
the sequencer and peer already discard. What changes is **cost**, and there are three places it would hurt:

1. **Cross-kind blocking - the one that matters.** Today a snapshot on TCP cannot delay a bundle on UDP. On a
   single WebSocket, a deflated snapshot of a few hundred KB ahead of the bundles holds every peer's ticks for
   its whole transfer time. On a drop-in that is every *other* peer of that link as well, if the server
   forwards one stream per peer in order. The adapter must keep the two kinds apart: either **two WebSocket
   connections per link** (one RELIABLE, one UNRELIABLE, which is the shape `NettyTransport` already has with
   TCP+UDP), or one connection with RELIABLE messages sliced into chunks and UNRELIABLE ones written between
   them first. The first is simpler and matches the existing link handshake: a link opens when both are up,
   as it does now when the UDP token arrives.
2. **The `UNRELIABLE` Javadoc says why it exists**: "waiting for a lost packet to be resent would only make
   the fresh one late". On a stream that is exactly what happens. One sentence in `Delivery` saying that a
   stream transport may deliver it reliably, and then late behind anything sent before it on the same
   connection, keeps the port honest about cost without changing any caller.
3. **Redundancy becomes waste.** `redundantTicks` and the RTT-paced resend cost bytes on a stream and buy
   nothing (the `redundantTicks` comment in `network.properties` puts 8 seats always moving at 8.8 kB/s per
   machine with 2 copies). That is harmless at this size. If it ever matters, a link capability (`ordered`) read in one place would let the
   sequencer send each tick once; it is not needed to ship.

**What else the adapter changes.** No UDP bind step, so there is one fewer failure mode in the reason
strings: a link is open when the WebSocket handshake completes. The heartbeat's RTT now includes head-of-line
stalls, which is the honest number for the lead. Testing: `ConditionedTransport` applies loss only to
UNRELIABLE and drops the message. A **stream mode** would turn each loss into a hold of about 1.25 RTT on that
message and every later one on the link. That is the only way `SessionTest` could show this cost before a
playtest does.

## 5. WebTransport and QUIC datagrams from Java

- **QUIC datagrams** (RFC 9221) are not retransmitted on loss (§5.2) and share the connection's congestion
  controller (§5.4) ([rfc-editor](https://www.rfc-editor.org/rfc/rfc9221.html)). Over one QUIC connection
  they give UDP's loss behaviour for ticks, plus reliable streams with no head-of-line blocking between
  them, TLS, and a single UDP port.
- **Netty 4.2 has it in core.** The QUIC codec graduated from incubator in 4.2.1.Final
  ([release notes](https://netty.io/news/2025/05/06/4-2-1.html)) and HTTP/3 in 4.2.2.Final
  ([release notes](https://netty.io/news/2025/06/05/4-2-2.html)). `QuicCodecBuilder.datagram(recvQueueLen,
  sendQueueLen)` enables datagram support
  ([Javadoc](https://netty.io/4.2/api/io/netty/handler/codec/quic/QuicCodecBuilder.html)). It is built on
  quiche through JNI, so it needs `netty-codec-native-quic` per platform (classifiers such as
  `linux-x86_64`, `windows-x86_64`, `osx-aarch_64`) and a TLS certificate on the server. Netty has no
  WebTransport codec; only a third-party netty-socketio feature request asks for one.
- **Pure Java**: Kwik (QUIC, client and server) and Flupke (HTTP/3). Flupke's README lists "experimental
  support for WebTransport" and requires Java 11 ([Flupke](https://github.com/ptrd/flupke)). It does not
  mention datagrams.
- **On candidate platforms QUIC needs inbound UDP, the same as today's transport.** Render accepts inbound
  HTTP/WebSocket on a single port and nothing else
  ([Render WebSockets](https://render.com/docs/websocket)); staff on its forum say UDP is not on the
  roadmap. Fly.io accepts UDP only on a dedicated IPv4, bound to `fly-global-services`, with the same
  port inside and out, path MTU not guaranteed, and QUIC/HTTP/3 "not supported" though it can be made to
  work with caveats ([Fly.io UDP and TCP](https://docs.fly.io/networking/udp-and-tcp)).

So wherever QUIC works, plain UDP works too, and `NettyTransport` already does that. QUIC would buy
encryption, one port, and no TCP channel for snapshots (which would also close ADR 0012's path-MTU / TCP-join
revisit trigger). It would cost a native library per platform and a certificate. WebTransport matters only
for a browser client, which nothing on this map asks for. Neither helps a platform that is TCP-only.

## 6. Recommendation for the map

- Prefer a platform with **inbound UDP** (Fly.io dedicated IPv4 is one). The server then speaks the existing
  `NettyTransport` protocol and lockstep costs what it costs today plus the relay's detour.
- Keep a **WebSocket `Transport`** as the fallback for networks or platforms that allow only TCP. Use two
  connections per link (or chunked reliable traffic) so a snapshot never sits in front of ticks, and add one
  sentence on `Delivery.UNRELIABLE`. Nothing above the port changes.
- Tell the player when he is on the fallback. Pillar 5 says state is visible, and his input delay roughly
  triples on a lossy line.
- QUIC with datagrams is the later upgrade if encryption or a single port is wanted. It is not a fix for a
  TCP-only host.

## Sources

- ADR 0012, `net/transport/*.java`, `net/netty/NettyTransport.java`, `assets/network.properties` (this repo)
- RFC 8985 RACK-TLP - https://www.rfc-editor.org/rfc/rfc8985.html
- RFC 6298 RTO - https://www.rfc-editor.org/rfc/rfc6298.html
- RFC 9221 QUIC datagrams - https://www.rfc-editor.org/rfc/rfc9221.html
- Linux ip-sysctl - https://docs.kernel.org/networking/ip-sysctl.html
- Linux thin streams - https://docs.kernel.org/networking/tcp-thin.html
- Microsoft, Algorithmic improvements boost TCP performance - https://techcommunity.microsoft.com/blog/networkingblog/algorithmic-improvements-boost-tcp-performance-on-the-internet/2347061
- Gaffer On Games, Deterministic Lockstep - https://gafferongames.com/post/deterministic_lockstep/
- Griwodz and Halvorsen, The Fun of Using TCP for an MMORPG, NOSSDAV 2006 - https://cms.simula.no/sites/default/files/publications/Griwodz.2006.1.pdf (numbers not extracted)
- Factorio wiki, Multiplayer - https://wiki.factorio.com/Multiplayer
- GGPO udp.cpp - https://raw.githubusercontent.com/pond3r/ggpo/master/src/lib/ggpo/network/udp.cpp
- Steam Datagram Relay - https://partner.steamgames.com/doc/features/multiplayer/steamdatagramrelay
- OpenTTD tcp_game.h, game_coordinator.md - https://github.com/OpenTTD/OpenTTD
- OpenRA Connection.cs - https://github.com/OpenRA/OpenRA
- Netty websocketx package, WebSocketDecoderConfig, QuicCodecBuilder, 4.2.1 / 4.2.2 release notes - https://netty.io
- Flupke - https://github.com/ptrd/flupke
- Render WebSockets - https://render.com/docs/websocket
- Fly.io UDP and TCP - https://docs.fly.io/networking/udp-and-tcp
