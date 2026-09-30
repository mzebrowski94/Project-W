# New systems (upgrades, wounds, army reserve, crates) - critical review and staged plan

## Decisions (2026-09-30)

Wound clocks: all three in whole ticks · `graphic.properties` and `controls.properties` bound through
`ConfigBinder` · invariants split per domain now · optional items all in: delete `Effect.Deal` (V7), the
plan-ID scan test (W3), one-loop `Modifiers` (M1). S8's two-copy check and S9 were already played by
Mateusz, so nothing about them goes into `alpha-phase.md`.

### Stage 2 (docs)

| Item | Done | Evidence |
|---|---|---|
| W1 | The four scope docs deleted (2,119 lines, ~37k tokens); CLAUDE.md's two rows for them removed | `SourceCitationsTest.namesOnlyDocsThatExist` green; seen failing on a deliberate `docs/design/upgrades-scope.md` in the glossary, then restored |
| V8 / W3 | Every plan ID in a comment rewritten to the record that holds the decision: D1, D2, D12, D18 → G0049; D4, S8 → G0052; `(B)` → G0056; D11 → ADR 0017; the wire (S8) → ADR 0012; H3 and D6/D7 dropped (the reason is already beside the number). **Changed from the plan:** the D1-D19 table is not moved into G0049 - with every citation pointing at a record, nobody would read it, and each of D1-D19 is already in G0049, G0052, G0056, ADR 0017, the glossary or `upgrades.properties`. W3's rule and test moved here from stage 8, since they guard exactly this change | `SourceCitationsTest.citesNoScopeDocsNumbersInAComment` failed first on 57 citations, then green; rule in `conventions.md` |
| - | Comment-only | Stripped-code compare against `bf9af72`: **38 of 38 `.java` / `.properties` files identical** (stripper checked to catch a real code change, `8bb389c`'s `UpgradeSystem`) |
| - | Doc pointers into the deleted docs fixed: ADR 0017 (enforced-by names its rules; "until those land" gone), G0006, G0018, G0020, G0023, G0049, G0052, G0053, G0056, glossary, README (3), `menu_layout_agent.md` (3), `alpha-phase.md`; `Spawner`'s sealed-cache comment | same test |
| - | Backlog: `Modifiers` cache (P1), `Msg.PROTOCOL` (V10), power budget per upgrade (from the deleted plan's §10), file sizes updated | - |
| - | **Withdrawn**: V8's "G0050's index row not marked superseded" - it is, in its last column | - |
| - | `Stat`/`Effect` sealed-cache docs left for stage 6, which deletes both | - |

### Stage 3 (guards)

| Item | Done | Evidence |
|---|---|---|
| V2 | `SystemOrderTest` finds the ledger's readers - every registered system whose constructor takes `Modifiers` - instead of a list: 15 rows, was 12 (`DamageSystem`, `CrateSystem`, `CasualtySystem` added); `simulation-order.md` says so | With `CrateSystem` moved after `UpgradeSystem`: the old list's 72 constraints **green**, the derived 75 **red** on "CrateSystem before UpgradeSystem"; restored |
| V3 | `CommanderControlSystem` sizes its per-machine groups from the capacity it is handed, and throws instead of skipping a group it has no room for (unreachable now: one commander per slot) | `CoopTest.holdsTogetherTheCommandersAtOneMachineInAGameOfMoreMachinesThanTheCeiling` red first (**98 apart, leash 20**), then green. New `CeilingTest`: `Players.MAX` read in code only where a capacity is chosen (`Players`, `PlayerInputs`, `Simulation`) - a source scan, because javac inlines the constant and ArchUnit cannot see it; red first on `CommanderControlSystem.java:88-97` |
| V9 | `Dealer.requireFits`, called by `UpgradeDef.checkCatalogue`: a 65th upgrade kind fails the catalogue at load. `TopBarPainter.crateKey` writes its key in base 2 × pickup kinds instead of 64, so the 32-kind limit is gone rather than guarded | `DealerTest.refusesMoreKindsThanAPassOverCanName`; `TopBarPainterTest.givesEveryCrateItCanShowAKeyOfItsOwn` (245,411 crates, 245,411 keys). Both seen red on a deliberate break (limit doubled; base 16: 97,865 keys), then restored |
| - | Behaviour unchanged | Fingerprint (12 scenarios × 1200 ticks of `ScriptedPlay` + a 300 crowd, world hash and bodies hash) **identical** to `4a0f60d`; `PWT_FUZZ=1 DeterminismFuzzTest` 12/12 green (182 s) |

### Stage 4 (wound clocks)

Decision: all three clocks in whole ticks (asked 2026-09-30).

| Item | Done | Evidence |
|---|---|---|
| V1 / A2 | `Vitality` counts both clocks in hundredths of a tick (`Vitality.TICK`): `bleedOutTicks`, `stabiliseTicks`, `hauledStabiliseTicks` converted once at spawn through `Ticks.of`; `remaining` and `stabilised` are ints. A tick of work is `STABILISE_SPEED` folded onto 100 (`Modifiers.stabiliseWork` is now whole); a dragged bleed is `draggedBleedFactor` × 100, converted once in `CasualtySystem`. The mend compares its tick count with `Ticks.of(seconds)` instead of multiplying by `deltaTime` | New `WoundClockTest`, on the shipped config: stabilise **48 ticks (was 49)**, First Aid II **32 (was 33)** - both red first; hauled 77, bleed-out 480 / 300, mend 600 - green before and after, kept as guards. `conventions.md`'s timer row names the rule and the test |
| - | **Behaviour change**: a stabilise ends one tick sooner wherever the float sum used to fall short (the shipped base, First Aid II); every other clock is unchanged | Bodies hash (positions, health, wounds, down) **identical in all 12 scenarios and the crowd**: no scripted run finishes a stabilise on the boundary tick. World hash differs in every world with a man in it, from the snapshot layout (five `Vitality` fields changed type or name) - `Crowd` has none and is identical. `PWT_FUZZ=1 DeterminismFuzzTest` 12/12 green (197 s) |

### Stage 5 (stored fold, crate order)

| Item | Done | Evidence |
|---|---|---|
| V5 / A3 | `Vitality.carries` is gone. `DamageSystem.carries(modifiers, man)` - the one caller of `Modifiers.maxWounds` - folds at the hit, and `Simulation.maxWounds(man)` hands the panel and the debug overlay the same method, as `squadMost()` already did. `Vitality.take(harm, carries)` weighs against what it is handed | `ReadoutWriterTest.publishesTheMaximumHisUpgradesGiveHimFromTheFrameHeArrives` red first (**1, expected 2** with Thick Skin), then green; fence 3 still one caller |
| V4 / A4 | `ActionSystem` hears every commander, then opens crates in slot order (a scratch array sized from capacity); `Crates` lost its same-tick steal, which slot order makes unnecessary | `CrateTest.givesTheHigherSlotTheNextCrateWhenTheLowerTakesTheNearestOnTheSameTick`, both spawn orders: red first on the higher-first order (**slot 1 got nothing**), then green; the existing lower-slot test still green |
| - | Behaviour unchanged apart from the two fixes | Bodies hash **identical** in all 12 scenarios and the crowd; world hash differs only where a man's `Vitality` lost its field. `PWT_FUZZ=1 DeterminismFuzzTest` 12/12 green |

### Stage 6 (one loop; the full ledger measured)

Decision: `Effect.Deal`, `Stat.ARMOURY_CARDS` and the deck reshuffle **stay** - kept for features to come (2026-09-30).

| Item | Done | Evidence |
|---|---|---|
| M1 | `Modifiers`' five whole-number loops (`percent`, `amount` ×2, `dealt`, `stocked`) are one `sum` over the ledger and one `share` - a switch over the sealed `Effect` that says what each rank of each kind adds to a stat for a reader. A new kind of effect does not compile until it says. The chance keeps its own loop: a rate per second, not a whole number. 263 → 211 lines | Throwaway probe: the old class kept beside the new one, every public read compared on every entity of three worlds (Adventure, Adventure 2, Casualty, 600 ticks each) at ranks 0-3, personal ranks for two slots: **69,600 reads, all identical**. Fingerprint (world + bodies, 13 worlds) **identical** to stage 5 |
| P1 | `SimulationBenchmarkTest` measures the fold with every upgrade and adaptation at its top rank, without stepping (so what the upgrades change about the fight is not counted as their cost): ns and bytes per read over the 800 crowd, a tenth of it spitters. Two new guards: the fold under 10% of the 800 step at two reads per entity, and no bytes per read. `performance.md`, `workflow.md`, backlog updated | Before/after, alternating runs: old **105 / 120 / 132 ns** a read, new **204 / 127 / 120** (the 204 a cold first run) - the same within noise; 0 bytes; ~4-5% of a 4.0-5.5 ms step. 800 line: 4.0-5.5 ms, 6 B/step, growth ×6.0 (100→800) |

Scope: `cebbac5..8bb389c` (8 commits, ~19k lines added). Baseline on this branch: `verify.sh` green, 1881
tests; `PWT_BENCH=1` 800 crowd **5.24 ms/step**, growth ×7.8 (100→800), 6 B/step (cloud container; the
2026-09-27 figure of 3.89 ms was Mateusz's machine). Every probe below was a `Zz*ProbeTest`, run and
deleted, never committed.

## What is already good

- The ledger and the fold at the reader (ADR 0017) hold up: fence 3 (one `@Reads` method, one caller per
  `Stat`), a catalogue check that rejects lying keys, cycles, lone groups and a deck a `Bag` cannot hold
  (`UpgradeDef.checkCatalogue`), and a pick that names the kind, not the card position.
- Allocation is where the rules allow it: `UpgradeSystem` allocates once per pick, `ReinforcementSystem` /
  `RespawnSystem` once per arrival; the readout allocates nothing (P2).
- Picks and crate events are held until the next tick runs (`ScenarioSession.pendingCrateTake`), so no
  input edge is lost between frames; the muster is a deadline (`Level.musterTick`), not a float.
- `Presence`, the drafts and the ledger live on the level entity, so drop-in and heal carry them free.

## A. Verified defects

| # | Finding | Evidence | Severity |
|---|---|---|---|
| V1 | **Stabilise runs one tick long** - the 71-durations bug class again. `Vitality.stabilised` accumulates float seconds (`Aid.java:81`) against `stabiliseSeconds` (`Vitality.java:164-181`). It was not in the sim review's "deliberately left as floats" list (bleed-out was). The bleed-out (`CasualtySystem.java:65-69`) and the new mend (`:87-89`, `mendTicks * deltaTime >= seconds`, converting per tick instead of once) are the same shape, exact today by luck of the numbers. | Probe through `Aid.work` with the shipped config: **0.8 s = 49 ticks, `Ticks.of` = 48.** Arithmetic over every case: First Aid II (+50 %) **33 vs 32**; First Aid I, hauled (×1.6): exact. Bleed-out 8 s / 5 s: 480 / 300, exact. Mend 1-60 s: exact. | **Medium**: every unhurried stabilise, the most common rescue, is 17 ms late; any retune can move another one. |
| V2 | **`SystemOrderTest` enforces "every reader sees the same ledger" for a hand-written list** (`SystemOrderTest.java:241-255`) that misses three of the fifteen systems handed `Modifiers`: `DamageSystem`, `CrateSystem`, `CasualtySystem`. Enforcement narrower than the rule. | Probe: every registered system whose constructor takes `Modifiers` → 15, all before `UpgradeSystem`@46 today; 3 absent from the list. Moving any of the 3 after `UpgradeSystem` keeps the test green. | Medium (latent) |
| V3 | **The leash drops every machine past the eighth, silently.** `CommanderControlSystem` sizes its per-machine groups by `Players.MAX` and `continue`s when full (`:88-97, 161-163`). Pillar 7: sized from capacity, never a literal. `CoopTest`'s larger-than-the-ceiling test does not reach it. | Probe, capacity 12, eight single-seat machines + a pair at the ninth walking apart: **98 units apart, leash allows 20.** | Low (capacity ships at 8), but the failure is silent |
| V4 | **Who gets a crate depends on entity order**, contrary to `Crates.open`'s doc ("never depends on the order the commanders happen to be held in", `Crates.java:67-74`). A lower slot steals a crate opened that tick, but the displaced higher slot is not re-offered the other crate in reach. | Probe, two commanders, two crates in reach of both, same-tick press: spawned 0,1 → **A:0, B:1**; spawned 1,0 → **A:0, B: nobody**. Deterministic on every machine (no desync), but a press is lost and a respawn reorders commanders. | Low |
| V5 | **`Vitality.carries` stores the `MAX_WOUNDS` fold** (`DamageSystem.java:101-105`), against ADR 0017 ("Nothing stores the result") and its do-not-build "per-entity current-stats component". A man spawned after `DamageSystem` holds the unfolded base until next tick, and the readout copies it (`ReadoutWriter.java:207`). It is also a derived value in every snapshot and hash. | Probe, Thick Skin I held: commander before death `carries=2 fold=2`; **tick of return `carries=1 fold=2`**; next tick 2/2. His panel shows one pip too few for a frame. | Low |
| V6 | **`graphic.properties` still has two sources of truth**: 114 literal defaults in `GraphicConfig`, **2 already drifted** - `graphic.tile.patch` code 3 / file 5 (`:291`), `graphic.terrain.rockEdge` code `7a7a98ff` / file `8a8aa8ff` (`:322`). These commits added 15 more (shield, crate, wound marks, wound colour). `ControlsConfig`: 11 literal defaults, 0 drift. `network.properties` is already bound (a8ba90f). | Script over every `get*("key", literal)` against the file. | Low-Medium: a missing or misspelt key falls back silently to a number nobody tuned |
| V7 | **Dead mechanism: `Effect.Deal`, `Stat.ARMOURY_CARDS`, `Modifiers.armouryCards`, `Armoury.shuffledAt`'s reshuffle.** Since G0056, Firepower uses `stock`; no shipped upgrade deals (`upgrades.properties:197`). Fence 3 keeps the stat alive because `CrateSystem.card` still calls it. Their docs describe the superseded sealed cache (`Stat.java:34-38`, `Effect.java:62-69`, `Spawner.java:777`, `upgrades.properties:60`). | grep: `deal` appears only in tests and comments. | Low (maintainability) |
| V8 | **Code cites plan IDs that live only in expiring docs**: about 50 `D1`-`D19` / `S5` / `S8` / `health-scope H3` references in `sim`, `core`, `ui`, `net`, `assets`; 8 × `(B)` in 6 files (`Presence`, `CommanderControlSystem`, `PlayerInputs`, `Simulation`, `Bundle`, `Sequencer`) that resolve to nothing in the repo - G0055's row B is a different rule. Also stale: ADR 0017 "Until those land: nothing mechanical" (`:15`) and its pointer to `upgrades-scope §5.1` (`:8`); G0050's index row not marked superseded. | grep | Low, but it grows with every scope doc |
| V9 | **Latent capacity, no guard**: the anti-streak mask `1L << kind.ordinal()` (`UpgradeSystem.java:322`, `Dealer.java:206`) aliases the 65th `UpgradeKind` onto the 1st; `TopBarPainter.crateKey` (`:323`) packs `ordinal*2` in base 64, so a 33rd `PickupKind` collides and a crate card shows stale text. 13 and 11 today. | Reading | Low (latent) |
| V10 | `Msg.PROTOCOL` stayed 3 when `Bundle`'s layout changed in `77d5687` (crate events). Harmless: `BuildId` refuses any other build first. So `PROTOCOL` is a number nobody maintains. | `git show` per commit | Low → backlog |

## B. Performance

| # | Measured | Verdict |
|---|---|---|
| P1 | The fold with a **full ledger** (all 13 kinds at top rank) vs empty, 800 crowd, 4 interleaved rounds: best **4.49 vs 5.14 ms**, spread 4.5-7.4 either way; 7 B/step both. The benchmark's crowd holds nothing, so `Modifiers` returns on its first line there and the fold is never measured. | Fold is free at this scale: **no compiled cache** (ADR 0017's `@NotState` cache stays unbuilt). Add a full-ledger 800 crowd to `SimulationBenchmarkTest` so a regression shows. |
| P2 | `ReadoutWriter.fill` after 1800 scripted ticks: **Adventure 3.6 µs, 0 B/frame; Adventure 2 2.6 µs, 0 B.** | Fine. |
| P3 | `core` painters (`TopBarPainter`, `HeldUpgradesPainter`, `BottomBarPainter`) rebuild text only on a revision or content key; per frame they call `TextFit.fit` on the aside's lines. | Fine; unmeasured (needs GL), a suggestion at most. |
| P4 | `MAX_WOUNDS` folded for every man every tick; `SPEED` for every driven unit. | Inside P1: free. |

## C. Maintainability and extensibility

| # | Finding | Proposal |
|---|---|---|
| M1 | `Modifiers` repeats one triple loop six times (`percent`, `amount` ×2, `dealt`, `stocked`, `chance`), 263 lines. A new effect type means finding every loop. | One private sum over the ledger with a switch on the sealed `Effect`: the one place an effect is decided. ~150 lines. Behaviour-identical. |
| M2 | Past the backlog's numbers: `Spawner` 1089→1152, `SimConfig` 980→1154, `AlphaWorlds` 1623→1869, `Sequencer` 961→1078 (backlog says ~870), `PeerSession` 870. | Already deferred: update the numbers, no new proposal. |
| M3 | Comment weight: `sim` 30 % (the sweep left 31 %), files added here 26 %, ~28 history lines. | Fine. No sweep. |
| M4 | `Crate` holds four named fields and a switch; the capacity is one constant (`CrateConfig.CAPACITY`, reused by `PlayerReadout.CRATE_CARDS`). | Fine at four. |

## D. Architecture decisions, re-examined

| # | Decision | Verdict |
|---|---|---|
| A1 | Ledger on the level entity, folded at the one reader (ADR 0017). | Right, and measured free (P1). Keep. |
| A2 | `Vitality` keeps three float clocks (stabilise, bleed-out, mend) because two of their rates vary. | **Better: whole ticks with the rate as a whole percent** - need = `Ticks.of(seconds)` × 100, each tick adds the folded percent (100 base; `Fold.scaleWhole` for `STABILISE_SPEED`; `draggedBleedFactor` as a percent, 0 stops). Exact, converts once, and matches G0054's whole-number direction. Fixes V1. |
| A3 | Stored fold `Vitality.carries` (V5). | Fold where the hit is weighed, as every other stat; the panel asks `Simulation.maxWounds(entity)` through the same single caller, as `squadMost()` already does. |
| A4 | Crate opens resolved as each commander is processed (V4). | Two phases in `ActionSystem`: collect each slot's open press into a scratch array sized from capacity, then grant in slot order. The doc's promise becomes true. |
| A5 | `Effect.Deal` kept "for later" (V7). | Delete. A deal is a line of config plus a record when content asks for it; today it is untested-in-play code with wrong docs. |
| A6 | `GraphicConfig` / `ControlsConfig` hand-written (V6). | `ConfigBinder` + a compiled-in shipped file + strict test, as `network.properties` already is. |
| A7 | The army reserve: muster as a deadline, sizes from a `Bag`, one rally point function. | Good. |

## E. LLM workflow efficiency

| # | Finding | Proposal |
|---|---|---|
| W1 | CLAUDE.md sends every upgrade and wounds session to ~32k tokens of scope docs that expired (below). | Delete all four (verdict below). |
| W2 | **`docs/design/README.md` is 112 KB (~28k tokens)**, 96 KB of it one invariants table (315 rows, 258 `enforced`), and the `feature` skill reads it whole for every gameplay change. The biggest recurring token cost in the repo. | Split the table into per-domain files (`docs/design/invariants/<domain>.md`) with a one-line index; `feature` reads only the domains a change touches. Estimated 6-9k tokens per session instead of 28k. Proof: the multiset of rows before = after. |
| W3 | Plan IDs in code (V8) dangle as soon as a scope doc goes. | Rule in `conventions.md`: code cites only G and ADR records. A source-scan test fails on `(D12)`, `(S8)`, `(B)`, `health-scope`, `upgrades S5` in `src/main` - shown to bite. |
| W4 | Friction: this session was linked to your folder and the repo was cloned mid-session, so the SessionStart provisioning did not run; first `verify.sh` 1 m 32 s. | Once per session; no action. |

## The four scope docs (the additional task)

All four have met their own expiry condition. Nothing in them is load-bearing except what is moved below.

| Doc | Size | Status | What still matters, and where it goes | Verdict |
|---|---|---|---|---|
| `squad-and-objectives-scope.md` | 236 lines, ~4.2k tok | Says "done 2026-09-10, safe to delete"; content already in README, glossary, alpha-phase, G0015-G0017. No references. | nothing | **Delete** |
| `map-and-view-scope.md` | 83 lines, ~1.3k tok | Stages 1-5 done; stage 6 is future work. Both "found while building" items are settled (README route invariant, flow field). No references. | nothing | **Delete** |
| `health-scope.md` | 430 lines, ~7.9k tok | H0-H5 done; expires "when H4 ships". H3's rationale already sits beside `sim.swarm.healthPercent`; rules in G0053/G0054; keys in the files. | Pointers to fix: CLAUDE.md row, `SimConfig.java:149,1036`, `SwarmHealthTest:20`, `units.properties:6`, README :471 :476, G0006, G0018, G0020, G0023, G0053 | **Delete** |
| `upgrades-scope.md` | 1370 lines, ~23.8k tok | S0-S10 done; expires "when adaptations ship (S10)". Architecture = ADR 0017 + code; guards = README rows; S11's questions already in README's open questions. | The D1-D19 table (cited ~50 times in code, properties, i18n) → an appendix of G0049, so every citation resolves. §10's power-budget deferral → backlog. Pointers: CLAUDE.md row, ADR 0017, glossary :220 | **Delete** |

~37k tokens out of `docs/design`.

## Plan: each stage ships on its own

Proof methods as in the sim review: world fingerprint = `WorldCodec.hash` of every scenario (`Scenarios.all()`,
`ScriptedPlay.input`, 1200 ticks) + a 300 crowd; positions and health instead where the snapshot layout
changes; `PWT_FUZZ=1 DeterminismFuzzTest` after any sim change; stripped-code compare for comment-only files.

| Stage | Items | Proof |
|---|---|---|
| 2 - Docs | Delete the four scope docs; D1-D19 → G0049 appendix; fix every pointer listed; `(B)` → the record that settled the leash per machine (G0056); `Stat`/`Effect` sealed-cache docs; ADR 0017 stale lines; G0050 row marked superseded; backlog: power budget, updated file sizes, V10 | Stripped-code compare on every `.java` touched; fingerprint identical; verify green |
| 3 - Guards | V2: `SystemOrderTest` derives the readers from constructors. V3: ArchUnit "nothing in `sim.system` reads `Players.MAX`" → `CommanderControlSystem` sized from capacity; the probe becomes a `CoopTest`. V9: catalogue check throws past 64 upgrade kinds / 32 pickup kinds | Each guard seen failing on a deliberate break, then restored; fingerprint identical (capacity 8) |
| 4 - Wound clocks | V1 via A2: stabilise, bleed-out and mend in whole ticks. Tests first: 48 ticks at 0.8 s, 32 with First Aid II, 77 hauled, 480/300 bleed-out, dragged stops the clock, mend | **Behaviour change**: one tick shorter exactly where the float ran long; the hash change listed per scenario; fuzz green |
| 5 - Stored fold, crate order | V5 via A3; V4 via A4. Tests first: the panel on the tick of return shows the folded pips; two crates, both spawn orders, same owners | Positions + health fingerprint identical (snapshot loses `carries`) |
| 6 - Dead code, one loop | V7 via A5; M1; P1's full-ledger bench crowd | Positions + health fingerprint identical; bench before/after |
| 7 - Config | V6 via A6: `GraphicConfig`, `ControlsConfig` bound; drift fixed to the file's values (what ships). Throwaway guard: old loader output = bound record, field by field | `ScreenLayout.solve` output identical; keys listed |
| 8 - Invariants split | W2 (W3 done in stage 2) | Row multiset identical (script); `feature` skill reads the index |

Deferred to `docs/backlog.md` in stage 2: V10 (bring back when builds from different commits must play
together); a compiled `Modifiers` cache (bring back when the full-ledger crowd shows the fold above 10 % of a
step).

## Decisions I need from you

1. Wound clocks (V1): all three to whole ticks, stabilise only, or leave floats and document?
2. `graphic.properties` / `controls.properties` (V6): bind now, fix the two drifts only, or defer?
3. README invariants (W2): split per domain now, or backlog?
4. Optional items: delete `Effect.Deal` (V7), the plan-ID scan test (W3), the one-loop `Modifiers` (M1).
