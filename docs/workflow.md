# Working in this repository

Defaults, not procedure. Better path for a specific task? Take it. Entries that stop earning their
place get deleted - this file is meant to be pruned.

## Environments

A session runs in one of three places. The commands are the same in all of them - `gradle.sh` and
`verify.sh` work out where they are - but a handful of rules only exist because of one of them.

| | **Desktop** | **Cowork** | **Cloud** |
|---|---|---|---|
| What it is | Claude Code desktop on Mateusz's Windows machine | A Linux VM with the Windows drive mounted, reached through `device_bash` | Claude Code on the web: a fresh Linux clone |
| How to tell | `uname -s` starts `MINGW` (Git Bash) | the device shell; the repo is under a mount | `CLAUDE_CODE_REMOTE=true` |
| Gradle | `gradle.sh` hands over to `./gradlew` with the machine's JDK 25 | `gradle.sh` bootstraps JDK and Gradle into `ai/toolchain`, through the proxy | as Cowork, provisioned by the session-start hook |
| Build output | `~/.pwt/out/<module>/build/`; a linked worktree `~/.pwt/out/wt/<folder>/<module>/build/` | same | same |
| Delete permission | nothing to ask for | resets every session - ask in your first message | always |
| Commits | Mateusz; an AFK run commits its own branches (*AFK runs*) | Mateusz | Claude, through `cloud-commit.sh` (*Cloud sessions*) |
| The game | runs (`./gradlew lwjgl3:run`); Mateusz judges the feel | cannot run | cannot run |

Sections below marked **(Cowork)** apply only there.

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

The same commands in every environment.

| Task | Command |
|---|---|
| Everything: format, compile, lint, test | `.claude/scripts/verify.sh` |
| One module's tests | `.claude/scripts/verify.sh :sim:test` |
| Networking tests, real sockets on this machine included | `.claude/scripts/verify.sh :net:test` |
| Whole online sessions in one test: host, peers, lag, drop-in, a desync healed | `.claude/scripts/gradle.sh :net:test --tests '*SessionTest'` |
| Read a desync: every `reference`/`desynced` dump pair in a directory, report written beside them | `PWT_DESYNC=<dir> .claude/scripts/gradle.sh :core:test --tests '*DesyncReportRunnerTest' -i` |
| Determinism fuzz, every scenario, about a minute (opt-in) | `PWT_FUZZ=1 .claude/scripts/gradle.sh :sim:test --tests '*DeterminismFuzzTest'` |
| Performance guard: time and allocation per step, 100 to 800 grunts, and the fold's cost with everything held, about 25 s (opt-in) | `PWT_BENCH=1 .claude/scripts/gradle.sh :sim:test --tests '*SimulationBenchmarkTest' --rerun-tasks -i` |
| One test class | `.claude/scripts/gradle.sh :sim:test --tests '*MovementSystemTest'` |
| One test method | `.claude/scripts/gradle.sh :sim:test --tests '*MovementSystemTest.movesAnEntityByItsVelocityEachStep'` |
| Format only | `.claude/scripts/gradle.sh spotlessApply` |
| After changing a dependency | `.claude/scripts/verify.sh` then `.claude/scripts/gradle.sh --save-cache` (Cowork; a no-op elsewhere) |
| Repack art from `assets-raw/` | `.claude/scripts/gradle.sh :core:packTextures` |
| Raw Gradle, any task | `.claude/scripts/gradle.sh <task>` |
| New sim tunable | A component on the config record and one line in the `assets/sim/*.properties` file its prefix lives in - nothing else. `ShippedConfigFilesTest` fails on either alone, and `ShippedConfigTest` on a key given in two files. A new file in `assets/sim/` is read without further wiring. |
| New network tunable | A component on `NetConfig` (or one of its records) and one line in `assets/network.properties` - nothing else. `ShippedConfigFilesTest` fails on either alone. |
| New graphic or controls tunable | A component on `GraphicConfig` or `ControlsConfig` (or one of their records) where its key's path says, and one line in `assets/graphic.properties` or `assets/controls.properties` - nothing else. A colour, a key and a mouse button are read by the binder. `ShippedConfigFilesTest` fails on either alone. |
| Run the game | Desktop only: `./gradlew lwjgl3:run` |
| Windows exe, Java bundled, for friends | Desktop only: `./gradlew lwjgl3:packageWinX64` -> `dist/` (`docs/packaging.md`) |
| Run it as copy 1, say, of several | Desktop only: `./gradlew lwjgl3:run --args="--instance=1"` |
| Play a mission by its code | Desktop only: `./gradlew lwjgl3:run --args="--mission=MQHN-R4YX"` - codes are printed by `PWT_SWEEP=1` (the `new-biome` skill) |

