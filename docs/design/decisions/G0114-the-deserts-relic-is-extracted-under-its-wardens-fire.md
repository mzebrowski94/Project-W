# G0114: The desert's relic is extracted under its wardens' fire, and its taking wakes snakes

**Status.** accepted

**Trigger.** The desert's site; a worked site laid in the open, or one with guards round it or something that comes up
when it is taken; a warden; whether the squad works a site of its own accord.

**Enforced by.** `RelicTest` (four wardens on its diagonals guarding it by handle; taken on the key held from inside the
lanes, untouched; offered from inside the ring; wardens silent until its first attempt, then all four firing
north, east, south and west every period while nobody works it, and silent for good once it is taken; a warden's shot
wounds a commander and passes an alien; the plinth stays and two or three snakes come up outside the ring, off the
men; extracted by the squad watching from inside the lanes, its ring holding their watch on the shipped numbers; its rouse twice a point's on the shipped numbers); `RelicScenarioTest`;
`MissionTest.laysOneOrTwoRelicsAmongTheSitesOfEveryDesertAndNoneElsewhere`, `.spawnsADesertsRelicWithItsFourWardensSilent`;
`MissionSweepTest.holdsEveryMissionToTheRules` through `MissionFaults.sites` (the relic count) and
`MissionFaults.relics` (its room open base ground, nothing else in it, its plinth reachable from inside its wardens);
`PromptsTest` (`HOLD [E] EXTRACT`, then `EXTRACTING…`); `TrooperStepOffTest` (a trooper a warden's bead wounds steps off
its line to his own side; an ordinary shot is not stepped from); `ArchitectureTest` (`docs/components.md`'s writers).

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 10, and ticket 09). The points
under *Provisional* are the implementer's, taken in an AFK run, for review.

**Amends G0068** - the order: a biome's worked sites in the open after those in its houses, before its control points.
**Amends G0111** - a worked site may stand in the open in a room of its own, have guards and a hatch, and be left to
the commanders by the squad. **Amends G0113** - a warden is an emplacement its guarded site wakes and silences.

**Amended 2026-11-23 (approved by Mateusz).** The wardens stand further out, the squad extracts the relic, a man its
bead wounds steps off the lane, and a warden is a ringed token. What changed is marked *(amended)* below; provisional
point 1 is reversed and point 4's numbers are moved.

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
- **The wardens and their cross-hatch** *(amended)*. `EmplacementKind.WARDEN`: radius 1.2 (a ringed token drawn, a
  circle in the solver, ADR 0007), a `volley 0,90,180,270` every 0.75 s, `wakeRadius=0`, gun `warden_shot` (the
  spire's bead, speed 26, range 22, a wound). On the relic's diagonals **8** from its middle, each line - a **lane** -
  runs 5.7 off the plinth's middle (`WorkedSiteDef.Guards.lane`), north to south and east to west, so the lanes
  cross-hatch the ground round the ring. Inside them is a square 11.3 across round a plinth 3 across: a trooper (3.2
  across) touching the plinth anywhere round it is clear of every lane by his body and a bead, so the squad's five
  stand round it out of the fire, and anywhere inside them a man is within arm's length of the plinth. Getting in
  crosses a lane: that is the dodge, found by watching the beads (pillar 5). Shots pass the other wardens and every
  alien (G0113, decision 8) and miss the plinth. Between two wardens (8.9 clear), anybody fits.
- **The squad extracts it** *(amended; was provisional point 1)*. `sim.site.relic.troopersWork=true`: one man works
  it and up to `captureCrowd` less one watch their sectors, as at a mainframe - but a man counts as standing round a
  site with guards only inside its guards' lanes, less his body and their bead (`TrooperObjective`), so a watcher is
  never standing in one. Measured in the Relic scenario (8 seeds, squads of 7-10, the commander idle at the start):
  taken 6-26 s after its first attempt (about 12 s typical), 0-4 men down by then (about 2), every one of them hit
  crossing a lane on the way in, wandering round it as a spare past the crowd, or going to fetch a man down in a lane -
  none standing round it. Ticket 09's measure was all nine down within 12 s.
- **A man its bead wounds steps off the lane** *(amended)*. A gun may declare `stepOff` (`sim.weapon.<kind>.stepOff`,
  zero off - every gun but `warden_shot`, 2.5). `Spawner` reads it onto the shot (`Projectile.stepOff`), the one
  place; `CollisionSystem` notes on the blow the step square to the shot's flight, to the side of its line the man
  stands on (dead on it, the shot's left) - from inputs, never drawn (G0012); `DamageSystem` sends a trooper the blow
  leaves on his feet to where it ends, for no longer than a wounded man takes to walk it; `TrooperMindSystem` puts it
  above the whole cascade (`Trooper.Mode.STEP_OFF`) and ends it there, and `TrooperMoveSystem` walks it straight.
  Then he takes up what he was doing. No new component or system. Turned down: a `Dodge` on troopers (a dodge
  answers a shot still coming, and its numbers are a strain's); a step drawn from the `Rng` (G0012); stepping a
  commander (he is the player's).
- **Woken by the first attempt, silenced by the taking.** A warden carries its relic's handle (`Emplacement.guards`,
  ADR 0016). The tick the relic's work goes from none to some, `WorkedSiteSystem` wakes every emplacement that names
  it; they fire from then on whether anybody works it or not; the tick it is taken, it puts them to sleep for good.
  `WorkedSiteSystem` (38) runs before `EmplacementSystem` (54), so a warden woken this tick starts its clock this
  tick (its first volley a period later, G0113 decision 6) and one silenced this tick fires nothing on it.
- **Snakes.** Taken, it lays what any taken site lays, then two or three `SNAKE` come up within 17 of it *(amended)*, outside its
  room and `sim.nest.hatchKeepOff` off every man, through `Brood` (G0074). They are ordinary snakes.
- **Placement.** `sim.biome.desert.furniture.relics=1 2` (`0 0` everywhere else, at most `sitesMin`), among the
  biome's 2-3 sites: each in a band of its own, in a point's place, so a desert dealt two relics and two sites has no
  point.

**Provisional, for review (AFK run).**

1. ~~**Troopers do not extract a relic**~~ *(reversed 2026-11-23: they do, see above)* (`sim.site.relic.troopersWork=false`): the squad passes it over for the next
   site, and the commander extracts it. Turned down: troopers extracting it as they download a mainframe, as the
   ticket proposed by analogy - measured in the Relic scenario, a squad of nine sent to it stood round it at arm's
   length, in the lanes, and was all down within twelve seconds of its first attempt; the squad is the resource
   (pillar 3), and losing it to a decision the player never took is not a fight. The better answer - troopers working
   it from the four out-of-lane spots and keeping their watch outside the ring - is new trooper behaviour, left open.
2. **The ring's room**: `room=10` *(amended, was 7)*, the wardens' outer edge (9.2) and a little; placement anchors a relic only where
   every tile within the room of anywhere on its tile is the biome's base ground (sand: no mud, no rock), not kept (no
   pass, apron or start), off every way in and the finale's watch, and `pointApart` off every other site - then every
   later pass keeps off it by its room as by a body. Turned down: reserving only the plinth and the four warden
   bodies, which let a stone stand between them; a flat check on the anchor tile, which let mud into the ring.
3. **Which bands** a relic takes are dealt from the biome's own stream (the same on every attempt and tier), never the
   last biome's last band - the approach to the finale - so a last desert with two sites has one relic. Where in its
   band, the attempt's stream.
4. **Numbers:** warden offset 8 along the diagonal *(amended, was 5)*, body 1.2, period 0.75; bead speed 26, range 22
   (a lane reaches about 11 past the far warden); plinth 1.5; hatch radius 17 *(was 14)*; room 10 *(was 7)*. Key names generalised from the ticket's:
   `guards.kind/offset` for `wardenOffset`, `hatch.strain/min/max/radius` for `snakesMin/snakesMax/hatchRadius`,
   since they sit on the definition every worked site has.
5. **What a taken relic leaves lies outside its room** (`Sites` reserves a worked site's room, not only its body), so
   no crate or cache lands among the wardens. Turned down: its body only, as for the mainframe.
6. **A guard fires a fixed volley** - refused at load otherwise (`WorkedSiteDef.checkGuards`), since nothing deals a
   cycle when a site is put down; and its room must hold its guards.
7. **Look:** plinth `graphic.color.relic=2a2216ff` (darker than sand), square `graphic.site.relic.glow=ffc94aff`,
   1.1 of the plinth's radius, `spinDegreesPerSecond=30` on the client's clock times `graphic.ui.motionScale`, in a
   halo 1.5 as wide at alpha 0.3; warden `graphic.color.warden=8c7a55ff` *(amended)*: a token seen from above - its top that colour,
   its side `graphic.site.relic.wardenSide=5a4c33ff` dropped 0.25 of its radius south, two rings of `wardenRing=c2b08aff`
   0.1 wide every 0.3 in from the edge (was an oval 1.4 times as long as wide); bead `graphic.color.warden_shot=ffe6a0ff`. Turned down: a separate `WardenMark` (one record draws the
   relic and its guards).
8. **The Relic scenario's runners** are unaware, as a mission's are (G0068), so they come only when an attempt rouses
   them.
