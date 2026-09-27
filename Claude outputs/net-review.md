# `net` module - critical review and staged plan

## Decisions (2026-09-27)

Stage 1 before the Hamachi test · D1 fixed by X1 (timer resend + redundancy) · M2 through `ConfigBinder` ·
stage 4 splits: do them.

### Stage 1 (before the Hamachi test)

| Item | Done | Evidence |
|---|---|---|
| D1 bundle window | `Sequencer.writeBundles`: every datagram reserves room for the newest `redundantTicks` (3) unacknowledged ticks; older ones go oldest first, only if never sent or unacknowledged for a round trip + 2 ticks. The round trip is measured per peer from a tick's first send to its ack, and logged as `rttMs` in `cat=stats`. The wire format did not change. | Two new tests, red before: `SessionTest.keepsAFarMachineInStepWhenEverySeatIsTaken` (8 seats, 120 ms ±10, 5% loss: **460 ticks behind → ≤ 24**) and `SequencerTest.keepsSendingNewTicksToAPeerWhoseAcknowledgementsAreLate`. Probe rerun: behind is half a round trip at every seat count; **8 seats at 60/90/120 ms: 401/600/602 → 4/6/8 ticks behind, lead 30 → 10/14/18**. Bundle bytes to a far peer per tick: **2 seats at 120 ms 758 → 142; 8 seats 1072 → 466**. |
| D2 trace | `NetLog.trace(owner)` hands each transport its own ring; `NettyTransport` takes a name (`sequencer` / `peer`), and every dump line carries `tr=`. | `NetLogTest.handsEachTransportARingOfItsOwnNamedInItsDump`. The race is gone by construction: no ring is shared between threads. |
| D3 oversized snapshot | The host peer logs `event=snapshot-too-big` and sends nothing, so the joiner waits and the session plays on. The sequencer's bound now leaves room for the 13-byte header (`Msg.SNAPSHOT_HEADER`). | `SessionTest.leavesAJoinerWaitingRatherThanFailWhenTheWorldIsTooBigToSend`: red with an `IllegalArgumentException` out of `pump`, now green. |
| E2 desync runner | `DesyncReportRunnerTest` (core, `PWT_DESYNC=<dir>`) pairs dumps by tick, replays with `ScenarioWorlds` (the host's shipped config) and writes `desync-t<tick>.report.txt` beside them. Recipe in `workflow.md`. | Run on dumps from a real knocked-out session: fields listed, "the reference's world", "no hidden state". |
| - | Full `verify.sh` green, 1471 tests. | - |

Key added: `net.session.redundantTicks=3`.

### Stage 2 (guards)

| Item | Done | Evidence |
|---|---|---|
| D5 buttons | `BundleTest.carriesEveryComponentOfAHeldInput`: builds a `CommanderInput` through its canonical constructor with each component set alone and round-trips it, so a new button or axis that `Bundle` does not carry fails by name. | Seen failing, naming `aimRelative`, with that bit dropped from `Bundle.buttons`; restored. |
| D4 drain | **Not changed, on purpose.** Measured under a 30 ± 12 ms link for 20 s: held ticks played early **0.05/s at 30, 60, 144 and 240 Hz alike**. At high frame rates the cushion resets whenever a frame has nothing extra, so the frame count never reaches 60. The rule is frame-based but costs nothing measurable. | Throwaway probe with a temporary counter, both removed. |
| M3 rings | `NetConfig.Session.MOST_LEAD_TICKS` (120) is the one ceiling; `Sequencer.AHEAD` and `PeerSession.OWN` are derived from it (still 128). | Arithmetic: `highestOneBit(120) · 2 = 128`, so behaviour is unchanged; the whole suite passes. |
| Class 11 | `NetArchitectureTest.nothingElseReadsTheCalendarOrSleeps`: no `java.time` `now()` and no `Thread.sleep` outside `NetLog`. | Seen failing on a probe calling `Instant.now()` and `Thread.sleep` (2 violations, only the new rule red); restored. |
| Stale docs | `NetLog` dangling "3.b of the online plan" removed; "every few seconds" → `statsSeconds` (`NetLog`, `LinkStats`, `architecture.md`); `WorldFactory` no longer claims `net` cannot parse a config; ADR index row for 0012 notes that 0016 supersedes its `Entity` snapshot hazards. | Strip-and-compare against the previous commit: `NetLog`, `LinkStats`, `WorldFactory` code identical. |
| - | Full `verify.sh` green, 1473 tests. | - |

Stage 1 deliverable: verify, propose, plan. **No production code changed.** Baseline `:net:test` green (65
tests). Numbers come from throwaway probes (`Zz*ProbeTest`) run on `SessionHarness` / `LoopbackNetwork` in
this session and deleted afterwards. Nothing here re-proposes a `docs/backlog.md` entry.

## What is already good

Not worth changing, and I would argue for each again: a polled transport port with Netty behind it
(`NetArchitectureTest` holds it); time only through `Clock` in **integer nanoseconds** everywhere (defect
class 2 checked: no float timer in `net`; `tickNanos` is derived once from the host's float tick and shipped
in the welcome, so every peer uses the same integer); a session driven by `pump(now)` so a whole
lockstep game runs on a manual clock in one test; held input lossless as raw float bits; events numbered and
applied once; per-message trace in a preallocated ring instead of logging; the build id hashing
`map`+`sim`+`net`; garbage and oversize input rejected as `WireException` without killing the sequencer; the
desync dump + `DesyncReport` (field diff, which side a replay lands on, first tick of hidden state) is
better than most shipped games have. The core rebinds `simulation()` by identity every frame
(`ScenarioScreen:401`, `ReadoutWriter:87`), so a heal or restart never leaves a stale world drawn (class 3
checked).

---

## A. Verified defects

| # | Finding | Evidence | Severity |
|---|---|---|---|
| D1 | **A far peer starves once its unacknowledged bundles outgrow one datagram.** `sendBundles` sends ticks `acked+1 ..` oldest first and stops at `maxDatagramBytes` (`Sequencer.java:313-320`). The ack only moves once per round trip, so the peer receives at most `k = floor(1190 / (8 + 18·seats))` new ticks per round trip. When the round trip is longer than `k` ticks it falls behind for good, its lead climbs to `leadMaxTicks` (0.5 s input delay), and after 600 ticks it is "healed" by a snapshot, then falls behind again. | Probe, 30 s sessions, far peer at one-way latency L, others perfect. **8 seats: L=60 ms → 401 ticks (6.7 s) behind, lead 30; L=90 → hit 600 and healed.** 4 seats: L=90 fine (6 behind), **L=120 → 319 behind, lead 30.** 2 seats: fine to L=120 (8 behind, lead 18). The model matches every row: starves when RTT in ticks + ~2 > k (k = 27 / 14 / 7 for 2 / 4 / 8 seats). | **High** at 4+ seats over a relayed or long link. Two machines, one seat each: not hit below ~450 ms round trip. |
| D2 | **The host's two transports share one `NetTrace` across two threads.** `OnlineSession.host` opens one `NetLog` and hands it to the sequencer's `NettyTransport` (sequencer thread) and the host peer's (game thread) - `OnlineSession.java:102,105,123`. `NetTrace.record` is unsynchronised (`NetTrace.java:50-56`) and documents itself as single-threaded (`:11`). Both transports number links from 1 (`NettyTransport.java:104`), so a dump for `link=1` interleaves the sequencer's link to the first joiner with the host's own loopback link. | Probe: two threads × 2M records into one trace → **1,819,186 of 4,000,000 lost (45%)**, and torn entries. The `cat=trace` dump is the one thing written when a friend's link drops. | **Medium** - it corrupts exactly the evidence a Hamachi drop leaves behind. No gameplay effect. |
| D3 | **A snapshot larger than a reliable message crashes the host's game thread** instead of refusing the drop-in. The host peer sends `SNAPSHOT_TAKEN` (snapshot + 13 bytes) and `send` throws `IllegalArgumentException`, which `PeerSession.Listener` does not catch (`PeerSession.java:552-558, 763`). The sequencer re-wraps a snapshot of up to `maxMessageBytes` plus 13 header bytes (`Sequencer.java:549, 564`), so the edge case throws on the sequencer thread and ends the session. | Probe with `maxMessageBytes=20000`, `Advance` (24 KB snapshot), late joiner: `IllegalArgumentException ... at PeerSession.received(PeerSession.java:558)` out of `pump`. Today's largest snapshot is 138 KB (`Adventure`) against 8 MB. | Low (latent, 60× headroom), but the failure mode is a crash. |
| D4 | **Input pacing depends on the frame rate.** `DRAIN_FRAMES = 60` ("about a second", `PeerSession.java:68-73`) counts frames: a peer plays a cushioned tick early after 60 frames without running dry. That is 0.42 s at 144 Hz and 2 s at 30 Hz. Time is otherwise in nanoseconds. | Reading `PeerSession.java:215-224`. Defect class 2 in its frame-count form. | Low-Medium: 144 Hz players drain their cushion 2.4× faster than designed, so they stall more often on a jittery link. |
| D5 | **A button added to `CommanderInput` would silently never cross the wire.** `Bundle.buttons` / `readHeld` list the six booleans by hand and reject bits above 63 (`Bundle.java:228, 237-247, 281-288`). Every peer, the host included, would lose it identically: no desync, and the feature just does nothing online. | Probe: 6 booleans on the record today, 6 on the wire. No test ties the two. | Low (latent). Class 6. |
| D6 | **No guard on `network.properties`.** `NetConfigLoader` reads each key with a literal fallback from `NetConfig.defaults()` and ignores unknown keys, so a typo in a playtest tweak (`net.session.leadMaxTick=40`) silently does nothing. `ConfigDefaultsTest.netConfigFallsBackToTheNetModulesOwnDefaults` looks like a drift test but compares the fallback with itself: `assets/` is on `lwjgl3`'s classpath only (`lwjgl3/build.gradle:21`), so core tests never read the file. | `NetConfigLoader.java:57-95`, `ConfigDefaultsTest.java:91-93`. All 27 keys compared by hand: **0 drift today**. | Low-Medium. The same pattern as `sim`'s A1, not yet bitten. |

## B. Performance

| # | Measured | Verdict |
|---|---|---|
| P1 | Allocation per 60 Hz frame, 2 machines, `Skirmish`: sequencer **378 B**; two peers including their worlds **3.1 KB**; the same world's step alone **0 B**. So about 1.5 KB per peer per frame is the session: loopback copies, a `CommanderInput` per changed held input, and the checksum snapshot (13 KB every 60 ticks, ~220 B/frame amortised). | Per frame, not per entity: fine (`performance.md`). No action. |
| P2 | Checksum on the game thread every `checksumTicks`: snapshot + hash **0.05-1.09 ms** (`Adventure`, 279 entities, 138 KB), about 2.2 steps' worth, once a second. | Fine now. Worth a look past ~1000 entities: then it is a once-a-second frame spike. |
| P3 | Bandwidth: a bundle is `8 + 18·seats` bytes, and every unacked one is resent every tick until acked. 8 seats at 90 ms one-way: ~1.2 KB × 60 = **72 KB/s per peer, ~500 KB/s host upload for 7 peers**, against ADR 0012's "a few tens of KB/s per peer". Held input rarely changes between ticks, yet each costs 17 bytes. | Coupled to D1. See the D1 fix options and X1. |
| P4 | Scans over peers (`peerWithId` per slot per seal: 8×8; `applyEvents` ≤ 32²; `host()`/`onLink` linear). | An explicitly bounded count (≤ 8 peers), said out loud. No action. |
| P5 | `NettyTransport`: a `byte[]` copy, an `Unpooled` heap buffer, a `DatagramPacket`, a record and a queue node per message; `new ArrayList(links.values())` per poll. | Per message at 60 Hz × links: tens of KB/s of young garbage. Suggestion only, no number: leave it. |
| P6 | Logging: nothing per message (the trace takes it); stats once per `statsSeconds`. | Clean. |

## C. Maintainability and extensibility

| # | Finding | Proposal |
|---|---|---|
| M1 | **God classes** (class 10): `Sequencer` 816, `PeerSession` 781, `NettyTransport` 660 lines. Each mixes 4-5 responsibilities. | Split verbatim, ~400 lines each, fingerprint unchanged (method below). `Sequencer` → `SequencerPeer` (the inner `Peer`, events), `HeldInputs` (`ahead`/`aheadTick`/`lastHeld`), `BundleHistory` (history + what goes in each datagram - D1 lands here), `ChecksumReferee` (host hashes, waiting, compare). `PeerSession` → `LeadControl` (`adaptLead`, calm, settle), `PlayPacer` (accumulator, cushion, catch-up), `BundleRing` (ring, contiguous), `OwnInput` (own ring, events, `sendInput`), `Keyframes` + dump. `NettyTransport` → `Link`/`State`, the Netty handlers + inbound records, the handshake constants. |
| M2 | **Two sources of truth for network tunables** (D6): the `NetConfig.defaults()` literals and the file, plus a hand loader line per key. | Reuse `ConfigBinder`: `NetConfig` bound from `network.properties` by component name, file compiled in the way `generateShippedConfig` does it, strict unknown/missing test. Needs `ConfigBinder`'s generic entry to be public (it is package-private in `sim`). `net.conditions.latencySteps` (core's) needs a home. Cheaper alternative: keep the loader, add a strict key-set test only. |
| M3 | **Coupled literals** (class 6): `Sequencer.AHEAD = 128` must exceed `leadMaxTicks`, which `NetConfig` caps at a literal `120` (`NetConfig.java:184`) - two files, no link. `PeerSession.OWN = 128`, `fit ≤ OWN/2`; `Peer` holds 32 events against the peer's 64-deep queue; `BIND_RETRY_NANOS`, `MOST_DUMPED`, `CALM_REPORTS` are tunables written as literals. | Derive `AHEAD`/`OWN` from `leadMaxTicks` (next power of two above `2·leadMax`), or `require` the relation in one place. Leave the rest: none is a playtest knob. |
| M4 | **The session tests never ship config text**: `SessionHarness` passes `""` (`SessionHarness.java:54`) and `TestWorlds` ignores it, so the "host ships its `game.properties`" path is covered only in `core` (`ScenarioWorldsTest`, `OnlineSessionTest`). | See X3. |
| M5 | Order-dependent pipelines (class 9): `Sequencer.pump` is poll → ask snapshots → seal → send; `PeerSession.pump` is poll → orders → step → send. | Checked: 4-5 calls in one method each, readable at a glance. A test-as-data would cost more than it holds. No action. |