Claude's build output lands in `~/.pwt/out/<module>/build/`, apart from IntelliJ's `<project>/build`;
a linked worktree builds into `~/.pwt/out/wt/<folder>/` instead (`.claude/scripts/out-dir.sh`), so
parallel agents never overwrite each other's classes. Test result XML under `.../test-results/test/`.
Verify log: `ai/logs/verify.log`.

**Connection logs** are in `ai/logs/net/` when the game runs from Gradle, one file per instance,
`<instance>-<yyyyMMdd-HHmmss>.log`; a build run any other way writes `logs/net/` beside itself, which is
what a friend sends back. Every line is `key=value`: `grep "event=closed"` gives every link that ended and
why, `grep "cat=stats"` each link's round trip and traffic once a second, and a link that closed badly is
followed by `cat=trace` lines - its last messages, times counted back from the moment it went; a host
has two transports, and `tr=sequencer` / `tr=peer` says whose. Netty's
own warnings arrive as `cat=netty msg="..."`. A session logs as `cat=sequencer` and `cat=peer`;
`grep "event=desync"` finds a disagreement, and `cat=stats` lines carry each peer's lead, how far behind
it runs, its round trip as the sequencer measures it (`rttMs`), and how often the sequencer had to repeat
its input.

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
replay lands on, and where a replay through snapshots parts from a straight one. The `PWT_DESYNC` recipe
above runs it on a directory, with the host's shipped config. Ask a friend for their `.bin` along with their log.

**Batch Gradle calls (Cowork, Cloud).** The daemon does not survive between shell invocations there, so
each call pays ~7s of JVM startup. Two tasks in one command beat two commands. On the desktop the
wrapper's daemon stays warm.

**Scope every recursive search** to `sim/src core/src map/src ui/src net/src docs`. The tree also holds
`ai/` (a toolchain and a dependency cache) and build output; across the Cowork mount a whole-repo `grep`
times out.

**Editing in IntelliJ between sessions is fine.** Source, config and assets are the same files; only
build output differs. Re-read a file before editing rather than trusting an earlier read.

### Reading git state

**`git --no-optional-locks status --porcelain`**, and the same flag for `diff`, in every environment.

The flag tells git not to take `index.lock` for the optional index refresh. In Cowork that lock is a
hazard: removing it needs delete rights on the mount, which reset between sessions, so a status run without
them strands a zero-byte lock that makes Mateusz's own `git add` fail with "another git process seems
to be running". Elsewhere the flag costs nothing, and one habit beats three.

A read has no business writing the index, and the flag makes that true rather than hoped for. Writes
are `CLAUDE.md`'s *Mateusz commits* and *AFK runs* below.

## Verification ladder

Start at the cheapest level that can refute the change. Escalate on a trigger, not by habit.

| Level | Command | Run it when |
|---|---|---|
| 1 | `gradle.sh :<module>:compileJava` | You want to know it parses. Rarely worth it alone - level 2 compiles too. |
| 2 | `gradle.sh :sim:test --tests '*TheOneTest'` | Iterating on one behaviour. The default while working. |
| 3 | `verify.sh :sim:test` | That module's logic is settled. |
| 4 | `verify.sh` | Before saying anything is done. Also whenever a public API, a module boundary, system registration order, `sim`/`map` logic, or shared infrastructure changed. |
| 4b | the performance guard above | A system, the solver, `SpatialGrid` or anything else in a per-entity loop changed. `docs/performance.md`. |
| 5 | `./gradlew lwjgl3:run`, on the desktop | Rendering, input or feel changed - the part no test covers. Mateusz judges it. |

