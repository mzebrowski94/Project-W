# Lockstep over a stream

Type: research
Status: resolved

## Question

If the only way to reach the server is TCP (raw, or WebSocket behind a platform's proxy), what does that
cost this game's lockstep, and what does it change in the `Transport` port?

Today tick traffic is UDP, and every bundle datagram repeats the newest `net.pacing.redundantTicks`
unacknowledged ticks, so a lost datagram costs nothing. Control traffic and snapshots go over TCP
(ADR 0012, `NettyTransport`). Over one TCP stream a lost segment holds up every message behind it
(head-of-line blocking). Find out:

- how large the stall is under realistic loss (1-2 %) and RTT (30-80 ms), from measurements or published
  analysis;
- what other lockstep or input-relay games do over TCP and WebSocket, with sources: GGPO-style relays,
  Factorio, browser lockstep games, Steam Datagram Relay;
- the WebSocket client and server Netty 4.2 already provides, and whether a `Transport` over WebSocket fits
  the port as it is. `Delivery.UNRELIABLE` would arrive reliably. Is that acceptable, or does the port lie?
- whether WebTransport or QUIC (datagrams over HTTP/3) is usable from Java today and on any candidate
  platform.

Read `net/src/main/java/pl/mzebrows/pwt/net/transport/` and `net/netty/NettyTransport.java` for the port.
External sources for the rest. The answer says whether a stream is good enough for pillar 1, and at what
latency it stops being so.

Findings: `research/02-lockstep-over-a-stream.md`.

## Answer

Full findings: [research/02-lockstep-over-a-stream.md](../research/02-lockstep-over-a-stream.md).

- **Cost.** Over one stream, a lost segment holds up every tick behind it. With RACK-TLP (the default on Linux,
  and on Windows since Server 2016/2022) the hold is about **1.25 x RTT**: about 40-55 ms at 30 ms RTT and
  100-120 ms at 80 ms. Rare tails are 2-3 x RTT on a tail-loss probe and 200 ms or more on a timeout. At 60
  messages a second and 1-2 % loss, that is **about one stall a second in each direction**. These figures are
  derived from RFC 8985 and RFC 6298, not measured. Gaffer On Games saw the same lockstep scheme over TCP
  "hitch every few seconds" at 100 ms and 1 %.
- **Effect here.** The session stays correct: no freeze, no desync. A stall up to about 4 ticks raises the
  lead. A longer one trips `net.pacing.spikeTicks` (3): the lead does not rise, and the player's held input
  for those ticks is replaced by the previous tick's, so his stick is ignored for about 70-120 ms each second.
  Downstream stalls either hitch or hold the cushion at their size. With both covered, input-to-screen goes
  from about 1 x RTT (UDP) to about **3.5 x RTT**.
- **Pillar 1.** A stream is good enough only **below about 30 ms RTT on a lossy (1-2 %) line**, or on a line with
  almost no loss (wired, under about 0.5 %) at any RTT where UDP is fine. Above about 45 ms RTT with loss,
  it costs lost input rather than delay, which is wrong for an arcade shooter.
- **Others.** The strategy games run lockstep over TCP: OpenTTD, and OpenRA with `NoDelay`. The fast ones and
  the relays built for them use UDP: Factorio ("UDP only"), GGPO and Steam Datagram Relay.
- **The port.** A WebSocket `Transport` (Netty 4.2 `codec-http` `websocketx`; the default frame cap is 64 KB,
  so raise it or fragment for 8 MB snapshots) keeps the contract. Reliable and in order is one of the outcomes
  `UNRELIABLE` already allows, and the session already discards duplicates. It needs three things:
  - **(a)** No snapshot in front of ticks on one stream: two WebSocket connections per link, as TCP+UDP
    today, or chunked reliable traffic.
  - **(b)** One sentence on `Delivery.UNRELIABLE` saying a stream transport may deliver it late, behind
    whatever was sent before it.
  - **(c)** A stream mode in `ConditionedTransport`, so `SessionTest` can show the cost.

  Redundant ticks become harmless waste.
- **QUIC/WebTransport.** Netty 4.2 has QUIC (in core since 4.2.1) with RFC 9221 datagrams
  (`QuicCodecBuilder.datagram`), built on native quiche, and HTTP/3 since 4.2.2. It has no WebTransport;
  Flupke has experimental support for it. All of these need inbound UDP, so they do not help a TCP-only host
  such as Render. Where UDP exists (Fly.io, on a dedicated IPv4) plain UDP and today's `NettyTransport`
  already work.
- **Recommendation.** Pick a platform with inbound UDP. Keep WebSocket as the fallback, and show the player
  when he is on it (pillar 5).
