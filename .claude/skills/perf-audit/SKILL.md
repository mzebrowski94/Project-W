---
name: perf-audit
description: Audit a Project-W hot path for allocation, boxing, logging and other per-frame costs. Use when reviewing a system for performance, when frame time regresses, or before merging code that runs per entity per frame.
---

# Hot-path audit

The hot path is anything that runs **per entity** per frame: the body of every
`IteratingSystem.processEntity`, the loops inside an `EntitySystem.update`, the renderer's draw loop,
and everything they call. Work that happens once per system per tick is not the hot path.

Read `docs/conventions.md` first - this skill applies those rules, it does not replace them.

**Before reporting anything, know what it is worth.** The rule now requires a measurement for any
optimization beyond the standing conventions, and that applies to this audit's findings too. A
finding with no number attached is a suggestion, and should say so.

## What to look for, in order of how much it costs

**Allocation, inside a per-entity loop.** Look for:

- `new` anywhere in the update path, including implicit ones
- `Vector2` and `Color` created per entity instead of reused scratch instances
- string concatenation, including inside a disabled log call - the concatenation still happens
- boxing: `Integer`, `Float`, `Optional`, generic collections of primitives, varargs
- lambdas or method references that capture - each capture is an allocation
- iterators: enhanced-`for` over a `List` allocates; over a libGDX `Array` or an indexed loop it
  does not
- `engine.getEntitiesFor(...)` called per frame instead of cached once

**Logging.** Any log call in a per-frame or per-entity loop is a defect, at any level.

**Repeated lookups.** `ComponentMapper.get` is cheap but not free; `entity.getComponent(X.class)` is
much worse. Mappers belong in `private static final` fields.

**Work that does not change per frame.** Config reads, `Math` calls on constants, trigonometry that
could be a lookup, anything derivable at spawn time and stored in a component.

**Determinism, but only in `core`.** `ArchitectureTest` already fails the build for wall-clock
reads, `Math.random`, `java.util.Random` and hash iteration inside `sim`, so do not re-check those
there. The client module has no such guard, and rendering code that feeds a value back into the
simulation is the failure worth looking for.

## How to report

For each finding: the file and line, what it costs (an allocation per entity per frame, a lookup per
entity), and the concrete fix. Order by cost, not by file.

Say plainly when something is fine. An audit that manufactures findings to look thorough wastes more
time than it saves - and in a 60-entity scene most of this list genuinely does not matter yet. Note
when a finding is theoretical at current scale, and prefer leaving it alone: readable code that is
fast enough beats fast code nobody wants to change. An allocation outside a per-entity loop is not a
finding at all.

Do not change behaviour to make something faster without saying so explicitly.

## Verify

Any change from an audit must keep `.claude/scripts/verify.sh` green, and must not change what
`SimulationDeterminismTest` produces.
