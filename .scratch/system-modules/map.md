# System packages - map

Split `sim.system` (75 classes, flat) into **fenced packages** by domain, after making component
write-ownership something the build checks. Grilled 2026-10-03.

Word: a domain package under `sim.system` is a **fenced package** (ADR 0017's term), never a "module" -
in `CLAUDE.md` a module is a Gradle module. A public class of one that another may use is a **port**
(0017: "`Modifiers` is the read port").

## Notes

What the code said on 2026-10-03, which the decisions rest on.

- Systems do not call each other. 52 systems; one real call across them -
  `TrooperMoveSystem` -> `NavigationSystem.spreadOut`. Every other mention is javadoc.
- Coupling in code runs through 19 package-private helpers, and they cross domains: `Drivers` has 32
  users, `Sight` 14, `Guns` 7, `Throws` 7, `Odds` 5 (plus components, `def`, `Simulation`). The seven
  `Trooper*` helpers are used by `TrooperMindSystem` alone - a fenced package already in all but name.
- Coupling in data - who writes which component - is enforced by nothing. `components.md`'s *Touched by*
  cannot tell a read from a write. `architecture-review` 01 (four writers of `Damage`, fixed as G0057) is
  the cost of that.
- The tick order interleaves domains (grenade: 14, 26, 40), so packages cannot mirror it.
- Splitting a Java package turns package-private helpers public - there is no "visible to siblings".
  ArchUnit then has to close what the compiler closed for free.
- ~70 tests sit in package `sim.system` and reach package-private helpers; they move with what they test.
- `core` never imports `sim.system`. The renderer reads `Appearance`, `Position`, `Facing`, `Body`,
  `Commander`; every other component read is `DebugOverlay`. `core/client` is the bigger flat package
  (91 files).
- `component-index.py` globs `system/*.java` non-recursively and skips `sim.upgrade` - a move would drop
  systems from the index silently.
- `DecisionRecordsTest.namesOnlyTestsThatExistAsWhatEnforcesIt` - an ADR can only land with its test.
  Hence the two drafts here rather than in `docs/adr/`.

## Decisions so far

1. **Write-ownership first** ([adr-0019-draft.md](adr-0019-draft.md), ticket 01). A *Written by* column in
   `components.md` is the declaration; an ArchUnit test compares it with what the bytecode writes and
   fails on any difference, either way. Per component, credited to the system (followed through the
   helpers it calls). A write is a field set from outside the component or a call to a changing method.
   `component-index.py` keeps the column. Whether several writers need a *Why several* note: after
   measuring.
2. **Fenced packages by domain, coarse** ([adr-0020-draft.md](adr-0020-draft.md)): about eight under
   `sim.system.<domain>`, plus `sim.system.query`. Not by tick phase, not fine-grained. Amends ADR 0017
  only - 0003 was already amended by 0017, and 0020 uses that amendment more widely.
3. **Fence = package-private by default** + `package-info.java` per package + ArchUnit only for what
   visibility cannot say (dependencies between packages, ports, `query` writes nothing), each rule with
   `.because(...)` and refusing to pass on zero classes. No `@Internal`, no `.internal`.
4. **Between packages**: `query`, `component`, `def`, `collision`, the `sim` root, and the declared ports of
   other fenced packages. Nothing else.
5. **`query`**: public, stateless, reads components, writes none. Anything that writes is a port of its
   package (`Deaths.kill`).
6. **Placement**: a new system goes to the package whose components it mostly writes. A new package needs
   two or more systems, or a system with a helper of its own, **and** a glossary word.
7. **`upgrade` moves to `sim.system.upgrade`** and becomes one fenced package among several; 0017's
   other fences stand.
8. **`component` and `def` stay flat and shared.** Ownership is the write rule, not a package.
9. **Annotations** (`@NotState`, `@ConfigKey`, `@Reads`) stay beside what enforces them; a three-row
   table in `docs/conventions.md` lists them.
10. **Order**: 01 -> 02 -> 03 -> 04 -> 05. 03-06 and 09 of `architecture-review` are outside this.
11. **`core/client`** is its own effort, later (ticket 06).

Sketch of the split - to be replaced by ticket 03's measurement, not trusted as it stands:

```
sim/system/
  query/      Drivers, Sight, Odds ...
  motion/     Drive, Movement, Avoidance, Navigation, Weave, Hop, Dodge, Wander, Stun, Facing, PositionHistory
  squad/      SquadOrder, TrooperMind, TrooperMove, TrooperCombat + Trooper*
  commander/  CommanderControl, Melee, Strike, Drag, Action, Aid, Crate, Turret
  creature/   Perception, Grip, EnemyRanged, EnemyLeap, Evolution, Barrage
  combat/     Collision, Damage, Shock, Grenade, ProjectileBounds, Cooldown, Pickup + Guns, Muzzle, Throws, Aim, Grants
  death/      Casualty, Dying, Respawn, Bloat, Mine, Blast, Slick, Reaper + Deaths, RallyPoint
  level/      Reinforcement, Wave, Nest, Lair, ControlPoint, Objective + Rouse
  upgrade/    from sim.upgrade
```

Already pushing back: `Pace` (motion) uses `Guns` and `Aid`; `AppearanceSystem` has no home yet.

## Fog

- How many components have several writers - decides the *Why several* note (01).
- What `scenario/` and `Simulation`'s constructor write while assembling a world: treat as assembly like
  `Spawner`, or list them (01).
- Whether lambdas inside systems are credited to the system by ArchUnit's call graph (01).
- Whether `architecture-review` 08 and 10 are taken (02).
- The real split, the ports, where tests that span packages go, what becomes of `spreadOut` (03).
