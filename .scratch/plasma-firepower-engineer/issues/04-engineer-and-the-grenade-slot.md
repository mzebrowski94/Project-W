# Engineer and the grenade slot

Type: task
Status: open
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
