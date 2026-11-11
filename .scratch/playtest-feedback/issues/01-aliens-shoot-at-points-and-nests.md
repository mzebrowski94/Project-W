# Aliens shoot at control points and nests

Type: task
Status: resolved
Blocked by: -
Section: Alien behaviour - bugs

## Question

Why do aliens fire at **control points** and **nests** - and keep at it, as if either were on the player's
side - and what is the smallest fix that makes them fire only at men? Mateusz starts the round here.

## From Mateusz (translated)

> BUG: enemies for some reason shoot at control points and nests, and focus on attacking them as if they
> were their enemy.

## Read first

`mattpocock-skills:diagnosing-bugs`. `docs/glossary.md` *Driving a unit* (aggro, rouse, hold ground,
settle). G0016 (aggro latches a place), G0026 (who an alien hunts), G0027 (the post). The ranged and barrage
aiming path and `Perception`'s published target.

## A lead (read, not run)

A **rouse** sends what is in earshot to a *place* - the point being taken, the nest being hit - and G0016
latches that place, not a man. If a shooter aims at its latched place while nobody stands there, it fires
at exactly the point or the nest. Check that first; then whether points or nests carry anything a target
query reads as the player's side.

## Done when

The smallest reproducing test first (a roused spitter by an empty point fires nothing at it), then the fix;
`verify.sh` green. An invariant row if the rule was unwritten.

## Answer

**Cause - the lead, confirmed, and narrower than it read.** Targeting was never wrong: points carry no
`Faction`, nests are `Side.ALIEN`, and `Drivers.huntable` takes only `Side.PLAYER`. Spitters
(`EnemyRangedSystem`) fire only on `aggro`, at the published man. The one shooter that fires at a *provoked*
place is `BarrageSystem` - by design, "the place it was last hurt from" (invariant: a boss faces where its
barrage shoots). A **rouse** latched that same place via `provokeAt`, so every barrage carrier in earshot -
searer, thumper, sludger, boss grunt - shot the point being taken or the nest being hit. The focus: the latch
holds until it arrives within a tile, and a barrage that holds still or never walks never arrives.

**Fix.** `Perception.roused` tells a rouse from a hurt: `Rouse` calls the new `rouseAt`, a hit's `provokeAt`
clears it, and `BarrageSystem` reads `Perception.hurtFromAPlace()` (provoked and not roused) - the one reader.
A roused creature still walks to look (G0016 unchanged); it just shoots nothing there. No tunables.

**Test.** `BarrageTest.holdsFireAtANestItWasRousedTo` - red before (12-shot rings at nobody), green after;
`verify.sh` green (2600 tests). The ticket's "roused spitter" case needed no test: spitters were never the
shooter. A point rouses through the same `Rouse`, so the nest test covers both.

**Invariant row** (added to aliens.md, after "A boss faces where its barrage shoots"): *A rouse is never somewhere to
shoot* - enforced by the test above; one reader `Perception.hurtFromAPlace`.
