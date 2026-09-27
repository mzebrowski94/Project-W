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
| Networking tests, real sockets on this machine included | `.claude/scripts/verify.sh :net:test` |
| Whole online sessions in one test: host, peers, lag, drop-in, a desync healed | `.claude/scripts/gradle.sh :net:test --tests '*SessionTest'` |
| Determinism fuzz, every scenario, about a minute (opt-in) | `PWT_FUZZ=1 .claude/scripts/gradle.sh :sim:test --tests '*DeterminismFuzzTest'` |
| Performance guard: time and allocation per step, 100 to 800 grunts, about 25 s (opt-in) | `PWT_BENCH=1 .claude/scripts/gradle.sh :sim:test --tests '*SimulationBenchmarkTest' --rerun-tasks -i` |
| One test class | `.claude/scripts/gradle.sh :sim:test --tests '*MovementSystemTest'` |
| One test method | `.claude/scripts/gradle.sh :sim:test --tests '*MovementSystemTest.movesAnEntityByItsVelocityEachStep'` |
| Format only | `.claude/scripts/gradle.sh spotlessApply` |
| After changing a dependency | `.claude/scripts/verify.sh` then `.claude/scripts/gradle.sh --save-cache` |
| Repack art from `assets-raw/` | `.claude/scripts/gradle.sh :core:packTextures` |
| Raw Gradle, any task | `.claude/scripts/gradle.sh <task>` |
| New sim tunable | A component on the config record and one line in `assets/game.properties` - nothing else. `ShippedConfigTest` fails on either alone. |
| Run the game | Windows only: `./gradlew lwjgl3:run` |
| Run it as copy 1, say, of several | Windows only: `./gradlew lwjgl3:run --args="--instance=1"` |

Build outputs land in `~/.pwt/build/`, not `<project>/build` - see *Speed*. Test result XML under
`~/.pwt/build/<module>/test-results/`. Verify log: `ai/logs/verify.log`.

**Connection logs** are in `ai/logs/net/` when the game runs from Gradle, one file per instance,
`<instance>-<yyyyMMdd-HHmmss>.log`; a build run any other way writes `logs/net/` beside itself, which is
what a friend sends back. Every line is `key=value`: `grep "event=closed"` gives every link that ended and
why, `grep "cat=stats"` each link's round trip and traffic once a second, and a link that closed badly is
followed by `cat=trace` lines - its last messages, times counted back from the moment it went. Netty's
own warnings arrive as `cat=netty msg="..."`. A session logs as `cat=sequencer` and `cat=peer`;
`grep "event=desync"` finds a disagreement, and `cat=stats` lines carry each peer's lead, how far behind
it runs, and how often the sequencer had to repeat its input.

**Two copies on one PC** - for online play, and the latency playtest in `docs/design/alpha-phase.md`.
Two `gradlew run`s at once contend for Gradle's locks, so build the jar once and start it twice, from
`assets/` as the run task does, with the log pointed where the run task points it:

```
./gradlew lwjgl3:jar
cd assets
java -Dpwt.net.logDir=..\ai\logs\net -jar ..\lwjgl3\build\libs\Project-W-1.0.0.jar --instance=0
java -Dpwt.net.logDir=..\ai\logs\net -jar ..\lwjgl3\build\libs\Project-W-1.0.0.jar --instance=1
```

(the second in another terminal; `java` must be 25 - `$env:JAVA_HOME\bin\java` if the one on the path
is older). Host with `H` in one; in the other `J` opens the join screen, where the host is listed - it
announces itself to this PC as well as to the network - and Enter joins it. The first time a copy hosts
or opens the join screen, **Windows Firewall asks about Java**: two copies on one PC talk over
`127.0.0.1` and need nothing, but for a LAN or Hamachi, allow it on the network type that adapter is in -
Hamachi's is usually *Public* - or a friend's game is never listed and his join times out with nothing
said. A friend on Hamachi sees the game in his list; one the list cannot reach types the host's room code
or one of the addresses its overlay shows.

**Desync dumps** land beside the connection log as `desync-t<tick>-peer<n>-<role>.bin`, one from the peer
that diverged (`desynced`) and one from the host (`reference`), each with a `.txt` summary.
`DesyncReport.compare(reference, desynced, worlds)` reads the pair: the fields that differ, which side a
replay lands on, and where a replay through snapshots parts from a straight one - `SessionTest`'s
desync test shows the call. Ask a friend for their `.bin` along with their log.

**Batch Gradle calls.** The daemon does not survive between shell invocations, so each call pays ~7s of
JVM startup. Two tasks in one command beat two commands.

**Scope every recursive search.** `grep` or `find` across the whole repo on the Windows mount times out.
Use `sim/src core/src map/src docs`.

