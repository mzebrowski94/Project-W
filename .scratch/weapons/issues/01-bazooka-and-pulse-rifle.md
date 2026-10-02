# 01 - The bazooka and the pulse rifle

Status: ready-for-agent (bazooka done; pulse rifle open)

Two found guns, both dealt into crates. Grilled with Mateusz on 2026-10-02.

## Bazooka (`BAZOOKA`)

- **Reload is heat.** `heat.perShot=1`: every shot stops the gun, and it comes back once it has cooled below
  `resumeBelow`. The heat arc over the holder is the reload, which keeps pillar 5. Short `cooldownSeconds`.
  This broke combat row 62 (the beam runs out sooner than any found gun); Mateusz chose to narrow it to
  guns that fire more than once before they stop (G0058).
- **Recoil is a short self-shove** opposite the aim, through the existing shove path (a stagger of about
  0.15 s). New `WeaponDef` numbers `recoilSpeed` and `recoilSeconds`, zero on every other gun, charged in
  `Guns.spend` - the one place a pull is charged - so whoever holds it is pushed. A turret is a station and
  does not move.
- **A fast rocket with a medium blast** where it stops: on the first thing it touches, or at its reach. The
  blast is an ordinary one (G0018), on nobody's side, so it catches the shooter and his men.
- **A man in its blast takes a wound**, as in a grenade's. A shot's blast harm today is the one
  `sim.blast.harm`, so the gun gets a `blastHarm` (charged spit keeps `down`).
- **Crates:** an `armoury_bazooka` cache, 2 cards (as the laser's; the others have 3).

## Pulse rifle (`PULSE`)

`laser` is the beam's word and stays it.

- Fast, small, quick bolts - a turret's kind of fire, less of it. Runs hot, slower than the beam.
- **Shock:** each hit builds shock on the body it lands on, and full shock stuns it for `stunSeconds`
  (default 1 s). Full is `stunAfterHits` hits (default 10) on a reference body; **a bigger body takes
  more**, in proportion to its radius as Bloat does. `stunAfterHits` 0 or 1 is today's stun on every hit.
- **It drains** at a rate while nothing hits it. It empties when the stun lands and does not build while
  the stun runs, so a held trigger is not a stun-lock (decided by Claude, not asked).
- Drawn on the target's charge ring (pillar 5).
- **The boss:** G0048 rules out a barrage that pauses when stunned. A full shock on the boss stuns it, but
  its barrage goes on, so the stun does little to it. Ask Mateusz whether the boss should take shock at
  all before building it.
- **Crates:** an `armoury_pulse` cache, 3 cards.

## Order

The bazooka first: recoil belongs in the deepened `Guns.spend` (architecture-review 02, done). Then the
pulse rifle, which needs a `Shock` component and a small system for the drain, registered after
`DamageSystem`.
