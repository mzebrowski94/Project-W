# ADR 0006: Claude runs the real Gradle build in its sandbox

**Trigger.** A build fails in the sandbox, or somebody proposes a faster compile path.

**Enforced by.** Nothing. `gradle.sh` either works or it does not; `docs/workflow.md` has the symptoms.

**Constraint.** Claude builds through `.claude/scripts/gradle.sh`, never `./gradlew`. The script
bootstraps the JDK and Gradle, translates the proxy configuration Java cannot read from the
environment, and keeps the dependency cache and build outputs off the Windows mount.

**Scope.** Claude's builds and the developer's builds are deliberately independent - different
output directories, no shared incremental state, no clobbering. `./gradlew` and IntelliJ remain the
normal path on Windows and need none of this.

**DO NOT BUILD** - rejected, and likely to be re-proposed. Exporting dependency jars and compiling with plain
`javac` - a faster inner loop on paper, but no Error Prone, no NullAway, no Spotless, and a second
classpath that silently drifts from Gradle's. It looks attractive whenever the script breaks; it is
not worth it.

**Depends on** Maven Central, `plugins.gradle.org` and `services.gradle.org` being allowlisted. If
that changes, the `javac` fallback is the retreat.

**Revisit when** the sandbox gains a JDK 25 and unproxied network of its own, at which point most of
the script becomes unnecessary.
