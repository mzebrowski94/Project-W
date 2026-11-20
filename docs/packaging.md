# Packaging a Windows build

The game ships as a zip: `Project-W.exe`, the game jar and a trimmed Java 25 runtime. Whoever
unzips it double-clicks the exe; nobody needs Java installed. The packager is
[Construo](https://github.com/fourlastor-alexandria/construo), configured in `lwjgl3/build.gradle`.

## Build it

On Windows, from the project folder (PowerShell or cmd):

```
.\gradlew lwjgl3:packageWinX64
```

Result: `dist\Project-W-<build version>-winX64.zip` (about 55 MB), as in
`Project-W-2026.11.13-b412b68-winX64.zip`. The **build version** is the date and short hash of the commit
the zip was built from, with `-dirty` when the tree held anything uncommitted; the build works it out
(`lwjgl3/build.gradle`), and the main menu shows the same words in its bottom right corner, so a playtester
can say which build he has. `.\gradlew lwjgl3:printBuildVersion` prints it without building. Inside:

```
Project-W.exe          the launcher - this is what you run
Project-W-1.0.0.jar    the game, assets included
app\Project-W.json     launcher settings: main class, JVM options
runtime\               Java 25, only the modules the game uses
```

The first run downloads the Temurin 25 JDK for Windows (~140 MB) and the launcher; later runs
reuse them, so a rebuild takes seconds.

## Test it before sending it

1. Unzip into a fresh folder, ideally one whose path has a space or a Polish letter in it.
2. Double-click `Project-W.exe`. The game should open with no console window.
3. Host (`H`). Windows Firewall now asks about **Project-W**, not Java - allow it on the network
   type your adapter uses (Hamachi's is usually *Public*), as in `docs/workflow.md`.
4. Connection logs land in `logs\net\` beside the exe.

If it closes without a word, set `useConsole.set(true)` in the `winX64` target, rebuild, and run it
again: the error is printed in the console window.

## Must be built on Windows

Temurin 24 and later ship no `jmods` folder (JEP 493), so `jlink` can only copy the JDK that runs
Gradle. Built on Linux, the Windows zip gets Linux libraries and an exe that does not start. The
build refuses: `downloadJdkWinX64 must run on windows`. The same holds for the Linux and macOS
targets - `packageLinuxX64` on Linux, `packageMacM1` / `packageMacX64` on a Mac. This also means
Claude's sandbox cannot produce the Windows zip; `./gradlew` on the Windows side can.

## Things to change as the game grows

| When | Change |
|---|---|
| New release | Nothing: the zip's name and the menu carry the build version, from the commit. `projectVersion` in `gradle.properties` names only the jar inside. |
| New Java update | `packagedJdkVersion` in `gradle.properties` - an exact Temurin 25 release, e.g. `25.0.4.1+1`. Keep the major at 25 while `options.release` is 25. |
| New library | Re-run `jdeps --multi-release 25 --ignore-missing-deps --list-deps <game jar>` and add any new module to `jlink.modules`. A missing module fails only in the packaged game, with `NoClassDefFoundError`. |
| Memory | `-Xmx1G` under `roast.vmArgs`. |
| Icon | `lwjgl3/icons/logo.png` (the exe) and the `libgdx*.png` in `lwjgl3/src/main/resources` (the window). |

## The relay as a jar

The relay (`relay` module) is a headless process; one jar carries it with everything it needs:

```
.\gradlew :relay:relayJar
```

Result: `pwt-relay.jar` under `relay/build/libs/` (in this checkout's build output - `.claude/scripts/gradle.sh` redirects
it to `~/.pwt/out/`). Copy it to the server and run it there with Java 25:

```
java -jar pwt-relay.jar [overrides.properties] [port]
```

Both arguments are optional and may come in either order; a bare number is the port and beats `net.relay.port`.

The optional file holds `key=value` lines of `assets/network.properties` to override (`net.relay.port`, the limits, `net.log.dir`);
nothing else needs shipping. Open the port - 24741 by default - for **TCP and UDP**. It prints a banner and echoes what rooms
and links do to the console; the full log is `logs/net/relay-<time>.log` beside where it was started. The jar's build id must
match the players' builds - rebuild and redeploy it with each game build that changes `map`, `sim` or the session protocol.

The console shows every link's handshake steps too (`cat=link`: `accepted`, `udp-bound`, `closed reason=...`). A client
that says THE RELAY CANNOT BE REACHED while the relay console shows no `cat=link event=accepted` never got a TCP
connection through (host, port or a firewall); one with `accepted` and then `closed reason="timeout: udp unreachable"`
got TCP through but not UDP - open the port for UDP as well. The game's own console prints the client's side of the same
steps, and the reason its link closed.

### Diagnostics over HTTP

Set `net.relay.diagnosticPort` (zero, as shipped, is off) in the overrides file - `net.relay.diagnosticPort=24742` - and
open that TCP port to yourself only: it names the addresses of the machines that connected. Then, from curl or Postman:

| Request | What it says |
|---|---|
| `GET http://<relay>:24742/status` | build, ports, uptime, the rooms and links open now, and `you` - your address as the relay saw it |
| `GET http://<relay>:24742/log?lines=200` | the tail of this run's log file, handshake steps and close reasons included |
| `POST http://<relay>:24742/probe` | the relay dials its own door on `127.0.0.1`, opens a room and joins it from a second link, and says how far each step got and in how many ms; `?host=<address>` dials another address instead, e.g. the server's public one |

A probe's room closes by itself after `net.rejoin.windowSeconds`, and counts toward `net.relay.roomsPerAddressMax` for
`127.0.0.1` until it does.

## Not done yet

- **Not signed.** Windows SmartScreen shows "Windows protected your PC" on first run; *More info ->
  Run anyway*. A code-signing certificate removes that.
- **No installer.** A zip is enough for playtests. For an installer, feed the unzipped folder to
  Inno Setup.
