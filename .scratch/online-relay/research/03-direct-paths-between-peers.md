# Direct paths between peers - research findings

Ticket: `../issues/03-direct-paths-between-peers.md`. Researched 2026-11-19 against primary sources
(papers, RFCs, the libraries' own repositories and Maven Central, JDK JEPs, the platform's own staff).
Inferences of ours are marked **(inference)**.

## Verdict

**Stays deferred.** Hole punching works often enough to be worth having one day, but it never removes the
relay: by every published measurement a fifth to a third of pairs still need it, and more on mobile. So
the relay has to be built first anyway, and ADR 0012's revisit condition - "the relay's detour measurably
hurts" - cannot be checked until a relay exists to be measured. When the time comes, it fits behind
`Transport` without the port changing, and the cheapest version is hand-rolled on the Netty UDP channel
we already have rather than a library.

## 1. How often hole punching succeeds

| Source | Population | UDP success |
|---|---|---|
| Ford, Srisuresh, Kegel, USENIX ATC 2005 [1] | NAT Check run by volunteers on many NAT vendors | 82% of NATs support UDP hole punching; 64% for TCP |
| Halkes, Pouwelse, IFIP Networking 2011 [2] | Tribler, a live P2P network | ~64% of peers sit behind a box that should allow it; >80% of attempts between those succeed |
| Trautwein et al., arXiv 2510.27500 (Oct 2025), IMC '26 [3] | 4.4 M attempts, 85,000+ networks, 167 countries (IPFS/libp2p DCUtR) | **~70% ±7.1%**, TCP and QUIC statistically indistinguishable; 97.6% of successes on the first try |
| ProbeLab, Aug 2026 [4] | 43,684 classified live IPFS peers | Port-restricted cone 82.9% (n=3,669); **symmetric 39.7%** (n=536, authors doubt these successes); aggregate ~70% |
| WebRTC practice (Hancke, ~100k calls) [5] | Production WebRTC calls | ~12% TURN/UDP + ~5% TURN/TCP + <0.5% TURN/TLS: ~17% relayed |

Where it fails:

- **Symmetric (endpoint-dependent) mapping.** RFC 4787 REQ-1 says a NAT MUST use endpoint-independent
  mapping [6]; symmetric NATs violate that, and the port seen by the reflector is not the port the peer
  will be reached on. ProbeLab's ~40% for symmetric is flagged by its own authors as probably
  misclassification [4].
- **CGNAT, and mobile in particular.** Richter et al., IMC 2016 [7]: more than 90% of cellular ASes run
  a CGN (17-18% of all eyeball ASes). 11% of non-cellular CGN ASes are symmetric even at their most
  permissive; cellular is bimodal, with 40% of cellular CGN ASes symmetric at their most restrictive.
  Port allocation is random in 35.6% (non-cellular) and 44.7% (cellular) of CGN ASes, which defeats port
  prediction.
- **Short UDP timeouts.** 74% of CGNs expire an idle UDP mapping within a minute; median 35 s
  non-cellular, 65 s cellular [7]. A direct path needs a keepalive under that - our tick traffic already
  is one during play, but not in a paused session **(inference)**.

**(inference)** For two desktop players on home broadband, expect roughly 70-80% of pairs to get a direct
path; the rest, and most pairs involving a phone hotspot, go through the relay. In a star (ADR 0012) a
session of eight machines has seven links to the host, so with 70% per link the chance that *every* link
is direct is 0.7^7, about 8%: almost every real session will have at least one relayed peer.

## 2. Java libraries

