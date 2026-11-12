# The leech and the slinger that throws it

Type: grilling
Status: resolved
Blocked by: 03
Section: Enemies

## Question

A new small strain, the **leech**, and a **slinger** that **slings** leeches instead of runners. A slung leech
that lands on a trooper clings to him and, after a while, goes off - a kill - leaving **venom** round it, as a
frog's shot does. One that lands with nobody close by sits still and goes off after a moment, leaving venom.
What is it, as data on what exists?

## From Mateusz (translated)

> New Slingers that throw "leeches" (described below).
> Leeches - a new small enemy type (purple) that, once thrown, attaches to a trooper and after some time explodes
> (lethal hit), leaving venom (like the frog's shot) around. A leech thrown on the ground, if nobody is in close
> contact, does not move, and after a while explodes leaving venom.

## Read first

`/mattpocock-skills:grill-with-docs`, then `/mattpocock-skills:implement`.

Project context: `feature`, `new-kind` and `new-system`. G0074 (the slinger, riders as a count), G0045 (the grip - the
closest thing to clinging), G0018 (things explode by dying), G0034, 03's answer (a spatter on dying),
`docs/glossary.md` *Units and sides*.

## Open

- Purple is the **spitter**'s colour - pillar 5 wants the leech legible apart from it.
- Clinging: a **grip** that does not carry, or something new? Can the man or his squad get it off (a strike, a
  shot)? Does a commander get one?
- Whose side the venom takes (G0034: venom takes the frog's side).

## Done when

Both built as data where they can be (G0008), glossary rows, `verify.sh` green. Ask for a scenario.

## Settled in grilling (2026-11-12)

All agreed by Mateusz. First guesses for numbers are marked ~; they go in `assets/sim/`, never a literal.

1. **Look.** Magenta, ~`d94fc0` - his purple kept, warm where the spitter's is blue, bright where the searer is
   dark - small (radius ~0.8, half a spitter). Its ground clock drawn as a mine's quickening **fuse** (pillar 5).
2. **The cling is the grip, minus the hold.** `GripDef` gains `pins` (ant true, leech false). Shared with the ant:
   the pair (`Grip.load` / `Gripped.by`), the clock published on him and drawn as a grip's, the finish, letting go
   when either dies. Unpinned, he drives, fires, throws and acts as ever: `Drivers.held` counts only a pinning
   grip. The leech is the one carried - kept touching his edge at the bearing it took him from, **by position**, so
   rock never scrapes it off. Word: **cling** (written).
3. **It takes hold by touch and never bites.** `GripDef` gains `byTouch`: any contact with a man on his feet whom
   nothing else holds takes hold - on landing, or a man walking into one on the ground. The ant keeps taking hold
   only from an airborne leap.
4. **Commanders too** (pillar 2). `Gripped` is already on him; a slinger slings at whoever it hunts.
5. **Going off on a man.** At the end of the clock it lands its claws' blow - `KILL`, the default - and dies
   (`GripDef` gains a "spent when it finishes him" field); its death lays its venom (G0018's one death reaction).
   Hold ~3.5 s.
6. **On the ground with nobody near.** Speed 0, so **rooted**. A **fuse** - a new definition every strain carries,
   off at zero, "dies of itself after so long on the field" - runs from landing, ~2 s, then it dies and lays its
   venom. Taking hold stops the fuse and the grip's clock takes over. Troopers notice and shoot a ground leech
   like any enemy.
7. **Getting it off.** Health 150, a grunt's (three sidearm rounds, one hand strike; tier-scaled like the swarm).
   Killed, it lays its venom where it is - on the man - which is the price; `escapeHarm=none`. Friendly shots pass
   through him, so his mates can shoot it off.
8. **The venom.** Alien side (G0034), so aliens wade it. The shared blood puddle `sim.blood.*`, the leech declaring
   `deathSlicks` ~5 (about a frog spatter), heeded 0.85 (G0076). Toxic blood's ranks add to it as to anyone.
9. **The leech slinger** - a new strain `leech_slinger`, the slinger's block with `sling.rider=leech`,
   `landBehind=0` (aimed where he stands: a trooper standing to shoot is hit, a moving commander can step out),
   `landingSeconds=0` (it grips on landing), 3 riders. Dead, what is aboard spills through `Brood` and sits and
   ticks. Drawn slinger-brown, riders in the leech's colour (the rule already draws riders in theirs). Words:
   **leech**, **leech slinger** (written).
10. **Where.** `loose leech_slinger 0 1` in badlands and the cut, the mission sweep proving the room; plus a scenario.
11. **A ridden trooper** fights on, and on `AiChance.STRUGGLE` swings at the leech instead - one swing kills it and
    its venom wounds him. Nobody flees a ridden man; walking him off the squad is the player's call.
12. **He goes down with it on him** (shot, blast): the grip's rule - a man down is let go. The leech drops beside
    him, rooted, its fuse started fresh.
13. **A second leech touching a ridden man** does not take hold (one grip per man); it sits, fuse running.

**Records.** New G0083 "The leech clings, and a slinger slings it onto you", amending G0045 (grip by touch, a grip
that does not pin, a gripper spent on finishing) and G0074 (a second slinger; a rider aimed onto its target);
rows in `docs/design/invariants/aliens.md`. Not in any Do-not-build column: G0045's "grab that kills on landing"
stays refused (the clock still kills), G0074's "rider shot down in the air" stays refused (it is untouchable in
flight).

## Answer

Built (2026-11-12), `verify.sh` green, nothing committed.

- **The leech** - `LEECH`, rooted, magenta (`graphic.color.leech=d94fc0ff`), health 150. A cling is the grip with three
  new `GripDef` fields: `pins` (false: he keeps his legs and gun - `Drivers.held` counts only a pinning grip),
  `byTouch` (takes hold on any contact, never bites), `spentOnFinish` (dies with its `KILL`, laying its venom).
  Carried on his edge **by position**, not solid while it clings. A trooper with one on him struggles on
  `AiChance.STRUGGLE` and otherwise fights on.
- **The fuse** - `FuseDef` on every strain, off at zero; carried in a `Mine` component on a strain that has one (a
  mine's clock and flash, no trigger). It starts once the leech is down holding nobody and stands down, whole, while it
  is slung or holding a man.
- **The leech slinger** - `LEECH_SLINGER`, the slinger's block with `rider=leech`, `landBehind=0`, `landingSeconds=0`;
  `loose leech_slinger 0 1` in badlands and the cut (the mission sweep passes).
- **Found in review and fixed:** a speed-0 rider was immovable in flight, so it passed through rock and scenery - a
  slung body is now movable in the air and rooted again once down (`Speed.rooted()`); the fuse stood frozen-lit while
  waiting - it now stands down.
- Records: [G0083](../../../docs/design/decisions/G0083-the-leech-clings-and-a-slinger-slings-it-onto-you.md), amending
  G0045 and G0074; three rows in `docs/design/invariants/aliens.md`; glossary **leech**, **leech slinger**, **cling**,
  **pin**, and **fuse** and **rooted** widened. Tests: `LeechTest` (13), `SimConfigTest.slingsLeechesOntoHim...`.
- **From playtest (Mateusz, 2026-11-12): a leech lying in wait leaps at a man passing close by.** Its own leap
  (`sim.unit.leech.leap.*`, trigger 5, coil 0.15-0.3 s, 26 for 0.3 s, cooldown 1.5 s), aimed once, gripping on
  landing; an ordinary leap - shot in the air, stopped by scenery, a rooted leaper being movable while airborne
  (`EnemyLeapSystem.holdOffSceneryInTheAir`, which now also covers the slung case). Fuse runs on through it. G0083
  edited in place (not yet committed): its "a leech that walks or hops to a man" Do-not-build line narrowed.
- No scenario: asked, Mateusz declined for now (2026-11-12). Both strains are in the proving ground's strain row.
