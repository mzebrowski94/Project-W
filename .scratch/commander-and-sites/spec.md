# Commander and sites - spec

Grilled with Mateusz on 2026-11-22 (`grill-with-docs`). Everything below is **agreed, not provisional**. A
ticket's implementer builds it as written. Where a ticket meets a choice this list does not settle, it takes
it as *provisional, for review (AFK run)*, as `docs/workflow.md` (*AFK runs*) describes.

Vocabulary is `docs/glossary.md`'s. The rows this effort coined are already written there: **jaguar**,
**dash**, **puff**, **redeploy**, **down mark**, **frost**, **fissure**, **ice**, **clearing**, **site**,
**mainframe**, **relic**, **plinth**, **warden**, **spire**, **rift**. Use only those words, never *charges*,
*arena*, *terminal*, *artifact*, *totem*, *chasm* or *objective* (for a site).

## 1. Dash (tickets 02, 03)

- **Inputs.**
  - Keyboard: `Shift` or `Space`, two bindings for one action, either works.
  - Pad: `EAST` (B / Circle). A back paddle (R4) is mapped to B in the pad's own software, because the
    input layer has no paddle button.
  - The pad **loses "take hold / let go" (drag)** for now, with no new binding.
  - Keyboard `F` stays drag.
- **Direction.**
  - The dash goes along the **movement** direction, never the aim.
  - With a neutral stick, it goes along the **last movement direction**, falling back to the aim if he has
    never moved.
- **Physics.**
  - A fixed-duration burst of velocity.
  - Distance = speed × duration.
  - It collides as walking does: rock, fissures, shut doors, ice and bodies all stop it.
  - **No invulnerability** (pillar 2).
- **Refused** while he is **down, stunned, gripped, clinched or aboard a turret** (the cases of
  `Drivers.held`). A refused press spends nothing and shows nothing.
- **Allowed** with a crate or draft open, and while hauling (he keeps hold).
- **A dash interrupts** a stabilise or door-open in progress: the work is lost, as letting go loses it.
- **Slowing ground.**
  - Per tick: every tick spent on ground that slows him moves at **half** dash speed.
  - Starting in a puddle and staying in it therefore gives exactly 50 %.
  - Glue, thicket and mud count; fire does not.
- **Numbers.** Distance 15 (three tiles) over 0.15 s. **2 dashes.** Recharge 3 s per dash, **one at a
  time**.
- **Panel.** The dashes left show as **purple pips** next to the wounds, on the player panel.
- **Look.**
  - Two small thrusters (a jetpack) fire on the commander while he dashes.
  - A small translucent smoke **puff** lingers briefly where the dash began.
  - A press with **no dash left** plays only the puff, at his feet.
- **Rocketman** (personal, 2 ranks). At the **start** of a dash, a magnetic stun round him:
  - It reaches **aliens only**.
  - The radius is ~6 at rank 1 and ~10 at rank 2. The fold is linear per rank, so 5 and 10 if it cannot
    say 6 and 10.
  - The stun lasts 1 s.
  - A short ring shows it.
- **Pantera** (personal, 2 ranks).
  - Rank 1: +1 dash.
  - Rank 2: +1 more dash and recharge 25 % faster. The fold is linear per rank, so each rank is +1 dash and
    −12.5 % recharge if it cannot say this.
  - Dash speed is **not** upgraded.

## 2. Commander colours (ticket 01)

- Slot 0 becomes **gold `ffd76eff`**, slot 1 becomes **white `eef4ffff`**, and troopers stay `4fb8d8ff`.
- Where they live: `assets/graphic.properties`.
  - `graphic.color.commander` (slot 0) and `graphic.color.commander.1`-`.7`, at about lines 13-21.
  - `graphic.color.trooper`, at about line 28.

## 3. Full House and `--scenario` (ticket 01)

- **The scenario.** Take commit `6a1bdcc` from `claude/keen-carson-rfz9ts`, the Full House scenario.
  **Not** `de972c0`, which turns the scenario list on in the menus. Scenarios stay hidden
  (`graphic.ui.page.scenarios=false`).
- **The argument.** `--scenario=<name>` matches a scenario's `name()` ignoring case, spaces, hyphens and
  underscores, so `--scenario=full-house` works.
  - An unknown name is a logged problem that lists the valid names, and the game opens on the menu.
  - With `--mission` given too, the mission wins and a problem is logged.

## 4. Redeploy (ticket 04)

- **The down mark.**
  - A **black-ringed red X (a saltire), never a red cross**, over a down commander, at about 65 % opacity.
  - It shows for **every player, on every machine**, for the whole of his bleed-out, whatever the PROMPTS
    switch says (it is state).
  - A G-record amends G0098: it is the one red mark over the view.
- **The window.**
  - The commander's bleed-out goes **5 → 10 s**.
  - From **5 s in**, his own screen shows `[F] REDEPLOY` (pad `[B] REDEPLOY`) over the mark.
  - Pressing it ends the bleed-out through the ordinary death path. Throes, respawn delay, reserve cost and
    shield are unchanged.
  - Friends can stabilise him for the whole 10 s.
  - Troopers keep their own 8 s.

## 5. Sites and crates (ticket 05)