## D. Architecture decisions, re-examined

**Keep**: host-relayed lockstep with the sequencer on its own thread (0012), the star over a mesh, TCP for
control and snapshots beside UDP for ticks, the polled port, held input + numbered events, shipping the
host's config. The first latency playtest already backs them.

| # | Alternative | Why it is better | Cost |
|---|---|---|---|
| X1 | **Bundles send each tick once, resend on a timer, and carry a little redundancy** (fixes D1, most of P3). The sequencer records when it last sent each history tick per peer. A datagram carries the newest `R` ticks (e.g. 3, so one loss costs nothing), then every unacked tick whose last send is older than that peer's measured round trip, oldest first. The round trip in ticks is free: the tick at which the ack for T arrives, minus the tick T was first sent. **The wire format does not change**: a peer already files bundles by their own tick, in any order (`PeerSession.bundles`). | Throughput is no longer capped by one datagram per round trip. Bandwidth drops from `window × bundle` to about `R × bundle` per tick. | M, all inside `Sequencer` (→ `BundleHistory` after M1). |
| X1b | Cheaper stop-gap: send as many datagrams per tick as the unacked window needs. | Fixes D1 in about 10 lines. | Bandwidth grows with the round trip: 8 seats at 120 ms one-way ≈ 146 KB/s per peer. |
| X2 | **Held input as "same as last tick"** (P3). One bit per present slot, and 17 bytes only when the input changed. Still lossless. | A bundle of 8 steady players drops from 152 to ~12 bytes. Leaves room for rollback later, which will resend input far more often. | M, wire change (the build id already forces both sides onto the same build). Measure after X1 first. |
| X3 | **`ScenarioWorlds` moves from `core` to `net`**, parsing with `SimConfig.parse` (already in `sim`) and logging problems to the `NetLog`. `TestWorlds` goes away and `SessionHarness` ships real config text. `WorldFactory`'s doc says "`net` cannot parse a config" - it can; what it lacks is `core`'s `Log`. | One `WorldFactory`, and the session tests cover the config-shipping path. | S. Optional. |
| X4 | **One `NetTrace` per transport** (fixes D2), with the transport's role on each dump line (`tr=seq` / `tr=peer`). | Correct, and a host's log becomes readable. | S. |

