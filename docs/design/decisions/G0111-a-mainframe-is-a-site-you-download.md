# G0111: A mainframe is a site you download, and one man does it while the others watch

**Status.** accepted

**Trigger.** A site taken by work held at it rather than by standing on it - a mainframe, the desert's relic (ticket
09, G0114); the action key at one; a trooper working one; where a biome stands one; how it is drawn; the ring drawn
round every site not yet taken.

**Enforced by.** `WorkedSiteTest` (offered at arm's length, taken after its work seconds held and not a tick before,
lost on letting go and on a wound, its charge on it and nothing on him, a fresh attempt rouses once, taking it lays
what a taken site leaves and credits the man, never taken twice); `TrooperWorkedSiteTest` (a squad alone downloads
it, one trooper works it and the others hold sectors, a wound on him loses the work, a sixth past a crowd of five is
sent on); `MissionTest.standsAMainframeInAboutHalfTheBiomesThatLayAHouse`, `.spawnsAMissionsMainframeAsAWorkedSite`,
`.putsBetweenItsFewestAndMostSitesInEveryBiome`; `MissionSweepTest.holdsEveryMissionToTheRules` through
`MissionFaults.sites` (two or three sites a biome, at most one of a kind, a housed one in a house of its biome and a
man's width clear of every doorway) and `.bands`; `ScenariosTest.spawnsNothingInsideRock`; `PromptsTest`
(`HOLD [E] DOWNLOAD`, then the working verb); `ObjectiveRingTest`; `SystemOrderTest`.

**Settled with Mateusz, 2026-11-22** (`.scratch/commander-and-sites/spec.md`, section 6, and ticket 06); the objective
ring was asked for by him directly the same day. The points under *Provisional* are the implementer's, taken in an AFK
run, for review.

**Amends G0068** - the order: a worked site in its house first. **Amends G0098** - a prompt says what is being done
while a site is worked.

**Constraint.**

- **A worked site** is a site (G0110) taken by one man's work held at it - a **mainframe** is **downloaded**. Its kinds
  are a definition table, `WorkedSiteKind` and `WorkedSiteDef`, a `sim.site.<kind>` block each (ADR 0008):
  `radius`, `workSeconds`, `rouseRadius`, `chance`, `housed`, `action`, `look`. **The relic is a configuration of
  the same thing** (ticket 09): a constant, a block and its own `ActionKind` whose `worksASite()` is true - its verb
  while worked (`EXTRACTING…`) is one more `ui.action.<ACTION>.working` key.
- **The object.** Solid, immovable and unbreakable: no health, a solid collider and an `Obstacle`, so shots and
  blasts stop on it as on a boulder; on nobody's side, so nothing hunts it. `sim.site.mainframe.radius=2.4`.
- **The work** is on a door's rules (G0069), counted by `WorkedSiteSystem` straight after `DoorSystem`: held at arm's
  length (`Reach`, as a crate is reached) for `sim.site.mainframe.workSeconds=6`; a tick nobody worked it loses all
  of it - letting go, stepping out of reach, a dash (G0107) - and so does a wound (`Vitality.woundedThisTick`). One
  tick of work a tick however many hands: a commander and a trooper together are no faster than one. **Each fresh
  attempt rouses** - the tick its work goes from none to some - through `Rouse.within` at
  `sim.site.mainframe.rouseRadius=58`, the point's. Done, it goes through `Sites.taken` - the draft point, the goal on
  the tally of every commander at arm's length, the caches, the medkit, the crates - and is never worked again. A
  worked site counts for `Objective.CAPTURE_POINTS` as a point does.
- **The action key.** `ActionKind.DOWNLOAD`, a hold. A tie goes to the gun, the crate, the casualty, then the
  worked site, and only then the door. Before work starts the prompt is `HOLD [E] DOWNLOAD`; while it runs the
  worker's chip reads `DECRYPTING…` with its fill (`Prompts.working`), this machine's own (G0098). **Everyone** sees
  the work as the site's charge, drawn as the door's loading circle on it; nothing fills on the man.
- **One man works it while the others watch.** A trooper goes for an untaken worked site as for an untaken point -
  `TrooperObjective` walks to the nearest **site** by route, opening a shut door on the way - and stands at arm's
  length of it. It goes to one trooper, as a door does (G0069): the first settled at it, a tie to the lower handle,
  kept while he is at it, on his feet and securing it; a man who turns to fight, or is wounded, drops it, and another
  takes it up. The capture crowd (G0086, `sim.trooper.captureCrowd`) counts at it as at a point: the worker and up to
  four others stand round it in their sectors; a spare past the crowd is sent on to the next site.
- **Placement** (amends G0068): first of a biome's passes, before its points. For each housed kind, from the biome's
  **own** stream (`stream.derive(WORKED)`, the same on every attempt), kind after kind: on its `chance`
  (`sim.site.mainframe.chance=50`), in a house wholly inside the biome - never a gatehouse - dealt from the stream when
  there are two; on a tile beside one of its walls, at that tile's middle and clear of the wall, a man's width
  (`sim.trooper.radius` twice) and its body clear of every doorway, dealt from the attempt's stream (`attempt.derive(WORKED)`), so a biome placed again can move it and the attempt taken - the first that fits every tier - stands it in one place on all of them; it takes the band its
  column falls in, which a control point then leaves to it (G0110's seam). At most one of a kind a biome. Its band's
  neighbours' points keep `pointApart` off it; nests keep `pointKeepOff` and geysers `geyserKeepOff`; the house's
  stock is laid round it. No turret stands by one - turrets stay by points (G0082).
- **Drawn** as a dark-grey cabinet (`graphic.color.mainframe`) with a black screen showing a **red X**, a **green tick**
  once taken (`graphic.site.mainframe.*`) - on the object, never over the view.
- **The objective ring** round every site not yet taken - a point, a mainframe, a finale's point, whatever site a relic
  carries: white and yellow stripes by turns, turning and shining (`graphic.site.ring.*`), underfoot, gone the moment
  it is taken. Rendering only, read off `Site.taken`, so the same on every machine. Not the action's highlight -
  that is a bracket and never a ring (G0098) - and not a charge.

**Provisional (AFK run, for review).**

1. **The body is `radius=2.4`, 4.8 across.** The ticket's "about 2.5 across" read as the `radius` key it names: 2.5
   across would be smaller than a trooper (3.2). 2.4 rather than 2.5 so it stands on the tile beside a wall clear of
   it, as everything put down is. Turned down: a 1.25 radius; a body flush against the wall (fails "nothing in or
   against rock").
2. **Against the wall:** the middle of any tile along the inside of any wall, a man's width and its body off every
   doorway, dealt from the attempt's stream, not the biome's: with the spot fixed for every attempt, a biome it crowded could never be placed again round it (sweep: `HHRG-6VV9`, `HHQG-J6A3` short of nests). Whether, and which house, stay the biome's stream's, as settled. Turned down: the wall opposite the doorways only (a house's two doorways
   are on any two walls); the middle of the house (not "against a wall").
3. **Troopers go to the nearest site by route, a mainframe and a point alike** (the ticket's default). Turned down:
   preferring the mainframe.
4. **A taken mainframe is held as a taken point is** (G0029, G0040): the squad stands round it until it moves on.
   Turned down: leaving it the tick it is taken.
5. **"At" a worked site** for the crowd and the sectors is its edge plus a man's body and arm's length
   (`sim.trooper.radius + sim.action.reach`); a man stops short of that by the point's share (`INSIDE_THE_EDGE`), so
   whoever stands there can reach it. Turned down: a radius key of its own (a number every caller must honour).
6. **The fill on his chip is his `Action.progress`**, the site's progress while he holds the key, so the readout and
   `Prompts` learn it as a stabilise's. Turned down: the chip reading the site's component as it reads a door's -
   `Prompts` could not then say `DECRYPTING…`.
7. **A worked site counts for `CAPTURE_POINTS`**, so a level that wants its points taken wants its mainframe too.
   Turned down: a new objective.
8. **Reinforcements still lie by control points only** (G0030): a biome whose mainframe replaced a point has one
   point's fewer. Turned down: men by a mainframe too (inside a house, they would be its stock).
9. **The objective ring:** hidden once taken (turned down: kept, dimmed); underfoot, under men and bodies (turned
   down: over them); 24 stripes, 0.6 wide, 0.8 outside the edge, 30 degrees a second, a 0.8 Hz pulse dimming 35 %; spin
   and pulse times `graphic.ui.motionScale`. Also round the finale's point while it is untaken.
10. **The cabinet body's colour is `graphic.color.mainframe`**, the palette's key every look has, not the ticket's
    `graphic.site.mainframe.body`: one key for one colour.
