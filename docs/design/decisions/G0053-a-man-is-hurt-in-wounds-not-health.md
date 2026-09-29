# G0053: A man is hurt in wounds, not health

**Trigger.** Adding a damage source, changing what a hit does to a commander or a trooper, adding
anything that heals, protects or disables a man, or proposing that a man's fate depend on an amount.

**Enforced by.** `VitalityTest` holds the harm table as one table, `ShieldTest` the shield,
`WoundCostTest` what a wound costs, `StunTest` a declared stun; `MedkitTest` arrives with the medkit
(H4). The invariant rows in `docs/design/README.md` name the rest.
The build order is in `docs/design/health-scope.md`.

**Supersedes in part G0020's** "a casualty is at zero health, so the next hit of any kind finishes
him": a man who is down is now finished by a hit that could have put him down or killed him standing,
and by nothing lighter. **Amends G0006, G0018 and G0023**, each noted in its own record.

**Constraint.**

- **Commanders and troopers have no health.** Each carries a count of **wounds** against a maximum,
  and a state: *standing* or *down*. Dying and dead are the throes and the reaper, as for everything
  else. Nothing on the player's side has a hidden number underneath the wounds. Aliens, nests,
  turrets and everything a level scatters keep their health exactly as it was.
- **Every blow declares a harm as well as an amount** - `WOUND`, `DOWN` or `KILL`, named for what it
  does to a man with no wounds. A man reads only the harm; anything with health reads only the
  amount. G0020, finished: for a man, the amount is not even consulted.
- **What each harm does** is one table, in one place (`DamageSystem`, through `Vitality`):
  - `WOUND` adds a wound. One past his maximum puts him down. Several on one tick are one wound, and
    for half a second afterwards - **grace** - another cannot land.
  - `DOWN` puts him down whatever his wounds. `KILL` kills him.
  - **A man who is down takes no `WOUND`.** A `DOWN` or a `KILL` still finishes him. What would have
    put him on the ground standing is still a risk lying down, which is the half of G0020's "rule
    that costs nothing" worth keeping.
- **Wounded is not a state.** It is a standing man with wounds above zero; he does everything a
  standing man does, slower and firing slower. Protections and disablements - grace, a **shield**, a
  stun, a slow - are timers on permanent components that compose on top, never states of the
  machine.
- **A shield takes one hit of any harm**, a grab and a stun included, and breaks. The shove still
  lands. Shields do not stack.
- **Everything that heals clears every wound**: being revived, a **medkit**, and *mending* after a
  spell out of trouble. A medkit is worth something only to a man who has a wound.
- **Wounds belong to a life** (G0044): a commander who respawns starts with none. His maximum is a
  stat, which belongs to his slot (G0049).

**Scope.** What a hit does to a man on the player's side. It says nothing about how much damage a
source deals to things with health, about aliens, or about when a source may hit at all.

**What maps where, 2026-09-29.** Gunfire, a boss's barrage, fire, venom and a grenade's blast wound -
fire and venom with a shove off the circle. A barrel, a mine, a charged corpse, a charged spit and a
turret going up under its crew put a man down. Claws, a leap and an ant's last bite kill. An ant's
grab he escapes from wounds him as he is let go. The keys beside each source's `damage=` are the
record from then on; this paragraph is not kept up to date.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *Health kept underneath the wounds* - a man at 100 whose wounds are thresholds on it. Rejected for
the reason G0020 gave against a health threshold: every source's amount would become a second
decision about lethality, and the shield, grace and a medkit are rules about *hits*, which an amount
can express only with a special case for each.

**No:** *Wounded as a state of its own* - STANDING, WOUNDED, DOWN. A wounded man does everything a
standing one does, so every system that asks "is he on his feet" would have to ask twice. The count
already says it.

**No:** *One state machine enumerating the conditions* - standing-shielded, standing-stunned,
wounded-shielded-in-grace. The combinations multiply with every condition added. A condition is a
timer beside the machine.

**No:** *Protection after a hit declared by what hit him*, as `victimInvulnerableSeconds` is for an
alien. How long a man is protected is a fact about him, as `Aid`'s protection already is (G0023).

**No:** *Grace that turns every harm.* A pellet would buy him half a second of immunity to a leap.

**No:** *A man who is down immune to everything.* Asked for, and narrowed on 2026-09-29: it would make
the bleed-out the only way a man on the ground dies, and leaving him in the open a timer rather than
a risk.

**No:** *A medkit carrying a `canTake` flag* for the wounded. G0006's reason: a pickup worth nothing to
its taker already says so, from the taker's state, where the claim is made.

**No:** *Adding or removing `Vitality` or `Shield` when a man is hurt or healed.* Permanent components and
a field, for the reason `Casualty`, `Dying` and `Stunned` already give.

**No:** *A blast that wounds some men and puts others down*, by distance or by anything else. A blast
still has one meaning inside its circle for a man, declared by its kind; that it kills every alien it
catches is a fact about the aliens (G0018, G0020).

**Revisit when** anything should hurt a man in more than one step at once - a heavy shot worth two
wounds - because that is the first thing three harms cannot say, and the answer should be a count on
the harm rather than an amount. Also when the playtest shows men spending the game wounded, which
would mean medkits and mending are doing the work a maximum should.
