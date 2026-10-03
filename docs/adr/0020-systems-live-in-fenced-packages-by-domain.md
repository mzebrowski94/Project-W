# 0020: Systems live in fenced packages by domain

**Status.** accepted

**Trigger.** Adding, moving or splitting a system or a helper; one fenced package using another's class;
adding a fenced package or a port; proposing `@Internal`, an `.internal` package, a facade, or a split of
`component` or `def`.

**Enforced by.** Nothing under `sim.system` sits outside a declared fenced package
(`ArchitectureTest.nothingUnderSystemLivesOutsideADeclaredPackage`) and every system sits under `sim.system`
(`.everySystemLivesUnderSystem`); a fenced package uses another only through `query` and the declared
ports (`.aFencedPackageUsesOnlyQueryAndPortsOfAnother`); `query` is public and holds no state
(`.queryIsPublicAndHoldsNothing`) and writes no component, measured with ADR 0019's analysis
(`.queryWritesNothing`). Each fails on zero classes rather than passing.

**Amends ADR 0017.** 0017 made `sim.upgrade` the one fenced package and the one exception to "systems
live in `system/`". Here every system lives in one, and `upgrade` is one of them: `sim.system.upgrade`,
its four entry points its ports, its other fences unchanged. 0017's status becomes `amended by ADR 0020:
sim.upgrade is sim.system.upgrade, one fenced package among several`. ADR 0003 is untouched - 0017
already amended it, and this only uses that amendment more widely.

**Constraint.**

- **Every system lives in exactly one fenced package**, `sim.system.<domain>`. A fenced package is a Java
  package with a `package-info.java` saying what it is, its systems and ports, what does not belong in it,
  and the records it answers to.
- **Package-private by default.** Public are only its systems (`Simulation` constructs them) and its
  **ports** - classes other packages may use, each named in `package-info.java` and in
  `ArchitectureTest.PORTS`.
- **A fenced package uses only** `sim.system.query`, `component`, `def`, `collision`, the `sim` root, and
  other packages' ports. Never another package's system or helper.
- **`sim.system.query`** holds public, stateless questions about components, and writes none (ADR 0019's
  analysis). Something shared that writes is a port of the package it belongs to.
- **Placement.** A system goes with the non-`query` helpers it uses; failing that, with the component it
  alone writes (ADR 0019's column); failing that, to the glossary section that names what it does. Not by
  the components it mostly writes - shared state (`Velocity`, `Position`, `Steering`) swamps that, and most
  systems write nothing of their own. A package founded after this record needs two or more systems, or a
  system with a helper of its own, **and** a glossary word. `appearance` is the one founding exception: a
  single system that writes for the renderer alone (G0003).
- Every fence says `.because(...)` naming this record, and fails rather than passes on zero classes.
- **Packages say nothing about order.** `Simulation`'s constructor is still the one source of it.

**Scope.** `sim.system` and what was `sim.upgrade`. Not `component`, `def`, `collision`, `scenario` or the
`sim` root, which stay flat and shared. Not `core`, `ui` or `net`.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *An `@Internal` annotation.* On a package-private class it repeats what the compiler already
enforces; on a public one it repeats the rule.

**No:** *`.internal` subpackages.* A subpackage's classes must be public for its parent to use them; the
name promises what the compiler does not keep.

**No:** *Packages by tick phase.* The constructor is the one source of the order; a phase package copies
it, and a reorder would move a file.

**No:** *Splitting `component` or `def` by domain.* Components are the shared contract - any system
reading any component is the decoupling the ECS gives. Ownership is ADR 0019's write rule.

**No:** *Fine-grained packages (motion apart from combat, and so on).* `Drivers` (32 users) and `Sight`
(14) cross every such line; a fine split is a large public core and a rule for every pair.

**No:** *A facade per package.* A system's one caller is `Simulation`; a facade in front of it passes
through.

**No:** *A Gradle module per domain.* Nothing needs a classpath wall - 0017's reason, unchanged.

**No:** *An `annotation` package for `@NotState`, `@ConfigKey`, `@Reads`.* Each sits beside what enforces
it; `docs/conventions.md` lists them.

**Revisit when** a package's ports outnumber its systems (it is a library, not a domain), `query` needs a
writer by exception, or `core/client` takes the same shape.
