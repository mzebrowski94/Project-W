# A build version in the artifact and the menu

Type: task
Status: resolved
Blocked by: -
Section: General (last, by request)

## Question

Stamp a version into the packaged artifact's name (`./gradlew lwjgl3:packageWinX64`) and show it in a bottom
corner of the main menu, decided at build time so nobody has to keep it up to date.

## From Mateusz (translated)

> It would help to add some versioning to the name of the built artifact, and also to the UI in the bottom
> corner of the main menu - ideally decided only at build time, so it does not need watching.

## Read first

`/mattpocock-skills:implement`.

`lwjgl3/build.gradle` (the package task), the **build id** generation (`docs/glossary.md` *Online play*) - the
same build step may carry the version - `docs/ui/menu_layout_agent.md` (the main page), `GameProperties`.

## Open

What the version is made of - a date and short git hash, a tag with `git describe`, or a counter - and what a
dirty tree shows. Not the build id, which hashes the sources and means something else.

## Done when

The artifact's name and the menu both show it, `verify.sh` green.

## Answer

Resolved 2026-11-13, in an AFK session - the open point settled by Claude, **provisional, for review**.

**The build version is the commit's date and short hash**, `2026.11.13-b412b68`, with `-dirty` after it when
the tree held anything uncommitted (untracked files count; ignored ones do not) and `dev` where there is no
git to ask or the copy was never stamped. Against the alternatives: `git describe` needs tags somebody must
remember to make, and a counter needs somewhere to keep it - both are things to watch. The commit's date,
not the build's, so the same commit is the same version on every machine.

- `lwjgl3/build.gradle` works it out lazily (`generateBuildVersion`; `printBuildVersion` to read it) and
  stamps `pwt-version.properties` into the launcher's resources - there rather than in `core`, so a new
  commit rebuilds the launcher's resources and no module's tests.
- The zip is `dist/Project-W-<build version>-winX64.zip`, every target the same way. The jar inside keeps
  `projectVersion`.
- `BuildVersion` (`client.config`) reads the stamp; `MenuScreen` draws `ui.main.version = BUILD {0}` dim in
  the bottom right corner of `UI:main`.
- Written down: glossary *build version*, `docs/ui/menu_layout_agent.md` (`UI:main`), `docs/packaging.md`.

Not run: `lwjgl3:packageWinX64` itself, and the look of the corner - Mateusz's.
