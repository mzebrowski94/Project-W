# `--scenario`, Full House and the commander colours

Type: task
Status: claimed
Label: ready-for-agent
Blocked by: none
Spec: [spec.md](../spec.md) sections 2 and 3
G-records: none (tooling and a colour); write the scenario flag into `docs/design/alpha-phase.md`

## What

Three small things, one session:
- bring the Full House scenario onto the integration branch;
- add a launch argument that opens any scenario directly, while the menus keep scenarios hidden;
- make the first commander's colour clearly different from his troopers'.

Ticket 01 goes first: every later ticket leaves a hidden scenario that Mateusz opens with this flag.

## Settled (2026-11-22, not provisional)

- **Full House** is commit `6a1bdcc` on `claude/keen-carson-rfz9ts` ("Added Full House scenario with a
  commander in every slot").
  - Cherry-pick **that commit only**.
  - **Do not** take `de972c0`, which flips `graphic.ui.page.scenarios` to `true`. Scenarios stay hidden in
    the campaign and host menus.
  - `6a1bdcc` touches four files:
    - `sim/.../scenario/FullHouseScenario.java` (new);
    - `Scenarios.ALL`;
    - `Scenario.java` (javadoc only);
    - `ScenariosTest`, whose check becomes "a commander in slot 0 and no slot twice";
    - plus one sentence in `docs/design/alpha-phase.md`.
  - `main` has moved two relay/net commits since, so expect no conflict.
- **`--scenario=<name>`**:
  - Matches a registered scenario's `name()`, ignoring case, spaces, hyphens and underscores. So
    `--scenario=full-house`, `--scenario=FullHouse` and `--scenario="Full House"` all open Full House.
  - **Unknown name:** a logged problem that lists every valid name in its loose form, and the game opens on
    the menu as without the flag.
  - **With `--mission` too:** the mission wins and the scenario is a logged problem.
  - The scenario opens as the menu would open it, returning to `UiPage.CAMPAIGN` on leaving.
- **Colours** in `assets/graphic.properties`:
  - `graphic.color.commander=ffd76eff` (gold, was `6ee7ffff`);
  - `graphic.color.commander.1=eef4ffff` (white, was `ffd76eff`);
  - the rest unchanged;
  - `graphic.color.trooper=4fb8d8ff` stays.
  - Rewrite the two comments: the one on the commander, and the trooper's "kin to the commander's blue",
    which is no longer true. Every commander now contrasts with every squad.

## Read first

- `CLAUDE.md`, `docs/design/alpha-phase.md` (*Adding a scenario*).
- `core/src/main/java/pl/mzebrows/pwt/client/LaunchOptions.java`, its test `LaunchOptionsTest`.
- `core/.../client/PwtGame.java` `create()` (about lines 85-106): the `--mission` path is the template.
- `sim/.../scenario/Scenarios.java` (`named`, about line 84).
- `lwjgl3/.../Lwjgl3Launcher.java`.
- `assets/graphic.properties` lines 1-30.

## Build

1. `git cherry-pick 6a1bdcc` onto the ticket branch. Run `ScenariosTest`.
2. **Lookup.** Add one place that resolves a loose name, for example `Scenarios.looselyNamed(String)` or a
   private helper in `PwtGame`.
   - It normalises `name()` and the argument the same way, and matches only the registered `ALL`.
   - It never resolves the `mission <tier>` form or the proving ground.
   - Leave `Scenarios.named` exact: an online session names scenarios by it.
3. **`LaunchOptions`** gains a `@Nullable String scenario` component, parsed from `--scenario=`.
   - An empty value is a problem.
   - Keep `LaunchOptions` free of `sim` lookups if it is today. If so, the name is resolved in
     `PwtGame.create()`, and an unknown name adds a problem and is logged there.
4. **`PwtGame.create()`**:
   - if a mission is given, as today, plus a problem if a scenario is given too;
   - else, if a scenario resolves, `new ScenarioScreen(this, new ScenarioSession(scenario, simConfig),
     UiPage.CAMPAIGN)`;
   - else the menu.
5. **Colours and comments** as settled.
6. **Docs.** Write a paragraph in `docs/design/alpha-phase.md`: *Opening a scenario directly*.
   - The flag, the loose match.
   - That the scenario list in the menus stays off.
   - The command line: `./gradlew lwjgl3:run --args="--scenario=full-house"`. Check that `lwjgl3/build.gradle`'s
     `run` passes `--args` through, and say how if it does not.
   - The equivalent IntelliJ run configuration in prose. No `.idea` file is committed.

## Tests

- `LaunchOptionsTest`:
  - `--scenario=full-house` is kept;
  - `--scenario=` is a problem;
  - `--scenario` beside `--mission` keeps the mission and records a problem.
- A test of the lookup:
  - every registered scenario is found by its own name lower-cased with hyphens for spaces;
  - an unknown name is not found;
  - `mission 1` is not found by the loose lookup.

## Judge by

```
./gradlew lwjgl3:run --args="--scenario=full-house"
```

That shows eight commanders, with gold and white the first two.

## Open for the implementer (provisional, for review)

- Where the lookup lives (`Scenarios` or `core`).
- The exact problem wording.

## Done when

- Full House is on the branch, with the menus still showing no scenarios.
- `--scenario` opens any registered scenario by its loose name, and refuses an unknown one with the list.
- Slot 0 is gold and slot 1 is white.
- `alpha-phase.md` says how to open a scenario directly.
- `verify.sh` is green and the branch is committed.
- The final message names the colour keys, with lines, so Mateusz can retune them himself.
