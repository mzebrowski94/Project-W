# A build version in the artifact and the menu

Type: task
Status: open
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
