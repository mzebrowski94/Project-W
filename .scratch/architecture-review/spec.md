# Architecture review, 2026-09-30

Deepening opportunities found by `/mattpocock-skills:improve-codebase-architecture`: places where a
module is shallow (its interface nearly as complex as its implementation), so a change or a bug spreads
across callers instead of living in one place. Vocabulary: `/mattpocock-skills:codebase-design`
(module, interface, depth, seam, adapter, leverage, locality). Domain words: `docs/glossary.md`.

**Scope.** Hot spots of the last 150 commits: `Simulation`, `Spawner`, `SimConfig`, `CollisionSystem`,
`DamageSystem`, `GraphicConfig`, `SimConfigLoader`, `ScenarioScreen`, `ScenarioSession`.

**Left out on purpose** - already deferred in `docs/backlog.md` with their triggers: definitions copied
into components, presentation in the snapshot, `Simulation`'s surface, the `Trooper` split, oversized
files, one shared neighbour index, a cache in front of `Modifiers`, the `net` class splits. Nothing here
contradicts an ADR's *Do not build* column; where a ticket runs close to one, it says which.

**Done.** The tick clock (report candidate 2): three float-second timers became whole ticks, and
`ArchitectureTest.timeIsCountedInWholeTicks` refuses a new one. The trigger pull (02): one `Guns` per simulation, one muzzle gap in config, and the boss's barrage
through both.

**Evidence** was gathered on 2026-09-30; file and line numbers drift, so re-read before acting. Every
ticket is `needs-triage`: each needs a design conversation (`/mattpocock-skills:grilling`) before it is
ready for an agent. Start with 01.

| # | Ticket | Strength |
|---|---|---|
| 01 | [One blow module owning `Damage`](issues/01-blow-module.md) | Strong - likely live bug |
| 02 | [Deepen the trigger pull](issues/02-trigger-pull.md) | Strong |
| 03 | [One config front door](issues/03-config-front-door.md) | Strong |
| 04 | [Let the binder convert; drop the mirror records](issues/04-binder-conversions.md) | Strong |
| 05 | [One queue for held-back input events](issues/05-held-input-events.md) | Strong - likely live bug |
| 06 | [Local seats and top bar out of `ScenarioScreen`](issues/06-local-seats.md) | Strong |
| 07 | [Odds that keep their own due](issues/07-odds.md) | Worth exploring - done |
| 08 | [One question for a man's life stage](issues/08-life-stage.md) | Worth exploring |
| 09 | [Dev overlay reads the readout](issues/09-overlay-reads-readout.md) | Worth exploring |
| 10 | [One nearest-interactable query](issues/10-nearest-interactable.md) | Speculative |
