# Engineer and the grenade slot

Type: task
Status: resolved
Label: ready-for-agent
Blocked by: 03
Spec: [spec.md](../spec.md) C2-C3
G-record: **G0129** (exists)
Skills: `feature` (steps 1-3), then `/mattpocock-skills:implement` with `new-kind`

## What

The **Engineer** upgrade: while held, the grenade key sets a **sentry** (ticket 03) down at his feet for a grenade, and
the HUD's grenade slot shows a sentry.

## Settled (2026-11-24, not provisional)

- `sim.upgrade.engineer.*`: side player, **personal** (`chooser`), **2 ranks**, tier 1, weight 10, requires none,
  group 0, answers none.
- **While held**, the grenade key sets a sentry down **at his feet** (just in front of him, clear of his body by
  `sim.muzzleGap`) instead of throwing. Refused, spending nothing, where the ground there is solid (`Solid`).
- **It spends a grenade**: the same count and most (G0077), Bandolier raises it, a grenade cache refills it, Grenadier
  shortens the wait. No other cap on standing sentries.
- **Rank two**: the sentry's gun fires faster - a `fire_cooldown` effect reaching that gun alone (e.g.
  `gun sentry_gun fire_cooldown chooser -25`). If a gun effect on a sentry's gun cannot reach the chooser's sentries
  (its shooter is not his commander), make the sentry carry whose it is and the one place that resolves the reader do
  so - say what it took. Rank one's effect is what switches the key; how that is expressed (a count, a flag stat) is
  the implementer's, through `Modifiers`, never a branch on the upgrade's kind.
- The squad's grenades (Explosives, G0051) are untouched.
- **HUD**: the commander's grenade slot (`ReadoutWriter`, `PlayerReadout`, `PanelLayout`) shows a sentry icon instead
  of a grenade while he holds Engineer; the count is the same. `docs/ui/menu_layout_agent.md` if the slot is listed
  there. The draft card's text in `assets/i18n/`.

## Open for the implementer

- How rank one switches the key (see above) and the rank-two percentage.
- The sentry icon's look in `graphic.properties`.

## Read first

- `CLAUDE.md`; `docs/design/invariants/players.md`, `progression.md`; `docs/ui/menu_layout_agent.md`.
- G0049, G0051, G0077, G0108, G0129, and ticket 03's *Answer*.
- Grenadier, Bandolier and Rocketman in `upgrades.properties`, `Effect`, `Modifiers`, `Stat`, the grenade throw's system,
  `ReadoutWriter`, `PlayerReadout`.

## Done when

- Tests: under Engineer the key sets a sentry and spends a grenade, never with none left, never on solid ground; without
  it the grenade is thrown as before; rank two fires faster; personal - another player's key still throws; the readout
  names the sentry slot. `verify.sh` green.
- A hidden scenario (`--scenario=<name>`) with Engineer held at rank 1 (and one at rank 2, if cheap) and a pack coming.

## Answer

Built on `afk/pfe-04-engineer`, merged into `pfe-integration` (green but for the rift seeds).

**What.** `UpgradeKind.ENGINEER` and its block in `upgrades.properties`; a stat `Stat.SETS_SENTRIES` read in
`Modifiers.setsSentries`, whose one caller is `Throws.setsSentries` - the press and the panel both go through it.
`Throws.release` sets a sentry down just in front of him along the aim (his radius + the sentry's + `sim.muzzleGap`)
instead of throwing, and pays for it in the one `pay` a grenade uses (count, wait, Grenadier). Refused, spending
nothing, where `Solid.isClearPath` from his middle to the spot at the sentry's width is not clear. `Scope`: `chooser`
reaches a sentry whose `Sentry.slot` is the chooser's; a sentry's reads are folded each time, as a commander's are.
HUD: `PlayerReadout.sentries`, `TallyIcon.SENTRY`/`SENTRY_SPARE`, drawn in `graphic.color.sentry` in place of the
grenade pips. Tests: `EngineerTest` (8), `ReadoutWriterTest.namesTheSentrySlotWhileHeHoldsEngineer`.

**Provisional decisions** (also in G0129 *Amended by*):
- Rank one switches the key by a stat (`sets_sentries`, a count, zero off). Turned down: a branch on the kind; a flag on
  `Thrower` written by `UpgradeSystem` (a second copy of the ledger).
- Rank two: `gun sentry_gun fire_cooldown chooser -20`; a rank folds linearly, so the gun's base went 0.2 -> 0.25 s, giving
  0.2 at rank one (ticket 03's rate) and 0.15 at rank two. Turned down: a "rank two only" effect form.
- `chooser` alone reaches a sentry; `team`/`squad`/`commanders` never do. Turned down: `team` reaching it (Weapon
  Handling and every team gun effect would land on it). Its reads fold each time: a per-slot cache sized by
  `Players.MAX` is what `CeilingTest` refuses.
- "Solid ground" read as a clear path at the sentry's width, so none stands half in rock.
- The icon: a square with its gun up, in the existing `graphic.color.sentry` - no new key.

**Keys.** `upgrades.properties`: `sim.upgrade.engineer.side=player`, `weight=10`, `ranks=2`, `tier=1`, `requires=none`,
`group=0`, `answers=none`, `effects=count sets_sentries chooser 1; gun sentry_gun fire_cooldown chooser -20`;
`sim.upgrade.stat.sets_sentries.minPercent=0`, `maxPercent=0`, `min=0`. `weapons.properties`:
`sim.weapon.sentry_gun.cooldownSeconds` 0.2 -> **0.25**. `i18n/ui.properties`: `ui.upgrade.ENGINEER`,
`ui.effect.count.SETS_SENTRIES`.

**Scenarios:** `--scenario=engineer-yard` (Engineer I, every seat) and `--scenario=engineer-yard-ii` (Engineer II): an
outcrop to press the key into, two grenade caches, and a pack from the east in four lots.
