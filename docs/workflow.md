# Working in this repository

Defaults, not procedure. If you have a better path for a specific task, take it. Entries here that
stop earning their place get deleted - this file is meant to be pruned.

## Recipes

Run these in the shell that has the repo mounted. On Windows use `./gradlew` instead of the script.

| Task | Command |
|---|---|
| Everything: format, compile, lint, test | `.claude/scripts/verify.sh` |
| One module's tests | `.claude/scripts/verify.sh :sim:test` |
| One test class | `.claude/scripts/gradle.sh :sim:test --tests '*MovementSystemTest'` |
| One test method | `.claude/scripts/gradle.sh :sim:test --tests '*MovementSystemTest.movesAnEntityByItsVelocityEachStep'` |
| Format only | `.claude/scripts/gradle.sh spotlessApply` |
| After changing a dependency | `.claude/scripts/verify.sh` then `.claude/scripts/gradle.sh --save-cache` |
| Repack art from `assets-raw/` | `.claude/scripts/gradle.sh :core:packTextures` |
| Raw Gradle, any task | `.claude/scripts/gradle.sh <task>` |
| Run the game | Windows only: `./gradlew lwjgl3:run` |

Build outputs land in `~/.pwt/build/`, not `<project>/build` - see *Speed*, below. Test result XML
is under `~/.pwt/build/<module>/test-results/`. The verify log is `ai/logs/verify.log`.

**Batch Gradle calls.** The daemon does not survive between shell invocations, so each separate
call pays ~7s of JVM startup. Two tasks in one command are much cheaper than two commands.

**Editing in IntelliJ between sessions is fine.** Source, config and assets are the same files on
`D:`; only build output is separate (`~/.pwt/build` here, `<project>/build` there). Re-read a file
before editing it rather than trusting an earlier read.

## Speed

The Windows mount writes at ~12 MB/s; the sandbox's own disk does ~2 GB/s. `gradle.sh` therefore
keeps both the dependency cache and all build output on the VM's disk. Warm build: 2.7s, against
7.2s with everything on the mount.

The durable copy of the dependency cache lives at `ai/gradle-home` and is copied in once per
session (~25s on the first build). New dependencies downloaded during a session exist only in the
fast local copy until `--save-cache` pushes them back - which is why that is part of the
dependency-change recipe.

## Failure modes

Only things actually hit. Add what you hit; delete what stops happening.

| Symptom | Cause | Action |
|---|---|---|
| `UnknownHostException: services.gradle.org` | Java does not read `HTTPS_PROXY`; the wrapper cannot reach the network. | Use `.claude/scripts/gradle.sh`, never `./gradlew`, in the sandbox. |
| `formatting: NEEDS APPLYING` in verify output | Delete permission not granted this session; Spotless rewrites a file by unlinking it first. | Everything else still ran. Ask Mateusz to approve deletion for the folder, then re-run. |
| `403 Forbidden` fetching a `.pom` | The host is not on the egress allowlist. | Report the host. Do not route around it. `jitpack.io` and `central.sonatype.com` are blocked; Maven Central, the plugin portal and foojay are allowed. |
| `Could not read directory .../build/...` | Stale build output from another toolchain or a killed build. | `rm -rf */build ~/.pwt/build` and retry. |
| `Failed to run Gradle Worker Daemon`, often with a `java.security` read error | Sandbox memory pressure. | Retry; if it repeats, lower `-Xmx` in `gradle.sh`. Do not raise `gradle.properties` - that file is tuned for the desktop. |
| Spotless fails `build` with a diff wall | Files not yet formatted - the gdx-liftoff template used tabs. | `spotlessApply` first; `verify.sh` already does. |
| `cannot find symbol: com.badlogic.gdx.utils.ImmutableArray` | It is Ashley's, not libGDX's. | `com.badlogic.ashley.utils.ImmutableArray`. |
| A dependency's "latest" is a milestone (AssertJ resolved `4.0.0-M1`) | Maven metadata `<latest>` includes pre-releases. | Pin the newest stable explicitly in `gradle.properties`. |
| `ArchitectureTest` suddenly reports violations inside itself | Build outputs were relocated and no longer match `.../build/classes/java/test/`; ArchUnit's `DoNotIncludeTests` silently stops excluding test classes. | Keep the `/build/` segment in any relocated output path - `gradle.sh` does. |
| Lombok `sun.misc.Unsafe` warning | Unavoidable on JDK 25. | Ignore; `verify.sh` filters it. |

## Friction only Mateusz can remove

Named here so it is not re-discovered every session. Nothing here blocks work - each one is a cost
we currently pay repeatedly.

- **Delete permission resets every session** and cannot be made persistent from here. **Ask for it in
  your first message**, not when you hit it - see `CLAUDE.md`. Mid-task is exactly when Mateusz is
  least likely to be watching, and the task stalls on an unanswered prompt.
  It is not a blocker for everything: `verify.sh` probes for it and, without it, compiles, lints and
  tests anyway, reporting formatting instead of applying it. It *is* a hard blocker for renaming or
  deleting a file. Spotless is the only thing in the whole build that deletes anything on `D:`
  (verified by syscall trace, 2026-09-07).
- **Computer-use access also lapses between sessions.** Re-approve when Claude needs to run or look
  at the game.
- **The repository lives on `D:`, a Windows mount.** That costs the ~175x write penalty described
  above. Worked around in `gradle.sh`; a genuinely faster setup would put the repo on the Linux
  filesystem, at the cost of IntelliJ no longer seeing it directly. Not recommended - the workaround
  is enough.

Checked and closed: nothing in the test suite needs Docker or Testcontainers, and nothing will -
the simulation has no external collaborators.