| Library | What it is | Maintenance (checked 2026-11-19) | Size | Java 25 |
|---|---|---|---|---|
| **ice4j** (jitsi/ice4j) [8] | Pure-JVM ICE/STUN/TURN client, Apache-2.0 | Active: last push 2026-11-17; 485 stars. Releases are git-describe builds (`3.2-10-gfcadc70`, jar dated Nov 2025), not semantic versions | 494 KB jar, but drags `kotlin-stdlib`, `kotlin-reflect`, Typesafe Config, `jitsi-utils` (with JNA), `jicoco-config`, `jitsi-metaconfig`, weupnp, an SDP library [8][9] | Compiled `--release 17`; pure JVM, so it runs on 25 **(inference: not tested here)**. API built round SDP offer/answer and Jitsi's config system |
| **webrtc-java** (devopvoid/webrtc-java) [10] | JNI bindings to Google's native WebRTC: ICE + DTLS + SCTP data channels (reliable and unreliable) | Active: v1.0.1 on 2026-11-14, v1.0.0 2026-11-06, v0.20.0 2026-10-16 | 654 KB Java jar + a native jar of **7.8-10.5 MB per platform** (linux x86_64 10.5 MB, windows x86_64 9.2 MB, macos aarch64 7.8 MB) [11] | Compiled `--release 11`. JNI: since JDK 24 loading it warns unless the launcher passes `--enable-native-access`, and a future JDK will refuse by default [12]. (LWJGL already puts us in that position.) |
| **Netty** | No STUN/ICE codec in Netty or its incubator; none found [13] | - | - | - |
| Small STUN-only libraries (hf/stun, JSTUN) [13] | Message format plus a client | Small, low activity | Tiny | Not checked |