**Editing in IntelliJ between sessions is fine.** Source, config and assets are the same files on `D:`;
only build output differs (`~/.pwt/build` here, `<project>/build` there). Re-read a file before editing
rather than trusting an earlier read.

### Reading git state

**`git --no-optional-locks status --porcelain`**, and the same flag for `diff`. Never a bare
`git status`.

The flag tells git not to take `index.lock` for the optional index refresh. Without it, every status
run from the sandbox takes that lock - and if delete permission has lapsed, git cannot remove it
again and leaves a zero-byte lock file that makes Mateusz's own `git add` fail with "another git
process seems to be running". The lock is not doing anything; it is litter that only looks like a
running process, and clearing it needs the delete grant the sandbox may not have.

Nothing here should ever run `git add`, `commit` or `push` - see `CLAUDE.md`; a cloud session commits
only through `cloud-commit.sh` (*Cloud sessions*, below). So no command from this
side has any business writing the index at all, and the flag makes that true rather than hoped for.

## Verification ladder

Start at the cheapest level that can refute the change. Escalate on a trigger, not by habit.

| Level | Command | Run it when |
|---|---|---|
| 1 | `gradle.sh :<module>:compileJava` | You want to know it parses. Rarely worth it alone - level 2 compiles too. |
| 2 | `gradle.sh :sim:test --tests '*TheOneTest'` | Iterating on one behaviour. The default while working. |
| 3 | `verify.sh :sim:test` | That module's logic is settled. |
| 4 | `verify.sh` | Before saying anything is done. Also whenever a public API, a module boundary, system registration order, `sim`/`map` logic, or shared infrastructure changed. |
| 4b | the performance guard above | A system, the solver, `SpatialGrid` or anything else in a per-entity loop changed. `docs/performance.md`. |
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
| `.claude/scripts/verify.sh: Permission denied` (cloud) | The scripts were committed as `100644` from Windows, so a Linux clone cannot execute them. Hit 2026-09-26; now committed as `100755`, and `verify.sh` calls `gradle.sh` through `bash`. | Run `bash .claude/scripts/verify.sh`. The session-start hook also restores the bit. |
| `sandbox-helper: no Plan9 drive shares mounted under /mnt/.virtiofs-root/shared` | The device shell cannot mount `D:` at all. Hit 2026-09-11, attributed to a Windows update of 2026-09-08; fixed on Mateusz's side 2026-09-12. | Nothing in the build can run. Say so and fall back to the file tools - see below. |
| IntelliJ or the command line says `Unable to create '.git/index.lock': File exists. Another git process seems to be running` | **A `git` command run from the sandbox left its lock behind.** Plain `git status` takes `index.lock` to write a refreshed index cache, and removing it again needs delete rights on `D:` - which reset between sessions, so a status run without them strands a zero-byte lock that blocks Mateusz's own git indefinitely. Hit 2026-09-17. | Delete `.git/index.lock` (needs delete permission), then **always** `git --no-optional-locks ...` for anything read-only. See the recipe below. |
| `too deeply nested to stage: N folders below the connected folder` | `device_stage_files` supports 7 folders; Java sources sit 8-9 deep. | Not workable around from here. Read the file another way or ask Mateusz to connect a deeper folder. |

## Cloud sessions

A Claude Code session on the web runs in a Linux container cloned fresh from GitHub: no Windows mount, no
IntelliJ, no display, and **thrown away** after a while idle. `CLAUDE_CODE_REMOTE=true` says you are in one.
What differs from the sandbox on Mateusz's machine:

- **The toolchain is provisioned before you start.** `.claude/hooks/session-start.sh` (SessionStart,
  cloud only) downloads JDK 25 and Gradle into `ai/toolchain` and compiles main and test sources, so the
  first `verify.sh` is a build rather than a two-minute download. Its log: `ai/logs/session-start.log`.
  If it failed, `verify.sh` simply redoes the bootstrap.
- **Commit and push, or lose the work.** `.claude/scripts/cloud-commit.sh -m "Subject" -m "trailer"...
  [-- path...]` stages (everything, or the paths given), commits and pushes to the current `claude/*`
  branch with retries. It refuses outside a cloud session, on any other branch, and with nothing staged;
  it never amends or forces. Raw `git add/commit/push` stay denied in `.claude/settings.json` for every
  session - the script is the one door, and its guards are what make it safe to leave open.
  Commit at each stage a change can stop after, not only at the end. Mateusz merges into main.
- **Several commits for several concerns**: pass paths after `--` to commit one concern at a time.
- **No delete-permission ask**, no index-lock hazard, no Windows mount: the local-only rules above
  (`--no-optional-locks`, scoped greps) are harmless here and stay the habit.
- **Build output** is in `~/.pwt/out/<module>/build/`, test XML under `.../test-results/test/`.
- **The game cannot run.** Level 5 of the ladder is still Mateusz's machine.

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
