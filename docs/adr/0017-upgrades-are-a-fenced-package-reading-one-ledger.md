# 0017: Upgrades are a fenced package reading one ledger

**Trigger.** Adding an upgrade, an adaptation, a new kind of effect or a new `Stat`; making a number
something an upgrade can move; reading or writing `Upgrades` or `Drafts` from outside
`sim.upgrade`; proposing a per-entity stats component, a buff system, or a module for the upgrade
code.

**Enforced by.** `ArchitectureTest`, as each fence lands (`docs/design/upgrades-scope.md` §5.1):
nothing outside `..sim.upgrade..` reads or writes `Upgrades` or `Drafts` but `WorldCodec`,
`Components` and the `Spawner` that creates them (S3), with the interface's half in
`UiArchitectureTest` and `ClientArchitectureTest` (S5); `..sim.upgrade..` depends on nothing in
`..sim.system..` or `..sim.scenario..` (S2); every `Stat` sits on exactly one `@Reads` method of
`Modifiers`, and that method has exactly one caller outside the package (S2);
`systemsHoldNoStateAcrossTicks` covers the package (S2). `UpgradeTest` - a unit spawned after an
upgrade is identical to one on the field when it landed (S3). Until those land: nothing mechanical -
caught by reading.

**Constraint.**

- **One ledger per level.** What each side holds, with ranks, lives on the level entity (G0007) as
  records in named collections (ADR 0014), so a snapshot, a hash, a drop-in and a heal carry it with
  no code of their own.
- **Folded at the one reader, never written back.** A number an upgrade may move is a `Stat`. Its
  base is whatever the reader already holds - a definition, or the copy a component took at spawn -
  and the reader asks `Modifiers` for the folded value every time it reads it: whole-number
  percentages summed, clamped per stat, applied once. Nothing stores the result. A unit spawned
  after an upgrade is therefore identical to one that was standing when it landed, by construction.
- **An upgrade changes numbers, never composition.** Behaviour it unlocks is written once, carried
  from spawn by every unit that could use it, and off at zero (ADR 0013; `AiChance`,
  `RangedDef.off()`).
- **Four entry points.** `UpgradeSystem` earns drafts and is the only writer of the ledger; `Picks`
  takes the tick's picks from `Simulation.step`; `Modifiers` is the read port; `UpgradeView` is what
  the interface reads. Everything else in the package is package-private.
- **`UpgradeSystem` lives in `sim.upgrade`, not `system/`** - the one exception to "systems live in
  `system/`". A thin system there handing everything to package-private rules would split one concern
  across two packages and need a fence exception anyway. `SystemOrderTest` reads `Simulation`'s
  constructor, not packages, so the order keeps one source of truth.
- The package is called, and does not call: it depends on components, definitions and `Rng`, never on
  a system or a scenario. Its pure parts (`Fold`, `Dealer`, `Rotation`) depend on nothing, so lifting
  them into a module later is a move rather than a rewrite.

**Scope.** Both sides: the players' upgrades and the aliens' adaptations use the same catalogue,
ledger and read port; only the trigger differs. It does not cover the interface - how a draft is
shown is `ui` and `core` (ADR 0015) - nor what earns a draft or who chooses, which are gameplay
(G0049). It says nothing about pickups: a gun from a crate is still found and lost, and no effect
hands one out.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A per-entity current-stats component.* Every one needs filling at spawn and refilling when
an upgrade lands - the on-spawn hook and the re-apply pass below - and it is a second copy of every
number for the snapshot to carry.

**No:** *A dispatcher that applies effects to entities.* Nothing is applied to an entity. The only
writer appends to the ledger; readers fold.

**No:** *An on-spawn hook.* Reading at the seam makes a late unit right by construction; a hook is a
list of places somebody will forget to add the next stat to.

**No:** *Adding or removing a component for a tactic or ability.* Composition is fixed at spawn (ADR
0013); a toggle would desync a drop-in. A tactic moves numbers the mind already weighs, or turns up a
dormant capability.

**No:** *A running total edited in place.* Incremental float edits drift and must be undone exactly;
a fold from the base cannot drift and never needs undoing.

**No:** *A stat with two readers.* Two readers are two places that must agree about the same number,
and one of them will be missed. Fence 3 fails the build.

**No:** *A Gradle module for the subsystem now.* ADR 0003 and 0015 chose modules to make rendering
unreachable from the simulation; nothing here needs a classpath wall, and the package's fences say
what a module boundary would. The pure half is kept dependency-free so the move stays cheap.

**Revisit when** `def/` moves into a module of its own, or a second consumer of the pure half
appears - either makes a module worth its build cost.