## E. LLM workflow efficiency

| # | Friction (seen this session) | Fix |
|---|---|---|
| E1 | The session-start bootstrap failed on Maven Central **429 Too Many Requests**; `verify.sh` failed twice more on 429 before passing on the third try (about 4 minutes lost). | Retry with backoff on 429 inside `.claude/hooks/session-start.sh` / `gradle.sh`. Friction for Mateusz: an allowlisted Maven mirror, or a committed `ai/gradle-home`, would remove it. |
| E2 | **Reading a friend's desync dump needs code**: `DesyncReport.compare` has no runner. `SessionTest` shows the call, but with `TestWorlds`, which ignores the host's config, so a playtest on tuned numbers would replay wrong. | An opt-in runner in `core` (it has `ScenarioWorlds`): `PWT_DESYNC=<dir> gradle.sh :core:test --tests '*DesyncReportRunner'` prints the report for every pair in the directory. One row in `workflow.md`. |
| E3 | Three 650-820 line files (M1): a session changing lead control reads 781 lines. | M1. |
| E4 | No refactor fingerprint for `net`: nothing records what a session sent, so a split can only be shown safe by "tests still pass". | `SessionTranscript` (test side): wraps each machine's transport and hashes every message sent and received, in order, with the final world hash. Recorded before a split, compared after. Listed in `workflow.md`. |
| E5 | No bandwidth/window guard: D1 was invisible to all 65 tests because every session test seats ≤ 3 players at ≤ 40 ms. | D1's regression test (8 seats × 60/90/120 ms, ~2 s) runs by default; the full table from the probe goes behind `PWT_NETBENCH=1`. |
| E6 | Comment weight: 15% of `net` lines (664 of 4445), against `sim`'s 39% before its sweep. Little history, but some: `PeerSession.adaptLead` ("the first playtest: 25 seconds at 27 ticks"). | Light sweep, only while splitting (M1). Not a stage of its own. |

