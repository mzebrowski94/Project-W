# Plasma cannon, Firepower, Engineer - spec

Grilled with Mateusz, 2026-11-24. Agreed, not provisional. Records: **G0127** (Firepower), **G0128** (plasma cannon),
**G0129** (the sentry and Engineer). Numbers are starting values for tuning, keys in `assets/sim/*.properties`.

## A. The plasma cannon

1. **Got from a crate**: a weapons cache card (`ARMOURY_PLASMA`) in the armoury deck, dealt as the other found guns are
   (`armoury.cards=3`, `crate=false`). Lost with the commander as every found gun is. Type `energy`.
2. **One orb a reload.** Each pull throws one **orb** and runs the heat bar to the stop, as the bazooka's does (G0058):
   `perShot=1`, the next orb waits on `resumeBelow`. Several orbs may be in the air at once.
3. **The orb** flies straight along the aim at **2x the commander's speed** (`sim.commander.speed=13`, so ~26), with a
   flight (`projectileRange`) past which it goes off.
4. **Beams.** While it flies, the orb keeps a beam on each of **up to 3 aliens** within its **reach** (~3 tiles,
   `sim.map.tileSize=5`, so ~15 world units), nearest first, re-picked as it moves, through `SpatialGrid`.
   - Built as the laser is: a stream of fast short shots from the orb towards each target, the drawn line made from
     the shots. A beam shot stops at the first thing it touches - another alien on the way, scenery, rock - and lands
     there, as the laser's does.
   - **Beams spare our men**: they never pick a man, and pass him harmlessly (the squad's side, as the laser's shots
     already are for the commander's own fire - check how the laser treats a trooper in the line and match it).
5. **The orb's blast.** On touching an alien, a man of ours, scenery or rock, the orb goes off in a **small ordinary
   blast** (G0018): nobody's side, **wounds** a man caught in it as a grenade's does, moderate damage, leaves what an
   ordinary blast leaves.
6. **Depletion.** Every point its beams land on anything alive drains the orb's **power**; at its power (`X`) it goes
   off where it is, in the same blast. It also goes off at its flight's end.
7. **Look** (pillar 5, numbers in `graphic.properties`): a green orb inside a thin, slightly offset outer ring that
   flickers green-white; green beams; a green blast.

## B. Firepower

1. **No more flamer stock.** Firepower no longer puts a flamer in every crate. The flamer is dealt at the same baseline as
   every other gun (it already has `armoury.cards=3`): the `stock armoury_flamer 1` effect goes.
2. **Heavy damage.** Each rank: **+15 %** damage for the **flamer, bazooka and detonator (limpet)**, for whoever holds one
   (`team`). Two ranks, as now. Written as three `gun <kind> gun_damage team 15` effects - no new effect form (no second
   user of a by-type form is named). The bazooka's and the limpet's **blast** damage must rise with it: if `gun_damage`
   does not reach a gun's blast today, make the one place that reads it do so for these guns.
3. Retire `CrateTest.addsNoSecondFlamerUnderFirepower`, `UpgradeTest.stocksEveryCrateWithAFlamerUnderFirepower` and the
   invariant rows and G0056/G0110 text that speak of Firepower's flamer.

## C. The sentry and Engineer

1. **The sentry** (new glossary word): a small, light-grey, standing, rapid-fire gun **nobody crews**, on the **players'
   side**. Not a **turret** (G0024: a place a man gets on) and not an **emplacement** (G0113: the aliens' uncrewed
   shooter). Nobody gets on it, nobody hauls it.
   - Stationary, turns fast onto its target, fires a light rapid gun at the nearest alien in **medium reach**.
   - Has health. **The aliens hunt it from the moment it stands** - a target like a man, so it draws them.
   - **Lives ~60 s**, then goes off. Its time left is shown (pillar 5).
   - **When it goes off** - destroyed or out of time - a **small ordinary blast** (nobody's side, wounds a man, small
     damage) **and** a brief **stun** on the aliens round it (never our men; the dash surge's way of stunning, G0108).
2. **Engineer**: a **personal** upgrade (chooser), **2 ranks**, tier 1, weight 10.
   - While held, the commander's **grenade key sets a sentry down at his feet** instead of throwing a grenade.
   - **It spends a grenade**: same count, same most (Bandolier raises it), refilled by grenade caches, the wait shortened
     by Grenadier. No other cap on how many stand.
   - **Rank 2: the sentry fires faster** (a shorter cooldown on its gun).
   - The squad's grenades (Explosives, G0051) are untouched.
3. **HUD**: the commander's grenade slot shows a sentry icon instead of a grenade while he holds Engineer; the count is
   the same count.

## Allocated numbers

G0127, G0128, G0129 written in this grilling. **G0130-G0131 spare**, ADR **0027** spare. (G0123-G0126 and ADR 0026 belong
to the finale-variety run.)
