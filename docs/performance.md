# Performance

The rule is about **per-entity** work, not per-frame work, and the distinction is the whole point.

The hot path is anything running once per entity per tick: the body of every `IteratingSystem.processEntity`,
the loops inside an `EntitySystem.update`, the renderer's draw loop, and everything they call. Work that
happens once per system per tick is not the hot path.

## Inside a per-entity loop

- **No allocation.** No `new`, boxing, varargs, `Optional`, string concatenation - including inside a
  disabled log call, where the concatenation still happens. A lambda or method reference that captures
  allocates too.
- Classic `for`, or enhanced-`for` over a libGDX `Array`. No streams, no iterator churn - enhanced-`for`
  over a `List` allocates.
- Cache the `ImmutableArray` from `engine.getEntitiesFor(...)` once, in the constructor or
  `addedToEngine`. Ashley keeps the view live; re-querying per frame is waste.
- Cache `ComponentMapper`s in `private static final` fields. `ComponentMapper.get` is cheap but not
  free; `entity.getComponent(X.class)` is far worse.
- **Never log.** Any level.
- Nothing derivable earlier: config reads, `Math` on constants, trigonometry that could be resolved at
  spawn and stored in a component.

## No O(N²) entity pairs

A nested loop over two entity collections does not scale and is the classic way a shooter dies at 200
projectiles. Go through `SpatialGrid`, which is a dense grid with no hashing and therefore no iteration
order that could vary between runs.

The exception is an explicitly bounded count with a measured cost, said out loud.

## Pooling

Transient gameplay entities - projectiles, strikes, hit effects, enemies - use the established pooled
`Component` path. Do not introduce a new allocation-heavy transient type in a simulation hot path.

## Per-tick allocation is fine

Once per system per tick rather than once per entity. A short-lived object dies in the young generation
and costs very little on a desktop JVM, and clarity is worth more. `CommanderInput`, built once a frame,
is the model.

Streams, lambdas and allocation are all fine in setup, loading, scenarios, tooling and tests.

## Anything beyond these rules needs a measurement

Not an argument, not an instinct: before and after numbers, said out loud in the change that introduces
it.

The numbers come from `SimulationBenchmarkTest` (`PWT_BENCH=1`, about 25 s): ms and bytes per step for
crowds of 100 to 800 grunts. 2026-09-27: 0.43 / 0.65 / 1.56 / 3.89 ms (before the avoidance grid, 0.71 /
1.92 / 6.60 / 28.9) and tens of bytes per step in all. It fails on four things, the last three independent
of the machine:

- the 800 crowd no longer fits one tick;
- 8× the crowd costs more than 13× the time - an entity-pair loop (a light one measured 16-17×);
- a step allocates 2 bytes or more per entity - an allocation inside a per-entity loop (one `new float[4]`
  measured 32);
- with every upgrade and adaptation held at its top rank, the fold (`Modifiers`) would take a tenth of the
  800 step at three reads per entity, or a read allocates. 2026-09-30, uncached: 105-205 ns a read. 2026-11-11,
  18 kinds held: 106 ns uncached (12% at three reads); 24 ns with the sums kept per kind of reader, about 3%.

Run it after any change to a system, the solver or `SpatialGrid`, and quote the 800 line in the summary. A
time failure within a hair of the line: rerun once; twice over is a regression.

This is deliberate. The game is a few hundred circles on hardware that draws millions, and the previous
version of this rule - zero allocation anywhere in an update path - cost readability twice without
anyone ever timing what it saved. An optimization nobody measured is a guess with a maintenance bill.

## Auditing a hot path

`perf-audit` is the skill. It applies this file; it does not replace it. Order findings by cost, not by
file: allocation first, then logging, then repeated lookups, then work that does not change per frame.

Say plainly when something is fine. In a 60-entity scene most of this list genuinely does not matter
yet. Note when a finding is theoretical at current scale and prefer leaving it alone - readable code
that is fast enough beats fast code nobody wants to change. An allocation outside a per-entity loop is
not a finding at all.

A finding with no number attached is a suggestion, and should say so. Never change behaviour to make
something faster without saying so explicitly, and never at the cost of what
`SimulationDeterminismTest` produces.
