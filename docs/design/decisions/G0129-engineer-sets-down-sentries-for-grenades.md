# G0129: Engineer sets down sentries for grenades

**Status.** accepted

**Trigger.** A shooter on the players' side nobody crews; an upgrade that changes what a key does; the grenade slot.

**Enforced by.** Tests written with the code.

**Settled with Mateusz, 2026-11-24** (grilling). Not yet built, not yet played.

**Constraint.**

- **The sentry** is a small light-grey standing gun nobody crews, on the players' side: it turns fast onto the nearest
  alien in medium reach and fires a light rapid gun at it. Nobody gets on it, nobody hauls it. Not a **turret** (G0024)
  and not an **emplacement** (G0113).
- **The aliens hunt it from the moment it stands**: a target, as a man is.
- **It lives about sixty seconds**, its time left shown (pillar 5), and then goes off.
- **Going off** - destroyed or out of time - is a **small ordinary blast** (nobody's side, wounds a man, small damage)
  and a brief **stun** on the aliens round it, never our men, as the dash's surge stuns (G0108).
- **Engineer** is a **personal** upgrade, two ranks, tier 1. While held, the commander's grenade key **sets a sentry
  down at his feet** instead of throwing a grenade. **It spends a grenade**: the same count and most, refilled by a
  grenade cache, its wait shortened by Grenadier, raised by Bandolier. Nothing else caps how many stand.
- **Rank two: its gun fires faster.**
- **The grenade slot** shows a sentry where it showed a grenade, while he holds Engineer.
- The squad's grenades (G0051) are untouched.
- A new kind of thing, per G0008: a constant, a definition and a block in `assets/sim/`, with whatever component and
  system its standing fire genuinely needs.

**Why a new word.** A turret is a place a man gets on, and G0024 rules out a turret as a kit; an emplacement is the
aliens' standing shooter that is never a target. The sentry is neither, and borrowing either word would make its rule a
lie.

**Why grenades pay for it.** One count, one cache, one slot: Engineer changes what the key makes, not what the player
tracks (pillar 5).

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A sentry a man can get on or haul.* That is a turret.

**No:** *A count or recharge of its own.*

**No:** *Thrown along the aim.* It is set down at his feet.

**No:** *A cap on standing sentries* until play shows a carpet of them.

**Revisit when** play shows sentries carrying a level, or never living long enough to matter.
