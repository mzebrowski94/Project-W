---
name: perf-audit
description: Audit a Project-W hot path for allocation, boxing, logging and other per-frame costs. Use when reviewing a system for performance, when frame time regresses, or before merging code that runs per entity per frame.
---

# Hot-path audit

**Read `docs/performance.md` first.** It defines the hot path, the rules, and what counts as a finding.
This skill is the procedure for applying it to a specific file; it does not restate it.

## Procedure

1. **Establish the loop boundary.** Which lines run once per entity per tick, and which once per system
   per tick. Everything downstream depends on drawing this line correctly, and it is the mistake most
   audits make.
2. **Walk the per-entity body**, including everything it calls, against `docs/performance.md`'s list -
   allocation, then logging, then repeated lookups, then work that does not change per frame. In that
   order, because that is the order of what it costs.
3. **Price each finding.** Allocations per entity per frame, lookups per entity. A finding with no
   number is a suggestion and must say so.
4. **Report**, ordered by cost rather than by file: file and line, what it costs, the concrete fix.
5. **Verify any change** with `.claude/scripts/verify.sh`, and confirm `SimulationDeterminismTest`
   produces what it produced before.

## What makes an audit worth reading

Say plainly when something is fine. An audit that manufactures findings to look thorough wastes more
time than it saves.

Note when a finding is theoretical at current scale - a 60-entity scene does not care about most of
this list - and prefer leaving it alone. Readable code that is fast enough beats fast code nobody wants
to change.

Never change behaviour to make something faster without saying so explicitly.

## Determinism, but only in `core`

`ArchitectureTest` already fails the build for wall-clock reads, `Math.random`, `java.util.Random` and
hash iteration inside `sim`, so do not re-check those there. The client has no such guard; the failure
worth looking for is rendering code that feeds a value back into the simulation.
