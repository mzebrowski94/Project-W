# Working in this repository

Defaults, not procedure. Better path for a specific task? Take it. Entries that stop earning their
place get deleted - this file is meant to be pruned.

## When something fails

A failed build is information, not a cue to try something else.

1. **Read the actual error** - file, line, what the checker or assertion objected to. Failures print in
   full; the whole log is `ai/logs/verify.log`. Never re-run an unchanged command hoping for a
   different result.
2. **Check the failure table below before theorising.** Most of what goes wrong here has gone wrong
   before, and the known cause is usually not what the message suggests.
3. **State the cause in one sentence before editing.** Cannot state it -> do not know it yet -> read
   more. A fix chosen before the cause is named is a guess.
4. **One hypothesis per attempt**, tested with the narrowest run that can refute it - level 2 on the
   ladder below, not a full verify.
5. **Two failed fixes is the limit.** Then stop and report: the error, what you tried, what you ruled
   out, what you would try next.

A failing test is not automatically the wrong test. Say which of the two is wrong before editing
either.

## Recipes

Run in the shell with the repo mounted. On Windows use `./gradlew` instead of the script.

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

Build outputs land in `~/.pwt/build/`, not `<project>/build` - see *Speed*. Test result XML under
`~/.pwt/build/<module>/test-results/`. Verify log: `ai/logs/verify.log`.

**Batch Gradle calls.** The daemon does not survive between shell invocations, so each call pays ~7s of
JVM startup. Two tasks in one command beat two commands.

**Scope every recursive search.** `grep` or `find` across the whole repo on the Windows mount times out.
Use `sim/src core/src map/src docs`.

**Editing in IntelliJ between sessions is fine.** Source, config and assets are the same files on `D:`;
only build output differs (`~/.pwt/build` here, `<project>/build` there). Re-read a file before editing
rather than trusting an earlier read.

## Verification ladder

Start at the cheapest level that can refute the change. Escalate on a trigger, not by habit.

| Level | Command | Run it when |
|---|---|---|
| 1 | `gradle.sh :<module>:compileJava` | You want to know it parses. Rarely worth it alone - level 2 compiles too. |
| 2 | `gradle.sh :sim:test --tests '*TheOneTest'` | Iterating on one behaviour. The default while working. |
| 3 | `verify.sh :sim:test` | That module's logic is settled. |
| 4 | `verify.sh` | Before saying anything is done. Also whenever a public API, a module boundary, system registration order, `sim`/`map` logic, or shared infrastructure changed. |
| 5 | `./gradlew lwjgl3:run`, on Windows | Rendering, input or feel changed - the part no test covers. Mateusz runs it. |

Escalate immediately when the current level fails: a level-2 failure is not evidence about level 4.
Never claim done below level 4.

## Speed

The Windows mount writes at ~12 MB/s; the sandbox's own disk does ~2 GB/s. `gradle.sh` therefore keeps
both the dependency cache and all build output on the VM's disk. Warm build 2.7s against 7.2s.

The durable dependency cache lives at `ai/gradle-home`, copied in once per session (~25s on the first
build). New dependencies downloaded during a session exist only in the fast local copy until
`--save-cache` pushes them back - hence the dependency-change recipe.

## Failure modes

Only things actually hit. Add what you hit; delete what stops happening.

