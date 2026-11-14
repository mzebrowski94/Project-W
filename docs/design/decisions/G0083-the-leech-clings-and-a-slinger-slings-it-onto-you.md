# G0083: The leech clings, and a slinger slings it onto you

**Status.** amended by G0086: the leech ships with no fuse

**Trigger.** Adding a creature that rides, latches onto or travels with a man; a grip that leaves its man free; a
creature that goes off of itself after a time; a sling aimed onto a man rather than past him; making a leech pin; a
creature that never walks and still leaps.

**Enforced by.** `LeechTest`; `SimConfigTest.slingsLeechesOntoHimThatClingAndWaitOnTheGroundForAManToPass`; `AntTest`
(a grip that pins, unchanged); `MissionSweepTest` (the rosters).

**Settled with Mateusz, 2026-11-12**, from the playtest list of 2026-11-11 (ticket 12 of the playtest-feedback map).

**Constraint.** **Amends G0045** (a grip may cling: taken by touch, leaving its man free, spent on finishing him).
**Amends G0074** (a second slinger, whose riders are leeches, slung onto its target).

- **The leech is small, magenta and never walks** (speed 0: rooted). Health 250 since the 2026-11-13 playtest (150, a grunt's, before) - five sidearm rounds, still one hand strike. Its colour is the purple
  asked for, taken warm and bright so it reads apart from the spitter's blue-purple and the searer's dark pink
  (pillar 5).
- **It clings to the first man it touches**, commander or trooper (pillar 2) - on landing, or a man walking into one
  on the ground - and never bites. One leech to a man; a second that touches him merely touches him.
- **Lying in wait, it leaps at a man who passes close by** (from playtest, 2026-11-12): its own leap, the one every
  strain carries - within `triggerRange` of it, after the coiling tint every leap shows, aimed once at where he was
  (G0012), and taking hold of whoever it lands on. An ordinary leap: it can be shot in the air and rock and scenery
  stop it - what never walks is a body in the air like any leaper's. A man walking past is taken; one who reads the
  coil and runs is not. Missed, it sits and goes again after its cooldown; its fuse runs on through every leap.
- **A cling is a grip that pins nobody** (amends G0045). The pair, the clock drawn on him, the finish and letting go
  are the grip's; but he drives, fires, throws and acts as ever, and it is carried on his edge where it took him -
  not solid while it is on him, so it neither blocks nor shoves him, and still touched by whatever comes at it.
- **When his time is up it kills him and goes off**: its claws' blow (`KILL`), and it dies with it - spent on
  finishing him - so its death lays its venom on him (G0018's one death reaction).
- **Every death of it lays the venom**: `deathSlicks` circles of the shared blood puddle, on the alien side (G0034,
  G0076). So shooting or striking it off a man costs him a wound of venom rather than his life, and nothing else -
  no throw clear. His mates' shots pass through him to it.
- **A trooper with one on him fights on**, and on `AiChance.STRUGGLE` swings at it instead. Nobody flees a ridden
  man: walking him off the squad is the player's call.
- **Its fuse**: one that has hold of nobody goes off where it lies when its fuse runs out - a definition every strain
  carries, off at zero, drawn as a mine's quickening fuse. It runs from where it comes down: it waits, whole, while
  the leech is slung and while it has hold of a man, and starts afresh if it lets go - a man put down with one on him
  is let go, and it goes off beside him.
- **The leech slinger is a slinger whose riders are leeches** (amends G0074): the same body, numbers, tint and sling -
  in the air a leech is held off scenery as any rider is, though it is rooted on the ground - but aimed onto whoever it
  hunts (`landBehind` 0) and live the moment it lands (`landingSeconds` 0). A man standing
  to shoot is hit; a man moving steps out of it, and the leech sits on its fuse. Dead, what is aboard spills through
  `Brood` and sits on its fuses.

**Why a grip and not a new mechanism.** Everything a cling needs but one thing the grip already has: a pair named by
handles, a clock published on the man, a finish with the creature's own lethality, and letting go when either is
done. The one thing it does not is leaving him his legs, and that is a field (`pins`), not a system.

**Why it is not solid while it clings, and is carried by position.** A speed-0 strain is rooted, and a rooted body on a
man's edge would stop him walking into it the tick he turned. Not solid, it never pushes him; it still takes every
contact, so it is shot, struck and blasted as anything is. Put on his edge by position each tick, nothing that holds a
creature still or slows it - a stun, the ground - takes it off him.

**Why the fuse is a mine's.** A mine already is a clock on something that goes off by dying, with a flash that
quickens as it runs out. A leech's fuse is one with nothing to set it off - armed from the start, or never.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

- *A leech that pins its man, as an ant does.* It was the first proposal; Mateusz chose a man who keeps moving and
  shooting with a clock on him - the tension is where he walks it, not whether he can get free.
- *A leech that walks to a man, or leaps at one further off than a man passing by.* It never walks; what brings it to
  you is the sling, your own feet, or passing too close.
- *A small blast when it goes off.* It kills the man it is on and leaves venom; a blast would also catch his mates
  and every alien by him (G0018), and make it a grenade.
- *Troopers who flee a ridden mate, or who prefer to shoot a leech off one.* They fight on and shoot what they
  notice; getting him clear is the player's job.
- *Shaking one off by running past rock.* It is carried on him whatever is beside him.

**Revisit when** play says a leech on a trooper is never seen in time (a bigger mark on him - drawn only), or that the
venom of one shot off a man makes shooting it off not worth it (a smaller `deathSlicks`, never a special case).
