# G0113: An emplacement fires a fixed cycle, and the fen has spires

**Status.** accepted

**Trigger.** A standing shooter nobody crews - the fen's spire now, the desert relic's warden next (ticket 09, G0114) -
and where a fen stands its spires.

**Enforced by.** `EmplacementTest` (one step a period along the next bearing, round the cycle and back to its start,
idle and holding its place with nobody near, its shot wounds a commander and passes an alien, never an enemy to clear
nor a trooper's target, a volley fires every bearing together once something wakes it); `CycleDefTest` (one seed one
cycle, others others; four to six different compass points one a step; a volley one step; a bearing that is no compass
point refused); `MissionTest.standsItsSpiresInEveryFenAndNowhereElseEachWithTheCycleItsSeedDeals`,
`.spawnsAFensSpiresAsEmplacements`; `MissionSweepTest.holdsEveryMissionToTheRules` through
`MissionFaults.emplacements`; `SpireFenScenarioTest`; `SystemOrderTest`.

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 9, and ticket 08). The points
under *Provisional* are the implementer's, taken in an AFK run, for review.

**Amends G0068** - the order: emplacements after the geysers.

**Constraint.**

- **An emplacement** is a standing shooter nobody crews. Its kinds are a definition table, `EmplacementKind` and
  `EmplacementDef`, a `sim.emplacement.<kind>` block each (ADR 0008): `radius`, `look`, `gun`, `periodSeconds`,
  `wakeRadius` and `cycle`. One component, `Emplacement`, and one system, `EmplacementSystem`, for every kind; a
  warden is one more constant and block.
- **Its cycle** is either *dealt* - `cycle=dealt 4 6`, that many different compass points, dealt when the level is
  built from the placing stream and fired one a step - or a *volley* - `cycle=volley 0,90,180,270`, every bearing named
  fired together every step. The cycle is held on the entity, packed into one number a snapshot writes (ADR 0014), and
  **never dealt again**: no `Rng` after placement.
- **It fires** one shot of its gun along each bearing of its current step, through `Guns.volley`, every
  `periodSeconds`, then moves to the next step. **Unaimed**: it never turns to anybody.
- **It wakes** while a man on his feet stands within `wakeRadius`, asked of a `SpatialGrid`; asleep, its clock and its
  cycle stand still. A `wakeRadius` of zero is woken by something else - a warden by its relic (ticket 09).
- **Its body and side.** Solid, immovable, an `Obstacle`, and **unbreakable**: no `Health`, so shots and blasts stop
  on it and nothing kills it. On the **aliens' side**, so its shots pass every alien and hurt only troopers and
  commanders. **Never an enemy**: it carries no `Enemy`, which is the one place each of "counted by `CLEAR_ENEMIES`",
  "a trooper's or a turret's target", "a mine's trigger" and "a nest-like threat" is asked - so no field and no branch
  says so; it is simply not one. Aliens never hunt it either: perception's sides need `Health` or `Vitality`.
- **`EmplacementSystem`** sits beside `BarrageSystem` and `SlingSystem`, after the solver and the death chain, before
  the ledger changes (`docs/simulation-order.md`).

**The spire.** The fen's, `sim.biome.fen.furniture.spires=3 5`, and no other biome's (`0 0`). `radius=1.4`,
`periodSeconds=1`, `wakeRadius=45`, `cycle=dealt 4 6`, gun `spire_shot`: `projectileRadius=0.15` (half the sidearm's),
`projectileRange=30`, `projectileSpeed=25`, `harm=wound`. A placement pass after the geysers: as a boulder stands -
`rockMargin` off rock and everything already standing - `emplacementKeepOff` off every way in (the start's
`startKeepOff` in a first biome), every nest and the finale's watch, and off every site's edge; never on a thicket's
tile, so never in a blob's middle; mire is fine. Its cycle is dealt from the pass's attempt stream as it goes down.
Drawn as a mossy grey-green disc (`graphic.color.spire`, not the frog's green) under a lighter cap, its bead pale green.

**Why unaimed fixed cycles.** A cycle that never changes can be watched, learnt and walked through - the threat is a
pattern on the field, read by eye (pillar 5), and beaten by timing (pillar 1). An aimed spire would be a turret that
cannot be killed; a cycle re-dealt during play could never be learnt.

**Why alien-side but not an enemy.** Its shots must hurt only the men (Mateusz), which the side gives for free through
the one friendly-fire rule. But a thing nobody can kill cannot be something to clear, or `CLEAR_ENEMIES` could never be
met and every trooper would empty his gun into it.

**Why unbreakable.** A spire is ground to cross, not a fight: shooting it down would turn the pattern back into a
damage race, and a squad would stop for it.

**Provisional, for review (AFK run).**

1. **The cycle grammar:** `dealt <fewest> <most>` and `volley <bearings>` with the bearings separated by **commas**
   (`volley 0,90,180,270`), because a record of one argument is read from the rest of the line and a whole-number list
   is comma-separated in every config here. Turned down: spaces (`volley 0 90 180 270`), which would need a reading
   of its own in `ConfigBinder`.
2. **A dealt cycle's bearings are all different**, a partial shuffle of the eight. Turned down: repeats allowed, which
   deals cycles like N, N, N, E that read as broken.
3. **Bearings are the eight compass points**, a volley's too (a multiple of 45 degrees or refused), so one packed form
   serves both. Turned down: free angles for a volley.
4. **The next-bearing tell**: a short tick on the spire's edge at each bearing its next step fires along, always shown,
   read off `Emplacement` by the renderer (`graphic.spire.*`). Turned down: no tell; a tell only while awake; turning a
   `Facing` towards it (a second writer of `Facing`).
5. **Woken by a man on his feet** - a casualty or a body in its throes wakes nothing. Turned down: any man on the field.
6. **Asleep, its clock stands still too**, so the first shot comes a whole period after waking. Turned down: a clock
   that runs while asleep, so a waking spire may fire at once.
7. **Keep-offs:** one key, `sim.mission.placement.emplacementKeepOff=15`, off ways in, nests, the finale's watch and
   every site's edge (so 21 from a control point's middle). Turned down: its wake radius (45), which leaves a fen no
   room for three to five; its reach (30), for the same reason.
8. **Alien shots pass a spire** as they pass any alien - the friendly rule, unchanged. Turned down: a spire that stops
   every shot, which would need an exception in `CollisionSystem.friendly`.
9. **Spire Fen's cycles are laid by hand** (four, five and six bearings), so the scenario is the same every time it is
   judged; a mission deals them. Turned down: dealing them in the scenario, which would start a stream outside the
   stream table (`ArchitectureTest.onlyTheStreamTableStartsARandomStream`).
10. **`EmplacementSystem` in `system/level`**, with the other placed things on clocks of their own (nests, sites).
11. **No ADR 0024**: `EmplacementKind` is one more `Defs` table on ADR 0008's pattern, as `WorkedSiteKind` was (G0111),
    not a new structure. Turned down: recording it as an ADR.
12. **The bead's damage** is the sidearm's 50, which reaches only something neither alien nor man - a turret, a barrel;
    a man takes the wound whatever the number.