Escalate immediately when the current level fails: a level-2 failure is not evidence about level 4.
Never claim done below level 4.

## Speed (Cowork)

The Windows mount writes at ~12 MB/s; the sandbox's own disk does ~2 GB/s. `gradle.sh` therefore keeps
both the dependency cache and all build output on the VM's disk. Warm build 2.7s against 7.2s.

The durable dependency cache lives at `ai/gradle-home`, copied in once per session (~25s on the first
build). New dependencies downloaded during a session exist only in the fast local copy until
`--save-cache` pushes them back - hence the dependency-change recipe.

## Failure modes

Only things actually hit. Add what you hit; delete what stops happening.

| Symptom | Cause | Action |
|---|---|---|
| `JAVA_HOME is set to an invalid directory: .../ai/toolchain/jdk-25` (Desktop) | `gradle.sh` took the Linux bootstrap path on Windows. Hit 2026-09-30, before it learned to hand over to `./gradlew`. | Check the `uname -s` branch at the top of `gradle.sh` still matches `MINGW*`. |
| `UnknownHostException: services.gradle.org` (Cowork) | Java does not read `HTTPS_PROXY`; the wrapper cannot reach the network. | `.claude/scripts/gradle.sh`, never `./gradlew`, in the sandbox. |
| `formatting: NEEDS APPLYING` (Cowork) | No delete permission this session; Spotless rewrites a file by unlinking it first. | Everything else still ran. Ask Mateusz to approve deletion, re-run. |
| `403 Forbidden` fetching a `.pom` (Cowork, Cloud) | Host not on the egress allowlist. | Report the host. Do not route around it. `jitpack.io` and `central.sonatype.com` blocked; Maven Central, plugin portal and foojay allowed. |
| `Could not read directory .../build/...` | Stale build output from another toolchain or a killed build. | `rm -rf */build ~/.pwt/out`, retry. |
| `Failed to run Gradle Worker Daemon`, often with a `java.security` read error (Cowork) | Sandbox memory pressure. | Retry; if it repeats lower `-Xmx` in `gradle.sh`. Do not raise `gradle.properties` - tuned for the desktop. |
| Spotless fails `build` with a diff wall | Files not yet formatted - the gdx-liftoff template used tabs. | `spotlessApply` first; `verify.sh` already does. |
| `cannot find symbol: com.badlogic.gdx.utils.ImmutableArray` | It is Ashley's, not libGDX's. | `com.badlogic.ashley.utils.ImmutableArray`. |
| A dependency's "latest" is a milestone (AssertJ resolved `4.0.0-M1`) | Maven metadata `<latest>` includes pre-releases. | Pin the newest stable in `gradle.properties`. |
| `ArchitectureTest` reports violations inside itself | Build outputs relocated, no longer matching `.../build/classes/java/test/`; ArchUnit's `DoNotIncludeTests` silently stops excluding test classes. | Keep the `/build/` segment in any relocated output path - `gradle.sh` does, in every branch. |
| `cp: ... .pwt/gradle-home ...: No space left on device` while seeding the cache (Cowork) | The device shell's `/sessions` disk, where `$HOME` lives, is shared with every earlier session and full. Hit 2026-09-29. `/tmp` is on another disk with room, and persists between calls. | `HOME=/tmp/pwthome .claude/scripts/verify.sh`. Every call must pass the same `HOME`, or the build starts cold. |
| Lombok `sun.misc.Unsafe` warning | Unavoidable on JDK 25. | Ignore; `verify.sh` filters it. |
| `Could not create service of type FileHasher ... java.io.IOException: Input/output error`, and `rm` on `.gradle/**` says `Permission denied` (Cowork) | IntelliJ or a Windows `gradlew` daemon holds `<project>/.gradle`'s lock files, which the sandbox VM may not touch. Reads like a corrupt cache; is not one. | Nothing to do: `gradle.sh` passes `--project-cache-dir`. If it reappears, check that flag is still there before deleting anything. |
| An input that is "just pressed" is ignored at random | A frame shorter than one tick runs **zero** simulation steps, so a one-frame input reaches no tick. Above 60 Hz that is most frames. | Latch it until a tick spends it, as `ScenarioSession.pendingOrder` does. Anything read with `isKeyJustPressed` and handed to `advance()` has this. |
| `error: warnings found and -Werror specified` | A real warning. Most often a javadoc orphaned by inserting a method *above* the one it documented. | Fix it. Do not disable the check - eight warnings accumulated in one session before anyone noticed. |
| `.claude/scripts/verify.sh: Permission denied` (Cloud) | The scripts were committed as `100644` from Windows, so a Linux clone cannot execute them. Hit 2026-09-26; now committed as `100755`, and `verify.sh` calls `gradle.sh` through `bash`. | Run `bash .claude/scripts/verify.sh`. The session-start hook also restores the bit. |
| `sandbox-helper: no Plan9 drive shares mounted under /mnt/.virtiofs-root/shared` (Cowork) | The device shell cannot mount the Windows drive at all. Hit 2026-09-11, attributed to a Windows update of 2026-09-08; fixed on Mateusz's side 2026-09-12. | Nothing in the build can run. Say so and fall back to the file tools - see below. |
| IntelliJ or the command line says `Unable to create '.git/index.lock': File exists. Another git process seems to be running` (Cowork) | **A `git` command run from the sandbox left its lock behind** - see *Reading git state*. Hit 2026-09-17. | Delete `.git/index.lock` (needs delete permission), then **always** `git --no-optional-locks ...` for anything read-only. |
| A `--tests 'Class.method'` run reports the whole class, and IntelliJ's `<project>/*/build` changes under it (Desktop) | `./gradlew` called directly: it builds into IntelliJ's output, not `~/.pwt/out` (ADR 0006). Hit 2026-11-11. Through `gradle.sh` the same filter runs the one method. | `.claude/scripts/gradle.sh`, never `./gradlew`, in every environment. |
| An `invariants/*.md` (or any doc) left empty or cut short (Desktop) | An inline Python or shell script that read a whole file and rewrote it failed between the two. Hit 2026-11-11; `DecisionRecordsTest.keepsATableOfInvariantsInEveryFileOfThem` now catches an empty invariants file. | Change docs with the Edit tool, one exact replacement at a time. A file clean at HEAD comes back with `git restore -- <path>` - see the friction below. |
| `too deeply nested to stage: N folders below the connected folder` (Cowork) | `device_stage_files` supports 7 folders; Java sources sit 8-9 deep. | Not workable around from here. Read the file another way or ask Mateusz to connect a deeper folder. |

