# 03 - Measure the graph and choose the split

Type: research

Blocked by: 02

## Question

Which fenced packages, holding which classes, leave the fewest public helpers - and what are the ports?

## Read first

`.scratch/system-modules/map.md` (the sketch to test, the rules it must obey),
`.scratch/system-modules/adr-0020-draft.md`, `docs/simulation-order.md`, the *Written by* column ticket 01
added to `docs/components.md`.

## Settled

Coarse (about eight plus `query`), by domain, under `sim.system.<domain>`, `upgrade` among them. A package
uses only `query`, the shared packages and other packages' ports. `query` writes nothing. A system goes to
the package whose components it mostly writes.

## Deliver

1. The use graph in `sim.system` and `sim.upgrade`, code references only (comments and javadoc
   stripped) - a script in `.claude/scripts/` or a throwaway test; say which and why.
2. A split: package -> systems, helpers, ports. Every class that must be public, and why.
3. Where each test in `sim/src/test/.../sim/system/` goes. Tests reaching package-private helpers in two
   packages are the hard case - name them and propose what each becomes.
4. What becomes of the one direct call between systems, `TrooperMoveSystem` -> `NavigationSystem.spreadOut`.
5. Where `AppearanceSystem` and `Pace` go (the sketch has no answer).
6. Every place a path is hard-coded and must change: `ArchitectureTest`, `SystemOrderTest`,
   `component-index.py`, the `unit-tests` and `new-system` skills, `docs/architecture.md`, `CLAUDE.md`,
   the ADRs and glossary that name `system/` or `sim.upgrade`, and `ui`, `core` and `net` importing
   `sim.upgrade`.

## Done when

The split is in `map.md` *Decisions so far* in place of the sketch, approved by Mateusz.
