# Placement inside a biome

Type: grilling
Status: resolved (grilled 2026-10-05: placement G0068, doors G0069 and ADR 0022)
Blocked by: 12

## Question

Where everything a biome's roster and furniture count goes, relative to each other, the passes, the ridges and
rooms - as **rules, never coordinates**. Graduated from map.md's *Not yet specified* by 11. Grilled after 12 (a
room's contents are placement rules; the two may merge) and **before stage 7**, the first biome stage, which waits
for it. Run `game-patterns` first (`Scatter` is the house tool), then `feature`.

## Handed down

- **Counts are settled elsewhere**: `Roster.count` for creatures (tier x ramp, 03, G0063); furniture ranges are
  fixed (03). This ticket says *where*, never *how many*.
- **Rules are strip-wide** (`sim.mission.placement.*`) unless a biome genuinely needs its own - then a field on
  `BiomeDef`, off at zero for every biome (10, Q1).
- **From 04 / G0064**: one of the last biome's points stands in its last `sim.mission.finale.approachColumns` (8)
  columns; the last biome's nests stand outside the finale's watch.
- **From 01, 02, 03, 13**: nothing but base ground in a pass's apron, and no scatter there either; clearance 2.
- **From G0062**: a nest wakes only for somebody in its own biome and within `wakeRadius`.
- **Built from `RngStream.MISSION`**, derived per sub-concern and per position (05); build-time only.
- **Checked by `MissionSweepTest`** (10): every point and crate reachable, the two rules from 04.

## Open, roughly in the order they unblock each other

- **Where the squad starts** - the first biome's west edge, and what may stand near it.
- **Points** - 2-3 a biome: spread along the march or free; distance from passes, ridges and each other; in a room
  or never in one.
- **Nests** - distance from the pass you come in by, from points, from each other; how near a ridge (a nest by the
  far side of a ridge is quiet until you cross, G0062).
- **Loose creatures** - put down unaware (03): spread, clumped round something, kept off the entry pass.
- **Crates, medkits, mushrooms, flowers** - near points, in rooms, or anywhere; how far from nests.
- **Barrels, boulders, rabbits** - by rock, by points, anywhere.
- **What a house holds** - 12 settled the house (ground only); what stands in one is here.
- **Doors** - folded in here by 12's grilling; see *Doors* below.
- **The technique** - one `Scatter` pass per kind with exclusion radii, a ranked list of candidate spots, or zones
  per biome; and what happens when a rule cannot be met.

## Doors

Mateusz, 2026-10-05, during 12's grilling - folded into this ticket rather than a ticket of its own. A new
mechanic: `feature` before anything, and a stage may have to grow for it.

> Rooms will feature two doors (placed on whichever wall is appropriate). Doors can be opened by Commanders and
> Troopers (this process takes a few seconds and is visualized by a progress bar - similar to the overheat bar,
> but green). For now, doors cannot be closed once opened. If someone is inside the room, enemies can target and
> attack the doors in a repeating cycle: enemy leap + enemy bouncing off the door + screen shake for nearby
> Commanders - continuing until the door is destroyed. Doors can only be destroyed by explosions and "HEAVY"
> weapons, such as the Bazooka or Flamethrower. For transitions between biomes, Troopers can open the doors
> themselves without assistance.

From 12: every **house** has exactly two **doorways**, 3 wide, in two different walls; a **gatehouse**'s two are 3
wide too, so one door fits every doorway. `Strip.Laid` reports each house and its doorways; a door is an entity
`sim` stands in one. The map's connectivity checks treat a doorway as open ground.

Open:

- Is a door a kind (G0008: a constant, a definition, a block) - scenery with health that only some blows land on?
- **"HEAVY"**: a field on the weapon (off at zero) or on the blow; explosions are already blasts.
- **Opening**: who may (commander, trooper - a trooper on his own only at a gatehouse?), how long, interrupted by
  what, the green bar's place among the bars (pillar 5).
- **Closed**: what the flow field makes of a closed door - a route through it or not, for which side; whether a
  door blocks sight and shots as a wall does.
