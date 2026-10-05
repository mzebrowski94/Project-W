# The mission seed and its code

Type: grilling
Status: resolved

## Question

How (UTC date, tier) becomes a mission seed, and how a seed and tier become the short code and back:
alphabet, length, where tier sits, checksum or none, what a mistyped code does. Where each lives
(`core` reads the date; `sim` sees only the seed). How the mission names itself to `Scenarios` and to
`SessionSetup` so a join builds the same one.

## Answer

Grilled 2026-10-05 (G0065). Settled before the session: one mission seed builds everything; daily = UTC date +
tier, read only in `core`; the code is about 8 characters, has no 0/O/1/I and includes the tier; no
compatibility across builds.

**The seed** is a number in [0, 25^6) - about 244 million a tier - and it is the simulation's seed
(`SessionSetup.seed`, `config.withSeed`). `sim.seed` is not read for a mission.

**The tier never moves the map.** The seed alone deals the order, lays the ground, the ridges and the finale, and
chooses where things stand; the tier moves numbers only (G0063). One seed at another tier is the same mission at
another strength. Per-concern streams keep it so: hard's bigger counts draw from roster streams, not the ground's.

**Streams** - the question 03 left open, answered in `Randomness`:

- `Randomness` gains an abstract `derive(long salt)`. `Rng` implements it from its **current state without
  advancing**, with the mixing `Rng.stream` uses; `Rng.stream(seed, concern)` is rewritten on it and must give
  today's values exactly, so every existing scenario lays out as it does now. `map` still holds no generator.
  `Randomness` stops being a lambda type; the map tests that write one get a small test class.
- **The tree.** `Strip` takes the MAP stream and derives per biome **position**, then per coat, plus a stream per
  border for its ridge and passes. `sim`'s build-time draws come from one new `RngStream.MISSION` concern, derived
  per sub-concern (order, finale kind and flip, rosters, placement) and per position. Keyed by position, never by
  biome kind.
- **Rules.** A stream either derives or draws, never both; no two call sites derive under one salt path. Derived
  streams live only during the build - anything drawn from the first tick is a `Streams` concern, so a snapshot
  carries it (ADR 0014). `MISSION` itself sits in `Streams` like every concern (one more long a snapshot); its
  children never do.
- **Tested by the property**, not the javadoc: retuning one coat or roster leaves every other biome identical, and
  a restored world equals a fresh one. Written down in `docs/determinism.md`.

**The code** - `T SSSSSS C`, eight characters, shown `HK3X-Q7RA`:

- **T** the tier's letter: the first letter of the `TierKind` name (E/M/H) - no switch over kinds (G0008); a test
  asserts each is in the alphabet and no two tiers share one.
- **SSSSSS** the mission seed in base 25.
- **C** a check character over the seven before it: a weighted sum mod 25 with weights not divisible by 5
  (1 2 3 4 6 7 8), so every single wrong character and every swapped neighbour pair is caught. Editing the tier
  letter by hand fails it too.
- The **room code**'s alphabet (`ACDEFGHJKMNPQRTUVWXY34679`) and its forgiving reading - any case, spaces and
  dashes ignored - lifted into one public static helper on `RoomCode` and used, not copied.
- **A mistyped code is refused** on ENTER CODE ("not a mission code"); never corrected, never played.

**Where each lives**

| Where | What |
|---|---|
| `core` | `MissionCode` beside `Invitation`: tier + seed to text and back, the check, and `daily(epochDay, tier)` - mix of the day and a tier salt, mod 25^6 - a pure function. The date: `LocalDate.now(clock).toEpochDay()` with an injected UTC `Clock`, read when the DAILY page opens; a daily keeps the day it was started on, so its ✓ is filed under that day (09). RANDOM draws a seed uniformly from [0, 25^6) with the client's own randomness; the player picks the tier, the page's widget is 08's. |
| `sim` | `Scenarios.mission(TierKind)` builds the mission's `Scenario`, outside the menu registry as `provingGround` is; its `configure` is `MissionConfig.apply(tier)`. `Scenarios.named` looks in the registry first, then takes the mission form. Sees only tier and seed. |
| `net` | Nothing new: `SessionSetup(configText, "mission hard", missionSeed)`. RETRY is the same setup. The lobby shows `mission hard` - the announcement does not carry the seed. |
| `ui` | Shows the code as a string it is handed; `CodePicker` already takes any alphabet. |

**Out** - codes surviving a build change (F); offering "this mission on another tier" from the result screen (08
if wanted - the code then needs its check recomputed, never a letter swapped); the lobby showing the code.