**Stale documentation** (class 12): `NetTrace` "single-threaded" (false on the host, D2); `NetLog`
"(3.b of the online plan)" points at nothing (`NetLog.java:23`); `WorldFactory` "net cannot parse a config"
(X3); ADR 0012's "Known hazards for the snapshot" still names `Strike.owner`/`Damage.locksOnDeath` as
`Entity` fields - superseded by 0016, but the ADR index row for 0012 does not say so;
`architecture.md` "every few seconds" for stats that ship at 1 s.

**Enforcement narrower than the rule** (class 11): `onlyTheSystemClockReadsTheWallClock` bans
`nanoTime`/`currentTimeMillis` only. `Instant.now()`, `LocalDateTime.now()` (used legitimately at
`NetLog.java:74`) and `Thread.sleep` pass. Widen it to `java.time` `now()` and `sleep`, with `NetLog` (file
stamps) and `SequencerThread` (parking) named as the exceptions, and watch it fail on a probe.

---

## Plan: each stage ships on its own

| Stage | Contents | Size | Risk |
|---|---|---|---|
| **1 - before the Hamachi test** | D2/X4 trace per transport (test: two transports on one log keep separate traces) · D1 fix (**your choice**, X1 or X1b), tests first: 8 seats × 60/90/120 ms stay within round trip + buffer, no heal, identical worlds; plus the full `SessionTest` · D3 guard: an oversized snapshot is logged and the drop-in waits, instead of an exception · E2 desync runner | M | D1 touches what every peer receives. `SessionTest` (lag, loss, drop-in, desync heal) is the net under it. |
| **2 - guards** | D5 test: every `CommanderInput` boolean round-trips through `Bundle` (seen failing with a 7th button) · D4 cushion drain in nanoseconds, not frames · M3 derive `AHEAD`/`OWN` from `leadMaxTicks` · class-11 widening of the wall-clock rule · stale docs above | S | D4 changes pacing at non-60 Hz frame rates, and only there. |
| **3 - one config source** | M2 (**your choice**: `ConfigBinder` or strict key test only) | S-M | `ConfigBinder` becomes public API in `sim`. |
| **4 - splits** | E4 transcript first, then M1 × 3, verbatim moves, transcript + world hash identical per commit; comment sweep inside the moved code | M | Pure refactor. |
| **5 - optional** | X2 delta held input (measure X1 first) · X3 `ScenarioWorlds` into `net` | M / S | X2 is a wire change. |
| **Workflow, any time** | E1 429 retry · E5 bench row in `workflow.md` | XS | none |

Keys stage 1 would add, if X1: `net.session.redundantTicks=3` (how many newest ticks every bundle datagram
repeats; higher survives more loss, costs `bundle × n` per tick). None moved.

## Decisions I need from you

1. **Before tonight**: stage 1 first, or leave `net` untouched until after the playtest? (How many
   machines and seats you expect decides how much D1 matters: two machines with one seat each never
   hit it.)
2. **D1 fix**: X1 (timer resend + redundancy, recommended), or X1b (more datagrams per tick, smallest)?
3. **M2**: `network.properties` through `ConfigBinder`, or a strict key test on the hand loader?
4. **Stage 4 splits**: do them, or record them in `docs/backlog.md`?