| Symptom | Cause | Action |
|---|---|---|
| `UnknownHostException: services.gradle.org` | Java does not read `HTTPS_PROXY`; the wrapper cannot reach the network. | `.claude/scripts/gradle.sh`, never `./gradlew`, in the sandbox. |
| `formatting: NEEDS APPLYING` | No delete permission this session; Spotless rewrites a file by unlinking it first. | Everything else still ran. Ask Mateusz to approve deletion, re-run. |
| `403 Forbidden` fetching a `.pom` | Host not on the egress allowlist. | Report the host. Do not route around it. `jitpack.io` and `central.sonatype.com` blocked; Maven Central, plugin portal and foojay allowed. |
| `Could not read directory .../build/...` | Stale build output from another toolchain or a killed build. | `rm -rf */build ~/.pwt/build`, retry. |
| `Failed to run Gradle Worker Daemon`, often with a `java.security` read error | Sandbox memory pressure. | Retry; if it repeats lower `-Xmx` in `gradle.sh`. Do not raise `gradle.properties` - tuned for the desktop. |
| Spotless fails `build` with a diff wall | Files not yet formatted - the gdx-liftoff template used tabs. | `spotlessApply` first; `verify.sh` already does. |
| `cannot find symbol: com.badlogic.gdx.utils.ImmutableArray` | It is Ashley's, not libGDX's. | `com.badlogic.ashley.utils.ImmutableArray`. |
| A dependency's "latest" is a milestone (AssertJ resolved `4.0.0-M1`) | Maven metadata `<latest>` includes pre-releases. | Pin the newest stable in `gradle.properties`. |
| `ArchitectureTest` reports violations inside itself | Build outputs relocated, no longer matching `.../build/classes/java/test/`; ArchUnit's `DoNotIncludeTests` silently stops excluding test classes. | Keep the `/build/` segment in any relocated output path - `gradle.sh` does. |
| Lombok `sun.misc.Unsafe` warning | Unavoidable on JDK 25. | Ignore; `verify.sh` filters it. |
| `Could not create service of type FileHasher ... java.io.IOException: Input/output error`, and `rm` on `.gradle/**` says `Permission denied` | IntelliJ or a Windows `gradlew` daemon holds `<project>/.gradle`'s lock files, which the sandbox VM may not touch. Reads like a corrupt cache; is not one. | Nothing to do: `gradle.sh` passes `--project-cache-dir`. If it reappears, check that flag is still there before deleting anything. |
| An input that is "just pressed" is ignored at random | A frame shorter than one tick runs **zero** simulation steps, so a one-frame input reaches no tick. Above 60 Hz that is most frames. | Latch it until a tick spends it, as `ScenarioSession.pendingOrder` does. Anything read with `isKeyJustPressed` and handed to `advance()` has this. |
| `error: warnings found and -Werror specified` | A real warning. Most often a javadoc orphaned by inserting a method *above* the one it documented. | Fix it. Do not disable the check - eight warnings accumulated in one session before anyone noticed. |
| `sandbox-helper: no Plan9 drive shares mounted under /mnt/.virtiofs-root/shared` | The device shell cannot mount `D:` at all. Hit 2026-09-11, attributed to a Windows update of 2026-09-08; fixed on Mateusz's side 2026-09-12. | Nothing in the build can run. Say so and fall back to the file tools - see below. |
| `too deeply nested to stage: N folders below the connected folder` | `device_stage_files` supports 7 folders; Java sources sit 8-9 deep. | Not workable around from here. Read the file another way or ask Mateusz to connect a deeper folder. |

## When the mount is down

`device_list_dir`, `device_stage_files` and `device_commit_files` still work, but staging caps at
**7 folders** below the connected root and Java sources are 8-9 deep. Consequence, stated plainly:
**sources can be written but not read.** Check this early, before planning a session around reading
code. Docs, `CLAUDE.md`, `build.gradle` and everything in `.claude/` are shallow enough.

`.claude/` is blocked for `device_commit_files` specifically, not for the device shell: with the mount up,
`device_bash` writes there normally. Only when the mount is down must a skill or script edit be handed to
Mateusz as a file.

## Friction only Mateusz can remove

Named so it is not re-discovered every session. None of it blocks work; each is a cost paid repeatedly.

- **Delete permission resets every session**, and can drop mid-session when the bridge reconnects. Ask
  in your first message. Not a blocker for everything: `verify.sh` probes for it and without it
  compiles, lints and tests anyway, reporting formatting instead of applying it. It *is* a hard blocker
  for renaming or deleting a file. Spotless is the only thing in the whole build that deletes anything
  on `D:` (verified by syscall trace, 2026-09-07).
- **Computer-use access lapses between sessions.** Re-approve when Claude needs to run or look at the
  game.
- **Allowlisting `repo.maven.apache.org`** would allow plain `mavenCentral()`; `api.foojay.io` would fix
  Gradle toolchain provisioning.
- **The repo lives on `D:`, a Windows mount** - the ~175x write penalty above. Worked around in
  `gradle.sh`. A faster setup would put it on the Linux filesystem, at the cost of IntelliJ no longer
  seeing it. Not recommended; the workaround is enough.

Checked and closed: nothing in the test suite needs Docker or Testcontainers, and nothing will - the
simulation has no external collaborators.
