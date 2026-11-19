# Direct paths between peers

Type: research
Status: resolved

## Question

Can two machines on home networks reach each other directly with the server's help (UDP hole punching:
STUN, ICE), so that the server only introduces them and relays when that fails? And what would it take in
Java?

- What share of home and mobile NATs hole punching succeeds through, from published measurements. Where it
  fails (symmetric NAT, CGNAT), the relay has to carry the traffic.
- The Java libraries for it: ice4j, webrtc-java (deferred in ADR 0012), anything Netty-based. Are they
  maintained, how big are they, and do they work on Java 25?
- What the server needs to provide for it: a STUN-like reflector, signalling, and a TURN-like relay as the
  fallback. Can a platform that offers only HTTP host the signalling part?
- How a direct path would sit behind `Transport` (ADR 0012: no library type crosses the port).

The answer says whether direct paths are worth building now or stay deferred (ADR 0012's revisit
condition: the relay's detour measurably hurts).

Findings: `research/03-direct-paths-between-peers.md`.

## Answer

**Stays deferred.** Hole punching succeeds for roughly 70% of pairs in the largest measurement (4.4 M
attempts, 2025), ~40% at best behind symmetric NATs, and worse on mobile, where over 90% of carriers run a
CGN. So the relay is never optional: in an eight-machine star almost every session has a relayed peer, and
ADR 0012's revisit condition (the detour measurably hurts) can only be checked once a relay exists.

- **Server.** Signalling can ride HTTP or the room's control connection. The reflector needs a public UDP
  port, which an HTTP-only platform cannot give. A UDP relay supplies it for free.
- **Java.** ice4j is maintained and pure JVM (release 17), but it brings Kotlin, Typesafe Config and the
  Jitsi utilities. webrtc-java is active (1.0.1, Nov 2026), but it carries 8-10 MB of JNI natives per
  platform and needs `--enable-native-access` on JDK 24+. Netty has no STUN or ICE. The proportionate
  shape is a hand-rolled reflector and punch on the existing Netty UDP channel.
- **Port.** The same link is opened over the relay, and its unreliable traffic later moves to a punched
  path inside the adapter. Reliable traffic stays on the relay. `Transport` does not change, and no
  library type crosses it.

Details and sources: [../research/03-direct-paths-between-peers.md](../research/03-direct-paths-between-peers.md).