- **Sites.**
  - A **site** is a control point, a mainframe or a relic.
  - A biome has **2-3 sites**, which is `pointsMin/pointsMax` as today, now counting sites.
  - A mainframe or relics **replace** control points.
- **What a taken site leaves.**
  - Every taken site earns what a taken point earns today: the draft point, the goal line's count, and what
    the taking leaves (grenade caches per seated slot, the 35 % medkit). It also leaves **crates**.
  - The crates are laid in the same ring.
  - They are counted by seated players over thresholds `1,3,6,10`: 1 crate for 1-2 players, 2 for 3-5, 3 for
    6-9, 4 for 10 and more. `Players.MAX` is 8 today.
- **Furniture crates.**
  - Crates stay as **house stock**. The biome's `pickup crate` count is honoured only in houses.
  - Crates are no longer laid near points, so a biome with no house gets no furniture crate.
  - Finales keep their hand-placed crates.
- **No duplicates.**
  - No two cards of one **pickup kind** in one crate.
  - A drawn duplicate goes back and another card is drawn. A deck short of kinds makes a smaller crate.
  - Firepower's flamer swap happens only if no flamer is already in the crate.
- **Looter** (personal, 1 rank).
  - The first time a commander holding it opens a given crate, the crate gets **+1 card** of a distinct kind,
    up to 4.
  - It happens once per crate ever, whoever's Looter set it off.
  - The extra card stays for anyone.
  - It works on a crate a non-Looter opened first.

## 6. Mainframe (ticket 06)

- **Where.**
  - At most one per biome.
  - A **50 %** chance in a biome that lays a house (never a gatehouse).
  - Inside the house, against a wall.
- **The object.**
  - Solid, immovable, unbreakable, about 2.5 across.
  - Drawn as a big mainframe with a black screen showing a **red X**, which turns into a **green tick**
    when downloaded.
- **The work.** Hold the action key for **6 s**. Letting go or being wounded starts it again. Each fresh
  attempt **rouses** what is near, at the control point's radius.
- **Text.**
  - Before work starts: `HOLD [E] DOWNLOAD`.
  - While it runs: `DECRYPTING…` with the fill, on the worker's screen.
  - Everyone sees its charge on the mainframe itself.
- **Troopers.**
  - They go for it as for a point.
  - **One trooper downloads**, and up to four others watch their sectors, as at a door.
  - They open a shut door on the way.

## 7. Jaguar (ticket 07)

- **Body.**
  - The runner's block (weave, dodge, leap), on a grunt-sized body (radius 1.8).
  - **Health 350.**
  - **Speed 15.6** (+30 %).
- **Leap.** Speed 44, cooldown 0.6 s, shorter hesitation.
- **Shots.**
  - A barrage of **3 small globs** 0.1 s apart, reach ~24, rest ~3 s.
  - Each glob does **0 damage, harm none** and leaves **glue** where it lands.
- **Colour.** Strong purple **`9b1fe8ff`**.
- **Where.**
  - In biomes with houses, each house (not a gatehouse) has **50 %** to hold **1-3** jaguars.
  - They **wander inside** until they notice somebody, then break the door (G0075's siege; they leap).
  - A G-record amends G0068's "no pack in a house".

## 8. Rift (ticket 10)

- **Ground.**
  - **Frost** base: cold, grey-blue stone, not pure snow.
  - **Fissures** of a new impassable dark terrain, laid as dense crags. They are solid as lava is, to shots
    and sight too.
- **Ice** walls, light blue, across chokepoints of the maze.
  - Solid until broken.
  - A player-side shot chips one (health ~300). A blast or an alien leap breaks it.
  - Generation counts the ways they shut as open.
- **Clearings.** Two round **clearings** (radius ~8 tiles) with no fissure, **one control point in each**,
  and denser enemies.
- **Roster.** Nest sludger 2-3, nest jaguar 2, loose thumper 2-3, loose sludger 4-5. **No houses.**

## 9. Fen spires (ticket 08)

- **Where.** The **fen** only, 3-5 per fen.
- **The object.** Solid, unbreakable, and it blocks shots.
- **The pattern.**
  - Each is dealt at build time a **fixed cycle of 4-6 bearings** from the eight compass points.
  - It fires one shot about every **1 s** along the next bearing of its cycle.
  - It fires only while a player-side unit is within ~45.
- **Shots.**
  - Small, range ~30, speed ~25, a normal wound.
  - They **hurt only troopers and commanders**: alien-side.

## 10. Desert relic (ticket 09)

- **Count.** **1-2 relics** per desert, counting among its 2-3 sites.
- **The object.**
  - A desert-stone **plinth** (solid, radius ~1.5).
  - A glowing square turning slowly over it, gone once claimed.
- **The wardens.** Four sand-coloured oval **wardens**, solid and unbreakable, at the **diagonals** about 5
  out: a tight space with a little room to move.
- **The work.**
  - Extraction works as a mainframe's download does: 6 s held, the same reset.
  - Prompts: `HOLD [E] EXTRACT`, then `EXTRACTING…`.
  - It rouses at **twice** a point's radius.
- **The wardens firing.**
  - They wake on the **first attempt**.
  - Each fires small, normal-wound shots **N, E, S and W every 0.75 s**, hurting only troopers and
    commanders.
  - They **stop when the relic is claimed**.
- **Claiming it** hatches **2-3 snakes** nearby, off the men.
