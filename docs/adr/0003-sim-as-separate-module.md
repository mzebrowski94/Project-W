# ADR 0003: The simulation is its own Gradle module

**Trigger.** Adding anything to `sim`, or proposing to merge it back into `core`.

**Enforced by.** The module graph, plus `ArchitectureTest` for what the classpath still offers.

**Constraint.** `sim` is a Gradle module that depends on neither `core` nor any rendering library.
`core` depends on `sim`. Never the reverse.

**Scope.** The module graph removes rendering *modules* and asset access from `sim`'s classpath -
but not every rendering class, because `sim` needs `gdx.jar` for `Vector2`, `MathUtils` and libGDX
collections, and that jar also contains `SpriteBatch`. `ArchitectureTest` closes the gap: it fails
the build if anything in `sim` touches `com.badlogic.gdx.graphics..`, `Gdx`, `java.util.Random`,
hash-ordered collections or the wall clock. Compiler and test together, not compiler alone.

**DO NOT BUILD** - rejected, and likely to be re-proposed. Packages plus an ArchUnit test, no extra module - the
same rules with one fewer `build.gradle`, but the classpath still offers rendering to simulation
code, and the temptation is one import away. This will look like reasonable simplification at some
point; it is not.

**Revisit when** never, realistically. The cost is one module file; the property it protects is the
one everything else rests on.