## AFK runs

Mateusz hands over a set of tickets and walks away; an orchestrator session fans them out to agents, each
in its own worktree, and gathers them on one integration branch he merges when back. Desktop only.

**The decisions are the orchestrator's.** Grilling tickets and "agree, then plan" assume Mateusz answers;
in an AFK run nobody does. The orchestrator settles each open question in the agent's brief, every record
or invariant the run changes says *provisional, for review*, and the final message lists each such
decision with its alternative. A question that would break a pillar is not settled - that ticket is left
open, with the question written in it.

**Allocate before fanning out.** Decision-record numbers (`G00NN`) are handed out in the briefs, one block
per ticket, so two branches never claim the same one.

**Models.** Exploration and merger subagents get `model: "sonnet"`; implementers keep the session's model,
since theirs is the work Mateusz judges.

**Branches and where they live.**
- The integration branch is cut from main, its worktree at `.claude/worktrees/integration`.
- Each agent works in its own worktree under `.claude/worktrees/`, on its own branch, cut from the
  integration branch; it commits there and merges the integration tip in before it reports.
- The orchestrator merges each finished branch into integration with `git -C .claude/worktrees/integration
  merge`, resolves conflicts, runs `verify.sh` there alone, and reviews the whole branch at the end.
- Agents edit by absolute path inside their own worktree. In 2026-11-11's run one agent's test edits turned
  up in another's worktree, cause never found; absolute paths make the next one visible.

