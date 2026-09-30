# G0020: Lethality belongs to the source, not to the amount

**Status.** amended by G0023: the commander can be a casualty; amended by G0053: three harms, and a man who is down takes no wound

**Trigger.** Adding a damage source, changing what a blow does to a man, or proposing that how a unit
dies should depend on how badly it was hurt.

**Enforced by.** `CasualtyTest.leavesATrooperShotDownOnTheGroundRatherThanDead`,
`.killsATrooperOutrightWhenTheBlowIsOneThatKills`,
`VitalityTest.finishesAManWhoIsDownWithABlowThatWouldHavePutHimDownStanding`.

**Constraint.** Every blow declares, as data, what it does to a man - whether it kills outright or
leaves a **casualty**: a man on the ground with a **bleed-out** running, who can be **stabilised** back
onto his feet. The declaration rides on `ContactDamage`, travels on the `Damage` owed, and is read in
exactly one place - `DamageSystem`. **The worse declaration wins when hits combine**, as the longest
recovery window does. G0053 makes the declaration one of three harms; see *Amended by G0053*.

A unit that nothing on its side could come and rescue does not carry a `Casualty` component and dies as
it always did. That is why no alien can be one.

**Scope.** What a blow does to a man, and nothing else. Not how much damage a source does, how often, or
to whom - those are numbers in `assets/sim/`. The death path is untouched: a casualty who runs out of
time dies through the same `Deaths.kill` a bullet would have used, so there is one kind of corpse.

**Leaving a man in the open is a risk, not a timer.** Something that reaches him while he lies there can
still finish him, through the ordinary death path, with no special case written for it.

**DO NOT BUILD** - rejected, and likely to be re-proposed.

**No:** *A health threshold - below zero by more than X kills, otherwise a casualty.* What happens to you
would depend on arithmetic you cannot see (pillar 5): the player would learn "sometimes he gets up"
rather than "bullets knock you down and claws kill you". It also makes every weapon's damage a *second*
decision about lethality, so retuning a gun silently changes the rules of rescue.

**No:** *A chance to go down instead of dying.* A roll at the most consequential moment in the game, and
"he usually gets up" is not a rule anybody can play around (pillars 5 and 6).

**No:** *A `Casualty` component added when a man goes down and removed when he is picked up.* Adding or
removing a component mid-tick mutates the families being iterated and silently skips entities. The
component is permanent and `begun` is the switch.

**Reversed by G0023:** *Letting the commander be a casualty too.* It was refused while nothing but the
commander could stabilise anybody; troopers now pick him up.

**No:** *Making a blast wounding for some things and fatal for others.* A blast is one circle with one
meaning inside it (G0018); what it leaves behind is a fact about the victim, not about the blast.

**Revisit when** troopers turn out to spend the whole game on the ground, which would mean the bleed-out
is doing the work a difficulty setting should.

## Amended by G0053

**Three harms instead of a flag.** A blow declares `WOUND`, `DOWN` or `KILL` for a man, and a man has
wounds instead of health, so "what happens at zero health" becomes "what each harm does" - still
declared by the source and read in one place. Gunfire wounds; blasts put down, except a grenade's;
claws, a sweep and a leap kill.

A man who is down is finished by a `DOWN` or a `KILL`, and a `WOUND` does nothing to him: a barrel going
off beside him, or claws, still end it, and a stray pellet does not.