- **"Someone inside"**: how an enemy knows (perception, the house's rect from `Laid`), and what "inside" is.
- **The attack**: a leap that bounces off is the leap (G0012's) aimed at a door; the shake is a **shake**
  published by the blow, felt by commanders near it.
- **Closed at the start** everywhere, or some houses open; a gatehouse's doors and the march (pillar 1).

## Answer

Grilled 2026-10-05. Placement is **G0068**, built in stage 7. Doors are **G0069** and **ADR 0022**, built in a stage
of their own, **7b**, between 7 and the finale: stage 7 ships houses with bare doorways under a scaffolding row.

### Placement

**Ordered `Scatter` passes**, one biome at a time, from the biome's own stream derived off `RngStream.MISSION`
(05), most constrained first:

1. **the start** (first biome only), 2. **points**, 3. **house stock**, 4. **nests**, 5. **pack anchors** and their
packs, 6. **crates by points**, 7. **medkits, mushrooms, flowers**, 8. **barrels**, 9. **boulders**, 10. **rabbits**.

Each pass keeps off what earlier passes put down by its radii, measured **crow-flies** - the same measure as
`wakeRadius`, which a route distance would disagree with. Every pass keeps off **aprons and doorsteps**. In play a
pass that falls short places what fits; `MissionSweepTest` fails any seed where one does, as a house short of its
`min` fails `StripSweepTest` (12). Counts are drawn before placement and never rescaled by it.

- **The start** - the spawn area against the first biome's west border, centred on the middle row. No nest and no
  loose creature within `startKeepOff`, above `wakeRadius`, so nothing wakes on the first tick; no house over it.
  The same opening every mission, learnt like the finale.
- **Points** - the biome split into as many equal column bands as it has points, one point scattered in each, so the
  goal line leads east step by step; `pointApart` from each other across a band edge. The last biome's last band is
  its last `approachColumns` (04, G0064). **Never in a house.**
- **Nests** - `nestKeepOff` from the pass you came in by (the first biome: from the start), `nestApart` from each
  other, `pointKeepOff` from every point (above the point's `aggroRadius`, so starting a capture rouses no hole).
  **Never in a house.** `nestKeepOff` is under `wakeRadius` by choice: a nest may stand in the west half and wake as
  you come in, while every nest beyond a ridge sleeps until you cross (G0062).
- **Loose creatures** - each roster line's count split into **packs** of `packMin`-`packMax`, each scattered round an
  anchor; anchors keep off the entry pass and the start by `startKeepOff` and stay `packApart` apart. Fights come as
  encounters, not a carpet. Put down unaware (03).
- **What a house holds** - the biome's **stock**, crates first, then medkits, up to `houseStock` a house. Nothing
  hostile and no point: a house is a cache you open. A biome with a house and no stock leaves it empty.
- **Crates** left over - within `pointReach` of a point, outside its radius: the reward where the fight is (G0030).
- **Medkits** anywhere; **mushrooms and flowers** on organic ground where the biome has it, else anywhere - food is
  where creatures graze. Every pickup `pickupKeepOff` from nests.
- **Barrels** within `barrelReach` of a nest or a pack anchor, so a shot sets one off among them. **Boulders**
  anywhere, `keepingOffRock` as today (G0042). **Rabbits** anywhere off the start.

```properties
# Placement inside a biome (missions ticket 14, G0068). World units; a tile is 5.
sim.mission.placement.startKeepOff=150
sim.mission.placement.pointApart=60
sim.mission.placement.pointKeepOff=50
sim.mission.placement.nestKeepOff=80
sim.mission.placement.nestApart=50
sim.mission.placement.packMin=2
sim.mission.placement.packMax=5
sim.mission.placement.packApart=40
sim.mission.placement.houseStock=2
sim.mission.placement.pointReach=25
sim.mission.placement.pickupKeepOff=40
sim.mission.placement.barrelReach=25
sim.mission.finale.approachColumns=8
```

`MissionSweepTest` gains: every point, crate and house's stock reachable from the start (a doorway counts as open
ground); nothing on an apron or doorstep; no nest or creature within `startKeepOff`; nests off the entry pass,
points and each other; no point, nest or creature in a house; one point a band; and 04's two rules.

**The word.** The finale's `doorColumns` is renamed `approachColumns` - unbuilt, so free - and "the finale's door"
becomes "the last biome's last point" in prose. *Door* is now the thing in a doorway.

### Doors

A new mechanic, through `feature`. **A door is a wall that opens.**

- **What it is** - one thing with a `sim.door.*` block, as a nest, a mine and a turret are: no kind table, since every
  doorway is 3 wide and one door fits all (12). An entity with a `Handle`, its doorway, its health and its opening
  work, all in the snapshot.
- **Shut, it is solid** (ADR 0022). Its doorway's tiles count as solid in **one place in `sim`** that answers "solid
  now?" - the solver's terrain clamp, a shot flying into rock, a blast's clear line, perception's clear line and a
  shooter's choice of target. It holds under crowd pressure exactly as a wall does, and sits flush with it. **Routes
  ignore it**: the flow field is the map's and knows terrain alone, so a route still runs through a shut door.
- **Every door starts shut**, houses and gatehouses alike, and is **never shut again** once opened or broken.
- **Opening** - stabilise's rules (G0023): within arm's length, holding the action key, `sim.door.openSeconds`
  standing still; a wound, going down or stepping out of reach loses the work. The action key's list gains a door
  (G0021); a tie goes to what is already ahead of it. **A commander and at most one trooper** may open one door
  together, never faster than one. The bar is the **door's**: an arc in the heat arc's shape, green, over the door,
  filling while anybody opens it and gone when the work is lost (pillar 5).
- **A trooper opens whatever door his route needs**, anywhere, on his own - the first to arrive, a tie to the lower
  handle. Every other trooper whose route needs that door stops near it and turns through his own sector, as men on
  a point do: the rest keep a lookout.
- **The siege** - an alien hunts by route (G0026), so one hunting a man behind a shut door walks up to it. A hunter
  whose way is shut by a door **leaps at the door**: aimed once at its face, never steered; on contact
  `sim.door.leapDamage` lands on the door through `Damage.land`, the creature is thrown back off it, and the door
  publishes a **shake** (`sim.door.shake.*`) felt by commanders near it. It leaps again on its own leap clock. A
  strain that cannot leap waits at the door. No "someone inside" test and no house rect in `sim`: only a hunter can
  find its way shut. Amends G0026.
- **Breaking** - a leap, a **blast** (decisive: one breaks it outright, G0018) and a **heavy gun**
  (`sim.weapon.*.type=heavy`: the bazooka, the flamethrower). No other gun hurts it: a door is opened, not shot
  open. `sim.door.health` is six grunt leaps; one number on the door, the same for every strain's leap.
- **Seeing in** - a house has no roof and is drawn open (pillar 4): the player sees in. The creatures' clear line
  does not pass a shut door, so nothing outside notices a man inside through one.
- **Gone once open** (Mateusz, 2026-10-05) - an opened door **sinks into the ground**: drawn as its doorway's squares,
  it shows them shrinking for `sim.door.sinkSeconds` and then is not there at all - no open door left beside the
  doorway, nothing to close. The doorway is open ground on the tick the work is done, not when the sinking ends: the
  sinking is a look, as a blast's flash is after the tick it caught (G0018). The door then leaves the field. A
  **broken** door simply disappears on the tick it breaks - no sinking, no debris, for now.

```properties
# A door (missions ticket 14, G0069). Values to tune in stage 7b.
sim.door.openSeconds=3
# How long an opened door is seen sinking into the ground. A look only: the doorway is open from the first tick.
sim.door.sinkSeconds=0.4
sim.door.leapDamage=50
sim.door.health=300
sim.door.shake.strength=1
sim.door.shake.radius=30
sim.door.shake.seconds=0.2
```

The flamer's time through a door is measured in 7b and `health` tuned against it, not the other way round.

**Pillars.** 1 - a fight at a door is a reflex fight; a siege is about six leaps. 2 - the commander opens doors
himself and stands at them. 4 - a house is drawn open. 5 - the door's bar and its shake. 7 - a door's state lives in
the snapshot and it is named by its handle.