**What keeps it safe** - so the permission rules can allow git writes in any wording:
- `.githooks/reference-transaction`, copied into `.git/hooks` by the session-start hook, refuses any move
  of `main` from a Claude shell (`CLAUDECODE=1`) - commit, merge, fast-forward, reset - in every worktree.
- `.claude/hooks/git-guard.py` (PreToolUse) refuses push, `branch -D`, `worktree remove --force` and
  stash drops anywhere, and in the main checkout anything that discards working-tree changes or switches
  its branch. Its docstring lists exactly what.
- The auto-mode classifier needs the same picture: `.claude/auto-mode.user-settings.json` is the
  `autoMode` block for Mateusz's `~/.claude/settings.json` - the only place Claude Code reads it from.

## Cloud sessions

A Claude Code session on the web runs in a Linux container cloned fresh from GitHub: no Windows mount, no
IntelliJ, no display, and **thrown away** after a while idle. `CLAUDE_CODE_REMOTE=true` says you are in one.

- **The toolchain is provisioned before you start.** `.claude/hooks/session-start.sh` (SessionStart,
  cloud only) downloads JDK 25 and Gradle into `ai/toolchain` and compiles main and test sources, so the
  first `verify.sh` is a build rather than a two-minute download. Its log: `ai/logs/session-start.log`.
  If it failed, `verify.sh` simply redoes the bootstrap.
- **Commit and push, or lose the work.** `.claude/scripts/cloud-commit.sh -m "Subject" -m "trailer"...
  [-- path...]` stages (everything, or the paths given), commits and pushes to the current `claude/*`
  branch with retries. It refuses outside a cloud session, on any other branch, and with nothing staged;
  it never amends or forces. Push stays denied in `.claude/settings.json` for every session - the script
  is the one door to the remote, and its guards are what make it safe to leave open.
  Commit at each stage a change can stop after, not only at the end. Mateusz merges into main.
- **Several commits for several concerns**: pass paths after `--` to commit one concern at a time.
- **The game cannot run.** Level 5 of the ladder is still Mateusz's machine.

## When the mount is down (Cowork)

`device_list_dir`, `device_stage_files` and `device_commit_files` still work, but staging caps at
**7 folders** below the connected root and Java sources are 8-9 deep. Consequence, stated plainly:
**sources can be written but not read.** Check this early, before planning a session around reading
code. Docs, `CLAUDE.md`, `build.gradle` and everything in `.claude/` are shallow enough.

`.claude/` is blocked for `device_commit_files` specifically, not for the device shell: with the mount up,
`device_bash` writes there normally. Only when the mount is down must a skill or script edit be handed to
Mateusz as a file.

## Friction only Mateusz can remove

Named so it is not re-discovered every session. None of it blocks work; each is a cost paid repeatedly.

- **Delete permission resets every Cowork session**, and can drop mid-session when the bridge
  reconnects. `verify.sh` probes for it and without it compiles, lints and tests anyway, reporting
  formatting instead of applying it. It *is* a hard blocker for renaming or deleting a file. Spotless
  is the only thing in the whole build that deletes anything on the mount (verified by syscall trace,
  2026-09-07).
- **Computer-use access lapses between sessions.** Re-approve when Claude needs to run or look at the
  game.
- **Allowlisting `repo.maven.apache.org`** would allow plain `mavenCentral()` in Cowork; `api.foojay.io`
  would fix Gradle toolchain provisioning there.
- **Auto mode denies `git restore`** (Desktop), and `git show HEAD:<path> > <path>` with it, even for a
  file that is clean at HEAD. Closed 2026-11-11 for docs and tickets by `Bash(git restore -- docs/*)` and
  `Bash(git restore -- .scratch/*)` in `.claude/settings.json`; anywhere else Claude still hands it over.
- **In Cowork the repo lives on a Windows mount** - the ~175x write penalty above. Worked around in
  `gradle.sh`.

Checked and closed: nothing in the test suite needs Docker or Testcontainers, and nothing will - the
simulation has no external collaborators.
