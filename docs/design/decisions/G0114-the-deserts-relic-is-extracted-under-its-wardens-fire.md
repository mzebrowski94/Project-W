# G0114: The desert's relic is extracted under its wardens' fire, and its taking wakes snakes

**Status.** accepted

**Trigger.** The desert's site; a worked site laid in the open, or one with guards round it or something that comes up
when it is taken; a warden; whether the squad works a site of its own accord.

**Enforced by.** `RelicTest` (four wardens on its diagonals guarding it by handle; taken on the key held from out of
every lane, untouched; offered from inside the ring; wardens silent until its first attempt, then all four firing
north, east, south and west every period while nobody works it, and silent for good once it is taken; a warden's shot
wounds a commander and passes an alien; the plinth stays and two or three snakes come up outside the ring, off the
men; left to the commanders by the squad; its rouse twice a point's on the shipped numbers); `RelicScenarioTest`;
`MissionTest.laysOneOrTwoRelicsAmongTheSitesOfEveryDesertAndNoneElsewhere`, `.spawnsADesertsRelicWithItsFourWardensSilent`;
`MissionSweepTest.holdsEveryMissionToTheRules` through `MissionFaults.sites` (the relic count) and
`MissionFaults.relics` (its room open base ground, nothing else in it, its plinth reachable from inside its wardens);
`PromptsTest` (`HOLD [E] EXTRACT`, then `EXTRACTING…`); `ArchitectureTest` (`docs/components.md`'s writers).

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 10, and ticket 09). The points
under *Provisional* are the implementer's, taken in an AFK run, for review.

**Amends G0068** - the order: a biome's worked sites in the open after those in its houses, before its control points.
**Amends G0111** - a worked site may stand in the open in a room of its own, have guards and a hatch, and be left to
the commanders by the squad. **Amends G0113** - a warden is an emplacement its guarded site wakes and silences.

**Constraint.**

- **The relic** is a worked site, `WorkedSiteKind.RELIC`, one more row of G0111's table (`sim.site.relic.*`), with
  its own action, `ActionKind.EXTRACT`: `HOLD [E] EXTRACT`, then `EXTRACTING…`. Its body is the **plinth** - solid,
  immovable, unbreakable, radius 1.5 - which stays once it is taken; the glowing square turning over it is only its
  look, and is gone once it is taken. Extracting is downloading: 6 s held at arm's length, lost on letting go, a step
  away, a dash or a wound, each fresh attempt rousing once, taken through `Sites.taken`.
- **What G0111's table gained**, each off for the mainframe (zero means off): `room` - the open ground round its
  middle where it is laid in the open, which its guards stand inside and what its taking leaves and hatches lies
  outside; `guards` - an emplacement kind put down on each of its four diagonals `offset` from its middle by
  `Spawner.spawnWorkedSite`, so a mission and a scenario stand the same ring; `hatch` - a strain, a fewest and a
  most, and a radius, put down by `Brood.hatch` the tick it is taken; and `troopersWork`. One system still,
  `WorkedSiteSystem`; no copy of it and no branch on the kind.
- **Why the rouse is wider.** `rouseRadius=116`, twice a point's `aggroRadius`: a mainframe is in a house, a relic
  in open sand, and a site you work under fire is one you should not be able to work in peace. Every attempt calls
  the desert in.
- **The wardens and their cross-hatch.** `EmplacementKind.WARDEN`: radius 1.2 (an oval drawn, a circle in the solver,
  ADR 0007), a `volley 0,90,180,270` every 0.75 s, `wakeRadius=0`, gun `warden_shot` (the spire's bead, speed 26,
  range 22, a wound). On the relic's diagonals 5 from its middle, each line runs 3.5 off the plinth's middle, north to
  south and east to west, so the lines cross-hatch the ring in lanes. Shots pass the other wardens and every alien
  (G0113, decision 8) and miss the plinth. Square on a compass point, just outside the ring, a man is out of every lane
  and still within arm's length of the plinth: the place to extract from untouched, found by watching the beads
  (pillar 5). Between plinth and warden, and between two wardens (4.7 clear), a commander fits.
- **Woken by the first attempt, silenced by the taking.** A warden carries its relic's handle (`Emplacement.guards`,
  ADR 0016). The tick the relic's work goes from none to some, `WorkedSiteSystem` wakes every emplacement that names
  it; they fire from then on whether anybody works it or not; the tick it is taken, it puts them to sleep for good.
  `WorkedSiteSystem` (38) runs before `EmplacementSystem` (54), so a warden woken this tick starts its clock this
  tick (its first volley a period later, G0113 decision 6) and one silenced this tick fires nothing on it.
