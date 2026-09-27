# Packaging a Windows build

The game ships as a zip: `Project-W.exe`, the game jar and a trimmed Java 25 runtime. Whoever
unzips it double-clicks the exe; nobody needs Java installed. The packager is
[Construo](https://github.com/fourlastor-alexandria/construo), configured in `lwjgl3/build.gradle`.

## Build it

On Windows, from the project folder (PowerShell or cmd):

```
.\gradlew lwjgl3:packageWinX64
```

Result: `dist\Project-W-winX64.zip` (about 55 MB). Inside:

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
| New release | `projectVersion` in `gradle.properties`. The jar name follows it. |
| New Java update | `packagedJdkVersion` in `gradle.properties` - an exact Temurin 25 release, e.g. `25.0.4.1+1`. Keep the major at 25 while `options.release` is 25. |
| New library | Re-run `jdeps --multi-release 25 --ignore-missing-deps --list-deps <game jar>` and add any new module to `jlink.modules`. A missing module fails only in the packaged game, with `NoClassDefFoundError`. |
| Memory | `-Xmx1G` under `roast.vmArgs`. |
| Icon | `lwjgl3/icons/logo.png` (the exe) and the `libgdx*.png` in `lwjgl3/src/main/resources` (the window). |

## Not done yet

- **Not signed.** Windows SmartScreen shows "Windows protected your PC" on first run; *More info ->
  Run anyway*. A code-signing certificate removes that.
- **No installer.** A zip is enough for playtests. For an installer, feed the unzipped folder to
  Inno Setup.
