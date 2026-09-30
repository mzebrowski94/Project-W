# ADR 0008: A kind of thing is a definition in a table, not a marker and not a data file

**Status.** accepted

**Trigger.** Adding a strain, weapon, pickup or any other thing that comes in kinds.

**Enforced by.** `ArchitectureTest` bans `HashMap` in `sim`; `Defs` refuses to build with a hole in it.

**Constraint.** Anything that comes in kinds - a strain of enemy, a gun, a pickup - is an enum
constant plus an immutable definition record, held in a `Defs<K, V>` table indexed by that enum. An
entity's kind is a **field on a component** (`Strain.kind`, `Weapon.kind`), never which marker
components it carries. One method reads a definition into the components an entity already has -
`Spawner.applyStrain`, `Spawner.applyWeapon` - and it is the only writer, so spawning as a kind and
changing into it are the same code.

Adding a kind is a constant, a definition and a block in `game.properties`. It is never a new class,
a new marker, a new `spawn` method or a new branch.

**Scope.** Kinds within the simulation. It says nothing about *composition*: which components an
entity is made of is still Java in `Spawner`, and deliberately so - see the second rejection below.

It does not apply to things there is exactly one of. The arena, the commander's hand strike and the
collision solver's settings stay as named records on `SimConfig`; a table of one is a table nobody
reads twice.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A marker component per kind, as `Brute` was.* This is the obvious Ashley idiom and it is what this
codebase did until strains had to change at runtime. Rejected because evolving would mean adding a
component in the middle of a tick, which mutates the families being iterated and silently skips
entities - the exact trap `Damage`, `Dying` and `Stunned` are all carried permanently to avoid. A
marker is fine for something an entity is for its whole life (`Commander`, `Enemy`, `Obstacle`);
it is wrong for anything that can change.

**No:** *Data-driven archetypes: entity composition described in files, `Spawner` as a generic assembler.*
This is what Unity prefabs, Unreal blueprints and Flecs prefabs give you, and it will be proposed the
first time somebody counts the `spawn` methods. Rejected because it trades compile-time errors for
content errors, and this project's entire quality strategy is the opposite - `ArchitectureTest`,
NullAway, Error Prone and the enforced invariants all exist to fail the *build*. "This archetype has
`Position` but forgot `PreviousPosition`" would stop being a compile error and become a bug found by
playing. It also needs a name-to-component registry, whose natural implementations are reflection or
a `HashMap` - and hash iteration order is the one thing ADR 0004's determinism cannot survive.

The deciding argument is the one already written in `Scenario`'s Javadoc: a scenario is a Java class
rather than a data file *on purpose*, because "adding one means a recompile, which costs nothing
here". One programmer, a handful of kinds, a three-second build. Kinds have the same profile.

**No:** *A `HashMap<Kind, Def>` instead of an ordinal-indexed array.* Rejected outright: hash iteration order
is not reproducible, and `ArchitectureTest` fails the build on `HashMap` in `sim` for that reason.
The array indexes on `ordinal()`, which is safe here only because nothing is persisted against those
numbers - the table is built from the enum's own constants in the same process that reads it.

**Revisit when** kinds are being authored by somebody who is not compiling the game - a level editor,
a modding surface, or a designer iterating faster than a build. That is a team-shape milestone, not a
code-quality one. Do not revisit it because the number of enum constants has grown; that is what the
table is for. Promoting a definition from `SimConfig.defaults()` to something parsed out of
`game.properties` is a change to one loader method and needs no record at all - the loader already
does exactly that for every number.