- **Snakes.** Taken, it lays what any taken site lays, then two or three `SNAKE` come up within 14 of it, outside its
  room and `sim.nest.hatchKeepOff` off every man, through `Brood` (G0074). They are ordinary snakes.
- **Placement.** `sim.biome.desert.furniture.relics=1 2` (`0 0` everywhere else, at most `sitesMin`), among the
  biome's 2-3 sites: each in a band of its own, in a point's place, so a desert dealt two relics and two sites has no
  point.

**Provisional, for review (AFK run).**

1. **Troopers do not extract a relic** (`sim.site.relic.troopersWork=false`): the squad passes it over for the next
   site, and the commander extracts it. Turned down: troopers extracting it as they download a mainframe, as the
   ticket proposed by analogy - measured in the Relic scenario, a squad of nine sent to it stood round it at arm's
   length, in the lanes, and was all down within twelve seconds of its first attempt; the squad is the resource
   (pillar 3), and losing it to a decision the player never took is not a fight. The better answer - troopers working
   it from the four out-of-lane spots and keeping their watch outside the ring - is new trooper behaviour, left open.
2. **The ring's room**: `room=7`, the wardens' outer edge (6.2) and a little; placement anchors a relic only where
   every tile within the room of anywhere on its tile is the biome's base ground (sand: no mud, no rock), not kept (no
   pass, apron or start), off every way in and the finale's watch, and `pointApart` off every other site - then every
   later pass keeps off it by its room as by a body. Turned down: reserving only the plinth and the four warden
   bodies, which let a stone stand between them; a flat check on the anchor tile, which let mud into the ring.
3. **Which bands** a relic takes are dealt from the biome's own stream (the same on every attempt and tier), never the
   last biome's last band - the approach to the finale - so a last desert with two sites has one relic. Where in its
   band, the attempt's stream.
4. **Numbers:** warden offset 5 along the diagonal, body 1.2, period 0.75; bead speed 26, range 22 (a lane reaches
   about 18 past the far warden); plinth 1.5; hatch radius 14; room 7. Key names generalised from the ticket's:
   `guards.kind/offset` for `wardenOffset`, `hatch.strain/min/max/radius` for `snakesMin/snakesMax/hatchRadius`,
   since they sit on the definition every worked site has.
5. **What a taken relic leaves lies outside its room** (`Sites` reserves a worked site's room, not only its body), so
   no crate or cache lands among the wardens. Turned down: its body only, as for the mainframe.
6. **A guard fires a fixed volley** - refused at load otherwise (`WorkedSiteDef.checkGuards`), since nothing deals a
   cycle when a site is put down; and its room must hold its guards.
7. **Look:** plinth `graphic.color.relic=2a2216ff` (darker than sand), square `graphic.site.relic.glow=ffc94aff`,
   1.1 of the plinth's radius, `spinDegreesPerSecond=30` on the client's clock times `graphic.ui.motionScale`, in a
   halo 1.5 as wide at alpha 0.3; warden `graphic.color.warden=8c7a55ff`, an oval 1.4 times as long as wide, north to
   south; bead `graphic.color.warden_shot=ffe6a0ff`. Turned down: a separate `WardenMark` (one record draws the
   relic and its guards).
8. **The Relic scenario's runners** are unaware, as a mission's are (G0068), so they come only when an attempt rouses
   them.
