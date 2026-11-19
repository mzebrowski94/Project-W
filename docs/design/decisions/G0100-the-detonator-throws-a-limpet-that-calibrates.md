# G0100: The detonator throws a limpet that calibrates

**Status.** accepted

**Trigger.** Adding or changing the detonator or its limpet; anything thrown that does not go off where its flight runs
out; a second press of the fire key meaning something other than firing; a blast whose size depends on how long its
explosive has waited.

**Enforced by.** `DetonatorTest` (one limpet, the press, where it sticks, its two circles, every way it goes off, the bar
and the panel word), `SystemOrderTest.sticksALimpetStraightAfterTheGrenadesBounceIsAnswered`; the look by judgement.

**Asked for** by Mateusz, 2026-11-19: a weapon that throws a small mud-green rotating rectangle, which sticks to walls
and enemies, can be set off in the air for a small explosion, and once stuck calibrates for about four seconds - a
small green ring, like a door being opened - and then ticks with a red dot and goes off bigger. Its heat shows over
him once it is thrown, with a word of its own in his panel. Grilled the same day.

**Constraint.** The **detonator** is a found gun (`WeaponKind`), `heavy`, dealt into crates; it replaces what he
carries and is lost with him like every gun. No trooper carries it, and G0077's grenade count does not apply - it is
no grenade. It throws a **limpet**.

- **One limpet, one key.** A fire press with no limpet out throws one, if the bar is ready. A fire press with a limpet
  out **detonates** it, ready bar or not - detonating is not a pull. Detonating takes a **fresh** press: the trigger
  still held from the throw sets nothing off. The pad's fire button does the same; no binding is added.
- **The bar is a reload.** A throw runs the heat to the stop (`perShot` 1, `resumeBelow` 0, G0058), and it cools as
  every bar does (G0025) whether a limpet is out or not. The arc over him is that reload; it is what limits throwing
  and setting one off in the air again and again. While a limpet is out, his panel shows **DETONATE** where it shows
  OVERHEAT, because the fire key now means something else (pillar 5).
- **The throw is G0019's** - along the aim direction, a fixed reach, never to the aim point - except where it ends.
  **Amends G0019**, whose "it blasts where the distance runs out" a limpet does not honour, as its revisit foresaw.
  It **sticks** to the first rock, arena edge, door, scenery (a barrel, a nest, an obstacle) or alien it touches, and
  passes through its own side - his men and his own turret - and through a body in its throes. Stuck to an alien it rides on that alien's edge, as a
  leech clings. A limpet that flies its whole reach touching nothing **lands and sticks to the ground there** - laid
  like a mine.
- **It calibrates once stuck**, for `calibrateSeconds` (2 - retuned from 4 by Mateusz, 2026-11-19), drawn as a small ring on the good-news colour; then it is
  **calibrated**, ticking with a red dot, and waits for as long as it takes. It never goes off by itself.
- **Two circles, one lethality.** A limpet goes off **uncalibrated** - in flight, or stuck and still calibrating - as
  a small blast (about 3) that **wounds** a man it catches; **calibrated**, as a big one (about 8, past the bazooka's 7)
  that puts him **down**. Both are ordinary blasts (G0018): fatal inside, nobody's side, stopped by rock, leaving fire
  (G0035), breaking a door outright (G0069). "More damage" is the wider circle and the harder harm, never a number of
  hit points.
- **It explodes by dying** (G0018), in whatever state it is in, and it dies when: it is **detonated**; a blast catches
  it - so limpets chain as mines do; its **owner loses the detonator** - takes another gun, or is killed; or **what it
  is stuck to dies or leaves the field** - an alien killed, a door opened or broken, a barrel gone up. Going down keeps
  it; a man down cannot fire, so he cannot set it off until he is up. Shots and claws pass through it: it lands no
  blow and takes none, and a blast alone reaches it.
- **Named by handle.** A limpet names its owner and what it is stuck to by handle (ADR 0016), and is found with
  `Handles.resolve` every tick; nothing holds an `Entity`.

**Scope.** The detonator and its limpet. Not the grenade, whose throw and count are unchanged (G0019, G0077); not how
often or how hard an upgrade makes a heavy gun - that is ADR 0017's, and a damage upgrade moves nothing on a blast.
The numbers are `sim.weapon.detonator.*`; the looks are `graphic.properties`.

**Why a gun, not a second throwable.** It has a bar and a word in the panel, and it is a choice made at a crate - the
grenade is what he can always do (G0019's *no grenade `WeaponKind`*), the detonator is what he found.

**Why fire both throws and detonates.** One key, and the game chooses (G0021): with one limpet out the press can only
mean one thing, and every free key is already claimed. A fresh press keeps a held trigger from throwing and setting
off in the same breath.

**Why the bar keeps cooling while a limpet is out.** A bar that held until the limpet went off would put a second rule
on heat - G0025 says a bar always cools. The reload does the work that matters: it rations the small blast in the air.

**Why a limpet the owner loses goes off.** Asked for by Mateusz. A limpet nobody can set off is clutter, and its going
off is still his doing - he threw it and he gave the gun up, or died holding it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Several limpets out at once, and one press setting them all off.* A field of remote charges is a minefield
he lays and then reads; one limpet is a decision made in the fight.

**No:** *A detonate key of its own.* Every key is claimed, and with one limpet out the fire press is unambiguous.

**No:** *Holding the bar full while a limpet is out.* Heat that stops cooling is G0025's rejected *heat on the man*
by another road.

**No:** *A small blast that a brute survives* - a blast damage per kind. G0018's one readable circle becomes a damage
curve; the two states differ by radius and harm instead.

**No:** *A calibrated limpet that goes off on its own after a while.* It would be a mine with a fuse, and the one
thing the detonator gives him is choosing the moment.

**No:** *A lost limpet that fizzles.* Raised and turned down by Mateusz: it goes off.

**No:** *Detonating on the held trigger.* The throw and the blast in one breath - a grenade that goes off on contact,
which G0019 refused.

**Revisit when** a trooper is to carry it, a second limpet is wanted, or an upgrade wants to move the calibration.