**(inference)** Neither library is attractive for this game. ice4j is maintained but is a Jitsi-shaped
dependency tree (two Kotlin runtimes' worth of jars and a config framework) for a job our Netty UDP
channel can mostly do. webrtc-java brings DTLS and congestion control we would get for free, but ~10 MB
of native code per platform and the JNI flag. What a direct path for *this* game needs is small - STUN
Binding (RFC 8489) is a 20-byte header plus attributes, and Ford et al.'s simultaneous-open [1] is a
handful of messages - so a hand-rolled reflector and punch on the existing `net.netty` UDP channel is the
proportionate shape, as ADR 0012 already chose for the session protocol ("the session protocol is ours
whichever library carries it").

## 3. What the server would have to provide

ICE (RFC 8445) assumes a signalling channel between the agents and leaves its transport to the using
protocol; STUN gives server-reflexive candidates, TURN relayed ones [14].

| Piece | Needs | Can an HTTP-only platform host it? |
|---|---|---|
| **Signalling** (exchange candidates, start the punch at the same moment) | Any reliable message channel | **Yes.** It is a few small messages per link; HTTP or a WebSocket carries them. The room's control connection is the natural place **(inference)** |
| **Reflector** (STUN-like: "what address do I come from?") | A public **UDP** port | **No.** The reflected address must be the UDP mapping the punch will use. Render staff: public traffic is HTTP(S) only, UDP not on the roadmap [15]. Public STUN servers exist but are a third party's service with no promise to us |
| **Fallback relay** (TURN-like) | Carries the session's traffic for the pairs that fail | Over a WebSocket, yes - but then tick traffic rides TCP; whether that is acceptable is ticket 02 (lockstep over a stream) |

**(inference)** If the relay speaks UDP, the reflector comes free: the relay already sees each peer's
public UDP endpoint on its first datagram. If the platform is HTTP-only, direct paths would need a second,
UDP-capable host just for reflection - which is exactly the host that could have run a UDP relay in the
first place. So direct paths are worth most on the platform that needs them least.

## 4. How it would sit behind `Transport`

`Transport` (`net/transport/Transport.java`) is link-numbered, polled, and delivery is chosen per message
(`RELIABLE` / `UNRELIABLE`). Nothing in it names a path. **(inference throughout)**

- **Same link, path upgraded underneath.** A link opens over the relay and is reported `opened` as today.
  The adapter then tries a punch in the background; on success it moves `UNRELIABLE` traffic to the direct
  UDP path and keeps the link number; on a lost keepalive it falls back to the relay. The session layer
  never learns which path a datagram took - which is what ADR 0012's "a relay is another way for the same
  packets to arrive" asks for.
- **Reliable traffic stays on the relay.** TCP hole punching succeeds less often (64% vs 82% in [1]), and
  a reliable stream over the punched UDP path would be a protocol of our own. Ticks are the only traffic
  where the detour costs feel; the handshake, config and snapshots can keep the relay's path.
- **No library type crosses the port**: the punch, the reflector client and any library live inside the
  adapter package, under an architecture rule like `nettyStaysInsideItsAdapter`. If a library were used,
  it would get a package of its own with the same rule.
- **What it does to the port:** `connect(String host, int port)` addresses a socket. Reaching a peer
  through a room is the rooms ticket's question (04), not this one's. A direct path adds nothing to the
  port beyond what the relay already forces - at most a read-out of whether a link is direct, for
  `NetLog` and the host overlay.
- **Tests:** `LoopbackNetwork` and `ConditionedTransport` model a path, not a NAT. A punch would need a
  fake NAT in the in-memory network (mapping and filtering per RFC 4787) to be tested deterministically.

## 5. When to revisit

The revisit condition in ADR 0012 stands and becomes checkable once the relay exists: compare, per link,
the round trip through the relay with the direct one (a ping on a punched path is cheap to add as a
measurement before building the upgrade). The detour hurts when players are near each other and far from
the server - a server in one region, a session in another. With the server in the players' region the
detour is a few milliseconds against the 50 ms each way that Hamachi playtests found fine (ADR 0012).

## Sources

1. Ford, Srisuresh, Kegel, *Peer-to-Peer Communication Across Network Address Translators*, USENIX ATC
   2005 - https://www.usenix.org/conference/2005-usenix-annual-technical-conference/peer-peer-communication-across-network-address ; arXiv cs/0603074
2. Halkes, Pouwelse, *UDP NAT and Firewall Puncturing in the Wild*, IFIP Networking 2011,
   doi:10.1007/978-3-642-20798-3_1 - https://dl.ifip.org/IFIP-LNCS/hal-01597969v1
3. Trautwein, Ihle, Schubotz, Gipp, *Challenging Tribal Knowledge - Large Scale Measurement Campaign on
   Decentralized NAT Traversal*, arXiv 2510.27500 - https://arxiv.org/abs/2510.27500 ; IMC '26 version
   arXiv 2604.12484
4. ProbeLab, *Can libp2p punch through NATs?*, 2026-08-03 - https://probelab.io/blog/can-libp2p-punch-through-nats/
5. P. Hancke, *What kind of TURN server is being used?* - https://medium.com/@fippo/what-kind-of-turn-server-is-being-used-d67dbfc2ff5d
6. RFC 4787, NAT Behavioral Requirements for Unicast UDP - https://www.rfc-editor.org/rfc/rfc4787
7. Richter et al., *A Multi-perspective Analysis of Carrier-Grade NAT Deployment*, IMC 2016 -
   https://arxiv.org/abs/1605.05606 (sections 6.2, 6.5)
8. jitsi/ice4j - https://github.com/jitsi/ice4j (repo API, `pom.xml` on master: `<release>17</release>`,
   kotlin 2.4.20) ; Maven Central `org.jitsi:ice4j` 3.2-10-gfcadc70
9. Maven Central POMs of `org.jitsi:jicoco-config` 1.1-179-g52cf6ef and `org.jitsi:jitsi-utils`
   1.0-127-g6c65524
10. devopvoid/webrtc-java - https://github.com/devopvoid/webrtc-java (releases; `pom.xml`: `<release>11</release>`)
11. Maven Central listing - https://repo1.maven.org/maven2/dev/onvoid/webrtc/webrtc-java/1.0.1/
12. JEP 472, Prepare to Restrict the Use of JNI (delivered JDK 24) - https://openjdk.org/jeps/472
13. No Netty STUN/ICE codec found; small STUN libraries: https://github.com/hf/stun , JSTUN (not checked further)
14. RFC 8445, ICE, sections 2, 2.1, 3, 5, 5.1.1.2 - https://www.rfc-editor.org/rfc/rfc8445 ; STUN RFC 8489;
    TURN RFC 8656
15. Render community, staff answer on UDP - https://community.render.com/t/opening-up-a-udp-port-for-incoming-traffic/821
    (a forum answer, not the docs; ticket 01 owns the platform question)
